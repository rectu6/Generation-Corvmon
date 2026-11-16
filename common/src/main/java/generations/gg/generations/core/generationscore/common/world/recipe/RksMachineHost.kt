package generations.gg.generations.core.generationscore.common.world.recipe

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.Optional

/**
 * The machine an RKS recipe's result is made in: the old RKS Machine or the new console
 * (RKS-PLAN.md D16): where a Pokémon it makes appears (wild), the way it faces, and the Pokémon
 * of a Time Capsule the recipe used.
 */
interface RksMachineHost {
    val hostLevel: Level?

    fun spawnPoint(): Vec3

    fun spawnYaw(): Float

    var capsulePokemon: Optional<Pokemon>
}
