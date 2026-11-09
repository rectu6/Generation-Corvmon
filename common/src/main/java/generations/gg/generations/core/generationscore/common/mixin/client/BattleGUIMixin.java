package generations.gg.generations.core.generationscore.common.mixin.client;

import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection;
import generations.gg.generations.core.generationscore.common.client.screen.battle.SwshActionSelection;
import generations.gg.generations.core.generationscore.common.client.screen.battle.SwshBattleHud;
import generations.gg.generations.core.generationscore.common.client.screen.battle.SwshSwitchSelection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The battle's action menu and switch screen become the Sword and Shield style ones. */
@Mixin(value = BattleGUI.class, remap = false)
public abstract class BattleGUIMixin {
    @Inject(method = "deriveRootActionSelection", at = @At("RETURN"), cancellable = true)
    private void generations$swshActions(ClientBattleActor actor, SingleActionRequest request, CallbackInfoReturnable<BattleActionSelection> cir) {
        if (cir.getReturnValue() instanceof BattleGeneralActionSelection) {
            cir.setReturnValue(new SwshActionSelection((BattleGUI) (Object) this, request));
        } else if (cir.getReturnValue() instanceof BattleSwitchPokemonSelection && SwshBattleHud.INSTANCE.getEnabled()) {
            cir.setReturnValue(new SwshSwitchSelection((BattleGUI) (Object) this, request));
        }
    }
}
