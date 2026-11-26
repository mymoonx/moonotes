package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * UV-only mirror of one Streamotes image quad.
 *
 * Negative X/Y scaling reverses quad winding, so the text rendering pipeline
 * may cull an otherwise valid flipped emote. Instead keep the original four
 * vertex positions and winding and mirror UV coordinates inside the current
 * frame's actual bounds. That works for sprite-sheet animation too.
 *
 * Streamotes GlyphCommons emits exactly four chained vertices per draw:
 * addVertex(matrix,...) -> setColor -> setUv -> setLight.
 * This adapter only wraps the known Streamotes EmoticonGlyph, never generic
 * Minecraft fonts or entities.
 */
final class BttvMirroredVertices implements VertexConsumer {
    private static final int QUAD = 4;
    private final VertexConsumer target;
    private final boolean horizontal;
    private final boolean vertical;
    private final Vertex[] quad = new Vertex[QUAD];
    private int filled = 0;
    private Vertex current;

    private static final class Vertex {
        float x, y, z, u, v, nx, ny, nz, u3, v3, lineWidth;
        int packedColor = 0xFFFFFFFF;
        int uv1u, uv1v, uv2u, uv2v;
        boolean colored, uv, hasUv1, hasUv2, hasUv3, hasNormal, hasLineWidth;
    }

    BttvMirroredVertices(VertexConsumer target, boolean horizontal, boolean vertical) {
        this.target = target;
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (filled == QUAD) flush();
        Vertex point = new Vertex();
        point.x = x;
        point.y = y;
        point.z = z;
        quad[filled++] = point;
        current = point;
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        if (current != null) {
            current.packedColor = ((alpha & 255) << 24)
                | ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);
            current.colored = true;
        }
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        if (current != null) {
            current.packedColor = color;
            current.colored = true;
        }
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        if (current != null) {
            current.u = u; current.v = v;
            current.uv = true;
        }
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        if (current != null) {
            current.uv1u = u; current.uv1v = v;
            current.hasUv1 = true;
        }
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        if (current != null) {
            current.uv2u = u; current.uv2v = v;
            current.hasUv2 = true;
        }
        return this;
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        if (current != null) {
            current.u3 = u; current.v3 = v;
            current.hasUv3 = true;
        }
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        if (current != null) {
            current.nx = x; current.ny = y; current.nz = z;
            current.hasNormal = true;
        }
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        if (current != null) {
            current.lineWidth = width;
            current.hasLineWidth = true;
        }
        return this;
    }

    static float mirror(float coordinate, float minimum, float maximum) {
        return minimum + maximum - coordinate;
    }

    /** Called once after the source image's render() completes. */
    void finish() { flush(); }

    private void flush() {
        if (filled == 0) return;
        if (filled != QUAD) {
            // Fail closed on a future Streamotes renderer with non-quad output.
            // Do not reverse triangle winding or invent missing vertices.
            for (int i = 0; i < filled; i++) submit(quad[i], quad[i].u, quad[i].v);
            reset();
            return;
        }
        float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < QUAD; i++) {
            minU = Math.min(minU, quad[i].u);
            maxU = Math.max(maxU, quad[i].u);
            minV = Math.min(minV, quad[i].v);
            maxV = Math.max(maxV, quad[i].v);
        }
        boolean valid = Float.isFinite(minU) && Float.isFinite(maxU)
            && Float.isFinite(minV) && Float.isFinite(maxV);
        for (int i = 0; i < QUAD; i++) {
            Vertex v = quad[i];
            float u = valid && horizontal ? mirror(v.u, minU, maxU) : v.u;
            float y = valid && vertical ? mirror(v.v, minV, maxV) : v.v;
            submit(v, u, y);
        }
        reset();
    }

    private void submit(Vertex v, float u, float uvV) {
        VertexConsumer dst = target.addVertex(v.x, v.y, v.z);
        if (v.colored) dst.setColor(v.packedColor);
        if (v.uv) dst.setUv(u, uvV);
        if (v.hasUv1) dst.setUv1(v.uv1u, v.uv1v);
        if (v.hasUv2) dst.setUv2(v.uv2u, v.uv2v);
        if (v.hasUv3) dst.setUv3(v.u3, v.v3);
        if (v.hasNormal) dst.setNormal(v.nx, v.ny, v.nz);
        if (v.hasLineWidth) dst.setLineWidth(v.lineWidth);
    }

    private void reset() {
        for (int i = 0; i < filled; i++) quad[i] = null;
        filled = 0;
        current = null;
    }
}
