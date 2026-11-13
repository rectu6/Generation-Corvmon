package generations.gg.generations.core.generationscore.common.world.ultraspace.ride

import java.util.SplittableRandom
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * What a journey's tunnel holds, the same on the server and the client without
 * sending any object: the tunnel is cut in slots of slotLength blocks, and each slot
 * draws its objects from the seed and its number, with the chances of the difficulty
 * there (UltraWarpDifficulty) and of the events announced over it (storms: more
 * dangers). Positions are across the tunnel at the object's distance (x right, y up
 * from the axis), always inside the walls: orange orbs where the Pokémon can reach,
 * dangers where they can touch it.
 *
 * Never a dead end: after placing a slot's dangers, a free spot for the Pokémon must
 * remain within reach of the previous slot's free spot (at the steering speed); if
 * none does, dangers are taken out until one does. Slots are made in order (each
 * depends on the previous free spot), once, and again from a window announced later.
 * StrictMath for the angles: the same numbers on every machine.
 */
class UltraWarpCourse(private val seed: Long, private val tunnel: UltraWarpTunnel, private val settings: UltraWarpRideSettings) {
    enum class Kind(val dangerous: Boolean) {
        /** Orange orb: energy. */
        ORB(false),
        /** Blue electric orb: takes energy. */
        SPARK(true),
        /** Floating dimensional debris: takes energy. */
        DEBRIS(true),
        /** Electric arc across part of the tunnel, from (x, y) to (x2, y2): takes energy. */
        ARC(true),
        /** Gravitational anomaly: pulls (strength > 0) or pushes the Pokémon, gravityLength along the tunnel. */
        GRAVITY(false),
    }

    class Item(
        /** Slot × 8 + its place in the slot: the same on both sides. */
        val id: Int,
        val kind: Kind,
        val distance: Double,
        val x: Double,
        val y: Double,
        /** Ball radius; an arc's half thickness; an anomaly's size. */
        val radius: Double,
        val x2: Double = 0.0,
        val y2: Double = 0.0,
        val strength: Double = 0.0,
        /** For the look: shape and turning of debris, flicker of arcs. */
        val look: Long = 0,
    )

    enum class WindowKind { STORM, CURRENT }

    /**
     * An event over a stretch of the tunnel, announced by the server ahead of it:
     * storm (more dangers, times strength) or speed current (a tube at (x, y)).
     */
    class Window(val id: Int, val kind: WindowKind, val from: Double, val to: Double, val strength: Float, val x: Double, val y: Double)

    private val config = settings.config
    private val slotLength = config.objects.slotLength.toDouble()
    private val slots = ArrayList<List<Item>>()
    private val safeX = ArrayList<Double>()
    private val safeY = ArrayList<Double>()
    private val shape = UltraWarpRideLimits.shape(settings)
    private val hitbox = UltraWarpRideLimits.hitbox(settings)
    val windows = ArrayList<Window>()

    fun slotOf(distance: Double): Int = floor(distance / slotLength).toInt().coerceAtLeast(0)

    /** An event announced: the slots it covers are made again with it. */
    fun addWindow(window: Window) {
        if (windows.any { it.id == window.id }) return
        windows.add(window)
        val from = slotOf(window.from)
        while (slots.size > from) {
            slots.removeAt(slots.lastIndex)
            safeX.removeAt(safeX.lastIndex)
            safeY.removeAt(safeY.lastIndex)
        }
    }

    fun windowAt(kind: WindowKind, distance: Double): Window? = windows.firstOrNull { it.kind == kind && distance >= it.from && distance < it.to }

    /** Stretches of the tunnel kept clear (around portals): from..to along it. The same on both sides. */
    private val clearZones = ArrayList<Pair<Double, Double>>()

    fun clear(from: Double, to: Double) {
        clearZones.add(from to to)
    }

    /** The objects of the slots between these distances, but those in a clear stretch. */
    fun items(from: Double, to: Double, out: MutableList<Item>) {
        if (to < from) return
        for (k in slotOf(from)..slotOf(to)) {
            for (item in slot(k)) {
                if (clearZones.none { item.distance >= it.first && item.distance <= it.second }) out.add(item)
            }
        }
    }

    fun slot(k: Int): List<Item> {
        while (slots.size <= k) generate(slots.size)
        return slots[k]
    }

