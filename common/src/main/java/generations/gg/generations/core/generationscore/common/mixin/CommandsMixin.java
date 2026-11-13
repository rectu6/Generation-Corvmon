package generations.gg.generations.core.generationscore.common.mixin;

import generations.gg.generations.core.generationscore.common.commands.UltraWarpCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The mod's commands, added where vanilla builds its own: the same for the
 * integrated server (solo) and a dedicated server, with no loader-specific
 * registration (Cobblemon has no common event for commands).
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void generations$registerCommands(Commands.CommandSelection selection, CommandBuildContext context, CallbackInfo ci) {
        UltraWarpCommand.register(((Commands) (Object) this).getDispatcher());
    }
}
