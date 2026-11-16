package generations.gg.generations.core.generationscore.common.world.boss

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.entity.Despawner
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * The bosses alive in the loaded world, for the spawn limits and the commands.
 * Kept up to date by the bosses themselves: every boss entity calls tick once a
 * second (PokemonEntityMixin), which also ends its lifetime. An entry not seen for a
 * few seconds (removed, unloaded, no longer ticking) is dropped when read. Nothing
 * runs for other Pokémon but a look at their persistent data once a second.
 */
object BossRegistry {
    /** Ticks without news after which an entry is forgotten (the entity ticks every 20). */
    private const val STALE_TICKS = 100L

    class Entry(val entity: PokemonEntity, var dimension: ResourceKey<Level>, var x: Double, var z: Double, var seen: Long) {
        val pokemon get() = entity.pokemon
    }

    private val live = HashMap<UUID, Entry>()

    /** A boss Cobblemon never despawns (spawns.json naturalDespawn false); its lifetime still applies. */
    private val keep = object : Despawner<PokemonEntity> {
        override fun beginTracking(entity: PokemonEntity) {}
        override fun shouldDespawn(entity: PokemonEntity) = false
    }

    private fun now(level: ServerLevel) = level.server.overworld().gameTime

    fun track(entity: PokemonEntity, level: ServerLevel) {
        val now = now(level)
        val entry = live[entity.uuid]
        if (entry == null || entry.entity !== entity) {
            live[entity.uuid] = Entry(entity, level.dimension(), entity.x, entity.z, now)
        } else {
            entry.dimension = level.dimension()
            entry.x = entity.x
            entry.z = entity.z
            entry.seen = now
        }
    }

    fun forget(entity: PokemonEntity) {
        if (live[entity.uuid]?.entity === entity) live.remove(entity.uuid)
    }

    /** Once a second, from every Pokémon entity on the server. */
    @JvmStatic
    fun tick(entity: PokemonEntity) {
        val level = entity.level() as? ServerLevel ?: return
        val pokemon = entity.pokemon
        if (!BossData.has(pokemon.persistentData)) return
        // A boss that somehow got an owner (another mod, an old save) is a plain Pokémon again.
        if (!pokemon.isWild()) {
            Bosses.clear(pokemon)
            forget(entity)
            return
        }
        val data = Bosses.of(pokemon) ?: return
        track(entity, level)

        val spawns = BossConfig.spawns
        val defaultDespawner = Cobblemon.bestSpawner.defaultPokemonDespawner
        if (!spawns.naturalDespawn && entity.despawner === defaultDespawner) entity.despawner = keep
        else if (spawns.naturalDespawn && entity.despawner === keep) entity.despawner = defaultDespawner

        val lifetime = (spawns.lifetimeMinutes * 1200.0).toLong()
        if (lifetime > 0 && !entity.isBattling && now(level) - data.spawnedAt > lifetime) {
            GenerationsCore.LOGGER.info(
                "Bosses: {} ({}) left after {} minutes at {}",
                pokemon.species.name, data.rarity.id, spawns.lifetimeMinutes, entity.blockPosition().toShortString(),
            )
            forget(entity)
            entity.discard()
        }
    }

    /** Living bosses of a dimension (stale entries dropped). */
    fun alive(level: ServerLevel): List<Entry> {
        prune(now(level))
        val dimension = level.dimension()
        return live.values.filter { it.dimension == dimension }
    }

    fun all(level: ServerLevel): List<Entry> {
        prune(now(level))
        return live.values.toList()
    }

    fun cell(x: Double, size: Int): Int = Mth.floor(x / size)

    private fun prune(now: Long) {
        live.values.removeIf { it.entity.isRemoved || !it.entity.isAlive || now - it.seen > STALE_TICKS }
    }

    fun clear() = live.clear()
}
