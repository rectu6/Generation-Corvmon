package generations.gg.generations.core.generationscore.common.client.render.block.entity

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.google.gson.JsonParser
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import kotlin.math.sin

/**
 * The raid den's 3D model: a ring of rocks around a pit, about 3 blocks wide, drawn
 * around the den block (RaidDenRenderer) and as its item. The mesh comes from the den
 * model raid_den.bmd, converted by tools/raid_den_model.py; texture den_model.png.
 * An active den's pit glows in the colour of its beam.
 */
object RaidDenModel {
    private val MESH = GenerationsCore.id("raid_den/raid_den_mesh.json")
    private val TEXTURE = GenerationsCore.id("textures/block/raid_den/den_model.png")
    private val GLOW = GenerationsCore.id("textures/block/raid_den/glow.png")

    /** Model units to blocks: the model is about 5 units wide. */
    const val SCALE = 0.6f
    /** One vertex: x, y, z, normal x, y, z, u, v. */
    private const val STRIDE = 8

    private class Mesh(val triangles: FloatArray, val pitMinX: Float, val pitMaxX: Float, val pitMinZ: Float, val pitMaxZ: Float, val pitTop: Float)

    private var mesh: Mesh? = null
    private var failed = false

    private fun mesh(): Mesh? {
        mesh?.let { return it }
        if (failed) return null
        return try {
            val json = Minecraft.getInstance().resourceManager.getResourceOrThrow(MESH).openAsReader().use { JsonParser.parseReader(it).asJsonObject }
            val data = ArrayList<Float>()
            val pit = ArrayList<Float>()
            json.getAsJsonArray("triangles").forEach { element ->
                val triangle = element.asJsonObject
                val values = triangle.getAsJsonArray("v").map { it.asFloat }
                data += values
                if (triangle.get("pit")?.asBoolean == true) pit += values
            }
            val xs = pit.indices.step(STRIDE).map { pit[it] }
            val ys = pit.indices.step(STRIDE).map { pit[it + 1] }
            val zs = pit.indices.step(STRIDE).map { pit[it + 2] }
            Mesh(
                data.toFloatArray(),
                xs.minOrNull() ?: -1f, xs.maxOrNull() ?: 1f, zs.minOrNull() ?: -1f, zs.maxOrNull() ?: 1f, ys.maxOrNull() ?: 0.05f,
            ).also { mesh = it }
        } catch (e: Exception) {
            failed = true
            GenerationsCore.LOGGER.error("Raid den model {} could not be loaded", MESH, e)
            null
        }
    }

    /**
     * The den with its base on the origin of [pose] (the bottom centre of the den
     * block). [glowColor]: the beam's colour of an active den, null when inactive.
     */
    fun render(pose: PoseStack, buffers: MultiBufferSource, light: Int, overlay: Int, glowColor: Int?, time: Float) {
        val mesh = mesh() ?: return
        pose.pushPose()
        pose.scale(SCALE, SCALE, SCALE)
        val last = pose.last()
        triangles(buffers.getBuffer(RenderType.entityCutout(TEXTURE)), last, mesh.triangles, light, overlay)
        if (glowColor != null) {
            // The pit lit from inside, slowly pulsing.
            val alpha = ((0.7f + 0.25f * sin(time * 0.1f)) * 255).toInt()
            val colour = (alpha shl 24) or (glowColor and 0xFFFFFF)
            val y = mesh.pitTop + 0.02f
            val glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW))
            vertex(glow, last, mesh.pitMinX, y, mesh.pitMinZ, 0f, 0f, colour, overlay)
            vertex(glow, last, mesh.pitMinX, y, mesh.pitMaxZ, 0f, 1f, colour, overlay)
            vertex(glow, last, mesh.pitMaxX, y, mesh.pitMaxZ, 1f, 1f, colour, overlay)
            vertex(glow, last, mesh.pitMaxX, y, mesh.pitMinZ, 1f, 0f, colour, overlay)
        }
        pose.popPose()
    }

    /** Triangles drawn as quads whose last corner repeats the third one. */
    private fun triangles(consumer: VertexConsumer, last: PoseStack.Pose, data: FloatArray, light: Int, overlay: Int) {
        var i = 0
        while (i + 3 * STRIDE <= data.size) {
            for (corner in 0..3) {
                val o = i + minOf(corner, 2) * STRIDE
                consumer.addVertex(last, data[o], data[o + 1], data[o + 2])
                    .setColor(-1)
                    .setUv(data[o + 6], data[o + 7])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(last, data[o + 3], data[o + 4], data[o + 5])
            }
            i += 3 * STRIDE
        }
    }

    private fun vertex(consumer: VertexConsumer, last: PoseStack.Pose, x: Float, y: Float, z: Float, u: Float, v: Float, colour: Int, overlay: Int) {
        consumer.addVertex(last, x, y, z)
            .setColor(colour)
            .setUv(u, v)
            .setOverlay(overlay)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(last, 0f, 1f, 0f)
    }
}
