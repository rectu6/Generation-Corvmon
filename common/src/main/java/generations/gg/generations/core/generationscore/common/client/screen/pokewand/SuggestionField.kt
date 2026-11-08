package generations.gg.generations.core.generationscore.common.client.screen.pokewand

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * Text field with a list of suggestions under it while it is focused: typing "gir"
 * lists what starts with "gir" first, then what contains it. Clicking a suggestion
 * fills the field.
 */
class SuggestionField(
    private val font: Font,
    x: Int, y: Int, width: Int, height: Int,
    hint: String,
    initial: String,
    /** (value written in the field, label shown) for every possible entry. */
    private val entries: () -> List<Pair<String, String>>,
    private val onChange: (String) -> Unit = {},
) {
    val box = EditBox(font, x, y, width, height, Component.literal(hint)).also {
        it.setMaxLength(128)
        it.value = initial
        it.setHint(Component.literal(hint))
    }

    private var suggestions: List<Pair<String, String>> = emptyList()
    private var picked = false

    init {
        box.setResponder {
            if (!picked) refresh()
            onChange(it)
        }
    }

    val value: String get() = box.value.trim()

    private fun refresh() {
        val query = box.value.trim().lowercase()
        suggestions = if (query.isEmpty()) emptyList() else {
            val all = entries()
            val starts = all.filter { (value, label) -> value.lowercase().substringAfter(':').startsWith(query) || label.lowercase().startsWith(query) }.toSet()
            val contains = all.filter { entry ->
                entry !in starts && (entry.first.lowercase().contains(query) || entry.second.lowercase().contains(query))
            }
            (starts.sortedBy { it.second } + contains.sortedBy { it.second }).take(MAX_SUGGESTIONS)
        }
    }

    private fun visible(): Boolean = box.isFocused && suggestions.isNotEmpty()

    fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!visible()) return
        val x = box.x
        val y = box.y + box.height
        val width = maxOf(box.width, suggestions.maxOf { font.width(it.second) } + 6)
        graphics.pose().pushPose()
        // Over the other widgets.
        graphics.pose().translate(0f, 0f, 400f)
        graphics.fill(x, y, x + width, y + suggestions.size * LINE + 2, 0xF0101018.toInt())
        graphics.renderOutline(x, y, width, suggestions.size * LINE + 2, 0xFF5A5A70.toInt())
        suggestions.forEachIndexed { index, (_, label) ->
            val lineY = y + 1 + index * LINE
            val hovered = mouseX in x until x + width && mouseY in lineY until lineY + LINE
            if (hovered) graphics.fill(x + 1, lineY, x + width - 1, lineY + LINE, 0xFF3A3A55.toInt())
            graphics.drawString(font, label, x + 3, lineY + 1, if (hovered) 0xFFFFA0 else 0xE0E0E0)
        }
        graphics.pose().popPose()
    }

    /** Picks the suggestion under the mouse; true when the click was used. */
    fun click(mouseX: Double, mouseY: Double): Boolean {
        if (!visible()) return false
        val x = box.x
        val y = box.y + box.height + 1
        val width = maxOf(box.width, suggestions.maxOf { font.width(it.second) } + 6)
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + suggestions.size * LINE) return false
        val (value, _) = suggestions[((mouseY - y) / LINE).toInt()]
        picked = true
        box.value = value
        box.moveCursorToStart(false)
        picked = false
        suggestions = emptyList()
        return true
    }

    companion object {
        private const val MAX_SUGGESTIONS = 8
        private const val LINE = 10
    }
}
