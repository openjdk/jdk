/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Checks HTTP versions when interacting with an HTTP/2 server
 * @bug 8242044
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer jdk.test.lib.net.SimpleSSLContext
 *        jdk.test.lib.Platform
 * @run junit/othervm HttpVersionsTest
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.test.lib.net.SimpleSSLContext;
import static java.lang.String.format;
import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpResponse.BodyHandlers.ofString;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class HttpVersionsTest {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    static Http2TestServer http2TestServer;
    static Http2TestServer https2TestServer;
    static String http2URI;
    static String https2URI;

    static final int ITERATIONS = 3;
    static final String[] BODY = new String[] {
            "I'd like another drink I think",
            "Another drink to make me pink",
            "I think I'll drink until I stink",
            "I'll drink until I cannot blink"
    };
    static int nextBodyId;

    public static Object[][] scenarios() {
        return new Object[][] {
                { http2URI,  true  },
                { https2URI, true  },
                { http2URI,  false },
                { https2URI, false },
        };
    }

    /** Checks that an HTTP/2 request receives an HTTP/2 response. */
    @ParameterizedTest
    @MethodSource("scenarios")
    void testHttp2Get(String uri, boolean sameClient) throws Exception {
        out.printf("\n--- testHttp2Get uri:%s, sameClient:%s%n", uri, sameClient);
        HttpClient client = null;
        for (int i=0; i<ITERATIONS; i++) {
            if (!sameClient || client == null)
                client = HttpClient.newBuilder()
                                   .sslContext(sslContext)
                                   .version(HTTP_2)
                                   .build();

            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .build();
            HttpResponse<String> response = client.send(request, ofString());
            out.println("Got response: " + response);
            out.println("Got body: " + response.body());

            assertEquals(200, response.statusCode());
            assertEquals(HTTP_2, response.version());
            assertEquals("", response.body());
            if (uri.startsWith("https"))
                assertTrue(response.sslSession().isPresent());
        }
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void testHttp2Post(String uri, boolean sameClient) throws Exception {
        out.printf("\n--- testHttp2Post uri:%s, sameClient:%s%n", uri, sameClient);
        HttpClient client = null;
        for (int i=0; i<ITERATIONS; i++) {
            if (!sameClient || client == null)
                client = HttpClient.newBuilder()
                                   .sslContext(sslContext)
                                   .version(HTTP_2)
                                   .build();

            String msg = BODY[nextBodyId++%4];
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .POST(BodyPublishers.ofString(msg))
                    .build();
            HttpResponse<String> response = client.send(request, ofString());
            out.println("Got response: " + response);
            out.println("Got body: " + response.body());

            assertEquals(200, response.statusCode());
            assertEquals(HTTP_2, response.version());
            assertEquals(msg, response.body());
            if (uri.startsWith("https"))
                assertTrue(response.sslSession().isPresent());
        }
    }

    /** Checks that an HTTP/1.1 request receives an HTTP/1.1 response, from the HTTP/2 server. */
    @ParameterizedTest
    @MethodSource("scenarios")
    void testHttp1dot1Get(String uri, boolean sameClient) throws Exception {
        out.printf("\n--- testHttp1dot1Get uri:%s, sameClient:%s%n", uri, sameClient);
        HttpClient client = null;
        for (int i=0; i<ITERATIONS; i++) {
            if (!sameClient || client == null)
                client = HttpClient.newBuilder()
                                   .sslContext(sslContext)
                                   .version(HTTP_1_1)
                                   .build();

            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .build();
            HttpResponse<String> response = client.send(request, ofString());
            out.println("Got response: " + response);
            out.println("Got body: " + response.body());
            response.headers().firstValue("X-Received-Body").ifPresent(s -> out.println("X-Received-Body:" + s));

            assertEquals(200, response.statusCode());
            assertEquals(HTTP_1_1, response.version());
            assertEquals("", response.body());
            assertEquals("HTTP/1.1 request received by HTTP/2 server", response.headers().firstValue("X-Magic").get());
            assertEquals("", response.headers().firstValue("X-Received-Body").get());
            if (uri.startsWith("https"))
                assertTrue(response.sslSession().isPresent());
        }
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void testHttp1dot1Post(String uri, boolean sameClient) throws Exception {
        out.printf("\n--- testHttp1dot1Post uri:%s, sameClient:%s%n", uri, sameClient);
        HttpClient client = null;
        for (int i=0; i<ITERATIONS; i++) {
            if (!sameClient || client == null)
                client = HttpClient.newBuilder()
                                   .sslContext(sslContext)
                                   .version(HTTP_1_1)
                                   .build();
            String msg = BODY[nextBodyId++%4];
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .POST(BodyPublishers.ofString(msg))
                    .build();
            HttpResponse<String> response = client.send(request, ofString());
            out.println("Got response: " + response);
            out.println("Got body: " + response.body());
            response.headers().firstValue("X-Received-Body").ifPresent(s -> out.println("X-Received-Body:" + s));

            assertEquals(200, response.statusCode());
            assertEquals(HTTP_1_1, response.version());
            assertEquals("", response.body());
            assertEquals("HTTP/1.1 request received by HTTP/2 server", response.headers().firstValue("X-Magic").get());
            assertEquals(msg, response.headers().firstValue("X-Received-Body").get());
            if (uri.startsWith("https"))
                assertTrue(response.sslSession().isPresent());
        }
    }

    // -- Infrastructure

    static final ExecutorService executor = Executors.newCachedThreadPool();

    @BeforeAll
    public static void setup() throws Exception {
        http2TestServer =  new Http2TestServer("localhost", false, 0, executor, 50, null, null, true);
        http2TestServer.addHandler(new Http2VerEchoHandler(), "/http2/vts");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/vts";

        https2TestServer =  new Http2TestServer("localhost", true, 0, executor, 50, null, sslContext, true);
        https2TestServer.addHandler(new Http2VerEchoHandler(), "/https2/vts");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/vts";

        http2TestServer.start();
        https2TestServer.start();
    }

    @AfterAll
    public static void teardown() throws Exception {
        http2TestServer.stop();
        https2TestServer.stop();
        executor.shutdown();
    }

    static class Http2VerEchoHandler implements Http2Handler {
        @Override
        public void handle(Http2TestExchange t) throws IOException {
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                byte[] bytes = is.readAllBytes();
                t.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }
    }
}
