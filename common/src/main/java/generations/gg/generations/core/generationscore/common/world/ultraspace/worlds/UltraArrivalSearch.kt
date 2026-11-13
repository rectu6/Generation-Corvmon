package generations.gg.generations.core.generationscore.common.world.ultraspace.worlds

import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.tags.TagKey
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.Heightmap
import java.util.UUID
import kotlin.math.abs

/**
 * Where a player may be put down safely, on the server, in loaded chunks only:
 * standing on a full solid block, two free blocks for the body, no fluid, no lava,
 * fire, magma, cactus... around, no void below, no precipice next to it, under the
 * open sky, on the terrain's own ground (not a roof), away from player builds and
 * from other players.
 *
 * Each check is a Rule; later phases (protection around the arrival points, claim
 * mods) add theirs to [rules].
 */
object UltraArrivalSearch {
    /** What the spot is checked against. */
    class Context(
        val requireSky: Boolean,
        val buildRadius: Int,
        val maxDrop: Int,
        val edgeRadius: Int,
        val hazardRadius: Int,
        val playerRadius: Int,
        /** The player arriving (not counted among the others), null for none. */
        val player: UUID?,
        /** On the terrain's own ground, not on what stands on it (roofs, spikes): Ultra worlds' arrivals. */
        val onTerrain: Boolean = false,
    ) {
        companion object {
            fun arrival(player: UUID?): Context {
                val arrival = UltraWorldsConfig.settings.arrival
                return Context(arrival.requireSky, arrival.buildRadius, arrival.maxDrop, arrival.edgeRadius, arrival.hazardRadius, arrival.playerRadius, player, true)
            }

            /** Coming back where the player stood before leaving: their own home may be roofed. */
            fun home(player: UUID?): Context {
                val arrival = UltraWorldsConfig.settings.arrival
                return Context(false, 0, arrival.maxDrop, 1, arrival.hazardRadius, 0, player)
            }
        }
    }

    /** A check of a spot (the block where the feet go): why it is refused, null when it passes. */
    fun interface Rule {
        fun refuse(level: ServerLevel, feet: BlockPos, context: Context): String?
    }

    val BUILT: TagKey<Block> = TagKey.create(Registries.BLOCK, GenerationsCore.id("ultra_arrival_built"))

    /**
     * A world's own list of player blocks, when it has one (`ultra_arrival_built/<world>`):
     * the Ultra Skyscraper's city is made of glass, bricks and concrete, which would
     * otherwise turn every street into a "player build".
     */
    fun built(level: ServerLevel): TagKey<Block> {
        val own = TagKey.create(Registries.BLOCK, GenerationsCore.id("ultra_arrival_built/" + level.dimension().location().path))
        return if (BuiltInRegistries.BLOCK.getTag(own).isPresent) own else BUILT
    }
    val UNSAFE: TagKey<Block> = TagKey.create(Registries.BLOCK, GenerationsCore.id("ultra_arrival_unsafe"))
    /** Biomes no arrival lands in (lava fields, toxic marsh, underground lakes...): data/generations_core/tags/worldgen/biome. */
    val NO_ARRIVAL: TagKey<Biome> = TagKey.create(Registries.BIOME, GenerationsCore.id("ultra_space/no_arrival"))

    /** A world under a roof (the Ultra Abyss): arrivals go to a cavern's floor, not on top of the roof. */
    fun cavern(level: ServerLevel): Boolean = level.dimensionType().hasCeiling()

    private val GROUND = Rule { level, feet, _ ->
        val below = feet.below()
        val state = level.getBlockState(below)
        when {
            !state.fluidState.isEmpty -> "fluid underfoot"
            state.`is`(UNSAFE) -> "unsafe ground"
            !state.isFaceSturdy(level, below, Direction.UP) -> "no solid ground"
            else -> null
        }
    }

    private val BODY = Rule { level, feet, _ ->
        listOf(feet, feet.above()).firstNotNullOfOrNull { pos ->
            val state = level.getBlockState(pos)
            when {
                !state.fluidState.isEmpty -> "fluid"
                state.`is`(UNSAFE) -> "unsafe block"
                !state.getCollisionShape(level, pos).isEmpty -> "no room"
                else -> null
            }
        }
    }

    private val VOID = Rule { level, feet, _ ->
        if (feet.y <= level.minBuildHeight + 4 || feet.y + 2 >= level.maxBuildHeight) "too low or high" else null
    }

    private val HAZARDS = Rule { level, feet, context ->
        val r = context.hazardRadius
        var found: String? = null
        for (pos in BlockPos.betweenClosed(feet.offset(-r, -1, -r), feet.offset(r, 2, r))) {
            val state = level.getBlockState(pos)
            if (state.`is`(UNSAFE) || state.fluidState.`is`(FluidTags.LAVA)) {
                found = "danger near"
                break
            }
        }
        found
    }

