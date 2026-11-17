import generations.gg.generations.core.generationscore.common.client.cinematic.audio.EonMusic;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthBuffer;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CamFrame;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMotion;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorKey;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorPose;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Cue;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Grade;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.LatiasScript;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.LatiosScript;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.SceneScript;
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes;
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicStateMachine;
import generations.gg.generations.core.generationscore.common.world.eon.EonConfig;
import generations.gg.generations.core.generationscore.common.world.eon.EonMachine;
import generations.gg.generations.core.generationscore.common.world.eon.EonMath;
import generations.gg.generations.core.generationscore.common.world.eon.EonPath;
import generations.gg.generations.core.generationscore.common.world.eon.EonRecord;
import generations.gg.generations.core.generationscore.common.world.eon.EonRules;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Off-game checks of the Eon manifestations and of Latias's and Latios's scenes (EON-PLAN.md
 * § 10), on the compiled classes: the exploration rules (moves, teleports, AFK-free samples,
 * presence, distance and new ground, region delays that slide, the biome memory), the draws'
 * calendar, the odds against the Python simulation, the manifestation's phases (one reward,
 * repeated clicks, a failure never after the reward), the way's curve and motion, the defaults
 * (an anomaly a walking player can follow); then, for both scenes and several landings, the
 * timeline, the camera (above the ground, no jump, sane field of view, the Pokémon in view at
 * the end), the actor (no teleport while shown, at the landing point at the spawn moment), the
 * envelopes, the cues, one Pokémon whatever happens, the music without clipping and ready in time.
 *
 *   javac -cp <classes>;<kotlin-stdlib>;<joml>;<gson> -d <out> tools/EonBench.java
 *   java -cp <out>;<classes>;<kotlin-stdlib>;<joml>;<gson> EonBench
 */
public class EonBench {
    static int passed = 0, failed = 0;

    static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    static boolean finite(double... values) {
        for (double v : values) if (!Double.isFinite(v)) return false;
        return true;
    }

