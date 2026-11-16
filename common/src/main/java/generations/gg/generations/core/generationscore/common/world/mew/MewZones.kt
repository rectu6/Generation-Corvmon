package generations.gg.generations.core.generationscore.common.world.mew

import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.config.JsonConfigs
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.level.biome.Biome
import java.nio.file.Files

/**
 * The Mew islands (MEW-PLAN.md § 3): config/generations/core/mew/mew_zones.json, read at server
 * start and by the reload commands, written by zone add and zone remove; plus test zones kept in
 * memory only (zone test, lost at the next start). Shipped with two example islands, disabled.
 */
object MewZones {
    const val VERSION = 1

    /** The file's content. */
    class ZonesFile(var zones: MutableList<MewZone> = examples())

    private val file = JsonConfigs.file("mew", "mew_zones.json")

    private var fileZones: MutableList<MewZone> = mutableListOf()
    private val testZones = ArrayList<MewZone>()
    private var set = MewZoneSet(emptyList())

    fun reload() {
        val read = JsonConfigs.load("Mew zones", file, ZonesFile::class.java, ::ZonesFile, VERSION, { check(it) })
        fileZones = read.zones
        rebuild()
    }

    @Suppress("SENSELESS_COMPARISON")
    private fun check(read: ZonesFile) {
        if (read.zones == null) read.zones = mutableListOf()
        read.zones.removeIf { it == null }
        val seen = HashSet<String>()
        for (zone in read.zones) {
            val problem = zone.normalize()
            if (problem.isNotEmpty()) {
                GenerationsCore.LOGGER.warn("Mew zones: zone '{}' ignored (wrong or missing {})", zone.id, problem)
            } else if (!seen.add(zone.id)) {
                zone.problem = "id"
                GenerationsCore.LOGGER.warn("Mew zones: zone '{}' ignored (the id is used twice)", zone.id)
            }
        }
    }

    private fun rebuild() {
        set = MewZoneSet(fileZones + testZones)
    }

    /** The file's zones then the test zones. */
    fun all(): List<MewZone> = fileZones + testZones

    fun isTest(zone: MewZone): Boolean = testZones.any { it === zone }

    fun usableCount(): Int = set.size

    fun exists(id: String): Boolean = all().any { it.id == id }

    /** A test zone around the player, in memory (replaces their previous one). */
    fun putTest(zone: MewZone) {
        zone.normalize()
        testZones.removeIf { it.id == zone.id }
        testZones.add(zone)
        rebuild()
    }

    /** A new zone written to the file at once. False if it could not be written. */
    fun add(zone: MewZone): Boolean {
        zone.normalize()
        fileZones.add(zone)
        rebuild()
        return save()
    }

    /** Removes a test zone or a file zone (the file is written). Null when there is none of that id; otherwise whether it was a test zone. */
    fun remove(id: String): Boolean? {
        if (testZones.removeIf { it.id == id }) {
            rebuild()
            return true
        }
        if (!fileZones.removeIf { it.id == id }) return null
        rebuild()
        save()
        return false
    }

    private fun save(): Boolean = try {
        Files.createDirectories(file.parent)
        val tree = JsonConfigs.gson.toJsonTree(ZonesFile(fileZones)).asJsonObject
        tree.addProperty("version", VERSION)
        Files.writeString(file, JsonConfigs.gson.toJson(tree))
        true
    } catch (e: Exception) {
        GenerationsCore.LOGGER.error("Mew zones: could not write {}", file, e)
        false
    }

    /** Matches the list's entries against the biome there. */
    fun biomeTest(biome: Holder<Biome>): MewZone.BiomeTest = MewZone.BiomeTest { entry ->
        if (entry.startsWith("#")) {
            val tag = ResourceLocation.tryParse(entry.substring(1))
            tag != null && biome.`is`(TagKey.create(Registries.BIOME, tag))
        } else {
            val id = ResourceLocation.tryParse(entry)
            id != null && biome.`is`(id)
        }
    }

    /** The zone the player is in, its biome allowed, or null. The biome is read only inside a zone (its chunk is loaded). */
    fun at(player: ServerPlayer): MewZone? {
        val dimension = player.level().dimension().location().toString()
        val inside = set.containing(dimension, player.x, player.y, player.z)
        if (inside.isEmpty()) return null
        val test = biomeTest(player.level().getBiome(player.blockPosition()))
        return inside.firstOrNull { it.biomeAllowed(test) }
    }

    /** The zones containing the player's position, whatever their biomes (zone here). */
    fun containing(player: ServerPlayer): List<MewZone> =
        set.containing(player.level().dimension().location().toString(), player.x, player.y, player.z)

    private fun examples(): MutableList<MewZone> = mutableListOf(
        MewZone(
            "ile_ouest", false, "minecraft:overworld", MewZone.SHAPE_BOX,
            minX = -8200.0, minZ = 3100.0, maxX = -8020.0, maxZ = 3280.0, minY = -64.0, maxY = 320.0,
            biomes = mutableListOf("#minecraft:is_forest", "#cobblemon:is_jungle", "minecraft:flower_forest"),
        ),
        MewZone("ile_nord", false, "minecraft:overworld", MewZone.SHAPE_CIRCLE, centerX = 1500.0, centerZ = -9000.0, radius = 100.0),
    )
}
