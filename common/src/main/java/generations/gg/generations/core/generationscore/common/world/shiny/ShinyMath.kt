package generations.gg.generations.core.generationscore.common.world.shiny

/**
 * The arithmetic of the shiny chances (ShinyOdds), apart from the game so that it can be
 * tested and simulated outside it. A chance is a probability (0 to 1); Cobblemon's rate
 * is "1 in N" (8192).
 *
 * Normal roll: chance = base × multiplier, the multiplier coming from the bonuses:
 * - ADDITIVE (default): 1 + Σ(bonusᵢ − 1), so ×2 and ×2.5 give ×3.5;
 * - MULTIPLICATIVE: Π bonusᵢ (×5 for the same).
 * The multiplier is capped (cap, 0 or less = no cap).
 *
 * Special chance (the Ultra Warp Ride's arrival encounter): an absolute chance from its
 * tables, never lowered. The other bonuses add the base chance times their extra:
 * p = special + base × min(cap − 1, extra), extra = Σ(bonusᵢ − 1) (or Π − 1).
 */
object ShinyMath {
    enum class Stacking { ADDITIVE, MULTIPLICATIVE }

    class Bonus(val source: String, val multiplier: Double) {
        override fun toString() = "$source ×${format(multiplier)}"
    }

    class Result(
        /** Chance without any bonus (1 / Cobblemon's rate, or the special chance). */
        val baseChance: Double,
        /** Total multiplier of the bonuses after the cap (normal roll). */
        val multiplier: Double,
        val capped: Boolean,
        /** The final chance (0 to 1). */
        val chance: Double,
        /** The special chance it started from, null for a normal roll. */
        val special: Double?,
        val bonuses: List<Bonus>,
    ) {
        /** "1/1820" for the log and the commands. */
        val oneIn: String get() = if (chance <= 0.0) "0" else "1/${Math.round(1.0 / chance)}"

        override fun toString(): String = buildString {
            append(oneIn)
            if (special != null) append(" (special ${format(special)})")
            if (bonuses.isNotEmpty()) append(" = ${bonuses.joinToString(" + ")} → ×${format(multiplier)}${if (capped) " (capped)" else ""}")
        }
    }

    /** "2", "1,15", "2,5" (no trailing zeros); tiny chances with 3 significant digits. */
    fun format(value: Double): String = when {
        !value.isFinite() -> value.toString()
        value == Math.rint(value) && Math.abs(value) < 1e15 -> value.toLong().toString()
        Math.abs(value) < 0.01 -> "%.3g".format(value)
        else -> "%.4f".format(value).trimEnd('0').trimEnd('.', ',')
    }

    /** Usable bonuses: finite, above 1 (a bonus never lowers a chance). */
    private fun usable(bonuses: List<Bonus>) = bonuses.filter { it.multiplier.isFinite() && it.multiplier > 1.0 }

    /** The bonuses' extra over 1 (Σ(m − 1) or Π m − 1), before the cap. */
    fun extra(bonuses: List<Bonus>, stacking: Stacking): Double {
        val list = usable(bonuses)
        if (list.isEmpty()) return 0.0
        return when (stacking) {
            Stacking.ADDITIVE -> list.sumOf { it.multiplier - 1.0 }
            Stacking.MULTIPLICATIVE -> list.fold(1.0) { total, bonus -> total * bonus.multiplier } - 1.0
        }
    }

    /** Extra after the cap (cap ≤ 0: none), and whether the cap applied. */
    fun cappedExtra(bonuses: List<Bonus>, stacking: Stacking, cap: Double): Pair<Double, Boolean> {
        val extra = extra(bonuses, stacking)
        if (cap <= 0.0 || !cap.isFinite()) return extra to false
        val limit = (cap - 1.0).coerceAtLeast(0.0)
        return if (extra > limit) limit to true else extra to false
    }

    /** A normal roll: rate is Cobblemon's "1 in N". */
    fun normal(rate: Double, bonuses: List<Bonus>, stacking: Stacking, cap: Double): Result {
        val base = if (rate > 0.0 && rate.isFinite()) 1.0 / rate else 0.0
        val list = usable(bonuses)
        val (extra, capped) = cappedExtra(list, stacking, cap)
        val multiplier = 1.0 + extra
        return Result(base, multiplier, capped, (base * multiplier).coerceIn(0.0, 1.0), null, list)
    }

    /** A special chance (absolute probability), never lowered; the bonuses add base × extra. */
    fun special(special: Double, rate: Double, bonuses: List<Bonus>, stacking: Stacking, cap: Double): Result {
        val start = if (special.isFinite()) special.coerceIn(0.0, 1.0) else 0.0
        val base = if (rate > 0.0 && rate.isFinite()) 1.0 / rate else 0.0
        val list = usable(bonuses)
        val (extra, capped) = cappedExtra(list, stacking, cap)
        val chance = (start + base * extra).coerceIn(start, 1.0)
        return Result(start, 1.0 + extra, capped, chance, start, list)
    }

    /** Back to Cobblemon's "1 in N" (what SHINY_CHANCE_CALCULATION returns). */
    fun toRate(chance: Double): Float = if (chance <= 0.0) Float.MAX_VALUE else (1.0 / chance).toFloat()
}
