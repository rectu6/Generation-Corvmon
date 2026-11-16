package generations.gg.generations.core.generationscore.common.client.cinematic.audio

import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ArticunoScript
import kotlin.math.exp
import kotlin.math.sin

/**
 * Articuno's music and ambience (pure, tested off-game), written for its 20 s and stretched with
 * the scene (stretch = duration / 20). In D minor: icy bells and a pad while the altar wakes, a
 * pulse and a rising wind in the vortex, a hit on the silhouette, tension and accelerating drums
 * down to the burst (on the burst's real time, slow motion included), then a short heroic theme
 * that ends in D major as Articuno lands.
 */
object ArticunoMusic {
    private const val D2 = 38
    private const val A2 = 45
    private const val BB2 = 46
    private const val C3 = 48
    private const val D3 = 50
    private const val F3 = 53
    private const val G3 = 55
    private const val A3 = 57
    private const val BB3 = 58
    private const val C4 = 60
    private const val D4 = 62
    private const val E4 = 64
    private const val F4 = 65
    private const val FS4 = 66
    private const val A4 = 69
    private const val C5 = 72
    private const val D5 = 74
    private const val E5 = 76
    private const val F5 = 77
    private const val FS5 = 78
    private const val G5 = 79
    private const val A5 = 81
    private const val D6 = 86
    private const val E6 = 88
    private const val FS6 = 90
    private const val A6 = 93

    private fun chord(vararg notes: Int) = DoubleArray(notes.size) { midi(notes[it]) }

    fun voices(script: ArticunoScript, stretch: Double): List<Voice> {
        val s = stretch
        val hit = script.explosionReal
        val v = ArrayList<Voice>()
        // The altar wakes.
        v += Pad(0.0, 4.9 * s, chord(D3, A3, E4, F4), 0.16, 2.2 * s, 0.8 * s, 400.0, 1100.0)
        v += Pad(0.0, 9.2 * s, chord(D2, A2), 0.12, 3.0 * s, 0.4 * s, 180.0, 420.0)
        listOf(0.35 to A5, 1.05 to E6, 1.6 to D6, 2.45 to F5, 3.2 to A5, 3.9 to E6).forEachIndexed { i, (t, note) ->
            v += Bell(t * s, midi(note), 0.09, if (i % 2 == 0) -0.4 else 0.4, 2.4)
        }
        // The vortex.
        v += Pad(4.4 * s, 2.6 * s, chord(BB2, F3, A3, D4), 0.15, 0.8 * s, 0.6 * s, 500.0, 1400.0)
        v += Pad(6.6 * s, 2.5 * s, chord(43, D3, F3, A3, BB3), 0.16, 0.5 * s, 0.2 * s, 700.0, 2200.0)
        val ostinato = intArrayOf(D3, D3, A3, D3, F3, D3, A3, C4)
        var t = 4.6
        var k = 0
        while (t < 9.0) {
            val g = 0.05 + 0.07 * ((t - 4.6) / 4.4)
            v += Pluck(t * s, midi(ostinato[k % ostinato.size]), g, if (k % 2 == 0) -0.2 else 0.2)
            t += 1.0 / 3.0
            k++
        }
        v += NoiseSweep(5.6 * s, 3.4 * s, 300.0, 5000.0, 0.5, 0.18, rise = true, seed = 11)
        v += Rise(6.0 * s, 3.0 * s, 110.0, 1.5, 0.08)
        val arpeggio = intArrayOf(D5, F5, A5, D6)
        t = 6.8
        k = 0
        while (t < 9.0) {
            v += Bell(t * s, midi(arpeggio[k % 4] + 12 * ((k / 8) % 2)), 0.05, (k % 4 - 1.5) / 3.0, 0.9)
            t += 1.0 / 6.0
            k++
        }
        // The silhouette.
        val reveal = ArticunoScript.REVEAL * s
        v += Boom(reveal, 95.0, 34.0, 0.75, 1.3)
        v += NoiseSweep(reveal, 2.2 * s, 6000.0, 2500.0, 0.7, 0.16, rise = false, mode = NoiseSweep.HIGH, seed = 13)
        v += Bell(reveal, midi(D6), 0.12, -0.2, 3.0)
        v += Bell(reveal, midi(A6), 0.07, 0.3, 3.2)
        v += Pad(reveal, (hit - ArticunoScript.REVEAL) * s, chord(D3, F3, A3, C4, E4), 0.18, 0.15, 0.3, 1800.0, 900.0)
        // Tension, accelerating drums, the swell into the burst.
        v += Rise(9.4 * s, (hit - 9.4) * s, 80.0, 2.0, 0.1)
        for (at in doubleArrayOf(11.0, 12.0, 12.6, hit - 0.55, hit - 0.3, hit - 0.12)) v += Boom(at * s, 70.0, 48.0, 0.35, 0.4)
        v += NoiseSweep(11.0 * s, (hit - 11.0) * s, 200.0, 7000.0, 0.4, 0.2, rise = true, seed = 17)
        t = 9.0
        k = 0
        while (t < hit - 0.3) {
            v += Pluck(t * s, midi(if (k % 4 == 3) F3 else D3), 0.07, if (k % 2 == 0) -0.15 else 0.15, 0.2)
            t += 1.0 / 6.0
            k++
        }
        // The burst.
        val burst = hit * s
        v += Boom(burst, 110.0, 30.0, 0.95, 1.8)
        v += Boom(burst, 60.0, 28.0, 0.5, 2.5)
        v += NoiseSweep(burst, 3.0 * s, 8000.0, 3000.0, 0.6, 0.22, rise = false, mode = NoiseSweep.HIGH, seed = 19)
        listOf(D5, F5, A5, D6, E6).forEachIndexed { i, note -> v += Bell(burst + i * 0.012, midi(note), 0.09, (i - 2) * 0.3, 4.0) }
        v += Pad(burst, 1.8 * s, chord(BB2, F3, BB3, D4, F4), 0.2, 0.05, 0.6 * s, 2500.0, 900.0)
        // The heroic theme.
        v += Pad(15.0 * s, 1.4 * s, chord(D3, A3, D4, F4), 0.17, 0.25, 0.2, 900.0, 1600.0)
        v += Pad(16.33 * s, 0.75 * s, chord(BB2, F3, BB3, D4), 0.17, 0.08, 0.15, 1200.0, 1800.0)
        v += Pad(17.0 * s, 1.05 * s, chord(C3, G3, C4, E4), 0.17, 0.08, 0.15, 1300.0, 2200.0)
        v += Pad(18.0 * s, 3.0 * s, chord(D3, A3, D4, FS4, A4), 0.18, 0.1, 1.6 * s, 2400.0, 700.0)
        for ((at, note) in listOf(15.0 to 26, 16.33 to 34, 17.0 to 36, 18.0 to D2)) v += Pluck(at * s, midi(note + 12), 0.18, 0.0, 1.1)
        val melody = listOf(Triple(15.05, D5, 0.55), Triple(15.6, A4, 0.4), Triple(16.0, D5, 0.3), Triple(16.33, F5, 0.62), Triple(17.0, E5, 0.45), Triple(17.5, G5, 0.45), Triple(18.0, FS5, 1.9))
        for ((at, note, length) in melody) {
            v += Lead(at * s, length * s, midi(note), 0.12, 0.1)
            v += Lead(at * s, length * s, midi(note - 12), 0.05, -0.1)
        }
        listOf(18.0 to D6, 18.2 to FS6, 18.45 to A6).forEach { (at, note) -> v += Bell(at * s, midi(note), 0.07, 0.25, 3.0) }
        v += NoiseSweep(18.0 * s, 2.0 * s, 3000.0, 9000.0, 0.3, 0.05, rise = false, mode = NoiseSweep.HIGH, seed = 23)
        return v
    }

