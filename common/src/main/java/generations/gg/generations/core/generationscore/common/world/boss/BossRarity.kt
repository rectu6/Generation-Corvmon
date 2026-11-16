package generations.gg.generations.core.generationscore.common.world.boss

/**
 * The five boss rarities, from the most common to the rarest. The values here are
 * the defaults of rarities.json (config/generations/core/bosses/), which can change
 * them all but the ids. The id is saved with each boss and synced to clients through
 * the forced aspect gc_boss_<id>: never rename one.
 */
enum class BossRarity(
    val id: String,
    /** Colour of the name, the aura and the particles (RGB). */
    val defaultColor: Int,
    /** Weight of the rarity in the draw of a natural boss (out of 100 by default). */
    val defaultWeight: Double,
    /** Levels added to the boss's level (used from lot B: adaptive level). */
    val defaultLevelBonus: Int,
    /** Size multiplier of the boss, capped by general.json's maxHeightBlocks. */
    val defaultScale: Float,
) {
    COMMON("common", 0xD8D8D8, 50.0, 0, 1.25f),
    UNCOMMON("uncommon", 0x55FF55, 28.0, 3, 1.4f),
    RARE("rare", 0x5599FF, 15.0, 6, 1.6f),
    EPIC("epic", 0xAA55FF, 6.0, 10, 1.85f),
    LEGENDARY("legendary", 0xFFAA00, 1.0, 15, 2.2f);

    /** Forced aspect of a boss of this rarity, synced to clients (aura, particles, name). */
    val aspect: String get() = ASPECT_PREFIX + id

    /** "Common", "Épique"…: the rarity alone. */
    val translationKey: String get() = "generations_core.boss.rarity.$id"

    /** "Common Boss", "Boss épique"…: what the name of the Pokémon shows. */
    val tierKey: String get() = "generations_core.boss.tier.$id"

    companion object {
        const val ASPECT_PREFIX = "gc_boss_"
        /** Forced aspect of a Titan boss (the "Alpha" of the specification, see BOSS-PLAN.md D1). */
        const val TITAN_ASPECT = "gc_boss_titan"
        /** Colour of the Titan's mark (name suffix, outer ring, particles). */
        const val TITAN_COLOR = 0xC0162E

        fun byId(id: String?): BossRarity? {
            val key = id?.trim() ?: return null
            return entries.firstOrNull { it.id.equals(key, ignoreCase = true) }
        }

        /** The rarity carried by a set of aspects (the client's view of a boss), or null. */
        fun fromAspects(aspects: Collection<String>): BossRarity? {
            if (aspects.isEmpty()) return null
            for (i in entries.indices.reversed()) if (entries[i].aspect in aspects) return entries[i]
            return null
        }

        fun isBossAspect(aspect: String) = aspect.startsWith(ASPECT_PREFIX)
    }
}
