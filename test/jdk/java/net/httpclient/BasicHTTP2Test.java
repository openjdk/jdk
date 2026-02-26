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
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 *        ReferenceTracker
 * @run junit/othervm -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.HttpClient.log=requests,responses,errors
 *                     BasicHTTP2Test
 * @summary Basic HTTP/2 test when HTTP/3 is requested
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
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
import javax.net.ssl.SSLContext;

import jdk.test.lib.net.SimpleSSLContext;
import jdk.httpclient.test.lib.common.HttpServerAdapters;

import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.H3_DISCOVERY;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;

public class BasicHTTP2Test implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private static HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    private static String https2URI;
    private static DatagramSocket udp;

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

    private HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        HttpClient client =  HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .executor(executor)
                .sslContext(sslContext)
                .build();
        return TRACKER.track(client);
    }

    @Test
    public void testH2() throws Exception {

        System.err.println("XXXXX ====== xxxxx first xxxxx ====== XXXXX");
        HttpClient client = makeNewClient();
        URI uri = URI.create(https2URI);
        Builder builder = HttpRequest.newBuilder(uri)
                .version(Version.HTTP_3)
                .GET();
        if (udp == null) {
            out.println("Using config " + ALT_SVC);
            builder.setOption(H3_DISCOVERY, ALT_SVC);
        }
        HttpRequest request = builder.build();

        // send first request: that will go through regular HTTP/2.
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        out.println("Response #1: " + response);
        out.println("Version  #1: " + response.version());
        assertEquals(200, response.statusCode(), "first response status");
        assertEquals(HTTP_2, response.version(), "first response version");

        Thread.sleep(1000);

        // send second request: we still will not find an endpoint in the
        //     AltServicesRegistry and will send everything to an
        //     HTTP/2 connection
        System.err.println("XXXXX ====== xxxxx second xxxxx ====== XXXXX");
        response = client.send(request, BodyHandlers.ofString());
        out.println("Response #2: " + response);
        out.println("Version  #2: " + response.version());
        assertEquals(200, response.statusCode(), "second response status");
        assertEquals(HTTP_2, response.version(), "second response version");

    }

    @BeforeAll
    public static void setup() throws Exception {
        // HTTP/2
        HttpTestHandler handler = new Handler();

        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(handler, "/https2/test204/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/test204/x";
        try {
            // attempt to prevent any other server/process to use this port
            // for UDP
            udp = new DatagramSocket(https2TestServer.getAddress());
        } catch (Exception x) {
            System.out.println("Failed to allocate UDP socket at: " + https2TestServer.getAddress());
            udp = null;
        }

        serverCount.addAndGet(1);
        https2TestServer.start();
    }

    @AfterAll
    public static void teardown() throws Exception {
        Thread.sleep(100);
        AssertionError fail = TRACKER.check(500);
        try {
            if (udp != null) udp.close();
            https2TestServer.stop();
        } finally {
            if (fail != null) {
                 throw fail;
            }
        }
    }

    static class Handler implements HttpTestHandler {

        public Handler() {}

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
