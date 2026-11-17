package generations.gg.generations.core.generationscore.common.world.level.block.rks

import com.mojang.serialization.MapCodec
import generations.gg.generations.core.generationscore.common.world.level.block.entities.GenerationsBlockEntities
import generations.gg.generations.core.generationscore.common.world.level.block.generic.GenericRotatableModelBlock
import generations.gg.generations.core.generationscore.common.world.rks.RksSettings
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksLayout
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksMachines
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksModule
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * One of the RKS Machine's five modules (RKS-PLAN.md § 3, D6): a Generations multi-cell block
 * (the same block in each of its cells, real collisions, broken whole, one drop). Its 3D model
 * is drawn by RksMachineRenderer; the console draws the whole machine once it is assembled.
 *
 * - Placed near an incomplete console (assistedPlacementRange), it goes to its place and turns
 *   the console's way when that place is free (D7, RksModuleItem); otherwise it is placed as usual.
 * - Placed or removed: the console it belongs to checks its structure (a scheduled tick).
 * - Right click: the console's menu (the console of an incomplete machine also shows the missing
 *   modules' hologram); the repair kit on a damaged machine starts the repair.
 * - While Mewtwo grows or awakens it cannot be broken in survival (D9); pistons never move it;
 *   explosions do not break it (resistance of obsidian).
 * - The console's item keeps the psychic energy; the chamber's item keeps the damage (no repair
 *   is skipped by taking the machine apart).
 */
open class RksModuleBlock(properties: Properties, val module: RksModule) : GenericRotatableModelBlock(
    properties, model = null, width = module.w - 1, height = module.h - 1, length = module.d - 1,
) {
    override val blockEntityType: Holder<BlockEntityType<*>>
        get() = if (module == RksModule.CONSOLE) GenerationsBlockEntities.RKS_CONSOLE else GenerationsBlockEntities.RKS_MODULE

    override fun codec(): MapCodec<out BaseEntityBlock> = simpleCodec { RksModuleBlock(it, module) }

    fun isBase(state: BlockState): Boolean = getWidthValue(state) == 0 && getHeightValue(state) == 0 && getLengthValue(state) == 0

    /** This cell's place within the module. */
    fun cellOf(state: BlockState): IntArray = intArrayOf(getWidthValue(state), getHeightValue(state), getLengthValue(state))

    public override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = Shapes.block()

    public override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    // ---------------------------------------------------------------- placement

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val level = context.level
        val pos = context.clickedPos
        // The console's way when this is the place an incomplete console waits for it (D7).
        val facing = RksMachines.assistedFacing(level, pos, module) ?: context.horizontalDirection.opposite
        if (pos.y + module.h - 1 >= level.maxBuildHeight || !areaClear(level, pos, facing)) return null
        val state = defaultBlockState().setValue(FACING, facing).setValue(WATERLOGGED, level.getFluidState(pos).type === Fluids.WATER)
        return setSize(state, 0, 0, 0)
    }

    /** Every cell of the module free (replaceable), for that facing. */
    fun areaClear(level: LevelReader, base: BlockPos, facing: Direction): Boolean {
        val o = IntArray(3)
        val f = facing.get2DDataValue()
        for (x in 0 until module.w) for (y in 0 until module.h) for (z in 0 until module.d) {
            RksLayout.offset(f, x, y, z, o)
            val at = base.offset(o[0], o[1], o[2])
            if (!level.getBlockState(at).canBeReplaced()) return false
        }
        return true
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (level.isClientSide) return
        val data = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
        when (val be = level.getBlockEntity(pos)) {
            is RksConsoleBlockEntity -> {
                if (data != null) {
                    be.energy = data.getInt(RksConsoleBlockEntity.ITEM_ENERGY).coerceIn(0, RksSettings.mewtwo.energyRequired)
                    be.fireEnergy = data.getInt(RksConsoleBlockEntity.ITEM_FIRE_ENERGY).coerceIn(0, RksSettings.fire.energyRequired)
                }
                be.setChanged()
                level.scheduleTick(pos, this, 1)
            }
            is RksModuleBlockEntity -> {
                if (data != null && data.getBoolean(RksModuleBlockEntity.ITEM_DAMAGED)) be.setDamaged(true)
                RksMachines.scheduleCheck(level, pos, state)
            }
        }
    }

    /** A scheduled tick: the console checks its structure. */
    public override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        if (module == RksModule.CONSOLE && isBase(state)) (level.getBlockEntity(pos) as? RksConsoleBlockEntity)?.revalidate()
    }

    public override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.`is`(newState.block) && !level.isClientSide && isBase(state)) {
            if (module == RksModule.CONSOLE) (level.getBlockEntity(pos) as? RksConsoleBlockEntity)?.removed()
            else RksMachines.scheduleCheck(level, pos, state)
        }
        super.onRemove(state, level, pos, newState, moved)
    }

    /** The console's energy, the chamber's damage, kept in the dropped item. */
    public override fun getDrops(state: BlockState, params: LootParams.Builder): List<ItemStack> {
        val drops = super.getDrops(state, params)
        val be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
        for (stack in drops) {
            if (!stack.`is`(asItem())) continue
            when (be) {
                is RksConsoleBlockEntity -> if (be.energy > 0 || be.fireEnergy > 0) RksModuleItemData.put(stack) {
                    if (be.energy > 0) it.putInt(RksConsoleBlockEntity.ITEM_ENERGY, be.energy)
                    if (be.fireEnergy > 0) it.putInt(RksConsoleBlockEntity.ITEM_FIRE_ENERGY, be.fireEnergy)
                }
                is RksModuleBlockEntity -> if (be.damaged) RksModuleItemData.put(stack) { it.putBoolean(RksModuleBlockEntity.ITEM_DAMAGED, true) }
            }
        }
        return drops
    }

    // ---------------------------------------------------------------- protection (D9)

    public override fun getDestroyProgress(state: BlockState, player: Player, level: BlockGetter, pos: BlockPos): Float {
        if (RksSettings.machine.protectModulesDuringExperiment && RksMachines.isProtected(level, pos, state)) return 0f
        return super.getDestroyProgress(state, player, level, pos)
    }

    // ---------------------------------------------------------------- use

    public override fun useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): ItemInteractionResult {
        if (!RksMachines.isRepairKit(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        val console = RksMachines.consoleOf(level, pos, state) ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        if (!level.isClientSide) console.repair(player, stack)
        return ItemInteractionResult.sidedSuccess(level.isClientSide)
    }

    public override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        val console = RksMachines.consoleOf(level, pos, state)
        if (console == null) {
            if (!level.isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("generations_core.rks.no_console"), true)
            return InteractionResult.sidedSuccess(level.isClientSide)
        }
        if (level.isClientSide) {
            // The missing modules' hologram, on this client (D7).
            if (!console.state.assembled) console.hologramUntil = level.gameTime + RksSettings.machine.hologramSeconds * 20L
        } else {
            console.openMenu(player)
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (level.isClientSide || module != RksModule.CONSOLE) return null
        @Suppress("UNCHECKED_CAST")
        return createTickerHelper(type, GenerationsBlockEntities.RKS_CONSOLE.value() as BlockEntityType<RksConsoleBlockEntity>) { _, _, _, be -> be.serverTick() }
    }
}

/** The custom data the modules' items keep (minecraft:custom_data). */
object RksModuleItemData {
    fun put(stack: ItemStack, write: (net.minecraft.nbt.CompoundTag) -> Unit) {
        val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: net.minecraft.nbt.CompoundTag()
        write(tag)
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag))
    }

    fun read(stack: ItemStack): net.minecraft.nbt.CompoundTag? = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
}
