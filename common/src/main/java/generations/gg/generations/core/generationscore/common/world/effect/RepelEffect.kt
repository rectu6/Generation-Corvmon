package generations.gg.generations.core.generationscore.common.world.effect

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.ai.util.DefaultRandomPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * Wild Pokémon flee from a player under this effect and do not spawn within its
 * radius (spawns are cancelled in GenerationsArchitecturyEvents).
 */
class RepelEffect : MobEffect(MobEffectCategory.BENEFICIAL, 0x8E5BC8) {

    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean = duration % FLEE_INTERVAL == 0

    override fun applyEffectTick(entity: LivingEntity, amplifier: Int): Boolean {
        val player = entity as? ServerPlayer ?: return true
        val radius = radius(amplifier)
        val playerPos = player.position()
        player.serverLevel().getEntitiesOfClass(PokemonEntity::class.java, player.boundingBox.inflate(radius)) {
            it.pokemon.isWild() && !it.isBattling && !it.isBusy && it.distanceToSqr(playerPos) <= radius * radius
        }.forEach { flee(it, playerPos, radius) }
        return true
    }

    private fun flee(pokemon: PokemonEntity, from: Vec3, radius: Double) {
        val distance = (radius + 8).toInt()
        // A walkable spot away from the player; flying and swimming Pokémon may
        // have none, so they head straight away instead.
        val target = DefaultRandomPos.getPosAway(pokemon, distance, 7, from)
            ?: pokemon.position().subtract(from).normalize().scale(distance.toDouble()).add(pokemon.position())
        pokemon.navigation.moveTo(target.x, target.y, target.z, FLEE_SPEED)
        // Cobblemon's brain moves Pokémon to their walk target: give it the same one.
        pokemon.brain.setMemory(MemoryModuleType.WALK_TARGET, WalkTarget(target, FLEE_SPEED.toFloat(), 1))
    }

    companion object {
        private const val FLEE_INTERVAL = 10
        private const val FLEE_SPEED = 1.4

        /** Repel: 10 blocks, Super Repel: 15, Max Repel: 25. */
        @JvmStatic
        fun radius(amplifier: Int): Double = when (amplifier) {
            0 -> 10.0
            1 -> 15.0
            else -> 25.0
        }

        /** True when a player's repel covers this position. */
        @JvmStatic
        fun isRepelled(level: Level, pos: Vec3): Boolean = level.players().any { player ->
            val effect = player.getEffect(GenerationsMobEffects.REPEL) ?: return@any false
            val radius = radius(effect.amplifier)
            player.distanceToSqr(pos) <= radius * radius
        }
    }
}
