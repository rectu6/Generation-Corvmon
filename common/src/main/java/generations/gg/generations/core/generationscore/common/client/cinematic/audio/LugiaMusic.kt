package generations.gg.generations.core.generationscore.common.client.cinematic.audio

import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.LugiaScript
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lugia's music and ambience (pure), written for its 31 s; an original piece in E minor ending
 * in E major. The depths: a low drone, far bells, a slow rise. The anger of the oceans: chords
 * swelling (Em, C, D), drums of thunder, a low brass call. The appearance: a deep hit, the theme
 * on the brass over a wide pad, bells at the cry. Aeroblast: a rising tension (B7), the shot's
 * impact, then E major, bright. The meeting: a harp and a soft pad settling on E.
 * The ambience: the sea's rumble and the whirlpool, the wind and the gusts, the spray at the
 * burst, the rush of the beam.
 */
object LugiaMusic {
    private const val E1 = 28
    private const val B1 = 35
    private const val E2 = 40
    private const val G2 = 43
    private const val A2 = 45
    private const val B2 = 47
    private const val C3 = 48
    private const val D3 = 50
    private const val E3 = 52
    private const val F3S = 54
    private const val G3 = 55
    private const val G3S = 56
    private const val A3 = 57
    private const val B3 = 59
    private const val C4 = 60
    private const val D4 = 62
    private const val D4S = 63
    private const val E4 = 64
    private const val F4S = 66
    private const val G4 = 67
    private const val G4S = 68
    private const val A4 = 69
    private const val B4 = 71
    private const val D5 = 74
    private const val E5 = 76
    private const val G5 = 79
    private const val G5S = 80
    private const val B5 = 83
    private const val E6 = 88

    private fun chord(vararg notes: Int) = DoubleArray(notes.size) { midi(notes[it]) }

