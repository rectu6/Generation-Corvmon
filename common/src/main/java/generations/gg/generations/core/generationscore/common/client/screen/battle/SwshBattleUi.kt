package generations.gg.generations.core.generationscore.common.client.screen.battle

import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection
import com.mojang.blaze3d.systems.RenderSystem
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import kotlin.math.floor

/**
 * Battle menus in the style of Sword and Shield, bottom right of the screen:
 * the actions (Fight, Pokémon, Bag/Capture, Run) as a column of pills with an icon,
 * the moves as pills in their type colour. Textures are drawn at four times the GUI
 * size and scaled down smoothly. Cobblemon keeps the logic: SwshActionSelection
 * replaces its action menu, mixins only move and redraw its move tiles.
 */
object SwshBattleUi {
    private fun texture(name: String) = GenerationsCore.id("textures/gui/battle/$name.png")

    val PILL = texture("swsh_pill")
    val PILL_SELECTED = texture("swsh_pill_selected")
    val MOVE = texture("swsh_move")
    val MOVE_SELECTED = texture("swsh_move_selected")
    val CURSOR = texture("swsh_cursor")
    val ICON_FIGHT = texture("swsh_icon_fight")
    val ICON_PARTY = texture("swsh_icon_party")
    val ICON_BAG = texture("swsh_icon_bag")
    val ICON_RUN = texture("swsh_icon_run")
    val ICON_FORFEIT = texture("swsh_icon_forfeit")

    const val ACTION_WIDTH = 116
    const val ACTION_HEIGHT = 20
    const val MOVE_WIDTH = 140
    const val MOVE_HEIGHT = 22
    private const val SPACING = 4
    private const val RIGHT = 12
    /** Space under the menus, for the back and gimmick buttons. */
    private const val BOTTOM = 30
    /** Height taken by the menus: the battle log goes above them. */
    const val MENU_HEIGHT = 4 * (MOVE_HEIGHT + SPACING) + 4

    private val sizes = mapOf(
        PILL to (464 to 80), PILL_SELECTED to (464 to 80), MOVE to (560 to 88), MOVE_SELECTED to (560 to 88),
        CURSOR to (48 to 48), ICON_FIGHT to (64 to 64), ICON_PARTY to (64 to 64), ICON_BAG to (64 to 64),
        ICON_RUN to (64 to 64), ICON_FORFEIT to (64 to 64),
    )
    private val smooth = mutableSetOf<ResourceLocation>()

    private val width get() = Minecraft.getInstance().window.guiScaledWidth
    private val height get() = Minecraft.getInstance().window.guiScaledHeight

    fun actionX() = width - RIGHT - ACTION_WIDTH
    fun actionY(index: Int, count: Int) = height - BOTTOM - (count - index) * (ACTION_HEIGHT + SPACING)
    fun moveX() = width - RIGHT - MOVE_WIDTH
    fun moveY(index: Int, count: Int) = height - BOTTOM - (count - index) * (MOVE_HEIGHT + SPACING)
    /** Row of the back button and the gimmick buttons, under the menus. */
    fun buttonsY() = height - 24f

    /**
     * Where a move tile goes. Cobblemon lays them out in a 2 x 2 grid from the
     * selection's corner; they become a column at the bottom right. Gimmick tiles
     * (Dynamax, Z, Tera) copy placed tiles and keep their place.
     */
    fun movePosition(selection: BattleMoveSelection, x: Float, y: Float): FloatArray {
        if (x >= width / 2f) return floatArrayOf(x, y)
        val index = (if (x > selection.x + 1) 1 else 0) + (if (y > selection.y + 1) 2 else 0)
        val count = selection.moveSet.moves.size.coerceIn(1, 4)
        return floatArrayOf(moveX().toFloat(), moveY(index.coerceAtMost(count - 1), count).toFloat())
    }

