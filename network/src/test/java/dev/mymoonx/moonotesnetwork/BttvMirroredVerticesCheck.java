package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;

/** Prevent mirror effects being culled by reversed vertex winding. */
public final class BttvMirroredVerticesCheck {
    private static final float EPS = 0.00001f;
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static void close(float actual, float expected, String label) {
        check(Math.abs(actual - expected) < EPS,
            label + " actual " + actual + " expected " + expected);
    }

    private static final class Point {
        float x, y, z, u, v;
        int light, color;
    }
    private static final class Capture implements VertexConsumer {
        final List<Point> points = new ArrayList<>();
        Point current;
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            current = new Point();
            current.x = x; current.y = y; current.z = z;
            points.add(current);
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            current.color = (a << 24) | (r << 16) | (g << 8) | b;
            return this;
        }
        @Override public VertexConsumer setUv(float u, float v) {
            current.u = u; current.v = v; return this;
        }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) {
            current.light = (v << 16) | u; return this;
        }
        @Override public VertexConsumer setUv3(float u, float v) { return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }

    private static List<Point> render(boolean horizontal, boolean vertical) {
        Capture result = new Capture();
        BttvMirroredVertices wrapped = new BttvMirroredVertices(
            result, horizontal, vertical);
        // Crop within an animated sprite sheet: frame U=[.25,.5],
        // frame V=[.5,.75]. The output must not sample adjacent frames.
        float[] xx = {0,1,1,0};
        float[] yy = {1,1,0,0};
        float[] uu = {.25f,.5f,.5f,.25f};
        float[] vv = {.75f,.75f,.5f,.5f};
        for (int i=0; i<4; i++)
            wrapped.addVertex(xx[i], yy[i], 0)
                .setColor(0x80ABCDEF).setUv(uu[i], vv[i]).setLight(0x00F000F0);
        check(result.points.isEmpty(), "wait until all UVs are available");
        wrapped.finish();
        check(result.points.size() == 4, "one complete quad emitted");
        for (int i=0; i<4; i++) {
            Point p = result.points.get(i);
            close(p.x,xx[i],"vertex X keeps winding");
            close(p.y,yy[i],"vertex Y keeps winding");
            close(p.u,horizontal ? .75f - uu[i] : uu[i],"sprite-frame U");
            close(p.v,vertical ? 1.25f - vv[i] : vv[i],"sprite-frame V");
            check(p.color == 0x80ABCDEF, "pixel ARGB preserved");
            check(p.light == 0x00F000F0, "packed light preserved");
        }
        return result.points;
    }

    public static void main(String[] args) {
        render(true, false);
        render(false, true);
        render(false, false);
        close(BttvMirroredVertices.mirror(.25f,.25f,.5f),.5f,
            "horizontal mirror within current frame");
        close(BttvMirroredVertices.mirror(.75f,.5f,.75f),.5f,
            "vertical mirror within current frame");
        System.out.println("BttvMirroredVerticesCheck: " + checks + " checks passed");
    }
}
