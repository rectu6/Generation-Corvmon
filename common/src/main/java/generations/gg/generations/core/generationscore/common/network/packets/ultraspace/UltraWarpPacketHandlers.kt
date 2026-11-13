package generations.gg.generations.core.generationscore.common.network.packets.ultraspace

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import generations.gg.generations.core.generationscore.common.client.screen.ultraspace.UltraWarpRideScreen
import generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient
import generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpTransferClient
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpRide
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object UltraWarpPacketHandlers {
    object Menu : ClientNetworkPacketHandler<S2CUltraWarpPacket> {
        override fun handle(packet: S2CUltraWarpPacket, client: Minecraft) {
            client.execute { UltraWarpRideScreen.show(packet) }
        }
    }

    object RideStart : ClientNetworkPacketHandler<S2CUltraWarpRideStartPacket> {
        override fun handle(packet: S2CUltraWarpRideStartPacket, client: Minecraft) {
            client.execute { UltraWarpRideClient.start(packet) }
        }
    }

    object Portal : ClientNetworkPacketHandler<S2CUltraWarpPortalPacket> {
        override fun handle(packet: S2CUltraWarpPortalPacket, client: Minecraft) {
            client.execute { UltraWarpRideClient.onPortal(packet.portal) }
        }
    }

    object RideStop : ClientNetworkPacketHandler<S2CUltraWarpRideStopPacket> {
        override fun handle(packet: S2CUltraWarpRideStopPacket, client: Minecraft) {
            client.execute { UltraWarpRideClient.stop(packet.reason, packet.outcome) }
        }
    }

    object Transfer : ClientNetworkPacketHandler<S2CUltraWarpTransferPacket> {
        override fun handle(packet: S2CUltraWarpTransferPacket, client: Minecraft) {
            client.execute { UltraWarpTransferClient.onPacket(packet) }
        }
    }

    object RideUpdate : ClientNetworkPacketHandler<S2CUltraWarpRideUpdatePacket> {
        override fun handle(packet: S2CUltraWarpRideUpdatePacket, client: Minecraft) {
            client.execute { UltraWarpRideClient.onUpdate(packet) }
        }
    }

    object RideEvent : ClientNetworkPacketHandler<S2CUltraWarpRideEventPacket> {
        override fun handle(packet: S2CUltraWarpRideEventPacket, client: Minecraft) {
            client.execute { UltraWarpRideClient.onEvent(packet) }
        }
    }

    object RideState : ServerNetworkPacketHandler<C2SUltraWarpRideStatePacket> {
        override fun handle(packet: C2SUltraWarpRideStatePacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute { UltraWarpRide.onState(player, packet) }
        }
    }

    object Action : ServerNetworkPacketHandler<C2SUltraWarpActionPacket> {
        override fun handle(packet: C2SUltraWarpActionPacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute { UltraWarpRide.onAction(player, packet) }
        }
    }

    /** The weather of the player's Ultra world (phase 4.4). */
    object Weather : ClientNetworkPacketHandler<S2CUltraWeatherPacket> {
        override fun handle(packet: S2CUltraWeatherPacket, client: Minecraft) {
            client.execute { generations.gg.generations.core.generationscore.common.client.ultraspace.weather.UltraWeatherClient.onWeather(packet) }
        }
    }

    /** The player's exposure to their world's dangerous weather (the indicator). */
    object Hazard : ClientNetworkPacketHandler<S2CUltraHazardPacket> {
        override fun handle(packet: S2CUltraHazardPacket, client: Minecraft) {
            client.execute { generations.gg.generations.core.generationscore.common.client.ultraspace.weather.UltraWeatherClient.onHazard(packet) }
        }
    }

    /** The rifts of the player's world (phase 6). */
    object Rift : ClientNetworkPacketHandler<S2CUltraRiftPacket> {
        override fun handle(packet: S2CUltraRiftPacket, client: Minecraft) {
            client.execute { generations.gg.generations.core.generationscore.common.client.ultraspace.rift.UltraRiftClient.onPacket(packet) }
        }
    }

    /** "Back to the Overworld" on a Solgaleo's or Lunala's wheel (phase 6). */
    object Return : ServerNetworkPacketHandler<C2SUltraReturnPacket> {
        override fun handle(packet: C2SUltraReturnPacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute { generations.gg.generations.core.generationscore.common.world.ultraspace.rift.UltraReturn.request(player, packet.entity) }
        }
    }
}
