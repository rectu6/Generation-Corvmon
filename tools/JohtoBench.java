import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CamFrame;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMotion;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorKey;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorPose;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Cue;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Grade;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.SceneScript;
import generations.gg.generations.core.generationscore.common.client.johto.HoOhSummonScript;
import generations.gg.generations.core.generationscore.common.client.johto.JohtoAwakeningScript;
import generations.gg.generations.core.generationscore.common.client.johto.JohtoEncounterScript;
import generations.gg.generations.core.generationscore.common.client.johto.JohtoHistoryScript;
import generations.gg.generations.core.generationscore.common.client.johto.JohtoMusic;
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes;
import generations.gg.generations.core.generationscore.common.world.johto.BeastState;
import generations.gg.generations.core.generationscore.common.world.johto.JohtoBeast;
import generations.gg.generations.core.generationscore.common.world.johto.JohtoRecord;
import generations.gg.generations.core.generationscore.common.world.johto.JohtoStateMachine;
import generations.gg.generations.core.generationscore.common.world.johto.JohtoTrialMath;
import generations.gg.generations.core.generationscore.common.world.johto.SummonState;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Off-game checks of the Legend of Johto (JOHTO-PLAN.md § 12): the per-player state machine (each
 * transition from one state only, no duplicate reward or spawn, recovery after a crash), its
 * saving, the trials' shapes and windows, the four scenes (timeline, camera above the ground,
 * no jump, actors, cues) and their music.
 *
 * Build the mod first (common/build/classes/kotlin/main), then:
 *   javac -cp <classes>;<kotlin-stdlib>;<joml> -d <out> tools/JohtoBench.java
 *   java -cp <out>;<classes>;<kotlin-stdlib>;<joml> JohtoBench
 */
public class JohtoBench {
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
        stateMachine();
        saving();
        shapes();
        scene("history", new JohtoHistoryScript(22.0, 7.0), 50.0, 0.0);
        scene("history small", new JohtoHistoryScript(8.0, 3.0), 50.0, 0.0);
        scene("history large", new JohtoHistoryScript(60.0, 20.0), 50.0, 0.0);
        for (int b = 0; b < 3; b++) {
            scene("awakening " + b, new JohtoAwakeningScript(b, 35.0, 1.0), 20.0, 0.0);
            scene("encounter " + b, new JohtoEncounterScript(b), 22.0, 0.0);
        }
        scene("summon", new HoOhSummonScript(new Vector3d(0, 0, 5)), 30.0, 0.0);
        scene("summon low", new HoOhSummonScript(new Vector3d(3, -4, 6)), 30.0, -4.0);
        actors();
        music();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static final JohtoStateMachine M = JohtoStateMachine.INSTANCE;

