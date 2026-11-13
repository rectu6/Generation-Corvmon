package generations.gg.generations.core.generationscore.common.network.packets.ultraspace

import com.cobblemon.mod.common.api.net.NetworkPacket
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpOutcome
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpSession
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpPortal
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpVariant
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpRideSettings
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTransfers
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Server to client: the journey begins. Everything the client needs to draw it the
 * same way the server counts it: the seed and the settings (tunnel, course,
 * difficulty), and the server game time it began at. Distance and energy then come
 * with S2CUltraWarpRideUpdatePacket.
 */
class S2CUltraWarpRideStartPacket(
    val variant: UltraWarpVariant,
    /** Look of the Pokémon ridden (shiny...). */
    val aspects: List<String>,
    val seed: Long,
    val startTick: Long,
    val settings: UltraWarpRideSettings,
    /** The journey ends by itself after this many seconds; 0 = never. */
    val maxDurationSeconds: Int,
) : NetworkPacket<S2CUltraWarpRideStartPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnum(variant)
        buffer.writeCollection(aspects, FriendlyByteBuf::writeUtf)
        buffer.writeLong(seed)
        buffer.writeLong(startTick)
        settings.encode(buffer)
        buffer.writeVarInt(maxDurationSeconds)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ultra_warp_ride_start")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CUltraWarpRideStartPacket(
            buf.readEnum(UltraWarpVariant::class.java),
            buf.readList(FriendlyByteBuf::readUtf),
            buf.readLong(),
            buf.readLong(),
            UltraWarpRideSettings.decode(buf),
            buf.readVarInt(),
        )
    }
}

/** Server to client: the journey is over, back to the world, with how it ended (the server's record of it). */
class S2CUltraWarpRideStopPacket(val reason: UltraWarpSession.EndReason, val outcome: UltraWarpOutcome? = null) : NetworkPacket<S2CUltraWarpRideStopPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnum(reason)
        buffer.writeNullable(outcome) { buf, value -> value.encode(buf) }
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ultra_warp_ride_stop")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CUltraWarpRideStopPacket(
            buf.readEnum(UltraWarpSession.EndReason::class.java),
            buf.readNullable { UltraWarpOutcome.decode(it) },
        )
    }
}

/**
 * Server to client: a portal placed ahead, as the server drew it (place, destination,
 * colour, rarity level): the client draws exactly this one.
 */
class S2CUltraWarpPortalPacket(val portal: UltraWarpPortal) : NetworkPacket<S2CUltraWarpPortalPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        portal.encode(buffer)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ultra_warp_portal")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CUltraWarpPortalPacket(UltraWarpPortal.decode(buf))
    }
}

/**
 * Server to client, 10 times per second: where the journey is by the server (the
 * authority), for the client to follow smoothly: distance along the tunnel, speed,
 * energy.
 */
class S2CUltraWarpRideUpdatePacket(val distance: Double, val speed: Float, val energy: Float) : NetworkPacket<S2CUltraWarpRideUpdatePacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeDouble(distance)
        buffer.writeFloat(speed)
        buffer.writeFloat(energy)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ultra_warp_ride_update")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CUltraWarpRideUpdatePacket(buf.readDouble(), buf.readFloat(), buf.readFloat())
    }
}

/**
 * Server to client: something happened in the journey, decided by the server.
 * - COLLECT, HIT: object id taken or touched; value = energy after.
 * - WINDOW: storm or speed current over the tunnel, announced ahead: id, a = kind
 *   (UltraWarpCourse.WindowKind), b..c = from..to (distance), value = strength,
 *   d, e = place across the tunnel (current).
 * - ANOMALY: the steering drifts for a = seconds, value = strength, b = its own seed.
 * - NECROZMA: Necrozma passes by during a = seconds.
 * - DEFEAT: the energy ran out; a = seconds before the end.
 */
class S2CUltraWarpRideEventPacket(
    val type: Type,
    val eventId: Int,
    val value: Float,
    val a: Double = 0.0,
    val b: Double = 0.0,
    val c: Double = 0.0,
    val d: Double = 0.0,
    val e: Double = 0.0,
) : NetworkPacket<S2CUltraWarpRideEventPacket> {
    enum class Type { COLLECT, HIT, WINDOW, ANOMALY, NECROZMA, DEFEAT, PORTAL_CAPTURE }

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnum(type)
        buffer.writeVarInt(eventId)
        buffer.writeFloat(value)
        buffer.writeDouble(a)
        buffer.writeDouble(b)
        buffer.writeDouble(c)
        buffer.writeDouble(d)
        buffer.writeDouble(e)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ultra_warp_ride_event")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CUltraWarpRideEventPacket(
            buf.readEnum(Type::class.java), buf.readVarInt(), buf.readFloat(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
        )
    }
}

/**
 * Client to server, 20 times per second during the journey: where the player steers
 * their Pokémon, at which distance along the tunnel (the client's own, which the
 * server checks against its), across the tunnel (x sideways, y up from the axis, in
 * blocks), and the boost and dodge keys. The server checks it against the same
 * limits and works out the collisions from it.
 */
class C2SUltraWarpRideStatePacket(val distance: Double, val x: Float, val y: Float, val boost: Boolean, val dodge: Boolean) : NetworkPacket<C2SUltraWarpRideStatePacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeDouble(distance)
        buffer.writeFloat(x)
        buffer.writeFloat(y)
        buffer.writeBoolean(boost)
        buffer.writeBoolean(dodge)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ultra_warp_ride_state")

        fun decode(buf: RegistryFriendlyByteBuf) = C2SUltraWarpRideStatePacket(buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean())
    }
}

/**
 * Server to client: a move between the worlds (UltraWarpTransfers). STARTED: the
 * client hides the world behind its white screen while the place loads (a journey's
 * arrival opens it itself, from the stop packet); ARRIVED: the player is there (sent
 * after the change of dimension), with the journey's outcome for its result screen;
 * FAILED: the player stays where they were.
 */
class S2CUltraWarpTransferPacket(
    val state: State,
    val kind: UltraWarpTransfers.Kind,
    /** The world (its id, which is also its destination's), empty for the return. */
    val world: String,
    val color: Int,
    val outcome: UltraWarpOutcome?,
) : NetworkPacket<S2CUltraWarpTransferPacket> {
    enum class State { STARTED, ARRIVED, FAILED }

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnum(state)
        buffer.writeEnum(kind)
        buffer.writeUtf(world)
        buffer.writeInt(color)
        buffer.writeNullable(outcome) { buf, value -> value.encode(buf) }
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ultra_warp_transfer")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CUltraWarpTransferPacket(
            buf.readEnum(State::class.java),
            buf.readEnum(UltraWarpTransfers.Kind::class.java),
            buf.readUtf(),
            buf.readInt(),
            buf.readNullable { UltraWarpOutcome.decode(it) },
        )
    }
}
