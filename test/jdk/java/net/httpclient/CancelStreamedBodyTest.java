/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8294916 8297075 8297149
 * @summary Tests that closing a streaming handler (ofInputStream()/ofLines())
 *      without reading all the bytes unregisters the underlying subscriber.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters jdk.test.lib.net.SimpleSSLContext
 *        ReferenceTracker CancelStreamedBodyTest
 * @run testng/othervm -Djdk.internal.httpclient.debug=true
 *                     CancelStreamedBodyTest
 */
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.httpclient.test.lib.common.HttpServerAdapters;

import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CancelStreamedBodyTest implements HttpServerAdapters {

    SSLContext sslContext;
    HttpTestServer httpTestServer;    // HTTP/1.1    [ 4 servers ]
    HttpTestServer httpsTestServer;   // HTTPS/1.1
    HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    HttpTestServer http3TestServer;   // HTTP/3 ( h3  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;
    String https3URI;

    static final long SERVER_LATENCY = 75;
    static final int ITERATION_COUNT = 3;
    static final long CLIENT_SHUTDOWN_GRACE_DELAY = 1500; // milliseconds
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

    final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;
    private volatile HttpClient sharedClient;

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

    protected boolean stopAfterFirstFailure() {
        return Boolean.getBoolean("jdk.internal.httpclient.debug");
    }

    final AtomicReference<SkipException> skiptests = new AtomicReference<>();
    void checkSkip() {
        var skip = skiptests.get();
        if (skip != null) throw skip;
    }
    static String name(ITestResult result) {
        var params = result.getParameters();
        return result.getName()
                + (params == null ? "()" : Arrays.toString(result.getParameters()));
    }

    @BeforeMethod
    void beforeMethod(ITestContext context) {
        if (stopAfterFirstFailure() && context.getFailedTests().size() > 0) {
            if (skiptests.get() == null) {
                SkipException skip = new SkipException("some tests failed");
                skip.setStackTrace(new StackTraceElement[0]);
                skiptests.compareAndSet(null, skip);
            }
        }
    }

    @AfterClass
    static final void printFailedTests(ITestContext context) {
        out.println("\n=========================");
        var failed = context.getFailedTests().getAllResults().stream()
                .collect(Collectors.toMap(r -> name(r), ITestResult::getThrowable));
        FAILURES.putAll(failed);
        try {
            out.printf("%n%sCreated %d servers and %d clients%n",
                    now(), serverCount.get(), clientCount.get());
            if (FAILURES.isEmpty()) return;
            out.println("Failed tests: ");
            FAILURES.entrySet().forEach((e) -> {
                out.printf("\t%s: %s%n", e.getKey(), e.getValue());
                e.getValue().printStackTrace(out);
            });
            if (tasksFailed) {
                System.out.println("WARNING: Some tasks failed");
            }
        } finally {
            out.println("\n=========================\n");
        }
    }

    private String[] uris() {
        return new String[] {
                https3URI,
                httpURI,
                httpsURI,
                http2URI,
                https2URI,
        };
    }


    @DataProvider(name = "urls")
    public Object[][] alltests() {
        String[] uris = uris();
        Object[][] result = new Object[uris.length * 2][];
        int i = 0;
        for (boolean sameClient : List.of(false, true)) {
            for (String uri : uris()) {
                String path = sameClient ? "same" : "new";
                result[i++] = new Object[]{uri + path, sameClient};
            }
        }
        assert i == uris.length * 2;
        return result;
    }

    private HttpClient makeNewClient(HttpClient.Builder builder) {
        clientCount.incrementAndGet();
        var client = builder
                .proxy(HttpClient.Builder.NO_PROXY)
                .executor(executor)
                .sslContext(sslContext)
                .build();
        // It is OK to even track the shared client here:
        // the test methods will verify that the client has shut down
        // only if it's not the shared client.
        // Only the teardown() method verify that the shared client
        // has shut down in this test.
        return TRACKER.track(client);
    }

    private Version version(String uri) {
        if (uri == null) return null;
        if (uri.contains("/http3/")) return HTTP_3;
        if (uri.contains("/http2/")) return HTTP_2;
        if (uri.contains("/https2/")) return HTTP_2;
        if (uri.contains("/http1/")) return HTTP_1_1;
        if (uri.contains("/https1/")) return HTTP_1_1;
        return null;
    }

    HttpClient makeNewClient(Version version) {
        var builder = (version == HTTP_3)
                ? newClientBuilderForH3()
                : HttpClient.newBuilder();
        return makeNewClient(builder);
    }

    HttpClient newHttpClient(boolean share, String uri) {
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

    HttpRequest.Builder requestBuilder(String uri) {
        var builder = HttpRequest.newBuilder(URI.create(uri));
        var version = version(uri);
        return version == HTTP_3
                ? builder.version(HTTP_3).setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                : builder;
    }

    final static String BODY = "Some string |\n that ?\n can |\n be split ?\n several |\n ways.";

    @Test(dataProvider = "urls")
    public void testAsLines(String uri, boolean sameClient)
            throws Exception {
        checkSkip();
        HttpClient client = null;
        uri = uri + "/testAsLines";
        out.printf("%n%s testAsLines(%s, %b)%n", now(), uri, sameClient);
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient(sameClient, uri);
            var tracker = TRACKER.getTracker(client);

            HttpRequest req = requestBuilder(uri)
                    .GET()
                    .build();
            List<String> lines;
            for (int j = 0; j < 2; j++) {
                try (Stream<String> body = client.send(req, BodyHandlers.ofLines()).body()) {
                    lines = body.limit(j).toList();
                    assertEquals(lines, BODY.replaceAll("\\||\\?", "")
                            .lines().limit(j).toList());
                }
                // Only check our still alive client for outstanding operations
                // and outstanding subscribers here: it should have none.
                var error = TRACKER.check(tracker, 500,
                        (t) -> t.getOutstandingOperations() > 0 || t.getOutstandingSubscribers() > 0,
                        "subscribers for testAsLines(%s)\n\t step [%s,%s]".formatted(req.uri(), i,j),
                        false);
                Reference.reachabilityFence(client);
                if (error != null) throw error;
            }
            // The shared client is only shut down at the end.
            // Skip shutdown check for the shared client.
            if (sameClient) continue;
            client = null;
            System.gc();
            var error = TRACKER.check(tracker, CLIENT_SHUTDOWN_GRACE_DELAY);
            if (error != null) throw error;
        }
    }

    @Test(dataProvider = "urls")
    public void testInputStream(String uri, boolean sameClient)
            throws Exception {
        checkSkip();
        HttpClient client = null;
        uri = uri + "/testInputStream";
        out.printf("%n%s testInputStream(%s, %b)%n", now(), uri, sameClient);
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient(sameClient, uri);
            var tracker = TRACKER.getTracker(client);

            HttpRequest req = requestBuilder(uri)
                    .GET()
                    .build();
            int read = -1;
            for (int j = 0; j < 2; j++) {
                try (InputStream is = client.send(req, BodyHandlers.ofInputStream()).body()) {
                    for (int k = 0; k < j; k++) {
                        read = is.read();
                        assertEquals(read, BODY.charAt(k));
                    }
                }
                // Only check our still alive client for outstanding operations
                // and outstanding subscribers here: it should have none.
                var error = TRACKER.check(tracker, 500,
                        (t) -> t.getOutstandingOperations() > 0 || t.getOutstandingSubscribers() > 0,
                        "subscribers for testInputStream(%s)\n\t step [%s,%s]".formatted(req.uri(), i,j),
                        false);
                Reference.reachabilityFence(client);
                if (error != null) throw error;
            }
            // The shared client is only shut down at the end.
            // Skip shutdown check for the shared client.
            if (sameClient) continue;
            client = null;
            System.gc();
            var error = TRACKER.check(tracker, CLIENT_SHUTDOWN_GRACE_DELAY);
            if (error != null) throw error;
        }
    }



    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        // HTTP/1.1
        HttpTestHandler h1_chunkHandler = new HTTPSlowHandler();
        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(h1_chunkHandler, "/http1/x/");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1/x/";

        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(h1_chunkHandler, "/https1/x/");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1/x/";

        // HTTP/2
        HttpTestHandler h2_chunkedHandler = new HTTPSlowHandler();

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(h2_chunkedHandler, "/http2/x/");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/x/";

        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(h2_chunkedHandler, "/https2/x/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/x/";

        http3TestServer = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        http3TestServer.addHandler(h2_chunkedHandler, "/http3/x/");
        https3URI = "https://" + http3TestServer.serverAuthority() + "/http3/x/";

        serverCount.addAndGet(5);
        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        http3TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        String sharedClientName =
                sharedClient == null ? null : sharedClient.toString();
        sharedClient = null;
        // check that the shared client (and any other client) have
        // properly shut down
        System.gc();
        Thread.sleep(100);
        AssertionError fail = TRACKER.check(500);
        try {
            httpTestServer.stop();
            httpsTestServer.stop();
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

    /**
     * A handler that slowly sends back a body to give time for the
     * the request to get cancelled before the body is fully received.
     */
    static class HTTPSlowHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            try {
                out.println("HTTPSlowHandler received request to " + t.getRequestURI());
                System.err.println("HTTPSlowHandler received request to " + t.getRequestURI());

                byte[] req;
                try (InputStream is = t.getRequestBody()) {
                    req = is.readAllBytes();
                }

                // we're not expecting a request body.
                // if we receive any, pretend we're a teapot.
                int status = req.length == 0 ? 200 : 418;
                t.sendResponseHeaders(status, -1); // chunked/variable
                try (OutputStream os = t.getResponseBody()) {
                    // lets split the response in several chunks...
                    String msg = (req != null && req.length != 0)
                            ? new String(req, UTF_8)
                            : BODY;
                    String[] str = msg.split("\\|");
                    for (var s : str) {
                        req = s.getBytes(UTF_8);
                        os.write(req);
                        os.flush();
                        out.printf("Server wrote %d bytes%n", req.length);
                        try {
                            Thread.sleep(SERVER_LATENCY);
                        } catch (InterruptedException x) {
                            // OK
                        }
                    }
                }
            } catch (Throwable e) {
                out.println("HTTPSlowHandler: unexpected exception: " + e);
                e.printStackTrace();
                throw e;
            } finally {
                out.printf("HTTPSlowHandler reply sent: %s%n", t.getRequestURI());
                System.err.printf("HTTPSlowHandler reply sent: %s%n", t.getRequestURI());
            }
        }
    }

}
