package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraDirector
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.bezier
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.decay
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeInOutCubic
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
import kotlin.math.sin

/**
 * Mewtwo — the Awakening (RKS-PLAN.md § 5.2, decision D11), 24 s, pure (tested off-game). Its
 * frame: the bottom centre of the RKS Machine's tank, +z toward the console's front.
 *
 * 0–4 s, awakening: through the glass, Mewtwo floats in the murky liquid; a heartbeat, its first
 * psychic pulse; a slow push toward its face.
 * 4–9 s, alarm: red emergency lights, sirens; a wide low orbit round the machine; arcs from the
 * reactor, cracks spreading on the glass, the tank shaking more and more.
 * 9–13 s, breach: a surge, then the glass bursts at 11 s (3D shards, the liquid thrown out, a
 * shock wave, slow motion); a low shot in front of the machine.
 * 13–19 s, release: Mewtwo rises out of the broken tank, floats forward over the console, its
 * violet aura, debris levitating round it; its cry at 16.5 s with a psychic wave.
 * 19–24 s: it comes down to the landing point (21.2 s) and stands; back to the player's camera
 * from 22.4 s.
 *
 * tankFeet: the height of Mewtwo's feet in the tank (the model's height at 0.9 of its size, as the
 * machine's renderer draws it), so that the scene takes over where the tank left it.
 */
class MewtwoScript(landing: Vector3d, val test: Boolean, val tankFeet: Double = 1.05) : SceneScript {
    val landing = Vector3d(landing)

    /** A slow motion on the breach, the time given back by 13.8 s. */
    val slowMotion = SlowMotion(slowAt = 11.3, slowWidth = 0.35, slowDepth = 0.6, catchAt = 12.5, catchWidth = 0.35)

    override fun action(t: Double): Double = slowMotion.action(t)

    // ---------------------------------------------------------------- Mewtwo's path

    /** Where it hovers for its cry, over the console's front. */
    val hoverPoint = Vector3d(0.0, 2.6, 2.4)
    private val risePoint = Vector3d(0.0, 2.35, 0.1)
    private val tmp = Vector3d()
    private val c1 = Vector3d()
    private val c2 = Vector3d()

    private fun tank(a: Double, out: Vector3d): Vector3d {
        // Trembling more and more during the alarm (deterministic noise).
        val shake = 0.012 + 0.03 * phase(a, ALARM, BREAK)
        return out.set(CameraMath.noise(a * 9.0, 11) * shake, tankFeet + 0.04 * sin(a * 1.3), CameraMath.noise(a * 9.0, 23) * shake)
    }

    private fun hover(a: Double, out: Vector3d): Vector3d = out.set(hoverPoint.x + 0.05 * sin(a * 0.9), hoverPoint.y + 0.08 * sin(a * 1.4), hoverPoint.z)

    /** Mewtwo's feet at action time a. */
    fun mewtwoPos(a: Double, out: Vector3d): Vector3d = when {
        a < BREAK -> tank(a, out)
        a < RISE -> {
            // Pushed up a little by the burst.
            tank(BREAK, tmp)
            out.set(tmp).lerp(risePoint, 0.25 * easeOutCubic((a - BREAK) / (RISE - BREAK)))
        }
        a < OUT -> {
            tank(BREAK, tmp)
            val start = Vector3d(tmp).lerp(risePoint, 0.25)
            out.set(start).lerp(risePoint, easeInOutSine((a - RISE) / (OUT - RISE)))
        }
        a < HOVER -> {
            hover(HOVER, tmp)
            c1.set(risePoint.x, risePoint.y + 0.4, risePoint.z + 0.6)
            c2.set(tmp.x, tmp.y + 0.3, tmp.z - 0.8)
            bezier(risePoint, c1, c2, tmp, easeInOutCubic((a - OUT) / (HOVER - OUT)), out)
        }
        a < DESCEND -> hover(a, out)
        a < LANDING -> {
            hover(DESCEND, tmp)
            c1.set(tmp.x, tmp.y - 0.2, tmp.z + 0.4)
            c2.set(landing.x, landing.y + 0.9, landing.z - 0.3)
            bezier(tmp, c1, c2, landing, easeInOutCubic((a - DESCEND) / (LANDING - DESCEND)), out)
        }
        else -> out.set(landing)
    }

