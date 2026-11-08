package generations.gg.generations.core.generationscore.common.battle

import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import net.minecraft.world.entity.Entity

/**
 * Mega Evolution animation: Cobblemon's evolution animation shortened to 4.5 s, in
 * rainbow colours (assets/generations_core/bedrock/generic/animations/
 * mega_evolution.animation.json and particles/mega_*.particle.json). The Pokémon is
 * hidden by light from 1 s, and the new form must appear at the burst.
 */
object MegaEvolutionAnimation {
    /** Burst of mega_particles, when the Mega form is revealed. */
    const val REVEAL_SECONDS = 3.4f
    const val LENGTH_SECONDS = 4.5f

    private const val ANIMATION = "q.bedrock_stateful('mega_evolution', 'mega_evolution', 'endures_primary_animations');"
    private const val RANGE = 128.0

    fun play(entity: Entity) {
        PlayPosableAnimationPacket(entity.id, setOf(ANIMATION), emptyList())
            .sendToPlayersAround(entity.x, entity.y, entity.z, RANGE, entity.level().dimension())
    }
}
