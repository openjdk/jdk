/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests that the server_name TLS extension (SNI) is not used for requests
 *          to URLs with literal IP addresses
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @bug 8314519
 * @run testng/othervm -Djdk.net.hosts.file=SNITest.hosts SNITest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;

public class SNITest implements HttpServerAdapters {

    static final String FAST_PATH = "/fast";
    static final String BLOCKING_PATH = "/blocking";

    static final ExecutorService serverExecutor = Executors.newFixedThreadPool(2);
    static HttpTestServer h1TestServer;
    static PrintStream testLog = System.err;
    static SSLContext sslContext;
    static CountDownLatch cdl = new CountDownLatch(1);

    HttpClient hc;
    URI testURI;

    @BeforeTest
    public void setup() throws IOException, URISyntaxException {
        String hostsFileName = System.getProperty("jdk.net.hosts.file");
        try (FileWriter writer= new FileWriter(hostsFileName, false)) {
            writer.write("127.0.0.1 localhost.localdomain");
        }
        sslContext = new SimpleSSLContext().get();
        h1TestServer = HttpTestServer.create(HTTP_1_1, sslContext, serverExecutor);

        h1TestServer.addHandler(new FastHandler(), FAST_PATH);
        h1TestServer.addHandler(new BlockingHandler(), BLOCKING_PATH);
        testURI = URIBuilder.newBuilder()
                .scheme("https")
                .loopback()
                .port(h1TestServer.getAddress().getPort())
                .build();
        h1TestServer.start();
        testLog.println("HTTP/1.1 Server up at address: " + h1TestServer.getAddress());
        testLog.println("Request URI for Client: " + testURI);

        hc = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .sslContext(sslContext)
                .build();
    }

    @AfterTest
    public void teardown() {
        if (h1TestServer != null)
            h1TestServer.stop();
    }

    @Test
    public void requestTest() throws IOException, InterruptedException {
        URI fastUri = URI.create(testURI + FAST_PATH);
        URI blockingUri = URI.create(testURI + BLOCKING_PATH);
        // first request to prime the connection pool cache
        HttpRequest fastReq = HttpRequest.newBuilder()
                .version(HTTP_1_1)
                .GET()
                .uri(fastUri)
                .build();
        HttpResponse<String> resp = hc.send(fastReq, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(resp.statusCode(), 200, resp.body());
        assertEquals(resp.version(), HTTP_1_1);
        // start second request to remove the connection from the pool
        HttpRequest blockingReq = HttpRequest.newBuilder()
                .version(HTTP_1_1)
                .GET()
                .uri(blockingUri)
                .build();
        CompletableFuture<HttpResponse<String>> asyncResp =
                hc.sendAsync(blockingReq, HttpResponse.BodyHandlers.ofString(UTF_8));
        // third request to establish a new connection.
        // If SNI is included, this request will fail.
        HttpResponse<String> resp3 = hc.send(fastReq, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(resp3.statusCode(), 200, resp3.body());
        assertEquals(resp3.version(), HTTP_1_1);

        // finish second request
        cdl.countDown();
        HttpResponse<String> resp2 = asyncResp.join();
        assertEquals(resp2.statusCode(), 200, resp3.body());
        assertEquals(resp2.version(), HTTP_1_1);
    }

    public static void sendResponse(HttpTestExchange ex, String body, int rCode) throws IOException {
        try (OutputStream os = ex.getResponseBody()) {
            byte[] bytes = body.getBytes(UTF_8);
            ex.sendResponseHeaders(rCode, bytes.length);
            os.write(bytes);
        }
    }

    static class FastHandler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            testLog.println("Processing fast request");
            sendResponse(exchange, "Response completed", 200);
        }
    }

    static class BlockingHandler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            testLog.println("Received blocking request");
            try {
                cdl.await();
            } catch (InterruptedException e) {
                sendResponse(exchange, "Wait interrupted", 500);
                return;
            }
            testLog.println("Processing blocking request");
            sendResponse(exchange, "Response completed", 200);
        }
    }
}
