package generations.gg.generations.core.generationscore.common.world.ultraspace.weather

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.levelgen.Heightmap

/**
 * Whether a player is out of a dangerous weather's reach (UltraShelter), decided by the
 * server. Cheap tests only, never a sweep of the area: a heightmap (one read), eight short
 * rays at eye level, a column above the head, and for the toxic mist a fill bounded by
 * sealedVolume cells. UltraHazards keeps the answer while the player stays on the same
 * block (two seconds at most).
 */
object UltraShelters {
    private val DIRECTIONS = arrayOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)

    fun sheltered(level: ServerLevel, player: ServerPlayer, rule: UltraShelter): Boolean {
        val rules = UltraWeatherConfig.settings.shelters
        val head = BlockPos.containing(player.x, player.eyeY, player.z)
        val inWater = player.isInWater
        return when (rule) {
            UltraShelter.SHADE -> inWater || covered(level, head, Heightmap.Types.MOTION_BLOCKING)
            UltraShelter.ROOF -> covered(level, head, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES)
            UltraShelter.SOLID_ROOF -> !inWater && solidRoof(level, head, rules.roofScan)
            UltraShelter.BUILDING -> covered(level, head, Heightmap.Types.MOTION_BLOCKING) &&
                (depth(level, head) >= rules.undergroundDepth || walls(level, head, rules.rayLength) >= rules.buildingWalls)
            UltraShelter.HEAT_REFUGE -> inWater || depth(level, head) >= rules.undergroundDepth ||
                (covered(level, head, Heightmap.Types.MOTION_BLOCKING) && walls(level, head, rules.rayLength) >= rules.refugeWalls)
            UltraShelter.SEALED -> sealed(level, head, player.blockPosition(), rules.sealedVolume)
        }
    }

    /** Something of this heightmap's kind over the head. */
    private fun covered(level: ServerLevel, head: BlockPos, type: Heightmap.Types): Boolean = level.getHeight(type, head.x, head.z) > head.y

    /** Blocks between the head and the surface. */
    private fun depth(level: ServerLevel, head: BlockPos): Int = level.getHeight(Heightmap.Types.WORLD_SURFACE, head.x, head.z) - head.y

    /** An opaque, sturdy block above (leaves and glass do not count); deep under the ground counts too. */
    private fun solidRoof(level: ServerLevel, head: BlockPos, scan: Int): Boolean {
        val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, head.x, head.z) - 1
        if (top <= head.y) return false
        val pos = BlockPos.MutableBlockPos()
        for (y in head.y + 1..minOf(top, head.y + scan)) {
            pos.set(head.x, y, head.z)
            val state = level.getBlockState(pos)
            if (state.canOcclude() && !state.`is`(BlockTags.LEAVES) &&
                (state.isFaceSturdy(level, pos, Direction.DOWN) || state.isFaceSturdy(level, pos, Direction.UP))
            ) return true
        }
        return top > head.y + scan
    }

    /** Of the eight directions around the eyes, how many meet a wall within length blocks. */
    private fun walls(level: ServerLevel, head: BlockPos, length: Int): Int {
        val pos = BlockPos.MutableBlockPos()
        var count = 0
        for ((dx, dz) in DIRECTIONS) {
            for (step in 1..length) {
                pos.set(head.x + dx * step, head.y, head.z + dz * step)
                if (level.getBlockState(pos).blocksMotion()) {
                    count++
                    break
                }
            }
        }
        return count
    }

    /** The mist goes through the air, the fluids and the open doors, trapdoors and gates. */
    private fun permeable(state: BlockState): Boolean {
        if (state.isAir || !state.blocksMotion()) return true
        val block = state.block
        return (block is DoorBlock || block is TrapDoorBlock || block is FenceGateBlock) &&
            state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)
    }

    /** The open space around the player holds at most volume cells: the mist cannot get in. */
    private fun sealed(level: ServerLevel, head: BlockPos, feet: BlockPos, volume: Int): Boolean {
        val start = when {
            permeable(level.getBlockState(head)) -> head
            permeable(level.getBlockState(feet)) -> feet
            else -> return true
        }
        val seen = LongOpenHashSet()
        val queue = LongArrayFIFOQueue()
        seen.add(start.asLong())
        queue.enqueue(start.asLong())
        val pos = BlockPos.MutableBlockPos()
        while (!queue.isEmpty) {
            val at = queue.dequeueLong()
            for (direction in Direction.entries) {
                pos.set(at).move(direction)
                val key = pos.asLong()
                if (seen.contains(key)) continue
                if (pos.y < level.minBuildHeight || pos.y >= level.maxBuildHeight || !level.hasChunkAt(pos)) return false
                if (!permeable(level.getBlockState(pos))) continue
                seen.add(key)
                if (seen.size > volume) return false
                queue.enqueue(key)
            }
        }
        return true
    }
}
