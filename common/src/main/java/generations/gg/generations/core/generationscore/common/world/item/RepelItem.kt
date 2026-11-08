package generations.gg.generations.core.generationscore.common.world.item

import generations.gg.generations.core.generationscore.common.world.effect.GenerationsMobEffects
import generations.gg.generations.core.generationscore.common.world.effect.RepelEffect
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

/** Repel (tier 0), Super Repel (1) and Max Repel (2): see RepelEffect. */
class RepelItem(properties: Properties, private val tier: Int, private val durationTicks: Int) : Item(properties) {

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (level.isClientSide) return InteractionResultHolder.success(stack)

        val current = player.getEffect(GenerationsMobEffects.REPEL)
        if (current != null && current.amplifier > tier) {
            // A stronger repel is active: keep the item.
            player.displayClientMessage(Component.translatable("item.generations_core.repel.stronger_active"), true)
            return InteractionResultHolder.fail(stack)
        }

        // No particles: the icon and its timer show the effect.
        player.addEffect(MobEffectInstance(GenerationsMobEffects.REPEL, durationTicks, tier, false, false, true))
        level.playSound(null, player.x, player.y, player.z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.5f, 1.6f)
        player.displayClientMessage(Component.translatable("item.generations_core.repel.used", RepelEffect.radius(tier).toInt(), durationTicks / 1200), true)
        stack.consume(1, player)
        return InteractionResultHolder.consume(stack)
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip.add(Component.translatable("item.generations_core.repel.tooltip", RepelEffect.radius(tier).toInt(), durationTicks / 1200))
    }
}
