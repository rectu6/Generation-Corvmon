package generations.gg.generations.core.generationscore.common.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.SharedSuggestionProvider
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpGameplay
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpPortals
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraSpaceConfig
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpRide
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpSessions
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpVariant
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraArrivalPoints
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTransfers
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorldsConfig

/**
 * /ultrawarp, for operators (level 2; in solo, with commands allowed). Testing the
 * Ultra Warp Ride alone: the sessions go through the same manager and limit as the
 * altar's, only the altar and the required Pokémon are skipped.
 *
 * - sessions: the journeys in progress, and the places taken.
 * - reload: the server's config file again.
 * - test start sun|moon [mode] [players]: a journey without altar nor Pokémon, in
 *   the server's difficulty (config), or for testing in the mode given.
 * - test end [players]: ends the journey.
 * - test portal <destination|random> <level> [count], test convergence: portals
 *   soon ahead, of any level, without travelling thousands of light-years.
 * - test event storm|current|anomaly|necrozma, test energy <value>, test distance
 *   <light-years>: for trying the journey's events, defeat and difficulty.
 * - test simulate <n>: n simulated sessions (no player) take places, to see the
 *   limit of a multiplayer server alone; test clear removes them.
 * - test arrive <world> [players]: into an Ultra Space world without a journey, through
 *   the same safe transfer as a portal's; test return [players]: back where the player
 *   stood before leaving (the provisional return).
 * - test biome <biome> [players]: into the Ultra Space biome given (its world), near
 *   where it is found, through the same safe transfer (for testing each of the 35).
 * - worlds: the seven worlds (open, loaded, players, arrival points); worlds points
 *   <world>: its arrival points; worlds prepare <world>: every point looked at now.
 *
 * Registered by CommandsMixin, on the integrated and the dedicated server alike.
 */
