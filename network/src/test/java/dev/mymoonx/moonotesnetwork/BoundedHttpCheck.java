package dev.mymoonx.moonotesnetwork;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Real loopback HTTP tests: bounded chunked body, stalled body, and cancellation. */
public final class BoundedHttpCheck {
    public static void main(String[] args) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
        server.setExecutor(executor);
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, 4);
            try (var body = exchange.getResponseBody()) { body.write(new byte[] {1, 2, 3, 4}); }
        });
        server.createContext("/large", exchange -> {
            exchange.sendResponseHeaders(200, 0); // Chunked: no Content-Length to trust.
            try (var body = exchange.getResponseBody()) { body.write(new byte[4096]); }
            catch (IOException ignored) { }
        });
        server.createContext("/stall", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var body = exchange.getResponseBody()) {
                body.write(1); body.flush();
                try { Thread.sleep(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            } catch (IOException ignored) { }
        });
        server.createContext("/post", exchange -> {
            boolean correct = exchange.getRequestMethod().equals("POST")
                && "application/json".equals(exchange.getRequestHeaders().getFirst("Content-Type"))
                && new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).equals("{\"q\":\"Moonotes\"}");
            exchange.sendResponseHeaders(correct ? 200 : 400, 2);
            try (var body = exchange.getResponseBody()) { body.write(new byte[] {'{','}'}); }
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/ok"); exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        int checks = 0;
        try {
            AtomicLong received = new AtomicLong();
            var response = BoundedHttp.get(URI.create(base + "/ok"), "application/octet-stream", 4,
                Duration.ofSeconds(2), () -> false, received::addAndGet);
            if (response.body().length != 4 || received.get() != 4) throw new AssertionError("exact body/byte count");
            checks++;
            try {
                BoundedHttp.get(URI.create(base + "/large"), "application/octet-stream", 32,
                    Duration.ofSeconds(2), () -> false, count -> { });
                throw new AssertionError("chunked oversized body accepted");
            } catch (IOException expected) {
                if (!DiagnosticsReport.errorKind(expected).equals("size_limit")) throw expected;
                checks++;
            }
            long started = System.nanoTime();
            try {
                BoundedHttp.get(URI.create(base + "/stall"), "application/octet-stream", 32,
                    Duration.ofMillis(250), () -> false, count -> { });
                throw new AssertionError("stalled body completed");
            } catch (HttpTimeoutException expected) {
                if ((System.nanoTime() - started) / 1_000_000L > 1500) throw new AssertionError("body deadline ignored");
                checks++;
            }
            AtomicBoolean cancel = new AtomicBoolean();
            try {
                BoundedHttp.get(URI.create(base + "/stall"), "application/octet-stream", 32,
                    Duration.ofSeconds(5), cancel::get, count -> cancel.set(true));
                throw new AssertionError("cancelled body completed");
            } catch (InterruptedException expected) { checks++; }
            var posted = BoundedHttp.postJson(URI.create(base + "/post"), "{\"q\":\"Moonotes\"}", 16,
                Duration.ofSeconds(2), () -> false, count -> {});
            if (posted.statusCode() != 200) throw new AssertionError("JSON POST contract"); checks++;
            var redirected = BoundedHttp.get(URI.create(base + "/redirect"), "application/json", 32,
                Duration.ofSeconds(2), () -> false, count -> {});
            if (redirected.statusCode() != 302) throw new AssertionError("redirect followed"); checks++;
            try {
                BoundedHttp.postJson(URI.create(base + "/post"), "x".repeat(8193), 32, Duration.ofSeconds(2), () -> false, count -> {});
                throw new AssertionError("oversized request accepted");
            } catch (IOException expected) { checks++; }
            System.out.println("Bounded HTTP checks passed (" + checks + " checks, real local HTTP)");
        } finally { server.stop(0); executor.shutdownNow(); }
    }
}
