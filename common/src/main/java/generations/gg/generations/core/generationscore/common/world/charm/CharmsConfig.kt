package generations.gg.generations.core.generationscore.common.world.charm

import generations.gg.generations.core.generationscore.common.config.JsonConfigs

/**
 * config/generations/core/charms/charms.json: what each charm does (provisional values)
 * and the charm case. A charm counts when it is in the case or anywhere in the inventory;
 * two of the same do not add up.
 */
class CharmsConfig(
    var version: Int = VERSION,
    /** Shiny Charm: multiplier of the shiny chance, added up with the other bonuses (shiny_odds.json). */
    var shinyCharm: Double = 2.0,
    /** Exp Charm: multiplier of the experience won in battle. */
    var expCharm: Double = 1.5,
    /** Catching Charm: multiplier of the critical capture chance. */
    var catchingCharm: Double = 2.0,
    /** Mark Charm: multiplier of the chance of marks. */
    var markCharm: Double = 3.0,
    /** Glimmering Charm: multiplier of the quantities of the Dynamax raids' rewards. */
    var glimmeringCharm: Double = 1.5,
    /** false: only the charm case counts, not the rest of the inventory. */
    var countInInventory: Boolean = true,
    /** The charm case keeps its content on death (like the ender chest); false: dropped like the inventory. */
    var keepOnDeath: Boolean = true,
) {
    fun validate() {
        shinyCharm = JsonConfigs.clamp(shinyCharm, 1.0, 100.0, 2.0)
        expCharm = JsonConfigs.clamp(expCharm, 1.0, 100.0, 1.5)
        catchingCharm = JsonConfigs.clamp(catchingCharm, 1.0, 100.0, 2.0)
        markCharm = JsonConfigs.clamp(markCharm, 1.0, 1000.0, 3.0)
        glimmeringCharm = JsonConfigs.clamp(glimmeringCharm, 1.0, 100.0, 1.5)
    }

    companion object {
        const val VERSION = 1

        fun load(): CharmsConfig = JsonConfigs.load(
            "Charms", JsonConfigs.file("charms", "charms.json"), CharmsConfig::class.java, ::CharmsConfig, VERSION, { it.validate() },
        )
    }
}
