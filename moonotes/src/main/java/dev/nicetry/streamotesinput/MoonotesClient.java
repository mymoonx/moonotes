package dev.nicetry.streamotesinput;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Client-only controller for the existing Streamotes mod.
 * Never starts two Streamotes metadata loads at once.  Does not host images.
 */
public final class MoonotesClient implements ClientModInitializer {
    private static final Pattern TWITCH_NAME = Pattern.compile("[A-Za-z0-9_]{1,25}");
    private static final String DEFAULT_CHANNEL = "v0kky";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("moonotes.json");
    private static final Path OLD_CONFIG = FabricLoader.getInstance().getConfigDir().resolve("freelomotes.json");
    private static final int MAX_CHANNELS = 12;
    private static final int QUIET_TICKS = 40;
    private static final int JOIN_DELAY_TICKS = 60;
    private static final int TIMEOUT_TICKS = 20 * 120;

    private static Settings settings = new Settings();
    private static String serverKey = "singleplayer";
    private static boolean connected;
    private static boolean pending;
    private static boolean running;
    private static int ticksUntilCheck;
    private static int idleTicks;
    private static int pendingTicks;
    private static int reportTicks;
    private static int lastKnownCount = -1;
    private static String appliedServerKey = "";
    private static boolean localNoticeShown;
    private static Method notificationBridge;
    private static boolean notificationBridgeChecked;

    private static final class Settings {
        String defaultChannel = DEFAULT_CHANNEL;
        Map<String, List<String>> servers = new TreeMap<>();
    }

