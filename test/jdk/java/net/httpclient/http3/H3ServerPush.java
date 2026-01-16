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
 * @bug 8087112 8159814
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.httpclient.test.lib.http2.PushHandler
 *        jdk.test.lib.Utils
 *        jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm/timeout=960
 *      -Djdk.httpclient.HttpClient.log=errors,requests,headers
 *      -Djdk.internal.httpclient.debug=false
 *      H3ServerPush
 * @summary This is a clone of http2/ServerPush but for HTTP/3
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.PushHandler;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jdk.test.lib.Utils.createTempFileOfSize;
import static org.testng.Assert.assertEquals;

public class H3ServerPush implements HttpServerAdapters {

    private static final String CLASS_NAME = H3ServerPush.class.getSimpleName();

    static final int LOOPS = 13;
    static final int FILE_SIZE = 512 * 1024 + 343;

    static Path tempFile;

    HttpTestServer server;
    URI uri;
    URI headURI;

    @BeforeTest
    public void setup() throws Exception {
        tempFile = createTempFileOfSize(CLASS_NAME, ".dat", FILE_SIZE);
        var sslContext = SimpleSSLContext.findSSLContext();
        var h2Server = new Http2TestServer(true, sslContext);
        h2Server.enableH3AltServiceOnSamePort();
        h2Server.addHandler(new PushHandler(tempFile, LOOPS), "/foo/");
        System.out.println("Using temp file:" + tempFile);
        server = HttpTestServer.of(h2Server);
        server.addHandler(new HttpHeadOrGetHandler(), "/head/");
        headURI = new URI("https://" + server.serverAuthority() + "/head/x");
        uri = new URI("https://" + server.serverAuthority() + "/foo/a/b/c");

        System.err.println("Server listening at " + server.serverAuthority());
        server.start();

    }

    private void sendHeadRequest(HttpClient client) throws IOException, InterruptedException {
        HttpRequest headRequest = HttpRequest.newBuilder(headURI)
                .HEAD().version(Version.HTTP_2).build();
        var headResponse = client.send(headRequest, BodyHandlers.ofString());
        assertEquals(headResponse.statusCode(), 200);
        assertEquals(headResponse.version(), Version.HTTP_2);
    }

    @AfterTest
    public void teardown() {
        server.stop();
    }

    // Test 1 - custom written push promise handler, everything as a String
    @Test
    public void testTypeString() throws Exception {
        System.out.println("\n**** testTypeString\n");
        String tempFileAsString = Files.readString(tempFile);
        ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<String>>>
                resultMap = new ConcurrentHashMap<>();

        PushPromiseHandler<String> pph = (initial, pushRequest, acceptor) -> {
            BodyHandler<String> s = BodyHandlers.ofString(UTF_8);
            CompletableFuture<HttpResponse<String>> cf = acceptor.apply(s);
            resultMap.put(pushRequest, cf);
        };

        try (HttpClient client = newClientBuilderForH3().proxy(Builder.NO_PROXY)
                .sslContext(SimpleSSLContext.findSSLContext())
                .version(Version.HTTP_3).build()) {
            sendHeadRequest(client);

            HttpRequest request = HttpRequest.newBuilder(uri).GET()
                    .build();
            CompletableFuture<HttpResponse<String>> cf =
                    client.sendAsync(request, BodyHandlers.ofString(UTF_8), pph);
            resultMap.put(request, cf);
            System.out.println("waiting for response");
            var resp = cf.join();
            assertEquals(resp.version(), Version.HTTP_3);
            var seen = new HashSet<>();
            resultMap.forEach((k, v) -> {
                if (seen.add(k)) {
                    System.out.println("Got " + v.join());
                }
            });

            // waiting for all promises to reach us
            System.out.println("waiting for promises");
            System.out.println("results.size: " + resultMap.size());
            for (HttpRequest r : resultMap.keySet()) {
                System.out.println("Checking " + r);
                HttpResponse<String> response = resultMap.get(r).join();
                assertEquals(response.statusCode(), 200);
                assertEquals(response.version(), Version.HTTP_3);
                assertEquals(response.body(), tempFileAsString);
            }
            resultMap.forEach((k, v) -> {
                if (seen.add(k)) {
                    System.out.println("Got " + v.join());
                }
            });
            assertEquals(resultMap.size(), LOOPS + 1);
        }
    }

