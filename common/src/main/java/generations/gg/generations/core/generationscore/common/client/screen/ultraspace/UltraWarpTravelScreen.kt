package generations.gg.generations.core.generationscore.common.client.screen.ultraspace

import generations.gg.generations.core.generationscore.common.client.screen.hd.HdButton
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdGui
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpDifficulty
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.sin

/**
 * Open for the whole Ultra Warp Ride: the journey shows through it (no background),
 * and it takes the keys and clicks, so that nothing reaches the world where the
 * player stands. While it has them, the movement keys steer the Pokémon
 * (UltraWarpSteering reads them). Escape asks whether to leave the journey. The game
 * is not paused.
 *
 * Its HUD, on the edges so as to hide neither the Pokémon nor what comes: energy
 * (top left, blinking when low), light-years (top right), light on the edges when
 * something is taken (orange) or touched (red), and the events (top middle).
 */
class UltraWarpTravelScreen : Screen(Component.translatable("generations_core.ultra_warp.title")) {
    /** Asking whether to leave: the keys do not steer meanwhile. */
    var confirming = false
        private set
    private val opened = System.currentTimeMillis()

    override fun init() {
        if (!confirming) return
        val x = width / 2
        val y = height / 2 + 6
        addRenderableWidget(HdButton(x - 128, y, 120, 20, Component.translatable("generations_core.ultra_warp.travel.leave")) { UltraWarpRideClient.quit() })
        addRenderableWidget(HdButton(x + 8, y, 120, 20, Component.translatable("generations_core.ultra_warp.travel.stay")) { toggle() })
    }

