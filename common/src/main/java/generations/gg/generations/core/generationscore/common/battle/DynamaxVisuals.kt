package generations.gg.generations.core.generationscore.common.battle

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * What a Dynamax looks like: the Pokémon grows over two seconds to a set height,
 * whatever its species, and carries ASPECT, synced to clients. With it the client
 * tints the model red (PokemonRendererMixin, RareCandyBone for 3D models) and
 * draws the red Dynamax clouds above its head (DynamaxCloudRenderer).
 * Cobblemon already plays the Dynamax itself in battle (Max Moves, with a Dynamax
 * Band); StartInstructionMixin and EndInstructionMixin call start and end on
 * Showdown's "-start/-end ... Dynamax" messages.
 */
object DynamaxVisuals {
    /** Forced aspect of a Dynamaxed Pokémon, synced to clients. */
    const val ASPECT = "dynamax"
    private const val ORIGINAL_SCALE = "generations_dynamax_scale"
    private const val STEPS = 20
    private const val STEP_SECONDS = 0.1f
    private const val MIN_SCALE = 1.5f
    private const val MAX_SCALE = 25f

    fun isDynamaxed(pokemon: Pokemon): Boolean = ASPECT in pokemon.forcedAspects

    /** Size multiplier that makes this Pokémon the given number of blocks tall. */
    fun scaleFor(pokemon: Pokemon, height: Float): Float {
        val base = pokemon.form.hitbox.height * pokemon.form.baseScale
        return (height / base.coerceAtLeast(0.1f)).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /** Makes the Pokémon look Dynamaxed, growing to the given height in blocks. */
    fun start(pokemon: Pokemon, height: Float = GenerationsCore.CONFIG.raids.dynamaxHeight) {
        if (isDynamaxed(pokemon)) return
        val original = pokemon.scaleModifier
        pokemon.persistentData.putFloat(ORIGINAL_SCALE, original)
        pokemon.forcedAspects = pokemon.forcedAspects + ASPECT
        pokemon.updateAspects()
        val target = original * scaleFor(pokemon, height)

        val entity = pokemon.entity
        if (entity == null) {
            pokemon.scaleModifier = target
            return
        }
        (entity.level() as? ServerLevel)?.playSound(null, entity.x, entity.y, entity.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1f, 0.6f)
        growTo(entity, pokemon, original, target)
    }

    /** Back to its size and colours, gradually when it is out. */
    fun end(pokemon: Pokemon) {
        if (!isDynamaxed(pokemon)) return
        val original = pokemon.persistentData.getFloat(ORIGINAL_SCALE).takeIf { it > 0f } ?: 1f
        pokemon.persistentData.remove(ORIGINAL_SCALE)
        pokemon.forcedAspects = pokemon.forcedAspects - ASPECT
        pokemon.updateAspects()

        val entity = pokemon.entity
        if (entity == null || !entity.isAlive) pokemon.scaleModifier = original
        else growTo(entity, pokemon, pokemon.scaleModifier, original)
    }

    private fun growTo(entity: PokemonEntity, pokemon: Pokemon, from: Float, to: Float) {
        for (step in 1..STEPS) {
            entity.after(step * STEP_SECONDS) {
                // A later start or end replaces this animation.
                val growing = to > from
                if (growing == isDynamaxed(pokemon)) pokemon.scaleModifier = from + (to - from) * step / STEPS
            }
        }
    }
}
