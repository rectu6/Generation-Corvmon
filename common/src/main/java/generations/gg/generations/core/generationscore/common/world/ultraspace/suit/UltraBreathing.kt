package generations.gg.generations.core.generationscore.common.world.ultraspace.suit

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.platform.events.PlatformEvents
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraWarpSessions
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.FluidTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.effect.MobEffectUtil
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import java.util.UUID

/**
 * Dimensional suffocation (phase 4.4): in the seven Ultra worlds, the air can only be
 * breathed with the four pieces of the Ultra Suit. Without them the air reserve falls
 * (Minecraft's bubbles show), then the "ultra_suffocation" damage comes; with them it
 * comes back little by little. Always, whatever the weather; the shields do not replace
 * it. The Overworld, the Nether and the End are not touched (only the Ultra worlds' keys).
 *
 * Underwater, Minecraft's own drowning goes on (the air is not taken twice). The
 * recovery Minecraft gives each tick out of the water is held back by LivingEntityMixin
 * (blocksRecovery), on the server and, for the bubbles not to flicker, on the client.
 */
object UltraBreathing {
    val DAMAGE_TYPE: ResourceKey<DamageType> = ResourceKey.create(Registries.DAMAGE_TYPE, GenerationsCore.id("ultra_suffocation"))

    /** No loss until this server tick (after respawning or logging in inside an Ultra world). */
    private val graceUntil = HashMap<UUID, Int>()
    /** Ticks since the last damage, once the air is gone. */
    private val damageTicks = HashMap<UUID, Int>()
    /** Told that the air cannot be breathed, until breathing freely again. */
    private val warned = HashSet<UUID>()

    fun init() {
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { UltraSuitConfig.load() }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) {
            graceUntil.clear()
            damageTicks.clear()
            warned.clear()
        }
        PlatformEvents.SERVER_PLAYER_TICK_POST.subscribe(Priority.NORMAL) { tick(it.player) }
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe(Priority.NORMAL) { arrived(it.player, true) }
        PlatformEvents.PLAYER_RESPAWN.subscribe(Priority.NORMAL) { arrived(it.player, true) }
        PlatformEvents.CHANGE_DIMENSION.subscribe(Priority.NORMAL) { event -> event.destination?.takeIf { UltraWorlds.isUltra(it.dimension()) }?.let { arrived(event.player, false, it) } }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(Priority.NORMAL) {
            graceUntil.remove(it.player.uuid)
            damageTicks.remove(it.player.uuid)
            warned.remove(it.player.uuid)
        }
    }

    fun source(level: Level): DamageSource =
        DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE))

    /** Whether this player cannot breathe now (server side). */
    fun suffocates(player: ServerPlayer): Boolean {
        val rules = UltraSuitConfig.settings.breathing
        if (!rules.enabled || !UltraWorlds.isUltra(player.level().dimension())) return false
        if (player.isCreative || player.isSpectator || !player.isAlive) return false
        if (UltraSuit.fullSuit(player)) return false
        if (rules.waterBreathingProtects && MobEffectUtil.hasWaterBreathing(player)) return false
        if ((graceUntil[player.uuid] ?: -1) >= player.server.tickCount) return false
        // In the Ultra Warp Ride's tunnel, the player is not in the world.
        if (UltraWarpSessions.get(player.uuid)?.ride != null) return false
        return true
    }

    /** Seconds of grace left (0 when none). */
    fun graceSeconds(player: ServerPlayer): Int = (((graceUntil[player.uuid] ?: 0) - player.server.tickCount).coerceAtLeast(0) + 19) / 20

    /**
     * LivingEntityMixin: Minecraft's recovery of the air held back. On the server, while
     * the player suffocates; on the client, the same guess for the local player (its suit
     * and its world), so that the bubbles do not go up between two updates.
     */
    @JvmStatic
    fun blocksRecovery(entity: LivingEntity): Boolean {
        if (entity !is Player) return false
        if (entity is ServerPlayer) return suffocates(entity)
        val level = entity.level()
        return level.isClientSide && UltraWorlds.isUltra(level.dimension()) && !entity.isCreative && !entity.isSpectator && !UltraSuit.fullSuit(entity)
    }

    private fun tick(player: ServerPlayer) {
        val uuid = player.uuid
        if (!suffocates(player)) {
            damageTicks.remove(uuid)
            if (player.airSupply >= player.maxAirSupply) warned.remove(uuid)
            return
        }
        // Underwater without Water Breathing, Minecraft already takes the air (and drowns).
        val eye = BlockPos.containing(player.x, player.eyeY, player.z)
        if (player.isEyeInFluid(FluidTags.WATER) && !player.level().getBlockState(eye).`is`(Blocks.BUBBLE_COLUMN) &&
            !player.canBreatheUnderwater() && !MobEffectUtil.hasWaterBreathing(player)
        ) return

        val rules = UltraSuitConfig.settings.breathing
        val air = player.airSupply
        if (air > 0) {
            val bonus = player.getAttributeValue(Attributes.OXYGEN_BONUS).toInt()
            val slowed = rules.respirationSlows && bonus > 0 && player.random.nextInt(bonus + 1) > 0
            if (!slowed) player.airSupply = (air - rules.airLossPerTick).coerceAtLeast(0)
            damageTicks[uuid] = 0
        } else {
            if (player.airSupply < 0) player.airSupply = 0
            val ticks = (damageTicks[uuid] ?: 0) + 1
            if (ticks >= rules.damageIntervalTicks) {
                damageTicks[uuid] = 0
                if (rules.damage > 0f) player.hurt(source(player.level()), rules.damage)
            } else {
                damageTicks[uuid] = ticks
            }
        }
        if (warned.add(uuid)) {
            player.sendSystemMessage(Component.translatable("generations_core.ultra_suit.no_air").withStyle(ChatFormatting.RED))
        }
    }

    /**
     * In an Ultra world after a login, a respawn (grace: true) or a change of dimension:
     * the grace, and a word when the suit is not complete.
     */
    private fun arrived(player: ServerPlayer, grace: Boolean, level: Level = player.level()) {
        val rules = UltraSuitConfig.settings.breathing
        if (!rules.enabled || !UltraWorlds.isUltra(level.dimension())) return
        if (grace && rules.respawnGraceSeconds > 0) graceUntil[player.uuid] = player.server.tickCount + rules.respawnGraceSeconds * 20
        if (player.isCreative || player.isSpectator || UltraSuit.fullSuit(player) || !rules.warnOnArrival) return
        val message = if (grace && rules.respawnGraceSeconds > 0) {
            Component.translatable("generations_core.ultra_suit.grace", rules.respawnGraceSeconds)
        } else {
            Component.translatable("generations_core.ultra_suit.warning")
        }
        player.sendSystemMessage(message.withStyle(ChatFormatting.GOLD))
    }
}
