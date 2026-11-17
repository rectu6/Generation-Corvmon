package generations.gg.generations.core.generationscore.common.client.johto

import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CamPose
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraDirector
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.decay
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutSine
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.lerp
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.phase
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.pulse
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smooth
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smoother
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.window
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.Shot
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorKey
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorPose
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Cue
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Grade
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.SceneScript
import org.joml.Vector3d
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * The Legend of Johto's scenes (JOHTO-PLAN.md §§ 4, 5, 9, 10), pure (tested off-game by
 * tools/CinematicBench). Each frame: the scene's origin on the ground, +z toward the watcher's
 * side (the player, the history's anchor's view, the altar's approach).
 */

private fun shot(name: String, start: Double, blend: Double, f: (Double, CamPose) -> Unit) = Shot(name, start, blend) { t, _, out -> f(t, out) }

private fun yawOf(dx: Double, dz: Double): Double = Math.toDegrees(atan2(-dx, dz))

/** The three beasts' places in the tower, and the ways they flee (history, summoning). */
internal object Trio {
    val colours = intArrayOf(0xFFD84A, 0xFF6A2A, 0x5AD2FF)
    val species = arrayOf("cobblemon:raikou", "cobblemon:entei", "cobblemon:suicune")
    /** Their angles round the centre (radians): Raikou to the left, Entei behind, Suicune to the right. */
    val angles = doubleArrayOf(2.4, -1.57, 0.75)
}

/**
 * The history of the Burned Tower, 50 s, seven sequences: the tower whole with three unknown
 * shapes; the storm and the lightning; the fire; the rain; the rainbow and Ho-Oh with three
 * spheres; the rebirth (yellow, red, blue); the flight. Ho-Oh is the actor; the three beasts are
 * extra actors; the tower and the shapes are drawn (nothing in the world changes).
 */
class JohtoHistoryScript(val height: Double, val radius: Double) : SceneScript {
    override fun action(t: Double): Double = t

    /** Where the three shapes lie, then the beasts are reborn (local, on the ground). */
    fun place(i: Int, out: Vector3d): Vector3d {
        val r = radius * 0.45
        return out.set(cos(Trio.angles[i]) * r, 0.0, sin(Trio.angles[i]) * r)
    }

    private val h1 = Vector3d(-22.0, 46.0, -38.0)
    private val h2 = Vector3d(-6.0, 20.0, -10.0)

    fun hoohPos(a: Double, out: Vector3d): Vector3d {
        val hover = Vector3d(0.0, height * 0.55 + 4.0, 0.0)
        return when {
            a < 34.0 -> {
                val u = easeInOutSine(phase(a, 29.0, 34.0))
                out.set(lerp(h1.x, hover.x, u), lerp(h1.y, hover.y, u), lerp(h1.z, hover.z, u))
            }
            a < 44.0 -> out.set(hover.x + 0.6 * sin(a * 0.9), hover.y + 0.4 * sin(a * 1.3), hover.z)
            else -> {
                val u = smooth(phase(a, 44.0, 49.5))
                out.set(lerp(hover.x, 30.0, u * u), lerp(hover.y, 60.0, u), lerp(hover.z, -40.0, u))
            }
        }
    }

    override fun actor(a: Double, out: ActorKey): ActorKey {
        out.scale = SCALE
        out.pose = if (a < 29.0 || a > 49.6) ActorPose.HIDDEN else if (a in 34.0..44.0) ActorPose.HOVER else ActorPose.FLY
        hoohPos(a, out.pos)
        val ahead = hoohPos(a + 0.2, Vector3d())
        val dx = ahead.x - out.pos.x
        val dz = ahead.z - out.pos.z
        out.yaw = if (abs(dx) + abs(dz) < 0.02) 0.0 else yawOf(dx, dz)
        out.pitch = 0.0
        out.roll = 0.0
        return out
    }

    /** Beast i at action time a: reborn at 38.5 s, fleeing from 43 s (hidden before and after). */
    fun beast(i: Int, a: Double, out: ActorKey): ActorKey {
        out.scale = 1.0
        out.pose = if (a < REBIRTH || a > 47.8) ActorPose.HIDDEN else ActorPose.STAND
        place(i, out.pos)
        val ang = Trio.angles[i]
        val run = (a - 43.0 - i * 0.3).coerceAtLeast(0.0)
        val d = 7.5 * run + 2.0 * run * run
        out.pos.x += cos(ang) * d
        out.pos.z += sin(ang) * d
        out.pos.y += if (run > 0.0) abs(sin(run * 5.0)) * 0.9 else 0.0
        out.yaw = if (run > 0.0) yawOf(cos(ang), sin(ang)) else yawOf(-out.pos.x, 6.0 - out.pos.z)
        out.pitch = 0.0
        out.roll = 0.0
        return out
    }

    private val m = Vector3d()
    private val r get() = radius

    private val tower = shot("tower", 0.0, 0.0) { t, out ->
        val u = easeInOutSine(phase(t, 0.0, 7.5))
        out.pos.set(lerp(0.0, r * 0.6, u), lerp(6.0, 4.0, u), lerp(r * 3.4, r * 2.4, u))
        out.target.set(0.0, height * 0.45, 0.0)
        out.fov = lerp(62.0, 55.0, u)
    }
    private val storm = shot("storm", 7.0, 1.6) { t, out ->
        val u = easeInOutSine(phase(t, 7.0, 14.0))
        // From near the first shot's end, round to the left (no rush across, whatever the tower's size).
        out.pos.set(lerp(-0.2 * r, -1.2 * r, u), lerp(3.0, 2.4, u), lerp(2.3 * r, 1.8 * r, u))
        out.target.set(0.0, lerp(height * 0.6, height * 0.95, u), 0.0)
        out.fov = 58.0 + 3.0 * pulse(t, STRIKE, 0.05, 0.6)
    }
    private val fire = shot("fire", 14.0, 1.0) { t, out ->
        val u = easeInOutSine(phase(t, 14.0, 22.0))
        // The same side as the storm's shot (no rush across the tower), coming round to the front.
        out.pos.set(lerp(-1.3 * r, -0.4 * r, u), lerp(2.4, 2.0, u), lerp(1.7 * r, 1.5 * r, u))
        out.target.set(0.0, lerp(height * 0.45, 1.5, u), 0.0)
        out.fov = 62.0
    }
    private val rain = shot("rain", 22.0, 1.5) { t, out ->
        val u = easeInOutSine(phase(t, 22.0, 28.0))
        out.pos.set(lerp(0.9 * r, 0.5 * r, u), 1.3, lerp(1.7 * r, 1.5 * r, u))
        out.target.set(0.0, 0.6, 0.0)
        out.fov = 50.0
    }
    private val rainbow = shot("rainbow", 28.0, 1.6) { t, out ->
        hoohPos(t, m)
        val u = easeInOutSine(phase(t, 28.0, 36.0))
        out.pos.set(0.0, lerp(2.2, 3.0, u), lerp(2.7 * r, 2.2 * r, u))
        val sky = smooth(phase(t, 29.0, 31.0))
        out.target.set(lerp(0.0, m.x, sky), lerp(12.0, m.y, sky), lerp(-20.0, m.z, sky))
        out.fov = 64.0
    }
    private val rebirth = shot("rebirth", 36.0, 1.2) { t, out ->
        val u = easeInOutSine(phase(t, 36.0, 43.0))
        out.pos.set(lerp(-0.5, 0.5, u), lerp(3.2, 2.4, u), r * 1.3 + 3.0)
        out.target.set(0.0, lerp(3.0, 1.0, u), 0.0)
        out.fov = 56.0 + 3.0 * pulse(t, REBIRTH, 0.05, 0.6)
    }
    private val flight = shot("flight", 43.0, 1.0) { t, out ->
        val u = easeInOutSine(phase(t, 43.0, 50.0))
        out.pos.set(0.0, lerp(4.0, 14.0, u), lerp(2.0 * r, 3.0 * r, u))
        out.target.set(0.0, lerp(3.0, 8.0, u), 0.0)
        out.fov = 62.0
    }

    override val director = CameraDirector(listOf(tower, storm, fire, rain, rainbow, rebirth, flight))

    override fun cameraWeight(t: Double): Double = smoother(t / 1.2) * (1.0 - smoother((t - 48.4) / 1.6))
    override fun letterbox(t: Double): Double = smooth(t / 1.0) * (1.0 - smooth((t - 48.6) / 1.3))
    override fun title(t: Double): Double = window(t, 1.5, 2.3, 5.5, 6.6)
    override fun presence(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 48.5) / 1.5))
    override fun shake(a: Double): Double = 0.5 * decay(a, STRIKE, 0.5) + 0.2 * window(a, 19.0, 19.6, 21.0, 22.0) + 0.25 * decay(a, REBIRTH, 0.5)
    override fun flash(a: Double): Double = (0.5 * pulse(a, STRIKE, 0.03, 0.3) + 0.4 * pulse(a, REBIRTH, 0.05, 0.5)).coerceIn(0.0, 1.0)

    override fun grade(t: Double, out: Grade): Grade {
        val storm = window(t, 7.0, 9.0, 27.0, 30.0)
        val fire = window(t, 12.3, 14.0, 21.0, 24.0)
        val gloom = window(t, 21.0, 23.0, 28.0, 31.0)
        val glory = window(t, 29.0, 31.0, 46.0, 49.0)
        out.r = 1.0 - 0.18 * storm + 0.22 * fire + 0.08 * glory
        out.g = 1.0 - 0.12 * storm + 0.04 * fire + 0.04 * glory
        out.b = 1.0 + 0.04 * storm - 0.16 * fire - 0.06 * glory
        out.exposure = 1.0 - 0.22 * storm - 0.1 * gloom + 0.12 * glory
        out.saturation = 1.0 - 0.25 * gloom + 0.2 * glory
        out.contrast = 1.0 + 0.08 * storm
        out.vignette = 0.15 + 0.2 * storm
        return out
    }

    // ---------------------------------------------------------------- envelopes

    /** The ghost tower whole; its fire; its fall. */
    fun towerWhole(a: Double): Double = window(a, 0.0, 1.5, 19.0, 22.5)
    fun burn(a: Double): Double = window(a, STRIKE, 15.0, 22.0, 25.0)
    fun collapse(a: Double): Double = smooth(phase(a, 19.0, 22.5))
    /** The three unknown shapes (no species: smoke and light). */
    fun shapes(a: Double): Double = window(a, 0.5, 2.0, 16.5, 21.0)
    fun clouds(a: Double): Double = window(a, 7.0, 10.0, 28.0, 32.0)
    fun rainFall(a: Double): Double = window(a, 8.5, 11.0, 30.0, 34.0) * (0.55 + 0.45 * window(a, 21.0, 23.0, 27.0, 30.0))
    fun rainbow(a: Double): Double = window(a, 28.5, 31.0, 46.0, 49.0)
    fun spheres(a: Double): Double = window(a, 33.0, 35.0, REBIRTH - 0.3, REBIRTH)
    fun auras(a: Double): Double = window(a, REBIRTH, REBIRTH + 0.4, 45.0, 47.8)

    override fun envelopes(a: Double): DoubleArray = doubleArrayOf(towerWhole(a), burn(a), collapse(a), shapes(a), clouds(a), rainFall(a), rainbow(a), spheres(a), auras(a))

    override val cues: List<Cue> = buildList {
        add(Cue(9.5, Cue.Kind.SOUND, "minecraft:entity.lightning_bolt.thunder", "origin", -30.0, 40.0, -50.0, volume = 2f, pitch = 0.6f))
        add(Cue(STRIKE, Cue.Kind.SOUND, "minecraft:entity.lightning_bolt.impact", "origin", 0.0, height, 0.0, volume = 3f, pitch = 0.8f))
        add(Cue(STRIKE + 0.15, Cue.Kind.SOUND, "minecraft:entity.lightning_bolt.thunder", "origin", 0.0, height, 0.0, volume = 3f, pitch = 0.7f))
        add(Cue(STRIKE, Cue.Kind.EVENT, "strike"))
        add(Cue(12.6, Cue.Kind.SOUND, "minecraft:entity.blaze.shoot", "origin", 0.0, height * 0.8, 0.0, volume = 2f, pitch = 0.5f))
        add(Cue(15.0, Cue.Kind.SOUND, "minecraft:block.fire.ambient", "origin", 0.0, 3.0, 0.0, volume = 3f, pitch = 0.8f))
        add(Cue(17.5, Cue.Kind.SOUND, "minecraft:block.fire.ambient", "origin", 2.0, 5.0, 1.0, volume = 3f, pitch = 0.7f))
        add(Cue(19.6, Cue.Kind.SOUND, "minecraft:entity.generic.explode", "origin", 0.0, height * 0.5, 0.0, volume = 1.6f, pitch = 0.55f))
        add(Cue(19.6, Cue.Kind.EVENT, "collapse"))
        add(Cue(23.5, Cue.Kind.SOUND, "minecraft:block.fire.extinguish", "origin", 0.0, 1.0, 0.0, volume = 1.4f, pitch = 0.6f))
        add(Cue(29.0, Cue.Kind.SOUND, "minecraft:block.amethyst_block.resonate", "origin", 0.0, 12.0, -10.0, volume = 1.6f, pitch = 0.8f))
        add(Cue(30.5, Cue.Kind.SOUND, "cobblemon:pokemon.hooh.cry", "actor", volume = 3.5f, pitch = 0.95f))
        add(Cue(33.0, Cue.Kind.EVENT, "spheres"))
        add(Cue(33.0, Cue.Kind.SOUND, "minecraft:block.beacon.activate", "origin", 0.0, 3.0, 0.0, volume = 1.4f, pitch = 1.3f))
        add(Cue(35.0, Cue.Kind.CRY, "cobblemon:pokemon.hooh.cry", "actor", volume = 3.5f))
        add(Cue(REBIRTH, Cue.Kind.EVENT, "rebirth"))
        add(Cue(REBIRTH, Cue.Kind.SOUND, "minecraft:item.totem.use", "origin", 0.0, 1.5, 0.0, volume = 1.2f, pitch = 1.1f))
        for (i in 0..2) {
            val p = place(i, Vector3d())
            add(Cue(39.2 + i * 0.6, Cue.Kind.SOUND, "cobblemon:pokemon.${Trio.species[i].substringAfter(':')}.cry", "origin", p.x, 1.0, p.z, volume = 2.5f))
        }
        add(Cue(44.2, Cue.Kind.SOUND, "cobblemon:pokemon.hooh.cry", "actor", volume = 3f, pitch = 1.05f))
        add(Cue(44.5, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 2.5f, pitch = 1.2f))
    }.sortedBy { it.time }

    companion object {
        const val STRIKE = 12.0
        const val REBIRTH = 38.5
        const val SCALE = 1.4
    }
}

