package generations.gg.generations.core.generationscore.common.client.rks

import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksModule
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * The RKS Machine's 3D model (RKS-PLAN.md § 3.3, D8): the mesh made by tools/rks_machine_model.py
 * (rks/rks_machine_mesh.json) on the atlas painted by tools/RksArt.java, read once. Drawn with
 * Minecraft's own render types, in the world's pass: it works without a shader pack and under
 * Iris (lit parts entityCutoutNoCull, lights full bright, glows added with eyes, glass
 * entityTranslucent sorted back to front, drawn after the Pokémon of the tank, RksMachineClient).
 *
 * The model's frame: origin at the bottom centre of the chamber, x to the right seen from the
 * front, y up, z toward the front. Every module's parts are in that frame, so a module alone, its
 * hologram and the assembled machine are drawn the same way.
 */
object RksMachineModel {
    private val MESH = GenerationsCore.id("rks/rks_machine_mesh.json")
    val ATLAS = GenerationsCore.id("textures/block/rks/rks_machine.png")

    enum class Material { METAL, GLOW, ADD, GLASS }

    /** The links (cables, floor plate) belong to the assembled machine only. */
    const val LINK_BIT = 1 shl 5
    const val ALL_MODULES = (1 shl 6) - 1

    class Group(
        val name: String,
        val moduleBit: Int,
        val material: Material,
        /** 0 both, 1 intact only, 2 damaged only. */
        val variant: Int,
        val anim: String,
        val pivot: Vector3f,
        val axis: Vector3f,
        val detail: Boolean,
        val data: FloatArray,
    ) {
        val triangles: Int get() = data.size / 24
        /** Each triangle's centre (x, y, z), for sorting the glass. */
        val centres: FloatArray = FloatArray(triangles * 3).also { c ->
            for (t in 0 until triangles) for (k in 0..2) c[t * 3 + k] = (data[t * 24 + k] + data[t * 24 + 8 + k] + data[t * 24 + 16 + k]) / 3f
        }
    }

    class Mesh(val groups: List<Group>, val bounds: Map<Int, FloatArray>)

    private var mesh: Mesh? = null
    private var failed = false

    fun mesh(): Mesh? {
        mesh?.let { return it }
        if (failed) return null
        return try {
            val json = Minecraft.getInstance().resourceManager.getResourceOrThrow(MESH).openAsReader().use { JsonParser.parseReader(it).asJsonObject }
            val groups = json.getAsJsonArray("groups").map { element ->
                val o = element.asJsonObject
                val module = when (o.get("module").asString) {
                    "console" -> RksModule.CONSOLE.bit
                    "chamber" -> RksModule.CHAMBER.bit
                    "reactor" -> RksModule.REACTOR.bit
                    "power" -> RksModule.POWER.bit
                    "analyzer" -> RksModule.ANALYZER.bit
                    else -> LINK_BIT
                }
                val material = when (o.get("material").asString) {
                    "glow" -> Material.GLOW
                    "add" -> Material.ADD
                    "glass" -> Material.GLASS
                    else -> Material.METAL
                }
                val variant = when (o.get("variant").asString) {
                    "intact" -> 1
                    "damaged" -> 2
                    else -> 0
                }
                val pivot = o.getAsJsonArray("pivot").map { it.asFloat }
                val axis = o.getAsJsonArray("axis").map { it.asFloat }
                val v = o.getAsJsonArray("v")
                val data = FloatArray(v.size()) { v[it].asFloat }
                Group(o.get("name").asString, module, material, variant, o.get("anim").asString, Vector3f(pivot[0], pivot[1], pivot[2]), Vector3f(axis[0], axis[1], axis[2]), o.get("detail").asInt > 0, data)
            }
            val bounds = HashMap<Int, FloatArray>()
            for (g in groups) {
                if (g.variant == 2) continue
                val b = bounds.getOrPut(g.moduleBit) { floatArrayOf(1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f) }
                var i = 0
                while (i < g.data.size) {
                    for (k in 0..2) {
                        b[k] = minOf(b[k], g.data[i + k])
                        b[k + 3] = maxOf(b[k + 3], g.data[i + k])
                    }
                    i += 8
                }
            }
            Mesh(groups, bounds).also { mesh = it }
        } catch (e: Exception) {
            failed = true
            GenerationsCore.LOGGER.error("RKS Machine model {} could not be loaded", MESH, e)
            null
        }
    }

