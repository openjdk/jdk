/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.test.lib.net.SimpleSSLContext;

import javax.net.ssl.SSLContext;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
 *        jdk.httpclient.test.lib.quic.QuicStandaloneServer
 * @run junit/othervm/timeout=480 -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.HttpClient.log=requests,responses,errors
 *                     -Djavax.net.debug=all
 *                     H3DataLimitsTest
 * @summary Verify handling of MAX_DATA / MAX_STREAM_DATA frames
 */
public class H3DataLimitsTest implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private static HttpTestServer h3TestServer;
    private static String h3URI;

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

    public static Object[][] versions() {
        return new Object[][] {{h3URI}};
    }

    private HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        HttpClient client = newClientBuilderForH3()
                .version(Version.HTTP_3)
                .proxy(HttpClient.Builder.NO_PROXY)
                .executor(executor)
                .sslContext(sslContext)
                .build();
        return client;
    }

    @ParameterizedTest
    @MethodSource("versions")
    public void testHugeResponse(final String h3URI) throws Exception {
        HttpClient client = makeNewClient();
        URI uri = URI.create(h3URI + "?16000000");
        Builder builder = HttpRequest.newBuilder(uri)
                .version(HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                .GET();
        HttpRequest request = builder.build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        out.println("Response #1: " + response);
        out.println("Version  #1: " + response.version());
        assertEquals(200, response.statusCode(), "first response status");
        assertEquals(HTTP_3, response.version(), "first response version");

        response = client.send(request, BodyHandlers.ofString());
        out.println("Response #2: " + response);
        out.println("Version  #2: " + response.version());
        assertEquals(200, response.statusCode(), "second response status");
        assertEquals(HTTP_3, response.version(), "second response version");
    }

    @ParameterizedTest
    @MethodSource("versions")
    public void testManySmallResponses(final String h3URI) throws Exception {
        HttpClient client = makeNewClient();
        URI uri = URI.create(h3URI + "?160000");
        Builder builder = HttpRequest.newBuilder(uri)
                .version(HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                .GET();
        HttpRequest request = builder.build();
        for (int i=0; i<102; i++) { // more than 100 to exercise MAX_STREAMS
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            out.println("Response #" + i + ": " + response);
            out.println("Version  #" + i + ": " + response.version());
            assertEquals(200, response.statusCode(), "response status");
            assertEquals(HTTP_3, response.version(), "response version");
        }
    }

    @BeforeAll
    public static void setup() throws Exception {
        // An HTTP/3 server that only supports HTTP/3
        h3TestServer = HttpTestServer.of(new Http3TestServer(sslContext));
        final HttpTestHandler h3Handler = new Handler();
        h3TestServer.addHandler(h3Handler, "/h3/testH3/");
        h3URI = "https://" + h3TestServer.serverAuthority() + "/h3/testH3/x";

        serverCount.addAndGet(1);
        h3TestServer.start();
    }

    @AfterAll
    public static void teardown() throws Exception {
        System.err.println("=======================================================");
        System.err.println("               Tearing down test");
        System.err.println("=======================================================");
        h3TestServer.stop();
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
                try (InputStream is = t.getRequestBody()) {
                    is.readAllBytes();
                }
                System.out.println("Query: "+uri.getQuery());
                int bytesToProduce = Integer.parseInt(uri.getQuery());
                if ((invocation++ % 2) == 1) {
                    System.err.printf("Server sending %d - chunked\n", 200);
                    t.sendResponseHeaders(200, -1);
                } else {
                    System.err.printf("Server sending %d - 0 length\n", 200);
                    t.sendResponseHeaders(200, bytesToProduce);
                }
                try (OutputStream os = t.getResponseBody()) {
                    os.write(new byte[bytesToProduce]);
                }
            } catch (Throwable e) {
                e.printStackTrace(System.err);
                throw new IOException(e);
            }
        }
    }
}
