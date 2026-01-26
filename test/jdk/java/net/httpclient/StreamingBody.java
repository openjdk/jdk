/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Exercise a streaming subscriber ( InputStream ) without holding a
 *          strong (or any ) reference to the client.
 * @key randomness
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters jdk.test.lib.net.SimpleSSLContext
 * @run junit/othervm
 *       -Djdk.httpclient.HttpClient.log=trace,headers,requests
 *       ${test.main.class}
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.RandomFactory;
import jdk.test.lib.net.SimpleSSLContext;
import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.net.http.HttpClient.Builder.NO_PROXY;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StreamingBody implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    HttpTestServer httpTestServer;        // HTTP/1.1    [ 4 servers ]
    HttpTestServer httpsTestServer;       // HTTPS/1.1
    HttpTestServer http2TestServer;       // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;      // HTTP/2 ( h2  )
    HttpTestServer http3TestServer;       // HTTP/3 ( h3  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;
    String http3URI;

    static final AtomicLong clientCount = new AtomicLong();
    static final AtomicLong serverCount = new AtomicLong();
    static final ConcurrentMap<String, Throwable> FAILURES = new ConcurrentHashMap<>();
    private static boolean stopAfterFirstFailure() {
        return true;
    }

    static final long start = System.nanoTime();
    public static String now() {
        long now = System.nanoTime() - start;
        long secs = now / 1000_000_000;
        long mill = (now % 1000_000_000) / 1000_000;
        long nan = now % 1000_000;
        return String.format("[%d s, %d ms, %d ns] ", secs, mill, nan);
    }

    final static class TestStopper implements TestWatcher, BeforeEachCallback {
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

    /// The GCTrigger triggers GC at random intervals to
    /// help garbage collecting HttpClient intances. This test
    /// wants to verify that HttpClient instances which are no
    /// longer strongly referenced are not garbage collected
    /// before pending HTTP requests are finished. The test
    /// creates many client instances (up to 500) and relies
    /// on the GC to collect them, since it does not want to
    /// keep a strong reference, and therefore cannot not call
    /// close(). This can put extra load on the machine since
    /// we can't (and don't want to) control when the GC will
    /// intervene. The purpose of this class is to trigger the
    /// GC at random intervals to 1. help garbage collect client
    /// instances earlier, thus reducing the load on the machine,
    /// and 2. potentially trigger bugs if the client gets
    /// inadvertently GC'ed before the request is finished
    /// (which is the bug we're testing for here).
    static class GCTrigger {
        private final long gcinterval;
        private final Thread runner;
        private volatile boolean stop;
        private final static Random RANDOM = RandomFactory.getRandom();

        GCTrigger(long gcinterval) {
            this.gcinterval = Math.clamp(gcinterval, 100, Long.MAX_VALUE/2);
            runner = Thread.ofPlatform().daemon().unstarted(this::loop);
        }

        private void loop() {
            long min = gcinterval / 2;
            long max = gcinterval + min;
            while (!stop) {
                try {
                    Thread.sleep(RANDOM.nextLong(min, max));
                } catch (InterruptedException x) {
                    stop = true;
                    break;
                }
                out.println(now() + "triggering gc");
                System.gc();
            }
        }

        public void start() {
            runner.start();
        }

        public void stop() throws InterruptedException {
            stop = true;
            runner.interrupt();
            runner.join();
        }
    }

    static GCTrigger gcTrigger;
    static final String MESSAGE = "StreamingBody message body";
    static final int ITERATIONS = 100;

    public Object[][] positive() {
        return new Object[][] {
                { http3URI,   },
                { httpURI,    },
                { httpsURI,   },
                { http2URI,   },
                { https2URI,  },
        };
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
    @MethodSource("positive")
    void test(String uriString) throws Exception {
        out.printf("%n%s---- starting (%s) ----%n", now(), uriString);
        URI uri = URI.create(uriString);
        HttpRequest request = newRequestBuilder(uri).build();

        for (int i=0; i< ITERATIONS; i++) {
            try {
                out.println(now() + "iteration: " + i);
                var builder = uriString.contains("/http3/")
                        ? newClientBuilderForH3()
                        : HttpClient.newBuilder();
                clientCount.incrementAndGet();

                // we want to relinquish the reference to the HttpClient facade
                // as soon as possible. We're using `ofInputStream()` because
                // the HttpResponse will be returned almost immediately, before
                // the response is read. Similarly we use sendAsync() because
                // this will return a CompletableFuture and not wait for the
                // request to complete within a method called on the client
                // facade.
                HttpResponse<InputStream> response = builder
                        .sslContext(sslContext)
                        .proxy(NO_PROXY)
                        .build()
                        .sendAsync(request, BodyHandlers.ofInputStream())
                        .join();

                String body = new String(response.body().readAllBytes(), UTF_8);
                out.println("Got response: " + response);
                out.println("Got body Path: " + body);

                assertEquals(200, response.statusCode());
                assertEquals(MESSAGE, body);
            } catch (Throwable t) {
                String msg = "%stest(%s)[%s] failed: %s"
                        .formatted(now(), uriString, i, t);
                out.println(msg);
                throw new AssertionError(msg, t);
            }
        }
    }


    // -- Infrastructure

    @BeforeAll
    public void setup() throws Exception {
        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(new MessageHandler(), "/http1/streamingbody/");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1/streamingbody/w";
        serverCount.incrementAndGet();

        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(new MessageHandler(),"/https1/streamingbody/");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1/streamingbody/x";
        serverCount.incrementAndGet();

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(new MessageHandler(), "/http2/streamingbody/");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/streamingbody/y";
        serverCount.incrementAndGet();

        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(new MessageHandler(), "/https2/streamingbody/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/streamingbody/z";
        serverCount.incrementAndGet();

        http3TestServer = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        http3TestServer.addHandler(new MessageHandler(), "/http3/streamingbody/");
        http3URI = "https://" + http3TestServer.serverAuthority() + "/http3/streamingbody/z";
        serverCount.incrementAndGet();

        gcTrigger = new GCTrigger(500);

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        http3TestServer.start();
        gcTrigger.start();
    }

    @AfterAll
    public void teardown() throws Exception {
        try {
            httpTestServer.stop();
            httpsTestServer.stop();
            http2TestServer.stop();
            https2TestServer.stop();
            http3TestServer.stop();
            gcTrigger.stop();
        } finally {
            printFailedTests();
        }
    }

    static final void printFailedTests() {
        out.println("\n=========================");
        try {
            out.printf("%n%sCreated %s servers and %s clients%n",
                    now(), serverCount.get(), clientCount.get());
            if (FAILURES.isEmpty()) return;
            out.println("Failed tests: ");
            FAILURES.entrySet().forEach((e) -> {
                out.printf("\t%s: %s%n", e.getKey(), e.getValue());
                e.getValue().printStackTrace(out);
            });
        } finally {
            out.println("\n=========================\n");
        }
    }

    static class MessageHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            System.out.println("MessageHandler for: " + t.getRequestURI());
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                is.readAllBytes();
                byte[] bytes = MESSAGE.getBytes(UTF_8);
                t.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }
    }
}
