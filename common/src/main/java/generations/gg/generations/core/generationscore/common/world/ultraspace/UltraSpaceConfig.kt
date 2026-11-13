package generations.gg.generations.core.generationscore.common.world.ultraspace

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpDifficulty
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpRideSettings
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Ultra Space settings of the server: config/generations/core/ultra_space/ultra_warp_ride.json,
 * read at each server start (each world opened in solo) and by /ultrawarp reload. The
 * file is written back with every option, so that new ones show up; values out of
 * range are brought back into it. A file with an error keeps the defaults (logged).
 * A journey keeps the settings it started with (a copy, sent to its client), so a
 * reload only changes the next journeys. What only changes the look (camera, colours)
 * is in the client's own file.
 */
object UltraSpaceConfig {
    class Ride {
        /** Version of the file: older ones are brought up to date (see load). */
        var version: Int = 0
        /** Ultra Warp Rides at the same time on the server, one player each; 0 turns them off. */
        var maxSessions: Int = 6
        /** Initial speed of the journey, in blocks per second (the difficulty tiers multiply it). */
        var speed: Float = 24f
        /** Light-years counted per block travelled. */
        var lightYearsPerBlock: Float = 0.05f
        var tunnel = Tunnel()
        var steering = Steering()
        var riders = Riders()
        var limits = Limits()
        var energy = Energy()
        var objects = Objects()
        var difficulty = Difficulty()
        /** The difficulty of every journey on this server: the id of one of the modes below. */
        var mode: String = "normal"
        /** Difficulty modes: each multiplies the tiers; add, change or remove them, then choose one in mode. */
        var modes: MutableList<Mode> = defaultModes()
        var events = Events()
        var defeat = Defeat()
        var portals = Portals()
        var encounters = Encounters()
        var sync = Sync()
        var test = Test()

        fun validate() {
            maxSessions = maxSessions.coerceIn(0, 64)
            speed = speed.coerceIn(2f, 120f)
            lightYearsPerBlock = lightYearsPerBlock.coerceIn(0.0001f, 100f)
            tunnel.validate()
            steering.validate()
            riders.validate()
            limits.validate()
            energy.validate()
            objects.validate()
            difficulty.validate()
            if (modes.isEmpty()) modes = defaultModes()
            modes.forEach { it.validate() }
            mode = mode.lowercase()
            if (modes.none { it.id == mode }) {
                GenerationsCore.LOGGER.warn("Ultra Warp Ride: no difficulty mode '{}', '{}' used", mode, modes.first().id)
                mode = modes.first().id
            }
            events.validate()
            defeat.validate()
            portals.validate()
            encounters.validate()
            sync.validate()
            test.validate()
        }

        /** The mode with this id, else the server's (mode). */
        fun modeOf(id: String?): Mode = modes.firstOrNull { it.id == id } ?: modes.firstOrNull { it.id == mode } ?: modes.first()

        /** What a journey with this Pokémon uses (in the server's mode, or the one given by a test command): a copy, which a reload does not change. */
        fun settings(variant: UltraWarpVariant, mode: String? = null) = UltraWarpRideSettings(variant, copy(this), modeOf(mode ?: this.mode).id)
    }

    /**
     * A difficulty mode (the server's is chosen by Ride.mode): multiplies the tiers'
     * values for the whole journey. speed: the
     * speed; orbs: the orange orbs' chance; dangers: the chances of blue orbs, debris,
     * arcs and gravitational anomalies; anomalies: their strength; events: the events'
     * chances; drain: the energy lost each second; damage: the energy dangers take.
     * Its name comes from the language files (generations_core.ultra_warp.mode.<id>),
     * else its id.
     */
    class Mode(
        var id: String = "normal",
        var speed: Float = 1f,
        var orbs: Float = 1f,
        var dangers: Float = 1f,
        var anomalies: Float = 1f,
        var events: Float = 1f,
        var drain: Float = 1f,
        var damage: Float = 1f,
    ) {
        fun validate() {
            id = id.lowercase().filter { it.isLetterOrDigit() || it == '_' }.ifEmpty { "normal" }
            speed = speed.coerceIn(0.2f, 5f)
            orbs = orbs.coerceIn(0f, 10f)
            dangers = dangers.coerceIn(0f, 10f)
            anomalies = anomalies.coerceIn(0f, 10f)
            events = events.coerceIn(0f, 10f)
            drain = drain.coerceIn(0f, 10f)
            damage = damage.coerceIn(0f, 10f)
        }
    }

