package generations.gg.generations.core.generationscore.common.world.ultraspace.worlds

import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraSpaceConfig
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level

/**
 * The seven Ultra Space worlds: real Minecraft dimensions, made by the mod's data
 * (data/generations_core/dimension, written by tools/ultra_worlds.py), persistent and
 * shared by every player, one per destination (never one per journey).
 *
 * Their ids are fixed here and in the data: saves name them (the chunks are kept
 * under dimensions/generations_core/<id>), so they must never change. The config can
 * only close a world (UltraWorldsConfig) or lead a destination to another of these
 * seven (portals.destinations[].dimension).
 */
object UltraWorlds {
    /** Destination ids, which are also the dimensions' paths. */
    val IDS = listOf("ultra_jungle", "ultra_desert", "ultra_central", "ultra_crater", "ultra_forest", "ultra_abyss", "ultra_skyscraper")

    val KEYS: Map<String, ResourceKey<Level>> = IDS.associateWith { ResourceKey.create(Registries.DIMENSION, GenerationsCore.id(it)) }

    fun key(id: String): ResourceKey<Level>? = KEYS[id]

    fun isUltra(key: ResourceKey<Level>): Boolean = idOf(key) != null

    /** The world's id (ultra_jungle...) of a dimension, null when it is not one of the seven. */
    fun idOf(key: ResourceKey<Level>): String? =
        key.location().takeIf { it.namespace == GenerationsCore.MOD_ID && it.path in IDS }?.path

    private val warned = HashSet<String>()

    /**
     * The world a portal destination leads to: its config's dimension when it names one
     * of the seven worlds, else the world of the destination's own id (and a warning,
     * once). Null when neither is an Ultra Space world.
     */
    fun forDestination(destination: String): ResourceKey<Level>? {
        val configured = UltraSpaceConfig.ride.portals.destinations.firstOrNull { it.id == destination }?.dimension
        val key = configured?.let { ResourceLocation.tryParse(it) }?.let { ResourceKey.create(Registries.DIMENSION, it) }
        if (key != null && isUltra(key)) return key
        if (configured != null && warned.add("$destination=$configured")) {
            GenerationsCore.LOGGER.warn("Ultra Space: destination {} leads to {}, which is not an Ultra Space world; {} used", destination, configured, KEYS[destination]?.location())
        }
        return KEYS[destination]
    }

    /** Open to arrivals (UltraWorldsConfig). */
    fun enabled(key: ResourceKey<Level>): Boolean = idOf(key)?.let { UltraWorldsConfig.settings.worlds[it]?.enabled != false } ?: false

    /** The level of a world open to arrivals and loaded by the server, else null. */
    fun level(server: MinecraftServer, key: ResourceKey<Level>?): ServerLevel? =
        key?.takeIf { enabled(it) }?.let { server.getLevel(it) }

    /** Whether a destination's world can be reached now. */
    fun available(server: MinecraftServer, destination: String): Boolean = level(server, forDestination(destination)) != null

    /**
     * The seed of a world's relief (ChunkMapMixin): the save's own seed mixed with the
     * world's id, so that the seven worlds do not share the Overworld's shape (they use
     * its noise router). The same for a save forever: reproducible.
     */
    @JvmStatic
    fun terrainSeed(level: ServerLevel, seed: Long): Long =
        if (isUltra(level.dimension())) seed xor (level.dimension().location().toString().hashCode().toLong() * -0x61c8864680b583ebL) else seed

    /** Whether a dimension is one of the seven (WorldDimensionsMixin: no "experimental settings" warning for them). */
    @JvmStatic
    fun isUltraStem(location: ResourceLocation): Boolean = location.namespace == GenerationsCore.MOD_ID && location.path in IDS
}