    private static void stateMachine() {
        JohtoRecord r = new JohtoRecord();
        JohtoBeast b = JohtoBeast.RAIKOU;
        check(!M.opened(r, b) && !M.begin(r, b) && !M.win(r, b), "nothing happens before an ash");
        check(M.awaken(r, b) && r.track(b).getState() == BeastState.AWAKENING && r.track(b).getSession() == 1, "an ash opens session 1");
        check(!M.awaken(r, b), "a second ash on the same beast is refused (one session per beast)");
        check(M.awaken(r, JohtoBeast.ENTEI), "another beast may be awakened meanwhile");
        check(M.opened(r, b) && !M.opened(r, b), "the awakening opens the session once");
        check(M.begin(r, b) && r.track(b).getState() == BeastState.RUNNING, "the trial runs");
        check(M.roundWon(r, b) && r.track(b).getRound() == 2, "round 2");
        check(M.fail(r, b, 1000L, 20 * 60_000L) && r.track(b).getState() == BeastState.COOLDOWN && r.track(b).getRound() == 2, "a failure waits, the round is kept");
        check(!M.fail(r, b, 1000L, 1L), "a second failure changes nothing");
        check(!M.ready(r, b, 1000L + 60_000L), "not ready before the delay");
        check(M.ready(r, b, 1000L + 20 * 60_000L) && r.track(b).getState() == BeastState.OPEN && r.track(b).getSession() == 1, "ready after the delay, same session, no new ash");
        check(M.begin(r, b) && M.roundWon(r, b) && r.track(b).getRound() == 3 && !M.roundWon(r, b), "round 3 is the last");
        check(M.win(r, b) && r.track(b).getState() == BeastState.ENCOUNTER && r.track(b).getMemoryOwed(), "won: the memory is owed");
        check(!M.win(r, b), "won once");
        check(M.memoryGiven(r, b) && !M.memoryGiven(r, b) && r.track(b).getMemoriesGiven() == 1, "the memory is given once");
        check(M.spawned(r, b, "uuid-1") && !M.spawned(r, b, "uuid-2") && r.track(b).getState() == BeastState.IDLE, "the beast appears once per session");
        check("uuid-1".equals(r.track(b).getLastSpawn()), "the spawn is remembered");
        check(M.awaken(r, b) && r.track(b).getSession() == 2 && r.track(b).getRound() == 1, "a new ash, a new session from round 1");
        // A crash during the awakening or the trial: open again, nothing lost.
        check(M.recover(r) && r.track(b).getState() == BeastState.OPEN && r.track(JohtoBeast.ENTEI).getState() == BeastState.OPEN, "recovery opens what was playing");
        check(!M.recover(r), "recovery changes nothing twice");
        // Ho-Oh.
        check(M.summon(r) && !M.summon(r) && r.getSummon() == SummonState.SUMMONING, "the memories are consumed once");
        check(M.hoOhSpawned(r) && !M.hoOhSpawned(r) && r.getSummons() == 1, "Ho-Oh appears once per summoning");
        // Admin.
        check(M.forceOpen(r, JohtoBeast.SUICUNE) && r.track(JohtoBeast.SUICUNE).getState() == BeastState.OPEN, "an admin opens a session without ash");
        M.begin(r, JohtoBeast.SUICUNE);
        M.fail(r, JohtoBeast.SUICUNE, 0L, 999_999L);
        check(M.clearCooldown(r, JohtoBeast.SUICUNE) && r.track(JohtoBeast.SUICUNE).getState() == BeastState.OPEN, "an admin clears a delay");
        M.reset(r, JohtoBeast.SUICUNE);
        check(r.track(JohtoBeast.SUICUNE).getState() == BeastState.IDLE, "an admin resets a beast");
        check(JohtoBeast.Companion.of("cobblemon:Raikou") == JohtoBeast.RAIKOU && JohtoBeast.Companion.of("pikachu") == null, "beasts by species");
    }

    private static void saving() {
        JohtoRecord r = new JohtoRecord();
        r.setHistorySeen(true);
        r.getSeen().add("johto_history");
        r.getSeen().add("raikou_awakening");
        M.awaken(r, JohtoBeast.ENTEI);
        M.opened(r, JohtoBeast.ENTEI);
        M.begin(r, JohtoBeast.ENTEI);
        M.roundWon(r, JohtoBeast.ENTEI);
        M.fail(r, JohtoBeast.ENTEI, 123456789L, 1000L);
        M.summon(r);
        Map<String, Object> saved = new HashMap<>(r.save());
        JohtoRecord back = JohtoRecord.Companion.load(saved);
        check(back.getHistorySeen() && back.getSeen().contains("raikou_awakening") && back.getSeen().size() == 2, "saved: the scenes seen");
        check(back.track(JohtoBeast.ENTEI).getState() == BeastState.COOLDOWN && back.track(JohtoBeast.ENTEI).getRound() == 2
            && back.track(JohtoBeast.ENTEI).getCooldownUntil() == 123456789L + 1000L && back.track(JohtoBeast.ENTEI).getFailures() == 1, "saved: a beast's track");
        check(back.getSummon() == SummonState.SUMMONING, "saved: Ho-Oh coming");
        // Booleans written as numbers (NBT) read back.
        saved.put("b1.owed", 1);
        saved.put("history", 0);
        JohtoRecord n = JohtoRecord.Companion.load(saved);
        check(n.track(JohtoBeast.ENTEI).getMemoryOwed() && !n.getHistorySeen(), "saved: numbers for booleans");
        check(JohtoRecord.Companion.load(new HashMap<>()).track(JohtoBeast.RAIKOU).getState() == BeastState.IDLE, "an empty record is idle");
    }

