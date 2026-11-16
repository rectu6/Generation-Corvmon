package generations.gg.generations.core.generationscore.common.world.boss

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * The Mega Stones each player already got from a boss, by Mega form ("charizard:mega_y"),
 * saved with the world (generations_core_boss_megas.dat): the first stone of a form
 * comes with BossRewards' firstVictoryChance, the next ones with repeatChance.
 */
class BossMegaSavedData : SavedData() {
    private val stones = HashMap<UUID, MutableSet<String>>()

    fun has(player: UUID, key: String): Boolean = stones[player]?.contains(key) == true

    fun of(player: UUID): Set<String> = stones[player].orEmpty()

    fun add(player: UUID, key: String) {
        if (stones.getOrPut(player) { LinkedHashSet() }.add(key)) setDirty()
    }

    fun reset(player: UUID): Int {
        val removed = stones.remove(player)?.size ?: 0
        if (removed > 0) setDirty()
        return removed
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val players = ListTag()
        for ((player, keys) in stones) {
            if (keys.isEmpty()) continue
            players += CompoundTag().apply {
                putUUID("player", player)
                put("stones", ListTag().apply { keys.forEach { add(StringTag.valueOf(it)) } })
            }
        }
        tag.put("players", players)
        return tag
    }

    companion object {
        private const val NAME = "generations_core_boss_megas"

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = BossMegaSavedData().apply {
            tag.getList("players", Tag.TAG_COMPOUND.toInt()).forEach { entry ->
                val player = entry as? CompoundTag ?: return@forEach
                if (!player.hasUUID("player")) return@forEach
                val keys = player.getList("stones", Tag.TAG_STRING.toInt()).map { it.asString }.filter { it.isNotBlank() }
                stones[player.getUUID("player")] = keys.toCollection(LinkedHashSet())
            }
        }

        private val FACTORY = SavedData.Factory(::BossMegaSavedData, ::load, null)

        fun get(server: MinecraftServer): BossMegaSavedData = server.overworld().dataStorage.computeIfAbsent(FACTORY, NAME)
    }
}