    fun headPos(a: Double, out: Vector3d): Vector3d = mewtwoPos(a, out).add(0.0, HEAD * scaleAt(a), 0.0)

    fun scaleAt(a: Double): Double = if (a < BREAK) 0.9 else 0.9 + 0.1 * smooth((a - BREAK) / 1.8)

    override fun actor(a: Double, out: ActorKey): ActorKey {
        mewtwoPos(a, out.pos)
        out.yaw = 0.0
        out.pitch = 0.0
        out.roll = 0.0
        out.scale = scaleAt(a)
        out.pose = if (a >= LANDING - 0.25) ActorPose.STAND else ActorPose.HOVER
        // Leaning forward a little while it floats out, back up for its cry.
        if (a >= OUT && a < HOVER) out.pitch = 9.0 * window(a, OUT, OUT + 0.6, HOVER - 0.8, HOVER)
        if (a >= DESCEND && a < LANDING) out.pitch = 6.0 * window(a, DESCEND, DESCEND + 0.5, LANDING - 0.7, LANDING - 0.2)
        if (test && a >= VANISH) {
            out.scale = out.scale * (1.0 - CameraMath.easeInCubic(phase(a, VANISH, VANISH_END)))
            if (a >= VANISH_END) out.pose = ActorPose.HIDDEN
        }
        return out
    }

    // ---------------------------------------------------------------- the camera

    private val m2 = Vector3d()

    /** Through the glass, a slow push toward its face. */
    private val awakening = Shot("awakening", 0.0, 0.0) { t, m, out ->
        val u = easeInOutSine(phase(t, 0.0, 4.2))
        out.pos.set(lerp(0.35, 0.12, u), lerp(tankFeet + 1.1, tankFeet + 1.3, u), lerp(2.7, 1.85, u))
        out.target.set(0.0, tankFeet + 1.45, 0.0)
        out.fov = lerp(42.0, 34.0, u) - 2.0 * pulse(action(t), PULSE, 0.05, 0.5) * m.punch
        out.roll = 1.5 * u * m.roll
    }

    /** A wide, low orbit round the machine, the alarm's lights sweeping. */
    private val alarm = Shot("alarm", 3.9, 0.7) { t, m, out ->
        val v = phase(t, 3.9, 9.0)
        orbit(0.0, 0.0, lerp(6.2, 5.2, v), 28.0 + 52.0 * easeInOutSine(v) * m.orbit, lerp(1.1, 1.9, v), out.pos)
        out.target.set(0.0, 2.1, 0.0)
        out.fov = lerp(60.0, 54.0, v)
        out.roll = -3.0 * v * m.roll
    }

    /** The breach: low in front of the machine, aside. */
    private val breach = Shot("breach", 9.0, 0.0) { t, m, out ->
        val u = easeInOutSine(phase(t, 9.0, 13.2))
        out.pos.set(lerp(-2.3, -1.9, u), lerp(0.9, 1.05, u), lerp(4.4, 3.8, u))
        out.target.set(0.0, 1.9 + 0.4 * u, 0.0)
        val a = action(t)
        out.fov = lerp(50.0, 47.0, u) + 7.0 * pulse(a, BREAK, 0.04, 0.5) * m.punch
        out.roll = 2.0 * m.roll * (1.0 - u)
    }

    /** The release: a low orbit round Mewtwo as it floats out, looking up at it. */
    private val release = Shot("release", 13.2, 0.8) { t, m, out ->
        val a = action(t)
        mewtwoPos(a, m2)
        val v = phase(t, 13.2, 19.0)
        val angle = -38.0 + 76.0 * easeInOutSine(v) * m.orbit
        orbit(m2.x, m2.z, lerp(4.6, 4.0, v), angle, maxOf(0.7, m2.y - 1.3), out.pos)
        out.target.set(m2.x, m2.y + 1.2, m2.z)
        out.fov = lerp(56.0, 48.0, v) - 4.0 * pulse(a, CRY, 0.05, 0.6) * m.punch
        out.roll = (2.0 - 4.0 * v) * m.roll
    }

