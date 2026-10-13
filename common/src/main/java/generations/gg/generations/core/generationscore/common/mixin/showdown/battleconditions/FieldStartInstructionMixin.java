package generations.gg.generations.core.generationscore.common.mixin.showdown.battleconditions;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.FieldStartInstruction;
import generations.gg.generations.core.generationscore.common.battle.BattleConditionsProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FieldStartInstruction.class)
public abstract class FieldStartInstructionMixin {
    @Shadow(remap = false) public abstract BattleMessage getMessage();

    @Inject(method = "invoke", at = @At("TAIL"), remap = false)
    private void fieldConditionCount(PokemonBattle battle, CallbackInfo ci) {
        battle.dispatchWaiting(0F, () -> {
            BattleConditionsProcessor.processFieldStart(battle, getMessage());
            return null;
        });
    }
}
