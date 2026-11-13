package generations.gg.generations.core.generationscore.common.world.ultraspace.spawning

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.entity.SpawnBucketChosenEvent
import com.cobblemon.mod.common.api.events.entity.SpawnEvent
import com.cobblemon.mod.common.api.molang.function.WorldMoLangFunctions
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.weather.UltraWeatherSky
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import java.util.Random

/**
 * Phase 5, what Cobblemon's own spawning cannot do in the Ultra worlds:
 * - the MoLang functions world.ultra_weather (the weather in progress, '' when calm or
 *   outside the Ultra worlds) and world.ultra_weather_dangerous (1 or 0), read by the
 *   spawn rules of data/generations_core/spawn_rules/ultra_space;
 * - during a dangerous weather, Cobblemon's bucket drawn again with the uncommon and rare
 *   buckets weighing more (ultra_spawns.json): rare encounters truly more frequent,
 *   since a heavier species only takes more of its own bucket;
 * - the natural Ultra Beasts of the Ultra worlds: only a share of Cobblemon's picks kept,
 *   one alive per world, a wait between two, their own shiny odds.
 * The other dimensions and the Pokémon spawned by the mod itself are not touched.
 */
object UltraSpawns {
    private val random = Random()
    /** Game time of the last natural Ultra Beast of each world (from the server's start). */
    private val lastBeast = HashMap<String, Long>()

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) {
            UltraSpawnsConfig.load()
            lastBeast.clear()
        }
        WorldMoLangFunctions.custom.add { world: Holder<Level> ->
            hashMapOf<String, (MoParams) -> Any>(
                "ultra_weather" to { _ -> StringValue(weather(world.value())) },
                "ultra_weather_dangerous" to { _ -> DoubleValue(if (dangerous(world.value())) 1.0 else 0.0) },
            )
        }
        CobblemonEvents.SPAWN_BUCKET_CHOSEN.subscribe(Priority.NORMAL) { bucketChosen(it) }
        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.NORMAL) { spawning(it) }
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.NORMAL) { spawned(it) }
    }

    /** The weather that changes the spawns of a level now ('' when none): past the first part of its coming. */
    @JvmStatic
    fun weather(level: Level): String {
        if (!UltraSpawnsConfig.settings.weatherSpawns) return ""
        val active = UltraWeatherSky.active(level) ?: return ""
        return if (active.intensity(level.gameTime.toDouble()) >= 0.3f) active.event.id else ""
    }

    fun dangerous(level: Level): Boolean = weather(level).let { id -> id.isNotEmpty() && UltraWeatherSky.active(level)?.event?.dangerous == true }

    private fun bucketChosen(event: SpawnBucketChosenEvent) {
        val level = event.spawnCause.entity?.level() ?: return
        if (!UltraWorlds.isUltra(level.dimension())) return
        val id = weather(level).takeIf { it.isNotEmpty() } ?: return
        val factors = UltraSpawnsConfig.settings.dangerousBuckets[id] ?: return
        if (factors.isEmpty() || factors.values.all { it == 1f }) return
        // The same draw as Cobblemon's, with the weights changed.
        val weights = event.bucketWeights.mapValues { (bucket, weight) -> weight * (factors[bucket] ?: 1f) }
        val total = weights.values.sum()
        if (total <= 0f) return
        var roll = random.nextFloat() * total
        for ((bucket, weight) in weights) {
            if (roll < weight) {
                event.bucket = bucket
                return
            }
            roll -= weight
        }
    }

    private fun isUltraBeast(entity: PokemonEntity): Boolean = entity.pokemon.hasLabels("ultra_beast")

    /** A natural spawn about to happen: an Ultra Beast of the Ultra worlds only within the limits. */
    private fun spawning(event: SpawnEvent<*>) {
        val entity = event.entity as? PokemonEntity ?: return
        val level = entity.level() as? ServerLevel ?: return
        val id = UltraWorlds.idOf(level.dimension()) ?: return
        if (!isUltraBeast(entity)) return
        val rules = UltraSpawnsConfig.settings.ultraBeasts
        val keep = rules.keepChance * (if (dangerous(level)) rules.dangerousWeatherMultiplier else 1f)
        if (!rules.enabled || random.nextFloat() >= keep) return event.cancel()
        val now = level.server.overworld().gameTime
        val last = lastBeast[id]
        if (last != null && now - last < (rules.cooldownMinutes * 1200f).toLong()) return event.cancel()
        if (rules.maxAlivePerWorld > 0 && alive(level) >= rules.maxAlivePerWorld) return event.cancel()
        lastBeast[id] = now
        GenerationsCore.LOGGER.info(
            "Ultra Space spawns: a wild {} in {} at {}", entity.pokemon.species.name, id, entity.blockPosition().toShortString(),
        )
    }

    /** Wild Ultra Beasts loaded in a level. */
    fun alive(level: ServerLevel): Int =
        level.getEntities(CobblemonEntities.POKEMON) { it.isAlive && it.pokemon.isWild() && isUltraBeast(it) }.size

    /** A natural Ultra Beast of the Ultra worlds is shiny by its own odds, not Cobblemon's. */
    private fun spawned(event: SpawnEvent<PokemonEntity>) {
        val entity = event.entity
        if (!UltraWorlds.isUltra(entity.level().dimension()) || !isUltraBeast(entity)) return
        entity.pokemon.shiny = random.nextFloat() * UltraSpawnsConfig.settings.ultraBeasts.naturalShinyOdds < 1f
    }

    /** /ultrawarp spawns: the last natural Ultra Beast of each world (minutes ago, -1 for none) and those alive. */
    fun beasts(server: net.minecraft.server.MinecraftServer): List<Triple<String, Long, Int>> {
        val now = server.overworld().gameTime
        return UltraWorlds.IDS.map { id ->
            val level = UltraWorlds.key(id)?.let { server.getLevel(it) }
            Triple(id, lastBeast[id]?.let { (now - it) / 1200 } ?: -1L, level?.let { alive(it) } ?: 0)
        }
    }
}
