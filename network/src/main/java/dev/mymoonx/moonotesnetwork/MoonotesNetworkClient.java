package dev.mymoonx.moonotesnetwork;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Independent client addon. At join, offer an explicit opt-in to download the
 * v0kky 7TV collection. Nothing is downloaded in bulk until user accepts.
 * Other emote providers and Streamotes settings remain untouched.
 */
public final class MoonotesNetworkClient implements ClientModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BUNDLED_CATALOG = "/assets/moonotes_network/v0kky-manifest.json";
    private static final String DEFAULT_WORKER = "https://moonotes-emotes.thefreelom.workers.dev";
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("moonotes-network.json");
    private static final Path MANIFEST_FILE = FabricLoader.getInstance().getConfigDir().resolve("moonotes-v0kky-manifest.json");
    private static final Path CATALOGS_FILE = FabricLoader.getInstance().getConfigDir().resolve("moonotes-catalogs.json");
    private static final CatalogStore CATALOGS = new CatalogStore(CATALOGS_FILE);
    private static final ServerCatalogProfiles PROFILES = new ServerCatalogProfiles(
        FabricLoader.getInstance().getConfigDir().resolve("moonotes-network-profiles.json"));
    private static volatile String activeWorldKey = ServerCatalogProfiles.worldKey("singleplayer");
    private static volatile Set<String> lastChatChannels = Set.of();
    private static int chatChannelCheckTicks;
    private static final CatalogTransport CATALOG_NETWORK = new CatalogTransport();
    private static volatile boolean catalogsInitialized;
    private static volatile long publishedCatalogRevision;
    private static volatile String catalogStatus = "";
    private static volatile String catalogErrorDetails = "";
    private static final AtomicLong CATALOG_PREVIEW_TOKEN = new AtomicLong();
    private static volatile PendingCatalogPreview pendingCatalogPreview;
    record CatalogPreview(long token, String kind, String reference, String name, String setId,
                          String source, int names, int files, List<String> sample) { }
    private record PendingCatalogPreview(CatalogPreview view, CatalogTransport.Result result) { }
    private static volatile Map<String, CacheFact> catalogCache = Map.of();
    private static volatile Map<String, String> visibleCatalogNames = Map.of();
    record CatalogView(String key, String name, String kind, boolean enabled, int total, int cached,
                       long lastSync, int added, int removed, String status) { }
    private record CacheFact(boolean valid, long bytes, long modified) { }
    private record CatalogViews(long revision, Map<String, CacheFact> files, List<CatalogView> views) { }
    private static volatile CatalogViews catalogViews;
    private static final CacheFact NO_CACHE = new CacheFact(false, 0, 0);
    private static volatile String manifestSource = "Ожидание каталога";
    private static volatile String lastDownloadFailure = "";
    private static volatile String lastFailedEmoteId = "";
    private static volatile String lastFailedEmoteKind = "";
    private static volatile String lastFailedDirect = "не проверялся", lastFailedWorker = "не проверялся";
    private static volatile String lastDownloadSource = "нет";
    private static volatile long directSuccess = 0, workerSuccess = 0, sourceFallbacks = 0;
    private static volatile long lastDirectLatencyMs = -1, lastWorkerLatencyMs = -1;
    private static volatile String manifestError = "";
    private static final ExecutorService IO = singleWorker("Moonotes-Catalog");
    private static final ExecutorService BULK = singleWorker("Moonotes-Prefetch");
    private static final ExecutorService CACHE_SCAN = singleWorker("Moonotes-Cache-Index");
    private static final Object PAUSE_LOCK = new Object();
    private static final TransferRate RATE = new TransferRate();
    private static final AtomicInteger ACTIVE_TRANSFERS = new AtomicInteger();
    private static final AtomicInteger QUEUED_DOWNLOADS = new AtomicInteger();
    private static final AtomicInteger TRANSFERRED_COUNT = new AtomicInteger();
    private static final AtomicLong RECEIVED_BYTES = new AtomicLong();
    private static final AtomicLong NETWORK_MILLIS = new AtomicLong();
    private static final AtomicBoolean CACHE_SCANNING = new AtomicBoolean();
    private static volatile CacheSnapshot cacheSnapshot;
    private static volatile Thread bulkThread;
    private static volatile long endedMillis, pausedAt, pauseMillis;
    private static volatile String stage = "Готово к загрузке";
    private static final ImageDownloader IMAGES = new ImageDownloader(DEFAULT_WORKER, new ImageDownloader.Observer() {
        @Override public void success(String source, long millis) { recordImageSuccess(source, millis); }
        @Override public void fallback() { synchronized (MoonotesNetworkClient.class) { if (config.localDiagnostics) sourceFallbacks++; } }
        @Override public void failure(String category) { if (config.localDiagnostics) lastDownloadFailure = category; }
    });

    private static ExecutorService singleWorker(String name) {
        return Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, name); t.setDaemon(true); return t; });
    }
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static volatile boolean paused = false, cancelled = false;
    private static volatile long lastTransferMillis = 0;
    private static volatile long conversionMillis = 0;
    private static volatile String currentEmote = "";
    private static volatile long startedMillis = 0;
    private static volatile Config config = new Config();
    private static volatile long cacheCheckedAt = 0;
    private static volatile int cachedCount = 0;
    private static volatile boolean cacheCheckValid = false;
    private static volatile int progress = 0, total = 0, failed = 0, downloaded = 0, alreadyCached = 0;
    private static volatile boolean inWorld, offeredInWorld;
    private static int joinDelay;
    private static volatile Manifest knownManifest;
    private static final AtomicBoolean MANIFEST_LOADING = new AtomicBoolean(false);

    private static final class Config {
        volatile boolean enabled = true;
        volatile boolean offerAtJoin = true;
        volatile boolean showCompletionInChat = true;
        volatile boolean joinOfferMigrated = true;
        volatile String imageSource = "auto"; // auto, direct, worker; no arbitrary hosts
        volatile boolean localDiagnostics = true;
        volatile boolean retryDownloads = true;
        volatile int parallelDownloads = 0; // 0 = automatic (4); 1, 2, 4 or 6
        volatile boolean autoCatalogSync = true;
        volatile boolean serverProfiles = true;
        volatile boolean chatSuggestions = true;
        volatile boolean chatPicker = true;
        volatile boolean hudProgress = true;
        volatile int hudCorner = 1; // 0 left top, 1 right top, 2 left bottom, 3 right bottom
        volatile boolean hudCustomPosition = false;
        volatile double hudPositionX = 1;
        volatile double hudPositionY = 0.12;
        volatile String noticeMode = "toast"; // toast, important, chat, off
        volatile boolean unifiedStreamotesNotifications = true;
        volatile long lastCatalogCheckMs = 0;
        volatile String releaseShownVersion = "";
    }
    private static final class Manifest {
        String channel;
        int count;
        int imageCount;
        List<EmoteItem> emotes;
    }
    private static final class EmoteItem {
        String id;
        String name;
    }

    @Override
    public void onInitializeClient() {
        readConfig();
        LegacyIdentityCleanup.run();
        MoonotesNotifications.register();
        StreamotesBridge.initialize();
        WheelKeybind.register();
        loadInitialManifest();
        requestCacheScan();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            inWorld = true;
            offeredInWorld = false;
            activeWorldKey = ServerCatalogProfiles.worldKey(
                client.getCurrentServer() == null ? "singleplayer" : client.getCurrentServer().ip);
            if (config.serverProfiles) {
                String preferred = PROFILES.getOrDefault(activeWorldKey, CATALOGS.selected());
                if ("all".equals(preferred) || CATALOGS.get(preferred) != null) {
                    try { CATALOGS.select(preferred); }
                    catch (IOException ignored) { /* A stale profile must not prevent joining. */ }
                }
            }
            publishCatalogs(); // Refresh chat priority when the active world's channels change.
            cacheCheckValid = false;
            requestCacheScan();
            if (knownManifest == null) loadInitialManifest();
            joinDelay = 100; // allow the world / other mods to finish initial setup
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            inWorld = false;
            MoonotesNotifications.clearTransient();
            // Keep the last validated manifest for the next world / offline use.
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        System.out.println("[Moonotes Network] Offline catalogues and opt-in prefetch available");
    }

    private void tick(Minecraft client) {
        if (!inWorld || client.player == null || !config.enabled) return;
        if (++chatChannelCheckTicks >= 40) {
            chatChannelCheckTicks=0;
            Set<String> current=StreamotesBridge.activeChannels();
            if (!current.equals(lastChatChannels)) {
                lastChatChannels=current;
                ChatPalette.publish(ChatPalette.fromCatalogs(CATALOGS.catalogs(), current));
            }
        }
        if (joinDelay > 0 && --joinDelay == 0 && !offeredInWorld) {
            long now = System.currentTimeMillis();
            if (config.autoCatalogSync && CatalogRefreshPolicy.due(config.lastCatalogCheckMs, now)) {
                config.lastCatalogCheckMs = now;
                saveConfig();
                requestManifest(false); // Metadata only; never downloads images automatically.
            } else if (config.offerAtJoin && knownManifest != null) {
                offeredInWorld = true;
                int missing = missing(knownManifest);
                if (missing > 0) IO.execute(() -> showJoinOffer(missing));
            }
        }
    }

    /** Called by Moonotes /moonotes through reflection; no bundled Streamotes code. */
    public static void openGui() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            mc.gui.setScreen(new MoonotesScreen());
            if (knownManifest == null) requestManifest(true);
        });
    }

    private static void requestManifest(boolean manual) {
        if (!config.enabled) { if (manual) chat("Загрузка смайликов отключена в настройках"); return; }
        if (!inWorld || (!manual && offeredInWorld)) return;
        offeredInWorld = true;
        if (!MANIFEST_LOADING.compareAndSet(false, true)) return;
        List<CatalogStore.Catalog> snapshot = CATALOGS.catalogs();
        String selection = manual ? CATALOGS.selected() : "all";
        IO.execute(() -> {
            boolean offered = false;
            try {
                Manifest initial = knownManifest;
                int missingItems = initial == null ? 0 : missing(initial);
                if (!manual && missingItems > 0) { showJoinOffer(missingItems); offered = true; }
                catalogErrorDetails = "";
                int changed = 0, errors = 0, newEmotes = 0, removedEmotes = 0;
                for (CatalogStore.Catalog c : snapshot) {
                    if (!c.enabled() || c.kind().equals("import") || (!selection.equals("all") && !selection.equals(c.key()))) continue;
                    catalogStatus = "Синхронизация: " + c.name();
                    try {
                        CatalogTransport.Result result = CATALOG_NETWORK.fetch(c.kind(), c.reference(), c.setId(), config.imageSource);
                        CatalogStore.Catalog updated = CATALOGS.update(c.key(), result.parsed(), result.source());
                        newEmotes += updated.added(); removedEmotes += updated.removed();
                        changed++;
                    } catch (Exception failure) { errors++; catalogOperationError(failure);
                        CATALOGS.failure(c.key(), failure instanceof CatalogTransport.LookupException ? failure.getMessage() : DiagnosticsReport.errorKind(failure)); }
                    publishCatalogs();
                }
                catalogStatus = errors == 0 ? "Каталоги обновлены: " + changed : "Используются сохранённые данные; недоступно: " + errors;
                manifestError = errors == 0 ? "" : "Каталог недоступен: " + errors;
                if (manual && errors > 0) MoonotesNotifications.notifyUser("Каталоги", "Некоторые каталоги недоступны. Локальные копии сохранены.", true);
                if (!manual && newEmotes > 0) MoonotesNotifications.notifyUser("Каталоги", "Новых эмотов: " + newEmotes
                    + (removedEmotes > 0 ? "; удалено из набора: " + removedEmotes : "")
                    + ". Скачивание вручную через /moonotes.", false);
                if (!manual && !offered && knownManifest != null) showJoinOffer(missing(knownManifest));
            } finally { MANIFEST_LOADING.set(false); }
        });
    }

    static void refreshManifest() {
        // Do not wipe a working saved catalog before a network refresh.
        requestManifest(true);
    }

    static void downloadFromGui() {
        if (CacheMaintenance.busy()) { chat("Дождитесь завершения проверки кеша"); return; }
        Manifest manifest = knownManifest;
        if (manifest == null) {
            requestManifest(true);
            return;
        }
        downloadAll(manifest);
    }

    static boolean isDownloading() { return BUSY.get(); }
    static boolean isManifestLoading() { return MANIFEST_LOADING.get(); }
    static int progress() { return progress; }
    static int total() { return total; }
    static int failed() { return failed; }
    static int downloaded() { return downloaded; }
    static int alreadyCached() { return alreadyCached; }
    static int collectionCount() { return knownManifest == null ? -1 : knownManifest.emotes.size(); }
    static int collectionImageCount() { Manifest manifest = knownManifest; return manifest == null ? -1 : manifest.imageCount; }
    static int missingCount() { return knownManifest == null ? -1 : Math.max(0, collectionImageCount() - cachedImageCount()); }
    static int cachedImageCount() {
        requestCacheScan(); CacheSnapshot snapshot = currentCacheSnapshot(); return snapshot == null ? 0 : snapshot.imageCount;
    }
    static int cachedCount() {
        requestCacheScan();
        CacheSnapshot snapshot = currentCacheSnapshot();
        return snapshot == null ? 0 : snapshot.count;
    }
    static boolean isEnabled() { return config.enabled; }
    static String manifestSource() { return manifestSource; }
    static String manifestError() { return manifestError; }

    /** Restores metadata before any network access; an intentionally empty store stays empty. */
    private static synchronized void loadInitialManifest() {
        if (catalogsInitialized) return;
        catalogsInitialized = true;
        String bundled = null;
        CatalogStore.Parsed bundledFacts = null;
        try (InputStream in = MoonotesNetworkClient.class.getResourceAsStream(BUNDLED_CATALOG)) {
            if (in != null) {
                byte[] bytes = in.readNBytes(CatalogStore.MAX_JSON + 1);
                if (bytes.length > CatalogStore.MAX_JSON) throw new IOException("Каталог слишком велик");
                bundled = new String(bytes, StandardCharsets.UTF_8);
                bundledFacts = CatalogStore.parse(bundled, "v0kky");
            }
        } catch (Exception error) { bundled = null; }
        boolean loaded = false;
        try { if (Files.isRegularFile(CATALOGS_FILE)) { CATALOGS.load(); loaded = true; } }
        catch (IOException error) { manifestError = "Сохранённый список повреждён"; }
        if (!loaded) {
            try {
                String seed = null;
                if (Files.isRegularFile(MANIFEST_FILE) && Files.size(MANIFEST_FILE) <= CatalogStore.MAX_JSON) {
                    try (InputStream saved = Files.newInputStream(MANIFEST_FILE)) {
                        byte[] bytes = saved.readNBytes(CatalogStore.MAX_JSON + 1);
                        if (bytes.length > CatalogStore.MAX_JSON) throw new IOException("Каталог слишком велик");
                        seed = new String(bytes, StandardCharsets.UTF_8); CatalogStore.parse(seed, "v0kky");
                    } catch (Exception ignored) { seed = null; }
                }
                if (seed == null) seed = bundled;
                if (seed == null) throw new IOException("Встроенный каталог отсутствует");
                CATALOGS.bootstrap(seed);
                CATALOGS.save();
            } catch (Exception error) { manifestError = "Не удалось прочитать каталог"; }
        }
        if (bundledFacts != null) {
            var animation = new LinkedHashMap<String, Boolean>();
            for (CatalogStore.Entry entry : bundledFacts.emotes()) if (entry.animated() != null)
                animation.merge(entry.id(), entry.animated(), (left, right) -> left || right);
            try { CATALOGS.enrichMissingAnimation(animation); }
            catch (IOException error) { catalogStatus = "Не удалось сохранить метаданные анимации"; }
        }
        publishCatalogs();
    }

    private static String safeError(Exception e) { return e == null ? "неизвестная ошибка" : DiagnosticsReport.errorKind(e); }
    private static void publishCatalogs() {
        // Publish the UI view on Minecraft's client thread, never half-way through a render.
        Minecraft.getInstance().execute(MoonotesNetworkClient::publishCatalogState);
    }
    private static void publishCatalogState() {
        Manifest m = new Manifest(); m.channel = selectedCatalogLabel();
        m.emotes = CATALOGS.visible().stream().map(MoonotesNetworkClient::item).toList(); m.count = m.emotes.size();
        m.imageCount = (int) m.emotes.stream().map(e -> e.id).distinct().count();
        knownManifest = m;
        var labels = new LinkedHashMap<String, String>();
        for (CatalogStore.Catalog source : CATALOGS.catalogs()) {
            if (!source.enabled() || (!CATALOGS.selected().equals("all") && !source.key().equals(CATALOGS.selected()))) continue;
            for (CatalogStore.Entry entry : source.emotes()) labels.putIfAbsent(entry.key(), source.name());
        }
        visibleCatalogNames = Map.copyOf(labels);
        CatalogStore.Catalog c = CATALOGS.get(CATALOGS.selected());
        manifestSource = c == null ? "Локальные каталоги" : c.source();
        long catalogueErrors = CATALOGS.catalogs().stream().filter(source -> source.enabled() && !source.error().isEmpty()).count();
        manifestError = catalogueErrors == 0 ? "" : "Каталоги недоступны: " + catalogueErrors;
        cacheCheckValid = false;
        var animatedIds = new LinkedHashMap<String, Boolean>();
        for (CatalogStore.Catalog source : CATALOGS.catalogs()) for (CatalogStore.Entry entry : source.emotes())
            animatedIds.merge(entry.id(), Boolean.TRUE.equals(entry.animated()), (left, right) -> left || right);
        animatedIds.forEach(CacheFiles::expectAnimated);
        StreamotesBridge.updateEntries(registrationItems());
        lastChatChannels=StreamotesBridge.activeChannels();
        ChatPalette.publish(ChatPalette.fromCatalogs(CATALOGS.catalogs(), lastChatChannels));
        publishedCatalogRevision = CATALOGS.revision();
        requestCacheScan();
    }
    private static EmoteItem item(CatalogStore.Entry entry) {
        EmoteItem value = new EmoteItem(); value.id = entry.id(); value.name = entry.name(); return value;
    }
    static List<StreamotesBridge.Entry> registrationItems() {
        var entries = new ArrayList<StreamotesBridge.Entry>();
        for (CatalogStore.Catalog c : CATALOGS.catalogs()) if (c.enabled())
            for (CatalogStore.Entry e : c.emotes()) entries.add(new StreamotesBridge.Entry(c.key(),
                c.kind().equals("channel") ? c.reference() : "", e.id(), e.name(), e.zeroWidth(), true, e.animated()));
        return List.copyOf(entries);
    }
    static long catalogRevision() { return publishedCatalogRevision; }
    static String selectedCatalog() { return CATALOGS.selected(); }
    static String selectedCatalogLabel() {
        CatalogStore.Catalog c = CATALOGS.get(CATALOGS.selected()); return c == null ? "Все каталоги" : c.name();
    }
    static String catalogStatus() { return catalogStatus; }
    static String catalogErrorDetails() { return catalogErrorDetails; }
    static CatalogPreview catalogPreview() {
        PendingCatalogPreview pending = pendingCatalogPreview; return pending == null || pending.view().token() != CATALOG_PREVIEW_TOKEN.get() ? null : pending.view();
    }
    static void cancelCatalogPreview() { CATALOG_PREVIEW_TOKEN.incrementAndGet(); pendingCatalogPreview = null; }
    static void beginCatalogPreview(String kind, String value) {
        final String reference;
        try {
            reference = "channel".equals(kind) ? CatalogStore.channel(value) : "set".equals(kind) ? CatalogStore.id(value) : "";
            if (reference.isEmpty()) throw new IOException("Неизвестный источник каталога");
            if (CATALOGS.get(kind + ":" + reference) != null) throw new IOException("Этот каталог уже добавлен");
            if (CATALOGS.catalogs().size() >= CatalogStore.MAX_CATALOGS) throw new IOException("Допустимо не больше " + CatalogStore.MAX_CATALOGS + " каталогов");
        } catch (IOException error) { catalogStatus = catalogOperationError(error); return; }
        if (!MANIFEST_LOADING.compareAndSet(false, true)) { catalogStatus = "Дождитесь текущей операции"; return; }
        long token = CATALOG_PREVIEW_TOKEN.incrementAndGet(); pendingCatalogPreview = null;
        catalogErrorDetails = ""; catalogStatus = "Проверка каталога…";
        IO.execute(() -> {
            try {
                CatalogTransport.Result result = CATALOG_NETWORK.fetch(kind, reference, "", config.imageSource);
                CatalogStore.Parsed parsed = result.parsed();
                CatalogPreview view = new CatalogPreview(token, kind, reference, parsed.name(), parsed.setId(), result.source(),
                    parsed.emotes().size(), (int) parsed.emotes().stream().map(CatalogStore.Entry::id).distinct().count(),
                    parsed.emotes().stream().limit(6).map(CatalogStore.Entry::name).toList());
                if (CATALOG_PREVIEW_TOKEN.get() == token) {
                    pendingCatalogPreview = new PendingCatalogPreview(view, result);
                    catalogStatus = "Каталог найден: " + parsed.name() + ". Подтвердите добавление.";
                }
            } catch (Exception error) { if (CATALOG_PREVIEW_TOKEN.get() == token) catalogStatus = catalogOperationError(error); }
            finally { MANIFEST_LOADING.set(false); }
        });
    }
    static boolean confirmCatalogPreview(long token) {
        PendingCatalogPreview pending = pendingCatalogPreview;
        if (pending == null || pending.view().token() != token || CATALOG_PREVIEW_TOKEN.get() != token) return false;
        if (!MANIFEST_LOADING.compareAndSet(false, true)) return false;
        pendingCatalogPreview = null;
        catalogStatus = "Добавление каталога…";
        IO.execute(() -> {
            try {
                CatalogPreview view = pending.view();
                CatalogStore.Catalog added = CATALOGS.add(view.kind(), view.reference(), pending.result().parsed(), view.source());
                if (view.kind().equals("channel")) StreamotesBridge.enableChannelForCurrentWorld(view.reference());
                publishCatalogs(); catalogErrorDetails = "";
                catalogStatus = "Добавлен: " + added.name() + " · имён: " + view.names() + " · файлов: " + view.files();
            } catch (Exception error) { catalogStatus = catalogOperationError(error); }
            finally { MANIFEST_LOADING.set(false); }
        });
        return true;
    }
    static boolean catalogBusy() { return MANIFEST_LOADING.get(); }
    static List<CatalogView> catalogs() {
        Map<String, CacheFact> files = catalogCache;
        long revision = CATALOGS.revision();
        CatalogViews previous = catalogViews;
        if (previous != null && previous.revision() == revision && previous.files() == files) return previous.views();
        List<CatalogView> views = CATALOGS.catalogs().stream().map(c -> new CatalogView(c.key(), c.name(), c.kind(), c.enabled(),
            (int)c.emotes().stream().map(CatalogStore.Entry::id).distinct().count(),
            (int)c.emotes().stream().map(CatalogStore.Entry::id).distinct().filter(id -> files.getOrDefault(id, NO_CACHE).valid()).count(),
            c.updatedAt(), c.added(), c.removed(), c.error().isEmpty() ? c.source() : "Нет связи · " + c.error())).toList();
        catalogViews = new CatalogViews(revision, files, views);
        return views;
    }
    static void selectCatalog(String key) {
        try { CATALOGS.select(key); publishCatalogs(); IO.execute(() -> { try {
            CATALOGS.save();
            if (config.serverProfiles) PROFILES.remember(activeWorldKey, key);
        } catch (IOException e) { catalogStatus = "Не удалось сохранить выбор каталога"; } }); }
        catch (IOException error) { catalogStatus = error.getMessage(); }
    }
    static void addChannel(String value) { addRemoteCatalog("channel", value); }
    static void addSet(String value) { addRemoteCatalog("set", value); }
    private static void addRemoteCatalog(String kind, String value) {
        final String reference;
        try { reference = kind.equals("channel") ? CatalogStore.channel(value) : CatalogStore.id(value); }
        catch (IOException error) { catalogStatus = error.getMessage(); return; }
        if (!MANIFEST_LOADING.compareAndSet(false, true)) { catalogStatus = "Дождитесь текущей операции"; return; }
        catalogErrorDetails = ""; catalogStatus = "Получение каталога…";
        IO.execute(() -> {
            try {
                CatalogTransport.Result fetched = CATALOG_NETWORK.fetch(kind, reference, "", config.imageSource);
                CatalogStore.Catalog added = CATALOGS.add(kind, reference, fetched.parsed(), fetched.source());
                if (kind.equals("channel")) StreamotesBridge.enableChannelForCurrentWorld(reference);
                publishCatalogs(); catalogStatus = "Добавлен: " + added.name() + " · " + added.emotes().size();
            } catch (Exception error) { catalogStatus = catalogOperationError(error); }
            finally { MANIFEST_LOADING.set(false); }
        });
    }
    static void importCatalogJson(String json) {
        if (!MANIFEST_LOADING.compareAndSet(false, true)) { catalogStatus = "Дождитесь текущей операции"; return; }
        catalogErrorDetails = ""; catalogStatus = "Импорт JSON…";
        IO.execute(() -> {
            try {
                CatalogStore.Parsed parsed = CatalogStore.parse(json, "Импорт JSON");
                CatalogStore.Catalog added = CATALOGS.add("import", "", parsed, "Импорт JSON");
                publishCatalogs(); catalogStatus = "Импортирован: " + added.name() + " · " + added.emotes().size();
            } catch (Exception error) { catalogStatus = catalogOperationError(error); }
            finally { MANIFEST_LOADING.set(false); }
        });
    }
    static void updateImportedCatalog(String key, String json) {
        CatalogStore.Catalog current = CATALOGS.get(key);
        if (current == null || !current.kind().equals("import")) { catalogStatus = "Выберите импортированный каталог"; return; }
        if (!MANIFEST_LOADING.compareAndSet(false, true)) { catalogStatus = "Дождитесь текущей операции"; return; }
        IO.execute(() -> {
            try {
                CatalogStore.Catalog next = CATALOGS.update(key, CatalogStore.parse(json, current.name()), "Импорт JSON");
                publishCatalogs(); catalogStatus = "Импорт обновлён: +" + next.added() + " / −" + next.removed();
            } catch (Exception error) { catalogStatus = catalogOperationError(error); }
            finally { MANIFEST_LOADING.set(false); }
        });
    }
    static void toggleCatalog(String key) { mutateCatalog(key, false); }
    static void removeCatalog(String key) { mutateCatalog(key, true); }
    private static void mutateCatalog(String key, boolean remove) {
        if (!MANIFEST_LOADING.compareAndSet(false, true)) { catalogStatus = "Дождитесь текущей операции"; return; }
        IO.execute(() -> {
            try {
                if (remove) CATALOGS.remove(key); else CATALOGS.toggle(key);
                publishCatalogs(); catalogStatus = remove ? "Каталог удалён. Общий кеш сохранён." : "Настройка каталога сохранена";
            } catch (IOException error) { catalogStatus = catalogOperationError(error); }
            finally { MANIFEST_LOADING.set(false); }
        });
    }
    static void syncCatalog(String key) {
        CatalogStore.Catalog c = CATALOGS.get(key);
        if (c == null) { catalogStatus = "Каталог не найден"; return; }
        if (c.kind().equals("import")) { catalogStatus = "Для обновления импортируйте новый JSON"; return; }
        if (!MANIFEST_LOADING.compareAndSet(false, true)) { catalogStatus = "Дождитесь текущей операции"; return; }
        IO.execute(() -> {
            try {
                catalogErrorDetails = ""; catalogStatus = "Синхронизация: " + c.name();
                CatalogTransport.Result fetched = CATALOG_NETWORK.fetch(c.kind(), c.reference(), c.setId(), config.imageSource);
                CatalogStore.Catalog next = CATALOGS.update(key, fetched.parsed(), fetched.source());
                catalogStatus = "Добавлено: " + next.added() + " · удалено из списка: " + next.removed();
            } catch (Exception error) { catalogStatus = catalogOperationError(error); CATALOGS.failure(key, error instanceof CatalogTransport.LookupException ? error.getMessage() : DiagnosticsReport.errorKind(error)); }
            finally { publishCatalogs(); MANIFEST_LOADING.set(false); }
        });
    }
    private static String catalogOperationError(Exception error) {
        if (error instanceof InterruptedException) { Thread.currentThread().interrupt(); catalogErrorDetails = "Операция отменена"; return catalogErrorDetails; }
        if (error instanceof CatalogTransport.LookupException lookup) {
            catalogErrorDetails = lookup.details(); return "Каталог недоступен: " + lookup.getMessage();
        }
        catalogErrorDetails = "Каталог: " + DiagnosticsReport.errorKind(error);
        if (error instanceof IOException && error.getMessage() != null && error.getMessage().length() < 180
                && error.getMessage().matches("(?s)^(Введите|Некоррект|Этот|Допустимо|JSON|В JSON|В каталоге|Каталог|Нет |Неизвест|Повтор|Пустая|Поле |Для ).*"))
        {
            catalogErrorDetails = error.getMessage().replaceAll("[\\p{Cc}\\p{Cf}]", " ");
            return catalogErrorDetails;
        }
        return "Не удалось получить каталог: " + DiagnosticsReport.errorKind(error);
    }
    static String itemKey(int index) { return itemId(index) + ":" + itemName(index); }
    static String itemCatalog(int index) {
        return visibleCatalogNames.getOrDefault(itemKey(index), "");
    }
    static List<String> selectedCacheIds() {
        Manifest m = knownManifest; return m == null ? List.of() : m.emotes.stream().map(e -> e.id).distinct().toList();
    }
    static void repairFromGui(List<String> snapshot) {
        var names = new LinkedHashMap<String, CatalogStore.Entry>();
        for (CatalogStore.Entry e : CATALOGS.allEnabled()) names.putIfAbsent(e.id(), e);
        Manifest repair = new Manifest(); repair.channel = "Проверенные файлы"; repair.emotes = new ArrayList<>();
        for (String raw : new java.util.LinkedHashSet<>(snapshot)) {
            try { String id = CatalogStore.id(raw); repair.emotes.add(item(names.getOrDefault(id, new CatalogStore.Entry(id, id)))); }
            catch (IOException ignored) { }
        }
        repair.count = repair.emotes.size(); downloadAll(repair);
    }
    static void cacheChanged() { cacheCheckValid = false; requestCacheScan(); StreamotesBridge.updateEntries(registrationItems()); StreamotesBridge.refresh(); }

    private static int missing(Manifest manifest) {
        int count = 0;
        var checked = new HashSet<String>();
        for (EmoteItem entry : manifest.emotes) {
            if (checked.add(entry.id) && !CacheFiles.looksValid(CacheFiles.forId(entry.id))) count++;
        }
        return count;
    }

    static long receivedBytes() { return RECEIVED_BYTES.get(); }
    static long networkMillis() { return NETWORK_MILLIS.get(); }
    static long conversionMillis() { return conversionMillis; }
    static long lastTransferMillis() { return lastTransferMillis; }
    static long elapsedMillis() {
        if (startedMillis == 0) return 0;
        long end = endedMillis == 0 ? System.currentTimeMillis() : endedMillis;
        long currentPause = pausedAt == 0 ? 0 : end - pausedAt;
        return Math.max(0, end - startedMillis - pauseMillis - currentPause);
    }
    static double speedBytesPerSecond() { return isDownloading() && !paused ? RATE.bytesPerSecond() : 0; }
    static int activeTransfers() { return ACTIVE_TRANSFERS.get(); }
    static int queuedDownloads() { return QUEUED_DOWNLOADS.get(); }
    static int transferredCount() { return TRANSFERRED_COUNT.get(); }
    static String stageLabel() { return paused ? "Пауза" : stage; }
    static String lastDownloadSource() { return lastDownloadSource; }
    static long fallbackRemainingSeconds() { return IMAGES.fallbackRemainingSeconds(); }
    static String currentEmote() { return currentEmote; }
    static boolean isPaused() { return paused; }
    static void togglePause() {
        synchronized (PAUSE_LOCK) {
            if (!BUSY.get()) return;
            paused = !paused;
            long now = System.currentTimeMillis();
            if (paused) pausedAt = now;
            else { if (pausedAt != 0) pauseMillis += now - pausedAt; pausedAt = 0; RATE.reset(); }
            PAUSE_LOCK.notifyAll();
        }
    }
    static void cancelDownload() {
        synchronized (PAUSE_LOCK) {
            cancelled = true;
            if (pausedAt != 0) pauseMillis += System.currentTimeMillis() - pausedAt;
            pausedAt = 0; paused = false;
            PAUSE_LOCK.notifyAll();
        }
        Thread thread = bulkThread;
        if (thread != null) thread.interrupt();
    }
    static String parallelDownloadsLabel() { return config.parallelDownloads == 0 ? "Авто · 4" : Integer.toString(config.parallelDownloads); }
    static int parallelDownloads() { return switch (config.parallelDownloads) { case 1, 2, 4, 6 -> config.parallelDownloads; default -> 4; }; }
    static void cycleParallelDownloads() {
        config.parallelDownloads = switch (config.parallelDownloads) { case 0 -> 1; case 1 -> 2; case 2 -> 4; case 4 -> 6; default -> 0; };
        saveConfig();
    }
    static boolean offerAtJoin() { return config.offerAtJoin; }
    static boolean showCompletionInChat() { return config.showCompletionInChat; }
    static void toggleJoinOffer() { config.offerAtJoin = !config.offerAtJoin; config.joinOfferMigrated = true; saveConfig(); }
    static void toggleCompletionNotice() { config.showCompletionInChat = !config.showCompletionInChat; saveConfig(); }
    static String imageSource() { return switch (config.imageSource == null ? "auto" : config.imageSource) {
        case "direct" -> "7TV"; case "worker" -> "Cloudflare"; default -> "Авто";
    }; }
    static void cycleImageSource() {
        config.imageSource = switch (config.imageSource == null ? "auto" : config.imageSource) {
            case "auto" -> "direct"; case "direct" -> "worker"; default -> "auto";
        };
        IMAGES.resetCircuit();
        saveConfig();
    }
    static boolean localDiagnostics() { return config.localDiagnostics; }
    static void toggleLocalDiagnostics() {
        config.localDiagnostics = !config.localDiagnostics;
        if (!config.localDiagnostics) synchronized (MoonotesNetworkClient.class) {
            directSuccess = workerSuccess = sourceFallbacks = 0;
            lastDirectLatencyMs = lastWorkerLatencyMs = -1;
            lastDownloadFailure = "";
        }
        saveConfig();
    }
    static boolean autoCatalogSync() { return config.autoCatalogSync; }
    static void toggleAutoCatalogSync() { config.autoCatalogSync = !config.autoCatalogSync; saveConfig(); }
    static boolean serverProfilesEnabled() { return config.serverProfiles; }
    static void toggleServerProfiles() {
        config.serverProfiles = !config.serverProfiles;
        if (config.serverProfiles) IO.execute(() -> { try { PROFILES.remember(activeWorldKey, CATALOGS.selected()); }
            catch (IOException ignored) { /* The UI retains its in-memory selection. */ } });
        saveConfig();
    }
    static boolean chatSuggestionsEnabled() { return config.chatSuggestions && config.enabled; }
    static void toggleChatSuggestions() { config.chatSuggestions = !config.chatSuggestions; saveConfig(); }
    static boolean chatPickerEnabled() { return config.chatPicker && config.enabled; }
    static void toggleChatPicker() { config.chatPicker = !config.chatPicker; saveConfig(); }
    static String notificationMode(){return NotificationQueue.mode(config.noticeMode);}
    static String notificationModeLabel(){return switch(notificationMode()){
        case "chat"->"В чате";case "important"->"Только ошибки";case "off"->"Выключены";default->"На экране";};}
    static void cycleNotificationMode(){config.noticeMode=switch(notificationMode()){
        case "toast"->"important";case "important"->"chat";case "chat"->"off";default->"toast";};
        MoonotesNotifications.clearTransient();saveConfig();}
    public static boolean unifiedStreamotesNotifications(){return config.enabled && config.unifiedStreamotesNotifications;}
    static void toggleUnifiedStreamotesNotifications(){config.unifiedStreamotesNotifications=!config.unifiedStreamotesNotifications;saveConfig();}
    static boolean hudProgressEnabled(){return config.hudProgress;}
    static void toggleHudProgress(){config.hudProgress=!config.hudProgress;saveConfig();}
    static int hudCorner(){return config.hudCorner;}
    static String hudCornerLabel(){if(config.hudCustomPosition)return "Своя позиция";return switch(config.hudCorner){
        case 0->"Сверху слева";case 2->"Снизу слева";case 3->"Снизу справа";default->"Сверху справа";};}
    static void cycleHudCorner(){config.hudCorner=(config.hudCorner+1)%4;config.hudCustomPosition=false;saveConfig();}
    static HudLayout.Placement hudPlacement(){return new HudLayout.Placement(config.hudCustomPosition,
        config.hudCorner,config.hudPositionX,config.hudPositionY);}
    static void saveHudPlacement(HudLayout.Placement placement){
        config.hudCustomPosition=placement.custom();config.hudCorner=placement.corner();
        config.hudPositionX=placement.x();config.hudPositionY=placement.y();saveConfig();
    }
    /** Optional bridge for the separate main Moonotes addon. No compile dependency or bundled mod. */
    public static void notifyAddon(String title,String message,boolean error){
        MoonotesNotifications.notifyUser(title,message,error);
    }
    static boolean releaseNotesPending() { return !"0.5.3".equals(config.releaseShownVersion); }
    static void markReleaseNotesSeen() { config.releaseShownVersion = "0.5.3"; saveConfig(); }

    static boolean retryDownloads() { return config.retryDownloads; }
    static void toggleRetryDownloads() { config.retryDownloads = !config.retryDownloads; saveConfig(); }
    static String diagnosticsReport(int clicks, int drags, String lastMouseAction, int selectedCard) {
        // Never include username, server address, paths, emote names, IP or device ID.
        return DiagnosticsReport.build(
            FabricLoader.getInstance().getModContainer("moonotes_network")
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown"),
            "26.3", System.getProperty("java.version", "unknown"),
            System.getProperty("os.name", "unknown"), imageSource(),
            manifestSource, collectionCount(), cachedCount(), progress, failed,
            directSuccess, workerSuccess, sourceFallbacks,
            lastDirectLatencyMs, lastWorkerLatencyMs,
            lastDownloadSource, manifestError.isEmpty() ? "none" : DiagnosticsReport.errorKind(manifestError),
            lastDownloadFailure.isEmpty() ? "none" : lastDownloadFailure,
            clicks, drags, selectedCard >= 0, localDiagnostics());
    }
    static String activeChannel() { return selectedCatalogLabel(); }
    static String itemName(int idx) { Manifest m = knownManifest; return m != null && idx >= 0 && idx < m.emotes.size() ? m.emotes.get(idx).name : ""; }
    static String itemId(int idx) { Manifest m = knownManifest; return m != null && idx >= 0 && idx < m.emotes.size() ? m.emotes.get(idx).id : ""; }
    static long itemBytes(int idx) { requestCacheScan(); CacheSnapshot s = currentCacheSnapshot(); return s != null && idx >= 0 && idx < s.bytes.length ? s.bytes[idx] : 0; }
    static boolean itemCached(int idx) { requestCacheScan(); CacheSnapshot s = currentCacheSnapshot(); return s != null && idx >= 0 && idx < s.valid.length && s.valid[idx]; }
    /** Uses the completed background cache scan, never probes 704 files during a GUI frame. */
    static String firstMissingId() {
        requestCacheScan();
        CacheSnapshot snapshot=currentCacheSnapshot();
        if (snapshot == null) return "";
        for (int i=0;i<snapshot.valid.length;i++)
            if (!snapshot.valid[i]) return snapshot.manifest.emotes.get(i).id;
        return "";
    }
    static String firstMissingSummary() {
        requestCacheScan();
        CacheSnapshot snapshot=currentCacheSnapshot();
        if(snapshot==null)return "индексируется кеш…";
        for(int i=0;i<snapshot.valid.length;i++) {
            if(snapshot.valid[i])continue;
            EmoteItem item=snapshot.manifest.emotes.get(i);
            String summary=item.name+" ("+item.id+")";
            if(item.id.equalsIgnoreCase(lastFailedEmoteId) && !lastFailedEmoteKind.isBlank())
                summary+=" · последняя ошибка: "+lastFailedEmoteKind;
            return summary;
        }
        return "нет";
    }
    static String failedSourcesFor(String id) {
        if (id == null || !id.equalsIgnoreCase(lastFailedEmoteId)) return "";
        return "7TV: " + lastFailedDirect + " · Cloudflare: " + lastFailedWorker;
    }
    static String nameForId(String id) {
        Manifest manifest=knownManifest;
        if (manifest != null) for (EmoteItem item : manifest.emotes)
            if (item.id.equalsIgnoreCase(id)) return item.name;
        return "эмот";
    }
    private static void recordFailure(String id, Exception error) {
        lastFailedEmoteId = id;
        lastFailedEmoteKind = DiagnosticsReport.errorKind(error);
        if (error instanceof ImageDownloader.SourcesUnavailable unavailable) {
            lastFailedDirect = unavailable.direct();
            lastFailedWorker = unavailable.worker();
        } else {
            lastFailedDirect = "загрузка завершена";
            lastFailedWorker = "ошибка подготовки кеша";
        }
        if (config.localDiagnostics) lastDownloadFailure = lastFailedEmoteKind;
    }
    static long itemModified(int idx) { requestCacheScan(); CacheSnapshot s = currentCacheSnapshot(); return s != null && idx >= 0 && idx < s.modified.length ? s.modified[idx] : 0; }
    static long cacheSize() { requestCacheScan(); CacheSnapshot s = currentCacheSnapshot(); return s == null ? 0 : s.allBytes; }
    static long channelCacheSize() { requestCacheScan(); CacheSnapshot s = currentCacheSnapshot(); return s == null ? 0 : s.channelBytes; }

    private static final class CacheSnapshot {
        final Manifest manifest;
        final boolean[] valid;
        final long[] bytes, modified;
        final int count, imageCount;
        final long channelBytes, allBytes;
        CacheSnapshot(Manifest manifest, boolean[] valid, long[] bytes, long[] modified, int count, int imageCount, long channelBytes, long allBytes) {
            this.manifest = manifest; this.valid = valid; this.bytes = bytes; this.modified = modified;
            this.count = count; this.imageCount = imageCount; this.channelBytes = channelBytes; this.allBytes = allBytes;
        }
    }
    private static CacheSnapshot currentCacheSnapshot() {
        CacheSnapshot snapshot = cacheSnapshot;
        return snapshot != null && snapshot.manifest == knownManifest ? snapshot : null;
    }
    private static void requestCacheScan() {
        Manifest manifest = knownManifest;
        if (manifest == null) return;
        long now = System.currentTimeMillis();
        if ((cacheCheckValid && now - cacheCheckedAt < 2500L) || now - cacheCheckedAt < 250L) return;
        if (!CACHE_SCANNING.compareAndSet(false, true)) return;
        CACHE_SCAN.execute(() -> {
            try {
                var allIds = new java.util.LinkedHashSet<String>();
                for (CatalogStore.Catalog catalog : CATALOGS.catalogs()) for (CatalogStore.Entry entry : catalog.emotes()) allIds.add(entry.id());
                var facts = new LinkedHashMap<String, CacheFact>();
                for (String id : allIds) {
                    Path file = CacheFiles.forId(id);
                    boolean validFile = CacheFiles.looksValid(file);
                    long fileBytes = 0, fileModified = 0;
                    try {
                        if (Files.isRegularFile(file)) { fileBytes = Files.size(file); fileModified = Files.getLastModifiedTime(file).toMillis(); }
                    } catch (IOException ignored) { }
                    facts.put(id, new CacheFact(validFile, fileBytes, fileModified));
                }
                catalogCache = Map.copyOf(facts);
                int size = manifest.emotes.size(), count = 0, imageCount = 0;
                boolean[] valid = new boolean[size];
                long[] bytes = new long[size], modified = new long[size];
                long channelBytes = 0;
                var counted = new HashSet<String>();
                for (int i = 0; i < size; i++) {
                    String id = manifest.emotes.get(i).id;
                    CacheFact fact = facts.getOrDefault(id, NO_CACHE);
                    valid[i] = fact.valid(); bytes[i] = fact.bytes(); modified[i] = fact.modified();
                    if (valid[i]) count++;
                    if (counted.add(id)) {
                        if (valid[i]) imageCount++;
                        channelBytes += fact.bytes();
                        Path sidecar = CacheFiles.animationMetadata(CacheFiles.forId(id));
                        try { if (Files.isRegularFile(sidecar)) channelBytes += Files.size(sidecar); } catch (IOException ignored) { }
                    }
                }
                long allBytes = 0;
                Path folder = FabricLoader.getInstance().getGameDir().resolve("emoticons/cache/images");
                if (Files.isDirectory(folder)) try (var files = Files.list(folder)) {
                    allBytes = files.filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().endsWith(".png") || f.getFileName().toString().endsWith(".png.txt"))
                        .mapToLong(f -> { try { return Files.size(f); } catch (IOException e) { return 0; } }).sum();
                }
                cacheSnapshot = new CacheSnapshot(manifest, valid, bytes, modified, count, imageCount, channelBytes, allBytes);
                cachedCount = count; cacheCheckedAt = System.currentTimeMillis(); cacheCheckValid = true;
            } catch (Exception error) {
                cacheCheckedAt = System.currentTimeMillis();
            } finally { CACHE_SCANNING.set(false); }
        });
    }

    private static void saveConfig() {
        try { Files.createDirectories(CONFIG_FILE.getParent()); Files.writeString(CONFIG_FILE, GSON.toJson(config), StandardCharsets.UTF_8); }
        catch (IOException e) { System.err.println("[Moonotes Network] Could not save config: " + e); }
    }

    private record FetchResult(EmoteItem entry, ImageDownloader.Image image, Exception failure, boolean cached) { }

    private static void downloadAll(Manifest requested) {
        Manifest manifest = new Manifest(); manifest.channel = requested.channel;
        var unique = new LinkedHashMap<String, EmoteItem>();
        for (EmoteItem item : requested.emotes) unique.putIfAbsent(item.id, item);
        manifest.emotes = List.copyOf(unique.values()); manifest.count = manifest.emotes.size();
        if (!BUSY.compareAndSet(false, true)) { chat("Загрузка уже выполняется"); return; }
        total = manifest.emotes.size();
        progress = failed = downloaded = alreadyCached = 0;
        lastFailedEmoteId = lastFailedEmoteKind = "";
        lastFailedDirect = lastFailedWorker = "не проверялся";
        RECEIVED_BYTES.set(0); NETWORK_MILLIS.set(0); TRANSFERRED_COUNT.set(0); QUEUED_DOWNLOADS.set(0);
        conversionMillis = lastTransferMillis = endedMillis = pausedAt = pauseMillis = 0;
        startedMillis = System.currentTimeMillis(); currentEmote = "";
        paused = cancelled = false; RATE.reset(); stage = "Проверка кеша";
        final int parallel = parallelDownloads();
        final String preference = config.imageSource;
        final boolean retry = config.retryDownloads;
        MoonotesNotifications.downloadStarted();
        BULK.execute(() -> {
            bulkThread = Thread.currentThread();
            try (var pipeline = new DownloadPipeline<FetchResult>(parallel)) {
                var missing = new ArrayList<EmoteItem>();
                for (EmoteItem entry : manifest.emotes) {
                    awaitDownloadReady();
                    if (CacheFiles.looksValid(CacheFiles.forId(entry.id))) { alreadyCached++; progress++; }
                    else missing.add(entry);
                }
                int next = 0;
                while (next < missing.size() && pipeline.pendingCount() < parallel) {
                    submitFetch(pipeline, missing.get(next++), preference, retry);
                }
                while (pipeline.pendingCount() > 0) {
                    awaitDownloadReady();
                    stage = "Получение изображений";
                    FetchResult result = pipeline.poll(150, TimeUnit.MILLISECONDS);
                    if (result == null) continue;
                    if (result.image != null) QUEUED_DOWNLOADS.decrementAndGet();
                    awaitDownloadReady();
                    // At most 'parallel' fetches plus this one image waiting for the decoder.
                    if (next < missing.size()) {
                        submitFetch(pipeline, missing.get(next++), preference, retry);
                    }
                    currentEmote = result.entry.name;
                    if (result.cached) alreadyCached++;
                    else if (result.failure != null) {
                        failed++;
                        recordFailure(result.entry.id, result.failure);
                    } else {
                        stage = "Подготовка кеша";
                        long start = System.nanoTime();
                        try {
                            // The Streamotes decoder stays on one thread; network work runs ahead.
                            if (StreamotesCacheWriter.save(result.entry.id, result.image.data())) downloaded++;
                            else alreadyCached++;
                        } catch (Exception error) {
                            failed++;
                            recordFailure(result.entry.id, error);
                        } finally { conversionMillis += (System.nanoTime() - start) / 1_000_000L; }
                    }
                    progress++; cacheCheckValid = false; requestCacheScan();
                }
            } catch (InterruptedException stopped) {
                cancelled = true;
                // This dedicated worker is reused; clear the cancellation before returning it.
                Thread.interrupted();
            } catch (Exception failure) {
                failed++;
                if (config.localDiagnostics) lastDownloadFailure = DiagnosticsReport.errorKind(failure);
            } finally {
                endedMillis = System.currentTimeMillis();
                if (pausedAt != 0) pauseMillis += endedMillis - pausedAt;
                pausedAt = 0; paused = false; currentEmote = ""; QUEUED_DOWNLOADS.set(0);
                stage = cancelled ? "Остановлено" : failed > 0 ? "Завершено с ошибками" : "Готово";
                bulkThread = null; BUSY.set(false); cacheChanged();
                if (config.showCompletionInChat) MoonotesNotifications.downloadFinished(downloaded, alreadyCached, failed, cancelled);
            }
        });
    }

    private static void submitFetch(DownloadPipeline<FetchResult> pipeline, EmoteItem entry, String preference, boolean retry) {
        pipeline.submit(() -> {
            boolean active = false;
            try {
                awaitDownloadReady();
                if (CacheFiles.looksValid(CacheFiles.forId(entry.id))) return new FetchResult(entry, null, null, true);
                ACTIVE_TRANSFERS.incrementAndGet(); active = true;
                URI direct = URI.create("https://cdn.7tv.app/emote/" + entry.id + "/2x.webp");
                ImageDownloader.Image image = IMAGES.fetch(direct, preference, retry, () -> cancelled,
                    count -> { RECEIVED_BYTES.addAndGet(count); RATE.add(count); });
                NETWORK_MILLIS.addAndGet(image.millis()); lastTransferMillis = image.millis();
                TRANSFERRED_COUNT.incrementAndGet(); QUEUED_DOWNLOADS.incrementAndGet();
                return new FetchResult(entry, image, null, false);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt(); return new FetchResult(entry, null, stopped, false);
            } catch (Exception failure) { return new FetchResult(entry, null, failure, false); }
            finally { if (active) ACTIVE_TRANSFERS.decrementAndGet(); }
        });
    }
    private static void awaitDownloadReady() throws InterruptedException {
        synchronized (PAUSE_LOCK) {
            while (paused && !cancelled) PAUSE_LOCK.wait(150);
            if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException("Download cancelled");
        }
    }
    private static synchronized void recordImageSuccess(String source, long millis) {
        lastDownloadSource = source;
        if (!config.localDiagnostics) return;
        if (source.equals("7TV")) { directSuccess++; lastDirectLatencyMs = millis; }
        else { workerSuccess++; lastWorkerLatencyMs = millis; }
        lastDownloadFailure = "";
    }
    /** Returns null only for URLs outside this addon's allowlist or when disabled. */
    public static InputStream openImageStream(URI original) throws IOException {
        if (proxyFor(original) == null) return null;
        try {
            ImageDownloader.Image image = IMAGES.fetch(original, config.imageSource, config.retryDownloads,
                () -> false, count -> { });
            return new ByteArrayInputStream(image.data());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new IOException("Image loading interrupted", interrupted);
        }
    }

    private static void showStatus() {
        chat("Каталог: " + manifestSource + " | " + selectedCatalogLabel() + " — " + (BUSY.get() ? "скачивание " + progress + "/" + total : "загрузчик свободен")
           + " | ошибка каталога: " + (manifestError.isEmpty() ? "нет" : manifestError) + " | новых: " + downloaded + " | уже в кеше: " + alreadyCached + " | ошибок: " + failed);
    }

    private static void showJoinOffer(int missing) {
        if (missing <= 0) return;
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (!inWorld || mc.player == null || !config.offerAtJoin) return;
            if (!"chat".equals(config.noticeMode)) {
                MoonotesNotifications.notifyUser("Moonotes", "Не хватает "+missing+" эмотов. Открой /moonotes для загрузки.",false);
                return;
            }
            Component link = Component.literal("[Открыть Moonotes]")
                .withStyle(style -> style.withColor(0x83D5FF).withUnderlined(true)
                    .withClickEvent(new ClickEvent.RunCommand("/moonotes"))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Открыть управление смайликами"))));
            mc.gui.hud.getChat().addClientSystemMessage(
                Component.literal("[Moonotes] Доступно " + missing + " смайликов 7TV для скачивания. ").append(link));
        });
    }

    private static void chat(String msg) {
        MoonotesNotifications.notifyUser("Moonotes",msg,
            msg.toLowerCase(java.util.Locale.ROOT).contains("ошиб") ||
            msg.toLowerCase(java.util.Locale.ROOT).contains("не удалось"));
    }

    private static void readConfig() {
        try {
            if (Files.isRegularFile(CONFIG_FILE)) {
                if (Files.size(CONFIG_FILE) > 128 * 1024) throw new IOException("Config too large");
                String json;
                try (InputStream input = Files.newInputStream(CONFIG_FILE)) {
                    byte[] bytes = input.readNBytes(128 * 1024 + 1);
                    if (bytes.length > 128 * 1024) throw new IOException("Config too large");
                    json = new String(bytes, StandardCharsets.UTF_8);
                }
                CatalogStore.checkJson(json, 128 * 1024);
                Config saved = GSON.fromJson(json, Config.class);
                if (saved != null) {
                    config = saved;
                    if (!"direct".equals(config.imageSource) && !"worker".equals(config.imageSource)) config.imageSource = "auto";
                    if (config.parallelDownloads != 1 && config.parallelDownloads != 2 && config.parallelDownloads != 4 && config.parallelDownloads != 6) config.parallelDownloads = 0;
                    // The Worker address is a constant, not a user-controlled
                    // config field. Legacy JSON workerBaseUrl is ignored.
                    // One-time migration: previous build saved the default as OFF.
                    // Respect future changes through the GUI by persisting this migration.
                    config.noticeMode = NotificationQueue.mode(config.noticeMode);
                    if (config.hudCorner < 0 || config.hudCorner > 3) config.hudCorner = 1;
                    config.hudPositionX = HudLayout.finiteFraction(config.hudPositionX, 1);
                    config.hudPositionY = HudLayout.finiteFraction(config.hudPositionY, 0.12);
                    if (!json.contains("\"hudProgress\"")) config.hudProgress = true;
                    if (!json.contains("\"noticeMode\"")) config.noticeMode = "toast";
                    if (!json.contains("\"unifiedStreamotesNotifications\"")) config.unifiedStreamotesNotifications = true;
                    if (!json.contains("\"autoCatalogSync\"")) config.autoCatalogSync = true;
                    if (!json.contains("\"serverProfiles\"")) config.serverProfiles = true;
                    if (!json.contains("\"chatSuggestions\"")) config.chatSuggestions = true;
                    if (!json.contains("\"chatPicker\"")) config.chatPicker = true;
                    if (!json.contains("\"joinOfferMigrated\"")) {
                        config.offerAtJoin = true;
                        saveConfig();
                    }
                }
            } else {
                Files.createDirectories(CONFIG_FILE.getParent());
                Files.writeString(CONFIG_FILE, GSON.toJson(config), StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) { System.err.println("[Moonotes Network] Config error: " + e); }
    }

    private static String trustedWorkerBase() { return DEFAULT_WORKER; }

    public static URI proxyFor(URI original) {
        return config.enabled ? ProxyUrlResolver.resolve(original, trustedWorkerBase()) : null;
    }
}
