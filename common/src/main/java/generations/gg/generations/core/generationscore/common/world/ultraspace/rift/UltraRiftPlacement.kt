package generations.gg.generations.core.generationscore.common.world.ultraspace.rift

import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraArrivalPoints
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraArrivalSearch
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.Heightmap
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where a rift may open: floating above the terrain under the open sky, in the middle
 * of a tall hall under a roof (the Ultra Abyss), always in air (never through a block,
 * a fluid or a structure), in chunks already loaded (none is ever loaded for it), far
 * enough from the other rifts and from the arrival points.
 */
object UltraRiftPlacement {
    class Spot(val x: Double, val y: Double, val z: Double, val yaw: Float, val width: Float, val height: Float)

    /** A few columns around the player; the spot found, else the last reason. */
    fun near(level: ServerLevel, player: ServerPlayer, random: Random, others: List<UltraRift>): Pair<Spot?, String> {
        val config = UltraRiftsConfig.rifts
        var reason = "no place tried"
        repeat(config.triesPerAttempt) {
            val angle = random.nextDouble() * 2 * PI
            val distance = config.spawnDistance.pick(random).toDouble()
            val (spot, why) = at(level, player.x + cos(angle) * distance, player.z + sin(angle) * distance, random, others)
            if (spot != null) return spot to "ok"
            reason = why
        }
        return null to reason
    }

    /** A rift over this column: its height found, every rule checked. */
    fun at(level: ServerLevel, x: Double, z: Double, random: Random, others: List<UltraRift>): Pair<Spot?, String> {
        val config = UltraRiftsConfig.rifts
        val variation = config.sizeVariation
        val width = config.width * (1 + (random.nextFloat() * 2 - 1) * variation)
        val height = config.height * (1 + (random.nextFloat() * 2 - 1) * variation)
        val yaw = random.nextFloat() * 360f
        val min = config.minDistanceBetween.toDouble()
        others.firstOrNull { horizontal(it.x, it.z, x, z) < min }?.let { return null to "too close to rift #${it.id}" }
        val clearance = config.arrivalPointClearance.toDouble()
        if (clearance > 0) {
            for (point in UltraArrivalPoints.of(level).points) {
                val (px, pz) = point.spot?.let { it.x to it.z } ?: if (point.anchored) point.anchorX to point.anchorZ else UltraArrivalPoints.ideal(level, point.index)
                if (horizontal(px + 0.5, pz + 0.5, x, z) < clearance) return null to "too close to arrival point ${point.index}"
            }
        }
        val reach = (max(width, height) / 2 + config.clearanceMargin + 2).toDouble()
        if (!loaded(level, x, z, reach)) return null to "chunks not loaded"
        if (structure(level, x, z, reach)) return null to "structure near"
        val y = if (UltraArrivalSearch.cavern(level)) hall(level, x, z, height, random) ?: return null to "no hall tall enough"
        else sky(level, x, z, yaw, width, height, random)
        if (y < config.minY || y > config.maxY) return null to "height ${y.toInt()} out of range"
        if (y + height / 2 + config.clearanceMargin >= level.maxBuildHeight - 2) return null to "too close to the top of the world"
        blocked(level, x, y, z, yaw, width, height, config.clearanceMargin)?.let { return null to it }
        return Spot(x, y, z, yaw, width, height) to "ok"
    }

