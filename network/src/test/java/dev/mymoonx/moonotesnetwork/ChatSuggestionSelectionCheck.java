package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;

/** Uses Minecraft 26.3 SDL keys to protect native chat caret and history. */
public final class ChatSuggestionSelectionCheck {
    private static int checks;
    public static void main(String[] args){
        var s=new ChatSuggestionSelection();
        check(s.key(key(InputConstants.KEY_SPACE),3)==ChatSuggestionSelection.Action.NONE,
            "ordinary space never autoaccepts");
        for(int arrow:new int[]{InputConstants.KEY_UP,InputConstants.KEY_DOWN,
                InputConstants.KEY_LEFT,InputConstants.KEY_RIGHT}){
            check(s.key(key(arrow),3)==ChatSuggestionSelection.Action.NONE,
                "default must leave all cursor arrows to Minecraft");
            check(s.index()==-1,"arrow navigation is disabled by default");
        }
        check(s.key(key(InputConstants.KEY_TAB),3)==ChatSuggestionSelection.Action.MOVE&&s.index()==0,
            "Tab selects first");
        check(s.key(key(InputConstants.KEY_TAB),3)==ChatSuggestionSelection.Action.MOVE&&s.index()==1,
            "Tab cycles");
        check(s.key(key(InputConstants.KEY_LEFT),3)==ChatSuggestionSelection.Action.NONE&&s.index()==-1,
            "caret movement also clears prior Tab selection");
        check(s.key(key(InputConstants.KEY_SPACE),3)==ChatSuggestionSelection.Action.NONE,
            "space after caret movement is ordinary space");
        check(s.key(key(InputConstants.KEY_TAB),3)==ChatSuggestionSelection.Action.MOVE&&s.index()==0,
            "Tab works after cursor editing");
        check(s.key(new KeyEvent(InputConstants.KEY_TAB,0,InputConstants.MOD_SHIFT),3)
                ==ChatSuggestionSelection.Action.MOVE&&s.index()==2,"Shift Tab reverses");
        check(s.key(key(InputConstants.KEY_SPACE),3)==ChatSuggestionSelection.Action.COMMIT,
            "Space explicitly commits selected emote");
        s.afterCommit();check(s.consumeCharacter(' '),"SDL text space after acceptance swallowed once");
        check(!s.consumeCharacter(' '),"ordinary subsequent space remains");
        s.key(key(InputConstants.KEY_TAB),2);s.key(key(InputConstants.KEY_SPACE),2);
        s.afterCommit();s.keyReleased(key(InputConstants.KEY_SPACE));
        check(!s.consumeCharacter(' '),"key-up ends suppression");
        s.key(key(InputConstants.KEY_TAB),2);s.key(key(InputConstants.KEY_SPACE),2);
        s.afterCommit();s.key(key(InputConstants.KEY_SPACE),0);
        check(!s.consumeCharacter(' '),"a subsequent key-down ends suppression");

        check(s.key(key(InputConstants.KEY_DOWN),2)==ChatSuggestionSelection.Action.NONE,
            "Down passes to normal chat history by default");
        check(s.key(key(InputConstants.KEY_RETURN),2)==ChatSuggestionSelection.Action.NONE,
            "Enter remains normal chat submission");
        s.key(key(InputConstants.KEY_TAB),2);
        check(s.key(key(InputConstants.KEY_ESCAPE),2)==ChatSuggestionSelection.Action.DISMISS,
            "Escape dismisses");
        check(s.index()<0,"Escape clears selected candidate");
        check(s.key(new KeyEvent(InputConstants.KEY_DOWN,0,InputConstants.MOD_CONTROL),2)
                ==ChatSuggestionSelection.Action.NONE,"control shortcuts not captured");
        check(s.key(key(InputConstants.KEY_TAB),0)==ChatSuggestionSelection.Action.NONE,
            "empty suggestion list does not consume Tab");

        // Old arrow navigation is only restored when the user explicitly enables it.
        check(s.key(key(InputConstants.KEY_DOWN),3,true)==ChatSuggestionSelection.Action.MOVE
                &&s.index()==0,"opt-in Down selects first");
        check(s.key(key(InputConstants.KEY_RIGHT),3,true)==ChatSuggestionSelection.Action.MOVE
                &&s.index()==1,"opt-in Right moves active selection");
        check(s.key(key(InputConstants.KEY_UP),3,true)==ChatSuggestionSelection.Action.MOVE
                &&s.index()==0,"opt-in Up moves backwards");
        check(s.key(key(InputConstants.KEY_LEFT),3,true)==ChatSuggestionSelection.Action.MOVE
                &&s.index()==2,"opt-in Left wraps");
        check(s.key(key(InputConstants.KEY_LEFT),3)==ChatSuggestionSelection.Action.NONE&&s.index()==-1,
                "disabling arrows takes effect immediately");
        System.out.println("ChatSuggestionSelectionCheck: "+checks+" checks passed");
    }
    private static KeyEvent key(int value){return new KeyEvent(value,0,0);}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
