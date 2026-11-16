package generations.gg.generations.core.generationscore.common.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import generations.gg.generations.core.generationscore.common.world.charm.Charms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Mark Charm: the chance given to applyPotentialMarks ×3 (charms.json), for its owner or the spawn's player. */
@Mixin(value = Pokemon.class, remap = false)
public abstract class PokemonMarksMixin {
    @ModifyVariable(method = "applyPotentialMarks", at = @At("HEAD"), argsOnly = true)
    private double generations$markCharm(double chance) {
        return Charms.markChance((Pokemon) (Object) this, chance);
    }
}
