package generations.gg.generations.core.generationscore.common.mixin;

import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnAction;
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction;
import com.cobblemon.mod.common.api.spawning.fishing.FishingSpawnCause;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import generations.gg.generations.core.generationscore.common.world.catchcombo.CatchCombo;
import generations.gg.generations.core.generationscore.common.world.catchcombo.SpawnContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Around the making of a Cobblemon spawn (natural or fishing): the player whose spawner
 * made it is known while Cobblemon rolls its shiny (SpawnContext, the Catch Combo's
 * bonus), then the combo's IVs and reservation once the Pokémon exists, before it is in
 * the world (CatchCombo.afterSpawn).
 */
@Mixin(value = PokemonSpawnAction.class, remap = false)
public abstract class PokemonSpawnActionMixin {
    @Inject(method = "createEntity()Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;", at = @At("HEAD"))
    private void generations$spawnStarts(CallbackInfoReturnable<PokemonEntity> cir) {
        SpawnCause cause = ((SpawnAction<?>) (Object) this).getSpawnablePosition().getCause();
        Entity entity = cause.getEntity();
        SpawnContext.begin(entity instanceof ServerPlayer player ? player : null, cause instanceof FishingSpawnCause);
    }

    @Inject(method = "createEntity()Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;", at = @At("RETURN"))
    private void generations$spawnMade(CallbackInfoReturnable<PokemonEntity> cir) {
        CatchCombo.afterSpawn(cir.getReturnValue(), SpawnContext.end());
    }
}
