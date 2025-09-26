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
 *       -Djdk.internal.httpclient.debug=true
 *       -Djdk.httpclient.HttpClient.log=errors,requests,responses
 *       H3ServerPushWithDiffTypes
 * @summary This is a clone of http2/ServerPushWithDiffTypes but for HTTP/3
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Flow;
import java.util.function.BiPredicate;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.Test;

import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;

public class H3ServerPushWithDiffTypes implements HttpServerAdapters {

    static Map<String,String> PUSH_PROMISES = Map.of(
            "/x/y/z/1", "the first push promise body",
            "/x/y/z/2", "the second push promise body",
            "/x/y/z/3", "the third push promise body",
            "/x/y/z/4", "the fourth push promise body",
            "/x/y/z/5", "the fifth push promise body",
            "/x/y/z/6", "the sixth push promise body",
            "/x/y/z/7", "the seventh push promise body",
            "/x/y/z/8", "the eighth push promise body",
            "/x/y/z/9", "the ninth push promise body"
    );

    private void sendHeadRequest(HttpClient client, URI headURI) throws IOException, InterruptedException {
        HttpRequest headRequest = HttpRequest.newBuilder(headURI)
                .HEAD().version(Version.HTTP_2).build();
        var headResponse = client.send(headRequest, BodyHandlers.ofString());
        assertEquals(headResponse.statusCode(), 200);
        assertEquals(headResponse.version(), Version.HTTP_2);
    }

    @Test
    public void test() throws Exception {
        var sslContext = new SimpleSSLContext().get();
        try (HttpTestServer server = HttpTestServer.create(ANY, sslContext)) {
            HttpTestHandler pushHandler =
                    new ServerPushHandler("the main response body",
                            PUSH_PROMISES);
            server.addHandler(pushHandler, "/push/");
            server.addHandler(new HttpHeadOrGetHandler(), "/head/");

            server.start();
            System.err.println("Server listening on port " + server.serverAuthority());

            // use multi-level path
            URI uri = new URI("https://" + server.serverAuthority() + "/push/a/b/c");
            URI headURI = new URI("https://" + server.serverAuthority() + "/head/x");

            try (HttpClient client = newClientBuilderForH3().proxy(Builder.NO_PROXY)
                    .sslContext(sslContext).version(Version.HTTP_3).build()) {

                sendHeadRequest(client, headURI);

                HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

                ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<BodyAndType<?>>>>
                        results = new ConcurrentHashMap<>();
                PushPromiseHandler<BodyAndType<?>> bh = PushPromiseHandler.of(
                        BodyAndTypeHandler::new, results);

                CompletableFuture<HttpResponse<BodyAndType<?>>> cf =
                        client.sendAsync(request, new BodyAndTypeHandler(request), bh);
                results.put(request, cf);
                cf.join();

                assertEquals(results.size(), PUSH_PROMISES.size() + 1);

                for (HttpRequest r : results.keySet()) {
                    URI u = r.uri();
                    var resp = results.get(r).get();
                    assertEquals(resp.statusCode(), 200);
                    assertEquals(resp.version(), Version.HTTP_3);
                    BodyAndType<?> body = resp.body();
                    String result;
                    // convert all body types to String for easier comparison
                    if (body.type() == String.class) {
                        result = (String) body.body();
                    } else if (body.type() == byte[].class) {
                        byte[] bytes = (byte[]) body.body();
                        result = new String(bytes, UTF_8);
                    } else if (Path.class.isAssignableFrom(body.type())) {
                        Path path = (Path) body.body();
                        result = Files.readString(path);
                    } else {
                        throw new AssertionError("Unknown:" + body.type());
                    }

                    System.err.printf("%s -> %s\n", u.toString(), result);
                    String expected = PUSH_PROMISES.get(r.uri().getPath());
                    if (expected == null)
                        expected = "the main response body";
                    assertEquals(result, expected);
                }
            }
        }
    }

    interface BodyAndType<T> {
        Class<T> type();
        T body();
    }

    static final Path WORK_DIR = Paths.get(".");

    static class BodyAndTypeHandler implements BodyHandler<BodyAndType<?>> {
        int count;
        final HttpRequest request;

        BodyAndTypeHandler(HttpRequest request) {
            this.request = request;
        }

        @Override
        @SuppressWarnings("rawtypes,unchecked")
        public BodySubscriber<BodyAndType<?>> apply(HttpResponse.ResponseInfo info) {
            int whichType = count++ % 3;  // real world may base this on the request metadata
            switch (whichType) {
                case 0: // String
                    return new BodyAndTypeSubscriber(BodySubscribers.ofString(UTF_8));
                case 1: // byte[]
                    return new BodyAndTypeSubscriber(BodySubscribers.ofByteArray());
                case 2: // Path
                    URI u = request.uri();
                    Path path = Paths.get(WORK_DIR.toString(), u.getPath());
                    try {
                        Files.createDirectories(path.getParent());
                    } catch (IOException ee) {
                        throw new UncheckedIOException(ee);
                    }
                    return new BodyAndTypeSubscriber(BodySubscribers.ofFile(path));
                default:
                    throw new AssertionError("Unexpected " + whichType);
            }
        }
    }

    static class BodyAndTypeSubscriber<T>
        implements BodySubscriber<BodyAndType<T>>
    {
        private record BodyAndTypeImpl<T>(Class<T> type, T body) implements BodyAndType<T> { }

        private final BodySubscriber<?> bodySubscriber;
        private final CompletableFuture<BodyAndType<T>> cf;

        @SuppressWarnings("unchecked")
        BodyAndTypeSubscriber(BodySubscriber<T> bodySubscriber) {
            this.bodySubscriber = bodySubscriber;
            cf = new CompletableFuture<>();
            bodySubscriber.getBody().whenComplete(
                    (r,t) -> cf.complete(new BodyAndTypeImpl<>((Class<T>) r.getClass(), r)));
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            bodySubscriber.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            bodySubscriber.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            bodySubscriber.onError(throwable);
            cf.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            bodySubscriber.onComplete();
        }

        @Override
        public CompletionStage<BodyAndType<T>> getBody() {
            return cf;
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
                pushPromises(exchange);
            }

            // response data for the main response
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] bytes = mainResponseBody.getBytes(UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }

        static final BiPredicate<String,String> ACCEPT_ALL = (x, y) -> true;

        private void pushPromises(HttpTestExchange exchange) throws IOException {
            URI requestURI = exchange.getRequestURI();
            for (Map.Entry<String,String> promise : promises.entrySet()) {
                URI uri = requestURI.resolve(promise.getKey());
                InputStream is = new ByteArrayInputStream(promise.getValue().getBytes(UTF_8));
                Map<String,List<String>> map = Map.of("X-Promise", List.of(promise.getKey()));
                HttpHeaders headers = HttpHeaders.of(map, ACCEPT_ALL);
                // TODO: add some check on headers, maybe
                exchange.serverPush(uri, headers, is);
            }
            System.err.println("Server: All pushes sent");
        }
    }
}
