package generations.gg.generations.core.generationscore.common.client.boss

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.render.DynamaxCloudRenderer
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.RareCandyShadows
import generations.gg.generations.core.generationscore.common.world.boss.BossMegas
import generations.gg.generations.core.generationscore.common.world.boss.BossRarity
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.client.ParticleStatus
import net.minecraft.util.Mth
import org.joml.Vector3f
import java.util.Random
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * What a boss looks like on the client, from its synced aspects only (BossRarity.aspect,
 * TITAN_ASPECT): nothing is sent per tick.
 * - Name label: "Charizard [Epic Boss]" in the rarity's colour (PokemonRendererMixin).
 * - Aura, drawn with the entity (PokemonRendererMixin, at the end of its render, as the
 *   Dynamax clouds): a glow on the ground, a light rising around it from Uncommon, a
 *   red outer ring for a Titan. Translucent, emissive, no depth written: the shader
 *   (Iris) draws it with the other entities; it is left out of the shadow pass.
 * - Particles of the rarity's colour, a number per tick by rarity, at most once per
 *   tick per boss, only when it is drawn (in sight) and near the camera.
 * Settings in boss_client.json (BossClientConfig).
 */
object BossClient {
    private val TEXTURE = GenerationsCore.id("textures/entity/boss_aura.png")
    private const val RING_SEGMENTS = 48
    private const val SHELL_SEGMENTS = 32

    /** Aura opacity by rarity (common → legendary). */
    private val AURA_ALPHA = floatArrayOf(0.30f, 0.38f, 0.46f, 0.55f, 0.65f)
    /** Particles per tick by rarity, for a Pokémon up to 1 block wide. */
    private val PARTICLES = floatArrayOf(0.12f, 0.25f, 0.45f, 0.75f, 1.2f)

    private val random = Random()
    private val lastParticleTick = WeakHashMap<PokemonEntity, Int>()

    @JvmStatic
    fun init() {
        BossClientConfig.load()
        // Edits of boss_client.json apply on joining a world.
        PlatformEvents.CLIENT_PLAYER_LOGIN.subscribe(Priority.NORMAL) { BossClientConfig.load() }
        PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe(Priority.NORMAL) { lastParticleTick.clear() }
    }

    // ---- Name ---------------------------------------------------------------------

    @JvmStatic
    fun label(entity: PokemonEntity, base: MutableComponent?): MutableComponent? {
        val aspects = entity.aspects
        val rarity = BossRarity.fromAspects(aspects) ?: return base
        val settings = BossClientConfig.settings
        if (!settings.label.enabled) return base
        val titan = BossRarity.TITAN_ASPECT in aspects
        val style = Style.EMPTY.withColor(settings.color(rarity)).withBold(rarity == BossRarity.LEGENDARY)
        val tier = Component.translatable(rarity.tierKey)
        if (titan) tier.append(Component.literal(" ")).append(Component.translatable("generations_core.boss.titan"))
        if (base == null || (base.contents == PlainTextContents.EMPTY && base.siblings.isEmpty())) return tier.withStyle(style)
        // A Legendary boss is a Mega form: "Mega Charizard Y".
        val mega = aspects.firstOrNull(BossMegas::isMegaAspect)
        val name = if (mega != null) BossMegas.name(base, mega) else base
        return Component.translatable("generations_core.boss.label", name, tier).withStyle(style)
    }

    // ---- Aura and particles -------------------------------------------------------

    @JvmStatic
    fun render(entity: PokemonEntity, partialTicks: Float, pose: PoseStack, buffers: MultiBufferSource) {
        val aspects = entity.aspects
        val rarity = BossRarity.fromAspects(aspects) ?: return
        if (entity.isInvisible || RareCandyShadows.isShadowPass()) return
        val settings = BossClientConfig.settings
        val minecraft = Minecraft.getInstance()
        val camera = minecraft.gameRenderer.mainCamera.position
        val distance = entity.position().distanceToSqr(camera)
        val auraOn = settings.aura.enabled && settings.aura.opacity > 0f && distance <= Mth.square(settings.aura.maxDistance.toDouble())
        val particlesOn = settings.particles.enabled && settings.particles.density > 0f && distance <= Mth.square(settings.particles.maxDistance.toDouble())
        if (!auraOn && !particlesOn) return

        val titan = BossRarity.TITAN_ASPECT in aspects
        val (height, halfWidth) = DynamaxCloudRenderer.size(entity, pose)
        val half = halfWidth.coerceIn(0.2f, 24f)
        val time = (entity.tickCount + partialTicks) / 20f
        if (auraOn) aura(rarity, titan, height, half, time, pose, buffers)
        if (particlesOn) particles(entity, rarity, titan, height, half, partialTicks)
    }

