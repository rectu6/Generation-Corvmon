package generations.gg.generations.core.generationscore.common.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import generations.gg.generations.core.generationscore.common.battle.flee.InstantFlee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Instant flight (battle/flee/InstantFlee): the Run request is decided at once instead
 * of Cobblemon's "walk away" prompt, and an accepted flight ends the battle through
 * Cobblemon's own flight at the next tick without animation.
 */
@Mixin(value = PokemonBattle.class, remap = false)
public abstract class PokemonBattleFleeMixin {
    @Inject(method = "checkFleeAttempt", at = @At("HEAD"), cancellable = true)
    private void generations$instantFlee(CallbackInfoReturnable<Boolean> cir) {
        if (InstantFlee.onFleeAttempt((PokemonBattle) (Object) this)) cir.setReturnValue(true);
    }

    @Inject(method = "checkFlee", at = @At("HEAD"), cancellable = true)
    private void generations$acceptedFlight(CallbackInfo ci) {
        if (InstantFlee.onCheckFlee((PokemonBattle) (Object) this)) ci.cancel();
    }
}
