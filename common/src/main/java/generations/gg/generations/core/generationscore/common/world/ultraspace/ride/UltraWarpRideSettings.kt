package generations.gg.generations.core.generationscore.common.world.ultraspace.ride

import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraSpaceConfig
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpVariant
import net.minecraft.network.FriendlyByteBuf

/**
 * What a journey uses: the server's settings when it began (a copy), the Pokémon
 * ridden and the difficulty mode chosen. Sent whole to the client (as JSON) with the seed, so that both build the
 * same tunnel and course (UltraWarpTunnel, UltraWarpCourse) and apply the same limits
 * (UltraWarpRideLimits).
 */
class UltraWarpRideSettings(val variant: UltraWarpVariant, val config: UltraSpaceConfig.Ride, val modeId: String) {
    /** The difficulty mode chosen for this journey. */
    val mode: UltraSpaceConfig.Mode = config.modeOf(modeId)

    /** Initial speed, blocks per second along the tunnel. */
    val speed: Float get() = config.speed
    val segmentLength: Float get() = config.tunnel.segmentLength
    val curveIntensity: Float get() = config.tunnel.curveIntensity
    val curveChangeSegments: Int get() = config.tunnel.curveChangeSegments
    val maxPitch: Float get() = config.tunnel.maxPitch
    val radius: Float get() = config.tunnel.radius
    val radiusVariation: Float get() = config.tunnel.radiusVariation
    val steerSpeed: Float get() = config.steering.maxSpeed
    val steerAcceleration: Float get() = config.steering.acceleration
    val steerDeceleration: Float get() = config.steering.deceleration
    /** The Pokémon ridden: its sizes. */
    val rider: UltraSpaceConfig.RiderSize get() = if (variant == UltraWarpVariant.SUN) config.riders.sun else config.riders.moon
    val hitRadius: Float get() = rider.hitRadius
    val maxFill: Float get() = config.riders.maxFill
    val statesPerSecond: Int get() = config.sync.statesPerSecond

    fun encode(buf: FriendlyByteBuf) {
        buf.writeEnum(variant)
        buf.writeUtf(UltraSpaceConfig.toJson(config), MAX_JSON)
        buf.writeUtf(modeId)
    }

    companion object {
        private const val MAX_JSON = 65536

        fun decode(buf: FriendlyByteBuf) = UltraWarpRideSettings(
            buf.readEnum(UltraWarpVariant::class.java),
            UltraSpaceConfig.fromJson(buf.readUtf(MAX_JSON)),
            buf.readUtf(),
        )
    }
}
