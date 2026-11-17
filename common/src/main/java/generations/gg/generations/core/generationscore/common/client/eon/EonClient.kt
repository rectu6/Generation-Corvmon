package generations.gg.generations.core.generationscore.common.client.eon

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexSorting
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClient
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.CinematicAudio
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.CinematicShaders
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.CinematicTextures.Kind
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxBatches
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial.Layer
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxParticles
import generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient
import generations.gg.generations.core.generationscore.common.config.JsonConfigs
import generations.gg.generations.core.generationscore.common.network.GenerationsNetwork
import generations.gg.generations.core.generationscore.common.network.packets.eon.C2SEonInteractPacket
import generations.gg.generations.core.generationscore.common.network.packets.eon.S2CEonPacket
import generations.gg.generations.core.generationscore.common.world.eon.EonPath
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.FogRenderer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionHand
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * config/generations/core/eon/eon_client.json: this player's side of the Eon manifestations
 * (EON-PLAN.md § 4). particleIntensity 0 to 2, volume 0 to 1, hints (the direction in the action
 * bar), trailSeconds (1 to 6), footprintSeconds (2 to 12), glowThroughLeaves (a faint glow seen
 * through the leaves).
 */
class EonClientConfig(
    var particleIntensity: Float = 1f,
    var volume: Float = 1f,
    var hints: Boolean = true,
    var trailSeconds: Float = 3f,
    var footprintSeconds: Float = 6f,
    var glowThroughLeaves: Boolean = true,
) {
    fun validate() {
        particleIntensity = JsonConfigs.clamp(particleIntensity, 0f, 2f, 1f)
        volume = JsonConfigs.clamp(volume, 0f, 1f, 1f)
        trailSeconds = JsonConfigs.clamp(trailSeconds, 1f, 6f, 3f)
        footprintSeconds = JsonConfigs.clamp(footprintSeconds, 2f, 12f, 6f)
    }

    fun particles(base: Int): Int = (base * particleIntensity).toInt()

    companion object {
        const val VERSION = 1

        fun load(): EonClientConfig = JsonConfigs.load(
            "Eon manifestations (client)", JsonConfigs.file("eon", "eon_client.json"), EonClientConfig::class.java, ::EonClientConfig, VERSION, { it.validate() },
        )
    }
}

/** The Eon packets' client side (the packet holds no client class). */
object EonPacketHandlers {
    object Manifestation : ClientNetworkPacketHandler<S2CEonPacket> {
        override fun handle(packet: S2CEonPacket, client: Minecraft) {
            client.execute { EonClient.receive(packet) }
        }
    }
}

/**
 * An Eon manifestation on this client (EON-PLAN.md § 4): the signal (a vibrating light, motes,
 * a spiral forming, a psychic sound), the anomaly (a red and a blue heart turning round each
 * other, a halo with a dark rim for contrast, a faint glow through the leaves, a column of light
 * when the player is far), its way along the server's points kept above the real ground, its
 * twin trail and its footprints, the illusions, the core and its condensation into a fragment.
 * Only the player of the manifestation sees it (D4). The clock is the game's.
 */
object EonClient {
    private val FRAGMENT_TEXTURE = GenerationsCore.id("textures/item/legend_items/enigma_fragment.png")

    private var config = EonClientConfig()
    private var configLoaded = false

    private class Illusion(val start: Double, val seconds: Double, val speed: Double, val angles: DoubleArray, val from: Vector3d, val heading: Double) {
        val popped = BooleanArray(angles.size)
        val pos = Array(angles.size) { Vector3d(from) }
    }

    private class Show(val id: Long, val signal: Vector3d, val yaw: Double, val signalSeconds: Double, val test: Boolean, val hue: Int, val start: Double) {
        val path = EonPath()
        /** 0 signal, 1 anomaly, 2 pursuit, 3 core, 4 condensing. */
        var phase = 0
        var phaseAt = start
        val drawn = Vector3d(signal)
        val previous = Vector3d(signal)
        var heading = yaw
        var groundY = signal.y - EonPath.HEIGHT
        val trail = ArrayDeque<DoubleArray>()
        val prints = ArrayDeque<DoubleArray>()
        var printX = signal.x
        var printZ = signal.z
        var printSide = 1.0
        var printCount = 0
        var lastTrail = 0.0
        var lastMote = 0.0
        var nextHum = 0.0
        var core: Vector3d? = null
        var coreSeconds = 30.0
        var stabilize = -1.0
        var stabilizeSeconds = 2.5
        var end = Double.MAX_VALUE
        var result = -1
        var illusion: Illusion? = null
        var dash = false
        var formed = false
        var vanished = false
        var given = false
    }

    private var show: Show? = null
    private var hudTicks = 0
    private var frameT = 0.0
    private var lastFrame = Double.NaN

    // ---------------------------------------------------------------- the drawing path

