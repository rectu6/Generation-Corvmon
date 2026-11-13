package generations.gg.generations.core.generationscore.common.world.ultraspace.weather

import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.util.Mth
import net.minecraft.world.level.Level

/**
 * The weather a world shows, on both sides: the server's (UltraWeather) and the one the
 * client was sent for its world (S2CUltraWeatherPacket). LevelMixin asks it for the rain
 * and thunder levels of the Ultra worlds, which are then their own and no longer the
 * Overworld's (ServerLevelMixin stops Minecraft's cycle there).
 */
object UltraWeatherSky {
    /** A weather in progress: from start to end (game ticks), coming and going in fade ticks. */
    class Active(
        val world: String,
        val event: UltraWeatherEvent,
        val start: Long,
        val end: Long,
        /** The wind's direction (radians), the same for every player. */
        val wind: Float,
        val fade: Int,
    ) {
        /** From 0 (not there) to 1 (full strength), at this game time. */
        fun intensity(time: Double): Float {
            if (time < start || time >= end) return 0f
            if (fade <= 0) return 1f
            val rising = ((time - start) / fade).toFloat()
            val falling = ((end - time) / fade).toFloat()
            return Mth.clamp(minOf(rising, falling), 0f, 1f)
        }
    }

    /** The client's: the weather of its world, null when calm (written by the packet handler). */
    @Volatile
    @JvmField
    var client: Active? = null

    /** The weather a level shows now, null when calm (or not an Ultra world). */
    fun active(level: Level): Active? {
        val id = UltraWorlds.idOf(level.dimension()) ?: return null
        return if (level.isClientSide) client?.takeIf { it.world == id } else UltraWeather.active(id)
    }

    fun intensity(level: Level, partialTick: Float): Float =
        active(level)?.intensity(level.gameTime + partialTick.toDouble()) ?: 0f

    @JvmStatic
    fun rainLevel(level: Level, partialTick: Float): Float {
        val active = active(level) ?: return 0f
        return active.event.rain * active.intensity(level.gameTime + partialTick.toDouble())
    }

    @JvmStatic
    fun thunderLevel(level: Level, partialTick: Float): Float {
        val active = active(level) ?: return 0f
        return active.event.thunder * active.intensity(level.gameTime + partialTick.toDouble())
    }

    /** Whether Minecraft's rain falls in the client's world now (LevelRendererMixin). */
    @JvmStatic
    fun precipitation(level: Level): Boolean {
        val active = active(level) ?: return false
        return active.event.precipitation && active.intensity(level.gameTime.toDouble()) > 0f
    }
}
