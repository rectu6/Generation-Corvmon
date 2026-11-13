import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraSpaceConfig;
import generations.gg.generations.core.generationscore.common.world.ultraspace.encounter.UltraEncounterDraw;
import kotlin.ranges.IntRange;

import java.util.*;

/** Draws the arrival encounter with the mod's own code and default tables, and compares the rates found with the configured ones. */
public class UltraEncounterSim {
    public static void main(String[] args) {
        UltraSpaceConfig.Encounters tables = new UltraSpaceConfig.Encounters();
        tables.validate();
        Map<String, Float> beasts = new LinkedHashMap<>();
        beasts.put("nihilego", 1f);
        beasts.put("poipole", 0.85f);
        List<UltraEncounterDraw.Candidate> ordinary = List.of(
            new UltraEncounterDraw.Candidate("glimmet", 6f), new UltraEncounterDraw.Candidate("boldore", 3f));
        Random random = new Random(42);
        int n = Integer.parseInt(args.length > 0 ? args[0] : "2000000");
        double[] distances = {0, 1000, 4000, 7999, 8000, 12000};
        System.out.println("ly     level | UB found / set       | ordinary shiny found / set | UB shiny found / set (UB drawn) | poipole share of UB");
        for (double ly : distances) {
            for (int level = 1; level <= 4; level++) {
                float pBeast = tables.chance(tables.getUltraBeast(), ly, level);
                float pOrdinary = tables.chance(tables.getOrdinaryShiny(), ly, level);
                float pBeastShiny = tables.chance(tables.getUltraBeastShiny(), ly, level);
                long b = 0, bs = 0, os = 0, poipole = 0;
                for (int i = 0; i < n; i++) {
                    UltraEncounterDraw.Result r = UltraEncounterDraw.INSTANCE.draw(random, pBeast, pOrdinary, pBeastShiny, beasts, ordinary,
                        new IntRange(25, 35), new IntRange(50, 60));
                    if (r.getUltraBeast()) {
                        b++;
                        if (r.getShiny()) bs++;
                        if (r.getSpecies().equals("poipole")) poipole++;
                    } else if (r.getShiny()) os++;
                }
                long o = n - b;
                System.out.printf(Locale.ROOT, "%6.0f  %d   | %7.4f%% / %7.4f%% | %8.4f%% / %8.4f%%       | %7.4f%% / %7.4f%% (%d) | %.3f (set %.3f)%n",
                    ly, level, 100.0 * b / n, 100.0 * pBeast, 100.0 * os / o, 100.0 * pOrdinary, b > 0 ? 100.0 * bs / b : 0.0, 100.0 * pBeastShiny, b,
                    b > 0 ? (double) poipole / b : 0.0, 0.85 / 1.85);
            }
        }
        // Independence: an exceptional portal at 8 000 ly (ordinary shiny 90 %), every draw an Ultra Beast.
        float pOrdinary = tables.chance(tables.getOrdinaryShiny(), 8000, 4);
        float pBeastShiny = tables.chance(tables.getUltraBeastShiny(), 8000, 4);
        long m = 20_000_000L, shiny = 0;
        for (long i = 0; i < m; i++) {
            UltraEncounterDraw.Result r = UltraEncounterDraw.INSTANCE.draw(random, 1f, pOrdinary, pBeastShiny, beasts, ordinary,
                new IntRange(25, 35), new IntRange(50, 60));
            if (r.getShiny()) shiny++;
        }
        System.out.printf(Locale.ROOT, "Every draw an Ultra Beast, 8000 ly level 4 (ordinary shiny set to %.1f%%): UB shiny 1 in %.0f (set 1 in %.0f), %d draws%n",
            100.0 * pOrdinary, (double) m / shiny, 1.0 / pBeastShiny, m);
    }
}
