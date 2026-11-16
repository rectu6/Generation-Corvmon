package generations.gg.generations.core.generationscore.common.world.cinematic

/** The legendary cinematics, by id (the same on both sides). */
object CinematicScenes {
    const val ARTICUNO = "articuno"
    const val ZAPDOS = "zapdos"
    const val MOLTRES = "moltres"
    /** The awakening of Mewtwo in the RKS Machine (RKS-PLAN.md § 5.2, D11). */
    const val MEWTWO = "mewtwo"

    val ALL = listOf(ARTICUNO, ZAPDOS, MOLTRES, MEWTWO)

    /** Scenes this build can play. */
    val AVAILABLE = setOf(ARTICUNO, ZAPDOS, MOLTRES, MEWTWO)

    /** The birds' scenes are written for 20 seconds; a longer or shorter duration stretches a scene whole. */
    const val WRITTEN_SECONDS = 20.0

    /** Mewtwo's is written for 24. */
    const val MEWTWO_SECONDS = 24.0

    const val MIN_SECONDS = 15.0
    const val MAX_SECONDS = 25.0

    /** The seconds a scene is written for. */
    fun writtenSeconds(scene: String): Double = if (scene == MEWTWO) MEWTWO_SECONDS else WRITTEN_SECONDS

    /** The shortest and longest a scene may be stretched to (three quarters to five quarters). */
    fun minSeconds(scene: String): Double = writtenSeconds(scene) * 0.75

    fun maxSeconds(scene: String): Double = writtenSeconds(scene) * 1.25

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
