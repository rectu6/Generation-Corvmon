package generations.gg.generations.core.generationscore.common.world.eon

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongArrayTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * A summoning of Latias or Latios whose Pokémon has not appeared yet (EON-PLAN.md § 7): the
 * dialogue's properties ("latias level=65"), the scene, where the original would have made it
 * appear, the scene's landing point.
 */
class EonPendingSummon(
    val player: UUID,
    val playerName: String,
    val properties: String,
    val scene: String,
    val dimension: String,
    val x: Double, val y: Double, val z: Double, val yaw: Float,
    val landingX: Double, val landingY: Double, val landingZ: Double,
    val timeMs: Long,
)

/**
 * The Eon manifestations and summonings, saved with the world: the Overworld's SavedData
 * generations_eon, one EonRecord by player and the summonings waiting for their Pokémon. Marked
 * dirty at every change; written at once when a fragment is won or given and around a summoning
 * (it must survive a crash: nothing given twice, nothing lost).
 */
class EonData : SavedData() {
    val records = HashMap<UUID, EonRecord>()
    val summons = LinkedHashMap<UUID, EonPendingSummon>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        records.forEach { (player, r) ->
            list.add(CompoundTag().apply {
                putUUID("player", player)
                putLong("counted", r.counted)
                putLong("total", r.totalTicks)
                putLong("presence", r.presence)
                putBoolean("fresh", r.fresh)
                putBoolean("owed", r.owed)
                putDouble("blocks", r.intervalBlocks)
                put("cells", LongArrayTag(r.cells.toLongArray()))
                put("previousCells", LongArrayTag(r.previousCells.toLongArray()))
                putInt("pending", r.pending)
                putLong("pendingSession", r.pendingSession)
                putLong("rewardedSession", r.rewardedSession)
                putInt("draws", r.draws)
                putInt("manifestations", r.manifestations)
                putInt("successes", r.successes)
                putInt("failures", r.failures)
                putInt("fragments", r.fragments)
                val rewards = ListTag()
                for (w in r.rewards) rewards.add(CompoundTag().apply {
                    putString("dimension", w.dimension)
                    putDouble("x", w.x)
                    putDouble("z", w.z)
                    putString("biome", w.biome)
                    putLong("time", w.timeMs)
                })
                put("rewards", rewards)
            })
        }
        tag.put("records", list)
        val pending = ListTag()
        for (p in summons.values) pending.add(CompoundTag().apply {
            putUUID("player", p.player)
            putString("name", p.playerName)
            putString("properties", p.properties)
            putString("scene", p.scene)
            putString("dimension", p.dimension)
            putDouble("x", p.x); putDouble("y", p.y); putDouble("z", p.z)
            putFloat("yaw", p.yaw)
            putDouble("lx", p.landingX); putDouble("ly", p.landingY); putDouble("lz", p.landingZ)
            putLong("time", p.timeMs)
        })
        tag.put("summons", pending)
        return tag
    }

    companion object {
        const val NAME = "generations_eon"

        fun of(server: MinecraftServer): EonData =
            server.overworld().dataStorage.computeIfAbsent(Factory(::EonData, ::load, null), NAME)

        private fun load(tag: CompoundTag, @Suppress("UNUSED_PARAMETER") registries: HolderLookup.Provider) = EonData().apply {
            tag.getList("records", Tag.TAG_COMPOUND.toInt()).forEach { entry ->
                val c = entry as CompoundTag
                if (!c.hasUUID("player")) return@forEach
                runCatching {
                    val r = EonRecord()
                    r.counted = c.getLong("counted")
                    r.totalTicks = c.getLong("total")
                    r.presence = c.getLong("presence")
                    r.fresh = !c.contains("fresh") || c.getBoolean("fresh")
                    r.owed = c.getBoolean("owed")
                    r.intervalBlocks = c.getDouble("blocks")
                    c.getLongArray("cells").forEach { r.cells += it }
                    c.getLongArray("previousCells").forEach { r.previousCells += it }
                    r.pending = c.getInt("pending").coerceAtLeast(0)
                    r.pendingSession = c.getLong("pendingSession")
                    r.rewardedSession = c.getLong("rewardedSession")
                    r.draws = c.getInt("draws")
                    r.manifestations = c.getInt("manifestations")
                    r.successes = c.getInt("successes")
                    r.failures = c.getInt("failures")
                    r.fragments = c.getInt("fragments")
                    c.getList("rewards", Tag.TAG_COMPOUND.toInt()).forEach { w ->
                        val wc = w as CompoundTag
                        r.rewards += EonReward(wc.getString("dimension"), wc.getDouble("x"), wc.getDouble("z"), wc.getString("biome"), wc.getLong("time"))
                    }
                    records[c.getUUID("player")] = r
                }
            }
            tag.getList("summons", Tag.TAG_COMPOUND.toInt()).forEach { entry ->
                val c = entry as CompoundTag
                if (!c.hasUUID("player")) return@forEach
                val p = EonPendingSummon(
                    c.getUUID("player"), c.getString("name"), c.getString("properties"), c.getString("scene"), c.getString("dimension"),
                    c.getDouble("x"), c.getDouble("y"), c.getDouble("z"), c.getFloat("yaw"),
                    c.getDouble("lx"), c.getDouble("ly"), c.getDouble("lz"), c.getLong("time"),
                )
                summons[p.player] = p
            }
        }

        fun get(server: MinecraftServer, player: UUID): EonRecord? = of(server).records[player]

        fun getOrCreate(server: MinecraftServer, player: UUID): EonRecord {
            val data = of(server)
            return data.records.getOrPut(player) { data.setDirty(); EonRecord() }
        }

        fun changed(server: MinecraftServer) = of(server).setDirty()

        fun saveNow(server: MinecraftServer) {
            of(server).setDirty()
            server.overworld().dataStorage.save()
        }
    }
}
