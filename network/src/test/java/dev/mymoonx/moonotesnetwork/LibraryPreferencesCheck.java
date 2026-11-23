package dev.mymoonx.moonotesnetwork;

import java.nio.file.Files;

public final class LibraryPreferencesCheck {
    public static void main(String[] args)throws Exception {
        var dir=Files.createTempDirectory("moonotes-preferences-check-");var path=dir.resolve("library.json");
        String key="0123456789abcdef:Test";
        LibraryPreferences prefs=new LibraryPreferences(path);
        require(prefs.toggle(key),"favorite enabled");prefs.used(key);prefs.saveNow();
        LibraryPreferences restored=new LibraryPreferences(path);
        require(restored.favorite(key)&&restored.lastUsed(key)>0,"persistent favorite/recent");
        restored.used(key+"2");require(restored.lastUsed(key+"2")>restored.lastUsed(key),"recent ordering");
        require(!restored.toggle(key),"favorite removed");
        require(!LibraryPreferences.validKey("../../x:Name"),"path-like identifier rejected");
        require(!LibraryPreferences.validKey(key+"\u202e") && !LibraryPreferences.validKey(key+"\n") && !LibraryPreferences.validKey(key+"§"),"unsafe name controls");
        for(int i=0;i<110;i++)restored.used(key+i);
        restored.saveNow();LibraryPreferences capped=new LibraryPreferences(path);
        require(capped.lastUsed(key+"0")==0 && capped.lastUsed(key+"109")>0,"recent limited to 100");
        // Use a separate file so asynchronous persistence of the first store cannot race this input.
        var malicious=dir.resolve("malicious.json");
        Files.writeString(malicious,"{\"recent\":{\""+key+"\":"+Long.MAX_VALUE+"}}");
        LibraryPreferences safe=new LibraryPreferences(malicious);safe.used(key+"next");
        require(safe.lastUsed(key+"next")>safe.lastUsed(key),"saved recency cannot overflow clock");
        var deep=dir.resolve("deep.json");Files.writeString(deep,"[".repeat(100)+"0"+"]".repeat(100));
        require(!new LibraryPreferences(deep).warning().isEmpty(),"deep JSON safely rejected");
        var gFile=dir.resolve("groups.json");var grouped=new LibraryPreferences(gFile);
        require(grouped.createGroup("Коты").isEmpty(),"unicode group name");
        require(!grouped.createGroup("коты").isEmpty(),"case-insensitive duplicate group");
        require(grouped.toggleGroup("Коты",key),"add group member");
        require(grouped.inGroup("Коты",key),"group membership");
        grouped.saveNow();var reloaded=new LibraryPreferences(gFile);
        require(reloaded.inGroup("Коты",key),"group persistence");
        long revision=reloaded.revision();
        require(reloaded.renameGroup("Коты","Котики").isEmpty()&&reloaded.inGroup("Котики",key),"rename preserves members");
        require(reloaded.revision()>revision,"preference changes invalidate cached views");
        reloaded.saveNow();require(new LibraryPreferences(gFile).inGroup("Котики",key),"rename persists");
        require(reloaded.removeGroup("Котики") && !reloaded.inGroup("Коты",key),"remove group preserves emote");
        require(!LibraryPreferences.validGroupName("../secret") && !LibraryPreferences.validGroupName("  bad "),"group input validation");
        System.out.println("LibraryPreferencesCheck: 19 cases passed (persistence, groups, recency, bounds, privacy controls)");
    }
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
}
