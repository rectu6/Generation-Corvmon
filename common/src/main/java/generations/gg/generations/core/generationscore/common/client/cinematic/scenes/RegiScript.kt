package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CamPose
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraDirector
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.decay
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutSine
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeOutCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.lerp
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.phase
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.pulse
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.random
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smooth
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smoother
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.window
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.Shot
import org.joml.Vector3d
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The Regis' elements (REGIS-PLAN.md § 5), by scene. */
enum class RegiElement {
    ROCK, ICE, STEEL, ELECTRIC, DRAGON, GIANT;

    companion object {
        fun of(scene: String): RegiElement = when (scene) {
            "regirock" -> ROCK
            "regice" -> ICE
            "registeel" -> STEEL
            "regieleki" -> ELECTRIC
            "regidrago" -> DRAGON
            else -> GIANT
        }
    }
}

/** A piece the scene moves (an Unown letter, a fragment): its centre in the frame, angles, size (0: not drawn). */
class PieceKey {
    val pos = Vector3d()
    var yaw = 0.0
    var pitch = 0.0
    var roll = 0.0
    var scale = 0.0
}

/**
 * « Le Réveil des Titans » (REGIS-PLAN.md § 5), pure (tested off-game by tools/RegiBench): the
 * same four sequences for the six, written for 21 s (Regigigas 27 s, its times stretched by
 * 27/21). 1. The shrine wakes (0–5): the Unown letters rise and fly into the shrine, runes, the
 * shrine trembles. 2. The core (5–10): a light over the shrine, the fragments of the element
 * rise and turn round it, the titan's translucent shape. 3. The forming (10–15): the fragments
 * close on the body, the shape turns into stone, ice, metal..., then the real model at the flash
 * (revealAt). 4. The awakening (15–21): its lights, its cry, a wave on the ground, calm; the real
 * Pokémon takes the actor's place (2 ticks before the end).
 *
 * The frame: the shrine's ground at the origin, the invoker toward +z. `landing`: where the real
 * Pokémon appears (the centre of the block above the shrine), where the titan stands; it faces
 * +z. height, width: the Pokémon's (its hitbox). ceiling: the free height above the shrine (14:
 * open sky); room: the free distance toward the invoker; the cameras stay within both.
 * letters: the centres of the letters destroyed. test: the titan vanishes at the end.
 */
