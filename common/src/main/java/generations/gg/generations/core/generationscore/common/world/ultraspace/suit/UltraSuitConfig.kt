package generations.gg.generations.core.generationscore.common.world.ultraspace.suit

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import generations.gg.generations.core.generationscore.common.GenerationsCore
import java.nio.file.Files
import java.nio.file.Paths

/**
 * config/generations/core/ultra_space/ultra_suit.json (server): the dimensional
 * suffocation of the seven Ultra worlds and the rules of the suit's shields. Read when
 * the server starts and by /ultrawarp reload; written back with the options added since
 * and the values brought into range.
 */
object UltraSuitConfig {
    class Breathing(
        /** Without the four pieces of the Ultra Suit, the air of the Ultra worlds cannot be breathed. */
        var enabled: Boolean = true,
        /** Air lost each tick (a full reserve is 300; underwater, Minecraft takes 1). */
        var airLossPerTick: Int = 1,
        /** Air given back each tick with the full suit (Minecraft gives 4). */
        var airRecoveryPerTick: Int = 4,
        /** Damage (in half hearts) once the air is gone, every damageIntervalTicks. */
        var damage: Float = 2f,
        var damageIntervalTicks: Int = 20,
        /** Respiration (the helmet's enchantment) slows the loss, as underwater. */
        var respirationSlows: Boolean = false,
        /** Water Breathing (the effect, a conduit) protects too. */
        var waterBreathingProtects: Boolean = false,
        /** Seconds without loss after respawning or logging in inside an Ultra world. */
        var respawnGraceSeconds: Int = 60,
        /** A message on arriving in an Ultra world without the full suit. */
        var warnOnArrival: Boolean = true,
    ) {
        fun validate() {
            airLossPerTick = airLossPerTick.coerceIn(1, 300)
            airRecoveryPerTick = airRecoveryPerTick.coerceIn(1, 300)
            damage = damage.coerceIn(0f, 1000f)
            damageIntervalTicks = damageIntervalTicks.coerceIn(1, 1200)
            respawnGraceSeconds = respawnGraceSeconds.coerceIn(0, 3600)
        }
    }

    class Shields(
        /** A shield protects only when the four pieces worn are Ultra pieces that all carry it. */
        var requireAllPieces: Boolean = true,
        /** Without requireAllPieces: the part of the damage each piece carrying the shield removes. */
        var partialPerPiece: Float = 0.25f,
    ) {
        fun validate() {
            partialPerPiece = partialPerPiece.coerceIn(0f, 1f)
        }
    }

    class Settings(
        var version: Int = 0,
        var breathing: Breathing = Breathing(),
        var shields: Shields = Shields(),
    ) {
        fun validate() {
            breathing.validate()
            shields.validate()
        }
    }

    private const val VERSION = 1
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "ultra_space", "ultra_suit.json")

    var settings = Settings(VERSION).also { it.validate() }; private set

    fun load() {
        val file = file
        settings = try {
            if (Files.exists(file)) gson.fromJson(Files.readString(file), Settings::class.java) ?: Settings(VERSION) else Settings(VERSION)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Space: could not read {} ({}), default values used", file, e.message)
            settings = Settings(VERSION).also { it.validate() }
            return
        }
        // Gson leaves the objects missing from an old file null.
        @Suppress("SENSELESS_COMPARISON")
        settings.apply {
            if (breathing == null) breathing = Breathing()
            if (shields == null) shields = Shields()
        }
        settings.validate()
        settings.version = VERSION
        GenerationsCore.LOGGER.info(
            "Ultra Space: suit breathing {}, shields {}",
            if (settings.breathing.enabled) "on" else "off",
            if (settings.shields.requireAllPieces) "on the four pieces" else "per piece",
        )
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(settings))
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Space: could not write {}", file, e)
        }
    }
}
