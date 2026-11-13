package generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.gen

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import generations.gg.generations.core.generationscore.common.util.PlatformRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.util.KeyDispatchDataCodec
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.util.valueproviders.IntProvider
import net.minecraft.core.Direction
import net.minecraft.server.level.WorldGenRegion
import net.minecraft.tags.BlockTags
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.VineBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.placement.PlacedFeature
import net.minecraft.world.level.levelgen.structure.StructureType
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType
import java.util.Optional
import kotlin.math.atan2
import kotlin.math.pow
import net.minecraft.world.level.levelgen.DensityFunction
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Ultra Space worlds' own worldgen pieces (phase 4.2), only where Minecraft's own
 * have nothing alike; everything else is vanilla, configured in the data
 * (tools/ultra_worlds.py). Registered on both loaders through PlatformRegistry, like
 * the raid dens.
 */
object UltraWorldFeatures : PlatformRegistry<Feature<*>>(Registries.FEATURE, BuiltInRegistries.FEATURE) {
    val CRATER: Holder<Feature<*>> = create("crater") { CraterFeature(CraterFeature.Config.CODEC) }
    val CRYSTAL_SPIKE: Holder<Feature<*>> = create("crystal_spike") { CrystalSpikeFeature(CrystalSpikeFeature.Config.CODEC) }
    val TWISTED_COLUMN: Holder<Feature<*>> = create("twisted_column") { TwistedColumnFeature(TwistedColumnFeature.Config.CODEC) }
    val ZEN_GARDEN: Holder<Feature<*>> = create("zen_garden") { ZenGardenFeature(ZenGardenFeature.Config.CODEC) }
    val STEAM_VENT: Holder<Feature<*>> = create("steam_vent") { SteamVentFeature(SteamVentFeature.Config.CODEC) }
    val CITY_BLOCK: Holder<Feature<*>> = create("city_block") { CityBlockFeature(CityBlockFeature.Config.CODEC) }
    val RESOURCES: Holder<Feature<*>> = create("ultra_resources") { UltraResourcesFeature(UltraResourcesFeature.Config.CODEC) }
}

object UltraDensityFunctions : PlatformRegistry<MapCodec<out DensityFunction>>(Registries.DENSITY_FUNCTION_TYPE, BuiltInRegistries.DENSITY_FUNCTION_TYPE) {
    val CRATERS: Holder<MapCodec<out DensityFunction>> = create("craters") { Craters.MAP_CODEC }
}

/** Where the Ultra Space structures are tried (phase 4.3.1): Minecraft's random spread, its numbers in ultra_structures.json. */
object UltraStructurePlacements : PlatformRegistry<StructurePlacementType<*>>(Registries.STRUCTURE_PLACEMENT, BuiltInRegistries.STRUCTURE_PLACEMENT) {
    val ULTRA_SPREAD: Holder<StructurePlacementType<*>> = create("ultra_spread") { UltraSpreadPlacement.TYPE }
}

/** The Ultra Space structures' own type (phase 4.3.1): a jigsaw started in a cavern, never in solid rock (the Ultra Abyss). */
object UltraStructureTypes : PlatformRegistry<StructureType<*>>(Registries.STRUCTURE_TYPE, BuiltInRegistries.STRUCTURE_TYPE) {
    val ULTRA_CAVERN: Holder<StructureType<*>> = create("ultra_cavern") { UltraCavernStructure.TYPE }
}

/**
 * A feature may only read and write the chunks of its generating region (its own and
 * the ones next to it): anywhere else Minecraft stops the chunk ("Requested chunk
 * unavailable during world generation"). Checked before every read and write.
 */
private fun WorldGenLevel.inRegion(x: Int, z: Int): Boolean = hasChunk(x shr 4, z shr 4)

/** The surface height at (x, z), null outside the generating region. */
private fun WorldGenLevel.surfaceAt(x: Int, z: Int): Int? = if (inRegion(x, z)) getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) else null

/**
 * Whether a structure stands in the chunk of (x, z): its bounding box overlaps it. The
 * scenery (craters, spikes, columns, vents, gardens) leaves the place to it: features
 * come after the structures and would dig into a base or grow through its roofs.
 * Outside a real generating region (the checks made outside the game): never. What
 * StructureManager.hasAnyStructureAt does, read straight from the region's chunk (no
 * StructureManager made at each call).
 */
private fun WorldGenLevel.structureAt(x: Int, z: Int): Boolean {
    if (this !is WorldGenRegion || !inRegion(x, z)) return false
    return getChunk(x shr 4, z shr 4, ChunkStatus.STRUCTURE_REFERENCES).hasAnyStructureReferences()
}

/** Writes a block if the generating region allows it, whatever is there. */
private fun WorldGenLevel.setIfWritable(pos: BlockPos, state: BlockState): Boolean {
    if (!inRegion(pos.x, pos.z) || !ensureCanWrite(pos)) return false
    setBlock(pos, state, 2)
    return true
}

/** Removes the plants (and snow layers) at pos and the block above, if any. */
private fun WorldGenLevel.clearPlants(x: Int, y: Int, z: Int) {
    val pos = BlockPos.MutableBlockPos()
    for (dy in 0..1) {
        pos.set(x, y + dy, z)
        if (!inRegion(x, z)) return
        val there = getBlockState(pos)
        if (!there.isAir && there.canBeReplaced() && there.fluidState.isEmpty) setIfWritable(pos, Blocks.AIR.defaultBlockState())
    }
}

/** Writes a block if the generating region allows it and the place can take it (air, plants, fluids). */
private fun WorldGenLevel.placeSoft(pos: BlockPos, state: BlockState): Boolean {
    if (!inRegion(pos.x, pos.z) || !ensureCanWrite(pos)) return false
    val there = getBlockState(pos)
    if (!(there.isAir || there.canBeReplaced() || !there.fluidState.isEmpty)) return false
    setBlock(pos, state, 2)
    return true
}

/**
 * Giant impact basins (Ultra Crater): one crater per cell of cell_size blocks at most
 * (chance), its place and size drawn from the cell and the save's seed. Returns the
 * height change in the terrain's depth units (blocks / 128): the bowl below, the rim
 * raised around it. Two-dimensional: wrap it in minecraft:cache_2d. Minecraft has no
 * cellular noise, hence this function.
 */
