package generations.gg.generations.core.generationscore.common.client.johto

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
import generations.gg.generations.core.generationscore.common.network.packets.johto.C2SJohtoTrialActionPacket
import generations.gg.generations.core.generationscore.common.network.packets.johto.S2CJohtoTrialPacket
import generations.gg.generations.core.generationscore.common.world.johto.JohtoBeast
import generations.gg.generations.core.generationscore.common.world.johto.JohtoTrialMath
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3d
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A trial on its player's client (JOHTO-PLAN.md §§ 6-7): what the server announces, drawn for
 * this player alone (D16) — Raikou's tracks, flashes and silhouettes; Entei's arena, attacks and
 * seal; Suicune's signs, orbs and rings —, the beast itself (CinematicActor, in the world's pass),
 * the clicks on Suicune's orbs and harmony, a discreet line in the action bar. The server decides
 * every outcome; nothing here changes the world.
 */
object JohtoClient {
    private class Attack(val kind: Int, val points: List<Vec3>, val warn: Long, val active: Long, val end: Long) {
        val flat = DoubleArray(points.size * 2).also { for ((i, p) in points.withIndex()) { it[i * 2] = p.x; it[i * 2 + 1] = p.z } }
    }

    private class Trial(val beast: JohtoBeast, val session: Int, val centre: Vec3, val radius: Double, val enduranceMax: Int) {
        var step = 0
        var round = 1
        var deadline = 0L
        var points: List<Vec3> = emptyList()
        var index = 0
        var health = enduranceMax
        var seal = 0
        var warn = -1
        var cue: S2CJohtoTrialPacket? = null
        val attacks = ArrayList<Attack>()
        var hint = 0
        var notes = 0
        var lastNote = -1.0
        var harmony: S2CJohtoTrialPacket? = null
        var hits = 0
        var roundStart = 0.0
        var messageUntil = 0.0
        val actor = CinematicActor(ResourceLocation.parse(beast.species), emptySet())
        var lastFlash = -10.0
        var played = HashSet<String>()
        var clicked = -1
        var clickedAt = -10.0
    }

    private var trial: Trial? = null
    private var lastFrame = Double.NaN
    private var hudTicks = 0
    private val tmp = Vector3d()
    private val points = FloatArray(3 * 64)

    private val yellow = FxMeshes.argb(1f, 0.92f, 0.35f, 1f)
    private val electric = FxMeshes.argb(0.55f, 0.75f, 1f, 1f)
    private val violet = FxMeshes.argb(0.62f, 0.38f, 1f, 1f)
    private val white = FxMeshes.argb(1f, 1f, 1f, 1f)
    private val flame = FxMeshes.argb(1f, 0.42f, 0.10f, 1f)
    private val gold = FxMeshes.argb(1f, 0.78f, 0.28f, 1f)
    private val red = FxMeshes.argb(1f, 0.15f, 0.08f, 1f)
    private val aqua = FxMeshes.argb(0.35f, 0.85f, 1f, 1f)
    private val murk = FxMeshes.argb(0.28f, 0.18f, 0.34f, 1f)
    private val notesColours = intArrayOf(
        FxMeshes.argb(0.35f, 0.85f, 1f, 1f), FxMeshes.argb(0.55f, 1f, 0.8f, 1f), FxMeshes.argb(0.75f, 0.6f, 1f, 1f),
        FxMeshes.argb(1f, 0.85f, 0.5f, 1f), FxMeshes.argb(0.9f, 0.95f, 1f, 1f),
    )
    private val notePitch = floatArrayOf(0.84f, 1.0f, 1.12f, 1.26f, 1.5f)

    fun init() {
        PlatformEvents.CLIENT_TICK_POST.subscribe(Priority.NORMAL) { tick() }
        PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { clear() }
    }

    private fun clear() {
        trial = null
        lastFrame = Double.NaN
        JohtoFx.clear()
    }

    /** The level's ticks now (with the frame's part), frozen while the solo game is paused. */
    private fun ticks(): Double {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return 0.0
        return level.gameTime + minecraft.timer.getGameTimeDeltaPartialTick(true).toDouble()
    }

    private fun sound(id: String, p: Vec3, volume: Float, pitch: Float) = CinematicAudio.play(id, p.x, p.y, p.z, volume, pitch)

    private fun bar(text: Component, seconds: Double = 2.5) {
        Minecraft.getInstance().gui.setOverlayMessage(text, false)
        trial?.messageUntil = ticks() + seconds * 20.0
    }

    private fun key(name: String, vararg args: Any) = Component.translatable("generations_core.johto.trial.$name", *args)

    // ---------------------------------------------------------------- packets

