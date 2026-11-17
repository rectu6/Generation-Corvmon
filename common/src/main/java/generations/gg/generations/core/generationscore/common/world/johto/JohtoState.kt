package generations.gg.generations.core.generationscore.common.world.johto

/*
 * The Legend of Johto's states (JOHTO-PLAN.md § 2), pure (tested off-game by tools/JohtoBench):
 * no Minecraft class here.
 */

/** The three beasts, in the altar's order (Thunder, Flames, Rain). */
enum class JohtoBeast(val id: String, val species: String, val rgb: Int) {
    RAIKOU("raikou", "cobblemon:raikou", 0xFFD84A),
    ENTEI("entei", "cobblemon:entei", 0xFF6A2A),
    SUICUNE("suicune", "cobblemon:suicune", 0x5AD2FF);

    val awakeningScene: String get() = "${id}_awakening"
    val encounterScene: String get() = "${id}_encounter"
    val memoryId: String get() = "${id}_memory"

    companion object {
        fun of(id: String?): JohtoBeast? = entries.firstOrNull { it.id == id?.lowercase()?.removePrefix("cobblemon:") }
    }
}

/**
 * A beast's track for one player: IDLE → AWAKENING (ash consumed) → OPEN (the session waits for
 * the player in the trial zone) → RUNNING → won: ENCOUNTER (its memory owed, then given; the
 * encounter scene and the wild Pokémon) → IDLE. A failure: COOLDOWN, then OPEN again (no new ash),
 * at the round that failed (D10).
 */
enum class BeastState { IDLE, AWAKENING, OPEN, RUNNING, COOLDOWN, ENCOUNTER }

/** Ho-Oh's summoning: SUMMONING once the memories are consumed, until Ho-Oh appeared. */
enum class SummonState { IDLE, SUMMONING }

class BeastTrack {
    var state = BeastState.IDLE
    /** Grows with each ash: a session's number. */
    var session = 0
    /** The round to play (1 to 3), kept on a failure. */
    var round = 1
    var failures = 0
    /** When the failure's delay ends (epoch milliseconds, D9). */
    var cooldownUntil = 0L
    /** The memory of the last won session is still to be given (full inventory, a crash...). */
    var memoryOwed = false
    var memoriesGiven = 0
    var wins = 0
    /** The UUID of the last wild beast that appeared (none twice for one session). */
    var lastSpawn = ""
    /** The session whose beast appeared (never a second time for it). */
    var spawnedSession = 0
}

/** One player's legend (about 200 bytes saved). */
class JohtoRecord {
    var historySeen = false
    /** The scenes seen at least once (for skipping, D17). */
    val seen = LinkedHashSet<String>()
    val beasts = Array(3) { BeastTrack() }
    var summon = SummonState.IDLE
    var summons = 0
    var hoOhSpawned = 0

    fun track(beast: JohtoBeast): BeastTrack = beasts[beast.ordinal]

    /** As flat values (JohtoData writes them as NBT). */
    fun save(): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        out["history"] = historySeen
        out["seen"] = seen.joinToString(",")
        out["summon"] = summon.ordinal
        out["summons"] = summons
        out["hoOh"] = hoOhSpawned
        for ((i, b) in beasts.withIndex()) {
            out["b$i.state"] = b.state.ordinal
            out["b$i.session"] = b.session
            out["b$i.round"] = b.round
            out["b$i.failures"] = b.failures
            out["b$i.cooldown"] = b.cooldownUntil
            out["b$i.owed"] = b.memoryOwed
            out["b$i.memories"] = b.memoriesGiven
            out["b$i.wins"] = b.wins
            out["b$i.spawn"] = b.lastSpawn
            out["b$i.spawned"] = b.spawnedSession
        }
        return out
    }

    companion object {
        private fun int(values: Map<String, Any?>, key: String, fallback: Int = 0): Int = (values[key] as? Number)?.toInt() ?: fallback
        private fun long(values: Map<String, Any?>, key: String): Long = (values[key] as? Number)?.toLong() ?: 0L
        private fun bool(values: Map<String, Any?>, key: String): Boolean = when (val v = values[key]) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            else -> false
        }

        fun load(values: Map<String, Any?>): JohtoRecord = JohtoRecord().apply {
            historySeen = bool(values, "history")
            (values["seen"] as? String)?.split(',')?.filter { it.isNotBlank() }?.let { seen.addAll(it) }
            summon = SummonState.entries.getOrElse(int(values, "summon")) { SummonState.IDLE }
            summons = int(values, "summons")
            hoOhSpawned = int(values, "hoOh")
            for ((i, b) in beasts.withIndex()) {
                b.state = BeastState.entries.getOrElse(int(values, "b$i.state")) { BeastState.IDLE }
                b.session = int(values, "b$i.session")
                b.round = int(values, "b$i.round", 1).coerceIn(1, 3)
                b.failures = int(values, "b$i.failures")
                b.cooldownUntil = long(values, "b$i.cooldown")
                b.memoryOwed = bool(values, "b$i.owed")
                b.memoriesGiven = int(values, "b$i.memories")
                b.wins = int(values, "b$i.wins")
                b.lastSpawn = values["b$i.spawn"] as? String ?: ""
                b.spawnedSession = int(values, "b$i.spawned")
            }
        }
    }
}

