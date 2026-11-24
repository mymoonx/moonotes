package dev.mymoonx.moonotesnetwork;

/** Positions a hover preview beside its card, within the GUI and above footer actions. */
record WheelPreviewPosition(int x, int y) {
    static WheelPreviewPosition place(int screenWidth, int screenHeight,
                                      int cardX, int cardY, int cardWidth, int cardHeight,
                                      int popupWidth, int popupHeight, int footerInset) {
        int margin = 6, gap = 8;
        int right = cardX + cardWidth + gap;
        int left = cardX - popupWidth - gap;
        int maxX = Math.max(margin, screenWidth - popupWidth - margin);
        int x = right <= maxX ? right : left >= margin ? left
            : Math.clamp(cardX + (cardWidth - popupWidth) / 2, margin, maxX);
        int maxY = Math.max(margin, screenHeight - popupHeight - footerInset);
        int y = Math.clamp(cardY + (cardHeight - popupHeight) / 2, margin, maxY);
        if (x < cardX + cardWidth && x + popupWidth > cardX) {
            if (cardY - popupHeight - gap >= margin) y = cardY - popupHeight - gap;
            else if (cardY + cardHeight + gap <= maxY) y = cardY + cardHeight + gap;
        }
        return new WheelPreviewPosition(x, y);
    }
}
