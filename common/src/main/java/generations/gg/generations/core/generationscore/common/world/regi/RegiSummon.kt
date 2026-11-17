package generations.gg.generations.core.generationscore.common.world.regi

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.config.LegendKeys
import generations.gg.generations.core.generationscore.common.config.SpeciesKey
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicPlaces
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicSession
import generations.gg.generations.core.generationscore.common.world.cinematic.LegendaryCinematics
import generations.gg.generations.core.generationscore.common.world.entity.block.PokemonUtil
import generations.gg.generations.core.generationscore.common.world.johto.JohtoTrials
import generations.gg.generations.core.generationscore.common.world.level.block.UnownBlock
import generations.gg.generations.core.generationscore.common.world.level.block.shrines.RegiShrineBlock
import generations.gg.generations.core.generationscore.common.world.level.block.shrines.RegigigasShrineBlock
import generations.gg.generations.core.generationscore.common.world.mew.MewGame
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.Locale
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Regis woken at their shrines with a scene (REGIS-PLAN.md), the same way as Lugia's
 * (LugiaSummon): the original consumption happens at the same moment, then the scene, then the
 * Pokémon at its end (2 ticks before), once. Regigigas: the very call of its shrine
 * (PokemonUtil.spawn, level 70, the block above the shrine); the five Regis: the same call with
 * their own species (D2: the original made none). No scene (cinematics off, the invoker busy,
 * another scene near): the Pokémon at once.
 *
 * A record (SavedData generations_regis) is written when the scene starts and removed, saved,
 * before the Pokémon appears: cut short, it appears at once; after a crash, when a player loads
 * the shrine's chunk again (checked every 2 s, nothing loaded by this). At worst none, never two.
 */
object RegiSummon {
    private const val NAME = "generations_regis"
    private const val KEY = "generations_core.cinematic.regi"
    const val LEVEL = 70

    /** The records whose scene is playing: their session (the tick leaves them to it). */
    private val playing = HashMap<UUID, UUID>()

