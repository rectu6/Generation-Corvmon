package generations.gg.generations.core.generationscore.common.mixin.client;

import com.cobblemon.mod.common.client.gui.battle.widgets.BattleMessagePane;
import generations.gg.generations.core.generationscore.common.client.screen.battle.SwshBattleHud;
import generations.gg.generations.core.generationscore.common.client.screen.battle.SwshBattleUi;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cobblemon's battle log: above the Sword and Shield menus, and hidden when the HD
 * HUD shows its own log (SwshBattleHud).
 */
@Mixin(value = BattleMessagePane.class, remap = false)
public abstract class BattleMessagePaneMixin {
    @Inject(method = "getAppropriateY", at = @At("RETURN"), cancellable = true)
    private void generations$aboveMenus(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() - SwshBattleUi.MENU_HEIGHT);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, remap = true)
    private void generations$hdLog(GuiGraphics context, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (SwshBattleHud.INSTANCE.getEnabled()) ci.cancel();
    }
}
