import generations.gg.generations.core.generationscore.common.client.cinematic.CinematicClock;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.ArticunoMusic;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthBuffer;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CamFrame;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMotion;
import generations.gg.generations.core.generationscore.common.client.cinematic.fx.FlashLimiter;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorKey;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorPose;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ArticunoScript;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Cue;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Grade;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.MoltresScript;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.MewtwoScript;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.MewtwoMusic;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.MewScript;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.MewMusic;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.SceneScript;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ZapdosScript;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.Shot;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.MoltresMusic;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.ZapdosMusic;
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicStateMachine;
import org.joml.Vector3d;

import java.util.List;

/**
 * Off-game checks of the legendary cinematics (CINEMATIQUES-PLAN.md § 6.1, lot 6 early): the
 * Articuno timeline has no invalid value, its camera stays above a flat ground, the server's
 * state machine handles interruptions, double calls and the end; the flash limiter, the clock
 * and the synthesizer behave.
 *
 * Build the mod first (common/build/classes/kotlin/main), then:
 *   javac -cp <classes>;<kotlin-stdlib>;<joml> -d <out> tools/CinematicBench.java
 *   java -cp <out>;<classes>;<kotlin-stdlib>;<joml> CinematicBench
 */
public class CinematicBench {
    private static int passed = 0;
    private static int failed = 0;

    private static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    private static boolean finite(double... values) {
        for (double v : values) if (!Double.isFinite(v)) return false;
        return true;
    }

