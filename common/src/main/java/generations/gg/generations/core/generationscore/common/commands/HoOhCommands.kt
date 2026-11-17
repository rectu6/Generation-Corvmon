package generations.gg.generations.core.generationscore.common.commands

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.permission.Permission
import com.cobblemon.mod.common.api.permission.PermissionLevel
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.hooh.HoOhMath
import generations.gg.generations.core.generationscore.common.world.hooh.HoOhPassage
import generations.gg.generations.core.generationscore.common.world.hooh.HoOhPassageData
import generations.gg.generations.core.generationscore.common.world.hooh.HoOhRules
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * /generations hooh … (HOOH-PLAN.md § 12), for the development only: permission
 * generations_core.command.hooh (level 2 by default).
 *
 * - trigger [player]: a whole passage now (the scene, the pursuit, the feather), as a test (the
 *   counters and the delay untouched; the feather is given);
 * - draw [player]: one draw now with the configured chance (a passage when it succeeds, for real);
 * - flight [player]: the scene alone (Ho-Oh flies away after it);
 * - pursuit [player]: the pursuit without the scene;
 * - feather drop [player]: only the feather's fall in front of the player; feather give [player];
 * - why [player]: what keeps a passage from happening here now;
 * - info [player]: active play, delay, draws, passages, a pending feather;
 * - active <hours> [player]: sets the active play since the last passage;
 * - odds: the configured rarity, its mean and median;
 * - status: the passages under way; stop [player]; reset [player]; reload.
 */
object HoOhCommands {
    private const val KEY = "generations_core.hooh.command"

    private val PERMISSION = object : Permission {
        override val identifier: ResourceLocation = GenerationsCore.id("command.hooh")
        override val literal: String = "generations_core.command.hooh"
        override val level: PermissionLevel = PermissionLevel.CHEAT_COMMANDS_AND_COMMAND_BLOCKS
    }

    private fun allowed(source: CommandSourceStack) =
        runCatching { Cobblemon.permissionValidator.hasPermission(source, PERMISSION) }.getOrElse { source.hasPermission(2) }

    private fun key(name: String, vararg args: Any): MutableComponent = Component.translatable("$KEY.$name", *args)

    private fun ok(source: CommandSourceStack, message: MutableComponent): Int {
        source.sendSuccess({ message.withStyle(ChatFormatting.GOLD) }, false)
        return 1
    }

    private fun fail(source: CommandSourceStack, message: Component): Int {
        source.sendFailure(message)
        return 0
    }

