package generations.gg.generations.core.generationscore.common.world.raid

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/** An NPC raid participant: a trainer and the Pokémon it fights with. */
class RaidAlly(val name: String, val pokemon: Pokemon) {
    var trainer: NPCEntity? = null
    var entity: PokemonEntity? = null

    /** "Lucario Nv. 50". */
    val label: String get() = pokemon.getDisplayName(false).string + " Nv. " + pokemon.level

    /** "Lucario de Léa". */
    fun displayName(): Component = Component.translatable("generations_core.raid.ally_pokemon", pokemon.getDisplayName(false), name)
}

/**
 * The three NPCs that complete a raid to four participants, as in Sword and Shield.
 * They fight in Showdown (raid.js); in the world they stand next to the player
 * with their Pokémon in front of them, facing the boss.
 */
object RaidAllies {
    /** Scoreboard tag of raid entities: never saved with the world (EntityMixin). */
    const val TEMPORARY = "generations_raid"
    /** Scoreboard tag of the allies' Pokémon: they cannot be caught or battled. */
    const val ALLY = "generations_raid_ally"
    const val COUNT = 3

    private val NPC_CLASS = GenerationsCore.id("raid_ally")
    private val NAMES = listOf(
        "Léa", "Hugo", "Chloé", "Louis", "Manon", "Jules", "Inès", "Nathan", "Camille", "Théo",
        "Zoé", "Lucas", "Emma", "Gabriel", "Jade", "Arthur", "Lina", "Raphaël", "Alice", "Noah",
    )

    /**
     * Allies for a raid of this tier: Pokémon of the tier from the den's biome table,
     * at the tier's top level (tiers.json npcLevelOffset).
     */
    fun create(stars: Int, random: RandomSource, biome: net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>?, count: Int = COUNT): List<RaidAlly> {
        val names = NAMES.shuffled().take(count)
        val tier = generations.gg.generations.core.generationscore.common.world.raid.config.RaidConfig.tier(stars)
        val level = (tier.maxLevel + tier.npcLevelOffset).coerceIn(1, 100)
        // Legendary raids get strong non-legendary allies.
        val allyStars = minOf(stars, 5)
        return names.mapNotNull { name ->
            val pick = generations.gg.generations.core.generationscore.common.world.raid.config.RaidConfig.pokemon
                .pick(allyStars, biome, random, level..level) ?: return@mapNotNull null
            val species = pick.species
            val pokemon = PokemonProperties().apply {
                this.species = species.resourceIdentifier.toString()
                this.level = level
                if (pick.aspects.isNotEmpty()) this.aspects = pick.aspects
            }.create()
            UncatchableProperty.uncatchable().apply(pokemon)
            // An ally always has a move that deals damage.
            if (pokemon.moveSet.getMoves().none { it.damageCategory != DamageCategories.STATUS }) {
                Moves.getByName("tackle")?.let { pokemon.moveSet.setMove(0, it.create()) }
            }
            RaidAlly(name, pokemon)
        }
    }

    /**
     * The trainers stand in line with the player, their Pokémon in front of them, all
     * facing the boss. Each entity is given to [track] before it is added (the raid
     * keeps it; RaidManager removes raid entities nobody fights any more).
     */
    fun spawn(level: ServerLevel, allies: List<RaidAlly>, player: ServerPlayer, boss: Entity, track: (Entity) -> Unit) {
        val toBoss = Vec3(boss.x - player.x, 0.0, boss.z - player.z).let { if (it.lengthSqr() < 1e-4) Vec3(0.0, 0.0, 1.0) else it.normalize() }
        val side = Vec3(-toBoss.z, 0.0, toBoss.x)
        val npcClass = NPCClasses.getByIdentifier(NPC_CLASS) ?: NPCClasses.getByName("standard")
        val offsets = listOf(-2.5, 2.5, 5.0)

        allies.forEachIndexed { i, ally ->
            val spot = player.position().add(side.scale(offsets.getOrElse(i) { 2.5 * (i + 1) }))
            val trainerPos = ground(level, spot)
            val pokemonPos = ground(level, spot.add(toBoss.scale(2.5)))

            if (npcClass != null) {
                val trainer = NPCEntity(level)
                trainer.npc = npcClass
                trainer.customName = Component.literal(ally.name)
                trainer.isCustomNameVisible = true
                trainer.setNoAi(true)
                trainer.addTag(TEMPORARY)
                trainer.moveTo(trainerPos.x, trainerPos.y, trainerPos.z, yawTo(trainerPos, boss), 0f)
                trainer.yHeadRot = trainer.yRot
                trainer.yBodyRot = trainer.yRot
                track(trainer)
                level.addFreshEntity(trainer)
                ally.trainer = trainer
            }

            val entity = PokemonEntity(level, ally.pokemon)
            entity.setNoAi(true)
            entity.isInvulnerable = true
            entity.addTag(TEMPORARY)
            entity.addTag(ALLY)
            entity.moveTo(pokemonPos.x, pokemonPos.y, pokemonPos.z, yawTo(pokemonPos, boss), 0f)
            entity.yHeadRot = entity.yRot
            entity.yBodyRot = entity.yRot
            track(entity)
            level.addFreshEntity(entity)
            level.sendParticles(ParticleTypes.CLOUD, pokemonPos.x, pokemonPos.y + 0.5, pokemonPos.z, 12, 0.3, 0.3, 0.3, 0.02)
            ally.entity = entity
        }
        level.playSound(null, player.x, player.y, player.z, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5f, 1.4f)
    }