    /** Draws a whole HD texture in the given GUI rectangle, smoothly scaled down. */
    fun blit(graphics: GuiGraphics, texture: ResourceLocation, x: Number, y: Number, w: Int, h: Int, alpha: Float = 1f, red: Float = 1f, green: Float = 1f, blue: Float = 1f) {
        val (tw, th) = sizes[texture] ?: (w to h)
        if (smooth.add(texture)) Minecraft.getInstance().textureManager.getTexture(texture).setFilter(true, false)
        RenderSystem.enableBlend()
        RenderSystem.setShaderColor(red, green, blue, alpha)
        graphics.pose().pushPose()
        graphics.pose().translate(x.toFloat(), y.toFloat(), 0f)
        graphics.blit(texture, 0, 0, w, h, 0f, 0f, tw, th, tw, th)
        graphics.pose().popPose()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    }

    private fun argb(rgb: Int, alpha: Float) = ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or (rgb and 0xFFFFFF)

    /** One action of the menu: white pill, black when hovered, with its icon and the cursor. */
    fun renderAction(graphics: GuiGraphics, x: Int, y: Int, label: Component, icon: ResourceLocation, hovered: Boolean, opacity: Float) {
        blit(graphics, if (hovered) PILL_SELECTED else PILL, x, y, ACTION_WIDTH, ACTION_HEIGHT, opacity)
        blit(graphics, icon, x + ACTION_WIDTH - 19, y + 2, 16, 16, opacity)
        HdText.draw(graphics, label, x + 12f, y + 5f, 9.5f, argb(if (hovered) 0xFFFFFF else 0x24242C, opacity), bold = true)
        if (hovered) blit(graphics, CURSOR, x - 13, y + 4, 12, 12, opacity)
    }

    fun isOver(mouseX: Double, mouseY: Double, x: Number, y: Number, w: Int, h: Int) =
        mouseX >= x.toDouble() && mouseX <= x.toDouble() + w && mouseY >= y.toDouble() && mouseY <= y.toDouble() + h

    /** A move: a pill in its type colour, type icon, name and PP. */
    fun renderMove(tile: BattleMoveSelection.MoveTile, graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val opacity = tile.moveSelection.opacity
        val alpha = opacity * if (tile.selectable) 1f else 0.5f
        val hovered = tile.selectable && tile.isHovered(mouseX.toDouble(), mouseY.toDouble())
        val (r, g, b) = tile.rgb
        val x = tile.x
        val y = tile.y

        blit(graphics, MOVE, x, y, MOVE_WIDTH, MOVE_HEIGHT, alpha, r.toFloat(), g.toFloat(), b.toFloat())
        if (hovered) {
            blit(graphics, MOVE_SELECTED, x, y, MOVE_WIDTH, MOVE_HEIGHT, opacity)
            blit(graphics, CURSOR, x - 13, y + 5, 12, 12, opacity)
        }
        TypeIcon(x = x + 3, y = y + 2, type = tile.elementalType, opacity = opacity).render(graphics)

        val name = HdText.fit(tile.moveTemplate.displayName.string, MOVE_WIDTH - 74f, 9.5f, true)
        HdText.draw(graphics, name, x + 24f, y + 6f, 9.5f, argb(0xFFFFFF, alpha), bold = true, outline = argb(0x101018, 0.55f * alpha))

        val move = tile.move
        val pp = if (move.pp == 100 && move.maxpp == 100) "—/—" else "${move.pp}/${move.maxpp}"
        val ppColour = when {
            move.pp == 0 -> 0xFF5050
            move.pp <= floor(move.maxpp / 2f) -> 0xFFD040
            else -> 0xFFFFFF
        }
        HdText.draw(graphics, "PP $pp", x + MOVE_WIDTH - 10f, y + 6.5f, 8.5f, argb(ppColour, alpha), bold = true, align = HdText.Align.RIGHT, outline = argb(0x101018, 0.55f * alpha))
    }
}
