package generations.gg.generations.core.generationscore.common.client.ultraspace

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.client.screen.ultraspace.UltraWarpResultScreen
import generations.gg.generations.core.generationscore.common.client.screen.ultraspace.UltraWarpTransferScreen
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWarpTransferPacket
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpOutcome
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTransfers
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ReceivingLevelScreen
import net.minecraft.network.chat.Component

/**
 * The client's side of a move between the worlds (UltraWarpTransfers on the server):
 * the white screen while the server loads the place, then, once arrived and Minecraft's
 * own loading screen gone, the journey's result screen. Nothing is decided here.
 */
object UltraWarpTransferClient {
    private class Waiting(val world: Component?, val colour: Int, val until: Long, val closable: Long)

    private var waiting: Waiting? = null
    private var result: UltraWarpOutcome? = null

    /** The longest wait shown (the server gives up well before). */
    private const val WAIT_MILLIS = 90_000L
    private const val CLOSABLE_MILLIS = 30_000L

    fun init() {
        PlatformEvents.CLIENT_TICK_POST.subscribe(Priority.NORMAL) { tick() }
        PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe(Priority.NORMAL) {
            waiting = null
            result = null
        }
    }

    /** A journey through a portal ended, its arrival on the way: the white stays. */
    fun begin(outcome: UltraWarpOutcome) {
        show(UltraWarpResultScreen.destinationName(outcome.destination), outcome.color)
    }

    fun onPacket(packet: S2CUltraWarpTransferPacket) {
        when (packet.state) {
            S2CUltraWarpTransferPacket.State.STARTED ->
                show(if (packet.kind == UltraWarpTransfers.Kind.RETURN) null else UltraWarpResultScreen.destinationName(packet.world), packet.color)
            S2CUltraWarpTransferPacket.State.ARRIVED, S2CUltraWarpTransferPacket.State.FAILED -> {
                waiting = null
                val minecraft = Minecraft.getInstance()
                if (minecraft.screen is UltraWarpTransferScreen) minecraft.setScreen(null)
                // A journey's result, shown once in the new world.
                if (packet.kind == UltraWarpTransfers.Kind.ARRIVAL) packet.outcome?.let { result = it }
            }
        }
    }

    private fun show(world: Component?, colour: Int) {
        val now = System.currentTimeMillis()
        waiting = Waiting(world, colour, now + WAIT_MILLIS, now + CLOSABLE_MILLIS)
        open(now)
    }

    private fun open(now: Long) {
        val waiting = waiting ?: return
        val minecraft = Minecraft.getInstance()
        if (minecraft.screen is UltraWarpTransferScreen || minecraft.screen is ReceivingLevelScreen || UltraWarpRideClient.isActive) return
        minecraft.setScreen(UltraWarpTransferScreen(waiting.world, waiting.colour, waiting.closable))
    }

    private fun tick() {
        val minecraft = Minecraft.getInstance()
        if (minecraft.level == null || minecraft.player == null) return
        val now = System.currentTimeMillis()
        waiting?.let { current ->
            if (now > current.until) {
                waiting = null
                if (minecraft.screen is UltraWarpTransferScreen) minecraft.setScreen(null)
            } else if (minecraft.screen == null) {
                // Closed by the player after a long wait: so be it; by something else: back.
                if (now > current.closable) waiting = null else open(now)
            }
            return
        }
        val outcome = result ?: return
        if (minecraft.screen == null) {
            result = null
            minecraft.setScreen(UltraWarpResultScreen(outcome))
        }
    }
}
