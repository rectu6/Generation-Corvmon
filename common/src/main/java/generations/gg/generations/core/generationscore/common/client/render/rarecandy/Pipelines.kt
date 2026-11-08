package generations.gg.generations.core.generationscore.common.client.render.rarecandy

import com.cobblemon.mod.common.api.reactive.EventObservable
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.systems.RenderSystem
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.GenerationsTextureLoader
import generations.gg.generations.core.generationscore.common.client.MatrixCache
import generations.gg.generations.core.generationscore.common.client.model.ModelContextProviders.TintProvider
import gg.generations.rarecandy.pokeutils.BlendType
import gg.generations.rarecandy.pokeutils.CullType
import gg.generations.rarecandy.pokeutils.reader.ITextureLoader
import gg.generations.rarecandy.renderer.animation.Transform
import gg.generations.rarecandy.renderer.loading.ITexture
import gg.generations.rarecandy.renderer.model.material.PipelineRegistry
import gg.generations.rarecandy.renderer.pipeline.Pipeline
import gg.generations.rarecandy.renderer.pipeline.UniformUploadContext
import gg.generations.rarecandy.renderer.storage.AnimatedObjectInstance
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL20C
import kotlin.math.sin

object Pipelines {
    val INSTANCE: RenderContext.Key<CobblemonInstance> = RenderContext.key(
        GenerationsCore.id("object_instance"), TypeToken.get(
            CobblemonInstance::class.java
        )
    )
    private val ONE = Vector3f(1f, 1f, 1f)
    private val ZERO = Vector3f(0f, 0f, 0f)


    @JvmField
    var REGISTER = EventObservable<PipelineRegister>()

    private var useLegacy = false

    private val PIPELINE_MAP: MutableMap<String, (String) -> Pipeline?> = HashMap()


    private var initialized = false

    @JvmStatic
    fun toggleRendering() {
        useLegacy = !useLegacy
    }

    private const val MAIN = "main"

    fun getPipeline(name: String): Pipeline {
        return PIPELINE_MAP[MAIN]!!.invoke(name)!!
    }

    /**
     * Called on first usage of RareCandy to reduce lag later on
     */
    @JvmStatic
    fun onInitialize(manager: ResourceManager) {
        if (!initialized) {
            REGISTER.post(PipelineRegister(manager, PIPELINE_MAP), then = {
                initialized = true
                useLegacy = GenerationsCore.CONFIG.client.usePixelmonShading
                PipelineRegistry.setFunction(::getPipeline)
            })
        }
    }

    private val TEMP = Vector4f()

    @JvmStatic
    fun initGenerationsPipelines(register: PipelineRegister) {
        register.register("main") { manager ->
            val pipeline = createShader(manager)
            return@register { pipeline }
        }
    }

    class PipelineRegister(
        private val resourceManager: ResourceManager,
        private val pipelines: MutableMap<String, (String) -> Pipeline?>
    ) {
        fun register(name: String, function: (ResourceManager) -> (String) -> Pipeline?) {
            pipelines[name] = function.invoke(resourceManager)
        }
    }

