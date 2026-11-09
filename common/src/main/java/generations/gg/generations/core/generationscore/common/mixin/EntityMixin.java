package generations.gg.generations.core.generationscore.common.mixin;

import generations.gg.generations.core.generationscore.common.world.raid.RaidManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raid entities (the boss, the NPCs and their Pokémon, the copies of the players'
 * Pokémon) only live for their raid:
 * - never saved with the world. Cobblemon's Pokémon override shouldBeSaved without
 *   calling it here, so saveAsPassenger, which writes every entity, refuses them too;
 * - removed when their raid is no longer fought (a crash, an older save), otherwise
 *   they would stay in the world and could be fought or caught.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
    private void generations$raidEntities(CallbackInfoReturnable<Boolean> cir) {
        if (RaidManager.isRaidEntity((Entity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "saveAsPassenger", at = @At("HEAD"), cancellable = true)
    private void generations$raidEntitiesUnsaved(CompoundTag tag, CallbackInfoReturnable<Boolean> cir) {
        if (RaidManager.isRaidEntity((Entity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void generations$strayRaidEntities(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide() || entity.tickCount % 20 != 0) return;
        if (RaidManager.isStray(entity)) entity.discard();
    }
}
