package generations.gg.generations.core.generationscore.common.client.ultraspace

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.PoseType
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpVariant
import net.minecraft.client.Minecraft
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.PlayerSkin

/**
 * Solgaleo or Lunala flying through the tunnel, with the player on its back, at
 * the size fitting the tunnel (UltraWarpRideLimits) and centred on the point the
 * player steers. The
 * Pokémon is the one of the mod's models (RareCandy, from the resource packs) in its
 * flying pose (FLY: "flywalk"), with the look of the player's own (shiny...): drawn
 * like an entity of the world, but without one. RareCandy draws it in its pass right
 * after (firstRenderPass). The player is their skin on the vanilla player model,
 * sitting as on any mount.
 */
object UltraWarpRiderRenderer {
    private val playerModels = HashMap<Boolean, PlayerModel<AbstractClientPlayer>>()

    fun render(ride: UltraWarpRideClient.Ride, buffers: MultiBufferSource.BufferSource) {
        val model = ride.model
        val scale = ride.riderScale
        val pose = PoseStack()
        pose.translate(ride.riderPos.x - ride.camera.x, ride.riderPos.y - ride.camera.y, ride.riderPos.z - ride.camera.z)
        pose.mulPose(ride.riderRotation)
        // The steered point is the model's centre (the middle of its bounds), not its
        // feet: the whole Pokémon is around it, and the limits keep it inside the walls.
        pose.translate(-model.centerX * scale.toDouble(), -model.centerY * scale.toDouble(), -model.centerZ * scale.toDouble())
        renderPokemon(ride, pose, buffers, scale)
        if (ride.config.rider.showPlayer) {
            pose.pushPose()
            // Hips on the seat: a sitting player's feet are about 0.6 below them.
            pose.translate(0.0, model.seatY * scale - 0.6, model.seatZ * scale.toDouble())
            renderPlayer(pose, buffers)
            pose.popPose()
        }
    }

    /**
     * Necrozma passing by (a rare event, only seen): Ultra Necrozma (aspect
     * ultra-fusion), from far ahead near the top of the tunnel past the camera, facing
     * it, in its own light (UltraWarpObjectRenderer). No battle, no catch: there is no
     * entity.
     */
    fun renderNecrozma(ride: UltraWarpRideClient.Ride, buffers: MultiBufferSource.BufferSource) {
        val position = ride.necrozmaPosition() ?: return
        val pose = PoseStack()
        pose.translate(position.x - ride.camera.x, position.y - ride.camera.y, position.z - ride.camera.z)
        val distance = ride.cameraPath
        val t = ride.tunnel.tangent(distance + 60.0)
        val up = org.joml.Vector3d(0.0, 1.0, 0.0)
        pose.mulPose(UltraWarpRideClient.orientation(org.joml.Vector3f(-t.x.toFloat(), -t.y.toFloat(), -t.z.toFloat()), org.joml.Vector3f().set(up), org.joml.Quaternionf()))
        // Its centre (the middle of its bounds) on the path, not its feet.
        val scale = ride.config.rider.necrozmaScale
        val centre = ride.config.rider.necrozma
        pose.translate(-centre.centerX * scale.toDouble(), -centre.centerY * scale.toDouble(), -centre.centerZ * scale.toDouble())
        renderModel(ride, NECROZMA, NECROZMA_ASPECTS, ride.necrozmaState, PoseType.STAND, pose, buffers, scale)
    }

    private val NECROZMA = com.cobblemon.mod.common.util.cobblemonResource("necrozma")
    private val NECROZMA_ASPECTS = setOf("ultra-fusion")

    private fun renderPokemon(ride: UltraWarpRideClient.Ride, pose: PoseStack, buffers: MultiBufferSource.BufferSource, scale: Float) {
        renderModel(ride, ride.variant.species, ride.aspects, ride.pokemonState, PoseType.FLY, pose, buffers, scale)
    }

    /** A Pokémon model drawn like an entity of the world, without one, in this pose. */
    private fun renderModel(
        ride: UltraWarpRideClient.Ride, species: net.minecraft.resources.ResourceLocation, aspects: Set<String>,
        state: com.cobblemon.mod.common.client.render.models.blockbench.FloatingState, poseType: PoseType,
        pose: PoseStack, buffers: MultiBufferSource.BufferSource, scale: Float,
    ) {
        val form = PokemonSpecies.getByIdentifier(species)?.getForm(aspects) ?: return
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
        state.setPoseToFirstSuitable(poseType)
        // Animations run on real time: in ticks since the last frame.
        state.updatePartialTicks(ride.frameSeconds * 20f)
        model.applyAnimations(null, state, 0f, 0f, ride.seconds * 20f, 0f, 0f)

        // As an entity facing its way: Minecraft's living-entity transform.
        pose.pushPose()
        pose.mulPose(Axis.YP.rotationDegrees(180f))
        val size = form.baseScale * scale
        pose.scale(size, size, size)
        pose.scale(-1f, -1f, 1f)
        pose.translate(0.0, -1.501, 0.0)
        val type = RenderType.entityCutout(VaryingModelRepository.getTexture(species, state))
        val buffer = buffers.getBuffer(type)
        model.withLayerContext(buffers, state, VaryingModelRepository.getLayers(species, state)) {
            model.render(context, pose, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1)
        }
        model.setDefault()
        // A RareCandy model is only queued here; any other kind is drawn now.
        buffers.endBatch(type)
        pose.popPose()
    }

    private fun renderPlayer(pose: PoseStack, buffers: MultiBufferSource.BufferSource) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val skin = player.skin
        val slim = skin.model() == PlayerSkin.Model.SLIM
        val model = playerModels.getOrPut(slim) {
            PlayerModel(minecraft.entityModels.bakeLayer(if (slim) ModelLayers.PLAYER_SLIM else ModelLayers.PLAYER), slim)
        }
        model.setAllVisible(true)
        model.riding = true
        model.young = false
        model.crouching = false
        model.attackTime = 0f
        model.setupAnim(player, 0f, 0f, player.tickCount.toFloat(), 0f, 0f)

        pose.pushPose()
        pose.mulPose(Axis.YP.rotationDegrees(180f))
        pose.scale(0.9375f, 0.9375f, 0.9375f)
        pose.scale(-1f, -1f, 1f)
        pose.translate(0.0, -1.501, 0.0)
        val type = RenderType.entityTranslucent(skin.texture())
        model.renderToBuffer(pose, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY)
        buffers.endBatch(type)
        pose.popPose()
    }
}
