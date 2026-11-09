package generations.gg.generations.core.generationscore.common.mixin.showdown;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.EndInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import generations.gg.generations.core.generationscore.common.GenerationsCore;
import generations.gg.generations.core.generationscore.common.battle.DynamaxVisuals;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Showdown's "-end ... Dynamax" message drives DynamaxVisuals (as in CobblemonMegaShowdown). */
@Mixin(value = EndInstruction.class, remap = false)
public class DynamaxEndInstructionMixin {
    @Shadow @Final private BattleMessage message;

    @Inject(method = "invoke", at = @At("HEAD"), remap = false)
    private void generations$dynamaxEnd(PokemonBattle battle, CallbackInfo ci) {
        boolean dynamax = false;
        for (String part : message.getRawMessage().split("[|]")) {
            if (part.contains("Dynamax")) dynamax = true;
        }
        if (!dynamax) return;
        BattlePokemon pokemon = message.battlePokemon(0, battle);
        if (pokemon == null) return;
        battle.dispatchGo(() -> {
            DynamaxVisuals.INSTANCE.end(pokemon.getEffectedPokemon());
            return kotlin.Unit.INSTANCE;
        });
    }
}
