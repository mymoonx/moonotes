package dev.mymoonx.moonotesnetwork;

/** Scaled coordinates shared by rendering, mouse dispatch and layout checks. */
final class ChatOverlayGeometry {
    static final int ROW=24, BODY_TOP=78, FOOTER=21, CELL=28;
    record Rect(int x,int y,int width,int height) {
        boolean contains(double px,double py){return px>=x&&py>=y&&px<x+width&&py<y+height;}
    }
    record Layout(int screenW,int screenH,int x,int y,int width,int height,int rows,int bodyTop,boolean form) {
        Rect bounds(){return new Rect(x,y,width,height);}
        Rect row(int index){return new Rect(x+7,y+bodyTop+index*ROW,width-23,ROW-1);}
        int footerY(){return y+height-FOOTER;}
        Rect input(){return new Rect(x+8,y+(form?43:bodyTop-22),width-16,18);}
        int inputLabelY(){return y+(form?30:bodyTop-34);}
        boolean hasInputLabel(){return form||bodyTop==BODY_TOP;}
        Rect tab(int index){int available=width-16,start=x+8+available*index/4;return new Rect(start,y+21,available*(index+1)/4-available*index/4-2,17);}
        Rect footer(int index,int count){int available=width-16,start=x+8+available*index/count;return new Rect(start,footerY(),available*(index+1)/count-available*index/count-3,18);}
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
    static Layout forScreen(int width,int height,boolean form) {
        int w=Math.min(312,Math.max(100,width-16));
        int available=Math.max(1,height-49);
        int top=available<BODY_TOP+ROW+FOOTER?64:BODY_TOP;
        int rows=form?0:Math.max(0,Math.min(7,(available-top-FOOTER)/ROW));
        int h=Math.min(available,form?126:top+rows*ROW+FOOTER);
        int x=Math.max(4,width-w-8),y=Math.max(4,height-44-h);
        return new Layout(width,height,x,y,w,h,rows,top,form);
    }
    static Rect pickerButton(int width,int height){return new Rect(Math.max(4,width-122),height-39,48,21);}
    static Rect managerButton(int width,int height){return new Rect(Math.max(56,width-70),height-39,64,21);}
    static Rect favoriteAction(Rect row){return new Rect(row.x()+row.width()-47,row.y()+3,19,row.height()-6);}
    static Rect moreAction(Rect row){return new Rect(row.x()+row.width()-23,row.y()+3,19,row.height()-6);}
    static Strip suggestions(int width,int height,int count,int preferredX){
        int right=pickerButton(width,height).x()-7;
        int n=Math.max(0,Math.min(Math.min(5,count),(right-5)/CELL));
        int x=Math.max(5,Math.min(preferredX,right-n*CELL));
        return new Strip(x,Math.max(17,height-43),n);
    }
}
