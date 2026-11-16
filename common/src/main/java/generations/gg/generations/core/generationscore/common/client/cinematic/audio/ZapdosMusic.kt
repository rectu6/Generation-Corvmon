package generations.gg.generations.core.generationscore.common.client.cinematic.audio

import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ZapdosScript
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Zapdos's music and ambience (pure, tested off-game), written for its 20 s and stretched with
 * the scene. In E minor, driving (132 per minute): a dark drone and accelerating pulses on the
 * altar, an ostinato with drums and a rising storm in the orbit, hits on the giant bolt and the
 * burst, a fast chase with brass stabs, a hit on the wings' opening (on its real time, slow
 * motion included), then a short heroic theme that ends in E major as Zapdos lands. The
 * ambience: storm wind and rumble, an electric hum and crackles that follow the energy.
 */
object ZapdosMusic {
    private const val E2 = 40
    private const val G2 = 43
    private const val A2 = 45
    private const val B2 = 47
    private const val C3 = 48
    private const val D3 = 50
    private const val E3 = 52
    private const val FS3 = 54
    private const val G3 = 55
    private const val GS3 = 56
    private const val A3 = 57
    private const val B3 = 59
    private const val C4 = 60
    private const val D4 = 62
    private const val DS4 = 63
    private const val E4 = 64
    private const val F4 = 65
    private const val FS4 = 66
    private const val G4 = 67
    private const val B4 = 71
    private const val C5 = 72
    private const val D5 = 74
    private const val E5 = 76
    private const val FS5 = 78
    private const val G5 = 79
    private const val GS5 = 80
    private const val B5 = 83
    private const val E6 = 88
    private const val GS6 = 92
    private const val B6 = 95

    private const val BEAT = 60.0 / 132.0

    private fun chord(vararg notes: Int) = DoubleArray(notes.size) { midi(notes[it]) }

    private fun snare(v: MutableList<Voice>, at: Double, gain: Double, seed: Int) {
        v += NoiseSweep(at, 0.25, 3200.0, 1600.0, 0.8, gain, rise = false, mode = NoiseSweep.HIGH, seed = seed)
    }

