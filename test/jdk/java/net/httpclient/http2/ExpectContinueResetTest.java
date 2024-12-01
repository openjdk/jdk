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
 * @summary Verifies that the client reacts correctly to receiving RST_STREAM at various stages of
 *          a Partial Response.
 * @bug 8309118
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run testng/othervm/timeout=40  -Djdk.internal.httpclient.debug=true -Djdk.httpclient.HttpClient.log=trace,errors,headers
 *                              ExpectContinueResetTest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.BodyOutputStream;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2TestExchangeImpl;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestServerConnection;

import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.frame.ResetFrame;
import org.testng.TestException;
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
import java.util.Iterator;
import java.util.concurrent.ExecutionException;

import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.*;

public class ExpectContinueResetTest {

    Http2TestServer http2TestServer;
    // "NoError" urls complete with an exception. "NoError" or "Error" here refers to the error code in the RST_STREAM frame
    // and not the outcome of the test.
    URI warmup, partialResponseResetNoError, partialResponseResetError, fullResponseResetNoError, fullResponseResetError;

    static PrintStream err = new PrintStream(System.err);

    @DataProvider(name = "testData")
    public Object[][] testData() {
        // Not consuming the InputStream in the server's handler results in different handling of RST_STREAM client-side
        return new Object[][] {
                { partialResponseResetNoError },
                { partialResponseResetError },  // Checks RST_STREAM is processed if client sees no END_STREAM
                { fullResponseResetNoError },
                { fullResponseResetError }
        };
    }


    @Test(dataProvider = "testData")
    public void test(URI uri) {
        err.printf("\nTesting with Version: %s, URI: %s\n", HTTP_2, uri.toASCIIString());
        Iterable<byte[]> iterable = EndlessDataChunks::new;
        HttpRequest.BodyPublisher testPub = HttpRequest.BodyPublishers.ofByteArrays(iterable);
        Throwable testThrowable = null;
        try {
            performRequest(testPub, uri);
        } catch (Exception e) {
            testThrowable = e.getCause();
        }
        assertNotNull(testThrowable, "Request should have completed exceptionally but testThrowable is null");
        assertEquals(testThrowable.getClass(), IOException.class, "Test should have closed with an IOException");
        testThrowable.printStackTrace();
    }

    static public class EndlessDataChunks implements Iterator<byte[]> {

        byte[] data = new byte[16];
        @Override
        public boolean hasNext() {
            return true;
        }
        @Override
        public byte[] next() {
            return data;
        }
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        http2TestServer = new Http2TestServer(false, 0);
        http2TestServer.setExchangeSupplier(ExpectContinueResetTestExchangeImpl::new);
        http2TestServer.addHandler(new GetHandler().toHttp2Handler(), "/warmup");
        http2TestServer.addHandler(new NoEndStreamOnPartialResponse(), "/partialResponse/codeNoError");
        http2TestServer.addHandler(new NoEndStreamOnPartialResponse(), "/partialResponse/codeError");
        http2TestServer.addHandler(new NoEndStreamOnFullResponse(), "/fullResponse/codeNoError");
        http2TestServer.addHandler(new NoEndStreamOnFullResponse(), "/fullResponse/codeError");

        warmup = URI.create("http://" + http2TestServer.serverAuthority() + "/warmup");
        partialResponseResetNoError = URI.create("http://" + http2TestServer.serverAuthority() + "/partialResponse/codeNoError");
        partialResponseResetError = URI.create("http://" + http2TestServer.serverAuthority() + "/partialResponse/codeError");
        fullResponseResetNoError = URI.create("http://" + http2TestServer.serverAuthority() + "/fullResponse/codeNoError");
        fullResponseResetError = URI.create("http://" + http2TestServer.serverAuthority() + "/fullResponse/codeError");
        http2TestServer.start();
    }

    @AfterTest
    public void teardown() {
        http2TestServer.stop();
    }

    private void performRequest(HttpRequest.BodyPublisher bodyPublisher, URI uri)
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
            // TODO: when test is stable and complete, see then if fromSubscriber makes our subscriber non null
            client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString()).get();
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

    static class NoEndStreamOnPartialResponse implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            err.println("Sending 100");
            exchange.sendResponseHeaders(100, 0);
            if (exchange instanceof ExpectContinueResetTestExchangeImpl testExchange) {
                err.println("Sending Reset");
                err.println(exchange.getRequestURI().getPath());
                switch (exchange.getRequestURI().getPath()) {
                    case "/partialResponse/codeNoError" -> testExchange.addResetToOutputQ(ResetFrame.NO_ERROR);
                    case "/partialResponse/codeError" -> testExchange.addResetToOutputQ(ResetFrame.PROTOCOL_ERROR);
                    default -> throw new TestException("Invalid Request Path");
                }
            } else {
                throw new TestException("Wrong Exchange type used");
            }
        }
    }

    static class NoEndStreamOnFullResponse implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            err.println("Sending 100");
            exchange.sendResponseHeaders(100, 0);
            err.println("Sending 200");
            exchange.sendResponseHeaders(200, 0);
            if (exchange instanceof ExpectContinueResetTestExchangeImpl testExchange) {
                err.println("Sending Reset");
                switch (exchange.getRequestURI().getPath()) {
                    case "/fullResponse/codeNoError" -> testExchange.addResetToOutputQ(ResetFrame.NO_ERROR);
                    case "/fullResponse/codeError" -> testExchange.addResetToOutputQ(ResetFrame.PROTOCOL_ERROR);
                    default -> throw new TestException("Invalid Request Path");
                }
            } else {
                throw new TestException("Wrong Exchange type used");
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
    }
}
