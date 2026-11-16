package generations.gg.generations.core.generationscore.common.world.rks.machine

/**
 * The RKS Machine's states (RKS-PLAN.md § 3.2): INCOMPLETE → ASSEMBLING → IDLE ⇄ CRAFTING /
 * ANALYSING → GROWING → AWAKENING → DAMAGED → REPAIRING → IDLE.
 */
enum class RksState {
    INCOMPLETE, ASSEMBLING, IDLE, CRAFTING, ANALYSING, GROWING, AWAKENING, DAMAGED, REPAIRING;

    /** The slots are locked (nothing put in or taken out of the inputs). */
    val locksSlots: Boolean get() = this == CRAFTING || this == ANALYSING || this == GROWING || this == AWAKENING || this == REPAIRING

    /** The modules cannot be broken in survival, pushed or blown up (D9; the short repair too). */
    val protectsModules: Boolean get() = this == GROWING || this == AWAKENING || this == REPAIRING

    /** Assembled: one model for the five modules. */
    val assembled: Boolean get() = this != INCOMPLETE

    companion object {
        fun byOrdinal(i: Int): RksState = entries.getOrElse(i) { INCOMPLETE }
    }
}

/**
 * The machine's logic, without Minecraft (tested off-game: double clicks, a double spawn, a
 * reload): the console's block entity holds one, ticks it and acts on what it returns. Times are
 * the level's game ticks.
 *
 * - revision: changes at every accepted action and every change of state. An action from a menu
 *   carries the revision its player saw; a second click with the same one is refused (the
 *   double click), and so is a click on a machine that changed in between.
 * - The experiment (Mewtwo) survives the machine being taken apart (only possible in creative while
 *   it grows): it waits, then goes on once assembled again.
 * - spawned: Mewtwo appeared, never twice (saved before it appears).
 */
class RksStateMachine {
    enum class Event { ASSEMBLED, CRAFT_DONE, ANALYSED, READY, SPAWN, FINISHED, REPAIRED, CANCELLED }

    var state = RksState.INCOMPLETE
        private set
    /** When the state began, and how long a timed state lasts (ticks). */
    var since = 0L
        private set
    var duration = 0
        private set
    /** The five modules are in place. */
    var assembled = false
        private set
    /** The containment is destroyed (also kept by the chamber itself). */
    var damaged = false
    /** The samples were analysed: the research files' slots are open. */
    var analysed = false
        private set
    /** A Mewtwo experiment is under way (growing or awakening). */
    var experiment = false
        private set
    /** Growth done and needed (ticks), and its speed (/generations rks speed). */
    var growth = 0.0
        private set
    var growthTotal = 6000.0
        private set
    var speed = 1.0
    var spawned = false
        private set
    var revision = 0
        private set

    val progress: Double get() = if (growthTotal <= 0.0) 1.0 else (growth / growthTotal).coerceIn(0.0, 1.0)

    val ready: Boolean get() = experiment && growth >= growthTotal

    private fun enter(next: RksState, now: Long, ticks: Int = 0) {
        state = next
        since = now
        duration = ticks
        revision++
    }

    /** The state once assembled: the experiment goes on, or the damage shows, or it waits. */
    private fun settled(): RksState = when {
        experiment -> RksState.GROWING
        damaged -> RksState.DAMAGED
        else -> RksState.IDLE
    }

    /** A click that carries the revision its player saw: accepted once. */
    fun accept(seen: Int): Boolean {
        if (seen != revision) return false
        revision++
        return true
    }

    /** Something changed that the menus show (energy, slots' meaning): their clicks are stale. */
    fun touch() {
        revision++
    }

    /**
     * The structure was checked: complete or not. While Mewtwo awakens nothing changes (the scene
     * ends first). CANCELLED when a craft or an analysis stopped.
     */
    fun structure(complete: Boolean, now: Long, assemblyTicks: Int): Event? {
        assembled = complete
        if (state == RksState.AWAKENING) return null
        if (complete) {
            if (state != RksState.INCOMPLETE) return null
            if (assemblyTicks <= 0) {
                enter(settled(), now)
                return Event.ASSEMBLED
            }
            enter(RksState.ASSEMBLING, now, assemblyTicks)
            return null
        }
        if (state == RksState.INCOMPLETE) return null
        val cancelled = state == RksState.CRAFTING || state == RksState.ANALYSING || state == RksState.REPAIRING
        enter(RksState.INCOMPLETE, now)
        return if (cancelled) Event.CANCELLED else null
    }

    fun startCraft(now: Long, ticks: Int): Boolean {
        if (state != RksState.IDLE) return false
        enter(RksState.CRAFTING, now, ticks.coerceAtLeast(1))
        return true
    }

    /** The craft's result was given (or could not be: false keeps it waiting at 100 %). */
    fun finishCraft(now: Long) {
        if (state == RksState.CRAFTING) enter(RksState.IDLE, now)
    }

