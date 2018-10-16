/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jdk.test.lib.net.SimpleSSLContext;
import javax.net.ssl.SSLContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static java.lang.String.format;
import static java.lang.System.out;

/**
 * @test
 * @summary This test verifies that if an h2 connection going through a
 *          proxy P is downgraded to HTTP/1.1, then a new h2 request
 *          going to a different host through the same proxy will not
 *          be preemptively downgraded. That, is the stack should attempt
 *          a new h2 connection to the new host.
 * @bug 8196967
 * @library /test/lib http2/server
 * @build jdk.test.lib.net.SimpleSSLContext HttpServerAdapters DigestEchoServer HttpsTunnelTest
 * @modules java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.logging
 *          java.base/sun.net.www.http
 *          java.base/sun.net.www
 *          java.base/sun.net
 * @run main/othervm -Djdk.internal.httpclient.debug=true HttpsTunnelTest
 */

public class HttpsTunnelTest implements HttpServerAdapters {

    static final String data[] = {
        "Lorem ipsum",
        "dolor sit amet",
        "consectetur adipiscing elit, sed do eiusmod tempor",
        "quis nostrud exercitation ullamco",
        "laboris nisi",
        "ut",
        "aliquip ex ea commodo consequat." +
        "Duis aute irure dolor in reprehenderit in voluptate velit esse" +
        "cillum dolore eu fugiat nulla pariatur.",
        "Excepteur sint occaecat cupidatat non proident."
    };

    static final SSLContext context;
    static {
        try {
            context = new SimpleSSLContext().get();
            SSLContext.setDefault(context);
        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }

    HttpsTunnelTest() {
    }

    public HttpClient newHttpClient(ProxySelector ps) {
        HttpClient.Builder builder = HttpClient
                .newBuilder()
                .sslContext(context)
                .proxy(ps);
        return builder.build();
    }

    public static void main(String[] args) throws Exception {
        InetSocketAddress sa = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        HttpsServer server1 = HttpsServer.create(sa, 0);
        server1.setHttpsConfigurator(new HttpsConfigurator(context));
        HttpTestServer http1Server =
                HttpTestServer.of(server1);
        http1Server.addHandler(new HttpTestEchoHandler(), "/");
        http1Server.start();
        HttpTestServer http2Server = HttpTestServer.of(
                new Http2TestServer("localhost", true, 0));
        http2Server.addHandler(new HttpTestEchoHandler(), "/");
        http2Server.start();

        DigestEchoServer.TunnelingProxy proxy = DigestEchoServer.createHttpsProxyTunnel(
                DigestEchoServer.HttpAuthSchemeType.NONE);

        try {
            URI uri1 = new URI("https://" + http1Server.serverAuthority() + "/foo/https1");
            URI uri2 = new URI("https://" + http2Server.serverAuthority() + "/foo/https2");
            ProxySelector ps = ProxySelector.of(proxy.getProxyAddress());
                    //HttpClient.Builder.NO_PROXY;
            HttpsTunnelTest test = new HttpsTunnelTest();
            HttpClient client = test.newHttpClient(ps);
            out.println("Proxy is: " + ps.select(uri2));

            List<String> lines = List.of(Arrays.copyOfRange(data, 0, data.length));
            assert lines.size() == data.length;
            String body = lines.stream().collect(Collectors.joining("\r\n"));
            HttpRequest.BodyPublisher reqBody = HttpRequest.BodyPublishers.ofString(body);
            HttpRequest req1 = HttpRequest
                    .newBuilder(uri1)
                    .version(Version.HTTP_2)
                    .POST(reqBody)
                    .build();
            out.println("\nPosting to HTTP/1.1 server at: " + req1);
            HttpResponse<Stream<String>> response = client.send(req1, BodyHandlers.ofLines());
            out.println("Checking response...");
            if (response.statusCode() != 200) {
                throw new RuntimeException("Unexpected status code: " + response);
            }
            if (response.version() != Version.HTTP_1_1) {
                throw new RuntimeException("Unexpected protocol version: "
                        + response.version());
            }
            List<String> respLines = response.body().collect(Collectors.toList());
            if (!lines.equals(respLines)) {
                throw new RuntimeException("Unexpected response 1: " + respLines);
            }
            HttpRequest.BodyPublisher reqBody2 = HttpRequest.BodyPublishers.ofString(body);
            HttpRequest req2 = HttpRequest
                    .newBuilder(uri2)
                    .version(Version.HTTP_2)
                    .POST(reqBody2)
                    .build();
            out.println("\nPosting to HTTP/2 server at: " + req2);
            response = client.send(req2, BodyHandlers.ofLines());
            out.println("Checking response...");
            if (response.statusCode() != 200) {
                throw new RuntimeException("Unexpected status code: " + response);
            }
            if (response.version() != Version.HTTP_2) {
                throw new RuntimeException("Unexpected protocol version: "
                        + response.version());
            }
            respLines = response.body().collect(Collectors.toList());
            if (!lines.equals(respLines)) {
                throw new RuntimeException("Unexpected response 2: " + respLines);
            }
        } catch(Throwable t) {
            out.println("Unexpected exception: exiting: " + t);
            t.printStackTrace();
            throw t;
        } finally {
            proxy.stop();
            http1Server.stop();
            http2Server.stop();
        }
    }

}