    public static void main(String[] args) {
        EonConfig config = new EonConfig();
        config.validate();
        EonRules rules = config.rules();
        defaults(config, rules);
        moves(rules);
        calendar(rules);
        regions(rules);
        biomes(rules);
        machine();
        path();
        odds(rules);
        for (boolean test : new boolean[]{false, true}) {
            for (Vector3d landing : new Vector3d[]{new Vector3d(0, 0, 4), new Vector3d(0.8, 0.5, 4.5), new Vector3d(-1.2, -0.5, 3.2), new Vector3d(0.3, 1.0, 6.0)}) {
                scene("latias", new LatiasScript(landing, test), landing, test, LatiasScript.SECONDS);
                scene("latios", new LatiosScript(landing, test), landing, test, LatiosScript.SECONDS);
            }
        }
        illusions();
        stateMachine("latias");
        stateMachine("latios");
        music();
        System.out.println("EonBench: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- the fragments

    static void defaults(EonConfig c, EonRules rules) {
        EonConfig.Manifestation m = c.getManifestation();
        check(m.getSpeed() < 4.3, "the anomaly is slower than a walking player (" + m.getSpeed() + ")");
        check(m.getDashSpeed() < 5.6, "its dashes are slower than a running player (" + m.getDashSpeed() + ")");
        check(m.getLostRadius() > m.getFollowRadius() + 4, "lost only well beyond the follow radius");
        check(m.getPursuitMinSeconds() >= 60 && m.getPursuitMaxSeconds() <= 90, "a pursuit of 60 to 90 s");
        check(m.getMaxSeconds() >= m.getSignalSeconds() + m.getGazeSeconds() + m.getPursuitMaxSeconds() + m.getCoreSeconds(), "the limit leaves room for a whole manifestation");
        check(rules.getFirstTicks() == 9600 && rules.getIntervalTicks() == 9600, "first draw and interval at 8 min");
        check(Math.abs(rules.getChance() - 0.33) < 1e-9, "chance 33 %");
        check(!c.getLegacyBiomeFragments(), "the original fragment by biome is off (D2)");
        check(c.getExcludedBiomes().contains("#minecraft:is_ocean"), "the oceans are excluded by default");
        check(c.getDimensions().size() == 1 && c.getDimensions().get(0).equals("minecraft:overworld"), "the Overworld by default");
        // Out-of-range values are brought back.
        EonConfig bad = new EonConfig();
        bad.setChancePercent(250.0);
        bad.getManifestation().setSpeed(-3.0);
        bad.getManifestation().setLostRadius(1.0);
        bad.setRegionSize(1);
        bad.validate();
        check(bad.getChancePercent() == 100.0 && bad.getManifestation().getSpeed() >= 1.0 && bad.getManifestation().getLostRadius() > bad.getManifestation().getFollowRadius() && bad.getRegionSize() >= 32, "a bad file is clamped");
    }

    static void moves(EonRules rules) {
        EonMath math = EonMath.INSTANCE;
        check(math.move(120, 130, rules) == EonMath.Move.MOVED, "a walk of 120 blocks: moved");
        check(math.move(3, 4, rules) == EonMath.Move.STILL, "3 blocks: still");
        check(math.move(4, 60, rules) == EonMath.Move.MOVED, "walking in a small circle still moves (the cells will tell)");
        check(math.move(400, 20, rules) == EonMath.Move.TELEPORT, "400 blocks with 20 travelled: a teleport");
        check(math.move(30, 2, rules) == EonMath.Move.TELEPORT, "30 blocks with 2 travelled: a teleport");
        check(math.move(20, 2, rules) == EonMath.Move.MOVED, "20 blocks with 2 travelled: a knock-back, not a teleport");
        check(math.move(900, 905, rules) == EonMath.Move.MOVED, "a fast glide of 900 blocks: moved");
    }

    /** Samples of 30 s along a way: x, z each sample; returns the samples until the first due and ready draw (or -1). */
    static int walk(EonRecord r, EonRules rules, BiFunction<Integer, double[], double[]> at, int max) {
        EonMath math = EonMath.INSTANCE;
        double[] last = at.apply(0, new double[2]);
        for (int i = 1; i <= max; i++) {
            double[] p = at.apply(i, new double[2]);
            double dx = p[0] - last[0], dz = p[1] - last[1];
            double d = Math.sqrt(dx * dx + dz * dz);
            List<Long> cells = new ArrayList<>();
            math.cellsAlong(last[0], last[1], p[0], p[1], cells);
            if (math.move(d, d, rules) == EonMath.Move.MOVED) math.count(r, 600, Math.min(d, 480), cells);
            last = p;
            if (math.due(r, rules) && math.waiting(r, rules) == null) return i;
        }
        return -1;
    }

    static void calendar(EonRules rules) {
        EonMath math = EonMath.INSTANCE;
        // Walking straight on at about 3 blocks a second: a draw after 16 samples (8 min).
        EonRecord straight = new EonRecord();
        int n = walk(straight, rules, (i, out) -> { out[0] = i * 90.0; out[1] = 0; return out; }, 100);
        check(n == 16, "straight on: the first draw after 8 min (" + n + " samples)");
        math.drawn(straight);
        check(straight.getCounted() == 0 && !straight.getFresh() && straight.getCells().isEmpty() && !straight.getPreviousCells().isEmpty(), "after a draw: a new interval, its cells kept");
        int again = walk(straight, rules, (i, out) -> { out[0] = 1440 + i * 90.0; out[1] = 0; return out; }, 100);
        check(again == 16, "the next draw 8 min later (" + again + ")");
        // Round and round in a circle of 40 blocks: due, never ready after the first interval.
        EonRecord circle = new EonRecord();
        BiFunction<Integer, double[], double[]> round = (i, out) -> {
            double a = i * 2.4;
            out[0] = 40 * Math.cos(a);
            out[1] = 40 * Math.sin(a);
            return out;
        };
        int first = walk(circle, rules, round, 40);
        math.drawn(circle);
        int second = walk(circle, rules, round, 200);
        check(second == -1, "round and round in the same circle: no second draw (first " + first + ", then " + second + ")");
        check(math.due(circle, rules) && "cells".equals(math.waiting(circle, rules)), "the circle's draw waits for new ground (" + math.waiting(circle, rules) + ")");
        // Back and forth on a line of 300 blocks: the same cells, no new ground.
        EonRecord line = new EonRecord();
        BiFunction<Integer, double[], double[]> zigzag = (i, out) -> { out[0] = (i % 2 == 0) ? 0 : 300; out[1] = 0; return out; };
        walk(line, rules, zigzag, 40);
        math.drawn(line);
        check(walk(line, rules, zigzag, 200) == -1, "back and forth on the same line: no second draw");
        // Standing still: nothing counted.
        EonRecord still = new EonRecord();
        check(walk(still, rules, (i, out) -> { out[0] = 5; out[1] = 5; return out; }, 100) == -1 && still.getCounted() == 0, "standing still: nothing counted");
        // Presence: a teleport resets it; a draw waits for 2 min in a row.
        EonRecord fresh = new EonRecord();
        walk(fresh, rules, (i, out) -> { out[0] = i * 90.0; out[1] = 0; return out; }, 15);
        fresh.setPresence(0);
        check("presence".equals(math.waiting(fresh, rules)) || !math.due(fresh, rules), "just teleported: the draw waits for the presence");
        int after = walk(fresh, rules, (i, out) -> { out[0] = 5000 + i * 90.0; out[1] = 0; return out; }, 20);
        check(after == 4, "then 2 min of presence (" + after + " samples)");
        // Owed draws are not in the record's way.
        check(math.threshold(new EonRecord(), rules) == rules.getFirstTicks(), "a new record: the first draw's delay");
    }

    static void regions(EonRules rules) {
        EonMath math = EonMath.INSTANCE;
        EonRecord r = new EonRecord();
        long now = 1_000_000_000L;
        math.rewarded(r, "minecraft:overworld", 100, 100, "minecraft:forest", now);
        check(r.getFresh() && r.getCounted() == 0, "a fragment won: a fresh interval");
        check(math.blocked(r, "minecraft:overworld", 150, 120, now + 1000, rules), "its region waits");
        check(math.blocked(r, "minecraft:overworld", 500, 100, now + 1000, rules), "450 blocks away (another region): still too near");
        check(!math.blocked(r, "minecraft:overworld", 700, 100, now + 1000, rules), "600 blocks away in another region: allowed");
        check(!math.blocked(r, "minecraft:the_nether", 100, 100, now + 1000, rules), "another dimension: allowed");
        check(!math.blocked(r, "minecraft:overworld", 120, 120, now + rules.getRegionCooldownMs() + 1, rules), "after 6 h the region gives again (sliding)");
        // Many rewards: 8 kept, so the 16 fragments never run out.
        for (int i = 0; i < 20; i++) math.rewarded(r, "minecraft:overworld", i * 1000.0, 0, "minecraft:plains", now + i);
        check(r.getRewards().size() == EonRecord.KEPT, "8 rewards kept (" + r.getRewards().size() + ")");
        // On a 20 000 × 20 000 map: thousands of regions, no chunk generation needed.
        int size = rules.getRegionSize();
        check((20000 / size) * (20000 / size) > 2000, "more than 2 000 regions on a 20 000 map (" + (20000 / size) * (20000 / size) + ")");
        check(math.region(-1, -1, size) != math.region(1, 1, size) && math.region(10, 10, size) == math.region(300, 300, size), "regions split at the origin, and are " + size + " blocks wide");
    }

    static void biomes(EonRules rules) {
        EonMath math = EonMath.INSTANCE;
        EonRecord r = new EonRecord();
        check(Math.abs(math.chance(r, "minecraft:forest", rules) - 0.33) < 1e-9, "a new biome: the whole chance");
        math.rewarded(r, "minecraft:overworld", 0, 0, "minecraft:forest", 1);
        check(Math.abs(math.chance(r, "minecraft:forest", rules) - 0.33 * 0.6) < 1e-9, "a biome of the last fragments: × 0.6");
        for (String b : new String[]{"a:1", "a:2", "a:3", "a:4"}) math.rewarded(r, "minecraft:overworld", 0, 0, b, 2);
        check(Math.abs(math.chance(r, "minecraft:forest", rules) - 0.33) < 1e-9, "forgotten after 4 other biomes: the whole chance again");
    }

    static void machine() {
        EonMachine m = new EonMachine();
        check(m.getPhase() == EonMachine.Phase.SIGNAL, "starts with the signal");
        check(!m.toPursuit(), "no pursuit before the anomaly");
        check(m.toAnomaly() && m.toPursuit(), "signal, anomaly, pursuit");
        check(!m.claim(), "no claim during the pursuit");
        check(m.toCore(), "the core");
        check(m.claim(), "the first click is taken");
        check(!m.claim() && !m.claim(), "repeated clicks are not");
        check(!m.fail(), "no failure once the reward is granted (a disconnection then keeps it)");
        check(m.finish() && m.getOver() && !m.finish(), "finished once");
        EonMachine f = new EonMachine();
        f.toAnomaly();
        check(f.fail() && f.getOver() && !f.fail() && !f.toCore() && !f.claim(), "a failure ends it for good");
        EonMachine s = new EonMachine();
        check(s.toCore() && s.claim(), "a test's shortcut from the signal to the core");
    }

    static void path() {
        EonPath p = new EonPath();
        double[] out = new double[3];
        p.add(0, 1.7, 0);
        check(p.getSettled() == 0 && finite(p.position(0, out)), "one point: still, finite");
        for (int i = 1; i <= 8; i++) p.add(i * 7.0, 1.7 + (i % 2) * 0.5, (i % 3) * 2.0);
        check(p.getSettled() > 40 && p.getSettled() < 60, "seven settled segments of about 7 blocks (" + p.getSettled() + ")");
        p.setMotion(100, 0, 3.6);
        double last = -1, maxStep = 0;
        double[] prev = null;
        boolean finiteAll = true;
        for (int k = 0; k <= 400; k++) {
            double tick = 100 + k * 0.5;
            double s = p.sAt(tick);
            if (s < last - 1e-9) finiteAll = false;
            last = s;
            p.position(tick, out);
            if (!finite(out)) finiteAll = false;
            if (prev != null) maxStep = Math.max(maxStep, Math.sqrt(Math.pow(out[0] - prev[0], 2) + Math.pow(out[1] - prev[1], 2) + Math.pow(out[2] - prev[2], 2)));
            prev = out.clone();
        }
        check(finiteAll, "its motion goes forward, finite");
        check(maxStep < 0.2, "no jump along the way (" + maxStep + " per half tick)");
        check(Math.abs(p.sAt(100 + 20 * 5) - 18.0) < 1e-6, "3.6 blocks a second");
        check(Math.abs(p.sAt(100000) - p.getSettled()) < 1e-9, "never past the settled length");
        p.setMotion(200, p.sAt(200), 0);
        double[] a = p.position(200, new double[3]);
        double[] b = p.position(260, new double[3]);
        check(Math.abs(a[0] - b[0]) < 1e-9 && Math.abs(a[2] - b[2]) < 1e-9 && Math.abs(a[1] - b[1]) < 0.5, "speed 0: it hovers in place");
    }

    static void odds(EonRules rules) {
        EonMath.Odds typical = EonMath.INSTANCE.odds(rules, 0.85, 0.10, 6000, 11L);
        EonMath.Odds good = EonMath.INSTANCE.odds(rules, 0.95, 0.05, 6000, 12L);
        EonMath.Odds weak = EonMath.INSTANCE.odds(rules, 0.70, 0.20, 6000, 13L);
        System.out.printf("odds: typical %.2f h (median %.2f, P10 %.2f, P90 %.2f), good %.2f h, weak %.2f h%n",
            typical.getMean(), typical.getMedian(), typical.getP10(), typical.getP90(), good.getMean(), weak.getMean());
        check(typical.getMean() > 10.0 && typical.getMean() < 12.5, "a typical player: about 11 h like the Python simulation (" + typical.getMean() + ")");
        check(typical.getP10() > 8.0 && typical.getP90() < 15.0, "a typical player: 80 % between 8 and 15 h");
        check(good.getMean() > 8.5 && weak.getMean() < 16.5, "skilled " + good.getMean() + " h, less skilled " + weak.getMean() + " h");
    }

    // ---------------------------------------------------------------- the scenes

    static double wrap(double deg) {
        double x = (deg + 180.0) % 360.0;
        if (x < 0) x += 360.0;
        return x - 180.0;
    }

    static void scene(String name, SceneScript s, Vector3d landing, boolean test, double seconds) {
        String label = name + (test ? " test" : "") + " landing " + landing.x + "," + landing.y + "," + landing.z;
        check(CinematicScenes.INSTANCE.writtenSeconds(name) == seconds && seconds >= 20 && seconds <= 25, label + ": written for 20 to 25 s (" + seconds + ")");
        check(CinematicScenes.INSTANCE.minSeconds(name) == 20.0 && CinematicScenes.INSTANCE.maxSeconds(name) == 25.0 && CinematicScenes.INSTANCE.spawnLeadTicks(name) == 2, label + ": stretched 20 to 25 s, the Pokémon 2 ticks before the end");
        check(Math.abs(s.action(0)) < 1e-6 && Math.abs(s.action(seconds) - seconds) < 1e-3, label + ": the action back on time (" + s.action(seconds) + ")");
        Vector3f forward = new Vector3f();
        for (int mode = 0; mode < 2; mode++) {
            CameraMotion motion = new CameraMotion(mode == 1);
            CamFrame frame = new CamFrame();
            ActorKey actor = new ActorKey();
            Grade grade = new Grade();
            double lowest = 99, minFov = 999, maxFov = 0, maxJump = 0, maxJumpAt = 0, lastA = -1, actorStep = 0, actorStepAt = 0, worstView = 0, worstViewAt = 0;
            Vector3d last = null, lastActor = null;
            int invalid = 0, backwards = 0, envelopes = 0, under = 0;
            String first = null;
            for (int i = 0; i <= (int) (seconds * 200); i++) {
                double t = i * 0.005;
                double a = s.action(t);
                if (!finite(a)) invalid++;
                if (a < lastA - 1e-9) backwards++;
                lastA = a;
                s.getDirector().evaluate(t, motion, frame);
                if (!frame.isFinite()) {
                    invalid++;
                    if (first == null) first = "camera at " + t;
                }
                Vector3d cam = frame.getPos();
                lowest = Math.min(lowest, cam.y);
                minFov = Math.min(minFov, frame.getFov());
                maxFov = Math.max(maxFov, frame.getFov());
                if (last != null) {
                    double jump = last.distance(cam);
                    if (jump > maxJump) {
                        maxJump = jump;
                        maxJumpAt = t;
                    }
                }
                last = new Vector3d(cam);
                s.actor(a, actor);
                if (!actor.isFinite()) {
                    invalid++;
                    if (first == null) first = "actor at " + t;
                }
                boolean shown = actor.getPose() != ActorPose.HIDDEN;
                if (shown) {
                    if (lastActor != null) {
                        double step = lastActor.distance(actor.getPos());
                        if (step > actorStep) {
                            actorStep = step;
                            actorStepAt = t;
                        }
                    }
                    lastActor = new Vector3d(actor.getPos());
                    // Never under the ground of the scene's frame (the landing may be a little lower).
                    if (actor.getPos().y < Math.min(0.0, landing.y) - 0.05) under++;
                } else lastActor = null;
                for (double e : s.envelopes(a)) if (!Double.isFinite(e) || e < -1e-9 || e > 1.0 + 1e-9) envelopes++;
                s.grade(t, grade);
                if (!grade.isFinite()) invalid++;
                // The end: the Pokémon in view, coming down to the player.
                if (shown && t >= seconds - 5.0 && t <= seconds - 1.6 && s.cameraWeight(t) > 0.5) {
                    Vector3d body = new Vector3d(actor.getPos()).add(0, 0.7, 0);
                    frame.forward(forward);
                    Vector3d to = new Vector3d(body).sub(cam).normalize();
                    double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, forward.x * to.x + forward.y * to.y + forward.z * to.z))));
                    double limit = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(frame.getFov() / 2.0)) * 1.6));
                    if (angle - limit > worstView) {
                        worstView = angle - limit;
                        worstViewAt = t;
                    }
                }
            }
            String m = label + (mode == 1 ? " (reduced motion)" : "");
            check(invalid == 0, m + ": no invalid value" + (first == null ? "" : " (" + first + ")"));
            check(backwards == 0, m + ": the action never goes back");
            check(lowest >= 0.5, m + ": the camera stays 0.5 m over the floor (" + lowest + ")");
            check(minFov >= 34 && maxFov <= 78, m + ": field of view " + minFov + " to " + maxFov);
            check(maxJump < 0.35, m + ": no camera jump (" + maxJump + " at " + maxJumpAt + ")");
            check(actorStep < 1.3, m + ": no actor teleport while shown (" + actorStep + " m in 5 ms at " + actorStepAt + ")");
            check(under == 0, m + ": the actor never under the ground (" + under + ")");
            check(envelopes == 0, m + ": envelopes in 0..1 (" + envelopes + " out)");
            check(worstView < 1.0, m + ": the Pokémon in view at the end (" + worstView + " degrees out at " + worstViewAt + ")");
        }
        // The spawn moment (2 ticks before the end): where the real Pokémon appears.
        ActorKey end = new ActorKey();
        s.actor(s.action(seconds - 0.1), end);
        if (test) check(end.getPose() == ActorPose.HIDDEN || end.getScale() < 0.05, label + ": a test ends with nothing left");
        else check(end.getPose() != ActorPose.HIDDEN && end.getPos().distance(landing) < 0.05 && Math.abs(end.getScale() - 1) < 1e-9, label + ": at the landing point at the spawn moment (" + end.getPos().distance(landing) + ")");
        // The cues: in time, sorted, valid, with the cry.
        double previous = -1;
        boolean sorted = true, inTime = true, cry = false;
        for (Cue c : s.getCues()) {
            if (c.getTime() < previous) sorted = false;
            previous = c.getTime();
            if (c.getTime() < 0 || c.getTime() > seconds) inTime = false;
            if (c.getKind() == Cue.Kind.CRY && c.getId().contains(name)) cry = true;
        }
        check(sorted && inTime, label + ": cues sorted and in time");
        check(cry, label + ": its own cry");
        check(s.getDirector().getShots().size() >= 8, label + ": " + s.getDirector().getShots().size() + " shots");
    }

    static void illusions() {
        LatiasScript s = new LatiasScript(new Vector3d(0, 0, 4), false);
        ActorKey k = new ActorKey();
        int maxShown = 0;
        boolean allGone = true;
        for (double a = 5.0; a < 10.0; a += 0.05) {
            int shown = 0;
            for (int i = 0; i < LatiasScript.ILLUSION_COUNT; i++) {
                s.illusion(i, a, k);
                if (k.getPose() != ActorPose.HIDDEN) {
                    shown++;
                    double r = Math.sqrt(k.getPos().x * k.getPos().x + k.getPos().z * k.getPos().z);
                    check(r > 6.5 && r < 10.5 && k.getPos().y > 1.0, "illusion " + i + " circles the player (r " + r + ", y " + k.getPos().y + ")");
                }
            }
            maxShown = Math.max(maxShown, shown);
        }
        for (int i = 0; i < LatiasScript.ILLUSION_COUNT; i++) {
            s.illusion(i, s.vanishAt(i) + 0.01, k);
            if (k.getPose() != ActorPose.HIDDEN) allGone = false;
        }
        check(maxShown == 6, "six illusions at once (" + maxShown + ")");
        check(allGone, "each vanishes at its burst");
        for (int i = 0; i < LatiasScript.ILLUSION_COUNT; i++) {
            s.illusion(i, LatiasScript.WAVE + 0.01, k);
            if (k.getPose() != ActorPose.HIDDEN) allGone = false;
        }
        check(allGone, "the wave dispels the last illusions");
        // Latias is hidden while the illusions circle, then the real one appears.
        s.actor(7.0, k);
        check(k.getPose() == ActorPose.HIDDEN, "the real Latias hidden among the illusions");
        s.actor(LatiasScript.REVEAL + 0.5, k);
        check(k.getPose() == ActorPose.FLY, "the real Latias revealed, flying");
    }

    /** One Pokémon whatever happens (the engine's machine, the scene's length, 2 ticks ahead). */
    static void stateMachine(String scene) {
        int ticks = (int) (CinematicScenes.INSTANCE.writtenSeconds(scene) * 20);
        int lead = CinematicScenes.INSTANCE.spawnLeadTicks(scene);
        CinematicStateMachine full = new CinematicStateMachine(10, ticks, false, lead);
        int spawns = 0;
        long at = -1;
        for (long now = 0; now < 900; now++) for (CinematicStateMachine.Action action : full.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) {
            spawns++;
            at = now;
        }
        check(spawns == 1 && at == 10 + ticks - 2, scene + " played out: one Pokémon, at " + at);
        for (double cutAt : new double[]{0.0, 4.0, 9.0, 15.0, 19.0, 22.85}) {
            CinematicStateMachine m = new CinematicStateMachine(10, ticks, false, lead);
            int n = 0;
            long cut = 10 + (long) (cutAt * 20);
            for (long now = 0; now <= cut; now++) for (CinematicStateMachine.Action action : m.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) n++;
            CinematicStateMachine.Interruption i = m.interrupt();
            if (i != null && i.getSpawnNow()) n++;
            CinematicStateMachine.Interruption again = m.interrupt();
            if (again != null && again.getSpawnNow()) n++;
            for (long now = cut + 1; now < 900; now++) for (CinematicStateMachine.Action action : m.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) n++;
            check(n == 1, scene + " cut at " + cutAt + " s: one spawn moment (" + n + ")");
        }
        CinematicStateMachine test = new CinematicStateMachine(10, ticks, true, lead);
        int n = 0;
        for (long now = 0; now < 900; now++) for (CinematicStateMachine.Action action : test.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) n++;
        CinematicStateMachine.Interruption i = test.interrupt();
        if (i != null && i.getSpawnNow()) n++;
        check(n == 0, scene + " test: no Pokémon, even cut short");
    }

    static void music() {
        LatiasScript latias = new LatiasScript(new Vector3d(0, 0, 4), false);
        LatiosScript latios = new LatiosScript(new Vector3d(0, 0, 4), false);
        for (double stretch : new double[]{1.0, 20.0 / 23.0, 25.0 / 23.0}) {
            for (double offset : new double[]{0.0, 11.3}) {
                SynthRenderer[] all = {
                    EonMusic.INSTANCE.latiasMusic(latias, stretch, offset), EonMusic.INSTANCE.latiasAmbience(latias, stretch, offset),
                    EonMusic.INSTANCE.latiosMusic(latios, stretch, offset), EonMusic.INSTANCE.latiosAmbience(latios, stretch, offset),
                };
                String[] names = {"latias music", "latias ambience", "latios music", "latios ambience"};
                for (int k = 0; k < all.length; k++) {
                    long started = System.nanoTime();
                    float peak = all[k].peak();
                    double ms = (System.nanoTime() - started) / 1e6;
                    String what = String.format("%s x%.2f from %.1f s", names[k], stretch, offset);
                    check(Float.isFinite(peak) && peak <= 1.0f && peak > 0.01f, what + ": peak " + peak);
                    check(ms < 10000, what + ": made in " + ms + " ms");
                    if (stretch == 1.0 && offset == 0.0) System.out.printf("%s: peak %.2f (%.0f ms)%n", what, peak, ms);
                }
            }
        }
        for (int k = 0; k < 2; k++) {
            long started = System.nanoTime();
            SynthBuffer buffer = new SynthBuffer(k == 0 ? EonMusic.INSTANCE.latiasMusic(latias, 1.0, 0.0) : EonMusic.INSTANCE.latiosMusic(latios, 1.0, 0.0)).start("bench");
            float[] l = new float[4 * 44100];
            float[] r = new float[4 * 44100];
            int first = buffer.read(0, l.length, l, r);
            double ms = (System.nanoTime() - started) / 1e6;
            check(first == l.length && ms < 500, (k == 0 ? "latias" : "latios") + ": first four seconds ready in " + ms + " ms");
            buffer.cancel();
            System.out.printf("%s made ahead: first 4 s in %.0f ms%n", k == 0 ? "latias" : "latios", ms);
        }
    }
}
