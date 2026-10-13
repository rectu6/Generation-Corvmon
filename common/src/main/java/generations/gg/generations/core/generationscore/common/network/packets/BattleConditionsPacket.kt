package generations.gg.generations.core.generationscore.common.network.packets

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import generations.gg.generations.core.generationscore.common.battle.ConditionsData
import generations.gg.generations.core.generationscore.common.battle.BattleSideData
import generations.gg.generations.core.generationscore.common.battle.WeatherTracker
import net.minecraft.network.RegistryFriendlyByteBuf

class BattleConditionsPacket(val isActor: Boolean, val conditionsData: ConditionsData) : NetworkPacket<BattleConditionsPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(isActor)

        buffer.writeString(conditionsData.weatherTracker.weather)
        buffer.writeInt(conditionsData.weatherTracker.turns)
        buffer.writeBoolean(conditionsData.weatherTracker.upkeep)
        buffer.writeBoolean(conditionsData.weatherTracker.disabled)

        buffer.writeInt(conditionsData.fieldList.size)
        for (effect in conditionsData.fieldList) {
            buffer.writeString(effect.first)
            buffer.writeInt(effect.second)
        }

        buffer.writeInt(conditionsData.sideList.size)
        for (side in conditionsData.sideList) {
            buffer.writeString(side.side)
            buffer.writeString(side.playerName)
            buffer.writeString(side.effect)
            buffer.writeInt(side.layers)
            buffer.writeInt(side.turnCounter)
        }
    }
    companion object {
        val ID = cobblemonResource("battle_conditions")
        fun decode(buffer: RegistryFriendlyByteBuf): BattleConditionsPacket {
            val isActor = buffer.readBoolean()

            val weather = buffer.readString()
            val turns = buffer.readInt()
            val upkeep = buffer.readBoolean()
            val disabled = buffer.readBoolean()
            val weatherTracker = WeatherTracker(weather, turns, upkeep, disabled)

            val fieldCount = buffer.readInt()
            val fieldList = mutableListOf<Pair<String, Int>>()
            repeat(fieldCount) {
                val effectName = buffer.readString()
                val effectValue = buffer.readInt()
                fieldList.add(effectName to effectValue)
            }

            val sideCount = buffer.readInt()
            val sideList = mutableListOf<BattleSideData>()
            repeat(sideCount) {
                val side = buffer.readString()
                val playerName = buffer.readString()
                val effect = buffer.readString()
                val layers = buffer.readInt()
                val turnCounter = buffer.readInt()
                sideList.add(BattleSideData(side, playerName, effect, layers, turnCounter)
                )
            }

            val conditionsData = ConditionsData(
                weatherTracker = weatherTracker,
                fieldList = fieldList,
                sideList = sideList
            )

            return BattleConditionsPacket(isActor, conditionsData)
        }
    }
}