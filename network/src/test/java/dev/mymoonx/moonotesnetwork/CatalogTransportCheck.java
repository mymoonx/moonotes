package dev.mymoonx.moonotesnetwork;

import java.io.IOException;
import java.util.ArrayList;

/** Offline contract checks reflect the live v3 GQL and connection responses verified 2026-11-23. */
public final class CatalogTransportCheck {
    private static final String SET="01g4m2821r000dsyz0tdha3zkx";
    private static final String NEW_SET="01kycqs3b86r95r13aawjsbvt7";
    private static final String EMOTE="01f0hmz1zg0004v54c00a616cb";
    private static final String USERS="{\"data\":{\"users\":[{\"connections\":[{\"platform\":\"TWITCH\",\"username\":\"catlord2\",\"id\":\"777\"}]},"
        +"{\"connections\":[{\"platform\":\"TWITCH\",\"username\":\"catlord\",\"id\":\"1234\"}]}]}}";
    private static final String SET_BODY="{\"id\":\""+SET+"\",\"name\":\"Cats\",\"emotes\":[{\"id\":\""+EMOTE+"\",\"name\":\"catJAM\",\"data\":{\"animated\":true,\"flags\":256}},{\"id\":\""+EMOTE+"\",\"name\":\"catAlias\"}]}";
    private static final String CONNECTION="{\"id\":\"1234\",\"platform\":\"TWITCH\",\"username\":\"catlord\",\"emote_set_id\":\""+SET+"\",\"emote_set\":"+SET_BODY+"}";
    private static final String WORKER_BODY="{\"channel\":\"catlord\",\"setId\":\""+SET+"\",\"count\":1,\"emotes\":[{\"id\":\""+EMOTE+"\",\"name\":\"catJAM\"}]}";
    private static int checks;
    interface Throwing { void run() throws Exception; }
    private static void rejects(Throwing action,String label) throws Exception {
        try { action.run(); throw new AssertionError(label); } catch (IOException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        check(CatalogTransport.resolveTwitchId(USERS,"catlord").equals("1234"),"exact Twitch login match, not 7TV username or fuzzy result");
        rejects(() -> CatalogTransport.resolveTwitchId(USERS,"unrelated"),"unknown login accepted");
        rejects(() -> CatalogTransport.resolveTwitchId(USERS.replace("catlord2","catlord"),"catlord"),"ambiguous Twitch IDs accepted");
        rejects(() -> CatalogTransport.resolveTwitchId(USERS.replace("TWITCH","DISCORD"),"catlord"),"wrong platform accepted");
        rejects(() -> CatalogTransport.resolveTwitchId("{\"errors\":[{\"message\":\"private debug\"}]}","catlord"),"GQL errors must not become empty catalog");
        rejects(() -> CatalogTransport.resolveTwitchId("[".repeat(25)+"]".repeat(25),"catlord"),"deep search response accepted");
        var requested=new ArrayList<CatalogTransport.Request>();
        var direct=new CatalogTransport(request->{
            requested.add(request);
            if(request.uri().getPath().equals("/v3/gql")) {
                check(request.post() && request.body().contains("FindChannel") && request.body().contains("catlord"),"search uses bounded JSON POST"); return USERS;
            }
            if(request.uri().getPath().equals("/v3/users/twitch/1234")) return CONNECTION;
            if(request.uri().getPath().equals("/v3/emote-sets/"+SET))return SET_BODY;
            throw new IOException("unexpected upstream");
        });
        var result=direct.fetch("channel","catlord","","auto");
        check(result.source().equals("7TV")&&result.parsed().emotes().size()==2,"direct lookup preserves named aliases sharing one image");
        check(result.parsed().emotes().getFirst().animated() && result.parsed().emotes().getFirst().zeroWidth(),"animation and zero width retained");
        check(requested.size()==2 && requested.stream().noneMatch(s->s.uri().getHost().endsWith("workers.dev")),"no Worker on successful direct fetch");
        requested.clear(); direct.fetch("channel","catlord",SET,"auto");
        check(requested.size()==2 && requested.getFirst().uri().getPath().equals("/v3/gql"),"refresh resolves current channel, not stale saved set");
        var switched=new CatalogTransport(request -> request.post() ? USERS : CONNECTION.replace(SET,NEW_SET));
        check(switched.fetch("channel","catlord",SET,"direct").parsed().setId().equals(NEW_SET),"channel set switch discovered");
        var fallback=new CatalogTransport(request->{
            if(request.uri().getHost().equals("7tv.io"))throw new IOException("TLS handshake private user/path");
            if(request.uri().getPath().endsWith("channel/catlord"))return WORKER_BODY;
            throw new IOException("unexpected request");
        });
        result=fallback.fetch("channel","catlord","","auto");
        check(result.source().equals("Cloudflare"),"Worker fallback when 7TV blocked");
        rejects(() -> fallback.fetch("channel","catlord","","direct"),"Worker used in direct only mode");
        rejects(() -> direct.fetch("channel","https://evil.test/","","auto"),"arbitrary host accepted");
        rejects(() -> CatalogTransport.parseConnection(CONNECTION.replace("1234","999"),"catlord","1234"),"wrong numeric connection accepted");
        rejects(() -> CatalogTransport.parseConnection(CONNECTION.replace("catlord","someone_else"),"catlord","1234"),"wrong Twitch login accepted");
        rejects(() -> CatalogTransport.parseConnection(CONNECTION.replace("\"emote_set_id\":\""+SET,"\"emote_set_id\":\""+NEW_SET),"catlord","1234"),"mismatched connected set accepted");
        String empty="{\"id\":\"1234\",\"platform\":\"TWITCH\",\"username\":\"catlord\",\"emote_set\":{\"id\":\""+SET+"\",\"emotes\":[]}}";
        check(CatalogTransport.parseConnection(empty,"catlord","1234").emotes().isEmpty(),"valid empty set accepted");
        rejects(() -> CatalogTransport.parseConnection(empty.replace("{\"id\":\""+SET+"\",\"emotes\":[]}","null"),"catlord","1234"),"missing selected set treated as empty");
        var failed=new CatalogTransport(request->{
            if(request.uri().getHost().equals("7tv.io"))throw new CatalogTransport.HttpFailure(404,"http","",0);
            throw new CatalogTransport.HttpFailure(502,"upstream_unavailable","набор канала",403);
        });
        try { failed.fetch("channel","catlord","","auto"); throw new AssertionError("both failed accepted"); }
        catch (CatalogTransport.LookupException error) {
            check(error.failures().size()==2,"both source failures retained");
            check(error.details().contains("HTTP 404") && error.details().contains("HTTP 502") && error.details().contains("7TV HTTP 403"),"exact local and upstream HTTP statuses retained");
            check(!error.details().contains("private") && !error.details().contains("https://"),"safe details exclude arbitrary text and URLs");
        }
        var interrupted=new CatalogTransport(request->{ throw new InterruptedException("cancel"); });
        try { interrupted.fetch("channel","catlord","","auto");throw new AssertionError("interruption swallowed"); }
        catch (InterruptedException expected) { checks++; }
        System.out.println("CatalogTransportCheck: "+checks+" cases passed");
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
}
