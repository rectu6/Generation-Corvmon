import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * The three memories' textures (JOHTO-PLAN.md § 8), drawn by code at 512 × 512 and reduced to
 * 128 × 128 (4 × 4 averaging, premultiplied alpha): a faceted crystal tear lit from the top left,
 * the beast's emblem glowing inside — Raikou's lightning, Entei's flame, Suicune's ripples —, a
 * star of light on its edge and a dark outline for the inventory. Original art, by code only.
 *
 *   javac -d <out> tools/JohtoMemoryArt.java
 *   java -cp <out> JohtoMemoryArt <Generations-Core>
 */
public class JohtoMemoryArt {
    static final int S = 512;

    static double clamp(double x, double a, double b) { return Math.max(a, Math.min(b, x)); }
    static double smooth(double a, double b, double x) { double t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }
    static double[] mix(double[] a, double[] b, double f) { return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f}; }

    /** The tear's half width at height v (0 bottom, 1 top), in pixels; its top is a point, its bottom round. */
    static double halfWidth(double v) {
        if (v < 0 || v > 1) return -1;
        double round = Math.sqrt(Math.max(0, 1 - Math.pow((v - 0.36) / 0.36, 2)));
        double point = Math.pow(Math.max(0, (1 - v) / 0.64), 1.25);
        return 150 * (v < 0.36 ? round : Math.min(1, point));
    }

    static double distSeg(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double t = clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy), 0, 1);
        double cx = ax + dx * t - px, cy = ay + dy * t - py;
        return Math.sqrt(cx * cx + cy * cy);
    }

    /** How much of the emblem covers (x, y) (pixels from the tear's centre, y up), with its glow. */
    static double[] emblem(int beast, double x, double y) {
        double core, glow;
        if (beast == 0) {
            // A lightning bolt: three strokes.
            double[][] p = {{-38, 92}, {18, 18}, {-22, 4}, {34, -86}};
            double d = 1e9;
            for (int i = 0; i < p.length - 1; i++) d = Math.min(d, distSeg(x, y, p[i][0], p[i][1], p[i + 1][0], p[i + 1][1]));
            core = 1 - smooth(9, 14, d);
            glow = Math.exp(-d / 26.0);
        } else if (beast == 1) {
            // A flame: a tear pointing up, its tongues waving.
            double v = (y + 80) / 190.0;
            double cv = clamp(v, 0, 1);
            // A tear pointing up: round at the bottom, a curling tip.
            double w = cv < 0.3 ? 58 * Math.sqrt(Math.max(0, 1 - Math.pow((cv - 0.3) / 0.3, 2))) : 58 * Math.pow(1 - (cv - 0.3) / 0.7, 1.4);
            double d = Math.abs(x - 40 * Math.sin(cv * 2.4) * cv * cv) - w;
            // A second, smaller tongue on the left.
            double v2 = (y + 40) / 120.0;
            double c2 = clamp(v2, 0, 1);
            double w2 = c2 < 0.3 ? 22 * Math.sqrt(Math.max(0, 1 - Math.pow((c2 - 0.3) / 0.3, 2))) : 22 * Math.pow(1 - (c2 - 0.3) / 0.7, 1.3);
            double d2 = Math.abs(x + 44 + 20 * c2 * c2) - w2;
            if (v2 < 0 || v2 > 1) d2 = 99;
            d = Math.min(d, d2);
            if (v < 0 || v > 1) d = Math.max(d, 20);
            core = 1 - smooth(-3, 4, d);
            glow = Math.exp(-Math.max(0, d) / 30.0);
        } else {
            // Ripples round a diamond.
            double diamond = Math.abs(x) / 34.0 + Math.abs(y - 6) / 50.0;
            double r = Math.sqrt(x * x + (y - 6) * (y - 6));
            double ring = Math.max(Math.exp(-Math.pow((r - 72) / 6.0, 2)), Math.exp(-Math.pow((r - 102) / 5.0, 2)) * 0.7);
            core = Math.max(1 - smooth(0.92, 1.02, diamond), ring * 0.85);
            glow = Math.max(Math.exp(-Math.max(0, diamond - 1) * 3.0), ring);
        }
        return new double[]{clamp(core, 0, 1), clamp(glow, 0, 1)};
    }

    static double[] pixel(int beast, double px, double py) {
        double[][] dark = {{0.42, 0.26, 0.05}, {0.42, 0.06, 0.04}, {0.04, 0.20, 0.42}};
        double[][] bright = {{1.0, 0.90, 0.40}, {1.0, 0.55, 0.20}, {0.55, 0.92, 1.0}};
        double[][] edge = {{0.62, 0.42, 0.95}, {1.0, 0.85, 0.35}, {0.85, 0.75, 1.0}};
        double[] emblemColour = {1.0, 0.98, 0.9};
        double cx = S / 2.0, base = S - 40.0, top = 34.0;
        double v = (base - py) / (base - top);
        double x = px - cx;
        double hw = halfWidth(v);
        double outline = 10;
        if (hw < 0 || Math.abs(x) > hw + outline) return new double[]{0, 0, 0, 0};
        if (Math.abs(x) > hw) return new double[]{0.06, 0.04, 0.08, 1 - smooth(hw + outline - 2, hw + outline, Math.abs(x))};
        double u = x / Math.max(1, hw);
        // Facets: six bands by the angle from a point above the centre.
        double fy = (v - 0.42) * (base - top);
        double ang = Math.atan2(fy, x);
        int facet = (int) Math.floor((ang + Math.PI) / (Math.PI / 3));
        double shade = 0.62 + 0.38 * Math.cos(ang - 2.2) * 0.5 + 0.08 * (facet % 2);
        shade *= 0.85 + 0.25 * (1 - Math.abs(u));
        double[] col = mix(dark[beast], bright[beast], clamp(shade * (0.55 + 0.6 * v), 0, 1));
        // The rim light, the beast's second colour.
        col = mix(col, edge[beast], 0.45 * smooth(0.78, 1.0, Math.abs(u)));
        // The emblem inside.
        double[] e = emblem(beast, x, fy);
        col = mix(col, bright[beast], 0.55 * e[1]);
        col = mix(col, emblemColour, e[0]);
        // A star of light on the upper left edge.
        double sx = px - (cx - 62), sy = py - (top + 150);
        double star = Math.exp(-Math.abs(sx) / 4.0) * Math.exp(-Math.abs(sy) / 40.0) + Math.exp(-Math.abs(sy) / 4.0) * Math.exp(-Math.abs(sx) / 40.0);
        col = mix(col, new double[]{1, 1, 1}, clamp(star, 0, 1));
        return new double[]{clamp(col[0], 0, 1), clamp(col[1], 0, 1), clamp(col[2], 0, 1), 1};
    }

    static BufferedImage draw(int beast) {
        double[][] r = new double[S * S][];
        for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) r[y * S + x] = pixel(beast, x + 0.5, y + 0.5);
        BufferedImage out = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            double cr = 0, cg = 0, cb = 0, ca = 0;
            for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) {
                double[] p = r[(y * 4 + j) * S + x * 4 + i];
                cr += p[0] * p[3]; cg += p[1] * p[3]; cb += p[2] * p[3]; ca += p[3];
            }
            int a = (int) Math.round(ca / 16 * 255);
            int red = ca > 0 ? (int) Math.round(cr / ca * 255) : 0;
            int green = ca > 0 ? (int) Math.round(cg / ca * 255) : 0;
            int blue = ca > 0 ? (int) Math.round(cb / ca * 255) : 0;
            out.setRGB(x, y, (a << 24) | (red << 16) | (green << 8) | blue);
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : ".");
        String[] names = {"raikou_memory", "entei_memory", "suicune_memory"};
        BufferedImage preview = new BufferedImage(3 * 256 + 40, 296, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = preview.createGraphics();
        g.setColor(new java.awt.Color(40, 38, 48));
        g.fillRect(0, 0, preview.getWidth(), preview.getHeight());
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int b = 0; b < 3; b++) {
            BufferedImage img = draw(b);
            File file = new File(root, "common/src/main/resources/assets/generations_core/textures/item/" + names[b] + ".png");
            file.getParentFile().mkdirs();
            ImageIO.write(img, "png", file);
            g.drawImage(img, 10 + b * 266, 20, 256, 256, null);
            System.out.println("wrote " + file);
        }
        g.dispose();
        File prev = new File(root, "tools/previews/johto_memories_preview.png");
        prev.getParentFile().mkdirs();
        ImageIO.write(preview, "png", prev);
        System.out.println("wrote " + prev);
    }
}
