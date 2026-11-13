package generations.gg.generations.core.generationscore.common.client.ultraspace

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.screen.ultraspace.UltraWarpTravelScreen
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.C2SUltraWarpActionPacket
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.C2SUltraWarpRideStatePacket
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWarpRideEventPacket
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWarpRideStartPacket
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWarpRideUpdatePacket
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpOutcome
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpSession
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpPortal
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpVariant
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpCollision
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpCourse
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpRideLimits
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpTunnel
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Ultra Warp Ride as this client draws it. The server is the authority: it says
 * where the journey is along the tunnel (S2CUltraWarpRideUpdatePacket, followed
 * smoothly in between), how much energy is left, what was taken or touched. The
 * client works out each frame where the player steers the Pokémon
 * (UltraWarpSteering, within UltraWarpRideLimits) and where the camera is
 * (UltraWarpCamera), sends the steering 20 times per second, and shows at once what
 * the Pokémon touches (the same course and collisions as the server) without waiting
 * for the server to count it.
 *
 * While it lasts, Minecraft's camera is moved to an empty point high above the world
 * (the anchor), turned like the ride's camera, and the journey is drawn around it
 * (UltraWarpTunnelRenderer, UltraWarpObjectRenderer, UltraWarpRiderRenderer, from
 * LevelRendererMixin). Nothing is changed for good: when the ride stops (end,
 * disconnection, error), the camera and the HUD are Minecraft's again at the next
 * frame. Only this player's view is concerned.
 */
object UltraWarpRideClient {
    /** Something seen happening at a place of the tunnel (a burst of light). */
    class Burst(val kind: UltraWarpCourse.Kind, val position: Vector3d, val start: Float)

    class Ride(val packet: S2CUltraWarpRideStartPacket, val config: UltraWarpClientConfig.Settings, val anchor: Vec3) {
        val settings = packet.settings
        val tunnel = UltraWarpTunnel(packet.seed, settings)
        val course = UltraWarpCourse(packet.seed, tunnel, settings)
        val variant: UltraWarpVariant get() = packet.variant
        val aspects: Set<String> = packet.aspects.toSet()
        val pokemonState = FloatingState()
        val steering = UltraWarpSteering(settings)
        val cameraRig = UltraWarpCamera()
        /** Size of the Pokémon for this journey, its shape and hitbox (UltraWarpRideLimits, as on the server). */
        val riderScale: Float = UltraWarpRideLimits.riderScale(settings)
        val riderRadius: Double = UltraWarpRideLimits.riderRadius(settings)
        val shape = UltraWarpRideLimits.shape(settings)
        val hitbox = UltraWarpRideLimits.hitbox(settings)
        /** Where this model's centre and seat are. */
        val model: UltraWarpClientConfig.RiderModel get() = if (variant == UltraWarpVariant.SUN) config.rider.sun else config.rider.moon
        private val started = System.nanoTime()

        /** Seconds of real time since the start (animations, floating, shake, effects). */
        val seconds: Float get() = (System.nanoTime() - started) / 1e9f

        // The server's distance and energy, and when they came (real seconds).
        private var baseDistance = 0.0
        private var baseSpeed = settings.speed.toDouble()
        private var baseTime = 0f
        private var correction = 0.0
        var speed = settings.speed.toDouble()
            private set
        private var serverEnergy = settings.config.energy.start.toDouble()
        private var energyTime = 0f

        /** Objects taken or touched (seen here, or told by the server). */
        val consumed = HashSet<Int>()
        val bursts = ArrayList<Burst>()

        /** Portals as the server placed them (by id), and the one the Pokémon went through. */
        val portals = LinkedHashMap<Int, UltraWarpPortal>()
        var captured: UltraWarpPortal? = null
        var captureStart = 0f
        var captureSeconds = 3f

        private var captureFromDistance = 0.0
        private var captureFromX = 0.0
        private var captureFromY = 0.0

        /** How far the trip through the portal has gone (0 to 1), null before. */
        val capture: Float?
            get() = if (captured == null) null else ((seconds - captureStart) / captureSeconds).coerceIn(0f, 1f)

