/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8326949
 * @summary Authorization header is removed when a proxy Authenticator is set
 * @library /test/lib /test/jdk/java/net/httpclient /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.test.lib.net.IPSupport
 *
 * @modules java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.logging
 *          java.base/sun.net.www.http
 *          java.base/sun.net.www
 *          java.base/sun.net
 *
 * @run main/othervm UserAuthWithAuthenticator
 */

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import javax.net.ssl.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.*;
import java.util.*;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.net.IPSupport;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import com.sun.net.httpserver.BasicAuthenticator;

import jdk.test.lib.net.URIBuilder;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class UserAuthWithAuthenticator {
    private static final String AUTH_PREFIX = "Basic ";

    static class AuthTestHandler implements HttpTestHandler {
        volatile String authValue;
        final String response = "Hello world";

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                byte[] bytes = is.readAllBytes();
                authValue = t.getRequestHeaders()
                        .firstValue("Authorization")
                        .orElse(AUTH_PREFIX)
                        .substring(AUTH_PREFIX.length());
                t.sendResponseHeaders(200, response.length());
                os.write(response.getBytes(US_ASCII));
                t.close();
            }
        }

        String authValue() {return authValue;}
    }

    // if useHeader is true, we expect the Authenticator was not called
    // and the user set header used. If false, Authenticator must
    // be called and the user set header not used.

    // If rightPassword is true we expect the authentication to succeed and 200 OK
    // If false, then an error should be returned.

    static void h2Test(final boolean useHeader, boolean rightPassword) throws Exception {
        SSLContext ctx;
        HttpTestServer h2s = null;
        HttpClient client = null;
        ExecutorService ex=null;
        try {
            ctx = new SimpleSSLContext().get();
            ex = Executors.newCachedThreadPool();
            InetAddress addr = InetAddress.getLoopbackAddress();

            h2s = HttpTestServer.of(new Http2TestServer(addr, "::1", true, 0, ex,
                    10, null, ctx, false));
            AuthTestHandler h = new AuthTestHandler();
            var context = h2s.addHandler(h, "/test1");
            context.setAuthenticator(new BasicAuthenticator("realm") {
                public boolean checkCredentials(String username, String password) {
                    if (useHeader) {
                        return username.equals("user") && password.equals("pwd");
                    } else {
                        return username.equals("serverUser") && password.equals("serverPwd");
                    }
                }
            });
            h2s.start();

            int port = h2s.getAddress().getPort();
            ServerAuth sa = new ServerAuth();
            var plainCreds = rightPassword? "user:pwd" : "user:wrongPwd";
            var encoded = java.util.Base64.getEncoder().encodeToString(plainCreds.getBytes(US_ASCII));

            URI uri = URIBuilder.newBuilder()
                 .scheme("https")
                 .host(addr.getHostAddress())
                 .port(port)
                 .path("/test1/foo.txt")
                 .build();

            HttpClient.Builder builder = HttpClient.newBuilder()
                    .sslContext(ctx)
                    .executor(ex);

            builder.authenticator(sa);
            client = builder.build();

            HttpRequest req = HttpRequest.newBuilder(uri)
                    .version(HttpClient.Version.HTTP_2)
                    .header(useHeader ? "Authorization" : "X-Ignore", AUTH_PREFIX + encoded)
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (!useHeader) {
                assertTrue(resp.statusCode() == 200, "Expected 200 response");
                assertTrue(!h.authValue().equals(encoded), "Expected user set header to not be set");
                assertTrue(h.authValue().equals(sa.authValue()), "Expected auth value from Authenticator");
                assertTrue(sa.wasCalled(), "Expected authenticator to be called");
                System.out.println("h2Test: using authenticator OK");
            } else if (rightPassword) {
                assertTrue(resp.statusCode() == 200, "Expected 200 response");
                assertTrue(h.authValue().equals(encoded), "Expected user set header to be set");
                assertTrue(!sa.wasCalled(), "Expected authenticator not to be called");
                System.out.println("h2Test: using user set header OK");
            } else {
                assertTrue(resp.statusCode() == 401, "Expected 401 response");
                assertTrue(!sa.wasCalled(), "Expected authenticator not to be called");
                System.out.println("h2Test: using user set header with wrong password OK");
            }
        } finally {
            if (h2s != null)
                h2s.stop();
            if (client != null)
                client.close();
            if (ex != null)
                ex.shutdown();
        }
    }

    static final String data = "0123456789";

    static final String data1 = "ABCDEFGHIJKL";

    static final String[] proxyResponses = {
        "HTTP/1.1 407 Proxy Authentication Required\r\n"+
        "Content-Length: 0\r\n" +
        "Proxy-Authenticate: Basic realm=\"Access to the proxy\"\r\n\r\n"
        ,
        "HTTP/1.1 200 OK\r\n"+
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Content-Length: " + data.length() + "\r\n\r\n" + data
    };

    static final String[] proxyWithErrorResponses = {
        "HTTP/1.1 407 Proxy Authentication Required\r\n"+
        "Content-Length: 0\r\n" +
        "Proxy-Authenticate: Basic realm=\"Access to the proxy\"\r\n\r\n"
        ,
        "HTTP/1.1 407 Proxy Authentication Required\r\n"+
        "Content-Length: 0\r\n" +
        "Proxy-Authenticate: Basic realm=\"Access to the proxy\"\r\n\r\n"
    };

    static final String[] serverResponses = {
        "HTTP/1.1 200 OK\r\n"+
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Content-Length: " + data1.length() + "\r\n\r\n" + data1
    };

    static final String[] authenticatorResponses = {
        "HTTP/1.1 401 Authentication Required\r\n"+
        "Content-Length: 0\r\n" +
        "WWW-Authenticate: Basic realm=\"Access to the server\"\r\n\r\n"
        ,
        "HTTP/1.1 200 OK\r\n"+
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Content-Length: " + data1.length() + "\r\n\r\n" + data1
    };

    public static void main(String[] args) throws Exception {
        testServerOnly();
        testServerWithProxy();
        testServerWithProxyError();
        testServerOnlyAuthenticator();
        h2Test(true, true);
        h2Test(false, true);
        h2Test(true, false);
    }

    static void testServerWithProxy() throws IOException, InterruptedException {
        Mocker proxyMock = new Mocker(proxyResponses);
        proxyMock.start();
        ProxyAuth p = new ProxyAuth();
        try (var client = HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .proxy(new ProxySel(proxyMock.getPort()))
                .authenticator(p)
                .build()) {

            var plainCreds = "user:pwd";
            var encoded = java.util.Base64.getEncoder().encodeToString(plainCreds.getBytes(US_ASCII));
            var request = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1/some_url"))
                .setHeader("User-Agent", "myUserAgent")
                .setHeader("Authorization", AUTH_PREFIX + encoded)
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(p.wasCalled(), "Proxy authenticator was not called");
            assertEquals(data, response.body());
            var proxyStr = proxyMock.getRequest(1);

            assertContains(proxyStr, "/some_url");
            assertPattern(".*^Proxy-Authorization:.*Basic " + encoded + ".*", proxyStr);
            assertPattern(".*^User-Agent:.*myUserAgent.*", proxyStr);
            assertPattern(".*^Authorization:.*Basic.*", proxyStr);
            System.out.println("testServerWithProxy: OK");
        } finally {
            proxyMock.stopMocker();
        }
    }

    static void testServerWithProxyError() throws IOException, InterruptedException {
        Mocker proxyMock = new Mocker(proxyWithErrorResponses);
        proxyMock.start();
        ProxyAuth p = new ProxyAuth();
        try (var client = HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .proxy(new ProxySel(proxyMock.getPort()))
                .authenticator(p)
                .build()) {

            var badCreds = "user:wrong";
            var encoded1 = java.util.Base64.getEncoder().encodeToString(badCreds.getBytes(US_ASCII));
            var request = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1/some_url"))
                .setHeader("User-Agent", "myUserAgent")
                .setHeader("Proxy-Authorization", AUTH_PREFIX + encoded1)
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            var proxyStr = proxyMock.getRequest(0);
            assertEquals(407, response.statusCode());
            assertPattern(".*^Proxy-Authorization:.*Basic " + encoded1 + ".*", proxyStr);
            assertTrue(!p.wasCalled(), "Proxy Auth should not have been called");
            System.out.println("testServerWithProxyError: OK");
        } finally {
            proxyMock.stopMocker();
        }
    }

    static void testServerOnly() throws IOException, InterruptedException {
        Mocker serverMock = new Mocker(serverResponses);
        serverMock.start();
        try (var client = HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build()) {

            var plainCreds = "user:pwd";
            var encoded = java.util.Base64.getEncoder().encodeToString(plainCreds.getBytes(US_ASCII));
            var request = HttpRequest.newBuilder().uri(URI.create(serverMock.baseURL() + "/some_serv_url"))
                .setHeader("User-Agent", "myUserAgent")
                .setHeader("Authorization", AUTH_PREFIX + encoded)
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals(data1, response.body());

            var serverStr = serverMock.getRequest(0);
            assertContains(serverStr, "/some_serv_url");
            assertPattern(".*^User-Agent:.*myUserAgent.*", serverStr);
            assertPattern(".*^Authorization:.*Basic " + encoded + ".*", serverStr);
            System.out.println("testServerOnly: OK");
        } finally {
            serverMock.stopMocker();
        }
    }

    // This is effectively a regression test for existing behavior
    static void testServerOnlyAuthenticator() throws IOException, InterruptedException {
        Mocker serverMock = new Mocker(authenticatorResponses);
        serverMock.start();
        try (var client = HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .authenticator(new ServerAuth())
                .build()) {

            // credentials set in the server authenticator
            var plainCreds = "serverUser:serverPwd";
            var encoded = java.util.Base64.getEncoder().encodeToString(plainCreds.getBytes(US_ASCII));
            var request = HttpRequest.newBuilder().uri(URI.create(serverMock.baseURL() + "/some_serv_url"))
                .setHeader("User-Agent", "myUserAgent")
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals(data1, response.body());

            var serverStr = serverMock.getRequest(1);
            assertContains(serverStr, "/some_serv_url");
            assertPattern(".*^User-Agent:.*myUserAgent.*", serverStr);
            assertPattern(".*^Authorization:.*Basic " + encoded + ".*", serverStr);
            System.out.println("testServerOnlyAuthenticator: OK");
        } finally {
            serverMock.stopMocker();
        }
    }

    static void close(Closeable... clarray) {
        for (Closeable c : clarray) {
            try {
                c.close();
            } catch (Exception e) {}
        }
    }

    static class Mocker extends Thread {
        final ServerSocket ss;
        final String[] responses;
        volatile List<String> requests;
        volatile InputStream in;
        volatile OutputStream out;
        volatile Socket s = null;

        public Mocker(String[] responses) throws IOException {
            this.ss = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
            this.responses = responses;
            this.requests = new LinkedList<>();
        }

        public void stopMocker() {
            close(ss, s, in, out);
        }

        public int getPort() {
            return ss.getLocalPort();
        }

        public String baseURL() {
            try {
                return URIBuilder.newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(getPort())
                    .build()
                    .toString();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        private String readRequest() throws IOException {
            String req = "";
            while (!req.endsWith("\r\n\r\n")) {
                int x = in.read();
                if (x == -1) {
                    s.close();
                    s = ss.accept();
                    in = s.getInputStream();
                    out = s.getOutputStream();
                }
                req += (char)x;
            }
            return req;
        }

        public String getRequest(int i) {
            return requests.get(i);
        }

        public void run() {
            try {
                int index=0;
                s = ss.accept();
                in = s.getInputStream();
                out = s.getOutputStream();
                while (index < responses.length) {
                    requests.add(readRequest());
                    out.write(responses[index++].getBytes(US_ASCII));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class ProxySel extends ProxySelector {
        final int port;

        ProxySel(int port) {
            this.port = port;
        }
        @Override
        public List<Proxy> select(URI uri) {
          return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(
              InetAddress.getLoopbackAddress(), port)));
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {}

    }

    static class ProxyAuth extends Authenticator {
        private volatile boolean called = false;

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            called = true;
            return new PasswordAuthentication("proxyUser", "proxyPwd".toCharArray());
        }

        boolean wasCalled() {
            return called;
        }
    }

    static class ServerAuth extends Authenticator {
        private volatile boolean called = false;

        private static String USER = "serverUser";
        private static String PASS = "serverPwd";

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            called = true;
            if (getRequestorType() != RequestorType.SERVER) {
                // We only want to handle server authentication here
                return null;
            }
            return new PasswordAuthentication(USER, PASS.toCharArray());
        }

        String authValue() {
            var plainCreds = USER + ":" + PASS;
            return java.util.Base64.getEncoder().encodeToString(plainCreds.getBytes(US_ASCII));
        }

        boolean wasCalled() {
            return called;
        }
    }

    static void assertTrue(boolean assertion, String failMsg) {
        if (!assertion) {
            throw new RuntimeException(failMsg);
        }
    }

    static void assertEquals(int a, int b) {
        if (a != b) {
            String msg = String.format("Error: expected %d Got %d", a, b);
            throw new RuntimeException(msg);
        }
    }

    static void assertEquals(String s1, String s2) {
        if (!s1.equals(s2)) {
            String msg = String.format("Error: expected %s Got %s", s1, s2);
            throw new RuntimeException(msg);
        }
    }

    static void assertContains(String container, String containee) {
        if (!container.contains(containee)) {
            String msg = String.format("Error: expected %s Got %s", container, containee);
            throw new RuntimeException(msg);
        }
    }

    static void assertPattern(String pattern, String candidate) {
        Pattern pat = Pattern.compile(pattern, Pattern.DOTALL | Pattern.MULTILINE);
        Matcher matcher = pat.matcher(candidate);
        if (!matcher.matches()) {
            String msg = String.format("Error: expected %s Got %s", pattern, candidate);
            throw new RuntimeException(msg);
        }
    }
}
