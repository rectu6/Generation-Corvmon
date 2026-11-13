package generations.gg.generations.core.generationscore.common.network.packets.ultraspace

import com.cobblemon.mod.common.api.net.NetworkPacket
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.rift.UltraRiftView
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * The rifts of the player's world (phase 6), for drawing only: FULL replaces the
 * client's list (on joining a world), ADD opens one, CLOSE (it expired) and COLLAPSE
 * (it was crossed) end one by its id. Never a destination's id, only its colour.
 */
class S2CUltraRiftPacket(val action: Action, val rifts: List<UltraRiftView>, val riftId: Int) : NetworkPacket<S2CUltraRiftPacket> {
    enum class Action { FULL, ADD, CLOSE, COLLAPSE }

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnum(action)
        buffer.writeVarInt(riftId)
        buffer.writeVarInt(rifts.size)
        rifts.forEach { it.encode(buffer) }
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ultra_rift")

        fun decode(buf: RegistryFriendlyByteBuf): S2CUltraRiftPacket {
            val action = buf.readEnum(Action::class.java)
            val riftId = buf.readVarInt()
            val count = buf.readVarInt().coerceIn(0, 256)
            return S2CUltraRiftPacket(action, List(count) { UltraRiftView.decode(buf) }, riftId)
        }
    }
}

/**
 * "Back to the Overworld" chosen on the interaction wheel of a Solgaleo or Lunala: only
 * the Pokémon's entity id; the server checks everything again (UltraReturn).
 */
class C2SUltraReturnPacket(val entity: UUID) : NetworkPacket<C2SUltraReturnPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(entity)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ultra_return")

        fun decode(buf: RegistryFriendlyByteBuf) = C2SUltraReturnPacket(buf.readUUID())
    }
}
