package generations.gg.generations.core.generationscore.common.client.rks

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdButton
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdGui
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdModel
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import generations.gg.generations.core.generationscore.common.network.GenerationsNetwork
import generations.gg.generations.core.generationscore.common.network.packets.rks.C2SRksActionPacket
import generations.gg.generations.core.generationscore.common.network.packets.rks.C2SRksSacrificePacket
import generations.gg.generations.core.generationscore.common.network.packets.rks.S2CRksSacrificeListPacket
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksConsoleBlockEntity
import generations.gg.generations.core.generationscore.common.world.recipe.FossilResult
import generations.gg.generations.core.generationscore.common.world.recipe.ItemResult
import generations.gg.generations.core.generationscore.common.world.recipe.PokemonResult
import generations.gg.generations.core.generationscore.common.world.recipe.RksCategory
import generations.gg.generations.core.generationscore.common.world.recipe.RksProtocolRecipe
import generations.gg.generations.core.generationscore.common.world.rks.RksSettings
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksConsoleMenu
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksLayout
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksMachines
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksModule
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeHolder
import java.util.UUID
import kotlin.math.sin

/**
 * The RKS Machine's menu (RKS-PLAN.md § 4.1), in the HD style of the interfaces (client/screen/hd):
 * dark panels, steel and night blue, cyan and violet accents.
 *
 * Left: the three categories, then their recipes. Middle: the result (its Pokémon turning, or the
 * item), its name and description. Right: the ingredients (needed, present, missing: green or
 * red), the state, the button. Below: the machine's slots and the player's inventory.
 * An incomplete machine shows the missing modules on a plan; a damaged one "ERREUR CRITIQUE".
 * Mewtwo's protocol: the energy (the sacrifice's list, sent by the server page by page), the
 * analysis, the research files, the confirmed start. Every click goes to the server with the
 * machine's revision as seen here (double clicks are refused there).
 */
class RksConsoleScreen(menu: RksConsoleMenu, inventory: Inventory, title: Component) : AbstractContainerScreen<RksConsoleMenu>(menu, inventory, title) {
    private var category = RksCategory.GENETIC
    private var selected: ResourceLocation? = null
    private var scroll = 0
    private val state = FloatingState()
    private var openedAt = System.currentTimeMillis()

    // The overlays.
    private var sacrifice: S2CRksSacrificeListPacket? = null
    private val chosen = LinkedHashMap<UUID, S2CRksSacrificeListPacket.Entry>()
    private var confirmSacrifice = false
    private var confirmStart = false

    private lateinit var launch: HdButton
    private lateinit var analyse: HdButton
    private lateinit var start: HdButton
    private lateinit var give: HdButton
    private val overlayButtons = ArrayList<HdButton>()
    private lateinit var previous: HdButton
    private lateinit var next: HdButton
    private lateinit var cancel: HdButton
    private lateinit var confirm: HdButton
    private lateinit var yes: HdButton
    private lateinit var no: HdButton

    init {
        imageWidth = 344
        imageHeight = 244
    }

    private val console: RksConsoleBlockEntity? get() = menu.console

    private fun revision(): Int = console?.machine?.revision ?: 0

    private fun send(action: Int, recipe: String = "", value: Int = revision()) {
        GenerationsNetwork.sendToServer(C2SRksActionPacket(menu.pos, action, recipe, value))
    }

    override fun init() {
        super.init()
        val x = leftPos
        val y = topPos
        launch = addRenderableWidget(HdButton(x + 238, y + 138, 96, 16, Component.translatable("generations_core.rks.button.launch"), 8f) { launchSelected() })
        analyse = addRenderableWidget(HdButton(x + 238, y + 138, 96, 16, Component.translatable("generations_core.rks.button.analyse"), 6.5f) { send(C2SRksActionPacket.ANALYSE) })
        start = addRenderableWidget(HdButton(x + 238, y + 138, 96, 16, Component.translatable("generations_core.rks.button.start"), 6.5f) { confirmStart = true })
        give = addRenderableWidget(HdButton(x + 238, y + 117, 96, 14, Component.translatable("generations_core.rks.button.sacrifice"), 6.5f) {
            chosen.clear()
            send(C2SRksActionPacket.LIST, value = 0)
        })
        val cx = x + imageWidth / 2
        previous = HdButton(x + 30, y + 214, 22, 14, Component.literal("<"), 8f) { sacrifice?.let { send(C2SRksActionPacket.LIST, value = it.page - 1) } }
        next = HdButton(x + 58, y + 214, 22, 14, Component.literal(">"), 8f) { sacrifice?.let { send(C2SRksActionPacket.LIST, value = it.page + 1) } }
        cancel = HdButton(cx + 30, y + 214, 66, 14, Component.translatable("generations_core.rks.button.cancel"), 7f) { closeOverlays() }
        confirm = HdButton(cx + 102, y + 214, 66, 14, Component.translatable("generations_core.rks.button.confirm"), 7f) { if (chosen.isNotEmpty()) confirmSacrifice = true }
        yes = HdButton(cx - 74, y + 150, 70, 15, Component.translatable("generations_core.rks.button.yes"), 7f) { answer(true) }
        no = HdButton(cx + 4, y + 150, 70, 15, Component.translatable("generations_core.rks.button.no"), 7f) { answer(false) }
        // Input only: drawn over the overlay by drawOverlays.
        for (b in listOf(previous, next, cancel, confirm, yes, no)) {
            overlayButtons += b
            addWidget(b)
        }
        // An experiment under way: its recipe at once.
        val c = console
        if (c != null && (c.machine.experiment || c.machine.analysed)) mewtwoRecipe()?.let { selected = it.id(); category = it.value().category }
    }

