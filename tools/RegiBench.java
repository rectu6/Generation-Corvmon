import generations.gg.generations.core.generationscore.common.client.cinematic.audio.RegiMusic;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthBuffer;
import generations.gg.generations.core.generationscore.common.client.cinematic.audio.SynthRenderer;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CamFrame;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.CameraMotion;
import generations.gg.generations.core.generationscore.common.client.cinematic.camera.Shot;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorKey;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.ActorPose;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Cue;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.Grade;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.PieceKey;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.RegiElement;
import generations.gg.generations.core.generationscore.common.client.cinematic.scenes.RegiScript;
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes;
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicStateMachine;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Off-game checks of the Regis' scenes (REGIS-PLAN.md § 7), on the compiled classes: the six
 * scenes in several rooms (open sky, a temple, a cramped cell; letters behind, in front, to the
 * side, none) and both camera settings: no invalid value, the camera above the floor, under the
 * ceiling, within the room, never inside the titan, never jumping outside its cut, the titan in
 * view; the titan, the letters and the fragments move without jumps and stay between the floor
 * and the ceiling; the titan ends where the real Pokémon appears (or vanishes in a test); the
 * cues are valid; the state machine makes one Pokémon whatever happens; the music does not clip.
 * Also the letter puzzle of the Regis' shrines with the D1 fix (a Java copy of RegiShrineBlock's
 * reading, checked on layouts), and that the original reading never matched.
 *
 *   javac -cp <classes>;<kotlin-stdlib>;<joml> -d <out> tools/RegiBench.java
 *   java -cp <out>;<classes>;<kotlin-stdlib>;<joml> RegiBench
 */
