package generations.gg.generations.core.generationscore.common.client.johto

import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMath.random
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.CinematicTextures.Kind
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxBatches
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMaterial.Layer
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes.argb
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxMeshes.withAlpha
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FxParticles
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.PostParams
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorKey
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorLook
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.CinematicScene
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Cue
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.SceneFrame
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.SceneScript
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import net.minecraft.network.chat.Component
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The Legend of Johto's scenes, made from their start packet (CinematicClient.start). */
object JohtoScenes {
    fun create(scene: String, landing: Vector3d, title: Component, options: Map<String, String>): CinematicScene = when (scene) {
        CinematicScenes.JOHTO_HISTORY -> JohtoHistoryScene(options["height"]?.toDoubleOrNull() ?: 22.0, options["radius"]?.toDoubleOrNull() ?: 7.0)
        CinematicScenes.HO_OH_SUMMON -> HoOhSummonScene(landing, title)
        in CinematicScenes.AWAKENINGS -> JohtoAwakeningScene(
            CinematicScenes.ALL.indexOf(scene) - CinematicScenes.ALL.indexOf(CinematicScenes.RAIKOU_AWAKENING), title,
            options["material"] ?: "concrete", options["syaw"]?.toDoubleOrNull() ?: 0.0, options["scale"]?.toDoubleOrNull() ?: 1.0,
        )
        else -> JohtoEncounterScene(CinematicScenes.ALL.indexOf(scene) - CinematicScenes.ALL.indexOf(CinematicScenes.RAIKOU_ENCOUNTER), title)
    }
}

/** What the four scenes share: their materials, bursts, emitters and colours. */
abstract class JohtoSceneBase : CinematicScene {
    protected val glows = FxMaterial("johto_glows", Layer.ADD, Kind.HALO)
    protected val streaks = FxMaterial("johto_streaks", Layer.ADD, Kind.STREAK)
    protected val rings = FxMaterial("johto_rings", Layer.ADD, Kind.RING).noise(0f, 0.3f, 4f, 0.4f)
    protected val fire = FxMaterial("johto_fire", Layer.ADD, Kind.SMOKE).noise(0f, 0.7f, 3f, 0.5f).shape(0f, 0f, 0.4f, 1.3f)
    protected val bolts = FxMaterial("johto_bolts", Layer.ADD, Kind.WHITE)
    protected val ghostly = FxMaterial("johto_ghostly", Layer.ALPHA, Kind.NOISE).noise(0.05f, 0.1f, 2f, 0.3f).shape(2f, 0.6f, 0.3f, 1f)
    protected val smoke = FxMaterial("johto_smoke", Layer.ALPHA, Kind.SMOKE)

    private val particleMaterials = arrayOf(
        FxMaterial("johto_p_spark", Layer.ADD, Kind.SPARK),
        FxMaterial("johto_p_ember", Layer.ADD, Kind.EMBER),
        FxMaterial("johto_p_smoke", Layer.ALPHA, Kind.SMOKE),
        FxMaterial("johto_p_mote", Layer.ADD, Kind.HALO),
        FxMaterial("johto_p_rain", Layer.ALPHA, Kind.STREAK),
    )

    override fun particles(capacity: Int) = FxParticles(capacity, particleMaterials)

    protected val v3 = Vector3f()
    protected val w3 = Vector3f()
    protected val local = Vector3d()
    protected val world = Vector3d()
    protected val pts = FloatArray(3 * 64)
    protected val uv = FloatArray(2)

    protected val gold = argb(1f, 0.82f, 0.32f, 1f)
    protected val flame = argb(1f, 0.42f, 0.1f, 1f)
    protected val white = argb(1f, 0.97f, 0.9f, 1f)
    protected val ash = argb(0.62f, 0.6f, 0.58f, 1f)
    protected val storm = argb(0.16f, 0.17f, 0.22f, 1f)
    protected val beastColours = intArrayOf(argb(1f, 0.88f, 0.3f, 1f), argb(1f, 0.38f, 0.16f, 1f), argb(0.36f, 0.85f, 1f, 1f))
    protected val rainbowColours = intArrayOf(
        argb(1f, 0.22f, 0.18f, 1f), argb(1f, 0.58f, 0.12f, 1f), argb(0.95f, 0.9f, 0.25f, 1f),
        argb(0.3f, 0.95f, 0.35f, 1f), argb(0.25f, 0.65f, 1f, 1f), argb(0.35f, 0.35f, 1f, 1f), argb(0.7f, 0.35f, 1f, 1f),
    )

    companion object {
        const val SPARK = 0
        const val EMBER = 1
        const val SMOKE = 2
        const val MOTE = 3
        const val RAIN = 4
    }

    protected fun burst(frame: SceneFrame, x: Double, y: Double, z: Double, n: Int, speed: Double, salt: Int, colour: Int, material: Int, life: Float = 1.4f, size: Float = 0.2f, drag: Float = 1.6f, gravity: Float = 0f) {
        frame.camera.toWorld(x, y, z, world)
        for (i in 0 until frame.count(n)) {
            val a = random(i, salt) * 2.0 * PI
            val b = (random(i, salt + 1) - 0.4) * PI
            val sp = speed * (0.5 + random(i, salt + 2))
            frame.particles.emit(material, world.x, world.y, world.z, (cos(b) * sin(a) * sp).toFloat(), (sin(b) * sp).toFloat(), (cos(b) * cos(a) * sp).toFloat(), life, size, size * 0.2f, colour, drag = drag, gravity = gravity)
        }
    }

