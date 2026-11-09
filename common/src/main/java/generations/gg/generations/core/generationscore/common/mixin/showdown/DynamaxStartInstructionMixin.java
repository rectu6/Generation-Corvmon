package generations.gg.generations.core.generationscore.common.mixin.showdown;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.StartInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import generations.gg.generations.core.generationscore.common.GenerationsCore;
import generations.gg.generations.core.generationscore.common.battle.DynamaxVisuals;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Showdown's "-start ... Dynamax" message drives DynamaxVisuals (as in CobblemonMegaShowdown). */
@Mixin(value = StartInstruction.class, remap = false)
public class DynamaxStartInstructionMixin {
    @Shadow @Final private BattleMessage message;

    @Inject(method = "invoke", at = @At("HEAD"), remap = false)
    private void generations$dynamaxStart(PokemonBattle battle, CallbackInfo ci) {
        boolean dynamax = false;
        for (String part : message.getRawMessage().split("[|]")) {
            if (part.contains("Dynamax")) dynamax = true;
        }
        if (!dynamax) return;
        BattlePokemon pokemon = message.battlePokemon(0, battle);
        if (pokemon == null) return;
        battle.dispatchGo(() -> {
            DynamaxVisuals.INSTANCE.start(pokemon.getEffectedPokemon(), GenerationsCore.CONFIG.raids.dynamaxHeight);
            return kotlin.Unit.INSTANCE;
        });
    }
}
