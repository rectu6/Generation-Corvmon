package generations.gg.generations.core.generationscore.common.network.packets.boss

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import generations.gg.generations.core.generationscore.common.client.boss.BossRewardsEditorScreen
import generations.gg.generations.core.generationscore.common.client.boss.BossVictoryScreen
import generations.gg.generations.core.generationscore.common.commands.BossCommands
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object BossPacketHandlers {
    object Victory : ClientNetworkPacketHandler<S2CBossVictoryPacket> {
        override fun handle(packet: S2CBossVictoryPacket, client: Minecraft) {
            client.execute { client.setScreen(BossVictoryScreen(packet)) }
        }
    }

    object Editor : ClientNetworkPacketHandler<S2CBossRewardsEditorPacket> {
        override fun handle(packet: S2CBossRewardsEditorPacket, client: Minecraft) {
            client.execute { client.setScreen(BossRewardsEditorScreen(packet)) }
        }
    }

    object Save : ServerNetworkPacketHandler<C2SBossRewardsSavePacket> {
        override fun handle(packet: C2SBossRewardsSavePacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute { BossCommands.saveRewards(player, packet) }
        }
    }
}
