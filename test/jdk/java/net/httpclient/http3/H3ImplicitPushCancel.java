/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *      H3ImplicitPushCancel
 * @summary This is a clone of http2/ImplicitPushCancel but for HTTP/3
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
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.internal.net.http.common.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;

public class H3ImplicitPushCancel implements HttpServerAdapters {

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

    @BeforeTest
    public void setup() throws Exception {
        server = HttpTestServer.create(ANY, SimpleSSLContext.findSSLContext());
        HttpTestHandler pushHandler = new ServerPushHandler(MAIN_RESPONSE_BODY,
                                                     PUSH_PROMISES);
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

    /*
     * With a handler not capable of accepting push promises, then all push
     * promises should be rejected / cancelled, without interfering with the
     * main response.
     */
    @Test
    public void test() throws Exception {
        try (HttpClient client = newClientBuilderForH3()
                .proxy(Builder.NO_PROXY)
                .version(Version.HTTP_3)
                .sslContext(SimpleSSLContext.findSSLContext())
                .build()) {

            sendHeadRequest(client);

            // Send with no promise handler
            try {
                client.sendAsync(HttpRequest.newBuilder(uri)
                                .build(), BodyHandlers.ofString())
                        .thenApply(H3ImplicitPushCancel::assert200ResponseCode)
                        .thenApply(HttpResponse::body)
                        .thenAccept(body -> assertEquals(body, MAIN_RESPONSE_BODY))
                        .join();
                System.out.println("Got result before error was raised");
                throw new AssertionError("should have failed");
            } catch (CompletionException c) {
                Throwable cause = Utils.getCompletionCause(c);
                if (cause.getMessage().contains("Max pushId exceeded")) {
                    System.out.println("Got expected exception: " + cause);
                } else throw new AssertionError(cause);
            }

            // Send with promise handler
            ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<String>>> promises
                    = new ConcurrentHashMap<>();
            PushPromiseHandler<String> pph = PushPromiseHandler
                    .of((r) -> BodyHandlers.ofString(), promises);

            HttpResponse<String> main;
            try {
                main = client.sendAsync(
                                HttpRequest.newBuilder(uri)
                                        .header("X-WaitForPushId", String.valueOf(1))
                                        .build(),
                                BodyHandlers.ofString(),
                                pph)
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
            assertEquals(promises.size(), PUSH_PROMISES.size() + 1);

            promises.clear();

            // Send with no promise handler
            try {
                client.sendAsync(HttpRequest.newBuilder(uri).build(), BodyHandlers.ofString())
                        .thenApply(H3ImplicitPushCancel::assert200ResponseCode)
                        .thenApply(HttpResponse::body)
                        .thenAccept(body -> assertEquals(body, MAIN_RESPONSE_BODY))
                        .join();
            } catch (CompletionException c) {
                throw new AssertionError(c.getCause());
            }

            assertEquals(promises.size(), 0);
        }
    }


    // --- server push handler ---
    static class ServerPushHandler implements HttpTestHandler {

        private final String mainResponseBody;
        private final Map<String,String> promises;

        public ServerPushHandler(String mainResponseBody,
                                 Map<String,String> promises)
            throws Exception
        {
            Objects.requireNonNull(promises);
            this.mainResponseBody = mainResponseBody;
            this.promises = promises;
        }

        public void handle(HttpTestExchange exchange) throws IOException {
            System.err.println("Server: handle " + exchange);
            try (InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
            }

            if (exchange.serverPushAllowed()) {
                long waitForPushId = exchange.getRequestHeaders()
                        .firstValueAsLong("X-WaitForPushId").orElse(-1);
                long allowed = -1;
                if (waitForPushId >= 0) {
                    while (allowed <= waitForPushId) {
                        try {
                            allowed = exchange.waitForHttp3MaxPushId(waitForPushId);
                            System.err.println("Got maxPushId: " + allowed);
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }
                }
                pushPromises(exchange);
            }

            // response data for the main response
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] bytes = mainResponseBody.getBytes(UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }

        private void pushPromises(HttpTestExchange exchange) throws IOException {
            URI requestURI = exchange.getRequestURI();
            for (Map.Entry<String,String> promise : promises.entrySet()) {
                URI uri = requestURI.resolve(promise.getKey());
                InputStream is = new ByteArrayInputStream(promise.getValue().getBytes(UTF_8));
                HttpHeaders headers = HttpHeaders.of(Collections.emptyMap(), (x, y) -> true);
                exchange.serverPush(uri, headers, is);
            }
            System.err.println("Server: All pushes sent");
        }
    }
}
