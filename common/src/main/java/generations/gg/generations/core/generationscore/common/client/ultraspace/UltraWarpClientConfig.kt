package generations.gg.generations.core.generationscore.common.client.ultraspace

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import generations.gg.generations.core.generationscore.common.GenerationsCore
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Look of the Ultra Warp Ride on this client (camera, colours, effects):
 * config/generations/core/ultra_space/ultra_warp_ride_client.json, read again at the
 * start of each journey, so that it can be tuned without restarting. The file is
 * written back with every option and the values brought into range. What shapes the
 * journey itself (speed, tunnel) is the server's.
 */
object UltraWarpClientConfig {
    class Settings {
        /** Version of the file: older ones are brought up to date (see load). */
        var version: Int = 0
        var camera = Camera()
        var visual = Visual()
        var rider = Rider()
        var controls = Controls()
        var dev = Dev()

        fun validate() {
            camera.validate()
            visual.validate()
            rider.validate()
        }
    }

    /**
     * The camera follows the tunnel (never free): behind the Pokémon on the tunnel's
     * path, a little towards where it steers, always kept inside the walls; nearer
     * when the walls would hide the Pokémon in a bend.
     */
    class Camera {
        /** Distance behind the Pokémon, in Pokémon sizes (the room it takes in the tunnel). */
        var distance: Float = 3.4f
        /** Height above the tunnel's path, in Pokémon sizes. */
        var height: Float = 0.9f
        var minDistance: Float = 6f
        var maxDistance: Float = 40f
        /** How much the camera follows the Pokémon across the tunnel (0: stays on the path, 1: follows fully). */
        var follow: Float = 0.5f
        /** Room kept between the camera and the walls, in blocks. */
        var wallMargin: Float = 1.5f
        /** The camera looks this far ahead of the Pokémon. */
        var lookAhead: Float = 10f
        /** How fast the camera turns with the tunnel (higher: stiffer). */
        var smoothing: Float = 4f
        /** How fast it slides across the tunnel and changes distance. */
        var positionSmoothing: Float = 5f
        /** Banking into the bends (0: none). */
        var roll: Float = 1f
        var maxRoll: Float = 25f
        var fov: Float = 75f
        /** Field of view added by the speed. */
        var fovBoost: Float = 12f
        /** Camera shake (0: none). */
        var shake: Float = 0.3f

        fun validate() {
            distance = distance.coerceIn(0.5f, 10f)
            height = height.coerceIn(-2f, 3f)
            minDistance = minDistance.coerceIn(1f, 40f)
            maxDistance = maxDistance.coerceIn(minDistance, 120f)
            follow = follow.coerceIn(0f, 1f)
            wallMargin = wallMargin.coerceIn(0.3f, 10f)
            lookAhead = lookAhead.coerceIn(0f, 60f)
            smoothing = smoothing.coerceIn(0.5f, 30f)
            positionSmoothing = positionSmoothing.coerceIn(0.5f, 30f)
            roll = roll.coerceIn(0f, 4f)
            maxRoll = maxRoll.coerceIn(0f, 60f)
            fov = fov.coerceIn(30f, 120f)
            fovBoost = fovBoost.coerceIn(0f, 40f)
            shake = shake.coerceIn(0f, 3f)
        }
    }

    class Visual {
        /** How far ahead the tunnel is drawn, in blocks (at most 80 % of the render distance). */
        var viewDistance: Float = 160f
        /** Faces around the tunnel. */
        var sides: Int = 40
        /** Blocks between two rings of the tunnel. */
        var ringSpacing: Float = 2f
        /** Length of the wall texture along the tunnel, in blocks. */
        var textureLength: Float = 28f
        /** Turns per second of the walls' swirl. */
        var swirlSpeed: Float = 0.04f
        /** Twist of the swirl along the tunnel. */
        var twist: Float = 0.6f
        /** Light streaks rushing past. */
        var streaks: Int = 90
        var brightness: Float = 1f
        /** Second, glowing layer of the walls, scrolling faster (depth). */
        var glowLayer: Boolean = true
        /** Light at the end of the tunnel. */
        var endGlow: Boolean = true
        /** Where the walls start and end fading into the dark, in blocks. */
        var fadeStart: Float = 40f
        var fadeEnd: Float = 150f
        var wallColor: String = "#C8B8FF"
        var glowColor: String = "#6FE8FF"
        var streakColor: String = "#E8F4FF"
        var endColor: String = "#B8FFE0"
        var fogColor: String = "#08051A"

