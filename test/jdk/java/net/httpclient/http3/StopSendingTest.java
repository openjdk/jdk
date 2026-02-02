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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.internal.net.http.ResponseSubscribers;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;

/*
 * @test
 * @summary Exercises the HTTP3 client to send a STOP_SENDING frame
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @compile ../ReferenceTracker.java
 * @run testng/othervm -Djdk.internal.httpclient.debug=true
 *             -Djdk.httpclient.HttpClient.log=requests,responses,errors StopSendingTest
 */
public class StopSendingTest implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private HttpTestServer h3Server;
    private String requestURIBase;

    @BeforeClass
    public void beforeClass() throws Exception {
        h3Server = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        h3Server.addHandler(new Handler(), "/hello");
        h3Server.start();
        System.out.println("Server started at " + h3Server.getAddress());
        requestURIBase = URIBuilder.newBuilder().scheme("https").loopback()
                .port(h3Server.getAddress().getPort()).build().toString();

    }

    @AfterClass
    public void afterClass() throws Exception {
        if (h3Server != null) {
            System.out.println("Stopping server " + h3Server.getAddress());
            h3Server.stop();
        }
    }

    private static final class Handler implements HttpTestHandler {
        private static final byte[] DUMMY_BODY = "foo bar hello world".getBytes(StandardCharsets.UTF_8);
        private static volatile boolean stop;
        private static final CountDownLatch stopped = new CountDownLatch(1);

        /**
         * Keeps writing out response data (bytes) until asked to stop
         */
        @Override
        public void handle(final HttpTestExchange exchange) throws IOException {
            System.out.println("Handling request: " + exchange.getRequestURI());
            exchange.sendResponseHeaders(200, -1);
            try (final OutputStream os = exchange.getResponseBody()) {
                while (!stop) {
                    os.write(DUMMY_BODY);
                    os.flush();
                    System.out.println("Wrote response data of size " + DUMMY_BODY.length);
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
                System.out.println("Stopped writing response");
            } catch (IOException io) {
                System.out.println("Got expected exception: " + io);
            } finally {
                stopped.countDown();
            }
        }
    }

    /**
     * Issues a HTTP3 request to a server handler which keeps sending data. When some amount of
     * data is received on the client side, the request is cancelled by the test method. This
     * internally is expected to trigger a STOP_SENDING frame from the HTTP client to the server.
     */
    @Test
    public void testStopSending() throws Exception {
        HttpClient client = newClientBuilderForH3()
                .version(Version.HTTP_3)
                .sslContext(sslContext).build();
        final URI reqURI = new URI(requestURIBase + "/hello");
        final HttpRequest req = HttpRequest.newBuilder(reqURI)
                .version(Version.HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                .build();
        // used to wait and trigger a request cancellation
        final CountDownLatch cancellationTrigger = new CountDownLatch(1);
        System.out.println("Issuing request to " + reqURI);
        final CompletableFuture<HttpResponse<Void>> futureResp = client.sendAsync(req,
                BodyHandlers.fromSubscriber(new CustomBodySubscriber(cancellationTrigger)));
        // wait for the subscriber to receive some amount of response data before we trigger
        // the request cancellation
        System.out.println("Awaiting some response data to arrive");
        cancellationTrigger.await();
        System.out.println("Cancelling request");
        // cancel the request which will internal trigger a STOP_SENDING frame from the HTTP
        // client to the server
        final boolean cancelled = futureResp.cancel(true);
        System.out.println("Cancelled request: " + cancelled);
        try {
            // we expect a CancellationException for a cancelled request,
            // but due to a bug (race condition) in the HttpClient's implementation
            // of the Future instance, sometimes the Future.cancel(true) results
            // in an ExecutionException which wraps the CancellationException.
            // TODO: fix the actual race condition and then expect only CancellationException here
            final Exception actualException = Assert.expectThrows(Exception.class, futureResp::get);
            if (actualException instanceof CancellationException) {
                // expected
                System.out.println("Received the expected CancellationException");
            } else if (actualException instanceof ExecutionException
                    && actualException.getCause() instanceof CancellationException) {
                System.out.println("Received CancellationException wrapped as ExecutionException");
            } else {
                // unexpected
                throw actualException;
            }
        } catch (Exception | Error e) {
            Handler.stop = true;
            System.err.println("Unexpected exception: " + e);
            e.printStackTrace();
            throw e;
        } finally {
            // wait until the handler stops sending
            Handler.stopped.await(10, TimeUnit.SECONDS);
        }
        var TRACKER = ReferenceTracker.INSTANCE;
        var tracker = TRACKER.getTracker(client);
        client = null;
        System.gc();
        var error = TRACKER.check(tracker,1000);
        if (error != null) throw error;
    }

    /**
     * A {@link java.net.http.HttpResponse.BodySubscriber} which informs any interested parties
     * whenever it receives any data in {@link #onNext(List)}
     */
    private static final class CustomBodySubscriber extends ResponseSubscribers.ByteArraySubscriber<byte[]> {
        // the latch used to inform any interested parties about data being received
        private final CountDownLatch latch;

        private CustomBodySubscriber(final CountDownLatch latch) {
            // a finisher which just returns the bytes back
            super((bytes) -> bytes);
            this.latch = latch;
        }

        @Override
        public void onNext(final List<ByteBuffer> items) {
            super.onNext(items);
            long totalSize = 0;
            for (final ByteBuffer bb : items) {
                totalSize += bb.remaining();
            }
            System.out.println("Subscriber got response data of size " + totalSize);
            // inform interested party that we received some data
            latch.countDown();
        }
    }
}
