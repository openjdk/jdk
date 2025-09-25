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
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.AfterAll;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utilities for {@code TimeoutResponse*Test}s.
 *
 * @see TimeoutResponseBodyTest Server <b>response body</b> timeout tests
 * @see TimeoutResponseHeaderTest Server <b>response header</b> timeout tests
 * @see TimeoutBasic Server <b>connection</b> timeout tests
 */
public class TimeoutResponseTestSupport {

    private static final String CLASS_NAME = TimeoutResponseTestSupport.class.getSimpleName();

    private static final Logger LOGGER = Utils.getDebugLogger(CLASS_NAME::toString, Utils.DEBUG);

    private static final SSLContext SSL_CONTEXT = createSslContext();

    /**
     * A timeout long enough for all test platforms to ensure that the request reaches to the handler.
     */
    protected static final Duration TIMEOUT = Duration.ofSeconds(1);

    protected static final ServerRequestPair
            HTTP1 = ServerRequestPair.of(Version.HTTP_1_1, false),
            HTTPS1 = ServerRequestPair.of(Version.HTTP_1_1, true),
            HTTP2 = ServerRequestPair.of(Version.HTTP_2, false),
            HTTPS2 = ServerRequestPair.of(Version.HTTP_2, true),
            HTTP3 = ServerRequestPair.of(Version.HTTP_3, true);

