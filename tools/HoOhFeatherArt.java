import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * The Sacred Feather of Ho-Oh's texture (HOOH-PLAN.md § 8), drawn by code at 512 × 512 and
 * reduced to 128 × 128 (4 × 4 averaging, premultiplied alpha): a golden feather with red and
 * orange reflections, a glowing tip, a thin rainbow sheen on its outer edge, a downy afterfeather
 * and a dark outline for the inventory. Writes the item's texture and an enlarged preview.
 *
 *   javac -d <out> tools/HoOhFeatherArt.java
 *   java -cp <out> HoOhFeatherArt <Generations-Core>
 */
public class HoOhFeatherArt {
    static final int S = 512, N = 128, K = S / N;
    static final int SAMPLES = 600;
    static final double[] CX = new double[SAMPLES + 1], CY = new double[SAMPLES + 1], ARC = new double[SAMPLES + 1];

    // The shaft: a quadratic curve from the quill (bottom left) to the tip (top right).
    static final double X0 = 100, Y0 = 426, XC = 286, YC = 292, X1 = 446, Y1 = 62;

    static double clamp(double v, double a, double b) { return v < a ? a : v > b ? b : v; }

    static double smooth(double a, double b, double x) {
        double t = clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    static double hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    /** Smooth value noise. */
    static double noise(double x, double y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        double sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
        double a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), d = hash(ix + 1, iy + 1);
        return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy;
    }

    /** The vane's colour along the shaft (t from the quill to the tip). */
    static double[] gradient(double t) {
        double[][] stops = {
            {0.20, 0.40, 0.05, 0.03},
            {0.34, 0.52, 0.07, 0.03},
            {0.46, 0.80, 0.15, 0.05},
            {0.60, 0.95, 0.34, 0.09},
            {0.74, 1.00, 0.60, 0.14},
            {0.87, 1.00, 0.82, 0.30},
            {1.00, 1.00, 0.97, 0.80},
        };
        if (t <= stops[0][0]) return new double[]{stops[0][1], stops[0][2], stops[0][3]};
        for (int i = 1; i < stops.length; i++) {
            if (t <= stops[i][0]) {
                double u = smooth(stops[i - 1][0], stops[i][0], t);
                return new double[]{
                    stops[i - 1][1] + (stops[i][1] - stops[i - 1][1]) * u,
                    stops[i - 1][2] + (stops[i][2] - stops[i - 1][2]) * u,
                    stops[i - 1][3] + (stops[i][3] - stops[i - 1][3]) * u,
                };
            }
        }
        double[] l = stops[stops.length - 1];
        return new double[]{l[1], l[2], l[3]};
    }

    static double[] hue(double h) {
        h = h - Math.floor(h);
        double r = clamp(Math.abs(h * 6 - 3) - 1, 0, 1);
        double g = clamp(2 - Math.abs(h * 6 - 2), 0, 1);
        double b = clamp(2 - Math.abs(h * 6 - 4), 0, 1);
        return new double[]{r, g, b};
    }

    /** Half-width of the vane at t, on the outer (right, side > 0) or inner side. */
    static double vane(double t, boolean outer) {
        if (t < 0.19) return 0;
        double u = (t - 0.19) / 0.81;
        double rise = Math.pow(smooth(0.0, 0.36, u), 0.85);
        double taper = u < 0.58 ? 1 : Math.sqrt(Math.max(0, 1 - Math.pow((u - 0.58) / 0.42, 2)));
        double bulge = 1 + 0.08 * Math.sin(Math.PI * u);
        return (outer ? 66 : 46) * rise * taper * bulge;
    }

    static double down(double t) {
        if (t < 0.07 || t > 0.27) return 0;
        return 40 * Math.sin(Math.PI * (t - 0.07) / 0.20);
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        double length = 0;
        for (int i = 0; i <= SAMPLES; i++) {
            double t = i / (double) SAMPLES, m = 1 - t;
            CX[i] = m * m * X0 + 2 * m * t * XC + t * t * X1;
            CY[i] = m * m * Y0 + 2 * m * t * YC + t * t * Y1;
            if (i > 0) length += Math.hypot(CX[i] - CX[i - 1], CY[i] - CY[i - 1]);
            ARC[i] = length;
        }
        double[][] big = new double[S * S][];
        for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) big[y * S + x] = shade(x + 0.5, y + 0.5, length);

