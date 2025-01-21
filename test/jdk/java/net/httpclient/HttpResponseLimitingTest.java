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
 * @library /test/lib
 *          /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run junit HttpResponseLimitingTest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscription;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final byte[] RESPONSE_BODY = "random non-empty body".getBytes(UTF_8);

    @ParameterizedTest
    @MethodSource("sufficientCapacities")
    void testSuccessOnSufficientCapacity(HttpClient.Version version, boolean secure, long sufficientCapacity) throws Exception {
        HttpResponse<byte[]> response = requestBytes(version, secure, sufficientCapacity);
        assertArrayEquals(RESPONSE_BODY, response.body());
    }

    static Arguments[] sufficientCapacities() {
        return capacityArgs(Long.MAX_VALUE, RESPONSE_BODY.length);
    }

    @ParameterizedTest
    @MethodSource("insufficientCapacities")
    void testFailureOnInsufficientCapacity(HttpClient.Version version, boolean secure, long insufficientCapacity) {
        assertThrows(
                IOException.class,
                () -> requestBytes(version, secure, insufficientCapacity),
                "body exceeds capacity: " + RESPONSE_BODY.length);
    }

    static Arguments[] insufficientCapacities() {
        return capacityArgs(0, RESPONSE_BODY.length - 1);
    }

    private static Arguments[] capacityArgs(long... capacities) {
        return Stream
                .of(HttpClient.Version.HTTP_1_1, HttpClient.Version.HTTP_2)
                .flatMap(version -> Stream
                        .of(true, false)
                        .flatMap(secure -> Arrays
                                .stream(capacities)
                                .mapToObj(capacity -> Arguments.of(version, secure, capacity))))
                .toArray(Arguments[]::new);
    }

    private static HttpResponse<byte[]> requestBytes(
            HttpClient.Version version,
            boolean secure,
            long capacity)
            throws Exception {

        // Create the server and the request URI
        SSLContext sslContext;
        HttpServerAdapters.HttpTestServer server;
        String handlerPath = "/";
        URI requestUri;
        if (secure) {
            sslContext = new SimpleSSLContext().get();
            server = HttpServerAdapters.HttpTestServer.create(version, sslContext);
            requestUri = URI.create("https://" + server.serverAuthority() + handlerPath);
        } else {
            sslContext = null;
            server = HttpServerAdapters.HttpTestServer.create(version);
            requestUri = URI.create("http://" + server.serverAuthority() + handlerPath);
        }

        // Register the request handler
        server.addHandler(
                (exchange) -> {
                    exchange.sendResponseHeaders(200, RESPONSE_BODY.length);
                    try (var outputStream = exchange.getResponseBody()) {
                        outputStream.write(RESPONSE_BODY);
                    }
                    exchange.close();
                },
                handlerPath);

        // Start the server and the client
        server.start();
        try (var client = createClient(sslContext)) {

            // Issue the request
            var request = HttpRequest
                    .newBuilder(requestUri)
                    .timeout(Duration.ofSeconds(5))
                    .build();
            var handler = BodyHandlers.limiting(BodyHandlers.ofByteArray(), capacity);
            return client.send(request, handler);

        } finally {
            server.stop();
        }

    }

    private static HttpClient createClient(SSLContext sslContext) {
        HttpClient.Builder builder = HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(5));
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder.build();
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
