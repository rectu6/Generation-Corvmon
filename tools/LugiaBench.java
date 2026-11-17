import generations.gg.generations.core.generationscore.common.client.cinematic.audio.LugiaMusic;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthBuffer;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CamFrame;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMotion;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.Shot;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorKey;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorPose;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Cue;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Grade;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.LugiaScript;
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes;
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicStateMachine;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Off-game checks of Lugia's scene (LUGIA-PLAN.md § 7), on the compiled classes: for several
 * places of the whirlpool (on the ground, on water lower or higher, near or far) and both camera
 * settings: the timeline has no invalid value, the camera stays above the floor, never jumps
 * outside its cuts, keeps Lugia in view and out of its body, the field of view stays sane; Lugia
 * moves and turns without jumps, rises out of the whirlpool, fires away from the shrine, lands
 * where the real Lugia appears (or vanishes in a test); the cues are valid; the state machine
 * makes one Lugia whatever happens; the music does not clip and is ready in time.
 *
 *   javac -cp <classes>;<kotlin-stdlib>;<joml> -d <out> tools/LugiaBench.java
 *   java -cp <out>;<classes>;<kotlin-stdlib>;<joml> LugiaBench
 */
public class LugiaBench {
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

    static double wrap(double deg) {
        double x = (deg + 180.0) % 360.0;
        if (x < 0) x += 360.0;
        return x - 180.0;
    }

    public static void main(String[] args) {
        scenes();
        timeline(new Vector3d(0, 0.5, 0), true, 12, 0, false, 0, "ground, test");
        timeline(new Vector3d(0.6, 0.5, -0.4), false, 12, 0, false, 90, "ground, summoning");
        timeline(new Vector3d(0, 0.5, 0), false, 8, -6, true, -135, "sea 6 m lower, near");
        timeline(new Vector3d(1, 0.5, 0.3), false, 20, -2.5, true, 180, "lake, far");
        timeline(new Vector3d(0, 0.5, 0), true, 24, 3, false, 45, "ground higher, farthest, test");
        timeline(new Vector3d(-0.4, 0.5, 0.2), false, 7, -12, true, 0, "sea 12 m lower, nearest");
        stateMachine();
        music();
        System.out.println(passed + "/" + (passed + failed) + " checks passed");
        if (failed > 0) System.exit(1);
    }

    static void scenes() {
        CinematicScenes c = CinematicScenes.INSTANCE;
        check(c.getAVAILABLE().contains("lugia") && c.getALL().indexOf("lugia") == 14, "lugia available, after Johto's (their indices unchanged)");
        check(c.writtenSeconds("lugia") == 31.0 && c.minSeconds("lugia") == 25.0 && c.maxSeconds("lugia") == 35.0, "31 s, 25 to 35");
        check(c.spawnLeadTicks("lugia") == 2, "Lugia appears 2 ticks before the end (where the actor stands)");
        check(c.defaultSpecies("lugia").equals("cobblemon:lugia"), "species cobblemon:lugia");
        check(c.spawnLeadTicks("articuno") == 20 && c.writtenSeconds("ho_oh_summon") == 30.0, "the other scenes unchanged");
    }

    /** The floor under (x, z): the whirlpool's within 12 m of it, the shrine's ground elsewhere. */
    static double floor(LugiaScript s, double x, double z) {
        double dx = x - s.getVortex().x, dz = z - s.getVortex().z;
        return Math.sqrt(dx * dx + dz * dz) <= 12.0 ? s.getVy() : 0.0;
    }

