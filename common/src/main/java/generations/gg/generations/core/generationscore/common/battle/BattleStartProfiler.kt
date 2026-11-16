package generations.gg.generations.core.generationscore.common.battle

import com.cobblemon.mod.common.battles.BattleStartResult
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import generations.gg.generations.core.generationscore.common.GenerationsCore

/**
 * Time of BattleRegistry.startBattle on the server thread, where Cobblemon starts the
 * battle in Showdown (GraalJS, synchronously): the first battle after a start runs
 * Showdown's code cold. Logged when client.profileFirstUse is on (main.json), to decide
 * whether a warm-up battle is worth it (COMBO-FUITE-PERF-PLAN.md, 2.1).
 */
object BattleStartProfiler {
    private val started = ThreadLocal<Long>()
    private var count = 0

    private val enabled: Boolean
        get() = runCatching { GenerationsCore.CONFIG.client.profileFirstUse }.getOrDefault(false)

    @JvmStatic
    fun start() {
        if (enabled) started.set(System.nanoTime())
    }

    @JvmStatic
    fun stop(result: BattleStartResult?) {
        val start = started.get() ?: return
        started.remove()
        count++
        GenerationsCore.LOGGER.info(
            "[first-use] server: BattleRegistry.startBattle took {} ms (battle {} since the start{})",
            "%.1f".format((System.nanoTime() - start) / 1_000_000.0), count, if (result is SuccessfulBattleStart) "" else ", not started",
        )
    }
}
