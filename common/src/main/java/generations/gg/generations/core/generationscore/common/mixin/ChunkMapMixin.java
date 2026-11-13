package generations.gg.generations.core.generationscore.common.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The Ultra Space worlds use the Overworld's noise router: with the save's seed they
 * would all have the Overworld's very relief. Their generator gets a seed of its own,
 * the save's mixed with the world's id (UltraWorlds.terrainSeed): different and
 * reproducible. Other dimensions are left as they are.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/core/HolderGetter;J)Lnet/minecraft/world/level/levelgen/RandomState;"),
            index = 2
    )
    private long generations$ultraWorldSeed(long seed, @Local(argsOnly = true) ServerLevel level) {
        return UltraWorlds.terrainSeed(level, seed);
    }
}
