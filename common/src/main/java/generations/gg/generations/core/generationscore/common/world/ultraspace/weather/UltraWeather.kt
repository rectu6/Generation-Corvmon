package generations.gg.generations.core.generationscore.common.world.ultraspace.weather

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWeatherPacket
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWarpTransfers
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.WeatheringCopper
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * The weathers of the seven Ultra worlds (phase 4.4), decided by the server: one cycle
 * per world, apart from the others (calm, then a weather drawn by weight, then calm...,
 * UltraWeatherConfig), saved (UltraWeatherSavedData), sent to the world's players
 * (S2CUltraWeatherPacket). The dangers are UltraHazards'; the looks and sounds the
 * client's (UltraWeatherClient). The vanilla dimensions keep their own weather.
 */
object UltraWeather {
    private var server: MinecraftServer? = null
    private var data: UltraWeatherSavedData? = null
    /** The weathers in progress, by world (what UltraWeatherSky gives the server's levels). */
    private val active = HashMap<String, UltraWeatherSky.Active>()
    private val random = RandomSource.create()
    /** The electric storm: the game time of the next bolt near each player. */
    private val nextBolt = HashMap<UUID, Long>()

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) {
            server = it.server
            UltraWeatherConfig.load()
            data = UltraWeatherSavedData.get(it.server)
            active.clear()
            val now = now(it.server)
            for (id in UltraWorlds.IDS) {
                state(id, now)
                rebuild(id)
            }
        }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) {
            server = null
            data = null
            active.clear()
            nextBolt.clear()
            UltraHazards.clear()
        }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { tick(it.server) }
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe(Priority.NORMAL) { arrived(it.player, it.player.serverLevel()) }
        PlatformEvents.PLAYER_RESPAWN.subscribe(Priority.NORMAL) { arrived(it.player, it.player.serverLevel()) }
        PlatformEvents.CHANGE_DIMENSION.subscribe(Priority.NORMAL) { event -> arrived(event.player, event.destination ?: event.player.serverLevel()) }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(Priority.NORMAL) {
            nextBolt.remove(it.player.uuid)
            UltraHazards.remove(it.player.uuid)
        }
    }

    /** The Overworld's game time: the clock of every cycle (it stops with the server, /time does not change it). */
    fun now(server: MinecraftServer): Long = server.overworld().gameTime

    /** The weather in progress in a world (server side), null when calm. */
    fun active(world: String): UltraWeatherSky.Active? = active[world]

    private fun fade(): Int = UltraWeatherConfig.settings.transitionSeconds * 20

    private fun state(id: String, now: Long): UltraWeatherSavedData.WorldState {
        val data = data!!
        return data.worlds.getOrPut(id) {
            // A new save: each world starts with a calm of its own length, so that they never move together.
            val calm = UltraWeatherConfig.world(id)?.calmMinutes?.max ?: 15f
            UltraWeatherSavedData.WorldState(next = pick(id)?.id, nextStart = now + 1200L + random.nextInt(((calm + 10f) * 1200f).toInt().coerceAtLeast(1)))
                .also { data.setDirty() }
        }
    }

    private fun rebuild(id: String) {
        val state = data?.worlds?.get(id)
        val event = UltraWeatherEvent.byId(state?.event)
        if (state == null || event == null) active.remove(id)
        else active[id] = UltraWeatherSky.Active(id, event, state.start, state.end, state.wind, fade())
    }

    /** A weather of the world drawn by weight among the enabled ones, null when none can come. */
    private fun pick(id: String): UltraWeatherEvent? {
        val world = UltraWeatherConfig.world(id) ?: return null
        val choices = UltraWeatherEvent.of(id).mapNotNull { event -> world.events[event.id]?.takeIf { it.enabled && it.weight > 0 }?.let { event to it.weight } }
        val total = choices.sumOf { it.second }
        if (total <= 0) return null
        var roll = random.nextInt(total)
        for ((event, weight) in choices) {
            if (roll < weight) return event
            roll -= weight
        }
        return choices.last().first
    }

    private fun on(id: String): Boolean = UltraWeatherConfig.settings.enabled && UltraWeatherConfig.world(id)?.enabled == true

    private fun tick(server: MinecraftServer) {
        if (data == null) return
        val now = now(server)
        if (server.tickCount % 20 == 0) {
            for (id in UltraWorlds.IDS) advance(server, id, now)
            lightning(server, now)
        }
        UltraHazards.tick(server, now)
    }

    /** One world's cycle, every second. */
    private fun advance(server: MinecraftServer, id: String, now: Long) {
        val data = data ?: return
        val state = state(id, now)
        val event = UltraWeatherEvent.byId(state.event)
        if (event != null) {
            // Turned off meanwhile: it goes away (in the fade) instead of stopping at once.
            val wanted = on(id) && UltraWeatherConfig.event(event)?.enabled != false
            if (!wanted && state.end > now + fade()) {
                state.end = now + fade()
                data.setDirty()
                rebuild(id)
                broadcast(server, id)
            }
            if (now >= state.end) finish(server, id, state, event, now)
            return
        }
        if (!on(id)) return
        var next = UltraWeatherEvent.byId(state.next)
        if (state.nextStart < 0 || next == null || UltraWeatherConfig.event(next)?.let { it.enabled && it.weight > 0 } != true) {
            plan(id, state, now)
            next = UltraWeatherEvent.byId(state.next) ?: return
        }
        val warning = UltraWeatherConfig.settings.warningSeconds * 20L
        if (next.dangerous && !state.warned && warning > 0 && state.nextStart - now <= warning) {
            state.warned = true
            data.setDirty()
            val seconds = ((state.nextStart - now + 19) / 20).coerceAtLeast(1)
            tell(server, id, Component.translatable("generations_core.ultra_weather.warning", next.displayName(), seconds).withStyle(ChatFormatting.GOLD))
        }
        if (now >= state.nextStart) begin(server, id, state, next, now, null)
    }

    /** The calm after an event: its length drawn, and the weather that will follow. */
    private fun plan(id: String, state: UltraWeatherSavedData.WorldState, now: Long) {
        val next = pick(id)
        state.next = next?.id
        // No weather can come: looked at again in a minute (after /ultrawarp reload, say).
        state.nextStart = if (next == null) now + 1200L else now + ((UltraWeatherConfig.world(id)?.calmMinutes?.roll(random) ?: 10f) * 1200f).toLong()
        state.warned = false
        data?.setDirty()
    }

    private fun begin(server: MinecraftServer, id: String, state: UltraWeatherSavedData.WorldState, event: UltraWeatherEvent, now: Long, minutes: Float?) {
        val duration = minutes ?: UltraWeatherConfig.event(event)?.durationMinutes?.roll(random) ?: 50f
        state.event = event.id
        state.start = now
        state.end = now + (duration * 1200f).toLong().coerceAtLeast(fade() * 2L + 20L)
        state.wind = random.nextFloat() * Mth.TWO_PI
        state.next = null
        state.nextStart = -1
        state.warned = false
        data?.setDirty()
        rebuild(id)
        broadcast(server, id)
        tell(server, id, Component.translatable("generations_core.ultra_weather.start.${event.id}").withColor(event.colour))
        if (event.dangerous) tell(server, id, Component.translatable("generations_core.ultra_weather.advice.${event.id}").withStyle(ChatFormatting.GOLD))
        log("{}: {} begins, {} minutes", id, event.id, Math.round(duration))
    }

    private fun finish(server: MinecraftServer, id: String, state: UltraWeatherSavedData.WorldState, event: UltraWeatherEvent, now: Long) {
        state.event = null
        plan(id, state, now)
        rebuild(id)
        broadcast(server, id)
        tell(server, id, Component.translatable("generations_core.ultra_weather.end", event.displayName()).withStyle(ChatFormatting.GRAY))
        log("{}: {} ends, next {} in {} minutes", id, event.id, state.next, (state.nextStart - now) / 1200)
    }

    private fun packet(id: String): S2CUltraWeatherPacket {
        val state = active[id]
        return if (state == null) S2CUltraWeatherPacket(id, "", 0, 0, 0f, 0)
        else S2CUltraWeatherPacket(id, state.event.id, state.start, state.end, state.wind, state.fade)
    }

    private fun level(server: MinecraftServer, id: String): ServerLevel? = UltraWorlds.key(id)?.let { server.getLevel(it) }

    private fun broadcast(server: MinecraftServer, id: String) {
        val packet = packet(id)
        level(server, id)?.players()?.forEach { packet.sendToPlayer(it) }
    }

    private fun tell(server: MinecraftServer, id: String, message: Component) {
        level(server, id)?.players()?.forEach { it.sendSystemMessage(message) }
    }

    /** In a level (login, respawn, change of dimension): its weather, or none outside the Ultra worlds. */
    private fun arrived(player: ServerPlayer, level: Level) {
        val id = UltraWorlds.idOf(level.dimension())
        (if (id != null) packet(id) else S2CUltraWeatherPacket("", "", 0, 0, 0f, 0)).sendToPlayer(player)
        UltraHazards.arrived(player)
    }

    /**
     * The electric storm's bolts: now and then one falls 16 to 64 blocks from each player
     * outside. Visual only (no fire, no damage), never on a lightning rod nor on copper,
     * so that no block changes.
     */
    private fun lightning(server: MinecraftServer, now: Long) {
        val id = UltraWeatherEvent.ELECTRIC_STORM.world
        val storm = active[id]?.takeIf { it.event == UltraWeatherEvent.ELECTRIC_STORM } ?: return
        if (storm.intensity(now.toDouble()) < 0.6f) return
        val level = level(server, id) ?: return
        val range = UltraWeatherConfig.settings.lightningSeconds
        for (player in level.players()) {
            if (player.isSpectator) continue
            val due = nextBolt[player.uuid]
            if (due == null) { nextBolt[player.uuid] = now + (range.roll(random) * 20f).toLong(); continue }
            if (now < due) continue
            nextBolt[player.uuid] = now + (range.roll(random) * 20f).toLong()
            val angle = random.nextFloat() * Mth.TWO_PI
            val distance = 16.0 + random.nextDouble() * 48.0
            val x = Mth.floor(player.x + Mth.cos(angle) * distance)
            val z = Mth.floor(player.z + Mth.sin(angle) * distance)
            if (!level.hasChunkAt(BlockPos(x, 0, z))) continue
            val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
            if (y <= level.minBuildHeight) continue
            val struck = level.getBlockState(BlockPos(x, y - 1, z))
            if (struck.`is`(Blocks.LIGHTNING_ROD) || struck.block is WeatheringCopper) continue
            val bolt = EntityType.LIGHTNING_BOLT.create(level) ?: continue
            bolt.moveTo(Vec3.atBottomCenterOf(BlockPos(x, y, z)))
            bolt.setVisualOnly(true)
            level.addFreshEntity(bolt)
        }
    }

    // ---- /ultrawarp weather ----

    /** A weather now (minutes: its length, else drawn), or the calm (event null). */
    fun force(server: MinecraftServer, id: String, event: UltraWeatherEvent?, minutes: Float?): Boolean {
        if (data == null || id !in UltraWorlds.IDS) return false
        val now = now(server)
        val state = state(id, now)
        UltraWeatherEvent.byId(state.event)?.let { finish(server, id, state, it, now) }
        if (event != null) begin(server, id, state, event, now, minutes)
        return true
    }

    /** The next step at once: the event in progress ends, or the next one starts. */
    fun skip(server: MinecraftServer, id: String): Boolean {
        if (data == null || id !in UltraWorlds.IDS) return false
        val now = now(server)
        val state = state(id, now)
        val running = UltraWeatherEvent.byId(state.event)
        if (running != null) {
            finish(server, id, state, running, now)
        } else {
            val next = UltraWeatherEvent.byId(state.next) ?: pick(id) ?: return false
            begin(server, id, state, next, now, null)
        }
        return true
    }

    /** One line per world: the weather in progress and its minutes left, or the next and when. */
    fun status(server: MinecraftServer): List<Component> {
        val now = now(server)
        return UltraWorlds.IDS.map { id ->
            val name = UltraWarpTransfers.worldName(id)
            val state = data?.worlds?.get(id)
            val event = UltraWeatherEvent.byId(state?.event)
            when {
                state == null -> Component.translatable("generations_core.ultra_weather.status.unknown", name)
                !on(id) && event == null -> Component.translatable("generations_core.ultra_weather.status.off", name)
                event != null -> Component.translatable("generations_core.ultra_weather.status.active", name, event.displayName(), (state.end - now).coerceAtLeast(0) / 1200 + 1)
                else -> Component.translatable(
                    "generations_core.ultra_weather.status.calm", name,
                    UltraWeatherEvent.byId(state.next)?.displayName() ?: Component.literal("?"), (state.nextStart - now).coerceAtLeast(0) / 1200 + 1,
                )
            }
        }
    }

    private fun log(message: String, vararg args: Any?) = GenerationsCore.LOGGER.info("Ultra Space weather: $message", *args)
}
