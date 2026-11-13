package generations.gg.generations.core.generationscore.common.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.pokemon.update.PokemonUpdatePacketHandler
import com.cobblemon.mod.common.client.net.spawn.SpawnExtraDataEntityHandler
import com.cobblemon.mod.common.net.PacketRegisterInfo
import com.cobblemon.mod.common.net.messages.client.pokemon.update.TeraTypeUpdatePacket
import com.cobblemon.mod.common.util.server
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.packets.*
import generations.gg.generations.core.generationscore.common.network.packets.shop.*
import generations.gg.generations.core.generationscore.common.network.packets.pokewand.*
import generations.gg.generations.core.generationscore.common.network.packets.raid.*
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.*
import generations.gg.generations.core.generationscore.common.network.packets.statue.S2COpenStatueEditorScreenHandler
import generations.gg.generations.core.generationscore.common.network.packets.statue.S2COpenStatueEditorScreenPacket
import generations.gg.generations.core.generationscore.common.network.packets.statue.UpdateStatueHandler
import generations.gg.generations.core.generationscore.common.network.packets.statue.UpdateStatuePacket
import generations.gg.generations.core.generationscore.common.network.spawn.SpawnStatuePacket
import net.minecraft.server.level.ServerPlayer

object GenerationsNetwork {
    fun ServerPlayer.sendPacket(packet: NetworkPacket<*>) {
        sendPacketToPlayer(this, packet)
    }
    fun sendToServer(packet: NetworkPacket<*>) {
        GenerationsCore.implementation.networkManager.sendToServer(packet)
    }
    fun sendToAllPlayers(packet: NetworkPacket<*>) = sendPacketToPlayers(server()!!.playerList.players, packet)
    fun sendPacketToPlayers(players: Iterable<ServerPlayer>, packet: NetworkPacket<*>) = players.forEach { sendPacketToPlayer(it, packet) }

    val s2cPayloads = generateS2CPacketInfoList()
    val c2sPayloads = generateC2SPacketInfoList()

    private fun generateS2CPacketInfoList(): List<PacketRegisterInfo<*>> {
        val list = mutableListOf<PacketRegisterInfo<*>>()

        list.add(PacketRegisterInfo(BattleConditionsPacket.ID, BattleConditionsPacket::decode, BattleConditionsPacketHandler))
        list.add(PacketRegisterInfo(GensInteractPokemonUIPacket.ID, GensInteractPokemonUIPacket::decode, GensInteractPokemonUIPacketHandler))
        list.add(PacketRegisterInfo(S2COpenMailEditScreenPacket.ID, S2COpenMailEditScreenPacket::decode, S2COpenMailEditScreenPacketHandler))
        list.add(PacketRegisterInfo(S2COpenMailPacket.ID, S2COpenMailPacket::decode, S2COpenMailPacketHandler))
        list.add(PacketRegisterInfo(S2CUnlockReloadPacket.ID, S2CUnlockReloadPacket::decode, UnlockReloadPacketHandler))
        list.add(PacketRegisterInfo(S2COpenStatueEditorScreenPacket.ID, S2COpenStatueEditorScreenPacket::decode, S2COpenStatueEditorScreenHandler))
//        list.add(PacketRegisterInfo(ShopRegistrySyncPacket.ID, ShopRegistrySyncPacket::decode, DataRegistrySyncPacketHandler()))
//        list.add(PacketRegisterInfo(ShopPresetRegistrySyncPacket.ID, ShopPresetRegistrySyncPacket::decode, DataRegistrySyncPacketHandler()))
        list.add(PacketRegisterInfo(S2COpenShopPacket.ID, S2COpenShopPacket::decode, S2COpenShopHandler))
        list.add(PacketRegisterInfo(S2CSyncPlayerMoneyPacket.ID, ::S2CSyncPlayerMoneyPacket, S2CSyncPlayerMoneyHandler))
        list.add(PacketRegisterInfo(SpawnStatuePacket.ID, SpawnStatuePacket::decode, SpawnExtraDataEntityHandler()))
        list.add(PacketRegisterInfo(S2CPlaySoundPacket.ID, S2CPlaySoundPacket::decode, S2CPlaySoundHandler))
        list.add(PacketRegisterInfo(S2CPokeWandPartyPacket.ID, S2CPokeWandPartyPacket::decode, S2CPokeWandPartyHandler))
        list.add(PacketRegisterInfo(S2CRaidLobbyPacket.ID, S2CRaidLobbyPacket::decode, RaidPacketHandlers.Lobby))
        list.add(PacketRegisterInfo(S2CRaidResultPacket.ID, S2CRaidResultPacket::decode, RaidPacketHandlers.Result))
        list.add(PacketRegisterInfo(S2CRaidHudPacket.ID, S2CRaidHudPacket::decode, RaidPacketHandlers.Hud))
        list.add(PacketRegisterInfo(S2CRaidLobbyClosePacket.ID, S2CRaidLobbyClosePacket::decode, RaidPacketHandlers.LobbyClose))
        list.add(PacketRegisterInfo(S2CRaidCheerPacket.ID, S2CRaidCheerPacket::decode, RaidPacketHandlers.Cheer))
        list.add(PacketRegisterInfo(S2CUltraWarpPacket.ID, S2CUltraWarpPacket::decode, UltraWarpPacketHandlers.Menu))
        list.add(PacketRegisterInfo(S2CUltraWarpRideStartPacket.ID, S2CUltraWarpRideStartPacket::decode, UltraWarpPacketHandlers.RideStart))
        list.add(PacketRegisterInfo(S2CUltraWarpRideStopPacket.ID, S2CUltraWarpRideStopPacket::decode, UltraWarpPacketHandlers.RideStop))
        list.add(PacketRegisterInfo(S2CUltraWarpRideUpdatePacket.ID, S2CUltraWarpRideUpdatePacket::decode, UltraWarpPacketHandlers.RideUpdate))
        list.add(PacketRegisterInfo(S2CUltraWarpRideEventPacket.ID, S2CUltraWarpRideEventPacket::decode, UltraWarpPacketHandlers.RideEvent))
        list.add(PacketRegisterInfo(S2CUltraWarpPortalPacket.ID, S2CUltraWarpPortalPacket::decode, UltraWarpPacketHandlers.Portal))
        list.add(PacketRegisterInfo(S2CUltraWarpTransferPacket.ID, S2CUltraWarpTransferPacket::decode, UltraWarpPacketHandlers.Transfer))
        list.add(PacketRegisterInfo(S2CUltraWeatherPacket.ID, S2CUltraWeatherPacket::decode, UltraWarpPacketHandlers.Weather))
        list.add(PacketRegisterInfo(S2CUltraHazardPacket.ID, S2CUltraHazardPacket::decode, UltraWarpPacketHandlers.Hazard))
        list.add(PacketRegisterInfo(S2CUltraRiftPacket.ID, S2CUltraRiftPacket::decode, UltraWarpPacketHandlers.Rift))
        // TODO: remove TeraTypeUpdatePacket when its registered in Cobblemon's own network
//        list.add(PacketRegisterInfo(TeraTypeUpdatePacket.ID, TeraTypeUpdatePacket::decode, PokemonUpdatePacketHandler()))

        return list;
    }

