package generations.gg.generations.core.generationscore.common.world.ultraspace

import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.C2SUltraWarpRideStatePacket
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWarpRideEventPacket
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWarpRideUpdatePacket
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpCollision
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpCourse
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpDifficulty
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpRideLimits
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpRideState
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import kotlin.math.max
import kotlin.math.min

/**
 * The journey's gameplay on the server, the authority (the integrated server in solo,
 * the same code): it moves the journey along the tunnel each tick, drains the energy,
 * and works out the collisions from the positions the client sends, against the
 * course both build from the seed. The client is told what happened and follows.
 * Each player's journey is its own: nothing here is shared between sessions.
 */
object UltraWarpGameplay {
    /** Each server tick, for a journey in the tunnel (or being lost). */
    fun tick(session: UltraWarpSession, player: ServerPlayer, now: Long) {
        val ride = session.ride ?: return
        val config = ride.settings.config
        // Speed: the difficulty's at these light-years, more in a speed current (when
        // the Pokémon flies in its tube), less once the journey is lost; changing
        // gradually, faster in and out of a current.
        val tier = UltraWarpDifficulty.at(ride.settings, ride.lightYears)
        var target = config.speed * tier.speed.toDouble()
        // Each tier reached, its chances in the log (the mode counted).
        val reached = config.difficulty.tiers.indexOfLast { ride.lightYears >= it.lightYears }
        if (reached > ride.loggedTier) {
            ride.loggedTier = reached
            GenerationsCore.LOGGER.info(
                "Ultra Warp Ride: {} reached tier {} ({}): {}", player.gameProfile.name, reached, ride.settings.mode.id,
                UltraWarpDifficulty.line(config, UltraWarpDifficulty.withMode(config.difficulty.tiers[reached], ride.settings.mode)),
            )
        }
        val current = ride.course.windowAt(UltraWarpCourse.WindowKind.CURRENT, ride.distance)
        val inCurrent = current != null && UltraWarpRideLimits.length(ride.x - current.x, ride.y - current.y) <= config.objects.currentRadius
        if (inCurrent) target *= current!!.strength
        if (session.stage == UltraWarpSession.Stage.DEFEAT) target *= 0.35
        // Through a portal: faster and faster (only seen; nothing more can happen).
        if (session.stage == UltraWarpSession.Stage.CAPTURE) target *= 1.8
        val rate = config.difficulty.speedAcceleration / 20.0 * (if (inCurrent || current != null) 4 else 1)
        ride.speed += (target - ride.speed).coerceIn(-rate * 2, rate)
        ride.distance += ride.speed / 20.0
        if (session.stage == UltraWarpSession.Stage.RIDE && !config.test.invincible) {
            ride.energy = max(0.0, ride.energy - config.energy.drainPerSecond * ride.settings.mode.drain / 20.0)
        }
        if (now % 2 == 0L) S2CUltraWarpRideUpdatePacket(ride.distance, ride.speed.toFloat(), ride.energy.toFloat()).sendToPlayer(player)
        if (session.stage == UltraWarpSession.Stage.RIDE && now % 20 == 0L) events(ride, player, now)

        // Defeat: energy at 0. Once; the player loses control, then dies of it.
        if (session.stage == UltraWarpSession.Stage.RIDE && ride.energy <= 0.0 && !config.test.invincible) {
            session.beginDefeat()
            ride.defeatTick = now
            S2CUltraWarpRideEventPacket(S2CUltraWarpRideEventPacket.Type.DEFEAT, 0, 0f, config.defeat.seconds.toDouble()).sendToPlayer(player)
        }
        if (session.stage == UltraWarpSession.Stage.DEFEAT) {
            if (!ride.killSent && now - ride.defeatTick >= (config.defeat.seconds * 20).toLong()) {
                ride.killSent = true
                ride.killTick = now
                strike(player)
            }
            // The death was prevented (another mod): the journey ends anyway, no session stays stuck.
            if (ride.killSent && now - ride.killTick > 60) UltraWarpSessions.end(session.player, UltraWarpSession.EndReason.DEFEATED)
        }
        // Portals: placed ahead, left behind, the end through one.
        UltraWarpPortals.tick(session, player, ride, now)
    }

    enum class Event { STORM, CURRENT, ANOMALY, NECROZMA }

    private fun eventSettings(ride: UltraWarpRideState, event: Event) = with(ride.settings.config.events) {
        when (event) {
            Event.STORM -> storm
            Event.CURRENT -> current
            Event.ANOMALY -> anomaly
            Event.NECROZMA -> necrozma
        }
    }

