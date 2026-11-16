package generations.gg.generations.core.generationscore.common.compat.jei

import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.asValue
import generations.gg.generations.core.generationscore.common.client.screen.container.RksMachineScreen
import generations.gg.generations.core.generationscore.common.world.level.block.GenerationsUtilityBlocks
import generations.gg.generations.core.generationscore.common.world.recipe.GenerationsCoreRecipeTypes
import generations.gg.generations.core.generationscore.common.world.recipe.RksCategory
import generations.gg.generations.core.generationscore.common.world.recipe.RksProtocolRecipe
import generations.gg.generations.core.generationscore.common.world.recipe.RksRecipe
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.registration.*
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.RecipeHolder

@JeiPlugin
class GenerationsCoreJeiCompat : IModPlugin {
    companion object {
        private val id: ResourceLocation = GenerationsCore.id("rks")
    }

    override fun getPluginUid(): ResourceLocation {
        return id
    }

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        // The RKS Machine's three families (RKS-PLAN.md § 2.4).
        for (category in RksCategory.entries) registration.addRecipeCategories(RksProtocolCategory(registration.jeiHelpers.guiHelper, category))
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        for ((category, recipes) in rksRecipes()) registration.addRecipes(RksProtocolCategory.type(category), recipes)

        // The Ultra Suit's shields: what they protect against and how they are installed.
        registration.addItemStackInfo(
            generations.gg.generations.core.generationscore.common.world.ultraspace.suit.UltraShield.entries.map {
                net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(generations.gg.generations.core.generationscore.common.GenerationsCore.id(it.itemName)))
            },
            net.minecraft.network.chat.Component.translatable("generations_core.ultra_shield.jei"),
        )
    }

    override fun registerVanillaCategoryExtensions(registration: IVanillaCategoryExtensionRegistration) {
        registration.smithingCategory.addExtension(
            generations.gg.generations.core.generationscore.common.world.ultraspace.suit.UltraShieldSmithingRecipe::class.java,
            UltraShieldSmithingExtension,
        )
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        for (category in RksCategory.entries) registration.addRecipeCatalyst(GenerationsUtilityBlocks.RKS_CONTROL_CONSOLE.value(), RksProtocolCategory.type(category))
    }

    override fun registerGuiHandlers(registration: IGuiHandlerRegistration) {
        registration.addRecipeClickArea(RksMachineScreen::class.java, 90, 35, 22, 15, *RksCategory.entries.map(RksProtocolCategory::type).toTypedArray())
    }

    /** The rks_protocol recipes by family, in the menu's order (older RKS formats are not shown). */
    private fun rksRecipes(): Map<RksCategory, List<RecipeHolder<RksProtocolRecipe>>> {
        val recipeManager = Minecraft.getInstance().level?.recipeManager ?: return emptyMap()
        return recipeManager.getAllRecipesFor(GenerationsCoreRecipeTypes.RKS.asValue<RksRecipe>())
            .filter { it.value() is RksProtocolRecipe }
            .map { @Suppress("UNCHECKED_CAST") (it as RecipeHolder<RksProtocolRecipe>) }
            .sortedWith(compareBy({ it.value().order }, { it.id().toString() }))
            .groupBy { it.value().category }
    }
}
