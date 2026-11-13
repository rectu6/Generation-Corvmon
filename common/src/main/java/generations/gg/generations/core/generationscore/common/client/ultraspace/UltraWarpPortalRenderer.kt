package generations.gg.generations.core.generationscore.common.client.ultraspace

import com.mojang.blaze3d.vertex.VertexConsumer
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpPortal
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * The dimensional portals, as the server placed them, standing across the tunnel at
 * their place (facing along it, so they follow the bends), glowing (additive: no
 * depth trouble with the walls). The levels differ by their shape, not only their
 * light:
 * 1. simple: a turning vortex opening with depth (three swirls one behind the other),
 *    a bright core, a thin rim, a few sparkles drawn in;
 * 2. one ring: a 3D ring (a torus) around it, tilted and turning; more sparkles;
 * 3. two rings: two tori turning opposite ways at different tilts; electric arcs;
 * 4. exceptional: two rainbow tori, a crown of iridescent energy petals turning both
 *    ways, a pulsing halo, many sparkles.
 * The colour is the destination's. Once the Pokémon is through it, it grows until it
 * fills the view.
 */
object UltraWarpPortalRenderer {
    private val GLOW: ResourceLocation = GenerationsCore.id("textures/ultra_space/glow.png")
    private val RING: ResourceLocation = GenerationsCore.id("textures/ultra_space/ring.png")
    private val VORTEX: ResourceLocation = GenerationsCore.id("textures/ultra_space/vortex.png")
    private val BOLT: ResourceLocation = GenerationsCore.id("textures/ultra_space/bolt.png")
    private val PETAL: ResourceLocation = GenerationsCore.id("textures/ultra_space/petal.png")
    private val SPARKLE: ResourceLocation = GenerationsCore.id("textures/ultra_space/sparkle.png")

    /** A portal of this frame: where it is from the camera, its plane (right, up), its axis along the tunnel. */
    private class Seen(
        val portal: UltraWarpPortal, val centre: Vector3f, val right: Vector3f, val up: Vector3f, val axis: Vector3f,
        val size: Float, val fade: Float, val pulse: Float, val level: Int,
    )

    private val t = Vector3d()
    private val u = Vector3d()
    private val r = Vector3d()

