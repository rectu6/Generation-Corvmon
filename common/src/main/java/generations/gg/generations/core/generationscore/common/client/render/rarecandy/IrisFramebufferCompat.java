package generations.gg.generations.core.generationscore.common.client.render.rarecandy;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sends RareCandy's world draws to the framebuffer Iris uses for entities.
 *
 * <p>With a shader pack active, Iris leaves Minecraft's main framebuffer bound
 * between its own programs and overwrites that framebuffer's colour in its final
 * pass. RareCandy draws with raw OpenGL, so its colour landed in the main
 * framebuffer and was thrown away, while its depth stayed in the depth texture
 * Iris shares with that framebuffer (depthtex0). The shader pack then saw a
 * surface with no colour of its own, which shows up as see-through models.
 *
 * <p>Iris is optional, so it is only reached through reflection, and any
 * mismatch turns this class into a no-op that keeps the previous behaviour.
 */
public final class IrisFramebufferCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("Generations/IrisCompat");
    private static final String IRIS_PACKAGE = "net.irisshaders.";

    /** Per Iris shader class (ExtendedShader, FallbackShader): fields before, after, parent. */
    private static final Map<Class<?>, Field[]> SHADER_FIELDS = new HashMap<>();
    private static Field beforeTranslucentFlag;
    private static Method framebufferId;
    private static boolean disabled;

    /** Values of the auxMode uniform in animated.fs.glsl. */
    public static final int AUX_NONE = 0, AUX_COMPLEMENTARY = 1;

    private static boolean bound;
    private static int previousDrawFramebuffer;
    private static int previousReadFramebuffer;
    private static int[] drawBuffers;
    private static int auxMode = AUX_NONE;
    private static Method currentPackName;
    private static boolean packNameUnavailable;
    private static Method pipelineManager, pipelineNullable, sunPath;
    private static boolean sunPathUnavailable;

    private IrisFramebufferCompat() {
    }

    /** Which extra pack targets animated.fs.glsl must fill during the current pass. */
    public static int auxMode() {
        return auxMode;
    }

    /** Whether Iris is rendering the world with a shader pack (it then replaces the entity shaders). */
    public static boolean isShaderPackActive() {
        ShaderInstance shader = GameRenderer.getRendertypeEntityCutoutNoCullShader();
        return shader != null && shader.getClass().getName().startsWith(IRIS_PACKAGE);
    }

    /**
     * The pack's sunPathRotation (degrees), which tilts the sun's path and so the
     * direction of its shadows: IrisRenderingPipeline.getSunPathRotation(). 0 if unknown.
     */
    public static float sunPathRotation() {
        if (sunPathUnavailable) return 0;
        try {
            if (pipelineManager == null) {
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                pipelineManager = iris.getMethod("getPipelineManager");
                pipelineNullable = pipelineManager.getReturnType().getMethod("getPipelineNullable");
            }
            Object pipeline = pipelineNullable.invoke(pipelineManager.invoke(null));
            if (pipeline == null) return 0;
            if (sunPath == null || sunPath.getDeclaringClass() != pipeline.getClass()) {
                sunPath = pipeline.getClass().getMethod("getSunPathRotation");
            }
            return ((Number) sunPath.invoke(pipeline)).floatValue();
        } catch (NoSuchMethodException e) {
            // Vanilla pipeline (shaders off): no tilt.
            return 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            sunPathUnavailable = true;
            LOGGER.warn("Could not read the shader pack's sun path rotation", e);
            return 0;
        }
    }

    /**
     * Binds the gbuffer framebuffer Iris's entity program would write to right now.
     * Does nothing without an active shader pack.
     *
     * @param solidPass the opaque pass; the extra targets are only filled there
     */
    public static void begin(boolean solidPass) {
        auxMode = AUX_NONE;
        if (disabled || bound) return;

        ShaderInstance shader = GameRenderer.getRendertypeEntityCutoutNoCullShader();
        // Without a shader pack, or in vanilla mode, Iris returns the vanilla shader.
        if (shader == null || !shader.getClass().getName().startsWith(IRIS_PACKAGE)) return;

        int target;
        try {
            target = findFramebuffer(shader);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            LOGGER.warn("Unsupported Iris version, RareCandy models keep the default framebuffer", e);
            return;
        }

        previousDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        previousReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, target);

        drawBuffers = new int[GL11C.glGetInteger(GL20C.GL_MAX_DRAW_BUFFERS)];
        for (int i = 0; i < drawBuffers.length; i++) {
            drawBuffers[i] = GL11C.glGetInteger(GL20C.GL_DRAW_BUFFER0 + i);
        }

        // Output 0 is the pack's main colour target. The others hold per-pack data
        // (normals, material flags read by TAA, SSAO, reflections) whose meaning
        // RareCandy cannot know, except for the layout below. Elsewhere they are
        // left untouched: undefined values would be worse than the terrain's.
        if (solidPass && isComplementaryEntityLayout(drawBuffers) && isComplementaryPack()) {
            auxMode = AUX_COMPLEMENTARY;
        } else {
            GL20C.glDrawBuffers(drawBuffers[0]);
        }

        bound = true;
    }

    /** Restores the Iris framebuffer's draw buffers and the previous binding. */
    public static void end() {
        auxMode = AUX_NONE;
        if (!bound) return;
        bound = false;

        GL20C.glDrawBuffers(drawBuffers);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
    }

    /**
     * Complementary (Reimagined and Unbound) gbuffers_entities writes colortex 0, 3, 6,
     * plus 4 when reflections are on. Iris attaches the program's targets in that
     * order to attachments 0, 1, 2(, 3) (RenderTargets.createColorFramebuffer), so
     * only their count can be checked here; the pack name does the rest.
     */
    private static boolean isComplementaryEntityLayout(int[] buffers) {
        int count = 0;
        while (count < buffers.length && buffers[count] == GL30C.GL_COLOR_ATTACHMENT0 + count) count++;
        for (int i = count; i < buffers.length; i++) {
            if (buffers[i] != GL11C.GL_NONE) return false;
        }
        return count == 3 || count == 4;
    }

    /** Iris.getCurrentPackName(), e.g. "ComplementaryUnbound_r5.9.3.zip". */
    private static boolean isComplementaryPack() {
        if (packNameUnavailable) return false;
        try {
            if (currentPackName == null) {
                currentPackName = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName");
            }
            Object name = currentPackName.invoke(null);
            return name instanceof String s && s.toLowerCase(Locale.ROOT).contains("complementary");
        } catch (ReflectiveOperationException | RuntimeException e) {
            packNameUnavailable = true;
            LOGGER.warn("Could not read the Iris shader pack name", e);
            return false;
        }
    }

    /** Mirrors ExtendedShader#bindFramebuffer / FallbackShader#apply in Iris 1.8 (MC 1.21.1). */
    private static int findFramebuffer(ShaderInstance shader) throws ReflectiveOperationException {
        Field[] fields = SHADER_FIELDS.get(shader.getClass());
        if (fields == null) {
            fields = new Field[]{
                    declaredField(shader.getClass(), "writingToBeforeTranslucent"),
                    declaredField(shader.getClass(), "writingToAfterTranslucent"),
                    declaredField(shader.getClass(), "parent")
            };
            SHADER_FIELDS.put(shader.getClass(), fields);
        }

        Object pipeline = fields[2].get(shader);
        if (beforeTranslucentFlag == null) {
            beforeTranslucentFlag = pipeline.getClass().getField("isBeforeTranslucent");
        }

        Object framebuffer = fields[beforeTranslucentFlag.getBoolean(pipeline) ? 0 : 1].get(shader);
        if (framebufferId == null) {
            framebufferId = framebuffer.getClass().getMethod("getId");
        }
        return (int) framebufferId.invoke(framebuffer);
    }

    private static Field declaredField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
