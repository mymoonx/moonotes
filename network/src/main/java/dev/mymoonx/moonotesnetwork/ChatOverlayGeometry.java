package dev.mymoonx.moonotesnetwork;

/** Scaled coordinates shared by rendering, mouse dispatch and layout checks. */
final class ChatOverlayGeometry {
    static final int ROW=24, BODY_TOP=78, FOOTER=MoonotesUiMetrics.ACTION_HEIGHT+MoonotesUiMetrics.FOOTER_PADDING, CELL=28;
    record Rect(int x,int y,int width,int height) {
        boolean contains(double px,double py){return px>=x&&py>=y&&px<x+width&&py<y+height;}
    }
    record Layout(int screenW,int screenH,int x,int y,int width,int height,int rows,int bodyTop,boolean form) {
        Rect bounds(){return new Rect(x,y,width,height);}
        Rect row(int index){return row(index,false);}
        Rect row(int index,boolean scrollbar){
            return new Rect(x+8,y+bodyTop+index*ROW,width-16-(scrollbar?MoonotesUiMetrics.SCROLLBAR_WIDTH+6:0),ROW-2);
        }
        int footerY(){return y+height-FOOTER;}
        Rect input(){return new Rect(x+8,y+(form?43:bodyTop-22),width-16,18);}
        int inputLabelY(){return y+(form?30:bodyTop-34);}
        boolean hasInputLabel(){return form||bodyTop==BODY_TOP;}
        Rect tab(int index){int available=width-16,start=x+8+available*index/4;return new Rect(start,y+21,available*(index+1)/4-available*index/4-2,17);}
        Rect footer(int index,int count){int available=width-16,start=x+8+available*index/count;return new Rect(start,footerY(),available*(index+1)/count-available*index/count-3,22);}
    }
    record Strip(int x,int y,int count) {
        Rect cell(int index){return new Rect(x+index*CELL,y,CELL-2,25);}
        int hit(double px,double py){
            for(int i=0;i<count;i++)if(cell(i).contains(px,py))return i;
            return -1;
        }
    }
    private ChatOverlayGeometry() { }
    static Layout forScreen(int width,int height){return forScreen(width,height,false);}
    static Layout forScreen(int width,int height,boolean form){return forScreen(width,height,form,-1);}
    // A short list now shrinks its popup rather than leaving an oversized empty body.
    static Layout forScreen(int width,int height,boolean form,int itemCount) {
        int w=Math.min(312,Math.max(100,width-16));
        int available=Math.max(1,height-49);
        int top=available<BODY_TOP+ROW+FOOTER
            ?Math.max(43,available-ROW-FOOTER):BODY_TOP;
        int rows=form?0:Math.max(0,Math.min(7,(available-top-FOOTER)/ROW));
        if(!form && itemCount>=0 && rows>0)rows=Math.min(rows,Math.max(1,itemCount));
        int h=Math.min(available,form?126:top+rows*ROW+FOOTER);
        Rect origin=pickerButton(width,height);
        int x=Math.clamp(origin.x()+origin.width()-w,4,Math.max(4,width-w-4));
        int preferredY=origin.y()-h-5;
        if(preferredY<4)preferredY=origin.y()+origin.height()+5;
        int y=Math.clamp(preferredY,4,Math.max(4,height-44-h));
        return new Layout(width,height,x,y,w,h,rows,top,form);
    }
    static Rect pickerButton(int width,int height){return ChatButtonPlacement.picker(width,height);}
    static Rect managerButton(int width,int height){return ChatButtonPlacement.manager(width,height);}
    static Rect favoriteAction(Rect row){return new Rect(row.x()+row.width()-47,row.y()+3,19,row.height()-6);}
    static Rect moreAction(Rect row){return new Rect(row.x()+row.width()-23,row.y()+3,19,row.height()-6);}
    static Strip suggestions(int width,int height,int count,int preferredX){
        Rect picker=pickerButton(width,height);
        int leftSpace=picker.x()-7, rightStart=picker.x()+picker.width()+7;
        boolean after=leftSpace<CELL+5 && rightStart+CELL<width-5;
        int n=after
            ? Math.max(0,Math.min(Math.min(5,count),(width-rightStart-5)/CELL))
            : Math.max(0,Math.min(Math.min(5,count),(leftSpace-5)/CELL));
        int x=after?rightStart:Math.max(5,Math.min(preferredX,leftSpace-n*CELL));
        return new Strip(x,Math.max(17,height-43),n);
    }
}
