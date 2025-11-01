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
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @compile ../ReferenceTracker.java
 * @run testng/othervm -Djdk.internal.httpclient.debug=true H3BadHeadersTest
 * @summary this test verifies the behaviour of the HttpClient when presented
 *          with bad headers
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.util.List.of;
import static java.util.Map.entry;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class H3BadHeadersTest implements HttpServerAdapters  {

    private static final List<List<Entry<String, String>>> BAD_HEADERS = of(
        of(entry(":status", "200"),  entry(":hello", "GET")),                      // Unknown pseudo-header
        of(entry(":status", "200"),  entry("hell o", "value")),                    // Space in the name
        of(entry(":status", "200"),  entry("hello", "line1\r\n  line2\r\n")),      // Multiline value
        of(entry(":status", "200"),  entry("hello", "DE" + ((char) 0x7F) + "L"))   // Bad byte in value
        // Not easily testable with H3, because we use a HttpHeadersBuilders which sorts headers...
        // of(entry("hello", "world!"), entry(":status", "200"))                               // Pseudo header is not the first one
    );

    static final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;

    SSLContext sslContext;
    HttpTestServer http3TestServer;   // HTTP/3 ( h3 only )
    HttpTestServer https2TestServer;  // HTTP/2 ( h2 + h3 )
    String http3URI;
    String https2URI;


    @DataProvider(name = "variants")
    public Object[][] variants() {
        return new Object[][] {
                { http3URI,  false},
                { https2URI, false},
                { http3URI,  true},
                { https2URI, true},
        };
    }


    @Test(dataProvider = "variants")
    void test(String uri,
              boolean sameClient)
        throws Exception
    {
        System.out.printf("%ntest %s, %s, STARTING%n%n", uri, sameClient);
        System.err.printf("%ntest %s, %s, STARTING%n%n", uri, sameClient);
        var config = uri.startsWith(http3URI)
                ? Http3DiscoveryMode.HTTP_3_URI_ONLY
                : https2TestServer.supportsH3DirectConnection()
                ? Http3DiscoveryMode.ANY
                : Http3DiscoveryMode.ALT_SVC;

        boolean sendHeadRequest = config != Http3DiscoveryMode.HTTP_3_URI_ONLY;

        HttpClient client = null;
        for (int i=0; i< BAD_HEADERS.size(); i++) {
            boolean needsHeadRequest = false;
            if (!sameClient || client == null) {
                needsHeadRequest = sendHeadRequest;
                client = newClientBuilderForH3()
                        .version(Version.HTTP_3)
                        .sslContext(sslContext)
                        .build();
            }

            if (needsHeadRequest) {
                URI simpleURI = URI.create(uri);
                HttpRequest head = HttpRequest.newBuilder(simpleURI)
                        .version(Version.HTTP_2)
                        .HEAD().setOption(H3_DISCOVERY, config).build();
                System.out.println("\nSending HEAD request: " + head);
                var headResponse = client.send(head, BodyHandlers.ofString());
                assertEquals(headResponse.statusCode(), 200);
                assertEquals(headResponse.version(), Version.HTTP_2);
            }

            URI uriWithQuery = URI.create(uri +  "?BAD_HEADERS=" + i);
            HttpRequest request = HttpRequest.newBuilder(uriWithQuery)
                    .POST(BodyPublishers.ofString("Hello there!"))
                    .setOption(H3_DISCOVERY, config)
                    .version(Version.HTTP_3)
                    .build();
            System.out.println("\nSending request:" + uriWithQuery);
            try {
                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                fail("Expected exception, got :" + response + ", " + response.body());
            } catch (IOException ioe) {
                System.out.println("Got EXPECTED: " + ioe);
                assertDetailMessage(ioe, i);
            }
            if (!sameClient) {
                var tracker = TRACKER.getTracker(client);
                client = null;
                System.gc();
                var error = TRACKER.check(tracker, 1500);
                if (error != null) throw error;
            }
        }
        if (client !=  null) {
            var tracker = TRACKER.getTracker(client);
            client = null;
            System.gc();
            var error = TRACKER.check(tracker, 1500);
            if (error != null) throw error;
        }
        System.out.printf("%ntest %s, %s, DONE%n%n", uri, sameClient);
        System.err.printf("%ntest %s, %s, DONE%n%n", uri, sameClient);
    }

    @Test(dataProvider = "variants")
    void testAsync(String uri,
                   boolean sameClient) throws Exception
    {

        System.out.printf("%ntestAsync %s, %s, STARTING%n%n", uri, sameClient);
        System.err.printf("%ntestAsync %s, %s, STARTING%n%n", uri, sameClient);
        var config = uri.startsWith(http3URI)
                ? Http3DiscoveryMode.HTTP_3_URI_ONLY
                : https2TestServer.supportsH3DirectConnection()
                ? Http3DiscoveryMode.ANY
                : Http3DiscoveryMode.ALT_SVC;

        boolean sendHeadRequest = config != Http3DiscoveryMode.HTTP_3_URI_ONLY;

        HttpClient client = null;
        for (int i=0; i< BAD_HEADERS.size(); i++) {
            boolean needsHeadRequest = false;
            if (!sameClient || client == null) {
                needsHeadRequest = sendHeadRequest;
                client = newClientBuilderForH3()
                        .version(Version.HTTP_3)
                        .sslContext(sslContext)
                        .build();
            }

            if (needsHeadRequest) {
                URI simpleURI = URI.create(uri);
                HttpRequest head = HttpRequest.newBuilder(simpleURI)
                        .version(Version.HTTP_2)
                        .HEAD()
                        .setOption(H3_DISCOVERY, config)
                        .build();
                System.out.println("\nSending HEAD request: " + head);

                var headResponse = client.send(head, BodyHandlers.ofString());
                assertEquals(headResponse.statusCode(), 200);
                assertEquals(headResponse.version(), Version.HTTP_2);
            }

            URI uriWithQuery = URI.create(uri +  "?BAD_HEADERS=" + i);
            HttpRequest request = HttpRequest.newBuilder(uriWithQuery)
                    .POST(BodyPublishers.ofString("Hello there!"))
                    .setOption(H3_DISCOVERY, config)
                    .version(Version.HTTP_3)
                    .build();
            System.out.println("\nSending request:" + uriWithQuery);

            Throwable t = null;
            try {
                HttpResponse<String> response = client.sendAsync(request, BodyHandlers.ofString()).get();
                fail("Expected exception, got :" + response + ", " + response.body());
            } catch (Throwable t0) {
                System.out.println("Got EXPECTED: " + t0);
                if (t0 instanceof ExecutionException) {
                    t0 = t0.getCause();
                }
                t = t0;
            }
            assertDetailMessage(t, i);
            if (!sameClient) {
                var tracker = TRACKER.getTracker(client);
                client = null;
                System.gc();
                var error = TRACKER.check(tracker, 1500);
                if (error != null) throw error;
            }
        }
        if (client != null) {
            var tracker = TRACKER.getTracker(client);
            client = null;
            System.gc();
            var error = TRACKER.check(tracker, 1500);
            if (error != null) throw error;
        }
        System.out.printf("%ntestAsync %s, %s, DONE%n%n", uri, sameClient);
        System.err.printf("%ntestAsync %s, %s, DONE%n%n", uri, sameClient);
    }

    // Assertions based on implementation specific detail messages. Keep in
    // sync with implementation.
    static void assertDetailMessage(Throwable throwable, int iterationIndex) {
        try {
            assertTrue(throwable instanceof IOException,
                    "Expected IOException, got, " + throwable);
            assertNotNull(throwable.getMessage(), "No message for " + throwable);
            assertTrue(throwable.getMessage().contains("malformed response"),
                    "Expected \"malformed response\" in: " + throwable.getMessage());

            if (iterationIndex == 0) { // unknown
                assertTrue(throwable.getMessage().contains("Unknown pseudo-header"),
                        "Expected \"Unknown pseudo-header\" in: " + throwable.getMessage());
            } else if (iterationIndex == 4) { // unexpected
                assertTrue(throwable.getMessage().contains(" Unexpected pseudo-header"),
                        "Expected \" Unexpected pseudo-header\" in: " + throwable.getMessage());
            } else {
                assertTrue(throwable.getMessage().contains("Bad header"),
                        "Expected \"Bad header\" in: " + throwable.getMessage());
            }
        } catch (AssertionError e) {
            System.out.println("Exception does not match expectation: " + throwable);
            throwable.printStackTrace(System.out);
            throw e;
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        System.out.println("creating servers");
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        http3TestServer =  HttpTestServer.create(Http3DiscoveryMode.HTTP_3_URI_ONLY, sslContext);
        http3TestServer.addHandler(new BadHeadersHandler(), "/http3/echo");
        http3URI = "https://" + http3TestServer.serverAuthority() + "/http3/echo";

        https2TestServer = HttpTestServer.create(Http3DiscoveryMode.ANY, sslContext);
        https2TestServer.addHandler(new BadHeadersHandler(), "/https2/echo");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/echo";

        http3TestServer.start();
        https2TestServer.start();
        System.out.println("server started");
    }

    @AfterTest
    public void teardown() throws Exception {
        System.err.println("\n\n**** stopping servers\n");
        System.out.println("stopping servers");
        http3TestServer.stop();
        https2TestServer.stop();
        System.out.println("servers stopped");
    }

    static class BadHeadersHandler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            var uri = t.getRequestURI();
            String query = uri.getRawQuery();
            if (query != null && !query.isEmpty()) {
                int badHeadersIndex = Integer.parseInt(query.substring(query.indexOf("=") + 1));
                assert badHeadersIndex >= 0 && badHeadersIndex < BAD_HEADERS.size() :
                        "Unexpected badHeadersIndex value: " + badHeadersIndex;
                List<Entry<String, String>> headers = BAD_HEADERS.get(badHeadersIndex);
                var responseHeaders = t.getResponseHeaders();
                for (var e : headers) {
                    responseHeaders.addHeader(e.getKey(), e.getValue());
                }
            }
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                byte[] bytes = is.readAllBytes();
                t.sendResponseHeaders(200, bytes.length);
                if (t.getRequestMethod().equals("HEAD")) {
                    os.close();
                } else {
                    os.write(bytes);
                }
            }
        }
    }

}
