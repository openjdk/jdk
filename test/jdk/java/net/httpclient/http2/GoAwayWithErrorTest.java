/*
 * Copyright (c) 2025, Oracle and/or its affiliates.
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2TestExchangeImpl;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.internal.net.http.frame.ErrorFrame;
import jdk.test.lib.net.SimpleSSLContext;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8371903
 * @summary Verify that HTTP/2 client properly handles GOAWAY frames with error codes
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.httpclient.test.lib.http2.Http2TestExchange
 *        jdk.httpclient.test.lib.http2.Http2TestExchangeImpl
 * @run junit GoAwayWithErrorTest
 */

public class GoAwayWithErrorTest {

    static final int PROTOCOL_ERROR = ErrorFrame.PROTOCOL_ERROR;
    static final String DEBUG_MESSAGE = "Test GOAWAY error from server";
    static final byte[] DEBUG_DATA = DEBUG_MESSAGE.getBytes(UTF_8);
    static SSLContext sslContext;
    static Http2TestServer server;
    static int port;
    static AtomicBoolean firstRequestHandled;
    static AtomicBoolean secondRequestHandled;
    static CountDownLatch goAwaySentLatch;

    @BeforeAll
    static void setup() throws Exception {
        firstRequestHandled = new AtomicBoolean(false);
        secondRequestHandled = new AtomicBoolean(false);
        goAwaySentLatch = new CountDownLatch(1);
        sslContext = new SimpleSSLContext().get();
        server = new Http2TestServer(true, 0, null, sslContext);
        server.addHandler(new GoAwayHandler(), "/");
        server.start();
        port = server.getAddress().getPort();
        System.out.println("Server started on port: " + port);
    }

    @AfterAll
    static void teardown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testGoAwayWithProtocolError() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .version(HTTP_2)
                .build();

        URI uri = URI.create("https://localhost:" + port + "/test");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .build();

        CompletableFuture<HttpResponse<String>> first = client.sendAsync(
                request, HttpResponse.BodyHandlers.ofString());

        if (!goAwaySentLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("GOAWAY not sent in time");
        }

        CompletableFuture<HttpResponse<String>> second = client.sendAsync(
                request, HttpResponse.BodyHandlers.ofString());

        CompletableFuture<HttpResponse<String>> third = client.sendAsync(
                request, HttpResponse.BodyHandlers.ofString());

        CompletableFuture.allOf(first, second, third)
                .orTimeout(20, TimeUnit.SECONDS)
                .exceptionally(ex -> null)
                .join();

        Throwable firstError = first.handle((r, ex) -> ex).join();
        if (firstError instanceof CompletionException ce && ce.getCause() != null) {
            firstError = ce.getCause();
        }

        assertTrue(firstError != null && firstError.getMessage() != null,
                "First request should fail with GOAWAY error");
        assertTrue(firstError.getMessage().contains("GOAWAY")
                        && firstError.getMessage().contains("Protocol error")
                        && firstError.getMessage().contains("0x1")
                        && firstError.getMessage().contains(DEBUG_MESSAGE),
                "First request should contain GOAWAY error details: " + firstError.getMessage());

        HttpResponse<String> secondResponse = second.handle((r, ex) -> r).join();
        HttpResponse<String> thirdResponse = third.handle((r, ex) -> r).join();

        assertTrue(secondResponse != null && secondResponse.statusCode() == 200,
                "Second request (unprocessed stream) should be retried and succeed");
        assertTrue(thirdResponse != null && thirdResponse.statusCode() == 200,
                "Third request (unprocessed stream) should be retried and succeed");

        assertTrue(secondRequestHandled.get(),
                "Second and third requests should reach server after being retried");

        System.out.println("Test passed: GOAWAY error details preserved and unprocessed streams retried");
    }

    static class GoAwayHandler implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            System.out.println("Handler: received request for " + exchange.getRequestURI());

            Http2TestExchangeImpl impl = (Http2TestExchangeImpl) exchange;

            if (!firstRequestHandled.getAndSet(true)) {
                System.out.println("Handler: sending GOAWAY(PROTOCOL_ERROR, lastStreamId=1) and closing connection");

                impl.getServerConnection().sendGoAway(1, PROTOCOL_ERROR, DEBUG_DATA);
                impl.getServerConnection().close(PROTOCOL_ERROR);
                goAwaySentLatch.countDown();
                return;
            }

            secondRequestHandled.set(true);
            System.out.println("Handler: successfully handling retried request");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        }
    }
}
