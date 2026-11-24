package dev.mymoonx.moonotesnetwork;

/** Shared drawing and hit-test geometry, in Minecraft's scaled GUI coordinates. */
final class MoonotesLayout {
    record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }
    record Grid(Rect viewport, int columns, int rows, int cardWidth, int rowHeight, int gap) {
        Rect card(int column, int row) {
            return new Rect(viewport.x + column * (cardWidth + gap),
                viewport.y + row * rowHeight, cardWidth, rowHeight - gap);
        }
        int positionAt(double x, double y, int firstRow, int itemCount) {
            if (!viewport.contains(x, y)) return -1;
            int column = (int) ((x - viewport.x) / (cardWidth + gap));
            int row = (int) ((y - viewport.y) / rowHeight);
            if (column < 0 || column >= columns || row < 0 || row >= rows ||
                !card(column, row).contains(x, y)) return -1;
            int pos = (firstRow + row) * columns + column;
            return pos >= 0 && pos < itemCount ? pos : -1;
        }
        int maxScroll(int items) { return Math.max(0, (items + columns - 1) / columns - rows); }
        int firstVisible(int firstRow, int items) { return items==0 ? 0 : Math.min(items,Math.max(0,firstRow)*columns+1); }
        int lastVisible(int firstRow, int items) { return Math.min(items,(Math.max(0,firstRow)+rows)*columns); }
        Rect scrollbar() { return new Rect(viewport.right() + 5, viewport.y, 13, rows * rowHeight - gap); }
    }

    final Rect panel;
    final int contentX, contentWidth, contentTop, contentBottom, footerY;
    final boolean wide;
    MoonotesLayout(int screenWidth, int screenHeight) {
        int margin = screenWidth < 360 || screenHeight < 260 ? 6 : 12;
        int pw = Math.max(1, Math.min(720, screenWidth - margin * 2));
        int ph = Math.max(1, Math.min(480, screenHeight - margin * 2));
        panel = new Rect((screenWidth - pw) / 2, (screenHeight - ph) / 2, pw, ph);
        contentX = panel.x + 12;
        contentWidth = Math.max(1, pw - 24);
        contentTop = panel.y + 66;
        footerY = panel.bottom() - 32;
        contentBottom = footerY - 10;
        wide = pw >= 520;
    }
    Rect closeButton() { return new Rect(panel.right()-34, panel.y()+4, 22, 22); }
    int authorRight() { return closeButton().x()-10; }
    Rect footer(int index, int count) {
        int gap = 6;
        int w = Math.max(1, (contentWidth - gap * (count - 1)) / count);
        return new Rect(contentX + index * (w + gap), footerY, w, 22);
    }
    Rect settingsSidebar() {
        return new Rect(contentX,contentTop,Math.min(112,Math.max(84,contentWidth/5)),contentBottom-contentTop);
    }
    Rect settingsTab(int index) {
        var side=settingsSidebar();
        int step=Math.min(25,Math.max(14,(side.height()-20)/6));
        return new Rect(side.x()+5,side.y()+18+index*step,side.width()-10,Math.min(22,step-2));
    }
    Rect settingsHeading() {
        var side=settingsSidebar();
        return new Rect(side.right()+12,contentTop+3,contentX+contentWidth-side.right()-12,33);
    }
    Grid settingsGrid(int items) {
        var heading=settingsHeading();
        int top=contentTop+42,gap=6,rowHeight=50;
        int columns=heading.width()>=420?2:1;
        int rows=Math.max(1,(contentBottom-top+gap)/rowHeight);
        boolean scroll=items>columns*rows;
        int usable=Math.max(1,heading.width()-(scroll?18:0));
        int cw=Math.max(1,(usable-gap*(columns-1))/columns);
        return new Grid(new Rect(heading.x(),top,usable,rows*rowHeight-gap),columns,rows,cw,rowHeight,gap);
    }
    Rect downloadMinimize() { return new Rect(contentX+contentWidth-104,contentTop,104,22); }
    Rect downloadProgress() { return new Rect(contentX,contentTop+30,contentWidth,6); }
    int librarySummaryY() { return contentBottom-9; }
    Rect libraryStar(Rect card) {
        int detailY=card.y()+Math.max(15,card.height()-14);
        return new Rect(card.right()-18,detailY-2,17,card.bottom()-detailY+1);
    }
    Grid libraryGrid() {
        int gridTop = contentTop + 30;
        int gap = 5, rowHeight = panel.height()<270 ? 32 : 40;
        int usableWidth = Math.max(1, contentWidth - 18);
        int cols = Math.max(1, Math.min(5, (usableWidth + gap) / 128));
        int cardWidth = Math.max(1, (usableWidth - gap * (cols - 1)) / cols);
        int rows = Math.max(1, (contentBottom - 17 - gridTop + gap) / rowHeight);
        return new Grid(new Rect(contentX, gridTop, usableWidth, rows * rowHeight - gap),
            cols, rows, cardWidth, rowHeight, gap);
    }
    Rect modal(boolean report) {
        int mw = Math.max(1, Math.min(report ? 560 : 400, panel.width - 20));
        int mh = Math.max(1, Math.min(report ? 392 : 322, panel.height - 16));
        return new Rect(panel.x + (panel.width - mw) / 2, panel.y + (panel.height - mh) / 2, mw, mh);
    }
}

