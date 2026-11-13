package generations.gg.generations.core.generationscore.common.world.ultraspace.worlds

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.util.party
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWarpTransferPacket
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraSpaceConfig
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpOutcome
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpRecords
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Moving players between the worlds, on the server (the authority, solo's integrated
 * server too), one transfer per player, each with its own chunk tickets, apart from the
 * journeys' sessions:
 * - ARRIVAL: through a portal. Prepared as soon as the Pokémon goes in (the cinematic
 *   gives the chunks time to load), done once the journey has ended (released);
 * - TEST: /ultrawarp test arrive, the same without a journey;
 * - RETURN: /ultrawarp test return, back where the player stood before leaving;
 * - PREPARE: /ultrawarp worlds prepare, every arrival point looked at, no player;
 * - RIFT (phase 6): through a rift, from an Ultra world to another. Never a journey: no
 *   outcome, so none of its rewards (encounter, shiny, Ultra Beast, light-years, portal
 *   level), and the home is never replaced. RETURN is also the wheel's "Back to the
 *   Overworld". Both wait a moment (the client's passage) before arriving, and are kept
 *   as pending when cut by a disconnection, finished at the next login.
 *
 * The player only moves once the place is loaded and checked (UltraArrivalSearch): they
 * wait where they are, unharmed, behind the client's white screen, and arrive standing
 * on solid ground, then stay protected for a few seconds. Places are tried in turn (the
 * world's arrival points, new places), then the fallback (a small platform, or stay).
 * A journey's arrival cut by a disconnection or a server stop is kept as pending
 * (UltraWarpTravellers) and finished at the next login, once (the journey's id).
 */
object UltraWarpTransfers {
    enum class Kind { ARRIVAL, TEST, RETURN, PREPARE, RIFT }

    /** A place tried: its chunks are loaded (a ticket), then a safe spot is looked for around it. */
    private class Candidate(
        val level: ServerLevel,
        val x: Int,
        val z: Int,
        val radius: Int,
        val step: Int,
        val context: UltraArrivalSearch.Context,
        /** The arrival point it comes from (null for the others). */
        val point: UltraArrivalPoints.Point?,
        val label: String,
        /** No safe spot anywhere: a small platform here. */
        val platform: Boolean = false,
        /** A place to try first as it is (where the player stood before leaving, their respawn point). */
        val exact: Vec3? = null,
        val yaw: Float? = null,
        val pitch: Float? = null,
    ) {
        val chunk = ChunkPos(x shr 4, z shr 4)
        val chunkRadius = (radius + max(context.buildRadius, max(context.edgeRadius, context.hazardRadius)) + 2 + 15) / 16
    }

    private class Transfer(
        val player: UUID?,
        val kind: Kind,
        /** The world arrived in (ARRIVAL, TEST, PREPARE). */
        val target: ResourceKey<Level>?,
        val journey: UUID,
        var outcome: UltraWarpOutcome?,
        val start: Int,
        val report: ((Component) -> Unit)?,
    ) {
        /** An arrival waits for its journey's end (the cinematic). */
        var released = kind != Kind.ARRIVAL
        /** A place to try before the arrival points (/ultrawarp test biome). */
        var first: Pair<Int, Int>? = null
        var notBefore = 0
        /** No arrival before this tick (the client's passage, phase 6). */
        var holdUntil = 0
        val queue = ArrayDeque<() -> Candidate?>()
        var current: Candidate? = null
        var loadStart = 0
        var spot: BlockPos? = null
        var fallback = false
        var firstAnchor: Pair<Int, Int>? = null
        var found = 0
        var refused = 0
    }

    private val TICKET: TicketType<ChunkPos> = TicketType.create("generations_core_ultra_arrival", Comparator.comparingLong<ChunkPos> { it.toLong() }, 20 * 120)

    private val transfers = LinkedHashMap<UUID, Transfer>()
    private val prepares = ArrayList<Transfer>()
    /** Players protected until this server tick, after arriving. */
    private val protectedUntil = HashMap<UUID, Int>()
    private var server: MinecraftServer? = null

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) {
            server = it.server
            UltraWorldsConfig.load()
            generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.gen.UltraResourcesConfig.load()
            generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.gen.UltraStructuresConfig.load()
        }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) {
            // The chunks are saved with the server; a pending arrival stays saved (UltraWarpTravellers).
            transfers.clear()
            prepares.clear()
            protectedUntil.clear()
            server = null
        }
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe(Priority.NORMAL) { onLogin(it.player) }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { tick(it.server) }
    }

    /** Moving, or just arrived: not hurt (what goes through invulnerability still does). */
    @JvmStatic
    fun protects(player: ServerPlayer): Boolean =
        player.uuid in transfers || (protectedUntil[player.uuid] ?: -1) >= player.server.tickCount

    fun busy(player: UUID): Boolean = player in transfers

    /** A portal took the player's Pokémon in: its world starts loading during the cinematic. */
    fun prepare(player: ServerPlayer, journey: UUID, destination: String) {
        val server = player.server
        val key = UltraWorlds.forDestination(destination) ?: return
        if (UltraWorlds.level(server, key) == null) return
        cancel(player.uuid)
        begin(server, Transfer(player.uuid, Kind.ARRIVAL, key, journey, null, server.tickCount, null), player)
    }

    /** The journey prepared at the capture ended in another way (a test command): nothing to arrive in. */
    fun cancelPrepared(player: UUID) {
        transfers[player]?.takeIf { it.kind == Kind.ARRIVAL && !it.released }?.let { cancel(player) }
    }

    /**
     * A journey through a portal ended (its outcome recorded, arrival PENDING or
     * UNAVAILABLE): kept as pending, then done now if the player is still here, at their
     * next login otherwise.
     */
    fun afterJourney(server: MinecraftServer, outcome: UltraWarpOutcome, online: Boolean) {
        val player = if (online) server.playerList.getPlayer(outcome.player) else null
        if (outcome.arrival == UltraWarpOutcome.Arrival.UNAVAILABLE) {
            cancel(outcome.player)
            player?.sendSystemMessage(Component.translatable("generations_core.ultra_warp.world.unavailable", worldName(outcome.destination)))
            return
        }
        UltraWarpTravellers.edit(server, outcome.player) { it.pending = outcome }
        if (player == null) {
            cancel(outcome.player)
            return
        }
        val prepared = transfers[outcome.player]?.takeIf { it.kind == Kind.ARRIVAL && it.journey == outcome.journey }
        if (prepared != null) {
            prepared.outcome = outcome
            prepared.released = true
        } else {
            val key = UltraWorlds.forDestination(outcome.destination) ?: return
            begin(server, Transfer(outcome.player, Kind.ARRIVAL, key, outcome.journey, outcome, server.tickCount, null).also { it.released = true }, player)
        }
    }

    /** /ultrawarp test arrive: into a world without a journey. False when the player is busy or the world closed. */
    fun test(player: ServerPlayer, world: String): Boolean {
        val key = UltraWorlds.key(world) ?: return false
        if (busy(player.uuid) || UltraWorlds.level(player.server, key) == null) return false
        begin(player.server, Transfer(player.uuid, Kind.TEST, key, UUID.randomUUID(), null, player.server.tickCount, null), player)
        S2CUltraWarpTransferPacket(S2CUltraWarpTransferPacket.State.STARTED, Kind.TEST, world, colour(world), null).sendToPlayer(player)
        return true
    }

    /** /ultrawarp test biome: into a world, near this place first (the biome found there). False when busy or closed. */
    fun testAt(player: ServerPlayer, world: String, x: Int, z: Int): Boolean {
        val key = UltraWorlds.key(world) ?: return false
        if (busy(player.uuid) || UltraWorlds.level(player.server, key) == null) return false
        val transfer = Transfer(player.uuid, Kind.TEST, key, UUID.randomUUID(), null, player.server.tickCount, null).also { it.first = x to z }
        begin(player.server, transfer, player)
        S2CUltraWarpTransferPacket(S2CUltraWarpTransferPacket.State.STARTED, Kind.TEST, world, colour(world), null).sendToPlayer(player)
        return true
    }

    /**
     * Back where the player stood before leaving (/ultrawarp test return, the wheel's
     * "Back to the Overworld"), not before holdTicks (the passage). False when they are busy.
     */
    fun returnHome(player: ServerPlayer, holdTicks: Int = 0): Boolean {
        if (busy(player.uuid)) return false
        val server = player.server
        val transfer = Transfer(player.uuid, Kind.RETURN, null, UUID.randomUUID(), null, server.tickCount, null).also {
            it.holdUntil = server.tickCount + holdTicks
        }
        UltraWarpTravellers.edit(server, player.uuid) { it.pendingReturn = true }
        begin(server, transfer, player)
        S2CUltraWarpTransferPacket(S2CUltraWarpTransferPacket.State.STARTED, Kind.RETURN, "", 0xFFFFFF, null).sendToPlayer(player)
        return true
    }

    /**
     * Through a rift (phase 6), into another Ultra world: first near the rift's x/z there
     * (the "coordinates" arrival), then the world's arrival points, then the fallback;
     * not before holdTicks (the passage). False when the player is busy or the world closed.
     */
    fun rift(player: ServerPlayer, world: String, x: Double, z: Double, holdTicks: Int): Boolean {
        val server = player.server
        val key = UltraWorlds.key(world) ?: return false
        if (busy(player.uuid) || UltraWorlds.level(server, key) == null) return false
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        UltraWarpTravellers.edit(server, player.uuid) { it.pendingRift = UltraWarpTravellers.PendingRift(world, bx, bz) }
        startRift(server, player, world, key, bx, bz, holdTicks)
        return true
    }

    private fun startRift(server: MinecraftServer, player: ServerPlayer, world: String, key: ResourceKey<Level>, x: Int, z: Int, holdTicks: Int, notBefore: Int = 0) {
        val transfer = Transfer(player.uuid, Kind.RIFT, key, UUID.randomUUID(), null, server.tickCount, null).also {
            if (generations.gg.generations.core.generationscore.common.world.ultraspace.rift.UltraRiftsConfig.rifts.arrival == "coordinates") it.first = x to z
            it.holdUntil = server.tickCount + holdTicks
            it.notBefore = notBefore
        }
        begin(server, transfer, player)
        S2CUltraWarpTransferPacket(S2CUltraWarpTransferPacket.State.STARTED, Kind.RIFT, world, colour(world), null).sendToPlayer(player)
    }

    /** /ultrawarp worlds prepare: every arrival point of a world looked at; the summary told at the end. */
    fun prepareWorld(server: MinecraftServer, world: String, report: (Component) -> Unit): Boolean {
        val key = UltraWorlds.key(world) ?: return false
        if (server.getLevel(key) == null) return false
        begin(server, Transfer(null, Kind.PREPARE, key, UUID.randomUUID(), null, server.tickCount, report), null)
        return true
    }

    fun onLogout(player: UUID) {
        cancel(player)
    }

    /** Dying while waiting (only what goes through the protection): no arrival. */
    fun onDeath(player: ServerPlayer) {
        val transfer = transfers[player.uuid] ?: return
        fail(player.server, transfer, player, "the player died")
    }

    /** A pending arrival (cut by a disconnection or a server stop): finished now, once. */
    private fun onLogin(player: ServerPlayer) {
        val server = player.server
        val traveller = UltraWarpTravellers.get(server, player.uuid) ?: return
        if (busy(player.uuid)) return
        // A passage through a rift or a return cut the same way (phase 6): done now, protected meanwhile.
        traveller.pendingRift?.let { pending ->
            val key = UltraWorlds.key(pending.world)
            if (key == null || UltraWorlds.level(server, key) == null) {
                UltraWarpTravellers.edit(server, player.uuid) { it.pendingRift = null }
                protectedUntil[player.uuid] = server.tickCount + (UltraWorldsConfig.settings.protection.seconds * 20).toInt()
                player.sendSystemMessage(Component.translatable("generations_core.ultra_warp.world.unavailable", worldName(pending.world)))
            } else {
                GenerationsCore.LOGGER.info("Ultra Space: {} logged in during a passage through a rift to {}, finishing it", player.gameProfile.name, pending.world)
                startRift(server, player, pending.world, key, pending.x, pending.z, 0, server.tickCount + 40)
            }
            return
        }
        if (traveller.pendingReturn) {
            GenerationsCore.LOGGER.info("Ultra Space: {} logged in during a return, finishing it", player.gameProfile.name)
            val transfer = Transfer(player.uuid, Kind.RETURN, null, UUID.randomUUID(), null, server.tickCount, null).also { it.notBefore = server.tickCount + 40 }
            begin(server, transfer, player)
            S2CUltraWarpTransferPacket(S2CUltraWarpTransferPacket.State.STARTED, Kind.RETURN, "", 0xFFFFFF, null).sendToPlayer(player)
            return
        }
        val pending = traveller.pending ?: return
        val key = UltraWorlds.forDestination(pending.destination)
        if (UltraWorlds.level(server, key) == null) {
            pending.arrival = UltraWarpOutcome.Arrival.UNAVAILABLE
            UltraWarpRecords.updateOutcome(server, pending)
            UltraWarpTravellers.edit(server, player.uuid) { it.pending = null }
            player.sendSystemMessage(Component.translatable("generations_core.ultra_warp.world.unavailable", worldName(pending.destination)))
            return
        }
        GenerationsCore.LOGGER.info("Ultra Space: {} logged in with a pending arrival in {} (journey {}), finishing it", player.gameProfile.name, pending.destination, pending.journey)
        val transfer = Transfer(player.uuid, Kind.ARRIVAL, key, pending.journey, pending, server.tickCount, null).also {
            it.released = true
            // A moment for the client to be in the world.
            it.notBefore = server.tickCount + 40
        }
        begin(server, transfer, player)
        player.sendSystemMessage(Component.translatable("generations_core.ultra_warp.world.resumed", worldName(pending.destination)))
        S2CUltraWarpTransferPacket(S2CUltraWarpTransferPacket.State.STARTED, Kind.ARRIVAL, pending.destination, pending.color, pending).sendToPlayer(player)
    }

    private fun begin(server: MinecraftServer, transfer: Transfer, player: ServerPlayer?) {
        plan(server, transfer, player)
        if (transfer.player != null) transfers[transfer.player] = transfer else prepares.add(transfer)
        log("{} transfer of {} to {} begins ({} places to try)", transfer.kind, transfer.player ?: "-", transfer.target?.location() ?: "home", transfer.queue.size)
    }

    /** The places to try, in order, worked out one at a time when reached. */
    private fun plan(server: MinecraftServer, transfer: Transfer, player: ServerPlayer?) {
        val arrival = UltraWorldsConfig.settings.arrival
        when (transfer.kind) {
            Kind.ARRIVAL, Kind.TEST, Kind.PREPARE, Kind.RIFT -> {
                val level = transfer.target?.let { server.getLevel(it) } ?: return
                val points = UltraArrivalPoints.of(level)
                val context = UltraArrivalSearch.Context.arrival(transfer.player)
                val random = java.util.Random(transfer.journey.mostSignificantBits xor transfer.journey.leastSignificantBits)
                transfer.first?.let { (x, z) ->
                    transfer.queue.add { Candidate(level, x, z, arrival.searchRadius + 16, 2, context, null, "biome found at $x $z") }
                }
                val list = if (transfer.kind == Kind.PREPARE) points.points.filter { !it.noLand } else points.order(transfer.journey)
                list.forEach { point ->
                    transfer.queue.add {
                        val anchor = points.anchor(level, point)
                        if (anchor == null) {
                            log("{} point {}: {}", level.dimension().location(), point.index, point.reason)
                            transfer.refused++
                            return@add null
                        }
                        if (transfer.firstAnchor == null) transfer.firstAnchor = anchor
                        // Each player a little apart from the point: not all on one block.
                        val scatter = if (transfer.kind == Kind.PREPARE) 0 else arrival.scatter
                        val dx = if (scatter > 0) random.nextInt(-scatter, scatter + 1) else 0
                        val dz = if (scatter > 0) random.nextInt(-scatter, scatter + 1) else 0
                        Candidate(level, anchor.first + dx, anchor.second + dz, arrival.searchRadius, 2, context, point, "point ${point.index}")
                    }
                }
                if (transfer.kind != Kind.PREPARE) repeat(arrival.freshTries) { i ->
                    transfer.queue.add {
                        val angle = random.nextDouble() * 2 * Math.PI
                        val radius = arrival.minRadius + random.nextDouble() * (arrival.spread - arrival.minRadius)
                        val land = UltraArrivalPoints.land(level, (cos(angle) * radius).toInt(), (sin(angle) * radius).toInt(), arrival.landSearch) ?: return@add null
                        if (transfer.firstAnchor == null) transfer.firstAnchor = land
                        Candidate(level, land.first, land.second, arrival.searchRadius, 2, context, null, "new place ${i + 1}")
                    }
                }
            }
            Kind.RETURN -> {
                val uuid = transfer.player ?: return
                val overworldOnly = generations.gg.generations.core.generationscore.common.world.ultraspace.rift.UltraRiftsConfig.wheelReturn.overworldOnly
                // Never back into an Ultra world; by the config, only into the Overworld.
                val home = UltraWarpTravellers.get(server, uuid)?.home
                    ?.takeIf { !UltraWorlds.isUltra(it.dimension) && (!overworldOnly || it.dimension == Level.OVERWORLD) }
                val context = UltraArrivalSearch.Context.home(uuid)
                if (home != null) server.getLevel(home.dimension)?.let { level ->
                    transfer.queue.add {
                        Candidate(level, floor(home.x).toInt(), floor(home.z).toInt(), UltraWorldsConfig.settings.returnHome.searchRadius, 1, context, null, "home",
                            exact = Vec3(home.x, home.y, home.z), yaw = home.yaw, pitch = home.pitch)
                    }
                }
                val respawn = player?.respawnPosition
                val respawnLevel = player?.let { server.getLevel(it.respawnDimension) }
                    ?.takeIf { !UltraWorlds.isUltra(it.dimension()) && (!overworldOnly || it.dimension() == Level.OVERWORLD) }
                if (respawn != null && respawnLevel != null) {
                    transfer.queue.add { Candidate(respawnLevel, respawn.x, respawn.z, 8, 1, context, null, "respawn point", exact = Vec3.atBottomCenterOf(respawn.above())) }
                }
                val overworld = server.overworld()
                val spawn = overworld.sharedSpawnPos
                transfer.queue.add {
                    Candidate(overworld, spawn.x, spawn.z, 16, 1, UltraArrivalSearch.Context(true, 0, arrival.maxDrop, 1, arrival.hazardRadius, 0, uuid), null, "world spawn")
                }
            }
        }
    }

    private fun tick(server: MinecraftServer) {
        val now = server.tickCount
        // Just arrived: no fall counted.
        protectedUntil.entries.removeIf { (uuid, until) ->
            if (until < now) return@removeIf true
            server.playerList.getPlayer(uuid)?.let { it.fallDistance = 0f }
            false
        }
        if (transfers.isEmpty() && prepares.isEmpty()) return
        (transfers.values.toList() + prepares.toList()).forEach { transfer ->
            try {
                step(server, transfer, now)
            } catch (e: Exception) {
                GenerationsCore.LOGGER.error("Ultra Space: {} transfer of {} failed", transfer.kind, transfer.player ?: "-", e)
                fail(server, transfer, transfer.player?.let { server.playerList.getPlayer(it) }, "error: ${e.message}")
            }
        }
    }

    private fun step(server: MinecraftServer, transfer: Transfer, now: Int) {
        val player = transfer.player?.let { server.playerList.getPlayer(it) }
        if (transfer.player != null && player == null) return cancel(transfer.player)
        if (now < transfer.notBefore) return
        val timeout = UltraWorldsConfig.settings.arrival.loadTimeoutSeconds * 20
        // Far too long overall: straight to the fallback.
        if (transfer.kind != Kind.PREPARE && transfer.released && !transfer.fallback && now - transfer.start > timeout * 3) {
            log("{} transfer of {}: too long, fallback", transfer.kind, transfer.player)
            return fallback(server, transfer, player)
        }
        val candidate = transfer.current ?: run {
            val next = next(transfer) ?: return fallback(server, transfer, player)
            transfer.current = next
            transfer.loadStart = now
            transfer.spot = null
            next.level.chunkSource.addRegionTicket(TICKET, next.chunk, next.chunkRadius, next.chunk)
            log("{} transfer of {}: trying {} in {} at {} {}", transfer.kind, transfer.player ?: "-", next.label, next.level.dimension().location(), next.x, next.z)
            next
        }
        if (!loaded(candidate)) {
            if (now - transfer.loadStart > timeout) {
                refuse(transfer, candidate, "chunks not loaded in time")
            }
            return
        }
        if (transfer.spot == null) {
            val spot = if (candidate.platform) platform(candidate) else search(transfer, candidate)
            if (spot == null) return
            transfer.spot = spot
            if (transfer.kind == Kind.PREPARE) {
                candidate.point?.let { UltraArrivalPoints.of(candidate.level).used(it, spot, count = false) }
                transfer.found++
                log("{} point {}: safe at {}", candidate.level.dimension().location(), candidate.point?.index, spot.toShortString())
                release(transfer)
                return
            }
        }
        // An arrival waits for the end of its journey's cinematic, its place ready.
        if (!transfer.released || player == null) return
        // A passage (rift, return) lasts at least its transition, for the client's fade.
        if (now < transfer.holdUntil) return
        val spot = transfer.spot ?: return
        // Checked again just before: the place may have changed while waiting.
        if (!candidate.platform && !(candidate.exact != null && BlockPos.containing(candidate.exact) == spot)) {
            val again = UltraArrivalSearch.refusal(candidate.level, spot, candidate.context)
            if (again != null) {
                log("{} transfer of {}: {} no longer safe ({}), looking again", transfer.kind, transfer.player, spot.toShortString(), again)
                transfer.spot = null
                return
            }
        }
        arrive(server, transfer, player, candidate, spot)
    }

    /** A safe spot at the candidate (its exact place first, when it has one); null when refused (the next one is tried). */
    private fun search(transfer: Transfer, candidate: Candidate): BlockPos? {
        candidate.exact?.let { exact ->
            val feet = BlockPos.containing(exact)
            for (dy in 0..1) for (dx in -2..2) for (dz in -2..2) {
                val pos = feet.offset(dx, dy, dz)
                if (UltraArrivalSearch.refusal(candidate.level, pos, candidate.context) == null) return pos
            }
        }
        val (feet, reason) = UltraArrivalSearch.find(candidate.level, candidate.x, candidate.z, candidate.radius, candidate.step, candidate.context)
        if (feet == null) refuse(transfer, candidate, reason ?: "no safe spot")
        return feet
    }

    /** This place is refused: its point waits, the next place is tried. */
    private fun refuse(transfer: Transfer, candidate: Candidate, reason: String) {
        log("{} transfer of {}: {} refused ({})", transfer.kind, transfer.player ?: "-", candidate.label, reason)
        candidate.point?.let { UltraArrivalPoints.of(candidate.level).refused(it, reason) }
        transfer.refused++
        release(transfer)
    }

    private fun release(transfer: Transfer) {
        transfer.current?.let { it.level.chunkSource.removeRegionTicket(TICKET, it.chunk, it.chunkRadius, it.chunk) }
        transfer.current = null
        transfer.spot = null
    }

    private fun next(transfer: Transfer): Candidate? {
        while (transfer.queue.isNotEmpty()) {
            transfer.queue.removeFirst()()?.let { return it }
        }
        return null
    }

    /** No place was safe: a small platform (or the player stays, by the config); for the return, at the world spawn. */
    private fun fallback(server: MinecraftServer, transfer: Transfer, player: ServerPlayer?) {
        if (transfer.kind == Kind.PREPARE) return finishPrepare(transfer)
        if (transfer.fallback) return fail(server, transfer, player, "no safe place, even the fallback")
        transfer.fallback = true
        release(transfer)
        transfer.queue.clear()
        val arrival = UltraWorldsConfig.settings.arrival
        val context = UltraArrivalSearch.Context(false, 0, arrival.maxDrop, 0, 0, 0, transfer.player)
        when {
            transfer.kind == Kind.RETURN -> {
                val overworld = server.overworld()
                val spawn = overworld.sharedSpawnPos
                transfer.queue.add { Candidate(overworld, spawn.x, spawn.z, 0, 1, context, null, "platform at the world spawn", platform = true) }
            }
            arrival.fallback == "return" -> return fail(server, transfer, player, "no safe place (fallback: stay)")
            else -> {
                val level = transfer.target?.let { server.getLevel(it) } ?: return fail(server, transfer, player, "world gone")
                val (x, z) = transfer.firstAnchor ?: UltraArrivalPoints.ideal(level, 0)
                transfer.queue.add { Candidate(level, x, z, 0, 1, context, null, "rescue platform", platform = true) }
            }
        }
        log("{} transfer of {}: no safe place, fallback", transfer.kind, transfer.player)
    }

    /**
     * A small platform at the top of the column, laid only where there is air, a fluid
     * or a plant (never instead of a solid block): the spot above it.
     */
    private fun platform(candidate: Candidate): BlockPos {
        val level = candidate.level
        if (UltraArrivalSearch.cavern(level)) return cavernRescue(candidate)
        val block = ResourceLocation.tryParse(UltraWorldsConfig.settings.arrival.platformBlock)
            ?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) } ?: Blocks.STONE
        val state = block.defaultBlockState()
        val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, candidate.x, candidate.z)
        val y = top.coerceIn(level.minBuildHeight + 6, level.maxBuildHeight - 4)
        for (dx in -2..2) for (dz in -2..2) {
            val pos = BlockPos(candidate.x + dx, y, candidate.z + dz)
            val there = level.getBlockState(pos)
            if (there.isAir || there.canBeReplaced() || !there.fluidState.isEmpty) level.setBlockAndUpdate(pos, state)
        }
        // Room for the body.
        for (dy in 1..2) {
            val pos = BlockPos(candidate.x, y + dy, candidate.z)
            val there = level.getBlockState(pos)
            if (!there.isAir && (there.canBeReplaced() || !there.fluidState.isEmpty)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())
        }
        GenerationsCore.LOGGER.warn("Ultra Space: rescue platform of {} laid in {} at {} {} {}", block, level.dimension().location(), candidate.x, y, candidate.z)
        return BlockPos(candidate.x, y + 1, candidate.z)
    }

    /**
     * Under a roof: the column's tallest hall when it has one (a platform laid on its
     * floor where there is air), else a small room dug at mid height (the rescue).
     */
    private fun cavernRescue(candidate: Candidate): BlockPos {
        val level = candidate.level
        val block = ResourceLocation.tryParse(UltraWorldsConfig.settings.arrival.platformBlock)
            ?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) } ?: Blocks.STONE
        val hall = UltraArrivalSearch.surface(level, candidate.x, candidate.z)
        val floor = hall?.y ?: (level.seaLevel + 30)
        for (dx in -2..2) for (dz in -2..2) {
            val under = BlockPos(candidate.x + dx, floor - 1, candidate.z + dz)
            val there = level.getBlockState(under)
            if (there.isAir || there.canBeReplaced() || !there.fluidState.isEmpty) level.setBlockAndUpdate(under, block.defaultBlockState())
            if (hall == null && dx in -1..1 && dz in -1..1) {
                // No hall at all: a room of 3 x 3 x 3 dug in the rock (never a block entity).
                for (dy in 0..2) {
                    val pos = BlockPos(candidate.x + dx, floor + dy, candidate.z + dz)
                    if (level.getBlockEntity(pos) == null) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())
                }
            }
        }
        GenerationsCore.LOGGER.warn("Ultra Space: cave rescue in {} at {} {} {} ({})", level.dimension().location(), candidate.x, floor, candidate.z, if (hall != null) "hall" else "dug room")
        return BlockPos(candidate.x, floor, candidate.z)
    }

    private fun loaded(candidate: Candidate): Boolean {
        val r = candidate.chunkRadius
        for (dx in -r..r) for (dz in -r..r) {
            if (candidate.level.chunkSource.getChunkNow(candidate.chunk.x + dx, candidate.chunk.z + dz) == null) return false
        }
        return true
    }

    /** Into the world, standing on the spot: out of any mount, Pokémon called back, protected, recorded. */
    private fun arrive(server: MinecraftServer, transfer: Transfer, player: ServerPlayer, candidate: Candidate, spot: BlockPos) {
        val level = candidate.level
        // Out of any mount, the party's Pokémon back in their balls (Cobblemon also does it on a change of dimension).
        player.stopRiding()
        player.party().forEach { pokemon -> pokemon.entity?.recallWithAnimation() }
        // Where they stood, for the return: not replaced when coming from another Ultra Space
        // world, nor by a rift. A real journey records it at its start (UltraWarpRide.beginRide);
        // this is for the test arrivals and the saves made before.
        if (transfer.kind != Kind.RETURN && transfer.kind != Kind.RIFT && !UltraWorlds.isUltra(player.level().dimension())) {
            val home = UltraWarpTravellers.Place.of(player)
            UltraWarpTravellers.edit(server, player.uuid) { it.home = home }
        }
        val exact = candidate.exact?.takeIf { BlockPos.containing(it) == spot }
        val x = exact?.x ?: (spot.x + 0.5)
        val y = exact?.y ?: spot.y.toDouble()
        val z = exact?.z ?: (spot.z + 0.5)
        player.teleportTo(level, x, y, z, candidate.yaw ?: player.yRot, candidate.pitch ?: 0f)
        if (player.level().dimension() != level.dimension()) {
            // Another mod refused the change of dimension.
            return fail(server, transfer, player, "the change of dimension was refused")
        }
        player.fallDistance = 0f
        player.deltaMovement = Vec3.ZERO
        protectedUntil[player.uuid] = server.tickCount + (UltraWorldsConfig.settings.protection.seconds * 20).toInt()
        candidate.point?.let { UltraArrivalPoints.of(level).used(it, spot) }
        release(transfer)
        transfers.remove(player.uuid)

        val world = UltraWorlds.idOf(level.dimension())
        val outcome = transfer.outcome
        when (transfer.kind) {
            Kind.ARRIVAL -> if (outcome != null) {
                outcome.arrival = if (candidate.platform) UltraWarpOutcome.Arrival.PLATFORM else UltraWarpOutcome.Arrival.ARRIVED
                outcome.world = level.dimension().location().toString()
                outcome.arrivalPoint = candidate.point?.index ?: -1
                outcome.arrivalX = spot.x
                outcome.arrivalY = spot.y
                outcome.arrivalZ = spot.z
                UltraWarpRecords.updateOutcome(server, outcome)
                UltraWarpTravellers.edit(server, player.uuid) {
                    it.pending = null
                    it.stay = outcome
                }
                // Phase 5: the journey's Pokémon, drawn now, appearing near the player.
                generations.gg.generations.core.generationscore.common.world.ultraspace.encounter.UltraEncounters.onArrival(server, player, outcome, level, spot)
            }
            Kind.RETURN -> UltraWarpTravellers.edit(server, player.uuid) {
                it.stay = null
                it.pendingReturn = false
            }
            // A rift: nothing else (no outcome, no encounter, no record).
            Kind.RIFT -> UltraWarpTravellers.edit(server, player.uuid) { it.pendingRift = null }
            else -> {}
        }
        val name = world?.let { worldName(it) } ?: Component.literal(level.dimension().location().toString())
        player.sendSystemMessage(
            when {
                transfer.kind == Kind.RETURN -> Component.translatable("generations_core.ultra_warp.world.returned")
                transfer.kind == Kind.RIFT -> Component.translatable("generations_core.ultra_rift.arrived", name)
                candidate.platform -> Component.translatable("generations_core.ultra_warp.world.platform", name)
                else -> Component.translatable("generations_core.ultra_warp.world.arrived", name)
            }
        )
        S2CUltraWarpTransferPacket(S2CUltraWarpTransferPacket.State.ARRIVED, transfer.kind, world ?: "", world?.let { colour(it) } ?: 0xFFFFFF, outcome).sendToPlayer(player)
        GenerationsCore.LOGGER.info(
            "Ultra Space: {} {} {} at {} ({}{}){}",
            player.gameProfile.name, if (transfer.kind == Kind.RETURN) "came back to" else "arrived in", level.dimension().location(), spot.toShortString(),
            candidate.label, if (candidate.platform) ", rescue platform" else "", outcome?.let { ", journey ${it.journey}" } ?: "",
        )
    }

    /** No arrival: the player stays where they are (told why), the journey's outcome says so. */
    private fun fail(server: MinecraftServer, transfer: Transfer, player: ServerPlayer?, reason: String) {
        release(transfer)
        transfer.player?.let { transfers.remove(it) }
        prepares.remove(transfer)
        GenerationsCore.LOGGER.warn("Ultra Space: {} transfer of {} to {} failed: {}", transfer.kind, transfer.player ?: "-", transfer.target?.location() ?: "home", reason)
        val outcome = transfer.outcome
        if (transfer.kind == Kind.ARRIVAL && outcome != null) {
            outcome.arrival = UltraWarpOutcome.Arrival.FAILED
            UltraWarpRecords.updateOutcome(server, outcome)
            UltraWarpTravellers.edit(server, outcome.player) { it.pending = null }
        }
        // A passage that failed is over: never resumed at the next login.
        if (transfer.player != null && (transfer.kind == Kind.RIFT || transfer.kind == Kind.RETURN)) {
            UltraWarpTravellers.edit(server, transfer.player) {
                if (transfer.kind == Kind.RIFT) it.pendingRift = null else it.pendingReturn = false
            }
        }
        if (player != null) {
            val world = transfer.target?.let { UltraWorlds.idOf(it) } ?: ""
            player.sendSystemMessage(
                when (transfer.kind) {
                    Kind.RETURN -> Component.translatable("generations_core.ultra_warp.world.return_failed")
                    Kind.RIFT -> Component.translatable("generations_core.ultra_rift.failed", worldName(world))
                    else -> Component.translatable("generations_core.ultra_warp.world.failed", worldName(world))
                }
            )
            S2CUltraWarpTransferPacket(S2CUltraWarpTransferPacket.State.FAILED, transfer.kind, world, colour(world), outcome).sendToPlayer(player)
        }
    }

    private fun finishPrepare(transfer: Transfer) {
        release(transfer)
        prepares.remove(transfer)
        val world = transfer.target?.let { UltraWorlds.idOf(it) } ?: "?"
        GenerationsCore.LOGGER.info("Ultra Space: arrival points of {}: {} safe, {} refused", world, transfer.found, transfer.refused)
        transfer.report?.invoke(Component.translatable("generations_core.ultra_warp.command.prepared", worldName(world), transfer.found, transfer.refused))
    }

    /** Stops the player's transfer, if any, without a word (they left, or a new one replaces it). */
    private fun cancel(player: UUID) {
        val transfer = transfers.remove(player) ?: return
        release(transfer)
        log("{} transfer of {} stopped", transfer.kind, player)
    }

    /** A world's (destination's) name, from the language files. */
    fun worldName(id: String): Component = Component.translatableWithFallback("generations_core.ultra_warp.destination.$id", id)

    /** The colour of the destination of this id (its portals'), white for none. */
    fun colour(id: String): Int = UltraSpaceConfig.ride.portals.destinations.firstOrNull { it.id == id }?.rgb ?: 0xFFFFFF

    private fun log(message: String, vararg args: Any?) {
        if (UltraWorldsConfig.settings.test.logSearch) GenerationsCore.LOGGER.info("Ultra Space: $message", *args)
    }
}
