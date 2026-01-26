/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify that non-US-ASCII chars are replaced with a sequence of
 *          escaped octets that represent that char in the UTF-8 character set.
 * @bug 8201238
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer jdk.test.lib.net.SimpleSSLContext
 * @compile -encoding utf-8 NonAsciiCharsInURI.java
 * @run testng/othervm
 *       -Djdk.httpclient.HttpClient.log=requests,headers,errors,quic
 *       NonAsciiCharsInURI
 */

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.lang.System.err;
import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.testng.Assert.assertEquals;

public class NonAsciiCharsInURI implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    HttpTestServer httpTestServer;         // HTTP/1.1    [ 5 servers ]
    HttpTestServer httpsTestServer;        // HTTPS/1.1
    HttpTestServer http2TestServer;        // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;       // HTTP/2 ( h2  )
    HttpTestServer http3TestServer;        // HTTP/3 ( h3  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;
    String http3URI;
    String http3URI_head;

    private volatile HttpClient sharedClient;

    // € = '\u20AC' => 0xE20x820xAC
    static final String[][] pathsAndQueryStrings = new String[][] {
               // partial-path
            {  "/001/plain"                                                            },
            {  "/002/plain?plainQuery"                                                 },
            {  "/003/withEuroSymbol/€"                                                 },
            {  "/004/withEuroSymbol/€?euroSymbol=€"                                    },
            {  "/005/wiki/エリザベス1世_(イングランド女王)"                                },
            {  "/006/x?url=https://ja.wikipedia.org/wiki/エリザベス1世_(イングランド女王)" },
    };

    @DataProvider(name = "variants")
    public Object[][] variants() {
        List<Object[]> list = new ArrayList<>();

        for (boolean sameClient : new boolean[] { false, true }) {
            Arrays.asList(pathsAndQueryStrings).stream()
                    .map(e -> new Object[] {httpURI + e[0], sameClient})
                    .forEach(list::add);
            Arrays.asList(pathsAndQueryStrings).stream()
                    .map(e -> new Object[] {httpsURI + e[0], sameClient})
                    .forEach(list::add);
            Arrays.asList(pathsAndQueryStrings).stream()
                    .map(e -> new Object[] {http2URI + e[0], sameClient})
                    .forEach(list::add);
            Arrays.asList(pathsAndQueryStrings).stream()
                    .map(e -> new Object[] {https2URI + e[0], sameClient})
                    .forEach(list::add);
            Arrays.asList(pathsAndQueryStrings).stream()
                    .map(e -> new Object[] {http3URI + e[0], sameClient})
                    .forEach(list::add);
        }
        return list.stream().toArray(Object[][]::new);
    }

    static final long start = System.nanoTime();
    public static String now() {
        long now = System.nanoTime() - start;
        long secs = now / 1000_000_000;
        long mill = (now % 1000_000_000) / 1000_000;
        long nan = now % 1000_000;
        return String.format("[%d s, %d ms, %d ns] ", secs, mill, nan);
    }

    static Version version(String uri) {
        if (uri.contains("/http1/") || uri.contains("/https1/"))
            return HTTP_1_1;
        if (uri.contains("/http2/") || uri.contains("/https2/"))
            return HTTP_2;
        if (uri.contains("/http3/"))
            return HTTP_3;
        return null;
    }

    HttpRequest.Builder newRequestBuilder(String uri) {
        var builder = HttpRequest.newBuilder(URI.create(uri));
        if (version(uri) == HTTP_3) {
            builder.version(HTTP_3);
            builder.setOption(H3_DISCOVERY, http3TestServer.h3DiscoveryConfig());
        }
        return builder;
    }

    HttpResponse<String> headRequest(HttpClient client)
            throws IOException, InterruptedException
    {
        out.println("\n" + now() + "--- Sending HEAD request ----\n");
        err.println("\n" + now() + "--- Sending HEAD request ----\n");

        var request = newRequestBuilder(http3URI_head)
                .HEAD().version(HTTP_2).build();
        var response = client.send(request, BodyHandlers.ofString());
        assertEquals(response.statusCode(), 200);
        assertEquals(response.version(), HTTP_2);
        out.println("\n" + now() + "--- HEAD request succeeded ----\n");
        err.println("\n" + now() + "--- HEAD request succeeded ----\n");
        return response;
    }

    private HttpClient makeNewClient() {
        return newClientBuilderForH3()
                .proxy(NO_PROXY)
                .sslContext(sslContext)
                .build();
    }

    HttpClient newHttpClient(boolean share) {
        if (!share) return makeNewClient();
        HttpClient shared = sharedClient;
        if (shared != null) return shared;
        synchronized (this) {
            shared = sharedClient;
            if (shared == null) {
                shared = sharedClient = makeNewClient();
            }
            return shared;
        }
    }

    record CloseableClient(HttpClient client, boolean shared)
            implements Closeable {
        public void close() {
            if (shared) return;
            client.close();
        }
    }

    static final int ITERATION_COUNT = 3; // checks upgrade and re-use

    @Test(dataProvider = "variants")
    void test(String uriString, boolean sameClient) throws Exception {
        out.println("\n--- Starting ");
        // The single-argument factory requires any illegal characters in its
        // argument to be quoted and preserves any escaped octets and other
        // characters that are present.
        URI uri = URI.create(uriString);

        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uriString) == HTTP_3) {
                    headRequest(client);
                }
            }


            try (var cl = new CloseableClient(client, sameClient)) {
                HttpRequest request = newRequestBuilder(uriString).build();
                HttpResponse<String> resp = client.send(request, BodyHandlers.ofString());

                out.println("Got response: " + resp);
                out.println("Got body: " + resp.body());
                assertEquals(resp.statusCode(), 200,
                        "Expected 200, got:" + resp.statusCode());

                // the response body should contain the toASCIIString
                // representation of the URI
                String expectedURIString = uri.toASCIIString();
                if (!expectedURIString.contains(resp.body())) {
                    err.println("Test failed: " + resp);
                    throw new AssertionError(expectedURIString +
                            " does not contain '" + resp.body() + "'");
                } else {
                    out.println("Found expected " + resp.body() + " in " + expectedURIString);
                }
                assertEquals(resp.version(), version(uriString));
            }
        }
    }

    @Test(dataProvider = "variants")
    void testAsync(String uriString, boolean sameClient) throws Exception {
        out.println("\n--- Starting ");
        URI uri = URI.create(uriString);

        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uriString) == HTTP_3) {
                    headRequest(client);
                }
            }

            try (var cl = new CloseableClient(client, sameClient)) {
                HttpRequest request = newRequestBuilder(uriString).build();

                client.sendAsync(request, BodyHandlers.ofString())
                        .thenApply(response -> {
                            out.println("Got response: " + response);
                            out.println("Got body: " + response.body());
                            assertEquals(response.statusCode(), 200);
                            assertEquals(response.version(), version(uriString));
                            return response.body();
                        })
                        .thenAccept(body -> {
                            // the response body should contain the toASCIIString
                            // representation of the URI
                            String expectedURIString = uri.toASCIIString();
                            if (!expectedURIString.contains(body)) {
                                err.println("Test failed: " + body);
                                throw new AssertionError(expectedURIString +
                                        " does not contain '" + body + "'");
                            } else {
                                out.println("Found expected " + body + " in "
                                        + expectedURIString);
                            }
                        })
                        .join();
            }
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        out.println(now() + "begin setup");

        HttpTestHandler handler = new HttpUriStringHandler();
        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(handler, "/http1/get");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1/get";

        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(handler, "/https1/get");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1/get";

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(handler, "/http2/get");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/get";

        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(handler, "/https2/get");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/get";

        http3TestServer = HttpTestServer.create(HTTP_3, sslContext);
        http3TestServer.addHandler(new HttpUriStringHandler(), "/http3/get");
        http3TestServer.addHandler(new HttpHeadOrGetHandler(), "/http3/head");
        http3URI = "https://" + http3TestServer.serverAuthority() + "/http3/get";
        http3URI_head = "https://" + http3TestServer.serverAuthority() + "/http3/head/x";

        err.println(now() + "Starting servers");
        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        http3TestServer.start();

        out.println("HTTP/1.1 server (http) listening at: " + httpTestServer.serverAuthority());
        out.println("HTTP/1.1 server (TLS)  listening at: " + httpsTestServer.serverAuthority());
        out.println("HTTP/2   server (h2c)  listening at: " + http2TestServer.serverAuthority());
        out.println("HTTP/2   server (h2)   listening at: " + https2TestServer.serverAuthority());
        out.println("HTTP/3   server (h2)   listening at: " + http3TestServer.serverAuthority());
        out.println(" + alt endpoint (h3)   listening at: " + http3TestServer.getH3AltService()
                .map(Http3TestServer::getAddress));

        headRequest(newHttpClient(true));

        out.println(now() + "setup done");
        err.println(now() + "setup done");
    }

    @AfterTest
    public void teardown() throws Exception {
        sharedClient.close();
        httpTestServer.stop();
        httpsTestServer.stop();
        http2TestServer.stop();
        https2TestServer.stop();
        http3TestServer.stop();
    }

    /** A handler that returns, as its body, the exact received request URI. */
    static class HttpUriStringHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            String uri = t.getRequestURI().toString();
            out.println("HttpUriStringHandler received, uri: " + uri);
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                is.readAllBytes();
                byte[] bytes = uri.getBytes(US_ASCII);
                t.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }
    }
}
