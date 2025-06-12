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

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.PushPromiseHandler.PushId.Http3PushId;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @summary Verifies the HTTP/3 server push handling of the HTTP client
 * @library /test/jdk/java/net/httpclient/lib
 *          /test/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.test.lib.net.SimpleSSLContext
 * @run junit H3ServerPushTest
 */

/**
 * Verifies the HTTP/3 server push handling of {@link HttpClient}.
 *
 * @implNote
 * Some tests deliberately corrupt the HTTP/3 stream state. Hence, instead of
 * creating a single {@link HttpTestServer}-{@link HttpClient} pair attached
 * to the class, and sharing it across tests, each test creates its own
 * server/client pair.
 */
@TestMethodOrder(OrderAnnotation.class)
class H3ServerPushTest {

    private static final HttpHeaders EMPTY_HEADERS = HttpHeaders.of(Map.of(), (_, _) -> false);

    private static final SSLContext SSL_CONTEXT = createSslContext();

    private static SSLContext createSslContext() {
        try {
            return new SimpleSSLContext().get();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Test
    @Order(1)
    void testBasicRequestResponse(TestInfo testInfo) throws Exception {
        try (HttpClient client = createClient();
             HttpTestServer server = createServer()) {

            // Configure the server handler
            URI uri = createUri(server, testInfo);
            server.addHandler(
                    exchange -> {
                        try (exchange) {
                            exchange.sendResponseHeaders(200, 0);
                        }
                    },
                    uri.getPath());

            // Send the request and verify its response
            HttpRequest request = createRequest(uri);
            log("requesting `%s`...", request.uri());
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            assertEquals(200, response.statusCode());

        }
    }

    @Test
    @Order(2)
    void testTwoConsecutiveRequestsToSameServer(TestInfo testInfo) throws Exception {
        try (HttpClient client = createClient();
             HttpTestServer server = createServer()) {

            // Configure the server handler
            URI uri = createUri(server, testInfo);
            server.addHandler(new PushSender(), uri.getPath());

            // Send the 1st request
            PushReceiver pushReceiver = new PushReceiver();
            HttpRequest request = createRequest(uri);
            log("requesting `%s`...", request.uri());
            HttpResponse<String> response1 = client
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString(US_ASCII), pushReceiver)
                    .get();

            // Verify the 1st response
            assertEquals(200, response1.statusCode());
            assertEquals("response0", response1.body());
            String connectionLabel = response1.connectionLabel().orElseThrow();
            final long initialPushId;
            {
                ReceivedPush.Promise[] push1Ref = {null};   // 1. Push(initialPushId) promise
                ReceivedPush.Response[] push2Ref = {null};  // 2. Push(initialPushId) response, since this is the very first request
                ReceivedPush.Promise[] push3Ref = {null};   // 3. Push(initialPushId+1) promise, the orphan one
                pushReceiver.consume(push1Ref, push2Ref, push3Ref);
                initialPushId = push1Ref[0].pushId.pushId();
                assertEquals(connectionLabel, push1Ref[0].pushId.connectionLabel());
                assertEquals(initialPushId, push2Ref[0].pushId.pushId());
                assertEquals(connectionLabel, push2Ref[0].pushId.connectionLabel());
                assertEquals("pushResponse0", push2Ref[0].responseBody);
                assertEquals(initialPushId + 1, push3Ref[0].pushId.pushId());
                assertEquals(connectionLabel, push3Ref[0].pushId.connectionLabel());
            }

            // Send the 2nd request
            log("requesting `%s`...", request.uri());
            HttpResponse<String> response2 = client
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString(US_ASCII), pushReceiver)
                    .get();

            // Verify the 2nd response
            assertEquals(200, response2.statusCode());
            assertEquals("response1", response2.body());
            {
                ReceivedPush.AdditionalPromise[] push1Ref = {null};     // 1. Push(initialPushId) additional promise
                ReceivedPush.Promise[] push2Ref = {null};               // 2. Push(initialPushId+2) promise, the orphan one
                pushReceiver.consume(push1Ref, push2Ref);
                assertEquals(initialPushId, push1Ref[0].pushId.pushId());
                assertEquals(connectionLabel, push1Ref[0].pushId.connectionLabel());
                assertEquals(initialPushId + 2, push2Ref[0].pushId.pushId());
                assertEquals(connectionLabel, push2Ref[0].pushId.connectionLabel());
            }

        }
    }

