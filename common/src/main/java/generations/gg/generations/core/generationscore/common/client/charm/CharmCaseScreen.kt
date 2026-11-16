package generations.gg.generations.core.generationscore.common.client.charm

import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.packets.combo.C2SOpenCharmCasePacket
import generations.gg.generations.core.generationscore.common.world.charm.CharmCaseMenu
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import org.lwjgl.glfw.GLFW

/**
 * The charm case, drawn like a vanilla two-row chest (generic_54). Closing it with the
 * inventory key goes back to the inventory; Escape closes everything.
 */
class CharmCaseScreen(menu: CharmCaseMenu, inventory: Inventory, title: Component) : AbstractContainerScreen<CharmCaseMenu>(menu, inventory, title) {
    private var backToInventory = true

    init {
        imageHeight = CharmCaseMenu.CASE_BOTTOM + 96
        inventoryLabelY = imageHeight - 94
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val hovered = hoveredSlot
        if (hovered is CharmCaseMenu.CaseSlot && !hovered.hasItem() && menu.carried.isEmpty) {
            graphics.renderTooltip(font, Component.translatable(hovered.place.hint), mouseX, mouseY)
        } else {
            renderTooltip(graphics, mouseX, mouseY)
        }
    }

    /** generic_54 without its rows of 9: its title band, a plain row stretched, a frame per slot, its inventory part. */
    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = (width - imageWidth) / 2
        val y = (height - imageHeight) / 2
        val bottom = CharmCaseMenu.CASE_BOTTOM
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, 17)
        graphics.blit(TEXTURE, x, y + 17, imageWidth, bottom - 17, 0f, 16f, imageWidth, 1, 256, 256)
        for (slot in menu.slots) if (slot is CharmCaseMenu.CaseSlot) graphics.blit(TEXTURE, x + slot.x - 1, y + slot.y - 1, 7, 17, 18, 18)
        graphics.blit(TEXTURE, x, y + bottom, 0, 126, imageWidth, 96)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) backToInventory = false
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun onClose() {
        super.onClose()
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (backToInventory && minecraft.gameMode?.hasInfiniteItems() != true) minecraft.setScreen(InventoryScreen(player))
    }

    companion object {
        private val TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png")

        /** The button's icon (tools/charm_case_icons.py): 16x16, normal above, hovered below. */
        private val BUTTON_TEXTURE = GenerationsCore.id("textures/gui/charm_case_button.png")

        /**
         * The button from the inventory's top left corner: in the gap between the player's
         * frame (ends at 75) and the crafting grid (starts at 97), level with its first row.
         */
        const val BUTTON_X = 78
        const val BUTTON_Y = 14

        /** The inventory's button (InventoryScreenMixin): the case's icon alone, lighter when hovered. */
        @JvmStatic
        fun button(x: Int, y: Int): Button = object : Button(
            x, y, 16, 16, Component.translatable("container.generations_core.charm_case"),
            Button.OnPress { if (Minecraft.getInstance().player?.containerMenu?.carried?.isEmpty != false) C2SOpenCharmCasePacket().sendToServer() },
            Button.CreateNarration { it.get() },
        ) {
            init {
                setTooltip(Tooltip.create(Component.translatable("container.generations_core.charm_case")))
            }

            override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
                graphics.blit(BUTTON_TEXTURE, x, y, 0f, if (isHoveredOrFocused) 16f else 0f, 16, 16, 16, 32)
            }
        }
    }
}
