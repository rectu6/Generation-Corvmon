import generations.gg.generations.core.generationscore.common.world.mew.MewConfig;
import generations.gg.generations.core.generationscore.common.world.mew.MewMath;
import generations.gg.generations.core.generationscore.common.world.mew.MewRecord;
import generations.gg.generations.core.generationscore.common.world.mew.MewState;
import generations.gg.generations.core.generationscore.common.world.mew.MewStateMachine;
import generations.gg.generations.core.generationscore.common.world.mew.MewZone;
import generations.gg.generations.core.generationscore.common.world.mew.MewZoneSet;

import java.util.*;
import java.util.function.DoubleSupplier;

/**
 * Off-game checks of the Mew hunt (MEW-PLAN.md § 16, build 59): the scale (exact and Monte-Carlo,
 * with the table really written in MewConfig), the guarantee, the schedule, the zones and biomes,
 * the away-from-keyboard rule, the progress shared between islands, the state machine of this
 * step (draws, unlock once, reset, hand-set counter, reschedule, save and load).
 *
 * Build the mod first (common/build/classes/kotlin/main), then:
 *   javac -cp <classes>;<kotlin-stdlib>;<gson> -d <out> tools/MewBench.java
 *   java -cp <out>;<classes>;<kotlin-stdlib>;<gson> MewBench
 */
public class MewBench {
    private static int passed = 0;
    private static int failed = 0;

    private static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    private static final long H = MewMath.TICKS_PER_HOUR;
    private static final DoubleSupplier NEVER = () -> 0.999999;
    private static final DoubleSupplier ALWAYS = () -> 0.0;

