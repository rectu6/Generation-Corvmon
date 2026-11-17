package generations.gg.generations.core.generationscore.common.world.johto

import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.GenerationsNetwork.sendPacket
import generations.gg.generations.core.generationscore.common.network.packets.johto.C2SJohtoTrialActionPacket
import generations.gg.generations.core.generationscore.common.network.packets.johto.S2CJohtoTrialPacket
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicPlaces
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The trials' engine (JOHTO-PLAN.md §§ 6-7), on the server: one run per player at most, in
 * memory; checked every 2 ticks for the players in a trial only (flat shapes, no block search;
 * the terrain is read only when a round places its elements). Everything is decided here; the
 * client draws what it is told (one packet per event). A failure, leaving the zone too long,
 * dying or disconnecting: the session waits its delay (Johto.trialFailed); a round won is kept.
 */
object JohtoTrials {
    class Run(
        val player: UUID,
        val name: String,
        val beast: JohtoBeast,
        val zone: JohtoPlaces.Zone,
        val test: Boolean,
        val session: Int,
        var round: Int,
        val dimension: ResourceKey<Level>,
        val started: Long,
        seed: Long,
    ) {
        var step = 0
        var deadline = 0L
        var outsideSince = -1L
        var lastWarn = -1
        val random = java.util.Random(seed)
        lateinit var logic: Logic
        lateinit var centre: Vec3
    }

    abstract class Logic(val run: Run) {
        abstract fun round(level: ServerLevel, player: ServerPlayer, now: Long)
        abstract fun tick(level: ServerLevel, player: ServerPlayer, now: Long)
        open fun action(level: ServerLevel, player: ServerPlayer, target: Int, now: Long) {}
        /** The area to stay in: the zone (with a margin), Entei's arena. */
        open fun inside(player: ServerPlayer): Boolean = run.zone.contains(player.x, player.y, player.z, 4.0)
        abstract val roundSeconds: Int
        /** The area's radius (drawn as its border). */
        open val radius: Double get() = run.zone.reach
        open val endurance: Int get() = 0
    }

    private val runs = HashMap<UUID, Run>()
    private var sessions = 1

    fun isRunning(player: UUID): Boolean = runs.containsKey(player)

    fun run(player: UUID): Run? = runs[player]

    fun runs(): Collection<Run> = runs.values

    private val config: JohtoConfig get() = Johto.config

    fun latencyTicks(player: ServerPlayer): Int = (minOf(player.connection.latency(), config.maxLatencyMs).coerceAtLeast(0) + 25) / 50

    fun send(
        player: ServerPlayer, run: Run, event: Int, kind: Int = 0, index: Int = 0, value: Int = 0, tick: Long = 0L, tick2: Long = 0L,
        points: List<Vec3> = emptyList(), ints: IntArray = IntArray(0), origin: Vec3 = run.centre,
    ) {
        player.sendPacket(S2CJohtoTrialPacket(event, run.session, run.step, run.beast.ordinal, run.round, kind, index, value, tick, tick2, origin, points, ints))
    }

    /** Ground under a flat point near a height (the trial's elements stand on it). */
    fun ground(level: ServerLevel, x: Double, y: Double, z: Double): Vec3 = Vec3(x, CinematicPlaces.ground(level, x, y, z), z)

    /** A random point of the zone at most `fraction` of its reach from the centre, loaded, on the ground. */
    fun randomPoint(level: ServerLevel, run: Run, fraction: Double, near: Vec3? = null, min: Double = 0.0, max: Double = 1e9): Vec3 {
        val c = run.centre
        val reach = run.zone.reach * fraction
        repeat(24) {
            val a = run.random.nextDouble() * 2.0 * PI
            val r = reach * kotlin.math.sqrt(run.random.nextDouble())
            val x = c.x + cos(a) * r
            val z = c.z + sin(a) * r
            if (!run.zone.contains(x, c.y, z)) return@repeat
            if (!level.isLoaded(BlockPos.containing(x, c.y, z))) return@repeat
            if (near != null) {
                val d = JohtoTrialMath.flat(x, z, near.x, near.z)
                if (d < min || d > max) return@repeat
            }
            return ground(level, x, near?.y ?: c.y, z)
        }
        return ground(level, c.x, c.y, c.z)
    }

