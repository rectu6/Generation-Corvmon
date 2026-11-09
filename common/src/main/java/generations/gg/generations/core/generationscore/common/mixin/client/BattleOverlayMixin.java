package generations.gg.generations.core.generationscore.common.mixin.client;

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBallDisplay;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.status.PersistentStatus;
import generations.gg.generations.core.generationscore.common.client.BattleOverlayProxy;
import generations.gg.generations.core.generationscore.common.client.screen.battle.SwshBattleHud;
import generations.gg.generations.core.generationscore.common.client.screen.raid.RaidHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BattleOverlay.class)
public abstract class BattleOverlayMixin {

    @Shadow public abstract float getPassedSeconds();

    @Shadow public abstract double getOpacity();

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/gui/battle/BattleOverlay;drawTile(Lnet/minecraft/client/gui/GuiGraphics;FLcom/cobblemon/mod/common/client/battle/ActiveClientBattlePokemon;ZILcom/cobblemon/mod/common/api/pokedex/PokedexEntryProgress;ZZZ)V"
            )
    )
    private void redirectDrawTile(BattleOverlay instance, GuiGraphics context, float tickDelta, ActiveClientBattlePokemon pokemon, boolean left, int rank, PokedexEntryProgress dex, boolean hasCommand, boolean isHovered, boolean isCompact) {
        if (SwshBattleHud.INSTANCE.getEnabled()) {
            // The HD HUD draws the boxes and the weather badges; the tiles' slide
            // animations keep running.
            SwshBattleHud.INSTANCE.trackTile(pokemon, left, tickDelta);
            return;
        }
        BattleOverlayProxy.render(context, tickDelta, pokemon, left, rank, dex, hasCommand, isHovered, isCompact, instance.getPassedSeconds(), instance.getOpacity());
    }

    /** HD battle HUD (Sword and Shield style), or the raid panel with Cobblemon's tiles. */
    @Inject(method = "render", at = @At("TAIL"))
    private void generations$raidHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (SwshBattleHud.INSTANCE.getEnabled()) {
            SwshBattleHud.INSTANCE.render(context, tickCounter.getRealtimeDeltaTicks(), (float) getOpacity());
        } else {
            RaidHud.INSTANCE.render(context, getOpacity());
        }
    }
}