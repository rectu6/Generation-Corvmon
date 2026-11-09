package generations.gg.generations.core.generationscore.common.client.screen.pokewand

import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget
import com.cobblemon.mod.common.pokemon.Species
import generations.gg.generations.core.generationscore.common.network.packets.pokewand.C2SPokeWandActionPacket
import generations.gg.generations.core.generationscore.common.network.packets.pokewand.S2CPokeWandPartyPacket
import generations.gg.generations.core.generationscore.common.world.pokewand.PokeWandPokemon
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * Edits one party slot. An empty field keeps the current value: for a new
 * Pokémon, Cobblemon then generates it (random IVs, nature, moves of its level).
 */
class PokeWandEditScreen(
    private val party: S2CPokeWandPartyPacket,
    private val slot: Int,
    private val original: PokeWandPokemon?,
) : Screen(Component.translatable("generations_core.pokewand.edit_title", slot + 1, party.targetName)) {

    /** Values shown when the widgets are (re)built, e.g. after a window resize. */
    private var shown: PokeWandPokemon = original ?: EMPTY
    private var form = shown.form
    private var shiny = shown.shiny
    private var gender = shown.gender
    private var gmax = shown.gmaxFactor
    private var status: Component? = null
    private var confirmDelete = false

    private var left = 0
    private var top = 0
    private val suggestionFields = mutableListOf<SuggestionField>()
    private lateinit var speciesField: SuggestionField
    private lateinit var natureField: SuggestionField
    private lateinit var abilityField: SuggestionField
    private lateinit var itemField: SuggestionField
    private lateinit var ballField: SuggestionField
    private lateinit var aspectsBox: EditBox
    private lateinit var nicknameBox: EditBox
    private lateinit var levelBox: EditBox
    private lateinit var friendshipBox: EditBox
    private lateinit var formButton: Button
    private lateinit var shinyButton: Button
    private lateinit var genderButton: Button
    private lateinit var gmaxButton: Button
    private lateinit var dynamaxBox: EditBox
    private val moveFields = mutableListOf<SuggestionField>()
    private val ivBoxes = mutableListOf<EditBox>()
    private val evBoxes = mutableListOf<EditBox>()
    private var model: ModelWidget? = null

    override fun init() {
        left = (width - WIDTH) / 2
        top = (height - HEIGHT) / 2
        suggestionFields.clear()
        moveFields.clear()
        ivBoxes.clear()
        evBoxes.clear()
        model = null

        // Left column: preview, shiny, gender, level and friendship.
        shinyButton = addRenderableWidget(Button.builder(Component.empty()) {
            shiny = !shiny
            updateLabels()
            updatePreview()
        }.bounds(left + 8, top + 122, 110, 16).build())
        genderButton = addRenderableWidget(Button.builder(Component.empty()) {
            gender = GENDERS[(GENDERS.indexOf(gender) + 1) % GENDERS.size]
            updateLabels()
            updatePreview()
        }.bounds(left + 8, top + 140, 110, 16).build())
        levelBox = numberBox(left + 8, top + 168, 54, if (shown.level > 0) shown.level.toString() else "", "generations_core.pokewand.level_hint")
        friendshipBox = numberBox(left + 64, top + 168, 54, if (shown.friendship >= 0 && original != null) shown.friendship.toString() else "", "generations_core.pokewand.friendship_hint")
        // Dynamax Level (0-10) and G-Max Factor.
        dynamaxBox = numberBox(left + 8, top + 186, 54, if (shown.dynamaxLevel >= 0 && original != null) shown.dynamaxLevel.toString() else "", "generations_core.pokewand.dynamax_hint")
        dynamaxBox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("generations_core.pokewand.dynamax_hint")))
        gmaxButton = addRenderableWidget(Button.builder(Component.empty()) {
            gmax = !gmax
            updateLabels()
        }.bounds(left + 64, top + 186, 54, 16).build())

        // Middle column: identity of the Pokémon.
        val mx = left + 124
        speciesField = suggestion(mx, top + 20, 140, "generations_core.pokewand.species_hint", shown.species, ::speciesEntries) {
            if (species() == null) return@suggestion
            // A form of the previous species may not exist on the new one.
            if (species()!!.forms.none { f -> f.formOnlyShowdownId() == form }) form = ""
            updateLabels()
            updatePreview()
        }
        formButton = addRenderableWidget(Button.builder(Component.empty()) { cycleForm() }.bounds(mx, top + 38, 140, 16).build())
        aspectsBox = textBox(mx, top + 56, 140, shown.aspects.joinToString(", "), "generations_core.pokewand.aspects_hint")
        aspectsBox.setResponder { updatePreview() }
        natureField = suggestion(mx, top + 74, 140, "generations_core.pokewand.nature_hint", shown.nature, ::natureEntries)
        abilityField = suggestion(mx, top + 92, 140, "generations_core.pokewand.ability_hint", shown.ability, ::abilityEntries)
        itemField = suggestion(mx, top + 110, 140, "generations_core.pokewand.item_hint", shown.heldItem, ::itemEntries)
        ballField = suggestion(mx, top + 128, 140, "generations_core.pokewand.ball_hint", shown.pokeball, ::ballEntries)
        nicknameBox = textBox(mx, top + 146, 140, shown.nickname, "generations_core.pokewand.nickname_hint")

        // Right column: moves, IVs and EVs.
        val rx = left + 270
        repeat(PokeWandPokemon.MAX_MOVES) { index ->
            moveFields += suggestion(rx, top + 20 + index * 18, 132, "generations_core.pokewand.move_hint", shown.moves.getOrElse(index) { "" }, ::moveEntries)
        }
        PokeWandPokemon.STATS.indices.forEach { index ->
            ivBoxes += numberBox(rx + index * 22, top + 106, 21, shown.ivs.getOrNull(index)?.takeIf { it >= 0 && original != null }?.toString() ?: "", null)
            evBoxes += numberBox(rx + index * 22, top + 140, 21, shown.evs.getOrNull(index)?.takeIf { it >= 0 && original != null }?.toString() ?: "", null)
        }

        // Bottom buttons.
        val by = top + HEIGHT - 22
        addRenderableWidget(Button.builder(Component.translatable("generations_core.pokewand.save")) { save() }
            .bounds(left + 124, by, 90, 16).build())
        if (original != null) {
            addRenderableWidget(Button.builder(Component.translatable("generations_core.pokewand.delete")) { button ->
                if (!confirmDelete) {
                    confirmDelete = true
                    button.message = Component.translatable("generations_core.pokewand.delete_confirm")
                } else {
                    C2SPokeWandActionPacket(party.target, C2SPokeWandActionPacket.Action.DELETE, slot).sendToServer()
                }
            }.bounds(left + 218, by, 90, 16).build())
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back")) { onClose() }
            .bounds(left + 312, by, 90, 16).build())

        updateLabels()
        updatePreview()
    }

    // Keeps what was typed when the window is resized (init runs again).
    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        shown = draft()
        super.resize(minecraft, width, height)
    }

    private fun textBox(x: Int, y: Int, width: Int, value: String, hint: String): EditBox =
        addRenderableWidget(EditBox(font, x, y, width, 16, Component.translatable(hint)).also {
            it.setMaxLength(128)
            it.value = value
            it.setHint(Component.translatable(hint))
        })

    private fun numberBox(x: Int, y: Int, width: Int, value: String, hint: String?): EditBox =
        addRenderableWidget(EditBox(font, x, y, width, 16, Component.empty()).also {
            it.setMaxLength(4)
            it.value = value
            it.setFilter { text -> text.all(Char::isDigit) }
            if (hint != null) it.setHint(Component.translatable(hint))
        })

    private fun suggestion(x: Int, y: Int, width: Int, hint: String, value: String, entries: () -> List<Pair<String, String>>, onChange: (String) -> Unit = {}): SuggestionField =
        SuggestionField(font, x, y, width, 16, Component.translatable(hint).string, value, entries, onChange).also {
            addRenderableWidget(it.box)
            suggestionFields += it
        }

    private fun species(): Species? = ResourceLocation.tryParse(speciesField.value)?.let(PokemonSpecies::getByIdentifier)

    private fun cycleForm() {
        val forms = species()?.forms?.takeIf { it.size > 1 } ?: return
        val standard = species()!!.standardForm
        val index = forms.indexOfFirst { if (form.isEmpty()) it == standard else it.formOnlyShowdownId() == form }
        val next = forms[(index + 1) % forms.size]
        form = if (next == standard) "" else next.formOnlyShowdownId()
        updateLabels()
        updatePreview()
    }

    private fun updateLabels() {
        val formName = species()?.forms?.firstOrNull { it.formOnlyShowdownId() == form && form.isNotEmpty() }?.name
            ?: Component.translatable("generations_core.pokewand.form_standard").string
        formButton.message = Component.translatable("generations_core.pokewand.form", formName)
        shinyButton.message = Component.translatable(if (shiny) "generations_core.pokewand.shiny_on" else "generations_core.pokewand.shiny_off")
        genderButton.message = Component.translatable("generations_core.pokewand.gender." + gender.lowercase())
        gmaxButton.message = Component.translatable(if (gmax) "generations_core.pokewand.gmax_on" else "generations_core.pokewand.gmax_off")
    }

    private fun updatePreview() {
        val renderable = draft().toRenderable() ?: return
        val current = model
        if (current != null) current.pokemon = renderable
        else model = addRenderableOnly(ModelWidget(left + 8, top + 20, 110, 98, renderable, 2.0f, 325f, -8.0))
    }

    private fun draft(): PokeWandPokemon = PokeWandPokemon(
        species = speciesField.value,
        form = form,
        aspects = aspectsBox.value.split(',').map(String::trim).filter(String::isNotEmpty),
        shiny = shiny,
        gender = gender,
        level = levelBox.value.toIntOrNull() ?: 0,
        nature = natureField.value,
        ability = abilityField.value,
        moves = moveFields.map { it.value }.filter(String::isNotEmpty),
        ivs = ivBoxes.map { it.value.toIntOrNull() ?: -1 },
        evs = evBoxes.map { it.value.toIntOrNull() ?: -1 },
        heldItem = itemField.value,
        nickname = nicknameBox.value.trim(),
        pokeball = ballField.value,
        friendship = friendshipBox.value.toIntOrNull() ?: -1,
        dynamaxLevel = dynamaxBox.value.toIntOrNull() ?: -1,
        gmaxFactor = gmax,
    )

    private fun save() {
        if (species() == null) {
            status = Component.translatable("generations_core.pokewand.unknown_species")
            return
        }
        C2SPokeWandActionPacket(party.target, C2SPokeWandActionPacket.Action.EDIT, slot, draft()).sendToServer()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Suggestions are drawn over the other widgets: they get the click first.
        if (suggestionFields.any { it.click(mouseX, mouseY) }) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PokeWandPartyScreen.PANEL_COLOR)
        graphics.renderOutline(left, top, WIDTH, HEIGHT, PokeWandPartyScreen.OUTLINE_COLOR)
        graphics.fill(left + 8, top + 20, left + 118, top + 118, 0xFF000000.toInt())
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawString(font, title, left + 8, top + 7, 0xFFFFFF)
        graphics.drawString(font, Component.translatable("generations_core.pokewand.level_friendship"), left + 8, top + 158, 0xA0A0B0)
        graphics.drawString(font, Component.translatable("generations_core.pokewand.ivs"), left + 270, top + 94, 0xA0A0B0)
        graphics.drawString(font, Component.translatable("generations_core.pokewand.evs"), left + 270, top + 128, 0xA0A0B0)
        STAT_LABELS.forEachIndexed { index, key ->
            val label = Component.translatable(key)
            graphics.drawString(font, label, left + 270 + index * 22 + (21 - font.width(label)) / 2, top + 158, 0x707080)
        }
        status?.let { graphics.drawString(font, it, left + 124, top + HEIGHT - 34, 0xFF6060) }
        suggestionFields.forEach { it.render(graphics, mouseX, mouseY) }
    }

    override fun onClose() {
        minecraft?.setScreen(PokeWandPartyScreen(party))
    }

    override fun isPauseScreen(): Boolean = false

    private fun speciesEntries() = SPECIES_ENTRIES
    private fun natureEntries() = Natures.all().map { it.name.toString() to Component.translatable(it.displayName).string }
    private fun abilityEntries() = Abilities.all().map { it.name to Component.translatable(it.displayName).string }
    private fun moveEntries() = Moves.all().map { it.name to it.displayName.string }
    private fun itemEntries() = ITEM_ENTRIES
    private fun ballEntries() = PokeBalls.all().map { it.name.toString() to it.name.path }

    companion object {
        private const val WIDTH = 410
        private const val HEIGHT = 228
        private val GENDERS = listOf("MALE", "FEMALE", "GENDERLESS")
        private val STAT_LABELS = listOf(
            "generations_core.pokewand.stat.hp", "generations_core.pokewand.stat.atk", "generations_core.pokewand.stat.def",
            "generations_core.pokewand.stat.spa", "generations_core.pokewand.stat.spd", "generations_core.pokewand.stat.spe",
        )
        private val EMPTY = PokeWandPokemon(
            species = "", form = "", aspects = emptyList(), shiny = false, gender = "MALE", level = 0,
            nature = "", ability = "", moves = emptyList(), ivs = List(6) { -1 }, evs = List(6) { -1 },
            heldItem = "", nickname = "", pokeball = "cobblemon:poke_ball", friendship = -1,
        )

        // Species can change with the server's data: read them each time.
        private val SPECIES_ENTRIES: List<Pair<String, String>>
            get() = PokemonSpecies.species.map { it.resourceIdentifier.toString() to it.translatedName.string }

        // Thousands of items, fixed for the game session: built once.
        private val ITEM_ENTRIES by lazy {
            BuiltInRegistries.ITEM.keySet().map { it.toString() to Component.translatable(BuiltInRegistries.ITEM.get(it).descriptionId).string }
        }
    }
}
