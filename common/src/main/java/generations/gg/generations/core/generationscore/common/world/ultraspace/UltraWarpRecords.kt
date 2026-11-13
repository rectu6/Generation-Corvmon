package generations.gg.generations.core.generationscore.common.world.ultraspace

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Each player's Ultra Warp Rides, saved with the world: the last distance, the best,
 * the number of journeys and the light-years in all. Written at the end of every
 * journey (whatever ended it), for the later phases (special encounters, shiny
 * chances) and the end message.
 */
class UltraWarpRecords : SavedData() {
    class Record(var last: Double = 0.0, var best: Double = 0.0, var journeys: Int = 0, var total: Double = 0.0) {
        /** Best distance in each difficulty mode (by its id). */
        val bestByMode = HashMap<String, Double>()

        /** The last journeys' outcomes, newest last (OUTCOMES at most). */
        val outcomes = ArrayList<UltraWarpOutcome>()
    }

    private val records = HashMap<UUID, Record>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        records.forEach { (player, record) ->
            list.add(CompoundTag().apply {
                putUUID("player", player)
                putDouble("last", record.last)
                putDouble("best", record.best)
                putInt("journeys", record.journeys)
                putDouble("total", record.total)
                put("modes", CompoundTag().apply { record.bestByMode.forEach { (mode, best) -> putDouble(mode, best) } })
                put("outcomes", ListTag().apply { record.outcomes.forEach { add(it.save()) } })
            })
        }
        tag.put("records", list)
        return tag
    }

    companion object {
        private const val NAME = "generations_ultra_warp_records"

        private fun of(server: MinecraftServer): UltraWarpRecords =
            server.overworld().dataStorage.computeIfAbsent(Factory(::UltraWarpRecords, ::load, null), NAME)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = UltraWarpRecords().apply {
            tag.getList("records", Tag.TAG_COMPOUND.toInt()).forEach { entry ->
                val compound = entry as CompoundTag
                runCatching {
                    val record = Record(compound.getDouble("last"), compound.getDouble("best"), compound.getInt("journeys"), compound.getDouble("total"))
                    val modes = compound.getCompound("modes")
                    modes.allKeys.forEach { record.bestByMode[it] = modes.getDouble(it) }
                    compound.getList("outcomes", Tag.TAG_COMPOUND.toInt()).forEach { outcome ->
                        runCatching { record.outcomes.add(UltraWarpOutcome.load(outcome as CompoundTag)) }
                    }
                    records[compound.getUUID("player")] = record
                }
            }
        }

        /**
         * A journey of this many light-years in this mode ended: the player's best in
         * that mode after it, and whether it is a new one.
         */
        fun add(server: MinecraftServer, player: UUID, lightYears: Double, mode: String): Pair<Double, Boolean> {
            val data = of(server)
            val record = data.records.getOrPut(player) { Record() }
            val previous = record.bestByMode[mode] ?: 0.0
            val best = lightYears > previous
            if (best) record.bestByMode[mode] = lightYears
            record.last = lightYears
            if (lightYears > record.best) record.best = lightYears
            record.journeys++
            record.total += lightYears
            data.setDirty()
            return (record.bestByMode[mode] ?: lightYears) to best
        }

        fun get(server: MinecraftServer, player: UUID): Record? = of(server).records[player]

        private const val OUTCOMES = 20

        /** A journey's outcome, kept with the player's (the last OUTCOMES); once per journey (a second one replaces it). */
        fun addOutcome(server: MinecraftServer, outcome: UltraWarpOutcome) {
            val data = of(server)
            val record = data.records.getOrPut(outcome.player) { Record() }
            record.outcomes.removeIf { it.journey == outcome.journey }
            record.outcomes.add(outcome)
            while (record.outcomes.size > OUTCOMES) record.outcomes.removeAt(0)
            data.setDirty()
        }

        /** The arrival of a journey's outcome changed (UltraWarpTransfers): the saved one follows. */
        fun updateOutcome(server: MinecraftServer, outcome: UltraWarpOutcome) {
            val data = of(server)
            val outcomes = data.records[outcome.player]?.outcomes ?: return
            val i = outcomes.indexOfFirst { it.journey == outcome.journey }
            if (i >= 0) outcomes[i] = outcome else outcomes.add(outcome)
            while (outcomes.size > OUTCOMES) outcomes.removeAt(0)
            data.setDirty()
        }
    }
}
