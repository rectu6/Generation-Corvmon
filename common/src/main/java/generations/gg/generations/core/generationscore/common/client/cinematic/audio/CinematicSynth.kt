package generations.gg.generations.core.generationscore.common.client.cinematic.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * A small synthesizer for the cinematics' music and ambience (CINEMATIQUES-PLAN.md D4), pure
 * (tested off-game): original sounds made from oscillators, noise and filters, no sample.
 * Voices are scheduled on a score (seconds of the scene, already stretched to its duration);
 * the renderer mixes them into stereo with an echo and a reverb.
 */
const val SAMPLE_RATE = 44100
private const val DT = 1.0 / SAMPLE_RATE
private const val TWO_PI = 2.0 * PI

/** A sound on the score: from start (seconds) to end; render adds its sample at local time t to out[0], out[1]. */
abstract class Voice(val start: Double, val end: Double) {
    abstract fun render(t: Double, out: FloatArray)

    protected fun mono(value: Double, pan: Double, out: FloatArray) {
        val p = pan.coerceIn(-1.0, 1.0)
        out[0] += (value * (1.0 - p) * 0.5 * 1.41421356).toFloat()
        out[1] += (value * (1.0 + p) * 0.5 * 1.41421356).toFloat()
    }
}

/** Music notes: A4 = 440 Hz; midi 69. */
fun midi(note: Int): Double = 440.0 * 2.0.pow((note - 69) / 12.0)

private fun smooth(x: Double): Double {
    val t = x.coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}

/** A cheap white noise (xorshift), from -1 to 1. */
class Noise(seed: Int) {
    private var state = (seed.toLong() * 2654435761L - 7046029254386353131L) or 1L

    fun next(): Double {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return ((x ushr 11).toDouble() / (1L shl 53).toDouble()) * 2.0 - 1.0
    }
}

/** A state-variable filter (Chamberlin, twice oversampled for stability): low, band and high outputs. */
class StateFilter {
    var low = 0.0
        private set
    var band = 0.0
        private set
    var high = 0.0
        private set

    fun process(x: Double, cutoff: Double, damping: Double) {
        val f = 2.0 * sin(PI * cutoff.coerceIn(20.0, 9000.0) / (SAMPLE_RATE * 2.0))
        val q = damping.coerceIn(0.05, 2.0)
        repeat(2) {
            low += f * band
            high = x - low - q * band
            band += f * high
        }
        if (!low.isFinite() || !band.isFinite()) {
            low = 0.0
            band = 0.0
            high = 0.0
        }
    }
}

/** A glassy FM bell: bright at the strike, a long ringing tail. */
class Bell(start: Double, private val freq: Double, private val gain: Double, private val pan: Double, private val decay: Double = 2.6, private val ratio: Double = 3.5, private val index: Double = 3.0) :
    Voice(start, start + decay * 5.0) {
    override fun render(t: Double, out: FloatArray) {
        val env = (1.0 - exp(-t * 400.0)) * exp(-t / decay)
        val i = index * exp(-t * 2.5) + 0.25
        val w = TWO_PI * freq * t
        val body = sin(w + i * sin(w * ratio))
        val shimmer = sin(w * 2.76) * 0.22 * exp(-t * 2.0 / decay)
        mono((body * 0.75 + shimmer) * env * gain, pan, out)
    }
}

