package generations.gg.generations.core.generationscore.common.world.ultraspace.rift

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sign

/**
 * A rift between two Ultra worlds (phase 6): only data, kept by the server
 * (UltraRifts), never an entity nor a block. It stands in a vertical plane turned by
 * [yaw]; its opening is the torn outline of [UltraRiftShape], [width] wide and
 * [height] high. Its destination is fixed when it opens.
 */
class UltraRift(
    val id: Int,
    /** Its world (ultra_jungle...). */
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val width: Float,
    val height: Float,
    val seed: Long,
    /** The world it leads to (ultra_desert...). */
    val destination: String,
    /** Game time (the Overworld's) it opened at and closes at. */
    val opened: Long,
    val expires: Long,
) {
    /** Open: can be crossed. Taken: a player is going through it (never open again). */
    var taken = false

    val centre: Vec3 get() = Vec3(x, y, z)

    /** The plane's axes: along the opening (horizontal), and its normal. */
    val rightX: Double get() = cos(Math.toRadians(yaw.toDouble()))
    val rightZ: Double get() = sin(Math.toRadians(yaw.toDouble()))
    val normalX: Double get() = -rightZ
    val normalZ: Double get() = rightX

    /** The opening's outline, in the plane (u along it, v up), for the crossing test. */
    val outline: FloatArray by lazy { UltraRiftShape.outline(seed, width / 2f, height / 2f) }

    fun save(): CompoundTag = CompoundTag().apply {
        putInt("id", this@UltraRift.id)
        putString("world", world)
        putDouble("x", x)
        putDouble("y", y)
        putDouble("z", z)
        putFloat("yaw", yaw)
        putFloat("width", width)
        putFloat("height", height)
        putLong("seed", seed)
        putString("destination", destination)
        putLong("opened", opened)
        putLong("expires", expires)
    }

    override fun toString(): String = "#$id $world %.1f %.1f %.1f -> $destination".format(x, y, z)

    companion object {
        fun load(tag: CompoundTag) = UltraRift(
            tag.getInt("id"), tag.getString("world"), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("yaw"),
            tag.getFloat("width"), tag.getFloat("height"), tag.getLong("seed"), tag.getString("destination"), tag.getLong("opened"), tag.getLong("expires"),
        )
    }
}

/**
 * What a client needs to draw a rift (never its destination's id: only its colour).
 * [age] and [remaining] are in ticks when sent.
 */
class UltraRiftView(
    val id: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val width: Float,
    val height: Float,
    val seed: Long,
    val color: Int,
    val age: Int,
    val remaining: Int,
) {
    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(id)
        buf.writeDouble(x)
        buf.writeDouble(y)
        buf.writeDouble(z)
        buf.writeFloat(yaw)
        buf.writeFloat(width)
        buf.writeFloat(height)
        buf.writeLong(seed)
        buf.writeInt(color)
        buf.writeVarInt(age)
        buf.writeVarInt(remaining)
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf) = UltraRiftView(
            buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readLong(), buf.readInt(), buf.readVarInt(), buf.readVarInt(),
        )
    }
}

/**
 * The torn outline of a rift, the same on the server (the crossing) and on the client
 * (the drawing): a long lens with pointed ends, its edge broken by the seed.
 */
object UltraRiftShape {
    const val POINTS = 24

    /** u, v pairs (POINTS of them), counter-clockwise, for half sizes w and h. */
    fun outline(seed: Long, w: Float, h: Float): FloatArray {
        val random = java.util.Random(seed)
        val out = FloatArray(POINTS * 2)
        for (k in 0 until POINTS) {
            val angle = 2.0 * PI * k / POINTS
            val c = cos(angle)
            val s = sin(angle)
            // Round on the sides, pointed at the top and the bottom.
            val u = sign(c) * abs(c).pow(1.5)
            val v = sign(s) * abs(s).pow(0.85)
            var r = 1.0 + (random.nextDouble() * 2 - 1) * 0.16
            // A jagged edge: every other point drawn in a little.
            if (k % 2 == 1) r -= 0.07
            // Longer, sharper tips.
            if (k == POINTS / 4 || k == 3 * POINTS / 4) r += 0.14
            out[2 * k] = (u * r * w).toFloat()
            out[2 * k + 1] = (v * r * h).toFloat()
        }
        return out
    }

    /** Whether (u, v) is inside the outline scaled by fraction (an even-odd test). */
    fun inside(outline: FloatArray, u: Double, v: Double, fraction: Double): Boolean {
        var inside = false
        val n = outline.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val ui = outline[2 * i] * fraction
            val vi = outline[2 * i + 1] * fraction
            val uj = outline[2 * j] * fraction
            val vj = outline[2 * j + 1] * fraction
            if ((vi > v) != (vj > v) && u < (uj - ui) * (v - vi) / (vj - vi) + ui) inside = !inside
            j = i
        }
        return inside
    }
}
