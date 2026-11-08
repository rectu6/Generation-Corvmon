package generations.gg.generations.core.generationscore.common.client.model

import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import generations.gg.generations.core.generationscore.common.client.render.CobblemonInstanceProvider
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.ModelRegistry
import gg.generations.rarecandy.renderer.animation.Animation
import gg.generations.rarecandy.renderer.components.AnimatedMeshObject
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f

data class RareCandyAnimation(private val model: ResourceLocation, private val name: String) {

    val animation: Animation?
        get() {
            val objects = ModelRegistry[model]?.renderObject ?: return null
            if (objects.isReady) {
                return (objects.objects[0] as AnimatedMeshObject).animations[name]
            }

            return null
        }

    fun run(
        context: RenderContext,
        animationSeconds: Float,
        intensity: Float
    ): Boolean {
        val instance = context.request(RenderContext.POSABLE_STATE)?.takeIf { it is CobblemonInstanceProvider }?.let { it as CobblemonInstanceProvider }?.instance ?: return false

        val anim = animation ?: let {
            return false
        }

        val duration = anim.animationDuration.div(anim.ticksPerSecond).toFloat()

        var currentSeconds = animationSeconds
        if (anim.loops) {
            currentSeconds %= duration
        } else if (animationSeconds > duration && duration > 0) {
            return false
        }

        instance.setAnimation(anim)

        // getFrameTransform returns one array per Animation, shared by every Pokémon
        // playing it: each instance copies the pose into its own array, otherwise all
        // of them would be drawn with the pose computed last. The pose is computed
        // once per time: Iris's shadow pass and the main pass both pose the model.
        val transforms = instance.matrixTransforms
        if (instance.posedAnimation !== anim || instance.posedSeconds != currentSeconds || transforms == null) {
            val frame = anim.getFrameTransform(currentSeconds.toDouble())
            val target = transforms?.takeIf { it.size == frame.size } ?: Array(frame.size) { Matrix4f() }
            for (i in frame.indices) target[i].set(frame[i])
            instance.matrixTransforms = target
            instance.posedAnimation = anim
            instance.posedSeconds = currentSeconds
        }
        anim.getFrameOffset(instance.currentAnimation!!)

        return true
    }
}