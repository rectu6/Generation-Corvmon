package generations.gg.generations.core.generationscore.common.client.hooh

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexSorting
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClient
import generations.gg.generations.core.generationscore.common.client.cinematic.actor.CinematicActor
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.CinematicAudio
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.CinematicShaders
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.CinematicTextures.Kind
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxBatches
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial.Layer
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxParticles
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorPose
import generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient
import generations.gg.generations.core.generationscore.common.config.JsonConfigs
import generations.gg.generations.core.generationscore.common.network.packets.hooh.S2CHoOhPacket
import generations.gg.generations.core.generationscore.common.world.hooh.HoOhFlight
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.FogRenderer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * config/generations/core/hooh/ho_oh_client.json: this player's side of the Passage of the
 * Rainbow (HOOH-PLAN.md § 7). particleIntensity 0 to 2, volume 0 to 1, hints (the direction in
 * the action bar), trailSeconds (how long the rainbow trail stays, 1 to 6).
 */
class HoOhClientConfig(
    var particleIntensity: Float = 1f,
    var volume: Float = 1f,
    var hints: Boolean = true,
    var trailSeconds: Float = 3f,
) {
    fun validate() {
        particleIntensity = JsonConfigs.clamp(particleIntensity, 0f, 2f, 1f)
        volume = JsonConfigs.clamp(volume, 0f, 1f, 1f)
        trailSeconds = JsonConfigs.clamp(trailSeconds, 1f, 6f, 3f)
    }

    fun particles(base: Int): Int = (base * particleIntensity).toInt()

    companion object {
        const val VERSION = 1

        fun load(): HoOhClientConfig = JsonConfigs.load(
            "Ho-Oh (client)", JsonConfigs.file("hooh", "ho_oh_client.json"), HoOhClientConfig::class.java, ::HoOhClientConfig, VERSION, { it.validate() },
        )
    }
}

/** The Ho-Oh packets' client side (the packet holds no client class). */
object HoOhPacketHandlers {
    object Passage : ClientNetworkPacketHandler<S2CHoOhPacket> {
        override fun handle(packet: S2CHoOhPacket, client: Minecraft) {
            client.execute { HoOhClient.receive(packet) }
        }
    }
}

/**
 * The Passage of the Rainbow on this client (HOOH-PLAN.md §§ 6 to 8): Ho-Oh flying (the actor of
 * the cinematics, no entity) along the flight the server sends, smoothed, banking in its turns;
 * its golden halo seen from afar, its rainbow trail kept a few seconds, the embers and feathers
 * it sheds, a light under it when the player falls behind, the direction in the action bar, its
 * wings and cries from where it is; without the scene, the signal in the sky (a rainbow and a
 * golden light); the feather's fall, its light where it lies, its pick-up. Only the player of the
 * passage sees it (D3). The clock is the game's (a paused solo game stops it).
 */
object HoOhClient {
    private val HO_OH = ResourceLocation.fromNamespaceAndPath("cobblemon", "hooh")
    private val FEATHER_TEXTURE = GenerationsCore.id("textures/item/ho_oh_sacred_feather.png")

    private var config = HoOhClientConfig()
    private var configLoaded = false

    private class Feather(val from: Vector3d, val to: Vector3d, val start: Double, val seconds: Double) {
        var picked = -1.0
        var lastSpark = 0.0
    }

    private class Passage(val id: Long, val origin: Vector3d, val yaw: Double, val scale: Double, val cinematic: Boolean, val mode: Int, val start: Double) {
        val flight = HoOhFlight()
        val actor = CinematicActor(HO_OH, emptySet())
        val drawn = Vector3d()
        val previous = Vector3d()
        var drawnReady = false
        var heading = yaw
        var pitch = 0.0
        var roll = 0.0
        var visible = false
        var leave = Double.MAX_VALUE
        var end = Double.MAX_VALUE
        var feather: Feather? = null
        /** x, y, z, time of the trail's points (oldest first). */
        val trail = ArrayDeque<DoubleArray>()
        var lastTrail = 0.0
        var lastEmber = 0.0
        var nextFlap = 0.0
        var nextCry = 0.0
        var pursuitAnnounced = false
        var vanished = false
    }

