package generations.gg.generations.core.generationscore.common.client.model

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver
import com.cobblemon.mod.common.client.render.models.blockbench.LocatorAccess.Companion.PREFIX
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import generations.gg.generations.core.generationscore.common.battle.DynamaxVisuals
import generations.gg.generations.core.generationscore.common.client.IVariant
import generations.gg.generations.core.generationscore.common.client.render.CobblemonInstanceProvider
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.CompiledModel
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.ModelRegistry
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.instanceOrNull
import generations.gg.generations.core.generationscore.common.client.render.tera.tint
import generations.gg.generations.core.generationscore.common.util.extensions.battleTeraType
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3f
import java.util.*
import java.util.function.Supplier

//TODO: Simplify and figure out how to do this in a simpler fashion

private val RenderContext.form: FormData?
    get() = this.species?.getForm(this.request(RenderContext.ASPECTS) ?: Collections.emptySet())

private val RenderContext.species: Species?
    get() = this.request(RenderContext.SPECIES)?.let { PokemonSpecies.getByIdentifier(it) }

class RareCandyBone /*Remove when cobblemon doesn't have parts of code that assumes Bone will always be a ModelPart */(
    location: ResourceLocation): ModelPart(mutableListOf(), MAP), Supplier<Bone>, Bone {
    private val objectSupplier: () -> CompiledModel? = { ModelRegistry[location] }

    override fun getChildren(): Map<String, Bone> {
        return MAP as Map<String, Bone>
    }

    override fun render(
        context: RenderContext,
        stack: PoseStack,
        buffer: VertexConsumer,
        packedLight: Int,
        packedOverlay: Int,
        color: Int
    ) {
        renderModel(context, buffer, stack, packedLight, packedOverlay, color)
    }

    private fun renderModel(
        context: RenderContext,
        buffer: VertexConsumer,
        stack: PoseStack,
        packedLight: Int,
        packedOverlay: Int,
        color: Int
    ) {

        val instance = context.request(RenderContext.Companion.POSABLE_STATE).instanceOrNull<CobblemonInstanceProvider>()?.instance

        if(instance != null) {
            let {  }
        }

        val model = objectSupplier.invoke()
        if (model?.renderObject == null) return

        var scale = model.renderObject!!.scale // / context.requires(RenderContext.SCALE)
        if (instance == null) {
            return
        } else {
            scale *= 1f / (context.form?.baseScale ?: 1f)
        }
        if (model.renderObject!!.isReady) {
            instance.light = packedLight
            // Red while hurt by a player, like vanilla mobs and Cobblemon's own
            // models; not during a Pokémon battle.
            val pokemonEntity = context.entity.instanceOrNull<PokemonEntity>()
            val battling = pokemonEntity?.isBattling == true
            instance.overlay = if (battling) OverlayTexture.NO_OVERLAY else packedOverlay
            // The entity's synced aspects: those of the client Pokémon are only the ones it
            // had when it appeared, so Dynamax or Tera started meanwhile would only show
            // once the entity is loaded again.
            val aspects = pokemonEntity?.aspects ?: context.request(RenderContext.ASPECTS)
            instance.teraActive = aspects?.contains("terastal_active") ?: false
            instance.dynamax = aspects?.contains(DynamaxVisuals.ASPECT) ?: false
            if (instance.teraActive) {
                context.entity.instanceOrNull<PokemonEntity>()?.battleTeraType?.let {
                    instance.teraTint.set(it.tint)
                }
            }
//            instance.tint.set(r, g, b) TODO: convert color int into its float components for tint.
            val variant = getVariant(context)
            if (variant != null) {
                instance.setVariant(variant)
            }

            stack.pushPose()
            stack.mulPose(ROTATION_CORRECTION)
            stack.scale(-scale, -scale, scale)
            instance.transformationMatrix().set(stack.last().pose())
            stack.popPose()

            // Size of the model in blocks (all scales included), for the culling box:
            // big models outgrow their hitbox. RareCandy's dimensions are the largest
            // vertex coordinates, so a margin covers parts on the negative side (tails).
            val dimensions = model.renderObject!!.dimensions
            val modelScale = instance.transformationMatrix().getScale(SCALE).x
            instance.modelRadius = maxOf(dimensions.x, dimensions.y, dimensions.z) * modelScale * 1.25f
            // Top of the model (camera-relative height, like the pose) and its half width,
            // for what sits on it: the Dynamax clouds.
            instance.modelTopY = instance.transformationMatrix().transformPosition(0f, dimensions.y, 0f, TOP).y
            instance.modelHalfWidth = maxOf(dimensions.x, dimensions.z) * modelScale
            instance.modelHeight = dimensions.y * modelScale

            model.render(instance, Minecraft.getInstance().renderBuffers().bufferSource())
        }
    }

    private fun getVariant(context: RenderContext): String? {
        return try {
            val aspects = context.request<Set<String>>(RenderContext.Companion.ASPECTS)
            val species = context.request<ResourceLocation>(RenderContext.Companion.SPECIES)!!
            return VaryingModelRepository.variations[species]?.getResolvedVariant(aspects ?: emptySet())
        } catch (e: Exception) {
            null
        }
    }

    override fun transform(poseStack: PoseStack) {}
    override fun get(): Bone = this

    companion object {
        private val MAP = mapOf(
            PREFIX + "seat_1" to ModelPart(mutableListOf(), mapOf()).also {
                it.setPos(0f, -35f, 0f)
            }
        )
        private val ROTATION_CORRECTION = Axis.YP.rotationDegrees(180f)
        private val SCALE = Vector3f()
        private val TOP = Vector3f()
    }
}

private fun VaryingRenderableResolver.getResolvedVariant(aspects: Set<String>): String? {
    return variations.lastOrNull { it.aspects.all { it in aspects } && (it as IVariant).variant != null }?.let { (it as IVariant).variant }
}