package generations.gg.generations.core.generationscore.common.world.eon

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where the anomaly may go (EON-PLAN.md § 4), in the chunks already loaded only (never one
 * loaded for it): on the walkable ground (the height map without the leaves, so under the trees'
 * crowns and between their trunks), with two free blocks over it, no deep water, no lava, a step
 * of one block at most between two columns a block apart: a player on foot can follow it.
 */
object EonPlaces {
    fun forward(yaw: Double): Vec3 {
        val r = Math.toRadians(yaw)
        return Vec3(-sin(r), 0.0, cos(r))
    }

    /** The walkable ground's height at (x, z) (the first free block), or null: not loaded, a tree, deep water, lava. */
    fun ground(level: ServerLevel, x: Double, z: Double): Int? {
        val bx = Mth.floor(x)
        val bz = Mth.floor(z)
        if (!level.isLoaded(BlockPos(bx, level.minBuildHeight, bz))) return null
        val g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz)
        if (g <= level.minBuildHeight) return null
        val floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, bx, bz)
        val below = BlockPos(bx, g - 1, bz)
        val fluid = level.getFluidState(below)
        if (!fluid.isEmpty) {
            if (fluid.`is`(FluidTags.LAVA)) return null
            // Water deeper than one block: not a way on foot.
            if (g - floor > 1) return null
        }
        val feet = BlockPos(bx, g, bz)
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty) return null
        val head = feet.above()
        if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty) return null
        return g
    }

    /** The ground at the end of a walkable segment (a column every block), or null. */
    fun walkable(level: ServerLevel, x0: Double, z0: Double, g0: Int, x1: Double, z1: Double): Int? {
        val dx = x1 - x0
        val dz = z1 - z0
        val steps = ceil(sqrt(dx * dx + dz * dz)).toInt().coerceAtLeast(1)
        var previous = g0
        for (i in 1..steps) {
            val u = i / steps.toDouble()
            val g = ground(level, x0 + dx * u, z0 + dz * u) ?: return null
            if (abs(g - previous) > 1) return null
            previous = g
        }
        return previous
    }

    class Point(val pos: Vec3, val yaw: Double, val ground: Int)

    /**
     * The next point of the way: a turn of 20 to 70 degrees (either side), 5 to 9 blocks on; if
     * that is not walkable, a smaller turn, the other side, straight on, square, back. Null: no way.
     */
    fun next(level: ServerLevel, from: Vec3, fromGround: Int, yaw: Double, random: Random): Point? {
        val side = if (random.nextBoolean()) 1.0 else -1.0
        val turn = side * (20.0 + random.nextDouble() * 50.0)
        val turns = doubleArrayOf(turn, turn * 0.5, -turn * 0.7, 0.0, -turn, 95.0 * side, -95.0 * side, 140.0 * side, -140.0 * side, 180.0)
        for (t in turns) {
            val heading = yaw + t
            val d = 5.0 + random.nextDouble() * 4.0
            val dir = forward(heading)
            val x = from.x + dir.x * d
            val z = from.z + dir.z * d
            val g = walkable(level, from.x, from.z, fromGround, x, z) ?: continue
            return Point(Vec3(x, g + EonPath.HEIGHT, z), heading, g)
        }
        return null
    }

    /**
     * Whether the anomaly's centre is seen from the player's eyes: no opaque block between
     * (leaves, plants and glass let it through), so never behind a hill or in a wall.
     */
    fun visible(level: ServerLevel, eye: Vec3, target: Vec3): Boolean {
        val d = target.subtract(eye)
        val length = d.length()
        val steps = (length / 0.5).toInt()
        var last: BlockPos? = null
        for (i in 1 until steps) {
            val p = eye.add(d.scale(i / steps.toDouble()))
            val pos = BlockPos.containing(p)
            if (pos == last) continue
            last = pos
            val state = level.getBlockState(pos)
            if (state.canOcclude() && !state.`is`(BlockTags.LEAVES)) return false
        }
        return true
    }

    /**
     * The signal's place: of about 24 candidates (ahead first, then the sides, then behind), at
     * startDistanceMin to startDistanceMax blocks, the first on walkable ground near the
     * player's height, seen from their eyes, with a way on from it. Null: nowhere (the
     * manifestation waits a minute).
     */
    fun start(level: ServerLevel, player: ServerPlayer, m: EonConfig.Manifestation, random: Random): Point? {
        val angles = doubleArrayOf(0.0, 22.0, -22.0, 45.0, -45.0, 68.0, -68.0, 95.0, -95.0, 180.0, 150.0, -150.0)
        val mid = (m.startDistanceMin + m.startDistanceMax) / 2.0
        val distances = doubleArrayOf(mid, m.startDistanceMin, m.startDistanceMax)
        for (d in distances) for (a in angles) {
            val yaw = player.yRot + a + (random.nextDouble() - 0.5) * 8.0
            val dir = forward(yaw)
            val x = player.x + dir.x * d
            val z = player.z + dir.z * d
            val g = ground(level, x, z) ?: continue
            if (abs(g - player.y) > 6.0) continue
            val centre = Vec3(x, g + EonPath.HEIGHT, z)
            if (!visible(level, player.eyePosition, centre)) continue
            // A way on, away from the player.
            next(level, centre, g, yaw, random) ?: continue
            return Point(centre, yaw, g)
        }
        return null
    }
}
