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
import generations.gg.generations.core.generationscore.common.world.recipe.PokemonResult
import generations.gg.generations.core.generationscore.common.world.recipe.RksCategory
import generations.gg.generations.core.generationscore.common.world.recipe.RksProtocolRecipe
import generations.gg.generations.core.generationscore.common.world.rks.RksEnergy
import generations.gg.generations.core.generationscore.common.world.rks.RksSettings
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksConsoleMenu
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksMachines
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksModule
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeHolder
import java.util.EnumMap
import java.util.UUID
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * The RKS Machine's menu (RKS-PLAN.md § 4.1, HOOH-PLAN.md § 3), in the HD style of the interfaces
 * (client/screen/hd): dark panels, steel and night blue, cyan, violet and fire accents.
 *
 * Top: the title and the machine's state. Left: the three categories. Middle: the category's
 * recipes in a scrolling list (smooth wheel, a thumb to drag, a track to click, arrow keys; clipped,
 * nothing overflows). Right: the selected recipe's sheet (result, name, description, ingredients
 * needed / present / missing in green or red, extra conditions such as an energy reserve, the
 * button). Below: the machine's slots and the player's inventory.
 *
 * Each category keeps its own scroll and selection, also when the menu is opened again during the
 * game (D10). The recipes are read from the data (RksMachines.recipes). Every click goes to the
 * server with the machine's revision as seen here (double clicks are refused there).
 */
