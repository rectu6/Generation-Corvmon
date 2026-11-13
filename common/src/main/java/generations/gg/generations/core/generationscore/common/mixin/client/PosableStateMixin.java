package generations.gg.generations.core.generationscore.common.mixin.client;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import generations.gg.generations.core.generationscore.common.client.render.CobblemonInstanceProvider;
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.CobblemonInstance;
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.RareCandyModelSize;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PosableState.class)
public class PosableStateMixin implements CobblemonInstanceProvider, RareCandyModelSize {
    private CobblemonInstance instance;

    public @NotNull CobblemonInstance getInstance() {
        if (instance == null) {
            instance = new CobblemonInstance(new Matrix4f(), new Matrix4f(), null);
        }

        return instance;
    }

    @Override
    public float generations$modelRadius() {
        // Does not create the instance: entities drawn with Cobblemon's own models have none.
        return instance != null ? instance.modelRadius : 0f;
    }

    @Override
    public float generations$modelTopY() {
        return instance != null ? instance.modelTopY : 0f;
    }

    @Override
    public float generations$modelHalfWidth() {
        return instance != null ? instance.modelHalfWidth : 0f;
    }

    @Override
    public float generations$modelHeight() {
        return instance != null ? instance.modelHeight : 0f;
    }
}
