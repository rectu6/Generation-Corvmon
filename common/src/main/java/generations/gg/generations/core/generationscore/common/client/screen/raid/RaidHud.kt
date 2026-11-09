package generations.gg.generations.core.generationscore.common.client.screen.raid

import com.cobblemon.mod.common.client.CobblemonClient
import generations.gg.generations.core.generationscore.common.network.packets.raid.RaidHudMember
import generations.gg.generations.core.generationscore.common.network.packets.raid.S2CRaidHudPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

/**
 * The raid status sent by the server (S2CRaidHudPacket): participants, turn, faints,
 * shield. Drawn by SwshBattleHud, or as a simple panel under the player's battle tile
 * when the Sword and Shield interface is off (BattleOverlayMixin). It belongs to one
 * raid battle: any other battle (a wild Pokémon after the raid, another world) is
 * drawn as a normal battle.
 */
object RaidHud {
    private var hud: S2CRaidHudPacket? = null

    private const val X = 12
    private const val Y = 58
    private const val WIDTH = 168
    private const val ROW = 20

    fun update(packet: S2CRaidHudPacket) {
        hud = packet.takeIf { it.active }
    }

    /** The raid being fought in the current battle, null outside raids. */
    fun current(): S2CRaidHudPacket? {
        val battle = CobblemonClient.battle
        val hud = hud ?: return null
        if (battle == null || hud.battleId != battle.battleId) return null
        return hud
    }

    /** The simple panel, with Cobblemon's own battle tiles. */
    fun render(graphics: GuiGraphics, opacity: Double) {
        val hud = current() ?: return
        val font = Minecraft.getInstance().font
        val alpha = (opacity.coerceIn(0.3, 1.0) * 255).toInt()
        fun argb(rgb: Int, a: Int = alpha) = (a shl 24) or (rgb and 0xFFFFFF)

        val rows = hud.members.size
        val height = 30 + rows * ROW
        graphics.fill(X, Y, X + WIDTH, Y + height, argb(0x14101C, alpha * 3 / 4))
        graphics.fill(X, Y, X + WIDTH, Y + 13, argb(0xC8203A))
        graphics.renderOutline(X, Y, WIDTH, height, argb(0xE8304A))
        graphics.drawString(font, Component.translatable("generations_core.raid.hud.title"), X + 4, Y + 3, argb(0xFFFFFF), false)
        val turn = Component.translatable("generations_core.raid.hud.turn", hud.turn, hud.maxTurns)
        graphics.drawString(font, turn, X + WIDTH - 4 - font.width(turn), Y + 3, argb(0xFFFFFF), false)

        // Fainted Pokémon of the raid: one red orb each, out of the limit.
        graphics.drawString(font, Component.translatable("generations_core.raid.hud.storm"), X + 4, Y + 17, argb(0xD8D8E0), false)
        for (i in 0 until hud.maxFaints) {
            val ox = X + WIDTH - 4 - (hud.maxFaints - i) * 11
            graphics.fill(ox, Y + 17, ox + 8, Y + 25, argb(if (i < hud.faints) 0xE8304A else 0x3A3A48))
        }

        var y = Y + 30
        hud.members.forEach { member ->
            val you = member.kind == RaidHudMember.YOU
            row(graphics, y, member.name, member.label, if (you) null else member.hp, member.maxHp, member.down, argb(if (you) 0xFFD040 else 0xFFFFFF), alpha)
            y += ROW
        }
    }

    private fun row(graphics: GuiGraphics, y: Int, name: String, label: String, hp: Int?, maxHp: Int?, down: Boolean, nameColour: Int, alpha: Int) {
        val font = Minecraft.getInstance().font
        fun argb(rgb: Int) = (alpha shl 24) or (rgb and 0xFFFFFF)
        graphics.drawString(font, name, X + 4, y, nameColour, false)
        val pokemon = font.plainSubstrByWidth(label, WIDTH - 60)
        graphics.drawString(font, pokemon, X + 56, y, argb(if (down) 0x8A8A9A else 0xD8D8E0), false)
        if (down) {
            val ko = Component.translatable("generations_core.raid.hud.down")
            graphics.drawString(font, ko, X + WIDTH - 4 - font.width(ko), y, argb(0xFF5060), false)
        }
        // HP bar of the others' Pokémon.
        if (hp != null && maxHp != null && maxHp > 0) {
            val ratio = Mth.clamp(hp.toFloat() / maxHp, 0f, 1f)
            val colour = when {
                ratio > 0.5f -> 0x48D060
                ratio > 0.2f -> 0xF0C030
                else -> 0xE84040
            }
            graphics.fill(X + 56, y + 10, X + WIDTH - 4, y + 13, argb(0x30303C))
            graphics.fill(X + 56, y + 10, X + 56 + ((WIDTH - 60) * ratio).toInt(), y + 13, argb(colour))
        }
    }
}
