package generations.gg.generations.core.generationscore.common.world.pokewand

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.storage.pc.link.PCLinkManager
import com.cobblemon.mod.common.net.messages.client.storage.pc.OpenPCPacket
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.packets.pokewand.C2SPokeWandActionPacket
import generations.gg.generations.core.generationscore.common.network.packets.pokewand.S2CPokeWandPartyPacket
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * Server side of the Pokewand: an operator tool that shows and edits a player's
 * party and opens their PC. Every action checks the permission again.
 */
object PokeWand {
    /** Operators (gamemaster level), as for /give or Cobblemon's /pokeedit. */
    private const val PERMISSION_LEVEL = 2
    private const val PARTY_SIZE = 6
    private const val NEW_POKEMON_LEVEL = 5

    @JvmStatic
    fun canUse(player: ServerPlayer): Boolean = player.hasPermissions(PERMISSION_LEVEL)

    fun open(viewer: ServerPlayer, target: ServerPlayer) {
        if (!canUse(viewer)) {
            viewer.displayClientMessage(Component.translatable("generations_core.pokewand.no_permission"), true)
            return
        }
        val party = Cobblemon.storage.getParty(target)
        val slots = List(PARTY_SIZE) { slot -> party.get(slot)?.let(PokeWandPokemon::of) }
        S2CPokeWandPartyPacket(target.uuid, target.gameProfile.name, slots).sendToPlayer(viewer)
    }

    fun handle(viewer: ServerPlayer, packet: C2SPokeWandActionPacket) {
        if (!canUse(viewer)) return
        val target = viewer.server.playerList.getPlayer(packet.target)
        if (target == null) {
            viewer.displayClientMessage(Component.translatable("generations_core.pokewand.offline"), true)
            return
        }

        when (packet.action) {
            C2SPokeWandActionPacket.Action.EDIT -> {
                val data = packet.pokemon ?: return
                if (packet.slot !in 0 until PARTY_SIZE) return
                try {
                    edit(viewer, target, packet.slot, data)
                    viewer.displayClientMessage(Component.translatable("generations_core.pokewand.saved"), true)
                } catch (e: IllegalArgumentException) {
                    viewer.displayClientMessage(Component.literal(e.message ?: "?"), true)
                }
                open(viewer, target)
            }
            C2SPokeWandActionPacket.Action.DELETE -> {
                val party = Cobblemon.storage.getParty(target)
                party.get(packet.slot)?.let(party::remove)
                open(viewer, target)
            }
            C2SPokeWandActionPacket.Action.OPEN_PC -> openPc(viewer, target)
            C2SPokeWandActionPacket.Action.REFRESH -> open(viewer, target)
        }
    }

    private fun edit(viewer: ServerPlayer, target: ServerPlayer, slot: Int, data: PokeWandPokemon) {
        val speciesId = ResourceLocation.tryParse(data.species)
        val species = speciesId?.let(PokemonSpecies::getByIdentifier)
            ?: throw IllegalArgumentException("Espèce inconnue : ${data.species}")
        val party = Cobblemon.storage.getParty(target)
        val existing = party.get(slot)

        // Empty fields (level 0, negative IVs, EVs and friendship, blank text) keep
        // the current value; a new Pokémon gets Cobblemon's generated ones.
        val level = data.level.takeIf { it > 0 }?.coerceAtMost(Cobblemon.config.maxPokemonLevel)
        val pokemon = existing ?: PokemonProperties().apply {
            this.species = species.resourceIdentifier.toString()
            this.level = level ?: NEW_POKEMON_LEVEL
        }.create()
        if (pokemon.species != species) pokemon.species = species

        // Fields Cobblemon's own properties (as in /pokeedit) apply consistently.
        PokemonProperties().apply {
            if (data.form.isNotBlank()) form = data.form
            shiny = data.shiny
            gender = runCatching { Gender.valueOf(data.gender) }.getOrNull()
            if (level != null) this.level = level
            if (data.nature.isNotBlank()) nature = data.nature
            if (data.ability.isNotBlank()) ability = data.ability
        }.apply(pokemon)
        if (data.form.isBlank() && pokemon.form != species.standardForm) pokemon.form = species.standardForm

        pokemon.forcedAspects = data.aspects.map(String::trim).filter(String::isNotEmpty).toSet()
        pokemon.updateAspects()

        val moves = data.moves.mapNotNull { Moves.getByName(it.trim().lowercase()) }.take(PokeWandPokemon.MAX_MOVES)
        if (moves.isNotEmpty()) {
            pokemon.moveSet.clear()
            moves.forEachIndexed { index, move -> pokemon.moveSet.setMove(index, move.create()) }
        }

        PokeWandPokemon.STATS.forEachIndexed { index, stat ->
            data.ivs.getOrElse(index) { -1 }.takeIf { it >= 0 }?.let { pokemon.setIV(stat, it.coerceAtMost(PokeWandPokemon.MAX_IV)) }
            data.evs.getOrElse(index) { -1 }.takeIf { it >= 0 }?.let { pokemon.setEV(stat, it.coerceAtMost(PokeWandPokemon.MAX_EV)) }
        }

        val item = data.heldItem.takeIf(String::isNotBlank)?.let(ResourceLocation::tryParse)
            ?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }
        pokemon.swapHeldItem(item?.let(::ItemStack) ?: ItemStack.EMPTY, false, false)

        pokemon.nickname = data.nickname.takeIf(String::isNotBlank)?.let(Component::literal)
        ResourceLocation.tryParse(data.pokeball)?.let(PokeBalls::getPokeBall)?.let { pokemon.caughtBall = it }
        if (data.friendship >= 0) pokemon.setFriendship(data.friendship.coerceAtMost(255), true)
        pokemon.heal()

        if (existing == null) party.set(slot, pokemon)
        // Server log of admin edits.
        GenerationsCore.LOGGER.info("Pokewand: {} edited slot {} of {} ({})", viewer.gameProfile.name, slot + 1, target.gameProfile.name, species.name)
    }

    /** Opens the target's PC for the viewer, who can move its Pokémon like in their own. */
    private fun openPc(viewer: ServerPlayer, target: ServerPlayer) {
        val pc = Cobblemon.storage.getPC(target)
        pc.sendTo(viewer)
        PCLinkManager.addLink(viewer.uuid, pc) { canUse(it) }
        OpenPCPacket(pc).sendToPlayer(viewer)
    }
}
