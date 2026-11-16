package generations.gg.generations.core.generationscore.common.client.catchcombo

import com.cobblemon.mod.common.client.CobblemonClient
import com.mojang.blaze3d.platform.InputConstants
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdGui
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import generations.gg.generations.core.generationscore.common.config.JsonConfigs
import generations.gg.generations.core.generationscore.common.network.packets.combo.S2CCatchComboPacket
import generations.gg.generations.core.generationscore.common.world.catchcombo.CatchCombo
import net.minecraft.Util
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Catch Combo's HUD (COMBO-FUITE-PERF-PLAN.md, 2.4), drawn with the HD tools
 * (HdText, HdGui's panels): "CATCH COMBO", the species ×n, the shiny bonus, the
 * guaranteed IVs and a bar to the next tier. A pulse and a glow at each capture, a
 * banner when a tier is reached and when the combo is lost. Only what the server sent
 * (S2CCatchComboPacket); the texts are HdText's cached textures, the panels HdGui's.
 * catch_combo_client.json (position, size, hidden in battle…) and a key (none by
 * default, Controls → Generations) to hide it.
 */
object CatchComboHud {
    class Settings(
        var version: Int = 1,
        /** false: never drawn. */
        var enabled: Boolean = true,
        /** Hidden by the key (kept between sessions). */
        var hidden: Boolean = false,
        /** top_left, top_right, bottom_left, bottom_right, middle_left, middle_right. */
        var anchor: String = "top_left",
        var offsetX: Int = 6,
        var offsetY: Int = 6,
        var scale: Float = 1f,
        /** Not drawn during a battle (the battle interface uses the screen). */
        var hideInBattle: Boolean = true,
        /** Pulse, glow and banners. */
        var animations: Boolean = true,
    ) {
        fun validate() {
            scale = JsonConfigs.clamp(scale, 0.5f, 3f, 1f)
            @Suppress("SENSELESS_COMPARISON")
            if (anchor == null) anchor = "top_left"
            anchor = anchor.lowercase()
        }
    }

    private val file get() = JsonConfigs.file("catch_combo_client.json")
    var settings = Settings().also { it.validate() }; private set

    /** The key that hides or shows the HUD: registered by the Fabric client (unbound). */
    val TOGGLE_KEY = KeyMapping("key.generations_core.catch_combo_hud", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "Generations")

    private const val WIDTH = 122f
    private const val HEIGHT = 50f
    private const val PULSE_MS = 380L
    private const val BANNER_MS = 3200L
    private const val FADE_MS = 600L

    private var combo: S2CCatchComboPacket? = null
    private var lastCount = 0
    private var pulseAt = 0L
    private var banner: Pair<Component, Component>? = null
    private var bannerGold = true
    private var bannerAt = 0L
    /** The combo just lost: still drawn fading out. */
    private var fading: S2CCatchComboPacket? = null
    private var fadingAt = 0L

    fun load() {
        settings = JsonConfigs.load("Catch Combo HUD", file, Settings::class.java, ::Settings, 1, { it.validate() })
    }

    private fun save() {
        runCatching {
            java.nio.file.Files.createDirectories(file.parent)
            java.nio.file.Files.writeString(file, JsonConfigs.gson.toJson(settings))
        }
    }

    fun init() {
        load()
    }

    /** Each client tick: the key. */
    fun tick() {
        while (TOGGLE_KEY.consumeClick()) {
            settings.hidden = !settings.hidden
            save()
            Minecraft.getInstance().player?.displayClientMessage(
                Component.translatable(if (settings.hidden) "generations_core.catch_combo.hud.hidden" else "generations_core.catch_combo.hud.shown"), true,
            )
        }
    }

    /** Leaving a world: nothing left from the last one. */
    fun clear() {
        combo = null; fading = null; banner = null; lastCount = 0
    }

    fun update(packet: S2CCatchComboPacket) {
        val now = Util.getMillis()
        val previous = combo
        when (packet.event) {
            S2CCatchComboPacket.Event.BREAK, S2CCatchComboPacket.Event.RESET -> {
                if (previous != null && previous.count > 0) {
                    fading = previous
                    fadingAt = now
                    if (packet.event == S2CCatchComboPacket.Event.BREAK) {
                        showBanner(
                            Component.translatable("generations_core.catch_combo.hud.lost"),
                            Component.translatable("generations_core.catch_combo.break.${packet.reason.ifEmpty { "command" }}"), false, now,
                        )
                    }
                }
            }
            S2CCatchComboPacket.Event.TIER -> showBanner(
                Component.translatable("generations_core.catch_combo.hud.tier", packet.count),
                bonusLine(packet.shinyMultiplier, packet.perfectIvs), true, now,
            )
            else -> {}
        }
        if (packet.count > lastCount && packet.event != S2CCatchComboPacket.Event.SYNC) pulseAt = now
        lastCount = packet.count
        combo = packet.takeIf { it.count > 0 && it.species.isNotEmpty() }
        if (combo != null) fading = null
    }

    private fun showBanner(title: Component, line: Component, gold: Boolean, now: Long) {
        banner = title to line
        bannerGold = gold
        bannerAt = now
    }

    private fun bonusLine(shiny: Float, ivs: Int): Component {
        val parts = mutableListOf<String>()
        if (shiny > 1.0001f) parts += Component.translatable("generations_core.catch_combo.hud.shiny", format(shiny)).string
        if (ivs > 0) parts += Component.translatable("generations_core.catch_combo.hud.ivs", ivs).string
        return Component.literal(if (parts.isEmpty()) Component.translatable("generations_core.catch_combo.hud.no_bonus").string else parts.joinToString("  ·  "))
    }

    /** ×1,15, ×2,5, ×2 (two decimals at most, no trailing zeros). */
    private fun format(value: Float): String = generations.gg.generations.core.generationscore.common.world.shiny.ShinyMath.format(Math.round(value * 100.0) / 100.0)

    private fun speciesName(key: String): String =
        CatchCombo.speciesOf(key)?.translatedName?.string ?: key.substringAfter(':').substringBefore('/').replaceFirstChar { it.uppercase() }

    /** GuiMixin, after the vanilla HUD. */
    @JvmStatic
    fun render(graphics: GuiGraphics) {
        val minecraft = Minecraft.getInstance()
        if (!settings.enabled || settings.hidden || minecraft.options.hideGui || minecraft.player == null) return
        if (settings.hideInBattle && CobblemonClient.battle != null) return
        val now = Util.getMillis()
        val shown = combo ?: fading?.takeIf { now - fadingAt < FADE_MS }
        val bannerLeft = banner?.let { BANNER_MS - (now - bannerAt) } ?: 0L
        if (bannerLeft <= 0L) banner = null
        if (shown == null && banner == null) return

        val scale = settings.scale
        val screenW = graphics.guiWidth() / scale
        val screenH = graphics.guiHeight() / scale
        val x = when {
            settings.anchor.endsWith("right") -> screenW - WIDTH - settings.offsetX
            else -> settings.offsetX.toFloat()
        }
        val y = when {
            settings.anchor.startsWith("bottom") -> screenH - HEIGHT - settings.offsetY
            settings.anchor.startsWith("middle") -> (screenH - HEIGHT) / 2f + settings.offsetY
            else -> settings.offsetY.toFloat()
        }

        val pose = graphics.pose()
        pose.pushPose()
        pose.scale(scale, scale, 1f)
        if (shown != null) {
            val alpha = if (shown === combo) 1f else 1f - (now - fadingAt).toFloat() / FADE_MS
            drawPanel(graphics, shown, x, y, alpha.coerceIn(0f, 1f), now)
        }
        banner?.let { (title, line) ->
            val t = (now - bannerAt).toFloat()
            val appear = if (settings.animations) min(1f, t / 180f) else 1f
            val leave = min(1f, bannerLeft / 300f)
            val alpha = min(appear, leave)
            val by = if (settings.anchor.startsWith("bottom")) y - 26f else y + HEIGHT + 4f
            drawBanner(graphics, title, line, x, by + (1f - appear) * 6f, alpha)
        }
        pose.popPose()
    }

    private fun drawPanel(graphics: GuiGraphics, combo: S2CCatchComboPacket, x: Float, y: Float, alpha: Float, now: Long) {
        if (alpha <= 0.01f) return
        HdGui.panel(graphics, HdGui.PANEL_DARK, x, y, WIDTH, HEIGHT, HdGui.withAlpha(0xFFFFFF, 0.88f * alpha), corner = 6f)
        HdGui.panel(graphics, HdGui.PANEL_OUTLINE, x, y, WIDTH, HEIGHT, HdGui.withAlpha(if (combo.shinyMultiplier > 1.0001f) 0xF5C030 else 0x7FB8FF, 0.55f * alpha), corner = 6f)

        // Title and count, with a pulse and a glow at each capture.
        HdText.draw(graphics, "CATCH COMBO", x + 6f, y + 4f, 6.5f, HdGui.withAlpha(0xF5C030, alpha), bold = true)
        val sincePulse = now - pulseAt
        val pulse = if (settings.animations && sincePulse in 0 until PULSE_MS) 1f - sincePulse.toFloat() / PULSE_MS else 0f
        val countSize = 13f * (1f + 0.35f * pulse * pulse)
        val countText = "×${combo.count}"
        if (pulse > 0f) {
            val glow = 34f * (1f + pulse)
            HdGui.draw(graphics, HdGui.GLOW, x + WIDTH - 22f - glow / 2f, y + 10f - glow / 2f, glow, glow, HdGui.withAlpha(0xFFD860, 0.7f * pulse * alpha))
        }
        HdText.draw(graphics, countText, x + WIDTH - 6f, y + 3f - (countSize - 13f) / 2f, countSize, HdGui.withAlpha(0xFFFFFF, alpha), bold = true, align = HdText.Align.RIGHT, shadow = true)

        // Species.
        val name = HdText.fit(speciesName(combo.species), WIDTH - 44f, 8.5f, true)
        HdText.draw(graphics, name, x + 6f, y + 13f, 8.5f, HdGui.withAlpha(0xFFFFFF, alpha), bold = true)

        // Bonuses.
        val bonus = bonusLine(combo.shinyMultiplier, combo.perfectIvs).string
        val bonusColour = if (combo.shinyMultiplier > 1.0001f || combo.perfectIvs > 0) 0x9FE3FF else 0x9A9AA8
        HdText.draw(graphics, HdText.fit(bonus, WIDTH - 12f, 6.5f), x + 6f, y + 25f, 6.5f, HdGui.withAlpha(bonusColour, alpha))
        if (combo.shinyMultiplier > 1.0001f && settings.animations) {
            val twinkle = 0.55f + 0.45f * sin(now / 260.0).toFloat()
            HdGui.draw(graphics, HdGui.SPARKLE, x + WIDTH - 15f, y + 23f, 9f, 9f, HdGui.withAlpha(0xFFF2A0, twinkle * alpha))
        }

        // Next tier.
        val barY = y + HEIGHT - 11f
        if (combo.nextThreshold > 0) {
            val from = combo.tierThreshold.toFloat()
            val ratio = ((combo.count - from) / max(1f, combo.nextThreshold - from)).coerceIn(0f, 1f)
            HdGui.bar(graphics, x + 6f, barY, WIDTH - 46f, 5f, HdGui.withAlpha(0x2A2A34, alpha))
            if (ratio > 0f) HdGui.bar(graphics, x + 6.5f, barY + 0.5f, ((WIDTH - 47f) * ratio).coerceAtLeast(4f), 4f, HdGui.withAlpha(0xF5C030, alpha))
            HdText.draw(graphics, "${combo.count}/${combo.nextThreshold}", x + WIDTH - 6f, barY - 1.5f, 6f, HdGui.withAlpha(0xD0D0DC, alpha), align = HdText.Align.RIGHT)
        } else {
            HdText.draw(graphics, Component.translatable("generations_core.catch_combo.hud.max").string, x + 6f, barY - 1.5f, 6f, HdGui.withAlpha(0xF5C030, alpha), bold = true)
        }
    }

    private fun drawBanner(graphics: GuiGraphics, title: Component, line: Component, x: Float, y: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val colour = if (bannerGold) 0xF5C030 else 0xEE4444
        HdGui.panel(graphics, if (bannerGold) HdGui.PANEL_DARK else HdGui.PANEL_RED, x, y, WIDTH, 22f, HdGui.withAlpha(0xFFFFFF, 0.9f * alpha), corner = 5f)
        HdGui.panel(graphics, HdGui.PANEL_OUTLINE, x, y, WIDTH, 22f, HdGui.withAlpha(colour, 0.8f * alpha), corner = 5f)
        HdText.draw(graphics, HdText.fit(title.string, WIDTH - 10f, 7.5f, true), x + WIDTH / 2f, y + 2.5f, 7.5f, HdGui.withAlpha(colour, alpha), bold = true, align = HdText.Align.CENTER)
        HdText.draw(graphics, HdText.fit(line.string, WIDTH - 10f, 6f), x + WIDTH / 2f, y + 12f, 6f, HdGui.withAlpha(0xFFFFFF, alpha), align = HdText.Align.CENTER)
    }
}
