package generations.gg.generations.core.generationscore.common.world.raid.den

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.battle.DynamaxLevel
import generations.gg.generations.core.generationscore.common.world.level.block.raid.RaidDenBlock
import generations.gg.generations.core.generationscore.common.world.level.block.raid.RaidDenBlockEntity
import generations.gg.generations.core.generationscore.common.world.raid.RaidManager
import generations.gg.generations.core.generationscore.common.world.raid.config.RaidConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.state.BlockState

/**
 * Life of the dens: registration when their block loads, the raid boss appearing
 * every Minecraft morning (denRespawnChance, one attempt per den and per day, even
 * with the chunk unloaded), the beam following the raid's tier.
 */
object RaidDens {
    private const val CHECK_TICKS = 40

    fun init() {
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { event -> tick(event.server) }
    }

    private fun tick(server: MinecraftServer) {
        if (server.tickCount % CHECK_TICKS != 0) return
        server.allLevels.forEach { level -> RaidDenRegistry.existing(level)?.let { mornings(level, it) } }
    }

    /** Once a day, at denRespawnTime: one respawn attempt for each inactive den. */
    private fun mornings(level: ServerLevel, registry: RaidDenRegistry) {
        val time = level.dayTime
        val day = Math.floorDiv(time, 24000L)
        // Time set back: wait for the next morning instead of skipping days.
        if (registry.lastProcessedDay > day) registry.lastProcessedDay = day - 1
        if (registry.lastProcessedDay >= day || Math.floorMod(time, 24000L) < RaidConfig.dens.denRespawnTime) return
        registry.lastProcessedDay = day
        registry.all.forEach { den -> attempt(level, registry, den, day) }
    }

    private fun attempt(level: ServerLevel, registry: RaidDenRegistry, den: RaidDenRegistry.Den, day: Long) {
        if (den.lastAttemptDay >= day) return
        den.lastAttemptDay = day
        registry.setDirty()
        if (den.active || RaidManager.isBusy(level, den.pos)) return
        if (level.random.nextDouble() < RaidConfig.dens.denRespawnChance) spawn(level, den)
    }

    /**
     * A new raid in the den: tier from the star weights, Pokémon from the biome's
     * table, generated once with its IVs and shiny chance.
     */
    fun spawn(level: ServerLevel, den: RaidDenRegistry.Den, forcedStars: Int? = null, forcedSpecies: String? = null): Boolean {
        val random = level.random
        val stars = forcedStars ?: pickStars(random)
        val boss = generateBoss(level, den, stars, random, forcedSpecies) ?: return false
        val registry = RaidDenRegistry.of(level)
        den.stars = stars
        den.boss = boss.saveToNBT(level.registryAccess())
        registry.setDirty()
        updateBeam(level, den)
        if (level.isLoaded(den.pos)) {
            level.playSound(null, den.pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, if (stars >= 6) 0.6f else 1f)
        }
        return true
    }

    /**
     * A Wishing item used on an inactive den: a raid of one of [tiers] at once (6 stars,
     * legendary, with [legendaryChance]). False when the den is active or busy, or no
     * Pokémon fits.
     */
    fun summon(level: ServerLevel, pos: BlockPos, tiers: IntRange, legendaryChance: Double): Boolean {
        val registry = RaidDenRegistry.of(level)
        val den = registry[pos] ?: registry.add(pos).also {
            // A den just placed, not registered by its ticker yet.
            it.biome = level.getBiome(pos).unwrapKey().map { key -> key.location().toString() }.orElse("")
            it.lastAttemptDay = Math.floorDiv(level.dayTime, 24000L)
        }
        if (den.active || RaidManager.isBusy(level, pos)) return false
        val random = level.random
        val stars = if (legendaryChance > 0.0 && random.nextDouble() < legendaryChance) 6 else pickStars(random, tiers)
        return spawn(level, den, stars)
    }

    /** The den becomes inactive: its raid is over. */
    fun clear(level: ServerLevel, pos: BlockPos) {
        val registry = RaidDenRegistry.existing(level) ?: return
        val den = registry[pos] ?: return
        den.stars = 0
        den.boss = null
        registry.setDirty()
        updateBeam(level, den)
    }

    /** The boss of the den's raid, a fresh copy of the one generated. */
    fun boss(level: ServerLevel, den: RaidDenRegistry.Den): Pokemon? =
        den.boss?.let { runCatching { Pokemon.loadFromNBT(level.registryAccess(), it) }.getOrNull() }

