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
 * @run testng/othervm/timeout=240 -Djdk.tracePinnedThreads=full H3SimplePost
 */
// -Djdk.tracePinnedThreads=full
// -Djdk.httpclient.HttpClient.log=requests,errors,quic
// -Djdk.httpclient.quic.defaultMTU=64000
// -Djdk.httpclient.quic.defaultMTU=16384
// -Djdk.httpclient.http3.maxStreamLimitTimeout=1375


import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;

public class H3SimplePost implements HttpServerAdapters {
    static HttpTestServer httpsServer;
    static HttpClient client = null;
    static SSLContext sslContext;
    static String httpsURIString;
    static ExecutorService serverExec =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                    .name("server-vt-worker-", 1).factory());

    static void initialize() throws Exception {
        try {
            SimpleSSLContext sslct = new SimpleSSLContext();
            sslContext = sslct.get();
            client = getClient();

            httpsServer = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext, serverExec);
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
        SimpleSSLContext sslct = new SimpleSSLContext();
        var sslContext = sslct.get();

        // warmup server
        try (var client2 = createClient(sslContext, Executors.newVirtualThreadPerTaskExecutor())) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(httpsURIString))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .HEAD().build();
            client2.send(request, BodyHandlers.discarding());
        }

        // warmup client
        var httpsServer2 = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext,
                Executors.newVirtualThreadPerTaskExecutor());
        httpsServer2.addHandler(new TestHandler(), "/");
        var httpsURIString2 = "https://" + httpsServer2.serverAuthority() + "/bar/";
        httpsServer2.start();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(httpsURIString2))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
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
            System.out.println("Initialization and warmup took "+ TimeUnit.NANOSECONDS.toMillis(done-prestart)+" millis");
            // Thread.sleep(30000);
            HttpRequest getRequest = HttpRequest.newBuilder(URI.create(httpsURIString))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET().build();

            byte[][] requestData = new byte[REPEAT][];
            Arrays.fill(requestData, RESPONSE_BYTES);
            HttpRequest postRequest = HttpRequest.newBuilder(URI.create(httpsURIString))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(Arrays.asList(requestData)))
                    .build();
            long start = System.nanoTime();
            var resp = client.send(getRequest, BodyHandlers.ofByteArrayConsumer(b-> {}));
            Assert.assertEquals(resp.statusCode(), 200);
            long elapsed = System.nanoTime() - start;
            System.out.println("First GET request took: " + elapsed + " nanos (" + TimeUnit.NANOSECONDS.toMillis(elapsed) + " ms)");
            final int max = 50;
            List<CompletableFuture<HttpResponse<Void>>> list = new ArrayList<>(max);
            long start2 = System.nanoTime();
            for (int i = 0; i < max; i++) {
                list.add(client.sendAsync(postRequest, BodyHandlers.ofByteArrayConsumer(b -> {
                })));
            }
            CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).join();
            long elapsed2 = System.nanoTime() - start2;
            System.out.println("Next " + max + " POST requests took: " + elapsed2 + " nanos ("
                    + TimeUnit.NANOSECONDS.toMillis(elapsed2) + "ms for " + max + " requests): "
                    + elapsed2 / max + " nanos per request (" + TimeUnit.NANOSECONDS.toMillis(elapsed2) / max + " ms)");
            list.forEach((cf) -> Assert.assertEquals(cf.join().statusCode(), 200));
        } catch (Throwable tt) {
            System.err.println("tt caught");
            tt.printStackTrace();
            throw tt;
        } finally {
            httpsServer.stop();
        }
    }

    static HttpClient createClient(SSLContext sslContext, ExecutorService clientExec) {
        var builder = HttpServerAdapters.createClientBuilderForH3()
                .sslContext(sslContext)
                .version(HTTP_3)
                .proxy(Builder.NO_PROXY);
        if (clientExec != null) {
            builder = builder.executor(clientExec);
        }
        return builder.build();
    }

    static HttpClient getClient() {
        if (client == null) {
            client = createClient(sslContext, null);
        }
        return client;
    }

    private final static int REPEAT = 32;
    private final static String RESPONSE = "abcdefghij".repeat(1024*32);
    private final static byte[] RESPONSE_BYTES = RESPONSE.getBytes(StandardCharsets.UTF_8);

    private static class TestHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            // consume all input bytes,
            try (var in = t.getRequestBody()) {
                in.skip(Integer.MAX_VALUE);
                t.sendResponseHeaders(200, 0);
                t.getResponseBody().close();
            }
        }
    }

}