    private const val MOTE = 0
    private const val SPARK = 1
    private val particleMaterials = arrayOf(
        FxMaterial("eonfx_mote", Layer.ADD, Kind.HALO),
        FxMaterial("eonfx_spark", Layer.ADD, Kind.SPARK),
    )
    private val particles = FxParticles(2500, particleMaterials)
    private val glows = FxMaterial("eonfx_glows", Layer.ADD, Kind.HALO)
    private val glowsThrough = FxMaterial("eonfx_glows_through", Layer.ADD, Kind.HALO, xray = true)
    private val rims = FxMaterial("eonfx_rims", Layer.ALPHA, Kind.HALO)
    private val trails = FxMaterial("eonfx_trails", Layer.ADD, Kind.STREAK)
    private val beams = FxMaterial("eonfx_beams", Layer.ADD, Kind.STREAK)
    private val prints = FxMaterial("eonfx_prints", Layer.ADD, Kind.HALO)
    private val swirl = FxMaterial("eonfx_swirl", Layer.ADD, Kind.STREAK).noise(0.4f, 0f, 2f, 0.4f)
    private val rings = FxMaterial("eonfx_rings", Layer.ADD, Kind.RING).noise(0f, 0.3f, 4f, 0.4f)
    private val aura = FxMaterial("eonfx_aura", Layer.ADD, Kind.WHITE).noise(0.25f, 0.6f, 2.5f, 0.55f).shape(2.0f, 1f, 0f, 1.25f)
    private val fragmentSprite = FxMaterial("eonfx_fragment", Layer.ALPHA, Kind.WHITE, image = FRAGMENT_TEXTURE)
    private val batches = FxBatches()
    private val keptView = Matrix4f()
    private val keptProjection = Matrix4f()
    private var kept = false
    private val right = Vector3f()
    private val up = Vector3f()
    private val tmp = DoubleArray(3)
    private val trailPoints = FloatArray(3 * 64)

    private val RED = FxMeshes.argb(1f, 0.28f, 0.38f, 1f)
    private val PINK = FxMeshes.argb(1f, 0.55f, 0.82f, 1f)
    private val BLUE = FxMeshes.argb(0.32f, 0.58f, 1f, 1f)
    private val WHITE = FxMeshes.argb(0.95f, 0.92f, 1f, 1f)
    private val DARK = FxMeshes.argb(0.10f, 0.03f, 0.18f, 1f)

    fun init() {
        PlatformEvents.CLIENT_TICK_POST.subscribe(Priority.NORMAL) { tick() }
        PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { clear() }
    }

    private fun clear() {
        show = null
        particles.clear()
        batches.clear()
        kept = false
        lastFrame = Double.NaN
    }

    private fun ensureConfig() {
        if (configLoaded) return
        configLoaded = true
        config = try {
            EonClientConfig.load()
        } catch (e: Exception) {
            EonClientConfig()
        }
    }

    private fun now(): Double {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return 0.0
        return level.gameTime + minecraft.timer.getGameTimeDeltaPartialTick(true).toDouble()
    }

    private fun sound(id: String, x: Double, y: Double, z: Double, volume: Float, pitch: Float) =
        CinematicAudio.play(id, x, y, z, volume * config.volume, pitch)

    private fun forward(yaw: Double): Vector3d {
        val r = Math.toRadians(yaw)
        return Vector3d(-sin(r), 0.0, cos(r))
    }

    // ---------------------------------------------------------------- packets