    /** n per second of action, from (x, y, z) local with a spread, while k > 0. */
    protected fun emit(frame: SceneFrame, rate: Double, k: Double, salt: Int, spawn: (Int) -> Unit) {
        if (k <= 0.02) return
        val n = frame.count((rate * k * frame.da * 1000.0).toInt()) / 1000.0
        var count = n.toInt()
        if (random((frame.a * 1000).toInt(), salt) < n - count) count++
        for (i in 0 until count) spawn((frame.a * 977).toInt() + i * 31 + salt)
    }

    protected fun drawRainbow(frame: SceneFrame, batches: FxBatches, k: Float, cx: Double, cy: Double, cz: Double, radius: Double) {
        if (k <= 0.01f) return
        val steps = 48
        for ((band, colour) in rainbowColours.withIndex()) {
            val r = radius - band * 2.4
            for (i in 0..steps) {
                val ang = PI - PI * i / steps
                frame.rel(cx + cos(ang) * r, cy + sin(ang) * r, cz, w3)
                pts[i * 3] = w3.x; pts[i * 3 + 1] = w3.y; pts[i * 3 + 2] = w3.z
            }
            FxMeshes.ribbon(batches.of(streaks), pts, steps + 1, 1.4f, 1.4f, withAlpha(colour, 0.45f * k), withAlpha(colour, 0.45f * k))
        }
    }

    protected fun halo(frame: SceneFrame, batches: FxBatches, x: Double, y: Double, z: Double, size: Float, colour: Int, angle: Float = 0f) {
        val c = frame.rel(x, y, z, v3)
        FxMeshes.billboard(batches.of(glows), c.x, c.y, c.z, size, angle, colour, frame.right, frame.up)
    }

    protected fun bolt(frame: SceneFrame, batches: FxBatches, ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double, seed: Int, spread: Float, width: Float, colour: Int) {
        val a = frame.rel(ax, ay, az, v3)
        val ax3 = a.x; val ay3 = a.y; val az3 = a.z
        val b = frame.rel(bx, by, bz, w3)
        FxMeshes.bolt(batches.of(bolts), ax3, ay3, az3, b.x, b.y, b.z, seed, spread, width, colour)
    }

    override fun music(stretch: Double, offset: Double): SynthRenderer? = JohtoMusic.music(id, script, stretch, offset)

    override fun ambience(stretch: Double, offset: Double): SynthRenderer? = JohtoMusic.ambience(id, script, stretch, offset)

    override fun post(frame: SceneFrame, out: PostParams) {}
}

/**
 * The history (JohtoHistoryScript), drawn: the ghost tower in tiers, golden then burning then
 * falling in embers; the three shapes of smoke and light (no species); the storm's clouds, the
 * lightning on the spire, the rain (and Minecraft's own rain on this client, D5); the rainbow,
 * Ho-Oh and its three spheres; the rebirth in yellow, red and blue; the flight.
 */
class JohtoHistoryScene(height: Double, radius: Double) : JohtoSceneBase() {
    override val id = CinematicScenes.JOHTO_HISTORY
    override val script = JohtoHistoryScript(height, radius)
    private val s = script
    override val title: Component = Component.translatable("generations_core.johto.history.title")
    override val subtitle: Component = Component.translatable("generations_core.johto.history.subtitle")
    private val tiers = 5

    override val extraActors: Int get() = 3
    override fun extraSpecies(i: Int): String = Trio.species[i]
    override fun extraActor(i: Int, a: Double, out: ActorKey): ActorKey = s.beast(i, a, out)
    override fun actorLook(a: Double, out: ActorLook): ActorLook = out.normal().also { it.bright = true }

    override fun rain(a: Double): Float = s.rainFall(a).toFloat()
    override fun thunder(a: Double): Float = (s.clouds(a) * 0.9).toFloat()

    override fun event(cue: Cue, frame: SceneFrame) {
        when (cue.id) {
            "strike" -> burst(frame, 0.0, s.height, 0.0, 70, 9.0, 901, white, SPARK, 1.0f, 0.2f)
            "collapse" -> burst(frame, 0.0, s.height * 0.5, 0.0, 120, 6.0, 911, flame, EMBER, 2.2f, 0.25f, gravity = 2f)
            "spheres" -> for (i in 0..2) {
                s.place(i, local)
                burst(frame, local.x, 6.0, local.z, 30, 3.0, 921 + i * 7, beastColours[i], MOTE, 1.6f, 0.3f)
            }
            "rebirth" -> for (i in 0..2) {
                s.place(i, local)
                burst(frame, local.x, 1.0, local.z, 80, 6.0, 941 + i * 9, beastColours[i], SPARK, 1.4f, 0.22f)
                burst(frame, local.x, 1.0, local.z, 40, 3.0, 961 + i * 9, white, MOTE, 1.8f, 0.35f)
            }
        }
    }

