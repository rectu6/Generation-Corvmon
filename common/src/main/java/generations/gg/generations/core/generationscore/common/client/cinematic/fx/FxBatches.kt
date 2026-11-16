package generations.gg.generations.core.generationscore.common.client.cinematic.fx

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.ShaderInstance
import org.lwjgl.opengl.GL11

/**
 * One frame's vertices for one material (camera-relative positions, quads: a triangle is a quad
 * with its last corner twice). Kept from frame to frame, emptied each frame.
 */
class FxBuffer {
    var floats = FloatArray(8 * 1024)
        private set
    var colours = IntArray(1024)
        private set
    var count = 0
        private set

    fun clear() {
        count = 0
    }

    private fun grow() {
        floats = floats.copyOf(floats.size * 2)
        colours = colours.copyOf(colours.size * 2)
    }

    fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float, argb: Int, nx: Float = 0f, ny: Float = 1f, nz: Float = 0f) {
        if (count >= colours.size) grow()
        val i = count * 8
        floats[i] = x
        floats[i + 1] = y
        floats[i + 2] = z
        floats[i + 3] = u
        floats[i + 4] = v
        floats[i + 5] = nx
        floats[i + 6] = ny
        floats[i + 7] = nz
        colours[count] = argb
        count++
    }

    val quads: Int get() = count / 4
}

/**
 * How a batch is drawn. SOLID: the crystal shader, opaque, writes the depth (drawn first).
 * ALPHA: blended, soft against the world's depth. ADD: light, added (into the light target in
 * HIGH, for the bloom). The uniforms are this material's, set by the scene each frame.
 */
class FxMaterial(
    val name: String,
    val layer: Layer,
    val texture: CinematicTextures.Kind,
    val cull: Boolean = false,
) {
    enum class Layer { SOLID, ALPHA, ADD }

    /** FxNoise: scroll x, y (per second), scale, erosion (0 = none). */
    val noise = floatArrayOf(0f, 0f, 1f, 0f)

    /** FxShape: rim power, rim mix, soft depth distance, brightness. */
    val shape = floatArrayOf(0f, 0f, 0f, 1f)

    /** Crystals: CrystalTint (rgb, inner glow) and CrystalParams (refraction, rim power, brightness, dissolve). */
    val tint = floatArrayOf(0.62f, 0.86f, 1f, 1f)
    val crystal = floatArrayOf(0.03f, 2.5f, 1f, 0f)

    fun noise(sx: Float, sy: Float, scale: Float, erosion: Float): FxMaterial {
        noise[0] = sx; noise[1] = sy; noise[2] = scale; noise[3] = erosion
        return this
    }

    fun shape(rimPower: Float, rimMix: Float, soft: Float, brightness: Float): FxMaterial {
        shape[0] = rimPower; shape[1] = rimMix; shape[2] = soft; shape[3] = brightness
        return this
    }
}

/**
 * The frame's batches, by material, and how they are drawn: with our own shaders, one draw per
 * material, Minecraft's state kept in step (RenderSystem).
 */
class FxBatches {
    private val buffers = LinkedHashMap<FxMaterial, FxBuffer>()
    private val tesselator = Tesselator(1 shl 21)

    /** This frame's buffer for the material (emptied at the frame's start). */
    fun of(material: FxMaterial): FxBuffer = buffers.getOrPut(material) { FxBuffer() }

    fun clear() {
        for (buffer in buffers.values) buffer.clear()
    }

    fun has(layer: FxMaterial.Layer): Boolean = buffers.any { (m, b) -> m.layer == layer && b.count > 0 }

    /**
     * Every batch of a layer. depth: the world's depth copy (soft edges), 0 when there is none;
     * behind: the image before the effects (crystal refraction).
     */
    fun draw(layer: FxMaterial.Layer, time: Float, depth: Int, behind: Int) {
        for ((material, buffer) in buffers) {
            if (material.layer != layer || buffer.count < 4) continue
            val shader = (if (layer == FxMaterial.Layer.SOLID) CinematicShaders.crystal else CinematicShaders.fx) ?: continue
            drawBatch(material, buffer, shader, time, depth, behind)
        }
    }

    private fun drawBatch(material: FxMaterial, buffer: FxBuffer, shader: ShaderInstance, time: Float, depth: Int, behind: Int) {
        val builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL)
        val f = buffer.floats
        val c = buffer.colours
        val n = buffer.count - buffer.count % 4
        for (k in 0 until n) {
            val i = k * 8
            builder.addVertex(f[i], f[i + 1], f[i + 2]).setUv(f[i + 3], f[i + 4]).setColor(c[k]).setNormal(f[i + 5], f[i + 6], f[i + 7])
        }
        val mesh = builder.build() ?: return
        RenderSystem.setShader { shader }
        val noise = CinematicTextures.id(CinematicTextures.Kind.NOISE)
        if (material.layer == FxMaterial.Layer.SOLID) {
            RenderSystem.setShaderTexture(0, noise)
            RenderSystem.setShaderTexture(1, if (behind != 0) behind else CinematicTextures.id(CinematicTextures.Kind.WHITE))
            shader.safeGetUniform("CrystalTint").set(material.tint[0], material.tint[1], material.tint[2], material.tint[3])
            shader.safeGetUniform("CrystalParams").set(material.crystal[0], material.crystal[1], material.crystal[2], material.crystal[3])
            RenderSystem.disableBlend()
            RenderSystem.depthMask(true)
        } else {
            RenderSystem.setShaderTexture(0, CinematicTextures.id(material.texture))
            RenderSystem.setShaderTexture(1, noise)
            RenderSystem.setShaderTexture(2, if (depth != 0) depth else CinematicTextures.id(CinematicTextures.Kind.WHITE))
            shader.safeGetUniform("FxNoise").set(material.noise[0], material.noise[1], material.noise[2], material.noise[3])
            val soft = if (depth != 0) material.shape[2] else 0f
            shader.safeGetUniform("FxShape").set(material.shape[0], material.shape[1], soft, material.shape[3])
            RenderSystem.enableBlend()
            if (material.layer == FxMaterial.Layer.ADD) {
                RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE)
            } else {
                RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE)
            }
            RenderSystem.depthMask(false)
        }
        shader.safeGetUniform("FxTime").set(time)
        if (material.cull) RenderSystem.enableCull() else RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.depthFunc(GL11.GL_LEQUAL)
        BufferUploader.drawWithShader(mesh)
    }
}
