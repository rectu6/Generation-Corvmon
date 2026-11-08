package generations.gg.generations.core.generationscore.common.client.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL13C
import org.lwjgl.opengl.GL20C

object RenderStateRecord {
    private var readOnly = false

    var blendEnabled: Boolean = false
    var srcRgb: Int = GL11C.GL_ONE
    var dstRgb: Int = GL11C.GL_ZERO
    var srcAlpha: Int = GL11C.GL_ONE
    var dstAlpha: Int = GL11C.GL_ZERO

    var depthTestEnabled: Boolean = false
    var depthMask: Boolean = true
    var depthFunc: Int = GL11C.GL_LESS

    var cullEnabled: Boolean = false

    private var program = 0
    private var activeTexture = GL13C.GL_TEXTURE0
    // Keep this in sync with the sampler slots in Pipelines.createShader.
    private val textures = IntArray(6)

    fun isActive() = readOnly

    fun push() {
        check(!readOnly) { "RareCandy render state is already saved" }
        program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)
        activeTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE)
        for (slot in textures.indices) {
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + slot)
            textures[slot] = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D)
        }
        GL13C.glActiveTexture(activeTexture)
        // Blend/depth/cull values are tracked by RenderSystemMixin. Freeze
        // those values while RareCandy changes the corresponding state.
        readOnly = true
    }

    fun pop() {
        readOnly = false

        // Blend
        if (blendEnabled) RenderSystem.enableBlend() else RenderSystem.disableBlend()
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)

        // Depth
        if (depthTestEnabled) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
        RenderSystem.depthMask(depthMask)
        RenderSystem.depthFunc(depthFunc)

        // Cull
        if (cullEnabled) RenderSystem.enableCull() else RenderSystem.disableCull()

        // RareCandy binds its own program and samplers. Restore both the
        // Minecraft cache and the GL bindings before Iris/Minecraft draws again.
        for (slot in textures.indices) {
            RenderSystem.activeTexture(GL13C.GL_TEXTURE0 + slot)
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + slot)
            RenderSystem.bindTexture(textures[slot])
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textures[slot])
        }
        RenderSystem.activeTexture(activeTexture)
        GL13C.glActiveTexture(activeTexture)
        // RareCandy switches programs with raw GL calls, which GlStateManager's
        // callers never see. Iris caches the program behind _glUseProgram and
        // skips a switch to 0 when it believes 0 is already bound, so the raw call
        // restores the real GL state and the second call resyncs that cache.
        GL20C.glUseProgram(program)
        GlStateManager._glUseProgram(program)
    }
}
