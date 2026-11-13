package generations.gg.generations.core.generationscore.common.world.ultraspace.ride

import generations.gg.generations.core.generationscore.common.world.ultraspace.UltraSpaceConfig

/**
 * The difficulty at a number of light-years: the config's tiers blended linearly
 * between the two around it, so nothing jumps from one tier to the next (before the
 * first tier, the first; after the last, the last), then multiplied by the journey's
 * difficulty mode. describe() writes it all out, for the log.
 */
object UltraWarpDifficulty {
    /** The tiers blended at these light-years, the mode not counted. */
    fun base(config: UltraSpaceConfig.Ride, lightYears: Double): UltraSpaceConfig.Tier {
        val tiers = config.difficulty.tiers
        if (tiers.isEmpty()) return UltraSpaceConfig.Tier()
        if (lightYears <= tiers.first().lightYears) return tiers.first()
        if (lightYears >= tiers.last().lightYears) return tiers.last()
        val upper = tiers.indexOfFirst { it.lightYears > lightYears }
        val a = tiers[upper - 1]
        val b = tiers[upper]
        val t = ((lightYears - a.lightYears) / (b.lightYears - a.lightYears).coerceAtLeast(1e-6f)).toFloat().coerceIn(0f, 1f)
        fun mix(x: Float, y: Float) = x + (y - x) * t
        return UltraSpaceConfig.Tier(
            lightYears.toFloat(), mix(a.speed, b.speed), mix(a.orbs, b.orbs), mix(a.sparks, b.sparks), mix(a.debris, b.debris),
            mix(a.arcs, b.arcs), mix(a.gravity, b.gravity), mix(a.anomalyStrength, b.anomalyStrength), mix(a.events, b.events),
        )
    }

    /** The difficulty of this journey at these light-years: the tiers blended, times its mode. Chances stay within 0 to 1. */
    fun at(settings: UltraWarpRideSettings, lightYears: Double): UltraSpaceConfig.Tier = withMode(base(settings.config, lightYears), settings.mode)

    fun withMode(tier: UltraSpaceConfig.Tier, mode: UltraSpaceConfig.Mode): UltraSpaceConfig.Tier = UltraSpaceConfig.Tier(
        tier.lightYears,
        tier.speed * mode.speed,
        (tier.orbs * mode.orbs).coerceIn(0f, 1f),
        (tier.sparks * mode.dangers).coerceIn(0f, 1f),
        (tier.debris * mode.dangers).coerceIn(0f, 1f),
        (tier.arcs * mode.dangers).coerceIn(0f, 1f),
        (tier.gravity * mode.dangers).coerceIn(0f, 1f),
        tier.anomalyStrength * mode.anomalies,
        tier.events * mode.events,
    )

    /** One tier in words: speed, and each object's chance per slot and how many per 100 blocks. */
    fun line(config: UltraSpaceConfig.Ride, tier: UltraSpaceConfig.Tier): String {
        val per100 = 100.0 / config.objects.slotLength
        fun chance(name: String, value: Float) = "%s %.0f%% (~%.1f/100 blocks)".format(name, value * 100, value * per100)
        return "%.0f ly: speed %.1f b/s, %s, %s, %s, %s, %s, anomaly strength %.2f, events x%.2f".format(
            tier.lightYears, config.speed * tier.speed,
            chance("orange orb", tier.orbs), chance("blue orb", tier.sparks), chance("debris", tier.debris),
            chance("arc", tier.arcs), chance("gravity", tier.gravity),
            tier.anomalyStrength, tier.events,
        )
    }

    /**
     * Every chance of a mode, for the log: each tier (the mode counted), each event
     * (how often per minute at the tiers' events, and its conditions), and the energy.
     */
    fun describe(config: UltraSpaceConfig.Ride, mode: UltraSpaceConfig.Mode): List<String> {
        val lines = ArrayList<String>()
        lines.add(
            "mode '%s': speed x%.2f, orange orbs x%.2f, dangers x%.2f, anomalies x%.2f, events x%.2f, drain x%.2f, damage x%.2f".format(
                mode.id, mode.speed, mode.orbs, mode.dangers, mode.anomalies, mode.events, mode.drain, mode.damage,
            ),
        )
        lines.add(
            "  energy: start %.0f / max %.0f, drain %.2f/s, orange orb +%.0f, blue orb -%.1f, debris -%.1f, arc -%.1f, %.2f s unhurt after a hit".format(
                config.energy.start, config.energy.max, config.energy.drainPerSecond * mode.drain, config.energy.orb,
                config.energy.spark * mode.damage, config.energy.debris * mode.damage, config.energy.arc * mode.damage, config.energy.invulnerableSeconds,
            ),
        )
        config.difficulty.tiers.forEach { lines.add("  " + line(config, withMode(it, mode))) }
        val events = config.events
        listOf("storm" to events.storm, "current" to events.current, "anomaly" to events.anomaly, "necrozma" to events.necrozma).forEach { (name, event) ->
            val first = config.difficulty.tiers.firstOrNull()?.events ?: 1f
            val last = config.difficulty.tiers.lastOrNull()?.events ?: 1f
            lines.add(
                "  event %s: %s, ~%.2f to %.2f per minute (tiers' events x mode), %.0f-%.0f s, from %.0f ly, pause %.0f s, strength %.2f".format(
                    name, if (event.enabled) "on" else "off",
                    event.chancePerMinute * first * mode.events, event.chancePerMinute * last * mode.events,
                    event.minSeconds, event.maxSeconds, event.minLightYears, event.cooldownSeconds, event.strength,
                ),
            )
        }
        return lines
    }
}