/**
 * A statue's awakening, 20 s: the statue trembles and cracks, ashes rise, a burst — the real
 * beast, its cry, it turns to the player and flees; then the statue forms again from its ashes
 * (D15). syaw: the statue's own way in this frame.
 */
class JohtoAwakeningScript(val beast: Int, val syaw: Double, val statueScale: Double) : SceneScript {
    override fun action(t: Double): Double = t

    /** The beast's feet at time a (at the statue, then fleeing along -z with a curve). */
    fun beastPos(a: Double, out: Vector3d): Vector3d {
        val tremble = if (a < BURST) 0.08 * smooth(phase(a, 3.0, BURST)) else 0.0
        out.set(sin(a * 47.0) * tremble, 0.0, cos(a * 53.0) * tremble)
        val run = (a - FLEE).coerceAtLeast(0.0)
        if (run > 0.0) {
            val d = 5.5 * run + 1.1 * run * run
            val side = (if (beast == 1) -1.0 else 1.0) * 0.18 * d
            out.x += side
            out.z -= d
            out.y = abs(sin(run * 5.5)) * 0.7
        }
        return out
    }

    override fun actor(a: Double, out: ActorKey): ActorKey {
        out.scale = statueScale
        out.pose = if (a > GONE) ActorPose.HIDDEN else ActorPose.STAND
        beastPos(a, out.pos)
        out.yaw = when {
            a < TURN -> syaw
            a < FLEE -> lerp(syaw, 0.0, easeInOutSine(phase(a, TURN, TURN + 1.4)))
            else -> {
                val p0 = beastPos(a, Vector3d())
                val p1 = beastPos(a + 0.1, Vector3d())
                yawOf(p1.x - p0.x, p1.z - p0.z)
            }
        }
        out.pitch = 0.0
        out.roll = 0.0
        return out
    }

