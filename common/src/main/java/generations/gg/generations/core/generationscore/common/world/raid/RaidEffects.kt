package generations.gg.generations.core.generationscore.common.world.raid

import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.particles.DustColorTransitionOptions
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visual effects of the raids, server side (particles and sounds seen by everyone
 * around). Purely visual: no damage, no block or item touched.
 */
object RaidEffects {
    val RED = DustParticleOptions(Vector3f(0.85f, 0.08f, 0.16f), 3f)
    private val PURPLE = DustParticleOptions(Vector3f(0.62f, 0.18f, 0.9f), 2.5f)
    private val RED_TO_PURPLE = DustColorTransitionOptions(Vector3f(1f, 0.15f, 0.25f), Vector3f(0.6f, 0.2f, 1f), 2.5f)

    /** The boss appears above its den: flash, red clouds and a burst. */
    fun pop(level: ServerLevel, entity: PokemonEntity) {
        level.sendParticles(ParticleTypes.FLASH, entity.x, entity.y + 1.0, entity.z, 1, 0.0, 0.0, 0.0, 0.0)
        level.sendParticles(RED, entity.x, entity.y + 1.0, entity.z, 40, 1.2, 1.2, 1.2, 0.0)
        level.sendParticles(ParticleTypes.EXPLOSION, entity.x, entity.y + 0.5, entity.z, 2, 0.4, 0.4, 0.4, 0.0)
        level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.6f, 1.4f)
    }

    /** The boss leaves quietly (flight, abandoned raid). */
    fun leave(entity: PokemonEntity) {
        val level = entity.level() as? ServerLevel
        level?.sendParticles(ParticleTypes.POOF, entity.x, entity.y + 0.5, entity.z, 20, 0.5, 0.5, 0.5, 0.02)
        entity.discard()
    }

    /** The Dynamax storm grows: a gust of red clouds around the boss. */
    fun storm(entity: PokemonEntity) {
        val level = entity.level() as? ServerLevel ?: return
        val size = entity.bbWidth.toDouble()
        level.sendParticles(RED, entity.x, entity.y + entity.bbHeight * 0.6, entity.z, 60, size, entity.bbHeight * 0.4, size, 0.0)
        level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.ELYTRA_FLYING, SoundSource.NEUTRAL, 0.6f, 0.6f)
    }

    /** The shield breaks: shards of light and a crack. */
    fun shieldBreak(entity: PokemonEntity) {
        val level = entity.level() as? ServerLevel ?: return
        val y = entity.y + entity.bbHeight * 0.5
        level.sendParticles(ParticleTypes.END_ROD, entity.x, y, entity.z, 50, entity.bbWidth * 0.6, entity.bbHeight * 0.4, entity.bbWidth * 0.6, 0.15)
        level.sendParticles(PURPLE, entity.x, y, entity.z, 40, entity.bbWidth * 0.6, entity.bbHeight * 0.4, entity.bbWidth * 0.6, 0.0)
        level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.5f, 0.6f)
    }

    /** The shield rises: a dome of purple light. */
    fun shieldRise(entity: PokemonEntity) {
        val level = entity.level() as? ServerLevel ?: return
        ring(level, entity, entity.bbWidth * 0.8, entity.bbHeight * 0.5, PURPLE, 40)
        level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1f, 1.6f)
    }

    /** Shockwave: a red and purple ring spreading from the boss. */
    fun shockwave(entity: PokemonEntity) {
        val level = entity.level() as? ServerLevel ?: return
        for (step in 0..5) {
            afterOnServer(step * 0.08f) {
                if (!entity.isAlive) return@afterOnServer
                ring(level, entity, entity.bbWidth * 0.6 + step * 1.6, 0.4, RED_TO_PURPLE, 32 + step * 8)
            }
        }
        level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.NEUTRAL, 1f, 0.8f)
    }

    /**
     * Failed raid: the boss freezes and glows, red and purple energy gathers on it, a
     * Dynamax symbol of light shows, the energy closes in, then a great flash and the
     * boss scatters. done runs once it is gone (beam off, raid over).
     */
    fun explode(entity: PokemonEntity, done: () -> Unit) {
        val level = entity.level() as? ServerLevel ?: run { entity.discard(); done(); return }
        val cx = entity.x
        val cz = entity.z
        val cy = entity.y + entity.bbHeight * 0.5
        val radius = entity.bbWidth.toDouble().coerceAtLeast(1.0)
        entity.setNoGravity(true)
        entity.deltaMovement = net.minecraft.world.phys.Vec3.ZERO
        level.playSound(null, cx, cy, cz, SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 2f, 0.5f)

        // 1. Gathering: rings of particles closing in, faster and faster.
        for (i in 0 until 20) {
            afterOnServer(i * 0.1f) {
                val r = radius * (2.2 - i * 0.09)
                val angle = i * 0.6
                for (k in 0 until 16) {
                    val a = angle + k * Math.PI * 2 / 16
                    level.sendParticles(if (k % 2 == 0) RED else PURPLE, cx + cos(a) * r, cy + sin(a * 2) * radius * 0.5, cz + sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0)
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 6, radius * 0.5, entity.bbHeight * 0.3, radius * 0.5, 0.05)
            }
        }
        // 2. The Dynamax symbol: a cross of light above the boss.
        afterOnServer(1.2f) {
            val top = entity.y + entity.bbHeight + 1.5
            for (t in -10..10) {
                val d = t * radius * 0.12
                level.sendParticles(ParticleTypes.END_ROD, cx + d, top + d, cz, 1, 0.0, 0.0, 0.0, 0.0)
                level.sendParticles(ParticleTypes.END_ROD, cx + d, top - d, cz, 1, 0.0, 0.0, 0.0, 0.0)
            }
            ring(level, entity, radius * 0.9, entity.bbHeight + 1.5 - entity.bbHeight * 0.5, RED, 48)
            level.playSound(null, cx, cy, cz, SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 2f, 0.5f)
        }
        // 3. The energy rushes in.
        afterOnServer(2.2f) {
            level.sendParticles(RED_TO_PURPLE, cx, cy, cz, 120, radius * 1.5, entity.bbHeight * 0.6, radius * 1.5, 0.0)
            level.playSound(null, cx, cy, cz, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.NEUTRAL, 2f, 1f)
        }
        // 4. The burst: flash, explosion, the boss scatters.
        afterOnServer(3.2f) {
            level.sendParticles(ParticleTypes.FLASH, cx, cy, cz, 3, 0.5, 0.5, 0.5, 0.0)
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 3, radius * 0.4, entity.bbHeight * 0.3, radius * 0.4, 0.0)
            level.sendParticles(RED, cx, cy, cz, 200, radius * 1.4, entity.bbHeight * 0.6, radius * 1.4, 0.2)
            level.sendParticles(PURPLE, cx, cy, cz, 150, radius * 1.4, entity.bbHeight * 0.6, radius * 1.4, 0.2)
            level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 3f, 0.6f)
            level.playSound(null, cx, cy, cz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 2f, 0.8f)
            entity.discard()
            done()
        }
    }

    private fun ring(level: ServerLevel, entity: PokemonEntity, radius: Double, height: Double, particle: net.minecraft.core.particles.ParticleOptions, count: Int) {
        for (k in 0 until count) {
            val a = k * Math.PI * 2 / count
            level.sendParticles(particle, entity.x + cos(a) * radius, entity.y + height, entity.z + sin(a) * radius, 1, 0.0, 0.05, 0.0, 0.0)
        }
    }
}