class Craters(
    private val noise: DensityFunction.NoiseHolder,
    private val cellSize: Int,
    private val minRadius: Double,
    private val maxRadius: Double,
    private val depth: Double,
    private val rim: Double,
    private val chance: Double,
) : DensityFunction.SimpleFunction {
    /** The save's seed as the noise sees it (0 until the visitor gave it its noise). */
    private val salt: Long by lazy { (noise.getValue(0.5, 0.0, 0.5) * 1.0e9).toLong() xor 0x5DEECE66DL }

    private fun hash(x: Int, z: Int, k: Int): Double {
        var h = salt xor (x.toLong() * 0x9E3779B97F4A7C15uL.toLong()) xor (z.toLong() * -0x3d4d51c2d82b14b1L) xor (k.toLong() * 0x2545F4914F6CDD1DL)
        h = (h xor (h ushr 33)) * -0xae502812aa7333L
        h = (h xor (h ushr 33)) * -0x3b314601e57a13adL
        h = h xor (h ushr 33)
        return (h ushr 11).toDouble() / (1L shl 53).toDouble()
    }

    override fun compute(context: DensityFunction.FunctionContext): Double {
        val x = context.blockX().toDouble()
        val z = context.blockZ().toDouble()
        val cx = Math.floorDiv(context.blockX(), cellSize)
        val cz = Math.floorDiv(context.blockZ(), cellSize)
        var bowl = 0.0
        var raised = 0.0
        for (gx in cx - 1..cx + 1) for (gz in cz - 1..cz + 1) {
            if (hash(gx, gz, 0) > chance) continue
            val px = (gx + 0.2 + 0.6 * hash(gx, gz, 1)) * cellSize
            val pz = (gz + 0.2 + 0.6 * hash(gx, gz, 2)) * cellSize
            val r = minRadius + (maxRadius - minRadius) * hash(gx, gz, 3)
            val dx = x - px
            val dz = z - pz
            val t = sqrt(dx * dx + dz * dz) / r
            if (t > 1.8) continue
            val size = r / maxRadius
            if (t < 1.0) bowl = min(bowl, -depth * size * (1.0 - t * t))
            raised = max(raised, rim * size * exp(-((t - 1.0) / 0.22) * ((t - 1.0) / 0.22)))
        }
        return (bowl + raised) / 128.0
    }

    override fun mapAll(visitor: DensityFunction.Visitor): DensityFunction =
        visitor.apply(Craters(visitor.visitNoise(noise), cellSize, minRadius, maxRadius, depth, rim, chance))

    override fun minValue(): Double = -depth / 128.0

    override fun maxValue(): Double = rim / 128.0

    override fun codec(): KeyDispatchDataCodec<out DensityFunction> = CODEC

    companion object {
        val MAP_CODEC: MapCodec<Craters> = RecordCodecBuilder.mapCodec { i ->
            i.group(
                DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter { it.noise },
                Codec.intRange(32, 4096).fieldOf("cell_size").forGetter { it.cellSize },
                Codec.doubleRange(4.0, 1024.0).fieldOf("min_radius").forGetter { it.minRadius },
                Codec.doubleRange(4.0, 1024.0).fieldOf("max_radius").forGetter { it.maxRadius },
                Codec.doubleRange(0.0, 256.0).fieldOf("depth").forGetter { it.depth },
                Codec.doubleRange(0.0, 256.0).fieldOf("rim").forGetter { it.rim },
                Codec.doubleRange(0.0, 1.0).fieldOf("chance").forGetter { it.chance },
            ).apply(i, ::Craters)
        }
        val CODEC: KeyDispatchDataCodec<Craters> = KeyDispatchDataCodec.of(MAP_CODEC)
    }
}

/**
 * A small or medium impact crater on the surface: a bowl dug from the ground (its
 * floor of `floor`), a raised rim of `rim`, debris of `ejecta` thrown around. Radius
 * up to 11 (a feature stays within its chunk and the ones next to it).
 */
class CraterFeature(codec: Codec<Config>) : Feature<CraterFeature.Config>(codec) {
    class Config(
        val radius: IntProvider,
        val depth: Float,
        val floor: BlockStateProvider,
        val rim: BlockStateProvider,
        val ejecta: BlockStateProvider,
        val ejectaCount: Int,
    ) : FeatureConfiguration {
        companion object {
            val CODEC: Codec<Config> = RecordCodecBuilder.create { i ->
                i.group(
                    IntProvider.codec(2, 11).fieldOf("radius").forGetter { it.radius },
                    Codec.floatRange(0.1f, 1.5f).fieldOf("depth").forGetter { it.depth },
                    BlockStateProvider.CODEC.fieldOf("floor").forGetter { it.floor },
                    BlockStateProvider.CODEC.fieldOf("rim").forGetter { it.rim },
                    BlockStateProvider.CODEC.fieldOf("ejecta").forGetter { it.ejecta },
                    Codec.intRange(0, 64).fieldOf("ejecta_count").forGetter { it.ejectaCount },
                ).apply(i, ::Config)
            }
        }
    }

