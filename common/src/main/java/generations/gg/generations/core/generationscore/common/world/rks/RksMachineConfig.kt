package generations.gg.generations.core.generationscore.common.world.rks

import generations.gg.generations.core.generationscore.common.config.JsonConfigs
import generations.gg.generations.core.generationscore.common.world.recipe.RksCategory

/**
 * config/generations/core/rks/rks_machine.json: the RKS Machine (RKS-PLAN.md § 2.3), read at
 * server start. The recipes themselves are data (the rks_protocol files of recipe/recipes/rks),
 * the fossils' cost included; the five modules' and the repair kit's recipes are crafting-table
 * recipes, also data.
 *
 * - categories: genetic, fossil, items; a category set to false makes nothing.
 * - The other values are for the multiblock machine (conversations 3 to 6 of the plan).
 */
class RksMachineConfig(
    var categories: MutableMap<String, Boolean> = defaultCategories(),
    /** The assembly animation, once the five modules are in place. */
    var assemblySeconds: Double = 3.0,
    /** "ANALYSER LES ÉCHANTILLONS" (Mewtwo). */
    var analysisSeconds: Double = 4.0,
    /** The repair kit's animation. */
    var repairSeconds: Double = 4.0,
    /** Modules cannot be broken, pushed or blown up while Mewtwo grows or awakens (D9). */
    var protectModulesDuringExperiment: Boolean = true,
    /** A module placed this close to an incomplete console goes to its place (D7). */
    var assistedPlacementRange: Int = 4,
    /** How long the missing modules' hologram stays. */
    var hologramSeconds: Int = 30,
) {
    @Suppress("SENSELESS_COMPARISON")
    fun validate() {
        if (categories == null) categories = defaultCategories()
        for (category in RksCategory.entries) categories.putIfAbsent(category.serializedName, true)
        assemblySeconds = JsonConfigs.clamp(assemblySeconds, 0.0, 30.0, 3.0)
        analysisSeconds = JsonConfigs.clamp(analysisSeconds, 0.0, 60.0, 4.0)
        repairSeconds = JsonConfigs.clamp(repairSeconds, 0.0, 60.0, 4.0)
        assistedPlacementRange = assistedPlacementRange.coerceIn(0, 16)
        hologramSeconds = hologramSeconds.coerceIn(0, 600)
    }

    fun enabled(category: RksCategory): Boolean = categories[category.serializedName] != false

    companion object {
        const val VERSION = 1

        fun defaultCategories(): MutableMap<String, Boolean> = RksCategory.entries.associateTo(LinkedHashMap()) { it.serializedName to true }

        fun load(): RksMachineConfig = JsonConfigs.load(
            "RKS Machine", JsonConfigs.file("rks", "rks_machine.json"), RksMachineConfig::class.java, ::RksMachineConfig, VERSION, { it.validate() },
        )
    }
}
