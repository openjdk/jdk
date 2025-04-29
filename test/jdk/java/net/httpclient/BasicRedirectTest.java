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
 * @summary Basic test for redirect and redirect policies
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm
 *       -Djdk.httpclient.HttpClient.log=trace,headers,requests
 *       -Djdk.internal.httpclient.debug=true
 *       BasicRedirectTest
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;


public class BasicRedirectTest implements HttpServerAdapters {

    SSLContext sslContext;
    HttpTestServer httpTestServer;        // HTTP/1.1    [ 4 servers ]
    HttpTestServer httpsTestServer;       // HTTPS/1.1
    HttpTestServer http2TestServer;       // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;      // HTTP/2 ( h2  )
    HttpTestServer http3TestServer;       // HTTP/3 ( h3  )
    String httpURI;
    String httpURIToMoreSecure;   // redirects HTTP to HTTPS
    String httpURIToH3MoreSecure; // redirects HTTP to HTTPS/3
    String httpsURI;
    String httpsURIToLessSecure; // redirects HTTPS to HTTP
    String http2URI;
    String http2URIToMoreSecure; // redirects HTTP to HTTPS
    String http2URIToH3MoreSecure; // redirects HTTP to HTTPS/3
    String https2URI;
    String https2URIToLessSecure; // redirects HTTPS to HTTP
    String https3URI;
    String https3HeadURI;
    String http3URIToLessSecure; // redirects HTTP3 to HTTP
    String http3URIToH2cLessSecure; // redirects HTTP3 to h2c

    static final String MESSAGE = "Is fearr Gaeilge briste, na Bearla cliste";
    static final int ITERATIONS = 3;

    @DataProvider(name = "positive")
    public Object[][] positive() {
        return new Object[][] {
                { httpURI,                 Redirect.ALWAYS, Optional.empty()    },
                { httpsURI,                Redirect.ALWAYS, Optional.empty()    },
                { http2URI,                Redirect.ALWAYS, Optional.empty()    },
                { https2URI,               Redirect.ALWAYS, Optional.empty()    },
                { https3URI,               Redirect.ALWAYS, Optional.of(HTTP_3) },
                { httpURIToMoreSecure,     Redirect.ALWAYS, Optional.empty()    },
                { httpURIToH3MoreSecure,   Redirect.ALWAYS, Optional.of(HTTP_3) },
                { http2URIToMoreSecure,    Redirect.ALWAYS, Optional.empty()    },
                { http2URIToH3MoreSecure,  Redirect.ALWAYS, Optional.of(HTTP_3) },
                { httpsURIToLessSecure,    Redirect.ALWAYS, Optional.empty()    },
                { https2URIToLessSecure,   Redirect.ALWAYS, Optional.empty()    },
                { http3URIToLessSecure,    Redirect.ALWAYS, Optional.of(HTTP_3) },
                { http3URIToH2cLessSecure, Redirect.ALWAYS, Optional.of(HTTP_3) },

                { httpURI,                 Redirect.NORMAL, Optional.empty()    },
                { httpsURI,                Redirect.NORMAL, Optional.empty()    },
                { http2URI,                Redirect.NORMAL, Optional.empty()    },
                { https2URI,               Redirect.NORMAL, Optional.empty()    },
                { https3URI,               Redirect.NORMAL, Optional.of(HTTP_3) },
                { httpURIToMoreSecure,     Redirect.NORMAL, Optional.empty()    },
                { http2URIToMoreSecure,    Redirect.NORMAL, Optional.empty()    },
                { httpURIToH3MoreSecure,   Redirect.NORMAL, Optional.of(HTTP_3) },
                { http2URIToH3MoreSecure,  Redirect.NORMAL, Optional.of(HTTP_3) },
        };
    }

