package generations.gg.generations.core.generationscore.common.mixin.client;

import generations.gg.generations.core.generationscore.common.client.cinematic.audio.CinematicAudio;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * The legendary cinematics' music and ambience, made by their synthesizer: the stream of a
 * file that does not exist (generations_core:sounds/cinematic_synth/...), given from memory.
 * Vanilla has no SoundInstance.getStream (Fabric API adds one, common does not depend on it).
 * Any other file, and a resource pack's own music for the scene, load as usual.
 */
@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {
    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void generations$cinematicSynth(ResourceLocation location, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        AudioStream stream = CinematicAudio.takeStream(location);
        if (stream != null) cir.setReturnValue(CompletableFuture.completedFuture(stream));
    }
}
