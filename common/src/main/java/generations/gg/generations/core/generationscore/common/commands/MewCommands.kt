package generations.gg.generations.core.generationscore.common.commands

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.permission.Permission
import com.cobblemon.mod.common.api.permission.PermissionLevel
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.mew.MewGame
import generations.gg.generations.core.generationscore.common.world.mew.MewHunt
import generations.gg.generations.core.generationscore.common.world.mew.MewReward
import generations.gg.generations.core.generationscore.common.world.mew.MewHuntData
import generations.gg.generations.core.generationscore.common.world.mew.MewMath
import generations.gg.generations.core.generationscore.common.world.mew.MewRecord
import generations.gg.generations.core.generationscore.common.world.mew.MewState
import generations.gg.generations.core.generationscore.common.world.mew.MewZone
import generations.gg.generations.core.generationscore.common.world.mew.MewZones
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom

/**
 * /generations mew (operators, Cobblemon permission generations_core.command.mew, level 2),
 * MEW-PLAN.md § 14, build 59 part:
 * - zone test [radius]: a test island around the player, in memory (lost at the next start);
 *   zone add <id> <radius>: a real island (a circle around the player) written to mew_zones.json;
 *   zone remove <id>, zone list, zone here (the zone, the biome and whether the hunt counts there),
 *   zone reload;
 * - hunt get|set|add <hours> [player]: the real counter (set and add plan the next draw from it,
 *   without drawing);
 * - draw [player]: a draw now on the real progression, with the chance of the current hours;
 * - info [player], reset [player] (a new hunt from zero, real progression), reload (mew.json and
 *   mew_zones.json).
 * - manifest <a|b|c|d> [player], minigame [round], cinematic, spawn: test mode (testMode in mew.json),
 *   on a copy in memory: the real progress is never touched, the test Mew is not marked;
 *   cinematic real and spawn real: the real ones, from WON only;
 * - unlock, fail, cooldown <minutes> [player]: the real progress (they say so);
 * - giveup: the player gives up their challenge (no permission).
 * Registered by CommandsMixin.
 */
object MewCommands {
    private const val KEY = "generations_core.mew.command"

    private val PERMISSION = object : Permission {
        override val identifier: ResourceLocation = GenerationsCore.id("command.mew")
        override val literal: String = "generations_core.command.mew"
        override val level: PermissionLevel = PermissionLevel.CHEAT_COMMANDS_AND_COMMAND_BLOCKS
    }

    private fun allowed(source: CommandSourceStack) =
        runCatching { Cobblemon.permissionValidator.hasPermission(source, PERMISSION) }.getOrElse { source.hasPermission(2) }

    private fun num(value: Double, decimals: Int = 1): String = "%.${decimals}f".format(Locale.ROOT, value)

    private fun key(name: String, vararg args: Any): MutableComponent = Component.translatable("$KEY.$name", *args)

    /** The subcommand alone, or followed by a player. */
    private fun <T : ArgumentBuilder<CommandSourceStack, T>> withPlayer(node: T, run: (CommandContext<CommandSourceStack>, ServerPlayer?) -> Int): T =
        node.executes { run(it, null) }
            .then(Commands.argument("player", EntityArgument.player()).executes { run(it, EntityArgument.getPlayer(it, "player")) })

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val zoneId = Commands.argument("id", StringArgumentType.word())
            .suggests { _, builder -> SharedSuggestionProvider.suggest(MewZones.all().map { it.id }, builder) }
        val zone = Commands.literal("zone").requires(::allowed)
            .then(
                Commands.literal("test").executes { zoneTest(it.source, 48) }
                    .then(Commands.argument("radius", IntegerArgumentType.integer(4, 512)).executes { zoneTest(it.source, IntegerArgumentType.getInteger(it, "radius")) })
            )
            .then(
                Commands.literal("add").then(
                    Commands.argument("id", StringArgumentType.word()).then(
                        Commands.argument("radius", IntegerArgumentType.integer(4, 10_000))
                            .executes { zoneAdd(it.source, StringArgumentType.getString(it, "id"), IntegerArgumentType.getInteger(it, "radius")) }
                    )
                )
            )
            .then(Commands.literal("remove").then(zoneId.executes { zoneRemove(it.source, StringArgumentType.getString(it, "id")) }))
            .then(Commands.literal("list").executes { zoneList(it.source) })
            .then(Commands.literal("here").executes { zoneHere(it.source) })
            .then(Commands.literal("reload").executes { zoneReload(it.source) })

