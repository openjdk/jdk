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
 * @test id=with-continuations
 * @bug 8087112 8372409
 * @requires os.family != "windows" | ( os.name != "Windows 10" & os.name != "Windows Server 2016"
 *                                      & os.name != "Windows Server 2019" )
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 * @run testng/othervm/timeout=360 -XX:+CrashOnOutOfMemoryError
 *                     -Djdk.httpclient.quic.minPtoBackoffTime=60
 *                     -Djdk.httpclient.quic.maxPtoBackoffTime=90
 *                     -Djdk.httpclient.quic.maxPtoBackoff=10
 *                     -Djdk.internal.httpclient.quic.useNioSelector=false
 *                     -Djdk.internal.httpclient.quic.poller.usePlatformThreads=false
 *                     -Djdk.httpclient.quic.maxEndpoints=-1
 *                     -Djdk.httpclient.http3.maxStreamLimitTimeout=0
 *                     -Djdk.internal.httpclient.quic.maxBidiStreams=2
 *                     -Djdk.httpclient.retryOnStreamlimit=50
 *                     -Djdk.httpclient.HttpClient.log=errors,http3,quic:retransmit
 *                     -Dsimpleget.requests=100
 *                     H3MultipleConnectionsToSameHost
 * @summary test multiple connections and concurrent requests with blocking IO and virtual threads
 */
/*
 * @test id=without-continuations
 * @bug 8087112 8372409
 * @requires os.family == "windows" & ( os.name == "Windows 10" | os.name == "Windows Server 2016"
 *                                     | os.name == "Windows Server 2019" )
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 * @run testng/othervm/timeout=360 -XX:+CrashOnOutOfMemoryError
 *                     -Djdk.httpclient.quic.minPtoBackoffTime=45
 *                     -Djdk.httpclient.quic.maxPtoBackoffTime=60
 *                     -Djdk.httpclient.quic.maxPtoBackoff=9
 *                     -Djdk.internal.httpclient.quic.useNioSelector=false
 *                     -Djdk.internal.httpclient.quic.poller.usePlatformThreads=false
 *                     -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations
 *                     -Djdk.httpclient.quic.maxEndpoints=-1
 *                     -Djdk.httpclient.http3.maxStreamLimitTimeout=0
 *                     -Djdk.internal.httpclient.quic.maxBidiStreams=2
 *                     -Djdk.httpclient.retryOnStreamlimit=50
 *                     -Djdk.httpclient.HttpClient.log=errors,http3,quic:retransmit
 *                     -Dsimpleget.requests=100
 *                     H3MultipleConnectionsToSameHost
 * @summary test multiple connections and concurrent requests with blocking IO and virtual threads
 *          on windows 10 and windows 2016 - but with -XX:-VMContinuations
 */
/*
 * @test id=useNioSelector
 * @bug 8087112 8372409
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 * @run testng/othervm/timeout=360 -XX:+CrashOnOutOfMemoryError
 *                     -Djdk.httpclient.quic.idleTimeout=120
 *                     -Djdk.httpclient.keepalive.timeout.h3=120
 *                     -Djdk.test.server.quic.idleTimeout=90
 *                     -Djdk.httpclient.quic.minPtoBackoffTime=60
 *                     -Djdk.httpclient.quic.maxPtoBackoffTime=120
 *                     -Djdk.httpclient.quic.maxPtoBackoff=9
 *                     -Djdk.internal.httpclient.quic.useNioSelector=true
 *                     -Djdk.httpclient.http3.maxStreamLimitTimeout=0
 *                     -Djdk.httpclient.quic.maxEndpoints=1
 *                     -Djdk.internal.httpclient.quic.maxBidiStreams=2
 *                     -Djdk.httpclient.retryOnStreamlimit=50
 *                     -Djdk.httpclient.HttpClient.log=errors,http3,quic:hs:retransmit
 *                     -Dsimpleget.requests=100
 *                     H3MultipleConnectionsToSameHost
 * @summary Send 100 large concurrent requests, with connections whose max stream
 *          limit is artificially low, in order to cause concurrent connections
 *          to the same host to be created, with non-blocking IO and selector
 */
/*
 * @test id=reno-cc
 * @bug 8087112 8372409
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 * @run testng/othervm/timeout=360 -XX:+CrashOnOutOfMemoryError
 *                     -Djdk.httpclient.quic.idleTimeout=120
 *                     -Djdk.httpclient.keepalive.timeout.h3=120
 *                     -Djdk.test.server.quic.idleTimeout=90
 *                     -Djdk.httpclient.quic.minPtoBackoffTime=60
 *                     -Djdk.httpclient.quic.maxPtoBackoffTime=120
 *                     -Djdk.httpclient.quic.maxPtoBackoff=9
 *                     -Djdk.httpclient.http3.maxStreamLimitTimeout=0
 *                     -Djdk.httpclient.quic.maxEndpoints=1
 *                     -Djdk.internal.httpclient.quic.maxBidiStreams=2
 *                     -Djdk.httpclient.retryOnStreamlimit=50
 *                     -Djdk.httpclient.HttpClient.log=errors,http3,quic:hs:retransmit
 *                     -Dsimpleget.requests=100
 *                     -Djdk.internal.httpclient.quic.congestionController=reno
 *                     H3MultipleConnectionsToSameHost
 * @summary Send 100 large concurrent requests, with connections whose max stream
 *          limit is artificially low, in order to cause concurrent connections
 *          to the same host to be created, with Reno congestion controller
 */