    HttpClient createClient(Redirect redirectPolicy, Optional<Version> version) throws Exception {
        var clientBuilder = newClientBuilderForH3()
                .followRedirects(redirectPolicy)
                .sslContext(sslContext);
        HttpClient client = version.map(clientBuilder::version)
                .orElse(clientBuilder)
                .build();
        if (version.stream().anyMatch(HTTP_3::equals)) {
            var builder = HttpRequest.newBuilder(URI.create(https3HeadURI))
                    .setOption(H3_DISCOVERY, ALT_SVC);
            var head = builder.copy().HEAD().version(HTTP_2).build();
            var get = builder.copy().GET().build();
            out.printf("%n---- sending initial head request (%s) -----%n", head.uri());
            var resp = client.send(head, BodyHandlers.ofString());
            assertEquals(resp.statusCode(), 200);
            assertEquals(resp.version(), HTTP_2);
            out.println("HEADERS: " + resp.headers());
            var length = resp.headers().firstValueAsLong("Content-Length")
                    .orElseThrow(AssertionError::new);
            if (length < 0) throw new AssertionError("negative length " + length);
            out.printf("%n---- sending initial HTTP/3 GET request (%s) -----%n", get.uri());
            resp = client.send(get, BodyHandlers.ofString());
            assertEquals(resp.statusCode(), 200);
            assertEquals(resp.version(), HTTP_3);
            assertEquals(resp.body().getBytes(UTF_8).length, length,
                    "body \"" + resp.body() + "\": ");
        }
        return client;
    }