    /** Under the open sky: the bottom of the opening some blocks above the highest ground under it. */
    private fun sky(level: ServerLevel, x: Double, z: Double, yaw: Float, width: Float, height: Float, random: Random): Double {
        val rx = cos(Math.toRadians(yaw.toDouble()))
        val rz = sin(Math.toRadians(yaw.toDouble()))
        var ground = level.minBuildHeight
        val half = width / 2 + 1
        var s = -half
        while (s <= half) {
            for (d in -1..1) {
                val px = floor(x + rx * s - rz * d).toInt()
                val pz = floor(z + rz * s + rx * d).toInt()
                ground = max(ground, level.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz))
            }
            s += 1f
        }
        return ground + UltraRiftsConfig.rifts.heightAboveGround.pick(random) + height / 2.0
    }

    /**
     * Under a roof: the column's tallest hall (air, no fluid) at least cavernMinHeight
     * high; the rift in its middle, never touching its floor nor its ceiling.
     */
    private fun hall(level: ServerLevel, x: Double, z: Double, height: Float, random: Random): Double? {
        val config = UltraRiftsConfig.rifts
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        val pos = BlockPos.MutableBlockPos()
        val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 2
        var bestFloor = 0
        var bestRoom = 0
        var air = 0
        for (y in top downTo level.minBuildHeight + 4) {
            val state = level.getBlockState(pos.set(bx, y, bz))
            if (state.getCollisionShape(level, pos).isEmpty && state.fluidState.isEmpty) {
                air++
                continue
            }
            if (air > bestRoom) {
                bestRoom = air
                bestFloor = y + 1
            }
            air = 0
        }
        val needed = max(config.cavernMinHeight, ceil(height + 2.0 * config.clearanceMargin + 2).toInt())
        if (bestRoom < needed) return null
        // The opening's bottom between just above the floor's margin and just under the
        // ceiling's, in the lower part of that room (easier to reach).
        val lowest = bestFloor + config.clearanceMargin + 1.0
        val highest = bestFloor + bestRoom - config.clearanceMargin - 1.0 - height
        if (highest < lowest) return null
        return lowest + random.nextDouble() * (highest - lowest) * 0.6 + height / 2.0
    }

    /** Every chunk under the rift and its margin is loaded now (nothing is loaded for it). */
    fun loaded(level: ServerLevel, x: Double, z: Double, reach: Double): Boolean {
        for (cx in (floor(x - reach).toInt() shr 4)..(floor(x + reach).toInt() shr 4)) {
            for (cz in (floor(z - reach).toInt() shr 4)..(floor(z + reach).toInt() shr 4)) {
                if (level.chunkSource.getChunkNow(cx, cz) == null) return false
            }
        }
        return true
    }

    /** A structure has a part in one of these chunks (its references): never through one. */
    private fun structure(level: ServerLevel, x: Double, z: Double, reach: Double): Boolean {
        for (cx in (floor(x - reach).toInt() shr 4)..(floor(x + reach).toInt() shr 4)) {
            for (cz in (floor(z - reach).toInt() shr 4)..(floor(z + reach).toInt() shr 4)) {
                val chunk = level.chunkSource.getChunkNow(cx, cz) ?: continue
                if (chunk.allReferences.values.any { !it.isEmpty() }) return true
            }
        }
        return false
    }

    /** The first block (or fluid) in the rift's room and its margin, null when it is all air. */
    fun blocked(level: ServerLevel, x: Double, y: Double, z: Double, yaw: Float, width: Float, height: Float, margin: Int): String? {
        val rx = cos(Math.toRadians(yaw.toDouble()))
        val rz = sin(Math.toRadians(yaw.toDouble()))
        val halfU = width / 2.0 + margin
        val halfV = height / 2.0 + margin
        val halfD = margin + 0.5
        val reach = sqrt(halfU * halfU + halfD * halfD)
        val pos = BlockPos.MutableBlockPos()
        for (bx in floor(x - reach).toInt()..floor(x + reach).toInt()) {
            for (bz in floor(z - reach).toInt()..floor(z + reach).toInt()) {
                val dx = bx + 0.5 - x
                val dz = bz + 0.5 - z
                val u = dx * rx + dz * rz
                val d = -dx * rz + dz * rx
                if (abs(u) > halfU + 0.5 || abs(d) > halfD + 0.5) continue
                for (by in floor(y - halfV).toInt()..floor(y + halfV).toInt()) {
                    val state = level.getBlockState(pos.set(bx, by, bz))
                    if (!state.getCollisionShape(level, pos).isEmpty || !state.fluidState.isEmpty) {
                        return "blocked by ${state.blockHolder.unwrapKey().map { it.location().path }.orElse("a block")} at $bx $by $bz"
                    }
                }
            }
        }
        return null
    }

    private fun horizontal(ax: Double, az: Double, bx: Double, bz: Double): Double {
        val dx = ax - bx
        val dz = az - bz
        return sqrt(dx * dx + dz * dz)
    }
}
