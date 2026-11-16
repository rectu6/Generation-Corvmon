package generations.gg.generations.core.generationscore.common.client.cinematic

import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Open for the whole cinematic: invisible (the scene is the background), it holds the keys and
 * the mouse (no moving, aiming, hitting or using), hides the cursor, and draws the black bars,
 * the title and, in a debug test, the scene's time and shot. Escape opens the pause menu (the
 * solo game pauses, and so does the scene); the screen comes back when it closes.
 */
class CinematicScreen : Screen(Component.translatable("generations_core.cinematic.title")) {
    override fun init() {
        GLFW.glfwSetInputMode(Minecraft.getInstance().window.window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // pauseGame does nothing while a screen is open: the pause menu itself (it pauses a solo game).
            Minecraft.getInstance().setScreen(net.minecraft.client.gui.screens.PauseScreen(true))
            return true
        }
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = true

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean = true

    override fun shouldCloseOnEsc(): Boolean = false

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val p = CinematicClient.playback ?: return
        val script = p.scene.script
        val t = p.sceneTime.coerceIn(0.0, CinematicScenes.WRITTEN_SECONDS)
        val endFade = if (p.ending >= 0) 1.0 - ((p.clock.seconds - p.ending) / 0.6).coerceIn(0.0, 1.0) else 1.0
        if (p.config.letterbox && p.sceneTime >= 0.0) {
            // Bars to 2.39:1.
            val target = ((height - width / 2.39) / 2.0).coerceAtLeast(0.0)
            val bar = (target * script.letterbox(t) * endFade).toInt()
            if (bar > 0) {
                graphics.fill(0, 0, width, bar, 0xFF000000.toInt())
                graphics.fill(0, height - bar, width, height, 0xFF000000.toInt())
            }
        }
        val alpha = (script.title(t) * endFade).toFloat()
        if (alpha > 0.01f) title(graphics, p, alpha, t)
        if (p.packet.debug) {
            val lines = listOf(
                "%s  t %.2f / a %.2f s  (%.1f s real)".format(p.scene.id, t, p.frame.a, p.clock.seconds),
                "shot %s  fov %.1f  weight %.2f".format(script.shotName(t), p.camera.fov(70.0), p.camera.weight),
                "actor %s  particles %d  %s".format(p.frame.actor.pose, p.frame.particles.count, p.config.quality),
                "${Minecraft.getInstance().fps} fps",
            )
            lines.forEachIndexed { i, line -> HdText.draw(graphics, line, 8f, 8f + i * 10f, 7.5f, 0xFFB8F0FF.toInt(), shadow = true) }
        }
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    /** The name, large, in the lower third, with the scene's subtitle and two thin lines. */
    private fun title(graphics: GuiGraphics, p: CinematicClient.Playback, alpha: Float, t: Double) {
        val name = p.scene.title.string.uppercase()
        val rise = (1.0 - ((t - 15.5) / 0.6).coerceIn(0.0, 1.0)).toFloat()
        val y = height * 0.68f + rise * 6f
        val size = (height / 9f).coerceIn(18f, 46f)
        val spaced = name.toCharArray().joinToString(" ")
        val colour = (((alpha * 255f).toInt().coerceIn(0, 255)) shl 24) or 0xEAF6FF
        val lineColour = (((alpha * 170f).toInt().coerceIn(0, 255)) shl 24) or 0xBFE6FF
        val w = HdText.width(spaced, size, bold = true)
        val half = (w / 2f + 18f).toInt()
        val cx = width / 2
        graphics.fill(cx - half, (y - 6f).toInt(), cx + half, (y - 5f).toInt(), lineColour)
        HdText.draw(graphics, spaced, width / 2f, y, size, colour, bold = true, align = HdText.Align.CENTER, shadow = true)
        graphics.fill(cx - half, (y + size + 4f).toInt(), cx + half, (y + size + 5f).toInt(), lineColour)
        val sub = (((alpha * 220f).toInt().coerceIn(0, 255)) shl 24) or 0xCFE8FF
        HdText.draw(graphics, p.scene.subtitle, width / 2f, y + size + 11f, (size * 0.32f).coerceAtLeast(8f), sub, align = HdText.Align.CENTER, shadow = true)
    }
}
