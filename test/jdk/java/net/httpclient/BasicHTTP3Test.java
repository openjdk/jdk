/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.internal.net.quic.QuicVersion;
import jdk.test.lib.net.SimpleSSLContext;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;

import static org.junit.jupiter.api.Assertions.*;

import static java.lang.System.out;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
 *        ReferenceTracker
 *        jdk.httpclient.test.lib.quic.QuicStandaloneServer
 * @run junit/othervm -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.HttpClient.log=requests,responses,errors
 *                      -Djavax.net.debug=all
 *                     BasicHTTP3Test
 * @summary Basic HTTP/3 test
 */
public class BasicHTTP3Test implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    static HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    static String https2URI;
    static HttpTestServer h3TestServer;  // HTTP/2 ( h2 + h3)
    static String h3URI;
    static HttpTestServer h3qv2TestServer;  // HTTP/2 ( h2 + h3 on Quic v2, incompatible nego)
    static String h3URIQv2;
    static HttpTestServer h3qv2CTestServer;  // HTTP/2 ( h2 + h3 on Quic v2, compatible nego)
    static String h3URIQv2C;
    static HttpTestServer h3mtlsTestServer;  // HTTP/2 ( h2 + h3), h3 requires client cert
    static String h3mtlsURI;
    static HttpTestServer h3TestServerWithRetry;  // h3
    static String h3URIRetry;
    static HttpTestServer h3TestServerWithTLSHelloRetry;  // h3
    static String h3URITLSHelloRetry;

    static final int ITERATION_COUNT = 4;
    // a shared executor helps reduce the amount of threads created by the test
    static final Executor executor = new TestExecutor(Executors.newCachedThreadPool());
    static final ConcurrentMap<String, Throwable> FAILURES = new ConcurrentHashMap<>();
    static volatile boolean tasksFailed;
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
    private static volatile HttpClient sharedClient;

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
            out.printf("%n%sCreated %d clients%n",
                    now(), clientCount.get());
            if (FAILURES.isEmpty()) return;
            out.println("Failed tests: ");
            FAILURES.entrySet().forEach((e) -> {
                out.printf("\t%s: %s%n", e.getKey(), e.getValue());
                e.getValue().printStackTrace(out);
                e.getValue().printStackTrace();
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
                https2URI,
                h3URI
        };
    }

    public static Object[][] variants() {
        String[] uris = uris();
        Object[][] result = new Object[uris.length * 2 * 2][];
        int i = 0;
        for (var version : List.of(Optional.empty(), Optional.of(Version.HTTP_3))) {
            for (boolean sameClient : List.of(false, true)) {
                for (String uri : uris()) {
                    result[i++] = new Object[]{uri, sameClient, version};
                }
            }
        }
        assert i == uris.length * 2 * 2;
        return result;
    }

    public static Object[][] versions() {
        Object[][] result = {
                {h3URI}, {h3URIRetry},
                {h3URIQv2}, {h3URIQv2C},
                {h3mtlsURI}, {h3URITLSHelloRetry},
        };
        return result;
    }

    private HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        HttpClient client = newClientBuilderForH3()
                .version(Version.HTTP_3)
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
    public void test(String uri, boolean sameClient, Optional<Version> version) throws Exception {
        System.out.printf("%n%s-- test version=%s, sameClient=%s, uri=%s%n%n",
                now(), version, sameClient, uri);

        HttpClient client = newHttpClient(sameClient);

        Builder builder = HttpRequest.newBuilder(URI.create(uri))
                .GET();
        version.ifPresent(builder::version);
        for (int i = 0; i < ITERATION_COUNT; i++) {
            // don't want to attempt direct connection as there could be another
            // HTTP/3 endpoint listening at the URI port.
            // sameClient should be fine because version.empty() should
            // have come first and populated alt-services.
            builder.setOption(H3_DISCOVERY, ALT_SVC);
            HttpRequest request = builder.build();
            System.out.println("Iteration: " + i);
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            out.println("Response: " + response);
            out.println("Version: " + response.version());
            int expectedResponse =  200;
            if (response.statusCode() != expectedResponse)
                throw new RuntimeException("wrong response code " + response.statusCode());
        }
        if (!sameClient) {
            var tracker = TRACKER.getTracker(client);
            client = null;
            System.gc();
            AssertionError error = TRACKER.check(tracker, 1000);
            if (error != null) throw error;
        }
        System.out.println("test: DONE");
    }

    @ParameterizedTest
    @MethodSource("versions")
    public void testH3(final String h3URI) throws Exception {
        System.out.printf("%n%s-- testH3 h3URI=%s%n%n", now(), h3URI);
        HttpClient client = makeNewClient();
        URI uri = URI.create(h3URI);
        Builder builder = HttpRequest.newBuilder(uri)
                .version(HTTP_2)
                .GET();
        HttpRequest request = builder.build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        out.println("Response #1: " + response);
        out.println("Version  #1: " + response.version());
        assertEquals(200, response.statusCode(), "first response status");
        assertEquals(HTTP_2, response.version(), "first response version");

        request = builder.version(Version.HTTP_3).build();
        response = client.send(request, BodyHandlers.ofString());
        out.println("Response #2: " + response);
        out.println("Version  #2: " + response.version());
        assertEquals(200, response.statusCode(), "second response status");
        assertEquals(Version.HTTP_3, response.version(), "second response version");

        if (h3URI.equals(h3mtlsURI)) {
            assertNotNull(response.sslSession().get().getLocalCertificates());
        } else {
            assertNull(response.sslSession().get().getLocalCertificates());
        }
        var tracker = TRACKER.getTracker(client);
        client = null;
        System.gc();
        AssertionError error = TRACKER.check(tracker, 1000);
        if (error != null) throw error;
    }

    // verify that the client handles HTTP/3 reset stream correctly
    @Test
    public void testH3Reset() throws Exception {
        System.out.printf("%n%s-- testH3Reset uri=%s%n%n", now(), h3URI);
        HttpClient client = makeNewClient();
        URI uri = URI.create(h3URI);
        Builder builder = HttpRequest.newBuilder(uri)
                .version(HTTP_2)
                .GET();
        HttpRequest request = builder.build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        out.println("Response #1: " + response);
        out.println("Version  #1: " + response.version());
        assertEquals(200, response.statusCode(), "first response status");
        assertEquals(HTTP_2, response.version(), "first response version");

        // instruct the server side handler to throw an exception
        // that then causes the test server to reset the stream
        final String resetCausingURI = h3URI + "?handlerShouldThrow=true";
        builder = HttpRequest.newBuilder(URI.create(resetCausingURI))
                .GET();
        request = builder.version(Version.HTTP_3)
                .setOption(H3_DISCOVERY, ALT_SVC)
                .build();
        try {
            response = client.send(request, BodyHandlers.ofString());
            throw new RuntimeException("Unexpectedly received a response instead of an exception," +
                    " response: " + response);
        } catch (IOException e) {
            final String msg = e.getMessage();
            if (msg == null || !msg.contains("reset by peer")) {
                // unexpected message in the exception, propagate the exception
                throw e;
            }
        }
        var tracker = TRACKER.getTracker(client);
        client = null;
        System.gc();
        AssertionError error = TRACKER.check(tracker, 1000);
        if (error != null) throw error;
    }

    @BeforeAll
    public static void setup() throws Exception {
        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(new Handler(), "/https2/test/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/test/x";

        // A HTTP2 server with H3 enabled on a different host:port than the HTTP2 server
        h3TestServer = HttpTestServer.create(HTTP_3, sslContext);
        final HttpTestHandler h3Handler = new Handler();
        h3TestServer.addHandler(h3Handler, "/h3/testH3/");
        h3URI = "https://" + h3TestServer.serverAuthority() + "/h3/testH3/h3";
        assertTrue(h3TestServer.canHandle(HTTP_2, Version.HTTP_3), "Server was expected" +
                " to handle both HTTP2 and HTTP3, but doesn't");

        // A HTTP2 server with H3 QUICv2 enabled on a different host:port than the HTTP2 server
        final Http2TestServer h2q2Server = new Http2TestServer("localhost", true, sslContext)
                .enableH3AltServiceOnEphemeralPortWithVersion(QuicVersion.QUIC_V2, false);
        h3qv2TestServer = HttpTestServer.of(h2q2Server);
        h3qv2TestServer.addHandler(h3Handler, "/h3/testH3/");
        h3URIQv2 = "https://" + h3qv2TestServer.serverAuthority() + "/h3/testH3/h3qv2";
        assertTrue(h3qv2TestServer.canHandle(HTTP_2, Version.HTTP_3), "Server was expected" +
                " to handle both HTTP2 and HTTP3, but doesn't");

        // A HTTP2 server with H3 QUICv2 compatible negotiation enabled on a different host:port than the HTTP2 server
        final Http2TestServer h2q2CServer = new Http2TestServer("localhost", true, sslContext)
                .enableH3AltServiceOnEphemeralPortWithVersion(QuicVersion.QUIC_V2, true);
        h3qv2CTestServer = HttpTestServer.of(h2q2CServer);
        h3qv2CTestServer.addHandler(h3Handler, "/h3/testH3/");
        h3URIQv2C = "https://" + h3qv2CTestServer.serverAuthority() + "/h3/testH3/h3qv2c";
        assertTrue(h3qv2CTestServer.canHandle(HTTP_2, Version.HTTP_3), "Server was expected" +
                " to handle both HTTP2 and HTTP3, but doesn't");

        // A HTTP2 server with H3 enabled on a different host:port than the HTTP2 server
        // H3 server requires the client to authenticate with a certificate
        h3mtlsTestServer = HttpTestServer.create(HTTP_3, sslContext);
        h3mtlsTestServer.addHandler(h3Handler, "/h3/testH3/");
        h3mtlsTestServer.getH3AltService().get().getQuicServer().setNeedClientAuth(true);
        h3mtlsURI = "https://" + h3mtlsTestServer.serverAuthority() + "/h3/testH3/h3mtls";
        assertTrue(h3mtlsTestServer.canHandle(HTTP_2, Version.HTTP_3), "Server was expected" +
                " to handle both HTTP2 and HTTP3, but doesn't");

        // A HTTP2 test server with H3 alt service listening on different host:port
        // and the underlying quic server for H3 is configured to send a RETRY packet
        final Http2TestServer h2Server = new Http2TestServer("localhost", true, sslContext)
                .enableH3AltServiceOnEphemeralPort();
        // configure send retry on QUIC server
        h2Server.getH3AltService().get().getQuicServer().sendRetry(true);
        h3TestServerWithRetry = HttpTestServer.of(h2Server);
        h3TestServerWithRetry.addHandler(h3Handler, "/h3/testH3Retry/");
        h3URIRetry = "https://" + h3TestServerWithRetry.serverAuthority() + "/h3/testH3Retry/x";

        // A HTTP2 server with H3 enabled on a different host:port than the HTTP2 server
        // TLS server rejects X25519 and secp256r1 key shares,
        // which forces a hello retry at the moment of writing this test.
        h3TestServerWithTLSHelloRetry = HttpTestServer.create(HTTP_3, sslContext);
        h3TestServerWithTLSHelloRetry.addHandler(h3Handler, "/h3/testH3tlsretry/");
        h3TestServerWithTLSHelloRetry.getH3AltService().get().getQuicServer().setRejectKeyAgreement(Set.of("x25519", "secp256r1"));
        h3URITLSHelloRetry = "https://" + h3TestServerWithTLSHelloRetry.serverAuthority() + "/h3/testH3tlsretry/x";
        assertTrue(h3TestServerWithTLSHelloRetry.canHandle(HTTP_2, Version.HTTP_3), "Server was expected" +
                " to handle both HTTP2 and HTTP3, but doesn't");

        https2TestServer.start();
        h3TestServer.start();
        h3qv2TestServer.start();
        h3qv2CTestServer.start();
        h3mtlsTestServer.start();
        h3TestServerWithRetry.start();
        h3TestServerWithTLSHelloRetry.start();
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
        AssertionError fail = TRACKER.check(500);
        try {
            https2TestServer.stop();
            h3TestServer.stop();
            h3qv2CTestServer.stop();
            h3qv2TestServer.stop();
            h3mtlsTestServer.stop();
            h3TestServerWithRetry.stop();
            h3TestServerWithTLSHelloRetry.stop();
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
                final String query = uri.getQuery();
                if (query != null && query.contains("handlerShouldThrow=true")) {
                    System.err.printf("intentionally throwing an exception for request %s\n", uri);
                    throw new RuntimeException("intentionally thrown by handler for request " + uri);
                }
                try (InputStream is = t.getRequestBody()) {
                    is.readAllBytes();
                }
                if ((invocation++ % 2) == 1) {
                    System.err.printf("Server sending %d - chunked\n", 200);
                    t.sendResponseHeaders(200, -1);
                    OutputStream os = t.getResponseBody();
                    os.close();
                } else {
                    System.err.printf("Server sending %d - 0 length\n", 200);
                    t.sendResponseHeaders(200, 0);
                }
            } catch (Throwable e) {
                e.printStackTrace(System.err);
                throw new IOException(e);
            }
        }
    }
}
