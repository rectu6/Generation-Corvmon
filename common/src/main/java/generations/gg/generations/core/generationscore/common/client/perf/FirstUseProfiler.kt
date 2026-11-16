package generations.gg.generations.core.generationscore.common.client.perf

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * Diagnostic of the freezes "the first time, smooth afterwards" (option
 * client.profileFirstUse of config/generations/core/main.json, off by default). It writes
 * to the log:
 * - how long each screen took to open (its init and its first frame), the first time
 *   and the next ones;
 * - every frame slower than 50 ms, with what was happening (screen, battle, HD texts
 *   made in that frame and their time);
 * - the time between the start of a battle on the client and the first frame of its
 *   interface, with the worst frame in between.
 * The server's side (BattleRegistry.startBattle, Showdown) is timed by BattleStartProfiler.
 * Nothing is measured when the option is off: two calls per frame that return at once.
 */
object FirstUseProfiler {
    private const val SLOW_FRAME_MS = 50.0

    private var frame = 0L
    private var frameStart = 0L
    private var texturesAtStart = 0
    private var textureNanosAtStart = 0L
    private val notes = mutableListOf<String>()

    private val screensSeen = HashSet<String>()
    private var openingScreen: String? = null
    private var openingStart = 0L
    private var openingInitMs = 0.0
    private var openingFirst = false

    private var battleActive = false
    private var battleSince = 0L
    private var battleUiLogged = false
    private var battleWorstFrame = 0.0

    private val enabled: Boolean
        get() = runCatching { GenerationsCore.CONFIG.client.profileFirstUse }.getOrDefault(false)

    private fun ms(nanos: Long) = nanos / 1_000_000.0

    private fun log(message: String, vararg args: Any?) = GenerationsCore.LOGGER.info("[first-use] $message", *args)

    /** A line added to the next slow-frame report (only when the option is on). */
    @JvmStatic
    fun note(text: String) {
        if (enabled && notes.size < 16) notes += text
    }

    @JvmStatic
    fun frameStart() {
        frame++
        HdText.onFrame(frame)
        if (!enabled) return
        frameStart = System.nanoTime()
        texturesAtStart = HdText.texturesMade
        textureNanosAtStart = HdText.textureNanos
    }

    @JvmStatic
    fun frameEnd() {
        if (!enabled || frameStart == 0L) return
        val now = System.nanoTime()
        val frameMs = ms(now - frameStart)
        val minecraft = Minecraft.getInstance()
        val screen = minecraft.screen
        val texts = HdText.texturesMade - texturesAtStart
        val textMs = ms(HdText.textureNanos - textureNanosAtStart)

        openingScreen?.let { name ->
            log(
                "screen {} opened{}: init {} ms, first frame {} ms ({} HD texts made, {} ms)",
                name, if (openingFirst) " for the first time" else "", "%.1f".format(openingInitMs), "%.1f".format(ms(now - openingStart)), texts, "%.1f".format(textMs),
            )
            openingScreen = null
        }

        val battle = CobblemonClient.battle != null
        if (battle && !battleActive) {
            battleActive = true
            battleSince = frameStart
            battleUiLogged = false
            battleWorstFrame = 0.0
        } else if (!battle) {
            battleActive = false
        }
        if (battleActive && !battleUiLogged) {
            battleWorstFrame = maxOf(battleWorstFrame, frameMs)
            if (screen is BattleGUI) {
                battleUiLogged = true
                log("battle: interface shown {} ms after the battle started on the client (worst frame {} ms)", "%.0f".format(ms(now - battleSince)), "%.1f".format(battleWorstFrame))
            } else if (now - battleSince > 20_000_000_000L) {
                battleUiLogged = true
            }
        }

        if (frameMs > SLOW_FRAME_MS) {
            log(
                "slow frame {} ms: screen {}, {}{} HD texts made ({} ms){}",
                "%.1f".format(frameMs), screen?.javaClass?.simpleName ?: "none", if (battle) "in battle, " else "", texts, "%.1f".format(textMs),
                if (notes.isEmpty()) "" else "; " + notes.joinToString("; "),
            )
        }
        notes.clear()
    }

    @JvmStatic
    fun screenOpening(screen: Screen?) {
        if (!enabled || screen == null) return
        openingScreen = screen.javaClass.simpleName
        openingFirst = screensSeen.add(screen.javaClass.name)
        openingStart = System.nanoTime()
    }

    @JvmStatic
    fun screenOpened(screen: Screen?) {
        if (!enabled || screen == null || openingScreen == null) return
        openingInitMs = ms(System.nanoTime() - openingStart)
    }
}
