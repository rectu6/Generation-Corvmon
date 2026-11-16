package generations.gg.generations.core.generationscore.common.network.packets.mew

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.mew.MewGame
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/*
 * The Mew packets (MEW-PLAN.md §§ 6 to 8): encoding only, no client class here (the client's
 * handlers are in client/mew/MewPacketHandlers). Sent to the hunter alone.
 */

/**
 * A false appearance (§ 6): its type (0 to 3 = A to D), a seed the client draws everything from,
 * its origin, its way (Minecraft yaw) and a second point (the end of A's path, C's height...),
 * relative to the origin, its length. About 49 bytes; nothing else follows.
 */
class S2CMewManifestPacket(
    val type: Int,
    val seed: Int,
    val origin: Vec3,
    val yaw: Float,
    val dx: Float,
    val dy: Float,
    val dz: Float,
    /** How long it lasts (mew.json, by type). */
    val seconds: Float,
) : NetworkPacket<S2CMewManifestPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeByte(type)
        buffer.writeInt(seed)
        buffer.writeDouble(origin.x)
        buffer.writeDouble(origin.y)
        buffer.writeDouble(origin.z)
        buffer.writeFloat(yaw)
        buffer.writeFloat(dx)
        buffer.writeFloat(dy)
        buffer.writeFloat(dz)
        buffer.writeFloat(seconds)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("mew_manifest")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CMewManifestPacket(
            buf.readByte().toInt(), buf.readInt(), Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
        )
    }
}

/**
 * The challenge (§§ 7 to 9), one packet per change: the session id and its step (a click from an
 * older step is ignored by the server), the round, the places (relative to the origin, as floats),
 * round 2's real copy or round 3's current point (index), a value (errors, or the outcome), the
 * ticks left before the time limit. About 100 bytes for a round.
 */
class S2CMewGamePacket(
    val event: Int,
    val session: Int,
    val step: Int,
    val round: Int,
    val index: Int,
    val value: Int,
    val ticksLeft: Int,
    val origin: Vec3,
    val yaw: Float,
    val points: List<Vec3>,
) : NetworkPacket<S2CMewGamePacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeByte(event)
        buffer.writeInt(session)
        buffer.writeVarInt(step)
        buffer.writeByte(round)
        buffer.writeByte(index)
        buffer.writeVarInt(value)
        buffer.writeVarInt(ticksLeft)
        buffer.writeDouble(origin.x)
        buffer.writeDouble(origin.y)
        buffer.writeDouble(origin.z)
        buffer.writeFloat(yaw)
        buffer.writeByte(points.size)
        for (p in points) {
            buffer.writeFloat((p.x - origin.x).toFloat())
            buffer.writeFloat((p.y - origin.y).toFloat())
            buffer.writeFloat((p.z - origin.z).toFloat())
        }
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("mew_game")

        /** Mew's opening scene (6.5 s), then round 1. */
        const val OPEN = 0

        /** Mew comes back after a failure (3 s), then round 1. */
        const val RETURN = 1

        /** A round starts (or round 2's copies are shuffled): its places. */
        const val ROUND = 2

        /** Round 2: a wrong copy (value: the errors). */
        const val ERROR = 3

        /** Round 3: Mew's next point (index), or the same point moved closer. */
        const val MOVE = 4

        /** Over (value: MewGameSession.Outcome's ordinal). */
        const val END = 5

        /** Out of the area: back within the time given (value: seconds). */
        const val WARN = 6

        fun decode(buf: RegistryFriendlyByteBuf): S2CMewGamePacket {
            val event = buf.readByte().toInt()
            val session = buf.readInt()
            val step = buf.readVarInt()
            val round = buf.readByte().toInt()
            val index = buf.readByte().toInt()
            val value = buf.readVarInt()
            val left = buf.readVarInt()
            val origin = Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
            val yaw = buf.readFloat()
            val count = buf.readByte().toInt().coerceIn(0, 16)
            val points = List(count) { Vec3(origin.x + buf.readFloat(), origin.y + buf.readFloat(), origin.z + buf.readFloat()) }
            return S2CMewGamePacket(event, session, step, round, index, value, left, origin, yaw, points)
        }
    }
}

/** A click on an illusion: the session, the step the client saw, the illusion's index. */
class C2SMewInteractPacket(val session: Int, val step: Int, val target: Int) : NetworkPacket<C2SMewInteractPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(session)
        buffer.writeVarInt(step)
        buffer.writeByte(target)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("mew_interact")

        fun decode(buf: RegistryFriendlyByteBuf) = C2SMewInteractPacket(buf.readInt(), buf.readVarInt(), buf.readByte().toInt())
    }
}

object MewServerHandlers {
    object Interact : ServerNetworkPacketHandler<C2SMewInteractPacket> {
        override fun handle(packet: C2SMewInteractPacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute { MewGame.interact(player, packet.session, packet.step, packet.target) }
        }
    }
}