    fun receive(packet: S2CEonPacket) {
        ensureConfig()
        val d = packet.data
        val t = packet.tick.toDouble()
        if (packet.kind == S2CEonPacket.START) {
            if (d.size < 7) return
            particles.clear()
            val s = Show(packet.session, Vector3d(d[0], d[1], d[2]), d[3], d[4], d[5] > 0.5, d[6].toInt(), t)
            show = s
            // The signal: a psychic sound where it forms, a faint cry far away in the sky.
            sound("minecraft:block.amethyst_block.resonate", d[0], d[1], d[2], 2.2f, 0.7f)
            sound("minecraft:entity.illusioner.prepare_mirror", d[0], d[1], d[2], 1.6f, 1.3f)
            sound("minecraft:block.beacon.ambient", d[0], d[1], d[2], 2.0f, 1.8f)
            val far = forward(s.yaw + 30.0)
            sound(if (s.hue == 0) "cobblemon:pokemon.latias.cry" else "cobblemon:pokemon.latios.cry", d[0] + far.x * 40.0, d[1] + 30.0, d[2] + far.z * 40.0, 1.2f, 1.25f)
            return
        }
        val s = show ?: return
        if (packet.session != s.id) return
        when (packet.kind) {
            S2CEonPacket.POINTS -> {
                var i = 0
                while (i + 2 < d.size) {
                    s.path.add(d[i], d[i + 1], d[i + 2])
                    i += 3
                }
            }
            S2CEonPacket.MOTION -> if (d.size >= 2) {
                s.path.setMotion(packet.tick, d[0], d[1])
                val dash = d.size >= 3 && d[2] > 0.5
                if (dash && !s.dash) sound("minecraft:item.trident.riptide_1", s.drawn.x, s.drawn.y, s.drawn.z, 1.4f, 1.8f)
                s.dash = dash
            }
            S2CEonPacket.PHASE -> if (d.isNotEmpty()) {
                s.phase = d[0].toInt().coerceIn(1, 2)
                s.phaseAt = t
                if (s.phase == 1) {
                    burst(s.drawn.x, s.drawn.y, s.drawn.z, config.particles(36), 3f, PINK, SPARK, 1.4f, 0.12f, 11)
                    burst(s.drawn.x, s.drawn.y, s.drawn.z, config.particles(24), 2.4f, BLUE, MOTE, 1.6f, 0.16f, 19)
                    sound("minecraft:block.beacon.power_select", s.drawn.x, s.drawn.y, s.drawn.z, 2.0f, 1.5f)
                    sound("minecraft:entity.illusioner.mirror_move", s.drawn.x, s.drawn.y, s.drawn.z, 1.6f, 1.2f)
                }
            }
            S2CEonPacket.ILLUSION -> if (d.size >= 3) {
                val angles = DoubleArray(d.size - 2) { d[it + 2] }
                s.illusion = Illusion(t, d[0], d[1], angles, Vector3d(s.drawn), s.heading)
                burst(s.drawn.x, s.drawn.y, s.drawn.z, config.particles(30), 2.6f, WHITE, SPARK, 1.0f, 0.1f, 23)
                sound("minecraft:entity.illusioner.mirror_move", s.drawn.x, s.drawn.y, s.drawn.z, 1.8f, 1.25f)
            }
            S2CEonPacket.CORE -> if (d.size >= 4) {
                s.core = Vector3d(d[0], d[1], d[2])
                s.coreSeconds = d[3]
                s.phase = 3
                s.phaseAt = t
                s.illusion = null
                sound("minecraft:block.beacon.power_select", d[0], d[1], d[2], 2.0f, 1.3f)
                sound("minecraft:block.amethyst_block.resonate", d[0], d[1], d[2], 2.0f, 1.1f)
            }
            S2CEonPacket.STABILIZE -> {
                s.stabilize = t
                s.stabilizeSeconds = if (d.isNotEmpty()) d[0] else 2.5
                s.phase = 4
                val c = s.core ?: s.drawn
                sound("minecraft:block.beacon.activate", c.x, c.y, c.z, 2.0f, 1.6f)
            }
            S2CEonPacket.END -> {
                s.end = t
                s.result = if (d.isNotEmpty()) d[0].toInt() else 1
                if (s.result != 0) {
                    // Dispersed: nothing left.
                    burst(s.drawn.x, s.drawn.y, s.drawn.z, config.particles(50), 3.5f, PINK, MOTE, 1.8f, 0.14f, 31)
                    burst(s.drawn.x, s.drawn.y, s.drawn.z, config.particles(40), 3.0f, BLUE, MOTE, 1.8f, 0.14f, 37)
                    sound("minecraft:block.beacon.deactivate", s.drawn.x, s.drawn.y, s.drawn.z, 1.8f, 1.5f)
                    s.vanished = true
                }
            }
        }
    }

    // ---------------------------------------------------------------- ticks: sounds and hints

    private fun tick() {
        val s = show ?: return
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val t = now()
        if (t > s.end + if (s.result == 0) 30.0 else 10.0) {
            show = null
            return
        }
        if (CinematicClient.isActive() || s.vanished) return
        val seconds = t / 20.0
        // A soft hum where it is, a chime now and then.
        if (seconds >= s.nextHum && s.phase in 1..3) {
            sound(if (s.phase == 3) "minecraft:block.amethyst_block.resonate" else "minecraft:block.beacon.ambient", s.drawn.x, s.drawn.y, s.drawn.z, 1.3f, if (s.phase == 3) 1.2f else 1.9f)
            s.nextHum = seconds + if (s.phase == 3) 2.2 else 1.6
        }
        if (!config.hints || ++hudTicks % 10 != 0) return
        val target = s.core ?: s.drawn
        val dx = target.x - player.x
        val dz = target.z - player.z
        val distance = sqrt(dx * dx + dz * dz)
        val key = when {
            s.phase == 4 || s.end != Double.MAX_VALUE -> return
            s.phase == 3 && distance < 6.0 -> {
                val left = ((s.phaseAt + s.coreSeconds * 20.0 - t) / 20.0).toInt().coerceAtLeast(0)
                minecraft.gui.setOverlayMessage(Component.translatable("generations_core.eon.hint.click", left).withStyle(ChatFormatting.LIGHT_PURPLE), false)
                return
            }
            s.phase == 3 -> "generations_core.eon.hint.core"
            distance < 10.0 -> return
            s.path.motionSpeed <= 0.0 && s.phase == 2 -> "generations_core.eon.hint.waiting"
            else -> "generations_core.eon.hint.follow"
        }
        val angle = Math.toDegrees(atan2(-dx, dz))
        var delta = (angle - player.yRot) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        val arrows = arrayOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")
        val arrow = arrows[Math.floorMod(Math.round(delta / 45.0).toInt(), 8)]
        minecraft.gui.setOverlayMessage(Component.translatable(key, arrow, distance.toInt()).withStyle(ChatFormatting.LIGHT_PURPLE), false)
    }

