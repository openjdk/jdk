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
 * @bug 8199943
 * @summary Test for cookie handling when retrying after close
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.httpclient.test.lib.http2.Http2TestServer jdk.test.lib.net.SimpleSSLContext
 *        ReferenceTracker
 * @run testng/othervm
 *       -Djdk.httpclient.HttpClient.log=trace,headers,requests
 *       RetryWithCookie
 */

import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import jdk.httpclient.test.lib.common.HttpServerAdapters;

import static java.lang.System.out;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class RetryWithCookie implements HttpServerAdapters {

    SSLContext sslContext;
    HttpTestServer httpTestServer;        // HTTP/1.1    [ 5 servers ]
    HttpTestServer httpsTestServer;       // HTTPS/1.1
    HttpTestServer http2TestServer;       // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;      // HTTP/2 ( h2  )
    HttpTestServer http3TestServer;       // HTTP/3 ( h3  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;
    String http3URI;

    static final String MESSAGE = "BasicRedirectTest message body";
    static final int ITERATIONS = 3;

    @DataProvider(name = "positive")
    public Object[][] positive() {
        return new Object[][] {
                { http3URI,   },
                { httpURI,    },
                { httpsURI,   },
                { http2URI,   },
                { https2URI,  },
        };
    }

    static final AtomicLong requestCounter = new AtomicLong();
    final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;

    private HttpRequest.Builder newRequestBuilder(URI uri) {
        var builder = HttpRequest.newBuilder(uri);
        if (uri.getRawPath().contains("/http3/")) {
            builder = builder.version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
        }
        return builder;
    }

    @Test(dataProvider = "positive")
    void test(String uriString) throws Exception {
        out.printf("%n---- starting (%s) ----%n", uriString);
        CookieManager cookieManager = new CookieManager();
        var builder = uriString.contains("/http3/")
                ? newClientBuilderForH3()
                : HttpClient.newBuilder();
        HttpClient client = builder
                .proxy(NO_PROXY)
                .followRedirects(Redirect.ALWAYS)
                .cookieHandler(cookieManager)
                .sslContext(sslContext)
                .build();
        TRACKER.track(client);
        assert client.cookieHandler().isPresent();

        URI uri = URI.create(uriString);
        List<String> cookies = new ArrayList<>();
        cookies.add("CUSTOMER=ARTHUR_DENT");
        Map<String, List<String>> cookieHeaders = new HashMap<>();
        cookieHeaders.put("Set-Cookie", cookies);
        cookieManager.put(uri, cookieHeaders);

        HttpRequest request = newRequestBuilder(uri)
                .header("X-uuid", "uuid-" + requestCounter.incrementAndGet())
                .build();
        out.println("Initial request: " + request.uri());

        for (int i=0; i< ITERATIONS; i++) {
            out.println("iteration: " + i);
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            out.println("  Got response: " + response);
            out.println("  Got body Path: " + response.body());

            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), MESSAGE);
            assertEquals(response.headers().allValues("X-Request-Cookie"), cookies);
            request = newRequestBuilder(uri)
                    .header("X-uuid", "uuid-" + requestCounter.incrementAndGet())
                    .build();
        }
    }

    // -- Infrastructure

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(new CookieRetryHandler(), "/http1/cookie/");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1/cookie/retry";
        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(new CookieRetryHandler(),"/https1/cookie/");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1/cookie/retry";

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(new CookieRetryHandler(), "/http2/cookie/");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/cookie/retry";
        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(new CookieRetryHandler(), "/https2/cookie/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/cookie/retry";

        http3TestServer = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        http3TestServer.addHandler(new CookieRetryHandler(), "/http3/cookie/");
        http3URI = "https://" + http3TestServer.serverAuthority() + "/http3/cookie/retry";

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        http3TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        Thread.sleep(100);
        AssertionError fail = TRACKER.check(500);
        try {
            httpTestServer.stop();
            httpsTestServer.stop();
            http2TestServer.stop();
            https2TestServer.stop();
            http3TestServer.stop();
        } finally {
            if (fail != null) throw fail;
        }
    }

    static class CookieRetryHandler implements HttpTestHandler {
        ConcurrentHashMap<String,String> closedRequests = new ConcurrentHashMap<>();

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            System.out.println("CookieRetryHandler for: " + t.getRequestURI());

            List<String> uuids = t.getRequestHeaders().get("X-uuid");
            if (uuids == null || uuids.size() != 1) {
                readAllRequestData(t);
                try (OutputStream os = t.getResponseBody()) {
                    String msg = "Incorrect uuid header values:[" + uuids + "]";
                    (new RuntimeException(msg)).printStackTrace();
                    t.sendResponseHeaders(500, -1);
                    os.write(msg.getBytes(UTF_8));
                }
                return;
            }

            String uuid = uuids.get(0);
            // retrying
            if (closedRequests.putIfAbsent(uuid, t.getRequestURI().toString()) == null) {
                if (t.getExchangeVersion() == HTTP_1_1) {
                    // Throwing an exception here only causes a retry
                    // with HTTP_1_1 - where it forces the server to close
                    // the connection.
                    // For HTTP/2 then throwing an IOE would cause the server
                    // to close the stream, and throwing anything else would
                    // cause it to close the connection, but neither would
                    // cause the client to retry.
                    // So we simply do not try to retry with HTTP/2 and just verify
                    // we have received the expected cookie
                    throw new IOException("Closing on first request");
                }
            }

            // not retrying
            readAllRequestData(t);
            try (OutputStream os = t.getResponseBody()) {
                List<String> cookie = t.getRequestHeaders().get("Cookie");

                if (cookie == null || cookie.size() == 0) {
                    String msg = "No cookie header present";
                    (new RuntimeException(msg)).printStackTrace();
                    t.sendResponseHeaders(500, -1);
                    os.write(msg.getBytes(UTF_8));
                } else if (!cookie.get(0).equals("CUSTOMER=ARTHUR_DENT")) {
                    String msg = "Incorrect cookie header value:[" + cookie.get(0) + "]";
                    (new RuntimeException(msg)).printStackTrace();
                    t.sendResponseHeaders(500, -1);
                    os.write(msg.getBytes(UTF_8));
                } else if (cookie.size() > 1) {
                    String msg = "Incorrect cookie header values:[" + cookie + "]";
                    (new RuntimeException(msg)).printStackTrace();
                    t.sendResponseHeaders(500, -1);
                    os.write(msg.getBytes(UTF_8));
                } else {
                    assert cookie.get(0).equals("CUSTOMER=ARTHUR_DENT");
                    byte[] bytes = MESSAGE.getBytes(UTF_8);
                    for (String value : cookie) {
                        t.getResponseHeaders().addHeader("X-Request-Cookie", value);
                    }
                    t.sendResponseHeaders(200, bytes.length);
                    os.write(bytes);
                }
            }

            closedRequests.remove(uuid);
        }
    }

    static void readAllRequestData(HttpTestExchange t) throws IOException {
        try (InputStream is = t.getRequestBody()) {
            is.readAllBytes();
        }
    }
}
