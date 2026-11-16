package generations.gg.generations.core.generationscore.common.client.screen.hd

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.perf.FirstUseProfiler
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.util.FormattedCharSequence
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.util.concurrent.CompletableFuture
import kotlin.math.ceil

/**
 * Smooth text for the HD interfaces: drawn with a TrueType font of the system
 * (Bahnschrift, Segoe UI or Arial on Windows) by Java, at the real screen
 * resolution, instead of Minecraft's pixel font. Each text becomes a small texture,
 * kept in a cache. Sizes are in GUI pixels (10 is about Minecraft's font).
 *
 * Java's font system costs one to two seconds the first time it is used (font
 * manager, list of the system's fonts). [warmUp] does that on a background thread at
 * the end of the client's loading, so that the first HD screen (the Altar's menu, the
 * first battle) does not freeze; a screen opened before it is done waits only for the
 * rest. Fonts are kept per size, new texts are made within a time budget per frame
 * (the rest the next frames), and the cache is emptied when the GUI scale changes and
 * when the resources are reloaded.
 */
object HdText {
    enum class Align { LEFT, CENTER, RIGHT }

    private const val PADDING = 2
    private const val CACHE_SIZE = 600
    /** Render-thread time per frame for new text textures; the next ones wait a frame. */
    private const val FRAME_BUDGET_NANOS = 6_000_000L
    private val PREFERRED = listOf("Bahnschrift", "Segoe UI", "Arial", "Helvetica", "DejaVu Sans")

    // ---- Java's fonts, set up once on a background thread ----

    private class Awt(val family: String, val measure: Graphics2D)

    @Volatile private var warm: CompletableFuture<Awt>? = null
    private var awtCache: Awt? = null

    /** Starts Java's font system on a background thread (idempotent). */
    @JvmStatic
    fun warmUp() {
        if (warm != null) return
        synchronized(this) {
            if (warm != null) return
            val future = CompletableFuture<Awt>()
            val thread = Thread({ future.complete(initAwt()) }, "Generations HD text warm-up")
            thread.isDaemon = true
            thread.priority = Thread.MIN_PRIORITY + 1
            warm = future
            thread.start()
        }
    }

    private fun initAwt(): Awt {
        val start = System.nanoTime()
        val awt = try {
            // Font(name).family is "Dialog" for a font that is not installed: no need to
            // list the 300 families of the system (the slowest part).
            val family = PREFERRED.firstOrNull { name -> runCatching { Font(name, Font.PLAIN, 12).family.equals(name, ignoreCase = true) }.getOrDefault(false) } ?: Font.SANS_SERIF
            val measure = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().apply { hints() }
            // The glyphs most texts use, plain and bold, so that the first real text is quick.
            val sample = "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789 éèêàçùôî ÉÈÀ ×%/:.,!?-+()…"
            val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
            image.createGraphics().apply {
                hints()
                for (style in intArrayOf(Font.PLAIN, Font.BOLD)) for (size in floatArrayOf(16f, 24f, 32f)) {
                    font = Font(family, style, 1).deriveFont(size)
                    drawString(sample, 0f, 6f)
                    measure.getFontMetrics(font).stringWidth(sample)
                }
                dispose()
            }
            Awt(family, measure)
        } catch (e: Throwable) {
            GenerationsCore.LOGGER.error("HD text: could not prepare the fonts ({}), Java's default font used", e.toString())
            Awt(Font.SANS_SERIF, BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().apply { hints() })
        }
        GenerationsCore.LOGGER.info("HD text: fonts ready in {} ms on a background thread ({})", (System.nanoTime() - start) / 1_000_000, awt.family)
        return awt
    }

    private fun awt(): Awt {
        awtCache?.let { return it }
        warmUp()
        val future = warm!!
        val waited = !future.isDone
        val start = System.nanoTime()
        val awt = future.join()
        if (waited) FirstUseProfiler.note("HD text: waited ${(System.nanoTime() - start) / 1_000_000} ms for the font warm-up")
        awtCache = awt
        return awt
    }

    // ---- caches ----

    private class Entry(val location: ResourceLocation, val width: Int, val height: Int, val ascent: Int)

