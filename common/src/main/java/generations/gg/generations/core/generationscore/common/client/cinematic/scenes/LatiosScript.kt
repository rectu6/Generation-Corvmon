package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraDirector
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.decay
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutSine
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeOutCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.lerp
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.orbit
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.phase
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.pulse
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smooth
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smoother
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.window
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.Shot
import org.joml.Vector3d
import kotlin.math.PI
import kotlin.math.sin

/**
 * Latios — the Azure Guardian (EON-PLAN.md § 6), 23 s, pure (tested off-game). Its frame: the
 * player's feet, +z where they looked when the summoning ended.
 *
 * 0–5 s: the Enigma Stone glows blue, a rumble in the sky, a beam crosses it, a blue silhouette
 * passes far away at an extraordinary speed (its boom late, by the distance).
 * 5–12 s: three passes of the real Latios (low over the trees, overhead, sideways near the
 * camera), blue trails, the air bending, the camera half following; he comes back.
 * 12–18 s: still over the player, he spreads out, blue and white energy gathers, three psychic
 * rings turn, a great pulse into the sky (no harm), his cry.
 * 18–23 s: down at full speed, a brutal stop near the player, a shock wave of light along the
 * ground, blue motes falling; the camera given back.
 */
class LatiosScript(landing: Vector3d, val test: Boolean) : SceneScript {
    val landing = Vector3d(landing)

    private val slow = SlowMotion(P3_NEAR, 0.4, 0.55, P3_NEAR + 1.6, 0.6)

    override fun action(t: Double): Double = slow.action(t)

    // ---------------------------------------------------------------- the paths

    val stone = Vector3d(0.0, 1.25, 0.85)

    /** The beam across the sky: from where to where (its head runs from BEAM to BEAM_END). */
    val beamFrom = Vector3d(-150.0, 48.0, 95.0)
    val beamTo = Vector3d(150.0, 42.0, 75.0)

    private val farTrack = TimedTrack(doubleArrayOf(FAR, FAR_MID, FAR_END), arrayOf(Vector3d(-130.0, 42.0, 96.0), Vector3d(0.0, 40.0, 88.0), Vector3d(130.0, 38.0, 84.0)))
    private val pass1 = TimedTrack(doubleArrayOf(P1, P1_NEAR, P1_END), arrayOf(Vector3d(-55.0, 6.0, 26.0), Vector3d(0.0, 4.0, 17.0), Vector3d(55.0, 7.0, 22.0)))
    private val pass2 = TimedTrack(doubleArrayOf(P2, P2_NEAR, P2_END), arrayOf(Vector3d(0.0, 30.0, 70.0), Vector3d(0.0, 6.5, 2.0), Vector3d(0.0, 24.0, -60.0)))
    private val pass3 = TimedTrack(
        doubleArrayOf(P3, P3_NEAR, P3_END, 11.4, 11.9, HOVER),
        arrayOf(Vector3d(-50.0, 2.8, 4.0), Vector3d(0.0, 2.6, 5.0), Vector3d(50.0, 3.5, 7.0), Vector3d(40.0, 14.0, 30.0), Vector3d(12.0, 10.0, 14.0), Vector3d(0.0, 7.5, 7.0)),
        endStill = true,
    )

    val hover = Vector3d(0.0, 7.5, 7.0)
    /** Where the brutal stop happens, near the player. */
    val stop: Vector3d
    private val settleTrack: TimedTrack

    init {
        stop = Vector3d(landing.x * 0.9, landing.y + 1.3, landing.z + 0.6)
        val over = Vector3d(landing.x, landing.y + 0.5, landing.z - 0.15)
        settleTrack = TimedTrack(doubleArrayOf(BRAKE, 19.7, LANDING), arrayOf(Vector3d(stop), over, Vector3d(landing)), startStill = true, endStill = true)
    }

    private val tmp = Vector3d()

