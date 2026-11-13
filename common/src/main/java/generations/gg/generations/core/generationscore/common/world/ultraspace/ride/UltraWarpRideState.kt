package generations.gg.generations.core.generationscore.common.world.ultraspace.ride

/**
 * A journey as the server keeps it (the authority, also in solo): distance and speed
 * along the tunnel, energy, the objects already taken or touched (each counts once),
 * the last position the client sent and the server accepted. The tunnel and the
 * course are built from the seed like the client's.
 */
class UltraWarpRideState(val settings: UltraWarpRideSettings, val seed: Long) {
    val tunnel = UltraWarpTunnel(seed, settings)
    val course = UltraWarpCourse(seed, tunnel, settings)
    val shape = UltraWarpRideLimits.shape(settings)
    val hitbox = UltraWarpRideLimits.hitbox(settings)

    /** Blocks travelled along the tunnel, and current speed (blocks per second). */
    var distance = 0.0
    var speed = settings.speed.toDouble()
    var energy = settings.config.energy.start.toDouble()

    /** Last position accepted: distance along the tunnel, offset across it. */
    var sampleDistance = 0.0
    var x = 0.0
    var y = 0.0

    /** Objects taken or touched (their ids): never twice. */
    val consumed = HashSet<Int>()

    /** Server tick until which nothing hurts (after a hit). */
    var invulnerableUntil = 0L

    // What happened, for the end of the journey and /ultrawarp sessions.
    var orbs = 0
    var hits = 0
    var gaps = 0
    var boosting = false
    var dodges = 0

    /** Server tick of the last position received (one per tick at most). */
    var lastState = Long.MIN_VALUE

    val lightYears: Double get() = distance * settings.config.lightYearsPerBlock

    /** Events: their own draws, when each may come again (server tick), the next window's id, Necrozma seen. */
    val eventRandom = java.util.SplittableRandom(seed xor 0x5EEDE7E7L)
    val cooldownUntil = HashMap<String, Long>()
    var nextWindow = 1
    var necrozmaSeen = false

    /** Last difficulty tier written to the log. */
    var loggedTier = -1

    /** Portals placed (told to the client), the next one's distance and id, their own draws. */
    val portals = ArrayList<UltraWarpPortal>()
    var nextPortal = 0.0
    var nextPortalId = 1
    val portalRandom = java.util.SplittableRandom(seed xor 0x9047A15L)

    /** The portal the Pokémon went through (at captureTick). */
    var captured: UltraWarpPortal? = null
    var captureTick = 0L

    /** Defeat: when it began (server tick), and whether the player was struck down (once). */
    var defeatTick = -1L
    var killSent = false
    var killTick = -1L
}
