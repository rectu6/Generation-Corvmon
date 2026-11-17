package generations.gg.generations.core.generationscore.common.client.cinematic.audio

import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.LatiasScript
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.LatiosScript
import kotlin.math.PI
import kotlin.math.sin

/**
 * The Eon duo's music and ambiences (pure, tested off-game), written for their 23 s and
 * stretched with the scene.
 *
 * Latias (F sharp minor to A major, 108): bells and a harp while the stone wakes and the
 * invisible one passes, bells circling with the illusions, a harp ostinato and a soaring tune for
 * her flight, a choir at the sphere, a bright hit on her cry, a soft close in A.
 * Latios (D minor to D major, 128): a low drone and a rumble, the beam's rising hiss, drums and a
 * brass motif through his passes, a swelling pad and circling bells for the rings, the pulse in D
 * major, the hit of his stop, a wide close.
 */
object EonMusic {
    private fun chord(vararg notes: Int) = DoubleArray(notes.size) { midi(notes[it]) }

    private fun smoothstep(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    private fun fade(t: Double, end: Double): Double = smoothstep(t / 1.2) * (1.0 - smoothstep((t - end) / 1.6))

    // ---------------------------------------------------------------- Latias

    fun latiasVoices(script: LatiasScript, stretch: Double): List<Voice> {
        val s = stretch
        val v = ArrayList<Voice>()
        // The stone and the invisible one: a soft F sharp minor, bells, a slow harp.
        v += Pad(0.0, 5.4 * s, chord(54, 57, 61, 64, 68), 0.07, 1.4 * s, 0.8 * s, 500.0, 1300.0)
        for ((at, note) in listOf(0.4 to 73, 0.9 to 78, 1.5 to 76, 2.4 to 81, 3.4 to 78, 4.4 to 73)) v += Bell(at * s, midi(note), 0.05, if (note % 2 == 0) -0.3 else 0.3, 2.0, ratio = 4.0, index = 1.6)
        var t = LatiasScript.GHOST
        var k = 0
        val harp = intArrayOf(54, 61, 66, 69, 73, 69, 66, 61)
        while (t < LatiasScript.GHOST_END) {
            v += Pluck(t * s, midi(harp[k % harp.size]), 0.045, if (k % 2 == 0) -0.4 else 0.4, 0.6)
            t += 0.28
            k++
        }
        v += NoiseSweep(LatiasScript.GHOST * s, (LatiasScript.GHOST_END - LatiasScript.GHOST) * s, 3000.0, 7000.0, 0.6, 0.05, rise = false, seed = 211)
        // The illusions: bells round in a circle, a D major seventh.
        v += Pad(LatiasScript.ILLUSIONS * s, 5.2 * s, chord(50, 57, 61, 66), 0.08, 0.8 * s, 0.6 * s, 700.0, 2000.0)
        t = LatiasScript.ILLUSIONS
        k = 0
        val circle = intArrayOf(78, 81, 85, 88, 85, 81)
        while (t < LatiasScript.POPS) {
            v += Bell(t * s, midi(circle[k % circle.size]), 0.035, sin(k * 1.1) * 0.6, 1.3, ratio = 4.0, index = 1.4)
            t += 0.3
            k++
        }
        for (i in 0 until LatiasScript.ILLUSION_COUNT) {
            val at = script.vanishAt(i) * s
            v += Bell(at, midi(85 + (i % 3) * 3), 0.05, (i - 2.5) * 0.15, 1.6)
            v += Pluck(at + 0.02, midi(73 + (i % 3) * 2), 0.04, (2.5 - i) * 0.15, 0.4)
        }
        // The reveal and her flight: a harp ostinato, a soaring tune, chords F#m, D, E.
        v += Boom(LatiasScript.REVEAL * s, 120.0, 45.0, 0.35, 0.9)
        for ((i, note) in intArrayOf(85, 88, 90).withIndex()) v += Bell(LatiasScript.REVEAL * s + i * 0.02, midi(note), 0.05, (i - 1) * 0.4, 2.4)
        v += Pad(LatiasScript.REVEAL * s, 2.8 * s, chord(54, 61, 66, 69), 0.08, 0.3, 0.5, 800.0, 1800.0)
        v += Pad(12.7 * s, 2.4 * s, chord(50, 57, 62, 66), 0.08, 0.3, 0.5, 900.0, 2000.0)
        v += Pad(15.0 * s, 2.2 * s, chord(52, 59, 64, 68), 0.08, 0.3, 0.6, 900.0, 2200.0)
        t = LatiasScript.REVEAL
        k = 0
        val ostinato = intArrayOf(66, 69, 73, 69, 62, 66, 69, 66, 64, 68, 71, 68)
        while (t < LatiasScript.HOVER - 0.1) {
            v += Pluck(t * s, midi(ostinato[k % ostinato.size]), 0.05, if (k % 2 == 0) -0.35 else 0.35, 0.4)
            t += 0.278
            k++
        }
        val tune = listOf(10.3 to 73, 10.85 to 76, 11.4 to 78, 12.0 to 81, 12.6 to 80, 13.2 to 76, 13.7 to 78, 14.3 to 85, 15.6 to 83, 16.2 to 81)
        for ((i, pair) in tune.withIndex()) {
            val length = if (i + 1 < tune.size) tune[i + 1].first - pair.first else 0.8
            v += Lead(pair.first * s, (length * 0.92) * s, midi(pair.second), 0.045, 0.1)
        }
        v += Rise(12.7 * s, 1.0 * s, 300.0, 1.5, 0.035)
        v += NoiseSweep(14.3 * s, 0.7 * s, 800.0, 9000.0, 0.5, 0.11, rise = true, seed = 223)
        v += Boom(LatiasScript.OVERHEAD * s, 100.0, 38.0, 0.45, 1.0)
        // The sphere: a choir in A, rising to her cry.
        v += Pad(LatiasScript.SPHERE * s, 2.2 * s, chord(57, 61, 64, 69, 73), 0.11, 0.6, 0.6, 900.0, 2400.0, width = 0.9)
        v += Rise(LatiasScript.SPHERE * s, (LatiasScript.WAVE - LatiasScript.SPHERE) * s, 220.0, 2.0, 0.045)
        v += NoiseSweep((LatiasScript.WAVE - 0.7) * s, 0.7 * s, 500.0, 7000.0, 0.45, 0.1, rise = true, seed = 227)
        val w = LatiasScript.WAVE * s
        v += Boom(w, 110.0, 36.0, 0.55, 1.2)
        for ((i, note) in intArrayOf(81, 85, 88).withIndex()) v += Bell(w + i * 0.02, midi(note), 0.06, (i - 1) * 0.4, 3.2)
        for ((i, note) in intArrayOf(69, 73, 76).withIndex()) v += Lead(w, 1.2 * s, midi(note), 0.045, (i - 1) * 0.3)
        // The descent: a soft close in A.
        v += Pad(LatiasScript.DESCEND * s, 3.4 * s, chord(57, 61, 64, 69), 0.09, 0.8 * s, 1.4 * s, 600.0, 1300.0)
        for ((i, at) in doubleArrayOf(19.9, 20.4, 20.9, 21.4).withIndex()) v += Bell(at * s, midi(intArrayOf(76, 73, 69, 76)[i]), 0.05, if (i % 2 == 0) -0.2 else 0.2, 2.4, ratio = 4.0, index = 1.6)
        v += Bell(LatiasScript.LANDING * s + 0.03, midi(81), 0.05, 0.3, 3.0)
        return v
    }

    fun latiasMusic(script: LatiasScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(latiasVoices(script, stretch), 24.5 * stretch, offset)

    /** A light wind, glints, the rush of her flight (in the scene's own time). */
    fun latiasAmbienceVoices(script: LatiasScript, stretch: Double): List<Voice> {
        val windL = StateFilter()
        val windR = StateFilter()
        val rushL = StateFilter()
        val rushR = StateFilter()
        val glint = StateFilter()
        val noiseL = Noise(501)
        val noiseR = Noise(509)
        val noiseRushL = Noise(519)
        val noiseRushR = Noise(521)
        val noiseGlint = Noise(531)
        val noiseClick = Noise(533)
        val air = Bed(0.0, 24.0 * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val rush = script.trails(a) + 0.6 * script.ghost(a)
            val sway = 0.5 + 0.5 * sin(2.0 * PI * 0.11 * t)
            windL.process(noiseL.next(), 420.0 + 260.0 * sway, 0.7)
            windR.process(noiseR.next(), 500.0 + 260.0 * (1.0 - sway), 0.7)
            rushL.process(noiseRushL.next(), 900.0 + 2200.0 * rush, 0.5)
            rushR.process(noiseRushR.next(), 1000.0 + 2200.0 * rush, 0.5)
            val sparkle = script.stoneGlow(a) + script.illusions(a) + script.sphere(a) + script.sparkle(a)
            val click = if (noiseClick.next() > 1.0 - 0.0012 * sparkle) noiseGlint.next() * 2.5 else 0.0
            glint.process(click, 5400.0, 0.4)
            val g = fade(t, 21.8)
            out[0] += ((windL.band * 0.07 + rushL.band * 0.15 * rush + glint.high * 0.22) * g).toFloat()
            out[1] += ((windR.band * 0.07 + rushR.band * 0.15 * rush + glint.high * 0.18) * g).toFloat()
        }
        return listOf(air)
    }

    fun latiasAmbience(script: LatiasScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(latiasAmbienceVoices(script, stretch), 24.0 * stretch, offset, reverbWet = 0.2, echoWet = 0.0)

    // ---------------------------------------------------------------- Latios

    fun latiosVoices(@Suppress("UNUSED_PARAMETER") script: LatiosScript, stretch: Double): List<Voice> {
        val s = stretch
        val v = ArrayList<Voice>()
        // The signal: a low drone, the rumble, the beam's hiss, a bell on the stone.
        v += Pad(0.0, 5.2 * s, chord(38, 45, 50), 0.09, 1.2 * s, 0.6 * s, 220.0, 600.0)
        v += Bell(0.3 * s, midi(74), 0.05, 0.0, 2.6)
        v += Bell(0.8 * s, midi(81), 0.04, 0.3, 2.2)
        v += Boom(1.2 * s, 70.0, 30.0, 0.35, 2.0)
        v += NoiseSweep(LatiosScript.BEAM * s, (LatiosScript.BEAM_END - LatiosScript.BEAM) * s, 900.0, 9000.0, 0.5, 0.09, rise = true, seed = 311)
        v += Boom(3.9 * s, 90.0, 32.0, 0.4, 1.1)
        // The passes: drums on the beat (128), a brass motif; Dm, Bb, C, A.
        var t = LatiosScript.P1
        var beat = 0
        while (t < LatiosScript.HOVER) {
            v += Boom(t * s, 95.0, 42.0, if (beat % 4 == 0) 0.2 else 0.12, 0.25)
            if (beat % 2 == 1) v += NoiseSweep(t * s, 0.09 * s, 2500.0, 6000.0, 0.5, 0.05, rise = false, seed = 331 + beat)
            t += 0.469
            beat++
        }
        v += Pad(LatiosScript.P1 * s, 2.0 * s, chord(50, 53, 57, 62), 0.08, 0.2, 0.4, 700.0, 1600.0)
        v += Pad(LatiosScript.P2 * s, 1.8 * s, chord(46, 50, 53, 58), 0.08, 0.2, 0.4, 700.0, 1600.0)
        v += Pad(LatiosScript.P3 * s, 1.8 * s, chord(48, 52, 55, 60), 0.08, 0.2, 0.4, 800.0, 1800.0)
        v += Pad(11.0 * s, 1.6 * s, chord(45, 49, 52, 57), 0.08, 0.3, 0.6, 800.0, 1800.0)
        val brass = listOf(
            5.0 to 62, 5.47 to 65, 5.94 to 69, 6.4 to 74,
            7.2 to 70, 7.67 to 69, 8.14 to 67, 8.6 to 65,
            9.2 to 74, 9.67 to 72, 10.14 to 69, 10.6 to 65, 11.1 to 64, 11.6 to 69,
        )
        for ((i, pair) in brass.withIndex()) {
            val length = if (i + 1 < brass.size) (brass[i + 1].first - pair.first).coerceAtMost(0.8) else 0.8
            v += Lead(pair.first * s, (length * 0.9) * s, midi(pair.second), 0.05, 0.0)
        }
        for (at in doubleArrayOf(LatiosScript.P1_NEAR, LatiosScript.P2_NEAR, LatiosScript.P3_NEAR)) {
            v += Boom(at * s, 120.0, 34.0, 0.45, 0.9)
            v += NoiseSweep((at - 0.5) * s, 0.5 * s, 700.0, 8000.0, 0.5, 0.1, rise = true, seed = 341 + at.toInt())
        }
        // The Guardian's energy: a swelling Dm9, rising, bells circling with the rings.
        v += Pad(LatiosScript.HOVER * s, (LatiosScript.PULSE - LatiosScript.HOVER) * s, chord(50, 57, 60, 64, 65), 0.1, 1.0 * s, 0.3, 600.0, 2600.0, width = 0.9)
        v += Rise(12.6 * s, (LatiosScript.PULSE - 12.6) * s, 160.0, 2.2, 0.05)
        v += NoiseSweep(14.0 * s, (LatiosScript.PULSE - 14.0) * s, 600.0, 9000.0, 0.5, 0.1, rise = true, seed = 351)
        t = LatiosScript.RINGS
        var k = 0
        val rings = intArrayOf(74, 77, 81, 76, 79, 84)
        while (t < LatiosScript.PULSE - 0.1) {
            v += Bell(t * s, midi(rings[k % rings.size]), 0.035, sin(k * 0.9) * 0.6, 1.2, ratio = 4.0, index = 1.4)
            t += 0.234 * (if (t > 14.5) 1 else 2)
            k++
        }
        // The pulse: D major at last.
        val p = LatiosScript.PULSE * s
        v += Boom(p, 120.0, 30.0, 0.6, 1.4)
        for ((i, note) in intArrayOf(74, 78, 81, 86).withIndex()) v += Bell(p + i * 0.015, midi(note), 0.055, (i - 1.5) * 0.3, 3.4)
        for ((i, note) in intArrayOf(62, 66, 69).withIndex()) v += Lead(p, 1.6 * s, midi(note), 0.05, (i - 1) * 0.3)
        v += Pad(p, 2.0 * s, chord(50, 54, 57, 62, 66), 0.1, 0.2, 0.8, 900.0, 2400.0, width = 0.9)
        // The dive and the stop.
        v += NoiseSweep((LatiosScript.DIVE) * s, (LatiosScript.BRAKE - LatiosScript.DIVE) * s, 500.0, 9000.0, 0.5, 0.12, rise = true, seed = 361)
        v += Boom(LatiosScript.BRAKE * s, 140.0, 32.0, 0.65, 1.3)
        // The close: D major, wide.
        v += Pad(LatiosScript.BRAKE * s, 4.0 * s, chord(50, 54, 57, 62), 0.09, 0.4, 1.6 * s, 600.0, 1400.0)
        for ((i, at) in doubleArrayOf(19.6, 20.1, 20.6).withIndex()) v += Bell(at * s, midi(intArrayOf(81, 78, 74)[i]), 0.05, if (i % 2 == 0) -0.25 else 0.25, 2.6)
        v += Bell(LatiosScript.LANDING * s + 0.4, midi(86), 0.045, 0.2, 3.0)
        return v
    }

    fun latiosMusic(script: LatiosScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(latiosVoices(script, stretch), 24.5 * stretch, offset)

    /** Wind high up, a low rumble in the sky, the rush of his passes (in the scene's own time). */
    fun latiosAmbienceVoices(script: LatiosScript, stretch: Double): List<Voice> {
        val windL = StateFilter()
        val windR = StateFilter()
        val rushL = StateFilter()
        val rushR = StateFilter()
        val rumble = StateFilter()
        val noiseL = Noise(601)
        val noiseR = Noise(609)
        val noiseRushL = Noise(619)
        val noiseRushR = Noise(621)
        val noiseRumble = Noise(631)
        val air = Bed(0.0, 24.0 * stretch) { time, out ->
            val t = time / stretch
            val a = script.action(t)
            val rush = script.passes(a).coerceAtMost(1.0) + 0.5 * script.far(a)
            val sky = smoothstep(t / 1.0) * (1.0 - smoothstep((t - 5.0) / 2.0)) + 0.6 * script.energy(a)
            val sway = 0.5 + 0.5 * sin(2.0 * PI * 0.09 * t)
            windL.process(noiseL.next(), 380.0 + 260.0 * sway, 0.7)
            windR.process(noiseR.next(), 460.0 + 260.0 * (1.0 - sway), 0.7)
            rushL.process(noiseRushL.next(), 800.0 + 2600.0 * rush, 0.45)
            rushR.process(noiseRushR.next(), 900.0 + 2600.0 * rush, 0.45)
            rumble.process(noiseRumble.next(), 90.0, 0.6)
            val g = fade(t, 21.8)
            val low = rumble.band * 0.35 * sky
            out[0] += ((windL.band * 0.08 + rushL.band * 0.17 * rush + low) * g).toFloat()
            out[1] += ((windR.band * 0.08 + rushR.band * 0.17 * rush + low) * g).toFloat()
        }
        return listOf(air)
    }

    fun latiosAmbience(script: LatiosScript, stretch: Double, offset: Double): SynthRenderer =
        SynthRenderer(latiosAmbienceVoices(script, stretch), 24.0 * stretch, offset, reverbWet = 0.2, echoWet = 0.0)
}