    /** Whether the actor is drawn at a (hidden between the passes, off-screen). */
    fun shown(a: Double): Boolean = (a >= FAR && a < FAR_END) || (a >= P1 && a < P1_END) || (a >= P2 && a < P2_END) || a >= P3

    /** Latios's feet at action time a. */
    fun latiosPos(a: Double, out: Vector3d): Vector3d = when {
        a < P1 -> farTrack.at(a, out)
        a < P2 -> pass1.at(a, out)
        a < P3 -> pass2.at(a, out)
        a < HOVER -> pass3.at(a, out)
        a < DIVE -> out.set(hover.x + 0.08 * sin(a * 0.8), hover.y + 0.12 * sin(a * 1.9), hover.z)
        a < BRAKE -> {
            // Down at full speed to the stop, faster and faster (the stop is brutal).
            val u = easeInCubic(phase(a, DIVE, BRAKE))
            out.set(lerp(hover.x, stop.x, u), lerp(hover.y, stop.y, u), lerp(hover.z, stop.z, u))
        }
        else -> settleTrack.at(a, out)
    }

    fun centre(a: Double, out: Vector3d): Vector3d = latiosPos(a, out).add(0.0, BODY, 0.0)

    override fun actor(a: Double, out: ActorKey): ActorKey {
        latiosPos(a, out.pos)
        out.scale = 1.0
        out.pitch = 0.0
        out.roll = 0.0
        val face = yawToward(-out.pos.x, -out.pos.z)
        if (!shown(a)) {
            out.pose = ActorPose.HIDDEN
            out.yaw = face
            return out
        }
        when {
            a < HOVER -> {
                out.pose = ActorPose.FLY
                attitude(::latiosPos, a, out, face, 0.18, 45.0)
                val brake = smooth(phase(a, 11.9, HOVER))
                var d = (face - out.yaw) % 360.0
                if (d > 180.0) d -= 360.0
                if (d < -180.0) d += 360.0
                out.yaw += d * brake
                out.pitch *= 1.0 - brake
                out.roll *= 1.0 - brake
                if (brake > 0.6) out.pose = ActorPose.HOVER
            }
            a < DIVE -> {
                out.pose = ActorPose.HOVER
                out.yaw = face
                out.roll = 4.0 * sin(a * 1.1)
            }
            a < BRAKE -> {
                out.pose = ActorPose.FLY
                out.yaw = face
                out.pitch = 35.0 * smooth(phase(a, DIVE, DIVE + 0.3))
            }
            else -> {
                out.pose = ActorPose.HOVER
                out.yaw = face
                // The stop: his body thrown back, then still.
                out.pitch = -22.0 * decay(a, BRAKE, 0.35)
            }
        }
        if (test && a >= VANISH) {
            out.scale = 1.0 - easeInCubic(phase(a, VANISH, VANISH_END))
            if (a >= VANISH_END) out.pose = ActorPose.HIDDEN
        }
        return out
    }

    /** The beam's head at a, from beamFrom to beamTo. */
    fun beamHead(a: Double, out: Vector3d): Vector3d {
        val u = easeInOutSine(phase(a, BEAM, BEAM_END))
        return out.set(lerp(beamFrom.x, beamTo.x, u), lerp(beamFrom.y, beamTo.y, u), lerp(beamFrom.z, beamTo.z, u))
    }

    // ---------------------------------------------------------------- the camera

    private val m = Vector3d()
    private val n = Vector3d()

    /** Half following a point: the target between a fixed point and him (the camera cannot keep up). */
    private fun halfFollow(a: Double, fixed: Vector3d, share: Double, out: Vector3d): Vector3d {
        centre(a, n)
        return out.set(lerp(fixed.x, n.x, share), lerp(fixed.y, n.y, share), lerp(fixed.z, n.z, share))
    }

