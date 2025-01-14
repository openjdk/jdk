/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @summary verifies `limiting()` behaviour in `HttpResponse.Body{Handlers,Subscribers}`
 * @library /test/lib
 * @run junit HttpResponseLimitingTest
 */

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscription;
import java.util.stream.Collectors;

import static java.util.Arrays.copyOfRange;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpResponseLimitingTest {

    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private static final HttpServer SERVER = new HttpServer();

    private static final HttpRequest REQUEST = HttpRequest
            .newBuilder(URI.create("http://localhost:" + SERVER.socket.getLocalPort()))
            .timeout(Duration.ofSeconds(5))
            .build();

    private static final HttpClient CLIENT = HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @AfterAll
    static void tearDown() throws Exception {
        CLIENT.close();
        SERVER.close();
    }

    @ParameterizedTest
    @MethodSource("sufficientCapacities")
    void testSuccessOnSufficientCapacity(long sufficientCapacity) throws Exception {
        BodyHandler<byte[]> handler =
                BodyHandlers.limiting(BodyHandlers.ofByteArray(), sufficientCapacity);
        HttpResponse<byte[]> response = CLIENT.send(REQUEST, handler);
        assertArrayEquals(HttpServer.RESPONSE_BODY, response.body());
    }

    static long[] sufficientCapacities() {
        return new long[]{Long.MAX_VALUE, HttpServer.RESPONSE_BODY.length};
    }

    @ParameterizedTest
    @MethodSource("insufficientCapacities")
    void testFailureOnInsufficientCapacity(long insufficientCapacity) {
        assertThrows(
                IOException.class,
                () -> {
                    BodyHandler<byte[]> handler =
                            BodyHandlers.limiting(BodyHandlers.ofByteArray(), insufficientCapacity);
                    CLIENT.send(REQUEST, handler);
                },
                "the maximum number of bytes that are allowed to be consumed is exceeded");
    }

    static long[] insufficientCapacities() {
        return new long[]{0, HttpServer.RESPONSE_BODY.length - 1};
    }

    @Test
    void testSubscriberForCompleteConsumption() {

        // Create the subscriber (with sufficient capacity)
        ObserverSubscriber downstreamSubscriber = new ObserverSubscriber();
        int sufficientCapacity = HttpServer.RESPONSE_BODY.length;
        BodySubscriber<String> subscriber = BodySubscribers.limiting(downstreamSubscriber, sufficientCapacity);

        // Emit values
        subscriber.onSubscribe(DummySubscription.INSTANCE);
        byte[] responseBodyPart1 = {HttpServer.RESPONSE_BODY[0]};
        byte[] responseBodyPart2 = copyOfRange(HttpServer.RESPONSE_BODY, 1, HttpServer.RESPONSE_BODY.length);
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
        byte[] responseBodyPart1 = {HttpServer.RESPONSE_BODY[0]};
        byte[] responseBodyPart2 = copyOfRange(HttpServer.RESPONSE_BODY, 1, HttpServer.RESPONSE_BODY.length);
        List<ByteBuffer> buffers = toByteBuffers(responseBodyPart1, responseBodyPart2);
        subscriber.onNext(buffers);

        // Verify the downstream propagation
        assertNull(downstreamSubscriber.lastBuffers);
        assertNotNull(downstreamSubscriber.lastThrowable);
        assertEquals(
                "the maximum number of bytes that are allowed to be consumed is exceeded",
                downstreamSubscriber.lastThrowable.getMessage());
        assertFalse(downstreamSubscriber.completed);

    }

    private static List<ByteBuffer> toByteBuffers(byte[]... buffers) {
        return Arrays.stream(buffers).map(ByteBuffer::wrap).collect(Collectors.toList());
    }

    /**
     * An HTTP server always returning an excessive response.
     */
    private static final class HttpServer implements Runnable, AutoCloseable {

        private static final byte[] RESPONSE_BODY = "random non-empty body".getBytes(CHARSET);

        private static final byte[] RESPONSE = (
                "HTTP/1.2 200 OK\r\n" +
                        "Content-Length: " + RESPONSE_BODY.length + "\r\n" +
                        "\r\n" +
                        new String(RESPONSE_BODY, CHARSET))
                .getBytes(CHARSET);

        private final ServerSocket socket;

        private final Thread thread;

        private HttpServer() {
            try {
                this.socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            this.thread = new Thread(this);
            thread.setDaemon(true);     // Avoid blocking JVM exit
            thread.start();
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try (Socket clientSocket = socket.accept();
                     OutputStream outputStream = clientSocket.getOutputStream()) {
                    outputStream.write(RESPONSE);
                } catch (IOException _) {
                    // Do nothing
                }
            }
        }

        @Override
        public void close() throws Exception {
            socket.close();
            thread.interrupt();
        }

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