    fun trial(packet: S2CJohtoTrialPacket) {
        val beast = JohtoBeast.entries.getOrNull(packet.beast) ?: return
        if (packet.event == S2CJohtoTrialPacket.START) {
            JohtoFx.clear()
            trial = Trial(beast, packet.session, packet.origin, (packet.ints.firstOrNull() ?: 200) / 10.0, packet.value)
            return
        }
        val t = trial ?: return
        if (t.session != packet.session) return
        t.step = packet.step
        t.round = packet.round
        val now = ticks()
        when (packet.event) {
            S2CJohtoTrialPacket.ROUND -> {
                t.points = packet.points
                t.index = 0
                t.deadline = packet.tick
                t.cue = null
                t.harmony = null
                t.attacks.clear()
                t.notes = 0
                t.hint = 0
                t.seal = 0
                t.roundStart = now
                if (beast == JohtoBeast.ENTEI) t.health = packet.value
            }
            S2CJohtoTrialPacket.PROGRESS -> when (beast) {
                JohtoBeast.RAIKOU -> {
                    t.index = packet.index
                    t.points.getOrNull(packet.index - 1)?.let { JohtoFx.burst(it.x, it.y + 0.4, it.z, 30, 4f, yellow, seed = packet.index * 31) }
                }
                JohtoBeast.ENTEI -> t.seal = packet.index
                JohtoBeast.SUICUNE -> {
                    t.notes = packet.index
                }
            }
            S2CJohtoTrialPacket.CUE -> when (beast) {
                JohtoBeast.ENTEI -> {
                    t.attacks += Attack(packet.kind, packet.points, packet.tick, packet.tick2, packet.tick2 + (packet.ints.firstOrNull() ?: 12))
                }
                JohtoBeast.SUICUNE -> if (packet.kind == 2) t.harmony = packet else {
                    t.cue = packet
                    t.notes = 0
                    t.hint = packet.value
                    t.played.clear()
                }
                JohtoBeast.RAIKOU -> {
                    t.cue = packet
                    t.played.clear()
                }
            }
            S2CJohtoTrialPacket.HIT -> {
                t.health = packet.value
                Minecraft.getInstance().player?.let { JohtoFx.burst(it.x, it.y + 1.0, it.z, 26, 3f, flame, JohtoFx.EMBER, seed = packet.value) }
                bar(key("hit", packet.value.coerceAtLeast(0), t.enduranceMax).withStyle(ChatFormatting.RED), 1.5)
            }
            S2CJohtoTrialPacket.RESULT -> result(t, packet, now)
            S2CJohtoTrialPacket.WARN -> {
                t.warn = packet.value
                if (packet.value >= 0) bar(key("outside", packet.value).withStyle(ChatFormatting.RED), 1.2) else bar(key("back").withStyle(ChatFormatting.GREEN), 1.2)
            }
            S2CJohtoTrialPacket.END -> {
                val p = Minecraft.getInstance().player
                if (packet.value == 0 && p != null) {
                    val colour = (0xFF shl 24) or beast.rgb
                    JohtoFx.burst(p.x, p.y + 1.2, p.z, 90, 6f, colour, JohtoFx.MOTE, 2.2f, 0.25f, 77)
                }
                trial = null
            }
        }
    }

    private fun result(t: Trial, packet: S2CJohtoTrialPacket, now: Double) {
        val beastColour = (0xFF shl 24) or t.beast.rgb
        when (packet.kind) {
            S2CJohtoTrialPacket.RESULT_OK -> {
                t.cue?.points?.lastOrNull()?.let { JohtoFx.burst(it.x, it.y + 0.8, it.z, 60, 6f, beastColour, JohtoFx.SPARK, 1.4f, 0.18f, 41) }
                t.cue = null
                bar(key("round_won", packet.value).withStyle(ChatFormatting.GOLD))
            }
            S2CJohtoTrialPacket.RESULT_MISS -> {
                t.cue = null
                bar(key("missed", packet.value).withStyle(ChatFormatting.RED))
            }
            S2CJohtoTrialPacket.RESULT_FOUND -> {
                t.points.firstOrNull()?.let { JohtoFx.burst(it.x, it.y + 0.6, it.z, 50, 3f, aqua, JohtoFx.MOTE, 1.6f, 0.2f, 51) }
                bar(key("found").withStyle(ChatFormatting.AQUA))
            }
            S2CJohtoTrialPacket.RESULT_ERROR -> {
                t.hint = packet.index
                t.notes = 0
                t.cue = null
                bar(key("error", packet.value).withStyle(ChatFormatting.RED))
                Minecraft.getInstance().player?.let { sound("minecraft:block.note_block.didgeridoo", it.position(), 0.8f, 0.6f) }
            }
            S2CJohtoTrialPacket.RESULT_PURIFIED -> {
                t.points.firstOrNull()?.let { JohtoFx.burst(it.x, it.y + 1.0, it.z, 120, 5f, aqua, JohtoFx.MOTE, 2.0f, 0.22f, 61) }
                t.cue = null
                bar(key("purified", packet.value).withStyle(ChatFormatting.AQUA))
            }
            S2CJohtoTrialPacket.RESULT_HARMONY -> {
                t.hits = packet.value
                t.points.firstOrNull()?.let { JohtoFx.burst(it.x, it.y + 1.2, it.z, 50, 4f, white, JohtoFx.MOTE, 1.4f, 0.2f, 71 + packet.value) }
                bar(key("harmony", packet.value).withStyle(ChatFormatting.AQUA), 1.5)
            }
            S2CJohtoTrialPacket.RESULT_DISCORD -> bar(key("discord", packet.value).withStyle(ChatFormatting.RED), 1.5)
        }
    }

    // ---------------------------------------------------------------- clicks

