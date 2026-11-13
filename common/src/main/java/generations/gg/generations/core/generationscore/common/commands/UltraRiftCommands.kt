package generations.gg.generations.core.generationscore.common.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import generations.gg.generations.core.generationscore.common.world.ultraspace.rift.UltraRift
import generations.gg.generations.core.generationscore.common.world.ultraspace.rift.UltraRiftPlacement
import generations.gg.generations.core.generationscore.common.world.ultraspace.rift.UltraRifts
import generations.gg.generations.core.generationscore.common.world.ultraspace.rift.UltraRiftsConfig
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTransfers
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTravellers
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Operators' commands of the rifts and the return (phase 6), under /ultrawarp
 * (permission 2): rift create | list | info | expire | cross | spawn | diagnose, and
 * home (the point of return) | home return.
 */
object UltraRiftCommands {
    private const val KEY = "generations_core.ultra_rift.command"

    fun rift(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("rift")
        .then(
            Commands.literal("create").then(
                Commands.argument("destination", StringArgumentType.word())
                    .suggests { _, builder -> SharedSuggestionProvider.suggest(UltraWorlds.IDS, builder) }
                    .executes { create(it.source, StringArgumentType.getString(it, "destination"), 300) }
                    .then(
                        Commands.argument("seconds", IntegerArgumentType.integer(10, 7200))
                            .executes { create(it.source, StringArgumentType.getString(it, "destination"), IntegerArgumentType.getInteger(it, "seconds")) }
                    )
            )
        )
        .then(Commands.literal("list").executes { list(it.source) })
        .then(Commands.literal("info").executes { info(it.source) })
        .then(
            Commands.literal("expire").then(
                Commands.argument("which", StringArgumentType.word())
                    .suggests { context, builder -> SharedSuggestionProvider.suggest(listOf("nearest", "all") + UltraRifts.open(context.source.server).map { it.id.toString() }, builder) }
                    .executes { expire(it.source, StringArgumentType.getString(it, "which")) }
            )
        )
        .then(
            Commands.literal("cross").executes { cross(it.source, "nearest") }.then(
                Commands.argument("which", StringArgumentType.word())
                    .suggests { context, builder -> SharedSuggestionProvider.suggest(listOf("nearest") + UltraRifts.open(context.source.server).map { it.id.toString() }, builder) }
                    .executes { cross(it.source, StringArgumentType.getString(it, "which")) }
            )
        )
        .then(Commands.literal("spawn").executes { spawn(it.source) })
        .then(Commands.literal("diagnose").executes { diagnose(it.source) })

    fun home(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("home")
        .executes { home(it.source) }
        .then(Commands.literal("return").executes { returnHome(it.source) })

    /** In front of the operator, facing them, whatever the rules (an obstruction is told). */
    private fun create(source: CommandSourceStack, destination: String, seconds: Int): Int {
        val player = source.playerOrException
        val world = UltraWorlds.idOf(player.level().dimension()) ?: return fail(source, "$KEY.not_ultra")
        if (destination !in UltraWorlds.IDS) return fail(source, "$KEY.unknown", destination)
        if (UltraWorlds.level(source.server, UltraWorlds.key(destination)) == null) return fail(source, "$KEY.closed", UltraWarpTransfers.worldName(destination))
        val config = UltraRiftsConfig.rifts
        val yaw = player.yRot
        val x = player.x - sin(Math.toRadians(yaw.toDouble())) * 7
        val z = player.z + cos(Math.toRadians(yaw.toDouble())) * 7
        val y = player.eyeY
        val spot = UltraRiftPlacement.Spot(x, y, z, yaw, config.width, config.height)
        UltraRiftPlacement.blocked(player.serverLevel(), x, y, z, yaw, config.width, config.height, 0)?.let {
            source.sendSuccess({ Component.translatable("$KEY.obstructed", it).withStyle(ChatFormatting.GOLD) }, false)
        }
        val server = source.server
        val rift = UltraRifts.create(server, UltraRifts.data(server), world, spot, destination, seconds * 20L, server.overworld().gameTime)
        source.sendSuccess({ Component.translatable("$KEY.created", rift.id, UltraWarpTransfers.worldName(destination), seconds) }, true)
        return rift.id
    }

    private fun list(source: CommandSourceStack): Int {
        val rifts = UltraRifts.open(source.server)
        if (rifts.isEmpty()) return fail(source, "$KEY.none")
        rifts.forEach { source.sendSuccess({ describe(source, it) }, false) }
        return rifts.size
    }

    private fun info(source: CommandSourceStack): Int {
        val rift = nearest(source.playerOrException) ?: return fail(source, "$KEY.none")
        source.sendSuccess({ describe(source, rift) }, false)
        return rift.id
    }

    private fun expire(source: CommandSourceStack, which: String): Int {
        val server = source.server
        val data = UltraRifts.data(server)
        val rifts = when (which) {
            "all" -> UltraRifts.open(server)
            "nearest" -> listOfNotNull(nearest(source.playerOrException))
            else -> UltraRifts.open(server).filter { it.id.toString() == which }
        }
        if (rifts.isEmpty()) return fail(source, "$KEY.none")
        rifts.forEach { UltraRifts.close(server, data, it, "closed by ${source.textName}") }
        source.sendSuccess({ Component.translatable("$KEY.expired", rifts.size) }, true)
        return rifts.size
    }

    /** The crossing's effect without crossing (no mount needed): the same path as a real one. */
    private fun cross(source: CommandSourceStack, which: String): Int {
        val player = source.playerOrException
        val server = source.server
        val rift = if (which == "nearest") nearest(player) else UltraRifts.open(server).firstOrNull { it.id.toString() == which && it.world == UltraWorlds.idOf(player.level().dimension()) }
        rift ?: return fail(source, "$KEY.none")
        if (UltraWarpTransfers.busy(player.uuid)) return fail(source, "$KEY.busy")
        val ok = UltraRifts.use(server, UltraRifts.data(server), rift, player, server.overworld().gameTime, server.tickCount)
        return if (ok) 1 else 0
    }

    /** An opening near the operator now, by the natural rules (the chance and the maximum per world aside). */
    private fun spawn(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val world = UltraWorlds.idOf(player.level().dimension()) ?: return fail(source, "$KEY.not_ultra")
        val server = source.server
        val reason = UltraRifts.tryNear(server, UltraRifts.data(server), player.serverLevel(), world, player, server.overworld().gameTime)
        source.sendSuccess({ Component.translatable("$KEY.spawn", reason) }, false)
        return if (reason == "ok") 1 else 0
    }

    private fun diagnose(source: CommandSourceStack): Int {
        val config = UltraRiftsConfig.rifts
        val server = source.server
        val open = UltraRifts.open(server)
        val lines = mutableListOf(
            "rifts ${if (config.enabled) "on" else "off"}, max ${config.maxPerDimension}/world, try every ${config.attemptIntervalSeconds} s at ${(config.spawnChance * 100).toInt()} %, " +
                "life ${config.lifetimeSeconds.min.toInt()}-${config.lifetimeSeconds.max.toInt()} s, single use ${config.singleUse}, arrival ${config.arrival}",
            "mounts ${config.mounts}, wheel return ${if (UltraRiftsConfig.wheelReturn.enabled) "on" else "off"} (${UltraRiftsConfig.wheelReturn.species})",
        )
        for (world in UltraWorlds.IDS) {
            val here = open.count { it.world == world }
            val players = UltraWorlds.key(world)?.let { server.getLevel(it) }?.players()?.size ?: 0
            lines += "$world: $here open, $players players, last try: ${UltraRifts.lastAttempt[world] ?: "-"}"
        }
        source.player?.let { player ->
            val mount = UltraRifts.mountOf(player)
            lines += "you: ${if (mount != null) "on ${mount.pokemon.species.name}, can cross" else "no valid mount (Solgaleo/Lunala ridden and steered)"}" +
                ", busy ${UltraWarpTransfers.busy(player.uuid)}"
        }
        lines.forEach { line -> source.sendSuccess({ Component.literal(line).withStyle(ChatFormatting.GRAY) }, false) }
        return open.size
    }

    private fun home(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val traveller = UltraWarpTravellers.get(source.server, player.uuid)
        val home = traveller?.home
        source.sendSuccess({
            if (home != null) Component.translatable("$KEY.home", home.toString()) else Component.translatable("$KEY.no_home")
        }, false)
        traveller?.pendingRift?.let { pending -> source.sendSuccess({ Component.literal("pending rift: ${pending.world} ${pending.x} ${pending.z}").withStyle(ChatFormatting.GRAY) }, false) }
        if (traveller?.pendingReturn == true) source.sendSuccess({ Component.literal("pending return").withStyle(ChatFormatting.GRAY) }, false)
        return if (home != null) 1 else 0
    }

    /** The wheel's return without the Pokémon (its transition included). */
    private fun returnHome(source: CommandSourceStack): Int {
        val player = source.playerOrException
        if (!UltraWarpTransfers.returnHome(player, (UltraRiftsConfig.wheelReturn.transitionSeconds * 20).toInt())) return fail(source, "$KEY.busy")
        return 1
    }

    private fun nearest(player: ServerPlayer): UltraRift? {
        val world = UltraWorlds.idOf(player.level().dimension()) ?: return null
        return UltraRifts.open(player.server).filter { it.world == world }.minByOrNull { player.distanceToSqr(it.x, it.y, it.z) }
    }

    private fun describe(source: CommandSourceStack, rift: UltraRift): Component {
        val remaining = ((rift.expires - source.server.overworld().gameTime) / 20).coerceAtLeast(0)
        val distance = source.player?.takeIf { UltraWorlds.idOf(it.level().dimension()) == rift.world }?.let { " (%.0f m)".format(Math.sqrt(it.distanceToSqr(rift.x, rift.y, rift.z))) } ?: ""
        return Component.translatable(
            "$KEY.describe", rift.id, UltraWarpTransfers.worldName(rift.world), "%.0f %.0f %.0f".format(rift.x, rift.y, rift.z),
            UltraWarpTransfers.worldName(rift.destination), remaining,
        ).append(Component.literal(distance))
    }

    private fun fail(source: CommandSourceStack, key: String, vararg args: Any): Int {
        source.sendFailure(Component.translatable(key, *args))
        return 0
    }
}