    override fun update(frame: SceneFrame) {
        val a = frame.a
        val p = frame.particles
        val h = s.height
        val r = s.radius
        // The fire's embers and smoke, rising from the tiers that burn.
        emit(frame, 90.0, s.burn(a), 1001) { i ->
            val ang = random(i, 1) * 2.0 * PI
            val y = h * random(i, 2) * (1.0 - 0.6 * s.collapse(a))
            val rr = r * (1.0 - y / h * 0.5) * (0.7 + 0.3 * random(i, 3))
            frame.camera.toWorld(cos(ang) * rr, y, sin(ang) * rr, world)
            if (i % 3 == 0) p.emit(SMOKE, world.x, world.y, world.z, 0f, 2.2f, 0f, 3.5f, 1.4f, 3.2f, withAlpha(storm, 0.55f), drag = 0.3f)
            else p.emit(EMBER, world.x, world.y, world.z, ((random(i, 4) - 0.5) * 1.5).toFloat(), (1.5 + 2.5 * random(i, 5)).toFloat(), ((random(i, 6) - 0.5) * 1.5).toFloat(), 1.8f, 0.18f, 0.05f, if (i % 2 == 0) flame else gold, drag = 0.4f)
        }
        // The rain, round the camera.
        emit(frame, 700.0, s.rainFall(a), 1011) { i ->
            val x = frame.cam.x + (random(i, 1) - 0.5) * 30.0
            val z = frame.cam.z + (random(i, 2) - 0.5) * 30.0
            p.emit(RAIN, x, frame.cam.y + 10.0 + 6.0 * random(i, 3), z, 0.4f, -22f, 0.2f, 0.9f, 0.05f, 0.05f, argb(0.7f, 0.78f, 0.9f, 0.45f))
        }
        // Steam as the rain puts the fire out.
        emit(frame, 30.0, s.rainFall(a) * s.burn(a + 4.0), 1021) { i ->
            val ang = random(i, 1) * 2.0 * PI
            frame.camera.toWorld(cos(ang) * r * random(i, 2), 0.3, sin(ang) * r * random(i, 2), world)
            p.emit(SMOKE, world.x, world.y, world.z, 0f, 1.2f, 0f, 3f, 0.8f, 2.4f, argb(0.85f, 0.86f, 0.9f, 0.35f), drag = 0.4f)
        }
        // The reborn beasts' auras.
        emit(frame, 50.0, s.auras(a), 1031) { i ->
            val k = i % 3
            s.beast(k, a, ActorKey()).pos.let { pos ->
                frame.camera.toWorld(pos.x + (random(i, 1) - 0.5), pos.y + 0.2 + random(i, 2) * 1.4, pos.z + (random(i, 3) - 0.5), world)
            }
            p.emit(MOTE, world.x, world.y, world.z, 0f, 0.8f, 0f, 1.2f, 0.16f, 0.04f, beastColours[k], drag = 0.6f)
        }
        p.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        drawTower(frame, batches, a)
        drawShapes(frame, batches, a)
        drawSky(frame, batches, a)
        drawRainbow(frame, batches, s.rainbow(a).toFloat(), 0.0, -8.0, -60.0, 64.0)
        drawSpheres(frame, batches, a)
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    /** The tower whole: tiers of golden ghostly walls with their roofs; on fire; falling. */
    private fun drawTower(frame: SceneFrame, batches: FxBatches, a: Double) {
        val whole = s.towerWhole(a).toFloat()
        if (whole <= 0.01f) return
        val burn = s.burn(a).toFloat()
        val fall = s.collapse(a).toFloat()
        val o = frame.rel(0.0, 0.0, 0.0, v3)
        val ox = o.x; val oy = o.y; val oz = o.z
        val tier = (s.height / tiers).toFloat()
        val base = withAlpha(FxMeshes.lerpColour(gold, flame, burn), 0.32f * whole)
        val top = withAlpha(FxMeshes.lerpColour(white, gold, burn), 0.06f * whole)
        for (k in 0 until tiers) {
            val y0 = k * tier
            // The upper tiers fall first, lower and fainter.
            val drop = fall * (1.2f + k * 0.8f) * tier
            val fade = (1f - fall * (0.5f + k * 0.15f)).coerceIn(0f, 1f)
            if (fade <= 0.02f) continue
            val rr = (s.radius * (1.0 - k * 0.12)).toFloat()
            val y = y0 - drop
            FxMeshes.cylinder(batches.of(ghostly), ox, oy + y, oz, 0f, tier * 0.78f, rr, rr * 0.96f, 4, 2, withAlpha(base, (base ushr 24) / 255f * fade), withAlpha(top, (top ushr 24) / 255f * fade), uScale = 2f, vScale = 1.5f)
            // Its roof: a flat cone over the tier.
            FxMeshes.cylinder(batches.of(ghostly), ox, oy + y + tier * 0.78f, oz, 0f, tier * 0.22f, rr * 1.25f, rr * 0.6f, 4, 1, withAlpha(base, 0.5f * whole * fade), withAlpha(top, 0.1f * whole * fade))
            if (burn > 0.02f) {
                FxMeshes.cylinder(batches.of(fire), ox, oy + y, oz, 0f, tier * 1.1f, rr * 1.05f, rr * 0.8f, 16, 2, withAlpha(flame, 0.55f * burn * fade), withAlpha(gold, 0f), uScale = 3f, vOffset = -a.toFloat() * 0.8f)
            }
        }
        // The spire.
        val spire = (1f - fall).coerceIn(0f, 1f)
        if (spire > 0.02f) FxMeshes.beam(batches.of(streaks), ox, oy + s.height.toFloat(), oz, ox, oy + s.height.toFloat() + 4f, oz, 0.35f, 0.02f, withAlpha(gold, 0.5f * whole * spire), withAlpha(gold, 0f))
    }

    /** Three shapes lying in the tower: smoke and a faint light, no species. */
    private fun drawShapes(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.shapes(a).toFloat()
        if (k <= 0.01f) return
        val dying = (1f - s.burn(a).toFloat() * 0.8f)
        for (i in 0..2) {
            s.place(i, local)
            val breath = 1f + 0.06f * sin((a * 1.6 + i).toFloat())
            for (part in 0 until 6) {
                val dx = (part - 2.5) * 0.32
                val dy = 0.55 + 0.18 * sin(part * 1.3)
                val c = frame.rel(local.x + dx * cos(Trio.angles[i] + 1.57), local.y + dy, local.z + dx * sin(Trio.angles[i] + 1.57), v3)
                FxMeshes.billboard(batches.of(smoke), c.x, c.y, c.z, 0.7f * breath, part.toFloat(), withAlpha(storm, 0.6f * k), frame.right, frame.up)
            }
            halo(frame, batches, local.x, local.y + 0.7, local.z, 1.3f * breath, withAlpha(white, 0.12f * k * dying))
        }
    }

    /** The storm's clouds over the tower and the lightning on the spire. */
    private fun drawSky(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.clouds(a).toFloat()
        if (k > 0.01f) for (i in 0 until 14) {
            val ang = i * 0.45 + a * 0.02
            val rr = 8.0 + (i % 4) * 7.0
            val c = frame.rel(cos(ang) * rr, s.height + 10.0 + (i % 3) * 3.0, sin(ang) * rr - 6.0, v3)
            FxMeshes.billboard(batches.of(smoke), c.x, c.y, c.z, 12f + (i % 3) * 4f, i * 0.7f, withAlpha(storm, 0.55f * k), frame.right, frame.up)
        }
        val strike = (1.0 - ((a - JohtoHistoryScript.STRIKE) / 0.35)).coerceIn(0.0, 1.0)
        if (a >= JohtoHistoryScript.STRIKE - 0.05 && strike > 0.0) {
            bolt(frame, batches, 4.0, s.height + 40.0, -6.0, 0.0, s.height + 0.5, 0.0, (a * 30.0).toInt(), 3f, 0.35f, withAlpha(white, strike.toFloat()))
            halo(frame, batches, 0.0, s.height + 1.0, 0.0, 6f * strike.toFloat(), withAlpha(white, 0.8f * strike.toFloat()))
        }
        // Lightning far off in the clouds.
        if (k > 0.3f && ((a * 1.7).toInt() % 5 == 0)) {
            val i = (a * 1.7).toInt()
            bolt(frame, batches, -20.0 + i % 7 * 6.0, s.height + 22.0, -30.0, -18.0 + i % 5 * 7.0, s.height + 8.0, -28.0, i * 17, 4f, 0.15f, withAlpha(white, 0.35f * k))
        }
    }

    /** The three spheres over the places, coming down into them. */
    private fun drawSpheres(frame: SceneFrame, batches: FxBatches, a: Double) {
        val k = s.spheres(a).toFloat()
        if (k <= 0.01f) return
        val down = ((a - 36.0) / (JohtoHistoryScript.REBIRTH - 36.0)).coerceIn(0.0, 1.0)
        for (i in 0..2) {
            s.place(i, local)
            val y = 6.0 - 5.0 * down * down
            val pulse = 1f + 0.12f * sin((a * 4.0 + i).toFloat())
            halo(frame, batches, local.x, y, local.z, 1.6f * pulse * k, withAlpha(beastColours[i], 0.85f * k))
            halo(frame, batches, local.x, y, local.z, 0.6f * k, withAlpha(white, 0.9f * k))
            // A thread of light to Ho-Oh.
            s.hoohPos(a, world)
            val c = frame.rel(local.x, y, local.z, v3)
            val cx = c.x; val cy = c.y; val cz = c.z
            val hh = frame.rel(world.x, world.y + 1.5, world.z, w3)
            FxMeshes.beam(batches.of(streaks), cx, cy, cz, hh.x, hh.y, hh.z, 0.08f, 0.2f, withAlpha(beastColours[i], 0.5f * k), withAlpha(gold, 0.3f * k))
        }
    }

    override fun post(frame: SceneFrame, out: PostParams) {
        val a = frame.a
        out.heat = (0.25 * s.burn(a)).toFloat()
        out.heatSpeed = 0.6f
        out.heatScale = 3f
        val k = s.spheres(a).toFloat()
        if (k > 0.05f && frame.project(frame.rel(0.0, 4.0, 0.0, v3), uv)) {
            out.raysX = uv[0]
            out.raysY = uv[1]
            out.rays = 0.4f * k
            out.raysDecay = 0.95f
        }
    }
}

/**
 * A statue's awakening (JohtoAwakeningScript), drawn: the cracks' light up the statue, the ashes
 * rising, the burst; then the beast's element — Raikou's lightning and arcs, Entei's golden flames
 * and heat wave, Suicune's mist and droplets — and its trail as it flees; the ashes forming the
 * statue again. The statue itself is the actor (its material) until the burst.
 */
class JohtoAwakeningScene(private val beast: Int, override val title: Component, private val material: String, syaw: Double, scale: Double) : JohtoSceneBase() {
    override val id = CinematicScenes.ALL[CinematicScenes.ALL.indexOf(CinematicScenes.RAIKOU_AWAKENING) + beast.coerceIn(0, 2)]
    override val script = JohtoAwakeningScript(beast.coerceIn(0, 2), syaw, scale.coerceIn(0.2, 4.0))
    private val s = script
    override val subtitle: Component = Component.translatable("generations_core.johto.awakening.subtitle.$beast")
    private val colour get() = beastColours[s.beast]

