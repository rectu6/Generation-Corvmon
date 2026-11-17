package generations.gg.generations.core.generationscore.common.compat.jei

import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.level.block.GenerationsUtilityBlocks
import generations.gg.generations.core.generationscore.common.world.recipe.RksCategory
import generations.gg.generations.core.generationscore.common.world.recipe.RksProtocolRecipe
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.AbstractRecipeCategory
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder

fun <T: Recipe<*>> Holder<net.minecraft.world.item.crafting.RecipeType<*>>.asValue(): net.minecraft.world.item.crafting.RecipeType<T> = this.value() as net.minecraft.world.item.crafting.RecipeType<T>

/**
 * One of the RKS Machine's three families in JEI (RKS-PLAN.md § 2.4): the counted ingredients
 * (up to 9 kinds, 3 × 3), the result, the time; the Mewtwo protocol adds its own conditions.
 */
class RksProtocolCategory(private val helper: IGuiHelper, val category: RksCategory) :
    AbstractRecipeCategory<RecipeHolder<RksProtocolRecipe>>(
        type(category),
        Component.translatable(category.translationKey),
        helper.createDrawableItemLike(GenerationsUtilityBlocks.RKS_CONTROL_CONSOLE.value()),
        WIDTH,
        HEIGHT,
    ) {

    override fun setRecipe(builder: IRecipeLayoutBuilder, holder: RecipeHolder<RksProtocolRecipe>, focuses: IFocusGroup) {
        val recipe = holder.value()
        recipe.inputs.take(GRID).forEachIndexed { i, input ->
            val stacks = input.ingredient.matchingStacks().map { it.copyWithCount(input.count) }
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % 3) * 18, 1 + (i / 3) * 18)
                .setStandardSlotBackground()
                .addItemStacks(stacks)
        }
        val result = recipe.result.stack
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, 19)
            .setOutputSlotBackground()
            .addItemStack(if (result.isEmpty) ItemStack.EMPTY else result.copy())
        // The M2 protocol's research files (used up by each Mewtwo).
        if (recipe.isMewtwo) {
            val files = listOf(
                generations.gg.generations.core.generationscore.common.world.item.GenerationsItems.RKS_RESEARCH_M01_GENETICS,
                generations.gg.generations.core.generationscore.common.world.item.GenerationsItems.RKS_RESEARCH_M02_STABILIZATION,
                generations.gg.generations.core.generationscore.common.world.item.GenerationsItems.RKS_RESEARCH_M03_CONTAINMENT,
            )
            files.forEachIndexed { i, item ->
                builder.addSlot(RecipeIngredientRole.INPUT, 84 + i * 18, 39).setStandardSlotBackground().addItemStack(ItemStack(item.value()))
            }
        }
    }

    override fun draw(holder: RecipeHolder<RksProtocolRecipe>, view: IRecipeSlotsView, graphics: GuiGraphics, mouseX: Double, mouseY: Double) {
        val recipe = holder.value()
        helper.recipeArrow.draw(graphics, 62, 19)
        val font = Minecraft.getInstance().font
        val seconds = recipe.processingTime / 20
        val time = Component.translatable("generations_core.rks.jei.time", seconds)
        graphics.drawString(font, time, 0, 58, 0xFF606060.toInt(), false)
        if (recipe.isMewtwo) {
            for ((line, text) in font.split(Component.translatable("generations_core.rks.jei.mewtwo"), WIDTH).withIndex()) {
                graphics.drawString(font, text, 0, 69 + line * 10, 0xFF7A3FB0.toInt(), false)
            }
        }
        recipe.energyKind?.let { kind ->
            for ((line, text) in font.split(Component.translatable(kind.key("generations_core.rks.jei.energy"), kind.required), WIDTH).withIndex()) {
                graphics.drawString(font, text, 0, 69 + line * 10, 0xFFB5470F.toInt(), false)
            }
        }
    }

    companion object {
        const val WIDTH = 150
        const val HEIGHT = 90
        private const val GRID = 9
        private const val OUTPUT_X = 102

        private val TYPES = RksCategory.entries.associateWith {
            RecipeType.createRecipeHolderType<RksProtocolRecipe>(GenerationsCore.id("rks_" + it.serializedName))
        }

        fun type(category: RksCategory): RecipeType<RecipeHolder<RksProtocolRecipe>> = TYPES.getValue(category)
    }
}
