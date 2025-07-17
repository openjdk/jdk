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
 * @bug 8087112
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters jdk.test.lib.net.SimpleSSLContext
 * @run main/othervm BasicAuthTest
 * @summary Basic Authentication Test
 */

import com.sun.net.httpserver.BasicAuthenticator;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.PasswordAuthentication;
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

public class BasicAuthTest implements HttpServerAdapters {

    static volatile boolean ok;
    static final String RESPONSE = "Hello world";
    static final String POST_BODY = "This is the POST body 123909090909090";

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
        HttpTestContext serverContext = server.addHandler(h,"/test/");
        ServerAuth sa = new ServerAuth("foo realm");
        serverContext.setAuthenticator(sa);
        server.start();
        System.out.println("Server auth = " + server.serverAuthority());

        ClientAuth ca = new ClientAuth();
        var clientBuilder = HttpClient.newBuilder();
        if (sslContext != null) clientBuilder.sslContext(sslContext);
        HttpClient client = clientBuilder.authenticator(ca).build();

        try {
            String scheme = sslContext == null ? "http" : "https";
            URI uri = new URI(scheme + "://" + server.serverAuthority() + "/test/foo/"+version);
            var builder = HttpRequest.newBuilder(uri);
            HttpRequest req = builder.copy().GET().build();

            System.out.println("\n\nSending request: " + req);
            HttpResponse resp = client.send(req, BodyHandlers.ofString());
            ok = resp.statusCode() == 200 && resp.body().equals(RESPONSE);

            var count = ca.count;
            if (!ok || count != 1)
                throw new RuntimeException("Test failed: ca.count=" + count);

            // repeat same request, should succeed but no additional authenticator calls

            System.out.println("\n\nRepeat request: " + req);
            resp = client.send(req, BodyHandlers.ofString());
            ok = resp.statusCode() == 200 && resp.body().equals(RESPONSE);

            count = ca.count;
            if (!ok || count != 1)
                throw new RuntimeException("Test failed: ca.count=" + count);

            // try a POST

            req = builder.copy().POST(BodyPublishers.ofString(POST_BODY))
                             .build();
            System.out.println("\n\nSending POST request: " + req);
            resp = client.send(req, BodyHandlers.ofString());
            ok = resp.statusCode() == 200;

            count = ca.count;
            if (!ok || count != 1)
                throw new RuntimeException("Test failed: ca.count=" + count);


        } finally {
            server.stop();
            e.shutdownNow();
        }
        System.out.println("OK");
    }

    static class ServerAuth extends BasicAuthenticator {

        ServerAuth(String realm) {
            super(realm);
        }

        @Override
        public boolean checkCredentials(String username, String password) {
            if (!"user".equals(username) || !"passwd".equals(password)) {
                return false;
            }
            return true;
        }

    }

    static class ClientAuth extends java.net.Authenticator {
        volatile int count = 0;

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            count++;
            return new PasswordAuthentication("user", "passwd".toCharArray());
        }
    }

   static class Handler implements HttpTestHandler {
        static volatile boolean ok;

        @Override
        public void handle(HttpTestExchange he) throws IOException {
            String method = he.getRequestMethod();
            InputStream is = he.getRequestBody();
            if (method.equalsIgnoreCase("POST")) {
                String requestBody = new String(is.readAllBytes(), US_ASCII);
                if (!requestBody.equals(POST_BODY)) {
                    he.sendResponseHeaders(500, 0);
                    ok = false;
                } else {
                    he.sendResponseHeaders(200, 0);
                    ok = true;
                }
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
