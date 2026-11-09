package generations.gg.generations.core.generationscore.common.client.screen.hd

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FormattedCharSequence
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.ceil

/**
 * Smooth text for the HD interfaces: drawn with a TrueType font of the system
 * (Bahnschrift, Segoe UI or Arial on Windows) by Java, at the real screen
 * resolution, instead of Minecraft's pixel font. Each text becomes a small texture,
 * kept in a cache. Sizes are in GUI pixels (10 is about Minecraft's font).
 */
object HdText {
    enum class Align { LEFT, CENTER, RIGHT }

    private const val PADDING = 2
    private const val CACHE_SIZE = 600

    private val family: String by lazy {
        val installed = runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet() }.getOrDefault(emptySet())
        listOf("Bahnschrift", "Segoe UI", "Arial", "Helvetica", "DejaVu Sans").firstOrNull { it in installed } ?: Font.SANS_SERIF
    }

    private class Entry(val location: ResourceLocation, val width: Int, val height: Int, val ascent: Int)

    private val cache = object : LinkedHashMap<String, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean {
            if (size <= CACHE_SIZE) return false
            Minecraft.getInstance().textureManager.release(eldest.value.location)
            return true
        }
    }
    private var counter = 0
    private val measure = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().apply { hints() }

    private fun java.awt.Graphics2D.hints() {
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }

    private val scale: Double get() = Minecraft.getInstance().window.guiScale

    private fun font(size: Float, bold: Boolean): Font = Font(family, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont((size * scale).toFloat())

    /** Width in GUI pixels. */
    fun width(text: String, size: Float, bold: Boolean = false): Float =
        (measure.getFontMetrics(font(size, bold)).stringWidth(text) / scale).toFloat()

    fun lineHeight(size: Float) = size * 1.25f

    private fun entry(text: String, size: Float, bold: Boolean): Entry {
        val key = "$scale|$size|$bold|$text"
        cache[key]?.let { return it }
        val font = font(size, bold)
        val metrics = measure.getFontMetrics(font)
        val width = ceil(metrics.stringWidth(text).toDouble()).toInt() + PADDING * 2
        val height = metrics.ascent + metrics.descent + PADDING * 2
        val image = BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().apply {
            hints()
            this.font = font
            color = java.awt.Color.WHITE
            drawString(text, PADDING.toFloat(), (PADDING + metrics.ascent).toFloat())
            dispose()
        }
        // White glyphs, the colour comes from the shader when drawing.
        val native = NativeImage(NativeImage.Format.RGBA, image.width, image.height, false)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val alpha = image.getRGB(x, y) ushr 24
                native.setPixelRGBA(x, y, (alpha shl 24) or 0xFFFFFF)
            }
        }
        val texture = DynamicTexture(native)
        texture.setFilter(true, false)
        val location = GenerationsCore.id("hd_text/${counter++}")
        Minecraft.getInstance().textureManager.register(location, texture)
        return Entry(location, image.width, image.height, metrics.ascent).also { cache[key] = it }
    }

    /**
     * Draws the text with its top at y. colour is ARGB. outline draws a dark border
     * around the letters (for text over pictures), shadow a soft drop shadow.
     */
    fun draw(
        graphics: GuiGraphics, text: String, x: Float, y: Float, size: Float, colour: Int,
        bold: Boolean = false, align: Align = Align.LEFT, shadow: Boolean = false, outline: Int? = null,
    ) {
        if (text.isEmpty()) return
        val alpha = (colour ushr 24) / 255f
        if (alpha <= 0.01f) return
        val left = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - width(text, size, bold) / 2f
            Align.RIGHT -> x - width(text, size, bold)
        }
        val entry = entry(text, size, bold)
        if (outline != null) {
            val o = 0.6f
            for ((dx, dy) in listOf(-o to 0f, o to 0f, 0f to -o, 0f to o, -o to -o, o to o, -o to o, o to -o)) {
                blit(graphics, entry, left + dx, y + dy, (outline and 0xFFFFFF) or ((alpha * ((outline ushr 24) / 255f) * 255).toInt() shl 24))
            }
        }
        if (shadow) blit(graphics, entry, left + 0.7f, y + 0.7f, ((alpha * 110).toInt() shl 24))
        blit(graphics, entry, left, y, colour)
    }

    fun draw(graphics: GuiGraphics, text: Component, x: Float, y: Float, size: Float, colour: Int, bold: Boolean = false, align: Align = Align.LEFT, shadow: Boolean = false, outline: Int? = null) =
        draw(graphics, text.string, x, y, size, colour, bold, align, shadow, outline)

    private fun blit(graphics: GuiGraphics, entry: Entry, x: Float, y: Float, colour: Int) {
        val s = scale.toFloat()
        RenderSystem.enableBlend()
        RenderSystem.setShaderColor(((colour shr 16) and 0xFF) / 255f, ((colour shr 8) and 0xFF) / 255f, (colour and 0xFF) / 255f, (colour ushr 24) / 255f)
        val pose = graphics.pose()
        pose.pushPose()
        // The texture has a margin: its glyphs start PADDING screen pixels in.
        pose.translate(x - PADDING / s, y - PADDING / s, 0f)
        pose.scale(1f / s, 1f / s, 1f)
        graphics.blit(entry.location, 0, 0, entry.width, entry.height, 0f, 0f, entry.width, entry.height, entry.width, entry.height)
        pose.popPose()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    }

    /** Splits the text into lines no wider than maxWidth. */
    fun wrap(text: String, maxWidth: Float, size: Float, bold: Boolean = false): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && width(candidate, size, bold) > maxWidth) {
                lines += line
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    /** Fits the text in maxWidth, ending with "…" when cut. */
    fun fit(text: String, maxWidth: Float, size: Float, bold: Boolean = false): String {
        if (width(text, size, bold) <= maxWidth) return text
        var cut = text
        while (cut.isNotEmpty() && width("$cut…", size, bold) > maxWidth) cut = cut.dropLast(1)
        return "$cut…"
    }

    /** Plain text and colour (ARGB, or null) of a Minecraft text line. */
    fun plain(sequence: FormattedCharSequence): Pair<String, Int?> {
        val builder = StringBuilder()
        var colour: Int? = null
        sequence.accept { _, style, codePoint ->
            if (colour == null && !Character.isWhitespace(codePoint)) style.color?.let { colour = 0xFF000000.toInt() or it.value }
            builder.appendCodePoint(codePoint)
            true
        }
        return builder.toString() to colour
    }
}
