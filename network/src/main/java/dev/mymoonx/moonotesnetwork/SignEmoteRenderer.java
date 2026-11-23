package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignText;

/**
 * Sign-face renderer with independent image quads. It runs after vanilla applies
 * the face transform, so wall, standing, hanging, front and back share one path.
 * Saved components and the vanilla glyph cache are never changed.
 */
public final class SignEmoteRenderer {
    private static EmotePreviews previews;

    private SignEmoteRenderer() { }

    public static boolean submit(Font font, SignRenderState state, PoseStack poses,
            SubmitNodeCollector collector, SignText sign) {
        if (!ExtrasSettings.local().signText() || sign == null
                || state.textLineHeight < 1 || state.maxTextLineWidth < 1) return false;
        List<Component> messages = sign.getMessages(state.isTextFilteringEnabled);
        if (messages.size() != 4) return false;
        if (messages.stream().noneMatch(message -> !EmoteTextTokens.find(message.getString()).isEmpty()))
            return false;
        FormattedCharSequence[] fallback = sign.getRenderMessages(state.isTextFilteringEnabled, message -> {
            List<FormattedCharSequence> wrapped = font.split(message, state.maxTextLineWidth);
            return wrapped.isEmpty() ? FormattedCharSequence.EMPTY : wrapped.getFirst();
        });

        List<Line> lines = new ArrayList<>(4);
        boolean anyImage = false;
        int iconSize = Math.min(12, state.textLineHeight);
        for (int i = 0; i < messages.size(); i++) {
            Line line = layout(font, messages.get(i), state.maxTextLineWidth, iconSize, fallback[i]);
            lines.add(line);
            anyImage |= line.hasImage;
        }
        // While cache images load, vanilla draws the complete original text.
        if (!anyImage) return false;

        int darkColor = AbstractSignRenderer.getDarkColor(sign);
        int color = sign.hasGlowingText() ? sign.getColor().getTextColor() : darkColor;
        int light = sign.hasGlowingText() ? 0xF000F0 : state.lightCoords;
        boolean outline = sign.hasGlowingText()
                && (color == DyeColor.BLACK.getTextColor() || state.drawOutline);
        int midpoint = 4 * state.textLineHeight / 2;
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            float x = -line.width / 2f;
            float y = index * state.textLineHeight - midpoint;
            for (Piece piece : line.pieces) {
                if (piece.texture != null) {
                    submitImage(collector, poses, piece.texture, x, y - 1, iconSize, light);
                } else {
                    collector.submitText(poses, x, y, piece.text, false,
                            Font.DisplayMode.POLYGON_OFFSET, light, color, 0, outline ? darkColor : 0);
                }
                x += piece.width;
            }
        }
        return true;
    }

    private static Line layout(Font font, Component message, int maxWidth, int iconSize,
            FormattedCharSequence fallback) {
        StyledText styled = flatten(message.getVisualOrderText());
        List<EmoteTextTokens.Match> matches = EmoteTextTokens.find(styled.text);
        if (matches.isEmpty()) return vanillaLine(font, fallback);

        List<Replacement> replacements = new ArrayList<>();
        for (EmoteTextTokens.Match match : matches) {
            Style style = styled.styles.get(match.start());
            if (style.getClickEvent() != null || style.isObfuscated()) continue;
            boolean uniformStyle = true;
            for (int i = match.start() + 1; i < match.end(); i++) {
                if (!style.equals(styled.styles.get(i))) { uniformStyle = false; break; }
            }
            if (!uniformStyle) continue;
            if (previews == null) previews = new EmotePreviews();
            Identifier texture = previews.texture(match.id());
            if (texture != null) replacements.add(new Replacement(match.start(), match.end(), texture));
            else if (previews.missing(match.id())) WorldImageRequests.request(match);
        }
        if (replacements.isEmpty()) return vanillaLine(font, fallback);

        List<Piece> pieces = new ArrayList<>();
        List<FormattedCharSequence> pending = new ArrayList<>();
        int pendingWidth = 0, width = 0, position = 0, replacementIndex = 0;
        boolean hasImage = false;
        while (position < styled.text.length()) {
            Replacement next = replacementIndex < replacements.size() ? replacements.get(replacementIndex) : null;
            if (next != null && next.start == position) {
                if (width + iconSize > maxWidth) break;
                if (!pending.isEmpty()) {
                    pieces.add(new Piece(FormattedCharSequence.composite(List.copyOf(pending)), null, pendingWidth));
                    pending.clear();
                    pendingWidth = 0;
                }
                pieces.add(new Piece(null, next.texture, iconSize));
                width += iconSize;
                hasImage = true;
                position = next.end;
                replacementIndex++;
            } else {
                int codepoint = styled.text.codePointAt(position);
                if (codepoint == '\n' || codepoint == '\r') break;
                FormattedCharSequence glyph = FormattedCharSequence.codepoint(codepoint, styled.styles.get(position));
                int advance = font.width(glyph);
                if (width + advance > maxWidth) break;
                pending.add(glyph);
                pendingWidth += advance;
                width += advance;
                position += Character.charCount(codepoint);
            }
        }
        if (!pending.isEmpty()) {
            pieces.add(new Piece(FormattedCharSequence.composite(List.copyOf(pending)), null, pendingWidth));
        }
        return hasImage ? new Line(List.copyOf(pieces), width, true) : vanillaLine(font, fallback);
    }

    private static Line vanillaLine(Font font, FormattedCharSequence line) {
        int width = font.width(line);
        return new Line(List.of(new Piece(line, null, width)), width, false);
    }

    private static StyledText flatten(FormattedCharSequence sequence) {
        StringBuilder text = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        sequence.accept((index, style, codepoint) -> {
            if (text.length() >= 4096) return false;
            text.appendCodePoint(codepoint);
            for (int i = 0; i < Character.charCount(codepoint); i++) styles.add(style);
            return true;
        });
        return new StyledText(text.toString(), styles);
    }

    private static void submitImage(SubmitNodeCollector collector, PoseStack poses, Identifier texture,
            float x, float y, float size, int light) {
        // The texture contains a fitted whole frame. There is no font-atlas UV or name-length state.
        collector.submitCustomGeometry(poses, RenderTypes.textPolygonOffset(texture), (pose, vertices) -> {
            vertex(vertices, pose, x, y, 0, 0, light);
            vertex(vertices, pose, x, y + size, 0, 1, light);
            vertex(vertices, pose, x + size, y + size, 1, 1, light);
            vertex(vertices, pose, x + size, y, 1, 0, light);
        });
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
            float x, float y, float u, float v, int light) {
        vertices.addVertex(pose, x, y, 0).setColor(0xFFFFFFFF).setUv(u, v).setLight(light);
    }

    public static void reset() {
        if (previews != null) previews.close();
        previews = null;
    }

    private record StyledText(String text, List<Style> styles) { }
    private record Replacement(int start, int end, Identifier texture) { }
    private record Piece(FormattedCharSequence text, Identifier texture, int width) { }
    private record Line(List<Piece> pieces, int width, boolean hasImage) { }
}
