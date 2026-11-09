package generations.gg.generations.core.generationscore.common.client.render.block.entity

import com.cobblemon.mod.common.client.render.item.CobblemonBuiltinItemRenderer
import com.mojang.blaze3d.vertex.PoseStack
import generations.gg.generations.core.generationscore.common.world.level.block.raid.RaidDenBlock
import generations.gg.generations.core.generationscore.common.world.level.block.raid.RaidDenBlockEntity
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BeaconRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

/**
 * A Dynamax den: its 3D model (RaidDenModel), and when a raid is waiting, a light beam
 * whose colour gives the tier, with the pit glowing the same colour.
 */
class RaidDenRenderer(@Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<RaidDenBlockEntity> {

    override fun render(den: RaidDenBlockEntity, partialTick: Float, poseStack: PoseStack, buffers: MultiBufferSource, light: Int, overlay: Int) {
        val beam = den.blockState.getValue(RaidDenBlock.BEAM)
        val time = den.level?.gameTime ?: 0L
        val active = beam != RaidDenBlock.Beam.NONE
        poseStack.pushPose()
        poseStack.translate(0.5, 0.0, 0.5)
        RaidDenModel.render(poseStack, buffers, light, overlay, if (active) beam.color else null, time + partialTick)
        poseStack.popPose()
        if (!active) return
        BeaconRenderer.renderBeaconBeam(poseStack, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1f, time, 0, BEAM_HEIGHT, beam.color, 0.3f, 0.45f)
    }

    override fun shouldRenderOffScreen(den: RaidDenBlockEntity): Boolean = true

    override fun getViewDistance(): Int = VIEW_DISTANCE

    // Seen from afar like a beacon: only the horizontal distance counts.
    override fun shouldRender(den: RaidDenBlockEntity, cameraPos: Vec3): Boolean =
        Vec3.atCenterOf(den.blockPos).multiply(1.0, 0.0, 1.0).closerThan(cameraPos.multiply(1.0, 0.0, 1.0), VIEW_DISTANCE.toDouble())

    companion object {
        private const val BEAM_HEIGHT = 96
        private const val VIEW_DISTANCE = 192
    }
}

/** The den item: the same 3D model, inactive (item model generations_core:item/raid_den, builtin/entity). */
class RaidDenItemRenderer : CobblemonBuiltinItemRenderer {
    override fun render(stack: ItemStack, mode: ItemDisplayContext, matrices: PoseStack, vertexConsumers: MultiBufferSource, light: Int, overlay: Int) {
        matrices.pushPose()
        matrices.translate(0.5, 0.25, 0.5)
        RaidDenModel.render(matrices, vertexConsumers, light, overlay, null, 0f)
        matrices.popPose()
    }
}