    private fun generateC2SPacketInfoList(): MutableList<PacketRegisterInfo<*>> {
        val list = mutableListOf<PacketRegisterInfo<*>>()

        list.add(PacketRegisterInfo(GensInteractPokemonPacket.ID, GensInteractPokemonPacket::decode, GensInteractPokemonHandler))
        list.add(PacketRegisterInfo(C2STogglePacket.ID, C2STogglePacket::decode, C2SToggleHandler))
        list.add(PacketRegisterInfo(C2SEditMailPacket.ID, C2SEditMailPacket::decode, C2SEditMailHandler))
        list.add(PacketRegisterInfo(C2SPokeWandActionPacket.ID, C2SPokeWandActionPacket::decode, C2SPokeWandActionHandler))
        list.add(PacketRegisterInfo(C2SRaidLobbyPacket.ID, C2SRaidLobbyPacket::decode, RaidPacketHandlers.LobbyAction))
        list.add(PacketRegisterInfo(C2SRaidResultPacket.ID, C2SRaidResultPacket::decode, RaidPacketHandlers.ResultAction))
        list.add(PacketRegisterInfo(C2SRaidCheerPacket.ID, C2SRaidCheerPacket::decode, RaidPacketHandlers.CheerAction))
        list.add(PacketRegisterInfo(C2SUltraWarpActionPacket.ID, C2SUltraWarpActionPacket::decode, UltraWarpPacketHandlers.Action))
        list.add(PacketRegisterInfo(C2SUltraWarpRideStatePacket.ID, C2SUltraWarpRideStatePacket::decode, UltraWarpPacketHandlers.RideState))
        list.add(PacketRegisterInfo(C2SUltraReturnPacket.ID, C2SUltraReturnPacket::decode, UltraWarpPacketHandlers.Return))
        list.add(PacketRegisterInfo(C2SShopItemPacket.ID, C2SShopItemPacket::decode, C2SShopItemHandler))
        list.add(PacketRegisterInfo(HeadPatPacket.ID, ::HeadPatPacket, HeadPatPacketHandler))

        list.add(PacketRegisterInfo(UpdateStatuePacket.PROPERTIES_ID, UpdateStatuePacket.Companion::propertiesDecode, UpdateStatueHandler.Properties))
        list.add(PacketRegisterInfo(UpdateStatuePacket.LABEL_ID, UpdateStatuePacket.Companion::labelDecode, UpdateStatueHandler.Label))
        list.add(PacketRegisterInfo(UpdateStatuePacket.SCALE_ID, UpdateStatuePacket.Companion::scaleDecode, UpdateStatueHandler.Scale))
        list.add(PacketRegisterInfo(UpdateStatuePacket.POSE_TYPE_ID, UpdateStatuePacket.Companion::poseTypeDecode, UpdateStatueHandler.PoseType))
        list.add(PacketRegisterInfo(UpdateStatuePacket.STATIC_TOGGLE_ID, UpdateStatuePacket.Companion::staticToggleDecode, UpdateStatueHandler.StaticToggle))
        list.add(PacketRegisterInfo(UpdateStatuePacket.STATIC_PARTIAL_ID, UpdateStatuePacket.Companion::staticPartialDecode, UpdateStatueHandler.StaticPartial))
        list.add(PacketRegisterInfo(UpdateStatuePacket.STATIC_AGE_ID, UpdateStatuePacket.Companion::staticAgeDecode, UpdateStatueHandler.StaticAge))
        list.add(PacketRegisterInfo(UpdateStatuePacket.INTERACTABLE_ID, UpdateStatuePacket.Companion::interactableDecode, UpdateStatueHandler.Interactable))
        list.add(PacketRegisterInfo(UpdateStatuePacket.MATERIAL_ID, UpdateStatuePacket.Companion::materialDecode, UpdateStatueHandler.Material))
        list.add(PacketRegisterInfo(UpdateStatuePacket.ORIENTATION_ID, UpdateStatuePacket.Companion::orientationDecode, UpdateStatueHandler.Orientation))
        return list
    }


    fun sendPacketToPlayer(player: ServerPlayer, packet: NetworkPacket<*>) {
        GenerationsCore.implementation.networkManager.sendPacketToPlayer(player, packet)
    }
}