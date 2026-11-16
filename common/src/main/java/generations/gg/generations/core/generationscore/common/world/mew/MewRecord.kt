package generations.gg.generations.core.generationscore.common.world.mew

/**
 * Where a hunt stands (MEW-PLAN.md §§ 5 to 12). HUNTING → UNLOCKED (a draw succeeded, Mew waits
 * for the player to be free) → CHALLENGE (the three rounds) → WON, or COOLDOWN after a failure
 * (active minutes on an island, then CHALLENGE again, no new draw) → WON → RELEASED (the real Mew
 * is out, once) → HUNTING again after its capture.
 */
enum class MewState {
    HUNTING, UNLOCKED, CHALLENGE, COOLDOWN, WON, RELEASED;

    companion object {
        /** An unknown name (a file from another version) keeps the hunt going rather than losing it. */
        @JvmStatic
        fun byName(name: String?): MewState = entries.firstOrNull { it.name == name } ?: HUNTING
    }
}

/**
 * One player's hunt, saved with the world (MewHuntData, about 60 bytes): no position, no path.
 * Pure: written to and read from a plain map of numbers and strings, which MewHuntData turns into
 * NBT (the bench checks the round trip without Minecraft).
 */
class MewRecord {
    var state = MewState.HUNTING

    /** Active presence on the islands, in ticks, whichever island. */
    var activeTicks = 0L

    /** Active ticks at which the next draw is made (-1: to work out from the counter). */
    var nextDrawTicks = -1L

    /** Draws made in this hunt (the guarantee included). */
    var draws = 0

    /** Active ticks of the next false appearance's draw (-1: not chosen yet; conversation 2). */
    var nextManifestTicks = -1L

    /** Active ticks left before Mew comes back after a failure (conversation 3). */
    var cooldownTicks = 0L

    /** Challenges started in this hunt. */
    var attempts = 0

    /** The real Mew, once out ("" when none). */
    var mewUuid = ""

    /** When it came out (milliseconds since 1970, 0 when none). */
    var releasedAtMs = 0L

    /** The counter when the draw succeeded (-1 while hunting). */
    var unlockedAtTicks = -1L

    /** Hunts completed (Mew caught by this player), kept by a reset. */
    var completed = 0

    fun save(): Map<String, Any> = linkedMapOf(
        "state" to state.name,
        "active" to activeTicks,
        "nextDraw" to nextDrawTicks,
        "draws" to draws,
        "nextManifest" to nextManifestTicks,
        "cooldown" to cooldownTicks,
        "attempts" to attempts,
        "mew" to mewUuid,
        "releasedAt" to releasedAtMs,
        "unlockedAt" to unlockedAtTicks,
        "completed" to completed,
    )

    companion object {
        /** Missing keys keep their default; numbers of any kind are accepted. */
        @JvmStatic
        fun load(map: Map<String, Any?>): MewRecord = MewRecord().apply {
            fun long(key: String, fallback: Long) = (map[key] as? Number)?.toLong() ?: fallback
            fun int(key: String, fallback: Int) = (map[key] as? Number)?.toInt() ?: fallback
            state = MewState.byName(map["state"] as? String)
            activeTicks = long("active", 0L).coerceAtLeast(0L)
            nextDrawTicks = long("nextDraw", -1L)
            draws = int("draws", 0).coerceAtLeast(0)
            nextManifestTicks = long("nextManifest", -1L)
            cooldownTicks = long("cooldown", 0L).coerceAtLeast(0L)
            attempts = int("attempts", 0).coerceAtLeast(0)
            mewUuid = map["mew"] as? String ?: ""
            releasedAtMs = long("releasedAt", 0L)
            unlockedAtTicks = long("unlockedAt", -1L)
            completed = int("completed", 0).coerceAtLeast(0)
        }
    }
}
