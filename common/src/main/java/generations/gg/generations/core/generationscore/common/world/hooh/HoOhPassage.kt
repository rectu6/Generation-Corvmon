package generations.gg.generations.core.generationscore.common.world.hooh

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.trade.TradeManager
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.GenerationsNetwork.sendPacket
import generations.gg.generations.core.generationscore.common.network.packets.hooh.S2CHoOhPacket
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import generations.gg.generations.core.generationscore.common.world.cinematic.LegendaryCinematics
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems
import generations.gg.generations.core.generationscore.common.world.mew.MewGame
import generations.gg.generations.core.generationscore.common.world.mew.MewMath
import generations.gg.generations.core.generationscore.common.world.mew.MewPlaces
import generations.gg.generations.core.generationscore.common.world.mew.MewReward
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpSessions
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.Random
import java.util.UUID
import kotlin.math.sqrt

/**
 * The Passage of the Rainbow on the server (HOOH-PLAN.md §§ 5 to 8): the rules, the draws, the
 * sessions, the checks of the pursuit and the feather. Everything seen (Ho-Oh, the rainbow, the
 * light, the feather's fall) is drawn by the player's client from a few packets; no entity, no
 * Pokémon, no chunk loaded for it.
 *
 * Every connected player is sampled once every 30 s at a tick of their own (their UUID's slot):
 * active play (the same AFK rule as Mew's hunt) is added up, a draw is made every
 * checkIntervalMinutes of it once allowed, and only when the player can see the passage (sky,
 * ground, nothing else on screen). A passage won is one Sacred Feather, saved before it is
 * announced and given once (the session's number).
 */
object HoOhPassage {
    var config = HoOhConfig().also { it.validate() }
        private set
    var rules = config.rules()
        private set

    private const val CHECK_SECONDS = 30
    private const val SESSION_EVERY = 5
    private const val FALL_TICKS = 140
    private const val WAIT_TICKS = 20L * 300
    private const val PICK_DISTANCE = 1.9
    private val HO_OH = ResourceLocation.fromNamespaceAndPath("cobblemon", "hooh")

    enum class Mode { FULL, FLIGHT, PURSUIT, FEATHER }

    enum class Phase { CINEMATIC, PURSUIT, FALL, WAIT }

    class Session(val id: Long, val player: UUID, val dimension: ResourceKey<Level>, val mode: Mode, val test: Boolean, val origin: Vec3) {
        var phase = Phase.PURSUIT
        val flight = HoOhFlight()
        var cinematic: UUID? = null
        var yaw = 0f
        var lastGround = 0
        var started = 0L
        var pursuitStart = 0L
        var pursuitTicks = 0L
        var lostTicks = 0L
        var retryAt = 0L
        var band = 2
        var bandCount = 0
        var speed = 0.0
        var featherAt: Vec3? = null
        var fallStart = 0L
        var waitStart = 0L
    }

    private val sessions = LinkedHashMap<UUID, Session>()
    private val activity = HashMap<UUID, MewMath.Activity>()
    private val random = Random()
    private var ticks = 0L
    private var counter = 0L
    private val tmp = DoubleArray(3)

    fun sessions(): Collection<Session> = sessions.values