class RegiScript(
    val element: RegiElement,
    landing: Vector3d,
    val test: Boolean,
    height: Double,
    width: Double,
    ceiling: Double,
    room: Double,
    letters: List<Vector3d>,
) : SceneScript {
    val giant = element == RegiElement.GIANT

    /** The scene's length and its moments. */
    val end = if (giant) 27.0 else 21.0
    private val k = end / 21.0
    val liftAt = 0.3
    val gatherAt = 3.0 * k
    val coreAt = 4.6 * k
    val shapeAt = 5.6 * k
    val formAt = 10.4 * k
    val matterAt = 11.6 * k
    val revealAt = 14.6 * k
    val lightsAt = 15.6 * k
    val cryAt = 17.0 * k
    val calmAt = 18.4 * k

    val landing = Vector3d(landing)
    val h = height.coerceIn(0.8, 7.0)
    val w = width.coerceIn(0.6, 6.0)
    val ceiling = ceiling.coerceIn(2.0, 14.0)
    val open = ceiling >= 13.5
    val room = room.coerceIn(2.0, 12.0)
    val letters: List<Vector3d> = letters.map { Vector3d(it) }

    /** The titan's centre, where the core forms. */
    val centre = Vector3d(this.landing.x, this.landing.y + h * 0.5, this.landing.z)

    /** Where its lights are (its face, toward +z), roughly. */
    val face = Vector3d(
        this.landing.x,
        this.landing.y + h * when (element) {
            RegiElement.ELECTRIC -> 0.52
            RegiElement.DRAGON -> 0.6
            RegiElement.GIANT -> 0.84
            else -> 0.7
        },
        this.landing.z + w * 0.46,
    )

    override fun action(t: Double): Double = t

    // ---------------------------------------------------------------- the titan

    fun hover(a: Double): Double = 0.16 * sin(a * 1.4) * window(a, shapeAt, shapeAt + 1.5, calmAt - 1.0, calmAt)

    /** Regigigas rises out of the light (from half its height under its place). */
    fun rise(a: Double): Double = if (giant) -0.5 * h * (1.0 - easeInOutSine(phase(a, shapeAt, revealAt))) else 0.0

    fun titanPos(a: Double, out: Vector3d): Vector3d = out.set(landing.x, landing.y + hover(a) + rise(a), landing.z)

    /** Regieleki turns on itself (four turns, slowing down, ending facing +z); Regidrago sways. */
    fun titanYaw(a: Double): Double = when (element) {
        RegiElement.ELECTRIC -> 1440.0 * easeOutCubic(phase(a, formAt, revealAt + 0.6))
        RegiElement.DRAGON -> 6.0 * sin(a * 0.8) * window(a, shapeAt, shapeAt + 1.0, calmAt - 0.8, calmAt)
        else -> 0.0
    }

    /** Regirock leans forward after the reveal; Regigigas straightens up as it rises. */
    fun titanPitch(a: Double): Double = when (element) {
        RegiElement.ROCK -> 9.0 * window(a, revealAt + 0.6, revealAt + 1.6, cryAt + 0.5, calmAt)
        RegiElement.GIANT -> 16.0 * (1.0 - smooth(phase(a, formAt, revealAt + 0.8)))
        else -> 0.0
    }

    override fun actor(a: Double, out: ActorKey): ActorKey {
        out.scale = 1.0
        out.roll = 0.0
        out.pose = when {
            a < shapeAt -> ActorPose.HIDDEN
            test && a >= end - 0.15 -> ActorPose.HIDDEN
            else -> ActorPose.STAND
        }
        titanPos(a, out.pos)
        out.yaw = titanYaw(a)
        out.pitch = titanPitch(a)
        return out
    }

    // ---------------------------------------------------------------- the envelopes

    /** The shrine trembling. */
    fun rumble(a: Double): Double = window(a, 0.2, 1.5, calmAt - 0.4, calmAt + 0.8)

    /** The runes on the ground. */
    fun runes(a: Double): Double = window(a, 0.8, 3.2, calmAt, end - 0.6)

    /** The element on the ground and in the air (cracks, frost, lines, arcs, aura, pillars). */
    fun ground(a: Double): Double = window(a, 0.6, 4.5, calmAt, end - 0.4)

    /** The core of light over the shrine. */
    fun core(a: Double): Double = window(a, coreAt - 0.6, coreAt + 0.8, revealAt - 0.1, revealAt + 0.5)

    /** The core's size, from 0 to 1. */
    fun coreSize(a: Double): Double = smooth(phase(a, coreAt - 0.6, shapeAt + 1.0))

    /** The translucent shape of the titan. */
    fun ghost(a: Double): Double = smooth(phase(a, shapeAt, shapeAt + 2.6))

    /** The fragments closing on the body, from 0 to 1. */
    fun converge(a: Double): Double = easeInOutSine(phase(a, formAt, revealAt - 0.3))

    /** Regice's crystal: grown, from 0 to 1 (gone at the reveal). */
    fun shell(a: Double): Double = if (a >= revealAt) 0.0 else smooth(phase(a, coreAt - 0.2, shapeAt + 2.0))

    /** Its cracks. */
    fun cracks(a: Double): Double = if (a >= revealAt) 0.0 else smooth(phase(a, formAt + 0.8, revealAt - 0.2))

    /** The lights on its face (each one after the other, then fading into the model's own). */
    fun lights(a: Double): Double = smooth(phase(a, lightsAt, lightsAt + 0.3)) * (1.0 - smooth(phase(a, calmAt + 0.6, end - 0.6)))

    /** Light i of n, from 0 to 1. */
    fun light(i: Int, n: Int, a: Double): Double {
        val at = lightsAt + i * 1.1 / max(1, n)
        return smooth(phase(a, at, at + 0.2)) * (1.0 - smooth(phase(a, calmAt + 0.6, end - 0.6)))
    }

    /** The wave crossing the ground after the cry, from 0 (at the shrine) to 1. */
    fun shock(a: Double): Double = phase(a, cryAt + 0.1, cryAt + 1.9)

    /** Minecraft's other sounds fading. */
    fun duck(a: Double): Double = window(a, 0.0, 2.0, calmAt + 0.5, end - 0.4)

    /** Test only: the titan dissolving at the end (nothing appears). */
    fun vanish(a: Double): Double = if (test) smooth(phase(a, end - 0.85, end - 0.2)) else 0.0

    /** Regieleki's arcs striking the ground: times and where (angle, radius). */
    val strikes: List<DoubleArray> = if (element != RegiElement.ELECTRIC) emptyList() else
        (0 until 16).map { i ->
            val at = 1.0 + i * (cryAt + 0.6 - 1.0) / 16.0 + 0.25 * random(i, 41)
            doubleArrayOf(at, random(i, 42) * 2.0 * PI, 1.6 + 3.2 * random(i, 43))
        }

    /** The flicker of the light (Regieleki's surges). */
    fun flicker(a: Double): Double {
        var x = 0.0
        for (s in strikes) x += pulse(a, s[0], 0.02, 0.12)
        return x.coerceIn(0.0, 1.0)
    }

    override fun envelopes(a: Double): DoubleArray = doubleArrayOf(
        rumble(a), runes(a), ground(a), core(a), coreSize(a), ghost(a), converge(a), shell(a), cracks(a), lights(a), shock(a), duck(a), vanish(a), flicker(a),
    )

    // ---------------------------------------------------------------- the pieces

    private val roofY = if (open) 40.0 else this.ceiling - 0.6

    /** The letters' tops (where they rise to) and the controls of their flight to the centre. */
    private val letterTop = this.letters.map { p -> Vector3d(p.x, (p.y + 1.6).coerceAtMost(max(p.y, roofY - 0.2)), p.z) }
    private val letterC1 = letterTop.map { p -> Vector3d(p.x, min(p.y + 1.4, max(p.y, roofY)), p.z) }
    private val letterC2 = Vector3d(centre.x, min(centre.y + 1.8, max(centre.y, roofY)), centre.z)

    fun letterArrive(i: Int): Double = gatherAt + 0.1 * i + 1.4

    /** Letter i at action time a (it rises, then flies into the centre and is gone). */
    fun letter(i: Int, a: Double, out: PieceKey): PieceKey {
        val p0 = letters[i]
        val top = letterTop[i]
        val start = liftAt + 0.12 * i
        val go = gatherAt + 0.1 * i
        val arrive = letterArrive(i)
        out.yaw = 40.0 * a + 30.0 * i
        out.pitch = 0.0
        out.roll = 8.0 * sin(a * 2.0 + i) * smooth(phase(a, start, start + 0.6))
        when {
            a < go -> {
                val u = easeOutCubic(phase(a, start, start + 1.6))
                val bob = 0.08 * sin(a * 2.6 + i) * window(a, start + 1.0, start + 1.6, go - 0.4, go)
                out.pos.set(lerp(p0.x, top.x, u), lerp(p0.y, top.y, u) + bob, lerp(p0.z, top.z, u))
                out.scale = lerp(1.0, 0.8, u)
            }
            a < arrive -> {
                val u = easeInOutSine(phase(a, go, arrive))
                bezier(top, letterC1[i], letterC2, centre, u, out.pos)
                out.scale = lerp(0.8, 0.15, u)
            }
            else -> out.scale = 0.0
        }
        return out
    }

    /** How many fragments of the element turn round the core. */
    val fragments = when (element) {
        RegiElement.ROCK -> 22
        RegiElement.ICE -> 14
        RegiElement.STEEL -> 16
        RegiElement.DRAGON -> 10
        RegiElement.GIANT -> 20
        RegiElement.ELECTRIC -> 0
    }

    private val fTheta = DoubleArray(fragments) { it * 2.39996323 + random(it, 51) * 0.4 }
    private val fGround = DoubleArray(fragments) { (if (giant) 2.6 else 1.5) + (if (giant) 3.8 else 2.4) * random(it, 52) }
    private val fRadius = DoubleArray(fragments) { w * 0.55 + (if (giant) 1.8 else 0.9) + (if (giant) 1.6 else 1.1) * random(it, 53) }
    private val fHeight = DoubleArray(fragments) { (random(it, 54) - 0.5) * h * 0.95 }
    private val fSpeed = DoubleArray(fragments) { (0.45 + 0.5 * random(it, 55)) * (if (it % 3 == 0) -1.0 else 1.0) * (if (element == RegiElement.STEEL) 1.3 else 1.0) }
    private val fSize = DoubleArray(fragments) {
        val base = when (element) {
            RegiElement.ROCK -> 0.36
            RegiElement.ICE -> 0.3
            RegiElement.STEEL -> 0.3
            RegiElement.DRAGON -> 0.26
            else -> 0.42
        }
        base * (0.75 + 0.6 * random(it, 56))
    }

    fun fragmentRise(j: Int): Double = coreAt - 0.9 + 0.07 * j

    /** The fragment's turn round the core: slower once they close in (fastened to the body). */
    private fun turned(a: Double): Double = min(a, formAt) + max(0.0, a - formAt) * 0.35

    /** Fragment j at action time a: on the ground, rising, turning round the core, closing on the body; gone at the reveal. */
    fun fragment(j: Int, a: Double, out: PieceKey): PieceKey {
        val size = fSize[j]
        val appear = 0.8 + 0.05 * j
        val rise0 = fragmentRise(j)
        val rise1 = rise0 + 1.4
        val c = converge(a)
        out.yaw = (a * 37.0 * (1.0 + random(j, 57))) * (1.0 - 0.6 * c) + 90.0 * random(j, 58)
        out.pitch = (a * 23.0 * random(j, 59)) * (1.0 - 0.6 * c) + 45.0 * random(j, 60)
        out.roll = 30.0 * random(j, 61)
        if (a >= revealAt) {
            out.scale = 0.0
            return out
        }
        val gx = fGround[j] * sin(fTheta[j])
        val gz = fGround[j] * cos(fTheta[j])
        val gy = size * 0.5
        val angle = fTheta[j] + fSpeed[j] * turned(a)
        val r = lerp(fRadius[j], w * 0.42 + size * 0.3, c)
        val ox = centre.x + r * sin(angle)
        val oy = min(centre.y + fHeight[j] * (1.0 - 0.15 * c) + 0.12 * sin(a * 1.7 + j), roofY - size)
        val oz = centre.z + r * cos(angle)
        when {
            a < rise0 -> out.pos.set(gx, gy, gz)
            a < rise1 -> {
                val u = easeInOutSine(phase(a, rise0, rise1))
                out.pos.set(lerp(gx, ox, u), lerp(gy, oy, u) + 0.8 * sin(PI * u), lerp(gz, oz, u))
            }
            else -> out.pos.set(ox, oy, oz)
        }
        out.scale = size * smooth(phase(a, appear, appear + 0.8)) * (1.0 - 0.45 * c)
        return out
    }

    private fun bezier(a: Vector3d, b: Vector3d, c: Vector3d, e: Vector3d, u: Double, out: Vector3d): Vector3d {
        val v = 1.0 - u
        val k0 = v * v * v
        val k1 = 3.0 * v * v * u
        val k2 = 3.0 * v * u * u
        val k3 = u * u * u
        return out.set(a.x * k0 + b.x * k1 + c.x * k2 + e.x * k3, a.y * k0 + b.y * k1 + c.y * k2 + e.y * k3, a.z * k0 + b.z * k1 + c.z * k2 + e.z * k3)
    }

    // ---------------------------------------------------------------- the camera

    /** How far the cameras stand from the titan (its size), within the room. */
    val distance = (1.15 * h + 2.8).coerceIn(4.0, 13.0)
    private val roomRadius = (this.room - 0.35).coerceAtLeast(2.4)
    private val camTop = if (open) 40.0 else max(0.65, this.ceiling - 0.55)

    private fun fit(r: Double): Double = min(r, roomRadius)
    private fun cy(y: Double): Double = y.coerceIn(0.65, camTop)

    private fun shot(name: String, start: Double, blend: Double, f: (Double, CamPose) -> Unit) = Shot(name, start, blend) { t, _, out -> f(t, out) }

    /** A point round the titan: angle in degrees from +z toward +x. */
    private fun around(angle: Double, radius: Double, y: Double, out: Vector3d): Vector3d {
        val r = Math.toRadians(angle)
        return out.set(centre.x + radius * sin(r), cy(y), centre.z + radius * cos(r))
    }

    /** The first shot: the letters and the shrine both in view (from the side away from the letters, or beyond them). */
    private val firstFrom = Vector3d()
    private val firstTo = Vector3d()
    private val firstLook = Vector3d()

    init {
        var ux = 0.0
        var uy = 0.0
        var uz = 0.0
        for (p in this.letters) {
            ux += p.x
            uy += p.y
            uz += p.z
        }
        val n = this.letters.size
        val len = if (n > 0) sqrt(ux * ux + uz * uz) / n else 0.0
        if (n == 0 || len < 1.0) {
            firstFrom.set(0.6, cy(1.6 + 0.2 * h), fit(distance * 1.2))
            firstTo.set(0.4, cy(1.3 + 0.2 * h), fit(distance))
            firstLook.set(centre.x, centre.y - 0.3 * h, centre.z)
        } else {
            ux /= n
            uy /= n
            uz /= n
            val dx = ux / len
            val dz = uz / len
            if (dz <= 0.2) {
                firstFrom.set(-dx * fit(distance * 1.25), cy(1.8 + 0.25 * h), -dz * fit(distance * 1.25))
                firstTo.set(-dx * fit(distance * 1.05), cy(1.5 + 0.2 * h), -dz * fit(distance * 1.05))
            } else {
                firstFrom.set(ux + dx * 2.6, cy(uy + 1.6), uz + dz * 2.6)
                firstTo.set(ux + dx * 1.9, cy(uy + 1.4), uz + dz * 1.9)
            }
            firstLook.set((ux + centre.x) * 0.5, (uy + 1.0 + centre.y) * 0.5, (uz + centre.z) * 0.5)
        }
    }

    private val sanctuary = shot("sanctuary", 0.0, 0.0) { t, out ->
        val u = easeInOutSine(phase(t, 0.0, coreAt + 0.6))
        out.pos.set(lerp(firstFrom.x, firstTo.x, u), lerp(firstFrom.y, firstTo.y, u), lerp(firstFrom.z, firstTo.z, u))
        val l = smooth(phase(t, gatherAt - 0.4, gatherAt + 1.9))
        out.target.set(lerp(firstLook.x, centre.x, l), lerp(firstLook.y, centre.y, l), lerp(firstLook.z, centre.z, l))
        out.fov = lerp(62.0, 56.0, u)
    }
    /**
     * The orbits' angles, each starting where the last shot ends (a blend between two far
     * angles cuts across the arc, toward the titan, in a small room): from the first shot's
     * side, 45° on, 15° back low, then 95° across, within ±80° of the invoker's side.
     */
    private val angle0 = Math.toDegrees(atan2(firstTo.x, firstTo.z)).coerceIn(-75.0, 75.0)
    private val angle1 = angle0 + if (angle0 <= 20.0) 45.0 else -45.0
    private val angle2 = angle1 + if (angle1 <= 0.0) 15.0 else -15.0
    private val angle3 = (angle2 + if (angle2 <= 0.0) 95.0 else -95.0).coerceIn(-80.0, 80.0)

    private val coreShot = shot("core", coreAt - 0.2, 1.6) { t, out ->
        val u = easeInOutSine(phase(t, coreAt - 0.2, 8.6 * k))
        around(lerp(angle0, angle1, u), fit(distance), landing.y + lerp(0.2, 0.9, u) * h, out.pos)
        out.target.set(centre.x, centre.y - lerp(0.15, 0.0, u) * h, centre.z)
        out.fov = lerp(56.0, 50.0, u)
    }
    private val silhouette = shot("silhouette", 8.2 * k, 1.4) { t, out ->
        val u = easeInOutSine(phase(t, 8.2 * k, formAt + 0.6))
        around(lerp(angle1, angle2, u), fit(distance * 0.8), landing.y + lerp(-0.5 + 0.05 * h, -0.2, u), out.pos)
        out.target.set(centre.x, centre.y + 0.15 * h, centre.z)
        out.fov = lerp(62.0, 58.0, u)
    }
    private val forming = shot("forming", formAt, 1.2) { t, out ->
        val u = easeInOutSine(phase(t, formAt, revealAt))
        around(lerp(angle2, angle3, u), fit(lerp(distance * 0.95, distance * 0.85, u)), landing.y + 0.55 * h + 0.3 * sin(PI * u), out.pos)
        out.target.set(centre)
        out.fov = 52.0
    }
    // A cut on the flash: the titan from low in front.
    private val reveal = shot("reveal", revealAt, 0.0) { t, out ->
        val u = easeInOutSine(phase(t, revealAt, lightsAt + 0.6))
        val r = fit(distance * 0.85)
        out.pos.set(centre.x + 0.3 * r, cy(landing.y + lerp(-0.35 + 0.05 * h, 0.1, u)), centre.z + r)
        out.target.set(centre.x, centre.y + 0.12 * h, centre.z)
        out.fov = lerp(64.0, 58.0, u)
    }
    private val awaken = shot("awaken", lightsAt + 0.5, 1.0) { t, out ->
        val u = easeInOutSine(phase(t, lightsAt + 0.5, calmAt + 0.6))
        val r = fit(lerp(distance * 0.95, distance * (if (giant) 1.6 else 1.35), u))
        out.pos.set(centre.x - 0.15 * r, cy(landing.y + lerp(0.6, if (giant) 0.8 else 0.65, u) * h), centre.z + r)
        out.target.set(centre.x, centre.y + 0.1 * h, centre.z)
        out.fov = lerp(50.0, 56.0, u)
    }
    private val back = shot("back", calmAt + 0.4, 1.6) { t, out ->
        val u = easeInOutSine(phase(t, calmAt + 0.4, end))
        val r = fit(distance * 1.2)
        out.pos.set(centre.x + 0.1 * r, cy(lerp(landing.y + 0.6 * h, 1.7, u)), centre.z + r)
        out.target.set(centre)
        out.fov = 60.0
    }

    override val director = CameraDirector(listOf(sanctuary, coreShot, silhouette, forming, reveal, awaken, back))

    override fun cameraWeight(t: Double): Double = smoother(t / 1.2) * (1.0 - smoother((t - (end - 1.8)) / 1.8))
    override fun letterbox(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - (end - 1.6)) / 1.5))
    override fun title(t: Double): Double = window(t, revealAt + 1.2, revealAt + 1.8, calmAt + 0.6, calmAt + 1.4)
    override fun presence(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - (end - 1.6)) / 1.6))

    override fun shake(a: Double): Double {
        val g = if (giant) 1.4 else 1.0
        return (0.1 * rumble(a) * (0.6 + 0.4 * sin(a * 9.0).let { it * it }) + 0.08 * window(a, formAt, formAt + 1.0, revealAt - 0.2, revealAt) +
            0.3 * decay(a, revealAt, 0.5) + 0.45 * decay(a, cryAt + 0.1, 0.6) + 0.1 * flicker(a)) * g
    }

    override fun flash(a: Double): Double {
        var x = 0.2 * pulse(a, matterAt, 0.05, 0.3) + 0.55 * pulse(a, revealAt, 0.04, 0.45) + 0.25 * pulse(a, cryAt + 0.1, 0.05, 0.4)
        if (element == RegiElement.ELECTRIC) x += 0.3 * pulse(a, revealAt, 0.04, 0.6) + 0.12 * flicker(a)
        return x.coerceIn(0.0, 1.0)
    }

    override fun grade(t: Double, out: Grade): Grade {
        val a = action(t)
        val g = window(a, 0.5, 4.0, calmAt, end - 0.3)
        val c = core(a)
        val f = flicker(a)
        when (element) {
            RegiElement.ROCK -> { out.r = 1.0 + 0.06 * g; out.g = 1.0 + 0.0 * g; out.b = 1.0 - 0.1 * g }
            RegiElement.ICE -> { out.r = 1.0 - 0.1 * g; out.g = 1.0 - 0.02 * g; out.b = 1.0 + 0.1 * g }
            RegiElement.STEEL -> { out.r = 1.0 - 0.03 * g; out.g = 1.0; out.b = 1.0 + 0.05 * g }
            RegiElement.ELECTRIC -> { out.r = 1.0 + 0.05 * g; out.g = 1.0 + 0.03 * g; out.b = 1.0 - 0.1 * g }
            RegiElement.DRAGON -> { out.r = 1.0 + 0.08 * g; out.g = 1.0 - 0.1 * g; out.b = 1.0 - 0.07 * g }
            RegiElement.GIANT -> { out.r = 1.0 + 0.03 * g; out.g = 1.0; out.b = 1.0 - 0.05 * g }
        }
        out.exposure = 1.0 - 0.12 * g + 0.1 * c + 0.12 * f
        out.saturation = 1.0 + (if (element == RegiElement.STEEL) -0.12 else 0.05) * g
        out.contrast = 1.0 + 0.08 * g
        out.vignette = 0.1 + 0.2 * g
        return out
    }

    // ---------------------------------------------------------------- sounds

    private fun cry(): String = "cobblemon:pokemon." + when (element) {
        RegiElement.ROCK -> "regirock"
        RegiElement.ICE -> "regice"
        RegiElement.STEEL -> "registeel"
        RegiElement.ELECTRIC -> "regieleki"
        RegiElement.DRAGON -> "regidrago"
        RegiElement.GIANT -> "regigigas"
    } + ".cry"

    /** How many lights its face has. */
    val lightCount = when (element) {
        RegiElement.ELECTRIC -> 4
        RegiElement.DRAGON -> 5
        else -> 7
    }

    override val cues: List<Cue> = buildList {
        val cx = centre.x
        val cyy = centre.y
        val cz = centre.z
        fun sound(at: Double, id: String, volume: Float, pitch: Float, x: Double = cx, y: Double = cyy, z: Double = cz) =
            add(Cue(at, Cue.Kind.SOUND, id, "origin", x, y, z, volume = volume, pitch = pitch))
        // Common: the shrine wakes, the letters, the core, the reveal, the lights, the cry, calm.
        sound(0.1, "minecraft:block.beacon.activate", 1.2f, 0.6f, 0.0, 1.0, 0.0)
        sound(0.5, "minecraft:block.respawn_anchor.charge", 1.4f, if (element == RegiElement.ELECTRIC) 1.2f else 0.5f, 0.0, 0.5, 0.0)
        add(Cue(liftAt, Cue.Kind.EVENT, "lift"))
        for ((i, p) in letters.withIndex()) {
            add(Cue(liftAt + 0.12 * i, Cue.Kind.SOUND, "minecraft:block.amethyst_block.chime", "origin", p.x, p.y, p.z, volume = 1.2f, pitch = (0.7 + 0.06 * i).toFloat()))
        }
        add(Cue(gatherAt, Cue.Kind.EVENT, "gather"))
        sound(gatherAt, "minecraft:block.beacon.power_select", 1.4f, 0.7f)
        add(Cue(coreAt, Cue.Kind.EVENT, "core"))
        sound(coreAt, "minecraft:block.conduit.activate", 1.5f, 0.7f)
        sound(shapeAt, "minecraft:block.beacon.ambient", 1.8f, 0.6f)
        add(Cue(matterAt, Cue.Kind.EVENT, "matter"))
        add(Cue(revealAt, Cue.Kind.EVENT, "reveal"))
        sound(revealAt, "minecraft:block.end_portal_frame.fill", 1.6f, 0.6f)
        add(Cue(revealAt + 0.5, Cue.Kind.ANIMATE, "glad"))
        // The lights: a beep each, like a code.
        for (i in 0 until lightCount) {
            val at = lightsAt + i * 1.1 / lightCount
            add(Cue(at, Cue.Kind.SOUND, "minecraft:block.note_block.bit", "origin", face.x, face.y, face.z, volume = 1.3f, pitch = floatArrayOf(1.19f, 0.89f, 1.33f, 1.0f, 1.19f, 0.79f, 1.5f)[i % 7]))
        }
        add(Cue(lightsAt, Cue.Kind.EVENT, "lights"))
        add(Cue(cryAt, Cue.Kind.CRY, cry(), "actor", volume = 4.0f, pitch = 1.0f))
        add(Cue(cryAt, Cue.Kind.EVENT, "cry"))
        add(Cue(calmAt, Cue.Kind.EVENT, "calm"))
        sound(calmAt, "minecraft:block.beacon.deactivate", 1.0f, 0.8f, 0.0, 1.0, 0.0)
        if (test) add(Cue(end - 0.85, Cue.Kind.EVENT, "vanish"))
        // The element's own.
        when (element) {
            RegiElement.ROCK -> {
                sound(0.6, "minecraft:entity.warden.emerge", 1.4f, 0.6f, 0.0, 0.3, 0.0)
                sound(1.8, "minecraft:block.deepslate.break", 1.6f, 0.5f, 0.0, 0.3, 0.0)
                sound(coreAt + 0.5, "minecraft:block.grindstone.use", 1.2f, 0.5f)
                for (i in 0 until 5) sound(formAt + 0.4 + i * 0.8, "minecraft:block.stone.place", 1.6f, (0.55 + 0.06 * i).toFloat())
                sound(matterAt, "minecraft:block.anvil.land", 0.8f, 0.5f)
                sound(revealAt, "minecraft:block.basalt.break", 2.2f, 0.5f)
                sound(cryAt + 0.1, "minecraft:entity.warden.emerge", 1.2f, 0.9f, 0.0, 0.2, 0.0)
            }
            RegiElement.ICE -> {
                sound(0.6, "minecraft:entity.player.hurt_freeze", 0.8f, 0.5f, 0.0, 1.0, 0.0)
                sound(1.5, "minecraft:block.powder_snow.step", 1.4f, 0.6f, 0.0, 0.2, 0.0)
                sound(coreAt + 0.2, "minecraft:block.amethyst_cluster.place", 1.6f, 0.5f)
                for (i in 0 until 4) sound(formAt + 1.0 + i * 0.75, "minecraft:block.amethyst_block.break", 1.6f, (0.55 + 0.08 * i).toFloat())
                sound(revealAt, "minecraft:block.glass.break", 2.5f, 0.6f)
                sound(revealAt + 0.05, "minecraft:block.amethyst_cluster.break", 2.0f, 0.5f)
                sound(cryAt + 0.1, "minecraft:entity.breeze.wind_burst", 1.5f, 0.5f)
            }
            RegiElement.STEEL -> {
                sound(0.5, "minecraft:block.beacon.ambient", 1.6f, 0.5f, 0.0, 0.5, 0.0)
                sound(1.2, "minecraft:block.iron_door.close", 1.2f, 0.5f, 0.0, 0.5, 0.0)
                sound(coreAt + 0.3, "minecraft:block.chain.place", 1.4f, 0.5f)
                for (i in 0 until 4) sound(formAt + 0.5 + i * 0.85, "minecraft:block.anvil.use", 0.9f, (0.6 + 0.05 * i).toFloat())
                sound(formAt + 1.0, "minecraft:block.smithing_table.use", 1.2f, 0.6f)
                sound(matterAt, "minecraft:block.anvil.land", 1.0f, 0.6f)
                sound(revealAt, "minecraft:entity.iron_golem.repair", 1.6f, 0.5f)
                sound(cryAt + 0.1, "minecraft:block.anvil.land", 1.2f, 0.4f, 0.0, 0.2, 0.0)
            }
            RegiElement.ELECTRIC -> {
                for ((i, s) in strikes.withIndex()) add(Cue(s[0], Cue.Kind.EVENT, "arc$i"))
                sound(coreAt, "minecraft:block.beacon.power_select", 1.4f, 1.4f)
                sound(formAt, "minecraft:entity.guardian.attack", 1.2f, 1.6f)
                sound(revealAt, "minecraft:entity.lightning_bolt.thunder", 2.0f, 1.0f)
                sound(revealAt + 0.03, "minecraft:item.trident.thunder", 1.5f, 1.0f)
                sound(cryAt + 0.1, "minecraft:entity.lightning_bolt.impact", 1.5f, 0.8f, 0.0, 0.2, 0.0)
            }
            RegiElement.DRAGON -> {
                sound(0.6, "minecraft:entity.ender_dragon.growl", 0.5f, 0.5f, 0.0, 2.0, -12.0)
                sound(2.5, "minecraft:particle.soul_escape", 2.0f, 0.5f, 0.0, 0.5, 0.0)
                sound(coreAt, "minecraft:entity.blaze.shoot", 1.0f, 0.5f)
                sound(formAt + 0.6, "minecraft:entity.ender_dragon.ambient", 1.0f, 0.6f)
                for (i in 0 until 3) sound(formAt + 1.2 + i * 1.2, "minecraft:entity.warden.heartbeat", 2.0f, 0.6f)
                sound(revealAt, "minecraft:entity.ender_dragon.shoot", 1.5f, 0.6f)
                sound(cryAt + 0.1, "minecraft:entity.ender_dragon.growl", 1.0f, 0.8f)
            }
            RegiElement.GIANT -> {
                sound(0.5, "minecraft:entity.warden.emerge", 2.0f, 0.45f, 0.0, 0.2, 0.0)
                var at = 1.5
                while (at < formAt) {
                    sound(at, "minecraft:entity.warden.heartbeat", 2.5f, 0.5f, 0.0, 0.5, 0.0)
                    at += 1.6
                }
                sound(coreAt, "minecraft:block.end_portal.spawn", 0.7f, 0.6f)
                sound(shapeAt, "minecraft:block.conduit.ambient", 2.0f, 0.5f)
                sound(formAt, "minecraft:entity.ravager.roar", 0.8f, 0.5f)
                sound(revealAt, "minecraft:block.anvil.land", 1.5f, 0.3f, 0.0, 0.2, 0.0)
                sound(cryAt + 0.1, "minecraft:entity.warden.sonic_boom", 1.0f, 0.5f)
                sound(cryAt + 0.6, "minecraft:entity.iron_golem.step", 2.0f, 0.5f, 0.0, 0.2, 0.0)
            }
        }
    }.sortedBy { it.time }
}
