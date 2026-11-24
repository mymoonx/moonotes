package dev.mymoonx.moonotesnetwork;

/** Row-based multicolumn wheel editor geometry and draggable scrollbar math. */
record WheelGridGeometry(int left, int top, int width, int bottom,
                         int columns, int rowHeight, int visibleRows, int total) {
    private static final int GAP = 6;
    private static final int TRACK_SPACE = 14;

    WheelGridGeometry {
        if (columns < 1 || rowHeight < 1 || visibleRows < 1 || width < 32 || bottom <= top || total < 0)
            throw new IllegalArgumentException("Invalid editor grid");
    }

    int cardWidth() { return Math.max(1, (width - TRACK_SPACE - GAP*(columns-1))/columns); }
    int cardX(int col) { return left + col * (cardWidth()+GAP); }
    int cardY(int visibleRow) { return top + visibleRow*rowHeight + 2; }
    int gridRows() { return (total + columns - 1)/columns; }
    int maxRow() { return Math.max(0, gridRows() - visibleRows); }
    int trackX() { return left + width - 9; }
    int trackHeight() { return bottom - top; }
    int thumbHeight() {
        if (maxRow()==0) return trackHeight();
        return Math.max(15, (int)Math.round(trackHeight() * Math.min(1.0, visibleRows/(double)gridRows())));
    }
    int thumbTop(int row) {
        if (maxRow()==0) return top;
        return top + (int)Math.round((trackHeight()-thumbHeight()) * Math.clamp(row,0,maxRow())/(double)maxRow());
    }
    int rowForThumb(double mouseY, double grabOffset) {
        int travel = trackHeight()-thumbHeight();
        if (travel<=0) return 0;
        return Math.clamp((int)Math.round((mouseY-grabOffset-top)*maxRow()/(double)travel),0,maxRow());
    }
    boolean onTrack(double x,double y) {
        return x>=trackX()-3 && x<left+width+2 && y>=top && y<bottom;
    }
    int itemAt(double x,double y,int scrollRow) {
        if (x<left || y<top || y>=bottom || x>=trackX()-3) return -1;
        int col=(int)((x-left)/(cardWidth()+GAP));
        if (col<0 || col>=columns || x>=cardX(col)+cardWidth()) return -1;
        int localRow=(int)((y-top)/rowHeight);
        if (localRow<0 || localRow>=visibleRows) return -1;
        int item=(scrollRow+localRow)*columns+col;
        return item<total ? item : -1;
    }
}