    // Test 2 - of(...) populating the given Map, everything as a String
    @Test
    public void testTypeStringOfMap() throws Exception {
        System.out.println("\n**** testTypeStringOfMap\n");
        String tempFileAsString = Files.readString(tempFile);
        ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<String>>>
                resultMap = new ConcurrentHashMap<>();

        PushPromiseHandler<String> pph =
                PushPromiseHandler.of(pushPromise -> BodyHandlers.ofString(UTF_8), resultMap);

        try (HttpClient client = newClientBuilderForH3().proxy(Builder.NO_PROXY)
                .sslContext(SimpleSSLContext.findSSLContext())
                .version(Version.HTTP_3).build()) {
            sendHeadRequest(client);
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            CompletableFuture<HttpResponse<String>> cf =
                    client.sendAsync(request, BodyHandlers.ofString(UTF_8), pph);
            cf.join();
            resultMap.put(request, cf);
            System.err.println("results.size: " + resultMap.size());
            for (HttpRequest r : resultMap.keySet()) {
                HttpResponse<String> response = resultMap.get(r).join();
                assertEquals(response.statusCode(), 200);
                assertEquals(response.version(), Version.HTTP_3);
                assertEquals(response.body(), tempFileAsString);
            }
            assertEquals(resultMap.size(), LOOPS + 1);
        }
    }

    // --- Path ---

