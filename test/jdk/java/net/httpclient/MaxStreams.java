/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8196389
 * @summary Should HttpClient support SETTINGS_MAX_CONCURRENT_STREAMS from the server
 *
 * @modules java.base/sun.net.www.http
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.logging
 *          jdk.httpserver
 * @library /lib/testlibrary http2/server
 * @build Http2TestServer
 * @build jdk.testlibrary.SimpleSSLContext
 * @run testng/othervm -ea -esa MaxStreams
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import jdk.testlibrary.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

public class MaxStreams {

    Http2TestServer http2TestServer;   // HTTP/2 ( h2c )
    Http2TestServer https2TestServer;   // HTTP/2 ( h2 )
    final Http2FixedHandler handler = new Http2FixedHandler();
    SSLContext ctx;
    String http2FixedURI;
    String https2FixedURI;
    volatile CountDownLatch latch;
    ExecutorService exec;
    final Semaphore canStartTestRun = new Semaphore(1);

    // we send an initial warm up request, then MAX_STREAMS+1 requests
    // in parallel. The last of them should hit the limit.
    // Then we wait for all the responses and send a further request
    // which should succeed. The server should see (and respond to)
    // MAX_STREAMS+2 requests per test run.

    static final int MAX_STREAMS = 10;
    static final String RESPONSE = "Hello world";

    @DataProvider(name = "uris")
    public Object[][] variants() {
        return new Object[][]{
                {http2FixedURI},
                {https2FixedURI},
                {http2FixedURI},
                {https2FixedURI}
        };
    }


    @Test(dataProvider = "uris", timeOut=20000)
    void testAsString(String uri) throws Exception {
        canStartTestRun.acquire();
        latch = new CountDownLatch(1);
        handler.setLatch(latch);
        HttpClient client = HttpClient.newBuilder().sslContext(ctx).build();
        List<CompletableFuture<HttpResponse<String>>> responses = new LinkedList<>();

        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                                         .version(HttpClient.Version.HTTP_2)
                                         .GET()
                                         .build();
        // send warmup to ensure we only have one Http2Connection
        HttpResponse<String> warmup = client.send(request, BodyHandlers.ofString());
        if (warmup.statusCode() != 200 || !warmup.body().equals(RESPONSE))
            throw new RuntimeException();

        for (int i=0;i<MAX_STREAMS+1; i++) {
            responses.add(client.sendAsync(request, BodyHandlers.ofString()));
        }

        // wait until we get local exception before allow server to proceed
        try {
            CompletableFuture.anyOf(responses.toArray(new CompletableFuture<?>[0])).join();
        } catch (Exception ee) {
            System.err.println("Expected exception 1 " + ee);
        }

        latch.countDown();

        // check the first MAX_STREAMS requests succeeded
        try {
            CompletableFuture.allOf(responses.toArray(new CompletableFuture<?>[0])).join();
            System.err.println("Did not get Expected exception 2 ");
        } catch (Exception ee) {
            System.err.println("Expected exception 2 " + ee);
        }
        int count = 0;
        int failures = 0;
        for (CompletableFuture<HttpResponse<String>> cf : responses) {
            HttpResponse<String> r = null;
            try {
                count++;
                r = cf.join();
                if (r.statusCode() != 200 || !r.body().equals(RESPONSE))
                    throw new RuntimeException();
            } catch (Throwable t) {
                failures++;
                System.err.printf("Failure %d at count %d\n", failures, count);
                System.err.println(t);
                t.printStackTrace();
            }
        }
        if (failures != 1) {
            String msg = "Expected 1 failure. Got " + failures;
            throw new RuntimeException(msg);
        }

        // make sure it succeeds now as number of streams == 0 now
        HttpResponse<String> warmdown = client.send(request, BodyHandlers.ofString());
        if (warmdown.statusCode() != 200 || !warmdown.body().equals(RESPONSE))
            throw new RuntimeException();
        System.err.println("Test OK");
    }

    @BeforeTest
    public void setup() throws Exception {
        ctx = (new SimpleSSLContext()).get();
        exec = Executors.newCachedThreadPool();

        InetSocketAddress sa = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

        Properties props = new Properties();
        props.setProperty("http2server.settings.max_concurrent_streams", Integer.toString(MAX_STREAMS));
        http2TestServer = new Http2TestServer("localhost", false, 0, exec, 10, props, null);
        http2TestServer.addHandler(handler, "/http2/fixed");
        http2FixedURI = "http://" + http2TestServer.serverAuthority()+ "/http2/fixed";
        http2TestServer.start();

        https2TestServer = new Http2TestServer("localhost", true, 0, exec, 10, props, ctx);
        https2TestServer.addHandler(handler, "/http2/fixed");
        https2FixedURI = "https://" + https2TestServer.serverAuthority()+ "/http2/fixed";
        https2TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        System.err.println("Stopping test server now");
        http2TestServer.stop();
    }

    class Http2FixedHandler implements Http2Handler {
        final AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch;

        synchronized void setLatch(CountDownLatch latch) {
            this.latch = latch;
        }

        synchronized CountDownLatch getLatch() {
            return latch;
        }

        @Override
        public void handle(Http2TestExchange t) throws IOException {
            int c = -1;
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {

                is.readAllBytes();
                c = counter.getAndIncrement();
                if (c > 0 && c <= MAX_STREAMS) {
                    // Wait for latch.
                    try {
                        // don't send any replies until all requests are sent
                        System.err.println("latch await");
                        getLatch().await();
                        System.err.println("latch resume");
                    } catch (InterruptedException ee) {}
                }
                t.sendResponseHeaders(200, RESPONSE.length());
                os.write(RESPONSE.getBytes());
            } finally {
                // client issues MAX_STREAMS + 3 requests in total
                // but server should only see MAX_STREAMS + 2 in total. One is rejected by client
                // counter c captured before increment so final value is MAX_STREAMS + 1
                if (c == MAX_STREAMS + 1) {
                    counter.set(0);
                    canStartTestRun.release();
                }
            }
        }
    }
}
