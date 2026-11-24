package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import java.nio.file.Files;
import java.util.List;

/** Exercises the production click/key state machine without booting Minecraft or sending chat. */
public final class ChatPickerGroupFlowCheck {
    private static final int W=640,H=360;
    private static int checks;
    public static void main(String[] args)throws Exception{
        var path=Files.createTempDirectory("moonotes-chat-groups-").resolve("library.json");
        var prefs=new LibraryPreferences(path);
        String a="0123456789abcdef:Alpha",b="0123456789abcdee:Bravo";
        ChatPalette.publish(List.of(new ChatPalette.Emote(a,"0123456789abcdef","Alpha","test"),new ChatPalette.Emote(b,"0123456789abcdee","Bravo","test")));
        var picker=new ChatEmotePicker(prefs,()->true,()->true);picker.resize(W,H);
        check(click(picker,ChatOverlayGeometry.pickerButton(W,H))==ChatEmotePicker.Click.OPEN,"text launcher opens picker");
        check(click(picker,picker.geometry().tab(3))==ChatEmotePicker.Click.EDIT_GROUP,"groups tab opens group directory");
        picker.searchChanged("old filter");
        click(picker,picker.geometry().footer(0,1));
        check(picker.nameFieldVisible()&&!picker.searchVisible(),"creation shows only distinct group-name field");
        check(picker.search().equals("old filter")&&picker.groupName().isEmpty(),"search text is never reused as group name");
        picker.groupNameChanged("Коты");enter(picker);
        check(!picker.namingGroup()&&prefs.groupNames().equals(List.of("Коты")),"Create saves group and opens member selection");
        click(picker,picker.geometry().row(0));
        check(prefs.inGroup("Коты",a)&&picker.isOpen(),"member selection adds item without inserting or closing chat");
        click(picker,picker.geometry().row(0));check(!prefs.inGroup("Коты",a),"selection can remove item");
        picker.key(key(InputConstants.KEY_DOWN),null,false);enter(picker);
        picker.key(key(InputConstants.KEY_DOWN),null,false);enter(picker);
        check(prefs.inGroup("Коты",a)&&prefs.inGroup("Коты",b),"keyboard can toggle consecutive members without losing selection");
        click(picker,picker.geometry().footer(1,2));
        picker.searchChanged("Al");
        click(picker,picker.geometry().footer(2,3));
        check(picker.groupName().equals("Коты")&&picker.search().equals("Al"),"rename has independent prefilled name, preserving search");
        picker.groupNameChanged("wrong name");
        picker.key(key(InputConstants.KEY_ESCAPE),null,false);
        check(!picker.namingGroup()&&picker.search().equals("Al")&&prefs.groupNames().contains("Коты"),"Escape cancels rename without changing search or group");
        click(picker,picker.geometry().footer(2,3));picker.groupNameChanged("Котики");enter(picker);
        check(prefs.inGroup("Котики",a)&&prefs.inGroup("Котики",b)&&picker.search().equals("Al"),"rename preserves members and filter");
        picker.searchChanged("");
        click(picker,ChatOverlayGeometry.favoriteAction(picker.geometry().row(0)));
        check(prefs.favorite(a)&&prefs.groupSize("Котики")==2&&picker.isOpen(),"favorite action is independent from group and chat insertion");
        var star=ChatOverlayGeometry.favoriteAction(picker.geometry().row(0));
        check(picker.click(star.x()+star.width()+2,star.y()+3,InputConstants.MOUSE_BUTTON_LEFT,null,false)==ChatEmotePicker.Click.CONSUMED&&picker.isOpen(),"gap between row actions cannot insert an emote");
        prefs.createGroup("Смешное");
        click(picker,ChatOverlayGeometry.moreAction(picker.geometry().row(0)));
        picker.searchChanged("См");click(picker,picker.geometry().row(0));
        check(prefs.inGroup("Смешное",a),"more action opens explicit group membership list");
        click(picker,picker.geometry().footer(0,2));picker.groupNameChanged("Котики");enter(picker);
        check(picker.namingGroup()&&prefs.groupNames().size()==2,"duplicate name stays in form without changing groups");
        click(picker,picker.geometry().footer(1,2));
        check(picker.search().equals("См"),"Cancel new group restores membership filter");
        click(picker,picker.geometry().footer(1,2));
        check(picker.search().isEmpty(),"Done restores original emote filter");
        click(picker,picker.geometry().footer(0,3));
        click(picker,ChatOverlayGeometry.moreAction(picker.geometry().row(0)));
        check(picker.namingGroup(),"group row has a direct edit action");
        click(picker,picker.geometry().footer(1,3));
        check(!picker.fieldVisible(),"delete confirmation has no misleading input field");
        click(picker,picker.geometry().footer(0,2));
        check(!prefs.groupNames().contains("Котики")&&prefs.favorite(a)&&ChatPalette.snapshot().size()==2,"delete removes group only, preserving emotes and favorites");
        check(click(picker,ChatOverlayGeometry.managerButton(W,H))==ChatEmotePicker.Click.MANAGER&&!picker.isOpen(),"Moonotes text button opens manager after closing popup");
        prefs.saveNow();check(new LibraryPreferences(path).inGroup("Смешное",a),"membership persists across restart");
        System.out.println("ChatPickerGroupFlowCheck: "+checks+" checks passed (production SDL clicks/keys, distinct input modes, groups, inert gaps, persistence)");
    }
    private static ChatEmotePicker.Click click(ChatEmotePicker p,ChatOverlayGeometry.Rect r){return p.click(r.x()+r.width()/2.0,r.y()+r.height()/2.0,InputConstants.MOUSE_BUTTON_LEFT,null,false);}
    private static KeyEvent key(int code){return new KeyEvent(code,0,0);}
    private static void enter(ChatEmotePicker p){check(p.key(key(InputConstants.KEY_RETURN),null,true),"Enter is consumed inside picker; never reaches chat send");}
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
}