    override val extraActors: Int get() = 1
    override fun extraActor(i: Int, a: Double, out: ActorKey): ActorKey = s.reform(a, out)
    override fun actorLook(a: Double, out: ActorLook): ActorLook = if (a < JohtoAwakeningScript.BURST) out.statue(material) else out.normal()
    override fun extraLook(i: Int, a: Double, out: ActorLook): ActorLook = out.statue(material)

    override fun event(cue: Cue, frame: SceneFrame) {
        if (cue.id != "burst") return
        burst(frame, 0.0, 1.0, 0.0, 120, 5.0, 1101, ash, SMOKE, 2.5f, 0.6f, drag = 1.2f)
        burst(frame, 0.0, 1.0, 0.0, 90, 8.0, 1111, colour, SPARK, 1.2f, 0.18f)
        burst(frame, 0.0, 1.0, 0.0, 40, 3.0, 1121, white, MOTE, 1.4f, 0.4f)
    }

    override fun update(frame: SceneFrame) {
        val a = frame.a
        val p = frame.particles
        // Ashes rising from the statue.
        emit(frame, 60.0, s.ashes(a), 1201) { i ->
            frame.camera.toWorld((random(i, 1) - 0.5) * 1.4, 0.2 + random(i, 2) * 1.6, (random(i, 3) - 0.5) * 1.4, world)
            p.emit(if (i % 4 == 0) EMBER else SMOKE, world.x, world.y, world.z, 0f, (0.6 + random(i, 4)).toFloat(), 0f, 2.2f, if (i % 4 == 0) 0.08f else 0.25f, 0.5f, if (i % 4 == 0) colour else withAlpha(ash, 0.7f), drag = 0.4f, swirl = 0.6f)
        }
        // The beast's element round it and along its way.
        emit(frame, 70.0, s.element(a), 1211) { i ->
            s.beastPos(a, local)
            val x = local.x + (random(i, 1) - 0.5) * 1.6
            val y = local.y + 0.2 + random(i, 2) * 1.6
            val z = local.z + (random(i, 3) - 0.5) * 1.6
            frame.camera.toWorld(x, y, z, world)
            when (s.beast) {
                0 -> p.emit(SPARK, world.x, world.y, world.z, ((random(i, 4) - 0.5) * 4).toFloat(), ((random(i, 5) - 0.3) * 4).toFloat(), ((random(i, 6) - 0.5) * 4).toFloat(), 0.5f, 0.1f, 0.02f, if (i % 2 == 0) colour else white, drag = 2f)
                1 -> p.emit(EMBER, world.x, world.y, world.z, 0f, (1.5 + random(i, 4) * 2).toFloat(), 0f, 1.0f, 0.18f, 0.04f, if (i % 2 == 0) gold else flame, drag = 0.6f)
                else -> p.emit(if (i % 3 == 0) MOTE else SMOKE, world.x, world.y - 0.1, world.z, 0f, 0.3f, 0f, 2.0f, if (i % 3 == 0) 0.1f else 0.6f, 1.2f, if (i % 3 == 0) colour else argb(0.8f, 0.9f, 1f, 0.35f), drag = 0.5f)
            }
        }
        // The statue's ashes coming back to it.
        emit(frame, 80.0, s.reforming(a), 1221) { i ->
            val ang = random(i, 1) * 2.0 * PI
            val rr = 2.5 + random(i, 2) * 2.0
            frame.camera.toWorld(cos(ang) * rr, 0.3 + random(i, 3) * 1.8, sin(ang) * rr, world)
            p.emit(SMOKE, world.x, world.y, world.z, (-cos(ang) * rr * 0.9).toFloat(), 0f, (-sin(ang) * rr * 0.9).toFloat(), 1.1f, 0.3f, 0.05f, withAlpha(ash, 0.6f), drag = 0.2f)
        }
        p.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        val cr = s.cracks(a).toFloat()
        if (cr > 0.01f) {
            // Glowing cracks up the statue: thin bolts on its height.
            val o = frame.rel(0.0, 0.0, 0.0, v3)
            val ox = o.x; val oy = o.y; val oz = o.z
            val n = 3 + (cr * 7).toInt()
            for (i in 0 until n) {
                val ang = i * 2.4f
                val y0 = 0.2f + (i % 4) * 0.35f
                FxMeshes.bolt(batches.of(bolts), ox + cos(ang) * 0.45f, oy + y0, oz + sin(ang) * 0.45f, ox + cos(ang + 0.6f) * 0.5f, oy + y0 + 0.6f + cr * 0.4f, oz + sin(ang + 0.6f) * 0.5f, i * 31 + (a * 4).toInt(), 0.12f, 0.02f + 0.03f * cr, withAlpha(colour, 0.5f + 0.5f * cr))
            }
            halo(frame, batches, 0.0, 1.0, 0.0, 1.2f + 2.4f * cr, withAlpha(colour, 0.25f * cr))
        }
        val e = s.element(a).toFloat()
        if (e > 0.01f) {
            s.beastPos(a, local)
            when (s.beast) {
                0 -> {
                    // Arcs round Raikou, a bolt from the sky at the burst.
                    for (i in 0 until 3) bolt(frame, batches, local.x, local.y + 1.0, local.z, local.x + cos(a * 9.0 + i * 2.1) * 1.6, local.y + 0.2 + i * 0.5, local.z + sin(a * 9.0 + i * 2.1) * 1.6, (a * 20).toInt() + i * 7, 0.4f, 0.04f, withAlpha(white, 0.8f * e))
                    val strike = (1.0 - (a - JohtoAwakeningScript.BURST) / 0.4).coerceIn(0.0, 1.0)
                    if (strike > 0.0) bolt(frame, batches, 2.0, 30.0, -4.0, 0.0, 0.5, 0.0, (a * 30).toInt(), 2.5f, 0.3f, withAlpha(white, strike.toFloat()))
                }
                1 -> {
                    val o = frame.rel(local.x, local.y, local.z, v3)
                    FxMeshes.cylinder(batches.of(fire), o.x, o.y, o.z, 0f, 2.4f, 1.2f, 0.3f, 24, 2, withAlpha(gold, 0.6f * e), withAlpha(flame, 0f), uScale = 3f, vOffset = -a.toFloat())
                    val wave = ((a - JohtoAwakeningScript.BURST) / 1.2).coerceIn(0.0, 1.0).toFloat()
                    if (wave < 1f) FxMeshes.ring(batches.of(rings), o.x, o.y + 0.1f, o.z, 12f * wave, 12f * wave + 1.2f, 96, withAlpha(gold, 0.7f * (1f - wave)), withAlpha(flame, 0.4f * (1f - wave)), vScale = 10f)
                }
                else -> {
                    val o = frame.rel(local.x, local.y, local.z, v3)
                    FxMeshes.ring(batches.of(rings), o.x, o.y + 0.05f, o.z, 0.8f, 2.6f, 64, withAlpha(colour, 0.35f * e), withAlpha(white, 0f), vScale = 6f, vOffset = a.toFloat() * 0.3f)
                    // Its aurora ribbon.
                    for (i in 0 until 20) {
                        val at = a - i * 0.06
                        s.beastPos(at, world)
                        val c = frame.rel(world.x, world.y + 1.3 + 0.2 * sin(at * 4.0), world.z, w3)
                        pts[i * 3] = c.x; pts[i * 3 + 1] = c.y; pts[i * 3 + 2] = c.z
                    }
                    FxMeshes.ribbon(batches.of(streaks), pts, 20, 0.35f, 0.02f, withAlpha(colour, 0.7f * e), withAlpha(white, 0f))
                }
            }
            if (a > JohtoAwakeningScript.FLEE && s.beast == 0) {
                for (i in 0 until 16) {
                    s.beastPos(a - i * 0.05, world)
                    val c = frame.rel(world.x, world.y + 0.9, world.z, w3)
                    pts[i * 3] = c.x; pts[i * 3 + 1] = c.y; pts[i * 3 + 2] = c.z
                }
                FxMeshes.ribbon(batches.of(streaks), pts, 16, 0.3f, 0.02f, withAlpha(colour, 0.8f * e), withAlpha(white, 0f))
            }
        }
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    override fun post(frame: SceneFrame, out: PostParams) {
        val a = frame.a
        if (s.beast == 1) {
            out.heat = (0.3 * s.element(a)).toFloat()
            out.heatSpeed = 0.7f
            out.heatScale = 3f
        }
        val cr = s.cracks(a).toFloat()
        if (cr > 0.05f && frame.project(frame.rel(0.0, 1.0, 0.0, v3), uv)) {
            out.raysX = uv[0]
            out.raysY = uv[1]
            out.rays = 0.35f * cr
            out.raysDecay = 0.94f
        }
    }
}

/**
 * A beast's encounter (JohtoEncounterScript), drawn: its omen (gathering storm, glowing ground,
 * rolling mist), its run, the burst of its landing, its aura while it looks at the player.
 */
class JohtoEncounterScene(private val beast: Int, override val title: Component) : JohtoSceneBase() {
    override val id = CinematicScenes.ALL[CinematicScenes.ALL.indexOf(CinematicScenes.RAIKOU_ENCOUNTER) + beast.coerceIn(0, 2)]
    override val script = JohtoEncounterScript(beast.coerceIn(0, 2))
    private val s = script
    override val subtitle: Component = Component.translatable("generations_core.johto.encounter.subtitle.$beast")
    private val colour get() = beastColours[s.beast]

