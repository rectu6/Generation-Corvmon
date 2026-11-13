package generations.gg.generations.core.generationscore.common.network.packets.ultraspace

import com.cobblemon.mod.common.api.net.NetworkPacket
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpRide
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpVariant
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Server to client: an altar's Ultra Warp Ride menu, opened or refreshed. What it
 * shows (Pokémon present, places) is only a preview: starting checks it all again.
 */
class S2CUltraWarpPacket(
    val pos: BlockPos,
    val variant: UltraWarpVariant,
    val hasPokemon: Boolean,
    val freeSlots: Int,
    val maxSessions: Int,
    /** The face of the player's journey in progress, null when they have none. */
    val session: UltraWarpVariant?,
    /** Answer to the player's last action, null when the menu just opens. */
    val result: UltraWarpRide.Result?,
    /** The server's difficulty mode (its id), shown in the menu. */
    val mode: String = "normal",
) : NetworkPacket<S2CUltraWarpPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(pos)
        buffer.writeEnum(variant)
        buffer.writeBoolean(hasPokemon)
        buffer.writeVarInt(freeSlots)
        buffer.writeVarInt(maxSessions)
        buffer.writeNullable(session) { buf, value -> buf.writeEnum(value) }
        buffer.writeNullable(result) { buf, value -> buf.writeEnum(value) }
        buffer.writeUtf(mode)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ultra_warp_ride")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CUltraWarpPacket(
            buf.readBlockPos(),
            buf.readEnum(UltraWarpVariant::class.java),
            buf.readBoolean(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readNullable { it.readEnum(UltraWarpVariant::class.java) },
            buf.readNullable { it.readEnum(UltraWarpRide.Result::class.java) },
            buf.readUtf(),
        )
    }
}

/**
 * Client to server: start the journey at this altar, or end the one in progress.
 * variant is the face the menu showed, so that a change meanwhile is told.
 */
class C2SUltraWarpActionPacket(val pos: BlockPos, val action: Action, val variant: UltraWarpVariant) : NetworkPacket<C2SUltraWarpActionPacket> {
    enum class Action { START, END }

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(pos)
        buffer.writeEnum(action)
        buffer.writeEnum(variant)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ultra_warp_ride_action")

        fun decode(buf: RegistryFriendlyByteBuf) = C2SUltraWarpActionPacket(
            buf.readBlockPos(), buf.readEnum(Action::class.java), buf.readEnum(UltraWarpVariant::class.java),
        )
    }
}
