package generations.gg.generations.core.generationscore.common.client

import com.cobblemon.mod.common.util.asResource
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.serialization.Codec
import generations.gg.generations.core.generationscore.common.GenerationsCore
import generations.gg.generations.core.generationscore.common.client.model.SpriteRegistry
import generations.gg.generations.core.generationscore.common.client.render.rarecandy.ITextureWithResourceLocation
import generations.gg.generations.core.generationscore.common.util.GenerationsUtils
import gg.generations.rarecandy.pokeutils.reader.ITextureLoader
import gg.generations.rarecandy.renderer.loading.ITexture
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.client.renderer.texture.SimpleTexture
import net.minecraft.resources.FileToIdConverter
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import org.lwjgl.opengl.GL13C
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlin.random.Random

object GenerationsTextureLoader : ITextureLoader() {
    // RareCandy registers model images from its loading threads while the render
    // thread reads and releases them.
    val REGULAR: MutableMap<String, ResourceLocation> = ConcurrentHashMap()
    val CODEC = Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC)
    val RARE_CANDY = FileToIdConverter("textures", "rare_candy_texture.json")
    val gson = Gson()



    object MissingTextureProxy : ITexture {

        override fun close() {
        }
        override fun bind(slot: Int) {
            var texture = Minecraft.getInstance().textureManager.getTexture(MissingTextureAtlasSprite.getLocation())

            RenderSystem.activeTexture(GL13C.GL_TEXTURE0 + slot)
            RenderSystem.bindTexture(texture.id)
        }

        override fun width(): Int = 16

        override fun height(): Int = 16

    }

    fun initialize(manager: ResourceManager) {
        clear()
        try {
            RARE_CANDY.listMatchingResourceStacks(manager).forEach { name, list ->
                list.forEach { resource ->
                    val obj = resource.openAsReader().use {
                        SpriteRegistry.GSON.fromJson(it, JsonObject::class.java)
                    }
                    val map = GenerationsUtils.decode(CODEC, obj)

                    if (map.isNotEmpty()) {
                        map.forEach { (key, value) ->
                            val textureLoc = "${value.namespace}:textures/${value.path}.png".asResource()
                            register(key, SimpleTextureEnhanced(textureLoc))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }


    override fun getTexture(s: String?): ITexture? {
        val texture = s?.let { REGULAR[it] }?.let { Minecraft.getInstance().textureManager.getTexture(it, null) }.takeIf { it is ITextureWithResourceLocation } ?: return MissingTextureProxy

        return texture as ITexture
    }

    override fun register(s: String, iTexture: ITexture) {
        if (iTexture !is ITextureWithResourceLocation) return
        val location = iTexture.location
        val previous = REGULAR.putIfAbsent(s, location)
        // The texture already registered itself with Minecraft when it was created:
        // a duplicate that is not kept here would never be released.
        if (previous != null && previous != location) {
            RenderSystem.recordRenderCall { Minecraft.getInstance().textureManager.release(location) }
        }
    }

    override fun register(id: String, name: String, data: ByteArray) {
        // Reloading a model registers its images again; keep the existing texture.
        if (REGULAR.containsKey(id)) return
        SimpleTextureIndependentData.read(data, name)?.let { register(id, it) }
    }


    override fun remove(s: String) {
        REGULAR.remove(s)?.run { Minecraft.getInstance().textureManager.release(this) }
    }

    /**
     * Releases every image of a RareCandy model. ModelLoader.readImages registers
     * all of them as "<model>-<image>", but Material.close (RareCandy 2.14.2) only
     * removes some: it removes the diffuse texture twice and never the emission
     * one, and images no material uses are never removed. Models are unloaded 5 s
     * after they leave the screen, so each reload leaked textures. Render thread only.
     */
    fun removeModelTextures(model: String) {
        val prefix = "$model-"
        val iterator = REGULAR.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.key.startsWith(prefix)) continue
            iterator.remove()
            Minecraft.getInstance().textureManager.release(entry.value)
        }
    }

    fun clear() {
        val iterator = REGULAR.iterator()
        while(iterator.hasNext()) {
            val entry = iterator.next()
            iterator.remove()
            Minecraft.getInstance().textureManager.release(entry.value)
        }
    }

    override fun getDarkFallback(): ITexture? = getTexture("dark")

    override fun getBrightFallback(): ITexture? = getTexture("bright")

    override fun getNuetralFallback(): ITexture? = getTexture("neutral")

    override fun getTextureEntries(): Set<String> = REGULAR.keys

    fun has(texture: String?): Boolean = texture != null && REGULAR.containsKey(texture)

    fun getLocation(material: String?): ResourceLocation? = material?.let { REGULAR[it] }
    fun getTextureOrNull(name: String?): ITexture? {
        return if(has(name)) getTexture(name) else null
    }

    private class SimpleTextureEnhanced(override var location: ResourceLocation) : SimpleTexture(location), ITextureWithResourceLocation {

        init {
            Minecraft.getInstance().textureManager.register(location, this)
        }

        override fun bind(slot: Int) {
            RenderSystem.activeTexture(GL13C.GL_TEXTURE0 + slot)
            RenderSystem.bindTexture(id)
        }

        override fun width(): Int {
            TODO("Not yet implemented")
        }

        override fun height(): Int {
            TODO("Not yet implemented")
        }
    }

    private class SimpleTextureIndependentData(override var location: ResourceLocation, private val texture: ByteArray?) : SimpleTexture(location), ITextureWithResourceLocation {

        init {
            Minecraft.getInstance().textureManager.register(location, this)
        }

        override fun bind(slot: Int) {
            RenderSystem.activeTexture(GL13C.GL_TEXTURE0 + slot)
            RenderSystem.bindTexture(id)
        }

        override fun width(): Int {
            TODO("Not yet implemented")
        }

        override fun height(): Int {
            TODO("Not yet implemented")
        }

        override fun getTextureImage(resourceManager: ResourceManager): TextureImage {
            return load()!!
        }

        fun load(): TextureImage? {
            return try {
                val inputStream = ByteArrayInputStream(texture)
                val nativeImage: NativeImage = try {
                    NativeImage.read(inputStream)
                } catch (var9: Throwable) {
                    try {
                        inputStream.close()
                    } catch (var7: Throwable) {
                        var9.addSuppressed(var7)
                    }
                    throw var9
                }

                inputStream.close()
//                var textureMetadataSection: TextureMetadataSection? = null //TODO: See if implmenting is viable
//                try {
//                    textureMetadataSection = resource.metadata().getSection(TextureMetadataSection.SERIALIZER)
//                        .orElse(null as Any?) as TextureMetadataSection
//                } catch (var8: java.lang.RuntimeException) {
//                    LOGGER.warn("Failed reading metadata of: {}", location, var8)
//                }
                TextureImage(null, nativeImage)
            } catch (var10: IOException) {
                TextureImage(var10)
            }
        }

        companion object {
            @Throws(IOException::class)
            fun read(imageBytes: ByteArray?, name: String): SimpleTextureIndependentData? {
                if(imageBytes == null) return null

                val resource = if(name.contains(":")) {
                    name.lowercase().asResource()
                } else {
                    GenerationsCore.id((1..4).joinToString("") { Random.nextInt(0, 10).toString() }  + name.lowercase())
                }

                return SimpleTextureIndependentData(resource, imageBytes)
            }

        }
    }
}