        fun onPortal(portal: UltraWarpPortal) {
            portals[portal.id] = portal
            course.clear(portal.distance - settings.config.portals.clearBefore, portal.distance + 8.0)
        }
        /** Light on the screen's edges: damage (red) and energy (orange), 1 then fading. */
        var hitFlash = 0f
        var pickupFlash = 0f
        /** Extra camera shake after a hit, in degrees, fading. */
        var jolt = 0.0

        // Worked out each frame (update), in the tunnel's own space.
        var distance = 0.0
            private set
        /** The camera, and how far along the path it is. */
        val camera: Vector3d get() = cameraRig.position
        val cameraPath: Double get() = cameraRig.pathDistance
        val view: Quaternionf get() = cameraRig.view
        val forward: Vector3f get() = cameraRig.forward
        /** The Pokémon's centre (steered across the tunnel) and orientation (+Z forward, +Y up). */
        val riderPos = Vector3d()
        val riderRotation = Quaternionf()
        var allowed = 0.0
        var fov = 70.0
        var roll = 0.0
        var frameSeconds = 0f
        private var bank = 0.0
        private var pitch = 0.0
        private var yaw = 0.0
        private var lastFrame = 0L
        private var stateTimer = 0.0
        private var lastDistance = 0.0
        private var lastX = 0.0
        private var lastY = 0.0

        private val t = Vector3d()
        private val u = Vector3d()
        private val r = Vector3d()

        // Events. Storms and currents are windows of the course; anomalies and Necrozma
        // last a time from when the server said so.
        /** How much the storm shows (0 to 1, eased), and a lightning flash (1, fading). */
        var stormLevel = 0f
        var lightning = 0f
        private var lastFlash = 0L
        private var inStorm = false
        private var inCurrent = false
        private var anomalyStart = -1f
        private var anomalyEnd = -1f
        private var anomalyStrength = 0.0
        private var anomalySeed = 0
        private var defeatStart = -1f
        private var defeatDuration = 3f
        private var necrozmaStart = -1f
        private var necrozmaDuration = 1f
        val necrozmaState = FloatingState()
        /** The latest event's name, shown a few seconds. */
        var banner: net.minecraft.network.chat.Component? = null
        var bannerStart = 0f

        /** How strong the anomaly is now (0 to 1, in and out over half a second). */
        val anomaly: Float
            get() {
                val now = seconds
                if (now < anomalyStart || now > anomalyEnd) return 0f
                return min(1f, min(now - anomalyStart, anomalyEnd - now) * 2f)
            }

        /** How far the defeat has gone (0 to 1), null while the journey goes on. */
        val defeat: Float?
            get() = if (defeatStart < 0) null else ((seconds - defeatStart) / defeatDuration).coerceIn(0f, 1f)

        /** How far Necrozma has passed by (0 to 1), null when it is not there. */
        val necrozma: Float?
            get() = if (necrozmaStart < 0) null else ((seconds - necrozmaStart) / necrozmaDuration).takeIf { it in 0f..1f }

        /** Where Necrozma is in the tunnel's space: from far ahead, near the top, past the camera. */
        fun necrozmaPosition(): Vector3d? {
            val f = necrozma ?: return null
            val e = (f * f * (3 - 2 * f)).toDouble()
            val d = cameraPath + 170.0 + (-12.0 - 170.0) * e
            val core = tunnel.coreRadius(d)
            tunnel.frame(d, t, u, r)
            return tunnel.position(d).fma(core * (-0.2 + 0.35 * e), r).fma(core * (0.3 - 0.1 * e), u)
        }

        /** The walls' and glow's colours, turning red and magenta in a storm. */
        fun wallColour(): Int = mix(
            mix(mix(UltraWarpClientConfig.rgb(config.visual.wallColor), 0xFF6A9A, stormLevel), 0x5A4A7A, defeat ?: 0f),
            captured?.color ?: 0, (capture ?: 0f) * (capture ?: 0f),
        )

        fun glowColour(): Int = mix(mix(UltraWarpClientConfig.rgb(config.visual.glowColor), 0xFFA050, stormLevel), 0xE0C0FF, defeat ?: 0f)

