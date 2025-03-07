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
 * @bug 8328919
 * @summary tests `limiting()` in `HttpResponse.Body{Handlers,Subscribers}`
 * @key randomness
 * @library /test/lib
 *          /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.RandomFactory
 *        jdk.test.lib.net.SimpleSSLContext
 * @run junit HttpResponseLimitingTest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.test.lib.RandomFactory;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscription;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.copyOfRange;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpResponseLimitingTest {

    private static final Random RANDOM = RandomFactory.getRandom();

    private static final byte[] RESPONSE_BODY = "random non-empty body".getBytes(UTF_8);

    private static final String RESPONSE_HEADER_NAME = "X-Excessive-Data";

    /**
     * A header value larger than {@link #RESPONSE_BODY} to verify that {@code limiting()} doesn't affect header parsing.
     */
    private static final String RESPONSE_HEADER_VALUE = "!".repeat(RESPONSE_BODY.length + 1);

    private static final ServerClientPair HTTP1 = ServerClientPair.of(HttpClient.Version.HTTP_1_1, false);

    private static final ServerClientPair HTTPS1 = ServerClientPair.of(HttpClient.Version.HTTP_1_1, true);

    private static final ServerClientPair HTTP2 = ServerClientPair.of(HttpClient.Version.HTTP_2, false);

    private static final ServerClientPair HTTPS2 = ServerClientPair.of(HttpClient.Version.HTTP_2, true);

    private record ServerClientPair(HttpTestServer server, HttpClient client, HttpRequest request) {

        private static final SSLContext SSL_CONTEXT = createSslContext();

        private static SSLContext createSslContext() {
            try {
                return new SimpleSSLContext().get();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private ServerClientPair {
            try {
                server.start();
            } catch (Exception serverException) {
                try {
                    client.close();
                } catch (Exception clientException) {
                    Exception localClientException = new RuntimeException("failed closing client", clientException);
                    serverException.addSuppressed(localClientException);
                }
                throw new RuntimeException("failed closing server", serverException);
            }
        }

        private static ServerClientPair of(HttpClient.Version version, boolean secure) {

            // Create the server and the request URI
            SSLContext sslContext = secure ? SSL_CONTEXT : null;
            HttpTestServer server = createServer(version, sslContext);
            String handlerPath = "/";
            String requestUriScheme = secure ? "https" : "http";
            URI requestUri = URI.create(requestUriScheme + "://" + server.serverAuthority() + handlerPath);

            // Register the request handler
            server.addHandler(
                    (exchange) -> {
                        exchange.getResponseHeaders().addHeader(RESPONSE_HEADER_NAME, RESPONSE_HEADER_VALUE);
                        exchange.sendResponseHeaders(200, RESPONSE_BODY.length);
                        try (var outputStream = exchange.getResponseBody()) {
                            outputStream.write(RESPONSE_BODY);
                        }
                        exchange.close();
                    },
                    handlerPath);

            // Create the client and the request
            HttpClient client = createClient(version, sslContext);
            HttpRequest request = HttpRequest.newBuilder(requestUri).version(version).build();

            // Create the pair
            return new ServerClientPair(server, client, request);

        }

        private static HttpTestServer createServer(HttpClient.Version version, SSLContext sslContext) {
            try {
                return HttpTestServer.create(version, sslContext);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private static HttpClient createClient(HttpClient.Version version, SSLContext sslContext) {
            HttpClient.Builder builder = HttpClient.newBuilder().version(version).proxy(NO_PROXY);
            if (sslContext != null) {
                builder.sslContext(sslContext);
            }
            return builder.build();
        }

        private <T> HttpResponse<T> request(BodyHandler<T> downstreamHandler, long capacity) throws Exception {
            var handler = BodyHandlers.limiting(downstreamHandler, capacity);
            return client.send(request, handler);
        }

        @Override
        public String toString() {
            String version = client.version().toString();
            return client.sslContext() != null ? version.replaceFirst("_", "S_") : version;
        }

    }

    @AfterAll
    static void closeServerClientPairs() {
        Exception[] exceptionRef = {null};
        Stream
                .of(HTTP1, HTTPS1, HTTP2, HTTPS2)
                .flatMap(pair -> Stream.<Runnable>of(
                        pair.client::close,
                        pair.server::stop))
                .forEach(closer -> {
                    try {
                        closer.run();
                    } catch (Exception exception) {
                        if (exceptionRef[0] == null) {
                            exceptionRef[0] = exception;
                        } else {
                            exceptionRef[0].addSuppressed(exception);
                        }
                    }
                });
        if (exceptionRef[0] != null) {
            throw new RuntimeException("failed closing one or more server-client pairs", exceptionRef[0]);
        }
    }

    @ParameterizedTest
    @MethodSource("sufficientCapacities")
    void testSuccessOnSufficientCapacityForByteArray(ServerClientPair pair, long sufficientCapacity) throws Exception {
        HttpResponse<byte[]> response = pair.request(BodyHandlers.ofByteArray(), sufficientCapacity);
        verifyHeaders(response.headers());
        assertArrayEquals(RESPONSE_BODY, response.body());
    }

    @ParameterizedTest
    @MethodSource("sufficientCapacities")
    void testSuccessOnSufficientCapacityForInputStream(ServerClientPair pair, long sufficientCapacity) throws Exception {
        HttpResponse<InputStream> response = pair.request(BodyHandlers.ofInputStream(), sufficientCapacity);
        verifyHeaders(response.headers());
        try (InputStream responseBodyStream = response.body()) {
            byte[] responseBodyBuffer = responseBodyStream.readAllBytes();
            assertArrayEquals(RESPONSE_BODY, responseBodyBuffer);
        }
    }

    static Arguments[] sufficientCapacities() {
        long minExtremeCapacity = RESPONSE_BODY.length;
        long maxExtremeCapacity = Long.MAX_VALUE;
        long nonExtremeCapacity = RANDOM.nextLong(minExtremeCapacity + 1, maxExtremeCapacity);
        return capacityArgs(minExtremeCapacity, nonExtremeCapacity, maxExtremeCapacity);
    }

    @ParameterizedTest
    @MethodSource("insufficientCapacities")
    void testFailureOnInsufficientCapacityForByteArray(ServerClientPair pair, long insufficientCapacity) {
        BodyHandler<byte[]> handler = responseInfo -> {
            verifyHeaders(responseInfo.headers());
            return BodySubscribers.limiting(BodySubscribers.ofByteArray(), insufficientCapacity);
        };
        var exception = assertThrows(IOException.class, () -> pair.request(handler, insufficientCapacity));
        assertEquals(exception.getMessage(), "body exceeds capacity: " + insufficientCapacity);
    }

    @ParameterizedTest
    @MethodSource("insufficientCapacities")
    void testFailureOnInsufficientCapacityForInputStream(ServerClientPair pair, long insufficientCapacity) throws Exception {
        HttpResponse<InputStream> response = pair.request(BodyHandlers.ofInputStream(), insufficientCapacity);
        verifyHeaders(response.headers());
        try (InputStream responseBodyStream = response.body()) {
            var exception = assertThrows(IOException.class, responseBodyStream::readAllBytes);
            assertNotNull(exception.getCause());
            assertEquals(exception.getCause().getMessage(), "body exceeds capacity: " + insufficientCapacity);
        }
    }

    static Arguments[] insufficientCapacities() {
        long minExtremeCapacity = 0;
        long maxExtremeCapacity = RESPONSE_BODY.length - 1;
        long nonExtremeCapacity = RANDOM.nextLong(minExtremeCapacity + 1, maxExtremeCapacity);
        return capacityArgs(minExtremeCapacity, nonExtremeCapacity, maxExtremeCapacity);
    }

    private static void verifyHeaders(HttpHeaders responseHeaders) {
        List<String> responseHeaderValues = responseHeaders.allValues(RESPONSE_HEADER_NAME);
        assertEquals(List.of(RESPONSE_HEADER_VALUE), responseHeaderValues);
    }

    @ParameterizedTest
    @MethodSource("invalidCapacities")
    void testFailureOnInvalidCapacityForHandler(long invalidCapacity) {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> BodyHandlers.limiting(BodyHandlers.ofByteArray(), invalidCapacity));
        assertEquals(exception.getMessage(), "capacity must not be negative: " + invalidCapacity);
    }

    @ParameterizedTest
    @MethodSource("invalidCapacities")
    void testFailureOnInvalidCapacityForSubscriber(long invalidCapacity) {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> BodySubscribers.limiting(BodySubscribers.ofByteArray(), invalidCapacity));
        assertEquals(exception.getMessage(), "capacity must not be negative: " + invalidCapacity);
    }

    static long[] invalidCapacities() {
        long minExtremeCapacity = Long.MIN_VALUE;
        long maxExtremeCapacity = -1;
        long nonExtremeCapacity = RANDOM.nextLong(minExtremeCapacity + 1, maxExtremeCapacity);
        return new long[]{minExtremeCapacity, nonExtremeCapacity, maxExtremeCapacity};
    }

    @Test
    void testFailureOnNullDownstreamHandler() {
        var exception = assertThrows(NullPointerException.class, () -> BodyHandlers.limiting(null, 0));
        assertEquals(exception.getMessage(), "downstreamHandler");
    }

    @Test
    void testFailureOnNullDownstreamSubscriber() {
        var exception = assertThrows(NullPointerException.class, () -> BodySubscribers.limiting(null, 0));
        assertEquals(exception.getMessage(), "downstreamSubscriber");
    }

    private static Arguments[] capacityArgs(long... capacities) {
        return Stream
                .of(HTTP1, HTTPS1, HTTP2, HTTPS2)
                .flatMap(pair -> Arrays
                                .stream(capacities)
                                .mapToObj(capacity -> Arguments.of(pair, capacity)))
                .toArray(Arguments[]::new);
    }

    @Test
    void testSubscriberForCompleteConsumption() {

        // Create the subscriber (with sufficient capacity)
        ObserverSubscriber downstreamSubscriber = new ObserverSubscriber();
        int sufficientCapacity = RESPONSE_BODY.length;
        BodySubscriber<String> subscriber = BodySubscribers.limiting(downstreamSubscriber, sufficientCapacity);

        // Emit values
        subscriber.onSubscribe(DummySubscription.INSTANCE);
        byte[] responseBodyPart1 = {RESPONSE_BODY[0]};
        byte[] responseBodyPart2 = copyOfRange(RESPONSE_BODY, 1, RESPONSE_BODY.length);
        List<ByteBuffer> buffers = toByteBuffers(responseBodyPart1, responseBodyPart2);
        subscriber.onNext(buffers);

        // Verify the downstream propagation
        assertSame(buffers, downstreamSubscriber.lastBuffers);
        assertNull(downstreamSubscriber.lastThrowable);
        assertFalse(downstreamSubscriber.completed);

    }

    @Test
    void testSubscriberForFailureOnExcess() {

        // Create the subscriber (with insufficient capacity)
        ObserverSubscriber downstreamSubscriber = new ObserverSubscriber();
        int insufficientCapacity = 2;
        BodySubscriber<String> subscriber = BodySubscribers.limiting(downstreamSubscriber, insufficientCapacity);

        // Emit values
        subscriber.onSubscribe(DummySubscription.INSTANCE);
        byte[] responseBodyPart1 = {RESPONSE_BODY[0]};
        byte[] responseBodyPart2 = copyOfRange(RESPONSE_BODY, 1, RESPONSE_BODY.length);
        List<ByteBuffer> buffers = toByteBuffers(responseBodyPart1, responseBodyPart2);
        subscriber.onNext(buffers);

        // Verify the downstream propagation
        assertNull(downstreamSubscriber.lastBuffers);
        assertNotNull(downstreamSubscriber.lastThrowable);
        assertEquals(
                "body exceeds capacity: " + insufficientCapacity,
                downstreamSubscriber.lastThrowable.getMessage());
        assertFalse(downstreamSubscriber.completed);

    }

    private static List<ByteBuffer> toByteBuffers(byte[]... buffers) {
        return Arrays.stream(buffers).map(ByteBuffer::wrap).collect(Collectors.toList());
    }

    private static final class ObserverSubscriber implements BodySubscriber<String> {

        private List<ByteBuffer> lastBuffers;

        private Throwable lastThrowable;

        private boolean completed;

        @Override
        public CompletionStage<String> getBody() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            lastBuffers = buffers;
        }

        @Override
        public void onError(Throwable throwable) {
            lastThrowable = throwable;
        }

        @Override
        public void onComplete() {
            completed = true;
        }

    }

    private enum DummySubscription implements Subscription {

        INSTANCE;

        @Override
        public void request(long n) {
            // Do nothing
        }

        @Override
        public void cancel() {
            // Do nothing
        }

    }

}
