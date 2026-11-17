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
    /** The Passage of the Rainbow: the signal and Ho-Oh's passage (HOOH-PLAN.md § 6), 13 s, one player. */
    const val HO_OH = "ho_oh"

    /** The Legend of Johto (JOHTO-PLAN.md): the tower's history, 50 s (40 to 60). */
    const val JOHTO_HISTORY = "johto_history"
    /** A beast's statue awakening, 20 s (15 to 25). */
    const val RAIKOU_AWAKENING = "raikou_awakening"
    const val ENTEI_AWAKENING = "entei_awakening"
    const val SUICUNE_AWAKENING = "suicune_awakening"
    /** A beast's encounter after its trial, 22 s (20 to 25). */
    const val RAIKOU_ENCOUNTER = "raikou_encounter"
    const val ENTEI_ENCOUNTER = "entei_encounter"
    const val SUICUNE_ENCOUNTER = "suicune_encounter"
    /** Ho-Oh summoned at the Bell Tower with the three memories, 30 s (25 to 35). */
    const val HO_OH_SUMMON = "ho_oh_summon"
    /** Lugia summoned at its shrine with the Melody Flute (LUGIA-PLAN.md), 31 s (25 to 35). */
    const val LUGIA = "lugia"

    /** The Regis woken at their shrines (REGIS-PLAN.md): 21 s (15 to 25), Regigigas 27 s (20 to 30). */
    const val REGIROCK = "regirock"
    const val REGICE = "regice"
    const val REGISTEEL = "registeel"
    const val REGIELEKI = "regieleki"
    const val REGIDRAGO = "regidrago"
    const val REGIGIGAS = "regigigas"

    /** The Eon duo summoned with the Enigma Stone (EON-PLAN.md § 6): 23 s each (20 to 25). */
    const val LATIAS = "latias"
    const val LATIOS = "latios"

    val AWAKENINGS = setOf(RAIKOU_AWAKENING, ENTEI_AWAKENING, SUICUNE_AWAKENING)
    val ENCOUNTERS = setOf(RAIKOU_ENCOUNTER, ENTEI_ENCOUNTER, SUICUNE_ENCOUNTER)
    val REGIS = setOf(REGIROCK, REGICE, REGISTEEL, REGIELEKI, REGIDRAGO, REGIGIGAS)
    val EON = setOf(LATIAS, LATIOS)

    val ALL = listOf(
        ARTICUNO, ZAPDOS, MOLTRES, MEWTWO, MEW, HO_OH,
        JOHTO_HISTORY, RAIKOU_AWAKENING, ENTEI_AWAKENING, SUICUNE_AWAKENING,
        RAIKOU_ENCOUNTER, ENTEI_ENCOUNTER, SUICUNE_ENCOUNTER, HO_OH_SUMMON, LUGIA,
        REGIROCK, REGICE, REGISTEEL, REGIELEKI, REGIDRAGO, REGIGIGAS,
        LATIAS, LATIOS,
    )

    /** Scenes this build can play. */
    val AVAILABLE = ALL.toSet()

    /** Ho-Oh's is written for 13 (not stretched: the pursuit starts where it ends). */
    const val HO_OH_SECONDS = 13.0

    /** The birds' scenes are written for 20 seconds; a longer or shorter duration stretches a scene whole. */
    const val WRITTEN_SECONDS = 20.0

    /** Mewtwo's is written for 24. */
    const val MEWTWO_SECONDS = 24.0

    const val MIN_SECONDS = 15.0
    const val MAX_SECONDS = 25.0

    /** Mew's is written for 24 too (20 to 25 allowed). */
    const val MEW_SECONDS = 24.0

    const val JOHTO_HISTORY_SECONDS = 50.0
    const val AWAKENING_SECONDS = 20.0
    const val ENCOUNTER_SECONDS = 22.0
    const val HO_OH_SUMMON_SECONDS = 30.0
    const val LUGIA_SECONDS = 31.0
    const val REGI_SECONDS = 21.0
    const val REGIGIGAS_SECONDS = 27.0
    const val EON_SECONDS = 23.0

    /** The seconds a scene is written for. */
    fun writtenSeconds(scene: String): Double = when (scene) {
        MEWTWO -> MEWTWO_SECONDS
        MEW -> MEW_SECONDS
        HO_OH -> HO_OH_SECONDS
        JOHTO_HISTORY -> JOHTO_HISTORY_SECONDS
        in AWAKENINGS -> AWAKENING_SECONDS
        in ENCOUNTERS -> ENCOUNTER_SECONDS
        HO_OH_SUMMON -> HO_OH_SUMMON_SECONDS
        LUGIA -> LUGIA_SECONDS
        REGIGIGAS -> REGIGIGAS_SECONDS
        in REGIS -> REGI_SECONDS
        in EON -> EON_SECONDS
        else -> WRITTEN_SECONDS
    }

    /** The shortest and longest a scene may be stretched to (three quarters to five quarters; Mew 20 to 25 s; Ho-Oh never). */
    fun minSeconds(scene: String): Double = when (scene) {
        MEW -> 20.0
        HO_OH -> HO_OH_SECONDS
        JOHTO_HISTORY -> 40.0
        in AWAKENINGS -> 15.0
        in ENCOUNTERS -> 20.0
        HO_OH_SUMMON, LUGIA -> 25.0
        REGIGIGAS -> 20.0
        in REGIS -> 15.0
        in EON -> 20.0
        else -> writtenSeconds(scene) * 0.75
    }

    fun maxSeconds(scene: String): Double = when (scene) {
        MEW -> 25.0
        HO_OH -> HO_OH_SECONDS
        JOHTO_HISTORY -> 60.0
        in AWAKENINGS -> 25.0
        in ENCOUNTERS -> 25.0
        HO_OH_SUMMON, LUGIA -> 35.0
        REGIGIGAS -> 30.0
        in REGIS -> 25.0
        in EON -> 25.0
        else -> writtenSeconds(scene) * 1.25
    }

    /** Whether the players near it who cannot watch get its ambience (Mewtwo's, D11). */
    fun hasAmbience(scene: String): Boolean = scene == MEWTWO

    /**
     * How long before the scene's end the Pokémon appears: the birds 1 s; Mewtwo and Mew right at
     * the end, where the actor stands (no second one on screen for a second); Johto's encounters
     * and Ho-Oh's summoning too.
     */
    fun spawnLeadTicks(scene: String): Int = if (scene == MEWTWO || scene == MEW || scene in ENCOUNTERS || scene == HO_OH_SUMMON || scene == LUGIA || scene in REGIS || scene in EON) 2 else 20

    /** The Pokémon of each scene by default (cinematics.json can change it). */
    fun defaultSpecies(scene: String): String = when (scene) {
        HO_OH, JOHTO_HISTORY, HO_OH_SUMMON -> "cobblemon:hooh"
        RAIKOU_AWAKENING, RAIKOU_ENCOUNTER -> "cobblemon:raikou"
        ENTEI_AWAKENING, ENTEI_ENCOUNTER -> "cobblemon:entei"
        SUICUNE_AWAKENING, SUICUNE_ENCOUNTER -> "cobblemon:suicune"
        else -> "cobblemon:$scene"
    }
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
    /** Skipped by its invoker, who had seen it already (JOHTO-PLAN.md D17): what follows happens as usual. */
    SKIPPED,
}
