package dev.mymoonx.moonotesnetwork;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bounded, provider-neutral gradient paint model.
 * Only color/gradient paints are rendered at this stage.
 * This does NOT guess or access undocumented Chatterino+ endpoints.
 */
final class PaintStyle {
    record Stop(double at, int color) { }
    final String name;
    final List<Stop> stops;
    final String imageUrl;
    final String kind;
    private PaintStyle(String name, List<Stop> stops, String imageUrl, String kind) {
        this.name = name;
        this.stops = List.copyOf(stops);
        this.imageUrl = imageUrl;
        this.kind = kind;
    }

    static PaintStyle parse(String input) {
        if (input == null || input.length() > 65536) throw new IllegalArgumentException("Invalid paint data size");
        JsonElement raw;
        try { raw = JsonParser.parseString(input); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid paint JSON", e); }
        if (!raw.isJsonObject()) throw new IllegalArgumentException("Expected paint object");
        JsonObject root = raw.getAsJsonObject();
        if (root.has("paint") && root.get("paint").isJsonObject()) root = root.getAsJsonObject("paint");
        String label = field(root, "name");
        if (root.has("data") && root.get("data").isJsonObject()) root = root.getAsJsonObject("data");
        if (label.isEmpty()) label = field(root, "name");
        if (label.isEmpty()) label = "Импортированный пейнт";
        if (label.length() > 64) label = label.substring(0, 64);

        List<Stop> stops = new ArrayList<>();
        String image = "";
        String kind = "color";
        if (root.has("gradients") && root.get("gradients").isJsonArray()) {
            JsonArray gradients = root.getAsJsonArray("gradients");
            // Multi-layer gradients require compositing; use the first supported layer.
            for (JsonElement layer : gradients) {
                if (!layer.isJsonObject()) continue;
                JsonObject obj = layer.getAsJsonObject();
                String function = field(obj, "function").toUpperCase(java.util.Locale.ROOT);
                if ("URL".equals(function)) {
                    if (image.isEmpty()) image = field(obj, "image_url");
                    continue;
                }
                if (!obj.has("stops") || !obj.get("stops").isJsonArray()) continue;
                List<Stop> parsed = parseStops(obj.getAsJsonArray("stops"));
                if (parsed.size() > 0) {
                    stops.addAll(parsed);
                    kind = "RADIAL_GRADIENT".equals(function) ? "radial" : "linear";
                    break;
                }
            }
        }
        if (stops.isEmpty() && root.has("stops") && root.get("stops").isJsonArray()) {
            stops.addAll(parseStops(root.getAsJsonArray("stops")));
            if (!stops.isEmpty()) kind = "linear";
        }
        if (image.isEmpty()) image = field(root, "image_url");
        // Images are metadata only until an authorized provider and image renderer are available.
        if (stops.isEmpty() && root.has("color")) {
            stops.add(new Stop(0.0, rgba(color(root.get("color")))));
            stops.add(new Stop(1.0, rgba(color(root.get("color")))));
        }
        if (stops.isEmpty()) throw new IllegalArgumentException("No supported color stops; image-only paints not yet rendered");
        if (stops.size() == 1) stops.add(new Stop(1, stops.get(0).color()));
        stops.sort(Comparator.comparingDouble(Stop::at));
        return new PaintStyle(label, stops, image, kind);
    }

    private static List<Stop> parseStops(JsonArray input) {
        if (input.size() > 32) throw new IllegalArgumentException("Too many color stops");
        List<Stop> result = new ArrayList<>();
        for (JsonElement value : input) {
            if (!value.isJsonObject()) continue;
            JsonObject stop = value.getAsJsonObject();
            if (!stop.has("at") || !stop.has("color")) continue;
            double at;
            try { at = stop.get("at").getAsDouble(); }
            catch (RuntimeException e) { continue; }
            if (!Double.isFinite(at)) continue;
            // Supports fractions (0..1) and percentages (0..100).
            if (at > 1) at /= 100;
            at = Math.max(0, Math.min(1, at));
            try { result.add(new Stop(at, rgba(color(stop.get("color"))))); }
            catch (RuntimeException ignored) { }
        }
        return result;
    }
    private static int color(JsonElement value) {
        if (value.getAsJsonPrimitive().isNumber()) return value.getAsInt();
        String v = value.getAsString().strip();
        if (v.startsWith("#")) return (int) Long.parseLong(v.substring(1), 16);
        return (int) Long.parseLong(v);
    }
    private static int rgba(int argb) {
        return (argb & 0x00FFFFFF) | 0xFF000000;
    }
    private static String field(JsonObject obj, String key) {
        try { return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : ""; }
        catch (RuntimeException ex) { return ""; }
    }

    int sample(double point) {
        if (!Double.isFinite(point)) point = 0;
        point = Math.max(0, Math.min(1, point));
        Stop before = stops.get(0);
        if (point <= before.at()) return before.color();
        for (int i = 1; i < stops.size(); i++) {
            Stop after = stops.get(i);
            if (point <= after.at()) {
                double width = after.at() - before.at();
                return blend(before.color(), after.color(), width <= 0 ? 1 : (point - before.at()) / width);
            }
            before = after;
        }
        return stops.get(stops.size() - 1).color();
    }
    private static int blend(int a, int b, double p) {
        p = Math.max(0, Math.min(1, p));
        int red = (int) Math.round(((a >>> 16) & 255) * (1 - p) + ((b >>> 16) & 255) * p);
        int green = (int) Math.round(((a >>> 8) & 255) * (1 - p) + ((b >>> 8) & 255) * p);
        int blue = (int) Math.round((a & 255) * (1 - p) + (b & 255) * p);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
    MutableComponent colored(String text, boolean animated, long nowMillis) {
        MutableComponent out = Component.empty();
        int[] chars = text.codePoints().toArray();
        if (chars.length == 0 || chars.length > 128) return Component.literal(text);
        double phase = animated ? (nowMillis % 6000L) / 6000.0 : 0;
        for (int i = 0; i < chars.length; i++) {
            double at = chars.length < 2 ? 0.5 : i / (double) (chars.length - 1);
            // Keep within the gradient boundaries: ping-pong to avoid discontinuities.
            double progress = animated ? Math.abs(((at + phase) % 1.0) * 2 - 1) : at;
            int color = sample(progress);
            out.append(Component.literal(new String(Character.toChars(chars[i]))).withStyle(s -> s.withColor(color & 0xFFFFFF)));
        }
        return out;
    }
}
