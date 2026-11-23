package dev.mymoonx.moonotesnetwork;

/** A captured scrollbar drag keeps its grip until release, even outside the track. */
final class ScrollState {
    int row;
    private boolean dragging;
    private double grab;
    private int top, height, visible, maximum;

    void configure(int top, int height, int visible, int maximum) {
        this.top = top;
        this.height = height;
        this.visible = visible;
        this.maximum = Math.max(0, maximum);
        row = ScrollMath.clamp(row, 0, this.maximum);
        if (this.maximum == 0) release();
    }
    int maximum() { return maximum; }
    boolean dragging() { return dragging; }
    int thumbHeight() { return ScrollMath.thumbHeight(height, visible, maximum); }
    int thumbTop() { return top + ScrollMath.thumbOffset(height, thumbHeight(), row, maximum); }
    void press(double mouseY) {
        if (maximum <= 0 || height <= 0) return;
        int thumb = thumbHeight();
        int offset = thumbTop();
        grab = mouseY >= offset && mouseY < offset + thumb ? mouseY - offset : thumb / 2d;
        dragging = true;
        drag(mouseY);
    }
    boolean drag(double mouseY) {
        if (!dragging) return false;
        row = ScrollMath.rowForMouse(mouseY, top, height, visible, maximum, grab);
        return true;
    }
    boolean release() {
        boolean wasDragging = dragging;
        dragging = false;
        return wasDragging;
    }
    void move(int delta) { row = ScrollMath.clamp(row + delta, 0, maximum); }
}

