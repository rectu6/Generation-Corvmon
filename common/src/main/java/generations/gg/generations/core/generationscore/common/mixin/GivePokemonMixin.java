package generations.gg.generations.core.generationscore.common.mixin;

import com.cobblemon.mod.common.command.GivePokemon;
import com.mojang.brigadier.CommandDispatcher;
import generations.gg.generations.core.generationscore.common.commands.GivePokemonProxy;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GivePokemon.class)
public abstract class GivePokemonMixin {

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private void cancelRegister(CommandDispatcher<CommandSourceStack> dispatcher, CallbackInfo ci) {
        ci.cancel();
        GivePokemonProxy.INSTANCE.register(dispatcher);
    }
}
