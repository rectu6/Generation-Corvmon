package generations.gg.generations.core.generationscore.common.world.recipe

import com.mojang.serialization.Codec
import net.minecraft.util.StringRepresentable

/** The three families of the RKS Machine's menu (RKS-PLAN.md § 2.2), in the menu's order. */
enum class RksCategory(private val id: String) : StringRepresentable {
    GENETIC("genetic"),
    FOSSIL("fossil"),
    ITEMS("items");

    override fun getSerializedName(): String = id

    val translationKey: String get() = "generations_core.rks.category.$id"

    companion object {
        val CODEC: Codec<RksCategory> = StringRepresentable.fromEnum { entries.toTypedArray() }

        fun byId(id: String): RksCategory? = entries.firstOrNull { it.id == id }
    }
}
