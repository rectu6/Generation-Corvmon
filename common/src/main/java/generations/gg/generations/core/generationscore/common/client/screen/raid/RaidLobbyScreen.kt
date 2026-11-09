package generations.gg.generations.core.generationscore.common.client.screen.raid

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.Species
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdButton
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdGui
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdModel
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import generations.gg.generations.core.generationscore.common.network.packets.raid.C2SRaidLobbyPacket
import generations.gg.generations.core.generationscore.common.network.packets.raid.RaidLobbyMember
import generations.gg.generations.core.generationscore.common.network.packets.raid.RaidPick
import generations.gg.generations.core.generationscore.common.network.packets.raid.S2CRaidLobbyPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * Raid lobby, as in Sword and Shield, in HD: black silhouette of the Pokémon, tier,
 * types, end rules, and the four participants (players, then NPCs for the places
 * left) with the Pokémon each one brings and whether they are ready. The raid
 * starts when everyone is ready or when the time runs out. Escape leaves the lobby.
 */
class RaidLobbyScreen(lobby: S2CRaidLobbyPacket) : Screen(Component.translatable("generations_core.raid.lobby.title")) {
    var lobby: S2CRaidLobbyPacket = lobby
        private set
    private val silhouetteState = FloatingState()
    private val states = List(4) { FloatingState() }
    private var left = 0f
    private var top = 0f

    private val species: Species? get() = speciesOf(lobby.species)
    val selected: UUID? get() = lobby.selected
    val pick: RaidPick? get() = lobby.picks.firstOrNull { it.uuid == lobby.selected }

    /** New lobby data from the server: the screen stays open. */
    fun update(packet: S2CRaidLobbyPacket) {
        lobby = packet
        rebuildWidgets()
    }

    fun select(uuid: UUID) {
        C2SRaidLobbyPacket(lobby.pos, C2SRaidLobbyPacket.Action.SELECT, uuid).sendToServer()
    }

    override fun init() {
        left = ((width - WIDTH) / 2).toFloat()
        top = ((height - HEIGHT) / 2).toFloat()
        val bx = (left + WIDTH - 172).toInt()
        val by = (top + HEIGHT).toInt()
        val ready = if (lobby.ready) "generations_core.raid.lobby.not_ready" else "generations_core.raid.lobby.ready"
        addRenderableWidget(HdButton(bx, by - 74, 156, 20, Component.translatable(ready)) {
            C2SRaidLobbyPacket(lobby.pos, C2SRaidLobbyPacket.Action.READY, null).sendToServer()
        }).active = lobby.selected != null
        addRenderableWidget(HdButton(bx, by - 50, 156, 20, Component.translatable("generations_core.raid.lobby.switch")) {
            Minecraft.getInstance().setScreen(RaidPickerScreen(this))
        }).active = !lobby.ready
        addRenderableWidget(HdButton(bx, by - 26, 156, 20, Component.translatable("generations_core.raid.lobby.quit")) { onClose() })
    }

