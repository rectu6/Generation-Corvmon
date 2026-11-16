package generations.gg.generations.core.generationscore.common.client.cinematic.audio

import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.MoltresScript
import kotlin.math.exp
import kotlin.math.sin

/**
 * Moltres's music and ambience (pure, tested off-game), written for its 20 s and stretched with
 * the scene. In C minor, warm (92 per minute): a drone and warm bells while the altar glows, a
 * rising arpeggio and drums along the column, a hit on the burst from the top (on its real time,
 * slow motion included) with a bright choir in A flat, a soaring theme for the circle, then a
 * calm descent that ends in C major as Moltres lands. The ambience: the fire's roar, its
 * crackles and a low rumble.
 */
object MoltresMusic {
    private const val C2 = 36
    private const val F2 = 41
    private const val G2 = 43
    private const val AB2 = 44
    private const val BB2 = 46
    private const val B2 = 47
    private const val C3 = 48
    private const val D3 = 50
    private const val EB3 = 51
    private const val F3 = 53
    private const val G3 = 55
    private const val AB3 = 56
    private const val BB3 = 58
    private const val C4 = 60
    private const val D4 = 62
    private const val EB4 = 63
    private const val E4 = 64
    private const val F4 = 65
    private const val G4 = 67
    private const val AB4 = 68
    private const val BB4 = 70
    private const val C5 = 72
    private const val D5 = 74
    private const val EB5 = 75
    private const val E5 = 76
    private const val F5 = 77
    private const val G5 = 79
    private const val AB5 = 80
    private const val C6 = 84
    private const val EB6 = 87
    private const val E6 = 88
    private const val G6 = 91

    private const val BEAT = 60.0 / 92.0

    private fun chord(vararg notes: Int) = DoubleArray(notes.size) { midi(notes[it]) }

    /** A warmer bell than Articuno's (lower ratio, softer strike). */
    private fun warmBell(at: Double, note: Int, gain: Double, pan: Double, decay: Double = 3.0) = Bell(at, midi(note), gain, pan, decay, ratio = 2.0, index = 1.8)