    private val skyPoint = Vector3d(0.0, 30.0, 80.0)
    private val p1Point = Vector3d(0.0, 4.5, 17.0)
    private val p2Point = Vector3d(0.0, 11.0, 10.0)
    private val p3Point = Vector3d(0.0, 2.8, 5.0)

    private val stoneShot = Shot("stone", 0.0, 0.0) { t, motion, out ->
        val u = easeInOutSine(phase(t, 0.0, 1.8))
        out.pos.set(lerp(-0.45, -0.9, u), lerp(1.6, 1.4, u), lerp(-0.15, 0.2, u))
        out.target.set(stone.x, stone.y + 0.05, stone.z)
        out.fov = lerp(56.0, 42.0, u)
        out.roll = -1.5 * u * motion.roll
    }

    /** Low behind the player, looking up: the beam, then the silhouette far away. */
    private val skyShot = Shot("sky", 1.8, 0.9) { t, _, out ->
        val a = action(t)
        val u = easeInOutSine(phase(t, 1.8, P1))
        out.pos.set(lerp(-0.8, -1.2, u), lerp(1.2, 1.0, u), lerp(-2.0, -2.4, u))
        if (a < FAR) beamHead(a, m).let { out.target.set(lerp(skyPoint.x, it.x, 0.35), lerp(skyPoint.y, it.y, 0.35), lerp(skyPoint.z, it.z, 0.35)) }
        else halfFollow(a.coerceAtMost(FAR_END), skyPoint, 0.3, out.target)
        out.fov = 64.0
        out.roll = 0.0
    }

    /** Beside the player: the first pass, low over the trees, the camera turning after him. */
    private val pass1Shot = Shot("pass1", P1, 0.5) { t, motion, out ->
        val a = action(t)
        out.pos.set(1.1, 1.55, -0.9)
        halfFollow(a.coerceIn(P1, P1_END), p1Point, 0.55, out.target)
        out.fov = 60.0 - 5.0 * pulse(t, P1_NEAR, 0.05, 0.4) * motion.punch
        out.roll = 0.0
    }

    /** Low, looking up: the second, over the player's head. */
    private val pass2Shot = Shot("pass2", P1_END + 0.2, 0.4) { t, motion, out ->
        val a = action(t)
        out.pos.set(0.9, 0.95, -1.6)
        halfFollow(a.coerceIn(P2, P2_END), p2Point, 0.6, out.target)
        out.fov = 70.0 - 6.0 * pulse(t, P2_NEAR, 0.05, 0.4) * motion.punch
        out.roll = 2.0 * motion.roll
    }

    /** Low beside the player: the third, sideways and close (slowed down). */
    private val pass3Shot = Shot("pass3", P2_END + 0.2, 0.3) { t, motion, out ->
        val a = action(t)
        out.pos.set(-1.0, 1.3, 1.2)
        halfFollow(a.coerceIn(P3, P3_END), p3Point, 0.5, out.target)
        out.fov = 58.0 - 6.0 * pulse(t, P3_NEAR, 0.05, 0.5) * motion.punch
        out.roll = 0.0
    }

    /** Wide behind the player: he comes back. */
    private val arriveShot = Shot("arrive", P3_END + 0.2, 0.7) { t, _, out ->
        val u = easeInOutSine(phase(t, P3_END + 0.2, HOVER + 0.2))
        out.pos.set(lerp(-2.6, -2.0, u), lerp(2.0, 2.4, u), lerp(-3.6, -3.0, u))
        centre(action(t), m)
        out.target.set(m.x, m.y, m.z)
        out.fov = lerp(62.0, 54.0, u)
        out.roll = 0.0
    }

    /** An orbit round the Guardian, the energy gathering, the rings. */
    private val guardianShot = Shot("guardian", HOVER + 0.2, 0.8) { t, motion, out ->
        val u = easeInOutSine(phase(t, HOVER + 0.2, 15.6))
        orbit(hover.x, hover.z, lerp(5.6, 4.8, u), 200.0 + 80.0 * u * motion.orbit, lerp(4.6, 5.6, u), out.pos)
        centre(action(t), m)
        out.target.set(m.x, m.y, m.z)
        out.fov = lerp(56.0, 48.0, u)
        out.roll = 2.0 * sin(u * PI) * motion.roll
    }

