package generations.gg.generations.core.generationscore.common.client.ultraspace

import generations.gg.generations.core.generationscore.common.world.ultraspace.ride.UltraWarpCourse
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

/** Sounds of the journey, for this player only (not placed in the world). Vanilla sounds. */
object UltraWarpSounds {
    fun play(sound: SoundEvent, pitch: Float, volume: Float) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(sound, pitch, volume))
    }

    fun storm() {
        play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 0.35f)
    }

    fun thunder() {
        play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2f, 0.18f)
    }

    fun current() {
        play(SoundEvents.BEACON_ACTIVATE, 1.6f, 0.8f)
    }

    fun anomaly() {
        play(SoundEvents.ENDERMAN_TELEPORT, 0.5f, 0.7f)
    }

    fun necrozma() {
        play(SoundEvents.BEACON_POWER_SELECT, 0.6f, 0.9f)
        play(SoundEvents.ENDER_DRAGON_GROWL, 1.4f, 0.35f)
    }

    fun portal() {
        play(SoundEvents.PORTAL_TRIGGER, 1.6f, 0.4f)
        play(SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.8f)
    }

    fun defeat() {
        play(SoundEvents.BEACON_DEACTIVATE, 0.6f, 1f)
        play(SoundEvents.END_PORTAL_SPAWN, 1.4f, 0.5f)
    }

    fun touched(kind: UltraWarpCourse.Kind) {
        when (kind) {
            UltraWarpCourse.Kind.ORB -> {
                play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3f, 0.7f)
                play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 0.9f)
            }
            UltraWarpCourse.Kind.SPARK -> play(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.6f, 0.45f)
            UltraWarpCourse.Kind.DEBRIS -> {
                play(SoundEvents.AMETHYST_BLOCK_BREAK, 0.7f, 1f)
                play(SoundEvents.GLASS_BREAK, 0.8f, 0.6f)
            }
            UltraWarpCourse.Kind.ARC -> play(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.1f, 0.6f)
            UltraWarpCourse.Kind.GRAVITY -> {}
        }
    }
}
