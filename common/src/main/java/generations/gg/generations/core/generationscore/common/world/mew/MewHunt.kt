package generations.gg.generations.core.generationscore.common.world.mew

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.GenerationsNetwork.sendPacket
import generations.gg.generations.core.generationscore.common.network.packets.mew.S2CMewManifestPacket
import generations.gg.generations.core.generationscore.common.world.cinematic.LegendaryCinematics
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpSessions
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.Random
import java.util.UUID

/**
 * The Mew hunt on the server (MEW-PLAN.md §§ 4 to 12): every connected player is checked once
 * every checkSeconds, at a tick of their own (their UUID's slot: about 7 checks a second for 200
 * players, nothing on the other ticks). A check counts when the player is in a zone, in an
 * allowed biome, and not away from keyboard; then, by the hunt's state:
 *
 * - HUNTING: the time since their previous check (at most twice the interval) is added, the draws
 *   due are made; false appearances now and then (§ 6);
 * - UNLOCKED: the challenge starts as soon as the player is free (MewGame); "Mew waits" otherwise;
 * - COOLDOWN: the wait goes down with the same active time; then Mew comes back (no new draw);
 * - WON: the final scene and the real Mew (MewReward), when it did not happen yet;
 * - RELEASED: nothing here (capture and loss are events; the timeout is checked every minute).
 *
 * Offline players do not progress; nothing about a position is saved. Started by
 * GenerationsCobblemonEvents.init().
 */
object MewHunt {
    var config = MewConfig()
        private set
    var machine = MewStateMachine(config.hunt.rules())
        private set

    /** The connected players' samples (never saved). */
    private val activity = HashMap<UUID, MewMath.Activity>()
    private var ticks = 0L
    private val random = Random()

