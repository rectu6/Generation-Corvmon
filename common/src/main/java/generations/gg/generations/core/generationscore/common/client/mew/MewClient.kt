package generations.gg.generations.core.generationscore.common.client.mew

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClient
import generations.gg.generations.core.generationscore.common.client.cinematic.actor.CinematicActor
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.CinematicAudio
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxBatches
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorPose
import generations.gg.generations.core.generationscore.common.network.GenerationsNetwork
import generations.gg.generations.core.generationscore.common.network.packets.mew.C2SMewInteractPacket
import generations.gg.generations.core.generationscore.common.network.packets.mew.S2CMewGamePacket
import generations.gg.generations.core.generationscore.common.network.packets.mew.S2CMewManifestPacket
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mew on this client (MEW-PLAN.md §§ 6 to 9): the false appearances, the opening and the return,
 * the three rounds of the game (its Mews, their tells and hints, the clicks), the discreet timer.
 * Everything is drawn without an entity (CinematicActor in the world's pass, MewFx after it) from
 * the server's few packets; the clock is the game's (a paused solo game stops it). Nothing here is
 * seen by another player (D10).
 */
object MewClient {
    private val MEW = ResourceLocation.fromNamespaceAndPath("cobblemon", "mew")

    private var config = MewClientConfig()
    private var configLoaded = false

    private class Running(val show: MewShow, val start: Double, val visual: Boolean) {
        val actor = CinematicActor(MEW, emptySet())
        val pose = MewPose()
        var next = 0
        val trail = ArrayDeque<Vector3d>()
        /** When each trail point was left (the show's own time): the trail fades in 1.5 s. */
        val trailTimes = ArrayDeque<Double>()
        var lastMote = 0.0
        /** The show's own time: it runs slower while Mew waits to be seen (HOOH-PLAN.md § 4). */
        var local = 0.0
        var seenFor = 0.0
        var seen = false
        var held = 0.0
    }

    private val shows = ArrayList<Running>()

    private class Game(val session: Int, var step: Int, val origin: Vector3d, val yaw: Double, val comeback: Boolean, start: Double) {
        var round = 0
        val points = ArrayList<Vector3d>()
        var index = 0
        var hint = 1.0
        var errors = 0
        var roundStart = start
        var deadline = start + 600.0
        val removed = HashSet<Int>()
        var lastClicked = -1
        var aimed = -1
        val actors = ArrayList<CinematicActor>()
        // Round 3: the flight from the last point.
        val from = Vector3d()
        var moveStart = -10.0
        var teleport = false
        var nextChime = 0.0
        var nextMote = 0.0
        var nextTrail = 0.0
        var nextPop = 0.0
        var popStart = -10.0
        var nextTaunt = 0.0
        var tauntStart = -10.0
        val pose = MewPose()

        fun actor(i: Int): CinematicActor {
            while (actors.size <= i) actors += CinematicActor(MEW, emptySet())
            return actors[i]
        }
    }

    /** The longest a show waits for the gaze, and how slowly it runs meanwhile. */
    private const val MAX_HOLD = 3.0
    private const val HOLD_SPEED = 0.3
    private const val TRAIL_SECONDS = 1.5

    private var game: Game? = null
    private var level: ClientLevel? = null
    private var lastFrame = Double.NaN
    private var hudTicks = 0

    private val player = Vector3d()
    private val tmp = Vector3d()
    private val rel = Vector3f()
    private val trailPoints = FloatArray(3 * 24)

    fun init() {
        PlatformEvents.CLIENT_TICK_POST.subscribe(Priority.NORMAL) { tick() }
        PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { clear() }
    }

    private fun clear() {
        shows.clear()
        game = null
        lastFrame = Double.NaN
        MewFx.clear()
    }

    private fun ensureConfig() {
        if (configLoaded) return
        configLoaded = true
        config = try {
            MewClientConfig.load()
        } catch (e: Exception) {
            MewClientConfig()
        }
    }

    /** The game's seconds now (ticks and the frame's part), frozen while the solo game is paused. */
    private fun now(): Double {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return 0.0
        return (level.gameTime + minecraft.timer.getGameTimeDeltaPartialTick(true)) / 20.0
    }

    private fun sound(id: String, x: Double, y: Double, z: Double, volume: Float, pitch: Float) =
        CinematicAudio.play(id, x, y, z, volume * config.volume, pitch)

    // ---------------------------------------------------------------- packets

    fun manifest(packet: S2CMewManifestPacket) {
        ensureConfig()
        val minecraft = Minecraft.getInstance()
        if (minecraft.level == null) return
        level = minecraft.level
        val origin = Vector3d(packet.origin.x, packet.origin.y, packet.origin.z)
        val second = Vector3d(packet.dx.toDouble(), packet.dy.toDouble(), packet.dz.toDouble())
        val show = MewShow.manifestation(packet.type, packet.seed, origin, packet.yaw.toDouble(), second, packet.seconds.toDouble())
        if (shows.size >= 4) shows.removeAt(0)
        shows += Running(show, now(), config.manifestationVisuals)
    }

    fun game(packet: S2CMewGamePacket) {
        configLoaded = false
        ensureConfig()
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        this.level = level
        val t = now()
        val g = game
        when (packet.event) {
            S2CMewGamePacket.OPEN, S2CMewGamePacket.RETURN -> {
                val comeback = packet.event == S2CMewGamePacket.RETURN
                val origin = Vector3d(packet.origin.x, packet.origin.y, packet.origin.z)
                game = Game(packet.session, packet.step, origin, packet.yaw.toDouble(), comeback, t).also { it.deadline = t + packet.ticksLeft / 20.0 }
                shows.clear()
                shows += Running(if (comeback) Comeback(origin, packet.yaw.toDouble()) else Opening(origin, packet.yaw.toDouble()), t, true)
                minecraft.gui.setTitle(Component.literal("Mew").withStyle(ChatFormatting.LIGHT_PURPLE))
                minecraft.gui.setSubtitle(Component.translatable(if (comeback) "generations_core.mew.game.return" else "generations_core.mew.game.open").withStyle(ChatFormatting.LIGHT_PURPLE))
            }
            S2CMewGamePacket.ROUND -> {
                if (g == null || g.session != packet.session) return
                // Round 2 shuffled: every copy goes up in smoke first.
                if (g.round == 2 && packet.round == 2) for ((i, p) in g.points.withIndex()) if (i !in g.removed) puff(p.x, p.y + 0.4, p.z, i)
                g.step = packet.step
                g.round = packet.round
                g.points.clear()
                packet.points.forEach { g.points += Vector3d(it.x, it.y, it.z) }
                g.index = packet.index
                if (packet.round != 2) g.hint = (packet.value / 100.0).coerceIn(0.1, 3.0) else g.errors = 0
                g.removed.clear()
                g.lastClicked = -1
                g.roundStart = t
                g.deadline = t + packet.ticksLeft / 20.0
                g.moveStart = -10.0
                g.nextChime = t + 2.0
                g.nextPop = t + 150.0 / g.hint
                g.nextTaunt = t + 3.0
                for ((i, p) in g.points.withIndex()) if (packet.round == 2 || i == g.index) appear(p.x, p.y + 0.4, p.z, i)
            }
            S2CMewGamePacket.ERROR -> {
                if (g == null || g.session != packet.session) return
                g.step = packet.step
                g.errors = packet.value
                val i = g.lastClicked
                if (i >= 0 && i < g.points.size && i != g.index) {
                    g.removed += i
                    val p = g.points[i]
                    puff(p.x, p.y + 0.4, p.z, i)
                    sound("minecraft:entity.allay.ambient_without_item", p.x, p.y + 0.5, p.z, 0.8f, 1.5f)
                }
            }
            S2CMewGamePacket.MOVE -> {
                if (g == null || g.session != packet.session) return
                g.step = packet.step
                currentRound3(g, t, g.from)
                g.index = packet.index.coerceIn(0, (g.points.size - 1).coerceAtLeast(0))
                if (packet.points.isNotEmpty() && g.index < g.points.size) g.points[g.index] = Vector3d(packet.points[0].x, packet.points[0].y, packet.points[0].z)
                g.moveStart = t
                g.teleport = MewShow.random(packet.step, 31) < 0.3
                if (g.teleport) {
                    sound("minecraft:entity.enderman.teleport", g.from.x, g.from.y, g.from.z, 0.4f, 1.7f)
                    MewFx.burst(g.from.x, g.from.y + 0.4, g.from.z, config.particles(14), 1.6f, MewFx.white, MewFx.SPARK, seed = packet.step)
                } else {
                    sound("minecraft:entity.phantom.flap", g.from.x, g.from.y, g.from.z, 0.5f, 1.9f)
                }
                g.nextTaunt = t + 2.5
            }
            S2CMewGamePacket.WARN -> {
                val p = minecraft.player ?: return
                sound("minecraft:block.note_block.pling", p.x, p.y + 1.0, p.z, 0.6f, 0.6f)
            }
            S2CMewGamePacket.END -> {
                if (g == null || g.session != packet.session) return
                val won = packet.value == 0
                val at = Vector3d()
                if (!gameMew(g, t, 0, at) && g.points.isNotEmpty()) at.set(g.points[g.index.coerceIn(0, g.points.size - 1)])
                if (won) {
                    MewFx.burst(at.x, at.y + 0.4, at.z, config.particles(30), 3f, MewFx.pink, seed = packet.session)
                    sound("minecraft:entity.allay.ambient_with_item", at.x, at.y + 0.5, at.z, 1f, 1.4f)
                } else {
                    puff(at.x, at.y + 0.4, at.z, 0)
                }
                game = null
                minecraft.gui.setOverlayMessage(Component.empty(), false)
            }
        }
    }

    private fun appear(x: Double, y: Double, z: Double, seed: Int) {
        MewFx.burst(x, y, z, config.particles(10), 1.4f, MewFx.pink, seed = seed * 7 + 1)
    }

    private fun puff(x: Double, y: Double, z: Double, seed: Int) {
        MewFx.burst(x, y, z, config.particles(8), 0.9f, FxMeshes.argb(1f, 0.75f, 0.9f, 0.6f), MewFx.PUFF, life = 1.0f, size = 0.35f, seed = seed * 13 + 3)
        MewFx.burst(x, y, z, config.particles(10), 1.8f, MewFx.pink, seed = seed * 13 + 5)
        sound("minecraft:entity.illusioner.mirror_move", x, y, z, 0.5f, 1.8f)
    }

    // ---------------------------------------------------------------- the game's Mews

    /** Round 3's Mew now: flying from its last point to the current one, or waiting on it. */
    private fun currentRound3(g: Game, t: Double, out: Vector3d): Vector3d {
        if (g.points.isEmpty()) return out.set(g.origin)
        val target = g.points[g.index.coerceIn(0, g.points.size - 1)]
        val u = ((t - g.moveStart) / 0.7).coerceIn(0.0, 1.0)
        if (u >= 1.0 || g.teleport) {
            return out.set(target.x, target.y + 0.12 * sin(t * 2.2), target.z)
        }
        val e = MewShow.smooth(u)
        return out.set(g.from).lerp(target, e).add(0.0, 0.8 * sin(PI * e), 0.0)
    }

    /** Where the game's Mew number i is drawn (feet), false when it is not shown. */
    private fun gameMew(g: Game, t: Double, i: Int, out: Vector3d): Boolean {
        if (g.round == 0 || g.points.isEmpty()) return false
        val rt = t - g.roundStart
        return when (g.round) {
            1 -> {
                if (i != 0) return false
                val p = g.points[0]
                val pop = if (t - g.popStart < 1.4) 0.6 * MewShow.smooth((t - g.popStart) / 0.3) * (1.0 - MewShow.smooth((t - g.popStart - 1.0) / 0.4)) else 0.0
                out.set(p.x, p.y + 0.15 + 0.05 * sin(rt * 1.7) + pop, p.z)
                true
            }
            2 -> {
                if (i >= g.points.size || i in g.removed) return false
                val p = g.points[i]
                val speed = if (i == g.index) 1.3 else 2.1 + 0.2 * i
                out.set(p.x, p.y + 0.1 * sin(t * speed + i), p.z)
                true
            }
            else -> {
                if (i != 0) return false
                currentRound3(g, t, out)
                true
            }
        }
    }

    // ---------------------------------------------------------------- clicks

    /** A click on one of the illusions (Minecraft.startAttack / startUseItem): true when it was ours. */
    @JvmStatic
    fun click(): Boolean {
        val g = game ?: return false
        if (g.round == 0) return false
        val minecraft = Minecraft.getInstance()
        val p = minecraft.player ?: return false
        val target = aim(g, now(), minecraft) ?: return false
        g.lastClicked = target
        p.swing(InteractionHand.MAIN_HAND)
        GenerationsNetwork.sendToServer(C2SMewInteractPacket(g.session, g.step, target))
        return true
    }

    /** The illusion under the crosshair within reach (its index as the server knows it), or null. */
    private fun aim(g: Game, t: Double, minecraft: Minecraft): Int? {
        val p = minecraft.player ?: return null
        val partial = minecraft.timer.getGameTimeDeltaPartialTick(true)
        val eye = p.getEyePosition(partial)
        val look = p.getViewVector(partial)
        var best: Int? = null
        var bestT = 4.5 + 0.8
        val count = if (g.round == 2) g.points.size else 1
        for (i in 0 until count) {
            if (!gameMew(g, t, i, tmp)) continue
            val index = when (g.round) {
                2 -> i
                3 -> if (g.index == g.points.size - 1) g.index else continue
                else -> 0
            }
            val cx = tmp.x - eye.x
            val cy = tmp.y + 0.45 - eye.y
            val cz = tmp.z - eye.z
            val along = cx * look.x + cy * look.y + cz * look.z
            if (along <= 0.0 || along > bestT) continue
            val d2 = cx * cx + cy * cy + cz * cz - along * along
            if (d2 > 0.8 * 0.8) continue
            bestT = along
            best = index
        }
        return best
    }

    // ---------------------------------------------------------------- each tick and frame

    private fun tick() {
        val minecraft = Minecraft.getInstance()
        val current = minecraft.level
        if (current !== level) {
            // Another world (or none): nothing of the last one stays.
            level = current
            clear()
            return
        }
        val g = game ?: return
        if (!config.hud || g.round == 0) return
        if (++hudTicks % 10 != 0) return
        val t = now()
        // The round's name and the server's warnings first.
        if (t - g.roundStart < 3.0) return
        val left = (g.deadline - t).coerceAtLeast(0.0).toInt()
        minecraft.gui.setOverlayMessage(Component.literal("✦ %d:%02d".format(left / 60, left % 60)).withStyle(ChatFormatting.LIGHT_PURPLE), false)
    }

    /** In the world's pass (LevelRendererMixin): the matrices kept, the Mews drawn. */
    @JvmStatic
    fun beforeRareCandy(frustum: Matrix4f, projection: Matrix4f) {
        if (shows.isEmpty() && game == null) return
        try {
            MewFx.keep(frustum, projection)
            if (CinematicClient.isActive()) return
            val minecraft = Minecraft.getInstance()
            val p = minecraft.player ?: return
            val t = now()
            player.set(p.x, p.y, p.z)
            for (r in shows) {
                if (!r.visual) continue
                val pose = r.show.pose(r.local.coerceIn(0.0, r.show.duration), player, r.pose)
                if (!pose.visible || !pose.isFinite() || pose.scale <= 0.01) continue
                val big = r.show.manifestation
                val scale = if (big) pose.scale * config.manifestationScale else pose.scale
                r.actor.render(pose.pos, pose.yaw, 0.0, pose.roll, scale, ActorPose.HOVER, r.local.toFloat(), bright = big)
            }
            val g = game ?: return
            val count = if (g.round == 2) g.points.size else 1
            for (i in 0 until count) {
                if (!gameMew(g, t, i, tmp)) continue
                var yaw = MewShow.yaw(tmp.x, tmp.z, player.x, player.z)
                var roll = 0.0
                var seconds = t.toFloat() + i * 0.37f
                var scale = 1.0
                when (g.round) {
                    1 -> {
                        yaw += 8.0 * sin(t * 1.3)
                        roll = 6.0 * sin(t * 0.9)
                    }
                    2 -> if (i == g.index) {
                        // The real one: slower, a different breath.
                        seconds = (t * 0.75 + 3.1).toFloat()
                        scale = 1.0 + 0.015 * sin(t * 1.3)
                    } else {
                        scale = 1.0 + 0.035 * sin(t * 2.4 + i)
                    }
                    else -> {
                        val spin = t - g.tauntStart
                        if (spin in 0.0..0.6) yaw += 360.0 * MewShow.smooth(spin / 0.6)
                    }
                }
                g.actor(i).render(tmp, yaw, 0.0, roll, scale, ActorPose.HOVER, seconds)
            }
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Mew: drawing stopped", e)
            clear()
        }
    }

    /** Right after the world's render (GameRendererMixin): events, hints, particles, halos and trails. */
    @JvmStatic
    fun renderAfterLevel() {
        if (shows.isEmpty() && game == null && MewFx.particles.count == 0) return
        try {
            val minecraft = Minecraft.getInstance()
            val p = minecraft.player ?: return
            val t = now()
            val dt = if (lastFrame.isNaN()) 0.0 else (t - lastFrame).coerceIn(0.0, 0.25)
            lastFrame = t
            player.set(p.x, p.y, p.z)
            updateShows(t, dt)
            game?.let { updateGame(it, t, minecraft) }
            MewFx.render(dt.toFloat()) { batches, cx, cy, cz -> drawExtras(batches, cx, cy, cz, t) }
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Mew: effects stopped", e)
            clear()
        }
    }

    /** Mew in the middle of the screen (about 20°), not too far: it has been noticed. */
    private fun looked(at: Vector3d): Boolean {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        val eye = camera.position
        val dx = at.x - eye.x
        val dy = at.y + 0.4 - eye.y
        val dz = at.z - eye.z
        val d = sqrt(dx * dx + dy * dy + dz * dz)
        if (d < 0.5 || d > 64.0) return d < 0.5
        val look = camera.lookVector
        return (dx * look.x() + dy * look.y() + dz * look.z()) / d > 0.94
    }

    private fun updateShows(t: Double, dt: Double) {
        val iterator = shows.iterator()
        while (iterator.hasNext()) {
            val r = iterator.next()
            // The show's clock: slowed while Mew waits for the player's gaze (3 s at most).
            val waiting = config.waitForGaze && r.visual && !r.seen && r.held < MAX_HOLD && r.show.holdable(r.local)
            val speed = if (waiting) HOLD_SPEED else 1.0
            r.held += dt * (1.0 - speed)
            r.local += dt * speed
            val local = r.local
            val pose = r.show.pose(local.coerceIn(0.0, r.show.duration), player, r.pose)
            if (!r.seen && pose.visible && pose.scale > 0.2 && looked(pose.pos)) {
                r.seenFor += dt
                if (r.seenFor >= 0.6) r.seen = true
            }
            val events = r.show.events
            while (r.next < events.size && events[r.next].time <= local) {
                event(r, events[r.next], pose)
                r.next++
            }
            // B's motes where the player turns; trails for C.
            val rate = r.show.motes(local)
            val at = r.show.motesAt
            if (rate > 0.0 && at != null && r.visual) {
                val n = config.particleIntensity * rate
                if (n > 0.0) while (r.lastMote + 1.0 / n <= local) {
                    r.lastMote += 1.0 / n
                    val k = (r.lastMote * 97).toInt()
                    val a = MewShow.random(k, 3) * 2.0 * PI
                    val d = 0.3 + 1.2 * MewShow.random(k, 5)
                    MewFx.mote(at.x + d * sin(a), at.y - 0.6 + 1.4 * MewShow.random(k, 7), at.z + d * kotlin.math.cos(a), if (k % 3 == 0) MewFx.blue else MewFx.pink, life = 2.2f)
                }
            }
            if (r.show.trail(local) && pose.visible) {
                val last = r.trail.lastOrNull()
                if (last == null || last.distanceSquared(pose.pos) > 0.04) {
                    r.trail.addLast(Vector3d(pose.pos))
                    r.trailTimes.addLast(local)
                    while (r.trail.size > 24) {
                        r.trail.removeFirst()
                        r.trailTimes.removeFirst()
                    }
                }
            }
            // The trail fades away in 1.5 s.
            while (r.trailTimes.isNotEmpty() && local - r.trailTimes.first() > TRAIL_SECONDS) {
                r.trail.removeFirst()
                r.trailTimes.removeFirst()
            }
            if (local > r.show.duration + 0.5 && r.trail.isEmpty()) iterator.remove()
        }
    }

    private fun event(r: Running, e: MewShowEvent, pose: MewPose) {
        val x = pose.pos.x
        val y = pose.pos.y + 0.4
        val z = pose.pos.z
        val visual = r.visual
        // The false appearances are louder (heard 24 to 32 blocks away, from their side).
        val loud = if (r.show.manifestation) 2.2f else 1f
        when (e.kind) {
            MewShowEvent.Kind.APPEAR -> {
                if (visual) {
                    MewFx.burst(x, y, z, config.particles(if (r.show.manifestation) 26 else 12), if (r.show.manifestation) 2.2f else 1.5f, MewFx.pink, seed = r.hashCode())
                    if (r.show.manifestation) MewFx.burst(x, y, z, config.particles(12), 1.6f, MewFx.blue, MewFx.SPARK, seed = r.hashCode() + 5)
                }
                sound("minecraft:entity.illusioner.mirror_move", x, y, z, 0.45f * loud, 1.5f)
                // Mew's own sign: a high chime and a giggle, the same each time.
                if (r.show.manifestation) {
                    sound("minecraft:block.amethyst_block.chime", x, y, z, 1.6f, 1.7f)
                    sound("minecraft:entity.allay.item_given", x, y, z, 1.2f, 1.6f)
                }
            }
            MewShowEvent.Kind.CRY -> {
                r.actor.cry()
                sound("cobblemon:pokemon.mew.cry", x, y, z, 0.7f * loud, 1.1f)
            }
            MewShowEvent.Kind.LAUGH -> {
                r.actor.animate("glad")
                sound("minecraft:entity.allay.ambient_without_item", x, y, z, 0.8f * loud, 1.4f)
            }
            MewShowEvent.Kind.PUFF -> if (visual) puff(x, y, z, r.hashCode()) else sound("minecraft:entity.illusioner.mirror_move", x, y, z, 0.5f * loud, 1.8f)
            MewShowEvent.Kind.TELEPORT -> {
                if (visual) {
                    MewFx.burst(x, y, z, config.particles(24), 2.6f, MewFx.white, MewFx.SPARK, seed = r.hashCode() + 9)
                    MewFx.burst(x, y, z, config.particles(16), 1.8f, MewFx.pink, seed = r.hashCode() + 13)
                }
                sound("minecraft:entity.enderman.teleport", x, y, z, 0.4f * loud, 1.7f)
            }
            MewShowEvent.Kind.DISSOLVE -> {
                if (visual) {
                    MewFx.burst(x, y, z, config.particles(36), 1.4f, MewFx.pink, life = 2.4f, size = 0.12f, seed = r.hashCode() + 31)
                    MewFx.burst(x, y, z, config.particles(20), 1.0f, MewFx.blue, life = 2.8f, size = 0.12f, seed = r.hashCode() + 37)
                }
                sound("minecraft:block.amethyst_block.resonate", x, y, z, 1.4f, 1.6f)
                sound("minecraft:entity.illusioner.mirror_move", x, y, z, 1.0f, 1.3f)
            }
            MewShowEvent.Kind.BURST -> {
                if (visual) {
                    MewFx.burst(x, y, z, config.particles(40), 3.5f, MewFx.pink, seed = r.hashCode() + 17)
                    MewFx.burst(x, y, z, config.particles(20), 2.5f, MewFx.blue, seed = r.hashCode() + 23)
                }
                sound("minecraft:block.amethyst_block.break", x, y, z, 0.9f, 1.4f)
                sound("minecraft:entity.illusioner.cast_spell", x, y, z, 0.6f, 1.5f)
            }
        }
    }

    /** The game's hints (MEW-PLAN.md § 8) and the aimed note of round 2. */
    private fun updateGame(g: Game, t: Double, minecraft: Minecraft) {
        if (g.round == 0 || g.points.isEmpty()) return
        val rt = t - g.roundStart
        when (g.round) {
            1 -> {
                val at = g.points[0]
                gameMew(g, t, 0, tmp)
                // A soft chime every 6 to 8 s where Mew hides.
                if (t >= g.nextChime) {
                    sound("minecraft:block.amethyst_block.chime", at.x, at.y + 0.5, at.z, 0.8f, 1.6f)
                    g.nextChime = t + 6.0 + 2.0 * MewShow.random((t * 10).toInt(), 41)
                }
                // Motes: discreet, more at 45 s.
                val rate = (if (rt * g.hint < 45.0) 1.2 else 4.0) * config.particleIntensity
                if (rate > 0.0 && t >= g.nextMote) {
                    g.nextMote = t + 1.0 / rate
                    val k = (t * 31).toInt()
                    val a = MewShow.random(k, 43) * 2.0 * PI
                    MewFx.mote(tmp.x + 0.5 * sin(a), tmp.y + 0.2 + 0.6 * MewShow.random(k, 47), tmp.z + 0.5 * kotlin.math.cos(a), MewFx.pink, life = 1.8f)
                }
                // At 90 s, a trail of motes from the player toward it.
                if (rt * g.hint >= 90.0 && t >= g.nextTrail) {
                    g.nextTrail = t + 0.35
                    for (k in 0 until 3) {
                        val f = 0.15 + 0.75 * MewShow.random((t * 13).toInt() + k, 53)
                        MewFx.mote(player.x + (at.x - player.x) * f, player.y + 1.0 + (at.y - player.y) * f, player.z + (at.z - player.z) * f, MewFx.pink, vy = 0.1f, life = 1.4f)
                    }
                }
                // At 150 s, it puts its head out and laughs, every 10 s.
                if (t >= g.nextPop) {
                    g.popStart = t
                    g.nextPop = t + 10.0
                    g.actor(0).animate("glad")
                    sound("minecraft:entity.allay.ambient_without_item", at.x, at.y + 0.5, at.z, 0.9f, 1.4f)
                }
            }
            2 -> {
                val aimed = aim(g, t, minecraft) ?: -1
                if (aimed != g.aimed) {
                    g.aimed = aimed
                    if (aimed >= 0) {
                        val p = g.points[aimed]
                        if (aimed == g.index) sound("minecraft:block.note_block.chime", p.x, p.y + 0.5, p.z, 0.6f, 1.5f)
                        else sound("minecraft:block.note_block.basedrum", p.x, p.y + 0.5, p.z, 0.6f, 0.7f)
                    }
                }
            }
            else -> {
                if (t >= g.nextTaunt && t - g.moveStart > 0.8) {
                    g.tauntStart = t
                    g.nextTaunt = t + 4.0
                    g.actor(0).animate("glad")
                    currentRound3(g, t, tmp)
                    sound("minecraft:entity.allay.ambient_without_item", tmp.x, tmp.y + 0.5, tmp.z, 0.7f, 1.5f)
                }
            }
        }
    }

    private fun drawExtras(batches: FxBatches, cx: Double, cy: Double, cz: Double, t: Double) {
        if (CinematicClient.isActive()) return
        val right = MewFx.right
        val up = MewFx.up
        for (r in shows) {
            if (!r.visual) continue
            val big = r.show.manifestation
            val size = if (big) config.manifestationScale else 1f
            // The trails (A, C, D): wider, fading in 1.5 s.
            if (r.trail.size >= 2) {
                var n = 0
                for (p in r.trail) {
                    if (n >= 24) break
                    trailPoints[n * 3] = (p.x - cx).toFloat()
                    trailPoints[n * 3 + 1] = (p.y + 0.35 * size - cy).toFloat()
                    trailPoints[n * 3 + 2] = (p.z - cz).toFloat()
                    n++
                }
                val age = r.local - (r.trailTimes.lastOrNull() ?: r.local)
                val fade = (1.0 - age / TRAIL_SECONDS).coerceIn(0.0, 1.0).toFloat()
                // Oldest first in the deque: the ribbon thins toward its end.
                FxMeshes.ribbon(batches.of(MewFx.trails), trailPoints, n, 0.01f, 0.26f * size, FxMeshes.withAlpha(MewFx.blue, 0f), FxMeshes.withAlpha(MewFx.pink, 0.85f * fade))
            }
            // The aura: a pink core and a blue halo round Mew, pulsing; a faint glow through the leaves.
            if (!big || !config.aura) continue
            val pose = r.pose
            if (!pose.visible || pose.scale <= 0.05 || !pose.isFinite()) continue
            val ax = (pose.pos.x - cx).toFloat()
            val ay = (pose.pos.y + 0.45 * size * pose.scale - cy).toFloat()
            val az = (pose.pos.z - cz).toFloat()
            val pulse = (0.85 + 0.15 * sin(r.local * 5.0)).toFloat()
            val s = (pose.scale * size).toFloat()
            FxMeshes.billboard(batches.of(MewFx.glows), ax, ay, az, 1.05f * s * pulse, 0f, FxMeshes.withAlpha(MewFx.pink, 0.55f), right, up)
            FxMeshes.billboard(batches.of(MewFx.glows), ax, ay, az, 2.1f * s, (r.local * 0.4).toFloat(), FxMeshes.withAlpha(MewFx.blue, 0.28f * pulse), right, up)
            if (config.glowThroughLeaves) FxMeshes.billboard(batches.of(MewFx.glowsThrough), ax, ay, az, 0.9f * s, 0f, FxMeshes.withAlpha(MewFx.pink, 0.16f * pulse), right, up)
        }
        val g = game ?: return
        when (g.round) {
            2 -> {
                // The real copy's faint halo, seen from close by; stronger after each error.
                if (gameMew(g, t, g.index, tmp)) {
                    val d = sqrt(tmp.distanceSquared(player.x, player.y + 1.0, player.z))
                    val near = 1.0 - MewShow.smooth((d - 6.0) / 2.0)
                    val alpha = ((0.12 + 0.18 * g.errors) * near).toFloat()
                    if (alpha > 0.01f) FxMeshes.billboard(batches.of(MewFx.glows), (tmp.x - cx).toFloat(), (tmp.y + 0.45 - cy).toFloat(), (tmp.z - cz).toFloat(), 0.9f, 0f, FxMeshes.withAlpha(MewFx.pink, alpha), right, up)
                }
            }
            3 -> {
                currentRound3(g, t, tmp)
                // A short trail while it flies; a glow on the last point (catch it there).
                if (!g.teleport && t - g.moveStart < 0.9) {
                    var n = 0
                    for (k in 0 until 12) {
                        val at = t - k * 0.04
                        if (at < g.moveStart) break
                        currentRound3(g, at, Vector3d()).let {
                            trailPoints[n * 3] = (it.x - cx).toFloat()
                            trailPoints[n * 3 + 1] = (it.y + 0.35 - cy).toFloat()
                            trailPoints[n * 3 + 2] = (it.z - cz).toFloat()
                        }
                        n++
                    }
                    if (n >= 2) FxMeshes.ribbon(batches.of(MewFx.trails), trailPoints, n, 0.14f, 0.01f, FxMeshes.withAlpha(MewFx.pink, 0.8f), FxMeshes.withAlpha(MewFx.blue, 0f))
                }
                if (g.index == g.points.size - 1) {
                    val pulse = (0.35 + 0.15 * sin(t * 4.0)).toFloat()
                    FxMeshes.billboard(batches.of(MewFx.glows), (tmp.x - cx).toFloat(), (tmp.y + 0.45 - cy).toFloat(), (tmp.z - cz).toFloat(), 1.1f, 0f, FxMeshes.withAlpha(MewFx.pink, pulse), right, up)
                }
            }
        }
    }
}
