package dev.mymoonx.moonotesnetwork;

public final class ChatTextEditCheck {
    public static void main(String[] args) {
        check("ca".equals(ChatTextEdit.activePrefix("hello ca",8)),"current token");
        check("ca".equals(ChatTextEdit.activePrefix("hello :ca",9)),"colon prefix");
        check(ChatTextEdit.activePrefix("/tp ca",6).isEmpty(),"commands untouched");
        check(ChatTextEdit.activePrefix("a",1).isEmpty(),"minimum prefix");
        var inserted=ChatTextEdit.insert("всем ca привет",7,"catJAM",true,false);
        check(inserted.text().equals("всем catJAM привет"),"replace middle token preserving suffix");
        var colon=ChatTextEdit.insert("Привет :ca",10,"catJAM",true,true);
        check(colon.text().equals("Привет :catJAM: "),"required Streamotes colons");
        check(ChatTextEdit.insert("Привет",6,"Weirdge",false,false).text().equals("Привет Weirdge "),"append no send");
        check(ChatTextEdit.insert("Hi there",2,"catJAM",false,false).text().equals("Hi catJAM there"),"preserve cursor insert");
        reject(()->ChatTextEdit.insert("/say test",9,"catJAM",true,false));
        reject(()->ChatTextEdit.insert("",0,"bad\nname",false,false));
        reject(()->ChatTextEdit.insert("x".repeat(256),256,"catJAM",false,false));
        check(inserted.cursor()=="всем catJAM ".length(),"accepted emote leaves caret after existing separator");
        check(ChatTextEdit.activePrefix(" /tp ca",7).isEmpty(),"commands after leading whitespace untouched");
        reject(()->ChatTextEdit.insert("",0,"/say",false,false));
        System.out.println("ChatTextEditCheck: 14 cases passed");
    }
    private static void check(boolean v,String message) {if(!v)throw new AssertionError(message);}
    private static void reject(Runnable callback) {try {callback.run(); throw new AssertionError("unsafe text accepted");}
        catch (IllegalArgumentException expected) { }}
}
