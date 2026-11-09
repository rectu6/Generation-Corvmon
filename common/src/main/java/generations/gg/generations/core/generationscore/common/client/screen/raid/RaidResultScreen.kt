package generations.gg.generations.core.generationscore.common.client.screen.raid

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.mojang.math.Axis
import generations.gg.generations.core.generationscore.common.client.screen.battle.SwshBattleHud
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdButton
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdGui
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdModel
import generations.gg.generations.core.generationscore.common.client.screen.hd.HdText
import generations.gg.generations.core.generationscore.common.network.packets.raid.C2SRaidResultPacket
import generations.gg.generations.core.generationscore.common.network.packets.raid.S2CRaidResultPacket
import generations.gg.generations.core.generationscore.common.network.packets.raid.S2CRaidResultPacket.State
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * End of a won raid, in HD: catch the Pokémon or not (one Poké Ball, chosen from the
 * inventory), then the rewards to see and claim. Closing the menu claims them too.
 *
 * The catch is animated: the ball flies to the Pokémon, which turns to light and is
 * drawn in; the ball falls, wobbles, then clicks shut with sparkles (caught) or
 * bursts open (escaped).
 */
class RaidResultScreen(
    private val result: S2CRaidResultPacket,
    private var ballIndex: Int,
    /** Plays the catch animation before showing the outcome. */
    private val animate: Boolean,
) : Screen(Component.translatable("generations_core.raid.result.title", "")) {
    private val species = ResourceLocation.tryParse(result.species)?.let(PokemonSpecies::getByIdentifier)
    private val name: Component = species?.translatedName ?: Component.literal(result.species)
    private val state = FloatingState()
    private var left = 0f
    private var top = 0f
    private var claim: HdButton? = null

    private val ballColour = ballColour(result.ball)
    private val start = System.currentTimeMillis()
    private val caught = result.state == State.CAUGHT
    private val wobbles = if (caught) 3 else 1 + (result.ball.length % 2)
    private val animationEnd = if (animate) WOBBLE_START + wobbles * WOBBLE + 0.5f else 0f
    private var lastSound = -1

    /** Inventory slots holding a Poké Ball. */
    private val balls: List<Int>
        get() {
            val inventory = Minecraft.getInstance().player?.inventory ?: return emptyList()
            return (0 until inventory.containerSize).filter { inventory.getItem(it).item is PokeBallItem }
        }

    private val seconds: Float get() = (System.currentTimeMillis() - start) / 1000f
    private val animating: Boolean get() = animate && seconds < animationEnd

    override fun init() {
        left = ((width - WIDTH) / 2).toFloat()
        top = ((height - HEIGHT) / 2).toFloat()
        val rx = (left + 178).toInt()
        if (result.state == State.CHOICE) {
            addRenderableWidget(HdButton(rx, (top + 62).toInt(), 20, 20, Component.literal("<")) { ballIndex-- })
            addRenderableWidget(HdButton(rx + 172, (top + 62).toInt(), 20, 20, Component.literal(">")) { ballIndex++ })
            addRenderableWidget(HdButton(rx, (top + 92).toInt(), 93, 20, Component.translatable("generations_core.raid.result.catch")) { button ->
                currentBall()?.let {
                    button.active = false
                    C2SRaidResultPacket(C2SRaidResultPacket.Action.CATCH, it).sendToServer()
                }
            }).active = balls.isNotEmpty()
            addRenderableWidget(HdButton(rx + 99, (top + 92).toInt(), 93, 20, Component.translatable("generations_core.raid.result.skip")) {
                C2SRaidResultPacket(C2SRaidResultPacket.Action.SKIP).sendToServer()
            })
        }
        claim = addRenderableWidget(HdButton((left + WIDTH / 2 - 90).toInt(), (top + HEIGHT - 28).toInt(), 180, 20, Component.translatable("generations_core.raid.result.claim")) {
            C2SRaidResultPacket(C2SRaidResultPacket.Action.CLAIM).sendToServer()
            minecraft?.setScreen(null)
        })
        claim?.active = result.state != State.CHOICE && !animating
    }

    private fun currentBall(): Int? {
        val slots = balls
        if (slots.isEmpty()) return null
        return slots[Math.floorMod(ballIndex, slots.size)]
    }

    override fun tick() {
        super.tick()
        claim?.active = result.state != State.CHOICE && !animating
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        HdGui.draw(graphics, HdGui.RESULT_BG, left, top, WIDTH.toFloat(), HEIGHT.toFloat())
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val white = RaidLobbyScreen.WHITE
        val dark = RaidLobbyScreen.DARK
        val grey = RaidLobbyScreen.GREY

        // Title: tier and "Raid won!".
        HdGui.stars(graphics, result.stars, left + 13f, top + 9f, 11f, if (result.stars >= 6) RaidLobbyScreen.GOLD else white, spacing = 1.5f)
        HdText.draw(graphics, Component.translatable("generations_core.raid.result.won"), left + 16f + result.stars * 12.5f, top + 10f, 10f, white, bold = true, shadow = true)
        HdText.draw(graphics, name, left + 84f, top + 146f, 11f, white, bold = true, align = HdText.Align.CENTER, shadow = true)

        if (animate) renderCatch(graphics, partialTick)
        else pokemon(graphics, 1f, partialTick)

        val rx = left + 178f
        when {
            animating -> HdText.draw(graphics, Component.translatable("generations_core.raid.result.throwing", name), rx, top + 40f, 9f, dark, bold = true)
            result.state == State.CHOICE -> {
                HdText.draw(graphics, Component.translatable("generations_core.raid.result.catch_question", name), rx, top + 26f, 10f, dark, bold = true)
                HdText.draw(graphics, Component.translatable("generations_core.raid.result.one_try"), rx, top + 42f, 7.5f, grey)
                val slot = currentBall()
                if (slot == null) {
                    HdText.draw(graphics, Component.translatable("generations_core.raid.result.no_ball"), rx + 96f, top + 67f, 8f, RaidLobbyScreen.RED, bold = true, align = HdText.Align.CENTER)
                } else {
                    val stack = Minecraft.getInstance().player!!.inventory.getItem(slot)
                    HdGui.panel(graphics, HdGui.PANEL_LIGHT, rx + 26f, top + 62f, 140f, 20f, corner = 6f)
                    SwshBattleHud.ball(graphics, rx + 30f, top + 64f, 16f, ballColour(BuiltInRegistries.ITEM.getKey(stack.item).toString()), 1f)
                    HdText.draw(graphics, HdText.fit(stack.hoverName.string, 100f, 8f, true), rx + 50f, top + 67.5f, 8f, dark, bold = true)
                    HdText.draw(graphics, "×${stack.count}", rx + 162f, top + 67.5f, 7.5f, grey, align = HdText.Align.RIGHT)
                }
            }
            result.state == State.CAUGHT -> HdText.draw(graphics, Component.translatable("generations_core.raid.result.caught", name), rx, top + 40f, 10f, 0xFF1E8A3C.toInt(), bold = true)
            result.state == State.ESCAPED -> HdText.draw(graphics, Component.translatable("generations_core.raid.result.escaped", name), rx, top + 40f, 10f, RaidLobbyScreen.RED, bold = true)
            result.state == State.SKIPPED -> HdText.draw(graphics, Component.translatable("generations_core.raid.result.skipped", name), rx, top + 40f, 10f, dark, bold = true)
        }

        // Rewards won.
        HdText.draw(graphics, Component.translatable("generations_core.raid.result.rewards"), rx, top + 122f, 9f, dark, bold = true)
        var hovered: Int? = null
        result.rewards.forEachIndexed { i, stack ->
            val x = rx + (i % 8) * 23f
            val y = top + 136f + (i / 8) * 23f
            HdGui.panel(graphics, HdGui.PANEL_LIGHT, x, y, 20f, 20f, 0xFFE6E9EE.toInt(), corner = 5f)
            graphics.renderItem(stack, (x + 2).toInt(), (y + 2).toInt())
            graphics.renderItemDecorations(font, stack, (x + 2).toInt(), (y + 2).toInt())
            if (HdGui.isOver(mouseX.toDouble(), mouseY.toDouble(), x, y, 20f, 20f)) hovered = i
        }
        hovered?.let { graphics.renderTooltip(font, result.rewards[it], mouseX, mouseY) }
        if (result.state == State.CHOICE) {
            HdText.draw(graphics, Component.translatable("generations_core.raid.result.decide_first"), left + WIDTH / 2f, top + HEIGHT - 42f, 7.5f, grey, align = HdText.Align.CENTER)
        }
    }

    private fun pokemon(graphics: GuiGraphics, scale: Float, partialTick: Float) {
        val species = species ?: return
        if (scale <= 0.01f) return
        // The model shrinks towards the centre of its frame.
        val w = 150f * scale
        val h = 115f * scale
        HdModel.profile(
            graphics, RenderablePokemon(species, result.aspects.toSet()), state,
            left + 84f - w / 2f, top + 85f - h / 2f, w, h, 2.0f * scale, 325f, -8f * scale + (h - 115f * scale) / 2f, partialTick,
        )
    }

    /**
     * The catch, over time: throw, absorption, fall, wobbles, then the click or the
     * burst. The Pokémon is centred at (cx, cy), the ball lands at (cx, groundY).
     */
    private fun renderCatch(graphics: GuiGraphics, partialTick: Float) {
        val t = seconds
        val cx = left + 84f
        val cy = top + 85f
        val groundY = top + 128f

        // The Pokémon: shrinks into the ball, comes back out if it escapes.
        val pokemonScale = when {
            t < THROW -> 1f
            t < ABSORB -> 1f - (t - THROW) / (ABSORB - THROW)
            !caught && t >= animationEnd - 0.5f -> Mth.clamp((t - (animationEnd - 0.5f)) / 0.3f, 0f, 1f)
            else -> 0f
        }
        pokemon(graphics, pokemonScale, partialTick)

        // Light while it is drawn in, and when it bursts out.
        val flash = when {
            t in THROW..ABSORB -> 1f - abs((t - (THROW + ABSORB) / 2f) / ((ABSORB - THROW) / 2f))
            !caught && t in (animationEnd - 0.5f)..(animationEnd - 0.15f) -> 0.9f
            else -> 0f
        }
        if (flash > 0f) {
            val size = 150f * (0.6f + flash * 0.6f)
            HdGui.draw(graphics, HdGui.GLOW, cx - size / 2f, cy - size / 2f, size, size, HdGui.withAlpha(0xFFFFFF, flash))
        }

        if (!caught && t >= animationEnd - 0.5f) {
            sound(5, CobblemonSounds.POKE_BALL_SEND_OUT)
            return
        }
        val bx: Float
        val by: Float
        val angle: Float
        when {
            t < THROW -> {
                // From the bottom right in an arc to the Pokémon, spinning.
                val p = t / THROW
                bx = Mth.lerp(p, left + 150f, cx)
                by = Mth.lerp(p, top + 150f, cy) - sin(p * Math.PI.toFloat()) * 40f
                angle = p * 720f
            }
            t < ABSORB -> {
                bx = cx; by = cy; angle = 0f
            }
            t < WOBBLE_START -> {
                val p = (t - ABSORB) / (WOBBLE_START - ABSORB)
                bx = cx; by = Mth.lerp(p * p, cy, groundY); angle = 0f
            }
            else -> {
                val w = (t - WOBBLE_START) / WOBBLE
                val shaking = w < wobbles
                bx = cx; by = groundY
                angle = if (shaking) sin((w % 1f) * Math.PI.toFloat() * 2f) * 25f * (1f - (w % 1f)) else 0f
            }
        }
        sound(0, CobblemonSounds.POKE_BALL_THROW)
        if (t >= THROW) sound(1, CobblemonSounds.POKE_BALL_RECALL)
        if (t >= WOBBLE_START) {
            val w = ((t - WOBBLE_START) / WOBBLE).toInt()
            if (w < wobbles) sound(10 + w, SoundEvents.STONE_BUTTON_CLICK_ON)
            else if (caught) sound(4, SoundEvents.PLAYER_LEVELUP)
        }

        // A soft shadow under the ball once it has landed.
        if (t >= WOBBLE_START) HdGui.draw(graphics, HdGui.GLOW, bx - 14f, groundY + 9f, 28f, 8f, 0x60000000)
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(bx, by, 150f)
        // Wobbles pivot on the bottom of the ball.
        pose.translate(0f, BALL / 2f, 0f)
        pose.mulPose(Axis.ZP.rotationDegrees(angle))
        pose.translate(0f, -BALL / 2f, 0f)
        SwshBattleHud.ball(graphics, -BALL / 2f, -BALL / 2f, BALL, ballColour, 1f)
        pose.popPose()

        // Caught: sparkles around the ball.
        if (caught && t >= WOBBLE_START + wobbles * WOBBLE) {
            val since = t - (WOBBLE_START + wobbles * WOBBLE)
            for (i in 0 until 6) {
                val a = i * 60f * Mth.DEG_TO_RAD + since * 1.5f
                val r = 16f + since * 14f
                val size = (9f - since * 6f).coerceAtLeast(3f)
                HdGui.draw(graphics, HdGui.SPARKLE, bx + cos(a) * r - size / 2f, by + sin(a) * r * 0.7f - size / 2f, size, size, RaidLobbyScreen.GOLD)
            }
        }
    }

    /** Plays a sound once per step of the animation. */
    private fun sound(step: Int, sound: SoundEvent) {
        if (step <= lastSound) return
        lastSound = step
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(sound, 1f))
    }

    // Not before the catch is decided: Escape would otherwise give the chance up.
    override fun shouldCloseOnEsc(): Boolean = result.state != State.CHOICE

    // Escape: the server still gives the rewards.
    override fun onClose() {
        C2SRaidResultPacket(C2SRaidResultPacket.Action.CLOSE).sendToServer()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        private const val WIDTH = 380
        private const val HEIGHT = 230
        private const val BALL = 26f
        // Animation steps, in seconds.
        private const val THROW = 0.6f
        private const val ABSORB = 1.0f
        private const val WOBBLE_START = 1.4f
        private const val WOBBLE = 0.8f

        /** Colour of the upper half of each Poké Ball. */
        private val BALL_COLOURS = mapOf(
            "poke_ball" to 0xE83A3A, "great_ball" to 0x3A7AE8, "ultra_ball" to 0x2E2E34, "master_ball" to 0x8A3AC8,
            "premier_ball" to 0xF4F4F4, "safari_ball" to 0x6AA84A, "fast_ball" to 0xF0C030, "level_ball" to 0xE8A030,
            "lure_ball" to 0x2A9AB8, "heavy_ball" to 0x6A7A8A, "love_ball" to 0xF078B0, "friend_ball" to 0x4AB050,
            "moon_ball" to 0x3A5AA8, "sport_ball" to 0xE87A2A, "net_ball" to 0x3AB0C0, "dive_ball" to 0x3A6AE0,
            "nest_ball" to 0x8AB04A, "repeat_ball" to 0xE8A030, "timer_ball" to 0xE04030, "luxury_ball" to 0x2A2A30,
            "dusk_ball" to 0x2A6A3A, "heal_ball" to 0xF080B0, "quick_ball" to 0x3A8AE0, "dream_ball" to 0xE888C8,
            "beast_ball" to 0x3A8AE0, "cherish_ball" to 0xC8283A, "park_ball" to 0xE8C030, "citrine_ball" to 0xE8C030,
            "verdant_ball" to 0x4AB050, "azure_ball" to 0x3A7AE8, "roseate_ball" to 0xF078B0, "slate_ball" to 0x6A7A8A,
        )

        fun ballColour(id: String): Int {
            val path = ResourceLocation.tryParse(id)?.path ?: id
            return BALL_COLOURS[path] ?: if (path.startsWith("ancient")) 0x8A6A4A else 0xE83A3A
        }

        /** Opens the menu, or shows the outcome of the throw with its animation, keeping the chosen ball. */
        fun show(packet: S2CRaidResultPacket) {
            val minecraft = Minecraft.getInstance()
            val previous = minecraft.screen as? RaidResultScreen
            val thrown = previous?.result?.state == State.CHOICE && (packet.state == State.CAUGHT || packet.state == State.ESCAPED)
            minecraft.setScreen(RaidResultScreen(packet, previous?.ballIndex ?: 0, thrown))
        }
    }
}
