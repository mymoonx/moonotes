package dev.mymoonx.moonotesnetwork;

import java.util.Map;
import org.joml.Matrix4f;

/** Regression for the fixed-size distant reaction shown in the gameplay report. */
public final class ReactionProjectionCheck {
    public static void main(String[] args) {
        var near = ReactionProjection.project(0, 0, 4, 1.5, 16, 640, 360);
        var far = ReactionProjection.project(0, 0, 16, 1.5, 256, 640, 360);
        require(near != null && far != null, "near and far billboards visible");
        require(near.size() == 34 && far.size() == 8,
            "four times farther shrinks approximately fourfold instead of fixed HUD sizing");
        require(near.x() + near.size() / 2 == 320 && near.y() + near.size() / 2 == 180,
            "world anchor remains centered when size changes");
        require(ReactionProjection.project(0, 0, 0.1, 1.5, 0.01, 640, 360).size() <= 40,
            "close reaction cannot cover the whole view");
        require(ReactionProjection.project(0, 0, -4, 1.5, 16, 640, 360) == null,
            "behind camera is hidden");
        require(ReactionProjection.project(100, 0, 4, 1.5, 16, 640, 360) == null,
            "offscreen is hidden");
        require(ReactionProjection.project(0, 0, 33, 1.5, 1089, 640, 360) == null,
            "range uses camera distance even for local player in third person");
        require(ReactionProjection.project(0, 0, 31, 0.1, 961, 320, 180) == null,
            "distant subpixel reaction is culled instead of magnified");
        require(ReactionProjection.project(0, 0, Double.NaN, 1.5, 16, 640, 360) == null,
            "invalid projection ignored");

        // The Y-row norm gives the same projection scale when the camera rotates.
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, 0.05f, 100);
        Matrix4f rotated = new Matrix4f(projection).rotateX(0.45f).rotateY(-0.73f).rotateZ(0.1f);
        double verticalScale = Math.sqrt(rotated.m01() * rotated.m01()
            + rotated.m11() * rotated.m11() + rotated.m21() * rotated.m21());
        require(Math.abs(verticalScale - projection.m11()) < 0.00001,
            "perspective sizing remains correct for pitched and rolled cameras");

        var emote = new ChatPalette.Emote("id:42", "01F5Z4B9700008KDXA2TJQBNGW", "42", "Test");
        Map<String,ChatPalette.Emote> names = Map.of("42", emote);
        require(ChatReactionObserver.findEmote("42", names) == emote, "local emote");
        require(ChatReactionObserver.findEmote("!42", names) == emote, "global emote");
        require(ChatReactionObserver.findEmote("!:42:", names) == emote, "global colon-wrapped emote");
        require(ChatReactionObserver.findEmote("! 42", names) == emote, "global space after prefix");
        require(ChatReactionObserver.findEmote("142", names) == null, "no substring alias match");
        require(ChatReactionObserver.findEmote("/42", names) == null, "no command alias match");
        require(ChatReactionObserver.parseVanillaLine("[Global] Steve: 42") == null,
            "do not guess unknown system-message authors");
        System.out.println("ReactionProjectionCheck: PASS");
    }

    private static void require(boolean value, String description) {
        if (!value) throw new AssertionError(description);
    }
}
