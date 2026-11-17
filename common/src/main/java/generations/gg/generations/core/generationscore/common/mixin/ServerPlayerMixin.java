package generations.gg.generations.core.generationscore.common.mixin;

import com.mojang.authlib.GameProfile;
import generations.gg.generations.core.generationscore.common.GenerationsCore;
import generations.gg.generations.core.generationscore.common.api.player.BiomesVisited;
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems;
import generations.gg.generations.core.generationscore.common.world.item.legends.DistanceTraveled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpRide;

import java.util.Collection;
import java.util.stream.Stream;


@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player {
    @Shadow public abstract ServerLevel serverLevel();

    @Shadow public abstract ItemEntity drop(@NotNull ItemStack droppedItem, boolean dropAround, boolean includeThrowerName);

    @Shadow public abstract void sendSystemMessage(@NotNull Component component);

    @Unique
    ResourceKey<Biome> currentBiome = null;

    public ServerPlayerMixin(Level level, BlockPos pos, float yRot, GameProfile gameProfile) {
        super(level, pos, yRot, gameProfile);
    }

    /** The charm case goes to the new player on respawn and on return from the End (like the ender chest). */
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void generations$keepCharmCase(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfo ci) {
        generations.gg.generations.core.generationscore.common.world.charm.CharmCase.copy(oldPlayer, (ServerPlayer) (Object) this);
    }

    /** In the Ultra Warp Ride tunnel, the player is not hurt in the world (see UltraWarpRide.protects). */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void generations$ultraWarpRide(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (UltraWarpRide.protects((ServerPlayer) (Object) this, source)) cir.setReturnValue(false);
    }

    /** Watching a legendary cinematic, the player is not hurt (see LegendaryCinematics.protects). */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void generations$legendaryCinematic(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (generations.gg.generations.core.generationscore.common.world.cinematic.LegendaryCinematics.protects((ServerPlayer) (Object) this, source)) cir.setReturnValue(false);
    }

    @Inject(method = "checkRidingStatistics", at = @At("TAIL"))
    public void ridingCheck(double dx, double dy, double dz, CallbackInfo ci) {
        generations_Core$processStatistics(dx, dy, dz);
    }

    @Inject(method = "checkMovementStatistics", at = @At("TAIL"))
    public void movingCheck(double dx, double dy, double dz, CallbackInfo ci) {
        generations_Core$processStatistics(dx, dy, dz);
    }

    @Unique
    public void generations_Core$processStatistics(double distanceX, double distanceY, double distanceZ) {
        // The Eon manifestations (EON-PLAN.md): the blocks really travelled, to tell the teleports.
        generations.gg.generations.core.generationscore.common.world.eon.EonManifest.moved((ServerPlayer) (Object) this, (float) Math.sqrt(distanceX * distanceX + distanceZ * distanceZ));
        if (!GenerationsCore.CONFIG.enigmaFragment.enabled) return;
        // Generations' fragment for each new biome: replaced by the Eon manifestations (eon.json: legacyBiomeFragments, D2).
        BiomesVisited biomesVisited = generations.gg.generations.core.generationscore.common.world.eon.EonManifest.legacyBiomeFragments() ? BiomesVisited.get((ServerPlayer) (Object) this) : null;
        if(biomesVisited != null && biomesVisited.numberOfVisitedBiomes() < GenerationsCore.CONFIG.enigmaFragment.limit)
            serverLevel().getBiome(blockPosition()).unwrapKey().filter(a -> !a.equals(currentBiome)).ifPresent(biomeResourceKey -> {
            this.currentBiome = biomeResourceKey;


            if(!biomesVisited.hasVisited(currentBiome)) {
                var stack = GenerationsItems.ENIGMA_FRAGMENT.value().getDefaultInstance();
                if(!getInventory().add(stack)) {
                    Containers.dropItemStack(level(), position().x, position().y, position().z, stack);
                }
                biomesVisited.add(currentBiome);
            }

        });

        var distance = (float) Math.sqrt(distanceX * distanceX + /*distanceY * distanceY TODO: Determine if we should have height*/ + distanceZ * distanceZ);
        var inventory = getInventory();
        var fullInventoryList = Stream.of(inventory.items, inventory.armor, inventory.offhand).flatMap(Collection::stream).toList();

        for (var stack : fullInventoryList) {
            if(stack.getItem() instanceof DistanceTraveled distanceTraveled) distanceTraveled.incrementDistance((ServerPlayer) (Object) this, stack, distance);
        }
    }
}