    override fun place(context: FeaturePlaceContext<Config>): Boolean {
        val level = context.level()
        val random = context.random()
        val config = context.config()
        val origin = context.origin()
        val r = config.radius.sample(random)
        val deepest = max(1, (r * config.depth).toInt())
        // Not into water, nor hanging over a drop, nor into a structure.
        if (!level.getFluidState(origin.below()).isEmpty || !level.getFluidState(origin).isEmpty) return false
        if (level.structureAt(origin.x, origin.z)) return false
        val reach = r + r / 3 + 1
        val pos = BlockPos.MutableBlockPos()
        for (dx in -reach..reach) for (dz in -reach..reach) {
            val t = sqrt((dx * dx + dz * dz).toDouble()) / r
            if (t > 1.0 + 1.0 / 3.0 + 0.2) continue
            val x = origin.x + dx
            val z = origin.z + dz
            val top = (level.surfaceAt(x, z) ?: continue) - 1
            if (abs(top - origin.y) > r) continue
            if (t < 1.0) {
                // The bowl: dug down to the curve, a floor below it.
                val bottom = origin.y - (deepest * (1.0 - t * t)).toInt()
                for (y in top downTo bottom + 1) {
                    pos.set(x, y, z)
                    if (level.ensureCanWrite(pos) && level.getFluidState(pos).isEmpty) level.setBlock(pos, AIR, 2)
                }
                pos.set(x, bottom, z)
                if (level.ensureCanWrite(pos) && !level.getBlockState(pos).isAir) level.setBlock(pos, config.floor.getState(random, pos), 2)
            } else {
                // The rim: raised, lower further out.
                val height = ((1.0 - (t - 1.0) * 3.0) * (1 + r / 4)).toInt()
                for (y in 1..height) level.placeSoft(pos.set(x, top + y, z), config.rim.getState(random, pos))
            }
        }
        repeat(config.ejectaCount) {
            val angle = random.nextDouble() * Math.PI * 2
            val distance = min(r * (1.4 + random.nextDouble() * 0.6), 15.0)
            val x = origin.x + (cos(angle) * distance).toInt()
            val z = origin.z + (sin(angle) * distance).toInt()
            val y = level.surfaceAt(x, z) ?: return@repeat
            level.placeSoft(pos.set(x, y, z), config.ejecta.getState(random, pos))
        }
        return true
    }

    companion object {
        private val AIR = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
    }
}

/**
 * Angular crystals (or glass, quartz, sharp leaves...): a cluster of tilted, pointed
 * prisms growing up from the ground, or down from a ceiling (hanging), with a core
 * block inside the thick ones. Minecraft's spikes are fixed to ice and obsidian.
 */
class CrystalSpikeFeature(codec: Codec<Config>) : Feature<CrystalSpikeFeature.Config>(codec) {
    class Config(
        val block: BlockStateProvider,
        val core: BlockStateProvider,
        val height: IntProvider,
        val radius: IntProvider,
        val count: IntProvider,
        val tilt: Float,
        val hanging: Boolean,
    ) : FeatureConfiguration {
        companion object {
            val CODEC: Codec<Config> = RecordCodecBuilder.create { i ->
                i.group(
                    BlockStateProvider.CODEC.fieldOf("block").forGetter { it.block },
                    BlockStateProvider.CODEC.fieldOf("core").forGetter { it.core },
                    IntProvider.codec(1, 24).fieldOf("height").forGetter { it.height },
                    IntProvider.codec(0, 4).fieldOf("radius").forGetter { it.radius },
                    IntProvider.codec(1, 8).fieldOf("count").forGetter { it.count },
                    Codec.floatRange(0f, 0.9f).fieldOf("tilt").forGetter { it.tilt },
                    Codec.BOOL.optionalFieldOf("hanging", false).forGetter { it.hanging },
                ).apply(i, ::Config)
            }
        }
    }

    override fun place(context: FeaturePlaceContext<Config>): Boolean {
        val level = context.level()
        val random = context.random()
        val config = context.config()
        val origin = context.origin()
        val up = if (config.hanging) -1 else 1
        // Standing on (or hanging from) something solid, away from the structures.
        if (!level.getBlockState(origin.below(up)).isSolidRender(level, origin.below(up))) return false
        if (level.structureAt(origin.x, origin.z)) return false
        val pos = BlockPos.MutableBlockPos()
        var placed = false
        repeat(config.count.sample(random)) {
            val bx = origin.x + random.nextInt(5) - 2
            val bz = origin.z + random.nextInt(5) - 2
            val height = config.height.sample(random)
            val base = config.radius.sample(random) + 0.5
            val angle = random.nextDouble() * Math.PI * 2
            val lean = random.nextDouble() * config.tilt
            val sx = cos(angle) * lean
            val sz = sin(angle) * lean
            for (s in -1..height) {
                val fraction = s.coerceAtLeast(0).toDouble() / height
                val radius = base * (1.0 - fraction) + 0.3
                val cx = bx + sx * s
                val cz = bz + sz * s
                val y = origin.y + s * up
                val ri = radius.toInt() + 1
                for (dx in -ri..ri) for (dz in -ri..ri) {
                    val px = floor(cx).toInt() + dx
                    val pz = floor(cz).toInt() + dz
                    val d = sqrt((px + 0.5 - cx) * (px + 0.5 - cx) + (pz + 0.5 - cz) * (pz + 0.5 - cz))
                    if (d > radius) continue
                    pos.set(px, y, pz)
                    val state = if (d < radius - 1.2) config.core.getState(random, pos) else config.block.getState(random, pos)
                    if (s < 0) {
                        // The root, into the ground.
                        if (level.inRegion(px, pz) && level.ensureCanWrite(pos)) level.setBlock(pos, state, 2)
                    } else if (level.placeSoft(pos, state)) placed = true
                }
            }
        }
        return placed
    }
}

/**
 * Twisted pillars like giant cables: strands winding round a vertical axis, with
 * glowing accents every few blocks, sometimes bent into an arch reaching down to the
 * ground. Minecraft only makes straight columns.
 */
class TwistedColumnFeature(codec: Codec<Config>) : Feature<TwistedColumnFeature.Config>(codec) {
    class Config(
        val block: BlockStateProvider,
        val accent: BlockStateProvider,
        val height: IntProvider,
        val strands: IntProvider,
        val turns: Float,
        val archChance: Float,
    ) : FeatureConfiguration {
        companion object {
            val CODEC: Codec<Config> = RecordCodecBuilder.create { i ->
                i.group(
                    BlockStateProvider.CODEC.fieldOf("block").forGetter { it.block },
                    BlockStateProvider.CODEC.fieldOf("accent").forGetter { it.accent },
                    IntProvider.codec(4, 32).fieldOf("height").forGetter { it.height },
                    IntProvider.codec(1, 4).fieldOf("strands").forGetter { it.strands },
                    Codec.floatRange(0f, 4f).fieldOf("turns").forGetter { it.turns },
                    Codec.floatRange(0f, 1f).fieldOf("arch_chance").forGetter { it.archChance },
                ).apply(i, ::Config)
            }
        }
    }