    fun startAnalysis(now: Long, ticks: Int): Boolean {
        if (state != RksState.IDLE || analysed || experiment) return false
        enter(RksState.ANALYSING, now, ticks.coerceAtLeast(1))
        return true
    }

    fun startExperiment(now: Long, growthTicks: Double): Boolean {
        if (state != RksState.IDLE || !analysed || experiment || damaged) return false
        experiment = true
        analysed = false
        spawned = false
        growth = 0.0
        growthTotal = growthTicks.coerceAtLeast(1.0)
        enter(RksState.GROWING, now)
        return true
    }

    /** Growth set by a test command (0 to 1). */
    fun setProgress(fraction: Double) {
        if (!experiment) return
        growth = growthTotal * fraction.coerceIn(0.0, 1.0)
        revision++
    }

    /** Grown and someone is near: the awakening begins. */
    fun awaken(now: Long): Boolean {
        if (state != RksState.GROWING || !ready) return false
        enter(RksState.AWAKENING, now)
        return true
    }

    /** Mewtwo appears now: true the first time only. */
    fun markSpawned(): Boolean {
        if (spawned) return false
        spawned = true
        revision++
        return true
    }

    /**
     * One tick. delta: the growth's ticks since the last one (1 on the loaded clock, the world's
     * time on the other). sceneRunning: the awakening's cinematic still plays.
     */
    fun tick(now: Long, delta: Double = 1.0, sceneRunning: Boolean = false): Event? {
        val elapsed = now - since
        return when (state) {
            RksState.ASSEMBLING -> if (elapsed >= duration) {
                enter(settled(), now)
                Event.ASSEMBLED
            } else null
            RksState.CRAFTING -> if (elapsed >= duration) Event.CRAFT_DONE else null
            RksState.ANALYSING -> if (elapsed >= duration) {
                analysed = true
                enter(RksState.IDLE, now)
                Event.ANALYSED
            } else null
            RksState.GROWING -> {
                if (growth < growthTotal) growth = minOf(growthTotal, growth + delta.coerceAtLeast(0.0) * speed)
                if (growth >= growthTotal) Event.READY else null
            }
            RksState.AWAKENING -> when {
                sceneRunning -> null
                !spawned -> Event.SPAWN
                else -> {
                    experiment = false
                    growth = 0.0
                    damaged = true
                    enter(if (assembled) RksState.DAMAGED else RksState.INCOMPLETE, now)
                    Event.FINISHED
                }
            }
            RksState.REPAIRING -> if (elapsed >= duration) {
                damaged = false
                enter(RksState.IDLE, now)
                Event.REPAIRED
            } else null
            else -> null
        }
    }

    fun damage(now: Long): Boolean {
        if (!assembled || state.locksSlots) return false
        damaged = true
        analysed = false
        enter(RksState.DAMAGED, now)
        return true
    }

    fun startRepair(now: Long, ticks: Int): Boolean {
        if (state != RksState.DAMAGED) return false
        enter(RksState.REPAIRING, now, ticks.coerceAtLeast(1))
        return true
    }

    /** Repaired at once (test command). */
    fun repair(now: Long) {
        damaged = false
        if (state == RksState.DAMAGED || state == RksState.REPAIRING) enter(RksState.IDLE, now)
    }

    /** The experiment cancelled: nothing given back, nothing made (test command). */
    fun reset(now: Long) {
        experiment = false
        analysed = false
        spawned = false
        growth = 0.0
        enter(if (!assembled) RksState.INCOMPLETE else if (damaged) RksState.DAMAGED else RksState.IDLE, now)
    }

    // ---------------------------------------------------------------- saving (as plain values)

    fun save(out: MutableMap<String, Any>) {
        out["state"] = state.ordinal
        out["since"] = since
        out["duration"] = duration
        out["assembled"] = assembled
        out["damaged"] = damaged
        out["analysed"] = analysed
        out["experiment"] = experiment
        out["growth"] = growth
        out["growthTotal"] = growthTotal
        out["speed"] = speed
        out["spawned"] = spawned
        out["revision"] = revision
    }

    fun load(values: Map<String, Any?>) {
        fun int(key: String) = (values[key] as? Number)?.toInt() ?: 0
        fun bool(key: String) = values[key] as? Boolean ?: false
        fun double(key: String, default: Double) = (values[key] as? Number)?.toDouble() ?: default
        state = RksState.byOrdinal(int("state"))
        since = (values["since"] as? Number)?.toLong() ?: 0L
        duration = int("duration")
        assembled = bool("assembled")
        damaged = bool("damaged")
        analysed = bool("analysed")
        experiment = bool("experiment")
        growthTotal = double("growthTotal", 6000.0).coerceAtLeast(1.0)
        growth = double("growth", 0.0).coerceIn(0.0, growthTotal)
        speed = double("speed", 1.0).coerceIn(0.0, 1000.0)
        spawned = bool("spawned")
        revision = int("revision")
    }
}
