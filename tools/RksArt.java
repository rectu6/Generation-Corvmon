import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/**
 * The RKS Machine's art (RKS-PLAN.md § 3.3 and § 4.3, decision D8), painted with Java2D:
 * - the model's texture atlas (512 × 512, the regions of tools/rks_machine_model.py);
 * - the research files M-01, M-02, M-03 and the repair kit (128 × 128 items);
 * - previews of the 3D model rendered off-game (a small software rasteriser over the mesh JSON
 *   and the atlas): the assembled machine from the front and the back, the damaged machine, the
 *   five modules alone. Shape check only: the final look is judged in game.
 *
 *   javac -d <out> tools/RksArt.java
 *   java -cp <out> RksArt            (run from Generations-Core, after tools/rks_machine_model.py)
 */
public class RksArt {
    static final String ASSETS = "common/src/main/resources/assets/generations_core/";
    static final Random RNG = new Random(150);

    public static void main(String[] args) throws Exception {
        BufferedImage atlas = atlas();
        write(atlas, ASSETS + "textures/block/rks/rks_machine.png");
        write(researchM01(), ASSETS + "textures/item/rks/rks_research_m01_genetics.png");
        write(researchM02(), ASSETS + "textures/item/rks/rks_research_m02_stabilization.png");
        write(researchM03(), ASSETS + "textures/item/rks/rks_research_m03_containment.png");
        write(repairKit(), ASSETS + "textures/item/rks/rks_repair_kit.png");
        previews(atlas);
    }

    static void write(BufferedImage image, String path) throws Exception {
        File file = new File(path);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
        System.out.println("wrote " + path + " (" + image.getWidth() + "x" + image.getHeight() + ")");
    }

    static Graphics2D g(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    static Color c(int rgb) { return new Color(rgb); }
    static Color c(int rgb, int a) { return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a); }

    // ---------------------------------------------------------------- the atlas

