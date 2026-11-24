package dev.mymoonx.moonotesnetwork;

public final class ChatOverlayGeometryCheck {
    private static int checks;
    public static void main(String[] args){
        for(int[] pair:new int[][]{{320,160},{320,240},{427,240},{500,300},{640,360},{854,480},{1280,720},{1920,1080}}){
            var g=ChatOverlayGeometry.forScreen(pair[0],pair[1]);
            check(g.x()>=0&&g.x()+g.width()<=pair[0],"horizontal bounds");
            check(g.y()>=0&&g.y()+g.height()<=pair[1]-44,"popup stays above chat buttons and input");
            check(g.rows()>=1,"at least one fully visible row at minimum supported GUI size");
            check(g.y()+g.bodyTop()+g.rows()*ChatOverlayGeometry.ROW<=g.footerY(),"footer never overlaps rows");
            check(g.input().y()+g.input().height()<=g.row(0).y(),"search field never covers first row");
            var star=ChatOverlayGeometry.favoriteAction(g.row(0));var more=ChatOverlayGeometry.moreAction(g.row(0));
            check(star.x()+star.width()+4<=more.x(),"favorite and groups have a visible inert gap");
            check(star.y()>g.row(0).y()&&star.y()+star.height()<g.row(0).y()+g.row(0).height(),"action hover stays inside row");
            var form=ChatOverlayGeometry.forScreen(pair[0],pair[1],true);
            check(form.height()<=126&&form.rows()==0,"group form is a compact distinct view");
            check(form.inputLabelY()+9<form.input().y(),"group name has its own label");
            check(form.input().y()+form.input().height()+8<form.footerY(),"group name and form buttons cannot overlap");
            check(form.footer(0,3).x()+form.footer(0,3).width()<form.footer(1,3).x(),"form actions have separate hitboxes");
            var compact=ChatOverlayGeometry.forScreen(pair[0],pair[1],false,1);
            check(compact.rows()==1,"a single emote/group uses a compact one-row popup");
            check(compact.height()<=g.height(),"short result lists never leave unused empty space");
            check(compact.footer(0,1).y()+compact.footer(0,1).height()
                <=compact.y()+compact.height()-8,"footer has at least 8px bottom padding");
            check(compact.row(0,false).width()>compact.row(0,true).width(),
                "scrollbar space is not reserved when no scrolling is needed");
            var picker=ChatOverlayGeometry.pickerButton(pair[0],pair[1]);var manager=ChatOverlayGeometry.managerButton(pair[0],pair[1]);
            check(picker.x()+picker.width()<manager.x(),"chat buttons have separate hit boxes");
            check(manager.y()+manager.height()<pair[1]-14,"buttons never cover chat input");
            var strip=ChatOverlayGeometry.suggestions(pair[0],pair[1],5,pair[0]);
            check(strip.count()>0&&strip.count()<=5,"short bounded suggestion row");
            check(strip.cell(strip.count()-1).x()+26<picker.x(),"suggestions do not cover chat buttons");
            for(int i=0;i<strip.count();i++){
                var cell=strip.cell(i);check(strip.hit(cell.x()+10,cell.y()+10)==i,"drawn cell equals hit target");
                check(strip.hit(cell.x()+cell.width(),cell.y()+10)==-1,"gap cannot select neighbouring emote");
            }
            check(strip.hit(strip.x()-1,strip.y()+10)==-1,"left padding cannot select first emote");
            check(strip.hit(strip.x()+5,strip.y()-1)==-1,"name label is not an insertion target");
        }
        System.out.println("ChatOverlayGeometryCheck: "+checks+" checks passed (minimum GUI, shared hit geometry, input clearance)");
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