    fun voices(script: ZapdosScript, stretch: Double): List<Voice> {
        val s = stretch
        val burst = ZapdosScript.BURST
        val spread = script.spreadReal
        val v = ArrayList<Voice>()
        // The altar: a drone, a dissonant pad, pulses closer and closer.
        v += Pad(0.0, 4.6 * s, chord(E2, B2), 0.13, 2.0 * s, 0.6 * s, 150.0, 400.0)
        v += Pad(0.5 * s, 4.2 * s, chord(E3, B3, F4), 0.1, 2.5 * s, 0.6 * s, 600.0, 1200.0)
        for (at in doubleArrayOf(0.0, 1.0, 1.75, 2.35, 2.85, 3.25, 3.6, 3.85)) v += Pluck(at * s, midi(E2), 0.12, 0.0, 0.3)
        v += Bell(1.4 * s, midi(E6), 0.05, -0.3, 2.0)
        v += Bell(2.9 * s, midi(B5), 0.05, 0.3, 2.0)
        // The orbit: ostinato, drums, a rising storm.
        val ostinato = intArrayOf(E2, E2, G2, E2, A2, E2, B2, D3)
        var t = 4.0
        var k = 0
        while (t < 9.0) {
            v += Pluck(t * s, midi(ostinato[k % 8]), 0.07 + 0.05 * (t - 4.0) / 5.0, if (k % 2 == 0) -0.2 else 0.2, 0.22)
            t += BEAT / 2
            k++
        }
        t = 4.0
        k = 0
        while (t < 9.0) {
            v += Boom(t * s, 70.0, 45.0, 0.32, 0.3)
            if (t >= 6.0) snare(v, (t + BEAT) * s, 0.12, 31 + k)
            t += BEAT * 2
            k++
        }
        v += Pad(4.0 * s, 1.9 * s, chord(E3, G3, B3), 0.14, 0.3, 0.3, 700.0, 1500.0)
        v += Pad(5.8 * s, 1.7 * s, chord(C3, G3, C4, E4), 0.14, 0.2, 0.3, 800.0, 1700.0)
        v += Pad(7.4 * s, 1.7 * s, chord(D3, A3, D4, FS4), 0.15, 0.2, 0.2, 900.0, 2200.0)
        v += Rise(4.5 * s, 4.5 * s, 90.0, 2.0, 0.1)
        v += NoiseSweep(6.5 * s, 2.5 * s, 300.0, 6000.0, 0.5, 0.16, rise = true, seed = 37)
        // The giant bolt, the swell into the burst.
        val bolt = ZapdosScript.GIANT_BOLT * s
        v += Boom(bolt, 85.0, 32.0, 0.7, 1.0)
        v += NoiseSweep(bolt, 1.8 * s, 7000.0, 2500.0, 0.7, 0.16, rise = false, mode = NoiseSweep.HIGH, seed = 41)
        v += NoiseSweep(bolt + 0.05, (burst - ZapdosScript.GIANT_BOLT - 0.05) * s, 400.0, 8000.0, 0.4, 0.22, rise = true, seed = 43)
        // The burst.
        val b = burst * s
        v += Boom(b, 110.0, 28.0, 0.95, 1.6)
        v += Boom(b, 55.0, 27.0, 0.5, 2.2)
        v += NoiseSweep(b, 2.6 * s, 8000.0, 3000.0, 0.6, 0.22, rise = false, mode = NoiseSweep.HIGH, seed = 47)
        for ((i, note) in intArrayOf(E4, B4, E5).withIndex()) v += Lead(b, 0.5 * s, midi(note), 0.09 - 0.01 * i, (i - 1) * 0.3)
        for ((i, note) in intArrayOf(E5, B5, E6).withIndex()) v += Bell(b + i * 0.01, midi(note), 0.07, (i - 1) * 0.4, 3.0)
        // The chase.
        t = 9.9
        k = 0
        while (t < 15.0) {
            if (t < spread - 0.25 || t > spread + 0.15) v += Pluck(t * s, midi(if (k % 2 == 0) E2 else E3), 0.06, if (k % 2 == 0) -0.15 else 0.15, 0.12)
            t += BEAT / 4
            k++
        }
        t = 9.9
        k = 0
        while (t < 15.0) {
            if (t < spread - 0.2 || t > spread + 0.2) {
                v += Boom(t * s, 72.0, 46.0, 0.25, 0.3)
                if (k % 2 == 1) snare(v, t * s, 0.11, 61 + k)
            }
            t += BEAT
            k++
        }
        v += Pad(9.9 * s, 1.2 * s, chord(E3, G3, B3), 0.14, 0.1, 0.2, 900.0, 1800.0)
        v += Pad(11.0 * s, 1.1 * s, chord(C3, G3, C4, E4), 0.14, 0.1, 0.2, 900.0, 1900.0)
        v += Pad(12.0 * s, (spread - 12.0) * s, chord(D3, A3, D4, FS4), 0.15, 0.1, 0.1, 1000.0, 2400.0)
        for (at in doubleArrayOf(10.6, 11.55, 13.45, 14.4)) for ((i, note) in intArrayOf(E3, B3, E4).withIndex()) v += Lead(at * s, 0.25 * s, midi(note), 0.07, (i - 1) * 0.3)
        v += Rise(12.6 * s, 2.4 * s, 100.0, 2.0, 0.1)
        // The wings open.
        val w = spread * s
        v += Boom(w, 100.0, 30.0, 0.85, 1.4)
        v += NoiseSweep(w, 2.0 * s, 7500.0, 2800.0, 0.6, 0.2, rise = false, mode = NoiseSweep.HIGH, seed = 53)
        for ((i, note) in intArrayOf(C4, E4, G4, C5).withIndex()) v += Lead(w, 0.7 * s, midi(note), 0.07, (i - 1.5) * 0.25)
        v += Pad(w, (15.0 - spread) * s, chord(C3, G3, C4, E4, G4), 0.14, 0.05, 0.3, 2400.0, 1200.0)
        // The heroic theme, ending in E major.
        v += Pad(15.0 * s, 1.25 * s, chord(E3, B3, E4, G4), 0.16, 0.2, 0.2, 900.0, 1600.0)
        v += Pad(16.2 * s, 0.85 * s, chord(C3, G3, C4, E4), 0.16, 0.08, 0.15, 1100.0, 1800.0)
        v += Pad(17.0 * s, 0.95 * s, chord(D3, A3, D4, FS4), 0.16, 0.08, 0.15, 1200.0, 2000.0)
        v += Pad(17.9 * s, 0.55 * s, chord(B2, FS3, B3, DS4), 0.16, 0.06, 0.12, 1400.0, 2400.0)
        v += Pad(18.4 * s, 3.0 * s, chord(E3, GS3, B3, E4, GS5 - 12), 0.18, 0.1, 1.6 * s, 2600.0, 700.0)
        for ((at, note) in listOf(15.0 to E2, 16.2 to C3, 17.0 to D3, 17.9 to B2, 18.4 to E2)) v += Pluck(at * s, midi(note), 0.18, 0.0, 1.0)
        val melody = listOf(
            Triple(15.2, B4, 0.4), Triple(15.6, E5, 0.55), Triple(16.2, D5, 0.3), Triple(16.5, E5, 0.3),
            Triple(16.8, G5, 0.55), Triple(17.4, FS5, 0.45), Triple(17.9, FS5, 0.45), Triple(18.4, GS5, 1.8),
        )
        for ((at, note, length) in melody) {
            v += Lead(at * s, length * s, midi(note), 0.12, 0.1)
            v += Lead(at * s, length * s, midi(note - 12), 0.05, -0.1)
        }
        for ((at, note) in listOf(18.4 to E6, 18.6 to GS6, 18.85 to B6)) v += Bell(at * s, midi(note), 0.07, 0.25, 3.0)
        v += NoiseSweep(18.4 * s, 2.0 * s, 3000.0, 9000.0, 0.3, 0.05, rise = false, mode = NoiseSweep.HIGH, seed = 59)
        return v
    }

