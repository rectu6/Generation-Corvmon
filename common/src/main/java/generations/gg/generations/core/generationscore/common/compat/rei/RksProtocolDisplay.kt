package generations.gg.generations.core.generationscore.common.compat.rei

import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.level.block.GenerationsUtilityBlocks
import generations.gg.generations.core.generationscore.common.world.recipe.RksCategory
import generations.gg.generations.core.generationscore.common.world.recipe.RksProtocolRecipe
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.gui.Renderer
import me.shedaniel.rei.api.client.gui.widgets.Widget
import me.shedaniel.rei.api.client.gui.widgets.Widgets
import me.shedaniel.rei.api.client.registry.display.DisplayCategory
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.basic.BasicDisplay
import me.shedaniel.rei.api.common.util.EntryIngredients
import me.shedaniel.rei.api.common.util.EntryStacks
import net.minecraft.network.chat.Component
import net.minecraft.world.item.crafting.RecipeHolder
import java.util.Optional

/** An RKS protocol in REI (RKS-PLAN.md § 2.4): counted ingredients, the result, in its family. */
class RksProtocolDisplay(val holder: RecipeHolder<RksProtocolRecipe>) : BasicDisplay(
    holder.value().inputs.map { input -> EntryIngredients.ofItemStacks(input.ingredient.matchingStacks().map { it.copyWithCount(input.count) }) },
    listOf(EntryIngredients.of(holder.value().result.stack.copy())),
    Optional.of(holder.id()),
) {
    val recipe: RksProtocolRecipe get() = holder.value()

    override fun getCategoryIdentifier(): CategoryIdentifier<*> = RksProtocolReiCategory.id(recipe.category)

    companion object {
        fun of(holder: RecipeHolder<RksProtocolRecipe>): RksProtocolDisplay = RksProtocolDisplay(holder)
    }
}

/** One of the RKS Machine's three families in REI. */
class RksProtocolReiCategory(val category: RksCategory) : DisplayCategory<RksProtocolDisplay> {
    override fun getCategoryIdentifier(): CategoryIdentifier<out RksProtocolDisplay> = id(category)

    override fun getTitle(): Component = Component.translatable(category.translationKey)

    override fun getIcon(): Renderer = EntryStacks.of(GenerationsUtilityBlocks.RKS_CONTROL_CONSOLE.value())

    override fun getDisplayHeight(): Int = 96

    override fun setupDisplay(display: RksProtocolDisplay, bounds: Rectangle): List<Widget> {
        val start = Point(bounds.x + 8, bounds.y + 6)
        val widgets = ArrayList<Widget>()
        widgets += Widgets.createRecipeBase(bounds)
        display.inputEntries.take(9).forEachIndexed { i, entries ->
            widgets += Widgets.createSlot(Point(start.x + 1 + (i % 3) * 18, start.y + 1 + (i / 3) * 18)).entries(entries).markInput()
        }
        widgets += Widgets.createArrow(Point(start.x + 62, start.y + 18)).animationDurationTicks(display.recipe.processingTime.toDouble())
        widgets += Widgets.createResultSlotBackground(Point(start.x + 98, start.y + 19))
        widgets += Widgets.createSlot(Point(start.x + 98, start.y + 19)).entries(display.outputEntries[0]).disableBackground().markOutput()
        widgets += Widgets.createLabel(Point(start.x, start.y + 58), Component.translatable("generations_core.rks.jei.time", display.recipe.processingTime / 20))
            .leftAligned().noShadow().color(0xFF606060.toInt(), 0xFFBBBBBB.toInt())
        if (display.recipe.isMewtwo) {
            widgets += Widgets.createLabel(Point(start.x, start.y + 70), Component.translatable("generations_core.rks.jei.mewtwo.short"))
                .leftAligned().noShadow().color(0xFF7A3FB0.toInt(), 0xFFC9A0F0.toInt())
        }
        display.recipe.energyKind?.let { kind ->
            widgets += Widgets.createLabel(Point(start.x, start.y + 70), Component.translatable(kind.key("generations_core.rks.jei.energy.short"), kind.required))
                .leftAligned().noShadow().color(0xFFB5470F.toInt(), 0xFFFFB070.toInt())
        }
        return widgets
    }

    companion object {
        private val IDS = RksCategory.entries.associateWith {
            CategoryIdentifier.of<RksProtocolDisplay>(GenerationsCore.MOD_ID, "rks_" + it.serializedName)
        }

        fun id(category: RksCategory): CategoryIdentifier<RksProtocolDisplay> = IDS.getValue(category)
    }
}
