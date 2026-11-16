import generations.gg.generations.core.generationscore.common.world.rks.machine.RksLayout;
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksModule;
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksPoints;
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksState;
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksStateMachine;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Off-game checks of the RKS Machine (RKS-PLAN.md § 7): the assembly in the four orientations
 * (RksLayout against Generations' own multi-cell placement), the state machine (double clicks,
 * a double spawn, reloads, the experiment through a dismantling), and the psychic points on
 * every Psychic species of Cobblemon (read from its jar's species files).
 *
 * Build the mod first (common/build/classes/kotlin/main), then:
 *   javac -cp <classes>;<kotlin-stdlib> -d <out> tools/RksBench.java
 *   java -cp <out>;<classes>;<kotlin-stdlib> RksBench <the Cobblemon jar>
 */
public class RksBench {
    private static int passed = 0;
    private static int failed = 0;

    private static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] args) throws Exception {
        layout();
        machine();
        if (args.length > 0) points(args[0]);
        else System.out.println("(no Cobblemon jar given: the points are not checked)");
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- assembly

    /** Minecraft's facings by 2D value (0 south, 1 west, 2 north, 3 east): x, z. */
    private static final int[][] DIR = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

    /** Direction.getCounterClockWise and getOpposite, by 2D value, as Minecraft defines them. */
    private static int ccw(int f) {
        // south -> east, west -> south, north -> west, east -> north
        return new int[]{3, 0, 1, 2}[f];
    }

    private static int opposite(int f) {
        return (f + 2) % 4;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /**
     * The world as GenericRotatableModelBlock fills it: a module's cell (x, y, z) at its first cell
     * .relative(facing.counterClockWise, x).above(y).relative(facing.opposite, z), the module's
     * first cell where the plan's layout puts it (computed the same way from the console's).
     */
    private static Map<String, RksLayout.Cell> world(int facing, int cx, int cy, int cz, RksModule skip, RksModule turned) {
        Map<String, RksLayout.Cell> world = new HashMap<>();
        int[] r = DIR[ccw(facing)];
        int[] b = DIR[opposite(facing)];
        for (RksModule m : RksModule.values()) {
            if (m == skip) continue;
            int f = m == turned ? (facing + 1) % 4 : facing;
            int bx = cx + m.getOx() * r[0] + m.getOz() * b[0];
            int bz = cz + m.getOx() * r[1] + m.getOz() * b[1];
            int[] rr = DIR[ccw(f)];
            int[] bb = DIR[opposite(f)];
            for (int x = 0; x < m.getW(); x++) for (int y = 0; y < m.getH(); y++) for (int z = 0; z < m.getD(); z++) {
                int wx = bx + x * rr[0] + z * bb[0];
                int wz = bz + x * rr[1] + z * bb[1];
                RksLayout.Cell previous = world.put(key(wx, cy + y, wz), new RksLayout.Cell(m, x, y, z, f));
                if (previous != null && m != turned) check(false, "facing " + facing + ": " + m + " overlaps " + previous.getModule());
            }
        }
        return world;
    }

    private static void layout() {
        check(RksLayout.INSTANCE.getCELLS() == 19, "19 cells filled (" + RksLayout.INSTANCE.getCELLS() + ")");
        String[] names = {"south", "west", "north", "east"};
        for (int f = 0; f < 4; f++) {
            String label = "facing " + names[f];
            int cx = 10, cy = 64, cz = -7;
            Map<String, RksLayout.Cell> world = world(f, cx, cy, cz, null, null);
            check(world.size() == 19, label + ": 19 cells (" + world.size() + ")");
            // Every cell inside the 3 x 4 x 3 box of the machine.
            int[] r = DIR[ccw(f)];
            int[] b = DIR[opposite(f)];
            Set<String> box = new HashSet<>();
            for (int x = 0; x < 3; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 3; z++) box.add(key(cx + x * r[0] + z * b[0], cy + y, cz + x * r[1] + z * b[1]));
            check(box.containsAll(world.keySet()), label + ": every cell inside the 3 x 4 x 3 box");
            List<RksModule> missing = RksLayout.INSTANCE.missing(f, cx, cy, cz, (x, y, z) -> world.get(key(x, y, z)));
            check(missing.isEmpty(), label + ": complete (" + missing + ")");
            for (RksModule m : RksModule.values()) {
                if (m == RksModule.CONSOLE) continue;
                Map<String, RksLayout.Cell> without = world(f, cx, cy, cz, m, null);
                List<RksModule> one = RksLayout.INSTANCE.missing(f, cx, cy, cz, (x, y, z) -> without.get(key(x, y, z)));
                check(one.size() == 1 && one.get(0) == m, label + ": without " + m + " -> " + one);
                Map<String, RksLayout.Cell> turned = world(f, cx, cy, cz, null, m);
                List<RksModule> wrong = RksLayout.INSTANCE.missing(f, cx, cy, cz, (x, y, z) -> turned.get(key(x, y, z)));
                check(wrong.contains(m), label + ": " + m + " turned the wrong way is refused -> " + wrong);
                // Back from the module's first cell to the console's.
                int[] o = RksLayout.INSTANCE.offset(f, m.getOx(), m.getOy(), m.getOz(), new int[3]);
                int[] back = RksLayout.INSTANCE.consoleFrom(m, f, new int[3]);
                check(o[0] + back[0] == 0 && o[1] + back[1] == 0 && o[2] + back[2] == 0, label + ": " + m + " finds its console");
            }
            // The plan's layout, seen by the player in front: the chamber behind the console's middle,
            // the reactor behind it, the power module on the left, the analyser on the right.
            int[] front = DIR[f];
            int[] chamber = RksLayout.INSTANCE.offset(f, 1, 0, 1, new int[3]);
            int[] power = RksLayout.INSTANCE.offset(f, 0, 0, 1, new int[3]);
            int[] analyser = RksLayout.INSTANCE.offset(f, 2, 0, 1, new int[3]);
            // The player's right, standing in front and facing the machine: -front turned clockwise... i.e. ccw(front).
            int[] right = DIR[ccw(f)];
            int behind = -(chamber[0] - right[0]) * front[0] - (chamber[2] - right[1]) * front[1];
            check(behind == 1, label + ": the chamber one block behind the console's middle");
            int side = (analyser[0] - power[0]) * right[0] + (analyser[2] - power[2]) * right[1];
            check(side == 2, label + ": the power module on the left, the analyser on the right");
        }
    }

    // ---------------------------------------------------------------- the state machine

    private static void machine() {
        RksStateMachine m = new RksStateMachine();
        check(m.getState() == RksState.INCOMPLETE, "new machine incomplete");
        check(m.structure(true, 0, 60) == null && m.getState() == RksState.ASSEMBLING, "assembling for 3 s");
        check(m.tick(59, 1.0, false) == null, "still assembling at 59 ticks");
        check(m.tick(60, 1.0, false) == RksStateMachine.Event.ASSEMBLED && m.getState() == RksState.IDLE, "idle after the assembly");

        // A double click: the same revision twice, one start only.
        int seen = m.getRevision();
        boolean first = m.accept(seen) && m.startCraft(100, 240);
        boolean second = m.accept(seen) && m.startCraft(100, 240);
        check(first && !second && m.getState() == RksState.CRAFTING, "double click: one craft");
        check(!m.startCraft(101, 240), "no second craft while crafting");
        check(m.tick(339, 1.0, false) == null && m.tick(340, 1.0, false) == RksStateMachine.Event.CRAFT_DONE, "the craft ends after 240 ticks");
        m.finishCraft(340);
        check(m.getState() == RksState.IDLE, "idle after the craft");
        // A module taken away during a craft: cancelled, nothing taken (the console's side).
        m.startCraft(400, 240);
        check(m.structure(false, 410, 60) == RksStateMachine.Event.CANCELLED && m.getState() == RksState.INCOMPLETE, "dismantled while crafting: cancelled");
        m.structure(true, 420, 60);
        m.tick(480, 1.0, false);
        check(m.getState() == RksState.IDLE, "assembled again: idle");

        // The M2 protocol.
        check(!m.startExperiment(500, 6000), "no experiment before the analysis");
        check(m.startAnalysis(500, 80) && m.tick(580, 1.0, false) == RksStateMachine.Event.ANALYSED && m.getAnalysed(), "analysed after 4 s");
        int r = m.getRevision();
        check(m.accept(r) && m.startExperiment(600, 6000) && !(m.accept(r) && m.startExperiment(600, 6000)), "double click on the start: one experiment");
        check(m.getState() == RksState.GROWING && m.getState().getProtectsModules() && m.getState().getLocksSlots(), "growing, modules protected, slots locked");
        long now = 600;
        RksStateMachine.Event event = null;
        for (int i = 0; i < 5999; i++) event = m.tick(++now, 1.0, false);
        check(event == null && !m.getReady(), "not ready one tick early");
        check(m.tick(++now, 1.0, false) == RksStateMachine.Event.READY && m.getReady(), "ready after 300 s (6000 ticks)");
        check(m.tick(++now, 1.0, false) == RksStateMachine.Event.READY && m.getState() == RksState.GROWING, "waits at 100 % (no one near)");

        // Reloaded while growing: the growth is kept.
        RksStateMachine half = new RksStateMachine();
        half.structure(true, 0, 0);
        half.startAnalysis(0, 1);
        half.tick(1, 1.0, false);
        half.startExperiment(2, 6000);
        for (int i = 0; i < 1234; i++) half.tick(3 + i, 1.0, false);
        RksStateMachine reloaded = reload(half);
        check(Math.abs(reloaded.getGrowth() - 1234.0) < 1e-9 && reloaded.getState() == RksState.GROWING && reloaded.getExperiment(), "reloaded while growing: " + reloaded.getGrowth());
        // Taken apart while growing (creative): the experiment waits, then goes on.
        reloaded.structure(false, 5000, 60);
        check(reloaded.getState() == RksState.INCOMPLETE && reloaded.getExperiment(), "dismantled while growing: the experiment waits");
        reloaded.structure(true, 5010, 60);
        reloaded.tick(5070, 1.0, false);
        check(reloaded.getState() == RksState.GROWING && Math.abs(reloaded.getGrowth() - 1234.0) < 1.5, "assembled again: growing on");

        // The awakening: one spawn, whatever happens.
        check(m.awaken(now) && m.getState() == RksState.AWAKENING, "awakening");
        check(m.tick(++now, 1.0, true) == null, "nothing while the scene plays");
        RksStateMachine beforeSpawn = reload(m);
        check(m.markSpawned() && !m.markSpawned(), "Mewtwo appears once (a second call refused)");
        RksStateMachine afterSpawn = reload(m);
        check(m.tick(++now, 1.0, false) == RksStateMachine.Event.FINISHED && m.getState() == RksState.DAMAGED && m.getDamaged(), "after the scene: damaged");
        check(!m.getExperiment() && m.getSpawned(), "the experiment over, the flag kept");
        // Reloaded before the spawn (a restart during the scene): it appears at once, then the damage.
        check(beforeSpawn.tick(10, 1.0, false) == RksStateMachine.Event.SPAWN, "reloaded before the spawn: spawns now");
        check(beforeSpawn.markSpawned() && beforeSpawn.tick(11, 1.0, false) == RksStateMachine.Event.FINISHED, "then damaged");
        // Reloaded after the spawn: never a second one.
        check(afterSpawn.getSpawned() && afterSpawn.tick(10, 1.0, false) == RksStateMachine.Event.FINISHED && !afterSpawn.markSpawned(), "reloaded after the spawn: no second Mewtwo");
        // Dismantled during the awakening (creative): the scene ends first.
        RksStateMachine awake = reload(beforeSpawn);
        RksStateMachine scene = new RksStateMachine();
        scene.structure(true, 0, 0);
        scene.startAnalysis(0, 1);
        scene.tick(1, 1.0, false);
        scene.startExperiment(2, 10);
        for (int i = 0; i < 10; i++) scene.tick(3 + i, 1.0, false);
        scene.awaken(20);
        scene.structure(false, 21, 60);
        check(scene.getState() == RksState.AWAKENING, "dismantled while awakening: the scene goes on");
        scene.markSpawned();
        scene.tick(22, 1.0, false);
        check(scene.getState() == RksState.INCOMPLETE && scene.getDamaged(), "then incomplete and damaged");

        // Damaged: the menu is blocked; the repair.
        check(!m.startCraft(now, 240) && !m.startAnalysis(now, 80), "damaged: nothing starts");
        check(m.startRepair(now, 80) && m.getState() == RksState.REPAIRING && m.getState().getProtectsModules(), "repairing (4 s)");
        check(m.tick(now + 79, 1.0, false) == null && m.tick(now + 80, 1.0, false) == RksStateMachine.Event.REPAIRED && m.getState() == RksState.IDLE && !m.getDamaged(), "repaired: idle");
        // A damaged machine taken apart and assembled again stays damaged (the chamber keeps it).
        RksStateMachine kept = new RksStateMachine();
        kept.setDamaged(true);
        kept.structure(true, 0, 0);
        check(kept.getState() == RksState.DAMAGED, "assembled with a damaged chamber: damaged");
        // reset: nothing given back nor made.
        m.startAnalysis(now + 100, 1);
        m.tick(now + 101, 1.0, false);
        m.startExperiment(now + 102, 6000);
        m.reset(now + 103);
        check(m.getState() == RksState.IDLE && !m.getExperiment() && !m.getAnalysed(), "reset: idle, no experiment");
        check(!awake.getExperiment() || awake.getState() != RksState.IDLE, "a reloaded copy is independent");
        // The world clock (D10's option): the time spent unloaded counts.
        RksStateMachine clock = new RksStateMachine();
        clock.structure(true, 0, 0);
        clock.startAnalysis(0, 1);
        clock.tick(1, 1.0, false);
        clock.startExperiment(2, 6000);
        clock.tick(3, 4000.0, false);
        check(Math.abs(clock.getProgress() - 4000.0 / 6000.0) < 1e-9, "world clock: a long absence counts");
        clock.setSpeed(10.0);
        clock.tick(4, 200.0, false);
        check(clock.getReady(), "speed x10 (test command)");
    }

    private static RksStateMachine reload(RksStateMachine m) {
        Map<String, Object> saved = new LinkedHashMap<>();
        m.save(saved);
        RksStateMachine copy = new RksStateMachine();
        copy.load(saved);
        return copy;
    }

    // ---------------------------------------------------------------- the points (every Psychic species)

    private static void points(String jarPath) throws Exception {
        List<String> excludedLabels = Arrays.asList("legendary", "mythical", "ultra_beast", "paradox");
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("abra", 1);
        expected.put("kadabra", 2);
        expected.put("alakazam", 3);
        expected.put("wynaut", 1);
        expected.put("wobbuffet", 3);
        expected.put("ralts", 1);
        expected.put("kirlia", 2);
        expected.put("gardevoir", 3);
        expected.put("gallade", 3);
        expected.put("natu", 1);
        expected.put("xatu", 3);
        expected.put("espeon", 3);
        expected.put("slowpoke", 1);
        expected.put("slowbro", 3);
        expected.put("unown", 2);
        expected.put("lunatone", 2);
        int species = 0, psychic = 0, eligible = 0, excluded = 0;
        int[] byPoints = new int[4];
        Map<String, Integer> found = new HashMap<>();
        List<String> outOfRange = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jarPath)) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                String name = entry.getName();
                if (!name.startsWith("data/cobblemon/species/") || !name.endsWith(".json")) continue;
                String text;
                try (InputStream in = zip.getInputStream(entry)) {
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                Object parsed;
                try {
                    parsed = new Json(text).value();
                } catch (RuntimeException ex) {
                    check(false, "species file readable: " + name);
                    continue;
                }
                if (!(parsed instanceof Map)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> s = (Map<String, Object>) parsed;
                species++;
                String id = name.substring(name.lastIndexOf('/') + 1, name.length() - 5);
                List<String> labels = strings(s.get("labels"));
                boolean speciesPre = s.get("preEvolution") instanceof String && !((String) s.get("preEvolution")).isEmpty();
                boolean speciesEvo = s.get("evolutions") instanceof List && !((List<?>) s.get("evolutions")).isEmpty();
                // The base form, then the forms with types of their own.
                List<Map<String, Object>> forms = new ArrayList<>();
                forms.add(s);
                if (s.get("forms") instanceof List) for (Object f : (List<?>) s.get("forms")) if (f instanceof Map) {
                    @SuppressWarnings("unchecked") Map<String, Object> form = (Map<String, Object>) f;
                    if (form.containsKey("primaryType") || form.containsKey("secondaryType")) forms.add(form);
                }
                for (Map<String, Object> form : forms) {
                    String primary = String.valueOf(form.getOrDefault("primaryType", s.get("primaryType")));
                    String secondary = String.valueOf(form.getOrDefault("secondaryType", form == s ? s.get("secondaryType") : (form.containsKey("primaryType") ? null : s.get("secondaryType"))));
                    boolean isPsychic = "psychic".equalsIgnoreCase(primary) || "psychic".equalsIgnoreCase(secondary);
                    if (!isPsychic) continue;
                    psychic++;
                    List<String> formLabels = new ArrayList<>(labels);
                    formLabels.addAll(strings(form.get("labels")));
                    boolean ok = RksPoints.INSTANCE.eligible(true, id, formLabels, excludedLabels, Collections.emptyList(), Collections.emptyList());
                    if (!ok) {
                        excluded++;
                        continue;
                    }
                    eligible++;
                    boolean pre = form.get("preEvolution") instanceof String ? !((String) form.get("preEvolution")).isEmpty() : speciesPre;
                    boolean evo = form.get("evolutions") instanceof List ? !((List<?>) form.get("evolutions")).isEmpty() : speciesEvo;
                    int p = RksPoints.INSTANCE.points(RksPoints.INSTANCE.stage(pre, evo), 1, 2, 3, 2, null);
                    if (p < 1 || p > 3) outOfRange.add(id);
                    else byPoints[p]++;
                    if (form == s) found.put(id, p);
                }
            }
        }
        check(species > 900, "Cobblemon species read: " + species);
        check(psychic > 100, "Psychic species and forms: " + psychic);
        check(outOfRange.isEmpty(), "every point from 1 to 3 " + outOfRange);
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            Integer p = found.get(e.getKey());
            check(p != null && p.intValue() == e.getValue(), e.getKey() + ": " + p + " (expected " + e.getValue() + ")");
        }
        check(!found.containsKey("mewtwo") && !found.containsKey("mew") && !found.containsKey("lunala"), "legendary and mythical Psychic species excluded");
        // The configuration's override.
        check(RksPoints.INSTANCE.points(RksPoints.Stage.FINAL, 1, 2, 3, 2, 7) == 7, "a species' override wins");
        int[] credit = RksPoints.INSTANCE.credit(98, 100, 3);
        check(credit[0] == 2 && credit[1] == 1, "capped reserve: +2, 1 lost");
        System.out.printf("points: %d species, %d Psychic species and forms, %d eligible (%d excluded); 1 pt: %d, 2 pts: %d, 3 pts: %d%n",
            species, psychic, eligible, excluded, byPoints[1], byPoints[2], byPoints[3]);
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List) for (Object x : (List<?>) o) if (x instanceof String) out.add(((String) x).toLowerCase(Locale.ROOT));
        return out;
    }

    /** A minimal JSON reader (objects, arrays, strings with escapes, numbers, booleans, null). */
    static class Json {
        final String s;
        int i = 0;

        Json(String s) {
            this.s = s;
        }

        void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c)) i++;
                else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') { while (i < s.length() && s.charAt(i) != '\n') i++; }
                else break;
            }
        }

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
                    ws();
                    i++;
                    m.put(k, value());
                    ws();
                    char c = s.charAt(i++);
                    if (c == '}') return m;
                    ws();
                    if (s.charAt(i) == '}') { i++; return m; }
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
                    char c = s.charAt(i++);
                    if (c == ']') return l;
                    ws();
                    if (s.charAt(i) == ']') { i++; return l; }
                }
            }
            if (ch == '"') {
                StringBuilder b = new StringBuilder();
                i++;
                while (s.charAt(i) != '"') {
                    char c = s.charAt(i++);
                    if (c == '\\') {
                        char n = s.charAt(i++);
                        if (n == 'u') { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        else b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
                    } else b.append(c);
                }
                i++;
                return b.toString();
            }
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int j = i;
            while (j < s.length() && "+-0123456789.eE".indexOf(s.charAt(j)) >= 0) j++;
            double d = Double.parseDouble(s.substring(i, j));
            i = j;
            return d;
        }
    }
}