    /** How the machine looks this frame (colours ARGB, angles in degrees, offsets in blocks). */
    class Look {
        var damaged = false
        var lights = 0xFF35E0FF.toInt()
        var core = 0xFF6FE8FF.toInt()
        var screens = 0xFFFFFFFF.toInt()
        var screenRed = false
        val rings = FloatArray(3)
        var arm = 0f
        var scan = 0f
        var scanOn = false
        /** Light of the lit parts (packed). */
        var light = LightTexture.FULL_BRIGHT
        var detail = true
        /** Fraction of the links' triangles drawn (the cables grow while assembling). */
        var links = 1f
        /** The floor plate rising from under the ground while assembling. */
        var linkDrop = 0f
        /** Repair: the intact glass drawn up to this fraction of its height (1 = whole). */
        var glassRise = 1f
        /** Repair: the shards rising back (0 on the ground). */
        var shardRise = 0f
        var overlay = OverlayTexture.NO_OVERLAY

        fun set(other: Look): Look {
            damaged = other.damaged; lights = other.lights; core = other.core; screens = other.screens; screenRed = other.screenRed
            other.rings.copyInto(rings)
            arm = other.arm; scan = other.scan; scanOn = other.scanOn; light = other.light; detail = other.detail
            links = other.links; linkDrop = other.linkDrop; glassRise = other.glassRise; shardRise = other.shardRise; overlay = other.overlay
            return this
        }
    }

    private val rotation = Quaternionf()

    private fun visible(g: Group, modules: Int, look: Look): Boolean {
        if (g.moduleBit and modules == 0) return false
        if (g.detail && !look.detail) return false
        return when (g.variant) {
            1 -> !look.damaged
            2 -> look.damaged
            else -> true
        }
    }

    /** The group's own movement (rings, arm, scan line, the assembly's plate). */
    private fun place(pose: PoseStack, g: Group, look: Look) {
        when (g.anim) {
            "ring0", "ring1", "ring2" -> {
                val angle = look.rings[g.anim[4] - '0']
                if (angle != 0f) {
                    pose.translate(g.pivot.x, g.pivot.y, g.pivot.z)
                    rotation.identity().rotateAxis(Math.toRadians(angle.toDouble()).toFloat(), g.axis.x, g.axis.y, g.axis.z)
                    pose.mulPose(rotation)
                    pose.translate(-g.pivot.x, -g.pivot.y, -g.pivot.z)
                }
            }
            "arm" -> pose.translate(0f, 0f, look.arm)
            "scan" -> pose.translate(0f, 0f, look.scan)
        }
        if (g.moduleBit == LINK_BIT && look.linkDrop != 0f) pose.translate(0f, -look.linkDrop, 0f)
    }

    private fun colour(g: Group, look: Look): Int = when (g.anim) {
        "lights", "scan" -> look.lights
        "core" -> look.core
        "screen" -> look.screens
        else -> if (g.material == Material.GLOW && g.anim.startsWith("ring")) look.lights else -1
    }

    /** The opaque parts and the lights of these modules (a mask of RksModule bits and LINK_BIT). */
    fun drawSolid(pose: PoseStack, buffers: MultiBufferSource, modules: Int, look: Look) {
        val mesh = mesh() ?: return
        val lit = buffers.getBuffer(RenderType.entityCutoutNoCull(ATLAS))
        for (g in mesh.groups) {
            if (g.material != Material.METAL || !visible(g, modules, look)) continue
            draw(pose, lit, g, look, -1, look.light, look.overlay)
        }
        val bright = buffers.getBuffer(RenderType.entityCutoutNoCull(ATLAS))
        for (g in mesh.groups) {
            if (g.material != Material.GLOW || !visible(g, modules, look)) continue
            if (g.anim == "scan" && !look.scanOn) continue
            draw(pose, bright, g, look, colour(g, look), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY)
        }
    }

    private val keys = LongArray(4096)