    private fun closeOverlays() {
        sacrifice = null
        chosen.clear()
        confirmSacrifice = false
        confirmStart = false
    }

    private fun answer(ok: Boolean) {
        if (confirmSacrifice) {
            if (ok) {
                val list = sacrifice
                if (list != null) GenerationsNetwork.sendToServer(C2SRksSacrificePacket(menu.pos, list.revision, chosen.keys.toList()))
                chosen.clear()
            }
            confirmSacrifice = false
            return
        }
        if (confirmStart) {
            if (ok) send(C2SRksActionPacket.START)
            confirmStart = false
        }
    }

    // ---------------------------------------------------------------- recipes

    private fun recipes(): List<RecipeHolder<RksProtocolRecipe>> = minecraft?.level?.let { RksMachines.recipes(it) } ?: emptyList()

    private fun mewtwoRecipe(): RecipeHolder<RksProtocolRecipe>? = recipes().firstOrNull { it.value().isMewtwo }

    /** One entry per recipe, the variants of a group (the Unown blocks) under one. */
    private fun entries(): List<RecipeHolder<RksProtocolRecipe>> {
        val seen = HashSet<String>()
        return recipes().filter { it.value().category == category }.filter { holder ->
            val group = holder.value().group
            group.isEmpty() || seen.add(group)
        }
    }

    private fun selectedRecipe(): RecipeHolder<RksProtocolRecipe>? {
        val id = selected ?: return null
        return recipes().firstOrNull { it.id() == id }
    }

    private fun slots(): List<ItemStack> = (0 until RksConsoleBlockEntity.INPUTS).map { menu.slots[it].item }

    /** The three files in their slots (read from the menu: the client's console has no slots). */
    private fun researchPresent(): Boolean {
        val wanted = console?.researchItems() ?: return false
        return wanted.withIndex().all { (i, item) -> menu.slots[RksConsoleBlockEntity.RESEARCH + i].item.`is`(item) }
    }

    /** For a group, the variant the slots hold (else the first). */
    private fun launchSelected() {
        val holder = selectedRecipe() ?: return
        val group = holder.value().group
        val chosenRecipe = if (group.isEmpty()) holder else recipes().filter { it.value().group == group }.firstOrNull { it.value().allocate(slots()) != null } ?: holder
        send(C2SRksActionPacket.LAUNCH, chosenRecipe.id().toString())
    }

    // ---------------------------------------------------------------- frame

    private fun t(): Float = (System.currentTimeMillis() - openedAt) / 1000f

    override fun containerTick() {
        super.containerTick()
        val c = console
        val state = c?.state ?: RksState.INCOMPLETE
        val recipe = selectedRecipe()?.value()
        val mewtwo = recipe?.isMewtwo == true
        val overlay = sacrifice != null || confirmSacrifice || confirmStart
        val usable = state == RksState.IDLE && !overlay
        launch.visible = !mewtwo && state.assembled && state != RksState.DAMAGED && !overlay
        launch.active = usable && recipe != null && recipe.allocate(slots()) != null
        analyse.visible = mewtwo && state.assembled && state != RksState.DAMAGED && c?.machine?.analysed != true && c?.machine?.experiment != true && !overlay
        analyse.active = usable && c != null && c.energy >= RksSettings.mewtwo.energyRequired && recipe?.allocate(slots()) != null
        start.visible = mewtwo && c?.machine?.analysed == true && !overlay
        start.active = usable && c != null && researchPresent() && recipe?.allocate(slots()) != null
        give.visible = mewtwo && state.assembled && state != RksState.DAMAGED && c?.machine?.experiment != true && c?.machine?.analysed != true && !overlay
        give.active = state.assembled && !state.locksSlots && c != null && c.energy < RksSettings.mewtwo.energyRequired
        val list = sacrifice
        val inList = list != null && !confirmSacrifice
        previous.visible = inList
        next.visible = inList
        previous.active = list != null && list.page > 0
        next.active = list != null && list.page < list.pages - 1
        cancel.visible = inList
        confirm.visible = inList
        confirm.active = chosen.isNotEmpty() && (list?.blocked?.isEmpty() ?: false)
        yes.visible = confirmSacrifice || confirmStart
        no.visible = confirmSacrifice || confirmStart
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        drawOverlays(graphics, mouseX, mouseY)
        if (sacrifice == null && !confirmSacrifice && !confirmStart) renderTooltip(graphics, mouseX, mouseY)
    }