    fun render(ride: UltraWarpRideClient.Ride, buffers: MultiBufferSource.BufferSource) {
        val config = ride.settings.config.portals
        val view = config.viewDistance.toDouble()
        val time = ride.seconds
        val capture = ride.capture
        val seen = ArrayList<Seen>()
        for (portal in ride.portals.values) {
            if (portal.state == UltraWarpPortal.State.PASSED) continue
            val ahead = portal.distance - ride.cameraPath
            val captured = portal === ride.captured
            if (!captured && (ahead < -6.0 || ahead > view)) continue
            ride.tunnel.frame(portal.distance, t, u, r)
            val centre = ride.tunnel.position(portal.distance).fma(portal.x, r).fma(portal.y, u).sub(ride.camera)
            val level = portal.rarity.coerceIn(1, 4)
            val look = config.levels[level - 1]
            val grow = if (captured && capture != null) 1f + 7f * capture * capture else 1f
            val fade = if (captured) 1f else 1f - UltraWarpObjectRenderer.smoothstep(view.toFloat() * 0.75f, view.toFloat(), ahead.toFloat())
            val pulse = 1f
            seen.add(
                Seen(
                    portal, Vector3f(centre.x.toFloat(), centre.y.toFloat(), centre.z.toFloat()),
                    Vector3f().set(r), Vector3f().set(u), Vector3f().set(t),
                    config.radius * look.size * grow, fade, pulse, level,
                ),
            )
        }
        if (seen.isEmpty()) return
        val camRight = Vector3f(1f, 0f, 0f).rotate(ride.view)
        val camUp = Vector3f(0f, 1f, 0f).rotate(ride.view)

        // Openings: three swirls, one behind the other along the tunnel (depth), turning.
        UltraWarpObjectRenderer.draw(buffers, RenderType.eyes(VORTEX)) { buffer ->
            for (s in seen) {
                val look = ride.settings.config.portals.levels[s.level - 1]
                for (layer in 0 until 3) {
                    val behind = Vector3f(s.axis).mul(0.7f * layer * s.size / 3f).add(s.centre)
                    val turn = time * (1.2f + 0.4f * s.level) * (if (layer % 2 == 0) 1f else -0.7f)
                    val colour = UltraWarpObjectRenderer.shade(s.portal.color, (1.25f - 0.3f * layer) * s.fade * look.glow * s.pulse)
                    plane(buffer, behind, s.right, s.up, s.size * (1.15f - 0.22f * layer), turn, colour)
                }
            }
        }

        // Cores, haloes; the exceptional one's halo pulses in many colours.
        UltraWarpObjectRenderer.draw(buffers, RenderType.eyes(GLOW)) { buffer ->
            for (s in seen) {
                val look = ride.settings.config.portals.levels[s.level - 1]
                val core = mix(s.portal.color, 0xFFFFFF, 0.65f)
                UltraWarpObjectRenderer.billboard(buffer, s.centre, camRight, camUp, s.size * 0.95f, 0f, UltraWarpObjectRenderer.shade(core, 1.5f * s.fade * look.glow * s.pulse))
                UltraWarpObjectRenderer.billboard(buffer, s.centre, camRight, camUp, s.size * 2.4f, 0f, UltraWarpObjectRenderer.shade(s.portal.color, 0.45f * s.fade * look.glow))
                if (s.level >= 4) {
                    val breathe = 1f + 0.18f * sin(time * 2.6f)
                    UltraWarpObjectRenderer.billboard(buffer, s.centre, camRight, camUp, s.size * 4.2f * breathe, 0f, UltraWarpObjectRenderer.shade(rainbow(time * 0.15f), 0.35f * s.fade))
                }
            }
        }

        // Rims.
        UltraWarpObjectRenderer.draw(buffers, RenderType.eyes(RING)) { buffer ->
            for (s in seen) {
                plane(buffer, s.centre, s.right, s.up, s.size * 1.08f, 0f, UltraWarpObjectRenderer.shade(mix(s.portal.color, 0xFFFFFF, 0.3f), 1.3f * s.fade * s.pulse))
            }
        }

        // Exceptional: a crown of energy petals, two layers turning opposite ways.
        UltraWarpObjectRenderer.draw(buffers, RenderType.eyes(PETAL)) { buffer ->
            for (s in seen) {
                val look = ride.settings.config.portals.levels[s.level - 1]
                if (look.petals <= 0) continue
                for (layer in 0 until 2) {
                    val turn = time * 0.35f * (if (layer == 0) 1f else -1.3f) + layer * Math.PI.toFloat() / look.petals
                    for (i in 0 until look.petals) {
                        val angle = turn + i * 2f * Math.PI.toFloat() / look.petals
                        val colour = UltraWarpObjectRenderer.shade(rainbow(i.toFloat() / look.petals + time * 0.1f + layer * 0.5f), (0.55f - 0.15f * layer) * s.fade)
                        petal(buffer, s, angle, s.size * (1.55f + 0.35f * layer), s.size * (1.25f + 0.3f * layer), s.size * (0.62f + 0.12f * layer), colour)
                    }
                }
            }
        }

        // Rings (true tori), and arcs from the level 3.
        UltraWarpObjectRenderer.draw(buffers, RenderType.eyes(BOLT)) { buffer ->
            for (s in seen) {
                val look = ride.settings.config.portals.levels[s.level - 1]
                val spin = time * look.ringSpeed
                if (look.rings >= 1) {
                    torus(buffer, s, s.size * 1.45f, s.size * 0.075f, 0.45f + 0.08f * sin(time * 0.7f), spin, s.level >= 4) {
                        UltraWarpObjectRenderer.shade(mix(0xFFC060, s.portal.color, 0.3f), 1.4f * s.fade * s.pulse)
                    }
                }
                if (look.rings >= 2) {
                    torus(buffer, s, s.size * 1.8f, s.size * 0.065f, -0.55f + 0.06f * cos(time * 0.5f), -spin * 0.8f, s.level >= 4) {
                        UltraWarpObjectRenderer.shade(mix(0xFF8A80, s.portal.color, 0.25f), 1.3f * s.fade * s.pulse)
                    }
                }
                if (look.rings >= 3) {
                    torus(buffer, s, s.size * 2.15f, s.size * 0.05f, 1.2f, spin * 0.6f, s.level >= 4) {
                        UltraWarpObjectRenderer.shade(mix(0xFFFFFF, s.portal.color, 0.4f), 1.1f * s.fade)
                    }
                }
                if (s.level >= 3) {
                    val step = floor(time * 12f).toLong()
                    for (n in 0 until 3) {
                        val seed = s.portal.id * 7919L + n * 104729L + step * 31L
                        val a = (UltraWarpObjectRenderer.hash(seed, 1) * 2 * Math.PI).toFloat()
                        val from = Vector3f(s.right).mul(cos(a) * s.size * 1.05f).add(Vector3f(s.up).mul(sin(a) * s.size * 1.05f)).add(s.centre)
                        val b = a + (UltraWarpObjectRenderer.hash(seed, 2) - 0.5).toFloat() * 1.6f
                        val reach = s.size * (1.5f + UltraWarpObjectRenderer.hash(seed, 3).toFloat())
                        val to = Vector3f(s.right).mul(cos(b) * reach).add(Vector3f(s.up).mul(sin(b) * reach)).add(s.centre)
                        UltraWarpObjectRenderer.bolt(buffer, from, to, seed, 0.06f * s.size, UltraWarpObjectRenderer.shade(mix(s.portal.color, 0xFFFFFF, 0.6f), 1.2f * s.fade))
                    }
                }
            }
        }

        // Sparkles drawn in, spiralling towards the opening.
        UltraWarpObjectRenderer.draw(buffers, RenderType.eyes(SPARKLE)) { buffer ->
            for (s in seen) {
                val look = ride.settings.config.portals.levels[s.level - 1]
                val n = look.sparkles
                for (i in 0 until n) {
                    val phase = ((time * 0.45f + i.toFloat() / n) % 1f)
                    val radius = s.size * (2.3f - 1.9f * phase)
                    val angle = i * 2.39996f + time * 1.4f + phase * 3f
                    val p = Vector3f(s.right).mul(cos(angle) * radius).add(Vector3f(s.up).mul(sin(angle) * radius))
                        .add(Vector3f(s.axis).mul(sin(i * 1.7f + time) * 0.4f)).add(s.centre)
                    val colour = UltraWarpObjectRenderer.shade(mix(s.portal.color, 0xFFFFFF, 0.5f), 1.4f * (1f - phase) * s.fade)
                    UltraWarpObjectRenderer.billboard(buffer, p, camRight, camUp, s.size * 0.12f, time * 3f + i, colour)
                }
            }
        }
    }

