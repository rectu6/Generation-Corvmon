package generations.gg.generations.core.generationscore.common.world.hooh

import generations.gg.generations.core.generationscore.common.world.mew.MewPlaces
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.util.Mth
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import java.util.Random
import kotlin.math.abs

/**
 * The places of the Passage of the Rainbow (HOOH-PLAN.md §§ 6 and 7), found locally: loaded
 * chunks only (a chunk is never loaded for it), a bounded number of height map reads.
 */
object HoOhPlaces {
    /** The ground and the top of the trees at (x, z), or null when that chunk is not loaded. */
    class Column(val ground: Int, val top: Int, val water: Boolean)

    fun column(level: ServerLevel, x: Double, z: Double): Column? {
        val bx = Mth.floor(x)
        val bz = Mth.floor(z)
        if (!level.isLoaded(BlockPos(bx, level.minBuildHeight, bz))) return null
        val ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz)
        val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz)
        val floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, bx, bz)
        // Water deeper than two blocks: not a way for a player on foot.
        val water = ground - floor > 2 && !level.getFluidState(BlockPos(bx, ground - 1, bz)).isEmpty
        return Column(ground, top, water)
    }

    fun biomeExcluded(level: ServerLevel, pos: BlockPos, entries: List<String>): Boolean {
        if (entries.isEmpty()) return false
        val holder = level.getBiome(pos)
        return entries.any { raw ->
            val entry = raw.trim()
            if (entry.startsWith("#")) ResourceLocation.tryParse(entry.substring(1))?.let { holder.`is`(TagKey.create(Registries.BIOME, it)) } == true
            else ResourceLocation.tryParse(entry)?.let { holder.`is`(it) } == true
        }
    }

    /** The sky above the player is open (no roof, not underground). */
    fun openSky(level: ServerLevel, player: ServerPlayer): Boolean {
        val pos = BlockPos.containing(player.x, player.eyeY, player.z)
        return level.canSeeSky(pos)
    }

    /**
     * The way of the passage: of 8 directions (from the player's look), the one where the ground
     * and the trees stay lowest up to 64 blocks, so that Ho-Oh is not hidden by a mountain.
     */
    fun openDirection(level: ServerLevel, player: ServerPlayer, random: Random): Float {
        var best = player.yRot
        var bestScore = Double.MAX_VALUE
        for (k in 0 until 8) {
            val yaw = player.yRot + k * 45f + (random.nextFloat() - 0.5f) * 10f
            val dir = MewPlaces.forward(yaw)
            var score = 0.0
            var known = 0
            for (d in listOf(16.0, 32.0, 48.0, 64.0)) {
                val c = column(level, player.x + dir.x * d, player.z + dir.z * d) ?: continue
                score += (c.top - player.y).coerceAtLeast(0.0) * (if (d > 40) 1.5 else 1.0) + if (c.water) 6.0 else 0.0
                known++
            }
            if (known == 0) continue
            // Ahead of the player is a little better (they see it begin).
            score = score / known + if (k == 0) -2.0 else if (k == 1 || k == 7) -1.0 else 0.0
            if (score < bestScore) {
                bestScore = score
                best = yaw
            }
        }
        return best
    }

    /** Ho-Oh's height over a column, between the configured margins over the trees. */
    fun altitude(top: Int, ground: Int, config: HoOhConfig.Pursuit, random: Random): Double =
        maxOf(top + config.altitudeMin * 0.75, ground + config.altitudeMin) + random.nextDouble() * (config.altitudeMax - config.altitudeMin)

    /**
     * The next point of the flight after `from` (going `yaw`): of 7 directions up to 60° aside,
     * 26 to 34 blocks on, the one with the walkable ground (no deep water, no cliff of more than
     * 10 blocks from the last ground), the straightest. Its height over the highest tree of the
     * segment, smoothed. Null when nothing is loaded or walkable that way.
     */
    fun nextPoint(level: ServerLevel, from: Vec3, fromGround: Int, yaw: Float, config: HoOhConfig.Pursuit, random: Random): Pair<Vec3, Pair<Float, Int>>? {
        var best: Vec3? = null
        var bestYaw = yaw
        var bestGround = fromGround
        var bestScore = Double.MAX_VALUE
        for (angle in floatArrayOf(0f, 20f, -20f, 40f, -40f, 60f, -60f)) {
            val turn = yaw + angle + (random.nextFloat() - 0.5f) * 12f
            val step = 26.0 + random.nextDouble() * 8.0
            val dir = MewPlaces.forward(turn)
            val x = from.x + dir.x * step
            val z = from.z + dir.z * step
            val end = column(level, x, z) ?: continue
            val mid = column(level, from.x + dir.x * step * 0.5, from.z + dir.z * step * 0.5) ?: continue
            if (end.water || mid.water) continue
            val climb = abs(end.ground - fromGround)
            if (climb > 10) continue
            val score = abs(angle) * 0.08 + climb * 1.2 + abs(mid.ground - fromGround) * 0.6 + random.nextDouble() * 1.5
            if (score < bestScore) {
                bestScore = score
                val top = maxOf(end.top, mid.top)
                val target = altitude(top, end.ground, config, random)
                // Smoothed with the last height, never under the trees' margin.
                val y = maxOf(from.y * 0.4 + target * 0.6, top + config.altitudeMin * 0.7)
                best = Vec3(x, y, z)
                bestYaw = turn
                bestGround = end.ground
            }
        }
        val point = best ?: return null
        return point to (bestYaw to bestGround)
    }

    /**
     * Where the feather lands: a safe and reachable place near the player, on the side of Ho-Oh
     * (2 to 7 blocks), else the player's own feet.
     */
    fun featherLanding(level: ServerLevel, player: ServerPlayer, towards: Vec3, random: Random): Vec3 {
        val dx = towards.x - player.x
        val dz = towards.z - player.z
        val yaw = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
        for (i in 0 until 16) {
            val turn = yaw + (random.nextFloat() - 0.5f) * 90f
            val d = 2.0 + random.nextDouble() * 5.0
            val dir = MewPlaces.forward(turn)
            val x = player.x + dir.x * d
            val z = player.z + dir.z * d
            val y = MewPlaces.ground(level, x, player.y, z, 3) ?: continue
            if (abs(y - player.y) > 3.0) continue
            return Vec3(Mth.floor(x) + 0.5, y, Mth.floor(z) + 0.5)
        }
        return player.position()
    }
}
