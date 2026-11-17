package generations.gg.generations.core.generationscore.common.client.cinematic.audio

import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.HoOhScript
import kotlin.math.PI
import kotlin.math.sin

/**
 * Ho-Oh's music and ambience (pure), written for its 13 s. D major, bright and solemn: a pad
 * swelling under golden bells as the light grows, a harp rising when the rainbow forms, a brass
 * theme as Ho-Oh comes out of the light, a choir and a deep hit on its pass, then the theme
 * settling on D as the pursuit begins. The ambience: a high wind, the rush of its pass.
 */
object HoOhMusic {
    private const val D2 = 38
    private const val A2 = 45
    private const val D3 = 50
    private const val F3S = 54
    private const val G3 = 55
    private const val A3 = 57
    private const val B3 = 59
    private const val D4 = 62
    private const val E4 = 64
    private const val F4S = 66
    private const val G4 = 67
    private const val A4 = 69
    private const val B4 = 71
    private const val C5S = 73
    private const val D5 = 74
    private const val E5 = 76
    private const val F5S = 78
    private const val A5 = 81
    private const val D6 = 86

    private fun chord(vararg notes: Int) = DoubleArray(notes.size) { midi(notes[it]) }

    private fun smoothstep(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    fun voices(@Suppress("UNUSED_PARAMETER") script: HoOhScript, stretch: Double): List<Voice> {
        val s = stretch
        val v = ArrayList<Voice>()
        // The signal: a low pad swelling, golden bells high up.
        v += Pad(0.0, 5.0 * s, chord(D3, A3, D4, F4S), 0.08, 2.4 * s, 0.6 * s, 400.0, 1500.0)
        for ((i, pair) in listOf(0.5 to A5, 1.1 to F5S, 1.7 to D6, 2.6 to E5, 3.3 to A5, 4.0 to F5S).withIndex()) {
            v += Bell(pair.first * s, midi(pair.second), 0.045, if (i % 2 == 0) -0.4 else 0.4, 2.8, ratio = 3.5, index = 1.3)
        }
        // The rainbow forms: a harp rising over two octaves.
        val harp = intArrayOf(D4, E4, F4S, A4, B4, C5S, D5, E5, F5S, A5, D6)
        for ((i, note) in harp.withIndex()) v += Pluck((2.2 + i * 0.11) * s, midi(note), 0.07, -0.5 + i * 0.1, 0.9)
        v += NoiseSweep(2.0 * s, 2.4 * s, 800.0, 7000.0, 0.4, 0.06, rise = true, seed = 251)
        // Ho-Oh comes out of the light: the brass theme.
        val e = HoOhScript.EMERGE * s
        v += Boom(e, 80.0, 38.0, 0.35, 1.0)
        v += Pad(e, 3.8 * s, chord(D2, A2, D3, F3S, A3), 0.1, 0.3, 0.8, 700.0, 1800.0, width = 0.8)
        val theme = listOf(0.0 to D4, 0.5 to A4, 0.9 to D5, 1.6 to C5S, 1.9 to B4, 2.3 to A4, 2.9 to F5S)
        for ((at, note) in theme) v += Lead(e + at * s, 0.55 * s, midi(note), 0.06, 0.0)
        for ((at, note) in theme) v += Lead(e + at * s, 0.55 * s, midi(note - 12), 0.035, 0.25)
        v += Rise(6.4 * s, (HoOhScript.ZENITH - 6.4) * s, 180.0, 1.6, 0.04)
        // Its pass: a choir, a deep hit, bells.
        val z = HoOhScript.ZENITH * s
        v += Boom(z, 100.0, 34.0, 0.6, 1.4)
        v += NoiseSweep(z - 0.9 * s, 0.9 * s, 400.0, 6500.0, 0.45, 0.1, rise = true, seed = 257)
        v += Pad(z, 2.6 * s, chord(G3, B3, D4, G4, B4), 0.13, 0.25, 0.9 * s, 900.0, 2400.0, width = 0.9)
        for ((i, note) in intArrayOf(D5, F5S, A5, D6).withIndex()) v += Bell(z + i * 0.02, midi(note), 0.055, (i - 1.5) * 0.3, 3.6)
        for ((i, note) in intArrayOf(D4, F4S, A4).withIndex()) v += Lead(z + 0.05, 1.3 * s, midi(note), 0.045, (i - 1) * 0.3)
        // The pursuit begins: the theme settles on D.
        val f = 10.6 * s
        v += Pad(f, 2.6 * s, chord(D3, A3, D4, F4S, A4), 0.1, 0.5 * s, 1.2 * s, 700.0, 1400.0)
        for ((i, pair) in listOf(10.6 to A4, 11.0 to B4, 11.4 to D5, 12.0 to A5).withIndex()) {
            v += Lead(pair.first * s, (if (i == 3) 1.0 else 0.4) * s, midi(pair.second), 0.05, 0.1)
        }
        v += Bell(12.0 * s, midi(D6), 0.05, 0.2, 3.0)
        return v
    }

    fun music(script: HoOhScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(voices(script, stretch), 14.5 * stretch, offset)

    /** A high wind, swelling with the light; the rush of Ho-Oh's pass. */
    fun ambienceVoices(script: HoOhScript, stretch: Double): List<Voice> {
        val windL = StateFilter()
        val windR = StateFilter()
        val rushL = StateFilter()
        val rushR = StateFilter()
        val noiseL = Noise(501)
        val noiseR = Noise(509)
        val noiseRushL = Noise(519)
        val noiseRushR = Noise(521)
        val air = Bed(0.0, 14.0 * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val rush = script.gust(a) + 0.6 * script.trail(a) * smoothstep((a - 6.5) / 1.8) * (1.0 - smoothstep((a - 9.5) / 1.5))
            val sway = 0.5 + 0.5 * sin(2.0 * PI * 0.11 * t)
            windL.process(noiseL.next(), 380.0 + 300.0 * sway + 400.0 * script.glow(a), 0.7)
            windR.process(noiseR.next(), 420.0 + 300.0 * (1.0 - sway) + 400.0 * script.glow(a), 0.7)
            rushL.process(noiseRushL.next(), 700.0 + 2200.0 * rush, 0.5)
            rushR.process(noiseRushR.next(), 800.0 + 2200.0 * rush, 0.5)
            val g = smoothstep(t / 1.2) * (1.0 - smoothstep((t - 11.8) / 1.4))
            out[0] += ((windL.band * 0.08 + rushL.band * 0.22 * rush) * g).toFloat()
            out[1] += ((windR.band * 0.08 + rushR.band * 0.22 * rush) * g).toFloat()
        }
        return listOf(air)
    }

    fun ambience(script: HoOhScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(ambienceVoices(script, stretch), 14.0 * stretch, offset, reverbWet = 0.2, echoWet = 0.0)
}