    /** A square in the portal's plane (right, up), turned by angle, both faces. */
    private fun plane(buffer: VertexConsumer, centre: Vector3f, right: Vector3f, up: Vector3f, size: Float, angle: Float, colour: Int) {
        val c = cos(angle)
        val s = sin(angle)
        val rx = Vector3f(right).mul(c).add(Vector3f(up).mul(s)).mul(size)
        val ux = Vector3f(up).mul(c).sub(Vector3f(right).mul(s)).mul(size)
        quad(buffer, Vector3f(centre).sub(rx).sub(ux), Vector3f(centre).add(rx).sub(ux), Vector3f(centre).add(rx).add(ux), Vector3f(centre).sub(rx).add(ux), colour)
    }

    /** A petal pointing out at angle: from inner to outer distance from the middle, this wide; both faces. */
    private fun petal(buffer: VertexConsumer, s: Seen, angle: Float, outer: Float, length: Float, width: Float, colour: Int) {
        val dir = Vector3f(s.right).mul(cos(angle)).add(Vector3f(s.up).mul(sin(angle)))
        val side = Vector3f(s.right).mul(-sin(angle)).add(Vector3f(s.up).mul(cos(angle))).mul(width / 2)
        val inner = Vector3f(dir).mul(outer - length).add(s.centre)
        val tip = Vector3f(dir).mul(outer).add(s.centre)
        // u across, v from the tip (0) to the base (1).
        val a = Vector3f(inner).sub(side)
        val b = Vector3f(inner).add(side)
        val c = Vector3f(tip).add(side)
        val d = Vector3f(tip).sub(side)
        UltraWarpObjectRenderer.vertex(buffer, a.x, a.y, a.z, 0f, 1f, colour)
        UltraWarpObjectRenderer.vertex(buffer, b.x, b.y, b.z, 1f, 1f, colour)
        UltraWarpObjectRenderer.vertex(buffer, c.x, c.y, c.z, 1f, 0f, colour)
        UltraWarpObjectRenderer.vertex(buffer, d.x, d.y, d.z, 0f, 0f, colour)
        UltraWarpObjectRenderer.vertex(buffer, d.x, d.y, d.z, 0f, 0f, colour)
        UltraWarpObjectRenderer.vertex(buffer, c.x, c.y, c.z, 1f, 0f, colour)
        UltraWarpObjectRenderer.vertex(buffer, b.x, b.y, b.z, 1f, 1f, colour)
        UltraWarpObjectRenderer.vertex(buffer, a.x, a.y, a.z, 0f, 1f, colour)
    }

