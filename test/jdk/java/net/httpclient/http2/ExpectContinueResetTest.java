/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @summary Verifies that the client reacts correctly to receiving RST_STREAM at various stages of
 *          a Partial Response.
 * @bug 8309118
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run testng/othervm -Djdk.internal.httpclient.debug=true -Djdk.httpclient.HttpClient.log=errors,headers ExpectContinueResetTest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.BodyOutputStream;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2TestExchangeImpl;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestServerConnection;

import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.frame.HeaderFrame;
import jdk.internal.net.http.frame.ResetFrame;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.*;

public class ExpectContinueResetTest {

    Http2TestServer http2TestServer;
    final String samplePost = "Sample Post";

    URI warmup, postSuccessfully, postExceptionally;
    URI resetStreamAfter100NoError, resetStreamAfter100Error, resetStreamAfter200NoError, resetStreamAfter200Error;

    static PrintStream err = new PrintStream(System.err);

    @DataProvider(name = "testDataUnconsumedBody")
    public Object[][] testDataUnconsumedBody() {
        // Not consuming the InputStream in the server's handler results in different handling of RST_STREAM client-side
        return new Object[][] {
                { postSuccessfully, false }, // Checks RST_STREAM is ignored after client sees an END_STREAM
                { postExceptionally, true }  // Checks RST_STREAM is processed if client sees no END_STREAM
        };
    }

    @DataProvider(name = "testDataConsumedBody")
    public Object[][] testDataConsumedBody() {
        return new Object[][] {
                // All client requests to these URIs should complete exceptionally
                { resetStreamAfter100NoError }, // Client receives RST_STREAM before END_STREAM and 200
                { resetStreamAfter100Error },  // Client receives RST_STREAM before END_STREAM and 200
                { resetStreamAfter200NoError }, // Client receives RST_STREAM after 200 but before server sends END_STREAM
                { resetStreamAfter200Error } // Client receives RST_STREAM after 200 but before server sends END_STREAM
        };
    }

    @Test(dataProvider = "testDataUnconsumedBody")
    public void testUnconsumedBody(URI uri, boolean exceptionally) {
        err.printf("\nTesting with Version: %s, URI: %s\n", HTTP_2, uri);
        HttpRequest.BodyPublisher testPub = HttpRequest.BodyPublishers.ofString(samplePost);
        HttpResponse<String> resp = null;
        Throwable testThrowable = null;
        try {
            resp = performRequest(testPub, uri);
        } catch (Exception e) {
            testThrowable = e.getCause();
        }
        if (exceptionally) {
            assertNotNull(testThrowable, "Request should have completed exceptionally but testThrowable is null");
            assertEquals(testThrowable.getClass(), IOException.class, "Test should have closed with an IOException");
        } else {
            assertNull(testThrowable);
            assertNotNull(resp);
            assertEquals(resp.statusCode(), 200);
        }
    }

    @Test(dataProvider = "testDataConsumedBody")
    public void testConsumedBody(URI uri) {
        err.printf("\nTesting with Version: %s, URI: %s\n", HTTP_2, uri);
        HttpRequest.BodyPublisher testPub = HttpRequest.BodyPublishers.ofString(samplePost);
        Throwable testThrowable = null;
        try {
            performRequest(testPub, uri);
        } catch (Exception e) {
            testThrowable = e.getCause();
        }
        assertNotNull(testThrowable, "Request should have completed exceptionally but testThrowable is null");
        assertEquals(testThrowable.getClass(), IOException.class, "Test should have closed with an IOException");
    }

    @BeforeTest
    public void setup() throws Exception {
        http2TestServer = new Http2TestServer(false, 0);
        http2TestServer.setExchangeSupplier(ExpectContinueResetTestExchangeImpl::new);
        http2TestServer.addHandler(new GetHandler().toHttp2Handler(), "/warmup");

        http2TestServer.addHandler(new PostHandlerHttp2(), "/http2/resetStreamAfter100NoError");
        http2TestServer.addHandler(new PostHandlerHttp2(), "/http2/resetStreamAfter100Error");
        http2TestServer.addHandler(new PostHandlerHttp2(), "/http2/resetStreamAfter200Error");
        http2TestServer.addHandler(new PostHandlerHttp2(), "/http2/resetStreamAfter200NoError");

        http2TestServer.addHandler(new TestHandlerEndStreamOn200(), "/testHandlerSuccessfully");
        http2TestServer.addHandler(new TestHandlerNoEndStreamOn200(), "/testHandlerExceptionally");
        warmup = URI.create("http://" + http2TestServer.serverAuthority() + "/warmup");

        resetStreamAfter100NoError = URI.create("http://" + http2TestServer.serverAuthority() + "/http2/resetStreamAfter100NoError");
        resetStreamAfter100Error = URI.create("http://" + http2TestServer.serverAuthority() + "/http2/resetStreamAfter100Error");
        resetStreamAfter200NoError = URI.create("http://" + http2TestServer.serverAuthority() + "/http2/resetStreamAfter200NoError");
        resetStreamAfter200Error = URI.create("http://" + http2TestServer.serverAuthority() + "/http2/resetStreamAfter200Error");

        postSuccessfully = URI.create("http://" + http2TestServer.serverAuthority() + "/testHandlerSuccessfully");
        postExceptionally = URI.create("http://" + http2TestServer.serverAuthority() + "/testHandlerExceptionally");
        http2TestServer.start();
    }

    @AfterTest
    public void teardown() {
        http2TestServer.stop();
    }

