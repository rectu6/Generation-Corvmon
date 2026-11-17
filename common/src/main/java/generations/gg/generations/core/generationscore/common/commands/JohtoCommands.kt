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
import generations.gg.generations.core.generationscore.common.world.entity.statue.StatueEntity
import generations.gg.generations.core.generationscore.common.world.johto.BeastState
import generations.gg.generations.core.generationscore.common.world.johto.Johto
import generations.gg.generations.core.generationscore.common.world.johto.JohtoBeast
import generations.gg.generations.core.generationscore.common.world.johto.JohtoData
import generations.gg.generations.core.generationscore.common.world.johto.JohtoPlaces
import generations.gg.generations.core.generationscore.common.world.johto.JohtoStateMachine
import generations.gg.generations.core.generationscore.common.world.johto.JohtoTrials
import generations.gg.generations.core.generationscore.common.world.johto.SummonState
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.Locale

/**
 * /generations johto … (JOHTO-PLAN.md § 2), permission generations_core.command.johto (level 2):
 * - place …: the dimension, the history's anchor, the statues (the one looked at), the altar
 *   (the block looked at), the trial zones, the safe spawn points, a test zone around oneself,
 *   list, check (loaded chunks only); written to johto_places.json;
 * - test …: each scene, a trial at any round (a zone around oneself when none), an encounter, the
 *   summoning — nothing consumed, nothing saved (real: a test Pokémon appears);
 * - fail, info, reset, cooldown clear, give memory, open, reload;
 * - giveup: the player gives up their trial (no permission).
 * Registered by CommandsMixin.
 */
object JohtoCommands {
    private val PERMISSION = object : Permission {
        override val identifier: ResourceLocation = GenerationsCore.id("command.johto")
        override val literal: String = "generations_core.command.johto"
        override val level: PermissionLevel = PermissionLevel.CHEAT_COMMANDS_AND_COMMAND_BLOCKS
    }

    private fun allowed(source: CommandSourceStack) =
        runCatching { Cobblemon.permissionValidator.hasPermission(source, PERMISSION) }.getOrElse { source.hasPermission(2) }

    private fun key(name: String, vararg args: Any): MutableComponent = Component.translatable("generations_core.johto.command.$name", *args)

    private fun ok(source: CommandSourceStack, text: Component): Int {
        source.sendSuccess({ text }, false)
        return 1
    }

    private fun fail(source: CommandSourceStack, text: Component): Int {
        source.sendFailure(text)
        return 0
    }

    private fun num(v: Double): String = "%.1f".format(Locale.ROOT, v)

    private fun beastArg(name: String = "beast", extra: List<String> = emptyList()) = Commands.argument(name, StringArgumentType.word())
        .suggests { _, b -> SharedSuggestionProvider.suggest(JohtoBeast.entries.map { it.id } + extra, b) }

    private fun beast(ctx: CommandContext<CommandSourceStack>, name: String = "beast"): JohtoBeast? = JohtoBeast.of(StringArgumentType.getString(ctx, name))

