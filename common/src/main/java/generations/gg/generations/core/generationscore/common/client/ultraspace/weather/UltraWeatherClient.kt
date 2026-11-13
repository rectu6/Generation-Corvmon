package generations.gg.generations.core.generationscore.common.client.ultraspace.weather

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraHazardPacket
import generations.gg.generations.core.generationscore.common.network.packets.ultraspace.S2CUltraWeatherPacket
import generations.gg.generations.core.generationscore.common.world.ultraspace.weather.UltraHazardStatus
import generations.gg.generations.core.generationscore.common.world.ultraspace.weather.UltraShelter
import generations.gg.generations.core.generationscore.common.world.ultraspace.weather.UltraWeatherEvent
import generations.gg.generations.core.generationscore.common.world.ultraspace.weather.UltraWeatherSky
import net.minecraft.ChatFormatting
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.ParticleStatus
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.core.particles.DustColorTransitionOptions
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.material.FogType
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * What the client makes of its Ultra world's weather (phase 4.4): particles, sounds, fog,
 * a tinted veil and vignette over the screen, the heat's swaying of the view, Minecraft's
 * rain where it falls, and the indicator of the dangers. The server decides the weather
 * (S2CUltraWeatherPacket, kept in UltraWeatherSky.client); everything here only shows it.
 *
 * With a shader pack (Iris), the fog may be replaced by the pack's: every weather also has
 * what shaders keep, the particles, the veil (drawn with the interface), the rain level
 * (the packs' rainStrength), the bolts, the sounds and the swaying of the view.
 */
object UltraWeatherClient {
    /** A weather's look at full strength. */
    private class Look(
        val fog: Int, val fogStrength: Float, val fogEnd: Float, val fogStart: Float,
        val tint: Int, val tintAlpha: Float,
        val glow: Int, val glowAlpha: Float,
        val dark: Float,
        /** Degrees of swaying of the field of view (heat). */
        val sway: Float = 0f,
    )

    private val LOOKS: Map<UltraWeatherEvent, Look> = mapOf(
        UltraWeatherEvent.TROPICAL_RAIN to Look(0x8EA3AE, 0.45f, 0.55f, 0.1f, 0x3A5A70, 0.06f, 0, 0f, 0f),
        UltraWeatherEvent.EXTREME_HEAT to Look(0xE8B070, 0.4f, 0.7f, 0.25f, 0xFF7A1A, 0.08f, 0xFF5A00, 0.35f, 0f, 1.2f),
        UltraWeatherEvent.INTENSE_SUN to Look(0xFFF4D0, 0.3f, 1f, 0.85f, 0xFFF3C0, 0.07f, 0xFFE08A, 0.2f, 0f),
        UltraWeatherEvent.SANDSTORM to Look(0xC8A060, 0.85f, 0.18f, 0f, 0xC89A50, 0.22f, 0xA06A20, 0.25f, 0.2f),
        UltraWeatherEvent.ENERGY_SURGE to Look(0x7FD8FF, 0.25f, 0.85f, 0.4f, 0x3FA8FF, 0.04f, 0x5FC8FF, 0.3f, 0f),
        UltraWeatherEvent.ELECTRIC_STORM to Look(0x30303C, 0.6f, 0.5f, 0.1f, 0x101020, 0.12f, 0x8F8FFF, 0.15f, 0.4f),
        UltraWeatherEvent.ASH_RAIN to Look(0x77706C, 0.7f, 0.4f, 0.05f, 0x5A5550, 0.1f, 0, 0f, 0.2f),
        UltraWeatherEvent.THERMAL_ERUPTION to Look(0xB0402A, 0.6f, 0.35f, 0.05f, 0xFF3A10, 0.12f, 0xFF4000, 0.45f, 0.15f, 2f),
        UltraWeatherEvent.DIMENSIONAL_MIST to Look(0xD9CCFF, 0.85f, 0.22f, 0f, 0xE8E0FF, 0.16f, 0xB8A0FF, 0.15f, 0f),
        UltraWeatherEvent.CUTTING_WINDS to Look(0xC6C0D8, 0.4f, 0.6f, 0.15f, 0xFFFFFF, 0.04f, 0, 0f, 0.15f, 0.4f),
        UltraWeatherEvent.CRYSTAL_RAIN to Look(0x6FD8E8, 0.35f, 0.8f, 0.3f, 0x8FF3FF, 0.04f, 0x8FF3FF, 0.25f, 0f),
        UltraWeatherEvent.TOXIC_MIST to Look(0x4F8A3A, 0.85f, 0.2f, 0f, 0x5AB840, 0.16f, 0x9B4DCA, 0.35f, 0.15f, 0.5f),
        UltraWeatherEvent.DIMENSIONAL_DARKNESS to Look(0x0C0A14, 0.9f, 0.3f, 0f, 0x05030A, 0.25f, 0x3A1A5A, 0.2f, 0.8f),
        UltraWeatherEvent.ACID_RAIN to Look(0x6F7A4A, 0.6f, 0.45f, 0.1f, 0x6A8A20, 0.1f, 0xA5D84A, 0.2f, 0.2f),
    )

    private val VIGNETTE: ResourceLocation = ResourceLocation.withDefaultNamespace("textures/misc/vignette.png")
    private val ACID_RAIN: ResourceLocation = GenerationsCore.id("textures/environment/ultra_acid_rain.png")

    private val random = RandomSource.create()
    private var ticks = 0
    /** The visual strength under a roof or in a shelter (smoothed): 1 outside, less inside. */
    private var shelter = 1f
    private var flash = 0f
    private var loop: LoopSound? = null
    private val soundEvents = HashMap<String, SoundEvent?>()

    // The danger indicator (S2CUltraHazardPacket).
    private var hazardEvent: UltraWeatherEvent? = null
    private var hazardStatus = UltraHazardStatus.NONE
    private var hazardPieces = 0
    private var hazardSeconds = 0
    /** The name of a harmless weather is shown for a while when it starts or on arriving. */
    private var shownEvent: UltraWeatherEvent? = null
    private var shownUntil = 0

    fun init() {
        UltraWeatherClientConfig.load()
        PlatformEvents.CLIENT_TICK_POST.subscribe(Priority.NORMAL) { tick() }
        PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { reset() }
    }

    private fun reset() {
        UltraWeatherSky.client = null
        hazardEvent = null
        hazardStatus = UltraHazardStatus.NONE
        shownEvent = null
        loop?.let { Minecraft.getInstance().soundManager.stop(it) }
        loop = null
    }

    fun onWeather(packet: S2CUltraWeatherPacket) {
        val event = UltraWeatherEvent.byId(packet.event)
        UltraWeatherSky.client = if (packet.world.isEmpty() || event == null) null
        else UltraWeatherSky.Active(packet.world, event, packet.start, packet.end, packet.wind, packet.fade)
        if (event != shownEvent) {
            shownEvent = event
            shownUntil = ticks + 200
        }
    }

    fun onHazard(packet: S2CUltraHazardPacket) {
        hazardEvent = UltraWeatherEvent.byId(packet.event)
        hazardStatus = UltraHazardStatus.entries.getOrElse(packet.status) { UltraHazardStatus.NONE }
        hazardPieces = packet.pieces
        hazardSeconds = packet.seconds
    }

    private fun active(): UltraWeatherSky.Active? = Minecraft.getInstance().level?.let { UltraWeatherSky.active(it) }

    /** The weather's look and its strength now (shelter included), null when nothing shows. */
    private fun look(partialTick: Float): Pair<Look, Float>? {
        val level = Minecraft.getInstance().level ?: return null
        val active = UltraWeatherSky.active(level) ?: return null
        val strength = active.intensity(level.gameTime + partialTick.toDouble()) * shelter
        if (strength <= 0.001f) return null
        return (LOOKS[active.event] ?: return null) to strength
    }

    private fun tick() {
        ticks++
        val mc = Minecraft.getInstance()
        val level = mc.level
        val player = mc.player
        if (level == null || player == null) return
        if (mc.isPaused) return
        flash *= 0.72f
        val active = UltraWeatherSky.active(level)
        val intensity = active?.intensity(level.gameTime.toDouble()) ?: 0f
        shelter += (shelterTarget(level, player, active) - shelter) * 0.1f
        updateLoop(mc, active, intensity)
        if (active == null || intensity <= 0f) return
        particles(mc, level, active, intensity)
        sounds(mc, player, active, intensity)
        when (active.event) {
            UltraWeatherEvent.ENERGY_SURGE -> if (random.nextInt(140) == 0) flash = maxOf(flash, 0.6f * intensity)
            UltraWeatherEvent.ELECTRIC_STORM -> if (random.nextInt(220) == 0) flash = maxOf(flash, 0.4f * intensity)
            else -> {}
        }
    }

    private fun shelterTarget(level: ClientLevel, player: LocalPlayer, active: UltraWeatherSky.Active?): Float {
        if (active == null) return 1f
        if (hazardEvent == active.event && hazardStatus == UltraHazardStatus.SHELTERED) return 0.4f
        if (active.world == "ultra_abyss") return 1f
        return if (level.canSeeSky(BlockPos.containing(player.x, player.eyeY, player.z))) 1f else 0.5f
    }

    // ---- particles ----

    private fun particles(mc: Minecraft, level: ClientLevel, active: UltraWeatherSky.Active, intensity: Float) {
        val status = when (mc.options.particles().get()) {
            ParticleStatus.ALL -> 1f
            ParticleStatus.DECREASED -> 0.5f
            else -> 0.15f
        }
        val k = UltraWeatherClientConfig.settings.particles * status * intensity
        if (k <= 0f) return
        val cam = mc.gameRenderer.mainCamera.position
        val surface = active.world != "ultra_abyss"
        val windX = Mth.cos(active.wind).toDouble()
        val windZ = Mth.sin(active.wind).toDouble()

        fun count(base: Float): Int {
            val wanted = base * k
            var n = wanted.toInt()
            if (random.nextFloat() < wanted - n) n++
            return n
        }

        /** A spot of air around the camera, under the open sky in the surface worlds. */
        fun air(radius: Double, below: Double, above: Double): Vec3? {
            val x = cam.x + (random.nextDouble() * 2 - 1) * radius
            val y = cam.y - below + random.nextDouble() * (below + above)
            val z = cam.z + (random.nextDouble() * 2 - 1) * radius
            val pos = BlockPos.containing(x, y, z)
            if (!level.getBlockState(pos).isAir) return null
            if (surface && !level.canSeeSky(pos)) return null
            return Vec3(x, y, z)
        }

        /** The open ground around the camera (surface worlds), or a floor of the cavern below a spot of air. */
        fun ground(radius: Double): Vec3? {
            val x = cam.x + (random.nextDouble() * 2 - 1) * radius
            val z = cam.z + (random.nextDouble() * 2 - 1) * radius
            if (surface) {
                val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z))
                if (Math.abs(y - cam.y) > 20) return null
                return Vec3(x, y + 0.1, z)
            }
            val pos = BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(cam.y) + 4, Mth.floor(z))
            for (i in 0 until 12) {
                if (!level.getBlockState(pos).isAir && level.getBlockState(pos.above()).isAir) return Vec3(x, pos.y + 1.1, z)
                pos.move(0, -1, 0)
            }
            return null
        }

        fun spawn(options: ParticleOptions, at: Vec3?, vx: Double = 0.0, vy: Double = 0.0, vz: Double = 0.0, speed: Boolean = true) {
            if (at == null) return
            val particle = mc.particleEngine.createParticle(options, at.x, at.y, at.z, vx, vy, vz) ?: return
            if (speed) particle.setParticleSpeed(vx, vy, vz)
        }

        fun colour(rgb: Int) = Vector3f(((rgb shr 16) and 255) / 255f, ((rgb shr 8) and 255) / 255f, (rgb and 255) / 255f)
        fun jitter(amount: Double) = (random.nextDouble() * 2 - 1) * amount

        when (active.event) {
            UltraWeatherEvent.TROPICAL_RAIN -> repeat(count(1.5f)) {
                spawn(ParticleTypes.WHITE_SMOKE, ground(14.0)?.add(0.0, random.nextDouble(), 0.0), windX * 0.01, 0.004, windZ * 0.01)
            }
            UltraWeatherEvent.EXTREME_HEAT -> {
                val heat = DustColorTransitionOptions(colour(0xFFB347), colour(0xE8401C), 1f)
                repeat(count(5f)) { spawn(heat, ground(14.0)?.add(0.0, 0.2, 0.0), jitter(0.01), 0.03, jitter(0.01)) }
                repeat(count(1f)) { spawn(ParticleTypes.WHITE_SMOKE, ground(10.0), 0.0, 0.02, 0.0) }
            }
            UltraWeatherEvent.INTENSE_SUN -> repeat(count(1.5f)) { spawn(ParticleTypes.WAX_ON, air(12.0, 2.0, 6.0), speed = false) }
            UltraWeatherEvent.SANDSTORM -> {
                repeat(count(45f)) {
                    val sand = DustParticleOptions(colour(if (random.nextBoolean()) 0xD8B878 else 0xC09858), 1.2f + random.nextFloat() * 1.2f)
                    spawn(sand, air(18.0, 3.0, 10.0), windX * 0.9 + jitter(0.15), jitter(0.03), windZ * 0.9 + jitter(0.15))
                }
                repeat(count(4f)) { spawn(BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), air(12.0, 0.0, 8.0), speed = false) }
                repeat(count(0.3f)) { spawn(ParticleTypes.SMALL_GUST, air(10.0, 1.0, 3.0), speed = false) }
            }
            UltraWeatherEvent.ENERGY_SURGE -> {
                repeat(count(6f)) { spawn(ParticleTypes.ELECTRIC_SPARK, air(10.0, 2.0, 5.0), jitter(0.2), jitter(0.2), jitter(0.2)) }
                repeat(count(2f)) { spawn(ParticleTypes.GLOW, air(12.0, 2.0, 6.0), jitter(0.02), 0.01, jitter(0.02)) }
            }
            UltraWeatherEvent.ELECTRIC_STORM -> {
                repeat(count(10f)) { spawn(ParticleTypes.ELECTRIC_SPARK, air(12.0, 2.0, 6.0), jitter(0.3), jitter(0.3), jitter(0.3)) }
                repeat(count(1f)) { spawn(ParticleTypes.ELECTRIC_SPARK, ground(10.0), jitter(0.2), 0.3, jitter(0.2)) }
            }
            UltraWeatherEvent.ASH_RAIN -> {
                repeat(count(30f)) { spawn(ParticleTypes.ASH, air(16.0, 2.0, 12.0), speed = false) }
                repeat(count(12f)) { spawn(ParticleTypes.WHITE_ASH, air(16.0, 2.0, 12.0), speed = false) }
            }
            UltraWeatherEvent.THERMAL_ERUPTION -> {
                repeat(count(1.5f)) { spawn(ParticleTypes.LAVA, ground(16.0), speed = false) }
                repeat(count(0.6f)) { spawn(ParticleTypes.CAMPFIRE_COSY_SMOKE, ground(24.0), windX * 0.01, 0.07, windZ * 0.01) }
                repeat(count(4f)) { spawn(ParticleTypes.LARGE_SMOKE, air(14.0, 1.0, 8.0), windX * 0.02, 0.02, windZ * 0.02) }
                val embers = DustColorTransitionOptions(colour(0xFF8A2A), colour(0x6A1A0A), 1.2f)
                repeat(count(5f)) { spawn(embers, ground(14.0)?.add(0.0, 0.3, 0.0), jitter(0.02), 0.05, jitter(0.02)) }
            }
            UltraWeatherEvent.DIMENSIONAL_MIST -> {
                repeat(count(6f)) { spawn(ParticleTypes.WHITE_SMOKE, ground(16.0)?.add(0.0, random.nextDouble() * 2.0, 0.0), windX * 0.01, 0.0, windZ * 0.01) }
                repeat(count(4f)) { spawn(ParticleTypes.SPORE_BLOSSOM_AIR, air(14.0, 2.0, 6.0), speed = false) }
            }
            UltraWeatherEvent.CUTTING_WINDS -> {
                repeat(count(28f)) {
                    val paper = DustParticleOptions(colour(if (random.nextBoolean()) 0xF4F0E8 else 0xDCD4F0), 0.9f + random.nextFloat() * 0.5f)
                    spawn(paper, air(16.0, 2.0, 8.0), windX * 1.2 + jitter(0.2), jitter(0.08), windZ * 1.2 + jitter(0.2))
                }
                repeat(count(0.6f)) { spawn(ParticleTypes.SMALL_GUST, air(10.0, 1.0, 3.0), speed = false) }
                repeat(count(0.08f)) { spawn(ParticleTypes.GUST, air(12.0, 0.0, 3.0), speed = false) }
            }
            UltraWeatherEvent.CRYSTAL_RAIN -> {
                repeat(count(16f)) { spawn(ParticleTypes.END_ROD, air(14.0, -2.0, 10.0), jitter(0.01), -0.12, jitter(0.01)) }
                val shard = DustParticleOptions(colour(0x8FF3FF), 0.8f)
                repeat(count(4f)) { spawn(shard, air(12.0, -1.0, 8.0), 0.0, -0.8, 0.0) }
            }
            UltraWeatherEvent.TOXIC_MIST -> {
                repeat(count(22f)) {
                    val mist = DustParticleOptions(colour(if (random.nextInt(3) == 0) 0x9B4DCA else 0x6BE36B), 1.6f + random.nextFloat() * 0.8f)
                    spawn(mist, air(14.0, 3.0, 5.0), windX * 0.03 + jitter(0.02), jitter(0.01), windZ * 0.03 + jitter(0.02))
                }
                repeat(count(3f)) { spawn(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF6BE36B.toInt()), air(10.0, 2.0, 3.0), speed = false) }
            }
            UltraWeatherEvent.DIMENSIONAL_DARKNESS -> {
                repeat(count(5f)) { spawn(ParticleTypes.SMOKE, air(14.0, 2.0, 6.0), windX * 0.01, 0.0, windZ * 0.01) }
                repeat(count(1f)) { spawn(ParticleTypes.SQUID_INK, air(12.0, 1.0, 4.0), jitter(0.01), 0.0, jitter(0.01)) }
                repeat(count(0.15f)) { spawn(ParticleTypes.SCULK_SOUL, ground(12.0), 0.0, 0.04, 0.0) }
            }
            UltraWeatherEvent.ACID_RAIN -> {
                val acid = DustParticleOptions(colour(0xA8E04A), 0.7f)
                repeat(count(10f)) { spawn(acid, ground(12.0), jitter(0.05), 0.05, jitter(0.05)) }
                repeat(count(1f)) { spawn(ParticleTypes.SMOKE, ground(10.0), 0.0, 0.02, 0.0) }
            }
        }
    }

    // ---- sounds ----

    private fun sound(id: String): SoundEvent? = soundEvents.getOrPut(id) {
        ResourceLocation.tryParse(id)?.let { SoundEvent.createVariableRangeEvent(it) }
    }

    /** What the loop of this weather should sound like now (0 when another weather or none). */
    fun loopVolume(event: UltraWeatherEvent): Float {
        val level = Minecraft.getInstance().level ?: return 0f
        val active = UltraWeatherSky.active(level)?.takeIf { it.event == event } ?: return 0f
        val sounds = UltraWeatherClientConfig.settings.sounds[event.id] ?: return 0f
        return active.intensity(level.gameTime.toDouble()) * sounds.loopVolume * UltraWeatherClientConfig.settings.volume * (0.4f + 0.6f * shelter)
    }

    private fun updateLoop(mc: Minecraft, active: UltraWeatherSky.Active?, intensity: Float) {
        val event = active?.event?.takeIf { intensity > 0f } ?: return
        val current = loop
        if (current != null && current.event == event && !current.isStopped) return
        val id = UltraWeatherClientConfig.settings.sounds[event.id]?.loop?.takeIf { it.isNotBlank() } ?: return
        val sound = sound(id) ?: return
        loop = LoopSound(sound, event).also { mc.soundManager.play(it) }
    }

    private fun sounds(mc: Minecraft, player: LocalPlayer, active: UltraWeatherSky.Active, intensity: Float) {
        val sounds = UltraWeatherClientConfig.settings.sounds[active.event.id] ?: return
        if (sounds.sounds.isEmpty() || random.nextFloat() >= sounds.soundsPerMinute / 1200f * intensity) return
        val sound = sound(sounds.sounds[random.nextInt(sounds.sounds.size)]) ?: return
        val angle = random.nextFloat() * Mth.TWO_PI
        val distance = 4f + random.nextFloat() * 12f
        val volume = sounds.soundVolume * UltraWeatherClientConfig.settings.volume * (0.4f + 0.6f * shelter) * intensity
        if (volume <= 0f) return
        mc.soundManager.play(
            SimpleSoundInstance(
                sound, SoundSource.WEATHER, volume, 0.8f + random.nextFloat() * 0.4f, RandomSource.create(random.nextLong()),
                player.x + Mth.cos(angle) * distance, player.eyeY + random.nextFloat() * 4f - 1f, player.z + Mth.sin(angle) * distance,
            )
        )
    }

    /** A weather's ambient loop, following its strength, gone when the weather goes. */
    private class LoopSound(sound: SoundEvent, val event: UltraWeatherEvent) :
        AbstractTickableSoundInstance(sound, SoundSource.WEATHER, SoundInstance.createUnseededRandom()) {
        init {
            looping = true
            delay = 0
            relative = true
            attenuation = SoundInstance.Attenuation.NONE
            volume = 0.01f
        }

        override fun tick() {
            val target = UltraWeatherClient.loopVolume(event)
            volume += (target - volume) * 0.05f
            if (target <= 0f && volume < 0.02f) stop()
        }

        override fun canStartSilent(): Boolean = true
    }

    // ---- what the mixins ask ----

    private fun fogApplies(camera: Camera): Boolean {
        if (UltraWarpRideClient.isActive || camera.fluidInCamera != FogType.NONE) return false
        val entity = camera.entity as? LivingEntity
        return entity == null || (!entity.hasEffect(MobEffects.BLINDNESS) && !entity.hasEffect(MobEffects.DARKNESS))
    }

    /** FogRendererMixin.setupColor: how much of the weather's colour the fog takes (0: none). */
    @JvmStatic
    fun fogColourBlend(camera: Camera, partialTick: Float): Float {
        if (!fogApplies(camera)) return 0f
        val (look, strength) = look(partialTick) ?: return 0f
        return look.fogStrength * strength * UltraWeatherClientConfig.settings.fog
    }

    @JvmStatic
    fun fogColour(): Int = look(0f)?.first?.fog ?: 0

    /** FogRendererMixin.setupFog: the fog drawn nearer (or further) as the weather asks. */
    @JvmStatic
    fun adjustFog(camera: Camera, partialTick: Float) {
        if (!fogApplies(camera)) return
        val (look, strength) = look(partialTick) ?: return
        val k = strength * UltraWeatherClientConfig.settings.fog
        if (k <= 0f) return
        val start = RenderSystem.getShaderFogStart()
        val end = RenderSystem.getShaderFogEnd()
        val newEnd = Mth.lerp(k, end, end * look.fogEnd)
        val newStart = Mth.lerp(k, start, newEnd * look.fogStart)
        RenderSystem.setShaderFogStart(minOf(newStart, newEnd - 0.5f))
        RenderSystem.setShaderFogEnd(newEnd)
    }

    /** GameRendererMixin.getFov: degrees added to the field of view (the heat's shimmer). */
    @JvmStatic
    fun fovSway(partialTick: Float): Double {
        if (UltraWarpRideClient.isActive) return 0.0
        val (look, strength) = look(partialTick) ?: return 0.0
        if (look.sway <= 0f) return 0.0
        val t = ticks + partialTick
        val wave = Mth.sin(t * 0.07f) * 0.6f + Mth.sin(t * 0.19f + 1f) * 0.4f
        return (look.sway * strength * UltraWeatherClientConfig.settings.viewSway * wave).toDouble()
    }

    /** LevelRendererMixin: Minecraft's rain falls in the client's Ultra world now. */
    @JvmStatic
    fun raining(): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        return UltraWeatherSky.precipitation(level)
    }

    /** LevelRendererMixin: the acid rain's own texture. */
    @JvmStatic
    fun rainTexture(vanilla: ResourceLocation): ResourceLocation =
        if (active()?.event == UltraWeatherEvent.ACID_RAIN) ACID_RAIN else vanilla

    // ---- the screen ----

    /** GuiMixin: the weather's veil and vignette, then the indicator. */
    @JvmStatic
    fun renderOverlay(graphics: GuiGraphics, deltaTracker: DeltaTracker) {
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) return
        val partialTick = deltaTracker.getGameTimeDeltaPartialTick(false)
        val settings = UltraWeatherClientConfig.settings
        if (!UltraWarpRideClient.isActive && settings.overlay > 0f) {
            look(partialTick)?.let { (look, strength) -> drawLook(graphics, look, strength * settings.overlay) }
            if (flash > 0.01f) {
                graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), argb(Mth.clamp(flash * 0.3f * settings.overlay, 0f, 1f), 0xDDF4FF))
                graphics.flush()
            }
        }
        if (!mc.options.hideGui && settings.indicator) indicator(mc, graphics)
    }

    private fun argb(alpha: Float, rgb: Int): Int = (Mth.clamp((alpha * 255f).toInt(), 0, 255) shl 24) or (rgb and 0xFFFFFF)

    private fun drawLook(graphics: GuiGraphics, look: Look, strength: Float) {
        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        if (look.tintAlpha > 0f) {
            graphics.fill(0, 0, width, height, argb(look.tintAlpha * strength, look.tint))
            graphics.flush()
        }
        val dark = Mth.clamp(look.dark * strength, 0f, 1f)
        val glow = Mth.clamp(look.glowAlpha * strength, 0f, 1f)
        if (dark <= 0.005f && glow <= 0.005f) return
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.enableBlend()
        if (dark > 0.005f) {
            // Minecraft's own vignette: darker edges.
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR)
            graphics.setColor(dark, dark, dark, 1f)
            graphics.blit(VIGNETTE, 0, 0, -90, 0f, 0f, width, height, width, height)
        }
        if (glow > 0.005f) {
            // The same vignette added in the weather's colour: glowing edges (heat, poison, electricity).
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE)
            graphics.setColor(((look.glow shr 16) and 255) / 255f * glow, ((look.glow shr 8) and 255) / 255f * glow, (look.glow and 255) / 255f * glow, 1f)
            graphics.blit(VIGNETTE, 0, 0, -90, 0f, 0f, width, height, width, height)
        }
        graphics.setColor(1f, 1f, 1f, 1f)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
    }

    private val icons = HashMap<UltraWeatherEvent, ItemStack>()

    private fun icon(event: UltraWeatherEvent): ItemStack? {
        val shield = event.shield ?: return null
        return icons.getOrPut(event) { ItemStack(BuiltInRegistries.ITEM.get(GenerationsCore.id(shield.itemName))) }
    }

    /** Top left: the weather, and for a dangerous one where the player stands. */
    private fun indicator(mc: Minecraft, graphics: GuiGraphics) {
        val active = active() ?: return
        val event = active.event
        val lines = ArrayList<Component>()
        lines.add(event.displayName())
        if (!event.dangerous) {
            if (event != shownEvent || ticks > shownUntil) return
            lines.add(Component.translatable("generations_core.ultra_weather.hud.harmless").withStyle(ChatFormatting.GRAY))
        } else {
            val status = if (hazardEvent == event) hazardStatus else UltraHazardStatus.NONE
            when (status) {
                UltraHazardStatus.NONE -> {}
                UltraHazardStatus.GRACE -> lines.add(Component.translatable("generations_core.ultra_weather.hud.grace", hazardSeconds).withStyle(ChatFormatting.GOLD))
                UltraHazardStatus.SHELTERED -> lines.add(Component.translatable("generations_core.ultra_weather.hud.sheltered").withStyle(ChatFormatting.GREEN))
                UltraHazardStatus.SHIELDED -> lines.add(Component.translatable("generations_core.ultra_weather.hud.shielded", event.shield?.displayName() ?: Component.empty()).withStyle(ChatFormatting.AQUA))
                UltraHazardStatus.PARTIAL -> lines.add(Component.translatable("generations_core.ultra_weather.hud.partial", hazardPieces).withStyle(ChatFormatting.YELLOW))
                UltraHazardStatus.EXPOSED -> lines.add(Component.translatable("generations_core.ultra_weather.hud.exposed").withStyle(ChatFormatting.RED))
            }
            if (status == UltraHazardStatus.EXPOSED || status == UltraHazardStatus.GRACE || status == UltraHazardStatus.PARTIAL) {
                val shelter = event.shelter ?: UltraShelter.ROOF
                lines.add(Component.translatable("generations_core.ultra_weather.hud.shelter.${shelter.name.lowercase()}").withStyle(ChatFormatting.GRAY))
            }
        }
        val font = mc.font
        val icon = if (event.dangerous) icon(event) else null
        val textX = 6 + (if (icon != null) 20 else 0)
        val width = lines.maxOf { font.width(it) } + (textX - 6)
        val height = maxOf(lines.size * 10, if (icon != null) 16 else 0)
        graphics.fill(3, 3, 6 + width + 3, 6 + height + 2, 0x90000000.toInt())
        icon?.let { graphics.renderItem(it, 6, 6 + (height - 16) / 2) }
        lines.forEachIndexed { index, line -> graphics.drawString(font, line, textX, 7 + index * 10, 0xFFFFFF, true) }
    }
}
