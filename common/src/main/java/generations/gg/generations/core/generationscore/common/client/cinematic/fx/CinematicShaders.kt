package generations.gg.generations.core.generationscore.common.client.cinematic.fx

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import generations.gg.generations.core.generationscore.common.GenerationsCore
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.server.packs.resources.ResourceProvider

/**
 * The cinematics' own shaders (assets/minecraft/shaders/core/generations_cinematic_*: Minecraft
 * always looks for core shaders in its own namespace; the prefixed names are never loaded by
 * vanilla itself). Compiled when the resources (re)load, with Minecraft's own
 * (GameRendererMixin, GameRenderer.reloadShaders), never when a scene starts. A shader that
 * fails is null: its effects are left out, the scene still plays.
 */
object CinematicShaders {
    var fx: ShaderInstance? = null
        private set
    var crystal: ShaderInstance? = null
        private set
    var post: ShaderInstance? = null
        private set
    var blur: ShaderInstance? = null
        private set

    @JvmStatic
    fun reload(provider: ResourceProvider) {
        close()
        fx = load(provider, "generations_cinematic_fx", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL)
        crystal = load(provider, "generations_cinematic_crystal", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL)
        post = load(provider, "generations_cinematic_post", DefaultVertexFormat.POSITION_TEX)
        blur = load(provider, "generations_cinematic_blur", DefaultVertexFormat.POSITION_TEX)
        CinematicTextures.ensure()
    }

    private fun load(provider: ResourceProvider, name: String, format: VertexFormat): ShaderInstance? = try {
        ShaderInstance(provider, name, format)
    } catch (e: Exception) {
        GenerationsCore.LOGGER.error("Legendary cinematics: the shader {} could not be loaded, its effects are left out", name, e)
        null
    }

    private fun close() {
        for (shader in listOf(fx, crystal, post, blur)) runCatching { shader?.close() }
        fx = null
        crystal = null
        post = null
        blur = null
    }
}
