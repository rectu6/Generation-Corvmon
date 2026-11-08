package generations.gg.generations.core.generationscore.common.network.packets.pokewand

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import generations.gg.generations.core.generationscore.common.client.screen.pokewand.PokeWandPartyScreen
import generations.gg.generations.core.generationscore.common.world.pokewand.PokeWand
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object S2CPokeWandPartyHandler : ClientNetworkPacketHandler<S2CPokeWandPartyPacket> {
    override fun handle(packet: S2CPokeWandPartyPacket, client: Minecraft) {
        client.execute { PokeWandPartyScreen.show(packet) }
    }
}

object C2SPokeWandActionHandler : ServerNetworkPacketHandler<C2SPokeWandActionPacket> {
    override fun handle(packet: C2SPokeWandActionPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute { PokeWand.handle(player, packet) }
    }
}
