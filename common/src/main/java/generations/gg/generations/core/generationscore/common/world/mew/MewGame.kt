package generations.gg.generations.core.generationscore.common.world.mew

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.GenerationsNetwork.sendPacket
import generations.gg.generations.core.generationscore.common.network.packets.mew.S2CMewGamePacket
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.Random
import java.util.UUID

/**
 * Mew's game on the server (MEW-PLAN.md §§ 7 to 9): one MewGameSession per player playing, in
 * memory only (a session cut by a stop or a crash is a failure, D6). Every 5 ticks: the time limit,
 * leaving the area or the island, round 3's distances. A click is checked again here (session, step,
 * target, distance from the eyes, rate). Each player sees only their own illusions (D10).
 */
object MewGame {
    class Live(
        val session: MewGameSession,
        val origin: Vec3,
        val dimension: ResourceKey<Level>,
        val radius: Double,
        val hint: Int,
    )

    private val sessions = HashMap<UUID, Live>()
    private val random = Random()
    private var stopping = false

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { stopping = false; sessions.clear() }
        // First, before the other systems hear of the stop: the challenges are failures, not losses.
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.HIGHEST) { event ->
            stopping = true
            for (uuid in sessions.keys.toList()) finish(event.server, uuid, MewGameSession.Outcome.STOPPED)
        }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { event ->
            if (sessions.containsKey(event.player.uuid)) finish(event.player.server, event.player.uuid, MewGameSession.Outcome.DISCONNECTED)
        }
        PlatformEvents.PLAYER_DEATH.subscribe(Priority.LOWEST) { event ->
            if (!event.isCanceled && sessions.containsKey(event.player.uuid)) finish(event.player.server, event.player.uuid, MewGameSession.Outcome.DIED)
        }
        PlatformEvents.CHANGE_DIMENSION.subscribe(Priority.NORMAL) { event ->
            val live = sessions[event.player.uuid] ?: return@subscribe
            if (event.destination?.dimension() != live.dimension) finish(event.player.server, event.player.uuid, MewGameSession.Outcome.DIMENSION)
        }
    }

    val isStopping: Boolean get() = stopping

    fun isPlaying(player: UUID): Boolean = sessions.containsKey(player)

    fun live(player: UUID): Live? = sessions[player]

    fun count(): Int = sessions.size

    private fun rules(config: MewConfig): MewGameSession.Rules {
        val game = config.minigame
        return MewGameSession.Rules(
            rounds = game.rounds.toIntArray(),
            maxTicks = (game.maxMinutes * 1200.0).toLong(),
            leaveTicks = game.leaveSeconds * 20L,
            catchDistance = game.catchBlocks,
            interactDistance = game.interactBlocks + 1.5,
        )
    }

    /**
     * The challenge starts for this player: the opening (or Mew's return after a failure), then
     * the rounds. test: a copy in memory (the real progress is never touched). firstRound: a test
     * starting straight at that round.
     */
    fun start(player: ServerPlayer, test: Boolean, comeback: Boolean, firstRound: Int = 0): Boolean {
        if (sessions.containsKey(player.uuid)) return false
        val config = MewHunt.config
        val now = MewHunt.now
        val session = MewGameSession(random.nextInt(), player.uuid, test, rules(config), now, comeback, firstRound)
        val live = Live(session, player.position(), player.level().dimension(), config.minigame.gameRadius, (config.minigame.hintStrength * 100.0).toInt())
        sessions[player.uuid] = live
        val event = if (comeback) S2CMewGamePacket.RETURN else S2CMewGamePacket.OPEN
        player.sendPacket(S2CMewGamePacket(event, session.id, session.step, 0, 0, 0, session.ticksLeft(now).toInt(), player.position(), player.yRot, emptyList()))
        val key = if (comeback) "generations_core.mew.game.return" else "generations_core.mew.game.open"
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.LIGHT_PURPLE))
        GenerationsCore.LOGGER.info("Mew game: {} {} (session {}{})", player.scoreboardName, if (comeback) "meets Mew again" else "starts the challenge", session.id, if (test) ", test" else "")
        return true
    }

    fun tick(server: MinecraftServer, ticks: Long) {
        if (sessions.isEmpty() || ticks % 5L != 0L) return
        for (uuid in sessions.keys.toList()) {
            val live = sessions[uuid] ?: continue
            val player = server.playerList.getPlayer(uuid)
            if (player == null) {
                finish(server, uuid, MewGameSession.Outcome.DISCONNECTED)
                continue
            }
            if (!player.isAlive) {
                finish(server, uuid, MewGameSession.Outcome.DIED)
                continue
            }
            if (player.level().dimension() != live.dimension) {
                finish(server, uuid, MewGameSession.Outcome.DIMENSION)
                continue
            }
            val dx = player.x - live.origin.x
            val dz = player.z - live.origin.z
            val inCircle = dx * dx + dz * dz <= (live.radius + 4.0) * (live.radius + 4.0)
            val onIsland = live.session.test || MewZones.containing(player).isNotEmpty()
            try {
                val event = live.session.tick(ticks, player.x, player.y, player.z, inCircle && onIsland) ?: continue
                handle(server, player, live, event)
            } catch (e: Exception) {
                GenerationsCore.LOGGER.error("Mew game: session of {} stopped", player.scoreboardName, e)
                finish(server, uuid, MewGameSession.Outcome.STOPPED)
            }
        }
    }

    private fun places(player: ServerPlayer, live: Live): Pair<List<Vec3>, Int> {
        val level = player.serverLevel()
        val game = MewHunt.config.minigame
        return when (live.session.rounds()) {
            1 -> listOf(MewPlaces.hidingSpot(level, player, live.origin, game.hideMinBlocks, game.hideMaxBlocks, live.radius, random)) to 0
            2 -> MewPlaces.copies(level, player, game.illusions, random) to random.nextInt(game.illusions)
            else -> {
                val count = game.courseMinPoints + random.nextInt(game.courseMaxPoints - game.courseMinPoints + 1)
                MewPlaces.course(level, player, live.origin, count, game.courseMinBlocks, game.courseMaxBlocks, live.radius, random) to 0
            }
        }
    }

    private fun MewGameSession.rounds(): Int = rules.rounds[roundIndex.coerceAtMost(rules.rounds.size - 1)]

    private fun sendRound(player: ServerPlayer, live: Live) {
        val s = live.session
        val value = if (s.round == 2) s.reshuffles else live.hint
        player.sendPacket(
            S2CMewGamePacket(
                S2CMewGamePacket.ROUND, s.id, s.step, s.round, if (s.round == 2) s.real else s.course, value,
                s.ticksLeft(MewHunt.now).toInt(), live.origin, player.yRot, s.points.map { Vec3(it[0], it[1], it[2]) },
            )
        )
    }

    private fun send(player: ServerPlayer, live: Live, event: Int, index: Int, value: Int, points: List<Vec3> = emptyList()) {
        val s = live.session
        player.sendPacket(S2CMewGamePacket(event, s.id, s.step, s.round, index, value, s.ticksLeft(MewHunt.now).toInt(), live.origin, player.yRot, points))
    }

    private fun handle(server: MinecraftServer, player: ServerPlayer, live: Live, event: MewGameSession.Event) {
        val s = live.session
        val now = MewHunt.now
        when (event) {
            MewGameSession.Event.NEED_ROUND, MewGameSession.Event.RESHUFFLE -> {
                val (points, real) = places(player, live)
                if (s.beginRound(now, points.map { doubleArrayOf(it.x, it.y, it.z) }, real)) sendRound(player, live)
                if (event == MewGameSession.Event.NEED_ROUND) {
                    player.displayClientMessage(Component.translatable("generations_core.mew.game.round.${s.round}").withStyle(ChatFormatting.LIGHT_PURPLE), true)
                }
            }
            MewGameSession.Event.ERROR -> send(player, live, S2CMewGamePacket.ERROR, s.real, s.errors)
            MewGameSession.Event.MOVE -> send(player, live, S2CMewGamePacket.MOVE, s.course, 0, listOf(Vec3(s.points[s.course][0], s.points[s.course][1], s.points[s.course][2])))
            MewGameSession.Event.STUCK -> {
                val p = s.points[s.course]
                val closer = MewPlaces.closer(player.serverLevel(), player, Vec3(p[0], p[1], p[2]))
                s.comeBack(now, doubleArrayOf(closer.x, closer.y, closer.z))
                send(player, live, S2CMewGamePacket.MOVE, s.course, 1, listOf(closer))
            }
            MewGameSession.Event.WARN -> {
                val seconds = MewHunt.config.minigame.leaveSeconds
                send(player, live, S2CMewGamePacket.WARN, 0, seconds)
                player.displayClientMessage(Component.translatable("generations_core.mew.game.warn", seconds).withStyle(ChatFormatting.GOLD), true)
            }
            MewGameSession.Event.END -> finish(server, player.uuid, s.outcome ?: MewGameSession.Outcome.STOPPED)
        }
    }

    /** A click from the client, checked again here. */
    fun interact(player: ServerPlayer, session: Int, step: Int, target: Int) {
        val live = sessions[player.uuid] ?: return
        val s = live.session
        if (target < 0 || target >= s.points.size) return
        val p = s.points[target]
        val distance = player.eyePosition.distanceTo(Vec3(p[0], p[1] + 0.6, p[2]))
        val event = s.interact(MewHunt.now, session, step, target, distance) ?: return
        handle(player.server, player, live, event)
    }

    /** The player gives up (the giveup command). */
    fun giveUp(player: ServerPlayer): Boolean {
        if (!sessions.containsKey(player.uuid)) return false
        finish(player.server, player.uuid, MewGameSession.Outcome.GAVE_UP)
        return true
    }

    /** The fail command: the current challenge ends as a failure. */
    fun forceFail(server: MinecraftServer, player: UUID): Boolean {
        if (!sessions.containsKey(player)) return false
        finish(server, player, MewGameSession.Outcome.GAVE_UP)
        return true
    }

    /** The session ends: the client is told, the real progress follows (never for a test). */
    fun finish(server: MinecraftServer, uuid: UUID, outcome: MewGameSession.Outcome) {
        val live = sessions.remove(uuid) ?: return
        live.session.end(outcome)
        val player = server.playerList.getPlayer(uuid)
        player?.sendPacket(
            S2CMewGamePacket(S2CMewGamePacket.END, live.session.id, live.session.step, live.session.round, 0, outcome.ordinal, 0, live.origin, player.yRot, emptyList())
        )
        GenerationsCore.LOGGER.info("Mew game: {} ended ({}){}", player?.scoreboardName ?: uuid, outcome, if (live.session.test) ", test" else "")
        if (live.session.test) {
            player?.sendSystemMessage(Component.translatable("generations_core.mew.game.test_end", Component.translatable("generations_core.mew.outcome." + outcome.name.lowercase())).withStyle(ChatFormatting.GRAY))
            if (outcome == MewGameSession.Outcome.WON && player != null) MewReward.won(server, player, test = true)
            return
        }
        val record = MewHuntData.get(server, uuid) ?: return
        val machine = MewHunt.machine
        if (outcome == MewGameSession.Outcome.WON) {
            if (machine.win(record)) {
                MewHuntData.saveNow(server)
                player?.sendSystemMessage(Component.translatable("generations_core.mew.game.won").withStyle(ChatFormatting.LIGHT_PURPLE))
                if (player != null && !stopping) MewReward.won(server, player, test = false)
            }
            return
        }
        val interrupted = outcome == MewGameSession.Outcome.DISCONNECTED || outcome == MewGameSession.Outcome.STOPPED
        if (interrupted && !MewHunt.config.retry.disconnectFails) {
            // Without the delay: Mew waits again (the next check starts the challenge anew).
            if (machine.fail(record, 1L)) record.cooldownTicks = 0L
        } else {
            machine.fail(record, MewHunt.config.retry.retryMinutes * 1200L)
        }
        if (stopping) MewHuntData.changed(server) else MewHuntData.saveNow(server)
        player?.sendSystemMessage(
            Component.translatable(
                "generations_core.mew.game.failed", Component.translatable("generations_core.mew.outcome." + outcome.name.lowercase()), MewHunt.config.retry.retryMinutes,
            ).withStyle(ChatFormatting.GRAY)
        )
    }

    /** At the server's start: a challenge saved as running had no session any more (a crash): a failure (D6). */
    fun recover(server: MinecraftServer) {
        val data = MewHuntData.of(server)
        var changed = 0
        for (record in data.records.values) {
            if (record.state == MewState.CHALLENGE && MewHunt.machine.fail(record, MewHunt.config.retry.retryMinutes * 1200L)) changed++
        }
        if (changed > 0) {
            data.setDirty()
            GenerationsCore.LOGGER.info("Mew game: {} challenge(s) cut by the last stop counted as failures", changed)
        }
    }
}
