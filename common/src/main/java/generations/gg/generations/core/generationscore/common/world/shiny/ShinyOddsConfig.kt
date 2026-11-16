package generations.gg.generations.core.generationscore.common.world.shiny

import generations.gg.generations.core.generationscore.common.config.JsonConfigs

/**
 * config/generations/core/shiny/shiny_odds.json: how the shiny bonuses add up
 * (ShinyOdds). The bonuses themselves are elsewhere: Shiny Charm in charms/charms.json,
 * Catch Combo tiers in catch_combo/catch_combo.json.
 */
class ShinyOddsConfig(
    var version: Int = VERSION,
    /** false: the mod changes no shiny chance (Cobblemon's own rate only). */
    var enabled: Boolean = true,
    /** "additive" (1 + Σ(bonus − 1), recommended) or "multiplicative" (Π bonus). */
    var stacking: String = "additive",
    /** The total multiplier of the bonuses is capped at maxMultiplier (false: no cap). */
    var capEnabled: Boolean = true,
    var maxMultiplier: Double = 6.0,
    /**
     * The Ultra Warp Ride's arrival encounter keeps its own absolute chance (never lowered);
     * true: the Shiny Charm and the Catch Combo add base × their extra to it.
     */
    var ultraWarpRideBonuses: Boolean = true,
    /** Every calculation with bonuses written to the log (sources, multiplier, chance). */
    var logShiny: Boolean = false,
) {
    val mode: ShinyMath.Stacking get() = if (stacking.equals("multiplicative", true)) ShinyMath.Stacking.MULTIPLICATIVE else ShinyMath.Stacking.ADDITIVE
    val cap: Double get() = if (capEnabled) maxMultiplier else 0.0

    fun validate() {
        @Suppress("SENSELESS_COMPARISON")
        if (stacking == null || (!stacking.equals("additive", true) && !stacking.equals("multiplicative", true))) stacking = "additive"
        stacking = stacking.lowercase()
        maxMultiplier = JsonConfigs.clamp(maxMultiplier, 1.0, 10_000.0, 6.0)
    }

    companion object {
        const val VERSION = 1

        fun load(): ShinyOddsConfig = JsonConfigs.load(
            "Shiny odds", JsonConfigs.file("shiny", "shiny_odds.json"), ShinyOddsConfig::class.java, ::ShinyOddsConfig, VERSION, { it.validate() },
        )
    }
}
