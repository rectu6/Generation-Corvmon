package generations.gg.generations.core.generationscore.common.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.RareCandyShadows;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Exposes the buffers an entity is rendered into. RareCandyBone only gets a
 * VertexConsumer, and Iris's shadow pass passes its own buffers here.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void generations$pushBuffers(E entity, double x, double y, double z, float rotationYaw,
                                                           float partialTicks, PoseStack poseStack,
                                                           MultiBufferSource buffers, int packedLight, CallbackInfo ci) {
        RareCandyShadows.pushEntityBuffers(buffers);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends Entity> void generations$popBuffers(E entity, double x, double y, double z, float rotationYaw,
                                                          float partialTicks, PoseStack poseStack,
                                                          MultiBufferSource buffers, int packedLight, CallbackInfo ci) {
        RareCandyShadows.popEntityBuffers();
    }

    /** The real Pokémon stays hidden while a legendary cinematic's actor stands for it (lot 5). */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void generations$cinematicActor(E entity, net.minecraft.client.renderer.culling.Frustum frustum, double camX, double camY, double camZ,
                                                             org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClient.hides(entity)) cir.setReturnValue(false);
    }
}
