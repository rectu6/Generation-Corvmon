package generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.Optional;

/**
 * Minecraft's random spread placement (one try per cell of `spacing` chunks, at least
 * `separation` apart) for the Ultra Space structures (phase 4.3.1), with its numbers
 * read from the server's ultra_structures.json under the key `config`: the values in
 * the structure set's JSON are the default ones. A set can also be switched off there.
 * As for any placement, a change applies to the chunks generated from then on.
 *
 * It extends the vanilla class: /locate and the exclusion zones of other sets treat it
 * like any random spread. In Java because the vanilla codec helper is protected static.
 */
public class UltraSpreadPlacement extends RandomSpreadStructurePlacement {
    /** The fields this placement adds to the common ones (written at the same level of the JSON). */
    private record Spread(int spacing, int separation, RandomSpreadType spreadType, String config) {
        static final MapCodec<Spread> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(1, 4096).fieldOf("spacing").forGetter(Spread::spacing),
                Codec.intRange(0, 4096).fieldOf("separation").forGetter(Spread::separation),
                RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(Spread::spreadType),
                Codec.STRING.fieldOf("config").forGetter(Spread::config)
        ).apply(instance, Spread::new));
    }

    public static final MapCodec<UltraSpreadPlacement> CODEC = RecordCodecBuilder.<UltraSpreadPlacement>mapCodec(instance -> placementCodec(instance)
            .and(Spread.CODEC.forGetter((UltraSpreadPlacement p) -> new Spread(p.defaultSpacing, p.defaultSeparation, p.spreadType(), p.config)))
            .apply(instance, (offset, method, frequency, salt, exclusion, spread) ->
                    new UltraSpreadPlacement(offset, method, frequency, salt, exclusion, spread.spacing(), spread.separation(), spread.spreadType(), spread.config())));

    /** Registered as generations_core:ultra_spread (UltraStructurePlacements). */
    public static final StructurePlacementType<UltraSpreadPlacement> TYPE = () -> CODEC;

    private final int defaultSpacing;
    private final int defaultSeparation;
    private final String config;

    public UltraSpreadPlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt,
                                Optional<ExclusionZone> exclusionZone, int spacing, int separation, RandomSpreadType spreadType, String config) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, spacing, Math.min(separation, spacing - 1), spreadType);
        this.defaultSpacing = spacing;
        this.defaultSeparation = Math.min(separation, spacing - 1);
        this.config = config;
        UltraStructuresConfig.INSTANCE.declare(config, this.defaultSpacing, this.defaultSeparation);
    }

    @Override
    public int spacing() {
        return UltraStructuresConfig.INSTANCE.spacing(config, defaultSpacing);
    }

    @Override
    public int separation() {
        return Math.min(UltraStructuresConfig.INSTANCE.separation(config, defaultSeparation), spacing() - 1);
    }

    /** The vanilla computation, with the config's numbers. */
    @Override
    public ChunkPos getPotentialStructureChunk(long seed, int regionX, int regionZ) {
        int spacing = spacing();
        int separation = separation();
        int cellX = Math.floorDiv(regionX, spacing);
        int cellZ = Math.floorDiv(regionZ, spacing);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, cellX, cellZ, salt());
        int range = spacing - separation;
        int x = spreadType().evaluate(random, range);
        int z = spreadType().evaluate(random, range);
        return new ChunkPos(cellX * spacing + x, cellZ * spacing + z);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
        if (!UltraStructuresConfig.INSTANCE.enabled(config)) return false;
        ChunkPos chunk = getPotentialStructureChunk(state.getLevelSeed(), x, z);
        return chunk.x == x && chunk.z == z;
    }

    @Override
    public StructurePlacementType<?> type() {
        return TYPE;
    }
}
