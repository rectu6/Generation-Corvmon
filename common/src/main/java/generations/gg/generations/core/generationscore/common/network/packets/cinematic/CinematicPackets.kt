package generations.gg.generations.core.generationscore.common.network.packets.cinematic

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.NetworkPacket
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicEndReason
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * A legendary cinematic starts (world/cinematic/LegendaryCinematics), sent once to each
 * participant. Nothing else is sent while it plays: each client computes the whole scene
 * from this. startGameTime is the session level's game time of the scene's second 0 (half a
 * second ahead, for the latency); a late client catches the scene up at the right moment.
 */
class S2CCinematicStartPacket(
    val session: UUID,
    val scene: String,
    /** The shrine (bottom centre of its block), the way it faces (Minecraft yaw), the Pokémon's landing point. */
    val origin: Vec3,
    val facing: Float,
    val spawn: Vec3,
    val startGameTime: Long,
    val durationTicks: Int,
    val invoker: UUID,
    val species: String,
    val aspects: List<String>,
    val test: Boolean,
    val debug: Boolean,
) : NetworkPacket<S2CCinematicStartPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(session)
        buffer.writeUtf(scene)
        writeVec(buffer, origin)
        buffer.writeFloat(facing)
        writeVec(buffer, spawn)
        buffer.writeLong(startGameTime)
        buffer.writeVarInt(durationTicks)
        buffer.writeUUID(invoker)
        buffer.writeUtf(species)
        buffer.writeVarInt(aspects.size)
        aspects.forEach { buffer.writeUtf(it) }
        buffer.writeBoolean(test)
        buffer.writeBoolean(debug)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("cinematic_start")

        private fun writeVec(buffer: RegistryFriendlyByteBuf, v: Vec3) {
            buffer.writeDouble(v.x)
            buffer.writeDouble(v.y)
            buffer.writeDouble(v.z)
        }

        private fun readVec(buffer: RegistryFriendlyByteBuf) = Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())

        fun decode(buf: RegistryFriendlyByteBuf): S2CCinematicStartPacket {
            val session = buf.readUUID()
            val scene = buf.readUtf(64)
            val origin = readVec(buf)
            val facing = buf.readFloat()
            val spawn = readVec(buf)
            val start = buf.readLong()
            val duration = buf.readVarInt()
            val invoker = buf.readUUID()
            val species = buf.readUtf(256)
            val count = buf.readVarInt().coerceIn(0, 64)
            val aspects = List(count) { buf.readUtf(128) }
            return S2CCinematicStartPacket(session, scene, origin, facing, spawn, start, duration, invoker, species, aspects, buf.readBoolean(), buf.readBoolean())
        }
    }
}

/** The cinematic ended, or this participant left it: the client gives its camera back. */
class S2CCinematicEndPacket(val session: UUID, val reason: CinematicEndReason) : NetworkPacket<S2CCinematicEndPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(session)
        buffer.writeEnum(reason)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("cinematic_end")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CCinematicEndPacket(buf.readUUID(), buf.readEnum(CinematicEndReason::class.java))
    }
}

object CinematicPacketHandlers {
    object Start : ClientNetworkPacketHandler<S2CCinematicStartPacket> {
        override fun handle(packet: S2CCinematicStartPacket, client: Minecraft) {
            client.execute { generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClient.start(packet) }
        }
    }

    object End : ClientNetworkPacketHandler<S2CCinematicEndPacket> {
        override fun handle(packet: S2CCinematicEndPacket, client: Minecraft) {
            client.execute { generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClient.end(packet) }
        }
    }
}
