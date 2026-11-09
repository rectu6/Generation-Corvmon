package generations.gg.generations.core.generationscore.common.world.raid.config

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.google.gson.JsonElement
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.util.RandomSource
import net.minecraft.world.level.biome.Biome

/**
 * One line of the raid Pokémon tables (pokemon.json). species is one id or a list
 * of ids sharing the other values.
 */
class RaidSpawnEntry(
    /** Biome ids or tags ("#cobblemon:is_ocean"); empty: every biome. */
    var biomes: MutableList<String> = mutableListOf(),
    var excludeBiomes: MutableList<String> = mutableListOf(),
    var species: JsonElement? = null,
    /** Aspects of the form, e.g. ["alolan"]. */
    var aspects: MutableList<String> = mutableListOf(),
    /** Tiers (1 to 6) this entry can appear at. */
    var stars: MutableList<Int> = mutableListOf(),
    var weight: Double = 10.0,
    /** Levels, else the tier's. */
    var minLevel: Int? = null,
    var maxLevel: Int? = null,
    /** The boss has the Gigantamax Factor (G-Max form when the species has one). */
    var gigantamax: Boolean = false,
) {
    @Transient var speciesIds: List<String> = emptyList()

    fun matches(biome: Holder<Biome>): Boolean {
        val included = biomes.isEmpty() || biomes.any { matchesOne(biome, it) }
        return included && excludeBiomes.none { matchesOne(biome, it) }
    }

    private fun matchesOne(biome: Holder<Biome>, filter: String): Boolean =
        if (filter.startsWith("#")) {
            ResourceLocation.tryParse(filter.substring(1))?.let { biome.`is`(TagKey.create(Registries.BIOME, it)) } ?: false
        } else {
            biome.unwrapKey().map { it.location().toString() == filter }.orElse(false)
        }
}

/** A Pokémon picked for a raid. */
class RaidPick(val species: Species, val aspects: Set<String>, val levels: IntRange, val gigantamax: Boolean)

class RaidPokemonTables(
    /** Entries by biome. */
    var tables: MutableList<RaidSpawnEntry> = mutableListOf(),
    /** Used when no entry of the biome fits the tier. */
    var fallback: MutableList<RaidSpawnEntry> = mutableListOf(),
    /** 6-star raids (legendaries and mythicals), by biome too; then any legendary. */
    var legendaries: MutableList<RaidSpawnEntry> = mutableListOf(),
) {
    fun validate() {
        listOf(tables, fallback, legendaries).forEach { list ->
            list.forEach { entry ->
                val ids = when {
                    entry.species == null -> emptyList()
                    entry.species!!.isJsonArray -> entry.species!!.asJsonArray.map { it.asString }
                    else -> listOf(entry.species!!.asString)
                }
                entry.speciesIds = ids.filter { id ->
                    val known = ResourceLocation.tryParse(if (":" in id) id else "cobblemon:$id")?.let { PokemonSpecies.getByIdentifier(it) } != null
                    if (!known) RaidConfig.warn("pokemon.json: unknown species {}, skipped", id)
                    known
                }
                entry.stars = entry.stars.filter { it in 1..6 }.toMutableList()
                entry.weight = entry.weight.coerceAtLeast(0.0)
            }
        }
    }

    /**
     * A Pokémon for this tier in this biome: an entry of the biome, else of the
     * fallback table, else (still nothing) any implemented species of the tier.
     */
    fun pick(stars: Int, biome: Holder<Biome>?, random: RandomSource, tierLevels: IntRange): RaidPick? {
        val sources = if (stars >= 6) listOf(legendaries.filter { biome == null || it.matches(biome) }, legendaries.filter { it.biomes.isEmpty() })
        else listOf(tables.filter { biome != null && it.matches(biome) }, fallback)
        for (source in sources) {
            val candidates = source.filter { stars in it.stars && it.speciesIds.isNotEmpty() && it.weight > 0 }
                .flatMap { entry -> entry.speciesIds.map { entry to it } }
            if (candidates.isEmpty()) continue
            var roll = random.nextDouble() * candidates.sumOf { it.first.weight }
            for ((entry, id) in candidates) {
                roll -= entry.weight
                if (roll <= 0) return toPick(entry, id, tierLevels) ?: continue
            }
            return toPick(candidates.last().first, candidates.last().second, tierLevels)
        }
        return anyOfTier(stars, random, tierLevels)
    }

    private fun toPick(entry: RaidSpawnEntry, id: String, tierLevels: IntRange): RaidPick? {
        val species = ResourceLocation.tryParse(if (":" in id) id else "cobblemon:$id")?.let { PokemonSpecies.getByIdentifier(it) } ?: return null
        val min = entry.minLevel ?: tierLevels.first
        val max = (entry.maxLevel ?: tierLevels.last).coerceAtLeast(min)
        return RaidPick(species, entry.aspects.toSet(), min..max, entry.gigantamax)
    }

    /** Last resort: any species of the tier by base stat total (legendaries at 6 stars). */
    private fun anyOfTier(stars: Int, random: RandomSource, levels: IntRange): RaidPick? {
        val totals = listOf(0..329, 330..409, 410..479, 480..534, 535..Int.MAX_VALUE)
        val pool = PokemonSpecies.implemented.filter { species ->
            val legendary = "legendary" in species.labels || "mythical" in species.labels
            if (stars >= 6) legendary else !legendary && species.baseStats.values.sum() in totals[stars - 1]
        }
        if (pool.isEmpty()) return null
        return RaidPick(pool[random.nextInt(pool.size)], emptySet(), levels, false)
    }
}
