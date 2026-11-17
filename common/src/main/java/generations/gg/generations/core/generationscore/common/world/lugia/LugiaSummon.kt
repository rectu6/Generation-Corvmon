package generations.gg.generations.core.generationscore.common.world.lugia

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.config.LegendKeys
import generations.gg.generations.core.generationscore.common.config.SpeciesKey
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicPlaces
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicSession
import generations.gg.generations.core.generationscore.common.world.cinematic.LegendaryCinematics
import generations.gg.generations.core.generationscore.common.world.entity.block.PokemonUtil
import generations.gg.generations.core.generationscore.common.world.johto.JohtoTrials
import generations.gg.generations.core.generationscore.common.world.level.block.shrines.BirdShrineBlock
import generations.gg.generations.core.generationscore.common.world.level.block.shrines.InteractShrineBlock
import generations.gg.generations.core.generationscore.common.world.level.block.shrines.LugiaShrineBlock
import generations.gg.generations.core.generationscore.common.world.mew.MewGame
import generations.gg.generations.core.generationscore.common.world.sound.GenerationsSounds
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lugia's shrine with its scene (LUGIA-PLAN.md). The summoning itself is the original one
 * (BirdShrineBlock.useItemOn: a full Melody Flute holding a Silver Wing; the flute taken unless
 * in creative; the shrine's song; Lugia level 70, wild, at the clicked block, facing as the
 * shrine). Only the moment Lugia appears changes: at the end of the scene instead of 5 s later.
 *
 * - Checked before anything is taken: when the scene cannot start, the original 5 s path runs.
 * - Lugia appears once, whatever happens: the scene's state machine (one spawn), and a record
 *   removed before it appears. Cut short (the invoker leaving, dying, /generations cinematic
 *   stop, the server stopping): at once. A crash: the record (SavedData generations_lugia,
 *   written when the scene starts) makes it appear when the shrine's chunk is loaded again,
 *   never loading a chunk itself.
 * - The shrine's "active" state does nothing for the birds' shrines (not InteractShrineBlocks):
 *   one flute, one Lugia, as before; it is still toggled like the original, in case it ever does.
 */
object LugiaSummon {
    private const val NAME = "generations_lugia"
    private const val KEY = "generations_core.cinematic.lugia"

    /** The very Lugia each summoning made, by record (the record makes a new one after a crash). */
    private val live = HashMap<UUID, Pokemon>()

    /** The records whose scene is playing: their session (the tick leaves them to it). */
    private val playing = HashMap<UUID, UUID>()

