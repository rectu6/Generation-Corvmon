package generations.gg.generations.core.generationscore.common.mixin.client;

import generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient;

import generations.gg.generations.core.generationscore.common.client.screen.Overlays;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Shadow @Final protected Minecraft minecraft;

    /** No HUD during the Ultra Warp Ride (dev.hideHud in its client config). Before the line below. */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void generations$ultraWarpRide(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (UltraWarpRideClient.hidesHud()) ci.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"))
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Overlays.render(guiGraphics, deltaTracker);
        // The Ultra weathers' veil and the indicator of their dangers (phase 4.4).
        generations.gg.generations.core.generationscore.common.client.ultraspace.weather.UltraWeatherClient.renderOverlay(guiGraphics, deltaTracker);
    }
}
