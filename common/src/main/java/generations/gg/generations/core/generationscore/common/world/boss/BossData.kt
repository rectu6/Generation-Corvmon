package generations.gg.generations.core.generationscore.common.world.boss

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import java.util.UUID

/**
 * What makes a Pokémon a boss, kept in Pokemon.persistentData under KEY (saved with
 * the entity, copied with the Pokémon). Only the server reads it; clients see a boss
 * through its forced aspects (BossRarity.aspect, TITAN_ASPECT).
 *
 * The compound is rewritten in place, so keys added by later lots (level, traits,
 * rewards…) are kept by older code.
 */
class BossData(
    /** Unique id of this boss (not the entity's nor the Pokémon's: survives a copy). */
    val id: UUID,
    val rarity: BossRarity,
    val titan: Boolean,
    /** Pokemon.scaleModifier before it became a boss, given back by Bosses.clear. */
    val originalScale: Float,
    /** Game time (Overworld) when it became a boss: its lifetime counts from there. */
    val spawnedAt: Long,
    /** natural (a converted natural spawn) or command (/boss spawn). */
    val source: String,
    /** Forced aspect of its Mega form (mega, mega_x, mega_y) for a Legendary boss, else empty. */
    val megaAspect: String = "",
    /** Its rewards were given (once per boss). */
    val rewarded: Boolean = false,
) {
    fun write(data: CompoundTag) {
        val tag = if (data.contains(KEY, Tag.TAG_COMPOUND.toInt())) data.getCompound(KEY) else CompoundTag()
        tag.putUUID(ID, id)
        tag.putString(RARITY, rarity.id)
        tag.putBoolean(TITAN, titan)
        tag.putFloat(SCALE, originalScale)
        tag.putLong(SPAWNED, spawnedAt)
        tag.putString(SOURCE, source)
        tag.putString(MEGA, megaAspect)
        tag.putBoolean(REWARDED, rewarded)
        data.put(KEY, tag)
    }

    companion object {
        const val KEY = "generations_boss"
        private const val ID = "id"
        private const val RARITY = "rarity"
        private const val TITAN = "titan"
        private const val SCALE = "scale"
        private const val SPAWNED = "spawned"
        private const val SOURCE = "source"
        private const val MEGA = "mega"
        private const val REWARDED = "rewarded"

        const val SOURCE_NATURAL = "natural"
        const val SOURCE_COMMAND = "command"

        fun has(data: CompoundTag) = data.contains(KEY, Tag.TAG_COMPOUND.toInt())

        /** Marks the boss as rewarded; false when it already was. */
        fun markRewarded(data: CompoundTag): Boolean {
            if (!has(data)) return false
            val tag = data.getCompound(KEY)
            if (tag.getBoolean(REWARDED)) return false
            tag.putBoolean(REWARDED, true)
            data.put(KEY, tag)
            return true
        }

        /** The boss data, or null when there is none or its rarity is unknown. */
        fun read(data: CompoundTag): BossData? {
            if (!has(data)) return null
            val tag = data.getCompound(KEY)
            val rarity = BossRarity.byId(tag.getString(RARITY)) ?: return null
            return BossData(
                id = if (tag.hasUUID(ID)) tag.getUUID(ID) else UUID(0L, 0L),
                rarity = rarity,
                titan = tag.getBoolean(TITAN),
                originalScale = tag.getFloat(SCALE).takeIf { it > 0f } ?: 1f,
                spawnedAt = tag.getLong(SPAWNED),
                source = tag.getString(SOURCE).ifEmpty { SOURCE_NATURAL },
                megaAspect = tag.getString(MEGA),
                rewarded = tag.getBoolean(REWARDED),
            )
        }
    }
}
