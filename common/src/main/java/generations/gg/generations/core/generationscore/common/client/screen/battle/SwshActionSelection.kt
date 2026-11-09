package generations.gg.generations.core.generationscore.common.client.screen.battle

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.battles.FleeAttemptActionResponse
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.SingleActionRequest
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleBackButton
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.ForfeitConfirmationSelection
import com.cobblemon.mod.common.util.battleLang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * The battle's action menu in the style of Sword and Shield: a column of pills at
 * the bottom right (Fight, Pokémon, then Capture and Run against a wild Pokémon,
 * or Forfeit). Same choices as Cobblemon's menu, which it replaces (BattleGUIMixin).
 */
class SwshActionSelection(battleGUI: BattleGUI, request: SingleActionRequest) : BattleActionSelection(
    battleGUI,
    request,
    SwshBattleUi.actionX(),
    SwshBattleUi.actionY(0, 4),
    SwshBattleUi.ACTION_WIDTH,
    SwshBattleUi.MENU_HEIGHT,
    battleLang("choose_action"),
) {
    private class Option(val label: Component, val icon: ResourceLocation, val onClick: () -> Unit)

    private val options = mutableListOf<Option>()
    private val backButton = BattleBackButton(SwshBattleUi.actionX().toFloat(), SwshBattleUi.buttonsY())
    private val lastAnswered = CobblemonClient.battle?.getLastAnsweredRequest()

    init {
        options += Option(battleLang("ui.fight"), SwshBattleUi.ICON_FIGHT) {
            battleGUI.changeActionSelection(BattleMoveSelection(battleGUI, request))
        }
        if (request.moveSet?.trapped != true) {
            options += Option(battleLang("ui.switch"), SwshBattleUi.ICON_PARTY) {
                battleGUI.changeActionSelection(
                    if (SwshBattleHud.enabled) SwshSwitchSelection(battleGUI, request) else BattleSwitchPokemonSelection(battleGUI, request)
                )
            }
        }
        val battle = CobblemonClient.battle
        if (battle != null && battle.battleFormat.battleType.pokemonPerSide == 1 && battle.side2.actors.first().type == ActorType.WILD) {
            options += Option(battleLang("ui.capture"), SwshBattleUi.ICON_BAG) {
                battle.minimised = true
                Minecraft.getInstance().player?.displayClientMessage(battleLang("throw_pokeball_prompt"), false)
            }
            options += Option(battleLang("ui.run"), SwshBattleUi.ICON_RUN) {
                battle.minimised = true
                battleGUI.selectAction(request, FleeAttemptActionResponse())
            }
        } else {
            options += Option(battleLang("ui.forfeit"), SwshBattleUi.ICON_FORFEIT) {
                battleGUI.changeActionSelection(ForfeitConfirmationSelection(battleGUI, request))
            }
        }
    }

    private fun optionY(index: Int) = SwshBattleUi.actionY(index, options.size)

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val x = SwshBattleUi.actionX()
        // As in Sword and Shield, Fight is selected until the mouse points at another action.
        val pointed = options.indices.firstOrNull { i ->
            SwshBattleUi.isOver(mouseX.toDouble(), mouseY.toDouble(), x, optionY(i), SwshBattleUi.ACTION_WIDTH, SwshBattleUi.ACTION_HEIGHT)
        } ?: 0
        options.forEachIndexed { i, option ->
            SwshBattleUi.renderAction(context, x, optionY(i), option.label, option.icon, i == pointed, opacity)
        }
        if (lastAnswered != null) backButton.render(context, mouseX, mouseY, delta)
    }

    override fun mousePrimaryClicked(mouseX: Double, mouseY: Double): Boolean {
        if (lastAnswered != null && backButton.isHovered(mouseX, mouseY)) {
            playDownSound(Minecraft.getInstance().soundManager)
            CobblemonClient.battle?.cancelLastAnsweredRequest()
            battleGUI.selectAction(request, null)
            battleGUI.changeActionSelection(null)
            return true
        }
        val x = SwshBattleUi.actionX()
        options.forEachIndexed { i, option ->
            if (SwshBattleUi.isOver(mouseX, mouseY, x, optionY(i), SwshBattleUi.ACTION_WIDTH, SwshBattleUi.ACTION_HEIGHT)) {
                playDownSound(Minecraft.getInstance().soundManager)
                option.onClick()
                return true
            }
        }
        return false
    }

    override fun playDownSound(soundManager: SoundManager) {
        soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.GUI_CLICK, 1.0F))
    }

    override fun narrationPriority() = NarratableEntry.NarrationPriority.NONE
}
