package generations.gg.generations.core.generationscore.common.client.screen.hd

import generations.gg.generations.core.generationscore.common.client.screen.battle.SwshBattleUi
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

/** Button of the HD interfaces: white pill, black when hovered, smooth text. */
class HdButton(
    x: Int, y: Int, width: Int, height: Int, message: Component,
    private val textSize: Float = 9f,
    private val onPress: (HdButton) -> Unit,
) : AbstractButton(x, y, width, height, message) {
    override fun onPress() = onPress(this)

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val hovered = active && isHoveredOrFocused
        val alpha = if (active) 1f else 0.45f
        val texture = if (hovered) PILL_SELECTED else PILL
        HdGui.panel(graphics, texture, x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), HdGui.withAlpha(0xFFFFFF, alpha), corner = height / 2f, texCorner = 40)
        val colour = if (hovered) 0xFFFFFF else 0x24242C
        HdText.draw(graphics, message, x + width / 2f, y + (height - textSize) / 2f - 0.5f, textSize, HdGui.withAlpha(colour, alpha), bold = true, align = HdText.Align.CENTER)
        if (hovered) HdGui.draw(graphics, CURSOR, x - 11f, y + height / 2f - 5f, 10f, 10f)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)

    companion object {
        val PILL = HdGui.Texture(SwshBattleUi.PILL, 464, 80)
        val PILL_SELECTED = HdGui.Texture(SwshBattleUi.PILL_SELECTED, 464, 80)
        val CURSOR = HdGui.Texture(SwshBattleUi.CURSOR, 48, 48)
    }
}
