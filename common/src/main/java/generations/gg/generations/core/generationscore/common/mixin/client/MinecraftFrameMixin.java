package generations.gg.generations.core.generationscore.common.mixin.client;

import generations.gg.generations.core.generationscore.common.client.perf.FirstUseProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frame boundaries for HdText's time budget per frame, and the first-use diagnostic
 * (FirstUseProfiler, option client.profileFirstUse): whole frames (ticks and render)
 * and screen openings.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void generations$frameStart(boolean renderLevel, CallbackInfo ci) {
        FirstUseProfiler.frameStart();
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void generations$frameEnd(boolean renderLevel, CallbackInfo ci) {
        FirstUseProfiler.frameEnd();
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void generations$screenOpening(Screen screen, CallbackInfo ci) {
        FirstUseProfiler.screenOpening(screen);
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void generations$screenOpened(Screen screen, CallbackInfo ci) {
        FirstUseProfiler.screenOpened(screen);
    }
}
