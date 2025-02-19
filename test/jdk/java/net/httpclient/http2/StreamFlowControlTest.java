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
 * @bug 8342075 8343855
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm  -Djdk.internal.httpclient.debug=true
 *                      -Djdk.httpclient.connectionWindowSize=65535
 *                      -Djdk.httpclient.windowsize=16384
 *                      StreamFlowControlTest
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpHeadOrGetHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.httpclient.test.lib.http2.BodyOutputStream;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2TestExchangeImpl;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestServerConnection;
import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.frame.SettingsFrame;
import jdk.test.lib.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class StreamFlowControlTest {

    SSLContext sslContext;
    HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    String http2URI;
    String https2URI;
    final AtomicInteger reqid = new AtomicInteger();


    @DataProvider(name = "variants")
    public Object[][] variants() {
        return new Object[][] {
                { http2URI,  false },
                { https2URI, false },
                { http2URI,  true },
                { https2URI, true },
        };
    }

    static void sleep(long wait) throws InterruptedException {
        if (wait <= 0) return;
        long remaining = Utils.adjustTimeout(wait);
        long start = System.nanoTime();
        while (remaining > 0) {
            Thread.sleep(remaining);
            long end = System.nanoTime();
            remaining = remaining - NANOSECONDS.toMillis(end - start);
        }
        System.out.printf("Waited %s ms%n",
                NANOSECONDS.toMillis(System.nanoTime() - start));
    }


    @Test(dataProvider = "variants")
    void test(String uri,
              boolean sameClient)
        throws Exception
    {
        System.out.printf("%ntesting test(%s, %s)%n", uri, sameClient);
        ConcurrentHashMap<String, CompletableFuture<String>>  responseSent = new ConcurrentHashMap<>();
        FCHttp2TestExchange.setResponseSentCB((s) -> responseSent.get(s).complete(s));

        HttpClient client = null;
        try {
            int max = sameClient ? 10 : 3;
            String label = null;
            for (int i = 0; i < max; i++) {
                if (!sameClient || client == null)
                    client = HttpClient.newBuilder().sslContext(sslContext).build();

                String query = "reqId=" + reqid.incrementAndGet();
                URI uriWithQuery = URI.create(uri + "?" + query);
                CompletableFuture<String> sent = new CompletableFuture<>();
                responseSent.put(query, sent);
                HttpRequest request = HttpRequest.newBuilder(uriWithQuery)
                        .GET()
                        .build();
                System.out.println("\nSending request:" + uriWithQuery);
                final HttpClient cc = client;
                try {
                    HttpResponse<InputStream> response = cc.send(request, BodyHandlers.ofInputStream());
                    if (sameClient) {
                        String key = response.headers().firstValue("X-Connection-Key").get();
                        if (label == null) label = key;
                        assertEquals(key, label, "Unexpected key for " + query);
                    }
                    sent.join();
                    // we have to pull to get the exception, but slow enough
                    // so that DataFrames are buffered up to the point that
                    // the window is exceeded...
                    long wait = uri.startsWith("https://") ? 800 : 350;
                    try (InputStream is = response.body()) {
                        sleep(wait);
                        is.readAllBytes();
                    }
                    // we could fail here if we haven't waited long enough
                    fail("Expected exception, got :" + response + ", should sleep time be raised?");
                } catch (IOException ioe) {
                    System.out.println("Got EXPECTED: " + ioe);
                    assertDetailMessage(ioe, i);
                } finally {
                    if (!sameClient && client != null) {
                        client.close();
                        client = null;
                    }
                }
            }
        } finally {
            if (sameClient && client != null) client.close();
        }

    }

    @Test(dataProvider = "variants")
    void testAsync(String uri,
                   boolean sameClient)
    {
        System.out.printf("%ntesting testAsync(%s, %s)%n", uri, sameClient);
        ConcurrentHashMap<String, CompletableFuture<String>> responseSent = new ConcurrentHashMap<>();
        FCHttp2TestExchange.setResponseSentCB((s) -> responseSent.get(s).complete(s));

        HttpClient client = null;
        try {
            int max = sameClient ? 5 : 3;
            String label = null;
            for (int i = 0; i < max; i++) {
                if (!sameClient || client == null)
                    client = HttpClient.newBuilder().sslContext(sslContext).build();

                String query = "reqId=" + reqid.incrementAndGet();
                URI uriWithQuery = URI.create(uri + "?" + query);
                CompletableFuture<String> sent = new CompletableFuture<>();
                responseSent.put(query, sent);
                HttpRequest request = HttpRequest.newBuilder(uriWithQuery)
                        .GET()
                        .build();
                System.out.println("\nSending request:" + uriWithQuery);
                final HttpClient cc = client;

                Throwable t = null;
                try {
                    HttpResponse<InputStream> response = cc.sendAsync(request, BodyHandlers.ofInputStream()).get();
                    if (sameClient) {
                        String key = response.headers().firstValue("X-Connection-Key").get();
                        if (label == null) label = key;
                        assertEquals(key, label, "Unexpected key for " + query);
                    }
                    sent.join();
                    long wait = uri.startsWith("https://") ? 800 : 350;
                    try (InputStream is = response.body()) {
                        sleep(wait);
                        is.readAllBytes();
                    }
                    // we could fail here if we haven't waited long enough
                    fail("Expected exception, got :" + response + ", should sleep time be raised?");
                } catch (Throwable t0) {
                    System.out.println("Got EXPECTED: " + t0);
                    if (t0 instanceof ExecutionException) {
                        t0 = t0.getCause();
                    }
                    t = t0;
                } finally {
                    if (!sameClient && client != null) {
                        client.close();
                        client = null;
                    }
                }
                assertDetailMessage(t, i);
            }
        } finally {
            if (sameClient && client != null) client.close();
        }
    }

    // Assertions based on implementation specific detail messages. Keep in
    // sync with implementation.
    static void assertDetailMessage(Throwable throwable, int iterationIndex) {
        try {
            Throwable cause = throwable;
            while (cause != null) {
                if (cause instanceof ProtocolException) {
                    if (cause.getMessage().matches("stream [0-9]+ flow control window exceeded")) {
                       System.out.println("Found expected exception: " + cause);
                       return;
                    }
                }
                cause = cause.getCause();
            }
            throw new AssertionError(
                    "ProtocolException(\"stream X flow control window exceeded\") not found",
                             throwable);
        } catch (AssertionError e) {
            System.out.println("Exception does not match expectation: " + throwable);
            throwable.printStackTrace(System.out);
            throw e;
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        var http2TestServer = new Http2TestServer("localhost", false, 0);
        http2TestServer.addHandler(new Http2TestHandler(), "/http2/");
        this.http2TestServer = HttpTestServer.of(http2TestServer);
        http2URI = "http://" + this.http2TestServer.serverAuthority() + "/http2/x";

        var https2TestServer = new Http2TestServer("localhost", true, sslContext);
        https2TestServer.addHandler(new Http2TestHandler(), "/https2/");
        this.https2TestServer = HttpTestServer.of(https2TestServer);
        this.https2TestServer.addHandler(new HttpHeadOrGetHandler(), "/https2/head/");
        https2URI = "https://" + this.https2TestServer.serverAuthority() + "/https2/x";
        String h2Head = "https://" + this.https2TestServer.serverAuthority() + "/https2/head/z";

        // Override the default exchange supplier with a custom one to enable
        // particular test scenarios
        http2TestServer.setExchangeSupplier(FCHttp2TestExchange::new);
        https2TestServer.setExchangeSupplier(FCHttp2TestExchange::new);

        this.http2TestServer.start();
        this.https2TestServer.start();

        // warmup to eliminate delay due to SSL class loading and initialization.
        try (var client = HttpClient.newBuilder().sslContext(sslContext).build()) {
            var request = HttpRequest.newBuilder(URI.create(h2Head)).HEAD().build();
            var resp = client.send(request, BodyHandlers.discarding());
            assertEquals(resp.statusCode(), 200);
        }
    }

    @AfterTest
    public void teardown() throws Exception {
        http2TestServer.stop();
        https2TestServer.stop();
    }

    static class Http2TestHandler implements Http2Handler {

        @Override
        public void handle(Http2TestExchange t) throws IOException {
            String query = t.getRequestURI().getRawQuery();

            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {

                byte[] bytes = is.readAllBytes();
                if (bytes.length != 0) {
                    System.out.println("Server " + t.getLocalAddress() + " received:\n"
                            + t.getRequestURI() + ": " + new String(bytes, StandardCharsets.UTF_8));
                } else {
                    System.out.println("No request body for " + t.getRequestMethod());
                }

                t.getResponseHeaders().setHeader("X-Connection-Key", t.getConnectionKey());

                if (bytes.length == 0) {
                    bytes = "no request body!"
                            .repeat(100).getBytes(StandardCharsets.UTF_8);
                }
                int window = Integer.getInteger("jdk.httpclient.windowsize", 2 * 16 * 1024);
                final int maxChunkSize;
                if (t instanceof FCHttp2TestExchange fct) {
                    maxChunkSize = Math.min(window, fct.conn.getMaxFrameSize());
                } else {
                    maxChunkSize = Math.min(window, SettingsFrame.MAX_FRAME_SIZE);
                }
                byte[] resp = bytes.length <= maxChunkSize
                        ? bytes
                        : Arrays.copyOfRange(bytes, 0, maxChunkSize);
                int max = (window / resp.length) + 2;
                // send in chunks
                t.sendResponseHeaders(200, 0);
                for (int i = 0; i <= max; i++) {
                    if (t instanceof FCHttp2TestExchange fct) {
                        try {
                            // we don't wait for the stream window, but we want
                            // to wait for the connection window
                            fct.conn.obtainConnectionWindow(resp.length);
                        } catch (InterruptedException ie) {
                            var ioe = new InterruptedIOException(ie.toString());
                            ioe.initCause(ie);
                            throw ioe;
                        }
                    }
                    try {
                        ((BodyOutputStream) os).writeUncontrolled(resp, 0, resp.length);
                    } catch (IOException x) {
                        if (t instanceof FCHttp2TestExchange fct) {
                            fct.conn.updateConnectionWindow(resp.length);
                        }
                        throw x;
                    }
                }
            } finally {
                if (t instanceof FCHttp2TestExchange fct) {
                    fct.responseSent(query);
                } else {
                    fail("Exchange is not %s but %s"
                            .formatted(FCHttp2TestExchange.class.getName(), t.getClass().getName()));
                }
            }
        }
    }

    // A custom Http2TestExchangeImpl that overrides sendResponseHeaders to
    // allow headers to be sent with a number of CONTINUATION frames.
    static class FCHttp2TestExchange extends Http2TestExchangeImpl {
        static volatile Consumer<String> responseSentCB;
        static void setResponseSentCB(Consumer<String> responseSentCB) {
            FCHttp2TestExchange.responseSentCB = responseSentCB;
        }

        final Http2TestServerConnection conn;
        FCHttp2TestExchange(int streamid, String method, HttpHeaders reqheaders,
                             HttpHeadersBuilder rspheadersBuilder, URI uri, InputStream is,
                             SSLSession sslSession, BodyOutputStream os,
                             Http2TestServerConnection conn, boolean pushAllowed) {
            super(streamid, method, reqheaders, rspheadersBuilder, uri, is, sslSession, os, conn, pushAllowed);
            this.conn = conn;
        }
        public void responseSent(String query) {
            System.out.println("Server: response sent for " + query);
            responseSentCB.accept(query);
        }

    }
}
