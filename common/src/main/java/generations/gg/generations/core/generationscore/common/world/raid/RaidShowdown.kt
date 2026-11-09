package generations.gg.generations.core.generationscore.common.world.raid

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.ShowdownInterpreter
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import generations.gg.generations.core.generationscore.common.GenerationsCore
import java.lang.reflect.Method

/**
 * Raid rules inside Cobblemon's Showdown: resources/generations_core/showdown/raid.js
 * (participants, boss targets, shield, extra attacks, shockwaves, cheers, revival,
 * fail conditions). It is loaded into Cobblemon's GraalJS context the first time a
 * raid starts. The server drives each battle through GenerationsRaid (start, plan,
 * apply, state, cheer, leave, finish); its "|gcraid|..." lines come back through
 * RaidInstruction.
 */
object RaidShowdown {
    private val script: String by lazy {
        RaidShowdown::class.java.getResourceAsStream("/generations_core/showdown/raid.js")!!.use { it.readBytes().decodeToString() }
    }

    fun init() {
        ShowdownInterpreter.registerUpdateInstructionParser("gcraid") { _, _, message, _ -> RaidInstruction(message) }
    }

    fun start(battle: PokemonBattle, data: JsonObject): Boolean = call(battle, "start", data.toString()) != null

    /** The boss's actions this turn (host battle). */
    fun plan(battle: PokemonBattle): JsonObject? = json(call(battle, "plan", json = true))

    fun apply(battle: PokemonBattle, shared: JsonObject?, plan: JsonObject?) {
        call(battle, "apply", "${shared ?: "null"}, ${plan ?: "null"}")
    }

    fun state(battle: PokemonBattle): JsonObject? = json(call(battle, "state", json = true))

    fun cheer(battle: PokemonBattle, slot: Int, effect: String, stat: String) {
        call(battle, "cheer", "$slot, '${effect.filter(Char::isLetter)}', '${stat.filter(Char::isLetter)}'")
    }

    fun leave(battle: PokemonBattle, slot: Int) {
        call(battle, "leave", slot.toString())
    }

    /** Whether Showdown still waits for the choice of this side of the battle (p1, p2). */
    fun waiting(battle: PokemonBattle, side: String): Boolean =
        call(battle, "waiting", "'${side.filter(Char::isLetterOrDigit)}'", json = true) == "true"

    fun finish(battle: PokemonBattle, result: String) {
        call(battle, "finish", "'${result.filter(Char::isLetter)}'")
    }

    /** One Pokémon as a Cobblemon packed team, the format Showdown receives for real battles. */
    fun pack(pokemon: Pokemon): String = with(BattleRegistry) { listOf(BattlePokemon(pokemon)).packTeam() }

    private fun json(text: String?): JsonObject? =
        text?.takeIf { it.startsWith("{") }?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }

    /**
     * Calls GenerationsRaid.<function>(battle, arguments) in Showdown, with the
     * script loaded first. Returns the result (as JSON when asked), null on failure.
     */
    private fun call(battle: PokemonBattle, function: String, arguments: String = "", json: Boolean = false): String? {
        var result: String? = null
        Cobblemon.showdownThread.queue { service ->
            // The socket service only exists in development: no raid rules there.
            if (service !is GraalShowdownService) return@queue
            try {
                val context = context(service)
                // The script once per Showdown context (a new one after /reloadshowdown).
                if (context !== loadedIn) {
                    if (eval(context, "typeof GenerationsRaid").toString() == "undefined") eval(context, script)
                    loadedIn = context
                }
                val target = "GenerationsRaid.battleOf('${battle.battleId}')"
                val call = "GenerationsRaid.$function($target${if (arguments.isEmpty()) "" else ", $arguments"})"
                val value = eval(context, if (json) "JSON.stringify($call)" else "($call, 'ok')")
                result = text(value)
            } catch (e: Exception) {
                loadedIn = null
                GenerationsCore.LOGGER.error("Raid rules: GenerationsRaid.{} failed", function, e)
            }
        }
        return result
    }

    /** The Showdown context the raid script was last loaded into. */
    private var loadedIn: Any? = null
    private var contextGetter: Method? = null
    private var evalMethod: Method? = null

    private fun context(service: GraalShowdownService): Any {
        val getter = contextGetter ?: service.javaClass.getMethod("getContext").also { contextGetter = it }
        return getter.invoke(service)
    }

    /**
     * Runs JavaScript in Cobblemon's Showdown context. GraalVM is only bundled with
     * Cobblemon at runtime, hence the reflection (looked up once): Context.eval("js",
     * code) gives a Value.
     */
    private fun eval(context: Any, code: String): Any? {
        val method = evalMethod?.takeIf { it.declaringClass.isInstance(context) }
            ?: context.javaClass.getMethod("eval", String::class.java, CharSequence::class.java).also { evalMethod = it }
        return method.invoke(context, "js", code)
    }

    private var isStringMethod: Method? = null
    private var asStringMethod: Method? = null

    /** A Graal Value as text: its string when it is one. */
    private fun text(value: Any?): String? {
        value ?: return null
        val isString = runCatching {
            val method = isStringMethod?.takeIf { it.declaringClass.isInstance(value) } ?: value.javaClass.getMethod("isString").also { isStringMethod = it }
            method.invoke(value) as Boolean
        }.getOrDefault(false)
        if (!isString) return value.toString()
        val method = asStringMethod?.takeIf { it.declaringClass.isInstance(value) } ?: value.javaClass.getMethod("asString").also { asStringMethod = it }
        return method.invoke(value) as String
    }
}
