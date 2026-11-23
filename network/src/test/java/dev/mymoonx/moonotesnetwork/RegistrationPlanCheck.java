package dev.mymoonx.moonotesnetwork;

import java.util.List;
import java.util.Set;

/** Offline regression checks for catalogue aliases, per-world scope and provider collision decisions. */
public final class RegistrationPlanCheck {
    private static RegistrationPlan.Item item(String catalog, String channel, String id, String name, boolean enabled) {
        return new RegistrationPlan.Item(catalog, channel, id, name, false, enabled);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var a=item("channel:v0kky","v0kky","abc","Hello",true);
        var b=item("channel:other","other","def","World",true);
        var imported=item("import:local","","ghi","Imported",true);
        var disabled=item("set:off","","jkl","Hidden",false);
        var plan=RegistrationPlan.create(List.of(a,b,imported,disabled),Set.of("v0kky"));
        check(plan.names().keySet().equals(Set.of("Hello","Imported")),"Only current-world channel and enabled imports belong in chat");
        check(RegistrationPlan.create(List.of(a,b,imported),Set.of()).names().keySet().equals(Set.of("Imported")),
                "Disabling last channel must not silently restore v0kky");
        var alias=item("channel:v0kky","v0kky","abc","HelloAgain",true);
        check(RegistrationPlan.create(List.of(a,alias),Set.of("v0kky")).names().size()==2,
                "One image can keep several text aliases");
        var duplicate=item("set:dupe","","ABC","Hello",true);
        var same=RegistrationPlan.create(List.of(a,duplicate),Set.of("v0kky"));
        check(same.names().size()==1 && same.duplicateNames()==0,"Same image/name shared across catalogues is not a conflict");
        var conflict=item("set:conflict","","other-image","Hello",true);
        var collided=RegistrationPlan.create(List.of(a,conflict),Set.of("v0kky"));
        check(collided.names().get("Hello").id().equals("abc") && collided.duplicateNames()==1,
                "First enabled catalogue deterministically keeps a colliding name");
        check(RegistrationPlan.sameImage("ABC","abc"),"Case-normalized IDs must match existing Streamotes registration");
        check(!RegistrationPlan.sameImage("different","abc") && !RegistrationPlan.sameImage(new Object(),"abc"),
                "Other providers' names must never be assumed to refer to our image");
        try { plan.names().clear(); throw new AssertionError("Mutable plan"); }
        catch(UnsupportedOperationException expected) { }
        // Actual Streamotes Emoticon.equals also considers distinct emotes with one name equal.
        record Emote(String name) { }
        var old=new Emote("Hello");var replacement=new Emote("Hello");
        var nativeRegistry=new java.util.HashMap<String,Emote>();nativeRegistry.put("Hello",replacement);
        check(!RegistrationPlan.removeIdentity(nativeRegistry,"Hello",old) && nativeRegistry.get("Hello")==replacement,
                "Retiring an equal old object must preserve the newer native provider");
        check(RegistrationPlan.removeIdentity(nativeRegistry,"Hello",replacement) && nativeRegistry.isEmpty(),
                "Only the exact owned instance can be removed");
        System.out.println("RegistrationPlanCheck: 10 checks passed");
    }
}
