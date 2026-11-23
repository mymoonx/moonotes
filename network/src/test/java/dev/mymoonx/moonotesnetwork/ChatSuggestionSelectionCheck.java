package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;

/** Uses real 26.3 KeyEvent/SDL key constants, including the text event following Space. */
public final class ChatSuggestionSelectionCheck {
    private static int checks;
    public static void main(String[] args){
        var s=new ChatSuggestionSelection();
        check(s.key(key(InputConstants.KEY_SPACE),3)==ChatSuggestionSelection.Action.NONE,"ordinary space is never autoaccepted");
        check(s.key(key(InputConstants.KEY_RIGHT),3)==ChatSuggestionSelection.Action.NONE,"normal caret movement survives");
        check(s.key(key(InputConstants.KEY_TAB),3)==ChatSuggestionSelection.Action.MOVE&&s.index()==0,"Tab selects first, does not insert");
        check(s.key(key(InputConstants.KEY_TAB),3)==ChatSuggestionSelection.Action.MOVE&&s.index()==1,"Tab cycles");
        check(s.key(key(InputConstants.KEY_LEFT),3)==ChatSuggestionSelection.Action.MOVE&&s.index()==0,"left arrow cycles active selection");
        check(s.key(key(InputConstants.KEY_UP),3)==ChatSuggestionSelection.Action.MOVE&&s.index()==2,"up wraps backwards");
        check(s.key(new KeyEvent(InputConstants.KEY_TAB,0,InputConstants.MOD_SHIFT),3)==ChatSuggestionSelection.Action.MOVE&&s.index()==1,"Shift Tab reverses");
        check(s.key(key(InputConstants.KEY_SPACE),3)==ChatSuggestionSelection.Action.COMMIT,"Space explicitly commits selected emote");
        s.afterCommit();check(s.consumeCharacter(' '),"SDL text space following acceptance is swallowed once");
        check(!s.consumeCharacter(' '),"later ordinary space remains");
        s.key(key(InputConstants.KEY_TAB),2);s.key(key(InputConstants.KEY_SPACE),2);s.afterCommit();s.keyReleased(key(InputConstants.KEY_SPACE));
        check(!s.consumeCharacter(' '),"Space key-up expires suppression when no text event arrived");
        s.key(key(InputConstants.KEY_TAB),2);s.key(key(InputConstants.KEY_SPACE),2);s.afterCommit();s.key(key(InputConstants.KEY_SPACE),0);
        check(!s.consumeCharacter(' '),"a subsequent key-down expires suppression even with no previous key-up");
        s.key(key(InputConstants.KEY_DOWN),2);check(s.key(key(InputConstants.KEY_RETURN),2)==ChatSuggestionSelection.Action.NONE,"Enter remains normal chat submission when popup is closed");
        s.key(key(InputConstants.KEY_DOWN),2);check(s.key(key(InputConstants.KEY_ESCAPE),2)==ChatSuggestionSelection.Action.DISMISS,"Escape dismisses suggestions");
        check(s.index()<0,"Escape resets selected candidate");
        check(s.key(new KeyEvent(InputConstants.KEY_DOWN,0,InputConstants.MOD_CONTROL),2)==ChatSuggestionSelection.Action.NONE,"control shortcuts not captured");
        check(s.key(key(InputConstants.KEY_TAB),0)==ChatSuggestionSelection.Action.NONE,"empty list does not consume Tab");
        System.out.println("ChatSuggestionSelectionCheck: "+checks+" checks passed (real SDL keys, explicit acceptance, Space text event)");
    }
    private static KeyEvent key(int value){return new KeyEvent(value,0,0);}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
