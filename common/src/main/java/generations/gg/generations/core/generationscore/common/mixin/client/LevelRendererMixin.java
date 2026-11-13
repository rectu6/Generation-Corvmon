/*
 * Copyright (C) 2023 ThePokeCraftMod
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package generations.gg.generations.core.generationscore.common.mixin.client;

import generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import generations.gg.generations.core.generationscore.common.client.GenerationsCoreClient;
import generations.gg.generations.core.generationscore.common.client.GenerationsCoreClientKt;
import generations.gg.generations.core.generationscore.common.client.render.RenderStateRecord;
import gg.generations.rarecandy.renderer.rendering.RenderStage;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDD)V"))
    private void pokecraft$firstPass(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        // The Ultra Warp Ride (walls, Pokémon, player) first: RareCandy draws its Pokémon in the pass below.
        // With a shader pack it is drawn after the pack's final pass instead (GameRendererMixin).
        if (UltraWarpRideClient.drawsAfterShaders()) {
            UltraWarpRideClient.keepMatrices(frustumMatrix, projectionMatrix);
        } else {
            UltraWarpRideClient.renderOpaque();
        }
        GenerationsCoreClient.INSTANCE.firstRenderPass();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getCloudsType()Lnet/minecraft/client/CloudStatus;"))
    private void pokecraft$secondPass(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (!UltraWarpRideClient.drawsAfterShaders()) UltraWarpRideClient.renderTranslucent();
        GenerationsCoreClient.INSTANCE.secondRenderPass();
        // The rifts between the Ultra worlds (phase 6).
        generations.gg.generations.core.generationscore.common.client.ultraspace.rift.UltraRiftRenderer.render(camera, deltaTracker.getGameTimeDeltaPartialTick(false), frustumMatrix, projectionMatrix);
    }


    /** No rain nor snow around the Ultra Warp Ride's camera (Iris frames the call itself, not this method's body). */
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void generations$ultraWarpWeather(LightTexture lightTexture, float partialTick, double x, double y, double z, CallbackInfo ci) {
        if (UltraWarpRideClient.isActive()) ci.cancel();
    }

    @Shadow @Final private static net.minecraft.resources.ResourceLocation RAIN_LOCATION;

    /**
     * The tropical and acid rains (phase 4.4): Minecraft's rain falls in the Ultra world
     * although its biomes have none (has_precipitation false), under the open sky only.
     * Not required: nothing breaks if another mod draws the weather itself.
     */
    @org.spongepowered.asm.mixin.injection.Redirect(method = "renderSnowAndRain", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;hasPrecipitation()Z"), require = 0)
    private boolean generations$ultraRainHere(net.minecraft.world.level.biome.Biome biome) {
        return generations.gg.generations.core.generationscore.common.client.ultraspace.weather.UltraWeatherClient.raining() || biome.hasPrecipitation();
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = {"renderSnowAndRain", "tickRain"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"), require = 0)
    private net.minecraft.world.level.biome.Biome.Precipitation generations$ultraRainKind(net.minecraft.world.level.biome.Biome biome, net.minecraft.core.BlockPos pos) {
        return generations.gg.generations.core.generationscore.common.client.ultraspace.weather.UltraWeatherClient.raining() ? net.minecraft.world.level.biome.Biome.Precipitation.RAIN : biome.getPrecipitationAt(pos);
    }

    /** The acid rain's own texture. */
    @org.spongepowered.asm.mixin.injection.Redirect(method = "renderSnowAndRain", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;RAIN_LOCATION:Lnet/minecraft/resources/ResourceLocation;", opcode = org.objectweb.asm.Opcodes.GETSTATIC), require = 0)
    private net.minecraft.resources.ResourceLocation generations$ultraRainTexture() {
        return generations.gg.generations.core.generationscore.common.client.ultraspace.weather.UltraWeatherClient.rainTexture(RAIN_LOCATION);
    }
}