    private fun toggle() {
        confirming = !confirming
        rebuildWidgets()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // No leaving a lost journey (the server would not either).
            val ride = UltraWarpRideClient.ride
            if (ride?.defeat == null && ride?.captured == null) toggle()
            return true
        }
        if (confirming && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            UltraWarpRideClient.quit()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun shouldCloseOnEsc(): Boolean = false

    override fun isPauseScreen(): Boolean = false

    // The journey is the background.
    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val ride = UltraWarpRideClient.ride ?: return
        flashes(graphics, ride)
        edges(graphics, ride)
        energy(graphics, ride)
        lightYears(graphics, ride)
        banner(graphics, ride)

        if (confirming) {
            val w = 300f
            val h = 64f
            val left = width / 2f - w / 2
            val top = height / 2f - 40f
            HdGui.panel(graphics, HdGui.PANEL_DARK, left, top, w, h, corner = 10f)
            HdText.draw(graphics, Component.translatable("generations_core.ultra_warp.travel.confirm"), width / 2f, top + 12f, 10f, WHITE, bold = true, align = HdText.Align.CENTER)
        } else {
            // The way out and the keys, for the first seconds.
            val shown = (System.currentTimeMillis() - opened) / 1000f
            val alpha = (1f - (shown - 6f) / 2f).coerceIn(0f, 1f)
            if (alpha > 0f) {
                val options = Minecraft.getInstance().options
                val keys = listOf(options.keyUp, options.keyLeft, options.keyDown, options.keyRight).joinToString("") { it.translatedKeyMessage.string.uppercase() }
                HdText.draw(graphics, Component.translatable("generations_core.ultra_warp.travel.controls", keys), width / 2f, height - 40f, 8f, HdGui.withAlpha(0xFFFFFF, alpha * 0.85f), align = HdText.Align.CENTER, shadow = true)
                HdText.draw(graphics, Component.translatable("generations_core.ultra_warp.travel.hint"), width / 2f, height - 28f, 8f, HdGui.withAlpha(0xFFFFFF, alpha * 0.85f), align = HdText.Align.CENTER, shadow = true)
            }
        }
        if (ride.config.dev.debugInfo) {
            val steering = ride.steering
            val lines = listOf(
                "seed ${ride.packet.seed}",
                "distance %.1f  speed %.1f b/s".format(ride.distance, ride.speed),
                "offset %.2f, %.2f  floor %.2f".format(steering.x, steering.y, ride.allowed),
                "steer %.1f, %.1f  boost ${steering.boost}".format(steering.vx, steering.vy),
                "pokemon x%.2f  room %.2f".format(ride.riderScale, ride.riderRadius),
                UltraWarpDifficulty.at(ride.settings, ride.lightYears).let {
                    "${ride.settings.mode.id}: orb %.0f%% blue %.0f%% debris %.0f%% arc %.0f%% gravity %.0f%% events x%.2f".format(
                        it.orbs * 100, it.sparks * 100, it.debris * 100, it.arcs * 100, it.gravity * 100, it.events,
                    )
                },
                "camera %.1f behind  fov %.0f  ${Minecraft.getInstance().fps} fps".format(ride.cameraRig.distance, ride.fov),
            )
            lines.forEachIndexed { i, line -> HdText.draw(graphics, line, 8f, 44f + i * 10f, 7.5f, 0xFFB8F0FF.toInt(), shadow = true) }
        }
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    /** Energy gauge, top left: blue-green, orange below half, red and blinking below a quarter. */
    private fun energy(graphics: GuiGraphics, ride: UltraWarpRideClient.Ride) {
        val max = ride.settings.config.energy.max.toDouble()
        val ratio = (ride.energy / max).toFloat().coerceIn(0f, 1f)
        val x = 10f
        val y = 10f
        val w = 150f
        val h = 26f
        HdGui.panel(graphics, HdGui.PANEL_DARK, x, y, w, h, HdGui.withAlpha(0xFFFFFF, 0.8f), corner = 6f)
        HdText.draw(graphics, Component.translatable("generations_core.ultra_warp.hud.energy"), x + 8f, y + 4f, 6.5f, 0xFFD8E4F0.toInt(), bold = true)
        HdText.draw(graphics, "%.0f".format(ride.energy), x + w - 8f, y + 4f, 6.5f, WHITE, bold = true, align = HdText.Align.RIGHT)
        val colour = when {
            ratio < 0.25f -> 0xFF4A5A
            ratio < 0.5f -> 0xFFA030
            else -> 0x4CE0C8
        }
        val blink = if (ratio < 0.25f) 0.55f + 0.45f * sin(ride.seconds * 9f) else 1f
        HdGui.bar(graphics, x + 8f, y + 15f, w - 16f, 6f, 0xFF20202C.toInt())
        if (ratio > 0f) HdGui.bar(graphics, x + 8f, y + 15f, (w - 16f) * ratio, 6f, HdGui.withAlpha(colour, blink))
    }

    /** Light-years travelled, top right. */
    private fun lightYears(graphics: GuiGraphics, ride: UltraWarpRideClient.Ride) {
        val x = width - 10f
        HdText.draw(graphics, "%.1f".format(ride.lightYears), x, 10f, 13f, WHITE, bold = true, align = HdText.Align.RIGHT, shadow = true)
        HdText.draw(graphics, Component.translatable("generations_core.ultra_warp.hud.light_years"), x, 27f, 6.5f, 0xFFB8C8E8.toInt(), bold = true, align = HdText.Align.RIGHT, shadow = true)
        HdText.draw(graphics, UltraWarpRideScreen.modeName(ride.settings.mode.id), x, 36f, 6.5f, 0xFFFFC870.toInt(), bold = true, align = HdText.Align.RIGHT, shadow = true)
    }

    /** The latest event's name, top middle, for three seconds. */
    private fun banner(graphics: GuiGraphics, ride: UltraWarpRideClient.Ride) {
        val text = ride.banner ?: return
        val age = ride.seconds - ride.bannerStart
        if (age > 3f) return
        val alpha = (minOf(age * 4f, (3f - age) * 2f)).coerceIn(0f, 1f)
        HdText.draw(graphics, text, width / 2f, 16f, 12f, HdGui.withAlpha(0xFFFFFF, alpha), bold = true, align = HdText.Align.CENTER, outline = HdGui.withAlpha(0x301050, alpha * 0.8f))
    }

    /**
     * Whole-screen light: lightning in storms, Necrozma's glare as it passes, and the
     * defeat (a white-violet flash, then the dark).
     */
    private fun flashes(graphics: GuiGraphics, ride: UltraWarpRideClient.Ride) {
        fun fill(rgb: Int, alpha: Float) {
            if (alpha > 0.01f) graphics.fill(0, 0, width, height, HdGui.withAlpha(rgb, alpha.coerceIn(0f, 1f)))
        }
        fill(0xE8E0FF, ride.lightning * 0.35f)
        ride.necrozma?.let { f -> fill(0xFFF4D8, (1f - kotlin.math.abs(f - 0.8f) / 0.1f).coerceIn(0f, 1f) * 0.8f) }
        // Through a portal: its colour fills the view, then white.
        ride.capture?.let { p ->
            val colour = ride.captured?.color ?: 0xFFFFFF
            fill(colour, p * p * 0.55f)
            fill(0xFFFFFF, ((p - 0.65f) / 0.35f).coerceIn(0f, 1f))
        }
        ride.defeat?.let { d ->
            fill(0xD8C0FF, (1f - d * 4f).coerceIn(0f, 1f) * 0.8f)
            fill(0x120420, ((d - 0.2f) / 0.8f).coerceIn(0f, 1f))
            HdText.draw(graphics, Component.translatable("generations_core.ultra_warp.event.defeat"), width / 2f, height / 2f - 8f, 14f, HdGui.withAlpha(0xFFFFFF, (d * 3f).coerceIn(0f, 1f)), bold = true, align = HdText.Align.CENTER)
        }
    }

    /** Light on the screen's edges: red after a hit, orange after an orb. */
    private fun edges(graphics: GuiGraphics, ride: UltraWarpRideClient.Ride) {
        fun glow(rgb: Int, strength: Float) {
            if (strength <= 0.01f) return
            val edge = HdGui.withAlpha(rgb, 0.55f * strength)
            val none = HdGui.withAlpha(rgb, 0f)
            val band = (height * 0.18f).toInt()
            graphics.fillGradient(0, 0, width, band, edge, none)
            graphics.fillGradient(0, height - band, width, height, none, edge)
        }
        glow(0xFF2030, ride.hitFlash)
        glow(0xFFA020, ride.pickupFlash)
    }

    // Closed only by the end of the journey.
    override fun onClose() {}

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()
    }
}
