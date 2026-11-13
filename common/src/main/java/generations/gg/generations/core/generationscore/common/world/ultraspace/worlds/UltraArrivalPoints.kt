package generations.gg.generations.core.generationscore.common.world.ultraspace.worlds

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A world's arrival points, saved with that world (its data folder), so that players
 * do not all land on one spot:
 * - each point's ideal place comes from the save's seed, the world and the point's
 *   number (a spiral out to arrival.spread): reproducible;
 * - near it, the world's generator is asked where there is land (no water over it, not
 *   too steep), without loading any chunk: the point's anchor, kept once found;
 * - the safe spot found there (UltraArrivalSearch, once its chunks are loaded) is kept
 *   too, with how often it was used; a point found unsafe (a build, a precipice...)
 *   waits retryMinutes before being tried again.
 * Later phases can add protection around the points (protectionRadius).
 */
class UltraArrivalPoints : SavedData() {
    class Point(val index: Int) {
        var anchorX = 0
        var anchorZ = 0
        /** The anchor was worked out (anchorX/Z), or no land was found near the ideal place. */
        var anchored = false
        var noLand = false
        /** The last safe spot found at this point. */
        var spot: BlockPos? = null
        var uses = 0
        /** Epoch milliseconds before which the point is not tried again, and why. */
        var blockedUntil = 0L
        var reason = ""
        /** For later phases: no building this close to the point (not used yet). */
        var protectionRadius = 0

        val blocked: Boolean get() = noLand || blockedUntil > System.currentTimeMillis()

        fun save(): CompoundTag = CompoundTag().apply {
            putInt("index", index)
            putBoolean("anchored", anchored)
            putBoolean("noLand", noLand)
            putInt("x", anchorX)
            putInt("z", anchorZ)
            spot?.let { put("spot", NbtUtils.writeBlockPos(it)) }
            putInt("uses", uses)
            putLong("blockedUntil", blockedUntil)
            putString("reason", reason)
            putInt("protectionRadius", protectionRadius)
        }

        companion object {
            fun load(tag: CompoundTag) = Point(tag.getInt("index")).apply {
                anchored = tag.getBoolean("anchored")
                noLand = tag.getBoolean("noLand")
                anchorX = tag.getInt("x")
                anchorZ = tag.getInt("z")
                spot = NbtUtils.readBlockPos(tag, "spot").orElse(null)
                uses = tag.getInt("uses")
                blockedUntil = tag.getLong("blockedUntil")
                reason = tag.getString("reason")
                protectionRadius = tag.getInt("protectionRadius")
            }
        }
    }