    override fun place(context: FeaturePlaceContext<Config>): Boolean {
        val level = context.level()
        val random = context.random()
        val config = context.config()
        val origin = context.origin()
        if (!level.getBlockState(origin.below()).isSolidRender(level, origin.below())) return false
        if (level.structureAt(origin.x, origin.z)) return false
        val height = config.height.sample(random)
        val strands = config.strands.sample(random)
        val pos = BlockPos.MutableBlockPos()
        val start = random.nextDouble() * Math.PI * 2
        fun put(x: Int, y: Int, z: Int, step: Int) {
            pos.set(x, y, z)
            val state = if (step % 5 == 0) config.accent.getState(random, pos) else config.block.getState(random, pos)
            level.placeSoft(pos, state)
        }
        for (y in 0 until height) {
            for (k in 0 until strands) {
                val angle = start + config.turns * 2 * Math.PI * y / height + k * 2 * Math.PI / strands
                val x = origin.x + Mth.floor(cos(angle) * 1.6 + 0.5)
                val z = origin.z + Mth.floor(sin(angle) * 1.6 + 0.5)
                put(x, origin.y + y, z, y + k)
                if (y < 3) put(x, origin.y + y, z + 1, y)
            }
            put(origin.x, origin.y + y, origin.z, y + 2)
        }
        // An arch from the top down to the ground, 6 to 12 blocks away.
        if (random.nextFloat() < config.archChance) {
            val angle = random.nextDouble() * Math.PI * 2
            val length = 6 + random.nextInt(7)
            for (i in 0..length * 2) {
                val f = i / (length * 2.0)
                val x = origin.x + Mth.floor(cos(angle) * length * f)
                val z = origin.z + Mth.floor(sin(angle) * length * f)
                val ground = level.surfaceAt(x, z) ?: break
                val y = Mth.floor(Mth.lerp(f, origin.y + height.toDouble(), ground.toDouble()) + sin(f * Math.PI) * 3)
                put(x, y, z, i)
                put(x, y + 1, z, i + 1)
            }
        }
        return true
    }
}

/**
 * A zen garden (Ultra Forest): a round clearing of raked sand, its rings drawn round
 * one to four pointed rocks, edged with moss. Radius up to 12 (within the generating
 * region); only on fairly level ground, tree trunks left standing.
 */
class ZenGardenFeature(codec: Codec<Config>) : Feature<ZenGardenFeature.Config>(codec) {
    class Config(
        val sand: BlockStateProvider,
        val groove: BlockStateProvider,
        val edge: BlockStateProvider,
        val rock: BlockStateProvider,
        val scatter: BlockStateProvider,
        val radius: IntProvider,
        val rocks: IntProvider,
        val rockHeight: IntProvider,
    ) : FeatureConfiguration {
        companion object {
            val CODEC: Codec<Config> = RecordCodecBuilder.create { i ->
                i.group(
                    BlockStateProvider.CODEC.fieldOf("sand").forGetter { it.sand },
                    BlockStateProvider.CODEC.fieldOf("groove").forGetter { it.groove },
                    BlockStateProvider.CODEC.fieldOf("edge").forGetter { it.edge },
                    BlockStateProvider.CODEC.fieldOf("rock").forGetter { it.rock },
                    BlockStateProvider.CODEC.fieldOf("scatter").forGetter { it.scatter },
                    IntProvider.codec(4, 12).fieldOf("radius").forGetter { it.radius },
                    IntProvider.codec(1, 4).fieldOf("rocks").forGetter { it.rocks },
                    IntProvider.codec(2, 12).fieldOf("rock_height").forGetter { it.rockHeight },
                ).apply(i, ::Config)
            }
        }
    }

    override fun place(context: FeaturePlaceContext<Config>): Boolean {
        val level = context.level()
        val random = context.random()
        val config = context.config()
        val origin = context.origin()
        val ground = origin.below()
        if (!level.inRegion(origin.x, origin.z) || level.structureAt(origin.x, origin.z)) return false
        if (!level.getFluidState(origin).isEmpty || !level.getFluidState(ground).isEmpty) return false
        if (!level.getBlockState(ground).isSolidRender(level, ground)) return false
        val base = ground.y
        val r = config.radius.sample(random)
        val rocks = List(config.rocks.sample(random)) {
            val angle = random.nextDouble() * Math.PI * 2
            val distance = random.nextDouble() * r * 0.45
            doubleArrayOf(origin.x + 0.5 + cos(angle) * distance, origin.z + 0.5 + sin(angle) * distance)
        }
        val phase = random.nextDouble() * Math.PI * 2
        val pos = BlockPos.MutableBlockPos()
        var placed = false
        for (dx in -r..r) for (dz in -r..r) {
            val x = origin.x + dx
            val z = origin.z + dz
            val d = sqrt((dx * dx + dz * dz).toDouble())
            val outline = r * (0.86 + 0.14 * sin(atan2(dz.toDouble(), dx.toDouble()) * 3 + phase))
            if (d > outline) continue
            val top = (level.surfaceAt(x, z) ?: continue) - 1
            if (abs(top - base) > 2) continue
            if (level.getBlockState(pos.set(x, top, z)).`is`(BlockTags.LOGS)) continue
            // The ground levelled at the garden's height, the plants away.
            for (y in base + 1..top) level.setIfWritable(pos.set(x, y, z), AIR)
            level.clearPlants(x, max(base, top) + 1, z)
            for (y in top + 1 until base) level.setIfWritable(pos.set(x, y, z), config.sand.getState(random, pos))
            pos.set(x, base, z)
            val near = rocks.minOf { sqrt((x + 0.5 - it[0]).pow(2) + (z + 0.5 - it[1]).pow(2)) }
            val state = when {
                d > outline - 1.2 -> config.edge.getState(random, pos)
                near > 1.5 && near.toInt() % 2 == 0 -> config.groove.getState(random, pos)
                else -> config.sand.getState(random, pos)
            }
            if (level.setIfWritable(pos, state)) placed = true
            if (d <= outline - 1.2 && random.nextFloat() < 0.025f) level.placeSoft(pos.set(x, base + 1, z), config.scatter.getState(random, pos))
        }
        if (!placed) return false
        // The rocks: pointed, leaning a little, rooted in the sand.
        for (rock in rocks) {
            val height = config.rockHeight.sample(random)
            val width = 1.0 + random.nextDouble() * 1.2
            val lean = random.nextDouble() * 0.25
            val angle = random.nextDouble() * Math.PI * 2
            for (s in -1..height) {
                val radius = width * (1.0 - s.coerceAtLeast(0).toDouble() / height) + 0.35
                val cx = rock[0] + cos(angle) * lean * s
                val cz = rock[1] + sin(angle) * lean * s
                val ri = radius.toInt() + 1
                for (ox in -ri..ri) for (oz in -ri..ri) {
                    val px = floor(cx).toInt() + ox
                    val pz = floor(cz).toInt() + oz
                    if ((px + 0.5 - cx).pow(2) + (pz + 0.5 - cz).pow(2) > radius * radius) continue
                    level.setIfWritable(pos.set(px, base + s, pz), config.rock.getState(random, pos))
                }
            }
        }
        return true
    }