    /** The server's ticks since it started (the game's clock: a paused solo game does not count). */
    val now: Long get() = ticks

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { event ->
            activity.clear()
            ticks = 0L
            reload(event.server)
            MewGame.recover(event.server)
        }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) { activity.clear() }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { activity.remove(it.player.uuid) }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { tick(it.server) }
        MewGame.init()
        MewReward.init()
    }

    /** mew.json and mew_zones.json again; the hunts' next draws follow the new schedule. */
    fun reload(server: MinecraftServer?) {
        config = MewConfig.load()
        machine = MewStateMachine(config.hunt.rules())
        MewZones.reload()
        if (server != null) {
            val data = MewHuntData.of(server)
            data.records.values.forEach { machine.reschedule(it) }
            data.setDirty()
        }
    }

    private fun tick(server: MinecraftServer) {
        ticks++
        MewGame.tick(server, ticks)
        MewReward.tick(server, ticks)
        if (ticks % 1200L == 0L) MewReward.checkTimeouts(server)
        if (!config.hunt.enabled) return
        val period = config.hunt.checkSeconds * 20
        val slot = Math.floorMod(ticks, period.toLong()).toInt()
        for (player in server.playerList.players) {
            if (MewMath.slot(player.uuid.mostSignificantBits, player.uuid.leastSignificantBits, period) != slot) continue
            try {
                check(server, player)
            } catch (e: Exception) {
                GenerationsCore.LOGGER.error("Mew hunt: check of {} failed", player.scoreboardName, e)
            }
        }
    }

    /** 0 out of a battle; otherwise changes with every turn (a battle that goes on is not idleness). */
    private fun battleKey(player: ServerPlayer): Long {
        val battle = BattleRegistry.getBattleByParticipatingPlayer(player) ?: return 0L
        return (battle.battleId.leastSignificantBits * 31L + battle.turn) or 1L
    }

    private fun exempt(player: ServerPlayer): Boolean {
        val afk = config.afk
        return (afk.fishingExempt && player.fishing != null) ||
            (afk.ridingExempt && player.isPassenger) ||
            (afk.battleExempt && BattleRegistry.getBattleByParticipatingPlayer(player) != null)
    }

    private fun sample(player: ServerPlayer, nowMs: Long): Pair<MewMath.Activity, Long> {
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

    /** The away-from-keyboard rule for this player now (from their last sample). */
    fun afkStatus(player: ServerPlayer): MewMath.AfkStatus {
        if (!config.afk.enabled) return MewMath.AfkStatus.ACTIVE
        val act = activity[player.uuid] ?: return MewMath.AfkStatus.ACTIVE
        return MewMath.afk(Util.getMillis(), player.lastActionTime, act, exempt(player), config.afk.afkSeconds, config.afk.stillMinutes)
    }

    /**
     * Free for Mew (§ 7): alive, playing, on the ground or flying normally, out of water and lava,
     * not riding, not in a battle, a cinematic, an Ultra Warp journey or a challenge.
     */
    fun free(player: ServerPlayer): Boolean =
        player.isAlive && !player.isSpectator && !player.isInWater && !player.isInLava && !player.isPassenger &&
            (player.onGround() || player.abilities.flying) &&
            BattleRegistry.getBattleByParticipatingPlayer(player) == null &&
            !LegendaryCinematics.isWatching(player.uuid) && UltraWarpSessions.get(player.uuid) == null &&
            !MewGame.isPlaying(player.uuid) && !MewReward.isPending(player.uuid)

    /** Nothing else on screen for a false appearance (§ 6). */
    private fun quiet(player: ServerPlayer): Boolean =
        BattleRegistry.getBattleByParticipatingPlayer(player) == null && !LegendaryCinematics.isWatching(player.uuid) &&
            UltraWarpSessions.get(player.uuid) == null && !MewGame.isPlaying(player.uuid) && !MewReward.isPending(player.uuid)

    private fun check(server: MinecraftServer, player: ServerPlayer) {
        val (_, elapsed) = sample(player, Util.getMillis())
        if (!player.isAlive || player.isSpectator) return
        if (MewZones.at(player) == null) return
        if (afkStatus(player) != MewMath.AfkStatus.ACTIVE) return
        val credit = MewMath.credit(elapsed, config.hunt.checkSeconds)
        val record = MewHuntData.getOrCreate(server, player.uuid, machine)
        when (record.state) {
            MewState.HUNTING -> {
                if (credit <= 0L) return
                val outcome = machine.addActive(record, credit) { random.nextDouble() }
                MewHuntData.changed(server)
                if (outcome.unlocked) {
                    unlocked(server, player, outcome.draws.last())
                    tryStart(server, player, record)
                } else {
                    manifest(player, record, credit)
                }
            }
            MewState.UNLOCKED -> tryStart(server, player, record)
            MewState.COOLDOWN -> {
                val ready = machine.addCooldown(record, credit)
                MewHuntData.changed(server)
                if (ready) tryStart(server, player, record)
                else if (config.manifestations.duringCooldown) manifest(player, record, credit)
            }
            MewState.WON -> if (free(player)) MewReward.won(server, player, test = false)
            MewState.CHALLENGE, MewState.RELEASED -> {}
        }
    }

    /** UNLOCKED, or COOLDOWN over: the challenge if the player is free, "Mew waits" otherwise. Never lost. */
    private fun tryStart(server: MinecraftServer, player: ServerPlayer, record: MewRecord) {
        if (!free(player)) {
            player.displayClientMessage(Component.translatable("generations_core.mew.hunt.waiting").withStyle(ChatFormatting.LIGHT_PURPLE), true)
            return
        }
        val comeback = record.state == MewState.COOLDOWN
        if (!machine.startChallenge(record)) return
        MewHuntData.saveNow(server)
        if (!MewGame.start(player, test = false, comeback = comeback)) {
            // Could not start (already playing): counted as a failure would be unfair; back to waiting.
            machine.fail(record, 1L)
            record.cooldownTicks = 0L
            MewHuntData.changed(server)
        }
    }

    /** A false appearance now and then (§ 6): the countdown of active time, the chance, the type by weight. */
    private fun manifest(player: ServerPlayer, record: MewRecord, credit: Long) {
        val m = config.manifestations
        if (!m.enabled || credit <= 0L || !quiet(player)) return
        val interval = (m.intervalMinutes * 1200.0).toLong()
        if (!machine.manifestation(record, credit, interval, m.chancePercent / 100.0) { random.nextDouble() }) return
        val type = pickType() ?: return
        show(player, type)
    }

    /** A type by weight among the enabled ones (0 to 3 = A to D), or null when none is. */
    private fun pickType(): Int? {
        val types = listOf("a", "b", "c", "d").map { config.manifestations.types[it] }
        val total = types.sumOf { if (it != null && it.enabled) it.weight else 0 }
        if (total <= 0) return null
        var r = random.nextInt(total)
        for ((i, t) in types.withIndex()) {
            if (t == null || !t.enabled || t.weight <= 0) continue
            if (r < t.weight) return i
            r -= t.weight
        }
        return null
    }

    /** One false appearance of that type for that player (one packet). False when no place was found. */
    fun show(player: ServerPlayer, type: Int): Boolean {
        val places = MewPlaces.manifestation(player.serverLevel(), player, type, random) ?: return false
        val seconds = config.manifestations.types[listOf("a", "b", "c", "d")[type.coerceIn(0, 3)]]?.seconds ?: 4.0
        val (origin, second, yaw) = places
        player.sendPacket(S2CMewManifestPacket(type, random.nextInt(), origin, yaw, second.x.toFloat(), second.y.toFloat(), second.z.toFloat(), seconds.toFloat()))
        return true
    }

    /** A draw succeeded (by the hunt or the draw command): saved now. */
    fun unlocked(server: MinecraftServer, player: ServerPlayer?, draw: MewStateMachine.Draw) {
        MewHuntData.saveNow(server)
        GenerationsCore.LOGGER.info(
            "Mew hunt: {} unlocked the encounter at {} h ({}, chance {} %)", player?.scoreboardName,
            "%.2f".format(draw.hours), if (draw.guaranteed) "guarantee" else "draw", "%.2f".format(draw.chance * 100.0),
        )
    }

    /** The unlock command / a successful draw from a command: the challenge right away if the player is free. */
    fun startIfFree(server: MinecraftServer, player: ServerPlayer) {
        val record = MewHuntData.get(server, player.uuid) ?: return
        if (record.state == MewState.UNLOCKED || (record.state == MewState.COOLDOWN && record.cooldownTicks <= 0L)) tryStart(server, player, record)
    }
}
