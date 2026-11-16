package generations.gg.generations.core.generationscore.common.client.boss

import generations.gg.generations.core.generationscore.common.network.packets.boss.C2SBossRewardsSavePacket
import generations.gg.generations.core.generationscore.common.network.packets.boss.S2CBossRewardsEditorPacket
import generations.gg.generations.core.generationscore.common.world.boss.BossRarity
import generations.gg.generations.core.generationscore.common.world.boss.BossRewards
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * /boss rewards, for operators: the rewards of each rarity and the Titan bonus.
 * - a tab per table; each reward: item, quantity min–max, chance in % (100 = always), ✕;
 * - a click on an item of the inventory (bottom) adds it to the open tab, with its
 *   components (enchantments, name…) and its count as quantity;
 * - the Mega Stone chances (first victory over a Mega form, then the next ones);
 * - Save sends everything to the server, which checks the permission again, writes
 *   rewards.json and uses it at once. Escape or Cancel: nothing changes.
 */
class BossRewardsEditorScreen(packet: S2CBossRewardsEditorPacket) : Screen(Component.translatable("generations_core.boss.editor.title")) {
    private class Row(val stack: ItemStack, var min: String, var max: String, var chance: String)

    private val tables: List<MutableList<Row>> = BossRewards.TABLES.indices.map { index ->
        packet.tables.getOrNull(index).orEmpty().map { Row(it.stack.copyWithCount(1), it.min.toString(), it.max.toString(), number(it.chance)) }.toMutableList()
    }
    private var firstChance = number(packet.firstChance)
    private var repeatChance = number(packet.repeatChance)
    private var tab = 0
    private var scroll = 0
    private var left = 0
    private var top = 0

    private val rows: MutableList<Row> get() = tables[tab]

    override fun init() {
        left = (width - WIDTH) / 2
        top = (height - HEIGHT) / 2
        scroll = scroll.coerceIn(0, maxOf(0, rows.size - VISIBLE))

        // Tabs.
        val tabWidth = (WIDTH - 16) / BossRewards.TABLES.size
        BossRewards.TABLES.forEachIndexed { index, _ ->
            addRenderableWidget(Button.builder(tabName(index)) { select(index) }
                .bounds(left + 8 + index * tabWidth, top + 18, tabWidth - 2, 16).build()).active = index != tab
        }

        // Rewards of the tab.
        rows.drop(scroll).take(VISIBLE).forEachIndexed { i, row ->
            val y = top + LIST_TOP + i * ROW
            addRenderableWidget(number(left + 214, y, 34, row.min) { row.min = it })
            addRenderableWidget(number(left + 252, y, 34, row.max) { row.max = it })
            addRenderableWidget(number(left + 290, y, 42, row.chance) { row.chance = it })
            addRenderableWidget(Button.builder(Component.literal("✕")) {
                rows.remove(row)
                rebuildWidgets()
            }.bounds(left + 352, y, 16, 16).build())
        }

        // Mega Stone chances.
        addRenderableWidget(number(left + 196, top + 222, 40, firstChance) { firstChance = it })
        addRenderableWidget(number(left + 300, top + 222, 40, repeatChance) { repeatChance = it })

        addRenderableWidget(Button.builder(Component.translatable("generations_core.boss.editor.save")) { save() }
            .bounds(left + WIDTH - 178, top + HEIGHT - 24, 82, 18).build())
        addRenderableWidget(Button.builder(Component.translatable("generations_core.boss.editor.cancel")) { onClose() }
            .bounds(left + WIDTH - 90, top + HEIGHT - 24, 82, 18).build())
    }

    private fun number(x: Int, y: Int, w: Int, value: String, onChange: (String) -> Unit): EditBox {
        val box = EditBox(font, x, y, w, 16, Component.empty())
        box.setMaxLength(6)
        box.setFilter { text -> text.all { it.isDigit() || it == '.' } && text.count { it == '.' } <= 1 }
        box.value = value
        box.setResponder(onChange)
        return box
    }

    private fun tabName(index: Int): Component {
        val id = BossRewards.TABLES[index]
        return BossRarity.byId(id)?.let { Component.translatable(it.translationKey) } ?: Component.translatable("generations_core.boss.editor.tab.titan")
    }

    private fun tabColour(index: Int): Int {
        val rarity = BossRarity.byId(BossRewards.TABLES[index])
        return 0xFF000000.toInt() or (if (rarity != null) BossClientConfig.settings.color(rarity) else BossClientConfig.settings.titanColor)
    }

