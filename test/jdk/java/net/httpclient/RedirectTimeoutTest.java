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
 * @bug 8304701
 * @summary Verifies that for a redirected request, the given HttpClient
 *          will clear and start a new response timer instead of throwing
 *          an HttpTimeoutException during the redirected request.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=errors,trace -Djdk.internal.httpclient.debug=false RedirectTimeoutTest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestResponseHeaders;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import org.testng.TestException;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static java.net.http.HttpClient.Redirect.ALWAYS;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static jdk.test.lib.Utils.adjustTimeout;

public class RedirectTimeoutTest {

    static HttpTestServer h1TestServer, h2TestServer;
    static URI h1Uri, h1RedirectUri, h2Uri, h2RedirectUri, h2WarmupUri, testRedirectURI;
    private static final long TIMEOUT_MILLIS =  3000L; // 3s
    private static final long SLEEP_TIME = 1500L; // 1.5s
    public static final int ITERATIONS = 4;
    private static final PrintStream out = System.out;

    @BeforeTest
    public void setup() throws IOException {
        h1TestServer = HttpTestServer.create(HTTP_1_1);
        h2TestServer = HttpTestServer.create(HTTP_2);
        h1Uri = URI.create("http://" + h1TestServer.serverAuthority() + "/h1_test");
        h1RedirectUri = URI.create("http://" + h1TestServer.serverAuthority() + "/h1_redirect");
        h2Uri = URI.create("http://" + h2TestServer.serverAuthority() + "/h2_test");
        h2RedirectUri = URI.create("http://" + h2TestServer.serverAuthority() + "/h2_redirect");
        h2WarmupUri = URI.create("http://" + h2TestServer.serverAuthority() + "/h2_warmup");
        h1TestServer.addHandler(new GetHandler(), "/h1_test");
        h1TestServer.addHandler(new RedirectHandler(), "/h1_redirect");
        h2TestServer.addHandler(new GetHandler(), "/h2_test");
        h2TestServer.addHandler(new RedirectHandler(), "/h2_redirect");
        h2TestServer.addHandler(new Http2Warmup(), "/h2_warmup");
        h1TestServer.start();
        h2TestServer.start();
    }

    @AfterTest
    public void teardown() {
        h1TestServer.stop();
        h2TestServer.stop();
    }

    @DataProvider(name = "testData")
    public Object[][] testData() {
        return new Object[][] {
                { HTTP_1_1, h1Uri, h1RedirectUri },
                { HTTP_2, h2Uri, h2RedirectUri }
        };
    }

    @Test(dataProvider = "testData")
    public void test(Version version, URI uri, URI redirectURI) throws InterruptedException {
        out.println("Testing for " + version);
        testRedirectURI = redirectURI;
        HttpClient.Builder clientBuilder = HttpClient.newBuilder().followRedirects(ALWAYS);
        HttpRequest request = HttpRequest.newBuilder().uri(uri)
                .GET()
                .version(version)
                .timeout(Duration.ofMillis(adjustTimeout(TIMEOUT_MILLIS)))
                .build();

        try (HttpClient client = clientBuilder.build()) {
            if (version.equals(HTTP_2))
                client.send(HttpRequest.newBuilder(h2WarmupUri).HEAD().build(), HttpResponse.BodyHandlers.discarding());
            /*
                With TIMEOUT_MILLIS set to 1500ms and the server's RedirectHandler sleeping for 750ms before responding
                to each request, 4 iterations will take a guaranteed minimum time of 3000ms which will ensure that any
                uncancelled/uncleared timers will fire within the test window.
             */
            for (int i = 0; i < ITERATIONS; i++) {
                out.println(Instant.now() + ": Client: Sending request #" + (i + 1));
                client.send(request, HttpResponse.BodyHandlers.ofString());
                out.println("Request complete");
            }
        } catch (IOException e) {
            if (e.getClass() == HttpTimeoutException.class) {
                e.printStackTrace(System.out);
                throw new TestException("Timeout from original HttpRequest expired on redirect when it should have been cancelled.");
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    public static class Http2Warmup implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            t.sendResponseHeaders(200, 0);
        }
    }

    public static class GetHandler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            out.println(Instant.now() + ": Server: Get Handler Called");
            HttpTestResponseHeaders responseHeaders = exchange.getResponseHeaders();
            responseHeaders.addHeader("Location", testRedirectURI.toString());
            exchange.sendResponseHeaders(302, 0);
        }
    }

    public static class RedirectHandler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            out.println(Instant.now() + ": Server: Redirect Handler Called");
            byte[] data = "Test".getBytes(StandardCharsets.UTF_8);
            try {
                Thread.sleep(adjustTimeout(SLEEP_TIME));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
    }
}