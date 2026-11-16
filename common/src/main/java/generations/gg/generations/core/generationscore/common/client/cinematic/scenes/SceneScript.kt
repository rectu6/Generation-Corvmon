package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraDirector
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath
import org.joml.Vector3d
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** The actor's pose types (Cobblemon's FLY, HOVER, STAND), or not drawn. */
enum class ActorPose { HIDDEN, FLY, HOVER, STAND }

/**
 * The actor (the Pokémon of the scene, without an entity) at a moment, in the scene's local
 * frame: its feet, its yaw (degrees, 0 = facing local +z, toward the front), pitch (nose
 * down positive), roll (left wing up positive), scale.
 */
class ActorKey {
    val pos = Vector3d()
    var yaw = 0.0
    var pitch = 0.0
    var roll = 0.0
    var scale = 1.0
    var pose = ActorPose.HIDDEN

    fun isFinite(): Boolean = pos.x.isFinite() && pos.y.isFinite() && pos.z.isFinite() && yaw.isFinite() && pitch.isFinite() && roll.isFinite() && scale.isFinite()
}

/** The image's look: tint (multiplied), exposure, saturation, contrast, vignette. Neutral by default. */
class Grade {
    var r = 1.0
    var g = 1.0
    var b = 1.0
    var exposure = 1.0
    var saturation = 1.0
    var contrast = 1.0
    var vignette = 0.0

    fun neutral(): Grade {
        r = 1.0; g = 1.0; b = 1.0; exposure = 1.0; saturation = 1.0; contrast = 1.0; vignette = 0.0
        return this
    }

    fun isFinite(): Boolean = r.isFinite() && g.isFinite() && b.isFinite() && exposure.isFinite() && saturation.isFinite() && contrast.isFinite() && vignette.isFinite()
}

/**
 * Something that happens once at a moment of the action (slow motion included): a sound at a
 * place of the scene, or an event the scene draws (a burst...). where: "origin", "actor" or
 * "camera"; offset in the local frame.
 */
class Cue(
    val time: Double,
    val kind: Kind,
    val id: String,
    val where: String = "origin",
    val dx: Double = 0.0,
    val dy: Double = 0.0,
    val dz: Double = 0.0,
    val volume: Float = 1f,
    val pitch: Float = 1f,
) {
    enum class Kind { SOUND, CRY, EVENT, ANIMATE }
}

/**
 * Slow motion that gives the time back: the action runs slower around `slowAt` (down to
 * 1 - slowDepth) then faster around `catchAt`, by the same amount, so that the action is
 * again on time after it (equal areas: slowDepth × slowWidth = catchDepth × catchWidth).
 * action(t) is increasing; realOf is its inverse.
 */
class SlowMotion(
    private val slowAt: Double,
    private val slowWidth: Double,
    private val slowDepth: Double,
    private val catchAt: Double,
    private val catchWidth: Double,
) {
    private val catchDepth = slowDepth * slowWidth / catchWidth
    private val root2pi = sqrt(2.0 * PI)

    fun speed(t: Double): Double {
        val a = (t - slowAt) / slowWidth
        val b = (t - catchAt) / catchWidth
        return 1.0 - slowDepth * exp(-0.5 * a * a) + catchDepth * exp(-0.5 * b * b)
    }

    fun action(t: Double): Double =
        t - slowDepth * slowWidth * root2pi * phi((t - slowAt) / slowWidth) + catchDepth * catchWidth * root2pi * phi((t - catchAt) / catchWidth)

    /** The scene time when the action reaches a (bisection: action is increasing). */
    fun realOf(a: Double): Double {
        var lo = a - 3.0
        var hi = a + 3.0
        repeat(60) {
            val mid = (lo + hi) / 2.0
            if (action(mid) < a) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }

    companion object {
        /** Normal CDF (Abramowitz-Stegun 7.1.26 for erf, error below 1.5e-7). */
        fun phi(x: Double): Double {
            val z = abs(x) / sqrt(2.0)
            val t = 1.0 / (1.0 + 0.3275911 * z)
            val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-z * z)
            return if (x >= 0) 0.5 * (1.0 + y) else 0.5 * (1.0 - y)
        }
    }
}

/**
 * A scene's script, without Minecraft (tested off-game): its shots, the action's time, the
 * actor, the look and the cues. Times are the scene's (written for 20 s) unless named a
 * (the action's, with slow motion).
 */
interface SceneScript {
    val director: CameraDirector
    val cues: List<Cue>

    /** The action's time at scene time t. */
    fun action(t: Double): Double

    fun actor(a: Double, out: ActorKey): ActorKey

    fun grade(t: Double, out: Grade): Grade

    /** From 0 (the player's own camera) to 1 (the scene's). */
    fun cameraWeight(t: Double): Double

    /** Black bars, from 0 to 1. */
    fun letterbox(t: Double): Double

    /** The title's opacity. */
    fun title(t: Double): Double

    /** Camera shake, from 0 (none) to about 1 (the strongest). */
    fun shake(a: Double): Double

    /** The flash before the accessibility limit, from 0 to 1. */
    fun flash(a: Double): Double

    /** Whether post effects show (fades in and out with the scene). */
    fun presence(t: Double): Double = CameraMath.smooth(t / 0.8) * (1.0 - CameraMath.smooth((t - 18.5) / 1.5))

    /** The scene's own envelopes at action time a, each from 0 to 1 (checked off-game). */
    fun envelopes(a: Double): DoubleArray = DoubleArray(0)

    /** The shot's name, for the debug overlay. */
    fun shotName(t: Double): String = director.shotAt(t).name
}