    fun defaultModes(): MutableList<Mode> = mutableListOf(
        Mode("easy", 0.85f, 1.3f, 0.6f, 0.6f, 0.6f, 0.6f, 0.6f),
        Mode("normal"),
        Mode("hard", 1.15f, 0.85f, 1.4f, 1.3f, 1.3f, 1.3f, 1.3f),
        Mode("extreme", 1.3f, 0.7f, 1.9f, 1.6f, 1.6f, 1.6f, 1.6f),
    )

    class Tunnel {
        /** Distance between two points of the path, in blocks. */
        var segmentLength: Float = 16f
        /** Strength of the bends: largest turn between two points, in degrees. */
        var curveIntensity: Float = 8f
        /** Points before the path picks a new bend. */
        var curveChangeSegments: Int = 6
        /** Steepest slope, in degrees. */
        var maxPitch: Float = 22f
        var radius: Float = 13f
        /** How much the radius swells and narrows (0 to 0.6). */
        var radiusVariation: Float = 0.2f

        fun validate() {
            segmentLength = segmentLength.coerceIn(6f, 64f)
            curveIntensity = curveIntensity.coerceIn(0f, 30f)
            curveChangeSegments = curveChangeSegments.coerceIn(1, 50)
            maxPitch = maxPitch.coerceIn(0f, 45f)
            radius = radius.coerceIn(4f, 32f)
            radiusVariation = radiusVariation.coerceIn(0f, 0.6f)
        }
    }

    /** Arcade steering across the tunnel (the journey goes forward by itself). */
    class Steering {
        /** Fastest sideways or up-down movement, in blocks per second. */
        var maxSpeed: Float = 10f
        /** How fast that speed is reached while a key is held (per second). */
        var acceleration: Float = 6f
        /** How fast the Pokémon slows down once the keys are let go (per second). */
        var deceleration: Float = 4f

        fun validate() {
            maxSpeed = maxSpeed.coerceIn(0f, 60f)
            acceleration = acceleration.coerceIn(0.5f, 50f)
            deceleration = deceleration.coerceIn(0.5f, 50f)
        }
    }

    /**
     * Size of Solgaleo and Lunala in the tunnel, at full size, in blocks (measured from
     * the models by tools/ultra_warp_envelope.py). halfWidth, halfHeight, halfLength:
     * their gameplay shape (an ellipse across the tunnel, so long along it); a Pokémon
     * comes as near the walls as that shape allows. flapAllowance: room added above and
     * below for wings that beat. hitRadius: a disc holding the whole Pokémon whatever
     * its tilt, used to fit it in the tunnel (made smaller when wider than maxFill of
     * the narrowest radius) and to frame the camera. hitboxScale: part of the shape
     * that counts for collisions (a little forgiving).
     */
    class Riders {
        var sun = RiderSize(4.7f, 1.77f, 2.74f, 5.36f, 0f)
        var moon = RiderSize(7.9f, 6.63f, 3.79f, 0.72f, 1f)
        var maxFill: Float = 0.65f
        var hitboxScale: Float = 0.8f

        fun validate() {
            sun.validate()
            moon.validate()
            maxFill = maxFill.coerceIn(0.2f, 0.95f)
            hitboxScale = hitboxScale.coerceIn(0.2f, 1.5f)
        }
    }

