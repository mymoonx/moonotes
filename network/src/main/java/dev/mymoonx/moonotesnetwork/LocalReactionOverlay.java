package dev.mymoonx.moonotesnetwork;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * World-sized emote billboards projected above nearby players. Both their
 * position and size follow the camera, and block raycasts hide occluded ones.
 * This avoids modifying server name tags, entities, world data or network
 * packets. It is local to Moonotes clients; no server plugin required.
 */
final class LocalReactionOverlay {
    private static final int SELF_PREVIEW_SIZE = 16;
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
        // P * R: a rotation preserves this row's length, leaving the vertical
        // perspective scale even when the camera is pitched, rolled or zoomed.
        double verticalScale = Math.sqrt(viewProj.m01() * viewProj.m01()
            + viewProj.m11() * viewProj.m11() + viewProj.m21() * viewProj.m21());
        Vec3 camPos = camera.position();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int width = g.guiWidth(), height = g.guiHeight();
        int rendered = 0;
        for (LocalReactions.Visual visual : active) {
            if (rendered >= 12) break; // bounded even in crowded servers
            Player player = mc.level.getPlayerByUUID(visual.player());
            if (player == null || player.isInvisible()) continue;
            boolean firstPersonSelf = player == mc.player && camera.entity() == mc.player
                && mc.options.getCameraType().isFirstPerson();
            if (firstPersonSelf) {
                // This is explicitly a small self-preview, away from the crosshair.
                // Other players and the third-person avatar always use world sizing.
                previews.draw(g, visual.id(), 8, 8, SELF_PREVIEW_SIZE);
                g.text(mc.font, "Ваша реакция", 29, 12, 0xFFE4F2FF);
                rendered++;
                continue;
            }
            Vec3 head = player.getPosition(partial).add(0, player.getBbHeight(), 0);
            Vec3 pos = head.add(0, 0.18 + ReactionProjection.WORLD_SIZE / 2, 0);
            Vec3 relative = pos.subtract(camPos);
            Vector4f clip = new Vector4f((float) relative.x, (float) relative.y,
                (float) relative.z, 1.0f).mul(viewProj);
            ReactionProjection.Placement placement = ReactionProjection.project(clip.x, clip.y, clip.w,
                verticalScale, relative.lengthSqr(), width, height);
            if (placement == null) continue;
            // Raycast from the actual camera, not the player's eyes: F5 and
            // camera mods can move the viewer far from the local entity.
            if (!clearView(mc, camPos, head.add(0, -0.1, 0), player)
                    || !clearView(mc, camPos, pos, player)) continue;
            previews.draw(g, visual.id(), placement.x(), placement.y(), placement.size());
            rendered++;
        }
    }

    private static boolean clearView(Minecraft mc, Vec3 camera, Vec3 target, Player player) {
        return mc.level.clip(new ClipContext(camera, target, ClipContext.Block.VISUAL,
            ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }
}
