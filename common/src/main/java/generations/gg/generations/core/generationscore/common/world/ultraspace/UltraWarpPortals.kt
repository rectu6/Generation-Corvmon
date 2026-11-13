package generations.gg.generations.core.generationscore.common.world.ultraspace

import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWarpPortalPacket
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWarpRideEventPacket
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpPortal
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpRideLimits
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpRideState
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTransfers
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorldsConfig
import net.minecraft.server.level.ServerPlayer
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The journey's dimensional portals, on the server (the authority, also in solo):
 * - placed leadDistance ahead (beyond what the client has built), from
 *   firstLightYears on, one every minSpacing to maxSpacing blocks, maxVisible at most,
 *   sometimes several together (convergence); across the tunnel within its limits,
 *   in the middle or on a side, always leaving a free way past them (no portal has to
 *   be taken); nothing of the course just before them;
 * - destination (weights) and rarity level (the tier of light-years there) drawn
 *   apart, here only, and told to the client, which draws exactly that;
 * - activation like an orb's pick-up, but on purpose: a portal does not pull nor slow
 *   anything. Only the Pokémon's centre (the middle of its body, the point the player
 *   steers, not its wings) going through the portal's opening counts: between two
 *   accepted positions, the portal's plane crossed (its distance between the two) and
 *   the centre, where it crossed, within triggerFraction of the opening's drawn
 *   radius (the vortex, not the rings around it). Grazing it, or flying through its
 *   rings and light, does nothing; however fast, a crossing is never missed. Then the
 *   trip through (cinematicSeconds) and the journey ends: SUCCESS.
 * When several openings are crossed at once (a convergence), the one whose middle was
 * nearest wins, then the lowest id.
 */
object UltraWarpPortals {
    fun tick(session: UltraWarpSession, player: ServerPlayer, ride: UltraWarpRideState, now: Long) {
        val config = ride.settings.config.portals
        when (session.stage) {
            UltraWarpSession.Stage.RIDE -> {
                if (config.enabled) spawn(ride, player)
                // Left behind.
                ride.portals.filter { it.open && it.distance < ride.sampleDistance - 2.0 }.forEach { it.state = UltraWarpPortal.State.PASSED }
            }
            UltraWarpSession.Stage.CAPTURE ->
                if (now - ride.captureTick >= (config.cinematicSeconds * 20).toLong()) {
                    UltraWarpSessions.end(session.player, UltraWarpSession.EndReason.SUCCESS)
                }
            else -> {}
        }
    }

    /** The radius of a portal's opening that counts: triggerFraction of the opening drawn at its level's size. */
    fun opening(ride: UltraWarpRideState, portal: UltraWarpPortal): Double {
        val portals = ride.settings.config.portals
        return portals.radius * portals.levels[portal.rarity.coerceIn(1, 4) - 1].size * portals.triggerFraction.toDouble()
    }

    /** The largest opening that counts, whatever the level (for spacing portals apart). */
    private fun largestOpening(ride: UltraWarpRideState): Double {
        val portals = ride.settings.config.portals
        return portals.radius * (portals.levels.maxOfOrNull { it.size } ?: 1f) * portals.triggerFraction.toDouble()
    }

    /**
     * With each position the server accepted (UltraWarpGameplay.onState): whether the
     * Pokémon's centre went through a portal's opening on its way from (d0, x0, y0) to
     * (d1, x1, y1) (distance along the tunnel, place across it); if so, it goes in.
     */
    fun cross(session: UltraWarpSession, player: ServerPlayer, ride: UltraWarpRideState, d0: Double, x0: Double, y0: Double, d1: Double, x1: Double, y1: Double, now: Long) {
        if (session.stage != UltraWarpSession.Stage.RIDE || ride.captured != null || d1 <= d0) return
        val crossed = ride.portals.mapNotNull { portal ->
            if (!portal.open || portal.distance <= d0 || portal.distance > d1) return@mapNotNull null
            // Where the centre was as it crossed the portal's plane.
            val t = (portal.distance - d0) / (d1 - d0)
            val x = x0 + (x1 - x0) * t
            val y = y0 + (y1 - y0) * t
            val across = UltraWarpRideLimits.length(x - portal.x, y - portal.y)
            if (across <= opening(ride, portal)) portal to across else null
        }.minWithOrNull(compareBy<Pair<UltraWarpPortal, Double>> { it.second }.thenBy { it.first.id })?.first ?: return
        capture(session, player, ride, crossed, now)
    }