    override fun renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {}

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos.toFloat()
        val y = topPos.toFloat()
        HdGui.panel(graphics, HdGui.PANEL_DARK, x, y, imageWidth.toFloat(), imageHeight.toFloat(), 0xF20C1420.toInt(), corner = 10f)
        HdGui.panel(graphics, HdGui.PANEL_OUTLINE, x, y, imageWidth.toFloat(), imageHeight.toFloat(), 0x8035E0FF.toInt(), corner = 10f)
        val c = console
        val state = c?.state ?: RksState.INCOMPLETE
        HdText.draw(graphics, "RKS MACHINE", x + 10f, y + 5f, 9f, CYAN, bold = true)
        HdText.draw(graphics, stateText(c, state), x + imageWidth - 10f, y + 6f, 7f, stateColour(state), bold = true, align = HdText.Align.RIGHT)
        // The three areas.
        panel(graphics, x + 6f, y + 18f, 78f, 138f)
        panel(graphics, x + 88f, y + 18f, 142f, 138f)
        panel(graphics, x + 234f, y + 18f, 104f, 138f)
        drawCategories(graphics, x, y, mouseX, mouseY, state)
        when {
            !state.assembled -> drawIncomplete(graphics, x, y, c)
            state == RksState.DAMAGED -> drawDamaged(graphics, x, y)
            state == RksState.REPAIRING || state == RksState.ASSEMBLING -> drawWorking(graphics, x, y, c, state)
            else -> {
                drawPreview(graphics, x, y, partialTick)
                drawRight(graphics, x, y, c, state)
            }
        }
        // Slots and labels.
        HdText.draw(graphics, Component.translatable("generations_core.rks.slots"), x + 12f, y + 160f, 6f, MUTED)
        HdText.draw(graphics, Component.translatable("container.inventory"), x + 172f, y + 151f, 6f, MUTED)
        for (slot in menu.slots) {
            if (!slot.isActive) continue
            val output = slot.index == RksConsoleBlockEntity.OUTPUT && slot.container !is Inventory
            HdGui.panel(graphics, HdGui.PANEL_DARK, x + slot.x - 1f, y + slot.y - 1f, 18f, 18f, if (output) 0xFF203040.toInt() else 0xFF161E2A.toInt(), corner = 3f)
        }
        // The arrow and the craft's progress.
        val progress = if (c != null && state == RksState.CRAFTING) ((c.stateSeconds(minecraft!!.level!!.gameTime, partialTick) * 20.0) / c.machine.duration.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) else 0f
        HdGui.bar(graphics, x + 69f, y + 194f, 12f, 4f, 0xFF24303E.toInt())
        if (progress > 0f) HdGui.bar(graphics, x + 69f, y + 194f, 12f * progress, 4f, CYAN)
    }

    private fun panel(graphics: GuiGraphics, x: Float, y: Float, w: Float, h: Float) {
        HdGui.panel(graphics, HdGui.PANEL_DARK, x, y, w, h, 0xF0141F2E.toInt(), corner = 6f)
    }

    private fun stateText(c: RksConsoleBlockEntity?, state: RksState): String {
        val key = "generations_core.rks.state." + state.name.lowercase()
        val level = minecraft?.level ?: return Component.translatable(key).string
        return when (state) {
            RksState.GROWING -> {
                val g = c?.growthNow(level.gameTime) ?: 0.0
                if (g >= 1.0) Component.translatable("generations_core.rks.state.imminent").string
                else Component.translatable(key).string + " %.0f %%".format(g * 100.0)
            }
            else -> Component.translatable(key).string
        }
    }

    private fun stateColour(state: RksState): Int = when (state) {
        RksState.DAMAGED, RksState.AWAKENING -> RED
        RksState.INCOMPLETE -> AMBER
        RksState.GROWING -> VIOLET
        else -> CYAN
    }

    private fun drawCategories(graphics: GuiGraphics, x: Float, y: Float, mx: Int, my: Int, state: RksState) {
        val grey = !state.assembled || state == RksState.DAMAGED
        val icons = mapOf(
            RksCategory.GENETIC to (mewtwoRecipe()?.value()?.result?.stack ?: ItemStack.EMPTY),
            RksCategory.FOSSIL to (recipes().firstOrNull { it.value().category == RksCategory.FOSSIL }?.value()?.result?.stack ?: ItemStack.EMPTY),
            RksCategory.ITEMS to (recipes().firstOrNull { it.value().category == RksCategory.ITEMS }?.value()?.result?.stack ?: ItemStack.EMPTY),
        )
        for ((i, cat) in RksCategory.entries.withIndex()) {
            val cy = y + 21f + i * 17f
            val active = cat == category
            val hover = mx in (x + 9).toInt()..(x + 81).toInt() && my in cy.toInt()..(cy + 15).toInt()
            val tint = when {
                grey -> 0xFF1A2230.toInt()
                active -> 0xFF1E5470.toInt()
                hover -> 0xFF223448.toInt()
                else -> 0xFF18222F.toInt()
            }
            HdGui.panel(graphics, HdGui.PANEL_DARK, x + 9f, cy, 72f, 15f, tint, corner = 4f)
            icons[cat]?.takeIf { !it.isEmpty }?.let { graphics.renderItem(it, (x + 10).toInt(), cy.toInt() - 1) }
            HdText.draw(graphics, Component.translatable(cat.translationKey + ".short"), x + 28f, cy + 4f, 6f, if (grey) MUTED else if (active) 0xFFFFFFFF.toInt() else 0xFFB8C6D6.toInt(), bold = active)
        }
        // The recipes of the category.
        val list = entries()
        val visible = 4
        scroll = scroll.coerceIn(0, maxOf(0, list.size - visible))
        for (k in 0 until visible) {
            val holder = list.getOrNull(scroll + k) ?: break
            val ry = y + 74f + k * 20f
            val active = holder.id() == selected || (holder.value().group.isNotEmpty() && selectedRecipe()?.value()?.group == holder.value().group)
            HdGui.panel(graphics, HdGui.PANEL_DARK, x + 9f, ry, 72f, 18f, if (active) 0xFF2A3F7A.toInt() else 0xFF161F2C.toInt(), corner = 4f)
            graphics.renderItem(holder.value().result.stack, (x + 10).toInt(), (ry + 1).toInt())
            val name = nameOf(holder)
            HdText.draw(graphics, ellipsis(name, 48f, 5.5f), x + 28f, ry + 6f, 5.5f, if (grey) MUTED else 0xFFE6EEF6.toInt())
        }
        if (list.size > visible) {
            val h = 78f
            val bar = h * visible / list.size
            val by = y + 74f + (h - bar) * scroll / (list.size - visible).toFloat()
            HdGui.bar(graphics, x + 82.5f, by, 2f, bar, 0xFF35E0FF.toInt())
        }
    }

    private fun ellipsis(text: String, width: Float, size: Float): String {
        if (HdText.width(text, size) <= width) return text
        var s = text
        while (s.length > 1 && HdText.width("$s…", size) > width) s = s.dropLast(1)
        return "$s…"
    }

    private fun nameOf(holder: RecipeHolder<RksProtocolRecipe>): String {
        val recipe = holder.value()
        if (recipe.group == "unown_blocks") return Component.translatable("generations_core.rks.recipe.unown_blocks").string
        val result = recipe.result
        return when (result) {
            is PokemonResult -> PokemonSpecies.getByIdentifier(result.species)?.translatedName?.string ?: result.species.path
            is FossilResult -> result.species?.let { PokemonSpecies.getByIdentifier(it)?.translatedName?.string } ?: result.stack.hoverName.string
            else -> result.stack.hoverName.string
        }
    }

    private fun drawPreview(graphics: GuiGraphics, x: Float, y: Float, partialTick: Float) {
        val holder = selectedRecipe()
        if (holder == null) {
            HdText.draw(graphics, Component.translatable("generations_core.rks.choose"), x + 159f, y + 80f, 7f, MUTED, align = HdText.Align.CENTER)
            return
        }
        val recipe = holder.value()
        val result = recipe.result
        val species = when (result) {
            is PokemonResult -> PokemonSpecies.getByIdentifier(result.species)
            is FossilResult -> result.species?.let { PokemonSpecies.getByIdentifier(it) }
            else -> null
        }
        // A soft halo behind.
        HdGui.draw(graphics, HdGui.GLOW, x + 109f, y + 20f, 100f, 90f, if (recipe.isMewtwo) 0x60B06BFF else 0x5035E0FF)
        if (species != null) {
            val aspects = (result as? PokemonResult)?.aspects ?: emptySet()
            HdModel.profile(graphics, RenderablePokemon(species, aspects), state, x + 90f, y + 20f, 138f, 92f, 1.5f, 325f + 25f * sin(t() * 0.6f), -6f, partialTick)
        } else {
            val pose = graphics.pose()
            pose.pushPose()
            pose.translate(x + 159f - 24f, y + 40f, 0f)
            pose.scale(3f, 3f, 1f)
            graphics.renderItem(result.stack, 0, 0)
            pose.popPose()
        }
        HdText.draw(graphics, nameOf(holder), x + 159f, y + 113f, 8f, 0xFFFFFFFF.toInt(), bold = true, align = HdText.Align.CENTER)
        val description = if (recipe.description.isNotEmpty()) Component.translatable(recipe.description).string else ""
        var line = 0
        for (text in wrap(description, 132f, 5.5f).take(4)) {
            HdText.draw(graphics, text, x + 159f, y + 124f + line * 7.5f, 5.5f, 0xFFB8C6D6.toInt(), align = HdText.Align.CENTER)
            line++
        }
    }

    private fun wrap(text: String, width: Float, size: Float): List<String> {
        val out = ArrayList<String>()
        var line = ""
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (HdText.width(candidate, size) > width && line.isNotEmpty()) {
                out += line
                line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) out += line
        return out
    }

    private fun drawRight(graphics: GuiGraphics, x: Float, y: Float, c: RksConsoleBlockEntity?, state: RksState) {
        val holder = selectedRecipe() ?: return
        val recipe = holder.value()
        val present = recipe.present(slots())
        HdText.draw(graphics, Component.translatable("generations_core.rks.ingredients"), x + 240f, y + 21f, 6f, CYAN, bold = true)
        val cycle = (t() / 1.2f).toInt()
        for ((i, input) in recipe.inputs.withIndex().take(6)) {
            val ry = y + 30f + i * 11f
            val stacks = input.ingredient.matchingStacks()
            val icon = stacks.getOrNull(if (stacks.isEmpty()) 0 else cycle % stacks.size) ?: ItemStack.EMPTY
            val ok = present[i] >= input.count
            val pose = graphics.pose()
            pose.pushPose()
            pose.translate(x + 240f, ry - 1f, 0f)
            pose.scale(0.62f, 0.62f, 1f)
            graphics.renderItem(icon, 0, 0)
            pose.popPose()
            HdText.draw(graphics, ellipsis(icon.hoverName.string, 56f, 5.5f), x + 252f, ry + 1f, 5.5f, 0xFFDDE6EE.toInt())
            HdText.draw(graphics, "${minOf(present[i], 9999)}/${input.count}", x + 334f, ry + 1f, 5.5f, if (ok) GREEN else RED, bold = true, align = HdText.Align.RIGHT)
        }
        val level = minecraft?.level ?: return
        if (recipe.isMewtwo && c != null) {
            val required = RksSettings.mewtwo.energyRequired
            HdText.draw(graphics, Component.translatable("generations_core.rks.energy", c.energy, required), x + 240f, y + 99f, 5.5f, VIOLET, bold = true)
            HdGui.bar(graphics, x + 240f, y + 108f, 92f, 4f, 0xFF241A36.toInt())
            HdGui.bar(graphics, x + 240f, y + 108f, 92f * (c.energy / required.toFloat()).coerceIn(0f, 1f), 4f, VIOLET)
            if (c.machine.analysed || c.machine.experiment) {
                HdText.draw(graphics, Component.translatable("generations_core.rks.research"), x + 240f, y + 114f, 5f, MUTED)
            }
            when {
                state == RksState.ANALYSING -> HdText.draw(graphics, Component.translatable("generations_core.rks.analysing"), x + 286f, y + 142f, 6f, GREEN, bold = true, align = HdText.Align.CENTER)
                state == RksState.GROWING -> {
                    val g = c.growthNow(level.gameTime)
                    val phase = when {
                        g < 0.2 -> 1
                        g < 0.45 -> 2
                        g < 0.7 -> 3
                        g < 0.95 -> 4
                        g < 1.0 -> 5
                        else -> 6
                    }
                    HdText.draw(graphics, Component.translatable("generations_core.rks.phase.$phase"), x + 286f, y + 134f, 5.5f, VIOLET, bold = true, align = HdText.Align.CENTER)
                    HdGui.bar(graphics, x + 240f, y + 143f, 92f, 5f, 0xFF241A36.toInt())
                    HdGui.bar(graphics, x + 240f, y + 143f, 92f * g.toFloat(), 5f, VIOLET)
                }
                state == RksState.AWAKENING -> HdText.draw(graphics, Component.translatable("generations_core.rks.state.awakening"), x + 286f, y + 142f, 7f, RED, bold = true, align = HdText.Align.CENTER)
            }
        } else if (state == RksState.CRAFTING) {
            HdText.draw(graphics, Component.translatable("generations_core.rks.crafting"), x + 286f, y + 124f, 6f, CYAN, bold = true, align = HdText.Align.CENTER)
        }
        if (!recipe.isMewtwo) HdText.draw(graphics, Component.translatable("generations_core.rks.time", "%.0f".format(recipe.processingTime / 20.0)), x + 240f, y + 99f, 5f, MUTED)
    }

    private fun drawWorking(graphics: GuiGraphics, x: Float, y: Float, c: RksConsoleBlockEntity?, state: RksState) {
        val level = minecraft?.level ?: return
        val seconds = c?.stateSeconds(level.gameTime) ?: 0.0
        val total = if (state == RksState.ASSEMBLING) RksSettings.machine.assemblySeconds else RksSettings.machine.repairSeconds
        val p = (seconds / total.coerceAtLeast(0.1)).toFloat().coerceIn(0f, 1f)
        val key = if (state == RksState.ASSEMBLING) "generations_core.rks.assembling" else "generations_core.rks.repairing"
        HdText.draw(graphics, Component.translatable(key), x + 213f, y + 72f, 9f, CYAN, bold = true, align = HdText.Align.CENTER)
        HdGui.bar(graphics, x + 133f, y + 90f, 160f, 6f, 0xFF1A2A3A.toInt())
        HdGui.bar(graphics, x + 133f, y + 90f, 160f * p, 6f, CYAN)
    }

    private fun drawDamaged(graphics: GuiGraphics, x: Float, y: Float) {
        val flicker = if (sin(t() * 7f) > -0.6f) 1f else 0.6f
        HdGui.panel(graphics, HdGui.PANEL_RED, x + 90f, y + 22f, 246f, 130f, HdGui.withAlpha(0x5A0A0E, 0.9f), corner = 8f)
        HdText.draw(graphics, Component.translatable("generations_core.rks.critical.title"), x + 213f, y + 44f, 10f, HdGui.withAlpha(0xFF3B30, flicker), bold = true, align = HdText.Align.CENTER, shadow = true)
        HdText.draw(graphics, Component.translatable("generations_core.rks.critical.line"), x + 213f, y + 66f, 7f, 0xFFFFD0D0.toInt(), bold = true, align = HdText.Align.CENTER)
        var line = 0
        for (text in wrap(Component.translatable("generations_core.rks.critical.hint").string, 220f, 6f)) {
            HdText.draw(graphics, text, x + 213f, y + 92f + line * 9f, 6f, 0xFFE8C0C0.toInt(), align = HdText.Align.CENTER)
            line++
        }
    }

    /** The plan of the machine seen from above, each module green when in place, red when missing. */
    private fun drawIncomplete(graphics: GuiGraphics, x: Float, y: Float, c: RksConsoleBlockEntity?) {
        val missing = c?.missing ?: RksModule.entries.filter { it != RksModule.CONSOLE }
        HdText.draw(graphics, Component.translatable("generations_core.rks.incomplete"), x + 159f, y + 22f, 7f, AMBER, bold = true, align = HdText.Align.CENTER)
        val cell = 30f
        val ox = x + 159f - cell * 1.5f
        val oy = y + 34f
        for (module in RksModule.entries) {
            // Row z = 2 at the top (the back), the console at the bottom.
            val rx = ox + module.ox * cell
            val ry = oy + (2 - (module.oz + module.d - 1)) * cell
            val w = module.w * cell - 2f
            val h = module.d * cell - 2f
            val ok = module !in missing
            HdGui.panel(graphics, HdGui.PANEL_DARK, rx, ry, w, h, if (ok) 0xFF1E5A3A.toInt() else 0xFF5A1E22.toInt(), corner = 4f)
            HdText.draw(graphics, Component.translatable("generations_core.rks.module.short.${module.name.lowercase()}"), rx + w / 2f, ry + h / 2f - 3f, 5f, if (ok) GREEN else RED, bold = true, align = HdText.Align.CENTER)
        }
        HdText.draw(graphics, Component.translatable("generations_core.rks.front"), x + 159f, y + 128f, 5f, MUTED, align = HdText.Align.CENTER)
        HdText.draw(graphics, Component.translatable("generations_core.rks.missing"), x + 240f, y + 22f, 6f, RED, bold = true)
        var line = 0
        for (module in missing) {
            for (text in wrap(Component.translatable("generations_core.rks.missing.${module.name.lowercase()}").string, 94f, 5.5f)) {
                HdText.draw(graphics, text, x + 240f, y + 33f + line * 7.5f, 5.5f, 0xFFE6EEF6.toInt())
                line++
            }
            line++
        }
        HdText.draw(graphics, Component.translatable("generations_core.rks.hologram_hint"), x + 240f, y + 140f, 4.8f, MUTED)
    }

    // ---------------------------------------------------------------- the overlays

    private fun drawOverlays(graphics: GuiGraphics, mx: Int, my: Int) {
        val list = sacrifice
        val pose = graphics.pose()
        if (list == null && !confirmSacrifice && !confirmStart) return
        pose.pushPose()
        pose.translate(0f, 0f, 400f)
        val x = leftPos.toFloat()
        val y = topPos.toFloat()
        graphics.fill(0, 0, width, height, 0xA0000000.toInt())
        if (list != null && !confirmSacrifice) drawSacrifice(graphics, list, x, y, mx, my)
        if (confirmSacrifice || confirmStart) drawConfirm(graphics, x, y)
        pose.popPose()
        // The overlay's buttons over it.
        for (b in overlayButtons) if (b.visible) {
            pose.pushPose()
            pose.translate(0f, 0f, 500f)
            b.render(graphics, mx, my, 0f)
            pose.popPose()
        }
    }

    private fun drawSacrifice(graphics: GuiGraphics, list: S2CRksSacrificeListPacket, x: Float, y: Float, mx: Int, my: Int) {
        HdGui.panel(graphics, HdGui.PANEL_DARK, x + 8f, y + 8f, imageWidth - 16f, imageHeight - 16f, 0xFA101826.toInt(), corner = 10f)
        HdGui.panel(graphics, HdGui.PANEL_OUTLINE, x + 8f, y + 8f, imageWidth - 16f, imageHeight - 16f, 0xA0B06BFF.toInt(), corner = 10f)
        HdText.draw(graphics, Component.translatable("generations_core.rks.sacrifice.title"), x + 18f, y + 14f, 8f, VIOLET, bold = true)
        HdText.draw(graphics, Component.translatable("generations_core.rks.energy", list.energy, list.required), x + imageWidth - 18f, y + 15f, 6.5f, 0xFFE6EEF6.toInt(), align = HdText.Align.RIGHT)
        if (list.blocked.isNotEmpty()) HdText.draw(graphics, Component.translatable(list.blocked), x + imageWidth / 2f, y + 26f, 6f, RED, bold = true, align = HdText.Align.CENTER)
        if (list.entries.isEmpty()) HdText.draw(graphics, Component.translatable("generations_core.rks.sacrifice.none"), x + imageWidth / 2f, y + 100f, 7f, MUTED, align = HdText.Align.CENTER)
        for ((i, e) in list.entries.withIndex()) {
            val col = i / 9
            val row = i % 9
            val ex = x + 18f + col * 158f
            val ey = y + 36f + row * 19f
            val on = e.uuid in chosen
            val hover = mx >= ex && mx < ex + 152f && my >= ey && my < ey + 17f
            HdGui.panel(graphics, HdGui.PANEL_DARK, ex, ey, 152f, 17f, if (on) 0xFF3A2466.toInt() else if (hover) 0xFF1E2A3C.toInt() else 0xFF151E2B.toInt(), corner = 4f)
            HdGui.panel(graphics, HdGui.PANEL_DARK, ex + 4f, ey + 4f, 9f, 9f, if (on) VIOLET else 0xFF2A3446.toInt(), corner = 2f)
            if (on) HdGui.panel(graphics, HdGui.PANEL_DARK, ex + 6f, ey + 6f, 5f, 5f, 0xFFFFFFFF.toInt(), corner = 1f)
            val species = ResourceLocation.tryParse(e.species)?.let { PokemonSpecies.getByIdentifier(it) }
            val name = e.nickname.ifEmpty { species?.translatedName?.string ?: e.species }
            HdText.draw(graphics, ellipsis(name, 62f, 6f), ex + 17f, ey + 2.5f, 6f, if (e.shiny) 0xFFFFD34D.toInt() else 0xFFE6EEF6.toInt(), bold = e.shiny)
            val place = if (e.place < 0) Component.translatable("generations_core.rks.sacrifice.party").string else Component.translatable("generations_core.rks.sacrifice.box", e.place + 1).string
            HdText.draw(graphics, "N.${e.level}  $place", ex + 17f, ey + 10f, 4.6f, MUTED)
            HdText.draw(graphics, "+${e.points}", ex + 146f, ey + 5f, 7f, VIOLET, bold = true, align = HdText.Align.RIGHT)
            if (e.shiny) HdText.draw(graphics, "*", ex + 120f, ey + 3.5f, 9f, 0xFFFFD34D.toInt(), bold = true, align = HdText.Align.CENTER)
        }
        val total = chosen.values.sumOf { it.points }
        val room = (list.required - list.energy).coerceAtLeast(0)
        val lost = (total - room).coerceAtLeast(0)
        HdText.draw(graphics, "${list.page + 1}/${list.pages}", x + 90f, y + 217f, 6f, MUTED)
        HdText.draw(graphics, Component.translatable("generations_core.rks.sacrifice.total", chosen.size, total), x + 118f, y + 213f, 6f, 0xFFE6EEF6.toInt(), bold = true)
        if (lost > 0) HdText.draw(graphics, Component.translatable("generations_core.rks.sacrifice.over", lost), x + 118f, y + 221f, 5.5f, AMBER)
    }

    private fun drawConfirm(graphics: GuiGraphics, x: Float, y: Float) {
        val cx = x + imageWidth / 2f
        HdGui.panel(graphics, HdGui.PANEL_DARK, cx - 120f, y + 70f, 240f, 104f, 0xFC121A28.toInt(), corner = 10f)
        HdGui.panel(graphics, HdGui.PANEL_OUTLINE, cx - 120f, y + 70f, 240f, 104f, if (confirmStart) 0xC0FF3B30.toInt() else 0xC0B06BFF.toInt(), corner = 10f)
        val title: Component
        val lines = ArrayList<Pair<String, Int>>()
        if (confirmStart) {
            title = Component.translatable("generations_core.rks.confirm.start.title")
            lines += Component.translatable("generations_core.rks.confirm.start.line").string to 0xFFE6EEF6.toInt()
        } else {
            val total = chosen.values.sumOf { it.points }
            title = Component.translatable("generations_core.rks.confirm.sacrifice.title", chosen.size)
            lines += Component.translatable("generations_core.rks.confirm.sacrifice.line", total).string to 0xFFE6EEF6.toInt()
            if (chosen.values.any { it.shiny }) lines += Component.translatable("generations_core.rks.confirm.shiny").string to 0xFFFFD34D.toInt()
        }
        HdText.draw(graphics, title, cx, y + 80f, 8f, if (confirmStart) RED else VIOLET, bold = true, align = HdText.Align.CENTER)
        var row = 0
        for ((text, colour) in lines) for (w in wrap(text, 220f, 6f)) {
            HdText.draw(graphics, w, cx, y + 98f + row * 9f, 6f, colour, align = HdText.Align.CENTER)
            row++
        }
    }

    // ---------------------------------------------------------------- input

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val list = sacrifice
        if (confirmSacrifice || confirmStart) {
            for (b in listOf(yes, no)) if (b.visible && b.isMouseOver(mouseX, mouseY)) return b.mouseClicked(mouseX, mouseY, button)
            return true
        }
        if (list != null) {
            for (b in listOf(previous, next, cancel, confirm)) if (b.visible && b.isMouseOver(mouseX, mouseY)) return b.mouseClicked(mouseX, mouseY, button)
            for ((i, e) in list.entries.withIndex()) {
                val ex = leftPos + 18f + (i / 9) * 158f
                val ey = topPos + 36f + (i % 9) * 19f
                if (mouseX >= ex && mouseX < ex + 152f && mouseY >= ey && mouseY < ey + 17f) {
                    if (chosen.remove(e.uuid) == null) chosen[e.uuid] = e
                    return true
                }
            }
            return true
        }
        val state = console?.state ?: RksState.INCOMPLETE
        val usable = state.assembled && state != RksState.DAMAGED
        val x = leftPos
        val y = topPos
        for ((i, cat) in RksCategory.entries.withIndex()) {
            val cy = y + 21 + i * 17
            if (mouseX >= x + 9 && mouseX < x + 81 && mouseY >= cy && mouseY < cy + 15) {
                if (usable) {
                    category = cat
                    scroll = 0
                }
                return true
            }
        }
        val list2 = entries()
        for (k in 0 until 4) {
            val holder = list2.getOrNull(scroll + k) ?: break
            val ry = y + 74 + k * 20
            if (mouseX >= x + 9 && mouseX < x + 81 && mouseY >= ry && mouseY < ry + 18) {
                if (usable) selected = holder.id()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (sacrifice != null) {
            val list = sacrifice!!
            if (scrollY < 0 && list.page < list.pages - 1) send(C2SRksActionPacket.LIST, value = list.page + 1)
            if (scrollY > 0 && list.page > 0) send(C2SRksActionPacket.LIST, value = list.page - 1)
            return true
        }
        if (mouseX >= leftPos + 6 && mouseX < leftPos + 84 && mouseY >= topPos + 70 && mouseY < topPos + 156) {
            scroll -= scrollY.toInt().coerceIn(-1, 1)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 256 && (sacrifice != null || confirmSacrifice || confirmStart)) {
            if (confirmSacrifice || confirmStart) {
                confirmSacrifice = false
                confirmStart = false
            } else closeOverlays()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    fun onList(packet: S2CRksSacrificeListPacket) {
        sacrifice = packet
        // What was chosen stays chosen across pages, unless it is gone.
        if (packet.page == 0 && packet.total == 0) chosen.clear()
    }

    companion object {
        private val CYAN = 0xFF35E0FF.toInt()
        private val VIOLET = 0xFFB06BFF.toInt()
        private val RED = 0xFFFF3B30.toInt()
        private val GREEN = 0xFF4DFF88.toInt()
        private val AMBER = 0xFFFFB347.toInt()
        private val MUTED = 0xFF7F8EA0.toInt()

        /** The server's page of the sacrifice list, for the screen of that machine. */
        fun receive(packet: S2CRksSacrificeListPacket) {
            val screen = Minecraft.getInstance().screen as? RksConsoleScreen ?: return
            if (screen.menu.pos != packet.pos) return
            screen.onList(packet)
        }
    }
}
