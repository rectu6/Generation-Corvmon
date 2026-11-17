package generations.gg.generations.core.generationscore.common.world.rks.machine

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.trade.TradeManager
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.GenerationsNetwork.sendPacket
import generations.gg.generations.core.generationscore.common.network.packets.rks.S2CRksSacrificeListPacket
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksConsoleBlockEntity
import generations.gg.generations.core.generationscore.common.world.rks.RksEnergy
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import java.util.UUID

/**
 * The machine's energies (RKS-PLAN.md § 4.2, D12; HOOH-PLAN.md § 9): Pokémon of the reserve's
 * type (Psychic for Mewtwo's protocol, Fire for the Sacred Ash) given from the party and the PC,
 * each reserve under its own rules (RksEnergy.rules) and kept apart in the console.
 *
 * The server sends the list on request (a page at a time) and checks every Pokémon again by its
 * UUID when they are given: the player's, in their party or PC, not an egg, of the reserve's
 * type, not excluded, the player not in a battle or a trade, the Pokémon not sent out nor in a
 * pasture (tetheringId). Then all are taken and credited at once, the reserve capped (what goes
 * over is lost, the menu said so). The machine's revision is the lock: a list seen before
 * another gift (another player at the same machine) is refused and sent again. A Pokémon taken
 * is no longer in the player's storage: it cannot be given twice.
 */
object RksSacrifice {
    /** Where a Pokémon of this player is: -1 the party, the PC's box otherwise. */
    private class Found(val pokemon: Pokemon, val place: Int)

    private fun isEgg(pokemon: Pokemon): Boolean =
        pokemon.species.resourceIdentifier.path.equals("egg", ignoreCase = true) || pokemon.aspects.any { it.equals("egg", ignoreCase = true) }

    fun points(pokemon: Pokemon, kind: RksEnergy = RksEnergy.PSYCHIC): Int {
        val config = kind.rules
        val form = pokemon.form
        val stage = RksPoints.stage(form.preEvolution != null, form.evolutions.isNotEmpty())
        val id = RksPoints.normalise(pokemon.species.resourceIdentifier.toString())
        val override = config.speciesPoints.entries.firstOrNull { RksPoints.normalise(it.key) == id }?.value
        val p = config.points
        return RksPoints.points(stage, p.firstStage, p.middleStage, p.finalStage, p.noEvolution, override)
    }

    /** Of the reserve's type, primary or secondary. */
    private fun ofType(pokemon: Pokemon, kind: RksEnergy): Boolean = pokemon.types.any { it.name.equals(kind.type, ignoreCase = true) }

    /** The species and the Pokémon may be given (its own state: not sent out, not in a pasture, not an egg). */
    fun eligible(pokemon: Pokemon, kind: RksEnergy = RksEnergy.PSYCHIC): Boolean {
        if (isEgg(pokemon) || pokemon.entity != null || pokemon.tetheringId != null) return false
        val config = kind.rules
        return RksPoints.eligible(
            ofType(pokemon, kind), pokemon.species.resourceIdentifier.toString(), pokemon.form.labels,
            config.excludedLabels, config.excludedSpecies, config.allowedSpecies,
        )
    }

    /** Why this player cannot give any Pokémon now (a translation key), or null. */
    private fun blocked(player: ServerPlayer): String? = when {
        BattleRegistry.getBattleByParticipatingPlayer(player) != null -> "generations_core.rks.sacrifice.in_battle"
        TradeManager.getActiveTrade(player.uuid) != null -> "generations_core.rks.sacrifice.in_trade"
        else -> null
    }

    private fun candidates(player: ServerPlayer, kind: RksEnergy): List<Found> {
        val out = ArrayList<Found>()
        for (pokemon in Cobblemon.storage.getParty(player)) if (eligible(pokemon, kind)) out += Found(pokemon, -1)
        if (kind.rules.allowPc) {
            val pc = Cobblemon.storage.getPC(player)
            for ((box, content) in pc.boxes.withIndex()) for (pokemon in content) if (eligible(pokemon, kind)) out += Found(pokemon, box)
        }
        return out
    }

    private fun find(player: ServerPlayer, uuid: UUID, kind: RksEnergy): Found? {
        Cobblemon.storage.getParty(player).firstOrNull { it.uuid == uuid }?.let { return Found(it, -1) }
        if (!kind.rules.allowPc) return null
        val pc = Cobblemon.storage.getPC(player)
        for ((box, content) in pc.boxes.withIndex()) for (pokemon in content) if (pokemon.uuid == uuid) return Found(pokemon, box)
        return null
    }