    companion object {
        private val AIR = Blocks.AIR.defaultBlockState()
    }
}

/**
 * A steam vent (Ultra Crater): a signal campfire on a hay bale (its smoke rises high)
 * sunk in the ground inside a ring of rock, a stain of `ground` around. Campfires are
 * in ultra_arrival_unsafe: arrivals keep away from the fire.
 */
class SteamVentFeature(codec: Codec<Config>) : Feature<SteamVentFeature.Config>(codec) {
    class Config(val rim: BlockStateProvider, val ground: BlockStateProvider, val radius: IntProvider) : FeatureConfiguration {
        companion object {
            val CODEC: Codec<Config> = RecordCodecBuilder.create { i ->
                i.group(
                    BlockStateProvider.CODEC.fieldOf("rim").forGetter { it.rim },
                    BlockStateProvider.CODEC.fieldOf("ground").forGetter { it.ground },
                    IntProvider.codec(0, 6).fieldOf("radius").forGetter { it.radius },
                ).apply(i, ::Config)
            }
        }
    }

    override fun place(context: FeaturePlaceContext<Config>): Boolean {
        val level = context.level()
        val random = context.random()
        val config = context.config()
        val origin = context.origin()
        val below = origin.below()
        if (!level.inRegion(origin.x, origin.z) || level.structureAt(origin.x, origin.z)) return false
        if (!level.getFluidState(origin).isEmpty || !level.getFluidState(below).isEmpty) return false
        if (!level.getBlockState(below).isSolidRender(level, below)) return false
        val base = below.y
        val pos = BlockPos.MutableBlockPos()
        val r = config.radius.sample(random)
        for (dx in -r - 1..r + 1) for (dz in -r - 1..r + 1) {
            if (sqrt((dx * dx + dz * dz).toDouble()) > r + random.nextDouble() * 0.9) continue
            val x = origin.x + dx
            val z = origin.z + dz
            val top = (level.surfaceAt(x, z) ?: continue) - 1
            if (abs(top - base) > 2) continue
            pos.set(x, top, z)
            if (level.getBlockState(pos).isSolidRender(level, pos)) level.setIfWritable(pos, config.ground.getState(random, pos))
        }
        // The mouth: a ring of rock, higher on some sides.
        for (dx in -1..1) for (dz in -1..1) {
            if (dx == 0 && dz == 0) continue
            level.setIfWritable(pos.set(origin.x + dx, base, origin.z + dz), config.rim.getState(random, pos))
            if (random.nextFloat() < 0.65f) level.placeSoft(pos.set(origin.x + dx, base + 1, origin.z + dz), config.rim.getState(random, pos))
        }
        level.setIfWritable(pos.set(origin.x, base - 1, origin.z), Blocks.HAY_BLOCK.defaultBlockState())
        level.setIfWritable(pos.set(origin.x, base, origin.z), SIGNAL.setValue(CampfireBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)))
        level.clearPlants(origin.x, base + 1, origin.z)
        return true
    }

    companion object {
        val SIGNAL: BlockState = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true)
    }
}

/**
 * A block of the overgrown city (Ultra Skyscraper), one per chunk. The streets run
 * along the chunk's north and west edges (3 wide, a dashed line in the middle) with a
 * sidewalk; every chunk lays them the same way, so they meet into one grid across the
 * biomes. On the 11 x 11 lot: a building (walls and corners of a palette, a window
 * grid, floors every 4 blocks, shop fronts and an entrance, a roof garden or a
 * smoking chimney, vines down the walls; broken windows, holes, sometimes its top
 * broken off) or a garden (grass, a pond, trees). Placed at the chunk's corner (no
 * in_square), it never leaves its chunk; it skips chunks under water.
 */
class CityBlockFeature(codec: Codec<Config>) : Feature<CityBlockFeature.Config>(codec) {
    class Palette(val wall: BlockStateProvider, val accent: BlockStateProvider, val window: BlockStateProvider, val floor: BlockStateProvider) {
        companion object {
            val CODEC: Codec<Palette> = RecordCodecBuilder.create { i ->
                i.group(
                    BlockStateProvider.CODEC.fieldOf("wall").forGetter { it.wall },
                    BlockStateProvider.CODEC.fieldOf("accent").forGetter { it.accent },
                    BlockStateProvider.CODEC.fieldOf("window").forGetter { it.window },
                    BlockStateProvider.CODEC.fieldOf("floor").forGetter { it.floor },
                ).apply(i, ::Palette)
            }
        }
    }

    class Streets(val road: BlockStateProvider, val line: BlockStateProvider, val sidewalk: BlockStateProvider, val fireChance: Float) {
        companion object {
            val CODEC: Codec<Streets> = RecordCodecBuilder.create { i ->
                i.group(
                    BlockStateProvider.CODEC.fieldOf("road").forGetter { it.road },
                    BlockStateProvider.CODEC.fieldOf("line").forGetter { it.line },
                    BlockStateProvider.CODEC.fieldOf("sidewalk").forGetter { it.sidewalk },
                    Codec.floatRange(0f, 1f).optionalFieldOf("fire_chance", 0f).forGetter { it.fireChance },
                ).apply(i, ::Streets)
            }
        }
    }