        // 4 × 4 averaging, premultiplied.
        double[] r = new double[N * N], g = new double[N * N], b = new double[N * N], a = new double[N * N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            double sr = 0, sg = 0, sb = 0, sa = 0;
            for (int j = 0; j < K; j++) for (int i = 0; i < K; i++) {
                double[] p = big[(y * K + j) * S + x * K + i];
                sr += p[0] * p[3]; sg += p[1] * p[3]; sb += p[2] * p[3]; sa += p[3];
            }
            int k = y * N + x;
            a[k] = sa / (K * K);
            if (sa > 0) { r[k] = sr / sa; g[k] = sg / sa; b[k] = sb / sa; }
        }
        // Outline: dark red brown around the body, for the inventory.
        double[] outline = new double[N * N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int k = y * N + x;
            if (a[k] > 0.45) continue;
            double m = 0;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int xx = x + d[0], yy = y + d[1];
                if (xx < 0 || yy < 0 || xx >= N || yy >= N) continue;
                m = Math.max(m, a[yy * N + xx]);
            }
            outline[k] = smooth(0.35, 0.75, m);
        }
        // Glow: a soft golden halo round the tip, a faint orange one round the feather.
        double tipX = X1 / K, tipY = Y1 / K;
        BufferedImage out = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int k = y * N + x;
            double halo = 0;
            for (int j = -3; j <= 3; j++) for (int i = -3; i <= 3; i++) {
                int xx = x + i, yy = y + j;
                if (xx < 0 || yy < 0 || xx >= N || yy >= N) continue;
                halo += a[yy * N + xx];
            }
            halo = halo / 49.0;
            double dt = Math.hypot(x + 0.5 - tipX, y + 0.5 - tipY);
            double tip = Math.max(0, 1 - dt / 17.0);
            tip = tip * tip;
            double ga = Math.min(1, 0.30 * halo + 0.62 * tip);
            double gr = 1.0, gg = 0.62 + 0.30 * tip, gb = 0.18 + 0.45 * tip;
            // outline over glow
            double oa = outline[k] * 0.95;
            double cr = 0.26 * oa + gr * ga * (1 - oa), cg = 0.05 * oa + gg * ga * (1 - oa), cb = 0.03 * oa + gb * ga * (1 - oa);
            double ca = oa + ga * (1 - oa);
            // body over everything
            double ba = a[k];
            double fr = r[k] * ba + cr * (1 - ba), fg = g[k] * ba + cg * (1 - ba), fb = b[k] * ba + cb * (1 - ba);
            double fa = ba + ca * (1 - ba);
            if (fa > 0) { fr /= fa; fg /= fa; fb /= fa; }
            int A = (int) Math.round(clamp(fa, 0, 1) * 255);
            out.setRGB(x, y, (A << 24) | ((int) Math.round(clamp(fr, 0, 1) * 255) << 16) | ((int) Math.round(clamp(fg, 0, 1) * 255) << 8) | (int) Math.round(clamp(fb, 0, 1) * 255));
        }
        File texture = new File(root, "common/src/main/resources/assets/generations_core/textures/item/ho_oh_sacred_feather.png");
        texture.getParentFile().mkdirs();
        ImageIO.write(out, "png", texture);
        // Preview: ×4 on a dark and a light background.
        int P = 4;
        BufferedImage preview = new BufferedImage(N * P * 2, N * P, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < N * P; y++) for (int x = 0; x < N * P * 2; x++) {
            boolean light = x >= N * P;
            int px = (x % (N * P)) / P, py = y / P;
            int c = out.getRGB(px, py);
            double al = ((c >>> 24) & 255) / 255.0;
            double bg = light ? (((px / 8 + py / 8) & 1) == 0 ? 0.78 : 0.70) : 0.12;
            int rr = (int) (((c >> 16) & 255) * al + bg * 255 * (1 - al));
            int gg = (int) (((c >> 8) & 255) * al + bg * 255 * (1 - al));
            int bb = (int) ((c & 255) * al + (light ? bg : bg * 1.2) * 255 * (1 - al));
            preview.setRGB(x, y, (Math.min(rr, 255) << 16) | (Math.min(gg, 255) << 8) | Math.min(bb, 255));
        }
        File previewFile = new File(root, "tools/previews/ho_oh_sacred_feather_preview.png");
        previewFile.getParentFile().mkdirs();
        ImageIO.write(preview, "png", previewFile);
        System.out.println("written " + texture + " and " + previewFile);
    }

    /** The colour (r, g, b, a) of the feather at one point of the 512 × 512 drawing. */
    static double[] shade(double x, double y, double length) {
        // The nearest point of the shaft.
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i <= SAMPLES; i++) {
            double dx = x - CX[i], dy = y - CY[i];
            double d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = i; }
        }
        double t = best / (double) SAMPLES;
        int i0 = Math.max(0, best - 1), i1 = Math.min(SAMPLES, best + 1);
        double tx = CX[i1] - CX[i0], ty = CY[i1] - CY[i0];
        double tl = Math.hypot(tx, ty);
        tx /= tl; ty /= tl;
        // Signed distance: > 0 on the outer (right, lower) side.
        double side = (x - CX[best]) * (-ty) + (y - CY[best]) * tx;
        double dist = Math.abs(side);
        boolean outer = side > 0;
        double along = ARC[best];
        double beyond = (best == SAMPLES) ? Math.hypot(x - X1, y - Y1) : 0;
        double[] none = {0, 0, 0, 0};

        // The shaft: ivory at the quill, gold toward the tip, a highlight along it.
        double shaftW = 7.0 * (1 - t) + 1.6;
        if (t < 0.02) shaftW *= smooth(0.0, 0.02, t) * 0.7 + 0.3;
        if (beyond == 0 && dist < shaftW) {
            double u = dist / shaftW;
            double[] c = t < 0.30 ? new double[]{1.0, 0.93, 0.74} : mix(new double[]{1.0, 0.90, 0.62}, gradient(Math.min(1, t + 0.15)), 0.55);
            double light = 1.08 - 0.35 * u * u + (side < 0 ? 0.10 : -0.08) * u;
            double alpha = t < 0.20 ? 0.92 : 1.0;
            return new double[]{clamp(c[0] * light, 0, 1), clamp(c[1] * light, 0, 1), clamp(c[2] * light, 0, 1), alpha * (1 - smooth(0.85, 1.0, u))};
        }
        // The barbs run from the shaft toward the tip.
        double barb = along - dist * 0.78;
        double w = vane(t, outer);
        if (w > 0 && beyond < 1) {
            double edgeNoise = 1 + 0.05 * (noise(barb * 0.09, outer ? 3 : 9) - 0.5) * 2;
            double limit = w * edgeNoise;
            if (dist < limit) {
                // Two natural splits, one on each side.
                double split = outer ? 0.60 * length : 0.76 * length;
                double notch = 1.2 + 4.5 * clamp(dist / w - 0.35, 0, 1);
                if (Math.abs(barb - split) < notch && dist > 0.35 * w) return none;
                double u = dist / limit;
                double[] c = gradient(t + 0.06 * u);
                // Stripes of the barbs, finer near the tip.
                double period = 12.0 - 4.0 * t;
                double stripe = 0.5 + 0.5 * Math.sin(barb * 2 * Math.PI / period + 1.5 * noise(barb * 0.05, dist * 0.03));
                double grain = noise(barb * 0.35, dist * 0.08);
                double light = 0.80 + 0.26 * stripe * smooth(0.08, 0.30, u) + 0.10 * (grain - 0.5);
                light *= 1.10 - 0.28 * u * u;
                light *= outer ? 0.96 : 1.06;
                // A golden sheen along the shaft.
                double sheen = Math.exp(-Math.pow((dist - 10) / 9.0, 2)) * 0.25 * smooth(0.35, 0.7, t);
                double[] col = {c[0] * light + sheen, c[1] * light + sheen * 0.8, c[2] * light + sheen * 0.3};
                // The rainbow sheen on the outer edge.
                if (outer) {
                    double f = smooth(0.74, 0.90, u) * (1 - smooth(0.96, 1.0, u)) * smooth(0.32, 0.48, t) * (1 - smooth(0.88, 0.97, t));
                    if (f > 0) {
                        double[] h = hue(0.30 + 0.75 * (t - 0.32) + 0.06 * Math.sin(barb * 0.05));
                        col = mix(col, new double[]{0.45 + 0.55 * h[0], 0.45 + 0.55 * h[1], 0.45 + 0.55 * h[2]}, 0.72 * f);
                    }
                }
                // The glowing tip.
                double glow = smooth(0.86, 1.0, t);
                col = mix(col, new double[]{1.0, 0.98, 0.86}, 0.55 * glow);
                double alpha = 1 - smooth(0.92, 1.0, u);
                return new double[]{clamp(col[0], 0, 1), clamp(col[1], 0, 1), clamp(col[2], 0, 1), alpha};
            }
        }
        // The downy afterfeather: separate wisps, pale and soft.
        double dw = down(t);
        if (dw > 0 && dist < dw) {
            double downBarb = along - dist * 1.1;
            double wisp = Math.sin(downBarb * 2 * Math.PI / 15.0);
            double u = dist / dw;
            double cut = 0.10 + 0.55 * u;
            if (wisp > cut) {
                double[] c = mix(new double[]{1.0, 0.84, 0.58}, gradient(0.34), 0.5 * (1 - u));
                double alpha = (0.92 - 0.50 * u) * smooth(cut, cut + 0.35, wisp);
                return new double[]{c[0], c[1], c[2], clamp(alpha, 0, 1)};
            }
        }
        return none;
    }

    static double[] mix(double[] a, double[] b, double f) {
        return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }
}
