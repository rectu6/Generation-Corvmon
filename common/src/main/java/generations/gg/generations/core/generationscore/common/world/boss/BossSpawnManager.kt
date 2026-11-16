package generations.gg.generations.core.generationscore.common.world.boss

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.entity.SpawnEvent
import com.cobblemon.mod.common.api.spawning.fishing.FishingSpawnCause
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.raid.RaidManager
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import java.util.ArrayDeque
import java.util.EnumMap
import java.util.Random
import kotlin.math.sqrt

/**
 * Natural bosses: a few of Cobblemon's natural spawns become bosses, so biomes,
 * dimensions, conditions and Cobblemon's own budgets are kept and no extra spawn is
 * attempted. Listens to ENTITY_SPAWN last (Priority.LOWEST), only when nothing
 * cancelled the spawn, before the entity is added: it appears at its boss size.
 *
 * Order: cheap filters (dimension, cause, alpha, labels, species, raid), the chance,
 * then the limits that look at the living bosses (dimension, cell, players, other
 * bosses, per minute), then the rarity draw. Every refusal is counted (/boss spawninfo).
 * Raids and Ultra Space arrival encounters never come through ENTITY_SPAWN.
 */
object BossSpawnManager {
    enum class Reason(val key: String) {
        DISABLED("disabled"),
        DIMENSION("dimension"),
        ULTRA_WORLD("ultra_world"),
        FISHING("fishing"),
        OWNED("owned"),
        ALPHA("alpha"),
        LABEL("label"),
        SPECIES("species"),
        RAID("raid"),
        CHANCE("chance"),
        DIMENSION_LIMIT("dimension_limit"),
        CELL_LIMIT("cell_limit"),
        PLAYER_DISTANCE("player_distance"),
        BOSS_DISTANCE("boss_distance"),
        PER_MINUTE("per_minute"),
    }

    private val random = Random()
    val refused = EnumMap<Reason, Long>(Reason::class.java)
    var seen = 0L; private set
    var converted = 0L; private set
    /** Game times of the last natural bosses, for the per-minute limit. */
    private val recent = ArrayDeque<Long>()

