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
 * @summary Tests Http3 expect continue
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @compile ../ReferenceTracker.java
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run testng/othervm -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.HttpClient.log=errors,requests,headers
 *                     Http3ExpectContinueTest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.httpclient.test.lib.quic.QuicServer;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.TestException;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpRequest;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpOption;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.net.http.HttpClient.Version.HTTP_3;
import static org.testng.Assert.*;

public class Http3ExpectContinueTest implements HttpServerAdapters {

    ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;

    Http3TestServer http3TestServer;

    URI h3postUri, h3forcePostUri, h3hangUri;

    static PrintStream err = new PrintStream(System.err);
    static PrintStream out = new PrintStream(System.out);
    static final String EXPECTATION_FAILED_417 = "417 Expectation Failed";
    static final String CONTINUE_100 = "100 Continue";
    static final String RESPONSE_BODY= "Verify response received";
    static final String BODY = "Post body";
    private SSLContext sslContext;

    @DataProvider(name = "uris")
    public Object[][] urisData() {
        return new Object[][]{
                // URI, Expected Status Code, Will finish with Exception
                { h3postUri, 200, false },
                { h3forcePostUri, 200, false },
                { h3hangUri, 417, false },
        };
    }

    @Test(dataProvider = "uris")
    public void test(URI uri, int expectedStatusCode, boolean exceptionally)
            throws CancellationException, InterruptedException, ExecutionException, IOException {

        err.printf("\nTesting URI: %s, exceptionally: %b\n", uri, exceptionally);
        out.printf("\nTesting URI: %s, exceptionally: %b\n", uri, exceptionally);
        HttpClient client = newClientBuilderForH3().
                proxy(Builder.NO_PROXY)
                .version(HTTP_3).sslContext(sslContext)
                .build();
        AssertionError failed = null;
        TRACKER.track(client);
        try {
            HttpResponse<String> resp = null;
            Throwable testThrowable = null;

            HttpRequest postRequest = HttpRequest.newBuilder(uri)
                    .version(HTTP_3)
                    .setOption(HttpOption.H3_DISCOVERY,
                            Http3DiscoveryMode.HTTP_3_URI_ONLY)
                    .POST(HttpRequest.BodyPublishers.ofString(BODY))
                    .expectContinue(true)
                    .build();

            err.printf("Sending request: %s%n", postRequest);
            CompletableFuture<HttpResponse<String>> cf = client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString());
            try {
                resp = cf.get();
            } catch (Exception e) {
                testThrowable = e.getCause();
            }
            verifyRequest(uri.getPath(), expectedStatusCode, resp, exceptionally, testThrowable);
        } catch (Throwable x) {
            failed = new AssertionError("Unexpected exception:" + x, x);
        } finally {
            client.shutdown();
            if (!client.awaitTermination(Duration.ofMillis(1000))) {
                var tracker = TRACKER.getTracker(client);
                client = null;
                var error = TRACKER.check(tracker, 2000);
                if (error != null || failed != null) {
                    var ex = failed == null ? error : failed;
                    err.printf("FAILED URI: %s, exceptionally: %b, error: %s\n", uri, exceptionally, ex);
                    out.printf("FAILED URI: %s, exceptionally: %b, error: %s\n", uri, exceptionally, ex);
                }
                if (error != null) {
                    if (failed != null) {
                        failed.addSuppressed(error);
                        throw failed;
                    }
                    throw error;
                }
            }
        }
        if (failed != null) {
            err.printf("FAILED URI: %s, exceptionally: %b, error: %s\n", uri, exceptionally, failed);
            out.printf("FAILED URI: %s, exceptionally: %b, error: %s\n", uri, exceptionally, failed);
            throw failed;
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null) {
            throw new AssertionError("Unexpected null sslContext");
        }
        final QuicServer quicServer = Http3TestServer.quicServerBuilder()
                .sslContext(sslContext)
                .build();
        http3TestServer = new Http3TestServer(quicServer);
        http3TestServer.addHandler("/http3/post", new PostHandler().toHttp2Handler());
        http3TestServer.addHandler("/http3/forcePost", new ForcePostHandler().toHttp2Handler());
        http3TestServer.addHandler("/http3/hang", new PostHandlerCantContinue().toHttp2Handler());

        h3postUri = new URI("https://" + http3TestServer.serverAuthority() + "/http3/post");
        h3forcePostUri = URI.create("https://" + http3TestServer.serverAuthority() + "/http3/forcePost");
        h3hangUri = URI.create("https://" + http3TestServer.serverAuthority() + "/http3/hang");
        out.printf("HTTP/3 server listening at: %s", http3TestServer.getAddress());

        http3TestServer.start();
    }

    @AfterTest
    public void teardown() throws IOException {
        var error = TRACKER.check(500);
        if (error != null) throw error;
        http3TestServer.stop();
    }

    static class PostHandler implements HttpTestHandler {

        @java.lang.Override
        public void handle(HttpTestExchange exchange) throws IOException {
            System.out.printf("Server version %s and exchange version %s", exchange.getServerVersion(), exchange.getExchangeVersion());

            if(exchange.getExchangeVersion().equals(HTTP_3)){
                // send 100 header
                byte[] ContinueResponseBytes = CONTINUE_100.getBytes();
                err.println("Server send 100 (length="+ContinueResponseBytes.length+")");
                exchange.sendResponseHeaders(100, ContinueResponseBytes.length);
            }

            // Read body from client and acknowledge with 200
            try (InputStream is = exchange.getRequestBody()) {
                err.println("Server reading body");
                var bytes = is.readAllBytes();
                String responseBody = new String(bytes);
                assert responseBody.equals(BODY);
                byte[] responseBodyBytes = RESPONSE_BODY.getBytes();
                err.println("Server send 200 (length="+responseBodyBytes.length+")");
                exchange.sendResponseHeaders(200, responseBodyBytes.length);
                exchange.getResponseBody().write(responseBodyBytes);
            }
        }
    }

    static class ForcePostHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody()) {
                err.println("Server reading body inside the force Post");
                is.readAllBytes();
                err.println("Server send 200 (length=0) in the force post");
                exchange.sendResponseHeaders(200, 0);
            }
        }
    }

    static class PostHandlerCantContinue implements HttpTestHandler {
        @java.lang.Override
        public void handle(HttpTestExchange exchange) throws IOException {
            //Send 417 Headers, tell client to not send body
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] bytes = EXPECTATION_FAILED_417.getBytes();
                err.println("Server send 417 (length="+bytes.length+")");
                exchange.sendResponseHeaders(417, bytes.length);
                err.println("Server sending Response Body");
                os.write(bytes);
            }
        }
    }

    private void verifyRequest(String path, int expectedStatusCode, HttpResponse<String> resp, boolean exceptionally, Throwable testThrowable) {
        if (!exceptionally) {
            err.printf("Response code %s received for path %s %n", resp.statusCode(), path);
        }
        if (exceptionally && testThrowable != null) {
            err.println("Finished exceptionally Test throwable: " + testThrowable);
            assertEquals(IOException.class, testThrowable.getClass());
        } else if (exceptionally) {
            throw new TestException("Expected case to finish with an IOException but testException is null");
        } else if (resp != null) {
            assertEquals(resp.statusCode(), expectedStatusCode);
            err.println("Request completed successfully for path " + path);
            err.println("Response Headers: " + resp.headers());
            err.println("Response Status Code: " + resp.statusCode());
        }
    }
}