    /** A click (Minecraft.startAttack / startUseItem): one of Suicune's orbs, or its harmony. True when it was ours. */
    @JvmStatic
    fun click(): Boolean {
        val t = trial ?: return false
        if (t.beast != JohtoBeast.SUICUNE || CinematicClient.isActive()) return false
        val minecraft = Minecraft.getInstance()
        val p = minecraft.player ?: return false
        val now = ticks()
        if (t.harmony != null) {
            p.swing(InteractionHand.MAIN_HAND)
            GenerationsNetwork.sendToServer(C2SJohtoTrialActionPacket(t.session, t.step, C2SJohtoTrialActionPacket.HARMONY))
            return true
        }
        val cue = t.cue ?: return false
        if (now < cue.tick2) return true
        val partial = minecraft.timer.getGameTimeDeltaPartialTick(true)
        val eye = p.getEyePosition(partial)
        val look = p.getViewVector(partial)
        var best = -1
        var bestT = 6.0
        for ((i, orb) in cue.points.withIndex()) {
            val cx = orb.x - eye.x
            val cy = orb.y - eye.y
            val cz = orb.z - eye.z
            val along = cx * look.x + cy * look.y + cz * look.z
            if (along <= 0.0 || along > bestT) continue
            val d2 = cx * cx + cy * cy + cz * cz - along * along
            if (d2 > 0.7 * 0.7) continue
            bestT = along
            best = i
        }
        if (best < 0) return false
        p.swing(InteractionHand.MAIN_HAND)
        t.clicked = best
        t.clickedAt = now
        sound("minecraft:block.note_block.chime", cue.points[best], 1f, notePitch[best % notePitch.size])
        GenerationsNetwork.sendToServer(C2SJohtoTrialActionPacket(t.session, t.step, best))
        return true
    }

    // ---------------------------------------------------------------- each tick and frame

    private fun tick() {
        val minecraft = Minecraft.getInstance()
        val t = trial ?: return
        if (minecraft.level == null) return clear()
        if (++hudTicks % 10 != 0) return
        val now = ticks()
        if (now < t.messageUntil || CinematicClient.isActive()) return
        val left = ((t.deadline - now) / 20.0).coerceAtLeast(0.0).toInt()
        val time = "%d:%02d".format(left / 60, left % 60)
        val line = when (t.beast) {
            JohtoBeast.RAIKOU -> if (t.cue != null) key("hud.raikou_chase", t.round, time) else key("hud.raikou", t.round, t.index, t.points.size, time)
            JohtoBeast.ENTEI -> key("hud.entei", t.round, t.health.coerceAtLeast(0), t.enduranceMax, t.seal / 10, time)
            JohtoBeast.SUICUNE -> when {
                t.harmony != null -> key("hud.suicune_harmony", t.hits, t.harmony?.ints?.firstOrNull() ?: 3, time)
                t.cue != null -> key("hud.suicune_notes", t.round, t.notes, t.cue?.ints?.size ?: 0, time)
                else -> key("hud.suicune_find", t.round, time, hintDistance(t))
            }
        }
        minecraft.gui.setOverlayMessage(line.withStyle(ChatFormatting.GOLD), false)
    }

    /** Suicune's signs grow with the time spent searching: after 2 min, the distance is told. */
    private fun hintDistance(t: Trial): String {
        val p = Minecraft.getInstance().player ?: return ""
        val s = t.points.firstOrNull() ?: return ""
        if (ticks() - t.roundStart < 2400.0) return ""
        val d = sqrt((s.x - p.x) * (s.x - p.x) + (s.z - p.z) * (s.z - p.z)).toInt()
        return " · ~${d}m"
    }

    /** In the world's pass (LevelRendererMixin): the beast. */
    @JvmStatic
    fun beforeRareCandy(frustum: Matrix4f, projection: Matrix4f) {
        val t = trial ?: return
        try {
            JohtoFx.keep(frustum, projection)
            if (CinematicClient.isActive()) return
            val p = Minecraft.getInstance().player ?: return
            val now = ticks()
            val seconds = (now / 20.0).toFloat()
            when (t.beast) {
                JohtoBeast.RAIKOU -> raikouPos(t, now, tmp)?.let { pos ->
                    t.actor.render(pos, yawTo(pos, p.position()), 0.0, 0.0, 1.0, ActorPose.STAND, seconds, bright = true)
                }
                JohtoBeast.ENTEI -> {
                    tmp.set(t.centre.x, t.centre.y, t.centre.z)
                    t.actor.render(tmp, yawTo(tmp, p.position()), 0.0, 0.0, 1.0, ActorPose.STAND, seconds, bright = true)
                }
                JohtoBeast.SUICUNE -> if (t.harmony != null) {
                    val s = t.points.firstOrNull() ?: return
                    tmp.set(s.x + 3.0, s.y, s.z + 3.0)
                    t.actor.render(tmp, yawTo(tmp, Vec3(s.x, s.y, s.z)), 0.0, 0.0, 1.0, ActorPose.STAND, seconds, bright = true)
                }
            }
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Legend of Johto: drawing the trial stopped", e)
            clear()
        }
    }

    private fun yawTo(from: Vector3d, to: Vec3): Double = Math.toDegrees(atan2(-(to.x - from.x), to.z - from.z))

    private fun yawTo(from: Vec3, to: Vec3): Double = Math.toDegrees(atan2(-(to.x - from.x), to.z - from.z))

