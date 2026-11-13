package generations.gg.generations.core.generationscore.common.world.ultraspace.weather

import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.ultraspace.suit.UltraShield
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.damagesource.DamageType

/**
 * How a player escapes a dangerous weather without its shield (UltraShelters): not one
 * roof test for all, each danger has its own refuge.
 */
enum class UltraShelter {
    /** Shade (leaves count) or underground, or in the water: the heatwave. */
    SHADE,
    /** A building or a cave: covered, with walls around or deep under the surface (sandstorm, winds). */
    BUILDING,
    /** A solid roof (neither leaves nor glass) and out of the water: the electric storm. */
    SOLID_ROOF,
    /** A closed refuge, underground or in the water: the thermal eruption. */
    HEAT_REFUGE,
    /** A closed space the mist cannot get into: the toxic mist of the caverns. */
    SEALED,
    /** A roof without leaves over the head: the acid rain. */
    ROOF,
}

/**
 * The fourteen weathers of the Ultra worlds (phase 4.4), two per world: a harmless one
 * and a dangerous one. The ids are saved (ultra_weather.json, the weather state of the
 * save): never rename them.
 *
 * rain / thunder: what Level.getRainLevel / getThunderLevel give in the world at full
 * strength (LevelMixin): the sky's colour, its light and the shaders' rainStrength.
 * precipitation: Minecraft's rain is drawn (LevelRendererMixin), the biomes having none.
 */
enum class UltraWeatherEvent(
    val id: String,
    val world: String,
    val dangerous: Boolean,
    val shelter: UltraShelter? = null,
    /** Damage that grows with the time spent exposed (else the same, periodic). */
    val progressive: Boolean = false,
    val rain: Float = 0f,
    val thunder: Float = 0f,
    val precipitation: Boolean = false,
    /** The event's colour (messages, indicator). */
    val colour: Int,
) {
    TROPICAL_RAIN("tropical_rain", "ultra_jungle", false, rain = 1f, precipitation = true, colour = 0x6FB7E8),
    EXTREME_HEAT("extreme_heat", "ultra_jungle", true, UltraShelter.SHADE, progressive = true, colour = 0xFF8A3D),
    INTENSE_SUN("intense_sun", "ultra_desert", false, colour = 0xFFE27A),
    SANDSTORM("sandstorm", "ultra_desert", true, UltraShelter.BUILDING, progressive = true, rain = 0.35f, colour = 0xE3C07A),
    ENERGY_SURGE("energy_surge", "ultra_central", false, colour = 0x7FD8FF),
    ELECTRIC_STORM("electric_storm", "ultra_central", true, UltraShelter.SOLID_ROOF, rain = 1f, thunder = 1f, colour = 0xFFE14D),
    ASH_RAIN("ash_rain", "ultra_crater", false, rain = 0.3f, colour = 0xA7A2A0),
    THERMAL_ERUPTION("thermal_eruption", "ultra_crater", true, UltraShelter.HEAT_REFUGE, progressive = true, rain = 0.3f, colour = 0xF0552E),
    DIMENSIONAL_MIST("dimensional_mist", "ultra_forest", false, rain = 0.25f, colour = 0xD9CCFF),
    CUTTING_WINDS("cutting_winds", "ultra_forest", true, UltraShelter.BUILDING, progressive = true, rain = 0.2f, colour = 0xC6B5FF),
    CRYSTAL_RAIN("crystal_rain", "ultra_abyss", false, colour = 0x8FF3FF),
    TOXIC_MIST("toxic_mist", "ultra_abyss", true, UltraShelter.SEALED, colour = 0x72E07C),
    DIMENSIONAL_DARKNESS("dimensional_darkness", "ultra_skyscraper", false, rain = 0.8f, thunder = 0.6f, colour = 0x8A6FB8),
    ACID_RAIN("acid_rain", "ultra_skyscraper", true, UltraShelter.ROOF, progressive = true, rain = 1f, thunder = 0.3f, precipitation = true, colour = 0xA5D84A);

    /** The shield that protects against it (dangerous weathers only). */
    val shield: UltraShield? get() = if (dangerous) UltraShield.forHazard(id) else null

    /** Its damage: generations_core:ultra_<id> (data/generations_core/damage_type). */
    val damageType: ResourceKey<DamageType> = ResourceKey.create(Registries.DAMAGE_TYPE, GenerationsCore.id("ultra_$id"))

    fun displayName(): Component = Component.translatable("generations_core.ultra_weather.event.$id").withColor(colour)

    companion object {
        fun byId(id: String?): UltraWeatherEvent? = id?.let { wanted -> entries.firstOrNull { it.id == wanted } }

        fun of(world: String): List<UltraWeatherEvent> = entries.filter { it.world == world }
    }
}
