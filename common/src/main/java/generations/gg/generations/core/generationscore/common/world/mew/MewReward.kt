package generations.gg.generations.core.generationscore.common.world.mew

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicPlaces
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicSession
import generations.gg.generations.core.generationscore.common.world.cinematic.LegendaryCinematics
import generations.gg.generations.core.generationscore.common.world.entity.block.PokemonUtil
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * After the challenge (MEW-PLAN.md §§ 10 to 12): the final cinematic (scene "mew" of the engine),
 * the real Mew (once: WON → RELEASED, saved before it appears), its capture by its hunter (a new
 * hunt), and a lost Mew (caught by someone else, fainted, not caught within releaseTimeoutMinutes):
 * lostMew "reset" or "retry" (D8).
 *
 * The scene refused (another one nearby, the player busy): tried again every 5 s for
 * cinematicRetrySeconds, then Mew appears without it (D9). The hunter gone during the scene or
 * the server stopping: no Mew, WON is kept and the scene plays again at a later check.
 */
object MewReward {
    /** Marks the Mew of a hunt: its hunter's UUID, in the Pokémon's persistent data. */
    const val MARK = "generations_mew_hunt"

    private val MEW = ResourceLocation.fromNamespaceAndPath("cobblemon", "mew")

    private class Pending(val until: Long, var next: Long, val test: Boolean)

    private val pending = HashMap<UUID, Pending>()

