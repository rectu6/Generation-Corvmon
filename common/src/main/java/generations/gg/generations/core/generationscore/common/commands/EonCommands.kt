package generations.gg.generations.core.generationscore.common.commands

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.permission.Permission
import com.cobblemon.mod.common.api.permission.PermissionLevel
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.eon.EonData
import generations.gg.generations.core.generationscore.common.world.eon.EonManifest
import generations.gg.generations.core.generationscore.common.world.eon.EonMath
import generations.gg.generations.core.generationscore.common.world.eon.EonSummon
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * /generations eon … (EON-PLAN.md § 8), for the administrators only: permission
 * generations_core.command.eon (level 2). [player] is optional (yourself).
 *
 * - trigger: a real manifestation now (a fragment if won); test: one without any reward or
 *   counter; pursuit: the pursuit without the signal (test);
 * - succeed: the manifestation goes to its core; fail: it fails;
 * - give [n]: Enigma Fragments; stone: a charged Enigma Stone (to try the dialogue);
 * - info, why: the exploration counted and what blocks it here; cooldown clear: the regions and
 *   distances of the rewards forgotten; draw: a real draw now; reset: the counters;
 * - odds: the settings and the hours they give; status: the manifestations on the server;
 *   summons: the summonings waiting for their Pokémon; reload: eon.json read again.
 *
 * The scenes alone: /generations cinematic latias|latios [real] (CinematicCommands).
 */
object EonCommands {
    private const val KEY = "generations_core.eon.command"

    private val PERMISSION = object : Permission {
        override val identifier: ResourceLocation = GenerationsCore.id("command.eon")
        override val literal: String = "generations_core.command.eon"
        override val level: PermissionLevel = PermissionLevel.CHEAT_COMMANDS_AND_COMMAND_BLOCKS
    }

    private fun allowed(source: CommandSourceStack) =
        runCatching { Cobblemon.permissionValidator.hasPermission(source, PERMISSION) }.getOrElse { source.hasPermission(2) }

    private fun key(name: String, vararg args: Any): MutableComponent = Component.translatable("$KEY.$name", *args)

    private fun ok(source: CommandSourceStack, message: Component): Int {
        source.sendSuccess({ message.copy().withStyle(ChatFormatting.LIGHT_PURPLE) }, false)
        return 1
    }

    private fun fail(source: CommandSourceStack, message: Component): Int {
        source.sendFailure(message)
        return 0
    }

