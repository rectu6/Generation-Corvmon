package generations.gg.generations.core.generationscore.common.client.cinematic.audio

import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.RegiElement
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.RegiScript
import kotlin.math.PI
import kotlin.math.sin

/**
 * The Regis' music and ambience (pure), original pieces on one plan, each in its own key and
 * colour: the shrine waking (a low drone, a call in a code of short and long high notes, the
 * same for the six), the core (a minor chord swelling, the element's pulse: heartbeats for the
 * stone, the dragon and the master, bells for the ice, metal strikes for the steel, fast
 * arpeggios for the lightning), the forming (tension rising to the reveal), the awakening (a deep
 * hit, the theme in major, bells at the lights, a lift at the cry), calm (a soft chord). The
 * ambience: the shrine's rumble and the element's texture (grinding, cold wind, a machine's hum,
 * crackles, a breathing roar, the ground of the master).
 */
object RegiMusic {
    private fun chord(vararg notes: Int) = DoubleArray(notes.size) { midi(notes[it]) }

    private fun smoothstep(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    /** The key's root (MIDI), by element: D, B, C, E, F, C. */
    private fun root(e: RegiElement): Int = when (e) {
        RegiElement.ROCK -> 38
        RegiElement.ICE -> 47
        RegiElement.STEEL -> 36
        RegiElement.ELECTRIC -> 40
        RegiElement.DRAGON -> 41
        RegiElement.GIANT -> 36
    }

    fun voices(script: RegiScript, stretch: Double): List<Voice> {
        val s = stretch
        val e = script.element
        val r = root(e)
        val v = ArrayList<Voice>()
        val core = script.coreAt * s
        val form = script.formAt * s
        val reveal = script.revealAt * s
        val lights = script.lightsAt * s
        val cry = script.cryAt * s
        val calm = script.calmAt * s
        val end = script.end * s
        val big = if (e == RegiElement.GIANT) 1.25 else 1.0

        // The shrine waking: a low drone, a hit, the code.
        v += Pad(0.0, core + 1.0 * s, chord(r - 12, r - 5, r), 0.07, 2.5 * s, 1.0 * s, 160.0, 600.0, width = 0.4)
        v += Boom(0.4 * s, 55.0, 28.0, 0.3 * big, 2.0)
        val code = doubleArrayOf(0.0, 0.18, 0.36, 0.8, 1.25, 1.43, 1.9)
        val long = booleanArrayOf(false, false, false, true, false, false, true)
        for ((i, at) in code.withIndex()) {
            val note = r + 36 + if (long[i]) 7 else 0
            v += Bell(1.2 * s + at * s, midi(note), if (long[i]) 0.04 else 0.03, (i - 3) * 0.12, if (long[i]) 1.6 else 0.5, ratio = 1.0, index = 0.6)
        }
        v += Rise(script.gatherAt * s - 0.5 * s, core - script.gatherAt * s + 0.6 * s, 90.0, 1.5, 0.025)
        // The core: a minor chord swelling, the element's pulse, the theme low.
        v += Pad(core, form - core + 0.6 * s, chord(r, r + 7, r + 12, r + 15), 0.075, 1.5 * s, 0.6 * s, 400.0, 1300.0, width = 0.6)
        v += Boom(core, 70.0, 32.0, 0.35 * big, 1.2)
        var at = core + 0.6 * s
        var n = 0
        while (at < form - 0.1) {
            when (e) {
                RegiElement.ROCK, RegiElement.DRAGON, RegiElement.GIANT -> {
                    v += Boom(at, 62.0, 36.0, 0.22 * big, 0.5)
                    v += Boom(at + 0.22 * s, 58.0, 36.0, 0.14 * big, 0.4)
                    at += 1.6 * s
                }
                RegiElement.ICE -> {
                    v += Bell(at, midi(r + 24 + intArrayOf(0, 7, 3, 10, 12, 7)[n % 6]), 0.03, (n % 3 - 1) * 0.4, 2.4, ratio = 2.0, index = 1.2)
                    at += 0.8 * s
                }
                RegiElement.STEEL -> {
                    v += Bell(at, midi(r + 24 + intArrayOf(0, 0, 7, 3)[n % 4]), 0.035, (n % 2 - 0.5) * 0.5, 1.2, ratio = 3.5, index = 3.0)
                    at += 0.8 * s
                }
                RegiElement.ELECTRIC -> {
                    v += Pluck(at, midi(r + 24 + intArrayOf(0, 7, 12, 15, 12, 7, 3, 7)[n % 8]), 0.05, (n % 2 - 0.5) * 0.6, 0.25)
                    at += 0.25 * s
                }
            }
            n++
        }
        val theme = listOf(0.0 to 12, 0.6 to 15, 1.2 to 19, 2.0 to 17, 2.6 to 15, 3.4 to 12)
        for ((t, note) in theme) v += Lead(core + 1.4 * s + t * s, 0.55 * s, midi(r + note), 0.04, -0.1)
        // The forming: tension on the sixth then the fifth, rising into the reveal.
        v += Pad(form, (reveal - form) * 0.5, chord(r + 8, r + 12, r + 15, r + 20), 0.07, 0.6 * s, 0.4 * s, 600.0, 1800.0, width = 0.7)
        v += Pad(form + (reveal - form) * 0.5, (reveal - form) * 0.5 + 0.1 * s, chord(r + 7, r + 11, r + 14, r + 19), 0.075, 0.4 * s, 0.2 * s, 700.0, 2400.0, width = 0.7)
        v += Rise(form, reveal - form, 140.0, 1.8, 0.035)
        v += NoiseSweep(reveal - 1.2 * s, 1.2 * s, 300.0, 7000.0, 0.45, 0.08, rise = true, seed = 401 + r)
        // The awakening: the hit, the theme in major, bells.
        v += Boom(reveal, 95.0, 28.0, 0.6 * big, 1.8)
        v += Pad(reveal, cry - reveal + 0.4 * s, chord(r - 12, r, r + 7, r + 12, r + 16, r + 19), 0.1, 0.2, 0.8 * s, 900.0, 2400.0, width = 0.9)
        val bright = listOf(0.3 to 12, 0.7 to 16, 1.1 to 19, 1.7 to 24, 2.1 to 21, 2.5 to 19)
        for ((t, note) in bright) v += Lead(reveal + t * s, 0.5 * s, midi(r + note), 0.05, 0.1)
        for ((t, note) in bright) v += Lead(reveal + t * s, 0.5 * s, midi(r + note - 12), 0.028, -0.25)
        for (i in 0 until script.lightCount) {
            v += Bell(lights + i * 1.1 / script.lightCount * s, midi(r + 31 + intArrayOf(0, 5, 9, 12, 9, 5, 12)[i % 7]), 0.03, (i - 3) * 0.15, 2.0, ratio = 2.0, index = 1.0)
        }
        // The cry: a lift to the fourth, a hit.
        v += Boom(cry, 80.0, 40.0, 0.35 * big, 1.0)
        v += Pad(cry, calm - cry + 0.5 * s, chord(r + 5, r + 9, r + 12, r + 17), 0.09, 0.25, 0.8 * s, 900.0, 2200.0, width = 0.9)
        // Calm: a soft major chord settling.
        v += Pad(calm, end - calm + 1.0 * s, chord(r, r + 7, r + 12, r + 16), 0.075, 0.8 * s, 1.5 * s, 600.0, 1100.0)
        val harp = intArrayOf(24, 19, 16, 12, 7, 12, 16, 24)
        for ((i, note) in harp.withIndex()) v += Pluck(calm + (0.3 + i * 0.28) * s, midi(r + note), 0.05, -0.4 + i * 0.1, 0.9)
        v += Bell(end - 1.6 * s, midi(r + 24), 0.035, 0.0, 3.5)
        return v
    }

    fun music(script: RegiScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(voices(script, stretch), (script.end + 1.5) * stretch, offset)

    /** The shrine's rumble, and the element's own texture. */
    fun ambienceVoices(script: RegiScript, stretch: Double): List<Voice> {
        val e = script.element
        val rumbleL = StateFilter()
        val rumbleR = StateFilter()
        val texL = StateFilter()
        val texR = StateFilter()
        val noiseA = Noise(701)
        val noiseB = Noise(709)
        val noiseC = Noise(719)
        val noiseD = Noise(727)
        val slow = Noise(733)
        var sway = 0.0
        val end = script.end
        val bed = Bed(0.0, (end + 0.5) * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val rumble = script.rumble(a)
            val ground = script.ground(a)
            val core = script.core(a)
            val flicker = script.flicker(a)
            sway += (slow.next() - sway) * 0.0004
            val deep = if (e == RegiElement.GIANT) 45.0 else 70.0
            rumbleL.process(noiseA.next(), deep + 40.0 * rumble, 0.8)
            rumbleR.process(noiseB.next(), deep + 45.0 * rumble, 0.8)
            val (cutoff, damping) = when (e) {
                RegiElement.ROCK -> 420.0 + 200.0 * sway to 0.5
                RegiElement.ICE -> 2600.0 + 900.0 * sway to 0.6
                RegiElement.STEEL -> 900.0 to 0.3
                RegiElement.ELECTRIC -> 4200.0 to 0.4
                RegiElement.DRAGON -> 160.0 + 60.0 * sway to 0.7
                RegiElement.GIANT -> 260.0 + 80.0 * sway to 0.6
            }
            texL.process(noiseC.next(), cutoff, damping)
            texR.process(noiseD.next(), cutoff * 1.04, damping)
            var tex = when (e) {
                RegiElement.ROCK -> 0.1 * ground * (0.5 + 0.5 * sway.coerceIn(-1.0, 1.0))
                RegiElement.ICE -> 0.07 * ground
                RegiElement.STEEL -> 0.05 * ground
                RegiElement.ELECTRIC -> 0.05 * ground + 0.25 * flicker
                RegiElement.DRAGON -> 0.12 * ground * (0.6 + 0.4 * sin(2.0 * PI * 0.45 * t))
                RegiElement.GIANT -> 0.1 * ground
            }
            tex += 0.04 * core
            // A machine's hum for the steel, a buzz for the lightning.
            val hum = when (e) {
                RegiElement.STEEL -> 0.035 * ground * (sin(2.0 * PI * 60.0 * time) + 0.5 * sin(2.0 * PI * 120.0 * time))
                RegiElement.ELECTRIC -> 0.02 * ground * sin(2.0 * PI * 120.0 * time).let { if (it > 0) 1.0 else -1.0 } * (0.4 + 0.6 * flicker)
                else -> 0.0
            }
            val g = smoothstep(t / 1.5) * (1.0 - smoothstep((t - (end - 2.0)) / 2.0))
            val low = 0.45 * (0.25 + 0.75 * rumble) * (if (e == RegiElement.GIANT) 1.3 else 1.0)
            out[0] += ((rumbleL.low * low + texL.band * tex + hum) * g).toFloat()
            out[1] += ((rumbleR.low * low + texR.band * tex + hum) * g).toFloat()
        }
        return listOf(bed)
    }

    fun ambience(script: RegiScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(ambienceVoices(script, stretch), (script.end + 0.5) * stretch, offset, reverbWet = 0.3, echoWet = 0.0)
}
