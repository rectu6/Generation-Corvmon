package generations.gg.generations.core.generationscore.common.commands

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraSpaceConfig
import generations.gg.generations.core.generationscore.common.world.ultraspace.encounter.UltraEncounters
import generations.gg.generations.core.generationscore.common.world.ultraspace.spawning.UltraSpawns
import generations.gg.generations.core.generationscore.common.world.ultraspace.spawning.UltraSpawnsConfig
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTransfers
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import java.util.Locale
import java.util.Random

/**
 * /ultrawarp encounter and /ultrawarp spawns (phase 5), for the tests (operators):
 * - encounter simulate <light-years> <portal level> [draws]: the three chances drawn many
 *   times where the player stands (an Ultra world's biome, else the Ultra Jungle), the
 *   rates found beside the configured ones;
 * - encounter test <light-years> <portal level>: a real arrival encounter near the player;
 * - encounter pending: the encounters drawn and not appeared yet, and the last ones;
 * - spawns: the weather that changes the spawns here, the buckets, the Ultra Beasts.
 * Cobblemon's /checkspawn shows the chances of the Pokémon where one stands (rules included).
 */
object UltraEncounterCommands {
    private const val KEY = "generations_core.ultra_encounter.command"

    fun encounter(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("encounter")
        .then(
            Commands.literal("simulate").then(
                Commands.argument("lightYears", DoubleArgumentType.doubleArg(0.0, 100000.0)).then(
                    Commands.argument("level", IntegerArgumentType.integer(1, 4))
                        .executes { simulate(it, 100000) }
                        .then(Commands.argument("draws", IntegerArgumentType.integer(1, 5_000_000)).executes { simulate(it, IntegerArgumentType.getInteger(it, "draws")) })
                )
            )
        )
        .then(
            Commands.literal("test").then(
                Commands.argument("lightYears", DoubleArgumentType.doubleArg(0.0, 100000.0)).then(
                    Commands.argument("level", IntegerArgumentType.integer(1, 4)).executes { test(it) }
                )
            )
        )
        .then(Commands.literal("pending").executes { pending(it.source) })

    fun spawns(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("spawns").executes { spawns(it.source) }

    private fun place(source: CommandSourceStack): Pair<String, String> {
        val level = source.level
        val world = UltraWorlds.idOf(level.dimension())
        if (world == null) return "ultra_jungle" to "ultra_jungle/giant_jungle"
        val biome = level.getBiome(net.minecraft.core.BlockPos.containing(source.position)).unwrapKey().map { it.location().path }.orElse("")
        return world to biome
    }

    private fun percent(value: Double): String = String.format(Locale.ROOT, "%.4f %%", value * 100.0)

    private fun simulate(context: CommandContext<CommandSourceStack>, draws: Int): Int {
        val source = context.source
        val lightYears = DoubleArgumentType.getDouble(context, "lightYears")
        val level = IntegerArgumentType.getInteger(context, "level")
        val (world, biome) = place(source)
        val tables = UltraSpaceConfig.ride.encounters
        val random = Random()
        var beasts = 0
        var beastShiny = 0
        var ordinaryShiny = 0
        var none = 0
        val species = HashMap<String, Int>()
        repeat(draws) {
            val result = UltraEncounters.drawFor(world, biome, lightYears, level, random)
            if (result == null) { none++; return@repeat }
            species.merge(result.species, 1, Int::plus)
            if (result.ultraBeast) { beasts++; if (result.shiny) beastShiny++ } else if (result.shiny) ordinaryShiny++
        }
        val ordinary = draws - beasts - none
        source.sendSuccess({ Component.translatable("$KEY.simulate", draws, "%.0f".format(lightYears), level, "$world/${biome.substringAfter('/')}").withStyle(ChatFormatting.AQUA) }, false)
        source.sendSuccess({
            Component.translatable("$KEY.beast", percent(beasts.toDouble() / draws), percent(tables.chance(tables.ultraBeast, lightYears, level).toDouble()))
        }, false)
        source.sendSuccess({
            Component.translatable("$KEY.ordinary_shiny", percent(if (ordinary > 0) ordinaryShiny.toDouble() / ordinary else 0.0), percent(tables.chance(tables.ordinaryShiny, lightYears, level).toDouble()))
        }, false)
        source.sendSuccess({
            Component.translatable("$KEY.beast_shiny", percent(if (beasts > 0) beastShiny.toDouble() / beasts else 0.0), percent(tables.chance(tables.ultraBeastShiny, lightYears, level).toDouble()), beasts)
        }, false)
        if (none > 0) source.sendSuccess({ Component.translatable("$KEY.none", none).withStyle(ChatFormatting.RED) }, false)
        val top = species.entries.sortedByDescending { it.value }.take(12).joinToString { "${it.key} ${percent(it.value.toDouble() / draws)}" }
        source.sendSuccess({ Component.literal(top).withStyle(ChatFormatting.GRAY) }, false)
        return 1
    }

    private fun test(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val player = source.playerOrException
        val entry = UltraEncounters.test(player, DoubleArgumentType.getDouble(context, "lightYears"), IntegerArgumentType.getInteger(context, "level"))
        if (entry == null) {
            source.sendFailure(Component.translatable("$KEY.not_ultra"))
            return 0
        }
        source.sendSuccess({
            if (entry.species.isEmpty()) Component.translatable("$KEY.none", 1)
            else Component.translatable("$KEY.drawn", entry.species, entry.level, if (entry.ultraBeast) "UB" else "-", if (entry.shiny) "shiny" else "-")
        }, true)
        return 1
    }

    private fun pending(source: CommandSourceStack): Int {
        val pending = UltraEncounters.pending()
        source.sendSuccess({ Component.translatable("$KEY.pending", pending.size).withStyle(ChatFormatting.AQUA) }, false)
        for (entry in UltraEncounters.last(8)) {
            val player = source.server.playerList.getPlayer(entry.player)?.gameProfile?.name ?: entry.player.toString().take(8)
            source.sendSuccess({
                Component.literal(
                    "${entry.state} $player ${entry.world}/${entry.biome.substringAfter('/')} ${"%.0f".format(entry.lightYears)} ly L${entry.portalLevel}: " +
                        "${entry.species.ifEmpty { "-" }} ${entry.level}${if (entry.ultraBeast) " UB" else ""}${if (entry.shiny) " shiny" else ""}${if (entry.test) " (test)" else ""}"
                )
            }, false)
        }
        return 1
    }

    private fun spawns(source: CommandSourceStack): Int {
        val level = source.level
        val world = UltraWorlds.idOf(level.dimension())
        val weather = UltraSpawns.weather(level)
        source.sendSuccess({
            Component.translatable(
                "$KEY.spawns", world?.let { UltraWarpTransfers.worldName(it) } ?: Component.literal(level.dimension().location().toString()),
                weather.ifEmpty { "-" },
                UltraSpawnsConfig.settings.dangerousBuckets[weather]?.entries?.joinToString { "${it.key} x${it.value}" } ?: "-",
            ).withStyle(ChatFormatting.AQUA)
        }, false)
        for ((id, minutes, alive) in UltraSpawns.beasts(source.server)) {
            source.sendSuccess({ Component.translatable("$KEY.beasts", UltraWarpTransfers.worldName(id), alive, if (minutes < 0) "-" else minutes.toString()) }, false)
        }
        return 1
    }
}
