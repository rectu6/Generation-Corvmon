package generations.gg.generations.core.generationscore.common.world.item

import generations.gg.generations.core.generationscore.common.world.raid.config.RaidConfig
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

/**
 * Wishing Chip, Piece and Star: used on an inactive Dynamax den, they summon a raid at
 * once (RaidDenBlock), of the tiers set in dens.json: 1-2, 3-4 and 5 stars by default,
 * the Star sometimes a 6-star legendary.
 */
class WishingItem(properties: Properties, private val name: String) : Item(properties) {
    /** Tiers of the raid summoned (server side: the settings of dens.json). */
    val tiers: IntRange
        get() {
            val dens = RaidConfig.dens
            val range = when (name) {
                "wishing_chip" -> dens.wishingChipStars
                "wishing_piece" -> dens.wishingPieceStars
                else -> dens.wishingStarStars
            }
            return range[0]..range[1]
        }

    /** Chance of a 6-star legendary raid instead. */
    val legendaryChance: Double
        get() = if (name == "wishing_star") RaidConfig.dens.wishingStarLegendaryChance else 0.0

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip.add(Component.translatable("item.generations_core.$name.raid_tooltip"))
    }
}
