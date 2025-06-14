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
 * @test
 * @summary Verifies `HttpResponse::connectionLabel`
 * @library /test/lib
 *          /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 *
 * @comment Use a higher idle timeout to increase the chances of the same connection being used for sequential HTTP requests
 * @run junit/othervm -Djdk.httpclient.keepalive.timeout=120 HttpResponseConnectionLabelTest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HttpResponseConnectionLabelTest {

    private static final String CLASS_NAME = HttpResponseConnectionLabelTest.class.getSimpleName();

    private static final Logger LOGGER = Utils.getDebugLogger(CLASS_NAME::toString, Utils.DEBUG);

    private static final Charset CHARSET = US_ASCII;

    private static final String CONNECTION_KEY_HEADER_NAME = "X-Connection-Key";

    private static final String SERVER_ID_HEADER_NAME = "X-Server-Id";

    private static final SSLContext SSL_CONTEXT = createSslContext();

    // Start with a fresh client having no connections in the pool
    private final HttpClient client = HttpClient.newBuilder().sslContext(SSL_CONTEXT).proxy(NO_PROXY).build();

    // Primary server-client pairs

    private static final ServerRequestPair PRI_HTTP1 = ServerRequestPair.of(Version.HTTP_1_1, false);

    private static final ServerRequestPair PRI_HTTPS1 = ServerRequestPair.of(Version.HTTP_1_1, true);

    private static final ServerRequestPair PRI_HTTP2 = ServerRequestPair.of(Version.HTTP_2, false);

    private static final ServerRequestPair PRI_HTTPS2 = ServerRequestPair.of(Version.HTTP_2, true);

    // Secondary server-client pairs

    private static final ServerRequestPair SEC_HTTP1 = ServerRequestPair.of(Version.HTTP_1_1, false);

    private static final ServerRequestPair SEC_HTTPS1 = ServerRequestPair.of(Version.HTTP_1_1, true);

    private static final ServerRequestPair SEC_HTTP2 = ServerRequestPair.of(Version.HTTP_2, false);

    private static final ServerRequestPair SEC_HTTPS2 = ServerRequestPair.of(Version.HTTP_2, true);

    private static SSLContext createSslContext() {
        try {
            return new SimpleSSLContext().get();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record ServerRequestPair(
            HttpTestServer server,
            ExecutorService executor,
            HttpRequest request,
            boolean secure,
            AtomicReference<CountDownLatch> serverResponseLatchRef) {

        private static final AtomicInteger SERVER_COUNTER = new AtomicInteger();

        private static final AtomicInteger SERVER_RESPONSE_COUNTER = new AtomicInteger();

        private static ServerRequestPair of(Version version, boolean secure) {

            // Create the server and the request URI
            SSLContext sslContext = secure ? SSL_CONTEXT : null;
            String serverId = "" + SERVER_COUNTER.getAndIncrement();
            ExecutorService[] executorRef = {null};
            HttpTestServer server = createServer(version, secure, sslContext, serverId, executorRef);
            String handlerPath = "/%s/".formatted(CLASS_NAME);
            String requestUriScheme = secure ? "https" : "http";
            URI requestUri = URI.create("%s://%s%sx".formatted(requestUriScheme, server.serverAuthority(), handlerPath));

            // Register the request handler
            AtomicReference<CountDownLatch> serverResponseLatchRef = new AtomicReference<>();
            server.addHandler(createServerHandler(serverId, serverResponseLatchRef), handlerPath);

            // Create the client and the request
            HttpRequest request = HttpRequest.newBuilder(requestUri).version(version).build();

            // Create the pair
            ServerRequestPair pair = new ServerRequestPair(
                    server,
                    executorRef[0],
                    request,
                    secure,
                    serverResponseLatchRef);
            pair.server.start();
            LOGGER.log("Server[%s] is started at `%s`", serverId, server.serverAuthority());
            return pair;

        }

        private static HttpTestServer createServer(
                Version version,
                boolean secure,
                SSLContext sslContext,
                String serverId,
                ExecutorService[] executorRef) {
            try {
                // Only create a dedicated executor for HTTP/1.1, because
                //
                // - Only the HTTP/1.1 test server gets wedged when running
                //   tests involving parallel request handling.
                //
                // - The HTTP/2 test server creates its own sufficiently sized
                //   executor, and the thread names used there makes it easy to
                //   find which server they belong to.
                executorRef[0] = Version.HTTP_1_1.equals(version)
                        ? createExecutor(version, secure, serverId)
                        : null;
                return HttpTestServer.create(version, sslContext, executorRef[0]);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private static ExecutorService createExecutor(Version version, boolean secure, String serverId) {
            return Executors.newThreadPerTaskExecutor(runnable -> {
                String name = "%s-%s-%c-%s".formatted(
                        CLASS_NAME, version, secure ? 's' : 'c', serverId);
                Thread thread = new Thread(runnable, name);
                thread.setDaemon(true);     // Avoid blocking the JVM exit
                return thread;
            });
        }

        private static HttpTestHandler createServerHandler(
                String serverId,
                AtomicReference<CountDownLatch> serverResponseLatchRef) {
            return (exchange) -> {
                String responseBody = "" + SERVER_RESPONSE_COUNTER.getAndIncrement();
                String connectionKey = exchange.getConnectionKey();
                LOGGER.log("Server[%d] has received request (connectionKey=%s)", serverId, connectionKey);
                try (exchange) {

                    // Participate in the latch count down
                    CountDownLatch serverResponseLatch = serverResponseLatchRef.get();
                    if (serverResponseLatch != null) {
                        serverResponseLatch.countDown();
                        LOGGER.log(
                                "Server[%s] is waiting for the latch... (connectionKey=%s, responseBody=%s)",
                                serverId, connectionKey, responseBody);
                        serverResponseLatch.await();
                    }

                    // Write the response
                    LOGGER.log(
                            "Server[%s] is responding... (connectionKey=%s, responseBody=%s)",
                            serverId, connectionKey, responseBody);
                    exchange.getResponseHeaders().addHeader(CONNECTION_KEY_HEADER_NAME, connectionKey);
                    exchange.getResponseHeaders().addHeader(SERVER_ID_HEADER_NAME, serverId);
                    byte[] responseBodyBytes = responseBody.getBytes(CHARSET);
                    exchange.sendResponseHeaders(200, responseBodyBytes.length);
                    exchange.getResponseBody().write(responseBodyBytes);

                } catch (Exception exception) {
                    String message = "Server[%s] has failed! (connectionKey=%s, responseBody=%s)"
                            .formatted(serverId, connectionKey, responseBody);
                    LOGGER.log(Level.ERROR, message, exception);
                    if (exception instanceof InterruptedException) {
                        // Restore the interrupt
                        Thread.currentThread().interrupt();
                    }
                    throw new RuntimeException(message, exception);
                }
            };
        }

        @Override
        public String toString() {
            String version = server.getVersion().toString();
            return secure ? version.replaceFirst("_", "S_") : version;
        }

    }

    @AfterAll
    static void closeServers() {
        Exception[] exceptionRef = {null};
        Stream
                .of(PRI_HTTP1, PRI_HTTPS1, PRI_HTTP2, PRI_HTTPS2, SEC_HTTP1, SEC_HTTPS1, SEC_HTTP2, SEC_HTTPS2)
                .flatMap(pair -> Stream.<Runnable>of(
                        pair.server::stop,
                        () -> { if (pair.executor != null) { pair.executor.shutdownNow(); } }))
                .forEach(terminator -> {
                    try {
                        terminator.run();
                    } catch (Exception exception) {
                        if (exceptionRef[0] == null) {
                            exceptionRef[0] = exception;
                        } else {
                            exceptionRef[0].addSuppressed(exception);
                        }
                    }
                });
        if (exceptionRef[0] != null) {
            throw new RuntimeException("failed closing one or more server resources", exceptionRef[0]);
        }
    }

    @AfterEach
    void closeClient() {
        client.close();
    }

    static ServerRequestPair[] testParallelRequestsToSameServer() {
        return new ServerRequestPair[]{
                PRI_HTTP1,
                PRI_HTTPS1,
                PRI_HTTP2,
                PRI_HTTPS2
        };
    }

    @ParameterizedTest
    @MethodSource("testParallelRequestsToSameServer")
    void testParallelRequestsToSameServer(ServerRequestPair pair) throws Exception {

        // There is no implementation-agnostic reliable way to force admission
        // of multiple connections targeting the same server to an HTTP/2 pool.
        if (Version.HTTP_2.equals(pair.server.getVersion())) {
            return;
        }

        // Configure a synchronization point for 4 events:
        //
        // 1. client --(req1)--> server
        // 2. server --(res1)--> client
        // 3. client --(req2)--> server
        // 4. server --(res2)--> client
        //
        // This effectively will ensure:
        //
        // - Server waits for the rendezvous before responding.
        //   Hence, client won't be able to reuse the connection, but create a new one.
        //
        // - Client waits for the rendezvous before consuming responses.
        CountDownLatch latch = new CountDownLatch(4);
        pair.serverResponseLatchRef.set(latch);

        // Fire requests
        LOGGER.log("Firing request 1...");
        CompletableFuture<HttpResponse<String>> response1Future =
                client.sendAsync(pair.request, BodyHandlers.ofString(CHARSET));
        LOGGER.log("Firing request 2...");
        CompletableFuture<HttpResponse<String>> response2Future =
                client.sendAsync(pair.request, BodyHandlers.ofString(CHARSET));

        // Release latches to allow the server handlers to proceed
        latch.countDown();
        latch.countDown();

        // Wait for all parties to be ready
        LOGGER.log("Client is waiting for the latch...");
        latch.await();
        LOGGER.log("Client is continuing...");

        // Collect responses
        HttpResponse<String> response1 = response1Future.get();
        HttpResponse<String> response2 = response2Future.get();

        // Verify successful responses
        assertEquals(200, response1.statusCode());
        assertEquals(200, response2.statusCode());

        // Verify that connection keys differ; that is, requests are served through different connections
        String connectionKey1 = response1.headers().firstValue(CONNECTION_KEY_HEADER_NAME).get();
        String connectionKey2 = response2.headers().firstValue(CONNECTION_KEY_HEADER_NAME).get();
        assertNotEquals(connectionKey1, connectionKey2);

        // Verify that server IDs match; that is, both requests targeted the same server.
        // (Using `parseInt` to validate the content.)
        int serverId1 = Integer.parseInt(response1.headers().firstValue(SERVER_ID_HEADER_NAME).get());
        int serverId2 = Integer.parseInt(response2.headers().firstValue(SERVER_ID_HEADER_NAME).get());
        assertEquals(serverId1, serverId2);

        // Verify that response bodies differ.
        // (Using `parseInt` to validate the content.)
        int body1 = Integer.parseInt(response1.body());
        int body2 = Integer.parseInt(response2.body());
        assertNotEquals(body1, body2);

        // Verify that connection labels differ; that is, requests are served through different connections
        String label1 = response1.connectionLabel().orElse(null);
        assertNotNull(label1);
        LOGGER.log("Connection label 1: %s", label1);
        String label2 = response2.connectionLabel().orElse(null);
        assertNotNull(label2);
        LOGGER.log("Connection label 2: %s", label2);
        assertNotEquals(label1, label2);

    }

    static Stream<Arguments> testParallelRequestsToDifferentServers() {
        return Stream
                .of(PRI_HTTP1, PRI_HTTPS1, PRI_HTTP2, PRI_HTTPS2)
                .flatMap(source -> Stream
                        .of(SEC_HTTP1, SEC_HTTPS1, SEC_HTTP2, SEC_HTTPS2)
                        .map(target -> Arguments.of(source, target)));
    }

    @ParameterizedTest
    @MethodSource("testParallelRequestsToDifferentServers")
    void testParallelRequestsToDifferentServers(ServerRequestPair pair1, ServerRequestPair pair2) throws Exception {

        // Configure a synchronization point for 4 events:
        //
        // 1. client  --> server1
        // 2. server1 --> client
        // 3. client  --> server2
        // 4. server2 --> client
        //
        // This effectively will ensure:
        //
        // - Server waits for the rendezvous before responding.
        //   Hence, client won't be able to reuse the connection, but create a new one.
        //
        // - Client waits for the rendezvous before consuming responses.
        CountDownLatch latch = new CountDownLatch(4);
        pair1.serverResponseLatchRef.set(latch);
        pair2.serverResponseLatchRef.set(latch);

        // Fire requests
        LOGGER.log("Firing request 1...");
        CompletableFuture<HttpResponse<String>> response1Future =
                client.sendAsync(pair1.request, BodyHandlers.ofString(CHARSET));
        LOGGER.log("Firing request 2...");
        CompletableFuture<HttpResponse<String>> response2Future =
                client.sendAsync(pair2.request, BodyHandlers.ofString(CHARSET));

        // Release latches to allow the server handlers to proceed
        latch.countDown();
        latch.countDown();

        // Wait for all parties to be ready
        LOGGER.log("Client is waiting for the latch...");
        latch.await();
        LOGGER.log("Client is continuing...");

        // Collect responses
        HttpResponse<String> response1 = response1Future.get();
        HttpResponse<String> response2 = response2Future.get();

        // Verify successful responses
        assertEquals(200, response1.statusCode());
        assertEquals(200, response2.statusCode());

        // Verify that connection keys differ; that is, requests are served through different connections
        String connectionKey1 = response1.headers().firstValue(CONNECTION_KEY_HEADER_NAME).get();
        String connectionKey2 = response2.headers().firstValue(CONNECTION_KEY_HEADER_NAME).get();
        assertNotEquals(connectionKey1, connectionKey2);

        // Verify that server IDs differ.
        // (Using `parseInt` to validate the content.)
        int serverId1 = response1.headers().firstValue(SERVER_ID_HEADER_NAME).map(Integer::parseInt).get();
        int serverId2 = response2.headers().firstValue(SERVER_ID_HEADER_NAME).map(Integer::parseInt).get();
        assertNotEquals(serverId1, serverId2);

        // Verify that response bodies differ.
        // (Using `parseInt` to validate the content.)
        int body1 = Integer.parseInt(response1.body());
        int body2 = Integer.parseInt(response2.body());
        assertNotEquals(body1, body2);

        // Verify that connection labels differ; that is, requests are served through different connections
        String label1 = response1.connectionLabel().orElse(null);
        assertNotNull(label1);
        LOGGER.log("Connection label 1: %s", label1);
        String label2 = response2.connectionLabel().orElse(null);
        assertNotNull(label2);
        LOGGER.log("Connection label 2: %s", label2);
        assertNotEquals(label1, label2);

    }

    static Stream<ServerRequestPair> testSerialRequestsToSameServer() {
        return Stream.of(PRI_HTTP1, PRI_HTTPS1, PRI_HTTP2, PRI_HTTPS2);
    }

    @ParameterizedTest
    @MethodSource("testSerialRequestsToSameServer")
    void testSerialRequestsToSameServer(ServerRequestPair pair) throws Exception {

        // Disarm the synchronization point
        pair.serverResponseLatchRef.set(null);

        // Fire requests
        LOGGER.log("Firing request 1...");
        HttpResponse<String> response1 = client.send(pair.request, BodyHandlers.ofString(CHARSET));
        LOGGER.log("Firing request 2...");
        HttpResponse<String> response2 = client.send(pair.request, BodyHandlers.ofString(CHARSET));

        // Verify successful responses
        assertEquals(200, response1.statusCode());
        assertEquals(200, response2.statusCode());

        // Verify that connection keys match; that is, requests are served through the same connection
        String connectionKey1 = response1.headers().firstValue(CONNECTION_KEY_HEADER_NAME).get();
        String connectionKey2 = response2.headers().firstValue(CONNECTION_KEY_HEADER_NAME).get();
        assertEquals(connectionKey1, connectionKey2);

        // Verify that server IDs match.
        // (Using `parseInt` to validate the content.)
        int serverId1 = response1.headers().firstValue(SERVER_ID_HEADER_NAME).map(Integer::parseInt).get();
        int serverId2 = response2.headers().firstValue(SERVER_ID_HEADER_NAME).map(Integer::parseInt).get();
        assertEquals(serverId1, serverId2);

        // Verify that response bodies differ.
        // (Using `parseInt` to validate the content.)
        int body1 = Integer.parseInt(response1.body());
        int body2 = Integer.parseInt(response2.body());
        assertNotEquals(body1, body2);

        // Verify that connection labels match; that is, requests are served through the same connection
        String label1 = response1.connectionLabel().orElse(null);
        assertNotNull(label1);
        LOGGER.log("Connection label 1: %s", label1);
        String label2 = response2.connectionLabel().orElse(null);
        assertNotNull(label2);
        LOGGER.log("Connection label 2: %s", label2);
        assertEquals(label1, label2);

    }

}
