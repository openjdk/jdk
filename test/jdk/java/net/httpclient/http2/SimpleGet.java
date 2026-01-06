/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8087112
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.common.TestUtil
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 * @run testng/othervm -XX:+CrashOnOutOfMemoryError SimpleGet
 * @run testng/othervm -XX:+CrashOnOutOfMemoryError
 *                     -Dsimpleget.repeat=1 -Dsimpleget.chunks=1 -Dsimpleget.requests=1000
 *                     SimpleGet
 * @run testng/othervm -Dsimpleget.requests=150
 *                     -Dsimpleget.chunks=16384
 *                     -Djdk.httpclient.redirects.retrylimit=5
 *                     -Djdk.httpclient.HttpClient.log=errors
 *                     -XX:+CrashOnOutOfMemoryError
 *                     -XX:+HeapDumpOnOutOfMemoryError
 *                      SimpleGet
 */

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.Assert;
import org.testng.annotations.Test;

import static java.net.http.HttpClient.Version.HTTP_2;

public class SimpleGet implements HttpServerAdapters {
    static HttpTestServer httpsServer;
    static HttpClient client = null;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    static String httpsURIString;
    static ExecutorService serverExec = Executors.newVirtualThreadPerTaskExecutor();

    static void initialize() throws Exception {
        try {
            client = getClient();

            httpsServer = HttpTestServer.create(HTTP_2, sslContext, serverExec);
            httpsServer.addHandler(new TestHandler(), "/");
            httpsURIString = "https://" + httpsServer.serverAuthority() + "/bar/";

            httpsServer.start();
            warmup();
        } catch (Throwable e) {
            System.err.println("Throwing now");
            e.printStackTrace();
            throw e;
        }
    }

    private static void warmup() throws Exception {
        // warmup server
        try (var client2 = createClient(sslContext)) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(httpsURIString))
                    .version(HTTP_2)
                    .HEAD().build();
            client2.send(request, BodyHandlers.discarding());
        }

        // warmup client
        var httpsServer2 = HttpTestServer.create(HTTP_2, sslContext,
                Executors.newVirtualThreadPerTaskExecutor());
        httpsServer2.addHandler(new TestHandler(), "/");
        var httpsURIString2 = "https://" + httpsServer2.serverAuthority() + "/bar/";
        httpsServer2.start();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(httpsURIString2))
                    .version(HTTP_2)
                    .HEAD().build();
            client.send(request, BodyHandlers.discarding());
        } finally {
            httpsServer2.stop();
        }
    }

    public static void main(String[] args) throws Exception {
        test();
    }

    @Test
    public static void test() throws Exception {
        try {
            long prestart = System.nanoTime();
            initialize();
            long done = System.nanoTime();
            System.out.println("Stat: Initialization and warmup took " + TimeUnit.NANOSECONDS.toMillis(done - prestart) + " millis");
            HttpRequest request = HttpRequest.newBuilder(URI.create(httpsURIString))
                    .version(HTTP_2)
                    .GET().build();
            long start = System.nanoTime();
            var resp = client.send(request, BodyHandlers.ofByteArrayConsumer(b -> {}));
            Assert.assertEquals(resp.statusCode(), 200);
            long elapsed = System.nanoTime() - start;
            System.out.println("Stat: First request took: " + elapsed + " nanos (" + TimeUnit.NANOSECONDS.toMillis(elapsed) + " ms)");
            final int max = property("simpleget.requests", 50);
            ;
            List<CompletableFuture<HttpResponse<Void>>> list = new ArrayList<>(max);
            Set<String> connections = new ConcurrentSkipListSet<>();
            long start2 = System.nanoTime();
            for (int i = 0; i < max; i++) {
                var cf = client.sendAsync(request, BodyHandlers.ofByteArrayConsumer(b -> {}))
                        .whenComplete((r, t) -> Optional.ofNullable(r)
                                .flatMap(HttpResponse::connectionLabel)
                                .ifPresent(connections::add));
                list.add(cf);
                //cf.get(); // uncomment to test with serial instead of concurrent requests
            }
            try {
                CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).join();
            } finally {
                long elapsed2 = System.nanoTime() - start2;
                long completed = list.stream().filter(CompletableFuture::isDone)
                        .filter(Predicate.not(CompletableFuture::isCompletedExceptionally)).count();
                connections.forEach(System.out::println);
                if (completed > 0) {
                    System.out.println("Stat: Next " + completed + " requests took: " + elapsed2 + " nanos ("
                            + TimeUnit.NANOSECONDS.toMillis(elapsed2) + "ms for " + completed + " requests): "
                            + elapsed2 / completed + " nanos per request ("
                            + TimeUnit.NANOSECONDS.toMillis(elapsed2) / completed + " ms) on "
                            + connections.size() + " connections");
                }
            }
            list.forEach((cf) -> Assert.assertEquals(cf.join().statusCode(), 200));
        } catch (Throwable tt) {
            System.err.println("tt caught");
            tt.printStackTrace();
            throw tt;
        } finally {
            httpsServer.stop();
        }
    }

    static HttpClient createClient(SSLContext sslContext) {
        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .version(HTTP_2)
                .proxy(Builder.NO_PROXY)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    static HttpClient getClient() {
        if (client == null) {
            client = createClient(sslContext);
        }
        return client;
    }

    static int property(String name, int defaultValue) {
        return Integer.parseInt(System.getProperty(name, String.valueOf(defaultValue)));
    }

    // 32 * 32 * 1024 * 10 chars = 10Mb responses
    // 50 requests   => 500Mb
    // 100 requests  => 1Gb
    // 1000 requests => 10Gb
    private final static int REPEAT = property("simpleget.repeat", 32);
    private final static String RESPONSE = "abcdefghij".repeat(property("simpleget.chunks", 1024*32));
    private final static byte[] RESPONSE_BYTES = RESPONSE.getBytes(StandardCharsets.UTF_8);

    private static class TestHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
             try (var in = t.getRequestBody()) {
                byte[] input = in.readAllBytes();
                t.sendResponseHeaders(200, RESPONSE_BYTES.length * REPEAT);
                try (var out = t.getResponseBody()) {
                    if (t.getRequestMethod().equals("HEAD")) return;
                    for (int i=0; i<REPEAT; i++) {
                        out.write(RESPONSE_BYTES);
                    }
                }
            }
        }
    }

}
