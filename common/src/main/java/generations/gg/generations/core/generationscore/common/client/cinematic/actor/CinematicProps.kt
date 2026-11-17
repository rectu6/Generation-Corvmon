package generations.gg.generations.core.generationscore.common.client.cinematic.actor

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CinematicCamera
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.CinematicScene
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.PropKey
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.Sheets
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import org.joml.Vector3d

/**
 * A scene's real blocks (CinematicScene.props), drawn in the world's pass like the actor (the
 * same buffers, so the shader packs see them too): the Regis' floating rocks, ice, steel and
 * Unown letters. Nothing is placed in the world; a block's light is the world's there, raised
 * to its glow.
 */
object CinematicProps {
    private val key = PropKey()
    private val world = Vector3d()
    private val pose = PoseStack()

    fun render(scene: CinematicScene, camera: CinematicCamera, a: Double) {
        val n = scene.props
        if (n <= 0) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val cam = minecraft.gameRenderer.mainCamera.position
        val buffers = minecraft.renderBuffers().bufferSource()
        val blocks = minecraft.blockRenderer
        var drew = false
        for (i in 0 until n) {
            key.state = null
            if (!scene.prop(i, a, key)) continue
            val state = key.state ?: continue
            val p = key.pos
            if (key.scale <= 0.01 || !p.x.isFinite() || !p.y.isFinite() || !p.z.isFinite() || !key.yaw.isFinite() || !key.pitch.isFinite() || !key.roll.isFinite()) continue
            camera.toWorld(p, world)
            pose.pushPose()
            pose.translate(world.x - cam.x, world.y - cam.y, world.z - cam.z)
            pose.mulPose(Axis.YP.rotationDegrees(-camera.yawToWorld(key.yaw).toFloat()))
            pose.mulPose(Axis.XP.rotationDegrees(key.pitch.toFloat()))
            pose.mulPose(Axis.ZP.rotationDegrees(key.roll.toFloat()))
            val s = key.scale.toFloat()
            pose.scale(s, s, s)
            pose.translate(-0.5, -0.5, -0.5)
            val here = LevelRenderer.getLightColor(level, BlockPos.containing(world.x, world.y, world.z))
            val light = LightTexture.pack(maxOf(LightTexture.block(here), key.glow.coerceIn(0, 15)), LightTexture.sky(here))
            blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY)
            pose.popPose()
            drew = true
        }
        if (drew) {
            buffers.endBatch(Sheets.cutoutBlockSheet())
            buffers.endBatch(Sheets.solidBlockSheet())
            buffers.endBatch(Sheets.translucentItemSheet())
            buffers.endBatch(Sheets.translucentCullBlockSheet())
        }
    }
}