    /**
     * A click (MewClickMixin): on the core, in reach and looked at, it is sent to the server
     * (which checks it again) and nothing else happens (no block broken, no item used).
     */
    @JvmStatic
    fun click(): Boolean {
        val s = show ?: return false
        if (s.phase != 3 || s.end != Double.MAX_VALUE) return false
        val core = s.core ?: return false
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return false
        if (CinematicClient.isActive()) return false
        val eye = player.eyePosition
        val tx = core.x - eye.x
        val ty = core.y - eye.y
        val tz = core.z - eye.z
        val d = sqrt(tx * tx + ty * ty + tz * tz)
        if (d > 5.8) return false
        val look = player.getViewVector(1f)
        val dot = (look.x * tx + look.y * ty + look.z * tz) / d.coerceAtLeast(1e-6)
        if (d > 2.5 && dot < 0.86) return false
        player.swing(InteractionHand.MAIN_HAND)
        GenerationsNetwork.sendToServer(C2SEonInteractPacket(s.id))
        return true
    }

    // ---------------------------------------------------------------- where it is

    /** The real ground under (x, z) near yHint (a few blocks read, leaves skipped), or null. */
    private fun groundAt(x: Double, yHint: Double, z: Double): Double? {
        val level = Minecraft.getInstance().level ?: return null
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        val top = floor(yHint).toInt() + 1
        for (y in top downTo top - 8) {
            val pos = BlockPos(bx, y, bz)
            val state = level.getBlockState(pos)
            if (state.`is`(BlockTags.LEAVES)) continue
            val shape = state.getCollisionShape(level, pos)
            if (!shape.isEmpty) return y + shape.max(Direction.Axis.Y)
        }
        return null
    }

    /** In the world's pass (LevelRendererMixin): the matrices kept, the anomaly's place this frame. */
    @JvmStatic
    fun beforeRareCandy(frustum: Matrix4f, projection: Matrix4f) {
        val s = show
        if (s == null && particles.count == 0) return
        keptView.set(frustum)
        keptProjection.set(projection)
        kept = true
        if (s == null) return
        try {
            val t = now()
            val dt = if (lastFrame.isNaN()) 0.0 else ((t - lastFrame) / 20.0).coerceIn(0.0, 0.25)
            lastFrame = t
            update(s, t, dt)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Eon manifestation: drawing stopped", e)
            clear()
        }
    }

    private fun update(s: Show, t: Double, dt: Double) {
        if (s.path.size == 0) return
        s.previous.set(s.drawn)
        val core = s.core
        if (core != null) {
            tmp[0] = core.x; tmp[1] = core.y + 0.35 + 0.1 * sin(t / 20.0 * 1.7); tmp[2] = core.z
        } else {
            s.path.position(t, tmp)
            // Kept over the real ground (the way's curve may dip under a bump).
            groundAt(tmp[0], tmp[1], tmp[2])?.let { g ->
                s.groundY += (g - s.groundY) * (1.0 - exp(-dt * 6.0))
                tmp[1] = max(tmp[1], s.groundY + 1.45)
            }
        }
        if (dt > 0.0) {
            val k = 1.0 - exp(-dt * 5.0)
            s.drawn.add((tmp[0] - s.drawn.x) * k, (tmp[1] - s.drawn.y) * k, (tmp[2] - s.drawn.z) * k)
            val vx = (s.drawn.x - s.previous.x) / dt
            val vz = (s.drawn.z - s.previous.z) / dt
            if (vx * vx + vz * vz > 0.04) s.heading = Math.toDegrees(atan2(-vx, vz))
        }
        // Footprints: only the real anomaly leaves them, a step every 1.25 blocks.
        if (s.phase == 2) {
            val px = s.drawn.x - s.printX
            val pz = s.drawn.z - s.printZ
            if (px * px + pz * pz >= 1.25 * 1.25) {
                s.printX = s.drawn.x
                s.printZ = s.drawn.z
                val side = forward(s.heading + 90.0)
                val x = s.drawn.x + side.x * 0.32 * s.printSide
                val z = s.drawn.z + side.z * 0.32 * s.printSide
                val g = groundAt(x, s.drawn.y, z) ?: (s.drawn.y - 1.5)
                s.prints.addLast(doubleArrayOf(x, g + 0.04, z, t / 20.0, s.printSide, s.heading))
                s.printSide = -s.printSide
                if (++s.printCount % 4 == 0) sound("minecraft:block.amethyst_block.step", x, g, z, 0.9f, 1.6f)
                while (s.prints.size > 96) s.prints.removeFirst()
            }
        }
        // The illusions' decoys: straight on from where they split, popped at the end or when reached.
        val illusion = s.illusion
        if (illusion != null) {
            val age = (t - illusion.start) / 20.0
            val player = Minecraft.getInstance().player
            for (i in illusion.angles.indices) {
                if (illusion.popped[i]) continue
                val dir = forward(illusion.heading + illusion.angles[i])
                val p = illusion.pos[i]
                val x = illusion.from.x + dir.x * illusion.speed * age
                val z = illusion.from.z + dir.z * illusion.speed * age
                val g = groundAt(x, p.y, z)
                val y = if (g != null) max(g + 1.45, p.y - 0.4) else p.y
                p.set(x, if (dt > 0.0) p.y + (y - p.y) * (1.0 - exp(-dt * 5.0)) else y, z)
                val near = player != null && player.position().distanceTo(net.minecraft.world.phys.Vec3(p.x, p.y - 1.0, p.z)) < 3.0
                if (age >= illusion.seconds || near) {
                    illusion.popped[i] = true
                    burst(p.x, p.y, p.z, config.particles(40), 3.2f, if (i % 2 == 0) PINK else BLUE, SPARK, 1.2f, 0.12f, 41 + i * 7)
                    sound("minecraft:block.amethyst_cluster.break", p.x, p.y, p.z, 1.6f, 1.5f)
                }
            }
            if (illusion.popped.all { it }) s.illusion = null
        }
    }

