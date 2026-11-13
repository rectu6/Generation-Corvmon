package generations.gg.generations.core.generationscore.common.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import generations.gg.generations.core.generationscore.common.world.item.GenerationsArmor
import generations.gg.generations.core.generationscore.common.world.ultraspace.suit.UltraShield
import generations.gg.generations.core.generationscore.common.world.ultraspace.suit.UltraSuit
import generations.gg.generations.core.generationscore.common.world.ultraspace.weather.UltraWeather
import generations.gg.generations.core.generationscore.common.world.ultraspace.weather.UltraWeatherEvent
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTransfers
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack

/**
 * /ultrawarp weather and /ultrawarp suit (phases 4.4, 4.5), for the tests (operators,
 * like the rest of /ultrawarp):
 * - weather: the state of the seven worlds; weather set <world> <weather|clear> [minutes];
 *   weather next <world> (the event in progress ends, or the next one starts);
 * - suit check: the suit worn, the air, the shields complete on the four pieces;
 *   suit shield add|remove <shield>: on the piece held in the main hand;
 *   suit give: the four pieces, with the seven shields.
 */
object UltraWeatherCommands {
    private const val WEATHER = "generations_core.ultra_weather.command"
    private const val SUIT = "generations_core.ultra_suit.command"

    fun weather(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("weather")
        .executes { status(it.source) }
        .then(
            Commands.literal("set").then(
                world().then(
                    Commands.argument("event", StringArgumentType.word())
                        .suggests { context, builder ->
                            val id = runCatching { StringArgumentType.getString(context, "world") }.getOrNull()
                            SharedSuggestionProvider.suggest(UltraWeatherEvent.of(id ?: "").map { it.id } + "clear", builder)
                        }
                        .executes { set(it, null) }
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 24 * 60)).executes { set(it, IntegerArgumentType.getInteger(it, "minutes")) })
                )
            )
        )
        .then(Commands.literal("next").then(world().executes { next(it.source, StringArgumentType.getString(it, "world")) }))

    fun suit(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("suit")
        .then(Commands.literal("check").executes { check(it.source) })
        .then(Commands.literal("give").executes { give(it.source) })
        .then(
            Commands.literal("shield")
                .then(Commands.literal("add").then(shield().executes { shield(it, true) }))
                .then(Commands.literal("remove").then(shield().executes { shield(it, false) }))
        )

    private fun world() =
        Commands.argument("world", StringArgumentType.word()).suggests { _, builder -> SharedSuggestionProvider.suggest(UltraWorlds.IDS, builder) }

    private fun shield() =
        Commands.argument("shield", StringArgumentType.word()).suggests { _, builder -> SharedSuggestionProvider.suggest(UltraShield.entries.map { it.id }, builder) }

    private fun status(source: CommandSourceStack): Int {
        source.sendSuccess({ Component.translatable("$WEATHER.title").withStyle(ChatFormatting.AQUA) }, false)
        UltraWeather.status(source.server).forEach { line -> source.sendSuccess({ line }, false) }
        return 1
    }

    private fun set(context: CommandContext<CommandSourceStack>, minutes: Int?): Int {
        val source = context.source
        val world = StringArgumentType.getString(context, "world")
        val name = StringArgumentType.getString(context, "event")
        if (world !in UltraWorlds.IDS) {
            source.sendFailure(Component.translatable("$WEATHER.unknown_world", world, UltraWorlds.IDS.joinToString()))
            return 0
        }
        val event = if (name == "clear") null else UltraWeatherEvent.byId(name)?.takeIf { it.world == world }
        if (event == null && name != "clear") {
            source.sendFailure(Component.translatable("$WEATHER.unknown_event", name, (UltraWeatherEvent.of(world).map { it.id } + "clear").joinToString()))
            return 0
        }
        if (!UltraWeather.force(source.server, world, event, minutes?.toFloat())) return 0
        source.sendSuccess({
            if (event == null) Component.translatable("$WEATHER.cleared", UltraWarpTransfers.worldName(world))
            else Component.translatable("$WEATHER.set", UltraWarpTransfers.worldName(world), event.displayName())
        }, true)
        return 1
    }

    private fun next(source: CommandSourceStack, world: String): Int {
        if (world !in UltraWorlds.IDS || !UltraWeather.skip(source.server, world)) {
            source.sendFailure(Component.translatable("$WEATHER.unknown_world", world, UltraWorlds.IDS.joinToString()))
            return 0
        }
        status(source)
        return 1
    }

    private fun check(source: CommandSourceStack): Int {
        val player = source.playerOrException
        source.sendSuccess({ Component.translatable("$SUIT.pieces", UltraSuit.piecesWorn(player), player.airSupply, player.maxAirSupply) }, false)
        for (shield in UltraShield.entries) {
            val pieces = UltraSuit.piecesWith(player, shield)
            val style = if (UltraSuit.protection(player, shield) >= 1f) ChatFormatting.GREEN else if (pieces > 0) ChatFormatting.YELLOW else ChatFormatting.DARK_GRAY
            source.sendSuccess({ Component.translatable("$SUIT.shield", shield.displayName(), pieces).withStyle(style) }, false)
        }
        return 1
    }

    private fun shield(context: CommandContext<CommandSourceStack>, add: Boolean): Int {
        val source = context.source
        val player = source.playerOrException
        val shield = UltraShield.byId(StringArgumentType.getString(context, "shield"))
        if (shield == null) {
            source.sendFailure(Component.translatable("$SUIT.unknown_shield", UltraShield.entries.joinToString { it.id }))
            return 0
        }
        val held = player.mainHandItem
        if (!UltraSuit.isPiece(held)) {
            source.sendFailure(Component.translatable("$SUIT.not_a_piece"))
            return 0
        }
        val changed = if (add) UltraSuit.withShield(held, shield).also { it.count = held.count } else UltraSuit.withoutShield(held, shield)
        player.setItemInHand(InteractionHand.MAIN_HAND, changed)
        source.sendSuccess({ Component.translatable(if (add) "$SUIT.added" else "$SUIT.removed", shield.displayName()) }, true)
        return 1
    }

    private fun give(source: CommandSourceStack): Int {
        val player = source.playerOrException
        for (piece in GenerationsArmor.ULTRA.stream()) {
            var stack = ItemStack(piece)
            for (shield in UltraShield.entries) stack = UltraSuit.withShield(stack, shield)
            if (!player.inventory.add(stack)) player.drop(stack, false)
        }
        source.sendSuccess({ Component.translatable("$SUIT.given") }, true)
        return 1
    }
}
