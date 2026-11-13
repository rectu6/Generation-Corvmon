package generations.gg.generations.core.generationscore.common.client.screen.ultraspace

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdButton
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdGui
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpOutcome
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * The end of a journey through a portal, as the server recorded it: the destination
 * (in its colour), the portal's rarity level (its name and four marks), the distance
 * in light-years, the difficulty, the Pokémon ridden, and how the arrival in its
 * world went (phase 4.1): arrived, on a rescue platform, or not (world closed...).
 */
class UltraWarpResultScreen(private val outcome: UltraWarpOutcome) : Screen(Component.translatable("generations_core.ultra_warp.end.title")) {
    private var left = 0f
    private var top = 0f

    override fun init() {
        left = (width - WIDTH) / 2f
        top = (height - HEIGHT) / 2f
        addRenderableWidget(HdButton((left + WIDTH / 2f - 70).toInt(), (top + HEIGHT - 30).toInt(), 140, 20, Component.translatable("generations_core.ultra_warp.end.continue")) { onClose() })
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        HdGui.panel(graphics, HdGui.PANEL_DARK, left, top, WIDTH.toFloat(), HEIGHT.toFloat(), corner = 10f)
        HdGui.panel(graphics, HdGui.PANEL_OUTLINE, left, top, WIDTH.toFloat(), HEIGHT.toFloat(), 0xFF000000.toInt() or outcome.color, corner = 10f)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val centre = left + WIDTH / 2f
        HdText.draw(graphics, title.string.uppercase(), centre, top + 12f, 12f, WHITE, bold = true, align = HdText.Align.CENTER, shadow = true)

        // Destination, in its colour.
        HdText.draw(graphics, Component.translatable("generations_core.ultra_warp.end.destination"), centre, top + 34f, 7.5f, GREY, align = HdText.Align.CENTER)
        HdText.draw(graphics, destinationName(outcome.destination), centre, top + 45f, 13f, 0xFF000000.toInt() or outcome.color, bold = true, align = HdText.Align.CENTER, shadow = true)

        // The portal's level: its name and four marks, lit up to it.
        val level = outcome.rarity.coerceIn(1, 4)
        HdText.draw(graphics, Component.translatable("generations_core.ultra_warp.level.$level"), centre, top + 66f, 9f, LEVEL_COLOURS[level - 1], bold = true, align = HdText.Align.CENTER)
        for (i in 0 until 4) {
            val x = centre - 46f + i * 24f
            val lit = i < level
            HdGui.bar(graphics, x, top + 80f, 20f, 5f, if (lit) LEVEL_COLOURS[i] else 0xFF303040.toInt())
        }

        // Distance, difficulty, Pokémon.
        HdText.draw(graphics, Component.translatable("generations_core.ultra_warp.end.distance", "%.1f".format(outcome.lightYears)), centre, top + 94f, 9f, WHITE, bold = true, align = HdText.Align.CENTER)
        val pokemon = ResourceLocation.tryParse(outcome.pokemon)?.let { PokemonSpecies.getByIdentifier(it)?.translatedName } ?: Component.literal(outcome.pokemon)
        val details = Component.translatable(
            "generations_core.ultra_warp.end.details", UltraWarpRideScreen.modeName(outcome.mode), pokemon,
        )
        HdText.draw(graphics, details, centre, top + 109f, 7.5f, GREY, align = HdText.Align.CENTER)
        val world = destinationName(outcome.destination)
        val arrival = when (outcome.arrival) {
            UltraWarpOutcome.Arrival.ARRIVED -> Component.translatable("generations_core.ultra_warp.end.arrived", world)
            UltraWarpOutcome.Arrival.PLATFORM -> Component.translatable("generations_core.ultra_warp.end.platform", world)
            UltraWarpOutcome.Arrival.UNAVAILABLE -> Component.translatable("generations_core.ultra_warp.end.unavailable", world)
            UltraWarpOutcome.Arrival.FAILED -> Component.translatable("generations_core.ultra_warp.end.failed", world)
            UltraWarpOutcome.Arrival.PENDING -> Component.translatable("generations_core.ultra_warp.end.pending", world)
            UltraWarpOutcome.Arrival.NONE -> Component.translatable("generations_core.ultra_warp.end.later")
        }
        HdText.wrap(arrival.string, WIDTH - 40f, 7f).forEachIndexed { i, line ->
            HdText.draw(graphics, line, centre, top + 124f + i * 9f, 7f, 0xFF9AA8C0.toInt(), align = HdText.Align.CENTER)
        }
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        private const val WIDTH = 300
        private const val HEIGHT = 180
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val GREY = 0xFFA8B4C8.toInt()
        private val LEVEL_COLOURS = intArrayOf(0xFF8CC8FF.toInt(), 0xFFFFC060.toInt(), 0xFFFF8A80.toInt(), 0xFFE080FF.toInt())

        /** A destination's name (language files), else its id. */
        fun destinationName(id: String): Component = Component.translatableWithFallback("generations_core.ultra_warp.destination.$id", id)
    }
}
