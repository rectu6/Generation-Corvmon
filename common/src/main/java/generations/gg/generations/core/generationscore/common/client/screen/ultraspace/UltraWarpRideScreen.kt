package generations.gg.generations.core.generationscore.common.client.screen.ultraspace

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdButton
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdGui
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdModel
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.C2SUltraWarpActionPacket
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWarpPacket
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpRide
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpVariant
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Ultra Warp Ride menu of a Celestial Altar. Without a journey: the altar's face
 * (Sun or Moon), the Pokémon it asks for, "Start the journey" and "Cancel". With
 * one: the provisional screen of the test session, which can end it. The server
 * decides everything; this screen only shows what it sent.
 */
class UltraWarpRideScreen(private var menu: S2CUltraWarpPacket) : Screen(Component.translatable("generations_core.ultra_warp.title")) {
    private val portraitState = FloatingState()
    private var left = 0f
    private var top = 0f
    /** No second click while the server has not answered. */
    private var waiting = false

    private val shown: UltraWarpVariant get() = menu.session ?: menu.variant
    private val theme: Theme get() = if (shown == UltraWarpVariant.SUN) SUN else MOON

    override fun init() {
        left = ((width - WIDTH) / 2).toFloat()
        top = ((height - HEIGHT) / 2).toFloat()
        val y = (top + HEIGHT - 32).toInt()
        val x = left.toInt()
        if (menu.session == null) {
            addRenderableWidget(HdButton(x + 22, y, 150, 20, Component.translatable("generations_core.ultra_warp.start")) { act(C2SUltraWarpActionPacket.Action.START) })
            addRenderableWidget(HdButton(x + WIDTH - 22 - 110, y, 110, 20, Component.translatable("generations_core.ultra_warp.cancel")) { onClose() })
        } else {
            addRenderableWidget(HdButton(x + 22, y, 150, 20, Component.translatable("generations_core.ultra_warp.session.end")) { act(C2SUltraWarpActionPacket.Action.END) })
            addRenderableWidget(HdButton(x + WIDTH - 22 - 110, y, 110, 20, Component.translatable("generations_core.ultra_warp.session.close")) { onClose() })
        }
    }

    private fun act(action: C2SUltraWarpActionPacket.Action) {
        if (waiting) return
        waiting = true
        C2SUltraWarpActionPacket(menu.pos, action, menu.variant).sendToServer()
        if (action == C2SUltraWarpActionPacket.Action.END) onClose()
    }

    fun update(packet: S2CUltraWarpPacket) {
        menu = packet
        waiting = false
        rebuildWidgets()
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        HdGui.panel(graphics, HdGui.PANEL_DARK, left, top, WIDTH.toFloat(), HEIGHT.toFloat(), corner = 10f)
        HdGui.panel(graphics, HdGui.PANEL_OUTLINE, left, top, WIDTH.toFloat(), HEIGHT.toFloat(), theme.accent, corner = 10f)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val colours = theme
        val centre = left + WIDTH / 2f
        HdText.draw(graphics, title.string.uppercase(), centre, top + 10f, 11f, WHITE, bold = true, align = HdText.Align.CENTER, shadow = true)
        val subtitle = if (menu.session == null) Component.translatable("generations_core.ultra_warp.variant.${menu.variant.key}")
            else Component.translatable("generations_core.ultra_warp.session.title")
        HdText.draw(graphics, subtitle, centre, top + 26f, 8.5f, colours.accent, bold = true, align = HdText.Align.CENTER)

        // The Pokémon the altar asks for (or the one of the journey), over a glow of its colour.
        val px = left + 18f
        val py = top + 44f
        HdGui.draw(graphics, HdGui.GLOW, px - 14f, py - 12f, PORTRAIT + 28f, PORTRAIT + 24f, colours.glow)
        PokemonSpecies.getByIdentifier(shown.species)?.let { HdModel.portrait(graphics, it, portraitState, px, py, PORTRAIT, false, partialTick) }

        val tx = px + PORTRAIT + 14f
        val textWidth = left + WIDTH - 18f - tx
        if (menu.session == null) {
            HdText.draw(graphics, Component.translatable("generations_core.ultra_warp.required"), tx, top + 48f, 7.5f, GREY_TEXT)
            HdText.draw(graphics, menu.variant.speciesName(), tx, top + 59f, 10f, WHITE, bold = true)
            val (status, colour) = if (menu.hasPokemon) "generations_core.ultra_warp.in_party" to GREEN else "generations_core.ultra_warp.not_in_party" to RED
            HdText.draw(graphics, Component.translatable(status), tx, top + 73f, 7.5f, colour, bold = true)
            HdText.draw(graphics, Component.translatable("generations_core.ultra_warp.slots", menu.freeSlots, menu.maxSessions), tx, top + 88f, 7.5f, GREY_TEXT)
            // The server's difficulty (chosen in its config).
            HdText.draw(graphics, Component.translatable("generations_core.ultra_warp.mode", modeName(menu.mode)), tx, top + 100f, 7.5f, 0xFFFFC870.toInt(), bold = true)
        } else {
            var ly = top + 48f
            HdText.wrap(Component.translatable("generations_core.ultra_warp.session.text", shown.speciesName()).string, textWidth, 7.5f).forEach { line ->
                HdText.draw(graphics, line, tx, ly, 7.5f, LIGHT_TEXT)
                ly += HdText.lineHeight(7.5f)
            }
            val taken = menu.maxSessions - menu.freeSlots
            HdText.draw(graphics, Component.translatable("generations_core.ultra_warp.session.count", taken, menu.maxSessions), tx, ly + 4f, 7.5f, GREY_TEXT)
        }

        // The server's answer to the last click.
        menu.result?.let { result ->
            val text = result.message(menu.variant, menu.maxSessions)
            val colour = if (result.error) RED else GREEN
            var ly = top + HEIGHT - 58f
            HdText.wrap(text.string, WIDTH - 44f, 7.5f, bold = true).take(2).forEach { line ->
                HdText.draw(graphics, line, centre, ly, 7.5f, colour, bold = true, align = HdText.Align.CENTER)
                ly += HdText.lineHeight(7.5f)
            }
        }
    }

    override fun isPauseScreen(): Boolean = false

    private class Theme(val accent: Int, val glow: Int)

    companion object {
        private const val WIDTH = 320
        private const val HEIGHT = 180
        private const val PORTRAIT = 76f
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val LIGHT_TEXT = 0xFFD0D0DA.toInt()
        private const val GREY_TEXT = 0xFFA0A0B0.toInt()
        private const val GREEN = 0xFF4CD964.toInt()
        private const val RED = 0xFFFF5A6E.toInt()
        private val SUN = Theme(0xFFFFB13B.toInt(), 0x99FF9A2E.toInt())
        private val MOON = Theme(0xFF9B8CFF.toInt(), 0x996A5CFF.toInt())

        /** A mode's name (language files), else its id. */
        fun modeName(id: String): Component = Component.translatableWithFallback("generations_core.ultra_warp.mode.$id", id)

        /** Opens the menu, or refreshes it; a journey that ended closes it with a message. */
        fun show(packet: S2CUltraWarpPacket) {
            val minecraft = Minecraft.getInstance()
            val screen = minecraft.screen as? UltraWarpRideScreen
            if (packet.result == UltraWarpRide.Result.ENDED) {
                if (screen != null) minecraft.setScreen(null)
                minecraft.player?.displayClientMessage(Component.translatable(UltraWarpRide.Result.ENDED.key), true)
                return
            }
            if (screen != null) screen.update(packet) else minecraft.setScreen(UltraWarpRideScreen(packet))
        }
    }
}
