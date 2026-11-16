package generations.gg.generations.core.generationscore.common.client.cinematic.fx

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ShaderInstance
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30

/**
 * The cinematics' render targets and full-screen passes, on the frame Minecraft (or the shader
 * pack's last pass) left in the main framebuffer:
 * - a copy of the image before the effects (the crystals' refraction) and of the depth (soft
 *   edges; made with the main depth's own format, whatever a shader pack chose);
 * - in HIGH, the effects' light drawn apart (tested against that depth), blurred at half and
 *   quarter size: the bloom;
 * - the final composition (CinematicShaders.post), in every quality (LOW: grade and flash only).
 * Made when first needed and when the window's size changes.
 */
object CinematicPost {
    private class ColourTarget(val internal: Int, val type: Int) {
        var texture = 0
        var fbo = 0
        var width = 0
        var height = 0
    }

    private val before = ColourTarget(GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE)
    private val frame = ColourTarget(GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE)
    private val light = ColourTarget(GL30.GL_RGBA16F, GL11.GL_FLOAT)
    private val halfA = ColourTarget(GL30.GL_RGBA16F, GL11.GL_FLOAT)
    private val halfB = ColourTarget(GL30.GL_RGBA16F, GL11.GL_FLOAT)
    private val quarterA = ColourTarget(GL30.GL_RGBA16F, GL11.GL_FLOAT)
    private val quarterB = ColourTarget(GL30.GL_RGBA16F, GL11.GL_FLOAT)
    private val colours = listOf(before, frame, light, halfA, halfB, quarterA, quarterB)

    private var depthTexture = 0
    private var depthFbo = 0
    private var depthFormat = 0
    private var width = 0
    private var height = 0

    /** The depth copy works (same format as the main one); the light target has it. */
    var depthReady = false
        private set
    var lightReady = false
        private set
    private var checked = false
    private var broken = false

    private val quad = Tesselator(4096)

    val beforeTexture: Int get() = if (before.fbo != 0) before.texture else 0
    val depthCopy: Int get() = if (depthReady) depthTexture else 0

    /** Targets at the main framebuffer's size. False when they cannot be made (then no copy, no post). */
    fun ensure(main: RenderTarget): Boolean {
        if (broken) return false
        val format = mainDepthFormat(main)
        if (main.width == width && main.height == height && format == depthFormat && before.fbo != 0) return true
        destroy()
        width = main.width
        height = main.height
        depthFormat = format
        return try {
            make(before, width, height)
            make(frame, width, height)
            makeDepth()
            make(light, width, height, depthTexture.takeIf { depthReady } ?: 0)
            lightReady = depthReady && light.fbo != 0
            make(halfA, maxOf(1, width / 2), maxOf(1, height / 2))
            make(halfB, maxOf(1, width / 2), maxOf(1, height / 2))
            make(quarterA, maxOf(1, width / 4), maxOf(1, height / 4))
            make(quarterB, maxOf(1, width / 4), maxOf(1, height / 4))
            checked = false
            true
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Legendary cinematics: the render targets could not be made, no post-processing", e)
            destroy()
            broken = true
            false
        } finally {
            main.bindWrite(true)
        }
    }

