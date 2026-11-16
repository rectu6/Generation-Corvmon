package generations.gg.generations.core.generationscore.common.client.cinematic.audio

import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.AudioStream
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.util.valueproviders.ConstantFloat
import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.AudioFormat

/**
 * The cinematics' sound on the client: one-shot sounds at places of the scene (vanilla and
 * Cobblemon's), and two streams made by the synthesizer (music, ambience). A stream is played
 * as a sound event generations_core:cinematic.<scene>.<part>: a resource pack that declares that
 * event (an OGG) replaces it; otherwise the sound points to a file that does not exist,
 * generations_core:sounds/cinematic_synth/<scene>_<part>.ogg, whose stream SoundBufferLibraryMixin
 * takes from here. Everything is in the master category, with the scene's own volumes.
 */
object CinematicAudio {
    private const val SYNTH_PREFIX = "sounds/cinematic_synth/"
    private val streams = ConcurrentHashMap<ResourceLocation, AudioStream>()

    /** SoundBufferLibraryMixin: the synthesizer's stream for that file, once (null: not ours). */
    @JvmStatic
    fun takeStream(location: ResourceLocation): AudioStream? {
        if (location.namespace != GenerationsCore.MOD_ID || !location.path.startsWith(SYNTH_PREFIX)) return null
        return streams.remove(location)
    }

    /** The synthesizer as a 16-bit stereo stream (made ahead in a SynthBuffer); empty at its end. */
    class SynthStream(private val source: SynthBuffer) : AudioStream {
        private val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 2, true, false)
        private var left = FloatArray(0)
        private var right = FloatArray(0)
        private var position = 0

        override fun getFormat(): AudioFormat = format

        override fun read(size: Int): ByteBuffer {
            val frames = (size / 4).coerceIn(0, SAMPLE_RATE * 2)
            if (left.size < frames) {
                left = FloatArray(frames)
                right = FloatArray(frames)
            }
            val made = source.read(position, frames, left, right)
            position += made
            val buffer = BufferUtils.createByteBuffer(made * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until made) {
                buffer.putShort((left[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                buffer.putShort((right[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            }
            buffer.flip()
            return buffer
        }

        override fun close() {
            source.cancel()
        }
    }

    /** A stream of the scene: a pack's OGG if it declares the event, the synthesizer otherwise. */
    class StreamSound(event: ResourceLocation, private val synth: ResourceLocation, private var level: Float) :
        AbstractTickableSoundInstance(SoundEvent.createVariableRangeEvent(event), SoundSource.MASTER, RandomSource.create()) {
        private var fading = -1

        init {
            relative = true
            attenuation = SoundInstance.Attenuation.NONE
            looping = false
            volume = level
            x = 0.0
            y = 0.0
            z = 0.0
        }

        override fun resolve(manager: SoundManager): WeighedSoundEvents? {
            manager.getSoundEvent(location)?.let { pack ->
                sound = pack.getSound(random)
                return pack
            }
            val events = WeighedSoundEvents(location, null)
            val file = Sound(synth, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, true, false, 16)
            events.addSound(file)
            sound = file
            return events
        }

        /** Faded out over that many ticks, then stopped. */
        fun fadeOut(ticks: Int) {
            if (fading < 0) fading = ticks.coerceAtLeast(1)
        }

        private var fadeStart = -1

        override fun tick() {
            if (fading < 0) return
            if (fadeStart < 0) fadeStart = fading
            fading--
            volume = level * (fading.toFloat() / fadeStart).coerceIn(0f, 1f)
            if (fading <= 0) stop()
        }

        override fun canStartSilent(): Boolean = true
    }

    /** Plays a stream: event generations_core:cinematic.<scene>.<part>, else the synthesizer's renderer. */
    fun playStream(scene: String, part: String, source: SynthBuffer, volume: Float): StreamSound? {
        if (volume <= 0f) return null
        val event = GenerationsCore.id("cinematic.$scene.$part")
        val synth = GenerationsCore.id("cinematic_synth/${scene}_$part")
        val file = GenerationsCore.id("$SYNTH_PREFIX${scene}_$part.ogg")
        streams[file] = SynthStream(source)
        val sound = StreamSound(event, synth, volume)
        Minecraft.getInstance().soundManager.play(sound)
        return sound
    }

    fun stopStream(sound: StreamSound?, fadeTicks: Int) {
        if (sound == null) return
        if (fadeTicks <= 0) Minecraft.getInstance().soundManager.stop(sound) else sound.fadeOut(fadeTicks)
    }

    /** A one-shot sound at a world position (master category, the scene's volume). */
    fun play(id: String, x: Double, y: Double, z: Double, volume: Float, pitch: Float) {
        if (volume <= 0f) return
        val location = ResourceLocation.tryParse(id) ?: return
        val sound = SimpleSoundInstance(SoundEvent.createVariableRangeEvent(location), SoundSource.MASTER, volume, pitch, RandomSource.create(), x, y, z)
        Minecraft.getInstance().soundManager.play(sound)
    }

    fun clear() {
        streams.clear()
    }
}
