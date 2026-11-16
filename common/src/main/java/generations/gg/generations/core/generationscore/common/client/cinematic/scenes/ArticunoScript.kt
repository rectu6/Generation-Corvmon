package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CamPose
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraDirector
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.decay
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutSine
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInQuad
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeOutCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeOutQuad
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.lerp
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.orbit
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.phase
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.pulse
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smooth
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smoother
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.window
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.bezier
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMotion
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.Shot
import org.joml.Vector3d
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Articuno — Awakening of the Blizzard (CINEMATIQUES-PLAN.md § 5), 20 s, pure. O is the shrine
 * (local origin, on its ground); `landing` is where Articuno lands (local, sent by the server);
 * test: it vanishes in a last burst of snow instead of leaving its place to the real entity.
 *
 * 0–4 s: the altar wakes (light column, 12 crystals, cold mist, frost ring), travelling back.
 * 4–9 s: the vortex (spiral cones, snow, dark clouds, floating ice), crane up then orbit.
 * 9–11 s: Articuno's silhouette, backlit, at the heart of the vortex.
 * 11–13.5 s: its spiralling descent, wide low orbit; slow motion, then the burst at 13.5 s
 * (40 crystals, shock wave, cold wave on the ground, flash).
 * 15–20 s: the heroic shot, the cry at 15 s, the title, the blizzard clears, landing at 18 s,
 * back to the player's camera from 18.5 s.
 */
class ArticunoScript(landing: Vector3d, val test: Boolean) : SceneScript {
    val landing = Vector3d(landing)

    /** Slow motion around the burst, the time given back before the cry. */
    val slowMotion = SlowMotion(slowAt = 13.70, slowWidth = 0.35, slowDepth = 0.60, catchAt = 14.75, catchWidth = 0.30)

    /** The scene time of the burst (action time EXPLOSION). */
    val explosionReal: Double = slowMotion.realOf(EXPLOSION)

    override fun action(t: Double): Double = slowMotion.action(t)

    // ---------------------------------------------------------------- the actor

    private val tmpA = Vector3d()
    private val tmpB = Vector3d()
    private val bez0 = Vector3d()
    private val bez1 = Vector3d()
    private val bez2 = Vector3d()

    /** Articuno's feet at action time a (whatever its pose, visible or not). */
    fun birdPos(a: Double, out: Vector3d): Vector3d {
        when {
            a < DESCENT -> out.set(0.4 * sin(1.3 * a), 18.0 + 0.35 * sin(2.1 * a), 0.0)
            a < EXPLOSION -> {
                val u = easeInOutSine((a - DESCENT) / (EXPLOSION - DESCENT))
                val y = lerp(18.0 + 0.35 * sin(2.1 * DESCENT), 7.0, u)
                val rho = 3.2 * sin(PI * u)
                val phi = Math.toRadians(240.0 * u)
                // From the hover's sway (u = 0) to the axis.
                val sway = 0.4 * sin(1.3 * DESCENT) * (1.0 - u)
                out.set(rho * sin(phi) + sway, y, rho * Math.cos(phi) - 0.0)
            }
            a < GLIDE -> {
                val settle = smooth((a - EXPLOSION) / (GLIDE - EXPLOSION))
                val bob = 0.15 * sin(3.0 * (a - EXPLOSION)) * (1.0 - smooth((a - (GLIDE - 0.5)) / 0.5)) * smooth((a - EXPLOSION) / 0.4)
                out.set(0.0, lerp(7.0, 6.0, settle) + bob, 0.0)
            }
            a < LANDING -> {
                val u = easeInOutCubic((a - GLIDE) / (LANDING - GLIDE))
                bez0.set(0.0, 6.0, 0.0)
                bez1.set(landing.x * 0.2, 5.9, landing.z * 0.25)
                bez2.set(landing.x, landing.y + 2.0, landing.z - 0.6 * sign(landing.z))
                bezier(bez0, bez1, bez2, landing, u, out)
            }
            else -> out.set(landing)
        }
        return out
    }

    private fun sign(x: Double) = if (x < 0) -1.0 else 1.0