    class Ruin(val brokenWindows: Float, val holes: Float, val collapse: Float, val rubble: BlockStateProvider) {
        companion object {
            val CODEC: Codec<Ruin> = RecordCodecBuilder.create { i ->
                i.group(
                    Codec.floatRange(0f, 1f).fieldOf("broken_windows").forGetter { it.brokenWindows },
                    Codec.floatRange(0f, 1f).fieldOf("holes").forGetter { it.holes },
                    Codec.floatRange(0f, 1f).fieldOf("collapse").forGetter { it.collapse },
                    BlockStateProvider.CODEC.fieldOf("rubble").forGetter { it.rubble },
                ).apply(i, ::Ruin)
            }
        }
    }

    class Config(
        val streets: Streets,
        val palettes: List<Palette>,
        val height: IntProvider,
        val buildingChance: Float,
        val ground: BlockStateProvider,
        val roof: BlockStateProvider,
        val roofGarden: Float,
        val vines: Float,
        val chimney: Float,
        val pond: Float,
        val ruin: Ruin,
        val vegetation: Optional<Holder<PlacedFeature>>,
    ) : FeatureConfiguration {
        companion object {
            val CODEC: Codec<Config> = RecordCodecBuilder.create { i ->
                i.group(
                    Streets.CODEC.fieldOf("streets").forGetter { it.streets },
                    ExtraCodecs.nonEmptyList(Palette.CODEC.listOf()).fieldOf("palettes").forGetter { it.palettes },
                    IntProvider.codec(4, 120).fieldOf("height").forGetter { it.height },
                    Codec.floatRange(0f, 1f).fieldOf("building_chance").forGetter { it.buildingChance },
                    BlockStateProvider.CODEC.fieldOf("ground").forGetter { it.ground },
                    BlockStateProvider.CODEC.fieldOf("roof").forGetter { it.roof },
                    Codec.floatRange(0f, 1f).fieldOf("roof_garden").forGetter { it.roofGarden },
                    Codec.floatRange(0f, 1f).fieldOf("vines").forGetter { it.vines },
                    Codec.floatRange(0f, 1f).fieldOf("chimney").forGetter { it.chimney },
                    Codec.floatRange(0f, 1f).fieldOf("pond").forGetter { it.pond },
                    Ruin.CODEC.fieldOf("ruin").forGetter { it.ruin },
                    PlacedFeature.CODEC.optionalFieldOf("vegetation").forGetter { it.vegetation },
                ).apply(i, ::Config)
            }
        }
    }

    /** One chunk's work: its corner, the ground height of each column, the random. */
    private class Lot(val level: WorldGenLevel, val context: FeaturePlaceContext<Config>, val x0: Int, val z0: Int, val tops: IntArray) {
        val random: RandomSource = context.random()
        val config: Config = context.config()
        val pos = BlockPos.MutableBlockPos()

        fun top(lx: Int, lz: Int) = tops[lx + lz * 16]

        fun dry(lx: Int, lz: Int) = level.getFluidState(pos.set(x0 + lx, top(lx, lz), z0 + lz)).isEmpty

        fun set(lx: Int, y: Int, lz: Int, state: BlockState) = level.setIfWritable(pos.set(x0 + lx, y, z0 + lz), state)

        fun set(lx: Int, y: Int, lz: Int, provider: BlockStateProvider) = set(lx, y, lz, provider.getState(random, pos.set(x0 + lx, y, z0 + lz)))

        fun isAir(lx: Int, y: Int, lz: Int) = level.getBlockState(pos.set(x0 + lx, y, z0 + lz)).isAir

        /** The vegetation feature (a tree, a bush...) standing at (lx, y, lz). */
        fun grow(lx: Int, y: Int, lz: Int) {
            val feature = config.vegetation.orElse(null) ?: return
            feature.value().place(level, context.chunkGenerator(), random, BlockPos(x0 + lx, y, z0 + lz))
        }
    }

    override fun place(context: FeaturePlaceContext<Config>): Boolean {
        val level = context.level()
        val x0 = context.origin().x and -16
        val z0 = context.origin().z and -16
        // A structure stands in this chunk (the ruined towers, the small sites): the lot is its.
        if (!level.inRegion(x0, z0) || level.structureAt(x0, z0)) return false
        val tops = IntArray(256)
        val pos = BlockPos.MutableBlockPos()
        var wet = 0
        for (i in 0 until 256) {
            val x = x0 + (i and 15)
            val z = z0 + (i shr 4)
            tops[i] = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1
            if (!level.getFluidState(pos.set(x, tops[i], z)).isEmpty) wet++
        }
        if (wet > 64) return false
        val lot = Lot(level, context, x0, z0, tops)
        streets(lot)
        var sum = 0
        var n = 0
        for (lx in F0..F1) for (lz in F0..F1) if (lot.dry(lx, lz)) {
            sum += lot.top(lx, lz)
            n++
        }
        if (n < 60) return true
        val base = Math.round(sum.toFloat() / n)
        if (lot.random.nextFloat() < lot.config.buildingChance) building(lot, base) else garden(lot)
        // A tree on the sidewalk, sometimes.
        if (lot.random.nextFloat() < 0.35f) {
            val along = 5 + lot.random.nextInt(8)
            val (lx, lz) = if (lot.random.nextBoolean()) ROAD to along else along to ROAD
            if (lot.dry(lx, lz) && lot.isAir(lx, lot.top(lx, lz) + 1, lz)) lot.grow(lx, lot.top(lx, lz) + 1, lz)
        }
        return true
    }