    class RiderSize(
        var hitRadius: Float = 4f,
        var halfWidth: Float = 2f,
        var halfHeight: Float = 2f,
        var halfLength: Float = 2f,
        var flapAllowance: Float = 0f,
    ) {
        fun validate() {
            hitRadius = hitRadius.coerceIn(0.5f, 30f)
            halfWidth = halfWidth.coerceIn(0.1f, 30f)
            halfHeight = halfHeight.coerceIn(0.1f, 30f)
            halfLength = halfLength.coerceIn(0f, 30f)
            flapAllowance = flapAllowance.coerceIn(0f, 10f)
        }
    }

    /** Room kept between the Pokémon and the walls, in blocks: below, on the sides, above. */
    class Limits {
        var floorMargin: Float = 0.3f
        var sideMargin: Float = 0.6f
        var ceilingMargin: Float = 0.6f
        /** Blocks before the limit where moving outwards is braked. */
        var softness: Float = 0.75f

        fun validate() {
            floorMargin = floorMargin.coerceIn(0f, 5f)
            sideMargin = sideMargin.coerceIn(0f, 5f)
            ceilingMargin = ceilingMargin.coerceIn(0f, 5f)
            softness = softness.coerceIn(0.1f, 5f)
        }
    }

    /** The journey's energy: it drains, orange orbs give some, dangers take some; at 0 the journey is lost. */
    class Energy {
        var max: Float = 100f
        var start: Float = 100f
        var drainPerSecond: Float = 1f
        /** Given by an orange orb. */
        var orb: Float = 15f
        /** Taken by a blue electric orb, a debris, an electric arc. */
        var spark: Float = 20f
        var debris: Float = 15f
        var arc: Float = 25f
        /** After a hit, nothing else hurts for this long. */
        var invulnerableSeconds: Float = 0.75f

        fun validate() {
            max = max.coerceIn(1f, 10000f)
            start = start.coerceIn(1f, max)
            drainPerSecond = drainPerSecond.coerceIn(0f, 1000f)
            orb = orb.coerceIn(0f, max)
            spark = spark.coerceIn(0f, max)
            debris = debris.coerceIn(0f, max)
            arc = arc.coerceIn(0f, max)
            invulnerableSeconds = invulnerableSeconds.coerceIn(0f, 10f)
        }
    }

    /**
     * What the tunnel holds. It is cut in slots of slotLength blocks; each slot draws
     * its objects from the seed (the same on the server and the client), with the
     * chances of the difficulty tier there. Sizes in blocks.
     */
    class Objects {
        var slotLength: Float = 12f
        /** Blocks at the start without anything. */
        var clearStart: Float = 120f
        var orbRadius: Float = 1.2f
        var sparkRadius: Float = 1.0f
        var debrisMinRadius: Float = 1.0f
        var debrisMaxRadius: Float = 2.4f
        /** Thickness of an electric arc. */
        var arcThickness: Float = 0.6f
        /** Gravitational anomaly: size, length along the tunnel where it pulls or pushes, strength (blocks/s²). */
        var gravityRadius: Float = 4f
        var gravityLength: Float = 18f
        var gravityStrength: Float = 9f
        /** Speed current: size of the tube and how much faster it goes. */
        var currentRadius: Float = 3.5f
        var currentBoost: Float = 1.6f
        /** Orange orbs count bigger for picking up (easier). */
        var pickupScale: Float = 1.4f
        /** How far ahead objects are drawn (the server announces events further than this). */
        var viewDistance: Float = 220f

        fun validate() {
            slotLength = slotLength.coerceIn(4f, 64f)
            clearStart = clearStart.coerceIn(0f, 2000f)
            orbRadius = orbRadius.coerceIn(0.2f, 5f)
            sparkRadius = sparkRadius.coerceIn(0.2f, 5f)
            debrisMinRadius = debrisMinRadius.coerceIn(0.2f, 6f)
            debrisMaxRadius = debrisMaxRadius.coerceIn(debrisMinRadius, 8f)
            arcThickness = arcThickness.coerceIn(0.1f, 3f)
            gravityRadius = gravityRadius.coerceIn(0.5f, 12f)
            gravityLength = gravityLength.coerceIn(2f, 80f)
            gravityStrength = gravityStrength.coerceIn(0f, 60f)
            currentRadius = currentRadius.coerceIn(1f, 12f)
            currentBoost = currentBoost.coerceIn(1f, 4f)
            pickupScale = pickupScale.coerceIn(1f, 4f)
            viewDistance = viewDistance.coerceIn(60f, 400f)
        }
    }

