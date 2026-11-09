package generations.gg.generations.core.generationscore.common.client.screen.battle

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.Gender
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.BattleConditionsOverlay
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdGui
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdModel
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import generations.gg.generations.core.generationscore.common.client.screen.raid.RaidHud
import generations.gg.generations.core.generationscore.common.network.packets.raid.RaidHudMember
import generations.gg.generations.core.generationscore.common.network.packets.raid.S2CRaidHudPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.util.IdentityHashMap
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Battle HUD in the style of Sword and Shield, in HD (drawn by BattleOverlayMixin
 * in place of Cobblemon's tiles):
 * - the player's Pokémon at the bottom left (above the hotbar when it would cover
 *   it): portrait, name, gender, level, HP bar and HP, status;
 * - the opponents at the top right, the same without the HP numbers;
 * - in a raid, the boss as a long bar at the top centre with its name in red, the
 *   turn and the fainted Pokémon once there are some, and the NPC allies, small, at
 *   the top left;
 * - weather and terrain badges, only while one is active;
 * - the battle log on the right, above the menus.
 */
object SwshBattleHud {
    val enabled: Boolean get() = GenerationsCore.CONFIG.client.swshBattleUi

    private const val MARGIN = 8f
    private const val PLAYER_W = 140f
    private const val PLAYER_H = 34f
    private const val FOE_W = 136f
    private const val FOE_H = 30f
    private const val ALLY_W = 112f
    private const val ALLY_H = 20f
    private const val LOG_W = 160f
    private const val LOG_SIZE = 7f
    /** Half the width of the vanilla hotbar, centred at the bottom. */
    private const val HOTBAR_HALF = 91f
    private const val HOTBAR_H = 24f

    private const val WHITE = 0xFFFFFF
    private const val GREY = 0xB8BCC8
    private const val RAID_RED = 0xFF3A5C

    private class Line(val text: String, val colour: Int?, val time: Long)

    private var battleId: UUID? = null
    private val positions = IdentityHashMap<ActiveClientBattlePokemon, Float>()
    private val log = ArrayDeque<Line>()
    private val allyStates = mutableMapOf<String, FloatingState>()

    private val width get() = Minecraft.getInstance().window.guiScaledWidth.toFloat()
    private val height get() = Minecraft.getInstance().window.guiScaledHeight.toFloat()

    /** Bottom of the player's boxes: above the hotbar when they would reach it. */
    private val playerBottom: Float
        get() = if (MARGIN + PLAYER_W + 4f < width / 2f - HOTBAR_HALF) height - MARGIN else height - HOTBAR_H - 4f

    /** Width of the boss bar: between the allies on the left and the weather on the right. */
    private val bossWidth: Float
        get() = (width - 2f * (MARGIN + ALLY_W + 10f)).coerceIn(140f, 320f)

    /**
     * Called for each tile Cobblemon would draw: keeps its slide animations (switches)
     * running, with the boxes' own positions.
     */
    fun trackTile(active: ActiveClientBattlePokemon, left: Boolean, tickDelta: Float) {
        if (active.battlePokemon == null) return
        val rest = if (left) MARGIN else width - MARGIN - FOE_W
        active.invisibleX = if (left) -PLAYER_W - 2f else width + 2f
        active.xDisplacement = rest
        active.animate(tickDelta)
        positions[active] = active.xDisplacement
    }

