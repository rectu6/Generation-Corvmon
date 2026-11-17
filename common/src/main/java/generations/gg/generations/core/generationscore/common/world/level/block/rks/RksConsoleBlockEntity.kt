package generations.gg.generations.core.generationscore.common.world.level.block.rks

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Pokemon
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicPlaces
import generations.gg.generations.core.generationscore.common.world.cinematic.CinematicScenes
import generations.gg.generations.core.generationscore.common.world.cinematic.LegendaryCinematics
import generations.gg.generations.core.generationscore.common.world.container.ExtendedMenuProvider
import generations.gg.generations.core.generationscore.common.world.entity.block.PokemonUtil
import generations.gg.generations.core.generationscore.common.world.level.block.entities.GenerationsBlockEntities
import generations.gg.generations.core.generationscore.common.world.level.block.entities.SimpleBlockEntity
import generations.gg.generations.core.generationscore.common.world.level.block.generic.GenericRotatableModelBlock
import generations.gg.generations.core.generationscore.common.world.recipe.FossilResult
import generations.gg.generations.core.generationscore.common.world.recipe.ItemResult
import generations.gg.generations.core.generationscore.common.world.recipe.PokemonResult
import generations.gg.generations.core.generationscore.common.world.recipe.RksMachineHost
import generations.gg.generations.core.generationscore.common.world.recipe.RksProtocolRecipe
import generations.gg.generations.core.generationscore.common.world.rks.MewtwoConfig
import generations.gg.generations.core.generationscore.common.world.rks.RksEnergy
import generations.gg.generations.core.generationscore.common.world.rks.RksSettings
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksConsoleMenu
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksLayout
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksMachines
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksModule
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksState
import generations.gg.generations.core.generationscore.common.world.rks.machine.RksStateMachine
import generations.gg.generations.core.generationscore.common.util.getPokemon
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.Containers
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Optional
import java.util.UUID

/**
 * The RKS Machine's master (RKS-PLAN.md § 3.2): the control console keeps the machine's facing
 * (its block's), its modules (checked at each placement or removal, and at load), its state
 * (RksStateMachine), the psychic and fire energies (two separate reserves, HOOH-PLAN.md § 9),
 * the slots and the experiment.
 *
 * Slots: 0 to 8 the inputs (any order, counted ingredients), 9 the output, 10 to 12 the research
 * files M-01, M-02, M-03 (open once the samples are analysed).
 *
 * Sent to the clients only when something changes (never each tick): the clients compute the
 * progress from the state's start tick and the growth's speed. Nothing runs each tick unless the
 * machine works.
 */
