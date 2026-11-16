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
     * degrees). seconds: the action's time (its animations follow the slow motion).
     */
    fun render(feet: Vector3d, yaw: Double, pitch: Double, roll: Double, scale: Double, pose: ActorPose, seconds: Float) {
        if (pose == ActorPose.HIDDEN || scale <= 0.01) return
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
        VaryingModelRepository.getTextureNoSubstitute(species, state)?.let { context.put(RenderContext.TEXTURE, it) }
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
        val step = if (lastSeconds < 0f) 0f else (seconds - lastSeconds).coerceIn(0f, 0.5f)
        lastSeconds = seconds
        state.updatePartialTicks(step * 20f)
        model.applyAnimations(null, state, 0f, 0f, seconds * 20f, 0f, 0f)

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
        val renderType = RenderType.entityCutout(VaryingModelRepository.getTexture(species, state))
        val buffer = buffers.getBuffer(renderType)
        val light = LevelRenderer.getLightColor(level, BlockPos.containing(feet.x, feet.y + 1.0, feet.z))
        model.withLayerContext(buffers, state, VaryingModelRepository.getLayers(species, state)) {
            model.render(context, pose, buffer, light, OverlayTexture.NO_OVERLAY, -1)
        }
        model.setDefault()
        // A RareCandy model is only queued here; any other kind is drawn now.
        buffers.endBatch(renderType)
    }
}