    /** The statue forming again (extra actor 0). */
    fun reform(a: Double, out: ActorKey): ActorKey {
        out.scale = statueScale * lerp(0.92, 1.0, smooth(phase(a, REFORM, 19.4)))
        out.pose = if (a < REFORM) ActorPose.HIDDEN else ActorPose.STAND
        out.pos.set(0.0, 0.0, 0.0)
        out.yaw = syaw
        out.pitch = 0.0
        out.roll = 0.0
        return out
    }

    private val m = Vector3d()

    private val approach = shot("approach", 0.0, 0.0) { t, out ->
        val u = easeInOutSine(phase(t, 0.0, 3.2))
        out.pos.set(lerp(0.4, 1.3, u), lerp(1.75, 1.5, u), lerp(5.8, 3.8, u))
        out.target.set(0.0, 1.0, 0.0)
        out.fov = lerp(66.0, 58.0, u)
    }
    private val tremor = shot("tremor", 3.0, 1.0) { t, out ->
        val u = easeInOutSine(phase(t, 3.0, BURST))
        val ang = lerp(25.0, -30.0, u)
        val rad = Math.toRadians(ang)
        out.pos.set(sin(rad) * 3.0, lerp(1.2, 0.8, u), cos(rad) * 3.0)
        out.target.set(0.0, 1.0, 0.0)
        out.fov = lerp(58.0, 50.0, u) + 6.0 * pulse(t, BURST, 0.05, 0.5)
    }
    private val reveal = shot("reveal", BURST + 0.2, 0.8) { t, out ->
        val u = easeInOutSine(phase(t, BURST, FLEE))
        out.pos.set(lerp(0.9, 0.5, u), lerp(0.7, 0.9, u), lerp(3.4, 3.0, u))
        out.target.set(0.0, 1.1, 0.0)
        out.fov = lerp(52.0, 56.0, u)
    }
    private val chase = shot("chase", FLEE, 0.9) { t, out ->
        beastPos(t, m)
        val u = easeInOutSine(phase(t, FLEE, GONE))
        out.pos.set(lerp(1.2, 0.8, u), lerp(1.6, 2.6, u), lerp(2.6, 1.0, u))
        out.target.set(m.x, m.y + 1.0, m.z)
        out.fov = lerp(60.0, 48.0, u)
    }
    private val statue = shot("statue", REFORM - 0.3, 1.0) { t, out ->
        val u = easeInOutSine(phase(t, REFORM, 20.0))
        out.pos.set(lerp(1.4, 0.6, u), lerp(1.8, 1.7, u), lerp(4.2, 4.6, u))
        out.target.set(0.0, 1.0, 0.0)
        out.fov = 60.0
    }