    /**
     * Once per second: each event may start, if on, far enough (light-years), not
     * just had (its pause), with its chance per minute times the difficulty's.
     * Necrozma: once per journey.
     */
    private fun events(ride: UltraWarpRideState, player: ServerPlayer, now: Long) {
        val tier = UltraWarpDifficulty.at(ride.settings, ride.lightYears)
        for (event in Event.entries) {
            val settings = eventSettings(ride, event)
            if (!settings.enabled || ride.lightYears < settings.minLightYears) continue
            if (now < (ride.cooldownUntil[event.name] ?: 0L)) continue
            if (event == Event.NECROZMA && ride.necrozmaSeen) continue
            val chance = settings.chancePerMinute / 60.0 * tier.events
            if (ride.eventRandom.nextDouble() < chance) start(ride, player, event, now)
        }
    }

    /**
     * Starts an event. Storms and currents are stretches of the tunnel announced
     * leadDistance ahead, beyond what the client has built, so that both build the
     * same course over them; anomalies and Necrozma start at once, for a time.
     */
    fun start(ride: UltraWarpRideState, player: ServerPlayer, event: Event, now: Long) {
        val config = ride.settings.config
        val settings = eventSettings(ride, event)
        val seconds = settings.minSeconds + (settings.maxSeconds - settings.minSeconds) * ride.eventRandom.nextDouble()
        var busy = seconds
        when (event) {
            Event.STORM, Event.CURRENT -> {
                val from = ride.distance + config.events.leadDistance
                val to = from + seconds * ride.speed
                var x = 0.0
                var y = 0.0
                if (event == Event.CURRENT) {
                    // Where the Pokémon can fly into it.
                    val angle = ride.eventRandom.nextDouble() * 2 * Math.PI
                    val nx = StrictMath.cos(angle)
                    val ny = StrictMath.sin(angle)
                    val r = ride.eventRandom.nextDouble() * 0.6 * UltraWarpRideLimits.allowed(ride.tunnel, from, ride.shape, nx, ny)
                    x = nx * r
                    y = ny * r
                }
                val kind = if (event == Event.STORM) UltraWarpCourse.WindowKind.STORM else UltraWarpCourse.WindowKind.CURRENT
                val window = UltraWarpCourse.Window(ride.nextWindow++, kind, from, to, settings.strength, x, y)
                ride.course.addWindow(window)
                S2CUltraWarpRideEventPacket(S2CUltraWarpRideEventPacket.Type.WINDOW, window.id, window.strength, kind.ordinal.toDouble(), from, to, x, y).sendToPlayer(player)
                busy += config.events.leadDistance / ride.speed.coerceAtLeast(1.0)
            }
            Event.ANOMALY -> {
                val tier = UltraWarpDifficulty.at(ride.settings, ride.lightYears)
                S2CUltraWarpRideEventPacket(S2CUltraWarpRideEventPacket.Type.ANOMALY, ride.eventRandom.nextInt(1, 100000), settings.strength * tier.anomalyStrength, seconds).sendToPlayer(player)
            }
            Event.NECROZMA -> {
                ride.necrozmaSeen = true
                S2CUltraWarpRideEventPacket(S2CUltraWarpRideEventPacket.Type.NECROZMA, 0, 0f, seconds).sendToPlayer(player)
            }
        }
        ride.cooldownUntil[event.name] = now + ((busy + settings.cooldownSeconds) * 20).toLong()
        GenerationsCore.LOGGER.info(
            "Ultra Warp Ride: {} event {} at {} ly ({} s, strength {}; chance was ~{}/min)",
            player.gameProfile.name, event.name.lowercase(), "%.1f".format(ride.lightYears), "%.1f".format(seconds), settings.strength,
            "%.2f".format(settings.chancePerMinute * UltraWarpDifficulty.at(ride.settings, ride.lightYears).events),
        )
    }

    /** Damage of the Ultra Warp Ride's defeat (data/generations_core/damage_type/ultra_warp.json): goes through everything. */
    val DAMAGE: ResourceKey<DamageType> = ResourceKey.create(Registries.DAMAGE_TYPE, GenerationsCore.id("ultra_warp"))

    /**
     * The player is lost in the Ultra Wormhole: they die, with Minecraft's own death
     * and respawn (their death ends the journey, see UltraWarpRide). Their Pokémon stay
     * in their party.
     */
    fun strike(player: ServerPlayer) {
        val source = player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(DAMAGE)
            .map { DamageSource(it) }
            .orElseGet { player.damageSources().genericKill() }
        player.hurt(source, Float.MAX_VALUE)
    }

    /** /ultrawarp test energy: the energy set (at 0 the journey is lost). */
    fun setEnergy(ride: UltraWarpRideState, value: Double) {
        ride.energy = value.coerceIn(0.0, ride.settings.config.energy.max.toDouble())
    }