    /**
     * The glass of these modules, back to front from the camera (camera: in the model's frame;
     * null keeps the mesh's order, for the items).
     */
    fun drawGlass(pose: PoseStack, buffers: MultiBufferSource, modules: Int, look: Look, camera: Vector3f?) {
        val mesh = mesh() ?: return
        val consumer = buffers.getBuffer(RenderType.entityTranslucent(ATLAS))
        for (g in mesh.groups) {
            if (g.material != Material.GLASS || !visible(g, modules, look)) continue
            pose.pushPose()
            var fraction = 1f
            if (g.variant == 1 && look.glassRise < 1f) {
                // Repair: the glass closing again from its base.
                if (look.glassRise <= 0f) {
                    pose.popPose()
                    continue
                }
                pose.translate(0f, GLASS_Y0, 0f)
                pose.scale(1f, look.glassRise, 1f)
                pose.translate(0f, -GLASS_Y0, 0f)
            }
            if (g.variant == 2 && look.shardRise > 0f) pose.translate(0f, look.shardRise, 0f)
            val last = pose.last()
            val colour = -1
            if (camera == null || g.triangles > keys.size) {
                emit(consumer, last, g.data, 0, (g.triangles * fraction).toInt(), colour, look.light, look.overlay, 0f)
            } else {
                val n = g.triangles
                for (t in 0 until n) {
                    val dx = g.centres[t * 3] - camera.x
                    val dy = g.centres[t * 3 + 1] - camera.y
                    val dz = g.centres[t * 3 + 2] - camera.z
                    val d = dx * dx + dy * dy + dz * dz
                    keys[t] = ((0x7FFFFFFFL - java.lang.Float.floatToIntBits(d)) shl 32) or t.toLong()
                }
                java.util.Arrays.sort(keys, 0, n)
                for (k in 0 until n) emit(consumer, last, g.data, (keys[k] and 0xFFFFFFFFL).toInt(), 1, colour, look.light, look.overlay, 0f)
            }
            pose.popPose()
        }
    }

    /** A hologram of these modules: every part added in a pale colour (D7). */
    fun drawGhost(pose: PoseStack, buffers: MultiBufferSource, modules: Int, colour: Int) {
        val mesh = mesh() ?: return
        val consumer = buffers.getBuffer(RenderType.eyes(ATLAS))
        for (g in mesh.groups) {
            if (g.moduleBit and modules == 0 || g.variant == 2 || g.detail) continue
            val last = pose.last()
            emit(consumer, last, g.data, 0, g.triangles, colour, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0f)
        }
    }

    private fun draw(pose: PoseStack, consumer: VertexConsumer, g: Group, look: Look, colour: Int, light: Int, overlay: Int) {
        pose.pushPose()
        place(pose, g, look)
        val count = if (g.moduleBit == LINK_BIT && look.links < 1f) (g.triangles * look.links.coerceIn(0f, 1f)).toInt() else g.triangles
        // The screens show the alarm region of the atlas (next to theirs) when red.
        val du = if (g.anim == "screen" && look.screenRed) SCREEN_RED_U else 0f
        emit(consumer, pose.last(), g.data, 0, count, colour, light, overlay, du)
        pose.popPose()
    }

    /** Triangles (from the first one, count of them) as quads whose last corner repeats the third. */
    private fun emit(consumer: VertexConsumer, last: PoseStack.Pose, data: FloatArray, first: Int, count: Int, colour: Int, light: Int, overlay: Int, du: Float) {
        var i = first * 24
        val end = minOf(data.size, (first + count) * 24)
        while (i + 24 <= end) {
            for (corner in 0..3) {
                val o = i + minOf(corner, 2) * 8
                consumer.addVertex(last, data[o], data[o + 1], data[o + 2])
                    .setColor(colour)
                    .setUv(data[o + 6] + du, data[o + 7])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(last, data[o + 3], data[o + 4], data[o + 5])
            }
            i += 24
        }
    }

    /** The model's frame on a block: origin (block units from the pose's origin), facing yaw (Minecraft's). */
    fun frame(pose: PoseStack, x: Double, y: Double, z: Double, yaw: Float) {
        pose.translate(x, y, z)
        pose.mulPose(Axis.YP.rotationDegrees(-yaw))
    }

    /** The bounds (min x, y, z, max x, y, z) of a module's parts, for its item. */
    fun bounds(bit: Int): FloatArray? = mesh()?.bounds?.get(bit)

    const val GLASS_Y0 = 0.55f
    const val GLASS_Y1 = 3.45f
    /** The atlas's red screen is 128 pixels right of the cyan one. */
    private const val SCREEN_RED_U = 128f / 512f
}
