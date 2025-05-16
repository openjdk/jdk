/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8203433 8276559
 * @summary (httpclient) Add tests for HEAD and 304 responses.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm
 *       -Djdk.httpclient.HttpClient.log=trace,headers,requests
 *       HeadTest
 */

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;

import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.testng.Assert.assertEquals;

public class HeadTest implements HttpServerAdapters {

    SSLContext sslContext;
    HttpTestServer httpTestServer;        // HTTP/1.1
    HttpTestServer httpsTestServer;       // HTTPS/1.1
    HttpTestServer http2TestServer;       // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;      // HTTP/2 ( h2  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;

    static final String CONTENT_LEN = "300";

    /*
     * NOT_MODIFIED status code results from a conditional GET where
     * the server does not (must not) return a response body because
     * the condition specified in the request disallows it
     */
    static final int HTTP_NOT_MODIFIED = 304;
    static final int HTTP_OK = 200;


    @DataProvider(name = "positive")
    public Object[][] positive() {
        return new Object[][] {
                // HTTP/1.1
                { httpURI, "GET", HTTP_NOT_MODIFIED, HTTP_1_1  },
                { httpsURI, "GET", HTTP_NOT_MODIFIED, HTTP_1_1  },
                { httpURI, "HEAD", HTTP_OK, HTTP_1_1  },
                { httpsURI, "HEAD", HTTP_OK, HTTP_1_1  },
                { httpURI + "transfer/", "GET", HTTP_NOT_MODIFIED, HTTP_1_1  },
                { httpsURI + "transfer/", "GET", HTTP_NOT_MODIFIED, HTTP_1_1  },
                { httpURI + "transfer/", "HEAD", HTTP_OK, HTTP_1_1  },
                { httpsURI + "transfer/", "HEAD", HTTP_OK, HTTP_1_1  },
                // HTTP/2
                { http2URI, "GET", HTTP_NOT_MODIFIED, HttpClient.Version.HTTP_2  },
                { https2URI, "GET", HTTP_NOT_MODIFIED, HttpClient.Version.HTTP_2  },
                { http2URI, "HEAD", HTTP_OK, HttpClient.Version.HTTP_2  },
                { https2URI, "HEAD", HTTP_OK, HttpClient.Version.HTTP_2  },
                // HTTP2 forbids transfer-encoding
        };
    }

    @Test(dataProvider = "positive")
    void test(String uriString, String method,
                        int expResp, HttpClient.Version version) throws Exception {
        out.printf("%n---- starting (%s) ----%n", uriString);
        URI uri = URI.create(uriString);
        HttpRequest.Builder requestBuilder = HttpRequest
                .newBuilder(uri)
                .version(version)
                .method(method, HttpRequest.BodyPublishers.noBody());
        doTest(requestBuilder.build(), expResp);
        // repeat the test this time by building the request using convenience
        // GET and HEAD methods
        requestBuilder = HttpRequest.newBuilder(uri)
                .version(version);
        switch (method) {
            case "GET" -> requestBuilder.GET();
            case "HEAD" -> requestBuilder.HEAD();
            default -> throw new IllegalArgumentException("Unexpected method " + method);
        }
        doTest(requestBuilder.build(), expResp);
    }

    // issue a request with no body and verify the response code is the expected response code
    private void doTest(HttpRequest request, int expResp) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(Redirect.ALWAYS)
                .sslContext(sslContext)
                .build();
        out.println("Initial request: " + request.uri());

        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

        out.println("  Got response: " + response);

        assertEquals(response.statusCode(), expResp);
        assertEquals(response.body(), "");
        assertEquals(response.headers().firstValue("Content-length").get(), CONTENT_LEN);
        assertEquals(response.version(), request.version().get());
    }

    // -- Infrastructure

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        InetSocketAddress sa = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(new HeadHandler(), "/");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/";
        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(new HeadHandler(),"/");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/";

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(new HeadHandler(), "/");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/";
        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(new HeadHandler(), "/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/";


        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        httpTestServer.stop();
        httpsTestServer.stop();
        http2TestServer.stop();
        https2TestServer.stop();
    }

    static class HeadHandler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            readAllRequestData(t); // shouldn't be any
            String method = t.getRequestMethod();
            String path = t.getRequestURI().getPath();
            HttpTestResponseHeaders rsph = t.getResponseHeaders();
            if (path.contains("transfer"))
                rsph.addHeader("Transfer-Encoding", "chunked");
            rsph.addHeader("Content-length", CONTENT_LEN);
            if (method.equals("HEAD")) {
                t.sendResponseHeaders(HTTP_OK, -1);
            } else if (method.equals("GET")) {
                t.sendResponseHeaders(HTTP_NOT_MODIFIED, -1);
            }
            t.close();
        }
    }

    static void readAllRequestData(HttpTestExchange t) throws IOException {
        try (InputStream is = t.getRequestBody()) {
            is.readAllBytes();
        }
    }
}
