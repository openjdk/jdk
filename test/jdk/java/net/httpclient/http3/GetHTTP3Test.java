/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;

import jdk.test.lib.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;

import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static org.junit.jupiter.api.Assertions.*;
import static java.lang.System.out;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/*
 * @test
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.Utils
 * @compile ../ReferenceTracker.java
 * @run junit/othervm -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.HttpClient.log=requests,responses,errors
 *                     GetHTTP3Test
 * @summary Basic HTTP/3 GET test
 */
//                     -Djdk.httpclient.http3.maxDirectConnectionTimeout=2500
public class GetHTTP3Test implements HttpServerAdapters {

    // The response body
    static final String BODY = """
            May the road rise up to meet you.
            May the wind be always at your back.
            May the sun shine warm upon your face;
            """;

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private static HttpTestServer h3TestServer;  // HTTP/2 ( h2 + h3)
    private static String h3URI;

    static final int ITERATION_COUNT = 4;
    // a shared executor helps reduce the amount of threads created by the test
    static final Executor executor = new TestExecutor(Executors.newCachedThreadPool());
    static final ConcurrentMap<String, Throwable> FAILURES = new ConcurrentHashMap<>();
    static volatile boolean tasksFailed;
    static final AtomicLong serverCount = new AtomicLong();
    static final AtomicLong clientCount = new AtomicLong();
    static final long start = System.nanoTime();
    public static String now() {
        long now = System.nanoTime() - start;
        long secs = now / 1000_000_000;
        long mill = (now % 1000_000_000) / 1000_000;
        long nan = now % 1000_000;
        return String.format("[%d s, %d ms, %d ns] ", secs, mill, nan);
    }

    private static final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;
    private static final Set<String> sharedClientHasH3 = ConcurrentHashMap.newKeySet();
    private static volatile HttpClient sharedClient;
    private static boolean directQuicConnectionSupported;

    static class TestExecutor implements Executor {
        final AtomicLong tasks = new AtomicLong();
        Executor executor;
        TestExecutor(Executor executor) {
            this.executor = executor;
        }

        @java.lang.Override
        public void execute(Runnable command) {
            long id = tasks.incrementAndGet();
            executor.execute(() -> {
                try {
                    command.run();
                } catch (Throwable t) {
                    tasksFailed = true;
                    System.out.printf(now() + "Task %s failed: %s%n", id, t);
                    System.err.printf(now() + "Task %s failed: %s%n", id, t);
                    FAILURES.putIfAbsent("Task " + id, t);
                    throw t;
                }
            });
        }
    }

    private static boolean stopAfterFirstFailure() {
        return Boolean.getBoolean("jdk.internal.httpclient.debug");
    }

    static final class TestStopper implements TestWatcher, BeforeEachCallback {
        final AtomicReference<String> failed = new AtomicReference<>();
        TestStopper() { }
        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            if (stopAfterFirstFailure()) {
                String msg = "Aborting due to: " + cause;
                failed.compareAndSet(null, msg);
                FAILURES.putIfAbsent(context.getDisplayName(), cause);
                System.out.printf("%nTEST FAILED: %s%s%n\tAborting due to %s%n%n",
                        now(), context.getDisplayName(), cause);
                System.err.printf("%nTEST FAILED: %s%s%n\tAborting due to %s%n%n",
                        now(), context.getDisplayName(), cause);
            }
        }

