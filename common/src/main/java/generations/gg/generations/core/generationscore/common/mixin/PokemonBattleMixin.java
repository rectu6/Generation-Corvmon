package generations.gg.generations.core.generationscore.common.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeature;
import com.cobblemon.mod.common.api.tags.CobblemonItemTags;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import generations.gg.generations.core.generationscore.common.battle.BattleConditionsProcessor;
import generations.gg.generations.core.generationscore.common.battle.ExpAllCalculator;
import generations.gg.generations.core.generationscore.common.battle.GenerationsInstructionProcessor;
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.function.Consumer;
import java.util.stream.Stream;

@Mixin(PokemonBattle.class)
public abstract class PokemonBattleMixin {

    @Shadow public abstract void setMute(boolean b);

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void onConstruct(BattleFormat format, BattleSide side1, BattleSide side2, CallbackInfo ci) {
//        this.setMute(false);
    }

    @Inject(method = "end", at = @At("TAIL"), remap = false)
    private void injectEnd(CallbackInfo ci) {
        BattleConditionsProcessor.processBattleEnd((PokemonBattle) (Object) this);
        GenerationsInstructionProcessor.processBattleEnd((PokemonBattle) (Object) this);
    }

    @ModifyArgs(
            method = "end",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/api/pokemon/experience/ExperienceCalculator;calculate(Lcom/cobblemon/mod/common/battles/pokemon/BattlePokemon;Lcom/cobblemon/mod/common/battles/pokemon/BattlePokemon;D)I"
            ),
            remap = false
    )
    /**
     * Cobblemon calls the calculator for the Pokémon that faced the fainted one, and
     * for Exp Share holders that did not. With an Exp All on, the latter get the Exp
     * All's share instead (GenerationsInstructionProcessor.grantExpAll): their Exp
     * Share gives nothing. Pokémon that fought keep their experience.
     */
    private void interceptExpShareArgs(Args args) {
        BattlePokemon receiver = args.get(0);
        BattlePokemon fainted = args.get(1);

        ServerPlayer player = receiver.getEffectedPokemon().getOwnerPlayer();
        if (player == null || !ExpAllCalculator.hasExpAll(player)) return;

        boolean hasExpShare = receiver.getEffectedPokemon().heldItemNoCopy$common().is(CobblemonItemTags.EXPERIENCE_SHARE);
        if (hasExpShare && !receiver.getFacedOpponents().contains(fainted)) {
            // A negative amount is never awarded.
            args.set(2, -1.0D);
        }
    }
}