        private fun mix(a: Int, b: Int, t: Float): Int {
            fun channel(shift: Int) = ((((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt() and 0xFF) shl shift
            return channel(16) or channel(8) or channel(0)
        }

        private fun announce(key: String) {
            banner = net.minecraft.network.chat.Component.translatable(key)
            bannerStart = seconds
        }

        /** Energy now: the server's, drained since it came. */
        val energy: Double
            get() {
                val drain = if (settings.config.test.invincible) 0.0 else settings.config.energy.drainPerSecond.toDouble() * settings.mode.drain
                return max(0.0, serverEnergy - drain * min(0.3f, seconds - energyTime))
            }

        val lightYears: Double get() = distance * settings.config.lightYearsPerBlock

        fun onUpdate(packet: S2CUltraWarpRideUpdatePacket) {
            val now = seconds
            // The displayed distance goes on smoothly: the gap to the server's fades out
            // (a jump, from a test command, is taken at once).
            val gap = predicted(now) - packet.distance
            if (kotlin.math.abs(gap) > 60.0) {
                correction = 0.0
                distance = packet.distance
                lastDistance = packet.distance
            } else {
                correction += gap
            }
            baseDistance = packet.distance
            baseSpeed = packet.speed.toDouble()
            baseTime = now
            speed = baseSpeed
            serverEnergy = packet.energy.toDouble()
            energyTime = now
        }

        /** The server's distance, ahead by its speed since it came (a short while: in solo, a paused game stops). */
        private fun predicted(now: Float): Double = baseDistance + baseSpeed * min(0.3f, now - baseTime)

        fun onEvent(packet: S2CUltraWarpRideEventPacket) {
            when (packet.type) {
                S2CUltraWarpRideEventPacket.Type.COLLECT, S2CUltraWarpRideEventPacket.Type.HIT -> {
                    serverEnergy = packet.value.toDouble()
                    energyTime = seconds
                    // Seen here already, or the server saw what the client did not.
                    if (consumed.add(packet.eventId)) {
                        course.slot(packet.eventId / 8).firstOrNull { it.id == packet.eventId }?.let { touched(it) }
                    }
                }
                S2CUltraWarpRideEventPacket.Type.WINDOW -> course.addWindow(
                    UltraWarpCourse.Window(
                        packet.eventId, UltraWarpCourse.WindowKind.entries[packet.a.toInt().coerceIn(0, UltraWarpCourse.WindowKind.entries.size - 1)],
                        packet.b, packet.c, packet.value, packet.d, packet.e,
                    ),
                )
                S2CUltraWarpRideEventPacket.Type.ANOMALY -> {
                    anomalyStart = seconds
                    anomalyEnd = seconds + packet.a.toFloat()
                    anomalyStrength = packet.value.toDouble()
                    anomalySeed = packet.eventId
                    announce("generations_core.ultra_warp.event.anomaly")
                    UltraWarpSounds.anomaly()
                }
                S2CUltraWarpRideEventPacket.Type.PORTAL_CAPTURE -> portals[packet.eventId]?.let {
                    it.state = UltraWarpPortal.State.CAPTURED
                    captured = it
                    captureStart = seconds
                    captureSeconds = packet.value.coerceAtLeast(0.5f)
                    captureFromDistance = distance
                    captureFromX = steering.x
                    captureFromY = steering.y
                    UltraWarpSounds.portal()
                }
                S2CUltraWarpRideEventPacket.Type.DEFEAT -> if (defeatStart < 0) {
                    defeatStart = seconds
                    defeatDuration = packet.a.toFloat().coerceAtLeast(0.5f)
                    announce("generations_core.ultra_warp.event.defeat")
                    UltraWarpSounds.defeat()
                }
                S2CUltraWarpRideEventPacket.Type.NECROZMA -> {
                    necrozmaStart = seconds
                    necrozmaDuration = packet.a.toFloat().coerceAtLeast(1f)
                    announce("generations_core.ultra_warp.event.necrozma")
                    UltraWarpSounds.necrozma()
                }
            }
        }

        /** The anomaly's drift of the steering: turning slowly, strength in blocks/s². */
        private fun drift(time: Float): Pair<Double, Double> {
            val level = anomaly
            if (level <= 0f) return 0.0 to 0.0
            val phase = anomalySeed * 0.37
            return anomalyStrength * level * sin(time * 1.3 + phase) to anomalyStrength * level * kotlin.math.cos(time * 0.9 + phase * 0.7)
        }

        /** Storms, currents, lightning: what the journey is going through now. */
        private fun events(dt: Double, time: Float) {
            val storm = course.windowAt(UltraWarpCourse.WindowKind.STORM, distance) != null
            if (storm && !inStorm) {
                announce("generations_core.ultra_warp.event.storm")
                UltraWarpSounds.storm()
            }
            inStorm = storm
            stormLevel += ((if (storm) 1f else 0f) - stormLevel) * (1f - exp(-1.5 * dt).toFloat())
            lightning = max(0f, lightning - frameSeconds * 4f)
            if (stormLevel > 0.3f) {
                val step = kotlin.math.floor(time * 7f).toLong()
                if (step != lastFlash && UltraWarpObjectRenderer.hash(packet.seed + step, 77) < 0.06) {
                    lastFlash = step
                    lightning = 1f
                    UltraWarpSounds.thunder()
                }
            }
            val current = course.windowAt(UltraWarpCourse.WindowKind.CURRENT, distance)
            val inside = current != null &&
                UltraWarpRideLimits.length(steering.x - current.x, steering.y - current.y) <= settings.config.objects.currentRadius
            if (inside && !inCurrent) {
                announce("generations_core.ultra_warp.event.current")
                UltraWarpSounds.current()
            }
            inCurrent = inside
        }

        fun update(partialTick: Float, steer: Boolean) {
            val now = System.nanoTime()
            frameSeconds = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceIn(0f, 0.1f)
            lastFrame = now
            val dt = frameSeconds.toDouble()
            val time = seconds
            val cam = config.camera
            val rider = config.rider

            correction *= exp(-4.0 * dt)
            distance = max(distance, predicted(time) + correction)
            // Through a portal: drawn to its middle and into it, whatever the server's distance.
            val through = capture
            val into = captured
            val ease = through?.let { it * it * (3 - 2 * it) } ?: 0f
            if (into != null) distance = captureFromDistance + (into.distance + 2.0 - captureFromDistance) * ease

            // Steering across the tunnel, within the limits of each direction (the
            // floating, the Pokémon's tilt of the last frame counted).
            steering.read(steer, config.controls)
            val bounds = object : UltraWarpSteering.Bounds {
                override fun allowed(nx: Double, ny: Double): Double =
                    max(0.0, UltraWarpRideLimits.allowed(tunnel, distance, shape, nx, ny, bank, pitch, yaw) - rider.bob)

                override fun margin(ny: Double): Double = UltraWarpRideLimits.margin(settings, ny)
            }
            val (pushX, pushY) = pull()
            val (driftX, driftY) = drift(time)
            // Defeat: no more control, the Pokémon falls away spinning.
            val lost = defeat
            val fallX = if (lost != null) 6.0 * sin(time * 4.0) else 0.0
            val fallY = if (lost != null) -10.0 else 0.0
            // Portals do not pull: only going through one's opening takes the Pokémon in.
            steering.update(
                dt, bounds, settings.config.limits.softness.toDouble(),
                pushX + driftX + fallX, pushY + driftY + fallY, lost == null && into == null,
            )
            val steerX = if (into != null) captureFromX + (into.x - captureFromX) * ease else steering.x
            val steerY = if (into != null) captureFromY + (into.y - captureFromY) * ease else steering.y
            events(dt, time)
            allowed = bounds.allowed(0.0, -1.0)

            // The Pokémon's centre: on the path, where it is steered, floating a little.
            tunnel.frame(distance, t, u, r)
            tunnel.position(distance, riderPos)
            riderPos.fma(steerX, r).fma(steerY + sin(time * 0.9) * rider.bob, u)

            // Banked into the bends, and tilted by its own moves.
            val curve = (tunnel.turn(distance + 4.0) * 2400.0 * cam.roll).coerceIn(-cam.maxRoll.toDouble(), cam.maxRoll.toDouble())
            roll = if (dt == 0.0) curve else roll + (curve - roll) * (1 - exp(-cam.smoothing * dt))
            val steerSpeed = settings.steerSpeed.toDouble().coerceAtLeast(1e-3)
            val sideways = (steering.vx / steerSpeed).coerceIn(-1.0, 1.0)
            val vertical = (steering.vy / steerSpeed).coerceIn(-1.0, 1.0)
            bank = Math.toRadians(roll + sideways * rider.maxBank)
            pitch = Math.toRadians(vertical * rider.maxPitch)
            yaw = Math.toRadians(sideways * rider.maxYaw)
            val spin = lost?.let { it * it * 14f } ?: 0f
            orientation(Vector3f().set(t), Vector3f().set(u), riderRotation)
                .rotateY(-yaw.toFloat())
                .rotateX(-pitch.toFloat() + (lost ?: 0f) * 0.6f)
                .rotateZ(bank.toFloat() + spin)

            // The camera follows the tunnel, and the Pokémon a little.
            jolt *= exp(-5.0 * dt)
            // Storms shake it, anomalies make it sway.
            val sway = anomaly * 6.0 * sin(time * 2.1)
            cameraRig.update(
                tunnel, cam, dt, time, speed, distance, riderPos, riderRadius, steerX, steerY,
                roll + sideways * rider.maxBank * 0.3 + sway, jolt + stormLevel * 0.8 + anomaly * 0.5,
            )
            fov = (cam.fov + cam.fovBoost * min(1.0, speed / 40.0) + 35.0 * ease)
            hitFlash = max(0f, hitFlash - frameSeconds * 2.5f)
            pickupFlash = max(0f, pickupFlash - frameSeconds * 2.5f)
            bursts.removeIf { time - it.start > 0.6f }

            // What the Pokémon touches, shown at once (the server counts it).
            if (into == null) touch(lastDistance, lastX, lastY, distance, steering.x, steering.y)
            lastDistance = distance
            lastX = steering.x
            lastY = steering.y

            // Where it steers, 20 times per second, for the server.
            stateTimer += dt
            if (stateTimer >= 1.0 / settings.statesPerSecond.coerceAtLeast(1)) {
                stateTimer = 0.0
                C2SUltraWarpRideStatePacket(distance, steering.x.toFloat(), steering.y.toFloat(), steering.boost, steering.takeDodge()).sendToServer()
            }
        }

        /**
         * The gravitational anomalies near the Pokémon pull it towards them (or push it
         * away), stronger near their middle and fading along gravityLength.
         */
        private fun pull(): Pair<Double, Double> {
            val objects = settings.config.objects
            val reach = objects.gravityLength / 2.0
            val items = ArrayList<UltraWarpCourse.Item>()
            course.items(distance - reach, distance + reach, items)
            var px = 0.0
            var py = 0.0
            for (item in items) {
                if (item.kind != UltraWarpCourse.Kind.GRAVITY) continue
                val along = 1.0 - kotlin.math.abs(item.distance - distance) / reach
                if (along <= 0.0) continue
                val dx = item.x - steering.x
                val dy = item.y - steering.y
                val gap = UltraWarpRideLimits.length(dx, dy)
                if (gap < 1e-3) continue
                val across = (1.0 - gap / (item.radius * 3.0)).coerceIn(0.0, 1.0)
                val force = item.strength * along * across
                px += dx / gap * force
                py += dy / gap * force
            }
            return px to py
        }

        private fun touch(d0: Double, x0: Double, y0: Double, d1: Double, x1: Double, y1: Double) {
            if (d1 <= d0) return
            val items = ArrayList<UltraWarpCourse.Item>()
            course.items(d0 - 12.0, d1 + 12.0, items)
            for (item in items) {
                if (item.id in consumed || item.kind == UltraWarpCourse.Kind.GRAVITY) continue
                val grow = if (item.kind == UltraWarpCourse.Kind.ORB) settings.config.objects.pickupScale.toDouble() else 1.0
                if (UltraWarpCollision.touches(item, hitbox, d0, x0, y0, d1, x1, y1, grow)) {
                    consumed.add(item.id)
                    touched(item)
                }
            }
        }

        /** The look and sound of an object taken or touched. */
        private fun touched(item: UltraWarpCourse.Item) {
            val position = tunnel.position(item.distance)
            tunnel.frame(item.distance, t, u, r)
            position.fma(item.x, r).fma(item.y, u)
            bursts.add(Burst(item.kind, position, seconds))
            if (item.kind == UltraWarpCourse.Kind.ORB) {
                pickupFlash = 1f
            } else {
                hitFlash = 1f
                jolt = 2.5
            }
            UltraWarpSounds.touched(item.kind)
        }
    }

    var ride: Ride? = null
        private set

    private var failed = false

    @JvmStatic
    val isActive: Boolean get() = ride != null

    fun init() {
        PlatformEvents.CLIENT_TICK_POST.subscribe(Priority.NORMAL) { tick() }
        // Leaving the world or the server: back to normal, whatever happened.
        PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { stopLocally() }
        UltraWarpTransferClient.init()
    }

    fun start(packet: S2CUltraWarpRideStartPacket) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val level = minecraft.level ?: return
        val config = UltraWarpClientConfig.load()
        // High above the world: nothing to draw there but the journey.
        val anchor = Vec3(player.x, (level.maxBuildHeight + 2048).toDouble(), player.z)
        ride = Ride(packet, config, anchor)
        failed = false
        minecraft.setScreen(UltraWarpTravelScreen())
    }

    fun onUpdate(packet: S2CUltraWarpRideUpdatePacket) {
        ride?.onUpdate(packet)
    }

    fun onEvent(packet: S2CUltraWarpRideEventPacket) {
        ride?.onEvent(packet)
    }

    fun onPortal(portal: UltraWarpPortal) {
        ride?.onPortal(portal)
    }

    /** How the last journey ended (the server's record), for the result screen. */
    var lastOutcome: UltraWarpOutcome? = null
        private set

    /** The server ended the journey. */
    fun stop(reason: UltraWarpSession.EndReason, outcome: UltraWarpOutcome? = null) {
        lastOutcome = outcome
        if (ride == null) return
        stopLocally()
        // Through a portal: the white stays while the server takes the player into its
        // world (the result after arriving), or where it led when it cannot be reached.
        if (reason == UltraWarpSession.EndReason.SUCCESS && outcome != null) {
            if (outcome.arrival == UltraWarpOutcome.Arrival.PENDING) UltraWarpTransferClient.begin(outcome)
            else Minecraft.getInstance().setScreen(generations.gg.generations.core.generationscore.common.client.screen.ultraspace.UltraWarpResultScreen(outcome))
        }
    }

    /** The player leaves the journey: back to the world at once, the server is told. */
    fun quit() {
        val ride = ride ?: return
        stopLocally()
        C2SUltraWarpActionPacket(BlockPos.ZERO, C2SUltraWarpActionPacket.Action.END, ride.variant).sendToServer()
    }

    private fun stopLocally() {
        ride = null
        val minecraft = Minecraft.getInstance()
        if (minecraft.screen is UltraWarpTravelScreen) minecraft.setScreen(null)
    }

    /** Something could not be drawn: logged once, and the journey ends rather than leave the player stuck. */
    @JvmStatic
    fun fail(error: Throwable) {
        if (!failed) GenerationsCore.LOGGER.error("Ultra Warp Ride: the journey could not be drawn, back to the world", error)
        failed = true
        quit()
    }

    private fun tick() {
        val minecraft = Minecraft.getInstance()
        if (ride == null) return
        if (minecraft.level == null || minecraft.player == null) return stopLocally()
        // The journey's screen holds the keys (nothing reaches the world); it comes back
        // when another one closed it (the pause menu after leaving the window).
        if (minecraft.screen == null && minecraft.player!!.isAlive) minecraft.setScreen(UltraWarpTravelScreen())
    }

    /** Each frame, from Camera.setup: where the ride is. False when there is no ride. */
    @JvmStatic
    fun update(partialTick: Float): Boolean {
        val ride = ride ?: return false
        if (Minecraft.getInstance().level == null) return false
        return try {
            // The keys steer only while the journey's own screen has them (not its confirmation, not a menu).
            val screen = Minecraft.getInstance().screen
            ride.update(partialTick, screen is UltraWarpTravelScreen && !screen.confirming)
            true
        } catch (e: Exception) {
            fail(e)
            false
        }
    }

    @JvmStatic
    fun anchor(): Vec3 = ride?.anchor ?: Vec3.ZERO

    @JvmStatic
    fun view(): Quaternionf = ride?.view ?: Quaternionf()

    /** Minecraft's yaw for the look (the quaternion also has the roll). */
    @JvmStatic
    fun yaw(): Float = ride?.forward?.let { Math.toDegrees(atan2(-it.x.toDouble(), it.z.toDouble())).toFloat() } ?: 0f

    @JvmStatic
    fun pitch(): Float = ride?.forward?.let { Math.toDegrees(asin(-it.y.toDouble().coerceIn(-1.0, 1.0))).toFloat() } ?: 0f

    @JvmStatic
    fun fov(): Double = ride?.fov ?: 70.0

    @JvmStatic
    fun hidesHud(): Boolean = ride?.config?.dev?.hideHud == true

    @JvmStatic
    fun fogColor(): Int = ride?.let { UltraWarpClientConfig.rgb(it.config.visual.fogColor) } ?: 0

    @JvmStatic
    fun fogStart(): Float = ride?.config?.visual?.fadeStart ?: 0f

    @JvmStatic
    fun fogEnd(): Float = (ride?.config?.visual?.fadeEnd ?: 0f) + 20f

    /** Opaque part of the journey (walls, Pokémon, player), before RareCandy's pass draws the Pokémon. */
    @JvmStatic
    fun renderOpaque() {
        val ride = ride ?: return
        try {
            val buffers = Minecraft.getInstance().renderBuffers().bufferSource()
            if (ride.config.dev.showTunnel) UltraWarpTunnelRenderer.renderOpaque(ride, buffers)
            UltraWarpObjectRenderer.renderOpaque(ride, buffers)
            if (ride.config.dev.showRider) UltraWarpRiderRenderer.render(ride, buffers)
            UltraWarpRiderRenderer.renderNecrozma(ride, buffers)
        } catch (e: Exception) {
            fail(e)
        }
    }

    /** Glowing part (light layer, streaks, objects, light at the end), after the solid models. */
    @JvmStatic
    fun renderTranslucent() {
        val ride = ride ?: return
        try {
            val buffers = Minecraft.getInstance().renderBuffers().bufferSource()
            if (ride.config.dev.showTunnel) UltraWarpTunnelRenderer.renderTranslucent(ride, buffers)
            UltraWarpObjectRenderer.render(ride, buffers)
            UltraWarpPortalRenderer.render(ride, buffers)
        } catch (e: Exception) {
            fail(e)
        }
    }

    /** Rotation turning +Z into forward and +Y into up (orthonormal). */
    fun orientation(forward: Vector3f, up: Vector3f, out: Quaternionf): Quaternionf {
        val z = Vector3f(forward).normalize()
        val y = Vector3f(up).fma(-up.dot(z), z).normalize()
        val x = Vector3f(y).cross(z).normalize()
        return out.setFromNormalized(Matrix3f(x, y, z))
    }

    /** Camera rotation in Minecraft's convention: -Z looks forward, +Y is up. */
    fun cameraOrientation(forward: Vector3f, up: Vector3f, out: Quaternionf): Quaternionf {
        val z = Vector3f(forward).negate().normalize()
        val y = Vector3f(up).fma(-up.dot(z), z).normalize()
        val x = Vector3f(y).cross(z).normalize()
        return out.setFromNormalized(Matrix3f(x, y, z))
    }
}
