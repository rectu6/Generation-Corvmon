package generations.gg.generations.core.generationscore.common.world.cinematic

/** The legendary cinematics, by id (the same on both sides). */
object CinematicScenes {
    const val ARTICUNO = "articuno"
    const val ZAPDOS = "zapdos"
    const val MOLTRES = "moltres"
    /** The awakening of Mewtwo in the RKS Machine (RKS-PLAN.md § 5.2, D11). */
    const val MEWTWO = "mewtwo"
    /** Mew's game won (MEW-PLAN.md § 10). */
    const val MEW = "mew"

    val ALL = listOf(ARTICUNO, ZAPDOS, MOLTRES, MEWTWO, MEW)

    /** Scenes this build can play. */
    val AVAILABLE = setOf(ARTICUNO, ZAPDOS, MOLTRES, MEWTWO, MEW)

    /** The birds' scenes are written for 20 seconds; a longer or shorter duration stretches a scene whole. */
    const val WRITTEN_SECONDS = 20.0

    /** Mewtwo's is written for 24. */
    const val MEWTWO_SECONDS = 24.0

    const val MIN_SECONDS = 15.0
    const val MAX_SECONDS = 25.0

    /** Mew's is written for 24 too (20 to 25 allowed). */
    const val MEW_SECONDS = 24.0

    /** The seconds a scene is written for. */
    fun writtenSeconds(scene: String): Double = when (scene) {
        MEWTWO -> MEWTWO_SECONDS
        MEW -> MEW_SECONDS
        else -> WRITTEN_SECONDS
    }

    /** The shortest and longest a scene may be stretched to (three quarters to five quarters; Mew 20 to 25 s). */
    fun minSeconds(scene: String): Double = if (scene == MEW) 20.0 else writtenSeconds(scene) * 0.75

    fun maxSeconds(scene: String): Double = if (scene == MEW) 25.0 else writtenSeconds(scene) * 1.25

    /** Whether the players near it who cannot watch get its ambience (Mewtwo's, D11). */
    fun hasAmbience(scene: String): Boolean = scene == MEWTWO

    /**
     * How long before the scene's end the Pokémon appears: the birds 1 s; Mewtwo and Mew right at
     * the end, where the actor stands (no second one on screen for a second).
     */
    fun spawnLeadTicks(scene: String): Int = if (scene == MEWTWO || scene == MEW) 2 else 20

    /** The Pokémon of each scene by default (cinematics.json can change it). */
    fun defaultSpecies(scene: String): String = "cobblemon:$scene"
}

/** Why a cinematic ended, as the clients are told. */
enum class CinematicEndReason {
    /** Played to its end. */
    FINISHED,
    /** /generations cinematic stop. */
    STOPPED,
    /** The invoker disconnected, died or left the dimension. */
    INVOKER_LEFT,
    /** This spectator disconnected, died or left the dimension (only they are told). */
    LEFT,
    SERVER_STOPPING,
}