        @Override
        public void beforeEach(ExtensionContext context) {
            String msg = failed.get();
            Assumptions.assumeTrue(msg == null, msg);
        }
    }

    @RegisterExtension
    static final TestStopper stopper = new TestStopper();

    @AfterAll
    static void printFailedTests() {
        out.println("\n=========================");
        try {
            out.printf("%n%sCreated %d servers and %d clients%n",
                    now(), serverCount.get(), clientCount.get());
            if (FAILURES.isEmpty()) return;
            out.println("Failed tests: ");
            FAILURES.forEach((key, value) -> {
                out.printf("\t%s: %s%n", key, value);
                value.printStackTrace(out);
                value.printStackTrace();
            });
            if (tasksFailed) {
                System.out.println("WARNING: Some tasks failed");
            }
        } finally {
            out.println("\n=========================\n");
        }
    }

    private static String[] uris() {
        return new String[] {
                h3URI,
        };
    }

    public static Object[][] variants() {
        String[] uris = uris();
        Object[][] result = new Object[uris.length * 2 * 2 * 2][];
        int i = 0;
        for (var version : List.of(Optional.empty(), Optional.of(HTTP_3))) {
            for (Version firstRequestVersion : List.of(HTTP_2, HTTP_3)) {
                for (boolean sameClient : List.of(false, true)) {
                    for (String uri : uris) {
                        result[i++] = new Object[]{uri, firstRequestVersion, sameClient, version};
                    }
                }
            }
        }
        assert i == result.length;
        return result;
    }

    private HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        HttpClient client = newClientBuilderForH3()
                .version(HTTP_3)
                .proxy(HttpClient.Builder.NO_PROXY)
                .executor(executor)
                .sslContext(sslContext)
                .build();
        return TRACKER.track(client);
    }

    HttpClient newHttpClient(boolean share) {
        if (!share) return makeNewClient();
        HttpClient shared = sharedClient;
        if (shared != null) return shared;
        synchronized (this) {
            shared = sharedClient;
            if (shared == null) {
                shared = sharedClient = makeNewClient();
            }
            return shared;
        }
    }


    @ParameterizedTest
    @MethodSource("variants")
    public void testAsync(String uri, Version firstRequestVersion, boolean sameClient, Optional<Version> version) throws Exception {
        System.out.println("Request to " + uri +"/Async/*" +
                ", firstRequestVersion=" + firstRequestVersion +
                ", sameclient=" + sameClient + ", version=" + version);

        HttpClient client = newHttpClient(sameClient);
        final URI headURI = URI.create(uri + "/Async/First/HEAD");
        final Builder headBuilder = HttpRequest.newBuilder(headURI)
                .version(firstRequestVersion)
                .HEAD();
        Http3DiscoveryMode config = null;
        if (firstRequestVersion == HTTP_3 && !directQuicConnectionSupported) {
            // if the server doesn't listen for HTTP/3 on the same port than TCP, then
            // do not attempt to connect to the URI host:port through UDP - as we might
            // be connecting to some other server. Once the first request has gone
            // through, there should be an AltService record for the server, so
            // we should be able to safely use any default config (except
            // HTTP_3_URI_ONLY)
            config = ALT_SVC;
        }
        if (config != null) {
            out.println("first request will use " + config);
            headBuilder.setOption(H3_DISCOVERY, config);
            config = null;
        }

        HttpResponse<String> response1 = client.send(headBuilder.build(), BodyHandlers.ofString());
        assertEquals(200, response1.statusCode(), "Unexpected first response code");
        assertEquals("", response1.body(), "Unexpected first response body");
        boolean expectH3 = sameClient && sharedClientHasH3.contains(headURI.getRawAuthority());
        if (firstRequestVersion == HTTP_3) {
            if (expectH3) {
                out.println("Expecting HEAD response over HTTP_3");
                assertEquals(HTTP_3, response1.version(), "Unexpected first response version");
            }
        } else {
            out.println("Expecting HEAD response over HTTP_2");
            assertEquals(HTTP_2, response1.version(), "Unexpected first response version");
        }
        out.println("HEAD response version: " + response1.version());
        if (response1.version() == HTTP_2) {
            if (sameClient) {
                sharedClientHasH3.add(headURI.getRawAuthority());
            }
            expectH3 = version.isEmpty() && client.version() == HTTP_3;
            if (version.orElse(null) == HTTP_3 && !directQuicConnectionSupported) {
                config = ALT_SVC;
                expectH3 = true;
            }
            // we can expect H3 only if the (default) config is not ANY
            if (expectH3) {
                out.println("first response came over HTTP/2, so we should expect all responses over HTTP/3");
            }
        } else if (response1.version() == HTTP_3) {
            expectH3 = directQuicConnectionSupported && version.orElse(null) == HTTP_3;
            if (expectH3) {
                out.println("first response came over HTTP/3, direct connection supported: expect HTTP/3");
            } else if (firstRequestVersion == HTTP_3 && version.isEmpty()
                    && config == null && directQuicConnectionSupported) {
                config = ANY;
                expectH3 = true;
            }
        }
        out.printf("request version: %s, directConnectionSupported: %s, first response: %s," +
                        " config: %s, expectH3: %s%n",
            version, directQuicConnectionSupported, response1.version(), config, expectH3);
        if (expectH3) {
            out.println("All responses should now come through HTTP/3");
        }

        Builder builder = HttpRequest.newBuilder()
                .GET();
        version.ifPresent(builder::version);
        if (config != null) {
            builder.setOption(H3_DISCOVERY, config);
        }
        Map<URI, CompletableFuture<HttpResponse<String>>> responses = new HashMap<>();
        for (int i = 0; i < ITERATION_COUNT; i++) {
            HttpRequest request = builder.uri(URI.create(uri+"/Async/GET/"+i)).build();
            System.out.println("Iteration: " + request.uri());
            responses.put(request.uri(), client.sendAsync(request, BodyHandlers.ofString()));
        }
        int h3Count = 0;
        while (!responses.isEmpty()) {
            CompletableFuture.anyOf(responses.values().toArray(CompletableFuture[]::new)).join();
            var done = responses.entrySet().stream()
                    .filter((e) -> e.getValue().isDone()).toList();
            for (var e : done) {
                URI u = e.getKey();
                responses.remove(u);
                out.println("Checking response: " + u);
                var response = e.getValue().get();
                out.println("Response is: " + response + ", [version: " + response.version() + "]");
                assertEquals(200, response.statusCode(), "status for " + u);
                assertEquals(BODY, response.body(), "body for " + u);
                if (expectH3) {
                    assertEquals(HTTP_3, response.version(), "version for " + u);
                }
                if (response.version() == HTTP_3) {
                    h3Count++;
                }
            }
        }
        if (client.version() == HTTP_3 || version.orElse(null) == HTTP_3) {
            if (h3Count == 0) {
                throw new AssertionError("No request used HTTP/3");
            }
        }
        if (!sameClient) {
            var tracker = TRACKER.getTracker(client);
            client = null;
            System.gc();
            AssertionError error = TRACKER.check(tracker, Utils.adjustTimeout(1000));
            if (error != null) throw error;
        }
        System.out.println("test: DONE");
    }

    @ParameterizedTest
    @MethodSource("uris")
    public void testSync(String h3URI) throws Exception {
        HttpClient client = makeNewClient();
        Builder builder = HttpRequest.newBuilder(URI.create(h3URI + "/Sync/GET/1"))
                .version(HTTP_3)
                .GET();
        if (!directQuicConnectionSupported) {
            // if the server doesn't listen for HTTP/3 on the same port than TCP, then
            // do not attempt to connect to the URI host:port through UDP - as we might
            // be connecting to some other server. Once the first request has gone
            // through, there should be an AltService record for the server, so
            // we should be able to safely use any default config (except
            // HTTP_3_URI_ONLY)
            builder.setOption(H3_DISCOVERY, ALT_SVC);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        out.println("Response #1: " + response);
        out.println("Version  #1: " + response.version());
        assertEquals(200, response.statusCode(), "first response status");
        if (directQuicConnectionSupported) {
            // TODO unreliable assertion
            //assertEquals(response.version(), HTTP_3, "Unexpected first response version");
        } else {
            assertEquals(HTTP_2, response.version(), "Unexpected first response version");
        }
        assertEquals(BODY, response.body(), "first response body");

        request = builder.uri(URI.create(h3URI + "/Sync/GET/2")).build();
        response = client.send(request, BodyHandlers.ofString());
        out.println("Response #2: " + response);
        out.println("Version  #2: " + response.version());
        assertEquals(200, response.statusCode(), "second response status");
        assertEquals(HTTP_3, response.version(), "second response version");
        assertEquals(BODY, response.body(), "second response body");

        var tracker = TRACKER.getTracker(client);
        client = null;
        System.gc();
        AssertionError error = TRACKER.check(tracker, Utils.adjustTimeout(1000));
        if (error != null) throw error;
    }

    @BeforeAll
    public static void setup() throws Exception {
        final Http2TestServer h2WithAltService = new Http2TestServer("localhost", true,
                sslContext).enableH3AltServiceOnSamePort();
        h3TestServer = HttpTestServer.of(h2WithAltService);
        h3TestServer.addHandler(new Handler(), "/h3/testH3/");
        h3URI = "https://" + h3TestServer.serverAuthority() + "/h3/testH3/GET";
        serverCount.addAndGet(1);
        h3TestServer.start();
        directQuicConnectionSupported = h2WithAltService.supportsH3DirectConnection();
    }

    @AfterAll
    public static void teardown() throws Exception {
        System.err.println("=======================================================");
        System.err.println("               Tearing down test");
        System.err.println("=======================================================");
        String sharedClientName =
                sharedClient == null ? null : sharedClient.toString();
        sharedClient = null;
        Thread.sleep(100);
        AssertionError fail = TRACKER.check(Utils.adjustTimeout(1000));
        try {
            h3TestServer.stop();
        } finally {
            if (fail != null) {
                if (sharedClientName != null) {
                    System.err.println("Shared client name is: " + sharedClientName);
                }
                throw fail;
            }
        }
    }

    static class Handler implements HttpTestHandler {
        public Handler() {}

        volatile int invocation = 0;

        @java.lang.Override
        public void handle(HttpTestExchange t)
                throws IOException {
            try {
                URI uri = t.getRequestURI();
                System.err.printf("Handler received request for %s\n", uri);

                if ((invocation++ % 2) == 1) {
                    System.err.printf("Server sending %d - chunked\n", 200);
                    t.sendResponseHeaders(200, -1);
                } else {
                    System.err.printf("Server sending %d - %s length\n", 200, BODY.length());
                    t.sendResponseHeaders(200, BODY.length());
                }
                try (InputStream is = t.getRequestBody();
                     OutputStream os = t.getResponseBody()) {
                    assertEquals(0, is.readAllBytes().length);
                    if (!"HEAD".equals(t.getRequestMethod())) {
                        String[] body = BODY.split("\n");
                        for (String line : body) {
                            os.write(line.getBytes(StandardCharsets.UTF_8));
                            os.write('\n');
                            os.flush();
                        }
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace(System.err);
                throw new IOException(e);
            }
        }
    }
}
