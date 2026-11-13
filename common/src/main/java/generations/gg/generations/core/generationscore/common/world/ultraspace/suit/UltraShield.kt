package generations.gg.generations.core.generationscore.common.world.ultraspace.suit

import com.mojang.serialization.Codec
import io.netty.buffer.ByteBuf
import generations.gg.generations.core.generationscore.common.util.StreamCodecs.asRegistryFriendly
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.StringRepresentable

/**
 * The seven environmental shields of the Ultra Suit (phase 4.5), one per Ultra world:
 * each one protects only against the dangerous weather of its world (UltraWeatherEvent).
 * They are installed on the suit's existing pieces (the ultra_shields component), at
 * the smithing table; the seven can sit on the same piece.
 *
 * The ids are saved in the pieces' data: never rename them.
 */
enum class UltraShield(val id: String, val world: String, val hazard: String, val colour: Int) : StringRepresentable {
    THERMAL("thermal", "ultra_jungle", "extreme_heat", 0xFF8A3D),
    SAND("sand", "ultra_desert", "sandstorm", 0xE3C07A),
    INSULATING("insulating", "ultra_central", "electric_storm", 0xFFE14D),
    VOLCANIC("volcanic", "ultra_crater", "thermal_eruption", 0xF0552E),
    AERODYNAMIC("aerodynamic", "ultra_forest", "cutting_winds", 0xC6B5FF),
    FILTRATION("filtration", "ultra_abyss", "toxic_mist", 0x72E07C),
    ANTICORROSION("anticorrosion", "ultra_skyscraper", "acid_rain", 0xA5D84A);

    /** The shield's item: generations_core:<id>_shield. */
    val itemName: String get() = "${id}_shield"

    override fun getSerializedName(): String = id

    /** Its short name (Thermique...), coloured like its world. */
    fun displayName(): Component = Component.translatable("generations_core.ultra_shield.$id").withColor(colour)

    companion object {
        val CODEC: Codec<UltraShield> = StringRepresentable.fromEnum { entries.toTypedArray() }

        /** The installed shields, in the enum's order, without duplicates. */
        val LIST_CODEC: Codec<List<UltraShield>> = CODEC.listOf().xmap({ normalise(it) }, { it })

        val LIST_STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, List<UltraShield>> =
            ByteBufCodecs.VAR_INT.map({ entries[it.coerceIn(0, entries.size - 1)] }, { it.ordinal })
                .apply(ByteBufCodecs.list<ByteBuf, UltraShield>()).map({ normalise(it) }, { it }).asRegistryFriendly()

        fun byId(id: String): UltraShield? = entries.firstOrNull { it.id == id }

        /** The shield of a world's dangerous weather (null for a world without one). */
        fun forHazard(hazard: String): UltraShield? = entries.firstOrNull { it.hazard == hazard }

        fun normalise(list: Collection<UltraShield>): List<UltraShield> = entries.filter { it in list }
    }
}
