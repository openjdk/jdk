/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm -Djdk.internal.httpclient.debug=true
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
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;

import jdk.test.lib.net.SimpleSSLContext;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class BasicHTTP2Test implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    String https2URI;
    DatagramSocket udp;

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

    @BeforeMethod
    void beforeMethod(ITestContext context) {
        if (stopAfterFirstFailure() && context.getFailedTests().size() > 0) {
            var x = new SkipException("Skipping: some test failed");
            x.setStackTrace(new StackTraceElement[0]);
            throw x;
        }
    }

    @AfterClass
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

    private String[] uris() {
        return new String[] {
                https2URI,
        };
    }

    static AtomicLong URICOUNT = new AtomicLong();

    private HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        HttpClient client =  HttpClient.newBuilder()
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


    static void checkStatus(int expected, int found) throws Exception {
        if (expected != found) {
            System.err.printf ("Test failed: wrong status code %d/%d\n",
                expected, found);
            throw new RuntimeException("Test failed");
        }
    }

    static void checkStrings(String expected, String found) throws Exception {
        if (!expected.equals(found)) {
            System.err.printf ("Test failed: wrong string %s/%s\n",
                expected, found);
            throw new RuntimeException("Test failed");
        }
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
        assertEquals(response.statusCode(), 200, "first response status");
        assertEquals(response.version(), HTTP_2, "first response version");

        Thread.sleep(1000);

        // send second request: we still will not find an endpoint in the
        //     AltServicesRegistry and will send everything to an
        //     HTTP/2 connection
        System.err.println("XXXXX ====== xxxxx second xxxxx ====== XXXXX");
        response = client.send(request, BodyHandlers.ofString());
        out.println("Response #2: " + response);
        out.println("Version  #2: " + response.version());
        assertEquals(response.statusCode(), 200, "second response status");
        assertEquals(response.version(), HTTP_2, "second response version");

    }

    @BeforeTest
    public void setup() throws Exception {
        // HTTP/2
        HttpTestHandler handler = new Handler();
        HttpTestHandler h3Handler = new Handler();

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

    @AfterTest
    public void teardown() throws Exception {
        String sharedClientName =
                sharedClient == null ? null : sharedClient.toString();
        sharedClient = null;
        Thread.sleep(100);
        AssertionError fail = TRACKER.check(500);
        try {
            if (udp != null) udp.close();
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
