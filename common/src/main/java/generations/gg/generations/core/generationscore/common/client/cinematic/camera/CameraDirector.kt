package generations.gg.generations.core.generationscore.common.client.cinematic.camera

/**
 * A shot from `start` (scene seconds) to the next shot's start: its camera at each moment
 * (in the scene's local frame). blendIn: how long it fades in from the previous shot
 * (positions and fields of view mixed, rotations slerped); 0 is a cut.
 */
class Shot(
    val name: String,
    val start: Double,
    val blendIn: Double,
    val pose: (t: Double, motion: CameraMotion, out: CamPose) -> Unit,
)

/**
 * The scene's shots, in order: the camera at each moment, the fades between shots
 * included. Pure (tested off-game).
 */
class CameraDirector(shots: List<Shot>) {
    val shots: List<Shot> = shots.sortedBy { it.start }

    private val poseA = CamPose()
    private val poseB = CamPose()
    private val frameB = CamFrame()

    init {
        require(this.shots.isNotEmpty()) { "a scene has at least one shot" }
    }

    /** Index of the shot playing at t (the first one before it starts). */
    fun indexAt(t: Double): Int {
        var index = 0
        for (i in shots.indices) if (shots[i].start <= t) index = i
        return index
    }

    fun shotAt(t: Double): Shot = shots[indexAt(t)]

    /** The camera at t, into out. */
    fun evaluate(t: Double, motion: CameraMotion, out: CamFrame): CamFrame {
        val i = indexAt(t)
        val shot = shots[i]
        shot.pose(t, motion, poseB)
        val w = if (i == 0 || shot.blendIn <= 0.0) 1.0 else CameraMath.smoother((t - shot.start) / shot.blendIn)
        if (w >= 1.0) return out.set(poseB)
        shots[i - 1].pose(t, motion, poseA)
        out.set(poseA)
        frameB.set(poseB)
        return out.mix(frameB, w)
    }
}