    fun render(graphics: GuiGraphics, tickDelta: Float, opacity: Float) {
        val battle = CobblemonClient.battle ?: return
        sync(battle)
        val player = Minecraft.getInstance().player?.uuid ?: return
        val side1 = if (battle.side1.actors.any { it.uuid == player }) battle.side1 else battle.side2
        val side2 = if (side1 == battle.side1) battle.side2 else battle.side1
        val raid = RaidHud.current()
        val alpha = opacity.coerceIn(0.3f, 1f)

        side1.activeClientBattlePokemon.forEachIndexed { i, active -> playerBox(graphics, active, i, tickDelta, alpha) }
        if (raid != null) {
            bossBar(graphics, side2.activeClientBattlePokemon.firstOrNull()?.battlePokemon, raid, alpha)
            allies(graphics, raid, tickDelta, alpha)
        } else {
            val count = side2.activeClientBattlePokemon.count()
            side2.activeClientBattlePokemon.forEachIndexed { i, active -> foeBox(graphics, battle, active, count - 1 - i, tickDelta, alpha) }
        }
        conditions(graphics, raid != null, alpha)
        battleLog(graphics, alpha)
    }

    /** A new battle: its log, and no weather or terrain left from the previous one. */
    private fun sync(battle: ClientBattle) {
        if (battle.battleId == battleId) return
        battleId = battle.battleId
        positions.clear()
        log.clear()
        allyStates.clear()
        BattleConditionsOverlay.currentConditionsData = null
        battle.messages.subscribe { sequence ->
            if (battleId != battle.battleId) return@subscribe
            val (text, colour) = HdText.plain(sequence)
            if (text.isNotBlank()) log.addLast(Line(text.trim(), colour, System.currentTimeMillis()))
            while (log.size > 40) log.removeFirst()
        }
    }

    private fun ratio(pokemon: ClientBattlePokemon): Float =
        if (pokemon.isHpFlat) (if (pokemon.maxHp > 0f) pokemon.hpValue / pokemon.maxHp else 0f) else pokemon.hpValue

    private fun level(pokemon: ClientBattlePokemon) = Component.translatable("generations_core.battle.level", pokemon.level).string

    private fun portrait(graphics: GuiGraphics, pokemon: ClientBattlePokemon, x: Float, y: Float, size: Float, reversed: Boolean, tickDelta: Float, alpha: Float) {
        HdGui.draw(graphics, HdGui.PORTRAIT_RING, x, y, size, size, HdGui.withAlpha(WHITE, alpha))
        HdModel.portrait(graphics, pokemon.species, pokemon.state, x + 1.5f, y + 1.5f, size - 3f, reversed, tickDelta, alpha)
    }

    /** Name, then the gender symbol right after it. Returns where the name ends. */
    private fun name(graphics: GuiGraphics, pokemon: ClientBattlePokemon, x: Float, y: Float, maxWidth: Float, alpha: Float, size: Float): Float {
        val text = HdText.fit(pokemon.displayName.string, maxWidth - 8f, size, true)
        HdText.draw(graphics, text, x, y, size, HdGui.withAlpha(WHITE, alpha), bold = true, shadow = true)
        val end = x + HdText.width(text, size, true)
        val icon = when (pokemon.gender) {
            Gender.MALE -> HdGui.GENDER_MALE
            Gender.FEMALE -> HdGui.GENDER_FEMALE
            else -> null
        }
        icon?.let { HdGui.draw(graphics, it, end + 1.5f, y + 0.5f, size * 0.75f, size * 0.75f, HdGui.withAlpha(WHITE, alpha)) }
        return end
    }

    private fun status(graphics: GuiGraphics, pokemon: ClientBattlePokemon, x: Float, y: Float, alpha: Float) {
        val status = pokemon.status ?: return
        val colour = when (status.showdownName) {
            "par" -> 0xE8B820
            "brn" -> 0xE86A28
            "psn", "tox" -> 0x9A3AB0
            "slp" -> 0x7A7A8E
            "frz" -> 0x48B8D8
            else -> 0x6A6A7A
        }
        val label = Component.translatable("cobblemon.ui.status." + status.showdownName).string.uppercase()
        val w = HdText.width(label, 6f, true) + 7f
        HdGui.bar(graphics, x, y, w, 7.5f, HdGui.withAlpha(colour, alpha))
        HdText.draw(graphics, label, x + w / 2f, y + 0.6f, 6f, HdGui.withAlpha(WHITE, alpha), bold = true, align = HdText.Align.CENTER)
    }

