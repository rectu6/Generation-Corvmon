package generations.gg.generations.core.generationscore.common.world.item

import generations.gg.generations.core.generationscore.common.world.pokewand.PokeWand
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

/** Operator tool: right-click edits your party, right-click on a player edits theirs (PokeWand). */
class PokeWandItem(properties: Properties) : Item(properties) {

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (player is ServerPlayer) PokeWand.open(player, player)
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide)
    }

    override fun interactLivingEntity(stack: ItemStack, player: Player, target: LivingEntity, hand: InteractionHand): InteractionResult {
        if (target !is Player) return InteractionResult.PASS
        if (player is ServerPlayer && target is ServerPlayer) PokeWand.open(player, target)
        return InteractionResult.sidedSuccess(player.level().isClientSide)
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip.add(Component.translatable("item.generations_core.pokemon_wand.tooltip"))
    }
}
