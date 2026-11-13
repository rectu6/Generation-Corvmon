package generations.gg.generations.core.generationscore.common.world.ultraspace.encounter

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraSpaceConfig
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpOutcome
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.level.block.Blocks
import java.util.Random
import java.util.UUID

/**
 * The arrival encounter (phase 5): after a successful Ultra Warp Ride, once the player
 * stands in the destination's world, one wild Pokémon appears near them, of the world
 * and of the arrival's biome, sometimes an Ultra Beast; never a legendary. It is a real
 * wild Cobblemon Pokémon (fought, caught or left; no battle forced), spawned by the mod
 * itself, apart from Cobblemon's spawning.
 *
 * Decided by the server alone from the journey's outcome (light-years, portal level,
 * destination: UltraWarpOutcome), drawn once and saved before it appears
 * (UltraEncounterSavedData): one per journey, never drawn again. If no spot is found
 * (chunks loading, no room), it tries again while the player is in that world, after a
 * reconnection too. For priorityMinutes, only the traveller can throw a ball at it or
 * battle it.
 */
object UltraEncounters {
    private const val OWNER = "generations_core_ultra_encounter_owner"
    private const val UNTIL = "generations_core_ultra_encounter_until"
    private const val JOURNEY = "generations_core_ultra_encounter_journey"

