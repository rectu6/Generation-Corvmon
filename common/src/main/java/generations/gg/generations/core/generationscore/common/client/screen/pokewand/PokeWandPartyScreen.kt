package generations.gg.generations.core.generationscore.common.client.screen.pokewand

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import generations.gg.generations.core.generationscore.common.network.packets.pokewand.C2SPokeWandActionPacket
import generations.gg.generations.core.generationscore.common.network.packets.pokewand.S2CPokeWandPartyPacket
import generations.gg.generations.core.generationscore.common.world.pokewand.PokeWandPokemon
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/** Party of the targeted player: a slot opens the editor, empty slots add a Pokémon. */
class PokeWandPartyScreen(private val party: S2CPokeWandPartyPacket) :
    Screen(Component.translatable("generations_core.pokewand.title", party.targetName)) {

    private var left = 0
    private var top = 0

    override fun init() {
        left = (width - WIDTH) / 2
        top = (height - HEIGHT) / 2

        party.slots.forEachIndexed { slot, pokemon ->
            val x = slotX(slot)
            val y = slotY(slot)
            addRenderableWidget(Button.builder(Component.empty()) {
                minecraft?.setScreen(PokeWandEditScreen(party, slot, pokemon))
            }.bounds(x, y, SLOT_WIDTH, SLOT_HEIGHT).build())
            pokemon?.toRenderable()?.let {
                addRenderableOnly(ModelWidget(x + 2, y + 2, SLOT_HEIGHT - 4, SLOT_HEIGHT - 4, it, 1.2f, 325f, -4.0))
            }
        }

        val buttonY = top + HEIGHT - 24
        addRenderableWidget(Button.builder(Component.translatable("generations_core.pokewand.open_pc")) {
            C2SPokeWandActionPacket(party.target, C2SPokeWandActionPacket.Action.OPEN_PC).sendToServer()
        }.bounds(left + 8, buttonY, 92, 18).build())
        addRenderableWidget(Button.builder(Component.translatable("generations_core.pokewand.refresh")) {
            C2SPokeWandActionPacket(party.target, C2SPokeWandActionPacket.Action.REFRESH).sendToServer()
        }.bounds(left + 104, buttonY, 92, 18).build())
        addRenderableWidget(Button.builder(Component.translatable("gui.done")) { onClose() }
            .bounds(left + 200, buttonY, 92, 18).build())
    }

    private fun slotX(slot: Int) = left + 8 + (slot % 2) * (SLOT_WIDTH + 4)
    private fun slotY(slot: Int) = top + 20 + (slot / 2) * (SLOT_HEIGHT + 4)

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL_COLOR)
        graphics.renderOutline(left, top, WIDTH, HEIGHT, OUTLINE_COLOR)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawString(font, title, left + 8, top + 7, 0xFFFFFF)

        party.slots.forEachIndexed { slot, pokemon ->
            val x = slotX(slot) + SLOT_HEIGHT + 2
            val y = slotY(slot)
            if (pokemon == null) {
                graphics.drawString(font, Component.translatable("generations_core.pokewand.empty_slot"), x, y + SLOT_HEIGHT / 2 - 4, 0x9A9AAA)
                return@forEachIndexed
            }
            graphics.drawString(font, displayName(pokemon) + if (pokemon.shiny) " ★" else "", x, y + 8, 0xFFFFFF)
            graphics.drawString(font, Component.translatable("generations_core.pokewand.level", pokemon.level), x, y + 20, 0xC0C0C0)
            graphics.drawString(font, pokemon.moves.size.toString() + " / 4 " + Component.translatable("generations_core.pokewand.moves").string, x, y + 32, 0x9A9AAA)
        }
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        private const val WIDTH = 300
        private const val HEIGHT = 196
        private const val SLOT_WIDTH = 140
        private const val SLOT_HEIGHT = 46
        const val PANEL_COLOR = 0xE8101018.toInt()
        const val OUTLINE_COLOR = 0xFF5A5A70.toInt()

        /** Opens the screen, or refreshes it after an edit. */
        fun show(packet: S2CPokeWandPartyPacket) {
            Minecraft.getInstance().setScreen(PokeWandPartyScreen(packet))
        }

        fun displayName(pokemon: PokeWandPokemon): String = pokemon.nickname.ifBlank {
            ResourceLocation.tryParse(pokemon.species)?.let(PokemonSpecies::getByIdentifier)?.translatedName?.string ?: pokemon.species
        }
    }
}

/** Model of the Pokémon as Cobblemon renders it: form, forced aspects, shininess and gender. */
fun PokeWandPokemon.toRenderable(): RenderablePokemon? {
    val species = ResourceLocation.tryParse(species)?.let(PokemonSpecies::getByIdentifier) ?: return null
    val form = species.forms.firstOrNull { it.formOnlyShowdownId() == this.form } ?: species.standardForm
    val aspects = buildSet {
        addAll(form.aspects)
        addAll(this@toRenderable.aspects)
        if (shiny) add("shiny")
        when (gender) {
            "MALE" -> add("male")
            "FEMALE" -> add("female")
        }
    }
    return RenderablePokemon(species, aspects)
}
