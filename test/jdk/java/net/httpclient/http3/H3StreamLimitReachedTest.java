/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=with-default-wait
 * @summary this test verifies that the correct connection is
 *          used when a request is retried on a new connection
 *          due to stream limit reached.
 *          The maxBidiStreams limit is artificially set to 1.
 *          This configuration uses the default wait for a stream
 *          to become available before retrying.
 *          Different configurations are tested if possible,
 *          with both HTTP/3 only and HTTP/2 HTTP/3 with altsvc.
 *          In one case the HTTP/3 only server will be listening
 *          on the same port as the HTTP/2 server, with the
 *          HTTP/2 server advertising an AltService on a different
 *          port. In another case the AltService will be on the
 *          same port as the HTTP/2 server and the HTTP/3 server
 *          will be on a different port. In all case, the test
 *          verifies that the right connection is picked up
 *          for the retry.
 * @bug 8372951
 * @comment this test also tests bug 8372951
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.test.lib.Asserts
 *        jdk.test.lib.Utils
 *        jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=ssl,requests,responses,errors,http3,quic:control
 *                     -Djdk.internal.httpclient.debug=false
 *                     -Djdk.internal.httpclient.quic.maxBidiStreams=1
 *                     H3StreamLimitReachedTest
 */

/*
 * @test id=with-no-wait
 * @summary this test verifies that the correct connection is
 *          used when a request is retried on a new connection
 *          due to stream limit reached.
 *          The maxBidiStreams limit is artificially set to 1.
 *          This configuration retries immediately on a new
 *          connection when no stream is available.
 *          Different configurations are tested if possible,
 *          with both HTTP/3 only and HTTP/2 HTTP/3 with altsvc.
 *          In one case the HTTP/3 only server will be listening
 *          on the same port as the HTTP/2 server, with the
 *          HTTP/2 server advertising an AltService on a different
 *          port. In another case the AltService will be on the
 *          same port as the HTTP/2 server and the HTTP/3 server
 *          will be on a different port. In all case, the test
 *          verifies that the right connection is picked up
 *          for the retry.
 * @bug 8372951
 * @comment this test also tests bug 8372951
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.test.lib.Asserts
 *        jdk.test.lib.Utils
 *        jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=ssl,requests,responses,errors,http3,quic:control
 *                     -Djdk.internal.httpclient.debug=false
 *                     -Djdk.internal.httpclient.quic.maxBidiStreams=1
 *                     -Djdk.httpclient.http3.maxStreamLimitTimeout=0
 *                     -Djdk.httpclient.retryOnStreamlimit=9
 *                     H3StreamLimitReachedTest
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpOption;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.Test;

import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertNotEquals;
import static jdk.test.lib.Asserts.assertTrue;
import static org.testng.Assert.assertFalse;

public class H3StreamLimitReachedTest implements HttpServerAdapters {

    private static final String CLASS_NAME = H3StreamLimitReachedTest.class.getSimpleName();

    static int altsvcPort, https2Port, http3Port;
    static Http3TestServer http3OnlyServer;
    static Http2TestServer https2AltSvcServer;
    static volatile HttpClient client = null;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    static volatile String http3OnlyURIString, https2URIString, http3AltSvcURIString, http3DirectURIString;

    static void initialize(boolean samePort) throws Exception {
        BlockingHandler.GATE.drainPermits();
        BlockingHandler.IN_HANDLER.drainPermits();
        BlockingHandler.PERMITS.set(0);
        initialize(samePort, BlockingHandler::new);
    }

    static void initialize(boolean samePort, Supplier<Http2Handler> handlers) throws Exception {
        System.out.println("\nConfiguring for advertised AltSvc on "
                + (samePort ? "same port" : "ephemeral port"));
        try {
            client = null;
            client = getClient();

            // server that supports both HTTP/2 and HTTP/3, with HTTP/3 on an altSvc port.
            https2AltSvcServer = new Http2TestServer(true, sslContext);
            if (samePort) {
                System.out.println("Attempting to enable advertised HTTP/3 service on same port");
                https2AltSvcServer.enableH3AltServiceOnSamePort();
                System.out.println("Advertised AltSvc on same port " +
                        (https2AltSvcServer.supportsH3DirectConnection() ? "enabled" : " not enabled"));
            } else {
                System.out.println("Attempting to enable advertised HTTP/3 service on different port");
                https2AltSvcServer.enableH3AltServiceOnEphemeralPort();
            }
            https2AltSvcServer.addHandler(handlers.get(), "/" + CLASS_NAME + "/https2/");
            https2AltSvcServer.addHandler(handlers.get(), "/" + CLASS_NAME + "/h2h3/");
            https2Port = https2AltSvcServer.getAddress().getPort();
            altsvcPort = https2AltSvcServer.getH3AltService()
                    .map(Http3TestServer::getAddress).stream()
                    .mapToInt(InetSocketAddress::getPort).findFirst()
                    .getAsInt();
            // server that only supports HTTP/3 - we attempt to use the same port
            // as the HTTP/2 server so that we can pretend that the H2 server as two H3 endpoints:
            //   one advertised (the alt service endpoint og the HTTP/2 server)
            //   one non advertised (the direct endpoint, at the same authority as HTTP/2, but which
            //   is in fact our http3OnlyServer)
            try {
                http3OnlyServer = new Http3TestServer(sslContext, samePort ? 0 : https2Port);
                System.out.println("Unadvertised service enabled on "
                        + (samePort ? "ephemeral port" : "same port"));
            } catch (IOException ex) {
                System.out.println("Can't create HTTP/3 server on same port: " + ex);
                http3OnlyServer = new Http3TestServer(sslContext, 0);
            }
            http3OnlyServer.addHandler("/" + CLASS_NAME + "/http3/", handlers.get());
            http3OnlyServer.addHandler("/" + CLASS_NAME + "/h2h3/", handlers.get());
            http3OnlyServer.start();
            http3Port = http3OnlyServer.getQuicServer().getAddress().getPort();

            if (http3Port == https2Port) {
                System.out.println("HTTP/3 server enabled on same port than HTTP/2 server");
                if (samePort) {
                    System.out.println("WARNING: configuration could not be respected," +
                            " should have used ephemeral port for HTTP/3 server");
                }
            } else {
                System.out.println("HTTP/3 server enabled on a different port than HTTP/2 server");
                if (!samePort) {
                    System.out.println("WARNING: configuration could not be respected," +
                            " should have used same port for HTTP/3 server");
                }
            }
            if (altsvcPort == https2Port) {
                if (!samePort) {
                    System.out.println("WARNING: configuration could not be respected," +
                            " should have used same port for advertised AltSvc");
                }
            } else {
                if (samePort) {
                    System.out.println("WARNING: configuration could not be respected," +
                            " should have used ephemeral port for advertised AltSvc");
                }
            }

            http3OnlyURIString = "https://" + http3OnlyServer.serverAuthority() + "/" + CLASS_NAME + "/http3/foo/";
            https2URIString = "https://" + https2AltSvcServer.serverAuthority() + "/" + CLASS_NAME + "/https2/bar/";
            http3DirectURIString = "https://" + https2AltSvcServer.serverAuthority() + "/" + CLASS_NAME + "/h2h3/direct/";
            http3AltSvcURIString = https2URIString
                    .replace(":" + https2Port + "/", ":" + altsvcPort + "/")
                    .replace("/https2/bar/", "/h2h3/altsvc/");
            System.out.println("HTTP/2 server started at: " + https2AltSvcServer.serverAuthority());
            System.out.println(" with advertised HTTP/3 endpoint at: "
                    + URI.create(http3AltSvcURIString).getRawAuthority());
            System.out.println("HTTP/3 server started at:" + http3OnlyServer.serverAuthority());

            https2AltSvcServer.start();
        } catch (Throwable e) {
            System.out.println("Configuration failed: " + e);
            System.err.println("Throwing now: " + e);
            e.printStackTrace();
            throw e;
        }
    }

    static class BlockingHandler implements Http2Handler {
        static final AtomicLong REQCOUNT = new AtomicLong();
        static final Semaphore IN_HANDLER = new Semaphore(0);
        static final Semaphore GATE = new Semaphore(0);
        static final AtomicInteger PERMITS = new AtomicInteger();

        static void acquireGate() throws InterruptedException {
            GATE.acquire();
            System.out.println("GATE acquired: remaining permits: " +
                    PERMITS.decrementAndGet()
                    + " (actual: " + GATE.availablePermits() +")");
        }
        static void releaseGate() throws InterruptedException {
            int permits = PERMITS.incrementAndGet();
            GATE.release();
            System.out.println("GATE released: remaining permits: "
                    + permits + " (actual: " + GATE.availablePermits() +")");
        }

        static void releaseGate(int permits) throws InterruptedException {
            int npermits = PERMITS.addAndGet(permits);
            GATE.release(npermits);
            System.out.println("GATE released (" + permits + "): remaining permits: "
                    + npermits + " (actual: " + GATE.availablePermits() +")");
        }

        @Override
        public void handle(Http2TestExchange t) throws IOException {
            long count = REQCOUNT.incrementAndGet();
            byte[] resp;
            int status;
            try {
                try {
                    IN_HANDLER.release();
                    System.out.printf("*** Server [%s] waiting for GATE: %s%n",
                            count, t.getRequestURI());
                    System.err.printf("*** Server [%s] waiting for GATE: %s%n",
                            count, t.getRequestURI());
                    acquireGate();
                    System.err.printf("*** Server [%s] GATE acquired: %s%n",
                            count, t.getRequestURI());
                    status = 200;
                    resp = "Request %s OK".formatted(count)
                            .getBytes(StandardCharsets.UTF_8);
                } catch (InterruptedException x) {
                    status = 500;
                    resp = "Request %s interrupted: %s"
                            .formatted(count, x)
                            .getBytes(StandardCharsets.UTF_8);
                }
                System.out.printf("*** Server [%s] headers for: %s%n\t%s%n",
                        count, t.getRequestURI(), t.getRequestHeaders());
                System.out.printf("*** Server [%s] reading body for: %s%n",
                        count, t.getRequestURI());
                t.getRequestBody().readAllBytes();
                System.out.printf("*** Server [%s] sending headers for: %s%n",
                        count, t.getRequestURI());
                t.sendResponseHeaders(status, resp.length);
                System.out.printf("*** Server [%s] sending body %s (%s bytes) for: %s%n",
                        count, status, resp.length, t.getRequestURI());
                try (var body = t.getResponseBody()) {
                    body.write(resp);
                }
                System.out.printf("*** Server [%s] response %s sent for: %s%n",
                        count, status, t.getRequestURI());
            } catch (Throwable throwable) {
                var msg = String.format("Server [%s] response failed for: %s",
                        count, t.getRequestURI());
                System.out.printf("*** %s%n\t%s%n", msg, throwable);
                var error = new IOException(msg,throwable);
                error.printStackTrace(System.out);
                System.err.printf("*** %s%n\t%s%n", msg, throwable);
                //GATE.release();
                throw error;
            }
        }
    }

    private static void printResponse(String name,
                                      HttpOption option,
                                      HttpResponse<?> response) {
        printResponse("%s %s".formatted(
                name, response.request().getOption(option)),
                response);
    }

    private static void printResponse(String name, HttpResponse<?> response) {
        System.out.printf("%s: (%s): %s%n",
                name, response.connectionLabel(), response);
        response.headers().map().entrySet().forEach((e) -> {
            System.out.printf("     %s: %s%n", e.getKey(), e.getValue());
        });
        System.out.printf("     :body: \"%s\"%n%n", response.body());
    }

    @Test
    public static void testH3Only() throws Exception {
        System.out.println("\nTesting HTTP/3 only");
        initialize(true);
        try (HttpClient client = getClient()) {
            var reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(http3OnlyURIString))
                    .version(HTTP_3)
                    .GET();
            HttpRequest request1 = reqBuilder.copy()
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .build();
            var responseCF1 = client.sendAsync(request1, BodyHandlers.ofString());
            // wait until blocked in the handler
            BlockingHandler.IN_HANDLER.acquire();

            // ANY should reuse the same connection, get stream limit reached,
            //     and open a new connection
            HttpRequest request2 = reqBuilder.copy()
                    .setOption(H3_DISCOVERY, ANY)
                    .build();
            var responseCF2 = client.sendAsync(request2, BodyHandlers.ofString());
            // wait until blocked in the handler
            BlockingHandler.IN_HANDLER.acquire();

            // release both
            BlockingHandler.GATE.release(2);

            var response1 = responseCF1.get();
            printResponse("First response", response1);
            var response2 = responseCF2.get();
            printResponse("Second response", response2);

            // set a timeout to make sure we wait long enough for
            // the MAX_STREAMS update to reach us before attempting
            // to create the HTTP/3 exchange.
            HttpRequest request3 = reqBuilder.copy()
                    .timeout(Duration.ofSeconds(30))
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .build();
            var responseCF3 = client.sendAsync(request3, BodyHandlers.ofString());
            // request3 should now reuse the connection opened by request2
            // wait until blocked in the handler
            BlockingHandler.IN_HANDLER.acquire();

            // ANY should reuse the same connection as request3,
            // get stream limit exception, and open a new connection
            HttpRequest request4 = reqBuilder.copy()
                    .setOption(H3_DISCOVERY, ANY)
                    .build();
            var responseCF4 = client.sendAsync(request4, BodyHandlers.ofString());
            // wait until blocked in the handler
            BlockingHandler.IN_HANDLER.acquire();

            // release both response 3 and response 4
            BlockingHandler.GATE.release(2);

            var response3 = responseCF3.get();
            printResponse("Third response", response3);
            var response4 = responseCF4.get();
            printResponse("Fourth response", response4);

            assertNotEquals(response1.connectionLabel().get(), response2.connectionLabel().get());
            assertEquals(response2.connectionLabel().get(), response3.connectionLabel().get());
            assertNotEquals(response1.connectionLabel().get(), response4.connectionLabel().get());
            assertNotEquals(response2.connectionLabel().get(), response4.connectionLabel().get());
            assertNotEquals(response3.connectionLabel().get(), response4.connectionLabel().get());
        } finally {
            http3OnlyServer.stop();
            https2AltSvcServer.stop();
        }
    }

    @Test
    public static void testH2H3WithTwoAltSVC() throws Exception {
        testH2H3(false);
    }

    @Test
    public static void testH2H3WithAltSVCOnSamePort() throws Exception {
        testH2H3(true);
    }

    private static void testH2H3(boolean samePort) throws Exception {
        System.out.println("\nTesting with advertised AltSvc on "
                + (samePort ? "same port" : "ephemeral port"));
        initialize(samePort);
        try (HttpClient client = getClient()) {
            var req1Builder = HttpRequest.newBuilder()
                    .uri(URI.create(http3DirectURIString))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET();
            var req2Builder = HttpRequest.newBuilder()
                    .uri(URI.create(http3DirectURIString))
                    .setOption(H3_DISCOVERY, ALT_SVC)
                    .version(HTTP_3)
                    .GET();

            if (altsvcPort == https2Port) {
                System.out.println("Testing with alt service on same port");

                // first request with HTTP3_URI_ONLY should create H3 connection
                HttpRequest request1 = req1Builder.copy().build();
                var responseCF1 = client.sendAsync(request1, BodyHandlers.ofString());
                BlockingHandler.IN_HANDLER.acquire();

                HttpRequest request2 = req2Builder.copy().build();
                // first request with ALT_SVC is to get alt service, should be H2
                var h2resp2CF = client.sendAsync(request2, BodyHandlers.ofString());
                // Blocking until the handler is invoked should be enough
                // to ensure we get the AltSvc frame before the next request.
                BlockingHandler.IN_HANDLER.acquire();
                BlockingHandler.GATE.release(3);

                // second request should have ALT_SVC and create new connection with H3
                // it should not reuse the non-advertised connection
                // There's still a potential race here - to avoid it
                // we need h2resp2 = h2resp2CF.get(); before sending request2
                var h2resp2 = h2resp2CF.get();
                System.out.printf("Got expected h2 response:  %s [%s]%n",
                        h2resp2, h2resp2.version());

                var responseCF2 = client.sendAsync(request2, BodyHandlers.ofString());
                BlockingHandler.IN_HANDLER.acquire();

                assertEquals(HTTP_2, h2resp2.version());
                checkStatus(200, h2resp2.statusCode());

                var response1 = responseCF1.get();
                printResponse("response 1", H3_DISCOVERY, response1);
                var response2 = responseCF2.get();
                printResponse("response 2", H3_DISCOVERY, response2);
                assertEquals(HTTP_3, response1.version());
                checkStatus(200, response1.statusCode());
                assertEquals(HTTP_3, response2.version());
                checkStatus(200, response2.statusCode());
                assertNotEquals(response2.connectionLabel().get(), response1.connectionLabel().get());

                // second request with HTTP3_URI_ONLY should reuse a created connection
                // It should reuse the advertised connection (from response2) if same
                //    origin
                // Specify a request timeout here to make sure we wait long enough
                //    for the MAX_STREAMS frame to arrive before creating a new
                //    connection
                HttpRequest request3 = req1Builder.copy()
                        .timeout(Duration.ofSeconds(30)).build();
                var responseCF3 = client.sendAsync(request3, BodyHandlers.ofString());
                BlockingHandler.IN_HANDLER.acquire();

                // third request with ALT_SVC should reuse the same advertised
                // connection (from response2), regardless of same origin...
                // It should not reuse the connection from response1, it should
                // not invoke reconnection on the connection used by response 3
                // Specify a request timeout here to make sure we wait long enough
                //    for the MAX_STREAMS frame to arrive before creating a new
                //    connection
                HttpRequest request4 = req2Builder.copy()
                        .timeout(Duration.ofSeconds(30)).build();
                var responseCF4 = client.sendAsync(request4, BodyHandlers.ofString());
                BlockingHandler.IN_HANDLER.acquire();

                // release both
                BlockingHandler.GATE.release(2);

                var response3 = responseCF3.get();
                printResponse("response 3", H3_DISCOVERY, response3);
                var response4 = responseCF4.get();
                printResponse("response 4", H3_DISCOVERY, response4);

                assertEquals(HTTP_3, response3.version());
                checkStatus(200, response3.statusCode());
                assertEquals(response1.connectionLabel().get(), response3.connectionLabel().get());

                assertEquals(HTTP_3, response4.version());
                checkStatus(200, response4.statusCode());
                assertEquals(response2.connectionLabel().get(), response4.connectionLabel().get());

            } else if (http3Port == https2Port) {
                System.out.println("Testing with two alt services");
                // first, get the alt service
                HttpRequest request2 = req2Builder.copy().build();
                // first request with ALT_SVC is to get alt service, should be H2
                BlockingHandler.GATE.release();
                HttpResponse<String> h2resp2 = client.send(request2, BodyHandlers.ofString());
                assertEquals(HTTP_2, h2resp2.version());
                checkStatus(200, h2resp2.statusCode());
                BlockingHandler.IN_HANDLER.acquire();
                System.out.printf("Got expected h2 response:  %s [%s]%n",
                        h2resp2, h2resp2.version());

                // second - make a direct connection
                HttpRequest request1 = req1Builder.copy()
                        .uri(URI.create(http3DirectURIString+"?request1")).build();
                var responseCF1 = client.sendAsync(request1, BodyHandlers.ofString());

                // second request should have ALT_SVC and create new connection with H3
                // it should not reuse the non-advertised connection
                var req2 = req2Builder.copy()
                        .uri(URI.create(http3DirectURIString + "?request2"))
                        .build();
                var responseCF2 = client.sendAsync(req2, BodyHandlers.ofString());

                BlockingHandler.IN_HANDLER.acquire(2);
                BlockingHandler.GATE.release(2);

                var response1 = responseCF1.get();
                printResponse("response 1", H3_DISCOVERY, response1);
                var response2 = responseCF2.get();
                printResponse("response 2", H3_DISCOVERY, response2);

                assertEquals(HTTP_3, response1.version());
                checkStatus(200, response1.statusCode());
                assertEquals(HTTP_3, response2.version());
                checkStatus(200, response2.statusCode());
                assertNotEquals(response2.connectionLabel().get(), h2resp2.connectionLabel().get());
                assertNotEquals(response2.connectionLabel().get(), response1.connectionLabel().get());

                // third request with ALT_SVC should reuse the same advertised
                // connection (from response2), regardless of same origin...
                // Specify a request timeout here to make sure we wait long enough
                //    for the MAX_STREAMS frame to arrive before creating a new
                //    connection
                HttpRequest request3 = req2Builder.copy()
                        .uri(URI.create(http3DirectURIString + "?request3"))
                        .timeout(Duration.ofSeconds(30)).build();
                var responseCF3 = client.sendAsync(request3, BodyHandlers.ofString());

                // fourth request with HTTP_3_URI_ONLY should reuse the first connection,
                // and not reuse the second.
                // Specify a request timeout here to make sure we wait long enough
                //    for the MAX_STREAMS frame to arrive before creating a new
                //    connection
                HttpRequest request4 = req1Builder.copy()
                        .uri(URI.create(http3DirectURIString + "?request4"))
                        .timeout(Duration.ofSeconds(30)).build();
                var responseCF4 = client.sendAsync(request4, BodyHandlers.ofString());

                BlockingHandler.IN_HANDLER.acquire(2);
                BlockingHandler.GATE.release(2);

                var response3 = responseCF3.get();
                printResponse("response 3", H3_DISCOVERY, response3);
                var response4 = responseCF4.get();
                printResponse("response 4", H3_DISCOVERY, response4);

                assertEquals(HTTP_3, response3.version());
                checkStatus(200, response3.statusCode());
                assertEquals(response2.connectionLabel().get(), response3.connectionLabel().get());
                assertNotEquals(response1.connectionLabel().get(), response3.connectionLabel().get());
                assertEquals(HTTP_3, response4.version());
                assertEquals(response1.connectionLabel().get(), response4.connectionLabel().get());
                assertNotEquals(response3.connectionLabel().get(), response4.connectionLabel().get());
                checkStatus(200, response1.statusCode());
            } else {
                System.out.println("WARNING: Couldn't create HTTP/3 server on same port! Can't test all...");
                // Get, get the alt service
                HttpRequest request2 = req2Builder.copy().build();
                // first request with ALT_SVC is to get alt service, should be H2
                BlockingHandler.GATE.release(1);
                HttpResponse<String> h2resp2 = client.send(request2, BodyHandlers.ofString());
                assertEquals(HTTP_2, h2resp2.version());
                checkStatus(200, h2resp2.statusCode());
                BlockingHandler.IN_HANDLER.acquire();

                // second request should have ALT_SVC and create new connection with H3
                // it should not reuse the non-advertised connection
                var responseCF2 = client.sendAsync(request2, BodyHandlers.ofString());
                BlockingHandler.IN_HANDLER.acquire();

                // third request with ALT_SVC should reuse the same advertised
                // connection (from response2), regardless of same origin, get
                // StreamLimitReached exception, and create a new connection
                HttpRequest request3 = req2Builder.copy().build();
                var responseCF3 = client.sendAsync(request3, BodyHandlers.ofString());
                BlockingHandler.IN_HANDLER.acquire();

                BlockingHandler.GATE.release(2);

                var response2 = responseCF2.get();
                printResponse("response 2", H3_DISCOVERY, response2);

                var response3 = responseCF3.get();
                printResponse("response 3", H3_DISCOVERY, response3);

                assertEquals(HTTP_3, response2.version());
                checkStatus(200, response2.statusCode());
                assertNotEquals(response2.connectionLabel().get(), h2resp2.connectionLabel().get());
                assertEquals(HTTP_3, response3.version());
                checkStatus(200, response3.statusCode());
                assertNotEquals(response3.connectionLabel().get(), response2.connectionLabel().get());
            }
        } finally {
            http3OnlyServer.stop();
            https2AltSvcServer.stop();
        }
    }

    @Test
    public static void testParallelH2H3WithTwoAltSVC() throws Exception {
        testH2H3Concurrent(false);
    }

    @Test
    public static void testParallelH2H3WithAltSVCOnSamePort() throws Exception {
        testH2H3Concurrent(true);
    }


    private static void testH2H3Concurrent(boolean samePort) throws Exception {
        System.out.println("\nTesting concurrent reconnections with advertised AltSvc on "
                + (samePort ? "same port" : "ephemeral port"));
        initialize(samePort);
        try (HttpClient client = getClient()) {
            var req1Builder = HttpRequest.newBuilder()
                    .uri(URI.create(http3DirectURIString))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET();
            var req2Builder = HttpRequest.newBuilder()
                    .uri(URI.create(http3DirectURIString))
                    .setOption(H3_DISCOVERY, ALT_SVC)
                    .version(HTTP_3)
                    .GET();

            if (altsvcPort == https2Port) {
                System.out.println("Testing reconnections with alt service on same port");

                // first request with HTTP3_URI_ONLY should create H3 connection
                HttpRequest request1 = req1Builder.copy().build();
                HttpRequest request2 = req2Builder.copy().build();
                List<CompletableFuture<HttpResponse<String>>> directResponses = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    HttpRequest req1 = req1Builder.copy()
                            .uri(URI.create(http3DirectURIString+"?dir="+i)).build();
                    directResponses.add(client.sendAsync(req1, BodyHandlers.ofString()));
                    BlockingHandler.IN_HANDLER.acquire();
                }
                // can't send requests in parallel here because if any establishes
                // a connection before the H3 direct are established, then the H3
                // direct might reuse the H3 alt since the service is with same origin
                BlockingHandler.releaseGate(directResponses.size() + 1);
                HttpResponse<String> h2resp2 = client.send(request2, BodyHandlers.ofString());
                BlockingHandler.IN_HANDLER.acquire();

                CompletableFuture.allOf(directResponses.stream()
                        .toArray(CompletableFuture[]::new)).exceptionally((t) -> null)
                        .join();

                Set<String> c1Label = new HashSet<>();
                for (int i = 0; i < directResponses.size(); i++) {
                    HttpResponse<String> response1 = directResponses.get(i).get();
                    System.out.printf("direct response [%s][%s]: %s%n", i,
                            response1.connectionLabel(),
                            response1);
                    assertEquals(HTTP_3, response1.version());
                    checkStatus(200, response1.statusCode());
                    var cLabel = response1.connectionLabel().get();
                    assertFalse(c1Label.contains(cLabel),
                            "%s contained in %s".formatted(cLabel, c1Label));
                    c1Label.add(cLabel);
                }

                // first request with ALT_SVC is to get alt service, should be H2
                assertEquals(HTTP_2, h2resp2.version());
                checkStatus(200, h2resp2.statusCode());
                assertFalse(c1Label.contains(h2resp2.connectionLabel().get()),
                        "%s contained in %s!".formatted(h2resp2.connectionLabel().get(), c1Label));

                // second request should have ALT_SVC and create new connection with H3
                // it should not reuse the non-advertised connection
                List<CompletableFuture<HttpResponse<String>>> altResponses = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    HttpRequest req2 = req2Builder.copy()
                            .uri(URI.create(http3DirectURIString+"?alt="+i)).build();
                    altResponses.add(client.sendAsync(req2, BodyHandlers.ofString()));
                }

                BlockingHandler.releaseGate(altResponses.size());
                BlockingHandler.IN_HANDLER.acquire(altResponses.size());

                CompletableFuture.allOf(altResponses.stream().toArray(CompletableFuture[]::new))
                        .exceptionally((t) -> null)
                        .join();

                Set<String> c2Label = new HashSet<>();
                for (int i = 0; i < altResponses.size(); i++) {
                    HttpResponse<String> response2 = altResponses.get(i).get();
                    System.out.printf("alt response [%s][%s]: %s%n", i,
                            response2.connectionLabel(),
                            response2);
                    assertEquals(HTTP_3, response2.version());
                    checkStatus(200, response2.statusCode());
                    var cLabel = response2.connectionLabel().get();
                    if (c2Label.contains(cLabel)) {
                        System.out.printf("Connection %s reused%n", cLabel);
                    }
                    c2Label.add(cLabel);
                    assertFalse(c1Label.contains(cLabel),
                            "%s contained in %s".formatted(cLabel, c1Label));
                    // cLabel could already be in c2Label, if the previous
                    // request finished before the next one.
                }

                System.out.println("Sending mixed requests");

                // second set of requests should reuse a created connection
                HttpRequest request3 = req1Builder.copy().build();
                List<CompletableFuture<HttpResponse<String>>> mixResponses = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    var builder1 = req1Builder.copy()
                            .uri(URI.create(http3DirectURIString+"?mix1="+i));
                    if (i == 0) {
                        // make sure to give time for MAX_STREAMS to arrive
                        builder1 = builder1.timeout(Duration.ofSeconds(30));
                    }
                    HttpRequest req1 = builder1.build();
                    mixResponses.add(client.sendAsync(req1, BodyHandlers.ofString()));
                    if (i == 0) BlockingHandler.IN_HANDLER.acquire();
                    var builder2 = req2Builder.copy()
                            .uri(URI.create(http3DirectURIString+"?mix2="+i));
                    if (i == 0) {
                        // make sure to give time for MAX_STREAMS to arrive
                        builder2 = builder2.timeout(Duration.ofSeconds(30));
                    }
                    HttpRequest req2 = builder2.build();
                    mixResponses.add(client.sendAsync(req2, BodyHandlers.ofString()));
                    if (i == 0) BlockingHandler.IN_HANDLER.acquire();
                }

                System.out.println("IN_HANDLER.acquire("
                        + (mixResponses.size() - 2) + ") - available: "
                        + BlockingHandler.IN_HANDLER.availablePermits());
                BlockingHandler.IN_HANDLER.acquire(mixResponses.size() - 2);
                BlockingHandler.releaseGate(mixResponses.size());

                System.out.println("Getting mixed responses");

                CompletableFuture.allOf(mixResponses.stream().toArray(CompletableFuture[]::new))
                        .exceptionally((t) -> null)
                        .join();

                System.out.println("All mixed responses received");

                Set<String> mixC1Label = new HashSet<>();
                Set<String> mixC2Label = new HashSet<>();
                for (int i = 0; i < mixResponses.size(); i++) {
                    HttpResponse<String> response3 = mixResponses.get(i).get();
                    System.out.printf("mixed response [%s][%s] %s: %s%n", i,
                            response3.connectionLabel(),
                            response3.request().getOption(H3_DISCOVERY),
                            response3);
                    assertEquals(HTTP_3, response3.version());
                    checkStatus(200, response3.statusCode());
                    var cLabel = response3.connectionLabel().get();
                    if (response3.request().getOption(H3_DISCOVERY).orElse(null) == ALT_SVC) {
                        if (i == 0 || i == 1) {
                            assertTrue(c2Label.contains(cLabel),
                                    "%s not in %s".formatted(cLabel, c2Label));
                            System.out.printf("first ALTSVC connection reused %s from %s%n", cLabel, c2Label);
                        } else {
                            assertFalse(c2Label.contains(cLabel),
                                    "%s in %s".formatted(cLabel, c2Label));
                        }
                        assertFalse(c1Label.contains(cLabel),
                                "%s in %s".formatted(cLabel, c1Label));
                        assertFalse(mixC1Label.contains(cLabel),
                                "%s in %s".formatted(cLabel, mixC1Label));
                        if (mixC2Label.contains(cLabel)) {
                            System.out.printf("ALTSVC connection reused %s from %s%n", cLabel, mixC2Label);
                        }
                        mixC2Label.add(cLabel);
                    } else {
                        if (i == 0 || i == 1) {
                            assertTrue(c1Label.contains(cLabel),
                                    "%s not in %s".formatted(cLabel, c1Label));
                            System.out.printf("first ALTSVC connection reused %s from %s%n", cLabel, c1Label);
                        } else {
                            assertFalse(c1Label.contains(cLabel),
                                    "%s in %s".formatted(cLabel, c1Label));
                        }
                        assertFalse(c2Label.contains(cLabel),
                                "%s in %s".formatted(cLabel, c2Label));
                        assertFalse(mixC2Label.contains(cLabel),
                                "%s in %s".formatted(cLabel, mixC2Label));
                        if (mixC1Label.contains(cLabel)) {
                            System.out.printf("ALTSVC connection reused %s from %s%n", cLabel, mixC1Label);
                        }
                        mixC1Label.add(cLabel);
                    }
                }
                System.out.println("All done");
            } else if (http3Port == https2Port) {
                System.out.println("Testing with two alt services");
                // first - make a direct connection
                HttpRequest request1 = req1Builder.copy().build();

                // second, use the alt service
                HttpRequest request2 = req2Builder.copy().build();
                BlockingHandler.GATE.release();
                HttpResponse<String> h2resp2 = client.send(request2, BodyHandlers.ofString());
                assertEquals(HTTP_2, h2resp2.version());
                checkStatus(200, h2resp2.statusCode());
                BlockingHandler.IN_HANDLER.acquire();

                // third, use ANY
                HttpRequest request3 = req2Builder.copy().setOption(H3_DISCOVERY, ANY).build();

                List<CompletableFuture<HttpResponse<String>>> directResponses = new ArrayList<>();
                List<CompletableFuture<HttpResponse<String>>> altResponses = new ArrayList<>();
                List<CompletableFuture<HttpResponse<String>>> anyResponses = new ArrayList<>();
                checkStatus(200, h2resp2.statusCode());

                // We're going to send nine requests here. We could get
                // "No more stream available on connection" when we run with
                // a stream limit timeout of 0, unless we raise the retry on
                // stream limit to at least 6
                for (int i = 0; i < 3; i++) {
                    anyResponses.add(client.sendAsync(request3, BodyHandlers.ofString()));
                    directResponses.add(client.sendAsync(request1, BodyHandlers.ofString()));
                    altResponses.add(client.sendAsync(request2, BodyHandlers.ofString()));
                }

                var all = new ArrayList<>(directResponses);
                all.addAll(altResponses);
                all.addAll(anyResponses);
                int requestCount = all.size();

                BlockingHandler.GATE.release(requestCount);
                CompletableFuture.allOf(all.stream().toArray(CompletableFuture[]::new))
                        .exceptionally((t) -> null)
                        .join();

                Set<String> c1Label = new HashSet<>();
                for (int i = 0; i < directResponses.size(); i++) {
                    HttpResponse<String> response1 = directResponses.get(i).get();
                    System.out.printf("direct response [%s][%s] %s: %s%n", i,
                            response1.connectionLabel(),
                            response1.request().getOption(H3_DISCOVERY),
                            response1);
                    assertEquals(HTTP_3, response1.version());
                    checkStatus(200, response1.statusCode());

                    var cLabel = response1.connectionLabel().get();
                    if (c1Label.contains(cLabel)) {
                        System.out.printf("    connection %s reused from %s%n", cLabel, c1Label);
                    }
                    c1Label.add(cLabel);
                }
                Set<String> c2Label = new HashSet<>();
                for (int i = 0; i < altResponses.size(); i++) {
                    HttpResponse<String> response2 = altResponses.get(i).get();
                    System.out.printf("alt response [%s][%s] %s: %s%n", i,
                            response2.connectionLabel(),
                            response2.request().getOption(H3_DISCOVERY),
                            response2);
                    assertEquals(HTTP_3, response2.version());
                    checkStatus(200, response2.statusCode());

                    var cLabel = response2.connectionLabel().get();
                    if (c2Label.contains(cLabel)) {
                        System.out.printf("    connection %s reused from %s%n", cLabel, c2Label);
                    }
                    assertNotEquals(cLabel, h2resp2.connectionLabel().get());
                    assertFalse(c1Label.contains(cLabel),
                            "%s found in %s".formatted(cLabel, c1Label));
                    c2Label.add(cLabel);
                }

                var diff = new HashSet<>(c2Label);
                diff.retainAll(c1Label);
                assertTrue(diff.isEmpty());

                var anyLabels = new HashSet(Set.of(c1Label, c2Label));
                for (int i = 0; i < anyResponses.size(); i++) {
                    HttpResponse<String> response3 = anyResponses.get(i).get();
                    System.out.printf("any response [%s][%s] %s: %s%n", i,
                            response3.connectionLabel(),
                            response3.request().getOption(H3_DISCOVERY),
                            response3);
                    assertEquals(HTTP_3, response3.version());
                    checkStatus(200, response3.statusCode());
                    assertNotEquals(response3.connectionLabel().get(), h2resp2.connectionLabel().get());
                    var label = response3.connectionLabel().orElse("");
                    if (anyLabels.contains(label)) {
                        System.out.printf("    connection %s reused from %s%n", label, anyLabels);
                    }
                }
                BlockingHandler.IN_HANDLER.acquire(requestCount);
            } else {
                System.out.println("WARNING: Couldn't create HTTP/3 server on same port! Can't test all...");
                // Get, get the alt service
                HttpRequest request2 = req2Builder.copy().build();
                // first request with ALT_SVC is to get alt service, should be H2
                BlockingHandler.GATE.release();
                HttpResponse<String> h2resp2 = client.send(request2, BodyHandlers.ofString());
                assertEquals(HTTP_2, h2resp2.version());
                checkStatus(200, h2resp2.statusCode());
                BlockingHandler.IN_HANDLER.acquire();

                // second request should have ALT_SVC and create new connection with H3
                var responseCF2 = client.sendAsync(request2, BodyHandlers.ofString());
                BlockingHandler.IN_HANDLER.acquire();

                // third request with ALT_SVC should reuse the same advertised
                // connection (from response2), regardless of same origin, get
                // stream limit, and create a new connection
                HttpRequest request3 = req2Builder.copy().build();
                var responseCF3 = client.sendAsync(request3, BodyHandlers.ofString());
                BlockingHandler.IN_HANDLER.acquire();

                BlockingHandler.GATE.release(2);

                CompletableFuture.allOf(responseCF2, responseCF3)
                        .exceptionally((t) -> null)
                        .join();

                var response2 = responseCF2.get();
                printResponse("first HTTP/3 request", H3_DISCOVERY, response2);
                var response3 = responseCF3.get();
                printResponse("second HTTP/3 request", H3_DISCOVERY, response2);

                assertEquals(HTTP_3, response2.version());
                checkStatus(200, response2.statusCode());
                assertNotEquals(response2.connectionLabel().get(), h2resp2.connectionLabel().get());

                assertEquals(HTTP_3, response3.version());
                checkStatus(200, response3.statusCode());
                assertNotEquals(response3.connectionLabel().get(), h2resp2.connectionLabel().get());
                assertNotEquals(response3.connectionLabel().get(), response2.connectionLabel().get());
            }
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            throw t;
        } finally {
            http3OnlyServer.stop();
            https2AltSvcServer.stop();
        }
    }
    static HttpClient getClient() {
        if (client == null) {
            client = HttpServerAdapters.createClientBuilderForH3()
                    .sslContext(sslContext)
                    .version(HTTP_3)
                    .build();
        }
        return client;
    }

    static void checkStatus(int expected, int found) throws Exception {
        if (expected != found) {
            System.err.printf("Test failed: wrong status code %d/%d\n",
                    expected, found);
            throw new RuntimeException("Test failed");
        }
    }

    static void checkStrings(String expected, String found) throws Exception {
        if (!expected.equals(found)) {
            System.err.printf("Test failed: wrong string %s/%s\n",
                    expected, found);
            throw new RuntimeException("Test failed");
        }
    }


    static <T> T logExceptionally(String desc, Throwable t) {
        System.out.println(desc + " failed: " + t);
        System.err.println(desc + " failed: " + t);
        if (t instanceof RuntimeException r) throw r;
        if (t instanceof Error e) throw e;
        throw new CompletionException(t);
    }

}