    override fun event(cue: Cue, frame: SceneFrame) {
        if (cue.id != "land") return
        when (s.beast) {
            0 -> burst(frame, 0.0, 1.0, 0.0, 120, 9.0, 1301, colour, SPARK, 1.0f, 0.16f)
            1 -> burst(frame, 0.0, 0.5, 0.0, 140, 7.0, 1311, gold, EMBER, 1.8f, 0.24f, gravity = 1f)
            else -> burst(frame, 0.0, 0.5, 0.0, 120, 5.0, 1321, argb(0.8f, 0.92f, 1f, 0.6f), MOTE, 1.8f, 0.3f, gravity = 3f)
        }
        burst(frame, 0.0, 1.0, 0.0, 40, 3.0, 1331, white, MOTE, 1.4f, 0.45f)
    }

    override fun update(frame: SceneFrame) {
        val a = frame.a
        val p = frame.particles
        emit(frame, 60.0, s.omen(a), 1401) { i ->
            val ang = random(i, 1) * 2.0 * PI
            val rr = 1.0 + random(i, 2) * 6.0
            frame.camera.toWorld(cos(ang) * rr, 0.1 + random(i, 3) * (if (s.beast == 0) 6.0 else 1.0), sin(ang) * rr, world)
            when (s.beast) {
                0 -> p.emit(SPARK, world.x, world.y, world.z, 0f, -1f, 0f, 0.6f, 0.08f, 0.02f, if (i % 2 == 0) colour else white, drag = 1f)
                1 -> p.emit(EMBER, world.x, world.y, world.z, 0f, 1.6f, 0f, 1.4f, 0.14f, 0.04f, if (i % 2 == 0) flame else gold, drag = 0.5f)
                else -> p.emit(SMOKE, world.x, world.y, world.z, (-sin(ang) * 0.8).toFloat(), 0.1f, (cos(ang) * 0.8).toFloat(), 3f, 0.7f, 1.8f, argb(0.82f, 0.9f, 1f, 0.35f), drag = 0.2f)
            }
        }
        emit(frame, 40.0, s.aura(a), 1411) { i ->
            frame.camera.toWorld((random(i, 1) - 0.5) * 1.8, 0.2 + random(i, 2) * 1.8, (random(i, 3) - 0.5) * 1.8, world)
            p.emit(MOTE, world.x, world.y, world.z, 0f, 0.6f, 0f, 1.3f, 0.14f, 0.04f, colour, drag = 0.6f)
        }
        p.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        val omen = s.omen(a).toFloat()
        val o = frame.rel(0.0, 0.0, 0.0, v3)
        val ox = o.x; val oy = o.y; val oz = o.z
        if (omen > 0.01f) when (s.beast) {
            0 -> {
                for (i in 0 until 10) {
                    val ang = i * 0.63 + a * 0.05
                    val c = frame.rel(cos(ang) * (6.0 + i % 3 * 5.0), 16.0 + (i % 2) * 4.0, sin(ang) * (6.0 + i % 3 * 5.0), w3)
                    FxMeshes.billboard(batches.of(smoke), c.x, c.y, c.z, 9f, i.toFloat(), withAlpha(storm, 0.5f * omen), frame.right, frame.up)
                }
                if ((a * 2.3).toInt() % 3 == 0) bolt(frame, batches, -6.0 + (a * 7).toInt() % 12, 18.0, -4.0, -4.0 + (a * 5).toInt() % 9, 0.2, -2.0, (a * 13).toInt(), 2f, 0.12f, withAlpha(white, 0.5f * omen))
            }
            1 -> FxMeshes.ring(batches.of(rings), ox, oy + 0.05f, oz, 0f, 3.5f * omen, 64, withAlpha(flame, 0.5f * omen), withAlpha(gold, 0.1f * omen))
            else -> FxMeshes.ring(batches.of(rings), ox, oy + 0.05f, oz, 1f + (a % 1.0).toFloat() * 4f, 1.3f + (a % 1.0).toFloat() * 4f, 64, withAlpha(colour, 0.5f * omen * (1f - (a % 1.0).toFloat())), withAlpha(white, 0f))
        }
        val land = (1.0 - (a - JohtoEncounterScript.LAND) / 0.6).coerceIn(0.0, 1.0).toFloat()
        if (a >= JohtoEncounterScript.LAND && land > 0f) {
            when (s.beast) {
                0 -> bolt(frame, batches, 1.0, 34.0, -3.0, 0.0, 0.3, 0.0, (a * 40).toInt(), 2.6f, 0.35f, withAlpha(white, land))
                1 -> FxMeshes.cylinder(batches.of(fire), ox, oy, oz, 0f, 9f * (1.2f - land), 1.8f, 0.4f, 24, 3, withAlpha(gold, 0.8f * land), withAlpha(flame, 0f), uScale = 3f, vOffset = -a.toFloat())
                else -> FxMeshes.ring(batches.of(rings), ox, oy + 0.06f, oz, 8f * (1f - land), 8f * (1f - land) + 1f, 96, withAlpha(colour, 0.8f * land), withAlpha(white, 0.4f * land))
            }
        }
        val aura = s.aura(a).toFloat()
        if (aura > 0.01f) halo(frame, batches, 0.0, 1.0, 0.0, 2.2f + 0.2f * sin(a.toFloat() * 3f), withAlpha(colour, 0.18f * aura))
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    override fun post(frame: SceneFrame, out: PostParams) {
        if (s.beast == 1) {
            out.heat = (0.22 * (s.omen(frame.a) + s.aura(frame.a))).toFloat().coerceAtMost(0.3f)
            out.heatSpeed = 0.6f
            out.heatScale = 3f
        }
    }
}

/**
 * Ho-Oh summoned (HoOhSummonScript), drawn: the three memories' lights rising, the spectral
 * beasts (extra actors, translucent and tinted) and their trails, their beams to the sky, the
 * golden sky and the rainbow, Ho-Oh's embers and halo as it comes down and lands.
 */
class HoOhSummonScene(landing: Vector3d, override val title: Component) : JohtoSceneBase() {
    override val id = CinematicScenes.HO_OH_SUMMON
    override val script = HoOhSummonScript(landing)
    private val s = script
    override val subtitle: Component = Component.translatable("generations_core.johto.summon.subtitle")

