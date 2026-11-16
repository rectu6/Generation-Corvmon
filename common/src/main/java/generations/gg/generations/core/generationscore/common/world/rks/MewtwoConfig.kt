package generations.gg.generations.core.generationscore.common.world.rks

import generations.gg.generations.core.generationscore.common.config.JsonConfigs

/**
 * config/generations/core/rks/mewtwo.json: the M2 cloning protocol's own conditions
 * (RKS-PLAN.md § 4.2 and 5), on top of its recipe (recipe/recipes/rks/mewtwo.json, which holds
 * the genetic components: 6 Mew fossils, Mew DNA, an Orb, the DNA Splicers). Read at server
 * start; used from conversation 5 of the plan.
 *
 * - energyRequired: psychic energy to gather (the reserve never goes above it).
 * - points: what a sacrificed Psychic Pokémon gives by its place in its evolution line;
 *   speciesPoints overrides it by species ("cobblemon:wobbuffet": 2).
 * - excludedLabels / excludedSpecies: never accepted; allowedSpecies, when not empty, the only
 *   species accepted. allowPc: the PC's Pokémon may be given too (D12).
 * - research: the three research files, taken at every new Mewtwo.
 * - growthSeconds: Mewtwo's growth in the tank; growthClock "loaded" (only while its chunk is
 *   loaded, D10) or "world" (the world's time).
 * - spawn: the wild Mewtwo that appears (level, more Cobblemon properties such as "shiny=yes").
 * - cinematic: the awakening scene (otherwise Mewtwo appears at once).
 */
class MewtwoConfig(
    var energyRequired: Int = 100,
    var points: StagePoints = StagePoints(),
    var speciesPoints: MutableMap<String, Int> = LinkedHashMap(),
    var excludedLabels: MutableList<String> = mutableListOf("legendary", "mythical", "ultra_beast", "paradox"),
    var excludedSpecies: MutableList<String> = mutableListOf(),
    var allowedSpecies: MutableList<String> = mutableListOf(),
    var allowPc: Boolean = true,
    var research: MutableList<String> = defaultResearch(),
    var growthSeconds: Double = 300.0,
    var growthClock: String = CLOCK_LOADED,
    var spawn: Spawn = Spawn(),
    var cinematic: Boolean = true,
) {
    class StagePoints(
        /** The first of a line that evolves (Abra). */
        var firstStage: Int = 1,
        /** In the middle of a line of three (Kadabra). */
        var middleStage: Int = 2,
        /** The last of a line of two or three (Alakazam, Wobbuffet). */
        var finalStage: Int = 3,
        /** A Pokémon that does not evolve. */
        var noEvolution: Int = 2,
    ) {
        fun validate() {
            firstStage = firstStage.coerceIn(0, 100)
            middleStage = middleStage.coerceIn(0, 100)
            finalStage = finalStage.coerceIn(0, 100)
            noEvolution = noEvolution.coerceIn(0, 100)
        }
    }

    class Spawn(
        var level: Int = 70,
        /** More Cobblemon properties for the Mewtwo, "" for none. */
        var properties: String = "",
    ) {
        @Suppress("SENSELESS_COMPARISON")
        fun validate() {
            level = level.coerceIn(1, 100)
            if (properties == null) properties = ""
        }
    }

    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        energyRequired = energyRequired.coerceIn(1, 10_000)
        if (points == null) points = StagePoints()
        points.validate()
        if (speciesPoints == null) speciesPoints = LinkedHashMap()
        speciesPoints.replaceAll { _, value -> value.coerceIn(0, 10_000) }
        if (excludedLabels == null) excludedLabels = mutableListOf()
        if (excludedSpecies == null) excludedSpecies = mutableListOf()
        if (allowedSpecies == null) allowedSpecies = mutableListOf()
        if (research == null || research.isEmpty()) research = defaultResearch()
        growthSeconds = JsonConfigs.clamp(growthSeconds, 10.0, 24.0 * 3600.0, 300.0)
        if (growthClock != CLOCK_WORLD) growthClock = CLOCK_LOADED
        if (spawn == null) spawn = Spawn()
        spawn.validate()
    }

    companion object {
        const val VERSION = 1
        const val CLOCK_LOADED = "loaded"
        const val CLOCK_WORLD = "world"

        fun defaultResearch(): MutableList<String> = mutableListOf(
            "generations_core:rks_research_m01_genetics",
            "generations_core:rks_research_m02_stabilization",
            "generations_core:rks_research_m03_containment",
        )

        fun load(): MewtwoConfig = JsonConfigs.load(
            "RKS Mewtwo", JsonConfigs.file("rks", "mewtwo.json"), MewtwoConfig::class.java, ::MewtwoConfig, VERSION, { it.validate() },
        )
    }
}
