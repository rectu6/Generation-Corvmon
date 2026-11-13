package generations.gg.generations.core.generationscore.common.world.ultraspace.rift

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.util.party
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraRiftPacket
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpSessions
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTransfers
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.Random
import java.util.UUID

/**
 * The rifts between the Ultra worlds (phase 6), all decided by the server: where and
 * when one opens, where it leads, how long it lasts, who goes through. On the server
 * thread only, so taking a rift is atomic: the first crossing wins, nobody else can.
 *
 * - Opening: now and then, in a world with players, near one of them, in chunks already
 *   loaded (UltraRiftPlacement); limited per world and around a player.
 * - Crossing: on Solgaleo or Lunala really ridden and steered by the player (checked
 *   here), their move since the last tick going through the middle of the opening.
 * - Closing: at its time (the clients see it close), or once crossed (it collapses).
 * - Saved with the save (UltraRiftSavedData); the clients get their world's rifts.
 */
object UltraRifts {
    private class Track(val world: String, val tick: Int, val rider: Vec3, val mount: Vec3?)

    private var server: MinecraftServer? = null
    private val random = Random()
    /** Each player's position at the last tick, near a rift. */
    private val tracks = HashMap<UUID, Track>()
    /** The world whose rifts each player's client has ("" for none). */
    private val synced = HashMap<UUID, String>()
    /** A rift used more than once (singleUse off): not again by the same player before this tick. */
    private val cooldown = HashMap<UUID, Int>()
    /** Why the last opening failed, per world (/ultrawarp rift diagnose). */
    val lastAttempt = HashMap<String, String>()

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) {
            server = it.server
            UltraRiftsConfig.load()
            val data = UltraRiftSavedData.get(it.server)
            log("{} rifts kept from the last session", data.rifts.size)
        }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) {
            tracks.clear()
            synced.clear()
            cooldown.clear()
            lastAttempt.clear()
            server = null
        }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(Priority.NORMAL) {
            tracks.remove(it.player.uuid)
            synced.remove(it.player.uuid)
        }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) {
            try {
                tick(it.server)
            } catch (e: Exception) {
                GenerationsCore.LOGGER.error("Ultra Space: rifts tick failed", e)
            }
        }
    }

    fun data(server: MinecraftServer): UltraRiftSavedData = UltraRiftSavedData.get(server)

    fun open(server: MinecraftServer): List<UltraRift> = data(server).rifts.filter { !it.taken }

    private fun tick(server: MinecraftServer) {
        val ticks = server.tickCount
        val now = server.overworld().gameTime
        val data = data(server)
        if (ticks % 10 == 0) expire(server, data, now)
        if (ticks % 20 == 0) sync(server, data, now)
        val config = UltraRiftsConfig.rifts
        if (config.enabled) {
            val interval = config.attemptIntervalSeconds * 20
            config.dimensions.forEachIndexed { index, world ->
                if ((ticks + index * 37) % interval == 0) attempt(server, data, world, now)
            }
        }
        if (data.rifts.isNotEmpty()) crossings(server, data, now, ticks)
        if (ticks % 200 == 0) {
            tracks.entries.removeIf { ticks - it.value.tick > 40 }
            cooldown.entries.removeIf { it.value < ticks }
        }
    }

    // ---- Opening ------------------------------------------------------------------

    private fun attempt(server: MinecraftServer, data: UltraRiftSavedData, world: String, now: Long) {
        val level = UltraWorlds.key(world)?.let { server.getLevel(it) } ?: return
        val players = level.players().filter { !it.isSpectator && it.isAlive }
        if (players.isEmpty()) return
        val config = UltraRiftsConfig.rifts
        val inWorld = data.rifts.filter { it.world == world && !it.taken }
        if (inWorld.size >= config.maxPerDimension) {
            lastAttempt[world] = "maximum reached (${inWorld.size})"
            return
        }
        if (random.nextFloat() >= config.spawnChance) return
        val player = players[random.nextInt(players.size)]
        tryNear(server, data, level, world, player, now)
    }

    /** An opening near this player, by the rules; the reason when none. */
    fun tryNear(server: MinecraftServer, data: UltraRiftSavedData, level: ServerLevel, world: String, player: ServerPlayer, now: Long): String {
        val config = UltraRiftsConfig.rifts
        val inWorld = data.rifts.filter { it.world == world && !it.taken }
        val radius = config.nearPlayerRadius.toDouble()
        if (inWorld.count { horizontalSqr(it, player) < radius * radius } >= config.maxNearPlayer) {
            return "enough rifts near ${player.gameProfile.name}".also { lastAttempt[world] = it }
        }
        val destination = pickDestination(server, world) ?: return "no destination open".also { lastAttempt[world] = it }
        val (spot, reason) = UltraRiftPlacement.near(level, player, random, inWorld)
        if (spot == null) {
            lastAttempt[world] = reason
            log("no rift near {} in {}: {}", player.gameProfile.name, world, reason)
            return reason
        }
        val rift = create(server, data, world, spot, destination, (UltraRiftsConfig.rifts.lifetimeSeconds.pick(random) * 20).toLong(), now)
        lastAttempt[world] = "opened #${rift.id}"
        return "ok"
    }

    /** A new rift there, told to the players of its world. */
    fun create(server: MinecraftServer, data: UltraRiftSavedData, world: String, spot: UltraRiftPlacement.Spot, destination: String, lifeTicks: Long, now: Long): UltraRift {
        val rift = UltraRift(data.nextId++, world, spot.x, spot.y, spot.z, spot.yaw, spot.width, spot.height, random.nextLong(), destination, now, now + lifeTicks)
        data.rifts.add(rift)
        data.setDirty()
        broadcast(server, world, S2CUltraRiftPacket(S2CUltraRiftPacket.Action.ADD, listOf(view(rift, now)), rift.id))
        GenerationsCore.LOGGER.info("Ultra Space: rift {} opened ({} s)", rift, lifeTicks / 20)
        return rift
    }

    /** A destination by the weights: open and loaded worlds only, not its own world by default. */
    fun pickDestination(server: MinecraftServer, world: String): String? {
        val config = UltraRiftsConfig.rifts
        val choices = config.destinationWeights.filter { (id, weight) ->
            weight > 0f && (config.allowSameDimension || id != world) && UltraWorlds.level(server, UltraWorlds.key(id)) != null
        }
        val total = choices.values.sum()
        if (total <= 0f) return null
        var roll = random.nextFloat() * total
        for ((id, weight) in choices) {
            roll -= weight
            if (roll <= 0f) return id
        }
        return choices.keys.last()
    }

    // ---- Closing ------------------------------------------------------------------

    private fun expire(server: MinecraftServer, data: UltraRiftSavedData, now: Long) {
        if (data.rifts.isEmpty()) return
        val enabled = UltraRiftsConfig.rifts.enabled
        data.rifts.toList().forEach { rift ->
            val reason = when {
                rift.taken -> null
                now >= rift.expires -> "expired"
                !enabled -> "rifts turned off"
                rift.world !in UltraWorlds.IDS || UltraWorlds.key(rift.world)?.let { server.getLevel(it) } == null -> "its world is gone"
                UltraWorlds.level(server, UltraWorlds.key(rift.destination)) == null -> "its destination is closed"
                else -> null
            }
            if (reason != null) close(server, data, rift, reason)
        }
    }

    /** Gone: collision off at once, the clients see it close. */
    fun close(server: MinecraftServer, data: UltraRiftSavedData, rift: UltraRift, reason: String) {
        rift.taken = true
        data.rifts.remove(rift)
        data.setDirty()
        broadcast(server, rift.world, S2CUltraRiftPacket(S2CUltraRiftPacket.Action.CLOSE, emptyList(), rift.id))
        GenerationsCore.LOGGER.info("Ultra Space: rift {} closed ({})", rift, reason)
    }

    // ---- Crossing -----------------------------------------------------------------

    private fun crossings(server: MinecraftServer, data: UltraRiftSavedData, now: Long, ticks: Int) {
        val config = UltraRiftsConfig.rifts
        val byWorld = data.rifts.filter { !it.taken }.groupBy { it.world }
        for ((world, rifts) in byWorld) {
            val level = UltraWorlds.key(world)?.let { server.getLevel(it) } ?: continue
            for (player in level.players()) {
                if (player.isSpectator || !player.isAlive) continue
                val near = rifts.filter { rift ->
                    val reach = 24.0 + maxOf(rift.width, rift.height)
                    player.distanceToSqr(rift.x, rift.y, rift.z) < reach * reach
                }
                if (near.isEmpty()) continue
                val mount = mountOf(player)
                val rider = rider(player)
                val previous = tracks.put(player.uuid, Track(world, ticks, rider, mount?.boundingBox?.center))
                if (previous == null || previous.world != world || previous.tick != ticks - 1) continue
                if (mount == null || previous.mount == null) continue
                if (previous.rider.distanceToSqr(rider) > config.maxStepPerTick * config.maxStepPerTick) continue
                if (UltraWarpTransfers.busy(player.uuid) || UltraWarpSessions.get(player.uuid) != null) continue
                if ((cooldown[player.uuid] ?: -1) >= ticks) continue
                val mountNow = mount.boundingBox.center
                val rift = near.firstOrNull { !it.taken && crossed(it, previous.rider, rider, previous.mount, mountNow, config.innerFraction.toDouble()) } ?: continue
                use(server, data, rift, player, now, ticks)
            }
        }
    }

    /** The point that has to go through: the rider's body (on the mount's back). */
    private fun rider(player: ServerPlayer): Vec3 = player.position().add(0.0, 0.6, 0.0)

    /** Solgaleo or Lunala (by the config), ridden and steered by this player, theirs, in their party. */
    fun mountOf(player: ServerPlayer): PokemonEntity? {
        val mount = player.vehicle as? PokemonEntity ?: return null
        if (mount.controllingPassenger != player) return null
        val pokemon = mount.pokemon
        if (pokemon.getOwnerUUID() != player.uuid) return null
        if (player.party().none { it.uuid == pokemon.uuid }) return null
        if (pokemon.species.resourceIdentifier.toString() !in UltraRiftsConfig.rifts.mounts) return null
        return mount
    }

    /**
     * The move from a to b crosses the rift's plane inside the middle part of its
     * opening, and the mount is in the opening at that moment too: the edges, a wing,
     * passing by never count.
     */
    fun crossed(rift: UltraRift, a: Vec3, b: Vec3, mountA: Vec3?, mountB: Vec3?, fraction: Double): Boolean {
        val da = (a.x - rift.x) * rift.normalX + (a.z - rift.z) * rift.normalZ
        val db = (b.x - rift.x) * rift.normalX + (b.z - rift.z) * rift.normalZ
        if (da == 0.0 || !((da > 0 && db <= 0) || (da < 0 && db >= 0))) return false
        val t = da / (da - db)
        if (!insideAt(rift, a, b, t, fraction)) return false
        if (mountA == null || mountB == null) return true
        return insideAt(rift, mountA, mountB, t, 1.15)
    }

    private fun insideAt(rift: UltraRift, a: Vec3, b: Vec3, t: Double, fraction: Double): Boolean {
        val x = a.x + (b.x - a.x) * t
        val y = a.y + (b.y - a.y) * t
        val z = a.z + (b.z - a.z) * t
        val u = (x - rift.x) * rift.rightX + (z - rift.z) * rift.rightZ
        val v = y - rift.y
        return UltraRiftShape.inside(rift.outline, u, v, fraction)
    }

    /**
     * Through it: taken at once (nobody else can), collapsing for everyone, the passage
     * begins (UltraWarpTransfers, kind RIFT: never the journey's rewards).
     */
    fun use(server: MinecraftServer, data: UltraRiftSavedData, rift: UltraRift, player: ServerPlayer, now: Long, ticks: Int): Boolean {
        val key = UltraWorlds.key(rift.destination)
        if (UltraWorlds.level(server, key) == null) {
            close(server, data, rift, "its destination is closed")
            player.sendSystemMessage(Component.translatable("generations_core.ultra_rift.unstable"))
            return false
        }
        val config = UltraRiftsConfig.rifts
        if (config.singleUse) {
            rift.taken = true
            data.rifts.remove(rift)
            data.setDirty()
            broadcast(server, rift.world, S2CUltraRiftPacket(S2CUltraRiftPacket.Action.COLLAPSE, emptyList(), rift.id))
        } else {
            cooldown[player.uuid] = ticks + 100
        }
        val hold = (config.transitionSeconds * 20).toInt()
        val started = UltraWarpTransfers.rift(player, rift.destination, rift.x, rift.z, hold)
        GenerationsCore.LOGGER.info("Ultra Space: {} went through rift {}{}", player.gameProfile.name, rift, if (started) "" else " (the passage could not begin)")
        if (!started) player.sendSystemMessage(Component.translatable("generations_core.ultra_rift.failed", UltraWarpTransfers.worldName(rift.destination)))
        return started
    }

    // ---- The clients ---------------------------------------------------------------

    fun view(rift: UltraRift, now: Long) = UltraRiftView(
        rift.id, rift.x, rift.y, rift.z, rift.yaw, rift.width, rift.height, rift.seed, UltraWarpTransfers.colour(rift.destination),
        (now - rift.opened).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), (rift.expires - now).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
    )

    /** Each player's client has the rifts of the world they are in (sent again when they change world). */
    private fun sync(server: MinecraftServer, data: UltraRiftSavedData, now: Long) {
        for (player in server.playerList.players) {
            val world = UltraWorlds.idOf(player.level().dimension()) ?: ""
            if (synced[player.uuid] == world) continue
            synced[player.uuid] = world
            val list = if (world.isEmpty()) emptyList() else data.rifts.filter { it.world == world && !it.taken }.map { view(it, now) }
            S2CUltraRiftPacket(S2CUltraRiftPacket.Action.FULL, list, 0).sendToPlayer(player)
        }
    }

    private fun broadcast(server: MinecraftServer, world: String, packet: S2CUltraRiftPacket) {
        val level = UltraWorlds.key(world)?.let { server.getLevel(it) } ?: return
        level.players().forEach { if (synced[it.uuid] == world) packet.sendToPlayer(it) }
    }

    private fun horizontalSqr(rift: UltraRift, player: ServerPlayer): Double {
        val dx = rift.x - player.x
        val dz = rift.z - player.z
        return dx * dx + dz * dz
    }

    private fun log(message: String, vararg args: Any?) {
        if (UltraRiftsConfig.settings.test.logRifts) GenerationsCore.LOGGER.info("Ultra Space: $message", *args)
    }
}
