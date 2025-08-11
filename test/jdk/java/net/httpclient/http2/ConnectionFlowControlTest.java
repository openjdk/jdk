/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8342075
 * @summary checks connection flow control
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm  -Djdk.internal.httpclient.debug=true
 *                      -Djdk.httpclient.connectionWindowSize=65535
 *                      -Djdk.httpclient.windowsize=16384
 *                      ConnectionFlowControlTest
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.httpclient.test.lib.http2.BodyOutputStream;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2TestExchangeImpl;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestServerConnection;
import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.frame.ContinuationFrame;
import jdk.internal.net.http.frame.HeaderFrame;
import jdk.internal.net.http.frame.HeadersFrame;
import jdk.internal.net.http.frame.Http2Frame;
import jdk.internal.net.http.frame.SettingsFrame;
import jdk.test.lib.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.List.of;
import static java.util.Map.entry;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class ConnectionFlowControlTest {

    SSLContext sslContext;
    HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    String http2URI;
    String https2URI;
    final AtomicInteger reqid = new AtomicInteger();


    @DataProvider(name = "variants")
    public Object[][] variants() {
        return new Object[][] {
                { http2URI },
                { https2URI },
        };
    }

    @Test(dataProvider = "variants")
    void test(String uri) throws Exception {
        System.out.printf("%ntesting %s%n", uri);
        ConcurrentHashMap<String, CompletableFuture<String>> responseSent = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, HttpResponse<InputStream>> responses = new ConcurrentHashMap<>();
        FCHttp2TestExchange.setResponseSentCB((s) -> responseSent.get(s).complete(s));
        int connectionWindowSize = Math.max(Integer.getInteger(
                "jdk.httpclient.connectionWindowSize", 65535), 65535);
        int windowSize = Math.max(Integer.getInteger(
                "jdk.httpclient.windowsize", 65535), 16384);
        int max = connectionWindowSize / windowSize + 2;
        System.out.printf("connection window: %s, stream window: %s, will make %s requests%n",
                connectionWindowSize, windowSize, max);

        try (HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build()) {
            String label = null;

            Throwable t = null;
            try {
                String[] keys = new String[max];
                for (int i = 0; i < max; i++) {
                    String query = "reqId=" + reqid.incrementAndGet();
                    keys[i] = query;
                    URI uriWithQuery = URI.create(uri + "?" + query);
                    CompletableFuture<String> sent = new CompletableFuture<>();
                    responseSent.put(query, sent);
                    HttpRequest request = HttpRequest.newBuilder(uriWithQuery)
                            .POST(BodyPublishers.ofString("Hello there!"))
                            .build();
                    System.out.println("\nSending request:" + uriWithQuery);
                    final HttpClient cc = client;
                    var response = cc.send(request, BodyHandlers.ofInputStream());
                    responses.put(query, response);
                    String ckey = response.headers().firstValue("X-Connection-Key").get();
                    if (label == null) label = ckey;
                    try {
                        if (i < max - 1) {
                            // the connection window might be exceeded at i == max - 2, which
                            // means that the last request could go on a new connection.
                            assertEquals(ckey, label, "Unexpected key for " + query);
                        }
                    } catch (AssertionError ass) {
                        // since we won't pull all responses, the client
                        // will not exit unless we ask it to shutdown now.
                        client.shutdownNow();
                        throw ass;
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    // ignore
                }
                CompletableFuture<?> allsent = CompletableFuture.allOf(responseSent.values().stream()
                        .toArray(CompletableFuture<?>[]::new));
                allsent.get();
                for (int i = 0; i < max; i++) {
                    try {
                        String query = keys[i];
                        var response = responses.get(keys[i]);
                        String ckey = response.headers().firstValue("X-Connection-Key").get();
                        if (label == null) label = ckey;
                        if (i < max - 1) {
                            // the connection window might be exceeded at i == max - 2, which
                            // means that the last request could go on a new connection.
                            assertEquals(ckey, label, "Unexpected key for " + query);
                        }
                        int wait = uri.startsWith("https://") ? 500 : 250;
                        try (InputStream is = response.body()) {
                            Thread.sleep(Utils.adjustTimeout(wait));
                            is.readAllBytes();
                        }
                        System.out.printf("%s did not fail: %s%n", query, response.statusCode());
                    } catch (AssertionError t1) {
                        // since we won't pull all responses, the client
                        // will not exit unless we ask it to shutdown now.
                        client.shutdownNow();
                        throw t1;
                    } catch (Throwable t0) {
                        System.out.println("Got EXPECTED: " + t0);
                        if (t0 instanceof ExecutionException) {
                            t0 = t0.getCause();
                        }
                        t = t0;
                        try {
                            assertDetailMessage(t0, i);
                        } catch (AssertionError e) {
                            // since we won't pull all responses, the client
                            // will not exit unless we ask it to shutdown now.
                            client.shutdownNow();
                            throw e;
                        }
                    }
                }
            } catch (Throwable t0) {
                System.out.println("Got EXPECTED: " + t0);
                if (t0 instanceof ExecutionException) {
                    t0 = t0.getCause();
                }
                t = t0;
            }
            if (t == null) {
                // we could fail here if we haven't waited long enough
                fail("Expected exception, got all responses, should sleep time be raised?");
            } else {
                assertDetailMessage(t, max);
            }
            String query = "reqId=" + reqid.incrementAndGet();
            URI uriWithQuery = URI.create(uri + "?" + query);
            CompletableFuture<String> sent = new CompletableFuture<>();
            responseSent.put(query, sent);
            HttpRequest request = HttpRequest.newBuilder(uriWithQuery)
                    .POST(BodyPublishers.ofString("Hello there!"))
                    .build();
            System.out.println("\nSending last request:" + uriWithQuery);
            var response = client.send(request, BodyHandlers.ofString());
            if (label != null) {
                String ckey = response.headers().firstValue("X-Connection-Key").get();
                assertNotEquals(ckey, label);
                System.out.printf("last request %s sent on different connection as expected:" +
                        "\n\tlast: %s\n\tprevious: %s%n", query, ckey, label);
            }
        }
    }

    // Assertions based on implementation specific detail messages. Keep in
    // sync with implementation.
    static void assertDetailMessage(Throwable throwable, int iterationIndex) {
        try {
            Throwable cause = throwable;
            while (cause != null) {
                if (cause instanceof ProtocolException) {
                    if (cause.getMessage().contains("connection window exceeded")) {
                       System.out.println("Found expected exception: " + cause);
                       return;
                    }
                }
                cause = cause.getCause();
            }
            throw new AssertionError(
                    "ProtocolException(\"protocol error: connection window exceeded\") not found",
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
        https2URI = "https://" + this.https2TestServer.serverAuthority() + "/https2/x";

        // Override the default exchange supplier with a custom one to enable
        // particular test scenarios
        http2TestServer.setExchangeSupplier(FCHttp2TestExchange::new);
        https2TestServer.setExchangeSupplier(FCHttp2TestExchange::new);

        this.http2TestServer.start();
        this.https2TestServer.start();
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
                System.out.println("Server " + t.getLocalAddress() + " received:\n"
                        + t.getRequestURI() + ": " + new String(bytes, StandardCharsets.UTF_8));
                t.getResponseHeaders().setHeader("X-Connection-Key", t.getConnectionKey());

                if (bytes.length == 0) bytes = "no request body!".getBytes(StandardCharsets.UTF_8);
                int window = Math.max(16384, Integer.getInteger("jdk.httpclient.windowsize", 2*16*1024));
                 final int maxChunkSize;
                if (t instanceof FCHttp2TestExchange fct) {
                    maxChunkSize = Math.min(window, fct.conn.getMaxFrameSize());
                } else {
                    maxChunkSize = Math.min(window, SettingsFrame.MAX_FRAME_SIZE);
                }
                byte[] resp = bytes.length < maxChunkSize
                        ? bytes
                        : Arrays.copyOfRange(bytes, 0, maxChunkSize);
                int max = (window / resp.length);
                // send in chunks
                t.sendResponseHeaders(200, 0);
                int sent = 0;
                for (int i=0; i<=max; i++) {
                    int len = Math.min(resp.length, window - sent);
                    if (len <= 0) break;
                    if (os instanceof BodyOutputStream bos) {
                        try {
                            // we don't wait for the stream window, but we want
                            // to wait for the connection window
                            bos.waitForStreamWindow(len);
                        } catch (InterruptedException ie) {
                            // ignore and continue...
                        }
                    }
                    ((BodyOutputStream) os).writeUncontrolled(resp, 0, len);
                    sent += len;
                }
                if (sent != window) fail("should have sent %s, sent %s".formatted(window, sent));
            }
            if (t instanceof FCHttp2TestExchange fct) {
                fct.responseSent(query);
            } else {
                fail("Exchange is not %s but %s"
                        .formatted(FCHttp2TestExchange.class.getName(), t.getClass().getName()));
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