    private var passage: Passage? = null
    private var lastFrame = Double.NaN
    private var hudTicks = 0

    // ---------------------------------------------------------------- the drawing path

    private val particleMaterials = arrayOf(
        FxMaterial("hoohfx_ember", Layer.ADD, Kind.EMBER),
        FxMaterial("hoohfx_spark", Layer.ADD, Kind.SPARK),
        FxMaterial("hoohfx_mote", Layer.ADD, Kind.HALO),
    )
    private val particles = FxParticles(2500, particleMaterials)
    private val glows = FxMaterial("hoohfx_glows", Layer.ADD, Kind.HALO)
    private val trails = FxMaterial("hoohfx_trails", Layer.ADD, Kind.STREAK)
    private val beams = FxMaterial("hoohfx_beams", Layer.ADD, Kind.STREAK)
    private val featherSprite = FxMaterial("hoohfx_feather", Layer.ALPHA, Kind.WHITE, image = FEATHER_TEXTURE)
    private val batches = FxBatches()
    private val keptView = Matrix4f()
    private val keptProjection = Matrix4f()
    private var kept = false
    private val right = Vector3f()
    private val up = Vector3f()
    private val tmp = DoubleArray(3)
    private val trailPoints = FloatArray(3 * 64)

    private const val EMBER = 0
    private const val SPARK = 1
    private const val MOTE = 2

    private val GOLD = FxMeshes.argb(1f, 0.82f, 0.32f, 1f)
    private val FLAME = FxMeshes.argb(1f, 0.45f, 0.12f, 1f)
    private val WHITE = FxMeshes.argb(1f, 0.96f, 0.85f, 1f)
    /** The rainbow, from the outside (red) to the inside (violet). */
    private val RAINBOW = intArrayOf(
        FxMeshes.argb(1f, 0.22f, 0.18f, 1f), FxMeshes.argb(1f, 0.58f, 0.12f, 1f), FxMeshes.argb(1f, 0.95f, 0.25f, 1f),
        FxMeshes.argb(0.30f, 0.95f, 0.35f, 1f), FxMeshes.argb(0.25f, 0.65f, 1f, 1f), FxMeshes.argb(0.35f, 0.35f, 1f, 1f), FxMeshes.argb(0.70f, 0.35f, 1f, 1f),
    )

    fun init() {
        PlatformEvents.CLIENT_TICK_POST.subscribe(Priority.NORMAL) { tick() }
        PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { clear() }
    }

    private fun clear() {
        passage = null
        particles.clear()
        batches.clear()
        kept = false
        lastFrame = Double.NaN
    }

    private fun ensureConfig() {
        if (configLoaded) return
        configLoaded = true
        config = try {
            HoOhClientConfig.load()
        } catch (e: Exception) {
            HoOhClientConfig()
        }
    }

    /** The game's ticks now (with the frame's part), frozen while the solo game is paused. */
    private fun now(): Double {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return 0.0
        return level.gameTime + minecraft.timer.getGameTimeDeltaPartialTick(true).toDouble()
    }

    private fun sound(id: String, x: Double, y: Double, z: Double, volume: Float, pitch: Float) =
        CinematicAudio.play(id, x, y, z, volume * config.volume, pitch)

    // ---------------------------------------------------------------- packets