    /**
     * Difficulty tiers by light-years travelled, blended between two tiers (no jump):
     * speed (times the initial speed) and the chance of each object per slot,
     * strength of the anomalies, and how often events happen (times their chance).
     */
    class Difficulty {
        /** How fast the speed changes, in blocks per second each second. */
        var speedAcceleration: Float = 2f
        var tiers: MutableList<Tier> = mutableListOf(
            Tier(0f, 1.0f, 0.22f, 0.08f, 0.06f, 0f, 0f, 0.4f, 0.5f),
            Tier(40f, 1.15f, 0.2f, 0.14f, 0.12f, 0.05f, 0.04f, 0.6f, 0.8f),
            Tier(120f, 1.35f, 0.17f, 0.2f, 0.17f, 0.09f, 0.07f, 0.8f, 1f),
            Tier(250f, 1.6f, 0.15f, 0.26f, 0.22f, 0.12f, 0.1f, 1f, 1.3f),
            Tier(400f, 1.85f, 0.13f, 0.32f, 0.27f, 0.15f, 0.12f, 1.2f, 1.6f),
        )

        fun validate() {
            speedAcceleration = speedAcceleration.coerceIn(0.1f, 100f)
            if (tiers.isEmpty()) tiers = Difficulty().tiers
            tiers.forEach { it.validate() }
            tiers.sortBy { it.lightYears }
        }
    }

    class Tier(
        var lightYears: Float = 0f,
        var speed: Float = 1f,
        var orbs: Float = 0.2f,
        var sparks: Float = 0.1f,
        var debris: Float = 0.1f,
        var arcs: Float = 0f,
        var gravity: Float = 0f,
        var anomalyStrength: Float = 0.5f,
        var events: Float = 1f,
    ) {
        fun validate() {
            lightYears = lightYears.coerceAtLeast(0f)
            speed = speed.coerceIn(0.2f, 5f)
            orbs = orbs.coerceIn(0f, 1f)
            sparks = sparks.coerceIn(0f, 1f)
            debris = debris.coerceIn(0f, 1f)
            arcs = arcs.coerceIn(0f, 1f)
            gravity = gravity.coerceIn(0f, 1f)
            anomalyStrength = anomalyStrength.coerceIn(0f, 3f)
            events = events.coerceIn(0f, 10f)
        }
    }

    /**
     * Special events, each with its chance per minute (times the tier's events), its
     * length in seconds, from how many light-years, the pause before it can come again,
     * and its strength: storm = more dangers (times), current = speed (times), anomaly =
     * drift of the steering (blocks/s²), Necrozma = nothing (it is only seen).
     */
    class Events {
        /** Blocks ahead where the server announces storms and currents (more than the objects' view distance). */
        var leadDistance: Float = 300f
        var storm = EventSettings(true, 6f, 10f, 16f, 30f, 25f, 1.6f)
        var current = EventSettings(true, 8f, 5f, 8f, 10f, 15f, 1.6f)
        var anomaly = EventSettings(true, 5f, 6f, 10f, 50f, 30f, 6f)
        var necrozma = EventSettings(true, 2f, 7f, 7f, 80f, 100000f, 0f)

        fun validate() {
            leadDistance = leadDistance.coerceIn(100f, 1000f)
            storm.validate()
            current.validate()
            anomaly.validate()
            necrozma.validate()
        }
    }

