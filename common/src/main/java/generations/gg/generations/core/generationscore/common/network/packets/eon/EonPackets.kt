package generations.gg.generations.core.generationscore.common.network.packets.eon

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.eon.EonManifest
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * An Eon manifestation (EON-PLAN.md § 4), from the server to the player who sees it (no client
 * class here: the handler is client/eon/EonPacketHandlers). One packet for each change, nothing
 * each tick:
 *
 * - START: the signal's point x, y, z, the way (yaw), the signal's seconds, 1 for a test, the hue (0 or 1);
 * - POINTS: more points of the way (x, y, z each);
 * - MOTION: from `tick`, distance along the way, speed (0: it hovers), 1 for a dash;
 * - PHASE: the anomaly forms (1) or the pursuit starts (2);
 * - ILLUSION: from `tick`, for data[0] seconds, decoys leaving at the angles data[2..] (degrees from the way) at data[1] blocks a second;
 * - CORE: the core at x, y, z, for that many seconds;
 * - STABILIZE: the energy condenses, data[0] seconds;
 * - END: from `tick`, data[0]: 0 a fragment, 1 failed, 2 stopped.
 */
class S2CEonPacket(val kind: Int, val session: Long, val tick: Long, val data: DoubleArray) : NetworkPacket<S2CEonPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeByte(kind)
        buffer.writeLong(session)
        buffer.writeLong(tick)
        buffer.writeVarInt(data.size)
        for (v in data) buffer.writeDouble(v)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("eon_manifestation")
        const val START = 0
        const val POINTS = 1
        const val MOTION = 2
        const val PHASE = 3
        const val ILLUSION = 4
        const val CORE = 5
        const val STABILIZE = 6
        const val END = 7

        fun decode(buf: RegistryFriendlyByteBuf): S2CEonPacket {
            val kind = buf.readByte().toInt()
            val session = buf.readLong()
            val tick = buf.readLong()
            val n = buf.readVarInt().coerceIn(0, 512)
            return S2CEonPacket(kind, session, tick, DoubleArray(n) { buf.readDouble() })
        }
    }
}

/** The player clicked the core of their manifestation (checked again by the server: EonManifest.interact). */
class C2SEonInteractPacket(val session: Long) : NetworkPacket<C2SEonInteractPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeLong(session)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("eon_interact")

        fun decode(buf: RegistryFriendlyByteBuf) = C2SEonInteractPacket(buf.readLong())
    }
}

object EonServerHandlers {
    object Interact : ServerNetworkPacketHandler<C2SEonInteractPacket> {
        override fun handle(packet: C2SEonInteractPacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute { EonManifest.interact(player, packet.session) }
        }
    }
}
