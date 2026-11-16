package generations.gg.generations.core.generationscore.common.world.charm

/**
 * The eight charms (COMBO-FUITE-PERF-PLAN.md, 1.6 and 2.6). The ids are item ids of
 * generations_core: never rename them (saved stacks). Their effects are in Charms, their
 * values in charms/charms.json.
 */
enum class Charm(val id: String) {
    /** Shiny Charm: ×2 on the shiny chance (ShinyOdds). */
    SHINY("shiny_charm"),
    /** Exp Charm: ×1.5 on battle experience. */
    EXP("exp_charm"),
    /** Catching Charm: ×2 on the critical capture chance. */
    CATCHING("catching_charm"),
    /** Mark Charm: ×3 on the chance of marks (Pokemon.applyPotentialMarks). */
    MARK("mark_charm"),
    /** Oval Charm: no effect yet (no breeding in Cobblemon 1.8.4; it comes later). */
    OVAL("oval_charm"),
    /** Glimmering Charm: ×1.5 on the quantities of the Dynamax raids' rewards. */
    GLIMMERING("glimmering_charm"),
    /** Mega Charm: a key stone (Mega Evolution), active from the charm case too. */
    MEGA("mega_charm"),
    /** Old Charm: a collectible, no effect. */
    OLD("old_charm");

    /** Tooltip line saying what it does (fr_fr.json / en_us.json). */
    val effectKey: String get() = "item.generations_core.$id.effect"
}