    fun start(player: ServerPlayer, beast: JohtoBeast, zone: JohtoPlaces.Zone, round: Int, test: Boolean): Run {
        runs.remove(player.uuid)
        val level = player.serverLevel()
        val now = level.gameTime
        val run = Run(player.uuid, player.scoreboardName, beast, zone.copy(), test, sessions++, round.coerceIn(1, 3), level.dimension(), now, level.random.nextLong())
        run.centre = ground(level, zone.centreX, zone.centreY, zone.centreZ)
        run.logic = when (beast) {
            JohtoBeast.RAIKOU -> RaikouTrial(run)
            JohtoBeast.ENTEI -> EnteiTrial(run)
            JohtoBeast.SUICUNE -> SuicuneTrial(run)
        }
        runs[player.uuid] = run
        send(player, run, S2CJohtoTrialPacket.START, value = run.logic.endurance, ints = intArrayOf((run.logic.radius * 10.0).toInt()))
        startRound(level, player, run, now)
        log("trial {} of {} started at round {}{}", beast.id, player.scoreboardName, run.round, if (test) " (test)" else "")
        return run
    }

    private fun startRound(level: ServerLevel, player: ServerPlayer, run: Run, now: Long) {
        run.step++
        run.deadline = now + run.logic.roundSeconds * 20L
        run.logic.round(level, player, now)
        player.displayClientMessage(
            Component.translatable("generations_core.johto.trial.${run.beast.id}.round${run.round}").withStyle(ChatFormatting.GOLD), true,
        )
    }

    /** A round won: the next one, or the trial won after the third. */
    fun roundWon(level: ServerLevel, player: ServerPlayer, run: Run) {
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f)
        if (run.round >= JohtoStateMachine.ROUNDS) {
            win(player, run)
            return
        }
        run.round++
        Johto.trialRound(run, player)
        startRound(level, player, run, level.gameTime)
    }

    private fun win(player: ServerPlayer, run: Run) {
        runs.remove(run.player)
        send(player, run, S2CJohtoTrialPacket.END, value = 0)
        log("trial {} of {} won", run.beast.id, run.name)
        Johto.trialWon(run, player)
    }

    fun fail(run: Run, player: ServerPlayer?, reason: String) {
        if (runs[run.player] !== run) return
        runs.remove(run.player)
        player?.let { send(it, run, S2CJohtoTrialPacket.END, value = 1) }
        log("trial {} of {} failed at round {} ({})", run.beast.id, run.name, run.round, reason)
        Johto.trialFailed(run, player, reason)
    }

    /** Stopped by a command or the server stopping: like a failure, but quietly for the server's stop. */
    fun stop(player: UUID, reason: String) {
        val run = runs[player] ?: return
        val server = com.cobblemon.mod.common.util.server()
        fail(run, server?.playerList?.getPlayer(player), reason)
    }

    fun clear() = runs.clear()

    fun action(player: ServerPlayer, session: Int, step: Int, target: Int) {
        val run = runs[player.uuid] ?: return
        if (run.session != session || run.step != step) return
        val level = player.serverLevel()
        if (level.dimension() != run.dimension) return
        run.logic.action(level, player, target, level.gameTime)
    }

    fun tick(server: MinecraftServer, ticks: Long) {
        if (runs.isEmpty() || ticks % 2L != 0L) return
        for (run in runs.values.toList()) {
            val player = server.playerList.getPlayer(run.player)
            if (player == null) {
                fail(run, null, "disconnected")
                continue
            }
            if (!player.isAlive) {
                fail(run, player, "died")
                continue
            }
            if (player.level().dimension() != run.dimension) {
                fail(run, player, "left")
                continue
            }
            val level = player.serverLevel()
            val now = level.gameTime
            if (now - run.started > (config.trialMaxMinutes * 1200.0).toLong() || now > run.deadline) {
                fail(run, player, "time")
                continue
            }
            if (!run.logic.inside(player)) {
                if (run.outsideSince < 0) run.outsideSince = now
                val left = config.outsideSeconds - ((now - run.outsideSince) / 20L).toInt()
                if (left <= 0) {
                    fail(run, player, "outside")
                    continue
                }
                if (left != run.lastWarn) {
                    run.lastWarn = left
                    send(player, run, S2CJohtoTrialPacket.WARN, value = left)
                }
            } else if (run.outsideSince >= 0) {
                run.outsideSince = -1
                run.lastWarn = -1
                send(player, run, S2CJohtoTrialPacket.WARN, value = -1)
            }
            try {
                run.logic.tick(level, player, now)
            } catch (e: Exception) {
                GenerationsCore.LOGGER.error("Legend of Johto: the trial of {} stopped", run.name, e)
                fail(run, player, "error")
            }
        }
    }

    fun log(message: String, vararg args: Any?) {
        if (config.log) GenerationsCore.LOGGER.info("Legend of Johto: $message", *args)
    }
}

