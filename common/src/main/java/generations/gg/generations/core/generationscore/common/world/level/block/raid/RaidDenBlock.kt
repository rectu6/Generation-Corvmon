package generations.gg.generations.core.generationscore.common.world.level.block.raid

import com.mojang.serialization.MapCodec
import generations.gg.generations.core.generationscore.common.world.item.WishingItem
import generations.gg.generations.core.generationscore.common.world.level.block.entities.GenerationsBlockEntities
import generations.gg.generations.core.generationscore.common.world.raid.RaidManager
import generations.gg.generations.core.generationscore.common.world.raid.config.RaidConfig
import generations.gg.generations.core.generationscore.common.world.raid.den.RaidDens
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.StringRepresentable
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Dynamax den, generated in the world: a raid boss appears in it by itself on some
 * mornings (RaidDens), or at once with a Wishing item, shown by a light beam whose
 * colour gives the tier. Clicking an active den opens its lobby. The 3D model, a
 * ring of rocks about 3 blocks wide, is drawn around the block, which is the pit in
 * the middle. Broken, it drops nothing unless denDropsItem is on.
 */
class RaidDenBlock(properties: Properties) : BaseEntityBlock(properties) {

    /**
     * Beam of an active den, with the pit's glow: red, gold for a 6-star legendary.
     * LIME, GREEN, BLUE and PURPLE are former tier colours, kept so that dens saved with
     * them still load; their beam turns red at the next den tick.
     */
    enum class Beam(private val id: String, val color: Int) : StringRepresentable {
        NONE("none", 0),
        LIME("lime", 0xFF8CF02A.toInt()),
        GREEN("green", 0xFF1E8C3A.toInt()),
        BLUE("blue", 0xFF2F6BFF.toInt()),
        RED("red", 0xFFE8304A.toInt()),
        PURPLE("purple", 0xFFA848FF.toInt()),
        GOLD("gold", 0xFFFFC832.toInt());

        override fun getSerializedName(): String = id

        companion object {
            fun forStars(stars: Int): Beam = when {
                stars <= 0 -> NONE
                stars >= 6 -> GOLD
                else -> RED
            }
        }
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(BEAM, Beam.NONE))
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BEAM)
    }

    // The den is the 3D model drawn by RaidDenRenderer; the block model only gives
    // the breaking particles.
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.ENTITYBLOCK_ANIMATED

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = SHAPE

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = RaidDenBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? =
        if (level.isClientSide) null
        else {
            @Suppress("UNCHECKED_CAST")
            val raidDen = GenerationsBlockEntities.RAID_DEN.value() as BlockEntityType<RaidDenBlockEntity>
            createTickerHelper(type, raidDen) { l, pos, s, den -> RaidDenBlockEntity.serverTick(l, pos, s, den) }
        }

    /**
     * A Wishing item on an inactive den summons a raid at once (its tiers in dens.json)
     * and is used up. Any other item, or an active den: the den opens its lobby.
     */
    override fun useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): ItemInteractionResult {
        val wish = stack.item as? WishingItem ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        if (state.getValue(BEAM) != Beam.NONE) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        if (level.isClientSide) return ItemInteractionResult.SUCCESS
        val name = stack.hoverName
        if (!RaidDens.summon(level as ServerLevel, pos, wish.tiers, wish.legendaryChance)) {
            player.displayClientMessage(Component.translatable("generations_core.raid.wish_failed"), true)
            return ItemInteractionResult.CONSUME
        }
        stack.consume(1, player)
        player.displayClientMessage(Component.translatable("generations_core.raid.wish", name), true)
        return ItemInteractionResult.CONSUME
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        // The lobby of an active den; an inactive one says so (or reopens a raid's
        // results not settled yet).
        RaidManager.joinLobby(level as ServerLevel, pos, player as ServerPlayer)
        return InteractionResult.CONSUME
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        // Natural dens give nothing back unless the administrators allow it.
        if (!level.isClientSide && !player.isCreative && RaidConfig.dens.denDropsItem) popResource(level, pos, ItemStack(this))
        return super.playerWillDestroy(level, pos, state, player)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block) && level is ServerLevel) RaidDens.removed(level, pos)
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    companion object {
        @JvmField
        val BEAM: EnumProperty<Beam> = EnumProperty.create("beam", Beam::class.java)
        val CODEC: MapCodec<RaidDenBlock> = simpleCodec(::RaidDenBlock)
        private val SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 6.0, 16.0)

        /** Light of an active den. */
        @JvmStatic
        fun lightLevel(state: BlockState): Int = if (state.getValue(BEAM) == Beam.NONE) 0 else 12
    }
}
