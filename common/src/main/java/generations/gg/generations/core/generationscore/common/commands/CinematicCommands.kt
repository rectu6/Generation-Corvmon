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
import generations.gg.generations.core.generationscore.common.world.lugia.LugiaSummon
import generations.gg.generations.core.generationscore.common.world.regi.RegiSummon
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
 * - reload: cinematics.json again;
 * - lugia [real]: Lugia's scene at the Lugia shrine looked at (or 6 blocks ahead): nothing taken
 *   and no Pokémon; real (a shrine needed): the shrine's song, the scene and a real Lugia level
 *   70 at its end, nothing taken (LUGIA-PLAN.md § 6); lugia pending [clear]: the summonings
 *   waiting for their Lugia after a crash.
 * - regi <regirock|regice|registeel|regieleki|regidrago|regigigas> [real]: the same for the Regis
 *   (REGIS-PLAN.md § 6), at the Regi or Regigigas shrine looked at; letters are drawn for the
 *   scene, none is touched; regi pending [clear].
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
                    .then(
                        Commands.literal("lugia").executes { lugia(it.source, false) }
                            .then(Commands.literal("real").executes { lugia(it.source, true) })
                            .then(
                                Commands.literal("pending").executes { lugiaPending(it.source, false) }
                                    .then(Commands.literal("clear").executes { lugiaPending(it.source, true) })
                            )
                    )
                    .then(
                        Commands.literal("regi")
                            .then(
                                Commands.literal("pending").executes { regiPending(it.source, false) }
                                    .then(Commands.literal("clear").executes { regiPending(it.source, true) })
                            )
                            .then(
                                Commands.argument("species", StringArgumentType.word())
                                    .suggests { _, builder -> SharedSuggestionProvider.suggest(CinematicScenes.REGIS, builder) }
                                    .executes { regi(it.source, StringArgumentType.getString(it, "species"), false) }
                                    .then(Commands.literal("real").executes { regi(it.source, StringArgumentType.getString(it, "species"), true) })
                            )
                    )
                    .then(
                        Commands.literal("latias").executes { eon(it.source, CinematicScenes.LATIAS, false) }
                            .then(Commands.literal("real").executes { eon(it.source, CinematicScenes.LATIAS, true) })
                    )
                    .then(
                        Commands.literal("latios").executes { eon(it.source, CinematicScenes.LATIOS, false) }
                            .then(Commands.literal("real").executes { eon(it.source, CinematicScenes.LATIOS, true) })
                    )
                    .then(Commands.literal("eon").then(Commands.literal("pending").executes { eonPending(it.source) }))
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
        if (scene == CinematicScenes.LUGIA) {
            // Its own frame (the whirlpool on water near it, or on the ground beyond it).
            LugiaSummon.admin(player, false, origin)?.let { return fail(source, it) }
            val pos = "%.1f %.1f %.1f".format(origin.x, origin.y, origin.z)
            source.sendSuccess({ Component.translatable("$KEY.started", scene, pos, 1).withStyle(ChatFormatting.AQUA) }, false)
            return 1
        }
        if (scene in CinematicScenes.REGIS) {
            // Their own frame (facing the player, the room measured, letters drawn).
            RegiSummon.admin(player, scene, false, origin)?.let { return fail(source, it) }
            val pos = "%.1f %.1f %.1f".format(origin.x, origin.y, origin.z)
            source.sendSuccess({ Component.translatable("$KEY.started", scene, pos, 1).withStyle(ChatFormatting.AQUA) }, false)
            return 1
        }
        if (scene in CinematicScenes.EON) {
            // Its own frame: where the player stands, facing their way (as the Enigma Stone's summoning).
            generations.gg.generations.core.generationscore.common.world.eon.EonSummon.admin(player, scene, false)?.let { return fail(source, it) }
            val pos = "%.1f %.1f %.1f".format(player.x, player.y, player.z)
            source.sendSuccess({ Component.translatable("$KEY.started", scene, pos, 1).withStyle(ChatFormatting.AQUA) }, false)
            return 1
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

    /**
     * latias|latios [real]: the Enigma Stone's scene where the player stands (EON-PLAN.md § 8).
     * Test: nothing taken, no Pokémon. Real: the whole summoning without a stone, one Pokémon level 65.
     */
    private fun eon(source: CommandSourceStack, scene: String, real: Boolean): Int {
        val player = source.playerOrException
        if (!real && !LegendaryCinematics.config.testMode) return fail(source, Component.translatable("$KEY.test_off"))
        generations.gg.generations.core.generationscore.common.world.eon.EonSummon.admin(player, scene, real)?.let { return fail(source, it) }
        source.sendSuccess({ Component.translatable(if (real) "generations_core.cinematic.eon.real" else "generations_core.cinematic.eon.test", scene).withStyle(ChatFormatting.AQUA) }, real)
        return 1
    }

    private fun eonPending(source: CommandSourceStack): Int {
        val lines = generations.gg.generations.core.generationscore.common.world.eon.EonSummon.pending(source.server)
        if (lines.isEmpty()) source.sendSuccess({ Component.translatable("generations_core.cinematic.eon.pending_none") }, false)
        for (line in lines) source.sendSuccess({ Component.literal(line) }, false)
        return lines.size
    }

    private fun lugia(source: CommandSourceStack, real: Boolean): Int {
        val player = source.playerOrException
        if (!real && !LegendaryCinematics.config.testMode) return fail(source, Component.translatable("$KEY.test_off"))
        LugiaSummon.admin(player, real)?.let { return fail(source, it) }
        source.sendSuccess({ Component.translatable(if (real) "generations_core.cinematic.lugia.real" else "generations_core.cinematic.lugia.test").withStyle(ChatFormatting.AQUA) }, real)
        return 1
    }

    private fun lugiaPending(source: CommandSourceStack, clear: Boolean): Int {
        if (clear) {
            val count = LugiaSummon.clearPending(source.server)
            source.sendSuccess({ Component.translatable("generations_core.cinematic.lugia.cleared", count) }, true)
            return count
        }
        val lines = LugiaSummon.pending(source.server)
        if (lines.isEmpty()) source.sendSuccess({ Component.translatable("generations_core.cinematic.lugia.pending_none") }, false)
        for (line in lines) source.sendSuccess({ Component.literal(line).withStyle(ChatFormatting.GRAY) }, false)
        return lines.size
    }

    private fun regi(source: CommandSourceStack, scene: String, real: Boolean): Int {
        val player = source.playerOrException
        if (scene !in CinematicScenes.REGIS) return fail(source, Component.translatable("$KEY.unknown", scene))
        if (!real && !LegendaryCinematics.config.testMode) return fail(source, Component.translatable("$KEY.test_off"))
        RegiSummon.admin(player, scene, real)?.let { return fail(source, it) }
        source.sendSuccess({ Component.translatable(if (real) "generations_core.cinematic.regi.real" else "generations_core.cinematic.regi.test", scene).withStyle(ChatFormatting.AQUA) }, real)
        return 1
    }

    private fun regiPending(source: CommandSourceStack, clear: Boolean): Int {
        if (clear) {
            val count = RegiSummon.clearPending(source.server)
            source.sendSuccess({ Component.translatable("generations_core.cinematic.regi.cleared", count) }, true)
            return count
        }
        val lines = RegiSummon.pending(source.server)
        if (lines.isEmpty()) source.sendSuccess({ Component.translatable("generations_core.cinematic.regi.pending_none") }, false)
        for (line in lines) source.sendSuccess({ Component.literal(line).withStyle(ChatFormatting.GRAY) }, false)
        return lines.size
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
