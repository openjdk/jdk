/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @build jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.test.lib.Asserts
 *        jdk.test.lib.Utils
 *        jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=ssl,requests,responses,errors,http3,quic:hs
 *                     -Djdk.internal.httpclient.debug=false
 *                     H3ConnectionPoolTest
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.Test;

import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertNotEquals;
import static jdk.test.lib.Asserts.assertTrue;

public class H3ConnectionPoolTest implements HttpServerAdapters {

    private static final String CLASS_NAME = H3ConnectionPoolTest.class.getSimpleName();

    static int altsvcPort, https2Port, http3Port;
    static HttpTestServer http3OnlyServer;
    static HttpTestServer https2AltSvcServer;
    static volatile HttpClient client = null;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    static volatile String http3OnlyURIString, https2URIString, http3AltSvcURIString, http3DirectURIString;

    static void initialize(boolean samePort) throws Exception {
        initialize(samePort, HttpTestFileEchoHandler::new);
    }

    static void initialize(boolean samePort, Supplier<HttpTestHandler> handlers) throws Exception {
        System.out.println("\nConfiguring for advertised AltSvc on "
                + (samePort ? "same port" : "ephemeral port"));
        try {
            client = null;
            client = getClient();

            // server that supports both HTTP/2 and HTTP/3, with HTTP/3 on an altSvc port.
            Http2TestServer serverImpl = new Http2TestServer(true, sslContext);
            if (samePort) {
                System.out.println("Attempting to enable advertised HTTP/3 service on same port");
                serverImpl.enableH3AltServiceOnSamePort();
                System.out.println("Advertised AltSvc on same port " +
                        (serverImpl.supportsH3DirectConnection() ? "enabled" : " not enabled"));
            } else {
                System.out.println("Attempting to enable advertised HTTP/3 service on different port");
                serverImpl.enableH3AltServiceOnEphemeralPort();
            }
            https2AltSvcServer = HttpTestServer.of(serverImpl);
            https2AltSvcServer.addHandler(handlers.get(), "/" + CLASS_NAME + "/https2/");
            https2AltSvcServer.addHandler(handlers.get(), "/" + CLASS_NAME + "/h2h3/");
            https2Port = https2AltSvcServer.getAddress().getPort();
            altsvcPort = https2AltSvcServer.getH3AltService()
                    .map(Http3TestServer::getAddress).stream()
                    .mapToInt(InetSocketAddress::getPort).findFirst()
                    .getAsInt();
            // server that only supports HTTP/3 - we attempt to use the same port
            // as the HTTP/2 server so that we can pretend that the H2 server as two H3 endpoints:
            //   one advertised (the alt service endpoint og the HTTP/2 server)
            //   one non advertised (the direct endpoint, at the same authority as HTTP/2, but which
            //   is in fact our http3OnlyServer)
            Http3TestServer http3ServerImpl;
            try {
                http3ServerImpl = new Http3TestServer(sslContext, samePort ? 0 : https2Port);
                System.out.println("Unadvertised service enabled on "
                        + (samePort ? "ephemeral port" : "same port"));
            } catch (IOException ex) {
                System.out.println("Can't create HTTP/3 server on same port: " + ex);
                http3ServerImpl = new Http3TestServer(sslContext, 0);
            }
            http3OnlyServer = HttpTestServer.of(http3ServerImpl);
            http3OnlyServer.createContext("/" + CLASS_NAME + "/http3/", handlers.get());
            http3OnlyServer.createContext("/" + CLASS_NAME + "/h2h3/", handlers.get());
            http3OnlyServer.start();
            http3Port = http3ServerImpl.getQuicServer().getAddress().getPort();

            if (http3Port == https2Port) {
                System.out.println("HTTP/3 server enabled on same port than HTTP/2 server");
                if (samePort) {
                    System.out.println("WARNING: configuration could not be respected," +
                            " should have used ephemeral port for HTTP/3 server");
                }
            } else {
                System.out.println("HTTP/3 server enabled on a different port than HTTP/2 server");
                if (!samePort) {
                    System.out.println("WARNING: configuration could not be respected," +
                            " should have used same port for HTTP/3 server");
                }
            }
            if (altsvcPort == https2Port) {
                if (!samePort) {
                    System.out.println("WARNING: configuration could not be respected," +
                            " should have used same port for advertised AltSvc");
                }
            } else {
                if (samePort) {
                    System.out.println("WARNING: configuration could not be respected," +
                            " should have used ephemeral port for advertised AltSvc");
                }
            }

            http3OnlyURIString = "https://" + http3OnlyServer.serverAuthority() + "/" + CLASS_NAME + "/http3/foo/";
            https2URIString = "https://" + https2AltSvcServer.serverAuthority() + "/" + CLASS_NAME + "/https2/bar/";
            http3DirectURIString = "https://" + https2AltSvcServer.serverAuthority() + "/" + CLASS_NAME + "/h2h3/direct/";
            http3AltSvcURIString = https2URIString
                    .replace(":" + https2Port + "/", ":" + altsvcPort + "/")
                    .replace("/https2/bar/", "/h2h3/altsvc/");
            System.out.println("HTTP/2 server started at: " + https2AltSvcServer.serverAuthority());
            System.out.println(" with advertised HTTP/3 endpoint at: "
                    + URI.create(http3AltSvcURIString).getRawAuthority());
            System.out.println("HTTP/3 server started at:" + http3OnlyServer.serverAuthority());

            https2AltSvcServer.start();
        } catch (Throwable e) {
            System.out.println("Configuration failed: " + e);
            System.err.println("Throwing now: " + e);
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public static void testH3Only() throws Exception {
        System.out.println("\nTesting HTTP/3 only");
        initialize(true);
        try (HttpClient client = getClient()) {
            var reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(http3OnlyURIString))
                    .version(HTTP_3)
                    .GET();
            HttpRequest request1 = reqBuilder.copy()
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .build();
            HttpResponse<String> response1 = client.send(request1, BodyHandlers.ofString());
            System.out.printf("First response: (%s): %s%n", response1.connectionLabel(), response1);
            response1.headers().map().entrySet().forEach((e) -> {
                System.out.printf("     %s: %s%n", e.getKey(), e.getValue());
            });
            // ANY should reuse the same connection
            HttpRequest request2 = reqBuilder.copy()
                    .setOption(H3_DISCOVERY, ANY)
                    .build();
            HttpResponse<String> response2 = client.send(request2, BodyHandlers.ofString());
            HttpRequest request3 = reqBuilder.copy()
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .build();
            HttpResponse<String> response3 = client.send(request3, BodyHandlers.ofString());
            // ANY should reuse the same connection
            HttpRequest request4 = reqBuilder.copy()
                    .setOption(H3_DISCOVERY, ANY)
                    .build();
            HttpResponse<String> response4 = client.send(request4, BodyHandlers.ofString());
            assertEquals(response1.connectionLabel().get(), response2.connectionLabel().get());
            assertEquals(response2.connectionLabel().get(), response3.connectionLabel().get());
            assertEquals(response3.connectionLabel().get(), response4.connectionLabel().get());
        } finally {
            http3OnlyServer.stop();
            https2AltSvcServer.stop();
        }
    }

    @Test
    public static void testH2H3WithTwoAltSVC() throws Exception {
        testH2H3(false);
    }

    @Test
    public static void testH2H3WithAltSVCOnSamePort() throws Exception {
        testH2H3(true);
    }

    private static void testH2H3(boolean samePort) throws Exception {
        System.out.println("\nTesting with advertised AltSvc on "
                + (samePort ? "same port" : "ephemeral port"));
        initialize(samePort);
        try (HttpClient client = getClient()) {
            var req1Builder = HttpRequest.newBuilder()
                    .uri(URI.create(http3DirectURIString))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET();
            var req2Builder = HttpRequest.newBuilder()
                    .uri(URI.create(http3DirectURIString))
                    .setOption(H3_DISCOVERY, ALT_SVC)
                    .version(HTTP_3)
                    .GET();

            if (altsvcPort == https2Port) {
                System.out.println("Testing with alt service on same port");

                // first request with HTTP3_URI_ONLY should create H3 connection
                HttpRequest request1 = req1Builder.copy().build();
                HttpResponse<String> response1 = client.send(request1, BodyHandlers.ofString());
                assertEquals(HTTP_3, response1.version());
                checkStatus(200, response1.statusCode());

                HttpRequest request2 = req2Builder.copy().build();
                // first request with ALT_SVC is to get alt service, should be H2
                HttpResponse<String> h2resp2 = client.send(request2, BodyHandlers.ofString());
                assertEquals(HTTP_2, h2resp2.version());
                checkStatus(200, h2resp2.statusCode());
                // second request should have ALT_SVC and create new connection with H3
                // it should not reuse the non-advertised connection
                HttpResponse<String> response2 = client.send(request2, BodyHandlers.ofString());
                assertEquals(HTTP_3, response2.version());
                checkStatus(200, response2.statusCode());
                assertNotEquals(response2.connectionLabel().get(), response1.connectionLabel().get());

                // second request with HTTP3_URI_ONLY should reuse a created connection
                // It should reuse the advertised connection (from response2) if same
                //    origin
                HttpRequest request3 = req1Builder.copy().build();
                HttpResponse<String> response3 = client.send(request3, BodyHandlers.ofString());
                assertEquals(HTTP_3, response3.version());
                checkStatus(200, response3.statusCode());
                assertEquals(response1.connectionLabel().get(), response3.connectionLabel().get());

                // third request with ALT_SVC should reuse the same advertised
                // connection (from response2), regardless of same origin...
                HttpRequest request4 = req2Builder.copy().build();
                HttpResponse<String> response4 = client.send(request4, BodyHandlers.ofString());
                assertEquals(HTTP_3, response4.version());
                checkStatus(200, response4.statusCode());
                assertEquals(response4.connectionLabel().get(), response2.connectionLabel().get());
            } else if (http3Port == https2Port) {
                System.out.println("Testing with two alt services");
                // first - make a direct connection
                HttpRequest request1 = req1Builder.copy().build();
                HttpResponse<String> response1 = client.send(request1, BodyHandlers.ofString());
                assertEquals(HTTP_3, response1.version());
                checkStatus(200, response1.statusCode());

                // second, get the alt service
                HttpRequest request2 = req2Builder.copy().build();
                // first request with ALT_SVC is to get alt service, should be H2
                HttpResponse<String> h2resp2 = client.send(request2, BodyHandlers.ofString());
                assertEquals(HTTP_2, h2resp2.version());
                checkStatus(200, h2resp2.statusCode());

                // second request should have ALT_SVC and create new connection with H3
                // it should not reuse the non-advertised connection
                HttpResponse<String> response2 = client.send(request2, BodyHandlers.ofString());
                assertEquals(HTTP_3, response2.version());
                checkStatus(200, response2.statusCode());
                assertNotEquals(response2.connectionLabel().get(), h2resp2.connectionLabel().get());
                assertNotEquals(response2.connectionLabel().get(), response1.connectionLabel().get());

                // third request with ALT_SVC should reuse the same advertised
                // connection (from response2), regardless of same origin...
                HttpRequest request3 = req2Builder.copy().build();
                HttpResponse<String> response3 = client.send(request3, BodyHandlers.ofString());
                assertEquals(HTTP_3, response3.version());
                checkStatus(200, response3.statusCode());
                assertEquals(response3.connectionLabel().get(), response2.connectionLabel().get());
                assertNotEquals(response3.connectionLabel().get(), response1.connectionLabel().get());

                // fourth request with HTTP_3_URI_ONLY should reuse the first connection,
                // and not reuse the second.
                HttpRequest request4 = req1Builder.copy().build();
                HttpResponse<String> response4 = client.send(request1, BodyHandlers.ofString());
                assertEquals(HTTP_3, response4.version());
                assertEquals(response4.connectionLabel().get(), response1.connectionLabel().get());
                assertNotEquals(response4.connectionLabel().get(), response3.connectionLabel().get());
                checkStatus(200, response1.statusCode());
            } else {
                System.out.println("WARNING: Couldn't create HTTP/3 server on same port! Can't test all...");
                // Get, get the alt service
                HttpRequest request2 = req2Builder.copy().build();
                // first request with ALT_SVC is to get alt service, should be H2
                HttpResponse<String> h2resp2 = client.send(request2, BodyHandlers.ofString());
                assertEquals(HTTP_2, h2resp2.version());
                checkStatus(200, h2resp2.statusCode());

                // second request should have ALT_SVC and create new connection with H3
                // it should not reuse the non-advertised connection
                HttpResponse<String> response2 = client.send(request2, BodyHandlers.ofString());
                assertEquals(HTTP_3, response2.version());
                checkStatus(200, response2.statusCode());
                assertNotEquals(response2.connectionLabel().get(), h2resp2.connectionLabel().get());

                // third request with ALT_SVC should reuse the same advertised
                // connection (from response2), regardless of same origin...
                HttpRequest request3 = req2Builder.copy().build();
                HttpResponse<String> response3 = client.send(request3, BodyHandlers.ofString());
                assertEquals(HTTP_3, response3.version());
                checkStatus(200, response3.statusCode());
                assertEquals(response3.connectionLabel().get(), response2.connectionLabel().get());
            }
        } finally {
            http3OnlyServer.stop();
            https2AltSvcServer.stop();
        }
    }

    @Test
    public static void testParallelH2H3WithTwoAltSVC() throws Exception {
        testH2H3Concurrent(false);
    }

    @Test
    public static void testParallelH2H3WithAltSVCOnSamePort() throws Exception {
        testH2H3Concurrent(true);
    }

    private static void testH2H3Concurrent(boolean samePort) throws Exception {
        System.out.println("\nTesting concurrent connections with advertised AltSvc on "
                + (samePort ? "same port" : "ephemeral port"));
        initialize(samePort);
        try (HttpClient client = getClient()) {
            var req1Builder = HttpRequest.newBuilder()
                    .uri(URI.create(http3DirectURIString))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET();
            var req2Builder = HttpRequest.newBuilder()
                    .uri(URI.create(http3DirectURIString))
                    .setOption(H3_DISCOVERY, ALT_SVC)
                    .version(HTTP_3)
                    .GET();

            if (altsvcPort == https2Port) {
                System.out.println("Testing with alt service on same port");

                // first request with HTTP3_URI_ONLY should create H3 connection
                HttpRequest request1 = req1Builder.copy().build();
                HttpRequest request2 = req2Builder.copy().build();
                List<CompletableFuture<HttpResponse<String>>> directResponses = new ArrayList<>();
                for (int i=0; i<3; i++) {
                    directResponses.add(client.sendAsync(request1, BodyHandlers.ofString()));
                }
                // can't send requests in parallel here because if any establishes
                // a connection before the H3 direct are established, then the H3
                // direct might reuse the H3 alt since the service is with same origin
                HttpResponse<String> h2resp2 = client.send(request2, BodyHandlers.ofString());
                String c1Label = null;
                for (int i = 0; i < directResponses.size(); i++) {
                    HttpResponse<String> response1 = directResponses.get(i).get();
                    System.out.printf("direct response [%s][%s]: %s%n", i,
                            response1.connectionLabel(),
                            response1);
                    assertEquals(HTTP_3, response1.version());
                    checkStatus(200, response1.statusCode());
                    if (i == 0) {
                        c1Label = response1.connectionLabel().get();
                    }
                    assertEquals(c1Label, response1.connectionLabel().orElse(null));
                }
                // first request with ALT_SVC is to get alt service, should be H2
                assertEquals(HTTP_2, h2resp2.version());
                checkStatus(200, h2resp2.statusCode());
                assertNotEquals(c1Label, h2resp2.connectionLabel().orElse(null));

                // second request should have ALT_SVC and create new connection with H3
                // it should not reuse the non-advertised connection
                List<CompletableFuture<HttpResponse<String>>> altResponses = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    altResponses.add(client.sendAsync(request2, BodyHandlers.ofString()));
                }
                String c2Label = null;
                for (int i = 0; i < altResponses.size(); i++) {
                    HttpResponse<String> response2 = altResponses.get(i).get();
                    System.out.printf("alt response [%s][%s]: %s%n", i,
                            response2.connectionLabel(),
                            response2);
                    assertEquals(HTTP_3, response2.version());
                    checkStatus(200, response2.statusCode());
                    assertNotEquals(response2.connectionLabel().get(), c1Label);
                    if (i == 0) {
                        c2Label = response2.connectionLabel().get();
                    }
                    assertEquals(c2Label, response2.connectionLabel().orElse(null));
                }

                // second set of requests should reuse a created connection
                HttpRequest request3 = req1Builder.copy().build();
                List<CompletableFuture<HttpResponse<String>>> mixResponses = new ArrayList<>();
                for (int i=0; i < 3; i++) {
                    mixResponses.add(client.sendAsync(request3, BodyHandlers.ofString()));
                    mixResponses.add(client.sendAsync(request2, BodyHandlers.ofString()));
                }
                for (int i=0; i < mixResponses.size(); i++) {
                    HttpResponse<String> response3 = mixResponses.get(i).get();
                    System.out.printf("mixed response [%s][%s] %s: %s%n", i,
                            response3.connectionLabel(),
                            response3.request().getOption(H3_DISCOVERY),
                            response3);
                    assertEquals(HTTP_3, response3.version());
                    checkStatus(200, response3.statusCode());
                    if (response3.request().getOption(H3_DISCOVERY).orElse(null) == ALT_SVC) {
                        assertEquals(c2Label, response3.connectionLabel().get());
                    } else {
                        assertEquals(c1Label, response3.connectionLabel().get());
                    }
                }
            } else if (http3Port == https2Port) {
                System.out.println("Testing with two alt services");
                // first - make a direct connection
                HttpRequest request1 = req1Builder.copy().build();

                // second, use the alt service
                HttpRequest request2 = req2Builder.copy().build();
                HttpResponse<String> h2resp2 = client.send(request2, BodyHandlers.ofString());
                assertEquals(HTTP_2, h2resp2.version());
                checkStatus(200, h2resp2.statusCode());

                // third, use ANY
                HttpRequest request3 = req2Builder.copy().setOption(H3_DISCOVERY, ANY).build();

                List<CompletableFuture<HttpResponse<String>>> directResponses = new ArrayList<>();
                List<CompletableFuture<HttpResponse<String>>> altResponses = new ArrayList<>();
                List<CompletableFuture<HttpResponse<String>>> anyResponses = new ArrayList<>();
                checkStatus(200, h2resp2.statusCode());
                for (int i=0; i<3; i++) {
                    anyResponses.add(client.sendAsync(request3, BodyHandlers.ofString()));
                    directResponses.add(client.sendAsync(request1, BodyHandlers.ofString()));
                    altResponses.add(client.sendAsync(request2, BodyHandlers.ofString()));
                }
                String c1Label = null;
                for (int i = 0; i < directResponses.size(); i++) {
                    HttpResponse<String> response1 = directResponses.get(i).get();
                    System.out.printf("direct response [%s][%s] %s: %s%n", i,
                            response1.connectionLabel(),
                            response1.request().getOption(H3_DISCOVERY),
                            response1);
                    assertEquals(HTTP_3, response1.version());
                    checkStatus(200, response1.statusCode());
                    if (i == 0) {
                        c1Label = response1.connectionLabel().get();
                    }
                    assertEquals(c1Label, response1.connectionLabel().orElse(null));
                }
                String c2Label = null;
                for (int i = 0; i < altResponses.size(); i++) {
                    HttpResponse<String> response2 = altResponses.get(i).get();
                    System.out.printf("alt response [%s][%s] %s: %s%n", i,
                            response2.connectionLabel(),
                            response2.request().getOption(H3_DISCOVERY),
                            response2);
                    assertEquals(HTTP_3, response2.version());
                    checkStatus(200, response2.statusCode());
                    if (i == 0) {
                        c2Label = response2.connectionLabel().get();
                    }
                    assertNotEquals(response2.connectionLabel().get(), h2resp2.connectionLabel().get());
                    assertNotEquals(response2.connectionLabel().get(), c1Label);
                    assertEquals(c2Label, response2.connectionLabel().orElse(null));
                }
                var expectedLabels = Set.of(c1Label, c2Label);
                for (int i = 0; i < anyResponses.size(); i++) {
                    HttpResponse<String> response3 = anyResponses.get(i).get();
                    System.out.printf("any response [%s][%s] %s: %s%n", i,
                            response3.connectionLabel(),
                            response3.request().getOption(H3_DISCOVERY),
                            response3);
                    assertEquals(HTTP_3, response3.version());
                    checkStatus(200, response3.statusCode());
                    assertNotEquals(response3.connectionLabel().get(), h2resp2.connectionLabel().get());
                    var label = response3.connectionLabel().orElse("");
                    assertTrue(expectedLabels.contains(label), "Unexpected label: %s not in %s"
                            .formatted(label, expectedLabels));
                }
            } else {
                System.out.println("WARNING: Couldn't create HTTP/3 server on same port! Can't test all...");
                // Get, get the alt service
                HttpRequest request2 = req2Builder.copy().build();
                // first request with ALT_SVC is to get alt service, should be H2
                HttpResponse<String> h2resp2 = client.send(request2, BodyHandlers.ofString());
                assertEquals(HTTP_2, h2resp2.version());
                checkStatus(200, h2resp2.statusCode());

                // second request should have ALT_SVC and create new connection with H3
                // it should not reuse the non-advertised connection
                HttpResponse<String> response2 = client.send(request2, BodyHandlers.ofString());
                assertEquals(HTTP_3, response2.version());
                checkStatus(200, response2.statusCode());
                assertNotEquals(response2.connectionLabel().get(), h2resp2.connectionLabel().get());

                // third request with ALT_SVC should reuse the same advertised
                // connection (from response2), regardless of same origin...
                HttpRequest request3 = req2Builder.copy().build();
                HttpResponse<String> response3 = client.send(request3, BodyHandlers.ofString());
                assertEquals(HTTP_3, response3.version());
                checkStatus(200, response3.statusCode());
                assertEquals(response3.connectionLabel().get(), response2.connectionLabel().get());
            }
        } finally {
            http3OnlyServer.stop();
            https2AltSvcServer.stop();
        }
    }

    static HttpClient getClient() {
        if (client == null) {
            client = HttpServerAdapters.createClientBuilderForH3()
                    .sslContext(sslContext)
                    .version(HTTP_3)
                    .build();
        }
        return client;
    }

    static void checkStatus(int expected, int found) throws Exception {
        if (expected != found) {
            System.err.printf("Test failed: wrong status code %d/%d\n",
                    expected, found);
            throw new RuntimeException("Test failed");
        }
    }

    static void checkStrings(String expected, String found) throws Exception {
        if (!expected.equals(found)) {
            System.err.printf("Test failed: wrong string %s/%s\n",
                    expected, found);
            throw new RuntimeException("Test failed");
        }
    }


    static <T> T logExceptionally(String desc, Throwable t) {
        System.out.println(desc + " failed: " + t);
        System.err.println(desc + " failed: " + t);
        if (t instanceof RuntimeException r) throw r;
        if (t instanceof Error e) throw e;
        throw new CompletionException(t);
    }

}
