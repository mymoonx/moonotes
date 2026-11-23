package dev.mymoonx.moonotesnetwork;

/** Reported overlaps and unnecessary right-side gaps, across GUI scales. */
public final class ManagerLayoutCheck {
    public static void main(String[] args) {
        int checks=0;
        for(int[] size:new int[][]{{320,240},{427,240},{480,270},{640,360},{854,480},{1280,720},{1920,1080}}){
            var layout=new MoonotesLayout(size[0],size[1]);
            var minimize=layout.downloadMinimize();var progress=layout.downloadProgress();
            require(minimize.bottom()<progress.y(),"Minimize overlaps progress at "+size[0]);checks++;
            require(minimize.right()<=layout.panel.right()-12,"Minimize outside panel");checks++;
            var grid=layout.libraryGrid();
            require(grid.viewport().bottom()+3<=layout.librarySummaryY(),"Library hides range label");checks++;
            require(layout.librarySummaryY()+9<layout.footerY,"Range label overlaps buttons");checks++;
            require(grid.firstVisible(0,0)==0 && grid.lastVisible(0,0)==0,"Empty results range");checks++;
            require(grid.firstVisible(0,704)==1,"First range starts at 1");checks++;
            int last=grid.maxScroll(704);
            require(grid.lastVisible(last,704)==704,"Last range reaches final item");checks++;
            require(grid.firstVisible(last,704)<=704,"Last range non-empty");checks++;
            require(grid.lastVisible(0,3)==3,"Short result range bounded");checks++;
            var card=grid.card(0,0);var star=layout.libraryStar(card);
            require(!star.contains(card.right()-10,card.y()+9),"Name clicks must not toggle star");checks++;
            require(star.bottom()<=card.bottom() && star.y()>=card.y()+13,"Star stays in details row");checks++;
            for(int items:new int[]{2,3,4,5}){
                var settings=layout.settingsGrid(items);
                require(settings.viewport().bottom()<=layout.contentBottom,"Settings overflow content");checks++;
                require(settings.viewport().y()>=layout.settingsHeading().bottom(),"Heading overlaps fields");checks++;
                for(int section=0;section<4;section++){
                    var tab=layout.settingsTab(section);
                    require(tab.right()+8<=settings.viewport().x(),"Settings categories overlap fields");checks++;
                    require(tab.y()>=layout.contentTop && tab.bottom()<=layout.contentBottom,"Category outside content");checks++;
                    if(section>0){require(layout.settingsTab(section-1).bottom()<tab.y(),"Categories overlap");checks++;}
                }
                int max=settings.maxScroll(items);
                require(settings.lastVisible(max,items)==items,"Settings option unreachable");checks++;
                int usedRight=max==0?settings.viewport().right():settings.scrollbar().right();
                require(usedRight==layout.contentX+layout.contentWidth,"Unused scrollbar gap at right");checks++;
            }
        }
        System.out.println("ManagerLayoutCheck: "+checks+" assertions passed (7 GUI sizes; minimize, range, settings, reachability)");
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
