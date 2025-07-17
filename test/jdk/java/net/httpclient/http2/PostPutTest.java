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
 * @bug 8293786
 * @summary Checks to see if the HttpClient can process a request to cancel a transmission from a remote if the server
 *          does not process any data. The client should read all data from the server and close the connection.
 * @library /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer
 * @run testng/othervm/timeout=50 -Djdk.httpclient.HttpClient.log=all
 *                      PostPutTest
 */

import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2TestServer;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpRequest.BodyPublishers.ofByteArray;

public class PostPutTest {

    Http2TestServer http2TestServer;
    URI warmupURI, testHandlerBasicURI, testHandlerCloseBosURI, testHandleNegativeContentLengthURI;
    static PrintStream testLog = System.err;

    // As per jdk.internal.net.http.WindowController.DEFAULT_INITIAL_WINDOW_SIZE
    final int DEFAULT_INITIAL_WINDOW_SIZE = (64 * 1024) - 1;
    // Add on a small amount of arbitrary bytes to see if client hangs when receiving RST_STREAM
    byte[] data = new byte[DEFAULT_INITIAL_WINDOW_SIZE + 10];

    @BeforeTest
    public void setup() throws Exception {
        http2TestServer = new Http2TestServer(false, 0);
        http2TestServer.addHandler(new WarmupHandler(), "/Warmup");
        http2TestServer.addHandler(new TestHandlerBasic(), "/TestHandlerBasic");
        http2TestServer.addHandler(new TestHandlerCloseBos(), "/TestHandlerCloseBos");
        http2TestServer.addHandler(new TestHandleNegativeContentLength(), "/TestHandleNegativeContentLength");
        http2TestServer.start();
        testLog.println("PostPutTest.setup(): Starting server");
        warmupURI = new URI("http://" + http2TestServer.serverAuthority() + "/Warmup");
        testHandlerBasicURI = new URI("http://" + http2TestServer.serverAuthority() + "/TestHandlerBasic");
        testHandlerCloseBosURI = new URI("http://" + http2TestServer.serverAuthority() + "/TestHandlerCloseBos");
        testHandleNegativeContentLengthURI = new URI("http://" + http2TestServer.serverAuthority() + "/TestHandleNegativeContentLength");
        testLog.println("PostPutTest.setup(): warmupURI: " + warmupURI);
        testLog.println("PostPutTest.setup(): testHandlerBasicURI: " + testHandlerBasicURI);
        testLog.println("PostPutTest.setup(): testHandlerCloseBosURI: " + testHandlerCloseBosURI);
        testLog.println("PostPutTest.setup(): testHandleNegativeContentLengthURI: " + testHandleNegativeContentLengthURI);
    }

    @AfterTest
    public void teardown() {
        testLog.println("PostPutTest.teardown(): Stopping server");
        http2TestServer.stop();
        data = null;
    }

    @DataProvider(name = "variants")
    public Object[][] variants() {
        HttpRequest over64kPost, over64kPut, over64kPostCloseBos, over64kPutCloseBos, over64kPostNegativeContentLength, over64kPutNegativeContentLength;
        over64kPost = HttpRequest.newBuilder().version(HTTP_2).POST(ofByteArray(data)).uri(testHandlerBasicURI).build();
        over64kPut = HttpRequest.newBuilder().version(HTTP_2).PUT(ofByteArray(data)).uri(testHandlerBasicURI).build();

        over64kPostCloseBos = HttpRequest.newBuilder().version(HTTP_2).POST(ofByteArray(data)).uri(testHandlerCloseBosURI).build();
        over64kPutCloseBos = HttpRequest.newBuilder().version(HTTP_2).PUT(ofByteArray(data)).uri(testHandlerCloseBosURI).build();

        over64kPostNegativeContentLength = HttpRequest.newBuilder().version(HTTP_2).POST(ofByteArray(data)).uri(testHandleNegativeContentLengthURI).build();
        over64kPutNegativeContentLength = HttpRequest.newBuilder().version(HTTP_2).PUT(ofByteArray(data)).uri(testHandleNegativeContentLengthURI).build();

        return new Object[][] {
                { over64kPost, "POST data over 64k bytes" },
                { over64kPut, "PUT data over 64k bytes" },
                { over64kPostCloseBos, "POST data over 64k bytes with close bos" },
                { over64kPutCloseBos, "PUT data over 64k bytes with close bos" },
                { over64kPostNegativeContentLength, "POST data over 64k bytes with negative content length" },
                { over64kPutNegativeContentLength, "PUT data over 64k bytes with negative content length" }
        };
    }

    public HttpRequest getWarmupReq() {
        return HttpRequest.newBuilder()
                .GET()
                .uri(warmupURI)
                .build();
    }

    @Test(dataProvider = "variants")
    public void testOver64kPUT(HttpRequest req, String testMessage) {
        testLog.println("PostPutTest: Performing test: " + testMessage);
        HttpClient hc = HttpClient.newBuilder().version(HTTP_2).build();
        hc.sendAsync(getWarmupReq(), HttpResponse.BodyHandlers.ofString()).join();
        hc.sendAsync(req, HttpResponse.BodyHandlers.ofString()).join();
        /*
            If this test fails in timeout, it is likely due to one of two reasons:
              - The responseSubscriber is null, so no incoming frames are being processed by the client
                (See Stream::schedule)
              - The test server is for some reason not sending a RST_STREAM with the NO_ERROR flag set after
                sending an empty DATA frame with the END_STREAM flag set.
        */
    }

    private static class TestHandlerBasic implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            // The input stream is not read in this bug as this will trigger window updates for the server. This bug
            // concerns the case where no updates are sent and the server instead tells the client to abort the transmission.
            exchange.sendResponseHeaders(200, 0);
        }
    }

    private static class TestHandlerCloseBos implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            // This case does actually cause the test to hang due to the body input stream being closed before it can send
            // the RST_STREAM frame.
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        }
    }

    private static class TestHandleNegativeContentLength implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, -1);
        }
    }

    private static class WarmupHandler implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, 0);
        }
    }
}