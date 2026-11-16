package generations.gg.generations.core.generationscore.common.world.cinematic

/** The legendary cinematics, by id (the same on both sides). */
object CinematicScenes {
    const val ARTICUNO = "articuno"
    const val ZAPDOS = "zapdos"
    const val MOLTRES = "moltres"

    val ALL = listOf(ARTICUNO, ZAPDOS, MOLTRES)

    /** Scenes this build can play. */
    val AVAILABLE = setOf(ARTICUNO, ZAPDOS, MOLTRES)

    /** Every scene is written for 20 seconds; a longer or shorter duration stretches it whole. */
    const val WRITTEN_SECONDS = 20.0

    const val MIN_SECONDS = 15.0
    const val MAX_SECONDS = 25.0

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