    private fun streets(lot: Lot) {
        val streets = lot.config.streets
        for (lx in 0..15) for (lz in 0..15) {
            val road = lx < ROAD || lz < ROAD
            val walk = !road && (lx == ROAD || lz == ROAD || lx == 15 || lz == 15)
            if (!(road || walk) || !lot.dry(lx, lz)) continue
            val x = lot.x0 + lx
            val z = lot.z0 + lz
            val y = lot.top(lx, lz)
            val provider = when {
                walk -> streets.sidewalk
                lz == ROAD / 2 && lx >= ROAD && (x and 3) < 2 -> streets.line
                lx == ROAD / 2 && lz >= ROAD && (z and 3) < 2 -> streets.line
                else -> streets.road
            }
            lot.set(lx, y, lz, provider)
            lot.level.clearPlants(x, y + 1, z)
        }
        // A fire burning on the road (ruins).
        if (lot.random.nextFloat() < streets.fireChance) {
            val along = ROAD + lot.random.nextInt(16 - ROAD)
            val (lx, lz) = if (lot.random.nextBoolean()) lot.random.nextInt(ROAD) to along else along to lot.random.nextInt(ROAD)
            if (lot.dry(lx, lz)) lot.level.placeSoft(lot.pos.set(lot.x0 + lx, lot.top(lx, lz) + 1, lot.z0 + lz), Blocks.CAMPFIRE.defaultBlockState())
        }
    }

    private fun building(lot: Lot, base: Int) {
        val random = lot.random
        val config = lot.config
        val ruin = config.ruin
        val palette = config.palettes[random.nextInt(config.palettes.size)]
        val height = max(4, config.height.sample(random) / 4 * 4)
        val broken = random.nextFloat() < ruin.collapse
        val cut = if (broken) max(4, (height * (0.3 + random.nextDouble() * 0.5)).toInt()) else height
        val entrance = random.nextInt(4)
        fun window(lx: Int, y: Int, lz: Int): BlockState =
            if (random.nextFloat() < ruin.brokenWindows) AIR else palette.window.getState(random, lot.pos.set(lot.x0 + lx, y, lot.z0 + lz))
        // The ground levelled under the building.
        for (lx in F0..F1) for (lz in F0..F1) {
            val t = lot.top(lx, lz)
            for (y in t + 1 until base) lot.set(lx, y, lz, palette.floor)
            for (y in base + 1..t) lot.set(lx, y, lz, AIR)
            lot.level.clearPlants(lot.x0 + lx, max(base, t) + 1, lot.z0 + lz)
            lot.set(lx, base, lz, palette.floor)
        }
        val tops = IntArray(121)
        for (lx in F0..F1) for (lz in F0..F1) {
            val edgeX = lx == F0 || lx == F1
            val edgeZ = lz == F0 || lz == F1
            val edge = edgeX || edgeZ
            val corner = edgeX && edgeZ
            val along = if (edgeX) lz - F0 else lx - F0
            val side = when {
                lz == F0 -> 0
                lx == F1 -> 1
                lz == F1 -> 2
                else -> 3
            }
            val columnTop = if (broken) max(2, cut - random.nextInt(5)) else height
            tops[(lx - F0) + (lz - F0) * 11] = columnTop
            for (rel in 1..columnTop) {
                val y = base + rel
                val floorLevel = rel % 4 == 0
                val here = lot.pos.set(lot.x0 + lx, y, lot.z0 + lz)
                val state: BlockState = if (edge) {
                    when {
                        corner || floorLevel -> palette.accent.getState(random, here)
                        rel <= 3 && side == entrance && along in 4..6 -> AIR
                        rel <= 3 -> if (along % 3 == 0) palette.wall.getState(random, here) else window(lx, y, lz)
                        rel % 4 != 1 && along % 3 != 0 -> window(lx, y, lz)
                        else -> palette.wall.getState(random, here)
                    }
                } else if (floorLevel) {
                    palette.floor.getState(random, here)
                } else continue
                // Holes in the walls and floors (fewer near the ground; never the corners).
                if (!state.isAir && !corner && random.nextFloat() < ruin.holes * (if (rel < 4) 0.3f else 1f)) continue
                lot.set(lx, y, lz, state)
            }
        }
        if (!broken) roof(lot, palette, base + height)
        vines(lot, base, tops)
        if (broken) {
            // Fallen pieces in the ruin and on the sidewalks.
            repeat(8 + random.nextInt(12)) {
                val lx = ROAD + random.nextInt(16 - ROAD)
                val lz = ROAD + random.nextInt(16 - ROAD)
                val ground = lot.level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lot.x0 + lx, lot.z0 + lz)
                repeat(1 + random.nextInt(2)) { dy ->
                    lot.level.placeSoft(lot.pos.set(lot.x0 + lx, ground + dy, lot.z0 + lz), ruin.rubble.getState(random, lot.pos))
                }
            }
        }
    }

    /** The roof at y: a parapet round a garden or a covering, sometimes a chimney. */
    private fun roof(lot: Lot, palette: Palette, y: Int) {
        val random = lot.random
        val config = lot.config
        val garden = random.nextFloat() < config.roofGarden
        for (lx in F0..F1) for (lz in F0..F1) {
            val edge = lx == F0 || lx == F1 || lz == F0 || lz == F1
            if (edge) {
                lot.set(lx, y + 1, lz, palette.accent)
                if (!garden) continue
                lot.set(lx, y + 2, lz, palette.accent)
            } else {
                lot.set(lx, y + 1, lz, if (garden) config.ground else config.roof)
            }
        }
        if (garden) repeat(1 + random.nextInt(3)) {
            lot.grow(F0 + 2 + random.nextInt(7), y + 2, F0 + 2 + random.nextInt(7))
        } else if (random.nextFloat() < config.chimney) {
            val lx = F0 + 1 + random.nextInt(2) * 8
            val lz = F0 + 1 + random.nextInt(2) * 8
            val top = y + 2 + 4 + random.nextInt(9)
            for (cy in y + 2 until top) lot.set(lx, cy, lz, palette.accent)
            lot.set(lx, top, lz, Blocks.HAY_BLOCK.defaultBlockState())
            lot.set(lx, top + 1, lz, SteamVentFeature.SIGNAL)
        }
    }

    /** Vines hanging down the walls from a sturdy wall block, to the ground. */
    private fun vines(lot: Lot, base: Int, tops: IntArray) {
        val random = lot.random
        val chance = lot.config.vines
        if (chance <= 0f) return
        for (lx in F0..F1) for (lz in F0..F1) {
            val out = when {
                lz == F0 -> Direction.NORTH
                lx == F1 -> Direction.EAST
                lz == F1 -> Direction.SOUTH
                lx == F0 -> Direction.WEST
                else -> continue
            }
            if ((lx == F0 || lx == F1) && (lz == F0 || lz == F1)) continue
            if (random.nextFloat() >= chance) continue
            val columnTop = tops[(lx - F0) + (lz - F0) * 11]
            val start = base + 2 + random.nextInt(max(1, columnTop - 2))
            val wall = lot.pos.set(lot.x0 + lx, start, lot.z0 + lz)
            if (!lot.level.getBlockState(wall).isFaceSturdy(lot.level, wall, out)) continue
            val vine = VINES.getValue(out)
            val vx = lx + out.stepX
            val vz = lz + out.stepZ
            for (y in start downTo base + 1) {
                if (!lot.isAir(vx, y, vz)) break
                lot.set(vx, y, vz, vine)
            }
        }
    }

    /** A garden lot: the ground, a pond sometimes, a few trees. */
    private fun garden(lot: Lot) {
        val random = lot.random
        val config = lot.config
        for (lx in F0..F1) for (lz in F0..F1) if (lot.dry(lx, lz)) {
            lot.set(lx, lot.top(lx, lz), lz, config.ground)
            lot.level.clearPlants(lot.x0 + lx, lot.top(lx, lz) + 1, lot.z0 + lz)
        }
        val pond = BooleanArray(256)
        if (random.nextFloat() < config.pond) {
            val rx = 2.0 + random.nextInt(3)
            val rz = 2.0 + random.nextInt(3)
            fun inside(lx: Int, lz: Int) = ((lx - 9) / rx).pow(2) + ((lz - 9) / rz).pow(2)
            var water = Int.MAX_VALUE
            // The water's level: the lowest ground of the pond and of the ring around it.
            for (lx in F0..F1) for (lz in F0..F1) if (inside(lx, lz) < 2.6) water = min(water, lot.top(lx, lz))
            for (lx in F0..F1) for (lz in F0..F1) {
                val k = inside(lx, lz)
                if (k >= 1.0) continue
                pond[lx + lz * 16] = true
                for (y in water + 1..lot.top(lx, lz)) lot.set(lx, y, lz, AIR)
                lot.set(lx, water, lz, WATER)
                if (k < 0.35) {
                    lot.set(lx, water - 1, lz, WATER)
                    lot.set(lx, water - 2, lz, Blocks.CLAY.defaultBlockState())
                } else {
                    lot.set(lx, water - 1, lz, Blocks.CLAY.defaultBlockState())
                }
            }
        }
        repeat(2 + random.nextInt(4)) {
            val lx = F0 + random.nextInt(11)
            val lz = F0 + random.nextInt(11)
            if (!pond[lx + lz * 16] && lot.dry(lx, lz)) lot.grow(lx, lot.top(lx, lz) + 1, lz)
        }
    }

    companion object {
        /** The streets' width; the same everywhere, so that they meet. */
        const val ROAD = 3
        /** The lot inside the sidewalks (local coordinates). */
        const val F0 = ROAD + 1
        const val F1 = 14
        private val AIR = Blocks.AIR.defaultBlockState()
        private val WATER = Blocks.WATER.defaultBlockState()
        /** A vine hanging on the outer face of a wall, by the face's direction. */
        private val VINES: Map<Direction, BlockState> = Direction.Plane.HORIZONTAL.associateWith {
            Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(it.opposite), true)
        }
    }
}

