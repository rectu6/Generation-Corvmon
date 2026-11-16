package generations.gg.generations.core.generationscore.common.battle.flee

import generations.gg.generations.core.generationscore.common.config.JsonConfigs

/** config/generations/core/battle/flee.json: the instant flight from wild battles (InstantFlee). */
class FleeConfig(
    var version: Int = VERSION,
    /** The Run button ends the battle at once; false: Cobblemon's own (walk away from the wild Pokémon). */
    var enabled: Boolean = true,
    /** "always" (works unless trapped) or "speed" (the games' formula, one attempt per turn). */
    var formula: String = "always",
    /** Trapping moves and abilities (Mean Look, Shadow Tag, Arena Trap, Wrap…) prevent it, as Showdown says. */
    var respectTrapping: Boolean = true,
    /** A held Smoke Ball always escapes, even trapped. */
    var smokeBall: Boolean = true,
    /** The Run Away ability always escapes, even trapped. */
    var runAway: Boolean = true,
    /** Instant flight from bosses (world/boss) too. */
    var bosses: Boolean = true,
    /** Instant flight from the Ultra Warp Ride's arrival encounter too. */
    var ultraEncounters: Boolean = true,
) {
    val mode: FleeMath.Formula get() = if (formula.equals("speed", true)) FleeMath.Formula.SPEED else FleeMath.Formula.ALWAYS

    fun validate() {
        @Suppress("SENSELESS_COMPARISON")
        if (formula == null || (!formula.equals("always", true) && !formula.equals("speed", true))) formula = "always"
        formula = formula.lowercase()
    }

    companion object {
        const val VERSION = 1

        fun load(): FleeConfig = JsonConfigs.load(
            "Flee", JsonConfigs.file("battle", "flee.json"), FleeConfig::class.java, ::FleeConfig, VERSION, { it.validate() },
        )
    }
}
