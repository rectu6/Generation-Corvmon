package generations.gg.generations.core.generationscore.common.mixin;

import com.cobblemon.mod.common.item.interactive.PokerodItem;
import generations.gg.generations.core.generationscore.common.GenerationsCore;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cobblemon names every rod "Poké Rod": Generations' Old, Good and Super Rods keep their own names. */
@Mixin(PokerodItem.class)
public abstract class PokerodItemMixin {
    @Inject(method = "getDescriptionId", at = @At("HEAD"), cancellable = true)
    private void generations$ownName(CallbackInfoReturnable<String> cir) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey((Item) (Object) this);
        if (GenerationsCore.MOD_ID.equals(id.getNamespace())) {
            cir.setReturnValue(Util.makeDescriptionId("item", id));
        }
    }
}
