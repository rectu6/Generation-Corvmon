package generations.gg.generations.core.generationscore.common.world.rks

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.world.recipe.RksProtocolRecipe
import generations.gg.generations.core.generationscore.common.world.recipe.RksRecipe

/** The RKS Machine's settings (config/generations/core/rks/), read at every server start. */
object RksSettings {
    var machine: RksMachineConfig = RksMachineConfig()
        private set
    var mewtwo: MewtwoConfig = MewtwoConfig()
        private set

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { reload() }
    }

    fun reload() {
        machine = RksMachineConfig.load()
        mewtwo = MewtwoConfig.load()
    }

    /** Whether the machine may make this recipe: its category is on (older recipe formats always are). */
    fun allows(recipe: RksRecipe): Boolean = recipe !is RksProtocolRecipe || machine.enabled(recipe.category)
}
