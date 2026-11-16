package generations.gg.generations.core.generationscore.common.world.mew

/**
 * One Mew island of config/generations/core/mew/mew_zones.json (MEW-PLAN.md § 3). Pure (tested
 * off-game): its shape is plain numbers, its biomes are matched through a BiomeTest.
 *
 * - box: minX, minZ, maxX, maxZ (block coordinates, both ends included), minY and maxY optional;
 * - circle: centerX, centerZ, radius, minY and maxY optional;
 * - biomes: biome ids ("minecraft:flower_forest") or tags ("#minecraft:is_forest"); empty = all.
 *
 * The fields of the other shape are null (Gson does not write them back).
 */
class MewZone(
    var id: String = "",
    var enabled: Boolean = true,
    var dimension: String = "minecraft:overworld",
    var shape: String = SHAPE_BOX,
    var minX: Double? = null,
    var minZ: Double? = null,
    var maxX: Double? = null,
    var maxZ: Double? = null,
    var centerX: Double? = null,
    var centerZ: Double? = null,
    var radius: Double? = null,
    var minY: Double? = null,
    var maxY: Double? = null,
    var biomes: MutableList<String> = mutableListOf(),
) {
    /** Tells whether the biome under the player matches an entry of the list ("minecraft:jungle" or "#cobblemon:is_jungle"). */
    fun interface BiomeTest {
        fun matches(entry: String): Boolean
    }

    /** Why the zone cannot be used ("" when it can); set by normalize(). An unusable zone is kept in the file, untouched. */
    @Transient
    var problem: String = ""

    /** Whether this position is inside the shape (the biome aside). */
    fun contains(x: Double, y: Double, z: Double): Boolean {
        if (problem.isNotEmpty()) return false
        minY?.let { if (y < it) return false }
        maxY?.let { if (y >= it + 1.0) return false }
        return when (shape) {
            SHAPE_CIRCLE -> {
                val dx = x - centerX!!
                val dz = z - centerZ!!
                val r = radius!!
                dx * dx + dz * dz <= r * r
            }
            else -> x >= minX!! && x < maxX!! + 1.0 && z >= minZ!! && z < maxZ!! + 1.0
        }
    }

    /** Whether the biome is allowed: any biome when the list is empty. */
    fun biomeAllowed(test: BiomeTest): Boolean = biomes.isEmpty() || biomes.any { test.matches(it) }

    /** Horizontal area in blocks (for the list command). */
    fun area(): Double = when {
        problem.isNotEmpty() -> 0.0
        shape == SHAPE_CIRCLE -> Math.PI * radius!! * radius!!
        else -> (maxX!! - minX!! + 1.0) * (maxZ!! - minZ!! + 1.0)
    }

    /**
     * Checks the zone and puts its values in order (ends swapped when reversed, namespaces added,
     * the other shape's fields removed). Returns the problem, "" when it is fine; a zone with a
     * problem is left as it is and never matches.
     */
    @Suppress("SENSELESS_COMPARISON")
    fun normalize(): String {
        problem = findProblem()
        if (problem.isNotEmpty()) return problem
        id = id.trim()
        shape = shape.trim().lowercase()
        dimension = withNamespace(dimension.trim())
        biomes = biomes.filter { it != null && it.isNotBlank() }.map { entry ->
            val e = entry.trim()
            if (e.startsWith("#")) "#" + withNamespace(e.substring(1)) else withNamespace(e)
        }.distinct().toMutableList()
        if (minY != null && maxY != null && minY!! > maxY!!) minY = maxY.also { maxY = minY }
        if (shape == SHAPE_CIRCLE) {
            minX = null; minZ = null; maxX = null; maxZ = null
        } else {
            if (minX!! > maxX!!) minX = maxX.also { maxX = minX }
            if (minZ!! > maxZ!!) minZ = maxZ.also { maxZ = minZ }
            centerX = null; centerZ = null; radius = null
        }
        return ""
    }

    @Suppress("SENSELESS_COMPARISON")
    private fun findProblem(): String {
        if (id == null || !ID.matches(id.trim())) return "id"
        if (dimension == null || dimension.isBlank()) return "dimension"
        if (biomes == null) biomes = mutableListOf()
        if (!finiteOrNull(minY) || !finiteOrNull(maxY)) return "minY/maxY"
        return when (shape?.trim()?.lowercase()) {
            SHAPE_BOX -> if (listOf(minX, minZ, maxX, maxZ).all { it != null && it.isFinite() }) "" else "minX/minZ/maxX/maxZ"
            SHAPE_CIRCLE -> when {
                listOf(centerX, centerZ).any { it == null || !it.isFinite() } -> "centerX/centerZ"
                radius == null || !radius!!.isFinite() || radius!! <= 0.0 || radius!! > 100_000.0 -> "radius"
                else -> ""
            }
            else -> "shape"
        }
    }

    private fun finiteOrNull(v: Double?) = v == null || v.isFinite()

    companion object {
        const val SHAPE_BOX = "box"
        const val SHAPE_CIRCLE = "circle"
        private val ID = Regex("[a-z0-9_.\\-]{1,64}")

        @JvmStatic
        fun validId(id: String): Boolean = ID.matches(id)

        @JvmStatic
        fun withNamespace(id: String): String = if (id.contains(':')) id else "minecraft:$id"

        @JvmStatic
        fun circle(id: String, dimension: String, x: Double, z: Double, radius: Double): MewZone =
            MewZone(id, true, dimension, SHAPE_CIRCLE, centerX = x, centerZ = z, radius = radius)
    }
}

/**
 * The usable zones by dimension, rebuilt when the file or the test zones change. Looking up a
 * player is a few rectangle or circle tests over their dimension's zones; the biome is read only
 * once the position is inside one.
 */
class MewZoneSet(zones: List<MewZone>) {
    private val byDimension: Map<String, List<MewZone>> =
        zones.filter { it.enabled && it.problem.isEmpty() }.groupBy { it.dimension }

    val size: Int get() = byDimension.values.sumOf { it.size }

    /** The zones of this dimension containing the position, whatever their biomes. */
    fun containing(dimension: String, x: Double, y: Double, z: Double): List<MewZone> =
        byDimension[dimension]?.filter { it.contains(x, y, z) } ?: emptyList()

    /** The first zone containing the position whose biomes allow the one there, or null. */
    fun find(dimension: String, x: Double, y: Double, z: Double, biome: MewZone.BiomeTest): MewZone? {
        val list = byDimension[dimension] ?: return null
        for (zone in list) if (zone.contains(x, y, z) && zone.biomeAllowed(biome)) return zone
        return null
    }
}
