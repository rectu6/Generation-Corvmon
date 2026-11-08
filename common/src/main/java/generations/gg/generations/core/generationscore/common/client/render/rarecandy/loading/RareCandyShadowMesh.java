package generations.gg.generations.core.generationscore.common.client.render.rarecandy.loading;

import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.generations.rarecandy.renderer.loading.Attribute;
import gg.generations.rarecandy.renderer.loading.ModelLoader;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * CPU copy of one RareCandy mesh, skinned on the CPU so it can be drawn through
 * Minecraft's vertex pipeline. Iris only gives a shader pack's shadow program
 * to geometry drawn that way; RareCandy's own GPU draws never reach the shadow map.
 *
 * <p>Writing vertices to Minecraft's buffers, where Iris processes each vertex
 * and each quad, is the main cost, so the copy keeps as few as a shadow map can
 * tell apart: nearby vertices are merged ({@link #simplify}), triangles are paired
 * into real quads instead of one degenerate quad each ({@link #toQuads}), and a
 * coarser copy ({@link #far()}) serves distant models.
 *
 * <p>Stored compactly (about 30 bytes per vertex instead of RareCandy's 52):
 * normals and bone weights are quantised to bytes, and indices use 16 bits when
 * the mesh allows it.
 */
public final class RareCandyShadowMesh {
    // Layout written by RareCandy 2.14.2's ModelLoader (ModelLoader.ATTRIBUTES):
    // position 3f, texcoord 2f, normal 3f, bone ids 4ub, bone weights 4f.
    private static final int STRIDE = 52;
    private static final int UV = 12, NORMAL = 20, JOINTS = 32, WEIGHTS = 36;
    private static final int OUT = 7; // posed x, y, z, normal x, y, z, visible

    // Cells per side of the simplification grid (at most 255: 8 bits per axis in
    // the cluster key), and the size under which a mesh is kept as it is.
    private static final int NEAR_GRID = 40, NEAR_MIN_VERTICES = 1500;
    private static final int FAR_GRID = 16, FAR_MIN_VERTICES = 300;

    private static float[] scratch = new float[0];

    private final int vertexCount;
    private final float[] positions;
    private final float[] uvs;
    private final byte[] normals;
    private final byte[] joints;
    private final byte[] weights;
    /** Four vertices per quad. One of the two is set: 16 bits when every vertex fits. */
    private final char[] shortQuads;
    private final int[] intQuads;
    private RareCandyShadowMesh far = this;

    private RareCandyShadowMesh(int vertexCount, int quadIndexCount) {
        this.vertexCount = vertexCount;
        this.positions = new float[vertexCount * 3];
        this.uvs = new float[vertexCount * 2];
        this.normals = new byte[vertexCount * 3];
        this.joints = new byte[vertexCount * 4];
        this.weights = new byte[vertexCount * 4];
        boolean small = vertexCount <= 0x10000;
        this.shortQuads = small ? new char[quadIndexCount] : null;
        this.intQuads = small ? null : new int[quadIndexCount];
    }

    /** Coarser copy for distant models (this mesh when simplifying further does not help). */
    public RareCandyShadowMesh far() {
        return far;
    }

    /**
     * Reads a mesh back from the GPU buffers RareCandy uploaded (it frees its own
     * CPU copy after upload). Only done the first time the mesh casts a shadow,
     * so nothing is kept in memory without a shader pack. Render thread only.
     *
     * @return null if the buffers are not uploaded yet or the layout is unknown
     */
    public static RareCandyShadowMesh readBack(int vbo, int ebo, int indexCount, int indexType,
                                               List<Attribute> attributes) {
        if (vbo <= 0 || ebo <= 0) return null;

        // GL_COPY_READ_BUFFER is not part of any VAO, so binding it disturbs nothing.
        // In GL 3.1 the same enum is also the binding query (GL 4.2 only adds an alias).
        int previous = GL11C.glGetInteger(GL31C.GL_COPY_READ_BUFFER);
        ByteBuffer vertices = null, indices = null;
        try {
            GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER, vbo);
            int vertexBytes = GL15C.glGetBufferParameteri(GL31C.GL_COPY_READ_BUFFER, GL15C.GL_BUFFER_SIZE);
            GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER, ebo);
            int indexBytes = GL15C.glGetBufferParameteri(GL31C.GL_COPY_READ_BUFFER, GL15C.GL_BUFFER_SIZE);
            if (vertexBytes <= 0 || indexBytes < indexCount * 4) return null;

            indices = MemoryUtil.memAlloc(indexCount * 4);
            GL15C.glGetBufferSubData(GL31C.GL_COPY_READ_BUFFER, 0, indices);
            GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER, vbo);
            vertices = MemoryUtil.memAlloc(vertexBytes);
            GL15C.glGetBufferSubData(GL31C.GL_COPY_READ_BUFFER, 0, vertices);

            Source source = Source.read(vertices, indices, indexCount, indexType, attributes);
            if (source == null) return null;
            RareCandyShadowMesh near = build(source, NEAR_GRID, NEAR_MIN_VERTICES);
            RareCandyShadowMesh far = build(source, FAR_GRID, FAR_MIN_VERTICES);
            if (far.quadIndexCount() < near.quadIndexCount()) near.far = far;
            return near;
        } finally {
            GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER, previous);
            if (vertices != null) MemoryUtil.memFree(vertices);
            if (indices != null) MemoryUtil.memFree(indices);
        }
    }

    /** The full-resolution mesh as uploaded by RareCandy. */
    private record Source(int vertexCount, float[] positions, float[] uvs, float[] normals,
                          byte[] joints, float[] weights, int[] triangles) {
        /** Returns null for any layout this class does not understand. */
        static Source read(ByteBuffer vertices, ByteBuffer indices, int indexCount, int indexType,
                           List<Attribute> attributes) {
            if (!ModelLoader.ATTRIBUTES.equals(attributes) || indexType != GL11C.GL_UNSIGNED_INT) return null;

            int vertexStart = vertices.position();
            int vertexCount = (vertices.limit() - vertexStart) / STRIDE;
            int indexStart = indices.position();
            if (vertexCount == 0 || indexCount % 3 != 0 || indices.limit() - indexStart < indexCount * 4) return null;

            var source = new Source(vertexCount, new float[vertexCount * 3], new float[vertexCount * 2],
                    new float[vertexCount * 3], new byte[vertexCount * 4], new float[vertexCount * 4],
                    new int[indexCount]);
            for (int v = 0; v < vertexCount; v++) {
                int base = vertexStart + v * STRIDE;
                for (int i = 0; i < 3; i++) {
                    source.positions[v * 3 + i] = vertices.getFloat(base + i * 4);
                    source.normals[v * 3 + i] = vertices.getFloat(base + NORMAL + i * 4);
                }
                source.uvs[v * 2] = vertices.getFloat(base + UV);
                source.uvs[v * 2 + 1] = vertices.getFloat(base + UV + 4);
                for (int i = 0; i < 4; i++) {
                    source.joints[v * 4 + i] = vertices.get(base + JOINTS + i);
                    source.weights[v * 4 + i] = vertices.getFloat(base + WEIGHTS + i * 4);
                }
            }
            for (int i = 0; i < indexCount; i++) {
                int index = indices.getInt(indexStart + i * 4);
                if (index < 0 || index >= vertexCount) return null;
                source.triangles[i] = index;
            }
            return source;
        }
    }

    private static RareCandyShadowMesh build(Source source, int grid, int minVertices) {
        // Merge nearby vertices, then drop the triangles that collapse.
        int[] remap = new int[source.vertexCount];
        int[] kept = new int[source.vertexCount];
        int keptCount = simplify(source, grid, minVertices, remap, kept);

        int[] triangles = new int[source.triangles.length];
        int triangleIndices = 0;
        for (int i = 0; i < source.triangles.length; i += 3) {
            int a = remap[source.triangles[i]];
            int b = remap[source.triangles[i + 1]];
            int c = remap[source.triangles[i + 2]];
            if (a == b || b == c || a == c) continue;
            triangles[triangleIndices++] = a;
            triangles[triangleIndices++] = b;
            triangles[triangleIndices++] = c;
        }
        int[] quads = toQuads(triangles, triangleIndices / 3);

        var mesh = new RareCandyShadowMesh(keptCount, quads.length);
        for (int k = 0; k < keptCount; k++) {
            int v = kept[k];
            for (int i = 0; i < 3; i++) {
                mesh.positions[k * 3 + i] = source.positions[v * 3 + i];
                mesh.normals[k * 3 + i] = quantizeSigned(source.normals[v * 3 + i]);
            }
            mesh.uvs[k * 2] = source.uvs[v * 2];
            mesh.uvs[k * 2 + 1] = source.uvs[v * 2 + 1];
            for (int i = 0; i < 4; i++) {
                mesh.joints[k * 4 + i] = source.joints[v * 4 + i];
                mesh.weights[k * 4 + i] = quantizeUnsigned(source.weights[v * 4 + i]);
            }
        }
        for (int i = 0; i < quads.length; i++) {
            if (mesh.shortQuads != null) mesh.shortQuads[i] = (char) quads[i];
            else mesh.intQuads[i] = quads[i];
        }
        return mesh;
    }

    /**
     * Vertex clustering: vertices in the same cell of a grid³ grid over the mesh,
     * and driven by the same main bone, become one. A shadow map texel is larger
     * than a cell. The main bone keeps limbs that touch from being welded.
     *
     * @param remap filled with the kept vertex each vertex maps to
     * @param kept  filled with the original vertex of each kept vertex
     * @return the number of kept vertices
     */
    private static int simplify(Source source, int grid, int minVertices, int[] remap, int[] kept) {
        int vertexCount = source.vertexCount;
        float[] positions = source.positions;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int v = 0; v < vertexCount; v++) {
            minX = Math.min(minX, positions[v * 3]); maxX = Math.max(maxX, positions[v * 3]);
            minY = Math.min(minY, positions[v * 3 + 1]); maxY = Math.max(maxY, positions[v * 3 + 1]);
            minZ = Math.min(minZ, positions[v * 3 + 2]); maxZ = Math.max(maxZ, positions[v * 3 + 2]);
        }
        float cell = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) / grid;

        if (vertexCount < minVertices || !(cell > 0) || !Float.isFinite(cell)) {
            for (int v = 0; v < vertexCount; v++) {
                remap[v] = v;
                kept[v] = v;
            }
            return vertexCount;
        }

        var clusters = new HashMap<Long, Integer>(vertexCount);
        int keptCount = 0;
        for (int v = 0; v < vertexCount; v++) {
            long x = (long) ((positions[v * 3] - minX) / cell);
            long y = (long) ((positions[v * 3 + 1] - minY) / cell);
            long z = (long) ((positions[v * 3 + 2] - minZ) / cell);

            // Unweighted vertices are clipped on the GPU: keep them apart (256).
            int mainBone = 256;
            float mainWeight = 0;
            for (int i = 0; i < 4; i++) {
                if (source.weights[v * 4 + i] > mainWeight) {
                    mainWeight = source.weights[v * 4 + i];
                    mainBone = source.joints[v * 4 + i] & 0xFF;
                }
            }

            long key = x | y << 8 | z << 16 | (long) mainBone << 24;
            Integer cluster = clusters.get(key);
            if (cluster == null) {
                cluster = keptCount;
                kept[keptCount++] = v;
                clusters.put(key, cluster);
            }
            remap[v] = cluster;
        }
        return keptCount;
    }

    /**
     * Pairs triangles that share an edge into quads, greedily. Minecraft's entity
     * buffers only take quads, drawn as triangles (0,1,2) and (2,3,0): the quad
     * (u, x, v, w) is split along u-v, the shared edge, so both original
     * triangles are kept exactly. Unpaired triangles repeat their last vertex.
     */
    private static int[] toQuads(int[] triangles, int triangleCount) {
        var openEdges = new HashMap<Long, Integer>(triangleCount * 2);
        boolean[] paired = new boolean[triangleCount];
        int[] quads = new int[triangleCount * 4];
        int n = 0;

        for (int t = 0; t < triangleCount; t++) {
            for (int e = 0; e < 3 && !paired[t]; e++) {
                int u = triangles[t * 3 + e];
                int v = triangles[t * 3 + (e + 1) % 3];
                int w = triangles[t * 3 + (e + 2) % 3];
                Integer other = openEdges.get(edgeKey(u, v));
                if (other == null || paired[other]) continue;

                int x = opposite(triangles, other, u, v);
                quads[n++] = u;
                quads[n++] = x;
                quads[n++] = v;
                quads[n++] = w;
                paired[t] = paired[other] = true;
            }
            if (!paired[t]) {
                for (int e = 0; e < 3; e++) {
                    openEdges.put(edgeKey(triangles[t * 3 + e], triangles[t * 3 + (e + 1) % 3]), t);
                }
            }
        }

        for (int t = 0; t < triangleCount; t++) {
            if (paired[t]) continue;
            quads[n++] = triangles[t * 3];
            quads[n++] = triangles[t * 3 + 1];
            quads[n++] = triangles[t * 3 + 2];
            quads[n++] = triangles[t * 3 + 2];
        }
        return Arrays.copyOf(quads, n);
    }

    private static long edgeKey(int u, int v) {
        return u < v ? (long) u << 32 | v : (long) v << 32 | u;
    }

    private static int opposite(int[] triangles, int triangle, int u, int v) {
        for (int i = 0; i < 3; i++) {
            int vertex = triangles[triangle * 3 + i];
            if (vertex != u && vertex != v) return vertex;
        }
        return triangles[triangle * 3];
    }

    private int quadIndexCount() {
        return shortQuads != null ? shortQuads.length : intQuads.length;
    }

    /** Skinned like animated.vs.glsl: {@code pose * sum(weight * bone) * position}. */
    public void emit(VertexConsumer consumer, Matrix4f pose, Matrix3f normalMatrix, Matrix4f[] bones,
                     float uScale, float vScale, float uOffset, float vOffset) {
        float[] out = scratch(vertexCount * OUT);

        for (int v = 0; v < vertexCount; v++) {
            float x = positions[v * 3], y = positions[v * 3 + 1], z = positions[v * 3 + 2];
            float nx = normals[v * 3], ny = normals[v * 3 + 1], nz = normals[v * 3 + 2];
            float sx = 0, sy = 0, sz = 0, sw = 0, snx = 0, sny = 0, snz = 0;

            for (int i = 0; i < 4; i++) {
                int quantized = weights[v * 4 + i] & 0xFF;
                int joint = joints[v * 4 + i] & 0xFF;
                if (quantized == 0 || joint >= bones.length) continue;
                float w = quantized / 255F;
                Matrix4f b = bones[joint];
                if (b == null) {
                    sx += w * x; sy += w * y; sz += w * z; sw += w;
                    snx += w * nx; sny += w * ny; snz += w * nz;
                    continue;
                }
                sx += w * (b.m00() * x + b.m10() * y + b.m20() * z + b.m30());
                sy += w * (b.m01() * x + b.m11() * y + b.m21() * z + b.m31());
                sz += w * (b.m02() * x + b.m12() * y + b.m22() * z + b.m32());
                sw += w * (b.m03() * x + b.m13() * y + b.m23() * z + b.m33());
                snx += w * (b.m00() * nx + b.m10() * ny + b.m20() * nz);
                sny += w * (b.m01() * nx + b.m11() * ny + b.m21() * nz);
                snz += w * (b.m02() * nx + b.m12() * ny + b.m22() * nz);
            }

            int o = v * OUT;
            // The GPU path produces w = 0 here, which clips the vertex away.
            if (sw == 0) {
                out[o + 6] = 0;
                continue;
            }
            sx /= sw; sy /= sw; sz /= sw;

            out[o] = pose.m00() * sx + pose.m10() * sy + pose.m20() * sz + pose.m30();
            out[o + 1] = pose.m01() * sx + pose.m11() * sy + pose.m21() * sz + pose.m31();
            out[o + 2] = pose.m02() * sx + pose.m12() * sy + pose.m22() * sz + pose.m32();

            float qx = normalMatrix.m00() * snx + normalMatrix.m10() * sny + normalMatrix.m20() * snz;
            float qy = normalMatrix.m01() * snx + normalMatrix.m11() * sny + normalMatrix.m21() * snz;
            float qz = normalMatrix.m02() * snx + normalMatrix.m12() * sny + normalMatrix.m22() * snz;
            float length = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (length > 1.0E-6F) {
                qx /= length; qy /= length; qz /= length;
            } else {
                qx = 0; qy = 1; qz = 0;
            }
            out[o + 3] = qx;
            out[o + 4] = qy;
            out[o + 5] = qz;
            out[o + 6] = 1;
        }

        int quadIndexCount = quadIndexCount();
        for (int i = 0; i < quadIndexCount; i += 4) {
            int a = index(i), b = index(i + 1), c = index(i + 2), d = index(i + 3);
            if (out[a * OUT + 6] == 0 || out[b * OUT + 6] == 0
                    || out[c * OUT + 6] == 0 || out[d * OUT + 6] == 0) continue;
            vertex(consumer, out, a, uScale, vScale, uOffset, vOffset);
            vertex(consumer, out, b, uScale, vScale, uOffset, vOffset);
            vertex(consumer, out, c, uScale, vScale, uOffset, vOffset);
            vertex(consumer, out, d, uScale, vScale, uOffset, vOffset);
        }
    }

    private int index(int i) {
        return shortQuads != null ? shortQuads[i] : intQuads[i];
    }

    private void vertex(VertexConsumer consumer, float[] out, int v,
                        float uScale, float vScale, float uOffset, float vOffset) {
        int o = v * OUT;
        consumer.addVertex(out[o], out[o + 1], out[o + 2], 0xFFFFFFFF,
                uvs[v * 2] * uScale + uOffset, uvs[v * 2 + 1] * vScale + vOffset,
                OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT,
                out[o + 3], out[o + 4], out[o + 5]);
    }

    /** [-1, 1] to a signed byte. Only the direction matters: it is renormalised after skinning. */
    private static byte quantizeSigned(float value) {
        return (byte) Math.round(Math.max(-1F, Math.min(1F, value)) * 127F);
    }

    /** [0, 1] to an unsigned byte. Skinning divides by the weight sum, so rounding barely moves vertices. */
    private static byte quantizeUnsigned(float value) {
        return (byte) Math.round(Math.max(0F, Math.min(1F, value)) * 255F);
    }

    /** Render thread only. */
    private static float[] scratch(int size) {
        if (scratch.length < size) scratch = new float[size];
        return scratch;
    }
}
