/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Checks correct handling of Publishers that call onComplete without demand
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm CustomRequestPublisher
 */

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient.Builder;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class CustomRequestPublisher implements HttpServerAdapters {

    SSLContext sslContext;
    HttpTestServer httpTestServer;    // HTTP/1.1        [ 4 servers ]
    HttpTestServer httpsTestServer;   // HTTPS/1.1
    HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    HttpTestServer http3TestServer;   // HTTP/3 ( h3  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;
    String http3URI;

    @DataProvider(name = "variants")
    public Object[][] variants() {
        Supplier<BodyPublisher> fixedSupplier   = () -> new FixedLengthBodyPublisher();
        Supplier<BodyPublisher> unknownSupplier = () -> new UnknownLengthBodyPublisher();

        return new Object[][]{
                { httpURI,   fixedSupplier,   false },
                { httpURI,   unknownSupplier, false },
                { httpsURI,  fixedSupplier,   false },
                { httpsURI,  unknownSupplier, false },
                { http2URI,  fixedSupplier,   false },
                { http2URI,  unknownSupplier, false },
                { https2URI, fixedSupplier,   false,},
                { https2URI, unknownSupplier, false },

                { httpURI,   fixedSupplier,   true },
                { httpURI,   unknownSupplier, true },
                { httpsURI,  fixedSupplier,   true },
                { httpsURI,  unknownSupplier, true },
                { http2URI,  fixedSupplier,   true },
                { http2URI,  unknownSupplier, true },
                { https2URI, fixedSupplier,   true,},
                { https2URI, unknownSupplier, true },
                { http3URI,  fixedSupplier,   true,},  // always use same client with h3
                { http3URI,  unknownSupplier, true },  // always use same client with h3
        };
    }

    static final int ITERATION_COUNT = 10;

    /** Asserts HTTP Version, and SSLSession presence when applicable. */
    static void assertVersionAndSession(int step, HttpResponse response, String uri) {
        if (uri.contains("http2") || uri.contains("https2")) {
            assertEquals(response.version(), HTTP_2);
        } else if (uri.contains("http1") || uri.contains("https1")) {
            assertEquals(response.version(), HTTP_1_1);
        } else if (uri.contains("http3")) {
            if (step == 0) assertNotEquals(response.version(), HTTP_1_1);
            else assertEquals(response.version(), HTTP_3,
                    "unexpected response version on step " + step);
        } else {
            fail("Unknown HTTP version in test for: " + uri);
        }

        Optional<SSLSession> ssl = response.sslSession();
        if (uri.contains("https")) {
            assertTrue(ssl.isPresent(),
                    "Expected optional containing SSLSession but got:" + ssl);
            try {
                ssl.get().invalidate();
                fail("SSLSession is not immutable: " + ssl.get());
            } catch (UnsupportedOperationException expected) { }
        } else {
            assertTrue(!ssl.isPresent(), "UNEXPECTED non-empty optional:" + ssl);
        }
    }

    HttpClient.Builder newHttpClientBuilder(String uri) {
        HttpClient.Builder builder;
        if (uri.contains("/http3/")) {
            builder = newClientBuilderForH3();
            // ensure that the preferred version for the client
            // is HTTP/3
            builder.version(HTTP_3);
        } else builder = HttpClient.newBuilder();
        return builder.proxy(Builder.NO_PROXY);
    }

    HttpRequest.Builder newHttpRequestBuilder(String uri) {
        var builder = HttpRequest.newBuilder(URI.create(uri));
        if (uri.contains("/http3/") && !http3TestServer.supportsH3DirectConnection()) {
            // Ensure we don't attempt to connect to a
            // potentially different server if HTTP/3 endpoint and
            // HTTP/2 endpoint are not on the same port
            builder.setOption(H3_DISCOVERY, http3TestServer.h3DiscoveryConfig());
        }
        return builder;
    }

    @Test(dataProvider = "variants")
    void test(String uri, Supplier<BodyPublisher> bpSupplier, boolean sameClient)
            throws Exception
    {
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClientBuilder(uri).sslContext(sslContext).build();

            BodyPublisher bodyPublisher = bpSupplier.get();
            HttpRequest request = newHttpRequestBuilder(uri)
                    .POST(bodyPublisher)
                    .build();

            HttpResponse<String> resp = client.send(request, ofString());

            out.println("Got response: " + resp);
            out.println("Got body: " + resp.body());
            assertTrue(resp.statusCode() == 200,
                    "Expected 200, got:" + resp.statusCode());
            assertEquals(resp.body(), bodyPublisher.bodyAsString());

            assertVersionAndSession(i, resp, uri);
        }
    }

    @Test(dataProvider = "variants")
    void testAsync(String uri, Supplier<BodyPublisher> bpSupplier, boolean sameClient)
            throws Exception
    {
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClientBuilder(uri).sslContext(sslContext).build();

            BodyPublisher bodyPublisher = bpSupplier.get();
            HttpRequest request = newHttpRequestBuilder(uri)
                    .POST(bodyPublisher)
                    .build();

            CompletableFuture<HttpResponse<String>> cf = client.sendAsync(request, ofString());
            HttpResponse<String> resp = cf.get();

            out.println("Got response: " + resp);
            out.println("Got body: " + resp.body());
            assertTrue(resp.statusCode() == 200,
                    "Expected 200, got:" + resp.statusCode());
            assertEquals(resp.body(), bodyPublisher.bodyAsString());

            assertVersionAndSession(0, resp, uri);
        }
    }

    /** A Publisher that returns an UNKNOWN content length. */
    static class UnknownLengthBodyPublisher extends BodyPublisher {
        @Override
        public long contentLength() {
            return -1;  // unknown
        }
    }

    /** A Publisher that returns a FIXED content length. */
    static class FixedLengthBodyPublisher extends BodyPublisher {
        final int LENGTH = Arrays.stream(BODY)
                .mapToInt(s-> s.getBytes(US_ASCII).length)
                .sum();
        @Override
        public long contentLength() {
            return LENGTH;
        }
    }

    /**
     * A Publisher that ( quite correctly ) invokes onComplete, after the last
     * item has been published, even without any outstanding demand.
     */
    static abstract class BodyPublisher implements HttpRequest.BodyPublisher {

        String[] BODY = new String[]
                { "Say ", "Hello ", "To ", "My ", "Little ", "Friend" };

        protected volatile Flow.Subscriber subscriber;

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.subscriber = subscriber;
            subscriber.onSubscribe(new InternalSubscription());
        }

        @Override
        public abstract long contentLength();

        String bodyAsString() {
            return Arrays.stream(BODY).collect(Collectors.joining());
        }

        class InternalSubscription implements Flow.Subscription {

            private final AtomicLong demand = new AtomicLong();
            private final AtomicBoolean cancelled = new AtomicBoolean();
            private volatile int position;

            private static final int IDLE    =  1;
            private static final int PUSHING =  2;
            private static final int AGAIN   =  4;
            private final AtomicInteger state = new AtomicInteger(IDLE);

            @Override
            public void request(long n) {
                if (n <= 0L) {
                    subscriber.onError(new IllegalArgumentException(
                            "non-positive subscription request"));
                    return;
                }
                if (cancelled.get()) {
                    return;
                }

                while (true) {
                    long prev = demand.get(), d;
                    if ((d = prev + n) < prev) // saturate
                        d = Long.MAX_VALUE;
                    if (demand.compareAndSet(prev, d))
                        break;
                }

                while (true) {
                    int s = state.get();
                    if (s == IDLE) {
                        if (state.compareAndSet(IDLE, PUSHING)) {
                            while (true) {
                                push();
                                if (state.compareAndSet(PUSHING, IDLE))
                                    return;
                                else if (state.compareAndSet(AGAIN, PUSHING))
                                    continue;
                            }
                        }
                    } else if (s == PUSHING) {
                        if (state.compareAndSet(PUSHING, AGAIN))
                            return;
                    } else if (s == AGAIN){
                        // do nothing, the pusher will already rerun
                        return;
                    } else {
                        throw new AssertionError("Unknown state:" + s);
                    }
                }
            }

            private void push() {
                long prev;
                while ((prev = demand.get()) > 0) {
                    if (!demand.compareAndSet(prev, prev -1))
                        continue;

                    int index = position;
                    if (index < BODY.length) {
                        position++;
                        subscriber.onNext(ByteBuffer.wrap(BODY[index].getBytes(US_ASCII)));
                    }
                }

                if (position == BODY.length && !cancelled.get()) {
                    cancelled.set(true);
                    subscriber.onComplete();  // NOTE: onComplete without demand
                }
            }

            @Override
            public void cancel() {
                if (cancelled.compareAndExchange(false, true))
                    return;  // already cancelled
            }
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        InetSocketAddress sa = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(new HttpTestEchoHandler(), "/http1/echo");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1/echo";

        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(new HttpTestEchoHandler(), "/https1/echo");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1/echo";

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(new HttpTestEchoHandler(), "/http2/echo");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/echo";

        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(new HttpTestEchoHandler(), "/https2/echo");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/echo";

        http3TestServer = HttpTestServer.create(HTTP_3, sslContext);
        http3TestServer.addHandler(new HttpTestEchoHandler(), "/http3/echo");
        http3URI = "https://" + http3TestServer.serverAuthority() + "/http3/echo";

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        http3TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        httpTestServer.stop();
        httpsTestServer.stop();
        http2TestServer.stop();
        https2TestServer.stop();
        http3TestServer.stop();
    }

}
