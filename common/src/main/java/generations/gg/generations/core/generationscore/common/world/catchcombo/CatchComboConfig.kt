package generations.gg.generations.core.generationscore.common.world.catchcombo

import generations.gg.generations.core.generationscore.common.config.JsonConfigs

/**
 * config/generations/core/catch_combo/catch_combo.json (provisional values). The combo
 * lives in memory and is lost on logout: nothing is saved.
 */
class CatchComboConfig(
    var version: Int = VERSION,
    var enabled: Boolean = true,
    /** Highest count (the count stops there, the combo goes on). */
    var maxCount: Int = 999,
    /** From threshold captures in a row: shiny bonus (added up by shiny_odds.json) and IVs at 31. */
    var tiers: MutableList<CatchComboMath.Tier> = defaultTiers(),
    var breaks: Breaks = Breaks(),
    /** Species of these Cobblemon labels never count nor get a bonus (they keep their other bonuses). */
    var excludedLabels: MutableList<String> = mutableListOf("legendary", "mythical", "ultra_beast"),
    /** Species ids that never count ("cobblemon:ditto"). */
    var excludedSpecies: MutableList<String> = mutableListOf(),
    /** Species whose forms are separate combos ("cobblemon:vulpix": Alolan Vulpix apart). Others: forms, genders, levels and variants together. */
    var separateForms: MutableList<String> = mutableListOf(),
    /** Dimensions where the combo counts and gives its bonuses (empty: all). */
    var dimensions: MutableList<String> = mutableListOf(),
    /** Spawns that got a combo bonus are kept for its player that long (another player can neither throw a ball nor fight it); 0 = off. */
    var reservationSeconds: Int = 60,
    /** Bonuses on Pokémon fished by the combo player too (Cobblemon's fishing spawns). */
    var fishingBonuses: Boolean = true,
    /** A chat line when a tier is reached and when the combo is lost (the HUD shows them too). */
    var chatMessages: Boolean = true,
    /** Captures and spawns of the combo written to the log. */
    var log: Boolean = false,
) {
    class Breaks(
        /** Catching another species: the combo restarts with it at 1. */
        var otherSpecies: Boolean = true,
        /** A wild Pokémon knocked out in battle. */
        var wildFainted: Boolean = true,
        /** A boss knocked out (world/boss). */
        var bossFainted: Boolean = true,
        /** A wild battle that ends with no capture, no victory, no defeat and no flight (the wild Pokémon left). */
        var battleStopped: Boolean = true,
        /** The start of a battle against a player. */
        var pvpBattle: Boolean = true,
        /** Logging out (the combo is in memory only: false keeps it until the server stops). */
        var disconnect: Boolean = true,
    )

    val sortedTiers: List<CatchComboMath.Tier> get() = cached ?: CatchComboMath.sorted(tiers).also { cached = it }

    @Transient
    private var cached: List<CatchComboMath.Tier>? = null

    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        maxCount = maxCount.coerceIn(1, 1_000_000)
        if (tiers == null) tiers = defaultTiers()
        tiers.removeIf { it == null }
        for (tier in tiers) {
            tier.threshold = tier.threshold.coerceIn(1, maxCount)
            tier.shinyMultiplier = JsonConfigs.clamp(tier.shinyMultiplier, 1.0, 1000.0, 1.0)
            tier.perfectIvs = tier.perfectIvs.coerceIn(0, 6)
        }
        tiers = CatchComboMath.sorted(tiers).toMutableList()
        cached = null
        if (breaks == null) breaks = Breaks()
        if (excludedLabels == null) excludedLabels = mutableListOf("legendary", "mythical", "ultra_beast")
        if (excludedSpecies == null) excludedSpecies = mutableListOf()
        if (separateForms == null) separateForms = mutableListOf()
        if (dimensions == null) dimensions = mutableListOf()
        excludedLabels = excludedLabels.filterNotNull().map { it.trim().lowercase() }.toMutableList()
        excludedSpecies = excludedSpecies.filterNotNull().map(::speciesId).toMutableList()
        separateForms = separateForms.filterNotNull().map(::speciesId).toMutableList()
        dimensions = dimensions.filterNotNull().map { it.trim().lowercase() }.toMutableList()
        reservationSeconds = reservationSeconds.coerceIn(0, 3600)
    }

    companion object {
        const val VERSION = 1

        /**
         * The validated table (provisional values), each from N captures in a row: 1–10 no
         * bonus, 11–20 ×1.15, 21–30 ×1.3 and 1 IV, 31–40 ×1.5 and 1 IV, 41–50 ×1.75 and 2 IVs,
         * 51–75 ×2 and 2 IVs, 76–100 ×2.25 and 3 IVs, 101 and more ×2.5 and 3 IVs (the
         * count goes on with no more bonus).
         */
        fun defaultTiers() = mutableListOf(
            CatchComboMath.Tier(11, 1.15, 0),
            CatchComboMath.Tier(21, 1.3, 1),
            CatchComboMath.Tier(31, 1.5, 1),
            CatchComboMath.Tier(41, 1.75, 2),
            CatchComboMath.Tier(51, 2.0, 2),
            CatchComboMath.Tier(76, 2.25, 3),
            CatchComboMath.Tier(101, 2.5, 3),
        )

        /** "pidgey" means cobblemon:pidgey. */
        fun speciesId(text: String): String {
            val id = text.trim().lowercase()
            return if (':' in id) id else "cobblemon:$id"
        }

        fun load(): CatchComboConfig = JsonConfigs.load(
            "Catch Combo", JsonConfigs.file("catch_combo", "catch_combo.json"), CatchComboConfig::class.java, ::CatchComboConfig, VERSION, { it.validate() },
        )
    }
}
