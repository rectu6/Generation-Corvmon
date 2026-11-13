package generations.gg.generations.core.generationscore.common.compat.jei

import generations.gg.generations.core.generationscore.common.world.item.GenerationsArmor
import generations.gg.generations.core.generationscore.common.world.ultraspace.suit.UltraShield
import generations.gg.generations.core.generationscore.common.world.ultraspace.suit.UltraShieldItem
import generations.gg.generations.core.generationscore.common.world.ultraspace.suit.UltraShieldSmithingRecipe
import generations.gg.generations.core.generationscore.common.world.ultraspace.suit.UltraSuit
import mezz.jei.api.gui.builder.IIngredientAcceptor
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.category.extensions.vanilla.smithing.ISmithingCategoryExtension
import net.minecraft.world.item.ItemStack

/** JEI's smithing table shows the installation of the Ultra Suit's shields (phase 4.5), the result following the shield and the piece shown. */
object UltraShieldSmithingExtension : ISmithingCategoryExtension<UltraShieldSmithingRecipe> {
    override fun <T : IIngredientAcceptor<T>> setTemplate(recipe: UltraShieldSmithingRecipe, ingredients: T) {
        ingredients.addIngredients(recipe.template)
    }

    override fun <T : IIngredientAcceptor<T>> setBase(recipe: UltraShieldSmithingRecipe, ingredients: T) {
        ingredients.addIngredients(recipe.base)
    }

    override fun <T : IIngredientAcceptor<T>> setAddition(recipe: UltraShieldSmithingRecipe, ingredients: T) {
        ingredients.addIngredients(recipe.addition)
    }

    override fun <T : IIngredientAcceptor<T>> setOutput(recipe: UltraShieldSmithingRecipe, ingredients: T) {
        ingredients.addItemStack(UltraSuit.withShield(ItemStack(GenerationsArmor.ULTRA.chestplate.value()), UltraShield.THERMAL))
    }

    override fun onDisplayedIngredientsUpdate(
        recipe: UltraShieldSmithingRecipe,
        templateSlot: IRecipeSlotDrawable,
        baseSlot: IRecipeSlotDrawable,
        additionSlot: IRecipeSlotDrawable,
        outputSlot: IRecipeSlotDrawable,
        focuses: IFocusGroup,
    ) {
        val shield = (templateSlot.displayedItemStack.orElse(ItemStack.EMPTY).item as? UltraShieldItem)?.shield ?: return
        val base = baseSlot.displayedItemStack.orElse(ItemStack.EMPTY).takeIf { UltraSuit.isPiece(it) } ?: return
        outputSlot.createDisplayOverrides().addItemStack(UltraSuit.withShield(base, shield))
    }
}