/**
 * A world's ores and materials (phase 4.3.2), once per chunk: the veins listed for the
 * world in ultra_resources.json (UltraResourcesConfig), each tried as many times as its
 * count, the world's factor and the biome's say, at a height of its band, laid by
 * Minecraft's own vein makers. Placed at the chunk's corner, without placement
 * modifiers: counts and heights are the config's, read again at each chunk, so that a
 * reloaded config applies to the chunks generated from then on.
 */
class UltraResourcesFeature(codec: Codec<Config>) : Feature<UltraResourcesFeature.Config>(codec) {
    class Config(val world: String) : FeatureConfiguration {
        companion object {
            val CODEC: Codec<Config> = RecordCodecBuilder.create { i ->
                i.group(Codec.STRING.fieldOf("world").forGetter { it.world }).apply(i, ::Config)
            }
        }
    }

    override fun place(context: FeaturePlaceContext<Config>): Boolean {
        val veins = UltraResourcesConfig.veins(context.config().world)
        if (veins.isEmpty()) return false
        val level = context.level()
        val random = context.random()
        val x0 = context.origin().x and -16
        val z0 = context.origin().z and -16
        if (!level.inRegion(x0, z0)) return false
        // The chunk's biome (the same at every height in these worlds), as "ultra_crater/steam_fields".
        val biome = level.getBiome(BlockPos(x0 + 8, 64, z0 + 8)).unwrapKey().map { it.location().path }.orElse("")
        var placed = false
        for (vein in veins) {
            val expected = vein.tries(biome)
            if (expected <= 0f) continue
            if (vein.chance < 1f && random.nextFloat() >= vein.chance) continue
            var tries = expected.toInt()
            if (random.nextFloat() < expected - tries) tries++
            repeat(tries) {
                val pos = BlockPos(x0 + random.nextInt(16), vein.y(random), z0 + random.nextInt(16))
                if (pos.y < level.minBuildHeight || pos.y >= level.maxBuildHeight) return@repeat
                val maker = if (vein.scattered) Feature.SCATTERED_ORE else Feature.ORE
                if (maker.place(FeaturePlaceContext(Optional.empty(), level, context.chunkGenerator(), random, pos, vein.ore))) placed = true
            }
        }
        return placed
    }
}

/** Touches the registries (their entries are created with the objects). */
object UltraWorldgen {
    fun init() {
        UltraWorldFeatures.init()
        UltraDensityFunctions.init()
        UltraStructurePlacements.init()
        UltraStructureTypes.init()
    }
}