    private HttpResponse<String> performRequest(HttpRequest.BodyPublisher bodyPublisher, URI uri)
            throws IOException, InterruptedException, ExecutionException {
        try (HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).version(HTTP_2).build()) {
            err.printf("Performing warmup request to %s", warmup);
            client.send(HttpRequest.newBuilder(warmup).GET().version(HTTP_2).build(), HttpResponse.BodyHandlers.discarding());
            HttpRequest postRequest = HttpRequest.newBuilder(uri)
                    .version(HTTP_2)
                    .POST(bodyPublisher)
                    .expectContinue(true)
                    .build();
            err.printf("Sending request (%s): %s%n", HTTP_2, postRequest);
            CompletableFuture<HttpResponse<String>> cf = client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString());
            return cf.get();
        }
    }

    static class GetHandler implements HttpServerAdapters.HttpTestHandler {

        @Override
        public void handle(HttpServerAdapters.HttpTestExchange exchange) throws IOException {
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] bytes = "Response Body".getBytes(UTF_8);
                err.printf("Server sending 200  (length=%s)", bytes.length);
                exchange.sendResponseHeaders(200, bytes.length);
                err.println("Server sending Response Body");
                os.write(bytes);
            }
        }
    }

    static class PostHandlerHttp2 implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            if (exchange instanceof ExpectContinueResetTestExchangeImpl impl) {
                String path = exchange.getRequestURI().getPath();
                impl.handleTestExchange(path);
            }
        }
    }

    static class TestHandlerEndStreamOn200 implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            err.println("Sending 100");
            exchange.sendResponseHeaders(100, -1);
            err.println("Sending 200");
            exchange.sendResponseHeaders(200, -1);
            // Setting responseLength to -1, sets the END_STREAM flag on the ResponseHeaders before sending a RST_STREAM frame.
            // Therefore, there is no need to explicitly send a RST_STREAM here as this will be sent by the Server impl.
            err.println("Sending Reset");
        }
    }

    static class TestHandlerNoEndStreamOn200 implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            err.println("Sending 100");
            exchange.sendResponseHeaders(100, 0);
            err.println("Sending 200");
            exchange.sendResponseHeaders(200, 0);
            if (exchange instanceof ExpectContinueResetTestExchangeImpl testExchange) {
                err.println("Sending Reset");
                testExchange.addResetToOutputQ(ResetFrame.NO_ERROR);
            } else {
                throw new RuntimeException("Wrong Exchange type used");
            }
        }
    }
    static class ExpectContinueResetTestExchangeImpl extends Http2TestExchangeImpl {

        public ExpectContinueResetTestExchangeImpl(int streamid, String method, HttpHeaders reqheaders, HttpHeadersBuilder rspheadersBuilder, URI uri, InputStream is, SSLSession sslSession, BodyOutputStream os, Http2TestServerConnection conn, boolean pushAllowed) {
            super(streamid, method, reqheaders, rspheadersBuilder, uri, is, sslSession, os, conn, pushAllowed);
        }

        public void addResetToOutputQ(int code) throws IOException {
            ResetFrame rf = new ResetFrame(streamid, code);
            this.conn.addToOutputQ(rf);
        }

        public void handleTestExchange(String path) throws IOException {
            //Based on request path, execute a different response case
            try (InputStream reqBody = this.getRequestBody()) {
                switch (path) {
                    case "/http2/endStream" -> sendEndStreamHeaders();
                    case "/http2/resetStreamAfter100NoError" -> resetStreamAfter100NoError(reqBody);
                    case "/http2/resetStreamAfter100Error" -> resetStreamAfter100Error(reqBody);
                    case "/http2/resetStreamAfter200NoError" -> resetStreamAfter200NoError(reqBody);
                    case "/http2/resetStreamAfter200Error" -> resetStreamAfter200Error(reqBody);
                    default -> sendResponseHeaders(400, 0);
                }
            }
        }

        private void sendEndStreamHeaders() throws IOException {
            this.responseLength = 0;
            rspheadersBuilder.setHeader(":status", Integer.toString(100));
            HttpHeaders headers = rspheadersBuilder.build();
            Http2TestServerConnection.ResponseHeaders response
                    = new Http2TestServerConnection.ResponseHeaders(headers);
            response.streamid(streamid);
            response.setFlag(HeaderFrame.END_HEADERS);
            response.setFlag(HeaderFrame.END_STREAM);
            sendResponseHeaders(response);
        }

        private void resetStreamAfter100NoError(InputStream reqBody) throws IOException {
            err.println("IN HANDLER");
            this.sendResponseHeaders(100, 0);
            reqBody.readAllBytes();
            // Send Reset Frame immediately after Response Headers
            addResetToOutputQ(ResetFrame.NO_ERROR);
        }

        private void resetStreamAfter100Error(InputStream reqBody) throws IOException {
            this.sendResponseHeaders(100, 0);
            reqBody.readAllBytes();
            // Send Reset Frame immediately after Response Headers
            addResetToOutputQ(ResetFrame.PROTOCOL_ERROR);
        }

        public void resetStreamAfter200NoError(InputStream reqBody) throws IOException {
            this.sendResponseHeaders(100, 0);
            reqBody.readAllBytes();
            this.sendResponseHeaders(200, 0);
            // Send Reset after reading data and 200 sent. This means the RST_STREAM will be received by the client before
            // an empty DATA_FRAME with the END_STREAM flag sent causing the exchange to complete exceptionally.
            addResetToOutputQ(ResetFrame.NO_ERROR);
        }

        public void resetStreamAfter200Error(InputStream reqBody) throws IOException {
            this.sendResponseHeaders(100, 0);
            reqBody.readAllBytes();
            this.sendResponseHeaders(200, 0);
            // Send Reset after reading data and 200 sent. This means the RST_STREAM will be received by the client before
            // an empty DATA_FRAME with the END_STREAM flag sent causing the exchange to complete exceptionally.
            addResetToOutputQ(ResetFrame.PROTOCOL_ERROR);
        }
    }
}
