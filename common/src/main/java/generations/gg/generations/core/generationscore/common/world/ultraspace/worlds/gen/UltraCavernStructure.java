package generations.gg.generations.core.generationscore.common.world.ultraspace.worlds.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A jigsaw structure started in a cavern (phase 4.3.1, the Ultra Abyss): never in solid rock.
 * Minecraft's jigsaw (generations_core:ultra_cavern) whose start height is searched in the
 * terrain's own noise, like the Nether fossils do: in the chunk's middle column (or else in one of four
 * columns round it), a floor
 * (solid under, `clearance` blocks of air over it, between min_y and max_y), and around it,
 * `reach` blocks away in the eight directions, at least `open_sides` columns open too (air over
 * the same level, ground a few blocks under). One of the levels found is drawn; none, no start.
 * Water is not air: the flooded halls are left alone.
 *
 * The rest is the vanilla jigsaw (pools, depth, distance, terrain adaptation from the settings).
 * Only vanilla classes: the checks made outside the game run it as it is.
 */
public class UltraCavernStructure extends Structure {
    public static final MapCodec<UltraCavernStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter((UltraCavernStructure s) -> s.startPool),
            Codec.intRange(0, JigsawStructure.MAX_DEPTH).fieldOf("size").forGetter((UltraCavernStructure s) -> s.maxDepth),
            Codec.intRange(1, JigsawStructure.MAX_TOTAL_STRUCTURE_RANGE).fieldOf("max_distance_from_center").forGetter((UltraCavernStructure s) -> s.maxDistance),
            Codec.INT.fieldOf("min_y").forGetter((UltraCavernStructure s) -> s.minY),
            Codec.INT.fieldOf("max_y").forGetter((UltraCavernStructure s) -> s.maxY),
            Codec.intRange(2, 64).optionalFieldOf("clearance", 8).forGetter((UltraCavernStructure s) -> s.clearance),
            Codec.intRange(1, 64).optionalFieldOf("reach", 10).forGetter((UltraCavernStructure s) -> s.reach),
            Codec.intRange(0, 8).optionalFieldOf("open_sides", 5).forGetter((UltraCavernStructure s) -> s.openSides)
    ).apply(instance, UltraCavernStructure::new));

    /** Registered as generations_core:ultra_cavern (UltraStructureTypes). */
    public static final StructureType<UltraCavernStructure> TYPE = () -> CODEC;

    private static final int[][] AROUND = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    /** The columns of the chunk tried for a start, in this order: its middle, then four round it. */
    private static final int[][] CANDIDATES = {{0, 0}, {5, 5}, {-5, -5}, {5, -5}, {-5, 5}};

    private final Holder<StructureTemplatePool> startPool;
    private final int maxDepth;
    private final int maxDistance;
    private final int minY;
    private final int maxY;
    private final int clearance;
    private final int reach;
    private final int openSides;

    public UltraCavernStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int maxDepth, int maxDistance,
                                int minY, int maxY, int clearance, int reach, int openSides) {
        super(settings);
        this.startPool = startPool;
        this.maxDepth = maxDepth;
        this.maxDistance = maxDistance;
        this.minY = minY;
        this.maxY = maxY;
        this.clearance = clearance;
        this.reach = reach;
        this.openSides = openSides;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        for (int[] offset : CANDIDATES) {
            int x = chunk.getMiddleBlockX() + offset[0];
            int z = chunk.getMiddleBlockZ() + offset[1];
            OptionalInt y = floorIn(context, x, z);
            if (y.isPresent()) {
                return JigsawPlacement.addPieces(context, startPool, Optional.empty(), maxDepth, new BlockPos(x, y.getAsInt(), z), false, Optional.empty(),
                        maxDistance, PoolAliasLookup.EMPTY, JigsawStructure.DEFAULT_DIMENSION_PADDING, JigsawStructure.DEFAULT_LIQUID_SETTINGS);
            }
        }
        return Optional.empty();
    }

    /** One of the cavern floors of the column (x, z) open round it, drawn at random; none, empty. */
    private OptionalInt floorIn(GenerationContext context, int x, int z) {
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor height = context.heightAccessor();
        RandomState random = context.randomState();
        NoiseColumn middle = generator.getBaseColumn(x, z, height, random);
        int lo = Math.max(minY, height.getMinBuildHeight() + 2);
        int hi = Math.min(maxY, height.getMaxBuildHeight() - clearance - 1);
        List<Integer> floors = new ArrayList<>();
        for (int y = lo; y <= hi; y++) {
            if (floorAt(middle, y)) floors.add(y);
        }
        if (floors.isEmpty()) return OptionalInt.empty();
        NoiseColumn[] around = new NoiseColumn[AROUND.length];
        for (int i = 0; i < AROUND.length; i++) {
            around[i] = generator.getBaseColumn(x + AROUND[i][0] * reach, z + AROUND[i][1] * reach, height, random);
        }
        List<Integer> levels = new ArrayList<>();
        for (int y : floors) {
            int open = 0;
            for (NoiseColumn column : around) {
                if (openAt(column, y)) open++;
            }
            if (open >= openSides) levels.add(y);
        }
        if (levels.isEmpty()) return OptionalInt.empty();
        return OptionalInt.of(levels.get(context.random().nextInt(levels.size())));
    }

    /** Solid under y, nothing but air from y up to y + clearance - 1. */
    private boolean floorAt(NoiseColumn column, int y) {
        if (!solid(column.getBlock(y - 1))) return false;
        for (int i = 0; i < clearance; i++) {
            if (!column.getBlock(y + i).isAir()) return false;
        }
        return true;
    }

    /** Air over y + 1 to y + 4, ground within four blocks under (the jigsaw's beard fills the rest). */
    private static boolean openAt(NoiseColumn column, int y) {
        for (int i = 1; i <= 4; i++) {
            if (!column.getBlock(y + i).isAir()) return false;
        }
        for (int i = 0; i <= 4; i++) {
            if (solid(column.getBlock(y - i))) return true;
        }
        return false;
    }

    private static boolean solid(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    @Override
    public StructureType<?> type() {
        return TYPE;
    }
}