    // ---------------------------------------------------------------- effects after the world

    private fun burst(x: Double, y: Double, z: Double, count: Int, speed: Float, colour: Int, material: Int, life: Float, size: Float, seed: Int) {
        for (i in 0 until count) {
            val a = rand(seed + i, 11) * 2.0 * PI
            val b = (rand(seed + i, 13) - 0.5) * PI
            val sp = speed * (0.4 + rand(seed + i, 17).toFloat())
            particles.emit(material, x, y, z, (cos(b) * sin(a) * sp).toFloat(), (sin(b) * sp).toFloat(), (cos(b) * cos(a) * sp).toFloat(), life * (0.7f + 0.6f * rand(seed + i, 19).toFloat()), size, size * 0.2f, colour, drag = 2.2f)
        }
    }

    private fun rand(seed: Int, salt: Int): Double {
        var h = seed * 0x27d4eb2d + salt * 0x165667b1
        h = h xor (h ushr 15)
        h *= 0x2c1b3c6d
        h = h xor (h ushr 12)
        return (h ushr 8) / 16777216.0
    }

    private fun smooth(x: Double): Double {
        val u = x.coerceIn(0.0, 1.0)
        return u * u * (3.0 - 2.0 * u)
    }

    /** Right after the world's render (GameRendererMixin): every effect of the manifestation. */
    @JvmStatic
    fun renderAfterLevel() {
        val s = show
        if (s == null && particles.count == 0) return
        try {
            if (CinematicClient.isActive()) return
            val t = now()
            if (s != null) emit(s, t)
            render(((t - frameT).coerceIn(0.0, 5.0) / 20.0).toFloat()) { b, cx, cy, cz -> if (s != null) draw(s, b, cx, cy, cz, t) }
            frameT = t
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Eon manifestation: effects stopped", e)
            clear()
        }
    }

    /** New motes, sparks and trail points this frame. */
    private fun emit(s: Show, t: Double) {
        val seconds = t / 20.0
        if (s.vanished) return
        val c = s.drawn
        // The motes' swirl turns round the signal, then round the core.
        val axis = s.core ?: s.signal
        particles.axisX = axis.x
        particles.axisZ = axis.z
        if (s.phase == 0) {
            // The signal: motes rising round a forming spiral.
            val rate = 16.0 * config.particleIntensity
            if (rate > 0.0) while (s.lastMote + 1.0 / rate <= seconds) {
                s.lastMote = if (seconds - s.lastMote > 1.0) seconds else s.lastMote + 1.0 / rate
                val k = (s.lastMote * 173).toInt()
                val a = rand(k, 3) * 2.0 * PI
                val r = 0.4 + 1.4 * rand(k, 5)
                particles.emit(MOTE, s.signal.x + sin(a) * r, s.signal.y - 1.4 + rand(k, 7) * 0.6, s.signal.z + cos(a) * r, 0f, (0.5 + rand(k, 9) * 0.5).toFloat(), 0f, 2.4f, 0.11f, 0.03f, if (k % 2 == 0) PINK else BLUE, drag = 0.4f, swirl = 1.2f)
            }
            return
        }
        if (s.phase in 1..3 && s.end == Double.MAX_VALUE) {
            if (seconds - s.lastTrail >= 0.08) {
                s.lastTrail = seconds
                s.trail.addLast(doubleArrayOf(c.x, c.y, c.z, seconds))
                while (s.trail.size > 64) s.trail.removeFirst()
            }
            val rate = 10.0 * config.particleIntensity
            if (rate > 0.0) while (s.lastMote + 1.0 / rate <= seconds) {
                s.lastMote = if (seconds - s.lastMote > 1.0) seconds else s.lastMote + 1.0 / rate
                val k = (s.lastMote * 131).toInt()
                particles.emit(MOTE, c.x + (rand(k, 1) - 0.5) * 0.6, c.y + (rand(k, 2) - 0.5) * 0.6, c.z + (rand(k, 3) - 0.5) * 0.6, ((rand(k, 4) - 0.5) * 0.3).toFloat(), (0.15 + rand(k, 5) * 0.3).toFloat(), ((rand(k, 6) - 0.5) * 0.3).toFloat(), 1.8f, 0.1f, 0.02f, if (k % 2 == 0) PINK else BLUE, drag = 0.6f)
            }
        }
        // The condensation: motes drawn into the core.
        if (s.phase == 4 && s.end == Double.MAX_VALUE) {
            val core = s.core ?: return
            val rate = 40.0 * config.particleIntensity
            if (rate > 0.0) while (s.lastMote + 1.0 / rate <= seconds) {
                s.lastMote = if (seconds - s.lastMote > 1.0) seconds else s.lastMote + 1.0 / rate
                val k = (s.lastMote * 211).toInt()
                val a = rand(k, 3) * 2.0 * PI
                val r = 2.5 + 1.5 * rand(k, 5)
                val y = core.y + (rand(k, 7) - 0.3) * 2.0
                val x = core.x + sin(a) * r
                val z = core.z + cos(a) * r
                particles.emit(MOTE, x, y, z, ((core.x - x) * 1.3).toFloat(), ((core.y - y) * 1.3).toFloat(), ((core.z - z) * 1.3).toFloat(), 0.75f, 0.12f, 0.02f, if (k % 2 == 0) PINK else BLUE, drag = 0.2f, swirl = 2f)
            }
        }
        while (s.trail.isNotEmpty() && seconds - s.trail.first()[3] > config.trailSeconds) s.trail.removeFirst()
        while (s.prints.isNotEmpty() && seconds - s.prints.first()[3] > config.footprintSeconds) s.prints.removeFirst()
    }

