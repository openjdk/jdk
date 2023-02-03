/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jdk.internal.net.http.common.OperationTrackers.Tracker;
import jdk.test.lib.RandomFactory;
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;

import static java.lang.System.arraycopy;
import static java.lang.System.out;
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
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;

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

    private HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        var client = HttpClient.newBuilder()
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
                client = newHttpClient(sameClient);
            var tracker = TRACKER.getTracker(client);

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
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
                client = newHttpClient(sameClient);
            var tracker = TRACKER.getTracker(client);

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
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
                var error = TRACKER.check(tracker, 1,
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
        InetSocketAddress sa = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        httpTestServer = HttpTestServer.of(HttpServer.create(sa, 0));
        httpTestServer.addHandler(h1_chunkHandler, "/http1/x/");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1/x/";

        HttpsServer httpsServer = HttpsServer.create(sa, 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        httpsTestServer = HttpTestServer.of(httpsServer);
        httpsTestServer.addHandler(h1_chunkHandler, "/https1/x/");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1/x/";

        // HTTP/2
        HttpTestHandler h2_chunkedHandler = new HTTPSlowHandler();

        http2TestServer = HttpTestServer.of(new Http2TestServer("localhost", false, 0));
        http2TestServer.addHandler(h2_chunkedHandler, "/http2/x/");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/x/";

        https2TestServer = HttpTestServer.of(new Http2TestServer("localhost", true, sslContext));
        https2TestServer.addHandler(h2_chunkedHandler, "/https2/x/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/x/";

        serverCount.addAndGet(4);
        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
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
