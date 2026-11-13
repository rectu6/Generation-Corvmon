package generations.gg.generations.core.generationscore.common.world.ultraspace

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.party
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.C2SUltraWarpActionPacket
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.C2SUltraWarpRideStatePacket
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWarpPacket
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWarpRideStartPacket
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWarpRideStopPacket
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpDifficulty
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTransfers
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource

/**
 * Ultra Warp Ride: a click on the Celestial Altar opens its menu; "Start the
 * journey" checks everything again on the server (the altar within reach, its face
 * now, Solgaleo or Lunala in the party, a free place), then the ride begins: the
 * server picks the tunnel's seed and keeps time, the player's client draws the
 * tunnel. The player stays at the altar, unharmed, until the journey ends.
 * /ultrawarp test start does the same without altar nor Pokémon, through the same
 * checks and the same manager. Runs on the logical server, so solo and multiplayer
 * alike. See docs/ultra-warp-ride.md.
 */
object UltraWarpRide {
    /** Answer to an action, shown in the menu (or to the operator of a command). */
    enum class Result(val error: Boolean) {
        STARTED(false),
        ENDED(false),
        DISABLED(true),
        TOO_FAR(true),
        VARIANT_CHANGED(true),
        ALREADY_TRAVELLING(true),
        UNAVAILABLE(true),
        IN_BATTLE(true),
        MISSING_POKEMON(true),
        NO_SLOT(true);

        val key: String get() = "generations_core.ultra_warp.result.${name.lowercase()}"

        /** The message: args are the required Pokémon and the maximum of journeys. */
        fun message(variant: UltraWarpVariant, maxSessions: Int): Component = Component.translatable(key, variant.speciesName(), maxSessions)
    }

    private var server: MinecraftServer? = null

