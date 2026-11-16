package generations.gg.generations.core.generationscore.common.world.catchcombo

import net.minecraft.server.level.ServerPlayer

/**
 * The spawn being made by Cobblemon (PokemonSpawnActionMixin, around
 * PokemonSpawnAction.createEntity): the player whose spawner made it, natural or fishing.
 * Cobblemon rolls the shiny inside, with that player: the Catch Combo's bonus applies only
 * there. Server thread only.
 */
object SpawnContext {
    class Spawn(val player: ServerPlayer, val fishing: Boolean) {
        /** Set when a Catch Combo bonus changed its shiny chance. */
        var shinyBoosted = false
        var shinyMultiplier = 1.0
    }

    private var current: Spawn? = null

    @JvmStatic
    fun begin(player: ServerPlayer?, fishing: Boolean) {
        current = player?.let { Spawn(it, fishing) }
    }

    /** The spawn being made, then forgotten. */
    @JvmStatic
    fun end(): Spawn? = current.also { current = null }

    fun current(): Spawn? = current

    /** The spawn being made for this player, null otherwise. */
    fun of(player: ServerPlayer?): Spawn? = current?.takeIf { player != null && it.player === player }

    /** Forgotten at the end of each server tick (a spawn that threw an error leaves nothing behind). */
    fun clear() {
        current = null
    }
}
