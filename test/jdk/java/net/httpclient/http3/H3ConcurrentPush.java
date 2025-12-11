/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.http2.Http2TestServer
 * @run testng/othervm
 *      -Djdk.internal.httpclient.debug=true
 *      -Djdk.httpclient.HttpClient.log=errors,requests,responses,trace
 *      -Djdk.httpclient.http3.maxConcurrentPushStreams=45
 *      H3ConcurrentPush
 * @summary This test exercises some of the HTTP/3 specifities for PushPromises.
 *      It sends several concurrent requests, and the server sends a bunch of
 *      identical push promise frames to all of them. That is, there will be
 *      a push promise frame with the same push ID sent to each exchange.
 *      The one (and only one) of the handlers will open a push stream for
 *      that push id. The client checks that the expected HTTP/3 specific
 *      methods are invoked on the PushPromiseHandler.
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.internal.net.http.common.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class H3ConcurrentPush implements HttpServerAdapters {

    // dummy hack to prevent the IDE complaining that calling
    // println will throw NPE
    static final PrintStream err = System.err;
    static final PrintStream out = System.out;

    static Map<String,String> PUSH_PROMISES = Map.of(
            "/x/y/z/1", "the first push promise body",
            "/x/y/z/2", "the second push promise body",
            "/x/y/z/3", "the third push promise body",
            "/x/y/z/4", "the fourth push promise body",
            "/x/y/z/5", "the fifth push promise body",
            "/x/y/z/6", "the sixth push promise body",
            "/x/y/z/7", "the seventh push promise body",
            "/x/y/z/8", "the eight push promise body",
            "/x/y/z/9", "the ninth push promise body"
    );
    static final String MAIN_RESPONSE_BODY = "the main response body";

    HttpTestServer  server;
    URI uri;
    URI headURI;
    ServerPushHandler pushHandler;

    @BeforeTest
    public void setup() throws Exception {
        server = HttpTestServer.create(ANY, new SimpleSSLContext().get());
        pushHandler = new ServerPushHandler(MAIN_RESPONSE_BODY, PUSH_PROMISES);
        server.addHandler(pushHandler, "/push/");
        server.addHandler(new HttpHeadOrGetHandler(), "/head/");
        server.start();
        err.println("Server listening on port " + server.serverAuthority());
        uri = new URI("https://" + server.serverAuthority() + "/push/a/b/c");
        headURI = new URI("https://" + server.serverAuthority() + "/head/x");
    }

    @AfterTest
    public void teardown() {
        server.stop();
    }

    static <T> HttpResponse<T> assert200ResponseCode(HttpResponse<T> response) {
        assertEquals(response.statusCode(), 200);
        assertEquals(response.version(), Version.HTTP_3);
        return response;
    }

    private void sendHeadRequest(HttpClient client) throws IOException, InterruptedException {
        HttpRequest headRequest = HttpRequest.newBuilder(headURI)
                .HEAD().version(Version.HTTP_2).build();
        var headResponse = client.send(headRequest, BodyHandlers.ofString());
        assertEquals(headResponse.statusCode(), 200);
        assertEquals(headResponse.version(), Version.HTTP_2);
    }

    static final class TestPushPromiseHandler<T> implements PushPromiseHandler<T> {
        record NotifiedPromise(PushId pushId, HttpRequest initiatingRequest) {}
        final Map<HttpRequest, PushId> requestToPushId = new ConcurrentHashMap<>();
        final Map<PushId, HttpRequest> pushIdToRequest = new ConcurrentHashMap<>();
        final List<AssertionError> errors = new CopyOnWriteArrayList<>();
        final List<NotifiedPromise> notified = new CopyOnWriteArrayList<>();
        final ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<T>>> promises
                = new ConcurrentHashMap<>();
        final Supplier<BodyHandler<T>> bodyHandlerSupplier;
        final PushPromiseHandler<T> pph;
        TestPushPromiseHandler(Supplier<BodyHandler<T>> bodyHandlerSupplier) {
            this.bodyHandlerSupplier = bodyHandlerSupplier;
            this.pph = PushPromiseHandler.of((r) -> bodyHandlerSupplier.get(), promises);
        }

        @Override
        public void applyPushPromise(HttpRequest initiatingRequest,
                                     HttpRequest pushPromiseRequest,
                                     Function<BodyHandler<T>, CompletableFuture<HttpResponse<T>>> acceptor) {
            errors.add(new AssertionError("no pushID provided for: " + pushPromiseRequest));
        }

        @Override
        public void notifyAdditionalPromise(HttpRequest initiatingRequest, PushId pushid) {
            notified.add(new NotifiedPromise(pushid, initiatingRequest));
            out.println("notifyPushPromise: pushId=" + pushid);
            pph.notifyAdditionalPromise(initiatingRequest, pushid);
        }

        @Override
        public void applyPushPromise(HttpRequest initiatingRequest,
                                     HttpRequest pushPromiseRequest,
                                     PushId pushid,
                                     Function<BodyHandler<T>, CompletableFuture<HttpResponse<T>>> acceptor) {
            out.println("applyPushPromise: " + pushPromiseRequest + ", pushId=" + pushid);
            requestToPushId.putIfAbsent(pushPromiseRequest, pushid);
            if (pushIdToRequest.putIfAbsent(pushid, pushPromiseRequest) != null) {
                errors.add(new AssertionError("pushId already used: " + pushid));
            }
            pph.applyPushPromise(initiatingRequest, pushPromiseRequest, pushid, acceptor);
        }

    }

    @Test
    public void testConcurrentPushes() throws Exception {
        int maxPushes = Utils.getIntegerProperty("jdk.httpclient.http3.maxConcurrentPushStreams", -1);
        out.println("maxPushes: " + maxPushes);
        assertTrue(maxPushes > 0);
        try (HttpClient client = newClientBuilderForH3()
                .proxy(Builder.NO_PROXY)
                .version(Version.HTTP_3)
                .sslContext(new SimpleSSLContext().get())
                .build()) {

            sendHeadRequest(client);

            // Send with promise handler
            TestPushPromiseHandler<String> custom = new TestPushPromiseHandler<>(BodyHandlers::ofString);
            var promises = custom.promises;

            for (int j=0; j < 2; j++) {
                if (j == 0) out.println("\ntestCancel: First time around");
                else out.println("\ntestCancel: Second time around: should be a new connection");

                // now make sure there's an HTTP/3 connection
                client.send(HttpRequest.newBuilder(headURI).version(Version.HTTP_3)
                        .setOption(H3_DISCOVERY, ALT_SVC).HEAD().build(), BodyHandlers.discarding());

                int waitForPushId;
                List<CompletableFuture<HttpResponse<String>>> responses = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    waitForPushId = Math.min(PUSH_PROMISES.size(), maxPushes) + 1;
                    CompletableFuture<HttpResponse<String>> main = client.sendAsync(
                            HttpRequest.newBuilder(uri.resolve("?i=%s,j=%s".formatted(i, j)))
                                    .header("X-WaitForPushId", String.valueOf(waitForPushId))
                                    .build(),
                            BodyHandlers.ofString(),
                            custom);
                    responses.add(main);
                }
                CompletableFuture.allOf(responses.toArray(CompletableFuture<?>[]::new)).join();
                responses.forEach(cf -> {
                    var main = cf.join();
                    var old = promises.put(main.request(), CompletableFuture.completedFuture(main));
                    assertNull(old, "unexpected mapping for: " + old);
                });

                promises.forEach((key, value) -> out.println(key + ":" + value.join().body()));

                promises.forEach((request, value) -> {
                    HttpResponse<String> response = value.join();
                    assertEquals(response.statusCode(), 200);
                    if (PUSH_PROMISES.containsKey(request.uri().getPath())) {
                        assertEquals(response.body(), PUSH_PROMISES.get(request.uri().getPath()));
                    } else {
                        assertEquals(response.body(), MAIN_RESPONSE_BODY);
                    }
                });

                int expectedPushes = Math.min(PUSH_PROMISES.size(), maxPushes) + 5;
                assertEquals(promises.size(), expectedPushes);

                promises.clear();

                // Send with no promise handler
                try {
                    client.sendAsync(HttpRequest.newBuilder(uri).build(), BodyHandlers.ofString())
                            .thenApply(H3ConcurrentPush::assert200ResponseCode)
                            .thenApply(HttpResponse::body)
                            .thenAccept(body -> assertEquals(body, MAIN_RESPONSE_BODY))
                            .join();
                } catch (CompletionException c) {
                    throw new AssertionError(c.getCause());
                }
                assertEquals(promises.size(), 0);

                // Send with no promise handler, but use pushId bigger than allowed.
                // This should cause the connection to get closed
                long usePushId = maxPushes * 3 + 10;
                try {
                    HttpRequest bigger = HttpRequest.newBuilder(uri)
                            .header("X-UsePushId", String.valueOf(usePushId))
                            .build();
                    client.sendAsync(bigger, BodyHandlers.ofString())
                            .thenApply(H3ConcurrentPush::assert200ResponseCode)
                            .thenApply(HttpResponse::body)
                            .thenAccept(body -> assertEquals(body, MAIN_RESPONSE_BODY))
                            .join();
                    throw new AssertionError("Expected IOException not thrown");
                } catch (CompletionException c) {
                    boolean success = false;
                    if (c.getCause() instanceof IOException io) {
                        if (io.getMessage() != null &&
                                io.getMessage().contains("Max pushId exceeded (%s >= %s)"
                                        .formatted(usePushId, maxPushes))) {
                            success = true;
                        }
                        if (success) {
                            out.println("Got expected IOException: " + io);
                        } else throw io;
                    }
                    if (!success) {
                        throw new AssertionError("Unexpected exception: " + c.getCause(), c.getCause());
                    }
                }
                assertEquals(promises.size(), 0);

                // the next time around we should have a new connection,
                // so we can restart from scratch
                pushHandler.reset();
            }
            var errors = custom.errors;
            errors.forEach(t -> t.printStackTrace(System.out));
            var error = errors.stream().findFirst().orElse(null);
            if (error != null) throw error;
            var notified = custom.notified;
            assertEquals(notified.size(), 9*4*2, "Unexpected notification: " + notified);
        }
    }


    // --- server push handler ---
    static class ServerPushHandler implements HttpTestHandler {

        private final String mainResponseBody;
        private final Map<String,String> promises;
        private final ReentrantLock lock = new ReentrantLock();
        private final Map<String, Long> sentPromises = new ConcurrentHashMap<>();

        public ServerPushHandler(String mainResponseBody,
                                 Map<String,String> promises)
            throws Exception
        {
            Objects.requireNonNull(promises);
            this.mainResponseBody = mainResponseBody;
            this.promises = promises;
        }

        // The assumption is that there will be several concurrent
        // exchanges, but all on the same connection
        // The first exchange that emits a PushPromise sends
        // a push promise frame + open the push response stream.
        // The other exchanges will simply send a push promise
        // frame, with the pushId allocated by the previous exchange.
        // The sentPromises map is used to store that pushId.
        // This obviously only works if we have a single HTTP/3 connection.
        final AtomicInteger count = new AtomicInteger();
        public void handle(HttpTestExchange exchange) throws IOException {
            long count = -1;
            try {
                count = this.count.incrementAndGet();
                err.println("Server: handle " + exchange +
                        " on " + exchange.getConnectionKey());
                out.println("Server: handle " + exchange.getRequestURI() +
                        " on " + exchange.getConnectionKey());
                try (InputStream is = exchange.getRequestBody()) {
                    is.readAllBytes();
                }

                if (exchange.serverPushAllowed()) {
                    pushPromises(exchange);
                }

                // response data for the main response
                try (OutputStream os = exchange.getResponseBody()) {
                    byte[] bytes = mainResponseBody.getBytes(UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    os.write(bytes);
                } catch (ClosedChannelException ex) {
                    out.printf("handling exchange %s, %s: %s%n", count,
                            exchange.getRequestURI(), exchange.getRequestHeaders());
                    out.printf("Got closed channel exception sending response after sent=%s allowed=%s%n",
                            sent, allowed);
                }
            } finally {
                out.printf("handled exchange %s, %s: %s%n", count,
                        exchange.getRequestURI(), exchange.getRequestHeaders());
            }
        }

        volatile long allowed = -1;
        volatile int sent = 0;
        volatile int nsent = 0;
        void reset() {
            lock.lock();
            try {
                allowed = -1;
                sent = 0;
                nsent = 0;
                sentPromises.clear();
            } finally {
                lock.unlock();
            }
        }

        private void pushPromises(HttpTestExchange exchange) throws IOException {
            URI requestURI = exchange.getRequestURI();
            long waitForPushId = exchange.getRequestHeaders()
                    .firstValueAsLong("X-WaitForPushId").orElse(-1);
            long usePushId = exchange.getRequestHeaders()
                    .firstValueAsLong("X-UsePushId").orElse(-1);
            if (waitForPushId >= 0) {
                while (allowed <= waitForPushId) {
                    try {
                        err.printf("Server: waiting for pushId sent=%s allowed=%s: %s%n",
                                sent, allowed, waitForPushId);
                        var allowed = exchange.waitForHttp3MaxPushId(waitForPushId);
                        err.println("Server: Got maxPushId: " + allowed);
                        out.println("Server: Got maxPushId: " + allowed);
                        lock.lock();
                        if (allowed > this.allowed) this.allowed = allowed;
                        lock.unlock();
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
            }
            for (Map.Entry<String,String> promise : promises.entrySet()) {
                // if usePushId != -1 we send a single push promise,
                // without checking that it's allowed.
                // Otherwise, we stop sending promises when we have consumed
                // the whole window
                if (usePushId == -1 && allowed > 0 && sent >= allowed) {
                    err.println("Server: sent all allowed promises: " + sent);
                    break;
                }

                if (waitForPushId >= 0) {
                    while (allowed <= waitForPushId) {
                        try {
                            err.printf("Server: waiting for pushId sent=%s allowed=%s: %s%n",
                                    sent, allowed, waitForPushId);
                            var allowed = exchange.waitForHttp3MaxPushId(waitForPushId);
                            err.println("Server: Got maxPushId: " + allowed);
                            out.println("Server: Got maxPushId: " + allowed);
                            lock.lock();
                            if (allowed > this.allowed) this.allowed = allowed;
                            lock.unlock();
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }
                }
                URI uri = requestURI.resolve(promise.getKey());
                InputStream is = new ByteArrayInputStream(promise.getValue().getBytes(UTF_8));
                HttpHeaders headers = HttpHeaders.of(Collections.emptyMap(), (x, y) -> true);
                if (usePushId == -1) {
                    long pushId;
                    boolean send = false;
                    lock.lock();
                    try {
                        Long usedPushId = sentPromises.get(promise.getKey());
                        if (usedPushId == null) {
                            pushId = exchange.sendHttp3PushPromiseFrame(-1, uri, headers);
                            waitForPushId = pushId + 1;
                            sentPromises.put(promise.getKey(), pushId);
                            sent += 1;
                            send = true;
                        } else {
                            pushId = usedPushId;
                            exchange.sendHttp3PushPromiseFrame(pushId, uri, headers);
                        }
                    } finally {
                        lock.unlock();
                    }
                    if (send) {
                        exchange.sendHttp3PushResponse(pushId, uri, headers, headers, is);
                        err.println("Server: Sent push promise with response: " + pushId);
                    } else {
                        err.println("Server: Sent push promise frame: " + pushId);
                    }
                    if (pushId >= waitForPushId) waitForPushId = pushId + 1;
                } else {
                    exchange.sendHttp3PushPromiseFrame(usePushId, uri, headers);
                    err.println("Server: Sent push promise frame: " + usePushId);
                    exchange.sendHttp3PushResponse(usePushId, uri, headers, headers, is);
                    err.println("Server: Sent push promise response: " + usePushId);
                    lock.lock();
                    sent += 1;
                    lock.unlock();
                    return;
                }
            }
            err.println("Server: All pushes sent");
        }
    }
}
