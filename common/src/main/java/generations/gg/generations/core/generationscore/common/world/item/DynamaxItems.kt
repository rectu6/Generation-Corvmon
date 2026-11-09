package generations.gg.generations.core.generationscore.common.world.item

import com.cobblemon.mod.common.api.item.PokemonSelectingItem
import com.cobblemon.mod.common.item.battle.BagItem
import com.cobblemon.mod.common.pokemon.Pokemon
import generations.gg.generations.core.generationscore.common.battle.DynamaxLevel
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

/**
 * Generations' Dynamax items, used like Cobblemon's candies: right-click, then pick
 * a Pokémon of the party.
 */
abstract class DynamaxPartyItem(properties: Properties, private val tooltipKey: String) : Item(properties), PokemonSelectingItem {
    // Not usable in battle.
    override val bagItem: BagItem? = null

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (player is ServerPlayer) return use(player, player.getItemInHand(hand))
        return InteractionResultHolder.success(player.getItemInHand(hand))
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip.add(Component.translatable(tooltipKey))
    }

    protected fun success(player: ServerPlayer, stack: ItemStack, message: Component): InteractionResultHolder<ItemStack> {
        player.displayClientMessage(message, true)
        player.level().playSound(null, player.x, player.y, player.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.4f)
        stack.consume(1, player)
        return InteractionResultHolder.success(stack)
    }
}

/** +1 Dynamax Level, up to the maximum (10). */
class DynamaxCandyItem(properties: Properties) : DynamaxPartyItem(properties, "item.generations_core.dynamax_candy.tooltip") {
    override fun canUseOnPokemon(stack: ItemStack, pokemon: Pokemon): Boolean = DynamaxLevel.get(pokemon) < DynamaxLevel.max()

    override fun applyToPokemon(player: ServerPlayer, stack: ItemStack, pokemon: Pokemon): InteractionResultHolder<ItemStack> {
        val level = DynamaxLevel.get(pokemon)
        if (level >= DynamaxLevel.max()) {
            player.displayClientMessage(Component.translatable("generations_core.dynamax.max_level", pokemon.getDisplayName(false)), true)
            return InteractionResultHolder.fail(stack)
        }
        DynamaxLevel.set(pokemon, level + 1)
        return success(player, stack, Component.translatable("generations_core.dynamax.level_up", pokemon.getDisplayName(false), level + 1, DynamaxLevel.max()))
    }
}

/** Gives the G-Max Factor to a species with a G-Max form, or takes it away. */
class MaxSoupItem(properties: Properties) : DynamaxPartyItem(properties, "item.generations_core.max_soup.tooltip") {
    override fun canUseOnPokemon(stack: ItemStack, pokemon: Pokemon): Boolean = DynamaxLevel.canGigantamax(pokemon)

    override fun applyToPokemon(player: ServerPlayer, stack: ItemStack, pokemon: Pokemon): InteractionResultHolder<ItemStack> {
        if (!DynamaxLevel.canGigantamax(pokemon)) {
            player.displayClientMessage(Component.translatable("generations_core.dynamax.no_gmax", pokemon.getDisplayName(false)), true)
            return InteractionResultHolder.fail(stack)
        }
        pokemon.gmaxFactor = !pokemon.gmaxFactor
        val key = if (pokemon.gmaxFactor) "generations_core.dynamax.gmax_on" else "generations_core.dynamax.gmax_off"
        val result = success(player, stack, Component.translatable(key, pokemon.getDisplayName(false)))
        // The bowl comes back, like with vanilla soups.
        if (!player.inventory.add(ItemStack(Items.BOWL))) player.drop(ItemStack(Items.BOWL), false)
        return result
    }
}

/** Revives a fainted Pokémon with all its HP (Max Revive of the Max Lair). */
class MaxHoneyItem(properties: Properties) : DynamaxPartyItem(properties, "item.generations_core.max_honey.tooltip") {
    override fun canUseOnPokemon(stack: ItemStack, pokemon: Pokemon): Boolean = pokemon.isFainted()

    override fun applyToPokemon(player: ServerPlayer, stack: ItemStack, pokemon: Pokemon): InteractionResultHolder<ItemStack> {
        if (!pokemon.isFainted()) return InteractionResultHolder.fail(stack)
        pokemon.heal()
        return success(player, stack, Component.translatable("generations_core.dynamax.revived", pokemon.getDisplayName(false)))
    }
}