    private fun select(index: Int) {
        tab = index
        scroll = 0
        rebuildWidgets()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val white = 0xFFFFFFFF.toInt()
        val grey = 0xFFA0A0B0.toInt()
        graphics.drawString(font, title, left + 8, top + 6, white)

        // Tab underline in the table's colour.
        val tabWidth = (WIDTH - 16) / BossRewards.TABLES.size
        graphics.fill(left + 8 + tab * tabWidth, top + 35, left + 8 + (tab + 1) * tabWidth - 2, top + 37, tabColour(tab))

        // Column titles.
        graphics.drawString(font, Component.translatable("generations_core.boss.editor.item"), left + 10, top + 42, grey)
        graphics.drawString(font, Component.translatable("generations_core.boss.editor.min"), left + 214, top + 42, grey)
        graphics.drawString(font, Component.translatable("generations_core.boss.editor.max"), left + 252, top + 42, grey)
        graphics.drawString(font, Component.translatable("generations_core.boss.editor.chance"), left + 290, top + 42, grey)

        var hovered: ItemStack? = null
        if (rows.isEmpty()) {
            graphics.drawString(font, Component.translatable("generations_core.boss.editor.empty"), left + 10, top + LIST_TOP + 4, grey)
        }
        rows.drop(scroll).take(VISIBLE).forEachIndexed { i, row ->
            val y = top + LIST_TOP + i * ROW
            graphics.renderItem(row.stack, left + 10, y)
            val name = font.plainSubstrByWidth(row.stack.hoverName.string, 176)
            graphics.drawString(font, name, left + 30, y + 4, white)
            graphics.drawString(font, "%", left + 336, y + 4, grey)
            if (mouseX in left + 8 until left + 210 && mouseY in y until y + 16) hovered = row.stack
        }
        if (rows.size > VISIBLE) {
            graphics.drawString(font, "${scroll + 1}–${minOf(rows.size, scroll + VISIBLE)} / ${rows.size}", left + 372, top + LIST_TOP + VISIBLE * ROW - 10, grey)
        }

        // Inventory: a click adds the item.
        graphics.drawString(font, Component.translatable("generations_core.boss.editor.hint"), left + 10, top + 166, grey)
        val inventory = Minecraft.getInstance().player?.inventory
        if (inventory != null) {
            for (slot in 0 until 36) {
                val (x, y) = slotPosition(slot)
                graphics.fill(x, y, x + 17, y + 17, 0x40FFFFFF)
                val stack = inventory.getItem(slot)
                if (stack.isEmpty) continue
                graphics.renderItem(stack, x, y)
                graphics.renderItemDecorations(font, stack, x, y)
                if (mouseX in x until x + 17 && mouseY in y until y + 17) {
                    graphics.fill(x, y, x + 17, y + 17, 0x60FFFFFF)
                    hovered = stack
                }
            }
        }

        // Mega Stones.
        graphics.drawString(font, Component.translatable("generations_core.boss.editor.mega"), left + 196, top + 196, white)
        graphics.drawString(font, Component.translatable("generations_core.boss.editor.mega_first"), left + 196, top + 210, grey)
        graphics.drawString(font, Component.translatable("generations_core.boss.editor.mega_repeat"), left + 300, top + 210, grey)
        graphics.drawString(font, "%", left + 240, top + 226, grey)
        graphics.drawString(font, "%", left + 344, top + 226, grey)

        hovered?.let { graphics.renderTooltip(font, it, mouseX, mouseY) }
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, 0xFF5A5A6E.toInt())
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF0181820.toInt())
        graphics.fill(left + 6, top + LIST_TOP - 3, left + WIDTH - 6, top + LIST_TOP + VISIBLE * ROW, 0x30FFFFFF)
    }

    /** Inventory slot on screen: the three rows of the inventory, then the hotbar. */
    private fun slotPosition(slot: Int): Pair<Int, Int> {
        val row = if (slot < 9) 3 else slot / 9 - 1
        return (left + 10 + (slot % 9) * 18) to (top + INVENTORY_TOP + row * 18)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val inventory = Minecraft.getInstance().player?.inventory
        if (inventory != null && button == 0) {
            for (slot in 0 until 36) {
                val (x, y) = slotPosition(slot)
                if (mouseX >= x && mouseX < x + 17 && mouseY >= y && mouseY < y + 17) {
                    val stack = inventory.getItem(slot)
                    if (stack.isEmpty || rows.size >= MAX_ROWS) return true
                    rows += Row(stack.copyWithCount(1), stack.count.toString(), stack.count.toString(), "100")
                    scroll = maxOf(0, rows.size - VISIBLE)
                    rebuildWidgets()
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (rows.size > VISIBLE && mouseY >= top + LIST_TOP && mouseY < top + LIST_TOP + VISIBLE * ROW) {
            val next = (scroll - scrollY.toInt().coerceIn(-1, 1)).coerceIn(0, rows.size - VISIBLE)
            if (next != scroll) {
                scroll = next
                rebuildWidgets()
            }
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    private fun save() {
        val edited = tables.map { table ->
            table.map { row ->
                val min = row.min.toDoubleOrNull()?.toInt()?.coerceIn(1, 999) ?: 1
                val max = (row.max.toDoubleOrNull()?.toInt() ?: min).coerceIn(min, 999)
                val chance = (row.chance.toDoubleOrNull() ?: 100.0).coerceIn(0.0, 100.0)
                BossRewards.Edit(row.stack, min, max, chance)
            }
        }
        val first = (firstChance.toDoubleOrNull() ?: 100.0).coerceIn(0.0, 100.0)
        val repeat = (repeatChance.toDoubleOrNull() ?: 10.0).coerceIn(0.0, 100.0)
        C2SBossRewardsSavePacket(edited, first, repeat).sendToServer()
        onClose()
    }

    override fun isPauseScreen() = false

    companion object {
        private const val WIDTH = 420
        private const val HEIGHT = 270
        private const val LIST_TOP = 54
        private const val ROW = 18
        private const val VISIBLE = 6
        private const val INVENTORY_TOP = 178
        private const val MAX_ROWS = 256

        private fun number(value: Double): String =
            if (value == Math.floor(value)) value.toInt().toString() else value.toString()
    }
}
