package generations.gg.generations.core.generationscore.common.commands

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.permission.Permission
import com.cobblemon.mod.common.api.permission.PermissionLevel
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.party
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import generations.gg.generations.core.generationscore.common.GenerationsCore
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import generations.gg.generations.core.generationscore.common.network.packets.boss.C2SBossRewardsSavePacket
import generations.gg.generations.core.generationscore.common.network.packets.boss.S2CBossRewardsEditorPacket
import generations.gg.generations.core.generationscore.common.world.boss.BossConfig
import generations.gg.generations.core.generationscore.common.world.boss.BossMegaSavedData
import generations.gg.generations.core.generationscore.common.world.boss.BossMegas
import generations.gg.generations.core.generationscore.common.world.boss.BossRewards
import generations.gg.generations.core.generationscore.common.world.boss.BossVictory
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer
import generations.gg.generations.core.generationscore.common.world.boss.BossData
import generations.gg.generations.core.generationscore.common.world.boss.BossRarity
import generations.gg.generations.core.generationscore.common.world.boss.BossRegistry
import generations.gg.generations.core.generationscore.common.world.boss.BossSpawnManager
import generations.gg.generations.core.generationscore.common.world.boss.Bosses
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import kotlin.math.sqrt

/**
 * /boss, with Cobblemon's permissions (a permission mod such as LuckPerms decides,
 * else the level of general.json, 2 by default: operators, or commands allowed in solo):
 *
 * - spawn <species> [rarity] [titan]: a boss where the command is run, at the level
 *   of the highest Pokémon of the party (50 without a party); rarity drawn when not given;
 * - inspect: the nearest boss within 64 blocks;
 * - kill [all]: removes the nearest boss (all: every loaded boss of the dimension),
 *   except those in battle;
 * - reload: the files of config/generations/core/bosses/ again;
 * - spawninfo: the spawn settings, the living bosses and why spawns were refused;
 * - rewards: the rewards editor (BossRewardsEditorScreen); rewards test <rarity> [titan]:
 *   a draw given to the player, with the victory screen;
 * - megas [player]: the Mega Stones a player got from bosses; megas reset [player].
 * A Legendary boss is always a Mega form: spawn <species> legendary needs a species with
 * a Mega (random X/Y), spawn mega: any Mega.
 *
 * Registered by CommandsMixin.
 */
object BossCommands {
    private const val KEY = "generations_core.boss.command"
    private const val RANGE = 64.0
    /** /boss spawn mega: a Legendary boss in any Mega form. */
    private const val RANDOM_MEGA = "mega"

    private class BossPermission(node: String) : Permission {
        override val identifier: ResourceLocation = GenerationsCore.id("command.boss.$node")
        override val literal: String = "generations_core.command.boss.$node"
        override val level: PermissionLevel
            get() = PermissionLevel.entries.firstOrNull { it.numericalValue == BossConfig.general.commandPermissionLevel }
                ?: PermissionLevel.CHEAT_COMMANDS_AND_COMMAND_BLOCKS
    }

    private val SPAWN = BossPermission("spawn")
    private val INSPECT = BossPermission("inspect")
    private val KILL = BossPermission("kill")
    private val RELOAD = BossPermission("reload")
    private val SPAWNINFO = BossPermission("spawninfo")
    private val REWARDS = BossPermission("rewards")
    private val ALL = listOf(SPAWN, INSPECT, KILL, RELOAD, SPAWNINFO, REWARDS)

