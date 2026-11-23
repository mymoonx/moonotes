package dev.mymoonx.moonotesnetwork;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLException;

/** Specific missing-image regression: do not report a healthy first emote as a healthy missing emote. */
public final class MissingSourceCheck {
    private static final URI BOSS = URI.create("https://cdn.7tv.app/emote/01m34jjzfhr2kk2qkngqp2cdwh/2x.webp");
    private static final String BASE = "https://moonotes-emotes.thefreelom.workers.dev";
    private static int checked;
    private static void require(boolean condition, String message) {checked++;if(!condition)throw new AssertionError(message);}
    private static final ImageDownloader.Observer NOOP = new ImageDownloader.Observer() {
        public void success(String source,long millis){}
        public void fallback(){}
        public void failure(String category){}
    };
    public static void main(String[] args) throws Exception {
        List<URI> visited=new ArrayList<>();
        ImageDownloader unavailable = new ImageDownloader(BASE, (uri, cancel, count) -> {
            visited.add(uri);
            if (uri.getHost().equals("cdn.7tv.app")) throw new SSLException("sensitive host message");
            return new ImageDownloader.Response(404, "text/plain", new byte[0], 0);
        },NOOP,new SourceCircuit());
        try {
            unavailable.fetch(BOSS,"auto",true,()->false,v->{});
            throw new AssertionError("Both sources should fail");
        } catch(ImageDownloader.SourcesUnavailable failure) {
            require("TLS".equals(failure.direct()),"direct TLS status lost");
            require("HTTP 404".equals(failure.worker()),"Cloudflare status lost");
            require(!failure.getMessage().contains("sensitive") && !failure.getMessage().contains("cdn.7tv"),"unsanitized exception leaked");
        }
        require(visited.size()==2,"must not repeat direct after a transport error");
        require(visited.get(1).getPath().contains("01m34jjzfhr2kk2qkngqp2cdwh"),"must probe missing ID, not an unrelated first ID");
        visited.clear();
        try {
            unavailable.fetch(BOSS,"auto",false,()->false,v->{});
            throw new AssertionError("Worker 404 should fail");
        } catch(ImageDownloader.SourcesUnavailable failure) {
            require(failure.direct().contains("пауза"),"cooldown should be explicit");
            require("HTTP 404".equals(failure.worker()),"worker 404 should remain explicit");
        }
        require(visited.size()==1 && visited.getFirst().getHost().contains("workers.dev"),"network cooldown not respected");
        ImageDownloader onlyWorker = new ImageDownloader(BASE, (uri, cancel, bytes) -> new ImageDownloader.Response(404, "", new byte[0], 0), NOOP,new SourceCircuit());
        try {
            onlyWorker.fetch(BOSS,"worker",false,()->false,v->{});
            throw new AssertionError("Worker 404 should fail");
        } catch(ImageDownloader.SourcesUnavailable failure) {
            require(failure.direct().equals("выключен в настройках"),"forced mode must show direct disabled");
        }
        System.out.println("Missing source checks passed ("+checked+" assertions)");
    }
}
