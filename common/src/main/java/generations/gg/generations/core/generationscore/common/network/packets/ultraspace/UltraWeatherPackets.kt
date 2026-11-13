package generations.gg.generations.core.generationscore.common.network.packets.ultraspace

import com.cobblemon.mod.common.api.net.NetworkPacket
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Server to client: the weather of the player's Ultra world (phase 4.4), sent when it
 * changes, at login, on a change of dimension and on respawning. world empty: not in an
 * Ultra world; event empty: calm. The client computes the fades from the game time, so
 * a player arriving during an event sees it as the others do.
 */
class S2CUltraWeatherPacket(
    val world: String,
    val event: String,
    val start: Long,
    val end: Long,
    val wind: Float,
    val fade: Int,
) : NetworkPacket<S2CUltraWeatherPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(world)
        buffer.writeUtf(event)
        buffer.writeLong(start)
        buffer.writeLong(end)
        buffer.writeFloat(wind)
        buffer.writeVarInt(fade)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ultra_weather")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CUltraWeatherPacket(
            buf.readUtf(), buf.readUtf(), buf.readLong(), buf.readLong(), buf.readFloat(), buf.readVarInt(),
        )
    }
}

/**
 * Server to client: where this player stands against the dangerous weather of their
 * world (the indicator): status is UltraHazardStatus' ordinal; pieces, the Ultra pieces
 * worn that carry its shield; seconds, the grace left.
 */
class S2CUltraHazardPacket(
    val event: String,
    val status: Int,
    val pieces: Int,
    val seconds: Int,
) : NetworkPacket<S2CUltraHazardPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(event)
        buffer.writeVarInt(status)
        buffer.writeVarInt(pieces)
        buffer.writeVarInt(seconds)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ultra_hazard")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CUltraHazardPacket(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
    }
}