    private val random = Random()
    private var data: UltraEncounterSavedData? = null
    /** Journeys whose failed spot search was already written to the log. */
    private val toldNoRoom = HashSet<UUID>()

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) {
            UltraEncountersConfig.load()
            data = UltraEncounterSavedData.get(it.server)
        }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) {
            data = null
            toldNoRoom.clear()
        }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { if (it.server.tickCount % 20 == 0) tick(it.server) }
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.NORMAL) { ballHit(it) }
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL) { battleStarting(it) }
    }

    private fun now(server: MinecraftServer) = server.overworld().gameTime

    // ---- the draw ----

    /** UltraWarpTransfers: the player is down in the destination's world after a successful journey. */
    fun onArrival(server: MinecraftServer, player: ServerPlayer, outcome: UltraWarpOutcome, level: ServerLevel, spot: BlockPos) {
        val data = data ?: return
        if (!outcome.success || !UltraEncountersConfig.settings.enabled) return
        if (data.entries.containsKey(outcome.journey)) return
        val world = UltraWorlds.idOf(level.dimension()) ?: return
        draw(server, player.uuid, outcome.journey, world, biomeOf(level, spot), outcome.lightYears, outcome.rarity.coerceIn(1, 4), false)
    }

    private fun biomeOf(level: ServerLevel, pos: BlockPos): String =
        level.getBiome(pos).unwrapKey().map { it.location().path }.orElse("")

    /** The candidates of an ordinary encounter in a biome ("ultra_jungle/giant_jungle"). */
    fun ordinary(world: String, biome: String): List<UltraEncounterDraw.Candidate> {
        val settings = UltraEncountersConfig.settings
        (settings.species[biome] ?: settings.species[world])?.let { list ->
            return list.filter(::allowed).map { UltraEncounterDraw.Candidate(it, 1f) }
        }
        val prefix = "$biome/"
        return CobblemonSpawnPools.WORLD_SPAWN_POOL.details.asSequence()
            .filterIsInstance<PokemonSpawnDetail>()
            .filter { it.id.startsWith(prefix) && it.bucket in settings.ordinaryBuckets }
            .mapNotNull { detail -> detail.pokemon.species?.lowercase()?.takeIf(::allowed)?.let { UltraEncounterDraw.Candidate(it, detail.weight) } }
            .toList()
    }

    private fun allowed(species: String): Boolean {
        val found = PokemonSpecies.getByName(species.substringAfter(':').lowercase()) ?: return false
        return UltraEncountersConfig.settings.excludedLabels.none { it in found.labels }
    }

    /** One draw with the three chances of ultra_warp_ride.json for these light-years and this portal level. */
    fun drawFor(world: String, biome: String, lightYears: Double, portalLevel: Int, rng: Random = random): UltraEncounterDraw.Result? {
        val tables = UltraSpaceConfig.ride.encounters
        val settings = UltraEncountersConfig.settings
        val tier = settings.level(lightYears)
        return UltraEncounterDraw.draw(
            rng,
            tables.chance(tables.ultraBeast, lightYears, portalLevel),
            tables.chance(tables.ordinaryShiny, lightYears, portalLevel),
            tables.chance(tables.ultraBeastShiny, lightYears, portalLevel),
            settings.ultraBeasts[world].orEmpty(),
            ordinary(world, biome),
            tier.min..tier.max,
            settings.ultraBeastMinLevel..settings.ultraBeastMaxLevel,
        )
    }

    private fun draw(server: MinecraftServer, player: UUID, journey: UUID, world: String, biome: String, lightYears: Double, portalLevel: Int, test: Boolean): UltraEncounterSavedData.Entry? {
        val data = data ?: return null
        val now = now(server)
        val result = drawFor(world, biome, lightYears, portalLevel)
        val entry = if (result == null) {
            UltraEncounterSavedData.Entry(journey, player, world, biome, lightYears, portalLevel, false, "", 0, false, now, now, UltraEncounterSavedData.State.NONE, test = test)
        } else {
            UltraEncounterSavedData.Entry(
                journey, player, world, biome, lightYears, portalLevel, result.ultraBeast, result.species, result.level, result.shiny,
                now, now + (UltraEncountersConfig.settings.delaySeconds * 20f).toLong(), UltraEncounterSavedData.State.DRAWN, test = test,
            )
        }
        // Saved before anything appears: the same journey never draws again.
        data.entries[journey] = entry
        data.prune()
        data.setDirty()
        GenerationsCore.LOGGER.info(
            "Ultra Space encounter: journey {} ({}{}), {} ly, portal level {}, {}/{}: {}",
            journey, server.playerList.getPlayer(player)?.gameProfile?.name ?: player, if (test) ", test" else "", "%.1f".format(lightYears), portalLevel, world, biome,
            if (result == null) "no species here" else "${result.species} level ${result.level}${if (result.ultraBeast) " (Ultra Beast)" else ""}${if (result.shiny) " SHINY" else ""} (shiny chance ${result.shinyChance})",
        )
        return entry
    }

    // ---- the appearance ----

    private fun tick(server: MinecraftServer) {
        val data = data ?: return
        val now = now(server)
        val settings = UltraEncountersConfig.settings
        var changed = false
        for (entry in data.entries.values) {
            if (entry.state != UltraEncounterSavedData.State.DRAWN) continue
            if (settings.expireMinutes > 0f && now - entry.drawnAt > (settings.expireMinutes * 1200f).toLong()) {
                entry.state = UltraEncounterSavedData.State.EXPIRED
                changed = true
                GenerationsCore.LOGGER.info("Ultra Space encounter: journey {} expired without finding a place", entry.journey)
                continue
            }
            if (now < entry.readyAt) continue
            val player = server.playerList.getPlayer(entry.player) ?: continue
            val level = player.serverLevel()
            if (UltraWorlds.idOf(level.dimension()) != entry.world || !player.isAlive || player.isSpectator) continue
            if (spawn(entry, player, level, now)) {
                changed = true
            } else {
                entry.readyAt = now + (settings.retrySeconds * 20f).toLong()
                if (toldNoRoom.add(entry.journey)) GenerationsCore.LOGGER.info("Ultra Space encounter: journey {}: no spot near {} yet, trying again", entry.journey, player.gameProfile.name)
            }
        }
        if (changed) data.setDirty()
    }

    private fun spawn(entry: UltraEncounterSavedData.Entry, player: ServerPlayer, level: ServerLevel, now: Long): Boolean {
        val entity = runCatching {
            PokemonProperties.parse("${entry.species} level=${entry.level} shiny=${entry.shiny}").createEntity(level)
        }.getOrElse {
            GenerationsCore.LOGGER.error("Ultra Space encounter: could not make {} ({})", entry.species, it.message)
            entry.state = UltraEncounterSavedData.State.EXPIRED
            return true
        }
        val spot = findSpot(level, player, entity) ?: return false
        entity.moveTo(spot.x + 0.5, spot.y.toDouble(), spot.z + 0.5, random.nextFloat() * 360f, 0f)
        val settings = UltraEncountersConfig.settings
        entity.pokemon.persistentData.apply {
            putString(JOURNEY, entry.journey.toString())
            putUUID(OWNER, entry.player)
            putLong(UNTIL, now + (settings.priorityMinutes * 1200f).toLong())
        }
        if (!level.addFreshEntity(entity)) return false
        entry.state = UltraEncounterSavedData.State.SPAWNED
        entry.entity = entity.uuid
        val name = entity.pokemon.species.translatedName
        player.sendSystemMessage(
            Component.translatable(if (entry.ultraBeast) "generations_core.ultra_encounter.appear_beast" else "generations_core.ultra_encounter.appear", name)
                .withStyle(if (entry.ultraBeast) ChatFormatting.LIGHT_PURPLE else ChatFormatting.AQUA)
        )
        GenerationsCore.LOGGER.info("Ultra Space encounter: journey {}: {} appeared at {}", entry.journey, entry.species, spot.toShortString())
        return true
    }

    /**
     * A spot near the player where the Pokémon fits: solid ground under it, nothing in
     * its box, no fluid and no lava around, in loaded chunks, above the void.
     */
    private fun findSpot(level: ServerLevel, player: ServerPlayer, entity: PokemonEntity): BlockPos? {
        val radius = UltraEncountersConfig.settings.searchRadius
        val centre = player.blockPosition()
        val pos = BlockPos.MutableBlockPos()
        for (attempt in 0 until 48) {
            val distance = 3 + random.nextInt(radius - 2)
            val angle = random.nextFloat() * Mth.TWO_PI
            val x = centre.x + Mth.floor(Mth.cos(angle) * distance)
            val z = centre.z + Mth.floor(Mth.sin(angle) * distance)
            if (!level.hasChunkAt(BlockPos(x, centre.y, z))) continue
            for (dy in 4 downTo -6) {
                pos.set(x, centre.y + dy, z)
                if (pos.y <= level.minBuildHeight + 1) break
                val below = pos.below()
                val ground = level.getBlockState(below)
                if (!ground.isFaceSturdy(level, below, Direction.UP) || ground.`is`(Blocks.LAVA) || ground.`is`(Blocks.MAGMA_BLOCK)) continue
                entity.moveTo(x + 0.5, pos.y.toDouble(), z + 0.5, 0f, 0f)
                val box = entity.boundingBox
                if (!level.noCollision(entity, box) || level.containsAnyLiquid(box)) continue
                if (level.getBlockStates(box.inflate(1.0)).anyMatch { it.fluidState.`is`(FluidTags.LAVA) || it.`is`(Blocks.FIRE) }) continue
                return pos.immutable()
            }
        }
        return null
    }

    // ---- the traveller's priority ----

    /** The traveller who has priority on this Pokémon now, null when none. */
    private fun priorityOwner(entity: PokemonEntity): UUID? {
        val tag = entity.pokemon.persistentData
        if (!tag.hasUUID(OWNER) || entity.level().gameTime >= tag.getLong(UNTIL)) return null
        return tag.getUUID(OWNER)
    }

    private fun ballHit(event: ThrownPokeballHitEvent) {
        val owner = priorityOwner(event.pokemon) ?: return
        val thrower = event.pokeBall.owner as? ServerPlayer ?: return
        if (thrower.uuid == owner) return
        event.cancel()
        thrower.displayClientMessage(Component.translatable("generations_core.ultra_encounter.priority").withStyle(ChatFormatting.GOLD), true)
    }

    private fun battleStarting(event: BattleStartedEvent.Pre) {
        val battle = event.battle
        for (actor in battle.actors) {
            val wild = actor as? PokemonBattleActor ?: continue
            val entity = wild.pokemon.entity ?: continue
            val owner = priorityOwner(entity) ?: continue
            val others = battle.players.filter { it.uuid != owner }
            if (others.isEmpty()) continue
            event.cancel()
            others.forEach { it.displayClientMessage(Component.translatable("generations_core.ultra_encounter.priority").withStyle(ChatFormatting.GOLD), true) }
            return
        }
    }

    // ---- /ultrawarp encounter ----

    /** As if the player had just arrived after such a journey (a test journey, saved like the others). */
    fun test(player: ServerPlayer, lightYears: Double, portalLevel: Int): UltraEncounterSavedData.Entry? {
        val world = UltraWorlds.idOf(player.level().dimension()) ?: return null
        return draw(player.server, player.uuid, UUID.randomUUID(), world, biomeOf(player.serverLevel(), player.blockPosition()), lightYears, portalLevel.coerceIn(1, 4), true)
    }

    /** The encounters not appeared yet. */
    fun pending(): List<UltraEncounterSavedData.Entry> = data?.entries?.values?.filter { it.state == UltraEncounterSavedData.State.DRAWN }.orEmpty()

    /** The last ones (any state), for the command. */
    fun last(count: Int): List<UltraEncounterSavedData.Entry> = data?.entries?.values?.toList()?.takeLast(count).orEmpty()
}
