package dev.mymoonx.moonotesnetwork;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runtime adapter to the separately installed Streamotes. Saved catalogues supply names while
 * Streamotes' own renderer, cache and provider priority remain in charge of displaying them.
 * No Streamotes classes are compiled into, or distributed with, this addon.
 */
public final class StreamotesBridge {
    public record Entry(String catalogKey, String channel, String id, String name,
                        boolean zeroWidth, boolean enabled, Boolean animated) {
        public Entry(String catalogKey, String channel, String id, String name, boolean zeroWidth, boolean enabled) {
            this(catalogKey, channel, id, name, zeroWidth, enabled, null);
        }
    }
    public record Health(boolean compatible, boolean active, int expected, int registered,
                         int conflicts, int rejected, int imageErrors, String problem) {
        public boolean good() {
            return compatible && active && registered == expected && conflicts == 0
                    && rejected == 0 && imageErrors == 0;
        }
    }

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean DIRTY = new AtomicBoolean(true);
    private static final AtomicBoolean RETRY_IMAGES = new AtomicBoolean();
    private static final AtomicInteger IMAGE_ERRORS = new AtomicInteger();
    private static final int FALLBACK_PRIORITY = 100; // Streamotes channel 7TV has priority 6 (lower wins).
    private static volatile List<Entry> entries = List.of();
    private static volatile Health health = new Health(false, false, 0, 0, 0, 0, 0, "Ожидание мира");
    private static volatile String imageProblem = "";
    private static volatile boolean inWorld;
    private static volatile Api api;
    private static volatile Set<String> lastChannels = Set.of();
    private static int ticks;
    // Only the Minecraft client thread mutates this map. Workers consult registry identity instead.
    private static final Map<String, Owned> owned = new HashMap<>();

    private static final class Owned {
        final Entry entry;
        final Object emote;
        volatile boolean retired;
        Owned(Entry entry, Object emote) { this.entry = entry; this.emote = emote; }
    }

