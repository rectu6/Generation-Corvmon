package generations.gg.generations.core.generationscore.common.client.ultraspace.rift

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraRiftPacket
import generations.gg.generations.core.generationscore.common.world.ultraspace.rift.UltraRiftShape
import generations.gg.generations.core.generationscore.common.world.ultraspace.rift.UltraRiftView
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The rifts this client sees (phase 6): the list the server sends for the player's
 * world, their opening, closing and collapse animations, their particles and hum.
 * Nothing here decides anything; a rift whose time is over is dropped even if the
 * server's word was lost (never a rift left drawn).
 */
object UltraRiftClient {
    /** Ticks of the animations. */
    const val OPEN_TICKS = 30
    const val CLOSE_TICKS = 30
    const val COLLAPSE_TICKS = 14

    class Rift(val view: UltraRiftView, val received: Long) {
        val outline: FloatArray = UltraRiftShape.outline(view.seed, view.width / 2f, view.height / 2f)
        val rightX = cos(Math.toRadians(view.yaw.toDouble())).toFloat()
        val rightZ = sin(Math.toRadians(view.yaw.toDouble())).toFloat()
        /** Client tick the closing began at, -1 while open. */
        var closing = -1L
        var collapse = false

        fun age(now: Long): Long = view.age + (now - received)

        fun remaining(now: Long): Long = view.remaining - (now - received)
    }

    val rifts = LinkedHashMap<Int, Rift>()
    /** Client ticks, for the animations. */
    var ticks = 0L; private set
    private var level: ClientLevel? = null

    fun init() {
        UltraRiftClientConfig.load()
        PlatformEvents.CLIENT_TICK_POST.subscribe(Priority.NORMAL) { tick() }
        PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe(Priority.NORMAL) {
            rifts.clear()
            level = null
        }
    }

    fun onPacket(packet: S2CUltraRiftPacket) {
        val minecraft = Minecraft.getInstance()
        level = minecraft.level
        when (packet.action) {
            S2CUltraRiftPacket.Action.FULL -> {
                rifts.clear()
                packet.rifts.forEach { rifts[it.id] = Rift(it, ticks) }
            }
            S2CUltraRiftPacket.Action.ADD -> packet.rifts.forEach { view ->
                val rift = Rift(view, ticks)
                rifts[view.id] = rift
                if (view.age < OPEN_TICKS) sound(rift, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.4f, 48.0)
            }
            S2CUltraRiftPacket.Action.CLOSE, S2CUltraRiftPacket.Action.COLLAPSE -> rifts[packet.riftId]?.let { rift ->
                if (rift.closing < 0) {
                    rift.closing = ticks
                    rift.collapse = packet.action == S2CUltraRiftPacket.Action.COLLAPSE
                    if (rift.collapse) {
                        sound(rift, SoundEvents.BEACON_DEACTIVATE, 1.4f, 0.7f, 64.0)
                        burst(rift)
                    } else {
                        sound(rift, SoundEvents.BEACON_DEACTIVATE, 0.9f, 1.2f, 48.0)
                    }
                }
            }
        }
    }

    private fun tick() {
        val minecraft = Minecraft.getInstance()
        val current = minecraft.level
        if (current == null) {
            rifts.clear()
            return
        }
        // Another world: its rifts come with the server's next list.
        if (current !== level) {
            rifts.clear()
            level = current
        }
        ticks++
        val iterator = rifts.values.iterator()
        while (iterator.hasNext()) {
            val rift = iterator.next()
            if (rift.closing < 0 && rift.remaining(ticks) < -40) rift.closing = ticks
            val length = if (rift.collapse) COLLAPSE_TICKS else CLOSE_TICKS
            if (rift.closing >= 0 && ticks - rift.closing > length) iterator.remove()
        }
        if (rifts.isEmpty() || minecraft.isPaused) return
        val player = minecraft.player ?: return
        val config = UltraRiftClientConfig.settings
        val random = current.random
        for (rift in rifts.values) {
            if (rift.closing >= 0) continue
            val dx = rift.view.x - player.x
            val dy = rift.view.y - player.y
            val dz = rift.view.z - player.z
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            if (distance > config.particleDistance) continue
            // A few motes of its destination's colour drawn into the opening (only seen: nothing is pulled).
            val chance = config.particlesPerSecond / 20f
            var budget = chance
            while (budget > 0f) {
                if (random.nextFloat() < budget) mote(current, rift, random.nextInt(UltraRiftShape.POINTS), random.nextFloat())
                budget -= 1f
            }
            if (random.nextFloat() < 0.04f) {
                val k = random.nextInt(UltraRiftShape.POINTS)
                val (x, y, z) = edge(rift, k, 1.0f)
                current.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.01, 0.0)
            }
            if (config.sounds && ticks % 60 == (rift.view.id % 60).toLong() && distance < 16) {
                sound(rift, SoundEvents.BEACON_AMBIENT, 0.5f, 1.5f, 16.0)
            }
        }
    }

    /** A point on the edge, k of the outline, scaled; world position. */
    fun edge(rift: Rift, k: Int, scale: Float): Triple<Double, Double, Double> {
        val u = rift.outline[2 * k] * scale
        val v = rift.outline[2 * k + 1] * scale
        return Triple(rift.view.x + u * rift.rightX, rift.view.y + v, rift.view.z + u * rift.rightZ)
    }

    private fun mote(level: ClientLevel, rift: Rift, k: Int, spread: Float) {
        val (x, y, z) = edge(rift, k, 1.15f + spread * 0.5f)
        val speed = 0.06
        val vx = (rift.view.x - x) * speed
        val vy = (rift.view.y - y) * speed
        val vz = (rift.view.z - z) * speed
        level.addParticle(DustParticleOptions(colour(rift.view.color, 1f), 1.1f), x, y, z, vx, vy, vz)
    }

    /** The collapse: a short burst of its colour, once. */
    private fun burst(rift: Rift) {
        val level = Minecraft.getInstance().level ?: return
        val random = level.random
        repeat(24) {
            val k = random.nextInt(UltraRiftShape.POINTS)
            val (x, y, z) = edge(rift, k, 0.3f + random.nextFloat() * 0.6f)
            level.addParticle(DustParticleOptions(colour(rift.view.color, 1.2f), 1.6f), x, y, z, (x - rift.view.x) * 0.2, (y - rift.view.y) * 0.2, (z - rift.view.z) * 0.2)
        }
        level.addParticle(ParticleTypes.FLASH, rift.view.x, rift.view.y, rift.view.z, 0.0, 0.0, 0.0)
    }

    private fun sound(rift: Rift, event: net.minecraft.sounds.SoundEvent, volume: Float, pitch: Float, range: Double) {
        if (!UltraRiftClientConfig.settings.sounds) return
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (player.distanceToSqr(rift.view.x, rift.view.y, rift.view.z) > range * range) return
        minecraft.level?.playLocalSound(rift.view.x, rift.view.y, rift.view.z, event, SoundSource.AMBIENT, volume, pitch, false)
    }

    fun colour(rgb: Int, boost: Float): Vector3f = Vector3f(
        ((rgb shr 16) and 0xFF) / 255f * boost,
        ((rgb shr 8) and 0xFF) / 255f * boost,
        (rgb and 0xFF) / 255f * boost,
    ).let { Vector3f(it.x.coerceAtMost(1f), it.y.coerceAtMost(1f), it.z.coerceAtMost(1f)) }
}
