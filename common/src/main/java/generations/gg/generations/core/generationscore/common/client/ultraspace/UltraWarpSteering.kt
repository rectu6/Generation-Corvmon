package generations.gg.generations.core.generationscore.common.client.ultraspace

import com.mojang.blaze3d.platform.InputConstants
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpRideLimits
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpRideSettings
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import kotlin.math.exp
import kotlin.math.min

/**
 * Arcade steering of the Pokémon across the tunnel, in the tunnel's own axes: x
 * towards its right, y towards its up, measured from its axis (the journey goes
 * forward by itself). Speed builds up while a key is held and dies down once it is
 * let go. The Pokémon's centre stays within the limits of its direction
 * (UltraWarpRideLimits: its shape there, the floor, sides and ceiling margins):
 * moving outwards is braked over the last blocks, and when the tunnel narrows it is
 * brought back inside smoothly, never further out than most of the margin (so never
 * through a wall).
 */
class UltraWarpSteering(private val settings: UltraWarpRideSettings) {
    var x = 0.0
        private set
    var y = 0.0
        private set
    var vx = 0.0
        private set
    var vy = 0.0
        private set

    /** Keys of this frame: -1, 0 or 1 on each axis. */
    var inputX = 0
        private set
    var inputY = 0
        private set

    /** Boost key held (sprint), and dodge pressed (jump) since the last state sent: no effect yet. */
    var boost = false
        private set
    private var dodgeQueued = false
    private var jumpWasDown = false

    /** The keys, unless the journey's screen does not have them (confirmation, pause menu). */
    fun read(enabled: Boolean, controls: UltraWarpClientConfig.Controls) {
        if (!enabled) {
            inputX = 0
            inputY = 0
            boost = false
            jumpWasDown = false
            return
        }
        val options = Minecraft.getInstance().options
        val arrows = controls.arrowKeys
        val right = isDown(options.keyRight) || (arrows && isKeyDown(GLFW.GLFW_KEY_RIGHT))
        val left = isDown(options.keyLeft) || (arrows && isKeyDown(GLFW.GLFW_KEY_LEFT))
        val up = isDown(options.keyUp) || (arrows && isKeyDown(GLFW.GLFW_KEY_UP))
        val down = isDown(options.keyDown) || (arrows && isKeyDown(GLFW.GLFW_KEY_DOWN))
        inputX = (if (right) 1 else 0) - (if (left) 1 else 0)
        inputY = ((if (up) 1 else 0) - (if (down) 1 else 0)) * (if (controls.invertVertical) -1 else 1)
        boost = isDown(options.keySprint)
        val jump = isDown(options.keyJump)
        if (jump && !jumpWasDown) dodgeQueued = true
        jumpWasDown = jump
    }

    /** Dodge pressed since the last call. */
    fun takeDodge(): Boolean = dodgeQueued.also { dodgeQueued = false }

    /** Where the centre may go, by direction across the tunnel (x right, y up, unit). */
    interface Bounds {
        /** How far from the axis towards this direction. */
        fun allowed(nx: Double, ny: Double): Double

        /** Room kept from the wall towards a direction going up by ny. */
        fun margin(ny: Double): Double
    }

    /**
     * One frame of movement. bounds: how far the centre may go in each direction;
     * softness: blocks before that limit where moving outwards is braked; pushX/Y:
     * an outside pull (anomalies), in blocks per second each second; control: false
     * while the keys no longer steer (defeat).
     */
    fun update(dt: Double, bounds: Bounds, softness: Double, pushX: Double = 0.0, pushY: Double = 0.0, control: Boolean = true) {
        val max = settings.steerSpeed.toDouble()
        fun approach(v: Double, input: Int): Double {
            val rate = if (input != 0) settings.steerAcceleration else settings.steerDeceleration
            return v + (input * max - v) * (1 - exp(-rate * dt))
        }
        vx = approach(vx, if (control) inputX else 0) + pushX * dt
        vy = approach(vy, if (control) inputY else 0) + pushY * dt

        // Near the edge, moving outwards slows down to nothing at the limit.
        var length = UltraWarpRideLimits.length(x, y)
        if (length > 1e-6) {
            val nx = x / length
            val ny = y / length
            val outward = vx * nx + vy * ny
            val edge = bounds.allowed(nx, ny) - softness
            if (outward > 0 && length > edge) {
                val brake = ((length - edge) / softness.coerceAtLeast(1e-3)).coerceIn(0.0, 1.0)
                vx -= nx * outward * brake
                vy -= ny * outward * brake
            }
        }
        x += vx * dt
        y += vy * dt

        // Beyond the limit (the tunnel narrowed, a pull, a long frame): back inside
        // smoothly, never further out than most of the margin, no more speed outwards.
        length = UltraWarpRideLimits.length(x, y)
        if (length > 1e-6) {
            val nx = x / length
            val ny = y / length
            val allowed = bounds.allowed(nx, ny)
            if (length > allowed) {
                val back = length + (allowed - length) * (1 - exp(-8 * dt))
                val target = min(back, allowed + bounds.margin(ny) * 0.8)
                x = nx * target
                y = ny * target
                val outward = vx * nx + vy * ny
                if (outward > 0) {
                    vx -= nx * outward
                    vy -= ny * outward
                }
            }
        }
    }

    private fun isKeyDown(key: Int): Boolean = InputConstants.isKeyDown(Minecraft.getInstance().window.window, key)

    /** A key binding as set in the options (keyboard or mouse button). */
    private fun isDown(mapping: KeyMapping): Boolean {
        val key = InputConstants.getKey(mapping.saveString())
        if (key.value < 0) return false
        val window = Minecraft.getInstance().window.window
        return when (key.type) {
            InputConstants.Type.KEYSYM -> InputConstants.isKeyDown(window, key.value)
            InputConstants.Type.MOUSE -> GLFW.glfwGetMouseButton(window, key.value) == GLFW.GLFW_PRESS
            else -> false
        }
    }
}
