package generations.gg.generations.core.generationscore.common.world.ultraspace

import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpRideSettings
import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpRideState
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * One player's Ultra Warp Ride, kept by the logical server (the integrated server
 * in solo, the dedicated server in multiplayer: the same code). It lives in memory
 * only: the player stays where they were (the journey is drawn by their client) and
 * nothing is taken from them, so there is nothing to save, and a restart frees every
 * place.
 *
 * Stages: TEST before the journey starts (and for simulated sessions), RIDE in the
 * tunnel. Later phases add theirs, driven by tick() and closed by onEnd().
 */
class UltraWarpSession(
    /** The player, or a random id for a simulated session. */
    val player: UUID,
    val variant: UltraWarpVariant,
    val dimension: ResourceKey<Level>,
    /** The altar, or where the player stood for a session started by command. */
    val altar: BlockPos,
    /** Server game time at the start. */
    val startedAt: Long,
    val source: Source,
) {
    enum class Source {
        /** "Start the journey" on a Celestial Altar. */
        ALTAR,
        /** /ultrawarp test start: no altar nor Pokémon needed. */
        COMMAND,
        /** /ultrawarp test simulate: takes a place without a player, to test the limit alone. */
        SIMULATED,
    }

    enum class Stage {
        /** Before the journey (and simulated sessions): nothing happens. */
        TEST,
        /** In the tunnel: the player's client draws it, the server keeps time, energy and collisions. */
        RIDE,
        /** Energy at 0: the player has lost control, the journey ends with their death. */
        DEFEAT,
        /** Gone into a portal: the trip through it, then the journey ends with success. */
        CAPTURE,
    }

    enum class EndReason {
        /** The player ended the journey (or an operator did, or its test duration ran out). */
        FINISHED,
        /** Interrupted: the player died. */
        CANCELLED,
        /** Lost: the energy ran out, the player died of it. */
        DEFEATED,
        /** Through a portal: the journey reached its end. */
        SUCCESS,
        DISCONNECTED,
        SERVER_STOPPING,
    }

    var stage: Stage = Stage.TEST
        private set

    /** The tunnel's seed: the same on the server and the client. */
    var seed: Long = 0
        private set

    /** Server game time when the ride began: the distance travelled is speed × the time since. */
    var rideStart: Long = 0
        private set

    var settings: UltraWarpRideSettings? = null
        private set

    /** Look of the Pokémon ridden (the player's own: shiny...), empty when started by command. */
    var aspects: Set<String> = emptySet()
        private set

    /** The journey as the server keeps it (distance, energy, objects taken), from beginRide. */
    var ride: UltraWarpRideState? = null
        private set

    val simulated: Boolean get() = source == Source.SIMULATED

    fun beginRide(seed: Long, start: Long, settings: UltraWarpRideSettings, aspects: Set<String>) {
        this.seed = seed
        this.rideStart = start
        this.settings = settings
        this.aspects = aspects
        this.ride = UltraWarpRideState(settings, seed)
        stage = Stage.RIDE
    }

    /** This journey's own id (results, logs). */
    val journey: UUID = UUID.randomUUID()

    /** Taken in by a portal: the player no longer steers, nothing hurts any more. */
    fun beginCapture() {
        stage = Stage.CAPTURE
    }

    /** The energy ran out: the journey is lost, the player no longer steers. */
    fun beginDefeat() {
        stage = Stage.DEFEAT
    }

    /** Each second while the session lasts. Nothing to do yet: the client draws the ride. */
    fun tick() {}

    /** Once, when the session ends, whatever the reason. Nothing to undo: the player never moved. */
    fun onEnd(reason: EndReason) {}
}