object UltraWarpCommand {
    private const val KEY = "generations_core.ultra_warp.command"

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("ultrawarp")
                .requires { it.hasPermission(2) }
                .then(Commands.literal("sessions").executes { sessions(it.source) })
                .then(Commands.literal("reload").executes { reload(it.source) })
                .then(UltraWeatherCommands.weather())
                .then(UltraWeatherCommands.suit())
                .then(UltraEncounterCommands.encounter())
                .then(UltraEncounterCommands.spawns())
                .then(UltraRiftCommands.rift())
                .then(UltraRiftCommands.home())
                .then(
                    Commands.literal("worlds")
                        .executes { worlds(it.source) }
                        .then(Commands.literal("points").then(worldArgument().executes { points(it.source, StringArgumentType.getString(it, "world")) }))
                        .then(Commands.literal("prepare").then(worldArgument().executes { prepare(it.source, StringArgumentType.getString(it, "world")) }))
                )
                .then(
                    Commands.literal("test")
                        .then(Commands.literal("start").then(startBranch("sun", UltraWarpVariant.SUN)).then(startBranch("moon", UltraWarpVariant.MOON)))
                        .then(
                            Commands.literal("end")
                                .executes { end(it.source, listOf(it.source.playerOrException)) }
                                .then(Commands.argument("targets", EntityArgument.players()).executes { end(it.source, EntityArgument.getPlayers(it, "targets")) })
                        )
                        .then(
                            Commands.literal("simulate").then(
                                Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                    .executes { simulate(it.source, IntegerArgumentType.getInteger(it, "count")) }
                            )
                        )
                        .then(Commands.literal("clear").executes { clear(it.source) })
                        .then(
                            Commands.literal("arrive").then(
                                worldArgument()
                                    .executes { arrive(it.source, listOf(it.source.playerOrException), StringArgumentType.getString(it, "world")) }
                                    .then(
                                        Commands.argument("targets", EntityArgument.players())
                                            .executes { arrive(it.source, EntityArgument.getPlayers(it, "targets"), StringArgumentType.getString(it, "world")) }
                                    )
                            )
                        )
                        .then(
                            Commands.literal("biome").then(
                                Commands.argument("biome", ResourceLocationArgument.id())
                                    .suggests { context, builder -> SharedSuggestionProvider.suggestResource(ultraBiomes(context.source), builder) }
                                    .executes { biome(it.source, listOf(it.source.playerOrException), ResourceLocationArgument.getId(it, "biome")) }
                                    .then(
                                        Commands.argument("targets", EntityArgument.players())
                                            .executes { biome(it.source, EntityArgument.getPlayers(it, "targets"), ResourceLocationArgument.getId(it, "biome")) }
                                    )
                            )
                        )
                        .then(
                            Commands.literal("return")
                                .executes { returnHome(it.source, listOf(it.source.playerOrException)) }
                                .then(Commands.argument("targets", EntityArgument.players()).executes { returnHome(it.source, EntityArgument.getPlayers(it, "targets")) })
                        )
                        .then(
                            Commands.literal("portal").then(
                                Commands.argument("destination", StringArgumentType.word())
                                    .suggests { _, builder -> SharedSuggestionProvider.suggest(UltraSpaceConfig.ride.portals.destinations.map { it.id } + "random", builder) }
                                    .then(
                                        Commands.argument("level", IntegerArgumentType.integer(1, 4))
                                            .executes { portal(it.source, StringArgumentType.getString(it, "destination"), IntegerArgumentType.getInteger(it, "level"), 1) }
                                            .then(
                                                Commands.argument("count", IntegerArgumentType.integer(1, 4))
                                                    .executes {
                                                        portal(it.source, StringArgumentType.getString(it, "destination"), IntegerArgumentType.getInteger(it, "level"), IntegerArgumentType.getInteger(it, "count"))
                                                    }
                                            )
                                    )
                            )
                        )
                        .then(Commands.literal("convergence").executes { convergence(it.source) })
                        .then(
                            Commands.literal("event")
                                .then(eventBranch("storm", UltraWarpGameplay.Event.STORM))
                                .then(eventBranch("current", UltraWarpGameplay.Event.CURRENT))
                                .then(eventBranch("anomaly", UltraWarpGameplay.Event.ANOMALY))
                                .then(eventBranch("necrozma", UltraWarpGameplay.Event.NECROZMA))
                        )
                        .then(
                            Commands.literal("energy").then(
                                Commands.argument("value", FloatArgumentType.floatArg(0f, 10000f))
                                    .executes { energy(it.source, FloatArgumentType.getFloat(it, "value").toDouble()) }
                            )
                        )
                        .then(
                            Commands.literal("distance").then(
                                Commands.argument("lightYears", FloatArgumentType.floatArg(0f, 1_000_000f))
                                    .executes { distance(it.source, FloatArgumentType.getFloat(it, "lightYears").toDouble()) }
                            )
                        )
                )
        )
    }

    private fun worldArgument() =
        Commands.argument("world", StringArgumentType.word()).suggests { _, builder -> SharedSuggestionProvider.suggest(UltraWorlds.IDS, builder) }

    /** /ultrawarp test arrive: the same safe transfer as through a portal, without a journey. */
    private fun arrive(source: CommandSourceStack, players: Collection<ServerPlayer>, world: String): Int {
        if (UltraWorlds.key(world) == null) {
            source.sendFailure(Component.translatable("$KEY.unknown_world", world, UltraWorlds.IDS.joinToString()))
            return 0
        }
        var started = 0
        players.forEach { player ->
            when {
                UltraWarpSessions.get(player.uuid) != null || UltraWarpTransfers.busy(player.uuid) ->
                    source.sendFailure(Component.translatable("$KEY.busy", player.displayName))
                !UltraWarpTransfers.test(player, world) ->
                    source.sendFailure(Component.translatable("$KEY.world_closed", UltraWarpTransfers.worldName(world)))
                else -> {
                    started++
                    source.sendSuccess({ Component.translatable("$KEY.arriving", player.displayName, UltraWarpTransfers.worldName(world)) }, true)
                }
            }
        }
        return started
    }

    /** The 35 Ultra Space biomes (generations_core:<world>/<biome>). */
    private fun ultraBiomes(source: CommandSourceStack): List<ResourceLocation> =
        source.server.registryAccess().registryOrThrow(Registries.BIOME).keySet()
            .filter { it.namespace == generations.gg.generations.core.generationscore.common.GenerationsCore.MOD_ID && it.path.contains('/') }
            .sortedBy { it.toString() }

    /** /ultrawarp test biome: into its world, near the closest place of it (found like /locate biome), safely. */
    private fun biome(source: CommandSourceStack, players: Collection<ServerPlayer>, id: ResourceLocation): Int {
        val world = id.path.substringBefore('/')
        val level = UltraWorlds.key(world)?.let { source.server.getLevel(it) }
        if (level == null || id !in ultraBiomes(source)) {
            source.sendFailure(Component.translatable("$KEY.biome_unknown", id.toString()))
            return 0
        }
        val biome = ResourceKey.create(Registries.BIOME, id)
        var started = 0
        players.forEach { player ->
            val from = if (player.level() === level) player.blockPosition() else BlockPos(0, 64, 0)
            val found = level.findClosestBiome3d({ it.`is`(biome) }, from, 6400, 32, 64)
            when {
                found == null -> source.sendFailure(Component.translatable("$KEY.biome_not_found", id.toString()))
                UltraWarpSessions.get(player.uuid) != null || !UltraWarpTransfers.testAt(player, world, found.first.x, found.first.z) ->
                    source.sendFailure(Component.translatable("$KEY.busy", player.displayName))
                else -> {
                    started++
                    source.sendSuccess({ Component.translatable("$KEY.biome_going", player.displayName, id.toString(), found.first.x, found.first.z) }, true)
                }
            }
        }
        return started
    }

    /** /ultrawarp test return: back where the player stood before leaving (else their respawn point, the world spawn). */
    private fun returnHome(source: CommandSourceStack, players: Collection<ServerPlayer>): Int {
        var started = 0
        players.forEach { player ->
            if (UltraWarpSessions.get(player.uuid) != null || !UltraWarpTransfers.returnHome(player)) {
                source.sendFailure(Component.translatable("$KEY.busy", player.displayName))
            } else {
                started++
                source.sendSuccess({ Component.translatable("$KEY.returning", player.displayName) }, true)
            }
        }
        return started
    }

    /** /ultrawarp worlds: each world, open or not, loaded, its players and its arrival points. */
    private fun worlds(source: CommandSourceStack): Int {
        source.sendSuccess({ Component.translatable("$KEY.worlds") }, false)
        UltraWorlds.IDS.forEach { id ->
            val key = UltraWorlds.key(id)!!
            val level = source.server.getLevel(key)
            val state = when {
                level == null -> "missing"
                !UltraWorlds.enabled(key) -> "closed"
                else -> "open"
            }
            val points = level?.let { UltraArrivalPoints.of(it).points } ?: emptyList()
            source.sendSuccess({
                Component.translatable(
                    "$KEY.world_line", UltraWarpTransfers.worldName(id), Component.translatable("$KEY.world.$state"), level?.players()?.size ?: 0,
                    points.count { it.spot != null }, points.count { it.blocked }, points.size,
                )
            }, false)
        }
        return UltraWorlds.IDS.size
    }

    /** /ultrawarp worlds points: a world's arrival points, as saved with it. */
    private fun points(source: CommandSourceStack, world: String): Int {
        val level = UltraWorlds.key(world)?.let { source.server.getLevel(it) }
        if (level == null) {
            source.sendFailure(Component.translatable("$KEY.unknown_world", world, UltraWorlds.IDS.joinToString()))
            return 0
        }
        val data = UltraArrivalPoints.of(level)
        source.sendSuccess({ Component.translatable("$KEY.points", UltraWarpTransfers.worldName(world)) }, false)
        data.points.forEach { point ->
            val (x, z) = UltraArrivalPoints.ideal(level, point.index)
            val anchor = if (point.anchored && !point.noLand) "${point.anchorX} ${point.anchorZ}" else "-"
            val spot = point.spot?.toShortString() ?: "-"
            val state = if (point.blocked) " (${point.reason})" else ""
            source.sendSuccess({ Component.translatable("$KEY.point_line", point.index, "$x $z", anchor, spot, point.uses, state) }, false)
        }
        return data.points.size
    }

    /** /ultrawarp worlds prepare: every arrival point of a world looked at now (also from the server console). */
    private fun prepare(source: CommandSourceStack, world: String): Int {
        if (!UltraWarpTransfers.prepareWorld(source.server, world) { message -> source.sendSuccess({ message }, true) }) {
            source.sendFailure(Component.translatable("$KEY.unknown_world", world, UltraWorlds.IDS.joinToString()))
            return 0
        }
        source.sendSuccess({ Component.translatable("$KEY.preparing", UltraWarpTransfers.worldName(world)) }, true)
        return 1
    }

    private fun eventBranch(name: String, event: UltraWarpGameplay.Event): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).executes { event(it.source, event, name) }

    /** The journey of the player running the command, in the tunnel; told when there is none. */
    private fun ride(source: CommandSourceStack): Pair<net.minecraft.server.level.ServerPlayer, generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpRideState>? {
        val player = source.playerOrException
        val ride = UltraWarpSessions.get(player.uuid)?.ride
        if (ride == null) {
            source.sendFailure(Component.translatable("$KEY.no_ride", player.displayName))
            return null
        }
        return player to ride
    }

    /** Test portals come this far ahead (beyond the objects already shown, sooner than the usual ones). */
    private const val TEST_LEAD = 160.0

    /** /ultrawarp test portal: portals of this destination (or random) and level, soon ahead. */
    private fun portal(source: CommandSourceStack, destination: String, level: Int, count: Int): Int {
        val (player, ride) = ride(source) ?: return 0
        val placed = UltraWarpPortals.place(ride, player, ride.distance + TEST_LEAD, count, destination.takeIf { it != "random" }, level)
        source.sendSuccess({ Component.translatable("$KEY.portal", placed.size, level) }, true)
        return placed.size
    }

    /** /ultrawarp test convergence: several portals together, destinations and levels drawn. */
    private fun convergence(source: CommandSourceStack): Int {
        val (player, ride) = ride(source) ?: return 0
        val portals = ride.settings.config.portals
        val count = ride.portalRandom.nextInt(portals.convergenceMin, portals.convergenceMax + 1)
        val placed = UltraWarpPortals.place(ride, player, ride.distance + TEST_LEAD, count, null, null)
        source.sendSuccess({ Component.translatable("$KEY.convergence", placed.size) }, true)
        return placed.size
    }

    /** /ultrawarp test event: starts it now (chance, distance and pause ignored). */
    private fun event(source: CommandSourceStack, event: UltraWarpGameplay.Event, name: String): Int {
        val (player, ride) = ride(source) ?: return 0
        UltraWarpGameplay.start(ride, player, event, player.server.overworld().gameTime)
        source.sendSuccess({ Component.translatable("$KEY.event", name) }, true)
        return 1
    }

    private fun energy(source: CommandSourceStack, value: Double): Int {
        val (player, ride) = ride(source) ?: return 0
        UltraWarpGameplay.setEnergy(ride, value)
        source.sendSuccess({ Component.translatable("$KEY.energy", "%.0f".format(ride.energy), player.displayName) }, true)
        return 1
    }

    private fun distance(source: CommandSourceStack, lightYears: Double): Int {
        val (player, ride) = ride(source) ?: return 0
        UltraWarpGameplay.jump(ride, lightYears)
        source.sendSuccess({ Component.translatable("$KEY.distance", "%.1f".format(ride.lightYears), player.displayName) }, true)
        return 1
    }

    /** start sun|moon [mode] [players]: the mode among the server's (suggested), its default when left out. */
    private fun startBranch(name: String, variant: UltraWarpVariant): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name)
            .executes { start(it.source, listOf(it.source.playerOrException), variant, null) }
            .then(
                Commands.argument("mode", StringArgumentType.word())
                    .suggests { _, builder -> SharedSuggestionProvider.suggest(UltraSpaceConfig.ride.modes.map { it.id }, builder) }
                    .executes { start(it.source, listOf(it.source.playerOrException), variant, StringArgumentType.getString(it, "mode")) }
                    .then(
                        Commands.argument("targets", EntityArgument.players())
                            .executes { start(it.source, EntityArgument.getPlayers(it, "targets"), variant, StringArgumentType.getString(it, "mode")) }
                    )
            )

    private fun start(source: CommandSourceStack, players: Collection<ServerPlayer>, variant: UltraWarpVariant, mode: String?): Int {
        var started = 0
        players.forEach { player ->
            val result = UltraWarpRide.startTest(player, variant, mode)
            if (result == UltraWarpRide.Result.STARTED) {
                started++
                source.sendSuccess({ Component.translatable("$KEY.started", player.displayName, variant.speciesName()) }, true)
            } else {
                source.sendFailure(Component.translatable("$KEY.refused", player.displayName, result.message(variant, UltraSpaceConfig.ride.maxSessions)))
            }
        }
        return started
    }

    private fun end(source: CommandSourceStack, players: Collection<ServerPlayer>): Int {
        var ended = 0
        players.forEach { player ->
            if (UltraWarpRide.end(player)) {
                ended++
                source.sendSuccess({ Component.translatable("$KEY.ended", player.displayName) }, true)
            } else {
                source.sendFailure(Component.translatable("$KEY.no_session", player.displayName))
            }
        }
        return ended
    }

    private fun sessions(source: CommandSourceStack): Int {
        val all = UltraWarpSessions.all()
        val max = UltraSpaceConfig.ride.maxSessions
        source.sendSuccess({ Component.translatable("$KEY.sessions", all.size, max) }, false)
        val now = source.server.overworld().gameTime
        all.forEach { session ->
            val name: Component = when {
                session.simulated -> Component.translatable("$KEY.simulated_name")
                else -> source.server.playerList.getPlayer(session.player)?.displayName ?: Component.literal(session.player.toString())
            }
            val seconds = ((now - session.startedAt) / 20).coerceAtLeast(0)
            source.sendSuccess({
                Component.translatable(
                    "$KEY.session_line", name,
                    Component.translatable("generations_core.ultra_warp.variant.${session.variant.key}"),
                    Component.translatable("$KEY.source.${session.source.name.lowercase()}"),
                    seconds,
                    "%.1f".format(session.ride?.lightYears ?: 0.0),
                    "%.0f".format(session.ride?.energy ?: 0.0),
                    session.ride?.gaps ?: 0,
                )
            }, false)
        }
        return all.size
    }

    private fun simulate(source: CommandSourceStack, count: Int): Int {
        val max = UltraSpaceConfig.ride.maxSessions
        val added = UltraWarpSessions.simulate(count, source.level.dimension(), BlockPos.containing(source.position), source.server.overworld().gameTime)
        if (added == 0) {
            source.sendFailure(Component.translatable("$KEY.simulate_full", UltraWarpSessions.count(), max))
            return 0
        }
        source.sendSuccess({ Component.translatable("$KEY.simulate", added, UltraWarpSessions.count(), max) }, true)
        return added
    }

    /** The server's config file again (speed, tunnel, test options), for the next journeys. */
    private fun reload(source: CommandSourceStack): Int {
        UltraSpaceConfig.load()
        UltraWorldsConfig.load()
        // The resources of the chunks generated from now on (the others never change).
        generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.gen.UltraResourcesConfig.load()
        generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.gen.UltraStructuresConfig.load()
        // The suit and the weathers (the events in progress keep their times).
        generations.gg.generations.core.generationscore.common.world.ultraspace.suit.UltraSuitConfig.load()
        generations.gg.generations.core.generationscore.common.world.ultraspace.weather.UltraWeatherConfig.load()
        // The Pokémon of the Ultra worlds (the data, spawn pools and rules, comes with /reload).
        generations.gg.generations.core.generationscore.common.world.ultraspace.spawning.UltraSpawnsConfig.load()
        generations.gg.generations.core.generationscore.common.world.ultraspace.encounter.UltraEncountersConfig.load()
        // The rifts and the wheel's return (phase 6); the open rifts keep their times.
        generations.gg.generations.core.generationscore.common.world.ultraspace.rift.UltraRiftsConfig.load()
        source.sendSuccess({ Component.translatable("$KEY.reloaded", UltraSpaceConfig.ride.maxSessions) }, true)
        return 1
    }

    private fun clear(source: CommandSourceStack): Int {
        val removed = UltraWarpSessions.clearSimulated()
        source.sendSuccess({ Component.translatable("$KEY.cleared", removed) }, true)
        return removed
    }
}
