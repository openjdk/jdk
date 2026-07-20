/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.ByteBuffer.wrap;

/**
 * An HttpClient that returns a given fixed response.
 * Suitable for testing where network connections are to be avoided.
 * Can delegate to an actual HttpClient created from a supplied
 * HttpClient.Builder if needed, by calling methods on its
 * DelegatingHttpClient super class.
 */
public class FixedResponseHttpClient extends DelegatingHttpClient {
    private final ByteBuffer responseBodyBytes;
    private final int responseStatusCode;
    private final HttpHeaders responseHeaders;
    private final HttpClient.Version responseVersion;
    private final HttpResponse.ResponseInfo responseInfo;
    private final ConcurrentLinkedQueue<CompletableFuture<?>> responses = new ConcurrentLinkedQueue();
    volatile boolean shutdownRequested;

    private FixedResponseHttpClient(HttpClient.Builder builder,
                                    int responseStatusCode,
                                    HttpHeaders responseHeaders,
                                    ByteBuffer responseBodyBytes) {
        super(builder.build());
        this.responseStatusCode = responseStatusCode;
        this.responseHeaders = responseHeaders;
        this.responseBodyBytes = responseBodyBytes;
        this.responseVersion = HttpClient.Version.HTTP_1_1; // should be added to constructor
        this.responseInfo = new FixedResponseInfo();
    }

    /**
     * Creates a new HttpClient that returns a fixed response,
     * constructed from the given values, for every request sent.
     */
    public static HttpClient createClientFrom(HttpClient.Builder builder,
                                              int responseStatusCode,
                                              HttpHeaders responseHeaders,
                                              String responseBody) {
        return new FixedResponseHttpClient(builder,
                                           responseStatusCode,
                                           responseHeaders,
                                           wrap(responseBody.getBytes(UTF_8)));
    }