    // Uniforms are split by how often their value can change (see UniformCache.kt):
    // oncePerPass / oncePerInstance skip the upload while the program already holds the value.
    fun createShader(manager: ResourceManager): Pipeline {
        val bones = BoneUploader()
        val fogShape = RenderSystem.getShaderFogShape().ordinal
        val pipeline = Pipeline.Builder()
        .oncePerPass("legacy") { it.uniform().uploadBoolean(useLegacy) }
        .oncePerPass("FogShape") { it.uniform().uploadInt(fogShape) }
        .oncePerPass("viewMatrix") { it.uniform().uploadMat4f(MatrixCache.viewMatrix) }
        .oncePerInstance("modelMatrix") { it.uniform().uploadMat4f(it.instance().transformationMatrix()) }
        .oncePerPass("projectionMatrix") { it.uniform().uploadMat4f(MatrixCache.projectionMatrix) }
        .supplyVec2("uvOffset") { it.transform.offset() ?: Transform.DEFAULT_OFFSET }
        .supplyVec2("uvScale") { it.transform.scale() ?: Transform.DEFAULT_SCALE }
        .supplyUniform("boneTransforms") { bones.upload(it) }

        .oncePerPass("ColorModulator") { it.uploadColor(RenderSystem.getShaderColor()) }
        .oncePerPass("FogStart") { it.uniform().uploadFloat(RenderSystem.getShaderFogStart()) }
        .oncePerPass("FogEnd") { it.uniform().uploadFloat(RenderSystem.getShaderFogEnd()) }
        .oncePerPass("FogColor") { it.uploadColor(RenderSystem.getShaderFogColor()) }
        .oncePerPass("auxMode") { it.uniform().uploadInt(IrisFramebufferCompat.auxMode()) }
        .oncePerPass("sunDirection") { it.uniform().uploadVec3f(RareCandyLighting.direction) }
        .oncePerPass("sunLight") {
            val color = RareCandyLighting.color
            it.uniform().upload4f(color.x, color.y, color.z, RareCandyLighting.strength)
        }

        .supplyTexture("diffuse", 0) { it.instance().instanceOrNull<StatueInstance>()?.material?.let { GenerationsTextureLoader.getTextureOrNull(it) } ?: it.getTextureOrOther({ it.material.images().diffuse }) { ITextureLoader.instance().nuetralFallback } }
        .supplyTexture("mask", 1) { it.getTextureOrOther({ it.material.images().mask }) { ITextureLoader.instance().darkFallback } }
        .supplyTexture("layer", 2) { it.getTextureOrOther({ it.material.images().layer }) { ITextureLoader.instance().darkFallback } }
        .supplyTexture("lightmap", 3) { Minecraft.getInstance().gameRenderer.lightTexture() as ITexture }
        .supplyTexture("emission", 4) { it.getTextureOrOther({ it.material.images().emission }) { ITextureLoader.instance().darkFallback } }
        .supplyTexture("paradoxMask", 5) { ITextureLoader.instance().getTexture("paradox_mask") }

        .supplyInt("colorMethod") { it.material.colorMethod }
        .supplyInt("effect") { it.material.effect }

        .oncePerInstance("light") { ctx: UniformUploadContext ->
            val light = (ctx.instance() as BlockLightValueProvider).light
            ctx.uniform().upload2i(light and 0xFFFF, light shr 16 and 0xFFFF)
        }

        // Same colours as Minecraft's OverlayTexture, which vanilla shaders sample with
        // the packed overlay: red while hurt or dying (v < 8), else white with a
        // strength set by u (creeper flash). NO_OVERLAY leaves the colour unchanged.
        .oncePerInstance("overlayColor") {
            val overlay = (it.instance() as? CobblemonInstance)?.overlay ?: OverlayTexture.NO_OVERLAY
            val u = overlay and 0xFFFF
            val v = overlay shr 16 and 0xFFFF
            if (v < 8) it.uniform().upload4f(1f, 0f, 0f, 178f / 255f)
            else it.uniform().upload4f(1f, 1f, 1f, 1f - u / 15f * 0.75f)
        }

        .oncePerInstance("tint") { it.uniform().uploadVec3f(it.instance().instanceOrNull<CobblemonInstance>()?.tint?.takeIf { it != ZERO } ?: ONE) }

        .oncePerPass("frame") { it.uniform().uploadInt(pingpong(MinecraftClientGameProvider.getTimePassed()).toInt()) }

        .supplyVec3("baseColor1") { ctx -> ctx.instance().instanceOrNull<TintProvider>()?.tint ?: ctx.takeIf { !it.isStatueMaterial }?.material?.values()?.baseColor1 ?: ONE }
        .supplyVec3("baseColor2") { ctx -> ctx.takeIf { !it.isStatueMaterial }?.material?.values()?.baseColor2 ?: ONE }
        .supplyVec3("baseColor3") { ctx -> ctx.takeIf { !it.isStatueMaterial }?.material?.values()?.baseColor3 ?: ONE }
        .supplyVec3("baseColor4") { ctx -> ctx.takeIf { !it.isStatueMaterial }?.material?.values()?.baseColor4 ?: ONE }
        .supplyVec3("baseColor5") { ctx -> ctx.takeIf { !it.isStatueMaterial }?.material?.values()?.baseColor5 ?: ONE }
        .supplyVec3("emiColor1") { it.takeIf { !it.isStatueMaterial }?.material?.values()?.emiColor1 ?: ONE }
        .supplyVec3("emiColor2") { it.takeIf { !it.isStatueMaterial }?.material?.values()?.emiColor2 ?: ONE }
        .supplyVec3("emiColor3") { it.takeIf { !it.isStatueMaterial }?.material?.values()?.emiColor3 ?: ONE }
        .supplyVec3("emiColor4") { it.takeIf { !it.isStatueMaterial }?.material?.values()?.emiColor4 ?: ONE }
        .supplyVec3("emiColor5") { it.takeIf { !it.isStatueMaterial }?.material?.values()?.emiColor5 ?: ONE }
        .supplyFloatUniform("emiIntensity1") { it.takeIf { !it.isStatueMaterial }?.material?.values()?.emiIntensity1 ?: 0.0f }
        .supplyFloatUniform("emiIntensity2") { it.takeIf { !it.isStatueMaterial }?.material?.values()?.emiIntensity2 ?: 0.0f }
        .supplyFloatUniform("emiIntensity3") { it.takeIf { !it.isStatueMaterial }?.material?.values()?.emiIntensity3 ?: 0.0f }
        .supplyFloatUniform("emiIntensity4") { it.takeIf { !it.isStatueMaterial }?.material?.values()?.emiIntensity4 ?: 0.0f }
        .supplyFloatUniform("emiIntensity5") { it.takeIf { !it.isStatueMaterial }?.material?.values()?.emiIntensity5 ?: 1.0f }
        .supplyBooleanUniform("useLight") { it.material.values().useLight }

        .oncePerPass("Light0_Direction") { it.uniform().uploadVec3f(RenderSystem.shaderLightDirections[0]) }
        .oncePerPass("Light1_Direction") { it.uniform().uploadVec3f(RenderSystem.shaderLightDirections[1]) }

        .oncePerInstance("useTera") {
            it.uniform().uploadBoolean(it.instance().instanceOrNull<CobblemonInstance>()?.teraActive ?: false)
        }
        .oncePerInstance("teraTint") { it.uniform().uploadVec3f(it.instance().instanceOrNull<CobblemonInstance>()?.teraTint?.takeIf { it != ZERO } ?: ONE) }

        .prePostDraw({ material ->
            if (material.cullType() != CullType.None) {
                RenderSystem.enableCull()
            } else {
                RenderSystem.disableCull()
            }

            if (material.blendType() == BlendType.Regular) {
                RenderSystem.enableBlend()
                RenderSystem.defaultBlendFunc()
            } else {
                // An opaque mesh must not inherit blending from the previous
                // material or from Minecraft/Iris's preceding render pass.
                RenderSystem.disableBlend()
            }
        }, {})
        .shader(manager, "shaders/animated.vs.glsl", "shaders/animated.fs.glsl")
        .build()

        bones.location = GL20C.glGetUniformLocation(pipeline.program(), "boneTransforms")
        return pipeline
    }
}

