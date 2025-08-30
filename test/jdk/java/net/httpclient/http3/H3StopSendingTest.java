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
 * @summary Verifies that the client reacts correctly to the receipt of a STOP_SENDING frame.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run testng/othervm/timeout=40  -Djdk.internal.httpclient.debug=true -Djdk.httpclient.HttpClient.log=trace,errors,headers
 *                              H3StopSendingTest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.internal.net.http.http3.Http3Error;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static org.testng.Assert.*;

public class H3StopSendingTest {

    HttpTestServer h3TestServer;
    HttpRequest postRequestNoError, postRequestError;
    HttpRequest postRequestNoErrorWithData, postRequestErrorWithData;
    URI h3TestServerUriNoError, h3TestServerUriError;
    SSLContext sslContext;

    static final String TEST_ROOT_PATH = "/h3_stop_sending_test";
    static final String NO_ERROR_PATH = TEST_ROOT_PATH + "/no_error_path";
    static final String ERROR_PATH = TEST_ROOT_PATH + "/error_path";
    static final String WITH_RESPONSE_BODY_QUERY = "?withbody";

    static final PrintStream err = System.err;


    @Test
    public void test() throws ExecutionException, InterruptedException {
        HttpClient.Builder clientBuilder = HttpServerAdapters.createClientBuilderForH3()
                .proxy(NO_PROXY)
                .sslContext(sslContext);
        try (HttpClient client = clientBuilder.build()) {
            HttpResponse<String> resp = client.sendAsync(postRequestNoError, HttpResponse.BodyHandlers.ofString()).get();
            err.println(resp.headers());
            err.println(resp.body());
            err.println(resp.statusCode());
            assertEquals(resp.statusCode(), 200);
            resp = client.sendAsync(postRequestNoErrorWithData, HttpResponse.BodyHandlers.ofString()).get();
            err.println(resp.headers());
            err.println(resp.body());
            err.println(resp.statusCode());
            assertEquals(resp.statusCode(), 200);
            assertEquals(resp.body(), RESPONSE_MESSAGE.repeat(MESSAGE_REPEAT));
        }
    }

    @Test
    public void testError() {
        Throwable caught = null;
        HttpClient.Builder clientBuilder = HttpServerAdapters.createClientBuilderForH3()
                .proxy(NO_PROXY)
                .sslContext(sslContext);
        try (HttpClient client = clientBuilder.build()) {
            try {
                client.sendAsync(postRequestError, HttpResponse.BodyHandlers.ofString()).get();
            } catch (Throwable throwable) {
                caught = throwable;
            }
            assertRequestCancelled(caught);
            try {
                client.sendAsync(postRequestErrorWithData, HttpResponse.BodyHandlers.ofString()).get();
            } catch (Throwable throwable) {
                caught = throwable;
            }
            assertRequestCancelled(caught);
        }
    }

    private static void assertRequestCancelled(Throwable caught) {
        assertNotNull(caught);
        if (!caught.getMessage().contains(Long.toString(Http3Error.H3_REQUEST_CANCELLED.code()))
            && !caught.getMessage().contains(Http3Error.H3_REQUEST_CANCELLED.name())) {
            throw new AssertionError(caught.getMessage() + " does not contain " +
                    Http3Error.H3_REQUEST_CANCELLED.code() + " or " +
                    Http3Error.H3_REQUEST_CANCELLED.name(), caught);
        } else {
            System.out.println("Got expected exception: " + caught);
        }
    }

    @BeforeTest
    public void setup() throws IOException {

        sslContext  = new SimpleSSLContext().get();
        h3TestServer = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        h3TestServer.addHandler(new ServerRequestStopSendingHandler(), TEST_ROOT_PATH);
        h3TestServerUriError = URI.create("https://" + h3TestServer.serverAuthority() + ERROR_PATH);
        h3TestServerUriNoError = URI.create("https://" + h3TestServer.serverAuthority() + NO_ERROR_PATH);
        h3TestServer.start();

        Iterable<byte[]> iterable = EndlessDataChunks::new;
        HttpRequest.BodyPublisher testPub = HttpRequest.BodyPublishers.ofByteArrays(iterable);
        postRequestNoError = HttpRequest.newBuilder()
                .POST(testPub)
                .uri(h3TestServerUriNoError)
                .version(HttpClient.Version.HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                .build();
        postRequestError = HttpRequest.newBuilder()
                .POST(testPub)
                .uri(h3TestServerUriError)
                .version(HttpClient.Version.HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                .build();
        postRequestNoErrorWithData = HttpRequest.newBuilder()
                .POST(testPub)
                .uri(URI.create(h3TestServerUriNoError.toString() + WITH_RESPONSE_BODY_QUERY))
                .version(HttpClient.Version.HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                .build();
        postRequestErrorWithData = HttpRequest.newBuilder()
                .POST(testPub)
                .uri(URI.create(h3TestServerUriError.toString() + WITH_RESPONSE_BODY_QUERY))
                .version(HttpClient.Version.HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                .build();
    }

    @AfterTest
    public void afterTest() {
        h3TestServer.stop();
    }

    static final String RESPONSE_MESSAGE = "May the road rise up to meet you  ";
    static final String ERROR_MESSAGE    = "Forbidden: the data won't be sent ";
    static final String NO_BODY = "";

    static final int MESSAGE_REPEAT = 5;

    static class ServerRequestStopSendingHandler implements HttpTestHandler {

        @Override
        public void handle(HttpServerAdapters.HttpTestExchange t) throws IOException {
            String query = t.getRequestURI().getQuery();
            System.out.println("Query is: " + query);
            boolean withData = WITH_RESPONSE_BODY_QUERY.substring(1).equals(query);
            switch (t.getRequestURI().getPath()) {
                case NO_ERROR_PATH -> {
                    final String RESP = withData ? RESPONSE_MESSAGE : NO_BODY;
                    byte[] resp = RESP.getBytes(StandardCharsets.UTF_8);
                    System.err.println("Replying with 200 to " + t.getRequestURI().getPath()
                            + " with data " + resp.length * MESSAGE_REPEAT);
                    t.sendResponseHeaders(200, resp.length * MESSAGE_REPEAT);
                    t.requestStopSending(Http3Error.H3_NO_ERROR.code());
                    if (resp.length == 0) return;
                    try (var os = t.getResponseBody()) {
                        for (int i = 0; i<MESSAGE_REPEAT; i++) {
                            os.write(resp);
                            os.flush();
                            sleep(10);
                        }
                    }
                }
                case ERROR_PATH -> {
                    final String RESP = withData ? ERROR_MESSAGE : NO_BODY;
                    byte[] resp = RESP.getBytes(StandardCharsets.UTF_8);
                    System.err.println("Replying with 403 to " + t.getRequestURI().getPath()
                            + " with data " + resp.length * MESSAGE_REPEAT);
                    if (resp.length == 0) {
                        t.requestStopSending(Http3Error.H3_EXCESSIVE_LOAD.code());
                        sleep(100);
                        t.resetStream(Http3Error.H3_REQUEST_CANCELLED.code());
                    } else {
                        t.sendResponseHeaders(403, resp.length * MESSAGE_REPEAT);
                        t.requestStopSending(Http3Error.H3_EXCESSIVE_LOAD.code());
                        try (var os = t.getResponseBody()) {
                            for (int i = 0; i < MESSAGE_REPEAT; i++) {
                                os.write(resp);
                                os.flush();
                                sleep(10);
                                if (i == MESSAGE_REPEAT - 1) {
                                    t.resetStream(Http3Error.H3_REQUEST_CANCELLED.code());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
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
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return data;
        }
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
