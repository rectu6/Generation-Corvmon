package generations.gg.generations.core.generationscore.common.world.ultraspace

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import java.util.UUID

/**
 * How a journey ended, written by the server at the end of every journey (whatever
 * ended it) from its own data only, saved with the world (UltraWarpRecords) for the
 * next phases: the arrival in an Ultra Space world (destination), the encounters and
 * shiny chances (light-years, portal level). The portal fields are only set for a
 * SUCCESS (portalId 0 otherwise); so are the arrival's (phase 4.1): the world it led
 * to, and how the player got there (PENDING until done, then ARRIVED...).
 */
class UltraWarpOutcome(
    /** The journey (one per session). */
    val journey: UUID,
    val player: UUID,
    /** The Pokémon ridden (species id) and whether it was shiny. */
    val pokemon: String,
    val shiny: Boolean,
    /** Difficulty mode id. */
    val mode: String,
    val lightYears: Double,
    val result: UltraWarpSession.EndReason,
    val portalId: Int = 0,
    val destination: String = "",
    val color: Int = 0,
    val rarity: Int = 0,
    /** When it ended (epoch milliseconds). */
    val time: Long = System.currentTimeMillis(),
) {
    /** How the arrival in the destination's world went. */
    enum class Arrival {
        /** Not a journey through a portal. */
        NONE,
        /** Through the portal, the arrival not done yet (loading, or cut by a disconnection). */
        PENDING,
        /** Put down on safe ground in the world. */
        ARRIVED,
        /** No safe ground found: put down on a small rescue platform. */
        PLATFORM,
        /** The world is closed or missing: the player stayed where they were. */
        UNAVAILABLE,
        /** The transfer failed (no safe place, refused, the player died): they stayed. */
        FAILED,
    }

    /** Distance travelled along the tunnel, in blocks. */
    var distance: Double = 0.0
    /** The world the portal led to (dimension id), its arrival point (-1 for none) and the spot. */
    var world: String = ""
    var arrival: Arrival = Arrival.NONE
    var arrivalPoint: Int = -1
    var arrivalX: Int = 0
    var arrivalY: Int = 0
    var arrivalZ: Int = 0

    val success: Boolean get() = result == UltraWarpSession.EndReason.SUCCESS

    fun save(): CompoundTag = CompoundTag().apply {
        putUUID("journey", journey)
        putUUID("player", player)
        putString("pokemon", pokemon)
        putBoolean("shiny", shiny)
        putString("mode", mode)
        putDouble("lightYears", lightYears)
        putString("result", result.name)
        putInt("portal", portalId)
        putString("destination", destination)
        putInt("color", color)
        putInt("rarity", rarity)
        putLong("time", time)
        putDouble("distance", distance)
        putString("world", world)
        putString("arrival", arrival.name)
        putInt("arrivalPoint", arrivalPoint)
        putInt("arrivalX", arrivalX)
        putInt("arrivalY", arrivalY)
        putInt("arrivalZ", arrivalZ)
    }

    fun encode(buf: FriendlyByteBuf) {
        buf.writeUUID(journey)
        buf.writeUUID(player)
        buf.writeUtf(pokemon)
        buf.writeBoolean(shiny)
        buf.writeUtf(mode)
        buf.writeDouble(lightYears)
        buf.writeEnum(result)
        buf.writeVarInt(portalId)
        buf.writeUtf(destination)
        buf.writeInt(color)
        buf.writeVarInt(rarity)
        buf.writeLong(time)
        buf.writeDouble(distance)
        buf.writeUtf(world)
        buf.writeEnum(arrival)
        buf.writeVarInt(arrivalPoint)
        buf.writeInt(arrivalX)
        buf.writeInt(arrivalY)
        buf.writeInt(arrivalZ)
    }

    override fun toString(): String =
        "journey $journey, player $player, $pokemon${if (shiny) " (shiny)" else ""}, mode $mode, ${"%.1f".format(lightYears)} ly, $result" +
            (if (success) ", portal #$portalId to $destination (level $rarity)" else "") +
            (if (arrival != Arrival.NONE) ", arrival $arrival in $world" + (if (arrivalPoint >= 0) " point $arrivalPoint" else "") + " at $arrivalX $arrivalY $arrivalZ" else "")

    companion object {
        fun load(tag: CompoundTag) = UltraWarpOutcome(
            tag.getUUID("journey"), tag.getUUID("player"), tag.getString("pokemon"), tag.getBoolean("shiny"), tag.getString("mode"),
            tag.getDouble("lightYears"),
            runCatching { UltraWarpSession.EndReason.valueOf(tag.getString("result")) }.getOrDefault(UltraWarpSession.EndReason.FINISHED),
            tag.getInt("portal"), tag.getString("destination"), tag.getInt("color"), tag.getInt("rarity"), tag.getLong("time"),
        ).apply {
            // Outcomes saved before phase 4.1 have none of these: the defaults.
            distance = tag.getDouble("distance")
            world = tag.getString("world")
            arrival = runCatching { Arrival.valueOf(tag.getString("arrival")) }.getOrDefault(Arrival.NONE)
            arrivalPoint = if (tag.contains("arrivalPoint")) tag.getInt("arrivalPoint") else -1
            arrivalX = tag.getInt("arrivalX")
            arrivalY = tag.getInt("arrivalY")
            arrivalZ = tag.getInt("arrivalZ")
        }

        fun decode(buf: FriendlyByteBuf) = UltraWarpOutcome(
            buf.readUUID(), buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readUtf(), buf.readDouble(),
            buf.readEnum(UltraWarpSession.EndReason::class.java), buf.readVarInt(), buf.readUtf(), buf.readInt(), buf.readVarInt(), buf.readLong(),
        ).apply {
            distance = buf.readDouble()
            world = buf.readUtf()
            arrival = buf.readEnum(Arrival::class.java)
            arrivalPoint = buf.readVarInt()
            arrivalX = buf.readInt()
            arrivalY = buf.readInt()
            arrivalZ = buf.readInt()
        }
    }
}
