/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.HttpClientAccess;
import jdk.internal.net.http.AltServicesRegistry;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import static jdk.internal.net.http.AltServicesRegistry.AltService;
import static java.net.http.HttpResponse.BodyHandlers.ofString;

/*
 * @test
 * @summary This test verifies alt-svc registry updates
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build java.net.http/jdk.internal.net.http.HttpClientAccess
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 * @modules java.net.http/jdk.internal.net.http
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.base/jdk.internal.net.quic
 *          java.net.http/jdk.internal.net.http.quic
 *          java.net.http/jdk.internal.net.http.quic.packets
 *          java.net.http/jdk.internal.net.http.quic.frames
 *          java.net.http/jdk.internal.net.http.quic.streams
 *          java.net.http/jdk.internal.net.http.http3.streams
 *          java.net.http/jdk.internal.net.http.http3.frames
 *          java.net.http/jdk.internal.net.http.http3
 *          java.net.http/jdk.internal.net.http.qpack
 *          java.net.http/jdk.internal.net.http.qpack.readers
 *          java.net.http/jdk.internal.net.http.qpack.writers
 *          java.logging
 *          java.base/sun.net.www.http
 *          java.base/sun.net.www
 *          java.base/sun.net
 *          java.base/jdk.internal.util
 * @run testng/othervm
 *                    -Dtest.requiresHost=true
 *                   -Djdk.httpclient.HttpClient.log=headers
 *                   -Djdk.internal.httpclient.disableHostnameVerification
 *                   -Djdk.internal.httpclient.debug=true
 *                    AltSvcRegistryTest
 */


public class AltSvcRegistryTest implements HttpServerAdapters {

    static HttpTestServer https2Server;
    static String https2URI;
    static HttpClient client;
    private static final SSLContext server = SimpleSSLContext.findSSLContext();

    @BeforeTest
    public void setUp() throws Exception {
        getRegistry();
        final ExecutorService executor = Executors.newCachedThreadPool();
        https2Server = HttpServerAdapters.HttpTestServer.of(
            new Http2TestServer("localhost", true, 0, executor, 50, null, server, true));
        https2Server.addHandler(new AltSvcRegistryTestHandler("https", https2Server), "/");
        https2Server.start();
        https2URI = "https://" + https2Server.serverAuthority() + "/";


    }

    static AltServicesRegistry getRegistry() {
        client = HttpClient.newBuilder()
                           .sslContext(server)
                           .version(HttpClient.Version.HTTP_2)
                           .build();
        return HttpClientAccess.getRegistry(client);
    }
    @Test
    public void testAltSvcRegistry() throws URISyntaxException, IOException, InterruptedException {
        AltServicesRegistry registry = getRegistry();
        HttpRequest request = HttpRequest.newBuilder(new URI(https2URI))
                                         .GET()
                                         .build();
        HttpResponse<String> response = client.send(request, ofString());
        assert response.statusCode() == 200;
        List<AltService> h3service = registry.lookup(URI.create(https2URI), "h3")
                .toList();
        System.out.println("h3 services: " + h3service);
        assert h3service.stream().anyMatch( alt -> alt.alpn().equals("h3")
                && alt.host().equals("www.example.com")
                && alt.port() == 443
                && alt.isPersist());
        assert h3service.stream().anyMatch( alt -> alt.alpn().equals("h3")
                && alt.host().equals(request.uri().getHost())
                && alt.port() == 4567
                && !alt.isPersist());

        List<AltService> h34service = registry.lookup(URI.create(https2URI), "h3-34")
                .toList();
        System.out.println("h3-34 services: " + h34service);
        assert h34service.stream().noneMatch( alt -> alt.alpn().equals("h3-34"));
    }

    static class AltSvcRegistryTestHandler implements HttpTestHandler {
        final String scheme;
        final HttpTestServer server;

        AltSvcRegistryTestHandler(String scheme, HttpTestServer server) {
            this.scheme = scheme;
            this.server = server;
        }

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                var altsvc = """
                        h3-34=":5678", h3="www.example.com:443"; persist=1, h3=":4567"; persist=0""" ;
                t.getResponseHeaders().addHeader("alt-svc", altsvc.trim());
                byte[] bytes = is.readAllBytes();
                t.sendResponseHeaders(200, 10);
                os.write(bytes);
            }
        }
    }
}
