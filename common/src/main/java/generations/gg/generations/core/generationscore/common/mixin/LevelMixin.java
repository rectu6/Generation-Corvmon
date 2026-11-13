package generations.gg.generations.core.generationscore.common.mixin;

import generations.gg.generations.core.generationscore.common.world.ultraspace.weather.UltraWeatherSky;
import generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.UltraWorlds;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Ultra worlds' own weather (phase 4.4): their rain and thunder levels are those of
 * their Ultra weather (UltraWeatherSky), on the server and on the client, and no longer
 * the Overworld's. The other dimensions are not touched.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    /** -1: not known yet; 0: another dimension; 1: an Ultra world (a level never changes dimension). */
    @Unique
    private int generations$ultra = -1;

    @Unique
    private boolean generations$isUltra() {
        if (generations$ultra < 0) generations$ultra = UltraWorlds.INSTANCE.isUltra(((Level) (Object) this).dimension()) ? 1 : 0;
        return generations$ultra == 1;
    }

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void generations$ultraRain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (generations$isUltra()) cir.setReturnValue(UltraWeatherSky.rainLevel((Level) (Object) this, partialTick));
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void generations$ultraThunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (generations$isUltra()) cir.setReturnValue(UltraWeatherSky.thunderLevel((Level) (Object) this, partialTick));
    }
}