/**
 * Raikou — the Thunder Race (§ 7): electric tracks to follow to their end, then three flashing
 * moves along a line the player can read (a flash and a silhouette where it will land, a thunder
 * from there), and an interception on foot at its last point: there within the window (latency
 * added). Three rounds; a missed interception starts the moves again (missesAllowed in all).
 */
class RaikouTrial(run: JohtoTrials.Run) : JohtoTrials.Logic(run) {
    private val c get() = Johto.config.raikou
    override val roundSeconds: Int get() = c.roundSeconds

    private var tracks: List<Vec3> = emptyList()
    private var index = 0
    private var blinking = false
    private var blinks: List<Vec3> = emptyList()
    private var arrive = 0L
    private var windowEnd = 0L
    private var nextBlinks = -1L
    private var misses = 0

    override fun round(level: ServerLevel, player: ServerPlayer, now: Long) {
        blinking = false
        nextBlinks = -1L
        index = 0
        val list = ArrayList<Vec3>()
        var at = player.position()
        val count = c.tracks + run.round - 1
        repeat(count) {
            val next = JohtoTrials.randomPoint(level, run, 0.85, at, 7.0, 13.0)
            list += next
            at = next
        }
        tracks = list
        JohtoTrials.send(player, run, S2CJohtoTrialPacket.ROUND, tick = run.deadline, points = tracks)
    }

    private fun startBlinks(level: ServerLevel, player: ServerPlayer, now: Long) {
        run.step++
        blinking = true
        nextBlinks = -1L
        // A straight line a player can read: away from them, then three steps of the same size.
        val base = run.random.nextDouble() * 2.0 * PI
        val step = (6.0 + run.round) * (if (run.random.nextBoolean()) 1.0 else -1.0)
        val turn = base + (PI / 2.0) * (0.35 + 0.3 * run.random.nextDouble()) * (if (step > 0) 1 else -1)
        val p = player.position()
        var x = p.x + cos(base) * 9.0
        var z = p.z + sin(base) * 9.0
        val list = ArrayList<Vec3>()
        for (k in 0 until 3) {
            if (!run.zone.contains(x, p.y, z)) {
                // Back toward the centre rather than out of the zone.
                x = (x + run.centre.x) / 2.0
                z = (z + run.centre.z) / 2.0
            }
            list += JohtoTrials.ground(level, x, if (k == 0) p.y else list[k - 1].y, z)
            x += cos(turn) * abs(step)
            z += sin(turn) * abs(step)
        }
        blinks = list
        val warn = (c.warnSeconds * 20.0).toLong()
        val start = now + 8L
        val a0 = start + warn
        val a1 = a0 + 16L + warn
        val a2 = a1 + 16L + warn
        arrive = a2
        windowEnd = a2 + (c.interceptSeconds * 20.0).toLong() + JohtoTrials.latencyTicks(player)
        JohtoTrials.send(
            player, run, S2CJohtoTrialPacket.CUE, kind = 1, tick = start, tick2 = windowEnd, points = blinks,
            ints = intArrayOf((a0 - start).toInt(), (a1 - start).toInt(), (a2 - start).toInt(), warn.toInt()),
        )
    }

