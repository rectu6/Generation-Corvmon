package generations.gg.generations.core.generationscore.common.world.ultraspace.worlds

import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpOutcome
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Each player's travels between the worlds, saved with the save (the Overworld's
 * data, as UltraWarpRecords):
 * - home: where they stood before leaving for an Ultra Space world (not replaced while
 *   they travel from one Ultra Space world to another), for the return;
 * - pending: a journey through a portal whose arrival is not done yet (cinematic or
 *   loading cut by a disconnection or a server stop): finished at their next login,
 *   once only (by the journey's id);
 * - stay: the journey that brought them where they are (destination, portal level,
 *   light-years), for the encounters of a later phase; cleared by the return.
 */
class UltraWarpTravellers : SavedData() {
    class Place(val dimension: ResourceKey<Level>, val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float) {
        fun save(): CompoundTag = CompoundTag().apply {
            putString("dimension", dimension.location().toString())
            putDouble("x", x)
            putDouble("y", y)
            putDouble("z", z)
            putFloat("yaw", yaw)
            putFloat("pitch", pitch)
        }

        override fun toString() = "${dimension.location()} %.1f %.1f %.1f".format(x, y, z)

        companion object {
            fun of(player: ServerPlayer) = Place(player.level().dimension(), player.x, player.y, player.z, player.yRot, player.xRot)

            fun load(tag: CompoundTag): Place? {
                val location = ResourceLocation.tryParse(tag.getString("dimension")) ?: return null
                return Place(ResourceKey.create(Registries.DIMENSION, location), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("yaw"), tag.getFloat("pitch"))
            }
        }
    }

    class Traveller {
        var home: Place? = null
        var pending: UltraWarpOutcome? = null
        var stay: UltraWarpOutcome? = null
    }

    private val travellers = HashMap<UUID, Traveller>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.put("travellers", ListTag().apply {
            travellers.forEach { (player, traveller) ->
                add(CompoundTag().apply {
                    putUUID("player", player)
                    traveller.home?.let { put("home", it.save()) }
                    traveller.pending?.let { put("pending", it.save()) }
                    traveller.stay?.let { put("stay", it.save()) }
                })
            }
        })
        return tag
    }

    companion object {
        private const val NAME = "generations_ultra_warp_travellers"

        private fun of(server: MinecraftServer): UltraWarpTravellers =
            server.overworld().dataStorage.computeIfAbsent(Factory(::UltraWarpTravellers, ::load, null), NAME)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = UltraWarpTravellers().apply {
            tag.getList("travellers", Tag.TAG_COMPOUND.toInt()).forEach { entry ->
                val compound = entry as CompoundTag
                runCatching {
                    travellers[compound.getUUID("player")] = Traveller().apply {
                        if (compound.contains("home")) home = Place.load(compound.getCompound("home"))
                        if (compound.contains("pending")) pending = UltraWarpOutcome.load(compound.getCompound("pending"))
                        if (compound.contains("stay")) stay = UltraWarpOutcome.load(compound.getCompound("stay"))
                    }
                }
            }
        }

        fun get(server: MinecraftServer, player: UUID): Traveller? = of(server).travellers[player]

        /** Changes the player's entry and saves it with the world. */
        fun edit(server: MinecraftServer, player: UUID, change: (Traveller) -> Unit) {
            val data = of(server)
            change(data.travellers.getOrPut(player) { Traveller() })
            data.setDirty()
        }
    }
}
