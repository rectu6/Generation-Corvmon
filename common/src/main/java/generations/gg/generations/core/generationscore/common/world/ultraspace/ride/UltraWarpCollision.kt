package generations.gg.generations.core.generationscore.common.world.ultraspace.ride

import kotlin.math.max
import kotlin.math.min

/**
 * Collisions of the journey, from gameplay volumes only (never the models' look):
 * the Pokémon's hitbox is an ellipse across the tunnel (half width, half height) and
 * halfLength along it; objects are balls of their radius, electric arcs segments
 * across the tunnel of their thickness. The same on the server (which decides) and
 * the client (which shows the effect at once).
 */
object UltraWarpCollision {
    /** Whether the hitbox with its centre at (x, y) across the tunnel overlaps the item (whatever the distance along it). */
    fun overlaps(item: UltraWarpCourse.Item, hitbox: UltraWarpRideLimits.Shape, x: Double, y: Double, grow: Double = 1.0): Boolean {
        val r = item.radius * grow
        val sx = 1.0 / (hitbox.halfWidth + r)
        val sy = 1.0 / (hitbox.halfHeight + r)
        return if (item.kind == UltraWarpCourse.Kind.ARC) {
            // Distance from the centre to the arc's segment, in the ellipse's own measure.
            val ax = (item.x - x) * sx
            val ay = (item.y - y) * sy
            val bx = (item.x2 - x) * sx
            val by = (item.y2 - y) * sy
            val dx = bx - ax
            val dy = by - ay
            val length = dx * dx + dy * dy
            val t = if (length < 1e-12) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
            val px = ax + dx * t
            val py = ay + dy * t
            px * px + py * py <= 1.0
        } else {
            val dx = (item.x - x) * sx
            val dy = (item.y - y) * sy
            dx * dx + dy * dy <= 1.0
        }
    }

    /**
     * Whether the Pokémon, going from (d0, x0, y0) to (d1, x1, y1) (distance along the
     * tunnel, position across it), touches the item: its position when it passes the
     * item, checked against the item's volume.
     */
    fun touches(
        item: UltraWarpCourse.Item, hitbox: UltraWarpRideLimits.Shape,
        d0: Double, x0: Double, y0: Double, d1: Double, x1: Double, y1: Double, grow: Double = 1.0,
    ): Boolean {
        val along = hitbox.halfLength + item.radius * grow
        if (item.distance < min(d0, d1) - along || item.distance > max(d0, d1) + along) return false
        val t = if (d1 - d0 > 1e-6) ((item.distance - d0) / (d1 - d0)).coerceIn(0.0, 1.0) else 1.0
        return overlaps(item, hitbox, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, grow)
    }
}
