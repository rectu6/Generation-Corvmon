package generations.gg.generations.core.generationscore.common.client.cinematic.fx

import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The cinematics' own particles (not Minecraft's): a pool simulated on the CPU, in world
 * positions, drawn as camera-facing squares, one batch per material. The count stays within
 * the pool (the quality and the intensity choose how many are emitted).
 *
 * Each particle: velocity with drag and gravity, a swirl around the scene's vertical axis
 * (radians per second), a size from start to end, a spin, a colour faded in at birth and out
 * toward its end.
 */
class FxParticles(val capacity: Int, private val materials: Array<FxMaterial>) {
    private val x = DoubleArray(capacity)
    private val y = DoubleArray(capacity)
    private val z = DoubleArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val vz = FloatArray(capacity)
    private val age = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val size0 = FloatArray(capacity)
    private val size1 = FloatArray(capacity)
    private val angle = FloatArray(capacity)
    private val spin = FloatArray(capacity)
    private val drag = FloatArray(capacity)
    private val gravity = FloatArray(capacity)
    private val swirl = FloatArray(capacity)
    private val colour = IntArray(capacity)
    private val material = ByteArray(capacity)
    var count = 0
        private set

    /** The swirl's axis (world x, z). */
    var axisX = 0.0
    var axisZ = 0.0

    fun clear() {
        count = 0
    }

    /** One more particle, if the pool has room. material: index into the pool's materials. */
    fun emit(
        material: Int, px: Double, py: Double, pz: Double, svx: Float, svy: Float, svz: Float,
        life: Float, size0: Float, size1: Float, colour: Int,
        drag: Float = 0f, gravity: Float = 0f, swirl: Float = 0f, spin: Float = 0f, angle: Float = 0f,
    ): Boolean {
        if (count >= capacity || life <= 0f) return false
        val i = count++
        x[i] = px; y[i] = py; z[i] = pz
        vx[i] = svx; vy[i] = svy; vz[i] = svz
        age[i] = 0f
        this.life[i] = life
        this.size0[i] = size0
        this.size1[i] = size1
        this.colour[i] = colour
        this.drag[i] = drag
        this.gravity[i] = gravity
        this.swirl[i] = swirl
        this.spin[i] = spin
        this.angle[i] = angle
        this.material[i] = material.toByte()
        return true
    }

    /** dt: the action's seconds since the last frame (slow motion slows them too). */
    fun update(dt: Float) {
        if (dt <= 0f) return
        var i = 0
        while (i < count) {
            age[i] += dt
            if (age[i] >= life[i]) {
                remove(i)
                continue
            }
            val d = exp(-drag[i] * dt)
            vx[i] *= d
            vy[i] = vy[i] * d - gravity[i] * dt
            vz[i] *= d
            if (swirl[i] != 0f) {
                val rx = x[i] - axisX
                val rz = z[i] - axisZ
                val a = swirl[i] * dt
                val c = cos(a)
                val s = sin(a)
                x[i] = axisX + rx * c + rz * s
                z[i] = axisZ - rx * s + rz * c
            }
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            z[i] += vz[i] * dt
            angle[i] += spin[i] * dt
            i++
        }
    }

    private fun remove(i: Int) {
        val last = --count
        if (i == last) return
        x[i] = x[last]; y[i] = y[last]; z[i] = z[last]
        vx[i] = vx[last]; vy[i] = vy[last]; vz[i] = vz[last]
        age[i] = age[last]; life[i] = life[last]
        size0[i] = size0[last]; size1[i] = size1[last]
        angle[i] = angle[last]; spin[i] = spin[last]
        drag[i] = drag[last]; gravity[i] = gravity[last]; swirl[i] = swirl[last]
        colour[i] = colour[last]; material[i] = material[last]
    }

    /** Into the frame's batches, around the camera (camX..: world position; right, up: its axes). */
    fun render(batches: FxBatches, camX: Double, camY: Double, camZ: Double, right: Vector3f, up: Vector3f) {
        val buffers = Array(materials.size) { batches.of(materials[it]) }
        for (i in 0 until count) {
            val t = age[i] / life[i]
            val fade = (t / 0.12f).coerceAtMost(1f) * (1f - ((t - 0.6f) / 0.4f).coerceIn(0f, 1f))
            val c = colour[i]
            val a = ((c ushr 24) and 0xFF) / 255f * fade
            if (a < 0.004f) continue
            val size = size0[i] + (size1[i] - size0[i]) * t
            FxMeshes.billboard(
                buffers[material[i].toInt()], (x[i] - camX).toFloat(), (y[i] - camY).toFloat(), (z[i] - camZ).toFloat(),
                size, angle[i], FxMeshes.withAlpha(c, a), right, up,
            )
        }
    }
}
