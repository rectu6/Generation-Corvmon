package generations.gg.generations.core.generationscore.common.world.boss

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import java.util.Random

/**
 * Legendary bosses are Mega forms only (user's choice, 2026-11-13): every Mega of the
 * loaded species (Cobblemon 1.8.4 has 48, among them Mewtwo X/Y, Rayquaza, Latios,
 * Latias and Diancie), drawn by the weights of species.json (megaWeights, 1 when not
 * given, 0 = never).
 *
 * A boss keeps its Mega form in the world through a forced aspect (mega, mega_x,
 * mega_y): Cobblemon then picks the Mega form (model, stats, ability) and Showdown
 * starts the battle with it (a Mega cannot Mega Evolve again). Generations' own Mega
 * Evolution in battle uses species features, which never touch forced aspects.
 */
object BossMegas {
    class MegaForm(val species: Species, val form: FormData, val aspect: String) {
        /** "charizard:mega_x": key of species.json's megaWeights and rewards.json's stones. */
        val key: String = "${species.resourceIdentifier.path}:$aspect"
        val legendary: Boolean = species.standardForm.labels.any { it.equals("legendary", true) || it.equals("mythical", true) }

        /** " X", " Y" or "" after the species' name. */
        val suffix: String get() = suffixOf(aspect)

        fun displayName(): MutableComponent = name(species.translatedName, aspect)
    }

    private val random = Random()
    private var cache: List<MegaForm>? = null

    /** The species may have been reloaded (data packs): computed again on next use. */
    fun invalidate() {
        cache = null
    }

    fun isMegaAspect(aspect: String) = aspect == "mega" || aspect.startsWith("mega_")

    fun suffixOf(aspect: String): String = when (aspect) {
        "mega_x" -> " X"
        "mega_y" -> " Y"
        else -> ""
    }

    /** "Mega Charizard Y" from a name and the Mega aspect. */
    fun name(base: Component, aspect: String): MutableComponent =
        Component.translatable("generations_core.boss.mega_name", base, suffixOf(aspect))

    fun all(): List<MegaForm> {
        cache?.let { return it }
        val forms = ArrayList<MegaForm>()
        for (species in PokemonSpecies.implemented) {
            for (form in species.forms) {
                val aspect = form.aspects.firstOrNull(::isMegaAspect) ?: continue
                if (forms.none { it.species == species && it.aspect == aspect }) forms += MegaForm(species, form, aspect)
            }
        }
        if (forms.isNotEmpty()) cache = forms
        return forms
    }

    fun formsOf(species: Species): List<MegaForm> = all().filter { it.species == species }

    fun byKey(key: String): MegaForm? = all().firstOrNull { it.key == key.trim().lowercase() }

    /** The Mega form a Pokémon is kept in (forced aspect), or null. */
    fun of(pokemon: Pokemon): MegaForm? {
        val aspect = pokemon.forcedAspects.firstOrNull(::isMegaAspect) ?: return null
        return all().firstOrNull { it.species == pokemon.species && it.aspect == aspect }
    }

    /**
     * A Mega by the weights of species.json, among those that suit the place: in
     * water, Pokémon that swim; elsewhere, Pokémon that walk or fly. Null if none.
     */
    fun draw(inWater: Boolean? = null, among: List<MegaForm> = all()): MegaForm? {
        val candidates = among.filter { mega ->
            val moving = mega.form.behaviour.moving
            when (inWater) {
                null -> true
                true -> moving.swim.canSwimInWater
                false -> moving.walk.canWalk || moving.fly.canFly
            }
        }.map { it to BossConfig.species.megaWeight(it.key) }.filter { it.second > 0.0 }
        val total = candidates.sumOf { it.second }
        if (total <= 0.0) return null
        var roll = random.nextDouble() * total
        for ((mega, weight) in candidates) {
            roll -= weight
            if (roll < 0.0) return mega
        }
        return candidates.last().first
    }

    /** A new wild Pokémon in this Mega form, at this level. */
    fun create(mega: MegaForm, level: Int): Pokemon {
        val pokemon = PokemonProperties.parse("species=\"${mega.species.resourceIdentifier}\" level=$level").create()
        apply(pokemon, mega)
        return pokemon
    }

    /** Keeps the Pokémon in this Mega form (its species must be the Mega's). */
    fun apply(pokemon: Pokemon, mega: MegaForm) {
        pokemon.forcedAspects = pokemon.forcedAspects.filterNot(::isMegaAspect).toSet() + mega.aspect
        pokemon.updateAspects()
    }

    /** Every Mega of Cobblemon 1.8.4 with its Mega Stone in Generations (Rayquaza has none). */
    val DEFAULT_STONES: Map<String, String> = linkedMapOf(
        "abomasnow:mega" to "generations_core:abomasite",
        "absol:mega" to "generations_core:absolite",
        "aerodactyl:mega" to "generations_core:aerodactylite",
        "aggron:mega" to "generations_core:aggronite",
        "alakazam:mega" to "generations_core:alakazite",
        "altaria:mega" to "generations_core:altarianite",
        "ampharos:mega" to "generations_core:ampharosite",
        "audino:mega" to "generations_core:audinite",
        "banette:mega" to "generations_core:banettite",
        "beedrill:mega" to "generations_core:beedrillite",
        "blastoise:mega" to "generations_core:blastoisinite",
        "blaziken:mega" to "generations_core:blazikenite",
        "camerupt:mega" to "generations_core:cameruptite",
        "charizard:mega_x" to "generations_core:charizardite_x",
        "charizard:mega_y" to "generations_core:charizardite_y",
        "diancie:mega" to "generations_core:diancite",
        "gallade:mega" to "generations_core:galladite",
        "garchomp:mega" to "generations_core:garchompite",
        "gardevoir:mega" to "generations_core:gardevoirite",
        "gengar:mega" to "generations_core:gengarite",
        "glalie:mega" to "generations_core:glalitite",
        "gyarados:mega" to "generations_core:gyaradosite",
        "heracross:mega" to "generations_core:heracronite",
        "houndoom:mega" to "generations_core:houndoominite",
        "kangaskhan:mega" to "generations_core:kangaskhanite",
        "latias:mega" to "generations_core:latiasite",
        "latios:mega" to "generations_core:latiosite",
        "lopunny:mega" to "generations_core:lopunnite",
        "lucario:mega" to "generations_core:lucarionite",
        "manectric:mega" to "generations_core:manectite",
        "mawile:mega" to "generations_core:mawilite",
        "medicham:mega" to "generations_core:medichamite",
        "metagross:mega" to "generations_core:metagrossite",
        "mewtwo:mega_x" to "generations_core:mewtwonite_x",
        "mewtwo:mega_y" to "generations_core:mewtwonite_y",
        "pidgeot:mega" to "generations_core:pidgeotite",
        "pinsir:mega" to "generations_core:pinsirite",
        "rayquaza:mega" to "",
        "sableye:mega" to "generations_core:sablenite",
        "salamence:mega" to "generations_core:salamencite",
        "sceptile:mega" to "generations_core:sceptilite",
        "scizor:mega" to "generations_core:scizorite",
        "sharpedo:mega" to "generations_core:sharpedonite",
        "slowbro:mega" to "generations_core:slowbronite",
        "steelix:mega" to "generations_core:steelixite",
        "swampert:mega" to "generations_core:swampertite",
        "tyranitar:mega" to "generations_core:tyranitarite",
        "venusaur:mega" to "generations_core:venusaurite",
    )
}
