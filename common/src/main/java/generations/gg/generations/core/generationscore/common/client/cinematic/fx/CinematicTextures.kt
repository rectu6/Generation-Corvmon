package generations.gg.generations.core.generationscore.common.client.cinematic.fx

import com.mojang.blaze3d.platform.NativeImage
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.client.renderer.texture.DynamicTexture
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The cinematics' textures, made when the resources load (no image file): white shapes the
 * vertex colours tint, and a tileable noise. Linear filtering, repeat (the noise scrolls).
 */
object CinematicTextures {
    enum class Kind(val size: Int, val height: Int = size) {
        WHITE(4), NOISE(128), HALO(64), FLAKE(64), SPARK(64), SMOKE(64), EMBER(32), RING(128, 8), STREAK(128, 32), FROST(256)
    }

    private val textures = HashMap<Kind, DynamicTexture>()

    /** At the resources' (re)load, on the render thread: made once, kept. */
    fun ensure() {
        if (textures.size == Kind.entries.size) return
        val started = System.nanoTime()
        for (kind in Kind.entries) {
            if (textures.containsKey(kind)) continue
            try {
                val image = NativeImage(kind.size, kind.height, false)
                paint(kind, image)
                val texture = DynamicTexture(image)
                texture.setFilter(kind != Kind.WHITE, false)
                textures[kind] = texture
            } catch (e: Exception) {
                GenerationsCore.LOGGER.error("Legendary cinematics: could not make the {} texture", kind, e)
            }
        }
        GenerationsCore.LOGGER.info("Legendary cinematics: {} textures made in {} ms", textures.size, (System.nanoTime() - started) / 1_000_000)
    }

    /** The texture's OpenGL id (made if needed). */
    fun id(kind: Kind): Int {
        textures[kind]?.let { return it.id }
        ensure()
        return textures[kind]?.id ?: 0
    }