    private val cache = object : LinkedHashMap<String, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean {
            if (size <= CACHE_SIZE) return false
            Minecraft.getInstance().textureManager.release(eldest.value.location)
            return true
        }
    }
    private val fonts = HashMap<Long, Font>()
    private var counter = 0
    private var cacheScale = Double.NaN
    private var budgetFrame = -1L
    private var budgetUsed = 0L
    private var budgetSince = 0L

    /** Texts made into textures since the start (diagnostic, FirstUseProfiler). */
    var texturesMade = 0; private set
    var textureNanos = 0L; private set

    private fun Graphics2D.hints() {
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }

    private val scale: Double get() = Minecraft.getInstance().window.guiScale

    /** Releases every text texture (GUI scale changed, resources reloaded). */
    @JvmStatic
    fun clear() {
        val textures = Minecraft.getInstance().textureManager
        for (entry in cache.values) textures.release(entry.location)
        cache.clear()
        fonts.clear()
    }

    private fun checkScale() {
        val scale = scale
        if (scale != cacheScale) {
            if (!cacheScale.isNaN()) clear()
            cacheScale = scale
        }
    }

    private fun font(size: Float, bold: Boolean): Font {
        val pixels = (size * scale).toFloat()
        val key = (java.lang.Float.floatToIntBits(pixels).toLong() shl 1) or (if (bold) 1L else 0L)
        return fonts.getOrPut(key) { Font(awt().family, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(pixels) }
    }

    /** Width in GUI pixels. */
    fun width(text: String, size: Float, bold: Boolean = false): Float {
        checkScale()
        return (awt().measure.getFontMetrics(font(size, bold)).stringWidth(text) / scale).toFloat()
    }

    fun lineHeight(size: Float) = size * 1.25f

    /** Called at the start of each frame (MinecraftFrameMixin): a new time budget for new texts. */
    @JvmStatic
    fun onFrame(frame: Long) {
        if (frame != budgetFrame) {
            budgetFrame = frame
            budgetUsed = 0L
            budgetSince = System.nanoTime()
        }
    }

    /** The texture of this text, or null when this frame's budget is spent (drawn next frame). */
    private fun entry(text: String, size: Float, bold: Boolean): Entry? {
        checkScale()
        val key = "$size|$bold|$text"
        cache[key]?.let { return it }
        val start = System.nanoTime()
        // Safety: without a frame for 200 ms (no hook), no budget at all.
        if (budgetUsed >= FRAME_BUDGET_NANOS && start - budgetSince < 200_000_000L) return null
        val font = font(size, bold)
        val metrics = awt().measure.getFontMetrics(font)
        val width = (ceil(metrics.stringWidth(text).toDouble()).toInt() + PADDING * 2).coerceAtLeast(1)
        val height = (metrics.ascent + metrics.descent + PADDING * 2).coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().apply {
            hints()
            this.font = font
            color = java.awt.Color.WHITE
            drawString(text, PADDING.toFloat(), (PADDING + metrics.ascent).toFloat())
            dispose()
        }
        // White glyphs, the colour comes from the shader when drawing. The pixels are
        // read straight from the image's array (no colour conversion per pixel).
        val pixels = (image.raster.dataBuffer as DataBufferInt).data
        val native = NativeImage(NativeImage.Format.RGBA, width, height, false)
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                native.setPixelRGBA(x, y, ((pixels[index++] ushr 24) shl 24) or 0xFFFFFF)
            }
        }
        val texture = DynamicTexture(native)
        texture.setFilter(true, false)
        val location = GenerationsCore.id("hd_text/${counter++}")
        Minecraft.getInstance().textureManager.register(location, texture)
        val spent = System.nanoTime() - start
        budgetUsed += spent
        texturesMade++
        textureNanos += spent
        return Entry(location, width, height, metrics.ascent).also { cache[key] = it }
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
        val entry = entry(text, size, bold) ?: return
        val left = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - width(text, size, bold) / 2f
            Align.RIGHT -> x - width(text, size, bold)
        }
        if (outline != null) {
            val o = 0.6f
            for ((dx, dy) in OUTLINE) {
                blit(graphics, entry, left + dx * o, y + dy * o, (outline and 0xFFFFFF) or ((alpha * ((outline ushr 24) / 255f) * 255).toInt() shl 24))
            }
        }
        if (shadow) blit(graphics, entry, left + 0.7f, y + 0.7f, ((alpha * 110).toInt() shl 24))
        blit(graphics, entry, left, y, colour)
    }

    private val OUTLINE = listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f, -1f to -1f, 1f to 1f, -1f to 1f, 1f to -1f)

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

    /** Empties the cache when the resources are reloaded (registered by GenerationsCoreClient). */
    object Reloader : ResourceManagerReloadListener {
        override fun onResourceManagerReload(resourceManager: ResourceManager) = clear()
    }
}
