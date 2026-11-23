package dev.mymoonx.moonotesnetwork;

/** Pure scrollbar geometry; independent of Minecraft input/render classes. */
final class ScrollMath {
    private ScrollMath() { }
    static int clamp(int value,int min,int max) { return Math.max(min,Math.min(max,value)); }
    static int thumbHeight(int track,int visibleRows,int maxScroll) {
        if(track<=0)return 0;
        if(maxScroll<=0)return track;
        return Math.min(track,Math.max(18,(int)((long)track*visibleRows/(maxScroll+visibleRows))));
    }
    static int thumbOffset(int track,int thumb,int row,int maxScroll) {
        if(maxScroll<=0)return 0;
        return (int)((long)Math.max(0,track-thumb)*clamp(row,0,maxScroll)/maxScroll);
    }
    static int rowForMouse(double y,int trackTop,int trackHeight,int visibleRows,int maxScroll,double grabOffset) {
        if(maxScroll<=0 || trackHeight<=0)return 0;
        int thumb=thumbHeight(trackHeight,visibleRows,maxScroll);
        double travel=trackHeight-thumb;
        if(travel<=0)return 0;
        double fraction=(y-trackTop-grabOffset)/travel;
        if(!Double.isFinite(fraction))return 0;
        return clamp((int)Math.round(fraction*maxScroll),0,maxScroll);
    }
}
