package dev.mymoonx.moonotesnetwork;

import java.io.IOException;

/** Offline input validation: does not contact Twitch or use real credentials. */
public final class TwitchLinkCheck {
    public static void main(String[] args) throws Exception {
        require(TwitchLink.validClientId("abcdefghijklmnopqrst12345"), "client ID valid");
        require(!TwitchLink.validClientId("secret /token"), "reject spaces and punctuation");
        require(!TwitchLink.validClientId("short"), "reject short ID");

        TwitchLink.Device device = TwitchLink.parseDevice("""
            {"device_code":"example-local-device-id","user_code":"ABCDEFGH",
             "verification_uri":"https://www.twitch.tv/activate","expires_in":1800,"interval":5}
            """);
        require("ABCDEFGH".equals(device.userCode()), "device user code");
        require("www.twitch.tv".equals(device.verification().getHost()), "trusted site");
        fails(() -> TwitchLink.parseDevice("""
            {"device_code":"example-local-device-id","user_code":"ABCDEFGH",
             "verification_uri":"https://fake-twitch.example/activate","expires_in":1800,"interval":5}
            """));
        fails(() -> TwitchLink.parseDevice("""
            {"device_code":"example-local-device-id","user_code":"ABCDEFGH",
             "verification_uri":"http://www.twitch.tv/activate","expires_in":1800,"interval":5}
            """));

        TwitchLink.Identity user = TwitchLink.parseIdentity("""
            {"client_id":"abcdefghijklmnopqrst12345","user_id":"123456789","login":"MyMoon_X"}
            """, "abcdefghijklmnopqrst12345");
        require("123456789".equals(user.id()), "verified Twitch ID");
        require("mymoon_x".equals(user.login()), "normalized login");
        fails(() -> TwitchLink.parseIdentity("""
            {"client_id":"wrong-client-id","user_id":"123456789","login":"mymoon_x"}
            """, "abcdefghijklmnopqrst12345"));
        fails(() -> TwitchLink.parseIdentity("""
            {"client_id":"abcdefghijklmnopqrst12345","user_id":"not-an-id","login":"mymoon_x"}
            """, "abcdefghijklmnopqrst12345"));
        System.out.println("TwitchLinkCheck: PASS");
    }
    private static void require(boolean pass, String label) {
        if (!pass) throw new AssertionError(label);
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void fails(Checked task) throws Exception {
        try { task.run(); }
        catch (IOException expected) { return; }
        throw new AssertionError("Expected invalid Twitch response to be rejected");
    }
}