    private StreamotesBridge() { }

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            inWorld = true;
            DIRTY.set(true);
            RETRY_IMAGES.set(true);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            inWorld = false;
            retireOwned();
            lastChannels = Set.of();
            health = new Health(api != null, false, 0, 0, 0, 0, IMAGE_ERRORS.get(), "Ожидание мира");
        });
        ClientTickEvents.END_CLIENT_TICK.register(StreamotesBridge::tick);
    }

    public static void updateEntries(List<Entry> updated) {
        if (updated == null) return;
        entries = updated.stream().filter(e -> e != null && e.id() != null && e.name() != null
                && e.catalogKey() != null && e.channel() != null).toList();
        DIRTY.set(true);
        RETRY_IMAGES.set(true);
    }

    public static Health health() { return health; }
    public static boolean ready() { return health.good(); }
    public static int registeredCount() { return health.registered(); }
    public static String error() { return health.problem(); }

    public static String diagnostics() {
        Health h = health;
        return "Регистрация для чата: " + h.registered() + "/" + h.expected()
                + "\nФормат ввода Streamotes: " + activationLabel()
                + "\nКонфликты имён: " + h.conflicts() + "\nНеподдерживаемые имена: " + h.rejected()
                + "\nОшибки изображений: " + h.imageErrors()
                + (h.problem().isEmpty() ? "" : "\nСостояние: " + h.problem());
    }

    private static volatile Method directEmoteLookup;
    private static volatile long nextEmoteLookupAttempt;

    /**
     * Ask Streamotes itself, not the optional Network registration adapter.
     * The latter may not be initialised even though Streamotes already renders
     * native emotes, and previously caused !WW to stay plain text.
     */
    public static boolean registeredEmoteName(String name) {
        if (name == null || name.isBlank() || name.length() > 80) return false;
        Method lookup = directEmoteLookup;
        if (lookup == null) {
            long now = System.currentTimeMillis();
            if (now < nextEmoteLookupAttempt) return false;
            try {
                lookup = Class.forName("xeed.mc.streamotes.emoticon.EmoticonRegistry")
                    .getMethod("fromName", String.class);
                directEmoteLookup = lookup;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                nextEmoteLookupAttempt = now + 5_000L;
                return false;
            }
        }
        try { return lookup.invoke(null, name) != null; }
        catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Only used for reserving visual space after BTTV w!. No images are
     * duplicated and Streamotes still owns its texture and animation.
     */
    public static float chatEmoteWidth(String name) {
        if (name == null || name.isBlank() || name.length() > 80) return 0;
        try {
            Method lookup = directEmoteLookup;
            if (lookup == null) {
                lookup = Class.forName("xeed.mc.streamotes.emoticon.EmoticonRegistry")
                    .getMethod("fromName", String.class);
                directEmoteLookup = lookup;
            }
            Object emote = lookup.invoke(null, name);
            if (emote == null) return 0;
            Object width = emote.getClass().getMethod("getChatRenderWidth").invoke(emote);
            if (width instanceof Number value) {
                float pixels = value.floatValue();
                return Float.isFinite(pixels) && pixels > 0 ? Math.min(96, pixels) : 0;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { }
        return 0;
    }

    /** Honors Streamotes' existing colon setting without changing its configuration. */
    public static String activeChatPrefix() { return WheelSettings.activePrefix(); }

    public static String chatText(String name) {
        if (name == null) return "";
        return "Required".equals(activationMode()) && !(name.startsWith(":") && name.endsWith(":"))
                ? ":" + name + ":" : name;
    }

    public static String activationLabel() {
        return switch (activationMode()) {
            case "Required" -> "только :имя:";
            case "Optional" -> "имя или :имя:";
            case "Disabled" -> "имя без добавления двоеточий";
            default -> "не удалось определить";
        };
    }

    private static String activationMode() {
        try {
            Class<?> streamotes = Class.forName("xeed.mc.streamotes.Streamotes");
            Object instance = streamotes.getField("INSTANCE").get(null);
            if (instance == null) return "";
            Object config = streamotes.getMethod("getConfig").invoke(instance);
            Object value = config.getClass().getField("activationMode").get(config);
            return value instanceof Enum<?> mode ? mode.name() : "";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return "";
        }
    }

    /** Does not reset the other mod's loading counter, interrupt workers or start a metadata load. */
    public static boolean applyLocalCatalogs(List<String> channels) {
        if (channels == null || channels.isEmpty()) return false;
        Set<String> available = new HashSet<>();
        for (Entry e : entries) if (e.enabled() && !e.channel().isEmpty())
            available.add(e.channel().toLowerCase(Locale.ROOT));
        return health.compatible() && health.active() && health.expected() > 0
                && health.registered() == health.expected()
                && channels.stream().allMatch(c -> c != null && available.contains(c.toLowerCase(Locale.ROOT)));
    }

    /** Narrow 7TV pack hook: known metadata is already supplied by this independent addon. */
    public static boolean useLocalChannel(String channel) {
        if (!isChannelCovered(channel)) return false;
        DIRTY.set(true);
        return true;
    }

    public static boolean isChannelCovered(String channel) {
        if (channel == null || api == null || !MoonotesNetworkClient.isEnabled()) return false;
        for (Entry e : entries) if (e.enabled() && e.channel().equalsIgnoreCase(channel)) {
            return true;
        }
        return false;
    }

    public static void registryCleared() {
        // Callback can run in a Streamotes worker. Retire/close on the client thread only.
        DIRTY.set(true);
    }

    /** Explicit retry after a download or repair; only resets uninitialised matching emotes. */
    public static void refresh() {
        IMAGE_ERRORS.set(0);
        imageProblem = "";
        RETRY_IMAGES.set(true);
        DIRTY.set(true);
    }

    public static boolean channelEnabledForCurrentWorld(String channel) {
        return currentChannels().contains(channel.toLowerCase(Locale.ROOT));
    }

    public static boolean enableChannelForCurrentWorld(String channel) {
        return setChannelEnabledForCurrentWorld(channel, true);
    }

    public static boolean setChannelEnabledForCurrentWorld(String channel, boolean enabled) {
        try {
            Class<?> main = Class.forName("dev.nicetry.streamotesinput.MoonotesClient");
            Object result = main.getMethod("setChannelEnabled", String.class, boolean.class)
                    .invoke(null, channel, enabled);
            DIRTY.set(true);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            imageProblem = "Для настройки каналов нужен Moonotes 0.4.0";
            return false;
        }
    }

    static Set<String> activeChannels() { return currentChannels(); }

    private static Set<String> currentChannels() {
        try {
            Class<?> main = Class.forName("dev.nicetry.streamotesinput.MoonotesClient");
            Method method;
            try { method = main.getMethod("getCurrentChannels"); }
            catch (NoSuchMethodException legacy) {
                method = main.getDeclaredMethod("current");
                method.setAccessible(true);
            }
            Object result = method.invoke(null);
            if (result instanceof List<?> list) {
                Set<String> channels = new LinkedHashSet<>();
                for (Object value : list) if (value instanceof String channel)
                    channels.add(channel.toLowerCase(Locale.ROOT));
                return Set.copyOf(channels);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { }
        // Network can still work alongside Streamotes without the optional main Moonotes addon.
        return Set.of("v0kky");
    }

    private static void tick(Minecraft client) {
        ticks++;
        // Resolve the real installed API while the title screen is open, before a world starts
        // metadata workers. Never cancel a native metadata request when this adapter is unavailable.
        if (api == null && ticks % 20 == 1) {
            try { api = new Api(); }
            catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { }
        }
        if (!inWorld || client.player == null) return;
        if (!DIRTY.get() && ticks % 20 != 0) return;
        Set<String> channels = currentChannels();
        if (!channels.equals(lastChannels)) { lastChannels = channels; DIRTY.set(true); }
        boolean retryImages = RETRY_IMAGES.getAndSet(false);
        DIRTY.set(false);
        if (!MoonotesNetworkClient.isEnabled()) {
            retireOwned();
            health = new Health(api != null, true, 0, 0, 0, 0, IMAGE_ERRORS.get(), "Moonotes Network отключён");
            return;
        }
        try {
            if (api == null) api = new Api();
            reconcile(client, channels, retryImages);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            health = new Health(false, true, 0, 0, 0, 0, IMAGE_ERRORS.get(), "Несовместимый API Streamotes");
            // A retry is rate limited by the once-per-second tick above.
        }
    }

    private static void reconcile(Minecraft client, Set<String> channels, boolean retryImages)
            throws ReflectiveOperationException {
        var source = new ArrayList<RegistrationPlan.Item>();
        for (Entry e : entries) source.add(new RegistrationPlan.Item(e.catalogKey(), e.channel(),
                e.id(), e.name(), e.zeroWidth(), e.enabled()));
        RegistrationPlan.Plan plan = RegistrationPlan.create(source, channels);
        boolean changed = false;
        for (var iterator = owned.entrySet().iterator(); iterator.hasNext();) {
            var existing = iterator.next();
            Owned value = existing.getValue();
            var wanted = plan.names().get(existing.getKey());
            Object registered = api.find(existing.getKey());
            if (wanted == null || !wanted.id().equalsIgnoreCase(value.entry.id())
                    || wanted.zeroWidth() != value.entry.zeroWidth() || registered != value.emote) {
                value.retired = true;
                if (registered == value.emote) { api.removeOwned(existing.getKey(), value.emote); changed = true; }
                // Close is idempotent. Some old owned objects were replaced by a native provider
                // before the registry clear and are therefore absent from its disposal list.
                api.close(value.emote);
                iterator.remove();
                changed = true;
            }
        }
        int matched = 0, conflicts = plan.duplicateNames(), rejected = 0;
        for (var wanted : plan.names().values()) {
            Object existing = api.find(wanted.name());
            if (existing == null) {
                Entry entry = new Entry(wanted.catalogKey(), wanted.channel(), wanted.id(),
                        wanted.name(), wanted.zeroWidth(), true);
                Api.Registration result = api.register(entry);
                if (result.emote() == null) { rejected++; continue; }
                if (result.created()) {
                    owned.put(entry.name(), new Owned(entry, result.emote()));
                    changed = true;
                }
                existing = result.emote();
            }
            if (RegistrationPlan.sameImage(api.loadData.invoke(existing), wanted.id())) {
                matched++;
                if (retryImages && ((Number) api.width.invoke(existing)).intValue() <= 0)
                    api.loadRequested.setBoolean(existing, false);
            } else conflicts++;
        }
        if (changed || retryImages) client.gui.hud.getChat().rescaleChat();
        String problem = !imageProblem.isEmpty() ? imageProblem
                : conflicts > 0 ? "Часть имён занята другим набором"
                : rejected > 0 ? "Streamotes не поддерживает часть имён"
                : plan.names().isEmpty() ? "Нет каталогов для чата в этом мире" : "";
        health = new Health(true, true, plan.names().size(), matched, conflicts,
                rejected, IMAGE_ERRORS.get(), problem);
    }

    private static void retireOwned() {
        Api current = api;
        if (current == null) { owned.clear(); return; }
        for (Owned value : owned.values()) {
            value.retired = true;
            try {
                current.removeOwned(value.entry.name(), value.emote);
                current.close(value.emote);
            } catch (ReflectiveOperationException ignored) { }
        }
        owned.clear();
    }

    /** Invoked on Streamotes' image worker, never from a render callback. */
    public static boolean loadCachedImage(Object emote, URI source, String provider, String id) {
        if (!"7tv".equals(provider) || !MoonotesNetworkClient.isEnabled() || source == null) return false;
        try {
            if (ProxyUrlResolver.resolve(source, "https://moonotes-emotes.thefreelom.workers.dev") == null)
                return false;
            var png = CacheFiles.forId(id);
            if (!Files.exists(png, LinkOption.NOFOLLOW_LINKS)) return false;
            CacheIntegrity.Loaded loaded;
            try {
                if (CacheFiles.expectsAnimation(id)
                        && !Files.isRegularFile(CacheFiles.animationMetadata(png), LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Missing animation metadata");
                loaded = CacheIntegrity.read(png);
            }
            catch (IOException malformed) {
                CacheFiles.markInvalid(id);
                IMAGE_ERRORS.incrementAndGet();
                imageProblem = "Повреждённый кеш: откройте «Проверить и исправить»";
                DIRTY.set(true);
                // Do not fall through to Streamotes' unbounded cache decoder on invalid files.
                throw new UncheckedIOException(malformed);
            }
            Api current = api;
            if (current == null) { current = new Api(); api = current; }
            try {
                synchronized (emote) {
                    String name = (String) current.name.invoke(emote);
                    if (current.find(name) != emote) return true; // Was retired during file I/O.
                    current.setImage.invoke(emote, loaded.image());
                    if (loaded.times().length > 1 || loaded.frameWidth() != loaded.image().getWidth()
                            || loaded.frameHeight() != loaded.image().getHeight())
                        current.setFrameData.invoke(emote, loaded.times(), loaded.frameWidth(), loaded.frameHeight());
                    current.discardBitmap.invoke(emote);
                    if (current.find(name) != emote) {
                        // A metadata worker may clear the registry while NativeImage is being prepared.
                        // The original disposal pass may already have run, so close any late buffer too.
                        Api retiredApi = current;
                        Minecraft.getInstance().execute(() -> {
                            try { retiredApi.close(emote); } catch (ReflectiveOperationException ignored) { }
                        });
                    }
                }
                return true;
            } finally { loaded.image().flush(); }
        } catch (UncheckedIOException failure) { throw failure; }
        catch (ReflectiveOperationException | IllegalArgumentException | LinkageError failure) {
            IMAGE_ERRORS.incrementAndGet();
            imageProblem = "Не удалось передать изображение в Streamotes";
            DIRTY.set(true);
            throw new IllegalStateException("Moonotes cache adapter failed", failure);
        }
    }

    private static final class Api {
        final Class<?> emoteClass, loaderClass;
        final Method fromName, register, setData, loadData, setTooltip, loadImage, width, name,
                setImage, setFrameData, discardBitmap, close;
        final Field loadRequested;
        final Object loadingLock;
        final Map<?, ?> namedMap;
        final Object loader;

        Api() throws ReflectiveOperationException {
            Class<?> registry = Class.forName("xeed.mc.streamotes.emoticon.EmoticonRegistry");
            emoteClass = Class.forName("xeed.mc.streamotes.emoticon.Emoticon");
            loaderClass = Class.forName("xeed.mc.streamotes.api.IEmoticonLoader");
            fromName = registry.getMethod("fromName", String.class);
            register = registry.getMethod("registerEmoticon", String.class, String.class,
                    boolean.class, int.class, loaderClass);
            setData = emoteClass.getMethod("setLoadData", Object.class);
            loadData = emoteClass.getMethod("getLoadData");
            setTooltip = emoteClass.getMethod("setTooltip", String.class);
            width = emoteClass.getMethod("getWidth");
            name = emoteClass.getMethod("getName");
            setImage = emoteClass.getMethod("setImage", BufferedImage.class);
            setFrameData = emoteClass.getMethod("setFrameData", int[].class, int.class, int.class);
            discardBitmap = emoteClass.getMethod("discardBitmap");
            close = emoteClass.getMethod("close");
            loadRequested = emoteClass.getDeclaredField("loadRequested"); loadRequested.setAccessible(true);
            Field lock = registry.getDeclaredField("loadingLock"); lock.setAccessible(true);
            loadingLock = lock.get(null);
            Field map = registry.getDeclaredField("namedMap"); map.setAccessible(true);
            namedMap = (Map<?, ?>) map.get(null);
            Class<?> images = Class.forName("xeed.mc.streamotes.addon.TwitchEmotesAPI");
            loadImage = images.getMethod("loadEmoteImage", emoteClass, URI.class, String.class, String.class);
            loader = Proxy.newProxyInstance(loaderClass.getClassLoader(), new Class<?>[]{loaderClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("toString")) return "Moonotes local catalogue loader";
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("equals")) return proxy == args[0];
                        Object emote = args[0];
                        if (find((String) name.invoke(emote)) != emote) return null;
                        String id = (String) loadData.invoke(emote);
                        // Public Streamotes entry point reaches the bounded local-cache hook first.
                        try { loadImage.invoke(null, emote, URI.create("https://cdn.7tv.app/emote/" + id + "/2x.webp"), "7tv", id); }
                        catch (InvocationTargetException error) { throw error.getCause(); }
                        finally {
                            if (find((String) name.invoke(emote)) != emote) {
                                // Covers cache misses too: a network response may arrive after the
                                // user leaves the world or disables the catalogue.
                                Minecraft.getInstance().execute(() -> {
                                    try { close(emote); } catch (ReflectiveOperationException ignored) { }
                                });
                            }
                        }
                        return null;
                    });
        }

        Object find(String name) throws ReflectiveOperationException { return fromName.invoke(null, name); }

        record Registration(Object emote, boolean created) { }

        Registration register(Entry entry) throws ReflectiveOperationException {
            synchronized (loadingLock) {
                // Native metadata may have supplied this name between the earlier lookup and now.
                Object prior = find(entry.name());
                if (prior != null) return new Registration(prior, false);
                Object emote = register.invoke(null, entry.channel().isEmpty() ? "Moonotes" : entry.channel(),
                        entry.name(), entry.zeroWidth(), FALLBACK_PRIORITY, loader);
                if (emote != null) {
                    setData.invoke(emote, entry.id());
                    setTooltip.invoke(emote, "7TV · " + (entry.channel().isEmpty() ? "Moonotes" : entry.channel()));
                }
                return new Registration(emote, emote != null);
            }
        }

        void removeOwned(String name, Object emote) {
            synchronized (loadingLock) {
                // Emoticon.equals compares the textual name, so Map.remove(key,value) is NOT
                // an identity guard: it could remove a newer native provider with the same name.
                RegistrationPlan.removeIdentity(namedMap, name, emote);
            }
        }

        void close(Object emote) throws ReflectiveOperationException {
            synchronized (emote) { close.invoke(emote); }
        }
    }
}
