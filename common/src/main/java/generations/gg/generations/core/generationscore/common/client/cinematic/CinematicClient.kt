package generations.gg.generations.core.generationscore.common.client.cinematic

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.cinematic.actor.CinematicActor
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.CinematicAudio
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CamFrame
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMotion
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CinematicCamera
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.CinematicRenderer
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FlashLimiter
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxParticles
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorPose
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ArticunoScene
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.CinematicScene
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Cue
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.SceneFrame
import generations.gg.generations.core.generationscore.common.network.packets.cinematic.S2CCinematicEndPacket
import generations.gg.generations.core.generationscore.common.network.packets.cinematic.S2CCinematicStartPacket
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicEndReason
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3d
import java.util.UUID

/**
 * A legendary cinematic on this client (CINEMATIQUES-PLAN.md § 2): the start packet sets
 * everything up; then each frame the clock (the server's tick, then real time, frozen while
 * the solo game is paused), the shots and the camera (CameraMixin), the actor (in the world's
 * pass), the effects and the composition (after the world's render), the cues' sounds. The
 * keys are held by an invisible screen; the HUD is hidden.
 *
 * The camera is always given back: the end packet, leaving the world or the server, another
 * world, 3 s after the scene's planned end, or any exception (logged once).
 */
object CinematicClient {
    class Playback(
        val packet: S2CCinematicStartPacket,
        val config: CinematicClientConfig,
        val scene: CinematicScene,
        val clock: CinematicClock,
        val camera: CinematicCamera,
        val frame: SceneFrame,
        val level: ClientLevel,
        val actor: CinematicActor,
        /** The scene's duration over the seconds it is written for (20, Mewtwo's 24). */
        val stretch: Double,
    ) {
        /** Near the scene but unable to watch it: its ambience only (effects, sound), no camera, no screen (D11). */
        val ambient: Boolean get() = packet.ambient

        /** The seconds the scene is written for. */
        val written: Double = CinematicScenes.writtenSeconds(packet.scene)

        val motion = CameraMotion(config.reduceCameraMotion)
        val flash = FlashLimiter(config.reduceFlashes)
        val shot = CamFrame()
        var cue = 0
        var lastSeconds = Double.NaN
        var lastAction = 0.0
        /** The music and ambience, made ahead from the start packet on (SynthBuffer). */
        var musicSource: generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthBuffer? = null
        var ambienceSource: generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthBuffer? = null
        var music: CinematicAudio.StreamSound? = null
        var ambience: CinematicAudio.StreamSound? = null
        var streams = false

        /** Cut short (clock seconds when the quick fade began), or the server said it ended. */
        var ending = -1.0
        var finishedAt = -1.0
        var flashValue = 0.0
        var presence = 0.0
        var sceneTime = 0.0

        val durationSeconds: Double get() = packet.durationTicks / 20.0
    }

    var playback: Playback? = null
        private set
    private var failed = false

    /** Entities hidden while their cinematic's actor stands for them (lot 5). */
    private val hidden = HashSet<UUID>()

    fun init() {
        PlatformEvents.CLIENT_TICK_POST.subscribe(Priority.NORMAL) { tick() }
        PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { stopLocally(0) }
    }

    /** A scene holds this player's camera (not only its ambience). */
    @JvmStatic
    fun isActive(): Boolean = playback?.ambient == false

    @JvmStatic
    fun hidesHud(): Boolean = playback?.ambient == false

    @JvmStatic
    fun hides(entity: Entity): Boolean = hidden.isNotEmpty() && entity.uuid in hidden