    fun session(player: UUID): Session? = sessions[player]

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) {
            sessions.clear()
            activity.clear()
            ticks = 0L
            reload()
        }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) {
            sessions.clear()
            activity.clear()
        }
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe(Priority.NORMAL) { event -> deliverPending(event.player) }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { event ->
            activity.remove(event.player.uuid)
            sessions.remove(event.player.uuid)?.let { stopCinematic(it) }
        }
        PlatformEvents.PLAYER_DEATH.subscribe(Priority.LOWEST) { event ->
            if (!event.isCanceled) sessions[event.player.uuid]?.let { fail(it, event.player, "died") }
        }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { tick(it.server) }
    }

    fun reload() {
        config = HoOhConfig.load()
        rules = config.rules()
    }

    private fun log(message: String, vararg args: Any?) {
        if (config.log) GenerationsCore.LOGGER.info("Ho-Oh passage: $message", *args)
    }

    // ---------------------------------------------------------------- draws

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
                GenerationsCore.LOGGER.error("Ho-Oh passage: check of {} failed", player.scoreboardName, e)
            }
        }
    }

    private fun battleKey(player: ServerPlayer): Long {
        val battle = BattleRegistry.getBattleByParticipatingPlayer(player) ?: return 0L
        return (battle.battleId.leastSignificantBits * 31L + battle.turn) or 1L
    }

    private fun sample(player: ServerPlayer): Pair<MewMath.Activity, Long> {
        val nowMs = Util.getMillis()
        val existing = activity[player.uuid]
        if (existing == null) {
            val created = MewMath.Activity(nowMs, player.x, player.y, player.z, player.yRot, player.xRot, ticks)
            created.lastBattleKey = battleKey(player)
            activity[player.uuid] = created
            return created to 0L
        }
        val elapsed = ticks - existing.lastCheckTick
        existing.lastCheckTick = ticks
        existing.observe(nowMs, player.x, player.y, player.z, player.yRot, player.xRot, battleKey(player), config.afk.minMoveBlocks)
        return existing to elapsed
    }

    fun afk(player: ServerPlayer): MewMath.AfkStatus {
        val afk = config.afk
        if (!afk.enabled) return MewMath.AfkStatus.ACTIVE
        val act = activity[player.uuid] ?: return MewMath.AfkStatus.ACTIVE
        val exempt = (afk.fishingExempt && player.fishing != null) || (afk.ridingExempt && player.isPassenger) ||
            (afk.battleExempt && BattleRegistry.getBattleByParticipatingPlayer(player) != null)
        return MewMath.afk(Util.getMillis(), player.lastActionTime, act, exempt, afk.afkSeconds, afk.stillMinutes)
    }

    private fun dimensionAllowed(player: ServerPlayer): Boolean {
        val id = player.level().dimension().location().toString()
        return config.dimensions.any { it.trim() == id }
    }

    /**
     * Why this player cannot see a passage now (a translation key), or null: the server's limit,
     * the dimension, the biome, the sky, the ground, another scene, a battle, a journey, Mew.
     */
    fun whyNot(player: ServerPlayer, ignoreLimit: Boolean = false): String? {
        val level = player.serverLevel()
        return when {
            sessions.containsKey(player.uuid) -> "already"
            !ignoreLimit && sessions.size >= config.maxConcurrent -> "limit"
            !player.isAlive || player.isSpectator -> "busy"
            !dimensionAllowed(player) -> "dimension"
            HoOhPlaces.biomeExcluded(level, player.blockPosition(), config.excludedBiomes) -> "biome"
            !HoOhPlaces.openSky(level, player) -> "sky"
            player.isInWater || player.isInLava || player.isFallFlying -> "ground"
            !(player.onGround() || player.abilities.flying || player.vehicle?.onGround() == true) -> "ground"
            BattleRegistry.getBattleByParticipatingPlayer(player) != null -> "battle"
            LegendaryCinematics.isWatching(player.uuid) -> "cinematic"
            UltraWarpSessions.get(player.uuid) != null -> "journey"
            MewGame.isPlaying(player.uuid) || MewReward.isPending(player.uuid) -> "mew"
            TradeManager.getActiveTrade(player.uuid) != null -> "busy"
            else -> null
        }
    }

    private fun check(server: MinecraftServer, player: ServerPlayer) {
        val (_, elapsed) = sample(player)
        if (!player.isAlive || player.isSpectator) return
        if (!dimensionAllowed(player)) return
        if (afk(player) != MewMath.AfkStatus.ACTIVE) return
        val record = HoOhPassageData.getOrCreate(server, player.uuid)
        val due = HoOhMath.due(record, MewMath.credit(elapsed, CHECK_SECONDS), rules)
        HoOhPassageData.changed(server)
        if (!due || !HoOhMath.eligible(record, rules, System.currentTimeMillis())) return
        // The draw only happens when the passage can be seen.
        val why = whyNot(player)
        if (why != null) {
            log("draw of {} skipped ({})", player.scoreboardName, why)
            return
        }
        record.draws++
        val roll = random.nextDouble()
        log("draw of {}: {} against {}", player.scoreboardName, "%.5f".format(roll), "%.5f".format(rules.chance))
        if (roll >= rules.chance) return
        start(player, Mode.FULL, test = false)
    }

    /** A draw made now (the command): its result, and the passage if it succeeds. */
    fun drawNow(player: ServerPlayer): Pair<Double, Boolean> {
        val roll = random.nextDouble()
        val won = roll < rules.chance
        if (won) start(player, Mode.FULL, test = false)
        return roll to won
    }

    // ---------------------------------------------------------------- sessions

    private fun send(player: ServerPlayer, s: Session, kind: Int, tick: Long, vararg data: Double) {
        player.sendPacket(S2CHoOhPacket(kind, s.id, tick, data))
    }

    private fun newId(): Long = System.currentTimeMillis() * 1000L + (counter++ % 1000L)

    /**
     * Starts a passage for this player (the draw, or a test command). FULL: the scene, then the
     * pursuit and the feather; FLIGHT: the scene only; PURSUIT: the pursuit without the scene;
     * FEATHER: only the feather's fall. A test leaves the player's counters alone.
     */
    fun start(player: ServerPlayer, mode: Mode, test: Boolean): Boolean {
        if (sessions.containsKey(player.uuid)) return false
        val server = player.server
        val level = player.serverLevel()
        val s = Session(newId(), player.uuid, level.dimension(), mode, test, player.position())
        s.started = level.gameTime
        if (!test && mode == Mode.FULL) {
            val record = HoOhPassageData.getOrCreate(server, player.uuid)
            HoOhMath.passed(record, System.currentTimeMillis())
            HoOhPassageData.changed(server)
        }
        if (mode == Mode.FEATHER) {
            sessions[player.uuid] = s
            send(player, s, S2CHoOhPacket.START, level.gameTime, player.x, player.y, player.z, player.yRot.toDouble(), config.scale, 0.0, mode.ordinal.toDouble())
            val from = player.position().add(MewPlaces.forward(player.yRot).scale(3.0)).add(0.0, 14.0, 0.0)
            win(s, player, from)
            log("feather test for {}", player.scoreboardName)
            return true
        }
        s.yaw = HoOhPlaces.openDirection(level, player, random)
        val dir = MewPlaces.forward(s.yaw)
        val c0 = HoOhPlaces.column(level, player.x + dir.x * 36.0, player.z + dir.z * 36.0) ?: HoOhPlaces.column(level, player.x, player.z)
        val ground0 = c0?.ground ?: player.blockY
        val top0 = c0?.top ?: player.blockY
        val p0 = Vec3(player.x + dir.x * 36.0, HoOhPlaces.altitude(top0, ground0, config.pursuit, random), player.z + dir.z * 36.0)
        val cinematic = mode != Mode.PURSUIT && config.cinematic && LegendaryCinematics.canWatch(player)
        if (!cinematic) {
            // No scene: Ho-Oh passes over the player first, from behind.
            val behind = player.position().subtract(dir.scale(24.0))
            s.flight.add(behind.x, p0.y + 6.0, behind.z)
        }
        s.flight.add(p0.x, p0.y, p0.z)
        s.lastGround = ground0
        var last = p0
        repeat(3) {
            val next = HoOhPlaces.nextPoint(level, last, s.lastGround, s.yaw, config.pursuit, random) ?: return@repeat
            s.flight.add(next.first.x, next.first.y, next.first.z)
            s.yaw = next.second.first
            s.lastGround = next.second.second
            last = next.first
        }
        // Not enough loaded ground ahead: straight on, high over the player's own ground.
        while (s.flight.size < 4) {
            last = last.add(dir.scale(30.0)).add(0.0, 0.0, 0.0)
            s.flight.add(last.x, last.y, last.z)
        }
        sessions[player.uuid] = s
        val sceneTicks = (CinematicScenes.HO_OH_SECONDS * 20.0).toLong()
        if (cinematic) {
            val result = LegendaryCinematics.start(
                LegendaryCinematics.Request(level, CinematicScenes.HO_OH, player.position(), s.yaw, p0, player, HO_OH, emptySet(), test = false, onSpawn = { }),
            )
            if (result is LegendaryCinematics.StartResult.Started) {
                s.cinematic = result.session.id
                s.phase = Phase.CINEMATIC
            }
        }
        val pursuitAt = if (s.phase == Phase.CINEMATIC) level.gameTime + 10L + sceneTicks else level.gameTime
        send(player, s, S2CHoOhPacket.START, level.gameTime, player.x, player.y, player.z, s.yaw.toDouble(), config.scale, if (s.phase == Phase.CINEMATIC) 1.0 else 0.0, mode.ordinal.toDouble())
        val points = DoubleArray(s.flight.size * 3)
        for (i in 0 until s.flight.size) {
            s.flight.point(i, tmp)
            points[i * 3] = tmp[0]; points[i * 3 + 1] = tmp[1]; points[i * 3 + 2] = tmp[2]
        }
        send(player, s, S2CHoOhPacket.POINTS, level.gameTime, *points)
        s.speed = config.pursuit.speed
        s.flight.setMotion(pursuitAt, 0.0, s.speed)
        send(player, s, S2CHoOhPacket.MOTION, pursuitAt, 0.0, s.speed)
        if (s.phase != Phase.CINEMATIC) beginPursuit(s, player, level.gameTime)
        log("passage {} for {} ({}, {}{}) at {}", s.id, player.scoreboardName, mode, if (cinematic) "scene" else "no scene", if (test) ", test" else "", player.blockPosition())
        return true
    }

    private fun beginPursuit(s: Session, player: ServerPlayer, now: Long) {
        if (s.mode == Mode.FLIGHT) {
            send(player, s, S2CHoOhPacket.LEAVE, now)
            end(s, player, 2)
            return
        }
        s.phase = Phase.PURSUIT
        s.pursuitStart = now
        val p = config.pursuit
        s.pursuitTicks = ((p.minSeconds + random.nextDouble() * (p.maxSeconds - p.minSeconds)) * 20.0).toLong()
        s.speed = p.speed
        s.band = 2
        s.flight.setMotion(now, 0.0, s.speed)
        send(player, s, S2CHoOhPacket.MOTION, now, 0.0, s.speed)
        player.displayClientMessage(Component.translatable("generations_core.hooh.follow").withStyle(ChatFormatting.GOLD), false)
    }

    private fun tickSessions(server: MinecraftServer) {
        for (s in sessions.values.toList()) {
            val player = server.playerList.getPlayer(s.player)
            if (player == null) {
                sessions.remove(s.player)
                stopCinematic(s)
                continue
            }
            if (!player.isAlive || player.level().dimension() != s.dimension) {
                fail(s, player, "left")
                continue
            }
            val level = player.serverLevel()
            val now = level.gameTime
            try {
                when (s.phase) {
                    Phase.CINEMATIC -> if (s.cinematic?.let { LegendaryCinematics.session(it) } == null) beginPursuit(s, player, now)
                    Phase.PURSUIT -> pursue(s, player, level, now)
                    Phase.FALL -> if (now >= s.fallStart + FALL_TICKS) {
                        s.phase = Phase.WAIT
                        s.waitStart = now
                    }
                    Phase.WAIT -> waitPickup(s, player, now)
                }
            } catch (e: Exception) {
                GenerationsCore.LOGGER.error("Ho-Oh passage {}: stopped", s.id, e)
                fail(s, player, "error")
            }
        }
    }

    /** One more point of the flight (loaded chunks only); false when none could be found. */
    private fun extend(level: ServerLevel, s: Session): Boolean {
        s.flight.point(s.flight.size - 1, tmp)
        val from = Vec3(tmp[0], tmp[1], tmp[2])
        val next = HoOhPlaces.nextPoint(level, from, s.lastGround, s.yaw, config.pursuit, random) ?: return false
        s.flight.add(next.first.x, next.first.y, next.first.z)
        s.yaw = next.second.first
        s.lastGround = next.second.second
        level.server.playerList.getPlayer(s.player)?.let { send(it, s, S2CHoOhPacket.POINTS, level.gameTime, next.first.x, next.first.y, next.first.z) }
        return true
    }

    private fun pursue(s: Session, player: ServerPlayer, level: ServerLevel, now: Long) {
        val p = config.pursuit
        val f = s.flight
        val sNow = f.sAt(now.toDouble())
        f.position(now.toDouble(), tmp)
        val dx = player.x - tmp[0]
        val dz = player.z - tmp[2]
        val d = sqrt(dx * dx + dz * dz)
        // Lost, or too long.
        s.lostTicks = if (d > p.lostRadius) s.lostTicks + SESSION_EVERY else 0L
        if (s.lostTicks >= (p.lostSeconds * 20.0).toLong()) return fail(s, player, "lost")
        if (now - s.pursuitStart > (p.timeoutSeconds * 20.0).toLong()) return fail(s, player, "timeout")
        // More of the way ahead, until the pursuit's length is reached.
        val finishing = now - s.pursuitStart >= s.pursuitTicks
        if (!finishing && f.settled - sNow < 70.0 && now >= s.retryAt) {
            if (!extend(level, s)) s.retryAt = now + 20L
        }
        val atEnd = sNow >= f.settled - 0.5
        // Its speed follows the player (bands kept two checks before they change: few packets).
        val band = when {
            atEnd || d > p.waitRadius -> 0
            d > p.followRadius -> 1
            d < 12.0 -> 3
            else -> 2
        }
        if (band != s.band) {
            s.bandCount++
            if (s.bandCount >= 2 || band == 0) {
                s.band = band
                s.bandCount = 0
                val speed = when (band) {
                    0 -> 0.0
                    1 -> p.speed * 0.55
                    3 -> p.speed * 1.3
                    else -> p.speed
                }
                if (speed != s.speed) {
                    s.speed = speed
                    f.setMotion(now, sNow, speed)
                    send(player, s, S2CHoOhPacket.MOTION, now, sNow, speed)
                }
            }
        } else s.bandCount = 0
        // The end: Ho-Oh circles over its last point; the player must come near it.
        if (finishing && atEnd) {
            f.at(f.settled, tmp)
            val ex = player.x - tmp[0]
            val ez = player.z - tmp[2]
            if (sqrt(ex * ex + ez * ez) <= p.finishRadius) {
                f.position(now.toDouble(), tmp)
                win(s, player, Vec3(tmp[0], tmp[1] - 1.5, tmp[2]))
            }
        }
    }

    /** The pursuit won: the feather is saved for this player, then falls. */
    private fun win(s: Session, player: ServerPlayer, from: Vec3) {
        val server = player.server
        val level = player.serverLevel()
        val now = level.gameTime
        val record = HoOhPassageData.getOrCreate(server, player.uuid)
        if (record.rewardedSession != s.id && !(record.pendingFeather && record.pendingSession == s.id)) {
            if (record.pendingFeather) giveFeather(player) // an older one never given: given now
            record.pendingFeather = true
            record.pendingSession = s.id
        }
        if (!s.test) record.successes++
        HoOhPassageData.saveNow(server)
        val landing = HoOhPlaces.featherLanding(level, player, from, random)
        s.featherAt = landing
        s.phase = Phase.FALL
        s.fallStart = now
        send(player, s, S2CHoOhPacket.FEATHER, now, from.x, from.y, from.z, landing.x, landing.y, landing.z, FALL_TICKS / 20.0)
        if (s.mode != Mode.FEATHER) send(player, s, S2CHoOhPacket.LEAVE, now + 50L)
        player.displayClientMessage(Component.translatable("generations_core.hooh.feather_falls").withStyle(ChatFormatting.GOLD), false)
        log("passage {} won by {}, feather at {}", s.id, player.scoreboardName, landing)
    }

    private fun waitPickup(s: Session, player: ServerPlayer, now: Long) {
        val at = s.featherAt ?: return collect(s, player)
        val close = player.position().distanceTo(at) <= PICK_DISTANCE || player.position().add(0.0, 1.0, 0.0).distanceTo(at) <= PICK_DISTANCE
        // Taken, or given anyway when the player goes away or waits too long: never lost.
        if (close || now - s.waitStart > WAIT_TICKS || player.position().distanceTo(at) > 96.0) collect(s, player)
    }

    private fun collect(s: Session, player: ServerPlayer) {
        val server = player.server
        val record = HoOhPassageData.getOrCreate(server, player.uuid)
        if (record.pendingFeather && record.pendingSession == s.id) {
            giveFeather(player)
            record.pendingFeather = false
            record.rewardedSession = s.id
            HoOhPassageData.saveNow(server)
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.4f)
            player.displayClientMessage(Component.translatable("generations_core.hooh.feather_taken").withStyle(ChatFormatting.GOLD), false)
            log("passage {}: feather given to {}", s.id, player.scoreboardName)
        }
        send(player, s, S2CHoOhPacket.PICKED, player.serverLevel().gameTime)
        end(s, player, 0)
    }

    /** One Sacred Feather into the inventory (at the feet, kept for this player, when it is full). */
    fun giveFeather(player: ServerPlayer) {
        val stack = ItemStack(GenerationsItems.HO_OH_SACRED_FEATHER.value())
        if (!player.inventory.add(stack)) player.drop(stack, false)?.let { it.setTarget(player.uuid) }
    }

    /** A feather won but not received (a disconnection, a crash): given at the next connection. */
    private fun deliverPending(player: ServerPlayer) {
        val server = player.server
        val record = HoOhPassageData.get(server, player.uuid) ?: return
        if (!record.pendingFeather || sessions.containsKey(player.uuid)) return
        giveFeather(player)
        record.pendingFeather = false
        record.rewardedSession = record.pendingSession
        HoOhPassageData.saveNow(server)
        player.displayClientMessage(Component.translatable("generations_core.hooh.feather_delivered").withStyle(ChatFormatting.GOLD), false)
        log("pending feather given to {}", player.scoreboardName)
    }

    private fun fail(s: Session, player: ServerPlayer, reason: String) {
        val won = s.phase == Phase.FALL || s.phase == Phase.WAIT
        if (won) {
            // Won already: the feather stays saved for the next connection.
            sessions.remove(s.player)
            stopCinematic(s)
            return
        }
        player.displayClientMessage(Component.translatable("generations_core.hooh.failed.$reason").withStyle(ChatFormatting.GRAY), false)
        send(player, s, S2CHoOhPacket.LEAVE, player.serverLevel().gameTime)
        log("passage {} of {} failed ({})", s.id, player.scoreboardName, reason)
        end(s, player, 1)
    }

    private fun end(s: Session, player: ServerPlayer?, result: Int) {
        sessions.remove(s.player)
        stopCinematic(s)
        if (player != null) send(player, s, S2CHoOhPacket.END, player.serverLevel().gameTime + 80L, result.toDouble())
    }

    private fun stopCinematic(s: Session) {
        val id = s.cinematic ?: return
        LegendaryCinematics.session(id)?.let { LegendaryCinematics.interruptQuietly(it) }
    }

    /** /generations hooh stop: that player's passage (a feather already won stays saved). */
    fun stop(player: ServerPlayer): Boolean {
        val s = sessions[player.uuid] ?: return false
        if (s.phase == Phase.FALL || s.phase == Phase.WAIT) return collect(s, player).let { true }
        send(player, s, S2CHoOhPacket.LEAVE, player.serverLevel().gameTime)
        end(s, player, 2)
        return true
    }

    /** /generations hooh reset: the counters and the delay (a pending feather is kept). */
    fun reset(server: MinecraftServer, player: UUID) {
        val record = HoOhPassageData.getOrCreate(server, player)
        record.activeTicks = 0L
        record.sinceDraw = 0L
        record.lastPassageMs = 0L
        HoOhPassageData.changed(server)
    }

    /** /generations hooh active <hours>: sets the active play since the last passage (tests). */
    fun setActive(server: MinecraftServer, player: UUID, hours: Double) {
        val record = HoOhPassageData.getOrCreate(server, player)
        record.activeTicks = Math.round(hours * HoOhRules.TICKS_PER_HOUR)
        HoOhPassageData.changed(server)
    }
}
