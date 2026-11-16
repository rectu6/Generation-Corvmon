package generations.gg.generations.core.generationscore.common.world.cinematic

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.GenerationsNetwork.sendPacket
import generations.gg.generations.core.generationscore.common.network.packets.cinematic.S2CCinematicEndPacket
import generations.gg.generations.core.generationscore.common.network.packets.cinematic.S2CCinematicStartPacket
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpSessions
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** A legendary cinematic being played on the server: one object, nothing in the world. */
class CinematicSession(
    val id: UUID,
    val scene: String,
    val dimension: ResourceKey<Level>,
    val origin: Vec3,
    val facing: Float,
    val spawn: Vec3,
    val invoker: UUID,
    val invokerName: String,
    val species: ResourceLocation,
    val aspects: Set<String>,
    val test: Boolean,
    val machine: CinematicStateMachine,
    /** The players watching it, the invoker first. Nobody is added once it started. */
    val participants: MutableSet<UUID>,
    /** The players near it who could not watch (in a battle...): the ambience only, on their client. */
    val ambient: MutableSet<UUID> = LinkedHashSet(),
    /** What makes the Pokémon appear (the RKS Machine's Mewtwo); the global onSpawn otherwise. */
    val onSpawn: ((CinematicSession) -> Unit)? = null,
)

/**
 * The legendary cinematics on the server (CINEMATIQUES-PLAN.md § 3): validation, the
 * participants, one start packet each, then nothing until the end packet. Interruptions
 * (the invoker leaving, /generations cinematic stop, the server stopping) end it for all; a
 * spectator leaving only leaves. Participants are not hurt while they watch.
 *
 * The test mode (/generations cinematic test) consumes nothing, spawns nothing and starts no
 * battle. The shrines (lot 5) will start sessions through start() with test = false and set
 * onSpawn.
 */
object LegendaryCinematics {
    var config: CinematicConfig = CinematicConfig().also { it.validate() }
        private set

    private val sessions = LinkedHashMap<UUID, CinematicSession>()
    private val watching = HashMap<UUID, CinematicSession>()

    /** Shrine mode (lot 5): the Pokémon appears, once (1 s before the end, or at once if cut short). */
    var onSpawn: ((CinematicSession) -> Unit)? = null

    /** Half a second ahead: the start packet reaches every client before the scene's second 0. */
    private const val LEAD_TICKS = 10

    sealed class StartResult {
        class Started(val session: CinematicSession) : StartResult()
        class Refused(val message: Component) : StartResult()
    }

