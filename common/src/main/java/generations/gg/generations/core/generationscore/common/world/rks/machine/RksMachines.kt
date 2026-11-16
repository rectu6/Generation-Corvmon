package generations.gg.generations.core.generationscore.common.world.rks.machine

import generations.gg.generations.core.generationscore.common.compat.jei.asValue
import generations.gg.generations.core.generationscore.common.world.item.GenerationsItems
import generations.gg.generations.core.generationscore.common.world.level.block.generic.GenericRotatableModelBlock
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksConsoleBlockEntity
import generations.gg.generations.core.generationscore.common.world.level.block.rks.RksModuleBlock
import generations.gg.generations.core.generationscore.common.world.recipe.GenerationsCoreRecipeTypes
import generations.gg.generations.core.generationscore.common.world.recipe.RksProtocolRecipe
import generations.gg.generations.core.generationscore.common.world.recipe.RksRecipe
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/**
 * The RKS Machine's world side (RKS-PLAN.md § 3): which console a module belongs to, where an
 * assisted placement puts a module (D7), the protection of the modules (D9), the machine's
 * points in the world.
 */
object RksMachines {
    fun facingIndex(state: BlockState): Int = state.getValue(GenericRotatableModelBlock.FACING).get2DDataValue()

    /** The console a module's cell belongs to, when it stands where the module's place and facing say. */
    fun consoleOf(level: BlockGetter, pos: BlockPos, state: BlockState): RksConsoleBlockEntity? {
        val block = state.block as? RksModuleBlock ?: return null
        val base = block.getBaseBlockPos(pos, state)
        if (block.module == RksModule.CONSOLE) return level.getBlockEntity(base) as? RksConsoleBlockEntity
        val f = facingIndex(state)
        val o = RksLayout.consoleFrom(block.module, f)
        val console = level.getBlockEntity(base.offset(o[0], o[1], o[2])) as? RksConsoleBlockEntity ?: return null
        return if (console.facingIndex == f) console else null
    }

    /** A module (its first cell at base) was placed or removed: its console checks its structure next tick. */
    fun scheduleCheck(level: Level, base: BlockPos, state: BlockState) {
        val block = state.block as? RksModuleBlock ?: return
        val o = RksLayout.consoleFrom(block.module, facingIndex(state))
        val consolePos = base.offset(o[0], o[1], o[2])
        val consoleState = level.getBlockState(consolePos)
        val console = consoleState.block as? RksModuleBlock ?: return
        if (console.module != RksModule.CONSOLE || !console.isBase(consoleState)) return
        level.scheduleTick(consolePos, console, 1)
    }

    private fun waitingConsole(level: BlockGetter, consolePos: BlockPos, facing: Int, module: RksModule): RksConsoleBlockEntity? {
        val console = level.getBlockEntity(consolePos) as? RksConsoleBlockEntity ?: return null
        if (console.facingIndex != facing || console.state.assembled || console.missingMask and module.bit == 0) return null
        return console
    }

    /** The facing to give a module placed at pos when an incomplete console waits for it exactly there. */
    fun assistedFacing(level: BlockGetter, pos: BlockPos, module: RksModule): Direction? {
        if (module == RksModule.CONSOLE) return null
        val o = IntArray(3)
        for (f in 0..3) {
            RksLayout.consoleFrom(module, f, o)
            if (waitingConsole(level, pos.offset(o[0], o[1], o[2]), f, module) != null) return Direction.from2DDataValue(f)
        }
        return null
    }

    /**
     * Assisted placement (D7): the place of that module for the nearest incomplete console within
     * range that waits for it, when every cell of it is free. Null: placed as usual.
     */
    fun assistedTarget(level: Level, clicked: BlockPos, module: RksModule, range: Int): BlockPos? {
        if (module == RksModule.CONSOLE || range <= 0) return null
        var best: BlockPos? = null
        var bestDistance = Double.MAX_VALUE
        val reach = range + 2
        val cursor = BlockPos.MutableBlockPos()
        val o = IntArray(3)
        for (dy in -2..2) for (dx in -reach..reach) for (dz in -reach..reach) {
            cursor.set(clicked.x + dx, clicked.y + dy, clicked.z + dz)
            val state = level.getBlockState(cursor)
            val block = state.block as? RksModuleBlock ?: continue
            if (block.module != RksModule.CONSOLE || !block.isBase(state)) continue
            val distance = cursor.distSqr(clicked)
            if (distance > (range + 0.5) * (range + 0.5) || distance >= bestDistance) continue
            val f = facingIndex(state)
            if (waitingConsole(level, cursor, f, module) == null) continue
            RksLayout.offset(f, module.ox, module.oy, module.oz, o)
            val target = cursor.offset(o[0], o[1], o[2])
            val moduleBlock = moduleBlock(module) ?: continue
            if (!moduleBlock.areaClear(level, target, Direction.from2DDataValue(f))) continue
            best = target
            bestDistance = distance
        }
        return best
    }

    fun moduleBlock(module: RksModule): RksModuleBlock? =
        generations.gg.generations.core.generationscore.common.world.level.block.GenerationsUtilityBlocks.RKS_MODULES[module]?.value() as? RksModuleBlock

    /** The module's machine is growing or awakening Mewtwo (or being repaired): not broken in survival. */
    fun isProtected(level: BlockGetter, pos: BlockPos, state: BlockState): Boolean =
        consoleOf(level, pos, state)?.state?.protectsModules == true

    fun isRepairKit(stack: ItemStack): Boolean = stack.`is`(GenerationsItems.RKS_REPAIR_KIT)

    /** The bottom centre of the chamber: the 3D model's and the awakening scene's origin. */
    fun origin(consolePos: BlockPos, facing: Int): Vec3 {
        val o = RksLayout.offset(facing, RksModule.CHAMBER.ox, 0, RksModule.CHAMBER.oz)
        return Vec3(consolePos.x + o[0] + 0.5, consolePos.y.toDouble(), consolePos.z + o[2] + 0.5)
    }

    /** The RKS recipes of the active categories, in the menu's order. */
    fun recipes(level: Level): List<RecipeHolder<RksProtocolRecipe>> {
        @Suppress("UNCHECKED_CAST")
        return level.recipeManager.getAllRecipesFor(GenerationsCoreRecipeTypes.RKS.asValue<RksRecipe>())
            .filter { it.value() is RksProtocolRecipe }
            .map { it as RecipeHolder<RksProtocolRecipe> }
            .sortedWith(compareBy({ it.value().category.ordinal }, { it.value().order }, { it.id().toString() }))
    }

    fun recipe(level: Level, id: net.minecraft.resources.ResourceLocation): RecipeHolder<RksProtocolRecipe>? {
        val holder = level.recipeManager.byKey(id).orElse(null) ?: return null
        @Suppress("UNCHECKED_CAST")
        return if (holder.value() is RksProtocolRecipe) holder as RecipeHolder<RksProtocolRecipe> else null
    }

    /** The cloning protocol's recipe (protocol "mewtwo"). */
    fun mewtwoRecipe(level: Level): RecipeHolder<RksProtocolRecipe>? = recipes(level).firstOrNull { it.value().isMewtwo }
}