    private fun allowed(source: CommandSourceStack, permission: Permission) =
        runCatching { Cobblemon.permissionValidator.hasPermission(source, permission) }
            .getOrElse { source.hasPermission(permission.level.numericalValue) }

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("boss")
                .requires { source -> ALL.any { allowed(source, it) } }
                .then(spawn())
                .then(Commands.literal("inspect").requires { allowed(it, INSPECT) }.executes { inspect(it.source) })
                .then(
                    Commands.literal("kill").requires { allowed(it, KILL) }
                        .executes { kill(it.source, false) }
                        .then(Commands.literal("all").executes { kill(it.source, true) })
                )
                .then(Commands.literal("reload").requires { allowed(it, RELOAD) }.executes { reload(it.source) })
                .then(Commands.literal("spawninfo").requires { allowed(it, SPAWNINFO) }.executes { spawnInfo(it.source) })
                .then(rewards())
                .then(megas())
        )
    }

    private fun spawn(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("spawn").requires { allowed(it, SPAWN) }.then(
            Commands.argument("species", StringArgumentType.word())
                .suggests { _, builder -> SharedSuggestionProvider.suggest(listOf(RANDOM_MEGA) + PokemonSpecies.implemented.map { it.resourceIdentifier.path }, builder) }
                .executes { spawn(it, null, false) }
                .then(
                    Commands.argument("rarity", StringArgumentType.word())
                        .suggests { _, builder -> SharedSuggestionProvider.suggest(BossRarity.entries.map { it.id }, builder) }
                        .executes { spawn(it, StringArgumentType.getString(it, "rarity"), false) }
                        .then(Commands.literal("titan").executes { spawn(it, StringArgumentType.getString(it, "rarity"), true) })
                )
        )

    private fun findSpecies(name: String): Species? {
        val text = name.trim().lowercase()
        val id = ResourceLocation.tryParse(if (':' in text) text else "cobblemon:$text") ?: return null
        return PokemonSpecies.getByIdentifier(id)
    }

    private fun spawn(context: CommandContext<CommandSourceStack>, rarityName: String?, titan: Boolean): Int {
        val source = context.source
        val name = StringArgumentType.getString(context, "species")
        val randomMega = name.equals(RANDOM_MEGA, ignoreCase = true)
        val species = if (randomMega) null else findSpecies(name) ?: return fail(source, "unknown_species", name)
        var rarity = if (rarityName == null) BossSpawnManager.drawRarity() else BossRarity.byId(rarityName) ?: return fail(source, "unknown_rarity", rarityName)
        // "mega": a Legendary boss in any Mega form. A Legendary boss is always a Mega form.
        if (randomMega) rarity = BossRarity.LEGENDARY
        val megas = if (species == null) BossMegas.all() else BossMegas.formsOf(species)
        if (rarity == BossRarity.LEGENDARY && megas.isEmpty()) {
            if (rarityName != null || randomMega) return fail(source, "no_mega", species?.translatedName ?: Component.literal(name))
            rarity = BossRarity.EPIC
        }
        val level = source.level
        val pokemonLevel = (source.player?.party()?.maxOfOrNull { it.level } ?: 50).coerceIn(1, Cobblemon.config.maxPokemonLevel)
        val entity = runCatching {
            if (rarity == BossRarity.LEGENDARY) {
                val mega = BossMegas.draw(null, megas) ?: megas.random()
                PokemonEntity(level, BossMegas.create(mega, pokemonLevel))
            } else {
                PokemonProperties.parse("species=\"${species!!.resourceIdentifier}\" level=$pokemonLevel").createEntity(level)
            }
        }.getOrElse {
            GenerationsCore.LOGGER.error("Bosses: could not make {} ({})", name, it.message)
            return fail(source, "spawn_failed", species?.translatedName ?: Component.literal(name))
        }
        val pos = source.position
        entity.moveTo(pos.x, pos.y, pos.z, source.rotation.y, 0f)
        val data = Bosses.make(entity.pokemon, rarity, titan, BossData.SOURCE_COMMAND, level.server.overworld().gameTime)
        if (!level.addFreshEntity(entity)) return fail(source, "spawn_failed", Bosses.speciesName(entity.pokemon))
        BossRegistry.track(entity, level)
        val scale = entity.pokemon.scaleModifier / data.originalScale
        source.sendSuccess({
            Component.translatable(
                "$KEY.spawned", Bosses.tierName(rarity, titan), Bosses.speciesName(entity.pokemon), pokemonLevel,
                "%.2f".format(scale), "%.1f".format(Bosses.heightAt(entity.pokemon, entity.pokemon.scaleModifier)),
            )
        }, true)
        GenerationsCore.LOGGER.info("Bosses: /boss spawn {} {}{} at {}", entity.pokemon.species.resourceIdentifier, rarity.id, if (titan) " titan" else "", entity.blockPosition().toShortString())
        return 1
    }

    private fun nearest(source: CommandSourceStack): BossRegistry.Entry? {
        val pos = source.position
        return BossRegistry.alive(source.level)
            .map { it to it.entity.position().distanceToSqr(pos) }
            .filter { it.second <= RANGE * RANGE }
            .minByOrNull { it.second }?.first
    }

    private fun inspect(source: CommandSourceStack): Int {
        val entry = nearest(source) ?: return fail(source, "none_near", RANGE.toInt())
        val entity = entry.entity
        val pokemon = entity.pokemon
        val data = Bosses.of(pokemon) ?: return fail(source, "none_near", RANGE.toInt())
        val now = source.level.server.overworld().gameTime
        val ageMinutes = (now - data.spawnedAt) / 1200.0
        val lifetime = BossConfig.spawns.lifetimeMinutes
        val left: Component = if (lifetime <= 0.0) Component.translatable("$KEY.never") else Component.literal("%.1f min".format((lifetime - ageMinutes).coerceAtLeast(0.0)))
        val battle = Component.translatable(if (entity.isBattling) "$KEY.yes" else "$KEY.no")
        val distance = sqrt(entity.position().distanceToSqr(source.position)).toInt()
        source.sendSuccess({ Component.translatable("$KEY.inspect.head", Bosses.tierName(data.rarity, data.titan), Bosses.speciesName(pokemon), pokemon.level) }, false)
        source.sendSuccess({
            Component.translatable(
                "$KEY.inspect.size", "%.2f".format(pokemon.scaleModifier / data.originalScale), "%.2f".format(data.originalScale),
                "%.1f".format(Bosses.heightAt(pokemon, pokemon.scaleModifier)),
            )
        }, false)
        source.sendSuccess({ Component.translatable("$KEY.inspect.state", Component.translatable("$KEY.source.${data.source}"), "%.1f".format(ageMinutes), left, battle) }, false)
        source.sendSuccess({ Component.translatable("$KEY.inspect.where", entity.blockPosition().toShortString(), distance, data.id.toString().take(8)) }, false)
        return 1
    }

    private fun kill(source: CommandSourceStack, all: Boolean): Int {
        val targets = if (all) BossRegistry.alive(source.level) else listOfNotNull(nearest(source))
        if (targets.isEmpty()) return fail(source, "none_near", RANGE.toInt())
        var removed = 0
        var fighting = 0
        for (entry in targets) {
            if (entry.entity.isBattling) {
                fighting++
                continue
            }
            BossRegistry.forget(entry.entity)
            entry.entity.discard()
            removed++
        }
        source.sendSuccess({ Component.translatable("$KEY.killed", removed) }, true)
        if (fighting > 0) source.sendSuccess({ Component.translatable("$KEY.kill_in_battle", fighting).withStyle(ChatFormatting.GOLD) }, false)
        return removed
    }

    private fun reload(source: CommandSourceStack): Int {
        BossConfig.load()
        BossMegas.invalidate()
        BossRewards.load(source.server.registryAccess())
        source.sendSuccess({ Component.translatable("$KEY.reloaded") }, true)
        return 1
    }

    private fun spawnInfo(source: CommandSourceStack): Int {
        val spawns = BossConfig.spawns
        val level = source.level
        val on = BossConfig.general.enabled && spawns.enabled
        val here = when {
            level.dimension().location().toString() in spawns.excludedDimensions -> "$KEY.here.excluded"
            !spawns.allowUltraWorlds && UltraWorlds.isUltra(level.dimension()) -> "$KEY.here.ultra"
            else -> "$KEY.here.allowed"
        }
        val bosses = BossRegistry.alive(level)
        val pos = source.position
        val cellX = BossRegistry.cell(pos.x, spawns.cellSize)
        val cellZ = BossRegistry.cell(pos.z, spawns.cellSize)
        val inCell = bosses.count { BossRegistry.cell(it.x, spawns.cellSize) == cellX && BossRegistry.cell(it.z, spawns.cellSize) == cellZ }
        val lastMinute = BossSpawnManager.lastMinute(level.server.overworld().gameTime)
        val refused = BossSpawnManager.refused.entries.filter { it.value > 0 }.joinToString(", ") { "${it.key.key} ${it.value}" }.ifEmpty { "-" }

        source.sendSuccess({
            Component.translatable(
                "$KEY.spawninfo.config", Component.translatable(if (on) "$KEY.on" else "$KEY.off"), spawns.chance,
                spawns.maxPerDimension, spawns.maxPerCell, spawns.cellSize, spawns.maxPerMinute,
            )
        }, false)
        source.sendSuccess({
            Component.translatable(
                "$KEY.spawninfo.distances", spawns.minPlayerDistance.toInt(), spawns.maxPlayerDistance.toInt(),
                spawns.minDistanceBetweenBosses.toInt(), spawns.lifetimeMinutes,
            )
        }, false)
        source.sendSuccess({ Component.translatable(here) }, false)
        source.sendSuccess({
            Component.translatable("$KEY.spawninfo.live", bosses.size, spawns.maxPerDimension, inCell, spawns.maxPerCell, lastMinute, spawns.maxPerMinute)
        }, false)
        source.sendSuccess({ Component.translatable("$KEY.spawninfo.totals", BossSpawnManager.seen, BossSpawnManager.converted) }, false)
        source.sendSuccess({ Component.translatable("$KEY.spawninfo.refused", refused) }, false)
        for (entry in bosses.sortedBy { it.entity.position().distanceToSqr(pos) }.take(8)) {
            val data = Bosses.of(entry.pokemon) ?: continue
            val distance = sqrt(entry.entity.position().distanceToSqr(pos)).toInt()
            source.sendSuccess({
                Component.translatable("$KEY.spawninfo.boss", Bosses.tierName(data.rarity, data.titan), Bosses.speciesName(entry.pokemon), entry.entity.blockPosition().toShortString(), distance)
            }, false)
        }
        return bosses.size
    }

    // ---- Rewards and Mega Stones ------------------------------------------------

    private fun rewards(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("rewards").requires { allowed(it, REWARDS) }
            .executes { openEditor(it.source) }
            .then(
                Commands.literal("test").then(
                    Commands.argument("rarity", StringArgumentType.word())
                        .suggests { _, builder -> SharedSuggestionProvider.suggest(BossRarity.entries.map { it.id }, builder) }
                        .executes { testRewards(it.source, StringArgumentType.getString(it, "rarity"), false) }
                        .then(Commands.literal("titan").executes { testRewards(it.source, StringArgumentType.getString(it, "rarity"), true) })
                )
            )

    private fun megas(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("megas").requires { allowed(it, REWARDS) }
            .executes { listMegas(it.source, it.source.playerOrException) }
            .then(Commands.argument("player", EntityArgument.player()).executes { listMegas(it.source, EntityArgument.getPlayer(it, "player")) })
            .then(
                Commands.literal("reset")
                    .executes { resetMegas(it.source, it.source.playerOrException) }
                    .then(Commands.argument("player", EntityArgument.player()).executes { resetMegas(it.source, EntityArgument.getPlayer(it, "player")) })
            )

    /** /boss rewards: the editor, with the current tables. */
    private fun openEditor(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val stones = BossRewards.settings.megaStones
        S2CBossRewardsEditorPacket(BossRewards.forEditor(), stones.firstVictoryChance, stones.repeatChance).sendToPlayer(player)
        return 1
    }

    /** The editor's Save: permission checked again, then rewards.json written and used. */
    @JvmStatic
    fun saveRewards(player: ServerPlayer, packet: C2SBossRewardsSavePacket) {
        if (!allowed(player.createCommandSourceStack(), REWARDS)) {
            player.sendSystemMessage(Component.translatable("$KEY.rewards.denied").withStyle(ChatFormatting.RED))
            return
        }
        val ok = BossRewards.saveFromEditor(packet.tables, packet.firstChance, packet.repeatChance, player.server.registryAccess())
        player.sendSystemMessage(
            if (ok) Component.translatable("$KEY.rewards.saved", packet.tables.sumOf { it.size })
            else Component.translatable("$KEY.rewards.failed").withStyle(ChatFormatting.RED),
        )
        if (ok) GenerationsCore.LOGGER.info("Bosses: rewards saved by {} from the editor", player.gameProfile.name)
    }

    /** /boss rewards test <rarity> [titan]: a draw for the player, given, with the victory screen. */
    private fun testRewards(source: CommandSourceStack, rarityName: String, titan: Boolean): Int {
        val player = source.playerOrException
        val rarity = BossRarity.byId(rarityName) ?: return fail(source, "unknown_rarity", rarityName)
        val items = BossVictory.reward(player, rarity, titan, null)
        source.sendSuccess({ Component.translatable("$KEY.rewards.tested", Bosses.tierName(rarity, titan), items.size) }, false)
        return items.size
    }

    private fun listMegas(source: CommandSourceStack, player: ServerPlayer): Int {
        val keys = BossMegaSavedData.get(source.server).of(player.uuid)
        if (keys.isEmpty()) {
            source.sendSuccess({ Component.translatable("$KEY.megas.none", player.displayName) }, false)
            return 0
        }
        val names = Component.empty()
        keys.forEachIndexed { i, key ->
            if (i > 0) names.append(", ")
            names.append(BossMegas.byKey(key)?.displayName() ?: Component.literal(key))
        }
        source.sendSuccess({ Component.translatable("$KEY.megas.list", player.displayName, keys.size, names) }, false)
        return keys.size
    }

    private fun resetMegas(source: CommandSourceStack, player: ServerPlayer): Int {
        val removed = BossMegaSavedData.get(source.server).reset(player.uuid)
        source.sendSuccess({ Component.translatable("$KEY.megas.reset", player.displayName, removed) }, true)
        return removed
    }

    private fun fail(source: CommandSourceStack, key: String, vararg args: Any): Int {
        source.sendFailure(Component.translatable("$KEY.$key", *args))
        return 0
    }
}