    /** The final shot: low in front of the landing point, a slow push toward its head. */
    private val finale = Shot("final", FINAL_SHOT, 0.0) { t, m, out ->
        val u = easeInOutSine(phase(t, FINAL_SHOT, 22.4))
        out.pos.set(landing.x + lerp(1.9, 1.2, u), landing.y + lerp(0.8, 0.95, u), landing.z + lerp(4.2, 3.1, u))
        headPos(action(t), m2)
        out.target.set(m2)
        out.fov = lerp(52.0, 42.0, u)
        out.roll = 0.0
    }

    override val director = CameraDirector(listOf(awakening, alarm, breach, release, finale))

    // ---------------------------------------------------------------- the look

    override fun cameraWeight(t: Double): Double = smoother(t / 1.0) * (1.0 - smoother((t - 22.4) / 1.6))

    override fun letterbox(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 22.5) / 1.3))

    override fun title(t: Double): Double = window(t, 16.9, 17.4, 20.6, 21.2)

    override fun presence(t: Double): Double = smooth(t / 0.8) * (1.0 - smooth((t - 22.5) / 1.5))

    override fun shake(a: Double): Double =
        (0.08 + 0.3 * phase(a, ALARM, BREAK)) * window(a, ALARM, ALARM + 0.5, BREAK, BREAK + 0.2) +
            1.0 * decay(a, BREAK, 0.55) + 0.25 * decay(a, SURGE, 0.4) + 0.7 * decay(a, CRY, 0.5) + 0.15 * decay(a, LANDING, 0.3) +
            0.12 * decay(a, PULSE, 0.3)

    override fun flash(a: Double): Double =
        (0.9 * pulse(a, BREAK, 0.02, 0.22) + 0.5 * pulse(a, CRY, 0.03, 0.25) + 0.2 * pulse(a, PULSE, 0.05, 0.3) + 0.25 * pulse(a, SURGE, 0.03, 0.2)).coerceIn(0.0, 1.0)

    /** The red alarm (4–11 s), then the violet of its power, back to normal as it lands. */
    override fun grade(t: Double, out: Grade): Grade {
        val a = action(t)
        val red = alarm(a) * (0.65 + 0.35 * (0.5 + 0.5 * sin(a * 2.0 * Math.PI * 1.1)))
        val violet = aura(a)
        val dark = smooth(t / 2.0) * (1.0 - smooth((t - 21.0) / 2.5))
        out.r = 1.0 + 0.16 * red + 0.05 * violet
        out.g = 1.0 - 0.2 * red - 0.13 * violet
        out.b = 1.0 - 0.14 * red + 0.2 * violet
        out.exposure = 1.0 - 0.18 * dark - 0.12 * red + 0.06 * violet
        out.saturation = 1.0 + 0.1 * red + 0.12 * violet
        out.contrast = 1.0 + 0.12 * dark + 0.06 * violet
        out.vignette = 0.35 + 0.25 * red + 0.15 * violet
        return out
    }

    // ---------------------------------------------------------------- envelopes (action time)

    /** The emergency lights. */
    fun alarm(a: Double): Double = window(a, ALARM, ALARM + 0.4, 18.5, 20.5)

    /** The cracks on the glass (0 none, 1 all), gone with the glass. */
    fun cracks(a: Double): Double = if (a >= BREAK) 0.0 else smooth(phase(a, CRACK1 - 0.2, CRACK3 + 1.2))

    /** The liquid's own glow (the clone's energy). */
    fun liquidGlow(a: Double): Double = if (a >= BREAK) 0.0 else 0.35 + 0.25 * pulse(a, PULSE, 0.1, 0.8) + 0.5 * window(a, SURGE, SURGE + 0.3, BREAK - 0.1, BREAK)

    /** The arcs from the reactor and the cap during the alarm, and round Mewtwo at its cry. */
    fun arcs(a: Double): Double = (window(a, ALARM + 0.5, ALARM + 1.0, BREAK, BREAK + 0.3) * (0.5 + 0.5 * phase(a, ALARM, BREAK)) + 0.8 * window(a, CRY, CRY + 0.05, CRY + 0.9, CRY + 1.4)).coerceIn(0.0, 1.0)

    /** Mewtwo's violet aura. */
    fun aura(a: Double): Double = smooth((a - (BREAK + 0.4)) / 0.9) * (1.0 - smooth((a - 20.4) / 1.6))

    /** Debris levitating round it. */
    fun debris(a: Double): Double = window(a, BREAK + 0.6, BREAK + 1.6, 18.6, 20.0)

    /** The violet fog and the darkness round the machine (the ambience, up to 50 blocks). */
    fun fog(a: Double): Double = smooth((a - 3.5) / 2.5) * (1.0 - smooth((a - 20.0) / 3.0))

    /** The shock wave of the breach: radius (m) and strength. */
    fun shockRadius(a: Double): Double = 22.0 * easeOutCubic((a - BREAK) / 1.2)

    fun shock(a: Double): Double = if (a < BREAK) 0.0 else 1.0 - smooth((a - BREAK) / 1.2)

    /** The psychic wave of its cry. */
    fun cryWave(a: Double): Double = if (a < CRY) 0.0 else 1.0 - smooth((a - CRY) / 1.1)

    override fun envelopes(a: Double): DoubleArray = doubleArrayOf(alarm(a), cracks(a), liquidGlow(a).coerceAtMost(1.0), arcs(a), aura(a), debris(a), fog(a), shock(a), cryWave(a))

    // ---------------------------------------------------------------- cues

    override val cues: List<Cue> = buildList {
        add(Cue(0.2, Cue.Kind.SOUND, "minecraft:block.conduit.ambient", dy = 2.0, volume = 1.3f, pitch = 0.6f))
        for ((i, at) in doubleArrayOf(1.2, 2.0, 2.7, 3.25, 3.7).withIndex()) {
            add(Cue(at, Cue.Kind.SOUND, "minecraft:entity.warden.heartbeat", "actor", volume = 1.6f, pitch = 0.75f + 0.05f * i))
        }
        add(Cue(PULSE, Cue.Kind.EVENT, "pulse"))
        add(Cue(PULSE, Cue.Kind.SOUND, "minecraft:block.beacon.power_select", "actor", volume = 1.2f, pitch = 0.6f))
        add(Cue(ALARM, Cue.Kind.EVENT, "alarm"))
        add(Cue(ALARM + 0.2, Cue.Kind.SOUND, "minecraft:entity.elder_guardian.curse", dy = 2.0, volume = 1.0f, pitch = 0.7f))
        var bell = ALARM
        while (bell < BREAK - 0.5) {
            add(Cue(bell, Cue.Kind.SOUND, "minecraft:block.bell.use", dx = 1.2, dy = 1.0, dz = 1.0, volume = 1.1f, pitch = 0.5f))
            bell += 1.0
        }
        for ((i, at) in doubleArrayOf(CRACK1, CRACK2, CRACK3).withIndex()) {
            add(Cue(at, Cue.Kind.EVENT, "crack:$i"))
            add(Cue(at, Cue.Kind.SOUND, "minecraft:entity.turtle.egg_crack", dy = 1.5 + i * 0.6, dz = 0.6, volume = 1.6f, pitch = 0.6f + 0.1f * i))
        }
        add(Cue(5.0, Cue.Kind.SOUND, "minecraft:block.respawn_anchor.charge", dz = -1.0, dy = 2.0, volume = 1.2f, pitch = 0.8f))
        add(Cue(7.0, Cue.Kind.SOUND, "minecraft:block.respawn_anchor.charge", dz = -1.0, dy = 2.0, volume = 1.3f, pitch = 1.0f))
        add(Cue(SURGE, Cue.Kind.EVENT, "surge"))
        add(Cue(SURGE, Cue.Kind.SOUND, "minecraft:block.beacon.ambient", dy = 2.0, volume = 1.6f, pitch = 0.6f))
        add(Cue(SURGE + 0.4, Cue.Kind.SOUND, "minecraft:block.respawn_anchor.deplete", dy = 2.0, volume = 1.5f, pitch = 1.4f))
        add(Cue(BREAK, Cue.Kind.EVENT, "break"))
        add(Cue(BREAK, Cue.Kind.SOUND, "minecraft:block.glass.break", dy = 2.0, volume = 2.5f, pitch = 0.6f))
        add(Cue(BREAK, Cue.Kind.SOUND, "minecraft:block.glass.break", dy = 1.0, dz = 0.7, volume = 2.5f, pitch = 0.85f))
        add(Cue(BREAK, Cue.Kind.SOUND, "minecraft:entity.generic.explode", dy = 2.0, volume = 1.4f, pitch = 1.3f))
        add(Cue(BREAK, Cue.Kind.SOUND, "minecraft:entity.warden.sonic_boom", dy = 2.0, volume = 1.2f, pitch = 1.4f))
        add(Cue(BREAK + 0.05, Cue.Kind.SOUND, "minecraft:entity.generic.splash", dy = 1.0, dz = 0.8, volume = 1.6f, pitch = 0.7f))
        add(Cue(BREAK + 0.4, Cue.Kind.SOUND, "minecraft:block.glass.break", dx = -1.0, dz = 1.2, volume = 1.4f, pitch = 1.2f))
        add(Cue(BREAK + 0.7, Cue.Kind.SOUND, "minecraft:block.glass.break", dx = 1.1, dz = 1.4, volume = 1.2f, pitch = 1.4f))
        add(Cue(RISE, Cue.Kind.SOUND, "minecraft:block.beacon.activate", "actor", volume = 1.3f, pitch = 0.5f))
        add(Cue(13.4, Cue.Kind.SOUND, "minecraft:entity.illusioner.cast_spell", "actor", volume = 1.4f, pitch = 0.6f))
        add(Cue(OUT, Cue.Kind.SOUND, "minecraft:entity.evoker.prepare_summon", "actor", volume = 1.0f, pitch = 0.7f))
        add(Cue(CRY, Cue.Kind.CRY, "cobblemon:pokemon.mewtwo.cry", "actor", volume = 2.5f))
        add(Cue(CRY, Cue.Kind.EVENT, "cry"))
        add(Cue(CRY, Cue.Kind.SOUND, "minecraft:entity.warden.sonic_boom", "actor", volume = 0.9f, pitch = 0.8f))
        add(Cue(DESCEND, Cue.Kind.SOUND, "minecraft:block.beacon.deactivate", "actor", volume = 1.2f, pitch = 0.8f))
        add(Cue(LANDING, Cue.Kind.EVENT, "landing"))
        add(Cue(LANDING, Cue.Kind.SOUND, "minecraft:block.amethyst_block.step", "actor", volume = 1.4f, pitch = 0.6f))
        if (test) {
            add(Cue(VANISH - 0.05, Cue.Kind.EVENT, "vanish"))
            add(Cue(VANISH, Cue.Kind.SOUND, "minecraft:entity.enderman.teleport", "actor", volume = 1.2f, pitch = 0.8f))
        }
    }.sortedBy { it.time }

    companion object {
        const val SECONDS = 24.0
        const val PULSE = 2.6
        const val ALARM = 4.0
        const val CRACK1 = 6.0
        const val CRACK2 = 7.6
        const val CRACK3 = 9.0
        const val SURGE = 9.8
        const val BREAK = 11.0
        const val RISE = 12.0
        const val OUT = 13.6
        const val HOVER = 15.6
        const val CRY = 16.5
        const val DESCEND = 18.2
        const val FINAL_SHOT = 19.0
        const val LANDING = 21.2
        const val VANISH = 22.8
        const val VANISH_END = 23.2
        /** Mewtwo's head above its feet (full size). */
        const val HEAD = 1.8
    }
}
