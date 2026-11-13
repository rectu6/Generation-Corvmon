package generations.gg.generations.core.generationscore.common.mixin.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During this player's Ultra Warp Ride, the fog is the tunnel's: its dark colour, from
 * fadeStart to fadeEnd ahead, so that the far tunnel sinks into it. Recomputed by
 * Minecraft every frame, so nothing is left once the ride ends. Shader packs make
 * their own fog; the tunnel also darkens its far walls itself.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow private static float fogRed;

    @Shadow private static float fogGreen;

    @Shadow private static float fogBlue;

    @Inject(method = "setupColor", at = @At("RETURN"))
    private static void generations$ultraWarpColour(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, CallbackInfo ci) {
        if (!UltraWarpRideClient.isActive()) return;
        int colour = UltraWarpRideClient.fogColor();
        fogRed = ((colour >> 16) & 0xFF) / 255.0F;
        fogGreen = ((colour >> 8) & 0xFF) / 255.0F;
        fogBlue = (colour & 0xFF) / 255.0F;
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void generations$ultraWarpFog(Camera camera, FogRenderer.FogMode mode, float renderDistance, boolean thickFog, float partialTick, CallbackInfo ci) {
        if (!UltraWarpRideClient.isActive()) return;
        RenderSystem.setShaderFogStart(UltraWarpRideClient.fogStart());
        RenderSystem.setShaderFogEnd(UltraWarpRideClient.fogEnd());
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
}
