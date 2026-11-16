package generations.gg.generations.core.generationscore.common.world.cinematic

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where a cinematic stands (CINEMATIQUES-PLAN.md § 3.4): the ground near a point, the shrine's
 * front, and the Pokémon's landing point (also where its entity will appear, lot 5). Only blocks
 * within a few blocks of the point are read, never an unloaded chunk.
 */
object CinematicPlaces {
    /** Minecraft yaw of the direction from one point to another, on the ground plane. */
    fun yawTowards(from: Vec3, to: Vec3): Float = Math.toDegrees(atan2(-(to.x - from.x), to.z - from.z)).toFloat()

    /** The scene's forward direction (local +z) for a facing yaw. */
    fun forward(facing: Float): Vec3 {
        val r = Math.toRadians(facing.toDouble())
        return Vec3(-sin(r), 0.0, cos(r))
    }

    /**
     * The top of the ground under or near (x, aroundY, z): the first height, searching up and
     * down by up to 8 blocks, with a sturdy floor and two free blocks above. aroundY when none.
     */
    fun ground(level: ServerLevel, x: Double, aroundY: Double, z: Double): Double {
        val bx = Mth.floor(x)
        val bz = Mth.floor(z)
        val by = Mth.floor(aroundY)
        if (!level.isLoaded(BlockPos(bx, by, bz))) return aroundY
        for (i in 0..16) {
            val dy = if (i % 2 == 0) i / 2 else -(i + 1) / 2
            val pos = BlockPos(bx, by + dy, bz)
            if (standable(level, pos)) return pos.y.toDouble()
        }
        return aroundY
    }

    private fun standable(level: ServerLevel, pos: BlockPos): Boolean {
        val below = pos.below()
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) &&
            level.getBlockState(pos).getCollisionShape(level, pos).isEmpty &&
            level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty
    }

    /**
     * Test mode's shrine: 6 blocks in front of the player, on the ground, facing them.
     */
    fun testOrigin(level: ServerLevel, eye: Vec3, feetY: Double, yaw: Float): Vec3 {
        val ahead = forward(yaw).scale(6.0)
        val x = Mth.floor(eye.x + ahead.x) + 0.5
        val z = Mth.floor(eye.z + ahead.z) + 0.5
        return Vec3(x, ground(level, x, feetY, z), z)
    }

    /**
     * The landing point: in front of the shrine first (3 to 6 blocks), then on rings around it,
     * on a sturdy floor where a box of that size is free. The shrine's own point when none is.
     */
    fun landing(level: ServerLevel, origin: Vec3, facing: Float, width: Double, height: Double): Vec3 {
        val half = (width / 2.0).coerceIn(0.3, 3.0)
        val tall = height.coerceIn(0.5, 6.0)
        val base = Math.toRadians(facing.toDouble())
        val candidates = ArrayList<Pair<Double, Double>>()
        for (d in doubleArrayOf(4.0, 3.0, 5.0, 6.0)) candidates += 0.0 to d
        for (d in doubleArrayOf(3.0, 4.5, 6.0)) for (k in 1..11) {
            val a = Math.toRadians(30.0 * ((k + 1) / 2) * (if (k % 2 == 0) -1 else 1))
            candidates += a to d
        }
        for ((angle, d) in candidates) {
            val r = base + angle
            val x = origin.x - sin(r) * d
            val z = origin.z + cos(r) * d
            if (!level.isLoaded(BlockPos.containing(x, origin.y, z))) continue
            val y = ground(level, x, origin.y, z)
            val box = AABB(x - half, y + 0.01, z - half, x + half, y + tall, z + half)
            if (level.noCollision(box) && standable(level, BlockPos.containing(x, y, z))) return Vec3(x, y, z)
        }
        return origin
    }
}
