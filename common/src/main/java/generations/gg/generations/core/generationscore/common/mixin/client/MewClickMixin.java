package generations.gg.generations.core.generationscore.common.mixin.client;

import generations.gg.generations.core.generationscore.common.client.mew.MewClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mew's game (MEW-PLAN.md D5): a left or right click on the illusion under the crosshair, within
 * reach, is sent to the server instead of the click's usual action, at that moment only.
 */
@Mixin(Minecraft.class)
public abstract class MewClickMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void generations$mewAttack(CallbackInfoReturnable<Boolean> cir) {
        if (MewClient.click() || generations.gg.generations.core.generationscore.common.client.johto.JohtoClient.click() || generations.gg.generations.core.generationscore.common.client.eon.EonClient.click()) cir.setReturnValue(false);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void generations$mewUse(CallbackInfo ci) {
        if (MewClient.click() || generations.gg.generations.core.generationscore.common.client.johto.JohtoClient.click() || generations.gg.generations.core.generationscore.common.client.eon.EonClient.click()) ci.cancel();
    }
}