    override fun tick(level: ServerLevel, player: ServerPlayer, now: Long) {
        if (!blinking) {
            if (nextBlinks >= 0) {
                if (now >= nextBlinks) startBlinks(level, player, now)
                return
            }
            val target = tracks.getOrNull(index) ?: return
            if (JohtoTrialMath.flat(player.x, player.z, target.x, target.z) <= c.trackRadius && abs(player.y - target.y) < 4.0) {
                index++
                level.playSound(null, target.x, target.y, target.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.6f)
                JohtoTrials.send(player, run, S2CJohtoTrialPacket.PROGRESS, index = index)
                if (index >= tracks.size) nextBlinks = now + 20L
            }
            return
        }
        val last = blinks.last()
        if (now >= arrive - 10L && now <= windowEnd &&
            JohtoTrialMath.flat(player.x, player.z, last.x, last.z) <= c.interceptRadius && abs(player.y - last.y) < 3.5
        ) {
            JohtoTrials.send(player, run, S2CJohtoTrialPacket.RESULT, kind = S2CJohtoTrialPacket.RESULT_OK, value = run.round)
            blinking = false
            JohtoTrials.roundWon(level, player, run)
            return
        }
        if (now > windowEnd) {
            misses++
            blinking = false
            JohtoTrials.send(player, run, S2CJohtoTrialPacket.RESULT, kind = S2CJohtoTrialPacket.RESULT_MISS, value = misses)
            if (misses >= c.missesAllowed) {
                JohtoTrials.fail(run, player, "missed")
                return
            }
            nextBlinks = now + 30L
        }
    }
}

/**
 * Entei — the Judgement of Flames (§ 7): a burning arena around Entei; attacks announced before
 * they burn (a line of fire, volcanic circles, a shock wave with a gap — or jumped over, a cone of
 * breath), quicker each round; an endurance of the trial's own (no damage, no death); a seal a
 * round, held for a few seconds. Touches are decided here, on two samples (latency allowed for).
 */
class EnteiTrial(run: JohtoTrials.Run) : JohtoTrials.Logic(run) {
    private val c get() = Johto.config.entei
    override val roundSeconds: Int get() = c.roundSeconds
    override val radius: Double get() = minOf(c.arenaRadius, run.zone.reach.coerceAtLeast(7.0))
    override val endurance: Int get() = c.endurance

    private class Attack(val kind: Int, val points: DoubleArray, val active: Long, val end: Long) {
        var streak = 0
        var hit = false
    }

    private val attacks = ArrayList<Attack>()
    private var health = c.endurance
    private var seal: Vec3 = Vec3.ZERO
    private var sealTicks = 0
    private var lastSent = -1
    private var nextAttack = 0L

    override fun inside(player: ServerPlayer): Boolean =
        JohtoTrialMath.flat(player.x, player.z, run.centre.x, run.centre.z) <= radius + 1.5 && abs(player.y - run.centre.y) < 12.0

    override fun round(level: ServerLevel, player: ServerPlayer, now: Long) {
        attacks.clear()
        sealTicks = 0
        lastSent = -1
        // The seal: in the arena, away from its centre (Entei) and from the player.
        var best = run.centre
        repeat(16) {
            val a = run.random.nextDouble() * 2.0 * PI
            val r = 4.0 + (radius - 6.0).coerceAtLeast(1.0) * run.random.nextDouble()
            val x = run.centre.x + cos(a) * r
            val z = run.centre.z + sin(a) * r
            if (JohtoTrialMath.flat(x, z, player.x, player.z) >= 5.0) {
                best = JohtoTrials.ground(level, x, run.centre.y, z)
                return@repeat
            }
        }
        seal = best
        nextAttack = now + 50L
        JohtoTrials.send(player, run, S2CJohtoTrialPacket.ROUND, value = health, tick = run.deadline, points = listOf(seal))
    }

