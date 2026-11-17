package generations.gg.generations.core.generationscore.common.world.eon

import com.bedrockk.molang.runtime.MoParams
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.molang.function.GeneralMoLangFunctions
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.util.getStringOrNull
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.api.getObjectOrNull
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicPlaces
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicSession
import generations.gg.generations.core.generationscore.common.world.cinematic.LegendaryCinematics
import generations.gg.generations.core.generationscore.common.world.entity.block.PokemonUtil
import generations.gg.generations.core.generationscore.common.world.item.EnigmaStoneItem
import generations.gg.generations.core.generationscore.common.world.johto.JohtoTrials
import generations.gg.generations.core.generationscore.common.world.mew.MewGame
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * The Enigma Stone's summoning with its scene (EON-PLAN.md § 7). The original dialogue
 * (eon_spawn.json) chooses Latias or Latios and, at its last page, took one stone from the main
 * hand and made the Pokémon appear at the player (level 65). That page now calls
 * q.generations_eon_summon(q.player, '<species> level=65'), which:
 *
 * 1. checks again that the main hand holds a charged Enigma Stone (a modified client that closed
 *    the dialogue cannot have a Pokémon without it) and that nothing is waiting for this player;
 * 2. takes one stone, as the original did; writes the summoning as waiting (saved);
 * 3. plays the scene (latias or latios); at its spawn moment the record is removed and written
 *    BEFORE the Pokémon appears, with the very properties the dialogue gave (species, level 65);
 * 4. when no scene can play (cinematics off, a battle, another scene): the original's appearance
 *    at once (the player's position + 1, their yaw).
 *
 * Cut short while the player is there: the Pokémon appears at once at the landing point. The
 * player gone (disconnected, dead, elsewhere): it waits for them and appears in front of them
 * 3 s after they are back (or after a crash). One Pokémon a summoning, whatever happens.
 */
object EonSummon {
    private const val KEY = "generations_core.eon.summon"

    /** The players whose scene is playing: their session (the tick leaves them to it). */
    private val playing = HashMap<UUID, UUID>()

    /** When a waiting summoning's player was first seen free (3 s later, it appears). */
    private val seen = HashMap<UUID, Long>()

    private var ticks = 0L

    fun init() {
        GeneralMoLangFunctions.functions["generations_eon_summon"] = eonSummon@{ it: MoParams ->
            val player = it.getObjectOrNull(0, ServerPlayer::class.java) ?: return@eonSummon Unit
            val properties = it.getStringOrNull(1) ?: return@eonSummon Unit
            try {
                summon(player, properties)
            } catch (e: Exception) {
                GenerationsCore.LOGGER.error("Eon summoning of {} failed", player.scoreboardName, e)
            }
            return@eonSummon Unit
        }
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) {
            playing.clear()
            seen.clear()
        }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { tick(it.server) }
    }

    private fun sceneOf(properties: String): String? {
        val species = runCatching { PokemonProperties.parse(properties).species }.getOrNull()?.lowercase() ?: return null
        return when {
            species.endsWith("latios") -> CinematicScenes.LATIOS
            species.endsWith("latias") -> CinematicScenes.LATIAS
            else -> null
        }
    }

    /** The dialogue's last page: the stone checked and taken, then the scene and the Pokémon. */
    fun summon(player: ServerPlayer, properties: String): Boolean {
        val stack = player.mainHandItem
        if (stack.item !is EnigmaStoneItem || stack.damageValue < stack.maxDamage) {
            player.displayClientMessage(Component.translatable("$KEY.no_stone").withStyle(ChatFormatting.GRAY), false)
            return false
        }
        return begin(player, properties, test = false) { stack.shrink(1) } == null
    }

    /** The scene then the Pokémon; `take` runs once everything was checked. The refusal, or null. */
    private fun begin(player: ServerPlayer, properties: String, test: Boolean, take: () -> Unit): Component? {
        val server = player.server
        val data = EonData.of(server)
        if (!test && (data.summons.containsKey(player.uuid) || playing.containsKey(player.uuid))) return refuse(player, Component.translatable("$KEY.waiting"))
        val parsed = runCatching { PokemonProperties.parse(properties) }.getOrNull() ?: return refuse(player, Component.translatable("$KEY.unknown", properties))
        val level = player.serverLevel()
        val scene = sceneOf(properties)
        if (scene == null || !LegendaryCinematics.config.eonCinematic) {
            if (test) return Component.translatable("$KEY.no_scene")
            take()
            original(level, player, parsed)
            return null
        }
        val species = ResourceLocation.tryParse(LegendaryCinematics.config.species(scene)) ?: ResourceLocation.fromNamespaceAndPath("cobblemon", scene)
        val origin = Vec3(player.x, CinematicPlaces.ground(level, player.x, player.y, player.z), player.z)
        val facing = player.yRot
        val landing = landing(level, origin, facing, species)
        val id = player.uuid
        val request = LegendaryCinematics.Request(
            level, scene, origin, facing, landing, player, species, emptySet(), test,
            onSpawn = if (test) null else { session -> spawnMoment(server, id, session) },
            exclusive = false, solo = true, ambientRadius = LegendaryCinematics.config.eonSpectatorRadius,
            ambientFilter = { !JohtoTrials.isRunning(it.uuid) && !MewGame.isPlaying(it.uuid) && !EonManifest.isRunning(it.uuid) },
        )
        LegendaryCinematics.check(request)?.let {
            if (test) return it
            GenerationsCore.LOGGER.info("Eon summoning by {}: no scene ({}), the original appearance", player.gameProfile.name, it.string)
            take()
            original(level, player, parsed)
            return null
        }
        if (test) {
            return when (val result = LegendaryCinematics.start(request)) {
                is LegendaryCinematics.StartResult.Refused -> result.message
                is LegendaryCinematics.StartResult.Started -> null
            }
        }
        take()
        data.summons[id] = EonPendingSummon(
            id, player.gameProfile.name, properties, scene, level.dimension().location().toString(),
            player.x, player.y + 1.0, player.z, player.yRot, landing.x, landing.y, landing.z, System.currentTimeMillis(),
        )
        EonData.saveNow(server)
        when (val result = LegendaryCinematics.start(request)) {
            is LegendaryCinematics.StartResult.Started -> {
                playing[id] = result.session.id
                GenerationsCore.LOGGER.info("{} summoned by {}: its scene plays ({})", scene, player.gameProfile.name, result.session.id)
            }
            // Cannot happen after check(): the original appearance.
            is LegendaryCinematics.StartResult.Refused -> {
                data.summons.remove(id)
                EonData.saveNow(server)
                original(level, player, parsed)
            }
        }
        return null
    }

    private fun refuse(player: ServerPlayer, message: Component): Component {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.GRAY), false)
        return message
    }

    /** The original dialogue's appearance: at the player, one block up, their yaw. */
    private fun original(level: ServerLevel, player: ServerPlayer, properties: PokemonProperties) {
        PokemonUtil.spawn(properties, level, player.position().add(0.0, 1.0, 0.0), player.yRot)
        GenerationsCore.LOGGER.info("Eon summoning by {}: {} appeared at once", player.gameProfile.name, properties.species)
    }

    /** A safe point 3 to 6 blocks in front of the player for that species' size (the player's own point when none). */
    private fun landing(level: ServerLevel, origin: Vec3, facing: Float, species: ResourceLocation): Vec3 {
        val form = PokemonSpecies.getByIdentifier(species)?.standardForm
        val width = form?.let { it.hitbox.width() * it.baseScale }?.toDouble() ?: 1.6
        val height = form?.let { it.hitbox.height() * it.baseScale }?.toDouble() ?: 2.0
        return CinematicPlaces.landing(level, origin, facing, width, height)
    }

    /** The scene's spawn moment (2 ticks before its end, or at once when cut short). */
    private fun spawnMoment(server: MinecraftServer, id: UUID, @Suppress("UNUSED_PARAMETER") session: CinematicSession) {
        playing.remove(id)
        val entry = EonData.of(server).summons[id] ?: return
        val player = server.playerList.getPlayer(id)
        // The player gone, dead or elsewhere: it waits for them (the tick).
        if (player == null || !player.isAlive || player.level().dimension().location().toString() != entry.dimension) return
        appear(server, entry, player, Vec3(entry.landingX, entry.landingY, entry.landingZ))
    }

    /** The Pokémon appears for that record, once: the record removed and written first (a crash then leaves none rather than two). */
    private fun appear(server: MinecraftServer, entry: EonPendingSummon, player: ServerPlayer, at: Vec3) {
        val data = EonData.of(server)
        if (data.summons.remove(entry.player) == null) return
        EonData.saveNow(server)
        try {
            val properties = PokemonProperties.parse(entry.properties)
            val yaw = CinematicPlaces.yawTowards(at, player.position())
            PokemonUtil.spawn(properties, player.serverLevel(), at, yaw)
            GenerationsCore.LOGGER.info("{} summoned by {} appeared at {}", entry.scene, entry.playerName, at)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Eon summoning of {}: the Pokémon could not appear", entry.playerName, e)
        }
    }

    /** Every second: the summonings left waiting (a disconnection, a death, a crash), for their player back and free. */
    private fun tick(server: MinecraftServer) {
        if (++ticks % 20L != 0L) return
        val data = EonData.of(server)
        if (data.summons.isEmpty()) return
        // A scene gone without its spawn moment (should not happen): its record is recovered.
        playing.entries.removeIf { LegendaryCinematics.session(it.value) == null }
        for (entry in data.summons.values.toList()) {
            if (entry.player in playing) continue
            val player = server.playerList.getPlayer(entry.player)
            if (player == null || !player.isAlive || player.isSpectator || LegendaryCinematics.isWatching(player.uuid)) {
                seen.remove(entry.player)
                continue
            }
            val first = seen.getOrPut(entry.player) { ticks }
            if (ticks - first < 60L) continue
            seen.remove(entry.player)
            val level = player.serverLevel()
            val species = ResourceLocation.fromNamespaceAndPath("cobblemon", entry.scene)
            val origin = Vec3(player.x, CinematicPlaces.ground(level, player.x, player.y, player.z), player.z)
            appear(server, entry, player, landing(level, origin, player.yRot, species))
            player.displayClientMessage(Component.translatable("$KEY.delivered").withStyle(ChatFormatting.LIGHT_PURPLE), false)
        }
    }

    // ---------------------------------------------------------------- commands

    /**
     * /generations cinematic latias|latios [real]: the scene where the player stands. Test:
     * nothing taken, nothing appears. Real: the whole summoning without a stone, one real
     * Pokémon level 65 at its end. The refusal, or null.
     */
    fun admin(player: ServerPlayer, scene: String, real: Boolean): Component? =
        begin(player, "$scene level=65", test = !real) { }

    /** The summonings waiting for their Pokémon, as lines. */
    fun pending(server: MinecraftServer): List<String> =
        EonData.of(server).summons.values.map { "${it.playerName}: ${it.scene} ${it.dimension}${if (it.player in playing) " (scene playing)" else ""}" }
}