    private fun mainDepthFormat(main: RenderTarget): Int {
        val id = main.depthTextureId
        if (id <= 0) return 0
        GlStateManager._bindTexture(id)
        val format = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT)
        GlStateManager._bindTexture(0)
        return format
    }

    private fun make(target: ColourTarget, w: Int, h: Int, depth: Int = 0) {
        target.width = w
        target.height = h
        target.texture = GlStateManager._genTexture()
        GlStateManager._bindTexture(target.texture)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, target.internal, w, h, 0, GL11.GL_RGBA, target.type, null)
        GlStateManager._bindTexture(0)
        target.fbo = GlStateManager.glGenFramebuffers()
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.fbo)
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, target.texture, 0)
        if (depth != 0) GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth, 0)
        val status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GenerationsCore.LOGGER.warn("Legendary cinematics: a render target is incomplete ({}), left out", status)
            GlStateManager._glDeleteFramebuffers(target.fbo)
            target.fbo = 0
        }
    }

    private fun makeDepth() {
        depthReady = false
        if (depthFormat == 0) return
        val stencil = depthFormat == GL30.GL_DEPTH24_STENCIL8 || depthFormat == GL30.GL_DEPTH32F_STENCIL8
        val format = if (stencil) GL30.GL_DEPTH_STENCIL else GL11.GL_DEPTH_COMPONENT
        val type = when (depthFormat) {
            GL30.GL_DEPTH24_STENCIL8 -> GL30.GL_UNSIGNED_INT_24_8
            GL30.GL_DEPTH32F_STENCIL8 -> GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV
            else -> GL11.GL_FLOAT
        }
        depthTexture = GlStateManager._genTexture()
        GlStateManager._bindTexture(depthTexture)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, 0)
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, depthFormat, width, height, 0, format, type, null)
        GlStateManager._bindTexture(0)
        depthFbo = GlStateManager.glGenFramebuffers()
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthFbo)
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexture, 0)
        GL11.glDrawBuffer(GL11.GL_NONE)
        GL11.glReadBuffer(GL11.GL_NONE)
        depthReady = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE
        if (!depthReady) GenerationsCore.LOGGER.warn("Legendary cinematics: no depth copy (format {}), effects without soft edges", depthFormat)
    }

    private fun blit(from: Int, to: Int, mask: Int) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from)
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to)
        GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height, mask, GL11.GL_NEAREST)
    }

    /** The image before the effects (for the crystals). */
    fun copyBefore(main: RenderTarget) {
        if (before.fbo == 0) return
        blit(main.frameBufferId, before.fbo, GL11.GL_COLOR_BUFFER_BIT)
        main.bindWrite(true)
    }

    /** The depth as it is now (the world, then the crystals). The first time, checked: a mismatch leaves it out. */
    fun copyDepth(main: RenderTarget) {
        if (!depthReady) return
        if (!checked) for (i in 0 until 16) if (GlStateManager._getError() == 0) break // earlier errors, not ours
        blit(main.frameBufferId, depthFbo, GL11.GL_DEPTH_BUFFER_BIT)
        if (!checked) {
            checked = true
            val error = GlStateManager._getError()
            if (error != 0) {
                GenerationsCore.LOGGER.warn("Legendary cinematics: the depth cannot be copied (OpenGL error {}), effects without soft edges", error)
                depthReady = false
                lightReady = false
            }
        }
        main.bindWrite(true)
    }

    /** The final composition can be drawn (its shader and target exist): the light may go apart. */
    fun canCompose(): Boolean = CinematicShaders.post != null && frame.fbo != 0

    /** HIGH: the effects' light goes to its own target from now (cleared), tested against the depth copy. */
    fun beginLight(): Boolean {
        if (!lightReady) return false
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, light.fbo)
        GlStateManager._viewport(0, 0, width, height)
        RenderSystem.clearColor(0f, 0f, 0f, 0f)
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX)
        return true
    }

    private fun fullscreen(shader: ShaderInstance) {
        val builder = quad.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        builder.addVertex(-1f, -1f, 0f).setUv(0f, 0f)
        builder.addVertex(1f, -1f, 0f).setUv(1f, 0f)
        builder.addVertex(1f, 1f, 0f).setUv(1f, 1f)
        builder.addVertex(-1f, 1f, 0f).setUv(0f, 1f)
        RenderSystem.setShader { shader }
        BufferUploader.drawWithShader(builder.buildOrThrow())
    }

    private fun blur(shader: ShaderInstance, from: Int, fromWidth: Int, fromHeight: Int, to: ColourTarget, horizontal: Boolean, gain: Float) {
        if (to.fbo == 0) return
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, to.fbo)
        GlStateManager._viewport(0, 0, to.width, to.height)
        RenderSystem.setShaderTexture(0, from)
        if (horizontal) shader.safeGetUniform("BlurDir").set(1.5f / fromWidth, 0f) else shader.safeGetUniform("BlurDir").set(0f, 1.5f / fromHeight)
        shader.safeGetUniform("BlurGain").set(gain)
        fullscreen(shader)
    }

    /**
     * The final composition over the main framebuffer. quality: 0 low (grade and flash), 1 medium
     * (+ waves, heat), 2 high (+ light, bloom, rays, aberration, full grade).
     */
    fun compose(main: RenderTarget, p: PostParams, quality: Int, time: Float, lightDrawn: Boolean) {
        val shader = CinematicShaders.post ?: return
        if (frame.fbo == 0) return
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.disableBlend()
        RenderSystem.disableCull()
        blit(main.frameBufferId, frame.fbo, GL11.GL_COLOR_BUFFER_BIT)
        val white = CinematicTextures.id(CinematicTextures.Kind.WHITE)
        // The light target is read only when drawn this frame (never an old image).
        val high = quality >= 2 && lightReady && lightDrawn
        val blur = CinematicShaders.blur
        if (high && blur != null && p.bloom > 0f) {
            blur(blur, light.texture, width, height, halfA, true, 1f)
            blur(blur, halfA.texture, halfA.width, halfA.height, halfB, false, 1f)
            blur(blur, halfB.texture, halfB.width, halfB.height, quarterA, true, 1f)
            blur(blur, quarterA.texture, quarterA.width, quarterA.height, quarterB, false, 1f)
        }
        main.bindWrite(true)
        RenderSystem.setShaderTexture(0, frame.texture)
        RenderSystem.setShaderTexture(1, if (high) light.texture else white)
        RenderSystem.setShaderTexture(2, if (high && halfB.fbo != 0) halfB.texture else white)
        RenderSystem.setShaderTexture(3, if (high && quarterB.fbo != 0) quarterB.texture else white)
        RenderSystem.setShaderTexture(4, CinematicTextures.id(CinematicTextures.Kind.NOISE))
        shader.safeGetUniform("FxTime").set(time)
        shader.safeGetUniform("Quality").set(if (high) 2f else if (quality >= 1) 1f else 0f)
        shader.safeGetUniform("Aspect").set(width.toFloat() / maxOf(1, height))
        val waves = quality >= 1
        val w0 = p.waves[0]
        val w1 = p.waves[1]
        shader.safeGetUniform("Wave0").set(w0[0], w0[1], w0[2], if (waves) w0[3] else 0f)
        shader.safeGetUniform("Wave1").set(w1[0], w1[1], w1[2], if (waves) w1[3] else 0f)
        shader.safeGetUniform("WaveWidth").set(maxOf(w0[4], 1e-3f), maxOf(w1[4], 1e-3f))
        shader.safeGetUniform("Heat").set(if (waves) p.heat else 0f, p.heatSpeed, p.heatScale, p.heatFloor)
        shader.safeGetUniform("Tint").set(p.r, p.g, p.b, p.exposure)
        shader.safeGetUniform("Look").set(p.saturation, p.contrast, if (quality >= 1) p.vignette else 0f, if (high) p.aberration else 0f)
        shader.safeGetUniform("Flash").set(p.flashR, p.flashG, p.flashB, p.flash)
        shader.safeGetUniform("Rays").set(p.raysX, p.raysY, if (high) p.rays else 0f, p.raysDecay)
        shader.safeGetUniform("Glow").set(if (high) p.glow else 0f, if (high) p.bloom else 0f)
        shader.safeGetUniform("Radial").set(p.radialX, p.radialY, if (quality >= 2) p.radial else 0f, 0f)
        fullscreen(shader)
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(true)
        RenderSystem.enableCull()
    }

    fun destroy() {
        for (target in colours) {
            if (target.fbo != 0) GlStateManager._glDeleteFramebuffers(target.fbo)
            if (target.texture != 0) GlStateManager._deleteTexture(target.texture)
            target.fbo = 0
            target.texture = 0
        }
        if (depthFbo != 0) GlStateManager._glDeleteFramebuffers(depthFbo)
        if (depthTexture != 0) GlStateManager._deleteTexture(depthTexture)
        depthFbo = 0
        depthTexture = 0
        depthReady = false
        lightReady = false
        width = 0
        height = 0
    }
}
