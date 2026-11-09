package generations.gg.generations.core.generationscore.common.network.packets.raid

import com.cobblemon.mod.common.api.net.NetworkPacket
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.UUID

/** A Pokémon the player can bring to a raid: from the party (box -1) or a PC box. */
class RaidPick(val uuid: UUID, val species: String, val aspects: List<String>, val label: String, val box: Int, val slot: Int) {
    fun encode(buf: FriendlyByteBuf) {
        buf.writeUUID(uuid)
        buf.writeUtf(species)
        buf.writeCollection(aspects, FriendlyByteBuf::writeUtf)
        buf.writeUtf(label)
        buf.writeVarInt(box + 1)
        buf.writeVarInt(slot)
    }

    companion object {
        fun decode(buf: FriendlyByteBuf) = RaidPick(
            buf.readUUID(), buf.readUtf(), buf.readList(FriendlyByteBuf::readUtf), buf.readUtf(), buf.readVarInt() - 1, buf.readVarInt(),
        )
    }
}

/** A participant shown in the lobby: the player, another player or an NPC. */
class RaidLobbyMember(val name: String, val label: String, val species: String, val aspects: List<String>, val kind: Int, val ready: Boolean) {
    fun encode(buf: FriendlyByteBuf) {
        buf.writeUtf(name)
        buf.writeUtf(label)
        buf.writeUtf(species)
        buf.writeCollection(aspects, FriendlyByteBuf::writeUtf)
        buf.writeVarInt(kind)
        buf.writeBoolean(ready)
    }

    companion object {
        const val YOU = 0
        const val PLAYER = 1
        const val NPC = 2

        fun decode(buf: FriendlyByteBuf) = RaidLobbyMember(
            buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readList(FriendlyByteBuf::readUtf), buf.readVarInt(), buf.readBoolean(),
        )
    }
}

/** Server to client: a den's lobby (silhouette, tier, the four participants, the Pokémon to bring, time left). */
class S2CRaidLobbyPacket(
    val pos: BlockPos,
    val stars: Int,
    /** Species id and aspects of the raid Pokémon, shown as a silhouette. */
    val species: String,
    val aspects: List<String>,
    /** Players first, then the NPCs of the places left. */
    val members: List<RaidLobbyMember>,
    /** Every Pokémon of the player, party first, then the PC. */
    val picks: List<RaidPick>,
    val selected: UUID?,
    val ready: Boolean,
    val secondsLeft: Int,
    val maxFaints: Int = 4,
    val maxTurns: Int = 10,
) : NetworkPacket<S2CRaidLobbyPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(pos)
        buffer.writeVarInt(stars)
        buffer.writeUtf(species)
        buffer.writeCollection(aspects, FriendlyByteBuf::writeUtf)
        buffer.writeCollection(members) { buf, member -> member.encode(buf) }
        buffer.writeCollection(picks) { buf, pick -> pick.encode(buf) }
        buffer.writeNullable(selected) { buf, uuid -> buf.writeUUID(uuid) }
        buffer.writeBoolean(ready)
        buffer.writeVarInt(secondsLeft)
        buffer.writeVarInt(maxFaints)
        buffer.writeVarInt(maxTurns)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("raid_lobby")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CRaidLobbyPacket(
            buf.readBlockPos(), buf.readVarInt(), buf.readUtf(), buf.readList(FriendlyByteBuf::readUtf),
            buf.readList(RaidLobbyMember::decode), buf.readList(RaidPick::decode), buf.readNullable { it.readUUID() },
            buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
        )
    }
}

/** Server to client: the lobby closes (the raid starts, or the player left it). */
class S2CRaidLobbyClosePacket : NetworkPacket<S2CRaidLobbyClosePacket> {
    override val id: ResourceLocation = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {}

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("raid_lobby_close")
        fun decode(buf: RegistryFriendlyByteBuf) = S2CRaidLobbyClosePacket()
    }
}

/** Client to server: chooses the Pokémon to bring, toggles ready, or leaves the lobby. */
class C2SRaidLobbyPacket(val pos: BlockPos, val action: Action, val pokemon: UUID?) : NetworkPacket<C2SRaidLobbyPacket> {
    enum class Action { SELECT, READY, QUIT }

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(pos)
        buffer.writeEnum(action)
        buffer.writeNullable(pokemon) { buf, uuid -> buf.writeUUID(uuid) }
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("raid_lobby_action")

        fun decode(buf: RegistryFriendlyByteBuf) = C2SRaidLobbyPacket(buf.readBlockPos(), buf.readEnum(Action::class.java), buf.readNullable { it.readUUID() })
    }
}

