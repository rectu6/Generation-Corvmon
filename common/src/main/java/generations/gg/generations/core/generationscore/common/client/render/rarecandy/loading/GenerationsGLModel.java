package generations.gg.generations.core.generationscore.common.client.render.rarecandy.loading;

import gg.generations.rarecandy.pokeutils.BlendType;
import gg.generations.rarecandy.renderer.components.RenderObject;
import gg.generations.rarecandy.renderer.loading.Attribute;
import gg.generations.rarecandy.renderer.model.GLModel;
import gg.generations.rarecandy.renderer.model.material.PipelineRegistry;
import gg.generations.rarecandy.renderer.rendering.ObjectInstance;
import gg.generations.rarecandy.renderer.rendering.RenderStage;

import java.nio.ByteBuffer;
import java.util.List;

/** Keeps the bundled RareCandy 2.14.2 meshes in their respective world passes. */
public class GenerationsGLModel extends GLModel {
    private final int indexCount;
    private final int indexType;
    private final List<Attribute> attributes;
    /** CPU copy used to cast shadows with Iris, read back from the GPU on first use. */
    private RareCandyShadowMesh shadowMesh;
    private boolean shadowMeshUnsupported;

    public GenerationsGLModel(ByteBuffer vertices, ByteBuffer indices, List<Runnable> glCalls,
                              int indexCount, int indexType, List<Attribute> attributes) {
        super(vertices, indices, glCalls, indexCount, indexType, attributes);
        this.indexCount = indexCount;
        this.indexType = indexType;
        this.attributes = attributes;
    }

    /** Render thread only. Null until the mesh is on the GPU, or if its layout is unknown. */
    public RareCandyShadowMesh shadowMesh() {
        if (shadowMesh == null && !shadowMeshUnsupported && vbo > 0 && ebo > 0) {
            shadowMesh = RareCandyShadowMesh.readBack(vbo, ebo, indexCount, indexType, attributes);
            shadowMeshUnsupported = shadowMesh == null;
        }
        return shadowMesh;
    }

    @Override
    public void close() {
        super.close();
        shadowMesh = null;
    }

    @Override
    public <T extends RenderObject> void render(RenderStage stage, List<ObjectInstance> instances, T object) {
        for (var instance : instances) {
            // Despite its name, RareCandy's predicate returns true for hidden meshes.
            if (object.shouldRender(instance)) continue;

            var material = object.getMaterial(instance.variant());
            var materialStage = material.blendType() == BlendType.None
                    ? RenderStage.SOLID : RenderStage.TRANSPARENT;
            if (stage != materialStage) continue;

            var pipeline = PipelineRegistry.get(material.getPipeline());
            if (pipeline == null) continue;

            pipeline.bind(material);
            try {
                pipeline.updateOtherUniforms(instance, object);
                pipeline.updateTexUniforms(instance, object);
                runDrawCalls();
            } finally {
                pipeline.unbind(material);
            }
        }
    }
}