public class RegiBench {
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
        puzzle();
        String[] ids = {"regirock", "regice", "registeel", "regieleki", "regidrago", "regigigas"};
        double[][] sizes = {{1.8, 1.3}, {1.8, 1.3}, {1.9, 1.3}, {1.2, 2.0}, {2.1, 2.2}, {3.7, 2.6}};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            double h = sizes[i][0], w = sizes[i][1];
            int n = id.equals("regigigas") ? 0 : id.length();
            timeline(id, h, w, 14, 12, line(n, 0, 1.5, -3.5), false, "open sky, letters behind");
            timeline(id, h, w, 6, 7, line(n, 0, 1.5, 4.0), false, "temple, letters in front");
            timeline(id, h, w, 4, 4, line(n, 6, 1.5, 0), true, "low temple, letters to the side, test");
            timeline(id, h, w, 3, 2.5, new ArrayList<>(), false, "cramped cell, no letters");
            timeline(id, h * 1.4, w * 1.4, 14, 12, line(n, -9, 1.5, -9), true, "open, bigger, far letters, test");
        }
        stateMachine("regirock");
        stateMachine("regigigas");
        music();
        System.out.println(passed + "/" + (passed + failed) + " checks passed");
        if (failed > 0) System.exit(1);
    }

    /** n letters on a line along x, centred at (cx, y, cz). */
    static List<Vector3d> line(int n, double cx, double y, double cz) {
        List<Vector3d> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Vector3d(cx + (i - (n - 1) / 2.0) * 1.0, y, cz));
        return out;
    }

    static void scenes() {
        CinematicScenes c = CinematicScenes.INSTANCE;
        check(c.getALL().indexOf("lugia") == 14 && c.getALL().indexOf("regirock") == 15 && c.getALL().indexOf("regigigas") == 20, "the Regis after Lugia (the other indices unchanged)");
        for (String id : c.getREGIS()) {
            boolean gigas = id.equals("regigigas");
            check(c.getAVAILABLE().contains(id), id + " available");
            check(c.writtenSeconds(id) == (gigas ? 27.0 : 21.0), id + ": written for " + c.writtenSeconds(id) + " s");
            check(c.minSeconds(id) == (gigas ? 20.0 : 15.0) && c.maxSeconds(id) == (gigas ? 30.0 : 25.0), id + ": " + c.minSeconds(id) + " to " + c.maxSeconds(id) + " s");
            check(c.spawnLeadTicks(id) == 2, id + ": the Pokémon appears 2 ticks before the end (where the actor stands)");
            check(c.defaultSpecies(id).equals("cobblemon:" + id), id + ": species " + c.defaultSpecies(id));
            check(RegiElement.Companion.of(id).name().equals(new String[]{"ROCK", "ICE", "STEEL", "ELECTRIC", "DRAGON", "GIANT"}[List.of("regirock", "regice", "registeel", "regieleki", "regidrago", "regigigas").indexOf(id)]), id + ": its element");
        }
        check(c.getREGIS().size() == 6, "six Regi scenes");
        check(c.spawnLeadTicks("articuno") == 20 && c.writtenSeconds("lugia") == 31.0 && c.spawnLeadTicks("lugia") == 2, "the other scenes unchanged");
    }

    // ---------------------------------------------------------------- the puzzle (RegiShrineBlock, copied)

    static long key(int x, int y, int z) {
        return ((long) (x + 1000) << 40) | ((long) (y + 1000) << 20) | (z + 1000);
    }

    /** A world: letters (glyph) and stands. */
    static class World {
        final Map<Long, Character> letters = new HashMap<>();
        final Set<Long> stands = new HashSet<>();

        void letter(int x, int y, int z, char c, boolean stand) {
            letters.put(key(x, y, z), c);
            if (stand) stands.add(key(x, y - 1, z));
        }
    }

    static String symbol(World w, int[] p, boolean original) {
        if (original) {
            // The original: a stand at p with a letter above it, read from p.
            if (w.stands.contains(key(p[0], p[1], p[2])) && w.letters.containsKey(key(p[0], p[1] + 1, p[2]))) return String.valueOf(w.letters.get(key(p[0], p[1] + 1, p[2])));
            return "-";
        }
        // D1: a letter at p with a stand under it.
        if (w.stands.contains(key(p[0], p[1] - 1, p[2])) && w.letters.containsKey(key(p[0], p[1], p[2]))) return String.valueOf(w.letters.get(key(p[0], p[1], p[2])));
        return "-";
    }

    static int[] rel(int[] p, int[] dir, int n) {
        return new int[]{p[0] + dir[0] * n, p[1] + dir[1] * n, p[2] + dir[2] * n};
    }

    static String sequence(World w, int[] facing, int[] p, boolean original) {
        return symbol(w, rel(p, facing, 1), original) + symbol(w, p, original) + symbol(w, rel(p, facing, -1), original);
    }

    static List<int[]> inSequence(World w, List<String> list, int[] axis, int[] pos, boolean original) {
        int[] facing = axis;
        String seq = sequence(w, facing, pos, original);
        if (!list.contains(seq)) {
            seq = new StringBuilder(seq).reverse().toString();
            if (!list.contains(seq)) return null;
            facing = new int[]{-axis[0], -axis[1], -axis[2]};
        }
        int[] base = rel(pos, facing, list.indexOf(seq));
        List<int[]> positions = new ArrayList<>();
        int[] back = {-facing[0], -facing[1], -facing[2]};
        for (int i = 0; i < list.size(); i++) {
            int[] f = rel(base, back, i);
            if (list.contains(sequence(w, facing, f, original))) positions.add(f);
        }
        return !positions.isEmpty() && positions.size() == list.size() ? positions : null;
    }

    /** useItemOn's reading: the first letter within 15 blocks (x, then y, then z), then both axes. */
    static List<int[]> solve(World w, String species, int[] shrine, boolean original) {
        String cipher = "-" + species.toUpperCase() + "-";
        List<String> list = new ArrayList<>();
        for (int i = 0; i < cipher.length() - 2; i++) list.add(cipher.substring(i, i + 3));
        int[] found = null;
        search:
        for (int x = -15; x < 15; x++) for (int y = -15; y < 15; y++) for (int z = -15; z < 15; z++) {
            if (w.letters.containsKey(key(shrine[0] + x, shrine[1] + y, shrine[2] + z))) {
                found = new int[]{shrine[0] + x, shrine[1] + y, shrine[2] + z};
                break search;
            }
        }
        if (found == null) return null;
        List<int[]> z = inSequence(w, list, new int[]{0, 0, 1}, found, original);
        return z != null ? z : inSequence(w, list, new int[]{1, 0, 0}, found, original);
    }

    static World layout(String word, int x0, int y, int z0, int[] dir, boolean stands) {
        World w = new World();
        for (int i = 0; i < word.length(); i++) w.letter(x0 + dir[0] * i, y, z0 + dir[2] * i, word.charAt(i), stands);
        return w;
    }

    static void puzzle() {
        int[] shrine = {0, 64, 0};
        int[] east = {1, 0, 0}, west = {-1, 0, 0}, south = {0, 0, 1}, north = {0, 0, -1};
        Object[][] cases = {
            {"regirock", "REGIROCK", 3, 65, -4, east, true, true, "along x"},
            {"regirock", "REGIROCK", 10, 65, -4, west, true, true, "along x, written backward"},
            {"regirock", "REGIROCK", 5, 65, -6, south, true, true, "along z"},
            {"regirock", "REGIROCK", -5, 65, 6, north, true, true, "along z, written backward"},
            {"regice", "REGICE", -3, 66, 5, east, true, true, "regice, higher"},
            {"regidrago", "REGIDRAGO", -6, 65, -9, east, true, true, "regidrago"},
            {"regieleki", "REGIELEKI", 2, 64, 8, south, true, true, "regieleki"},
            {"registeel", "REGISTEEL", -10, 65, 2, east, true, true, "registeel"},
            {"regirock", "REGIRCK", 3, 65, -4, east, true, false, "a letter missing"},
            {"regirock", "REGICE", 3, 65, -4, east, true, false, "another Regi's name"},
            {"regirock", "REGIROCK", 3, 65, -4, east, false, false, "letters without their stands"},
            {"regice", "REGIROCK", 3, 65, -4, east, true, false, "regirock's word at regice's shrine"},
        };
        for (Object[] c : cases) {
            String species = (String) c[0], word = (String) c[1], what = (String) c[8];
            World w = layout(word, (int) c[2], (int) c[3], (int) c[4], (int[]) c[5], (boolean) c[6]);
            List<int[]> fixed = solve(w, species, shrine, false);
            boolean expected = (boolean) c[7];
            check((fixed != null) == expected, "puzzle " + what + ": " + (fixed != null ? "recognised" : "not recognised"));
            if (fixed != null) {
                boolean letters = fixed.size() == word.length();
                for (int[] p : fixed) if (!w.letters.containsKey(key(p[0], p[1], p[2]))) letters = false;
                check(letters, "puzzle " + what + ": the blocks destroyed are the " + word.length() + " letters, not their stands");
            }
            List<int[]> original = solve(w, species, shrine, true);
            check(original == null, "puzzle " + what + ": the original reading never matched (D1)");
        }
    }

    // ---------------------------------------------------------------- the scenes

    static void timeline(String id, double h, double w, double ceiling, double room, List<Vector3d> letters, boolean test, String what) {
        RegiElement element = RegiElement.Companion.of(id);
        Vector3d landing = new Vector3d(0, 1.5, 0);
        RegiScript s = new RegiScript(element, landing, test, h, w, ceiling, room, letters);
        String label = id + ", " + what;
        double end = s.getEnd();
        boolean open = s.getOpen();
        check(Math.abs(s.action(0.0)) < 1e-9 && Math.abs(s.action(end) - end) < 1e-9, label + ": action from 0 to " + end);
        check(end == CinematicScenes.INSTANCE.writtenSeconds(id), label + ": the script's length is the scene's");
        List<Double> cuts = new ArrayList<>();
        for (Shot shot : s.getDirector().getShots()) if (shot.getStart() > 0 && shot.getBlendIn() <= 0) cuts.add(shot.getStart());
        check(cuts.size() == 1 && Math.abs(cuts.get(0) - s.getRevealAt()) < 1e-9, label + ": one cut, at the reveal " + cuts);
        Vector3f forward = new Vector3f();
        Vector3d centre = s.getCentre();
        double roomLimit = Math.max(2.4, Math.min(room, 12.0) - 0.35) * 1.1 + 0.05;
        for (int mode = 0; mode < 2; mode++) {
            CameraMotion motion = new CameraMotion(mode == 1);
            CamFrame frame = new CamFrame();
            ActorKey actor = new ActorKey();
            PieceKey piece = new PieceKey();
            Grade grade = new Grade();
            double lowest = 99, highest = -99, minFov = 999, maxFov = 0, maxJump = 0, maxJumpAt = 0, actorStep = 0, yawStep = 0, worstView = 0, worstViewAt = 0, nearest = 999, nearestAt = 0, widest = 0;
            double pieceStep = 0, pieceStepAt = 0;
            Vector3d last = null, lastActor = null;
            double lastYaw = Double.NaN;
            int invalid = 0, envelopes = 0, outside = 0, pieceInvalid = 0;
            String first = null;
            Vector3d[] lastLetter = new Vector3d[letters.size()];
            Vector3d[] lastFragment = new Vector3d[s.getFragments()];
            int steps = (int) Math.round(end / 0.005);
            for (int i = 0; i <= steps; i++) {
                double t = i * 0.005;
                double a = s.action(t);
                s.getDirector().evaluate(t, motion, frame);
                if (!frame.isFinite()) {
                    invalid++;
                    if (first == null) first = "camera at " + t;
                }
                Vector3d cam = frame.getPos();
                lowest = Math.min(lowest, cam.y);
                highest = Math.max(highest, cam.y);
                minFov = Math.min(minFov, frame.getFov());
                maxFov = Math.max(maxFov, frame.getFov());
                double horizontal = Math.hypot(cam.x, cam.z);
                if (t > s.getCoreAt() + 1.6) widest = Math.max(widest, horizontal);
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
                if (!actor.isFinite() || Math.abs(actor.getScale() - 1.0) > 1e-9 || Math.abs(actor.getRoll()) > 1e-9 || Math.abs(actor.getPitch()) > 20) {
                    invalid++;
                    if (first == null) first = "actor at " + t;
                }
                boolean seen = actor.getPose() != ActorPose.HIDDEN;
                if (seen) {
                    if (lastActor != null) {
                        actorStep = Math.max(actorStep, lastActor.distance(actor.getPos()));
                        yawStep = Math.max(yawStep, Math.abs(wrap(actor.getYaw() - lastYaw)));
                    }
                    lastActor = new Vector3d(actor.getPos());
                    lastYaw = actor.getYaw();
                }
                if (s.cameraWeight(t) > 0.5) {
                    double axis = Math.hypot(cam.x - centre.x, cam.z - centre.z);
                    boolean overHead = cam.y > landing.y + s.getH() + 0.2;
                    if (!overHead && axis < nearest) {
                        nearest = axis;
                        nearestAt = t;
                    }
                }
                if (seen && a >= s.getShapeAt() + 1.5 && a <= end - 1.0 && s.cameraWeight(t) > 0.5) {
                    Vector3d body = new Vector3d(actor.getPos()).add(0, s.getH() * 0.5, 0);
                    frame.forward(forward);
                    Vector3d to = new Vector3d(body).sub(cam).normalize();
                    double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, forward.x * to.x + forward.y * to.y + forward.z * to.z))));
                    double limit = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(frame.getFov() / 2.0)) * 1.6));
                    if (angle - limit > worstView) {
                        worstView = angle - limit;
                        worstViewAt = t;
                    }
                }
                for (int k = 0; k < letters.size(); k++) {
                    s.letter(k, a, piece);
                    if (piece.getScale() <= 0) {
                        lastLetter[k] = null;
                        continue;
                    }
                    Vector3d p = piece.getPos();
                    if (!finite(p.x, p.y, p.z, piece.getYaw(), piece.getRoll()) || piece.getScale() > 1.0 + 1e-9) pieceInvalid++;
                    if (!open && p.y > ceiling - 0.2 && p.y > letters.get(k).y + 1e-6) outside++;
                    if (lastLetter[k] != null) {
                        double step = lastLetter[k].distance(p);
                        if (step > pieceStep) {
                            pieceStep = step;
                            pieceStepAt = t;
                        }
                    }
                    lastLetter[k] = new Vector3d(p);
                    if (a >= s.getShapeAt()) pieceInvalid++;
                }
                for (int k = 0; k < s.getFragments(); k++) {
                    s.fragment(k, a, piece);
                    if (piece.getScale() <= 0) {
                        lastFragment[k] = null;
                        continue;
                    }
                    Vector3d p = piece.getPos();
                    if (!finite(p.x, p.y, p.z, piece.getYaw(), piece.getPitch(), piece.getRoll())) pieceInvalid++;
                    if (p.y - piece.getScale() * 0.5 < -0.05 || (!open && p.y + piece.getScale() * 0.5 > ceiling + 0.05)) outside++;
                    if (a >= s.getRevealAt()) pieceInvalid++;
                    if (lastFragment[k] != null) {
                        double step = lastFragment[k].distance(p);
                        if (step > pieceStep) {
                            pieceStep = step;
                            pieceStepAt = t;
                        }
                    }
                    lastFragment[k] = new Vector3d(p);
                }
                s.grade(t, grade);
                if (!grade.isFinite() || grade.getExposure() <= 0.3 || grade.getExposure() > 1.5) invalid++;
                double[] common = {s.cameraWeight(t), s.letterbox(t), s.title(t), s.presence(t), s.flash(a)};
                for (double v : common) if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                for (double v : s.envelopes(a)) if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                for (int k = 0; k < s.getLightCount(); k++) {
                    double v = s.light(k, s.getLightCount(), a);
                    if (!finite(v) || v < -1e-9 || v > 1.0 + 1e-9) envelopes++;
                }
                if (!finite(s.shake(a)) || s.shake(a) < 0 || s.shake(a) > 1.6) envelopes++;
            }
            String m = label + (mode == 1 ? ", reduced motion" : "");
            check(invalid == 0, m + ": " + invalid + " invalid values (first: " + first + ")");
            check(lowest >= 0.5, m + ": camera " + lowest + " m above the floor at the lowest");
            check(open || highest <= ceiling - 0.4, m + ": camera " + highest + " m high under a ceiling at " + ceiling);
            check(widest <= roomLimit, m + ": camera " + widest + " m from the shrine in a room of " + room);
            check(nearest >= w * 0.5 + 0.4, m + ": camera " + nearest + " m from the titan's axis at " + nearestAt);
            check(minFov >= 30 && maxFov <= 80, m + ": field of view from " + minFov + " to " + maxFov);
            check(envelopes == 0, m + ": " + envelopes + " envelope values out of range");
            check(maxJump < 0.35, m + ": camera jump " + maxJump + " at " + maxJumpAt);
            check(actorStep < 0.1, m + ": titan's step " + actorStep);
            check(yawStep < 6.0, m + ": titan's turn " + yawStep + "°");
            check(worstView < 1.0, m + ": titan " + worstView + "° out of the frame at " + worstViewAt);
            check(pieceInvalid == 0, m + ": " + pieceInvalid + " invalid pieces (or still there after their time)");
            check(outside == 0, m + ": " + outside + " piece positions through the floor or the ceiling");
            check(pieceStep < 0.3, m + ": piece step " + pieceStep + " at " + pieceStepAt);
            if (mode == 0) System.out.printf("%s: camera %.2f to %.2f m high, %.2f m out, %.2f m from the titan, fov %.0f to %.0f, jump %.3f, pieces %.3f, out of frame %.1f°%n",
                m, lowest, highest, widest, nearest, minFov, maxFov, maxJump, pieceStep, worstView);
        }
        ActorKey key = new ActorKey();
        s.actor(s.getShapeAt() - 0.05, key);
        check(key.getPose() == ActorPose.HIDDEN, label + ": hidden before the shape");
        s.actor(s.getShapeAt() + 0.05, key);
        check(key.getPose() == ActorPose.STAND, label + ": the shape from " + s.getShapeAt());
        s.actor(end - 0.08, key);
        if (test) check(key.getPose() == ActorPose.HIDDEN, label + ": gone at the end of a test");
        else check(key.getPose() == ActorPose.STAND && key.getPos().distance(landing) < 1e-9 && Math.abs(wrap(key.getYaw())) < 1e-6 && Math.abs(key.getPitch()) < 1e-6,
            label + ": standing where the Pokémon appears, facing the invoker (" + key.getPos() + ", " + key.getYaw() + ")");
        check(s.vanish(end) == (test ? 1.0 : 0.0), label + ": vanishes only in a test");
        for (int k = 0; k < letters.size(); k++) check(s.letterArrive(k) < s.getShapeAt(), label + ": letter " + k + " in the shrine before the shape");
        List<Cue> cues = s.getCues();
        double lastTime = -1;
        boolean ok = true;
        for (Cue cue : cues) {
            if (cue.getTime() < lastTime || cue.getTime() < 0 || cue.getTime() > end) ok = false;
            lastTime = cue.getTime();
            if (cue.getId().isEmpty() || cue.getVolume() <= 0 || cue.getPitch() <= 0 || cue.getPitch() > 2.0f) ok = false;
        }
        check(ok, label + ": " + cues.size() + " cues sorted, within the scene, valid");
        check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.CRY && c.getId().equals("cobblemon:pokemon." + id + ".cry") && Math.abs(c.getTime() - s.getCryAt()) < 1e-9), label + ": its cry");
        for (String event : new String[]{"lift", "gather", "core", "matter", "reveal", "lights", "cry", "calm"}) {
            check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.EVENT && c.getId().equals(event)), label + ": event " + event);
        }
        check(cues.stream().anyMatch(c -> c.getKind() == Cue.Kind.EVENT && c.getId().equals("vanish")) == test, label + ": vanish only in a test");
        long beeps = cues.stream().filter(c -> c.getId().equals("minecraft:block.note_block.bit")).count();
        check(beeps == s.getLightCount(), label + ": a beep per light (" + beeps + ")");
        check(s.duck(1.0) > 0.0 && s.duck(10.0) > 0.99 && s.duck(end) < 0.01 && s.duck(0.0) == 0.0, label + ": the other sounds fade, then come back");
        check(element != RegiElement.ELECTRIC || s.getStrikes().size() == 16, label + ": Regieleki's 16 arcs");
    }

    /** One Pokémon, whatever happens. */
    static void stateMachine(String id) {
        int ticks = (int) (CinematicScenes.INSTANCE.writtenSeconds(id) * 20);
        int lead = CinematicScenes.INSTANCE.spawnLeadTicks(id);
        CinematicStateMachine full = new CinematicStateMachine(10, ticks, false, lead);
        int spawns = 0;
        long at = -1;
        for (long now = 0; now < ticks + 300; now++) for (CinematicStateMachine.Action action : full.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) {
            spawns++;
            at = now;
        }
        check(spawns == 1 && at == 10 + ticks - 2, id + " played out: one Pokémon, at " + at);
        check(full.interrupt() == null, id + " played out: an interruption afterwards does nothing");
        for (double cutAt : new double[]{0.0, 2.0, 6.0, 12.0, 15.0, 18.0, ticks / 20.0 - 0.15}) {
            CinematicStateMachine m = new CinematicStateMachine(10, ticks, false, lead);
            int n = 0;
            long cut = 10 + (long) (cutAt * 20);
            for (long now = 0; now <= cut; now++) for (CinematicStateMachine.Action action : m.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) n++;
            CinematicStateMachine.Interruption i = m.interrupt();
            if (i != null && i.getSpawnNow()) n++;
            CinematicStateMachine.Interruption again = m.interrupt();
            if (again != null && again.getSpawnNow()) n++;
            for (long now = cut + 1; now < ticks + 300; now++) for (CinematicStateMachine.Action action : m.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) n++;
            check(n == 1, id + " cut at " + cutAt + " s: one Pokémon (" + n + ")");
        }
        CinematicStateMachine test = new CinematicStateMachine(10, ticks, true, lead);
        int n = 0;
        for (long now = 0; now < 300; now++) for (CinematicStateMachine.Action action : test.tick(now)) if (action == CinematicStateMachine.Action.SPAWN) n++;
        CinematicStateMachine.Interruption i = test.interrupt();
        if (i != null && i.getSpawnNow()) n++;
        check(n == 0, id + " test: no Pokémon, even cut short");
    }

    static void music() {
        String[] ids = {"regirock", "regice", "registeel", "regieleki", "regidrago", "regigigas"};
        for (String id : ids) {
            RegiScript script = new RegiScript(RegiElement.Companion.of(id), new Vector3d(0, 1.5, 0), false, 2.0, 1.5, 8, 8, line(id.length(), 0, 1.5, -3));
            double written = script.getEnd();
            double min = CinematicScenes.INSTANCE.minSeconds(id), max = CinematicScenes.INSTANCE.maxSeconds(id);
            for (double stretch : new double[]{1.0, min / written, max / written}) {
                for (double offset : new double[]{0.0, 9.3}) {
                    SynthRenderer music = RegiMusic.INSTANCE.music(script, stretch, offset);
                    SynthRenderer ambience = RegiMusic.INSTANCE.ambience(script, stretch, offset);
                    long started = System.nanoTime();
                    float peak = music.peak();
                    double ms = (System.nanoTime() - started) / 1e6;
                    float ambiencePeak = ambience.peak();
                    String what = String.format("%s synth x%.2f from %.1f s", id, stretch, offset);
                    check(Float.isFinite(peak) && peak <= 1.0f && peak > 0.05f, what + ": music peak " + peak);
                    check(Float.isFinite(ambiencePeak) && ambiencePeak <= 1.0f && ambiencePeak > 0.005f, what + ": ambience peak " + ambiencePeak);
                    check(ms < 10000, what + ": music made in " + ms + " ms");
                    if (stretch == 1.0 && offset == 0.0) System.out.printf("%s: music peak %.2f (%.0f ms), ambience peak %.2f%n", what, peak, ms, ambiencePeak);
                }
            }
            long started = System.nanoTime();
            SynthBuffer buffer = new SynthBuffer(RegiMusic.INSTANCE.music(script, 1.0, 0.0)).start("bench");
            float[] l = new float[4 * 44100];
            float[] r = new float[4 * 44100];
            int first = buffer.read(0, l.length, l, r);
            double ms = (System.nanoTime() - started) / 1e6;
            check(first == l.length && ms < 500, id + " made ahead: first four seconds ready in " + ms + " ms");
            buffer.cancel();
        }
    }
}