    class EventSettings(
        var enabled: Boolean = true,
        var chancePerMinute: Float = 5f,
        var minSeconds: Float = 5f,
        var maxSeconds: Float = 10f,
        var minLightYears: Float = 0f,
        var cooldownSeconds: Float = 20f,
        var strength: Float = 1f,
    ) {
        fun validate() {
            chancePerMinute = chancePerMinute.coerceIn(0f, 60f)
            minSeconds = minSeconds.coerceIn(0.5f, 120f)
            maxSeconds = maxSeconds.coerceIn(minSeconds, 120f)
            minLightYears = minLightYears.coerceAtLeast(0f)
            cooldownSeconds = cooldownSeconds.coerceIn(0f, 1_000_000f)
            strength = strength.coerceIn(0f, 60f)
        }
    }

    /**
     * Dimensional portals (warp holes): the server places them ahead of the player,
     * each with a destination (its colour) and a rarity level (its look), drawn apart:
     * any colour can come at any level. Flying into one ends the journey with success.
     */
    class Portals {
        var enabled: Boolean = true
        /** Light-years before the first portal. */
        var firstLightYears: Float = 15f
        /** Blocks between two portals (or groups of them), drawn between the two. */
        var minSpacing: Float = 300f
        var maxSpacing: Float = 600f
        /** Portals ahead of the player at once, at most. */
        var maxVisible: Int = 3
        /** Blocks ahead where the server places them (more than the objects' view distance). */
        var leadDistance: Float = 320f
        /** Blocks ahead they are drawn from, fading in (client). */
        var viewDistance: Float = 300f
        /** Size of the opening, in blocks. */
        var radius: Float = 3.2f
        /**
         * Activation, on purpose: a portal neither pulls nor slows. Only the Pokémon's
         * centre (the middle of its body, not its wings) going through the opening
         * counts, within this part of the opening's drawn radius (the vortex, not the
         * rings and light around it).
         */
        var triggerFraction: Float = 0.8f
        /** Blocks before a portal kept free of the course's objects. */
        var clearBefore: Float = 24f
        /** Seconds of the trip through the portal, at the end. */
        var cinematicSeconds: Float = 3f
        /** Chance that a portal comes with others (convergence), and how many in all. */
        var convergenceChance: Float = 0.15f
        var convergenceMin: Int = 2
        var convergenceMax: Int = 3
        var destinations: MutableList<Destination> = defaultDestinations()
        /** Rarity by light-years: from each tier's distance, the weights of levels 1 to 4. */
        var rarity: MutableList<RarityTier> = defaultRarity()
        /** Look of each level (1 to 4). */
        var levels: MutableList<PortalLevel> = defaultLevels()

        fun validate() {
            firstLightYears = firstLightYears.coerceAtLeast(0f)
            minSpacing = minSpacing.coerceIn(50f, 10000f)
            maxSpacing = maxSpacing.coerceIn(minSpacing, 20000f)
            maxVisible = maxVisible.coerceIn(1, 10)
            leadDistance = leadDistance.coerceIn(150f, 1000f)
            viewDistance = viewDistance.coerceIn(60f, 600f)
            radius = radius.coerceIn(1f, 10f)
            triggerFraction = triggerFraction.coerceIn(0.2f, 1.2f)
            clearBefore = clearBefore.coerceIn(0f, 200f)
            cinematicSeconds = cinematicSeconds.coerceIn(0.5f, 15f)
            convergenceChance = convergenceChance.coerceIn(0f, 1f)
            convergenceMin = convergenceMin.coerceIn(2, 4)
            convergenceMax = convergenceMax.coerceIn(convergenceMin, 4)

            // Destinations: at least one that can come, ids unique.
            destinations.forEach { it.validate() }
            destinations = destinations.distinctBy { it.id }.toMutableList()
            if (destinations.none { it.weight > 0f }) {
                GenerationsCore.LOGGER.warn("Ultra Warp Ride: no portal destination with a weight above 0, the default ones used")
                destinations = defaultDestinations()
            }

            // Rarity: tiers by distance, 4 weights each, never negative, never all 0; the
            // first starts at 0; a simple portal always stays possible (1 % at least).
            if (rarity.isEmpty()) rarity = defaultRarity()
            rarity.forEach { tier ->
                tier.fromLightYears = tier.fromLightYears.coerceAtLeast(0f)
                val weights = (tier.weights + List(4) { 0f }).take(4).map { it.coerceAtLeast(0f) }.toMutableList()
                if (weights.sum() <= 0f) {
                    GenerationsCore.LOGGER.warn("Ultra Warp Ride: portal rarity from {} ly has no weight, 100 % simple portals", tier.fromLightYears)
                    weights[0] = 1f
                }
                val others = weights[1] + weights[2] + weights[3]
                if (weights[0] < others / 99f) {
                    GenerationsCore.LOGGER.warn("Ultra Warp Ride: portal rarity from {} ly: simple portals kept at 1 %", tier.fromLightYears)
                    weights[0] = others / 99f
                }
                tier.weights = weights
            }
            rarity.sortBy { it.fromLightYears }
            rarity.first().fromLightYears = 0f

            levels = (levels + defaultLevels()).take(4).toMutableList()
            levels.forEach { it.validate() }
        }
    }

