package generations.gg.generations.core.generationscore.common.battle

import java.util.UUID

data class ConditionsData (
    val weatherTracker: WeatherTracker = WeatherTracker(),
    val fieldList: MutableList<Pair<String, Int>> = mutableListOf<Pair<String, Int>>(),
    val sideList: MutableList<BattleSideData> = mutableListOf<BattleSideData>(),
)

data class BattleSideData(
    var side: String = "",
    var playerName: String = "",
    var effect: String = "",
    var layers: Int = 0,
    var turnCounter: Int = 0
)

data class WeatherTracker(
    var weather: String = "",
    var turns: Int = 0,
    var upkeep: Boolean = false,
    var disabled: Boolean = false

)
