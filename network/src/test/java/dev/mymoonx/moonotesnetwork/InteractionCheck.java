package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Regression for the actual SDL mouse event, plus drag capture and layout hit-testing. */
public final class InteractionCheck {
    public static void main(String[] args) {
        require(MoonotesInput.isPrimary(new MouseButtonEvent(100, 200, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0))), "Minecraft left-click ignored");
        require(!MoonotesInput.isPrimary(new MouseButtonEvent(100, 200, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, 0))), "Right-click mistaken for primary");
        require(!MoonotesInput.isPrimary(new MouseButtonEvent(100, 200, new MouseButtonInfo(0, 0))), "Obsolete GLFW zero accepted");
        ScrollState scroll = new ScrollState();
        scroll.configure(100, 200, 5, 230);
        scroll.press(104);
        require(scroll.row == 0 && scroll.dragging(), "Thumb must not jump on grip");
        scroll.drag(304);
        require(scroll.row == 230, "Dragging to end must expose final row");
        scroll.drag(-100);
        require(scroll.row == 0, "Dragging outside track must clamp");
        scroll.release();
        require(!scroll.drag(220) && scroll.row == 0, "Released thumb must stop following pointer");
        scroll.press(200);
        require(scroll.row > 0, "Track click must seek");
        scroll.configure(100, 200, 5, 0);
        require(scroll.row == 0 && !scroll.dragging(), "Filtering must clamp and release empty scrollbar");
        for (int[] size : new int[][]{{320,240},{427,240},{640,360},{854,480},{1280,720},{1920,1080}}) {
            MoonotesLayout layout = new MoonotesLayout(size[0], size[1]);
            MoonotesLayout.Grid grid = layout.libraryGrid();
            require(grid.columns()>=2 && grid.rows()>=2, "Minimum GUI density regressed " + size[0]);
            require(grid.viewport().bottom() <= layout.contentBottom, "Grid overlaps footer " + size[0]);
            require(grid.scrollbar().right() <= layout.panel.right(), "Scrollbar outside panel");
            for (int row = 0; row < grid.rows(); row++) for (int col = 0; col < grid.columns(); col++) {
                MoonotesLayout.Rect card = grid.card(col,row);
                int expected = row * grid.columns() + col;
                require(grid.positionAt(card.x()+1,card.y()+1,0,704)==expected, "Card drawing and input differ");
                require(grid.positionAt(card.x()+1,card.bottom(),0,704)==-1, "Card gap clickable");
            }
            for(int i=0;i<4;i++) {
                var footer=layout.footer(i,4);
                require(footer.right()<=layout.panel.right() && footer.bottom()<=layout.panel.bottom(), "Footer outside panel");
                require(footer.width()==layout.footer(0,4).width(), "Unequal footer buttons");
            }
            int max=grid.maxScroll(704);
            var last=grid.card((703 % grid.columns()), (703 / grid.columns())-max);
            require(grid.positionAt(last.x()+1,last.y()+1,max,704)==703, "Last emote unreachable");
        }
        System.out.println("InteractionCheck: passed actual MouseButtonEvent, drag lifecycle, card hit-tests and 6 GUI sizes");
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}

