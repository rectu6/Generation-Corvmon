package generations.gg.generations.core.generationscore.common.mixin.client;

import com.mojang.blaze3d.platform.GlDebug;
import generations.gg.generations.core.generationscore.common.GenerationsCore;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL43C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft logs OpenGL errors without saying which code caused them. With
 * client.traceOpenGlErrors, the first errors are logged with their Java call stack.
 */
@Mixin(GlDebug.class)
public class GlDebugMixin {
    @Unique
    private static final int generations$MAX_TRACES = 10;
    @Unique
    private static int generations$traces;

    @Unique
    private static boolean generations$tracing() {
        // The config is loaded by the mod initializer, before the renderer starts.
        var config = GenerationsCore.CONFIG;
        return config != null && config.client.traceOpenGlErrors;
    }

    /** Minecraft never asks for synchronous output; without it the stack would not be the faulty call's. */
    @Inject(method = "enableDebugCallback", at = @At("RETURN"))
    private static void generations$synchronous(int verbosity, boolean sync, CallbackInfo ci) {
        if (!sync && GlDebug.isDebugEnabled() && generations$tracing()) {
            GL11C.glEnable(GL43C.GL_DEBUG_OUTPUT_SYNCHRONOUS);
        }
    }

    @Inject(method = "printDebugLog", at = @At("HEAD"))
    private static void generations$traceError(int source, int type, int id, int severity, int messageLength, long message, long userParam, CallbackInfo ci) {
        if (type != GL43C.GL_DEBUG_TYPE_ERROR || generations$traces >= generations$MAX_TRACES || !generations$tracing()) return;
        generations$traces++;
        GenerationsCore.LOGGER.warn("OpenGL error {} ({}/{}), call stack:", id, generations$traces, generations$MAX_TRACES, new Throwable());
    }
}