    private var ticks = 0L

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { playing.clear() }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { tick(it.server) }
    }

    // ---------------------------------------------------------------- the shrines

    /**
     * A Regi's shrine whose puzzle is solved (RegiShrineBlock.useItemOn). `take`: the original
     * consumption (the letters, the key), run here in any case; the letters are read first, for
     * the scene. Then the Regi: at the scene's end, or at once.
     */
    fun summonRegi(level: ServerLevel, pos: BlockPos, player: ServerPlayer, key: SpeciesKey, letters: List<BlockPos>, take: () -> Unit) {
        val scene = key.species.path.takeIf { it in CinematicScenes.REGIS && it != CinematicScenes.REGIGIGAS }
        val glyphs = letters.mapNotNull { p -> (level.getBlockState(p).block as? UnownBlock)?.glyph?.let { it to p } }
        val place = place(level, pos, player.position())
        val id = UUID.randomUUID()
        val request = if (LegendaryCinematics.config.regiShrines && scene != null) {
            request(level, player, scene, place, key, false, letterOption(place, pos, glyphs)) { spawnMoment(level.server, id) }
        } else null
        val refusal = request?.let { LegendaryCinematics.check(it) }
        take()
        if (request == null || refusal != null) {
            GenerationsCore.LOGGER.info("{}'s shrine at {}: no scene ({}), the Regi appears at once", key.species.path, pos, refusal?.string ?: "off")
            appearNow(level, pos, key, place.yaw, player.gameProfile.name)
            return
        }
        begin(level, pos, player, key, place, id, request)
    }

    /**
     * Regigigas' shrine full (RegigigasShrineBlock.interact), before it is emptied as before.
     * True: its scene started and Regigigas appears at its end; false: nothing done, the
     * original call happens at once.
     */
    fun summonGigas(level: ServerLevel, pos: BlockPos, player: ServerPlayer): Boolean {
        if (!LegendaryCinematics.config.regiShrines) return false
        val place = place(level, pos, player.position())
        val id = UUID.randomUUID()
        val request = request(level, player, CinematicScenes.REGIGIGAS, place, LegendKeys.REGIGIGAS, false, "orbs=1") { spawnMoment(level.server, id) }
        LegendaryCinematics.check(request)?.let {
            GenerationsCore.LOGGER.info("Regigigas' shrine at {}: no scene ({}), the original summoning", pos, it.string)
            return false
        }
        return begin(level, pos, player, LegendKeys.REGIGIGAS, place, id, request)
    }

    /** The record, then the scene. False (cannot happen after check): the Pokémon at once. */
    private fun begin(level: ServerLevel, pos: BlockPos, player: ServerPlayer, key: SpeciesKey, place: Place, id: UUID, request: LegendaryCinematics.Request): Boolean {
        val server = level.server
        val data = RegiData.of(server)
        data.pending[id] = Pending(id, level.dimension().location(), pos.immutable(), place.yaw, key.species.toString(), key.aspects.toList(), LEVEL, player.gameProfile.name)
        saveNow(server)
        return when (val result = LegendaryCinematics.start(request)) {
            is LegendaryCinematics.StartResult.Started -> {
                playing[id] = result.session.id
                GenerationsCore.LOGGER.info("{} summoned by {} at {}: its scene plays ({})", key.species.path, player.gameProfile.name, pos, result.session.id)
                true
            }
            is LegendaryCinematics.StartResult.Refused -> {
                data.pending.remove(id)
                saveNow(server)
                if (key != LegendKeys.REGIGIGAS) appearNow(level, pos, key, place.yaw, player.gameProfile.name)
                false
            }
        }
    }

    /** The shrine's own call: the Pokémon level 70 at the centre of the block above it. */
    private fun appearNow(level: ServerLevel, pos: BlockPos, key: SpeciesKey, yaw: Float, invoker: String) {
        try {
            PokemonUtil.spawn(key.createProperties(LEVEL), level, pos.above(), yaw)
            GenerationsCore.LOGGER.info("{} summoned by {} appeared at {}", key.species.path, invoker, pos.above())
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("{}'s shrine at {}: the Pokémon could not appear", key.species.path, pos, e)
        }
    }

    /** The session's spawn moment (2 ticks before its end, or at once when cut short). */
    private fun spawnMoment(server: MinecraftServer, id: UUID) {
        playing.remove(id)
        appear(server, id)
    }

    /** The record's Pokémon, once: the record removed and written first. Not loaded: left for the tick. */
    private fun appear(server: MinecraftServer, id: UUID): Boolean {
        val data = RegiData.of(server)
        val entry = data.pending[id] ?: return false
        val level = server.getLevel(ResourceKey.create(Registries.DIMENSION, entry.dimension)) ?: return false
        if (!level.isLoaded(entry.pos)) return false
        data.pending.remove(id)
        saveNow(server)
        val species = ResourceLocation.tryParse(entry.species) ?: run {
            GenerationsCore.LOGGER.error("Regi's shrine at {}: unknown species {}", entry.pos, entry.species)
            return false
        }
        try {
            PokemonUtil.spawn(SpeciesKey(species, entry.aspects.toSet()).createProperties(entry.level), level, entry.pos.above(), entry.yaw)
            GenerationsCore.LOGGER.info("{} summoned by {} appeared at {}", species.path, entry.invoker, entry.pos.above())
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("{}'s shrine at {}: the Pokémon could not appear", species.path, entry.pos, e)
        }
        return true
    }

    /** Every 2 s: the records left by a crash, once their chunk is loaded (by players, never by this). */
    private fun tick(server: MinecraftServer) {
        if (++ticks % 40L != 0L) return
        val data = RegiData.peek(server) ?: return
        if (data.pending.isEmpty()) return
        playing.entries.removeIf { LegendaryCinematics.session(it.value) == null }
        for (id in data.pending.keys.toList()) if (id !in playing) appear(server, id)
    }

    // ---------------------------------------------------------------- the place

    /**
     * The scene's frame: the shrine's ground at the origin, facing the invoker (local +z toward
     * them); the Pokémon's place (the centre of the block above the shrine) and yaw (toward them);
     * the free height above the shrine and the free room toward the invoker, for the cameras
     * (a few dozen block reads, once).
     */
    class Place(val origin: Vec3, val facing: Float, val spawn: Vec3, val yaw: Float, val ceiling: Double, val room: Double)

    fun place(level: ServerLevel, pos: BlockPos, viewer: Vec3): Place {
        val origin = Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        val spawn = Vec3.atCenterOf(pos.above())
        val facing = if (viewer.distanceToSqr(origin.x, viewer.y, origin.z) > 0.25) CinematicPlaces.yawTowards(origin, viewer) else 0f
        var ceiling = 14.0
        for (h in 1..14) {
            val p = pos.above(h)
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty) {
                ceiling = h.toDouble()
                break
            }
        }
        var room = 12.0
        for (turn in doubleArrayOf(-70.0, -35.0, 0.0, 35.0, 70.0)) {
            val yaw = Math.toRadians(facing + turn)
            val dx = -sin(yaw)
            val dz = cos(yaw)
            var d = 1.0
            while (d < room) {
                val p = BlockPos.containing(origin.x + dx * d, origin.y + 1.6, origin.z + dz * d)
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty) {
                    room = d
                    break
                }
                d += 0.5
            }
        }
        return Place(origin, facing, spawn, facing, ceiling, room)
    }

    /**
     * The letters destroyed, for the scene: glyph and centre in the scene's frame (x right, y up,
     * z toward the invoker, from the shrine's ground; CinematicCamera.toLocal), "R:3.0:0.5:-2.0/...".
     */
    private fun letterOption(place: Place, pos: BlockPos, letters: List<Pair<String, BlockPos>>): String {
        val f = Math.toRadians(place.facing.toDouble())
        val cosF = cos(f)
        val sinF = sin(f)
        return letters.joinToString("/") { (glyph, p) ->
            val dx = (p.x - pos.x).toDouble()
            val dz = (p.z - pos.z).toDouble()
            "%s:%.2f:%.2f:%.2f".format(Locale.ROOT, glyph.take(1).uppercase(), cosF * dx + sinF * dz, p.y - pos.y + 0.5, -sinF * dx + cosF * dz)
        }.let { if (it.isEmpty()) "" else "u=$it" }
    }

    /** Letters for a test (nothing is destroyed): the name on a line behind the shrine, in the scene's frame. */
    private fun testLetters(name: String): String {
        val letters = name.uppercase().filter { it in 'A'..'Z' }
        return letters.mapIndexed { i, c ->
            "%s:%.2f:%.2f:%.2f".format(Locale.ROOT, c.toString(), (i - (letters.length - 1) / 2.0) * 1.1, 0.5, -3.5)
        }.joinToString("/").let { if (it.isEmpty()) "" else "u=$it" }
    }

    private fun request(
        level: ServerLevel, player: ServerPlayer, scene: String, place: Place, key: SpeciesKey, test: Boolean, extra: String,
        onSpawn: ((CinematicSession) -> Unit)?,
    ): LegendaryCinematics.Request {
        val options = "test=%d;ceil=%.1f;room=%.1f".format(Locale.ROOT, if (test) 1 else 0, place.ceiling, place.room) + (if (extra.isEmpty()) "" else ";$extra")
        return LegendaryCinematics.Request(
            level, scene, place.origin, place.facing, place.spawn, player, key.species, key.aspects, test,
            onSpawn = onSpawn, exclusive = true, solo = true, ambientRadius = LegendaryCinematics.config.regiSpectatorRadius,
            ambientFilter = { !JohtoTrials.isRunning(it.uuid) && !MewGame.isPlaying(it.uuid) },
            options = options.take(1000),
        )
    }

    // ---------------------------------------------------------------- commands

    /** A Regi's or Regigigas' shrine the player looks at (8 blocks), or null. */
    fun lookedAtShrine(player: ServerPlayer): BlockPos? {
        val hit = player.pick(8.0, 0f, false)
        if (hit.type != HitResult.Type.BLOCK) return null
        val pos = (hit as BlockHitResult).blockPos
        val block = player.serverLevel().getBlockState(pos).block
        return pos.takeIf { block is RegiShrineBlock || block is RegigigasShrineBlock }
    }

    private fun keyOf(scene: String): SpeciesKey = when (scene) {
        CinematicScenes.REGIROCK -> LegendKeys.REGIROCK
        CinematicScenes.REGICE -> LegendKeys.REGICE
        CinematicScenes.REGISTEEL -> LegendKeys.REGISTEEL
        CinematicScenes.REGIELEKI -> LegendKeys.REGIELEKI
        CinematicScenes.REGIDRAGO -> LegendKeys.REGIDRAGO
        else -> LegendKeys.REGIGIGAS
    }

    /**
     * /generations cinematic regi <species> [real] and test <species>: the scene at the shrine
     * looked at (or 6 blocks ahead, or at `at`), with letters drawn for it (none is touched).
     * Test: nothing taken, nothing appears. Real (a shrine needed): the scene and a real Pokémon
     * level 70 at its end, nothing taken. The refusal, or null.
     */
    fun admin(player: ServerPlayer, scene: String, real: Boolean, at: Vec3? = null): Component? {
        if (scene !in CinematicScenes.REGIS) return Component.translatable("generations_core.cinematic.command.unknown", scene)
        val level = player.serverLevel()
        val shrine = if (at == null) lookedAtShrine(player) else null
        val key = keyOf(scene)
        val gigas = scene == CinematicScenes.REGIGIGAS
        if (real) {
            shrine ?: return Component.translatable("$KEY.no_shrine")
            val place = place(level, shrine, player.position())
            val id = UUID.randomUUID()
            val request = request(level, player, scene, place, key, false, if (gigas) "orbs=1" else testLetters(scene)) { spawnMoment(level.server, id) }
            LegendaryCinematics.check(request)?.let { return it }
            begin(level, shrine, player, key, place, id, request)
            return null
        }
        val place = if (shrine != null) {
            place(level, shrine, player.position())
        } else {
            val origin = at ?: CinematicPlaces.testOrigin(level, player.eyePosition, player.y, player.yRot)
            place(level, BlockPos.containing(origin.x, origin.y + 0.01, origin.z), player.position())
        }
        val species = ResourceLocation.tryParse(LegendaryCinematics.config.species(scene)) ?: key.species
        val request = request(level, player, scene, place, SpeciesKey(species, key.aspects), true, if (gigas) "orbs=1" else testLetters(scene), null)
        return when (val result = LegendaryCinematics.start(request)) {
            is LegendaryCinematics.StartResult.Refused -> result.message
            is LegendaryCinematics.StartResult.Started -> null
        }
    }

    /** The records waiting for their Pokémon (a crash), as lines. */
    fun pending(server: MinecraftServer): List<String> =
        RegiData.of(server).pending.values.map { "${it.species} (${it.invoker}): ${it.dimension} ${it.pos.x} ${it.pos.y} ${it.pos.z}${if (it.id in playing) " (scene playing)" else ""}" }

    /** Forgets the waiting records whose scene is not playing (no Pokémon for them). How many. */
    fun clearPending(server: MinecraftServer): Int {
        val data = RegiData.of(server)
        val gone = data.pending.keys.filter { it !in playing }
        gone.forEach { data.pending.remove(it) }
        if (gone.isNotEmpty()) saveNow(server)
        return gone.size
    }

    private fun saveNow(server: MinecraftServer) {
        RegiData.of(server).setDirty()
        server.overworld().dataStorage.save()
    }

    // ---------------------------------------------------------------- saved

    /** A summoning whose Pokémon has not appeared yet. */
    class Pending(
        val id: UUID, val dimension: ResourceLocation, val pos: BlockPos, val yaw: Float,
        val species: String, val aspects: List<String>, val level: Int, val invoker: String,
    )

    class RegiData : SavedData() {
        val pending = LinkedHashMap<UUID, Pending>()

        override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
            val list = ListTag()
            for (p in pending.values) {
                val c = CompoundTag()
                c.putUUID("id", p.id)
                c.putString("dimension", p.dimension.toString())
                c.putInt("x", p.pos.x)
                c.putInt("y", p.pos.y)
                c.putInt("z", p.pos.z)
                c.putFloat("yaw", p.yaw)
                c.putString("species", p.species)
                val aspects = ListTag()
                p.aspects.forEach { aspects.add(net.minecraft.nbt.StringTag.valueOf(it)) }
                c.put("aspects", aspects)
                c.putInt("level", p.level)
                c.putString("invoker", p.invoker)
                list.add(c)
            }
            tag.put("pending", list)
            return tag
        }

        companion object {
            fun of(server: MinecraftServer): RegiData =
                server.overworld().dataStorage.computeIfAbsent(Factory(::RegiData, ::load, null), NAME)

            /** Without creating the file when there is none (the tick). */
            fun peek(server: MinecraftServer): RegiData? = server.overworld().dataStorage.get(Factory(::RegiData, ::load, null), NAME)

            private fun load(tag: CompoundTag, @Suppress("UNUSED_PARAMETER") registries: HolderLookup.Provider) = RegiData().apply {
                tag.getList("pending", Tag.TAG_COMPOUND.toInt()).forEach { entry ->
                    val c = entry as CompoundTag
                    if (!c.hasUUID("id")) return@forEach
                    val dimension = ResourceLocation.tryParse(c.getString("dimension")) ?: return@forEach
                    val aspects = c.getList("aspects", Tag.TAG_STRING.toInt()).map { it.asString }
                    val p = Pending(
                        c.getUUID("id"), dimension, BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")), c.getFloat("yaw"),
                        c.getString("species"), aspects, c.getInt("level").coerceAtLeast(1), c.getString("invoker"),
                    )
                    pending[p.id] = p
                }
            }
        }
    }
}
