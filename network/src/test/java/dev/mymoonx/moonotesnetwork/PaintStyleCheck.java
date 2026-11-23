package dev.mymoonx.moonotesnetwork;

import java.nio.file.Files;
import java.nio.file.Path;

/** Offline regression checks: no live Twitch or Chatterino+ requests. */
public final class PaintStyleCheck {
    public static void main(String[] args) throws Exception {
        PaintStyle gradient = PaintStyle.parse("""
            {"paint":{"name":"Test paint","gradients":[
              {"function":"LINEAR_GRADIENT","stops":[
                {"at":0,"color":"#FF0000"},
                {"at":0.5,"color":"#00FF00"},
                {"at":1,"color":"#0000FF"}]}]}}
            """);
        require(gradient.stops.size()==3,"stops");
        require((gradient.sample(0) & 0xFFFFFF)==0xFF0000,"start");
        require((gradient.sample(0.5) & 0xFFFFFF)==0x00FF00,"mid");
        require((gradient.sample(1) & 0xFFFFFF)==0x0000FF,"end");
        require((gradient.sample(0.25) & 0xFFFFFF)==0x808000,"interpolation");
        require(gradient.colored("Moonotes", false, 0).getString().equals("Moonotes"),"text preserved");
        expectError(() -> PaintStyle.parse("[]"));
        expectError(() -> PaintStyle.parse("""
            {"gradients":[{"function":"URL","image_url":"https://example.org/paint.webp"}]}
            """));
        expectError(() -> PaintStyle.parse("{\\"stops\\":[]}"));
        expectError(() -> PaintStyle.parse("x".repeat(65537)));
        Path dir=Files.createTempDirectory("moonotes-paint-test-");
        PaintSettings setting=new PaintSettings(dir.resolve("settings.json"),dir.resolve("paint.json"));
        require(!setting.enabled(),"disabled by default");
        setting.toggleEnabled();
        setting.toggleDemo();
        require(setting.paint()!=null,"explicit demo");
        require(setting.tab(),"tab enabled");
        setting.toggleTab();
        PaintSettings restored=new PaintSettings(dir.resolve("settings.json"),dir.resolve("paint.json"));
        require(restored.enabled() && restored.demo() && !restored.tab(),"persist options");
        restored.toggleDemo();
        require(restored.paint()==null,"no automatic assignment");
        Files.writeString(dir.resolve("paint.json"),"""
            {"name":"File paint","color":"#54C2FE"}
            """);
        restored.reload();
        require(restored.paint()!=null && "File paint".equals(restored.paint().name),"manual paint data");
        System.out.println("PaintStyleCheck: PASS");
    }
    @FunctionalInterface private interface Checked {void run() throws Exception;}
    private static void require(boolean pass,String description){
        if(!pass)throw new AssertionError(description);
    }
    private static void expectError(Checked body)throws Exception{
        try {body.run();} catch(IllegalArgumentException expected){return;}
        throw new AssertionError("Invalid paint data accepted");
    }
}
