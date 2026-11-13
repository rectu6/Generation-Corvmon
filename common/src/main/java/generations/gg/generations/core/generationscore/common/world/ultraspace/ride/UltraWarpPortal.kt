package generations.gg.generations.core.generationscore.common.world.ultraspace.ride

import net.minecraft.network.FriendlyByteBuf

/**
 * A dimensional portal of a journey, as the server placed it (and told the client):
 * where it is (its distance along the tunnel, its middle across it), where it leads
 * (destination id and colour, from the config) and its rarity level (1 simple, 2 one
 * ring, 3 two rings, 4 exceptional), drawn apart. group: portals of the same
 * convergence share it (0: alone).
 */
class UltraWarpPortal(
    val id: Int,
    val distance: Double,
    val x: Double,
    val y: Double,
    val destination: String,
    val color: Int,
    val rarity: Int,
    val group: Int,
) {
    enum class State {
        /** Ahead, waiting. */
        ANNOUNCED,
        /** The Pokémon went in: the journey ends with it. */
        CAPTURED,
        /** Left behind. */
        PASSED,
    }

    var state = State.ANNOUNCED

    val open: Boolean get() = state == State.ANNOUNCED

    fun encode(buf: FriendlyByteBuf) {
        buf.writeVarInt(id)
        buf.writeDouble(distance)
        buf.writeDouble(x)
        buf.writeDouble(y)
        buf.writeUtf(destination)
        buf.writeInt(color)
        buf.writeVarInt(rarity)
        buf.writeVarInt(group)
    }

    companion object {
        fun decode(buf: FriendlyByteBuf) = UltraWarpPortal(
            buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUtf(), buf.readInt(), buf.readVarInt(), buf.readVarInt(),
        )
    }
}