    private static void shapes() {
        JohtoTrialMath t = JohtoTrialMath.INSTANCE;
        double[] line = {0, 0, 10, 0};
        check(t.hits(JohtoTrialMath.LINE, line, 100, 112, 13, 105, 5, 0.5, false), "the line touches on it");
        check(!t.hits(JohtoTrialMath.LINE, line, 100, 112, 13, 105, 5, 2.0, false), "the line misses beside it");
        check(!t.hits(JohtoTrialMath.LINE, line, 100, 112, 13, 99, 5, 0.0, false), "nothing before it burns");
        check(!t.hits(JohtoTrialMath.LINE, line, 100, 112, 13, 113, 5, 0.0, false), "nothing after it");
        double[] circles = {0, 0, 6, 6};
        check(t.hits(JohtoTrialMath.CIRCLES, circles, 0, 12, 13, 5, 6.5, 6.5, false) && !t.hits(JohtoTrialMath.CIRCLES, circles, 0, 12, 13, 5, 3, 3, false), "the circles");
        double[] wave = {0, 0, 1, 0};
        long at = 0;
        long when = at + JohtoTrialMath.WAVE_TICKS / 2;
        double r = t.waveRadius(at, when, 13);
        check(t.hits(JohtoTrialMath.WAVE, wave, at, at + 26, 13, when, 0, r, false), "the wave touches on its ring");
        check(!t.hits(JohtoTrialMath.WAVE, wave, at, at + 26, 13, when, r, 0, false), "the wave's gap is safe");
        check(!t.hits(JohtoTrialMath.WAVE, wave, at, at + 26, 13, when, 0, r, true), "jumping over the wave is safe");
        double[] cone = {0, 0, 0, 10};
        check(t.hits(JohtoTrialMath.CONE, cone, 0, 14, 13, 5, 0.5, 8, false) && !t.hits(JohtoTrialMath.CONE, cone, 0, 14, 13, 5, 8, 2, false), "the cone");
        check(!t.hits(JohtoTrialMath.CONE, cone, 0, 14, 13, 5, 0, 16, false), "the cone ends");
        // The harmony's window: half the latency allowed for, ± the window.
        long target = t.harmonyTick(1000, 2);
        check(t.onTime(target, 0, target, 350) && t.onTime(target + 6, 200, target, 350) && !t.onTime(target + 10, 0, target, 350), "the harmony's window with latency");
        check(t.harmonyTick(1000, 1) > t.harmonyTick(1000, 0) && t.harmonyTick(1000, 9) - t.harmonyTick(1000, 8) >= 44, "the harmony's meetings, quicker but not too quick");
        check(Math.abs(t.angleDiff(Math.PI - 0.1, -Math.PI + 0.1) + 0.2) < 1e-9, "angles wrap");
    }

