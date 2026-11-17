package generations.gg.generations.core.generationscore.common.world.rks

import generations.gg.generations.core.generationscore.common.config.JsonConfigs

/**
 * The RKS Machine's energy reserves (HOOH-PLAN.md § 9), kept apart in the console and never
 * converted: the psychic energy of Mewtwo's protocol, the fire energy ("énergie de renaissance")
 * of the Sacred Ash. Each is filled by sacrificing Pokémon of its type (RksSacrifice) under its
 * own rules (its configuration file).
 *
 * id: the name used in the recipes ("energy": "fire") and the commands; type: the Pokémon type
 * accepted (Cobblemon's type name, primary or secondary).
 */
enum class RksEnergy(val id: String, val type: String) {
    PSYCHIC("psychic", "psychic"),
    FIRE("fire", "fire");

    val rules: SacrificeRules
        get() = when (this) {
            PSYCHIC -> RksSettings.mewtwo
            FIRE -> RksSettings.fire
        }

    val required: Int get() = rules.energyRequired

    /** A translation key of this reserve: the psychic one keeps the original keys, the others "<key>.<id>". */
    fun key(base: String): String = if (this == PSYCHIC) base else "$base.$id"

    companion object {
        fun byId(id: String?): RksEnergy? {
            val s = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == s }
        }
    }
}

/** What a sacrifice accepts and gives (rks/mewtwo.json for the psychic energy, rks/fire_energy.json for the fire). */
interface SacrificeRules {
    /** The reserve's size: never above, and what a recipe of this energy takes. */
    val energyRequired: Int
    val points: MewtwoConfig.StagePoints
    val speciesPoints: MutableMap<String, Int>
    val excludedLabels: MutableList<String>
    val excludedSpecies: MutableList<String>
    val allowedSpecies: MutableList<String>
    val allowPc: Boolean
}

/**
 * config/generations/core/rks/fire_energy.json: the fire energy of the Sacred Ash (HOOH-PLAN.md
 * § 9). Fire Pokémon (primary or secondary type) from the party and the PC; the same scale as
 * Mewtwo's psychic energy (first stage 1, middle 2, last of a line of two or three 3, no
 * evolution 2); legendary, mythical, Ultra Beasts and Paradox Pokémon refused.
 */
class FireEnergyConfig(
    override var energyRequired: Int = 150,
    override var points: MewtwoConfig.StagePoints = MewtwoConfig.StagePoints(),
    override var speciesPoints: MutableMap<String, Int> = LinkedHashMap(),
    override var excludedLabels: MutableList<String> = mutableListOf("legendary", "mythical", "ultra_beast", "paradox"),
    override var excludedSpecies: MutableList<String> = mutableListOf(),
    override var allowedSpecies: MutableList<String> = mutableListOf(),
    override var allowPc: Boolean = true,
) : SacrificeRules {
    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        energyRequired = energyRequired.coerceIn(1, 10_000)
        if (points == null) points = MewtwoConfig.StagePoints()
        points.validate()
        if (speciesPoints == null) speciesPoints = LinkedHashMap()
        speciesPoints.replaceAll { _, value -> value.coerceIn(0, 10_000) }
        if (excludedLabels == null) excludedLabels = mutableListOf()
        if (excludedSpecies == null) excludedSpecies = mutableListOf()
        if (allowedSpecies == null) allowedSpecies = mutableListOf()
    }

    companion object {
        const val VERSION = 1

        fun load(): FireEnergyConfig = JsonConfigs.load(
            "RKS fire energy", JsonConfigs.file("rks", "fire_energy.json"), FireEnergyConfig::class.java, ::FireEnergyConfig, VERSION, { it.validate() },
        )
    }
}
