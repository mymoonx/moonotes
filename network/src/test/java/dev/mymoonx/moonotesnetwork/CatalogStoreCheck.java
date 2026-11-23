package dev.mymoonx.moonotesnetwork;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Metadata integrity, alias identity, offline persistence and source fallback policy. */
public final class CatalogStoreCheck {
    private static final String A = "01f0hmz1zg0004v54c00a616cb", B = "01g4m2821r000dsyz0tdha3zkx", S = "01f0hmz1zg0004v54c00a616cc";
    private static int count;
    private static void expect(boolean ok, String message) { count++; if (!ok) throw new AssertionError(message); }
    private interface Action { void run() throws Exception; }
    private static void rejects(Action action, String message) throws Exception {
        try { action.run(); throw new AssertionError(message); } catch (IOException expected) { count++; }
    }
    private static String json(String entries) { return "{\"name\":\"Example\",\"emotes\":[" + entries + "]}"; }
    private static String entry(String id, String name) { return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}"; }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("moonotes-catalog-check");
        try {
            String initial = json(entry(A,"One") + "," + entry(A,"Alias") + "," + entry(A,"One") + "," + entry(B,"Two"));
            var parsed = CatalogStore.parse(initial, "fallback");
            expect(parsed.emotes().size() == 3, "retain aliases, deduplicate exact identity");
            expect(parsed.emotes().getFirst().animated() == null, "legacy unknown animation is retained");
            String animated = "{\"id\":\"" + A + "\",\"name\":\"Anim\",\"data\":{\"animated\":true}}";
            expect(Boolean.TRUE.equals(CatalogStore.parse(json(animated), "x").emotes().getFirst().animated()), "animation hint preserved");
            rejects(() -> CatalogStore.parse(json("{\"id\":\"" + A + "\",\"name\":\"Anim\",\"animated\":\"true\"}"), "x"), "nonboolean animation hint rejected");
            var store = new CatalogStore(dir.resolve("catalogs.json")); store.bootstrap(initial); store.save();
            expect(store.allEnabled().size() == 3, "offline bootstrap");
            store.update("channel:v0kky", CatalogStore.parse(json(entry(A,"One")), "v0kky"), "test");
            expect(store.get("channel:v0kky").added() == 0 && store.get("channel:v0kky").removed() == 2, "incremental diff uses aliases");
            store.update("channel:v0kky", CatalogStore.parse(json(""), "v0kky"), "test");
            expect(store.get("channel:v0kky").emotes().isEmpty() && store.get("channel:v0kky").removed() == 1, "valid empty set removes every entry");
            var added = store.add("set", S, parsed, "test");
            expect(store.selected().equals("set:" + S), "new catalog is selected");
            int beforeAdded = added.added(), beforeRemoved = added.removed();
            store.enrichMissingAnimation(Map.of(A, true, B, false));
            var enriched = store.get(added.key());
            expect(Boolean.TRUE.equals(enriched.emotes().getFirst().animated()), "offline bundled fact enriches legacy animation");
            expect(enriched.emotes().getFirst().name().equals("One") && enriched.added() == beforeAdded && enriched.removed() == beforeRemoved,
                "enrichment leaves aliases and sync diff unchanged");
            expect(store.selected().equals(added.key()), "enrichment preserves selection");
            store.enrichMissingAnimation(Map.of(A, false));
            expect(Boolean.TRUE.equals(store.get(added.key()).emotes().getFirst().animated()), "known flag is never overwritten");
            store.toggle(added.key()); expect(store.visible().isEmpty(), "disabled catalog hidden");
            store.toggle(added.key()); expect(store.visible().size() == 3, "reenable restores aliases");
            store.select("all"); store.save();
            var restored = new CatalogStore(dir.resolve("catalogs.json")); restored.load();
            expect(restored.allEnabled().equals(store.allEnabled()), "offline restore keeps entries");
            expect(restored.selected().equals("all"), "selection survives reload");
            Path sharedPng = dir.resolve("7tv-" + A + ".png"); Files.writeString(sharedPng, "do not delete");
            restored.remove(added.key()); restored.remove("channel:v0kky");
            expect(Files.readString(sharedPng).equals("do not delete"), "metadata removal never deletes shared image");
            restored.load(); expect(restored.catalogs().isEmpty(), "intentional empty catalogue store persists");
            restored.enrichMissingAnimation(Map.of(A, true));
            expect(restored.catalogs().isEmpty(), "enrichment never resurrects a deleted catalogue");
            rejects(() -> CatalogStore.parse(json(entry("../../bad", "Bad")), "x"), "path traversal rejected");
            rejects(() -> CatalogStore.parse(json(entry(A,"has space")), "x"), "whitespace name rejected");
            rejects(() -> CatalogStore.parse(json(entry(A,"§cRed")), "x"), "minecraft formatting rejected");
            rejects(() -> CatalogStore.parse(json(entry(A,"/op")), "x"), "command-like name rejected");
            rejects(() -> CatalogStore.parse(json(entry(A,"bad\\u202Ename")), "x"), "bidi rejected");
            rejects(() -> CatalogStore.parse(json(entry(A,"good") + "," + entry("bad", "bad")), "x"), "bad entry cannot silently shrink catalogue");
            rejects(() -> CatalogStore.parse("[".repeat(25) + "]".repeat(25), "x"), "deep JSON rejected");
            rejects(() -> CatalogStore.parse("x".repeat(CatalogStore.MAX_JSON + 1), "x"), "oversized JSON rejected");
            rejects(() -> CatalogStore.channel("abc/../x"), "unsafe channel rejected");
            expect(CatalogStore.channel(" V0KKY ").equals("v0kky"), "channel normalization");
            String zero = "{\"id\":\"" + A + "\",\"name\":\"Overlay\",\"data\":{\"flags\":256}}";
            expect(CatalogStore.parse(json(zero), "x").emotes().getFirst().zeroWidth(), "7TV zero-width flag retained");
            for (int i = 0; i < CatalogStore.MAX_CATALOGS; i++) restored.add("channel", "user" + i, parsed, "test");
            rejects(() -> restored.add("channel", "overflow", parsed, "test"), "catalog count is bounded");
            var routes = new ArrayList<java.net.URI>();
            CatalogTransport transport = new CatalogTransport(request -> {
                var uri = request.uri(); routes.add(uri); if (uri.getHost().equals("7tv.io")) throw new IOException("blocked");
                return uri.getPath().contains("/channel/") ? initial.replace("\"name\":\"Example\"", "\"channel\":\"v0kky\"") : initial.replace("{\"name\"", "{\"setId\":\"" + S + "\",\"name\"");
            });
            expect(transport.fetch("set", S, "", "auto").source().equals("Cloudflare"), "set can fallback to Worker");
            expect(routes.size() == 2 && routes.getFirst().getHost().equals("7tv.io") && routes.getLast().getPath().endsWith("/set/" + S), "correct direct then Worker routes");
            routes.clear(); rejects(() -> transport.fetch("set", S, "", "direct"), "forced direct never proxies");
            expect(routes.size() == 1, "only forced source used");
            routes.clear();
            var channelFallback = transport.fetch("channel", "v0kky", "", "auto");
            expect(channelFallback.source().equals("Cloudflare"), "Twitch channel falls back when direct 7TV is unavailable");
            expect(routes.size() == 2
                    && "7tv.io".equals(routes.getFirst().getHost())
                    && "/v3/gql".equals(routes.getFirst().getPath())
                    && routes.getFirst().getRawQuery() == null
                    && "moonotes-emotes.thefreelom.workers.dev".equals(routes.getLast().getHost())
                    && "/v1/7tv/channel/v0kky".equals(routes.getLast().getPath()),
                "Twitch lookup tries 7TV first, then exact-channel Worker fallback");
            CatalogTransport stale = new CatalogTransport(request -> { throw new IOException("blocked"); });
            rejects(() -> stale.fetch("channel", "v0kky", S, "direct"), "known set cannot silently hide a channel connection failure");
            rejects(() -> transport.fetch("set", "https://evil.invalid/", "", "auto"), "arbitrary endpoint rejected");
            CatalogTransport wrong = new CatalogTransport(request -> initial);
            rejects(() -> wrong.fetch("channel", "v0kky", "", "auto"), "worker cannot substitute another channel");
            CatalogTransport wrongSet = new CatalogTransport(request -> "{\"setId\":\"" + A + "\",\"name\":\"Other\",\"emotes\":[]}");
            rejects(() -> wrongSet.fetch("set", S, "", "worker"), "worker cannot substitute another set");
            System.out.println("CatalogStoreCheck: " + count + " checks passed");
        } finally {
            try (var paths = Files.walk(dir)) { for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
}
