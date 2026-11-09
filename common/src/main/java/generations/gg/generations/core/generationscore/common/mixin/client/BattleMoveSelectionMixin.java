package generations.gg.generations.core.generationscore.common.mixin.client;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import generations.gg.generations.core.generationscore.common.client.screen.battle.SwshBattleUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** The back button of the move menu, and the gimmick buttons placed after it, go under the moves. */
@Mixin(value = BattleMoveSelection.class, remap = false)
public abstract class BattleMoveSelectionMixin {
    @ModifyArgs(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/gui/battle/subscreen/BattleBackButton;<init>(FF)V")
    )
    private void generations$swshBackButton(Args args) {
        args.set(0, (float) SwshBattleUi.INSTANCE.moveX());
        args.set(1, SwshBattleUi.INSTANCE.buttonsY());
    }
}