    fun receive(packet: S2CHoOhPacket) {
        ensureConfig()
        val d = packet.data
        if (packet.kind == S2CHoOhPacket.START) {
            if (d.size < 7) return
            particles.clear()
            passage = Passage(packet.session, Vector3d(d[0], d[1], d[2]), d[3], d[4], d[5] > 0.5, d[6].toInt(), packet.tick.toDouble())
            val p = passage!!
            if (!p.cinematic && p.mode != 3) {
                // The signal without the scene: a cry from the sky, the light.
                val dir = forward(p.yaw)
                sound("cobblemon:pokemon.hooh.cry", p.origin.x + dir.x * 40.0, p.origin.y + 30.0, p.origin.z + dir.z * 40.0, 4f, 1f)
                sound("minecraft:block.beacon.activate", p.origin.x, p.origin.y + 2.0, p.origin.z, 1.2f, 1.4f)
            }
            return
        }
        val p = passage ?: return
        if (packet.session != p.id) return
        when (packet.kind) {
            S2CHoOhPacket.POINTS -> {
                var i = 0
                while (i + 2 < d.size) {
                    p.flight.add(d[i], d[i + 1], d[i + 2])
                    i += 3
                }
            }
            S2CHoOhPacket.MOTION -> if (d.size >= 2) p.flight.setMotion(packet.tick, d[0], d[1])
            S2CHoOhPacket.FEATHER -> if (d.size >= 7) {
                p.feather = Feather(Vector3d(d[0], d[1], d[2]), Vector3d(d[3], d[4], d[5]), packet.tick.toDouble(), d[6])
                if (p.flight.size > 0) {
                    p.actor.cry()
                    sound("cobblemon:pokemon.hooh.cry", p.drawn.x, p.drawn.y, p.drawn.z, 4f, 0.95f)
                }
                sound("minecraft:block.amethyst_block.chime", d[0], d[1], d[2], 2f, 1.2f)
            }
            S2CHoOhPacket.LEAVE -> p.leave = packet.tick.toDouble()
            S2CHoOhPacket.PICKED -> p.feather?.let { f ->
                f.picked = now()
                burst(f.to.x, f.to.y + 0.6, f.to.z, config.particles(40), 3f, GOLD, SPARK, 2f, 0.12f, 77)
            }
            S2CHoOhPacket.END -> p.end = packet.tick.toDouble()
        }
    }

    // ---------------------------------------------------------------- ticks: sounds and hints

    private fun forward(yaw: Double): Vector3d {
        val r = Math.toRadians(yaw)
        return Vector3d(-sin(r), 0.0, cos(r))
    }

    private fun tick() {
        val p = passage ?: return
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val t = now()
        if (t > p.end && (p.feather == null || p.feather!!.picked >= 0.0 || t > p.end + 40.0)) {
            passage = null
            return
        }
        if (CinematicClient.isActive() || !p.visible) return
        val seconds = t / 20.0
        // Its wings and its cry, from where it is.
        if (p.leave == Double.MAX_VALUE) {
            if (seconds >= p.nextFlap) {
                sound("minecraft:entity.ender_dragon.flap", p.drawn.x, p.drawn.y, p.drawn.z, 2.2f, 1.25f)
                p.nextFlap = seconds + 0.95
            }
            if (seconds >= p.nextCry) {
                if (p.nextCry > 0.0) {
                    p.actor.cry()
                    sound("cobblemon:pokemon.hooh.cry", p.drawn.x, p.drawn.y, p.drawn.z, 4f, 1f)
                }
                p.nextCry = seconds + 16.0 + (p.id % 7)
            }
        }
        // The direction in the action bar (half a second).
        if (!config.hints || p.feather != null || p.leave != Double.MAX_VALUE || ++hudTicks % 10 != 0) return
        val dx = p.drawn.x - player.x
        val dz = p.drawn.z - player.z
        val distance = sqrt(dx * dx + dz * dz)
        val target = Math.toDegrees(atan2(-dx, dz))
        var delta = (target - player.yRot) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        val arrows = arrayOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")
        val arrow = arrows[Math.floorMod(Math.round(delta / 45.0).toInt(), 8)]
        val key = if (p.flight.motionSpeed <= 0.0) "generations_core.hooh.hint.waiting" else "generations_core.hooh.hint.follow"
        minecraft.gui.setOverlayMessage(Component.translatable(key, arrow, distance.toInt()).withStyle(ChatFormatting.GOLD), false)
    }