    /**
     * A ring as a real 3D torus around the portal: its plane is the portal's, tilted
     * by tilt (radians, about the portal's right) and turned by spin about its own
     * axis; major radius, tube radius. rainbow: its colour runs through all hues
     * along it (exceptional portals), else colour().
     */
    private fun torus(
        buffer: VertexConsumer, s: Seen, major: Float, minor: Float, tilt: Float, spin: Float, rainbow: Boolean, colour: () -> Int,
    ) {
        // The ring's plane: (e1, e2), its axis n.
        val e2 = Vector3f(s.up).mul(cos(tilt)).add(Vector3f(s.axis).mul(sin(tilt)))
        val n = Vector3f(s.axis).mul(cos(tilt)).sub(Vector3f(s.up).mul(sin(tilt)))
        val e1 = Vector3f(s.right).mul(cos(spin)).add(Vector3f(e2).mul(sin(spin)))
        val f2 = Vector3f(e2).mul(cos(spin)).sub(Vector3f(s.right).mul(sin(spin)))
        val segments = 40
        val sides = 6
        val base = colour()
        fun point(i: Int, j: Int): Vector3f {
            val theta = i * 2f * Math.PI.toFloat() / segments
            val phi = j * 2f * Math.PI.toFloat() / sides
            val d = Vector3f(e1).mul(cos(theta)).add(Vector3f(f2).mul(sin(theta)))
            val tube = Vector3f(d).mul(cos(phi)).add(Vector3f(n).mul(sin(phi))).mul(minor)
            return Vector3f(d).mul(major).add(tube).add(s.centre)
        }
        for (i in 0 until segments) {
            val c = if (rainbow) UltraWarpObjectRenderer.shade(this.rainbow(i.toFloat() / segments + s.portal.id * 0.13f), 1.3f * s.fade) else base
            for (j in 0 until sides) {
                val a = point(i, j)
                val b = point(i + 1, j)
                val cc = point(i + 1, j + 1)
                val d = point(i, j + 1)
                // u across each face (a soft band of light), v along the ring.
                UltraWarpObjectRenderer.vertex(buffer, a.x, a.y, a.z, 0f, 0f, c)
                UltraWarpObjectRenderer.vertex(buffer, b.x, b.y, b.z, 0f, 1f, c)
                UltraWarpObjectRenderer.vertex(buffer, cc.x, cc.y, cc.z, 1f, 1f, c)
                UltraWarpObjectRenderer.vertex(buffer, d.x, d.y, d.z, 1f, 0f, c)
                UltraWarpObjectRenderer.vertex(buffer, d.x, d.y, d.z, 1f, 0f, c)
                UltraWarpObjectRenderer.vertex(buffer, cc.x, cc.y, cc.z, 1f, 1f, c)
                UltraWarpObjectRenderer.vertex(buffer, b.x, b.y, b.z, 0f, 1f, c)
                UltraWarpObjectRenderer.vertex(buffer, a.x, a.y, a.z, 0f, 0f, c)
            }
        }
    }

    private fun quad(buffer: VertexConsumer, a: Vector3f, b: Vector3f, c: Vector3f, d: Vector3f, colour: Int) {
        UltraWarpObjectRenderer.vertex(buffer, a.x, a.y, a.z, 0f, 1f, colour)
        UltraWarpObjectRenderer.vertex(buffer, b.x, b.y, b.z, 1f, 1f, colour)
        UltraWarpObjectRenderer.vertex(buffer, c.x, c.y, c.z, 1f, 0f, colour)
        UltraWarpObjectRenderer.vertex(buffer, d.x, d.y, d.z, 0f, 0f, colour)
        UltraWarpObjectRenderer.vertex(buffer, d.x, d.y, d.z, 0f, 0f, colour)
        UltraWarpObjectRenderer.vertex(buffer, c.x, c.y, c.z, 1f, 0f, colour)
        UltraWarpObjectRenderer.vertex(buffer, b.x, b.y, b.z, 1f, 1f, colour)
        UltraWarpObjectRenderer.vertex(buffer, a.x, a.y, a.z, 0f, 1f, colour)
    }

    /** A bright hue (0 to 1 round the colour wheel) as 0xRRGGBB. */
    fun rainbow(hue: Float): Int {
        val h = ((hue % 1f) + 1f) % 1f * 6f
        val x = 1f - kotlin.math.abs(h % 2f - 1f)
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(1f, x, 0f)
            1 -> Triple(x, 1f, 0f)
            2 -> Triple(0f, 1f, x)
            3 -> Triple(0f, x, 1f)
            4 -> Triple(x, 0f, 1f)
            else -> Triple(1f, 0f, x)
        }
        return ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
    }

    /** Two colours 0xRRGGBB mixed (t of b). */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun channel(shift: Int) = ((((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt() and 0xFF) shl shift
        return channel(16) or channel(8) or channel(0)
    }

    private fun min3(a: Float, b: Float) = min(a, b)
}
