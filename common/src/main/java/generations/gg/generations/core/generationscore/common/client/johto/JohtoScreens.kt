package generations.gg.generations.core.generationscore.common.client.johto

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import generations.gg.generations.core.generationscore.common.network.GenerationsNetwork
import generations.gg.generations.core.generationscore.common.network.packets.johto.C2SJohtoConfirmPacket
import generations.gg.generations.core.generationscore.common.network.packets.johto.C2SJohtoSummonPacket
import generations.gg.generations.core.generationscore.common.network.packets.johto.S2CJohtoAltarPacket
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems
import generations.gg.generations.core.generationscore.common.world.johto.JohtoBeast
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

private fun beastName(beast: JohtoBeast): Component = PokemonSpecies.getByName(beast.id)?.translatedName ?: Component.literal(beast.id)

/** A dark panel with a golden frame, centred. */
private fun panel(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, accent: Int) {
    graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xC0000000.toInt())
    graphics.fill(x, y, x + w, y + h, 0xF0121018.toInt())
    graphics.fill(x, y, x + w, y + 1, accent)
    graphics.fill(x, y + h - 1, x + w, y + h, accent)
    graphics.fill(x, y, x + 1, y + h, accent)
    graphics.fill(x + w - 1, y, x + w, y + h, accent)
}

/**
 * Confirming a statue's awakening (JOHTO-PLAN.md § 5): one Sacred Ash spent, the session opened.
 * The server checks everything again on the answer; closing the screen is a no.
 */
class JohtoPromptScreen(private val token: Int, private val beast: JohtoBeast) : Screen(Component.translatable("generations_core.johto.prompt.title", beastName(beast))) {
    private var answered = false

    override fun init() {
        val cx = width / 2
        val cy = height / 2
        addRenderableWidget(Button.builder(Component.translatable("generations_core.johto.prompt.yes")) { answer(true) }.bounds(cx - 104, cy + 34, 100, 20).build())
        addRenderableWidget(Button.builder(Component.translatable("generations_core.johto.prompt.no")) { answer(false) }.bounds(cx + 4, cy + 34, 100, 20).build())
    }

    private fun answer(yes: Boolean) {
        if (answered) return
        answered = true
        GenerationsNetwork.sendToServer(C2SJohtoConfirmPacket(token, yes))
        onClose()
    }

    override fun removed() {
        if (!answered) {
            answered = true
            GenerationsNetwork.sendToServer(C2SJohtoConfirmPacket(token, false))
        }
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val w = 240
        val h = 104
        val x = (width - w) / 2
        val y = height / 2 - 52
        val accent = (0xFF shl 24) or beast.rgb
        panel(graphics, x, y, w, h, accent)
        graphics.renderItem(ItemStack(GenerationsItems.SACRED_ASH.value()), x + 10, y + 10)
        HdText.draw(graphics, title, x + 32f, y + 13f, 10f, accent, bold = true)
        HdText.draw(graphics, Component.translatable("generations_core.johto.prompt.line1"), x + 12f, y + 36f, 8f, 0xFFE6E0D4.toInt())
        HdText.draw(graphics, Component.translatable("generations_core.johto.prompt.line2", beastName(beast)), x + 12f, y + 48f, 8f, 0xFFB8B0A0.toInt())
        // The buttons are drawn by super.render, over the panel: draw them again on top.
        for (child in children()) if (child is Button) child.render(graphics, mouseX, mouseY, partialTick)
    }
}

/**
 * The Bell Tower's altar (JOHTO-PLAN.md § 10): the three places (Thunder, Flames, Rain), lit when
 * this player carries that memory (read by the server, D3: no item goes into the screen), and
 * "SUMMON HO-OH" with its confirmation. The server takes the memories, all or none.
 */
class JohtoAltarScreen(private val token: Int, private val present: Int, private val reason: Int) : Screen(Component.translatable("generations_core.johto.altar.title")) {
    private var confirming = false
    private lateinit var summon: Button
    private lateinit var cancel: Button

    override fun init() {
        val cx = width / 2
        val cy = height / 2
        summon = addRenderableWidget(Button.builder(Component.translatable("generations_core.johto.altar.summon")) { press() }.bounds(cx - 90, cy + 46, 180, 20).build())
        cancel = addRenderableWidget(Button.builder(Component.translatable("generations_core.johto.altar.cancel")) { if (confirming) { confirming = false; refresh() } else onClose() }.bounds(cx - 50, cy + 70, 100, 20).build())
        refresh()
    }

    private fun refresh() {
        summon.active = reason == S2CJohtoAltarPacket.OK
        summon.message = Component.translatable(if (confirming) "generations_core.johto.altar.confirm" else "generations_core.johto.altar.summon")
    }

    private fun press() {
        if (reason != S2CJohtoAltarPacket.OK) return
        if (!confirming) {
            confirming = true
            refresh()
            return
        }
        GenerationsNetwork.sendToServer(C2SJohtoSummonPacket(token))
        onClose()
    }

    override fun isPauseScreen(): Boolean = false

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val w = 280
        val h = 170
        val x = (width - w) / 2
        val y = height / 2 - 80
        val gold = 0xFFE8B84A.toInt()
        panel(graphics, x, y, w, h, gold)
        HdText.draw(graphics, title, x + w / 2f, y + 10f, 11f, gold, bold = true, align = HdText.Align.CENTER)
        HdText.draw(graphics, Component.translatable("generations_core.johto.altar.line"), x + w / 2f, y + 26f, 7.5f, 0xFFB8B0A0.toInt(), align = HdText.Align.CENTER)
        val items = listOf(GenerationsItems.RAIKOU_MEMORY, GenerationsItems.ENTEI_MEMORY, GenerationsItems.SUICUNE_MEMORY)
        for ((i, beast) in JohtoBeast.entries.withIndex()) {
            val sx = x + 40 + i * 80
            val sy = y + 46
            val lit = present and (1 shl i) != 0
            val accent = if (lit) (0xFF shl 24) or beast.rgb else 0xFF3A3640.toInt()
            graphics.fill(sx - 1, sy - 1, sx + 41, sy + 41, accent)
            graphics.fill(sx, sy, sx + 40, sy + 40, if (lit) 0xFF201A12.toInt() else 0xFF15131A.toInt())
            graphics.pose().pushPose()
            graphics.pose().translate((sx + 4).toFloat(), (sy + 4).toFloat(), 0f)
            graphics.pose().scale(2f, 2f, 1f)
            if (lit) graphics.renderItem(ItemStack(items[i].value()), 0, 0) else graphics.renderFakeItem(ItemStack(items[i].value()), 0, 0)
            graphics.pose().popPose()
            if (!lit) graphics.fill(sx, sy, sx + 40, sy + 40, 0xA015131A.toInt())
            HdText.draw(graphics, Component.translatable("generations_core.johto.altar.slot.${beast.id}"), sx + 20f, sy + 46f, 7.5f, accent, bold = lit, align = HdText.Align.CENTER)
        }
        val status = Component.translatable("generations_core.johto.altar.reason.$reason")
        HdText.draw(graphics, status, x + w / 2f, y + 110f, 8f, if (reason == S2CJohtoAltarPacket.OK) 0xFFFFE6A0.toInt() else 0xFFB07070.toInt(), align = HdText.Align.CENTER)
        for (child in children()) if (child is Button) child.render(graphics, mouseX, mouseY, partialTick)
    }
}