    /** The node, then the same with a player argument. */
    private fun <T : ArgumentBuilder<CommandSourceStack, T>> withPlayer(node: T, run: (CommandContext<CommandSourceStack>, ServerPlayer) -> Int): T {
        node.executes { run(it, it.source.playerOrException) }
        node.then(Commands.argument("player", EntityArgument.player()).executes { run(it, EntityArgument.getPlayer(it, "player")) })
        return node
    }

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("generations").then(
                Commands.literal("hooh").requires(::allowed)
                    .then(withPlayer(Commands.literal("trigger")) { ctx, p -> start(ctx.source, p, HoOhPassage.Mode.FULL) })
                    .then(withPlayer(Commands.literal("flight")) { ctx, p -> start(ctx.source, p, HoOhPassage.Mode.FLIGHT) })
                    .then(withPlayer(Commands.literal("pursuit")) { ctx, p -> start(ctx.source, p, HoOhPassage.Mode.PURSUIT) })
                    .then(withPlayer(Commands.literal("draw")) { ctx, p -> draw(ctx.source, p) })
                    .then(withPlayer(Commands.literal("why")) { ctx, p -> why(ctx.source, p) })
                    .then(withPlayer(Commands.literal("info")) { ctx, p -> info(ctx.source, p) })
                    .then(withPlayer(Commands.literal("stop")) { ctx, p -> stop(ctx.source, p) })
                    .then(withPlayer(Commands.literal("reset")) { ctx, p -> reset(ctx.source, p) })
                    .then(
                        Commands.literal("feather")
                            .then(withPlayer(Commands.literal("drop")) { ctx, p -> start(ctx.source, p, HoOhPassage.Mode.FEATHER) })
                            .then(withPlayer(Commands.literal("give")) { ctx, p -> give(ctx.source, p) }),
                    )
                    .then(
                        Commands.literal("active").then(
                            withPlayer(Commands.argument("hours", DoubleArgumentType.doubleArg(0.0, 1000.0))) { ctx, p ->
                                HoOhPassage.setActive(ctx.source.server, p.uuid, DoubleArgumentType.getDouble(ctx, "hours"))
                                ok(ctx.source, key("active", p.scoreboardName, "%.2f".format(DoubleArgumentType.getDouble(ctx, "hours"))))
                            },
                        ),
                    )
                    .then(Commands.literal("odds").executes { odds(it.source) })
                    .then(Commands.literal("status").executes { status(it.source) })
                    .then(Commands.literal("reload").executes {
                        HoOhPassage.reload()
                        ok(it.source, key("reloaded"))
                    }),
            ),
        )
    }

    private fun start(source: CommandSourceStack, player: ServerPlayer, mode: HoOhPassage.Mode): Int {
        HoOhPassage.whyNot(player, ignoreLimit = true)?.let { reason ->
            if (reason == "already" || reason == "battle" || reason == "cinematic" || reason == "journey" || reason == "mew") return fail(source, key("cannot", key("why.$reason")))
        }
        if (!HoOhPassage.start(player, mode, test = true)) return fail(source, key("cannot", key("why.already")))
        return ok(source, key("started.${mode.name.lowercase()}", player.scoreboardName))
    }

    private fun draw(source: CommandSourceStack, player: ServerPlayer): Int {
        HoOhPassage.whyNot(player)?.let { return fail(source, key("cannot", key("why.$it"))) }
        val (roll, won) = HoOhPassage.drawNow(player)
        return ok(source, key(if (won) "draw.won" else "draw.lost", "%.4f".format(roll * 100.0), "%.4f".format(HoOhPassage.rules.chance * 100.0)))
    }

    private fun why(source: CommandSourceStack, player: ServerPlayer): Int {
        val reason = HoOhPassage.whyNot(player) ?: return ok(source, key("why.none", player.scoreboardName))
        return ok(source, key("why.head", player.scoreboardName, key("why.$reason")))
    }

    private fun info(source: CommandSourceStack, player: ServerPlayer): Int {
        val record = HoOhPassageData.get(source.server, player.uuid)
        val rules = HoOhPassage.rules
        if (record == null) return ok(source, key("info.never", player.scoreboardName))
        val active = record.activeTicks / HoOhRules.TICKS_PER_HOUR
        val next = (rules.intervalTicks - record.sinceDraw).coerceAtLeast(0L) / 1200.0
        val waitMs = if (record.lastPassageMs == 0L) 0L else (record.lastPassageMs + rules.cooldownMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val eligible = HoOhMath.eligible(record, rules, System.currentTimeMillis())
        ok(source, key("info.head", player.scoreboardName))
        source.sendSuccess({ key("info.active", "%.2f".format(active), "%.2f".format(rules.minActiveTicks / HoOhRules.TICKS_PER_HOUR), "%.1f".format(next)) }, false)
        source.sendSuccess({ key("info.delay", "%.1f".format(waitMs / 3_600_000.0), if (eligible) key("info.yes") else key("info.no")) }, false)
        source.sendSuccess({ key("info.counts", record.draws, record.passages, record.successes) }, false)
        source.sendSuccess({ key("info.afk", HoOhPassage.afk(player).name) }, false)
        if (record.pendingFeather) source.sendSuccess({ key("info.pending").withStyle(ChatFormatting.YELLOW) }, false)
        HoOhPassage.session(player.uuid)?.let { s -> source.sendSuccess({ key("info.session", s.id, s.mode.name, s.phase.name) }, false) }
        return 1
    }

    private fun odds(source: CommandSourceStack): Int {
        val c = HoOhPassage.config
        val r = HoOhPassage.rules
        ok(source, key("odds.head"))
        source.sendSuccess({ key("odds.rules", "%.3f".format(c.chancePercent), "%.1f".format(c.checkIntervalMinutes), "%.1f".format(c.minActiveHours), "%.1f".format(c.cooldownHours), c.maxConcurrent) }, false)
        source.sendSuccess({ key("odds.wait", "%.1f".format(HoOhMath.meanHours(r)), "%.1f".format(HoOhMath.quantileHours(r, 0.5)), "%.1f".format(HoOhMath.quantileHours(r, 0.9))) }, false)
        source.sendSuccess({ key("odds.within", "%.1f".format(100.0 * HoOhMath.within(r, 5.0)), "%.1f".format(100.0 * HoOhMath.within(r, 24.0)), "%.1f".format(100.0 * (1.0 - HoOhMath.within(r, 50.0)))) }, false)
        source.sendSuccess({ key("odds.places", c.dimensions.joinToString(", "), c.excludedBiomes.joinToString(", ").ifEmpty { "-" }, if (c.enabled) key("info.yes") else key("info.no")) }, false)
        return 1
    }

    private fun status(source: CommandSourceStack): Int {
        val sessions = HoOhPassage.sessions()
        ok(source, key("status", sessions.size, HoOhPassage.config.maxConcurrent))
        for (s in sessions) {
            val name = source.server.playerList.getPlayer(s.player)?.scoreboardName ?: s.player.toString()
            source.sendSuccess({ Component.literal("  #${s.id} $name ${s.mode} ${s.phase}${if (s.test) " (test)" else ""}") }, false)
        }
        return 1
    }

    private fun stop(source: CommandSourceStack, player: ServerPlayer): Int =
        if (HoOhPassage.stop(player)) ok(source, key("stopped", player.scoreboardName)) else fail(source, key("none", player.scoreboardName))

    private fun reset(source: CommandSourceStack, player: ServerPlayer): Int {
        HoOhPassage.reset(source.server, player.uuid)
        return ok(source, key("reset", player.scoreboardName))
    }

    private fun give(source: CommandSourceStack, player: ServerPlayer): Int {
        HoOhPassage.giveFeather(player)
        return ok(source, key("given", player.scoreboardName))
    }
}