    override val director = CameraDirector(listOf(approach, tremor, reveal, chase, statue))

    override fun cameraWeight(t: Double): Double = smoother(t / 0.9) * (1.0 - smoother((t - 18.6) / 1.4))
    override fun letterbox(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 18.6) / 1.3))
    override fun title(t: Double): Double = window(t, BURST + 1.6, BURST + 2.1, 13.4, 14.2)
    override fun presence(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 18.7) / 1.3))
    override fun shake(a: Double): Double = 0.12 * window(a, 3.0, 6.0, 9.0, BURST) + 0.55 * decay(a, BURST, 0.5) + 0.15 * decay(a, 10.4, 0.6)
    override fun flash(a: Double): Double = (0.45 * pulse(a, BURST, 0.04, 0.4)).coerceIn(0.0, 1.0)

    override fun grade(t: Double, out: Grade): Grade {
        val tint = window(t, 3.0, BURST, 15.0, 17.5)
        val c = Trio.colours[beast]
        out.r = 1.0 + 0.1 * tint * (((c shr 16) and 255) / 255.0 - 0.5)
        out.g = 1.0 + 0.1 * tint * (((c shr 8) and 255) / 255.0 - 0.5)
        out.b = 1.0 + 0.1 * tint * ((c and 255) / 255.0 - 0.5)
        out.exposure = 1.0 - 0.1 * window(t, 2.0, 5.0, 8.5, BURST) + 0.1 * pulse(t, BURST, 0.05, 1.2)
        out.saturation = 1.0 + 0.15 * tint
        out.contrast = 1.0 + 0.05 * tint
        out.vignette = 0.2 * tint
        return out
    }

    /** The cracks' light; the ashes rising; the beast's own element; the statue's ashes coming back. */
    fun cracks(a: Double): Double = smooth(phase(a, 3.5, BURST - 0.2)) * (if (a < BURST) 1.0 else 0.0)
    fun ashes(a: Double): Double = window(a, 3.0, 5.0, BURST, BURST + 0.6)
    fun element(a: Double): Double = window(a, BURST, BURST + 0.4, GONE - 0.5, GONE + 0.8)
    fun reforming(a: Double): Double = window(a, REFORM - 0.6, REFORM, 19.0, 19.8)

    override fun envelopes(a: Double): DoubleArray = doubleArrayOf(cracks(a), ashes(a), element(a), reforming(a))

    override val cues: List<Cue> = buildList {
        val id = Trio.species[beast].substringAfter(':')
        add(Cue(1.0, Cue.Kind.SOUND, "minecraft:block.stone.hit", "origin", 0.0, 1.0, 0.0, volume = 1.2f, pitch = 0.5f))
        add(Cue(3.2, Cue.Kind.SOUND, "minecraft:block.deepslate.break", "origin", 0.0, 1.0, 0.0, volume = 1.4f, pitch = 0.6f))
        add(Cue(5.5, Cue.Kind.SOUND, "minecraft:block.stone.break", "origin", 0.0, 1.2, 0.0, volume = 1.5f, pitch = 0.5f))
        add(Cue(7.4, Cue.Kind.SOUND, "minecraft:block.deepslate.break", "origin", 0.0, 1.2, 0.0, volume = 1.8f, pitch = 0.45f))
        add(Cue(8.0, Cue.Kind.SOUND, "minecraft:block.beacon.ambient", "origin", 0.0, 1.0, 0.0, volume = 1.5f, pitch = 0.6f))
        add(Cue(BURST, Cue.Kind.EVENT, "burst"))
        add(Cue(BURST, Cue.Kind.SOUND, "minecraft:entity.generic.explode", "origin", 0.0, 1.0, 0.0, volume = 1.0f, pitch = 1.4f))
        when (beast) {
            0 -> add(Cue(BURST, Cue.Kind.SOUND, "minecraft:entity.lightning_bolt.impact", "origin", 0.0, 1.0, 0.0, volume = 2f, pitch = 1f))
            1 -> add(Cue(BURST, Cue.Kind.SOUND, "minecraft:entity.blaze.shoot", "origin", 0.0, 1.0, 0.0, volume = 2f, pitch = 0.6f))
            else -> add(Cue(BURST, Cue.Kind.SOUND, "minecraft:entity.player.splash.high_speed", "origin", 0.0, 1.0, 0.0, volume = 1.6f, pitch = 0.8f))
        }
        add(Cue(10.4, Cue.Kind.CRY, "cobblemon:pokemon.$id.cry", "actor", volume = 3f))
        add(Cue(FLEE, Cue.Kind.SOUND, "minecraft:entity.horse.gallop", "actor", volume = 1.4f, pitch = 0.7f))
        add(Cue(FLEE + 0.8, Cue.Kind.SOUND, "minecraft:entity.horse.gallop", "actor", volume = 1.2f, pitch = 0.75f))
        add(Cue(REFORM, Cue.Kind.SOUND, "minecraft:block.sand.place", "origin", 0.0, 1.0, 0.0, volume = 1.4f, pitch = 0.6f))
        add(Cue(19.2, Cue.Kind.SOUND, "minecraft:block.stone.place", "origin", 0.0, 1.0, 0.0, volume = 1.4f, pitch = 0.7f))
    }.sortedBy { it.time }

    companion object {
        const val BURST = 9.5
        const val TURN = 10.8
        const val FLEE = 13.2
        const val GONE = 16.8
        const val REFORM = 17.4
    }
}