/** A pad: detuned saws through a soft low-pass, slow to rise and fall. */
class Pad(
    start: Double, private val length: Double, private val freqs: DoubleArray, private val gain: Double,
    private val attack: Double, private val release: Double, private val cutoff0: Double, private val cutoff1: Double, private val width: Double = 0.6,
) : Voice(start, start + length) {
    private val detune = doubleArrayOf(-0.0042, 0.0, 0.0047)
    private val phases = DoubleArray(freqs.size * 3) { (it * 0.137) % 1.0 }
    private val filterL = StateFilter()
    private val filterR = StateFilter()

    override fun render(t: Double, out: FloatArray) {
        var left = 0.0
        var right = 0.0
        val lfo = sin(TWO_PI * 0.23 * t) * 0.0012
        for (n in freqs.indices) for (k in 0 until 3) {
            val i = n * 3 + k
            val f = freqs[n] * (1.0 + detune[k] + lfo * (k - 1))
            val inc = f * DT
            phases[i] = (phases[i] + inc) % 1.0
            val p = phases[i]
            var saw = 2.0 * p - 1.0
            // PolyBLEP: fewer aliases at the wrap.
            if (p < inc) {
                val x = p / inc
                saw -= x + x - x * x - 1.0
            } else if (p > 1.0 - inc) {
                val x = (p - 1.0) / inc
                saw -= x * x + x + x + 1.0
            }
            if (k == 0) left += saw else if (k == 2) right += saw else {
                left += saw * 0.5
                right += saw * 0.5
            }
        }
        val u = (t / length).coerceIn(0.0, 1.0)
        val cutoff = cutoff0 * (cutoff1 / cutoff0).pow(u)
        filterL.process(left, cutoff, 0.9)
        filterR.process(right, cutoff * 1.03, 0.9)
        val env = smooth(t / attack) * (1.0 - smooth((t - (length - release)) / release))
        val g = gain * env / (freqs.size * 1.5)
        val mid = (filterL.low + filterR.low) * 0.5
        out[0] += ((mid + (filterL.low - mid) * width) * g).toFloat()
        out[1] += ((mid + (filterR.low - mid) * width) * g).toFloat()
    }
}

/** A deep impact: a sine falling from f0 to f1, a click, soft-clipped. */
class Boom(start: Double, private val f0: Double, private val f1: Double, private val gain: Double, private val decay: Double = 1.2) :
    Voice(start, start + decay * 6.0) {
    private var phase = 0.0
    private val noise = Noise((start * 1000).toInt())

    override fun render(t: Double, out: FloatArray) {
        val f = f1 + (f0 - f1) * exp(-t * 7.0)
        phase = (phase + f * DT) % 1.0
        var s = sin(TWO_PI * phase) * exp(-t / decay) * (1.0 - exp(-t * 900.0))
        if (t < 0.012) s += noise.next() * (1.0 - t / 0.012) * 0.5
        s = tanh(s * 1.6) / tanh(1.6)
        mono(s * gain, 0.0, out)
    }
}

/** Filtered noise: a wind, a swell, a crash. rise: grows to its end (cut short there); otherwise it decays from the start. */
class NoiseSweep(
    start: Double, private val length: Double, private val f0: Double, private val f1: Double, private val damping: Double,
    private val gain: Double, private val rise: Boolean, private val mode: Int = BAND, private val pan: Double = 0.0, seed: Int = 7,
) : Voice(start, start + length) {
    private val noiseL = Noise(seed)
    private val noiseR = Noise(seed * 31 + 5)
    private val filterL = StateFilter()
    private val filterR = StateFilter()

    override fun render(t: Double, out: FloatArray) {
        val u = (t / length).coerceIn(0.0, 1.0)
        val cutoff = f0 * (f1 / f0).pow(u)
        filterL.process(noiseL.next(), cutoff, damping)
        filterR.process(noiseR.next(), cutoff * 1.05, damping)
        val env = if (rise) u * u * (1.0 - smooth((t - (length - 0.02)) / 0.02)) else exp(-t / (length / 5.0)) * (1.0 - exp(-t * 500.0))
        fun pick(f: StateFilter) = when (mode) {
            LOW -> f.low
            HIGH -> f.high
            else -> f.band
        }
        val g = gain * env
        val p = pan.coerceIn(-1.0, 1.0)
        out[0] += (pick(filterL) * g * (1.0 - p * 0.5)).toFloat()
        out[1] += (pick(filterR) * g * (1.0 + p * 0.5)).toFloat()
    }

    companion object {
        const val LOW = 0
        const val BAND = 1
        const val HIGH = 2
    }
}

/** A short plucked FM note (the ostinato). */
class Pluck(start: Double, private val freq: Double, private val gain: Double, private val pan: Double, private val decay: Double = 0.35) :
    Voice(start, start + decay * 6.0) {
    override fun render(t: Double, out: FloatArray) {
        val w = TWO_PI * freq * t
        val s = sin(w + 2.2 * exp(-t * 14.0) * sin(w)) * exp(-t / decay) * (1.0 - exp(-t * 600.0))
        mono(s * gain, pan, out)
    }
}

