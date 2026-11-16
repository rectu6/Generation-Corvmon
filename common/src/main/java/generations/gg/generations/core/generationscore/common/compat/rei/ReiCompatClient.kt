package generations.gg.generations.core.generationscore.common.compat.rei

import generations.gg.generations.core.generationscore.common.client.screen.container.RksMachineScreen
import generations.gg.generations.core.generationscore.common.compat.jei.asValue
import generations.gg.generations.core.generationscore.common.world.level.block.GenerationsUtilityBlocks
import generations.gg.generations.core.generationscore.common.world.recipe.GenerationsCoreRecipeTypes
import generations.gg.generations.core.generationscore.common.world.recipe.RksCategory
import generations.gg.generations.core.generationscore.common.world.recipe.RksProtocolRecipe
import generations.gg.generations.core.generationscore.common.world.recipe.RksRecipe
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.plugins.REIClientPlugin
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry
import me.shedaniel.rei.api.client.search.method.InputMethodRegistry
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessorRegistry
import me.shedaniel.rei.api.common.util.EntryStacks
import net.minecraft.world.item.crafting.RecipeHolder

open class ReiCompatClient : REIClientPlugin {
    /** The RKS Machine's three families (RKS-PLAN.md § 2.4). */
    override fun registerCategories(registry: CategoryRegistry) {
        for (category in RksCategory.entries) {
            registry.add(RksProtocolReiCategory(category)) { configuration: CategoryRegistry.CategoryConfiguration<RksProtocolDisplay> ->
                configuration.addWorkstations(EntryStacks.of(GenerationsUtilityBlocks.RKS_CONTROL_CONSOLE.value()))
            }
        }
    }

    override fun registerDisplays(registry: DisplayRegistry) {
        registry.registerRecipeFiller(
            RksProtocolRecipe::class.java, GenerationsCoreRecipeTypes.RKS.asValue<RksRecipe>()
        ) { recipe: RecipeHolder<RksProtocolRecipe> -> RksProtocolDisplay.of(recipe) }
    }

    override fun registerScreens(registry: ScreenRegistry) {
        registry.registerContainerClickArea(
            { _: RksMachineScreen? -> Rectangle(90, 35, 22, 15) },
            RksMachineScreen::class.java, *RksCategory.entries.map(RksProtocolReiCategory::id).toTypedArray()
        )
    }
}
