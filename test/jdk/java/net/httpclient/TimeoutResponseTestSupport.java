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

import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.frame.ErrorFrame;
import jdk.internal.net.http.http3.Http3Error;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpOption;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static jdk.httpclient.test.lib.common.HttpServerAdapters.createClientBuilderFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    protected static final Duration REQUEST_TIMEOUT =
            Duration.ofMillis(Long.parseLong(System.getProperty("test.requestTimeoutMillis")));

    static {
        assertTrue(
                REQUEST_TIMEOUT.isPositive(),
                "was expecting `test.requestTimeoutMillis > 0`, found: " + REQUEST_TIMEOUT);
    }

    protected static final int RETRY_LIMIT =
            Integer.parseInt(System.getProperty("jdk.httpclient.redirects.retrylimit", "0"));

    private static final long RESPONSE_FAILURE_WAIT_DURATION_MILLIS =
            Long.parseLong(System.getProperty("test.responseFailureWaitDurationMillis", "0"));

    static {
        if (RETRY_LIMIT > 0) {

            // Verify that response failure wait duration is provided
            if (RESPONSE_FAILURE_WAIT_DURATION_MILLIS <= 0) {
                var message = String.format(
                        "`jdk.httpclient.redirects.retrylimit` (%s) is greater than zero. " +
                                "`test.responseFailureWaitDurationMillis` (%s) must be greater than zero too.",
                        RETRY_LIMIT, RESPONSE_FAILURE_WAIT_DURATION_MILLIS);
                throw new AssertionError(message);
            }

            // Verify that the total response failure waits exceed the request timeout
            var totalResponseFailureWaitDuration = Duration
                    .ofMillis(RESPONSE_FAILURE_WAIT_DURATION_MILLIS)
                    .multipliedBy(RETRY_LIMIT);
            if (totalResponseFailureWaitDuration.compareTo(REQUEST_TIMEOUT) <= 0) {
                var message = ("`test.responseFailureWaitDurationMillis * jdk.httpclient.redirects.retrylimit` (%s * %s = %s) " +
                        "must be greater than `test.requestTimeoutMillis` (%s)")
                        .formatted(
                                RESPONSE_FAILURE_WAIT_DURATION_MILLIS,
                                RETRY_LIMIT,
                                totalResponseFailureWaitDuration,
                                REQUEST_TIMEOUT);
                throw new AssertionError(message);
            }

        }
    }

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

    protected record ServerRequestPair(HttpTestServer server, HttpRequest request, boolean secure) {

        private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

        private static final CountDownLatch SHUT_DOWN_LATCH = new CountDownLatch(1);

        private static final AtomicInteger SERVER_COUNTER = new AtomicInteger();

        /**
         * An arbitrary content length to cause the client wait for it.
         * It just needs to be greater than zero, and big enough to trigger a timeout when delivered slowly.
         */
        public static final int CONTENT_LENGTH = 1234;

        public enum ServerHandlerBehaviour {
            BLOCK_BEFORE_HEADER_DELIVERY,
            BLOCK_BEFORE_BODY_DELIVERY,
            DELIVER_BODY_SLOWLY,
            DELIVER_NO_BODY
        }

        public static volatile ServerHandlerBehaviour SERVER_HANDLER_BEHAVIOUR;

        public static volatile int SERVER_HANDLER_PENDING_FAILURE_COUNT = 0;

        private static ServerRequestPair of(Version version, boolean secure) {

            // Create the server and the request URI
            var sslContext = secure ? SSL_CONTEXT : null;
            var serverId = "" + SERVER_COUNTER.getAndIncrement();
            var server = createServer(version, sslContext);
            server.getVersion();
            var handlerPath = "/%s/".formatted(CLASS_NAME);
            var requestUriScheme = secure ? "https" : "http";
            var requestUri = URI.create("%s://%s%s-".formatted(requestUriScheme, server.serverAuthority(), handlerPath));

            // Register the request handler
            server.addHandler(createServerHandler(serverId), handlerPath);

            // Create the request
            var request = createRequestBuilder(requestUri, version).timeout(REQUEST_TIMEOUT).build();

            // Create the pair
            var pair = new ServerRequestPair(server, request, secure);
            pair.server.start();
            LOGGER.log("Server[%s] is started at `%s`", serverId, server.serverAuthority());
            return pair;

        }

        private static HttpTestServer createServer(Version version, SSLContext sslContext) {
            try {
                return switch (version) {
                    case HTTP_1_1, HTTP_2 -> HttpTestServer.create(version, sslContext, EXECUTOR);
                    case HTTP_3 -> HttpTestServer.create(HTTP_3_URI_ONLY, sslContext, EXECUTOR);
                };
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private static HttpTestHandler createServerHandler(String serverId) {
            return (exchange) -> {
                var connectionKey = exchange.getConnectionKey();
                LOGGER.log(
                        "Server[%s] has received request %s",
                        serverId, Map.of("connectionKey", connectionKey));
                try (exchange) {

                    // Short-circuit on `HEAD` requests.
                    // They are used for admitting established connections to the pool.
                    if ("HEAD".equals(exchange.getRequestMethod())) {
                        LOGGER.log(
                                "Server[%s] is responding to the `HEAD` request %s",
                                serverId, Map.of("connectionKey", connectionKey));
                        exchange.sendResponseHeaders(200, 0);
                        return;
                    }

                    // Short-circuit if instructed to fail
                    synchronized (ServerRequestPair.class) {
                        if (SERVER_HANDLER_PENDING_FAILURE_COUNT > 0) {
                            LOGGER.log(
                                    "Server[%s] is prematurely failing as instructed %s",
                                    serverId,
                                    Map.of(
                                            "connectionKey", connectionKey,
                                            "SERVER_HANDLER_PENDING_FAILURE_COUNT", SERVER_HANDLER_PENDING_FAILURE_COUNT));
                            // Closing the exchange will trigger an `END_STREAM` without a headers frame.
                            // This is a protocol violation, hence we must reset the stream first.
                            // We are doing so using by rejecting the stream, which is known to make the client retry.
                            if (Version.HTTP_2.equals(exchange.getExchangeVersion())) {
                                exchange.resetStream(ErrorFrame.REFUSED_STREAM);
                            } else if (Version.HTTP_3.equals(exchange.getExchangeVersion())) {
                                exchange.resetStream(Http3Error.H3_REQUEST_REJECTED.code());
                            }
                            SERVER_HANDLER_PENDING_FAILURE_COUNT--;
                            return;
                        }
                    }

                    switch (SERVER_HANDLER_BEHAVIOUR) {

                        case BLOCK_BEFORE_HEADER_DELIVERY -> sleepIndefinitely(serverId, connectionKey);

                        case BLOCK_BEFORE_BODY_DELIVERY -> {
                            sendResponseHeaders(serverId, exchange, connectionKey);
                            sleepIndefinitely(serverId, connectionKey);
                        }

                        case DELIVER_BODY_SLOWLY -> {
                            sendResponseHeaders(serverId, exchange, connectionKey);
                            sendResponseBodySlowly(serverId, exchange, connectionKey);
                        }

                        case DELIVER_NO_BODY -> sendResponseHeaders(serverId, exchange, connectionKey, 204, 0);

                    }

                } catch (Exception exception) {
                    var message = String.format(
                            "Server[%s] has failed! %s",
                            serverId, Map.of("connectionKey", connectionKey));
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
            LOGGER.log("Server[%s] is sleeping %s", serverId, Map.of("connectionKey", connectionKey));
            SHUT_DOWN_LATCH.await();
        }

        private static void sendResponseHeaders(String serverId, HttpTestExchange exchange, String connectionKey)
                throws IOException {
            sendResponseHeaders(serverId, exchange, connectionKey, 200, CONTENT_LENGTH);
        }

        private static void sendResponseHeaders(
                String serverId,
                HttpTestExchange exchange,
                String connectionKey,
                int statusCode,
                long contentLength)
                throws IOException {
            LOGGER.log("Server[%s] is sending headers %s", serverId, Map.of("connectionKey", connectionKey));
            exchange.sendResponseHeaders(statusCode, contentLength);
            // Force the headers to be flushed
            exchange.getResponseBody().flush();
        }

        private static void sendResponseBodySlowly(String serverId, HttpTestExchange exchange, String connectionKey)
                throws Exception {
            var perBytePauseDuration = Duration.ofMillis(100);
            assertTrue(
                    perBytePauseDuration.multipliedBy(CONTENT_LENGTH).compareTo(REQUEST_TIMEOUT) > 0,
                    "Per-byte pause duration (%s) must be long enough to exceed the timeout (%s) when delivering the content (%s bytes)".formatted(
                            perBytePauseDuration, REQUEST_TIMEOUT, CONTENT_LENGTH));
            try (var responseBody = exchange.getResponseBody()) {
                for (int i = 0; i < CONTENT_LENGTH; i++) {
                    LOGGER.log(
                            "Server[%s] is sending the body %s/%s %s",
                            serverId, i, CONTENT_LENGTH, Map.of("connectionKey", connectionKey));
                    responseBody.write(i);
                    responseBody.flush();
                    Thread.sleep(perBytePauseDuration);
                }
                throw new AssertionError("Delivery should never have succeeded due to timeout!");
            } catch (IOException _) {
                // Client's timeout mechanism is expected to short-circuit and cut the stream.
                // Hence, discard I/O failures.
            }
        }

        public HttpClient createClientWithEstablishedConnection() throws IOException, InterruptedException {
            var version = server.getVersion();
            var client = createClientBuilderFor(version)
                    .version(version)
                    .sslContext(SSL_CONTEXT)
                    .proxy(NO_PROXY)
                    .build();
            // Ensure an established connection is admitted to the pool. This
            // helps to cross out any possibilities of a timeout before a
            // request makes it to the server handler. For instance, consider
            // HTTP/1.1 to HTTP/2 upgrades, or long-running TLS handshakes.
            var headRequest = createRequestBuilder(request.uri(), version).HEAD().build();
            client.send(headRequest, HttpResponse.BodyHandlers.discarding());
            return client;
        }

        private static HttpRequest.Builder createRequestBuilder(URI uri, Version version) {
            var requestBuilder = HttpRequest.newBuilder(uri).version(version);
            if (Version.HTTP_3.equals(version)) {
                requestBuilder.setOption(HttpOption.H3_DISCOVERY, HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY);
            }
            return requestBuilder;
        }

        @Override
        public String toString() {
            var version = server.getVersion();
            var versionString = version.toString();
            return switch (version) {
                case HTTP_1_1, HTTP_2 -> secure ? versionString.replaceFirst("_", "S_") : versionString;
                case HTTP_3 -> versionString;
            };
        }

    }

    @AfterAll
    static void closeServers() {

        // Terminate all handlers before shutting down the server, which would block otherwise.
        ServerRequestPair.SHUT_DOWN_LATCH.countDown();
        ServerRequestPair.EXECUTOR.shutdown();

        // Shut down servers
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

    /**
     * Configures how many times the handler should fail.
     */
    @BeforeEach
    void resetServerHandlerFailureIndex() {
        ServerRequestPair.SERVER_HANDLER_PENDING_FAILURE_COUNT = Math.max(0, RETRY_LIMIT - 1);
    }

    /**
     * Ensures that the handler has failed as many times as instructed.
     */
    @AfterEach
    void verifyServerHandlerFailureIndex() {
        assertEquals(0, ServerRequestPair.SERVER_HANDLER_PENDING_FAILURE_COUNT);
    }

    protected static Stream<ServerRequestPair> serverRequestPairs() {
        return Stream.of(HTTP1, HTTPS1, HTTP2, HTTPS2, HTTP3);
    }

    protected static void assertThrowsHttpTimeoutException(Executable executable) {
        var rootException = assertThrows(Exception.class, executable);
        // Due to intricacies involved in the way exceptions are generated and
        // nested, there is no bullet-proof way to determine at which level of
        // the causal chain an `HttpTimeoutException` will show up. Hence, we
        // scan through the entire causal chain.
        Throwable exception = rootException;
        while (exception != null) {
            if (exception instanceof HttpTimeoutException) {
                return;
            }
            exception = exception.getCause();
        }
        throw new AssertionError("was expecting an `HttpTimeoutException` in the causal chain", rootException);
    }

}
