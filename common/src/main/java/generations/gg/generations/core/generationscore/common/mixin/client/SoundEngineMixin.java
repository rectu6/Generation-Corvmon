package generations.gg.generations.core.generationscore.common.mixin.client;

import generations.gg.generations.core.generationscore.common.client.cinematic.audio.CinematicDuck;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lugia's silence of the depths (LUGIA-PLAN.md § 5): while a scene asks for it, Minecraft's
 * ambience, weather, music and creatures are quieter on the watcher's client (CinematicDuck).
 * Every sound's volume goes through calculateVolume(float, SoundSource): played, ticked, or
 * refreshed by updateCategoryVolume. The cinematics' own sounds (MASTER), blocks (the shrine's
 * song) and players are not touched.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
    private void generations$cinematicDuck(float volume, SoundSource source, CallbackInfoReturnable<Float> cir) {
        float factor = CinematicDuck.factor(source);
        if (factor < 1f) cir.setReturnValue(cir.getReturnValueF() * factor);
    }
}
