package generations.gg.generations.core.generationscore.common.client.cinematic.audio

import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.MewScript
import kotlin.math.PI
import kotlin.math.sin

/**
 * Mew's music and ambience (pure, tested off-game), written for its 24 s and stretched with the
 * scene. A playful major mode (F Lydian): a celesta and bells while it waits, harp arpeggios and
 * a skipping tune for its flight, a rising shimmer through the whirlwind, a choir at the fusion, a
 * bright hit on its cry, then a soft close as it comes down. The ambience: a light wind and
 * sparkles, the whirlwind's rush.
 */
object MewMusic {
    private const val F2 = 41
    private const val C3 = 48
    private const val F3 = 53
    private const val G3 = 55
    private const val A3 = 57
    private const val B3 = 59
    private const val C4 = 60
    private const val D4 = 62
    private const val E4 = 64
    private const val F4 = 65
    private const val G4 = 67
    private const val A4 = 69
    private const val B4 = 71
    private const val C5 = 72
    private const val D5 = 74
    private const val E5 = 76
    private const val F5 = 77
    private const val G5 = 79
    private const val A5 = 81
    private const val C6 = 84
    private const val E6 = 88
    private const val F6 = 89

    private fun chord(vararg notes: Int) = DoubleArray(notes.size) { midi(notes[it]) }

