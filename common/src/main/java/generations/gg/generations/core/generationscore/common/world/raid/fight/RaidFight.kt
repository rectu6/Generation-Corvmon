package generations.gg.generations.core.generationscore.common.world.raid.fight

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.PassActionResponse
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.ai.RandomBattleAI
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.activestate.SentOutState
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.battle.DynamaxVisuals
import generations.gg.generations.core.generationscore.common.network.packets.raid.RaidHudMember
import generations.gg.generations.core.generationscore.common.network.packets.raid.S2CRaidCheerPacket
import generations.gg.generations.core.generationscore.common.network.packets.raid.S2CRaidHudPacket
import generations.gg.generations.core.generationscore.common.world.raid.RaidAllies
import generations.gg.generations.core.generationscore.common.world.raid.RaidAlly
import generations.gg.generations.core.generationscore.common.world.raid.RaidEffects
import generations.gg.generations.core.generationscore.common.world.raid.RaidManager
import generations.gg.generations.core.generationscore.common.world.raid.RaidResults
import generations.gg.generations.core.generationscore.common.world.raid.RaidShowdown
import generations.gg.generations.core.generationscore.common.world.raid.config.RaidConfig
import generations.gg.generations.core.generationscore.common.world.raid.den.RaidDens
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * A raid being fought: one Cobblemon battle per player (Showdown has no 4 against 1),
 * kept in step by the server.
 *
 * Each turn: every boss waits (RaidBossActor) until all players have chosen and the
 * fainted ones have cheered or not; the host battle (the first player's) plans the
 * boss's actions; then each battle plays in turn, the shared raid (boss HP, shield,
 * faints, participants) being copied in before and read back after. Every player
 * sees the same boss, the same turns and the others' actions in their log. The host
 * battle also runs the NPC allies.
 */