    /** Its head, roughly, for the camera to look at. */
    fun headPos(a: Double, out: Vector3d): Vector3d = birdPos(a, out).add(0.0, HEAD, 0.0)

    private fun headingOf(dx: Double, dz: Double): Double = Math.toDegrees(atan2(-dx, dz))

    override fun actor(a: Double, out: ActorKey): ActorKey {
        out.scale = 1.0
        out.roll = 0.0
        out.pitch = 0.0
        if (a < REVEAL) {
            out.pose = ActorPose.HIDDEN
            return birdPos(a, out.pos).let { out }
        }
        birdPos(a, out.pos)
        when {
            a < DESCENT -> {
                out.pose = ActorPose.FLY
                // Facing the silhouette's camera, below and in front.
                out.yaw = 20.0
                out.pitch = -8.0
            }
            a < EXPLOSION -> {
                out.pose = ActorPose.FLY
                birdPos(a - 0.03, tmpA)
                birdPos(a + 0.03, tmpB)
                val vx = (tmpB.x - tmpA.x) / 0.06
                val vy = (tmpB.y - tmpA.y) / 0.06
                val vz = (tmpB.z - tmpA.z) / 0.06
                val horizontal = sqrt(vx * vx + vz * vz)
                val heading = if (horizontal > 0.3) headingOf(vx, vz) else 0.0
                // The heading a moment later, for the bank of the turn.
                birdPos(a + 0.09, tmpA)
                val heading2 = if (horizontal > 0.3) headingOf(tmpA.x - tmpB.x, tmpA.z - tmpB.z) else heading
                val settle = smooth((a - (EXPLOSION - 0.6)) / 0.6)
                val start = 1.0 - smooth((a - DESCENT) / 0.4)
                out.yaw = lerpAngle(lerpAngle(heading, 0.0, settle), 20.0, start)
                val pitch = Math.toDegrees(atan2(-vy, maxOf(horizontal, 0.5))) * 0.5
                out.pitch = pitch.coerceIn(-10.0, 35.0) * (1.0 - settle)
                out.roll = (-wrap(heading2 - heading) / 0.06 * 0.25).coerceIn(-35.0, 35.0) * (1.0 - settle) * (1.0 - start)
            }
            a < GLIDE -> {
                out.pose = ActorPose.HOVER
                out.yaw = 0.0
                out.pitch = -5.0 * smooth((a - EXPLOSION) / 0.5)
            }
            a < LANDING -> {
                val flare = a >= LANDING - 0.7
                out.pose = if (flare) ActorPose.HOVER else ActorPose.FLY
                val toward = headingOf(landing.x, landing.z)
                val far = landing.x * landing.x + landing.z * landing.z > 1.0
                out.yaw = lerpAngle(if (far) toward else 0.0, 0.0, smooth((a - (LANDING - 0.9)) / 0.6))
                out.pitch = if (flare) -10.0 * window(a, LANDING - 0.7, LANDING - 0.5, LANDING - 0.2, LANDING) else 8.0 * window(a, GLIDE, GLIDE + 0.4, LANDING - 1.1, LANDING - 0.7)
            }
            else -> {
                out.pose = ActorPose.STAND
                out.yaw = 0.0
                if (test) {
                    if (a >= VANISH_END) out.pose = ActorPose.HIDDEN
                    out.scale = 1.0 - easeInCubic(phase(a, VANISH, VANISH_END))
                }
            }
        }
        return out
    }

