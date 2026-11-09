package generations.gg.generations.core.generationscore.common.client.screen.raid

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdButton
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdGui
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import generations.gg.generations.core.generationscore.common.network.packets.raid.RaidPick
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * Picks the Pokémon the player brings to the raid, in HD: the party first, then each
 * PC box that holds Pokémon. A click selects it and goes back to the lobby.
 */
class RaidPickerScreen(val lobbyScreen: RaidLobbyScreen) : Screen(Component.translatable("generations_core.raid.picker.title")) {
    /** Page 0 is the party, then one page per PC box with Pokémon. */
    private val pages: List<Pair<Component, List<RaidPick>>> = buildList {
        add(Component.translatable("generations_core.raid.picker.party") to lobbyScreen.lobby.picks.filter { it.box < 0 })
        lobbyScreen.lobby.picks.filter { it.box >= 0 }.groupBy { it.box }.toSortedMap().forEach { (box, picks) ->
            add(Component.translatable("generations_core.raid.picker.box", box + 1) to picks.sortedBy { it.slot })
        }
    }
    private var page = pages.indexOfFirst { (_, picks) -> picks.any { it.uuid == lobbyScreen.selected } }.coerceAtLeast(0)
    private var left = 0f
    private var top = 0f

    override fun init() {
        left = ((width - WIDTH) / 2).toFloat()
        top = ((height - HEIGHT) / 2).toFloat()
        // The Pokémon are drawn directly: up to 30 at once.
        current().forEachIndexed { i, pick ->
            val species = ResourceLocation.tryParse(pick.species)?.let(PokemonSpecies::getByIdentifier) ?: return@forEachIndexed
            addRenderableOnly(ModelWidget(slotX(i).toInt(), slotY(i).toInt(), SLOT, SLOT, RenderablePokemon(species, pick.aspects.toSet()), 1.1f, 325f, -3.0))
        }
        val arrows = pages.size > 1
        addRenderableWidget(HdButton((left + 8).toInt(), (top + 6).toInt(), 22, 18, Component.literal("<")) { turn(-1) }).active = arrows
        addRenderableWidget(HdButton((left + WIDTH - 30).toInt(), (top + 6).toInt(), 22, 18, Component.literal(">")) { turn(1) }).active = arrows
        addRenderableWidget(HdButton((left + WIDTH / 2 - 60).toInt(), (top + HEIGHT - 26).toInt(), 120, 18, Component.translatable("generations_core.raid.picker.back")) { onClose() })
    }

    private fun current(): List<RaidPick> = pages.getOrNull(page)?.second ?: emptyList()

    private fun turn(delta: Int) {
        page = Math.floorMod(page + delta, pages.size)
        rebuildWidgets()
    }

    private fun slotX(i: Int) = left + 12f + (i % COLUMNS) * (SLOT + 4)
    private fun slotY(i: Int) = top + 34f + (i / COLUMNS) * (SLOT + 4)

    private fun slotAt(mouseX: Double, mouseY: Double): Int? = current().indices.firstOrNull { i ->
        HdGui.isOver(mouseX, mouseY, slotX(i), slotY(i), SLOT.toFloat(), SLOT.toFloat())
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        slotAt(mouseX, mouseY)?.let { i ->
            lobbyScreen.select(current()[i].uuid)
            onClose()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        HdGui.panel(graphics, HdGui.PANEL_LIGHT, left, top, WIDTH.toFloat(), HEIGHT.toFloat(), corner = 12f)
        HdGui.panel(graphics, HdGui.PANEL_RED, left, top, WIDTH.toFloat(), 30f, corner = 12f)
        val hovered = slotAt(mouseX.toDouble(), mouseY.toDouble())
        current().forEachIndexed { i, pick ->
            val x = slotX(i)
            val y = slotY(i)
            HdGui.panel(graphics, HdGui.PANEL_LIGHT, x, y, SLOT.toFloat(), SLOT.toFloat(), 0xFFE6E9EE.toInt(), corner = 6f)
            when {
                pick.uuid == lobbyScreen.selected -> HdGui.panel(graphics, HdGui.PANEL_OUTLINE, x, y, SLOT.toFloat(), SLOT.toFloat(), RaidLobbyScreen.RED, corner = 6f)
                hovered == i -> HdGui.panel(graphics, HdGui.PANEL_OUTLINE, x, y, SLOT.toFloat(), SLOT.toFloat(), 0xB0E8304A.toInt(), corner = 6f)
            }
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val name = pages.getOrNull(page)?.first?.string ?: ""
        HdText.draw(graphics, "${title.string} – $name", left + WIDTH / 2f, top + 9.5f, 10f, RaidLobbyScreen.WHITE, bold = true, align = HdText.Align.CENTER, shadow = true)
        if (current().isEmpty()) {
            HdText.draw(graphics, Component.translatable("generations_core.raid.picker.empty"), left + WIDTH / 2f, top + HEIGHT / 2f, 9f, RaidLobbyScreen.DARK, align = HdText.Align.CENTER)
        }
        slotAt(mouseX.toDouble(), mouseY.toDouble())?.let { i ->
            val label = current()[i].label
            val w = HdText.width(label, 8f, true) + 12f
            val x = (mouseX + 10f).coerceAtMost(width - w - 4f)
            val y = mouseY - 16f
            HdGui.panel(graphics, HdGui.PANEL_DARK, x, y, w, 14f, corner = 5f)
            HdText.draw(graphics, label, x + 6f, y + 3f, 8f, RaidLobbyScreen.WHITE, bold = true)
        }
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(lobbyScreen)
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        private const val COLUMNS = 6
        private const val SLOT = 34
        private const val WIDTH = 12 * 2 + COLUMNS * (SLOT + 4) - 4
        private const val HEIGHT = 34 + 5 * (SLOT + 4) + 30
    }
}
