package generations.gg.generations.core.generationscore.common.client

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.blue
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.text.strikethrough
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.render.*
import generations.gg.generations.core.generationscore.common.battle.ConditionsData
import generations.gg.generations.core.generationscore.common.battle.BattleSideData
import generations.gg.generations.core.generationscore.common.battle.WeatherTracker
import generations.gg.generations.core.generationscore.common.util.colorSort
import generations.gg.generations.core.generationscore.common.util.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation

object BattleConditionsOverlay {
    var isActor: Boolean? = null
    var currentConditionsData: ConditionsData? = null

    fun onBattleDataReceived(isActorFromPacket: Boolean, conditionsData: ConditionsData) {
        isActor = isActorFromPacket
        currentConditionsData = conditionsData
    }

    fun renderConditionsOverlay(context: GuiGraphics) {
        val textureLocation = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/battle/battle_log_expanded.png");

        val screenWidth = Minecraft.getInstance().window.guiScaledWidth
        val screenHeight = Minecraft.getInstance().window.guiScaledHeight

        val textureWidth = 169;
        val textureHeight = 101;

        val x = screenWidth - 181;
        val y = (screenHeight - textureHeight) / 2;

        val opacity = CobblemonClient.battleOverlay.opacity

        val font = CobblemonResources.DEFAULT_LARGE

        val alpha = (opacity * 255).toInt() shl 24
        val color = 0x00FFFFFF or alpha

        val textX = x + 10;
        val textY = y + 8;
        val lineHeight = 10;

        blitk(context.pose(), textureLocation, x, y, textureHeight, textureWidth, 0, 0, textureWidth, textureHeight, 0, 1, 1, 1, opacity, true, 1.0f)
        context.enableScissor(x + 5, y + 6, x + 174, y + 98);

        var displayList = mutableListOf<MutableComponent>()

        currentConditionsData?.let { battleData ->
            val legendList = setLegend(battleData)
            displayList = legendList

            val weatherComponent = resolveWeatherTurns(battleData.weatherTracker)
            displayList.add(weatherComponent)

            for ((effect, turnCounter) in battleData.fieldList) {
                val fieldComponent = resolveFieldTurns(effect, turnCounter)
                displayList.add(fieldComponent)
            }

            for (sideData in battleData.sideList) {
                val sideComponent: Component
                if (sideData.effect.contains("Spikes") || sideData.effect == "Sticky Web" || sideData.effect == "Stealth Rock") {
                    sideComponent = resolveHazards(sideData)
                } else {
                    sideComponent = resolveSideTurns(sideData)
                }
                displayList.add(sideComponent)
            }
        } ?: run {
            val text = "No Field Conditions".text().bold()
            displayList.add(text)
        }

        for ((i, component) in displayList.filter { it.string != "" }.withIndex()) {
            drawScaledText(
                context, font, component,
                textX, textY + i * lineHeight,
                1.0f, opacity, Int.MAX_VALUE, color, false, false, null, null
            )
        }

        context.disableScissor();
    }

    private fun setLegend(battleData: ConditionsData): MutableList<MutableComponent> {
        val legendList = mutableListOf<MutableComponent>()
        var sideMap = mutableMapOf<String, String>()
        for (sideData in battleData.sideList) {
            if (sideData.effect == "legend") {
                if (isActor == true) {
                    if (Minecraft.getInstance().player?.name?.string == sideData.playerName) {
                        sideMap["1"] = sideData.playerName
                    } else {
                        sideMap["2"] = sideData.playerName
                    }
                } else {
                    sideMap[sideData.side] = sideData.playerName
                }
            }
        }

        legendList.add("⬤ ${sideMap["1"]?.toPossessive()} Side".text().withRgb(0x227ace).bold())
        legendList.add("⬤ ${sideMap["2"]?.toPossessive()} Side".text().withRgb(0xbe2f2f).bold())

        return legendList
    }

    private fun resolveFieldTurns(condition: String, turnCounter: Int): MutableComponent {
        var fieldComponent = ""
        if (condition == "Unknown" || condition == "") {

        } else if (condition.contains("Terrain")) {
            if (turnCounter < 5) {
                fieldComponent = "$condition (${5 - turnCounter} or ${8 - turnCounter} turns)"
            } else {
                fieldComponent = "$condition (${8 - turnCounter} turns)"
            }
        } else if (condition.contains("Room") || condition == "Gravity") {
            fieldComponent = "$condition (${5 - turnCounter} turns)"
        }
        return fieldComponent.text().bold()
    }

    private fun resolveSideTurns(sideData: BattleSideData): MutableComponent {
        var sideComponent: String
        val turnCounter = sideData.turnCounter

        if (sideData.effect == "legend") {
            return "".text()
        }

        if (sideData.effect == "Reflect" || sideData.effect == "Light Screen" || sideData.effect == "Aurora Veil") {
            if (turnCounter < 5) {
                sideComponent = "${sideData.effect} (${5 - turnCounter} or ${8 - turnCounter} turns)"
            } else {
                sideComponent = "${sideData.effect} (${8 - turnCounter} turns)"
            }
        } else if (sideData.effect == "Tailwind") {
            sideComponent = "${sideData.effect} (${4 - turnCounter} turns)"
        } else {
            sideComponent = "${sideData.effect} (${5 - turnCounter} turns)"
        }

        return sideComponent.text().bold()
    }

    private fun resolveHazards(sideData: BattleSideData): MutableComponent {
        val hazardComponent: String

        val layers: String = if (sideData.layers == 1) "layer" else "layers"
        if (sideData.effect.contains("Spikes")) {
            hazardComponent = "${sideData.effect} (${sideData.layers} $layers)"
        } else {
            hazardComponent = sideData.effect
        }

        return hazardComponent.text().colorSort(sideData).bold()
    }

    private fun resolveWeatherTurns(weatherTracker: WeatherTracker): MutableComponent {
        val weather = weatherTracker.weather
        val turnCounter = weatherTracker.turns
        val upkeep = weatherTracker.upkeep
        val disabled = weatherTracker.disabled

        if (weather == "" || weather == "none") {
            return "".text()
        }

        var weatherComponent: MutableComponent
        val oras = setOf("Delta Stream", "Desolate Land", "Primordial Sea")

        if (oras.contains(weather)) {
            weatherComponent = weather.text()
        } else if (upkeep && turnCounter < 5) {
            weatherComponent = "$weather (${5 - turnCounter} or ${8 - turnCounter} turns)".text()
        } else if (upkeep && turnCounter >= 5) {
            weatherComponent = "$weather (${8 - turnCounter} turns)".text()
        } else {
            weatherComponent = "$weather (${5 - turnCounter} or ${8 - turnCounter} turns)".text()
        }

        if (disabled) weatherComponent.strikethrough()

        return weatherComponent.bold()
    }

    fun String.toPossessive(): String =
        if (endsWith("s", ignoreCase = true)) "$this'" else "$this's"

}