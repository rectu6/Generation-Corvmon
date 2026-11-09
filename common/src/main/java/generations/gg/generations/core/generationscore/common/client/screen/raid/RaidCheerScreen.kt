package generations.gg.generations.core.generationscore.common.client.screen.raid

import generations.gg.generations.core.generationscore.common.client.screen.hd.HdButton
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdGui
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import generations.gg.generations.core.generationscore.common.network.packets.raid.C2SRaidCheerPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * The player's Pokémon is down for this turn: they may cheer the team (a random
 * boost, cure or heal for the allies) during a few seconds. Closing it is no cheer.
 */
class RaidCheerScreen(private val previous: Screen?, private val seconds: Int) : Screen(Component.translatable("generations_core.raid.cheer.title")) {
    private val end = System.currentTimeMillis() + seconds * 1000L
    private var answered = false
    private var left = 0f
    private var top = 0f

    override fun init() {
        left = (width - WIDTH) / 2f
        top = height - HEIGHT - 40f
        addRenderableWidget(HdButton((left + 14).toInt(), (top + 46).toInt(), 120, 20, Component.translatable("generations_core.raid.cheer.accept")) { answer(true) })
        addRenderableWidget(HdButton((left + WIDTH - 134).toInt(), (top + 46).toInt(), 120, 20, Component.translatable("generations_core.raid.cheer.decline")) { answer(false) })
    }

    private fun answer(accept: Boolean) {
        if (!answered) C2SRaidCheerPacket(accept).sendToServer()
        answered = true
        Minecraft.getInstance().setScreen(previous)
    }

    override fun tick() {
        super.tick()
        if (System.currentTimeMillis() >= end) answer(false)
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        HdGui.panel(graphics, HdGui.PANEL_DARK, left, top, WIDTH.toFloat(), HEIGHT.toFloat(), corner = 10f)
        HdGui.panel(graphics, HdGui.PANEL_OUTLINE, left, top, WIDTH.toFloat(), HEIGHT.toFloat(), RaidLobbyScreen.RED, corner = 10f)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        HdText.draw(graphics, title, left + WIDTH / 2f, top + 9f, 10f, RaidLobbyScreen.WHITE, bold = true, align = HdText.Align.CENTER)
        HdText.draw(graphics, Component.translatable("generations_core.raid.cheer.text"), left + WIDTH / 2f, top + 24f, 7.5f, 0xFFD0D0DA.toInt(), align = HdText.Align.CENTER)
        val remaining = ((end - System.currentTimeMillis()) / 1000L + 1).coerceAtLeast(0)
        HdText.draw(graphics, "$remaining s", left + WIDTH / 2f, top + 50f, 8f, RaidLobbyScreen.GOLD, bold = true, align = HdText.Align.CENTER)
    }

    override fun onClose() = answer(false)

    override fun isPauseScreen(): Boolean = false

    companion object {
        private const val WIDTH = 280
        private const val HEIGHT = 76
    }
}
