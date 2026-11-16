package generations.gg.generations.core.generationscore.common.mixin;

import generations.gg.generations.core.generationscore.common.world.charm.CharmCase;
import generations.gg.generations.core.generationscore.common.world.charm.CharmCaseHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The charm case (world/charm/CharmCase) of every player, saved in its data next to the
 * ender chest (key generations_core:charm_case), dropped on death only when charms.json
 * says it is not kept and keepInventory is off.
 */
@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements CharmCaseHolder {
    @Unique
    private final SimpleContainer generations$charmCase = new SimpleContainer(CharmCase.SIZE);

    protected PlayerMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public SimpleContainer generations$getCharmCase() {
        return generations$charmCase;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void generations$saveCharmCase(CompoundTag tag, CallbackInfo ci) {
        CharmCase.save(generations$charmCase, tag, this.registryAccess());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void generations$loadCharmCase(CompoundTag tag, CallbackInfo ci) {
        CharmCase.load((Player) (Object) this, tag, this.registryAccess());
    }

    @Inject(method = "dropEquipment", at = @At("TAIL"))
    private void generations$dropCharmCase(CallbackInfo ci) {
        if (!this.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) CharmCase.dropOnDeath((Player) (Object) this);
    }
}