// Interesting additional settings for debugging and manual testing:
// -----------------------------------------------------------------
// -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations
// -Djdk.httpclient.HttpClient.log=errors,requests,http3,quic
// -Djdk.httpclient.HttpClient.log=requests,errors,quic:retransmit:control,http3
// -Djdk.httpclient.HttpClient.log=errors,requests,quic:all
// -Djdk.httpclient.quic.defaultMTU=64000
// -Djdk.httpclient.quic.defaultMTU=16384
// -Djdk.httpclient.quic.defaultMTU=4096
// -Djdk.httpclient.http3.maxStreamLimitTimeout=1375
// -Xmx16g
// -Djdk.httpclient.quic.defaultMTU=16384
// -Djdk.internal.httpclient.debug=err
// -XX:+HeapDumpOnOutOfMemoryError
// -Djdk.httpclient.HttpClient.log=errors,quic:cc
// -Djdk.httpclient.quic.sendBufferSize=16384
// -Djdk.httpclient.quic.receiveBufferSize=16384

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
import jdk.internal.net.http.common.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.Assert;
import org.testng.annotations.Test;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static jdk.internal.net.http.Http3ClientProperties.MAX_STREAM_LIMIT_WAIT_TIMEOUT;

public class H3MultipleConnectionsToSameHost implements HttpServerAdapters {
    static HttpTestServer httpsServer;
    static HttpClient client = null;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    static String httpsURIString;
    static ExecutorService serverExec =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                    .name("server-vt-worker-", 1).factory());

    static void initialize() throws Exception {
        try {
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
        // warmup server
        try (var client2 = createClient(sslContext, Executors.newVirtualThreadPerTaskExecutor())) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(httpsURIString))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .HEAD().build();
            client2.send(request, BodyHandlers.ofByteArrayConsumer(b-> {}));
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
            client.send(request, BodyHandlers.ofByteArrayConsumer(b-> {}));
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
            int maxBidiStreams = Utils.getIntegerNetProperty("jdk.internal.httpclient.quic.maxBidiStreams", 100);
            long timeout = MAX_STREAM_LIMIT_WAIT_TIMEOUT;

            Set<String> connections = new ConcurrentSkipListSet<>();
            HttpRequest request = HttpRequest.newBuilder(URI.create(httpsURIString))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET().build();
            long start = System.nanoTime();
            var resp = client.send(request, BodyHandlers.ofByteArrayConsumer(b-> {}));
            Assert.assertEquals(resp.statusCode(), 200);
            long elapsed = System.nanoTime() - start;
            System.out.println("First request took: " + elapsed + " nanos (" + TimeUnit.NANOSECONDS.toMillis(elapsed) + " ms)");
            final int max = property("simpleget.requests", 50);
            List<CompletableFuture<HttpResponse<Void>>> list = new ArrayList<>(max);
            long start2 = System.nanoTime();
            for (int i = 0; i < max; i++) {
                int rqNum = i;
                var cf = client.sendAsync(request, BodyHandlers.ofByteArrayConsumer(b-> {}))
                                .whenComplete((r, t) -> {
                                    Optional.ofNullable(r)
                                            .flatMap(HttpResponse::connectionLabel)
                                            .ifPresent(connections::add);
                                    if (r != null) {
                                        System.out.println(rqNum + " completed: " + r.connectionLabel());
                                    } else {
                                        System.out.println(rqNum + " failed: " + t);
                                    }
                                });
                list.add(cf);
                //cf.get(); // uncomment to test with serial instead of concurrent requests
            }
            try {
                CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).join();
            } finally {
                long elapsed2 = System.nanoTime() - start2;
                long completed = list.stream().filter(CompletableFuture::isDone)
                        .filter(Predicate.not(CompletableFuture::isCompletedExceptionally)).count();
                if (completed > 0) {
                    System.out.println("Next " + completed + " requests took: " + elapsed2 + " nanos ("
                            + TimeUnit.NANOSECONDS.toMillis(elapsed2) + "ms for " + completed + " requests): "
                            + elapsed2 / completed + " nanos per request (" + TimeUnit.NANOSECONDS.toMillis(elapsed2) / completed
                            + " ms) on " + connections.size() + " connections");
                }
                if (completed == list.size()) {
                    long msPerRequest = TimeUnit.NANOSECONDS.toMillis(elapsed2) / completed;
                    if (timeout == 0 || timeout < msPerRequest) {
                        int expectedCount = max / maxBidiStreams;
                        if (expectedCount > 2) {
                            if (connections.size() < expectedCount - Math.max(1, expectedCount/5)) {
                                throw new AssertionError(
                                        "Too few connections: %s for %s requests with %s streams/connection (timeout %s ms)"
                                                .formatted(connections.size(), max, maxBidiStreams, timeout));
                            }
                        }
                    }
                    if (connections.size() > max - Math.max(1, max/5)) {
                        throw new AssertionError(
                                "Too few connections: %s for %s requests with %s streams/connection (timeout %s ms)"
                                        .formatted(connections.size(), max, maxBidiStreams, timeout));
                    }

                }
            }
            list.forEach((cf) -> Assert.assertEquals(cf.join().statusCode(), 200));
            client.close();
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