/** A brass-like FM lead: the brightness rises with the note, a vibrato after a moment. */
class Lead(start: Double, private val length: Double, private val freq: Double, private val gain: Double, private val pan: Double = 0.0) :
    Voice(start, start + length + 0.4) {
    private var phase = 0.0
    private var sub = 0.0

    override fun render(t: Double, out: FloatArray) {
        val vibrato = 1.0 + 0.004 * sin(TWO_PI * 5.2 * t) * smooth((t - 0.25) / 0.3)
        val f = freq * vibrato
        phase = (phase + f * DT) % 1.0
        sub = (sub + f * 0.5 * DT) % 1.0
        val index = 2.6 * smooth(t / 0.09) * (1.0 - 0.35 * smooth((t - 0.1) / 0.4)) + 0.4
        val w = TWO_PI * phase
        val s = sin(w + index * sin(w)) * 0.8 + sin(TWO_PI * sub) * 0.25
        val env = smooth(t / 0.05) * (1.0 - smooth((t - length) / 0.35))
        mono(s * env * gain, pan, out)
    }
}

/** A rising glide that never seems to end (Shepard tones), for tension. */
class Rise(start: Double, private val length: Double, private val base: Double, private val octaves: Double, private val gain: Double) :
    Voice(start, start + length) {
    private val phases = DoubleArray(4)

    override fun render(t: Double, out: FloatArray) {
        val u = (t / length).coerceIn(0.0, 1.0)
        var s = 0.0
        for (k in 0 until 4) {
            val position = ((k + u * octaves) % 4.0) / 4.0
            val f = base * 2.0.pow(position * 4.0)
            phases[k] = (phases[k] + f * DT) % 1.0
            val bell = exp(-((position - 0.5) / 0.22).pow(2.0))
            s += sin(TWO_PI * phases[k]) * bell
        }
        val env = smooth(t / 0.6) * (0.4 + 0.6 * u) * (1.0 - smooth((t - (length - 0.03)) / 0.03))
        mono(s * env * gain * 0.5, 0.0, out)
    }
}

/** Something continuous whose loudness and colour follow the scene (the ambience). */
class Bed(start: Double, end: Double, private val shape: (Double, FloatArray) -> Unit) : Voice(start, end) {
    override fun render(t: Double, out: FloatArray) = shape(start + t, out)
}

/** A score's voices, mixed in real time, with an echo and a reverb. offset: where it starts (a late client). */
class SynthRenderer(voices: List<Voice>, val length: Double, offset: Double, private val reverbWet: Double = 0.28, private val echoWet: Double = 0.16, private val echoSeconds: Double = 0.47) {
    private val pending = voices.sortedBy { it.start }
    private var next = 0
    private val active = ArrayList<Voice>()
    var time = maxOf(0.0, offset)
        private set
    val finished: Boolean get() = time >= length

    // Reverb (Schroeder): 4 combs and 2 all-passes per side.
    private val combL = intArrayOf(1116, 1188, 1277, 1356).map { DoubleArray(it) }
    private val combR = intArrayOf(1139, 1211, 1300, 1379).map { DoubleArray(it) }
    private val combIndex = IntArray(8)
    private val combStore = DoubleArray(8)
    private val allL = intArrayOf(556, 441).map { DoubleArray(it) }
    private val allR = intArrayOf(579, 464).map { DoubleArray(it) }
    private val allIndex = IntArray(4)
    private val echoL = DoubleArray((echoSeconds * SAMPLE_RATE).toInt().coerceAtLeast(1))
    private val echoR = DoubleArray((echoSeconds * SAMPLE_RATE).toInt().coerceAtLeast(1))
    private var echoIndex = 0
    private var echoLowL = 0.0
    private var echoLowR = 0.0
    private val sample = FloatArray(2)

    init {
        while (next < pending.size && pending[next].start <= time) {
            if (pending[next].end > time) active += pending[next]
            next++
        }
    }

    private fun comb(buffer: DoubleArray, i: Int, input: Double): Double {
        val pos = combIndex[i]
        val out = buffer[pos]
        combStore[i] = out * 0.75 + combStore[i] * 0.25
        buffer[pos] = input + combStore[i] * 0.82
        combIndex[i] = if (pos + 1 >= buffer.size) 0 else pos + 1
        return out
    }

    private fun allpass(buffer: DoubleArray, i: Int, input: Double): Double {
        val pos = allIndex[i]
        val stored = buffer[pos]
        val out = stored - input
        buffer[pos] = input + stored * 0.5
        allIndex[i] = if (pos + 1 >= buffer.size) 0 else pos + 1
        return out
    }

