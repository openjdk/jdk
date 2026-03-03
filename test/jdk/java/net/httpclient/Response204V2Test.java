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
 * @bug 8238270
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *       ReferenceTracker jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run junit/othervm -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.HttpClient.log=requests,responses,errors
 *                     Response204V2Test
 * @summary Tests that streams are closed after receiving a 204 response.
 *          This test uses the OperationsTracker and will fail in
 *          teardown if the tracker reports that some HTTP/2 streams
 *          are still open.
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import jdk.httpclient.test.lib.common.HttpServerAdapters;

import jdk.test.lib.net.SimpleSSLContext;

import javax.net.ssl.SSLContext;

import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class Response204V2Test implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private static HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    private static HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    private static HttpTestServer http3TestServer;   // HTTP/3 ( h3  )
    private static String http2URI;
    private static String https2URI;
    private static String http3URI;

    static final int RESPONSE_CODE = 204;
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
    static final void printFailedTests() {
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

    private HttpClient makeNewClient(HttpClient.Builder builder) {
        clientCount.incrementAndGet();
        HttpClient client =  builder
                .proxy(HttpClient.Builder.NO_PROXY)
                .executor(executor)
                .sslContext(sslContext)
                .build();
        return TRACKER.track(client);
    }

    HttpClient newHttpClient(String uri, boolean share) {
        if (!share) return makeNewClient(newClientBuilderForH3());
        HttpClient shared = sharedClient;
        if (shared != null) return shared;
        synchronized (this) {
            shared = sharedClient;
            if (shared == null) {
                var builder = uri.contains("/http3/")
                        ? newClientBuilderForH3()
                        : HttpClient.newBuilder();
                shared = sharedClient = makeNewClient(builder);
            }
            return shared;
        }
    }

    private HttpRequest.Builder newRequestBuilder(URI uri) {
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
        out.printf("%n%s-- test sameClient=%s, uri=%s%n%n", now(), sameClient, uri);

        HttpClient client = newHttpClient(uri, sameClient);

        HttpRequest request = newRequestBuilder(URI.create(uri))
                .GET()
                .build();
        for (int i = 0; i < ITERATION_COUNT; i++) {
            out.println("Iteration: " + i);
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            int expectedResponse =  RESPONSE_CODE;
            if (response.statusCode() != expectedResponse)
                throw new RuntimeException("wrong response code " + response.statusCode());
        }
        if (!sameClient) {
            out.println("test: closing test client");
            client.close();
        }
        out.println("test: DONE");
    }

    @BeforeAll
    public static void setup() throws Exception {
        // HTTP/2
        HttpTestHandler handler204 = new Handler204();

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(handler204, "/http2/test204/");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/test204/x";

        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(handler204, "/https2/test204/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/test204/x";

        http3TestServer = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        http3TestServer.addHandler(handler204, "/http3/test204/");
        http3URI = "https://" + http3TestServer.serverAuthority() + "/http3/test204/x";

        serverCount.addAndGet(3);
        http2TestServer.start();
        https2TestServer.start();
        http3TestServer.start();
    }

    @AfterAll
    public static void teardown() throws Exception {
        String sharedClientName =
                sharedClient == null ? null : sharedClient.toString();
        sharedClient = null;
        Thread.sleep(100);
        AssertionError fail = TRACKER.check(5000);
        try {
            http2TestServer.stop();
            https2TestServer.stop();
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

    static class Handler204 implements HttpTestHandler {

        public Handler204() {}

        volatile int invocation = 0;

        @Override
        public void handle(HttpTestExchange t)
                throws IOException {
            try {
                URI uri = t.getRequestURI();
                System.err.printf("Handler received request for %s\n", uri);
                String type = uri.getScheme().toLowerCase();
                InputStream is = t.getRequestBody();
                while (is.read() != -1);
                is.close();


                if ((invocation++ % 2) == 1) {
                    System.err.printf("Server sending %d - chunked\n", RESPONSE_CODE);
                    t.sendResponseHeaders(RESPONSE_CODE, -1);
                    OutputStream os = t.getResponseBody();
                    os.close();
                } else {
                    System.err.printf("Server sending %d - 0 length\n", RESPONSE_CODE);
                    t.sendResponseHeaders(RESPONSE_CODE, 0);
                }
            } catch (Throwable e) {
                e.printStackTrace(System.err);
                throw new IOException(e);
            }
        }
    }
}