    override val extraActors: Int get() = 3
    override fun extraSpecies(i: Int): String = Trio.species[i]
    override fun extraActor(i: Int, a: Double, out: ActorKey): ActorKey = s.ghost(i, a, out)
    override fun extraLook(i: Int, a: Double, out: ActorLook): ActorLook = out.ghost(withAlpha(beastColours[i], (0.55 * s.ghostAlpha(a)).toFloat()))

    override fun event(cue: Cue, frame: SceneFrame) {
        when (cue.id) {
            "beams" -> for (i in 0..2) {
                s.ghost(i, cue.time, ActorKey()).pos.let { burst(frame, it.x, it.y + 1.0, it.z, 40, 5.0, 1501 + i * 7, beastColours[i], SPARK, 1.2f, 0.18f) }
            }
            "landed" -> {
                burst(frame, s.landing.x, s.landing.y + 0.5, s.landing.z, 120, 7.0, 1531, gold, EMBER, 1.8f, 0.22f)
                burst(frame, s.landing.x, s.landing.y + 1.0, s.landing.z, 60, 4.0, 1541, white, MOTE, 1.6f, 0.4f)
            }
        }
    }

    override fun update(frame: SceneFrame) {
        val a = frame.a
        val p = frame.particles
        emit(frame, 45.0, s.ghostAlpha(a), 1601) { i ->
            val k = i % 3
            val pos = s.ghost(k, a, ActorKey()).pos
            frame.camera.toWorld(pos.x + (random(i, 1) - 0.5), pos.y + 0.3 + random(i, 2) * 1.2, pos.z + (random(i, 3) - 0.5), world)
            p.emit(MOTE, world.x, world.y, world.z, 0f, 0.5f, 0f, 1.0f, 0.15f, 0.03f, beastColours[k], drag = 0.8f)
        }
        emit(frame, 40.0, s.embers(a), 1611) { i ->
            s.hoohPos(a, local)
            frame.camera.toWorld(local.x + (random(i, 1) - 0.5) * 4.0, local.y + 1.0 + random(i, 2) * 2.0, local.z + (random(i, 3) - 0.5) * 4.0, world)
            p.emit(if (i % 3 == 0) MOTE else EMBER, world.x, world.y, world.z, 0f, -0.8f, 0f, 2.6f, 0.18f, 0.05f, if (i % 2 == 0) gold else flame, drag = 0.4f, swirl = 0.4f)
        }
        p.update(frame.da.toFloat())
    }