    /** Right after the world's render (GameRendererMixin): the trial's marks, lights and particles. */
    @JvmStatic
    fun renderAfterLevel() {
        val t = trial
        if (t == null && JohtoFx.particles.count == 0) return
        try {
            val now = ticks()
            val dt = if (lastFrame.isNaN()) 0.0 else ((now - lastFrame) / 20.0).coerceIn(0.0, 0.25)
            lastFrame = now
            if (t != null) update(t, now, dt)
            JohtoFx.render(dt.toFloat()) { batches, cx, cy, cz -> if (t != null) draw(t, batches, cx, cy, cz, now) }
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("Legend of Johto: the trial's effects stopped", e)
            clear()
        }
    }

    // ---------------------------------------------------------------- Raikou

    /** Raikou's place now (the flashes' cue), or null when not shown. */
    private fun raikouPos(t: Trial, now: Double, out: Vector3d): Vector3d? {
        val cue = t.cue ?: return null
        if (cue.points.size < 3 || cue.ints.size < 4) return null
        val start = cue.tick.toDouble()
        for (k in 2 downTo 0) {
            if (now >= start + cue.ints[k]) {
                if (k == 2 && now > cue.tick2) return null
                val p = cue.points[k]
                return out.set(p.x, p.y, p.z)
            }
        }
        return null
    }

    private fun once(t: Trial, id: String, at: Double, now: Double): Boolean {
        if (now < at || id in t.played) return false
        t.played += id
        return now - at < 20.0
    }

    // ---------------------------------------------------------------- per frame: particles and sounds

    private fun update(t: Trial, now: Double, dt: Double) {
        val p = Minecraft.getInstance().player ?: return
        when (t.beast) {
            JohtoBeast.RAIKOU -> {
                // Sparks along the tracks still to follow.
                for (i in t.index until t.points.size) {
                    val a = t.points[i]
                    if (Math.random() < dt * (if (i == t.index) 18.0 else 4.0)) {
                        JohtoFx.mote(JohtoFx.SPARK, a.x + (Math.random() - 0.5) * 1.2, a.y + 0.1, a.z + (Math.random() - 0.5) * 1.2, if (i % 2 == 0) yellow else electric, vy = 1.4f, life = 0.6f, size = 0.1f)
                    }
                }
                val cue = t.cue
                if (cue != null && cue.ints.size >= 4) {
                    val start = cue.tick.toDouble()
                    val warn = cue.ints[3].toDouble()
                    for (k in 0..2) {
                        val at = start + cue.ints[k]
                        val q = cue.points.getOrNull(k) ?: continue
                        // The thunder rolls from where it will land; the flash, then Raikou.
                        if (once(t, "warn$k", at - warn, now)) sound("minecraft:entity.lightning_bolt.thunder", q, 0.9f, 1.4f + 0.1f * k)
                        if (once(t, "land$k", at, now)) {
                            sound("minecraft:entity.lightning_bolt.impact", q, 1.2f, 1.1f)
                            JohtoFx.burst(q.x, q.y + 0.6, q.z, 45, 7f, yellow, JohtoFx.SPARK, 0.8f, 0.12f, k * 13)
                            t.lastFlash = now
                            if (k == 2) sound("cobblemon:pokemon.raikou.cry", q, 1.3f, 1f)
                        }
                    }
                }
            }
            JohtoBeast.ENTEI -> {
                // Embers rising round the arena's edge.
                val n = (dt * 40.0).toInt() + if (Math.random() < (dt * 40.0) % 1.0) 1 else 0
                repeat(n) {
                    val a = Math.random() * 2.0 * PI
                    JohtoFx.mote(JohtoFx.EMBER, t.centre.x + cos(a) * t.radius, t.centre.y + 0.2, t.centre.z + sin(a) * t.radius, if (Math.random() < 0.5) flame else gold, vy = 1.6f, life = 1.6f, size = 0.12f)
                }
                val iterator = t.attacks.iterator()
                while (iterator.hasNext()) {
                    val a = iterator.next()
                    if (now > a.end + 10) {
                        iterator.remove()
                        continue
                    }
                    if (once(t, "atk${a.active}", a.active.toDouble(), now)) {
                        t.actor.animate("special")
                        val c = a.points.firstOrNull() ?: t.centre
                        sound(if (a.kind == JohtoTrialMath.WAVE) "minecraft:entity.generic.explode" else "minecraft:item.firecharge.use", c, 1f, 0.8f)
                    }
                    if (now in a.active.toDouble()..a.end.toDouble()) emitFire(t, a, now, dt)
                }
            }
            JohtoBeast.SUICUNE -> {
                val s = t.points.firstOrNull()
                if (s != null && t.cue == null && t.harmony == null) {
                    // The corrupted source's signs: a dark mist, glints, a drip from its place.
                    val d = sqrt((s.x - p.x) * (s.x - p.x) + (s.z - p.z) * (s.z - p.z))
                    val searching = (now - t.roundStart) / 20.0
                    val reach = 22.0 + 2.0 * searching.coerceAtMost(60.0)
                    if (d < reach) {
                        val rate = dt * 26.0 * (1.0 - d / reach).coerceIn(0.15, 1.0)
                        if (Math.random() < rate) JohtoFx.mote(JohtoFx.MIST, s.x + (Math.random() - 0.5) * 3.0, s.y + 0.2, s.z + (Math.random() - 0.5) * 3.0, murk, vy = 0.35f, life = 3.0f, size = 0.9f)
                        if (Math.random() < rate * 0.5) JohtoFx.mote(JohtoFx.MOTE, s.x + (Math.random() - 0.5) * 2.0, s.y + 0.1, s.z + (Math.random() - 0.5) * 2.0, aqua, vy = 0.1f, life = 1.0f, size = 0.14f)
                    }
                    val beat = Math.floor(now / 50.0)
                    if (once(t, "drip$beat", beat * 50.0, now)) sound("minecraft:block.pointed_dripstone.drip_water", s, 1.6f, 0.6f)
                }
                val cue = t.cue
                if (cue != null && t.harmony == null) {
                    // The sequence played back, note by note.
                    val each = if (t.hint >= 1) 18.0 else 13.0
                    for ((k, note) in cue.ints.withIndex()) {
                        val at = cue.tick + k * each
                        val orb = cue.points.getOrNull(note) ?: continue
                        if (once(t, "note$k", at, now)) {
                            sound("minecraft:block.note_block.chime", orb, 1.2f, notePitch[note % notePitch.size])
                            JohtoFx.burst(orb.x, orb.y, orb.z, 18, 1.5f, notesColours[note % notesColours.size], JohtoFx.MOTE, 0.8f, 0.14f, k * 7 + note)
                        }
                    }
                }
                val h = t.harmony
                if (h != null) {
                    val k = nextMeeting(h, now)
                    val at = JohtoTrialMath.harmonyTick(h.tick, k).toDouble()
                    if (once(t, "meet$k", at, now)) sound("minecraft:block.amethyst_block.chime", s ?: h.origin, 1.4f, 1.0f)
                }
            }
        }
    }

