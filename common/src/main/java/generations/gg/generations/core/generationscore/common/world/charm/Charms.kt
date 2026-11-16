package generations.gg.generations.core.generationscore.common.world.charm

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import generations.gg.generations.core.generationscore.common.world.catchcombo.SpawnContext
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems
import generations.gg.generations.core.generationscore.common.world.shiny.ShinyMath
import generations.gg.generations.core.generationscore.common.world.shiny.ShinyOdds
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The charms' effects (values in charms/charms.json). A charm counts when it is in the
 * charm case or in the inventory; it is looked for only when its effect is needed (a
 * spawn, a capture, experience, a raid's rewards): at most 59 slots read, nothing per tick.
 * - Shiny Charm: a bonus of ShinyOdds;
 * - Exp Charm: battle experience (EXPERIENCE_GAINED_EVENT_PRE) of the owner's Pokémon;
 * - Catching Charm: the critical capture (CobblemonCaptureCalculatorMixin, where Cobblemon
 *   left "* 1F → * 2F when the Catching Charm is implemented");
 * - Mark Charm: Pokemon.applyPotentialMarks (PokemonMarksMixin): its owner's charm, or
 *   the player of the spawn being made. Nothing in Cobblemon 1.8.4 calls it yet;
 * - Glimmering Charm: the quantities of the Dynamax raids' rewards (RaidResults);
 * - Oval Charm: no effect yet; Mega Charm: a key stone; Old Charm: a collectible.
 */
object Charms {
    var config = CharmsConfig().also { it.validate() }; private set

    fun load() {
        config = CharmsConfig.load()
    }

    fun init() {
        load()
        CharmCase.init()
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { load() }
        ShinyOdds.register { player, _, _ -> if (has(player, Charm.SHINY)) ShinyMath.Bonus("shiny_charm", config.shinyCharm) else null }
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.NORMAL) { event ->
            if (!event.source.isBattle() || event.experience <= 0 || config.expCharm <= 1.0) return@subscribe
            val owner = event.pokemon.getOwnerPlayer() ?: return@subscribe
            if (has(owner, Charm.EXP)) event.experience = (event.experience * config.expCharm).roundToInt()
        }
    }

    fun item(charm: Charm): Item = when (charm) {
        Charm.SHINY -> GenerationsItems.SHINY_CHARM
        Charm.EXP -> GenerationsItems.EXP_CHARM
        Charm.CATCHING -> GenerationsItems.CATCHING_CHARM
        Charm.MARK -> GenerationsItems.MARK_CHARM
        Charm.OVAL -> GenerationsItems.OVAL_CHARM
        Charm.GLIMMERING -> GenerationsItems.GLIMMERING_CHARM
        Charm.MEGA -> GenerationsItems.MEGA_CHARM
        Charm.OLD -> GenerationsItems.OLD_CHARM
    }.value()

    /** In the charm case, or (countInInventory) anywhere in the inventory. */
    fun has(player: Player, charm: Charm): Boolean {
        val item = item(charm)
        if (CharmCase.contains(player, item)) return true
        if (!config.countInInventory) return false
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) if (inventory.getItem(slot).`is`(item)) return true
        return false
    }

    /** The catch rate given to Cobblemon's critical capture test: ×catchingCharm with the charm. */
    @JvmStatic
    fun criticalCaptureRate(thrower: LivingEntity, rate: Float): Float =
        if (thrower is ServerPlayer && config.catchingCharm > 1.0 && has(thrower, Charm.CATCHING)) (rate * config.catchingCharm).toFloat() else rate

    /** The chance given to applyPotentialMarks: ×markCharm with the charm of its owner, or of the spawn's player. */
    @JvmStatic
    fun markChance(pokemon: Pokemon, chance: Double): Double {
        if (config.markCharm <= 1.0) return chance
        val player = pokemon.getOwnerPlayer() ?: SpawnContext.current()?.player ?: return chance
        return if (has(player, Charm.MARK)) chance * config.markCharm else chance
    }

    /**
     * A raid's rewards with the Glimmering Charm: each quantity ×glimmeringCharm, the
     * fraction drawn (×1.5 on 1 item: 1 or 2, 1.5 on average), split into full stacks.
     */
    fun raidRewards(player: ServerPlayer, rewards: List<ItemStack>, random: RandomSource): List<ItemStack> {
        if (config.glimmeringCharm <= 1.0 || rewards.isEmpty() || !has(player, Charm.GLIMMERING)) return rewards
        val result = ArrayList<ItemStack>(rewards.size + 2)
        for (stack in rewards) {
            if (stack.isEmpty) continue
            val scaled = stack.count * config.glimmeringCharm
            var count = floor(scaled).toInt() + if (random.nextDouble() < scaled - floor(scaled)) 1 else 0
            val max = stack.maxStackSize.coerceAtLeast(1)
            while (count > 0) {
                val part = minOf(count, max)
                result += stack.copyWithCount(part)
                count -= part
            }
        }
        return result
    }
}
