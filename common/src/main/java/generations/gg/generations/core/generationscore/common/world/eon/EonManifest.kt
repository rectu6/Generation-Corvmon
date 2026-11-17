package generations.gg.generations.core.generationscore.common.world.eon

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.trade.TradeManager
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.GenerationsNetwork.sendPacket
import generations.gg.generations.core.generationscore.common.network.packets.eon.S2CEonPacket
import generations.gg.generations.core.generationscore.common.world.cinematic.LegendaryCinematics
import generations.gg.generations.core.generationscore.common.world.hooh.HoOhPassage
import generations.gg.generations.core.generationscore.common.world.hooh.HoOhPlaces
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems
import generations.gg.generations.core.generationscore.common.world.johto.JohtoTrials
import generations.gg.generations.core.generationscore.common.world.mew.MewGame
import generations.gg.generations.core.generationscore.common.world.mew.MewMath
import generations.gg.generations.core.generationscore.common.world.mew.MewReward
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpSessions
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.Random
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The Eon manifestations on the server (EON-PLAN.md §§ 3 to 5): the exploration counted, the
 * draws, the sessions, the pursuit's checks, the core and the fragment. Everything seen (the
 * signal, the anomaly, its trail and footprints, the illusions, the core) is drawn by the
 * player's client from a few packets; no entity, no block, no chunk loaded for it.
 *
 * Every connected player is sampled once every 30 s at a tick of their own (their UUID's slot).
 * A fragment won is saved before it is announced and given once (the session's number); one
 * manifestation by player, maxConcurrent on the server.
 */
object EonManifest {
    var config = EonConfig().also { it.validate() }
        private set
    var rules = config.rules()
        private set

    private const val CHECK_SECONDS = 30
    private const val SESSION_EVERY = 5
    private const val STABILIZE_TICKS = 50L

    enum class Mode { FULL, PURSUIT }

    class Session(val id: Long, val player: UUID, val dimension: ResourceKey<Level>, val mode: Mode, val test: Boolean) {
        val machine = EonMachine()
        val path = EonPath()
        var yaw = 0.0
        var lastGround = 0
        var started = 0L
        var phaseStart = 0L
        var pursuitStart = 0L
        var pursuitTicks = 0L
        var lostTicks = 0L
        var retryAt = 0L
        var stuckSince = -1L
        var finishEarly = false
        var speed = 0.0
        var band = 2
        var bandCount = 0
        var dashUntil = 0L
        val dashTimes = ArrayList<Long>()
        val illusionTimes = ArrayList<Long>()
        var core: Vec3? = null
        var coreUntil = 0L
        var stabilizeAt = 0L
        var lastClick = -100L
        var lastX = 0.0
        var lastZ = 0.0
        var travelled = 0.0
        var biome = ""
    }

    /** Where a player was at their last sample, and the blocks really travelled since (the movement checks). */
    private class Travel(var x: Double, var z: Double, var dimension: String) {
        var travelled = 0.0
    }

    private val sessions = LinkedHashMap<UUID, Session>()
    private val activity = HashMap<UUID, MewMath.Activity>()
    private val travel = HashMap<UUID, Travel>()
    private val random = Random()
    private var ticks = 0L
    private var counter = 0L
    private val tmp = DoubleArray(3)

    fun sessions(): Collection<Session> = sessions.values

    fun session(player: UUID): Session? = sessions[player]

    fun isRunning(player: UUID): Boolean = sessions.containsKey(player)

    @JvmStatic
    fun legacyBiomeFragments(): Boolean = config.legacyBiomeFragments

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) {
            sessions.clear()
            activity.clear()
            travel.clear()
            ticks = 0L
            reload()
        }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) {
            sessions.clear()
            activity.clear()
            travel.clear()
        }
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe(Priority.NORMAL) { event -> deliverPending(event.player) }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { event ->
            val player = event.player
            activity.remove(player.uuid)
            travel.remove(player.uuid)
            sessions.remove(player.uuid)?.let { s ->
                // Gone before the end: no fragment (one won already stays saved for the next connection).
                if (!s.test && s.machine.phase != EonMachine.Phase.STABILIZE) {
                    EonData.getOrCreate(player.server, player.uuid).failures++
                    EonData.changed(player.server)
                }
                log("manifestation {} of {} ended by a disconnection", s.id, player.scoreboardName)
            }
        }
        PlatformEvents.PLAYER_DEATH.subscribe(Priority.LOWEST) { event ->
            if (event.isCanceled) return@subscribe
            val player = event.player
            EonData.get(player.server, player.uuid)?.let { it.presence = 0L; EonData.changed(player.server) }
            sessions[player.uuid]?.let { fail(it, player, "died") }
        }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { tick(it.server) }
    }

    fun reload() {
        config = EonConfig.load()
        rules = config.rules()
    }

    private fun log(message: String, vararg args: Any?) {
        if (config.log) GenerationsCore.LOGGER.info("Eon manifestation: $message", *args)
    }

    /** The blocks a player really travels (ServerPlayerMixin's movement checks): the teleports are told from them. */
    @JvmStatic
    fun moved(player: ServerPlayer, distance: Float) {
        if (!distance.isFinite() || distance <= 0f) return
        travel[player.uuid]?.let { it.travelled += distance }
        sessions[player.uuid]?.let { it.travelled += distance }
    }

    // ---------------------------------------------------------------- the exploration and the draws

    private fun tick(server: MinecraftServer) {
        ticks++
        if (sessions.isNotEmpty() && ticks % SESSION_EVERY == 0L) tickSessions(server)
        if (!config.enabled) return
        val period = CHECK_SECONDS * 20
        val slot = Math.floorMod(ticks, period.toLong()).toInt()
        for (player in server.playerList.players) {
            if (MewMath.slot(player.uuid.mostSignificantBits, player.uuid.leastSignificantBits, period) != slot) continue
            try {
                check(server, player)
            } catch (e: Exception) {
                GenerationsCore.LOGGER.error("Eon manifestation: check of {} failed", player.scoreboardName, e)
            }
        }
    }

    private fun battleKey(player: ServerPlayer): Long {
        val battle = BattleRegistry.getBattleByParticipatingPlayer(player) ?: return 0L
        return (battle.battleId.leastSignificantBits * 31L + battle.turn) or 1L
    }

    private fun sample(player: ServerPlayer): Long {
        val nowMs = Util.getMillis()
        val existing = activity[player.uuid]
        if (existing == null) {
            val created = MewMath.Activity(nowMs, player.x, player.y, player.z, player.yRot, player.xRot, ticks)
            created.lastBattleKey = battleKey(player)
            activity[player.uuid] = created
            return 0L
        }
        val elapsed = ticks - existing.lastCheckTick
        existing.lastCheckTick = ticks
        existing.observe(nowMs, player.x, player.y, player.z, player.yRot, player.xRot, battleKey(player), config.afk.minMoveBlocks)
        return elapsed
    }

    fun afk(player: ServerPlayer): MewMath.AfkStatus {
        val afk = config.afk
        if (!afk.enabled) return MewMath.AfkStatus.ACTIVE
        val act = activity[player.uuid] ?: return MewMath.AfkStatus.ACTIVE
        val exempt = (afk.fishingExempt && player.fishing != null) || (afk.ridingExempt && player.isPassenger)
        return MewMath.afk(Util.getMillis(), player.lastActionTime, act, exempt, afk.afkSeconds, afk.stillMinutes)
    }

    private fun dimensionId(player: ServerPlayer): String = player.level().dimension().location().toString()

    private fun dimensionAllowed(player: ServerPlayer): Boolean {
        val id = dimensionId(player)
        return config.dimensions.any { it.trim() == id }
    }

    fun biomeId(level: ServerLevel, pos: BlockPos): String = level.getBiome(pos).unwrapKey().map { it.location().toString() }.orElse("?")

    fun biomeAllowed(level: ServerLevel, pos: BlockPos): Boolean {
        if (config.biomes.isNotEmpty() && !HoOhPlaces.biomeExcluded(level, pos, config.biomes)) return false
        return !HoOhPlaces.biomeExcluded(level, pos, config.excludedBiomes)
    }

    /** Busy elsewhere: a manifestation, a battle, a scene, a journey, Mew, Ho-Oh, a trial, a trade, asleep. */
    private fun busy(player: ServerPlayer): String? {
        val id = player.uuid
        return when {
            sessions.containsKey(id) -> "already"
            BattleRegistry.getBattleByParticipatingPlayer(player) != null -> "battle"
            LegendaryCinematics.isWatching(id) -> "cinematic"
            UltraWarpSessions.get(id) != null -> "journey"
            MewGame.isPlaying(id) || MewReward.isPending(id) -> "mew"
            HoOhPassage.session(id) != null -> "hooh"
            JohtoTrials.isRunning(id) -> "trial"
            TradeManager.getActiveTrade(id) != null || player.isSleeping -> "busy"
            else -> null
        }
    }

    /**
     * Why the exploration does not count for this player here and now (a translation key's end),
     * or null: it counts.
     */
    fun whyNot(player: ServerPlayer): String? {
        val level = player.serverLevel()
        val record = EonData.getOrCreate(player.server, player.uuid)
        return when {
            !config.enabled -> "disabled"
            !player.isAlive || player.isSpectator -> "busy"
            !dimensionAllowed(player) -> "dimension"
            afk(player) != MewMath.AfkStatus.ACTIVE -> "afk"
            !biomeAllowed(level, player.blockPosition()) -> "biome"
            EonMath.blocked(record, dimensionId(player), player.x, player.z, System.currentTimeMillis(), rules) -> "region"
            else -> busy(player)
        }
    }

    private fun check(server: MinecraftServer, player: ServerPlayer) {
        val elapsed = sample(player)
        val dimension = dimensionId(player)
        val t = travel[player.uuid]
        if (t == null) {
            travel[player.uuid] = Travel(player.x, player.z, dimension)
            return
        }
        val dx = player.x - t.x
        val dz = player.z - t.z
        val displacement = sqrt(dx * dx + dz * dz)
        val travelled = t.travelled
        val x0 = t.x
        val z0 = t.z
        val sameDimension = t.dimension == dimension
        t.x = player.x
        t.z = player.z
        t.dimension = dimension
        t.travelled = 0.0
        if (elapsed <= 0L) return
        val record = EonData.getOrCreate(server, player.uuid)
        if (!sameDimension) {
            record.presence = 0L
            EonData.changed(server)
            return
        }
        if (!player.isAlive || player.isSpectator || !dimensionAllowed(player)) return
        if (afk(player) != MewMath.AfkStatus.ACTIVE) {
            if (record.presence != 0L) {
                record.presence = 0L
                EonData.changed(server)
            }
            return
        }
        when (EonMath.move(displacement, travelled, rules)) {
            EonMath.Move.TELEPORT -> {
                record.presence = 0L
                EonData.changed(server)
                return
            }
            EonMath.Move.STILL -> return
            EonMath.Move.MOVED -> {}
        }
        if (busy(player) != null) return
        val level = player.serverLevel()
        val pos = player.blockPosition()
        if (!biomeAllowed(level, pos)) return
        if (EonMath.blocked(record, dimension, player.x, player.z, System.currentTimeMillis(), rules)) return
        val cells = ArrayList<Long>()
        EonMath.cellsAlong(x0, z0, player.x, player.z, cells)
        val credit = MewMath.credit(elapsed, CHECK_SECONDS)
        // Blocks travelled, at most 16 a second (a glide or a fast mount still counts as exploring).
        EonMath.count(record, credit, min(travelled, credit / 20.0 * 16.0), cells)
        EonData.changed(server)
        if (!EonMath.due(record, rules)) return
        if (record.owed) {
            // Won by a draw that found no place: started without another draw.
            if (sessions.size < config.maxConcurrent && start(player, Mode.FULL, test = false)) record.owed = false
            else record.counted = EonMath.threshold(record, rules) - 1200L
            return
        }
        val waiting = EonMath.waiting(record, rules)
        if (waiting != null) return
        if (sessions.size >= config.maxConcurrent) {
            log("draw of {} waits (server full: {})", player.scoreboardName, sessions.size)
            return
        }
        record.draws++
        val biome = biomeId(level, pos)
        val chance = EonMath.chance(record, biome, rules)
        val roll = random.nextDouble()
        EonMath.drawn(record)
        log("draw of {} in {}: {} against {}", player.scoreboardName, biome, "%.4f".format(roll), "%.4f".format(chance))
        if (roll >= chance) return
        if (!start(player, Mode.FULL, test = false)) {
            record.owed = true
            record.counted = EonMath.threshold(record, rules) - 1200L
            log("manifestation of {} waits: no place in sight", player.scoreboardName)
        }
    }

    /** A draw made now (the command), in the biome where the player is: the roll, the chance, won. */
    fun drawNow(player: ServerPlayer): Triple<Double, Double, Boolean> {
        val record = EonData.getOrCreate(player.server, player.uuid)
        val chance = EonMath.chance(record, biomeId(player.serverLevel(), player.blockPosition()), rules)
        val roll = random.nextDouble()
        record.draws++
        EonMath.drawn(record)
        EonData.changed(player.server)
        val won = roll < chance && start(player, Mode.FULL, test = false)
        return Triple(roll, chance, won)
    }

    // ---------------------------------------------------------------- sessions

    private fun send(player: ServerPlayer, s: Session, kind: Int, tick: Long, vararg data: Double) {
        player.sendPacket(S2CEonPacket(kind, s.id, tick, data))
    }

    private fun newId(): Long = System.currentTimeMillis() * 1000L + (counter++ % 1000L)

    /**
     * Starts a manifestation for this player (a draw, or a command). FULL: the signal, the
     * anomaly, the pursuit, the core; PURSUIT: the pursuit at once. A test gives nothing and
     * leaves the counters alone. False: busy, or no place in sight.
     */
    fun start(player: ServerPlayer, mode: Mode, test: Boolean): Boolean {
        if (sessions.containsKey(player.uuid)) return false
        if (!player.isAlive || player.isSpectator) return false
        val level = player.serverLevel()
        val m = config.manifestation
        val place = EonPlaces.start(level, player, m, random) ?: return false
        val now = level.gameTime
        val s = Session(newId(), player.uuid, level.dimension(), mode, test)
        s.started = now
        s.phaseStart = now
        s.yaw = place.yaw
        s.lastGround = place.ground
        s.lastX = player.x
        s.lastZ = player.z
        s.biome = biomeId(level, player.blockPosition())
        s.path.add(place.pos.x, place.pos.y, place.pos.z)
        repeat(3) { extend(level, s, null) }
        sessions[player.uuid] = s
        if (!test) {
            EonData.getOrCreate(player.server, player.uuid).manifestations++
            EonData.changed(player.server)
        }
        send(player, s, S2CEonPacket.START, now, place.pos.x, place.pos.y, place.pos.z, place.yaw, m.signalSeconds, if (test) 1.0 else 0.0, (s.id % 2L).toDouble())
        val points = DoubleArray(s.path.size * 3)
        for (i in 0 until s.path.size) {
            s.path.point(i, tmp)
            points[i * 3] = tmp[0]; points[i * 3 + 1] = tmp[1]; points[i * 3 + 2] = tmp[2]
        }
        send(player, s, S2CEonPacket.POINTS, now, *points)
        s.path.setMotion(now, 0.0, 0.0)
        send(player, s, S2CEonPacket.MOTION, now, 0.0, 0.0, 0.0)
        player.displayClientMessage(Component.translatable("generations_core.eon.signal").withStyle(ChatFormatting.LIGHT_PURPLE), false)
        if (mode == Mode.PURSUIT) {
            s.machine.toAnomaly()
            beginPursuit(s, player, now)
        }
        log("manifestation {} for {} ({}{}) at {}", s.id, player.scoreboardName, mode, if (test) ", test" else "", BlockPos.containing(place.pos))
        return true
    }

    /** One more point of the way (loaded chunks only); false when none could be found. */
    private fun extend(level: ServerLevel, s: Session, player: ServerPlayer?): Boolean {
        s.path.point(s.path.size - 1, tmp)
        val next = EonPlaces.next(level, Vec3(tmp[0], tmp[1], tmp[2]), s.lastGround, s.yaw, random) ?: return false
        s.path.add(next.pos.x, next.pos.y, next.pos.z)
        s.yaw = next.yaw
        s.lastGround = next.ground
        if (player != null) send(player, s, S2CEonPacket.POINTS, level.gameTime, next.pos.x, next.pos.y, next.pos.z)
        return true
    }

    private fun tickSessions(server: MinecraftServer) {
        val m = config.manifestation
        for (s in sessions.values.toList()) {
            val player = server.playerList.getPlayer(s.player)
            if (player == null) {
                sessions.remove(s.player)
                continue
            }
            if (!player.isAlive || player.level().dimension() != s.dimension) {
                fail(s, player, "left")
                continue
            }
            // A teleport: much more displacement than travel.
            val dx = player.x - s.lastX
            val dz = player.z - s.lastZ
            val displacement = sqrt(dx * dx + dz * dz)
            if (displacement > s.travelled + 10.0 && displacement > 16.0) {
                fail(s, player, "teleport")
                continue
            }
            s.lastX = player.x
            s.lastZ = player.z
            s.travelled = 0.0
            val level = player.serverLevel()
            val now = level.gameTime
            if (s.machine.phase != EonMachine.Phase.STABILIZE && now - s.started > (m.maxSeconds * 20.0).toLong()) {
                fail(s, player, "timeout")
                continue
            }
            try {
                when (s.machine.phase) {
                    EonMachine.Phase.SIGNAL -> if (now >= s.phaseStart + (m.signalSeconds * 20.0).toLong()) {
                        s.machine.toAnomaly()
                        s.phaseStart = now
                        send(player, s, S2CEonPacket.PHASE, now, 1.0)
                    }
                    EonMachine.Phase.ANOMALY -> if (looks(player, s, now) || now >= s.phaseStart + (m.gazeSeconds * 20.0).toLong()) beginPursuit(s, player, now)
                    EonMachine.Phase.PURSUIT -> pursue(s, player, level, now)
                    EonMachine.Phase.CORE -> atCore(s, player, now)
                    EonMachine.Phase.STABILIZE -> if (now >= s.stabilizeAt) deliver(s, player)
                    else -> sessions.remove(s.player)
                }
            } catch (e: Exception) {
                GenerationsCore.LOGGER.error("Eon manifestation {}: stopped", s.id, e)
                fail(s, player, "error")
            }
        }
    }

    /** The player has seen the anomaly (looked at it, or come near). */
    private fun looks(player: ServerPlayer, s: Session, now: Long): Boolean {
        s.path.position(now.toDouble(), tmp)
        val eye = player.eyePosition
        val tx = tmp[0] - eye.x
        val ty = tmp[1] - eye.y
        val tz = tmp[2] - eye.z
        val d = sqrt(tx * tx + ty * ty + tz * tz)
        if (d < 10.0) return true
        if (d > 48.0) return false
        val look = player.getViewVector(1f)
        return (look.x * tx + look.y * ty + look.z * tz) / d > 0.93
    }

    private fun beginPursuit(s: Session, player: ServerPlayer, now: Long) {
        if (!s.machine.toPursuit()) return
        val m = config.manifestation
        s.pursuitStart = now
        s.pursuitTicks = ((m.pursuitMinSeconds + random.nextDouble() * (m.pursuitMaxSeconds - m.pursuitMinSeconds)) * 20.0).toLong()
        s.dashTimes.clear()
        for (i in 0 until m.dashes) s.dashTimes += now + (s.pursuitTicks * (0.12 + 0.72 * (i + 0.2 + 0.6 * random.nextDouble()) / m.dashes)).toLong()
        s.illusionTimes.clear()
        for (i in 0 until m.illusions) s.illusionTimes += now + (s.pursuitTicks * (0.22 + 0.56 * (i + 0.5) / m.illusions + (random.nextDouble() - 0.5) * 0.08)).toLong()
        s.speed = m.speed
        s.band = 2
        val sNow = s.path.sAt(now.toDouble())
        s.path.setMotion(now, sNow, s.speed)
        send(player, s, S2CEonPacket.PHASE, now, 2.0)
        send(player, s, S2CEonPacket.MOTION, now, sNow, s.speed, 0.0)
        player.displayClientMessage(Component.translatable("generations_core.eon.follow").withStyle(ChatFormatting.LIGHT_PURPLE), false)
    }

    /** Distance from the player to a point, the height counting only beyond 4 blocks. */
    private fun reach(player: ServerPlayer, x: Double, y: Double, z: Double): Double {
        val dx = player.x - x
        val dz = player.z - z
        return sqrt(dx * dx + dz * dz) + max(0.0, kotlin.math.abs(player.y + 1.0 - y) - 4.0)
    }

    private fun pursue(s: Session, player: ServerPlayer, level: ServerLevel, now: Long) {
        val m = config.manifestation
        val f = s.path
        val sNow = f.sAt(now.toDouble())
        f.position(now.toDouble(), tmp)
        val d = reach(player, tmp[0], tmp[1], tmp[2])
        s.lostTicks = if (d > m.lostRadius) s.lostTicks + SESSION_EVERY else 0L
        if (s.lostTicks >= (m.lostSeconds * 20.0).toLong()) return fail(s, player, "lost")
        val finishing = s.finishEarly || now - s.pursuitStart >= s.pursuitTicks
        // More of the way ahead; a dead end for 4 s: the pursuit ends there (never an unfair failure).
        if (!finishing && f.settled - sNow < 24.0 && now >= s.retryAt) {
            if (extend(level, s, player)) s.stuckSince = -1L
            else {
                s.retryAt = now + 20L
                if (s.stuckSince < 0L) s.stuckSince = now
                if (now - s.stuckSince > 80L) s.finishEarly = true
            }
        }
        val atEnd = sNow >= f.settled - 0.3
        // The dashes, only when the player keeps up (dropped after 10 s of waiting).
        while (s.dashTimes.isNotEmpty() && now > s.dashTimes[0] + 200L) s.dashTimes.removeAt(0)
        if (now >= s.dashUntil && s.dashTimes.isNotEmpty() && now >= s.dashTimes[0] && d <= m.followRadius * 0.75 && !finishing) {
            s.dashTimes.removeAt(0)
            s.dashUntil = now + 50L
        }
        val dashing = now < s.dashUntil
        val band = when {
            atEnd || d > m.followRadius * 1.4 -> 0
            d > m.followRadius -> 1
            dashing -> 3
            else -> 2
        }
        if (band != s.band) {
            s.bandCount++
            if (s.bandCount >= 2 || band == 0 || band == 3) {
                s.band = band
                s.bandCount = 0
                val speed = when (band) {
                    0 -> 0.0
                    1 -> m.speed * 0.55
                    3 -> m.dashSpeed
                    else -> m.speed
                }
                if (speed != s.speed) {
                    s.speed = speed
                    f.setMotion(now, sNow, speed)
                    send(player, s, S2CEonPacket.MOTION, now, sNow, speed, if (band == 3) 1.0 else 0.0)
                }
            }
        } else s.bandCount = 0
        // The illusions: decoys split from it, only the real one leaves footprints.
        if (s.illusionTimes.isNotEmpty() && now >= s.illusionTimes[0] && !finishing && band >= 2) {
            s.illusionTimes.removeAt(0)
            val side = if (random.nextBoolean()) 1.0 else -1.0
            val a1 = side * (35.0 + random.nextDouble() * 25.0)
            val a2 = -side * (40.0 + random.nextDouble() * 25.0)
            send(player, s, S2CEonPacket.ILLUSION, now, 5.0, m.speed * 1.05, a1, a2)
        }
        if (finishing && atEnd) toCore(s, player, now)
    }

    /** The anomaly stops where it is: its core appears. */
    private fun toCore(s: Session, player: ServerPlayer, now: Long) {
        if (!s.machine.toCore()) return
        val m = config.manifestation
        val f = s.path
        val sNow = f.sAt(now.toDouble())
        f.at(sNow, tmp)
        val g = EonPlaces.ground(player.serverLevel(), tmp[0], tmp[2])
        val core = Vec3(tmp[0], if (g != null && kotlin.math.abs(g - (tmp[1] - EonPath.HEIGHT)) < 4.0) g + 1.3 else tmp[1] - 0.4, tmp[2])
        s.core = core
        s.coreUntil = now + (m.coreSeconds * 20.0).toLong()
        s.lostTicks = 0L
        s.speed = 0.0
        f.setMotion(now, sNow, 0.0)
        send(player, s, S2CEonPacket.MOTION, now, sNow, 0.0, 0.0)
        send(player, s, S2CEonPacket.CORE, now, core.x, core.y, core.z, m.coreSeconds)
        player.displayClientMessage(Component.translatable("generations_core.eon.core").withStyle(ChatFormatting.LIGHT_PURPLE), false)
    }

    private fun atCore(s: Session, player: ServerPlayer, now: Long) {
        val m = config.manifestation
        val core = s.core ?: return fail(s, player, "error")
        val d = reach(player, core.x, core.y, core.z)
        s.lostTicks = if (d > m.lostRadius) s.lostTicks + SESSION_EVERY else 0L
        if (s.lostTicks >= (m.lostSeconds * 20.0).toLong()) return fail(s, player, "lost")
        if (now > s.coreUntil) return fail(s, player, "expired")
    }

    /**
     * The player clicked the core (C2SEonInteractPacket): checked again here (their session,
     * the core's phase, the reach, the dimension; a click every 5 ticks at most). The fragment is
     * saved as waiting before anything is shown: it is given once, even after a crash.
     */
    fun interact(player: ServerPlayer, sessionId: Long) {
        val s = sessions[player.uuid] ?: return
        if (s.id != sessionId) return
        val level = player.serverLevel()
        val now = level.gameTime
        if (now - s.lastClick < 5L) return
        s.lastClick = now
        if (s.machine.phase != EonMachine.Phase.CORE) return
        val core = s.core ?: return
        if (player.level().dimension() != s.dimension) return
        if (player.eyePosition.distanceTo(core) > config.manifestation.interactRadius + 1.5) return
        if (!s.machine.claim()) return
        val server = player.server
        if (!s.test) {
            val record = EonData.getOrCreate(server, player.uuid)
            if (record.rewardedSession != s.id && record.pendingSession != s.id) {
                record.pending++
                record.pendingSession = s.id
                record.successes++
                EonMath.rewarded(record, dimensionId(player), core.x, core.z, s.biome, System.currentTimeMillis())
            }
            EonData.saveNow(server)
        }
        s.stabilizeAt = now + STABILIZE_TICKS
        send(player, s, S2CEonPacket.STABILIZE, now, STABILIZE_TICKS / 20.0)
        log("manifestation {} stabilised by {}", s.id, player.scoreboardName)
    }

    /** The condensation is over: the fragment given (once), the session ended. */
    private fun deliver(s: Session, player: ServerPlayer) {
        s.machine.finish()
        val server = player.server
        if (!s.test) {
            val record = EonData.getOrCreate(server, player.uuid)
            if (record.pending > 0 && record.pendingSession == s.id) {
                giveFragment(player)
                record.pending--
                record.rewardedSession = s.id
                record.fragments++
                EonData.saveNow(server)
            }
            player.displayClientMessage(Component.translatable("generations_core.eon.fragment").withStyle(ChatFormatting.LIGHT_PURPLE), false)
            log("manifestation {}: a fragment given to {}", s.id, player.scoreboardName)
        } else {
            player.displayClientMessage(Component.translatable("generations_core.eon.fragment_test").withStyle(ChatFormatting.GRAY), false)
        }
        send(player, s, S2CEonPacket.END, player.serverLevel().gameTime + 40L, 0.0)
        sessions.remove(s.player)
    }

    /** One Enigma Fragment into the inventory (at the feet, kept for this player, when it is full). */
    fun giveFragment(player: ServerPlayer) {
        val stack = ItemStack(GenerationsItems.ENIGMA_FRAGMENT.value())
        if (!player.inventory.add(stack)) player.drop(stack, false)?.let { it.setTarget(player.uuid) }
    }

    /** Fragments won but not received (a disconnection, a crash): given at the next connection. */
    private fun deliverPending(player: ServerPlayer) {
        val server = player.server
        val record = EonData.get(server, player.uuid) ?: return
        if (record.pending <= 0 || sessions.containsKey(player.uuid)) return
        val n = record.pending
        repeat(n) { giveFragment(player) }
        record.pending = 0
        record.rewardedSession = record.pendingSession
        record.fragments += n
        EonData.saveNow(server)
        player.displayClientMessage(Component.translatable("generations_core.eon.fragment_delivered", n).withStyle(ChatFormatting.LIGHT_PURPLE), false)
        log("{} waiting fragment(s) given to {}", n, player.scoreboardName)
    }

    private fun fail(s: Session, player: ServerPlayer, reason: String) {
        if (!s.machine.fail()) {
            // The reward was granted already: given now.
            if (s.machine.phase == EonMachine.Phase.STABILIZE) deliver(s, player) else sessions.remove(s.player)
            return
        }
        if (!s.test) {
            EonData.getOrCreate(player.server, player.uuid).failures++
            EonData.changed(player.server)
        }
        player.displayClientMessage(Component.translatable("generations_core.eon.failed.$reason").withStyle(ChatFormatting.GRAY), false)
        send(player, s, S2CEonPacket.END, player.serverLevel().gameTime, 1.0)
        sessions.remove(s.player)
        log("manifestation {} of {} failed ({})", s.id, player.scoreboardName, reason)
    }

    // ---------------------------------------------------------------- commands

    /** /generations eon succeed: the manifestation goes to its core now. */
    fun succeed(player: ServerPlayer): Boolean {
        val s = sessions[player.uuid] ?: return false
        if (s.machine.phase == EonMachine.Phase.CORE) return true
        if (s.machine.phase == EonMachine.Phase.SIGNAL) s.machine.toAnomaly()
        toCore(s, player, player.serverLevel().gameTime)
        return s.machine.phase == EonMachine.Phase.CORE
    }

    /** /generations eon fail: the manifestation fails now. */
    fun failNow(player: ServerPlayer): Boolean {
        val s = sessions[player.uuid] ?: return false
        fail(s, player, "command")
        return true
    }

    /** /generations eon cooldown clear: the regions and distances of the rewards forgotten (their biomes kept). */
    fun clearCooldown(server: MinecraftServer, player: UUID) {
        val record = EonData.getOrCreate(server, player)
        val old = record.rewards.map { EonReward(it.dimension, it.x, it.z, it.biome, 0L) }
        record.rewards.clear()
        record.rewards += old
        EonData.changed(server)
    }

    /** /generations eon reset: the counters and the memories (a waiting fragment is kept). */
    fun reset(server: MinecraftServer, player: UUID) {
        val record = EonData.getOrCreate(server, player)
        record.counted = 0L
        record.presence = 0L
        record.fresh = true
        record.owed = false
        record.intervalBlocks = 0.0
        record.cells.clear()
        record.previousCells.clear()
        record.rewards.clear()
        EonData.changed(server)
    }
}