    private fun playerBox(graphics: GuiGraphics, active: ActiveClientBattlePokemon, index: Int, tickDelta: Float, alpha: Float) {
        val pokemon = active.battlePokemon ?: return
        val x = positions[active] ?: MARGIN
        val y = playerBottom - PLAYER_H - index * (PLAYER_H + 3f)
        HdGui.panel(graphics, HdGui.PANEL_DARK, x, y, PLAYER_W, PLAYER_H, HdGui.withAlpha(WHITE, alpha), corner = 6f)
        portrait(graphics, pokemon, x + 3f, y + 3f, 28f, false, tickDelta, alpha)

        val tx = x + 35f
        val level = level(pokemon)
        HdText.draw(graphics, level, x + PLAYER_W - 5f, y + 4f, 7f, HdGui.withAlpha(WHITE, alpha), bold = true, align = HdText.Align.RIGHT)
        name(graphics, pokemon, tx, y + 3.5f, PLAYER_W - 44f - HdText.width(level, 7f, true), alpha, 8f)

        val ratio = ratio(pokemon)
        HdGui.healthBar(graphics, tx, y + 15f, PLAYER_W - 40f, 4.5f, ratio, alpha)
        val hp = if (pokemon.isHpFlat) "${ceil(pokemon.hpValue).toInt()}/${pokemon.maxHp.toInt()}" else "${(ratio * 100).roundToInt()} %"
        HdText.draw(graphics, hp, x + PLAYER_W - 5f, y + 22.5f, 7f, HdGui.withAlpha(WHITE, alpha), bold = true, align = HdText.Align.RIGHT)
        status(graphics, pokemon, tx, y + 23f, alpha)
    }

    private fun foeBox(graphics: GuiGraphics, battle: ClientBattle, active: ActiveClientBattlePokemon, index: Int, tickDelta: Float, alpha: Float) {
        val pokemon = active.battlePokemon ?: return
        val x = positions[active] ?: (width - MARGIN - FOE_W)
        val y = MARGIN + index * (FOE_H + 3f)
        HdGui.panel(graphics, HdGui.PANEL_DARK, x, y, FOE_W, FOE_H, HdGui.withAlpha(WHITE, alpha), corner = 6f)
        portrait(graphics, pokemon, x + FOE_W - 27f, y + 3f, 24f, true, tickDelta, alpha)

        val right = x + FOE_W - 31f
        val level = level(pokemon)
        HdText.draw(graphics, level, right, y + 4f, 7f, HdGui.withAlpha(WHITE, alpha), bold = true, align = HdText.Align.RIGHT)
        val end = name(graphics, pokemon, x + 6f, y + 3.5f, right - x - 10f - HdText.width(level, 7f, true), alpha, 8f)
        // A wild Pokémon already caught: a small Poké Ball after its name.
        if (battle.isPvW && battle.knowledge == PokedexEntryProgress.OWNED) ball(graphics, end + 9f, y + 4f, 6.5f, 0xE83A3A, alpha)
        HdGui.healthBar(graphics, x + 6f, y + 15f, right - x - 8f, 4.5f, ratio(pokemon), alpha)
        status(graphics, pokemon, x + 6f, y + 21.5f, alpha)
    }

    fun ball(graphics: GuiGraphics, x: Float, y: Float, size: Float, colour: Int, alpha: Float) {
        HdGui.draw(graphics, HdGui.BALL_TOP, x, y, size, size, HdGui.withAlpha(colour, alpha))
        HdGui.draw(graphics, HdGui.BALL_BASE, x, y, size, size, HdGui.withAlpha(WHITE, alpha))
    }