    /** Generic checks of a scene, through SceneScript. ground: the lowest ground under the camera. */
    private static void scene(String label, SceneScript script, double seconds, double ground) {
        for (int mode = 0; mode < 2; mode++) {
            CameraMotion motion = new CameraMotion(mode == 1);
            CamFrame frame = new CamFrame();
            ActorKey actor = new ActorKey();
            Grade grade = new Grade();
            double lowest = 99, maxFov = 0, minFov = 999, maxJump = 0, maxJumpAt = 0;
            Vector3d last = null;
            int invalid = 0, below = 0, envelopes = 0;
            String firstInvalid = null;
            int steps = (int) (seconds / 0.005);
            for (int i = 0; i <= steps; i++) {
                double t = i * 0.005;
                double a = script.action(t);
                script.getDirector().evaluate(t, motion, frame);
                if (!frame.isFinite()) {
                    invalid++;
                    if (firstInvalid == null) firstInvalid = "camera at " + t;
                }
                double h = frame.getPos().y - ground;
                lowest = Math.min(lowest, h);
                if (h < 0.5) below++;
                minFov = Math.min(minFov, frame.getFov());
                maxFov = Math.max(maxFov, frame.getFov());
                if (last != null) {
                    double jump = last.distance(frame.getPos());
                    if (jump > maxJump) {
                        maxJump = jump;
                        maxJumpAt = t;
                    }
                }
                last = new Vector3d(frame.getPos());
                script.actor(a, actor);
                if (!actor.isFinite() || actor.getScale() < 0 || actor.getScale() > 4) {
                    invalid++;
                    if (firstInvalid == null) firstInvalid = "actor at " + t;
                }
                script.grade(t, grade);
                if (!grade.isFinite() || grade.getExposure() <= 0.3 || grade.getExposure() > 1.6) {
                    invalid++;
                    if (firstInvalid == null) firstInvalid = "grade at " + t;
                }
                double[] values = {script.cameraWeight(t), script.letterbox(t), script.title(t), script.presence(t), script.flash(a)};
                for (double v : values) if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                for (double v : script.envelopes(a)) if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                if (!finite(script.shake(a)) || script.shake(a) < 0) envelopes++;
            }
            String m = label + (mode == 1 ? ", reduced motion" : "");
            check(invalid == 0, m + ": " + invalid + " invalid values (first: " + firstInvalid + ")");
            check(below == 0, m + ": camera below 0.5 m above the ground " + below + " times (lowest " + lowest + ")");
            check(minFov >= 20 && maxFov <= 100, m + ": field of view from " + minFov + " to " + maxFov);
            check(envelopes == 0, m + ": " + envelopes + " envelope values out of [0, 1]");
            check(maxJump < 0.35, m + ": camera jump " + maxJump + " at " + maxJumpAt);
            check(script.cameraWeight(0.0) < 0.01 && script.cameraWeight(seconds) < 0.01, m + ": the player's own camera at both ends");
            System.out.printf("%s: lowest camera %.2f m, fov %.1f to %.1f, largest step %.3f m at %.3f s%n", m, lowest, minFov, maxFov, maxJump, maxJumpAt);
        }
        List<Cue> cues = script.getCues();
        double lastTime = -1;
        boolean sorted = true, inside = true, named = true;
        for (Cue cue : cues) {
            if (cue.getTime() < lastTime) sorted = false;
            lastTime = cue.getTime();
            if (cue.getTime() < 0 || cue.getTime() > seconds) inside = false;
            if (cue.getId().isEmpty() || !Float.isFinite(cue.getVolume()) || cue.getVolume() <= 0 || cue.getPitch() <= 0 || cue.getPitch() > 2.0f) named = false;
        }
        check(sorted && inside && named, label + ": " + cues.size() + " cues sorted, within the scene, valid");
    }