    override fun draw(frame: SceneFrame, batches: FxBatches) {
        val a = frame.a
        val m = s.memories(a).toFloat()
        if (m > 0.01f) for (i in 0..2) {
            s.memory(i, a, local)
            halo(frame, batches, local.x, local.y, local.z, 0.9f * m, withAlpha(beastColours[i], 0.85f * m), a.toFloat())
            halo(frame, batches, local.x, local.y, local.z, 0.35f * m, withAlpha(white, 0.9f * m))
        }
        val b = s.beams(a).toFloat()
        if (b > 0.01f) for (i in 0..2) {
            val pos = s.ghost(i, a, ActorKey()).pos
            val c = frame.rel(pos.x, pos.y + 1.2, pos.z, v3)
            val cx = c.x; val cy = c.y; val cz = c.z
            val top = frame.rel(0.0, 42.0, -6.0, w3)
            FxMeshes.beam(batches.of(streaks), cx, cy, cz, top.x, top.y, top.z, 0.35f * b, 1.2f * b, withAlpha(beastColours[i], 0.8f * b), withAlpha(gold, 0.4f * b))
        }
        if (b > 0.01f) halo(frame, batches, 0.0, 42.0, -6.0, 10f * b, withAlpha(gold, 0.6f * b))
        drawRainbow(frame, batches, s.rainbow(a).toFloat(), 0.0, -6.0, -45.0, 52.0)
        val e = s.embers(a).toFloat()
        if (e > 0.01f) {
            s.hoohPos(a, local)
            halo(frame, batches, local.x, local.y + 2.0, local.z, 6f + 0.4f * sin(a.toFloat() * 3f), withAlpha(gold, 0.3f * e))
        }
        frame.particles.render(batches, frame.cam.x, frame.cam.y, frame.cam.z, frame.right, frame.up)
    }

    override fun post(frame: SceneFrame, out: PostParams) {
        val b = s.beams(frame.a).toFloat()
        if (b > 0.05f && frame.project(frame.rel(0.0, 42.0, -6.0, v3), uv)) {
            out.raysX = uv[0]
            out.raysY = uv[1]
            out.rays = 0.5f * b
            out.raysDecay = 0.96f
        }
    }
}