    /**
     * Raid: the boss's name in red, the tier's stars under it, a long bar, then the
     * turn followed by the Dynamax shield, and the fainted Pokémon.
     */
    private fun bossBar(graphics: GuiGraphics, boss: ClientBattlePokemon?, raid: S2CRaidHudPacket, alpha: Float) {
        val barWidth = bossWidth
        val x = (width - barWidth) / 2f
        val y = 5f
        val name = boss?.displayName?.string ?: ""
        HdText.draw(graphics, name, width / 2f, y, 11f, HdGui.withAlpha(RAID_RED, alpha), bold = true, align = HdText.Align.CENTER, outline = 0xFF2A0010.toInt())
        if (raid.stars > 0) {
            val size = 7f
            val total = raid.stars * (size + 1f) - 1f
            HdGui.stars(graphics, raid.stars, width / 2f - total / 2f, y + 14f, size, HdGui.withAlpha(if (raid.stars >= 6) 0xFFD24A else WHITE, alpha))
        }
        HdGui.healthBar(graphics, x, y + 23f, barWidth, 6f, boss?.let { ratio(it) } ?: 0f, alpha, boss = true)

        val turn = Component.translatable("generations_core.raid.hud.turn", raid.turn, raid.maxTurns).string
        HdText.draw(graphics, turn, x, y + 32f, 7f, HdGui.withAlpha(WHITE, alpha), bold = true, outline = 0xC0101018.toInt())
        // The Dynamax shield after the turn: one purple segment per hit it can still take.
        if (raid.shieldActive && raid.shieldMax > 0) {
            val size = 6f
            val label = Component.translatable("generations_core.raid.hud.shield").string
            val labelX = x + HdText.width(turn, 7f, true) + 8f
            HdText.draw(graphics, label, labelX, y + 32.5f, 6.5f, HdGui.withAlpha(0xD8B0FF, alpha), bold = true, outline = 0xC0101018.toInt())
            val pipsX = labelX + HdText.width(label, 6.5f, true) + 4f
            for (i in 0 until raid.shieldMax) {
                HdGui.bar(graphics, pipsX + i * (size + 2f), y + 32.5f, size, size, HdGui.withAlpha(if (i < raid.shieldSegments) 0xB65CFF else 0x3A3A48, alpha))
            }
        }
        // Fainted Pokémon of the raid, out of the limit: shown from the first one.
        if (raid.faints > 0) {
            val label = Component.translatable("generations_core.raid.hud.storm").string
            val orbs = raid.maxFaints * 8f
            HdText.draw(graphics, label, x + barWidth - orbs - 3f, y + 32f, 7f, HdGui.withAlpha(WHITE, alpha), bold = true, align = HdText.Align.RIGHT, outline = 0xC0101018.toInt())
            for (i in 0 until raid.maxFaints) {
                val ox = x + barWidth - orbs + i * 8f
                HdGui.bar(graphics, ox, y + 32.5f, 6f, 6f, HdGui.withAlpha(if (i < raid.faints) 0xF0305A else 0x3A3A48, alpha))
            }
        }
    }

    /** Raid: the NPC allies at the top left, small, with their Pokémon and its HP. */
    private fun allies(graphics: GuiGraphics, raid: S2CRaidHudPacket, tickDelta: Float, alpha: Float) {
        var y = MARGIN
        raid.members.filter { it.kind != RaidHudMember.YOU }.forEach { ally ->
            val a = alpha * if (ally.down) 0.6f else 1f
            HdGui.panel(graphics, HdGui.PANEL_DARK, MARGIN, y, ALLY_W, ALLY_H, HdGui.withAlpha(WHITE, alpha), corner = 5f)
            HdGui.draw(graphics, HdGui.PORTRAIT_RING, MARGIN + 2.5f, y + 2.5f, 15f, 15f, HdGui.withAlpha(WHITE, alpha))
            ResourceLocation.tryParse(ally.species)?.let(PokemonSpecies::getByIdentifier)?.let { species ->
                val state = allyStates.getOrPut(ally.name) { FloatingState() }
                state.currentAspects = ally.aspects.toSet()
                HdModel.portrait(graphics, species, state, MARGIN + 3.5f, y + 3.5f, 13f, false, tickDelta, a)
            }
            HdText.draw(graphics, ally.name, MARGIN + 21f, y + 2.5f, 6.5f, HdGui.withAlpha(WHITE, a), bold = true)
            val label = HdText.fit(ally.label, ALLY_W - 27f - HdText.width(ally.name, 6.5f, true), 6f)
            HdText.draw(graphics, label, MARGIN + ALLY_W - 4f, y + 3f, 6f, HdGui.withAlpha(GREY, a), align = HdText.Align.RIGHT)
            if (ally.down) {
                HdText.draw(graphics, Component.translatable("generations_core.raid.hud.down").string, MARGIN + ALLY_W - 4f, y + 11f, 6.5f, HdGui.withAlpha(0xFF5060, alpha), bold = true, align = HdText.Align.RIGHT)
            } else {
                HdGui.healthBar(graphics, MARGIN + 21f, y + 12.5f, ALLY_W - 26f, 3.5f, if (ally.maxHp > 0) ally.hp.toFloat() / ally.maxHp else 0f, alpha)
            }
            y += ALLY_H + 2f
        }
    }

