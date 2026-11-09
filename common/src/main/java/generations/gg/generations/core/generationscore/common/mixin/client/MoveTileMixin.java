package generations.gg.generations.core.generationscore.common.mixin.client;

import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import generations.gg.generations.core.generationscore.common.client.screen.battle.SwshBattleUi;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cobblemon's move tiles in the style of Sword and Shield: a column at the bottom
 * right, pills in the type colour (SwshBattleUi). Their logic stays Cobblemon's.
 */
@Mixin(targets = "com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection$MoveTile", remap = false)
public abstract class MoveTileMixin {
    @Shadow @Mutable @Final private float x;
    @Shadow @Mutable @Final private float y;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void generations$swshPosition(BattleMoveSelection selection, InBattleMove move, float x, float y, CallbackInfo ci) {
        float[] position = SwshBattleUi.INSTANCE.movePosition(selection, x, y);
        this.x = position[0];
        this.y = position[1];
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void generations$swshRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        SwshBattleUi.INSTANCE.renderMove((BattleMoveSelection.MoveTile) (Object) this, context, mouseX, mouseY);
        ci.cancel();
    }

    @Inject(method = "isHovered", at = @At("HEAD"), cancellable = true)
    private void generations$swshHovered(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(SwshBattleUi.INSTANCE.isOver(mouseX, mouseY, x, y, SwshBattleUi.MOVE_WIDTH, SwshBattleUi.MOVE_HEIGHT));
    }
}