    @Override
    public void onInitializeClient() {
        readSettings();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            serverKey = client.getCurrentServer() == null
                ? "singleplayer"
                : client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
            connected = true;
            appliedServerKey = "";
            requestReload(JOIN_DELAY_TICKS);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            connected = false;
            pending = false;
            running = false;
            lastKnownCount = -1;
            appliedServerKey = "";
            serverKey = "singleplayer";
        });
        ClientTickEvents.END_CLIENT_TICK.register(MoonotesClient::onTick);

        // /emotes belongs to Emotecraft, so don't register it and break its subcommands.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            dispatcher.register(literal("moonotes")
                .executes(ctx -> openGui(ctx.getSource()))
                .then(literal("gui").executes(ctx -> openGui(ctx.getSource())))
                .then(literal("list").executes(ctx -> showChannels(ctx.getSource())))
                .then(literal("status").executes(ctx -> showStatus(ctx.getSource())))
                .then(literal("channel").then(argument("twitch", StringArgumentType.word())
                    .executes(ctx -> replaceChannel(ctx.getSource(), StringArgumentType.getString(ctx, "twitch")))))
                .then(literal("add").then(argument("twitch", StringArgumentType.word())
                    .executes(ctx -> addChannel(ctx.getSource(), StringArgumentType.getString(ctx, "twitch")))))
                .then(literal("remove").then(argument("twitch", StringArgumentType.word())
                    .executes(ctx -> removeChannel(ctx.getSource(), StringArgumentType.getString(ctx, "twitch")))))
                .then(literal("reset").executes(ctx -> reset(ctx.getSource())))
                .then(literal("reload").executes(ctx -> reload(ctx.getSource()))));
        });
    }

    private static void requestReload(int delayTicks) {
        pending = true;
        running = false;
        ticksUntilCheck = delayTicks;
        idleTicks = 0;
        pendingTicks = 0;
        lastKnownCount = -1;
        localNoticeShown = false;
    }

    private static void onTick(Minecraft client) {
        if (!connected || client.player == null) return;
        boolean localReady = applyLocalCatalogs();
        if (localReady && !localNoticeShown) {
            localNoticeShown = true;
            notifyUser("Каталоги Moonotes доступны для чата. Остальные провайдеры Streamotes обновляются отдельно.");
        }
        if (running) {
            if (reportTicks-- > 0) return;
            // This is a background job; only count after all loader workers exit.
            Integer active = activeLoaders();
            if (active == null) {
                running = false;
                notifyUser("Не удалось прочитать состояние Streamotes; проверь latest.log");
            } else if (active == 0) {
                running = false;
                Integer total = registeredCount();
                if (total != null) {
                    lastKnownCount = total;
                    notifyUser("Загрузка Streamotes завершена. Зарегистрировано эмотов: " + total);
                } else {
                    notifyUser("Загрузка завершилась, но число эмотов определить не удалось");
                }
            } else if (reportTicks < -TIMEOUT_TICKS) {
                running = false;
                notifyUser(localReady
                    ? "Каталоги Moonotes готовы; другие провайдеры Streamotes ещё загружают данные."
                    : "Streamotes всё ещё загружает данные. Проверь соединение и latest.log");
            }
            return;
        }
        if (!pending) return;
        if (ticksUntilCheck-- > 0) return;
        if (++pendingTicks > TIMEOUT_TICKS) {
            pending = false;
            notifyUser(localReady
                ? "Локальные каталоги работают. Дополнительное обновление Streamotes отложено: его фоновые задачи ещё заняты."
                : "Ожидание загрузчика Streamotes превысило 120 сек. Список сохранён, но обновление не запущено");
            return;
        }
        Integer active = activeLoaders();
        if (active == null) {
            pending = false;
            notifyUser("Не удалось найти загрузчик Streamotes; проверь установленный Streamotes");
            return;
        }
        if (active > 0) {
            idleTicks = 0;
            return;
        }
        if (++idleTicks < QUIET_TICKS) return;
        pending = false;
        idleTicks = 0;
        syncWhenIdle();
    }

    /** Number of outstanding workers in the original Streamotes loader. */
    private static Integer activeLoaders() {
        try {
            Class<?> registry = Class.forName("xeed.mc.streamotes.emoticon.EmoticonRegistry");
            Field field = registry.getDeclaredField("loading");
            field.setAccessible(true);
            return ((AtomicInteger) field.get(null)).get();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            System.err.println("[Moonotes] Cannot inspect Streamotes loader: " + e);
            return null;
        }
    }

    private static Integer registeredCount() {
        try {
            Class<?> registry = Class.forName("xeed.mc.streamotes.emoticon.EmoticonRegistry");
            Field lockField = registry.getDeclaredField("loadingLock");
            lockField.setAccessible(true);
            synchronized (lockField.get(null)) {
                Object names = registry.getMethod("getEmoteNames").invoke(null);
                return names instanceof List<?> list ? list.size() : null;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            System.err.println("[Moonotes] Cannot inspect Streamotes registry: " + e);
            return null;
        }
    }

    private static void syncWhenIdle() {
        try {
            Class<?> clazz = Class.forName("xeed.mc.streamotes.Streamotes");
            Object instance = clazz.getField("INSTANCE").get(null);
            if (instance == null) throw new IllegalStateException("Streamotes client not initialized");
            Object config = clazz.getMethod("getConfig").invoke(instance);
            Field channelsField = config.getClass().getField("emoteChannels");
            List<String> desired = current();
            @SuppressWarnings("unchecked") List<String> old = (List<String>) channelsField.get(config);
            if (desired.equals(old) && appliedServerKey.isEmpty()) {
                appliedServerKey = serverKey;
                Integer count = registeredCount();
                lastKnownCount = count == null ? -1 : count;
                notifyUser("Каналы уже настроены: " + String.join(", ", desired)
                    + (count == null ? "" : ". Эмотов: " + count));
                return;
            }
            // In-memory override only: never edits or destroys the user's Streamotes config.
            channelsField.set(config, new ArrayList<>(desired));
            appliedServerKey = serverKey;
            clazz.getMethod("onPlayerJoin").invoke(instance);
            running = true;
            reportTicks = QUIET_TICKS;
            notifyUser("Запрошена загрузка каналов: " + String.join(", ", desired));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            System.err.println("[Moonotes] Failed to apply channels: " + e);
            notifyUser("Не удалось изменить каналы Streamotes: " + e.getClass().getSimpleName());
        }
    }

    private static List<String> current() {
        List<String> list = settings.servers.get(serverKey);
        return list == null
            ? new ArrayList<>(List.of(settings.defaultChannel)) : new ArrayList<>(list);
    }

    /** Public bridge for the independent catalogue GUI. No server address is exposed. */
    public static List<String> getCurrentChannels() { return List.copyOf(current()); }

    /** Changes only Moonotes' per-world selection, leaving Streamotes' configuration on disk intact. */
    public static boolean setChannelEnabled(String rawName, boolean enabled) {
        if (rawName == null || !TWITCH_NAME.matcher(rawName).matches()) return false;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            // Adding a catalogue finishes on the network worker; world settings belong to the
            // client thread just like the existing command handlers.
            var result = new java.util.concurrent.CompletableFuture<Boolean>();
            client.execute(() -> {
                if (!result.isDone()) result.complete(setChannelEnabled(rawName, enabled));
            });
            try { return result.get(5, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); result.complete(false); return false;
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failed) {
                result.complete(false); return false;
            }
        }
        if (!connected) return false;
        String name = normalize(rawName);
        List<String> channels = current();
        if (enabled) {
            if (channels.contains(name)) return true;
            if (channels.size() >= MAX_CHANNELS) return false;
            channels.add(name);
        } else if (!channels.remove(name)) return true;
        settings.servers.put(serverKey, new ArrayList<>(new LinkedHashSet<>(channels)));
        writeSettings();
        requestReload(0);
        applyLocalCatalogs();
        return true;
    }

    private static boolean applyLocalCatalogs() {
        try {
            Class<?> bridge = Class.forName("dev.mymoonx.moonotesnetwork.StreamotesBridge");
            return Boolean.TRUE.equals(bridge.getMethod("applyLocalCatalogs", List.class).invoke(null, current()));
        } catch (ClassNotFoundException absent) {
            return false; // The base addon continues to work without Moonotes Network.
        } catch (ReflectiveOperationException | LinkageError | RuntimeException incompatible) {
            return false;
        }
    }

    private static String normalize(String name) { return name.toLowerCase(Locale.ROOT); }

    private static int replaceChannel(FabricClientCommandSource src, String name) {
        if (!valid(src, name)) return 0;
        name = normalize(name);
        return change(src, new ArrayList<>(List.of(name)), "Выбран канал " + name);
    }

    private static int addChannel(FabricClientCommandSource src, String name) {
        if (!valid(src, name)) return 0;
        name = normalize(name);
        var list = current();
        if (list.contains(name)) { feedback(src, "Канал уже в списке: " + name); return 1; }
        if (list.size() >= MAX_CHANNELS) { feedback(src, "Максимум " + MAX_CHANNELS + " каналов"); return 0; }
        list.add(name);
        return change(src, list, "Добавлен канал " + name);
    }

    private static int removeChannel(FabricClientCommandSource src, String name) {
        var list = current();
        name = normalize(name);
        if (!list.remove(name)) { feedback(src, "Канала нет в списке: " + name); return 0; }
        if (list.isEmpty()) list.add(DEFAULT_CHANNEL);
        return change(src, list, "Удалён канал " + name);
    }

    private static int reset(FabricClientCommandSource src) {
        return change(src, new ArrayList<>(List.of(DEFAULT_CHANNEL)), "Сброс на " + DEFAULT_CHANNEL);
    }

    private static int reload(FabricClientCommandSource src) {
        if (!connected) { feedback(src, "Сначала войди в мир"); return 0; }
        requestReload(0);
        feedback(src, "Обновление поставлено в очередь; дождёмся завершения текущей загрузки");
        return 1;
    }

    private static int openGui(FabricClientCommandSource src) {
        try {
            // GUI is owned by the separate Moonotes Network mod. No bundled classes.
            Class<?> network = Class.forName("dev.mymoonx.moonotesnetwork.MoonotesNetworkClient");
            network.getMethod("openGui").invoke(null);
            return 1;
        } catch (ReflectiveOperationException | LinkageError e) {
            feedback(src, "Для интерфейса нужен Moonotes Network 0.2.4 или новее");
            return 0;
        }
    }

    private static int showChannels(FabricClientCommandSource src) {
        feedback(src, "Мир/сервер: " + serverKey + " | Каналы: " + String.join(", ", current()));
        return 1;
    }

    private static int showStatus(FabricClientCommandSource src) {
        Integer active = activeLoaders();
        Integer count = registeredCount();
        feedback(src, "Загрузчик: " + (active == null ? "неизвестно" : active > 0 ? "работает (задач: " + active + ")" : "свободен")
            + " | В очереди: " + pending + " | Зарегистрировано: " + (count == null ? "?" : count));
        return 1;
    }

    private static boolean valid(FabricClientCommandSource src, String name) {
        if (TWITCH_NAME.matcher(name).matches()) return true;
        feedback(src, "Неверный Twitch-ник (1–25 символов: A–Z, 0–9, _)");
        return false;
    }

    private static int change(FabricClientCommandSource src, List<String> list, String message) {
        settings.servers.put(serverKey, new ArrayList<>(new LinkedHashSet<>(list)));
        writeSettings();
        if (connected) requestReload(0);
        feedback(src, message + (connected ? ". Обновление поставлено в очередь." : ". Сохранено."));
        return 1;
    }

    private static void feedback(FabricClientCommandSource src, String message) {
        // A command explicitly requests an answer. Keep it available even when routine notices are off.
        src.sendFeedback(Component.literal("[Moonotes] " + message));
    }

    private static void notifyUser(String message) {
        if (routeNotification(message)) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.gui.hud.getChat().addClientSystemMessage(Component.literal("[Moonotes] " + message));
        }
    }

    /** Optional integration; the addons and original Streamotes remain separate mods. */
    private static boolean routeNotification(String message) {
        if (!notificationBridgeChecked) {
            notificationBridgeChecked = true;
            try {
                notificationBridge = Class.forName("dev.mymoonx.moonotesnetwork.MoonotesNetworkClient")
                    .getMethod("notifyAddon", String.class, String.class, boolean.class);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                notificationBridge = null; // Older Network or no Network: retain ordinary chat compatibility.
            }
        }
        if (notificationBridge == null) return false;
        try {
            String normalized = message.toLowerCase(Locale.ROOT);
            boolean error = normalized.contains("не удалось") || normalized.contains("ошиб")
                || normalized.contains("проверь") || normalized.contains("не отвечает")
                || normalized.contains("не запущено");
            notificationBridge.invoke(null, "Moonotes", message, error);
            return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }

    private static void readSettings() {
        Path source = Files.isRegularFile(CONFIG) ? CONFIG : OLD_CONFIG;
        if (!Files.isRegularFile(source)) return;
        try {
            Settings loaded = GSON.fromJson(Files.readString(source, StandardCharsets.UTF_8), Settings.class);
            if (loaded == null || loaded.servers == null) return;
            if (loaded.defaultChannel == null || !TWITCH_NAME.matcher(loaded.defaultChannel).matches()) loaded.defaultChannel = DEFAULT_CHANNEL;
            loaded.defaultChannel = normalize(loaded.defaultChannel);
            var cleaned = new TreeMap<String, List<String>>();
            for (var entry : loaded.servers.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                var names = new LinkedHashSet<String>();
                for (String name : entry.getValue()) {
                    if (name != null && TWITCH_NAME.matcher(name).matches() && names.size() < MAX_CHANNELS) names.add(normalize(name));
                }
                if (!names.isEmpty() || entry.getValue().isEmpty())
                    cleaned.put(entry.getKey(), new ArrayList<>(names));
            }
            loaded.servers = cleaned;
            settings = loaded;
            if (!CONFIG.equals(source)) writeSettings();
        } catch (Exception e) {
            System.err.println("[Moonotes] Invalid config: " + e);
        }
    }

    private static void writeSettings() {
        try {
            Files.createDirectories(CONFIG.getParent());
            Files.writeString(CONFIG, GSON.toJson(settings), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Moonotes] Cannot save config: " + e);
        }
    }
}