    private fun emitFire(t: Trial, a: Attack, now: Double, dt: Double) {
        val n = (dt * 60.0).toInt().coerceAtLeast(1)
        val y = t.centre.y + 0.1
        repeat(n) {
            when (a.kind) {
                JohtoTrialMath.LINE -> if (a.points.size >= 2) {
                    val u = Math.random()
                    val x = a.points[0].x + (a.points[1].x - a.points[0].x) * u
                    val z = a.points[0].z + (a.points[1].z - a.points[0].z) * u
                    JohtoFx.mote(JohtoFx.EMBER, x, y, z, if (Math.random() < 0.5) flame else gold, vy = 2.2f, life = 0.7f, size = 0.16f)
                }
                JohtoTrialMath.CIRCLES -> for (c in a.points) {
                    val ang = Math.random() * 2.0 * PI
                    val r = Math.random() * JohtoTrialMath.CIRCLE_RADIUS
                    JohtoFx.mote(JohtoFx.EMBER, c.x + cos(ang) * r, y, c.z + sin(ang) * r, flame, vy = 2.6f, life = 0.6f, size = 0.15f)
                }
                JohtoTrialMath.WAVE -> {
                    val r = JohtoTrialMath.waveRadius(a.active, now.toLong(), t.radius)
                    val c = a.points.firstOrNull() ?: t.centre
                    val ang = Math.random() * 2.0 * PI
                    if (gapAt(a, ang)) return@repeat
                    JohtoFx.mote(JohtoFx.EMBER, c.x + cos(ang) * r, y, c.z + sin(ang) * r, gold, vy = 1.6f, life = 0.5f, size = 0.18f)
                }
                else -> if (a.points.size >= 2) {
                    val aim = atan2(a.points[1].z - a.points[0].z, a.points[1].x - a.points[0].x)
                    val ang = aim + (Math.random() - 0.5) * 2.0 * JohtoTrialMath.CONE_HALF
                    val r = Math.random() * JohtoTrialMath.CONE_LENGTH
                    JohtoFx.mote(JohtoFx.EMBER, a.points[0].x + cos(ang) * r, y + 0.8, a.points[0].z + sin(ang) * r, flame, vx = (cos(ang) * 4).toFloat(), vy = 0.6f, vz = (sin(ang) * 4).toFloat(), life = 0.5f, size = 0.2f)
                }
            }
        }
    }

    private fun gapAt(a: Attack, angle: Double): Boolean {
        if (a.points.size < 2) return false
        val gap = atan2(a.points[1].z - a.points[0].z, a.points[1].x - a.points[0].x)
        return abs(JohtoTrialMath.angleDiff(angle, gap)) <= JohtoTrialMath.WAVE_GAP
    }

    private fun nextMeeting(h: S2CJohtoTrialPacket, now: Double): Int {
        var k = 0
        while (k < 200 && JohtoTrialMath.harmonyTick(h.tick, k) + 4 < now) k++
        return k
    }

    // ---------------------------------------------------------------- drawing

    private fun draw(t: Trial, batches: FxBatches, cx: Double, cy: Double, cz: Double, now: Double) {
        val f = (now / 20.0).toFloat()
        when (t.beast) {
            JohtoBeast.RAIKOU -> drawRaikou(t, batches, cx, cy, cz, now, f)
            JohtoBeast.ENTEI -> drawEntei(t, batches, cx, cy, cz, now, f)
            JohtoBeast.SUICUNE -> drawSuicune(t, batches, cx, cy, cz, now, f)
        }
    }