    /**
     * A portal destination: its id (the Ultra Space world, for the next phase's
     * dimension), its colour, its weight among the destinations, its Ultra Beast.
     * Its name comes from the language files (generations_core.ultra_warp.destination.<id>).
     */
    class Destination(
        var id: String = "ultra_jungle",
        var color: String = "#3CE070",
        var weight: Float = 1f,
        var ultraBeast: String = "cobblemon:buzzwole",
        var dimension: String = "generations_core:ultra_jungle",
    ) {
        fun validate() {
            id = id.lowercase().filter { it.isLetterOrDigit() || it == '_' }.ifEmpty { "ultra_jungle" }
            weight = weight.coerceAtLeast(0f)
            if (color.removePrefix("#").toIntOrNull(16) == null) color = "#FFFFFF"
        }

        val rgb: Int get() = color.removePrefix("#").toIntOrNull(16)?.and(0xFFFFFF) ?: 0xFFFFFF
    }

    class RarityTier(var fromLightYears: Float = 0f, var weights: MutableList<Float> = mutableListOf(85f, 14f, 1f, 0f))

    /**
     * How a rarity level looks: 3D rings around the opening, energy petals (the
     * exceptional aura), sparkles drawn in, how fast the rings turn, glow and size.
     */
    class PortalLevel(
        var rings: Int = 0,
        var petals: Int = 0,
        var sparkles: Int = 8,
        var ringSpeed: Float = 1f,
        var glow: Float = 1f,
        var size: Float = 1f,
    ) {
        fun validate() {
            rings = rings.coerceIn(0, 3)
            petals = petals.coerceIn(0, 16)
            sparkles = sparkles.coerceIn(0, 64)
            ringSpeed = ringSpeed.coerceIn(0f, 10f)
            glow = glow.coerceIn(0.1f, 4f)
            size = size.coerceIn(0.3f, 3f)
        }
    }

    fun defaultDestinations(): MutableList<Destination> = mutableListOf(
        Destination("ultra_jungle", "#3CE070", 1f, "cobblemon:buzzwole", "generations_core:ultra_jungle"),
        Destination("ultra_desert", "#FF8A2A", 1f, "cobblemon:pheromosa", "generations_core:ultra_desert"),
        Destination("ultra_central", "#FFE040", 1f, "cobblemon:xurkitree", "generations_core:ultra_central"),
        Destination("ultra_crater", "#C8D0DC", 1f, "cobblemon:celesteela", "generations_core:ultra_crater"),
        Destination("ultra_forest", "#A060FF", 1f, "cobblemon:kartana", "generations_core:ultra_forest"),
        Destination("ultra_abyss", "#3A8CFF", 1f, "cobblemon:nihilego", "generations_core:ultra_abyss"),
        Destination("ultra_skyscraper", "#A01828", 1f, "cobblemon:guzzlord", "generations_core:ultra_skyscraper"),
    )