    fun voices(script: MoltresScript, stretch: Double): List<Voice> {
        val s = stretch
        val burst = script.burstReal
        val v = ArrayList<Voice>()
        // The glowing altar.
        v += Pad(0.0, 4.6 * s, chord(C2, G2), 0.13, 2.2 * s, 0.5 * s, 160.0, 380.0)
        v += Pad(0.3 * s, 4.3 * s, chord(C3, EB3, G3), 0.11, 2.4 * s, 0.6 * s, 500.0, 1100.0)
        listOf(0.6 to G5, 1.5 to EB5, 2.3 to C5, 3.1 to G5, 3.7 to BB4).forEachIndexed { i, (at, note) ->
            v += warmBell(at * s, note, 0.07, if (i % 2 == 0) -0.35 else 0.35)
        }
        // The column: a rising arpeggio over C minor, A flat, B flat, G.
        val chords = listOf(
            Triple(4.0, intArrayOf(C3, EB3, G3, C4), chord(C3, EB3, G3)),
            Triple(5.3, intArrayOf(AB2, C3, EB3, AB3), chord(AB2, EB3, AB3, C4)),
            Triple(6.6, intArrayOf(BB2, D3, F3, BB3), chord(BB2, F3, BB3, D4)),
            Triple(7.9, intArrayOf(G2, B2, D3, G3), chord(G2, D3, G3, B2 + 12)),
        )
        for ((index, entry) in chords.withIndex()) {
            val (start, arpeggio, pad) = entry
            val end = if (index + 1 < chords.size) chords[index + 1].first else 9.0
            v += Pad(start * s, (end - start + 0.1) * s, pad, 0.14, 0.25, 0.2, 700.0 + 300.0 * index, 1500.0 + 400.0 * index)
            var t = start
            var k = 0
            while (t < end) {
                val octave = if (k >= 4) 12 else 0
                v += Pluck(t * s, midi(arpeggio[k % 4] + octave + 12 * (index / 2)), 0.06 + 0.012 * index, (k % 4 - 1.5) / 3.0, 0.3)
                t += BEAT / 2
                k++
            }
        }
        v += Rise(5.0 * s, 4.0 * s, 85.0, 2.0, 0.1)
        v += NoiseSweep(7.0 * s, 2.0 * s, 300.0, 6000.0, 0.5, 0.16, rise = true, seed = 71)
        var t = 6.0
        while (t < 9.0) {
            v += Boom(t * s, 70.0, 45.0, 0.3, 0.3)
            t += BEAT
        }
        // The silhouette, the swell into the burst.
        val sil = MoltresScript.SILHOUETTE * s
        v += Boom(sil, 70.0, 30.0, 0.5, 1.4)
        v += Pad(sil, (burst - MoltresScript.SILHOUETTE) * s, chord(F2, C3, F3, AB3), 0.15, 0.1, 0.3, 1200.0, 800.0)
        v += NoiseSweep(sil + 0.2 * s, (burst - MoltresScript.SILHOUETTE - 0.2) * s, 300.0, 8000.0, 0.4, 0.22, rise = true, seed = 73)
        v += Rise(sil, (burst - MoltresScript.SILHOUETTE) * s, 110.0, 1.5, 0.08)
        // The burst: a bright choir in A flat.
        val b = burst * s
        v += Boom(b, 100.0, 28.0, 0.95, 1.8)
        v += Boom(b, 55.0, 27.0, 0.5, 2.4)
        v += NoiseSweep(b, 3.0 * s, 8000.0, 3000.0, 0.6, 0.22, rise = false, mode = NoiseSweep.HIGH, seed = 79)
        v += Pad(b, 1.6 * s, chord(AB3, C4, EB4, AB4), 0.2, 0.05, 0.6 * s, 3000.0, 1400.0)
        listOf(AB5, C6, EB6).forEachIndexed { i, note -> v += warmBell(b + i * 0.012, note, 0.08, (i - 1) * 0.4, 4.0) }
        // The circle: a soaring theme over A flat, E flat, B flat, C minor.
        val flight = listOf(
            Triple(11.0, AB2, chord(AB2, EB3, AB3, C4)), Triple(12.3, EB3, chord(EB3, G3, BB3, EB4)),
            Triple(13.6, BB2, chord(BB2, F3, BB3, D4)), Triple(14.9, C3, chord(C3, G3, C4, EB4)),
        )
        for ((index, entry) in flight.withIndex()) {
            val (start, root, pad) = entry
            val end = if (index + 1 < flight.size) flight[index + 1].first else 15.6
            v += Pad(start * s, (end - start + 0.1) * s, pad, 0.15, 0.15, 0.15, 1000.0, 2000.0)
            var at = start
            var k = 0
            while (at < end) {
                v += Pluck(at * s, midi(root - 12 + (if (k % 2 == 1) 12 else 0)), 0.07, if (k % 2 == 0) -0.15 else 0.15, 0.25)
                at += BEAT / 2
                k++
            }
        }
        t = 11.0
        while (t < 15.0) {
            v += Boom(t * s, 72.0, 46.0, 0.26, 0.32)
            t += BEAT
        }
        val theme = listOf(
            Triple(11.0, G4, 0.6), Triple(11.65, C5, 0.6), Triple(12.3, EB5, 0.9), Triple(13.25, D5, 0.3),
            Triple(13.6, F5, 0.9), Triple(14.5, EB5, 0.4), Triple(14.9, D5, 0.6),
        )
        for ((at, note, length) in theme) {
            v += Lead(at * s, length * s, midi(note), 0.11, 0.1)
            v += Lead(at * s, length * s, midi(note - 12), 0.045, -0.1)
        }
        // The calm descent, ending in C major.
        v += Pad(15.5 * s, 1.15 * s, chord(AB2, EB3, AB3, C4), 0.15, 0.4, 0.2, 800.0, 1400.0)
        v += Pad(16.6 * s, 1.05 * s, chord(BB2, F3, BB3, D4), 0.15, 0.1, 0.15, 1000.0, 1600.0)
        v += Pad(17.6 * s, 0.85 * s, chord(G2, D3, G3, B2 + 12, F4), 0.15, 0.08, 0.12, 1200.0, 2000.0)
        v += Pad(18.4 * s, 3.0 * s, chord(C3, G3, C4, E4, G4), 0.18, 0.1, 1.6 * s, 2400.0, 700.0)
        for ((at, note) in listOf(15.5 to AB2, 16.6 to BB2, 17.6 to G2, 18.4 to C3)) v += Pluck(at * s, midi(note - 12), 0.18, 0.0, 1.1)
        val ending = listOf(Triple(16.6, D5, 0.5), Triple(17.1, EB5, 0.5), Triple(17.6, D5, 0.75), Triple(18.4, E5, 1.9))
        for ((at, note, length) in ending) {
            v += Lead(at * s, length * s, midi(note), 0.11, 0.1)
            v += Lead(at * s, length * s, midi(note - 12), 0.045, -0.1)
        }
        listOf(18.4 to C6, 18.6 to E6, 18.85 to G6).forEach { (at, note) -> v += warmBell(at * s, note, 0.07, 0.25) }
        v += NoiseSweep(18.4 * s, 2.0 * s, 3000.0, 9000.0, 0.3, 0.05, rise = false, mode = NoiseSweep.HIGH, seed = 83)
        return v
    }

