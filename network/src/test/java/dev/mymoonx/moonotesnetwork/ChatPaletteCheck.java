package dev.mymoonx.moonotesnetwork;

import java.util.List;
import java.util.Set;
import java.util.Map;

public final class ChatPaletteCheck {
    public static void main(String[] args) {
        String id1="01f0hmz1zg0004v54c00a616cb",id2="01g4m2821r000dsyz0tdha3zkx";
        var first=new CatalogStore.Catalog("channel:other","other","channel","other","",true,0,"local",
            List.of(new CatalogStore.Entry(id1,"catJAM"),new CatalogStore.Entry(id2,"Sadge")),0,0,"");
        var second=new CatalogStore.Catalog("channel:v0kky","v0kky","channel","v0kky","",true,0,"local",
            List.of(new CatalogStore.Entry(id2,"catJAM"),new CatalogStore.Entry(id1,"Weirdge")),0,0,"");
        var all=ChatPalette.fromCatalogs(List.of(second,first),Set.of("v0kky","other"));
        check(all.size()==3,"deduplicated name");
        check(all.get(0).name().equals("catJAM")&&all.get(0).id().equals(id2),"active channel has priority");
        var matches=ChatPalette.find(all,"cat",5,k->false,k->0,"all");
        check(matches.size()==1&&matches.get(0).name().equals("catJAM"),"prefix match");
        check(ChatPalette.find(all,"Weird",5,k->false,k->0,"all").size()==1,"substring match");
        check(ChatPalette.find(all,"",5,k->k.endsWith("Weirdge"),k->0,"favorites").size()==1,"favorites filter");
        check(ChatPalette.find(all,"",5,k->false,k->k.endsWith("Sadge")?42:0,"recent").size()==1,"recent filter");
        check(ChatPalette.find(all,"cat",0,k->false,k->0,"all").isEmpty(),"zero limit");
        check(ChatPalette.find(all,"x".repeat(81),5,k->false,k->0,"all").isEmpty(),"bounded query");
        var grouped=ChatPalette.find(all,"",10,k->false,k->0,"group",k->k.endsWith("Weirdge"));
        check(grouped.size()==1 && grouped.getFirst().name().equals("Weirdge"),"group filters out nonmembers");
        check(ChatPalette.find(all,"",10,k->false,k->0,"group",k->false).isEmpty(),"empty group does not display all emotes");
        check(ChatPalette.fromCatalogs(List.of(first,second),Set.of("v0kky")).size()==2,"inactive channels absent from chat choices");
        long revision=ChatPalette.revision();ChatPalette.publish(all);check(ChatPalette.revision()>revision,"world-channel palette invalidates suggestions");
        System.out.println("ChatPaletteCheck: 12 cases passed");
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
