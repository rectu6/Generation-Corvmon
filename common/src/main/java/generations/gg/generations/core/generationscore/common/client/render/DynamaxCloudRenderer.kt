package generations.gg.generations.core.generationscore.common.client.render

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.battle.DynamaxVisuals
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.RareCandyModelSize
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Look of a Dynamaxed Pokémon (DynamaxVisuals.ASPECT) on the client:
 * - its model pulses red;
 * - red Dynamax clouds float above its head: three strings of puffs that curl
 *   around it, thinner towards their end, slowly turning and bobbing.
 * The clouds are a model built here from shaded spheres, sized to the Pokémon.
 */
object DynamaxCloudRenderer {
    private val TEXTURE = GenerationsCore.id("textures/entity/dynamax_cloud.png")
    private const val ARCS = 3
    private const val PUFFS = 7
    private const val RINGS = 7
    private const val SEGMENTS = 10

    // Colours of the puffs: lit top, red middle, dark underside.
    private val TOP = floatArrayOf(1f, 0.42f, 0.45f)
    private val MIDDLE = floatArrayOf(0.86f, 0.1f, 0.18f)
    private val BOTTOM = floatArrayOf(0.45f, 0.02f, 0.08f)

    /** One corner of the unit sphere: position (= normal) x, y, z, then colour r, g, b. */
    private const val CORNER = 6

    /**
     * The unit sphere as quads, four corners each, with their shading: computed once,
     * every puff only scales and moves it.
     */
    private val sphere: FloatArray = run {
        val rings = (0..RINGS).map { ring ->
            val polar = PI * ring / RINGS
            (0..SEGMENTS).map { segment ->
                val azimuth = 2 * PI * segment / SEGMENTS
                floatArrayOf((sin(polar) * cos(azimuth)).toFloat(), cos(polar).toFloat(), (sin(polar) * sin(azimuth)).toFloat())
            }
        }
        val corners = ArrayList<Float>(RINGS * SEGMENTS * 4 * CORNER)
        for (ring in 0 until RINGS) {
            for (segment in 0 until SEGMENTS) {
                for (point in listOf(rings[ring][segment], rings[ring + 1][segment], rings[ring + 1][segment + 1], rings[ring][segment + 1])) {
                    corners += point.toList()
                    corners += colour(point[1]).toList()
                }
            }
        }
        corners.toFloatArray()
    }

    /**
     * The entity's synced aspects: the client Pokémon only has those it appeared with,
     * so a Pokémon Dynamaxed meanwhile would stay plain until loaded again.
     */
    private fun isDynamaxed(entity: PokemonEntity): Boolean = DynamaxVisuals.ASPECT in entity.aspects

    /** Bedrock models: red tint, pulsing slowly. */
    fun tint(entity: PokemonEntity, model: PosableModel, partialTicks: Float) {
        if (!isDynamaxed(entity)) return
        val pulse = 0.5f + 0.5f * sin((entity.tickCount + partialTicks) / 20f * 2.5f)
        model.green = 0.25f + 0.12f * pulse
        model.blue = 0.32f + 0.12f * pulse
    }

    fun render(entity: PokemonEntity, partialTicks: Float, pose: PoseStack, buffers: MultiBufferSource) {
        if (!isDynamaxed(entity) || entity.isInvisible) return
        val (height, halfWidth) = size(entity, pose)
        // Overall size: tall and slim or long and flat, the clouds keep to the Pokémon.
        val size = maxOf(height, halfWidth * 2f)
        val ring = (halfWidth * 1.1f).coerceIn(height * 0.25f, height * 0.9f).coerceAtLeast(0.3f)
        val time = (entity.tickCount + partialTicks) / 20f
        val consumer = buffers.getBuffer(RenderType.entitySolid(TEXTURE))

        pose.pushPose()
        pose.translate(0.0, (height * 0.96f).toDouble(), 0.0)
        pose.mulPose(Axis.YP.rotationDegrees(time * 12f))
        for (arc in 0 until ARCS) {
            val start = arc * 360f / ARCS
            val bob = sin(time * 1.6f + arc * 2.1f) * size * 0.02f
            for (puff in 0 until PUFFS) {
                val t = puff / (PUFFS - 1f)
                // Each string rises from the head and curls around it.
                val angle = (start + t * 85f) * Mth.DEG_TO_RAD
                val distance = ring * (1f - 0.3f * t)
                val y = size * (0.03f + 0.1f * sin(t * PI.toFloat()) + 0.05f * t) + bob
                val radius = (size * (0.085f - 0.045f * t)).coerceAtLeast(0.05f)
                puff(pose, consumer, cos(angle) * distance, y, sin(angle) * distance, radius)
            }
        }
        pose.popPose()
    }

    /**
     * Height of the top of the Pokémon above its feet and its half width, in blocks:
     * from the 3D model as drawn (RareCandyBone), its hitbox otherwise. Hitboxes are
     * often far from the model (a placeholder for some big Pokémon, a scale that grew).
     */
    private fun size(entity: PokemonEntity, pose: PoseStack): Pair<Float, Float> {
        val hitbox = entity.bbHeight to entity.bbWidth / 2f
        val model = entity.delegate as? RareCandyModelSize ?: return hitbox
        val halfWidth = model.`generations$modelHalfWidth`()
        if (halfWidth <= 0f) return hitbox
        // Both heights are in the same camera-relative coordinates of this frame.
        val height = model.`generations$modelTopY`() - pose.last().pose().m31()
        if (height !in 0.1f..96f) return hitbox
        return height to halfWidth
    }

    private fun puff(pose: PoseStack, consumer: VertexConsumer, x: Float, y: Float, z: Float, radius: Float) {
        val last = pose.last()
        var i = 0
        while (i < sphere.size) {
            val nx = sphere[i]
            val ny = sphere[i + 1]
            val nz = sphere[i + 2]
            consumer.addVertex(last, x + nx * radius, y + ny * radius, z + nz * radius)
                .setColor(sphere[i + 3], sphere[i + 4], sphere[i + 5], 1f)
                .setUv(0.5f + nx * 0.5f, 0.5f + nz * 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(last, nx, ny, nz)
            i += CORNER
        }
    }

    /** Shading by height on the sphere: light on top, dark underneath. */
    private fun colour(up: Float): FloatArray {
        val (from, to, t) = if (up >= 0f) Triple(MIDDLE, TOP, up) else Triple(MIDDLE, BOTTOM, -up)
        return FloatArray(3) { i -> from[i] + (to[i] - from[i]) * t }
    }
}