    fun despawn(allies: List<RaidAlly>) {
        allies.forEach { ally ->
            ally.entity?.let { entity ->
                (entity.level() as? ServerLevel)?.sendParticles(ParticleTypes.POOF, entity.x, entity.y + 0.5, entity.z, 10, 0.3, 0.3, 0.3, 0.02)
                entity.discard()
            }
            ally.trainer?.discard()
            ally.entity = null
            ally.trainer = null
        }
    }

    // ---- Animations of the simulated fight ----------------------------------

    /** The ally's Pokémon attacks: its attack animation, a hit on the boss. */
    fun attack(ally: RaidAlly, boss: Entity?, special: Boolean, strong: Boolean) {
        val entity = ally.entity ?: return
        animate(entity, if (special) "special" else "physical")
        hit(boss, strong)
    }

    /** The boss attacks the ally's Pokémon. */
    fun attacked(ally: RaidAlly, boss: Entity?, special: Boolean, strong: Boolean) {
        boss?.let { animate(it, if (special) "special" else "physical") }
        hit(ally.entity, strong)
    }

    /** Fainted: its Pokémon disappears until it comes back. */
    fun down(ally: RaidAlly) {
        val entity = ally.entity ?: return
        (entity.level() as? ServerLevel)?.sendParticles(ParticleTypes.POOF, entity.x, entity.y + 0.5, entity.z, 15, 0.3, 0.3, 0.3, 0.02)
        entity.isInvisible = true
    }

    fun revive(ally: RaidAlly) {
        val entity = ally.entity ?: return
        entity.isInvisible = false
        val level = entity.level() as? ServerLevel ?: return
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, entity.x, entity.y + 0.5, entity.z, 15, 0.4, 0.4, 0.4, 0.02)
        level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.2f)
    }

    private fun animate(entity: Entity, animation: String) {
        PlayPosableAnimationPacket(entity.id, setOf(animation), emptyList())
            .sendToPlayersAround(entity.x, entity.y, entity.z, 64.0, entity.level().dimension())
    }

    private fun hit(target: Entity?, strong: Boolean) {
        val entity = target ?: return
        val level = entity.level() as? ServerLevel ?: return
        val y = entity.y + entity.bbHeight * 0.5
        val spread = entity.bbWidth * 0.4
        level.sendParticles(ParticleTypes.CRIT, entity.x, y, entity.z, 20, spread, spread, spread, 0.3)
        if (strong) level.sendParticles(ParticleTypes.ENCHANTED_HIT, entity.x, y, entity.z, 20, spread, spread, spread, 0.3)
        level.chunkSource.broadcast(entity, ClientboundHurtAnimationPacket(entity.id, 0f))
        level.playSound(null, entity.x, entity.y, entity.z, if (strong) SoundEvents.PLAYER_ATTACK_CRIT else SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 1f, 1f)
    }

    /** Standing spot on the ground near this position. */
    private fun ground(level: ServerLevel, pos: Vec3): Vec3 {
        val start = BlockPos.containing(pos.x, pos.y + 3, pos.z)
        for (dy in 0..8) {
            val below = start.below(dy + 1)
            val at = start.below(dy)
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty && level.getBlockState(at).getCollisionShape(level, at).isEmpty) {
                return Vec3(pos.x, at.y.toDouble(), pos.z)
            }
        }
        return pos
    }

    private fun yawTo(from: Vec3, target: Entity): Float =
        (Mth.atan2(target.z - from.z, target.x - from.x) * Mth.RAD_TO_DEG).toFloat() - 90f
}