    private fun drawRaikou(t: Trial, batches: FxBatches, cx: Double, cy: Double, cz: Double, now: Double, f: Float) {
        val up = JohtoFx.up
        val right = JohtoFx.right
        // The tracks: paws of light on the ground, linked by a crackling trail; the next one, a column.
        for (i in t.index until t.points.size) {
            val a = t.points[i]
            val x = (a.x - cx).toFloat()
            val y = (a.y - cy).toFloat() + 0.06f
            val z = (a.z - cz).toFloat()
            val next = i == t.index
            val pulse = 0.75f + 0.25f * sin(f * 6f + i)
            FxMeshes.flat(batches.of(JohtoFx.marks), x, y, z, if (next) 1.6f else 0.9f, f * 0.6f + i, JohtoFx.a(yellow, (if (next) 0.85f else 0.45f) * pulse))
            if (next) {
                FxMeshes.beam(batches.of(JohtoFx.streaks), x, y, z, x, y + 7f, z, 0.45f, 0.05f, JohtoFx.a(yellow, 0.8f * pulse), JohtoFx.a(electric, 0f))
                FxMeshes.billboard(batches.of(JohtoFx.glowsThrough), x, y + 1f, z, 1.4f, 0f, JohtoFx.a(yellow, 0.35f), right, up)
            }
            if (i + 1 < t.points.size) {
                val b = t.points[i + 1]
                FxMeshes.bolt(batches.of(JohtoFx.bolts), x, y + 0.15f, z, (b.x - cx).toFloat(), (b.y - cy).toFloat() + 0.21f, (b.z - cz).toFloat(), (f * 12f).toInt() + i * 101, 0.5f, 0.05f, JohtoFx.a(electric, 0.4f))
            }
        }
        // The flashes: a silhouette where it will land, the bolt from the sky, the dash between.
        val cue = t.cue ?: return
        if (cue.ints.size < 4) return
        val start = cue.tick.toDouble()
        val warn = cue.ints[3].toDouble()
        for (k in 0..2) {
            val q = cue.points.getOrNull(k) ?: continue
            val at = start + cue.ints[k]
            val x = (q.x - cx).toFloat()
            val y = (q.y - cy).toFloat()
            val z = (q.z - cz).toFloat()
            if (now in (at - warn)..at) {
                val u = ((now - (at - warn)) / warn).toFloat()
                FxMeshes.billboard(batches.of(JohtoFx.glowsThrough), x, y + 1f, z, 1.2f + 1.6f * u, 0f, JohtoFx.a(violet, 0.25f + 0.5f * u), right, up)
                FxMeshes.ring(batches.of(JohtoFx.rings), x, y + 0.08f, z, 1.6f - 1.2f * u, 1.9f - 1.2f * u, 48, JohtoFx.a(yellow, 0.7f * u), JohtoFx.a(electric, 0.2f * u))
            }
            if (now in at - 3.0..at + 4.0) {
                FxMeshes.bolt(batches.of(JohtoFx.bolts), x, y + 30f, z, x, y, z, (now * 3.0).toInt() + k * 31, 2.6f, 0.22f, JohtoFx.a(white, 0.95f))
            }
            if (k > 0 && now in at - 2.0..at + 3.0) {
                val p0 = cue.points[k - 1]
                FxMeshes.beam(batches.of(JohtoFx.streaks), (p0.x - cx).toFloat(), (p0.y - cy).toFloat() + 0.9f, (p0.z - cz).toFloat(), x, y + 0.9f, z, 0.25f, 0.6f, JohtoFx.a(electric, 0.7f), JohtoFx.a(yellow, 0.9f))
            }
        }
        // The interception: a ring at its last point, closing as the window ends.
        val last = cue.points.getOrNull(2) ?: return
        val arrive = start + cue.ints[2]
        if (now >= arrive - warn && now <= cue.tick2) {
            val left = ((cue.tick2 - now) / (cue.tick2 - arrive + warn)).toFloat().coerceIn(0f, 1f)
            val x = (last.x - cx).toFloat()
            val y = (last.y - cy).toFloat() + 0.07f
            val z = (last.z - cz).toFloat()
            FxMeshes.ring(batches.of(JohtoFx.rings), x, y, z, 2.6f * left, 2.8f * left + 0.1f, 64, JohtoFx.a(yellow, 0.9f), JohtoFx.a(white, 0.6f))
            FxMeshes.billboard(batches.of(JohtoFx.glows), x, y + 1.2f, z, 2.2f, f, JohtoFx.a(yellow, 0.3f), right, up)
        }
    }