    private fun speciesOf(id: String): Species? = ResourceLocation.tryParse(id)?.let(PokemonSpecies::getByIdentifier)

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        HdGui.draw(graphics, HdGui.LOBBY_BG, left, top, WIDTH.toFloat(), HEIGHT.toFloat())
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)

        // Title and tier.
        HdText.draw(graphics, title.string.uppercase(), left + 16f, top + 12f, 9f, WHITE, bold = true, shadow = true)
        HdGui.stars(graphics, lobby.stars, left + 15f, top + 25f, 15f, if (lobby.stars >= 6) GOLD else WHITE, spacing = 2f)

        // Types.
        var tx = left + 16f
        listOfNotNull(species?.primaryType, species?.secondaryType).forEach { type ->
            val name = type.displayName.string.uppercase()
            val w = HdText.width(name, 7.5f, true) + 14f
            HdGui.bar(graphics, tx, top + 46f, w, 12f, 0xFF000000.toInt() or type.hue)
            HdText.draw(graphics, name, tx + w / 2f, top + 48f, 7.5f, WHITE, bold = true, align = HdText.Align.CENTER, outline = 0x70000000)
            tx += w + 4f
        }

        // The Pokémon in black: its silhouette only, over a red glow.
        HdGui.draw(graphics, HdGui.GLOW, left + 15f, top + 50f, 200f, 150f, 0x8CFF6A88.toInt())
        species?.let {
            HdModel.profile(graphics, RenderablePokemon(it, lobby.aspects.toSet()), silhouetteState, left + 25f, top + 62f, 180f, 124f, 2.2f, 325f, -10f, partialTick, silhouette = true)
        }

        // End rules.
        val rx = left + 14f
        val ry = top + HEIGHT - 52f
        HdGui.panel(graphics, HdGui.PANEL_DARK, rx, ry, 196f, 42f, corner = 6f)
        HdText.draw(graphics, Component.translatable("generations_core.raid.lobby.rules_title"), rx + 7f, ry + 5f, 8f, 0xFFFF8098.toInt(), bold = true)
        HdText.wrap(Component.translatable("generations_core.raid.lobby.rules", lobby.maxFaints, lobby.maxTurns).string, 182f, 7.5f)
            .take(2).forEachIndexed { i, line -> HdText.draw(graphics, line, rx + 7f, ry + 16f + i * 10f, 7.5f, WHITE) }

        // The four participants.
        val px = left + WIDTH - 186f
        val players = lobby.members.count { it.kind != RaidLobbyMember.NPC }
        HdText.draw(graphics, Component.translatable("generations_core.raid.lobby.players", players, 4), px, top + 13f, 9f, DARK, bold = true)
        HdText.draw(graphics, Component.translatable("generations_core.raid.lobby.starts_in", lobby.secondsLeft), px + 176f, top + 14f, 7.5f, GREY, align = HdText.Align.RIGHT)
        lobby.members.take(4).forEachIndexed { i, member ->
            val y = top + 28f + i * 30f
            HdGui.panel(graphics, HdGui.PANEL_LIGHT, px, y, 176f, 26f, corner = 6f)
            if (member.kind == RaidLobbyMember.YOU) HdGui.panel(graphics, HdGui.PANEL_OUTLINE, px, y, 176f, 26f, RED, corner = 6f)
            HdGui.draw(graphics, HdGui.PORTRAIT_RING, px + 3f, y + 2f, 22f, 22f)
            speciesOf(member.species)?.let { s ->
                states[i].currentAspects = member.aspects.toSet()
                HdModel.portrait(graphics, s, states[i], px + 4f, y + 3f, 20f, false, partialTick)
            }
            HdText.draw(graphics, member.name, px + 30f, y + 4f, 8.5f, if (member.kind == RaidLobbyMember.YOU) RED else DARK, bold = true)
            HdText.draw(graphics, HdText.fit(member.label, 100f, 7.5f), px + 30f, y + 14.5f, 7.5f, GREY)
            val status = when {
                member.kind == RaidLobbyMember.NPC -> Component.translatable("generations_core.raid.lobby.npc") to GREY
                member.ready -> Component.translatable("generations_core.raid.lobby.is_ready") to GREEN
                else -> Component.translatable("generations_core.raid.lobby.waiting") to ORANGE
            }
            HdText.draw(graphics, status.first, px + 170f, y + 4.5f, 7.5f, status.second, bold = true, align = HdText.Align.RIGHT)
        }
        if (lobby.members.any { it.kind == RaidLobbyMember.NPC }) {
            HdText.draw(graphics, Component.translatable("generations_core.raid.lobby.npcs"), px, top + 28f + 4 * 30f - 1f, 7f, GREY)
        }
    }

    // Escape leaves the lobby.
    override fun onClose() {
        C2SRaidLobbyPacket(lobby.pos, C2SRaidLobbyPacket.Action.QUIT, null).sendToServer()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        private const val WIDTH = 440
        private const val HEIGHT = 248
        const val RED = 0xFFE8304A.toInt()
        const val LIGHT = 0xFFEEF2F5.toInt()
        const val DARK = 0xFF202030.toInt()
        const val GOLD = 0xFFFFD24A.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val GREY = 0xFF6A6A7A.toInt()
        const val GREEN = 0xFF1E9A44.toInt()
        const val ORANGE = 0xFFD07A10.toInt()

        /** Opens the lobby, or refreshes it (and the picker opened from it). */
        fun show(packet: S2CRaidLobbyPacket) {
            val minecraft = Minecraft.getInstance()
            when (val screen = minecraft.screen) {
                is RaidLobbyScreen -> if (screen.lobby.pos == packet.pos) screen.update(packet) else minecraft.setScreen(RaidLobbyScreen(packet))
                is RaidPickerScreen -> screen.lobbyScreen.takeIf { it.lobby.pos == packet.pos }?.update(packet) ?: minecraft.setScreen(RaidLobbyScreen(packet))
                else -> minecraft.setScreen(RaidLobbyScreen(packet))
            }
        }
    }
}