    private fun attack(player: ServerPlayer, now: Long) {
        val kinds = when (run.round) {
            1 -> intArrayOf(JohtoTrialMath.LINE, JohtoTrialMath.CIRCLES)
            2 -> intArrayOf(JohtoTrialMath.LINE, JohtoTrialMath.CIRCLES, JohtoTrialMath.WAVE)
            else -> intArrayOf(JohtoTrialMath.LINE, JohtoTrialMath.CIRCLES, JohtoTrialMath.WAVE, JohtoTrialMath.CONE)
        }
        val kind = kinds[run.random.nextInt(kinds.size)]
        val cx = run.centre.x
        val cz = run.centre.z
        val p: DoubleArray = when (kind) {
            JohtoTrialMath.LINE -> {
                val a = run.random.nextDouble() * PI
                val off = (run.random.nextDouble() - 0.5) * 2.0
                val nx = -sin(a) * off
                val nz = cos(a) * off
                val l = radius * 1.2
                doubleArrayOf(player.x + nx - cos(a) * l, player.z + nz - sin(a) * l, player.x + nx + cos(a) * l, player.z + nz + sin(a) * l)
            }
            JohtoTrialMath.CIRCLES -> {
                val n = 2 + run.round
                val out = DoubleArray(n * 2)
                out[0] = player.x + (run.random.nextDouble() - 0.5) * 1.5
                out[1] = player.z + (run.random.nextDouble() - 0.5) * 1.5
                for (i in 1 until n) {
                    val a = run.random.nextDouble() * 2.0 * PI
                    val r = radius * kotlin.math.sqrt(run.random.nextDouble())
                    out[i * 2] = cx + cos(a) * r
                    out[i * 2 + 1] = cz + sin(a) * r
                }
                out
            }
            JohtoTrialMath.WAVE -> {
                val a = run.random.nextDouble() * 2.0 * PI
                doubleArrayOf(cx, cz, cx + cos(a), cz + sin(a))
            }
            else -> doubleArrayOf(cx, cz, player.x, player.z)
        }
        val warn = (c.warnSeconds * 20.0 * (1.0 - 0.1 * (run.round - 1))).toLong().coerceAtLeast(12L)
        val active = now + warn
        val end = active + JohtoTrialMath.burnTicks(kind)
        attacks += Attack(kind, p, active, end)
        val points = ArrayList<Vec3>()
        var i = 0
        while (i + 1 < p.size) {
            points += Vec3(p[i], run.centre.y, p[i + 1])
            i += 2
        }
        JohtoTrials.send(player, run, S2CJohtoTrialPacket.CUE, kind = kind, tick = now, tick2 = active, points = points, ints = intArrayOf((end - active).toInt()))
        nextAttack = now + (c.attackSeconds * 20.0 * (1.0 - 0.12 * (run.round - 1))).toLong().coerceAtLeast(16L)
    }

    override fun tick(level: ServerLevel, player: ServerPlayer, now: Long) {
        if (now >= nextAttack) attack(player, now)
        val grace = minOf(JohtoTrials.latencyTicks(player) / 2, 6)
        val iterator = attacks.iterator()
        while (iterator.hasNext()) {
            val a = iterator.next()
            if (now > a.end) {
                iterator.remove()
                continue
            }
            if (a.hit) continue
            val airborne = !player.onGround() && player.y - run.centre.y > 0.6
            if (JohtoTrialMath.hits(a.kind, a.points, a.active + grace, a.end, radius, now, player.x, player.z, airborne)) {
                a.streak++
                if (a.streak >= 2) {
                    a.hit = true
                    health -= c.hitDamage
                    level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 0.8f)
                    JohtoTrials.send(player, run, S2CJohtoTrialPacket.HIT, value = health.coerceAtLeast(0))
                    if (health <= 0) {
                        JohtoTrials.fail(run, player, "burned")
                        return
                    }
                }
            } else a.streak = 0
        }
        val need = (c.sealSeconds * 20.0).toInt()
        if (JohtoTrialMath.flat(player.x, player.z, seal.x, seal.z) <= 2.2 && abs(player.y - seal.y) < 3.0) sealTicks += 2 else sealTicks = (sealTicks - 1).coerceAtLeast(0)
        val permille = (sealTicks * 1000 / need).coerceIn(0, 1000)
        if (permille / 50 != lastSent / 50) {
            lastSent = permille
            JohtoTrials.send(player, run, S2CJohtoTrialPacket.PROGRESS, index = permille)
        }
        if (sealTicks >= need) {
            JohtoTrials.send(player, run, S2CJohtoTrialPacket.RESULT, kind = S2CJohtoTrialPacket.RESULT_OK, value = run.round)
            level.playSound(null, seal.x, seal.y, seal.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.2f)
            JohtoTrials.roundWon(level, player, run)
        }
    }
}

