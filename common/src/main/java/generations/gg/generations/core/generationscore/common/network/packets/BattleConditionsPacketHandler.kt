package generations.gg.generations.core.generationscore.common.network.packets

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import generations.gg.generations.core.generationscore.common.client.BattleConditionsOverlay
import net.minecraft.client.Minecraft

object BattleConditionsPacketHandler: ClientNetworkPacketHandler<BattleConditionsPacket> {
    override fun handle(packet: BattleConditionsPacket, client: Minecraft) {
        client.execute {
            BattleConditionsOverlay.onBattleDataReceived(packet.isActor, packet.conditionsData)
        }
    }
}