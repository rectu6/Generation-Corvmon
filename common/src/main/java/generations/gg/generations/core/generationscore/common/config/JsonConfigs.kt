package generations.gg.generations.core.generationscore.common.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import generations.gg.generations.core.generationscore.common.GenerationsCore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The mod's JSON settings files in config/generations/core/ (charms, shiny odds, Catch
 * Combo, flee): a missing file is written with the default values, a file with an error
 * keeps the defaults and is reported in the log (never a crash), every value is checked
 * by the class's validate(), and the file is written back with the current version and
 * any new key (a migration step can rewrite old values first).
 */
object JsonConfigs {
    val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun file(vararg parts: String): Path = Paths.get("config", "generations", "core", *parts)

    /**
     * Reads the file, or its defaults. migrate gets the raw JSON and the version found
     * (0 when none) before it is read, and may change it in place.
     */
    fun <T : Any> load(
        label: String,
        file: Path,
        type: Class<T>,
        defaults: () -> T,
        version: Int,
        validate: (T) -> Unit,
        migrate: (JsonObject, Int) -> Unit = { _, _ -> },
    ): T {
        val value: T = try {
            if (Files.exists(file)) {
                val json = JsonParser.parseString(Files.readString(file))
                if (json.isJsonObject) {
                    val obj = json.asJsonObject
                    val found = obj.get("version")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
                    if (found < version) migrate(obj, found)
                    obj.addProperty("version", version)
                    gson.fromJson(obj, type) ?: defaults()
                } else {
                    GenerationsCore.LOGGER.error("{}: {} is not a JSON object, default values used", label, file)
                    defaults()
                }
            } else defaults()
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("{}: could not read {} ({}), default values used", label, file, e.message)
            val fallback = defaults()
            validate(fallback)
            return fallback
        }
        try {
            validate(value)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("{}: {} has invalid values ({}), default values used", label, file, e.toString())
            return defaults().also(validate)
        }
        try {
            Files.createDirectories(file.parent)
            val tree = gson.toJsonTree(value).asJsonObject
            tree.addProperty("version", version)
            Files.writeString(file, gson.toJson(tree))
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("{}: could not write {}", label, file, e)
        }
        return value
    }

    /** Clamps a float, NaN and infinities to the fallback. */
    fun clamp(value: Float, min: Float, max: Float, fallback: Float): Float = if (!value.isFinite()) fallback else value.coerceIn(min, max)

    fun clamp(value: Double, min: Double, max: Double, fallback: Double): Double = if (!value.isFinite()) fallback else value.coerceIn(min, max)
}