    private fun <T : ArgumentBuilder<CommandSourceStack, T>> withPlayer(node: T, run: (CommandContext<CommandSourceStack>, ServerPlayer) -> Int): T {
        node.executes { run(it, it.source.playerOrException) }
        node.then(Commands.argument("player", EntityArgument.player()).executes { run(it, EntityArgument.getPlayer(it, "player")) })
        return node
    }

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("generations").then(
                Commands.literal("eon").requires(::allowed)
                    .then(withPlayer(Commands.literal("trigger")) { ctx, p -> start(ctx.source, p, EonManifest.Mode.FULL, false) })
                    .then(withPlayer(Commands.literal("test")) { ctx, p -> start(ctx.source, p, EonManifest.Mode.FULL, true) })
                    .then(withPlayer(Commands.literal("pursuit")) { ctx, p -> start(ctx.source, p, EonManifest.Mode.PURSUIT, true) })
                    .then(withPlayer(Commands.literal("succeed")) { ctx, p ->
                        if (EonManifest.succeed(p)) ok(ctx.source, key("succeed", p.scoreboardName)) else fail(ctx.source, key("none", p.scoreboardName))
                    })
                    .then(withPlayer(Commands.literal("fail")) { ctx, p ->
                        if (EonManifest.failNow(p)) ok(ctx.source, key("failed", p.scoreboardName)) else fail(ctx.source, key("none", p.scoreboardName))
                    })
                    .then(
                        withPlayer(Commands.literal("give")) { ctx, p -> give(ctx.source, p, 1) }
                            .then(
                                withPlayer(Commands.argument("count", IntegerArgumentType.integer(1, 64))) { ctx, p ->
                                    give(ctx.source, p, IntegerArgumentType.getInteger(ctx, "count"))
                                },
                            ),
                    )
                    .then(withPlayer(Commands.literal("stone")) { ctx, p -> stone(ctx.source, p) })
                    .then(withPlayer(Commands.literal("info")) { ctx, p -> info(ctx.source, p) })
                    .then(withPlayer(Commands.literal("why")) { ctx, p -> why(ctx.source, p) })
                    .then(withPlayer(Commands.literal("draw")) { ctx, p -> draw(ctx.source, p) })
                    .then(withPlayer(Commands.literal("reset")) { ctx, p ->
                        EonManifest.reset(ctx.source.server, p.uuid)
                        ok(ctx.source, key("reset", p.scoreboardName))
                    })
                    .then(
                        Commands.literal("cooldown").then(
                            withPlayer(Commands.literal("clear")) { ctx, p ->
                                EonManifest.clearCooldown(ctx.source.server, p.uuid)
                                ok(ctx.source, key("cooldown_cleared", p.scoreboardName))
                            },
                        ),
                    )
                    .then(Commands.literal("odds").executes { odds(it.source) })
                    .then(Commands.literal("status").executes { status(it.source) })
                    .then(Commands.literal("summons").executes { summons(it.source) })
                    .then(Commands.literal("reload").executes {
                        EonManifest.reload()
                        ok(it.source, key("reloaded"))
                    }),
            ),
        )
    }

    private fun start(source: CommandSourceStack, player: ServerPlayer, mode: EonManifest.Mode, test: Boolean): Int {
        if (EonManifest.isRunning(player.uuid)) return fail(source, key("already", player.scoreboardName))
        if (!EonManifest.start(player, mode, test)) return fail(source, key("no_place"))
        return ok(source, key(if (test) "started.test" else "started.real", player.scoreboardName))
    }

    private fun give(source: CommandSourceStack, player: ServerPlayer, count: Int): Int {
        repeat(count) { EonManifest.giveFragment(player) }
        return ok(source, key("given", count, player.scoreboardName))
    }

    private fun stone(source: CommandSourceStack, player: ServerPlayer): Int {
        val stack = ItemStack(GenerationsItems.ENIGMA_STONE.value())
        stack.damageValue = stack.maxDamage
        if (!player.inventory.add(stack)) player.drop(stack, false)
        return ok(source, key("stone", player.scoreboardName))
    }

    private fun why(source: CommandSourceStack, player: ServerPlayer): Int {
        val reason = EonManifest.whyNot(player) ?: return ok(source, key("why.none", player.scoreboardName))
        return ok(source, key("why.head", player.scoreboardName, key("why.$reason")))
    }

    private fun draw(source: CommandSourceStack, player: ServerPlayer): Int {
        if (EonManifest.isRunning(player.uuid)) return fail(source, key("already", player.scoreboardName))
        val (roll, chance, won) = EonManifest.drawNow(player)
        return ok(source, key(if (won) "draw.won" else "draw.lost", "%.2f".format(roll * 100.0), "%.2f".format(chance * 100.0)))
    }

    private fun minutes(ticks: Long): String = "%.1f".format(ticks / 1200.0)

    private fun info(source: CommandSourceStack, player: ServerPlayer): Int {
        val record = EonData.get(source.server, player.uuid) ?: return ok(source, key("info.never", player.scoreboardName))
        val rules = EonManifest.rules
        ok(source, key("info.head", player.scoreboardName))
        val next = (EonMath.threshold(record, rules) - record.counted).coerceAtLeast(0L)
        source.sendSuccess({ key("info.counted", minutes(record.counted), minutes(EonMath.threshold(record, rules)), minutes(next), "%.1f".format(record.totalTicks / 72_000.0)) }, false)
        source.sendSuccess({ key("info.presence", minutes(record.presence), minutes(rules.presenceTicks)) }, false)
        source.sendSuccess({ key("info.interval", record.intervalBlocks.toInt(), rules.minIntervalBlocks.toInt(), EonMath.newCells(record), rules.minIntervalCells) }, false)
        val waiting = EonMath.waiting(record, rules)
        if (EonMath.due(record, rules) && waiting != null) source.sendSuccess({ key("info.waits", key("waiting.$waiting")).withStyle(ChatFormatting.YELLOW) }, false)
        if (record.owed) source.sendSuccess({ key("info.owed").withStyle(ChatFormatting.YELLOW) }, false)
        val level = player.serverLevel()
        val biome = EonManifest.biomeId(level, player.blockPosition())
        val chance = EonMath.chance(record, biome, rules) * 100.0
        source.sendSuccess({ key("info.here", biome, "%.1f".format(chance), EonManifest.whyNot(player)?.let { key("why.$it") } ?: key("why.counts")) }, false)
        val now = System.currentTimeMillis()
        for (w in record.rewards.takeLast(4)) {
            val hours = (now - w.timeMs) / 3_600_000.0
            val label = if (w.timeMs == 0L) key("info.cleared") else Component.literal("%.1f h".format(hours))
            source.sendSuccess({ key("info.reward", w.biome, w.x.toInt(), w.z.toInt(), label) }, false)
        }
        source.sendSuccess({ key("info.counts", record.draws, record.manifestations, record.successes, record.failures, record.fragments) }, false)
        source.sendSuccess({ key("info.afk", EonManifest.afk(player).name) }, false)
        if (record.pending > 0) source.sendSuccess({ key("info.pending", record.pending).withStyle(ChatFormatting.YELLOW) }, false)
        EonManifest.session(player.uuid)?.let { s -> source.sendSuccess({ key("info.session", s.id, s.machine.phase.name, if (s.test) "test" else "real") }, false) }
        return 1
    }

    private fun odds(source: CommandSourceStack): Int {
        val c = EonManifest.config
        val rules = EonManifest.rules
        ok(source, key("odds.head"))
        source.sendSuccess({ key("odds.settings", "%.1f".format(c.chancePercent), "%.1f".format(c.firstCheckMinutes), "%.1f".format(c.checkIntervalMinutes), "%.2f".format(c.repeatBiomeFactor), c.biomeMemory) }, false)
        source.sendSuccess({ key("odds.rules", "%.1f".format(c.minPresenceMinutes), c.minIntervalBlocks.toInt(), c.minIntervalCells, c.regionSize, "%.1f".format(c.regionCooldownHours), c.minRewardDistance.toInt(), c.maxConcurrent) }, false)
        for ((profile, pair) in listOf("good" to (0.95 to 0.05), "typical" to (0.85 to 0.10), "weak" to (0.70 to 0.20))) {
            val o = EonMath.odds(rules, pair.first, pair.second)
            source.sendSuccess({ key("odds.profile", key("odds.$profile"), "%.1f".format(o.mean), "%.1f".format(o.median), "%.1f".format(o.p10), "%.1f".format(o.p90)) }, false)
        }
        return 1
    }

    private fun status(source: CommandSourceStack): Int {
        val all = EonManifest.sessions()
        ok(source, key("status", all.size, EonManifest.config.maxConcurrent))
        for (s in all) {
            val name = source.server.playerList.getPlayer(s.player)?.scoreboardName ?: s.player.toString()
            source.sendSuccess({ Component.literal("  $name: ${s.machine.phase.name}${if (s.test) " (test)" else ""}") }, false)
        }
        return 1
    }

    private fun summons(source: CommandSourceStack): Int {
        val lines = EonSummon.pending(source.server)
        if (lines.isEmpty()) return ok(source, key("summons_none"))
        for (line in lines) source.sendSuccess({ Component.literal("  $line") }, false)
        return lines.size
    }
}
