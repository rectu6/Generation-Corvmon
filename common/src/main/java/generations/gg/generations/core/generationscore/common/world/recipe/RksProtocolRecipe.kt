package generations.gg.generations.core.generationscore.common.world.recipe

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.config.SpeciesKey
import generations.gg.generations.core.generationscore.common.recipe.GenerationsIngredidents
import generations.gg.generations.core.generationscore.common.recipe.RksInput
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems
import net.minecraft.core.NonNullList
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.level.Level
import java.util.Optional

/** An ingredient of an RKS protocol and how many of it the protocol takes. */
data class CountedIngredient(val ingredient: GenerationsIngredient, val count: Int) {
    companion object {
        const val MAX_COUNT = 4096

        val CODEC: Codec<CountedIngredient> = RecordCodecBuilder.create { builder ->
            builder.group(
                GenerationsIngredidents.CODEC.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
                Codec.intRange(1, MAX_COUNT).optionalFieldOf("count", 1).forGetter(CountedIngredient::count),
            ).apply(builder, ::CountedIngredient)
        }
    }
}

/**
 * generations_core:rks_protocol (RKS-PLAN.md § 2.2): what the RKS Machine makes, read from the
 * data (reloaded by /reload, sent to the clients with the other recipes). Ingredients are
 * counted and may lie in any of the machine's slots, in any order; extra items do not matter.
 *
 * - category: the menu's family (genetic, fossil, items); order sorts the recipes in it;
 *   group gathers variants under one entry (the 28 Unown blocks: "unown_blocks").
 * - description: a translation key for the menu.
 * - protocol: "standard", or "mewtwo" for the cloning experiment and its own conditions
 *   (rks/mewtwo.json).
 */