    fun sendList(player: ServerPlayer, console: RksConsoleBlockEntity, page: Int, kind: RksEnergy = RksEnergy.PSYCHIC) {
        val all = candidates(player, kind)
        val size = S2CRksSacrificeListPacket.PAGE_SIZE
        val pages = maxOf(1, (all.size + size - 1) / size)
        val p = page.coerceIn(0, pages - 1)
        val entries = all.drop(p * size).take(size).map { found ->
            val pokemon = found.pokemon
            S2CRksSacrificeListPacket.Entry(
                pokemon.uuid, pokemon.species.resourceIdentifier.toString(), pokemon.aspects.toList(),
                pokemon.nickname?.string ?: "", pokemon.level, points(pokemon, kind), found.place, pokemon.shiny,
            )
        }
        player.sendPacket(
            S2CRksSacrificeListPacket(console.blockPos, kind.ordinal, p, pages, all.size, console.energyOf(kind), kind.required, console.machine.revision, entries, blocked(player) ?: ""),
        )
    }

    private fun refuse(player: ServerPlayer, key: String, vararg args: Any) {
        player.displayClientMessage(Component.translatable(key, *args).withStyle(ChatFormatting.RED), true)
    }

    fun sacrifice(player: ServerPlayer, console: RksConsoleBlockEntity, revision: Int, uuids: List<UUID>, kind: RksEnergy = RksEnergy.PSYCHIC) {
        val distinct = uuids.distinct()
        if (distinct.isEmpty()) return
        val state = console.state
        if (state == RksState.DAMAGED) return refuse(player, "generations_core.rks.error.damaged")
        if (!state.assembled || state.locksSlots || console.machine.experiment) return refuse(player, "generations_core.rks.error.busy")
        blocked(player)?.let { return refuse(player, it) }
        val required = kind.required
        if (console.energyOf(kind) >= required) return refuse(player, kind.key("generations_core.rks.sacrifice.full"))
        // Each Pokémon again, by its UUID: all of them, or none.
        val found = ArrayList<Found>(distinct.size)
        for (uuid in distinct) {
            val f = find(player, uuid, kind)
            if (f == null || !eligible(f.pokemon, kind)) {
                refuse(player, "generations_core.rks.sacrifice.changed")
                return sendList(player, console, 0, kind)
            }
            found += f
        }
        // The lock: the reserve as this player saw it.
        if (!console.machine.accept(revision)) {
            refuse(player, "generations_core.rks.sacrifice.changed")
            return sendList(player, console, 0, kind)
        }
        var points = 0
        val party = Cobblemon.storage.getParty(player)
        val pc = Cobblemon.storage.getPC(player)
        var given = 0
        for (f in found) {
            val removed = if (f.place < 0) party.remove(f.pokemon) else pc.remove(f.pokemon)
            if (!removed) continue
            points += points(f.pokemon, kind)
            given++
        }
        val (added, lost) = RksPoints.credit(console.energyOf(kind), required, points).let { it[0] to it[1] }
        console.setEnergy(kind, console.energyOf(kind) + added)
        console.machine.touch()
        console.changed()
        val level = player.serverLevel()
        if (kind == RksEnergy.FIRE) {
            level.playSound(null, console.blockPos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.2f, 0.7f)
            level.playSound(null, console.blockPos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0f, 0.8f)
        } else {
            level.playSound(null, console.blockPos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4f, 0.6f)
            level.playSound(null, console.blockPos, SoundEvents.EVOKER_CAST_SPELL, SoundSource.BLOCKS, 1.0f, 1.4f)
        }
        player.displayClientMessage(
            Component.translatable(kind.key("generations_core.rks.sacrifice.done"), given, added, console.energyOf(kind), required)
                .withStyle(if (kind == RksEnergy.FIRE) ChatFormatting.GOLD else ChatFormatting.LIGHT_PURPLE), false,
        )
        if (lost > 0) player.displayClientMessage(Component.translatable("generations_core.rks.sacrifice.lost", lost).withStyle(ChatFormatting.GRAY), false)
        GenerationsCore.LOGGER.info("RKS Machine at {}: {} gave {} {} Pokémon (+{} {} energy, {} lost)", console.blockPos, player.gameProfile.name, given, kind.type, added, kind.id, lost)
        sendList(player, console, 0, kind)
    }
}
