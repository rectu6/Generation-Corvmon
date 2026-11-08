package generations.gg.generations.core.generationscore.common.mixin.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.RareCandyModelSize;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PokemonEntity.class)
public abstract class PokemonEntityMixin extends LivingEntity {
    private PokemonEntityMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Minecraft and Sodium skip entities whose culling box is off screen, and that
     * box is the hitbox. Some RareCandy models are far bigger than their species'
     * hitbox (Arceus: 1x1 block hitbox, ~5 block model), so they vanished as soon as
     * the hitbox left the screen, e.g. when looking up. The box also covers the
     * model's size, known once it has been drawn (RareCandyBone).
     */
    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        AABB box = super.getBoundingBoxForCulling();
        if (!(((PokemonEntity) (Object) this).getDelegate() instanceof RareCandyModelSize size)) return box;
        float radius = size.generations$modelRadius();
        if (radius <= 0) return box;
        double x = (box.minX + box.maxX) / 2;
        double z = (box.minZ + box.maxZ) / 2;
        return box.minmax(new AABB(x - radius, box.minY - radius, z - radius, x + radius, box.minY + radius, z + radius));
    }
}
