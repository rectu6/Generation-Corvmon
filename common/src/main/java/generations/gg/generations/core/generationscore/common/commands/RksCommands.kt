package generations.gg.generations.core.generationscore.common.commands

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.permission.Permission
import com.cobblemon.mod.common.api.permission.PermissionLevel
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import generations.gg.generations.core.generationscore.common.world.cinematic.LegendaryCinematics
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems
import generations.gg.generations.core.generationscore.common.world.level.block.GenerationsUtilityBlocks
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksConsoleBlockEntity
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksModuleBlock
import generations.gg.generations.core.generationscore.common.world.recipe.PokemonResult
import generations.gg.generations.core.generationscore.common.world.rks.RksSettings
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksMachines
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksState
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/**
 * /generations rks (RKS-PLAN.md § 6; operators, Cobblemon permission generations_core.command.rks,
 * level 2), on the machine looked at, otherwise the nearest console within 16 blocks:
 * - give modules|dossiers|kit [player]: the five modules, the three research files, a repair kit;
 * - energy <0-100>: the psychic energy;
 * - prepare: Mewtwo's components and the research files into the EMPTY slots only;
 * - start: the M2 experiment at once, nothing taken;
 * - progress <percent>, speed <multiplier>: the growth;
 * - cinematic: the awakening's scene alone (no Mewtwo, no damage);
 * - damage, repair: the damaged state, the repair;
 * - reset: the experiment cancelled, nothing given back nor made, the slots left as they are;
 * - info: the machine's state.
 * Registered by CommandsMixin.
 */
object RksCommands {
    private const val KEY = "generations_core.rks.command"

    private val PERMISSION = object : Permission {
        override val identifier: ResourceLocation = GenerationsCore.id("command.rks")
        override val literal: String = "generations_core.command.rks"
        override val level: PermissionLevel = PermissionLevel.CHEAT_COMMANDS_AND_COMMAND_BLOCKS
    }

