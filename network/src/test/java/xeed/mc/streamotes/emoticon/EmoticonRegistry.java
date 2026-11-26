package xeed.mc.streamotes.emoticon;

/**
 * Test-only reflective stand-in. No upstream Streamotes class is shipped with
 * the production Moonotes JAR.
 */
public final class EmoticonRegistry {
    private static final Object EMOTE = new Object();
    private EmoticonRegistry() { }
    public static Object fromName(String value) {
        return "WW".equals(value) || "!vanish".equals(value) ? EMOTE : null;
    }
}