    /** The actors where the plan says (no Eevee, the beasts reborn, the statue formed again...). */
    private static void actors() {
        JohtoHistoryScript h = new JohtoHistoryScript(22.0, 7.0);
        ActorKey k = new ActorKey();
        check(h.actor(10.0, k).getPose() == ActorPose.HIDDEN, "history: no Ho-Oh before the rainbow");
        check(h.actor(36.0, k).getPose() == ActorPose.HOVER, "history: Ho-Oh hovers over the tower");
        for (int i = 0; i < 3; i++) {
            check(h.beast(i, 30.0, new ActorKey()).getPose() == ActorPose.HIDDEN, "history: beast " + i + " not there before the rebirth");
            check(h.beast(i, 40.0, new ActorKey()).getPose() == ActorPose.STAND, "history: beast " + i + " reborn");
            check(h.beast(i, 49.0, new ActorKey()).getPose() == ActorPose.HIDDEN, "history: beast " + i + " gone at the end");
        }
        check(h.shapes(5.0) > 0.9 && h.shapes(30.0) < 1e-6, "history: the unknown shapes, then gone");
        check(h.rainFall(25.0) > 0.9 && h.rainFall(5.0) < 1e-6 && h.rainFall(45.0) < 1e-6, "history: the rain only in the storm");
        JohtoAwakeningScript w = new JohtoAwakeningScript(1, 35.0, 1.0);
        check(w.actor(2.0, k).getYaw() == 35.0 && w.actor(2.0, k).getPose() == ActorPose.STAND, "awakening: the statue in its own way");
        check(Math.abs(w.actor(12.6, k).getYaw()) < 1.0, "awakening: it turns to the player");
        check(w.actor(18.0, k).getPose() == ActorPose.HIDDEN && w.reform(18.5, new ActorKey()).getPose() == ActorPose.STAND, "awakening: gone, the statue forms again");
        Vector3d far = w.beastPos(16.5, new Vector3d());
        check(far.length() > 15.0, "awakening: it fled far (" + far.length() + " blocks)");
        JohtoEncounterScript e = new JohtoEncounterScript(2);
        check(e.actor(2.0, k).getPose() == ActorPose.HIDDEN && e.actor(21.9, k).getPose() == ActorPose.STAND && e.actor(21.9, k).getPos().length() < 1e-6, "encounter: it stands where the wild one appears");
        HoOhSummonScript s = new HoOhSummonScript(new Vector3d(1, 0, 6));
        check(s.actor(10.0, k).getPose() == ActorPose.HIDDEN, "summon: Ho-Oh not before its descent");
        check(s.actor(29.9, k).getPose() == ActorPose.STAND && s.actor(29.9, k).getPos().distance(new Vector3d(1, 0, 6)) < 1e-6, "summon: Ho-Oh lands on the spawn point");
        check(s.ghost(0, 8.0, new ActorKey()).getPose() == ActorPose.STAND && s.ghost(0, 25.0, new ActorKey()).getPose() == ActorPose.HIDDEN, "summon: the spectral beasts come and go");
        check(CinematicScenes.INSTANCE.spawnLeadTicks("raikou_encounter") == 2 && CinematicScenes.INSTANCE.spawnLeadTicks("ho_oh_summon") == 2, "the wild Pokémon takes the actor's place at the end");
        check(CinematicScenes.INSTANCE.minSeconds("johto_history") == 40.0 && CinematicScenes.INSTANCE.maxSeconds("johto_history") == 60.0, "history: 40 to 60 s");
    }

    private static void music() {
        String[] ids = {"johto_history", "raikou_awakening", "entei_awakening", "suicune_awakening", "raikou_encounter", "entei_encounter", "suicune_encounter", "ho_oh_summon"};
        for (String id : ids) {
            SceneScript script;
            if (id.equals("johto_history")) script = new JohtoHistoryScript(22.0, 7.0);
            else if (id.equals("ho_oh_summon")) script = new HoOhSummonScript(new Vector3d(0, 0, 5));
            else if (id.endsWith("awakening")) script = new JohtoAwakeningScript(id.startsWith("raikou") ? 0 : id.startsWith("entei") ? 1 : 2, 0.0, 1.0);
            else script = new JohtoEncounterScript(id.startsWith("raikou") ? 0 : id.startsWith("entei") ? 1 : 2);
            for (double stretch : new double[]{1.0, 0.8, 1.2}) {
                long started = System.nanoTime();
                SynthRenderer music = JohtoMusic.INSTANCE.music(id, script, stretch, 0.0);
                float peak = music.peak();
                double ms = (System.nanoTime() - started) / 1e6;
                float amb = JohtoMusic.INSTANCE.ambience(id, script, stretch, 0.0).peak();
                String what = String.format("%s x%.2f", id, stretch);
                check(Float.isFinite(peak) && peak <= 1.0f && peak > 0.05f, what + ": music peak " + peak);
                check(Float.isFinite(amb) && amb <= 1.0f && amb > 0.005f, what + ": ambience peak " + amb);
                check(ms < 15000, what + ": made in " + ms + " ms");
                System.out.printf("%s: music peak %.2f (%.0f ms), ambience %.3f%n", what, peak, ms, amb);
            }
        }
    }
}
