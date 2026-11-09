package generations.gg.generations.core.generationscore.common.client.screen.hd

import com.mojang.blaze3d.systems.RenderSystem
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

/**
 * HD textures of the interfaces (tools/hd_textures.py, textures/gui/hd/): drawn at
 * four times their GUI size and scaled down smoothly. Panels are 9-slice: their
 * rounded corners keep their shape whatever the panel size.
 */
object HdGui {
    class Texture(val location: ResourceLocation, val width: Int, val height: Int)

    private fun hd(name: String, width: Int, height: Int) = Texture(GenerationsCore.id("textures/gui/hd/$name.png"), width, height)

    val LOBBY_BG = hd("lobby_bg", 1760, 992)
    val RESULT_BG = hd("result_bg", 1520, 920)
    val PANEL_LIGHT = hd("panel_light", 96, 96)
    val PANEL_DARK = hd("panel_dark", 96, 96)
    val PANEL_RED = hd("panel_red", 96, 96)
    val PANEL_OUTLINE = hd("panel_outline", 96, 96)
    val BAR = hd("bar", 64, 16)
    val STAR = hd("star", 64, 64)
    val GLOW = hd("glow", 128, 128)
    val SPARKLE = hd("sparkle", 32, 32)
    val PORTRAIT_RING = hd("portrait_ring", 64, 64)
    val GENDER_MALE = hd("gender_male", 48, 48)
    val GENDER_FEMALE = hd("gender_female", 48, 48)
    val BALL_TOP = hd("ball_top", 128, 128)
    val BALL_BASE = hd("ball_base", 128, 128)

    /** Weather and terrain badges, by Generations' condition names. */
    val CONDITIONS = mapOf(
        "Sun" to hd("weather_sun", 64, 64),
        "Desolate Land" to hd("weather_harsh_sun", 64, 64),
        "Rain" to hd("weather_rain", 64, 64),
        "Primordial Sea" to hd("weather_heavy_rain", 64, 64),
        "Sand" to hd("weather_sand", 64, 64),
        "Hail" to hd("weather_hail", 64, 64),
        "Snow" to hd("weather_hail", 64, 64),
        "Delta Stream" to hd("weather_wind", 64, 64),
        "Electric Terrain" to hd("terrain_electric", 64, 64),
        "Grassy Terrain" to hd("terrain_grassy", 64, 64),
        "Misty Terrain" to hd("terrain_misty", 64, 64),
        "Psychic Terrain" to hd("terrain_psychic", 64, 64),
    )

    private val smooth = mutableSetOf<ResourceLocation>()

    private fun prepare(texture: ResourceLocation) {
        if (smooth.add(texture)) Minecraft.getInstance().textureManager.getTexture(texture).setFilter(true, false)
    }

    fun colour(argb: Int) {
        RenderSystem.setShaderColor(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f, (argb ushr 24) / 255f)
    }

    /** The whole texture in the rectangle, tinted with an ARGB colour. */
    fun draw(graphics: GuiGraphics, texture: Texture, x: Float, y: Float, w: Float, h: Float, tint: Int = -1) {
        prepare(texture.location)
        RenderSystem.enableBlend()
        colour(tint)
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(x, y, 0f)
        pose.scale(w / texture.width, h / texture.height, 1f)
        graphics.blit(texture.location, 0, 0, texture.width, texture.height, 0f, 0f, texture.width, texture.height, texture.width, texture.height)
        pose.popPose()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    }

    /**
     * 9-slice panel: corners of corner GUI pixels taken from the texture's
     * texCorner pixels, edges and middle stretched.
     */
    fun panel(graphics: GuiGraphics, texture: Texture, x: Float, y: Float, w: Float, h: Float, tint: Int = -1, corner: Float = 8f, texCorner: Int = 32) {
        prepare(texture.location)
        RenderSystem.enableBlend()
        colour(tint)
        val c = corner.coerceAtMost(minOf(w, h) / 2f)
        val tw = texture.width
        val th = texture.height
        val xs = floatArrayOf(x, x + c, x + w - c, x + w)
        val ys = floatArrayOf(y, y + c, y + h - c, y + h)
        val us = intArrayOf(0, texCorner, tw - texCorner, tw)
        val vs = intArrayOf(0, texCorner, th - texCorner, th)
        for (i in 0..2) {
            for (j in 0..2) {
                val dw = xs[i + 1] - xs[i]
                val dh = ys[j + 1] - ys[j]
                if (dw <= 0f || dh <= 0f) continue
                val uw = us[i + 1] - us[i]
                val vh = vs[j + 1] - vs[j]
                val pose = graphics.pose()
                pose.pushPose()
                pose.translate(xs[i], ys[j], 0f)
                pose.scale(dw / uw, dh / vh, 1f)
                graphics.blit(texture.location, 0, 0, uw, vh, us[i].toFloat(), vs[j].toFloat(), uw, vh, tw, th)
                pose.popPose()
            }
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    }

    /** Rounded bar (3-slice): ends of half its height. */
    fun bar(graphics: GuiGraphics, x: Float, y: Float, w: Float, h: Float, tint: Int) {
        if (w <= 0.5f) return
        panel(graphics, BAR, x, y, w, h, tint, corner = (h / 2f).coerceAtMost(w / 2f), texCorner = 8)
    }

    /** HP bar: dark track, coloured fill (green, yellow, red). */
    fun healthBar(graphics: GuiGraphics, x: Float, y: Float, w: Float, h: Float, ratio: Float, alpha: Float = 1f, boss: Boolean = false) {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt() shl 24
        bar(graphics, x, y, w, h, a or 0x2A2A34)
        val r = ratio.coerceIn(0f, 1f)
        val fill = when {
            boss -> 0xF0305A
            r > 0.5f -> 0x4CD964
            r > 0.2f -> 0xF5C030
            else -> 0xEE4444
        }
        if (r > 0f) bar(graphics, x + 0.5f, y + 0.5f, ((w - 1f) * r).coerceAtLeast(h - 1f), h - 1f, a or fill)
    }

    fun stars(graphics: GuiGraphics, count: Int, x: Float, y: Float, size: Float, tint: Int, spacing: Float = 1f) {
        for (i in 0 until count) draw(graphics, STAR, x + i * (size + spacing), y, size, size, tint)
    }

    fun withAlpha(rgb: Int, alpha: Float) = ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or (rgb and 0xFFFFFF)

    fun isOver(mouseX: Double, mouseY: Double, x: Float, y: Float, w: Float, h: Float) =
        mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
}
