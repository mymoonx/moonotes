package dev.mymoonx.moonotesnetwork;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.Properties;
import java.io.InputStream;
import java.util.Arrays;

/** Offline update policy regression, source validation and reversible file swaps. */
public final class UpdateSystemCheck {
    private static void check(boolean cond,String detail){
        if(!cond)throw new AssertionError(detail);
    }
    public static void main(String[] args)throws Exception{
        String manifest="""
            [
              {"tag_name":"v0.6.0-alpha.4","draft":false,"prerelease":true,
               "assets":[{"name":"moonotes-network-0.6.0-alpha.4.jar"},{"name":"SHA256SUMS.txt"}]},
              {"tag_name":"v0.6.0-beta.5","draft":true,"prerelease":true,
               "assets":[{"name":"moonotes-network-0.6.0-beta.5.jar"},{"name":"SHA256SUMS.txt"}]},
              {"tag_name":"v0.6.0-beta.4","draft":false,"prerelease":true,
               "assets":[{"name":"moonotes-network-0.6.0-beta.4.jar"},{"name":"SHA256SUMS.txt"}]},
              {"tag_name":"v0.6.0","draft":false,"prerelease":false,
               "assets":[{"name":"moonotes-network-0.6.0.jar"},{"name":"SHA256SUMS.txt"}]},
              {"tag_name":"v0.7.0","draft":false,"prerelease":false,
               "assets":[{"name":"moonotes-network-0.7.0.jar"}]}
            ]""";
        var releases=JsonParser.parseString(manifest).getAsJsonArray();
        var stable=MoonotesUpdateCheck.choose(releases,"0.6.0-beta.3",
            SupportPreferences.UpdateChannel.STABLE);
        var beta=MoonotesUpdateCheck.choose(releases,"0.6.0-beta.3",
            SupportPreferences.UpdateChannel.BETA);
        check(stable!=null && stable.version().equals("0.6.0"),
            "Stable channel ignores drafts, betas and unsigned assets");
        check(beta!=null && beta.version().equals("0.6.0"),
            "All-channel takes newest version, never lower beta");
        check(MoonotesUpdateCheck.compare("0.6.0-beta.4","0.6.0-alpha.4")>0,
            "beta succeeds alpha");
        check(MoonotesUpdateCheck.compare("0.6.0","0.6.0-rc.9")>0,
            "stable succeeds release candidate");
        check(MoonotesUpdateCheck.compare("0.5.3","0.6.0-beta.3")<0,
            "older stable is not offered as downgrade");
        check(MoonotesUpdateCheck.compare("0.6.0-beta.4","0.6.0-beta.4")==0,
            "same version not offered twice");
        check(MoonotesUpdateCheck.choose(releases,"0.7.0",
            SupportPreferences.UpdateChannel.BETA)==null,
            "unsigned or older updates are not offered");
        String hash="a".repeat(64);
        String lines=hash+"  moonotes-network-0.6.0-beta.4.jar\n"+
            "b".repeat(64)+"  other.jar\n";
        check(MoonotesUpdateCheck.expectedHash(lines.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "moonotes-network-0.6.0-beta.4.jar").equals(hash),
            "only exact JAR checksum accepted");
        try{
            MoonotesUpdateCheck.expectedHash(lines.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "moonotes-network-0.6.0-beta.5.jar");
            throw new AssertionError("missing checksum was accepted");
        }catch(java.io.IOException expected){}

        // Helper can update/rollback after process exit on both Linux and Windows.
        Path base=Files.createTempDirectory("moonotes-updater-check");
        Path mods=Files.createDirectories(base.resolve("mods"));
        Path updates=Files.createDirectories(base.resolve("updates"));
        Path old=mods.resolve("moonotes-network-0.6.0-beta.3.jar");
        Path staged=updates.resolve("moonotes-network-0.6.0-beta.4.jar");
        Files.writeString(old,"known-good-jar");
        Files.writeString(staged,"verified-new-jar");
        String expected=MoonotesUpdateInstaller.checksum(staged);
        MoonotesUpdateInstaller.main(new String[]{
            "INSTALL","123456789",old.toString(),staged.toString(),updates.toString(),
            expected,"0.6.0-beta.4"
        });
        Path next=mods.resolve("moonotes-network-0.6.0-beta.4.jar");
        check(Files.exists(next)&&!Files.exists(old),"one updated JAR, no duplicates");
        check(Files.readString(next).equals("verified-new-jar"),"new bits installed");
        Path record=updates.resolve("last-update.properties");
        check(Files.isRegularFile(record),"rollback marker saved");
        Properties p=new Properties();
        try(InputStream stream=Files.newInputStream(record)){p.load(stream);}
        Path backup=updates.resolve(p.getProperty("backup"));
        check(Files.readString(backup).equals("known-good-jar"),"prior JAR preserved");
        MoonotesUpdateInstaller.main(new String[]{
            "ROLLBACK","123456789",next.toString(),backup.toString(),updates.toString(),
            MoonotesUpdateInstaller.checksum(backup),"0.6.0-beta.3"
        });
        check(Files.exists(old)&&!Files.exists(next),"rollback restored previous JAR");
        check(Files.readString(old).equals("known-good-jar"),"prior bits restored");
        check(!Files.exists(record),"rollback marker consumed");
        check(!MoonotesUpdateInstaller.validJarName("../fake.jar"),
            "unsafe file names rejected");
        System.out.println("UpdateSystemCheck: PASS");
    }
}
