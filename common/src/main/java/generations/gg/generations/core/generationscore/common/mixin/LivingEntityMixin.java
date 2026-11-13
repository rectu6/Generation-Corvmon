package generations.gg.generations.core.generationscore.common.mixin;

import generations.gg.generations.core.generationscore.common.world.ultraspace.suit.UltraBreathing;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dimensional suffocation (UltraBreathing): in an Ultra world without the full Ultra
 * Suit, Minecraft does not give the air back out of the water. Players only, Ultra
 * worlds only: the other dimensions and the other entities are not touched.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "increaseAirSupply", at = @At("HEAD"), cancellable = true)
    private void generations$ultraSuffocation(int current, CallbackInfoReturnable<Integer> cir) {
        if (UltraBreathing.blocksRecovery((LivingEntity) (Object) this)) cir.setReturnValue(current);
    }
}
