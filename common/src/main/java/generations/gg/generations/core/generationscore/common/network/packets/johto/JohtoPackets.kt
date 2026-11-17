package generations.gg.generations.core.generationscore.common.network.packets.johto

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.johto.Johto
import generations.gg.generations.core.generationscore.common.world.johto.JohtoTrials
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/*
 * The Legend of Johto's packets (JOHTO-PLAN.md § 2): encoding only, no client class here (the
 * client's handlers are in client/johto/JohtoPacketHandlers). One packet per event, never per tick.
 */

/** Asks to confirm an awakening: the token (single use), the beast (its ordinal). */
class S2CJohtoPromptPacket(val token: Int, val beast: Int) : NetworkPacket<S2CJohtoPromptPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(token)
        buffer.writeByte(beast)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("johto_prompt")
        fun decode(buf: RegistryFriendlyByteBuf) = S2CJohtoPromptPacket(buf.readInt(), buf.readByte().toInt())
    }
}

/** The answer to a prompt. */
class C2SJohtoConfirmPacket(val token: Int, val accept: Boolean) : NetworkPacket<C2SJohtoConfirmPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(token)
        buffer.writeBoolean(accept)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("johto_confirm")
        fun decode(buf: RegistryFriendlyByteBuf) = C2SJohtoConfirmPacket(buf.readInt(), buf.readBoolean())
    }
}

/**
 * The altar's screen: its token, which memories this player may use (bits 0 to 2: Thunder,
 * Flames, Rain, read from their inventory, D3), and why it cannot be summoned now (0: it can).
 */
class S2CJohtoAltarPacket(val token: Int, val present: Int, val reason: Int) : NetworkPacket<S2CJohtoAltarPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(token)
        buffer.writeByte(present)
        buffer.writeByte(reason)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("johto_altar")
        fun decode(buf: RegistryFriendlyByteBuf) = S2CJohtoAltarPacket(buf.readInt(), buf.readByte().toInt(), buf.readByte().toInt())

        const val OK = 0
        const val MISSING = 1
        const val BUSY = 2
        const val SUMMONING = 3
        const val DISABLED = 4
    }
}

/** "Summon Ho-Oh" confirmed on the altar's screen. */
class C2SJohtoSummonPacket(val token: Int) : NetworkPacket<C2SJohtoSummonPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(token)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("johto_summon")
        fun decode(buf: RegistryFriendlyByteBuf) = C2SJohtoSummonPacket(buf.readInt())
    }
}

/**
 * A trial's event (§§ 6-7), to its player alone: the session and its step (an action from an
 * older step is ignored), the beast, the round, a kind and index, a value, two ticks (the
 * level's game time), the places (relative to origin, as floats) and a few numbers.
 */
class S2CJohtoTrialPacket(
    val event: Int,
    val session: Int,
    val step: Int,
    val beast: Int,
    val round: Int,
    val kind: Int,
    val index: Int,
    val value: Int,
    val tick: Long,
    val tick2: Long,
    val origin: Vec3,
    val points: List<Vec3>,
    val ints: IntArray,
) : NetworkPacket<S2CJohtoTrialPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeByte(event)
        buffer.writeInt(session)
        buffer.writeVarInt(step)
        buffer.writeByte(beast)
        buffer.writeByte(round)
        buffer.writeByte(kind)
        buffer.writeVarInt(index)
        buffer.writeVarInt(value)
        buffer.writeLong(tick)
        buffer.writeLong(tick2)
        buffer.writeDouble(origin.x)
        buffer.writeDouble(origin.y)
        buffer.writeDouble(origin.z)
        buffer.writeByte(points.size)
        for (p in points) {
            buffer.writeFloat((p.x - origin.x).toFloat())
            buffer.writeFloat((p.y - origin.y).toFloat())
            buffer.writeFloat((p.z - origin.z).toFloat())
        }
        buffer.writeByte(ints.size)
        for (i in ints) buffer.writeVarInt(i)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("johto_trial")

        /** The trial begins: origin = the zone's (or arena's) centre, value = the endurance (Entei), ints[0] = the area's radius × 10. */
        const val START = 0
        /** A round begins: its places (tracks, a seal, a source), tick = its deadline. */
        const val ROUND = 1
        /** Progress: index (the track reached, the seal ‰, the note reached). */
        const val PROGRESS = 2
        /** Something announced: a flash of Raikou, an attack of Entei, Suicune's sequence or harmony. */
        const val CUE = 3
        /** Entei's attack touched: value = the endurance left. */
        const val HIT = 4
        /** An outcome inside a round: kind (see the RESULT_* values), value a count. */
        const val RESULT = 5
        /** Out of the zone: value = the seconds left to come back. */
        const val WARN = 6
        /** Over: value 0 won, 1 failed, 2 stopped. */
        const val END = 7

        const val RESULT_OK = 0
        const val RESULT_MISS = 1
        const val RESULT_ERROR = 2
        const val RESULT_FOUND = 3
        const val RESULT_PURIFIED = 4
        const val RESULT_HARMONY = 5
        const val RESULT_DISCORD = 6

        fun decode(buf: RegistryFriendlyByteBuf): S2CJohtoTrialPacket {
            val event = buf.readByte().toInt()
            val session = buf.readInt()
            val step = buf.readVarInt()
            val beast = buf.readByte().toInt()
            val round = buf.readByte().toInt()
            val kind = buf.readByte().toInt()
            val index = buf.readVarInt()
            val value = buf.readVarInt()
            val tick = buf.readLong()
            val tick2 = buf.readLong()
            val origin = Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
            val count = buf.readByte().toInt().coerceIn(0, 32)
            val points = List(count) { Vec3(origin.x + buf.readFloat(), origin.y + buf.readFloat(), origin.z + buf.readFloat()) }
            val n = buf.readByte().toInt().coerceIn(0, 32)
            val ints = IntArray(n) { buf.readVarInt() }
            return S2CJohtoTrialPacket(event, session, step, beast, round, kind, index, value, tick, tick2, origin, points, ints)
        }
    }
}

/** A click in a trial (one of Suicune's orbs, or its harmony): the session, the step seen, the target. */
class C2SJohtoTrialActionPacket(val session: Int, val step: Int, val target: Int) : NetworkPacket<C2SJohtoTrialActionPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(session)
        buffer.writeVarInt(step)
        buffer.writeByte(target)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("johto_trial_action")
        fun decode(buf: RegistryFriendlyByteBuf) = C2SJohtoTrialActionPacket(buf.readInt(), buf.readVarInt(), buf.readByte().toInt())

        /** Suicune's harmony: a click anywhere. */
        const val HARMONY = 9
    }
}

object JohtoServerHandlers {
    object Confirm : ServerNetworkPacketHandler<C2SJohtoConfirmPacket> {
        override fun handle(packet: C2SJohtoConfirmPacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute { Johto.confirm(player, packet.token, packet.accept) }
        }
    }

    object Summon : ServerNetworkPacketHandler<C2SJohtoSummonPacket> {
        override fun handle(packet: C2SJohtoSummonPacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute { Johto.summon(player, packet.token) }
        }
    }

    object Action : ServerNetworkPacketHandler<C2SJohtoTrialActionPacket> {
        override fun handle(packet: C2SJohtoTrialActionPacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute { JohtoTrials.action(player, packet.session, packet.step, packet.target) }
        }
    }
}
