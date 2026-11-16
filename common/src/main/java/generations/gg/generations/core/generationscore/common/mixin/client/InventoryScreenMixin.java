package generations.gg.generations.core.generationscore.common.mixin.client;

import generations.gg.generations.core.generationscore.common.client.charm.CharmCaseScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The charm case's button in the survival inventory, right of the player's frame, level with
 * the crafting grid's first row; it follows the inventory when the recipe book opens (its
 * place from leftPos and topPos each frame).
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends EffectRenderingInventoryScreen<InventoryMenu> {
    @Unique
    private Button generations$charmCaseButton;

    protected InventoryScreenMixin(InventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void generations$addCharmCaseButton(CallbackInfo ci) {
        generations$charmCaseButton = this.addRenderableWidget(CharmCaseScreen.button(this.leftPos + CharmCaseScreen.BUTTON_X, this.topPos + CharmCaseScreen.BUTTON_Y));
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void generations$followRecipeBook(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (generations$charmCaseButton != null) generations$charmCaseButton.setPosition(this.leftPos + CharmCaseScreen.BUTTON_X, this.topPos + CharmCaseScreen.BUTTON_Y);
    }
}