    private static SSLContext createSslContext() {
        try {
            return new SimpleSSLContext().get();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    protected record ServerRequestPair(
            HttpServerAdapters.HttpTestServer server,
            HttpRequest request,
            boolean secure) {

        private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

        private static final AtomicInteger SERVER_COUNTER = new AtomicInteger();

        /**
         * An arbitrary content length to cause the client wait for it.
         * It just needs to be greater than zero, and big enough to trigger a timeout when delivered slowly.
         */
        public static final int CONTENT_LENGTH = 1234;

        public enum ServerHandlerBehaviour {
            BLOCK_BEFORE_HEADER_DELIVERY,
            BLOCK_BEFORE_BODY_DELIVERY,
            DELIVER_BODY_SLOWLY
        }

        public static volatile ServerHandlerBehaviour SERVER_HANDLER_BEHAVIOUR;

        private static ServerRequestPair of(Version version, boolean secure) {

            // Create the server and the request URI
            SSLContext sslContext = secure ? SSL_CONTEXT : null;
            String serverId = "" + SERVER_COUNTER.getAndIncrement();
            HttpServerAdapters.HttpTestServer server = createServer(version, sslContext);
            server.getVersion();
            String handlerPath = "/%s/".formatted(CLASS_NAME);
            String requestUriScheme = secure ? "https" : "http";
            URI requestUri = URI.create("%s://%s%s-".formatted(requestUriScheme, server.serverAuthority(), handlerPath));

            // Register the request handler
            server.addHandler(createServerHandler(serverId), handlerPath);

            // Create the request
            HttpRequest request = HttpRequest.newBuilder(requestUri).version(version).timeout(TIMEOUT).build();

            // Create the pair
            ServerRequestPair pair = new ServerRequestPair(server, request, secure);
            pair.server.start();
            LOGGER.log("Server[%s] is started at `%s`", serverId, server.serverAuthority());
            return pair;

        }

        private static HttpServerAdapters.HttpTestServer createServer(
                Version version,
                SSLContext sslContext) {
            try {
                return switch (version) {
                    case HTTP_1_1, HTTP_2 -> HttpServerAdapters.HttpTestServer.create(version, sslContext, EXECUTOR);
                    case HTTP_3 -> HttpServerAdapters.HttpTestServer.create(HTTP_3_URI_ONLY, sslContext, EXECUTOR);
                };
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private static HttpServerAdapters.HttpTestHandler createServerHandler(String serverId) {
            return (exchange) -> {
                String connectionKey = exchange.getConnectionKey();
                LOGGER.log("Server[%s] has received request (connectionKey=%s)", serverId, connectionKey);
                try (exchange) {

                    // Short-circuit on `HEAD` requests.
                    // They are used for admitting established connections to the pool.
                    if ("HEAD".equals(exchange.getRequestMethod())) {
                        LOGGER.log("Server[%s] is responding to the `HEAD` request (connectionKey=%s)", serverId, connectionKey);
                        exchange.sendResponseHeaders(200, 0);
                        return;
                    }

                    switch (SERVER_HANDLER_BEHAVIOUR) {

                        case BLOCK_BEFORE_HEADER_DELIVERY:
                            sleepIndefinitely(serverId, connectionKey);
                            break;

                        case BLOCK_BEFORE_BODY_DELIVERY:
                            sendResponseHeaders(serverId, exchange, connectionKey);
                            sleepIndefinitely(serverId, connectionKey);
                            break;

                        case DELIVER_BODY_SLOWLY:
                            sendResponseHeaders(serverId, exchange, connectionKey);
                            sendResponseBodySlowly(serverId, exchange, connectionKey);
                            break;
                    }

                } catch (Exception exception) {
                    String message = "Server[%s] has failed! (connectionKey=%s)".formatted(serverId, connectionKey);
                    LOGGER.log(System.Logger.Level.ERROR, message, exception);
                    if (exception instanceof InterruptedException) {
                        // Restore the interrupt
                        Thread.currentThread().interrupt();
                    }
                    throw new RuntimeException(message, exception);
                }
            };
        }

        private static void sleepIndefinitely(String serverId, String connectionKey) throws InterruptedException {
            LOGGER.log("Server[%s] is sleeping (connectionKey=%s)", serverId, connectionKey);
            Thread.sleep(Long.MAX_VALUE);
        }

        private static void sendResponseHeaders(
                String serverId,
                HttpServerAdapters.HttpTestExchange exchange,
                String connectionKey)
                throws IOException {
            LOGGER.log("Server[%s] is sending headers (connectionKey=%s)", serverId, connectionKey);
            exchange.sendResponseHeaders(200, CONTENT_LENGTH);
            // Force the headers to be flushed
            exchange.getResponseBody().flush();
        }

        private static void sendResponseBodySlowly(
                String serverId,
                HttpServerAdapters.HttpTestExchange exchange,
                String connectionKey)
                throws Exception {
            Duration perBytePauseDuration = Duration.ofMillis(100);
            assertTrue(
                    perBytePauseDuration.multipliedBy(CONTENT_LENGTH).compareTo(TIMEOUT) > 0,
                    "Per-byte pause duration (%s) must be long enough to exceed the timeout (%s) when delivering the content (%s bytes)".formatted(
                            perBytePauseDuration, TIMEOUT, CONTENT_LENGTH));
            try (OutputStream responseBody = exchange.getResponseBody()) {
                for (int i = 0; i < CONTENT_LENGTH; i++) {
                    LOGGER.log(
                            "Server[%s] is sending the body %s/%s (connectionKey=%s)",
                            serverId, i, CONTENT_LENGTH, connectionKey);
                    responseBody.write(i);
                    responseBody.flush();
                    Thread.sleep(perBytePauseDuration);
                }
            }
        }

        public HttpClient createClientWithEstablishedConnection() throws IOException, InterruptedException {
            Version version = server.getVersion();
            HttpClient client = HttpServerAdapters
                    .createClientBuilderFor(version)
                    .version(version)
                    .sslContext(SSL_CONTEXT)
                    .proxy(NO_PROXY)
                    .build();
            // Ensure an established connection is admitted to the pool. This
            // helps to cross out any possibilities of a timeout before a
            // request makes it to the server handler. For instance, consider
            // HTTP/1.1 to HTTP/2 upgrades, or long-running TLS handshakes.
            HttpRequest headRequest = HttpRequest.newBuilder(this.request.uri()).version(version).HEAD().build();
            client.send(headRequest, HttpResponse.BodyHandlers.discarding());
            return client;
        }

        @Override
        public String toString() {
            Version version = server.getVersion();
            String versionString = version.toString();
            return switch (version) {
                case HTTP_1_1, HTTP_2 -> secure ? versionString.replaceFirst("_", "S_") : versionString;
                case HTTP_3 -> versionString;
            };
        }

    }

    @AfterAll
    static void closeServers() {
        // Terminate all handlers before shutting down the server, which would block otherwise.
        ServerRequestPair.EXECUTOR.shutdownNow();
        Exception[] exceptionRef = {null};
        serverRequestPairs()
                .forEach(pair -> {
                    try {
                        pair.server.stop();
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

    protected static Stream<ServerRequestPair> serverRequestPairs() {
        return Stream.of(HTTP1, HTTPS1, HTTP2, HTTPS2, HTTP3);
    }

}