    /** From below, near the player: the pulse into the sky, his cry. */
    private val pulseShot = Shot("pulse", 15.6, 0.6) { t, motion, out ->
        val u = easeInOutSine(phase(t, 15.6, DIVE))
        out.pos.set(lerp(1.4, 1.2, u), lerp(1.2, 1.1, u), lerp(1.8, 2.2, u))
        centre(action(t), m)
        out.target.set(m.x, m.y + lerp(0.5, 2.0, smooth(phase(t, PULSE, PULSE + 0.6))), m.z)
        out.fov = lerp(62.0, 66.0, u) + 7.0 * pulse(t, PULSE, 0.04, 0.5) * motion.punch
        out.roll = 0.0
    }

    /** Beside the player: the dive and the brutal stop. */
    private val brakeShot = Shot("brake", DIVE, 0.5) { t, motion, out ->
        val u = easeInOutSine(phase(t, DIVE, 20.4))
        out.pos.set(lerp(2.6, 2.3, u), lerp(1.5, 1.4, u), lerp(1.0, 1.3, u))
        centre(action(t), m)
        out.target.set(m.x, m.y, m.z)
        out.fov = 56.0 + 6.0 * pulse(t, BRAKE, 0.03, 0.4) * motion.punch
        out.roll = 0.0
    }

    private val finalShot = Shot("final", 20.4, 0.8) { t, _, out ->
        val u = easeInOutSine(phase(t, 20.4, 22.4))
        out.pos.set(lerp(1.9, 1.5, u), lerp(1.8, 1.6, u), lerp(0.3, 0.8, u))
        centre(action(t), m)
        out.target.set(m.x, m.y, m.z)
        out.fov = lerp(54.0, 48.0, u)
        out.roll = 0.0
    }

    override val director = CameraDirector(listOf(stoneShot, skyShot, pass1Shot, pass2Shot, pass3Shot, arriveShot, guardianShot, pulseShot, brakeShot, finalShot))

    // ---------------------------------------------------------------- the look

    override fun cameraWeight(t: Double): Double = smoother(t / 1.0) * (1.0 - smoother((t - 21.6) / 1.4))

