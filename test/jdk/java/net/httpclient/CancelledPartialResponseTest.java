/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verifies that the client reacts correctly to receiving RST_STREAM or StopSendingFrame at various stages of
 *          a Partial/Expect-continue type Response for HTTP/2 and HTTP/3.
 * @bug 8309118
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run testng/othervm/timeout=40  -Djdk.internal.httpclient.debug=false -Djdk.httpclient.HttpClient.log=trace,errors,headers
 *                              CancelledPartialResponseTest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.httpclient.test.lib.http2.BodyOutputStream;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2TestExchangeImpl;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestServerConnection;

import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.frame.ResetFrame;
import jdk.internal.net.http.http3.Http3Error;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.TestException;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;

import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;

public class CancelledPartialResponseTest {

    Http2TestServer http2TestServer;

    HttpTestServer http3TestServer;

    // "NoError" urls complete with an exception. "NoError" or "Error" here refers to the error code in the RST_STREAM frame
    // and not the outcome of the test.
    URI warmup, h2PartialResponseResetNoError, h2PartialResponseResetError, h2FullResponseResetNoError, h2FullResponseResetError;
    URI h3PartialResponseStopSending, h3FullResponseStopSending;

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();

    static PrintStream err = new PrintStream(System.err);
    static PrintStream out = System.out;

    // TODO: Investigate further if checking against HTTP/3 Full Response is necessary
    @DataProvider(name = "testData")
    public Object[][] testData() {
        return new Object[][] {
                { HTTP_2, h2PartialResponseResetNoError },
                { HTTP_2, h2PartialResponseResetError },  // Checks RST_STREAM is processed if client sees no END_STREAM
                { HTTP_2, h2FullResponseResetNoError },
                { HTTP_2, h2FullResponseResetError },
                { HTTP_3, h3PartialResponseStopSending }, // All StopSending frames received by client throw exception regardless of code
                { HTTP_3, h3FullResponseStopSending }
        };
    }


    @Test(dataProvider = "testData")
    public void test(Version version, URI uri) {
        out.printf("\nTesting with Version: %s, URI: %s\n", version, uri.toASCIIString());
        err.printf("\nTesting with Version: %s, URI: %s\n", version, uri.toASCIIString());
        Iterable<byte[]> iterable = EndlessDataChunks::new;
        HttpRequest.BodyPublisher testPub = HttpRequest.BodyPublishers.ofByteArrays(iterable);
        Exception expectedException = null;
        try {
            performRequest(version, testPub, uri);
            throw new AssertionError("Expected exception not raised for " + uri);
        } catch (Exception e) {
            expectedException = e;
        }
        Throwable testThrowable = expectedException.getCause();
        if (testThrowable == null) {
            throw new AssertionError("Unexpected null cause for " + expectedException,
                    expectedException);
        }
        if (!(testThrowable instanceof IOException)) {
            throw new AssertionError(
                    "Test should have closed with an IOException, got: " + testThrowable,
                    testThrowable);
        }
        if (version == HTTP_3) {
            if (testThrowable.getMessage().contains(Http3Error.H3_EXCESSIVE_LOAD.name())) {
                System.out.println("Got expected message: " + testThrowable.getMessage());
            } else {
                throw new AssertionError("Expected " + Http3Error.H3_EXCESSIVE_LOAD.name()
                        + ", got " + testThrowable, testThrowable);
            }
        }
    }

    static public class EndlessDataChunks implements Iterator<byte[]> {

