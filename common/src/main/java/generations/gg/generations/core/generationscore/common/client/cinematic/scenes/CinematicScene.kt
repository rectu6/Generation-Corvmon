package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClientConfig
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CinematicCamera
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxBatches
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxParticles
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.PostParams
import net.minecraft.network.chat.Component
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * What a scene sees this frame (client): its times, the camera, the actor, the settings and the
 * particle pool. Positions given to the batches are camera-relative (rel).
 */
class SceneFrame(
    val camera: CinematicCamera,
    val config: CinematicClientConfig,
    val particles: FxParticles,
    val test: Boolean,
) {
    /** Scene time (written for 20 s), action time (slow motion), their steps since the last frame. */
    var t = 0.0
    var a = 0.0
    var da = 0.0
    var dt = 0.0

    /** The camera as drawn (world position, axes). */
    val cam = Vector3d()
    val right = Vector3f()
    val up = Vector3f()
    val forward = Vector3f()

    val actor = ActorKey()
    val actorWorld = Vector3d()

    /** The world pass's matrices (camera rotation, projection), for the screen positions. */
    val view = Matrix4f()
    val projection = Matrix4f()
    var aspect = 1f

    private val scratch = Vector3d()
    private val clip = Vector4f()

    /** A local point (x right, y up, z forward) relative to the camera. */
    fun rel(x: Double, y: Double, z: Double, out: Vector3f): Vector3f {
        camera.toWorld(x, y, z, scratch)
        return out.set((scratch.x - cam.x).toFloat(), (scratch.y - cam.y).toFloat(), (scratch.z - cam.z).toFloat())
    }

    fun rel(local: Vector3d, out: Vector3f): Vector3f = rel(local.x, local.y, local.z, out)

    /** A world point relative to the camera. */
    fun relWorld(world: Vector3d, out: Vector3f): Vector3f =
        out.set((world.x - cam.x).toFloat(), (world.y - cam.y).toFloat(), (world.z - cam.z).toFloat())

    /**
     * A camera-relative point on the screen: out = (u, v) from 0 to 1 (v up). False when it is
     * behind the camera.
     */
    fun project(rel: Vector3f, out: FloatArray): Boolean {
        clip.set(rel.x, rel.y, rel.z, 1f)
        view.transform(clip)
        projection.transform(clip)
        if (clip.w <= 0.05f) return false
        out[0] = (clip.x / clip.w) * 0.5f + 0.5f
        out[1] = (clip.y / clip.w) * 0.5f + 0.5f
        return true
    }

    /** The size on screen (in screen heights) of a radius at that camera-relative point. */
    fun screenRadius(rel: Vector3f, radius: Float): Float {
        clip.set(rel.x, rel.y, rel.z, 1f)
        view.transform(clip)
        val depth = -clip.z
        if (depth <= 0.05f) return 0f
        // projection[1][1] = cot(fov / 2): a height of 2 at depth 1 fills the screen.
        return radius * projection.m11() / depth * 0.5f
    }

    fun count(base: Int): Int = config.particles(base)

    /** A sound the scene plays itself (thunder after its lightning...): at scene time `at`, at a world position. */
    class Sound(val at: Double, val id: String, val x: Double, val y: Double, val z: Double, val volume: Float, val pitch: Float)

    val sounds = ArrayList<Sound>()

    /** Plays that sound `delay` scene seconds from now (0: this frame). */
    fun sound(delay: Double, id: String, x: Double, y: Double, z: Double, volume: Float, pitch: Float) {
        sounds += Sound(t + delay.coerceAtLeast(0.0), id, x, y, z, volume, pitch)
    }

    /** The delay of a sound from that world point to the camera (343 m/s). */
    fun soundDelay(x: Double, y: Double, z: Double): Double = cam.distance(x, y, z) / 343.0
}

/** A legendary cinematic on the client: its script, its effects, its look and its sound. */
interface CinematicScene {
    val id: String
    val script: SceneScript