/**
 * A beast's encounter, 22 s: its omen, it comes running, lands in a burst of its element, its
 * cry, the camera round it, its gaze; it stays where the wild one appears at the end.
 */
class JohtoEncounterScript(val beast: Int) : SceneScript {
    override fun action(t: Double): Double = t

    private val from = Vector3d(if (beast == 1) 14.0 else -14.0, 0.0, -12.0)

    fun beastPos(a: Double, out: Vector3d): Vector3d {
        val u = phase(a, ARRIVE, LAND)
        val e = easeInOutSine(u)
        out.set(lerp(from.x, 0.0, e), 0.0, lerp(from.z, 0.0, e))
        if (u in 0.0..0.999) out.y = abs(sin(u * 9.0)) * 1.1
        return out
    }

    override fun actor(a: Double, out: ActorKey): ActorKey {
        out.scale = 1.0
        out.pose = if (a < ARRIVE) ActorPose.HIDDEN else ActorPose.STAND
        beastPos(a, out.pos)
        out.yaw = if (a < LAND) yawOf(-from.x, -from.z) else lerp(yawOf(-from.x, -from.z), 0.0, easeInOutSine(phase(a, LAND, LAND + 1.2)))
        out.pitch = 0.0
        out.roll = 0.0
        return out
    }

