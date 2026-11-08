import generations.gg.generations.core.generationscore.common.client.render.rarecandy.loading.GenerationsGLModel;
import gg.generations.rarecandy.pokeutils.BlendType;
import gg.generations.rarecandy.pokeutils.CullType;
import gg.generations.rarecandy.renderer.components.MeshObject;
import gg.generations.rarecandy.renderer.model.GLModel;
import gg.generations.rarecandy.renderer.model.Variant;
import gg.generations.rarecandy.renderer.model.material.*;
import gg.generations.rarecandy.renderer.pipeline.Pipeline;
import gg.generations.rarecandy.renderer.rendering.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Real GPU regression checks, runnable without launching Minecraft. */
public final class RenderRegression {
    private static final String SHADERS = "common/src/main/resources/assets/generations_core/shaders/";

    public static void main(String[] args) throws Exception {
        if (!glfwInit()) throw new AssertionError("GLFW initialization failed");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(16, 16, "Generations render regression", 0, 0);
        if (window == 0) throw new AssertionError("OpenGL context creation failed");
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("GPU: " + glGetString(GL_RENDERER));
            String vertex = Files.readString(Path.of(SHADERS, "animated.vs.glsl"));
            String fragment = Files.readString(Path.of(SHADERS, "animated.fs.glsl"));
            int program = program(vertex, fragment);
            int fbo = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            int output = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, output);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 16, 16, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, output, 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new AssertionError("Incomplete test framebuffer");
            glViewport(0, 0, 16, 16);
            geometry();
            glDisable(GL_BLEND);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);

            for (float materialAlpha : new float[]{1f, .25f}) {
                for (float lightAlpha : new float[]{0f, .5f, 1f}) {
                    float[] pixel = draw(program, materialAlpha, lightAlpha, true);
                    near("material alpha independent of lightmap alpha", materialAlpha, pixel[3]);
                    near("lightmap still shades RGB", .4f, pixel[0]);
                }
            }
            float[] unlit = draw(program, .25f, 0f, false);
            near("unlit opacity", .25f, unlit[3]);
            near("unlit color", .8f, unlit[0]);
            float[] cutout = draw(program, 0f, 1f, true);
            near("transparent texels discarded", 0f, cutout[3]);
            System.out.println("PASS: opaque, translucent, unlit and cutout shader cases");

            if (args.length > 0) {
                int original = program(vertex, Files.readString(Path.of(args[0])));
                near("upstream shader reproduces zero opacity", 0f, draw(original, 1f, 0f, true)[3]);
                glDeleteProgram(original);
                System.out.println("PASS: original fragment shader reproduces invisible lit surface");
            }

            stageRouting(program);
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error during tests");
            glDeleteProgram(program);
            System.out.println("PASS: no OpenGL errors (Minecraft/Iris integration still requires in-game testing)");
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    private static void stageRouting(int program) {
        var counts = new int[2];
        var pipeline = new Pipeline(Map.of(), Map.of(), m -> counts[0]++, m -> counts[1]++, program);
        PipelineRegistry.setFunction(name -> pipeline);
        var model = new CountingModel();
        var original = new OriginalCountingModel();
        var opaque = material(BlendType.None);
        var transparent = material(BlendType.Regular);
        var mesh = new MeshObject();
        mesh.setup(Map.of("opaque", new Variant(opaque), "transparent", new Variant(transparent),
                "hidden", new Variant(opaque, true, null)), model, "test");
        var instances = List.of(new ObjectInstance(new Matrix4f(), "opaque"),
                new ObjectInstance(new Matrix4f(), "transparent"),
                new ObjectInstance(new Matrix4f(), "hidden"));
        model.render(RenderStage.SOLID, instances, mesh);
        equal("solid pass contains only opaque mesh", 1, model.draws);
        model.render(RenderStage.TRANSPARENT, instances, mesh);
        equal("transparent pass drawn exactly once", 2, model.draws);
        equal("pipeline bind/unbind balanced", counts[0], counts[1]);

        original.render(RenderStage.SOLID, instances, mesh);
        original.render(RenderStage.TRANSPARENT, instances, mesh);
        equal("bundled RareCandy reproduces extra transparent draw", 3, original.draws);

        model.fail = true;
        try {
            model.render(RenderStage.SOLID, instances, mesh);
            throw new AssertionError("Expected simulated draw failure");
        } catch (SimulatedDrawFailure expected) {
            equal("pipeline cleanup on draw failure", counts[0], counts[1]);
        }
        PipelineRegistry.setFunction(name -> null);
        model.fail = false;
        model.render(RenderStage.SOLID, instances, mesh);
        System.out.println("PASS: pass routing, hidden meshes, missing pipeline and exception cleanup");
    }

    private static Material material(BlendType blend) {
        return new Material("test", new MaterialImages(), new MaterialValues(), false,
                CullType.None, blend, "test", 0, 0);
    }

    private static class SimulatedDrawFailure extends RuntimeException {}

    private static class CountingModel extends GenerationsGLModel {
        int draws;
        boolean fail;
        CountingModel() { super(null, null, new ArrayList<>(), 0, GL_UNSIGNED_INT, List.of()); }
        @Override public void runDrawCalls() {
            if (fail) throw new SimulatedDrawFailure();
            draws++;
        }
    }

    private static class OriginalCountingModel extends GLModel {
        int draws;
        OriginalCountingModel() { super(null, null, new ArrayList<>(), 0, GL_UNSIGNED_INT, List.of()); }
        @Override public void runDrawCalls() { draws++; }
    }

    private static void geometry() {
        float[] vertices = {
                -1,-1,0, .5f,.5f, 0,0,1, 0,0,0,0, 1,0,0,0,
                 3,-1,0, .5f,.5f, 0,0,1, 0,0,0,0, 1,0,0,0,
                -1, 3,0, .5f,.5f, 0,0,1, 0,0,0,0, 1,0,0,0
        };
        glBindVertexArray(glGenVertexArrays());
        glBindBuffer(GL_ARRAY_BUFFER, glGenBuffers());
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        int offset = 0;
        int[] sizes = {3, 2, 3, 4, 4};
        for (int i = 0; i < sizes.length; i++) {
            glEnableVertexAttribArray(i);
            glVertexAttribPointer(i, sizes[i], GL_FLOAT, false, 64, offset * 4L);
            offset += sizes[i];
        }
    }

    private static float[] draw(int p, float materialAlpha, float lightAlpha, boolean lit) {
        glUseProgram(p);
        float[] identity = new Matrix4f().get(new float[16]);
        for (String name : List.of("modelMatrix", "viewMatrix", "projectionMatrix"))
            glUniformMatrix4fv(glGetUniformLocation(p, name), false, identity);
        float[] bones = new float[220 * 16];
        for (int i = 0; i < 220; i++) System.arraycopy(identity, 0, bones, i * 16, 16);
        glUniformMatrix4fv(glGetUniformLocation(p, "boneTransforms[0]"), false, bones);
        glUniform2f(glGetUniformLocation(p, "uvScale"), 1f, 1f);
        glUniform4f(glGetUniformLocation(p, "ColorModulator"), 1f, 1f, 1f, 1f);
        glUniform3f(glGetUniformLocation(p, "tint"), 1f, 1f, 1f);
        // OverlayTexture.NO_OVERLAY: white, colour fully kept.
        glUniform4f(glGetUniformLocation(p, "overlayColor"), 1f, 1f, 1f, 1f);
        glUniform1i(glGetUniformLocation(p, "legacy"), 1);
        glUniform1i(glGetUniformLocation(p, "useLight"), lit ? 1 : 0);
        glUniform1f(glGetUniformLocation(p, "FogStart"), 100f);
        glUniform1f(glGetUniformLocation(p, "FogEnd"), 200f);
        int diffuse = texture(p, "diffuse", 0, new float[]{.8f, .4f, .2f, materialAlpha});
        int lightmap = texture(p, "lightmap", 3, new float[]{.5f, .5f, .5f, lightAlpha});
        int emission = texture(p, "emission", 4, new float[]{0f, 0f, 0f, 0f});
        glClearColor(0f, 0f, 0f, 0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        float[] pixel = new float[4];
        glReadPixels(8, 8, 1, 1, GL_RGBA, GL_FLOAT, pixel);
        glDeleteTextures(diffuse);
        glDeleteTextures(lightmap);
        glDeleteTextures(emission);
        return pixel;
    }

    private static int texture(int p, String name, int slot, float[] rgba) {
        int texture = glGenTextures();
        glActiveTexture(GL_TEXTURE0 + slot);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 1, 1, 0, GL_RGBA, GL_FLOAT, rgba);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glUniform1i(glGetUniformLocation(p, name), slot);
        return texture;
    }

    private static int program(String vertex, String fragment) {
        int p = glCreateProgram();
        int vs = shader(GL_VERTEX_SHADER, vertex);
        int fs = shader(GL_FRAGMENT_SHADER, fragment);
        glAttachShader(p, vs);
        glAttachShader(p, fs);
        glLinkProgram(p);
        if (glGetProgrami(p, GL_LINK_STATUS) == GL_FALSE) throw new AssertionError(glGetProgramInfoLog(p));
        glDeleteShader(vs);
        glDeleteShader(fs);
        return p;
    }

    private static int shader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) throw new AssertionError(glGetShaderInfoLog(shader));
        return shader;
    }

    private static void near(String label, float expected, float actual) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > .002f)
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }

    private static void equal(String label, int expected, int actual) {
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