        fun validate() {
            viewDistance = viewDistance.coerceIn(32f, 512f)
            sides = sides.coerceIn(8, 96)
            ringSpacing = ringSpacing.coerceIn(0.5f, 8f)
            textureLength = textureLength.coerceIn(4f, 200f)
            swirlSpeed = swirlSpeed.coerceIn(-2f, 2f)
            twist = twist.coerceIn(-4f, 4f)
            streaks = streaks.coerceIn(0, 600)
            brightness = brightness.coerceIn(0.1f, 3f)
            fadeStart = fadeStart.coerceIn(0f, 500f)
            fadeEnd = fadeEnd.coerceIn(fadeStart + 1f, 600f)
        }
    }

    class Rider {
        /** The player on the Pokémon's back. */
        var showPlayer: Boolean = true
        /** Where each model's centre and seat are (tools/ultra_warp_envelope.py). */
        var sun = RiderModel(0f, 2.70f, -1.36f, 4.24f, -0.3f)
        var moon = RiderModel(0f, 3.78f, 0.06f, 4.69f, 0f)
        /**
         * Necrozma when it passes by: its size (1: as in the world, where Ultra Necrozma
         * is 24 blocks wide) and its model's centre (tools/ultra_warp_envelope.py).
         */
        var necrozmaScale: Float = 0.45f
        var necrozma = RiderModel(0f, 5.39f, -3.85f, 0f, 0f)
        /** Tilts while steering, in degrees: sideways bank, nose up or down, turn towards the move. */
        var maxBank: Float = 22f
        var maxPitch: Float = 12f
        var maxYaw: Float = 8f
        /** Slow up-and-down floating, in blocks. */
        var bob: Float = 0.25f

        fun validate() {
            sun.validate()
            moon.validate()
            maxBank = maxBank.coerceIn(0f, 45f)
            // The server's room for the Pokémon counts tilts of 15 degrees at most.
            maxPitch = maxPitch.coerceIn(0f, 15f)
            maxYaw = maxYaw.coerceIn(0f, 15f)
            bob = bob.coerceIn(0f, 1f)
        }
    }

    /**
     * A model's centre (the middle of its bounds, the point that is steered) and the
     * player's seat (their hips), in blocks from the model's origin at full size.
     */
    class RiderModel(
        var centerX: Float = 0f,
        var centerY: Float = 3f,
        var centerZ: Float = 0f,
        var seatY: Float = 4f,
        var seatZ: Float = 0f,
    ) {
        fun validate() {
            centerX = centerX.coerceIn(-20f, 20f)
            centerY = centerY.coerceIn(-20f, 20f)
            centerZ = centerZ.coerceIn(-20f, 20f)
            seatY = seatY.coerceIn(-20f, 20f)
            seatZ = seatZ.coerceIn(-20f, 20f)
        }
    }

    /**
     * Steering: Minecraft's movement keys (as bound in the options) and the arrow keys.
     * Forward = up, back = down, left and right = sideways. Sprint (boost) and jump
     * (dodge) are read and sent, with no effect yet.
     */
    class Controls {
        var arrowKeys: Boolean = true
        var invertVertical: Boolean = false
    }

    class Dev {
        /** No HUD during the journey. */
        var hideHud: Boolean = true
        /** Seed, distance, speed and frame rate on screen. */
        var debugInfo: Boolean = false
        var showTunnel: Boolean = true
        var showRider: Boolean = true
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = Paths.get("config", "generations", "core", "ultra_space", "ultra_warp_ride_client.json")

    /** Version 2: camera distances in Pokémon sizes, model centres and seats (phase 2.2). */
    private const val VERSION = 2

    var current = Settings().also { it.validate(); it.version = VERSION }; private set

    fun load(): Settings {
        val file = file
        current = try {
            if (Files.exists(file)) gson.fromJson(Files.readString(file), Settings::class.java) ?: Settings() else Settings().also { it.version = VERSION }
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Warp Ride: could not read {} ({}), default values used", file, e.message)
            return Settings().also { it.validate(); it.version = VERSION; current = it }
        }.also { it.validate() }
        // Older files: the camera and the riders changed meaning, they start again from the defaults.
        if (current.version < VERSION) {
            current.camera = Camera()
            current.rider = Rider()
            current.version = VERSION
            GenerationsCore.LOGGER.info("Ultra Warp Ride: {} brought up to date (camera and rider settings reset)", file)
        }
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(current))
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Ultra Warp Ride: could not write {}", file, e)
        }
        return current
    }

    /** "#RRGGBB" as 0xRRGGBB; white when it cannot be read. */
    fun rgb(text: String): Int = text.removePrefix("#").toIntOrNull(16)?.and(0xFFFFFF) ?: 0xFFFFFF
}
