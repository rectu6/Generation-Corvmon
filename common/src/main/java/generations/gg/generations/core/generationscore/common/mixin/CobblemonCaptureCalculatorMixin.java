package generations.gg.generations.core.generationscore.common.mixin;

import com.cobblemon.mod.common.pokeball.catching.calculators.CobblemonCaptureCalculator;
import generations.gg.generations.core.generationscore.common.world.charm.Charms;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Catching Charm: Cobblemon's critical capture chance is (rate × Pokédex multiplier × 1)
 * / 6, with "* 1F → * 2F when the Catching Charm is implemented" left in
 * CriticalCaptureProvider. The rate it is given is multiplied instead (same result).
 */
@Mixin(value = CobblemonCaptureCalculator.class, remap = false)
public abstract class CobblemonCaptureCalculatorMixin {
    @ModifyArg(
            method = "processCapture",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/pokeball/catching/calculators/CobblemonCaptureCalculator;shouldHaveCriticalCapture(Lnet/minecraft/server/level/ServerPlayer;F)Z"),
            index = 1
    )
    private float generations$catchingCharm(ServerPlayer player, float rate) {
        return Charms.criticalCaptureRate(player, rate);
    }
}