    /**
     * Creates a new HttpClient that returns a fixed response,
     * constructed from the given values, for every request sent.
     */
    public static HttpClient createClientFrom(HttpClient.Builder builder,
                                              int responseStatusCode,
                                              HttpHeaders responseHeaders,
                                              Path path) {
        try {
            return new FixedResponseHttpClient(builder,
                                               responseStatusCode,
                                               responseHeaders,
                                               wrap(Files.readAllBytes(path)));
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Creates a new HttpClient that returns a fixed response,
     * constructed from the given values, for every request sent.
     */
    public static HttpClient createClientFrom(HttpClient.Builder builder,
                                              int responseStatusCode,
                                              HttpHeaders responseHeaders,
                                              byte[] responseBody) {
        return new FixedResponseHttpClient(builder,
                responseStatusCode,
                responseHeaders,
                wrap(responseBody));
    }

    private static final ByteBuffer ECHO_SENTINAL = ByteBuffer.wrap(new byte[] {});

    /**
     * Creates a new HttpClient that returns a fixed response,
     * constructed from the given values, for every request sent.
     */
    public static HttpClient createEchoClient(HttpClient.Builder builder,
                                              int responseStatusCode,
                                              HttpHeaders responseHeaders) {
        return new FixedResponseHttpClient(builder,
                                           responseStatusCode,
                                           responseHeaders,
                                           ECHO_SENTINAL);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>>
    sendAsync(HttpRequest request, BodyHandler<T> responseBodyHandler) {
        return sendAsync(request, responseBodyHandler, null);
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request,
                                    BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        return sendAsync(request, responseBodyHandler).join();  // unwrap exceptions if needed
    }

    class FixedResponseInfo implements HttpResponse.ResponseInfo {
        private final int statusCode;
        private final HttpHeaders headers;
        private final HttpClient.Version version;

        FixedResponseInfo() {
            this.statusCode = responseStatusCode;
            this.headers = responseHeaders;
            this.version = HttpClient.Version.HTTP_1_1;
        }

        /**
         * Provides the response status code
         * @return the response status code
         */
        public int statusCode() {
            return statusCode;
        }

        /**
         * Provides the response headers
         * @return the response headers
         */
        public HttpHeaders headers() {
            return headers;
        }

        /**
         * provides the response protocol version
         * @return the response protocol version
         */
        public HttpClient.Version version() {
            return version;
        }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>>
    sendAsync(HttpRequest request,
              BodyHandler<T> responseBodyHandler,
              PushPromiseHandler<T> pushPromiseHandler) {
        CompletableFuture<HttpResponse<T>> cf = new CompletableFuture<>();
        synchronized (this) {
            if (shutdownRequested) {
                return CompletableFuture.failedFuture(new IOException("closed"));
            }
            responses.add(cf);
        }
        List<ByteBuffer> responseBody = List.of(responseBodyBytes.duplicate());

        // Push promises can be mocked too, if needed

        Optional<HttpRequest.BodyPublisher> obp = request.bodyPublisher();
        if (obp.isPresent()) {
            ConsumingSubscriber subscriber = new ConsumingSubscriber();
            obp.get().subscribe(subscriber);
            // wait for our subscriber to be completed and get the
            // list of ByteBuffers it received.
            var buffers = subscriber.getBuffers().join();
            if (responseBodyBytes == ECHO_SENTINAL) {
                responseBody = buffers;
            }
        }

        BodySubscriber<T> bodySubscriber =
                responseBodyHandler.apply(responseInfo);
        SubmissionPublisher<List<ByteBuffer>> publisher = new SubmissionPublisher<>();
        publisher.subscribe(bodySubscriber);
        publisher.submit(responseBody);
        publisher.close();

        bodySubscriber.getBody().whenComplete((body, throwable) -> {
                    responses.remove(cf);
                    if (body != null)
                        cf.complete(new FixedHttpResponse<>(
                                responseStatusCode,
                                request,
                                responseHeaders,
                                body,
                                null,
                                request.uri(),
                                request.version().orElse(Version.HTTP_2)));
                    else
                        cf.completeExceptionally(throwable);
                }
        );

        return cf;
    }

    /**
     * A Subscriber that demands and consumes all the Publishers data,
     * after which makes it directly available.
     */
    private static class ConsumingSubscriber implements Flow.Subscriber<ByteBuffer> {
        final List<ByteBuffer> buffers = Collections.synchronizedList(new ArrayList<>());
        // A CompletableFuture that will be completed with a list of ByteBuffers that the
        // ConsumingSubscriber has consumed.
        final CompletableFuture<List<ByteBuffer>> consumed = new CompletableFuture<>();

        public final CompletableFuture<List<ByteBuffer>> getBuffers() {
            return consumed;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override public void onNext(ByteBuffer item) {
            buffers.add(item.duplicate());
        }

        @Override public void onError(Throwable throwable) { consumed.completeExceptionally(throwable); }

        @Override public void onComplete() { consumed.complete(buffers.stream().toList()); }
    }

    @Override
    public boolean isTerminated() {
        // return true if this and the wrapped client are terminated
        synchronized (this) {
            if (!shutdownRequested) return false;
            return responses.isEmpty() && super.isTerminated();
        }
    }

    @Override
    public void shutdown() {
        // shutdown the wrapped client
        super.shutdown();
        // mark shutdown requested
        shutdownRequested = true;
    }

    @Override
    public void shutdownNow() {
        // shutdown the wrapped client now
        super.shutdownNow();
        // mark shutdown requested
        shutdownRequested = true;
        // cancel all completable futures
        CompletableFuture[] futures;
        synchronized (this) {
            if (responses.isEmpty()) return ;
            futures = responses.toArray(CompletableFuture[]::new);
            responses.removeAll(Arrays.asList(futures));
        }
        for (var op : futures) {
            op.cancel(true);
        }
    }

    @Override
    public boolean awaitTermination(Duration duration) throws InterruptedException {
        Objects.requireNonNull(duration);
        CompletableFuture[] futures = responses.toArray(CompletableFuture[]::new);
        if (futures.length == 0) {
            // nothing to do here: wait for the wrapped client
            return super.awaitTermination(duration) && isTerminated();
        }

        // waits for our own completable futures to get completed
        var all = CompletableFuture.allOf(futures);
        Duration max = Duration.ofMillis(Long.MAX_VALUE);
        long timeout = duration.compareTo(max) > 0 ? Long.MAX_VALUE : duration.toMillis();
        try {
            all.exceptionally((t) -> null).get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            return isTerminated();
        } catch (InterruptedException ie) {
            throw ie;
        } catch (ExecutionException failed) {
            return isTerminated();
        }
        return isTerminated();
    }

    @Override
    public void close() {
        try {
            // closes this client
            defaultClose();
        } finally {
            // closes the wrapped client (which should already be closed)
            super.close();
        }
    }
}
