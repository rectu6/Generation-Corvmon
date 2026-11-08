package generations.gg.generations.core.generationscore.common.network.packets.pokewand

import com.cobblemon.mod.common.api.net.NetworkPacket
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.pokewand.PokeWandPokemon
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/** Server to client: opens (or refreshes) the Pokewand screen on a player's party. */
class S2CPokeWandPartyPacket(
    val target: UUID,
    val targetName: String,
    /** Six party slots, null when empty. */
    val slots: List<PokeWandPokemon?>,
) : NetworkPacket<S2CPokeWandPartyPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(target)
        buffer.writeUtf(targetName)
        buffer.writeVarInt(slots.size)
        slots.forEach { slot -> buffer.writeNullable(slot) { buf, pokemon -> pokemon.encode(buf) } }
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("pokewand_party")

        fun decode(buf: RegistryFriendlyByteBuf): S2CPokeWandPartyPacket = S2CPokeWandPartyPacket(
            buf.readUUID(),
            buf.readUtf(),
            List(buf.readVarInt()) { buf.readNullable(PokeWandPokemon::decode) },
        )
    }
}

/** Client to server: an action of the Pokewand screen on a player's party. */
class C2SPokeWandActionPacket(
    val target: UUID,
    val action: Action,
    val slot: Int = 0,
    val pokemon: PokeWandPokemon? = null,
) : NetworkPacket<C2SPokeWandActionPacket> {
    enum class Action { EDIT, DELETE, OPEN_PC, REFRESH }

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(target)
        buffer.writeEnum(action)
        buffer.writeVarInt(slot)
        buffer.writeNullable(pokemon) { buf, pokemon -> pokemon.encode(buf) }
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("pokewand_action")

        fun decode(buf: RegistryFriendlyByteBuf): C2SPokeWandActionPacket = C2SPokeWandActionPacket(
            buf.readUUID(),
            buf.readEnum(Action::class.java),
            buf.readVarInt(),
            buf.readNullable(PokeWandPokemon::decode),
        )
    }
}
