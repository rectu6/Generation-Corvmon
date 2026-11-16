import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Off-game check of the legendary cinematics' shaders with this machine's OpenGL driver: a hidden
 * window with Minecraft's context (3.2 core, forward compatible), then each shader compiled and
 * linked, and every uniform and sampler its JSON declares found in the program (Minecraft warns
 * about those it cannot find).
 *
 *   java -cp <out>;<lwjgl jars and natives> CinematicShaderCheck <assets/minecraft/shaders/core>
 */
public class CinematicShaderCheck {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        if (!GLFW.glfwInit()) throw new IllegalStateException("no GLFW");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long window = GLFW.glfwCreateWindow(64, 64, "check", 0, 0);
        if (window == 0) throw new IllegalStateException("no window");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("OpenGL " + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION) + " / " + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
        int failures = 0;
        for (String name : List.of("generations_cinematic_fx", "generations_cinematic_crystal", "generations_cinematic_post", "generations_cinematic_blur")) {
            String json = Files.readString(dir.resolve(name + ".json"));
            String vertex = field(json, "vertex");
            String fragment = field(json, "fragment");
            int vs = compile(GL20.GL_VERTEX_SHADER, Files.readString(dir.resolve(vertex + ".vsh")), vertex + ".vsh");
            int fs = compile(GL20.GL_FRAGMENT_SHADER, Files.readString(dir.resolve(fragment + ".fsh")), fragment + ".fsh");
            if (vs == 0 || fs == 0) {
                failures++;
                continue;
            }
            int program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vs);
            GL20.glAttachShader(program, fs);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                System.out.println("LINK FAIL " + name + ": " + GL20.glGetProgramInfoLog(program));
                failures++;
                continue;
            }
            int missing = 0;
            for (String uniform : names(json)) {
                if (GL20.glGetUniformLocation(program, uniform) < 0) {
                    System.out.println("  " + name + ": uniform or sampler not found in the program: " + uniform);
                    missing++;
                }
            }
            System.out.println((missing == 0 ? "OK   " : "WARN ") + name);
        }
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        System.out.println(failures == 0 ? "all shaders compile and link" : failures + " shader(s) failed");
        if (failures > 0) System.exit(1);
    }

    private static String field(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        int a = json.indexOf('"', json.indexOf(':', i) + 1);
        int b = json.indexOf('"', a + 1);
        return json.substring(a + 1, b);
    }

    private static List<String> names(String json) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        int i = 0;
        while ((i = json.indexOf("\"name\"", i)) >= 0) {
            int a = json.indexOf('"', json.indexOf(':', i) + 1);
            int b = json.indexOf('"', a + 1);
            out.add(json.substring(a + 1, b));
            i = b;
        }
        return out;
    }

    private static int compile(int type, String source, String label) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            System.out.println("COMPILE FAIL " + label + ": " + GL20.glGetShaderInfoLog(shader));
            return 0;
        }
        String log = GL20.glGetShaderInfoLog(shader);
        if (log != null && !log.isBlank()) System.out.println("  " + label + " log: " + log.trim());
        return shader;
    }
}