class RaidFight(
    val level: ServerLevel,
    val pos: BlockPos,
    val stars: Int,
    /** The den's boss as generated; never changed. */
    private val template: Pokemon,
    entries: List<Pair<ServerPlayer, Pokemon>>,
    /** NPCs for the places left. */
    val npcs: List<RaidAlly>,
) {
    class Member(val uuid: UUID, val name: String, val slot: Int, val chosen: Pokemon) {
        var battle: PokemonBattle? = null
        var boss: RaidBossActor? = null
        var gone = false
        var cheer: Boolean? = null
        /** Turn of the faint the member already cheered for. */
        var cheeredFor = -1
        var resultSent = false

        val live: Boolean get() = !gone && battle?.ended == false
        fun actor(): PlayerBattleActor? = battle?.actors?.firstOrNull { it is PlayerBattleActor } as? PlayerBattleActor
    }

    val members: List<Member> = entries.mapIndexed { i, (player, pokemon) -> Member(player.uuid, player.gameProfile.name, i, pokemon) }
    private val tier = RaidConfig.tier(stars)
    private val gson = Gson()
    var entity: PokemonEntity? = null
        private set
    private var shared: JsonObject? = null
    private var turnStart = -1L
    var ended = false
        private set
    private var outcome: String? = null

    val center: Vec3 get() = Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)
    val battleIds: List<UUID> get() = members.mapNotNull { it.battle?.battleId }

    private fun npcSlot(index: Int) = members.size + index
    private fun npcOf(slot: Int): RaidAlly? = npcs.getOrNull(slot - members.size)

    /** "Lucario de Léa": the Pokémon of a participant. */
    fun participantName(slot: Int): Component {
        members.getOrNull(slot)?.let { return Component.translatable("generations_core.raid.ally_pokemon", it.chosen.getDisplayName(false), it.name) }
        return npcOf(slot)?.displayName() ?: Component.literal("?")
    }

    // ---- Start ----------------------------------------------------------------

    fun start(): Boolean {
        val server = level.server
        // The boss above the den, Dynamaxed, facing the first player: it fights in the
        // first player's battle.
        val world = bossCopy()
        val boss = PokemonEntity(level, world)
        boss.addTag(RaidAllies.TEMPORARY)
        boss.isInvulnerable = true
        val facing = members.firstNotNullOfOrNull { server.playerList.getPlayer(it.uuid) }
            ?.let { (Mth.atan2(it.z - center.z, it.x - center.x) * Mth.RAD_TO_DEG).toFloat() - 90f } ?: 0f
        boss.moveTo(center.x, center.y, center.z, facing, 0f)
        boss.yHeadRot = facing
        boss.yBodyRot = facing
        RaidManager.track(this, boss.uuid)
        RaidManager.track(this, world.uuid)
        level.addFreshEntity(boss)
        // As Pokemon.sendOut does: the Pokémon knows its entity. Without it the battle
        // never puts the boss on the field nor waits for its moves.
        world.state = SentOutState(boss)
        DynamaxVisuals.start(world, GenerationsCore.CONFIG.raids.bossHeight)
        RaidEffects.pop(level, boss)
        entity = boss

        var first = true
        members.forEach { member ->
            val player = server.playerList.getPlayer(member.uuid)
            if (player == null) {
                member.gone = true
                return@forEach
            }
            // A healed copy: nothing that happens in the raid stays. Marked, so that it
            // is never saved with the world nor left behind.
            val copy = BattlePokemon.safeCopyOf(member.chosen).also {
                it.effectedPokemon.heal()
                it.effectedPokemon.persistentData.putBoolean(RaidManager.COPY_KEY, true)
                RaidManager.track(this, it.effectedPokemon.uuid)
            }
            val playerActor = PlayerBattleActor(member.uuid, listOf(copy))
            playerActor.battleTheme = boss.getBattleTheme()
            // The other players fight a copy of the boss shown by the same entity.
            val bossPokemon = if (first) world else bossCopy().also {
                RaidManager.track(this, it.uuid)
                it.state = SentOutState(boss)
            }
            val bossActor = RaidBossActor(bossPokemon.uuid, BattlePokemon(bossPokemon), tier.aiSkill, center)
            BattleRegistry.startBattle(BattleFormat.GEN_9_SINGLES, BattleSide(playerActor), BattleSide(bossActor))
                .ifSuccessful { battle ->
                    member.battle = battle
                    member.boss = bossActor
                    battle.onEndHandlers.add { onBattleEnded(member) }
                    first = false
                }
                .ifErrored { error ->
                    error.sendTo(player) { it }
                    member.gone = true
                }
        }
        val live = members.filter { it.live }
        if (live.isEmpty()) {
            boss.discard()
            return false
        }
        // The raid rules in every battle.
        live.forEach { member ->
            if (!RaidShowdown.start(member.battle!!, data(member, live.first()))) {
                server.playerList.getPlayer(member.uuid)?.sendSystemMessage(Component.translatable("generations_core.raid.no_allies"))
            }
        }
        server.playerList.getPlayer(live.first().uuid)?.let { player -> RaidAllies.spawn(level, npcs, player, boss) { RaidManager.track(this, it.uuid) } }
        shared = RaidShowdown.state(live.first().battle!!)
        GenerationsCore.LOGGER.info(
            "Raid: {}★ {} at {} started, {} player(s), {} NPC(s), boss HP {}",
            stars, template.species.name, pos.toShortString(), live.size, npcs.size, shared?.get("bossMaxHp") ?: "?",
        )
        members.forEach { member ->
            server.playerList.getPlayer(member.uuid)?.sendSystemMessage(
                Component.translatable("generations_core.raid.started", template.species.translatedName, "★".repeat(stars)),
            )
        }
        sendHud()
        return true
    }

    /** A healed copy of the den's boss, marked as a raid boss. */
    private fun bossCopy(): Pokemon = template.clone(true, level.registryAccess()).apply {
        uuid = UUID.randomUUID()
        persistentData.putLong(RaidManager.BOSS_KEY, pos.asLong())
        heal()
    }

    /** What raid.js needs for one battle: its slot, whether it hosts the NPCs, settings, participants. */
    private fun data(member: Member, host: Member): JsonObject = JsonObject().apply {
        addProperty("localSlot", member.slot)
        addProperty("host", member === host)
        add("tier", gson.toJsonTree(tier))
        add("cfg", gson.toJsonTree(RaidConfig.battle))
        add("participants", JsonArray().apply {
            members.forEach { other ->
                add(JsonObject().apply {
                    addProperty("slot", other.slot)
                    addProperty("name", other.name)
                    if (other === member) {
                        addProperty("kind", "local")
                    } else {
                        addProperty("kind", "remote")
                        addProperty("packed", RaidShowdown.pack(other.chosen.clone(true, level.registryAccess()).apply { heal() }))
                    }
                })
            }
            npcs.forEachIndexed { i, npc ->
                val packed = runCatching { RaidShowdown.pack(npc.pokemon) }.getOrNull() ?: return@forEachIndexed
                add(JsonObject().apply {
                    addProperty("slot", npcSlot(i))
                    addProperty("name", npc.name)
                    addProperty("kind", "npc")
                    addProperty("packed", packed)
                })
            }
        })
    }

    // ---- Turns ----------------------------------------------------------------

    private fun participant(slot: Int): JsonObject? =
        shared?.getAsJsonArray("participants")?.firstOrNull { it.asJsonObject["slot"].asInt == slot }?.asJsonObject

    /** Down this turn: fainted and waiting to come back. */
    private fun isDown(member: Member): Boolean = participant(member.slot)?.get("down")?.takeIf { !it.isJsonNull } != null

    private fun downTurn(member: Member): Int = participant(member.slot)?.get("down")?.takeIf { !it.isJsonNull }?.asInt ?: -1

    fun tick() {
        if (ended) return
        val live = members.filter { it.live }
        if (live.isEmpty()) {
            end("abandoned")
            return
        }
        // The boss entity belongs to the battles still going on (a battle that ends,
        // e.g. a player leaving, frees it).
        entity?.let { boss -> if (live.none { it.battle?.battleId == boss.battleId }) boss.battleId = live.first().battle?.battleId }
        // Every boss waits for the turn: all battles are ready to play it.
        if (live.any { it.boss?.pending != true }) {
            turnStart = -1
            watchdog(live)
            return
        }
        stuckSince = -1
        val cheers = RaidConfig.battle.cheers
        if (turnStart < 0) {
            turnStart = level.gameTime
            // Fainted players may cheer the team during the turn they sit out.
            live.filter { isDown(it) && it.cheeredFor != downTurn(it) }.forEach { member ->
                member.cheer = null
                level.server.playerList.getPlayer(member.uuid)?.let { S2CRaidCheerPacket(cheers.cheerSeconds).sendToPlayer(it) }
            }
        }
        val elapsed = level.gameTime - turnStart
        val cheering = live.filter { isDown(it) && it.cheeredFor != downTurn(it) && it.cheer == null }
        if (cheering.isNotEmpty() && elapsed < cheers.cheerSeconds * 20L) return
        // Down players pass; idle ones get a move chosen for them at the end of the time.
        live.filter { isDown(it) && it.actor()?.mustChoose == true }.forEach { pass(it) }
        val waiting = live.filter { !isDown(it) && it.actor()?.mustChoose == true }
        if (waiting.isNotEmpty() && elapsed < RaidConfig.battle.turnSeconds * 20L) return
        waiting.forEach { choose(it) }
        resolve(live)
        turnStart = -1
    }

    private var stuckSince = -1L

    /**
     * Safety net: a player has chosen and Cobblemon sent their move, but Showdown still
     * waits for the boss, which Cobblemon is not asking (the battle would wait forever).
     * After a few seconds Showdown chooses for the boss, and the log says so.
     */
    private fun watchdog(live: List<Member>) {
        if (level.gameTime % 20L != 0L) return
        val stuck = live.filter { member ->
            val battle = member.battle ?: return@filter false
            val actor = member.actor() ?: return@filter false
            val boss = member.boss ?: return@filter false
            !boss.pending && !actor.mustChoose && actor.request == null && RaidShowdown.waiting(battle, boss.showdownId)
        }
        if (stuck.isEmpty()) {
            stuckSince = -1
            return
        }
        if (stuckSince < 0) stuckSince = level.gameTime
        if (level.gameTime - stuckSince < 60L) return
        stuck.forEach { member ->
            val boss = member.boss!!
            GenerationsCore.LOGGER.warn(
                "Raid: {}'s battle waited for the boss (mustChoose {}, request {}); Showdown chooses for it",
                member.name, boss.mustChoose, boss.request != null,
            )
            member.battle!!.writeShowdownAction(">${boss.showdownId} default")
        }
        stuckSince = -1
    }

    fun cheer(player: ServerPlayer, accept: Boolean) {
        val member = members.firstOrNull { it.uuid == player.uuid } ?: return
        if (!isDown(member) || member.cheeredFor == downTurn(member)) return
        member.cheer = accept
    }

    private fun pass(member: Member) {
        val actor = member.actor() ?: return
        runCatching { actor.request?.let { actor.setActionResponses(it.iterate(actor.activePokemon) { _, _, _ -> PassActionResponse }) } }
    }

    /** An idle player: a move is chosen for them. */
    private fun choose(member: Member) {
        val actor = member.actor() ?: return
        val battle = member.battle ?: return
        runCatching {
            actor.request?.let { request ->
                actor.setActionResponses(request.iterate(actor.activePokemon) { active, moveset, forceSwitch ->
                    RandomBattleAI().choose(active, battle, actor.getSide(), moveset, forceSwitch)
                })
            }
        }
        level.server.playerList.getPlayer(member.uuid)?.displayClientMessage(Component.translatable("generations_core.raid.auto_move"), true)
    }

    /** Plays the turn in every battle, one after the other, sharing the raid. */
    private fun resolve(live: List<Member>) {
        val host = live.first()
        val plan = RaidShowdown.plan(host.battle!!) ?: JsonObject()

        // Cheers of the fainted players, the same effect in every battle.
        live.filter { isDown(it) && it.cheer == true && it.cheeredFor != downTurn(it) }.forEach { member ->
            member.cheeredFor = downTurn(member)
            member.cheer = null
            val (effect, stat) = rollCheer()
            live.forEach { other -> RaidShowdown.cheer(other.battle!!, member.slot, effect, stat) }
        }
        live.filter { isDown(it) && it.cheer == false }.forEach { it.cheeredFor = downTurn(it); it.cheer = null }

        for (member in live) {
            val battle = member.battle ?: continue
            if (battle.ended) continue
            RaidShowdown.apply(battle, shared, plan)
            member.boss?.release(plan.get("move")?.takeIf { !it.isJsonNull }?.asString)
            val state = RaidShowdown.state(battle) ?: continue
            merge(state, member, member === host)
            relay(state, member, live)
            if (member !== host && plan.get("target")?.asInt == member.slot) animateBoss()
            if (state.get("ended")?.asBoolean == true) break
        }
        // The battles played first get the final shared raid.
        live.forEach { member -> member.battle?.takeIf { !it.ended }?.let { RaidShowdown.apply(it, shared, null) } }
        checkEnd(live)
        sendHud()
    }

    /** The shared raid takes this battle's boss, shield, faints, own participant (and NPCs on the host). */
    private fun merge(state: JsonObject, member: Member, host: Boolean) {
        val previous = shared ?: state
        val merged = state.deepCopy()
        val participants = JsonArray()
        previous.getAsJsonArray("participants").forEach { old ->
            val slot = old.asJsonObject["slot"].asInt
            val mine = slot == member.slot || (host && slot >= members.size)
            val source = if (mine) state.getAsJsonArray("participants").firstOrNull { it.asJsonObject["slot"].asInt == slot } ?: old else old
            participants.add(source.deepCopy())
        }
        merged.add("participants", participants)
        // A player who left stays gone.
        members.filter { it.gone }.forEach { gone ->
            participants.firstOrNull { it.asJsonObject["slot"].asInt == gone.slot }?.asJsonObject?.addProperty("gone", true)
        }
        shared = merged
    }

    /** What happened in one battle, told in the others' logs. */
    private fun relay(state: JsonObject, from: Member, live: List<Member>) {
        val events = state.getAsJsonArray("events") ?: return
        events.forEach { element ->
            val event = element.asJsonObject
            val slot = event.get("slot")?.asInt ?: -1
            // Told in the other battles: a player's move, faints, revivals, the shield and
            // the NPCs (who only act in the host battle).
            val bossName = template.getDisplayName(false)
            val text: Component = when (event.get("type")?.asString) {
                "move" -> Component.translatable("generations_core.raid.ally_move", participantName(slot), moveName(event.get("move")?.asString ?: ""))
                "down" -> Component.translatable("generations_core.raid.ally_down", participantName(slot))
                "revive" -> Component.translatable("generations_core.raid.ally_revive", participantName(slot))
                "shield" -> Component.translatable("generations_core.raid.shield_up", bossName)
                "shieldbreak" -> Component.translatable("generations_core.raid.shield_broken", bossName)
                "cheer" -> Component.translatable(
                    "generations_core.raid.cheer.${event.get("effect")?.asString ?: "nothing"}",
                    npcOf(slot)?.name ?: members.getOrNull(slot)?.name ?: "?", statName(event.get("stat")?.asString ?: ""),
                )
                else -> return@forEach
            }
            live.filter { it !== from }.forEach { other -> other.battle?.let { b -> b.dispatchGo { b.broadcastChatMessage(text) } } }
        }
    }

    private fun moveName(name: String): Component =
        Moves.getByName(name.lowercase().filter { it.isLetterOrDigit() })?.displayName ?: Component.literal(name)

    private fun animateBoss() {
        val boss = entity ?: return
        PlayPosableAnimationPacket(boss.id, setOf("physical"), emptyList()).sendToPlayersAround(boss.x, boss.y, boss.z, 64.0, level.dimension())
    }

    private fun rollCheer(): Pair<String, String> {
        val c = RaidConfig.battle.cheers
        val weights = listOf("boost" to c.boostWeight, "cure" to c.cureWeight, "heal" to c.healWeight, "nothing" to c.nothingWeight)
        var roll = level.random.nextDouble() * weights.sumOf { it.second }
        val effect = weights.firstOrNull { roll -= it.second; roll <= 0 }?.first ?: "nothing"
        val stat = listOf("atk", "def", "spa", "spd", "spe")[level.random.nextInt(5)]
        return effect to stat
    }

    // ---- End ------------------------------------------------------------------

    private fun checkEnd(live: List<Member>) {
        val state = shared ?: return
        val bossDown = (state.get("bossHp")?.asInt ?: 1) <= 0
        val winner = state.get("winner")?.takeIf { !it.isJsonNull }?.asString
        when {
            bossDown || winner == "players" -> {
                live.forEach { member -> member.battle?.takeIf { !it.ended }?.let { RaidShowdown.finish(it, "win") } }
                end("victory")
            }
            winner == "boss" -> {
                val reason = state.getAsJsonArray("events")?.firstOrNull { it.asJsonObject["type"]?.asString == "fail" }
                    ?.asJsonObject?.get("reason")?.asString ?: "faints"
                live.forEach { member -> member.battle?.takeIf { !it.ended }?.let { RaidShowdown.finish(it, reason) } }
                end("defeat")
            }
        }
    }

    /** A battle ended: the raid's end, or one player leaving (flight, disconnection). */
    private fun onBattleEnded(member: Member) {
        if (!ended) {
            member.gone = true
            members.filter { it.live }.forEach { other -> other.battle?.let { RaidShowdown.leave(it, member.slot) } }
            if (members.none { it.live }) end("abandoned")
            else sendHud()
        }
        sendResult(member)
        level.server.playerList.getPlayer(member.uuid)?.let { S2CRaidHudPacket(false).sendToPlayer(it) }
    }

    /** The boss fainted in this member's battle: the end menu opens. */
    fun onBossFainted(battle: PokemonBattle) {
        val member = members.firstOrNull { it.battle?.battleId == battle.battleId } ?: return
        afterOnServer(0.3f) { sendResult(member) }
    }

    private fun sendResult(member: Member) {
        if (outcome != "victory" || member.resultSent) return
        val player = level.server.playerList.getPlayer(member.uuid) ?: return
        if (!RaidResults.has(player)) return
        member.resultSent = true
        RaidResults.send(player)
    }

    /**
     * The raid is over. Victory: each player still in it wins their own copy of the boss
     * to catch and their rewards. Defeat: the Dynamax energy explodes. Either way the
     * den becomes inactive until a next morning.
     */
    fun end(result: String) {
        if (ended) return
        ended = true
        outcome = result
        val boss = entity
        when (result) {
            "victory" -> {
                val eligible = members.filter { member ->
                    !member.gone && (RaidConfig.capture.faintedPlayersEligible || !isDown(member))
                }
                eligible.forEach { member -> level.server.playerList.getPlayer(member.uuid)?.let { RaidResults.create(it, template, stars) } }
                RaidDens.clear(level, pos)
            }
            "defeat" -> {
                members.forEach { member -> level.server.playerList.getPlayer(member.uuid)?.sendSystemMessage(Component.translatable("generations_core.raid.lost")) }
                if (boss != null && boss.isAlive) RaidEffects.explode(boss) { RaidDens.clear(level, pos) } else RaidDens.clear(level, pos)
            }
            else -> {
                // Everyone left (flight, disconnection).
                if (boss != null && boss.isAlive) RaidEffects.leave(boss)
                if (RaidConfig.dens.fleeEndsRaid) RaidDens.clear(level, pos)
            }
        }
        // The NPCs leave once the end is seen.
        afterOnServer(if (result == "defeat") 3.5f else 1.5f) { RaidAllies.despawn(npcs) }
        if (result == "victory") afterOnServer(4f) { entity?.takeIf { it.isAlive }?.let(RaidEffects::leave) }
        RaidManager.fightEnded(this)
    }

    // ---- HUD ------------------------------------------------------------------

    fun sendHud() {
        val state = shared
        val server = level.server
        members.filter { it.live }.forEach { viewer ->
            val player = server.playerList.getPlayer(viewer.uuid) ?: return@forEach
            val list = mutableListOf<RaidHudMember>()
            members.forEach { member ->
                val p = participant(member.slot)
                list += RaidHudMember(
                    member.name, RaidLobby.label(member.chosen),
                    p?.get("hp")?.asInt ?: member.chosen.maxHealth, p?.get("maxhp")?.asInt ?: member.chosen.maxHealth,
                    isDown(member) || member.gone,
                    member.chosen.species.resourceIdentifier.toString(), member.chosen.aspects.toList(),
                    if (member === viewer) RaidHudMember.YOU else RaidHudMember.PLAYER,
                )
            }
            npcs.forEachIndexed { i, npc ->
                val p = participant(npcSlot(i))
                list += RaidHudMember(
                    npc.name, npc.label, p?.get("hp")?.asInt ?: npc.pokemon.maxHealth, p?.get("maxhp")?.asInt ?: npc.pokemon.maxHealth,
                    p?.get("down")?.takeIf { !it.isJsonNull } != null,
                    npc.pokemon.species.resourceIdentifier.toString(), npc.pokemon.aspects.toList(), RaidHudMember.NPC,
                )
            }
            val shield = state?.getAsJsonObject("shield")
            S2CRaidHudPacket(
                true,
                state?.get("turn")?.asInt ?: 1, RaidConfig.battle.maxTurns,
                state?.get("faints")?.asInt ?: 0, RaidConfig.battle.maxFaints,
                list, stars,
                shield?.get("active")?.asBoolean ?: false, shield?.get("segments")?.asInt ?: 0, shield?.get("max")?.asInt ?: 0,
                viewer.battle?.battleId,
            ).sendToPlayer(player)
        }
    }

    /** The NPCs' and the boss's actions told by raid.js in one battle's log, with animations. */
    fun onMessage(battle: PokemonBattle, type: String, args: List<String>) {
        val boss = entity
        fun slotOf(text: String) = text.removePrefix("slot").toIntOrNull() ?: -1
        val effectKey = mapOf("super" to "generations_core.raid.effect.super", "weak" to "generations_core.raid.effect.weak", "immune" to "generations_core.raid.effect.immune", "miss" to "generations_core.raid.effect.miss")
        val bossName = template.getDisplayName(false)
        when (type) {
            "ally" -> battle.dispatchWaiting(1.2f) {
                val slot = args[0].toIntOrNull() ?: return@dispatchWaiting
                battle.broadcastChatMessage(Component.translatable("generations_core.raid.ally_move", participantName(slot), moveName(args[1])))
                effectKey[args[2]]?.let { battle.broadcastChatMessage(Component.translatable(it, bossName)) }
                if (args[2] != "miss" && args[2] != "immune") npcOf(slot)?.let { RaidAllies.attack(it, boss, isSpecial(args[1]), args[2] == "super") }
            }
            "boss", "aim" -> battle.dispatchWaiting(if (type == "boss") 1.2f else 0.6f) {
                val slot = args[0].toIntOrNull() ?: return@dispatchWaiting
                battle.broadcastChatMessage(Component.translatable("generations_core.raid.boss_move", bossName, moveName(args[1]), participantName(slot)))
                if (type == "boss") {
                    effectKey[args[2]]?.let { battle.broadcastChatMessage(Component.translatable(it, participantName(slot))) }
                    if (args[2] != "miss" && args[2] != "immune") npcOf(slot)?.let { RaidAllies.attacked(it, boss, isSpecial(args[1]), args[2] == "super") }
                }
            }
            "extra" -> battle.dispatchWaiting(0.6f) {
                battle.broadcastChatMessage(Component.translatable("generations_core.raid.extra_attack", bossName))
            }
            "down" -> battle.dispatchWaiting(0.8f) {
                val slot = slotOf(args[0])
                val faints = args[1].toIntOrNull() ?: 0
                npcOf(slot)?.let(RaidAllies::down)
                battle.broadcastChatMessage(
                    if (members.getOrNull(slot)?.battle?.battleId == battle.battleId) Component.translatable("generations_core.raid.player_down")
                    else Component.translatable("generations_core.raid.ally_down", participantName(slot)),
                )
                if (faints < RaidConfig.battle.maxFaints) {
                    battle.broadcastChatMessage(Component.translatable("generations_core.raid.storm", faints, RaidConfig.battle.maxFaints))
                    boss?.let(RaidEffects::storm)
                }
            }
            "revive" -> battle.dispatchWaiting(0.8f) {
                val slot = slotOf(args[0])
                npcOf(slot)?.let(RaidAllies::revive)
                battle.broadcastChatMessage(
                    if (members.getOrNull(slot)?.battle?.battleId == battle.battleId) Component.translatable("generations_core.raid.player_revive")
                    else Component.translatable("generations_core.raid.ally_revive", participantName(slot)),
                )
            }
            "shield" -> battle.dispatchWaiting(1f) {
                battle.broadcastChatMessage(Component.translatable("generations_core.raid.shield_up", bossName))
                boss?.let(RaidEffects::shieldRise)
            }
            "shieldbreak" -> battle.dispatchWaiting(1f) {
                battle.broadcastChatMessage(Component.translatable("generations_core.raid.shield_broken", bossName))
                boss?.let(RaidEffects::shieldBreak)
            }
            "shieldblock" -> battle.dispatchGo {
                battle.broadcastChatMessage(Component.translatable("generations_core.raid.shield_blocks", bossName))
            }
            "shockwave" -> battle.dispatchWaiting(1.2f) {
                battle.broadcastChatMessage(Component.translatable("generations_core.raid.shockwave", bossName))
                boss?.let(RaidEffects::shockwave)
            }
            "cheer" -> battle.dispatchWaiting(1f) {
                val slot = args[0].toIntOrNull() ?: return@dispatchWaiting
                val who = members.getOrNull(slot)?.name ?: npcOf(slot)?.name ?: "?"
                battle.broadcastChatMessage(Component.translatable("generations_core.raid.cheer.${args[1]}", who, statName(args.getOrElse(2) { "" })))
            }
            "fail" -> battle.dispatchGo {
                val turns = args[0] == "turns"
                battle.broadcastChatMessage(Component.translatable(
                    if (turns) "generations_core.raid.out_of_turns" else "generations_core.raid.too_many_faints",
                    if (turns) RaidConfig.battle.maxTurns else RaidConfig.battle.maxFaints,
                ))
            }
            "state" -> battle.dispatchGo { }
        }
    }

    private fun isSpecial(move: String) =
        Moves.getByName(move.lowercase().filter { it.isLetterOrDigit() })?.damageCategory == com.cobblemon.mod.common.api.moves.categories.DamageCategories.SPECIAL

    private fun statName(stat: String): Component = Component.translatable("generations_core.raid.stat.$stat")
}
