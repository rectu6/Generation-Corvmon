package generations.gg.generations.core.generationscore.common.client.screen.battle

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.battles.PassActionResponse
import com.cobblemon.mod.common.battles.ShowdownPokemon
import com.cobblemon.mod.common.battles.SwitchActionResponse
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.SingleActionRequest
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleBackButton
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.battleLang
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdGui
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdModel
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component

/**
 * Choosing the Pokémon to send, in the style of Sword and Shield: the team as a
 * column of cards on the right (portrait, name, level, HP, status). Same rules as
 * Cobblemon's screen, which it replaces: fainted Pokémon and those already in
 * battle cannot be sent, except when reviving (Revival Blessing).
 */
class SwshSwitchSelection(battleGUI: BattleGUI, request: SingleActionRequest) : BattleActionSelection(
    battleGUI, request, 0, 0,
    Minecraft.getInstance().window.guiScaledWidth, Minecraft.getInstance().window.guiScaledHeight,
    battleLang("switch_pokemon"),
) {
    private class Card(val pokemon: Pokemon, val showdown: ShowdownPokemon, val fainted: Boolean, val inBattle: Boolean) {
        val state = FloatingState().also { it.currentAspects = pokemon.aspects }
    }

    private val cards = mutableListOf<Card>()
    private val backButton = BattleBackButton(SwshBattleUi.actionX().toFloat(), SwshBattleUi.buttonsY())
    private var reviving = false

    init {
        val actor = battleGUI.actor
        val pending = CobblemonClient.battle?.pendingActionRequests ?: emptyList()
        val switchingIn = pending.mapNotNull { it.response }.filterIsInstance<SwitchActionResponse>().map { it.newPokemonId }
        val pairs = request.side?.pokemon?.mapNotNull { showdown ->
            actor?.pokemon?.find { it.uuid == showdown.uuid }?.let { showdown to it }
        }?.filter { it.second.uuid !in switchingIn } ?: emptyList()
        val active = actor?.activePokemon?.mapNotNull { it.battlePokemon?.uuid } ?: emptyList()

        reviving = request.side?.pokemon?.any { it.reviving && it.uuid == request.activePokemon.battlePokemon?.uuid } == true
        if (request.forceSwitch && !reviving && pairs.all { it.second.uuid in active || "fnt" in it.first.condition }) {
            // Not enough Pokémon left to fill every empty place.
            battleGUI.selectAction(request, PassActionResponse)
        }
        pairs.forEach { (showdown, pokemon) -> cards += Card(pokemon, showdown, "fnt" in showdown.condition, pokemon.uuid in active) }
    }

    private fun selectable(card: Card) = if (reviving) card.fainted else !card.fainted && !card.inBattle

    private val cardX get() = SwshBattleUi.actionX() - (CARD_W - SwshBattleUi.ACTION_WIDTH)
    private fun cardY(index: Int) = Minecraft.getInstance().window.guiScaledHeight - 30f - (cards.size - index) * (CARD_H + 3f)

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val x = cardX.toFloat()
        val title = if (reviving) Component.translatable("generations_core.battle.revive_pokemon") else battleLang("switch_pokemon")
        HdText.draw(context, title, x + CARD_W, cardY(0) - 13f, 9.5f, HdGui.withAlpha(0xFFFFFF, opacity), bold = true, align = HdText.Align.RIGHT, outline = 0xC0101018.toInt())
        cards.forEachIndexed { i, card -> renderCard(context, card, x, cardY(i), mouseX, mouseY, delta) }
        if (!request.forceSwitch) backButton.render(context, mouseX, mouseY, delta)
    }

    private fun renderCard(graphics: GuiGraphics, card: Card, x: Float, y: Float, mouseX: Int, mouseY: Int, delta: Float) {
        val canSend = selectable(card)
        val hovered = canSend && HdGui.isOver(mouseX.toDouble(), mouseY.toDouble(), x, y, CARD_W, CARD_H)
        val alpha = opacity * if (canSend || card.inBattle) 1f else 0.6f
        HdGui.panel(graphics, if (hovered) HdGui.PANEL_RED else HdGui.PANEL_DARK, x, y, CARD_W, CARD_H, HdGui.withAlpha(0xFFFFFF, opacity), corner = 6f)
        if (hovered) HdGui.draw(graphics, HdGui.Texture(SwshBattleUi.CURSOR, 48, 48), x - 12f, y + CARD_H / 2f - 5f, 10f, 10f, HdGui.withAlpha(0xFFFFFF, opacity))

        HdGui.draw(graphics, HdGui.PORTRAIT_RING, x + 3f, y + 3f, CARD_H - 6f, CARD_H - 6f, HdGui.withAlpha(0xFFFFFF, alpha))
        HdModel.portrait(graphics, card.pokemon.species, card.state, x + 4f, y + 4f, CARD_H - 8f, false, delta, alpha)

        val tx = x + CARD_H + 2f
        val level = Component.translatable("generations_core.battle.level", card.pokemon.level).string
        HdText.draw(graphics, level, x + CARD_W - 6f, y + 3.5f, 7.5f, HdGui.withAlpha(0xFFFFFF, alpha), bold = true, align = HdText.Align.RIGHT)
        val name = HdText.fit(card.pokemon.getDisplayName().string, CARD_W - CARD_H - 30f, 8.5f, true)
        HdText.draw(graphics, name, tx, y + 3f, 8.5f, HdGui.withAlpha(0xFFFFFF, alpha), bold = true)
        val gender = when (card.pokemon.gender) {
            Gender.MALE -> HdGui.GENDER_MALE
            Gender.FEMALE -> HdGui.GENDER_FEMALE
            else -> null
        }
        gender?.let { HdGui.draw(graphics, it, tx + HdText.width(name, 8.5f, true) + 2f, y + 3.5f, 6.5f, 6.5f, HdGui.withAlpha(0xFFFFFF, alpha)) }

        // "123/150 hp" or "0 fnt".
        val (hp, max) = card.showdown.condition.split(" ")[0].split("/").let { (it.getOrNull(0)?.toFloatOrNull() ?: 0f) to (it.getOrNull(1)?.toFloatOrNull() ?: 0f) }
        val maxHp = if (max > 0f) max else card.pokemon.maxHealth.toFloat()
        HdGui.healthBar(graphics, tx, y + 14f, CARD_W - CARD_H - 52f, 4f, if (maxHp > 0f) hp / maxHp else 0f, alpha)
        val right = when {
            card.fainted -> Component.translatable("generations_core.raid.hud.down").string
            card.inBattle -> Component.translatable("generations_core.battle.in_battle").string
            else -> "${hp.toInt()}/${maxHp.toInt()}"
        }
        val colour = if (card.fainted) 0xFF5060 else 0xFFFFFF
        HdText.draw(graphics, right, x + CARD_W - 6f, y + 12.5f, 7f, HdGui.withAlpha(colour, alpha), bold = true, align = HdText.Align.RIGHT)
    }

    override fun mousePrimaryClicked(mouseX: Double, mouseY: Double): Boolean {
        if (!request.forceSwitch && backButton.isHovered(mouseX, mouseY)) {
            battleGUI.changeActionSelection(null)
            playDownSound(Minecraft.getInstance().soundManager)
            return true
        }
        val x = cardX.toFloat()
        cards.forEachIndexed { i, card ->
            if (selectable(card) && HdGui.isOver(mouseX, mouseY, x, cardY(i), CARD_W, CARD_H)) {
                playDownSound(Minecraft.getInstance().soundManager)
                battleGUI.selectAction(request, SwitchActionResponse(card.pokemon.uuid))
                return true
            }
        }
        return false
    }

    override fun playDownSound(soundManager: SoundManager) {
        soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.GUI_CLICK, 1.0F))
    }

    override fun narrationPriority() = NarratableEntry.NarrationPriority.HOVERED

    companion object {
        private const val CARD_W = 160f
        private const val CARD_H = 24f
    }
}