    private fun abgr(r: Double, g: Double, b: Double, a: Double): Int {
        fun c(x: Double) = (x.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt()
        return (c(a) shl 24) or (c(b) shl 16) or (c(g) shl 8) or c(r)
    }

    private fun white(a: Double) = abgr(1.0, 1.0, 1.0, a)

    private fun paint(kind: Kind, image: NativeImage) {
        val w = kind.size
        val h = kind.height
        for (y in 0 until h) for (x in 0 until w) {
            // Centred coordinates from -1 to 1.
            val u = (x + 0.5) / w * 2.0 - 1.0
            val v = (y + 0.5) / h * 2.0 - 1.0
            val r = sqrt(u * u + v * v)
            val colour = when (kind) {
                Kind.WHITE -> white(1.0)
                Kind.NOISE -> {
                    val px = x.toDouble() / w
                    val py = y.toDouble() / h
                    val n0 = tileFbm(px, py, 4, 5, 11)
                    val n1 = tileFbm(px, py, 8, 4, 23)
                    val ridged = 1.0 - abs(tileFbm(px, py, 6, 4, 37) * 2.0 - 1.0)
                    val n3 = tileFbm(px, py, 16, 3, 41)
                    abgr(n0, n1, ridged * ridged, n3)
                }
                Kind.HALO -> {
                    val a = exp(-r * r * 5.0) * 0.85 + exp(-r * r * 40.0) * 0.6
                    white(a * smoothEdge(r))
                }
                Kind.FLAKE -> white(flake(u, v, r))
                Kind.SPARK -> {
                    val star = exp(-abs(u) * 14.0) * exp(-v * v * 2.0) + exp(-abs(v) * 14.0) * exp(-u * u * 2.0)
                    val core = exp(-r * r * 30.0)
                    white(min(1.0, star * 0.8 + core) * smoothEdge(r))
                }
                Kind.SMOKE -> {
                    val px = x.toDouble() / w
                    val py = y.toDouble() / h
                    val n = tileFbm(px, py, 4, 4, 59)
                    val falloff = (1.0 - r).coerceIn(0.0, 1.0)
                    white((falloff * falloff * (0.45 + 0.75 * n)).coerceIn(0.0, 1.0))
                }
                Kind.EMBER -> {
                    val a = (1.0 - r).coerceIn(0.0, 1.0).pow(2.5) + exp(-r * r * 25.0) * 0.5
                    white(min(1.0, a))
                }
                Kind.RING -> {
                    // Across the band (u from inner to outer edge): a soft rise, a bright edge, a sharp end.
                    val t = (u + 1.0) / 2.0
                    val a = (t.pow(2.0) * 0.6 + exp(-((t - 0.82) / 0.07).pow(2.0)) * 0.9) * (1.0 - smooth01((t - 0.9) / 0.1))
                    white(min(1.0, a))
                }
                Kind.STREAK -> {
                    val a = exp(-v * v * 9.0) * (1.0 - abs(u)).coerceIn(0.0, 1.0).pow(0.8)
                    white(a)
                }
                Kind.FROST -> {
                    val px = x.toDouble() / w
                    val py = y.toDouble() / h
                    white(frost(px, py))
                }
            }
            image.setPixelRGBA(x, y, colour)
        }
    }

    private fun smooth01(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** Fades to 0 at the square's edge circle (no hard border). */
    private fun smoothEdge(r: Double): Double = 1.0 - smooth01((r - 0.8) / 0.2)

    /** A six-armed snowflake with small branches and a soft glow. */
    private fun flake(u: Double, v: Double, r: Double): Double {
        if (r > 1.0) return 0.0
        val angle = atan2(v, u)
        val sector = PI / 3.0
        val local = ((angle % sector) + sector) % sector - sector / 2.0
        val ax = r * cos(local)
        val ay = abs(r * sin(local))
        var a = exp(-(ay / 0.045).pow(2.0)) * (1.0 - smooth01((ax - 0.78) / 0.2))
        // Branches at 45° along each arm.
        for (at in doubleArrayOf(0.35, 0.58)) {
            val bx = ax - at
            if (bx > 0.0) {
                val along = bx
                val off = abs(ay - along * 0.8)
                a = max(a, exp(-(off / 0.04).pow(2.0)) * (1.0 - smooth01((along - 0.18) / 0.08)))
            }
        }
        a = max(a, exp(-r * r * 20.0))
        return (a * 0.9 + exp(-r * r * 3.0) * 0.18).coerceIn(0.0, 1.0)
    }

    /** Frost on the ground: the edges of crystal cells, broken by noise. */
    private fun frost(px: Double, py: Double): Double {
        val cells = 9
        val x = px * cells
        val y = py * cells
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        var d1 = 9.0
        var d2 = 9.0
        for (oy in -1..1) for (ox in -1..1) {
            val cx = ix + ox
            val cy = iy + oy
            val wx = ((cx % cells) + cells) % cells
            val wy = ((cy % cells) + cells) % cells
            val fx = cx + hash(wx, wy, 71)
            val fy = cy + hash(wx, wy, 83)
            val d = sqrt((x - fx) * (x - fx) + (y - fy) * (y - fy))
            if (d < d1) {
                d2 = d1
                d1 = d
            } else if (d < d2) d2 = d
        }
        val edge = exp(-((d2 - d1) / 0.06).pow(2.0))
        val n = tileFbm(px, py, 12, 3, 97)
        val fine = tileFbm(px, py, 32, 2, 101)
        return (edge * (0.5 + 0.6 * n) + fine * 0.35 * n).coerceIn(0.0, 1.0)
    }

    private fun hash(x: Int, y: Int, seed: Int): Double {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7fffffff) / 2147483647.0
    }

    /** Value noise on a grid of `period` cells, tileable. */
    private fun tileNoise(px: Double, py: Double, period: Int, seed: Int): Double {
        val x = px * period
        val y = py * period
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val fx = x - ix
        val fy = y - iy
        fun at(cx: Int, cy: Int) = hash(((cx % period) + period) % period, ((cy % period) + period) % period, seed)
        val ux = fx * fx * (3 - 2 * fx)
        val uy = fy * fy * (3 - 2 * fy)
        val a = at(ix, iy) + (at(ix + 1, iy) - at(ix, iy)) * ux
        val b = at(ix, iy + 1) + (at(ix + 1, iy + 1) - at(ix, iy + 1)) * ux
        return a + (b - a) * uy
    }

    private fun tileFbm(px: Double, py: Double, period: Int, octaves: Int, seed: Int): Double {
        var sum = 0.0
        var amp = 0.5
        var norm = 0.0
        var p = period
        for (o in 0 until octaves) {
            sum += tileNoise(px, py, p, seed + o * 17) * amp
            norm += amp
            amp *= 0.5
            p *= 2
        }
        return sum / norm
    }
}