    private fun generate(k: Int) {
        val start = k * slotLength
        val items = ArrayList<Item>()
        if (start < config.objects.clearStart) {
            slots.add(items)
            safeX.add(0.0)
            safeY.add(0.0)
            return
        }
        // The same draws whatever is kept, so that each value always means the same.
        val random = SplittableRandom(seed * 31 + k * -0x61c8864680b583ebL)
        val rolls = DoubleArray(32) { random.nextDouble() }
        val look = random.nextLong()
        val middle = start + slotLength / 2
        val tier = UltraWarpDifficulty.at(settings, middle * config.lightYearsPerBlock)
        val storm = windows.filter { it.kind == WindowKind.STORM && start < it.to && start + slotLength > it.from }.maxOfOrNull { it.strength } ?: 1f
        val objects = config.objects
        var index = 0

        // Orange orb, where the Pokémon can reach.
        if (rolls[0] < tier.orbs) {
            val d = start + rolls[1] * slotLength
            val (x, y) = spot(d, rolls[2], rolls[3], objects.orbRadius.toDouble(), reachable = true)
            items.add(Item(k * 8 + index++, Kind.ORB, d, x, y, objects.orbRadius.toDouble(), look = look))
        }
        // Blue electric orb.
        if (rolls[4] < tier.sparks * storm) {
            val d = start + rolls[5] * slotLength
            val (x, y) = spot(d, rolls[6], rolls[7], objects.sparkRadius.toDouble(), reachable = false)
            items.add(Item(k * 8 + index++, Kind.SPARK, d, x, y, objects.sparkRadius.toDouble(), look = look xor 0x5A5A))
        }

        // Floating debris.
        if (rolls[8] < tier.debris * storm) {
            val d = start + rolls[9] * slotLength
            val radius = objects.debrisMinRadius + (objects.debrisMaxRadius - objects.debrisMinRadius) * rolls[10]
            val (x, y) = spot(d, rolls[11], rolls[12], radius, reachable = false)
            items.add(Item(k * 8 + index++, Kind.DEBRIS, d, x, y, radius, look = look xor 0x3C3C))
        }
        // Electric arc: a chord from wall to wall, off the middle, so that the larger
        // side stays free (the free-spot check below makes sure of it).
        if (rolls[13] < tier.arcs * storm) {
            val d = start + rolls[14] * slotLength
            val angle = rolls[15] * 2 * PI
            val wall = tunnel.coreRadius(d) - 0.2
            val offset = (0.2 + 0.45 * rolls[16]) * wall * (if (rolls[17] < 0.5) 1 else -1)
            val nx = StrictMath.cos(angle)
            val ny = StrictMath.sin(angle)
            val half = sqrt(max(0.0, wall * wall - offset * offset))
            items.add(
                Item(
                    k * 8 + index++, Kind.ARC, d,
                    nx * offset - ny * half, ny * offset + nx * half, objects.arcThickness / 2.0,
                    nx * offset + ny * half, ny * offset - nx * half, look = look xor 0x7E7E,
                ),
            )
        }
        // Gravitational anomaly: pulls (most often) or pushes, a little stronger as it gets harder.
        if (rolls[18] < tier.gravity) {
            val d = start + rolls[19] * slotLength
            val (x, y) = spot(d, rolls[20], rolls[21], objects.gravityRadius.toDouble(), reachable = true)
            val sign = if (rolls[22] < 0.65) 1.0 else -1.0
            items.add(Item(k * 8 + index++, Kind.GRAVITY, d, x, y, objects.gravityRadius.toDouble(), strength = sign * objects.gravityStrength * tier.anomalyStrength, look = look))
        }

        // A free spot within reach of the last one; otherwise fewer dangers.
        val speed = settings.speed * tier.speed
        val reach = settings.steerSpeed * (slotLength / speed.coerceAtLeast(1f)) * 0.7 + 0.5
        val previousX = safeX.lastOrNull() ?: 0.0
        val previousY = safeY.lastOrNull() ?: 0.0
        var free = freeSpot(middle, items, previousX, previousY, reach, rolls[31])
        while (free == null && items.any { it.kind.dangerous }) {
            items.removeAt(items.indexOfLast { it.kind.dangerous })
            free = freeSpot(middle, items, previousX, previousY, reach, rolls[31])
        }
        slots.add(items)
        safeX.add(free?.first ?: previousX)
        safeY.add(free?.second ?: previousY)
    }

    /**
     * A place across the tunnel at distance d for a ball of this radius: where the
     * Pokémon's centre can be (reachable), or anywhere it can still touch, always
     * inside the walls. u and v (0 to 1) choose the angle and how far out.
     */
    private fun spot(d: Double, u: Double, v: Double, radius: Double, reachable: Boolean): Pair<Double, Double> {
        val angle = u * 2 * PI
        val nx = StrictMath.cos(angle)
        val ny = StrictMath.sin(angle)
        val inside = tunnel.coreRadius(d) - radius - 0.3
        val flying = UltraWarpRideLimits.allowed(tunnel, d, shape, nx, ny)
        val limit = if (reachable) min(flying, inside) else min(flying + UltraWarpRideLimits.reach(shape, nx, ny), inside)
        val r = sqrt(v) * max(0.0, limit)
        return nx * r to ny * r
    }

    /** The free spot nearest the previous one, within reach, among 17 candidates; null when none is. */
    private fun freeSpot(d: Double, items: List<Item>, fromX: Double, fromY: Double, reach: Double, turn: Double): Pair<Double, Double>? {
        var best: Pair<Double, Double>? = null
        var bestDistance = Double.MAX_VALUE
        fun consider(x: Double, y: Double) {
            val gap = UltraWarpRideLimits.length(x - fromX, y - fromY)
            if (gap > reach || gap >= bestDistance) return
            // Where the Pokémon can be here (the tunnel may have narrowed).
            val length = UltraWarpRideLimits.length(x, y)
            if (length > 1e-6 && length > UltraWarpRideLimits.allowed(tunnel, d, shape, x / length, y / length) + 1e-6) return
            if (items.any { it.kind.dangerous && UltraWarpCollision.overlaps(it, hitbox, x, y, 1.15) }) return
            best = x to y
            bestDistance = gap
        }
        consider(fromX, fromY)
        consider(0.0, 0.0)
        for (ring in listOf(0.45, 0.9)) {
            for (i in 0 until 8) {
                val angle = (i + turn) * PI / 4
                val nx = StrictMath.cos(angle)
                val ny = StrictMath.sin(angle)
                val r = ring * UltraWarpRideLimits.allowed(tunnel, d, shape, nx, ny)
                consider(nx * r, ny * r)
            }
        }
        return best
    }
}
