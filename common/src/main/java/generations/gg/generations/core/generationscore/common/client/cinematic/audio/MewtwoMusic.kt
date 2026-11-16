package generations.gg.generations.core.generationscore.common.client.cinematic.audio

import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.MewtwoScript
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Mewtwo's music and ambience (pure, tested off-game), written for its 24 s and stretched with
 * the scene. In D minor: a low drone and heartbeats through the glass; the alarm with a two-tone
 * siren, an accelerating ostinato and a rising tension; a huge hit on the breach (on its real
 * time, slow motion included); a dark, wide theme as Mewtwo comes out, a hit on its cry; then D
 * major as it lands. The ambience: the tank's bubbling and hum, the arcs' crackles, a deep
 * psychic rumble after the breach.
 */
object MewtwoMusic {
    private const val D2 = 38
    private const val F2 = 41
    private const val G2 = 43
    private const val A2 = 45
    private const val AS2 = 46
    private const val C3 = 48
    private const val D3 = 50
    private const val E3 = 52
    private const val F3 = 53
    private const val FS3 = 54
    private const val G3 = 55
    private const val A3 = 57
    private const val AS3 = 58
    private const val C4 = 60
    private const val D4 = 62
    private const val E4 = 64
    private const val F4 = 65
    private const val FS4 = 66
    private const val G4 = 67
    private const val A4 = 69
    private const val AS4 = 70
    private const val C5 = 72
    private const val D5 = 74
    private const val FS5 = 78
    private const val A5 = 81
    private const val D6 = 86

    private fun chord(vararg notes: Int) = DoubleArray(notes.size) { midi(notes[it]) }