    /** Weather and terrain badges, with the turns left; nothing without any. */
    private fun conditions(graphics: GuiGraphics, raid: Boolean, alpha: Float) {
        val data = BattleConditionsOverlay.currentConditionsData ?: return
        val badges = mutableListOf<Pair<HdGui.Texture, Int?>>()
        val weather = data.weatherTracker
        HdGui.CONDITIONS[weather.weather]?.let { texture ->
            val lasting = weather.weather in setOf("Delta Stream", "Desolate Land", "Primordial Sea")
            badges += texture to if (lasting) null else (5 - weather.turns).coerceAtLeast(1)
        }
        data.fieldList.forEach { (effect, turns) -> HdGui.CONDITIONS[effect]?.let { badges += it to (5 - turns).coerceAtLeast(1) } }
        if (badges.isEmpty()) return

        val size = 16f
        val total = badges.size * (size + 3f) - 3f
        val x0 = if (raid) (width + bossWidth) / 2f + 6f else width / 2f - total / 2f
        val y = if (raid) 22f else 5f
        badges.forEachIndexed { i, (texture, turns) ->
            val x = x0 + i * (size + 3f)
            HdGui.draw(graphics, texture, x, y, size, size, HdGui.withAlpha(WHITE, alpha))
            turns?.let { HdText.draw(graphics, it.toString(), x + size, y + size - 6f, 6.5f, HdGui.withAlpha(WHITE, alpha), bold = true, align = HdText.Align.RIGHT, outline = 0xFF101018.toInt()) }
        }
    }

    /** The last messages of the battle, on the right above the menus. */
    private fun battleLog(graphics: GuiGraphics, alpha: Float) {
        if (log.isEmpty()) return
        val lineHeight = HdText.lineHeight(LOG_SIZE)
        val bottom = height - 30f - SwshBattleUi.MENU_HEIGHT - 6f
        val room = ((bottom - 56f) / lineHeight).toInt().coerceIn(2, 5)
        val lines = log.takeLast(room)
        val x = width - 12f - LOG_W
        val top = bottom - lines.size * lineHeight - 6f
        HdGui.panel(graphics, HdGui.PANEL_DARK, x, top, LOG_W, bottom - top, HdGui.withAlpha(WHITE, alpha * 0.85f), corner = 5f)
        val now = System.currentTimeMillis()
        lines.forEachIndexed { i, line ->
            // Older lines fade.
            val age = (now - line.time) / 1000f
            val fade = if (age < 8f) 1f else (1f - (age - 8f) / 4f).coerceAtLeast(0.45f)
            val text = HdText.fit(line.text, LOG_W - 10f, LOG_SIZE)
            HdText.draw(graphics, text, x + 5f, top + 3f + i * lineHeight, LOG_SIZE, HdGui.withAlpha(line.colour ?: WHITE, alpha * fade))
        }
    }
}
