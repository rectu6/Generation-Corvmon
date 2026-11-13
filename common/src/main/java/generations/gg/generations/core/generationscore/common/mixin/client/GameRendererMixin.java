package generations.gg.generations.core.generationscore.common.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * During this player's Ultra Warp Ride: the ride's field of view (wider with the
 * speed), no view bobbing nor hurt tilt, no hand. Nothing is changed in the options.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void generations$ultraWarpFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        if (UltraWarpRideClient.isActive()) cir.setReturnValue(UltraWarpRideClient.fov());
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void generations$ultraWarpBob(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (UltraWarpRideClient.isActive()) ci.cancel();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void generations$ultraWarpHurt(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (UltraWarpRideClient.isActive()) ci.cancel();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void generations$ultraWarpHand(Camera camera, float partialTick, Matrix4f projection, CallbackInfo ci) {
        if (UltraWarpRideClient.isActive()) ci.cancel();
    }

    /**
     * With a shader pack, the journey over the pack's final image: right after the world's
     * render, where Iris has composited everything and no longer replaces any shader.
     */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
    private void generations$ultraWarpOverShaders(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        if (UltraWarpRideClient.isActive()) {
            UltraWarpRideClient.renderAfterShaders(((GameRenderer) (Object) this).getMainCamera(), deltaTracker.getGameTimeDeltaPartialTick(true));
        }
        // The rifts (phase 6), the same way: the pack's lights and shadows leave them alone.
        generations.gg.generations.core.generationscore.common.client.ultraspace.rift.UltraRiftRenderer.renderAfterShaders();
    }

    /** The heat's shimmer (phase 4.4): the field of view sways a little (ultra_weather_client.json, viewSway). */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void generations$ultraWeatherSway(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        if (!useFovSetting) return;
        double sway = generations.gg.generations.core.generationscore.common.client.ultraspace.weather.UltraWeatherClient.fovSway(partialTick);
        if (sway != 0.0) cir.setReturnValue(cir.getReturnValue() + sway);
    }
}
