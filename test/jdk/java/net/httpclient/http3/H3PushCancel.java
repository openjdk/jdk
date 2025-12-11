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
 *      -Djdk.httpclient.http3.maxConcurrentPushStreams=5
 *      H3PushCancel
 * @summary This test checks that not accepting one of the push promise
 *       will cancel it. It also verifies that receiving a pushId bigger
 *       than the max push ID allowed on the connection will cause
 *       the exchange to fail and the connection to get closed.
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.net.http.HttpResponse.PushPromiseHandler.PushId;
import java.net.http.HttpResponse.PushPromiseHandler.PushId.Http3PushId;
import java.nio.channels.ClosedChannelException;
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

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.internal.net.http.common.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class H3PushCancel implements HttpServerAdapters {

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
        System.err.println("Server listening on port " + server.serverAuthority());
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

    @Test
    public void testNoCancel() throws Exception {
        int maxPushes = Utils.getIntegerProperty("jdk.httpclient.http3.maxConcurrentPushStreams", -1);
        System.out.println("maxPushes: " + maxPushes);
        assertTrue(maxPushes > 0);
        try (HttpClient client = newClientBuilderForH3()
                .proxy(Builder.NO_PROXY)
                .version(Version.HTTP_3)
                .sslContext(new SimpleSSLContext().get())
                .build()) {

            sendHeadRequest(client);

            // Send with promise handler
            ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<String>>> promises
                    = new ConcurrentHashMap<>();
            PushPromiseHandler<String> pph = PushPromiseHandler
                    .of((r) -> BodyHandlers.ofString(), promises);

            for (int j=0; j < 2; j++) {
                if (j == 0) System.out.println("\ntestNoCancel: First time around");
                else System.out.println("\ntestNoCancel: Second time around: should be a new connection");

                int waitForPushId;
                for (int i = 0; i < 3; i++) {
                    HttpResponse<String> main;
                    waitForPushId = i * Math.min(PUSH_PROMISES.size(), maxPushes) + 1;
                    try {
                        main = client.sendAsync(
                                        HttpRequest.newBuilder(uri)
                                                .header("X-WaitForPushId", String.valueOf(waitForPushId))
                                                .build(),
                                        BodyHandlers.ofString(),
                                        pph)
                                .join();
                    } catch (CompletionException c) {
                        throw new AssertionError(c.getCause());
                    }

                    promises.forEach((key, value1) -> System.out.println(key + ":" + value1.join().body()));

                    promises.putIfAbsent(main.request(), CompletableFuture.completedFuture(main));
                    promises.forEach((request, value) -> {
                        HttpResponse<String> response = value.join();
                        assertEquals(response.statusCode(), 200);
                        if (PUSH_PROMISES.containsKey(request.uri().getPath())) {
                            assertEquals(response.body(), PUSH_PROMISES.get(request.uri().getPath()));
                        } else {
                            assertEquals(response.body(), MAIN_RESPONSE_BODY);
                        }
                    });
                    assertEquals(promises.size(), Math.min(PUSH_PROMISES.size(), maxPushes) + 1);

                    promises.clear();
                }

                // Send with no promise handler
                try {
                    client.sendAsync(HttpRequest.newBuilder(uri).build(), BodyHandlers.ofString())
                            .thenApply(H3PushCancel::assert200ResponseCode)
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
                            .thenApply(H3PushCancel::assert200ResponseCode)
                            .thenApply(HttpResponse::body)
                            .thenAccept(body -> assertEquals(body, MAIN_RESPONSE_BODY))
                            .join();
                    throw new AssertionError("Expected IOException not thrown");
                } catch (CompletionException c) {
                    boolean success = false;
                    if (c.getCause() instanceof IOException io) {
                        if (io.getMessage() != null &&
                                io.getMessage().contains("Max pushId exceeded (%s >= %s)"
                                        .formatted(usePushId, maxPushes * 3))) {
                            success = true;
                        }
                        if (success) {
                            System.out.println("Got expected IOException: " + io);
                        } else throw io;
                    }
                    if (!success) {
                        throw new AssertionError("Unexpected exception: " + c.getCause(), c.getCause());
                    }
                }
                assertEquals(promises.size(), 0);

                // the next time around we should have a new connection
                // so we can restart from scratch
                pushHandler.reset();
            }
        }
    }

    @Test
    public void testCancel() throws Exception {
        int maxPushes = Utils.getIntegerProperty("jdk.httpclient.http3.maxConcurrentPushStreams", -1);
        System.out.println("maxPushes: " + maxPushes);
        assertTrue(maxPushes > 0);
        try (HttpClient client = newClientBuilderForH3()
                .proxy(Builder.NO_PROXY)
                .version(Version.HTTP_3)
                .sslContext(new SimpleSSLContext().get())
                .build()) {

            sendHeadRequest(client);

            // Send with promise handler
            ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<String>>> promises
                    = new ConcurrentHashMap<>();
            PushPromiseHandler<String> pph = PushPromiseHandler
                    .of((r) -> BodyHandlers.ofString(), promises);
            record NotifiedPromise(PushId pushId, HttpRequest initiatingRequest) {}
            final Map<HttpRequest, PushId> requestToPushId = new ConcurrentHashMap<>();
            final Map<PushId, HttpRequest> pushIdToRequest = new ConcurrentHashMap<>();
            final List<AssertionError> errors = new CopyOnWriteArrayList<>();
            final List<NotifiedPromise> notified = new CopyOnWriteArrayList<>();
            PushPromiseHandler<String> custom = new PushPromiseHandler<>() {
                @Override
                public void applyPushPromise(HttpRequest initiatingRequest,
                                             HttpRequest pushPromiseRequest,
                                             Function<BodyHandler<String>, CompletableFuture<HttpResponse<String>>> acceptor) {
                    pph.applyPushPromise(initiatingRequest, pushPromiseRequest, acceptor);
                }
                @Override
                public void notifyAdditionalPromise(HttpRequest initiatingRequest, PushId pushid) {
                    notified.add(new NotifiedPromise(pushid, initiatingRequest));
                    pph.notifyAdditionalPromise(initiatingRequest, pushid);
                }
                @Override
                public void applyPushPromise(HttpRequest initiatingRequest,
                                             HttpRequest pushPromiseRequest,
                                             PushId pushid,
                                             Function<BodyHandler<String>, CompletableFuture<HttpResponse<String>>> acceptor) {
                    System.out.println("applyPushPromise: " + pushPromiseRequest + ", pushId=" + pushid);
                    requestToPushId.putIfAbsent(pushPromiseRequest, pushid);
                    if (pushIdToRequest.putIfAbsent(pushid, pushPromiseRequest) != null) {
                        errors.add(new AssertionError("pushId already used: " + pushid));
                    }
                    if (pushid instanceof Http3PushId http3PushId) {
                        if (http3PushId.pushId() == 1) {
                            System.out.println("Cancelling: " + http3PushId);
                            return; // cancel pushId == 1
                        }
                    }
                    pph.applyPushPromise(initiatingRequest, pushPromiseRequest, pushid, acceptor);
                }
            };

            for (int j=0; j < 2; j++) {
                if (j == 0) System.out.println("\ntestCancel: First time around");
                else System.out.println("\ntestCancel: Second time around: should be a new connection");

                int waitForPushId;
                for (int i = 0; i < 3; i++) {
                    HttpResponse<String> main;
                    waitForPushId = i * Math.min(PUSH_PROMISES.size(), maxPushes) + 1;
                    try {
                        main = client.sendAsync(
                                        HttpRequest.newBuilder(uri)
                                                .header("X-WaitForPushId", String.valueOf(waitForPushId))
                                                .build(),
                                        BodyHandlers.ofString(),
                                        custom)
                                .join();
                    } catch (CompletionException c) {
                        throw new AssertionError(c.getCause());
                    }

                    promises.forEach((key, value) -> System.out.println(key + ":" + value.join().body()));

                    promises.putIfAbsent(main.request(), CompletableFuture.completedFuture(main));
                    promises.forEach((request, value) -> {
                        HttpResponse<String> response = value.join();
                        assertEquals(response.statusCode(), 200);
                        if (PUSH_PROMISES.containsKey(request.uri().getPath())) {
                            assertEquals(response.body(), PUSH_PROMISES.get(request.uri().getPath()));
                        } else {
                            assertEquals(response.body(), MAIN_RESPONSE_BODY);
                        }
                    });
                    int expectedPushes = Math.min(PUSH_PROMISES.size(), maxPushes) + 1;
                    if (i == 0) expectedPushes--; // pushId == 1 was cancelled
                    assertEquals(promises.size(), expectedPushes);

                    promises.clear();
                }

                // Send with no promise handler
                try {
                    client.sendAsync(HttpRequest.newBuilder(uri).build(), BodyHandlers.ofString())
                            .thenApply(H3PushCancel::assert200ResponseCode)
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
                            .thenApply(H3PushCancel::assert200ResponseCode)
                            .thenApply(HttpResponse::body)
                            .thenAccept(body ->assertEquals(body, MAIN_RESPONSE_BODY))
                            .join();
                    throw new AssertionError("Expected IOException not thrown");
                } catch (CompletionException c) {
                    boolean success = false;
                    if (c.getCause() instanceof IOException io) {
                        if (io.getMessage() != null &&
                                io.getMessage().contains("Max pushId exceeded (%s >= %s)"
                                        .formatted(usePushId, maxPushes * 3))) {
                            success = true;
                        }
                        if (success) {
                            System.out.println("Got expected IOException: " + io);
                        } else throw io;
                    }
                    if (!success) {
                        throw new AssertionError("Unexpected exception: " + c.getCause(), c.getCause());
                    }
                }
                assertEquals(promises.size(), 0);

                // the next time around we should have a new connection
                // so we can restart from scratch
                pushHandler.reset();
            }
            errors.forEach(t -> t.printStackTrace(System.out));
            var error = errors.stream().findFirst().orElse(null);
            if (error != null) throw error;
            assertEquals(notified.size(), 0, "Unexpected notification: " + notified);
        }
    }


    // --- server push handler ---
    static class ServerPushHandler implements HttpTestHandler {

        private final String mainResponseBody;
        private final Map<String,String> promises;
        private final ReentrantLock lock = new ReentrantLock();

        public ServerPushHandler(String mainResponseBody,
                                 Map<String,String> promises)
            throws Exception
        {
            Objects.requireNonNull(promises);
            this.mainResponseBody = mainResponseBody;
            this.promises = promises;
        }

        final AtomicInteger count = new AtomicInteger();
        public void handle(HttpTestExchange exchange) throws IOException {
            long count = -1;
            lock.lock();
            try {
                count = this.count.incrementAndGet();
                System.err.println("Server: handle " + exchange);
                System.out.println("Server: handle " + exchange.getRequestURI());
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
                    System.out.printf("handling exchange %s, %s: %s%n", count,
                            exchange.getRequestURI(), exchange.getRequestHeaders());
                    System.out.printf("Got closed channel exception sending response after sent=%s allowed=%s%n",
                            sent, allowed);
                }
            } finally {
                lock.unlock();
                System.out.printf("handled exchange %s, %s: %s%n", count,
                        exchange.getRequestURI(), exchange.getRequestHeaders());
            }
        }

        volatile long allowed = -1;
        volatile int sent = 0;
        void reset() {
            lock.lock();
            try {
                allowed = -1;
                sent = 0;
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
                        System.err.printf("Server: waiting for pushId sent=%s allowed=%s: %s%n",
                                sent, allowed, waitForPushId);
                        allowed = exchange.waitForHttp3MaxPushId(waitForPushId);
                        System.err.println("Server: Got maxPushId: " + allowed);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
            }
            for (Map.Entry<String,String> promise : promises.entrySet()) {
                // if usePushId != -1 we send a single push promise,
                // without checking that's it's allowed.
                // Otherwise, we stop sending promises when we have consumed
                // the whole window
                if (usePushId == -1 && allowed > 0 && sent >= allowed) {
                    System.err.println("Server: sent all allowed promises: " + sent);
                    break;
                }
                if (waitForPushId >= 0) {
                    while (allowed <= waitForPushId) {
                        try {
                            System.err.printf("Server: waiting for pushId sent=%s allowed=%s: %s%n",
                                    sent, allowed, waitForPushId);
                            allowed = exchange.waitForHttp3MaxPushId(waitForPushId);
                            System.err.println("Server: Got maxPushId: " + allowed);
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }
                }
                URI uri = requestURI.resolve(promise.getKey());
                InputStream is = new ByteArrayInputStream(promise.getValue().getBytes(UTF_8));
                HttpHeaders headers = HttpHeaders.of(Collections.emptyMap(), (x, y) -> true);
                if (usePushId == -1) {
                    long pushId = exchange.http3ServerPush(uri, headers, headers, is);
                    System.err.println("Server: Sent push promise with response: " + pushId);
                    waitForPushId = pushId + 1; // assuming no concurrent requests...
                    sent += 1;
                } else {
                    exchange.sendHttp3PushPromiseFrame(usePushId, uri, headers);
                    System.err.println("Server: Sent push promise frame: " + usePushId);
                    exchange.sendHttp3PushResponse(usePushId, uri, headers, headers, is);
                    System.err.println("Server: Sent push promise response: " + usePushId);
                    sent += 1;
                    return;
                }
            }
            System.err.println("Server: All pushes sent");
        }
    }
}
