package generations.gg.generations.core.generationscore.common.network.packets.hooh

import com.cobblemon.mod.common.api.net.NetworkPacket
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * The Passage of the Rainbow (HOOH-PLAN.md § 7), from the server to the player who sees it (no
 * client class here: the handler is client/hooh/HoOhPacketHandlers). One packet for each change,
 * nothing each tick:
 *
 * - START: origin x, y, z, the passage's way (yaw), Ho-Oh's scale, 1 if a scene takes the camera;
 * - POINTS: more points of the flight (x, y, z each);
 * - MOTION: from `tick`, distance along the flight and speed (0: Ho-Oh circles and waits);
 * - FEATHER: the feather falls from x, y, z to x, y, z in that many seconds, from `tick`;
 * - LEAVE: Ho-Oh flies up and vanishes from `tick`;
 * - PICKED: the feather was taken;
 * - END: the passage ends (data[0]: 0 success, 1 failure, 2 stopped).
 */
class S2CHoOhPacket(val kind: Int, val session: Long, val tick: Long, val data: DoubleArray) : NetworkPacket<S2CHoOhPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeByte(kind)
        buffer.writeLong(session)
        buffer.writeLong(tick)
        buffer.writeVarInt(data.size)
        for (v in data) buffer.writeDouble(v)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("ho_oh_passage")
        const val START = 0
        const val POINTS = 1
        const val MOTION = 2
        const val FEATHER = 3
        const val LEAVE = 4
        const val PICKED = 5
        const val END = 6

        fun decode(buf: RegistryFriendlyByteBuf): S2CHoOhPacket {
            val kind = buf.readByte().toInt()
            val session = buf.readLong()
            val tick = buf.readLong()
            val n = buf.readVarInt().coerceIn(0, 512)
            return S2CHoOhPacket(kind, session, tick, DoubleArray(n) { buf.readDouble() })
        }
    }
}
