package generations.gg.generations.core.generationscore.common.client.cinematic.actor

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.PoseType
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorPose
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3d

/**
 * The scene's Pokémon, without an entity (nothing to catch, hit or duplicate): its model
 * (RareCandy, from the resource packs), with its look (shiny...), in the pose the scene gives
 * (FLY, HOVER, STAND) and the cry animation on cue. Drawn in the world's pass (LevelRendererMixin,
 * before RareCandy's pass): the shader pack's lights and shadows fall on it like on any
 * Pokémon, and the real entity can take its place exactly (lot 5). Like
 * UltraWarpRiderRenderer.renderModel, the transform of a living entity.
 */
class CinematicActor(val species: ResourceLocation, val aspects: Set<String>) {
    private val state = FloatingState()
    private val pending = LinkedHashSet<String>()
    private var lastSeconds = -1f

    /** The cry animation ("cry" → the model's roar), at the next frame. */
    fun cry() = animate("cry")

    /** A one-off animation of the poser ("cry", "special" → the model's attack...), at the next frame. */
    fun animate(name: String) {
        pending += name
    }

    /**
     * Queues the model at its world feet (yaw: Minecraft's; pitch nose down, roll left wing up,
     * degrees). seconds: the action's time (its animations follow the slow motion). bright: lit
     * at full light whatever the place (the manifestations, Ho-Oh in the sky).
     */
    fun render(feet: Vector3d, yaw: Double, pitch: Double, roll: Double, scale: Double, pose: ActorPose, seconds: Float, bright: Boolean = false) =
        render(feet, yaw, pitch, roll, scale, pose, seconds, bright, null, -1, false)

    /**
     * The same, with a look (JOHTO-PLAN.md § 2): material, a statue's texture (a Johto statue:
     * drawn like StatueEntityRenderer, its animations frozen when `frozen`); tint, the colour the
     * model is multiplied by (ARGB, -1: none), translucent with its alpha (the spectral beasts).
     */
    fun render(
        feet: Vector3d, yaw: Double, pitch: Double, roll: Double, scale: Double, pose: ActorPose, seconds: Float, bright: Boolean,
        material: ResourceLocation?, tint: Int, translucent: Boolean, frozen: Boolean = false,
    ) {
        if (pose == ActorPose.HIDDEN || scale <= 0.01) return
        if (translucent && (tint ushr 24) < 4) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val camera = minecraft.gameRenderer.mainCamera.position
        val form = PokemonSpecies.getByIdentifier(species)?.getForm(aspects) ?: return
        val type = when (pose) {
            ActorPose.FLY -> PoseType.FLY
            ActorPose.HOVER -> PoseType.HOVER
            else -> PoseType.STAND
        }
        state.currentAspects = aspects
        val model = VaryingModelRepository.getPoser(species, state)
        val context = RenderContext()
        model.context = context
        if (material == null) VaryingModelRepository.getTextureNoSubstitute(species, state)?.let { context.put(RenderContext.TEXTURE, it) }
        context.put(RenderContext.SCALE, form.baseScale)
        context.put(RenderContext.SPECIES, species)
        context.put(RenderContext.ASPECTS, aspects)
        context.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
        context.put(RenderContext.POSABLE_STATE, state)
        context.put(RenderContext.DO_QUIRKS, false)
        state.currentModel = model
        state.setPoseToFirstSuitable(type)
        if (pending.isNotEmpty()) {
            for (name in pending) state.addFirstAnimation(setOf(name))
            pending.clear()
        }
        val step = if (lastSeconds < 0f || frozen) 0f else (seconds - lastSeconds).coerceIn(0f, 0.5f)
        lastSeconds = seconds
        state.updatePartialTicks(step * 20f)
        model.applyAnimations(null, state, 0f, 0f, (if (frozen) 0f else seconds) * 20f, 0f, 0f)

        val pose = PoseStack()
        pose.translate(feet.x - camera.x, feet.y - camera.y, feet.z - camera.z)
        // Facing its way (local +Z is its front), then its pitch and roll, then Minecraft's living-entity transform.
        pose.mulPose(Axis.YP.rotationDegrees(-yaw.toFloat()))
        if (pitch != 0.0 || roll != 0.0) {
            val height = form.hitbox.height() * form.baseScale * scale.toFloat() * 0.5f
            pose.translate(0.0, height.toDouble(), 0.0)
            pose.mulPose(Axis.XP.rotationDegrees(pitch.toFloat()))
            pose.mulPose(Axis.ZP.rotationDegrees(roll.toFloat()))
            pose.translate(0.0, -height.toDouble(), 0.0)
        }
        pose.mulPose(Axis.YP.rotationDegrees(180f))
        val size = form.baseScale * scale.toFloat()
        pose.scale(size, size, size)
        pose.scale(-1f, -1f, 1f)
        pose.translate(0.0, -1.501, 0.0)
        val buffers = minecraft.renderBuffers().bufferSource()
        val texture = material ?: VaryingModelRepository.getTexture(species, state)
        val renderType = when {
            material != null -> RenderType.entitySolid(material)
            translucent -> RenderType.entityTranslucent(texture)
            else -> RenderType.entityCutout(texture)
        }
        val buffer = buffers.getBuffer(renderType)
        val light = if (bright) net.minecraft.client.renderer.LightTexture.FULL_BRIGHT else LevelRenderer.getLightColor(level, BlockPos.containing(feet.x, feet.y + 1.0, feet.z))
        if (material != null) {
            // A statue: its material for every layer, as StatueEntityRenderer does.
            generations.gg.generations.core.generationscore.common.client.render.entity.ModifiableTextureSupplier.texture = material
            model.withLayerContext(buffers, state, statueLayers) {
                model.render(context, pose, buffer, light, OverlayTexture.NO_OVERLAY, tint)
            }
        } else {
            model.withLayerContext(buffers, state, if (translucent) emptyList() else VaryingModelRepository.getLayers(species, state)) {
                model.render(context, pose, buffer, light, OverlayTexture.NO_OVERLAY, tint)
            }
        }
        model.setDefault()
        // A RareCandy model is only queued here; any other kind is drawn now.
        buffers.endBatch(renderType)
    }

    companion object {
        /** A statue's one layer, whose texture is the material (StatueEntityRenderer's, made the same way). */
        private val statueLayers: List<com.cobblemon.mod.common.client.render.ModelLayer> by lazy {
            val layer = com.cobblemon.mod.common.client.render.ModelLayer()
            try {
                val field = com.cobblemon.mod.common.client.render.ModelLayer::class.java.getDeclaredField("texture")
                field.isAccessible = true
                field.set(layer, generations.gg.generations.core.generationscore.common.client.render.entity.ModifiableTextureSupplier)
                listOf(layer)
            } catch (e: Exception) {
                emptyList()
            }
        }

        /** A statue material's texture ("concrete", "marble"...), or null when the pack has none. */
        fun materialTexture(name: String?): ResourceLocation? =
            if (name.isNullOrBlank()) null else generations.gg.generations.core.generationscore.common.client.GenerationsTextureLoader.getLocation(name)
    }
}
