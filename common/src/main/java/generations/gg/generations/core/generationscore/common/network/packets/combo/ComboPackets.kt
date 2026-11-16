package generations.gg.generations.core.generationscore.common.network.packets.combo

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * The player's Catch Combo (world/catchcombo/CatchCombo), sent to that player only when
 * it changes (and at login): what the HUD shows. species is the combo key ("" = none).
 */
class S2CCatchComboPacket(
    val enabled: Boolean,
    val species: String,
    val count: Int,
    val shinyMultiplier: Float,
    val perfectIvs: Int,
    val tierThreshold: Int,
    val nextThreshold: Int,
    val nextShinyMultiplier: Float,
    val nextPerfectIvs: Int,
    val event: Event,
    /** Why it was lost (generations_core.catch_combo.break.<reason>), "" otherwise. */
    val reason: String,
) : NetworkPacket<S2CCatchComboPacket> {
    enum class Event { SYNC, UP, TIER, BREAK, RESET }

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(enabled)
        buffer.writeUtf(species)
        buffer.writeVarInt(count)
        buffer.writeFloat(shinyMultiplier)
        buffer.writeVarInt(perfectIvs)
        buffer.writeVarInt(tierThreshold)
        buffer.writeVarInt(nextThreshold)
        buffer.writeFloat(nextShinyMultiplier)
        buffer.writeVarInt(nextPerfectIvs)
        buffer.writeEnum(event)
        buffer.writeUtf(reason)
    }

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("catch_combo")

        fun decode(buf: RegistryFriendlyByteBuf) = S2CCatchComboPacket(
            buf.readBoolean(), buf.readUtf(256), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readFloat(), buf.readVarInt(), buf.readEnum(Event::class.java), buf.readUtf(64),
        )
    }
}

/** The inventory's charm case button: the server opens the case (world/charm/CharmCase). */
class C2SOpenCharmCasePacket : NetworkPacket<C2SOpenCharmCasePacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {}

    companion object {
        val ID: ResourceLocation = GenerationsCore.id("open_charm_case")

        @Suppress("UNUSED_PARAMETER")
        fun decode(buf: RegistryFriendlyByteBuf) = C2SOpenCharmCasePacket()
    }
}

object ComboPacketHandlers {
    object Combo : ClientNetworkPacketHandler<S2CCatchComboPacket> {
        override fun handle(packet: S2CCatchComboPacket, client: Minecraft) {
            client.execute { generations.gg.generations.core.generationscore.common.client.catchcombo.CatchComboHud.update(packet) }
        }
    }

    object OpenCase : ServerNetworkPacketHandler<C2SOpenCharmCasePacket> {
        override fun handle(packet: C2SOpenCharmCasePacket, server: MinecraftServer, player: ServerPlayer) {
            server.execute { generations.gg.generations.core.generationscore.common.world.charm.CharmCase.open(player) }
        }
    }
}
