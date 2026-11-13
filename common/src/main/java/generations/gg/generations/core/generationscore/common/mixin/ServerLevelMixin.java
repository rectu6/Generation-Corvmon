package generations.gg.generations.core.generationscore.common.mixin;

import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No Minecraft weather cycle in the Ultra worlds: they would follow the Overworld's rain
 * (their level data is the Overworld's) and send it to their players. Their weather is
 * UltraWeather's (LevelMixin). The Overworld, the Nether and the End keep theirs.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void generations$noVanillaWeather(CallbackInfo ci) {
        if (UltraWorlds.INSTANCE.isUltra(((ServerLevel) (Object) this).dimension())) ci.cancel();
    }
}