    /** The next portal (or group) when it comes within leadDistance, if fewer than maxVisible are ahead. */
    private fun spawn(ride: UltraWarpRideState, player: ServerPlayer) {
        val config = ride.settings.config
        val portals = config.portals
        val ahead = ride.distance + portals.leadDistance
        if (ride.nextPortal <= 0.0) ride.nextPortal = max(portals.firstLightYears / config.lightYearsPerBlock.toDouble(), ahead)
        if (ahead < ride.nextPortal) return
        if (ride.portals.count { it.open } >= portals.maxVisible) return
        val random = ride.portalRandom
        val count = if (random.nextDouble() < portals.convergenceChance) random.nextInt(portals.convergenceMin, portals.convergenceMax + 1) else 1
        place(ride, player, ride.nextPortal, count, null, null)
        ride.nextPortal += portals.minSpacing + (portals.maxSpacing - portals.minSpacing) * random.nextDouble()
    }

    /**
     * Places count portals at this distance (a convergence when more than one), with
     * this destination and level or drawn ones. Fewer when they do not fit with a free
     * way left. Returns them.
     */
    fun place(ride: UltraWarpRideState, player: ServerPlayer, distance: Double, count: Int, destination: String?, level: Int?): List<UltraWarpPortal> {
        val config = ride.settings.config
        val portals = config.portals
        val spots = spots(ride, distance, count) ?: return emptyList()
        val group = if (spots.size > 1) ride.nextPortalId else 0
        return spots.map { (x, y) ->
            val target = portals.destinations.firstOrNull { it.id == destination } ?: drawDestination(ride, player)
            val lightYears = distance * config.lightYearsPerBlock
            val (rarity, roll) = level?.let { it.coerceIn(1, 4) to -1.0 } ?: drawRarity(ride, lightYears)
            UltraWarpPortal(ride.nextPortalId++, distance, x, y, target.id, target.rgb, rarity, group).also { portal ->
                ride.portals.add(portal)
                // Nothing of the course just before it nor at it: the way in is free.
                ride.course.clear(distance - portals.clearBefore, distance + 8.0)
                S2CUltraWarpPortalPacket(portal).sendToPlayer(player)
                val tier = portals.rarity.last { it.fromLightYears <= lightYears }
                GenerationsCore.LOGGER.info(
                    "Ultra Warp Ride: {} portal #{} to {} level {} at {} ly ({}; rarity table from {} ly: {}{})",
                    player.gameProfile.name, portal.id, portal.destination, portal.rarity, "%.1f".format(lightYears),
                    if (group != 0) "convergence $group" else "alone", tier.fromLightYears, tier.weights.joinToString("/"),
                    if (roll >= 0) ", roll %.2f".format(roll) else ", chosen",
                )
            }
        }
    }

    /** A destination by the config's weights; none whose world is closed (unless all are). */
    private fun drawDestination(ride: UltraWarpRideState, player: ServerPlayer): UltraSpaceConfig.Destination {
        val weighted = ride.settings.config.portals.destinations.filter { it.weight > 0f }
        val open = if (UltraWorldsConfig.settings.hidePortalsOfDisabledWorlds) weighted.filter { UltraWorlds.available(player.server, it.id) } else weighted
        val destinations = open.ifEmpty { weighted }
        var roll = ride.portalRandom.nextDouble() * destinations.sumOf { it.weight.toDouble() }
        for (destination in destinations) {
            roll -= destination.weight
            if (roll < 0) return destination
        }
        return destinations.last()
    }

