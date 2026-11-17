package generations.gg.generations.core.generationscore.common.network.packets.rks

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksConsoleBlockEntity
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksConsoleMenu
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksSacrifice
import generations.gg.generations.core.generationscore.common.world.rks.RksEnergy
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * A click of the RKS Machine's menu (RKS-PLAN.md § 4.1), checked by the server: the player has
 * that console's menu open and stands near it; revision is the machine's as the menu showed it
 * (a double click, or a click on a machine that changed in between, is refused).
 *
 * action: LAUNCH (recipe), ANALYSE, START (the M2 experiment, after its confirmation), LIST
 * (the sacrifice list's page `revision`, of the energy named by `recipe`: "psychic", "fire").
 */
class C2SRksActionPacket(val pos: BlockPos, val action: Int, val recipe: String, val revision: Int) : NetworkPacket<C2SRksActionPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(pos)
        buffer.writeVarInt(action)
        buffer.writeUtf(recipe)
        buffer.writeVarInt(revision)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("rks_action")
        const val LAUNCH = 0
        const val ANALYSE = 1
        const val START = 2
        const val LIST = 3

        fun decode(buf: RegistryFriendlyByteBuf) = C2SRksActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(256), buf.readVarInt())
    }
}

/** The Pokémon given to a reserve (kind: RksEnergy's ordinal), by UUID (all checked again by the server). */
class C2SRksSacrificePacket(val pos: BlockPos, val kind: Int, val revision: Int, val pokemon: List<UUID>) : NetworkPacket<C2SRksSacrificePacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(pos)
        buffer.writeVarInt(kind)
        buffer.writeVarInt(revision)
        buffer.writeVarInt(pokemon.size)
        pokemon.forEach { buffer.writeUUID(it) }
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("rks_sacrifice")
        const val MAX = 120

        fun decode(buf: RegistryFriendlyByteBuf): C2SRksSacrificePacket {
            val pos = buf.readBlockPos()
            val kind = buf.readVarInt()
            val revision = buf.readVarInt()
            val count = buf.readVarInt().coerceIn(0, MAX)
            return C2SRksSacrificePacket(pos, kind, revision, List(count) { buf.readUUID() })
        }
    }
}

/**
 * A page of the Pokémon this player may give to a reserve (kind: RksEnergy's ordinal; party
 * first, then the PC), with their points; sent on request only.
 */
class S2CRksSacrificeListPacket(
    val pos: BlockPos,
    val kind: Int,
    val page: Int,
    val pages: Int,
    val total: Int,
    val energy: Int,
    val required: Int,
    val revision: Int,
    val entries: List<Entry>,
    /** A reason the player cannot give any now (a battle, a trade), as a translation key; "" if none. */
    val blocked: String,
) : NetworkPacket<S2CRksSacrificeListPacket> {
    /** place: -1 the party, otherwise the PC box (from 0). */
    class Entry(val uuid: UUID, val species: String, val aspects: List<String>, val nickname: String, val level: Int, val points: Int, val place: Int, val shiny: Boolean)

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(pos)
        buffer.writeVarInt(kind)
        buffer.writeVarInt(page)
        buffer.writeVarInt(pages)
        buffer.writeVarInt(total)
        buffer.writeVarInt(energy)
        buffer.writeVarInt(required)
        buffer.writeVarInt(revision)
        buffer.writeVarInt(entries.size)
        for (e in entries) {
            buffer.writeUUID(e.uuid)
            buffer.writeUtf(e.species)
            buffer.writeVarInt(e.aspects.size)
            e.aspects.forEach { buffer.writeUtf(it) }
            buffer.writeUtf(e.nickname)
            buffer.writeVarInt(e.level)
            buffer.writeVarInt(e.points)
            buffer.writeVarInt(e.place + 1)
            buffer.writeBoolean(e.shiny)
        }
        buffer.writeUtf(blocked)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("rks_sacrifice_list")
        const val PAGE_SIZE = 18

        fun decode(buf: RegistryFriendlyByteBuf): S2CRksSacrificeListPacket {
            val pos = buf.readBlockPos()
            val kind = buf.readVarInt()
            val page = buf.readVarInt()
            val pages = buf.readVarInt()
            val total = buf.readVarInt()
            val energy = buf.readVarInt()
            val required = buf.readVarInt()
            val revision = buf.readVarInt()
            val count = buf.readVarInt().coerceIn(0, 64)
            val entries = List(count) {
                val uuid = buf.readUUID()
                val species = buf.readUtf(256)
                val aspects = List(buf.readVarInt().coerceIn(0, 32)) { buf.readUtf(128) }
                Entry(uuid, species, aspects, buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1, buf.readBoolean())
            }
            return S2CRksSacrificeListPacket(pos, kind, page, pages, total, energy, required, revision, entries, buf.readUtf(256))
        }
    }
}

object RksPacketHandlers {
    /** The console of the menu this player has open, if it is that one and they are near it. */
    private fun console(player: ServerPlayer, pos: BlockPos): RksConsoleBlockEntity? {
        val menu = player.containerMenu as? RksConsoleMenu ?: return null
        if (menu.pos != pos || player.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) > 64.0) return null
        return player.level().getBlockEntity(pos) as? RksConsoleBlockEntity
    }

    object Action : ServerNetworkPacketHandler<C2SRksActionPacket> {
        override fun handle(packet: C2SRksActionPacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute {
                val console = console(player, packet.pos) ?: return@execute
                when (packet.action) {
                    C2SRksActionPacket.LAUNCH -> ResourceLocation.tryParse(packet.recipe)?.let { console.launch(player, it, packet.revision) }
                    C2SRksActionPacket.ANALYSE -> console.analyse(player, packet.revision)
                    C2SRksActionPacket.START -> console.startExperiment(player, packet.revision)
                    C2SRksActionPacket.LIST -> RksSacrifice.sendList(player, console, packet.revision, RksEnergy.byId(packet.recipe) ?: RksEnergy.PSYCHIC)
                }
            }
        }
    }

    object Sacrifice : ServerNetworkPacketHandler<C2SRksSacrificePacket> {
        override fun handle(packet: C2SRksSacrificePacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute {
                val console = console(player, packet.pos) ?: return@execute
                val kind = RksEnergy.entries.getOrNull(packet.kind) ?: return@execute
                RksSacrifice.sacrifice(player, console, packet.revision, packet.pokemon, kind)
            }
        }
    }

    object SacrificeList : ClientNetworkPacketHandler<S2CRksSacrificeListPacket> {
        override fun handle(packet: S2CRksSacrificeListPacket, client: Minecraft) {
            client.execute { generations.gg.generations.core.generationscore.common.client.rks.RksConsoleScreen.receive(packet) }
        }
    }
}
