package dev.mymoonx.moonotesnetwork;

/** Cross-aligned close X and title never overflow the header at multiple GUI scales. */
public final class HeaderGeometryCheck {
    public static void main(String[] args) {
        int checks=0;
        for(int[] size:new int[][]{{320,240},{427,240},{640,360},{854,480},{1280,720},{1920,1080}}){
            MoonotesLayout layout=new MoonotesLayout(size[0],size[1]);
            var x=layout.closeButton();
            require(x.right()==layout.panel.right()-12,"right margin inconsistent");checks++;
            require(x.y()==layout.panel.y()+4,"top margin inconsistent");checks++;
            require(x.y()+x.height()==layout.panel.y()+26,"X must center on 30px header");checks++;
            require(layout.authorRight()<x.x(),"author overlaps close X");checks++;
            require(x.bottom()<layout.contentTop,"X must not overlap tabs");checks++;
        }
        System.out.println("Header geometry checks passed ("+checks+" assertions)");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