    class Request(
        val level: ServerLevel,
        val scene: String,
        val origin: Vec3,
        val facing: Float,
        val spawn: Vec3,
        val invoker: ServerPlayer,
        val species: ResourceLocation,
        val aspects: Set<String>,
        val test: Boolean,
        /** This scene's own spawn (RKS Machine); the global onSpawn otherwise. */
        val onSpawn: ((CinematicSession) -> Unit)? = null,
    )

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { reload() }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) { stopAll(CinematicEndReason.SERVER_STOPPING) }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { left(it.player.uuid, it.player) }
        PlatformEvents.PLAYER_DEATH.subscribe(Priority.LOWEST) { event ->
            if (!event.isCanceled) left(event.player.uuid, event.player)
        }
        PlatformEvents.CHANGE_DIMENSION.subscribe(Priority.NORMAL) { event ->
            val session = watching[event.player.uuid] ?: return@subscribe
            if (event.destination?.dimension() != session.dimension) left(event.player.uuid, event.player)
        }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { tick(it.server) }
    }

    fun reload() {
        config = CinematicConfig.load()
    }

    fun sessions(): Collection<CinematicSession> = sessions.values

    fun isWatching(player: UUID): Boolean = watching.containsKey(player)

    fun session(id: UUID): CinematicSession? = sessions[id]

    /** Whether this player could watch a scene now (alive, playing, not in a battle, a journey or another scene). */
    fun canWatch(player: ServerPlayer): Boolean = free(player)

    /** Cut short without a word to its invoker (a test command); the Pokémon appears as after any interruption. */
    fun interruptQuietly(session: CinematicSession) {
        val server = com.cobblemon.mod.common.util.server() ?: return
        val interruption = session.machine.interrupt() ?: return
        if (interruption.spawnNow) spawn(session)
        end(server, session, CinematicEndReason.STOPPED)
    }

    /** Watching a cinematic: not hurt (except by what ignores invulnerability, like /kill). */
    @JvmStatic
    fun protects(player: ServerPlayer, source: DamageSource): Boolean =
        watching.containsKey(player.uuid) && !source.`is`(DamageTypeTags.BYPASSES_INVULNERABILITY)

    private fun key(name: String, vararg args: Any): net.minecraft.network.chat.MutableComponent = Component.translatable("generations_core.cinematic.$name", *args)

    /** Someone who can watch: alive, playing, not in a battle, a journey or another scene. */
    private fun free(player: ServerPlayer): Boolean =
        player.isAlive && !player.isSpectator && !watching.containsKey(player.uuid) &&
            UltraWarpSessions.get(player.uuid) == null && BattleRegistry.getBattleByParticipatingPlayer(player) == null

    fun start(request: Request): StartResult {
        val invoker = request.invoker
        if (!config.enabled) return StartResult.Refused(key("refused.disabled"))
        if (request.scene !in CinematicScenes.AVAILABLE) return StartResult.Refused(key("refused.unavailable", request.scene))
        if (!free(invoker)) return StartResult.Refused(key("refused.busy"))
        val radius = config.radius(request.scene)
        val near = sessions.values.any { it.dimension == request.level.dimension() && it.origin.distanceTo(request.origin) < 2.0 * maxOf(radius, 16.0) }
        if (near) return StartResult.Refused(key("refused.nearby"))

        val participants = LinkedHashSet<UUID>()
        participants += invoker.uuid
        val ambient = LinkedHashSet<UUID>()
        // Mewtwo's scene has an ambience for those who cannot watch it (D11).
        val ambientRadius = if (CinematicScenes.hasAmbience(request.scene)) config.ambientRadius else 0.0
        for (player in request.level.players()) {
            if (player.uuid == invoker.uuid) continue
            val distance = player.position().distanceTo(request.origin)
            if (distance <= radius && free(player)) participants += player.uuid
            else if (distance <= ambientRadius && player.isAlive && !player.isSpectator && !watching.containsKey(player.uuid)) ambient += player.uuid
        }
        val now = request.level.gameTime
        // Mewtwo's entity takes the actor's place right at the end (no second Mewtwo on screen for a second).
        val lead = CinematicScenes.spawnLeadTicks(request.scene)
        val machine = CinematicStateMachine(now + LEAD_TICKS, config.durationTicks(request.scene), request.test, lead)
        val session = CinematicSession(
            UUID.randomUUID(), request.scene, request.level.dimension(), request.origin, request.facing, request.spawn,
            invoker.uuid, invoker.gameProfile.name, request.species, request.aspects, request.test, machine, participants, ambient, request.onSpawn,
        )
        sessions[session.id] = session
        fun packet(ambience: Boolean) = S2CCinematicStartPacket(
            session.id, session.scene, session.origin, session.facing, session.spawn, machine.startTick, machine.durationTicks,
            invoker.uuid, session.species.toString(), session.aspects.toList(), session.test, config.debugOverlay && session.test, ambience,
        )
        val players = request.level.server.playerList
        val watch = packet(false)
        for (uuid in participants) {
            watching[uuid] = session
            players.getPlayer(uuid)?.sendPacket(watch)
        }
        if (ambient.isNotEmpty()) {
            val ambience = packet(true)
            for (uuid in ambient) players.getPlayer(uuid)?.sendPacket(ambience)
        }
        GenerationsCore.LOGGER.info(
            "Legendary cinematic {} ({}) started by {} at {} in {}, {} participant(s), {} with the ambience only{}",
            session.scene, session.id, session.invokerName, session.origin, session.dimension.location(), participants.size, ambient.size, if (session.test) ", test" else "",
        )
        return StartResult.Started(session)
    }

    private fun tick(server: MinecraftServer) {
        if (sessions.isEmpty()) return
        for (session in sessions.values.toList()) {
            val level = server.getLevel(session.dimension)
            if (level == null) {
                interrupt(server, session, CinematicEndReason.STOPPED)
                continue
            }
            val invoker = server.playerList.getPlayer(session.invoker)
            if (invoker == null || !invoker.isAlive || invoker.level().dimension() != session.dimension) {
                interrupt(server, session, CinematicEndReason.INVOKER_LEFT)
                continue
            }
            // A spectator gone elsewhere without an event (a teleport between dimensions by another mod).
            for (uuid in session.participants.toList()) {
                if (uuid == session.invoker) continue
                val player = server.playerList.getPlayer(uuid)
                if (player == null || !player.isAlive || player.level().dimension() != session.dimension) left(uuid, player)
            }
            for (action in session.machine.tick(level.gameTime)) when (action) {
                CinematicStateMachine.Action.SPAWN -> spawn(session)
                CinematicStateMachine.Action.FINISH -> end(server, session, CinematicEndReason.FINISHED)
            }
        }
    }

    private fun spawn(session: CinematicSession) {
        if (session.test) return
        try {
            (session.onSpawn ?: onSpawn)?.invoke(session)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Legendary cinematic {}: the Pokémon could not appear", session.id, e)
        }
    }

    /** Cut short for all: the Pokémon appears at once in shrine mode (never twice). */
    fun interrupt(server: MinecraftServer, session: CinematicSession, reason: CinematicEndReason) {
        val interruption = session.machine.interrupt() ?: return
        if (interruption.spawnNow) spawn(session)
        end(server, session, reason)
        server.playerList.getPlayer(session.invoker)?.displayClientMessage(key("interrupted").withStyle(ChatFormatting.GRAY), true)
    }

    private fun end(server: MinecraftServer, session: CinematicSession, reason: CinematicEndReason) {
        sessions.remove(session.id)
        val packet = S2CCinematicEndPacket(session.id, reason)
        for (uuid in session.participants) {
            if (watching[uuid] === session) watching.remove(uuid)
            server.playerList.getPlayer(uuid)?.sendPacket(packet)
        }
        for (uuid in session.ambient) server.playerList.getPlayer(uuid)?.sendPacket(packet)
        session.participants.clear()
        session.ambient.clear()
    }

    /** A participant disconnected, died or left the dimension. */
    private fun left(uuid: UUID, player: ServerPlayer?) {
        val session = watching[uuid] ?: return
        val server = player?.server ?: return
        if (uuid == session.invoker) {
            interrupt(server, session, CinematicEndReason.INVOKER_LEFT)
            return
        }
        watching.remove(uuid)
        session.participants.remove(uuid)
        server.playerList.getPlayer(uuid)?.sendPacket(S2CCinematicEndPacket(session.id, CinematicEndReason.LEFT))
    }

    /** Every scene, cut short (the cameras are given back). How many there were. */
    fun stopAll(reason: CinematicEndReason): Int {
        val all = sessions.values.toList()
        val server = com.cobblemon.mod.common.util.server()
        for (session in all) {
            if (server != null) interrupt(server, session, reason) else session.machine.interrupt()
        }
        sessions.clear()
        watching.clear()
        return all.size
    }
}
