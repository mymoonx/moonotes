package dev.mymoonx.moonotesnetwork;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.Locale;

/** Human-readable and copy-safe checks, intentionally excluding raw logs or IDs. */
final class MoonotesHealthCheck {
    private MoonotesHealthCheck() { }

    static String run() {
        StringBuilder report = new StringBuilder(1024);
        report.append("Moonotes — проверка системы\n");
        report.append("Minecraft: 26.3\n");
        report.append("Network: ").append(version()).append('\n');
        report.append("Java major: ").append(javaMajor()).append('\n');
        report.append("OS family: ").append(osFamily()).append('\n');
        report.append("Fabric API: ").append(FabricLoader.getInstance().isModLoaded("fabric-api") ? "OK" : "не найден").append('\n');
        report.append("Streamotes: ").append(FabricLoader.getInstance().isModLoaded("streamotes") ? "OK" : "не найден").append('\n');
        Path config = FabricLoader.getInstance().getConfigDir();
        report.append("Настройки: ").append(Files.isDirectory(config) && Files.isWritable(config) ? "OK" : "нет доступа к записи").append('\n');
        report.append("Группы: ").append(LibraryPreferences.local().groupNames().size()).append('\n');
        report.append("Каталоги: ").append(MoonotesNetworkClient.catalogs().size()).append('\n');
        report.append("Настройки колеса: ").append(WheelSettings.local().error().isBlank() ? "OK" : "ошибка").append('\n');
        report.append("Настройки поддержки: ").append(SupportPreferences.local().status().isBlank() ? "OK" : "ошибка").append('\n');
        report.append("Профиль качества: ").append(SupportPreferences.local().performance().name()).append('\n');
        report.append("Категория последней ошибки: ").append(DiagnosticsReport.errorKind(MoonotesNetworkClient.lastDownloadFailure())).append('\n');
        report.append("\nОтчёт локальный. Не содержит имён, IP, адресов серверов, текста чата или токенов.\n");
        return report.toString();
    }

    static String compactReport() {
        String report=run();
        // Fixed typed fields only; no latest.log, freeform server input, paths or tokens.
        return report.length() <= 1600 ? report : report.substring(0,1600);
    }

    private static String version() {
        return FabricLoader.getInstance().getModContainer("moonotes-network")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .filter(s -> s.matches("[a-zA-Z0-9.\\-]{1,40}")).orElse("unknown");
    }
    private static String javaMajor() {
        String raw=System.getProperty("java.specification.version","?");
        return raw.matches("[0-9]{1,2}") ? raw : "unknown";
    }
    private static String osFamily() {
        String raw=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        if(raw.contains("windows"))return "Windows";
        if(raw.contains("linux"))return "Linux";
        if(raw.contains("mac"))return "macOS";
        return "other";
    }
}
