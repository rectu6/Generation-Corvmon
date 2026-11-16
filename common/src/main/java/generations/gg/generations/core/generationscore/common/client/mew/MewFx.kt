package generations.gg.generations.core.generationscore.common.client.mew

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexSorting
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.CinematicShaders
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.CinematicTextures.Kind
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxBatches
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial.Layer
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxParticles
import generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient
import generations.gg.generations.core.generationscore.common.config.JsonConfigs
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.FogRenderer
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * config/generations/core/mew/mew_client.json: this player's side of Mew's appearances and game
 * (MEW-PLAN.md § 13). particleIntensity 0 to 2 (0: none), volume 0 to 1, manifestationVisuals
 * (false: the false appearances are heard, not seen), hud (the challenge's discreet timer).
 */
class MewClientConfig(
    var particleIntensity: Float = 1f,
    var volume: Float = 1f,
    var manifestationVisuals: Boolean = true,
    var hud: Boolean = true,
) {
    fun validate() {
        particleIntensity = JsonConfigs.clamp(particleIntensity, 0f, 2f, 1f)
        volume = JsonConfigs.clamp(volume, 0f, 1f, 1f)
    }

    fun particles(base: Int): Int = (base * particleIntensity).toInt()

    companion object {
        const val VERSION = 1

        fun load(): MewClientConfig = JsonConfigs.load(
            "Mew (client)", JsonConfigs.file("mew", "mew_client.json"), MewClientConfig::class.java, ::MewClientConfig, VERSION, { it.validate() },
        )
    }
}

/**
 * Mew's light drawing path (no post-processing): its motes, halos, trails and rings, right after
 * the world's render (with or without a shader pack, like the cinematics), against the world's
 * depth, with the cinematics' shaders and textures. The world pass's matrices are kept by
 * LevelRendererMixin.
 */
object MewFx {
    val pink = FxMeshes.argb(1f, 0.55f, 0.85f, 1f)
    val blue = FxMeshes.argb(0.5f, 0.75f, 1f, 1f)
    val white = FxMeshes.argb(1f, 0.92f, 1f, 1f)

    const val MOTE = 0
    const val SPARK = 1
    const val PUFF = 2

    private val particleMaterials = arrayOf(
        FxMaterial("mewfx_mote", Layer.ADD, Kind.HALO),
        FxMaterial("mewfx_spark", Layer.ADD, Kind.SPARK),
        FxMaterial("mewfx_puff", Layer.ALPHA, Kind.SMOKE),
    )
    val particles = FxParticles(3000, particleMaterials)
    val glows = FxMaterial("mewfx_glows", Layer.ADD, Kind.HALO)
    val trails = FxMaterial("mewfx_trails", Layer.ADD, Kind.STREAK)
    val rings = FxMaterial("mewfx_rings", Layer.ADD, Kind.RING).noise(0f, 0.3f, 4f, 0.4f)

    private val batches = FxBatches()
    private val keptView = Matrix4f()
    private val keptProjection = Matrix4f()
    private var kept = false
    val right = Vector3f()
    val up = Vector3f()

    fun keep(frustum: Matrix4f, projection: Matrix4f) {
        keptView.set(frustum)
        keptProjection.set(projection)
        kept = true
    }

    fun clear() {
        particles.clear()
        batches.clear()
        kept = false
    }

    /** A burst of motes at a world point. */
    fun burst(x: Double, y: Double, z: Double, count: Int, speed: Float, colour: Int, material: Int = MOTE, life: Float = 0.9f, size: Float = 0.1f, seed: Int = 0) {
        for (i in 0 until count) {
            val a = MewShow.random(seed + i, 11) * 2.0 * PI
            val b = (MewShow.random(seed + i, 13) - 0.5) * PI
            val sp = speed * (0.4 + MewShow.random(seed + i, 17).toFloat())
            particles.emit(
                material, x, y, z, (cos(b) * sin(a) * sp).toFloat(), (sin(b) * sp).toFloat(), (cos(b) * cos(a) * sp).toFloat(),
                life * (0.7f + 0.6f * MewShow.random(seed + i, 19).toFloat()), size, size * 0.2f, colour, drag = 2.4f,
            )
        }
    }

    /** One drifting mote. */
    fun mote(x: Double, y: Double, z: Double, colour: Int, vy: Float = 0.3f, life: Float = 1.6f, size: Float = 0.09f) {
        particles.emit(MOTE, x, y, z, 0f, vy, 0f, life, size, size * 0.3f, colour, drag = 0.6f)
    }

    /**
     * This frame: the particles moved by dt (seconds of the game, 0 while paused), then everything
     * drawn; extra draws the halos, trails and rings (camera-relative positions).
     */
    fun render(dt: Float, extra: (FxBatches, Double, Double, Double) -> Unit) {
        if (!kept) return
        kept = false
        particles.update(dt)
        val shader = CinematicShaders.fx ?: return
        val minecraft = Minecraft.getInstance()
        val camera = minecraft.gameRenderer.mainCamera
        val cam = camera.position
        right.set(camera.leftVector).negate()
        up.set(camera.upVector)
        batches.clear()
        extra(batches, cam.x, cam.y, cam.z)
        particles.render(batches, cam.x, cam.y, cam.z, right, up)
        if (!batches.has(Layer.ALPHA) && !batches.has(Layer.ADD)) return
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
            val time = ((minecraft.level?.gameTime ?: 0L) % 24000L) / 20f
            if (shader != null) {
                batches.draw(Layer.ALPHA, time, 0, 0)
                batches.draw(Layer.ADD, time, 0, 0)
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
}