private fun Pipeline.Builder.oncePerPass(name: String, upload: (UniformUploadContext) -> Unit): Pipeline.Builder {
    val gate = PassGate()
    return supplyUniform(name) { if (gate.open()) upload(it) }
}

private fun Pipeline.Builder.oncePerInstance(name: String, upload: (UniformUploadContext) -> Unit): Pipeline.Builder {
    val gate = InstanceGate()
    return supplyUniform(name) { if (gate.open(it.instance())) upload(it) }
}

private fun UniformUploadContext.uploadColor(color: FloatArray) = uniform().upload4f(color[0], color[1], color[2], color[3])

fun Pipeline.Builder.shader(manager: ResourceManager, vertex: String, fragment: String): Pipeline.Builder =
    shader(read(manager, vertex.id()), read(manager, fragment.id()))


fun pingpong(time: Double): Double = (sin(time * Math.PI * 2) * 7 + 7).toInt().toDouble()

private fun Pipeline.Builder.supplyVec2(name: String, function: (UniformUploadContext) -> Vector2f): Pipeline.Builder = this.supplyUniform(name) { it.uniform().uploadVec2f(function.invoke(it)) }
private fun Pipeline.Builder.supplyVec3(name: String, function: (UniformUploadContext) -> Vector3f): Pipeline.Builder = this.supplyUniform(name) { it.uniform().uploadVec3f(function.invoke(it))}
private fun Pipeline.Builder.supplyFloatUniform(name: String, function: (UniformUploadContext) -> Float): Pipeline.Builder = this.supplyUniform(name) { it.uniform().uploadFloat(function.invoke(it)) }
private fun Pipeline.Builder.supplyInt(name: String, function: (UniformUploadContext) -> Int): Pipeline.Builder = this.also { this.supplyUniform(name) { it.uniform().uploadInt(function.invoke(it)) } }
public inline fun <reified T> Any?.instanceOrNull(): T? = if(this is T) this else null

private val UniformUploadContext.isStatueMaterial: Boolean
    get() = statueMaterial != null

private val UniformUploadContext.statueMaterial: String?
    get() = this.instance().instanceOrNull<StatueInstance>()?.material?.takeIf { GenerationsTextureLoader.has(it) }

private val UniformUploadContext.transform: Transform
    get() = this.instance().instanceOrNull<AnimatedObjectInstance>()?.getTransform(this.material.materialName)?.takeIf { !it.isUnit } ?: this.`object`().getTransform(this.instance().variant())


private fun UniformUploadContext.getTextureOrOther(function: (UniformUploadContext) -> String?, supplier: () -> ITexture): ITexture = GenerationsTextureLoader.getTexture(function.invoke(this))?.takeUnless { texture -> texture === GenerationsTextureLoader.MissingTextureProxy } ?: supplier.invoke()

private fun String.id(): ResourceLocation = GenerationsCore.id(this)

private fun Pipeline.Builder.supplyTexture(name: String, slot: Int, function: (UniformUploadContext) -> ITexture): Pipeline.Builder {
    // The sampler's texture unit never changes; the bound texture does, per material.
    val gate = PassGate()
    return this.supplyUniform(name) {
        function.invoke(it).bind(slot)
        if (gate.open()) it.uniform().uploadInt(slot)
    }
}

private fun Pipeline.Builder.supplyBooleanUniform(name: String, function: (UniformUploadContext) -> Boolean): Pipeline.Builder { return this.supplyUniform(name) { it.uniform().uploadBoolean(function.invoke(it)) } }

fun read(manager: ResourceManager, name: ResourceLocation): String {
    try {
        manager.getResource(name).orElseThrow().open().use { `is` ->
            return String(`is`.readAllBytes())
        }
    } catch (e: Exception) {
        throw RuntimeException("Failed to read shader from resource location in shader: $name", e)
    }
}