    fun init() {
        // The moves between the worlds (phase 4.1): arrivals through the portals, the return.
        UltraWarpTransfers.init()
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) {
            server = it.server
            UltraSpaceConfig.load()
        }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) {
            UltraWarpSessions.endAll(UltraWarpSession.EndReason.SERVER_STOPPING)
            server = null
        }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(Priority.NORMAL) {
            // Leaving during the defeat does not escape it: the death comes at once.
            val session = UltraWarpSessions.get(it.player.uuid)
            if (session?.stage == UltraWarpSession.Stage.DEFEAT && session.ride?.killSent == false) {
                session.ride?.killSent = true
                runCatching { UltraWarpGameplay.strike(it.player) }
            }
            UltraWarpSessions.end(it.player.uuid, UltraWarpSession.EndReason.DISCONNECTED)
            // A move in progress stops; a journey's arrival stays pending, done at the next login.
            UltraWarpTransfers.onLogout(it.player.uuid)
        }
        // Last, so that a death another mod cancels does not end the journey.
        PlatformEvents.PLAYER_DEATH.subscribe(Priority.LOWEST) { event ->
            if (event.isCanceled) return@subscribe
            UltraWarpTransfers.onDeath(event.player)
            // Dying of the defeat ends it as lost; any other death interrupts the journey.
            if (UltraWarpSessions.get(event.player.uuid)?.stage == UltraWarpSession.Stage.DEFEAT) {
                UltraWarpSessions.end(event.player.uuid, UltraWarpSession.EndReason.DEFEATED)
            } else if (UltraWarpSessions.end(event.player.uuid, UltraWarpSession.EndReason.CANCELLED) != null) {
                event.player.sendSystemMessage(Component.translatable("generations_core.ultra_warp.interrupted"))
            }
        }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { tick(it.server) }
        // Whatever ends a journey, the player's client goes back to the world.
        UltraWarpSessions.onEnded = { session, reason ->
            // Through a portal: the journey succeeded, even when a disconnection or a server
            // stop cut its cinematic (the arrival is then done at the next login).
            val throughPortal = session.stage == UltraWarpSession.Stage.CAPTURE && session.ride?.captured != null &&
                (reason == UltraWarpSession.EndReason.SUCCESS || reason == UltraWarpSession.EndReason.DISCONNECTED || reason == UltraWarpSession.EndReason.SERVER_STOPPING)
            val outcome = record(session, if (throughPortal) UltraWarpSession.EndReason.SUCCESS else reason)
            stopClient(session, reason, outcome)
            val server = server
            if (throughPortal && outcome != null && server != null) {
                UltraWarpTransfers.afterJourney(server, outcome, reason == UltraWarpSession.EndReason.SUCCESS)
            } else {
                UltraWarpTransfers.cancelPrepared(session.player)
            }
        }
    }

    /** Right click on an altar: its menu, as the server sees it now. */
    fun open(player: ServerPlayer, pos: BlockPos) {
        val variant = UltraWarpVariant.of(player.level().getBlockState(pos)) ?: return
        send(player, pos, variant, null)
    }

    fun onAction(player: ServerPlayer, packet: C2SUltraWarpActionPacket) {
        when (packet.action) {
            C2SUltraWarpActionPacket.Action.START -> startAtAltar(player, packet.pos, packet.variant)
            // Leaving is no way out of a defeat (the test command still is).
            // Leaving is no way out of a defeat, nor out of a portal once in it (the test command still is).
            C2SUltraWarpActionPacket.Action.END -> {
                val stage = UltraWarpSessions.get(player.uuid)?.stage
                if (stage != UltraWarpSession.Stage.DEFEAT && stage != UltraWarpSession.Stage.CAPTURE && !end(player)) send(player, packet.pos, packet.variant, null)
            }
        }
    }

    /**
     * What every journey needs, wherever it starts; null when all is met. A free
     * place is checked last, by the manager itself when it creates the session.
     */
    private fun refusal(player: ServerPlayer): Result? = when {
        UltraWarpSessions.get(player.uuid) != null || UltraWarpTransfers.busy(player.uuid) -> Result.ALREADY_TRAVELLING
        UltraSpaceConfig.ride.maxSessions <= 0 -> Result.DISABLED
        player.isSpectator || !player.isAlive -> Result.UNAVAILABLE
        player.isInBattle() -> Result.IN_BATTLE
        else -> null
    }

    /** "Start the journey": every condition again, the first one missing is the answer. */
    private fun startAtAltar(player: ServerPlayer, pos: BlockPos, seen: UltraWarpVariant) {
        refusal(player)?.let { return send(player, pos, seen, it) }
        val variant = altar(player, pos) ?: return send(player, pos, seen, Result.TOO_FAR)
        // The altar turned between the menu and the click: the menu shows its new face.
        if (variant != seen) return send(player, pos, variant, Result.VARIANT_CHANGED)
        if (!hasRequired(player, variant)) return send(player, pos, variant, Result.MISSING_POKEMON)
        val session = UltraWarpSessions.create(player, variant, pos, UltraWarpSession.Source.ALTAR) ?: return send(player, pos, variant, Result.NO_SLOT)
        beginRide(player, session, null)
    }

    /**
     * A journey without altar nor Pokémon (/ultrawarp test start), for testing alone:
     * same checks otherwise, same manager and limit.
     */
    fun startTest(player: ServerPlayer, variant: UltraWarpVariant, mode: String? = null): Result {
        refusal(player)?.let { return it }
        val session = UltraWarpSessions.create(player, variant, player.blockPosition(), UltraWarpSession.Source.COMMAND) ?: return Result.NO_SLOT
        beginRide(player, session, mode)
        return Result.STARTED
    }

    /**
     * Into the tunnel: the seed (fixed by the test config, or new), the start time and
     * the settings are the server's; the client draws the tunnel from them.
     */
    private fun beginRide(player: ServerPlayer, session: UltraWarpSession, mode: String?) {
        val config = UltraSpaceConfig.ride
        val seed = config.test.fixedSeed.takeIf { it != 0L } ?: player.random.nextLong()
        // The player's own Solgaleo or Lunala, as it looks (shiny...).
        val aspects = player.party().firstOrNull { it.species.resourceIdentifier == session.variant.species }?.aspects ?: emptySet()
        // The server's difficulty (config), or the one a test command gives.
        session.beginRide(seed, player.server.overworld().gameTime, config.settings(session.variant, mode), aspects)
        // Every chance of this journey, in the log.
        val settings = session.settings!!
        GenerationsCore.LOGGER.info("Ultra Warp Ride: {} journey (seed {}), chances:", player.gameProfile.name, seed)
        UltraWarpDifficulty.describe(settings.config, settings.mode).forEach { GenerationsCore.LOGGER.info("Ultra Warp Ride:   {}", it) }
        S2CUltraWarpRideStartPacket(session.variant, aspects.toList(), seed, session.rideStart, session.settings!!, config.test.maxDurationSeconds)
            .sendToPlayer(player)
    }

    /** Where the player steers: checked, and the collisions worked out (UltraWarpGameplay). */
    fun onState(player: ServerPlayer, packet: C2SUltraWarpRideStatePacket) {
        val session = UltraWarpSessions.get(player.uuid) ?: return
        UltraWarpGameplay.onState(session, player, packet, player.server.overworld().gameTime)
    }

    /** Ends the player's journey (their client asks, or a command) ; false when they had none. */
    fun end(player: ServerPlayer): Boolean {
        val session = UltraWarpSessions.end(player.uuid, UltraWarpSession.EndReason.FINISHED) ?: return false
        send(player, session.altar, session.variant, Result.ENDED)
        return true
    }

    /**
     * The distance of a journey that went into the tunnel is saved with the world
     * (UltraWarpRecords), whatever ended it, and logged; the player, still here, is
     * told how far they went and their best.
     */
    private fun record(session: UltraWarpSession, reason: UltraWarpSession.EndReason): UltraWarpOutcome? {
        val ride = session.ride ?: return null
        if (session.simulated) return null
        val server = server ?: return null
        val lightYears = ride.lightYears
        val mode = ride.settings.mode.id
        // How it ended, from the server's own data only (the portal it captured, never the client's word).
        val portal = ride.captured?.takeIf { reason == UltraWarpSession.EndReason.SUCCESS }
        val outcome = UltraWarpOutcome(
            session.journey, session.player, session.variant.species.toString(), "shiny" in session.aspects, mode, lightYears, reason,
            portal?.id ?: 0, portal?.destination ?: "", portal?.color ?: 0, portal?.rarity ?: 0,
        )
        outcome.distance = ride.distance
        if (portal != null) {
            // Where the portal leads: its world, if open (UltraWarpTransfers takes the player there).
            val world = UltraWorlds.forDestination(portal.destination)
            outcome.world = world?.location()?.toString() ?: ""
            outcome.arrival = if (UltraWorlds.level(server, world) != null) UltraWarpOutcome.Arrival.PENDING else UltraWarpOutcome.Arrival.UNAVAILABLE
        }
        UltraWarpRecords.addOutcome(server, outcome)
        val (record, best) = UltraWarpRecords.add(server, session.player, lightYears, mode)
        GenerationsCore.LOGGER.info(
            "Ultra Warp Ride: {} travelled {} ly in mode {} ({} orbs, {} hits, {} gaps, {})",
            session.player, "%.1f".format(lightYears), ride.settings.mode.id, ride.orbs, ride.hits, ride.gaps, reason,
        )
        GenerationsCore.LOGGER.info("Ultra Warp Ride: outcome: {}", outcome)
        if (reason == UltraWarpSession.EndReason.DISCONNECTED || reason == UltraWarpSession.EndReason.SERVER_STOPPING) return outcome
        val player = server.playerList.getPlayer(session.player) ?: return outcome
        val key = if (best) "generations_core.ultra_warp.result.distance_best" else "generations_core.ultra_warp.result.distance"
        val modeName = Component.translatableWithFallback("generations_core.ultra_warp.mode.$mode", mode)
        player.sendSystemMessage(Component.translatable(key, "%.1f".format(lightYears), "%.1f".format(record), modeName))
        return outcome
    }

    /** The player's client leaves the tunnel, unless they are gone. */
    private fun stopClient(session: UltraWarpSession, reason: UltraWarpSession.EndReason, outcome: UltraWarpOutcome?) {
        if (session.simulated || session.ride == null) return
        if (reason == UltraWarpSession.EndReason.DISCONNECTED || reason == UltraWarpSession.EndReason.SERVER_STOPPING) return
        val player = server?.playerList?.getPlayer(session.player) ?: return
        S2CUltraWarpRideStopPacket(reason, outcome).sendToPlayer(player)
    }

    /**
     * A player in the tunnel is not hurt in the world, where they stay unseen by
     * their own camera; what goes through invulnerability (/kill, the void) still
     * does, and their death ends the journey.
     */
    @JvmStatic
    fun protects(player: ServerPlayer, source: DamageSource): Boolean =
        (UltraWarpSessions.get(player.uuid)?.ride != null || UltraWarpTransfers.protects(player)) && !source.`is`(DamageTypeTags.BYPASSES_INVULNERABILITY)

    /**
     * The altar's face, when the player can reach it: checked before reading the
     * block, so that a far position never loads a chunk.
     */
    private fun altar(player: ServerPlayer, pos: BlockPos): UltraWarpVariant? {
        if (!player.canInteractWithBlock(pos, 1.0)) return null
        val level = player.level()
        if (!level.isLoaded(pos)) return null
        return UltraWarpVariant.of(level.getBlockState(pos))
    }

    private fun hasRequired(player: ServerPlayer, variant: UltraWarpVariant): Boolean =
        player.party().any { it.species.resourceIdentifier == variant.species }

    private fun send(player: ServerPlayer, pos: BlockPos, variant: UltraWarpVariant, result: Result?) {
        S2CUltraWarpPacket(
            pos,
            variant,
            hasRequired(player, variant),
            UltraWarpSessions.freeSlots(),
            UltraSpaceConfig.ride.maxSessions,
            UltraWarpSessions.get(player.uuid)?.variant,
            result,
            UltraSpaceConfig.ride.mode,
        ).sendToPlayer(player)
    }

    private fun tick(server: MinecraftServer) {
        val now = server.overworld().gameTime
        // The journeys move on each tick (UltraWarpGameplay).
        UltraWarpSessions.all().forEach { session ->
            if (session.ride == null || session.simulated) return@forEach
            val player = server.playerList.getPlayer(session.player) ?: return@forEach
            UltraWarpGameplay.tick(session, player, now)
        }
        if (server.tickCount % 20 != 0) return
        val maxTicks = UltraSpaceConfig.ride.test.maxDurationSeconds * 20L
        UltraWarpSessions.all().forEach { session ->
            val player = if (session.simulated) null else server.playerList.getPlayer(session.player)
            when {
                session.simulated -> {}
                // Safety net besides the logout event: no place stays taken by someone gone.
                player == null -> UltraWarpSessions.end(session.player, UltraWarpSession.EndReason.DISCONNECTED)
                // Test duration over: back to the world.
                maxTicks > 0 && session.stage == UltraWarpSession.Stage.RIDE && now - session.rideStart >= maxTicks -> {
                    UltraWarpSessions.end(session.player, UltraWarpSession.EndReason.FINISHED)
                    player.sendSystemMessage(Component.translatable("generations_core.ultra_warp.timed_out"))
                }
                else -> session.tick()
            }
        }
    }
}
