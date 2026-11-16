package generations.gg.generations.core.generationscore.common.client.cinematic

/**
 * A cinematic's clock on the client (pure, tested off-game). It starts from the server's tick
 * (startSeconds: negative while the scene's second 0 is still to come, positive for a late
 * client), then follows real time, independent of the frame rate and of the ticks' small
 * hitches. Frozen while the solo game is paused; a long freeze counts for one second at most.
 */
class CinematicClock(startSeconds: Double) {
    var seconds: Double = startSeconds
        private set

    private var last = Long.MIN_VALUE

    /** This frame's time: nanos is System.nanoTime(). */
    fun frame(nanos: Long, paused: Boolean): Double {
        if (last != Long.MIN_VALUE && !paused) {
            val dt = (nanos - last) / 1.0e9
            if (dt > 0.0) seconds += minOf(dt, MAX_STEP)
        }
        last = nanos
        return seconds
    }

    companion object {
        const val MAX_STEP = 1.0

        /** The scene's time when the start packet arrives: game ticks since its second 0. */
        fun startSeconds(clientGameTime: Long, startGameTime: Long): Double = (clientGameTime - startGameTime) / 20.0
    }
}
