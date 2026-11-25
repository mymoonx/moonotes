package dev.mymoonx.moonotesnetwork;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Room codes are random capabilities: knowing a code is permission to join.
 * The raw server address and room code NEVER leave the Minecraft client.
 * They are combined and hashed to select a Cloudflare Durable Object room.
 *
 * They are NOT proof of Minecraft account identity; only use with people you trust.
 */
final class ReactionRoomCode {
    private static final SecureRandom RANDOM = new SecureRandom();
    private ReactionRoomCode() { }

    static String create() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    static String normalize(String input) {
        if (input == null) return "";
        String clean = input.replace(" ", "").replace("-", "").trim().toUpperCase(Locale.ROOT);
        return clean.matches("[0-9A-F]{32}") ? clean : "";
    }

    static String roomKey(String serverAddress, String code) {
        String normalized = normalize(code);
        if (normalized.isEmpty() || serverAddress == null || serverAddress.isBlank())
            throw new IllegalArgumentException("Room code / server is missing");
        String worldKey = ServerCatalogProfiles.worldKey(serverAddress);
        String value = "moonotes-room-v1|" + worldKey + "|" + normalized;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