    /** Hunters whose scene is playing (no second one). */
    private val playing = HashSet<UUID>()

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { clear() }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) { clear() }
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL) { event -> captured(event.player, event.pokemon) }
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL) { event ->
            val pokemon = event.killed.originalPokemon
            val hunter = hunterOf(pokemon) ?: return@subscribe
            val server = com.cobblemon.mod.common.util.server() ?: return@subscribe
            lost(server, hunter, pokemon, "fainted")
        }
    }

    /** Clears what only lives in memory (server start and stop). */
    fun clear() {
        pending.clear()
        playing.clear()
    }

    fun isPending(player: UUID): Boolean = pending.containsKey(player) || playing.contains(player)

    private fun hunterOf(pokemon: Pokemon): UUID? {
        val mark = pokemon.persistentData.getString(MARK)
        if (mark.isEmpty()) return null
        return runCatching { UUID.fromString(mark) }.getOrNull()
    }

    /** The challenge was won: the final scene (or Mew without it). */
    fun won(server: MinecraftServer, player: ServerPlayer, test: Boolean) {
        if (isPending(player.uuid)) return
        val now = MewHunt.now
        if (!tryScene(player, test)) pending[player.uuid] = Pending(now + MewHunt.config.spawn.cinematicRetrySeconds * 20L, now + 100L, test)
    }

    private fun mewSize(): Pair<Double, Double> {
        val form = PokemonSpecies.getByIdentifier(MEW)?.standardForm ?: return 0.6 to 0.6
        return (form.hitbox.width() * form.baseScale).toDouble() to (form.hitbox.height() * form.baseScale).toDouble()
    }

    /** The scene around the player (+z: where they look); false when the engine refused it. */
    private fun tryScene(player: ServerPlayer, test: Boolean): Boolean {
        if (!MewHunt.config.spawn.cinematic) {
            spawnNow(player, test)
            return true
        }
        val level = player.serverLevel()
        val origin = Vec3(player.x, CinematicPlaces.ground(level, player.x, player.y, player.z), player.z)
        val facing = player.yRot
        val (width, height) = mewSize()
        val landing = CinematicPlaces.landing(level, origin, facing, width, height)
        val hunter = player.uuid
        val result = LegendaryCinematics.start(
            LegendaryCinematics.Request(level, CinematicScenes.MEW, origin, facing, landing, player, MEW, emptySet(), test) { session -> sceneEnds(session, hunter) }
        )
        if (result is LegendaryCinematics.StartResult.Started) {
            // A test scene spawns nothing (the engine's test mode): nothing to wait for.
            if (!test) playing += hunter
            return true
        }
        return false
    }

    /** The scene's end (or its interruption): Mew appears if its hunter is still there. */
    private fun sceneEnds(session: CinematicSession, hunter: UUID) {
        playing -= hunter
        val server = com.cobblemon.mod.common.util.server() ?: return
        val player = server.playerList.getPlayer(hunter)
        if (player == null || !player.isAlive || player.level().dimension() != session.dimension || MewGame.isStopping) {
            GenerationsCore.LOGGER.info("Mew: the scene of {} was cut, Mew will come at a later check", hunter)
            return
        }
        spawnAt(server, player, player.serverLevel(), session.spawn, session.facing + 180f, session.test)
    }

    private fun spawnNow(player: ServerPlayer, test: Boolean) {
        val level = player.serverLevel()
        val (width, height) = mewSize()
        val landing = CinematicPlaces.landing(level, player.position(), player.yRot, width, height)
        spawnAt(player.server, player, level, landing, player.yRot + 180f, test)
    }

    /** The real Mew (a test: an unmarked one, nothing saved). */
    private fun spawnAt(server: MinecraftServer, player: ServerPlayer, level: ServerLevel, at: Vec3, yaw: Float, test: Boolean) {
        val spawn = MewHunt.config.spawn
        val text = buildString {
            append(MEW.toString()).append(" level=").append(spawn.level)
            if (spawn.properties.isNotBlank()) append(' ').append(spawn.properties.trim())
        }
        try {
            val entity = PokemonProperties.parse(text).createEntity(level)
            if (!test) {
                val record = MewHuntData.get(server, player.uuid) ?: return
                // Saved before it appears: a crash right now never gives a second Mew.
                if (!MewHunt.machine.release(record, entity.pokemon.uuid.toString(), System.currentTimeMillis())) return
                entity.pokemon.persistentData.putString(MARK, player.uuid.toString())
                MewHuntData.saveNow(server)
            }
            PokemonUtil.spawn(entity, level, at, yaw)
            player.sendSystemMessage(Component.translatable(if (test) "generations_core.mew.reward.test_spawn" else "generations_core.mew.reward.spawn").withStyle(ChatFormatting.LIGHT_PURPLE))
            GenerationsCore.LOGGER.info("Mew: {} Mew of {} at {}{}", if (test) "test" else "the real", player.scoreboardName, at, if (test) "" else " (${entity.pokemon.uuid})")
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Mew: the Mew of {} could not appear", player.scoreboardName, e)
        }
    }

    /** The spawn command (test: unmarked; real: from WON only). */
    fun spawnCommand(player: ServerPlayer, test: Boolean): Boolean {
        if (!test) {
            val record = MewHuntData.get(player.server, player.uuid) ?: return false
            if (record.state != MewState.WON) return false
        }
        spawnNow(player, test)
        return true
    }

    /** The cinematic command: the scene alone (a test spawns nothing), or the real one from WON. */
    fun cinematicCommand(player: ServerPlayer, test: Boolean): Boolean {
        if (!test) {
            val record = MewHuntData.get(player.server, player.uuid) ?: return false
            if (record.state != MewState.WON) return false
        }
        return tryScene(player, test)
    }

    /** Every tick: the scenes to try again, then Mew without one. */
    fun tick(server: MinecraftServer, ticks: Long) {
        if (pending.isEmpty() || ticks % 20L != 0L) return
        for ((uuid, p) in pending.entries.toList()) {
            if (ticks < p.next) continue
            val player = server.playerList.getPlayer(uuid)
            if (player == null) {
                pending.remove(uuid)
                continue
            }
            if (tryScene(player, p.test)) {
                pending.remove(uuid)
            } else if (ticks >= p.until) {
                pending.remove(uuid)
                spawnNow(player, p.test)
            } else {
                p.next = ticks + 100L
            }
        }
    }

    /** Every minute: a released Mew not caught in time is lost. */
    fun checkTimeouts(server: MinecraftServer) {
        val data = MewHuntData.of(server)
        val limit = MewHunt.config.releaseTimeoutMinutes * 60_000L
        val now = System.currentTimeMillis()
        for ((uuid, record) in data.records.entries.toList()) {
            if (record.state != MewState.RELEASED) continue
            if (record.releasedAtMs > 0L && now - record.releasedAtMs > limit) lostRecord(server, uuid, record, "timeout")
        }
    }

    private fun captured(player: ServerPlayer, pokemon: Pokemon) {
        val hunter = hunterOf(pokemon) ?: return
        pokemon.persistentData.remove(MARK)
        val server = player.server
        if (hunter != player.uuid) {
            lost(server, hunter, pokemon, "caught by ${player.scoreboardName}")
            return
        }
        val record = MewHuntData.get(server, hunter) ?: return
        if (MewHunt.machine.caught(record)) {
            MewHuntData.saveNow(server)
            player.sendSystemMessage(Component.translatable("generations_core.mew.reward.caught").withStyle(ChatFormatting.LIGHT_PURPLE))
            GenerationsCore.LOGGER.info("Mew: {} caught their Mew, a new hunt begins", player.scoreboardName)
        }
    }

    private fun lost(server: MinecraftServer, hunter: UUID, pokemon: Pokemon, why: String) {
        val record = MewHuntData.get(server, hunter) ?: return
        if (record.mewUuid.isNotEmpty() && record.mewUuid != pokemon.uuid.toString()) return
        lostRecord(server, hunter, record, why)
    }

    private fun lostRecord(server: MinecraftServer, hunter: UUID, record: MewRecord, why: String) {
        val config = MewHunt.config
        val retry = config.lostMew == MewConfig.LOST_RETRY
        if (!MewHunt.machine.lost(record, retry, config.retry.retryMinutes * 1200L)) return
        MewHuntData.saveNow(server)
        GenerationsCore.LOGGER.info("Mew: the Mew of {} was lost ({}), {}", hunter, why, config.lostMew)
        server.playerList.getPlayer(hunter)?.sendSystemMessage(
            Component.translatable(if (retry) "generations_core.mew.reward.lost_retry" else "generations_core.mew.reward.lost_reset").withStyle(ChatFormatting.GRAY)
        )
    }
}
