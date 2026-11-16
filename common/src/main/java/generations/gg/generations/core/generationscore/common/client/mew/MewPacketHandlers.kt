package generations.gg.generations.core.generationscore.common.client.mew

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import generations.gg.generations.core.generationscore.common.network.packets.mew.S2CMewGamePacket
import generations.gg.generations.core.generationscore.common.network.packets.mew.S2CMewManifestPacket
import net.minecraft.client.Minecraft

/** The Mew packets' client side (the packets themselves hold no client class). */
object MewPacketHandlers {
    object Manifest : ClientNetworkPacketHandler<S2CMewManifestPacket> {
        override fun handle(packet: S2CMewManifestPacket, client: Minecraft) {
            client.execute { MewClient.manifest(packet) }
        }
    }

    object Game : ClientNetworkPacketHandler<S2CMewGamePacket> {
        override fun handle(packet: S2CMewGamePacket, client: Minecraft) {
            client.execute { MewClient.game(packet) }
        }
    }
}