    /** The anomaly (or a decoy): a dark rim for contrast, two hearts turning, halos, the glow through the leaves. */
    private fun sphere(b: FxBatches, x: Float, y: Float, z: Float, seconds: Double, scale: Float, alpha: Float, salt: Int) {
        val pulse = (0.9 + 0.1 * sin(seconds * 5.0 + salt)).toFloat()
        FxMeshes.billboard(b.of(rims), x, y, z, 1.6f * scale, 0f, FxMeshes.withAlpha(DARK, 0.42f * alpha), right, up)
        val a = seconds * 4.2 + salt
        val ox = (cos(a) * 0.28 * scale).toFloat()
        val oz = (sin(a) * 0.28 * scale).toFloat()
        val oy = (sin(a * 0.5) * 0.12 * scale).toFloat()
        FxMeshes.billboard(b.of(glows), x + ox * right.x, y + oy, z + ox * right.z + oz * 0f, 0.62f * scale * pulse, 0f, FxMeshes.withAlpha(RED, 0.9f * alpha), right, up)
        FxMeshes.billboard(b.of(glows), x - ox * right.x, y - oy, z - ox * right.z, 0.62f * scale * pulse, 0f, FxMeshes.withAlpha(BLUE, 0.9f * alpha), right, up)
        FxMeshes.billboard(b.of(glows), x, y, z, 0.38f * scale, (seconds * 0.8).toFloat(), FxMeshes.withAlpha(WHITE, 0.95f * alpha), right, up)
        FxMeshes.billboard(b.of(glows), x, y, z, 2.6f * scale * pulse, 0f, FxMeshes.withAlpha(PINK, 0.28f * alpha), right, up)
        FxMeshes.billboard(b.of(glows), x, y, z, 5.2f * scale, 0f, FxMeshes.withAlpha(BLUE, 0.10f * alpha), right, up)
        if (config.glowThroughLeaves) FxMeshes.billboard(b.of(glowsThrough), x, y, z, 1.8f * scale, 0f, FxMeshes.withAlpha(PINK, 0.16f * alpha), right, up)
    }