    static void timeline(Vector3d landing, boolean test, double d, double vy, boolean water, double syaw, String label) {
        LugiaScript s = new LugiaScript(landing, test, d, vy, water, syaw);
        double end = LugiaScript.END;
        check(Math.abs(s.action(0.0)) < 1e-6 && Math.abs(s.action(end) - end) < 1e-3, label + ": action from 0 to 31 (" + s.action(end) + ")");
        List<Double> cuts = new ArrayList<>();
        for (Shot shot : s.getDirector().getShots()) if (shot.getStart() > 0 && shot.getBlendIn() <= 0) cuts.add(shot.getStart());
        check(cuts.size() == 2, label + ": two cuts (the burst, the shot) " + cuts);
        Vector3f forward = new Vector3f();
        for (int mode = 0; mode < 2; mode++) {
            CameraMotion motion = new CameraMotion(mode == 1);
            CamFrame frame = new CamFrame();
            ActorKey actor = new ActorKey();
            Grade grade = new Grade();
            double lowest = 99, minFov = 999, maxFov = 0, maxJump = 0, maxJumpAt = 0, lastA = -1, actorStep = 0, actorStepAt = 0, yawStep = 0, yawStepAt = 0;
            double nearest = 999, nearestAt = 0, farthest = 0, farthestAt = 0, worstView = 0, worstViewAt = 0;
            Vector3d last = null, lastActor = null;
            double lastYaw = Double.NaN;
            int invalid = 0, below = 0, backwards = 0, envelopes = 0, underFloor = 0;
            String first = null;
            for (int i = 0; i <= 6200; i++) {
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
                double above = cam.y - floor(s, cam.x, cam.z);
                lowest = Math.min(lowest, above);
                if (above < 0.5) below++;
                minFov = Math.min(minFov, frame.getFov());
                maxFov = Math.max(maxFov, frame.getFov());
                if (last != null) {
                    boolean cut = false;
                    for (double c : cuts) if (Math.abs(t - c) < 0.006) cut = true;
                    double jump = last.distance(cam);
                    if (!cut && jump > maxJump) {
                        maxJump = jump;
                        maxJumpAt = t;
                    }
                }
                last = new Vector3d(cam);
                s.actor(a, actor);
                if (!actor.isFinite() || Math.abs(actor.getScale() - 1.0) > 1e-9 || Math.abs(actor.getRoll()) > 40 || Math.abs(actor.getPitch()) > 40) {
                    invalid++;
                    if (first == null) first = "actor at " + t + " (" + actor.getPitch() + ", " + actor.getRoll() + ", " + actor.getScale() + ")";
                }
                boolean seen = actor.getPose() != ActorPose.HIDDEN;
                if (seen) {
                    if (lastActor != null) {
                        double step = lastActor.distance(actor.getPos());
                        if (step > actorStep) {
                            actorStep = step;
                            actorStepAt = t;
                        }
                        double turn = Math.abs(wrap(actor.getYaw() - lastYaw));
                        if (turn > yawStep) {
                            yawStep = turn;
                            yawStepAt = t;
                        }
                    }
                    lastActor = new Vector3d(actor.getPos());
                    lastYaw = actor.getYaw();
                    // Out of the water, never in the ground (the shrine's block is where it appears).
                    if (a >= LugiaScript.EMERGED && actor.getPos().y < Math.min(floor(s, actor.getPos().x, actor.getPos().z), landing.y) - 0.05) underFloor++;
                }
                // Lugia in view and not too close nor too far, from the burst to the landing (the camera's own).
                if (seen && a >= LugiaScript.BURST + 0.3 && a <= LugiaScript.LANDED && s.cameraWeight(t) > 0.5) {
                    Vector3d body = new Vector3d(actor.getPos()).add(0, 2.2, 0);
                    double dist = cam.distance(body);
                    if (dist < nearest) {
                        nearest = dist;
                        nearestAt = t;
                    }
                    if (dist > farthest) {
                        farthest = dist;
                        farthestAt = t;
                    }
                    frame.forward(forward);
                    Vector3d to = new Vector3d(body).sub(cam).normalize();
                    double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, forward.x * to.x + forward.y * to.y + forward.z * to.z))));
                    // Within the screen: half the vertical field, wider sideways (16:9).
                    double limit = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(frame.getFov() / 2.0)) * 1.6));
                    double over = angle - limit;
                    if (over > worstView) {
                        worstView = over;
                        worstViewAt = t;
                    }
                }
                s.grade(t, grade);
                if (!grade.isFinite() || grade.getExposure() <= 0.3 || grade.getExposure() > 1.5) {
                    invalid++;
                    if (first == null) first = "grade at " + t;
                }
                double[] common = {s.cameraWeight(t), s.letterbox(t), s.title(t), s.presence(t), s.flash(a)};
                for (double v : common) if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                for (double v : s.envelopes(a)) if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                if (!finite(s.shake(a)) || s.shake(a) < 0 || s.shake(a) > 1.6) envelopes++;
            }
            String m = label + (mode == 1 ? ", reduced motion" : "");
            check(invalid == 0, m + ": " + invalid + " invalid values (first: " + first + ")");
            check(backwards == 0, m + ": action time goes back " + backwards + " times");
            check(below == 0, m + ": camera below 0.5 m above the floor " + below + " times (lowest " + lowest + ")");
            check(minFov >= 30 && maxFov <= 80, m + ": field of view from " + minFov + " to " + maxFov);
            check(envelopes == 0, m + ": " + envelopes + " envelope values out of range");
            check(maxJump < 0.35, m + ": camera jump " + maxJump + " at " + maxJumpAt);
            check(actorStep < 0.4, m + ": Lugia's step " + actorStep + " at " + actorStepAt);
            check(yawStep < 6.0, m + ": Lugia's turn " + yawStep + "° at " + yawStepAt);
            check(underFloor == 0, m + ": Lugia under the floor " + underFloor + " times after it rose");
            check(nearest > 4.5, m + ": camera " + nearest + " m from Lugia's body at " + nearestAt);
            check(farthest < 32.0, m + ": camera " + farthest + " m from Lugia at " + farthestAt);
            check(worstView < 1.0, m + ": Lugia " + worstView + "° out of the frame at " + worstViewAt);
            System.out.printf("%s: lowest camera %.2f m, fov %.1f to %.1f, camera step %.3f m at %.3f s, Lugia step %.3f m at %.3f s, turn %.2f° at %.3f s, Lugia from %.1f to %.1f m, out of frame %.1f° at %.2f s%n",
                m, lowest, minFov, maxFov, maxJump, maxJumpAt, actorStep, actorStepAt, yawStep, yawStepAt, nearest, farthest, worstView, worstViewAt);
        }
        ActorKey key = new ActorKey();
        s.actor(LugiaScript.SHAPE - 0.1, key);
        check(key.getPose() == ActorPose.HIDDEN, label + ": hidden before the shape");
        s.actor(LugiaScript.SHAPE + 1.0, key);
        check(key.getPose() != ActorPose.HIDDEN && key.getPos().y < s.getVy(), label + ": the shape under the surface");
        s.actor(LugiaScript.EMERGED, key);
        check(key.getPos().y > s.getVy() + 8.0, label + ": out of the whirlpool at " + LugiaScript.EMERGED);
        s.actor(LugiaScript.CRY, key);
        check(key.getPos().y >= s.getBase() + 16.0 && Math.abs(wrap(key.getYaw())) < 5.0, label + ": high, facing the shrine at the cry (" + key.getYaw() + ")");
        s.actor(LugiaScript.FIRE, key);
        check(Math.abs(wrap(key.getYaw() - 180.0)) < 1e-6 && key.getPose() == ActorPose.HOVER, label + ": hovering away from the shrine at the shot");
        // The beam never passes near the shrine's side.
        double nearestBeam = 999;
        Vector3d sp = s.getSphere();
        Vector3d dir = s.getBeamDir();
        for (int i = 0; i <= 140; i++) {
            Vector3d p = new Vector3d(sp).add(dir.x * i, dir.y * i, dir.z * i);
            nearestBeam = Math.min(nearestBeam, p.distance(0, 1, 0));
            check(p.z <= sp.z + 1e-9, label + ": beam toward -z");
        }
        check(nearestBeam > 12.0 && dir.y > 0.2, label + ": beam " + nearestBeam + " m from the shrine at the nearest, rising");
        s.actor(LugiaScript.LANDED + 0.2, key);
        check(key.getPose() == ActorPose.STAND && key.getPos().distance(landing) < 1e-6, label + ": standing where Lugia appears");
        s.actor(end - 0.05, key);
        check(test ? key.getPose() == ActorPose.HIDDEN : key.getPose() == ActorPose.STAND && key.getPos().distance(landing) < 1e-6 && Math.abs(wrap(key.getYaw() - syaw)) < 1e-6,
            label + ": at the end " + key.getPose() + " yaw " + key.getYaw());
        check(s.vanish(end) == (test ? 1.0 : 0.0), label + ": vanishes only in a test");
        for (double[] strike : s.getStrikes()) {
            double fromVortex = Math.hypot(strike[1] - s.getVortex().x, strike[2] - s.getVortex().z);
            double fromShrine = Math.hypot(strike[1], strike[2]);
            check(fromVortex >= 35 && fromShrine >= 25, label + ": lightning far away (" + fromVortex + ", " + fromShrine + ")");
        }
        List<Cue> cues = s.getCues();
        double lastTime = -1;
        boolean ok = true;
        for (Cue cue : cues) {
            if (cue.getTime() < lastTime || cue.getTime() < 0 || cue.getTime() > end) ok = false;
            lastTime = cue.getTime();
            if (cue.getId().isEmpty() || cue.getVolume() <= 0 || cue.getPitch() <= 0 || cue.getPitch() > 2.0f) ok = false;
        }
        check(ok, label + ": " + cues.size() + " cues sorted, within the scene, valid");
        check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.CRY && c.getId().equals("cobblemon:pokemon.lugia.cry") && Math.abs(c.getTime() - LugiaScript.CRY) < 1e-9), label + ": its cry at 17.2");
        check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.ANIMATE && c.getId().equals("special")), label + ": Aeroblast with special");
        check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.ANIMATE && c.getId().equals("glad")), label + ": wings opened with glad");
        for (String event : new String[]{"surge", "burst", "cry", "fire", "landed"}) {
            check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.EVENT && c.getId().equals(event)), label + ": event " + event);
        }
        boolean song = cues.stream().anyMatch(c -> c.getId().contains("lugia_shrine_song"));
        check(song == test, label + ": the shrine's song played by the scene only in a test");
        double[] sum = new double[1];
        cues.forEach(c -> sum[0] += c.getVolume());
        check(s.duck(1.0) > 0.0 && s.duck(15.0) > 0.99 && s.duck(end) < 0.01 && s.duck(0.0) == 0.0, label + ": the other sounds fade, then come back");
    }

    /** Lugia's session: one Lugia, whatever happens (CINEMATIQUES-PLAN.md machine, 620 ticks, 2 ahead). */
    static void stateMachine() {
        int ticks = (int) (CinematicScenes.INSTANCE.writtenSeconds("lugia") * 20);
        CinematicStateMachine full = new CinematicStateMachine(10, ticks, false, CinematicScenes.INSTANCE.spawnLeadTicks("lugia"));
        int spawns = 0;
        long at = -1;
        for (long now = 0; now < 900; now++) for (CinematicStateMachine.Action action : full.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) {
            spawns++;
            at = now;
        }
        check(spawns == 1 && at == 10 + ticks - 2, "played out: one Lugia, at " + at);
        check(full.interrupt() == null, "played out: an interruption afterwards does nothing");
        // Cut short in each sequence (the invoker leaving, a stop, the server stopping): at once, once.
        for (double cutAt : new double[]{0.0, 3.0, 8.0, 15.0, 22.0, 24.0, 28.0, 30.85}) {
            CinematicStateMachine m = new CinematicStateMachine(10, ticks, false, 2);
            int n = 0;
            long cut = 10 + (long) (cutAt * 20);
            for (long now = 0; now <= cut; now++) for (CinematicStateMachine.Action action : m.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) n++;
            CinematicStateMachine.Interruption i = m.interrupt();
            if (i != null && i.getSpawnNow()) n++;
            CinematicStateMachine.Interruption again = m.interrupt();
            if (again != null && again.getSpawnNow()) n++;
            for (long now = cut + 1; now < 900; now++) for (CinematicStateMachine.Action action : m.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) n++;
            check(n == 1, "cut at " + cutAt + " s: one Lugia (" + n + ")");
        }
        // A test: never.
        CinematicStateMachine test = new CinematicStateMachine(10, ticks, true, 2);
        int n = 0;
        for (long now = 0; now < 300; now++) for (CinematicStateMachine.Action action : test.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) n++;
        CinematicStateMachine.Interruption i = test.interrupt();
        if (i != null && i.getSpawnNow()) n++;
        check(n == 0, "test: no Lugia, even cut short");
    }

    static void music() {
        LugiaScript script = new LugiaScript(new Vector3d(0, 0.5, 0), false, 12, -3, true, 0);
        for (double stretch : new double[]{1.0, 25.0 / 31.0, 35.0 / 31.0}) {
            for (double offset : new double[]{0.0, 13.7}) {
                SynthRenderer music = LugiaMusic.INSTANCE.music(script, stretch, offset);
                SynthRenderer ambience = LugiaMusic.INSTANCE.ambience(script, stretch, offset);
                long started = System.nanoTime();
                float peak = music.peak();
                double ms = (System.nanoTime() - started) / 1e6;
                float ambiencePeak = ambience.peak();
                String what = String.format("lugia synth x%.2f from %.1f s", stretch, offset);
                check(Float.isFinite(peak) && peak <= 1.0f && peak > 0.05f, what + ": music peak " + peak);
                check(Float.isFinite(ambiencePeak) && ambiencePeak <= 1.0f && ambiencePeak > 0.01f, what + ": ambience peak " + ambiencePeak);
                check(ms < 10000, what + ": music made in " + ms + " ms");
                System.out.printf("%s: music peak %.2f (%.0f ms), ambience peak %.2f%n", what, peak, ms, ambiencePeak);
            }
        }
        long started = System.nanoTime();
        SynthBuffer buffer = new SynthBuffer(LugiaMusic.INSTANCE.music(script, 1.0, 0.0)).start("bench");
        float[] l = new float[4 * 44100];
        float[] r = new float[4 * 44100];
        int first = buffer.read(0, l.length, l, r);
        double ms = (System.nanoTime() - started) / 1e6;
        check(first == l.length && ms < 500, "made ahead: first four seconds ready in " + ms + " ms");
        buffer.cancel();
        System.out.printf("made ahead: first 4 s in %.0f ms%n", ms);
    }
}
