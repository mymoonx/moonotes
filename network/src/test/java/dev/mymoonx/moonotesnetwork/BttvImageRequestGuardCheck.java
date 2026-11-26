package dev.mymoonx.moonotesnetwork;

/** Guard against 10-second false BTTV icon loads blocking Streamotes' FIFO. */
public final class BttvImageRequestGuardCheck {
    private static int checks;
    private static void require(boolean value, String description) {
        checks++;
        if (!value) throw new AssertionError(description);
    }
    public static final class FakeEmote {
        private final String name;
        public FakeEmote(String name) { this.name = name; }
        public String getName() { return name; }
    }
    public static void main(String[] args) {
        for (String code : new String[]{"w!", "h!", "v!", "z!"}) {
            require(BttvImageRequestGuard.skip("bttv", code),
                "BTTV control token is not an image: " + code);
            require(BttvImageRequestGuard.skip("BTTV", new FakeEmote(code)),
                "BTTV control token object must bypass download: " + code);
            require(!BttvImageRequestGuard.skip("7tv", code),
                "7TV alias must remain independently loadable: " + code);
            require(!BttvImageRequestGuard.skip("ffz", new FakeEmote(code)),
                "FFZ alias must remain independently loadable: " + code);
        }
        for (String real : new String[]{"q", "re", "WW", "catJAM", "widescreen"}) {
            require(!BttvImageRequestGuard.skip("bttv", real),
                "ordinary BTTV emote must remain loadable: " + real);
            require(!BttvImageRequestGuard.skip("bttv", new FakeEmote(real)),
                "real BTTV object must reach the normal loader: " + real);
        }
        require(!BttvImageRequestGuard.skip("bttv", (Object) null),
            "null icon must not cancel the request");
        require(!BttvImageRequestGuard.skip(null, new FakeEmote("w!")),
            "unidentified provider is not blocked");
        require(!BttvImageRequestGuard.skip("bttv", new Object()),
            "changed Streamotes API fails closed without exceptions");
        require(!BttvImageRequestGuard.skip("bttv", "W!"),
            "BTTV code matching remains case-sensitive");
        System.out.println("BttvImageRequestGuardCheck: " + checks + " checks passed");
    }
}