/**
 * The transitions, each from one state only (a repeated packet or a second click changes
 * nothing). True when it happened.
 */
object JohtoStateMachine {
    const val ROUNDS = 3

    /** An ash consumed on a statue: a new session (IDLE only). */
    fun awaken(r: JohtoRecord, beast: JohtoBeast): Boolean {
        val b = r.track(beast)
        if (b.state != BeastState.IDLE) return false
        b.state = BeastState.AWAKENING
        b.session++
        b.round = 1
        b.failures = 0
        b.cooldownUntil = 0L
        return true
    }

    /** The awakening scene over (or cut short): the session waits for its trial. */
    fun opened(r: JohtoRecord, beast: JohtoBeast): Boolean {
        val b = r.track(beast)
        if (b.state != BeastState.AWAKENING) return false
        b.state = BeastState.OPEN
        return true
    }

    /** The player came into the trial's zone: it runs (at the round kept). */
    fun begin(r: JohtoRecord, beast: JohtoBeast): Boolean {
        val b = r.track(beast)
        if (b.state != BeastState.OPEN) return false
        b.state = BeastState.RUNNING
        return true
    }

    /** A round won (the next one is played after a failure, D10). */
    fun roundWon(r: JohtoRecord, beast: JohtoBeast): Boolean {
        val b = r.track(beast)
        if (b.state != BeastState.RUNNING || b.round >= ROUNDS) return false
        b.round++
        return true
    }

    /** Failed, left or disconnected: kept, a delay before trying again (no new ash). */
    fun fail(r: JohtoRecord, beast: JohtoBeast, now: Long, delayMs: Long): Boolean {
        val b = r.track(beast)
        if (b.state != BeastState.RUNNING) return false
        b.state = BeastState.COOLDOWN
        b.failures++
        b.cooldownUntil = now + delayMs.coerceAtLeast(0L)
        return true
    }

    /** The delay over (or cleared by an admin): the session is open again. */
    fun ready(r: JohtoRecord, beast: JohtoBeast, now: Long): Boolean {
        val b = r.track(beast)
        if (b.state != BeastState.COOLDOWN || now < b.cooldownUntil) return false
        b.state = BeastState.OPEN
        return true
    }

    /** The trial won: the session is consumed, its memory owed, its encounter to come. */
    fun win(r: JohtoRecord, beast: JohtoBeast): Boolean {
        val b = r.track(beast)
        if (b.state != BeastState.RUNNING) return false
        b.state = BeastState.ENCOUNTER
        b.memoryOwed = true
        b.wins++
        return true
    }

    /** Its memory given (once per won session). */
    fun memoryGiven(r: JohtoRecord, beast: JohtoBeast): Boolean {
        val b = r.track(beast)
        if (!b.memoryOwed) return false
        b.memoryOwed = false
        b.memoriesGiven++
        return true
    }

    /** The wild beast appeared, once per session (saved before it appears). */
    fun spawned(r: JohtoRecord, beast: JohtoBeast, uuid: String): Boolean {
        val b = r.track(beast)
        if (b.state != BeastState.ENCOUNTER || b.spawnedSession == b.session) return false
        b.state = BeastState.IDLE
        b.lastSpawn = uuid
        b.spawnedSession = b.session
        return true
    }

    /** The memories consumed at the altar: Ho-Oh is coming. */
    fun summon(r: JohtoRecord): Boolean {
        if (r.summon != SummonState.IDLE) return false
        r.summon = SummonState.SUMMONING
        return true
    }

    /** Ho-Oh appeared (once per summoning). */
    fun hoOhSpawned(r: JohtoRecord): Boolean {
        if (r.summon != SummonState.SUMMONING) return false
        r.summon = SummonState.IDLE
        r.summons++
        r.hoOhSpawned++
        return true
    }

    /** After a crash or a restart: a scene or a trial that was playing is open again (nothing lost). */
    fun recover(r: JohtoRecord): Boolean {
        var changed = false
        for (b in r.beasts) {
            if (b.state == BeastState.AWAKENING || b.state == BeastState.RUNNING) {
                b.state = BeastState.OPEN
                changed = true
            }
        }
        return changed
    }

    /** An admin clears a delay. */
    fun clearCooldown(r: JohtoRecord, beast: JohtoBeast): Boolean {
        val b = r.track(beast)
        if (b.state != BeastState.COOLDOWN) return false
        b.cooldownUntil = 0L
        b.state = BeastState.OPEN
        return true
    }

    /** An admin opens a session without ash (tests). */
    fun forceOpen(r: JohtoRecord, beast: JohtoBeast): Boolean {
        val b = r.track(beast)
        if (b.state != BeastState.IDLE) return false
        b.session++
        b.round = 1
        b.failures = 0
        b.state = BeastState.OPEN
        return true
    }

    /** An admin resets one beast. */
    fun reset(r: JohtoRecord, beast: JohtoBeast) {
        val b = r.track(beast)
        b.state = BeastState.IDLE
        b.round = 1
        b.failures = 0
        b.cooldownUntil = 0L
        b.memoryOwed = false
    }
}
