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

/*
 * @test
 * @bug 8252374
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters jdk.test.lib.net.SimpleSSLContext
 *       ReferenceTracker
 * @run junit/othervm -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.HttpClient.log=requests,responses,errors
 *                     ISO_8859_1_Test
 * @summary Tests that a client is able to receive ISO-8859-1 encoded header values.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;

import jdk.test.lib.net.SimpleSSLContext;

import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ISO_8859_1_Test implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private static DummyServer http1DummyServer;
    private static HttpTestServer http1TestServer;   // HTTP/1.1 ( http )
    private static HttpTestServer https1TestServer;  // HTTPS/1.1 ( https  )
    private static HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    private static HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    private static HttpTestServer http3TestServer;   // HTTP/3 ( h3  )
    private static String http1Dummy;
    private static String http1URI;
    private static String https1URI;
    private static String http2URI;
    private static String https2URI;
    private static String http3URI;

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
    private static volatile HttpClient sharedClient;

    static class TestExecutor implements Executor {
        final AtomicLong tasks = new AtomicLong();
        Executor executor;
        TestExecutor(Executor executor) {
            this.executor = executor;
        }

        @Override
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
                http3URI,
                http1Dummy,
                http1URI,
                https1URI,
                http2URI,
                https2URI,
        };
    }

    public static Object[][] variants() {
        String[] uris = uris();
        Object[][] result = new Object[uris.length * 2][];
        int i = 0;
        for (boolean sameClient : List.of(false, true)) {
            for (String uri : uris()) {
                result[i++] = new Object[]{uri, sameClient};
            }
        }
        assert i == uris.length * 2;
        return result;
    }

    private HttpClient makeNewClient(Version version) {
        clientCount.incrementAndGet();
        var builder = version == HTTP_3
                ? newClientBuilderForH3()
                : HttpClient.newBuilder();
        HttpClient client = builder
                .proxy(HttpClient.Builder.NO_PROXY)
                .executor(executor)
                .sslContext(sslContext)
                .build();
        return TRACKER.track(client);
    }

    Version version(String uri) {
        if (uri == null) return null;
        if (uri.contains("/http1/")) return HTTP_1_1;
        if (uri.contains("/https1/")) return HTTP_1_1;
        if (uri.contains("/http2/")) return HTTP_2;
        if (uri.contains("/https2/")) return HTTP_2;
        if (uri.contains("/http3/")) return HTTP_3;
        return null;
    }

    HttpClient newHttpClient(String uri, boolean share) {
        if (!share) return makeNewClient(version(uri));
        HttpClient shared = sharedClient;
        if (shared != null) return shared;
        synchronized (this) {
            shared = sharedClient;
            if (shared == null) {
                shared = sharedClient = makeNewClient(HTTP_3);
            }
            return shared;
        }
    }

    private static Exception completionCause(CompletionException x) {
        Throwable c = x;
        while (c  instanceof CompletionException
                || c instanceof ExecutionException) {
            if (c.getCause() == null) break;
            c = c.getCause();
        }
        if (c instanceof Error) throw (Error)c;
        return (Exception)c;
    }

    private static HttpRequest.Builder newRequestBuilder(URI uri) {
        var builder = HttpRequest.newBuilder(uri);
        if (uri.getRawPath().contains("/http3/")) {
            builder = builder.version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
        }
        return builder;
    }

    @ParameterizedTest
    @MethodSource("variants")
    public void test(String uri, boolean sameClient) throws Exception {

        System.out.printf("%n%s-- test sameClient=%s uri=%s%n%n", now(), sameClient, uri);

        HttpClient client = newHttpClient(uri, sameClient);

        List<CompletableFuture<HttpResponse<String>>> cfs = new ArrayList<>();
        for (int i = 0; i < ITERATION_COUNT; i++) {
            HttpRequest request = newRequestBuilder(URI.create(uri + "/" + i))
                    .build();
            cfs.add(client.sendAsync(request, BodyHandlers.ofString()));
        }
        try {
            CompletableFuture.allOf(cfs.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException x) {
            throw completionCause(x);
        }
        for (CompletableFuture<HttpResponse<String>> cf : cfs) {
            var response = cf.get();
            System.out.println("Got: " + response);
            var value = response.headers().firstValue("Header8859").orElse(null);
            assertEquals("U\u00ffU", value);
        }
        System.out.println("HttpClient: PASSED");
        if (uri.contains("http1")) {
            System.out.println("Testing with URLConnection");
            var url = URI.create(uri).toURL();
            var conn = url.openConnection();
            conn.connect();
            conn.getInputStream().readAllBytes();
            var value = conn.getHeaderField("Header8859");
            assertEquals("U\u00ffU", value, "legacy stack failed");
            System.out.println("URLConnection: PASSED");
        }
        System.out.println(now() + "test: DONE");
    }

    static final class DummyServer extends Thread implements AutoCloseable {
        String RESP = """
                HTTP/1.1 200 OK\r
                Content-length: 0\r
                Header8859: U\u00ffU\r
                Connection: close\r
                \r
                """;

        static final InetSocketAddress LOOPBACK =
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        final ServerSocket socket;
        final CopyOnWriteArrayList<Socket> accepted = new CopyOnWriteArrayList<>();
        final CompletableFuture<Void> done = new CompletableFuture<>();
        volatile boolean closed;
        DummyServer() throws IOException  {
            socket = new ServerSocket();
            socket.bind(LOOPBACK);
        }

        public String serverAuthority() {
            String address = socket.getInetAddress().getHostAddress();
            if (address.indexOf(':') >= 0) {
                address = "[" + address + "]";
            }
            return address + ":" + socket.getLocalPort();
        }

        public void run() {
            try {
                while (!socket.isClosed()) {
                    try (Socket client = socket.accept()) {
                        accepted.add(client);
                        try {
                            System.out.println("Accepted: " + client);
                            String req = "";
                            BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(client.getInputStream(),
                                            StandardCharsets.ISO_8859_1));
                            String line = null;
                            while (!(line = reader.readLine()).isEmpty()) {
                                System.out.println("Got line: " + line);
                                req = req + line + "\r\n";
                            }
                            System.out.println(req);
                            System.out.println("Sending back " + RESP);
                            client.getOutputStream().write(RESP.getBytes(StandardCharsets.ISO_8859_1));
                            client.getOutputStream().flush();
                        } finally {
                            accepted.remove(client);
                        }
                    }
                }
            } catch (Throwable t) {
                if (closed) {
                    done.complete(null);
                } else {
                    done.completeExceptionally(t);
                }
            } finally {
                done.complete(null);
            }
        }

        void close(AutoCloseable toclose) {
            try { toclose.close(); } catch (Exception x) {};
        }

        public void close() {
            closed = true;
            close(socket);
            accepted.forEach(this::close);
        }
    }

    final static class ISO88591Handler implements HttpServerAdapters.HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            try (HttpTestExchange e = t) {
                t.getRequestBody().readAllBytes();
                t.getResponseHeaders().addHeader("Header8859", "U\u00ffU");
                t.sendResponseHeaders(200, 0);
            }

        }
    }

    @BeforeAll
    public static void setup() throws Exception {
        HttpServerAdapters.HttpTestHandler handler = new ISO88591Handler();
        InetSocketAddress loopback = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

        http1DummyServer = new DummyServer();
        http1Dummy = "http://" + http1DummyServer.serverAuthority() +"/http1/dummy/x";

        http1TestServer = HttpServerAdapters.HttpTestServer.create(HTTP_1_1);
        http1TestServer.addHandler(handler, "/http1/server/");
        http1URI = "http://" + http1TestServer.serverAuthority() + "/http1/server/x";

        https1TestServer = HttpServerAdapters.HttpTestServer.create(HTTP_1_1, sslContext);
        https1TestServer.addHandler(handler, "/https1/server/");
        https1URI = "https://" + https1TestServer.serverAuthority() + "/https1/server/x";

        // HTTP/2
        http2TestServer = HttpServerAdapters.HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(handler, "/http2/server/");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/server/x";

        https2TestServer = HttpServerAdapters.HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(handler, "/https2/server/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/server/x";

        http3TestServer = HttpServerAdapters.HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        http3TestServer.addHandler(handler, "/http3/server/");
        http3URI = "https://" + http3TestServer.serverAuthority() + "/http3/server/x";

        serverCount.addAndGet(6);
        http1TestServer.start();
        https1TestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        http1DummyServer.start();
        http3TestServer.start();
    }

    @AfterAll
    public static void teardown() throws Exception {
        String sharedClientName =
                sharedClient == null ? null : sharedClient.toString();
        sharedClient = null;
        Thread.sleep(100);
        AssertionError fail = TRACKER.check(1500);
        try {
            http1TestServer.stop();
            https1TestServer.stop();
            http2TestServer.stop();
            https2TestServer.stop();
            http1DummyServer.close();
            http3TestServer.stop();
        } finally {
            if (fail != null) {
                if (sharedClientName != null) {
                    System.err.println("Shared client name is: " + sharedClientName);
                }
                throw fail;
            }
        }
    }
}