    public static void main(String[] args) {
        timeline(new Vector3d(0, 0, 4), true, "flat, test");
        timeline(new Vector3d(0, 0, 4), false, "flat, shrine");
        timeline(new Vector3d(2.5, 0, 5), true, "flat, landing aside");
        timeline(new Vector3d(-3, 1, 3), true, "landing higher");
        for (String id : new String[]{"articuno", "zapdos", "moltres"}) {
            generic(id, new Vector3d(0, 0, 4), true, id + ", flat, test");
            generic(id, new Vector3d(0, 0, 4), false, id + ", flat, shrine");
            generic(id, new Vector3d(2.5, 0, 5), true, id + ", landing aside");
            generic(id, new Vector3d(-3, 1, 3), true, id + ", landing higher");
        }
        mewtwo(new Vector3d(0, 0, 3.5), true, 1.05, "mewtwo, flat, test");
        mewtwo(new Vector3d(0, 0, 3.5), false, 1.05, "mewtwo, flat, machine");
        mewtwo(new Vector3d(1.8, 0, 4.2), false, 0.9, "mewtwo, landing aside");
        mewtwo(new Vector3d(-0.5, 1, 4.0), true, 1.2, "mewtwo, landing higher");
        mewtwoSynth();
        mew(new Vector3d(0, 0, 4), true, "mew, flat, test");
        mew(new Vector3d(0, 0, 4), false, "mew, flat, game won");
        mew(new Vector3d(2.0, 0, 5), false, "mew, landing aside");
        mew(new Vector3d(-1.5, 1, 3.5), true, "mew, landing higher");
        mewSynth();
        stateMachine();
        flashes();
        clock();
        synth();
        sceneSynth("zapdos");
        sceneSynth("moltres");
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void timeline(Vector3d landing, boolean test, String label) {
        ArticunoScript script = new ArticunoScript(landing, test);
        boolean flat = landing.y == 0.0;
        double explosion = script.getExplosionReal();
        check(explosion > ArticunoScript.EXPLOSION && explosion < ArticunoScript.EXPLOSION + 0.7, label + ": burst at scene time " + explosion);
        check(Math.abs(script.action(0.0)) < 1e-6, label + ": action(0) = " + script.action(0.0));
        check(Math.abs(script.action(20.0) - 20.0) < 1e-3, label + ": action(20) = " + script.action(20.0));
        check(Math.abs(script.getSlowMotion().realOf(script.action(13.9)) - 13.9) < 1e-6, label + ": realOf inverts action");
        double minSpeed = 9, maxSpeed = 0;
        for (double t = 0; t <= 20; t += 0.01) {
            minSpeed = Math.min(minSpeed, script.getSlowMotion().speed(t));
            maxSpeed = Math.max(maxSpeed, script.getSlowMotion().speed(t));
        }
        check(minSpeed > 0.3 && maxSpeed < 2.0, label + ": slow motion speed from " + minSpeed + " to " + maxSpeed);
        System.out.printf("%s: burst at %.3f s (scene), slow motion %.2f to %.2f%n", label, explosion, minSpeed, maxSpeed);

        for (int mode = 0; mode < 2; mode++) {
            CameraMotion motion = new CameraMotion(mode == 1);
            CamFrame frame = new CamFrame();
            ActorKey actor = new ActorKey();
            Grade grade = new Grade();
            double lowest = 99, maxFov = 0, minFov = 999, maxJump = 0, maxJumpAt = 0, lastA = -1;
            Vector3d last = null;
            int invalid = 0, below = 0, notMonotonic = 0, envelopes = 0;
            String firstInvalid = null;
            for (int i = 0; i <= 4000; i++) {
                double t = i * 0.005;
                double a = script.action(t);
                if (!finite(a)) invalid++;
                if (a < lastA - 1e-9) notMonotonic++;
                lastA = a;
                script.getDirector().evaluate(t, motion, frame);
                if (!frame.isFinite()) {
                    invalid++;
                    if (firstInvalid == null) firstInvalid = "camera at " + t;
                }
                // Above a flat ground (the shrine's): 0.4 m at least.
                double ground = flat ? 0.0 : Math.min(0.0, landing.y);
                lowest = Math.min(lowest, frame.getPos().y - ground);
                if (frame.getPos().y - ground < 0.4) below++;
                minFov = Math.min(minFov, frame.getFov());
                maxFov = Math.max(maxFov, frame.getFov());
                if (last != null) {
                    double jump = last.distance(frame.getPos());
                    boolean cut = Math.abs(t - ArticunoScript.REVEAL) < 0.006 || Math.abs(t - ArticunoScript.CRY_SHOT) < 0.006;
                    if (!cut && jump > maxJump) {
                        maxJump = jump;
                        maxJumpAt = t;
                    }
                }
                last = new Vector3d(frame.getPos());
                script.actor(a, actor);
                if (!actor.isFinite() || actor.getScale() < 0 || actor.getScale() > 1.0001) {
                    invalid++;
                    if (firstInvalid == null) firstInvalid = "actor at " + t;
                }
                script.grade(t, grade);
                if (!grade.isFinite() || grade.getExposure() <= 0.3 || grade.getExposure() > 1.5) {
                    invalid++;
                    if (firstInvalid == null) firstInvalid = "grade at " + t;
                }
                double[] values = {
                    script.cameraWeight(t), script.letterbox(t), script.title(t), script.presence(t), script.flash(a),
                    script.columnLight(a) / 1.2, script.vortex(a), script.cloudOpacity(a), script.mistOpacity(a), script.frostOpacity(a),
                    script.coldWaveOpacity(a), script.backlight(a), script.trails(a), script.snowfall(a), script.shardsPresence(a),
                    script.frostDecalOpacity(a), script.crystalRise(5, a),
                };
                for (double v : values) if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                double[] sizes = {script.columnHeight(a), script.mistRadius(a), script.frostRadius(a), script.coldWaveRadius(a), script.cloudRadius(a), script.shake(a)};
                for (double v : sizes) if (!finite(v) || v < -1e-9) envelopes++;
            }
            String m = label + (mode == 1 ? ", reduced motion" : "");
            check(invalid == 0, m + ": " + invalid + " invalid values (first: " + firstInvalid + ")");
            check(notMonotonic == 0, m + ": action time goes back " + notMonotonic + " times");
            check(below == 0, m + ": camera below 0.4 m above the ground " + below + " times (lowest " + lowest + ")");
            check(minFov >= 20 && maxFov <= 100, m + ": field of view from " + minFov + " to " + maxFov);
            check(envelopes == 0, m + ": " + envelopes + " envelope values out of [0, 1]");
            // 0.005 s steps: 0.35 m is 70 m/s (blends included, cuts excepted).
            check(maxJump < 0.35, m + ": camera jump " + maxJump + " at " + maxJumpAt);
            System.out.printf("%s: lowest camera %.2f m, fov %.1f to %.1f, largest step %.3f m at %.3f s%n", m, lowest, minFov, maxFov, maxJump, maxJumpAt);
        }

        // The actor: hidden before the reveal, standing at the landing point after 18 s, gone at the end in test mode.
        ActorKey key = new ActorKey();
        script.actor(5.0, key);
        check(key.getPose() == ActorPose.HIDDEN, label + ": hidden before the reveal");
        script.actor(10.0, key);
        check(key.getPose() == ActorPose.FLY, label + ": flying at its silhouette");
        script.actor(14.5, key);
        check(key.getPose() == ActorPose.HOVER, label + ": hovering after the burst");
        script.actor(18.6, key);
        check(key.getPose() == ActorPose.STAND && key.getPos().distance(landing) < 1e-6, label + ": standing on the landing point");
        script.actor(19.9, key);
        check(test ? key.getPose() == ActorPose.HIDDEN : key.getPose() == ActorPose.STAND, label + ": at the end (" + key.getPose() + ")");
        // Continuity of the actor's path (no teleport).
        Vector3d previous = null;
        double worst = 0;
        Vector3d p = new Vector3d();
        for (double a = ArticunoScript.REVEAL; a <= 19.0; a += 0.005) {
            script.birdPos(a, p);
            if (previous != null) worst = Math.max(worst, previous.distance(p));
            previous = new Vector3d(p);
        }
        check(worst < 0.2, label + ": actor step " + worst);

        List<Cue> cues = script.getCues();
        double lastTime = -1;
        boolean sorted = true, inside = true, named = true;
        for (Cue cue : cues) {
            if (cue.getTime() < lastTime) sorted = false;
            lastTime = cue.getTime();
            if (cue.getTime() < 0 || cue.getTime() > 20) inside = false;
            if (cue.getId().isEmpty() || !Float.isFinite(cue.getVolume()) || cue.getVolume() <= 0 || cue.getPitch() <= 0 || cue.getPitch() > 2.0f) named = false;
        }
        check(sorted && inside && named, label + ": " + cues.size() + " cues sorted, within the scene, valid");
        check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.CRY && c.getId().equals("cobblemon:pokemon.articuno.cry")), label + ": the cry");
    }

    private static SceneScript make(String id, Vector3d landing, boolean test) {
        switch (id) {
            case "zapdos": return new ZapdosScript(landing, test);
            case "moltres": return new MoltresScript(landing, test);
            default: return new ArticunoScript(landing, test);
        }
    }

    /** The same checks for every scene, through SceneScript only. */
    private static void generic(String id, Vector3d landing, boolean test, String label) {
        SceneScript script = make(id, landing, test);
        boolean flat = landing.y == 0.0;
        check(Math.abs(script.action(0.0)) < 1e-6 && Math.abs(script.action(20.0) - 20.0) < 1e-3, label + ": action time from 0 to 20 (" + script.action(20.0) + ")");
        java.util.ArrayList<Double> cuts = new java.util.ArrayList<>();
        for (Shot shot : script.getDirector().getShots()) if (shot.getStart() > 0 && shot.getBlendIn() <= 0) cuts.add(shot.getStart());
        for (int mode = 0; mode < 2; mode++) {
            CameraMotion motion = new CameraMotion(mode == 1);
            CamFrame frame = new CamFrame();
            ActorKey actor = new ActorKey();
            Grade grade = new Grade();
            double lowest = 99, minFov = 999, maxFov = 0, maxJump = 0, maxJumpAt = 0, lastA = -1, actorStep = 0, actorStepAt = 0;
            Vector3d last = null, lastActor = null;
            int invalid = 0, below = 0, backwards = 0, envelopes = 0;
            String first = null;
            for (int i = 0; i <= 4000; i++) {
                double t = i * 0.005;
                double a = script.action(t);
                if (!finite(a)) invalid++;
                if (a < lastA - 1e-9) backwards++;
                lastA = a;
                script.getDirector().evaluate(t, motion, frame);
                if (!frame.isFinite()) {
                    invalid++;
                    if (first == null) first = "camera at " + t;
                }
                double ground = flat ? 0.0 : Math.min(0.0, landing.y);
                lowest = Math.min(lowest, frame.getPos().y - ground);
                if (frame.getPos().y - ground < 0.4) below++;
                minFov = Math.min(minFov, frame.getFov());
                maxFov = Math.max(maxFov, frame.getFov());
                if (last != null) {
                    boolean cut = false;
                    for (double c : cuts) if (Math.abs(t - c) < 0.006) cut = true;
                    double jump = last.distance(frame.getPos());
                    if (!cut && jump > maxJump) {
                        maxJump = jump;
                        maxJumpAt = t;
                    }
                }
                last = new Vector3d(frame.getPos());
                script.actor(a, actor);
                if (!actor.isFinite() || actor.getScale() < 0 || actor.getScale() > 1.0001 || Math.abs(actor.getRoll()) > 60 || Math.abs(actor.getPitch()) > 60) {
                    invalid++;
                    if (first == null) first = "actor at " + t + " (" + actor.getPitch() + ", " + actor.getRoll() + ")";
                }
                if (a >= 9.0 && a <= 19.0) {
                    if (lastActor != null) {
                        double step = lastActor.distance(actor.getPos());
                        if (step > actorStep) {
                            actorStep = step;
                            actorStepAt = t;
                        }
                    }
                    lastActor = new Vector3d(actor.getPos());
                }
                script.grade(t, grade);
                if (!grade.isFinite() || grade.getExposure() <= 0.3 || grade.getExposure() > 1.5) {
                    invalid++;
                    if (first == null) first = "grade at " + t;
                }
                double[] common = {script.cameraWeight(t), script.letterbox(t), script.title(t), script.presence(t), script.flash(a)};
                for (double v : common) if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                for (double v : script.envelopes(a)) if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                if (!finite(script.shake(a)) || script.shake(a) < 0) envelopes++;
            }
            String m = label + (mode == 1 ? ", reduced motion" : "");
            check(invalid == 0, m + ": " + invalid + " invalid values (first: " + first + ")");
            check(backwards == 0, m + ": action time goes back " + backwards + " times");
            check(below == 0, m + ": camera below 0.4 m above the ground " + below + " times (lowest " + lowest + ")");
            check(minFov >= 20 && maxFov <= 100, m + ": field of view from " + minFov + " to " + maxFov);
            check(envelopes == 0, m + ": " + envelopes + " envelope values out of range");
            check(maxJump < 0.35, m + ": camera jump " + maxJump + " at " + maxJumpAt);
            check(actorStep < 0.35, m + ": actor step " + actorStep + " at " + actorStepAt);
            System.out.printf("%s: lowest camera %.2f m, fov %.1f to %.1f, largest camera step %.3f m at %.3f s, actor step %.3f m at %.3f s%n", m, lowest, minFov, maxFov, maxJump, maxJumpAt, actorStep, actorStepAt);
        }
        ActorKey key = new ActorKey();
        script.actor(5.0, key);
        check(key.getPose() == ActorPose.HIDDEN, label + ": hidden at 5 s");
        script.actor(10.5, key);
        check(key.getPose() != ActorPose.HIDDEN, label + ": seen at 10.5 s");
        script.actor(18.8, key);
        check(key.getPose() == ActorPose.STAND && key.getPos().distance(landing) < 1e-6, label + ": standing on the landing point at 18.8 s");
        script.actor(19.9, key);
        check(test ? key.getPose() == ActorPose.HIDDEN : key.getPose() == ActorPose.STAND, label + ": at the end (" + key.getPose() + ")");
        List<Cue> cues = script.getCues();
        double lastTime = -1;
        boolean ok = true;
        for (Cue cue : cues) {
            if (cue.getTime() < lastTime || cue.getTime() < 0 || cue.getTime() > 20) ok = false;
            lastTime = cue.getTime();
            if (cue.getId().isEmpty() || cue.getVolume() <= 0 || cue.getPitch() <= 0 || cue.getPitch() > 2.0f) ok = false;
        }
        check(ok, label + ": " + cues.size() + " cues sorted, within the scene, valid");
        check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.CRY && c.getId().equals("cobblemon:pokemon." + id + ".cry")), label + ": its cry");
        if (id.equals("zapdos")) check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.ANIMATE && c.getId().equals("special")), label + ": wings opened with special");
    }

    private static void sceneSynth(String id) {
        for (double stretch : new double[]{1.0, 0.75, 1.25}) {
            for (double offset : new double[]{0.0, 7.3}) {
                SynthRenderer music;
                SynthRenderer ambience;
                if (id.equals("zapdos")) {
                    ZapdosScript script = new ZapdosScript(new Vector3d(0, 0, 4), true);
                    music = ZapdosMusic.INSTANCE.music(script, stretch, offset);
                    ambience = ZapdosMusic.INSTANCE.ambience(script, stretch, offset);
                } else {
                    MoltresScript script = new MoltresScript(new Vector3d(0, 0, 4), true);
                    music = MoltresMusic.INSTANCE.music(script, stretch, offset);
                    ambience = MoltresMusic.INSTANCE.ambience(script, stretch, offset);
                }
                long started = System.nanoTime();
                float peak = music.peak();
                double ms = (System.nanoTime() - started) / 1e6;
                float ambiencePeak = ambience.peak();
                String what = String.format("%s synth x%.2f from %.1f s", id, stretch, offset);
                check(Float.isFinite(peak) && peak <= 1.0f && peak > 0.05f, what + ": music peak " + peak);
                check(Float.isFinite(ambiencePeak) && ambiencePeak <= 1.0f && ambiencePeak > 0.01f, what + ": ambience peak " + ambiencePeak);
                check(ms < 8000, what + ": music made in " + ms + " ms");
                System.out.printf("%s: music peak %.2f (%.0f ms), ambience peak %.2f%n", what, peak, ms, ambiencePeak);
            }
        }
        // Made ahead: the first four seconds within the packet's half second.
        SynthRenderer renderer = id.equals("zapdos")
            ? ZapdosMusic.INSTANCE.music(new ZapdosScript(new Vector3d(0, 0, 4), true), 1.0, 0.0)
            : MoltresMusic.INSTANCE.music(new MoltresScript(new Vector3d(0, 0, 4), true), 1.0, 0.0);
        long started = System.nanoTime();
        SynthBuffer buffer = new SynthBuffer(renderer).start("bench");
        float[] l = new float[4 * 44100];
        float[] r = new float[4 * 44100];
        int first = buffer.read(0, l.length, l, r);
        double ms = (System.nanoTime() - started) / 1e6;
        check(first == l.length && ms < 500, id + " made ahead: first four seconds ready in " + ms + " ms");
        buffer.cancel();
        System.out.printf("%s made ahead: first 4 s in %.0f ms%n", id, ms);
    }

    /** The awakening of Mewtwo: 24 s, its own checks (the tank, the machine's box, the landing). */
    private static void mewtwo(Vector3d landing, boolean test, double tankFeet, String label) {
        MewtwoScript script = new MewtwoScript(landing, test, tankFeet);
        double end = MewtwoScript.SECONDS;
        check(Math.abs(script.action(0.0)) < 1e-6 && Math.abs(script.action(end) - end) < 1e-3, label + ": action time from 0 to 24 (" + script.action(end) + ")");
        check(Math.abs(script.action(13.8) - 13.8) < 0.005, label + ": slow motion given back by 13.8 s (" + script.action(13.8) + ")");
        java.util.ArrayList<Double> cuts = new java.util.ArrayList<>();
        for (Shot shot : script.getDirector().getShots()) if (shot.getStart() > 0 && shot.getBlendIn() <= 0) cuts.add(shot.getStart());
        double minSpeed = 9, maxSpeed = 0;
        for (int mode = 0; mode < 2; mode++) {
            CameraMotion motion = new CameraMotion(mode == 1);
            CamFrame frame = new CamFrame();
            ActorKey actor = new ActorKey();
            Grade grade = new Grade();
            double lowest = 99, minFov = 999, maxFov = 0, maxJump = 0, maxJumpAt = 0, lastA = -1, actorStep = 0, actorStepAt = 0;
            Vector3d last = null, lastActor = null;
            int invalid = 0, below = 0, backwards = 0, envelopes = 0, inside = 0;
            String first = null;
            for (int i = 0; i <= 4800; i++) {
                double t = i * 0.005;
                double a = script.action(t);
                if (i > 0) {
                    double speed = (a - script.action(t - 0.005)) / 0.005;
                    minSpeed = Math.min(minSpeed, speed);
                    maxSpeed = Math.max(maxSpeed, speed);
                }
                if (!finite(a)) invalid++;
                if (a < lastA - 1e-9) backwards++;
                lastA = a;
                script.getDirector().evaluate(t, motion, frame);
                if (!frame.isFinite()) {
                    invalid++;
                    if (first == null) first = "camera at " + t;
                }
                Vector3d pos = frame.getPos();
                double ground = landing.y > 0 ? 0.0 : Math.min(0.0, landing.y);
                lowest = Math.min(lowest, pos.y - ground);
                if (pos.y - ground < 0.4) below++;
                // Never inside the machine (x and z within 1.5 of the tank, up to its cables).
                if (Math.abs(pos.x) < 1.55 && Math.abs(pos.z) < 1.55 && pos.y < 4.4) {
                    inside++;
                    if (first == null) first = "camera inside the machine at " + t + " (" + pos + ")";
                }
                minFov = Math.min(minFov, frame.getFov());
                maxFov = Math.max(maxFov, frame.getFov());
                if (last != null) {
                    boolean cut = false;
                    for (double c : cuts) if (Math.abs(t - c) < 0.006) cut = true;
                    double jump = last.distance(pos);
                    if (!cut && jump > maxJump) {
                        maxJump = jump;
                        maxJumpAt = t;
                    }
                }
                last = new Vector3d(pos);
                script.actor(a, actor);
                if (!actor.isFinite() || actor.getScale() < 0 || actor.getScale() > 1.0001 || Math.abs(actor.getRoll()) > 60 || Math.abs(actor.getPitch()) > 60) {
                    invalid++;
                    if (first == null) first = "actor at " + t;
                }
                if (lastActor != null) {
                    double step = lastActor.distance(actor.getPos());
                    if (step > actorStep) {
                        actorStep = step;
                        actorStepAt = t;
                    }
                }
                lastActor = new Vector3d(actor.getPos());
                script.grade(t, grade);
                if (!grade.isFinite() || grade.getExposure() <= 0.3 || grade.getExposure() > 1.5) {
                    invalid++;
                    if (first == null) first = "grade at " + t;
                }
                double[] common = {script.cameraWeight(t), script.letterbox(t), script.title(t), script.presence(t), script.flash(a)};
                for (double v : common) if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                for (double v : script.envelopes(a)) if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                if (!finite(script.shake(a)) || script.shake(a) < 0) envelopes++;
            }
            String m = label + (mode == 1 ? ", reduced motion" : "");
            check(invalid == 0, m + ": " + invalid + " invalid values (first: " + first + ")");
            check(backwards == 0, m + ": action time goes back " + backwards + " times");
            check(below == 0, m + ": camera below 0.4 m above the ground " + below + " times (lowest " + lowest + ")");
            check(inside == 0, m + ": camera inside the machine " + inside + " times (" + first + ")");
            check(minFov >= 20 && maxFov <= 100, m + ": field of view from " + minFov + " to " + maxFov);
            check(envelopes == 0, m + ": " + envelopes + " envelope values out of range");
            check(maxJump < 0.35, m + ": camera jump " + maxJump + " at " + maxJumpAt);
            check(actorStep < 0.12, m + ": actor step " + actorStep + " at " + actorStepAt);
            System.out.printf("%s: lowest camera %.2f m, fov %.1f to %.1f, largest camera step %.3f m at %.3f s, actor step %.3f m at %.3f s%n", m, lowest, minFov, maxFov, maxJump, maxJumpAt, actorStep, actorStepAt);
        }
        check(minSpeed >= 0.35 && maxSpeed <= 1.7, label + ": slow motion from " + minSpeed + " to " + maxSpeed);
        ActorKey key = new ActorKey();
        script.actor(0.0, key);
        check(key.getPose() == ActorPose.HOVER && Math.abs(key.getScale() - 0.9) < 1e-9 && Math.abs(key.getPos().y - tankFeet) < 0.06 && Math.abs(key.getPos().x) < 0.05, label + ": starts where the tank's renderer draws it (" + key.getPos() + ", " + key.getScale() + ")");
        script.actor(MewtwoScript.BREAK - 0.01, key);
        check(Math.hypot(key.getPos().x, key.getPos().z) < 0.1, label + ": in the tank until the breach");
        script.actor(MewtwoScript.CRY, key);
        check(key.getPos().distance(script.getHoverPoint()) < 0.2 && key.getPose() == ActorPose.HOVER, label + ": hovering over the console for its cry");
        script.actor(MewtwoScript.LANDING + 0.1, key);
        check(key.getPose() == ActorPose.STAND && key.getPos().distance(landing) < 1e-6, label + ": standing on the landing point after " + MewtwoScript.LANDING + " s");
        script.actor(23.95, key);
        check(test ? key.getPose() == ActorPose.HIDDEN : key.getPose() == ActorPose.STAND && key.getPos().distance(landing) < 1e-6, label + ": at the end (" + key.getPose() + ")");
        check(script.cameraWeight(0) == 0 && script.cameraWeight(23.99) < 0.01 && script.cameraWeight(12) > 0.99, label + ": the camera taken and given back");
        check(script.cracks(MewtwoScript.CRACK1 - 0.5) < 1e-9 && script.cracks(MewtwoScript.BREAK - 0.1) > 0.99 && script.cracks(MewtwoScript.BREAK) == 0.0, label + ": the cracks spread, gone with the glass");
        List<Cue> cues = script.getCues();
        double lastTime = -1;
        boolean ok = true;
        for (Cue cue : cues) {
            if (cue.getTime() < lastTime || cue.getTime() < 0 || cue.getTime() > end) ok = false;
            lastTime = cue.getTime();
            if (cue.getId().isEmpty() || cue.getVolume() <= 0 || cue.getPitch() <= 0 || cue.getPitch() > 2.0f) ok = false;
        }
        check(ok, label + ": " + cues.size() + " cues sorted, within the scene, valid");
        check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.CRY && c.getId().equals("cobblemon:pokemon.mewtwo.cry") && Math.abs(c.getTime() - MewtwoScript.CRY) < 1e-9), label + ": its cry at 16.5 s");
        check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.EVENT && c.getId().equals("break") && Math.abs(c.getTime() - MewtwoScript.BREAK) < 1e-9), label + ": the breach at 11 s");
        check(cues.stream().filter(c -> c.getKind() == Cue.Kind.EVENT && c.getId().startsWith("crack:")).count() == 3, label + ": three steps of cracks");
        // The server side: Mewtwo's entity takes the actor's place right at the end, once.
        CinematicStateMachine server = new CinematicStateMachine(10, 480, test, 2);
        int spawns = 0;
        long spawnAt = -1;
        for (long now = 0; now < 600; now++) for (CinematicStateMachine.Action action : server.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) {
            spawns++;
            spawnAt = now;
        }
        check(test ? spawns == 0 : spawns == 1 && spawnAt == 488, label + ": the server's spawn (" + spawns + " at " + spawnAt + ")");
    }

    /** Mew's scene (MEW-PLAN.md section 10): the camera, Mew, its illusions, the envelopes and the cues. */
    private static void mew(Vector3d landing, boolean test, String label) {
        MewScript script = new MewScript(landing, test);
        double end = MewScript.SECONDS;
        java.util.ArrayList<Double> cuts = new java.util.ArrayList<>();
        for (Shot shot : script.getDirector().getShots()) if (shot.getStart() > 0 && shot.getBlendIn() <= 0) cuts.add(shot.getStart());
        for (int mode = 0; mode < 2; mode++) {
            CameraMotion motion = new CameraMotion(mode == 1);
            CamFrame frame = new CamFrame();
            ActorKey actor = new ActorKey();
            ActorKey extra = new ActorKey();
            Grade grade = new Grade();
            double lowest = 99, minFov = 999, maxFov = 0, maxJump = 0, maxJumpAt = 0, actorStep = 0, actorStepAt = 0, extraStep = 0, extraStepAt = 0, minDistance = 99;
            Vector3d last = null, lastActor = null;
            Vector3d[] lastExtra = new Vector3d[MewScript.ILLUSION_COUNT];
            int invalid = 0, below = 0, envelopes = 0, shown = 0;
            String first = null;
            for (int i = 0; i <= 4800; i++) {
                double t = i * 0.005;
                double a = script.action(t);
                script.getDirector().evaluate(t, motion, frame);
                if (!frame.isFinite()) {
                    invalid++;
                    if (first == null) first = "camera at " + t;
                }
                Vector3d pos = frame.getPos();
                double ground = Math.min(0.0, landing.y);
                lowest = Math.min(lowest, pos.y - ground);
                if (pos.y - ground < 0.4) below++;
                minFov = Math.min(minFov, frame.getFov());
                maxFov = Math.max(maxFov, frame.getFov());
                if (last != null) {
                    boolean cut = false;
                    for (double c : cuts) if (Math.abs(t - c) < 0.006) cut = true;
                    double jump = last.distance(pos);
                    if (!cut && jump > maxJump) {
                        maxJump = jump;
                        maxJumpAt = t;
                    }
                }
                last = new Vector3d(pos);
                script.actor(a, actor);
                if (!actor.isFinite() || actor.getScale() < 0 || actor.getScale() > 1.0001 || Math.abs(actor.getRoll()) > 60 || Math.abs(actor.getPitch()) > 60) {
                    invalid++;
                    if (first == null) first = "actor at " + t;
                }
                // Mew never too close to the camera while the scene holds it.
                if (script.cameraWeight(t) > 0.5 && actor.getPose() != ActorPose.HIDDEN) minDistance = Math.min(minDistance, pos.distance(actor.getPos().x, actor.getPos().y + MewScript.HEAD, actor.getPos().z));
                if (lastActor != null) {
                    double step = lastActor.distance(actor.getPos());
                    if (step > actorStep) {
                        actorStep = step;
                        actorStepAt = t;
                    }
                }
                lastActor = new Vector3d(actor.getPos());
                for (int k = 0; k < MewScript.ILLUSION_COUNT; k++) {
                    script.illusion(k, a, extra);
                    if (extra.getPose() == ActorPose.HIDDEN) {
                        lastExtra[k] = null;
                        continue;
                    }
                    shown++;
                    if (!extra.isFinite() || extra.getScale() < 0 || extra.getScale() > 1.0001) invalid++;
                    if (lastExtra[k] != null) {
                        double step = lastExtra[k].distance(extra.getPos());
                        if (step > extraStep) {
                            extraStep = step;
                            extraStepAt = t;
                        }
                    }
                    lastExtra[k] = new Vector3d(extra.getPos());
                }
                script.grade(t, grade);
                if (!grade.isFinite() || grade.getExposure() <= 0.3 || grade.getExposure() > 1.5) {
                    invalid++;
                    if (first == null) first = "grade at " + t;
                }
                double[] common = {script.cameraWeight(t), script.letterbox(t), script.title(t), script.presence(t), script.flash(a)};
                for (double v : common) if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                for (double v : script.envelopes(a)) if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                if (!finite(script.shake(a)) || script.shake(a) < 0) envelopes++;
            }
            String m = label + (mode == 1 ? ", reduced motion" : "");
            check(invalid == 0, m + ": " + invalid + " invalid values (first: " + first + ")");
            check(below == 0, m + ": camera below 0.4 m above the ground " + below + " times (lowest " + lowest + ")");
            check(minFov >= 20 && maxFov <= 100, m + ": field of view from " + minFov + " to " + maxFov);
            check(envelopes == 0, m + ": " + envelopes + " envelope values out of range");
            check(maxJump < 0.35, m + ": camera jump " + maxJump + " at " + maxJumpAt);
            check(actorStep < 0.12, m + ": Mew step " + actorStep + " at " + actorStepAt);
            check(extraStep < 0.12, m + ": illusion step " + extraStep + " at " + extraStepAt);
            check(shown > 0, m + ": the illusions are seen");
            check(minDistance > 0.7, m + ": Mew at least " + minDistance + " m from the camera");
            System.out.printf("%s: lowest camera %.2f m, fov %.1f to %.1f, largest camera step %.3f m at %.3f s, Mew step %.3f m at %.3f s, illusion step %.3f m, closest %.2f m%n", m, lowest, minFov, maxFov, maxJump, maxJumpAt, actorStep, actorStepAt, extraStep, minDistance);
        }
        ActorKey key = new ActorKey();
        script.actor(0.0, key);
        check(key.getPose() == ActorPose.HOVER && key.getPos().distance(script.getHover()) < 0.1, label + ": waits in front of the player at first");
        script.actor(MewScript.CRY, key);
        check(key.getPos().distance(script.getCentre()) < 0.2, label + ": at the heart of the whirlwind for its cry");
        ActorKey ill = new ActorKey();
        script.illusion(0, MewScript.FUSION - 0.02, ill);
        boolean merged = ill.getPose() == ActorPose.HIDDEN || ill.getPos().distance(script.getCentre()) < 0.6;
        script.illusion(0, MewScript.FUSION + 0.01, ill);
        check(merged && ill.getPose() == ActorPose.HIDDEN, label + ": the illusions merge into Mew at the fusion");
        script.actor(MewScript.LANDING + 0.1, key);
        check(key.getPos().distance(landing) < 1e-6, label + ": on the landing point after " + MewScript.LANDING + " s");
        script.actor(23.95, key);
        check(test ? key.getPose() == ActorPose.HIDDEN : key.getPose() == ActorPose.HOVER && key.getPos().distance(landing) < 1e-6, label + ": at the end (" + key.getPose() + ")");
        check(script.cameraWeight(0) == 0 && script.cameraWeight(23.99) < 0.01 && script.cameraWeight(12) > 0.99, label + ": the camera taken and given back");
        List<Cue> cues = script.getCues();
        double lastTime = -1;
        boolean ok = true;
        for (Cue cue : cues) {
            if (cue.getTime() < lastTime || cue.getTime() < 0 || cue.getTime() > end) ok = false;
            lastTime = cue.getTime();
            if (cue.getId().isEmpty() || cue.getVolume() <= 0 || cue.getPitch() <= 0 || cue.getPitch() > 2.0f) ok = false;
        }
        check(ok, label + ": " + cues.size() + " cues sorted, within the scene, valid");
        check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.CRY && c.getId().equals("cobblemon:pokemon.mew.cry") && Math.abs(c.getTime() - MewScript.CRY) < 1e-9), label + ": its cry at 18 s");
        check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.EVENT && c.getId().equals("fusion")), label + ": the fusion");
        // The server side: the real Mew takes the actor's place right at the end, once.
        CinematicStateMachine server = new CinematicStateMachine(10, 480, test, 2);
        int spawns = 0;
        long spawnAt = -1;
        for (long now = 0; now < 600; now++) for (CinematicStateMachine.Action action : server.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) {
            spawns++;
            spawnAt = now;
        }
        check(test ? spawns == 0 : spawns == 1 && spawnAt == 488, label + ": the server's spawn (" + spawns + " at " + spawnAt + ")");
    }

    private static void mewSynth() {
        for (double stretch : new double[]{1.0, 20.0 / 24.0, 25.0 / 24.0}) {
            for (double offset : new double[]{0.0, 12.0}) {
                MewScript script = new MewScript(new Vector3d(0, 0, 4), true);
                SynthRenderer music = MewMusic.INSTANCE.music(script, stretch, offset);
                SynthRenderer ambience = MewMusic.INSTANCE.ambience(script, stretch, offset);
                long started = System.nanoTime();
                float peak = music.peak();
                double ms = (System.nanoTime() - started) / 1e6;
                float ambiencePeak = ambience.peak();
                String what = String.format("mew synth x%.2f from %.1f s", stretch, offset);
                check(Float.isFinite(peak) && peak <= 1.0f && peak > 0.05f, what + ": music peak " + peak);
                check(Float.isFinite(ambiencePeak) && ambiencePeak <= 1.0f && ambiencePeak > 0.005f, what + ": ambience peak " + ambiencePeak);
                check(ms < 9000, what + ": music made in " + ms + " ms");
                System.out.printf("%s: music peak %.2f (%.0f ms), ambience peak %.3f%n", what, peak, ms, ambiencePeak);
            }
        }
    }

    private static void mewtwoSynth() {
        for (double stretch : new double[]{1.0, 0.75, 1.25}) {
            for (double offset : new double[]{0.0, 11.3}) {
                MewtwoScript script = new MewtwoScript(new Vector3d(0, 0, 3.5), true, 1.05);
                SynthRenderer music = MewtwoMusic.INSTANCE.music(script, stretch, offset);
                SynthRenderer ambience = MewtwoMusic.INSTANCE.ambience(script, stretch, offset);
                long started = System.nanoTime();
                float peak = music.peak();
                double ms = (System.nanoTime() - started) / 1e6;
                float ambiencePeak = ambience.peak();
                String what = String.format("mewtwo synth x%.2f from %.1f s", stretch, offset);
                check(Float.isFinite(peak) && peak <= 1.0f && peak > 0.05f, what + ": music peak " + peak);
                check(Float.isFinite(ambiencePeak) && ambiencePeak <= 1.0f && ambiencePeak > 0.01f, what + ": ambience peak " + ambiencePeak);
                check(ms < 9000, what + ": music made in " + ms + " ms");
                System.out.printf("%s: music peak %.2f (%.0f ms), ambience peak %.2f%n", what, peak, ms, ambiencePeak);
            }
        }
        SynthRenderer renderer = MewtwoMusic.INSTANCE.music(new MewtwoScript(new Vector3d(0, 0, 3.5), true, 1.05), 1.0, 0.0);
        long started = System.nanoTime();
        SynthBuffer buffer = new SynthBuffer(renderer).start("bench");
        float[] l = new float[4 * 44100];
        float[] r = new float[4 * 44100];
        int first = buffer.read(0, l.length, l, r);
        double ms = (System.nanoTime() - started) / 1e6;
        check(first == l.length && ms < 500, "mewtwo made ahead: first four seconds ready in " + ms + " ms");
        buffer.cancel();
        System.out.printf("mewtwo made ahead: first 4 s in %.0f ms%n", ms);
    }

    private static void stateMachine() {
        // Test mode: never spawns, finishes once.
        CinematicStateMachine test = new CinematicStateMachine(100, 400, true, 20);
        int finishes = 0, spawns = 0;
        check(test.tick(99).isEmpty() && test.getState() == CinematicStateMachine.State.SCHEDULED, "test: scheduled before its start");
        for (long now = 100; now < 600; now++) {
            for (CinematicStateMachine.Action action : test.tick(now)) {
                if (action == CinematicStateMachine.Action.FINISH) {
                    finishes++;
                    check(now == 500, "test: finishes at its end (" + now + ")");
                }
                if (action == CinematicStateMachine.Action.SPAWN) spawns++;
            }
            if (now == 101) check(test.getState() == CinematicStateMachine.State.PLAYING, "test: playing");
        }
        check(finishes == 1 && spawns == 0, "test: one end, no spawn (" + finishes + ", " + spawns + ")");
        check(test.interrupt() == null, "test: no interruption once finished");
        check(!test.getSpawned(), "test: never spawned");

        // Shrine mode: spawns 1 s before the end, once.
        CinematicStateMachine shrine = new CinematicStateMachine(0, 400, false, 20);
        spawns = 0;
        finishes = 0;
        long spawnAt = -1;
        for (long now = 0; now < 800; now++) for (CinematicStateMachine.Action action : shrine.tick(now)) {
            if (action == CinematicStateMachine.Action.SPAWN) {
                spawns++;
                spawnAt = now;
            }
            if (action == CinematicStateMachine.Action.FINISH) finishes++;
        }
        check(spawns == 1 && spawnAt == 380 && finishes == 1, "shrine: spawn at 380 once, one end (" + spawns + ", " + spawnAt + ", " + finishes + ")");

        // Interrupted before the spawn: the Pokémon appears now, once; double interruption: nothing.
        CinematicStateMachine early = new CinematicStateMachine(0, 400, false, 20);
        early.tick(50);
        CinematicStateMachine.Interruption first = early.interrupt();
        check(first != null && first.getSpawnNow(), "shrine interrupted early: spawns now");
        check(early.interrupt() == null, "shrine interrupted twice: nothing more");
        check(early.tick(390).isEmpty() && early.tick(400).isEmpty(), "shrine interrupted: no later spawn nor end");
        check(early.getState() == CinematicStateMachine.State.INTERRUPTED, "shrine interrupted: state");

        // Interrupted after the spawn: no second Pokémon.
        CinematicStateMachine late = new CinematicStateMachine(0, 400, false, 20);
        for (long now = 0; now <= 390; now++) late.tick(now);
        CinematicStateMachine.Interruption after = late.interrupt();
        check(after != null && !after.getSpawnNow(), "shrine interrupted after the spawn: no second spawn");

        // Interrupted while scheduled (before its start) in test mode.
        CinematicStateMachine scheduled = new CinematicStateMachine(1000, 400, true, 20);
        CinematicStateMachine.Interruption cut = scheduled.interrupt();
        check(cut != null && !cut.getSpawnNow() && scheduled.isOver(), "test interrupted before its start");

        // A scene shorter than its spawn lead is refused.
        boolean refused = false;
        try {
            new CinematicStateMachine(0, 10, false, 20);
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        check(refused, "too short a scene refused");
        check(Math.abs(new CinematicStateMachine(100, 400, true, 20).elapsedSeconds(140) - 2.0) < 1e-9, "elapsed seconds");
    }

    private static void flashes() {
        FlashLimiter reduced = new FlashLimiter(true);
        FlashLimiter full = new FlashLimiter(false);
        double maxReduced = 0, maxFull = 0;
        double lastPeakStart = -10;
        int tooClose = 0;
        boolean wasOn = false;
        for (int i = 0; i <= 2000; i++) {
            double t = i * 0.005;
            // A flash every 0.3 s (too often), each a quick pulse.
            double phase = (t % 0.3) / 0.3;
            double raw = Math.exp(-phase * 12.0);
            double r = reduced.limit(t, raw);
            double f = full.limit(t, raw);
            maxReduced = Math.max(maxReduced, r);
            maxFull = Math.max(maxFull, f);
            boolean on = r > 0.01;
            if (on && !wasOn) {
                if (t - lastPeakStart < 0.5 - 1e-9) tooClose++;
                lastPeakStart = t;
            }
            wasOn = on;
        }
        check(maxReduced <= 0.25 + 1e-9, "reduced flashes at 25 % at most (" + maxReduced + ")");
        check(tooClose == 0, "reduced flashes: one every 0.5 s at most (" + tooClose + " too close)");
        check(maxFull < 1.0, "never a fully white screen (" + maxFull + ")");
    }

    private static void clock() {
        double start = CinematicClock.Companion.startSeconds(990, 1000);
        check(Math.abs(start + 0.5) < 1e-9, "clock: half a second ahead (" + start + ")");
        CinematicClock clock = new CinematicClock(start);
        long now = 0;
        clock.frame(now, false);
        now += 250_000_000L;
        clock.frame(now, false);
        check(Math.abs(clock.getSeconds() + 0.25) < 1e-9, "clock: real time");
        now += 2_000_000_000L;
        clock.frame(now, true);
        check(Math.abs(clock.getSeconds() + 0.25) < 1e-9, "clock: frozen while paused");
        now += 100_000_000L;
        clock.frame(now, false);
        check(Math.abs(clock.getSeconds() + 0.15) < 1e-9, "clock: resumes after the pause");
        now += 5_000_000_000L;
        clock.frame(now, false);
        check(Math.abs(clock.getSeconds() - 0.85) < 1e-9, "clock: a long freeze counts for one second (" + clock.getSeconds() + ")");
        CinematicClock late = new CinematicClock(CinematicClock.Companion.startSeconds(1100, 1000));
        check(Math.abs(late.getSeconds() - 5.0) < 1e-9, "clock: a late client starts 5 s in");
    }

    private static void synth() {
        // Made ahead: the first four seconds (the sound engine's first read) within the packet's half second.
        for (int round = 0; round < 2; round++) {
            ArticunoScript script = new ArticunoScript(new Vector3d(0, 0, 4), true);
            long started = System.nanoTime();
            SynthBuffer buffer = new SynthBuffer(ArticunoMusic.INSTANCE.music(script, 1.0, 0.0)).start("bench");
            float[] l = new float[4 * 44100];
            float[] r = new float[4 * 44100];
            int first = buffer.read(0, l.length, l, r);
            double ms = (System.nanoTime() - started) / 1e6;
            int total = first;
            while (true) {
                int n = buffer.read(total, l.length, l, r);
                if (n == 0) break;
                total += n;
            }
            check(first == l.length, "made ahead: first read complete (" + first + ")");
            check(total == buffer.getFrames() && buffer.getDone(), "made ahead: every frame read (" + total + " of " + buffer.getFrames() + ")");
            // The first round warms the JVM up; the second is what a game in progress sees.
            if (round == 1) check(ms < 500, "made ahead: first four seconds ready in " + ms + " ms");
            System.out.printf("made ahead (round %d): first 4 s in %.0f ms, %d frames%n", round, ms, total);
        }
        for (double stretch : new double[]{1.0, 0.75, 1.25}) {
            ArticunoScript script = new ArticunoScript(new Vector3d(0, 0, 4), true);
            for (double offset : new double[]{0.0, 7.3}) {
                long started = System.nanoTime();
                SynthRenderer music = ArticunoMusic.INSTANCE.music(script, stretch, offset);
                float peak = music.peak();
                double ms = (System.nanoTime() - started) / 1e6;
                SynthRenderer ambience = ArticunoMusic.INSTANCE.ambience(script, stretch, offset);
                float ambiencePeak = ambience.peak();
                String what = String.format("synth x%.2f from %.1f s", stretch, offset);
                check(Float.isFinite(peak) && peak <= 1.0f && peak > 0.05f, what + ": music peak " + peak);
                check(Float.isFinite(ambiencePeak) && ambiencePeak <= 1.0f && ambiencePeak > 0.01f, what + ": ambience peak " + ambiencePeak);
                // The sound thread reads a second at a time: 21 s must take well under 21 s.
                check(ms < 8000, what + ": music made in " + ms + " ms");
                System.out.printf("%s: music peak %.2f (%.0f ms), ambience peak %.2f%n", what, peak, ms, ambiencePeak);
            }
        }
    }
}