    fun defaultRarity(): MutableList<RarityTier> = mutableListOf(
        RarityTier(0f, mutableListOf(85f, 14f, 1f, 0f)),
        RarityTier(1000f, mutableListOf(65f, 28f, 6f, 1f)),
        RarityTier(2500f, mutableListOf(45f, 35f, 17f, 3f)),
        RarityTier(4000f, mutableListOf(25f, 35f, 30f, 10f)),
        RarityTier(6000f, mutableListOf(15f, 25f, 40f, 20f)),
        RarityTier(8000f, mutableListOf(10f, 20f, 35f, 35f)),
    )

    fun defaultLevels(): MutableList<PortalLevel> = mutableListOf(
        PortalLevel(0, 0, 8, 1f, 1f, 1f),
        PortalLevel(1, 0, 14, 1.2f, 1.15f, 1.08f),
        PortalLevel(2, 0, 22, 1.5f, 1.3f, 1.16f),
        PortalLevel(2, 8, 36, 1.8f, 1.6f, 1.25f),
    )

    /**
     * Prepared for the encounters phase, not used yet: three separate tables of
     * chances (0 to 1) by light-years and portal level (1 to 4): meeting an Ultra Beast,
     * an ordinary Pokémon being shiny, an Ultra Beast being shiny. Each is its own: an
     * ordinary Pokémon's 90 % shiny says nothing of an Ultra Beast's.
     */
    class Encounters {
        var ultraBeast: MutableList<ChanceTier> = defaultUltraBeast()
        var ordinaryShiny: MutableList<ChanceTier> = mutableListOf(
            ChanceTier(0f, mutableListOf(1f / 4096, 1f / 2048, 1f / 512, 1f / 64)),
            ChanceTier(2500f, mutableListOf(1f / 1024, 1f / 256, 1f / 32, 0.1f)),
            ChanceTier(8000f, mutableListOf(0.01f, 0.1f, 0.5f, 0.9f)),
        )
        var ultraBeastShiny: MutableList<ChanceTier> = mutableListOf(
            ChanceTier(0f, mutableListOf(1f / 4096, 1f / 4096, 1f / 4096, 1f / 4096)),
        )

        fun validate() {
            listOf(ultraBeast, ordinaryShiny, ultraBeastShiny).forEach { table ->
                table.forEach { tier ->
                    tier.fromLightYears = tier.fromLightYears.coerceAtLeast(0f)
                    tier.byLevel = (tier.byLevel + List(4) { 0f }).take(4).map { it.coerceIn(0f, 1f) }.toMutableList()
                }
                table.sortBy { it.fromLightYears }
            }
        }

        /** A chance from a table at these light-years for this level (1 to 4): for the encounters phase. */
        fun chance(table: List<ChanceTier>, lightYears: Double, level: Int): Float =
            (table.lastOrNull { it.fromLightYears <= lightYears } ?: table.firstOrNull())?.byLevel?.getOrNull(level - 1) ?: 0f
    }

    class ChanceTier(var fromLightYears: Float = 0f, var byLevel: MutableList<Float> = mutableListOf(0f, 0f, 0f, 0f))

    /**
     * Phase 5: the chance that the arrival's Pokémon is an Ultra Beast grows with the
     * light-years and the portal's level, up to 25 % from 8 000 ly through an
     * exceptional portal (version 4; the phase 3 values went up to 60 %).
     */
    fun defaultUltraBeast(): MutableList<ChanceTier> = mutableListOf(
        ChanceTier(0f, mutableListOf(0.01f, 0.02f, 0.04f, 0.08f)),
        ChanceTier(4000f, mutableListOf(0.02f, 0.04f, 0.08f, 0.15f)),
        ChanceTier(8000f, mutableListOf(0.03f, 0.06f, 0.12f, 0.25f)),
    )

    class Defeat {
        /** Seconds of the defeat (the player has lost control) before the end. */
        var seconds: Float = 3f

        fun validate() {
            seconds = seconds.coerceIn(0.5f, 20f)
        }
    }

    /** The client's positions, checked by the server. */
    class Sync {
        /** Positions the client sends per second. */
        var statesPerSecond: Int = 20
        /** Blocks a position may be beyond the limits before it counts as a gap (latency, rounding). */
        var tolerance: Float = 1f
        /** Seconds the client's distance may lag behind the server's (latency). */
        var latencySeconds: Float = 1.5f

