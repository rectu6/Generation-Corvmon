package generations.gg.generations.core.generationscore.common.client.boss

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.boss.BossConfig
import generations.gg.generations.core.generationscore.common.world.boss.BossRarity
import generations.gg.generations.core.generationscore.common.world.boss.BossRarityConfig
import java.nio.file.Files
import java.nio.file.Paths

/**
 * The look of bosses on this client, config/generations/core/bosses/boss_client.json:
 * aura (ground glow and rising shell), particles, name label, colours. Read when the
 * game starts and on joining a world; an error keeps the defaults (never a crash).
 * Everything is drawn by the client from the boss's aspects: no network traffic.
 */
object BossClientConfig {
    private const val VERSION = 1
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "bosses", "boss_client.json")

    class Aura(
        var enabled: Boolean = true,
        /** Opacity of the aura, 0 to 2 (1 = normal). */
        var opacity: Float = 1f,
        /** The glow on the ground around the boss. */
        var groundRing: Boolean = true,
        /** The light rising around the boss (from Uncommon). */
        var shell: Boolean = true,
        /** No aura beyond this distance from the camera, in blocks. */
        var maxDistance: Float = 96f,
    )

    class Particles(
        var enabled: Boolean = true,
        /** Particles per tick multiplier, 0 to 4 (Minecraft's particle setting also applies). */
        var density: Float = 1f,
        /** No particles beyond this distance from the camera, in blocks. */
        var maxDistance: Float = 48f,
    )

    class Label(
        /** "Charizard [Epic Boss]" in the rarity's colour above the boss. */
        var enabled: Boolean = true,
    )

    class Settings(
        var version: Int = VERSION,
        var aura: Aura = Aura(),
        var particles: Particles = Particles(),
        var label: Label = Label(),
        /** Colours by rarity id, and "titan", as "#RRGGBB". */
        var colors: MutableMap<String, String> = defaultColors(),
    ) {
        @Transient private var rgb = IntArray(BossRarity.entries.size + 1)

        fun color(rarity: BossRarity): Int = rgb[rarity.ordinal]
        val titanColor: Int get() = rgb[BossRarity.entries.size]

        @Suppress("SENSELESS_COMPARISON")
        fun validate() {
            if (aura == null) aura = Aura()
            if (particles == null) particles = Particles()
            if (label == null) label = Label()
            if (colors == null) colors = defaultColors()
            if (!aura.opacity.isFinite()) aura.opacity = 1f
            aura.opacity = aura.opacity.coerceIn(0f, 2f)
            if (!aura.maxDistance.isFinite()) aura.maxDistance = 96f
            aura.maxDistance = aura.maxDistance.coerceIn(0f, 512f)
            if (!particles.density.isFinite()) particles.density = 1f
            particles.density = particles.density.coerceIn(0f, 4f)
            if (!particles.maxDistance.isFinite()) particles.maxDistance = 48f
            particles.maxDistance = particles.maxDistance.coerceIn(0f, 256f)
            val fixed = LinkedHashMap<String, String>()
            rgb = IntArray(BossRarity.entries.size + 1)
            for (rarity in BossRarity.entries) {
                val value = BossConfig.parseColor(colors[rarity.id], rarity.defaultColor)
                rgb[rarity.ordinal] = value
                fixed[rarity.id] = BossRarityConfig.hex(value)
            }
            val titan = BossConfig.parseColor(colors[TITAN], BossRarity.TITAN_COLOR)
            rgb[BossRarity.entries.size] = titan
            fixed[TITAN] = BossRarityConfig.hex(titan)
            colors = fixed
        }
    }

    private const val TITAN = "titan"

    private fun defaultColors(): MutableMap<String, String> {
        val map = LinkedHashMap<String, String>()
        BossRarity.entries.forEach { map[it.id] = BossRarityConfig.hex(it.defaultColor) }
        map[TITAN] = BossRarityConfig.hex(BossRarity.TITAN_COLOR)
        return map
    }

    var settings = Settings().also { it.validate() }; private set

    fun load() {
        val file = file
        val read = try {
            if (Files.exists(file)) gson.fromJson(Files.readString(file), Settings::class.java) ?: Settings() else Settings()
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Bosses: could not read {} ({}), default values used", file, e.message)
            settings = Settings().also { it.validate() }
            return
        }
        read.validate()
        read.version = VERSION
        settings = read
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(read))
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Bosses: could not write {}", file, e)
        }
    }
}
