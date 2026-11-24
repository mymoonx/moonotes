package dev.mymoonx.moonotesnetwork;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Regression: migration, independent pages, server-scoped prefixes and privacy-safe backup. */
public final class PlayerExperienceCheck {
    private static final String ID="01F5Z4B9700008KDXA2TJQBNGW";
    private static void require(boolean yes,String message){if(!yes)throw new AssertionError(message);}

    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("moonotes-experience-");
        Path config=dir.resolve("moonotes-wheel.json");
        Files.writeString(config,"{\"channel\":\"GLOBAL\",\"slots\":[\""+ID+":42\"]}");
        WheelSettings pages=new WheelSettings(config);
        require(pages.pageCount()==1 && pages.slots().getFirst().equals(ID+":42"),
            "legacy wheel slots migrate");
        require(pages.channel()==WheelSettings.Channel.GLOBAL,"legacy chat mode migrates");
        require(pages.addPage("Коты"),"user page created");
        require(pages.pageCount()==2 && pages.page()==1,"new page selected");
        require(pages.renamePage("Котики"),"page can be renamed");
        require(pages.saveSlots(WheelSettings.assign(pages.slots(),0,ID+":67")),"page slots persist");
        require(pages.setPrefix("#"),"custom prefix accepted");
        require(ChatInsertion.forChannel("67",WheelSettings.Channel.GLOBAL).equals("#67"),
            "custom global chat works");
        String server=ServerCatalogProfiles.worldKey("play.example");
        pages.setWorld(server);
        require(pages.prefix().equals("!"),"other server inherits safe default");
        require(pages.setPrefix("@"),"server prefix set");
        require(ChatInsertion.forChannel(":42:",WheelSettings.Channel.GLOBAL).equals("@:42:"),
            "chat activation preserved");
        pages.setWorld(ServerCatalogProfiles.worldKey("singleplayer"));
        require(pages.prefix().equals("#"),"first profile recalled");
        require(!WheelSettings.validPrefix("/g")&&!WheelSettings.validPrefix(" ")
            &&!WheelSettings.validPrefix("!!!!!"),"unsafe prefixes rejected");
        WheelSettings restored=new WheelSettings(config);
        require(restored.pageCount()==2,"page collection survives reload");
        restored.setWorld(server);
        require(restored.prefix().equals("@"),"server setting persisted");
        require(restored.pageTitle().equals("Основные"),"per-world active page starts at first");
        require(restored.removePage(),"can remove page");
        require(restored.pageCount()==1,"one page remains");
        require(!restored.removePage(),"cannot remove last page");

        Path supportPath=dir.resolve("moonotes-support.json");
        SupportPreferences support=new SupportPreferences(supportPath);
        require(!support.telemetryOptIn(),"telemetry is off by default");
        support.toggleTelemetry();
        support.cyclePerformance();
        SupportPreferences supportRestored=new SupportPreferences(supportPath);
        require(supportRestored.telemetryOptIn(),"explicit telemetry choice stored");
        require(supportRestored.performance()==SupportPreferences.Performance.HIGH,
            "quality setting persists");
        ChatButtonPlacement placement=new ChatButtonPlacement(dir.resolve("moonotes-chat-layout.json"));
        ChatButtonPlacement.Data moved=placement.draft();
        ChatButtonPlacement.place(moved,ChatButtonPlacement.PICKER,960,540,150,300);
        require(placement.save(moved),"button placement saved");
        require(ChatButtonPlacement.picker(960,540).x()==150,"dragged button hitbox");
        Path backup=LocalBackup.backup(dir);
        require(Files.isRegularFile(backup),"manual backup created");
        Files.writeString(config,"{}");
        require(LocalBackup.restore(dir,backup)>=2,"restore settings from bounded archive");
        require(Files.readString(config).contains("pages"),"restored original page data");
        require(LocalBackup.newest(dir)!=null,"backup listing");
        System.out.println("PlayerExperienceCheck: PASS");
    }
}
