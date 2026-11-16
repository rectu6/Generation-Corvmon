package generations.gg.generations.core.generationscore.common.world.shiny

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.server.level.ServerPlayer

/**
 * The one place that computes shiny chances (COMBO-FUITE-PERF-PLAN.md, 2.2), in this order:
 * 1. Cobblemon's base rate (shinyRate, with what other mods' functions already did: ours
 *    is added last, Priority.LOWEST);
 * 2. the bonuses of the providers (Shiny Charm: Charms; Catch Combo, its species only, on
 *    the spawns of the combo player's spawner: CatchCombo; future bonuses), added up as
 *    shiny_odds.json says and capped (×6 by default);
 * 3. a special chance (the Ultra Warp Ride's arrival encounter, UltraEncounterDraw) is
 *    never lowered: the bonuses add base × their extra to it, the cap applies to them only.
 * Natural Ultra Beasts (UltraSpawns), raids and Cobblemon's fishing baits keep their own
 * rules. With logShiny, each calculation that had a bonus is written to the log.
 */
object ShinyOdds {
    /** A source of bonus: null when it does not apply to this player and species. */
    fun interface Provider {
        fun bonus(player: ServerPlayer, species: Species?, pokemon: Pokemon?): ShinyMath.Bonus?
    }

    private val providers = mutableListOf<Provider>()

    var config = ShinyOddsConfig().also { it.validate() }; private set

    fun register(provider: Provider) {
        providers += provider
    }

    fun load() {
        config = ShinyOddsConfig.load()
    }

    fun init() {
        load()
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { load() }
        CobblemonEvents.SHINY_CHANCE_CALCULATION.subscribe(Priority.LOWEST) { event ->
            event.addModificationFunction { rate, player, pokemon -> modify(rate, player, pokemon) }
        }
    }

    /** Cobblemon's rate, "1 in N". */
    val baseRate: Double get() = runCatching { Cobblemon.config.shinyRate.toDouble() }.getOrDefault(8192.0)

    fun bonuses(player: ServerPlayer?, species: Species?, pokemon: Pokemon?): List<ShinyMath.Bonus> {
        if (player == null || !config.enabled) return emptyList()
        val list = ArrayList<ShinyMath.Bonus>(2)
        for (provider in providers) {
            val bonus = runCatching { provider.bonus(player, species, pokemon) }
                .onFailure { GenerationsCore.LOGGER.error("Shiny odds: a bonus source failed", it) }
                .getOrNull() ?: continue
            if (bonus.multiplier > 1.0) list += bonus
        }
        return list
    }

    /** Cobblemon's roll (PokemonProperties.roll): the new "1 in N". */
    private fun modify(rate: Float, player: ServerPlayer?, pokemon: Pokemon): Float {
        if (!config.enabled || player == null) return rate
        val bonuses = bonuses(player, pokemon.species, pokemon)
        if (bonuses.isEmpty()) return rate
        val result = ShinyMath.normal(rate.toDouble(), bonuses, config.mode, config.cap)
        if (config.logShiny) {
            GenerationsCore.LOGGER.info("Shiny odds: {} for {} ({}): base 1/{}, {}", result.oneIn, pokemon.species.resourceIdentifier, player.gameProfile.name, Math.round(rate.toDouble()), result)
        }
        return ShinyMath.toRate(result.chance)
    }

    /** What a normal roll of this species would be for this player now (commands). */
    fun preview(player: ServerPlayer?, species: Species, pokemon: Pokemon? = null): ShinyMath.Result =
        ShinyMath.normal(baseRate, bonuses(player, species, pokemon), config.mode, config.cap)

    /**
     * A special chance (absolute, 0 to 1), e.g. the Ultra Warp Ride's: never lowered; the
     * player's bonuses add base × their extra when ultraWarpRideBonuses is on.
     */
    fun special(player: ServerPlayer?, species: Species?, chance: Float, label: String): Float {
        if (!config.enabled || !config.ultraWarpRideBonuses || player == null) return chance
        val bonuses = bonuses(player, species, null)
        if (bonuses.isEmpty()) return chance
        val result = ShinyMath.special(chance.toDouble(), baseRate, bonuses, config.mode, config.cap)
        if (config.logShiny) {
            GenerationsCore.LOGGER.info("Shiny odds ({}): {} for {} ({}): {}", label, result.oneIn, species?.resourceIdentifier ?: "?", player.gameProfile.name, result)
        }
        return result.chance.toFloat().coerceAtLeast(chance)
    }
}