        val hunt = Commands.literal("hunt").requires(::allowed)
            .then(withPlayer(Commands.literal("get")) { ctx, player -> huntGet(ctx.source, player) })
            .then(
                Commands.literal("set").then(
                    withPlayer(Commands.argument("hours", DoubleArgumentType.doubleArg(0.0, 1000.0))) { ctx, player ->
                        huntSet(ctx.source, player, DoubleArgumentType.getDouble(ctx, "hours"), add = false)
                    }
                )
            )
            .then(
                Commands.literal("add").then(
                    withPlayer(Commands.argument("hours", DoubleArgumentType.doubleArg(-1000.0, 1000.0))) { ctx, player ->
                        huntSet(ctx.source, player, DoubleArgumentType.getDouble(ctx, "hours"), add = true)
                    }
                )
            )

        dispatcher.register(
            Commands.literal("generations").then(
                Commands.literal("mew")
                    .then(zone)
                    .then(hunt)
                    .then(withPlayer(Commands.literal("draw").requires(::allowed)) { ctx, player -> draw(ctx.source, player) })
                    .then(withPlayer(Commands.literal("info").requires(::allowed)) { ctx, player -> info(ctx.source, player) })
                    .then(withPlayer(Commands.literal("reset").requires(::allowed)) { ctx, player -> reset(ctx.source, player) })
                    .then(Commands.literal("reload").requires(::allowed).executes { reload(it.source) })
                    .then(
                        Commands.literal("manifest").requires(::allowed).then(
                            Commands.argument("type", StringArgumentType.word())
                                .suggests { _, builder -> SharedSuggestionProvider.suggest(TYPES, builder) }
                                .executes { manifest(it.source, StringArgumentType.getString(it, "type"), null) }
                                .then(Commands.argument("player", EntityArgument.player()).executes { manifest(it.source, StringArgumentType.getString(it, "type"), EntityArgument.getPlayer(it, "player")) })
                        )
                    )
                    .then(withPlayer(Commands.literal("unlock").requires(::allowed)) { ctx, player -> unlock(ctx.source, player) })
                    .then(
                        Commands.literal("minigame").requires(::allowed).executes { minigame(it.source, 0) }
                            .then(Commands.argument("round", IntegerArgumentType.integer(1, 3)).executes { minigame(it.source, IntegerArgumentType.getInteger(it, "round")) })
                    )
                    .then(withPlayer(Commands.literal("fail").requires(::allowed)) { ctx, player -> failChallenge(ctx.source, player) })
                    .then(
                        Commands.literal("cooldown").requires(::allowed).then(
                            withPlayer(Commands.argument("minutes", DoubleArgumentType.doubleArg(0.0, 600.0))) { ctx, player -> cooldown(ctx.source, player, DoubleArgumentType.getDouble(ctx, "minutes")) }
                        )
                    )
                    .then(Commands.literal("cinematic").requires(::allowed).executes { cinematic(it.source, false) }.then(Commands.literal("real").executes { cinematic(it.source, true) }))
                    .then(Commands.literal("spawn").requires(::allowed).executes { spawn(it.source, false) }.then(Commands.literal("real").executes { spawn(it.source, true) }))
                    .then(Commands.literal("giveup").executes { giveUp(it.source) })
            )
        )
    }

    private fun fail(source: CommandSourceStack, message: Component): Int {
        source.sendFailure(message)
        return 0
    }

    private fun ok(source: CommandSourceStack, message: MutableComponent, broadcast: Boolean = false): Int {
        source.sendSuccess({ message }, broadcast)
        return 1
    }

    private fun target(source: CommandSourceStack, player: ServerPlayer?): ServerPlayer = player ?: source.playerOrException

    // ---------------------------------------------------------------- zones

    private fun zoneTest(source: CommandSourceStack, radius: Int): Int {
        val player = source.playerOrException
        val id = "test_" + player.scoreboardName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]"), "_")
        MewZones.putTest(MewZone.circle(id, player.level().dimension().location().toString(), player.x, player.z, radius.toDouble()))
        return ok(source, key("zone.test", id, radius).withStyle(ChatFormatting.AQUA))
    }

    private fun zoneAdd(source: CommandSourceStack, id: String, radius: Int): Int {
        val player = source.playerOrException
        if (!MewZone.validId(id)) return fail(source, key("zone.bad_id", id))
        if (MewZones.exists(id)) return fail(source, key("zone.exists", id))
        val x = Math.floor(player.x) + 0.5
        val z = Math.floor(player.z) + 0.5
        if (!MewZones.add(MewZone.circle(id, player.level().dimension().location().toString(), x, z, radius.toDouble()))) {
            return fail(source, key("zone.write_failed"))
        }
        return ok(source, key("zone.added", id, radius, num(x), num(z)).withStyle(ChatFormatting.AQUA), broadcast = true)
    }

    private fun zoneRemove(source: CommandSourceStack, id: String): Int = when (MewZones.remove(id)) {
        null -> fail(source, key("zone.unknown", id))
        true -> ok(source, key("zone.removed_test", id))
        false -> ok(source, key("zone.removed", id), broadcast = true)
    }

    private fun describe(zone: MewZone): MutableComponent {
        val status = when {
            zone.problem.isNotEmpty() -> key("zone.invalid", zone.problem).withStyle(ChatFormatting.RED)
            MewZones.isTest(zone) -> key("zone.test_status").withStyle(ChatFormatting.YELLOW)
            zone.enabled -> key("zone.on").withStyle(ChatFormatting.GREEN)
            else -> key("zone.off").withStyle(ChatFormatting.DARK_GRAY)
        }
        val shape = when {
            zone.problem.isNotEmpty() -> Component.literal(zone.shape)
            zone.shape == MewZone.SHAPE_CIRCLE -> key("zone.circle", num(zone.radius!!, 0), num(zone.centerX!!, 0), num(zone.centerZ!!, 0))
            else -> key("zone.box", num(zone.minX!!, 0), num(zone.minZ!!, 0), num(zone.maxX!!, 0), num(zone.maxZ!!, 0))
        }
        val biomes = if (zone.biomes.isEmpty()) key("zone.all_biomes") else key("zone.biomes", zone.biomes.joinToString(", "))
        return key("zone.line", zone.id, status, shape, zone.dimension, biomes).withStyle(ChatFormatting.GRAY)
    }

    private fun zoneList(source: CommandSourceStack): Int {
        val zones = MewZones.all()
        if (zones.isEmpty()) return ok(source, key("zone.none"))
        source.sendSuccess({ key("zone.list", zones.size, MewZones.usableCount()) }, false)
        zones.forEach { zone -> source.sendSuccess({ describe(zone) }, false) }
        return zones.size
    }

    private fun zoneHere(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val holder = player.level().getBiome(player.blockPosition())
        val biome = holder.unwrapKey().map { it.location().toString() }.orElse("?")
        source.sendSuccess({ key("zone.here_biome", biome, player.level().dimension().location().toString()) }, false)
        val inside = MewZones.containing(player)
        if (inside.isEmpty()) return ok(source, key("zone.here_none", MewZones.usableCount()).withStyle(ChatFormatting.GRAY))
        val test = MewZones.biomeTest(holder)
        for (zone in inside) {
            val line = if (zone.biomeAllowed(test)) key("zone.here_ok", zone.id).withStyle(ChatFormatting.GREEN)
            else key("zone.here_biome_refused", zone.id, zone.biomes.joinToString(", ")).withStyle(ChatFormatting.RED)
            source.sendSuccess({ line }, false)
        }
        val counts = MewZones.at(player) != null && MewHunt.afkStatus(player) == MewMath.AfkStatus.ACTIVE && MewHunt.config.hunt.enabled
        return ok(source, key(if (counts) "zone.here_counts" else "zone.here_not_counting", afk(MewHunt.afkStatus(player))))
    }

    private fun zoneReload(source: CommandSourceStack): Int {
        MewZones.reload()
        return ok(source, key("zone.reloaded", MewZones.usableCount(), MewZones.all().size), broadcast = true)
    }

    // ---------------------------------------------------------------- hunt

    private fun state(state: MewState): MutableComponent = Component.translatable("generations_core.mew.state." + state.name.lowercase(Locale.ROOT))

    private fun afk(status: MewMath.AfkStatus): MutableComponent {
        val afk = MewHunt.config.afk
        return when (status) {
            MewMath.AfkStatus.ACTIVE -> Component.translatable("generations_core.mew.afk.active")
            MewMath.AfkStatus.IDLE -> Component.translatable("generations_core.mew.afk.idle", afk.afkSeconds)
            MewMath.AfkStatus.STILL -> Component.translatable("generations_core.mew.afk.still", num(afk.minMoveBlocks), num(afk.stillMinutes, 0))
        }
    }

    /** The chance of the record's next draw (1 at the guarantee). */
    private fun nextChance(record: MewRecord): Double {
        val rules = MewHunt.machine.rules
        return if (record.nextDrawTicks >= rules.guaranteeTicks) 1.0 else rules.chance(MewMath.hours(record.nextDrawTicks))
    }

    private fun huntLine(name: String, record: MewRecord, keyName: String): MutableComponent {
        MewHunt.machine.schedule(record)
        return key(
            keyName, name, num(MewMath.hours(record.activeTicks), 2), state(record.state),
            num(MewMath.hours(record.nextDrawTicks), 2), num(nextChance(record) * 100.0, 2), num(MewHunt.config.hunt.guaranteeHours),
        )
    }

    private fun huntGet(source: CommandSourceStack, player: ServerPlayer?): Int {
        val target = target(source, player)
        val record = MewHuntData.get(source.server, target.uuid) ?: return ok(source, key("hunt.none", target.scoreboardName))
        return ok(source, huntLine(target.scoreboardName, record, "hunt.get"))
    }

    private fun huntSet(source: CommandSourceStack, player: ServerPlayer?, hours: Double, add: Boolean): Int {
        val target = target(source, player)
        val record = MewHuntData.getOrCreate(source.server, target.uuid, MewHunt.machine)
        val ticks = if (add) record.activeTicks + MewMath.ticks(hours) else MewMath.ticks(hours)
        MewHunt.machine.setActive(record, ticks)
        MewHuntData.changed(source.server)
        return ok(source, huntLine(target.scoreboardName, record, "hunt.set").withStyle(ChatFormatting.GOLD), broadcast = true)
    }

    private fun draw(source: CommandSourceStack, player: ServerPlayer?): Int {
        val target = target(source, player)
        val record = MewHuntData.getOrCreate(source.server, target.uuid, MewHunt.machine)
        val draw = MewHunt.machine.forceDraw(record) { ThreadLocalRandom.current().nextDouble() }
            ?: return fail(source, key("draw.refused", target.scoreboardName, state(record.state)))
        MewHuntData.changed(source.server)
        val result = when {
            draw.guaranteed -> key("draw.guarantee").withStyle(ChatFormatting.LIGHT_PURPLE)
            draw.success -> key("draw.success").withStyle(ChatFormatting.LIGHT_PURPLE)
            else -> key("draw.fail").withStyle(ChatFormatting.GRAY)
        }
        ok(source, key("draw.done", target.scoreboardName, num(draw.hours, 2), num(draw.chance * 100.0, 2), num(draw.roll * 100.0, 2), result).withStyle(ChatFormatting.GOLD), broadcast = true)
        if (draw.success) {
            MewHunt.unlocked(source.server, target, draw)
            MewHunt.startIfFree(source.server, target)
        }
        return 1
    }

    private fun info(source: CommandSourceStack, player: ServerPlayer?): Int {
        val target = target(source, player)
        val record = MewHuntData.get(source.server, target.uuid)
        val hunt = MewHunt.config.hunt
        source.sendSuccess({ key("info.head", target.scoreboardName, record?.let { state(it.state) } ?: key("info.never")).withStyle(ChatFormatting.LIGHT_PURPLE) }, false)
        if (!hunt.enabled) source.sendSuccess({ key("info.disabled").withStyle(ChatFormatting.RED) }, false)
        if (record != null) {
            MewHunt.machine.schedule(record)
            source.sendSuccess({
                key(
                    "info.hunt", num(MewMath.hours(record.activeTicks), 2), record.draws, num(MewMath.hours(record.nextDrawTicks), 2),
                    num(nextChance(record) * 100.0, 2), num(hunt.guaranteeHours),
                )
            }, false)
            if (record.state != MewState.HUNTING) {
                source.sendSuccess({
                    key("info.unlocked", num(MewMath.hours(record.unlockedAtTicks.coerceAtLeast(0L)), 2), record.attempts, num(record.cooldownTicks / 1200.0))
                }, false)
            }
            if (record.completed > 0) source.sendSuccess({ key("info.completed", record.completed) }, false)
        }
        MewGame.live(target.uuid)?.let { live ->
            val left = live.session.ticksLeft(MewHunt.now) / 20L
            source.sendSuccess({ key("info.game", live.session.round, left, if (live.session.test) " (test)" else "") }, false)
        }
        val zone = MewZones.at(target)
        val where = if (zone != null) key("info.in_zone", zone.id) else key("info.no_zone")
        return ok(source, key("info.now", where, afk(MewHunt.afkStatus(target))).withStyle(ChatFormatting.GRAY))
    }

    private fun reset(source: CommandSourceStack, player: ServerPlayer?): Int {
        val target = target(source, player)
        val record = MewHuntData.getOrCreate(source.server, target.uuid, MewHunt.machine)
        MewHunt.machine.reset(record)
        MewHuntData.saveNow(source.server)
        return ok(source, key("reset", target.scoreboardName, num(MewMath.hours(record.nextDrawTicks))).withStyle(ChatFormatting.GOLD), broadcast = true)
    }

    // ---------------------------------------------------------------- the rest of the plan (section 14)

    private val TYPES = listOf("a", "b", "c", "d")

    private fun testOff(source: CommandSourceStack): Boolean {
        if (MewHunt.config.testMode) return false
        source.sendFailure(key("test_off"))
        return true
    }

    private fun manifest(source: CommandSourceStack, type: String, player: ServerPlayer?): Int {
        if (testOff(source)) return 0
        val index = TYPES.indexOf(type.lowercase(Locale.ROOT))
        if (index < 0) return fail(source, key("manifest.unknown", type))
        val target = target(source, player)
        if (!MewHunt.show(target, index)) return fail(source, key("manifest.no_place"))
        return ok(source, key("manifest.done", type.uppercase(Locale.ROOT), target.scoreboardName).withStyle(ChatFormatting.AQUA))
    }

    private fun unlock(source: CommandSourceStack, player: ServerPlayer?): Int {
        val target = target(source, player)
        val record = MewHuntData.getOrCreate(source.server, target.uuid, MewHunt.machine)
        if (!MewHunt.machine.unlock(record)) return fail(source, key("unlock.refused", target.scoreboardName, state(record.state)))
        MewHuntData.saveNow(source.server)
        ok(source, key("unlock.done", target.scoreboardName).withStyle(ChatFormatting.GOLD), broadcast = true)
        MewHunt.startIfFree(source.server, target)
        return 1
    }

    private fun minigame(source: CommandSourceStack, round: Int): Int {
        if (testOff(source)) return 0
        val player = source.playerOrException
        if (MewGame.isPlaying(player.uuid)) return fail(source, key("minigame.busy"))
        if (!MewGame.start(player, test = true, comeback = false, firstRound = round)) return fail(source, key("minigame.busy"))
        return ok(source, key("minigame.started").withStyle(ChatFormatting.AQUA))
    }

    private fun failChallenge(source: CommandSourceStack, player: ServerPlayer?): Int {
        val target = target(source, player)
        val live = MewGame.live(target.uuid)
        if (live != null && !live.session.test) {
            MewGame.forceFail(source.server, target.uuid)
        } else {
            val record = MewHuntData.get(source.server, target.uuid)
            if (record == null || !MewHunt.machine.fail(record, MewHunt.config.retry.retryMinutes * 1200L)) {
                return fail(source, key("fail.refused", target.scoreboardName))
            }
            MewHuntData.saveNow(source.server)
        }
        return ok(source, key("fail.done", target.scoreboardName, MewHunt.config.retry.retryMinutes).withStyle(ChatFormatting.GOLD), broadcast = true)
    }

    private fun cooldown(source: CommandSourceStack, player: ServerPlayer?, minutes: Double): Int {
        val target = target(source, player)
        val record = MewHuntData.get(source.server, target.uuid)
            ?: return fail(source, key("cooldown.refused", target.scoreboardName, key("info.never")))
        if (!MewHunt.machine.setCooldown(record, (minutes * 1200.0).toLong())) return fail(source, key("cooldown.refused", target.scoreboardName, state(record.state)))
        MewHuntData.saveNow(source.server)
        ok(source, key("cooldown.done", target.scoreboardName, num(minutes)).withStyle(ChatFormatting.GOLD), broadcast = true)
        if (record.cooldownTicks <= 0L) MewHunt.startIfFree(source.server, target)
        return 1
    }

    private fun cinematic(source: CommandSourceStack, real: Boolean): Int {
        if (!real && testOff(source)) return 0
        val player = source.playerOrException
        val record = MewHuntData.get(source.server, player.uuid)
        if (!MewReward.cinematicCommand(player, test = !real)) {
            return fail(source, key("cinematic.refused", record?.let { state(it.state) } ?: key("info.never")))
        }
        return ok(source, key(if (real) "cinematic.real" else "cinematic.test").withStyle(ChatFormatting.AQUA))
    }

    private fun spawn(source: CommandSourceStack, real: Boolean): Int {
        if (!real && testOff(source)) return 0
        val player = source.playerOrException
        val record = MewHuntData.get(source.server, player.uuid)
        if (!MewReward.spawnCommand(player, test = !real)) {
            return fail(source, key("spawn.refused", player.scoreboardName, record?.let { state(it.state) } ?: key("info.never")))
        }
        return 1
    }

    private fun giveUp(source: CommandSourceStack): Int {
        val player = source.playerOrException
        if (!MewGame.giveUp(player)) return fail(source, key("giveup.none"))
        return 1
    }

    private fun reload(source: CommandSourceStack): Int {
        MewHunt.reload(source.server)
        return ok(source, key("reloaded", MewZones.usableCount()), broadcast = true)
    }
}
