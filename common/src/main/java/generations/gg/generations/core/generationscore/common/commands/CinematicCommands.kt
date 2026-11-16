package generations.gg.generations.core.generationscore.common.commands

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.permission.Permission
import com.cobblemon.mod.common.api.permission.PermissionLevel
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicEndReason
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicPlaces
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import generations.gg.generations.core.generationscore.common.world.cinematic.LegendaryCinematics
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

/**
 * /generations cinematic (operators, Cobblemon permission generations_core.command.cinematic):
 * - test <articuno|zapdos|moltres> [position]: the scene around that position, or 6 blocks in
 *   front of the player, facing them. Consumes nothing, spawns nothing, starts no battle
 *   (allowed by testMode in cinematics.json);
 * - stop: every scene cut short, the cameras given back;
 * - list: the scenes being played;
 * - reload: cinematics.json again.
 * Registered by CommandsMixin.
 */
object CinematicCommands {
    private const val KEY = "generations_core.cinematic.command"

    private val PERMISSION = object : Permission {
        override val identifier: ResourceLocation = GenerationsCore.id("command.cinematic")
        override val literal: String = "generations_core.command.cinematic"
        override val level: PermissionLevel = PermissionLevel.CHEAT_COMMANDS_AND_COMMAND_BLOCKS
    }

    private fun allowed(source: CommandSourceStack) =
        runCatching { Cobblemon.permissionValidator.hasPermission(source, PERMISSION) }.getOrElse { source.hasPermission(2) }

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val scene = Commands.argument("scene", StringArgumentType.word())
            .suggests { _, builder -> SharedSuggestionProvider.suggest(CinematicScenes.ALL, builder) }
            .executes { test(it.source, StringArgumentType.getString(it, "scene"), null) }
            .then(
                Commands.argument("position", Vec3Argument.vec3())
                    .executes { test(it.source, StringArgumentType.getString(it, "scene"), Vec3Argument.getVec3(it, "position")) }
            )
        dispatcher.register(
            Commands.literal("generations").then(
                Commands.literal("cinematic").requires(::allowed)
                    .then(Commands.literal("test").then(scene))
                    .then(Commands.literal("stop").executes { stop(it.source) })
                    .then(Commands.literal("list").executes { list(it.source) })
                    .then(Commands.literal("reload").executes { reload(it.source) })
            )
        )
    }

    private fun fail(source: CommandSourceStack, message: Component): Int {
        source.sendFailure(message)
        return 0
    }

    private fun test(source: CommandSourceStack, scene: String, position: Vec3?): Int {
        val player = source.playerOrException
        val config = LegendaryCinematics.config
        if (!config.testMode) return fail(source, Component.translatable("$KEY.test_off"))
        if (scene !in CinematicScenes.ALL) return fail(source, Component.translatable("$KEY.unknown", scene))
        val level = player.serverLevel()
        val origin = if (position != null) {
            Vec3(position.x, CinematicPlaces.ground(level, position.x, position.y, position.z), position.z)
        } else {
            CinematicPlaces.testOrigin(level, player.eyePosition, player.y, player.yRot)
        }
        val facing = if (player.position().distanceToSqr(origin.x, player.y, origin.z) > 0.25) CinematicPlaces.yawTowards(origin, player.position()) else player.yRot + 180f
        val speciesId = ResourceLocation.tryParse(config.species(scene)) ?: return fail(source, Component.translatable("$KEY.no_species", config.species(scene)))
        val species = PokemonSpecies.getByIdentifier(speciesId) ?: return fail(source, Component.translatable("$KEY.no_species", speciesId.toString()))
        val form = species.standardForm
        val size = form.hitbox
        val spawn = CinematicPlaces.landing(level, origin, facing, size.width() * form.baseScale.toDouble(), size.height() * form.baseScale.toDouble())
        val result = LegendaryCinematics.start(LegendaryCinematics.Request(level, scene, origin, facing, spawn, player, speciesId, emptySet(), test = true))
        return when (result) {
            is LegendaryCinematics.StartResult.Refused -> fail(source, result.message)
            is LegendaryCinematics.StartResult.Started -> {
                val pos = "%.1f %.1f %.1f".format(origin.x, origin.y, origin.z)
                source.sendSuccess({ Component.translatable("$KEY.started", scene, pos, result.session.participants.size).withStyle(ChatFormatting.AQUA) }, false)
                1
            }
        }
    }

    private fun stop(source: CommandSourceStack): Int {
        val count = LegendaryCinematics.stopAll(CinematicEndReason.STOPPED)
        source.sendSuccess({ Component.translatable("$KEY.stopped", count) }, true)
        return count
    }

    private fun list(source: CommandSourceStack): Int {
        val sessions = LegendaryCinematics.sessions()
        if (sessions.isEmpty()) {
            source.sendSuccess({ Component.translatable("$KEY.none") }, false)
            return 0
        }
        for (session in sessions) {
            val level = source.server.getLevel(session.dimension)
            val elapsed = level?.let { session.machine.elapsedSeconds(it.gameTime) } ?: 0.0
            val line = "%s  %s  %s  %.0f %.0f %.0f  %.1f/%.1f s  %d  %s%s".format(
                session.id.toString().substring(0, 8), session.scene, session.dimension.location(), session.origin.x, session.origin.y, session.origin.z,
                elapsed, session.machine.durationTicks / 20.0, session.participants.size, session.invokerName, if (session.test) "  test" else "",
            )
            source.sendSuccess({ Component.literal(line).withStyle(ChatFormatting.GRAY) }, false)
        }
        return sessions.size
    }

    private fun reload(source: CommandSourceStack): Int {
        LegendaryCinematics.reload()
        source.sendSuccess({ Component.translatable("$KEY.reloaded") }, true)
        return 1
    }
}