    /** The name shown large at the reveal (the species' name). */
    val title: Component
    val subtitle: Component

    /** A cue of kind EVENT (bursts...). Sounds and cries are played by the client. */
    fun event(cue: Cue, frame: SceneFrame)

    /** Each frame, before drawing: particles, state. */
    fun update(frame: SceneFrame)

    /** The effects into the frame's batches. */
    fun draw(frame: SceneFrame, batches: FxBatches)

    /** The final composition (waves, grade, flash...), before the quality's limits. */
    fun post(frame: SceneFrame, out: PostParams)

    /** The scene's particle pool (its materials). */
    fun particles(capacity: Int): FxParticles

    fun music(stretch: Double, offset: Double): SynthRenderer?

    fun ambience(stretch: Double, offset: Double): SynthRenderer?

    /** More actors than the scene's Pokémon (Mew's illusions), drawn like it: how many. */
    val extraActors: Int get() = 0

    /** Extra actor i at action time a (hidden by default). */
    fun extraActor(i: Int, a: Double, out: ActorKey): ActorKey {
        out.pose = ActorPose.HIDDEN
        return out
    }

    /** Extra actor i's species ("cobblemon:raikou"), or null: the scene's Pokémon (Mew's illusions). */
    fun extraSpecies(i: Int): String? = null

    /** The scene's Pokémon's look at action time a (a statue, a ghost...): normal by default. */
    fun actorLook(a: Double, out: ActorLook): ActorLook = out.normal()

    /** Extra actor i's look at action time a. */
    fun extraLook(i: Int, a: Double, out: ActorLook): ActorLook = out.normal()

    /**
     * The rain the watcher's own client shows at action time a, from 0 to 1 (JOHTO-PLAN.md D5:
     * Minecraft's rain on this client only, given back at the end), or a negative value: none.
     * thunder: the storm's darker sky, the same way.
     */
    fun rain(a: Double): Float = -1f

    fun thunder(a: Double): Float = -1f

    /**
     * How much Minecraft's other sounds (ambience, weather, music, creatures) fade on the
     * watcher's client at action time a, from 0 (not at all) to 1 (to a quarter): Lugia's
     * silence of the depths (LUGIA-PLAN.md § 5). The scene's own sounds are not touched.
     */
    fun duck(a: Double): Float = 0f

    /**
     * Real blocks the scene draws in the world's pass (the Regis' rocks, ice, steel and Unown
     * letters, REGIS-PLAN.md § 5): how many at most. Drawn only, never placed in the world.
     */
    val props: Int get() = 0

    /** Prop i at action time a, or false: not drawn this frame. */
    fun prop(i: Int, a: Double, out: PropKey): Boolean = false
}

/** A block drawn by a scene: its look, its centre in the scene's frame, its angles, its size, its glow (0 to 15). */
class PropKey {
    var state: net.minecraft.world.level.block.state.BlockState? = null
    val pos = Vector3d()
    var yaw = 0.0
    var pitch = 0.0
    var roll = 0.0
    var scale = 1.0
    var glow = 0
}

/** How an actor is drawn: its own skin, a statue's material, a tinted ghost. */
class ActorLook {
    /** A statue material's name ("concrete"), or null. */
    var material: String? = null
    /** ARGB the model is multiplied by (-1: none). */
    var tint = -1
    var translucent = false
    /** Animations held still (a statue). */
    var frozen = false
    /** Full light (a ghost, a spirit in the sky). */
    var bright = false

    fun normal(): ActorLook {
        material = null; tint = -1; translucent = false; frozen = false; bright = false
        return this
    }

    fun statue(name: String?): ActorLook {
        normal()
        material = name
        frozen = true
        return this
    }

    fun ghost(argb: Int): ActorLook {
        normal()
        tint = argb
        translucent = true
        bright = true
        return this
    }
}
