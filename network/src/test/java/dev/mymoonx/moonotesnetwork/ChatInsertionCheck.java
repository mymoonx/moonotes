package dev.mymoonx.moonotesnetwork;

public final class ChatInsertionCheck {
    public static void main(String[] args){
        require(ChatInsertion.append("","Kappa").equals("Kappa "),"empty draft");
        require(ChatInsertion.append("Привет","Kappa").equals("Привет Kappa "),"draft preserved with separator");
        require(ChatInsertion.append("Привет ","Kappa").equals("Привет Kappa "),"separator not duplicated");
        require(ChatInsertion.appendToken("Привет",":Kappa:").equals("Привет :Kappa: "),"required activation format");
        require(ChatInsertion.appendToken("",":"+"a".repeat(80)+":").length()==83,"full maximum name plus format");
        reject(()->ChatInsertion.append("a".repeat(252),"Kappa"),"length cannot truncate emote/draft");
        for(String bad:new String[]{"/command","bad\nname","bad name","bad\u202ename","§kbad"})
            reject(()->ChatInsertion.append("",bad),"unsafe emote token");
        System.out.println("ChatInsertionCheck: 11 cases passed (draft preservation, activation format, full token, command/control rejection)");
    }
    private static void reject(Runnable action,String label){try{action.run();throw new AssertionError(label);}catch(IllegalArgumentException expected){}}
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
}