    private fun drawEntei(t: Trial, batches: FxBatches, cx: Double, cy: Double, cz: Double, now: Double, f: Float) {
        val ox = (t.centre.x - cx).toFloat()
        val oy = (t.centre.y - cy).toFloat() + 0.06f
        val oz = (t.centre.z - cz).toFloat()
        val r = t.radius.toFloat()
        // The arena's burning edge.
        FxMeshes.ring(batches.of(JohtoFx.fire), ox, oy, oz, r - 0.6f, r + 0.4f, 96, JohtoFx.a(flame, 0.7f), JohtoFx.a(gold, 0.05f), vScale = 10f, vOffset = f * 0.2f)
        FxMeshes.cylinder(batches.of(JohtoFx.fire), ox, oy, oz, 0f, 2.2f, r, r, 96, 2, JohtoFx.a(flame, 0.45f), JohtoFx.a(gold, 0f), uScale = 12f, vOffset = -f * 0.5f)
        // The seal: a sigil filling as it is held.
        t.points.firstOrNull()?.let { s ->
            val sx = (s.x - cx).toFloat()
            val sy = (s.y - cy).toFloat() + 0.08f
            val sz = (s.z - cz).toFloat()
            val k = t.seal / 1000f
            FxMeshes.flat(batches.of(JohtoFx.marks), sx, sy, sz, 2.2f, f * 0.8f, JohtoFx.a(gold, 0.5f + 0.4f * k))
            FxMeshes.ring(batches.of(JohtoFx.rings), sx, sy + 0.01f, sz, 1.9f, 2.25f, 64, JohtoFx.a(gold, 0.8f), JohtoFx.a(white, 0.6f))
            if (k > 0f) FxMeshes.ring(batches.of(JohtoFx.rings), sx, sy + 0.02f, sz, 0f, 1.9f * k, 48, JohtoFx.a(white, 0.5f), JohtoFx.a(gold, 0.3f))
            FxMeshes.beam(batches.of(JohtoFx.streaks), sx, sy, sz, sx, sy + 5f + 6f * k, sz, 0.35f, 0.02f, JohtoFx.a(gold, 0.6f), JohtoFx.a(gold, 0f))
        }
        // The attacks: their marks while announced, then the fire.
        for (a in t.attacks) {
            if (now > a.end + 4) continue
            val announced = now < a.active
            val u = if (announced) ((now - a.warn) / (a.active - a.warn).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) else 1f
            val pulse = if (announced) 0.55f + 0.45f * sin(f * 18f) else 1f
            val colour = if (announced) JohtoFx.a(red, (0.15f + 0.45f * u) * pulse) else JohtoFx.a(flame, 0.85f)
            when (a.kind) {
                JohtoTrialMath.LINE -> if (a.points.size >= 2) {
                    val ax = a.points[0].x
                    val az = a.points[0].z
                    val dx = a.points[1].x - ax
                    val dz = a.points[1].z - az
                    val len = sqrt(dx * dx + dz * dz)
                    val steps = (len / 1.1).toInt().coerceIn(1, 60)
                    for (i in 0..steps) {
                        val x = (ax + dx * i / steps - cx).toFloat()
                        val z = (az + dz * i / steps - cz).toFloat()
                        FxMeshes.flat(batches.of(JohtoFx.marks), x, oy + 0.02f, z, (JohtoTrialMath.LINE_WIDTH / 2.0).toFloat(), 0f, colour)
                    }
                }
                JohtoTrialMath.CIRCLES -> for (c in a.points) {
                    val x = (c.x - cx).toFloat()
                    val z = (c.z - cz).toFloat()
                    val rr = JohtoTrialMath.CIRCLE_RADIUS.toFloat()
                    FxMeshes.ring(batches.of(JohtoFx.rings), x, oy + 0.03f, z, 0f, rr * (if (announced) u else 1f), 48, colour, JohtoFx.a(colour, 0.2f))
                    FxMeshes.ring(batches.of(JohtoFx.rings), x, oy + 0.04f, z, rr - 0.15f, rr, 48, colour, colour)
                }
                JohtoTrialMath.WAVE -> {
                    val c = a.points.firstOrNull() ?: continue
                    val wr = if (announced) 1.2 + 0.6 * u else JohtoTrialMath.waveRadius(a.active, now.toLong(), t.radius)
                    val steps = 72
                    for (i in 0 until steps) {
                        val ang = i * 2.0 * PI / steps
                        if (gapAt(a, ang)) continue
                        val x = (c.x + cos(ang) * wr - cx).toFloat()
                        val z = (c.z + sin(ang) * wr - cz).toFloat()
                        FxMeshes.flat(batches.of(JohtoFx.marks), x, oy + 0.05f, z, (JohtoTrialMath.WAVE_THICKNESS / 2.0).toFloat(), ang.toFloat(), colour)
                    }
                    // The gap, shown cool while announced.
                    if (announced && a.points.size >= 2) {
                        val gap = atan2(a.points[1].z - c.z, a.points[1].x - c.x)
                        val x = (c.x + cos(gap) * t.radius * 0.6 - cx).toFloat()
                        val z = (c.z + sin(gap) * t.radius * 0.6 - cz).toFloat()
                        FxMeshes.beam(batches.of(JohtoFx.streaks), x, oy, z, x, oy + 3f, z, 0.4f, 0.05f, JohtoFx.a(aqua, 0.7f), JohtoFx.a(aqua, 0f))
                    }
                }
                else -> if (a.points.size >= 2) {
                    val aim = atan2(a.points[1].z - a.points[0].z, a.points[1].x - a.points[0].x)
                    var d = 1.5
                    while (d <= JohtoTrialMath.CONE_LENGTH) {
                        val half = d * kotlin.math.tan(JohtoTrialMath.CONE_HALF)
                        var s = -half
                        while (s <= half) {
                            val x = (a.points[0].x + cos(aim) * d - sin(aim) * s - cx).toFloat()
                            val z = (a.points[0].z + sin(aim) * d + cos(aim) * s - cz).toFloat()
                            FxMeshes.flat(batches.of(JohtoFx.marks), x, oy + 0.06f, z, 0.6f, 0f, colour)
                            s += 1.1
                        }
                        d += 1.1
                    }
                }
            }
        }
    }

