package generations.gg.generations.core.generationscore.common.client.johto

import generations.gg.generations.core.generationscore.common.client.cinematic.audio.Bed
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.Bell
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.Boom
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.Lead
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.Noise
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.NoiseSweep
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.Pad
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.Pluck
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.Rise
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.StateFilter
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.Voice
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.midi
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.SceneScript
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import kotlin.math.PI
import kotlin.math.sin

/**
 * The Legend of Johto's music and ambiences (pure, D18): one theme of its own — a rising
 * pentatonic figure on a plucked string over a soft pad, answered by bells — in a key for each
 * beast (Raikou E minor, Entei C minor, Suicune A minor) and for Ho-Oh (D major); the history goes
 * through all of them. The ambiences: wind, the storm's rain, the fire's crackle, water.
 */
object JohtoMusic {
    /** The theme, in semitones over its root, and its beats. */
    private val theme = intArrayOf(0, 3, 5, 7, 10, 7, 12, 10, 7, 5)
    private val beats = doubleArrayOf(0.0, 0.5, 1.0, 1.5, 2.25, 2.75, 3.25, 4.0, 4.5, 5.25)

    private fun chord(root: Int, vararg steps: Int) = DoubleArray(steps.size) { midi(root + steps[it]) }

    private fun smoothstep(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    /** The theme from `at`, plucked (koto-like) or led (brass), `beat` seconds a beat. */
    private fun theme(v: MutableList<Voice>, at: Double, root: Int, beat: Double, gain: Double, lead: Boolean = false, notes: Int = theme.size) {
        for (i in 0 until notes.coerceAtMost(theme.size)) {
            val t = at + beats[i] * beat
            val f = midi(root + theme[i])
            if (lead) v += Lead(t, beat * 0.9, f, gain, (i % 3 - 1) * 0.2)
            else v += Pluck(t, f, gain, (i % 3 - 1) * 0.3, 0.7)
        }
    }

    private fun root(beast: Int): Int = when (beast) {
        0 -> 52 // E3
        1 -> 48 // C3
        else -> 57 // A3
    }

    fun music(id: String, script: SceneScript, stretch: Double, offset: Double): SynthRenderer {
        val s = stretch
        val v = ArrayList<Voice>()
        when {
            id == CinematicScenes.JOHTO_HISTORY -> history(v, s)
            id == CinematicScenes.HO_OH_SUMMON -> summon(v, s)
            id in CinematicScenes.AWAKENINGS -> awakening(v, s, CinematicScenes.ALL.indexOf(id) - CinematicScenes.ALL.indexOf(CinematicScenes.RAIKOU_AWAKENING))
            else -> encounter(v, s, CinematicScenes.ALL.indexOf(id) - CinematicScenes.ALL.indexOf(CinematicScenes.RAIKOU_ENCOUNTER))
        }
        return SynthRenderer(v, (CinematicScenes.writtenSeconds(id) + 1.5) * s, offset)
    }

    private fun history(v: MutableList<Voice>, s: Double) {
        // The tower whole: A minor, the theme on a string, distant bells.
        v += Pad(0.0, 8.0 * s, chord(45, 0, 7, 12, 15), 0.07, 2.5 * s, 1.2 * s, 500.0, 1300.0)
        theme(v, 1.2 * s, 57, 0.55 * s, 0.07)
        for ((i, n) in intArrayOf(81, 76, 79).withIndex()) v += Bell((0.8 + i * 1.7) * s, midi(n), 0.03, (i - 1) * 0.4, 3.0)
        // The storm: a low drone, a rising tension, the strike.
        v += Pad(7.0 * s, 8.0 * s, chord(40, 0, 1, 7, 13), 0.08, 1.5 * s, 1.0 * s, 300.0, 900.0, width = 0.9)
        v += Rise(8.0 * s, 4.0 * s, 90.0, 1.4, 0.035)
        v += Boom(JohtoHistoryScript.STRIKE * s, 70.0, 30.0, 0.7, 1.6)
        v += NoiseSweep((JohtoHistoryScript.STRIKE - 1.0) * s, 1.0 * s, 300.0, 5000.0, 0.4, 0.08, rise = true, seed = 301)
        // The fire: pulses, a dark chord.
        for (k in 0 until 6) v += Boom((14.0 + k * 1.4) * s, 60.0, 34.0, 0.25, 0.9)
        v += Pad(14.0 * s, 8.5 * s, chord(41, 0, 6, 12, 15), 0.08, 0.8 * s, 1.5 * s, 400.0, 1200.0)
        // The rain: sparse bells, the theme's first notes, slow.
        v += Pad(22.0 * s, 7.0 * s, chord(45, 0, 7, 12), 0.05, 1.5 * s, 1.5 * s, 300.0, 700.0)
        theme(v, 23.5 * s, 57, 0.9 * s, 0.05, notes = 4)
        // The rainbow: A major, a harp rising, bells.
        v += Pad(28.0 * s, 9.0 * s, chord(45, 0, 4, 7, 12, 16), 0.08, 2.0 * s, 1.0 * s, 700.0, 2000.0, width = 0.8)
        for ((i, n) in intArrayOf(57, 59, 61, 64, 66, 69, 71, 73, 76, 78, 81).withIndex()) v += Pluck((28.6 + i * 0.12) * s, midi(n), 0.06, -0.5 + i * 0.1, 0.9)
        for ((i, n) in intArrayOf(81, 85, 88).withIndex()) v += Bell((33.0 + i * 0.5) * s, midi(n), 0.04, (i - 1) * 0.4, 3.2)
        // The rebirth: a choir, the deep hit, the theme led in D major.
        v += Boom(JohtoHistoryScript.REBIRTH * s, 90.0, 34.0, 0.6, 1.4)
        v += Pad(JohtoHistoryScript.REBIRTH * s, 5.0 * s, chord(50, 0, 4, 7, 12, 16), 0.11, 0.3 * s, 1.5 * s, 900.0, 2400.0, width = 0.9)
        theme(v, 39.2 * s, 62, 0.45 * s, 0.05, lead = true)
        // The flight: the theme on the string again, resolved.
        v += Pad(43.5 * s, 7.0 * s, chord(50, 0, 7, 12, 16), 0.08, 1.0 * s, 2.0 * s, 700.0, 1500.0)
        theme(v, 44.0 * s, 62, 0.5 * s, 0.07)
        v += Bell(48.5 * s, midi(86), 0.04, 0.2, 3.5)
    }

    private fun awakening(v: MutableList<Voice>, s: Double, beast: Int) {
        val r = root(beast)
        val b = JohtoAwakeningScript.BURST * s
        v += Pad(0.0, 9.5 * s, chord(r - 12, 0, 7, 12, 13), 0.06, 2.0 * s, 0.4 * s, 300.0, 900.0)
        v += Rise(3.0 * s, 6.5 * s, 70.0, 1.5, 0.035)
        v += NoiseSweep(b - 1.2 * s, 1.2 * s, 300.0, 6000.0, 0.4, 0.08, rise = true, seed = 401 + beast)
        v += Boom(b, 80.0, 32.0, 0.7, 1.4)
        v += Pad(b, 6.0 * s, chord(r, 0, 3, 7, 12), 0.09, 0.2 * s, 2.0 * s, 800.0, 2000.0, width = 0.8)
        theme(v, b + 0.6 * s, r + 12, 0.38 * s, 0.05, lead = true, notes = 7)
        for ((i, n) in intArrayOf(r + 24, r + 27, r + 31).withIndex()) v += Bell(b + i * 0.03, midi(n), 0.04, (i - 1) * 0.4, 3.0)
        v += Pad(JohtoAwakeningScript.FLEE * s, 6.5 * s, chord(r, 0, 7, 12), 0.06, 0.6 * s, 2.0 * s, 500.0, 1200.0)
        theme(v, (JohtoAwakeningScript.REFORM - 0.4) * s, r + 12, 0.5 * s, 0.05, notes = 5)
    }

    private fun encounter(v: MutableList<Voice>, s: Double, beast: Int) {
        val r = root(beast)
        val land = JohtoEncounterScript.LAND * s
        v += Pad(0.0, 7.0 * s, chord(r - 12, 0, 7, 12), 0.06, 2.0 * s, 0.4 * s, 300.0, 800.0)
        v += Rise(3.0 * s, 4.0 * s, 80.0, 1.3, 0.03)
        v += Boom(land, 90.0, 32.0, 0.7, 1.4)
        v += Pad(land, 7.0 * s, chord(r, 0, 3, 7, 12, 15), 0.1, 0.3 * s, 1.5 * s, 800.0, 2200.0, width = 0.85)
        theme(v, land + 1.0 * s, r + 12, 0.45 * s, 0.06, lead = true)
        theme(v, land + 1.0 * s, r, 0.45 * s, 0.03)
        // Its gaze: the major, quiet.
        v += Pad(13.0 * s, 9.0 * s, chord(r, 0, 4, 7, 12), 0.07, 1.0 * s, 2.0 * s, 600.0, 1400.0)
        for ((i, n) in intArrayOf(r + 24, r + 28, r + 31, r + 36).withIndex()) v += Bell((14.0 + i * 0.4) * s, midi(n), 0.035, (i - 1.5) * 0.3, 3.0)
        theme(v, 17.0 * s, r + 12, 0.55 * s, 0.05, notes = 6)
    }

    private fun summon(v: MutableList<Voice>, s: Double) {
        // The memories: three bells, one per beast's key.
        for ((i, n) in intArrayOf(76, 72, 81).withIndex()) v += Bell((0.6 + i * 0.5) * s, midi(n), 0.045, (i - 1) * 0.5, 3.0)
        v += Pad(0.0, 6.0 * s, chord(50, 0, 7, 12, 14), 0.06, 2.0 * s, 1.0 * s, 500.0, 1200.0)
        // The spectral beasts: a chant, the theme in the three keys.
        v += Pad(5.0 * s, 6.5 * s, chord(45, 0, 3, 7, 10, 12), 0.08, 1.0 * s, 1.0 * s, 600.0, 1500.0, width = 0.9)
        theme(v, 5.4 * s, 52, 0.3 * s, 0.05, notes = 4)
        theme(v, 7.2 * s, 48, 0.3 * s, 0.05, notes = 4)
        theme(v, 9.0 * s, 57, 0.3 * s, 0.05, notes = 4)
        // The beams: the hit, a rise.
        v += Boom(11.0 * s, 80.0, 34.0, 0.6, 1.3)
        v += Rise(11.0 * s, 4.0 * s, 160.0, 1.6, 0.035)
        // The golden sky: D major, harp and bells.
        v += Pad(15.0 * s, 6.0 * s, chord(50, 0, 4, 7, 12, 16), 0.09, 1.5 * s, 1.0 * s, 800.0, 2200.0, width = 0.8)
        for ((i, n) in intArrayOf(62, 64, 66, 69, 71, 74, 76, 78, 81, 86).withIndex()) v += Pluck((15.4 + i * 0.12) * s, midi(n), 0.06, -0.5 + i * 0.1, 0.9)
        // Ho-Oh comes down: the theme, led, in D.
        v += Pad(HoOhSummonScript.DESCENT * s, 7.5 * s, chord(38, 0, 7, 12, 16, 19), 0.1, 0.5 * s, 1.0 * s, 700.0, 1800.0, width = 0.85)
        theme(v, (HoOhSummonScript.DESCENT + 0.5) * s, 62, 0.5 * s, 0.06, lead = true)
        theme(v, (HoOhSummonScript.DESCENT + 0.5) * s, 50, 0.5 * s, 0.035, lead = true)
        v += Boom(HoOhSummonScript.LANDED * s, 100.0, 34.0, 0.6, 1.4)
        v += Pad(HoOhSummonScript.LANDED * s, 3.5 * s, chord(50, 0, 4, 7, 12, 16, 19), 0.11, 0.2 * s, 1.5 * s, 900.0, 2400.0, width = 0.9)
        for ((i, n) in intArrayOf(74, 78, 81, 86).withIndex()) v += Bell(HoOhSummonScript.LANDED * s + i * 0.03, midi(n), 0.05, (i - 1.5) * 0.3, 3.6)
    }

    /** Wind always; the history's rain and fire; an encounter's own element. */
    fun ambience(id: String, script: SceneScript, stretch: Double, offset: Double): SynthRenderer {
        val windL = StateFilter()
        val windR = StateFilter()
        val rainL = StateFilter()
        val rainR = StateFilter()
        val n1 = Noise(601)
        val n2 = Noise(607)
        val n3 = Noise(613)
        val n4 = Noise(617)
        val length = CinematicScenes.writtenSeconds(id)
        val crackle = Noise(631)
        val bed = Bed(0.0, (length + 1.0) * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val sway = 0.5 + 0.5 * sin(2.0 * PI * 0.09 * t)
            var wind = 0.06
            var rain = 0.0
            var fire = 0.0
            when (script) {
                is JohtoHistoryScript -> {
                    wind += 0.08 * script.clouds(a)
                    rain = script.rainFall(a)
                    fire = script.burn(a)
                }
                is JohtoEncounterScript -> when (script.beast) {
                    0 -> wind += 0.1 * script.omen(a)
                    1 -> fire = 0.6 * (script.omen(a) + script.aura(a)).coerceAtMost(1.0)
                    else -> rain = 0.35 * (script.omen(a) + script.aura(a)).coerceAtMost(1.0)
                }
                is JohtoAwakeningScript -> if (script.beast == 1) fire = 0.5 * script.element(a) else if (script.beast == 2) rain = 0.3 * script.element(a)
                is HoOhSummonScript -> wind += 0.08 * script.beams(a)
            }
            windL.process(n1.next(), 350.0 + 300.0 * sway, 0.7)
            windR.process(n2.next(), 400.0 + 300.0 * (1.0 - sway), 0.7)
            rainL.process(n3.next(), 4200.0, 0.5)
            rainR.process(n4.next(), 4600.0, 0.5)
            val c = crackle.next()
            val pop = if (c > 0.985) (c - 0.985) * 40.0 else 0.0
            val g = smoothstep(t / 1.2) * (1.0 - smoothstep((t - length + 1.4) / 1.4))
            out[0] += ((windL.band * wind + rainL.high * 0.05 * rain + pop * 0.12 * fire) * g).toFloat()
            out[1] += ((windR.band * wind + rainR.high * 0.05 * rain + pop * 0.12 * fire) * g).toFloat()
        }
        return SynthRenderer(listOf(bed), (length + 1.0) * stretch, offset, reverbWet = 0.22, echoWet = 0.0)
    }
}
