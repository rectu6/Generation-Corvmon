package generations.gg.generations.core.generationscore.common.world.fishing

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.spawning.fishing.FishingSpawnCause
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

/**
 * Old, Good and Super Rods are Cobblemon rods: what bites depends on the biome
 * (cold, warm or deep water…), time, weather and bait like any Cobblemon rod. Like in
 * Pixelmon, the rod also limits rarity and level, the level following the party.
 */
object GenerationsFishing {

    private enum class Tier(
        /** Rarest spawn bucket this rod can catch. */
        val rarest: String,
        /** Fished level = party average level × factor, within [minLevel, maxLevel]. */
        val factor: Double,
        val minLevel: Int,
        val maxLevel: Int?,
    ) {
        OLD("common", 0.5, 5, 20),
        GOOD("rare", 0.75, 15, 45),
        SUPER("ultra-rare", 1.0, 30, null),
    }

    private val BUCKETS = listOf("common", "uncommon", "rare", "ultra-rare")
    private const val LEVEL_SPREAD = 3

    private fun tier(rod: ItemStack): Tier? = when (rod.item) {
        GenerationsItems.OLD_ROD.value() -> Tier.OLD
        GenerationsItems.GOOD_ROD.value() -> Tier.GOOD
        GenerationsItems.SUPER_ROD.value() -> Tier.SUPER
        else -> null
    }

    fun init() {
        // A rod cannot catch rarer than its tier: the bucket is lowered to its rarest one.
        CobblemonEvents.SPAWN_BUCKET_CHOSEN.subscribe(Priority.NORMAL) { event ->
            val cause = event.spawnCause as? FishingSpawnCause ?: return@subscribe
            val tier = tier(cause.rodStack) ?: return@subscribe
            if (BUCKETS.indexOf(event.bucket) > BUCKETS.indexOf(tier.rarest)) event.bucket = tier.rarest
        }

        CobblemonEvents.BOBBER_SPAWN_POKEMON_POST.subscribe(Priority.NORMAL) { event ->
            val player = event.bobber.playerOwner as? ServerPlayer ?: return@subscribe
            val tier = tier(player.mainHandItem) ?: tier(player.offhandItem) ?: return@subscribe
            val pokemon = event.pokemon.pokemon
            pokemon.level = level(tier, player)
            pokemon.heal()
        }
    }

    private fun level(tier: Tier, player: ServerPlayer): Int {
        val levels = Cobblemon.storage.getParty(player).mapNotNull { it?.level }
        val maxLevel = tier.maxLevel ?: Cobblemon.config.maxPokemonLevel
        val base = if (levels.isEmpty()) tier.minLevel else (levels.average() * tier.factor).roundToInt()
        val spread = player.random.nextIntBetweenInclusive(-LEVEL_SPREAD, LEVEL_SPREAD)
        return (base + spread).coerceIn(tier.minLevel, maxLevel)
    }
}
