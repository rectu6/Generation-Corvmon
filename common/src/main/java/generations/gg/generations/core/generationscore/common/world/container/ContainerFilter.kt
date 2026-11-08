package generations.gg.generations.core.generationscore.common.world.container

import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import net.minecraft.world.item.ItemStack

/** Items a GenericChestContainer accepts; sent to the client so its slots agree with the server's. */
enum class ContainerFilter(val id: String, private val accepts: (ItemStack) -> Boolean) {
    APRICORNS("apricorns", { it.`is`(CobblemonItemTags.APRICORNS) });

    fun test(stack: ItemStack): Boolean = accepts(stack)

    companion object {
        fun byId(id: String): ContainerFilter? = entries.firstOrNull { it.id == id }
    }
}