/**
 * Suicune — the Waters of Rebirth (§ 7): three corrupted sources (near water when there is some;
 * the water itself is never changed), found by their signs; at each, a sequence of notes to play
 * back on its orbs, a hint more after each error; after the third, the harmony: a click when the
 * three rings meet (± the window and half the latency).
 */
class SuicuneTrial(run: JohtoTrials.Run) : JohtoTrials.Logic(run) {
    private val c get() = Johto.config.suicune
    override val roundSeconds: Int get() = c.roundSeconds

    private enum class Phase { FIND, SEQUENCE, HARMONY }

    private var sources: List<Vec3> = emptyList()
    private var phase = Phase.FIND
    private var source: Vec3 = Vec3.ZERO
    private var orbs: List<Vec3> = emptyList()
    private var notes = IntArray(0)
    private var progress = 0
    private var errors = 0
    private var hint = 0
    private var playEnd = 0L
    private var replayAt = -1L
    private var harmonyStart = 0L
    private var nextAlign = 0
    private var hits = 0
    private var misses = 0

    private fun water(level: ServerLevel, p: Vec3): Int {
        var n = 0
        val bx = p.x.toInt()
        val by = p.y.toInt()
        val bz = p.z.toInt()
        for (dx in -3..3) for (dz in -3..3) for (dy in -1..1) {
            if (level.getFluidState(BlockPos(bx + dx, by + dy, bz + dz)).`is`(FluidTags.WATER)) n++
        }
        return n
    }

    private fun chooseSources(level: ServerLevel) {
        val candidates = ArrayList<Pair<Vec3, Int>>()
        repeat(28) {
            val p = JohtoTrials.randomPoint(level, run, 0.85)
            candidates += p to water(level, p)
        }
        candidates.sortByDescending { it.second }
        val chosen = ArrayList<Vec3>()
        for (spacing in doubleArrayOf(12.0, 8.0, 4.0, 0.0)) {
            for ((p, _) in candidates) {
                if (chosen.size >= 3) break
                if (chosen.all { JohtoTrialMath.flat(it.x, it.z, p.x, p.z) >= spacing }) chosen += p
            }
            if (chosen.size >= 3) break
        }
        while (chosen.size < 3) chosen += run.centre
        sources = chosen
    }

    override fun round(level: ServerLevel, player: ServerPlayer, now: Long) {
        if (sources.isEmpty()) chooseSources(level)
        phase = Phase.FIND
        source = sources[(run.round - 1).coerceIn(0, 2)]
        errors = 0
        hint = 0
        replayAt = -1L
        JohtoTrials.send(player, run, S2CJohtoTrialPacket.ROUND, tick = run.deadline, points = listOf(source))
    }

    private fun play(player: ServerPlayer, now: Long) {
        run.step++
        progress = 0
        val start = now + 16L
        val each = if (hint >= 1) 18L else 13L
        playEnd = start + each * notes.size
        JohtoTrials.send(player, run, S2CJohtoTrialPacket.CUE, kind = 0, value = hint, tick = start, tick2 = playEnd, points = orbs, ints = notes)
    }

