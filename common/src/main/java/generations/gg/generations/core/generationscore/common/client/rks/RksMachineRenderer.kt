package generations.gg.generations.core.generationscore.common.client.rks

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.item.CobblemonBuiltinItemRenderer
import com.mojang.blaze3d.vertex.PoseStack
import generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClientConfig
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.RareCandyShadows
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksConsoleBlockEntity
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksModuleBlockEntity
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksModuleItemData
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksLayout
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksMachines
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksModule
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The RKS Machine's block entities (RKS-PLAN.md § 3.3): a module alone draws its own model; the
 * console of an assembled machine draws the whole machine (the five modules and their links), in
 * the look of its state: the assembly (cables growing, light sweep), the rings, the liquid and
 * Mewtwo growing in it (§ 5.1), the alarm, the damage (broken glass, shards, hanging cables, dark
 * reactor, red screens, sparks and smoke), the repair (§ 5.4). An incomplete console also shows
 * the missing modules' hologram for a while after a right click (D7).
 */
class RksMachineRenderer<T : BlockEntity>(@Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<T> {
    private val look = RksMachineModel.Look()

    override fun render(be: T, partialTick: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int, overlay: Int) {
        when (be) {
            is RksConsoleBlockEntity -> renderConsole(be, partialTick, pose, buffers, overlay)
            is RksModuleBlockEntity -> renderModule(be, partialTick, pose, buffers, overlay)
        }
    }

    override fun shouldRenderOffScreen(be: T): Boolean = true

    override fun getViewDistance(): Int = 96

    // ---------------------------------------------------------------- a module alone

    private fun renderModule(be: RksModuleBlockEntity, partial: Float, pose: PoseStack, buffers: MultiBufferSource, overlay: Int) {
        val level = be.level ?: return
        val module = be.module
        val console = RksMachines.consoleOf(level, be.blockPos, be.blockState)
        if (console != null && console.state.assembled) return
        // Taken apart: the modules come back one by one.
        if (console != null && level.gameTime - console.machine.since < module.ordinal * 4L && level.gameTime - console.machine.since < 40L) return
        val facing = RksMachines.facingIndex(be.blockState)
        standby(look, level.gameTime + partial)
        look.damaged = module == RksModule.CHAMBER && be.damaged
        if (look.damaged) look.glassRise = 0f
        look.light = LevelRenderer.getLightColor(level, be.blockPos.above())
        look.overlay = overlay
        look.detail = detail(be.blockPos)
        val o = RksLayout.offset(facing, RksModule.CHAMBER.ox - module.ox, 0, RksModule.CHAMBER.oz - module.oz)
        pose.pushPose()
        RksMachineModel.frame(pose, o[0] + 0.5, 0.0, o[2] + 0.5, be.blockState.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot())
        RksMachineModel.drawSolid(pose, buffers, module.bit, look)
        pose.popPose()
        if (!RareCandyShadows.isShadowPass() && (module == RksModule.CHAMBER || module == RksModule.POWER)) {
            val origin = Vec3(be.blockPos.x + o[0] + 0.5, be.blockPos.y.toDouble(), be.blockPos.z + o[2] + 0.5)
            RksMachineClient.queue(RksMachineClient.Job(origin, be.blockState.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot(), module.bit, RksMachineModel.Look().set(look), 0f, 0f, 0f, 0, 0, 0f))
        }
    }

    private fun detail(pos: net.minecraft.core.BlockPos): Boolean {
        if (RksMachineClient.quality() == CinematicClientConfig.Quality.LOW) return false
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        return camera.distanceToSqr(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5) < 24.0 * 24.0
    }

    // ---------------------------------------------------------------- the console: the machine

    private fun renderConsole(be: RksConsoleBlockEntity, partial: Float, pose: PoseStack, buffers: MultiBufferSource, overlay: Int) {
        val level = be.level ?: return
        val time = level.gameTime + partial
        val state = be.state
        val facing = be.facingIndex
        val yaw = be.facing.toYRot()
        val o = RksLayout.offset(facing, RksModule.CHAMBER.ox, 0, RksModule.CHAMBER.oz)
        val origin = be.origin
        val anim = RksMachineClient.anim(be.blockPos)
        val seconds = be.stateSeconds(level.gameTime, partial)
        val shadow = RareCandyShadows.isShadowPass()
        val scene = if (shadow) null else RksMachineClient.sceneAction(origin)
        val broken = scene != null && scene >= generations.gg.generations.core.generationscore.common.client.cinematic.scenes.MewtwoScript.BREAK
        val modules = if (state.assembled) RksMachineModel.ALL_MODULES else RksModule.CONSOLE.bit

        // The rings turn at the state's speed (integrated: no jump when it changes).
        val now = System.nanoTime()
        val dt = if (anim.lastNanos == 0L) 0f else ((now - anim.lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        if (!shadow) anim.lastNanos = now
        if (!shadow) {
            val speeds = ringSpeeds(state, seconds, scene)
            for (i in 0..2) anim.rings[i] = (anim.rings[i] + speeds[i] * dt) % 360f
        }

        lookFor(be, state, seconds, time, broken)
        anim.rings.copyInto(look.rings)
        look.light = LevelRenderer.getLightColor(level, be.blockPos.relative(be.facing))
        look.overlay = overlay
        look.detail = detail(be.blockPos)

        pose.pushPose()
        RksMachineModel.frame(pose, o[0] + 0.5, 0.0, o[2] + 0.5, yaw)
        RksMachineModel.drawSolid(pose, buffers, modules, look)
        // The missing modules' hologram (D7).
        if (!state.assembled && level.gameTime < be.hologramUntil && !shadow) {
            val pulse = 0.55f + 0.25f * sin(time * 0.25f)
            val a = (pulse * 255).toInt().coerceIn(0, 255)
            val colour = (a shl 24) or (((0x30 * pulse).toInt()) shl 16) or (((0xC8 * pulse).toInt()) shl 8) or (0xFF * pulse).toInt()
            RksMachineModel.drawGhost(pose, buffers, be.missingMask, colour)
        }
        pose.popPose()
        if (shadow) return

        // The liquid and Mewtwo in the tank, the glass: drawn after RareCandy's pass.
        val growth = be.growthNow(level.gameTime, partial).toFloat()
        var liquid = 0f
        var murk = 0f
        var turbulence = 0f
        var bubbles = 0
        var mewtwoScale = 0f
        when {
            state == RksState.GROWING -> {
                liquid = if (growth < 0.2f) growth / 0.2f else 1f
                murk = when {
                    growth < 0.2f -> 0.95f
                    growth < 0.45f -> lerp(0.93f, 0.82f, (growth - 0.2f) / 0.25f)
                    growth < 0.7f -> lerp(0.82f, 0.6f, (growth - 0.45f) / 0.25f)
                    growth < 0.95f -> lerp(0.6f, 0.38f, (growth - 0.7f) / 0.25f)
                    else -> 0.38f
                }
                turbulence = if (growth >= 0.95f) 0.6f + 0.4f * (0.5f + 0.5f * sin(time * 0.9f)) else 0.12f + 0.08f * sin(time * 0.05f)
                bubbles = if (growth >= 0.95f) 40 else 26
                mewtwoScale = when {
                    growth < 0.2f -> 0f
                    growth < 0.45f -> lerp(0.2f, 0.4f, (growth - 0.2f) / 0.25f)
                    growth < 0.7f -> lerp(0.4f, 0.65f, (growth - 0.45f) / 0.25f) * (1f + 0.03f * heartbeat(time, 1.0f))
                    growth < 0.95f -> lerp(0.65f, 0.88f, (growth - 0.7f) / 0.25f)
                    else -> lerp(0.88f, 0.9f, ((growth - 0.95f) / 0.05f).coerceIn(0f, 1f))
                }
            }
            state == RksState.AWAKENING && !broken -> {
                liquid = 1f
                murk = 0.36f
                turbulence = 1f
                bubbles = 48
                mewtwoScale = if (scene != null) 0f else 0.9f
            }
        }
        val liquidColour = lerpColour(0x24103A, 0xC08BFF, 1f - murk)
        val sweep = if (state == RksState.ASSEMBLING) (seconds / generations.gg.generations.core.generationscore.common.world.rks.RksSettings.machine.assemblySeconds.coerceAtLeast(0.1)).toFloat().coerceIn(0f, 1f) else -1f
        RksMachineClient.queue(
            RksMachineClient.Job(origin, yaw, modules, RksMachineModel.Look().set(look), liquid, murk, turbulence, liquidColour, bubbles, time / 20f, sweep),
        )
        if (mewtwoScale > 0.01f) {
            val species = ResourceLocation.fromNamespaceAndPath("cobblemon", "mewtwo")
            val form = PokemonSpecies.getByIdentifier(species)?.standardForm
            val height = (form?.let { it.hitbox.height() * it.baseScale } ?: 2f) * mewtwoScale
            val bob = 0.05f * sin(time * 0.06f)
            var jx = 0.0
            var jz = 0.0
            if (growth >= 0.95f || state == RksState.AWAKENING) {
                jx = (Math.random() - 0.5) * 0.03
                jz = (Math.random() - 0.5) * 0.03
            }
            val feet = Vector3d(origin.x + jx, origin.y + 1.95 - height / 2.0 + bob, origin.z + jz)
            // Slow, as asleep: its own animations at a third of their speed.
            RksMachineClient.drawActor(anim, species, feet, yaw.toDouble(), mewtwoScale.toDouble(), time / 20f * 0.35f)
        }
        particles(be, state, anim, origin, growth)
    }

    private fun particles(be: RksConsoleBlockEntity, state: RksState, anim: RksMachineClient.Anim, origin: Vec3, growth: Float) {
        val level = be.level ?: return
        if (RksMachineClient.quality() == CinematicClientConfig.Quality.LOW || Minecraft.getInstance().isPaused) return
        val now = System.nanoTime()
        val damaged = state == RksState.DAMAGED
        val pulses = state == RksState.GROWING && growth >= 0.95f
        if (!damaged && !pulses) return
        val every = if (RksMachineClient.quality() == CinematicClientConfig.Quality.HIGH) 250_000_000L else 500_000_000L
        if (now - anim.particleAt < every) return
        anim.particleAt = now
        val random = level.random
        val a = random.nextDouble() * 2 * PI
        val r = 0.72
        if (damaged) {
            val top = random.nextBoolean()
            val y = if (top) 3.2 + random.nextDouble() * 0.2 else 0.7 + random.nextDouble() * 0.5
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, origin.x + r * sin(a), origin.y + y, origin.z + r * kotlin.math.cos(a), (random.nextDouble() - 0.5) * 0.2, 0.05, (random.nextDouble() - 0.5) * 0.2)
            if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, origin.x + 0.3 * sin(a), origin.y + 3.5, origin.z + 0.3 * kotlin.math.cos(a), 0.0, 0.03, 0.0)
        } else {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, origin.x + 0.46 * sin(a), origin.y + 3.05, origin.z + 0.46 * kotlin.math.cos(a), 0.0, -0.05, 0.0)
        }
    }

    // ---------------------------------------------------------------- looks

    private fun ringSpeeds(state: RksState, seconds: Double, scene: Double?): FloatArray = when (state) {
        RksState.INCOMPLETE, RksState.DAMAGED -> floatArrayOf(0f, 0f, 0f)
        RksState.ASSEMBLING -> {
            val p = (seconds / 3.0).toFloat().coerceIn(0f, 1f)
            floatArrayOf(25f * p, -18f * p, 12f * p)
        }
        RksState.CRAFTING -> floatArrayOf(110f, -85f, 60f)
        RksState.ANALYSING -> floatArrayOf(70f, -55f, 40f)
        RksState.GROWING -> floatArrayOf(45f, -32f, 22f)
        RksState.AWAKENING -> if (scene != null && scene >= generations.gg.generations.core.generationscore.common.client.cinematic.scenes.MewtwoScript.BREAK) floatArrayOf(15f, -10f, 6f) else floatArrayOf(240f, -190f, 150f)
        RksState.REPAIRING -> {
            val p = (seconds / 4.0).toFloat().coerceIn(0f, 1f)
            floatArrayOf(25f * p * p, -18f * p * p, 12f * p * p)
        }
        else -> floatArrayOf(25f, -18f, 12f)
    }

    /** A module alone: lights on standby (amber, slow), reactor off, screens dim. */
    private fun standby(out: RksMachineModel.Look, time: Float) {
        out.damaged = false
        out.glassRise = 1f
        out.shardRise = 0f
        out.links = 1f
        out.linkDrop = 0f
        out.scanOn = false
        out.arm = 0f
        out.scan = 0f
        out.screenRed = false
        out.rings.fill(0f)
        out.lights = withBrightness(AMBER, 0.45f + 0.25f * (0.5f + 0.5f * sin(time * 0.08f)))
        out.core = OFF
        out.screens = 0xFF4A525C.toInt()
    }

    private fun lookFor(be: RksConsoleBlockEntity, state: RksState, seconds: Double, time: Float, broken: Boolean) {
        standby(look, time)
        val growth = be.growthNow(be.level!!.gameTime).toFloat()
        when (state) {
            RksState.INCOMPLETE -> {}
            RksState.ASSEMBLING -> {
                val p = (seconds / 3.0).toFloat().coerceIn(0f, 1f)
                look.links = smooth(p / 0.8f)
                look.linkDrop = 0.06f * (1f - smooth(p / 0.4f))
                look.lights = withBrightness(CYAN, 0.25f + 0.75f * smooth((p - 0.6f) / 0.4f) + 0.3f * sin(p * 30f).coerceAtLeast(0f) * (1f - p))
                look.core = withBrightness(CYAN, smooth((p - 0.5f) / 0.5f))
                look.screens = withBrightness(0xFFFFFFFF.toInt(), 0.3f + 0.7f * smooth((p - 0.7f) / 0.3f))
            }
            RksState.IDLE -> idle(time)
            RksState.CRAFTING -> {
                idle(time)
                look.lights = withBrightness(CYAN, 0.75f + 0.25f * sin(time * 0.6f))
                look.arm = -0.55f + 0.5f * sin(time * 0.12f)
            }
            RksState.ANALYSING -> {
                idle(time)
                look.lights = withBrightness(GREEN, 0.75f + 0.25f * sin(time * 0.5f))
                look.scanOn = true
                look.scan = -0.5f + 0.75f * sin(time * 0.18f)
                look.arm = look.scan
            }
            RksState.GROWING -> {
                idle(time)
                val beat = heartbeat(time, 1f + growth)
                look.lights = withBrightness(VIOLET, 0.55f + 0.45f * beat)
                look.core = lerpColour(0x6FE8FF, 0xB06BFF, growth) or (0xFF shl 24)
                // Brain activity on the screens (70–95 %).
                if (growth >= 0.7f) look.screens = withBrightness(lerpColour(0xFFFFFF, 0xD9B8FF, 0.6f) or (0xFF shl 24), 0.7f + 0.3f * beat)
            }
            RksState.AWAKENING -> {
                val alarm = if (sin(time * 0.6f) > 0f) 1f else 0.35f
                look.lights = withBrightness(RED, alarm)
                look.screenRed = true
                look.core = withBrightness(lerpColour(0xB06BFF, 0xFF3B30, 0.5f) or (0xFF shl 24), 0.7f + 0.3f * alarm)
                look.screens = withBrightness(0xFFFFFFFF.toInt(), alarm)
                if (broken) {
                    look.damaged = true
                    look.glassRise = 0f
                }
            }
            RksState.DAMAGED -> {
                look.damaged = true
                look.glassRise = 0f
                val flicker = if (abs(sin(time * 0.9f) * sin(time * 0.37f)) > 0.3f) 0.6f else 0.15f
                look.lights = withBrightness(RED, flicker)
                look.screenRed = true
                look.screens = withBrightness(0xFFFFFFFF.toInt(), if (sin(time * 0.21f) > -0.2f) 0.55f else 0.08f)
                look.core = OFF
            }
            RksState.REPAIRING -> {
                val p = (seconds / 4.0).toFloat().coerceIn(0f, 1f)
                if (p < 0.5f) {
                    look.damaged = true
                    look.glassRise = 0f
                    look.shardRise = 2.4f * (p / 0.5f) * (p / 0.5f)
                } else {
                    look.damaged = false
                    look.glassRise = smooth((p - 0.5f) / 0.45f)
                }
                look.lights = withBrightness(CYAN, 0.4f + 0.6f * p)
                look.screenRed = p < 0.6f
                look.screens = withBrightness(0xFFFFFFFF.toInt(), if (p < 0.6f) 0.3f else smooth((p - 0.6f) / 0.3f))
                look.core = withBrightness(CYAN, smooth((p - 0.3f) / 0.7f))
            }
        }
    }

    private fun idle(time: Float) {
        look.lights = withBrightness(CYAN, 0.85f + 0.15f * sin(time * 0.1f))
        look.core = withBrightness(CORE, 0.8f + 0.2f * sin(time * 0.15f))
        look.screens = 0xFFFFFFFF.toInt()
    }

    companion object {
        private val CYAN = 0xFF35E0FF.toInt()
        private val CORE = 0xFF6FE8FF.toInt()
        private val VIOLET = 0xFFB06BFF.toInt()
        private val RED = 0xFFFF3B30.toInt()
        private val GREEN = 0xFF4DFF88.toInt()
        private val AMBER = 0xFFFFB347.toInt()
        private val OFF = 0xFF262C34.toInt()

        fun smooth(x: Float): Float {
            val t = x.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

        /** Two quick beats then a rest, `rate` times a second (ticks in). */
        fun heartbeat(ticks: Float, rate: Float): Float {
            val phase = (ticks / 20f * rate) % 1f
            fun beat(at: Float) = kotlin.math.exp(-((phase - at) * 18f).let { it * it })
            return (beat(0.1f) + 0.7f * beat(0.28f)).coerceIn(0f, 1f)
        }

        fun withBrightness(argb: Int, k: Float): Int {
            val f = k.coerceIn(0f, 1.2f)
            val r = (((argb shr 16) and 255) * f).toInt().coerceIn(0, 255)
            val g = (((argb shr 8) and 255) * f).toInt().coerceIn(0, 255)
            val b = ((argb and 255) * f).toInt().coerceIn(0, 255)
            return (argb and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }

        fun lerpColour(a: Int, b: Int, t: Float): Int {
            val k = t.coerceIn(0f, 1f)
            fun ch(shift: Int) = ((((a shr shift) and 255) * (1f - k) + ((b shr shift) and 255) * k).toInt() and 255) shl shift
            return ch(16) or ch(8) or ch(0)
        }
    }
}

/** A module's item drawn in 3D (D15): the module's own parts, fitted in the item's cube. */
class RksModuleItemRenderer(private val module: RksModule) : CobblemonBuiltinItemRenderer {
    private val look = RksMachineModel.Look()

    override fun render(stack: ItemStack, mode: ItemDisplayContext, matrices: PoseStack, vertexConsumers: MultiBufferSource, light: Int, overlay: Int) {
        val b = RksMachineModel.bounds(module.bit) ?: return
        val size = maxOf(b[3] - b[0], b[4] - b[1], b[5] - b[2]).coerceAtLeast(0.1f)
        val s = 1f / size
        look.lights = 0xFF35E0FF.toInt()
        look.core = 0xFF6FE8FF.toInt()
        look.screens = -1
        look.screenRed = false
        look.rings[0] = 30f
        look.rings[1] = 60f
        look.rings[2] = 15f
        look.light = light
        look.overlay = overlay
        look.detail = mode != ItemDisplayContext.GUI || module == RksModule.CONSOLE
        look.damaged = module == RksModule.CHAMBER && RksModuleItemData.read(stack)?.getBoolean(RksModuleBlockEntity.ITEM_DAMAGED) == true
        look.glassRise = if (look.damaged) 0f else 1f
        matrices.pushPose()
        matrices.translate(0.5, 0.5, 0.5)
        matrices.scale(s, s, s)
        matrices.translate(-(b[0] + b[3]) / 2.0, -(b[1] + b[4]) / 2.0, -(b[2] + b[5]) / 2.0)
        RksMachineModel.drawSolid(matrices, vertexConsumers, module.bit, look)
        RksMachineModel.drawGlass(matrices, vertexConsumers, module.bit, look, null)
        matrices.popPose()
    }
}