    private val m = Vector3d()

    private val omen = shot("omen", 0.0, 0.0) { t, out ->
        val u = easeInOutSine(phase(t, 0.0, ARRIVE))
        out.pos.set(lerp(0.0, 1.0, u), lerp(1.8, 2.2, u), lerp(9.0, 8.0, u))
        out.target.set(0.0, lerp(3.0, 1.5, u), 0.0)
        out.fov = 62.0
    }
    private val run = shot("run", ARRIVE, 0.8) { t, out ->
        beastPos(t, m)
        out.pos.set(2.6, 1.4, 6.5)
        out.target.set(m.x, m.y + 1.0, m.z)
        out.fov = 58.0 + 5.0 * pulse(t, LAND, 0.05, 0.6)
    }
    private val orbit = shot("orbit", LAND + 0.8, 1.0) { t, out ->
        val u = easeInOutSine(phase(t, LAND + 0.8, 12.5))
        val ang = Math.toRadians(lerp(40.0, 190.0, u))
        out.pos.set(sin(ang) * 5.2, lerp(1.6, 2.4, u), cos(ang) * 5.2)
        out.target.set(0.0, 1.1, 0.0)
        out.fov = 56.0
    }
    private val gaze = shot("gaze", 12.5, 1.2) { t, out ->
        val u = easeInOutSine(phase(t, 12.5, 18.0))
        out.pos.set(lerp(0.8, 0.4, u), lerp(0.8, 1.0, u), lerp(3.2, 2.8, u))
        out.target.set(0.0, 1.2, 0.0)
        out.fov = lerp(50.0, 46.0, u)
    }
    private val back = shot("back", 18.0, 1.4) { t, out ->
        val u = easeInOutSine(phase(t, 18.0, 22.0))
        out.pos.set(lerp(0.6, 0.0, u), lerp(1.7, 1.62, u), lerp(5.0, 8.0, u))
        out.target.set(0.0, 1.1, 0.0)
        out.fov = lerp(58.0, 70.0, u)
    }

    override val director = CameraDirector(listOf(omen, run, orbit, gaze, back))

    override fun cameraWeight(t: Double): Double = smoother(t / 0.9) * (1.0 - smoother((t - 20.4) / 1.6))
    override fun letterbox(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 20.4) / 1.4))
    override fun title(t: Double): Double = window(t, 8.6, 9.2, 11.8, 12.6)
    override fun presence(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 20.5) / 1.5))
    override fun shake(a: Double): Double = 0.5 * decay(a, LAND, 0.5) + 0.1 * window(a, 1.0, 3.0, 4.0, ARRIVE)
    override fun flash(a: Double): Double = (0.4 * pulse(a, LAND, 0.04, 0.4)).coerceIn(0.0, 1.0)

    override fun grade(t: Double, out: Grade): Grade {
        val k = window(t, 0.5, 2.5, 18.0, 21.0)
        out.neutral()
        when (beast) {
            0 -> { out.b = 1.0 + 0.06 * k; out.exposure = 1.0 - 0.12 * k * (1.0 - smooth(phase(t, LAND, LAND + 2.0))) }
            1 -> { out.r = 1.0 + 0.1 * k; out.b = 1.0 - 0.08 * k; out.exposure = 1.0 + 0.04 * k }
            else -> { out.b = 1.0 + 0.1 * k; out.g = 1.0 + 0.03 * k; out.saturation = 1.0 - 0.1 * k }
        }
        out.vignette = 0.18 * k
        return out
    }

    fun omen(a: Double): Double = window(a, 0.3, 2.0, LAND - 0.3, LAND + 0.5)
    fun aura(a: Double): Double = window(a, LAND, LAND + 0.6, 20.0, 21.8)

    override fun envelopes(a: Double): DoubleArray = doubleArrayOf(omen(a), aura(a))

    override val cues: List<Cue> = buildList {
        val id = Trio.species[beast].substringAfter(':')
        when (beast) {
            0 -> {
                add(Cue(1.2, Cue.Kind.SOUND, "minecraft:entity.lightning_bolt.thunder", "origin", -20.0, 30.0, -30.0, volume = 2f, pitch = 0.8f))
                add(Cue(LAND, Cue.Kind.SOUND, "minecraft:entity.lightning_bolt.impact", "origin", 0.0, 1.0, 0.0, volume = 3f, pitch = 1f))
            }
            1 -> {
                add(Cue(1.2, Cue.Kind.SOUND, "minecraft:block.lava.ambient", "origin", 0.0, 0.5, 0.0, volume = 2f, pitch = 0.6f))
                add(Cue(LAND, Cue.Kind.SOUND, "minecraft:entity.blaze.shoot", "origin", 0.0, 1.0, 0.0, volume = 3f, pitch = 0.5f))
            }
            else -> {
                add(Cue(1.2, Cue.Kind.SOUND, "minecraft:ambient.underwater.enter", "origin", 0.0, 1.0, 0.0, volume = 1.2f, pitch = 1.2f))
                add(Cue(LAND, Cue.Kind.SOUND, "minecraft:entity.player.splash.high_speed", "origin", 0.0, 1.0, 0.0, volume = 2.5f, pitch = 0.7f))
            }
        }
        add(Cue(ARRIVE, Cue.Kind.SOUND, "minecraft:entity.horse.gallop", "actor", volume = 1.6f, pitch = 0.7f))
        add(Cue(LAND, Cue.Kind.EVENT, "land"))
        add(Cue(8.2, Cue.Kind.CRY, "cobblemon:pokemon.$id.cry", "actor", volume = 3.5f))
        add(Cue(13.6, Cue.Kind.ANIMATE, "special"))
        add(Cue(16.0, Cue.Kind.SOUND, "cobblemon:pokemon.$id.cry", "actor", volume = 2f, pitch = 1.05f))
    }.sortedBy { it.time }

    companion object {
        const val ARRIVE = 4.0
        const val LAND = 7.0
    }
}

