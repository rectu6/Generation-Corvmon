package generations.gg.generations.core.generationscore.common.client.render.rarecandy;

import generations.gg.generations.core.generationscore.common.GenerationsCore;
import generations.gg.generations.core.generationscore.common.client.GenerationsTextureLoader;
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.loading.GenerationsGLModel;
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.loading.RareCandyShadowMesh;
import gg.generations.rarecandy.pokeutils.BlendType;
import gg.generations.rarecandy.renderer.animation.AnimationController;
import gg.generations.rarecandy.renderer.animation.Transform;
import gg.generations.rarecandy.renderer.components.MeshObject;
import gg.generations.rarecandy.renderer.components.MultiRenderObject;
import gg.generations.rarecandy.renderer.model.material.Material;
import gg.generations.rarecandy.renderer.rendering.ObjectInstance;
import gg.generations.rarecandy.renderer.storage.AnimatedObjectInstance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Makes RareCandy models cast shadows with an Iris shader pack.
 *
 * <p>Iris builds its shadow map by rendering entities and block entities a second
 * time from the sun, through Minecraft's vertex pipeline, with the pack's shadow
 * program. RareCandy draws with its own OpenGL program, outside that pipeline, so
 * its models never reached the shadow map. During that pass, the model is skinned
 * on the CPU ({@code RareCandyShadowMesh}) and written to the shadow buffers like
 * any vanilla entity, which lets each pack apply its own shadow projection.
 */
public final class RareCandyShadows {
    private static final Logger LOGGER = LoggerFactory.getLogger("Generations/IrisShadows");
    /** IrisApi.getInstance()::isRenderingShadowPass, or null without Iris. */
    private static final MethodHandle IS_SHADOW_PASS = findShadowPassCheck();
    private static boolean apiBroken;
    /** Set after an unexpected failure; the shadow pass is still detected, nothing is drawn. */
    private static boolean emitBroken;

    /** Buffer sources of the entities being rendered (EntityRenderDispatcherMixin). */
    private static MultiBufferSource[] entityBuffers = new MultiBufferSource[8];
    private static int entityDepth;

    /**
     * Callers store the pose for the current pass in the instance before
     * CompiledModel.render. RareCandy keeps drawing an instance for a moment after
     * its last use, so the shadow pose must not stay in it.
     */
    private static final Map<ObjectInstance, Matrix4f> MAIN_PASS_POSES = new WeakHashMap<>();
    private static final Matrix3f NORMAL = new Matrix3f();
    /** Blocks from the camera beyond which shadows use RareCandyShadowMesh.far(). */
    private static final float FAR_SHADOW_DISTANCE = 12;

    private RareCandyShadows() {
    }

    public static boolean isShadowPass() {
        if (IS_SHADOW_PASS == null || apiBroken) return false;
        try {
            return (boolean) IS_SHADOW_PASS.invokeExact();
        } catch (Throwable e) {
            apiBroken = true;
            LOGGER.warn("Could not query Iris's shadow pass, RareCandy shadows disabled", e);
            return false;
        }
    }

    public static void pushEntityBuffers(MultiBufferSource buffers) {
        if (entityDepth == entityBuffers.length) {
            entityBuffers = Arrays.copyOf(entityBuffers, entityDepth * 2);
        }
        entityBuffers[entityDepth++] = buffers;
    }

    public static void popEntityBuffers() {
        if (entityDepth > 0) entityBuffers[--entityDepth] = null;
    }

    /** Called for every regular (non-shadow) render of a RareCandy instance. */
    public static void rememberMainPassPose(ObjectInstance instance) {
        if (IS_SHADOW_PASS == null) return;
        MAIN_PASS_POSES.computeIfAbsent(instance, key -> new Matrix4f()).set(instance.transformationMatrix());
    }

