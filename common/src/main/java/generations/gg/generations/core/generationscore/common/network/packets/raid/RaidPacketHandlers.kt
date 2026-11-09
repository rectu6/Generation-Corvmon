package generations.gg.generations.core.generationscore.common.network.packets.raid

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import generations.gg.generations.core.generationscore.common.client.screen.raid.RaidCheerScreen
import generations.gg.generations.core.generationscore.common.client.screen.raid.RaidHud
import generations.gg.generations.core.generationscore.common.client.screen.raid.RaidLobbyScreen
import generations.gg.generations.core.generationscore.common.client.screen.raid.RaidPickerScreen
import generations.gg.generations.core.generationscore.common.client.screen.raid.RaidResultScreen
import generations.gg.generations.core.generationscore.common.world.raid.RaidManager
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RaidPacketHandlers {
    object Lobby : ClientNetworkPacketHandler<S2CRaidLobbyPacket> {
        override fun handle(packet: S2CRaidLobbyPacket, client: Minecraft) {
            client.execute { RaidLobbyScreen.show(packet) }
        }
    }

    object LobbyClose : ClientNetworkPacketHandler<S2CRaidLobbyClosePacket> {
        override fun handle(packet: S2CRaidLobbyClosePacket, client: Minecraft) {
            client.execute {
                if (client.screen is RaidLobbyScreen || client.screen is RaidPickerScreen) client.setScreen(null)
            }
        }
    }

    object Hud : ClientNetworkPacketHandler<S2CRaidHudPacket> {
        override fun handle(packet: S2CRaidHudPacket, client: Minecraft) {
            client.execute { RaidHud.update(packet) }
        }
    }

    object Cheer : ClientNetworkPacketHandler<S2CRaidCheerPacket> {
        override fun handle(packet: S2CRaidCheerPacket, client: Minecraft) {
            client.execute { client.setScreen(RaidCheerScreen(client.screen, packet.seconds)) }
        }
    }

    object Result : ClientNetworkPacketHandler<S2CRaidResultPacket> {
        override fun handle(packet: S2CRaidResultPacket, client: Minecraft) {
            client.execute { RaidResultScreen.show(packet) }
        }
    }

    object LobbyAction : ServerNetworkPacketHandler<C2SRaidLobbyPacket> {
        override fun handle(packet: C2SRaidLobbyPacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute { RaidManager.onLobbyAction(player, packet) }
        }
    }

    object CheerAction : ServerNetworkPacketHandler<C2SRaidCheerPacket> {
        override fun handle(packet: C2SRaidCheerPacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute { RaidManager.cheer(player, packet.accept) }
        }
    }

    object ResultAction : ServerNetworkPacketHandler<C2SRaidResultPacket> {
        override fun handle(packet: C2SRaidResultPacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute { RaidManager.onResultAction(player, packet) }
        }
    }
}