    @Test
    @Order(3)
    void testTwoConsecutiveRequestsToDifferentServers(TestInfo testInfo) throws Exception {
        try (HttpClient client = createClient();
             HttpTestServer server1 = createServer();
             HttpTestServer server2 = createServer()) {

            // Configure the server handlers
            URI uri1 = createUri(server1, testInfo);
            server1.addHandler(new PushSender(), uri1.getPath());
            URI uri2 = createUri(server2, testInfo);
            server2.addHandler(new PushSender(), uri2.getPath());

            // Send a request to the 1st server
            PushReceiver pushReceiver = new PushReceiver();
            HttpRequest request1 = createRequest(uri1);
            log("requesting `%s`...", request1.uri());
            HttpResponse<String> response1 = client
                    .sendAsync(request1, HttpResponse.BodyHandlers.ofString(US_ASCII), pushReceiver)
                    .get();

            // Verify the response from the 1st server
            assertEquals(200, response1.statusCode());
            assertEquals("response0", response1.body());
            String connectionLabel1 = response1.connectionLabel().orElseThrow();
            {
                ReceivedPush.Promise[] push1Ref = {null};   // 1. Push(initialPushId) promise
                ReceivedPush.Response[] push2Ref = {null};  // 2. Push(initialPushId) response, since this is the very first request
                ReceivedPush.Promise[] push3Ref = {null};   // 3. Push(initialPushId+1) promise, the orphan one
                pushReceiver.consume(push1Ref, push2Ref, push3Ref);
                long initialPushId = push1Ref[0].pushId.pushId();
                assertEquals(connectionLabel1, push1Ref[0].pushId.connectionLabel());
                assertEquals(initialPushId, push2Ref[0].pushId.pushId());
                assertEquals(connectionLabel1, push2Ref[0].pushId.connectionLabel());
                assertEquals("pushResponse0", push2Ref[0].responseBody);
                assertEquals(initialPushId + 1, push3Ref[0].pushId.pushId());
                assertEquals(connectionLabel1, push3Ref[0].pushId.connectionLabel());
            }

            // Send a request to the 2nd server
            HttpRequest request2 = createRequest(uri2);
            log("requesting `%s`...", request2.uri());
            HttpResponse<String> response2 = client
                    .sendAsync(request2, HttpResponse.BodyHandlers.ofString(US_ASCII), pushReceiver)
                    .get();

            // Verify the response from the 2nd server
            assertEquals(200, response2.statusCode());
            assertEquals("response0", response2.body());
            String connectionLabel2 = response2.connectionLabel().orElseThrow();
            {
                ReceivedPush.Promise[] push1Ref = {null};   // 1. Push(initialPushId) promise
                ReceivedPush.Response[] push2Ref = {null};  // 2. Push(initialPushId) response, since this is the very first request
                ReceivedPush.Promise[] push3Ref = {null};   // 3. Push(initialPushId+1) promise, the orphan one
                pushReceiver.consume(push1Ref, push2Ref, push3Ref);
                long initialPushId = push1Ref[0].pushId.pushId();
                assertEquals(connectionLabel2, push1Ref[0].pushId.connectionLabel());
                assertEquals(initialPushId, push2Ref[0].pushId.pushId());
                assertEquals(connectionLabel2, push2Ref[0].pushId.connectionLabel());
                assertEquals("pushResponse0", push2Ref[0].responseBody);
                assertEquals(initialPushId + 1, push3Ref[0].pushId.pushId());
                assertEquals(connectionLabel2, push3Ref[0].pushId.connectionLabel());
            }

            // Verify that connection labels differ
            assertNotEquals(connectionLabel1, connectionLabel2);

        }
    }

    /**
     * A server handler responding to <i>all</i> requests as follows:
     * <ol>
     * <li><code>push(initialPushId)</code> promise</li>
     * <li><code>push(initialPushId)</code> response: <code>"pushResponse" + responseIndex</code>,<br/>
     * iff <code>responseIndex == 0</code></li>
     * <li><code>push(pushId++)</code> promise (an orphan push promise)</li>
     * <li>response: <code>"response" + responseIndex</code></li>
     * </ol>
     */
    private static final class PushSender implements HttpTestHandler {

        private int responseIndex = 0;

        private long initialPushId = -1;

        @Override
        public synchronized void handle(HttpTestExchange exchange) throws IOException {
            try (exchange) {

                // Start with the push promise
                assertTrue(exchange.serverPushAllowed());
                log(">>> sending push promise (responseIndex=%d, pushId=%d)", responseIndex, initialPushId);
                long newInitialPushId = exchange.sendHttp3PushPromiseFrame(
                        initialPushId,
                        exchange.getRequestURI(),
                        EMPTY_HEADERS);
                if (initialPushId != newInitialPushId) {
                    log(">>> updated initial pushId=%d (responseIndex=%d)", newInitialPushId, responseIndex);
                    initialPushId = newInitialPushId;
                }

                // Send the push response iff it is the very first request
                if (responseIndex == 0) {
                    log(">>> sending push response (responseIndex=%d, pushId=%d)", responseIndex, initialPushId);
                    byte[] pushResponseBody = "pushResponse%d".formatted(responseIndex).getBytes(US_ASCII);
                    exchange.sendHttp3PushResponse(
                            initialPushId,
                            exchange.getRequestURI(),
                            EMPTY_HEADERS,
                            EMPTY_HEADERS,
                            new ByteArrayInputStream(pushResponseBody));
                }

                // Send the orphan push promise
                log(">>> sending an orphan push promise (responseIndex=%d)", responseIndex);
                long orphanPushId = exchange.sendHttp3PushPromiseFrame(-1, exchange.getRequestURI(), EMPTY_HEADERS);
                log(">>> sent the orphan push promise (responseIndex=%d, pushId=%d)", responseIndex, orphanPushId);

                // Send the response
                log(">>> sending response (responseIndex=%d)", responseIndex);
                byte[] responseBody = "response%d".formatted(responseIndex).getBytes(US_ASCII);
                exchange.sendResponseHeaders(200, responseBody.length);
                exchange.getResponseBody().write(responseBody);

            } finally {
                responseIndex++;
            }
        }

    }