    /** Around the spot, the ground is never more than maxDrop below its feet (no precipice, no shaft). */
    private val EDGE = Rule { level, feet, context ->
        val pos = BlockPos.MutableBlockPos()
        var found: String? = null
        loop@ for (d in 1..context.edgeRadius) {
            for (dx in -1..1) for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                val x = feet.x + dx * d
                val z = feet.z + dz * d
                if (!level.hasChunk(x shr 4, z shr 4)) continue
                var ground = false
                for (y in feet.y downTo feet.y - 1 - context.maxDrop) {
                    val state = level.getBlockState(pos.set(x, y, z))
                    if (!state.getCollisionShape(level, pos).isEmpty || !state.fluidState.isEmpty) {
                        ground = true
                        break
                    }
                }
                if (!ground) {
                    found = "precipice"
                    break@loop
                }
            }
        }
        found
    }

    /** Under the open sky (not in a building or a cave); a world under a roof has none. */
    private val SKY = Rule { level, feet, context ->
        if (context.requireSky && !cavern(level) && !level.canSeeSky(feet.above())) "no open sky" else null
    }

    private val BIOME = Rule { level, feet, _ ->
        if (level.getBiome(feet).`is`(NO_ARRIVAL)) "biome closed to arrivals" else null
    }

    /** In a cavern: a real hall, not a crack: room above the head and around. */
    private val ROOM = Rule { level, feet, _ ->
        if (!cavern(level)) return@Rule null
        val pos = BlockPos.MutableBlockPos()
        var open = 0
        for (dx in -1..1) for (dz in -1..1) for (dy in 0..3) {
            val state = level.getBlockState(pos.set(feet.x + dx, feet.y + dy, feet.z + dz))
            if (state.getCollisionShape(level, pos).isEmpty && state.fluidState.isEmpty) open++
        }
        if (open < 30) "no room in the cave" else null
    }

    private val PLAYERS = Rule { level, feet, context ->
        val r = context.playerRadius.toDouble()
        if (r <= 0) return@Rule null
        val near = level.players().any { it.uuid != context.player && !it.isSpectator && it.distanceToSqr(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5) < r * r }
        if (near) "another player there" else null
    }

    private val BUILDS = Rule { level, feet, context ->
        val r = context.buildRadius
        if (r <= 0) return@Rule null
        // Block entities (chests, furnaces, beds...) of the chunks around.
        val minChunk = ChunkPos((feet.x - r) shr 4, (feet.z - r) shr 4)
        val maxChunk = ChunkPos((feet.x + r) shr 4, (feet.z + r) shr 4)
        for (cx in minChunk.x..maxChunk.x) for (cz in minChunk.z..maxChunk.z) {
            val chunk = level.chunkSource.getChunkNow(cx, cz) ?: continue
            // Campfires aside: the worlds make their own (steam vents, chimneys, street fires).
            if (chunk.blockEntities.any { (at, entity) -> abs(at.x - feet.x) <= r && abs(at.z - feet.z) <= r && abs(at.y - feet.y) <= r && !entity.blockState.`is`(BlockTags.CAMPFIRES) }) {
                return@Rule "player build near (block entity)"
            }
        }
        var found: String? = null
        val built = built(level)
        for (pos in BlockPos.betweenClosed(feet.offset(-r, -3, -r), feet.offset(r, 4, r))) {
            if (level.getBlockState(pos).`is`(built)) {
                found = "player build near"
                break
            }
        }
        found
    }

    /**
     * On the ground the terrain itself has (the generator's height there, without what
     * the features put on it): not on a building's roof, a spike or a column, from where
     * there is no way down. Not under a roof (caverns have their own rule).
     */
    private val TERRAIN = Rule { level, feet, context ->
        if (!context.onTerrain || cavern(level)) return@Rule null
        val ground = level.chunkSource.generator.getBaseHeight(feet.x, feet.z, Heightmap.Types.WORLD_SURFACE_WG, level, level.chunkSource.randomState())
        if (feet.y > ground + 3) "on top of a building" else null
    }

    /** In order, cheapest first. Later phases add theirs (protection around the points, claims). */
    val rules: MutableList<Rule> = mutableListOf(VOID, GROUND, BODY, SKY, ROOM, BIOME, EDGE, HAZARDS, PLAYERS, TERRAIN, BUILDS)

    /** Why this spot is refused, null when it is safe. Its chunks must be loaded. */
    fun refusal(level: ServerLevel, feet: BlockPos, context: Context): String? = rules.firstNotNullOfOrNull { it.refuse(level, feet, context) }

    /**
     * The spot over a column: just above its highest block that stops movement (leaves
     * aside); under a roof, the floor of the column's tallest hall (above the lakes),
     * null when it has none.
     */
    fun surface(level: ServerLevel, x: Int, z: Int): BlockPos? {
        if (!cavern(level)) return BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z)
        val pos = BlockPos.MutableBlockPos()
        val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 2
        var best: BlockPos? = null
        var bestRoom = 0
        var air = 0
        for (y in top downTo level.seaLevel + 3) {
            val state = level.getBlockState(pos.set(x, y, z))
            if (state.getCollisionShape(level, pos).isEmpty && state.fluidState.isEmpty) {
                air++
                continue
            }
            // A floor with this much air above it.
            if (air >= 4 && air > bestRoom) {
                bestRoom = air
                best = BlockPos(x, y + 1, z)
            }
            air = 0
        }
        return best
    }

    /**
     * A safe spot around (x, z) within radius, in a spiral from the middle outwards, one
     * column in step; null when there is none. Why the middle was refused comes back too.
     */
    fun find(level: ServerLevel, x: Int, z: Int, radius: Int, step: Int, context: Context): Pair<BlockPos?, String?> {
        var reason: String? = null
        for (d in 0..radius step step.coerceAtLeast(1)) {
            for ((dx, dz) in ring(d)) {
                val cx = x + dx
                val cz = z + dz
                if (!level.hasChunk(cx shr 4, cz shr 4)) continue
                val feet = surface(level, cx, cz) ?: continue
                val refused = refusal(level, feet, context)
                if (refused == null) return feet to null
                if (reason == null) reason = refused
            }
        }
        return null to reason
    }

    /** The columns of a square ring at this distance from the middle (the middle alone for 0). */
    private fun ring(d: Int): List<Pair<Int, Int>> {
        if (d == 0) return listOf(0 to 0)
        val cells = ArrayList<Pair<Int, Int>>(8 * d)
        for (i in -d until d) {
            cells.add(i to -d)
            cells.add(d to i)
            cells.add(-i to d)
            cells.add(-d to -i)
        }
        return cells
    }
}