    private fun smoothstep(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    fun voices(@Suppress("UNUSED_PARAMETER") script: MewScript, stretch: Double): List<Voice> {
        val s = stretch
        val v = ArrayList<Voice>()
        // Waiting: a soft pad, a celesta tune, a bell on its head tilt.
        v += Pad(0.0, 5.6 * s, chord(F3, A3, C4, E4), 0.08, 1.6 * s, 0.8 * s, 500.0, 1200.0)
        val waiting = listOf(0.4 to C5, 0.9 to E5, 1.4 to G5, 2.0 to F5, 2.6 to A5, 3.2 to G5, 3.8 to E5, 4.4 to C5)
        for ((i, pair) in waiting.withIndex()) v += Bell(pair.first * s, midi(pair.second), 0.05, if (i % 2 == 0) -0.3 else 0.3, 1.6, ratio = 4.0, index = 1.6)
        v += Bell(1.1 * s, midi(B4), 0.06, 0.0, 2.4)
        // Flight: harp arpeggios and a skipping tune.
        val harp = intArrayOf(F3, A3, C4, E4, G4, B4, C5, E5)
        var t = MewScript.FLIGHT
        var k = 0
        while (t < MewScript.CIRCLE_END - 0.05) {
            v += Pluck(t * s, midi(harp[k % harp.size] + if ((k / 8) % 2 == 1) 2 else 0), 0.06, if (k % 2 == 0) -0.35 else 0.35, 0.5)
            t += 0.125
            k++
        }
        v += Pad(MewScript.FLIGHT * s, 2.6 * s, chord(F2, C3, F3, A3), 0.08, 0.4, 0.6, 700.0, 1600.0)
        v += Pad(7.6 * s, 2.6 * s, chord(G3, B3, D4, F4), 0.08, 0.4, 0.6, 700.0, 1700.0)
        val tune = listOf(5.5 to C5, 5.75 to D5, 6.0 to E5, 6.5 to G5, 7.0 to F5, 7.25 to E5, 7.5 to D5, 8.0 to E5, 8.5 to G5, 9.0 to A5, 9.5 to G5)
        for ((at, note) in tune) v += Lead(at * s, 0.28 * s, midi(note), 0.045, 0.15)
        // The whirlwind: a rising shimmer, bells round in a circle.
        v += Pad(MewScript.CIRCLE_END * s, 6.6 * s, chord(F3, C4, G4, B4, E5), 0.09, 1.2 * s, 0.4, 600.0, 2600.0)
        v += Rise(11.0 * s, (MewScript.FUSION - 11.0) * s, 220.0, 2.0, 0.05)
        v += NoiseSweep(12.0 * s, (MewScript.FUSION - 12.0) * s, 600.0, 8000.0, 0.5, 0.12, rise = true, seed = 151)
        t = MewScript.ILLUSIONS
        k = 0
        val circle = intArrayOf(C5, E5, G5, B4, D5, F5)
        while (t < MewScript.FUSION - 0.1) {
            v += Bell(t * s, midi(circle[k % circle.size] + 12 * (k / 12)), 0.035 + 0.01 * (k % 2), (sin(k * 1.0)) * 0.6, 1.2, ratio = 4.0, index = 1.4)
            t += 0.42 - 0.18 * ((t - MewScript.ILLUSIONS) / (MewScript.FUSION - MewScript.ILLUSIONS)).coerceIn(0.0, 1.0)
            k++
        }
        // The fusion: a choir, a bright hit.
        val f = MewScript.FUSION * s
        v += Pad(f, 4.6 * s, chord(F3, C4, F4, A4, C5), 0.12, 0.3, 1.2 * s, 900.0, 2200.0, width = 0.9)
        for ((i, note) in intArrayOf(F5, A5, C6).withIndex()) v += Bell(f + i * 0.015, midi(note), 0.06, (i - 1) * 0.4, 3.0)
        v += Boom(f, 90.0, 40.0, 0.35, 0.8)
        // The cry.
        val c = MewScript.CRY * s
        v += Boom(c, 110.0, 36.0, 0.55, 1.2)
        v += NoiseSweep(c - 0.7 * s, 0.7 * s, 500.0, 7000.0, 0.45, 0.12, rise = true, seed = 157)
        for ((i, note) in intArrayOf(C6, E6, F6).withIndex()) v += Bell(c + i * 0.02, midi(note), 0.06, (i - 1) * 0.4, 3.4)
        for ((i, note) in intArrayOf(F4, A4, C5).withIndex()) v += Lead(c, 1.0 * s, midi(note), 0.05, (i - 1) * 0.3)
        // The descent: a soft close in F.
        v += Pad(20.6 * s, 3.4 * s, chord(F3, A3, C4, F4), 0.09, 0.8 * s, 1.6 * s, 600.0, 1200.0)
        for ((i, at) in doubleArrayOf(21.0, 21.4, 21.8, 22.2).withIndex()) {
            v += Bell(at * s, midi(intArrayOf(C5, A4, F4, C5)[i]), 0.05, if (i % 2 == 0) -0.2 else 0.2, 2.4, ratio = 4.0, index = 1.6)
        }
        v += Bell(MewScript.LANDING * s + 0.03, midi(F5), 0.05, 0.3, 3.0)
        return v
    }

    fun music(script: MewScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(voices(script, stretch), 25.5 * stretch, offset)

    /** A light wind and sparkles, the whirlwind's rush (in the scene's own time). */
    fun ambienceVoices(script: MewScript, stretch: Double): List<Voice> {
        val windL = StateFilter()
        val windR = StateFilter()
        val rushL = StateFilter()
        val rushR = StateFilter()
        val glint = StateFilter()
        val noiseL = Noise(401)
        val noiseR = Noise(409)
        val noiseRushL = Noise(419)
        val noiseRushR = Noise(421)
        val noiseGlint = Noise(431)
        val noiseClick = Noise(433)
        val air = Bed(0.0, 25.0 * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val whirl = script.whirl(a)
            val sway = 0.5 + 0.5 * sin(2.0 * PI * 0.13 * t)
            windL.process(noiseL.next(), 450.0 + 250.0 * sway, 0.7)
            windR.process(noiseR.next(), 520.0 + 250.0 * (1.0 - sway), 0.7)
            rushL.process(noiseRushL.next(), 900.0 + 1600.0 * whirl, 0.5)
            rushR.process(noiseRushR.next(), 1000.0 + 1600.0 * whirl, 0.5)
            val sparkle = script.energy(a) + script.trails(a) + script.sparkle(a) + 0.6 * whirl
            val click = if (noiseClick.next() > 1.0 - 0.0012 * sparkle) noiseGlint.next() * 2.5 else 0.0
            glint.process(click, 5200.0, 0.4)
            val g = fade(t)
            out[0] += ((windL.band * 0.07 + rushL.band * 0.16 * whirl + glint.high * 0.25) * g).toFloat()
            out[1] += ((windR.band * 0.07 + rushR.band * 0.16 * whirl + glint.high * 0.2) * g).toFloat()
        }
        return listOf(air)
    }

    fun ambience(script: MewScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(ambienceVoices(script, stretch), 25.0 * stretch, offset, reverbWet = 0.2, echoWet = 0.0)

    private fun fade(t: Double): Double = smoothstep(t / 1.2) * (1.0 - smoothstep((t - 22.6) / 1.6))
}