    private fun smoothstep(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    fun voices(script: LugiaScript, stretch: Double): List<Voice> {
        val s = stretch
        val v = ArrayList<Voice>()
        // The depths: a low drone, far bells, the slow rise toward the burst.
        v += Pad(0.0, 12.5 * s, chord(E1, B1, E2), 0.07, 3.5 * s, 1.0 * s, 180.0, 700.0, width = 0.4)
        v += Boom(0.4 * s, 55.0, 30.0, 0.3, 2.2)
        for ((at, note) in listOf(1.0 to E5, 2.3 to B4, 3.4 to G5, 4.6 to E5)) v += Bell(at * s, midi(note), 0.035, if (note == B4) -0.5 else 0.4, 3.6, ratio = 2.0, index = 1.1)
        v += Rise(3.0 * s, 9.0 * s, 110.0, 2.0, 0.03)
        // The anger of the oceans: Em, C, D swelling; thunder drums; a low brass call.
        v += Pad(5.0 * s, 2.6 * s, chord(E3, G3, B3, E4), 0.07, 1.2 * s, 0.6 * s, 500.0, 1300.0)
        v += Pad(7.4 * s, 2.4 * s, chord(C3, G3, C4, E4), 0.07, 0.6 * s, 0.6 * s, 600.0, 1500.0)
        v += Pad(9.6 * s, 2.6 * s, chord(D3, A3, D4, F4S), 0.08, 0.6 * s, 0.5 * s, 700.0, 1900.0)
        for (strike in script.strikes) if (strike[0] < LugiaScript.BURST) v += Boom(strike[0] * s, 70.0, 34.0, 0.22, 0.9)
        for ((at, note) in listOf(6.0 to E3, 6.6 to G3, 7.2 to B3, 8.4 to A3, 9.0 to G3, 9.6 to F3S, 10.6 to E3)) {
            v += Lead(at * s, 0.55 * s, midi(note), 0.04, -0.15)
        }
        v += NoiseSweep(10.4 * s, 1.6 * s, 300.0, 6000.0, 0.45, 0.07, rise = true, seed = 331)
        // The appearance: the hit, the theme over a wide pad.
        val b = LugiaScript.BURST * s
        v += Boom(b, 90.0, 30.0, 0.55, 1.6)
        v += Pad(b, 5.0 * s, chord(E2, B2, E3, G3, B3), 0.1, 0.25, 0.9 * s, 700.0, 2200.0, width = 0.8)
        val theme = listOf(0.6 to E4, 1.0 to B4, 1.4 to E5, 2.2 to D5, 2.6 to B4, 3.0 to G4, 3.6 to A4, 4.2 to B4)
        for ((at, note) in theme) v += Lead(b + at * s, 0.5 * s, midi(note), 0.055, 0.1)
        for ((at, note) in theme) v += Lead(b + at * s, 0.5 * s, midi(note - 12), 0.03, -0.25)
        // The cry: bells and a lift to C.
        val c = LugiaScript.CRY * s
        v += Boom(c, 80.0, 40.0, 0.3, 1.0)
        v += Pad(c, 3.2 * s, chord(C3, G3, C4, E4, G4), 0.09, 0.3, 0.8 * s, 900.0, 2400.0, width = 0.9)
        for ((i, note) in intArrayOf(E5, G5, B5, E6).withIndex()) v += Bell(c + i * 0.04, midi(note), 0.04, (i - 1.5) * 0.3, 3.4)
        // Aeroblast: tension on B7, the shot, then E major.
        val ch = LugiaScript.CHARGE * s
        val fire = LugiaScript.FIRE * s
        v += Pad(ch, fire - ch + 0.2 * s, chord(B2, D4S, F4S, A4), 0.07, 1.0 * s, 0.2, 600.0, 2600.0, width = 0.7)
        v += Rise(ch, fire - ch, 160.0, 1.8, 0.04)
        v += NoiseSweep(fire - 1.0 * s, 1.0 * s, 400.0, 7500.0, 0.4, 0.09, rise = true, seed = 337)
        v += Boom(fire, 100.0, 28.0, 0.65, 1.8)
        v += Pad(fire, 3.0 * s, chord(E2, B2, E3, G3S, B3, E4), 0.11, 0.15, 1.0 * s, 1000.0, 2600.0, width = 0.9)
        for ((i, note) in intArrayOf(E4, G4S, B4).withIndex()) v += Lead(fire + 0.05, 1.4 * s, midi(note), 0.04, (i - 1) * 0.3)
        for ((i, note) in intArrayOf(B5, G5S, E6).withIndex()) v += Bell(fire + 0.4 * s + i * 0.05, midi(note), 0.035, (i - 1) * 0.4, 3.0)
        // The meeting: a harp falling on E, a soft pad.
        val d = LugiaScript.DESCENT * s
        v += Pad(d, 5.0 * s, chord(E3, B3, E4, G4S), 0.075, 1.0 * s, 1.5 * s, 600.0, 1100.0)
        val harp = intArrayOf(E5, B4, G4S, E4, B3, G4S, B4, E5)
        for ((i, note) in harp.withIndex()) v += Pluck(d + (0.4 + i * 0.32) * s, midi(note), 0.06, -0.4 + i * 0.1, 0.9)
        v += Bell(LugiaScript.LANDED * s, midi(E5), 0.04, 0.0, 4.0)
        v += Bell(LugiaScript.LANDED * s + 0.05, midi(B5), 0.03, 0.3, 4.0)
        return v
    }

    fun music(script: LugiaScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(voices(script, stretch), (LugiaScript.END + 1.5) * stretch, offset)

    /** The sea and the whirlpool, the wind and its gusts, the spray of the burst, the beam's rush. */
    fun ambienceVoices(script: LugiaScript, stretch: Double): List<Voice> {
        val rumbleL = StateFilter()
        val rumbleR = StateFilter()
        val windL = StateFilter()
        val windR = StateFilter()
        val rushL = StateFilter()
        val rushR = StateFilter()
        val noiseA = Noise(601)
        val noiseB = Noise(607)
        val noiseC = Noise(613)
        val noiseD = Noise(617)
        val noiseE = Noise(619)
        val noiseF = Noise(631)
        val bed = Bed(0.0, (LugiaScript.END + 0.5) * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val swirl = script.swirl(a)
            val storm = script.storm(a)
            val sway = 0.5 + 0.5 * sin(2.0 * PI * 0.13 * t)
            val gust = storm * (0.5 + 0.5 * sin(2.0 * PI * 0.31 * t + 1.3)) * (0.6 + 0.4 * sway)
            val spray = script.column(a) + 0.6 * script.wall(a)
            val rush = script.beam(a) + 0.5 * script.charge(a)
            rumbleL.process(noiseA.next(), 70.0 + 50.0 * swirl, 0.8)
            rumbleR.process(noiseB.next(), 75.0 + 50.0 * swirl, 0.8)
            windL.process(noiseC.next(), 350.0 + 500.0 * gust + 300.0 * sway, 0.6)
            windR.process(noiseD.next(), 400.0 + 500.0 * gust + 300.0 * (1.0 - sway), 0.6)
            rushL.process(noiseE.next(), 900.0 + 3500.0 * (rush + 0.5 * spray), 0.5)
            rushR.process(noiseF.next(), 1000.0 + 3500.0 * (rush + 0.5 * spray), 0.5)
            val g = smoothstep(t / 1.5) * (1.0 - smoothstep((t - 29.0) / 2.0))
            val low = 0.5 * (0.3 + 0.7 * swirl)
            out[0] += ((rumbleL.low * low + windL.band * (0.04 + 0.14 * gust) + rushL.band * (0.12 * spray + 0.2 * rush)) * g).toFloat()
            out[1] += ((rumbleR.low * low + windR.band * (0.04 + 0.14 * gust) + rushR.band * (0.12 * spray + 0.2 * rush)) * g).toFloat()
        }
        return listOf(bed)
    }

    fun ambience(script: LugiaScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(ambienceVoices(script, stretch), (LugiaScript.END + 0.5) * stretch, offset, reverbWet = 0.22, echoWet = 0.0)
}