    private var ticks = 0L

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) {
            live.clear()
            playing.clear()
        }
        PlatformEvents.SERVER_TICK_POST.subscribe(Priority.NORMAL) { tick(it.server) }
    }

    // ---------------------------------------------------------------- the shrine

    /**
     * A summoning whose conditions are met (BirdShrineBlock.summon): the original steps, then
     * the scene. False: nothing was done, the original path runs (cinematics off, the invoker in
     * a battle or a journey, another scene nearby).
     */
    fun summon(shrine: BirdShrineBlock, level: ServerLevel, pos: BlockPos, state: BlockState, player: ServerPlayer, pokemon: Pokemon, stack: ItemStack): Boolean {
        if (!LegendaryCinematics.config.lugiaShrine) return false
        return begin(shrine, level, pos, state, player, pokemon) {
            // The original's own steps, in its order.
            InteractShrineBlock.toggleActive(level, pos)
            level.playSound(null, pos, GenerationsSounds.LUGIA_SHRINE_SONG.value(), SoundSource.BLOCKS)
            if (!player.isCreative) stack.shrink(1)
        } == null
    }

    /** The scene then Lugia; `take` runs once everything was checked. The refusal, or null. */
    private fun begin(shrine: BirdShrineBlock, level: ServerLevel, pos: BlockPos, state: BlockState, player: ServerPlayer, pokemon: Pokemon, take: () -> Unit): Component? {
        val place = place(level, pos, state, player.position())
        val id = UUID.randomUUID()
        val server = level.server
        val request = request(level, player, place, pokemon.species.resourceIdentifier, pokemon.aspects, false) { session -> spawnMoment(server, id, session) }
        LegendaryCinematics.check(request)?.let {
            GenerationsCore.LOGGER.info("Lugia's shrine at {}: no scene ({}), the original summoning", pos, it.string)
            return it
        }
        take()
        val yaw = state.getValue(HorizontalDirectionalBlock.FACING).toYRot()
        val data = LugiaData.of(server)
        data.pending[id] = Pending(id, level.dimension().location(), pos.immutable(), yaw, pokemon.species.resourceIdentifier.toString(), pokemon.forcedAspects.toList(), pokemon.level, player.gameProfile.name)
        live[id] = pokemon
        saveNow(server)
        when (val result = LegendaryCinematics.start(request)) {
            is LegendaryCinematics.StartResult.Started -> {
                playing[id] = result.session.id
                GenerationsCore.LOGGER.info("Lugia summoned by {} at {}: its scene plays ({})", player.gameProfile.name, pos, result.session.id)
            }
            // Cannot happen after check(): the original 5 s.
            is LegendaryCinematics.StartResult.Refused -> {
                data.pending.remove(id)
                live.remove(id)
                saveNow(server)
                shrine.scheduleSpawn(level, pos, state, pokemon)
            }
        }
        return null
    }

    /** The session's spawn moment (2 ticks before its end, or at once when cut short). */
    private fun spawnMoment(server: MinecraftServer, id: UUID, @Suppress("UNUSED_PARAMETER") session: CinematicSession) {
        playing.remove(id)
        appear(server, id)
    }

    /**
     * Lugia appears for that record, once: the record removed and written first (a crash then
     * leaves no Lugia rather than two). Not loaded: left for the tick.
     */
    private fun appear(server: MinecraftServer, id: UUID): Boolean {
        val data = LugiaData.of(server)
        val entry = data.pending[id] ?: run {
            live.remove(id)
            return false
        }
        val level = server.getLevel(ResourceKey.create(Registries.DIMENSION, entry.dimension)) ?: return false
        if (!level.isLoaded(entry.pos)) return false
        data.pending.remove(id)
        val pokemon = live.remove(id) ?: entry.recreate() ?: run {
            saveNow(server)
            GenerationsCore.LOGGER.error("Lugia's shrine at {}: unknown species {}", entry.pos, entry.species)
            return false
        }
        saveNow(server)
        try {
            // The original's appearance: the shrine toggled back, the Pokémon at the block.
            InteractShrineBlock.toggleActive(level, entry.pos)
            PokemonUtil.spawn(pokemon, level, entry.pos, entry.yaw)
            GenerationsCore.LOGGER.info("Lugia summoned by {} appeared at {}", entry.invoker, entry.pos)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Lugia's shrine at {}: Lugia could not appear", entry.pos, e)
        }
        return true
    }

    /** Every 2 s: the records left by a crash, once their chunk is loaded (by players, never by this). */
    private fun tick(server: MinecraftServer) {
        if (++ticks % 40L != 0L) return
        val data = LugiaData.peek(server) ?: return
        if (data.pending.isEmpty()) return
        // A scene gone without its spawn moment (should not happen): its record is recovered.
        playing.entries.removeIf { LegendaryCinematics.session(it.value) == null }
        for (id in data.pending.keys.toList()) if (id !in playing) appear(server, id)
    }

    // ---------------------------------------------------------------- the place

    /** The scene's frame: the shrine, the whirlpool (on water near it, else on the ground beyond it). */
    class Place(val origin: Vec3, val facing: Float, val spawn: Vec3, val vortex: Vec3, val water: Boolean, val spawnYaw: Float) {
        fun options(test: Boolean): String {
            val d = sqrt((vortex.x - origin.x) * (vortex.x - origin.x) + (vortex.z - origin.z) * (vortex.z - origin.z))
            var syaw = Mth.wrapDegrees(spawnYaw - facing)
            if (!syaw.isFinite()) syaw = 0f
            return "d=%.2f;vy=%.2f;water=%d;syaw=%.1f;test=%d".format(java.util.Locale.ROOT, d, vortex.y - origin.y, if (water) 1 else 0, syaw, if (test) 1 else 0)
        }
    }

    /** `viewer`: where the invoker stands (the whirlpool goes beyond the shrine from there). */
    fun place(level: ServerLevel, pos: BlockPos, state: BlockState?, viewer: Vec3): Place {
        val spawn = Vec3.atCenterOf(pos)
        val shrine = state?.block as? BirdShrineBlock
        val base = if (shrine != null && state != null) shrine.getBaseBlockPos(pos, state) else pos
        val origin = Vec3(pos.x + 0.5, minOf(base.y, pos.y).toDouble(), pos.z + 0.5)
        val spawnYaw = state?.takeIf { it.hasProperty(HorizontalDirectionalBlock.FACING) }?.getValue(HorizontalDirectionalBlock.FACING)?.toYRot() ?: CinematicPlaces.yawTowards(origin, viewer)
        val water = findWater(level, origin)
        val vortex = water ?: groundVortex(level, origin, viewer, spawnYaw)
        val facing = CinematicPlaces.yawTowards(vortex, origin)
        return Place(origin, facing, spawn, vortex, water != null, spawnYaw)
    }

    /**
     * Open water within 24 blocks (loaded chunks only, one search when the scene starts): the
     * surface of the widest stretch, 7 to 24 blocks away, or null.
     */
    private fun findWater(level: ServerLevel, origin: Vec3): Vec3? {
        val ox = Mth.floor(origin.x)
        val oy = Mth.floor(origin.y)
        val oz = Mth.floor(origin.z)
        val cells = ArrayList<IntArray>()
        val p = BlockPos.MutableBlockPos()
        for (dx in -24..24 step 2) for (dz in -24..24 step 2) {
            if (dx * dx + dz * dz > 24 * 24) continue
            val x = ox + dx
            val z = oz + dz
            if (!level.isLoaded(p.set(x, oy, z))) continue
            for (y in oy + 3 downTo oy - 12) {
                p.set(x, y, z)
                val block = level.getBlockState(p)
                if (block.isAir) continue
                if (level.getFluidState(p).`is`(FluidTags.WATER)) cells += intArrayOf(dx, dz, y)
                break
            }
        }
        if (cells.size < 12) return null
        var best: IntArray? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (c in cells) {
            val dist = sqrt((c[0] * c[0] + c[1] * c[1]).toDouble())
            if (dist < 7.0) continue
            var near = 0
            for (o in cells) if (abs(o[0] - c[0]) <= 6 && abs(o[1] - c[1]) <= 6 && abs(o[2] - c[2]) <= 1) near++
            val score = near - abs(dist - 13.0) * 0.5
            if (score > bestScore) {
                bestScore = score
                best = c
            }
        }
        val c = best ?: return null
        // An open stretch (7 × 7 cells of 2 blocks hold 49): at least a third of it.
        if (bestScore < 12.0) return null
        return Vec3(ox + c[0] + 0.5, c[2] + 0.9, oz + c[1] + 0.5)
    }

    /** No water: 12 blocks beyond the shrine from the viewer, on open ground (turning round it), or behind it. */
    private fun groundVortex(level: ServerLevel, origin: Vec3, viewer: Vec3, spawnYaw: Float): Vec3 {
        var dx = origin.x - viewer.x
        var dz = origin.z - viewer.z
        var len = sqrt(dx * dx + dz * dz)
        if (len < 0.5) {
            // The viewer on the shrine: behind it.
            val f = CinematicPlaces.forward(spawnYaw)
            dx = -f.x
            dz = -f.z
            len = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-3)
        }
        dx /= len
        dz /= len
        for (k in intArrayOf(0, 1, -1, 2, -2, 3, -3, 4)) {
            val angle = Math.toRadians(45.0 * k)
            val rx = dx * cos(angle) - dz * sin(angle)
            val rz = dx * sin(angle) + dz * cos(angle)
            val x = origin.x + rx * 12.0
            val z = origin.z + rz * 12.0
            val bx = Mth.floor(x)
            val bz = Mth.floor(z)
            if (!level.isLoaded(BlockPos(bx, Mth.floor(origin.y), bz))) continue
            val g = CinematicPlaces.ground(level, x, origin.y, z)
            if (abs(g - origin.y) > 5.0) continue
            // Open sky above it (no tree, no roof over the whirlpool).
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) > Mth.floor(g) + 1) continue
            return Vec3(x, g, z)
        }
        return Vec3(origin.x + dx * 10.0, origin.y, origin.z + dz * 10.0)
    }

    private fun request(
        level: ServerLevel, player: ServerPlayer, place: Place, species: ResourceLocation, aspects: Set<String>, test: Boolean,
        onSpawn: ((CinematicSession) -> Unit)?,
    ) = LegendaryCinematics.Request(
        level, CinematicScenes.LUGIA, place.origin, place.facing, place.spawn, player, species, aspects, test,
        onSpawn = onSpawn, exclusive = true, solo = true, ambientRadius = LegendaryCinematics.config.lugiaSpectatorRadius,
        ambientFilter = { !JohtoTrials.isRunning(it.uuid) && !MewGame.isPlaying(it.uuid) },
        options = place.options(test),
    )

    // ---------------------------------------------------------------- commands

    /** The Lugia shrine the player looks at (8 blocks), or null. */
    fun lookedAtShrine(player: ServerPlayer): BlockPos? {
        val hit = player.pick(8.0, 0f, false)
        if (hit.type != HitResult.Type.BLOCK) return null
        val pos = (hit as BlockHitResult).blockPos
        return pos.takeIf { player.serverLevel().getBlockState(it).block is LugiaShrineBlock }
    }

    /**
     * /generations cinematic lugia [real]: the scene at the shrine looked at (or 6 blocks ahead).
     * Test: nothing taken, nothing appears. Real (a shrine needed): the shrine's song, the scene
     * and a real Lugia level 70 at its end, as a summoning, with nothing taken. The refusal, or null.
     */
    fun admin(player: ServerPlayer, real: Boolean, at: Vec3? = null): Component? {
        val level = player.serverLevel()
        val pos = if (at == null) lookedAtShrine(player) else null
        if (real) {
            pos ?: return Component.translatable("$KEY.no_shrine")
            val state = level.getBlockState(pos)
            val shrine = state.block as BirdShrineBlock
            val pokemon = LegendKeys.LUGIA.createPokemon(70)
            return begin(shrine, level, pos, state, player, pokemon) {
                InteractShrineBlock.toggleActive(level, pos)
                level.playSound(null, pos, GenerationsSounds.LUGIA_SHRINE_SONG.value(), SoundSource.BLOCKS)
            }
        }
        val place = if (pos != null) {
            place(level, pos, level.getBlockState(pos), player.position())
        } else {
            val origin = at ?: CinematicPlaces.testOrigin(level, player.eyePosition, player.y, player.yRot)
            place(level, BlockPos.containing(origin.x, origin.y + 0.01, origin.z), null, player.position())
        }
        val species = ResourceLocation.tryParse(LegendaryCinematics.config.species(CinematicScenes.LUGIA)) ?: LegendKeys.LUGIA.species
        return when (val result = LegendaryCinematics.start(request(level, player, place, species, emptySet(), true, null))) {
            is LegendaryCinematics.StartResult.Refused -> result.message
            is LegendaryCinematics.StartResult.Started -> null
        }
    }

    /** The records waiting for their Lugia (a crash), as lines. */
    fun pending(server: MinecraftServer): List<String> =
        LugiaData.of(server).pending.values.map { "${it.invoker}: ${it.dimension} ${it.pos.x} ${it.pos.y} ${it.pos.z}${if (it.id in playing) " (scene playing)" else ""}" }

    /** Forgets the waiting records whose scene is not playing (no Lugia for them). How many. */
    fun clearPending(server: MinecraftServer): Int {
        val data = LugiaData.of(server)
        val gone = data.pending.keys.filter { it !in playing }
        gone.forEach {
            data.pending.remove(it)
            live.remove(it)
        }
        if (gone.isNotEmpty()) saveNow(server)
        return gone.size
    }

    private fun saveNow(server: MinecraftServer) {
        LugiaData.of(server).setDirty()
        server.overworld().dataStorage.save()
    }

    // ---------------------------------------------------------------- saved

    /** A summoning whose Lugia has not appeared yet. */
    class Pending(
        val id: UUID, val dimension: ResourceLocation, val pos: BlockPos, val yaw: Float,
        val species: String, val aspects: List<String>, val level: Int, val invoker: String,
    ) {
        /** The same Lugia as the shrine makes (SpeciesKey.createPokemon, as WingItem's key does). */
        fun recreate(): Pokemon? {
            val id = ResourceLocation.tryParse(species) ?: return null
            return SpeciesKey(id, aspects.toSet()).createPokemon(level)
        }
    }

    class LugiaData : SavedData() {
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
                p.aspects.forEach { aspects.add(StringTag.valueOf(it)) }
                c.put("aspects", aspects)
                c.putInt("level", p.level)
                c.putString("invoker", p.invoker)
                list.add(c)
            }
            tag.put("pending", list)
            return tag
        }

        companion object {
            fun of(server: MinecraftServer): LugiaData =
                server.overworld().dataStorage.computeIfAbsent(Factory(::LugiaData, ::load, null), NAME)

            /** Without creating the file when there is none (the tick). */
            fun peek(server: MinecraftServer): LugiaData? = server.overworld().dataStorage.get(Factory(::LugiaData, ::load, null), NAME)

            private fun load(tag: CompoundTag, @Suppress("UNUSED_PARAMETER") registries: HolderLookup.Provider) = LugiaData().apply {
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
