import generations.gg.generations.core.generationscore.common.client.cinematic.audio.HoOhMusic;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CamFrame;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMotion;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorKey;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorPose;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.HoOhScript;
import generations.gg.generations.core.generationscore.common.world.hooh.HoOhFlight;
import generations.gg.generations.core.generationscore.common.world.hooh.HoOhMath;
import generations.gg.generations.core.generationscore.common.world.hooh.HoOhRecord;
import generations.gg.generations.core.generationscore.common.world.hooh.HoOhRules;
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksPoints;
import org.joml.Vector3d;

import java.util.Random;

/**
 * Off-game checks of the Passage of the Rainbow (HOOH-PLAN.md § 14), on the compiled classes:
 * the rarity (exact and Monte-Carlo through HoOhMath, never two draws at once, the delays), the
 * flight (continuity, settled length, motion, circling), the scene (timeline, Ho-Oh's path and
 * speed, the camera above the ground and without jumps, the hand-over to the pursuit), the music
 * (no clipping), the fire scale (RksPoints).
 *
 *   javac -cp <classes>;<kotlin-stdlib>;<joml> -d <out> tools/HoOhBench.java
 *   java -cp <out>;<classes>;<kotlin-stdlib>;<joml> HoOhBench
 */
public class HoOhBench {
    static int passed = 0, failed = 0;