class RksConsoleScreen(menu: RksConsoleMenu, inventory: Inventory, title: Component) : AbstractContainerScreen<RksConsoleMenu>(menu, inventory, title) {
    private var category = RksCategory.GENETIC
    private var selected: ResourceLocation? = null
    private var scrollTarget = 0f
    private var scrollPos = 0f
    private var dragging = false
    private var dragOffset = 0f
    private var ingredientScroll = 0
    private var lastFrame = System.nanoTime()
    private val modelState = FloatingState()
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
        imageWidth = W
        imageHeight = H
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
        val by = y + BUTTON_Y
        launch = addRenderableWidget(HdButton(x + DETAIL_X + 78, by, 96, 14, Component.translatable("generations_core.rks.button.launch"), 7f) { launchSelected() })
        analyse = addRenderableWidget(HdButton(x + DETAIL_X + 78, by, 96, 14, Component.translatable("generations_core.rks.button.analyse"), 5.5f) { send(C2SRksActionPacket.ANALYSE) })
        start = addRenderableWidget(HdButton(x + DETAIL_X + 78, by, 96, 14, Component.translatable("generations_core.rks.button.start"), 5.5f) { confirmStart = true })
        give = addRenderableWidget(HdButton(x + DETAIL_X + 4, by, 70, 14, Component.translatable("generations_core.rks.button.sacrifice"), 6f) {
            val kind = sacrificeKind() ?: return@HdButton
            chosen.clear()
            send(C2SRksActionPacket.LIST, recipe = kind.id, value = 0)
        })
        val cx = x + imageWidth / 2
        previous = HdButton(x + 30, y + 222, 22, 14, Component.literal("<"), 8f) { sacrifice?.let { send(C2SRksActionPacket.LIST, recipe = kindOf(it).id, value = it.page - 1) } }
        next = HdButton(x + 58, y + 222, 22, 14, Component.literal(">"), 8f) { sacrifice?.let { send(C2SRksActionPacket.LIST, recipe = kindOf(it).id, value = it.page + 1) } }
        cancel = HdButton(cx + 52, y + 222, 66, 14, Component.translatable("generations_core.rks.button.cancel"), 7f) { closeOverlays() }
        confirm = HdButton(cx + 124, y + 222, 66, 14, Component.translatable("generations_core.rks.button.confirm"), 7f) { if (chosen.isNotEmpty()) confirmSacrifice = true }
        yes = HdButton(cx - 74, y + 154, 70, 15, Component.translatable("generations_core.rks.button.yes"), 7f) { answer(true) }
        no = HdButton(cx + 4, y + 154, 70, 15, Component.translatable("generations_core.rks.button.no"), 7f) { answer(false) }
        // Input only: drawn over the overlay by drawOverlays.
        for (b in listOf(previous, next, cancel, confirm, yes, no)) {
            overlayButtons += b
            addWidget(b)
        }
        // The last category and its place, as left (D10).
        category = lastCategory
        restore(category)
        // An experiment under way: its recipe at once.
        val c = console
        if (c != null && (c.machine.experiment || c.machine.analysed)) mewtwoRecipe()?.let {
            category = it.value().category
            restore(category)
            selected = it.id()
        }
        if (selected == null || entries().none { matches(it, selected) }) selected = entries().firstOrNull()?.id()
        ensureVisible()
        scrollPos = scrollTarget
    }

    override fun removed() {
        remember()
        super.removed()
    }

    private fun remember() {
        memory[category] = Memory(scrollTarget, selected)
        lastCategory = category
    }

    private fun restore(cat: RksCategory) {
        val m = memory[cat]
        scrollTarget = m?.scroll ?: 0f
        scrollPos = scrollTarget
        selected = m?.selected
        ingredientScroll = 0
    }

    private fun switchTo(cat: RksCategory) {
        if (cat == category) return
        remember()
        category = cat
        restore(cat)
        if (selected == null || entries().none { matches(it, selected) }) selected = entries().firstOrNull()?.id()
        clampScroll()
        scrollPos = scrollTarget
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
                if (list != null) GenerationsNetwork.sendToServer(C2SRksSacrificePacket(menu.pos, list.kind, list.revision, chosen.keys.toList()))
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
    private fun entries(cat: RksCategory = category): List<RecipeHolder<RksProtocolRecipe>> {
        val seen = HashSet<String>()
        return recipes().filter { it.value().category == cat }.filter { holder ->
            val group = holder.value().group
            group.isEmpty() || seen.add(group)
        }
    }

    /** That entry is the selection (or the selection is one of its group's variants). */
    private fun matches(holder: RecipeHolder<RksProtocolRecipe>, id: ResourceLocation?): Boolean {
        if (id == null) return false
        if (holder.id() == id) return true
        val group = holder.value().group
        return group.isNotEmpty() && recipes().firstOrNull { it.id() == id }?.value()?.group == group
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

    /** The variant of a group the slots hold (else the first), or the recipe itself. */
    private fun variant(holder: RecipeHolder<RksProtocolRecipe>): RecipeHolder<RksProtocolRecipe> {
        val group = holder.value().group
        if (group.isEmpty()) return holder
        val slots = slots()
        return recipes().filter { it.value().group == group }.firstOrNull { it.value().allocate(slots) != null } ?: holder
    }

    private fun energyMet(recipe: RksProtocolRecipe): Boolean {
        val kind = recipe.energyKind ?: return true
        val c = console ?: return false
        return c.energyOf(kind) >= kind.required
    }

    /** What the slots and the reserves allow now (the machine's state apart). */
    private fun craftable(holder: RecipeHolder<RksProtocolRecipe>): Boolean {
        val recipe = variant(holder).value()
        if (recipe.isMewtwo) return false
        return recipe.allocate(slots()) != null && energyMet(recipe)
    }

    private fun launchSelected() {
        val holder = selectedRecipe() ?: return
        send(C2SRksActionPacket.LAUNCH, variant(holder).id().toString())
    }

    /** The reserve the selected recipe's sacrifice button fills. */
    private fun sacrificeKind(): RksEnergy? {
        val recipe = selectedRecipe()?.value() ?: return null
        return if (recipe.isMewtwo) RksEnergy.PSYCHIC else recipe.energyKind
    }

    private fun kindOf(list: S2CRksSacrificeListPacket): RksEnergy = RksEnergy.entries.getOrNull(list.kind) ?: RksEnergy.PSYCHIC

    private fun machineUsable(state: RksState): Boolean = state.assembled && state != RksState.DAMAGED

    // ---------------------------------------------------------------- scrolling

    private fun contentHeight(): Float = (entries().size * PITCH - (PITCH - ROW_H)).toFloat().coerceAtLeast(0f)

    private fun maxScroll(): Float = (contentHeight() - LIST_H).coerceAtLeast(0f)

    private fun clampScroll() {
        scrollTarget = scrollTarget.coerceIn(0f, maxScroll())
    }

    /** The selection fully in view. */
    private fun ensureVisible() {
        val index = entries().indexOfFirst { matches(it, selected) }
        if (index < 0) {
            clampScroll()
            return
        }
        val top = (index * PITCH).toFloat()
        val bottom = top + ROW_H
        if (top < scrollTarget) scrollTarget = top
        if (bottom > scrollTarget + LIST_H) scrollTarget = bottom - LIST_H
        clampScroll()
    }

    private fun thumb(): FloatArray? {
        val max = maxScroll()
        if (max <= 0f) return null
        val h = (LIST_H * LIST_H / contentHeight()).coerceIn(14f, LIST_H.toFloat())
        val y = topPos + LIST_Y + (LIST_H - h) * (scrollPos / max)
        return floatArrayOf(y, h)
    }

    private fun animate() {
        val now = System.nanoTime()
        val dt = ((now - lastFrame) / 1e9f).coerceIn(0f, 0.1f)
        lastFrame = now
        clampScroll()
        if (dragging) scrollPos = scrollTarget
        else {
            scrollPos += (scrollTarget - scrollPos) * (1f - exp(-dt * 16f))
            if (kotlin.math.abs(scrollTarget - scrollPos) < 0.25f) scrollPos = scrollTarget
        }
    }

    // ---------------------------------------------------------------- frame

    private fun t(): Float = (System.currentTimeMillis() - openedAt) / 1000f

    override fun containerTick() {
        super.containerTick()
        val c = console
        val state = c?.state ?: RksState.INCOMPLETE
        val holder = selectedRecipe()
        val recipe = holder?.value()
        val mewtwo = recipe?.isMewtwo == true
        val kind = sacrificeKind()
        val overlay = sacrifice != null || confirmSacrifice || confirmStart
        val usable = state == RksState.IDLE && !overlay
        val working = machineUsable(state)
        launch.visible = recipe != null && !mewtwo && working && !overlay
        launch.active = usable && holder != null && craftable(holder)
        launch.message = Component.translatable(
            when (category) {
                RksCategory.ITEMS -> "generations_core.rks.button.craft"
                RksCategory.FOSSIL -> "generations_core.rks.button.revive"
                else -> "generations_core.rks.button.launch"
            },
        )
        analyse.visible = mewtwo && working && c?.machine?.analysed != true && c?.machine?.experiment != true && !overlay
        analyse.active = usable && c != null && c.energy >= RksSettings.mewtwo.energyRequired && recipe?.allocate(slots()) != null
        start.visible = mewtwo && c?.machine?.analysed == true && !overlay
        start.active = usable && c != null && researchPresent() && recipe?.allocate(slots()) != null
        give.visible = kind != null && working && c?.machine?.experiment != true && !overlay && !(mewtwo && c?.machine?.analysed == true)
        give.active = kind != null && working && !state.locksSlots && c != null && c.energyOf(kind) < kind.required
        give.message = Component.translatable(if (kind != null) kind.key("generations_core.rks.button.sacrifice") else "generations_core.rks.button.sacrifice")
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
        animate()
        super.render(graphics, mouseX, mouseY, partialTick)
        drawOverlays(graphics, mouseX, mouseY)
        if (sacrifice == null && !confirmSacrifice && !confirmStart) {
            renderTooltip(graphics, mouseX, mouseY)
            drawHoverTips(graphics, mouseX, mouseY)
        }
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
        drawStatus(graphics, x, y, c, state)
        // The three areas.
        panel(graphics, x + CAT_X, y + PANEL_Y, CAT_W.toFloat(), PANEL_H.toFloat())
        panel(graphics, x + LIST_PANEL_X, y + PANEL_Y, LIST_PANEL_W.toFloat(), PANEL_H.toFloat())
        panel(graphics, x + DETAIL_X, y + PANEL_Y, DETAIL_W.toFloat(), PANEL_H.toFloat())
        drawCategories(graphics, x, y, mouseX, mouseY, state)
        when {
            !state.assembled -> drawIncomplete(graphics, x, y, c)
            state == RksState.DAMAGED -> drawDamaged(graphics, x, y)
            state == RksState.REPAIRING || state == RksState.ASSEMBLING -> drawWorking(graphics, x, y, c, state)
            else -> {
                drawList(graphics, x, y, mouseX, mouseY, state)
                drawDetail(graphics, x, y, c, state, partialTick)
            }
        }
        // Slots and labels.
        HdText.draw(graphics, Component.translatable("generations_core.rks.slots"), x + RksConsoleMenu.INPUT_X, y + 160f, 6f, MUTED)
        HdText.draw(graphics, Component.translatable("container.inventory"), x + RksConsoleMenu.PLAYER_X, y + 156f, 6f, MUTED)
        if (menu.slots[RksConsoleBlockEntity.RESEARCH].isActive) {
            HdText.draw(graphics, Component.translatable("generations_core.rks.files"), x + RksConsoleMenu.RESEARCH_X, y + RksConsoleMenu.RESEARCH_Y - 9f, 5f, VIOLET)
        }
        for (slot in menu.slots) {
            if (!slot.isActive) continue
            val output = slot.index == RksConsoleBlockEntity.OUTPUT && slot.container !is Inventory
            HdGui.panel(graphics, HdGui.PANEL_DARK, x + slot.x - 1f, y + slot.y - 1f, 18f, 18f, if (output) 0xFF203040.toInt() else 0xFF161E2A.toInt(), corner = 3f)
        }
        // The arrow and the craft's progress.
        val progress = if (c != null && state == RksState.CRAFTING) ((c.stateSeconds(minecraft!!.level!!.gameTime, partialTick) * 20.0) / c.machine.duration.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) else 0f
        val ax = x + RksConsoleMenu.INPUT_X + 56f
        val ay = y + RksConsoleMenu.OUTPUT_Y + 6f
        HdGui.bar(graphics, ax, ay, 14f, 4f, 0xFF24303E.toInt())
        if (progress > 0f) HdGui.bar(graphics, ax, ay, 14f * progress, 4f, CYAN)
    }

    private fun panel(graphics: GuiGraphics, x: Float, y: Float, w: Float, h: Float) {
        HdGui.panel(graphics, HdGui.PANEL_DARK, x, y, w, h, 0xF0141F2E.toInt(), corner = 6f)
    }

    /** The top right: the state, and the reserves when they hold something. */
    private fun drawStatus(graphics: GuiGraphics, x: Float, y: Float, c: RksConsoleBlockEntity?, state: RksState) {
        var right = x + imageWidth - 10f
        val text = stateText(c, state)
        HdText.draw(graphics, text, right, y + 6f, 7f, stateColour(state), bold = true, align = HdText.Align.RIGHT)
        right -= HdText.width(text, 7f, true) + 10f
        if (c == null || !state.assembled) return
        if (c.fireEnergy > 0) {
            val s = Component.translatable("generations_core.rks.short.fire", c.fireEnergy, RksEnergy.FIRE.required).string
            HdText.draw(graphics, s, right, y + 7f, 5.5f, FIRE, bold = true, align = HdText.Align.RIGHT)
            right -= HdText.width(s, 5.5f, true) + 8f
        }
        if (c.energy > 0) {
            val s = Component.translatable("generations_core.rks.short.psychic", c.energy, RksEnergy.PSYCHIC.required).string
            HdText.draw(graphics, s, right, y + 7f, 5.5f, VIOLET, bold = true, align = HdText.Align.RIGHT)
        }
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

    private fun categoryIcon(cat: RksCategory): ItemStack = when (cat) {
        RksCategory.GENETIC -> mewtwoRecipe()?.value()?.result?.stack
        else -> recipes().firstOrNull { it.value().category == cat }?.value()?.result?.stack
    } ?: ItemStack.EMPTY

    private fun drawCategories(graphics: GuiGraphics, x: Float, y: Float, mx: Int, my: Int, state: RksState) {
        val grey = !machineUsable(state)
        for ((i, cat) in RksCategory.entries.withIndex()) {
            val tx = x + CAT_X + 3f
            val ty = y + PANEL_Y + 3f + i * (TAB_H + 4f)
            val active = cat == category
            val hover = mx >= tx && mx < tx + TAB_W && my >= ty && my < ty + TAB_H
            val tint = when {
                grey -> 0xFF1A2230.toInt()
                active -> 0xFF1E5470.toInt()
                hover -> 0xFF223448.toInt()
                else -> 0xFF18222F.toInt()
            }
            HdGui.panel(graphics, HdGui.PANEL_DARK, tx, ty, TAB_W.toFloat(), TAB_H.toFloat(), tint, corner = 5f)
            if (active && !grey) HdGui.bar(graphics, tx, ty + 4f, 2f, TAB_H - 8f, CYAN)
            categoryIcon(cat).takeIf { !it.isEmpty }?.let { graphics.renderItem(it, (tx + 5).toInt(), (ty + 4).toInt()) }
            val count = entries(cat).size
            HdText.draw(graphics, count.toString(), tx + TAB_W - 5f, ty + 6f, 6f, if (grey) MUTED else if (active) CYAN else 0xFF8FA3B8.toInt(), bold = true, align = HdText.Align.RIGHT)
            val colour = if (grey) MUTED else if (active) 0xFFFFFFFF.toInt() else 0xFFB8C6D6.toInt()
            HdText.draw(graphics, HdText.fit(Component.translatable(cat.translationKey + ".short").string, TAB_W - 10f, 6.5f, true), tx + 6f, ty + 24f, 6.5f, colour, bold = active)
        }
    }

    private fun drawList(graphics: GuiGraphics, x: Float, y: Float, mx: Int, my: Int, state: RksState) {
        val list = entries()
        val lx = x + LIST_X
        val ly = y + LIST_Y
        if (list.isEmpty()) {
            for ((i, line) in HdText.wrap(Component.translatable("generations_core.rks.empty_category").string, LIST_W - 8f, 5.5f).withIndex()) {
                HdText.draw(graphics, line, lx + LIST_W / 2f, ly + 50f + i * 8f, 5.5f, MUTED, align = HdText.Align.CENTER)
            }
            return
        }
        val idle = state == RksState.IDLE
        val first = floor(scrollPos / PITCH).toInt().coerceAtLeast(0)
        val last = ((scrollPos + LIST_H) / PITCH).toInt().coerceAtMost(list.size - 1)
        graphics.enableScissor(lx.toInt(), ly.toInt(), (lx + LIST_W).toInt(), (ly + LIST_H).toInt())
        val inside = mx >= lx && mx < lx + LIST_W && my >= ly && my < ly + LIST_H && !dragging
        for (k in first..last) {
            val holder = list[k]
            val ry = ly + k * PITCH - scrollPos
            val active = matches(holder, selected)
            val hover = inside && my >= ry && my < ry + ROW_H
            val tint = when {
                active -> 0xFF2A3F7A.toInt()
                hover -> 0xFF1E2A3C.toInt()
                else -> 0xFF161F2C.toInt()
            }
            HdGui.panel(graphics, HdGui.PANEL_DARK, lx, ry, LIST_W.toFloat(), ROW_H.toFloat(), tint, corner = 4f)
            if (active) HdGui.bar(graphics, lx, ry + 3f, 2f, ROW_H - 6f, CYAN)
            graphics.renderItem(variant(holder).value().result.stack, (lx + 3).toInt(), (ry + 1).toInt())
            val name = nameOf(holder)
            HdText.draw(graphics, HdText.fit(name, LIST_W - 34f, 5.5f), lx + 22f, ry + 6.5f, 5.5f, if (active) 0xFFFFFFFF.toInt() else 0xFFDCE6F0.toInt(), bold = active)
            // A dot: the slots and reserves allow it now.
            val ready = idle && craftable(holder)
            val dot = when {
                holder.value().isMewtwo -> if (holder.value().allocate(slots()) != null) VIOLET else 0xFF2A3446.toInt()
                ready -> GREEN
                else -> 0xFF2A3446.toInt()
            }
            HdGui.panel(graphics, HdGui.PANEL_DARK, lx + LIST_W - 8f, ry + 7f, 4f, 4f, dot, corner = 2f)
            holder.value().energyKind?.let { HdGui.panel(graphics, HdGui.PANEL_DARK, lx + LIST_W - 14f, ry + 7f, 4f, 4f, if (it == RksEnergy.FIRE) FIRE else VIOLET, corner = 2f) }
        }
        graphics.disableScissor()
        // Fades at the cut edges.
        if (scrollPos > 0.5f) HdGui.bar(graphics, lx, ly, LIST_W.toFloat(), 1f, 0x6035E0FF)
        if (scrollPos < maxScroll() - 0.5f) HdGui.bar(graphics, lx, ly + LIST_H - 1f, LIST_W.toFloat(), 1f, 0x6035E0FF)
        // The scroll bar: the track and its thumb.
        val sx = x + SCROLL_X
        HdGui.panel(graphics, HdGui.PANEL_DARK, sx, ly, SCROLL_W.toFloat(), LIST_H.toFloat(), 0xFF101822.toInt(), corner = 2f)
        thumb()?.let { (ty, th) ->
            val over = mx >= sx - 1 && mx < sx + SCROLL_W + 1 && my >= ty && my < ty + th
            HdGui.panel(graphics, HdGui.PANEL_DARK, sx, ty, SCROLL_W.toFloat(), th, if (dragging || over) 0xFF7FF0FF.toInt() else CYAN, corner = 2f)
        }
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

    // ---------------------------------------------------------------- the sheet

    private fun drawDetail(graphics: GuiGraphics, x: Float, y: Float, c: RksConsoleBlockEntity?, state: RksState, partialTick: Float) {
        val dx = x + DETAIL_X
        val dy = y + PANEL_Y
        val entry = selectedRecipe()
        if (entry == null) {
            HdText.draw(graphics, Component.translatable("generations_core.rks.choose"), dx + DETAIL_W / 2f, dy + 60f, 7f, MUTED, align = HdText.Align.CENTER)
            return
        }
        val holder = variant(entry)
        val recipe = holder.value()
        val result = recipe.result
        // The result: its Pokémon turning, or the item.
        val bx = dx + 4f
        val by = dy + 3f
        HdGui.panel(graphics, HdGui.PANEL_DARK, bx, by, 50f, 50f, 0xFF0F1824.toInt(), corner = 5f)
        HdGui.draw(graphics, HdGui.GLOW, bx - 8f, by - 8f, 66f, 66f, if (recipe.isMewtwo) 0x60B06BFF else if (recipe.energyKind == RksEnergy.FIRE) 0x60FF7A1E else 0x4835E0FF)
        val species = when (result) {
            is PokemonResult -> PokemonSpecies.getByIdentifier(result.species)
            is FossilResult -> result.species?.let { PokemonSpecies.getByIdentifier(it) }
            else -> null
        }
        if (species != null) {
            graphics.enableScissor(bx.toInt(), by.toInt(), (bx + 50).toInt(), (by + 50).toInt())
            val aspects = (result as? PokemonResult)?.aspects ?: emptySet()
            HdModel.profile(graphics, RenderablePokemon(species, aspects), modelState, bx, by, 50f, 50f, 0.62f, 325f + 25f * sin(t() * 0.6f), -2f, partialTick)
            graphics.disableScissor()
        } else {
            val pose = graphics.pose()
            pose.pushPose()
            pose.translate(bx + 25f - 20f, by + 25f - 20f, 0f)
            pose.scale(2.5f, 2.5f, 1f)
            graphics.renderItem(result.stack, 0, 0)
            pose.popPose()
        }
        // The name, the category and time, the description.
        val tx = dx + 58f
        val textW = DETAIL_W - 62f
        var ty = dy + 4f
        val nameLines = HdText.wrap(nameOf(entry), textW, 7.5f, true).take(2)
        for (line in nameLines) {
            HdText.draw(graphics, line, tx, ty, 7.5f, 0xFFFFFFFF.toInt(), bold = true)
            ty += 9f
        }
        val seconds = "%.0f".format(recipe.processingTime / 20.0)
        val sub = Component.translatable(category.translationKey + ".short").string + if (recipe.isMewtwo) "" else "  ·  " + Component.translatable("generations_core.rks.time", seconds).string
        HdText.draw(graphics, sub, tx, ty + 1f, 5f, MUTED)
        ty += 9f
        val description = if (recipe.description.isNotEmpty()) Component.translatable(recipe.description).string else ""
        val lines = HdText.wrap(description, textW, 4.8f)
        for ((i, line) in lines.take(if (nameLines.size > 1) 2 else 3).withIndex()) {
            val last = i == (if (nameLines.size > 1) 1 else 2) && lines.size > i + 1
            HdText.draw(graphics, if (last) HdText.fit("$line …", textW, 4.8f) else line, tx, ty, 4.8f, 0xFFB8C6D6.toInt())
            ty += 6.2f
        }
        // The ingredients: needed, present, missing.
        val iy = dy + ING_Y
        HdText.draw(graphics, Component.translatable("generations_core.rks.ingredients"), dx + 4f, iy, 5.5f, CYAN, bold = true)
        HdText.draw(graphics, Component.translatable("generations_core.rks.col.need"), dx + COL_NEED, iy + 0.5f, 4.4f, MUTED, align = HdText.Align.RIGHT)
        HdText.draw(graphics, Component.translatable("generations_core.rks.col.present"), dx + COL_PRESENT, iy + 0.5f, 4.4f, MUTED, align = HdText.Align.RIGHT)
        HdText.draw(graphics, Component.translatable("generations_core.rks.col.missing"), dx + COL_MISSING, iy + 0.5f, 4.4f, MUTED, align = HdText.Align.RIGHT)
        val present = recipe.present(slots())
        val inputs = recipe.inputs
        ingredientScroll = ingredientScroll.coerceIn(0, (inputs.size - ING_ROWS).coerceAtLeast(0))
        val cycle = (t() / 1.2f).toInt()
        for (k in 0 until ING_ROWS) {
            val i = ingredientScroll + k
            val input = inputs.getOrNull(i) ?: break
            val ry = iy + 9f + k * ING_PITCH
            val stacks = input.ingredient.matchingStacks()
            val icon = stacks.getOrNull(if (stacks.isEmpty()) 0 else cycle % stacks.size) ?: ItemStack.EMPTY
            val have = present.getOrElse(i) { 0 }
            val ok = have >= input.count
            HdGui.panel(graphics, HdGui.PANEL_DARK, dx + 3f, ry - 1.5f, DETAIL_W - 6f, ING_PITCH - 0.5f, if (ok) 0x3020A060 else 0x30A02028, corner = 2f)
            val pose = graphics.pose()
            pose.pushPose()
            pose.translate(dx + 5f, ry - 1f, 0f)
            pose.scale(0.5f, 0.5f, 1f)
            graphics.renderItem(icon, 0, 0)
            pose.popPose()
            HdText.draw(graphics, HdText.fit(icon.hoverName.string, COL_NEED - 46f, 5f), dx + 15f, ry, 5f, 0xFFDDE6EE.toInt())
            HdText.draw(graphics, input.count.toString(), dx + COL_NEED, ry, 5.2f, 0xFFE6EEF6.toInt(), bold = true, align = HdText.Align.RIGHT)
            HdText.draw(graphics, minOf(have, 9999).toString(), dx + COL_PRESENT, ry, 5.2f, if (ok) GREEN else RED, bold = true, align = HdText.Align.RIGHT)
            val missing = (input.count - have).coerceAtLeast(0)
            HdText.draw(graphics, if (missing == 0) "—" else missing.toString(), dx + COL_MISSING, ry, 5.2f, if (missing == 0) GREEN else RED, bold = true, align = HdText.Align.RIGHT)
        }
        if (inputs.size > ING_ROWS) {
            val h = ING_ROWS * ING_PITCH
            val bh = h * ING_ROWS / inputs.size
            val byy = iy + 8f + (h - bh) * ingredientScroll / (inputs.size - ING_ROWS).toFloat()
            HdGui.bar(graphics, dx + DETAIL_W - 2.5f, byy, 1.5f, bh, CYAN)
        }
        drawConditions(graphics, dx, dy, c, state, recipe)
    }

    /** The extra conditions: an energy reserve, Mewtwo's protocol, the machine itself. */
    private fun drawConditions(graphics: GuiGraphics, dx: Float, dy: Float, c: RksConsoleBlockEntity?, state: RksState, recipe: RksProtocolRecipe) {
        val level = minecraft?.level ?: return
        val cy = dy + COND_Y
        val ready = state == RksState.IDLE
        val machineKey = when {
            ready -> "generations_core.rks.condition.ready"
            state == RksState.CRAFTING -> "generations_core.rks.crafting"
            else -> "generations_core.rks.condition.busy"
        }
        val kind = if (recipe.isMewtwo) RksEnergy.PSYCHIC else recipe.energyKind
        if (recipe.isMewtwo && c != null && (state == RksState.GROWING || state == RksState.ANALYSING || state == RksState.AWAKENING)) {
            when (state) {
                RksState.ANALYSING -> HdText.draw(graphics, Component.translatable("generations_core.rks.analysing"), dx + DETAIL_W / 2f, cy + 4f, 6f, GREEN, bold = true, align = HdText.Align.CENTER)
                RksState.GROWING -> {
                    val g = c.growthNow(level.gameTime)
                    val phase = when {
                        g < 0.2 -> 1
                        g < 0.45 -> 2
                        g < 0.7 -> 3
                        g < 0.95 -> 4
                        g < 1.0 -> 5
                        else -> 6
                    }
                    HdText.draw(graphics, Component.translatable("generations_core.rks.phase.$phase"), dx + 4f, cy, 5.5f, VIOLET, bold = true)
                    HdGui.bar(graphics, dx + 4f, cy + 8f, DETAIL_W - 8f, 5f, 0xFF241A36.toInt())
                    HdGui.bar(graphics, dx + 4f, cy + 8f, (DETAIL_W - 8f) * g.toFloat(), 5f, VIOLET)
                }
                else -> HdText.draw(graphics, Component.translatable("generations_core.rks.state.awakening"), dx + DETAIL_W / 2f, cy + 4f, 7f, RED, bold = true, align = HdText.Align.CENTER)
            }
            return
        }
        if (kind != null && c != null) {
            val value = c.energyOf(kind)
            val required = kind.required
            val full = value >= required
            val colour = if (kind == RksEnergy.FIRE) FIRE else VIOLET
            HdText.draw(graphics, Component.translatable(kind.key("generations_core.rks.energy"), value, required), dx + 4f, cy, 5.2f, if (full) GREEN else colour, bold = true)
            HdText.draw(graphics, Component.translatable(machineKey), dx + DETAIL_W - 4f, cy + 0.5f, 4.6f, if (ready) GREEN else AMBER, align = HdText.Align.RIGHT)
            gauge(graphics, dx + 4f, cy + 8f, DETAIL_W - 8f, 5f, value / required.toFloat(), kind)
            if (recipe.isMewtwo && (c.machine.analysed || c.machine.experiment)) {
                HdText.draw(graphics, Component.translatable("generations_core.rks.research"), dx + 4f, cy + 14.5f, 4.6f, if (researchPresent()) GREEN else MUTED)
            }
            return
        }
        // A standard recipe: the machine.
        HdText.draw(graphics, Component.translatable(machineKey), dx + 4f, cy + 2f, 5.2f, if (ready) GREEN else AMBER, bold = true)
        if (state == RksState.CRAFTING) {
            HdText.draw(graphics, Component.translatable("generations_core.rks.crafting"), dx + DETAIL_W - 4f, cy + 2f, 5.2f, CYAN, align = HdText.Align.RIGHT)
        }
    }

    /** A reserve's gauge: violet for the psychic energy, embers to gold for the fire. */
    private fun gauge(graphics: GuiGraphics, x: Float, y: Float, w: Float, h: Float, ratio: Float, kind: RksEnergy) {
        val r = ratio.coerceIn(0f, 1f)
        HdGui.bar(graphics, x, y, w, h, if (kind == RksEnergy.FIRE) 0xFF2A1410.toInt() else 0xFF241A36.toInt())
        if (r <= 0f) return
        if (kind != RksEnergy.FIRE) {
            HdGui.bar(graphics, x, y, w * r, h, VIOLET)
            return
        }
        val segments = 24
        val filled = w * r
        val step = w / segments
        var sx = 0f
        while (sx < filled) {
            val sw = minOf(step, filled - sx)
            val u = (sx + sw / 2f) / w
            HdGui.bar(graphics, x + sx, y, sw + 0.2f, h, lerpColour(0xFFB3200E.toInt(), 0xFFFFC23A.toInt(), u))
            sx += step
        }
        if (r >= 1f) {
            val pulse = 0.35f + 0.25f * sin(t() * 4f)
            HdGui.draw(graphics, HdGui.GLOW, x - 6f, y - 6f, w + 12f, h + 12f, HdGui.withAlpha(0xFFB040, pulse))
        }
    }

    private fun lerpColour(a: Int, b: Int, u: Float): Int {
        val t = u.coerceIn(0f, 1f)
        val r = Mth.lerp(t, ((a shr 16) and 255).toFloat(), ((b shr 16) and 255).toFloat()).toInt()
        val g = Mth.lerp(t, ((a shr 8) and 255).toFloat(), ((b shr 8) and 255).toFloat()).toInt()
        val bl = Mth.lerp(t, (a and 255).toFloat(), (b and 255).toFloat()).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /** Tooltips: an ingredient's item, a cut name, why the button is off. */
    private fun drawHoverTips(graphics: GuiGraphics, mx: Int, my: Int) {
        val state = console?.state ?: RksState.INCOMPLETE
        if (!machineUsable(state) || state == RksState.ASSEMBLING || state == RksState.REPAIRING) return
        val x = leftPos.toFloat()
        val y = topPos.toFloat()
        val entry = selectedRecipe()
        // An ingredient.
        if (entry != null) {
            val recipe = variant(entry).value()
            val dx = x + DETAIL_X
            val iy = y + PANEL_Y + ING_Y + 9f
            if (mx >= dx + 3f && mx < dx + DETAIL_W - 3f && my >= iy - 1.5f && my < iy - 1.5f + ING_ROWS * ING_PITCH) {
                val k = ((my - (iy - 1.5f)) / ING_PITCH).toInt()
                val input = recipe.inputs.getOrNull(ingredientScroll + k)
                if (input != null) {
                    val stacks = input.ingredient.matchingStacks()
                    val icon = stacks.getOrNull(if (stacks.isEmpty()) 0 else (t() / 1.2f).toInt() % stacks.size)
                    if (icon != null && !icon.isEmpty) graphics.renderTooltip(font, icon, mx, my)
                    return
                }
            }
            // The button, off: what is missing.
            if (launch.visible && !launch.active && launch.isMouseOver(mx.toDouble(), my.toDouble())) {
                val lines = ArrayList<Component>()
                val present = recipe.present(slots())
                for ((i, input) in recipe.inputs.withIndex()) {
                    val missing = input.count - present.getOrElse(i) { 0 }
                    if (missing > 0) {
                        val name = input.ingredient.matchingStacks().firstOrNull()?.hoverName?.string ?: "?"
                        lines += Component.literal("$missing × $name").withStyle(net.minecraft.ChatFormatting.RED)
                    }
                }
                recipe.energyKind?.let { kind ->
                    val c = console
                    if (c != null && c.energyOf(kind) < kind.required) lines += Component.translatable(kind.key("generations_core.rks.error.energy"), c.energyOf(kind), kind.required).withStyle(net.minecraft.ChatFormatting.GOLD)
                }
                if (state != RksState.IDLE) lines += Component.translatable("generations_core.rks.condition.busy").withStyle(net.minecraft.ChatFormatting.YELLOW)
                if (lines.isNotEmpty()) {
                    lines.add(0, Component.translatable("generations_core.rks.missing_title").withStyle(net.minecraft.ChatFormatting.GRAY))
                    graphics.renderComponentTooltip(font, lines, mx, my)
                }
                return
            }
        }
        // A cut name in the list.
        val lx = x + LIST_X
        val ly = y + LIST_Y
        if (mx >= lx && mx < lx + LIST_W && my >= ly && my < ly + LIST_H && !dragging) {
            val list = entries()
            val local = my - ly + scrollPos
            val index = floor(local / PITCH).toInt()
            val holder = list.getOrNull(index) ?: return
            if (local - index * PITCH >= ROW_H) return
            val name = nameOf(holder)
            if (HdText.width(name, 5.5f) > LIST_W - 34f) graphics.renderTooltip(font, Component.literal(name), mx, my)
        }
    }

    // ---------------------------------------------------------------- other states

    private fun drawWorking(graphics: GuiGraphics, x: Float, y: Float, c: RksConsoleBlockEntity?, state: RksState) {
        val level = minecraft?.level ?: return
        val seconds = c?.stateSeconds(level.gameTime) ?: 0.0
        val total = if (state == RksState.ASSEMBLING) RksSettings.machine.assemblySeconds else RksSettings.machine.repairSeconds
        val p = (seconds / total.coerceAtLeast(0.1)).toFloat().coerceIn(0f, 1f)
        val key = if (state == RksState.ASSEMBLING) "generations_core.rks.assembling" else "generations_core.rks.repairing"
        val cx = x + LIST_PANEL_X + (DETAIL_X + DETAIL_W - LIST_PANEL_X) / 2f
        HdText.draw(graphics, Component.translatable(key), cx, y + 72f, 9f, CYAN, bold = true, align = HdText.Align.CENTER)
        HdGui.bar(graphics, cx - 100f, y + 90f, 200f, 6f, 0xFF1A2A3A.toInt())
        HdGui.bar(graphics, cx - 100f, y + 90f, 200f * p, 6f, CYAN)
    }

    private fun drawDamaged(graphics: GuiGraphics, x: Float, y: Float) {
        val flicker = if (sin(t() * 7f) > -0.6f) 1f else 0.6f
        val left = x + LIST_PANEL_X + 2f
        val w = DETAIL_X + DETAIL_W - LIST_PANEL_X - 4f
        val cx = left + w / 2f
        HdGui.panel(graphics, HdGui.PANEL_RED, left, y + PANEL_Y + 4f, w, PANEL_H - 8f, HdGui.withAlpha(0x5A0A0E, 0.9f), corner = 8f)
        HdText.draw(graphics, Component.translatable("generations_core.rks.critical.title"), cx, y + 44f, 10f, HdGui.withAlpha(0xFF3B30, flicker), bold = true, align = HdText.Align.CENTER, shadow = true)
        HdText.draw(graphics, Component.translatable("generations_core.rks.critical.line"), cx, y + 66f, 7f, 0xFFFFD0D0.toInt(), bold = true, align = HdText.Align.CENTER)
        var line = 0
        for (text in HdText.wrap(Component.translatable("generations_core.rks.critical.hint").string, w - 30f, 6f)) {
            HdText.draw(graphics, text, cx, y + 92f + line * 9f, 6f, 0xFFE8C0C0.toInt(), align = HdText.Align.CENTER)
            line++
        }
    }

    /** The plan of the machine seen from above, each module green when in place, red when missing. */
    private fun drawIncomplete(graphics: GuiGraphics, x: Float, y: Float, c: RksConsoleBlockEntity?) {
        val missing = c?.missing ?: RksModule.entries.filter { it != RksModule.CONSOLE }
        val px = x + LIST_PANEL_X + LIST_PANEL_W / 2f
        HdText.draw(graphics, Component.translatable("generations_core.rks.incomplete"), px, y + 22f, 6.5f, AMBER, bold = true, align = HdText.Align.CENTER)
        val cell = 30f
        val ox = px - cell * 1.5f
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
        HdText.draw(graphics, Component.translatable("generations_core.rks.front"), px, y + 128f, 5f, MUTED, align = HdText.Align.CENTER)
        val dx = x + DETAIL_X + 6f
        HdText.draw(graphics, Component.translatable("generations_core.rks.missing"), dx, y + 22f, 6f, RED, bold = true)
        var line = 0
        for (module in missing) {
            for (text in HdText.wrap(Component.translatable("generations_core.rks.missing.${module.name.lowercase()}").string, DETAIL_W - 12f, 5.5f)) {
                HdText.draw(graphics, text, dx, y + 33f + line * 7.5f, 5.5f, 0xFFE6EEF6.toInt())
                line++
            }
            line++
        }
        for ((i, text) in HdText.wrap(Component.translatable("generations_core.rks.hologram_hint").string, DETAIL_W - 12f, 4.8f).withIndex()) {
            HdText.draw(graphics, text, dx, y + 134f + i * 6.5f, 4.8f, MUTED)
        }
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

    private fun entryX(i: Int): Float = leftPos + SAC_X + (i / 9) * 160f

    private fun entryY(i: Int): Float = topPos + 38f + (i % 9) * 19f

    private fun drawSacrifice(graphics: GuiGraphics, list: S2CRksSacrificeListPacket, x: Float, y: Float, mx: Int, my: Int) {
        val kind = kindOf(list)
        val accent = if (kind == RksEnergy.FIRE) FIRE else VIOLET
        val chosenTint = if (kind == RksEnergy.FIRE) 0xFF5A2A12.toInt() else 0xFF3A2466.toInt()
        HdGui.panel(graphics, HdGui.PANEL_DARK, x + 8f, y + 8f, imageWidth - 16f, imageHeight - 16f, 0xFA101826.toInt(), corner = 10f)
        HdGui.panel(graphics, HdGui.PANEL_OUTLINE, x + 8f, y + 8f, imageWidth - 16f, imageHeight - 16f, HdGui.withAlpha(accent and 0xFFFFFF, 0.65f), corner = 10f)
        HdText.draw(graphics, Component.translatable(kind.key("generations_core.rks.sacrifice.title")), x + 18f, y + 14f, 8f, accent, bold = true)
        HdText.draw(graphics, Component.translatable(kind.key("generations_core.rks.energy"), list.energy, list.required), x + imageWidth - 18f, y + 15f, 6.5f, 0xFFE6EEF6.toInt(), align = HdText.Align.RIGHT)
        gauge(graphics, x + imageWidth - 118f, y + 24f, 100f, 3f, list.energy / list.required.coerceAtLeast(1).toFloat(), kind)
        if (list.blocked.isNotEmpty()) HdText.draw(graphics, Component.translatable(list.blocked), x + imageWidth / 2f, y + 28f, 6f, RED, bold = true, align = HdText.Align.CENTER)
        if (list.entries.isEmpty()) HdText.draw(graphics, Component.translatable(kind.key("generations_core.rks.sacrifice.none")), x + imageWidth / 2f, y + 100f, 7f, MUTED, align = HdText.Align.CENTER)
        for ((i, e) in list.entries.withIndex()) {
            val ex = entryX(i)
            val ey = entryY(i)
            val on = e.uuid in chosen
            val hover = mx >= ex && mx < ex + 152f && my >= ey && my < ey + 17f
            HdGui.panel(graphics, HdGui.PANEL_DARK, ex, ey, 152f, 17f, if (on) chosenTint else if (hover) 0xFF1E2A3C.toInt() else 0xFF151E2B.toInt(), corner = 4f)
            HdGui.panel(graphics, HdGui.PANEL_DARK, ex + 4f, ey + 4f, 9f, 9f, if (on) accent else 0xFF2A3446.toInt(), corner = 2f)
            if (on) HdGui.panel(graphics, HdGui.PANEL_DARK, ex + 6f, ey + 6f, 5f, 5f, 0xFFFFFFFF.toInt(), corner = 1f)
            val species = ResourceLocation.tryParse(e.species)?.let { PokemonSpecies.getByIdentifier(it) }
            val name = e.nickname.ifEmpty { species?.translatedName?.string ?: e.species }
            HdText.draw(graphics, HdText.fit(name, 62f, 6f), ex + 17f, ey + 2.5f, 6f, if (e.shiny) 0xFFFFD34D.toInt() else 0xFFE6EEF6.toInt(), bold = e.shiny)
            val place = if (e.place < 0) Component.translatable("generations_core.rks.sacrifice.party").string else Component.translatable("generations_core.rks.sacrifice.box", e.place + 1).string
            HdText.draw(graphics, "N.${e.level}  $place", ex + 17f, ey + 10f, 4.6f, MUTED)
            HdText.draw(graphics, "+${e.points}", ex + 146f, ey + 5f, 7f, accent, bold = true, align = HdText.Align.RIGHT)
            if (e.shiny) HdText.draw(graphics, "*", ex + 120f, ey + 3.5f, 9f, 0xFFFFD34D.toInt(), bold = true, align = HdText.Align.CENTER)
        }
        val total = chosen.values.sumOf { it.points }
        val room = (list.required - list.energy).coerceAtLeast(0)
        val lost = (total - room).coerceAtLeast(0)
        HdText.draw(graphics, "${list.page + 1}/${list.pages}", x + 90f, y + 225f, 6f, MUTED)
        HdText.draw(graphics, Component.translatable("generations_core.rks.sacrifice.total", chosen.size, total), x + 118f, y + 221f, 6f, 0xFFE6EEF6.toInt(), bold = true)
        if (lost > 0) HdText.draw(graphics, Component.translatable("generations_core.rks.sacrifice.over", lost), x + 118f, y + 229f, 5.5f, AMBER)
    }

    private fun drawConfirm(graphics: GuiGraphics, x: Float, y: Float) {
        val cx = x + imageWidth / 2f
        val kind = sacrifice?.let { kindOf(it) } ?: RksEnergy.PSYCHIC
        val accent = if (confirmStart) 0xC0FF3B30.toInt() else if (kind == RksEnergy.FIRE) 0xC0FF7A1E.toInt() else 0xC0B06BFF.toInt()
        HdGui.panel(graphics, HdGui.PANEL_DARK, cx - 120f, y + 74f, 240f, 104f, 0xFC121A28.toInt(), corner = 10f)
        HdGui.panel(graphics, HdGui.PANEL_OUTLINE, cx - 120f, y + 74f, 240f, 104f, accent, corner = 10f)
        val title: Component
        val lines = ArrayList<Pair<String, Int>>()
        if (confirmStart) {
            title = Component.translatable("generations_core.rks.confirm.start.title")
            lines += Component.translatable("generations_core.rks.confirm.start.line").string to 0xFFE6EEF6.toInt()
        } else {
            val total = chosen.values.sumOf { it.points }
            title = Component.translatable("generations_core.rks.confirm.sacrifice.title", chosen.size)
            lines += Component.translatable(kind.key("generations_core.rks.confirm.sacrifice.line"), total).string to 0xFFE6EEF6.toInt()
            if (chosen.values.any { it.shiny }) lines += Component.translatable("generations_core.rks.confirm.shiny").string to 0xFFFFD34D.toInt()
        }
        HdText.draw(graphics, title, cx, y + 84f, 8f, if (confirmStart) RED else if (kind == RksEnergy.FIRE) FIRE else VIOLET, bold = true, align = HdText.Align.CENTER)
        var row = 0
        for ((text, colour) in lines) for (w in HdText.wrap(text, 220f, 6f)) {
            HdText.draw(graphics, w, cx, y + 102f + row * 9f, 6f, colour, align = HdText.Align.CENTER)
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
                val ex = entryX(i)
                val ey = entryY(i)
                if (mouseX >= ex && mouseX < ex + 152f && mouseY >= ey && mouseY < ey + 17f) {
                    if (chosen.remove(e.uuid) == null) chosen[e.uuid] = e
                    return true
                }
            }
            return true
        }
        val state = console?.state ?: RksState.INCOMPLETE
        val usable = machineUsable(state) && state != RksState.ASSEMBLING && state != RksState.REPAIRING
        val x = leftPos.toFloat()
        val y = topPos.toFloat()
        // The categories.
        for ((i, cat) in RksCategory.entries.withIndex()) {
            val tx = x + CAT_X + 3f
            val ty = y + PANEL_Y + 3f + i * (TAB_H + 4f)
            if (mouseX >= tx && mouseX < tx + TAB_W && mouseY >= ty && mouseY < ty + TAB_H) {
                if (usable) switchTo(cat)
                return true
            }
        }
        if (usable && button == 0) {
            // The scroll bar: drag the thumb, or jump a page on the track.
            val sx = x + SCROLL_X
            val ly = y + LIST_Y
            if (mouseX >= sx - 2 && mouseX < sx + SCROLL_W + 2 && mouseY >= ly && mouseY < ly + LIST_H) {
                val thumb = thumb()
                if (thumb != null) {
                    if (mouseY >= thumb[0] && mouseY < thumb[0] + thumb[1]) {
                        dragging = true
                        dragOffset = (mouseY - thumb[0]).toFloat()
                    } else {
                        scrollTarget += if (mouseY < thumb[0]) -LIST_H * 0.8f else LIST_H * 0.8f
                        clampScroll()
                    }
                }
                return true
            }
        }
        // The list.
        val lx = x + LIST_X
        val ly = y + LIST_Y
        if (mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ly && mouseY < ly + LIST_H) {
            if (!usable) return true
            val local = (mouseY - ly).toFloat() + scrollPos
            val index = floor(local / PITCH).toInt()
            val holder = entries().getOrNull(index)
            if (holder != null && local - index * PITCH < ROW_H) {
                if (!matches(holder, selected)) {
                    selected = holder.id()
                    ingredientScroll = 0
                }
                ensureVisible()
            }
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (dragging) {
            val max = maxScroll()
            val thumb = thumb()
            if (thumb != null && max > 0f) {
                val room = LIST_H - thumb[1]
                val top = (mouseY - topPos - LIST_Y - dragOffset).toFloat()
                scrollTarget = (top / room).coerceIn(0f, 1f) * max
                scrollPos = scrollTarget
            }
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (dragging) {
            dragging = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val list = sacrifice
        if (list != null) {
            if (confirmSacrifice) return true
            val kind = kindOf(list).id
            if (scrollY < 0 && list.page < list.pages - 1) send(C2SRksActionPacket.LIST, recipe = kind, value = list.page + 1)
            if (scrollY > 0 && list.page > 0) send(C2SRksActionPacket.LIST, recipe = kind, value = list.page - 1)
            return true
        }
        if (confirmStart) return true
        val x = leftPos
        val y = topPos
        // The list and its bar.
        if (mouseX >= x + LIST_PANEL_X && mouseX < x + LIST_PANEL_X + LIST_PANEL_W && mouseY >= y + PANEL_Y && mouseY < y + PANEL_Y + PANEL_H) {
            scrollTarget -= (scrollY * PITCH).toFloat()
            clampScroll()
            return true
        }
        // The sheet's ingredients.
        if (mouseX >= x + DETAIL_X && mouseX < x + DETAIL_X + DETAIL_W && mouseY >= y + PANEL_Y + ING_Y && mouseY < y + PANEL_Y + COND_Y) {
            ingredientScroll -= scrollY.toInt().coerceIn(-1, 1)
            return true
        }
        // The categories: the wheel moves from one to the next.
        if (mouseX >= x + CAT_X && mouseX < x + CAT_X + CAT_W && mouseY >= y + PANEL_Y && mouseY < y + PANEL_Y + PANEL_H) {
            val state = console?.state ?: RksState.INCOMPLETE
            if (machineUsable(state)) {
                val i = (category.ordinal - scrollY.toInt().coerceIn(-1, 1)).coerceIn(0, RksCategory.entries.size - 1)
                switchTo(RksCategory.entries[i])
            }
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
        // Arrow keys and pages: the selection in the list.
        if (sacrifice == null && !confirmSacrifice && !confirmStart && keyCode in KEYS) {
            val state = console?.state ?: RksState.INCOMPLETE
            if (!machineUsable(state)) return true
            val list = entries()
            if (list.isEmpty()) return true
            val index = list.indexOfFirst { matches(it, selected) }.coerceAtLeast(0)
            val page = (LIST_H / PITCH).coerceAtLeast(1)
            val next = when (keyCode) {
                KEY_UP -> index - 1
                KEY_DOWN -> index + 1
                KEY_PAGE_UP -> index - page
                KEY_PAGE_DOWN -> index + page
                KEY_HOME -> 0
                else -> list.size - 1
            }.coerceIn(0, list.size - 1)
            selected = list[next].id()
            ingredientScroll = 0
            ensureVisible()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    fun onList(packet: S2CRksSacrificeListPacket) {
        val before = sacrifice
        // Another reserve's list: start afresh.
        if (before != null && before.kind != packet.kind) chosen.clear()
        sacrifice = packet
        // What was chosen stays chosen across pages, unless it is gone.
        if (packet.page == 0 && packet.total == 0) chosen.clear()
    }

    private class Memory(val scroll: Float, val selected: ResourceLocation?)

    companion object {
        // The layout (HOOH-PLAN.md § 3): 400 × 248, at GUI scale 4 in 1080p the screen is 480 × 270.
        const val W = 400
        const val H = 248
        const val PANEL_Y = 18
        const val PANEL_H = 134
        const val CAT_X = 6
        const val CAT_W = 76
        const val TAB_W = 70
        const val TAB_H = 38
        const val LIST_PANEL_X = 86
        const val LIST_PANEL_W = 126
        const val LIST_X = 89
        const val LIST_Y = 21
        const val LIST_W = 111
        const val LIST_H = 128
        const val SCROLL_X = 204
        const val SCROLL_W = 4
        const val ROW_H = 18
        const val PITCH = 20
        const val DETAIL_X = 216
        const val DETAIL_W = 178
        const val ING_Y = 57f
        const val ING_ROWS = 5
        const val ING_PITCH = 8.5f
        const val COND_Y = 109f
        const val BUTTON_Y = 136
        const val COL_NEED = 124f
        const val COL_PRESENT = 148f
        const val COL_MISSING = 172f
        const val SAC_X = 40f

        private const val KEY_UP = 265
        private const val KEY_DOWN = 264
        private const val KEY_PAGE_UP = 266
        private const val KEY_PAGE_DOWN = 267
        private const val KEY_HOME = 268
        private const val KEY_END = 269
        private val KEYS = setOf(KEY_UP, KEY_DOWN, KEY_PAGE_UP, KEY_PAGE_DOWN, KEY_HOME, KEY_END)

        private val CYAN = 0xFF35E0FF.toInt()
        private val VIOLET = 0xFFB06BFF.toInt()
        private val FIRE = 0xFFFF8A2A.toInt()
        private val RED = 0xFFFF3B30.toInt()
        private val GREEN = 0xFF4DFF88.toInt()
        private val AMBER = 0xFFFFB347.toInt()
        private val MUTED = 0xFF7F8EA0.toInt()

        /** Each category's scroll and selection, and the last category, for the rest of the game (D10). */
        private val memory = EnumMap<RksCategory, Memory>(RksCategory::class.java)
        private var lastCategory = RksCategory.GENETIC

        /** The server's page of the sacrifice list, for the screen of that machine. */
        fun receive(packet: S2CRksSacrificeListPacket) {
            val screen = Minecraft.getInstance().screen as? RksConsoleScreen ?: return
            if (screen.menu.pos != packet.pos) return
            screen.onList(packet)
        }
    }
}
