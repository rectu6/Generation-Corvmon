package generations.gg.generations.core.generationscore.common.mixin.client;

import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import generations.gg.generations.core.generationscore.common.client.render.DynamaxCloudRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dynamaxed Pokémon: a pulsing red tint on the model and the red Dynamax clouds
 * above its head (see DynamaxCloudRenderer). RareCandy models get their red glow
 * from Pipelines.
 */
@Mixin(PokemonRenderer.class)
public abstract class PokemonRendererMixin {
    @Inject(
            method = "render(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableModel;setLayerContext(Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;Ljava/lang/Iterable;)V",
                    remap = false
            )
    )
    private void generations$dynamaxTint(PokemonEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        DynamaxCloudRenderer.INSTANCE.tint(entity, ((PokemonRenderer) (Object) this).getModel().getPosableModel(), partialTicks);
    }

    @Inject(
            method = "render(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL")
    )
    private void generations$dynamaxClouds(PokemonEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        DynamaxCloudRenderer.INSTANCE.render(entity, partialTicks, pose, buffers);
    }
}
