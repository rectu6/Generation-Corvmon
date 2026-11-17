package generations.gg.generations.core.generationscore.common.client.cinematic.scenes

import generations.gg.generations.core.generationscore.common.client.cinematic.audio.RegiMusic
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.easeOutCubic
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.phase
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.random
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.smooth
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.CinematicTextures.Kind
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxBatches
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial.Layer
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes.argb
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes.lerpColour
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes.withAlpha
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxParticles
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.PostParams
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Regis' scenes (RegiScript), drawn on the watchers' clients only. Common: the Unown letters
 * (real blocks drawn, the destroyed ones) rising and flying into the shrine, the rune circle, the
 * trembling, the core, the titan's shape then matter then skin, its lights, its cry and the wave
 * on the ground. Each element: Regirock's glowing cracks, dust and floating rocks; Regice's frost,
 * mist and crystal that breaks; Registeel's energy lines, plates, steel rings and sparks;
 * Regieleki's arcs, sphere and electric rings; Regidrago's red runes, aura, spectral jaws and
 * distortion; Regigigas' five orbs, pillars of light and ancient stones. Nothing in the world is
 * touched. Options of the start packet: test, ceil, room, u (the letters), orbs.
 */
class RegiScene(
    override val id: String,
    landing: Vector3d,
    test: Boolean,
    override val title: Component,
    height: Double,
    width: Double,
    options: Map<String, String>,
) : CinematicScene {
    private val glyphs = ArrayList<String>()

    override val script: RegiScript = run {
        val letters = ArrayList<Vector3d>()
        options["u"]?.split('/')?.forEach { part ->
            val bits = part.split(':')
            if (bits.size != 4) return@forEach
            val x = bits[1].toDoubleOrNull() ?: return@forEach
            val y = bits[2].toDoubleOrNull() ?: return@forEach
            val z = bits[3].toDoubleOrNull() ?: return@forEach
            if (letters.size >= 12) return@forEach
            glyphs += bits[0].take(1).lowercase()
            letters += Vector3d(x, y, z)
        }
        RegiScript(
            RegiElement.of(id), landing, test || options["test"] == "1", height, width,
            options["ceil"]?.toDoubleOrNull() ?: 14.0, options["room"]?.toDoubleOrNull() ?: 12.0, letters,
        )
    }
    private val s = script
    private val e = s.element
    override val subtitle: Component = Component.translatable("generations_core.cinematic.$id.subtitle")

    // ---------------------------------------------------------------- materials

    private val glows = FxMaterial("regi_glows", Layer.ADD, Kind.HALO)
    private val streaks = FxMaterial("regi_streaks", Layer.ADD, Kind.STREAK)
    private val runeRing = FxMaterial("regi_rune_ring", Layer.ADD, Kind.RING).noise(0f, 0.15f, 6f, 0.55f).shape(0f, 0f, 0.3f, 1.2f)
    private val runeMarks = FxMaterial("regi_rune_marks", Layer.ADD, Kind.STREAK).shape(0f, 0f, 0.2f, 1.3f)
    private val groundGlow = FxMaterial("regi_ground_glow", Layer.ADD, Kind.HALO).shape(0f, 0f, 0.4f, 1f)
    private val coreMat = FxMaterial("regi_core", Layer.ADD, Kind.NOISE).noise(0.2f, 0.5f, 3f, 0.3f).shape(2f, 0.7f, 0f, 1.4f)
    private val shellMat = FxMaterial("regi_shell", Layer.ADD, Kind.NOISE).noise(0.3f, -0.2f, 2.5f, 0.55f).shape(2.2f, 0.9f, 0f, 1.1f)
    private val column = FxMaterial("regi_column", Layer.ADD, Kind.WHITE).noise(0.05f, -0.9f, 2.5f, 0.6f).shape(1.3f, -0.7f, 0f, 1.2f)
    private val bolts = FxMaterial("regi_bolts", Layer.ADD, Kind.WHITE)
    private val frost = FxMaterial("regi_frost", Layer.ALPHA, Kind.FROST).shape(0f, 0f, 0.3f, 1f)
    private val haze = FxMaterial("regi_haze", Layer.ALPHA, Kind.SMOKE).noise(0.02f, 0.05f, 1.5f, 0.4f).shape(0f, 0f, 1.5f, 1f)
    private val solid = FxMaterial("regi_solid", Layer.SOLID, Kind.WHITE, cull = true)

    init {
        when (e) {
            RegiElement.ICE -> {
                solid.tint[0] = 0.6f; solid.tint[1] = 0.86f; solid.tint[2] = 1f; solid.tint[3] = 0.85f
                solid.crystal[0] = 0.04f; solid.crystal[1] = 2.3f; solid.crystal[2] = 1.05f
            }
            else -> {
                // Polished steel: little refraction, a bright rim.
                solid.tint[0] = 0.78f; solid.tint[1] = 0.84f; solid.tint[2] = 0.92f; solid.tint[3] = 0.35f
                solid.crystal[0] = 0.008f; solid.crystal[1] = 1.4f; solid.crystal[2] = 1.15f
            }
        }
    }

    private val particleMaterials = arrayOf(
        FxMaterial("regi_p_dust", Layer.ALPHA, Kind.SMOKE).shape(0f, 0f, 1f, 1f),
        FxMaterial("regi_p_spark", Layer.ADD, Kind.SPARK),
        FxMaterial("regi_p_mote", Layer.ADD, Kind.HALO),
        FxMaterial("regi_p_flake", Layer.ADD, Kind.FLAKE),
        FxMaterial("regi_p_ember", Layer.ADD, Kind.EMBER),
    )

    override fun particles(capacity: Int) = FxParticles(capacity, particleMaterials)

    companion object {
        const val DUST = 0
        const val SPARK = 1
        const val MOTE = 2
        const val FLAKE = 3
        const val EMBER = 4
    }

    // ---------------------------------------------------------------- colours

    private val main: Int
    private val light: Int
    private val dark: Int
    private val accent: Int
    private val dot: Int

    init {
        when (e) {
            RegiElement.ROCK -> {
                main = argb(0.95f, 0.6f, 0.24f, 1f); light = argb(1f, 0.84f, 0.48f, 1f); dark = argb(0.36f, 0.26f, 0.16f, 1f); accent = argb(0.75f, 0.62f, 0.45f, 1f); dot = argb(1f, 0.55f, 0.15f, 1f)
            }
            RegiElement.ICE -> {
                main = argb(0.55f, 0.85f, 1f, 1f); light = argb(0.9f, 0.97f, 1f, 1f); dark = argb(0.2f, 0.4f, 0.7f, 1f); accent = argb(0.75f, 0.92f, 1f, 1f); dot = argb(1f, 0.9f, 0.35f, 1f)
            }
            RegiElement.STEEL -> {
                main = argb(0.8f, 0.87f, 0.96f, 1f); light = argb(1f, 1f, 1f, 1f); dark = argb(0.32f, 0.36f, 0.42f, 1f); accent = argb(0.45f, 0.9f, 1f, 1f); dot = argb(1f, 0.25f, 0.2f, 1f)
            }
            RegiElement.ELECTRIC -> {
                main = argb(1f, 0.86f, 0.18f, 1f); light = argb(1f, 1f, 0.78f, 1f); dark = argb(0.4f, 0.3f, 0.05f, 1f); accent = argb(0.55f, 0.85f, 1f, 1f); dot = argb(0.6f, 0.95f, 1f, 1f)
            }
            RegiElement.DRAGON -> {
                main = argb(0.92f, 0.12f, 0.12f, 1f); light = argb(1f, 0.48f, 0.3f, 1f); dark = argb(0.22f, 0.02f, 0.05f, 1f); accent = argb(0.62f, 0.18f, 0.85f, 1f); dot = argb(1f, 0.55f, 0.2f, 1f)
            }
            RegiElement.GIANT -> {
                main = argb(0.98f, 0.95f, 0.82f, 1f); light = argb(1f, 1f, 1f, 1f); dark = argb(0.35f, 0.34f, 0.3f, 1f); accent = argb(0.6f, 0.95f, 0.75f, 1f); dot = argb(1f, 0.9f, 0.5f, 1f)
            }
        }
    }

    private val white = argb(1f, 1f, 1f, 1f)

    /** Regigigas' five orbs: Regirock, Regice, Registeel, Regieleki, Regidrago. */
    private val orbColours = intArrayOf(argb(0.95f, 0.6f, 0.24f, 1f), argb(0.5f, 0.85f, 1f, 1f), argb(0.85f, 0.9f, 1f, 1f), argb(1f, 0.9f, 0.2f, 1f), argb(0.95f, 0.15f, 0.15f, 1f))
    private val orbs = e == RegiElement.GIANT

    // ---------------------------------------------------------------- the blocks drawn

    private val letterStates: List<BlockState?> = glyphs.map { g ->
        val block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("generations_core", "unown_block_$g"))
        if (block == Blocks.AIR) null else block.defaultBlockState()
    }

    private val fragmentStates: Array<BlockState> = when (e) {
        RegiElement.ROCK -> arrayOf(Blocks.STONE, Blocks.COBBLESTONE, Blocks.TUFF, Blocks.GRANITE, Blocks.COBBLED_DEEPSLATE, Blocks.ANDESITE)
        RegiElement.ICE -> arrayOf(Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE)
        RegiElement.STEEL -> arrayOf(Blocks.IRON_BLOCK, Blocks.IRON_TRAPDOOR, Blocks.CHAIN, Blocks.IRON_BLOCK, Blocks.ANVIL)
        RegiElement.DRAGON -> arrayOf(Blocks.CRYING_OBSIDIAN, Blocks.OBSIDIAN, Blocks.BLACKSTONE)
        RegiElement.GIANT -> arrayOf(Blocks.CHISELED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS)
        RegiElement.ELECTRIC -> arrayOf(Blocks.STONE)
    }.map { it.defaultBlockState() }.toTypedArray()

    /** Regice's crystal breaking: ice thrown out at the reveal. */
    private val shards = if (e == RegiElement.ICE) 14 else 0

    override val props: Int get() = s.letters.size + s.fragments + shards

    private val piece = PieceKey()

    override fun prop(i: Int, a: Double, out: PropKey): Boolean {
        val letters = s.letters.size
        if (i < letters) {
            val state = letterStates.getOrNull(i) ?: return false
            s.letter(i, a, piece)
            if (piece.scale <= 0.01) return false
            out.state = state
            copy(out)
            out.glow = 15
            return true
        }
        val j = i - letters
        if (j < s.fragments) {
            s.fragment(j, a, piece)
            if (piece.scale <= 0.01) return false
            out.state = fragmentStates[j % fragmentStates.size]
            copy(out)
            out.glow = (5 + 9 * s.core(a)).toInt()
            return true
        }
        val k = j - s.fragments
        if (k < shards) {
            val u = phase(a, s.revealAt, s.revealAt + 1.6)
            if (u <= 0.0 || u >= 1.0) return false
            val ang = k * 2.39996 + random(k, 71)
            val up = 0.3 + 0.9 * random(k, 72)
            val speed = 3.5 + 3.0 * random(k, 73)
            val t = u * 1.6
            out.state = fragmentStates[k % fragmentStates.size]
            out.pos.set(
                s.centre.x + sin(ang) * (s.w * 0.45 + speed * t),
                s.centre.y + (random(k, 74) - 0.5) * s.h * 0.8 + up * speed * t - 4.9 * t * t,
                s.centre.z + cos(ang) * (s.w * 0.45 + speed * t),
            )
            out.yaw = 200.0 * t + 40.0 * k
            out.pitch = 150.0 * t
            out.roll = 0.0
            out.scale = (0.22 + 0.16 * random(k, 75)) * (1.0 - smooth(phase(u, 0.6, 1.0)))
            out.glow = 10
            return true
        }
        return false
    }

    private fun copy(out: PropKey) {
        out.pos.set(piece.pos)
        out.yaw = piece.yaw
        out.pitch = piece.pitch
        out.roll = piece.roll
        out.scale = piece.scale
    }

    // ---------------------------------------------------------------- scratch

    private val v3 = Vector3f()
    private val w3 = Vector3f()
    private val local = Vector3d()
    private val world = Vector3d()
    private val pts = FloatArray(3 * 72)
    private val uv = FloatArray(2)
    private val q = Quaternionf()
    private val qw = Quaternionf()

    private fun emitLocal(frame: SceneFrame, material: Int, x: Double, y: Double, z: Double, lvx: Double, lvy: Double, lvz: Double, life: Float, size0: Float, size1: Float, colour: Int, drag: Float = 0f, gravity: Float = 0f, swirl: Float = 0f) {
        frame.camera.toWorld(x, y, z, world)
        val wx = frame.camera.toWorldDx(lvx, lvz)
        val wz = frame.camera.toWorldDz(lvx, lvz)
        frame.particles.emit(material, world.x, world.y, world.z, wx.toFloat(), lvy.toFloat(), wz.toFloat(), life, size0, size1, colour, drag = drag, gravity = gravity, swirl = swirl, angle = (random(frame.particles.count, 3) * 6.28).toFloat())
    }

    private fun burst(frame: SceneFrame, x: Double, y: Double, z: Double, n: Int, speed: Double, up: Double, salt: Int, colour: Int, material: Int, life: Float, size: Float, drag: Float = 1.4f, gravity: Float = 0f) {
        for (i in 0 until frame.count(n)) {
            val a = random(i, salt) * 2.0 * PI
            val b = (random(i, salt + 1) - 0.3) * PI * 0.5
            val sp = speed * (0.4 + random(i, salt + 2))
            emitLocal(frame, material, x, y, z, cos(b) * sin(a) * sp, sin(b) * sp + up, cos(b) * cos(a) * sp, life * (0.7f + 0.6f * random(i, salt + 3).toFloat()), size, size * 0.3f, colour, drag, gravity)
        }
    }

    /** A flat ring of particles on the ground. */
    private fun groundBurst(frame: SceneFrame, n: Int, speed: Double, salt: Int, colour: Int, material: Int, life: Float, size: Float) {
        for (i in 0 until frame.count(n)) {
            val a = random(i, salt) * 2.0 * PI
            val sp = speed * (0.7 + 0.6 * random(i, salt + 1))
            emitLocal(frame, material, sin(a) * 0.8, 0.15, cos(a) * 0.8, sin(a) * sp, 0.3 + 0.5 * random(i, salt + 2), cos(a) * sp, life, size, size * 1.8f, colour, drag = 1.2f)
        }
    }

    /** n per second of action while k > 0. */
    private fun emit(frame: SceneFrame, rate: Double, k: Double, salt: Int, spawn: (Int) -> Unit) {
        if (k <= 0.02) return
        val n = frame.count((rate * k * frame.da * 1000.0).toInt()) / 1000.0
        var count = n.toInt()
        if (random((frame.a * 1000).toInt(), salt) < n - count) count++
        for (i in 0 until count) spawn((frame.a * 977).toInt() + i * 31 + salt)
    }

    private fun halo(frame: SceneFrame, batches: FxBatches, x: Double, y: Double, z: Double, size: Float, colour: Int, angle: Float = 0f) {
        val c = frame.rel(x, y, z, v3)
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, size, angle, colour, frame.right, frame.up)
    }

    /** A circle round c, perpendicular to the unit axis (ax, ay, az), into pts: its count of points (closed). */
    private fun circle(frame: SceneFrame, cx: Double, cy: Double, cz: Double, ax: Double, ay: Double, az: Double, radius: Double, steps: Int, spin: Double): Int {
        var px = -az
        var py = 0.0
        var pz = ax
        var len = Math.sqrt(px * px + py * py + pz * pz)
        if (len < 1e-3) {
            px = 0.0; py = -az; pz = ay
            len = Math.sqrt(py * py + pz * pz).coerceAtLeast(1e-6)
        }
        px /= len; py /= len; pz /= len
        val qx = ay * pz - az * py
        val qy = az * px - ax * pz
        val qz = ax * py - ay * px
        val n = steps.coerceIn(3, 70)
        for (i in 0..n) {
            val t = spin + 2.0 * PI * i / n
            val c = cos(t) * radius
            val sn = sin(t) * radius
            frame.rel(cx + px * c + qx * sn, cy + py * c + qy * sn, cz + pz * c + qz * sn, w3)
            pts[i * 3] = w3.x; pts[i * 3 + 1] = w3.y; pts[i * 3 + 2] = w3.z
        }
        return n + 1
    }

    /** A ring of energy round the centre, tilted (degrees) and turning. */
    private fun tiltedRing(frame: SceneFrame, batches: FxBatches, radius: Double, tilt: Double, turn: Double, width: Float, c0: Int, c1: Int, material: FxMaterial = streaks) {
        val t = Math.toRadians(tilt)
        val count = circle(frame, s.centre.x, s.centre.y, s.centre.z, sin(t) * cos(turn), cos(t), sin(t) * sin(turn), radius, 56, turn * 2.0)
        FxMeshes.ribbon(batches.of(material), pts, count, width, width, c0, c1)
    }

    // ---------------------------------------------------------------- events

    override fun event(cue: Cue, frame: SceneFrame) {
        val a = cue.time
        val c = s.centre
        when {
            cue.id == "lift" -> {
                for ((i, p) in s.letters.withIndex()) {
                    burst(frame, p.x, p.y - 0.3, p.z, 14, 1.2, 0.6, 900 + i * 7, withAlpha(argb(0.6f, 0.58f, 0.55f, 1f), 0.5f), DUST, 1.4f, 0.5f, gravity = -0.2f)
                    burst(frame, p.x, p.y, p.z, 10, 1.6, 0.4, 950 + i * 7, withAlpha(main, 0.9f), MOTE, 1.2f, 0.18f)
                }
                groundBurst(frame, 30, 2.2, 1001, withAlpha(argb(0.55f, 0.5f, 0.45f, 1f), 0.35f), DUST, 1.8f, 0.6f)
            }
            cue.id == "gather" -> burst(frame, c.x, c.y, c.z, 24, 1.4, 0.0, 1011, withAlpha(light, 0.8f), MOTE, 1.0f, 0.2f)
            cue.id == "core" -> {
                burst(frame, c.x, c.y, c.z, 40, 3.0, 0.0, 1021, withAlpha(light, 0.95f), MOTE, 1.2f, 0.22f)
                if (orbs) for (i in 0 until 5) burst(frame, c.x, c.y, c.z, 10, 2.0, 0.3, 1031 + i * 3, withAlpha(orbColours[i], 0.9f), MOTE, 1.0f, 0.25f)
            }
            cue.id == "matter" -> burst(frame, c.x, c.y, c.z, 50, 2.6, 0.0, 1041, withAlpha(main, 0.9f), elementParticle(), 1.0f, 0.18f)
            cue.id == "reveal" -> reveal(frame)
            cue.id == "lights" -> burst(frame, s.face.x, s.face.y, s.face.z, 16, 0.8, 0.0, 1051, withAlpha(dot, 0.9f), MOTE, 0.8f, 0.12f)
            cue.id == "cry" -> {
                burst(frame, s.face.x, s.face.y, s.face.z + 0.2, 40, 6.0, 0.0, 1061, withAlpha(white, 0.85f), MOTE, 1.0f, 0.25f)
                groundBurst(frame, 60, 6.0, 1071, withAlpha(lerpColour(dark, accent, 0.5f), 0.45f), DUST, 1.6f, 0.7f)
                groundBurst(frame, 40, 7.0, 1081, withAlpha(main, 0.9f), elementParticle(), 1.0f, 0.16f)
            }
            cue.id == "calm" -> burst(frame, c.x, c.y, c.z, 20, 1.0, 0.4, 1091, withAlpha(light, 0.6f), MOTE, 1.6f, 0.16f)
            cue.id == "vanish" -> {
                s.titanPos(a, local)
                burst(frame, local.x, local.y + s.h * 0.5, local.z, 50, 1.6, 0.6, 1101, withAlpha(light, 0.45f), DUST, 1.8f, 1.0f)
                burst(frame, local.x, local.y + s.h * 0.5, local.z, 60, 2.6, 1.0, 1111, withAlpha(main, 0.9f), MOTE, 1.5f, 0.2f)
            }
            cue.id.startsWith("arc") -> {
                val i = cue.id.removePrefix("arc").toIntOrNull() ?: return
                val strike = s.strikes.getOrNull(i) ?: return
                val x = sin(strike[1]) * strike[2]
                val z = cos(strike[1]) * strike[2]
                burst(frame, x, 0.15, z, 18, 3.0, 1.5, 1121 + i * 5, withAlpha(light, 1f), SPARK, 0.6f, 0.12f, gravity = 6f)
                frame.camera.toWorld(x, 0.2, z, world)
                frame.sound(0.0, "minecraft:entity.lightning_bolt.impact", world.x, world.y, world.z, 0.5f, (1.3 + 0.3 * random(i, 1131)).toFloat())
            }
        }
    }

    private fun elementParticle(): Int = when (e) {
        RegiElement.ICE -> FLAKE
        RegiElement.STEEL, RegiElement.ELECTRIC -> SPARK
        RegiElement.DRAGON -> EMBER
        else -> MOTE
    }

    private fun reveal(frame: SceneFrame) {
        val c = s.centre
        burst(frame, c.x, c.y, c.z, 60, 5.0, 0.5, 1201, withAlpha(light, 1f), MOTE, 1.2f, 0.25f)
        when (e) {
            RegiElement.ROCK -> {
                burst(frame, c.x, c.y, c.z, 70, 4.0, 1.0, 1211, withAlpha(argb(0.55f, 0.45f, 0.33f, 1f), 0.6f), DUST, 2.0f, 0.9f, gravity = 0.5f)
                burst(frame, c.x, c.y, c.z, 50, 7.0, 2.0, 1221, withAlpha(main, 1f), SPARK, 1.0f, 0.12f, gravity = 9f)
            }
            RegiElement.ICE -> {
                burst(frame, c.x, c.y, c.z, 120, 7.0, 1.5, 1231, withAlpha(light, 1f), FLAKE, 1.6f, 0.16f, gravity = 3f)
                burst(frame, c.x, c.y, c.z, 40, 3.0, 0.2, 1241, withAlpha(accent, 0.4f), DUST, 2.2f, 1.2f)
            }
            RegiElement.STEEL -> burst(frame, c.x, c.y, c.z, 140, 8.0, 2.0, 1251, withAlpha(argb(1f, 0.82f, 0.5f, 1f), 1f), SPARK, 0.9f, 0.1f, gravity = 12f, drag = 0.6f)
            RegiElement.ELECTRIC -> {
                burst(frame, c.x, c.y, c.z, 120, 9.0, 0.0, 1261, withAlpha(light, 1f), SPARK, 0.7f, 0.12f, drag = 1.0f)
                burst(frame, c.x, c.y, c.z, 30, 4.0, 0.0, 1271, withAlpha(accent, 0.9f), MOTE, 0.9f, 0.4f)
            }
            RegiElement.DRAGON -> {
                burst(frame, c.x, c.y, c.z, 90, 6.0, 1.0, 1281, withAlpha(light, 1f), EMBER, 1.6f, 0.14f, gravity = -0.5f)
                burst(frame, c.x, c.y, c.z, 40, 2.5, 0.8, 1291, withAlpha(dark, 0.6f), DUST, 2.4f, 1.3f)
            }
            RegiElement.GIANT -> {
                groundBurst(frame, 90, 6.0, 1301, withAlpha(argb(0.6f, 0.56f, 0.5f, 1f), 0.5f), DUST, 2.2f, 1.0f)
                for (i in 0 until 5) burst(frame, c.x, c.y + s.h * 0.3, c.z, 16, 4.0, 0.5, 1311 + i * 3, withAlpha(orbColours[i], 1f), MOTE, 1.3f, 0.3f)
            }
        }
    }

    // ---------------------------------------------------------------- each frame

    override fun update(frame: SceneFrame) {
        val a = frame.a
        val c = s.centre
        frame.camera.toWorld(c.x, c.y, c.z, world)
        frame.particles.axisX = world.x
        frame.particles.axisZ = world.z
        val g = s.ground(a)
        val core = s.core(a)
        // The shrine trembling: dust off it.
        emit(frame, 10.0, s.rumble(a), 2001) { i ->
            val ang = random(i, 1) * 2.0 * PI
            emitLocal(frame, DUST, sin(ang) * 0.6, 0.4 + random(i, 2) * 0.4, cos(ang) * 0.6, sin(ang) * 0.4, 0.3, cos(ang) * 0.4, 1.6f, 0.25f, 0.8f, withAlpha(argb(0.6f, 0.56f, 0.5f, 1f), 0.3f), drag = 0.8f)
        }
        // Light gathering into the core.
        emit(frame, 40.0, core * (1.0 - s.converge(a) * 0.5), 2011) { i ->
            val th = random(i, 1) * 2.0 * PI
            val ph = (random(i, 2) - 0.5) * PI
            val r = 2.5 + random(i, 3) * 1.5 + s.w * 0.4
            val ox = cos(ph) * sin(th) * r
            val oy = sin(ph) * r
            val oz = cos(ph) * cos(th) * r
            val life = 0.8
            emitLocal(frame, MOTE, c.x + ox, c.y + oy, c.z + oz, -ox / life * 0.95, -oy / life * 0.95, -oz / life * 0.95, life.toFloat(), 0.16f, 0.04f, withAlpha(if (i % 3 == 0) white else light, 0.85f))
        }
        when (e) {
            RegiElement.ROCK -> {
                // Dust rising off the cracks, small grit falling from above.
                emit(frame, 18.0, g, 2101) { i ->
                    val ang = random(i, 1) * 2.0 * PI
                    val r = 1.0 + random(i, 2) * 4.0
                    emitLocal(frame, DUST, sin(ang) * r, 0.1, cos(ang) * r, 0.0, 0.35 + random(i, 3) * 0.4, 0.0, 2.2f, 0.4f, 1.3f, withAlpha(argb(0.62f, 0.52f, 0.4f, 1f), 0.28f), drag = 0.6f, swirl = 0.3f)
                }
                emit(frame, 14.0, s.rumble(a), 2111) { i ->
                    val x = (random(i, 1) - 0.5) * 6.0
                    val z = (random(i, 2) - 0.5) * 6.0
                    val top = min(s.ceiling - 0.2, 7.0)
                    emitLocal(frame, SPARK, x, top, z, 0.0, -0.5, 0.0, 1.4f, 0.05f, 0.04f, withAlpha(argb(0.5f, 0.45f, 0.38f, 1f), 0.9f), gravity = 6f)
                }
            }
            RegiElement.ICE -> {
                emit(frame, 22.0, g, 2201) { i ->
                    val ang = random(i, 1) * 2.0 * PI
                    val r = 0.5 + random(i, 2) * 5.0
                    emitLocal(frame, DUST, sin(ang) * r, 0.15, cos(ang) * r, cos(ang) * 0.4, 0.05, -sin(ang) * 0.4, 3.0f, 0.8f, 2.0f, withAlpha(argb(0.85f, 0.93f, 1f, 1f), 0.18f), drag = 0.3f, swirl = 0.4f)
                }
                emit(frame, 30.0, g, 2211) { i ->
                    val ang = random(i, 1) * 2.0 * PI
                    val r = random(i, 2) * 5.0
                    emitLocal(frame, FLAKE, sin(ang) * r, 0.5 + random(i, 3) * 3.0, cos(ang) * r, 0.0, 0.15, 0.0, 2.5f, 0.07f, 0.04f, withAlpha(light, 0.85f), swirl = 0.6f)
                }
            }
            RegiElement.STEEL -> {
                emit(frame, 30.0, CameraMath.window(a, s.formAt, s.formAt + 0.5, s.revealAt - 0.3, s.revealAt), 2301) { i ->
                    val ang = random(i, 1) * 2.0 * PI
                    val y = c.y + (random(i, 2) - 0.5) * s.h * 0.8
                    val r = s.w * 0.5
                    emitLocal(frame, SPARK, c.x + sin(ang) * r, y, c.z + cos(ang) * r, sin(ang) * 2.5, 1.0 + random(i, 3), cos(ang) * 2.5, 0.8f, 0.07f, 0.03f, withAlpha(argb(1f, 0.8f, 0.45f, 1f), 1f), gravity = 9f, drag = 0.5f)
                }
            }
            RegiElement.ELECTRIC -> {
                emit(frame, 26.0, g, 2401) { i ->
                    val ang = random(i, 1) * 2.0 * PI
                    val r = random(i, 2) * 4.0
                    emitLocal(frame, SPARK, sin(ang) * r, 0.1 + random(i, 3) * 1.5, cos(ang) * r, (random(i, 4) - 0.5) * 3.0, 1.0, (random(i, 5) - 0.5) * 3.0, 0.4f, 0.08f, 0.02f, withAlpha(light, 1f), drag = 2f)
                }
            }
            RegiElement.DRAGON -> {
                emit(frame, 16.0, g, 2501) { i ->
                    val ang = random(i, 1) * 2.0 * PI
                    val r = 0.8 + random(i, 2) * 3.5
                    emitLocal(frame, DUST, sin(ang) * r, 0.1, cos(ang) * r, 0.0, 0.5 + random(i, 3) * 0.5, 0.0, 2.6f, 0.4f, 1.4f, withAlpha(argb(0.3f, 0.04f, 0.06f, 1f), 0.35f), drag = 0.5f, swirl = 0.8f)
                }
                emit(frame, 24.0, s.core(a), 2511) { i ->
                    val ang = random(i, 1) * 2.0 * PI
                    val r = s.w * 0.7 + 0.8
                    emitLocal(frame, EMBER, c.x + sin(ang) * r, c.y + (random(i, 2) - 0.5) * s.h, c.z + cos(ang) * r, cos(ang) * 1.5, 0.6, -sin(ang) * 1.5, 1.4f, 0.1f, 0.04f, withAlpha(light, 0.95f), swirl = 1.2f)
                }
            }
            RegiElement.GIANT -> {
                emit(frame, 16.0, g, 2601) { i ->
                    val p = i % 5
                    val ang = pillarAngle(p)
                    val r = pillarRadius()
                    emitLocal(frame, MOTE, sin(ang) * r, random(i, 2) * pillarHeight(a), cos(ang) * r, 0.0, 1.2, 0.0, 1.6f, 0.18f, 0.05f, withAlpha(orbColours[p], 0.85f))
                }
                emit(frame, 12.0, s.rumble(a), 2611) { i ->
                    val ang = random(i, 1) * 2.0 * PI
                    val r = 2.0 + random(i, 2) * 6.0
                    emitLocal(frame, DUST, sin(ang) * r, 0.1, cos(ang) * r, 0.0, 0.4, 0.0, 2.4f, 0.5f, 1.6f, withAlpha(argb(0.6f, 0.56f, 0.5f, 1f), 0.25f), drag = 0.5f)
                }
            }
        }
        // The letters' trails.
        for (i in s.letters.indices) {
            s.letter(i, a, piece)
            if (piece.scale <= 0.01) continue
            emit(frame, 18.0, 1.0, 2701 + i * 13) { n ->
                emitLocal(frame, MOTE, piece.pos.x + (random(n, 1) - 0.5) * 0.4, piece.pos.y + (random(n, 2) - 0.5) * 0.4, piece.pos.z + (random(n, 3) - 0.5) * 0.4, 0.0, 0.1, 0.0, 0.8f, 0.14f, 0.03f, withAlpha(main, 0.8f))
            }
        }
        frame.particles.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        drawRunes(frame, batches, a)
        drawGround(frame, batches, a)
        drawLetters(frame, batches, a)
        drawCore(frame, batches, a)
        drawElement(frame, batches, a)
        drawLights(frame, batches, a)
        drawShock(frame, batches, a)
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    private fun drawRunes(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.runes(a).toFloat()
        if (k < 0.01f) return
        val colour = if (e == RegiElement.DRAGON) main else lerpColour(main, light, 0.3f)
        val pulse = 0.8f + 0.2f * sin(a.toFloat() * 3f)
        val o = frame.rel(0.0, 0.04, 0.0, v3)
        val ox = o.x; val oy = o.y; val oz = o.z
        val r = if (orbs) 4.6f else 2.4f
        FxMeshes.ring(batches.of(runeRing), ox, oy, oz, r - 0.18f, r + 0.18f, 64, withAlpha(colour, 0.75f * k * pulse), withAlpha(colour, 0.75f * k * pulse), 4f, a.toFloat() * 0.05f)
        FxMeshes.ring(batches.of(runeRing), ox, oy, oz, r * 0.5f - 0.1f, r * 0.5f + 0.1f, 48, withAlpha(colour, 0.6f * k), withAlpha(colour, 0.6f * k), 3f, -a.toFloat() * 0.08f)
        // The marks between the two circles: three strokes each, lit one after the other.
        val marks = if (orbs) 20 else 12
        for (i in 0 until marks) {
            val lit = smooth(phase(a, 0.8 + i * 0.15, 1.2 + i * 0.15)).toFloat() * k
            if (lit < 0.01f) continue
            val ang = 2.0 * PI * i / marks + a * 0.05
            val rr = r * 0.75
            for (stroke in 0 until 3) {
                val off = (stroke - 1) * 0.16
                val x = sin(ang) * rr + cos(ang) * off
                val z = cos(ang) * rr - sin(ang) * off
                val m = frame.rel(x, 0.05, z, w3)
                val turn = (ang + random(i * 3 + stroke, 81) * PI).toFloat()
                FxMeshes.flat(batches.of(runeMarks), m.x, m.y, m.z, 0.16f, turn, withAlpha(colour, 0.9f * lit * pulse))
            }
        }
        FxMeshes.flat(batches.of(groundGlow), ox, oy + 0.01f, oz, r * 1.3f, 0f, withAlpha(colour, 0.35f * k))
        if (e == RegiElement.DRAGON) {
            // Red runes up the shrine's air too.
            for (i in 0 until 6) {
                val y = 0.6 + i * 0.5 + 0.2 * sin(a + i)
                val ang = a * 0.6 + i * 1.05
                halo(frame, batches, sin(ang) * 1.4, y, cos(ang) * 1.4, 0.35f, withAlpha(main, 0.7f * k))
            }
        }
    }

    private fun drawGround(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.ground(a).toFloat()
        if (k < 0.01f) return
        val o = frame.rel(0.0, 0.03, 0.0, v3)
        val ox = o.x; val oy = o.y; val oz = o.z
        when (e) {
            RegiElement.ROCK -> {
                // Glowing cracks out of the shrine, growing.
                val grow = smooth(phase(a, 0.6, 4.0))
                for (i in 0 until 9) {
                    val ang = i * 2.0 * PI / 9 + random(i, 91) * 0.4
                    val len = (2.0 + 2.5 * random(i, 92)) * grow
                    val a0 = frame.rel(sin(ang) * 0.6, 0.03, cos(ang) * 0.6, v3)
                    val ax = a0.x; val ay = a0.y; val az = a0.z
                    val b0 = frame.rel(sin(ang) * (0.6 + len), 0.03, cos(ang) * (0.6 + len), w3)
                    FxMeshes.bolt(batches.of(bolts), ax, ay, az, b0.x, b0.y, b0.z, 7001 + i * 13, 0.18f, 0.06f, withAlpha(main, 0.85f * k * (0.8f + 0.2f * sin(a.toFloat() * 5f + i))))
                }
                FxMeshes.flat(batches.of(groundGlow), ox, oy, oz, 3.5f, 0f, withAlpha(main, 0.3f * k))
            }
            RegiElement.ICE -> {
                FxMeshes.flat(batches.of(frost), ox, oy, oz, (2.0 + 4.0 * smooth(phase(a, 0.6, 5.0))).toFloat(), 0.4f, withAlpha(light, 0.75f * k))
                FxMeshes.flat(batches.of(groundGlow), ox, oy + 0.01f, oz, 3f, 0f, withAlpha(main, 0.25f * k))
                for (i in 0 until 10) {
                    val ang = i * 2.39996 + a * 0.03
                    val r = 2.5 + 3.0 * random(i, 93)
                    val c = frame.rel(sin(ang) * r, 0.5 + random(i, 94) * 1.2, cos(ang) * r, w3)
                    FxMeshes.billboard(batches.of(haze), c.x, c.y, c.z, (2.5 + 1.5 * random(i, 95)).toFloat(), (a * 0.05 + i).toFloat(), withAlpha(light, 0.18f * k), frame.right, frame.up)
                }
            }
            RegiElement.STEEL -> {
                // Lines of energy across the floor, a pulse running out along each.
                for (i in 0 until 8) {
                    val ang = i * PI / 4
                    val len = 5.0 * smooth(phase(a, 0.6 + i * 0.1, 2.6 + i * 0.1))
                    val a0 = frame.rel(sin(ang) * 0.7, 0.04, cos(ang) * 0.7, v3)
                    val ax = a0.x; val ay = a0.y; val az = a0.z
                    val b0 = frame.rel(sin(ang) * (0.7 + len), 0.04, cos(ang) * (0.7 + len), w3)
                    FxMeshes.beam(batches.of(streaks), ax, ay, az, b0.x, b0.y, b0.z, 0.09f, 0.05f, withAlpha(accent, 0.8f * k), withAlpha(accent, 0.2f * k))
                    val p = (((a * 0.8 + i * 0.13) % 1.0) + 1.0) % 1.0 * len
                    val m = frame.rel(sin(ang) * (0.7 + p), 0.06, cos(ang) * (0.7 + p), w3)
                    FxMeshes.billboard(batches.of(glows), m.x, m.y, m.z, 0.35f, 0f, withAlpha(white, 0.8f * k), frame.right, frame.up)
                }
                // Four plates lighting up round the shrine.
                for (i in 0 until 4) {
                    val ang = PI / 4 + i * PI / 2
                    val lit = smooth(phase(a, 1.4 + i * 0.4, 1.9 + i * 0.4)).toFloat()
                    val m = frame.rel(sin(ang) * 2.6, 0.05, cos(ang) * 2.6, w3)
                    FxMeshes.flat(batches.of(groundGlow), m.x, m.y, m.z, 0.9f, ang.toFloat(), withAlpha(lerpColour(main, accent, 0.4f), 0.6f * lit * k))
                }
            }
            RegiElement.ELECTRIC -> {
                // Arcs crawling on the floor, new ones 12 times a second (the same on every client).
                val seed = (a * 12.0).toInt()
                for (i in 0 until 5) {
                    val r0 = 0.6 + 2.5 * random(seed * 7 + i, 96)
                    val a0 = random(seed * 7 + i, 97) * 2.0 * PI
                    val a1 = a0 + (random(seed * 7 + i, 98) - 0.5) * 1.6
                    val r1 = r0 + 0.8 + 1.5 * random(seed * 7 + i, 99)
                    val p0 = frame.rel(sin(a0) * r0, 0.06, cos(a0) * r0, v3)
                    val px = p0.x; val py = p0.y; val pz = p0.z
                    val p1 = frame.rel(sin(a1) * r1, 0.06, cos(a1) * r1, w3)
                    FxMeshes.bolt(batches.of(bolts), px, py, pz, p1.x, p1.y, p1.z, seed * 31 + i, 0.25f, 0.04f, withAlpha(light, 0.9f * k))
                }
                // The strikes from the core to the ground.
                for ((i, strike) in s.strikes.withIndex()) {
                    val age = a - strike[0]
                    if (age < 0.0 || age > 0.25) continue
                    val f = (1.0 - age / 0.25).toFloat()
                    val from = frame.rel(s.centre.x, s.centre.y, s.centre.z, v3)
                    val fx = from.x; val fy = from.y; val fz = from.z
                    val to = frame.rel(sin(strike[1]) * strike[2], 0.05, cos(strike[1]) * strike[2], w3)
                    FxMeshes.bolt(batches.of(bolts), fx, fy, fz, to.x, to.y, to.z, 8101 + i * 17, 0.35f, 0.09f, withAlpha(light, f))
                    halo(frame, batches, sin(strike[1]) * strike[2], 0.2, cos(strike[1]) * strike[2], 1.8f, withAlpha(main, 0.7f * f))
                }
                FxMeshes.flat(batches.of(groundGlow), ox, oy, oz, 4f, 0f, withAlpha(main, (0.25f + 0.3f * s.flicker(a).toFloat()) * k))
            }
            RegiElement.DRAGON -> {
                FxMeshes.flat(batches.of(groundGlow), ox, oy, oz, 5f, 0f, withAlpha(main, (0.35f + 0.1f * sin(a.toFloat() * 2f)) * k))
                FxMeshes.flat(batches.of(groundGlow), ox, oy + 0.01f, oz, 2.4f, 0f, withAlpha(dark, 0.6f * k))
            }
            RegiElement.GIANT -> {
                // The five pillars of light, one per Regi.
                val top = pillarHeight(a).toFloat()
                for (p in 0 until 5) {
                    val lit = smooth(phase(a, 0.8 + p * 0.5, 1.6 + p * 0.5)).toFloat() * k
                    if (lit < 0.01f) continue
                    val ang = pillarAngle(p)
                    val r = pillarRadius()
                    val c = frame.rel(sin(ang) * r, 0.0, cos(ang) * r, w3)
                    FxMeshes.cylinder(batches.of(column), c.x, c.y, c.z, 0f, top * lit, 0.55f, 0.4f, 24, 6, withAlpha(orbColours[p], 0.8f * lit), withAlpha(orbColours[p], 0f), 2f, a.toFloat() * 0.3f, 1.5f, -a.toFloat() * 0.8f, 0.4f)
                    FxMeshes.flat(batches.of(groundGlow), c.x, c.y + 0.04f, c.z, 1.6f, 0f, withAlpha(orbColours[p], 0.6f * lit))
                }
            }
        }
    }

    private fun pillarAngle(p: Int): Double = PI + (p - 2) * 0.62
    private fun pillarRadius(): Double = (3.2 + s.w * 0.5).coerceAtMost(s.room + 2.0)
    private fun pillarHeight(a: Double): Double = min(s.ceiling - 0.1, 10.0) * smooth(phase(a, 0.8, 3.5))

    private fun drawLetters(frame: SceneFrame, batches: FxBatches, a: Double) {
        for (i in s.letters.indices) {
            s.letter(i, a, piece)
            if (piece.scale <= 0.01) continue
            val lit = smooth(phase(a, s.liftAt + 0.12 * i, s.liftAt + 0.12 * i + 0.5)).toFloat()
            halo(frame, batches, piece.pos.x, piece.pos.y, piece.pos.z, (1.6 * piece.scale).toFloat(), withAlpha(main, 0.55f * lit), a.toFloat())
        }
        if (!orbs) return
        // Regigigas: its five orbs over the shrine, rising, turning, then into the core.
        val shown = CameraMath.window(a, 0.3, 1.2, s.coreAt - 0.3, s.coreAt + 0.2)
        if (shown < 0.01) return
        for (p in 0 until 5) {
            val into = smooth(phase(a, s.gatherAt, s.coreAt))
            val ang = p * 2.0 * PI / 5 + a * 0.8
            val r = 1.6 * (1.0 - into)
            val y = CameraMath.lerp(1.0, s.centre.y, into) + 0.8 * smooth(phase(a, 0.3, 2.0)) * (1.0 - into)
            val c = frame.rel(sin(ang) * r, y, cos(ang) * r, v3)
            FxMeshes.sphere(batches.of(coreMat), c.x, c.y, c.z, 0.22f, withAlpha(orbColours[p], 0.95f * shown.toFloat()), a.toFloat())
            halo(frame, batches, sin(ang) * r, y, cos(ang) * r, 1.4f, withAlpha(orbColours[p], 0.7f * shown.toFloat()))
        }
    }

    private fun drawCore(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.core(a).toFloat()
        if (k < 0.01f) return
        val c = s.centre
        val size = s.coreSize(a).toFloat()
        val beat = 0.85f + 0.15f * sin(a.toFloat() * (if (e == RegiElement.DRAGON) 4.5f else 2.5f))
        val radius = (0.25f + 0.35f * size) * beat * (1f - 0.4f * s.converge(a).toFloat())
        val p = frame.rel(c.x, c.y, c.z, v3)
        val px = p.x; val py = p.y; val pz = p.z
        FxMeshes.sphere(batches.of(coreMat), px, py, pz, radius, withAlpha(main, 0.9f * k), a.toFloat() * 0.5f)
        FxMeshes.sphere(batches.of(coreMat), px, py, pz, radius * 0.55f, withAlpha(white, 0.9f * k), -a.toFloat())
        halo(frame, batches, c.x, c.y, c.z, radius * 6f, withAlpha(main, 0.55f * k), a.toFloat() * 0.2f)
        halo(frame, batches, c.x, c.y, c.z, radius * 2.5f, withAlpha(light, 0.8f * k))
        // The shape's glow round the body.
        val ghost = (s.ghost(a) * (1.0 - smooth(phase(a, s.revealAt - 0.2, s.revealAt + 0.3)))).toFloat()
        if (ghost > 0.01f) halo(frame, batches, c.x, c.y, c.z, (max(s.h, s.w) * 1.6).toFloat(), withAlpha(main, 0.25f * ghost * k), -a.toFloat() * 0.1f)
        // Regigigas: the light it rises from.
        if (orbs) {
            val o = frame.rel(0.0, 0.0, 0.0, w3)
            val hgt = (s.h * 1.25).toFloat()
            FxMeshes.cylinder(batches.of(column), o.x, o.y, o.z, 0f, hgt, (s.w * 0.65).toFloat(), (s.w * 0.4).toFloat(), 32, 6, withAlpha(light, 0.55f * k), withAlpha(light, 0f), 2f, a.toFloat() * 0.2f, 1.2f, -a.toFloat() * 0.6f, 0.3f)
        }
    }

    private fun drawElement(frame: SceneFrame, batches: FxBatches, a: Double) {
        val c = s.centre
        when (e) {
            RegiElement.ICE -> {
                // The crystal round Regice: seven tall prisms leaning outward, cracking, then gone.
                val grow = s.shell(a).toFloat()
                if (grow < 0.01f) return
                val crack = s.cracks(a).toFloat()
                val buffer = batches.of(solid)
                for (i in 0 until 7) {
                    val ang = i * 2.0 * PI / 7 + 0.3
                    val r = s.w * 0.42
                    val base = frame.rel(c.x + sin(ang) * r, s.landing.y - 0.2, c.z + cos(ang) * r, v3)
                    q.identity().rotateY(ang.toFloat()).rotateX(Math.toRadians(14.0 + 8.0 * random(i, 101)).toFloat())
                    frame.camera.rotationToWorld(q, qw)
                    val tall = (s.h * (0.95 + 0.3 * random(i, 102))).toFloat() * grow
                    val wide = (s.w * 0.3 + 0.15).toFloat() * (0.6f + 0.4f * grow)
                    // Dissolving where it cracks (the crystal shader's alpha).
                    FxMeshes.crystal(buffer, base.x, base.y, base.z, qw, wide, tall, argb(1f, 1f, 1f, 1f - 0.35f * crack * (0.5f + 0.5f * random(i, 103).toFloat())))
                }
                // The cold light pulsing inside, and the cracks as white lines.
                halo(frame, batches, c.x, c.y, c.z, (s.h * 1.2).toFloat(), withAlpha(main, (0.35f + 0.15f * sin(a.toFloat() * 3f)) * grow))
                if (crack > 0.01f) {
                    for (i in 0 until 6) {
                        val ang = i * 1.05 + 0.2
                        val r = s.w * 0.55
                        val y0 = c.y + (random(i, 104) - 0.5) * s.h * 0.8
                        val p0 = frame.rel(c.x + sin(ang) * r, y0, c.z + cos(ang) * r, v3)
                        val px = p0.x; val py = p0.y; val pz = p0.z
                        val len = s.h * 0.5 * crack
                        val p1 = frame.rel(c.x + sin(ang + 0.4) * r, y0 + len * (if (i % 2 == 0) 1 else -1), c.z + cos(ang + 0.4) * r, w3)
                        FxMeshes.bolt(batches.of(bolts), px, py, pz, p1.x, p1.y, p1.z, 9101 + i * 7, 0.15f, 0.025f, withAlpha(white, 0.9f * crack))
                    }
                }
            }
            RegiElement.STEEL -> {
                // Three steel rings round its body, closing in, then locked at the reveal.
                if (a >= s.revealAt + 0.15) return
                val shown = CameraMath.window(a, s.coreAt, s.shapeAt, s.revealAt - 0.1, s.revealAt + 0.15).toFloat()
                if (shown < 0.01f) return
                val close = s.converge(a)
                for (i in 0 until 3) {
                    val y = s.landing.y + s.h * (0.25 + 0.25 * i)
                    val major = ((s.w * 0.6 + 0.9 - 0.25 * i) * (1.0 - 0.32 * close)).toFloat()
                    val p = frame.rel(c.x, y + 0.08 * sin(a * 2.0 + i), c.z, v3)
                    FxMeshes.torus(batches.of(solid), p.x, p.y, p.z, major * shown, 0.09f, 48, 8, argb(1f, 1f, 1f, 1f), (a * (0.3 + 0.2 * i)).toFloat())
                    tiltedRing(frame, batches, major.toDouble() + 0.18, 6.0 + 4.0 * i, a * (if (i % 2 == 0) 1.2 else -1.0), 0.06f, withAlpha(accent, 0.7f * shown), withAlpha(white, 0.5f * shown))
                }
                // The white heart of the forge.
                halo(frame, batches, c.x, c.y, c.z, (s.h * 0.9).toFloat(), withAlpha(white, 0.4f * shown))
            }
            RegiElement.ELECTRIC -> {
                // The sphere of energy round Regieleki, its rings locking round the body.
                val shown = CameraMath.window(a, s.coreAt - 0.3, s.shapeAt, s.revealAt - 0.05, s.revealAt + 0.25).toFloat()
                if (shown < 0.01f) return
                val radius = (max(s.h, s.w) * 0.62 + 0.4).toFloat() * smooth(phase(a, s.coreAt - 0.3, s.shapeAt + 0.6)).toFloat()
                val p = frame.rel(c.x, c.y, c.z, v3)
                val px = p.x; val py = p.y; val pz = p.z
                FxMeshes.sphere(batches.of(shellMat), px, py, pz, radius, withAlpha(main, (0.55f + 0.25f * s.flicker(a).toFloat()) * shown), a.toFloat() * 1.3f)
                halo(frame, batches, c.x, c.y, c.z, radius * 3.2f, withAlpha(main, 0.45f * shown))
                val lock = s.converge(a)
                for (i in 0 until 3) {
                    val tilt = CameraMath.lerp(35.0 + 30.0 * i, 90.0, lock)
                    val turn = a * (2.2 + 0.6 * i) * (1.0 - 0.8 * lock) + i * 2.1
                    tiltedRing(frame, batches, radius.toDouble() * CameraMath.lerp(1.15, 0.75, lock), tilt, turn, 0.08f, withAlpha(light, 0.9f * shown), withAlpha(accent, 0.7f * shown), bolts)
                }
                // Arcs crawling on the sphere.
                val seed = (a * 16.0).toInt()
                for (i in 0 until 4) {
                    val t0 = random(seed * 5 + i, 105) * 2.0 * PI
                    val f0 = (random(seed * 5 + i, 106) - 0.5) * PI
                    val t1 = t0 + 0.9
                    val r = radius.toDouble()
                    val a0 = frame.rel(c.x + cos(f0) * sin(t0) * r, c.y + sin(f0) * r, c.z + cos(f0) * cos(t0) * r, v3)
                    val ax = a0.x; val ay = a0.y; val az = a0.z
                    val b0 = frame.rel(c.x + cos(f0) * sin(t1) * r, c.y + sin(f0 + 0.5) * r, c.z + cos(f0) * cos(t1) * r, w3)
                    FxMeshes.bolt(batches.of(bolts), ax, ay, az, b0.x, b0.y, b0.z, seed * 13 + i, 0.2f, 0.035f, withAlpha(light, shown))
                }
            }
            RegiElement.DRAGON -> {
                // The red mass, and the spectral jaws turning round it.
                val shown = CameraMath.window(a, s.coreAt - 0.3, s.shapeAt, s.revealAt - 0.1, s.revealAt + 0.3).toFloat()
                if (shown < 0.01f) return
                val radius = (max(s.h, s.w) * 0.55 + 0.3).toFloat() * smooth(phase(a, s.coreAt - 0.3, s.shapeAt + 0.8)).toFloat() * (1f - 0.3f * s.converge(a).toFloat())
                val p = frame.rel(c.x, c.y, c.z, v3)
                FxMeshes.sphere(batches.of(shellMat), p.x, p.y, p.z, radius, withAlpha(main, 0.5f * shown), -a.toFloat() * 0.8f)
                halo(frame, batches, c.x, c.y, c.z, radius * 3f, withAlpha(dark, 0.5f * shown))
                for (side in 0 until 2) jaw(frame, batches, a, side, radius.toDouble(), shown)
                // Flames winding round it.
                val o = frame.rel(c.x, s.landing.y, c.z, w3)
                for (i in 0 until 3) {
                    FxMeshes.spiral(batches.of(streaks), o.x, o.y, o.z, 0f, (s.h * 1.1).toFloat(), radius * 1.1f, radius * 0.5f, 1.6f, -a.toFloat() * 0.5f + i / 3f, 0.14f, withAlpha(light, 0.8f * shown), withAlpha(main, 0f), 40)
                }
            }
            RegiElement.ROCK, RegiElement.GIANT -> {
                // The fragments are real blocks (props); a ring of dust round them.
                val k = CameraMath.window(a, s.coreAt, s.shapeAt, s.revealAt - 0.3, s.revealAt).toFloat()
                if (k < 0.01f) return
                tiltedRing(frame, batches, s.w * 0.8 + (if (orbs) 2.6 else 1.4), 8.0, a * 0.5, 0.12f, withAlpha(main, 0.45f * k), withAlpha(light, 0.2f * k))
            }
        }
    }

    /** A spectral dragon's jaw round the red mass: an arc with teeth, opening and closing. */
    private fun jaw(frame: SceneFrame, batches: FxBatches, a: Double, side: Int, radius: Double, shown: Float) {
        val c = s.centre
        val around = a * 0.9 + side * PI
        val open = 0.35 + 0.25 * sin(a * 2.2 + side)
        val cx = c.x + sin(around) * (radius + 0.6)
        val cz = c.z + cos(around) * (radius + 0.6)
        for (half in 0 until 2) {
            val sign = if (half == 0) 1.0 else -1.0
            val n = 14
            for (i in 0 until n) {
                val u = i / (n - 1.0)
                val bend = sign * (open * sin(PI * u) + 0.05)
                val along = (u - 0.5) * 1.6
                val x = cx + cos(around) * along * 0.6
                val y = c.y + bend * 1.1
                val z = cz - sin(around) * along * 0.6
                frame.rel(x, y, z, w3)
                pts[i * 3] = w3.x; pts[i * 3 + 1] = w3.y; pts[i * 3 + 2] = w3.z
            }
            FxMeshes.ribbon(batches.of(streaks), pts, n, 0.12f, 0.04f, withAlpha(light, 0.85f * shown), withAlpha(main, 0.5f * shown))
            for (t in 1 until 6) {
                val u = t / 6.0
                val bend = sign * (open * sin(PI * u) + 0.05)
                val along = (u - 0.5) * 1.6
                val x = cx + cos(around) * along * 0.6
                val y = c.y + bend * 1.1
                val z = cz - sin(around) * along * 0.6
                val a0 = frame.rel(x, y, z, v3)
                val ax = a0.x; val ay = a0.y; val az = a0.z
                val b0 = frame.rel(x, y - sign * 0.28, z, w3)
                FxMeshes.beam(batches.of(streaks), ax, ay, az, b0.x, b0.y, b0.z, 0.07f, 0.01f, withAlpha(white, 0.8f * shown), withAlpha(light, 0f))
            }
        }
    }

    private fun drawLights(frame: SceneFrame, batches: FxBatches, a: Double) {
        if (s.lights(a) < 0.01) return
        val n = s.lightCount
        val spacing = (min(s.w, s.h) * 0.11).coerceIn(0.08, 0.3)
        for (i in 0 until n) {
            val k = s.light(i, n, a).toFloat()
            if (k < 0.01f) continue
            val (dx, dy) = lightOffset(i)
            val colour = if (orbs) orbColours[i % 5] else dot
            halo(frame, batches, s.face.x + dx * spacing, s.face.y + dy * spacing, s.face.z, 0.32f * (1f + 0.4f * (1f - k)), withAlpha(colour, 0.95f * k))
            halo(frame, batches, s.face.x + dx * spacing, s.face.y + dy * spacing, s.face.z, 0.12f, withAlpha(white, 0.9f * k))
        }
    }

    /** Where light i sits on its face, in units of the spacing (x right, y up). */
    private fun lightOffset(i: Int): Pair<Double, Double> = when (e) {
        RegiElement.ICE -> listOf(-1.0 to 1.0, -1.0 to 0.0, -1.0 to -1.0, 0.0 to 0.0, 1.0 to 1.0, 1.0 to 0.0, 1.0 to -1.0)[i % 7]
        RegiElement.STEEL -> if (i == 6) 0.0 to 0.0 else (1.3 * sin(i * PI / 3)) to (1.3 * cos(i * PI / 3))
        RegiElement.ELECTRIC -> (i - 1.5) to 0.0
        RegiElement.DRAGON -> listOf(-2.0 to 1.0, -1.0 to 0.0, 0.0 to -1.0, 1.0 to 0.0, 2.0 to 1.0)[i % 5]
        RegiElement.ROCK -> listOf(-1.0 to 1.0, 1.0 to 1.0, -1.5 to 0.0, 0.0 to 0.0, 1.5 to 0.0, -1.0 to -1.0, 1.0 to -1.0)[i % 7]
        RegiElement.GIANT -> listOf(-1.0 to 0.6, 1.0 to 0.6, -2.0 to 0.0, 2.0 to 0.0, 0.0 to 0.0, -1.0 to -0.6, 1.0 to -0.6)[i % 7]
    }

    private fun drawShock(frame: SceneFrame, batches: FxBatches, a: Double) {
        val u = s.shock(a)
        if (u <= 0.0 || u >= 1.0) return
        val o = frame.rel(0.0, 0.06, 0.0, v3)
        val radius = ((if (orbs) 22.0 else 14.0) * easeOutCubic(u)).toFloat()
        val alpha = (0.75 * (1.0 - u)).toFloat()
        val colour = lerpColour(main, light, 0.4f)
        FxMeshes.ring(batches.of(runeRing), o.x, o.y, o.z, (radius - 1.2f).coerceAtLeast(0f), radius, 72, withAlpha(colour, 0f), withAlpha(colour, alpha), 6f, 0f)
        FxMeshes.ring(batches.of(runeRing), o.x, o.y, o.z, radius, radius + 0.6f, 72, withAlpha(colour, alpha), withAlpha(colour, 0f), 6f, 0f)
    }

    // ---------------------------------------------------------------- the image

    override fun post(frame: SceneFrame, out: PostParams) {
        val a = frame.a
        val c = s.centre
        // The reveal's wave from the body, the cry's from its face.
        val r = a - s.revealAt
        if (r >= 0.0 && r < 1.2) {
            val p = frame.rel(c.x, c.y, c.z, v3)
            if (frame.project(p, uv)) out.wave(0, uv[0], uv[1], frame.screenRadius(p, (8.0 * easeOutCubic(r / 1.2)).toFloat()), (0.7 * (1.0 - smooth(r / 1.2))).toFloat(), 0.05f)
        }
        val cry = a - s.cryAt
        if (cry >= 0.0 && cry < 0.9) {
            val p = frame.rel(s.face.x, s.face.y, s.face.z, v3)
            if (frame.project(p, uv)) out.wave(1, uv[0], uv[1], (0.7 * easeOutCubic(cry / 0.9)).toFloat(), (0.5 * (1.0 - smooth(cry / 0.9))).toFloat(), 0.05f)
        }
        val core = s.core(a)
        val g = s.ground(a)
        out.heat = when (e) {
            RegiElement.DRAGON -> 0.25 * g + 0.3 * core
            RegiElement.GIANT -> 0.1 * core
            RegiElement.ELECTRIC -> 0.08 * core
            else -> 0.04 * core
        }.toFloat()
        out.heatSpeed = if (e == RegiElement.DRAGON) 0.5f else 0.3f
        out.heatScale = 3f
        val radial = 0.35 * CameraMath.pulse(a, s.revealAt, 0.05, 0.4) + (if (orbs) 0.3 * CameraMath.decay(a, s.cryAt + 0.1, 0.5) else 0.0)
        if (radial > 0.004) {
            val p = frame.rel(c.x, c.y, c.z, v3)
            if (frame.project(p, uv)) {
                out.radialX = uv[0]
                out.radialY = uv[1]
                out.radial = radial.toFloat()
            }
        }
        val rays = (0.55 * core).toFloat()
        if (rays > 0.004f && frame.project(frame.rel(c.x, c.y, c.z, v3), uv)) {
            out.raysX = uv[0]
            out.raysY = uv[1]
            out.rays = rays
            out.raysDecay = 0.95f
        }
        out.bloom = (0.9 + 0.4 * core + 0.3 * s.flicker(a) + 0.2 * s.lights(a)).toFloat()
        out.glow = 1f
        out.aberration = (0.3 + 1.6 * CameraMath.decay(a, s.revealAt, 0.35) + (if (e == RegiElement.DRAGON) 0.6 * g else 0.0) + 0.6 * s.flicker(a)).toFloat()
        when (e) {
            RegiElement.ROCK -> { out.flashR = 1f; out.flashG = 0.9f; out.flashB = 0.75f }
            RegiElement.ICE -> { out.flashR = 0.88f; out.flashG = 0.96f; out.flashB = 1f }
            RegiElement.STEEL -> { out.flashR = 0.95f; out.flashG = 0.98f; out.flashB = 1f }
            RegiElement.ELECTRIC -> { out.flashR = 1f; out.flashG = 0.97f; out.flashB = 0.8f }
            RegiElement.DRAGON -> { out.flashR = 1f; out.flashG = 0.6f; out.flashB = 0.55f }
            RegiElement.GIANT -> { out.flashR = 1f; out.flashG = 0.98f; out.flashB = 0.9f }
        }
    }

    override fun actorLook(a: Double, out: ActorLook): ActorLook {
        val v = s.vanish(a)
        if (v > 0.0) return out.ghost(withAlpha(light, (1.0 - v).toFloat()))
        if (a < s.matterAt) return out.ghost(withAlpha(light, (0.7 * s.ghost(a)).toFloat()))
        if (a < s.revealAt) {
            out.statue(
                when (e) {
                    RegiElement.ROCK -> "concrete"
                    RegiElement.ICE -> "glass"
                    RegiElement.STEEL -> "metal"
                    RegiElement.ELECTRIC -> "gold"
                    RegiElement.DRAGON -> "marble"
                    RegiElement.GIANT -> "marble"
                },
            )
            when (e) {
                RegiElement.DRAGON -> out.tint = argb(0.7f, 0.2f, 0.2f, 1f)
                RegiElement.ROCK -> out.tint = argb(1f, 0.9f, 0.78f, 1f)
                else -> {}
            }
            out.bright = e == RegiElement.ICE || e == RegiElement.ELECTRIC
            return out
        }
        return out.normal()
    }

    override fun duck(a: Double): Float = s.duck(a).toFloat()

    override fun music(stretch: Double, offset: Double): SynthRenderer = RegiMusic.music(script, stretch, offset)

    override fun ambience(stretch: Double, offset: Double): SynthRenderer = RegiMusic.ambience(script, stretch, offset)
}