        byte[] data = new byte[32];
        boolean hasNext = true;
        @Override
        public boolean hasNext() {
            return hasNext;
        }
        @Override
        public byte[] next() {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            hasNext = false;
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
        http3TestServer = HttpTestServer.create(Http3DiscoveryMode.HTTP_3_URI_ONLY, sslContext);

        http2TestServer.setExchangeSupplier(ExpectContinueResetTestExchangeImpl::new);
        http2TestServer.addHandler(new GetHandler().toHttp2Handler(), "/warmup");
        http2TestServer.addHandler(new PartialResponseResetStreamH2(), "/partialResponse/codeNoError");
        http2TestServer.addHandler(new PartialResponseResetStreamH2(), "/partialResponse/codeError");
        http2TestServer.addHandler(new FullResponseResetStreamH2(), "/fullResponse/codeNoError");
        http2TestServer.addHandler(new FullResponseResetStreamH2(), "/fullResponse/codeError");
        http3TestServer.addHandler(new PartialResponseStopSendingH3(), "/partialResponse/codeNoError");
        http3TestServer.addHandler(new FullResponseStopSendingH3(), "/fullResponse/codeNoError");

        warmup = URI.create("http://" + http2TestServer.serverAuthority() + "/warmup");
        h2PartialResponseResetNoError = URI.create("http://" + http2TestServer.serverAuthority() + "/partialResponse/codeNoError");
        h2PartialResponseResetError = URI.create("http://" + http2TestServer.serverAuthority() + "/partialResponse/codeError");
        h2FullResponseResetNoError = URI.create("http://" + http2TestServer.serverAuthority() + "/fullResponse/codeNoError");
        h2FullResponseResetError = URI.create("http://" + http2TestServer.serverAuthority() + "/fullResponse/codeError");
        h3PartialResponseStopSending = URI.create("https://" + http3TestServer.serverAuthority() + "/partialResponse/codeNoError");
        h3FullResponseStopSending = URI.create("https://" + http3TestServer.serverAuthority() + "/fullResponse/codeNoError");

        http2TestServer.start();
        http3TestServer.start();
    }

    @AfterTest
    public void teardown() {
        http2TestServer.stop();
    }

    private void performRequest(Version version, HttpRequest.BodyPublisher bodyPublisher, URI uri)
            throws IOException, InterruptedException, ExecutionException {

        HttpClient.Builder builder = HttpServerAdapters.createClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .version(version)
                .sslContext(sslContext);
        Http3DiscoveryMode requestConfig = null;
        if (version == HTTP_3)
            requestConfig = Http3DiscoveryMode.HTTP_3_URI_ONLY;

        try (HttpClient client = builder.build()) {
            err.printf("Performing warmup request to %s", warmup);
            if (version == HTTP_2)
                client.send(HttpRequest.newBuilder(warmup).GET().version(HTTP_2).build(),
                        HttpResponse.BodyHandlers.discarding());

            HttpRequest postRequest = HttpRequest.newBuilder(uri)
                    .version(version)
                    .POST(bodyPublisher)
                    .setOption(H3_DISCOVERY, requestConfig)
                    .expectContinue(true)
                    .build();
            err.printf("Sending request (%s): %s%n", version, postRequest);
            client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString()).get();
        }
    }

    static class GetHandler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] bytes = "Response Body".getBytes(UTF_8);
                err.printf("Server sending 200  (length=%s)", bytes.length);
                exchange.sendResponseHeaders(200, bytes.length);
                err.println("Server sending Response Body");
                os.write(bytes);
            }
        }
    }

    static class PartialResponseResetStreamH2 implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            err.println("Sending 100");
            exchange.sendResponseHeaders(100, -1);
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

    static class FullResponseResetStreamH2 implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            err.println("Sending 100");
            exchange.sendResponseHeaders(100, -1);

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

    static class PartialResponseStopSendingH3 implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            err.println("Sending 100");
            exchange.sendResponseHeaders(100, 0);
            // sending StopSending(NO_ERROR) before or after sending 100 with no data
            // should not fail.
            System.err.println("Sending StopSendingFrame");
            exchange.requestStopSending(Http3Error.H3_REQUEST_REJECTED.code());
            // Not resetting the stream would cause the client to wait forever
            exchange.resetStream(Http3Error.H3_EXCESSIVE_LOAD.code());
        }
    }

    static class FullResponseStopSendingH3 implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            err.println("Sending 100");
            exchange.sendResponseHeaders(100, 0);
            err.println("Sending 200");

            // sending StopSending before or after sending 200 with no data
            // should not fail.
            err.println("Sending StopSendingFrame");
            exchange.requestStopSending(Http3Error.H3_REQUEST_REJECTED.code());
            exchange.sendResponseHeaders(200, 10);
            exchange.resetStream(Http3Error.H3_EXCESSIVE_LOAD.code());
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
