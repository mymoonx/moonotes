package dev.mymoonx.moonotesnetwork;

import java.util.concurrent.atomic.LongAdder;

/**
 * Fixed-category, in-memory, aggregate diagnostics only. No user IDs,
 * IP addresses, server names, text, key strokes or emote names are collected.
 * Disabled by default. There is no automatic uploader.
 */
final class MoonotesTelemetry {
    enum Event { WHEEL_OPEN, PICKER_OPEN, MANAGER_OPEN, CHAT_SEND, LOCAL_REACTION }
    private static final LongAdder[] COUNTERS;
    static {
        COUNTERS=new LongAdder[Event.values().length];
        for(int i=0;i<COUNTERS.length;i++)COUNTERS[i]=new LongAdder();
    }
    private MoonotesTelemetry(){}
    static void count(Event event){
        if(!SupportPreferences.local().telemetryOptIn()||event==null)return;
        COUNTERS[event.ordinal()].increment();
    }
    static void clear(){for(LongAdder counter:COUNTERS)counter.reset();}
    static String summary(){
        if(!SupportPreferences.local().telemetryOptIn())return "Агрегированная телеметрия выключена.";
        StringBuilder out=new StringBuilder("Агрегированная статистика текущей сессии:\n");
        for(Event event:Event.values())
            out.append(event.name().toLowerCase(java.util.Locale.ROOT))
                .append(": ").append(Math.min(COUNTERS[event.ordinal()].sum(),999999)).append('\n');
        out.append("Только локально: автоматической отправки нет.\n");
        return out.toString();
    }
}
