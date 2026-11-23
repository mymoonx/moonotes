package dev.mymoonx.moonotesnetwork;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;
import java.util.Locale;

/** Local notices and transfer progress share one movable, compact HUD anchor. */
final class MoonotesNotifications {
    private static final NotificationQueue NOTICES = new NotificationQueue();
    private record Finished(int ready, int processed, int total, int errors, boolean cancelled, long expiresAt) { }
    private static Finished finished;
    private static volatile boolean registered;
    private MoonotesNotifications() { }

    static void register() {
        if (registered) return;
        HudElementRegistry.attachElementAfter(VanillaHudElements.TITLE_AND_SUBTITLE,
            Identifier.fromNamespaceAndPath("moonotes_network", "notices"), MoonotesNotifications::draw);
        registered = true;
    }

    static boolean isRegistered() { return registered; }

    static void notifyUser(String title, String description, boolean error) {
        post(title, description, error, false);
    }
    static void clearTransient() {
        NOTICES.clearTransient();
        finished = null;
    }
    static List<NotificationQueue.Event> history() { return NOTICES.history(); }

    private static void post(String title, String description, boolean error, boolean suppressVisual) {
        Minecraft.getInstance().execute(() -> {
            String mode = MoonotesNetworkClient.notificationMode();
            var entry = NOTICES.add(title, description, error, System.currentTimeMillis(), mode, suppressVisual);
            if (entry != null && "chat".equals(mode)) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(
                    Component.literal(("Streamotes".equals(entry.title()) ? "[Streamotes] " : "[Moonotes] ") + entry.message()));
            }
        });
    }

    static void downloadStarted() {
        Minecraft.getInstance().execute(() -> {
            finished = null;
            post("Загрузка", "Загрузка начата. Окно можно свернуть: прогресс останется на экране.", false,
                MoonotesNetworkClient.hudProgressEnabled());
        });
    }

    static void downloadFinished(int downloaded, int cached, int errors, boolean cancelled) {
        // Capture values while the originating transfer still owns its counters.
        int processed = MoonotesNetworkClient.progress(), total = MoonotesNetworkClient.total();
        Minecraft.getInstance().execute(() -> {
            String mode = MoonotesNetworkClient.notificationMode();
            boolean useHud = MoonotesNetworkClient.hudProgressEnabled() && NotificationQueue.visible(mode, errors > 0);
            finished = useHud ? new Finished(downloaded + cached, processed, total, errors, cancelled,
                System.currentTimeMillis() + (errors > 0 ? 6500 : 4200)) : null;
            String text = (cancelled ? "Остановлена. " : "Завершена. ") + "Новых: " + downloaded
                + ", уже в кеше: " + cached + ", ошибок: " + errors;
            post("Загрузка эмотов", text, errors > 0, useHud);
        });
    }

    static void draw(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() instanceof HudLayoutScreen) return;
        int width = g.guiWidth(), height = g.guiHeight();
        if (width < 150 || height < 110) return;
        long now = System.currentTimeMillis();
        String mode = MoonotesNetworkClient.notificationMode();
        var current = NOTICES.current(now, mode);
        boolean busy = MoonotesNetworkClient.isDownloading();
        Finished completed = finished;
        if (completed != null && (now > completed.expiresAt() || !NotificationQueue.visible(mode, completed.errors() > 0))) {
            finished = completed = null;
        }
        boolean downloadPage = mc.gui.screen() instanceof MoonotesScreen screen && screen.isDownloadPageVisible();
        boolean progress = MoonotesNetworkClient.hudProgressEnabled() && !downloadPage && (busy || completed != null);
        int textWidth = Math.min(HudLayout.WIDTH, width - HudLayout.MARGIN * 2) - 16;
        List<FormattedCharSequence> lines = current == null ? List.of()
            : mc.font.split(Component.literal(current.message()), Math.max(30, textWidth));
        int noticeHeight = current == null ? 0 : 23 + Math.min(2, lines.size()) * mc.font.lineHeight;
        var cards = HudLayout.cards(width, height, MoonotesNetworkClient.hudPlacement(), progress, noticeHeight);
        if (cards.progress() != null) {
            int processed = busy ? MoonotesNetworkClient.progress() : completed.processed();
            int total = busy ? MoonotesNetworkClient.total() : completed.total();
            int errors = busy ? MoonotesNetworkClient.failed() : completed.errors();
            boolean paused = busy && MoonotesNetworkClient.isPaused();
            String label = busy ? paused ? "На паузе" : "Загрузка эмотов"
                : completed.cancelled() ? "Остановлено" : errors > 0 ? "Есть ошибки" : "Готово";
            String detail = busy ? Math.max(0, processed) + "/" + Math.max(0, total)
                : completed.cancelled() ? "Обработано " + Math.max(0, processed) + "/" + Math.max(0, total)
                : "В кеше " + Math.max(0, completed.ready()) + "/" + Math.max(0, total);
            if (errors > 0) detail += " · ошибок: " + errors;
            else if (busy && !paused) detail += " · " + speed(MoonotesNetworkClient.speedBytesPerSecond());
            drawProgress(g, cards.progress(), label, detail, total == 0 ? 0 : processed / (double) total,
                errors > 0 ? 0xFFE0A56D : paused ? 0xFFD6BD87 : 0xFF81B3F5);
        }
        if (cards.notice() != null && current != null) drawNotice(g, cards.notice(), current.title(), lines, current.error());
    }

    static void drawProgress(GuiGraphicsExtractor g, MoonotesLayout.Rect box, String title, String detail,
                             double fraction, int color) {
        var font = Minecraft.getInstance().font;
        panel(g, box, 0xC8182435, 0xC9425670);
        g.fill(box.x(), box.y() + 1, box.x() + 2, box.bottom() - 1, color);
        g.text(font, Component.literal(font.plainSubstrByWidth(title, box.width() - 16)),
            box.x() + 8, box.y() + 5, 0xFFEDF2FA, false);
        g.text(font, Component.literal(font.plainSubstrByWidth(detail, box.width() - 16)),
            box.x() + 8, box.y() + 17, 0xFFA9B8CB, false);
        int barWidth = box.width() - 16;
        g.fill(box.x() + 8, box.bottom() - 6, box.right() - 8, box.bottom() - 3, 0xFF35445A);
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * barWidth);
        g.fill(box.x() + 8, box.bottom() - 6, box.x() + 8 + filled, box.bottom() - 3, color);
    }

    static void drawNotice(GuiGraphicsExtractor g, MoonotesLayout.Rect box, String title,
                           List<FormattedCharSequence> lines, boolean error) {
        var font = Minecraft.getInstance().font;
        panel(g, box, 0xE0192638, error ? 0xFFA47A56 : 0xD8496383);
        g.text(font, Component.literal(font.plainSubstrByWidth(title, box.width() - 16)),
            box.x() + 8, box.y() + 6, 0xFFEDF2FA, false);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(font, lines.get(i), box.x() + 8, box.y() + 20 + i * font.lineHeight, 0xFFA9B8CB, false);
        }
        // Full text stays available in Settings -> Events; the HUD never grows over the chat.
        if (lines.size() > 2) g.text(font, "…", box.right() - 14, box.bottom() - font.lineHeight - 2, 0xFFA9B8CB, false);
    }

    private static void panel(GuiGraphicsExtractor g, MoonotesLayout.Rect box, int fill, int border) {
        g.fill(box.x(), box.y(), box.right(), box.bottom(), fill);
        g.outline(box.x(), box.y(), box.width(), box.height(), border);
    }
    private static String speed(double bytes) {
        if (!Double.isFinite(bytes) || bytes < 0) bytes = 0;
        return bytes >= 1048576 ? String.format(Locale.ROOT, "%.1f МиБ/с", bytes / 1048576)
            : String.format(Locale.ROOT, "%.0f КиБ/с", bytes / 1024);
    }
}
