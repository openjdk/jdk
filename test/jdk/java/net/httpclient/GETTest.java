/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8327796
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters jdk.test.lib.net.SimpleSSLContext
 * @run main/othervm GETTest
 * @summary GET Test
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class GETTest implements HttpServerAdapters {

    static volatile boolean ok;
    static final String RESPONSE = "Hello world";

    public static void main(String[] args) throws Exception {
        test(Version.HTTP_1_1, false);
        test(Version.HTTP_2, false);
        test(Version.HTTP_1_1, true);
        test(Version.HTTP_2, true);
    }

    public static void test(Version version, boolean secure) throws Exception {

        ExecutorService e = Executors.newCachedThreadPool();
        Handler h = new Handler();
        SSLContext sslContext = secure ? new SimpleSSLContext().get() : null;
        HttpTestServer server = HttpTestServer.create(version, sslContext, e);
        HttpTestContext serverContext = server.addHandler(h, "/test/");
        server.start();

        var clientBuilder = HttpClient.newBuilder();
        if (sslContext != null) clientBuilder.sslContext(sslContext);
        HttpClient client = clientBuilder.build();

        try {
            String scheme = sslContext == null ? "http" : "https";
            String uri = scheme + "://" + server.serverAuthority() + "/test/foo/"+version;
            HttpRequest req = HttpRequest.GET(uri);

            System.out.println("\n\nSending request: " + req);
            String resp = client.send(req, BodyHandlers.ofString())
                                      .bodyWhen(r -> r.statusCode() == 200)
                                      .orElseThrow(() -> new RuntimeException("Failed"));
            // try some invalid URIs
            uri = "http!@://foo";
            try {
                req = HttpRequest.GET(uri);
                throw new RuntimeException("Invalid URI accepted");
            } catch (IllegalArgumentException ex1) {}

            uri = "ftp://foo.com/";
            try {
                req = HttpRequest.GET(uri);
                throw new RuntimeException("Invalid URI accepted");
            } catch (IllegalArgumentException ex2) {}
        } finally {
            server.stop();
            e.shutdownNow();
        }
        System.out.println("OK");
    }

   static class Handler implements HttpTestHandler {
        static volatile boolean ok;

        @Override
        public void handle(HttpTestExchange he) throws IOException {
            String method = he.getRequestMethod();
            InputStream is = he.getRequestBody();
            if (!method.equalsIgnoreCase("GET")) {
                he.sendResponseHeaders(500, 0);
                ok = false;
            } else { // GET
                he.sendResponseHeaders(200, RESPONSE.length());
                OutputStream os = he.getResponseBody();
                os.write(RESPONSE.getBytes(US_ASCII));
                os.close();
                ok = true;
            }
        }
   }
}