    fun start(packet: S2CCinematicStartPacket) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        if (minecraft.player == null) return
        stopLocally(0)
        if (packet.scene !in CinematicScenes.AVAILABLE) {
            GenerationsCore.LOGGER.warn("Legendary cinematic: unknown scene {}", packet.scene)
            return
        }
        try {
            val config = CinematicClientConfig.load()
            val camera = CinematicCamera(packet.origin, packet.facing)
            val landing = camera.toLocal(packet.spawn, Vector3d())
            val species = ResourceLocation.tryParse(packet.species) ?: return
            val title = PokemonSpecies.getByIdentifier(species)?.translatedName ?: Component.literal(packet.scene)
            val scene: CinematicScene = when (packet.scene) {
                CinematicScenes.ZAPDOS -> generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ZapdosScene(landing, packet.test, title)
                CinematicScenes.MOLTRES -> generations.gg.generations.core.generationscore.common.client.cinematic.scenes.MoltresScene(landing, packet.test, title)
                CinematicScenes.MEWTWO -> {
                    // Where the RKS Machine's renderer draws Mewtwo in its tank (0.9 of its size, centred at 1.95).
                    val form = PokemonSpecies.getByIdentifier(species)?.standardForm
                    val height = (form?.let { it.hitbox.height() * it.baseScale } ?: 2f) * 0.9
                    generations.gg.generations.core.generationscore.common.client.cinematic.scenes.MewtwoScene(landing, packet.test, title, 1.95 - height / 2.0)
                }
                else -> ArticunoScene(landing, packet.test, title)
            }
            val particles: FxParticles = scene.particles(6000)
            val frame = SceneFrame(camera, config, particles, packet.test)
            val stretch = (packet.durationTicks / 20.0 / CinematicScenes.writtenSeconds(packet.scene)).coerceIn(0.5, 2.0)
            val clock = CinematicClock(CinematicClock.startSeconds(level.gameTime, packet.startGameTime))
            val playback = Playback(packet, config, scene, clock, camera, frame, level, CinematicActor(species, packet.aspects.toSet()), stretch)
            // A late client skips what already happened.
            val startTime = clock.seconds / stretch
            if (startTime > 0.25) {
                val a = scene.script.action(startTime)
                while (playback.cue < scene.script.cues.size && scene.script.cues[playback.cue].time < a) playback.cue++
                playback.lastAction = a
            }
            // Made ahead from now (half a second before the scene's second 0).
            val offset = clock.seconds.coerceAtLeast(0.0)
            playback.musicSource = scene.music(stretch, offset)?.let { generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthBuffer(it).start("Generations cinematic music") }
            playback.ambienceSource = scene.ambience(stretch, offset)?.let { generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthBuffer(it).start("Generations cinematic ambience") }
            this.playback = playback
            failed = false
            CinematicRenderer.reset()
            minecraft.musicManager.stopPlaying()
            if (!packet.ambient) minecraft.setScreen(CinematicScreen())
        } catch (e: Exception) {
            fail(e)
        }
    }

    fun end(packet: S2CCinematicEndPacket) {
        val p = playback ?: return
        if (p.packet.session != packet.session) return
        if (packet.reason == CinematicEndReason.FINISHED) {
            if (p.finishedAt < 0) p.finishedAt = p.clock.seconds
        } else {
            interrupt(p)
        }
    }

    private fun interrupt(p: Playback) {
        if (p.ending >= 0) return
        p.ending = p.clock.seconds
        CinematicAudio.stopStream(p.music, 12)
        CinematicAudio.stopStream(p.ambience, 12)
    }

    /** Back to the player's own camera now. fadeTicks: the streams' fade. */
    fun stopLocally(fadeTicks: Int = 8) {
        val p = playback ?: return
        playback = null
        p.musicSource?.cancel()
        p.ambienceSource?.cancel()
        CinematicAudio.stopStream(p.music, fadeTicks)
        CinematicAudio.stopStream(p.ambience, fadeTicks)
        CinematicAudio.clear()
        CinematicRenderer.reset()
        hidden.clear()
        val minecraft = Minecraft.getInstance()
        if (minecraft.screen is CinematicScreen) minecraft.setScreen(null)
    }

    /** Something could not be done: logged once, and the camera given back rather than leave the player stuck. */
    @JvmStatic
    fun fail(error: Throwable) {
        if (!failed) GenerationsCore.LOGGER.error("Legendary cinematic: stopped, back to the player's camera", error)
        failed = true
        stopLocally(0)
    }

    private fun tick() {
        val p = playback ?: return
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        if (minecraft.level == null || player == null || minecraft.level !== p.level) return stopLocally(0)
        if (p.clock.seconds > p.durationSeconds + 3.0) return stopLocally()
        // The scene's screen holds the keys; it comes back when another closed it (the pause menu).
        if (!p.ambient && minecraft.screen == null && player.isAlive) minecraft.setScreen(CinematicScreen())
    }

    /**
     * Each frame, from Camera.setup: the scene's time, its cues, its actor and its camera. False
     * when nothing is to change in the player's camera.
     */
    @JvmStatic
    fun updateCamera(camera: Camera, partialTick: Float): Boolean {
        val p = playback ?: return false
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return false
        if (level !== p.level) {
            stopLocally(0)
            return false
        }
        return try {
            frame(p, camera, level)
        } catch (e: Exception) {
            fail(e)
            false
        }
    }

    private fun frame(p: Playback, camera: Camera, level: ClientLevel): Boolean {
        val minecraft = Minecraft.getInstance()
        val seconds = p.clock.frame(System.nanoTime(), minecraft.isPaused)
        val dt = if (p.lastSeconds.isNaN()) 0.0 else (seconds - p.lastSeconds).coerceAtLeast(0.0)
        p.lastSeconds = seconds
        val t = seconds / p.stretch
        p.sceneTime = t
        // The end: the scene played out, the quick fade of an interruption done, or the server's end.
        if (t >= p.written + 0.05 || (p.ending >= 0 && seconds - p.ending > 0.6) || (p.finishedAt >= 0 && seconds - p.finishedAt > 1.5)) {
            stopLocally()
            return false
        }
        val script = p.scene.script
        val clamped = t.coerceIn(0.0, p.written)
        val a = script.action(clamped)
        val frame = p.frame
        frame.t = clamped
        frame.da = (a - p.lastAction).coerceAtLeast(0.0)
        frame.a = a
        frame.dt = dt
        p.lastAction = a
        if (t >= 0.0) {
            streams(p, seconds)
            cues(p, a)
        }
        script.actor(a, frame.actor)
        p.camera.toWorld(frame.actor.pos, frame.actorWorld)
        p.scene.update(frame)
        sounds(p, clamped)

        script.director.evaluate(clamped, p.motion, p.shot)
        val endFade = if (p.ending >= 0) 1.0 - CameraMath.smooth((seconds - p.ending) / 0.6) else 1.0
        // The ambience only: the player's own camera, softer effects.
        val weight = if (t < 0.0 || p.ambient) 0.0 else script.cameraWeight(clamped) * endFade
        p.presence = (if (t < 0.0) 0.0 else script.presence(clamped)) * endFade * (if (p.ambient) 0.8 else 1.0)
        p.flashValue = p.flash.limit(clamped, script.flash(a)) * endFade * (if (p.ambient) 0.5 else 1.0)
        val shake = if (p.ambient) 0.0 else script.shake(a) * p.motion.shake * endFade
        p.camera.update(level, p.shot, clamped, dt, shake, weight, camera.position, Quaternionf(camera.rotation()))
        frame.cam.set(p.camera.position)
        frame.right.set(1f, 0f, 0f).rotate(p.camera.rotation)
        frame.up.set(0f, 1f, 0f).rotate(p.camera.rotation)
        frame.forward.set(0f, 0f, -1f).rotate(p.camera.rotation)
        return weight > 0.0
    }

    @Suppress("UNUSED_PARAMETER")
    private fun streams(p: Playback, seconds: Double) {
        if (p.streams || p.ending >= 0) return
        p.streams = true
        p.musicSource?.let { p.music = CinematicAudio.playStream(p.scene.id, "music", it, p.config.musicVolume * (if (p.ambient) 0.5f else 1f)) }
        p.ambienceSource?.let { p.ambience = CinematicAudio.playStream(p.scene.id, "ambience", it, p.config.volume) }
    }

    /** The scene's own sounds that are due (thunder after its lightning...). */
    private fun sounds(p: Playback, t: Double) {
        val list = p.frame.sounds
        if (list.isEmpty() || p.ending >= 0) return
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val sound = iterator.next()
            if (sound.at > t) continue
            iterator.remove()
            CinematicAudio.play(sound.id, sound.x, sound.y, sound.z, sound.volume * p.config.volume, sound.pitch)
        }
    }

    private val where = Vector3d()

    private fun cues(p: Playback, a: Double) {
        val cues = p.scene.script.cues
        while (p.cue < cues.size && cues[p.cue].time <= a) {
            val cue = cues[p.cue++]
            when (cue.kind) {
                Cue.Kind.EVENT -> p.scene.event(cue, p.frame)
                Cue.Kind.ANIMATE -> p.actor.animate(cue.id)
                Cue.Kind.SOUND, Cue.Kind.CRY -> {
                    if (cue.kind == Cue.Kind.CRY) p.actor.cry()
                    when (cue.where) {
                        "actor" -> p.camera.toWorld(p.frame.actor.pos.x + cue.dx, p.frame.actor.pos.y + 1.0 + cue.dy, p.frame.actor.pos.z + cue.dz, where)
                        "camera" -> where.set(p.camera.position)
                        else -> p.camera.toWorld(cue.dx, cue.dy, cue.dz, where)
                    }
                    CinematicAudio.play(cue.id, where.x, where.y, where.z, cue.volume * p.config.volume, cue.pitch)
                }
            }
        }
    }

    // ---------------------------------------------------------------- the mixins' side

    @JvmStatic
    fun cameraPosition(): Vec3 = playback?.camera?.position?.let { Vec3(it.x, it.y, it.z) } ?: Vec3.ZERO

    @JvmStatic
    fun cameraRotation(): Quaternionf = playback?.camera?.rotation ?: Quaternionf()

    @JvmStatic
    fun cameraYaw(): Float = playback?.camera?.yaw ?: 0f

    @JvmStatic
    fun cameraPitch(): Float = playback?.camera?.pitch ?: 0f

    @JvmStatic
    fun cameraDetached(): Boolean = playback?.camera?.detached ?: false

    /** The field of view: the player's own mixed with the shot's. */
    @JvmStatic
    fun fov(real: Double): Double = playback?.camera?.fov(real) ?: real

    /** In the world's pass, before RareCandy's: the matrices kept, the actor queued. */
    @JvmStatic
    fun beforeRareCandy(frustum: org.joml.Matrix4f, projection: org.joml.Matrix4f) {
        val p = playback ?: return
        try {
            CinematicRenderer.keep(frustum, projection)
            val actor = p.frame.actor
            if (actor.pose != ActorPose.HIDDEN && p.sceneTime >= 0.0) {
                p.actor.render(p.frame.actorWorld, p.camera.yawToWorld(actor.yaw), actor.pitch, actor.roll, actor.scale, actor.pose, p.frame.a.toFloat())
            }
        } catch (e: Exception) {
            fail(e)
        }
    }

    /** Right after the world's render (with or without a shader pack): the effects and the composition. */
    @JvmStatic
    fun renderAfterLevel() {
        val p = playback ?: return
        if (p.sceneTime < 0.0) return
        try {
            CinematicRenderer.render(p.scene, p.frame, p.config, p.flashValue, p.presence)
        } catch (e: Exception) {
            fail(e)
        }
    }
}