/**
 * Ho-Oh summoned at the Bell Tower, 30 s, six sequences: the memories rise; three spectral beasts
 * run round the altar; their beams to the sky; the sky turns golden, a rainbow; Ho-Oh comes down;
 * it lands where the wild one appears. landing: that point (local).
 */
class HoOhSummonScript(landing: Vector3d) : SceneScript {
    val landing = Vector3d(landing)

    override fun action(t: Double): Double = t

    /** Memory i's light (sequence 1). */
    fun memory(i: Int, a: Double, out: Vector3d): Vector3d {
        val u = smooth(phase(a, 0.6 + i * 0.4, 4.5))
        val ang = Trio.angles[i] + a * 0.8
        return out.set(cos(ang) * 1.3 * u, 0.6 + 2.4 * u, sin(ang) * 1.3 * u)
    }

    /** Spectral beast i (extra actor i): running round the altar, then still at its place, fading. */
    fun ghost(i: Int, a: Double, out: ActorKey): ActorKey {
        out.scale = 1.0
        out.pose = if (a < 5.0 || a > 20.2) ActorPose.HIDDEN else ActorPose.STAND
        val spin = (a.coerceAtMost(11.0) - 5.0) * 0.9
        val ang = Trio.angles[i] + spin
        val r = 7.0
        out.pos.set(cos(ang) * r, if (a < 11.0) abs(sin(a * 5.0 + i)) * 0.6 else 0.0, sin(ang) * r)
        out.yaw = if (a < 11.0) yawOf(-sin(ang), cos(ang)) else yawOf(-out.pos.x, -out.pos.z)
        out.pitch = 0.0
        out.roll = 0.0
        return out
    }

    fun ghostAlpha(a: Double): Double = window(a, 5.0, 6.2, 18.0, 20.2)

    fun hoohPos(a: Double, out: Vector3d): Vector3d {
        val high = Vector3d(-10.0, 55.0, -35.0)
        val above = Vector3d(landing.x, landing.y + 6.0, landing.z)
        return when {
            a < DESCENT_END -> {
                val u = easeInOutSine(phase(a, DESCENT, DESCENT_END))
                out.set(lerp(high.x, above.x, u), lerp(high.y, above.y, u), lerp(high.z, above.z, u))
            }
            else -> {
                val u = smoother(phase(a, DESCENT_END, LANDED))
                out.set(above.x, lerp(above.y, landing.y, u), above.z)
            }
        }
    }

    override fun actor(a: Double, out: ActorKey): ActorKey {
        out.scale = lerp(SCALE, 1.0, smooth(phase(a, DESCENT_END, LANDED)))
        out.pose = when {
            a < DESCENT -> ActorPose.HIDDEN
            a < DESCENT_END -> ActorPose.FLY
            a < LANDED -> ActorPose.HOVER
            else -> ActorPose.STAND
        }
        hoohPos(a, out.pos)
        out.yaw = if (a < DESCENT_END - 0.5) {
            val p1 = hoohPos(a + 0.2, Vector3d())
            yawOf(p1.x - out.pos.x, p1.z - out.pos.z)
        } else lerp(yawOf(-landing.x, 8.0 - landing.z), yawOf(-landing.x, 8.0 - landing.z), 1.0)
        out.pitch = 0.0
        out.roll = 0.0
        return out
    }

    private val m = Vector3d()

