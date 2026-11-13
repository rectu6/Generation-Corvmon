package generations.gg.generations.core.generationscore.common.world.ultraspace.weather

import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraHazardPacket
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpSessions
import generations.gg.generations.core.generationscore.common.world.ultraspace.suit.UltraSuit
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTransfers
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import java.util.UUID

/** Where a player stands against their world's dangerous weather (the indicator, S2CUltraHazardPacket). */
enum class UltraHazardStatus { NONE, GRACE, SHELTERED, SHIELDED, PARTIAL, EXPOSED }

/**
 * The damage of the dangerous weathers (phase 4.4), decided by the server: every
 * checkIntervalTicks, each player of a world under a dangerous weather is looked at. Its
 * shield on the four pieces (UltraSuit.protection): no damage. Else a shelter suited to
 * the danger (UltraShelters): no damage. Else, once the grace is over (after arriving,
 * after the start of the weather, and the protection of an Ultra Warp arrival), a hit
 * every intervalSeconds, growing with the time exposed for the progressive weathers. A
 * player without a shield always survives in a shelter.
 */
object UltraHazards {
    private class PlayerState {
        /** Game time of the arrival in the world (login, respawn, change of dimension). */
        var arrival = Long.MIN_VALUE
        /** Ticks spent exposed (the progressive damage); falls back while sheltered. */
        var exposure = 0
        var lastHit = 0L
        var status = UltraHazardStatus.NONE
        var sentEvent = ""
        var sentPieces = -1
        var sentSeconds = -1
        // The last shelter test, kept while the player stays on its block.
        var cachePos = Long.MIN_VALUE
        var cacheRule: UltraShelter? = null
        var cacheTime = 0L
        var cacheResult = false
    }

    private val players = HashMap<UUID, PlayerState>()

    fun clear() = players.clear()

    fun remove(player: UUID) {
        players.remove(player)
    }

    /** In a new level: the grace starts again, and the indicator is emptied until the next check. */
    fun arrived(player: ServerPlayer) {
        val state = players.getOrPut(player.uuid) { PlayerState() }
        state.arrival = UltraWeather.now(player.server)
        state.exposure = 0
        state.cachePos = Long.MIN_VALUE
        send(player, state, UltraHazardStatus.NONE, "", 0, 0, force = true)
    }

    fun tick(server: MinecraftServer, now: Long) {
        val interval = UltraWeatherConfig.settings.checkIntervalTicks
        if (server.tickCount % interval != 0) return
        for (id in UltraWorlds.IDS) {
            val level = UltraWorlds.key(id)?.let { server.getLevel(it) } ?: continue
            if (level.players().isEmpty()) continue
            val active = UltraWeather.active(id)?.takeIf { it.event.dangerous && it.intensity(now.toDouble()) > 0f }
            for (player in level.players()) check(level, player, active, now, interval)
        }
    }

    private fun check(level: ServerLevel, player: ServerPlayer, active: UltraWeatherSky.Active?, now: Long, interval: Int) {
        val state = players.getOrPut(player.uuid) { PlayerState().also { it.arrival = now } }
        val event = active?.event
        val damage = event?.let { UltraWeatherConfig.event(it)?.damage }
        if (event == null || damage == null || player.isCreative || player.isSpectator || !player.isAlive ||
            UltraWarpSessions.get(player.uuid)?.ride != null
        ) {
            state.exposure = 0
            send(player, state, UltraHazardStatus.NONE, "", 0, 0)
            return
        }
        val shield = event.shield
        val pieces = shield?.let { UltraSuit.piecesWith(player, it) } ?: 0
        val protection = shield?.let { UltraSuit.protection(player, it) } ?: 0f
        if (protection >= 1f) {
            state.exposure = (state.exposure - interval * 2).coerceAtLeast(0)
            send(player, state, UltraHazardStatus.SHIELDED, event.id, pieces, 0)
            return
        }
        if (sheltered(level, player, event.shelter ?: UltraShelter.ROOF, state, now)) {
            state.exposure = (state.exposure - interval * 2).coerceAtLeast(0)
            send(player, state, UltraHazardStatus.SHELTERED, event.id, pieces, 0)
            return
        }
        val grace = UltraWeatherConfig.settings.grace
        val graceEnd = maxOf(state.arrival + grace.arrivalSeconds * 20L, active.start + grace.eventStartSeconds * 20L)
        if (now < graceEnd || UltraWarpTransfers.protects(player)) {
            send(player, state, UltraHazardStatus.GRACE, event.id, pieces, ((graceEnd - now + 19) / 20).toInt().coerceAtLeast(0))
            return
        }
        val status = if (protection > 0f) UltraHazardStatus.PARTIAL else UltraHazardStatus.EXPOSED
        if (state.status != UltraHazardStatus.EXPOSED && state.status != UltraHazardStatus.PARTIAL) {
            // Just exposed: the first hit comes after an interval, and a word on the action bar.
            state.lastHit = now
            player.displayClientMessage(Component.translatable("generations_core.ultra_weather.advice.${event.id}").withStyle(ChatFormatting.GOLD), true)
        }
        send(player, state, status, event.id, pieces, 0)
        state.exposure += interval
        if (now - state.lastHit < (damage.intervalSeconds * 20f).toLong()) return
        state.lastHit = now
        val base = if (event.progressive) minOf(damage.max, damage.amount + damage.rampPerMinute * state.exposure / 1200f) else damage.amount
        val amount = base * (1f - protection)
        if (amount <= 0f) return
        val holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(event.damageType).orElse(null) ?: return
        player.hurt(DamageSource(holder), amount)
        if (event == UltraWeatherEvent.ELECTRIC_STORM) {
            // The discharge: sparks on the player, seen by everyone around.
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.x, player.y + 1.0, player.z, 24, 0.4, 0.8, 0.4, 0.3)
            level.playSound(null, player.x, player.y, player.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 0.6f, 1.6f)
        }
    }

    private fun sheltered(level: ServerLevel, player: ServerPlayer, rule: UltraShelter, state: PlayerState, now: Long): Boolean {
        val pos = player.blockPosition().asLong()
        if (state.cachePos == pos && state.cacheRule == rule && now - state.cacheTime < 40) return state.cacheResult
        state.cachePos = pos
        state.cacheRule = rule
        state.cacheTime = now
        state.cacheResult = UltraShelters.sheltered(level, player, rule)
        return state.cacheResult
    }

    private fun send(player: ServerPlayer, state: PlayerState, status: UltraHazardStatus, event: String, pieces: Int, seconds: Int, force: Boolean = false) {
        val changed = force || state.status != status || state.sentEvent != event || state.sentPieces != pieces || state.sentSeconds != seconds
        state.status = status
        if (!changed) return
        state.sentEvent = event
        state.sentPieces = pieces
        state.sentSeconds = seconds
        S2CUltraHazardPacket(event, status.ordinal, pieces, seconds).sendToPlayer(player)
    }
}