    static final Path dir = Paths.get(".", "serverPush");
    static BodyHandler<Path> requestToPath(HttpRequest req) {
        URI u = req.uri();
        Path path = Paths.get(dir.toString(), u.getPath());
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException ee) {
            throw new UncheckedIOException(ee);
        }
        return BodyHandlers.ofFile(path);
    }

    // Test 3 - custom written push promise handler, everything as a Path
    @Test
    public void testTypePath() throws Exception {
        System.out.println("\n**** testTypePath\n");
        String tempFileAsString = Files.readString(tempFile);
        ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<Path>>> resultsMap
                = new ConcurrentHashMap<>();

        PushPromiseHandler<Path> pushPromiseHandler = (initial, pushRequest, acceptor) -> {
            BodyHandler<Path> pp = requestToPath(pushRequest);
            CompletableFuture<HttpResponse<Path>> cf = acceptor.apply(pp);
            resultsMap.put(pushRequest, cf);
        };

        try (HttpClient client = newClientBuilderForH3().proxy(Builder.NO_PROXY)
                .sslContext(SimpleSSLContext.findSSLContext())
                .version(Version.HTTP_3).build()) {
            sendHeadRequest(client);

            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            CompletableFuture<HttpResponse<Path>> cf =
                    client.sendAsync(request, requestToPath(request), pushPromiseHandler);
            cf.join();
            resultsMap.put(request, cf);
            for (HttpRequest r : resultsMap.keySet()) {
                HttpResponse<Path> response = resultsMap.get(r).join();
                assertEquals(response.statusCode(), 200);
                assertEquals(response.version(), Version.HTTP_3);
                String fileAsString = Files.readString(response.body());
                assertEquals(fileAsString, tempFileAsString);
            }
            assertEquals(resultsMap.size(), LOOPS + 1);
        }
    }

    // Test 4 - of(...) populating the given Map, everything as a Path
    @Test
    public void testTypePathOfMap() throws Exception {
        System.out.println("\n**** testTypePathOfMap\n");
        String tempFileAsString = Files.readString(tempFile);
        ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<Path>>> resultsMap
                = new ConcurrentHashMap<>();

        PushPromiseHandler<Path> pushPromiseHandler =
                PushPromiseHandler.of(H3ServerPush::requestToPath, resultsMap);

        try (HttpClient client = newClientBuilderForH3().proxy(Builder.NO_PROXY)
                .sslContext(SimpleSSLContext.findSSLContext())
                .version(Version.HTTP_3).build()) {
            sendHeadRequest(client);

            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            CompletableFuture<HttpResponse<Path>> cf =
                    client.sendAsync(request, requestToPath(request), pushPromiseHandler);
            cf.join();
            resultsMap.put(request, cf);
            for (HttpRequest r : resultsMap.keySet()) {
                HttpResponse<Path> response = resultsMap.get(r).join();
                assertEquals(response.statusCode(), 200);
                assertEquals(response.version(), Version.HTTP_3);
                String fileAsString = Files.readString(response.body());
                assertEquals(fileAsString, tempFileAsString);
            }
            assertEquals(resultsMap.size(), LOOPS + 1);
        }
    }

    // ---  Consumer<byte[]> ---

    static class ByteArrayConsumer implements Consumer<Optional<byte[]>> {
        volatile List<byte[]> listByteArrays = new ArrayList<>();
        volatile byte[] accumulatedBytes;

        public byte[] getAccumulatedBytes() { return accumulatedBytes; }

        @Override
        public void accept(Optional<byte[]> optionalBytes) {
            assert accumulatedBytes == null;
            if (optionalBytes.isEmpty()) {
                int size = listByteArrays.stream().mapToInt(ba -> ba.length).sum();
                ByteBuffer bb = ByteBuffer.allocate(size);
                listByteArrays.forEach(bb::put);
                accumulatedBytes = bb.array();
            } else {
                listByteArrays.add(optionalBytes.get());
            }
        }
    }

    // Test 5 - custom written handler, everything as a consumer of optional byte[]
    @Test
    public void testTypeByteArrayConsumer() throws Exception {
        System.out.println("\n**** testTypeByteArrayConsumer\n");
        String tempFileAsString = Files.readString(tempFile);
        ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<Void>>> resultsMap
                = new ConcurrentHashMap<>();
        Map<HttpRequest,ByteArrayConsumer> byteArrayConsumerMap
                = new ConcurrentHashMap<>();

        try (HttpClient client = newClientBuilderForH3().proxy(Builder.NO_PROXY)
                .sslContext(SimpleSSLContext.findSSLContext())
                .version(Version.HTTP_3).build()) {
            sendHeadRequest(client);

            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            ByteArrayConsumer bac = new ByteArrayConsumer();
            byteArrayConsumerMap.put(request, bac);

            PushPromiseHandler<Void> pushPromiseHandler = (initial, pushRequest, acceptor) -> {
                CompletableFuture<HttpResponse<Void>> cf = acceptor.apply(
                        (info) -> {
                            ByteArrayConsumer bc = new ByteArrayConsumer();
                            byteArrayConsumerMap.put(pushRequest, bc);
                            return BodySubscribers.ofByteArrayConsumer(bc);
                        });
                resultsMap.put(pushRequest, cf);
            };

            CompletableFuture<HttpResponse<Void>> cf =
                    client.sendAsync(request, BodyHandlers.ofByteArrayConsumer(bac), pushPromiseHandler);
            cf.join();
            resultsMap.put(request, cf);
            for (HttpRequest r : resultsMap.keySet()) {
                HttpResponse<Void> response = resultsMap.get(r).join();
                assertEquals(response.statusCode(), 200);
                assertEquals(response.version(), Version.HTTP_3);
                byte[] ba = byteArrayConsumerMap.get(r).getAccumulatedBytes();
                String result = new String(ba, UTF_8);
                assertEquals(result, tempFileAsString);
            }
            assertEquals(resultsMap.size(), LOOPS + 1);
        }
    }

    // Test 6 - of(...) populating the given Map, everything as a consumer of optional byte[]
    @Test
    public void testTypeByteArrayConsumerOfMap() throws Exception {
        System.out.println("\n**** testTypeByteArrayConsumerOfMap\n");
        String tempFileAsString = Files.readString(tempFile);
        ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<Void>>> resultsMap
                = new ConcurrentHashMap<>();
        Map<HttpRequest, ByteArrayConsumer> byteArrayConsumerMap
                = new ConcurrentHashMap<>();

        try (HttpClient client = newClientBuilderForH3().proxy(Builder.NO_PROXY)
                .sslContext(SimpleSSLContext.findSSLContext())
                .version(Version.HTTP_3).build()) {
            sendHeadRequest(client);

            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            ByteArrayConsumer bac = new ByteArrayConsumer();
            byteArrayConsumerMap.put(request, bac);

            PushPromiseHandler<Void> pushPromiseHandler =
                    PushPromiseHandler.of(
                            pushRequest -> {
                                ByteArrayConsumer bc = new ByteArrayConsumer();
                                byteArrayConsumerMap.put(pushRequest, bc);
                                return BodyHandlers.ofByteArrayConsumer(bc);
                            },
                            resultsMap);

            CompletableFuture<HttpResponse<Void>> cf =
                    client.sendAsync(request, BodyHandlers.ofByteArrayConsumer(bac), pushPromiseHandler);
            cf.join();
            resultsMap.put(request, cf);
            for (HttpRequest r : resultsMap.keySet()) {
                HttpResponse<Void> response = resultsMap.get(r).join();
                assertEquals(response.statusCode(), 200);
                assertEquals(response.version(), Version.HTTP_3);
                byte[] ba = byteArrayConsumerMap.get(r).getAccumulatedBytes();
                String result = new String(ba, UTF_8);
                assertEquals(result, tempFileAsString);
            }
            assertEquals(resultsMap.size(), LOOPS + 1);
        }
    }
}
