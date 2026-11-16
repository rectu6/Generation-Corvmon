package generations.gg.generations.core.generationscore.common.mixin;

import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.BattleStartResult;
import generations.gg.generations.core.generationscore.common.battle.BattleStartProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Times BattleRegistry.startBattle on the server (Showdown runs in it), option client.profileFirstUse. */
@Mixin(value = BattleRegistry.class, remap = false)
public abstract class BattleRegistryMixin {
    @Inject(method = "startBattle", at = @At("HEAD"))
    private static void generations$startTimer(BattleFormat format, BattleSide side1, BattleSide side2, boolean canPreempt, CallbackInfoReturnable<BattleStartResult> cir) {
        BattleStartProfiler.start();
    }

    @Inject(method = "startBattle", at = @At("RETURN"))
    private static void generations$stopTimer(BattleFormat format, BattleSide side1, BattleSide side2, boolean canPreempt, CallbackInfoReturnable<BattleStartResult> cir) {
        BattleStartProfiler.stop(cir.getReturnValue());
    }
}