    private fun aura(rarity: BossRarity, titan: Boolean, height: Float, half: Float, time: Float, pose: PoseStack, buffers: MultiBufferSource) {
        val settings = BossClientConfig.settings
        val consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE))
        var rgb = rgb(settings.color(rarity))
        if (rarity == BossRarity.LEGENDARY) {
            // A slow shimmer towards white.
            val shine = 0.2f * (0.5f + 0.5f * sin(time * 1.7f))
            rgb = FloatArray(3) { rgb[it] + (1f - rgb[it]) * shine }
        }
        val pulse = 0.82f + 0.18f * sin(time * 2.2f)
        val alpha = (AURA_ALPHA[rarity.ordinal] * settings.aura.opacity * pulse).coerceIn(0f, 1f)
        val inner = half * 0.8f
        val outer = half * 1.35f + 0.35f + 0.08f * rarity.ordinal
        val spin = time * 0.04f

        if (settings.aura.groundRing) {
            band(pose, consumer, 0f, inner, 0.03f, rgb, alpha * 0.35f, alpha, spin)
            band(pose, consumer, inner, outer, 0.03f, rgb, alpha, 0f, spin)
        }
        if (settings.aura.shell && rarity != BossRarity.COMMON) {
            val radius = half * 1.05f + 0.2f
            val shellHeight = (height * (0.3f + 0.1f * rarity.ordinal)).coerceIn(0.6f, 20f)
            shell(pose, consumer, radius, shellHeight, rgb, alpha * 0.8f, time * 0.08f)
        }
        if (titan) {
            val titanRgb = rgb(settings.titanColor)
            val middle = outer * 1.2f + 0.2f
            val width = 0.15f + half * 0.1f
            val titanAlpha = (0.6f * settings.aura.opacity * (0.75f + 0.25f * sin(time * 3.1f))).coerceIn(0f, 1f)
            band(pose, consumer, middle - width, middle, 0.035f, titanRgb, 0f, titanAlpha, -spin)
            band(pose, consumer, middle, middle + width, 0.035f, titanRgb, titanAlpha, 0f, -spin)
        }
    }

    /** A flat ring on the ground, from radius `from` (alpha a) to `to` (alpha b). */
    private fun band(pose: PoseStack, consumer: VertexConsumer, from: Float, to: Float, y: Float, rgb: FloatArray, a: Float, b: Float, scroll: Float) {
        if (a <= 0f && b <= 0f) return
        val last = pose.last()
        for (i in 0 until RING_SEGMENTS) {
            val a0 = 2f * PI.toFloat() * i / RING_SEGMENTS
            val a1 = 2f * PI.toFloat() * (i + 1) / RING_SEGMENTS
            val u0 = i * 4f / RING_SEGMENTS + scroll
            val u1 = (i + 1) * 4f / RING_SEGMENTS + scroll
            vertex(consumer, last, cos(a0) * from, y, sin(a0) * from, rgb, a, u0, 0f, 0f, 1f, 0f)
            vertex(consumer, last, cos(a0) * to, y, sin(a0) * to, rgb, b, u0, 1f, 0f, 1f, 0f)
            vertex(consumer, last, cos(a1) * to, y, sin(a1) * to, rgb, b, u1, 1f, 0f, 1f, 0f)
            vertex(consumer, last, cos(a1) * from, y, sin(a1) * from, rgb, a, u1, 0f, 0f, 1f, 0f)
        }
    }

    /** Light rising around the boss: a cylinder, bright at the bottom, gone at the top. */
    private fun shell(pose: PoseStack, consumer: VertexConsumer, radius: Float, height: Float, rgb: FloatArray, alpha: Float, scroll: Float) {
        if (alpha <= 0f) return
        val last = pose.last()
        for (i in 0 until SHELL_SEGMENTS) {
            val a0 = 2f * PI.toFloat() * i / SHELL_SEGMENTS
            val a1 = 2f * PI.toFloat() * (i + 1) / SHELL_SEGMENTS
            val u0 = i * 3f / SHELL_SEGMENTS + scroll
            val u1 = (i + 1) * 3f / SHELL_SEGMENTS + scroll
            val x0 = cos(a0)
            val z0 = sin(a0)
            val x1 = cos(a1)
            val z1 = sin(a1)
            vertex(consumer, last, x0 * radius, 0f, z0 * radius, rgb, alpha, u0, 1f, x0, 0f, z0)
            vertex(consumer, last, x0 * radius, height, z0 * radius, rgb, 0f, u0, 0f, x0, 0f, z0)
            vertex(consumer, last, x1 * radius, height, z1 * radius, rgb, 0f, u1, 0f, x1, 0f, z1)
            vertex(consumer, last, x1 * radius, 0f, z1 * radius, rgb, alpha, u1, 1f, x1, 0f, z1)
        }
    }

    private fun vertex(
        consumer: VertexConsumer, last: PoseStack.Pose, x: Float, y: Float, z: Float,
        rgb: FloatArray, alpha: Float, u: Float, v: Float, nx: Float, ny: Float, nz: Float,
    ) {
        consumer.addVertex(last, x, y, z)
            .setColor(rgb[0], rgb[1], rgb[2], alpha)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(last, nx, ny, nz)
    }

    private fun particles(entity: PokemonEntity, rarity: BossRarity, titan: Boolean, height: Float, half: Float, partialTicks: Float) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.isPaused) return
        // Once per tick, however many times the entity is drawn.
        if (lastParticleTick.put(entity, entity.tickCount) == entity.tickCount) return
        val status = minecraft.options.particles().get()
        if (status == ParticleStatus.MINIMAL) return
        val level = entity.level()
        val settings = BossClientConfig.settings
        val sizeFactor = (half * 2f).coerceIn(1f, 4f)
        val factor = settings.particles.density * sizeFactor * (if (status == ParticleStatus.DECREASED) 0.5f else 1f)
        val x = Mth.lerp(partialTicks.toDouble(), entity.xo, entity.x)
        val y = Mth.lerp(partialTicks.toDouble(), entity.yo, entity.y)
        val z = Mth.lerp(partialTicks.toDouble(), entity.zo, entity.z)
        val radius = half * 1.05f + 0.2f
        val rise = height.coerceIn(0.5f, 20f)

        val color = rgb(settings.color(rarity))
        val dust = DustParticleOptions(Vector3f(color[0], color[1], color[2]), 0.8f + 0.1f * rarity.ordinal)
        repeat(count(PARTICLES[rarity.ordinal] * factor)) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val r = radius * (0.6f + 0.5f * random.nextFloat())
            level.addParticle(dust, x + cos(angle) * r, y + random.nextFloat() * rise * 0.4f, z + sin(angle) * r, 0.0, 0.03, 0.0)
        }
        if (rarity.ordinal >= BossRarity.EPIC.ordinal) {
            repeat(count(0.15f * factor * (rarity.ordinal - 2))) {
                val angle = random.nextFloat() * 2f * PI.toFloat()
                val r = radius * random.nextFloat()
                level.addParticle(ParticleTypes.END_ROD, x + cos(angle) * r, y + 0.1, z + sin(angle) * r, 0.0, 0.03 + 0.03 * random.nextDouble(), 0.0)
            }
        }
        if (titan) {
            val titanColor = rgb(settings.titanColor)
            val titanDust = DustParticleOptions(Vector3f(titanColor[0], titanColor[1], titanColor[2]), 1.4f)
            repeat(count(0.35f * factor)) {
                val angle = random.nextFloat() * 2f * PI.toFloat()
                val r = radius * (1.1f + 0.4f * random.nextFloat())
                level.addParticle(titanDust, x + cos(angle) * r, y + 0.1 + random.nextFloat() * 0.4, z + sin(angle) * r, 0.0, 0.02, 0.0)
            }
        }
    }

    /** A whole number of particles for a mean rate (the fraction drawn at random). */
    private fun count(rate: Float): Int {
        if (rate <= 0f) return 0
        val whole = rate.toInt()
        return (whole + if (random.nextFloat() < rate - whole) 1 else 0).coerceAtMost(16)
    }

    private fun rgb(color: Int) = floatArrayOf(((color shr 16) and 0xFF) / 255f, ((color shr 8) and 0xFF) / 255f, (color and 0xFF) / 255f)
}
