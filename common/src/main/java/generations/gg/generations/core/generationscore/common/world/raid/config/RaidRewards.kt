package generations.gg.generations.core.generationscore.common.world.raid.config

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack

/** One reward of rewards.json. */
class RewardEntry(
    var item: String = "",
    /** Tiers (1 to 6) it can be won at; empty: every tier. */
    var stars: MutableList<Int> = mutableListOf(),
    /** Types of the boss it needs (any of them), e.g. ["fire"]; empty: every type. */
    var types: MutableList<String> = mutableListOf(),
    /** Weight in the draws (starRewards, typeRewards) or chance from 0 to 1 (guaranteed). */
    var weight: Double = 10.0,
    var min: Int = 1,
    var max: Int = 1,
    /** common, rare or very_rare: only informative. */
    var rarity: String = "common",
) {
    @Transient var valid = false

    fun fits(stars: Int, types: Collection<String>) =
        valid && (this.stars.isEmpty() || stars in this.stars) && (this.types.isEmpty() || this.types.any { it.lowercase() in types })

    fun roll(random: RandomSource): ItemStack? {
        val item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(item)).orElse(null) ?: return null
        return ItemStack(item, (min + random.nextInt(max - min + 1)).coerceAtMost(item.defaultMaxStackSize * 4))
    }
}

/**
 * Raid rewards, drawn once per player after a win:
 * - each guaranteed entry of the tier, with its chance (weight from 0 to 1);
 * - starRolls draws (by weight) among the star rewards of the tier;
 * - typeRolls draws among the rewards of the boss's types.
 */
class RaidRewards(
    var starRolls: MutableList<Int> = mutableListOf(2, 2, 3, 3, 4, 5),
    var typeRolls: MutableList<Int> = mutableListOf(1, 1, 1, 2, 2, 2),
    var guaranteed: MutableList<RewardEntry> = mutableListOf(),
    var starRewards: MutableList<RewardEntry> = mutableListOf(),
    var typeRewards: MutableList<RewardEntry> = mutableListOf(),
) {
    fun validate() {
        while (starRolls.size < 6) starRolls.add(starRolls.lastOrNull() ?: 1)
        while (typeRolls.size < 6) typeRolls.add(typeRolls.lastOrNull() ?: 1)
        starRolls = starRolls.map { it.coerceIn(0, 20) }.toMutableList()
        typeRolls = typeRolls.map { it.coerceIn(0, 20) }.toMutableList()
        (guaranteed + starRewards + typeRewards).forEach { entry ->
            val id = ResourceLocation.tryParse(entry.item)
            entry.valid = id != null && BuiltInRegistries.ITEM.containsKey(id)
            if (!entry.valid) RaidConfig.warn("rewards.json: unknown item {}, skipped", entry.item)
            entry.min = entry.min.coerceIn(1, 64)
            entry.max = entry.max.coerceIn(entry.min, 64)
            entry.weight = entry.weight.coerceAtLeast(0.0)
        }
    }

    fun draw(stars: Int, types: Collection<String>, random: RandomSource): List<ItemStack> {
        val won = mutableListOf<ItemStack>()
        guaranteed.filter { it.fits(stars, types) && random.nextDouble() < it.weight.coerceAtMost(1.0) }.forEach { entry -> entry.roll(random)?.let(won::add) }
        repeat(starRolls[stars - 1]) { weighted(starRewards.filter { it.fits(stars, types) }, random)?.roll(random)?.let(won::add) }
        repeat(typeRolls[stars - 1]) { weighted(typeRewards.filter { it.fits(stars, types) && it.types.isNotEmpty() }, random)?.roll(random)?.let(won::add) }
        // Same items together.
        return won.groupBy { it.item }.flatMap { (item, stacks) ->
            var count = stacks.sumOf { it.count }
            val result = mutableListOf<ItemStack>()
            while (count > 0) {
                val size = minOf(count, item.defaultMaxStackSize)
                result += ItemStack(item, size)
                count -= size
            }
            result
        }
    }

    private fun weighted(entries: List<RewardEntry>, random: RandomSource): RewardEntry? {
        val total = entries.sumOf { it.weight }
        if (total <= 0) return null
        var roll = random.nextDouble() * total
        for (entry in entries) {
            roll -= entry.weight
            if (roll <= 0) return entry
        }
        return entries.last()
    }
}