    fun music(script: ArticunoScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(voices(script, stretch), 21.5 * stretch, offset)

    /** The wind and the rumble, following the vortex (in the scene's own time). */
    fun ambienceVoices(script: ArticunoScript, stretch: Double): List<Voice> {
        val windL = StateFilter()
        val windR = StateFilter()
        val whistle = StateFilter()
        val low1 = StateFilter()
        val noiseL = Noise(101)
        val noiseR = Noise(103)
        val noiseW = Noise(107)
        val noiseLow = Noise(109)
        val wind = Bed(0.0, 21.0 * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val vortex = script.vortex(a)
            val clear = 1.0 - 0.8 * smoothstep((a - 16.0) / 2.0)
            val gust = maxOf(0.0, sin(t * 1.3) * sin(t * 0.37 + 1.0)) * 0.08
            val level = (0.10 + 0.22 * smoothstep((t - 3.0) / 3.0) + 0.25 * vortex + gust) * clear * fade(t)
            val cutoff = 380.0 + 900.0 * vortex + 260.0 * sin(t * 0.9)
            windL.process(noiseL.next(), cutoff, 0.7)
            windR.process(noiseR.next(), cutoff * 1.12, 0.7)
            whistle.process(noiseW.next(), 900.0 + 700.0 * sin(t * 0.7) + 300.0 * sin(t * 2.3), 0.08)
            val w = whistle.band * 0.12 * vortex * clear
            out[0] += ((windL.band * level + w) * 0.9).toFloat()
            out[1] += ((windR.band * level + w) * 0.9).toFloat()
        }
        val rumble = Bed(0.0, 21.0 * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val level = (0.35 * script.vortex(a) + 0.4 * (if (a < ArticunoScript.EXPLOSION) 0.0 else exp(-(a - ArticunoScript.EXPLOSION)))) * fade(t)
            low1.process(noiseLow.next(), 70.0, 0.6)
            val s = low1.low * level * 2.2
            out[0] += s.toFloat()
            out[1] += s.toFloat()
        }
        return listOf(wind, rumble)
    }

    fun ambience(script: ArticunoScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(ambienceVoices(script, stretch), 21.0 * stretch, offset, reverbWet = 0.12, echoWet = 0.0)

    private fun smoothstep(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    /** In at the start, out over the last second and a half. */
    private fun fade(t: Double): Double = smoothstep(t / 1.5) * (1.0 - smoothstep((t - 18.8) / 1.8))
}
