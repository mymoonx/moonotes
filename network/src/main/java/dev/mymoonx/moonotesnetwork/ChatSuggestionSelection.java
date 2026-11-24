package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;

/** Explicit selection keeps a normal typed space from accepting an unchosen suggestion. */
final class ChatSuggestionSelection {
    enum Action { NONE, MOVE, COMMIT, DISMISS }
    private int index=-1;
    private boolean suppressSpace;
    int index(){return index;}
    void reset(){index=-1;suppressSpace=false;}
    // The default never intercepts cursor arrows. Players can opt into the legacy
    // arrow navigation, but Tab / Shift+Tab always work without stealing text editing.
    Action key(KeyEvent event,int count){return key(event,count,false);}
    Action key(KeyEvent event,int count,boolean arrowNavigation){
        suppressSpace=false;
        if(count<=0){index=-1;return Action.NONE;}
        if(event.isEscape()){index=-1;return Action.DISMISS;}
        if(event.hasAltDown()||event.hasControlDown())return Action.NONE;
        int key=event.key();
        if(key==InputConstants.KEY_UP||key==InputConstants.KEY_DOWN
                ||key==InputConstants.KEY_LEFT||key==InputConstants.KEY_RIGHT){
            if(!arrowNavigation){
                // Let vanilla EditBox move its caret. Invalidate any old selection,
                // so the next ordinary Space cannot unexpectedly insert an emote.
                index=-1;
                return Action.NONE;
            }
        }
        if(event.hasShiftDown()&&key!=InputConstants.KEY_TAB){index=-1;return Action.NONE;}
        boolean previous=arrowNavigation&&key==InputConstants.KEY_UP
                ||key==InputConstants.KEY_TAB&&event.hasShiftDown()
                ||arrowNavigation&&index>=0&&key==InputConstants.KEY_LEFT;
        boolean next=arrowNavigation&&key==InputConstants.KEY_DOWN
                ||key==InputConstants.KEY_TAB&&!event.hasShiftDown()
                ||arrowNavigation&&index>=0&&key==InputConstants.KEY_RIGHT;
        if(previous||next){index=index<0?(previous?count-1:0):Math.floorMod(index+(previous?-1:1),count);return Action.MOVE;}
        if(key==InputConstants.KEY_SPACE&&index>=0){suppressSpace=true;return Action.COMMIT;}
        if(key!=InputConstants.KEY_LSHIFT&&key!=InputConstants.KEY_RSHIFT)index=-1;
        return Action.NONE;
    }
    boolean consumeCharacter(int codepoint){
        boolean consume=suppressSpace&&codepoint==' ';suppressSpace=false;return consume;
    }
    void keyReleased(KeyEvent event){if(event.key()==InputConstants.KEY_SPACE)suppressSpace=false;}
    void afterCommit(){index=-1;}
}
