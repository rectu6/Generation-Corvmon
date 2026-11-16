package generations.gg.generations.core.generationscore.common.world.mew

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Safe places for Mew (MEW-PLAN.md § 8), found locally: loaded chunks only, a bounded number of
 * tries (at most 32 per place), never a large scan of blocks. A place has no liquid, two free
 * blocks, a floor at most 3 blocks below and a limited height difference from the start.
 */
object MewPlaces {
    private const val TRIES = 32

    /** Minecraft yaw → horizontal direction (x, z). */
    fun forward(yaw: Float): Vec3 {
        val r = Math.toRadians(yaw.toDouble())
        return Vec3(-sin(r), 0.0, cos(r))
    }

    fun yawTowards(from: Vec3, to: Vec3): Float = Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z)).toFloat()

    private fun free(level: ServerLevel, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return state.getCollisionShape(level, pos).isEmpty && state.fluidState.isEmpty
    }

    private fun floor(level: ServerLevel, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return !state.getCollisionShape(level, pos).isEmpty && state.fluidState.isEmpty
    }

    /**
     * The feet height of a safe place at (x, z) near aroundY (searching 6 up and 8 down), or null:
     * two free blocks over a floor, no liquid around the feet.
     */
    fun ground(level: ServerLevel, x: Double, aroundY: Double, z: Double, maxRise: Int = 6): Double? {
        val bx = Mth.floor(x)
        val bz = Mth.floor(z)
        val by = Mth.floor(aroundY)
        if (!level.isLoaded(BlockPos(bx, by, bz))) return null
        // Down first (the ground under the point), then up (a slope).
        for (dy in (0 downTo -8) + (1..maxRise)) {
            val pos = BlockPos(bx, by + dy, bz)
            if (free(level, pos) && free(level, pos.above()) && floor(level, pos.below())) return pos.y.toDouble()
        }
        return null
    }

    /** A safe place close to that one (the same place when it is fine), within maxRise of aroundY. */
    private fun safeNear(level: ServerLevel, x: Double, aroundY: Double, z: Double, maxRise: Int = 6): Vec3? {
        val y = ground(level, x, aroundY, z, maxRise) ?: return null
        if (kotlin.math.abs(y - aroundY) > maxRise) return null
        return Vec3(Mth.floor(x) + 0.5, y, Mth.floor(z) + 0.5)
    }

    /** Hidden from these eyes: a block stands between them and the place. */
    private fun hidden(level: ServerLevel, player: ServerPlayer, eyes: Vec3, at: Vec3): Boolean {
        val hit = level.clip(ClipContext(eyes, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player))
        return hit.type == HitResult.Type.BLOCK && hit.location.distanceTo(eyes) < eyes.distanceTo(at) - 0.6
    }

    /** How much cover a place has around it (trunks, leaves, rock, plants). */
    private fun cover(level: ServerLevel, at: Vec3): Int {
        var score = 0
        val base = BlockPos.containing(at)
        for (dx in -2..2) for (dz in -2..2) for (dy in -1..2) {
            val pos = base.offset(dx, dy, dz)
            val state = level.getBlockState(pos)
            if (state.isAir) continue
            score += when {
                state.`is`(BlockTags.LOGS) -> 3
                state.`is`(BlockTags.LEAVES) -> 2
                state.`is`(BlockTags.BASE_STONE_OVERWORLD) && dy >= 0 -> 2
                state.`is`(BlockTags.REPLACEABLE_BY_TREES) || state.`is`(BlockTags.FLOWERS) || state.`is`(BlockTags.SAPLINGS) -> 1
                else -> 0
            }
        }
        return score
    }

    private fun inCircle(origin: Vec3, at: Vec3, radius: Double): Boolean {
        val dx = at.x - origin.x
        val dz = at.z - origin.z
        return dx * dx + dz * dz <= radius * radius
    }

    /**
     * Round 1: a hiding place 10 to 20 blocks away, the best of several by cover (behind a tree, on
     * a rock, in a bush), out of the player's sight. Mew's feet (it hovers a little over it).
     */
    fun hidingSpot(level: ServerLevel, player: ServerPlayer, origin: Vec3, minD: Double, maxD: Double, radius: Double, random: Random): Vec3 {
        val eyes = player.eyePosition
        var best: Vec3? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until TRIES) {
            val angle = random.nextDouble() * Math.PI * 2.0
            val d = minD + random.nextDouble() * (maxD - minD)
            val x = player.x - sin(angle) * d
            val z = player.z + cos(angle) * d
            val at = safeNear(level, x, player.y, z) ?: continue
            if (!inCircle(origin, at, radius)) continue
            var score = cover(level, at)
            if (hidden(level, player, eyes, at.add(0.0, 0.6, 0.0))) score += 12
            if (score > bestScore) {
                bestScore = score
                best = at
            }
        }
        return best ?: fallback(level, player, origin, minD)
    }

    /** Somewhere in front of the player when nothing better was found (never fails). */
    private fun fallback(level: ServerLevel, player: ServerPlayer, origin: Vec3, d: Double): Vec3 {
        val ahead = forward(player.yRot).scale(d)
        return safeNear(level, player.x + ahead.x, player.y, player.z + ahead.z) ?: Vec3(player.x + ahead.x, player.y + 1.0, player.z + ahead.z)
    }

    /** Round 2: the copies in an arc 5 to 8 blocks in front of the player, hovering, each on safe ground. */
    fun copies(level: ServerLevel, player: ServerPlayer, count: Int, random: Random): List<Vec3> {
        val out = ArrayList<Vec3>()
        val spread = 120.0
        for (i in 0 until count) {
            var place: Vec3? = null
            for (k in 0 until 6) {
                val angle = player.yRot + (-spread / 2.0 + spread * (i + 0.5) / count) + (random.nextDouble() - 0.5) * 8.0
                val d = 5.0 + random.nextDouble() * 3.0
                val dir = forward(angle.toFloat())
                val at = safeNear(level, player.x + dir.x * d, player.y, player.z + dir.z * d, 3)
                if (at != null && out.none { it.distanceTo(at) < 1.6 }) {
                    place = at.add(0.0, 0.6, 0.0)
                    break
                }
            }
            out += place ?: player.position().add(forward((player.yRot - 60f + 120f * (i + 0.5f) / count)).scale(6.0)).add(0.0, 0.8, 0.0)
        }
        return out
    }

    /**
     * Round 3: a course of `count` points (minStep to maxStep apart), turning, inside the game's
     * circle; Mew hovers 1 to 3 blocks over each. Shorter when the ground does not allow more.
     */
    fun course(level: ServerLevel, player: ServerPlayer, origin: Vec3, count: Int, minStep: Double, maxStep: Double, radius: Double, random: Random): List<Vec3> {
        val out = ArrayList<Vec3>()
        var from = player.position()
        var heading = player.yRot.toDouble()
        for (i in 0 until count) {
            var next: Vec3? = null
            for (k in 0 until 16) {
                val turn = if (i == 0) (random.nextDouble() - 0.5) * 40.0 else (random.nextDouble() - 0.5) * 200.0
                val angle = heading + turn
                val d = (if (i == 0) 6.0 else minStep) + random.nextDouble() * (maxStep - minStep)
                val dir = forward(angle.toFloat())
                val ground = safeNear(level, from.x + dir.x * d, from.y, from.z + dir.z * d) ?: continue
                if (!inCircle(origin, ground, radius - 1.0)) continue
                if (out.any { it.distanceTo(ground) < minStep * 0.6 }) continue
                next = ground.add(0.0, 1.0 + random.nextDouble() * 2.0, 0.0)
                heading = angle
                break
            }
            if (next == null) break
            out += next
            from = Vec3(next.x, next.y - 1.0, next.z)
        }
        if (out.isEmpty()) out += fallback(level, player, origin, 6.0).add(0.0, 1.0, 0.0)
        return out
    }

    /** Round 3, the player stuck: Mew's point 40 % closer to them, still safe. */
    fun closer(level: ServerLevel, player: ServerPlayer, point: Vec3): Vec3 {
        val target = point.lerp(player.position(), 0.4)
        return safeNear(level, target.x, target.y, target.z)?.add(0.0, 1.2, 0.0) ?: point
    }

    /**
     * A false appearance's places (§ 6): origin, the second point (relative), Mew's way. Null when
     * none was found (nothing is shown then).
     */
    fun manifestation(level: ServerLevel, player: ServerPlayer, type: Int, random: Random): Triple<Vec3, Vec3, Float>? {
        val eyes = player.eyePosition
        return when (type) {
            // A: across the forest, behind the trees, 12 to 20 blocks away.
            0 -> {
                for (i in 0 until TRIES) {
                    val side = player.yRot + (random.nextDouble() * 140.0 - 70.0).toFloat()
                    val d = 12.0 + random.nextDouble() * 8.0
                    val mid = player.position().add(forward(side).scale(d))
                    val across = forward(side + 90f).scale(4.0 + random.nextDouble() * 3.0)
                    val a = safeNear(level, mid.x - across.x, player.y, mid.z - across.z) ?: continue
                    val b = safeNear(level, mid.x + across.x, a.y, mid.z + across.z) ?: continue
                    if (kotlin.math.abs(a.y - b.y) > 3.0) continue
                    if (!hidden(level, player, eyes, a.add(0.0, 1.0, 0.0)) && i < TRIES / 2) continue
                    val from = a.add(0.0, 1.0, 0.0)
                    return Triple(from, b.add(0.0, 1.0, 0.0).subtract(from), yawTowards(from, b))
                }
                null
            }
            // B: a presence behind the player.
            1 -> {
                val behind = player.position().add(forward(player.yRot + 180f).scale(2.5)).add(0.0, 1.4, 0.0)
                Triple(behind, Vec3.ZERO, player.yRot)
            }
            // C: high over the player's surroundings, across the sky.
            2 -> {
                val side = player.yRot + (random.nextDouble() * 120.0 - 60.0).toFloat()
                val ground = ground(level, player.x, player.y, player.z) ?: player.y
                val height = 8.0 + random.nextDouble() * 6.0
                val start = player.position().add(forward(side - 90f).scale(14.0)).add(forward(side).scale(6.0))
                val from = Vec3(start.x, ground + height, start.z)
                val path = forward(side + 90f).scale(28.0)
                Triple(from, Vec3(path.x, (random.nextDouble() - 0.5) * 3.0, path.z), side + 90f)
            }
            // D: curious, right in front of the player.
            else -> {
                val ahead = forward(player.yRot).scale(3.5)
                val at = safeNear(level, player.x + ahead.x, player.y, player.z + ahead.z, 3) ?: Vec3(player.x + ahead.x, player.y, player.z + ahead.z)
                val from = at.add(0.0, 0.9, 0.0)
                Triple(from, player.position().subtract(from), yawTowards(from, player.position()))
            }
        }
    }
}
