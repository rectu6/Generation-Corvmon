package generations.gg.generations.core.generationscore.common.client.cinematic.fx

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexSorting
import generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClientConfig
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.CinematicScene
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Grade
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.SceneFrame
import generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.FogRenderer
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11

/**
 * The cinematics' only drawing path, with or without a shader pack: right after
 * LevelRenderer.renderLevel (GameRendererMixin), over the final image of the world (Iris has
 * composited everything by then and replaces no shader), tested against its depth. The world
 * pass's camera rotation and projection are kept (LevelRendererMixin) and used again.
 *
 * Order: the crystals (opaque, refracting the image before them), a copy of the depth, the
 * blended sheets (soft against that depth), the light (added; in HIGH into its own target for
 * the bloom), then the final composition.
 */
object CinematicRenderer {
    private val batches = FxBatches()
    private val post = PostParams()
    private val grade = Grade()
    private val keptView = Matrix4f()
    private val keptProjection = Matrix4f()
    private var kept = false

    /** In the world's pass: its matrices for this frame. */
    @JvmStatic
    fun keep(frustum: Matrix4f, projection: Matrix4f) {
        keptView.set(frustum)
        keptProjection.set(projection)
        kept = true
    }

    /**
     * This frame's effects and composition. flash: the limited flash (0 to 1); presence: how
     * much of the scene's look there is (fades in and out with it).
     */
    fun render(scene: CinematicScene, frame: SceneFrame, config: CinematicClientConfig, flash: Double, presence: Double) {
        if (!kept) return
        kept = false
        val minecraft = Minecraft.getInstance()
        val main = minecraft.mainRenderTarget
        val stack = RenderSystem.getModelViewStack()
        val previousProjection = Matrix4f(RenderSystem.getProjectionMatrix())
        val previousSorting = RenderSystem.getVertexSorting()
        stack.pushMatrix()
        try {
            main.bindWrite(true)
            UltraWarpRideClient.restoreWriteMasks()
            UltraWarpRideClient.clearTextureUnits()
            RenderSystem.setProjectionMatrix(Matrix4f(keptProjection), VertexSorting.DISTANCE_TO_ORIGIN)
            stack.identity()
            stack.mul(keptView)
            RenderSystem.applyModelViewMatrix()
            FogRenderer.setupNoFog()
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

            frame.view.set(keptView)
            frame.projection.set(keptProjection)
            frame.aspect = main.width.toFloat() / maxOf(1, main.height)
            val targets = CinematicPost.ensure(main)
            val time = frame.a.toFloat()

            batches.clear()
            if (CinematicShaders.fx != null || CinematicShaders.crystal != null) scene.draw(frame, batches)
            if (targets && batches.has(FxMaterial.Layer.SOLID)) CinematicPost.copyBefore(main)
            batches.draw(FxMaterial.Layer.SOLID, time, 0, CinematicPost.beforeTexture)
            if (targets) CinematicPost.copyDepth(main)
            val depth = CinematicPost.depthCopy
            batches.draw(FxMaterial.Layer.ALPHA, time, depth, 0)
            val toLight = config.high && targets && CinematicPost.canCompose() && batches.has(FxMaterial.Layer.ADD) && CinematicPost.beginLight()
            // Into the light target the depth copy is attached: it cannot be read as well.
            batches.draw(FxMaterial.Layer.ADD, time, if (toLight) 0 else depth, 0)
            if (toLight) main.bindWrite(true)

            if (targets) {
                post.reset()
                scene.post(frame, post)
                scene.script.grade(frame.t, grade)
                val p = presence.coerceIn(0.0, 1.0)
                post.r = (1.0 + (grade.r - 1.0) * p).toFloat()
                post.g = (1.0 + (grade.g - 1.0) * p).toFloat()
                post.b = (1.0 + (grade.b - 1.0) * p).toFloat()
                post.exposure = (1.0 + (grade.exposure - 1.0) * p).toFloat()
                post.saturation = (1.0 + (grade.saturation - 1.0) * p).toFloat()
                post.contrast = (1.0 + (grade.contrast - 1.0) * p).toFloat()
                post.vignette = (grade.vignette * p).toFloat()
                post.aberration *= p.toFloat()
                post.flash = flash.toFloat()
                if (!config.high) post.bloom = 0f
                CinematicPost.compose(main, post, config.quality.ordinal, time, toLight)
            }
        } finally {
            RenderSystem.disableBlend()
            RenderSystem.defaultBlendFunc()
            RenderSystem.enableDepthTest()
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            RenderSystem.depthMask(true)
            RenderSystem.enableCull()
            main.bindWrite(true)
            stack.popMatrix()
            RenderSystem.applyModelViewMatrix()
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting)
        }
    }

    fun reset() {
        kept = false
        batches.clear()
    }
}
