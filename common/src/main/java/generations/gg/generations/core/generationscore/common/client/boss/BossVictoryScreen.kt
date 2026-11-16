package generations.gg.generations.core.generationscore.common.client.boss

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdButton
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdGui
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdModel
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import generations.gg.generations.core.generationscore.common.client.screen.raid.RaidLobbyScreen
import generations.gg.generations.core.generationscore.common.network.packets.boss.S2CBossVictoryPacket
import generations.gg.generations.core.generationscore.common.network.packets.boss.S2CBossVictoryPacket.StoneResult
import generations.gg.generations.core.generationscore.common.world.boss.BossMegas
import generations.gg.generations.core.generationscore.common.world.boss.BossRarity
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents

/**
 * End of a won boss battle: the boss (model, name, rarity), the Mega Stone at stake
 * for a Mega (first victory, luck, missed) and the items won, already in the
 * inventory. In the style of the raid results (HD toolkit). Opened by
 * S2CBossVictoryPacket, a little after Cobblemon's battle screen has closed.
 */
class BossVictoryScreen(private val result: S2CBossVictoryPacket) : Screen(Component.translatable("generations_core.boss.victory.title")) {
    private val species = ResourceLocation.tryParse(result.species)?.let(PokemonSpecies::getByIdentifier)
    private val rarity = BossRarity.byId(result.rarity) ?: BossRarity.COMMON
    private val megaAspect = result.aspects.firstOrNull(BossMegas::isMegaAspect)
    private val name: Component = species?.let { if (megaAspect != null) BossMegas.name(it.translatedName, megaAspect) else it.translatedName } ?: Component.empty()
    private val state = FloatingState()
    private var left = 0f
    private var top = 0f

    override fun init() {
        left = ((width - WIDTH) / 2).toFloat()
        top = ((height - HEIGHT) / 2).toFloat()
        addRenderableWidget(HdButton((left + WIDTH / 2 - 90).toInt(), (top + HEIGHT - 28).toInt(), 180, 20, Component.translatable("generations_core.boss.victory.ok")) {
            onClose()
        })
    }

    override fun added() {
        super.added()
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1f, 0.7f))
    }

    override fun isPauseScreen() = false

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        HdGui.draw(graphics, HdGui.RESULT_BG, left, top, WIDTH.toFloat(), HEIGHT.toFloat())
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val white = RaidLobbyScreen.WHITE
        val dark = RaidLobbyScreen.DARK
        val grey = RaidLobbyScreen.GREY
        val colour = BossClientConfig.settings.color(rarity)

        HdText.draw(graphics, Component.translatable("generations_core.boss.victory.title"), left + 16f, top + 10f, 10f, white, bold = true, shadow = true)

        // The boss.
        if (species != null) {
            HdModel.profile(graphics, RenderablePokemon(species, result.aspects.toSet()), state, left + 9f, top + 27.5f, 150f, 115f, 2.0f, 325f, -8f, partialTick)
            HdText.draw(graphics, name, left + 84f, top + 146f, 11f, white, bold = true, align = HdText.Align.CENTER, shadow = true)
            if (result.level > 0) {
                HdText.draw(graphics, Component.translatable("generations_core.boss.victory.level", result.level), left + 84f, top + 161f, 8f, white, align = HdText.Align.CENTER, shadow = true)
            }
        }

        val rx = left + 178f
        // Rarity, darker so that it reads on the light panel.
        val tier = Component.translatable(rarity.tierKey).let {
            if (result.titan) it.append(" ").append(Component.translatable("generations_core.boss.titan")) else it
        }
        HdText.draw(graphics, tier, rx, top + 24f, 10f, 0xFF000000.toInt() or darker(colour), bold = true)

        // Mega Stone.
        var y = top + 42f
        if (result.stoneResult != StoneResult.NONE) {
            val chance = formatChance(result.stoneChance)
            val (line, lineColour) = when (result.stoneResult) {
                StoneResult.FIRST -> Component.translatable("generations_core.boss.victory.stone.first", result.stone.hoverName) to 0xFF8A2BB8.toInt()
                StoneResult.LUCKY -> Component.translatable("generations_core.boss.victory.stone.lucky", result.stone.hoverName, chance) to 0xFF8A2BB8.toInt()
                StoneResult.MISSED -> Component.translatable("generations_core.boss.victory.stone.missed", result.stone.hoverName, chance) to grey
                else -> Component.translatable("generations_core.boss.victory.stone.none") to grey
            }
            var tx = rx
            if (!result.stone.isEmpty) {
                HdGui.panel(graphics, HdGui.PANEL_LIGHT, rx, y - 3f, 20f, 20f, if (result.stoneResult == StoneResult.MISSED) 0xFFC8C8D0.toInt() else 0xFFE8D8F8.toInt(), corner = 5f)
                graphics.renderItem(result.stone, (rx + 2).toInt(), (y - 1).toInt())
                if (HdGui.isOver(mouseX.toDouble(), mouseY.toDouble(), rx, y - 3f, 20f, 20f)) hoveredStack = result.stone
                tx += 24f
            }
            HdText.draw(graphics, HdText.fit(line.string, WIDTH - (tx - left) - 14f, 8f, true), tx, y + 3f, 8f, lineColour, bold = true)
            y += 26f
        }

        // Items won.
        HdText.draw(graphics, Component.translatable("generations_core.boss.victory.rewards"), rx, y, 9f, dark, bold = true)
        y += 14f
        if (result.rewards.isEmpty()) {
            HdText.draw(graphics, Component.translatable("generations_core.boss.victory.none"), rx, y + 4f, 8f, grey)
        }
        val shown = result.rewards.take(MAX_SHOWN)
        shown.forEachIndexed { i, stack ->
            val x = rx + (i % 8) * 23f
            val sy = y + (i / 8) * 23f
            HdGui.panel(graphics, HdGui.PANEL_LIGHT, x, sy, 20f, 20f, 0xFFE6E9EE.toInt(), corner = 5f)
            graphics.renderItem(stack, (x + 2).toInt(), (sy + 2).toInt())
            graphics.renderItemDecorations(font, stack, (x + 2).toInt(), (sy + 2).toInt())
            if (HdGui.isOver(mouseX.toDouble(), mouseY.toDouble(), x, sy, 20f, 20f)) hoveredStack = stack
        }
        if (result.rewards.size > MAX_SHOWN) {
            HdText.draw(graphics, "+${result.rewards.size - MAX_SHOWN}", rx + 184f, y + 3 * 23f + 2f, 8f, grey, align = HdText.Align.RIGHT)
        }
        if (result.dropped > 0) {
            HdText.draw(graphics, Component.translatable("generations_core.boss.victory.dropped_short", result.dropped), left + WIDTH / 2f, top + HEIGHT - 42f, 7.5f, grey, align = HdText.Align.CENTER)
        }

        hoveredStack?.let { graphics.renderTooltip(font, it, mouseX, mouseY) }
        hoveredStack = null
    }

    private var hoveredStack: net.minecraft.world.item.ItemStack? = null

    private fun darker(rgb: Int): Int {
        val r = ((rgb shr 16) and 0xFF) * 6 / 10
        val g = ((rgb shr 8) and 0xFF) * 6 / 10
        val b = (rgb and 0xFF) * 6 / 10
        return (r shl 16) or (g shl 8) or b
    }

    private fun formatChance(chance: Double): String =
        if (chance == Math.floor(chance)) chance.toInt().toString() else "%.1f".format(chance)

    companion object {
        private const val WIDTH = 380
        private const val HEIGHT = 230
        private const val MAX_SHOWN = 24
    }
}
