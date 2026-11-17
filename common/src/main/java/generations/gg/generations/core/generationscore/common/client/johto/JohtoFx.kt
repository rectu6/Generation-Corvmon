package generations.gg.generations.core.generationscore.common.client.johto

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
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
import generations.gg.generations.core.generationscore.common.network.packets.johto.S2CJohtoAltarPacket
import generations.gg.generations.core.generationscore.common.network.packets.johto.S2CJohtoPromptPacket
import generations.gg.generations.core.generationscore.common.network.packets.johto.S2CJohtoTrialPacket
import generations.gg.generations.core.generationscore.common.world.johto.JohtoBeast
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.FogRenderer
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The Legend of Johto's packets on the client (the packets hold no client class). */
object JohtoPacketHandlers {
    object Prompt : ClientNetworkPacketHandler<S2CJohtoPromptPacket> {
        override fun handle(packet: S2CJohtoPromptPacket, client: Minecraft) {
            client.execute { JohtoBeast.entries.getOrNull(packet.beast)?.let { client.setScreen(JohtoPromptScreen(packet.token, it)) } }
        }
    }

    object Altar : ClientNetworkPacketHandler<S2CJohtoAltarPacket> {
        override fun handle(packet: S2CJohtoAltarPacket, client: Minecraft) {
            client.execute { client.setScreen(JohtoAltarScreen(packet.token, packet.present, packet.reason)) }
        }
    }

    object Trial : ClientNetworkPacketHandler<S2CJohtoTrialPacket> {
        override fun handle(packet: S2CJohtoTrialPacket, client: Minecraft) {
            client.execute { JohtoClient.trial(packet) }
        }
    }
}

/**
 * The trials' light drawing path (no post-processing), like Mew's (MewFx): particles, halos,
 * flames, bolts, rings and marks on the ground, right after the world's render (with or without a
 * shader pack), against the world's depth. Only the player in a trial draws anything.
 */
object JohtoFx {
    const val SPARK = 0
    const val EMBER = 1
    const val MIST = 2
    const val MOTE = 3

    private val particleMaterials = arrayOf(
        FxMaterial("johtofx_spark", Layer.ADD, Kind.SPARK),
        FxMaterial("johtofx_ember", Layer.ADD, Kind.EMBER),
        FxMaterial("johtofx_mist", Layer.ALPHA, Kind.SMOKE),
        FxMaterial("johtofx_mote", Layer.ADD, Kind.HALO),
    )
    val particles = FxParticles(4000, particleMaterials)
    val glows = FxMaterial("johtofx_glows", Layer.ADD, Kind.HALO)
    val glowsThrough = FxMaterial("johtofx_glows_through", Layer.ADD, Kind.HALO, xray = true)
    val marks = FxMaterial("johtofx_marks", Layer.ADD, Kind.HALO)
    val streaks = FxMaterial("johtofx_streaks", Layer.ADD, Kind.STREAK)
    val rings = FxMaterial("johtofx_rings", Layer.ADD, Kind.RING).noise(0f, 0.3f, 4f, 0.4f)
    val fire = FxMaterial("johtofx_fire", Layer.ADD, Kind.SMOKE).noise(0f, 0.6f, 3f, 0.5f)
    val bolts = FxMaterial("johtofx_bolts", Layer.ADD, Kind.WHITE)
    val shade = FxMaterial("johtofx_shade", Layer.ALPHA, Kind.SMOKE)

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

    private fun random(i: Int, salt: Int): Double {
        var h = i * 374761393 + salt * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0x7FFFFFFF) / 2147483647.0
    }

    fun burst(x: Double, y: Double, z: Double, count: Int, speed: Float, colour: Int, material: Int = SPARK, life: Float = 0.9f, size: Float = 0.12f, seed: Int = 0) {
        for (i in 0 until count) {
            val a = random(seed + i, 11) * 2.0 * PI
            val b = (random(seed + i, 13) - 0.3) * PI
            val sp = speed * (0.4 + random(seed + i, 17).toFloat())
            particles.emit(
                material, x, y, z, (cos(b) * sin(a) * sp).toFloat(), (sin(b) * sp).toFloat(), (cos(b) * cos(a) * sp).toFloat(),
                life * (0.7f + 0.6f * random(seed + i, 19).toFloat()), size, size * 0.2f, colour, drag = 2.0f,
            )
        }
    }

    fun mote(material: Int, x: Double, y: Double, z: Double, colour: Int, vx: Float = 0f, vy: Float = 0.4f, vz: Float = 0f, life: Float = 1.4f, size: Float = 0.12f) {
        particles.emit(material, x, y, z, vx, vy, vz, life, size, size * 0.3f, colour, drag = 0.5f)
    }

    /** A colour with its alpha. */
    fun a(rgb: Int, alpha: Float): Int = FxMeshes.withAlpha(rgb, alpha)

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
