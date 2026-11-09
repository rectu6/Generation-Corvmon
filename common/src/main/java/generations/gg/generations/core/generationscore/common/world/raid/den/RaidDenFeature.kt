package generations.gg.generations.core.generationscore.common.world.raid.den

import com.mojang.serialization.Codec
import generations.gg.generations.core.generationscore.common.util.PlatformRegistry
import generations.gg.generations.core.generationscore.common.world.level.block.GenerationsUtilityBlocks
import generations.gg.generations.core.generationscore.common.world.raid.config.RaidConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.LegacyRandomSource
import net.minecraft.world.level.levelgen.WorldgenRandom
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Feature types of the raids. */
object GenerationsRaidFeatures : PlatformRegistry<Feature<*>>(Registries.FEATURE, BuiltInRegistries.FEATURE) {
    val RAID_DEN: Holder<Feature<*>> = create("raid_den") { RaidDenFeature(NoneFeatureConfiguration.CODEC) }
}

/**
 * Places Dynamax dens like structures: the world is cut in cells of `spacing`
 * chunks, and each cell has one possible chunk, kept away from the cell's edge by
 * minDistanceBetweenDens. No den is too close to another, without any state shared
 * between the generation threads. Dens stand on dry land, or on the surface of the
 * water when allowWaterDens is on.
 */
class RaidDenFeature(codec: Codec<NoneFeatureConfiguration>) : Feature<NoneFeatureConfiguration>(codec) {
    override fun place(context: FeaturePlaceContext<NoneFeatureConfiguration>): Boolean {
        val level = context.level()
        val origin = context.origin()
        val chunk = ChunkPos(origin)
        if (!isDenChunk(level.seed, chunk)) return false

        val random = context.random()
        val x = chunk.minBlockX + random.nextInt(16)
        val z = chunk.minBlockZ + random.nextInt(16)
        val pos = spot(level, x, z) ?: return false
        level.setBlock(pos, GenerationsUtilityBlocks.RAID_DEN.value().defaultBlockState(), 2)
        return true
    }

    /** Where the den goes in this column, or null when it cannot. */
    private fun spot(level: WorldGenLevel, x: Int, z: Int): BlockPos? {
        val top = BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z)
        val below = top.below()
        val belowState = level.getBlockState(below)
        if (belowState.fluidState.`is`(FluidTags.WATER)) {
            // On the surface of the water: the den takes the place of the top water block.
            if (!RaidConfig.dens.allowWaterDens || !level.getBlockState(top).isAir) return null
            return below
        }
        if (belowState.fluidState.`is`(FluidTags.LAVA)) return null
        if (!belowState.isFaceSturdy(level, below, Direction.UP)) return null
        val here = level.getBlockState(top)
        if (!here.isAir && !here.canBeReplaced()) return null
        if (!here.fluidState.isEmpty) return null
        return top
    }

    companion object {
        private const val SALT = 742_196_581L

        /** The chunk of this cell that may hold a den, and whether it holds one. */
        fun isDenChunk(seed: Long, chunk: ChunkPos): Boolean {
            val config = RaidConfig.dens
            val separation = ceil(config.minDistanceBetweenDens / 16.0).toInt().coerceAtLeast(1)
            val spacing = maxOf(separation + 1, sqrt(config.denSpawnRarity.toDouble()).roundToInt())
            // One den per cell of spacing² chunks, a den every denSpawnRarity chunks on average.
            val chance = (spacing * spacing / config.denSpawnRarity.toDouble()).coerceAtMost(1.0)
            val cellX = Math.floorDiv(chunk.x, spacing)
            val cellZ = Math.floorDiv(chunk.z, spacing)
            val random = WorldgenRandom(LegacyRandomSource(0L))
            random.setLargeFeatureWithSalt(seed, cellX, cellZ, SALT.toInt())
            val range = spacing - separation
            val chosenX = cellX * spacing + random.nextInt(range)
            val chosenZ = cellZ * spacing + random.nextInt(range)
            return chunk.x == chosenX && chunk.z == chosenZ && random.nextDouble() < chance
        }

        /** Distance in chunks between two dens at least. */
        fun minChunkDistance(): Int = Mth.ceil(RaidConfig.dens.minDistanceBetweenDens / 16.0)
    }
}