    @Test
    @Order(4)
    void testTwoPushPromisesWithSameIdInOneResponse(TestInfo testInfo) throws Exception {
        try (HttpClient client = createClient();
             HttpTestServer server = createServer()) {

            // Configure the server handler
            URI uri = createUri(server, testInfo);
            HttpTestHandler pushSender = new HttpTestHandler() {

                private int responseIndex = 0;

                @Override
                public synchronized void handle(HttpTestExchange exchange) throws IOException {
                    try (exchange) {

                        // Send the 1st push promise and receive the push ID
                        log(">>> sending push promise (responseIndex=%d)", responseIndex);
                        long pushId = exchange.sendHttp3PushPromiseFrame(-1, uri, EMPTY_HEADERS);

                        // Send the 2nd push promise using the same ID
                        log(">>> sending push response (responseIndex=%d, pushId=%d)", responseIndex, pushId);
                        exchange.sendHttp3PushPromiseFrame(pushId, uri, EMPTY_HEADERS);

                        // Send the response
                        log(">>> sending response (responseIndex=%d)", responseIndex);
                        byte[] responseBody = "response%d".formatted(responseIndex).getBytes(US_ASCII);
                        exchange.sendResponseHeaders(200, responseBody.length);
                        exchange.getResponseBody().write(responseBody);

                    } finally {
                        responseIndex++;
                    }
                }

            };
            server.addHandler(pushSender, uri.getPath());

            // Send the request
            PushReceiver pushReceiver = new PushReceiver();
            HttpRequest request = createRequest(uri);
            log("requesting `%s`...", request.uri());
            HttpResponse<String> response = client
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString(US_ASCII), pushReceiver)
                    .get();

            // Verify the response
            assertEquals(200, response.statusCode());
            assertEquals("response0", response.body());
            ReceivedPush.Promise[] push1Ref = {null};               // 1. Push(initialPushId) promise
            ReceivedPush.AdditionalPromise[] push2Ref = {null};     // 2. Push(initialPushId) promise, again
            pushReceiver.consume(push1Ref, push2Ref);
            long initialPushId = push1Ref[0].pushId.pushId();
            String connectionLabel = response.connectionLabel().orElseThrow();
            assertEquals(connectionLabel, push1Ref[0].pushId.connectionLabel());
            assertEquals(initialPushId, push2Ref[0].pushId.pushId());
            assertEquals(connectionLabel, push2Ref[0].pushId.connectionLabel());

        }
    }

    @Test
    @Order(5)
    void testTwoPushPromisesWithSameIdButDifferentHeadersInOneResponse(TestInfo testInfo) throws Exception {
        try (HttpClient client = createClient();
             HttpTestServer server = createServer()) {

            // Configure the server handler
            URI uri = createUri(server, testInfo);
            CountDownLatch responseBodyWriteLatch = new CountDownLatch(1);
            var pushSender = new HttpTestHandler() {

                private long pushId = -1;

                private int responseIndex = 0;

                @Override
                public synchronized void handle(HttpTestExchange exchange) throws IOException {
                    try (exchange) {

                        // Send the 1st push promise and receive the push ID
                        log(">>> sending push promise (responseIndex=%d)", responseIndex);
                        pushId = exchange.sendHttp3PushPromiseFrame(pushId, uri, EMPTY_HEADERS);

                        // Send the 2nd push promise using the same ID, but different headers
                        log(">>> sending push response (responseIndex=%d, pushId=%d)", responseIndex, pushId);
                        HttpHeaders nonEmptyHeaders = HttpHeaders.of(Map.of("Foo", List.of("Bar")), (_, _) -> true);
                        assertNotEquals(EMPTY_HEADERS, nonEmptyHeaders);
                        exchange.sendHttp3PushPromiseFrame(pushId, uri, nonEmptyHeaders);

                        // Send the response
                        log(">>> sending response (responseIndex=%d)", responseIndex);
                        byte[] responseBody = "response%d".formatted(responseIndex).getBytes(US_ASCII);
                        exchange.sendResponseHeaders(200, responseBody.length);
                        // Block to ensure the bad push stream is received before the response
                        awaitLatch(responseBodyWriteLatch);
                        exchange.getResponseBody().write(responseBody);

                    } finally {
                        responseIndex++;
                    }
                }

            };
            server.addHandler(pushSender, uri.getPath());

            // Send the request and verify the failure
            HttpRequest request = createRequest(uri);
            log("requesting `%s`...", request.uri());
            ExecutionException exception = assertThrows(ExecutionException.class, () -> client
                    .sendAsync(
                            request,
                            HttpResponse.BodyHandlers.ofString(US_ASCII),
                            // Push receiver has no semantic purpose here, but provide logs to aid troubleshooting.
                            new PushReceiver())
                    .get());
            responseBodyWriteLatch.countDown();
            Throwable cause = exception.getCause();
            assertNotNull(cause);
            assertInstanceOf(IOException.class, cause);
            String actualMessage = cause.getMessage();
            String expectedMessage = "push headers do not match with previous promise for %d".formatted(pushSender.pushId);
            assertEquals(expectedMessage, actualMessage);

        }
    }

    @Test
    @Order(6)
    void testTwoPushPromisesWithSameIdInTwoResponsesOverOneConnection(TestInfo testInfo) throws Exception {
        try (HttpClient client = createClient();
             HttpTestServer server = createServer()) {

            // Configure the server handler
            URI uri = createUri(server, testInfo);
            HttpTestHandler pushSender = new HttpTestHandler() {

                private long pushId = -1;

                private int responseIndex = 0;

                @Override
                public synchronized void handle(HttpTestExchange exchange) throws IOException {
                    try (exchange) {

                        // Send the push promise, and receive the push ID, if necessary
                        log(">>> sending push promise (responseIndex=%d, pushId=%d)", responseIndex, pushId);
                        pushId = exchange.sendHttp3PushPromiseFrame(pushId, uri, EMPTY_HEADERS);

                        // Send the response
                        log(">>> sending response (responseIndex=%d)", responseIndex);
                        byte[] responseBody = "response%d".formatted(responseIndex).getBytes(US_ASCII);
                        exchange.sendResponseHeaders(200, responseBody.length);
                        exchange.getResponseBody().write(responseBody);

                    } finally {
                        responseIndex++;
                    }
                }

            };
            server.addHandler(pushSender, uri.getPath());

            // Send the 1st request
            PushReceiver pushReceiver = new PushReceiver();
            HttpRequest request = createRequest(uri);
            log("requesting `%s`...", request.uri());
            HttpResponse<String> response1 = client
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString(US_ASCII), pushReceiver)
                    .get();

            // Verify the 1st response
            assertEquals(200, response1.statusCode());
            assertEquals("response0", response1.body());
            String connectionLabel = response1.connectionLabel().orElseThrow();
            final long initialPushId;
            {
                ReceivedPush.Promise[] pushRef = {null};
                pushReceiver.consume(pushRef);
                initialPushId = pushRef[0].pushId.pushId();
                assertEquals(connectionLabel, pushRef[0].pushId.connectionLabel());
            }

            // Send the 2nd request
            log("requesting `%s`...", request.uri());
            HttpResponse<String> response2 = client
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString(US_ASCII), pushReceiver)
                    .get();

            // Verify the 2nd request
            assertEquals(200, response2.statusCode());
            assertEquals("response1", response2.body());
            assertEquals(connectionLabel, response2.connectionLabel().orElseThrow());
            {
                ReceivedPush.AdditionalPromise[] pushRef = {null};
                pushReceiver.consume(pushRef);
                assertEquals(initialPushId, pushRef[0].pushId.pushId());
                assertEquals(connectionLabel, pushRef[0].pushId.connectionLabel());
            }

        }
    }

    @Test
    @Order(7)
    void testTwoPushResponsesWithSameIdInOneResponse(TestInfo testInfo) throws Exception {
        try (HttpClient client = createClient();
             HttpTestServer server = createServer()) {

            // Configure the server handler
            URI uri = createUri(server, testInfo);
            long[] pushId = {-1};
            CountDownLatch responseBodyWriteLatch = new CountDownLatch(1);
            HttpTestHandler pushSender = new HttpTestHandler() {

                private int responseIndex = 0;

                @Override
                public synchronized void handle(HttpTestExchange exchange) throws IOException {
                    try (exchange) {

                        // Send the 1st push promise and receive the push ID
                        log(">>> sending push promise (responseIndex=%d)", responseIndex);
                        pushId[0] = exchange.sendHttp3PushPromiseFrame(-1, uri, EMPTY_HEADERS);

                        // Send two push responses
                        for (int trialIndex = 0; trialIndex < 2; trialIndex++) {
                            log(
                                    ">>> sending push response (responseIndex=%d, pushId=%d, trialIndex=%d)",
                                    responseIndex, pushId[0], trialIndex);
                            byte[] pushResponseBody = "pushResponse%d-%d"
                                    .formatted(responseIndex, trialIndex)
                                    .getBytes(US_ASCII);
                            exchange.sendHttp3PushResponse(
                                    pushId[0],
                                    uri,
                                    EMPTY_HEADERS,
                                    EMPTY_HEADERS,
                                    new ByteArrayInputStream(pushResponseBody));
                        }

                        // Send the response
                        log(">>> sending response (responseIndex=%d)", responseIndex);
                        byte[] responseBody = "response%d".formatted(responseIndex).getBytes(US_ASCII);
                        exchange.sendResponseHeaders(200, responseBody.length);
                        // Block to ensure the bad push stream is received before the response
                        awaitLatch(responseBodyWriteLatch);
                        exchange.getResponseBody().write(responseBody);
                    } finally {
                        responseIndex++;
                    }
                }

            };
            server.addHandler(pushSender, uri.getPath());

            // Send the request and verify the failure
            HttpRequest request = createRequest(uri);
            log("requesting `%s`...", request.uri());
            ExecutionException exception = assertThrows(ExecutionException.class, () -> client
                    .sendAsync(
                            request,
                            HttpResponse.BodyHandlers.ofString(US_ASCII),
                            // Push receiver has no semantic purpose here, but provide logs to aid troubleshooting.
                            new PushReceiver())
                    .get());
            responseBodyWriteLatch.countDown();
            Throwable cause = exception.getCause();
            assertNotNull(cause);
            assertInstanceOf(IOException.class, cause);
            String actualMessage = cause.getMessage();
            String expectedMessage = "HTTP/3 pushId %d already used on this connection".formatted(pushId[0]);
            assertEquals(expectedMessage, actualMessage);
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();     // Restore the interrupt
            throw new RuntimeException(ie);
        }
    }

    @Test
    @Order(8)
    void testTwoPushResponsesWithSameIdInTwoResponsesOverOneConnection(TestInfo testInfo) throws Exception {
        try (HttpClient client = createClient();
             HttpTestServer server = createServer()) {

            // Configure the server handler
            URI uri = createUri(server, testInfo);
            long[] pushId = {-1};
            HttpTestHandler pushSender = new HttpTestHandler() {

                private int responseIndex = 0;

                private final Semaphore responseBodyWriteSem = new Semaphore(1);

                @Override
                public synchronized void handle(HttpTestExchange exchange) throws IOException {
                    try (exchange) {

                        // Send the push promise and receive the push ID, iff this is the very first response
                        if (responseIndex == 0) {
                            log(">>> sending push promise (responseIndex=%d)", responseIndex);
                            pushId[0] = exchange.sendHttp3PushPromiseFrame(-1, uri, EMPTY_HEADERS);
                        }

                        // Send the push response
                        log(">>> sending push response (responseIndex=%d, pushId=%d)", responseIndex, pushId[0]);
                        byte[] pushResponseBody = "pushResponse%d".formatted(responseIndex).getBytes(US_ASCII);
                        exchange.sendHttp3PushResponse(
                                pushId[0],
                                uri,
                                EMPTY_HEADERS,
                                EMPTY_HEADERS,
                                new ByteArrayInputStream(pushResponseBody));

                        // Send the response
                        log(">>> sending response (responseIndex=%d)", responseIndex);
                        byte[] responseBody = "response%d".formatted(responseIndex).getBytes(US_ASCII);
                        exchange.sendResponseHeaders(200, responseBody.length);
                        try {
                            // The second request will block here, ensuring the
                            // bad push stream is received before the response.
                            responseBodyWriteSem.acquire();
                        } catch (InterruptedException x) {
                            Thread.currentThread().interrupt();
                        }
                        exchange.getResponseBody().write(responseBody);

                    } finally {
                        responseIndex++;
                    }
                }

            };
            server.addHandler(pushSender, uri.getPath());

            // Send the 1st request
            PushReceiver pushReceiver = new PushReceiver();
            HttpRequest request = createRequest(uri);
            log("requesting `%s`...", request.uri());
            HttpResponse<String> response1 = client
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString(US_ASCII), pushReceiver)
                    .get();

            // Verify the 1st response
            assertEquals(200, response1.statusCode());
            assertEquals("response0", response1.body());
            ReceivedPush.Promise[] push1Ref = {null};   // 1. Push(initialPushId) promise
            ReceivedPush.Response[] push2Ref = {null};  // 2. Push(initialPushId) response
            pushReceiver.consume(push1Ref, push2Ref);
            long initialPushId = push1Ref[0].pushId.pushId();
            String connectionLabel = response1.connectionLabel().orElseThrow();
            assertEquals(connectionLabel, push1Ref[0].pushId.connectionLabel());
            assertEquals(initialPushId, push2Ref[0].pushId.pushId());
            assertEquals(connectionLabel, push2Ref[0].pushId.connectionLabel());
            assertEquals("pushResponse0", push2Ref[0].responseBody);

            // Send the 2nd request and verify the failure
            log("requesting `%s`...", request.uri());
            ExecutionException exception = assertThrows(ExecutionException.class, () -> client
                    .sendAsync(
                            request,
                            HttpResponse.BodyHandlers.ofString(US_ASCII),
                            // Push receiver has no semantic purpose here, but provide logs to aid troubleshooting.
                            pushReceiver)
                    .get());
            Throwable cause = exception.getCause();
            assertNotNull(cause);
            assertInstanceOf(IOException.class, cause);
            String actualMessage = cause.getMessage();
            String expectedMessage = "HTTP/3 pushId %d already used on this connection".formatted(pushId[0]);
            assertEquals(expectedMessage, actualMessage);

        }
    }

    @Test
    @Order(9)
    void testPushPromiseBeforeHeader(TestInfo testInfo) throws Exception {
        testPositionalPushPromise(testInfo, new PositionalPushSender() {
            @Override
            void handle0(HttpTestExchange exchange) throws IOException {
                sendPushPromise(exchange);
                sendHeaders(exchange);
                sendBody(exchange);
            }
        });
    }

    @Test
    @Order(10)
    void testPushPromiseAfterHeaderAndBeforeBody(TestInfo testInfo) throws Exception {
        testPositionalPushPromise(testInfo, new PositionalPushSender() {
            @Override
            void handle0(HttpTestExchange exchange) throws IOException {
                sendHeaders(exchange);
                sendPushPromise(exchange);
                sendBody(exchange);
            }
        });
    }

    @Test
    @Order(11)
    void testPushPromiseAfterBody(TestInfo testInfo) throws Exception {
        testPositionalPushPromise(testInfo, new PositionalPushSender() {
            @Override
            void handle0(HttpTestExchange exchange) throws IOException {
                sendHeaders(exchange);
                sendBody(exchange);
                sendPushPromise(exchange);
            }
        });
    }

    private static void testPositionalPushPromise(TestInfo testInfo, PositionalPushSender pushSender) throws Exception {
        try (HttpClient client = createClient();
             HttpTestServer server = createServer()) {

            // Configure the server handler
            URI uri = createUri(server, testInfo);
            server.addHandler(pushSender, uri.getPath());

            // Send the request
            PushReceiver pushReceiver = new PushReceiver();
            HttpRequest request = createRequest(uri);
            log("requesting `%s`...", request.uri());
            HttpResponse<String> response = client
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString(US_ASCII), pushReceiver)
                    .get();

            // Verify the response
            assertEquals(200, response.statusCode());
            assertEquals("response0", response.body());
            ReceivedPush.Promise[] pushRef = {null};
            pushReceiver.consume(pushRef);
            assertEquals(pushSender.pushId, pushRef[0].pushId.pushId());

        }
    }

    @Test
    @Order(12)
    void testPushPromiseAndResponseBeforeHeader(TestInfo testInfo) throws Exception {
        testPositionalPushPromiseAndResponse(testInfo, new PositionalPushSender() {
            @Override
            void handle0(HttpTestExchange exchange) throws IOException {
                sendPushPromise(exchange);
                sendPushResponse(exchange);
                sendHeaders(exchange);
                sendBody(exchange);
            }
        });
    }

    @Test
    @Order(13)
    void testPushPromiseAndResponseAfterHeaderAndBeforeBody(TestInfo testInfo) throws Exception {
        testPositionalPushPromiseAndResponse(testInfo, new PositionalPushSender() {
            @Override
            void handle0(HttpTestExchange exchange) throws IOException {
                sendHeaders(exchange);
                sendPushPromise(exchange);
                sendPushResponse(exchange);
                sendBody(exchange);
            }
        });
    }

    @Test
    @Order(14)
    void testPushPromiseAndResponseAfterBody(TestInfo testInfo) throws Exception {
        testPositionalPushPromiseAndResponse(testInfo, new PositionalPushSender() {
            @Override
            void handle0(HttpTestExchange exchange) throws IOException {
                sendHeaders(exchange);
                sendBody(exchange);
                sendPushPromise(exchange);
                sendPushResponse(exchange);
            }
        });
    }

    private static void testPositionalPushPromiseAndResponse(TestInfo testInfo, PositionalPushSender pushSender) throws Exception {
        try (HttpClient client = createClient();
             HttpTestServer server = createServer()) {

            // Configure the server handler
            URI uri = createUri(server, testInfo);
            server.addHandler(pushSender, uri.getPath());

            // Send the request
            PushReceiver pushReceiver = new PushReceiver();
            HttpRequest request = createRequest(uri);
            log("requesting `%s`...", request.uri());
            HttpResponse<String> response = client
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString(US_ASCII), pushReceiver)
                    .get();

            // Verify the response
            assertEquals(200, response.statusCode());
            assertEquals("response0", response.body());
            ReceivedPush.Promise[] push1Ref = {null};
            ReceivedPush.Response[] push2Ref = {null};
            pushReceiver.consume(push1Ref, push2Ref);
            assertEquals(pushSender.pushId, push1Ref[0].pushId.pushId());
            String connectionLabel = response.connectionLabel().orElseThrow();
            assertEquals(connectionLabel, push1Ref[0].pushId.connectionLabel());
            assertEquals(pushSender.pushId, push2Ref[0].pushId.pushId());
            assertEquals(connectionLabel, push2Ref[0].pushId.connectionLabel());
            assertEquals("pushResponse0", push2Ref[0].responseBody);

        }
    }

    /**
     * A server providing helper methods to send header, body, push promise &amp; response.
     * Subclasses can use these methods to inject custom server push behaviour at certain positions of the response assembly.
     */
    private static abstract class PositionalPushSender implements HttpTestHandler {

        private long pushId = -1;

        private int responseIndex = 0;

        @Override
        public final synchronized void handle(HttpTestExchange exchange) throws IOException {
            try (exchange) {
                handle0(exchange);
            } finally {
                responseIndex++;
            }
        }

        abstract void handle0(HttpTestExchange exchange) throws IOException;

        void sendHeaders(HttpTestExchange exchange) throws IOException {
            log(">>> sending headers (responseIndex=%d)", responseIndex);
            exchange.sendResponseHeaders(
                    200,
                    // Use `-1` to avoid generating a single DataFrame.
                    // Otherwise, server closes the stream after writing
                    // the response body, and this makes it impossible to test
                    // server pushes delivered after the response body.
                    -1);
        }

        void sendBody(HttpTestExchange exchange) throws IOException {
            log(">>> sending body (responseIndex=%d)", responseIndex);
            byte[] responseBody = "response%d".formatted(responseIndex).getBytes(US_ASCII);
            exchange.getResponseBody().write(responseBody);
        }

        void sendPushResponse(HttpTestExchange exchange) throws IOException {
            log(">>> sending push response (responseIndex=%d, pushId=%d)", responseIndex, pushId);
            byte[] pushResponseBody = "pushResponse%d".formatted(responseIndex).getBytes(US_ASCII);
            exchange.sendHttp3PushResponse(
                    pushId,
                    exchange.getRequestURI(),
                    EMPTY_HEADERS,
                    EMPTY_HEADERS,
                    new ByteArrayInputStream(pushResponseBody));
        }

        void sendPushPromise(HttpTestExchange exchange) throws IOException {
            log(">>> sending push promise (responseIndex=%d, pushId=%d)", responseIndex, pushId);
            pushId = exchange.sendHttp3PushPromiseFrame(pushId, exchange.getRequestURI(), EMPTY_HEADERS);
        }

    }

    /**
     * The maximum number of distinct push promise IDs allowed in a single response.
     */
    private static final int MAX_ALLOWED_PUSH_ID_COUNT_PER_RESPONSE = 100;

    /**
     * A value slightly more than {@link #MAX_ALLOWED_PUSH_ID_COUNT_PER_RESPONSE} to intentionally violate limits.
     */
    private static final int EXCESSIVE_PUSH_ID_COUNT_PER_RESPONSE =
            Math.addExact(10, MAX_ALLOWED_PUSH_ID_COUNT_PER_RESPONSE);

    @Test
    @Order(15)
    void testExcessivePushPromisesWithSameIdInOneResponse(TestInfo testInfo) throws Exception {
        try (HttpClient client = createClient();
             HttpTestServer server = createServer()) {

            // Configure the server handler
            URI uri = createUri(server, testInfo);
            int pushCount = EXCESSIVE_PUSH_ID_COUNT_PER_RESPONSE;
            HttpTestHandler pushSender = new ManyPushSender() {
                @Override
                void handle0(HttpTestExchange exchange) throws IOException {
                    sendPushPromise(exchange, pushCount, () -> pushId);
                }
            };
            server.addHandler(pushSender, uri.getPath());

            // Send the request
            PushReceiver pushReceiver = new PushReceiver();
            HttpRequest request = createRequest(uri);
            log("requesting `%s`...", request.uri());
            HttpResponse<String> response = client
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString(US_ASCII), pushReceiver)
                    .get();

            // Verify the response
            assertEquals(200, response.statusCode());
            assertEquals("response0", response.body());
            ReceivedPush[][] pushRefs = new ReceivedPush[pushCount][1];
            for (int i = 0; i < pushCount; i++) {
                pushRefs[i] = i == 0 ? new ReceivedPush.Promise[1] : new ReceivedPush.AdditionalPromise[1];
            }
            pushReceiver.consume(pushRefs);
            long initialPushId = ((ReceivedPush.Promise[]) pushRefs[0])[0].pushId.pushId();
            for (int i = 1; i < pushCount; i++) {
                assertEquals(
                        initialPushId, ((ReceivedPush.AdditionalPromise[]) pushRefs[i])[0].pushId.pushId(),
                        "push ID mismatch for received server push at index %d".formatted(i));
            }

        }
    }

    @Test
    @Order(16)
    void testExcessivePushPromisesWithDistinctIdsInOneResponse(TestInfo testInfo) throws Exception {
        try (HttpClient client = createClient();
             HttpTestServer server = createServer()) {

            // Configure the server handler
            URI uri = createUri(server, testInfo);
            HttpTestHandler pushSender = new ManyPushSender() {
                @Override
                void handle0(HttpTestExchange exchange) throws IOException {
                    sendPushPromise(exchange, EXCESSIVE_PUSH_ID_COUNT_PER_RESPONSE, () -> -1L);
                }
            };
            server.addHandler(pushSender, uri.getPath());

            // Send the request and verify the failure
            HttpRequest request = createRequest(uri);
            log("requesting `%s`...", request.uri());
            Exception exception = assertThrows(Exception.class, () -> client
                    .sendAsync(
                            request,
                            HttpResponse.BodyHandlers.ofString(US_ASCII),
                            // Push receiver has no semantic purpose here, but provide logs to aid troubleshooting.
                            new PushReceiver())
                    .get());
            String exceptionMessage = exception.getMessage();
            assertTrue(
                    exceptionMessage.contains("Max pushId exceeded"),
                    "Unexpected exception message: `%s`".formatted(exceptionMessage));

        }
    }

    @Test
    @Order(17)
    void testExcessivePushResponsesWithDistinctIdsInOneResponse(TestInfo testInfo) throws Exception {
        try (HttpClient client = createClient();
             HttpTestServer server = createServer()) {

            // Configure the server handler
            URI uri = createUri(server, testInfo);
            int pushCount = EXCESSIVE_PUSH_ID_COUNT_PER_RESPONSE;
            HttpTestHandler pushSender = new ManyPushSender() {
                @Override
                void handle0(HttpTestExchange exchange) throws IOException {
                    long[] returnedPushIds = sendPushPromise(exchange, pushCount, () -> -1L);
                    Queue<Long> returnedPushIdQueue = Arrays
                            .stream(returnedPushIds)
                            .boxed()
                            .collect(Collectors.toCollection(LinkedList::new));
                    sendPushResponse(exchange, pushCount, returnedPushIdQueue::poll);
                }
            };
            server.addHandler(pushSender, uri.getPath());

            // Send the request and verify the failure
            HttpRequest request = createRequest(uri);
            log("requesting `%s`...", request.uri());
            Exception exception = assertThrows(Exception.class, () -> client
                    .sendAsync(
                            request,
                            HttpResponse.BodyHandlers.ofString(US_ASCII),
                            // Push receiver has no semantic purpose here, but provide logs to aid troubleshooting.
                            new PushReceiver())
                    .get());
            String exceptionMessage = exception.getMessage();
            assertTrue(
                    exceptionMessage.contains("Max pushId exceeded"),
                    "Unexpected exception message: `%s`".formatted(exceptionMessage));

        }
    }

    /**
     * A server providing helper methods subclasses can extend to send multiple push promises &amp; responses.
     */
    private static abstract class ManyPushSender implements HttpTestHandler {

        long pushId = -1;

        private int responseIndex = 0;

        @Override
        public final synchronized void handle(HttpTestExchange exchange) throws IOException {
            try (exchange) {
                handle0(exchange);
                sendHeaders(exchange);
                sendBody(exchange);
            } finally {
                responseIndex++;
            }
        }

        abstract void handle0(HttpTestExchange exchange) throws IOException;

        private void sendHeaders(HttpTestExchange exchange) throws IOException {
            log(">>> sending headers (responseIndex=%d)", responseIndex);
            byte[] responseBody = responseBody();
            exchange.sendResponseHeaders(200, responseBody.length);
        }

        private void sendBody(HttpTestExchange exchange) throws IOException {
            log(">>> sending body (responseIndex=%d)", responseIndex);
            byte[] responseBody = responseBody();
            exchange.getResponseBody().write(responseBody);
        }

        private byte[] responseBody() {
            return "response%d".formatted(responseIndex).getBytes(US_ASCII);
        }

        long[] sendPushPromise(HttpTestExchange exchange, int count, Supplier<Long> pushIdProvider) throws IOException {
            long[] returnedPushIds = new long[count];
            for (int i = 0; i < count; i++) {
                long pushPromiseId = pushIdProvider.get();
                log(
                        ">>> sending push promise (responseIndex=%d, pushId=%d, i=%d/%d)",
                        responseIndex, pushPromiseId, i, count);
                pushId = returnedPushIds[i] =
                        exchange.sendHttp3PushPromiseFrame(pushPromiseId, exchange.getRequestURI(), EMPTY_HEADERS);
            }
            return returnedPushIds;
        }

        void sendPushResponse(HttpTestExchange exchange, int count, Supplier<Long> pushIdProvider) throws IOException {
            for (int i = 0; i < count; i++) {
                long pushResponseId = pushIdProvider.get();
                log(
                        ">>> sending push response (responseIndex=%d, pushId=%d, i=%d/%d)",
                        responseIndex, pushResponseId, i, count);
                byte[] pushResponseBody = "pushResponse%d-%d".formatted(responseIndex, i).getBytes(US_ASCII);
                exchange.sendHttp3PushResponse(
                        pushResponseId,
                        exchange.getRequestURI(),
                        EMPTY_HEADERS,
                        EMPTY_HEADERS,
                        new ByteArrayInputStream(pushResponseBody));
            }
        }

    }

    private static URI createUri(HttpTestServer server, TestInfo testInfo) {
        String uri = "https://%s/%s/%s".formatted(
                server.serverAuthority(),
                testInfo.getTestClass().map(Class::getSimpleName).orElse("UnknownClass"),
                testInfo.getTestMethod().map(Method::getName).orElse("UnknownMethod"));
        return URI.create(uri);
    }

    private static HttpRequest createRequest(URI uri) {
        return HttpRequest.newBuilder(uri).HEAD().setOption(H3_DISCOVERY, HTTP_3_URI_ONLY).build();
    }

    private static final class PushReceiver implements HttpResponse.PushPromiseHandler<String> {

        private final BlockingQueue<ReceivedPush> buffer = new LinkedBlockingQueue<>();

        @Override
        public void applyPushPromise(
                HttpRequest initiatingRequest,
                HttpRequest pushPromiseRequest,
                Function<HttpResponse.BodyHandler<String>, CompletableFuture<HttpResponse<String>>> acceptor) {
            fail("`applyPushPromise(...,PushId,...)` should have been called instead");
        }

        @Override
        public void applyPushPromise(
                HttpRequest initiatingRequest,
                HttpRequest pushPromiseRequest,
                PushId pushId,
                Function<HttpResponse.BodyHandler<String>, CompletableFuture<HttpResponse<String>>> acceptor) {
            Http3PushId http3PushId = (Http3PushId) pushId;
            buffer(new ReceivedPush.Promise(http3PushId));
            acceptor.apply(HttpResponse.BodyHandlers.ofString(US_ASCII)).thenAccept(response -> {
                assertEquals(200, response.statusCode());
                String responseBody = response.body();
                buffer(new ReceivedPush.Response(http3PushId, responseBody));
            });
        }

        @Override
        public void notifyAdditionalPromise(HttpRequest initiatingRequest, PushId pushId) {
            Http3PushId http3PushId = (Http3PushId) pushId;
            buffer(new ReceivedPush.AdditionalPromise(http3PushId));
        }

        private void buffer(ReceivedPush push) {
            log("<<< received push: `%s`", push);
            try {
                buffer.put(push);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // Restore the interrupt
                throw new RuntimeException(ie);
            }
        }

        @SuppressWarnings("rawtypes")
        private void consume(ReceivedPush[]... pushRefs) {
            int n = pushRefs.length;
            Class[] pushTypes = Arrays
                    .stream(pushRefs)
                    .map(pushRef -> pushRef.getClass().componentType())
                    .toArray(Class[]::new);
            boolean[] foundIndices = new boolean[n];
            for (int i = 0; i < n; i++) {
                ReceivedPush push;
                try {
                    push = buffer.take();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // Restore the interrupt
                    throw new RuntimeException(ie);
                }
                boolean found = false;
                for (int j = 0; j < n; j++) {
                    if (!foundIndices[j] && pushTypes[j].isInstance(push)) {
                        pushRefs[j][0] = push;
                        foundIndices[j] = true;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    log("pushRefs: %s", List.of(pushRefs));
                    log("foundIndices: %s", List.of(foundIndices));
                    log("n: %d", n);
                    log("i: %d", i);
                    log("push: %s", push);
                    fail("received push does not match with the expected types");
                }
            }
        }

    }

    private sealed interface ReceivedPush {

        record Promise(Http3PushId pushId) implements ReceivedPush {}

        record Response(Http3PushId pushId, String responseBody) implements ReceivedPush {}

        record AdditionalPromise(Http3PushId pushId) implements ReceivedPush {}

    }

    private static HttpTestServer createServer() throws IOException {
        HttpTestServer server = HttpTestServer.create(HTTP_3_URI_ONLY, SSL_CONTEXT);
        server.start();
        return server;
    }

    private static HttpClient createClient() {
        return HttpServerAdapters
                .createClientBuilderFor(HTTP_3)
                .proxy(NO_PROXY)
                .version(HTTP_3)
                .sslContext(SSL_CONTEXT)
                .build();
    }

    private static void log(String format, Object... args) {
        String text = format.formatted(args);
        System.err.printf(
                "%s [%25s] %s%n",
                LocalTime.now(),
                Thread.currentThread().getName(),
                text);
    }

}
