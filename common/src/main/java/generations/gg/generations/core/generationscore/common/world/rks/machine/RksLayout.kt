package generations.gg.generations.core.generationscore.common.world.rks.machine

/**
 * The RKS Machine's five modules (RKS-PLAN.md § 3.1, decision D6), pure (tested off-game by
 * tools/CinematicBench.java). The machine's frame: x to the right as seen from the front, y up,
 * z toward the back; the console's first cell is (0, 0, 0). Seen from above, the console in front:
 *
 *        x=0            x=1              x=2
 *   z=2  [Power]      [Reactor]        [Analyser]
 *   z=1  [Power]      [Chamber]        [Analyser]
 *   z=0  [Console ════════════════════════════]
 *
 * Each module is one Generations multi-cell block (w × h × d cells, the same block in each cell):
 * its cell (cx, cy, cz) lies at the machine's (ox + cx, oy + cy, oz + cz), exactly where
 * GenericRotatableModelBlock puts it when the module faces the machine's way.
 */
enum class RksModule(val id: String, val ox: Int, val oy: Int, val oz: Int, val w: Int, val h: Int, val d: Int) {
    CONSOLE("rks_control_console", 0, 0, 0, 3, 1, 1),
    CHAMBER("rks_containment_chamber", 1, 0, 1, 1, 4, 1),
    REACTOR("rks_reactor", 1, 0, 2, 1, 4, 1),
    POWER("rks_power_module", 0, 0, 1, 1, 2, 2),
    ANALYZER("rks_genetic_analyzer", 2, 0, 1, 1, 2, 2);

    val cells: Int get() = w * h * d

    /** Its bit in a module mask. */
    val bit: Int get() = 1 shl ordinal

    val translationKey: String get() = "block.generations_core.$id"

    companion object {
        val ALL_MASK: Int = entries.fold(0) { mask, module -> mask or module.bit }

        fun byId(id: String): RksModule? = entries.firstOrNull { it.id == id }

        fun of(mask: Int): List<RksModule> = entries.filter { mask and it.bit != 0 }
    }
}

/**
 * Where the machine's cells are in the world, for each of the four horizontal facings (the way
 * the machine looks, toward the player): Minecraft's 2D data values, 0 south, 1 west, 2 north,
 * 3 east. The right is the facing turned counter-clockwise (south → east), the back its opposite.
 */
object RksLayout {
    private val FORWARD_X = intArrayOf(0, -1, 0, 1)
    private val FORWARD_Z = intArrayOf(1, 0, -1, 0)

    /** The cells every module fills: 19 of the 3 × 4 × 3 box. */
    val CELLS: Int = RksModule.entries.sumOf { it.cells }

    fun forwardX(facing: Int): Int = FORWARD_X[facing and 3]
    fun forwardZ(facing: Int): Int = FORWARD_Z[facing and 3]
    fun rightX(facing: Int): Int = FORWARD_Z[facing and 3]
    fun rightZ(facing: Int): Int = -FORWARD_X[facing and 3]

    /** The world offset (dx, dy, dz) of the machine's cell (x, y, z) from the console's first cell. */
    fun offset(facing: Int, x: Int, y: Int, z: Int, out: IntArray = IntArray(3)): IntArray {
        out[0] = x * rightX(facing) - z * forwardX(facing)
        out[1] = y
        out[2] = x * rightZ(facing) - z * forwardZ(facing)
        return out
    }

    /** The module whose cell (x, y, z) of the machine's frame is, or null (a free cell of the box). */
    fun moduleAt(x: Int, y: Int, z: Int): RksModule? = RksModule.entries.firstOrNull {
        x - it.ox in 0 until it.w && y - it.oy in 0 until it.h && z - it.oz in 0 until it.d
    }

    /** What the world holds at a cell: the module it belongs to, its cell within it, its facing. */
    class Cell(val module: RksModule, val cx: Int, val cy: Int, val cz: Int, val facing: Int)

    /**
     * The modules missing (or misplaced, or turned the wrong way) around a console at (x, y, z)
     * facing that way; lookup reads the world at a position. Empty: the machine is complete.
     * The console itself is not checked (it is the one asking).
     */
    fun missing(facing: Int, x: Int, y: Int, z: Int, lookup: (Int, Int, Int) -> Cell?): List<RksModule> {
        val out = ArrayList<RksModule>()
        val o = IntArray(3)
        for (module in RksModule.entries) {
            if (module == RksModule.CONSOLE) continue
            var ok = true
            loop@ for (cx in 0 until module.w) for (cy in 0 until module.h) for (cz in 0 until module.d) {
                offset(facing, module.ox + cx, module.oy + cy, module.oz + cz, o)
                val cell = lookup(x + o[0], y + o[1], z + o[2])
                if (cell == null || cell.module != module || cell.facing != facing || cell.cx != cx || cell.cy != cy || cell.cz != cz) {
                    ok = false
                    break@loop
                }
            }
            if (!ok) out += module
        }
        return out
    }

    /** The console's first cell (world offset from a module's first cell), for that facing. */
    fun consoleFrom(module: RksModule, facing: Int, out: IntArray = IntArray(3)): IntArray {
        offset(facing, module.ox, module.oy, module.oz, out)
        out[0] = -out[0]
        out[1] = -out[1]
        out[2] = -out[2]
        return out
    }
}
