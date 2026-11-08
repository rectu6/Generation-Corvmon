package generations.gg.generations.core.generationscore.common.world.effect

import generations.gg.generations.core.generationscore.common.util.PlatformRegistry
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.effect.MobEffect

object GenerationsMobEffects : PlatformRegistry<MobEffect>(Registries.MOB_EFFECT, BuiltInRegistries.MOB_EFFECT) {
    /** Repel, Super Repel and Max Repel: amplifier 0, 1 and 2 (see RepelEffect.radius). */
    @JvmField
    val REPEL: Holder<MobEffect> = create<MobEffect>("repel", ::RepelEffect)
}
