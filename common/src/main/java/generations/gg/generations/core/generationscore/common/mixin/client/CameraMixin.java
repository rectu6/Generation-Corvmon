package generations.gg.generations.core.generationscore.common.mixin.client;

import generations.gg.generations.core.generationscore.common.client.ultraspace.UltraWarpRideClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During this player's Ultra Warp Ride, the camera is the ride's: at an empty point
 * high above the world, turned like the ride's camera (roll included, which the
 * yaw and pitch alone cannot give). Detached, so that neither Minecraft nor Iris
 * draws the hand. Only this client's camera, only while the ride lasts: the next
 * frame after it is Minecraft's own again.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(Vec3 pos);

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow @Final private Quaternionf rotation;

    @Shadow @Final private Vector3f forwards;

    @Shadow @Final private Vector3f up;

    @Shadow @Final private Vector3f left;

    @Shadow private boolean detached;

    @Inject(method = "setup", at = @At("RETURN"))
    private void generations$ultraWarpRide(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!UltraWarpRideClient.update(partialTick)) return;
        this.setPosition(UltraWarpRideClient.anchor());
        this.setRotation(UltraWarpRideClient.yaw(), UltraWarpRideClient.pitch());
        Quaternionf view = UltraWarpRideClient.view();
        this.rotation.set(view);
        this.forwards.set(0.0F, 0.0F, -1.0F).rotate(view);
        this.up.set(0.0F, 1.0F, 0.0F).rotate(view);
        this.left.set(-1.0F, 0.0F, 0.0F).rotate(view);
        this.detached = true;
    }

    /**
     * During a legendary cinematic: the scene's camera (CinematicClient), mixed with this one
     * while it fades in and out. Detached once away from the player's eyes (their body seen).
     */
    @Inject(method = "setup", at = @At("RETURN"))
    private void generations$legendaryCinematic(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClient.updateCamera((Camera) (Object) this, partialTick)) return;
        this.setPosition(generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClient.cameraPosition());
        this.setRotation(generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClient.cameraYaw(), generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClient.cameraPitch());
        Quaternionf view = generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClient.cameraRotation();
        this.rotation.set(view);
        this.forwards.set(0.0F, 0.0F, -1.0F).rotate(view);
        this.up.set(0.0F, 1.0F, 0.0F).rotate(view);
        this.left.set(-1.0F, 0.0F, 0.0F).rotate(view);
        this.detached = this.detached || generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClient.cameraDetached();
    }
}
