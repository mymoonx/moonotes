package dev.mymoonx.moonotesnetwork;

/** Regression checks for non-repeating titles and in-bounds editor hover previews. */
public final class WheelPreviewPolishCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var plain = WheelCenterLabels.of("Основные", 1, 2, null, null);
        check(plain.title().equals("Основные") && plain.subtitle().equals("1/2"),
            "Unhovered wheel must not repeat page title");
        var hover = WheelCenterLabels.of("Основные", 1, 2, "sad", null);
        check(hover.title().equals("sad") && hover.subtitle().equals("Основные · 1/2"),
            "Hovered emote keeps page context");
        var single = WheelCenterLabels.of("Основные", 1, 1, null, null);
        check(single.subtitle().isEmpty(), "Single page hides unnecessary counter");
        var singleHover = WheelCenterLabels.of("Основные", 1, 1, "sad", null);
        check(singleHover.subtitle().equals("Основные"), "Single-page hover keeps context without counter");
        var message = WheelCenterLabels.of("Страница 2", 2, 2, "sad", "Эмот недоступен");
        check(message.title().equals("Эмот недоступен") && message.subtitle().equals("Страница 2 · 2/2"),
            "Feedback overrides selected emote but retains page context");

        int[][] screens = {{320, 240}, {427, 240}, {640, 360}, {960, 540}};
        for (int[] screen : screens) {
            int width = screen[0], height = screen[1];
            for (int cardX : new int[]{12, width / 2 - 75, width - 164}) {
                var p = WheelPreviewPosition.place(width, height, cardX, 136, 150, 46,
                    176, 154, 32);
                check(p.x() >= 6 && p.x() + 176 <= width - 6, "Preview must fit screen width");
                check(p.y() >= 6 && p.y() + 154 <= height - 32, "Preview must stay above save/cancel");
            }
        }
        var right = WheelPreviewPosition.place(960, 540, 80, 200, 160, 46, 176, 154, 32);
        check(right.x() >= 248, "Preview prefers right side of card");
        var left = WheelPreviewPosition.place(960, 540, 740, 200, 160, 46, 176, 154, 32);
        check(left.x() + 176 <= 732, "Preview falls back to left side of card");
        System.out.println("WheelPreviewPolishCheck: PASS");
    }
}