    /** A rarity level (1 to 4) by the tier of these light-years, and the roll (0 to the weights' sum). */
    private fun drawRarity(ride: UltraWarpRideState, lightYears: Double): Pair<Int, Double> {
        val tier = ride.settings.config.portals.rarity.last { it.fromLightYears <= lightYears }
        val total = tier.weights.sum().toDouble()
        val roll = ride.portalRandom.nextDouble() * total
        var left = roll
        tier.weights.forEachIndexed { i, weight ->
            left -= weight
            if (left < 0) return (i + 1) to roll
        }
        return 1 to roll
    }

    /**
     * Places for count portals across the tunnel at this distance: where the
     * Pokémon's centre can go, their openings apart, and a free way past all of them.
     * Tries several turns, then fewer portals; null when not even one fits.
     */
    private fun spots(ride: UltraWarpRideState, distance: Double, count: Int): List<Pair<Double, Double>>? {
        val random = ride.portalRandom
        val opening = largestOpening(ride)
        for (n in count downTo 1) {
            repeat(24) {
                val turn = random.nextDouble() * 2 * Math.PI
                val spots = (0 until n).map { i ->
                    val angle = turn + i * 2 * Math.PI / n + (random.nextDouble() - 0.5) * 0.4
                    val nx = StrictMath.cos(angle)
                    val ny = StrictMath.sin(angle)
                    val reach = UltraWarpRideLimits.allowed(ride.tunnel, distance, ride.shape, nx, ny)
                    // Alone: anywhere from the middle to a side; together: around the middle.
                    val r = (if (n == 1) sqrt(random.nextDouble()) * 0.85 else 0.55 + 0.3 * random.nextDouble()) * reach
                    nx * r to ny * r
                }
                val apart = spots.indices.all { a -> spots.indices.all { b -> a >= b || dist(spots[a], spots[b]) >= opening * 2 + 3 } }
                if (apart && hasFreeWay(ride, distance, spots, opening)) return spots
            }
        }
        return null
    }

    /** Whether some place the Pokémon's centre can reach at this distance stays clear of every opening. */
    private fun hasFreeWay(ride: UltraWarpRideState, distance: Double, spots: List<Pair<Double, Double>>, opening: Double): Boolean {
        for (ring in listOf(0.0, 0.35, 0.7, 1.0)) {
            for (i in 0 until 16) {
                val angle = i * Math.PI / 8
                val nx = StrictMath.cos(angle)
                val ny = StrictMath.sin(angle)
                val r = ring * UltraWarpRideLimits.allowed(ride.tunnel, distance, ride.shape, nx, ny)
                val p = nx * r to ny * r
                if (spots.all { dist(it, p) > opening + 1.5 }) return true
            }
        }
        return false
    }

    private fun dist(a: Pair<Double, Double>, b: Pair<Double, Double>) = UltraWarpRideLimits.length(a.first - b.first, a.second - b.second)

    /** Into the portal: once; no more energy, collisions or steering; the trip through, then SUCCESS. */
    private fun capture(session: UltraWarpSession, player: ServerPlayer, ride: UltraWarpRideState, portal: UltraWarpPortal, now: Long) {
        if (session.stage != UltraWarpSession.Stage.RIDE || ride.captured != null) return
        session.beginCapture()
        portal.state = UltraWarpPortal.State.CAPTURED
        ride.captured = portal
        ride.captureTick = now
        val cinematic = ride.settings.config.portals.cinematicSeconds
        S2CUltraWarpRideEventPacket(S2CUltraWarpRideEventPacket.Type.PORTAL_CAPTURE, portal.id, cinematic).sendToPlayer(player)
        // Its world starts loading now: the cinematic gives the chunks time.
        UltraWarpTransfers.prepare(player, session.journey, portal.destination)
        GenerationsCore.LOGGER.info(
            "Ultra Warp Ride: {} went through portal #{} to {} (level {}) at {} ly",
            player.gameProfile.name, portal.id, portal.destination, portal.rarity, "%.1f".format(ride.lightYears),
        )
    }
}