    private val rise = shot("rise", 0.0, 0.0) { t, out ->
        val u = easeInOutSine(phase(t, 0.0, 5.0))
        out.pos.set(lerp(0.0, 1.5, u), lerp(1.8, 2.4, u), lerp(5.5, 4.2, u))
        out.target.set(0.0, lerp(1.0, 2.8, u), 0.0)
        out.fov = 60.0
    }
    private val circle = shot("circle", 5.0, 1.0) { t, out ->
        val u = easeInOutSine(phase(t, 5.0, 11.0))
        val ang = Math.toRadians(lerp(-20.0, 70.0, u))
        out.pos.set(sin(ang) * 11.0, lerp(4.0, 3.0, u), cos(ang) * 11.0)
        out.target.set(0.0, 1.0, 0.0)
        out.fov = 64.0
    }
    private val beams = shot("beams", 11.0, 1.2) { t, out ->
        val u = easeInOutSine(phase(t, 11.0, 20.0))
        out.pos.set(lerp(3.0, 0.0, u), lerp(2.0, 2.5, u), lerp(9.5, 12.0, u))
        out.target.set(0.0, lerp(4.0, 22.0, u), lerp(0.0, -8.0, u))
        out.fov = lerp(66.0, 70.0, u)
    }
    private val descent = shot("descent", DESCENT, 1.4) { t, out ->
        hoohPos(t, m)
        out.pos.set(2.5, 2.2, 10.0)
        out.target.set(m.x, m.y + 1.5, m.z)
        out.fov = 62.0
    }
    private val landed = shot("landed", DESCENT_END + 0.5, 1.2) { t, out ->
        val u = easeInOutSine(phase(t, DESCENT_END + 0.5, 30.0))
        out.pos.set(landing.x + lerp(3.0, 1.5, u), landing.y + lerp(1.2, 1.6, u), landing.z + lerp(7.5, 8.5, u))
        out.target.set(landing.x, landing.y + 2.0, landing.z)
        out.fov = lerp(56.0, 62.0, u)
    }

    override val director = CameraDirector(listOf(rise, circle, beams, descent, landed))

    override fun cameraWeight(t: Double): Double = smoother(t / 1.0) * (1.0 - smoother((t - 28.4) / 1.6))
    override fun letterbox(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 28.5) / 1.4))
    override fun title(t: Double): Double = window(t, 27.0, 27.6, 29.0, 29.8)
    override fun presence(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 28.5) / 1.5))
    override fun shake(a: Double): Double = 0.15 * window(a, 11.0, 12.0, 14.0, 15.5) + 0.35 * decay(a, LANDED, 0.6)
    override fun flash(a: Double): Double = (0.3 * pulse(a, 11.0, 0.05, 0.5) + 0.3 * pulse(a, LANDED, 0.05, 0.5)).coerceIn(0.0, 1.0)

    override fun grade(t: Double, out: Grade): Grade {
        val gold = window(t, 13.0, 16.0, 27.0, 30.0)
        out.r = 1.0 + 0.1 * gold
        out.g = 1.0 + 0.03 * gold
        out.b = 1.0 - 0.1 * gold
        out.exposure = 1.0 + 0.1 * gold
        out.saturation = 1.0 + 0.2 * gold
        out.contrast = 1.0 + 0.04 * gold
        out.vignette = 0.12 + 0.1 * gold
        return out
    }

    fun memories(a: Double): Double = window(a, 0.4, 1.4, 5.5, 7.0)
    fun beams(a: Double): Double = window(a, 11.0, 12.0, 17.5, 20.0)
    fun rainbow(a: Double): Double = window(a, 15.0, 17.5, 27.5, 30.0)
    fun embers(a: Double): Double = window(a, DESCENT, DESCENT + 1.5, 28.0, 30.0)

    override fun envelopes(a: Double): DoubleArray = doubleArrayOf(memories(a), ghostAlpha(a), beams(a), rainbow(a), embers(a))

    override val cues: List<Cue> = buildList {
        add(Cue(0.6, Cue.Kind.SOUND, "minecraft:block.amethyst_block.resonate", "origin", 0.0, 2.0, 0.0, volume = 1.4f, pitch = 0.7f))
        add(Cue(1.2, Cue.Kind.SOUND, "minecraft:block.beacon.activate", "origin", 0.0, 2.0, 0.0, volume = 1.2f, pitch = 1.2f))
        for (i in 0..2) add(Cue(5.4 + i * 0.5, Cue.Kind.SOUND, "cobblemon:pokemon.${Trio.species[i].substringAfter(':')}.cry", "origin", cos(Trio.angles[i]) * 7.0, 1.0, sin(Trio.angles[i]) * 7.0, volume = 1.8f, pitch = 0.85f))
        add(Cue(11.0, Cue.Kind.EVENT, "beams"))
        add(Cue(11.0, Cue.Kind.SOUND, "minecraft:block.beacon.power_select", "origin", 0.0, 3.0, 0.0, volume = 2f, pitch = 0.8f))
        add(Cue(15.5, Cue.Kind.SOUND, "minecraft:block.amethyst_block.resonate", "origin", 0.0, 16.0, -12.0, volume = 1.8f, pitch = 0.9f))
        add(Cue(DESCENT + 0.3, Cue.Kind.SOUND, "cobblemon:pokemon.hooh.cry", "actor", volume = 4f, pitch = 0.95f))
        add(Cue(22.5, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 3f, pitch = 1.1f))
        add(Cue(24.5, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 3f, pitch = 1.15f))
        add(Cue(LANDED, Cue.Kind.EVENT, "landed"))
        add(Cue(LANDED + 0.2, Cue.Kind.CRY, "cobblemon:pokemon.hooh.cry", "actor", volume = 4f))
    }.sortedBy { it.time }

    companion object {
        const val DESCENT = 20.0
        const val DESCENT_END = 25.5
        const val LANDED = 27.6
        const val SCALE = 1.4
    }
}
