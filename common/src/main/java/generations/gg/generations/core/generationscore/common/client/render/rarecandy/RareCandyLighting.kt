package generations.gg.generations.core.generationscore.common.client.render.rarecandy

import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Sun (or moon) light for RareCandy models when a shader pack is active.
 *
 * Minecraft lights entities with two fixed directions, which shader packs replace
 * with their own sun lighting. RareCandy draws outside the pack's programs, so
 * its models kept the fixed lighting and did not match the scene: the side facing
 * the sun was no brighter than the other. animated.vs.glsl blends this light in.
 *
 * The direction is the one Iris gives the pack (CelestialUniforms
 * .getShadowLightPositionInWorldSpace), so it matches the pack's shadows: the
 * vanilla sky rotation, tilted by the pack's sunPathRotation, the sun by day and
 * the moon by night.
 */
object RareCandyLighting {
    /** World space, towards the light. */
    @JvmField val direction = Vector3f(0f, 1f, 0f)
    @JvmField val color = Vector3f(1f, 1f, 1f)
    /** 0 keeps Minecraft's entity lighting. */
    @JvmField var strength = 0f

    private val celestial = Matrix4f()
    private val NOON = Vector3f(1.0f, 0.97f, 0.92f)
    private val LOW_SUN = Vector3f(1.0f, 0.72f, 0.45f)
    private val MOON = Vector3f(0.65f, 0.75f, 1.0f)

    /** Once per frame, before RareCandy draws. */
    @JvmStatic
    fun update() {
        strength = 0f
        if (!GenerationsCore.CONFIG.client.shaderSunLighting || !IrisFramebufferCompat.isShaderPackActive()) return
        val level = Minecraft.getInstance().level ?: return
        if (!level.dimensionType().hasSkyLight()) return

        val skyAngle = level.getTimeOfDay(0f)
        val sunAngle = if (skyAngle < 0.75f) skyAngle + 0.25f else skyAngle - 0.75f
        val day = sunAngle <= 0.5f

        celestial.identity()
            .rotateY(Math.toRadians(-90.0).toFloat())
            .rotateZ(Math.toRadians(IrisFramebufferCompat.sunPathRotation().toDouble()).toFloat())
            .rotateX(Math.toRadians(skyAngle * 360.0).toFloat())
        direction.set(0f, if (day) 1f else -1f, 0f)
        celestial.transformDirection(direction).normalize()

        // Fade out as the light reaches the horizon, and under rain.
        val elevation = direction.y
        val rain = level.getRainLevel(0f)
        strength = Mth.clamp(elevation / 0.15f, 0f, 1f) * (if (day) 1f else 0.35f) * (1f - 0.75f * rain)

        if (day) LOW_SUN.lerp(NOON, Mth.clamp(elevation / 0.5f, 0f, 1f), color) else color.set(MOON)
    }
}