/** One participant in the raid HUD. */
class RaidHudMember(
    val name: String, val label: String, val hp: Int, val maxHp: Int, val down: Boolean,
    val species: String, val aspects: List<String>, val kind: Int,
) {
    fun encode(buf: FriendlyByteBuf) {
        buf.writeUtf(name)
        buf.writeUtf(label)
        buf.writeVarInt(hp)
        buf.writeVarInt(maxHp)
        buf.writeBoolean(down)
        buf.writeUtf(species)
        buf.writeCollection(aspects, FriendlyByteBuf::writeUtf)
        buf.writeVarInt(kind)
    }

    companion object {
        const val YOU = 0
        const val PLAYER = 1
        const val NPC = 2

        fun decode(buf: FriendlyByteBuf) = RaidHudMember(
            buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(),
            buf.readList(FriendlyByteBuf::readUtf), buf.readVarInt(),
        )
    }
}

/** Server to client: the raid status shown during the battle (participants, turns, faints, shield). */
class S2CRaidHudPacket(
    val active: Boolean,
    val turn: Int = 0,
    val maxTurns: Int = 0,
    val faints: Int = 0,
    val maxFaints: Int = 0,
    val members: List<RaidHudMember> = emptyList(),
    val stars: Int = 0,
    val shieldActive: Boolean = false,
    val shieldSegments: Int = 0,
    val shieldMax: Int = 0,
    /** The raid battle this status belongs to: never shown in another battle. */
    val battleId: UUID? = null,
) : NetworkPacket<S2CRaidHudPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(active)
        buffer.writeVarInt(turn)
        buffer.writeVarInt(maxTurns)
        buffer.writeVarInt(faints)
        buffer.writeVarInt(maxFaints)
        buffer.writeCollection(members) { buf, member -> member.encode(buf) }
        buffer.writeVarInt(stars)
        buffer.writeBoolean(shieldActive)
        buffer.writeVarInt(shieldSegments)
        buffer.writeVarInt(shieldMax)
        buffer.writeBoolean(battleId != null)
        battleId?.let(buffer::writeUUID)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("raid_hud")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CRaidHudPacket(
            buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readList(RaidHudMember::decode), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
            if (buf.readBoolean()) buf.readUUID() else null,
        )
    }
}

/** Server to client: the player's Pokémon is down, they may cheer the team for a few seconds. */
class S2CRaidCheerPacket(val seconds: Int) : NetworkPacket<S2CRaidCheerPacket> {
    override val id: ResourceLocation = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(seconds)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("raid_cheer")
        fun decode(buf: RegistryFriendlyByteBuf) = S2CRaidCheerPacket(buf.readVarInt())
    }
}

/** Client to server: cheers the team, or not. */
class C2SRaidCheerPacket(val accept: Boolean) : NetworkPacket<C2SRaidCheerPacket> {
    override val id: ResourceLocation = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(accept)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("raid_cheer_action")
        fun decode(buf: RegistryFriendlyByteBuf) = C2SRaidCheerPacket(buf.readBoolean())
    }
}

/** Server to client: end of a won raid (catch choice and its outcome, rewards). */
class S2CRaidResultPacket(
    val species: String,
    val aspects: List<String>,
    val stars: Int,
    val rewards: List<ItemStack>,
    val state: State,
    /** Item id of the Poké Ball thrown, empty before the throw. */
    val ball: String = "",
) : NetworkPacket<S2CRaidResultPacket> {
    enum class State { CHOICE, CAUGHT, ESCAPED, SKIPPED }

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(species)
        buffer.writeCollection(aspects, FriendlyByteBuf::writeUtf)
        buffer.writeVarInt(stars)
        buffer.writeVarInt(rewards.size)
        rewards.forEach { ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, it) }
        buffer.writeEnum(state)
        buffer.writeUtf(ball)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("raid_result")

        fun decode(buf: RegistryFriendlyByteBuf): S2CRaidResultPacket {
            val species = buf.readUtf()
            val aspects = buf.readList(FriendlyByteBuf::readUtf)
            val stars = buf.readVarInt()
            val rewards = List(buf.readVarInt()) { ItemStack.OPTIONAL_STREAM_CODEC.decode(buf) }
            return S2CRaidResultPacket(species, aspects, stars, rewards, buf.readEnum(State::class.java), buf.readUtf())
        }
    }
}

/** Client to server: catch with the Poké Ball in this inventory slot, let it go, claim the rewards, or close. */
class C2SRaidResultPacket(val action: Action, val slot: Int = -1) : NetworkPacket<C2SRaidResultPacket> {
    enum class Action { CATCH, SKIP, CLAIM, CLOSE }

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnum(action)
        buffer.writeVarInt(slot)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("raid_result_action")

        fun decode(buf: RegistryFriendlyByteBuf) = C2SRaidResultPacket(buf.readEnum(Action::class.java), buf.readVarInt())
    }
}
