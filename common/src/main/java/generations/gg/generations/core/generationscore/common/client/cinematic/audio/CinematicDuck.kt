package generations.gg.generations.core.generationscore.common.client.cinematic.audio

import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundSource
import kotlin.math.abs

/**
 * Minecraft's other sounds made quieter while a scene asks for it (CinematicScene.duck: Lugia's
 * silence of the depths), on this client only: down to a quarter at most. The sounds already
 * playing are refreshed a few times a second while it changes (SoundManager.updateSourceVolume),
 * and once more when it is over. SoundEngineMixin applies it.
 */
object CinematicDuck {
    private const val DEEPEST = 0.75f

    /** From 0 (nothing) to 1 (the deepest). */
    @Volatile
    private var depth = 0f
    private var applied = 0f
    private var lastRefresh = 0L

    @JvmStatic
    fun factor(source: SoundSource?): Float {
        val d = depth
        if (d <= 0f || source == null) return 1f
        return when (source) {
            SoundSource.AMBIENT, SoundSource.WEATHER, SoundSource.MUSIC, SoundSource.RECORDS, SoundSource.NEUTRAL, SoundSource.HOSTILE -> 1f - DEEPEST * d
            else -> 1f
        }
    }

    /** This frame's depth (0 when no scene asks for it). */
    fun set(value: Float) {
        val d = value.coerceIn(0f, 1f)
        depth = d
        val now = System.currentTimeMillis()
        val changed = abs(d - applied)
        if ((changed > 0.04f && now - lastRefresh > 250L) || (d == 0f && applied != 0f)) {
            applied = d
            lastRefresh = now
            try {
                val minecraft = Minecraft.getInstance()
                minecraft.soundManager.updateSourceVolume(SoundSource.AMBIENT, minecraft.options.getSoundSourceVolume(SoundSource.AMBIENT))
            } catch (e: Exception) {
                // The sound engine not ready: the next change refreshes them.
            }
        }
    }
}
