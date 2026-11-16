package generations.gg.generations.core.generationscore.common.world.boss

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.mojang.serialization.JsonOps
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Boss rewards, config/generations/core/bosses/rewards.json, edited in game with
 * /boss rewards (BossRewardsEditor) or by hand. Six tables: one per rarity, and
 * "titan", added on top for a Titan boss. Each entry is drawn on its own: its chance
 * in % (100 = always), then a quantity between min and max. Items keep their
 * components (enchantments, names…): an item is written as its id, or as an object
 * (Minecraft's item format) when it has components.
 *
 * megaStones: a Legendary boss is a Mega form; beating one gives its Mega Stone with
 * firstVictoryChance (100 %) when the player never got that stone from a boss, else
 * with repeatChance (10 %). Only the stone of the form beaten (Mega Charizard Y gives
 * Charizardite Y, never X). Kept per player in BossMegaSavedData.
 *
 * Read at server start (the items need the registries) and by /boss reload.
 */
object BossRewards {
    const val TITAN = "titan"
    /** The tables in the editor's order. */
    val TABLES: List<String> = BossRarity.entries.map { it.id } + TITAN

    class Entry(
        /** "minecraft:diamond", or an item object with components. */
        var item: JsonElement? = null,
        var min: Int = 1,
        var max: Int = 1,
        /** Chance in %, 0 to 100 (100 = always). */
        var chance: Double = 100.0,
    ) {
        @Transient var stack: ItemStack = ItemStack.EMPTY
    }

    class MegaStones(
        var enabled: Boolean = true,
        /** Chance in % to get the stone of a Mega the player never got it from. */
        var firstVictoryChance: Double = 100.0,
        /** Chance in % on the next victories over the same Mega form. */
        var repeatChance: Double = 10.0,
        /** Stone of each Mega form ("" = none, as Rayquaza, which needs Dragon Ascent). */
        var stones: MutableMap<String, String> = LinkedHashMap(BossMegas.DEFAULT_STONES),
    ) {
        @Suppress("SENSELESS_COMPARISON")
        fun validate() {
            if (!firstVictoryChance.isFinite()) firstVictoryChance = 100.0
            if (!repeatChance.isFinite()) repeatChance = 10.0
            firstVictoryChance = firstVictoryChance.coerceIn(0.0, 100.0)
            repeatChance = repeatChance.coerceIn(0.0, 100.0)
            if (stones == null) stones = LinkedHashMap(BossMegas.DEFAULT_STONES)
            stones = stones.entries.filter { it.key != null }.associateTo(LinkedHashMap()) { it.key.trim().lowercase() to (it.value ?: "").trim() }
        }
    }

    class Settings(
        var version: Int = 1,
        var tables: MutableMap<String, MutableList<Entry>> = defaultTables(),
        var megaStones: MegaStones = MegaStones(),
    )

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "bosses", "rewards.json")

    var settings = Settings(tables = LinkedHashMap()); private set

    fun table(id: String): List<Entry> = settings.tables[id].orEmpty()

    fun load(registries: HolderLookup.Provider) {
        val file = file
        val read = try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.parent)
                Files.writeString(file, gson.toJson(Settings()))
            }
            gson.fromJson(Files.readString(file), Settings::class.java) ?: Settings().also {
                GenerationsCore.LOGGER.error("Bosses: {} is empty, default rewards used", file)
            }
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Bosses: could not read {} ({}), default rewards used", file, e.message)
            Settings()
        }
        settings = resolve(read, registries)
        GenerationsCore.LOGGER.info(
            "Bosses: rewards {}",
            TABLES.joinToString(", ") { id -> "$id ${table(id).count { !it.stack.isEmpty }}" },
        )
    }

    /** Checks every value and turns the items into stacks (an unknown item is kept in the file, unused). */
    @Suppress("SENSELESS_COMPARISON")
    private fun resolve(read: Settings, registries: HolderLookup.Provider): Settings {
        if (read.tables == null) read.tables = LinkedHashMap()
        if (read.megaStones == null) read.megaStones = MegaStones()
        read.megaStones.validate()
        val ops = RegistryOps.create(JsonOps.INSTANCE, registries)
        val tables = LinkedHashMap<String, MutableList<Entry>>()
        for (id in TABLES) {
            val entries = read.tables[id]?.filterNotNull()?.toMutableList() ?: mutableListOf()
            for (entry in entries) {
                validate(entry)
                entry.stack = decode(entry.item, ops)
                if (entry.stack.isEmpty) BossConfig.warn("rewards.json: unknown item {} in {}, skipped", entry.item, id)
            }
            tables[id] = entries
        }
        read.tables = tables
        return read
    }

    private fun validate(entry: Entry) {
        entry.min = entry.min.coerceIn(1, 999)
        entry.max = entry.max.coerceIn(entry.min, 999)
        if (!entry.chance.isFinite()) entry.chance = 100.0
        entry.chance = entry.chance.coerceIn(0.0, 100.0)
    }

    private fun decode(json: JsonElement?, ops: RegistryOps<JsonElement>): ItemStack {
        if (json == null || json.isJsonNull) return ItemStack.EMPTY
        if (json.isJsonPrimitive) {
            val id = ResourceLocation.tryParse(json.asString.trim()) ?: return ItemStack.EMPTY
            return BuiltInRegistries.ITEM.getOptional(id).map { ItemStack(it) }.orElse(ItemStack.EMPTY)
        }
        return runCatching { ItemStack.CODEC.parse(ops, json).result().orElse(ItemStack.EMPTY) }.getOrDefault(ItemStack.EMPTY).copyWithCount(1)
    }

    private fun encode(stack: ItemStack, ops: RegistryOps<JsonElement>): JsonElement {
        val one = stack.copyWithCount(1)
        val id = JsonPrimitive(BuiltInRegistries.ITEM.getKey(one.item).toString())
        if (one.componentsPatch.isEmpty) return id
        return runCatching { ItemStack.CODEC.encodeStart(ops, one).result().orElse(id) }.getOrDefault(id)
    }

    /** The items won against a boss of this rarity (and Titan), as stacks no bigger than the item allows. */
    fun roll(rarity: BossRarity, titan: Boolean, random: RandomSource): MutableList<ItemStack> {
        val won = ArrayList<ItemStack>()
        val tables = if (titan) listOf(rarity.id, TITAN) else listOf(rarity.id)
        for (id in tables) {
            for (entry in table(id)) {
                if (entry.stack.isEmpty || random.nextDouble() * 100.0 >= entry.chance) continue
                var count = entry.min + random.nextInt(entry.max - entry.min + 1)
                val max = entry.stack.maxStackSize.coerceAtLeast(1)
                while (count > 0) {
                    val part = minOf(count, max)
                    won += entry.stack.copyWithCount(part)
                    count -= part
                }
            }
        }
        return won
    }

    /** The Mega Stone of a Mega form, or null (none set, unknown item). */
    fun stone(mega: BossMegas.MegaForm): ItemStack? {
        val id = settings.megaStones.stones[mega.key] ?: BossMegas.DEFAULT_STONES[mega.key] ?: return null
        if (id.isBlank()) return null
        val location = ResourceLocation.tryParse(id) ?: return null
        return BuiltInRegistries.ITEM.getOptional(location).map { ItemStack(it) }.orElse(null)
    }

    // ---- In-game editor ---------------------------------------------------------

    class Edit(val stack: ItemStack, val min: Int, val max: Int, val chance: Double)

    /** The tables as the editor shows them (TABLES order). */
    fun forEditor(): List<List<Edit>> = TABLES.map { id ->
        table(id).filter { !it.stack.isEmpty }.map { Edit(it.stack.copy(), it.min, it.max, it.chance) }
    }

    /**
     * Saves the editor's tables: written to rewards.json and used at once. Entries of
     * the file whose item is unknown (a mod missing) are kept.
     */
    fun saveFromEditor(tables: List<List<Edit>>, firstChance: Double, repeatChance: Double, registries: HolderLookup.Provider): Boolean {
        val ops = RegistryOps.create(JsonOps.INSTANCE, registries)
        val saved = LinkedHashMap<String, MutableList<Entry>>()
        TABLES.forEachIndexed { index, id ->
            val entries = tables.getOrNull(index).orEmpty().filter { !it.stack.isEmpty }.map { edit ->
                Entry(encode(edit.stack, ops), edit.min, edit.max, edit.chance).also { validate(it) }
            }.toMutableList()
            entries += table(id).filter { it.stack.isEmpty }
            saved[id] = entries
        }
        val stones = MegaStones(
            settings.megaStones.enabled, firstChance, repeatChance, LinkedHashMap(settings.megaStones.stones),
        ).also { it.validate() }
        val next = Settings(settings.version, saved, stones)
        return try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(next))
            settings = resolve(next, registries)
            true
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Bosses: could not write {}", file, e)
            false
        }
    }

    private fun item(id: String, min: Int, max: Int, chance: Double) = Entry(JsonPrimitive(id), min, max, chance)

    /** Default rewards: items of Cobblemon and Generations only (decision D4). */
    private fun defaultTables(): MutableMap<String, MutableList<Entry>> = linkedMapOf(
        "common" to mutableListOf(
            item("cobblemon:exp_candy_s", 1, 3, 100.0),
            item("cobblemon:super_potion", 1, 2, 50.0),
            item("cobblemon:ultra_ball", 1, 2, 30.0),
        ),
        "uncommon" to mutableListOf(
            item("cobblemon:exp_candy_m", 1, 3, 100.0),
            item("cobblemon:hyper_potion", 1, 2, 50.0),
            item("cobblemon:revive", 1, 1, 30.0),
            item("cobblemon:rare_candy", 1, 1, 25.0),
        ),
        "rare" to mutableListOf(
            item("cobblemon:exp_candy_l", 1, 2, 100.0),
            item("cobblemon:rare_candy", 1, 2, 60.0),
            item("cobblemon:pp_up", 1, 1, 30.0),
            item("cobblemon:protein", 1, 1, 25.0),
            item("cobblemon:fire_stone", 1, 1, 15.0),
        ),
        "epic" to mutableListOf(
            item("cobblemon:exp_candy_xl", 1, 2, 100.0),
            item("cobblemon:rare_candy", 2, 3, 100.0),
            item("cobblemon:pp_max", 1, 1, 40.0),
            item("cobblemon:ability_capsule", 1, 1, 30.0),
            item("generations_core:bottle_cap", 1, 1, 25.0),
        ),
        "legendary" to mutableListOf(
            item("cobblemon:exp_candy_xl", 2, 4, 100.0),
            item("cobblemon:rare_candy", 3, 5, 100.0),
            item("cobblemon:max_revive", 1, 2, 50.0),
            item("cobblemon:ability_patch", 1, 1, 40.0),
            item("generations_core:gold_bottle_cap", 1, 1, 20.0),
        ),
        TITAN to mutableListOf(
            item("cobblemon:rare_candy", 1, 2, 100.0),
            item("cobblemon:full_restore", 1, 2, 50.0),
            item("generations_core:bottle_cap", 1, 1, 30.0),
        ),
    )
}
