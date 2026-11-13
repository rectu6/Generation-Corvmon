package generations.gg.generations.core.generationscore.common.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Lifecycle;
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minecraft marks a save "experimental" (a warning screen each time it is opened) when
 * it has more than the three vanilla dimensions, or one it does not know. The seven
 * Ultra Space worlds are the mod's own: they are left out of both checks. Any other
 * added dimension still makes the save experimental.
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {
    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void generations$ultraWorldsStable(ResourceKey<LevelStem> key, LevelStem stem, CallbackInfoReturnable<Lifecycle> cir) {
        if (UltraWorlds.isUltraStem(key.location())) cir.setReturnValue(Lifecycle.stable());
    }

    @ModifyExpressionValue(method = "bake", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int generations$ultraWorldsNotCounted(int size, @Local(argsOnly = true) Registry<LevelStem> registry) {
        int ultra = 0;
        for (ResourceKey<LevelStem> key : registry.registryKeySet()) {
            if (UltraWorlds.isUltraStem(key.location())) ultra++;
        }
        return size - ultra;
    }
}
