package generations.gg.generations.core.generationscore.common.commands

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.permission.Permission
import com.cobblemon.mod.common.api.permission.PermissionLevel
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.battle.flee.InstantFlee
import generations.gg.generations.core.generationscore.common.world.catchcombo.CatchCombo
import generations.gg.generations.core.generationscore.common.world.catchcombo.CatchComboMath
import generations.gg.generations.core.generationscore.common.world.charm.Charms
import generations.gg.generations.core.generationscore.common.world.shiny.ShinyMath
import generations.gg.generations.core.generationscore.common.world.shiny.ShinyOdds
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.Random

/**
 * /catchcombo (operators, Cobblemon permission generations_core.command.catchcombo):
 * - info [player]: the combo, its tier, its bonuses and the next tier;
 * - set <player> <species> <count>, reset [player];
 * - odds <species> [player]: the shiny chance of a spawn of that species for the player,
 *   with every bonus (Shiny Charm, combo) and the cap;
 * - simulate <rolls> [species]: that many rolls with that chance, against the expected count;
 * - reload: catch_combo.json, shiny_odds.json, charms.json and flee.json again.
 * Registered by CommandsMixin.
 */
object CatchComboCommands {
    private const val KEY = "generations_core.catch_combo.command"

    private val PERMISSION = object : Permission {
        override val identifier: ResourceLocation = GenerationsCore.id("command.catchcombo")
        override val literal: String = "generations_core.command.catchcombo"
        override val level: PermissionLevel = PermissionLevel.CHEAT_COMMANDS_AND_COMMAND_BLOCKS
    }

    private fun allowed(source: CommandSourceStack) =
        runCatching { Cobblemon.permissionValidator.hasPermission(source, PERMISSION) }.getOrElse { source.hasPermission(2) }

    private fun speciesArgument() = Commands.argument("species", StringArgumentType.word())
        .suggests { _, builder -> SharedSuggestionProvider.suggest(PokemonSpecies.implemented.map { it.resourceIdentifier.path }, builder) }

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("catchcombo").requires(::allowed)
                .executes { info(it.source, it.source.playerOrException) }
                .then(
                    Commands.literal("info").executes { info(it.source, it.source.playerOrException) }
                        .then(Commands.argument("player", EntityArgument.player()).executes { info(it.source, EntityArgument.getPlayer(it, "player")) })
                )
                .then(
                    Commands.literal("set").then(
                        Commands.argument("player", EntityArgument.player()).then(
                            speciesArgument().then(
                                Commands.argument("count", IntegerArgumentType.integer(1)).executes { context ->
                                    val player = EntityArgument.getPlayer(context, "player")
                                    val species = species(context) ?: return@executes 0
                                    CatchCombo.set(player, CatchCombo.key(species, null), IntegerArgumentType.getInteger(context, "count"))
                                    info(context.source, player)
                                },
                            ),
                        ),
                    ),
                )
                .then(
                    Commands.literal("reset").executes { reset(it.source, it.source.playerOrException) }
                        .then(Commands.argument("player", EntityArgument.player()).executes { reset(it.source, EntityArgument.getPlayer(it, "player")) })
                )
                .then(
                    Commands.literal("odds").then(
                        speciesArgument().executes { odds(it, it.source.playerOrException) }
                            .then(Commands.argument("player", EntityArgument.player()).executes { odds(it, EntityArgument.getPlayer(it, "player")) }),
                    ),
                )
                .then(
                    Commands.literal("simulate").then(
                        Commands.argument("rolls", IntegerArgumentType.integer(1, 10_000_000))
                            .executes { simulate(it, false) }
                            .then(speciesArgument().executes { simulate(it, true) }),
                    ),
                )
                .then(Commands.literal("reload").executes { reload(it.source) }),
        )
    }

    private fun species(context: CommandContext<CommandSourceStack>): Species? {
        val name = StringArgumentType.getString(context, "species").lowercase()
        val species = PokemonSpecies.getByName(name)
        if (species == null) context.source.sendFailure(Component.translatable("$KEY.unknown_species", name))
        return species
    }

    private fun info(source: CommandSourceStack, player: ServerPlayer): Int {
        val state = CatchCombo.stateOrNull(player)
        if (state == null) {
            source.sendSuccess({ Component.translatable("$KEY.none", player.displayName) }, false)
            return 1
        }
        val tier = CatchCombo.tier(state)
        val next = CatchComboMath.next(CatchCombo.config.sortedTiers, state.count)
        val name = CatchCombo.speciesOf(state.species!!)?.translatedName ?: Component.literal(state.species!!)
        source.sendSuccess({
            Component.translatable(
                "$KEY.info", player.displayName, name, state.count,
                ShinyMath.format(tier?.shinyMultiplier ?: 1.0), tier?.perfectIvs ?: 0,
                next?.let { Component.translatable("$KEY.next", it.threshold, ShinyMath.format(it.shinyMultiplier), it.perfectIvs) } ?: Component.translatable("$KEY.max"),
            ).withStyle(ChatFormatting.AQUA)
        }, false)
        return state.count
    }

    private fun reset(source: CommandSourceStack, player: ServerPlayer): Int {
        CatchCombo.reset(player)
        source.sendSuccess({ Component.translatable("$KEY.reset", player.displayName) }, true)
        return 1
    }

    private fun odds(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Int {
        val species = species(context) ?: return 0
        val result = ShinyOdds.preview(player, species)
        context.source.sendSuccess({
            Component.translatable(
                "$KEY.odds", species.translatedName, player.displayName, result.oneIn, Math.round(ShinyOdds.baseRate),
                if (result.bonuses.isEmpty()) Component.translatable("$KEY.no_bonus") else Component.literal(result.bonuses.joinToString(" + ")),
                ShinyMath.format(result.multiplier) + if (result.capped) " (max)" else "",
            ).withStyle(ChatFormatting.LIGHT_PURPLE)
        }, false)
        return 1
    }

    private fun simulate(context: CommandContext<CommandSourceStack>, withSpecies: Boolean): Int {
        val player = context.source.playerOrException
        val rolls = IntegerArgumentType.getInteger(context, "rolls")
        val species = if (withSpecies) species(context) ?: return 0 else {
            CatchCombo.stateOrNull(player)?.species?.let(CatchCombo::speciesOf) ?: PokemonSpecies.getByName("pidgey") ?: return 0
        }
        val result = ShinyOdds.preview(player, species)
        val random = Random()
        var shinies = 0
        repeat(rolls) { if (random.nextDouble() < result.chance) shinies++ }
        val expected = rolls * result.chance
        context.source.sendSuccess({
            Component.translatable("$KEY.simulate", rolls, species.translatedName, shinies, "%.1f".format(expected), result.oneIn).withStyle(ChatFormatting.LIGHT_PURPLE)
        }, false)
        return shinies
    }

    private fun reload(source: CommandSourceStack): Int {
        CatchCombo.load()
        ShinyOdds.load()
        Charms.load()
        InstantFlee.load()
        source.sendSuccess({ Component.translatable("$KEY.reloaded") }, true)
        return 1
    }
}
