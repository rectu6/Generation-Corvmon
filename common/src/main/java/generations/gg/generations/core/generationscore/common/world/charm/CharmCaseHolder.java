package generations.gg.generations.core.generationscore.common.world.charm;

import net.minecraft.world.SimpleContainer;

/** Added to every Player by PlayerMixin: the charm case, saved in the player's data like the ender chest. */
public interface CharmCaseHolder {
    SimpleContainer generations$getCharmCase();
}