    fun music(script: MoltresScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(voices(script, stretch), 21.5 * stretch, offset)

    /** The fire's roar and crackles, a low rumble (in the scene's own time). */
    fun ambienceVoices(script: MoltresScript, stretch: Double): List<Voice> {
        val roarL = StateFilter()
        val roarR = StateFilter()
        val low = StateFilter()
        val pops = StateFilter()
        val noiseL = Noise(301)
        val noiseR = Noise(303)
        val noiseLow = Noise(307)
        val noisePop = Noise(309)
        val noiseClick = Noise(311)
        val fire = Bed(0.0, 21.0 * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val column = script.column(a)
            val level = (0.12 * script.altarGlow(a) + 0.35 * column + 0.25 * script.trail(a) + 0.15 * script.emberRain(a)) * fade(t)
            val flicker = 0.7 + 0.3 * sin(t * 11.0) * sin(t * 4.3 + 1.0)
            val cutoff = 280.0 + 520.0 * column + 120.0 * sin(t * 2.1)
            roarL.process(noiseL.next(), cutoff, 0.6)
            roarR.process(noiseR.next(), cutoff * 1.12, 0.6)
            low.process(noiseLow.next(), 55.0, 0.6)
            val burst = if (a < MoltresScript.BURST) 0.0 else exp(-(a - MoltresScript.BURST))
            val rumble = low.low * (0.3 * column + 0.6 * burst) * 2.4 * fade(t)
            val density = (script.altarGlow(a) * 0.6 + column).coerceIn(0.0, 1.4)
            val click = if (noiseClick.next() > 1.0 - 0.0022 * density) noisePop.next() * 2.5 else 0.0
            pops.process(click, 1500.0, 0.6)
            val c = pops.band * 0.4 * density * fade(t)
            out[0] += (roarL.band * level * flicker + rumble + c * 0.9).toFloat()
            out[1] += (roarR.band * level * flicker + rumble + c * 1.1).toFloat()
        }
        return listOf(fire)
    }

    fun ambience(script: MoltresScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(ambienceVoices(script, stretch), 21.0 * stretch, offset, reverbWet = 0.12, echoWet = 0.0)

    private fun smoothstep(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    private fun fade(t: Double): Double = smoothstep(t / 1.5) * (1.0 - smoothstep((t - 18.8) / 1.8))
}