    /** A tier from the star weights, within [range] (a Wishing item's tiers). */
    fun pickStars(random: RandomSource, range: IntRange = 1..6): Int {
        val config = RaidConfig.dens
        val weights = config.starWeights.mapIndexed { i, w ->
            when {
                i + 1 !in range -> 0.0
                i == 5 -> w * config.legendaryWeightMultiplier
                else -> w
            }
        }
        if (weights.sum() <= 0.0) return range.first + random.nextInt(range.last - range.first + 1)
        var roll = random.nextDouble() * weights.sum()
        weights.forEachIndexed { i, weight ->
            roll -= weight
            if (weight > 0 && roll <= 0) return i + 1
        }
        return range.last
    }

    private fun biome(level: ServerLevel, den: RaidDenRegistry.Den): Holder<Biome>? {
        val id = ResourceLocation.tryParse(den.biome) ?: return null
        return level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(ResourceKey.create(Registries.BIOME, id)).orElse(null)
    }

    private fun generateBoss(level: ServerLevel, den: RaidDenRegistry.Den, stars: Int, random: RandomSource, forcedSpecies: String?): Pokemon? {
        val tier = RaidConfig.tier(stars)
        val levels = tier.minLevel..tier.maxLevel
        val forced = forcedSpecies?.let { ResourceLocation.tryParse(it) }?.let(PokemonSpecies::getByIdentifier)
        val pick = if (forced != null) null else RaidConfig.pokemon.pick(stars, biome(level, den), random, levels)
        val species = forced ?: pick?.species ?: return null
        val range = pick?.levels ?: levels
        val pokemon = PokemonProperties().apply {
            this.species = species.resourceIdentifier.toString()
            this.level = range.first + random.nextInt(range.last - range.first + 1)
            this.shiny = random.nextInt(tier.shinyRate) == 0
            pick?.aspects?.takeIf { it.isNotEmpty() }?.let { this.aspects = it.toMutableSet() }
        }.create()
        // perfectIvs different stats at 31, the others as generated.
        Stats.PERMANENT.shuffled().take(tier.perfectIvs).forEach { pokemon.setIV(it, 31) }
        if (pick?.gigantamax == true && DynamaxLevel.canGigantamax(pokemon)) pokemon.gmaxFactor = true
        return pokemon
    }

    // ---- The block ---------------------------------------------------------

    /**
     * Every second or so, for each loaded den: registration (first time; old raids
     * kept, new dens get a first respawn attempt), then the beam follows the raid.
     */
    fun tickDen(level: ServerLevel, pos: BlockPos, state: BlockState, entity: RaidDenBlockEntity) {
        val registry = RaidDenRegistry.of(level)
        var den = registry[pos]
        if (den == null) {
            den = registry.add(pos)
            den.biome = level.getBiome(pos).unwrapKey().map { it.location().toString() }.orElse("")
            if (entity.stars > 0) {
                // A raid of the former system (Wishing items): kept with its tier and species.
                spawn(level, den, entity.stars.coerceIn(1, 6), entity.species.ifEmpty { null })
                entity.clear()
            } else if (level.random.nextDouble() < RaidConfig.dens.denRespawnChance) {
                spawn(level, den)
            }
            den.lastAttemptDay = Math.floorDiv(level.dayTime, 24000L)
        }
        val beam = if (den.active) RaidDenBlock.Beam.forStars(den.stars) else RaidDenBlock.Beam.NONE
        if (state.getValue(RaidDenBlock.BEAM) != beam) level.setBlock(pos, state.setValue(RaidDenBlock.BEAM, beam), 3)
    }

    private fun updateBeam(level: ServerLevel, den: RaidDenRegistry.Den) {
        if (!level.isLoaded(den.pos)) return
        val state = level.getBlockState(den.pos)
        if (!state.hasProperty(RaidDenBlock.BEAM)) return
        val beam = if (den.active) RaidDenBlock.Beam.forStars(den.stars) else RaidDenBlock.Beam.NONE
        if (state.getValue(RaidDenBlock.BEAM) != beam) level.setBlock(den.pos, state.setValue(RaidDenBlock.BEAM, beam), 3)
    }

    /** The den block was broken or replaced. */
    fun removed(level: ServerLevel, pos: BlockPos) {
        RaidManager.denRemoved(level, pos)
        RaidDenRegistry.existing(level)?.remove(pos)
        GenerationsCore.LOGGER.debug("Raid den removed at {}", pos)
    }
}