    private fun allowed(source: CommandSourceStack) =
        runCatching { Cobblemon.permissionValidator.hasPermission(source, PERMISSION) }.getOrElse { source.hasPermission(2) }

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        fun give(what: String) = Commands.literal(what)
            .executes { give(it.source, what, it.source.playerOrException) }
            .then(Commands.argument("player", EntityArgument.player()).executes { give(it.source, what, EntityArgument.getPlayer(it, "player")) })
        dispatcher.register(
            Commands.literal("generations").then(
                Commands.literal("rks").requires(::allowed)
                    .then(Commands.literal("give").then(give("modules")).then(give("dossiers")).then(give("kit")))
                    .then(Commands.literal("energy").then(Commands.argument("value", IntegerArgumentType.integer(0, 10_000)).executes { energy(it) }))
                    .then(Commands.literal("prepare").executes { onMachine(it.source) { c -> prepare(it.source, c) } })
                    .then(Commands.literal("start").executes { onMachine(it.source) { c -> start(it.source, c) } })
                    .then(Commands.literal("progress").then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0)).executes { progress(it) }))
                    .then(Commands.literal("speed").then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0, 1000.0)).executes { speed(it) }))
                    .then(Commands.literal("cinematic").executes { onMachine(it.source) { c -> cinematic(it.source, c) } })
                    .then(Commands.literal("damage").executes { onMachine(it.source) { c -> damage(it.source, c) } })
                    .then(Commands.literal("repair").executes { onMachine(it.source) { c -> repair(it.source, c) } })
                    .then(Commands.literal("reset").executes { onMachine(it.source) { c -> reset(it.source, c) } })
                    .then(Commands.literal("info").executes { onMachine(it.source) { c -> info(it.source, c) } })
            )
        )
    }

    private fun fail(source: CommandSourceStack, key: String, vararg args: Any): Int {
        source.sendFailure(Component.translatable("$KEY.$key", *args))
        return 0
    }

    private fun ok(source: CommandSourceStack, key: String, vararg args: Any): Int {
        source.sendSuccess({ Component.translatable("$KEY.$key", *args).withStyle(ChatFormatting.AQUA) }, true)
        return 1
    }

    /** The console looked at (any module of its machine), otherwise the nearest within 16 blocks. */
    private fun target(source: CommandSourceStack): RksConsoleBlockEntity? {
        val level = source.level
        val player = source.player
        if (player != null) {
            val eye = player.eyePosition
            val hit = level.clip(ClipContext(eye, eye.add(player.lookAngle.scale(8.0)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
            if (hit.type == HitResult.Type.BLOCK) {
                val pos = (hit as BlockHitResult).blockPos
                val state = level.getBlockState(pos)
                if (state.block is RksModuleBlock) RksMachines.consoleOf(level, pos, state)?.let { return it }
            }
        }
        val centre = BlockPos.containing(source.position)
        var best: RksConsoleBlockEntity? = null
        var bestDistance = Double.MAX_VALUE
        for (pos in BlockPos.betweenClosed(centre.offset(-16, -8, -16), centre.offset(16, 8, 16))) {
            val be = level.getBlockEntity(pos) as? RksConsoleBlockEntity ?: continue
            val d = pos.distSqr(centre)
            if (d < bestDistance) {
                bestDistance = d
                best = be
            }
        }
        return best
    }

    private fun onMachine(source: CommandSourceStack, action: (RksConsoleBlockEntity) -> Int): Int {
        val console = target(source) ?: return fail(source, "no_machine")
        return action(console)
    }

    private fun give(source: CommandSourceStack, what: String, player: ServerPlayer): Int {
        val stacks = when (what) {
            "modules" -> GenerationsUtilityBlocks.RKS_MODULES.values.map { ItemStack(it.value()) }
            "dossiers" -> listOf(GenerationsItems.RKS_RESEARCH_M01_GENETICS, GenerationsItems.RKS_RESEARCH_M02_STABILIZATION, GenerationsItems.RKS_RESEARCH_M03_CONTAINMENT).map { ItemStack(it.value()) }
            else -> listOf(ItemStack(GenerationsItems.RKS_REPAIR_KIT.value()))
        }
        for (stack in stacks) if (!player.inventory.add(stack)) player.drop(stack, false)
        return ok(source, "given", stacks.size, player.displayName ?: player.name)
    }

    private fun energy(context: CommandContext<CommandSourceStack>): Int = onMachine(context.source) { console ->
        val value = IntegerArgumentType.getInteger(context, "value")
        console.commandEnergy(value)
        ok(context.source, "energy", console.energy, RksSettings.mewtwo.energyRequired)
    }

    private fun prepare(source: CommandSourceStack, console: RksConsoleBlockEntity): Int {
        val level = source.level
        val recipe = RksMachines.mewtwoRecipe(level)?.value() ?: return fail(source, "no_recipe")
        var filled = 0
        for (input in recipe.inputs) {
            val example = input.ingredient.matchingStacks().firstOrNull() ?: continue
            // Already enough of it in the slots: nothing to add.
            val present = (0 until RksConsoleBlockEntity.INPUTS).sumOf { i -> console.items[i].let { if (input.ingredient.matches(it)) it.count else 0 } }
            if (present >= input.count) continue
            val slot = (0 until RksConsoleBlockEntity.INPUTS).firstOrNull { console.items[it].isEmpty } ?: break
            console.items[slot] = example.copyWithCount(minOf(input.count - present, example.maxStackSize))
            filled++
        }
        for ((i, item) in console.researchItems().withIndex()) {
            val slot = RksConsoleBlockEntity.RESEARCH + i
            if (console.items[slot].isEmpty) {
                console.items[slot] = ItemStack(item)
                filled++
            }
        }
        console.setChanged()
        return ok(source, "prepared", filled)
    }

    private fun start(source: CommandSourceStack, console: RksConsoleBlockEntity): Int {
        if (!console.commandStart(source.playerOrException)) return fail(source, "cannot_start", console.state.name)
        return ok(source, "started")
    }

    private fun progress(context: CommandContext<CommandSourceStack>): Int = onMachine(context.source) { console ->
        if (!console.machine.experiment) return@onMachine fail(context.source, "no_experiment")
        val percent = DoubleArgumentType.getDouble(context, "percent")
        console.commandProgress(percent / 100.0)
        ok(context.source, "progress", "%.0f".format(console.machine.progress * 100.0))
    }

    private fun speed(context: CommandContext<CommandSourceStack>): Int = onMachine(context.source) { console ->
        val speed = DoubleArgumentType.getDouble(context, "multiplier")
        console.commandSpeed(speed)
        ok(context.source, "speed", "%.2f".format(console.machine.speed))
    }

    /** The scene alone, in test mode: no Mewtwo, no damage, the machine as it is. */
    private fun cinematic(source: CommandSourceStack, console: RksConsoleBlockEntity): Int {
        val player = source.playerOrException
        val level = source.level
        val result = RksMachines.mewtwoRecipe(level)?.value()?.result as? PokemonResult
        val species = result?.species ?: ResourceLocation.fromNamespaceAndPath("cobblemon", "mewtwo")
        val form = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByIdentifier(species)?.standardForm
        val landing = generations.gg.generations.core.generationscore.common.world.cinematic.CinematicPlaces.landing(
            level, console.origin, console.facing.toYRot(), form?.let { it.hitbox.width() * it.baseScale.toDouble() } ?: 1.2, form?.let { it.hitbox.height() * it.baseScale.toDouble() } ?: 2.0,
        )
        val started = LegendaryCinematics.start(
            LegendaryCinematics.Request(level, CinematicScenes.MEWTWO, console.origin, console.facing.toYRot(), landing, player, species, result?.aspects ?: emptySet(), test = true),
        )
        return when (started) {
            is LegendaryCinematics.StartResult.Refused -> {
                source.sendFailure(started.message)
                0
            }
            is LegendaryCinematics.StartResult.Started -> ok(source, "cinematic", started.session.participants.size, started.session.ambient.size)
        }
    }

    private fun damage(source: CommandSourceStack, console: RksConsoleBlockEntity): Int {
        if (!console.commandDamage()) return fail(source, "cannot_damage", console.state.name)
        return ok(source, "damaged")
    }

    private fun repair(source: CommandSourceStack, console: RksConsoleBlockEntity): Int {
        console.commandRepair()
        return ok(source, "repaired")
    }

    private fun reset(source: CommandSourceStack, console: RksConsoleBlockEntity): Int {
        console.commandReset()
        return ok(source, "reset")
    }

    private fun info(source: CommandSourceStack, console: RksConsoleBlockEntity): Int {
        val m = console.machine
        val missing = console.missing.joinToString(", ") { it.id }.ifEmpty { "-" }
        val pos = console.blockPos
        val line = "RKS %d %d %d  %s  %s  energy %d/%d  analysed %s  experiment %s  growth %.1f%% x%.2f  spawned %s  damaged %s  missing %s  revision %d".format(
            pos.x, pos.y, pos.z, console.facing.serializedName, m.state.name, console.energy, RksSettings.mewtwo.energyRequired,
            m.analysed, m.experiment, m.progress * 100.0, m.speed, m.spawned, m.damaged, missing, m.revision,
        )
        source.sendSuccess({ Component.literal(line).withStyle(if (m.state == RksState.DAMAGED) ChatFormatting.RED else ChatFormatting.GRAY) }, false)
        return 1
    }
}