    /** Brushed metal: a base tone, horizontal streaks, a soft vertical gradient. */
    static void brushed(BufferedImage img, int x0, int y0, int w, int h, int base, int spread, boolean vertical) {
        Random r = new Random(base * 31L + w);
        float[] row = new float[vertical ? w : h];
        for (int i = 0; i < row.length; i++) row[i] = (float) (r.nextGaussian() * 0.5);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = vertical ? x : y;
            double streak = row[i] + 0.35 * Math.sin((vertical ? y : x) * 0.07 + i) * 0.2;
            double grain = r.nextGaussian() * 0.25;
            double light = 1.0 + 0.10 * (0.5 - (double) y / h);
            int v = (int) (spread * (streak + grain));
            img.setRGB(x0 + x, y0 + y, 0xFF000000 | shade(base, v, light));
        }
    }

    static int shade(int rgb, int add, double mul) {
        int r = clamp((int) ((((rgb >> 16) & 255) + add) * mul));
        int g = clamp((int) ((((rgb >> 8) & 255) + add) * mul));
        int b = clamp((int) (((rgb & 255) + add) * mul));
        return (r << 16) | (g << 8) | b;
    }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    static void rivets(Graphics2D g, int x0, int y0, int w, int h, int inset, int light, int dark) {
        int[][] at = {{inset, inset}, {w - inset, inset}, {inset, h - inset}, {w - inset, h - inset}};
        for (int[] p : at) {
            g.setColor(c(dark));
            g.fill(new Ellipse2D.Double(x0 + p[0] - 3, y0 + p[1] - 3, 6, 6));
            g.setColor(c(light));
            g.fill(new Ellipse2D.Double(x0 + p[0] - 2.2, y0 + p[1] - 2.6, 3.6, 3.6));
        }
    }

    static BufferedImage atlas() {
        BufferedImage img = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        // metal_dark: gunmetal plates with seams.
        brushed(img, 0, 0, 128, 128, 0x3A4049, 10, false);
        g.setColor(c(0x1E2228, 200));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(0, 64, 128, 64);
        g.drawLine(64, 0, 64, 64);
        g.setColor(c(0x5A626E, 140));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(0, 66, 128, 66);
        rivets(g, 0, 0, 64, 64, 6, 0x8890A0, 0x22262C);
        rivets(g, 64, 0, 64, 64, 6, 0x8890A0, 0x22262C);
        // metal_light: brushed steel.
        brushed(img, 128, 0, 128, 128, 0xA9B2BE, 12, true);
        g.setColor(c(0x707884, 160));
        g.drawRect(129, 1, 125, 125);
        // panel: white laboratory panels, seams and a blue accent line.
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            double n = RNG.nextGaussian() * 2.5;
            int v = (int) (232 + n - 10.0 * y / 128.0);
            img.setRGB(256 + x, y, 0xFF000000 | (clamp(v - 6) << 16) | (clamp(v - 2) << 8) | clamp(v + 4));
        }
        g.setColor(c(0x9AA3AF));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(256, 42, 384, 42);
        g.drawLine(256, 86, 384, 86);
        g.drawLine(320, 0, 320, 42);
        g.drawLine(300, 86, 300, 128);
        g.setColor(c(0x2D9CDB));
        g.fillRect(256, 60, 128, 5);
        g.setColor(c(0x7B3FE4));
        g.fillRect(256, 66, 128, 2);
        g.setColor(c(0xC8CED6));
        for (int k = 0; k < 6; k++) g.fillRoundRect(340 + (k % 3) * 12, 98 + (k / 3) * 10, 8, 5, 3, 3);
        rivets(g, 256, 0, 64, 42, 5, 0xF4F6F8, 0x9AA3AF);
        // grille.
        g.setColor(c(0x24282E));
        g.fillRect(384, 0, 128, 128);
        for (int y = 4; y < 128; y += 8) {
            g.setColor(c(0x0E1013));
            g.fillRect(388, y, 120, 4);
            g.setColor(c(0x4A525C));
            g.fillRect(388, y + 4, 120, 1);
        }
        g.setColor(c(0x5A626E));
        g.drawRect(385, 1, 125, 125);
        // cable: dark ribbed rubber (runs along u).
        for (int y = 0; y < 32; y++) for (int x = 0; x < 128; x++) {
            double rib = Math.sin(x * Math.PI / 4.0) * 10;
            double round = Math.sin(Math.PI * (y + 0.5) / 32.0);
            int v = (int) (22 + 38 * round + rib * round);
            img.setRGB(x, 128 + y, 0xFF000000 | (clamp(v) << 16) | (clamp(v + 2) << 8) | clamp(v + 8));
        }
        // hazard stripes.
        g.setColor(c(0xF2C230));
        g.fillRect(128, 128, 128, 32);
        g.setColor(c(0x1A1A1A));
        for (int k = -2; k < 12; k++) {
            Polygon p = new Polygon();
            p.addPoint(128 + k * 20, 160);
            p.addPoint(128 + k * 20 + 10, 160);
            p.addPoint(128 + k * 20 + 42, 128);
            p.addPoint(128 + k * 20 + 32, 128);
            g.fillPolygon(p);
        }
        // pipe: shiny copper-steel tube.
        for (int y = 0; y < 32; y++) for (int x = 0; x < 128; x++) {
            double round = Math.sin(Math.PI * (y + 0.5) / 32.0);
            double hi = Math.pow(Math.max(0, Math.cos((y - 9) * 0.25)), 6);
            int r = (int) (70 + 110 * round + 70 * hi), gg = (int) (78 + 112 * round + 70 * hi), b = (int) (90 + 120 * round + 60 * hi);
            if (x % 32 < 3) { r -= 40; gg -= 40; b -= 40; }
            img.setRGB(x, 160 + y, 0xFF000000 | (clamp(r) << 16) | (clamp(gg) << 8) | clamp(b));
        }
        // trim: bevelled steel band with a bright edge.
        for (int y = 0; y < 32; y++) for (int x = 0; x < 128; x++) {
            double t = y / 31.0;
            int v = (int) (120 + 90 * Math.sin(Math.PI * t) + (t < 0.15 ? 50 : 0) + RNG.nextGaussian() * 3);
            img.setRGB(128 + x, 160 + y, 0xFF000000 | (clamp(v - 8) << 16) | (clamp(v - 2) << 8) | clamp(v + 6));
        }
        // screen frame.
        g.setPaint(new GradientPaint(256, 128, c(0x2A2F36), 256, 192, c(0x16191D)));
        g.fillRect(256, 128, 128, 64);
        g.setColor(c(0x4B535E));
        g.drawRect(258, 130, 123, 59);
        g.setColor(c(0x2D9CDB));
        g.fillRect(262, 186, 20, 2);
        // glass: pale cyan, streaks, brighter edges (alpha: translucent).
        for (int y = 0; y < 128; y++) for (int x = 0; x < 64; x++) {
            double edge = Math.pow(Math.abs(x - 31.5) / 32.0, 3);
            double streak = Math.max(0, Math.sin(x * 0.45 + y * 0.03)) * 0.15 + (x > 10 && x < 14 ? 0.35 : 0) + (x > 40 && x < 42 ? 0.2 : 0);
            int a = clamp((int) (70 + 80 * edge + 90 * streak));
            int r = clamp((int) (170 + 60 * streak)), gg = clamp((int) (225 + 30 * streak)), b = 255;
            img.setRGB(x, 192 + y, (a << 24) | (r << 16) | (gg << 8) | b);
        }
        // liquid: violet-pink clone fluid, a soft tileable noise (alpha).
        double[][] noise = tileNoise(128, 128, 8, 5);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            double n = noise[y][x];
            int a = clamp((int) (150 + 70 * n));
            int r = clamp((int) (150 + 70 * n)), gg = clamp((int) (90 + 60 * n)), b = clamp((int) (210 + 40 * n));
            img.setRGB(64 + x, 192 + y, (a << 24) | (r << 16) | (gg << 8) | b);
        }
        // screens: cyan interface, red alert.
        screen(g, 192, 192, 128, 64, 0x0A2733, 0x35E0FF, false);
        screen(g, 320, 192, 128, 64, 0x2A0608, 0xFF3B30, true);
        // light: white soft strip.
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            double d = Math.max(Math.abs(x - 31.5) / 32.0, Math.abs(y - 31.5) / 32.0);
            int v = clamp((int) (255 - 70 * Math.pow(d, 3)));
            img.setRGB(448 + x, 192 + y, 0xFF000000 | (v << 16) | (v << 8) | v);
        }
        // halo: radial glow (white, the colour comes from the vertices).
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            double d = Math.hypot(x - 63.5, y - 63.5) / 64.0;
            double v = Math.max(0, 1 - d);
            v = v * v * (3 - 2 * v);
            int a = clamp((int) (255 * v));
            img.setRGB(x, 320 + y, (a << 24) | 0xFFFFFF);
        }
        // core: bright energy swirls (white-cyan), tileable along v.
        double[][] swirl = tileNoise(128, 128, 6, 9);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            double s = 0.5 + 0.5 * Math.sin((x / 128.0) * 6 * Math.PI + (y / 128.0) * 4 * Math.PI + 3 * swirl[y][x]);
            double v = 0.55 + 0.45 * s;
            int r = clamp((int) (180 * v + 75)), gg = clamp((int) (230 * v + 25)), b = 255;
            img.setRGB(128 + x, 320 + y, 0xFF000000 | (r << 16) | (gg << 8) | b);
        }
        // dark matte.
        brushed(img, 256, 256, 128, 64, 0x1C1F24, 5, false);
        // gauge.
        gauge(g, 384, 256, 64);
        // buttons: 4 colours × 2 rows.
        int[] colours = {0xFF4D4D, 0x4DFF88, 0x4DB8FF, 0xFFD34D, 0xFF4DD2, 0xFFFFFF, 0x9B6BFF, 0xFF8C3B};
        for (int k = 0; k < 8; k++) {
            int bx = 448 + (k % 4) * 16, by = 256 + (k / 4) * 32;
            g.setColor(c(colours[k]));
            g.fillRect(bx, by, 16, 32);
            g.setColor(c(0xFFFFFF, 120));
            g.fillRect(bx + 3, by + 3, 6, 8);
        }
        // cracks: white crack lines on transparent.
        g.setColor(c(0xFFFFFF, 230));
        Random cr = new Random(7);
        for (int k = 0; k < 9; k++) crack(g, cr, 256 + 64, 320 + 64, cr.nextDouble() * Math.PI * 2, 6);
        // holo: a DNA helix (white, tinted).
        helix(g, 384, 320, 128, 128, 0xFFFFFF, 0xFFFFFF, false);
        // screen_dna: a helix on a dark cyan screen.
        g.setColor(c(0x062029));
        g.fillRect(192, 256, 64, 64);
        helix(g, 192, 256, 64, 64, 0x35E0FF, 0xB06BFF, true);
        // vial: coloured liquid in glass.
        int[] vials = {0x35E0FF, 0xB06BFF, 0x4DFF88, 0xFF4DD2};
        for (int k = 0; k < 4; k++) {
            g.setColor(c(vials[k]));
            g.fillRect(384 + k * 16, 448, 16, 64);
            g.setColor(c(0xFFFFFF, 140));
            g.fillRect(384 + k * 16 + 3, 452, 3, 56);
        }
        g.dispose();
        return img;
    }

    static double[][] tileNoise(int w, int h, int cells, long seed) {
        Random r = new Random(seed);
        double[][] grid = new double[cells][cells];
        for (double[] row : grid) for (int i = 0; i < cells; i++) row[i] = r.nextDouble() * 2 - 1;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double v = 0, amp = 1, total = 0;
            for (int o = 0; o < 3; o++) {
                int c2 = cells << o;
                double fx = (double) x / w * c2, fy = (double) y / h * c2;
                int ix = (int) fx, iy = (int) fy;
                double tx = fx - ix, ty = fy - iy;
                tx = tx * tx * (3 - 2 * tx);
                ty = ty * ty * (3 - 2 * ty);
                double a = grid[iy % cells][ix % cells], b = grid[iy % cells][(ix + 1) % cells];
                double cc = grid[(iy + 1) % cells][ix % cells], d = grid[(iy + 1) % cells][(ix + 1) % cells];
                v += amp * ((a * (1 - tx) + b * tx) * (1 - ty) + (cc * (1 - tx) + d * tx) * ty);
                total += amp;
                amp *= 0.5;
            }
            out[y][x] = v / total;
        }
        return out;
    }

    static void screen(Graphics2D g, int x, int y, int w, int h, int bg, int fg, boolean alert) {
        g.setColor(c(bg));
        g.fillRect(x, y, w, h);
        g.setColor(c(fg, 40));
        for (int k = 0; k < w; k += 8) g.drawLine(x + k, y, x + k, y + h);
        for (int k = 0; k < h; k += 8) g.drawLine(x, y + k, x + w, y + k);
        g.setStroke(new BasicStroke(1.5f));
        if (!alert) {
            g.setColor(c(fg));
            Path2D wave = new Path2D.Double();
            for (int k = 0; k <= 60; k++) {
                double px = x + 4 + k, py = y + 18 + 9 * Math.sin(k * 0.42) * Math.sin(k * 0.09 + 1);
                if (k == 0) wave.moveTo(px, py); else wave.lineTo(px, py);
            }
            g.draw(wave);
            for (int k = 0; k < 6; k++) {
                g.setColor(c(fg, 200 - k * 20));
                g.fillRect(x + 70, y + 6 + k * 9, 20 + (k * 37) % 34, 4);
            }
            g.setColor(c(0xB06BFF));
            for (int k = 0; k < 10; k++) g.fillRect(x + 6 + k * 6, y + h - 6 - (k * 13) % 22, 4, (k * 13) % 22 + 2);
            g.setColor(c(fg));
            g.drawOval(x + 100, y + 30, 22, 22);
            g.drawLine(x + 111, y + 41, x + 119, y + 34);
        } else {
            g.setColor(c(fg, 90));
            for (int k = -4; k < 12; k++) {
                Polygon p = new Polygon();
                p.addPoint(x + k * 16, y + h);
                p.addPoint(x + k * 16 + 8, y + h);
                p.addPoint(x + k * 16 + 8 + h, y);
                p.addPoint(x + k * 16 + h, y);
                g.fillPolygon(p);
            }
            g.setColor(c(0x000000, 170));
            g.fillRect(x + 6, y + 18, w - 12, 28);
            g.setColor(c(fg));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            FontMetrics fm = g.getFontMetrics();
            String s = "! ALERTE !";
            g.drawString(s, x + (w - fm.stringWidth(s)) / 2, y + 37);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 7));
            fm = g.getFontMetrics();
            s = "CONFINEMENT";
            g.drawString(s, x + (w - fm.stringWidth(s)) / 2, y + 45);
        }
        g.setStroke(new BasicStroke(1f));
    }

    static void gauge(Graphics2D g, int x, int y, int s) {
        g.setColor(c(0x2A2F36));
        g.fillRect(x, y, s, s);
        g.setColor(c(0xE8ECEF));
        g.fill(new Ellipse2D.Double(x + 4, y + 4, s - 8, s - 8));
        g.setColor(c(0x24282E));
        g.setStroke(new BasicStroke(1.2f));
        for (int k = 0; k <= 10; k++) {
            double a = Math.toRadians(225 - k * 27);
            double r0 = s / 2.0 - 7, r1 = s / 2.0 - (k % 5 == 0 ? 13 : 10);
            g.draw(new Line2D.Double(x + s / 2.0 + r0 * Math.cos(a), y + s / 2.0 - r0 * Math.sin(a), x + s / 2.0 + r1 * Math.cos(a), y + s / 2.0 - r1 * Math.sin(a)));
        }
        g.setColor(c(0xD0021B));
        g.setStroke(new BasicStroke(2f));
        double a = Math.toRadians(60);
        g.draw(new Line2D.Double(x + s / 2.0, y + s / 2.0, x + s / 2.0 + (s / 2.0 - 12) * Math.cos(a), y + s / 2.0 - (s / 2.0 - 12) * Math.sin(a)));
        g.setColor(c(0x24282E));
        g.fill(new Ellipse2D.Double(x + s / 2.0 - 3, y + s / 2.0 - 3, 6, 6));
        g.setStroke(new BasicStroke(1f));
    }

    static void crack(Graphics2D g, Random r, double x, double y, double angle, int depth) {
        if (depth <= 0) return;
        double len = 10 + r.nextDouble() * 14;
        double nx = x + Math.cos(angle) * len, ny = y + Math.sin(angle) * len;
        g.setStroke(new BasicStroke(0.6f + depth * 0.25f));
        g.draw(new Line2D.Double(x, y, nx, ny));
        crack(g, r, nx, ny, angle + (r.nextDouble() - 0.5) * 0.9, depth - 1);
        if (r.nextDouble() < 0.35) crack(g, r, nx, ny, angle + (r.nextDouble() - 0.5) * 2.0, depth - 2);
    }

    static void helix(Graphics2D g, int x, int y, int w, int h, int a, int b, boolean glow) {
        g.setStroke(new BasicStroke(Math.max(1.2f, w / 40f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int k = 0; k < 14; k++) {
            double t = (k + 0.5) / 14.0;
            double py = y + 4 + t * (h - 8);
            double p = t * Math.PI * 4;
            double xa = x + w / 2.0 + Math.sin(p) * w * 0.3, xb = x + w / 2.0 - Math.sin(p) * w * 0.3;
            g.setColor(c(0xFFFFFF, 150));
            g.draw(new Line2D.Double(xa, py, xb, py));
        }
        for (int s = 0; s < 2; s++) {
            Path2D path = new Path2D.Double();
            for (int k = 0; k <= 60; k++) {
                double t = k / 60.0;
                double px = x + w / 2.0 + (s == 0 ? 1 : -1) * Math.sin(t * Math.PI * 4) * w * 0.3, py = y + 4 + t * (h - 8);
                if (k == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            g.setColor(c(s == 0 ? a : b));
            g.draw(path);
        }
        g.setStroke(new BasicStroke(1f));
    }

    // ---------------------------------------------------------------- the items (128 × 128)

    /** A research folder: its colour, a tab, a label band; then the emblem drawn by the caller. */
    static Graphics2D folder(BufferedImage img, int main, int dark, int light, String code) {
        Graphics2D g = g(img);
        g.translate(64, 64);
        g.rotate(Math.toRadians(-8));
        g.translate(-64, -64);
        // Shadow.
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(16, 22, 100, 96, 12, 12));
        // Back sheet and tab.
        g.setColor(c(dark));
        g.fill(new RoundRectangle2D.Double(12, 14, 100, 98, 12, 12));
        g.fill(new RoundRectangle2D.Double(16, 6, 44, 18, 8, 8));
        // Papers sticking out.
        g.setColor(c(0xEFEFEA));
        g.fill(new RoundRectangle2D.Double(20, 12, 86, 30, 4, 4));
        g.setColor(c(0xC9CDD2));
        for (int k = 0; k < 4; k++) g.fillRect(26, 18 + k * 5, 50 + (k * 17) % 26, 2);
        // Front cover.
        g.setPaint(new GradientPaint(0, 24, c(light), 0, 116, c(main)));
        g.fill(new RoundRectangle2D.Double(10, 24, 104, 92, 12, 12));
        g.setColor(c(dark));
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new RoundRectangle2D.Double(10, 24, 104, 92, 12, 12));
        // Label band with the code.
        g.setColor(new Color(10, 14, 20, 210));
        g.fill(new RoundRectangle2D.Double(18, 92, 88, 18, 6, 6));
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(code, 62 - fm.stringWidth(code) / 2, 106);
        g.setStroke(new BasicStroke(1f));
        return g;
    }

    static BufferedImage researchM01() {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = folder(img, 0x0E7FA6, 0x074A63, 0x46D7FF, "M-01");
        g.setColor(new Color(255, 255, 255, 60));
        g.fill(new Ellipse2D.Double(32, 30, 60, 60));
        helix(g, 36, 30, 52, 60, 0xFFFFFF, 0xB8F4FF, false);
        g.dispose();
        return img;
    }

    static BufferedImage researchM02() {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = folder(img, 0x6A2FC7, 0x3B1677, 0xB98CFF, "M-02");
        g.setStroke(new BasicStroke(3.2f));
        double cx = 62, cy = 60;
        for (int k = 0; k < 3; k++) {
            g.setColor(k == 1 ? c(0xFFFFFF) : c(0xE5D4FF));
            Shape e = new Ellipse2D.Double(cx - 26 + k * 3, cy - 12 + k * 2, 52 - k * 6, 24 - k * 4);
            AffineTransform t = AffineTransform.getRotateInstance(Math.toRadians(-35 + k * 55), cx, cy);
            g.draw(t.createTransformedShape(e));
        }
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(cx - 7, cy - 7, 14, 14));
        g.setColor(c(0xB98CFF));
        g.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
        g.dispose();
        return img;
    }

    static BufferedImage researchM03() {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = folder(img, 0xB3181E, 0x680A0E, 0xFF5A5F, "M-03");
        // The padlock.
        double cx = 62, cy = 58;
        g.setColor(c(0xF3F3F3));
        g.setStroke(new BasicStroke(5f));
        g.draw(new Arc2D.Double(cx - 12, cy - 26, 24, 26, 0, 180, Arc2D.OPEN));
        g.draw(new Line2D.Double(cx - 12, cy - 13, cx - 12, cy - 4));
        g.draw(new Line2D.Double(cx + 12, cy - 13, cx + 12, cy - 4));
        g.fill(new RoundRectangle2D.Double(cx - 19, cy - 6, 38, 30, 6, 6));
        g.setColor(c(0x680A0E));
        g.fill(new Ellipse2D.Double(cx - 4, cy + 2, 8, 8));
        g.fillRect((int) cx - 2, (int) cy + 8, 4, 9);
        // The "CONFIDENTIEL" stamp.
        g.setStroke(new BasicStroke(2f));
        AffineTransform keep = g.getTransform();
        g.rotate(Math.toRadians(-16), 64, 80);
        g.setColor(new Color(255, 255, 255, 220));
        g.draw(new RoundRectangle2D.Double(18, 72, 92, 16, 4, 4));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        String s = "CONFIDENTIEL";
        g.drawString(s, 64 - fm.stringWidth(s) / 2, 84);
        g.setTransform(keep);
        g.dispose();
        return img;
    }

    static BufferedImage repairKit() {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(14, 40, 104, 74, 14, 14));
        // Handle.
        g.setColor(c(0x2B3038));
        g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new RoundRectangle2D.Double(44, 18, 40, 26, 12, 12));
        // Case.
        g.setPaint(new GradientPaint(0, 34, c(0xF2F4F7), 0, 110, c(0xA8B0BC)));
        g.fill(new RoundRectangle2D.Double(10, 34, 108, 76, 14, 14));
        g.setColor(c(0x3A4049));
        g.setStroke(new BasicStroke(3f));
        g.draw(new RoundRectangle2D.Double(10, 34, 108, 76, 14, 14));
        g.setColor(c(0x3A4049));
        g.fillRect(10, 58, 108, 6);
        g.setColor(c(0xF2C230));
        g.fill(new RoundRectangle2D.Double(52, 54, 24, 14, 4, 4));
        // The cyan cross and a pane of glass.
        g.setColor(c(0x13A7D6));
        g.fill(new RoundRectangle2D.Double(56, 72, 16, 32, 4, 4));
        g.fill(new RoundRectangle2D.Double(48, 80, 32, 16, 4, 4));
        g.setColor(new Color(190, 240, 255, 200));
        Polygon pane = new Polygon(new int[]{86, 108, 104, 82}, new int[]{72, 76, 102, 98}, 4);
        g.fill(pane);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(90, 78, 96, 94);
        // A wrench.
        g.setColor(c(0x6E7783));
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(20, 100, 40, 76);
        g.fill(new Ellipse2D.Double(34, 66, 16, 16));
        g.setColor(c(0xE6E9ED));
        g.fill(new Ellipse2D.Double(39, 70, 7, 7));
        g.dispose();
        return img;
    }

    // ---------------------------------------------------------------- previews (software rasteriser)

    static class GroupData {
        String name, module, material, variant, anim;
        float[] v;
    }

    static List<GroupData> readMesh() throws Exception {
        String text = new String(Files.readAllBytes(new File(ASSETS + "rks/rks_machine_mesh.json").toPath()), "UTF-8");
        Object root = new Json(text).value();
        List<GroupData> out = new ArrayList<>();
        @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) root;
        @SuppressWarnings("unchecked") List<Object> groups = (List<Object>) map.get("groups");
        for (Object o : groups) {
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
            GroupData g = new GroupData();
            g.name = (String) m.get("name");
            g.module = (String) m.get("module");
            g.material = (String) m.get("material");
            g.variant = (String) m.get("variant");
            g.anim = (String) m.get("anim");
            @SuppressWarnings("unchecked") List<Object> v = (List<Object>) m.get("v");
            g.v = new float[v.size()];
            for (int i = 0; i < g.v.length; i++) g.v[i] = ((Number) v.get(i)).floatValue();
            out.add(g);
        }
        return out;
    }

    /** A minimal JSON reader (objects, arrays, strings, numbers, booleans). */
    static class Json {
        final String s;
        int i = 0;
        Json(String s) { this.s = s; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object value() {
            ws();
            char ch = s.charAt(i);
            if (ch == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = (String) value();
                    ws(); i++; // :
                    m.put(k, value());
                    ws();
                    if (s.charAt(i++) == '}') return m;
                }
            }
            if (ch == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(value());
                    ws();
                    if (s.charAt(i++) == ']') return l;
                }
            }
            if (ch == '"') {
                int j = s.indexOf('"', i + 1);
                String out = s.substring(i + 1, j);
                i = j + 1;
                return out;
            }
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            int j = i;
            while (j < s.length() && "+-0123456789.eE".indexOf(s.charAt(j)) >= 0) j++;
            double d = Double.parseDouble(s.substring(i, j));
            i = j;
            return d;
        }
    }

    static void previews(BufferedImage atlas) throws Exception {
        List<GroupData> mesh = readMesh();
        int w = 1200, h = 900;
        BufferedImage sheet = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = g(sheet);
        g.setColor(new Color(0x1A1D22));
        g.fillRect(0, 0, w, h);
        // Assembled, front three-quarter; back three-quarter; damaged; then the modules alone.
        render(sheet, atlas, mesh, 0, 0, 400, 500, "all", false, 35, 18, 9.0, "Assemblée (avant)");
        render(sheet, atlas, mesh, 400, 0, 400, 500, "all", false, 200, 22, 9.0, "Assemblée (arrière)");
        render(sheet, atlas, mesh, 800, 0, 400, 500, "all", true, -30, 14, 9.0, "Endommagée");
        String[] modules = {"console", "chamber", "reactor", "power", "analyzer"};
        String[] names = {"Console", "Chambre", "Réacteur", "Alimentation", "Analyseur"};
        for (int k = 0; k < 5; k++) render(sheet, atlas, mesh, k * 240, 500, 240, 400, modules[k], false, 30, 20, 7.0, names[k]);
        g.dispose();
        File out = new File("tools/previews/rks_machine_previews.png");
        out.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
        System.out.println("wrote " + out.getPath());
    }

    /** One view: the groups of a module (or all, with the links), intact or damaged. */
    static void render(BufferedImage target, BufferedImage atlas, List<GroupData> mesh, int ox, int oy, int w, int h, String module, boolean damaged,
                       double yawDeg, double pitchDeg, double distance, String label) {
        int ss = 2;
        int W = w * ss, H = h * ss;
        float[] depth = new float[W * H];
        Arrays.fill(depth, Float.MAX_VALUE);
        int[] colour = new int[W * H];
        Arrays.fill(colour, 0xFF1A1D22);
        // The view's target: the middle of what is drawn.
        double[] centre = module.equals("all") ? new double[]{0, 1.8, 0} : moduleCentre(mesh, module);
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double camX = centre[0] + distance * Math.cos(pitch) * Math.sin(yaw);
        double camY = centre[1] + distance * Math.sin(pitch);
        double camZ = centre[2] + distance * Math.cos(pitch) * Math.cos(yaw);
        double[] f = normalise(new double[]{centre[0] - camX, centre[1] - camY, centre[2] - camZ});
        double[] right = normalise(cross(f, new double[]{0, 1, 0}));
        double[] up = cross(right, f);
        double focal = H * 1.15 * (module.equals("all") ? 1.0 : 1.25);
        double[] light = normalise(new double[]{0.4, 0.85, 0.55});
        // Opaque first, then the glass blended over (back to front).
        List<float[]> glassTris = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            for (GroupData gd : mesh) {
                boolean mine = module.equals("all") || gd.module.equals(module);
                if (!mine) continue;
                if (gd.variant.equals("damaged") && !damaged) continue;
                if (gd.variant.equals("intact") && damaged) continue;
                boolean glass = gd.material.equals("glass");
                if ((pass == 0) == glass) continue;
                for (int t = 0; t + 24 <= gd.v.length; t += 24) {
                    if (glass) {
                        float[] tri = Arrays.copyOfRange(gd.v, t, t + 24);
                        glassTris.add(tri);
                        continue;
                    }
                    raster(gd, gd.v, t, camX, camY, camZ, f, right, up, focal, W, H, depth, colour, atlas, light, false);
                }
            }
        }
        final double cx = camX, cy = camY, cz = camZ;
        glassTris.sort((a, b) -> Double.compare(dist2(b, cx, cy, cz), dist2(a, cx, cy, cz)));
        GroupData glassGroup = new GroupData();
        glassGroup.material = "glass";
        for (float[] tri : glassTris) raster(glassGroup, tri, 0, camX, camY, camZ, f, right, up, focal, W, H, depth, colour, atlas, light, true);
        // Down-sample into the sheet.
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int r = 0, gg = 0, b = 0;
            for (int sy = 0; sy < ss; sy++) for (int sx = 0; sx < ss; sx++) {
                int p = colour[(y * ss + sy) * W + x * ss + sx];
                r += (p >> 16) & 255; gg += (p >> 8) & 255; b += p & 255;
            }
            int n = ss * ss;
            target.setRGB(ox + x, oy + y, ((r / n) << 16) | ((gg / n) << 8) | (b / n));
        }
        Graphics2D g = g(target);
        g.setColor(new Color(0xCFD8E3));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.drawString(label, ox + 12, oy + 24);
        g.dispose();
    }

    static double dist2(float[] t, double x, double y, double z) {
        double mx = (t[0] + t[8] + t[16]) / 3 - x, my = (t[1] + t[9] + t[17]) / 3 - y, mz = (t[2] + t[10] + t[18]) / 3 - z;
        return mx * mx + my * my + mz * mz;
    }

    static double[] moduleCentre(List<GroupData> mesh, String module) {
        double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
        for (GroupData g : mesh) {
            if (!g.module.equals(module)) continue;
            for (int i = 0; i < g.v.length; i += 8) for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], g.v[i + k]);
                hi[k] = Math.max(hi[k], g.v[i + k]);
            }
        }
        return new double[]{(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2};
    }

    static double[] normalise(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return new double[]{a[0] / l, a[1] / l, a[2] / l};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static void raster(GroupData gd, float[] v, int t, double camX, double camY, double camZ, double[] f, double[] right, double[] up, double focal,
                       int W, int H, float[] depth, int[] colour, BufferedImage atlas, double[] light, boolean blend) {
        double[] sx = new double[3], sy = new double[3], sz = new double[3], uu = new double[3], vv = new double[3];
        double shadeSum = 0;
        for (int k = 0; k < 3; k++) {
            int o = t + k * 8;
            double dx = v[o] - camX, dy = v[o + 1] - camY, dz = v[o + 2] - camZ;
            double z = dx * f[0] + dy * f[1] + dz * f[2];
            if (z < 0.1) return;
            double x = dx * right[0] + dy * right[1] + dz * right[2];
            double y = dx * up[0] + dy * up[1] + dz * up[2];
            sx[k] = W / 2.0 + focal * x / z;
            sy[k] = H * 0.55 - focal * y / z;
            sz[k] = z;
            uu[k] = v[o + 6];
            vv[k] = v[o + 7];
            double n = v[o + 3] * light[0] + v[o + 4] * light[1] + v[o + 5] * light[2];
            // Both sides lit (the mesh is drawn without culling in game).
            double facing = v[o + 3] * -f[0] + v[o + 4] * -f[1] + v[o + 5] * -f[2];
            if (facing < 0) n = -n * 0.6 + 0.0;
            shadeSum += 0.45 + 0.55 * Math.max(0, n);
        }
        double shade = shadeSum / 3;
        boolean bright = gd.material.equals("glow") || gd.material.equals("add");
        int minX = (int) Math.max(0, Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2]))));
        int maxX = (int) Math.min(W - 1, Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
        int minY = (int) Math.max(0, Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2]))));
        int maxY = (int) Math.min(H - 1, Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
        double area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]);
        if (Math.abs(area) < 1e-9) return;
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
            double px = x + 0.5, py = y + 0.5;
            double w0 = ((sx[1] - px) * (sy[2] - py) - (sx[2] - px) * (sy[1] - py)) / area;
            double w1 = ((sx[2] - px) * (sy[0] - py) - (sx[0] - px) * (sy[2] - py)) / area;
            double w2 = 1 - w0 - w1;
            if (w0 < 0 || w1 < 0 || w2 < 0) continue;
            // Perspective-correct attributes.
            double iz = w0 / sz[0] + w1 / sz[1] + w2 / sz[2];
            double z = 1 / iz;
            int idx = y * W + x;
            if (z >= depth[idx] - (blend ? 0.0 : 1e-5)) continue;
            double u = (w0 * uu[0] / sz[0] + w1 * uu[1] / sz[1] + w2 * uu[2] / sz[2]) * z;
            double vt = (w0 * vv[0] / sz[0] + w1 * vv[1] / sz[1] + w2 * vv[2] / sz[2]) * z;
            int tx = Math.max(0, Math.min(511, (int) (u * 512))), ty = Math.max(0, Math.min(511, (int) (vt * 512)));
            int texel = atlas.getRGB(tx, ty);
            int a = (texel >>> 24) & 255;
            double k = bright ? 1.0 : shade;
            int r = clamp((int) (((texel >> 16) & 255) * k)), gg = clamp((int) (((texel >> 8) & 255) * k)), b = clamp((int) ((texel & 255) * k));
            if (bright) {
                // The machine's lights are cyan by default.
                r = clamp((int) (r * 0.45)); gg = clamp((int) (gg * 0.92));
            }
            if (blend) {
                double alpha = a / 255.0;
                int under = colour[idx];
                r = (int) (r * alpha + ((under >> 16) & 255) * (1 - alpha));
                gg = (int) (gg * alpha + ((under >> 8) & 255) * (1 - alpha));
                b = (int) (b * alpha + (under & 255) * (1 - alpha));
                colour[idx] = 0xFF000000 | (r << 16) | (gg << 8) | b;
            } else {
                if (a < 64) continue;
                depth[idx] = (float) z;
                colour[idx] = 0xFF000000 | (r << 16) | (gg << 8) | b;
            }
        }
    }
}