    fun init() {
        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.LOWEST) { spawning(it) }
    }

    fun reset() {
        refused.clear()
        seen = 0L
        converted = 0L
        recent.clear()
    }

    /** Natural bosses in the last minute (game time). */
    fun lastMinute(now: Long): Int {
        while (recent.isNotEmpty() && now - recent.peekFirst() >= 1200L) recent.pollFirst()
        return recent.size
    }

    private fun refuse(reason: Reason) {
        refused[reason] = (refused[reason] ?: 0L) + 1L
    }

    private fun spawning(event: SpawnEvent<*>) {
        if (event.isCanceled) return
        val entity = event.entity as? PokemonEntity ?: return
        val level = entity.level() as? ServerLevel ?: return
        seen++
        val reason = check(event, entity, level)
        if (reason != null) return refuse(reason)

        val now = level.server.overworld().gameTime
        var rarity = drawRarity()
        if (rarity == BossRarity.LEGENDARY) {
            // Legendary bosses are Mega forms only: the spawn becomes a Mega that suits the place.
            val inWater = level.getFluidState(entity.blockPosition()).`is`(FluidTags.WATER)
            val mega = BossMegas.draw(inWater)
            if (mega == null) rarity = BossRarity.EPIC
            else entity.pokemon = BossMegas.create(mega, entity.pokemon.level)
        }
        val data = Bosses.make(entity.pokemon, rarity, false, BossData.SOURCE_NATURAL, now)
        BossRegistry.track(entity, level)
        recent.addLast(now)
        converted++
        GenerationsCore.LOGGER.info(
            "Bosses: a {} {}{} (level {}) at {} in {}",
            data.rarity.id, entity.pokemon.species.name, data.megaAspect.let { if (it.isEmpty()) "" else " ($it)" },
            entity.pokemon.level, entity.blockPosition().toShortString(), level.dimension().location(),
        )
        announce(entity, level, data)
    }

    /** Why this spawn does not become a boss, or null when it does. */
    private fun check(event: SpawnEvent<*>, entity: PokemonEntity, level: ServerLevel): Reason? {
        val spawns = BossConfig.spawns
        if (!BossConfig.general.enabled || !spawns.enabled || spawns.chance <= 0.0) return Reason.DISABLED
        if (level.dimension().location().toString() in spawns.excludedDimensions) return Reason.DIMENSION
        if (!spawns.allowUltraWorlds && UltraWorlds.isUltra(level.dimension())) return Reason.ULTRA_WORLD
        if (!spawns.allowFishing && event.cause is FishingSpawnCause) return Reason.FISHING

        val pokemon = entity.pokemon
        if (!pokemon.isWild() || Bosses.isBoss(pokemon)) return Reason.OWNED
        // Cobblemon's own alpha: its size ignores scaleModifier, it stays as it is.
        if (pokemon.isAlpha) return Reason.ALPHA
        val labels = BossConfig.species.excludedLabels
        if (labels.isNotEmpty() && pokemon.form.labels.any { label -> labels.any { it.equals(label, ignoreCase = true) } }) return Reason.LABEL
        if (!BossConfig.species.allows(pokemon.species.resourceIdentifier.toString())) return Reason.SPECIES
        if (RaidManager.isRaidEntity(entity)) return Reason.RAID

        if (random.nextDouble() >= spawns.chance) return Reason.CHANCE

        val bosses = BossRegistry.alive(level)
        if (bosses.size >= spawns.maxPerDimension) return Reason.DIMENSION_LIMIT
        val cellX = BossRegistry.cell(entity.x, spawns.cellSize)
        val cellZ = BossRegistry.cell(entity.z, spawns.cellSize)
        val inCell = bosses.count { BossRegistry.cell(it.x, spawns.cellSize) == cellX && BossRegistry.cell(it.z, spawns.cellSize) == cellZ }
        if (inCell >= spawns.maxPerCell) return Reason.CELL_LIMIT

        val nearest = level.players().asSequence().filter { !it.isSpectator }.minOfOrNull { it.distanceToSqr(entity) }
        if (nearest == null || sqrt(nearest) < spawns.minPlayerDistance || sqrt(nearest) > spawns.maxPlayerDistance) return Reason.PLAYER_DISTANCE

        val gap = spawns.minDistanceBetweenBosses
        if (gap > 0.0 && bosses.any { (it.x - entity.x) * (it.x - entity.x) + (it.z - entity.z) * (it.z - entity.z) < gap * gap }) return Reason.BOSS_DISTANCE

        if (lastMinute(level.server.overworld().gameTime) >= spawns.maxPerMinute) return Reason.PER_MINUTE
        return null
    }

    /** A rarity by the weights of rarities.json. */
    fun drawRarity(): BossRarity {
        val total = BossRarity.entries.sumOf { BossConfig.rarity(it).weight }
        if (total <= 0.0) return BossRarity.COMMON
        var roll = random.nextDouble() * total
        for (rarity in BossRarity.entries) {
            roll -= BossConfig.rarity(rarity).weight
            if (roll < 0.0) return rarity
        }
        return BossRarity.entries.last { BossConfig.rarity(it).weight > 0.0 }
    }

    private fun announce(entity: PokemonEntity, level: ServerLevel, data: BossData) {
        val radius = BossConfig.spawns.announceRadius
        if (radius <= 0.0) return
        for (player in level.players()) {
            val distance = sqrt(player.distanceToSqr(entity))
            if (distance > radius) continue
            player.sendSystemMessage(
                Component.translatable(
                    "generations_core.boss.spawned",
                    Bosses.tierName(data.rarity, data.titan),
                    Bosses.speciesName(entity.pokemon),
                    distance.toInt(),
                ),
            )
        }
    }
}