    private fun wrap(deg: Double): Double {
        var d = deg % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    private fun lerpAngle(a: Double, b: Double, t: Double): Double = a + wrap(b - a) * t

    // ---------------------------------------------------------------- the camera

    private val bird = Vector3d()

    /** The first shot starts close to the altar, low: from 2.5 to 9 m, 20° of orbit, FOV 50 to 60. */
    private val awakening = Shot("awakening", 0.0, 0.0) { t, m, out ->
        val u = easeInOutSine(t / 4.6)
        orbit(0.0, 0.0, lerp(2.5, 9.0, u), 15.0 + 20.0 * u * m.orbit, lerp(0.55, 2.0, u), out.pos)
        out.target.set(0.0, lerp(1.4, 4.6, easeInQuad(t / 4.6)), 0.0)
        out.fov = lerp(50.0, 60.0, u)
        out.roll = -2.0 * u * m.roll
    }

    /** Crane up to the spiral (18 m), then an accelerating orbit around it (14 m, 0 to 90°). */
    private val vortex = Shot("vortex", 4.0, 0.6) { t, m, out ->
        if (t < 6.4) {
            val u = easeInOutSine((t - 4.0) / 2.4)
            orbit(0.0, 0.0, lerp(9.0, 14.0, u), 15.0 + 20.0 * m.orbit + 5.0 * u, lerp(2.0, 17.5, u), out.pos)
            out.target.set(0.0, lerp(4.6, 14.0, u), 0.0)
            out.fov = lerp(60.0, 68.0, u)
            out.roll = 0.0
        } else {
            val v = phase(t, 6.4, 9.0)
            orbit(0.0, 0.0, 14.0, 20.0 + 20.0 * m.orbit + 90.0 * m.orbit * easeInQuad(v), lerp(17.5, 16.5, v), out.pos)
            out.target.set(0.0, lerp(14.0, 15.5, v), 0.0)
            out.fov = lerp(68.0, 72.0, v)
            out.roll = -8.0 * sin(PI * v / 2.0) * m.roll
        }
    }

    /** Articuno's silhouette, seen from below, backlit; a slow push. */
    private val silhouette = Shot("silhouette", REVEAL, 0.0) { t, m, out ->
        val u = easeInOutSine(phase(t, REVEAL, DESCENT))
        out.pos.set(lerp(-4.5, -3.4, u), lerp(6.0, 7.0, u), lerp(12.5, 10.0, u))
        birdPos(action(t), bird)
        out.target.set(bird.x, bird.y + 0.6, bird.z)
        out.fov = lerp(40.0, 35.0, u)
        out.roll = 3.0 * m.roll
    }

    /** The descent: a wide orbit of 120°, low. */
    private val descent = Shot("descent", DESCENT, 0.6) { t, m, out ->
        val u = easeInOutSine(phase(t, DESCENT, explosionReal - 0.15))
        orbit(0.0, 0.0, lerp(12.5, 9.5, u), -55.0 + 120.0 * u * m.orbit, lerp(2.6, 1.5, u), out.pos)
        headPos(action(t), bird)
        out.target.set(bird.x, bird.y - HEAD + 0.5, bird.z)
        out.fov = lerp(48.0, 56.0, u)
        out.roll = lerp(4.0, -3.0, u) * m.roll
    }

    /** After the burst: pulled back, with the FOV's punch. */
    private val aftermath = Shot("burst", explosionReal - 0.15, 0.35) { t, m, out ->
        val u = easeOutCubic(phase(t, explosionReal - 0.15, CRY_SHOT))
        orbit(0.0, 0.0, lerp(9.5, 13.0, u), -55.0 + 120.0 * m.orbit + 10.0 * u * m.orbit, lerp(1.5, 2.4, u), out.pos)
        val a = action(t)
        birdPos(a, bird)
        out.target.set(bird.x, bird.y + 0.4, bird.z)
        out.fov = 56.0 + 12.0 * pulse(a, EXPLOSION, 0.06, 0.45) * m.punch
        out.roll = 0.0
    }

    /** The heroic shot: low, in front, a slow push toward its head (FOV 55 to 45). */
    private val heroic = Shot("heroic", CRY_SHOT, 0.0) { t, m, out ->
        val u = easeInOutSine(phase(t, CRY_SHOT, 18.5))
        out.pos.set(landing.x + lerp(2.4, 1.5, u), landing.y + lerp(0.6, 0.8, u), landing.z + lerp(5.8, 4.2, u))
        headPos(action(t), bird)
        out.target.set(bird)
        out.fov = lerp(55.0, 45.0, u) - 3.0 * pulse(action(t), CRY, 0.05, 0.6) * m.punch
        out.roll = 0.0
    }

    override val director = CameraDirector(listOf(awakening, vortex, silhouette, descent, aftermath, heroic))

    // ---------------------------------------------------------------- the look

    override fun cameraWeight(t: Double): Double = smoother(t / 1.0) * (1.0 - smoother((t - 18.5) / 1.5))

    override fun letterbox(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 18.6) / 1.2))

    override fun title(t: Double): Double = window(t, 15.5, 16.0, 18.0, 18.5)

    override fun shake(a: Double): Double =
        0.05 * vortex(a) + 1.0 * decay(a, EXPLOSION, 0.5) + 0.25 * decay(a, REVEAL, 0.3) + 0.15 * decay(a, CRY, 0.4) + 0.1 * decay(a, LANDING, 0.25)

    override fun flash(a: Double): Double = (0.85 * pulse(a, EXPLOSION, 0.04, 0.22) + 0.35 * pulse(a, REVEAL, 0.05, 0.25)).coerceIn(0.0, 1.0)

    /** Cold, then darker in the vortex, back to normal as the blizzard clears. */
    override fun grade(t: Double, out: Grade): Grade {
        val clear = smooth((t - 15.5) / 2.5)
        val cool = (0.55 * smooth(t / 3.0) + 0.3 * window(t, 4.0, 6.0, 13.5, 15.0)) * (1.0 - 0.8 * clear)
        out.r = 1.0 - 0.16 * cool
        out.g = 1.0 - 0.04 * cool
        out.b = 1.0 + 0.10 * cool
        out.exposure = 1.0 - (0.15 * smooth(t / 3.0) + 0.10 * window(t, 4.0, 6.0, 13.2, 14.5)) * (1.0 - clear)
        out.saturation = 1.0 - 0.25 * cool
        out.contrast = 1.0 + 0.12 * cool
        out.vignette = 0.35 + 0.25 * window(t, 4.0, 6.0, 15.0, 18.0)
        return out
    }

    // ---------------------------------------------------------------- the effects' envelopes (action time)

    /** The light column: its height (m) and brightness. */
    fun columnHeight(a: Double): Double = 30.0 * easeOutCubic(a / 1.4)

    fun columnLight(a: Double): Double =
        (0.85 * smooth(a / 0.6) + 0.35 * window(a, 3.5, 5.0, 9.0, 10.0)) * (1.0 - smooth((a - EXPLOSION) / 0.9))

    /** Crystal i (0 to 11) of the ring: how far it rose (0 to 1). */
    fun crystalRise(i: Int, a: Double): Double = easeOutCubic((a - (0.3 + 0.15 * i)) / 1.1)

    fun crystalAngle(i: Int, a: Double): Double {
        val t = minOf(a, EXPLOSION)
        return i * 30.0 + 15.0 * t + 3.0 * t * t
    }

    fun crystalRadius(a: Double): Double = 2.5 + 0.6 * smooth((a - 9.0) / 4.0)

    fun crystalLift(i: Int, a: Double): Double = 0.25 * sin(1.7 * a + i) * smooth((a - 2.0) / 2.0) + 1.8 * smooth((a - 9.0) / 4.0)

    fun mistRadius(a: Double): Double = 10.0 * easeOutCubic(a / 4.0) + 12.0 * easeOutCubic((a - EXPLOSION) / 1.5)

    fun mistOpacity(a: Double): Double = 0.55 * smooth(a / 1.0) * (1.0 - smooth((a - 16.0) / 2.0)) * (1.0 - 0.5 * smooth((a - EXPLOSION) / 1.5))

    fun frostRadius(a: Double): Double = lerp(0.5, 6.0, easeOutCubic(a / 3.0))

    fun frostOpacity(a: Double): Double = 0.8 * smooth(a / 0.5) * (1.0 - smooth((a - 16.0) / 3.0))

    /** The cold wave on the ground after the burst: radius (m) and opacity. */
    fun coldWaveRadius(a: Double): Double = 25.0 * easeOutCubic((a - EXPLOSION) / 1.2)

    fun coldWaveOpacity(a: Double): Double = if (a < EXPLOSION) 0.0 else 1.0 - smooth((a - EXPLOSION) / 1.4)

    /** The frost left on the ground. */
    fun frostDecalRadius(a: Double): Double = maxOf(frostRadius(a), coldWaveRadius(a))

    fun frostDecalOpacity(a: Double): Double = 0.45 * smooth(a / 0.8) * (1.0 - smooth((a - 16.0) / 3.0))

    /** The vortex's strength. */
    fun vortex(a: Double): Double = smooth((a - 3.5) / 2.5) * (1.0 - 0.3 * smooth((a - EXPLOSION) / 0.5)) * (1.0 - smooth((a - 16.0) / 2.0))

    fun cloudRadius(a: Double): Double = 32.0 * easeOutCubic((a - 4.0) / 3.0) + 13.0 * smooth((a - 16.0) / 2.0)

    fun cloudOpacity(a: Double): Double = 0.78 * smooth((a - 4.0) / 2.0) * (1.0 - smooth((a - 16.0) / 2.2))

    /** The 30 floating ice shards: how present (scale-in), before they are flung. */
    fun shardsPresence(a: Double): Double = smooth((a - 5.0) / 0.8)

    /** The light behind Articuno's silhouette. */
    fun backlight(a: Double): Double = window(a, 8.8, 9.2, 10.6, 11.6)

    /** The trails of its descent. */
    fun trails(a: Double): Double = window(a, DESCENT, DESCENT + 0.3, EXPLOSION - 0.3, EXPLOSION + 0.1)

    /** Ambient snowfall (outside the vortex), from the start to the clear sky. */
    fun snowfall(a: Double): Double = (0.35 + 0.65 * smooth((a - 3.0) / 3.0)) * (1.0 - smooth((a - 16.0) / 2.0))

    // ---------------------------------------------------------------- the cues

    override val cues: List<Cue> = buildList {
        if (test) add(Cue(0.0, Cue.Kind.SOUND, "generations_core:generations_core.item.lugia_shrine_song", volume = 1.0f))
        add(Cue(0.2, Cue.Kind.SOUND, "minecraft:block.beacon.activate", dy = 1.0, volume = 1.4f, pitch = 1.6f))
        for (i in 0 until 12) {
            val angle = Math.toRadians(i * 30.0 + 15.0 * (0.3 + 0.15 * i))
            add(Cue(0.3 + 0.15 * i, Cue.Kind.SOUND, "minecraft:block.amethyst_cluster.place", dx = 2.5 * sin(angle), dz = 2.5 * Math.cos(angle), volume = 1.0f, pitch = (0.8 + 0.5 * ((i * 7) % 12) / 11.0).toFloat()))
        }
        add(Cue(1.5, Cue.Kind.SOUND, "minecraft:block.amethyst_block.chime", dy = 1.5, volume = 1.2f, pitch = 1.2f))
        add(Cue(2.7, Cue.Kind.SOUND, "minecraft:block.amethyst_block.chime", dy = 1.5, volume = 1.2f, pitch = 1.5f))
        add(Cue(3.6, Cue.Kind.SOUND, "minecraft:block.amethyst_block.chime", dy = 1.5, volume = 1.2f, pitch = 1.35f))
        add(Cue(4.0, Cue.Kind.SOUND, "minecraft:entity.breeze.wind_burst", dy = 4.0, volume = 1.6f, pitch = 0.6f))
        add(Cue(5.0, Cue.Kind.SOUND, "minecraft:entity.breeze.whirl", dy = 8.0, volume = 1.4f, pitch = 0.5f))
        add(Cue(7.0, Cue.Kind.SOUND, "minecraft:item.trident.riptide_3", dy = 12.0, volume = 1.5f, pitch = 0.7f))
        add(Cue(8.6, Cue.Kind.SOUND, "minecraft:block.beacon.power_select", dy = 16.0, volume = 1.6f, pitch = 0.5f))
        add(Cue(REVEAL, Cue.Kind.EVENT, "reveal"))
        add(Cue(REVEAL, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 2.0f, pitch = 1.2f))
        add(Cue(REVEAL, Cue.Kind.SOUND, "minecraft:block.amethyst_block.resonate", "actor", volume = 1.6f, pitch = 0.7f))
        add(Cue(REVEAL + 0.05, Cue.Kind.SOUND, "minecraft:block.glass.break", "actor", volume = 1.2f, pitch = 1.6f))
        var flap = DESCENT
        while (flap < EXPLOSION - 0.2) {
            add(Cue(flap, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 1.6f, pitch = 1.25f))
            flap += 0.55
        }
        add(Cue(EXPLOSION - 0.4, Cue.Kind.SOUND, "minecraft:block.beacon.deactivate", "actor", volume = 1.4f, pitch = 1.8f))
        add(Cue(EXPLOSION, Cue.Kind.EVENT, "burst"))
        add(Cue(EXPLOSION, Cue.Kind.SOUND, "minecraft:entity.warden.sonic_boom", "actor", volume = 2.5f, pitch = 1.25f))
        add(Cue(EXPLOSION, Cue.Kind.SOUND, "minecraft:entity.generic.explode", "actor", volume = 1.2f, pitch = 1.8f))
        add(Cue(EXPLOSION, Cue.Kind.SOUND, "minecraft:block.powder_snow.break", "actor", volume = 2.0f, pitch = 0.8f))
        for ((k, p) in listOf(0.6f, 0.8f, 1.0f).withIndex()) {
            add(Cue(EXPLOSION + 0.02 * k, Cue.Kind.SOUND, "minecraft:block.glass.break", "actor", volume = 2.0f, pitch = p))
            add(Cue(EXPLOSION + 0.03 + 0.04 * k, Cue.Kind.SOUND, "minecraft:block.amethyst_cluster.break", "actor", volume = 2.0f, pitch = p + 0.2f))
        }
        for (k in 0 until 5) {
            val angle = Math.toRadians(40.0 + 70.0 * k)
            add(Cue(EXPLOSION + 0.9 + 0.18 * k, Cue.Kind.SOUND, "minecraft:block.amethyst_block.hit", dx = 7.0 * sin(angle), dz = 7.0 * Math.cos(angle), volume = 1.2f, pitch = 1.3f + 0.1f * k))
        }
        add(Cue(CRY, Cue.Kind.CRY, "cobblemon:pokemon.articuno.cry", "actor", volume = 2.5f))
        add(Cue(CRY, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.growl", "actor", volume = 0.5f, pitch = 1.9f))
        add(Cue(CRY, Cue.Kind.EVENT, "breath"))
        add(Cue(16.0, Cue.Kind.SOUND, "minecraft:entity.breeze.wind_burst", dy = 6.0, volume = 1.4f, pitch = 0.8f))
        add(Cue(16.0, Cue.Kind.EVENT, "clear"))
        add(Cue(LANDING - 0.8, Cue.Kind.SOUND, "minecraft:entity.ender_dragon.flap", "actor", volume = 1.5f, pitch = 1.1f))
        add(Cue(LANDING, Cue.Kind.EVENT, "landing"))
        add(Cue(LANDING, Cue.Kind.SOUND, "minecraft:block.powder_snow.fall", "actor", volume = 1.5f, pitch = 0.8f))
        add(Cue(LANDING, Cue.Kind.SOUND, "minecraft:block.snow.fall", "actor", volume = 1.2f))
        if (test) {
            add(Cue(VANISH - 0.05, Cue.Kind.EVENT, "vanish"))
            add(Cue(VANISH - 0.05, Cue.Kind.SOUND, "minecraft:block.powder_snow.break", "actor", volume = 1.6f))
            add(Cue(VANISH, Cue.Kind.SOUND, "minecraft:block.amethyst_block.chime", "actor", volume = 1.4f, pitch = 1.8f))
        }
    }.sortedBy { it.time }

    companion object {
        const val REVEAL = 9.0
        const val DESCENT = 11.0
        const val EXPLOSION = 13.5
        const val CRY_SHOT = 15.0
        const val CRY = 15.0
        const val GLIDE = 15.6
        const val LANDING = 18.0
        const val VANISH = 19.25
        const val VANISH_END = 19.5

        /** Articuno's head above its feet, roughly. */
        const val HEAD = 1.6
    }
}
