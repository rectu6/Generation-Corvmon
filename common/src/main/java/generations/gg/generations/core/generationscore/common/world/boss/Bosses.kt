package generations.gg.generations.core.generationscore.common.world.boss

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Boss Pokémon (BOSS-PLAN.md): a wild Pokémon made bigger, marked by a rarity, that
 * cannot be caught. This object is the API (isBoss, of, make, clear) and the entry
 * point (init). Lot A: data, size, client look (aspects), natural spawns, capture
 * refusal, solo guard, lifetime, config and /boss. Level, Showdown, AI, rewards,
 * traits and Bossdex come with lots B to E.
 *
 * Never uses Pokemon.isAlpha (Cobblemon's own alpha, which ignores scaleModifier):
 * native alphas are not turned into bosses.
 */
object Bosses {
    fun isBoss(pokemon: Pokemon?): Boolean = pokemon != null && BossData.has(pokemon.persistentData)

    fun isBoss(entity: Entity?): Boolean = isBoss((entity as? PokemonEntity)?.pokemon)

    fun of(pokemon: Pokemon?): BossData? = pokemon?.let { BossData.read(it.persistentData) }

    fun of(entity: Entity?): BossData? = of((entity as? PokemonEntity)?.pokemon)

    /** Height of the Pokémon in blocks at a scale modifier (its hitbox, as Cobblemon sizes it). */
    fun heightAt(pokemon: Pokemon, scale: Float): Float = pokemon.form.hitbox.height * pokemon.form.baseScale * scale

    /**
     * Size multiplier of a boss over its original scale: its rarity's (× the Titan's),
     * lowered so that it is at most general.json's maxHeightBlocks tall, never below 1
     * (a species already that tall keeps its size).
     */
    fun scaleFor(pokemon: Pokemon, originalScale: Float, rarity: BossRarity, titan: Boolean): Float {
        val wanted = BossConfig.rarity(rarity).scale * (if (titan) BossConfig.general.titanScaleMultiplier else 1f)
        if (wanted <= 1f) return wanted
        val height = heightAt(pokemon, originalScale)
        if (height <= 0.01f) return wanted
        return max(1f, min(wanted, BossConfig.general.maxHeightBlocks / height))
    }

    /**
     * Makes this Pokémon a boss (or changes an existing boss's rarity): data in
     * persistentData, size, forced aspects for the clients. Call it before the entity
     * is added to the world when possible (it then appears at its size).
     */
    fun make(pokemon: Pokemon, rarity: BossRarity, titan: Boolean, source: String, now: Long): BossData {
        val previous = of(pokemon)
        val original = previous?.originalScale ?: pokemon.scaleModifier
        val data = BossData(
            id = previous?.id ?: UUID.randomUUID(),
            rarity = rarity,
            titan = titan,
            originalScale = original,
            spawnedAt = previous?.spawnedAt ?: now,
            source = previous?.source ?: source,
            megaAspect = BossMegas.of(pokemon)?.aspect ?: previous?.megaAspect ?: "",
            rewarded = previous?.rewarded ?: false,
        )
        data.write(pokemon.persistentData)
        pokemon.scaleModifier = original * scaleFor(pokemon, original, rarity, titan)
        val aspects = pokemon.forcedAspects.filterNot(BossRarity::isBossAspect).toMutableSet()
        aspects += rarity.aspect
        if (titan) aspects += BossRarity.TITAN_ASPECT
        pokemon.forcedAspects = aspects
        pokemon.updateAspects()
        return data
    }

    /** Back to a plain Pokémon: data removed, original size, aspects removed. */
    fun clear(pokemon: Pokemon): Boolean {
        if (!isBoss(pokemon)) return false
        val data = of(pokemon)
        pokemon.persistentData.remove(BossData.KEY)
        if (data != null) pokemon.scaleModifier = data.originalScale
        // A Legendary boss's Mega form goes with it.
        val mega = data?.megaAspect.orEmpty()
        pokemon.forcedAspects = pokemon.forcedAspects.filterNot { BossRarity.isBossAspect(it) || (mega.isNotEmpty() && it == mega) }.toSet()
        pokemon.updateAspects()
        return true
    }

    /** "Charizard", or "Mega Charizard Y" for a boss kept in a Mega form. */
    fun speciesName(pokemon: Pokemon): MutableComponent =
        BossMegas.of(pokemon)?.displayName() ?: pokemon.species.translatedName.copy()

    /** "Epic Boss" in the rarity's colour (server messages). */
    fun tierName(rarity: BossRarity, titan: Boolean = false): MutableComponent {
        val name = Component.translatable(rarity.tierKey)
        if (titan) name.append(Component.literal(" ")).append(Component.translatable("generations_core.boss.titan"))
        return name.withStyle(Style.EMPTY.withColor(BossConfig.rarity(rarity).rgb).withBold(rarity == BossRarity.LEGENDARY))
    }

    fun init() {
        BossConfig.load()
        BossSpawnManager.init()
        BossVictory.init()
        // Edits made while the game was closed apply to the next world; rewards need the registries.
        PlatformEvents.SERVER_STARTED.subscribe(Priority.NORMAL) { event ->
            BossConfig.load()
            BossMegas.invalidate()
            BossRewards.load(event.server.registryAccess())
        }
        PlatformEvents.SERVER_STOPPING.subscribe(Priority.NORMAL) {
            BossRegistry.clear()
            BossSpawnManager.reset()
            BossVictory.reset()
        }
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGH) { ballHit(it) }
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.LOW) { battleStarting(it) }
    }

    /** No boss can be caught: Cobblemon gives the Poké Ball back (dropped where it hit). */
    private fun ballHit(event: ThrownPokeballHitEvent) {
        if (event.isCanceled || !BossConfig.general.refuseCapture || !isBoss(event.pokemon)) return
        event.cancel()
        (event.pokeBall.owner as? ServerPlayer)?.displayClientMessage(
            Component.translatable("generations_core.boss.uncatchable").withStyle(ChatFormatting.RED), true,
        )
    }

    /** One player per boss: Cobblemon refuses a second battle already, this refuses a shared one. */
    private fun battleStarting(event: BattleStartedEvent.Pre) {
        if (event.isCanceled || !BossConfig.general.soloBattlesOnly) return
        val battle = event.battle
        val boss = battle.actors.any { actor -> actor.pokemonList.any { isBoss(it.originalPokemon) || isBoss(it.entity) } }
        if (!boss || battle.players.size <= 1) return
        event.cancel()
        battle.players.forEach {
            it.displayClientMessage(Component.translatable("generations_core.boss.solo_only").withStyle(ChatFormatting.RED), true)
        }
        GenerationsCore.LOGGER.info("Bosses: a battle of {} players against a boss was refused", battle.players.size)
    }
}