    /**
     * Writes the instance to Iris's shadow buffers. Its transformation matrix holds
     * the shadow-pass pose set by the caller and is restored afterwards.
     *
     * @param buffers buffers handed to the caller; entities use the ones captured
     *                around EntityRenderDispatcher.render instead, since
     *                RareCandyBone only receives a single VertexConsumer.
     */
    public static void renderShadow(MultiRenderObject<MeshObject> object, ObjectInstance instance, MultiBufferSource buffers) {
        try {
            float distanceSq = cameraDistanceSq(instance);
            int maxDistance = GenerationsCore.CONFIG.client.shaderShadowDistance;
            boolean inRange = maxDistance <= 0 || distanceSq <= (float) maxDistance * maxDistance;
            if (GenerationsCore.CONFIG.client.shaderShadows && !emitBroken && inRange) {
                if (entityDepth > 0 && entityBuffers[entityDepth - 1] != null) buffers = entityBuffers[entityDepth - 1];
                emit(object, instance, buffers, distanceSq > FAR_SHADOW_DISTANCE * FAR_SHADOW_DISTANCE);
            }
        } catch (RuntimeException e) {
            emitBroken = true;
            LOGGER.warn("Failed to cast a RareCandy shadow, RareCandy shadows disabled", e);
        } finally {
            Matrix4f mainPose = MAIN_PASS_POSES.get(instance);
            if (mainPose != null) instance.transformationMatrix().set(mainPose);
        }
    }

    /**
     * Squared distance to the camera, from the last regular pose (camera-relative).
     * Shadows cost CPU time per vertex, so far models use a coarser mesh and the
     * farthest are skipped: their shadow is a few texels of the shadow map. A model
     * never drawn on screen has no such pose; its shadow may still be on screen, so
     * it counts as far (coarse mesh) but within range.
     */
    private static float cameraDistanceSq(ObjectInstance instance) {
        Matrix4f mainPose = MAIN_PASS_POSES.get(instance);
        if (mainPose == null) return FAR_SHADOW_DISTANCE * FAR_SHADOW_DISTANCE + 1;
        float x = mainPose.m30(), y = mainPose.m31(), z = mainPose.m32();
        return x * x + y * y + z * z;
    }

    private static void emit(MultiRenderObject<MeshObject> object, ObjectInstance instance, MultiBufferSource buffers,
                             boolean far) {
        Matrix4f pose = instance.transformationMatrix();
        pose.normal(NORMAL);

        Matrix4f[] bones = instance instanceof AnimatedObjectInstance animated ? animated.getTransforms() : null;
        if (bones == null) bones = AnimationController.NO_ANIMATION;

        String variant = instance.variant();
        for (MeshObject mesh : object.objects) {
            if (!(mesh.model instanceof GenerationsGLModel model)) continue;
            // Despite its name, RareCandy's predicate returns true for hidden meshes.
            if (mesh.shouldRender(instance)) continue;

            Material material = mesh.getMaterial(variant);
            // Only opaque materials: blended ones (glows, effects) should not darken the ground.
            if (material == null || material.blendType() != BlendType.None) continue;

            RareCandyShadowMesh shadowMesh = model.shadowMesh();
            if (shadowMesh == null) continue;
            if (far) shadowMesh = shadowMesh.far();

            Transform uv = uvTransform(instance, mesh, material, variant);
            Vector2f scale = uv != null && uv.scale() != null ? uv.scale() : null;
            Vector2f offset = uv != null && uv.offset() != null ? uv.offset() : null;

            var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture(instance, material)));
            shadowMesh.emit(consumer, pose, NORMAL, bones,
                    scale != null ? scale.x() : 1, scale != null ? scale.y() : 1,
                    offset != null ? offset.x() : 0, offset != null ? offset.y() : 0);
        }
    }

    /** Same choice as the uvScale/uvOffset uniforms in Pipelines.createShader. */
    private static Transform uvTransform(ObjectInstance instance, MeshObject mesh, Material material, String variant) {
        if (instance instanceof AnimatedObjectInstance animated) {
            Transform transform = animated.getTransform(material.getMaterialName());
            if (transform != null && !transform.isUnit()) return transform;
        }
        return mesh.getTransform(variant);
    }

    /** Same texture as the "diffuse" sampler in Pipelines.createShader, for the pack's alpha cutout. */
    private static ResourceLocation texture(ObjectInstance instance, Material material) {
        var loader = GenerationsTextureLoader.INSTANCE;
        String name = material.images().getDiffuse();
        if (instance instanceof StatueInstance statue && statue.getMaterial() != null && loader.has(statue.getMaterial())) {
            name = statue.getMaterial();
        }
        ResourceLocation location = name != null ? loader.getLocation(name) : null;
        return location != null ? location : MissingTextureAtlasSprite.getLocation();
    }

    private static MethodHandle findShadowPassCheck() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup()
                    .findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unsupported Iris API, RareCandy models will not cast shadows", e);
            return null;
        }
    }
}