class RksProtocolRecipe(
    val category: RksCategory,
    val order: Int,
    group: String,
    val description: String,
    val protocol: String,
    val inputs: List<CountedIngredient>,
    result: RksResult<*>,
    processingTime: Int,
    consumesTimeCapsules: Boolean,
    key: Optional<SpeciesKey>,
    experience: Float,
) : RksRecipe(group, result, consumesTimeCapsules, key, experience, processingTime, false) {

    val isMewtwo: Boolean get() = protocol == MEWTWO

    override fun matches(input: RksInput, level: Level): Boolean = allocate(input.items()) != null

    override fun getRksIngredients(): NonNullList<GenerationsIngredient> =
        NonNullList.createWithCapacity<GenerationsIngredient>(inputs.size).also { list -> inputs.forEach { list.add(it.ingredient) } }

    override fun canCraftInDimensions(width: Int, height: Int): Boolean = true

    override fun getSerializer(): RecipeSerializer<*> = GenerationsCoreRecipeSerializers.RKS_PROTOCOL.value()

    /** How many items of the stacks match each ingredient (each counted for every ingredient it matches). */
    fun present(stacks: List<ItemStack>): IntArray = IntArray(inputs.size) { i ->
        val ingredient = inputs[i].ingredient
        stacks.sumOf { if (!it.isEmpty && ingredient.matches(it)) it.count else 0 }
    }

    /**
     * Which stacks give how many items to each ingredient, an item going to one ingredient only:
     * each ingredient in turn takes from the first stacks that match. Null when one falls short.
     */
    fun allocate(stacks: List<ItemStack>): List<IntArray>? {
        val left = IntArray(stacks.size) { stacks[it].count }
        val plan = ArrayList<IntArray>(inputs.size)
        for (input in inputs) {
            val take = IntArray(stacks.size)
            var needed = input.count
            for (i in stacks.indices) {
                if (needed == 0) break
                val stack = stacks[i]
                if (left[i] <= 0 || stack.isEmpty || !input.ingredient.matches(stack)) continue
                val n = minOf(left[i], needed)
                take[i] = n
                left[i] -= n
                needed -= n
            }
            if (needed > 0) return null
            plan += take
        }
        return plan
    }

    /**
     * Takes the ingredients from the slots, all or nothing. A Time Capsule is shown to onCapsule
     * (its Pokémon may be the result's) and kept when the recipe does not consume capsules; what
     * an item leaves behind (a bucket) goes back in its slot, or is returned when the slot is
     * still in use. Null, and nothing taken, when the ingredients are not all there.
     */
    fun consume(slots: MutableList<ItemStack>, onCapsule: (ItemStack) -> Unit = {}): List<ItemStack>? {
        val plan = allocate(slots) ?: return null
        val leftovers = ArrayList<ItemStack>()
        for (take in plan) for (i in slots.indices) {
            val n = take[i]
            if (n == 0) continue
            val stack = slots[i]
            if (stack.`is`(GenerationsItems.TIME_CAPSULE)) {
                onCapsule(stack.copy())
                if (!consumesTimeCapsules) continue
            }
            val remainder = stack.item.craftingRemainingItem?.let { ItemStack(it, n) } ?: ItemStack.EMPTY
            stack.shrink(n)
            if (!remainder.isEmpty) {
                if (stack.isEmpty) slots[i] = remainder else leftovers += remainder
            }
        }
        return leftovers
    }

    companion object {
        const val STANDARD = "standard"
        const val MEWTWO = "mewtwo"
        const val DEFAULT_TICKS = 240

        val CODEC: MapCodec<RksProtocolRecipe> = RecordCodecBuilder.mapCodec { builder ->
            builder.group(
                RksCategory.CODEC.fieldOf("category").forGetter(RksProtocolRecipe::category),
                Codec.INT.optionalFieldOf("order", 0).forGetter(RksProtocolRecipe::order),
                Codec.STRING.optionalFieldOf("group", "").forGetter { it.group },
                Codec.STRING.optionalFieldOf("description", "").forGetter(RksProtocolRecipe::description),
                Codec.STRING.optionalFieldOf("protocol", STANDARD).forGetter(RksProtocolRecipe::protocol),
                CountedIngredient.CODEC.listOf().fieldOf("ingredients").forGetter(RksProtocolRecipe::inputs),
                GenerationsCore.RKS_RESULT_TYPE.codec.fieldOf("result").forGetter { it.result },
                Codec.intRange(1, 20 * 60 * 60).optionalFieldOf("processingTime", DEFAULT_TICKS).forGetter { it.processingTime },
                Codec.BOOL.optionalFieldOf("consumesTimeCapsules", true).forGetter { it.consumesTimeCapsules },
                SpeciesKey.CODEC.optionalFieldOf("key").forGetter { it.key },
                Codec.FLOAT.optionalFieldOf("experience", 1f).forGetter { it.experience },
            ).apply(builder, ::RksProtocolRecipe)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RksProtocolRecipe> = StreamCodec.of(::toNetwork, ::fromNetwork)

        private fun toNetwork(buffer: RegistryFriendlyByteBuf, value: RksProtocolRecipe) {
            buffer.writeVarInt(value.category.ordinal)
            buffer.writeVarInt(value.order)
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.group)
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.description)
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.protocol)
            buffer.writeVarInt(value.inputs.size)
            for (input in value.inputs) {
                GenerationsIngredidents.STREAM_CODEC.encode(buffer, input.ingredient)
                buffer.writeVarInt(input.count)
            }
            GenerationsCore.RKS_RESULT_TYPE.streamCodec.encode(buffer, value.result)
            buffer.writeVarInt(value.processingTime)
            buffer.writeBoolean(value.consumesTimeCapsules)
            SpeciesKey.OPTIONAL_STREAM_CODEC.encode(buffer, value.key)
            buffer.writeFloat(value.experience)
        }

        private fun fromNetwork(buffer: RegistryFriendlyByteBuf): RksProtocolRecipe {
            val category = RksCategory.entries[buffer.readVarInt()]
            val order = buffer.readVarInt()
            val group = ByteBufCodecs.STRING_UTF8.decode(buffer)
            val description = ByteBufCodecs.STRING_UTF8.decode(buffer)
            val protocol = ByteBufCodecs.STRING_UTF8.decode(buffer)
            val inputs = List(buffer.readVarInt()) {
                val ingredient = GenerationsIngredidents.STREAM_CODEC.decode(buffer)
                CountedIngredient(ingredient, buffer.readVarInt())
            }
            val result = GenerationsCore.RKS_RESULT_TYPE.streamCodec.decode(buffer)
            val ticks = buffer.readVarInt()
            val capsules = buffer.readBoolean()
            val key = SpeciesKey.OPTIONAL_STREAM_CODEC.decode(buffer)
            val experience = buffer.readFloat()
            return RksProtocolRecipe(category, order, group, description, protocol, inputs, result, ticks, capsules, key, experience)
        }
    }
}
