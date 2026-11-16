package generations.gg.generations.core.generationscore.common.world.charm

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.tags.GenerationsItemTags
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.WeakHashMap

/**
 * The charm case (COMBO-FUITE-PERF-PLAN.md, 2.6): one slot per charm (8, in the order of
 * Charm) and one per battle item (a Mega item, any key stone; Dynamax Band; Tera Orb;
 * Z-Ring), one item each, each slot showing its item's silhouette when empty, like the
 * armor slots. It lives in the player's data like the ender chest (PlayerMixin, key
 * generations_core:charm_case), is copied to the new player on respawn (ServerPlayerMixin)
 * and kept on death unless charms.json says otherwise. Opened from the inventory's button
 * (C2SOpenCharmCasePacket), as a vanilla-styled menu.
 */
object CharmCase {
    const val SAVE_KEY = "generations_core:charm_case"

    /** One slot: what it takes, its silhouette (textures/item/charm_case/empty_<name>) and its empty slot's tooltip. */
    class Place(val name: String, val hint: String, private val test: (ItemStack) -> Boolean) {
        val silhouette: ResourceLocation = GenerationsCore.id("item/charm_case/empty_$name")

        fun accepts(stack: ItemStack): Boolean = !stack.isEmpty && test(stack)
    }

    private fun battle(name: String, tag: TagKey<Item>) = Place(name, "generations_core.charm_case.slot.$name") { it.`is`(tag) }

    @JvmField
    val CHARMS: List<Place> = Charm.entries.map { charm -> Place(charm.id, "item.generations_core.${charm.id}") { it.`is`(Charms.item(charm)) } }

    @JvmField
    val BATTLE_ITEMS: List<Place> = listOf(
        battle("mega", GenerationsItemTags.KEY_STONES),
        battle("dynamax", GenerationsItemTags.DYNAMAX_BANDS),
        battle("tera", GenerationsItemTags.TERA_ORBS),
        battle("z_ring", GenerationsItemTags.Z_RINGS),
    )

    @JvmField
    val PLACES: List<Place> = CHARMS + BATTLE_ITEMS

    @JvmField
    val SIZE: Int = PLACES.size

    /** Items of an old save that found no slot (two Mega items...), given back at login when the inventory was full. */
    private val pending = WeakHashMap<Player, MutableList<ItemStack>>()

    fun of(player: Player): SimpleContainer = (player as CharmCaseHolder).`generations$getCharmCase`()

    fun init() {
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe(Priority.NORMAL) { event ->
            pending.remove(event.player)?.forEach { event.player.inventory.placeItemBackInInventory(it) }
        }
    }

    /** Whether this stack may go in this slot of the case. */
    fun accepts(slot: Int, stack: ItemStack): Boolean = PLACES.getOrNull(slot)?.accepts(stack) == true

    /** An item of this tag in the case (the key items' check of Mega Evolution, Dynamax...). */
    @JvmStatic
    fun contains(player: Player, tag: TagKey<Item>): Boolean = of(player).hasAnyMatching { it.`is`(tag) }

    fun contains(player: Player, item: Item): Boolean = of(player).hasAnyMatching { it.`is`(item) }

    /** Whether the case holds this item in another slot than skip (no doubles: they would not add up). */
    fun hasElsewhere(container: Container, item: Item, skip: Int): Boolean {
        for (i in 0 until container.containerSize) if (i != skip && container.getItem(i).`is`(item)) return true
        return false
    }

    @JvmStatic
    fun save(container: SimpleContainer, tag: CompoundTag, registries: HolderLookup.Provider) {
        val list = ListTag()
        for (slot in 0 until container.containerSize) {
            val stack = container.getItem(slot)
            if (stack.isEmpty) continue
            val item = CompoundTag()
            item.putByte("Slot", slot.toByte())
            list.add(stack.save(registries, item))
        }
        tag.put(SAVE_KEY, list)
    }

    /**
     * Each saved item goes back to its saved slot when that slot still takes it, else to the
     * first free slot that takes it (saves of another layout, like the first build 54 with two
     * rows of 9). What finds no slot goes to the inventory (loaded before), or at login.
     */
    @JvmStatic
    fun load(player: Player, tag: CompoundTag, registries: HolderLookup.Provider) {
        if (!tag.contains(SAVE_KEY, Tag.TAG_LIST.toInt())) return
        val container = of(player)
        container.clearContent()
        val list = tag.getList(SAVE_KEY, Tag.TAG_COMPOUND.toInt())
        val stacks = (0 until list.size).mapNotNull { i ->
            val item = list.getCompound(i)
            ItemStack.parse(registries, item).orElse(null)?.let { (item.getByte("Slot").toInt() and 0xFF) to it }
        }
        val homeless = mutableListOf<ItemStack>()
        val placed = BooleanArray(stacks.size)
        // Saved slots first, so that an item moved by the new layout cannot take another one's place.
        stacks.forEachIndexed { i, (slot, stack) ->
            if (accepts(slot, stack) && container.getItem(slot).isEmpty) {
                container.setItem(slot, stack.split(1))
                placed[i] = true
                if (!stack.isEmpty) homeless += stack
            }
        }
        stacks.forEachIndexed { i, (_, stack) ->
            if (placed[i]) return@forEachIndexed
            val slot = PLACES.indices.firstOrNull { accepts(it, stack) && container.getItem(it).isEmpty && !hasElsewhere(container, stack.item, it) }
            if (slot != null) container.setItem(slot, stack.split(1))
            if (!stack.isEmpty) homeless += stack
        }
        for (stack in homeless) {
            player.inventory.add(stack)
            if (!stack.isEmpty) pending.getOrPut(player) { mutableListOf() } += stack
        }
    }

    /** The case's content goes to the new player (respawn, return from the End). */
    @JvmStatic
    fun copy(from: Player, to: Player) {
        val source = of(from)
        val target = of(to)
        for (slot in 0 until SIZE) target.setItem(slot, source.getItem(slot).copy())
    }

    /** On death, when the case is not kept (charms.json keepOnDeath false and no keepInventory). */
    @JvmStatic
    fun dropOnDeath(player: Player) {
        if (Charms.config.keepOnDeath) return
        val container = of(player)
        for (slot in 0 until container.containerSize) {
            val stack = container.removeItemNoUpdate(slot)
            if (!stack.isEmpty) player.drop(stack, true, false)
        }
    }

    /** Opens the case (from the inventory's button). An item held by the cursor goes back first. */
    fun open(player: ServerPlayer) {
        if (player.isSpectator || !player.isAlive) return
        val carried = player.inventoryMenu.carried
        if (!carried.isEmpty) {
            player.inventoryMenu.setCarried(ItemStack.EMPTY)
            player.inventory.placeItemBackInInventory(carried)
        }
        player.openMenu(SimpleMenuProvider({ id, inventory, owner -> CharmCaseMenu(id, inventory, of(owner)) }, Component.translatable("container.generations_core.charm_case")))
    }
}