    private fun draw(s: Show, b: FxBatches, cx: Double, cy: Double, cz: Double, t: Double) {
        val seconds = t / 20.0
        val player = Minecraft.getInstance().player
        // The footprints, fading with their age (left pink, right blue).
        for (p in s.prints) {
            val age = seconds - p[3]
            val fade = (1.0 - age / config.footprintSeconds).coerceIn(0.0, 1.0).toFloat()
            if (fade <= 0.01f) continue
            val colour = if (p[4] > 0.0) PINK else BLUE
            val angle = Math.toRadians(-p[5]).toFloat()
            FxMeshes.flat(b.of(prints), (p[0] - cx).toFloat(), (p[1] - cy).toFloat(), (p[2] - cz).toFloat(), 0.34f, angle, FxMeshes.withAlpha(colour, 0.75f * fade))
            FxMeshes.flat(b.of(prints), (p[0] - cx).toFloat(), (p[1] + 0.01 - cy).toFloat(), (p[2] - cz).toFloat(), 0.16f, angle, FxMeshes.withAlpha(WHITE, 0.6f * fade))
        }
        if (s.vanished) return
        val x = (s.drawn.x - cx).toFloat()
        val y = (s.drawn.y - cy).toFloat()
        val z = (s.drawn.z - cz).toFloat()
        // The signal: a vibrating light, a spiral forming, a ring on the ground, a faint pillar.
        if (s.phase == 0) {
            val u = ((t - s.start) / (s.signalSeconds * 20.0)).coerceIn(0.0, 1.0)
            val jitter = (0.15 * sin(seconds * 37.0) + 0.1 * sin(seconds * 23.0)).toFloat()
            val size = (0.4 + 0.9 * u).toFloat() + jitter
            FxMeshes.billboard(b.of(glows), x, y, z, size, 0f, FxMeshes.withAlpha(WHITE, (0.4 + 0.5 * u).toFloat()), right, up)
            FxMeshes.billboard(b.of(glows), x, y, z, size * 3f, 0f, FxMeshes.withAlpha(PINK, (0.15 + 0.2 * u).toFloat()), right, up)
            if (config.glowThroughLeaves) FxMeshes.billboard(b.of(glowsThrough), x, y, z, 1.4f, 0f, FxMeshes.withAlpha(PINK, 0.14f), right, up)
            val base = y - EonPath.HEIGHT.toFloat()
            FxMeshes.spiral(b.of(swirl), x, base, z, 0f, (0.4 + 2.4 * u).toFloat(), (1.8 - 1.2 * u).toFloat(), 0.25f, (1.0 + 1.5 * u).toFloat(), (seconds * 2.0).toFloat(), 0.1f, FxMeshes.withAlpha(PINK, 0.6f), FxMeshes.withAlpha(BLUE, 0f))
            FxMeshes.spiral(b.of(swirl), x, base, z, 0.2f, (0.5 + 2.2 * u).toFloat(), (1.5 - 1.0 * u).toFloat(), 0.2f, (1.2 + 1.4 * u).toFloat(), (seconds * 2.4 + PI).toFloat(), 0.08f, FxMeshes.withAlpha(BLUE, 0.55f), FxMeshes.withAlpha(PINK, 0f))
            val r = (0.8 + 1.6 * ((seconds * 0.7) % 1.0)).toFloat()
            FxMeshes.ring(b.of(rings), x, base + 0.05f, z, maxOf(0f, r - 0.3f), r, 48, FxMeshes.withAlpha(PINK, 0.5f * (1f - r / 2.4f)), FxMeshes.withAlpha(BLUE, 0.4f * (1f - r / 2.4f)), vScale = 6f)
            FxMeshes.beam(b.of(beams), x, base, z, x, base + 12f, z, 0.5f, 0.1f, FxMeshes.withAlpha(PINK, 0.22f * u.toFloat()), FxMeshes.withAlpha(BLUE, 0f))
            return
        }
        // The twin trail, red above and blue below, fading with its age.
        if (s.trail.size >= 2) {
            val n = minOf(s.trail.size, 64)
            val start = s.trail.size - n
            for ((offset, colour) in listOf(0.14 to RED, -0.14 to BLUE)) {
                for (i in 0 until n) {
                    val q = s.trail.elementAt(start + i)
                    val age = ((seconds - q[3]) / config.trailSeconds).coerceIn(0.0, 1.0)
                    trailPoints[i * 3] = (q[0] - cx).toFloat()
                    trailPoints[i * 3 + 1] = (q[1] + offset * (1.0 - age) - cy).toFloat()
                    trailPoints[i * 3 + 2] = (q[2] - cz).toFloat()
                }
                FxMeshes.ribbon(b.of(trails), trailPoints, n, 0.01f, 0.24f, FxMeshes.withAlpha(colour, 0f), FxMeshes.withAlpha(colour, 0.7f))
            }
        }
        // The anomaly, growing as it forms; the decoys of an illusion look the same.
        val formed = smooth((t - s.phaseAt) / 20.0 / 0.8).toFloat()
        val alpha = if (s.phase >= 2) 1f else formed
        if (s.phase <= 2) sphere(b, x, y, z, seconds, 0.6f + 0.4f * alpha, alpha, 0)
        s.illusion?.let { ill ->
            for (i in ill.angles.indices) {
                if (ill.popped[i]) continue
                val p = ill.pos[i]
                sphere(b, (p.x - cx).toFloat(), (p.y - cy).toFloat(), (p.z - cz).toFloat(), seconds, 1f, 0.95f, 3 + i)
            }
        }
        // A column of light over it when the player is far behind.
        if (player != null && s.phase <= 3) {
            val dx = s.drawn.x - player.x
            val dz = s.drawn.z - player.z
            val far = ((sqrt(dx * dx + dz * dz) - 18.0) / 10.0).coerceIn(0.0, 1.0).toFloat()
            if (far > 0.01f) FxMeshes.beam(b.of(beams), x, y, z, x, y + 16f, z, 0.6f, 1.6f, FxMeshes.withAlpha(PINK, 0.34f * far), FxMeshes.withAlpha(BLUE, 0f))
        }
        // The core: an aura, rings turning round it, a ring on the ground; then the condensation.
        val core = s.core ?: return
        val kx = (core.x - cx).toFloat()
        val ky = (core.y - cy).toFloat()
        val kz = (core.z - cz).toFloat()
        val condense = if (s.stabilize >= 0.0) ((t - s.stabilize) / (s.stabilizeSeconds * 20.0)).coerceIn(0.0, 1.0) else 0.0
        if (s.end == Double.MAX_VALUE || s.result == 0) {
            val shrink = (1.0 - 0.75 * smooth(condense)).toFloat()
            val pulse = (0.92 + 0.08 * sin(seconds * 3.0)).toFloat()
            val fade = if (s.result == 0) (1.0 - (t - s.end) / 20.0).coerceIn(0.0, 1.0).toFloat() else 1f
            if (condense < 1.0) {
                FxMeshes.sphere(b.of(aura), kx, ky, kz, 0.55f * shrink * pulse, FxMeshes.argb(1f, 0.6f, 0.9f, 0.6f * fade), uOffset = (seconds * 0.3).toFloat())
                sphere(b, kx, ky, kz, seconds, shrink * 1.15f, fade, 9)
                for (k in 0 until 3) {
                    val r = ((1.1 + 0.45 * k) * shrink).toFloat()
                    val yy = ky + (0.35 * sin(seconds * (1.3 + 0.4 * k) + k * 2.0)).toFloat()
                    FxMeshes.ring(b.of(rings), kx, yy, kz, r - 0.08f, r, 48, FxMeshes.withAlpha(if (k % 2 == 0) PINK else BLUE, 0.6f * fade), FxMeshes.withAlpha(WHITE, 0.3f * fade), vScale = 6f, vOffset = (seconds * (0.6 + 0.3 * k)).toFloat())
                }
                val g = ky - 1.25f
                FxMeshes.ring(b.of(rings), kx, g, kz, 1.4f * shrink, 1.75f * shrink, 48, FxMeshes.withAlpha(PINK, 0.45f * fade), FxMeshes.withAlpha(BLUE, 0.2f * fade), vScale = 6f)
                if (condense > 0.0) {
                    val u = condense.toFloat()
                    FxMeshes.spiral(b.of(swirl), kx, g, kz, 0f, 2.6f, 2.4f * (1f - u), 0.1f, 2.5f, (seconds * 6.0).toFloat(), 0.12f, FxMeshes.withAlpha(PINK, 0.7f), FxMeshes.withAlpha(BLUE, 0.2f))
                    FxMeshes.billboard(b.of(glows), kx, ky, kz, 1.5f + 3f * u, 0f, FxMeshes.withAlpha(WHITE, 0.35f * u), right, up)
                }
            }
            // The fragment: it appears in the light, turns, then flies to the player.
            if (condense > 0.65 && !s.vanished) {
                val appear = smooth((condense - 0.65) / 0.35).toFloat()
                var fx = kx
                var fy = ky
                var fz = kz
                if (s.result == 0 && player != null) {
                    val fly = smooth((t - s.end) / 16.0)
                    val px = (player.x - cx).toFloat()
                    val py = (player.y + 1.2 - cy).toFloat()
                    val pz = (player.z - cz).toFloat()
                    fx += ((px - kx) * fly).toFloat()
                    fy += ((py - ky) * fly).toFloat() + (sin(fly * PI) * 0.8).toFloat()
                    fz += ((pz - kz) * fly).toFloat()
                    if (fly >= 1.0 && !s.given) {
                        s.given = true
                        burst(player.x, player.y + 1.2, player.z, config.particles(30), 2.2f, PINK, SPARK, 0.9f, 0.1f, 61)
                        sound("minecraft:block.amethyst_block.chime", player.x, player.y + 1.0, player.z, 1.8f, 1.5f)
                        sound("minecraft:entity.player.levelup", player.x, player.y + 1.0, player.z, 0.6f, 1.7f)
                    }
                }
                if (!s.given) {
                    FxMeshes.billboard(b.of(glows), fx, fy, fz, 1.2f * appear, 0f, FxMeshes.withAlpha(PINK, 0.6f * appear), right, up)
                    FxMeshes.billboard(b.of(fragmentSprite), fx, fy, fz, 0.42f * appear, (seconds * 1.6).toFloat(), FxMeshes.withAlpha(0xFFFFFF, appear), right, up)
                }
            }
        }
    }

