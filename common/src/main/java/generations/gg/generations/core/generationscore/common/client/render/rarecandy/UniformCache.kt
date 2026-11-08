package generations.gg.generations.core.generationscore.common.client.render.rarecandy

import gg.generations.rarecandy.renderer.animation.AnimationController
import gg.generations.rarecandy.renderer.pipeline.UniformUploadContext
import gg.generations.rarecandy.renderer.storage.AnimatedObjectInstance
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL20C

/**
 * RareCandy re-uploads every uniform before every mesh it draws, although a GL
 * program keeps its uniform values between draws. Within one world pass, the
 * per-frame values (camera, fog, lights) and the per-instance values (pose,
 * bones, light, tint) are the same for every mesh, so those uploads are skipped.
 *
 * Caching only happens between [begin] and [end]: outside a world pass, every
 * upload goes through as before.
 */
object RareCandyPass {
    /** Returned by [current] outside a pass: never equal to a stored epoch. */
    internal const val NONE = Int.MIN_VALUE

    private var epoch = 0
    private var active = false

    @JvmStatic
    fun begin() {
        if (++epoch == NONE) epoch = 0
        active = true
    }

    @JvmStatic
    fun end() {
        active = false
    }

    internal fun current(): Int = if (active) epoch else NONE
}

/** Opens once per pass, for values that do not change during a pass. */
internal class PassGate {
    private var epoch = RareCandyPass.NONE

    fun open(): Boolean {
        val current = RareCandyPass.current()
        if (current != RareCandyPass.NONE && epoch == current) return false
        epoch = current
        return true
    }
}

/** Opens once per instance and pass: the program keeps the value while that instance's meshes are drawn. */
internal class InstanceGate {
    private var epoch = RareCandyPass.NONE
    private var instance: Any? = null

    fun open(instance: Any?): Boolean {
        val current = RareCandyPass.current()
        if (current != RareCandyPass.NONE && epoch == current && this.instance === instance) return false
        epoch = current
        this.instance = instance
        return true
    }
}

/**
 * Uploads the bone array in one glUniformMatrix4fv call. RareCandy's
 * Uniform.uploadMat4fs issues one call per bone (up to 220) for every mesh.
 */
internal class BoneUploader {
    /** Location of boneTransforms[0]; array elements follow it. Set once the program exists. */
    var location = -1
    private val buffer = BufferUtils.createFloatBuffer(MAX_BONES * 16)
    private var epoch = RareCandyPass.NONE
    private var uploaded: Array<Matrix4f?>? = null

    fun upload(ctx: UniformUploadContext) {
        @Suppress("UNCHECKED_CAST")
        val bones = ((ctx.instance() as? AnimatedObjectInstance)?.transforms
            ?: AnimationController.NO_ANIMATION) as Array<Matrix4f?>

        if (location < 0) {
            @Suppress("UNCHECKED_CAST")
            ctx.uniform().uploadMat4fs(bones as Array<Matrix4f>)
            return
        }

        // Bone arrays are only rewritten between passes, so the same array means the same data.
        val current = RareCandyPass.current()
        if (current != RareCandyPass.NONE && epoch == current && uploaded === bones) return
        epoch = current
        uploaded = bones

        val count = minOf(bones.size, MAX_BONES)
        if (count == 0) return
        buffer.clear()
        for (i in 0 until count) (bones[i] ?: IDENTITY).get(i * 16, buffer)
        buffer.limit(count * 16)
        GL20C.glUniformMatrix4fv(location, false, buffer)
    }

    companion object {
        /** MAX_BONES in animated.vs.glsl. */
        const val MAX_BONES = 220
        private val IDENTITY = Matrix4f()
    }
}
