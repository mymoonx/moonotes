package dev.mymoonx.moonotesnetwork;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Actual emote images in a camera-projected HUD overlay above nearby players.
 * This avoids modifying server name tags, entities, world data or network
 * packets. It is local to Moonotes clients; no server plugin required.
 */
final class LocalReactionOverlay {
    private static final int ICON_SIZE = 34;
    private static boolean initialized;
    private static EmotePreviews previews;

    private LocalReactionOverlay() { }

    static void register() {
        if (initialized) return;
        initialized = true;
        previews = new EmotePreviews();
        HudElementRegistry.attachElementAfter(VanillaHudElements.TITLE_AND_SUBTITLE,
            Identifier.fromNamespaceAndPath("moonotes_network","reactions"), LocalReactionOverlay::draw);
    }

    static void disconnect() {
        LocalReactions.clear();
        if (previews != null) {
            previews.close();
            previews = new EmotePreviews();
        }
    }

    private static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gui.hud.isHidden()
            || mc.gui.screen() instanceof EmoteWheelScreen || !ExtrasSettings.local().reactions())
            return;
        long now = System.currentTimeMillis();
        var active = LocalReactions.active(now);
        if (active.isEmpty() || previews == null) return;
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        Matrix4f viewProj = camera.getViewRotationProjectionMatrix(new Matrix4f());
        Vec3 camPos = camera.position();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int width = g.guiWidth(), height = g.guiHeight();
        int rendered = 0;
        for (LocalReactions.Visual visual : active) {
            if (++rendered > 12) break; // bounded even in crowded servers
            Player player = mc.level.getPlayerByUUID(visual.player());
            if (player == null || player.isInvisible()) continue;
            if (player.distanceToSqr(mc.player) > 32 * 32) continue;
            int x, y;
            boolean firstPersonSelf = player == mc.player && mc.options.getCameraType().isFirstPerson();
            if (firstPersonSelf) {
                x = width / 2;
                y = Math.max(42, height / 2 - 74);
            } else {
                Vec3 pos = player.getPosition(partial).add(0, player.getBbHeight() + 0.46, 0);
                Vector4f clip = new Vector4f((float)(pos.x - camPos.x), (float)(pos.y - camPos.y),
                    (float)(pos.z - camPos.z), 1.0f).mul(viewProj);
                if (clip.w <= 0.02f || !Float.isFinite(clip.w)) continue;
                float nx = clip.x / clip.w, ny = clip.y / clip.w;
                if (!Float.isFinite(nx) || !Float.isFinite(ny)
                    || Math.abs(nx) > 1.12 || Math.abs(ny) > 1.12) continue;
                x = Math.round(width * (0.5f + nx * 0.5f));
                y = Math.round(height * (0.5f - ny * 0.5f));
            }
            int boxX = x - ICON_SIZE/2-5, boxY = y - ICON_SIZE-6;
            g.fill(boxX, boxY, boxX + ICON_SIZE + 10, boxY + ICON_SIZE + 12, 0xDF1B2B40);
            g.outline(boxX, boxY, ICON_SIZE + 10, ICON_SIZE + 12, 0xFF6D91BA);
            previews.draw(g, visual.id(), x - ICON_SIZE/2, boxY + 4, ICON_SIZE);
            // In first-person show a readable fallback as a quick local test.
            if (firstPersonSelf) {
                String shortName = mc.font.plainSubstrByWidth(visual.name(), 130);
                g.centeredText(mc.font, shortName, x, boxY - 12, 0xFFE4F2FF);
            }
        }
    }
}
