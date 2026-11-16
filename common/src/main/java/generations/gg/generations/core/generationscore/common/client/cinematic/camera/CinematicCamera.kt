package generations.gg.generations.core.generationscore.common.client.cinematic.camera

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import org.joml.Quaternionf
import org.joml.Vector3d
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The scene's camera in the world: the shot (local frame) placed at the shrine, kept out of
 * the hills (one ray per frame from what it looks at: it comes closer at once and goes back
 * slowly), out of the players' bodies, shaken (smooth noise, never a jump), then mixed with
 * the player's real camera (fades in and out). Only the camera moves; the player stays where
 * they are.
 */
class CinematicCamera(val origin: Vec3, facing: Float) {
    private val cosF = cos(Math.toRadians(facing.toDouble()))
    private val sinF = sin(Math.toRadians(facing.toDouble()))
    private val toWorld = Quaternionf().rotationY(Math.toRadians(-facing.toDouble()).toFloat())

    /** The terrain's allowance, from 0 (at the focus) to 1 (where the shot wants it). */
    private var allowance = 1.0

    val position = Vector3d()
    val rotation = Quaternionf()
    /** The shot's field of view, and how much of the scene's camera there is (fov() mixes them). */
    var sceneFov = 70.0
        private set
    var weight = 0.0
        private set
    var yaw = 0f
        private set
    var pitch = 0f
        private set

    /** Away from the player's eyes: drawn detached (their body seen, no hand). */
    var detached = false
        private set

    private val cine = Vector3d()
    private val focus = Vector3d()
    private val shakeRotation = Quaternionf()

    /** Local (x right, y up, z forward) to world. */
    fun toWorld(local: Vector3d, out: Vector3d): Vector3d =
        out.set(origin.x + cosF * local.x - sinF * local.z, origin.y + local.y, origin.z + sinF * local.x + cosF * local.z)

    fun toWorld(x: Double, y: Double, z: Double, out: Vector3d): Vector3d =
        out.set(origin.x + cosF * x - sinF * z, origin.y + y, origin.z + sinF * x + cosF * z)

    /** World to local (the inverse of toWorld). */
    fun toLocal(world: Vec3, out: Vector3d): Vector3d {
        val dx = world.x - origin.x
        val dz = world.z - origin.z
        return out.set(cosF * dx + sinF * dz, world.y - origin.y, -sinF * dx + cosF * dz)
    }

    /** The world offset (x, then z) of a local horizontal offset. */
    fun toWorldDx(x: Double, z: Double): Double = cosF * x - sinF * z

    fun toWorldDz(x: Double, z: Double): Double = sinF * x + cosF * z

    /** A local rotation turned into the world's. */
    fun rotationToWorld(local: Quaternionf, out: Quaternionf): Quaternionf = out.set(toWorld).mul(local)

    private val facingDegrees = facing.toDouble()

    /** World yaw (Minecraft's) of a local yaw (0 = facing local +z, the shrine's front). */
    fun yawToWorld(localYaw: Double): Double = localYaw + facingDegrees

    /**
     * This frame's camera. shot: the director's frame (local); time: scene seconds; dt: since
     * the last frame; shake: the scene's amplitude (already reduced for accessibility);
     * weight: 0 = the player's own camera, 1 = the scene's. The field of view is mixed later
     * (fov), once Minecraft computed the player's.
     */
    fun update(
        level: ClientLevel, shot: CamFrame, time: Double, dt: Double, shake: Double, weight: Double,
        realPos: Vec3, realRotation: Quaternionf,
    ) {
        this.weight = weight.coerceIn(0.0, 1.0)
        toWorld(shot.pos, cine)
        toWorld(shot.focus, focus)
        relief(level, dt)
        keepOut(level)
        if (shake > 0.0) {
            val s = shake.coerceAtMost(1.5)
            cine.add(0.06 * s * CameraMath.fbm(time * 8.0, 11), 0.05 * s * CameraMath.fbm(time * 8.0, 12), 0.06 * s * CameraMath.fbm(time * 8.0, 13))
        }
        rotationToWorld(shot.rotation, rotation)
        if (shake > 0.0) {
            val s = shake.coerceAtMost(1.5)
            shakeRotation.identity()
                .rotateY(Math.toRadians(4.0 * s * CameraMath.fbm(time * 9.0, 1)).toFloat())
                .rotateX(Math.toRadians(3.0 * s * CameraMath.fbm(time * 9.0, 2)).toFloat())
                .rotateZ(Math.toRadians(2.5 * s * CameraMath.fbm(time * 7.0, 3)).toFloat())
            rotation.mul(shakeRotation)
        }
        sceneFov = shot.fov
        val w = this.weight
        if (w < 1.0) {
            position.set(realPos.x, realPos.y, realPos.z).lerp(cine, w)
            val q = Quaternionf(realRotation).slerp(rotation, w.toFloat())
            rotation.set(q)
        } else {
            position.set(cine)
        }
        val forward = org.joml.Vector3f(0f, 0f, -1f).rotate(rotation)
        yaw = Math.toDegrees(kotlin.math.atan2(-forward.x.toDouble(), forward.z.toDouble())).toFloat()
        pitch = Math.toDegrees(kotlin.math.asin((-forward.y).toDouble().coerceIn(-1.0, 1.0))).toFloat()
        detached = position.distance(realPos.x, realPos.y, realPos.z) > 0.35
    }

    /** The field of view drawn: the player's own mixed with the shot's. */
    fun fov(real: Double): Double = real + (sceneFov - real) * weight

    /** One ray from the focus to the camera: a hill in between brings it closer. */
    private fun relief(level: ClientLevel, dt: Double) {
        val fromX = focus.x
        val fromY = focus.y + 0.3
        val fromZ = focus.z
        val dx = cine.x - fromX
        val dy = cine.y - fromY
        val dz = cine.z - fromZ
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        var wanted = 1.0
        val from = Vec3(fromX, fromY, fromZ)
        // A focus inside a block (under the ground): no ray, the shot as written.
        val inside = !level.getBlockState(BlockPos.containing(from)).getCollisionShape(level, BlockPos.containing(from)).isEmpty
        if (length > 0.5 && !inside) {
            val hit = level.clip(ClipContext(from, Vec3(cine.x, cine.y, cine.z), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()))
            if (hit.type == HitResult.Type.BLOCK) {
                val d = hit.location.distanceTo(from)
                wanted = ((d - 0.35) / length).coerceIn(0.05, 1.0)
            }
        }
        allowance = if (wanted < allowance) wanted else allowance + (wanted - allowance) * (1.0 - exp(-dt / 0.8))
        if (allowance < 0.9999) cine.set(fromX + dx * allowance, fromY + dy * allowance, fromZ + dz * allowance)
    }

    /** Never inside a player's body (theirs or another's): pushed out sideways. */
    private fun keepOut(level: ClientLevel) {
        for (player in level.players()) {
            if (player.isSpectator) continue
            val px = player.x
            val pz = player.z
            val dy = cine.y - player.y
            if (dy < -0.3 || dy > player.bbHeight + 0.4) continue
            val dx = cine.x - px
            val dz = cine.z - pz
            val d = sqrt(dx * dx + dz * dz)
            val radius = 0.75
            if (d >= radius) continue
            if (d < 1e-3) cine.add(radius, 0.0, 0.0) else cine.set(px + dx / d * radius, cine.y, pz + dz / d * radius)
        }
    }
}
