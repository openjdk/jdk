/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=ssl,requests,responses,errors
 *                     -Djdk.internal.httpclient.debug=true
 *                     H3ConnectionPoolTest
 */

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2EchoHandler;
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

public class H3ConnectionPoolTest implements HttpServerAdapters {

    private static final String CLASS_NAME = H3ConnectionPoolTest.class.getSimpleName();

    static int http3Port, https2Port;
    static Http3TestServer http3OnlyServer;
    static Http2TestServer https2AltSvcServer;
    static HttpClient client = null;
    static SSLContext sslContext;
    static volatile String http3URIString, https2URIString, http3AltSvcURIString;

    static void initialize() throws Exception {
        try {
            SimpleSSLContext sslct = new SimpleSSLContext();
            sslContext = sslct.get();
            client = null;
            client = getClient();

            // server that only supports HTTP/3
            http3OnlyServer = new Http3TestServer(sslContext);
            http3OnlyServer.addHandler("/"+CLASS_NAME+"/http3/", new Http2EchoHandler());
            System.out.println("HTTP/3 server started at:" + http3OnlyServer.serverAuthority());

            // server that supports both HTTP/2 and HTTP/3, with HTTP/3 on an altSvc port.
            https2AltSvcServer = new Http2TestServer(true, sslContext);
            https2AltSvcServer.enableH3AltServiceOnSamePort();
            https2AltSvcServer.addHandler(new Http2EchoHandler(), "/" + CLASS_NAME + "/https2/");
            https2Port = https2AltSvcServer.getAddress().getPort();
            http3Port = https2AltSvcServer.getH3AltService()
                    .map(Http3TestServer::getAddress).stream()
                    .mapToInt(InetSocketAddress::getPort).findFirst()
                    .getAsInt();

            http3URIString = "https://" + http3OnlyServer.serverAuthority() + "/" + CLASS_NAME + "/http3/foo/";
            https2URIString = "https://" + https2AltSvcServer.serverAuthority() + "/" + CLASS_NAME + "/https2/bar/";
            http3AltSvcURIString = https2URIString
                    .replace(":" + https2Port + "/", ":" + http3Port + "/");
            System.out.println("HTTP/2 server started at: " + https2AltSvcServer.serverAuthority());
            System.out.println(" with HTTP/3 endpoint at: " + URI.create(http3AltSvcURIString).getRawAuthority());



            http3OnlyServer.start();
            https2AltSvcServer.start();
        } catch (Throwable e) {
            System.err.println("Throwing now");
            e.printStackTrace();
            throw e;
        }
    }

    static final List<CompletableFuture<Long>> cfs = Collections
        .synchronizedList( new LinkedList<>());


    @Test
    public static void testH3Only() throws Exception {
        initialize();
        try (HttpClient client = getClient()) {
            var reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(http3URIString))
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
            HttpRequest request2 = reqBuilder.copy()
                    .setOption(H3_DISCOVERY, ANY)
                    .build();
            HttpResponse<String> response2 = client.send(request2, BodyHandlers.ofString());
            HttpRequest request3 = reqBuilder.copy()
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .build();
            HttpResponse<String> response3 = client.send(request3, BodyHandlers.ofString());
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
    public static void testH2H3() throws Exception {
        initialize();
        try (HttpClient client = getClient()) {
            var req1Builder = HttpRequest.newBuilder()
                    .uri(URI.create(http3AltSvcURIString))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET();
            var req2Builder = HttpRequest.newBuilder()
                    .uri(URI.create(https2URIString))
                    .setOption(H3_DISCOVERY, ALT_SVC)
                    .version(HTTP_3)
                    .GET();

            if (http3Port == https2Port) {
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
            } else {
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
                HttpRequest request4 = req2Builder.copy().build();
                HttpResponse<String> response4 = client.send(request4, BodyHandlers.ofString());
                assertEquals(HTTP_3, response4.version());
                checkStatus(200, response4.statusCode());
                assertEquals(response4.connectionLabel().get(), response2.connectionLabel().get());
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

    static URI getURI(boolean altsvc) {
        if (altsvc)
            return URI.create(https2URIString);
        else
            return URI.create(http3URIString);
    }

    static void checkStatus(int expected, int found) throws Exception {
        if (expected != found) {
            System.err.printf ("Test failed: wrong status code %d/%d\n",
                expected, found);
            throw new RuntimeException("Test failed");
        }
    }

    static void checkStrings(String expected, String found) throws Exception {
        if (!expected.equals(found)) {
            System.err.printf ("Test failed: wrong string %s/%s\n",
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