    private fun smoothstep(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    fun voices(script: MewtwoScript, stretch: Double): List<Voice> {
        val s = stretch
        val breakReal = script.slowMotion.realOf(MewtwoScript.BREAK)
        val cryReal = script.slowMotion.realOf(MewtwoScript.CRY)
        val v = ArrayList<Voice>()
        // The awakening: a drone, heartbeats, a shimmer on the first pulse.
        v += Pad(0.0, 4.6 * s, chord(D2, A2), 0.12, 2.2 * s, 0.8 * s, 140.0, 320.0)
        v += Pad(0.8 * s, 3.8 * s, chord(D3, F3, A3), 0.07, 2.0 * s, 0.8 * s, 400.0, 900.0)
        for (at in doubleArrayOf(1.2, 2.0, 2.7, 3.25, 3.7)) {
            v += Boom(at * s, 62.0, 40.0, 0.36, 0.25)
            v += Boom((at + 0.22) * s, 55.0, 38.0, 0.22, 0.22)
        }
        v += Bell(MewtwoScript.PULSE * s, midi(D6), 0.06, -0.3, 2.4)
        v += Bell((MewtwoScript.PULSE + 0.02) * s, midi(A5), 0.05, 0.3, 2.4)
        // The alarm: ostinato, pads, drums, a rising tension.
        val ostinato = intArrayOf(D2, D2, F2, D2, G2, D2, A2, AS2)
        var t = MewtwoScript.ALARM
        var k = 0
        while (t < breakReal - 0.15) {
            v += Pluck(t * s, midi(ostinato[k % 8]), 0.07 + 0.06 * ((t - MewtwoScript.ALARM) / 7.0), if (k % 2 == 0) -0.2 else 0.2, 0.22)
            t += 0.5 - 0.22 * ((t - MewtwoScript.ALARM) / (breakReal - MewtwoScript.ALARM)).coerceIn(0.0, 1.0)
            k++
        }
        t = MewtwoScript.ALARM
        while (t < breakReal - 0.3) {
            v += Boom(t * s, 70.0, 44.0, 0.3, 0.3)
            t += 1.0
        }
        v += Pad(MewtwoScript.ALARM * s, 2.4 * s, chord(D3, F3, A3), 0.12, 0.4, 0.4, 600.0, 1300.0)
        v += Pad(6.3 * s, 2.0 * s, chord(AS2, F3, AS3, D4), 0.13, 0.3, 0.3, 700.0, 1500.0)
        v += Pad(8.2 * s, (breakReal - 8.2) * s, chord(G2, D3, G3, AS3, E4), 0.14, 0.3, 0.2, 800.0, 2400.0)
        v += Rise(7.0 * s, (breakReal - 7.0) * s, 80.0, 2.0, 0.1)
        v += NoiseSweep(8.5 * s, (breakReal - 8.5) * s, 300.0, 7000.0, 0.5, 0.18, rise = true, seed = 53)
        // The breach.
        val b = breakReal * s
        v += Boom(b, 120.0, 26.0, 1.0, 1.8)
        v += Boom(b, 55.0, 25.0, 0.5, 2.4)
        v += NoiseSweep(b, 3.0 * s, 8000.0, 2500.0, 0.6, 0.22, rise = false, mode = NoiseSweep.HIGH, seed = 59)
        for ((i, note) in intArrayOf(D4, F4, A4, D5).withIndex()) v += Lead(b, 0.7 * s, midi(note), 0.075 - 0.01 * i, (i - 1.5) * 0.3)
        for ((i, note) in intArrayOf(D5, A5, D6).withIndex()) v += Bell(b + i * 0.012, midi(note), 0.07, (i - 1) * 0.4, 3.2)
        // The release: a dark, wide theme.
        v += Pad((breakReal + 1.0) * s, 2.4 * s, chord(D2, A2, D3, F3), 0.13, 0.8 * s, 0.4, 500.0, 1100.0)
        v += Pad(14.6 * s, 2.0 * s, chord(AS2, F3, AS3, D4), 0.13, 0.4, 0.4, 600.0, 1300.0)
        val melody = listOf(13.0 to D4, 13.5 to F4, 14.0 to A4, 14.6 to G4, 15.0 to F4, 15.4 to E4, 15.8 to D4)
        for ((at, note) in melody) v += Lead(at * s, 0.42 * s, midi(note), 0.06, -0.1)
        t = 13.0
        while (t < cryReal - 0.3) {
            v += Boom(t * s, 66.0, 42.0, 0.24, 0.3)
            t += 0.75
        }
        // The cry.
        val c = cryReal * s
        v += Boom(c, 95.0, 30.0, 0.8, 1.4)
        v += NoiseSweep(c - 0.9 * s, 0.9 * s, 400.0, 6000.0, 0.45, 0.16, rise = true, seed = 61)
        v += Pad(c, 2.0 * s, chord(C3, G3, C4, E4), 0.13, 0.1, 0.5, 800.0, 1800.0)
        for ((i, note) in intArrayOf(C4, G4, C5).withIndex()) v += Lead(c, 0.9 * s, midi(note), 0.06, (i - 1) * 0.3)
        // The landing: D major.
        v += Pad(18.6 * s, 5.0 * s, chord(D3, A3, D4, FS4), 0.12, 1.0 * s, 1.8 * s, 600.0, 1400.0)
        for ((i, at) in doubleArrayOf(19.2, 19.7, 20.2, 21.2).withIndex()) {
            v += Lead(at * s, 0.45 * s, midi(intArrayOf(D4, FS4, A4, D5)[i]), 0.055, 0.1)
        }
        v += Bell(MewtwoScript.LANDING * s, midi(FS5), 0.06, 0.3, 3.0)
        v += Bell(MewtwoScript.LANDING * s + 0.02, midi(D6), 0.05, -0.3, 3.0)
        v += Boom(MewtwoScript.LANDING * s, 60.0, 36.0, 0.3, 0.6)
        // The siren: two tones, from the alarm to the breach.
        var sirenPhase = 0.0
        v += Bed(MewtwoScript.ALARM * s, b) { time, out ->
            val local = time / s
            val tone = if (((local - MewtwoScript.ALARM) / 0.55).toInt() % 2 == 0) 660.0 else 495.0
            sirenPhase = (sirenPhase + tone / SAMPLE_RATE) % 1.0
            val tri = 1.0 - 4.0 * abs(sirenPhase - 0.5)
            val env = smoothstep((local - MewtwoScript.ALARM) / 0.3) * (1.0 - smoothstep((time - (b - 0.15)) / 0.15))
            val x = tri * 0.05 * env
            out[0] += (x * 0.8).toFloat()
            out[1] += (x * 1.2).toFloat()
        }
        return v
    }

    fun music(script: MewtwoScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(voices(script, stretch), 25.5 * stretch, offset)

    /** The tank's bubbling and hum, the arcs' crackles, the rumble after the breach (in the scene's own time). */
    fun ambienceVoices(script: MewtwoScript, stretch: Double): List<Voice> {
        val bubbleFilter = StateFilter()
        val humFilter = StateFilter()
        val low = StateFilter()
        val crackle = StateFilter()
        val windL = StateFilter()
        val windR = StateFilter()
        val noiseBubble = Noise(301)
        val noiseLow = Noise(307)
        val noiseCrackle = Noise(311)
        val noiseClick = Noise(313)
        val noiseL = Noise(317)
        val noiseR = Noise(319)
        var phase1 = 0.0
        var phase2 = 0.0
        val tank = Bed(0.0, 25.0 * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val glow = script.liquidGlow(a)
            val inTank = if (a < MewtwoScript.BREAK) 1.0 else 0.0
            // Bubbling: band-passed noise in gulps.
            val gulp = 0.5 + 0.5 * sin(2.0 * PI * (1.3 + 2.0 * glow) * t)
            bubbleFilter.process(noiseBubble.next(), 600.0 + 500.0 * gulp, 0.35)
            val bubbles = bubbleFilter.band * 0.18 * inTank * (0.5 + 0.5 * gulp)
            // The machine's hum (two saws), louder with the alarm.
            phase1 = (phase1 + 50.0 / SAMPLE_RATE) % 1.0
            phase2 = (phase2 + 100.4 / SAMPLE_RATE) % 1.0
            humFilter.process((2.0 * phase1 - 1.0) * 0.6 + (2.0 * phase2 - 1.0) * 0.4, 380.0 + 300.0 * script.alarm(a), 0.8)
            val hum = humFilter.low * (0.07 + 0.08 * script.alarm(a)) * (1.0 - 0.7 * (1.0 - inTank))
            val g = fade(t)
            out[0] += ((bubbles + hum) * g).toFloat()
            out[1] += ((bubbles * 0.8 + hum) * g).toFloat()
        }
        val power = Bed(0.0, 25.0 * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val arcs = script.arcs(a)
            val aura = script.aura(a)
            // Crackles: sparse bright clicks.
            val click = if (noiseClick.next() > 1.0 - 0.0018 * arcs) noiseCrackle.next() * 3.0 else 0.0
            crackle.process(click, 3600.0, 0.5)
            val c = crackle.high * 0.32 * arcs
            // A deep psychic rumble and a hiss after the breach.
            low.process(noiseLow.next(), 55.0, 0.6)
            val burst = if (a < MewtwoScript.BREAK) 0.0 else exp(-(a - MewtwoScript.BREAK) / 1.2)
            val rumble = low.low * (0.4 * aura + 0.9 * burst + 0.2 * script.fog(a)) * 2.2
            windL.process(noiseL.next(), 500.0 + 300.0 * aura, 0.7)
            windR.process(noiseR.next(), 560.0 + 300.0 * aura, 0.7)
            val g = fade(t)
            out[0] += ((c * 0.8 + rumble + windL.band * 0.12 * aura) * g).toFloat()
            out[1] += ((c * 1.2 + rumble + windR.band * 0.12 * aura) * g).toFloat()
        }
        return listOf(tank, power)
    }

    fun ambience(script: MewtwoScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(ambienceVoices(script, stretch), 25.0 * stretch, offset, reverbWet = 0.14, echoWet = 0.0)

    private fun fade(t: Double): Double = smoothstep(t / 1.2) * (1.0 - smoothstep((t - 22.6) / 1.6))
}