    /** The subcommand alone (oneself), or followed by a player. */
    private fun <T : ArgumentBuilder<CommandSourceStack, T>> withPlayer(node: T, run: (CommandContext<CommandSourceStack>, ServerPlayer) -> Int): T =
        node.executes { ctx -> ctx.source.player?.let { run(ctx, it) } ?: fail(ctx.source, key("no_player")) }
            .then(Commands.argument("player", EntityArgument.player()).executes { run(it, EntityArgument.getPlayer(it, "player")) })

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val place = Commands.literal("place")
            .then(Commands.literal("dimension").executes { placeDimension(it.source) })
            .then(Commands.literal("history").executes { placeHistory(it.source, null, null) }
                .then(Commands.argument("height", DoubleArgumentType.doubleArg(6.0, 80.0)).executes { placeHistory(it.source, DoubleArgumentType.getDouble(it, "height"), null) }
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(3.0, 30.0)).executes {
                        placeHistory(it.source, DoubleArgumentType.getDouble(it, "height"), DoubleArgumentType.getDouble(it, "radius"))
                    })))
            .then(Commands.literal("statue").then(beastArg().executes { placeStatue(it.source, beast(it)) }))
            .then(Commands.literal("unstatue").executes { unStatue(it.source) })
            .then(Commands.literal("altar").executes { placeAltar(it.source) })
            .then(Commands.literal("zone").then(beastArg()
                .then(Commands.literal("circle").then(Commands.argument("radius", DoubleArgumentType.doubleArg(6.0, 256.0)).executes {
                    placeCircle(it.source, beast(it), DoubleArgumentType.getDouble(it, "radius"))
                }))
                .then(Commands.literal("box").then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos()).executes {
                    placeBox(it, beast(it))
                })))
                .then(Commands.literal("clear").executes { clearZone(it.source, beast(it)) })))
            .then(Commands.literal("spawn").then(beastArg("id", listOf("hooh"))
                .then(Commands.literal("add").executes { addSpawn(it.source, StringArgumentType.getString(it, "id")) })
                .then(Commands.literal("clear").executes { clearSpawns(it.source, StringArgumentType.getString(it, "id")) })))
            .then(Commands.literal("test")
                .then(Commands.literal("clear").executes { ctx -> ctx.source.player?.let { if (Johto.clearTestZone(it)) ok(ctx.source, key("test_zone_cleared")) else fail(ctx.source, key("no_test_zone")) } ?: 0 })
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(8.0, 64.0)).executes { ctx ->
                    val p = ctx.source.player ?: return@executes fail(ctx.source, key("no_player"))
                    val zone = Johto.setTestZone(p, DoubleArgumentType.getDouble(ctx, "radius"))
                    ok(ctx.source, key("test_zone", num(zone.radius)))
                }))
            .then(Commands.literal("list").executes { list(it.source) })
            .then(Commands.literal("check").executes { check(it.source) })

        val test = Commands.literal("test")
            .then(Commands.literal("history").executes { ctx ->
                val p = ctx.source.player ?: return@executes fail(ctx.source, key("no_player"))
                if (Johto.startHistory(p, p.position().add(Johto.forward(p.yRot, 6.0)), p.yRot, test = true)) ok(ctx.source, key("test_started", "history")) else 0
            })
            .then(Commands.literal("awaken").then(beastArg().executes { testAwaken(it.source, beast(it)) }))
            .then(Commands.literal("trial").then(beastArg().executes { testTrial(it.source, beast(it), 1) }
                .then(Commands.argument("round", IntegerArgumentType.integer(1, 3)).executes { testTrial(it.source, beast(it), IntegerArgumentType.getInteger(it, "round")) })))
            .then(Commands.literal("encounter").then(beastArg().executes { testEncounter(it.source, beast(it), false) }
                .then(Commands.literal("real").executes { testEncounter(it.source, beast(it), true) })))
            .then(Commands.literal("summon").executes { testSummon(it.source, false) }
                .then(Commands.literal("real").executes { testSummon(it.source, true) }))

        dispatcher.register(
            Commands.literal("generations").then(
                Commands.literal("johto")
                    .then(Commands.literal("giveup").executes { ctx ->
                        val p = ctx.source.player ?: return@executes 0
                        if (!JohtoTrials.isRunning(p.uuid)) return@executes fail(ctx.source, key("no_trial"))
                        JohtoTrials.stop(p.uuid, "gave_up")
                        1
                    })
                    .then(place.requires(::allowed))
                    .then(test.requires(::allowed))
                    .then(withPlayer(Commands.literal("fail").requires(::allowed)) { ctx, p ->
                        if (!JohtoTrials.isRunning(p.uuid)) fail(ctx.source, key("no_trial")) else {
                            JohtoTrials.stop(p.uuid, "admin")
                            ok(ctx.source, key("failed", p.scoreboardName))
                        }
                    })
                    .then(withPlayer(Commands.literal("info").requires(::allowed)) { ctx, p -> info(ctx.source, p) })
                    .then(Commands.literal("reset").requires(::allowed)
                        .then(Commands.argument("player", EntityArgument.player()).executes { reset(it.source, EntityArgument.getPlayer(it, "player"), null) }
                            .then(beastArg("beast", listOf("all", "history", "hooh")).executes {
                                reset(it.source, EntityArgument.getPlayer(it, "player"), StringArgumentType.getString(it, "beast"))
                            })))
                    .then(Commands.literal("cooldown").requires(::allowed).then(Commands.literal("clear").then(withPlayer(beastArg()) { ctx, p -> clearCooldown(ctx.source, p, beast(ctx)) })))
                    .then(Commands.literal("give").requires(::allowed).then(Commands.literal("memory").then(withPlayer(beastArg("beast", listOf("all"))) { ctx, p ->
                        giveMemory(ctx.source, p, StringArgumentType.getString(ctx, "beast"))
                    })))
                    .then(Commands.literal("open").requires(::allowed).then(withPlayer(beastArg()) { ctx, p -> open(ctx.source, p, beast(ctx)) }))
                    .then(Commands.literal("reload").requires(::allowed).executes { ctx ->
                        Johto.reload()
                        ok(ctx.source, key("reloaded"))
                    }),
            ),
        )
    }

    // ---------------------------------------------------------------- places

    private fun player(source: CommandSourceStack): ServerPlayer? = source.player

    private fun placeDimension(source: CommandSourceStack): Int {
        val p = player(source) ?: return fail(source, key("no_player"))
        Johto.places.dimension = p.serverLevel().dimension().location().toString()
        Johto.savePlaces()
        return ok(source, key("dimension", Johto.places.dimension))
    }

    private fun placeHistory(source: CommandSourceStack, height: Double?, radius: Double?): Int {
        val p = player(source) ?: return fail(source, key("no_player"))
        Johto.places.history = JohtoPlaces.Anchor(p.x, p.y, p.z, p.yRot)
        height?.let { Johto.places.towerHeight = it }
        radius?.let { Johto.places.towerRadius = it }
        Johto.savePlaces()
        return ok(source, key("history", num(p.x), num(p.y), num(p.z), num(Johto.places.towerHeight), num(Johto.places.towerRadius)))
    }

    /** The statue the player looks at (within 8 blocks, 15° of their view). */
    private fun lookedStatue(p: ServerPlayer): StatueEntity? {
        val eye = p.eyePosition
        val look = p.lookAngle
        return p.serverLevel().getEntitiesOfClass(StatueEntity::class.java, p.boundingBox.inflate(8.0)).filter {
            val to = it.position().add(0.0, it.bbHeight / 2.0, 0.0).subtract(eye)
            val d = to.length()
            d < 9.0 && to.normalize().dot(look) > 0.96
        }.minByOrNull { it.distanceTo(p) }
    }

    private fun placeStatue(source: CommandSourceStack, beast: JohtoBeast?): Int {
        beast ?: return fail(source, key("unknown_beast"))
        val p = player(source) ?: return fail(source, key("no_player"))
        val statue = lookedStatue(p) ?: return fail(source, key("no_statue"))
        Johto.places.statues.removeIf { it.uuid == statue.uuid.toString() }
        Johto.places.statues += JohtoPlaces.StatuePlace(beast.id, statue.uuid.toString(), statue.x, statue.y, statue.z, statue.orientation)
        Johto.places.dimension = p.serverLevel().dimension().location().toString()
        Johto.savePlaces()
        return ok(source, key("statue", beast.id, num(statue.x), num(statue.y), num(statue.z)))
    }

    private fun unStatue(source: CommandSourceStack): Int {
        val p = player(source) ?: return fail(source, key("no_player"))
        val statue = lookedStatue(p) ?: return fail(source, key("no_statue"))
        if (!Johto.places.statues.removeIf { it.uuid == statue.uuid.toString() }) return fail(source, key("not_registered"))
        Johto.savePlaces()
        return ok(source, key("unstatue"))
    }

    private fun placeAltar(source: CommandSourceStack): Int {
        val p = player(source) ?: return fail(source, key("no_player"))
        val hit = p.pick(8.0, 1f, false)
        if (hit.type != HitResult.Type.BLOCK) return fail(source, key("no_block"))
        val pos = (hit as BlockHitResult).blockPos
        Johto.places.altar = JohtoPlaces.Altar(pos.x, pos.y, pos.z, Johto.yawTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos), p.position()))
        Johto.places.dimension = p.serverLevel().dimension().location().toString()
        Johto.savePlaces()
        val block = p.serverLevel().getBlockState(pos).block.name
        return ok(source, key("altar", block, pos.x, pos.y, pos.z))
    }

    private fun placeCircle(source: CommandSourceStack, beast: JohtoBeast?, radius: Double): Int {
        beast ?: return fail(source, key("unknown_beast"))
        val p = player(source) ?: return fail(source, key("no_player"))
        Johto.places.zones[beast.id] = JohtoPlaces.Zone("circle", p.x, p.y, p.z, radius, 24.0)
        Johto.savePlaces()
        return ok(source, key("zone", beast.id, num(radius)))
    }

    private fun placeBox(ctx: CommandContext<CommandSourceStack>, beast: JohtoBeast?): Int {
        beast ?: return fail(ctx.source, key("unknown_beast"))
        val a = BlockPosArgument.getLoadedBlockPos(ctx, "from")
        val b = BlockPosArgument.getLoadedBlockPos(ctx, "to")
        Johto.places.zones[beast.id] = JohtoPlaces.Zone("box", a.x.toDouble(), a.y.toDouble(), a.z.toDouble(), 24.0, 24.0, b.x.toDouble(), b.y.toDouble(), b.z.toDouble())
        Johto.savePlaces()
        return ok(ctx.source, key("zone_box", beast.id))
    }

    private fun clearZone(source: CommandSourceStack, beast: JohtoBeast?): Int {
        beast ?: return fail(source, key("unknown_beast"))
        if (Johto.places.zones.remove(beast.id) == null) return fail(source, key("no_zone"))
        Johto.savePlaces()
        return ok(source, key("zone_cleared", beast.id))
    }

    private fun addSpawn(source: CommandSourceStack, id: String): Int {
        val key = id.lowercase()
        if (key != "hooh" && JohtoBeast.of(key) == null) return fail(source, key("unknown_beast"))
        val p = player(source) ?: return fail(source, key("no_player"))
        Johto.places.spawns.getOrPut(key) { mutableListOf() } += JohtoPlaces.Point(p.x, p.y, p.z)
        Johto.savePlaces()
        return ok(source, key("spawn", key, Johto.places.spawns(key).size))
    }

    private fun clearSpawns(source: CommandSourceStack, id: String): Int {
        Johto.places.spawns.remove(id.lowercase())
        Johto.savePlaces()
        return ok(source, key("spawn_cleared", id))
    }

    private fun list(source: CommandSourceStack): Int {
        val pl = Johto.places
        source.sendSuccess({ key("list.dimension", pl.dimension) }, false)
        source.sendSuccess({ key("list.history", pl.history?.let { "${num(it.x)} ${num(it.y)} ${num(it.z)}" } ?: "—", num(pl.towerHeight), num(pl.towerRadius)) }, false)
        for (s in pl.statues) source.sendSuccess({ key("list.statue", s.beast, num(s.x), num(s.y), num(s.z)) }, false)
        for ((id, z) in pl.zones) source.sendSuccess({ key("list.zone", id, z.shape, num(z.centreX), num(z.centreY), num(z.centreZ), num(z.reach)) }, false)
        for ((id, points) in pl.spawns) source.sendSuccess({ key("list.spawns", id, points.size) }, false)
        source.sendSuccess({ key("list.altar", pl.altar?.let { "${it.x} ${it.y} ${it.z}" } ?: "—") }, false)
        return 1
    }

    /** What is missing or wrong (in the chunks loaded now: nothing is loaded for it). */
    private fun check(source: CommandSourceStack): Int {
        val server = source.server
        val pl = Johto.places
        val level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(pl.dimension)))
        var problems = 0
        fun warn(text: Component) {
            problems++
            source.sendSuccess({ text.copy().withStyle(ChatFormatting.YELLOW) }, false)
        }
        if (level == null) warn(key("check.dimension", pl.dimension))
        for (beast in JohtoBeast.entries) {
            val statues = pl.statues.filter { it.beast == beast.id }
            if (statues.isEmpty()) warn(key("check.no_statue", beast.id))
            for (s in statues) {
                val pos = net.minecraft.core.BlockPos.containing(s.x, s.y, s.z)
                if (level != null && level.isLoaded(pos)) {
                    val found = runCatching { level.getEntity(java.util.UUID.fromString(s.uuid)) }.getOrNull()
                    if (found !is StatueEntity) warn(key("check.statue_gone", beast.id, pos.x, pos.y, pos.z))
                }
            }
            if (pl.zone(beast) == null) warn(key("check.no_zone", beast.id))
            if (pl.spawns(beast.id).isEmpty()) warn(key("check.no_spawn", beast.id))
        }
        if (pl.history == null) warn(key("check.no_history"))
        if (pl.altar == null) warn(key("check.no_altar"))
        if (pl.spawns("hooh").isEmpty()) warn(key("check.no_spawn", "hooh"))
        return if (problems == 0) ok(source, key("check.ok")) else 1
    }

    // ---------------------------------------------------------------- tests

    private fun testAwaken(source: CommandSourceStack, beast: JohtoBeast?): Int {
        beast ?: return fail(source, key("unknown_beast"))
        val p = player(source) ?: return fail(source, key("no_player"))
        val statue = lookedStatue(p) ?: return fail(source, key("no_statue"))
        return if (Johto.startAwakening(p, statue, beast, test = true)) ok(source, key("test_started", beast.awakeningScene)) else 0
    }

    private fun testTrial(source: CommandSourceStack, beast: JohtoBeast?, round: Int): Int {
        beast ?: return fail(source, key("unknown_beast"))
        val p = player(source) ?: return fail(source, key("no_player"))
        return if (Johto.testTrial(p, beast, round)) ok(source, key("test_trial", beast.id, round)) else fail(source, key("busy"))
    }

    private fun testEncounter(source: CommandSourceStack, beast: JohtoBeast?, real: Boolean): Int {
        beast ?: return fail(source, key("unknown_beast"))
        val p = player(source) ?: return fail(source, key("no_player"))
        return if (Johto.startEncounter(p, beast, test = true, spawnTest = real)) ok(source, key("test_started", beast.encounterScene)) else 0
    }

    private fun testSummon(source: CommandSourceStack, real: Boolean): Int {
        val p = player(source) ?: return fail(source, key("no_player"))
        return if (Johto.startSummon(p, test = true, spawnTest = real)) ok(source, key("test_started", "ho_oh_summon")) else 0
    }

    // ---------------------------------------------------------------- state

    private fun info(source: CommandSourceStack, p: ServerPlayer): Int {
        val record = JohtoData.get(source.server, p.uuid)
        if (record == null) return ok(source, key("info.none", p.scoreboardName))
        source.sendSuccess({ key("info.head", p.scoreboardName, if (record.historySeen) "✔" else "✘", record.summon.name, record.summons) }, false)
        val now = System.currentTimeMillis()
        for (beast in JohtoBeast.entries) {
            val b = record.track(beast)
            val left = if (b.state == BeastState.COOLDOWN) ((b.cooldownUntil - now) / 1000L).coerceAtLeast(0L) else 0L
            source.sendSuccess({ key("info.beast", beast.id, b.state.name, b.round, b.failures, left, b.memoriesGiven, if (b.memoryOwed) "!" else "", b.wins) }, false)
        }
        JohtoTrials.run(p.uuid)?.let { r -> source.sendSuccess({ key("info.trial", r.beast.id, r.round, if (r.test) "test" else "") }, false) }
        return 1
    }

    private fun reset(source: CommandSourceStack, p: ServerPlayer, what: String?): Int {
        val record = JohtoData.getOrCreate(source.server, p.uuid)
        JohtoTrials.run(p.uuid)?.let { JohtoTrials.stop(p.uuid, "admin") }
        when (val w = what?.lowercase()) {
            null, "all" -> {
                JohtoData.of(source.server).records.remove(p.uuid)
                JohtoData.saveNow(source.server)
                return ok(source, key("reset_all", p.scoreboardName))
            }
            "history" -> {
                record.historySeen = false
                record.seen.clear()
            }
            "hooh" -> record.summon = SummonState.IDLE
            else -> {
                val beast = JohtoBeast.of(w) ?: return fail(source, key("unknown_beast"))
                JohtoStateMachine.reset(record, beast)
            }
        }
        JohtoData.saveNow(source.server)
        return ok(source, key("reset", p.scoreboardName, what ?: ""))
    }

    private fun clearCooldown(source: CommandSourceStack, p: ServerPlayer, beast: JohtoBeast?): Int {
        beast ?: return fail(source, key("unknown_beast"))
        val record = JohtoData.get(source.server, p.uuid) ?: return fail(source, key("no_cooldown"))
        if (!JohtoStateMachine.clearCooldown(record, beast)) return fail(source, key("no_cooldown"))
        JohtoData.saveNow(source.server)
        return ok(source, key("cooldown_cleared", beast.id, p.scoreboardName))
    }

    private fun giveMemory(source: CommandSourceStack, p: ServerPlayer, which: String): Int {
        val list = if (which.equals("all", true)) JohtoBeast.entries else listOfNotNull(JohtoBeast.of(which))
        if (list.isEmpty()) return fail(source, key("unknown_beast"))
        list.forEach { Johto.giveMemory(p, it) }
        return ok(source, key("given", list.size, p.scoreboardName))
    }

    private fun open(source: CommandSourceStack, p: ServerPlayer, beast: JohtoBeast?): Int {
        beast ?: return fail(source, key("unknown_beast"))
        val record = JohtoData.getOrCreate(source.server, p.uuid)
        if (!JohtoStateMachine.forceOpen(record, beast)) return fail(source, key("not_idle", beast.id))
        JohtoData.saveNow(source.server)
        return ok(source, key("opened", beast.id, p.scoreboardName))
    }
}