    public static void main(String[] args) {
        chanceTable();
        schedule();
        scale();
        guarantee();
        zones();
        afk();
        sharedIslands();
        machine();
        saveLoad();
        config();
        spread();
        transitions();
        manifestations();
        game();
        shows();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static MewStateMachine.Rules defaults() {
        return new MewConfig().getHunt().rules();
    }

    // ---------------------------------------------------------------- scale

    private static void chanceTable() {
        double[] h = {12, 20, 30, 40, 48};
        double[] p = {1.2, 2.4, 7.8, 17.8, 30.0};
        check(near(MewMath.chance(h, p, 12), 0.012, 1e-12), "chance at the first point");
        check(near(MewMath.chance(h, p, 16), 0.018, 1e-12), "chance half way 12-20");
        check(near(MewMath.chance(h, p, 35), 0.128, 1e-12), "chance half way 30-40");
        check(near(MewMath.chance(h, p, 48), 0.30, 1e-12), "chance at the last point");
        check(near(MewMath.chance(h, p, 5), 0.012, 1e-12), "before the table: first value");
        check(near(MewMath.chance(h, p, 60), 0.30, 1e-12), "after the table: last value");
        check(MewMath.chance(new double[0], new double[0], 20) == 0.0, "empty table: no chance");
        check(near(MewMath.chance(new double[]{10, 10}, new double[]{5, 50}, 10), 0.05, 1e-12), "two points at the same hour");
        check(MewMath.chance(new double[]{0}, new double[]{250}, 1) == 1.0, "chance clamped to 1");
    }

    private static List<Double> drawHours(MewStateMachine.Rules r) {
        List<Double> out = new ArrayList<>();
        long t = MewMath.nextDraw(0, r.getFirstDrawHours(), r.getDrawEveryHours(), r.getGuaranteeHours(), false);
        long guarantee = r.getGuaranteeTicks();
        for (int i = 0; i < 10_000; i++) {
            out.add(MewMath.hours(t));
            if (t >= guarantee) break;
            t = MewMath.nextDraw(t, r.getFirstDrawHours(), r.getDrawEveryHours(), r.getGuaranteeHours(), true);
        }
        return out;
    }

    private static void schedule() {
        List<Double> hours = drawHours(defaults());
        check(hours.size() == 20, "19 draws then the guarantee (" + hours.size() + ")");
        check(hours.get(0) == 12.0 && hours.get(1) == 14.0 && hours.get(18) == 48.0 && hours.get(19) == 50.0, "draws at 12, 14 ... 48, guarantee at 50: " + hours);
        check(MewMath.nextDraw(13 * H, 12, 2, 50, false) == 14 * H, "next draw after 13 h: 14 h");
        check(MewMath.nextDraw(14 * H, 12, 2, 50, false) == 14 * H, "at 14 h, inclusive: 14 h");
        check(MewMath.nextDraw(14 * H, 12, 2, 50, true) == 16 * H, "at 14 h, exclusive: 16 h");
        check(MewMath.nextDraw(49 * H, 12, 2, 50, false) == 50 * H, "after 48 h: the guarantee");
        check(MewMath.nextDraw(60 * H, 12, 2, 50, false) == 50 * H, "beyond the guarantee: due at once");
        check(MewMath.nextDraw(0, 60, 2, 50, false) == 50 * H, "first draw after the guarantee: the guarantee");
        check(MewMath.nextDraw(0, 12.5, 1.5, 50, false) == Math.round(12.5 * H), "fractional hours");
        // The machine follows the same schedule (draws never succeed): 30 s checks.
        MewStateMachine m = new MewStateMachine(defaults());
        MewRecord r = m.create();
        List<Double> seen = new ArrayList<>();
        while (r.getState() == MewState.HUNTING) {
            MewStateMachine.Outcome o = m.addActive(r, 600, NEVER);
            for (MewStateMachine.Draw d : o.getDraws()) seen.add(d.getHours());
        }
        check(seen.equals(hours), "machine with 30 s checks draws on the schedule: " + seen);
        check(r.getDraws() == 20 && MewMath.hours(r.getUnlockedAtTicks()) == 50.0, "unlocked by the guarantee at 50 h");
    }

    /** Mean, median, share reaching the guarantee, from the schedule and chances. */
    private static double[] exact(MewStateMachine.Rules r) {
        double s = 1, mean = 0, acc = 0, median = -1;
        for (double h : drawHours(r)) {
            double p = h >= r.getGuaranteeHours() ? 1.0 : r.chance(h);
            double q = s * p;
            mean += q * h;
            acc += q;
            if (median < 0 && acc >= 0.5) median = h;
            if (h >= r.getGuaranteeHours()) return new double[]{mean, median, s};
            s *= 1 - p;
        }
        return new double[]{mean, median, 0};
    }

    private static void scale() {
        MewStateMachine.Rules rules = defaults();
        double[] e = exact(rules);
        System.out.printf(Locale.ROOT, "scale B (config): mean %.2f h, median %.0f h, guarantee %.2f %%%n", e[0], e[1], e[2] * 100);
        check(e[0] >= 35.0 && e[0] <= 36.0, "mean between 35 and 36 h (" + e[0] + ")");
        check(e[1] == 36.0, "median 36 h (" + e[1] + ")");
        check(e[2] >= 0.09 && e[2] <= 0.11, "about 10 % at the guarantee (" + e[2] + ")");
        // The plan's points as written (2.6 % at 20 h, 8.4 % at 30 h, 18.6 % at 40 h): 35.0 h with straight lines.
        MewStateMachine.Rules plan = new MewStateMachine.Rules(12, 2, 50, new double[]{12, 20, 30, 40, 48}, new double[]{1.2, 2.6, 8.4, 18.6, 30});
        double[] p = exact(plan);
        System.out.printf(Locale.ROOT, "plan's points, linear: mean %.2f h, median %.0f h, guarantee %.2f %%%n", p[0], p[1], p[2] * 100);
        // Scale A of the plan, for the record: 38.1 h, 32.5 %.
        MewStateMachine.Rules a = new MewStateMachine.Rules(12, 2, 50, new double[]{12, 48}, new double[]{1.2, 10.2});
        double[] ea = exact(a);
        check(near(ea[0], 38.1, 0.05) && near(ea[2], 0.325, 0.002), "scale A as in the plan (" + ea[0] + ", " + ea[2] + ")");

        // Monte-Carlo through the state machine, 1 h of presence at a time.
        MewStateMachine m = new MewStateMachine(rules);
        Random random = new Random(151);
        DoubleSupplier roll = random::nextDouble;
        int n = 400_000, pity = 0;
        double total = 0;
        for (int i = 0; i < n; i++) {
            MewRecord r = m.create();
            MewStateMachine.Draw last = null;
            while (r.getState() == MewState.HUNTING) {
                MewStateMachine.Outcome o = m.addActive(r, H, roll);
                if (o.getUnlocked()) last = o.getDraws().get(o.getDraws().size() - 1);
            }
            total += last.getHours();
            if (last.getGuaranteed()) pity++;
        }
        double mcMean = total / n, mcPity = pity / (double) n;
        System.out.printf(Locale.ROOT, "Monte-Carlo %d hunts: mean %.2f h, guarantee %.2f %%%n", n, mcMean, mcPity * 100);
        check(near(mcMean, e[0], 0.08), "Monte-Carlo mean matches the exact one");
        check(near(mcPity, e[2], 0.003), "Monte-Carlo guarantee share matches the exact one");

        // 30 s checks and 1 h steps unlock at the same draw for the same random numbers.
        boolean same = true;
        for (int i = 0; i < 2000 && same; i++) {
            Random r1 = new Random(i), r2 = new Random(i);
            MewRecord a1 = m.create(), a2 = m.create();
            while (a1.getState() == MewState.HUNTING) m.addActive(a1, 600, r1::nextDouble);
            while (a2.getState() == MewState.HUNTING) m.addActive(a2, H, r2::nextDouble);
            same = a1.getDraws() == a2.getDraws();
        }
        check(same, "30 s checks and 1 h steps give the same draws");
    }

    private static void guarantee() {
        MewStateMachine m = new MewStateMachine(defaults());
        MewRecord r = m.create();
        MewStateMachine.Outcome last = null;
        while (r.getState() == MewState.HUNTING) last = m.addActive(r, 1200, NEVER);
        MewStateMachine.Draw d = last.getDraws().get(last.getDraws().size() - 1);
        check(d.getGuaranteed() && d.getSuccess() && d.getHours() == 50.0, "the guarantee at 50 h always succeeds");
        check(r.getActiveTicks() >= 50 * H && r.getActiveTicks() < 50 * H + 1200, "counter stops at the guarantee");
        // A big credit at once (hunt add style through addActive) makes every draw due, in order.
        MewRecord big = m.create();
        MewStateMachine.Outcome o = m.addActive(big, 60 * H, NEVER);
        check(o.getDraws().size() == 20 && o.getUnlocked(), "60 h at once: 19 draws then the guarantee");
    }

    // ---------------------------------------------------------------- zones

    private static MewZone box(String id, double minX, double minZ, double maxX, double maxZ) {
        MewZone z = new MewZone();
        z.setId(id);
        z.setShape("box");
        z.setMinX(minX);
        z.setMinZ(minZ);
        z.setMaxX(maxX);
        z.setMaxZ(maxZ);
        return z;
    }

    private static MewZone.BiomeTest biome(String id, String... tags) {
        Set<String> in = new HashSet<>(Arrays.asList(tags));
        return entry -> entry.startsWith("#") ? in.contains(entry.substring(1)) : entry.equals(id);
    }

    private static void zones() {
        MewZone b = box("ile_ouest", -10, -5, 10, 5);
        check(b.normalize().isEmpty(), "box is valid");
        check(b.contains(0, 64, 0), "box: centre inside");
        check(b.contains(10.9, 64, 0) && b.contains(-10.0, 64, 0), "box: both end blocks included");
        check(!b.contains(11.0, 64, 0) && !b.contains(-10.01, 64, 0) && !b.contains(0, 64, 6.0), "box: outside");
        check(b.getCenterX() == null && b.getRadius() == null, "box: circle fields removed");
        MewZone y = box("haut", -10, -10, 10, 10);
        y.setMinY(60.0);
        y.setMaxY(70.0);
        y.normalize();
        check(!y.contains(0, 59.9, 0) && y.contains(0, 70.9, 0) && !y.contains(0, 71.0, 0), "box: height limits");
        MewZone rev = box("rev", 10, 5, -10, -5);
        rev.normalize();
        check(rev.getMinX() == -10.0 && rev.getMaxX() == 10.0 && rev.contains(0, 0, 0), "box: reversed ends swapped");

        MewZone c = MewZone.circle("ile_nord", "minecraft:overworld", 100, 100, 50);
        check(c.normalize().isEmpty(), "circle is valid");
        check(c.contains(149.9, 64, 100) && c.contains(135, 64, 135), "circle: inside");
        check(!c.contains(135.4, 64, 135.4) && !c.contains(151, 64, 100), "circle: outside");
        check(c.getMinX() == null, "circle: box fields removed");

        MewZone bio = box("bio", -10, -10, 10, 10);
        bio.setBiomes(new ArrayList<>(Arrays.asList("#minecraft:is_forest", "jungle", " ", "jungle")));
        bio.normalize();
        check(bio.getBiomes().equals(Arrays.asList("#minecraft:is_forest", "minecraft:jungle")), "biomes: namespace added, blanks and doubles removed: " + bio.getBiomes());
        check(bio.biomeAllowed(biome("minecraft:birch_forest", "minecraft:is_forest")), "biome in an allowed tag");
        check(bio.biomeAllowed(biome("minecraft:jungle")), "biome allowed by its id");
        check(!bio.biomeAllowed(biome("minecraft:desert", "minecraft:is_hot")), "wrong biome refused");
        check(b.biomeAllowed(biome("minecraft:desert")), "empty list: every biome");

        MewZone typo = MewZone.circle("typo", "minecraft:overworld", 0, 0, 10);
        typo.setShape("cirle");
        check(typo.normalize().equals("shape") && !typo.contains(0, 0, 0) && typo.getCenterX() == 0.0, "bad shape: ignored, kept untouched");
        MewZone noRadius = MewZone.circle("r", "minecraft:overworld", 0, 0, 10);
        noRadius.setRadius(null);
        check(noRadius.normalize().equals("radius"), "circle without radius refused");
        MewZone badId = box("Ile Nord", 0, 0, 1, 1);
        check(badId.normalize().equals("id"), "id with capitals and a space refused");
        MewZone noNs = box("nons", 0, 0, 1, 1);
        noNs.setDimension("the_nether");
        noNs.normalize();
        check(noNs.getDimension().equals("minecraft:the_nether"), "dimension namespace added");

        MewZone off = box("off", -100, -100, 100, 100);
        off.setEnabled(false);
        off.normalize();
        MewZone nether = box("nether", -100, -100, 100, 100);
        nether.setDimension("minecraft:the_nether");
        nether.normalize();
        MewZone forest = box("forest", -20, -20, 20, 20);
        forest.setBiomes(new ArrayList<>(List.of("#minecraft:is_forest")));
        forest.normalize();
        MewZone all = box("all", 0, 0, 30, 30);
        all.normalize();
        MewZoneSet set = new MewZoneSet(List.of(off, nether, forest, all, typo));
        check(set.getSize() == 3, "set: disabled and broken zones left out (" + set.getSize() + ")");
        check(set.find("minecraft:overworld", -50, 64, -50, biome("minecraft:plains")) == null, "set: disabled zone ignored");
        check(set.find("minecraft:overworld", 5, 64, 5, biome("minecraft:plains")) == all, "set: first zone whose biome is allowed");
        check(set.find("minecraft:overworld", -5, 64, -5, biome("minecraft:plains")) == null, "set: inside a zone, wrong biome");
        check(set.find("minecraft:overworld", -5, 64, -5, biome("minecraft:forest", "minecraft:is_forest")) == forest, "set: inside, right biome");
        check(set.find("minecraft:the_nether", 5, 64, 5, biome("minecraft:plains")) == nether, "set: by dimension");
        check(set.find("minecraft:the_end", 5, 64, 5, biome("minecraft:plains")) == null, "set: no zone in that dimension");
        check(set.containing("minecraft:overworld", 5, 64, 5).size() == 2, "set: containing lists overlapping zones");
    }

    // ---------------------------------------------------------------- away from keyboard

    private static final int AFK = 300;
    private static final double STILL = 15;
    private static final long MIN = 60_000;

    /** A player sampled every 30 s for so many minutes; returns the rule's status at the end. */
    private interface Step {
        void at(int i, double[] pos, float[] look, long[] action, long[] battle);
    }

    private static MewMath.AfkStatus run(int minutes, boolean exempt, Step step) {
        double[] pos = {0, 64, 0};
        float[] look = {0, 0};
        long[] action = {0};
        long[] battle = {0};
        MewMath.Activity a = new MewMath.Activity(0, pos[0], pos[1], pos[2], look[0], look[1], 0);
        long now = 0;
        for (int i = 1; i <= minutes * 2; i++) {
            now = i * 30_000L;
            step.at(i, pos, look, action, battle);
            if (action[0] < 0) action[0] = now;
            a.observe(now, pos[0], pos[1], pos[2], look[0], look[1], battle[0], 3.0);
        }
        return MewMath.afk(now, action[0], a, exempt, AFK, STILL);
    }

    private static void afk() {
        MewMath.AfkStatus walk = run(60, false, (i, p, l, a, b) -> p[0] += 2);
        check(walk == MewMath.AfkStatus.ACTIVE, "walking without any click counts (the idle timer is not reset by walking) " + walk);
        check(run(10, false, (i, p, l, a, b) -> l[0] += 10) == MewMath.AfkStatus.ACTIVE, "looking around 10 min without moving: active");
        check(run(16, false, (i, p, l, a, b) -> l[0] += 10) == MewMath.AfkStatus.STILL, "looking around 16 min without moving: still");
        check(run(4, false, (i, p, l, a, b) -> { }) == MewMath.AfkStatus.ACTIVE, "standing 4 min: active");
        check(run(6, false, (i, p, l, a, b) -> { }) == MewMath.AfkStatus.IDLE, "nothing for 6 min: idle");
        check(run(20, false, (i, p, l, a, b) -> { if (i % 2 == 0) a[0] = -1; }) == MewMath.AfkStatus.STILL, "clicking 20 min in place: still");
        check(run(120, true, (i, p, l, a, b) -> { if (i % 2 == 0) a[0] = -1; }) == MewMath.AfkStatus.ACTIVE, "fishing 2 h in place (reeling in): active");
        check(run(20, true, (i, p, l, a, b) -> { }) == MewMath.AfkStatus.IDLE, "fishing exemption does not spare rule a");
        check(run(30, false, (i, p, l, a, b) -> { p[0] = (i % 2 == 0) ? 2 : 0; l[0] += 1; }) == MewMath.AfkStatus.STILL, "anti-AFK back and forth over 2 blocks: still");
        check(run(30, false, (i, p, l, a, b) -> { p[0] = (i % 20 < 10) ? 0 : 4; }) == MewMath.AfkStatus.ACTIVE, "walking 4 blocks every 5 min: active");
        check(run(20, true, (i, p, l, a, b) -> b[0] = 1000 + i) == MewMath.AfkStatus.ACTIVE, "a battle whose turns go on: active");
        check(run(20, true, (i, p, l, a, b) -> b[0] = 1001) == MewMath.AfkStatus.IDLE, "a battle waiting on its player: idle");
        check(run(6, false, (i, p, l, a, b) -> l[0] += 360f) == MewMath.AfkStatus.IDLE, "a full turn of 360 degrees is no change of view");
        check(run(6, false, (i, p, l, a, b) -> { if (i == 11) a[0] = -1; }) == MewMath.AfkStatus.ACTIVE, "an action 30 s ago: active");
        check(run(25, false, (i, p, l, a, b) -> { if (i <= 40) p[2] += 1; }) == MewMath.AfkStatus.IDLE, "walking 20 min then standing 5 min: idle");
        // Riding: the mount moves the player.
        check(run(60, true, (i, p, l, a, b) -> p[0] += 8) == MewMath.AfkStatus.ACTIVE, "riding: active");

        check(MewMath.credit(600, 30) == 600 && MewMath.credit(100_000, 30) == 1200 && MewMath.credit(-5, 30) == 0, "credit capped at twice the interval");
    }

    // ---------------------------------------------------------------- islands

    private static void sharedIslands() {
        MewZone a = box("ile_a", 0, 0, 100, 100);
        a.normalize();
        MewZone b = MewZone.circle("ile_b", "minecraft:overworld", 5000, 5000, 80);
        b.normalize();
        MewZoneSet set = new MewZoneSet(List.of(a, b));
        MewStateMachine m = new MewStateMachine(defaults());
        Map<String, MewRecord> records = new HashMap<>();
        double[][] path = {{50, 50}, {9999, 9999}, {5000, 5010}, {50, 50}};
        double[] hours = {1, 1, 2, 0.5};
        for (int leg = 0; leg < path.length; leg++) {
            for (int c = 0; c < hours[leg] * 120; c++) {
                MewZone z = set.find("minecraft:overworld", path[leg][0], 64, path[leg][1], biome("minecraft:forest"));
                if (z == null) continue;
                m.addActive(records.computeIfAbsent("player", k -> m.create()), 600, NEVER);
            }
        }
        check(records.size() == 1 && records.get("player").getActiveTicks() == (long) (3.5 * H), "one counter over two islands: 3.5 h (" + MewMath.hours(records.get("player").getActiveTicks()) + ")");
        MewRecord r = m.create();
        for (int c = 0; c < 6 * 120; c++) m.addActive(r, 600, NEVER);
        for (int c = 0; c < 6 * 120; c++) m.addActive(r, 600, NEVER);
        check(r.getDraws() == 1 && r.getNextDrawTicks() == 14 * H, "6 h on one island + 6 h on another: the 12 h draw is made");
    }

    // ---------------------------------------------------------------- state machine

    private static void machine() {
        check(MewState.values().length == 6 && String.join(",", Arrays.stream(MewState.values()).map(Enum::name).toList()).equals("HUNTING,UNLOCKED,CHALLENGE,COOLDOWN,WON,RELEASED"), "the plan's six states");
        MewStateMachine m = new MewStateMachine(defaults());
        MewRecord r = m.create();
        check(r.getState() == MewState.HUNTING && r.getNextDrawTicks() == 12 * H, "new hunt: first draw at 12 h");
        MewStateMachine.Outcome before = m.addActive(r, 11 * H, ALWAYS);
        check(before.getDraws().isEmpty() && !before.getUnlocked(), "no draw before 12 h");
        MewStateMachine.Outcome o = m.addActive(r, H, ALWAYS);
        check(o.getUnlocked() && r.getState() == MewState.UNLOCKED && r.getUnlockedAtTicks() == 12 * H && r.getDraws() == 1, "a successful draw unlocks: HUNTING -> UNLOCKED");
        check(!m.unlock(r), "unlock twice: refused");
        long ticks = r.getActiveTicks();
        MewStateMachine.Outcome again = m.addActive(r, H, ALWAYS);
        check(!again.getUnlocked() && again.getDraws().isEmpty() && r.getActiveTicks() == ticks && r.getDraws() == 1, "after the unlock: no time added, no draw");
        check(m.forceDraw(r, ALWAYS) == null, "forced draw refused when unlocked");
        check(m.addActive(r, 0, ALWAYS) == MewStateMachine.Outcome.Companion.getNONE(), "nothing for no time");

        r.setCompleted(2);
        r.setAttempts(3);
        r.setMewUuid("abc");
        m.reset(r);
        check(r.getState() == MewState.HUNTING && r.getActiveTicks() == 0 && r.getNextDrawTicks() == 12 * H && r.getDraws() == 0
            && r.getAttempts() == 0 && r.getMewUuid().isEmpty() && r.getUnlockedAtTicks() == -1 && r.getCompleted() == 2, "reset: a new hunt, the completed count kept");

        m.setActive(r, 30 * H);
        check(r.getNextDrawTicks() == 30 * H, "hunt set 30 h: next draw at 30 h");
        m.setActive(r, 31 * H);
        check(r.getNextDrawTicks() == 32 * H, "hunt set 31 h: next draw at 32 h");
        check(r.getState() == MewState.HUNTING && r.getDraws() == 0, "hunt set makes no draw");
        m.setActive(r, -5);
        check(r.getActiveTicks() == 0 && r.getNextDrawTicks() == 12 * H, "negative counter: 0");
        m.setActive(r, 55 * H);
        check(r.getNextDrawTicks() == 50 * H, "hunt set beyond the guarantee: due");
        MewStateMachine.Outcome g = m.addActive(r, 1, NEVER);
        check(g.getUnlocked() && g.getDraws().get(0).getGuaranteed(), "next check: the guarantee unlocks");

        MewRecord f = m.create();
        m.setActive(f, 20 * H);
        long next = f.getNextDrawTicks();
        MewStateMachine.Draw d = m.forceDraw(f, () -> 0.5);
        check(d != null && !d.getSuccess() && near(d.getChance(), 0.024, 1e-12) && f.getState() == MewState.HUNTING && f.getDraws() == 1 && f.getNextDrawTicks() == next,
            "forced draw at 20 h: 2.4 %, missed, schedule unchanged");
        MewStateMachine.Draw ok = m.forceDraw(f, () -> 0.01);
        check(ok != null && ok.getSuccess() && f.getState() == MewState.UNLOCKED, "forced draw that succeeds unlocks");
        MewRecord low = m.create();
        MewStateMachine.Draw early = m.forceDraw(low, () -> 0.011);
        check(early != null && early.getSuccess() && near(early.getChance(), 0.012, 1e-12), "forced draw before 12 h uses the first chance");

        // Reschedule after a reload.
        MewRecord s = m.create();
        m.addActive(s, 13 * H, NEVER);
        check(s.getNextDrawTicks() == 14 * H, "after 13 h: next draw 14 h");
        m.reschedule(s);
        check(s.getNextDrawTicks() == 14 * H, "reschedule with the same rules: unchanged");
        MewStateMachine every3 = new MewStateMachine(new MewStateMachine.Rules(12, 3, 50, new double[]{12}, new double[]{1}));
        every3.reschedule(s);
        check(s.getNextDrawTicks() == 15 * H, "reschedule with draws every 3 h: 15 h");
        MewRecord exact14 = m.create();
        m.addActive(exact14, 14 * H, NEVER);
        m.reschedule(exact14);
        check(exact14.getDraws() == 2 && exact14.getNextDrawTicks() == 16 * H, "reschedule right after the 14 h draw: no second 14 h draw");
        MewRecord fresh = m.create();
        m.reschedule(fresh);
        check(fresh.getNextDrawTicks() == 12 * H, "reschedule a new hunt: 12 h");
        MewRecord unlocked = m.create();
        m.unlock(unlocked);
        unlocked.setNextDrawTicks(777);
        m.reschedule(unlocked);
        check(unlocked.getNextDrawTicks() == 777, "reschedule leaves other states alone");
    }

    private static void saveLoad() {
        for (MewState state : MewState.values()) {
            MewRecord r = new MewRecord();
            r.setState(state);
            r.setActiveTicks(123_456_789L);
            r.setNextDrawTicks(987_654L);
            r.setDraws(7);
            r.setNextManifestTicks(55L);
            r.setCooldownTicks(54_000L);
            r.setAttempts(4);
            r.setMewUuid("0a1b2c3d-0000-4000-8000-000000000151");
            r.setReleasedAtMs(1_763_251_200_000L);
            r.setUnlockedAtTicks(3_000_000L);
            r.setCompleted(9);
            MewRecord l = MewRecord.load(new HashMap<>(r.save()));
            check(l.getState() == state && l.getActiveTicks() == 123_456_789L && l.getNextDrawTicks() == 987_654L && l.getDraws() == 7
                && l.getNextManifestTicks() == 55L && l.getCooldownTicks() == 54_000L && l.getAttempts() == 4
                && l.getMewUuid().equals("0a1b2c3d-0000-4000-8000-000000000151") && l.getReleasedAtMs() == 1_763_251_200_000L
                && l.getUnlockedAtTicks() == 3_000_000L && l.getCompleted() == 9, "save and load: " + state);
        }
        check(new MewRecord().save().size() == 11, "eleven saved values");
        Map<String, Object> odd = new HashMap<>();
        odd.put("state", "LOST_IN_TIME");
        odd.put("active", 42.0);
        odd.put("draws", 3L);
        MewRecord o = MewRecord.load(odd);
        check(o.getState() == MewState.HUNTING && o.getActiveTicks() == 42 && o.getDraws() == 3 && o.getNextDrawTicks() == -1 && o.getMewUuid().isEmpty(),
            "unknown state, other number types, missing keys");
        MewStateMachine m = new MewStateMachine(defaults());
        m.schedule(o);
        check(o.getNextDrawTicks() == 12 * H, "missing next draw: planned from the counter");
        MewRecord neg = MewRecord.load(Map.of("active", -100L, "attempts", -2));
        check(neg.getActiveTicks() == 0 && neg.getAttempts() == 0, "negative values: 0");
    }

    private static void config() {
        MewConfig c = new MewConfig();
        c.validate();
        List<MewConfig.ChancePoint> t = c.getHunt().getChanceTable();
        check(t.size() == 5 && t.get(1).getPercent() == 2.4 && t.get(2).getPercent() == 7.8 && t.get(3).getPercent() == 17.8, "default table: 1.2 / 2.4 / 7.8 / 17.8 / 30");
        check(c.getHunt().getCheckSeconds() == 30 && c.getAfk().getAfkSeconds() == 300 && c.getAfk().getMinMoveBlocks() == 3.0 && c.getAfk().getStillMinutes() == 15.0,
            "defaults of § 4: 30 s, 300 s, 3 blocks, 15 min");
        check(c.getRetry().getRetryMinutes() == 45 && c.getSpawn().getLevel() == 50 && c.getLostMew().equals("reset") && c.getReleaseTimeoutMinutes() == 60,
            "defaults of D6, D8, D11 and § 12");
        MewConfig bad = new MewConfig();
        bad.getHunt().setCheckSeconds(0);
        bad.getHunt().setDrawEveryHours(0);
        List<MewConfig.ChancePoint> table = new ArrayList<>();
        table.add(new MewConfig.ChancePoint(40, 20));
        table.add(new MewConfig.ChancePoint(Double.NaN, 5));
        table.add(new MewConfig.ChancePoint(10, 150));
        bad.getHunt().setChanceTable(table);
        bad.setLostMew("forget");
        bad.getRetry().setRetryMinutes(5000);
        bad.validate();
        check(bad.getHunt().getCheckSeconds() == 5 && bad.getHunt().getDrawEveryHours() == 0.05, "too small intervals raised");
        check(bad.getHunt().getChanceTable().size() == 2 && bad.getHunt().getChanceTable().get(0).getHours() == 10 && bad.getHunt().getChanceTable().get(0).getPercent() == 100,
            "table: NaN removed, sorted, percent capped");
        check(bad.getLostMew().equals("reset") && bad.getRetry().getRetryMinutes() == 600, "lostMew and retryMinutes fixed");
        MewConfig empty = new MewConfig();
        empty.getHunt().setChanceTable(new ArrayList<>());
        empty.validate();
        check(empty.getHunt().getChanceTable().size() == 5, "empty table: the default one");
        check(c.getManifestations().getTypes().keySet().equals(new LinkedHashSet<>(List.of("a", "b", "c", "d"))), "manifestations A to D declared");
    }

    // ---------------------------------------------------------------- after the unlock

    private static void transitions() {
        MewStateMachine m = new MewStateMachine(defaults());
        MewRecord r = m.create();
        check(!m.startChallenge(r), "no challenge while hunting");
        m.addActive(r, 12 * H, ALWAYS);
        int draws = r.getDraws();
        check(m.startChallenge(r) && r.getState() == MewState.CHALLENGE && r.getAttempts() == 1, "UNLOCKED -> CHALLENGE, first attempt");
        check(!m.startChallenge(r), "a challenge never starts twice");
        check(!m.win(new MewRecord()) && !m.release(r, "x", 1) && !m.caught(r), "no win, release or capture out of order");
        check(m.fail(r, 45 * 1200L) && r.getState() == MewState.COOLDOWN && r.getCooldownTicks() == 54_000L, "a failure: 45 active minutes to wait");
        check(!m.fail(r, 45 * 1200L), "a failure is counted once");
        check(!m.startChallenge(r), "no new challenge while waiting");
        // 20 active minutes, a disconnection (nothing counts offline), 25 more: Mew comes back.
        boolean ready = false;
        for (int c = 0; c < 40; c++) ready = m.addCooldown(r, 600);
        check(!ready && r.getCooldownTicks() == 30_000L, "20 active minutes: 25 left");
        for (int c = 0; c < 50; c++) ready = m.addCooldown(r, 600);
        check(ready && r.getCooldownTicks() == 0, "20 + 25 active minutes: Mew may come back");
        check(m.startChallenge(r) && r.getAttempts() == 2 && r.getDraws() == draws && r.getActiveTicks() == 12 * H, "the return: a second attempt, no new draw, the counter kept");
        check(!m.addCooldown(r, 600) && !m.setCooldown(r, 10), "no wait outside COOLDOWN");
        check(m.win(r) && r.getState() == MewState.WON && !m.win(r), "CHALLENGE -> WON, once");
        check(m.release(r, "mew-1", 1000L) && r.getState() == MewState.RELEASED && r.getMewUuid().equals("mew-1") && !m.release(r, "mew-2", 2000L) && r.getMewUuid().equals("mew-1"),
            "WON -> RELEASED once: a single Mew");
        check(m.caught(r) && r.getState() == MewState.HUNTING && r.getActiveTicks() == 0 && r.getAttempts() == 0 && r.getCompleted() == 1 && r.getNextDrawTicks() == 12 * H && !m.caught(r),
            "its capture: a new hunt from 0, one more completed, once");
        // A lost Mew: reset (D8) or retry.
        MewRecord lost = released(m);
        check(m.lost(lost, false, 54_000L) && lost.getState() == MewState.HUNTING && lost.getActiveTicks() == 0 && lost.getCompleted() == 0, "lost, reset: a new hunt, not completed");
        MewRecord again = released(m);
        check(m.lost(again, true, 54_000L) && again.getState() == MewState.COOLDOWN && again.getCooldownTicks() == 54_000L && again.getMewUuid().isEmpty(), "lost, retry: the wait then the challenge again");
        check(!m.lost(again, true, 54_000L), "lost once");
        MewRecord set = m.create();
        m.unlock(set);
        m.startChallenge(set);
        m.fail(set, 54_000L);
        check(m.setCooldown(set, 1200) && set.getCooldownTicks() == 1200, "cooldown set by hand in COOLDOWN");
    }

    private static MewRecord released(MewStateMachine m) {
        MewRecord r = m.create();
        m.unlock(r);
        m.startChallenge(r);
        m.win(r);
        m.release(r, "mew", 1L);
        return r;
    }

    private static void manifestations() {
        MewStateMachine m = new MewStateMachine(defaults());
        Random random = new Random(6);
        DoubleSupplier roll = random::nextDouble;
        MewRecord r = m.create();
        int shown = 0;
        long interval = 15 * 1200L;
        int checks = 1000 * 120;
        for (int c = 0; c < checks; c++) if (m.manifestation(r, 600, interval, 0.10, roll)) shown++;
        double expected = 1000 * 60 / 15.0 * 0.10;
        check(Math.abs(shown - expected) < 60, "1000 active hours: " + shown + " false appearances (about " + expected + ")");
        MewRecord first = m.create();
        m.manifestation(first, 0, interval, 0.1, () -> 0.5);
        check(first.getNextManifestTicks() == interval / 2, "the first slot is drawn inside an interval");
        MewRecord unlocked = m.create();
        m.unlock(unlocked);
        check(!m.manifestation(unlocked, interval * 2, interval, 1.0, () -> 0.0), "none once unlocked");
        MewRecord cooldown = m.create();
        m.unlock(cooldown);
        m.startChallenge(cooldown);
        m.fail(cooldown, 54_000L);
        boolean any = false;
        for (int c = 0; c < 200; c++) any |= m.manifestation(cooldown, 600, interval, 1.0, () -> 0.0);
        check(any, "some while waiting after a failure (D12)");
    }

    private static generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Rules rules() {
        return new generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Rules(new int[]{1, 2, 3}, 6 * 1200L, 400L, 5.0, 6.0, 130L, 60L, 800L, 4L, 3);
    }

    private static void game() {
        var E = generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.class;
        var s = new generations.gg.generations.core.generationscore.common.world.mew.MewGameSession(7, UUID.randomUUID(), false, rules(), 0L, false, 0);
        check(s.tick(129, 0, 64, 0, true) == null, "the opening plays (6.5 s)");
        check(s.tick(130, 0, 64, 0, true) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.NEED_ROUND, "then round 1 is asked for");
        check(s.beginRound(130, List.of(new double[]{10, 64, 0}), 0) && s.getRound() == 1, "round 1: Mew hidden");
        int step = s.getStep();
        check(s.interact(140, 8, step, 0, 3.0) == null, "a click of another session is ignored");
        check(s.interact(140, 7, step - 1, 0, 3.0) == null, "a click of an older step is ignored");
        check(s.interact(140, 7, step, 0, 9.0) == null, "a click from too far is ignored");
        check(s.interact(141, 7, step, 0, 3.0) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.NEED_ROUND, "found: round 2 next");
        check(s.interact(142, 7, s.getStep(), 0, 3.0) == null, "a second click between rounds changes nothing");
        List<double[]> copies = new ArrayList<>();
        for (int i = 0; i < 5; i++) copies.add(new double[]{i, 64, 6});
        s.beginRound(200, copies, 3);
        check(s.getRound() == 2 && s.getReal() == 3, "round 2: five copies");
        check(s.interact(201, 7, s.getStep(), 0, 3.0) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.ERROR && s.getErrors() == 1, "a wrong copy: error 1, the hint grows");
        check(s.interact(202, 7, s.getStep(), 1, 3.0) == null, "too fast: ignored");
        check(s.interact(210, 7, s.getStep(), 1, 3.0) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.ERROR && s.getErrors() == 2, "error 2");
        check(s.interact(220, 7, s.getStep(), 2, 3.0) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.RESHUFFLE, "error 3: the copies are shuffled, never a failure");
        check(s.getOutcome() == null && s.getPhase() == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Phase.ROUND, "still playing");
        s.beginRound(221, copies, 1);
        check(s.getRound() == 2 && s.getErrors() == 0 && s.getReal() == 1 && s.getReshuffles() == 1, "shuffled: new real copy, errors back to 0");
        check(s.interact(230, 7, s.getStep(), 1, 3.0) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.NEED_ROUND, "the real one: round 3 next");
        List<double[]> course = new ArrayList<>();
        for (int i = 0; i < 6; i++) course.add(new double[]{10 * i, 65, 0});
        s.beginRound(240, course, 0);
        check(s.getRound() == 3 && s.getCourse() == 0, "round 3: a course of 6 points");
        check(s.tick(245, -20, 64, 0, true) == null, "far from Mew: it waits");
        check(s.interact(246, 7, s.getStep(), 0, 3.0) == null, "it cannot be caught before its last point");
        int moves = 0;
        long now = 250;
        for (int i = 0; i < 5; i++) {
            if (s.tick(now, 10 * i, 64, 0, true) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.MOVE) moves++;
            now += 5;
        }
        check(moves == 5 && s.getCourse() == 5 && s.getLastPoint(), "followed to its last point (5 moves)");
        check(s.tick(now, 50, 64, 0, true) == null, "on its last point it stays");
        check(s.tick(now + 800, 50, 64, 30, true) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.STUCK, "stuck 40 s: Mew comes back toward the player");
        int before = s.getStep();
        s.comeBack(now + 800, new double[]{48, 65, 10});
        check(s.getStep() == before + 1 && s.getPoints().get(5)[2] == 10, "its point moved closer");
        check(s.interact(now + 810, 7, s.getStep(), 5, 3.0) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.END
            && s.getOutcome() == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Outcome.WON, "caught on its last point: won");
        check(s.interact(now + 820, 7, s.getStep(), 5, 3.0) == null && !s.end(generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Outcome.TIME), "nothing after the end");

        var time = new generations.gg.generations.core.generationscore.common.world.mew.MewGameSession(1, UUID.randomUUID(), false, rules(), 0L, false, 0);
        time.tick(130, 0, 0, 0, true);
        time.beginRound(130, List.of(new double[]{1, 1, 1}), 0);
        check(time.tick(130 + 6 * 1200 - 1, 0, 0, 0, true) == null && time.tick(130 + 6 * 1200, 0, 0, 0, true) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.END
            && time.getOutcome() == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Outcome.TIME, "6 minutes after the opening: time is up");
        var leave = new generations.gg.generations.core.generationscore.common.world.mew.MewGameSession(2, UUID.randomUUID(), false, rules(), 0L, true, 0);
        check(leave.tick(60, 0, 0, 0, true) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.NEED_ROUND, "Mew's return: 3 s then round 1");
        leave.beginRound(60, List.of(new double[]{1, 1, 1}), 0);
        check(leave.tick(100, 0, 0, 0, false) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.WARN, "out of the area: a warning");
        check(leave.tick(300, 0, 0, 0, true) == null && leave.tick(305, 0, 0, 0, false) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.WARN, "back in time: the count starts again");
        check(leave.tick(704, 0, 0, 0, false) == null && leave.tick(705, 0, 0, 0, false) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.END
            && leave.getOutcome() == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Outcome.LEFT, "20 s out: a failure");
        var third = new generations.gg.generations.core.generationscore.common.world.mew.MewGameSession(3, UUID.randomUUID(), true, rules(), 0L, false, 3);
        third.tick(130, 0, 0, 0, true);
        third.beginRound(130, List.of(new double[]{0, 0, 0}), 0);
        check(third.getRound() == 3 && third.getLastPoint() && third.interact(140, 3, third.getStep(), 0, 2.0) == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Event.END
            && third.getOutcome() == generations.gg.generations.core.generationscore.common.world.mew.MewGameSession.Outcome.WON, "a test from round 3: one point, caught, won");
    }

    private static void shows() {
        org.joml.Vector3d origin = new org.joml.Vector3d(100, 70, -40);
        org.joml.Vector3d player = new org.joml.Vector3d(100, 64, -52);
        List<generations.gg.generations.core.generationscore.common.client.mew.MewShow> all = new ArrayList<>();
        for (int type = 0; type < 4; type++) all.add(generations.gg.generations.core.generationscore.common.client.mew.MewShow.Companion.manifestation(type, 1234 + type, origin, 30.0, new org.joml.Vector3d(8, 0.5, 3), 4.0));
        all.add(new generations.gg.generations.core.generationscore.common.client.mew.Opening(player, 0.0));
        all.add(new generations.gg.generations.core.generationscore.common.client.mew.Comeback(player, 0.0));
        String[] names = {"A", "B", "C", "D", "opening", "return"};
        for (int k = 0; k < all.size(); k++) {
            var show = all.get(k);
            var pose = new generations.gg.generations.core.generationscore.common.client.mew.MewPose();
            int invalid = 0, visible = 0, jumps = 0;
            double maxStep = 0;
            org.joml.Vector3d last = null;
            for (int i = 0; i <= 2000; i++) {
                double t = show.getDuration() * i / 2000.0;
                show.pose(t, player, pose);
                if (!pose.isFinite() || pose.getScale() < 0 || pose.getScale() > 1.0001) invalid++;
                if (pose.getVisible()) {
                    visible++;
                    if (last != null) {
                        double step = last.distance(pose.getPos());
                        if (step > 0.5) jumps++;
                        else maxStep = Math.max(maxStep, step / (show.getDuration() / 2000.0));
                    }
                    last = new org.joml.Vector3d(pose.getPos());
                } else last = null;
            }
            long teleports = show.getEvents().stream().filter(e -> e.getKind() == generations.gg.generations.core.generationscore.common.client.mew.MewShowEvent.Kind.TELEPORT).count();
            check(jumps <= teleports, "show " + names[k] + ": " + jumps + " jumps for " + teleports + " teleports");
            boolean sorted = true;
            double prev = -1;
            for (var e : show.getEvents()) {
                if (e.getTime() < prev || e.getTime() < 0 || e.getTime() > show.getDuration()) sorted = false;
                prev = e.getTime();
            }
            show.pose(show.getDuration(), player, pose);
            boolean endsHidden = !pose.getVisible();
            String what = "show " + names[k];
            check(invalid == 0, what + ": " + invalid + " invalid poses");
            // B (build 61): Mew shows itself, then dissolves; the others keep it longer.
            check(visible > (k == 1 ? 300 : 1000), what + ": Mew seen " + visible + "/2001");
            check(sorted && !show.getEvents().isEmpty(), what + ": events in order within " + show.getDuration() + " s");
            check(endsHidden, what + ": gone at its end");
            check(maxStep < 40.0, what + ": Mew's speed at most " + maxStep + " blocks a second (teleports aside)");
        }
        check(all.get(4).getDuration() == 6.5 && all.get(5).getDuration() == 3.0, "the opening lasts 6.5 s, the return 3 s");
        check(all.get(1).motes(3.0) > 1.0 && all.get(1).getMotesAt() != null, "B: motes where the player turns");
        check(all.get(2).trail(1.0) && !all.get(2).trail(3.9), "C: a trail, then its teleport");
    }

    private static void spread() {
        int period = 30 * 20;
        int[] perTick = new int[period];
        Random random = new Random(200);
        boolean inRange = true;
        for (int i = 0; i < 200; i++) {
            UUID u = new UUID(random.nextLong(), random.nextLong());
            int s = MewMath.slot(u.getMostSignificantBits(), u.getLeastSignificantBits(), period);
            inRange &= s >= 0 && s < period;
            if (s >= 0 && s < period) perTick[s]++;
        }
        int max = Arrays.stream(perTick).max().orElse(0);
        check(inRange, "slots within the period");
        check(max <= 5, "200 players over 30 s: at most " + max + " checks on one tick");
        System.out.printf(Locale.ROOT, "200 players: %.1f checks a second, at most %d on a tick%n", 200 / 30.0, max);
        check(MewMath.slot(1, 2, 600) == MewMath.slot(1, 2, 600), "slot stable for a player");
    }
}
