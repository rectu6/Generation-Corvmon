package generations.gg.generations.core.generationscore.common.world.ultraspace

import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * The Ultra Warp Rides in progress, on the logical server: the integrated server in
 * solo (and on LAN), the dedicated server in multiplayer. One manager for both: one
 * session per player, at most maxSessions at once, no queue. The only place where a
 * session is created or ended, so its place is always given back. Never read from
 * the client: in solo it shares the JVM, and would seem to work there only.
 */
object UltraWarpSessions {
    private val sessions = LinkedHashMap<UUID, UltraWarpSession>()

    fun get(player: UUID): UltraWarpSession? = sessions[player]

    fun count(): Int = sessions.size

    fun freeSlots(): Int = (UltraSpaceConfig.ride.maxSessions - sessions.size).coerceAtLeast(0)

    /** The session of this player, or null when they have one already or no place is free. */
    fun create(player: ServerPlayer, variant: UltraWarpVariant, altar: BlockPos, source: UltraWarpSession.Source): UltraWarpSession? {
        if (player.uuid in sessions || freeSlots() <= 0) return null
        val session = UltraWarpSession(player.uuid, variant, player.level().dimension(), altar.immutable(), player.server.overworld().gameTime, source)
        sessions[player.uuid] = session
        GenerationsCore.LOGGER.info(
            "Ultra Warp Ride: {} started ({}, {}, at {} in {}), {}/{} sessions",
            player.gameProfile.name, variant.key, source, altar.toShortString(), session.dimension.location(), sessions.size, UltraSpaceConfig.ride.maxSessions,
        )
        return session
    }

    /** Up to count simulated sessions, while places are free; how many were added. */
    fun simulate(count: Int, dimension: ResourceKey<Level>, pos: BlockPos, gameTime: Long): Int {
        var added = 0
        while (added < count && freeSlots() > 0) {
            val id = UUID.randomUUID()
            sessions[id] = UltraWarpSession(id, UltraWarpVariant.SUN, dimension, pos.immutable(), gameTime, UltraWarpSession.Source.SIMULATED)
            added++
        }
        if (added > 0) GenerationsCore.LOGGER.info("Ultra Warp Ride: {} simulated sessions added, {}/{} sessions", added, sessions.size, UltraSpaceConfig.ride.maxSessions)
        return added
    }

    /** Told of every session that ends (UltraWarpRide: stops the player's journey on their client). */
    var onEnded: ((UltraWarpSession, UltraWarpSession.EndReason) -> Unit)? = null

    /** Ends the player's session and frees its place; null when they had none. */
    fun end(player: UUID, reason: UltraWarpSession.EndReason): UltraWarpSession? {
        val session = sessions.remove(player) ?: return null
        try {
            session.onEnd(reason)
            onEnded?.invoke(session, reason)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Warp Ride: ending the session of {} failed", player, e)
        }
        if (!session.simulated) GenerationsCore.LOGGER.info("Ultra Warp Ride: session of {} ended ({}), {}/{} sessions", player, reason, sessions.size, UltraSpaceConfig.ride.maxSessions)
        return session
    }

    /** Ends every simulated session; how many there were. */
    fun clearSimulated(): Int {
        val simulated = sessions.values.filter { it.simulated }.map { it.player }
        simulated.forEach { end(it, UltraWarpSession.EndReason.FINISHED) }
        if (simulated.isNotEmpty()) GenerationsCore.LOGGER.info("Ultra Warp Ride: {} simulated sessions removed, {}/{} sessions", simulated.size, sessions.size, UltraSpaceConfig.ride.maxSessions)
        return simulated.size
    }

    fun endAll(reason: UltraWarpSession.EndReason) {
        sessions.keys.toList().forEach { end(it, reason) }
    }

    fun all(): List<UltraWarpSession> = sessions.values.toList()
}