    static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] args) {
        rarity();
        flight();
        scene();
        music();
        points();
        System.out.println(passed + "/" + (passed + failed) + " checks passed");
        if (failed > 0) System.exit(1);
    }

    static HoOhRules rules(double chance) {
        return new HoOhRules(chance, 10L * 60 * 20, 2L * 72000, 24L * 3_600_000);
    }

    static void rarity() {
        HoOhRules r = rules(0.0075);
        check(Math.abs(HoOhMath.INSTANCE.meanHours(r) - 24.06) < 0.02, "mean 24.06 h: " + HoOhMath.INSTANCE.meanHours(r));
        double median = HoOhMath.INSTANCE.quantileHours(r, 0.5);
        check(Math.abs(median - 17.33) < 0.02, "median 17.33 h: " + median);
        check(Math.abs(HoOhMath.INSTANCE.within(r, 5.0) - 0.1331) < 0.001, "13.3 % within 5 h: " + HoOhMath.INSTANCE.within(r, 5.0));
        check(Math.abs(1 - HoOhMath.INSTANCE.within(r, 50.0) - 0.1138) < 0.001, "11.4 % after 50 h: " + (1 - HoOhMath.INSTANCE.within(r, 50.0)));
        // A draw every 10 active minutes, never two at once after a long gap.
        HoOhRecord rec = new HoOhRecord();
        int due = 0;
        for (int i = 0; i < 40; i++) if (HoOhMath.INSTANCE.due(rec, 600, r)) due++;
        check(due == 2, "two draws in 20 min of 30 s checks: " + due);
        HoOhRecord gap = new HoOhRecord();
        check(HoOhMath.INSTANCE.due(gap, 1200L * 20, r), "a draw after a long credit");
        check(!HoOhMath.INSTANCE.due(gap, 600, r), "no catching up after it");
        // Eligibility: 2 h of active play and 24 real hours.
        HoOhRecord e = new HoOhRecord();
        e.setActiveTicks(72000L);
        check(!HoOhMath.INSTANCE.eligible(e, r, 1_000_000L), "not before 2 h");
        e.setActiveTicks(2 * 72000L);
        check(HoOhMath.INSTANCE.eligible(e, r, 1_000_000L), "after 2 h, never passed");
        HoOhMath.INSTANCE.passed(e, 1_000_000L);
        check(e.getActiveTicks() == 0 && e.getPassages() == 1, "passed: counters reset");
        e.setActiveTicks(5 * 72000L);
        check(!HoOhMath.INSTANCE.eligible(e, r, 1_000_000L + 23L * 3_600_000), "not within 24 real hours");
        check(HoOhMath.INSTANCE.eligible(e, r, 1_000_000L + 24L * 3_600_000), "after 24 real hours");
        // Monte-Carlo: 200 000 players, 30 s checks, the active time to the first passage.
        Random random = new Random(1234);
        int n = 100_000;
        double sum = 0;
        int within5 = 0, after50 = 0;
        for (int k = 0; k < n; k++) {
            HoOhRecord p = new HoOhRecord();
            long ticks = 0;
            while (true) {
                ticks += 600;
                if (HoOhMath.INSTANCE.due(p, 600, r) && HoOhMath.INSTANCE.eligible(p, r, Long.MAX_VALUE / 2) && random.nextDouble() < r.getChance()) break;
                if (ticks > 72000L * 500) break;
            }
            double h = ticks / 72000.0;
            sum += h;
            if (h <= 5.0) within5++;
            if (h > 50.0) after50++;
        }
        double mean = sum / n;
        check(Math.abs(mean - HoOhMath.INSTANCE.meanHours(r)) < 0.25, "Monte-Carlo mean as computed: " + mean);
        check(Math.abs(within5 / (double) n - HoOhMath.INSTANCE.within(r, 5.0)) < 0.006, "Monte-Carlo within 5 h: " + within5 / (double) n);
        check(Math.abs(after50 / (double) n - (1 - HoOhMath.INSTANCE.within(r, 50.0))) < 0.006, "Monte-Carlo after 50 h: " + after50 / (double) n);
        System.out.printf("rarity: mean %.2f h, median %.1f h, within 5 h %.1f %%, after 50 h %.1f %%%n", mean, median, 100.0 * within5 / n, 100.0 * after50 / n);
    }

    static void flight() {
        HoOhFlight f = new HoOhFlight();
        f.add(0, 80, 0);
        check(f.getSettled() == 0.0, "one point: nothing settled");
        f.add(30, 82, 5);
        f.add(58, 79, 20);
        check(f.getSettled() > 25 && f.getSettled() < 40, "one settled segment: " + f.getSettled());
        double before = f.getSettled();
        double[] a = new double[3];
        f.at(before * 0.5, a);
        f.add(85, 81, 40);
        f.add(110, 84, 48);
        double[] b = new double[3];
        f.at(before * 0.5, b);
        check(dist(a, b) < 1e-9, "a settled segment never changes when points are added");
        // Continuity along the curve: no step longer than the distance walked.
        double[] p = new double[3], q = new double[3];
        f.at(0, p);
        double maxStep = 0;
        for (double s = 0.25; s <= f.getSettled(); s += 0.25) {
            f.at(s, q);
            maxStep = Math.max(maxStep, dist(p, q));
            System.arraycopy(q, 0, p, 0, 3);
        }
        check(maxStep < 0.3, "continuous along the curve: " + maxStep);
        // Motion: 5 blocks a second, clamped to the settled length.
        f.setMotion(100, 0, 5);
        check(Math.abs(f.sAt(120) - 5.0) < 1e-9, "5 blocks after one second");
        check(f.sAt(100 + 20 * 1000) == f.getSettled(), "never beyond the settled length");
        // Circling: radius 10 round its point, a little higher.
        f.setMotion(200, 10, 0);
        double[] c = new double[3], centre = new double[3];
        f.at(10, centre);
        double maxR = 0, minR = 1e9;
        for (int t = 0; t < 400; t++) {
            f.position(200 + t, c);
            double r = Math.hypot(c[0] - centre[0], c[2] - centre[2]);
            maxR = Math.max(maxR, r);
            minR = Math.min(minR, r);
        }
        check(Math.abs(maxR - 10) < 1e-6 && Math.abs(minR - 10) < 1e-6, "circles at 10 blocks");
    }

    static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    static void scene() {
        for (Vector3d landing : new Vector3d[]{new Vector3d(0, 22, 36), new Vector3d(0, 30, 36), new Vector3d(0, 16, 36)}) {
            HoOhScript s = new HoOhScript(landing);
            String label = "scene (landing y " + landing.y + ")";
            check(Math.abs(s.action(HoOhScript.END) - HoOhScript.END) < 0.01, label + ": on time at the end " + s.action(HoOhScript.END));
            ActorKey key = new ActorKey();
            s.actor(HoOhScript.EMERGE - 0.1, key);
            check(key.getPose() == ActorPose.HIDDEN, label + ": hidden before it comes out");
            s.actor(HoOhScript.END, key);
            check(key.getPos().distance(landing) < 1e-6, label + ": at the pursuit's first point at 13 s");
            // Its speed: never a teleport; about the pursuit's at the hand-over.
            Vector3d prev = new Vector3d(), cur = new Vector3d();
            double maxSpeed = 0;
            s.hoohPos(s.action(HoOhScript.EMERGE), prev);
            for (double t = HoOhScript.EMERGE + 0.02; t <= HoOhScript.END; t += 0.02) {
                s.hoohPos(s.action(t), cur);
                maxSpeed = Math.max(maxSpeed, cur.distance(prev) / 0.02);
                prev.set(cur);
            }
            check(maxSpeed < 40, label + ": no jump (max " + (int) maxSpeed + " blocks/s)");
            Vector3d e0 = new Vector3d(), e1 = new Vector3d();
            s.hoohPos(HoOhScript.END - 0.2, e0);
            s.hoohPos(HoOhScript.END, e1);
            double endSpeed = e0.distance(e1) / 0.2;
            check(endSpeed > 2 && endSpeed < 14, label + ": hand-over speed " + endSpeed);
            // Its pass over the player: close and above.
            s.hoohPos(HoOhScript.ZENITH, cur);
            check(cur.y > 10 && Math.hypot(cur.x, cur.z) < 12, label + ": the pass over the player " + cur);
            // The camera: above the ground, no jump, sane field of view.
            CameraMotion motion = new CameraMotion(false);
            CamFrame frame = new CamFrame();
            Vector3d last = null;
            double minY = 1e9, maxJump = 0, minFov = 1e9, maxFov = 0;
            for (double t = 0; t <= HoOhScript.END; t += 0.02) {
                s.getDirector().evaluate(t, motion, frame);
                minY = Math.min(minY, frame.getPos().y);
                minFov = Math.min(minFov, frame.getFov());
                maxFov = Math.max(maxFov, frame.getFov());
                // A shot with no blend is a planned cut: the jump across it is allowed.
                boolean cut = false;
                for (var shot : s.getDirector().getShots()) if (shot.getBlendIn() == 0.0 && shot.getStart() > 0 && t - 0.02 < shot.getStart() && t >= shot.getStart()) cut = true;
                if (last != null && !cut) maxJump = Math.max(maxJump, frame.getPos().distance(last));
                last = new Vector3d(frame.getPos());
            }
            check(minY >= 0.5, label + ": camera above the ground " + minY);
            check(maxJump < 0.6, label + ": camera without jump " + maxJump);
            check(minFov >= 35 && maxFov <= 80, label + ": field of view " + minFov + " to " + maxFov);
            check(s.cameraWeight(0) < 0.01 && s.cameraWeight(HoOhScript.END) < 0.01, label + ": the player's own view at both ends");
            check(s.letterbox(HoOhScript.END) < 0.01, label + ": no bars at the end");
            for (int i = 1; i < s.getCues().size(); i++) check(s.getCues().get(i).getTime() >= s.getCues().get(i - 1).getTime(), label + ": cues in order");
        }
    }

    static void music() {
        HoOhScript s = new HoOhScript(new Vector3d(0, 22, 36));
        for (int part = 0; part < 2; part++) {
            SynthRenderer r = part == 0 ? HoOhMusic.INSTANCE.music(s, 1.0, 0.0) : HoOhMusic.INSTANCE.ambience(s, 1.0, 0.0);
            int rate = 44100;
            int total = (int) (r.getLength() * rate);
            float[] l = new float[4096], rr = new float[4096];
            float peak = 0;
            int done = 0;
            while (done < total) {
                int n = Math.min(4096, total - done);
                r.render(l, rr, n, 0);
                for (int i = 0; i < n; i++) peak = Math.max(peak, Math.max(Math.abs(l[i]), Math.abs(rr[i])));
                done += n;
            }
            check(peak > 0.02 && peak <= 1.0, (part == 0 ? "music" : "ambience") + " heard, no clipping: peak " + peak);
        }
    }

    static void points() {
        // Charmander 1, Charmeleon 2, Charizard 3, a lone Fire Pokémon 2 (the Mewtwo scale, rks/fire_energy.json).
        int first = RksPoints.INSTANCE.points(RksPoints.INSTANCE.stage(false, true), 1, 2, 3, 2, null);
        int middle = RksPoints.INSTANCE.points(RksPoints.INSTANCE.stage(true, true), 1, 2, 3, 2, null);
        int last = RksPoints.INSTANCE.points(RksPoints.INSTANCE.stage(true, false), 1, 2, 3, 2, null);
        int alone = RksPoints.INSTANCE.points(RksPoints.INSTANCE.stage(false, false), 1, 2, 3, 2, null);
        check(first == 1 && middle == 2 && last == 3 && alone == 2, "fire scale 1/2/3/2");
        int[] credit = RksPoints.INSTANCE.credit(140, 150, 25);
        check(credit[0] == 10 && credit[1] == 15, "fire reserve capped at 150, 15 lost");
        check(!RksPoints.INSTANCE.eligible(true, "cobblemon:hooh", java.util.List.of("legendary"), java.util.List.of("legendary", "mythical"), java.util.List.of(), java.util.List.of()), "legendary refused");
        check(RksPoints.INSTANCE.eligible(true, "cobblemon:charmander", java.util.List.of(), java.util.List.of("legendary"), java.util.List.of(), java.util.List.of()), "Charmander accepted");
        check(!RksPoints.INSTANCE.eligible(false, "cobblemon:abra", java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()), "not of the type: refused");
    }
}
