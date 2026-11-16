package generations.gg.generations.core.generationscore.common.network.packets.boss

import com.cobblemon.mod.common.api.net.NetworkPacket
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.boss.BossRewards
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/** Most entries per table and tables sent, against a broken or hostile packet. */
private const val MAX_ENTRIES = 256

private fun writeTables(buffer: RegistryFriendlyByteBuf, tables: List<List<BossRewards.Edit>>) {
    buffer.writeVarInt(tables.size)
    for (table in tables) {
        buffer.writeVarInt(table.size)
        for (entry in table) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.stack)
            buffer.writeVarInt(entry.min)
            buffer.writeVarInt(entry.max)
            buffer.writeDouble(entry.chance)
        }
    }
}

private fun readTables(buffer: RegistryFriendlyByteBuf): List<List<BossRewards.Edit>> {
    val count = buffer.readVarInt().coerceIn(0, BossRewards.TABLES.size)
    return List(count) {
        val size = buffer.readVarInt()
        require(size in 0..MAX_ENTRIES) { "too many boss rewards: $size" }
        List(size) {
            BossRewards.Edit(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble())
        }
    }
}

/**
 * A boss was beaten: what the player won, shown by BossVictoryScreen. The items are
 * already in the inventory (or at the player's feet when it is full).
 */
class S2CBossVictoryPacket(
    /** Species id ("cobblemon:charizard"), empty for /boss rewards test. */
    val species: String,
    val aspects: List<String>,
    val rarity: String,
    val titan: Boolean,
    val level: Int,
    val rewards: List<ItemStack>,
    /** The Mega Stone at stake, empty when there was none. */
    val stone: ItemStack,
    val stoneResult: StoneResult,
    /** Chance of the stone, in %. */
    val stoneChance: Double,
    /** Stacks that did not fit and were dropped at the player's feet. */
    val dropped: Int,
) : NetworkPacket<S2CBossVictoryPacket> {
    enum class StoneResult { NONE, FIRST, LUCKY, MISSED, NO_STONE }

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(species)
        buffer.writeCollection(aspects, FriendlyByteBuf::writeUtf)
        buffer.writeUtf(rarity)
        buffer.writeBoolean(titan)
        buffer.writeVarInt(level)
        buffer.writeVarInt(rewards.size)
        rewards.forEach { ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, it) }
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stone)
        buffer.writeEnum(stoneResult)
        buffer.writeDouble(stoneChance)
        buffer.writeVarInt(dropped)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("boss_victory")

        fun decode(buf: RegistryFriendlyByteBuf): S2CBossVictoryPacket {
            val species = buf.readUtf()
            val aspects = buf.readList(FriendlyByteBuf::readUtf)
            val rarity = buf.readUtf()
            val titan = buf.readBoolean()
            val level = buf.readVarInt()
            val rewards = List(buf.readVarInt().coerceIn(0, MAX_ENTRIES)) { ItemStack.OPTIONAL_STREAM_CODEC.decode(buf) }
            val stone = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)
            return S2CBossVictoryPacket(species, aspects, rarity, titan, level, rewards, stone, buf.readEnum(StoneResult::class.java), buf.readDouble(), buf.readVarInt())
        }
    }
}

/** Opens the rewards editor (/boss rewards) with the current tables (BossRewards.TABLES order). */
class S2CBossRewardsEditorPacket(
    val tables: List<List<BossRewards.Edit>>,
    val firstChance: Double,
    val repeatChance: Double,
) : NetworkPacket<S2CBossRewardsEditorPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        writeTables(buffer, tables)
        buffer.writeDouble(firstChance)
        buffer.writeDouble(repeatChance)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("boss_rewards_editor")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CBossRewardsEditorPacket(readTables(buf), buf.readDouble(), buf.readDouble())
    }
}

/** The editor's tables, to save (the server checks the permission again). */
class C2SBossRewardsSavePacket(
    val tables: List<List<BossRewards.Edit>>,
    val firstChance: Double,
    val repeatChance: Double,
) : NetworkPacket<C2SBossRewardsSavePacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        writeTables(buffer, tables)
        buffer.writeDouble(firstChance)
        buffer.writeDouble(repeatChance)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("boss_rewards_save")

        fun decode(buf: RegistryFriendlyByteBuf) = C2SBossRewardsSavePacket(readTables(buf), buf.readDouble(), buf.readDouble())
    }
}