class RksConsoleBlockEntity(pos: BlockPos, state: BlockState) :
    SimpleBlockEntity(GenerationsBlockEntities.RKS_CONSOLE, pos, state), Container, ExtendedMenuProvider, RksMachineHost {

    val machine = RksStateMachine()
    val state: RksState get() = machine.state

    /** The psychic energy (Mewtwo's protocol). */
    var energy = 0
    /** The fire energy (the Sacred Ash), kept apart from the psychic one. */
    var fireEnergy = 0
    val items: NonNullList<ItemStack> = NonNullList.withSize(SLOTS, ItemStack.EMPTY)

    fun energyOf(kind: RksEnergy): Int = when (kind) {
        RksEnergy.PSYCHIC -> energy
        RksEnergy.FIRE -> fireEnergy
    }

    /** Sets a reserve (capped by its rules); the caller syncs. */
    fun setEnergy(kind: RksEnergy, value: Int) {
        val v = value.coerceIn(0, kind.required)
        when (kind) {
            RksEnergy.PSYCHIC -> energy = v
            RksEnergy.FIRE -> fireEnergy = v
        }
    }

    /** The craft under way. */
    var craftRecipe: ResourceLocation? = null
        private set
    /** Who launched the craft or the experiment. */
    var starter: UUID? = null
        private set
    var missingMask: Int = RksModule.ALL_MASK and RksModule.CONSOLE.bit.inv()
        private set
    /** The server's game time when this was sent (the clients' estimate of the growth starts there). */
    var syncTime = 0L
        private set

    /** The awakening's cinematic (not saved: a scene does not survive a restart). */
    private var session: UUID? = null
    private var checked = false
    private var lastTick = -1L
    override var capsulePokemon: Optional<Pokemon> = Optional.empty()

    // Client side.
    /** The missing modules' hologram shows until then (game time, D7). */
    var hologramUntil = 0L

    val facing: Direction get() = blockState.getValue(GenericRotatableModelBlock.FACING)
    val facingIndex: Int get() = facing.get2DDataValue()

    /** The chamber's bottom centre (the model's and the scene's origin). */
    val origin: Vec3 get() = RksMachines.origin(blockPos, facingIndex)

    val missing: List<RksModule> get() = RksModule.of(missingMask)

    // ---------------------------------------------------------------- structure

    private fun cellAt(level: Level, x: Int, y: Int, z: Int): RksLayout.Cell? {
        val pos = BlockPos(x, y, z)
        if (!level.isLoaded(pos)) return null
        val state = level.getBlockState(pos)
        val block = state.block as? RksModuleBlock ?: return null
        val cell = block.cellOf(state)
        return RksLayout.Cell(block.module, cell[0], cell[1], cell[2], RksMachines.facingIndex(state))
    }

    /** The chamber's block entity, when the machine has one in place. */
    fun chamber(): RksModuleBlockEntity? {
        val level = level ?: return null
        val o = RksLayout.offset(facingIndex, RksModule.CHAMBER.ox, RksModule.CHAMBER.oy, RksModule.CHAMBER.oz)
        val be = level.getBlockEntity(blockPos.offset(o[0], o[1], o[2])) as? RksModuleBlockEntity ?: return null
        return if (be.module == RksModule.CHAMBER) be else null
    }

    /**
     * Checks the five modules (each placement or removal, and once after loading). False, and
     * nothing changed, while a chunk of the machine is not loaded yet (it is checked again later).
     */
    fun revalidate(): Boolean {
        val level = level as? ServerLevel ?: return false
        val o = IntArray(3)
        for (x in 0..2) for (z in 0..2) {
            RksLayout.offset(facingIndex, x, 0, z, o)
            if (!level.isLoaded(blockPos.offset(o[0], 0, o[2]))) return false
        }
        val missing = RksLayout.missing(facingIndex, blockPos.x, blockPos.y, blockPos.z) { x, y, z -> cellAt(level, x, y, z) }
        var mask = 0
        for (m in missing) mask = mask or m.bit
        val complete = mask == 0
        val before = machine.state
        if (complete && !machine.assembled) machine.damaged = chamber()?.damaged == true
        val event = machine.structure(complete, level.gameTime, (RksSettings.machine.assemblySeconds * 20).toInt())
        if (event == RksStateMachine.Event.CANCELLED) craftRecipe = null
        if (mask != missingMask || machine.state != before) {
            missingMask = mask
            if (machine.state == RksState.ASSEMBLING) level.playSound(null, blockPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1.3f)
            if (machine.state == RksState.INCOMPLETE && before != RksState.INCOMPLETE) level.playSound(null, blockPos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1f, 1.2f)
            changed()
        }
        return true
    }

    /** The console is being removed: its slots fall (the energy went into its item, RksModuleBlock.getDrops). */
    fun removed() {
        val level = level ?: return
        Containers.dropContents(level, blockPos, items)
        items.clear()
    }

    // ---------------------------------------------------------------- ticking

    fun serverTick() {
        val level = level as? ServerLevel ?: return
        if (!checked) checked = revalidate()
        val state = machine.state
        if (state == RksState.IDLE || state == RksState.INCOMPLETE || state == RksState.DAMAGED) return
        val now = level.gameTime
        val world = RksSettings.mewtwo.growthClock == MewtwoConfig.CLOCK_WORLD
        val delta = if (world && lastTick >= 0) (now - lastTick).toDouble().coerceIn(0.0, 20.0 * 3600.0 * 24.0) else 1.0
        lastTick = now
        val running = session?.let { LegendaryCinematics.session(it) != null } == true
        when (machine.tick(now, delta, running)) {
            RksStateMachine.Event.ASSEMBLED -> {
                level.playSound(null, blockPos, SoundEvents.CONDUIT_ACTIVATE, SoundSource.BLOCKS, 1f, 1.1f)
                changed()
            }
            RksStateMachine.Event.CRAFT_DONE -> if (now % 20 == 0L || machine.duration > 0 && now - machine.since == machine.duration.toLong()) finishCraft(level)
            RksStateMachine.Event.ANALYSED -> {
                level.playSound(null, blockPos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, 1.4f)
                changed()
            }
            RksStateMachine.Event.READY -> if (now % 20 == 0L) tryAwaken(level)
            RksStateMachine.Event.SPAWN -> spawnMewtwo(level)
            RksStateMachine.Event.FINISHED -> {
                session = null
                chamber()?.setDamaged(true)
                level.playSound(null, blockPos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f)
                changed()
            }
            RksStateMachine.Event.REPAIRED -> {
                chamber()?.setDamaged(false)
                level.playSound(null, blockPos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2f, 1.2f)
                changed()
            }
            else -> {}
        }
        if (machine.state == RksState.CRAFTING && (now - machine.since) % 60 == 0L) {
            level.playSound(null, blockPos, generations.gg.generations.core.generationscore.common.world.sound.GenerationsSounds.RKS_MACHINE.value(), SoundSource.BLOCKS, 1f, 1f)
        }
    }

    /** Sent to the clients, saved. */
    fun changed() {
        syncTime = level?.gameTime ?: 0L
        sync()
    }

    private fun message(player: Player?, key: String, vararg args: Any, colour: ChatFormatting = ChatFormatting.RED) {
        player?.displayClientMessage(Component.translatable("generations_core.rks.$key", *args).withStyle(colour), true)
    }

    // ---------------------------------------------------------------- crafting (D16)

    private fun inputs(): MutableList<ItemStack> = items.subList(0, INPUTS)

    /** Whether the output slot can take that item result. */
    private fun outputFits(result: ItemStack): Boolean {
        val output = items[OUTPUT]
        if (output.isEmpty || result.isEmpty) return true
        return ItemStack.isSameItemSameComponents(output, result) && output.count + result.count <= output.maxStackSize
    }

    /** "Lancer": a standard recipe (validated here; revision: the menu's view). */
    fun launch(player: ServerPlayer, id: ResourceLocation, revision: Int) {
        val level = level as? ServerLevel ?: return
        if (machine.state == RksState.DAMAGED) return message(player, "error.damaged")
        if (machine.state != RksState.IDLE) return message(player, "error.busy")
        val holder = RksMachines.recipe(level, id) ?: return message(player, "error.no_recipe")
        val recipe = holder.value()
        if (recipe.isMewtwo) return message(player, "error.protocol")
        if (!RksSettings.allows(recipe)) return message(player, "error.category_off")
        if (recipe.allocate(inputs()) == null) return message(player, "error.missing_ingredients")
        recipe.energyKind?.let { kind ->
            if (energyOf(kind) < kind.required) return message(player, kind.key("error.energy"), energyOf(kind), kind.required)
        }
        val result = recipe.result
        if (result is ItemResult && !outputFits(result.stack)) return message(player, "error.output_full")
        if (!machine.accept(revision)) return
        machine.startCraft(level.gameTime, recipe.processingTime)
        craftRecipe = id
        starter = player.uuid
        level.playSound(null, blockPos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1f, 1.5f)
        changed()
    }

    /** Who receives a result that goes to a player: the starter, else the nearest player. */
    private fun receiver(level: ServerLevel): ServerPlayer? {
        starter?.let { level.server.playerList.getPlayer(it) }?.let { return it }
        val centre = origin
        return level.players().filter { it.position().distanceTo(centre) < 32.0 }.minByOrNull { it.position().distanceTo(centre) }
    }

    private fun finishCraft(level: ServerLevel) {
        val id = craftRecipe
        val holder = id?.let { RksMachines.recipe(level, it) }
        if (holder == null) {
            craftRecipe = null
            machine.finishCraft(level.gameTime)
            changed()
            return
        }
        val recipe = holder.value()
        val result = recipe.result
        if (result is ItemResult && !outputFits(result.stack)) return // waits for room
        // A Pokémon goes to a player or appears in front of the machine: someone must be there.
        val player = if (result is ItemResult) null else (receiver(level) ?: return)
        // The recipe's energy, checked again (a command may have changed it): taken with the ingredients.
        val kind = recipe.energyKind
        if (kind != null && energyOf(kind) < kind.required) {
            message(receiver(level), kind.key("error.energy"), energyOf(kind), kind.required)
            craftRecipe = null
            machine.finishCraft(level.gameTime)
            changed()
            return
        }
        val leftovers = recipe.consume(inputs()) { capsule -> capsulePokemon = Optional.ofNullable(capsule.getPokemon()) }
        if (leftovers == null) {
            message(receiver(level), "error.missing_ingredients")
            craftRecipe = null
            machine.finishCraft(level.gameTime)
            changed()
            return
        }
        if (kind != null) setEnergy(kind, energyOf(kind) - kind.required)
        when (result) {
            is ItemResult -> {
                val made = result.stack.copy()
                if (items[OUTPUT].isEmpty) items[OUTPUT] = made else items[OUTPUT].grow(made.count)
            }
            else -> result.process(player!!, this, result.stack.copy())
        }
        capsulePokemon = Optional.empty()
        for (left in leftovers) Containers.dropItemStack(level, spawnPoint().x, spawnPoint().y + 0.5, spawnPoint().z, left)
        level.playSound(null, blockPos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.2f, 1.3f)
        if (result is PokemonResult || result is FossilResult) GenerationsCore.LOGGER.info("RKS Machine at {}: {} made for {}", blockPos, holder.id(), player?.gameProfile?.name)
        craftRecipe = null
        machine.finishCraft(level.gameTime)
        changed()
    }

    // ---------------------------------------------------------------- the M2 protocol (§ 4.2)

    private val mewtwo: MewtwoConfig get() = RksSettings.mewtwo

    /** The research files' items, in the slots' order (M-01, M-02, M-03). */
    fun researchItems(): List<net.minecraft.world.item.Item> = mewtwo.research.take(RESEARCH_SLOTS).mapNotNull { id ->
        ResourceLocation.tryParse(id)?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }
    }

    fun researchPresent(): Boolean {
        val wanted = researchItems()
        return wanted.withIndex().all { (i, item) -> items[RESEARCH + i].`is`(item) }
    }

    /** "ANALYSER LES ÉCHANTILLONS": the energy gathered and the components in the slots. */
    fun analyse(player: ServerPlayer, revision: Int) {
        val level = level as? ServerLevel ?: return
        if (machine.state == RksState.DAMAGED) return message(player, "error.damaged")
        if (machine.state != RksState.IDLE || machine.experiment) return message(player, "error.busy")
        if (machine.analysed) return
        val recipe = RksMachines.mewtwoRecipe(level)?.value() ?: return message(player, "error.no_recipe")
        if (!RksSettings.allows(recipe)) return message(player, "error.category_off")
        if (energy < mewtwo.energyRequired) return message(player, "error.energy", energy, mewtwo.energyRequired)
        if (recipe.allocate(inputs()) == null) return message(player, "error.missing_ingredients")
        if (!machine.accept(revision)) return
        machine.startAnalysis(level.gameTime, (RksSettings.machine.analysisSeconds * 20).toInt())
        level.playSound(null, blockPos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.2f, 1.6f)
        changed()
    }

    /** "DÉMARRER L'EXPÉRIENCE M2", confirmed: the last check, then everything taken at once. */
    fun startExperiment(player: ServerPlayer, revision: Int) {
        val level = level as? ServerLevel ?: return
        if (machine.state == RksState.DAMAGED) return message(player, "error.damaged")
        if (machine.state != RksState.IDLE || machine.experiment) return message(player, "error.busy")
        if (!machine.analysed) return message(player, "error.not_analysed")
        val holder = RksMachines.mewtwoRecipe(level) ?: return message(player, "error.no_recipe")
        val recipe = holder.value()
        if (!RksSettings.allows(recipe)) return message(player, "error.category_off")
        if (energy < mewtwo.energyRequired) return message(player, "error.energy", energy, mewtwo.energyRequired)
        if (recipe.allocate(inputs()) == null) return message(player, "error.missing_ingredients")
        if (!researchPresent()) return message(player, "error.research")
        if (!machine.accept(revision)) return
        val leftovers = recipe.consume(inputs()) ?: return message(player, "error.missing_ingredients")
        for (i in researchItems().indices) items[RESEARCH + i].shrink(1)
        for (left in leftovers) Containers.dropItemStack(level, spawnPoint().x, spawnPoint().y + 0.5, spawnPoint().z, left)
        energy = 0
        starter = player.uuid
        machine.startExperiment(level.gameTime, mewtwo.growthSeconds * 20.0)
        lastTick = level.gameTime
        level.playSound(null, blockPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 0.7f)
        GenerationsCore.LOGGER.info("RKS Machine at {}: M2 experiment started by {}", blockPos, player.gameProfile.name)
        changed()
    }

    /** Grown: the awakening, as soon as someone is near (D11); the machine waits otherwise. */
    private fun tryAwaken(level: ServerLevel) {
        val config = LegendaryCinematics.config
        val radius = config.radius(CinematicScenes.MEWTWO)
        val centre = origin
        val near = level.players().filter { it.isAlive && !it.isSpectator && it.position().distanceTo(centre) <= radius }
        if (near.isEmpty()) return
        val scene = mewtwo.cinematic && config.enabled
        val free = near.filter { LegendaryCinematics.canWatch(it) }
        val starterNear = free.firstOrNull { it.uuid == starter }
        val invoker = starterNear ?: free.minByOrNull { it.position().distanceTo(centre) }
        if (scene && invoker == null) return // everyone near is busy: still "imminent awakening"
        if (!machine.awaken(level.gameTime)) return
        changed()
        if (scene && invoker != null) {
            val (species, aspects) = mewtwoSpecies(level)
            val landing = landing(level, species)
            val result = LegendaryCinematics.start(
                LegendaryCinematics.Request(
                    level, CinematicScenes.MEWTWO, centre, facing.toYRot(), landing, invoker, species, aspects, test = false,
                    onSpawn = { (level.getBlockEntity(blockPos) as? RksConsoleBlockEntity)?.spawnMewtwo(level) ?: spawnOrphan(level, it.spawn, species) },
                ),
            )
            if (result is LegendaryCinematics.StartResult.Started) {
                session = result.session.id
                return
            }
        }
        // No scene: Mewtwo appears at once.
        spawnMewtwo(level)
    }

    private fun mewtwoSpecies(level: ServerLevel): Pair<ResourceLocation, Set<String>> {
        val result = RksMachines.mewtwoRecipe(level)?.value()?.result as? PokemonResult
        return (result?.species ?: ResourceLocation.fromNamespaceAndPath("cobblemon", "mewtwo")) to (result?.aspects ?: emptySet())
    }

    private fun landing(level: ServerLevel, species: ResourceLocation): Vec3 {
        val form = PokemonSpecies.getByIdentifier(species)?.standardForm
        val width = form?.let { it.hitbox.width() * it.baseScale.toDouble() } ?: 1.2
        val height = form?.let { it.hitbox.height() * it.baseScale.toDouble() } ?: 2.0
        return CinematicPlaces.landing(level, origin, facing.toYRot(), width, height)
    }

    private fun mewtwoProperties(species: ResourceLocation, aspects: Set<String>): PokemonProperties {
        val text = buildString {
            append(species.toString()).append(" level=").append(mewtwo.spawn.level)
            if (mewtwo.spawn.properties.isNotBlank()) append(' ').append(mewtwo.spawn.properties.trim())
        }
        val properties = PokemonProperties.parse(text)
        if (aspects.isNotEmpty()) properties.aspects = aspects
        return properties
    }

    /** Mewtwo appears (§ 5.3): a wild one, in front of the machine, no battle; once at most (the flag first). */
    fun spawnMewtwo(level: ServerLevel) {
        if (!machine.markSpawned()) return
        setChanged()
        val (species, aspects) = mewtwoSpecies(level)
        val at = landing(level, species)
        try {
            val entity = mewtwoProperties(species, aspects).createEntity(level)
            PokemonUtil.spawn(entity, level, at, facing.toYRot())
            GenerationsCore.LOGGER.info("RKS Machine at {}: Mewtwo appeared at {}", blockPos, at)
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("RKS Machine at {}: Mewtwo could not appear", blockPos, e)
        }
        level.playSound(null, blockPos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2f, 0.5f)
        changed()
    }

    /** The console was taken away during the scene (creative): Mewtwo appears all the same. */
    private fun spawnOrphan(level: ServerLevel, at: Vec3, species: ResourceLocation) {
        try {
            PokemonUtil.spawn(mewtwoProperties(species, emptySet()).createEntity(level), level, at, facing.toYRot())
        } catch (e: Exception) {
            GenerationsCore.LOGGER.error("RKS Machine: Mewtwo could not appear", e)
        }
    }

    // ---------------------------------------------------------------- damage and repair (§ 5.4)

    fun repair(player: Player, kit: ItemStack) {
        val level = level as? ServerLevel ?: return
        if (machine.state != RksState.DAMAGED) return message(player, if (machine.state.assembled) "repair.not_damaged" else "repair.incomplete", colour = ChatFormatting.GRAY)
        if (!machine.startRepair(level.gameTime, (RksSettings.machine.repairSeconds * 20).toInt())) return
        if (!player.abilities.instabuild) kit.shrink(1)
        level.playSound(null, blockPos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 1.4f)
        changed()
    }

    // ---------------------------------------------------------------- test commands (§ 6)

    fun commandDamage(): Boolean {
        val level = level ?: return false
        if (!machine.damage(level.gameTime)) return false
        chamber()?.setDamaged(true)
        changed()
        return true
    }

    fun commandRepair() {
        val level = level ?: return
        machine.repair(level.gameTime)
        chamber()?.setDamaged(false)
        changed()
    }

    fun commandReset() {
        val level = level ?: return
        session?.let { id -> LegendaryCinematics.session(id)?.let { LegendaryCinematics.interruptQuietly(it) } }
        session = null
        craftRecipe = null
        machine.reset(level.gameTime)
        changed()
    }

    fun commandProgress(fraction: Double) {
        machine.setProgress(fraction)
        changed()
    }

    fun commandSpeed(speed: Double) {
        machine.speed = speed.coerceIn(0.0, 1000.0)
        machine.touch()
        changed()
    }

    /** Starts the experiment at once, without anything taken (test). */
    fun commandStart(player: ServerPlayer): Boolean {
        val level = level as? ServerLevel ?: return false
        if (machine.state != RksState.IDLE || machine.experiment) return false
        if (!machine.analysed) {
            if (!machine.startAnalysis(level.gameTime, 1)) return false
            machine.tick(level.gameTime + 1)
        }
        starter = player.uuid
        lastTick = level.gameTime
        val ok = machine.startExperiment(level.gameTime, mewtwo.growthSeconds * 20.0)
        changed()
        return ok
    }

    fun commandEnergy(value: Int, kind: RksEnergy = RksEnergy.PSYCHIC) {
        setEnergy(kind, value)
        machine.touch()
        changed()
    }

    fun commandAnalyse() {
        val level = level ?: return
        if (machine.state == RksState.IDLE && !machine.analysed && machine.startAnalysis(level.gameTime, 1)) {
            machine.tick(level.gameTime + 1)
            changed()
        }
    }

    // ---------------------------------------------------------------- the menu

    fun openMenu(player: Player) {
        val server = player as? ServerPlayer ?: return
        GenerationsCore.implementation.openExtendedMenu(server, this)
    }

    override fun getDisplayName(): Component = Component.translatable("generations_core.rks.title")

    override fun createMenu(id: Int, inventory: Inventory, player: Player): AbstractContainerMenu = RksConsoleMenu(id, inventory, this, blockPos)

    override fun saveExtraData(buffer: FriendlyByteBuf) {
        buffer.writeBlockPos(blockPos)
    }

    // ---------------------------------------------------------------- RksMachineHost

    override val hostLevel: Level? get() = level

    override fun spawnPoint(): Vec3 {
        val level = level as? ServerLevel ?: return Vec3.atCenterOf(blockPos)
        return CinematicPlaces.landing(level, origin, facing.toYRot(), 1.5, 2.0)
    }

    override fun spawnYaw(): Float = facing.toYRot()

    // ---------------------------------------------------------------- Container

    override fun getContainerSize(): Int = SLOTS

    override fun isEmpty(): Boolean = items.all { it.isEmpty }

    override fun getItem(slot: Int): ItemStack = items.getOrElse(slot) { ItemStack.EMPTY }

    override fun removeItem(slot: Int, amount: Int): ItemStack = ContainerHelper.removeItem(items, slot, amount).also { if (!it.isEmpty) setChanged() }

    override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items, slot)

    override fun setItem(slot: Int, stack: ItemStack) {
        if (slot !in 0 until SLOTS) return
        items[slot] = stack
        setChanged()
    }

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = when {
        state.locksSlots -> false
        slot < INPUTS -> true
        slot >= RESEARCH -> machine.analysed && researchItems().getOrNull(slot - RESEARCH)?.let { stack.`is`(it) } == true && items[slot].isEmpty
        else -> false
    }

    override fun canTakeItem(target: Container, slot: Int, stack: ItemStack): Boolean = slot == OUTPUT

    override fun stillValid(player: Player): Boolean =
        level?.getBlockEntity(blockPos) === this && player.distanceToSqr(Vec3.atCenterOf(blockPos)) <= 64.0

    override fun clearContent() = items.clear()

    // ---------------------------------------------------------------- saving and syncing

    private fun writeMachine(nbt: CompoundTag) {
        val values = LinkedHashMap<String, Any>()
        machine.save(values)
        val tag = CompoundTag()
        for ((key, value) in values) when (value) {
            is Int -> tag.putInt(key, value)
            is Long -> tag.putLong(key, value)
            is Double -> tag.putDouble(key, value)
            is Boolean -> tag.putBoolean(key, value)
        }
        nbt.put("Machine", tag)
        nbt.putInt("Energy", energy)
        nbt.putInt("FireEnergy", fireEnergy)
        nbt.putInt("Missing", missingMask)
        starter?.let { nbt.putUUID("Starter", it) }
        craftRecipe?.let { nbt.putString("Recipe", it.toString()) }
    }

    private fun readMachine(nbt: CompoundTag) {
        val tag = nbt.getCompound("Machine")
        val values = HashMap<String, Any?>()
        for (key in tag.allKeys) {
            values[key] = when (tag.getTagType(key).toInt()) {
                3 -> tag.getInt(key)
                4 -> tag.getLong(key)
                6 -> tag.getDouble(key)
                1 -> tag.getBoolean(key)
                else -> null
            }
        }
        machine.load(values)
        energy = nbt.getInt("Energy")
        fireEnergy = nbt.getInt("FireEnergy")
        missingMask = if (nbt.contains("Missing")) nbt.getInt("Missing") else missingMask
        starter = if (nbt.hasUUID("Starter")) nbt.getUUID("Starter") else null
        craftRecipe = if (nbt.contains("Recipe")) ResourceLocation.tryParse(nbt.getString("Recipe")) else null
    }

    override fun writeNbt(nbt: CompoundTag, provider: HolderLookup.Provider) {
        writeMachine(nbt)
        ContainerHelper.saveAllItems(nbt, items, provider)
        if (lastTick >= 0) nbt.putLong("LastTick", lastTick)
    }

    override fun readNbt(nbt: CompoundTag, provider: HolderLookup.Provider) {
        readMachine(nbt)
        // A sync from the server carries the machine only (the menu sends the slots).
        if (nbt.contains("Items")) {
            items.clear()
            ContainerHelper.loadAllItems(nbt, items, provider)
        }
        if (nbt.contains("LastTick")) lastTick = nbt.getLong("LastTick")
        // Sent by the server: when (the clients' estimate starts there).
        if (nbt.contains("SyncTime")) syncTime = nbt.getLong("SyncTime")
    }

    /** What the clients need: the machine, not the slots (the menu sends those). */
    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        writeMachine(tag)
        tag.putLong("SyncTime", level?.gameTime ?: syncTime)
        return tag
    }

    // ---------------------------------------------------------------- client estimates

    /** The growth (0 to 1) now, estimated from the last sync (client) or exact (server). */
    fun growthNow(gameTime: Long, partial: Float = 0f): Double {
        if (!machine.experiment) return 0.0
        if (level?.isClientSide != true || machine.state != RksState.GROWING) return machine.progress
        val extra = ((gameTime - syncTime).toDouble() + partial).coerceAtLeast(0.0) * machine.speed
        return ((machine.growth + extra) / machine.growthTotal).coerceIn(0.0, 1.0)
    }

    /** Seconds into the current state (client or server). */
    fun stateSeconds(gameTime: Long, partial: Float = 0f): Double = ((gameTime - machine.since) + partial) / 20.0

    fun renderBox(): AABB = AABB(blockPos).inflate(4.0, 1.0, 4.0).expandTowards(0.0, 5.0, 0.0)

    companion object {
        const val INPUTS = 9
        const val OUTPUT = 9
        const val RESEARCH = 10
        const val RESEARCH_SLOTS = 3
        const val SLOTS = 13
        /** In the console's item: the psychic energy it kept. */
        const val ITEM_ENERGY = "RksEnergy"
        /** In the console's item: the fire energy it kept. */
        const val ITEM_FIRE_ENERGY = "RksFireEnergy"
    }
}
