package generations.gg.generations.core.generationscore.common.world.ultraspace.suit

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import generations.gg.generations.core.generationscore.common.world.item.GenerationsArmor
import generations.gg.generations.core.generationscore.common.world.recipe.GenerationsCoreRecipeSerializers
import net.minecraft.core.HolderLookup
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.SmithingRecipe
import net.minecraft.world.item.crafting.SmithingRecipeInput
import net.minecraft.world.level.Level

/**
 * generations_core:ultra_shield_smithing: at the smithing table, a shield (template
 * slot) + a piece of the Ultra Suit (base slot) + the addition (a Z Ingot in
 * data/generations_core/recipe/ultra_shield_install.json) give back the same piece, its
 * enchantments, damage, name and trims kept, with the shield installed. Refused when the
 * piece already has it. The three items are used up (the smithing table's own rule).
 */
class UltraShieldSmithingRecipe(val template: Ingredient, val base: Ingredient, val addition: Ingredient) : SmithingRecipe {
    override fun matches(input: SmithingRecipeInput, level: Level): Boolean {
        val shield = (input.template().item as? UltraShieldItem)?.shield ?: return false
        return template.test(input.template()) && base.test(input.base()) && addition.test(input.addition()) &&
            UltraSuit.isPiece(input.base()) && shield !in UltraSuit.shields(input.base())
    }

    override fun assemble(input: SmithingRecipeInput, registries: HolderLookup.Provider): ItemStack {
        val shield = (input.template().item as? UltraShieldItem)?.shield ?: return ItemStack.EMPTY
        if (!UltraSuit.isPiece(input.base())) return ItemStack.EMPTY
        return UltraSuit.withShield(input.base(), shield)
    }

    /** What recipe lists show: a chestplate of the suit. */
    override fun getResultItem(registries: HolderLookup.Provider): ItemStack = ItemStack(GenerationsArmor.ULTRA.chestplate.value())

    override fun isTemplateIngredient(stack: ItemStack): Boolean = stack.item is UltraShieldItem && template.test(stack)

    override fun isBaseIngredient(stack: ItemStack): Boolean = UltraSuit.isPiece(stack) && base.test(stack)

    override fun isAdditionIngredient(stack: ItemStack): Boolean = addition.test(stack)

    override fun isIncomplete(): Boolean = template.isEmpty || base.isEmpty || addition.isEmpty

    override fun getSerializer(): RecipeSerializer<*> = GenerationsCoreRecipeSerializers.ULTRA_SHIELD_SMITHING.value()

    companion object {
        val CODEC: MapCodec<UltraShieldSmithingRecipe> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Ingredient.CODEC.fieldOf("template").forGetter(UltraShieldSmithingRecipe::template),
                Ingredient.CODEC.fieldOf("base").forGetter(UltraShieldSmithingRecipe::base),
                Ingredient.CODEC.fieldOf("addition").forGetter(UltraShieldSmithingRecipe::addition),
            ).apply(instance, ::UltraShieldSmithingRecipe)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, UltraShieldSmithingRecipe> = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, UltraShieldSmithingRecipe::template,
            Ingredient.CONTENTS_STREAM_CODEC, UltraShieldSmithingRecipe::base,
            Ingredient.CONTENTS_STREAM_CODEC, UltraShieldSmithingRecipe::addition,
            ::UltraShieldSmithingRecipe,
        )
    }
}
