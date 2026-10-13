package generations.gg.generations.core.generationscore.common.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PokemonEntity.class)
public interface PokemonEntityAccessor {
    @Invoker("attemptItemInteraction")
    boolean invokeAttemptItemInteraction(Player player, ItemStack stack);
}