    /** Up to n frames into left and right from index at (-1 to 1); how many were made (fewer at the end). */
    fun render(left: FloatArray, right: FloatArray, n: Int, at: Int = 0): Int {
        var made = 0
        while (made < n && time < length) {
            while (next < pending.size && pending[next].start <= time) active += pending[next++]
            sample[0] = 0f
            sample[1] = 0f
            var i = 0
            while (i < active.size) {
                val voice = active[i]
                if (time >= voice.end) {
                    active[i] = active[active.size - 1]
                    active.removeAt(active.size - 1)
                    continue
                }
                voice.render(time - voice.start, sample)
                i++
            }
            var l = sample[0].toDouble()
            var r = sample[1].toDouble()
            if (!l.isFinite()) l = 0.0
            if (!r.isFinite()) r = 0.0

            // Echo (ping-pong, darker each time).
            val el = echoL[echoIndex]
            val er = echoR[echoIndex]
            echoLowL += (er - echoLowL) * 0.35
            echoLowR += (el - echoLowR) * 0.35
            echoL[echoIndex] = l + echoLowL * 0.32
            echoR[echoIndex] = r + echoLowR * 0.32
            echoIndex = if (echoIndex + 1 >= echoL.size) 0 else echoIndex + 1
            l += el * echoWet
            r += er * echoWet

            // Reverb.
            val input = (l + r) * 0.08
            var wl = 0.0
            var wr = 0.0
            for (k in 0 until 4) {
                wl += comb(combL[k], k, input)
                wr += comb(combR[k], k + 4, input)
            }
            wl = allpass(allL[1], 1, allpass(allL[0], 0, wl))
            wr = allpass(allR[1], 3, allpass(allR[0], 2, wr))
            l += wl * reverbWet
            r += wr * reverbWet

            left[at + made] = (tanh(l * 0.9)).toFloat()
            right[at + made] = (tanh(r * 0.9)).toFloat()
            made++
            time += DT
        }
        return made
    }

    /** The loudest sample of a whole render (for the off-game check), from here to the end. */
    fun peak(): Float {
        val l = FloatArray(4096)
        val r = FloatArray(4096)
        var peak = 0f
        while (!finished) {
            val n = render(l, r, 4096)
            for (i in 0 until n) {
                if (!l[i].isFinite() || !r[i].isFinite()) return Float.NaN
                peak = maxOf(peak, abs(l[i]), abs(r[i]))
            }
        }
        return peak
    }
}

/**
 * A score made ahead on its own thread, into memory, from the moment the scene is announced (the
 * start packet comes half a second early, and the synthesizer runs about ten times faster than
 * real time): the sound engine's first read (four seconds at once) finds it ready, and the music
 * starts on time. A read waits for what is not made yet (3 s at most).
 */
class SynthBuffer(private val renderer: SynthRenderer) {
    val frames: Int = ((renderer.length - renderer.time) * SAMPLE_RATE).toInt().coerceAtLeast(0)
    private val left = FloatArray(frames)
    private val right = FloatArray(frames)

    @Volatile
    var ready = 0
        private set

    @Volatile
    var done = false
        private set

    @Volatile
    private var cancelled = false

    fun start(name: String): SynthBuffer {
        val thread = Thread({ run() }, name)
        thread.isDaemon = true
        thread.priority = Thread.NORM_PRIORITY - 1
        thread.start()
        return this
    }

    /** On the caller's thread (the off-game check), or the buffer's own. */
    fun run() {
        try {
            while (!cancelled && ready < frames) {
                val n = renderer.render(left, right, minOf(4096, frames - ready), ready)
                if (n <= 0) break
                ready += n
            }
        } finally {
            done = true
        }
    }

    fun cancel() {
        cancelled = true
    }

    /** Frames from `from` into the arrays (waiting for them if needed); how many (0 at the end). */
    fun read(from: Int, n: Int, outLeft: FloatArray, outRight: FloatArray): Int {
        if (from >= frames) return 0
        val want = minOf(n, frames - from)
        val deadline = System.nanoTime() + 3_000_000_000L
        while (ready < from + want && !done && System.nanoTime() < deadline) Thread.sleep(2)
        val count = (minOf(from + want, ready) - from).coerceAtLeast(0)
        System.arraycopy(left, from, outLeft, 0, count)
        System.arraycopy(right, from, outRight, 0, count)
        return count
    }
}
