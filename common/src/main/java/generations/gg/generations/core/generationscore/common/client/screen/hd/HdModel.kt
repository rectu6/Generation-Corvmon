package generations.gg.generations.core.generationscore.common.client.screen.hd

import com.cobblemon.mod.common.api.gui.drawPosablePortrait
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.ProfileTransformType
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexSorting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import kotlin.math.ceil

/**
 * Pokémon models in the HD interfaces: drawn off screen at twice the screen
 * resolution, then scaled down smoothly. Their outline is anti-aliased instead of
 * stair-stepped (the black raid silhouette shows it most). The rectangles are in
 * GUI coordinates, without transformation of the pose stack.
 */
object HdModel {
    private const val SUPERSAMPLE = 2
    private val targets = mutableMapOf<Long, TextureTarget>()

    private fun target(width: Int, height: Int): TextureTarget {
        val key = (width.toLong() shl 32) or height.toLong()
        return targets.getOrPut(key) {
            TextureTarget(width, height, true, Minecraft.ON_OSX).apply { setFilterMode(GL11.GL_LINEAR) }
        }
    }

    /** Renders draw() into the rectangle off screen, then shows it there. */
    fun offscreen(graphics: GuiGraphics, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f, draw: () -> Unit) {
        if (w <= 0f || h <= 0f || alpha <= 0.01f) return
        val minecraft = Minecraft.getInstance()
        val scale = minecraft.window.guiScale * SUPERSAMPLE
        val target = target(ceil(w * scale).toInt().coerceIn(1, 4096), ceil(h * scale).toInt().coerceIn(1, 4096))

        graphics.flush()
        target.setClearColor(0f, 0f, 0f, 0f)
        target.clear(Minecraft.ON_OSX)
        target.bindWrite(true)
        val projection = RenderSystem.getProjectionMatrix()
        val sorting = RenderSystem.getVertexSorting()
        RenderSystem.setProjectionMatrix(Matrix4f().setOrtho(x, x + w, y + h, y, 1000f, 21000f), VertexSorting.ORTHOGRAPHIC_Z)
        try {
            draw()
            graphics.flush()
        } finally {
            RenderSystem.setProjectionMatrix(projection, sorting)
            minecraft.mainRenderTarget.bindWrite(true)
        }

        // The picture has premultiplied colours: transparent pixels are black.
        RenderSystem.enableBlend()
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
        RenderSystem.setShaderTexture(0, target.colorTextureId)
        val matrix = graphics.pose().last().pose()
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        buffer.addVertex(matrix, x, y, 0f).setUv(0f, 1f).setColor(alpha, alpha, alpha, alpha)
        buffer.addVertex(matrix, x, y + h, 0f).setUv(0f, 0f).setColor(alpha, alpha, alpha, alpha)
        buffer.addVertex(matrix, x + w, y + h, 0f).setUv(1f, 0f).setColor(alpha, alpha, alpha, alpha)
        buffer.addVertex(matrix, x + w, y, 0f).setUv(1f, 1f).setColor(alpha, alpha, alpha, alpha)
        BufferUploader.drawWithShader(buffer.buildOrThrow())
        RenderSystem.defaultBlendFunc()
    }

    /**
     * The Pokémon standing, as in Cobblemon's summary: scale like ModelWidget's base
     * scale, offsetY from the top of the rectangle. silhouette draws it all black.
     */
    fun profile(
        graphics: GuiGraphics, pokemon: RenderablePokemon, state: FloatingState,
        x: Float, y: Float, w: Float, h: Float, scale: Float, rotationY: Float, offsetY: Float,
        partialTicks: Float, silhouette: Boolean = false, alpha: Float = 1f,
    ) = offscreen(graphics, x, y, w, h, alpha) {
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(x + w / 2f, y + offsetY, 0f)
        pose.scale(scale, scale, scale)
        val shade = if (silhouette) 0f else 1f
        if (silhouette) RenderSystem.setShaderColor(0f, 0f, 0f, 1f)
        drawProfilePokemon(
            renderablePokemon = pokemon,
            matrixStack = pose,
            rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13f, rotationY, 0f)),
            state = state,
            partialTicks = partialTicks,
            profileTransformType = ProfileTransformType.SUMMARY,
            r = shade, g = shade, b = shade,
        )
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        pose.popPose()
    }

    /** Head-and-shoulders portrait in a square, as on Cobblemon's battle tiles. */
    fun portrait(
        graphics: GuiGraphics, species: Species, state: FloatingState,
        x: Float, y: Float, size: Float, reversed: Boolean, partialTicks: Float, alpha: Float = 1f,
    ) = offscreen(graphics, x, y, size, size, alpha) {
        val pose = graphics.pose()
        val ratio = size / 28f
        pose.pushPose()
        pose.translate(x + size / 2f, y - 5f * ratio, 0f)
        pose.scale(ratio, ratio, 1f)
        drawPosablePortrait(
            identifier = species.resourceIdentifier,
            matrixStack = pose,
            scale = 18f,
            contextScale = species.getForm(state.currentAspects).baseScale,
            reversed = reversed,
            doQuirks = false,
            state = state,
            partialTicks = partialTicks,
        )
        pose.popPose()
    }
}
