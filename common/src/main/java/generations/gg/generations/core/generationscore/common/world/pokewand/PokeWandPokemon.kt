package generations.gg.generations.core.generationscore.common.world.pokewand

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.FriendlyByteBuf

/** Everything the Pokewand shows and edits about a Pokémon, as sent between client and server. */
data class PokeWandPokemon(
    /** Species id, e.g. cobblemon:giratina. */
    val species: String,
    /** Form showdown id (FormData.formOnlyShowdownId), empty for the standard form. */
    val form: String,
    /** Forced aspects: texture variants and other cosmetic aspects. */
    val aspects: List<String>,
    val shiny: Boolean,
    /** Gender enum name. */
    val gender: String,
    val level: Int,
    /** Nature id, e.g. cobblemon:adamant. */
    val nature: String,
    /** Ability name, e.g. levitate. */
    val ability: String,
    /** Up to four move names. */
    val moves: List<String>,
    /** In STATS order. */
    val ivs: List<Int>,
    val evs: List<Int>,
    /** Item id, empty for none. */
    val heldItem: String,
    /** Empty for none. */
    val nickname: String,
    /** Poké Ball id, e.g. cobblemon:poke_ball. */
    val pokeball: String,
    val friendship: Int,
) {
    fun encode(buf: FriendlyByteBuf) {
        buf.writeUtf(species)
        buf.writeUtf(form)
        buf.writeCollection(aspects, FriendlyByteBuf::writeUtf)
        buf.writeBoolean(shiny)
        buf.writeUtf(gender)
        buf.writeVarInt(level)
        buf.writeUtf(nature)
        buf.writeUtf(ability)
        buf.writeCollection(moves, FriendlyByteBuf::writeUtf)
        ivs.forEach(buf::writeVarInt)
        evs.forEach(buf::writeVarInt)
        buf.writeUtf(heldItem)
        buf.writeUtf(nickname)
        buf.writeUtf(pokeball)
        buf.writeVarInt(friendship)
    }

    companion object {
        /** IV and EV order: HP, Attack, Defence, Sp. Attack, Sp. Defence, Speed. */
        @JvmField
        val STATS = listOf(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED)
        const val MAX_IV = 31
        const val MAX_EV = 252
        const val MAX_MOVES = 4

        fun decode(buf: FriendlyByteBuf): PokeWandPokemon = PokeWandPokemon(
            species = buf.readUtf(),
            form = buf.readUtf(),
            aspects = buf.readList(FriendlyByteBuf::readUtf),
            shiny = buf.readBoolean(),
            gender = buf.readUtf(),
            level = buf.readVarInt(),
            nature = buf.readUtf(),
            ability = buf.readUtf(),
            moves = buf.readList(FriendlyByteBuf::readUtf),
            ivs = List(STATS.size) { buf.readVarInt() },
            evs = List(STATS.size) { buf.readVarInt() },
            heldItem = buf.readUtf(),
            nickname = buf.readUtf(),
            pokeball = buf.readUtf(),
            friendship = buf.readVarInt(),
        )

        fun of(pokemon: Pokemon): PokeWandPokemon = PokeWandPokemon(
            species = pokemon.species.resourceIdentifier.toString(),
            form = if (pokemon.form == pokemon.species.standardForm) "" else pokemon.form.formOnlyShowdownId(),
            aspects = pokemon.forcedAspects.toList(),
            shiny = pokemon.shiny,
            gender = pokemon.gender.name,
            level = pokemon.level,
            nature = pokemon.nature.name.toString(),
            ability = pokemon.ability.name,
            moves = pokemon.moveSet.getMoves().map { it.name },
            ivs = STATS.map { pokemon.ivs.get(it) ?: 0 },
            evs = STATS.map { pokemon.evs.get(it) ?: 0 },
            heldItem = pokemon.heldItem().let { if (it.isEmpty) "" else BuiltInRegistries.ITEM.getKey(it.item).toString() },
            nickname = pokemon.nickname?.string ?: "",
            pokeball = pokemon.caughtBall.name.toString(),
            friendship = pokemon.friendship,
        )
    }
}
