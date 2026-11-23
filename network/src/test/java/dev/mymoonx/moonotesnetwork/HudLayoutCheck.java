package dev.mymoonx.moonotesnetwork;

/** Real production placement math: clipping, shared stack and saved positions at GUI scales. */
public final class HudLayoutCheck {
    private static int checks;
    public static void main(String[] args) {
        for (int[] size : new int[][]{{160,110},{320,240},{427,240},{640,360},{854,480},{1280,720},{1920,1080}}) {
            for (int corner = 0; corner < 4; corner++) {
                verify(size[0], size[1], HudLayout.Placement.corner(corner));
            }
            for (double x : new double[]{0,0.25,0.5,1}) for (double y : new double[]{0,0.2,0.5,1}) {
                verify(size[0], size[1], new HudLayout.Placement(true, 0, x, y));
            }
            var dragged = HudLayout.moved(size[0], size[1], -5000, 9000);
            var box = HudLayout.cards(size[0], size[1], dragged, true, 0).progress();
            require(box.x() == HudLayout.MARGIN, "Dragging past left must clamp");
            require(box.bottom() <= size[1] - 42, "Dragging past bottom covers chat/hotbar margin");
        }
        var fallback = new HudLayout.Placement(true, -10, Double.NaN, Double.POSITIVE_INFINITY);
        require(fallback.corner() == 1 && fallback.x() == 1 && fallback.y() == 0.12, "Invalid persisted coordinates need safe defaults");
        var clamped = new HudLayout.Placement(true, 2, -1, 3);
        require(clamped.x() == 0 && clamped.y() == 1, "Out of range persisted position");
        var empty = HudLayout.cards(640, 360, HudLayout.Placement.defaults(), false, 0);
        require(empty.progress() == null && empty.notice() == null, "Idle HUD reserved a visible slot");
        var normal = HudLayout.Placement.defaults();
        var with = HudLayout.cards(640, 360, normal, true, 41);
        var without = HudLayout.cards(640, 360, normal, true, 0);
        require(with.progress().equals(without.progress()), "Progress jumps when notice expires");
        require(with.notice().y() - with.progress().bottom() == HudLayout.GAP, "A fixed blank slot separates notice from progress");
        var original = HudLayout.cards(640, 360, new HudLayout.Placement(true, 0, 0.3, 0.7), true, 0).progress();
        var stored = HudLayout.moved(640, 360, original.x(), original.y());
        var restored = HudLayout.cards(640, 360, stored, true, 0).progress();
        require(original.equals(restored), "Drag/save moved the anchor");
        var resized = HudLayout.cards(1280, 720, stored, true, 0).progress();
        require(resized.x() > restored.x() && resized.y() > restored.y(), "Saved placement must adapt to a resized GUI");
        System.out.println("HudLayoutCheck: " + checks + " assertions passed (stacking, drag bounds, resize, no reserved gaps)");
    }
    private static void verify(int width, int height, HudLayout.Placement placement) {
        var cards = HudLayout.cards(width, height, placement, true, 41);
        require(cards.progress() != null, "Visible transfer has no progress card");
        bounds(cards.progress(), width, height);
        if (cards.notice() != null) {
            bounds(cards.notice(), width, height);
            require(cards.progress().bottom() <= cards.notice().y() || cards.notice().bottom() <= cards.progress().y(),
                "Notice overlaps transfer bar");
        }
        var onlyNotice = HudLayout.cards(width, height, placement, false, 41);
        require(onlyNotice.progress() == null && onlyNotice.notice() != null, "Notice alone retains a progress slot");
        bounds(onlyNotice.notice(), width, height);
    }
    private static void bounds(MoonotesLayout.Rect r, int width, int height) {
        require(r.x() >= HudLayout.MARGIN && r.y() >= HudLayout.MARGIN, "HUD escaped top/left bounds");
        require(r.right() <= width - HudLayout.MARGIN && r.bottom() <= height - 42, "HUD escaped right/bottom bounds");
    }
    private static void require(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