    override fun letterbox(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 21.8) / 1.2))

    override fun title(t: Double): Double = window(t, 16.5, 17.0, 18.0, 18.6)

    override fun presence(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 21.8) / 1.2))

    override fun shake(a: Double): Double =
        0.18 * decay(a, P1_NEAR, 0.35) + 0.3 * decay(a, P2_NEAR, 0.4) + 0.25 * decay(a, P3_NEAR, 0.4) + 0.35 * decay(a, PULSE, 0.5) + 0.5 * decay(a, BRAKE, 0.35)

    override fun flash(a: Double): Double = (0.3 * pulse(a, P2_NEAR, 0.03, 0.25) + 0.55 * pulse(a, PULSE, 0.03, 0.35) + 0.35 * pulse(a, BRAKE, 0.02, 0.25)).coerceIn(0.0, 1.0)

    /** Cold blues, brighter at the pulse. */
    override fun grade(t: Double, out: Grade): Grade {
        val e = energy(t)
        val p = pulseWave(t)
        val soft = smooth(t / 1.5) * (1.0 - smooth((t - 21.5) / 1.5))
        out.r = 1.0 - 0.04 * soft
        out.g = 1.0 + 0.01 * soft
        out.b = 1.0 + 0.08 * soft + 0.05 * e
        out.exposure = 1.0 + 0.08 * e + 0.12 * p
        out.saturation = 1.0 + 0.1 * soft
        out.contrast = 1.0 + 0.07 * soft
        out.vignette = 0.22 * soft
        return out
    }

    // ---------------------------------------------------------------- envelopes

    fun stoneGlow(a: Double): Double = window(a, 0.0, 0.5, 2.2, 3.2)

    fun beam(a: Double): Double = window(a, BEAM, BEAM + 0.2, BEAM_END, BEAM_END + 0.8)

    fun far(a: Double): Double = window(a, FAR, FAR + 0.1, FAR_END - 0.1, FAR_END)

    /** The trails and the bent air of the passes. */
    fun passes(a: Double): Double =
        window(a, P1, P1 + 0.2, P1_END - 0.2, P1_END) + window(a, P2, P2 + 0.2, P2_END - 0.2, P2_END) + window(a, P3, P3 + 0.2, 11.6, HOVER)

    fun energy(a: Double): Double = window(a, DEPLOY, DEPLOY + 0.6, PULSE - 0.2, PULSE + 0.3)

    fun rings(a: Double): Double = window(a, RINGS, RINGS + 0.7, 17.2, DIVE)

    fun pulseWave(a: Double): Double = if (a < PULSE) 0.0 else 1.0 - smooth((a - PULSE) / 1.6)

    fun shock(a: Double): Double = if (a < BRAKE) 0.0 else 1.0 - smooth((a - BRAKE) / 1.2)

    fun fall(a: Double): Double = window(a, BRAKE, BRAKE + 0.3, 21.0, 22.2)

    override fun envelopes(a: Double): DoubleArray = doubleArrayOf(stoneGlow(a), beam(a), far(a), passes(a).coerceAtMost(1.0), energy(a), rings(a), pulseWave(a), shock(a), fall(a))

    // ---------------------------------------------------------------- cues

    override val cues: List<Cue> = buildList {
        add(Cue(0.2, Cue.Kind.SOUND, "minecraft:block.beacon.activate", "origin", 0.0, 1.2, 0.85, volume = 1.2f, pitch = 1.2f))
        add(Cue(0.5, Cue.Kind.SOUND, "minecraft:block.amethyst_block.resonate", "origin", 0.0, 1.2, 0.85, volume = 1.4f, pitch = 0.8f))
        add(Cue(1.2, Cue.Kind.SOUND, "minecraft:entity.lightning_bolt.thunder", "origin", 0.0, 30.0, 60.0, volume = 0.5f, pitch = 0.5f))
        add(Cue(BEAM, Cue.Kind.EVENT, "beam"))
        add(Cue(BEAM, Cue.Kind.SOUND, "minecraft:block.beacon.power_select", "origin", 0.0, 30.0, 40.0, volume = 2.0f, pitch = 0.6f))
        add(Cue(FAR, Cue.Kind.EVENT, "far"))
        add(Cue(FAR_MID, Cue.Kind.EVENT, "boom"))
        add(Cue(5.5, Cue.Kind.SOUND, "minecraft:entity.phantom.swoop", "actor", volume = 2.4f, pitch = 0.8f))
        add(Cue(P1_NEAR, Cue.Kind.EVENT, "p1"))
        add(Cue(P1_NEAR, Cue.Kind.SOUND, "minecraft:item.trident.riptide_3", "actor", volume = 2.0f, pitch = 0.9f))
        add(Cue(7.7, Cue.Kind.SOUND, "minecraft:entity.phantom.swoop", "actor", volume = 2.4f, pitch = 0.75f))
        add(Cue(P2_NEAR, Cue.Kind.EVENT, "p2"))
        add(Cue(P2_NEAR, Cue.Kind.SOUND, "minecraft:entity.firework_rocket.large_blast", "actor", volume = 2.5f, pitch = 0.6f))
        add(Cue(9.7, Cue.Kind.SOUND, "minecraft:entity.phantom.swoop", "actor", volume = 2.4f, pitch = 0.85f))
        add(Cue(P3_NEAR, Cue.Kind.EVENT, "p3"))
        add(Cue(P3_NEAR, Cue.Kind.SOUND, "minecraft:item.trident.riptide_3", "actor", volume = 2.2f, pitch = 0.8f))
        add(Cue(10.3, Cue.Kind.CRY, "cobblemon:pokemon.latios.cry", "actor", volume = 1.4f, pitch = 1.05f))
        add(Cue(12.2, Cue.Kind.SOUND, "minecraft:block.beacon.power_select", "actor", volume = 1.6f, pitch = 1.0f))
        add(Cue(DEPLOY, Cue.Kind.ANIMATE, "glad"))
        add(Cue(DEPLOY, Cue.Kind.EVENT, "deploy"))
        add(Cue(RINGS, Cue.Kind.EVENT, "rings"))
        add(Cue(RINGS, Cue.Kind.SOUND, "minecraft:block.conduit.activate", "actor", volume = 1.6f, pitch = 1.2f))
        add(Cue(14.5, Cue.Kind.SOUND, "minecraft:block.beacon.ambient", "actor", volume = 1.8f, pitch = 1.4f))
        add(Cue(15.6, Cue.Kind.ANIMATE, "special"))
        add(Cue(PULSE, Cue.Kind.CRY, "cobblemon:pokemon.latios.cry", "actor", volume = 2.6f))
        add(Cue(PULSE, Cue.Kind.EVENT, "pulse"))
        add(Cue(PULSE, Cue.Kind.SOUND, "minecraft:block.beacon.activate", "actor", volume = 2.0f, pitch = 0.8f))
        add(Cue(PULSE, Cue.Kind.SOUND, "minecraft:entity.firework_rocket.large_blast", "actor", volume = 2.5f, pitch = 0.7f))
        add(Cue(DIVE, Cue.Kind.SOUND, "minecraft:entity.phantom.swoop", "actor", volume = 2.2f, pitch = 0.9f))
        add(Cue(BRAKE, Cue.Kind.EVENT, "brake"))
        add(Cue(BRAKE, Cue.Kind.SOUND, "minecraft:entity.generic.explode", "actor", volume = 1.0f, pitch = 1.5f))
        add(Cue(BRAKE, Cue.Kind.SOUND, "minecraft:item.trident.riptide_3", "actor", volume = 2.0f, pitch = 0.7f))
        add(Cue(LANDING, Cue.Kind.EVENT, "landing"))
        add(Cue(LANDING, Cue.Kind.SOUND, "minecraft:block.amethyst_block.chime", "actor", volume = 1.2f, pitch = 1.2f))
        if (test) {
            add(Cue(VANISH - 0.05, Cue.Kind.EVENT, "vanish"))
            add(Cue(VANISH, Cue.Kind.SOUND, "minecraft:entity.enderman.teleport", "actor", volume = 1.0f, pitch = 1.2f))
        }
    }.sortedBy { it.time }

    @Suppress("unused")
    private fun eased(u: Double) = easeOutCubic(u)

    companion object {
        const val SECONDS = 23.0
        const val BEAM = 1.5
        const val BEAM_END = 3.0
        const val FAR = 3.0
        const val FAR_MID = 3.6
        const val FAR_END = 4.2
        const val P1 = 5.0
        const val P1_NEAR = 5.9
        const val P1_END = 6.8
        const val P2 = 7.2
        const val P2_NEAR = 8.0
        const val P2_END = 8.8
        const val P3 = 9.2
        const val P3_NEAR = 10.0
        const val P3_END = 10.8
        const val HOVER = 12.4
        const val DEPLOY = 12.5
        const val RINGS = 13.0
        const val PULSE = 16.0
        const val DIVE = 18.0
        const val BRAKE = 19.0
        const val LANDING = 20.6
        const val VANISH = 22.5
        const val VANISH_END = 22.9

        /** His body's centre over his feet. */
        const val BODY = 0.7
    }
}