    @Test(dataProvider = "positive")
    void test(String uriString, Redirect redirectPolicy, Optional<Version> clientVersion) throws Exception {
        out.printf("%n---- starting positive (%s, %s, %s) ----%n", uriString, redirectPolicy,
                clientVersion.map(Version::name).orElse("empty"));
        HttpClient client = createClient(redirectPolicy, clientVersion);

        URI uri = URI.create(uriString);
        HttpRequest request = HttpRequest.newBuilder(uri).build();
        out.println("Initial request: " + request.uri());

        for (int i=0; i< ITERATIONS; i++) {
            out.println("iteration: " + i);
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            out.println("  Got response: " + response);
            out.println("  Got body Path: " + response.body());
            out.println("  Got response.request: " + response.request());

            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), MESSAGE);
            // asserts redirected URI in response.request().uri()
            assertTrue(response.uri().getPath().endsWith("message"));
            assertPreviousRedirectResponses(request, response, clientVersion);
        }
    }

    static void assertPreviousRedirectResponses(HttpRequest initialRequest,
                                                HttpResponse<?> finalResponse,
                                                Optional<Version> clientVersion) {
        // there must be at least one previous response
        finalResponse.previousResponse()
                .orElseThrow(() -> new RuntimeException("no previous response"));

        HttpResponse<?> response = finalResponse;
        List<Version> versions = new ArrayList<>();
        versions.add(response.version());
        do {
            URI uri = response.uri();
            response = response.previousResponse().get();
            versions.add(response.version());
            assertTrue(300 <= response.statusCode() && response.statusCode() <= 309,
                       "Expected 300 <= code <= 309, got:" + response.statusCode());
            assertEquals(response.body(), null, "Unexpected body: " + response.body());
            String locationHeader = response.headers().firstValue("Location")
                      .orElseThrow(() -> new RuntimeException("no previous Location"));
            assertTrue(uri.toString().endsWith(locationHeader),
                      "URI: " + uri + ", Location: " + locationHeader);

        } while (response.previousResponse().isPresent());

        // initial
        assertEquals(initialRequest, response.request(),
                String.format("Expected initial request [%s] to equal last prev req [%s]",
                              initialRequest, response.request()));
        if (clientVersion.stream().anyMatch(HTTP_3::equals)) {
            out.println(versions.stream().map(Version::name)
                    .collect(Collectors.joining(" <-- ", "Redirects: ", ";")));
            assertTrue(versions.stream().anyMatch(HTTP_3::equals), "at least one version should be HTTP/3");
        }
    }

    // --  negatives

    @DataProvider(name = "negative")
    public Object[][] negative() {
        return new Object[][] {
                { httpURI,                 Redirect.NEVER,  Optional.empty()    },
                { httpsURI,                Redirect.NEVER,  Optional.empty()    },
                { http2URI,                Redirect.NEVER,  Optional.empty()    },
                { https2URI,               Redirect.NEVER,  Optional.empty()    },
                { https3URI,               Redirect.NEVER,  Optional.of(HTTP_3) },
                { httpURIToMoreSecure,     Redirect.NEVER,  Optional.empty()    },
                { http2URIToMoreSecure,    Redirect.NEVER,  Optional.empty()    },
                { httpURIToH3MoreSecure,   Redirect.NEVER,  Optional.of(HTTP_3) },
                { http2URIToH3MoreSecure,  Redirect.NEVER,  Optional.of(HTTP_3) },
                { httpsURIToLessSecure,    Redirect.NEVER,  Optional.empty()    },
                { https2URIToLessSecure,   Redirect.NEVER,  Optional.empty()    },
                { http3URIToLessSecure,    Redirect.NEVER,  Optional.of(HTTP_3) },
                { http3URIToH2cLessSecure, Redirect.NEVER,  Optional.of(HTTP_3) },

                { httpsURIToLessSecure,    Redirect.NORMAL, Optional.empty()    },
                { https2URIToLessSecure,   Redirect.NORMAL, Optional.empty()    },
                { http3URIToLessSecure,    Redirect.NORMAL, Optional.of(HTTP_3) },
                { http3URIToH2cLessSecure, Redirect.NORMAL, Optional.of(HTTP_3) },
        };
    }

    @Test(dataProvider = "negative")
    void testNegatives(String uriString, Redirect redirectPolicy, Optional<Version> clientVersion)
            throws Exception {
        out.printf("%n---- starting negative (%s, %s, %s) ----%n", uriString, redirectPolicy,
                clientVersion.map(Version::name).orElse("empty"));
        HttpClient client = createClient(redirectPolicy, clientVersion);

        URI uri = URI.create(uriString);
        HttpRequest request = HttpRequest.newBuilder(uri).build();
        out.println("Initial request: " + request.uri());

        for (int i=0; i< ITERATIONS; i++) {
            out.println("iteration: " + i);
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            out.println("  Got response: " + response);
            out.println("  Got body Path: " + response.body());
            out.println("  Got response.request: " + response.request());

            assertEquals(response.statusCode(), 302);
            assertEquals(response.body(), "XY");
            // asserts original URI in response.request().uri()
            assertTrue(response.uri().equals(uri));
            assertFalse(response.previousResponse().isPresent());
        }
    }


    // -- Infrastructure

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(new BasicHttpRedirectHandler(), "/http1/same/");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1/same/redirect";
        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(new BasicHttpRedirectHandler(),"/https1/same/");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1/same/redirect";

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(new BasicHttpRedirectHandler(), "/http2/same/");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/same/redirect";
        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(new BasicHttpRedirectHandler(), "/https2/same/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/same/redirect";

        http3TestServer = HttpTestServer.create(ANY, sslContext);
        http3TestServer.addHandler(new BasicHttpRedirectHandler(), "/http3/same/");
        https3URI = "https://" + http3TestServer.serverAuthority() + "/http3/same/redirect";
        http3TestServer.addHandler(new HttpHeadOrGetHandler(), "/http3/head");
        https3HeadURI = "https://" + http3TestServer.serverAuthority() + "/http3/head";


        // HTTP to HTTPS redirect handler
        httpTestServer.addHandler(new ToSecureHttpRedirectHandler(httpsURI), "/http1/toSecure/");
        httpURIToMoreSecure = "http://" + httpTestServer.serverAuthority()+ "/http1/toSecure/redirect";
        // HTTP to HTTP/3 redirect handler
        httpTestServer.addHandler(new ToSecureHttpRedirectHandler(https3URI), "/http1/toSecureH3/");
        httpURIToH3MoreSecure = "http://" + httpTestServer.serverAuthority()+ "/http1/toSecureH3/redirect";
        // HTTP2 to HTTP2S redirect handler
        http2TestServer.addHandler(new ToSecureHttpRedirectHandler(https2URI), "/http2/toSecure/");
        http2URIToMoreSecure = "http://" + http2TestServer.serverAuthority() + "/http2/toSecure/redirect";
        // HTTP2 to HTTP2S redirect handler
        http2TestServer.addHandler(new ToSecureHttpRedirectHandler(https3URI), "/http2/toSecureH3/");
        http2URIToH3MoreSecure = "http://" + http2TestServer.serverAuthority() + "/http2/toSecureH3/redirect";

        // HTTPS to HTTP redirect handler
        httpsTestServer.addHandler(new ToLessSecureRedirectHandler(httpURI), "/https1/toLessSecure/");
        httpsURIToLessSecure = "https://" + httpsTestServer.serverAuthority() + "/https1/toLessSecure/redirect";
        // HTTPS2 to HTTP2 redirect handler
        https2TestServer.addHandler(new ToLessSecureRedirectHandler(http2URI), "/https2/toLessSecure/");
        https2URIToLessSecure = "https://" + https2TestServer.serverAuthority() + "/https2/toLessSecure/redirect";
        // HTTP3 to HTTP redirect handler
        http3TestServer.addHandler(new ToLessSecureRedirectHandler(httpURI), "/http3/toLessSecure/");
        http3URIToLessSecure = "https://" + http3TestServer.serverAuthority() + "/http3/toLessSecure/redirect";
        // HTTP3 to HTTP2 redirect handler
        http3TestServer.addHandler(new ToLessSecureRedirectHandler(http2URI), "/http3/toLessSecureH2/");
        http3URIToH2cLessSecure = "https://" + http3TestServer.serverAuthority() + "/http3/toLessSecureH2/redirect";

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        http3TestServer.start();
        createClient(Redirect.NEVER, Optional.of(HTTP_3));
    }

    @AfterTest
    public void teardown() throws Exception {
        httpTestServer.stop();
        httpsTestServer.stop();
        http2TestServer.stop();
        https2TestServer.stop();
    }

    // Redirects to same protocol
    static class BasicHttpRedirectHandler implements HttpTestHandler {
        // flip-flop between chunked/variable and fixed length redirect responses
        volatile int count;

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            System.out.println("BasicHttpRedirectHandler for: " + t.getRequestURI());
            readAllRequestData(t);

            if (t.getRequestURI().getPath().endsWith("redirect")) {
                String url = t.getRequestURI().resolve("message").toString();
                t.getResponseHeaders().addHeader("Location", url);
                int len = count % 2 == 0 ? 2 : -1;
                t.sendResponseHeaders(302, len);
                try (OutputStream os = t.getResponseBody()) {
                    os.write(new byte[]{'X', 'Y'});  // stuffing some response body
                }
            } else {
                try (OutputStream os = t.getResponseBody()) {
                    byte[] bytes = MESSAGE.getBytes(UTF_8);
                    t.sendResponseHeaders(200, bytes.length);
                    os.write(bytes);
                }
            }
        }
    }

    // Redirects to a, possibly, more secure protocol, (HTTP to HTTPS)
    static class ToSecureHttpRedirectHandler implements HttpTestHandler {
        final String targetURL;
        ToSecureHttpRedirectHandler(String targetURL) {
            this.targetURL = targetURL;
        }
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            System.out.println("ToSecureHttpRedirectHandler for: " + t.getRequestURI());
            readAllRequestData(t);

            if (t.getRequestURI().getPath().endsWith("redirect")) {
                t.getResponseHeaders().addHeader("Location", targetURL);
                System.out.println("ToSecureHttpRedirectHandler redirecting to: " + targetURL);
                t.sendResponseHeaders(302, 2); // fixed-length
                try (OutputStream os = t.getResponseBody()) {
                    os.write(new byte[]{'X', 'Y'});
                }
            } else {
                Throwable ex = new RuntimeException("Unexpected request");
                ex.printStackTrace();
                t.sendResponseHeaders(500, 0);
            }
        }
    }

    // Redirects to a, possibly, less secure protocol (HTTPS to HTTP)
    static class ToLessSecureRedirectHandler implements HttpTestHandler {
        final String targetURL;
        ToLessSecureRedirectHandler(String targetURL) {
            this.targetURL = targetURL;
        }
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            System.out.println("ToLessSecureRedirectHandler for: " + t.getRequestURI());
            readAllRequestData(t);

            if (t.getRequestURI().getPath().endsWith("redirect")) {
                t.getResponseHeaders().addHeader("Location", targetURL);
                System.out.println("ToLessSecureRedirectHandler redirecting to: " + targetURL);
                t.sendResponseHeaders(302, -1);  // chunked/variable
                try (OutputStream os = t.getResponseBody()) {
                    os.write(new byte[]{'X', 'Y'});
                }
            } else {
                Throwable ex = new RuntimeException("Unexpected request");
                ex.printStackTrace();
                t.sendResponseHeaders(500, 0);
            }
        }
    }

    static void readAllRequestData(HttpTestExchange t) throws IOException {
        try (InputStream is = t.getRequestBody()) {
            is.readAllBytes();
        }
    }
}
