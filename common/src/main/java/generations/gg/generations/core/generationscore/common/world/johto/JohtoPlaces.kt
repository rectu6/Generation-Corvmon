package generations.gg.generations.core.generationscore.common.world.johto

import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.config.JsonConfigs
import java.nio.file.Files
import kotlin.math.sqrt

/**
 * config/generations/core/johto/johto_places.json: where the legend happens (JOHTO-PLAN.md § 3).
 * The towers are built by the players; nothing is generated and no chunk is kept loaded. Written
 * by /generations johto place …, readable and editable by hand.
 */
class JohtoPlaces(
    var version: Int = 1,
    /** The dimension of the towers, statues, zones and altar. */
    var dimension: String = "minecraft:overworld",
    /** The Burned Tower's centre on the ground and the way the history is watched from (yaw). */
    var history: Anchor? = null,
    /** The ghost tower's size in the history (blocks). */
    var towerHeight: Double = 22.0,
    var towerRadius: Double = 7.0,
    var statues: MutableList<StatuePlace> = mutableListOf(),
    /** A trial zone by beast id. */
    var zones: MutableMap<String, Zone> = linkedMapOf(),
    /** The safe spawn points by beast id, and "hooh". */
    var spawns: MutableMap<String, MutableList<Point>> = linkedMapOf(),
    /** The Bell Tower's altar: an existing block registered by its position (D2). */
    var altar: Altar? = null,
) {
    class Anchor(var x: Double = 0.0, var y: Double = 0.0, var z: Double = 0.0, var yaw: Float = 0f)

    class StatuePlace(var beast: String = "", var uuid: String = "", var x: Double = 0.0, var y: Double = 0.0, var z: Double = 0.0, var yaw: Float = 0f)

    class Point(var x: Double = 0.0, var y: Double = 0.0, var z: Double = 0.0)

    class Altar(var x: Int = 0, var y: Int = 0, var z: Int = 0, var yaw: Float = 0f)

    /** A circle (centre, radius) or a box (two corners); the height counts ±halfHeight around the centre for a circle. */
    class Zone(
        var shape: String = "circle",
        var x: Double = 0.0, var y: Double = 0.0, var z: Double = 0.0,
        var radius: Double = 24.0,
        var halfHeight: Double = 24.0,
        var x2: Double = 0.0, var y2: Double = 0.0, var z2: Double = 0.0,
    ) {
        val box: Boolean get() = shape == "box"

        fun contains(px: Double, py: Double, pz: Double, margin: Double = 0.0): Boolean = if (box) {
            px >= minOf(x, x2) - margin && px <= maxOf(x, x2) + 1 + margin &&
                pz >= minOf(z, z2) - margin && pz <= maxOf(z, z2) + 1 + margin &&
                py >= minOf(y, y2) - 4 - margin && py <= maxOf(y, y2) + 6 + margin
        } else {
            val dx = px - x
            val dz = pz - z
            dx * dx + dz * dz <= (radius + margin) * (radius + margin) && kotlin.math.abs(py - y) <= halfHeight + margin
        }

        val centreX: Double get() = if (box) (x + x2 + 1) / 2.0 else x
        val centreY: Double get() = if (box) minOf(y, y2) else y
        val centreZ: Double get() = if (box) (z + z2 + 1) / 2.0 else z

        /** About how far from the centre the zone reaches (the trials keep their elements within it). */
        val reach: Double get() = if (box) minOf(kotlin.math.abs(x2 - x), kotlin.math.abs(z2 - z)) / 2.0 else radius

        fun distance(px: Double, pz: Double): Double {
            val dx = px - centreX
            val dz = pz - centreZ
            return sqrt(dx * dx + dz * dz)
        }

        fun copy(): Zone = Zone(shape, x, y, z, radius, halfHeight, x2, y2, z2)
    }

    fun statue(uuid: java.util.UUID): StatuePlace? = statues.firstOrNull { it.uuid == uuid.toString() }

    fun zone(beast: JohtoBeast): Zone? = zones[beast.id]

    fun spawns(id: String): List<Point> = spawns[id] ?: emptyList()

    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        if (dimension == null || dimension.isBlank()) dimension = "minecraft:overworld"
        if (statues == null) statues = mutableListOf()
        if (zones == null) zones = linkedMapOf()
        if (spawns == null) spawns = linkedMapOf()
        statues.removeIf { it == null || JohtoBeast.of(it.beast) == null || it.uuid.isNullOrBlank() }
        towerHeight = JsonConfigs.clamp(towerHeight, 6.0, 80.0, 22.0)
        towerRadius = JsonConfigs.clamp(towerRadius, 3.0, 30.0, 7.0)
        zones.values.removeIf { it == null }
        zones.values.forEach { z ->
            if (z.shape == null) z.shape = "circle"
            z.radius = JsonConfigs.clamp(z.radius, 6.0, 256.0, 24.0)
            z.halfHeight = JsonConfigs.clamp(z.halfHeight, 4.0, 128.0, 24.0)
        }
        spawns.values.forEach { list -> list.removeIf { it == null } }
    }

    fun save() {
        try {
            val file = FILE
            Files.createDirectories(file.parent)
            Files.writeString(file, JsonConfigs.gson.toJson(this))
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Legend of Johto: could not write {}", FILE, e)
        }
    }

    companion object {
        val FILE = JsonConfigs.file("johto", "johto_places.json")

        fun load(): JohtoPlaces = JsonConfigs.load("Legend of Johto places", FILE, JohtoPlaces::class.java, ::JohtoPlaces, 1, { it.validate() })
    }
}