    // ---------------------------------------------------------------- Ho-Oh in the world's pass

    /** In the world's pass (LevelRendererMixin): the matrices kept, Ho-Oh drawn. */
    @JvmStatic
    fun beforeRareCandy(frustum: Matrix4f, projection: Matrix4f) {
        val p = passage ?: return
        try {
            keptView.set(frustum)
            keptProjection.set(projection)
            kept = true
            if (CinematicClient.isActive()) {
                p.visible = false
                return
            }
            val t = now()
            val dt = if (lastFrame.isNaN()) 0.0 else ((t - lastFrame) / 20.0).coerceIn(0.0, 0.25)
            lastFrame = t
            update(p, t, dt)
            if (!p.visible) return
            val leaving = leaveProgress(p, t)
            val scale = p.scale * (1.0 - smooth((leaving - 0.75) / 0.25))
            if (scale <= 0.02) return
            p.actor.render(p.drawn, p.heading, p.pitch, p.roll, scale, ActorPose.FLY, (t / 20.0).toFloat(), bright = true)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ho-Oh: drawing stopped", e)
            clear()
        }
    }

    private fun smooth(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    /** 0 before it leaves, 1 when it has vanished (4 s). */
    private fun leaveProgress(p: Passage, t: Double): Double = if (t < p.leave) 0.0 else ((t - p.leave) / 80.0).coerceIn(0.0, 1.0)

    /** Ho-Oh's place and attitude this frame: the flight, smoothed (no jump), banking in its turns. */
    private fun update(p: Passage, t: Double, dt: Double) {
        if (p.flight.size == 0) {
            p.visible = false
            return
        }
        p.flight.position(t, tmp)
        if (!p.drawnReady) {
            p.drawn.set(tmp[0], tmp[1], tmp[2])
            p.previous.set(p.drawn)
            p.drawnReady = true
            p.nextCry = t / 20.0 + 2.0
        }
        p.previous.set(p.drawn)
        val leaving = leaveProgress(p, t)
        if (leaving > 0.0) {
            // Up and away, faster and faster, into the sky.
            val dir = forward(p.heading)
            val k = 6.0 + 22.0 * leaving
            p.drawn.add(dir.x * k * dt, (4.0 + 18.0 * leaving) * dt, dir.z * k * dt)
            if (leaving >= 1.0 && !p.vanished) {
                p.vanished = true
                burst(p.drawn.x, p.drawn.y, p.drawn.z, config.particles(60), 6f, GOLD, SPARK, 2.2f, 0.25f, 31)
                for ((i, c) in RAINBOW.withIndex()) burst(p.drawn.x, p.drawn.y, p.drawn.z, config.particles(8), 4f, c, MOTE, 2.5f, 0.3f, 41 + i * 3)
                sound("minecraft:block.beacon.deactivate", p.drawn.x, p.drawn.y, p.drawn.z, 2.5f, 1.5f)
            }
        } else if (dt > 0.0) {
            val k = 1.0 - exp(-dt * 3.0)
            p.drawn.add((tmp[0] - p.drawn.x) * k, (tmp[1] - p.drawn.y) * k, (tmp[2] - p.drawn.z) * k)
        }
        p.visible = leaving < 1.0
        if (dt <= 0.0) return
        val vx = (p.drawn.x - p.previous.x) / dt
        val vy = (p.drawn.y - p.previous.y) / dt
        val vz = (p.drawn.z - p.previous.z) / dt
        val horizontal = sqrt(vx * vx + vz * vz)
        if (horizontal > 0.3) {
            val target = Math.toDegrees(atan2(-vx, vz))
            var delta = (target - p.heading) % 360.0
            if (delta > 180.0) delta -= 360.0
            if (delta < -180.0) delta += 360.0
            val turn = delta * (1.0 - exp(-dt * 4.0))
            p.heading += turn
            // Banking into the turn, the nose following the climb.
            val bank = (-turn / dt * 0.45).coerceIn(-38.0, 38.0)
            p.roll += (bank - p.roll) * (1.0 - exp(-dt * 3.0))
            val climb = (-Math.toDegrees(atan2(vy, horizontal)) * 0.6).coerceIn(-30.0, 30.0)
            p.pitch += (climb - p.pitch) * (1.0 - exp(-dt * 3.0))
        }
    }

    // ---------------------------------------------------------------- effects after the world

    private fun burst(x: Double, y: Double, z: Double, count: Int, speed: Float, colour: Int, material: Int, life: Float, size: Float, seed: Int) {
        for (i in 0 until count) {
            val a = rand(seed + i, 11) * 2.0 * PI
            val b = (rand(seed + i, 13) - 0.5) * PI
            val sp = speed * (0.4 + rand(seed + i, 17).toFloat())
            particles.emit(material, x, y, z, (cos(b) * sin(a) * sp).toFloat(), (sin(b) * sp).toFloat(), (cos(b) * cos(a) * sp).toFloat(), life * (0.7f + 0.6f * rand(seed + i, 19).toFloat()), size, size * 0.2f, colour, drag = 2.0f)
        }
    }

    private fun rand(seed: Int, salt: Int): Double {
        var h = seed * 0x27d4eb2d + salt * 0x165667b1
        h = h xor (h ushr 15)
        h *= 0x2c1b3c6d
        h = h xor (h ushr 12)
        return (h ushr 8) / 16777216.0
    }

    /** Right after the world's render (GameRendererMixin): the trail, the halo, the embers, the rainbow, the feather. */
    @JvmStatic
    fun renderAfterLevel() {
        val p = passage
        if (p == null && particles.count == 0) return
        try {
            if (p != null && CinematicClient.isActive()) return
            val t = now()
            emit(p, t)
            render(((t - frameT).coerceIn(0.0, 5.0) / 20.0).toFloat()) { b, cx, cy, cz -> if (p != null) drawExtras(p, b, cx, cy, cz, t) }
            frameT = t
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ho-Oh: effects stopped", e)
            clear()
        }
    }

    private var frameT = 0.0

    /** New embers, sparks and trail points this frame. */
    private fun emit(p: Passage?, t: Double) {
        if (p == null) return
        val seconds = t / 20.0
        if (p.visible && p.leave == Double.MAX_VALUE) {
            // The trail: a point every 0.1 s.
            if (seconds - p.lastTrail >= 0.1) {
                p.lastTrail = seconds
                p.trail.addLast(doubleArrayOf(p.drawn.x, p.drawn.y + 1.2 * p.scale, p.drawn.z, seconds))
            }
            // Embers and golden sparks from its wings.
            val rate = 18.0 * config.particleIntensity
            if (rate > 0.0) while (p.lastEmber + 1.0 / rate <= seconds) {
                p.lastEmber = if (seconds - p.lastEmber > 1.0) seconds else p.lastEmber + 1.0 / rate
                val k = (p.lastEmber * 131).toInt()
                val side = (rand(k, 3) - 0.5) * 4.0 * p.scale
                val dir = forward(p.heading + 90.0)
                val x = p.drawn.x + dir.x * side
                val z = p.drawn.z + dir.z * side
                val y = p.drawn.y + (1.0 + rand(k, 5) * 1.5) * p.scale
                val colour = when (k % 4) {
                    0 -> GOLD
                    1 -> FLAME
                    2 -> WHITE
                    else -> RAINBOW[k % RAINBOW.size]
                }
                particles.emit(if (k % 3 == 0) SPARK else EMBER, x, y, z, ((rand(k, 7) - 0.5) * 0.6).toFloat(), (-0.4 - rand(k, 9) * 0.6).toFloat(), ((rand(k, 8) - 0.5) * 0.6).toFloat(), 2.6f, 0.16f, 0.04f, colour, drag = 0.5f)
            }
        }
        while (p.trail.isNotEmpty() && seconds - p.trail.first()[3] > config.trailSeconds) p.trail.removeFirst()
        // The feather: golden sparks as it falls, a slow glitter where it lies.
        val f = p.feather ?: return
        if (f.picked >= 0.0) return
        val at = featherAt(f, t)
        val falling = t < f.start + f.seconds * 20.0
        val every = if (falling) 0.04 else 0.25
        if (seconds - f.lastSpark >= every) {
            f.lastSpark = seconds
            val k = (seconds * 97).toInt()
            particles.emit(if (falling) SPARK else MOTE, at.x + (rand(k, 1) - 0.5) * 0.4, at.y + (rand(k, 2) - 0.5) * 0.4, at.z + (rand(k, 3) - 0.5) * 0.4, 0f, if (falling) 0.1f else 0.35f, 0f, 1.6f, 0.09f, 0.02f, GOLD, drag = 1f)
        }
    }

    /** The feather's place: a slow fall, swaying and turning, then floating where it landed. */
    private fun featherAt(f: Feather, t: Double): Vector3d {
        val u = ((t - f.start) / (f.seconds * 20.0)).coerceIn(0.0, 1.0)
        val seconds = (t - f.start) / 20.0
        val e = 1.0 - (1.0 - u) * (1.0 - u)
        val sway = 1.4 * (1.0 - u)
        return Vector3d(
            f.from.x + (f.to.x - f.from.x) * e + sin(seconds * 2.3) * sway,
            f.from.y + (f.to.y + 0.7 - f.from.y) * e + 0.12 * sin(seconds * 2.0) * u,
            f.from.z + (f.to.z - f.from.z) * e + cos(seconds * 2.3) * sway,
        )
    }

    private fun drawExtras(p: Passage, b: FxBatches, cx: Double, cy: Double, cz: Double, t: Double) {
        val seconds = t / 20.0
        val player = Minecraft.getInstance().player
        // The rainbow trail: seven bands, fading with their age.
        if (p.trail.size >= 2) {
            val n = minOf(p.trail.size, 64)
            val start = p.trail.size - n
            val newest = p.trail.last()[3]
            val fade = (1.0 - (seconds - newest) / config.trailSeconds).coerceIn(0.0, 1.0).toFloat()
            for ((band, colour) in RAINBOW.withIndex()) {
                val offset = (band - 3) * 0.32 * p.scale
                for (i in 0 until n) {
                    val q = p.trail.elementAt(start + i)
                    trailPoints[i * 3] = (q[0] - cx).toFloat()
                    trailPoints[i * 3 + 1] = (q[1] + offset - cy).toFloat()
                    trailPoints[i * 3 + 2] = (q[2] - cz).toFloat()
                }
                FxMeshes.ribbon(b.of(trails), trailPoints, n, 0.02f, (0.26 * p.scale).toFloat(), FxMeshes.withAlpha(colour, 0f), FxMeshes.withAlpha(colour, 0.62f * fade))
            }
        }
        // Its golden halo, seen from afar; a light under it when the player falls behind.
        if (p.visible) {
            val leaving = leaveProgress(p, t)
            val glow = (1.0 - leaving * 0.5).toFloat()
            val x = (p.drawn.x - cx).toFloat()
            val y = (p.drawn.y + 1.4 * p.scale - cy).toFloat()
            val z = (p.drawn.z - cz).toFloat()
            val pulse = (0.9 + 0.1 * sin(seconds * 3.0)).toFloat()
            val s = p.scale.toFloat()
            FxMeshes.billboard(b.of(glows), x, y, z, 7.5f * s * pulse, 0f, FxMeshes.withAlpha(GOLD, 0.30f * glow), right, up)
            FxMeshes.billboard(b.of(glows), x, y, z, 3.6f * s, (seconds * 0.3).toFloat(), FxMeshes.withAlpha(WHITE, 0.45f * glow), right, up)
            FxMeshes.billboard(b.of(glows), x, y, z, 12f * s, 0f, FxMeshes.withAlpha(FLAME, 0.12f * glow), right, up)
            if (player != null && p.feather == null && p.leave == Double.MAX_VALUE) {
                val dx = p.drawn.x - player.x
                val dz = p.drawn.z - player.z
                val far = ((sqrt(dx * dx + dz * dz) - 24.0) / 16.0).coerceIn(0.0, 1.0).toFloat()
                if (far > 0.01f) FxMeshes.beam(b.of(beams), x, y, z, x, y - 34f, z, 0.9f * s, 2.2f * s, FxMeshes.withAlpha(GOLD, 0.35f * far), FxMeshes.withAlpha(GOLD, 0f))
            }
        }
        // Without the scene: the signal, a rainbow in the sky for six seconds.
        if (!p.cinematic && p.mode != 3) {
            val age = (t - p.start) / 20.0
            val show = (smooth(age / 1.5) * (1.0 - smooth((age - 4.5) / 1.5))).toFloat()
            if (show > 0.01f) drawRainbow(b, p, cx, cy, cz, show)
        }
        // The feather.
        val f = p.feather ?: return
        if (f.picked >= 0.0 && t - f.picked > 10.0) return
        val at = featherAt(f, t)
        val gone = if (f.picked >= 0.0) (1.0 - (t - f.picked) / 10.0).toFloat() else 1f
        val x = (at.x - cx).toFloat()
        val y = (at.y - cy).toFloat()
        val z = (at.z - cz).toFloat()
        val pulse = (0.7 + 0.3 * sin(seconds * 4.0)).toFloat()
        FxMeshes.billboard(b.of(glows), x, y, z, 1.6f * pulse * gone, 0f, FxMeshes.withAlpha(GOLD, 0.6f * gone), right, up)
        FxMeshes.billboard(b.of(featherSprite), x, y, z, 0.75f * gone, (seconds * 1.7).toFloat(), FxMeshes.withAlpha(0xFFFFFF, gone), right, up)
        // Where it lies: a column of light, to find it.
        if (t >= f.start + f.seconds * 20.0 && f.picked < 0.0) {
            FxMeshes.beam(b.of(beams), x, y - 0.6f, z, x, y + 7f, z, 0.35f, 0.08f, FxMeshes.withAlpha(GOLD, 0.45f * pulse), FxMeshes.withAlpha(WHITE, 0f))
        }
    }

    /** A rainbow arc ahead of the player, high in the sky (seven soft bands). */
    private fun drawRainbow(b: FxBatches, p: Passage, cx: Double, cy: Double, cz: Double, alpha: Float) {
        val dir = forward(p.yaw)
        val side = forward(p.yaw + 90.0)
        val centreX = p.origin.x + dir.x * 70.0
        val centreZ = p.origin.z + dir.z * 70.0
        val centreY = p.origin.y + 4.0
        val steps = 40
        for ((band, colour) in RAINBOW.withIndex()) {
            val radius = 46.0 - band * 1.6
            for (i in 0..steps) {
                val a = PI * i / steps
                trailPoints[i * 3] = (centreX + side.x * cos(a) * radius - cx).toFloat()
                trailPoints[i * 3 + 1] = (centreY + sin(a) * radius - cy).toFloat()
                trailPoints[i * 3 + 2] = (centreZ + side.z * cos(a) * radius - cz).toFloat()
            }
            FxMeshes.ribbon(b.of(trails), trailPoints, steps + 1, 1.0f, 1.0f, FxMeshes.withAlpha(colour, 0.42f * alpha), FxMeshes.withAlpha(colour, 0.42f * alpha))
        }
        // The golden light where Ho-Oh comes from.
        val lx = (centreX - cx).toFloat()
        val ly = (centreY + 30.0 - cy).toFloat()
        val lz = (centreZ - cz).toFloat()
        FxMeshes.billboard(b.of(glows), lx, ly, lz, 26f, 0f, FxMeshes.withAlpha(GOLD, 0.5f * alpha), right, up)
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

    @Suppress("unused")
    private fun near(a: Double, b: Double) = abs(a - b) < 1e-6
}