    /** /ultrawarp test distance: straight to that many light-years (its difficulty), nothing in between touched. */
    fun jump(ride: UltraWarpRideState, lightYears: Double) {
        val distance = lightYears / ride.settings.config.lightYearsPerBlock
        if (distance <= ride.distance) return
        ride.distance = distance
        ride.sampleDistance = distance
    }

    /**
     * Where the player steers (20 times per second). The distance is the client's,
     * kept within what latency allows (never ahead of the server, never far behind,
     * never backwards); the position within the limits there (the untilted shape: the
     * most room) plus the tolerance, else it is a gap, brought back inside. Then the
     * collisions between the last position and this one.
     */
    fun onState(session: UltraWarpSession, player: ServerPlayer, packet: C2SUltraWarpRideStatePacket, now: Long) {
        val ride = session.ride ?: return
        if (session.stage != UltraWarpSession.Stage.RIDE) return
        // One per tick at most: a flooding client gains nothing.
        if (now == ride.lastState) return
        ride.lastState = now
        val x = packet.x.toDouble()
        val y = packet.y.toDouble()
        if (!x.isFinite() || !y.isFinite() || !packet.distance.isFinite()) return
        val config = ride.settings.config
        val lag = ride.speed * config.sync.latencySeconds + 4.0
        val d = packet.distance.coerceIn(max(ride.sampleDistance, ride.distance - lag), max(ride.sampleDistance, ride.distance + 1.0))
        var px = x
        var py = y
        val length = UltraWarpRideLimits.length(x, y)
        if (length > 1e-6) {
            val allowed = UltraWarpRideLimits.allowed(ride.tunnel, d, ride.shape, x / length, y / length)
            if (length > allowed + config.sync.tolerance) {
                ride.gaps++
                px = x * allowed / length
                py = y * allowed / length
            }
        }
        collide(ride, player, d, px, py, now)
        // Through a portal's opening on the way from the last position to this one?
        UltraWarpPortals.cross(session, player, ride, ride.sampleDistance, ride.x, ride.y, d, px, py, now)
        ride.sampleDistance = d
        ride.x = px
        ride.y = py
        ride.boosting = packet.boost
        if (packet.dodge) ride.dodges++
    }

    /** What the Pokémon touched going from the last position to this one; each object counts once. */
    private fun collide(ride: UltraWarpRideState, player: ServerPlayer, d: Double, x: Double, y: Double, now: Long) {
        val config = ride.settings.config
        val items = ArrayList<UltraWarpCourse.Item>()
        ride.course.items(ride.sampleDistance - 12.0, d + 12.0, items)
        for (item in items) {
            if (item.id in ride.consumed) continue
            when (item.kind) {
                UltraWarpCourse.Kind.ORB -> {
                    if (!UltraWarpCollision.touches(item, ride.hitbox, ride.sampleDistance, ride.x, ride.y, d, x, y, config.objects.pickupScale.toDouble())) continue
                    ride.consumed.add(item.id)
                    ride.energy = min(config.energy.max.toDouble(), ride.energy + config.energy.orb)
                    ride.orbs++
                    event(player, S2CUltraWarpRideEventPacket.Type.COLLECT, item.id, ride.energy)
                }
                UltraWarpCourse.Kind.SPARK, UltraWarpCourse.Kind.DEBRIS, UltraWarpCourse.Kind.ARC -> {
                    if (!UltraWarpCollision.touches(item, ride.hitbox, ride.sampleDistance, ride.x, ride.y, d, x, y)) continue
                    ride.consumed.add(item.id)
                    // After a hit, nothing else hurts for a moment (no double hits).
                    if (now >= ride.invulnerableUntil && !config.test.invincible) {
                        val damage = when (item.kind) {
                            UltraWarpCourse.Kind.SPARK -> config.energy.spark
                            UltraWarpCourse.Kind.DEBRIS -> config.energy.debris
                            else -> config.energy.arc
                        } * ride.settings.mode.damage
                        ride.energy = max(0.0, ride.energy - damage)
                        ride.invulnerableUntil = now + (config.energy.invulnerableSeconds * 20).toLong()
                        ride.hits++
                    }
                    event(player, S2CUltraWarpRideEventPacket.Type.HIT, item.id, ride.energy)
                }
                UltraWarpCourse.Kind.GRAVITY -> {}
            }
        }
    }

    private fun event(player: ServerPlayer, type: S2CUltraWarpRideEventPacket.Type, id: Int, energy: Double) {
        S2CUltraWarpRideEventPacket(type, id, energy.toFloat()).sendToPlayer(player)
    }
}