    override fun tick(level: ServerLevel, player: ServerPlayer, now: Long) {
        when (phase) {
            Phase.FIND -> if (JohtoTrialMath.flat(player.x, player.z, source.x, source.z) <= c.findRadius && abs(player.y - source.y) < 4.0) {
                phase = Phase.SEQUENCE
                JohtoTrials.send(player, run, S2CJohtoTrialPacket.RESULT, kind = S2CJohtoTrialPacket.RESULT_FOUND, value = run.round)
                level.playSound(null, source.x, source.y, source.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.8f)
                orbs = List(5) { k ->
                    val a = k * 2.0 * PI / 5.0 + PI / 2.0
                    Vec3(source.x + cos(a) * 2.4, source.y + 1.3, source.z + sin(a) * 2.4)
                }
                val length = (c.notesMin + run.round - 1).coerceAtMost(c.notesMax)
                notes = IntArray(length) { run.random.nextInt(5) }
                play(player, now)
            }
            Phase.SEQUENCE -> if (replayAt in 0..now) {
                replayAt = -1L
                play(player, now)
            }
            Phase.HARMONY -> {}
        }
    }

    override fun action(level: ServerLevel, player: ServerPlayer, target: Int, now: Long) {
        when (phase) {
            Phase.SEQUENCE -> {
                if (now < playEnd || replayAt >= 0 || target !in 0..4) return
                if (target == notes[progress]) {
                    progress++
                    JohtoTrials.send(player, run, S2CJohtoTrialPacket.PROGRESS, index = progress)
                    if (progress < notes.size) return
                    JohtoTrials.send(player, run, S2CJohtoTrialPacket.RESULT, kind = S2CJohtoTrialPacket.RESULT_PURIFIED, value = run.round)
                    level.playSound(null, source.x, source.y, source.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.5f)
                    if (run.round < JohtoStateMachine.ROUNDS) {
                        JohtoTrials.roundWon(level, player, run)
                    } else {
                        // The harmony at the last source.
                        phase = Phase.HARMONY
                        run.step++
                        harmonyStart = now + 20L
                        nextAlign = 0
                        hits = 0
                        misses = 0
                        JohtoTrials.send(player, run, S2CJohtoTrialPacket.CUE, kind = 2, tick = harmonyStart, points = listOf(source), ints = intArrayOf(c.harmonyHits))
                    }
                } else {
                    errors++
                    hint = (hint + 1).coerceAtMost(3)
                    JohtoTrials.send(player, run, S2CJohtoTrialPacket.RESULT, kind = S2CJohtoTrialPacket.RESULT_ERROR, value = errors, index = hint)
                    if (errors > c.errorsAllowed) {
                        JohtoTrials.fail(run, player, "discord")
                        return
                    }
                    replayAt = now + 30L
                }
            }
            Phase.HARMONY -> {
                if (target != C2SJohtoTrialActionPacket.HARMONY) return
                val latency = minOf(player.connection.latency(), Johto.config.maxLatencyMs)
                val window = c.harmonyWindowMs + 100
                // The meetings gone by without a click are left behind.
                val sent = now - latency / 100L
                while (JohtoTrialMath.harmonyTick(harmonyStart, nextAlign) + window / 50 + 1 < sent) nextAlign++
                var matched = -1
                for (k in nextAlign..nextAlign + 1) {
                    if (JohtoTrialMath.onTime(now, latency, JohtoTrialMath.harmonyTick(harmonyStart, k), window)) {
                        matched = k
                        break
                    }
                }
                if (matched >= 0) {
                    hits++
                    nextAlign = matched + 1
                    JohtoTrials.send(player, run, S2CJohtoTrialPacket.RESULT, kind = S2CJohtoTrialPacket.RESULT_HARMONY, value = hits)
                    if (hits >= c.harmonyHits) JohtoTrials.roundWon(level, player, run)
                } else {
                    misses++
                    JohtoTrials.send(player, run, S2CJohtoTrialPacket.RESULT, kind = S2CJohtoTrialPacket.RESULT_DISCORD, value = misses)
                    if (misses > c.harmonyMisses) JohtoTrials.fail(run, player, "discord")
                }
            }
            Phase.FIND -> {}
        }
    }

    @Suppress("unused")
    private fun angle(a: Vec3, b: Vec3): Double = atan2(b.z - a.z, b.x - a.x)
}
