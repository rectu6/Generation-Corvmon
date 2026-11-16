package generations.gg.generations.core.generationscore.common.battle.flee

import java.util.Random

/**
 * Whether a flight works (InstantFlee), apart from the game so it can be tested.
 * - ALWAYS: it works unless the Pokémon is trapped (Cobblemon's behaviour today, but at once);
 * - SPEED: the games' formula (generation 5 on): with A the player's Pokémon's Speed, B the
 *   wild one's and C the attempts in this battle (this one included),
 *   F = ⌊A × 128 / B⌋ + 30 × C; it works when A ≥ B, F > 255, or a draw in 0..255 is below F.
 * Trapping is checked before: Smoke Ball and Run Away escape it (the games' rules).
 */
object FleeMath {
    enum class Formula { ALWAYS, SPEED }

    enum class Outcome { ESCAPED, TRAPPED, FAILED }

    fun odds(playerSpeed: Int, wildSpeed: Int, attempt: Int): Int {
        if (wildSpeed <= 0 || playerSpeed >= wildSpeed) return 256
        return (playerSpeed * 128) / wildSpeed + 30 * attempt.coerceAtLeast(1)
    }

    fun decide(
        formula: Formula, trapped: Boolean, escapesTraps: Boolean,
        playerSpeed: Int, wildSpeed: Int, attempt: Int, random: Random,
    ): Outcome {
        if (trapped && !escapesTraps) return Outcome.TRAPPED
        if (formula == Formula.ALWAYS || escapesTraps) return Outcome.ESCAPED
        val f = odds(playerSpeed, wildSpeed, attempt)
        return if (f > 255 || random.nextInt(256) < f) Outcome.ESCAPED else Outcome.FAILED
    }
}