        fun validate() {
            statesPerSecond = statesPerSecond.coerceIn(1, 20)
            tolerance = tolerance.coerceIn(0f, 10f)
            latencySeconds = latencySeconds.coerceIn(0.1f, 10f)
        }
    }

    class Test {
        /** Same tunnel every time when not 0 (a seed); 0 = a new one each journey. */
        var fixedSeed: Long = 0
        /** The journey ends by itself after this many seconds; 0 = it lasts until the player leaves. */
        var maxDurationSeconds: Int = 0
        /** No energy lost (drain, hits): for trying the journey. */
        var invincible: Boolean = false

        fun validate() {
            maxDurationSeconds = maxDurationSeconds.coerceIn(0, 36000)
        }
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val compact: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "ultra_space", "ultra_warp_ride.json")

    private const val VERSION = 4

    var ride = Ride().also { it.version = VERSION }; private set

    /** The settings as JSON (sent to the client with a journey), and back. */
    fun toJson(ride: Ride): String = compact.toJson(ride)

    fun fromJson(json: String): Ride = (compact.fromJson(json, Ride::class.java) ?: Ride()).also { it.validate() }

    fun copy(ride: Ride): Ride = fromJson(toJson(ride))

    fun load() {
        val file = file
        ride = try {
            if (Files.exists(file)) gson.fromJson(Files.readString(file), Ride::class.java) ?: Ride() else Ride().also { it.version = VERSION }
        } catch (e: Exception) {
            // The file stays as it is, for its author to fix.
            GenerationsCore.LOGGER.error("Ultra Warp Ride: could not read {} ({}), default values used", file, e.message)
            ride = Ride().also { it.validate(); it.version = VERSION }
            return
        }.also { it.validate() }
        if (ride.version < VERSION) {
            // Version 2 (phase 2.2): a wider tunnel by default. Values left at the former
            // defaults take the new ones; values someone chose are kept.
            if (ride.tunnel.radius == 9f) ride.tunnel.radius = 13f
            if (ride.tunnel.radiusVariation == 0.25f) ride.tunnel.radiusVariation = 0.2f
            // Version 3 (phases 2.3 and 2.4): positions sent 20 times per second for the
            // collisions; the riders' measured shapes (older files only had hitRadius).
            if (ride.sync.statesPerSecond == 5) ride.sync.statesPerSecond = 20
            if (ride.version < 3) {
                ride.riders.sun = RiderSize(ride.riders.sun.hitRadius, 1.77f, 2.74f, 5.36f, 0f)
                ride.riders.moon = RiderSize(ride.riders.moon.hitRadius, 6.63f, 3.79f, 0.72f, 1f)
            }
            // Version 4 (phase 5): the Ultra Beast chance of the arrival's encounter, lower; a
            // table left at the phase 3 values takes the new one.
            val former = listOf(0f to listOf(0.05f, 0.1f, 0.2f, 0.35f), 4000f to listOf(0.1f, 0.2f, 0.35f, 0.6f))
            if (ride.version < 4 && ride.encounters.ultraBeast.map { it.fromLightYears to it.byLevel.toList() } == former) {
                ride.encounters.ultraBeast = defaultUltraBeast()
            }
            ride.version = VERSION
            GenerationsCore.LOGGER.info("Ultra Warp Ride: {} brought up to date (version {})", file, VERSION)
        }
        // Every mode's chances, in the log.
        GenerationsCore.LOGGER.info("Ultra Warp Ride: difficulty '{}' for every journey; the modes:", ride.mode)
        ride.modes.forEach { mode ->
            UltraWarpDifficulty.describe(ride, mode).forEach { GenerationsCore.LOGGER.info("Ultra Warp Ride:   {}", it) }
        }
        // Written back: the options added since, and the values brought into range.
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(ride))
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Warp Ride: could not write {}", file, e)
        }
    }
}