    private fun drawSuicune(t: Trial, batches: FxBatches, cx: Double, cy: Double, cz: Double, now: Double, f: Float) {
        val up = JohtoFx.up
        val right = JohtoFx.right
        val s = t.points.firstOrNull() ?: return
        val x = (s.x - cx).toFloat()
        val y = (s.y - cy).toFloat()
        val z = (s.z - cz).toFloat()
        val searching = (now - t.roundStart) / 20.0
        if (t.cue == null && t.harmony == null) {
            // The corrupted reflections; a faint column after a minute of searching.
            FxMeshes.flat(batches.of(JohtoFx.marks), x, y + 0.05f, z, 1.8f + 0.2f * sin(f * 2f), f * 0.3f, JohtoFx.a(murk, 0.55f))
            FxMeshes.flat(batches.of(JohtoFx.marks), x, y + 0.06f, z, 1.1f, -f * 0.5f, JohtoFx.a(aqua, 0.18f + 0.12f * sin(f * 3f)))
            if (searching > 60.0) {
                val k = ((searching - 60.0) / 20.0).toFloat().coerceIn(0f, 1f)
                FxMeshes.beam(batches.of(JohtoFx.streaks), x, y, z, x, y + 18f, z, 0.5f, 0.1f, JohtoFx.a(aqua, 0.35f * k), JohtoFx.a(aqua, 0f))
                FxMeshes.billboard(batches.of(JohtoFx.glowsThrough), x, y + 1.5f, z, 2.5f, 0f, JohtoFx.a(aqua, 0.15f * k), right, up)
            }
            return
        }
        val cue = t.cue
        if (cue != null && t.harmony == null) {
            val each = if (t.hint >= 1) 18.0 else 13.0
            val playing = now < cue.tick2
            val expected = cue.ints.getOrNull(t.notes)
            for ((i, orb) in cue.points.withIndex()) {
                val ox = (orb.x - cx).toFloat()
                val oy = (orb.y - cy).toFloat()
                val oz = (orb.z - cz).toFloat()
                var lit = 0f
                if (playing) {
                    for ((k, note) in cue.ints.withIndex()) {
                        if (note != i) continue
                        val at = cue.tick + k * each
                        if (now in at..at + each * 0.8) lit = maxOf(lit, (1.0 - (now - at) / (each * 0.8)).toFloat())
                    }
                } else {
                    if (t.hint >= 2 && expected == i) lit = 0.35f + 0.25f * sin(f * 8f)
                    if (t.clicked == i && now - t.clickedAt < 8) lit = maxOf(lit, 1f - ((now - t.clickedAt) / 8.0).toFloat())
                }
                val colour = notesColours[i % notesColours.size]
                FxMeshes.billboard(batches.of(JohtoFx.glows), ox, oy, oz, 0.45f + 0.5f * lit, f + i, JohtoFx.a(colour, 0.55f + 0.45f * lit), right, up)
                FxMeshes.billboard(batches.of(JohtoFx.glows), ox, oy, oz, 0.18f, 0f, JohtoFx.a(white, 0.6f + 0.4f * lit), right, up)
                FxMeshes.beam(batches.of(JohtoFx.streaks), ox, oy - 1.2f, oz, ox, oy, oz, 0.04f, 0.08f, JohtoFx.a(colour, 0f), JohtoFx.a(colour, 0.4f))
            }
            // The notes found so far, as droplets of light rising from the source.
            for (k in 0 until t.notes) {
                val ang = k * 1.3f + f
                FxMeshes.billboard(batches.of(JohtoFx.glows), x + cos(ang) * 0.6f, y + 0.6f + k * 0.35f, z + sin(ang) * 0.6f, 0.2f, 0f, JohtoFx.a(aqua, 0.8f), right, up)
            }
            FxMeshes.flat(batches.of(JohtoFx.marks), x, y + 0.05f, z, 2.6f, f * 0.2f, JohtoFx.a(aqua, 0.25f))
            return
        }
        val h = t.harmony ?: return
        // Three rings closing on the source; a click when they meet.
        val k = nextMeeting(h, now)
        val at = JohtoTrialMath.harmonyTick(h.tick, k).toDouble()
        val speeds = floatArrayOf(0.10f, 0.16f, 0.22f)
        val colours = intArrayOf(aqua, white, FxMeshes.argb(0.6f, 0.5f, 1f, 1f))
        for (i in 0..2) {
            val rr = (0.7f + speeds[i] * (at - now).toFloat()).coerceIn(0.0f, 9f)
            FxMeshes.ring(batches.of(JohtoFx.rings), x, y + 0.08f + i * 0.02f, z, rr, rr + 0.22f, 72, JohtoFx.a(colours[i], 0.85f), JohtoFx.a(colours[i], 0.4f))
        }
        FxMeshes.ring(batches.of(JohtoFx.rings), x, y + 0.07f, z, 0.6f, 0.95f, 48, JohtoFx.a(white, 0.6f), JohtoFx.a(aqua, 0.3f))
        val near = (1.0 - abs(at - now) / 8.0).toFloat().coerceIn(0f, 1f)
        FxMeshes.billboard(batches.of(JohtoFx.glows), x, y + 1f, z, 1.2f + 2f * near, 0f, JohtoFx.a(aqua, 0.25f + 0.5f * near), right, up)
    }
}