    private fun render(dt: Float, extra: (FxBatches, Double, Double, Double) -> Unit) {
        if (!kept) return
        kept = false
        particles.update(dt)
        val shader = CinematicShaders.fx ?: return
        val minecraft = Minecraft.getInstance()
        val camera = minecraft.gameRenderer.mainCamera
        val cam = camera.position
        right.set(camera.leftVector).negate()
        up.set(camera.upVector)
        batches.clear()
        extra(batches, cam.x, cam.y, cam.z)
        particles.render(batches, cam.x, cam.y, cam.z, right, up)
        if (!batches.has(Layer.ALPHA) && !batches.has(Layer.ADD)) return
        val main = minecraft.mainRenderTarget
        val stack = RenderSystem.getModelViewStack()
        val previousProjection = Matrix4f(RenderSystem.getProjectionMatrix())
        val previousSorting = RenderSystem.getVertexSorting()
        stack.pushMatrix()
        try {
            main.bindWrite(true)
            UltraWarpRideClient.restoreWriteMasks()
            UltraWarpRideClient.clearTextureUnits()
            RenderSystem.setProjectionMatrix(Matrix4f(keptProjection), VertexSorting.DISTANCE_TO_ORIGIN)
            stack.identity()
            stack.mul(keptView)
            RenderSystem.applyModelViewMatrix()
            FogRenderer.setupNoFog()
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            val time = ((minecraft.level?.gameTime ?: 0L) % 24000L) / 20f
            if (shader != null) {
                batches.draw(Layer.ALPHA, time, 0, 0)
                batches.draw(Layer.ADD, time, 0, 0)
            }
        } finally {
            RenderSystem.disableBlend()
            RenderSystem.defaultBlendFunc()
            RenderSystem.enableDepthTest()
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            RenderSystem.depthMask(true)
            RenderSystem.enableCull()
            main.bindWrite(true)
            stack.popMatrix()
            RenderSystem.applyModelViewMatrix()
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting)
        }
    }
}
