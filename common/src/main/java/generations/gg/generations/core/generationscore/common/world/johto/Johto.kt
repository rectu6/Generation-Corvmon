package generations.gg.generations.core.generationscore.common.world.johto

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.api.events.TriState
import generations.gg.generations.core.generationscore.common.api.events.general.InteractionEvents
import generations.gg.generations.core.generationscore.common.config.LegendKeys
import generations.gg.generations.core.generationscore.common.network.GenerationsNetwork.sendPacket
import generations.gg.generations.core.generationscore.common.network.packets.johto.S2CJohtoAltarPacket
import generations.gg.generations.core.generationscore.common.network.packets.johto.S2CJohtoPromptPacket
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicPlaces
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicSession
import generations.gg.generations.core.generationscore.common.world.cinematic.LegendaryCinematics
import generations.gg.generations.core.generationscore.common.world.entity.block.PokemonUtil
import generations.gg.generations.core.generationscore.common.world.entity.statue.StatueEntity
import generations.gg.generations.core.generationscore.common.world.hooh.HoOhPassage
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems
import generations.gg.generations.core.generationscore.common.world.item.johto.JohtoMemoryItem
import generations.gg.generations.core.generationscore.common.world.mew.MewGame
import generations.gg.generations.core.generationscore.common.world.mew.MewReward
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpSessions
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.util.Random
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Legend of Johto on the server (JOHTO-PLAN.md): the statues of the Burned Tower (their
 * history, their awakening with a Sacred Ash), the trials' sessions and delays, the memories,
 * the encounters (a normal wild beast), Ho-Oh's summoning at the Bell Tower's altar. Nothing is
 * generated, nothing kept loaded, nothing done each tick but for the players concerned (a
 * check every half second of the online players' records, a trial every 2 ticks).
 */
object Johto {
    var config: JohtoConfig = JohtoConfig().also { it.validate() }
        private set
    var places: JohtoPlaces = JohtoPlaces().also { it.validate() }
        private set

    private class Prompt(val token: Int, val statue: UUID, val beast: JohtoBeast, val slot: Int, val expires: Long)
    private class AltarToken(val token: Int, val expires: Long)
    private class Countdown(val beast: JohtoBeast, val until: Long, val zone: JohtoPlaces.Zone)

    private val prompts = HashMap<UUID, Prompt>()
    private val altarTokens = HashMap<UUID, AltarToken>()
    private val countdowns = HashMap<UUID, Countdown>()
    /** The admins' zones around themselves, in memory (tests). */
    private val testZones = HashMap<UUID, JohtoPlaces.Zone>()
    /** A Johto scene of this player is playing (its end decides what follows). */
    private val playing = HashSet<UUID>()
    /** Not before this tick (the encounter after a won trial, a second try after a refusal). */
    private val notBefore = HashMap<UUID, Long>()
    private val random = Random()
    private var ticks = 0L

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { event ->
            ticks = 0L
            prompts.clear(); altarTokens.clear(); countdowns.clear(); playing.clear(); notBefore.clear()
            JohtoTrials.clear()
            reload()
            val data = JohtoData.of(event.server)
            var changed = false
            for (record in data.records.values) if (JohtoStateMachine.recover(record)) changed = true
            if (changed) data.setDirty()
        }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) {
            // A trial playing is open again at the next start (recover): nothing lost, no delay.
            JohtoTrials.clear()
            countdowns.clear()
        }
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe(Priority.NORMAL) { event -> login(event.player) }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { event ->
            val uuid = event.player.uuid
            prompts.remove(uuid)
            altarTokens.remove(uuid)
            countdowns.remove(uuid)
            JohtoTrials.run(uuid)?.let { JohtoTrials.fail(it, null, "disconnected") }
        }
        PlatformEvents.PLAYER_DEATH.subscribe(Priority.LOWEST) { event ->
            if (!event.isCanceled) JohtoTrials.run(event.player.uuid)?.let { JohtoTrials.fail(it, event.player, "died") }
        }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { tick(it.server) }
        InteractionEvents.RIGHT_CLICK_BLOCK.subscribe { event ->
            val player = event.player as? ServerPlayer ?: return@subscribe
            if (event.hand != InteractionHand.MAIN_HAND) return@subscribe
            if (altarClicked(player, event.pos)) event.result = TriState.TRUE
        }
    }

    fun reload() {
        config = JohtoConfig.load()
        places = JohtoPlaces.load()
    }

    fun savePlaces() = places.save()

    fun key(name: String, vararg args: Any): MutableComponent = Component.translatable("generations_core.johto.$name", *args)

    private fun say(player: ServerPlayer, text: MutableComponent, colour: ChatFormatting = ChatFormatting.GOLD, bar: Boolean = false) =
        player.displayClientMessage(text.withStyle(colour), bar)

    fun beastName(beast: JohtoBeast): Component = PokemonSpecies.getByName(beast.id)?.translatedName ?: Component.literal(beast.id)

    /** Free for the legend: alive, playing, not in a battle, a scene, a journey, Mew's game, Ho-Oh's passage or a trial. */
    fun free(player: ServerPlayer): Boolean =
        player.isAlive && !player.isSpectator &&
            BattleRegistry.getBattleByParticipatingPlayer(player) == null && !LegendaryCinematics.isWatching(player.uuid) &&
            UltraWarpSessions.get(player.uuid) == null && !MewGame.isPlaying(player.uuid) && !MewReward.isPending(player.uuid) &&
            HoOhPassage.session(player.uuid) == null && !JohtoTrials.isRunning(player.uuid) && !countdowns.containsKey(player.uuid)

    fun inDimension(level: ServerLevel): Boolean = level.dimension().location().toString() == places.dimension

    /** The Minecraft yaw looking from a to b. */
    fun yawTo(a: Vec3, b: Vec3): Float = Math.toDegrees(atan2(-(b.x - a.x), b.z - a.z)).toFloat()

    fun forward(yaw: Float, distance: Double): Vec3 {
        val r = Math.toRadians(yaw.toDouble())
        return Vec3(-sin(r) * distance, 0.0, cos(r) * distance)
    }

    private fun wrap(deg: Float): Float {
        var d = deg % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    fun memoryItem(beast: JohtoBeast): Item = when (beast) {
        JohtoBeast.RAIKOU -> GenerationsItems.RAIKOU_MEMORY.value()
        JohtoBeast.ENTEI -> GenerationsItems.ENTEI_MEMORY.value()
        JohtoBeast.SUICUNE -> GenerationsItems.SUICUNE_MEMORY.value()
    }

    /** The zone of this beast's trial for this player (an admin's test zone first). */
    fun zoneFor(player: ServerPlayer, beast: JohtoBeast): JohtoPlaces.Zone? =
        testZones[player.uuid] ?: places.zone(beast)?.takeIf { inDimension(player.serverLevel()) }

    fun setTestZone(player: ServerPlayer, radius: Double): JohtoPlaces.Zone {
        val zone = JohtoPlaces.Zone("circle", player.x, player.y, player.z, radius.coerceIn(8.0, 64.0), 16.0)
        testZones[player.uuid] = zone
        return zone
    }

    fun clearTestZone(player: ServerPlayer): Boolean = testZones.remove(player.uuid) != null

    private fun canSkip(record: JohtoRecord, scene: String): Boolean = config.allowSkipSeen && scene in record.seen

    private fun markSeen(server: MinecraftServer, record: JohtoRecord, scene: String) {
        if (record.seen.add(scene)) JohtoData.changed(server)
    }

    // ---------------------------------------------------------------- the statues

    /**
     * A click on a statue (StatueEntity.interact, server side): its history, its awakening with a
     * Sacred Ash, or what its beast waits for. Null: not ours (the statue's usual behaviour).
     */
    @JvmStatic
    fun onStatue(player: ServerPlayer, statue: StatueEntity, hand: InteractionHand): InteractionResult? {
        val stack = player.getItemInHand(hand)
        val place = places.statue(statue.uuid)
        if (place == null) {
            // D6: an unregistered statue of a beast is inert with an ash (it would bypass the legend).
            if (config.enabled && stack.`is`(GenerationsItems.SACRED_ASH) && JohtoBeast.of(statue.properties.species) != null) {
                say(player, key("statue.inert"), ChatFormatting.GRAY, true)
                return InteractionResult.FAIL
            }
            return null
        }
        val beast = JohtoBeast.of(place.beast) ?: return null
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS
        if (stack.`is`(GenerationsItems.CHISEL)) {
            say(player, key("statue.locked"), ChatFormatting.RED, true)
            return InteractionResult.FAIL
        }
        if (!config.enabled) {
            say(player, key("disabled"), ChatFormatting.GRAY, true)
            return InteractionResult.FAIL
        }
        val server = player.server
        val record = JohtoData.getOrCreate(server, player.uuid)
        if (!record.historySeen || (player.isShiftKeyDown && stack.isEmpty)) {
            if (!free(player)) {
                say(player, key("busy"), ChatFormatting.GRAY, true)
                return InteractionResult.FAIL
            }
            startHistory(player, statue.position(), statue.yRot, test = false)
            return InteractionResult.SUCCESS
        }
        if (stack.`is`(GenerationsItems.SACRED_ASH)) {
            askAwakening(player, statue, beast, record)
            return InteractionResult.SUCCESS
        }
        statusLine(player, beast, record)
        return InteractionResult.SUCCESS
    }

    /** What the statue's beast waits for, in the action bar. */
    private fun statusLine(player: ServerPlayer, beast: JohtoBeast, record: JohtoRecord) {
        val b = record.track(beast)
        val text = when (b.state) {
            BeastState.IDLE -> key("statue.idle", beastName(beast))
            BeastState.COOLDOWN -> key("statue.cooldown", beastName(beast), ((b.cooldownUntil - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1))
            BeastState.ENCOUNTER -> key("statue.encounter", beastName(beast))
            else -> key("statue.open", beastName(beast))
        }
        say(player, text, ChatFormatting.GOLD, true)
    }

    private fun refusal(player: ServerPlayer, beast: JohtoBeast, record: JohtoRecord): MutableComponent? {
        val b = record.track(beast)
        if (b.state != BeastState.IDLE) return when (b.state) {
            BeastState.ENCOUNTER -> key("refused.encounter", beastName(beast))
            BeastState.COOLDOWN -> key("refused.cooldown", beastName(beast))
            else -> key("refused.open", beastName(beast))
        }
        if (!free(player)) return key("busy")
        if (zoneFor(player, beast) == null) return key("refused.no_zone", beastName(beast))
        if (config.respectCaughtLimits) {
            val species = when (beast) {
                JohtoBeast.RAIKOU -> LegendKeys.RAIKOU
                JohtoBeast.ENTEI -> LegendKeys.ENTEI
                JohtoBeast.SUICUNE -> LegendKeys.SUICUNE
            }
            if (GenerationsCore.CONFIG.caught.capped(player, species)) return key("refused.capped", beastName(beast))
        }
        return null
    }

    private fun askAwakening(player: ServerPlayer, statue: StatueEntity, beast: JohtoBeast, record: JohtoRecord) {
        refusal(player, beast, record)?.let {
            say(player, it, ChatFormatting.GRAY, true)
            return
        }
        var token = random.nextInt()
        if (token == 0) token = 1
        prompts[player.uuid] = Prompt(token, statue.uuid, beast, player.inventory.selected, player.serverLevel().gameTime + 600L)
        player.sendPacket(S2CJohtoPromptPacket(token, beast.ordinal))
    }

    /**
     * The awakening confirmed: checked again, the ash taken from the same slot, the session
     * saved, then the scene — in the same tick. A token is used once (a second click, a repeated
     * packet: nothing).
     */
    fun confirm(player: ServerPlayer, token: Int, accept: Boolean) {
        val prompt = prompts[player.uuid] ?: return
        if (prompt.token != token) return
        prompts.remove(player.uuid)
        if (!accept) return
        val level = player.serverLevel()
        if (level.gameTime > prompt.expires) {
            say(player, key("refused.expired"), ChatFormatting.GRAY, true)
            return
        }
        val server = player.server
        val record = JohtoData.getOrCreate(server, player.uuid)
        refusal(player, prompt.beast, record)?.let {
            say(player, it, ChatFormatting.GRAY, true)
            return
        }
        val statue = level.getEntity(prompt.statue) as? StatueEntity
        if (statue == null || statue.distanceTo(player) > 8f || places.statue(statue.uuid) == null) {
            say(player, key("refused.statue"), ChatFormatting.GRAY, true)
            return
        }
        val stack = player.inventory.getItem(prompt.slot)
        if (!stack.`is`(GenerationsItems.SACRED_ASH)) {
            say(player, key("refused.ash"), ChatFormatting.GRAY, true)
            return
        }
        if (!JohtoStateMachine.awaken(record, prompt.beast)) return
        stack.shrink(1)
        JohtoData.saveNow(server)
        JohtoTrials.log("{} consumed a Sacred Ash on the statue of {} (session {})", player.scoreboardName, prompt.beast.id, record.track(prompt.beast).session)
        startAwakening(player, statue, prompt.beast, test = false)
    }

    // ---------------------------------------------------------------- the scenes

    /** The history of the Burned Tower (§ 4): its anchor, or the statue's place. */
    fun startHistory(player: ServerPlayer, near: Vec3, nearYaw: Float, test: Boolean): Boolean {
        val level = player.serverLevel()
        val anchor = places.history?.takeIf { inDimension(level) }
        val origin = anchor?.let { Vec3(it.x, it.y, it.z) } ?: near
        val facing = anchor?.yaw ?: yawTo(origin, player.position())
        val record = JohtoData.getOrCreate(player.server, player.uuid)
        val scene = CinematicScenes.JOHTO_HISTORY
        val options = "height=${places.towerHeight};radius=${places.towerRadius}"
        val request = LegendaryCinematics.Request(
            level, scene, origin, facing, origin, player, ResourceLocation.parse("cobblemon:hooh"), emptySet(), test,
            onSpawn = null, exclusive = false, solo = true, ambientRadius = 0.0, skippable = canSkip(record, scene), options = options,
        )
        return when (val result = LegendaryCinematics.start(request)) {
            is LegendaryCinematics.StartResult.Refused -> {
                say(player, result.message.copy(), ChatFormatting.GRAY, true)
                false
            }
            is LegendaryCinematics.StartResult.Started -> {
                if (!test) {
                    record.historySeen = true
                    markSeen(player.server, record, scene)
                    JohtoData.changed(player.server)
                }
                true
            }
        }
    }

    /** A statue's awakening (§ 5): seen by its player alone, the statue hidden for them meanwhile. */
    fun startAwakening(player: ServerPlayer, statue: StatueEntity, beast: JohtoBeast, test: Boolean): Boolean {
        val level = player.serverLevel()
        val origin = statue.position()
        val facing = yawTo(origin, player.position())
        val flee = origin.add(forward(facing + 180f, 26.0))
        val record = JohtoData.getOrCreate(player.server, player.uuid)
        val scene = beast.awakeningScene
        val statueYaw = wrap(statue.orientation + 180f - facing)
        val options = "material=${statue.material?.takeIf { it.isNotBlank() } ?: "concrete"};syaw=$statueYaw;scale=${statue.scale}"
        val uuid = player.uuid
        val request = LegendaryCinematics.Request(
            level, scene, origin, facing, flee, player, ResourceLocation.parse(beast.species), statue.properties.aspects, test,
            onSpawn = { awakened(uuid, beast) }, exclusive = false, solo = true, ambientRadius = 0.0,
            hidden = listOf(statue.uuid), skippable = canSkip(record, scene), options = options,
        )
        return when (val result = LegendaryCinematics.start(request)) {
            is LegendaryCinematics.StartResult.Refused -> {
                // The ash is spent: the session opens all the same, without its scene.
                if (!test) awakened(uuid, beast)
                false
            }
            is LegendaryCinematics.StartResult.Started -> {
                if (!test) {
                    markSeen(player.server, record, scene)
                    playing += uuid
                }
                true
            }
        }
    }

    private fun awakened(uuid: UUID, beast: JohtoBeast) {
        playing -= uuid
        val server = com.cobblemon.mod.common.util.server() ?: return
        val record = JohtoData.get(server, uuid) ?: return
        if (!JohtoStateMachine.opened(record, beast)) return
        JohtoData.saveNow(server)
        val player = server.playerList.getPlayer(uuid) ?: return
        say(player, key("awakened", beastName(beast)), ChatFormatting.GOLD)
        hint(player, beast)
    }

    /** Where the trial's zone is, from here. */
    private fun hint(player: ServerPlayer, beast: JohtoBeast) {
        val zone = zoneFor(player, beast) ?: return
        val dx = zone.centreX - player.x
        val dz = zone.centreZ - player.z
        val distance = kotlin.math.sqrt(dx * dx + dz * dz).toInt()
        val dirs = arrayOf("south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east")
        val yaw = Math.toDegrees(atan2(-dx, dz))
        val i = Math.floorMod(Math.round(yaw / 45.0).toInt(), 8)
        say(player, key("zone_hint", beastName(beast), distance, key("dir.${dirs[i]}")), ChatFormatting.YELLOW, true)
    }

    /** The nearest safe point declared for this beast (or "hooh") within 128 blocks, else in front of the player. */
    private fun spawnPoint(player: ServerPlayer, id: String, species: String): Vec3 {
        val level = player.serverLevel()
        if (inDimension(level)) {
            val best = places.spawns(id).map { Vec3(it.x, it.y, it.z) }
                .filter { it.distanceTo(player.position()) <= 128.0 && level.isLoaded(BlockPos.containing(it)) }
                .minByOrNull { it.distanceTo(player.position()) }
            if (best != null) return best
        }
        val form = PokemonSpecies.getByIdentifier(ResourceLocation.parse(species))?.standardForm
        val width = (form?.let { it.hitbox.width() * it.baseScale } ?: 2f).toDouble()
        val height = (form?.let { it.hitbox.height() * it.baseScale } ?: 2f).toDouble()
        val ahead = player.position().add(forward(player.yRot, 3.0))
        return CinematicPlaces.landing(level, ahead, player.yRot, width, height)
    }

    /**
     * A beast's encounter (§ 9): its own camera for its player, the ambience for the players near
     * it; then a normal wild Pokémon (never twice for a session). real = false: a test, nothing
     * appears (or a test Pokémon with spawnTest).
     */
    fun startEncounter(player: ServerPlayer, beast: JohtoBeast, test: Boolean, spawnTest: Boolean = false): Boolean {
        val level = player.serverLevel()
        val at = spawnPoint(player, beast.id, beast.species)
        val facing = yawTo(at, player.position())
        val record = JohtoData.getOrCreate(player.server, player.uuid)
        val scene = beast.encounterScene
        val uuid = player.uuid
        val request = LegendaryCinematics.Request(
            level, scene, at, facing, at, player, ResourceLocation.parse(beast.species), emptySet(), test && !spawnTest,
            onSpawn = { session -> encountered(uuid, beast, session, test) },
            exclusive = false, solo = true, ambientRadius = config.spectatorRadius,
            ambientFilter = { !JohtoTrials.isRunning(it.uuid) && !MewGame.isPlaying(it.uuid) },
            skippable = canSkip(record, scene),
        )
        return when (val result = LegendaryCinematics.start(request)) {
            is LegendaryCinematics.StartResult.Refused -> {
                if (test) say(player, result.message.copy(), ChatFormatting.GRAY, true)
                false
            }
            is LegendaryCinematics.StartResult.Started -> {
                if (!test) {
                    markSeen(player.server, record, scene)
                    playing += uuid
                }
                true
            }
        }
    }

    private fun encountered(uuid: UUID, beast: JohtoBeast, session: CinematicSession, test: Boolean) {
        playing -= uuid
        val server = com.cobblemon.mod.common.util.server() ?: return
        val player = server.playerList.getPlayer(uuid)
        // Cut short by its player leaving, or the server stopping: played again later (D14).
        if (player == null || !player.isAlive || player.level().dimension() != session.dimension || !server.isRunning) return
        val level = player.serverLevel()
        val text = buildString {
            append(beast.species).append(" level=").append(config.beastLevel)
            if (config.beastProperties.isNotBlank()) append(' ').append(config.beastProperties.trim())
        }
        try {
            val entity = PokemonProperties.parse(text).createEntity(level)
            if (!test) {
                val record = JohtoData.get(server, uuid) ?: return
                // Saved before it appears: a crash now never gives a second one.
                if (!JohtoStateMachine.spawned(record, beast, entity.pokemon.uuid.toString())) return
                JohtoData.saveNow(server)
            }
            PokemonUtil.spawn(entity, level, session.spawn, session.facing + 180f)
            say(player, key(if (test) "encounter.test_spawn" else "encounter.spawn", beastName(beast)), ChatFormatting.GOLD)
            JohtoTrials.log("{} {} of {} at {}", if (test) "a test" else "the wild", beast.id, player.scoreboardName, session.spawn)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Legend of Johto: the {} of {} could not appear", beast.id, player.scoreboardName, e)
        }
    }

    // ---------------------------------------------------------------- the trials' outcomes

    fun trialRound(run: JohtoTrials.Run, player: ServerPlayer) {
        if (run.test) return
        val record = JohtoData.get(player.server, run.player) ?: return
        if (JohtoStateMachine.roundWon(record, run.beast)) JohtoData.saveNow(player.server)
    }

    fun trialWon(run: JohtoTrials.Run, player: ServerPlayer) {
        say(player, key("trial.won", beastName(run.beast)), ChatFormatting.GOLD)
        if (run.test) return
        val server = player.server
        val record = JohtoData.get(server, run.player) ?: return
        if (!JohtoStateMachine.win(record, run.beast)) return
        JohtoData.saveNow(server)
        deliver(player, run.beast, record)
        notBefore[run.player] = player.serverLevel().gameTime + 40L
    }

    fun trialFailed(run: JohtoTrials.Run, player: ServerPlayer?, reason: String) {
        player?.let { say(it, key("trial.failed.$reason", beastName(run.beast)), ChatFormatting.RED) }
        if (run.test) return
        val server = com.cobblemon.mod.common.util.server() ?: return
        val record = JohtoData.get(server, run.player) ?: return
        if (JohtoStateMachine.fail(record, run.beast, System.currentTimeMillis(), config.cooldownMs)) {
            JohtoData.saveNow(server)
            player?.let { say(it, key("trial.cooldown", beastName(run.beast), Math.round(config.cooldownMinutes).toInt()), ChatFormatting.GRAY) }
        }
    }

    /** The memory owed for a won session, into the inventory (kept owed while it is full). */
    private fun deliver(player: ServerPlayer, beast: JohtoBeast, record: JohtoRecord): Boolean {
        if (!record.track(beast).memoryOwed) return false
        val stack = JohtoMemoryItem.stamp(ItemStack(memoryItem(beast)), player.uuid, player.scoreboardName)
        if (!player.inventory.add(stack)) {
            say(player, key("memory.full", beastName(beast)), ChatFormatting.YELLOW)
            return false
        }
        if (JohtoStateMachine.memoryGiven(record, beast)) JohtoData.saveNow(player.server)
        say(player, key("memory.given", stack.hoverName), ChatFormatting.AQUA)
        return true
    }

    /** A memory given by a command (tests, a lost one): stamped with that player. */
    fun giveMemory(player: ServerPlayer, beast: JohtoBeast) {
        val stack = JohtoMemoryItem.stamp(ItemStack(memoryItem(beast)), player.uuid, player.scoreboardName)
        if (!player.inventory.add(stack)) player.drop(stack, false)
    }

    private fun login(player: ServerPlayer) {
        val record = JohtoData.get(player.server, player.uuid) ?: return
        for (beast in JohtoBeast.entries) {
            val b = record.track(beast)
            if (b.memoryOwed) deliver(player, beast, record)
            when (b.state) {
                BeastState.OPEN -> say(player, key("login.open", beastName(beast)), ChatFormatting.GOLD)
                BeastState.ENCOUNTER -> say(player, key("login.encounter", beastName(beast)), ChatFormatting.GOLD)
                else -> {}
            }
        }
        if (record.summon == SummonState.SUMMONING) say(player, key("login.summon"), ChatFormatting.GOLD)
        notBefore[player.uuid] = player.serverLevel().gameTime + 100L
    }

    // ---------------------------------------------------------------- Ho-Oh's altar

    private fun altarAt(level: ServerLevel, pos: BlockPos): Boolean {
        val altar = places.altar ?: return false
        return inDimension(level) && pos.x == altar.x && pos.y == altar.y && pos.z == altar.z
    }

    /** The memories this player may offer: their slots by beast (-1: none). */
    private fun memorySlots(player: ServerPlayer): IntArray {
        val slots = IntArray(3) { -1 }
        val inventory = player.inventory
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (stack.isEmpty) continue
            for (beast in JohtoBeast.entries) {
                if (slots[beast.ordinal] >= 0 || !stack.`is`(memoryItem(beast))) continue
                if (config.memoriesBound && JohtoMemoryItem.owner(stack).let { it != null && it != player.uuid }) continue
                slots[beast.ordinal] = i
            }
        }
        return slots
    }

    private fun altarReason(player: ServerPlayer, record: JohtoRecord, slots: IntArray): Int = when {
        !config.enabled -> S2CJohtoAltarPacket.DISABLED
        record.summon == SummonState.SUMMONING -> S2CJohtoAltarPacket.SUMMONING
        !free(player) -> S2CJohtoAltarPacket.BUSY
        slots.any { it < 0 } -> S2CJohtoAltarPacket.MISSING
        else -> S2CJohtoAltarPacket.OK
    }

    /** The altar clicked (D2): its screen, which reads the inventory (D3). */
    private fun altarClicked(player: ServerPlayer, pos: BlockPos): Boolean {
        if (!altarAt(player.serverLevel(), pos)) return false
        val record = JohtoData.getOrCreate(player.server, player.uuid)
        if (record.summon == SummonState.SUMMONING && free(player)) {
            // A summoning cut short: Ho-Oh is still coming (D14), no memory needed.
            startSummon(player, test = false, replay = true)
            return true
        }
        val slots = memorySlots(player)
        var present = 0
        for (i in 0..2) if (slots[i] >= 0) present = present or (1 shl i)
        var token = random.nextInt()
        if (token == 0) token = 1
        altarTokens[player.uuid] = AltarToken(token, player.serverLevel().gameTime + 1200L)
        player.sendPacket(S2CJohtoAltarPacket(token, present, altarReason(player, record, slots)))
        return true
    }

    /**
     * "Summon Ho-Oh" confirmed: everything checked (the scene could start, a safe place), then in
     * the same tick the three memories taken (all or none), saved, and the scene. Nothing is
     * taken if anything stops it before.
     */
    fun summon(player: ServerPlayer, token: Int) {
        val t = altarTokens.remove(player.uuid) ?: return
        if (t.token != token) return
        val level = player.serverLevel()
        if (level.gameTime > t.expires) return
        val altar = places.altar ?: return
        if (!inDimension(level) || player.position().distanceTo(Vec3(altar.x + 0.5, altar.y.toDouble(), altar.z + 0.5)) > 12.0) {
            say(player, key("altar.far"), ChatFormatting.GRAY, true)
            return
        }
        val record = JohtoData.getOrCreate(player.server, player.uuid)
        val slots = memorySlots(player)
        val reason = altarReason(player, record, slots)
        if (reason != S2CJohtoAltarPacket.OK) {
            say(player, key("altar.refused.$reason"), ChatFormatting.GRAY, true)
            return
        }
        val request = summonRequest(player, test = false)
        LegendaryCinematics.check(request)?.let {
            say(player, it.copy(), ChatFormatting.GRAY, true)
            return
        }
        val stacks = slots.map { player.inventory.getItem(it) }
        if (stacks.withIndex().any { (i, s) -> !s.`is`(memoryItem(JohtoBeast.entries[i])) }) return
        if (!JohtoStateMachine.summon(record)) return
        stacks.forEach { it.shrink(1) }
        JohtoData.saveNow(player.server)
        JohtoTrials.log("{} offered the three memories at the altar", player.scoreboardName)
        when (LegendaryCinematics.start(request)) {
            is LegendaryCinematics.StartResult.Started -> {
                playing += player.uuid
                markSeen(player.server, record, CinematicScenes.HO_OH_SUMMON)
            }
            // Cannot happen after check(); Ho-Oh still comes, without its scene.
            is LegendaryCinematics.StartResult.Refused -> summoned(player.uuid, null, false)
        }
    }

    private fun summonRequest(player: ServerPlayer, test: Boolean, spawnTest: Boolean = false): LegendaryCinematics.Request {
        val level = player.serverLevel()
        val altar = places.altar
        val origin = if (altar != null && inDimension(level)) Vec3(altar.x + 0.5, altar.y.toDouble() + 1.0, altar.z + 0.5) else player.position().add(forward(player.yRot, 5.0))
        val facing = altar?.yaw?.takeIf { inDimension(level) } ?: yawTo(origin, player.position())
        val spawn = places.spawns("hooh").map { Vec3(it.x, it.y, it.z) }.filter { it.distanceTo(origin) <= 64.0 }.minByOrNull { it.distanceTo(origin) }
            ?: CinematicPlaces.landing(level, origin, facing, 4.0, 4.0)
        val record = JohtoData.getOrCreate(player.server, player.uuid)
        val uuid = player.uuid
        return LegendaryCinematics.Request(
            level, CinematicScenes.HO_OH_SUMMON, origin, facing, spawn, player, ResourceLocation.parse("cobblemon:hooh"), emptySet(), test && !spawnTest,
            onSpawn = { session -> summoned(uuid, session, test) },
            exclusive = false, solo = true, ambientRadius = config.spectatorRadius,
            ambientFilter = { !JohtoTrials.isRunning(it.uuid) && !MewGame.isPlaying(it.uuid) },
            skippable = canSkip(record, CinematicScenes.HO_OH_SUMMON),
        )
    }

    /** Ho-Oh's scene (a replay of a summoning cut short, or a test). */
    fun startSummon(player: ServerPlayer, test: Boolean, replay: Boolean = false, spawnTest: Boolean = false): Boolean {
        val request = summonRequest(player, test, spawnTest)
        return when (val result = LegendaryCinematics.start(request)) {
            is LegendaryCinematics.StartResult.Refused -> {
                say(player, result.message.copy(), ChatFormatting.GRAY, true)
                false
            }
            is LegendaryCinematics.StartResult.Started -> {
                if (replay) playing += player.uuid
                true
            }
        }
    }

    private fun summoned(uuid: UUID, session: CinematicSession?, test: Boolean) {
        playing -= uuid
        val server = com.cobblemon.mod.common.util.server() ?: return
        val player = server.playerList.getPlayer(uuid)
        if (player == null || !player.isAlive || !server.isRunning) return
        if (session != null && player.level().dimension() != session.dimension) return
        val level = player.serverLevel()
        val at = session?.spawn ?: spawnPoint(player, "hooh", "cobblemon:hooh")
        val text = buildString {
            append("cobblemon:hooh level=").append(config.hoOhLevel)
            if (config.hoOhProperties.isNotBlank()) append(' ').append(config.hoOhProperties.trim())
        }
        try {
            val entity = PokemonProperties.parse(text).createEntity(level)
            if (!test) {
                val record = JohtoData.get(server, uuid) ?: return
                if (!JohtoStateMachine.hoOhSpawned(record)) return
                JohtoData.saveNow(server)
            }
            PokemonUtil.spawn(entity, level, at, (session?.facing ?: player.yRot) + 180f)
            say(player, key(if (test) "summon.test_spawn" else "summon.spawn"), ChatFormatting.GOLD)
            JohtoTrials.log("{} Ho-Oh of {} at {}", if (test) "a test" else "the summoned", player.scoreboardName, at)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Legend of Johto: the Ho-Oh of {} could not appear", player.scoreboardName, e)
        }
    }

    // ---------------------------------------------------------------- each tick

    private fun tick(server: MinecraftServer) {
        ticks++
        JohtoTrials.tick(server, ticks)
        if (ticks % 10L != 0L) return
        countdownTick(server)
        if (!config.enabled) return
        val now = System.currentTimeMillis()
        val data = JohtoData.of(server)
        for (player in server.playerList.players) {
            val record = data.records[player.uuid] ?: continue
            val level = player.serverLevel()
            val gameTime = level.gameTime
            for (beast in JohtoBeast.entries) {
                val b = record.track(beast)
                if (b.state == BeastState.COOLDOWN && now >= b.cooldownUntil && JohtoStateMachine.ready(record, beast, now)) {
                    data.setDirty()
                    say(player, key("ready", beastName(beast)), ChatFormatting.GOLD)
                }
            }
            if (playing.contains(player.uuid) || (notBefore[player.uuid] ?: 0L) > gameTime) continue
            // An encounter or a summoning still owed (after a won trial, or cut short).
            val pending = JohtoBeast.entries.firstOrNull { record.track(it).state == BeastState.ENCOUNTER && record.track(it).spawnedSession != record.track(it).session }
            if (pending != null && ticks % 40L == 0L && free(player)) {
                if (record.track(pending).memoryOwed) deliver(player, pending, record)
                if (!startEncounter(player, pending, test = false)) notBefore[player.uuid] = gameTime + 200L
                continue
            }
            // A trial's zone entered: its countdown.
            if (!JohtoTrials.isRunning(player.uuid) && !countdowns.containsKey(player.uuid)) {
                for (beast in JohtoBeast.entries) {
                    if (record.track(beast).state != BeastState.OPEN) continue
                    val zone = zoneFor(player, beast) ?: continue
                    if (!zone.contains(player.x, player.y, player.z) || !free(player)) continue
                    countdowns[player.uuid] = Countdown(beast, gameTime + config.countdownSeconds * 20L, zone)
                    break
                }
            }
            if (ticks % 400L == 0L && !JohtoTrials.isRunning(player.uuid) && !countdowns.containsKey(player.uuid)) {
                JohtoBeast.entries.firstOrNull { record.track(it).state == BeastState.OPEN }?.let { hint(player, it) }
            }
        }
    }

    private fun countdownTick(server: MinecraftServer) {
        if (countdowns.isEmpty()) return
        for ((uuid, c) in countdowns.entries.toList()) {
            val player = server.playerList.getPlayer(uuid)
            if (player == null) {
                countdowns.remove(uuid)
                continue
            }
            if (!c.zone.contains(player.x, player.y, player.z) || !player.isAlive || LegendaryCinematics.isWatching(uuid)) {
                countdowns.remove(uuid)
                say(player, key("countdown.cancelled"), ChatFormatting.GRAY, true)
                continue
            }
            val now = player.serverLevel().gameTime
            if (now < c.until) {
                say(player, key("countdown", beastName(c.beast), ((c.until - now + 19) / 20).toInt()), ChatFormatting.YELLOW, true)
                continue
            }
            countdowns.remove(uuid)
            val record = JohtoData.get(server, uuid) ?: continue
            if (!free(player) || !JohtoStateMachine.begin(record, c.beast)) continue
            JohtoData.changed(server)
            JohtoTrials.start(player, c.beast, c.zone, record.track(c.beast).round, test = false)
        }
    }

    /** An admin's test trial (nothing saved, a zone around them when none is declared). */
    fun testTrial(player: ServerPlayer, beast: JohtoBeast, round: Int): Boolean {
        if (JohtoTrials.isRunning(player.uuid)) return false
        val zone = testZones[player.uuid] ?: places.zone(beast)?.takeIf { inDimension(player.serverLevel()) && it.contains(player.x, player.y, player.z) }
            ?: setTestZone(player, 24.0)
        JohtoTrials.start(player, beast, zone, round, test = true)
        return true
    }

    fun isPlaying(player: UUID): Boolean = playing.contains(player)
}