    fun music(script: ZapdosScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(voices(script, stretch), 21.5 * stretch, offset)

    /** Storm wind and rumble, the hum and the crackles of the energy (in the scene's own time). */
    fun ambienceVoices(script: ZapdosScript, stretch: Double): List<Voice> {
        val windL = StateFilter()
        val windR = StateFilter()
        val low = StateFilter()
        val humFilter = StateFilter()
        val crackle = StateFilter()
        val noiseL = Noise(201)
        val noiseR = Noise(203)
        val noiseLow = Noise(207)
        val noiseCrackle = Noise(209)
        val noiseClick = Noise(211)
        var phase1 = 0.0
        var phase2 = 0.0
        val storm = Bed(0.0, 21.0 * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val clouds = script.cloudOpacity(a)
            var strikes = 0.0
            for (strike in script.strikes) if (a >= strike[0]) strikes += exp(-(a - strike[0]) / 0.8)
            val level = (0.08 + 0.22 * clouds) * fade(t)
            val cutoff = 300.0 + 400.0 * clouds + 150.0 * sin(t * 0.8)
            windL.process(noiseL.next(), cutoff, 0.7)
            windR.process(noiseR.next(), cutoff * 1.1, 0.7)
            low.process(noiseLow.next(), 60.0, 0.6)
            val rumble = low.low * (0.3 * clouds + 0.5 * strikes.coerceAtMost(1.5) + 0.6 * burst(a)) * 2.4 * fade(t)
            out[0] += (windL.band * level + rumble).toFloat()
            out[1] += (windR.band * level + rumble).toFloat()
        }
        val energy = Bed(0.0, 21.0 * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val density = (script.sparks(a) + script.sphereLight(a) / 2.5 + 0.6 * script.energy(a)).coerceIn(0.0, 1.5)
            // A buzzing hum (two saws), trembling.
            phase1 = (phase1 + 55.0 / SAMPLE_RATE) % 1.0
            phase2 = (phase2 + 110.3 / SAMPLE_RATE) % 1.0
            humFilter.process((2.0 * phase1 - 1.0) * 0.6 + (2.0 * phase2 - 1.0) * 0.4, 500.0 + 400.0 * density, 0.8)
            val tremolo = 0.75 + 0.25 * sin(2.0 * PI * 8.0 * t)
            val hum = humFilter.low * 0.14 * density * tremolo
            // Crackles: sparse clicks, bright.
            val click = if (noiseClick.next() > 1.0 - 0.0016 * density) noiseCrackle.next() * 3.0 else 0.0
            crackle.process(click, 3500.0, 0.5)
            val c = crackle.high * 0.35 * density
            val g = fade(t)
            out[0] += ((hum + c * 0.8) * g).toFloat()
            out[1] += ((hum + c * 1.2) * g).toFloat()
        }
        return listOf(storm, energy)
    }

    fun ambience(script: ZapdosScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(ambienceVoices(script, stretch), 21.0 * stretch, offset, reverbWet = 0.12, echoWet = 0.0)

    private fun burst(a: Double): Double = if (a < ZapdosScript.BURST) 0.0 else exp(-(a - ZapdosScript.BURST))

    private fun smoothstep(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    private fun fade(t: Double): Double = smoothstep(t / 1.5) * (1.0 - smoothstep((t - 18.8) / 1.8))
}
