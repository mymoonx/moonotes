package dev.mymoonx.moonotesnetwork;

public final class ReactionRoomCodeCheck {
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        String code = ReactionRoomCode.create();
        check(code.matches("[0-9A-F]{32}"), "random code uses 128-bit HEX");
        check(ReactionRoomCode.normalize(code.toLowerCase()).equals(code),
            "case insensitive code entry");
        check(ReactionRoomCode.normalize(code.substring(0,8) + "-" + code.substring(8))
            .equals(code), "hyphenated pasted codes");
        check(ReactionRoomCode.normalize("foo").isEmpty(), "invalid code rejected");
        String one = ReactionRoomCode.roomKey("play.example.net:25565", code);
        String again = ReactionRoomCode.roomKey("PLAY.EXAMPLE.NET:25565", code);
        String otherServer = ReactionRoomCode.roomKey("another.example.net", code);
        String otherCode = ReactionRoomCode.roomKey("play.example.net:25565",
            ReactionRoomCode.create());
        check(one.matches("[a-f0-9]{64}"), "room key is hash");
        check(one.equals(again), "server normalization deterministic");
        check(!one.equals(otherServer), "room isolation by server");
        check(!one.equals(otherCode), "room isolation by code");
        check(!one.contains(code.toLowerCase()), "raw code never travels in URL");
        System.out.println("ReactionRoomCodeCheck: PASS");
    }
}
