package generations.gg.generations.core.generationscore.common.client.rks

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClient
import generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClientConfig
import generations.gg.generations.core.generationscore.common.client.cinematic.actor.CinematicActor
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorPose
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.MewtwoScript
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * The RKS Machine on the client (RKS-PLAN.md § 3.3 and § 5.1): what is drawn after the Pokémon
 * of the world's pass (the glass, the liquid, its bubbles: over the Mewtwo of the tank, which
 * RareCandy draws in its own pass, LevelRendererMixin), each machine's moving parts, and the
 * quality (cinematics_client.json's: LOW, MEDIUM, HIGH).
 */
object RksMachineClient {
    /** One machine's glass and liquid for this frame, queued by its renderer. */
    class Job(
        val origin: Vec3,
        val yaw: Float,
        val modules: Int,
        val look: RksMachineModel.Look,
        /** Liquid level (0 none, 1 full), its murk (0 clear, 1 opaque), its turbulence, its colour. */
        val liquid: Float,
        val murk: Float,
        val turbulence: Float,
        val liquidColour: Int,
        val bubbles: Int,
        val time: Float,
        /** The assembly's light sweep, from 0 (the ground) to 1 (the top); negative: none. */
        val sweep: Float = -1f,
    )

    private val jobs = ArrayList<Job>()

    /** Each machine's moving parts and Mewtwo's model in its tank. */
    class Anim {
        val rings = FloatArray(3)
        var lastNanos = 0L
        var actor: CinematicActor? = null
        var particleAt = 0L
    }

    private val anims = HashMap<BlockPos, Anim>()

    fun anim(pos: BlockPos): Anim {
        if (anims.size > 512) anims.clear()
        return anims.getOrPut(pos.immutable()) { Anim() }
    }

    fun forget(pos: BlockPos) {
        anims.remove(pos)
    }

    private var quality: CinematicClientConfig.Quality? = null

    /** The quality of the machine's effects (cinematics_client.json, read once). */
    fun quality(): CinematicClientConfig.Quality =
        quality ?: runCatching { CinematicClientConfig.load().quality }.getOrDefault(CinematicClientConfig.Quality.HIGH).also { quality = it }

    fun queue(job: Job) {
        if (jobs.size < 64) jobs += job
    }

    /** Mewtwo's model (CinematicActor), at a world point, for the tank. */
    fun drawActor(anim: Anim, species: ResourceLocation, feet: Vector3d, yaw: Double, scale: Double, seconds: Float) {
        val actor = anim.actor?.takeIf { it.species == species } ?: CinematicActor(species, emptySet()).also { anim.actor = it }
        actor.render(feet, yaw, 0.0, 0.0, scale, ActorPose.HOVER, seconds)
    }

    /**
     * The playing cinematic, when it is this machine's awakening (its tank at origin): its action
     * time, else null.
     */
    fun sceneAction(origin: Vec3): Double? {
        val p = CinematicClient.playback ?: return null
        if (p.packet.scene != CinematicScenes.MEWTWO || p.packet.origin.distanceToSqr(origin) > 0.25) return null
        return if (p.sceneTime >= 0.0) p.frame.a else 0.0
    }

    /** The glass broke in this machine's scene. */
    fun brokenInScene(origin: Vec3): Boolean = (sceneAction(origin) ?: -1.0) >= MewtwoScript.BREAK

    // ---------------------------------------------------------------- after RareCandy's pass

    @JvmStatic
    fun afterRareCandy() {
        if (jobs.isEmpty()) return
        try {
            val minecraft = Minecraft.getInstance()
            val camera = minecraft.gameRenderer.mainCamera.position
            val buffers = minecraft.renderBuffers().bufferSource()
            val pose = PoseStack()
            val local = Vector3f()
            for (job in jobs) {
                pose.pushPose()
                RksMachineModel.frame(pose, job.origin.x - camera.x, job.origin.y - camera.y, job.origin.z - camera.z, job.yaw)
                // The camera in the model's frame (the inverse of the frame's rotation).
                val r = Math.toRadians(job.yaw.toDouble())
                val dx = camera.x - job.origin.x
                val dz = camera.z - job.origin.z
                local.set((cos(r) * dx + sin(r) * dz).toFloat(), (camera.y - job.origin.y).toFloat(), (-sin(r) * dx + cos(r) * dz).toFloat())
                if (job.liquid > 0.001f) liquid(pose, buffers.getBuffer(RenderType.entityTranslucent(RksMachineModel.ATLAS)), job, local)
                RksMachineModel.drawGlass(pose, buffers, job.modules, job.look, local)
                if (job.sweep >= 0f) sweep(pose, buffers.getBuffer(RenderType.eyes(RksMachineModel.ATLAS)), job.sweep)
                pose.popPose()
            }
            buffers.endBatch(RenderType.entityTranslucent(RksMachineModel.ATLAS))
            buffers.endBatch(RenderType.eyes(RksMachineModel.ATLAS))
        } catch (e: Exception) {
            if (!failed) GenerationsCore.LOGGER.error("RKS Machine: the glass could not be drawn", e)
            failed = true
        } finally {
            jobs.clear()
        }
    }

    private var failed = false

    // The liquid's region of the atlas (64, 192)-(192, 320).
    private const val LU0 = 64.5f / 512f
    private const val LU1 = 191.5f / 512f
    private const val LV0 = 192.5f / 512f
    private const val LV1 = 319.5f / 512f

    private fun vertex(c: VertexConsumer, last: PoseStack.Pose, x: Float, y: Float, z: Float, u: Float, v: Float, colour: Int, light: Int, nx: Float, ny: Float, nz: Float) {
        c.addVertex(last, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, nx, ny, nz)
    }

    /** The liquid's column: its side (the far half first), its moving surface, its bubbles. */
    private fun liquid(pose: PoseStack, c: VertexConsumer, job: Job, camera: Vector3f) {
        val last = pose.last()
        val segments = if (quality() == CinematicClientConfig.Quality.LOW) 16 else 28
        val r = 0.655f
        val y0 = RksMachineModel.GLASS_Y0 + 0.01f
        val top = y0 + (RksMachineModel.GLASS_Y1 - 0.12f - y0) * job.liquid
        val a = (0.35f + 0.6f * job.murk).coerceIn(0f, 1f)
        val colour = ((a * 255).toInt() shl 24) or (job.liquidColour and 0xFFFFFF)
        val light = job.look.light
        val t = job.time
        val wave = 0.015f + 0.06f * job.turbulence
        fun surface(angle: Float): Float = top + wave * sin(angle * 3f + t * 2.1f) + wave * 0.6f * sin(angle * 5f - t * 3.3f)
        val camAngle = kotlin.math.atan2(camera.x, camera.z)
        for (pass in 0..1) {
            for (i in 0 until segments) {
                val a0 = (i.toFloat() / segments) * 2f * Math.PI.toFloat()
                val a1 = ((i + 1).toFloat() / segments) * 2f * Math.PI.toFloat()
                val mid = (a0 + a1) / 2f
                var diff = mid - camAngle
                while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
                while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()
                val near = kotlin.math.abs(diff) < Math.PI / 2
                if ((pass == 0) == near) continue
                val x0 = r * sin(a0)
                val z0 = r * cos(a0)
                val x1 = r * sin(a1)
                val z1 = r * cos(a1)
                val s0 = surface(a0)
                val s1 = surface(a1)
                val u0 = LU0 + (LU1 - LU0) * (i.toFloat() / segments)
                val u1 = LU0 + (LU1 - LU0) * ((i + 1).toFloat() / segments)
                val vScroll = (t * 0.03f) % 1f
                val v0 = LV0 + (LV1 - LV0) * (0.1f + 0.5f * vScroll)
                val v1 = LV0 + (LV1 - LV0) * (0.4f + 0.5f * vScroll)
                vertex(c, last, x0, y0, z0, u0, v1, colour, light, sin(a0), 0f, cos(a0))
                vertex(c, last, x1, y0, z1, u1, v1, colour, light, sin(a1), 0f, cos(a1))
                vertex(c, last, x1, s1, z1, u1, v0, colour, light, sin(a1), 0f, cos(a1))
                vertex(c, last, x0, s0, z0, u0, v0, colour, light, sin(a0), 0f, cos(a0))
            }
            if (pass == 0) {
                // The surface between the far and the near halves.
                val cu = (LU0 + LU1) / 2f
                val cv = (LV0 + LV1) / 2f
                val brighter = ((minOf(255, (a * 255).toInt() + 20)) shl 24) or (job.liquidColour and 0xFFFFFF)
                for (i in 0 until segments) {
                    val a0 = (i.toFloat() / segments) * 2f * Math.PI.toFloat()
                    val a1 = ((i + 1).toFloat() / segments) * 2f * Math.PI.toFloat()
                    vertex(c, last, 0f, top + wave * sin(t * 1.7f), 0f, cu, cv, brighter, light, 0f, 1f, 0f)
                    vertex(c, last, r * sin(a1), surface(a1), r * cos(a1), cu + 0.1f * sin(a1) * (LU1 - LU0), cv + 0.1f * cos(a1) * (LV1 - LV0), brighter, light, 0f, 1f, 0f)
                    vertex(c, last, r * sin(a0), surface(a0), r * cos(a0), cu + 0.1f * sin(a0) * (LU1 - LU0), cv + 0.1f * cos(a0) * (LV1 - LV0), brighter, light, 0f, 1f, 0f)
                    vertex(c, last, 0f, top + wave * sin(t * 1.7f), 0f, cu, cv, brighter, light, 0f, 1f, 0f)
                }
            }
        }
        // Bubbles rising (small bright quads facing the camera's side), fewer in LOW.
        val count = when (quality()) {
            CinematicClientConfig.Quality.LOW -> 0
            CinematicClientConfig.Quality.MEDIUM -> job.bubbles / 2
            else -> job.bubbles
        }
        if (count <= 0) return
        val glow = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.eyes(RksMachineModel.ATLAS))
        // A white texel of the atlas's light region.
        val bu = 480f / 512f
        val bv = 224f / 512f
        val bubbleColour = (0x70 shl 24) or 0xE8D8FF
        for (k in 0 until count) {
            val seed = k * 7919
            val speed = 0.25f + 0.35f * rand(seed, 1)
            val height = top - y0
            val phase = (t * speed / height + rand(seed, 2)) % 1f
            val y = y0 + phase * height
            val angle = rand(seed, 3) * 6.283f + t * 0.3f
            val radius = 0.1f + 0.45f * rand(seed, 4)
            val x = radius * sin(angle) + 0.03f * sin(t * 4f + k)
            val z = radius * cos(angle)
            val s = 0.012f + 0.025f * rand(seed, 5)
            // Facing the camera around y.
            val dx = camera.x - x
            val dz = camera.z - z
            val len = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(1e-3f)
            val rx = dz / len * s
            val rz = -dx / len * s
            vertex(glow, last, x - rx, y - s, z - rz, bu, bv, bubbleColour, LightTexture.FULL_BRIGHT, 0f, 1f, 0f)
            vertex(glow, last, x + rx, y - s, z + rz, bu, bv, bubbleColour, LightTexture.FULL_BRIGHT, 0f, 1f, 0f)
            vertex(glow, last, x + rx, y + s, z + rz, bu, bv, bubbleColour, LightTexture.FULL_BRIGHT, 0f, 1f, 0f)
            vertex(glow, last, x - rx, y + s, z - rz, bu, bv, bubbleColour, LightTexture.FULL_BRIGHT, 0f, 1f, 0f)
        }
    }

    /** A band of light rising round the machine while it assembles. */
    private fun sweep(pose: PoseStack, c: VertexConsumer, p: Float) {
        val last = pose.last()
        val y = 0.05f + 4.3f * p
        val alpha = (kotlin.math.sin(p * Math.PI.toFloat()) * 200).toInt().coerceIn(0, 255)
        val colour = (alpha shl 24) or 0x35E0FF
        val fade = 0x0035E0FF
        val u = 480f / 512f
        val v = 224f / 512f
        val segments = 40
        val r = 1.75f
        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * 2f * Math.PI.toFloat()
            val a1 = ((i + 1).toFloat() / segments) * 2f * Math.PI.toFloat()
            // Square-ish: the machine is 3 x 3.
            fun rx(a: Float) = r * (sin(a) / maxOf(kotlin.math.abs(sin(a)), kotlin.math.abs(cos(a)))).coerceIn(-1.05f, 1.05f) * 0.88f
            fun rz(a: Float) = r * (cos(a) / maxOf(kotlin.math.abs(sin(a)), kotlin.math.abs(cos(a)))).coerceIn(-1.05f, 1.05f) * 0.88f
            for ((y0, y1, c0, c1) in listOf(Quad(y - 0.25f, y, fade, colour), Quad(y, y + 0.08f, colour, fade))) {
                vertex(c, last, rx(a0), y0, rz(a0), u, v, c0, LightTexture.FULL_BRIGHT, 0f, 1f, 0f)
                vertex(c, last, rx(a1), y0, rz(a1), u, v, c0, LightTexture.FULL_BRIGHT, 0f, 1f, 0f)
                vertex(c, last, rx(a1), y1, rz(a1), u, v, c1, LightTexture.FULL_BRIGHT, 0f, 1f, 0f)
                vertex(c, last, rx(a0), y1, rz(a0), u, v, c1, LightTexture.FULL_BRIGHT, 0f, 1f, 0f)
            }
        }
    }

    private data class Quad(val y0: Float, val y1: Float, val c0: Int, val c1: Int)

    private fun rand(seed: Int, salt: Int): Float {
        var h = seed * 374761393 + salt * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777216f
    }
}