    val points = ArrayList<Point>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.put("points", ListTag().apply { points.forEach { add(it.save()) } })
        return tag
    }

    /** As many points as the config asks for (never fewer: those found are kept). */
    private fun ensure() {
        val count = UltraWorldsConfig.settings.arrival.points
        while (points.size < count) points.add(Point(points.size))
    }

    /**
     * This journey's points, in the order to try them: from one drawn by the journey's
     * id (players spread over the points), those not waiting after a refusal.
     */
    fun order(journey: UUID): List<Point> {
        if (points.isEmpty()) return emptyList()
        val start = Math.floorMod(journey.hashCode(), points.size)
        return (points.indices).map { points[(start + it) % points.size] }.filter { !it.blocked }
    }

    /** The point's anchor: land near its ideal place, as the generator makes it; null when there is none. */
    fun anchor(level: ServerLevel, point: Point): Pair<Int, Int>? {
        if (point.noLand) return null
        if (!point.anchored) {
            val (x, z) = ideal(level, point.index)
            val land = land(level, x, z, UltraWorldsConfig.settings.arrival.landSearch)
            point.anchored = true
            if (land == null) {
                point.noLand = true
                point.reason = "no land near $x $z"
            } else {
                point.anchorX = land.first
                point.anchorZ = land.second
            }
            setDirty()
        }
        return if (point.noLand) null else point.anchorX to point.anchorZ
    }

    /** A safe spot found at the point (count: a player arrived there, not only a look by /ultrawarp worlds prepare). */
    fun used(point: Point, spot: BlockPos, count: Boolean = true) {
        point.spot = spot.immutable()
        if (count) point.uses++
        point.blockedUntil = 0
        point.reason = ""
        setDirty()
    }

    fun refused(point: Point, reason: String) {
        point.blockedUntil = System.currentTimeMillis() + UltraWorldsConfig.settings.arrival.retryMinutes * 60_000L
        point.reason = reason
        setDirty()
    }

    companion object {
        private const val NAME = "generations_ultra_arrival_points"
        private const val GOLDEN_ANGLE = 2.399963229728653

        fun of(level: ServerLevel): UltraArrivalPoints =
            level.dataStorage.computeIfAbsent(Factory(::UltraArrivalPoints, ::load, null), NAME).also { it.ensure() }

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = UltraArrivalPoints().apply {
            tag.getList("points", Tag.TAG_COMPOUND.toInt()).forEach { runCatching { points.add(Point.load(it as CompoundTag)) } }
            points.sortBy { it.index }
        }

        /** Point i's ideal place: a spiral from the save's seed and the world. */
        fun ideal(level: ServerLevel, index: Int): Pair<Int, Int> {
            val arrival = UltraWorldsConfig.settings.arrival
            val seed = UltraWorlds.terrainSeed(level, level.seed)
            val turn = java.util.Random(seed).nextDouble() * 2 * Math.PI
            val angle = turn + index * GOLDEN_ANGLE
            val radius = arrival.minRadius + (arrival.spread - arrival.minRadius) * sqrt((index + 0.5) / arrival.points)
            return (cos(angle) * radius).roundToInt() to (sin(angle) * radius).roundToInt()
        }

        /**
         * Land near (x, z) as the world's generator makes it (no chunk loaded): ground
         * with no water over it, above the sea, not steep around. In rings of 32 blocks
         * out to radius; null when there is none.
         */
        fun land(level: ServerLevel, x: Int, z: Int, radius: Int): Pair<Int, Int>? {
            for (d in 0..radius step 32) {
                val cells = if (d == 0) listOf(0 to 0) else (-d until d step 32).flatMap { i -> listOf(i to -d, d to i, -i to d, -d to -i) }
                for ((dx, dz) in cells) {
                    if (isLand(level, x + dx, z + dz)) return (x + dx) to (z + dz)
                }
            }
            return null
        }

        fun isLand(level: ServerLevel, x: Int, z: Int): Boolean {
            val generator = level.chunkSource.generator
            val random = level.chunkSource.randomState()
            if (UltraArrivalSearch.cavern(level)) return hasHall(level, x, z)
            val ground = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random)
            if (level.getUncachedNoiseBiome(x shr 2, ground shr 2, z shr 2).`is`(UltraArrivalSearch.NO_ARRIVAL)) return false
            val surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random)
            if (ground < surface) return false
            if (ground <= level.seaLevel + 1 || ground <= level.minBuildHeight + 8 || ground >= level.maxBuildHeight - 16) return false
            // Not steep: the ground 6 blocks around stays close.
            return listOf(6 to 0, -6 to 0, 0 to 6, 0 to -6).all { (dx, dz) ->
                abs(generator.getBaseHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - ground) <= 5
            }
        }
    }
}

/**
 * Under a roof: whether the generator makes a hall in this column (a floor with at
 * least 5 blocks of air above it, above the lakes, in a biome open to arrivals),
 * without loading any chunk.
 */
private fun hasHall(level: ServerLevel, x: Int, z: Int): Boolean {
    val column = level.chunkSource.generator.getBaseColumn(x, z, level, level.chunkSource.randomState())
    var air = 0
    for (y in level.maxBuildHeight - 1 downTo level.seaLevel + 3) {
        val state = column.getBlock(y)
        if (state.isAir) {
            air++
            continue
        }
        if (air in 5..120 && state.fluidState.isEmpty &&
            !level.getUncachedNoiseBiome(x shr 2, (y + 1) shr 2, z shr 2).`is`(UltraArrivalSearch.NO_ARRIVAL)) return true
        air = 0
    }
    return false
}
