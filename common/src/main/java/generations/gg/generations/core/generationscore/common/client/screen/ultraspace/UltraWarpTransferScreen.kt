package generations.gg.generations.core.generationscore.common.client.screen.ultraspace

import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.sin

/**
 * The white of the portal, held while the server loads and checks the place of arrival
 * (UltraWarpTransfers): the world behind is not seen, no key reaches it, the player
 * waits where they stand, unharmed. Closed by the arrival (the change of dimension
 * replaces it with Minecraft's loading screen), a failure, or after a long time.
 */
class UltraWarpTransferScreen(
    /** The world's name, null for the return. */
    private val world: Component?,
    private val colour: Int,
    /** Can be closed with Escape after this (epoch milliseconds), never stuck. */
    private val closableAfter: Long,
    /** Phase 6: the passage fades in over this many milliseconds (0: white at once). */
    private val fadeMillis: Long = 0L,
    /** Phase 6: through a rift (its own title). */
    private val rift: Boolean = false,
) : Screen(Component.translatable("generations_core.ultra_warp.transfer.title")) {
    private val opened = System.currentTimeMillis()

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val fade = fade()
        graphics.fill(0, 0, width, height, ((fade * 255).toInt() shl 24) or 0xFFFFFF)
        // A soft wash of the destination's colour from the edges (stronger through a rift).
        val tint = (colour and 0xFFFFFF) or (((if (rift) 0x70 else 0x30) * fade).toInt() shl 24)
        graphics.fillGradient(0, 0, width, height / 3, tint, 0x00FFFFFF)
        graphics.fillGradient(0, height * 2 / 3, width, height, 0x00FFFFFF, tint)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val seconds = (System.currentTimeMillis() - opened) / 1000f
        val centre = width / 2f
        if (fade() < 0.6f) return
        val title = if (world != null && rift) Component.translatable("generations_core.ultra_rift.transfer", world)
        else if (world != null) Component.translatable("generations_core.ultra_warp.transfer.opening", world)
        else Component.translatable("generations_core.ultra_warp.transfer.return")
        val accent = 0xFF000000.toInt() or if (world != null) darker(colour) else 0x405070
        HdText.draw(graphics, title, centre, height / 2f - 14f, 14f, accent, bold = true, align = HdText.Align.CENTER)
        val dots = ".".repeat(1 + (seconds * 2).toInt() % 3)
        val alpha = (0.55f + 0.45f * sin(seconds * 3f)).coerceIn(0f, 1f)
        HdText.draw(
            graphics, Component.translatable("generations_core.ultra_warp.transfer.loading").string + dots,
            centre, height / 2f + 8f, 8f, ((alpha * 255).toInt() shl 24) or 0x506078, align = HdText.Align.CENTER,
        )
    }

    /** 0 to 1 over the fade (1 at once without one). */
    private fun fade(): Float = if (fadeMillis <= 0L) 1f else ((System.currentTimeMillis() - opened).toFloat() / fadeMillis).coerceIn(0f, 1f)

    /** Readable on white: the colour, darkened. */
    private fun darker(rgb: Int): Int {
        val r = ((rgb shr 16) and 0xFF) * 6 / 10
        val g = ((rgb shr 8) and 0xFF) * 6 / 10
        val b = (rgb and 0xFF) * 6 / 10
        return (r shl 16) or (g shl 8) or b
    }

    override fun shouldCloseOnEsc(): Boolean = System.currentTimeMillis() > closableAfter

    override fun isPauseScreen(): Boolean = false
}
