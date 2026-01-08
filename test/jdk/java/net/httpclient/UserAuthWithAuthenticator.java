/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpResponse;
import javax.net.ssl.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.*;
import java.util.*;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import com.sun.net.httpserver.BasicAuthenticator;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8326949
 * @summary Authorization header is removed when a proxy Authenticator is set
 * @library /test/lib /test/jdk/java/net/httpclient /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.test.lib.net.IPSupport
 * @run junit UserAuthWithAuthenticator
 */
class UserAuthWithAuthenticator {

    private static final class AuthTestHandler implements HttpTestHandler {
        private volatile String authHeaderValue;

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                is.readAllBytes();
                authHeaderValue = t.getRequestHeaders()
                        .firstValue("Authorization")
                        .orElse("");
                String response = "Hello world";
                t.sendResponseHeaders(200, response.length());
                os.write(response.getBytes(US_ASCII));
                t.close();
            }
        }

    }

    @Test
    void h2Test() throws Exception {
        h2Test(true, true);
        h2Test(false, true);
        h2Test(true, false);
    }

    private static void h2Test(final boolean useHeader, boolean rightPassword) throws Exception {
        SSLContext sslContext = SimpleSSLContext.findSSLContext();
        try (ExecutorService executor = Executors.newCachedThreadPool();
             HttpTestServer server = HttpTestServer.of(new Http2TestServer(
                     InetAddress.getLoopbackAddress(),
                     "::1",
                     true,
                     0,
                     executor,
                     10,
                     null,
                     sslContext,
                     false));
             HttpClient client = HttpClient.newBuilder()
                     .sslContext(sslContext)
                     .executor(executor)
                     .authenticator(new ServerAuth())
                     .build()) {
            hXTest(useHeader, rightPassword, server, client, HttpClient.Version.HTTP_2);
        }
    }

    @Test
    void h3Test() throws Exception {
        h3Test(true, true);
        h3Test(false, true);
        h3Test(true, false);
    }

    private static void h3Test(final boolean useHeader, boolean rightPassword) throws Exception {
        SSLContext sslContext = SimpleSSLContext.findSSLContext();
        try (ExecutorService executor = Executors.newCachedThreadPool();
             HttpTestServer server = HttpTestServer.create(Http3DiscoveryMode.HTTP_3_URI_ONLY, sslContext, executor);
             HttpClient client = HttpServerAdapters.createClientBuilderForH3()
                     .sslContext(sslContext)
                     .executor(executor)
                     .authenticator(new ServerAuth())
                     .build()) {
            hXTest(useHeader, rightPassword, server, client, HttpClient.Version.HTTP_3);
        }
    }

    /**
     * @param useHeader If {@code true}, we expect the authenticator was not called and the user set header used.
     *                  If {@code false}, authenticator must be called and the user set header discarded.
     * @param rightPassword If {@code true}, we expect the authentication to succeed with {@code 200 OK}.
     *                      If {@code false}, then an error should be returned.
     */
    private static void hXTest(
            final boolean useHeader,
            boolean rightPassword,
            HttpTestServer server,
            HttpClient client,
            HttpClient.Version version)
            throws Exception {

        AuthTestHandler handler = new AuthTestHandler();
        var context = server.addHandler(handler, "/test1");
        context.setAuthenticator(new BasicAuthenticator("realm") {
            public boolean checkCredentials(String username, String password) {
                if (useHeader) {
                    return username.equals("user") && password.equals("pwd");
                } else {
                    return username.equals("serverUser") && password.equals("serverPwd");
                }
            }
        });
        server.start();

        URI uri = URIBuilder.newBuilder()
                .scheme("https")
                .host(server.getAddress().getAddress())
                .port(server.getAddress().getPort())
                .path("/test1/foo.txt")
                .build();

        var authHeaderValue = authHeaderValue("user", rightPassword ? "pwd" : "wrongPwd");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .version(version)
                .header(useHeader ? "Authorization" : "X-Ignore", authHeaderValue)
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        var sa = (ServerAuth) client.authenticator().orElseThrow();
        if (!useHeader) {
            assertEquals(200, resp.statusCode(), "Expected 200 response");
            assertNotEquals(handler.authHeaderValue, authHeaderValue, "Expected user set header to not be set");
            assertEquals(handler.authHeaderValue, ServerAuth.AUTH_HEADER_VALUE, "Expected auth value from Authenticator");
            assertTrue(sa.called, "Expected authenticator to be called");
        } else if (rightPassword) {
            assertEquals(200, resp.statusCode(), "Expected 200 response");
            assertEquals(authHeaderValue, handler.authHeaderValue, "Expected user set header to be set");
            assertFalse(sa.called, "Expected authenticator not to be called");
        } else {
            assertEquals(401, resp.statusCode(), "Expected 401 response");
            assertFalse(sa.called, "Expected authenticator not to be called");
        }

    }

    private static final String data = "0123456789";

    private static final String data1 = "ABCDEFGHIJKL";

    private static final String[] proxyResponses = {
        "HTTP/1.1 407 Proxy Authentication Required\r\n"+
        "Content-Length: 0\r\n" +
        "Proxy-Authenticate: Basic realm=\"Access to the proxy\"\r\n\r\n"
        ,
        "HTTP/1.1 200 OK\r\n"+
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Content-Length: " + data.length() + "\r\n\r\n" + data
    };

    private static final String[] proxyWithErrorResponses = {
        "HTTP/1.1 407 Proxy Authentication Required\r\n"+
        "Content-Length: 0\r\n" +
        "Proxy-Authenticate: Basic realm=\"Access to the proxy\"\r\n\r\n"
        ,
        "HTTP/1.1 407 Proxy Authentication Required\r\n"+
        "Content-Length: 0\r\n" +
        "Proxy-Authenticate: Basic realm=\"Access to the proxy\"\r\n\r\n"
    };

    private static final String[] serverResponses = {
        "HTTP/1.1 200 OK\r\n"+
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Content-Length: " + data1.length() + "\r\n\r\n" + data1
    };

    private static final String[] authenticatorResponses = {
        "HTTP/1.1 401 Authentication Required\r\n"+
        "Content-Length: 0\r\n" +
        "WWW-Authenticate: Basic realm=\"Access to the server\"\r\n\r\n"
        ,
        "HTTP/1.1 200 OK\r\n"+
        "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
        "Server: Apache/1.3.14 (Unix)\r\n" +
        "Content-Length: " + data1.length() + "\r\n\r\n" + data1
    };

    @Test
    void h1TestServerWithProxy() throws IOException, InterruptedException {
        ProxyAuth p = new ProxyAuth();
        try (var proxyMock = new Mocker(proxyResponses);
             var client = HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .proxy(new ProxySel(proxyMock.getPort()))
                .authenticator(p)
                .build()) {

            var authHeaderValue = authHeaderValue("user", "pwd");
            var request = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1/some_url"))
                .setHeader("User-Agent", "myUserAgent")
                .setHeader("Authorization", authHeaderValue)
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(p.called, "Proxy authenticator was not called");
            assertEquals(data, response.body());
            var proxyStr = proxyMock.requests.get(1);

            assertContains(proxyStr, "/some_url");
            assertPattern(".*^Proxy-Authorization:.*\\Q" + authHeaderValue + "\\E.*", proxyStr);
            assertPattern(".*^User-Agent:.*myUserAgent.*", proxyStr);
            assertPattern(".*^Authorization:.*Basic.*", proxyStr);
        }
    }

    @Test
    void h1TestServerWithProxyError() throws IOException, InterruptedException {
        ProxyAuth p = new ProxyAuth();
        try (var proxyMock = new Mocker(proxyWithErrorResponses);
             var client = HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .proxy(new ProxySel(proxyMock.getPort()))
                .authenticator(p)
                .build()) {

            var authHeaderValue = authHeaderValue("user", "wrong");
            var request = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1/some_url"))
                .setHeader("User-Agent", "myUserAgent")
                .setHeader("Proxy-Authorization", authHeaderValue)
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            var proxyStr = proxyMock.requests.getFirst();
            assertEquals(407, response.statusCode());
            assertPattern(".*^Proxy-Authorization:.*\\Q" + authHeaderValue + "\\E.*", proxyStr);
            assertFalse(p.called, "Proxy Auth should not have been called");
        }
    }

    @Test
    void h1TestServerOnly() throws IOException, InterruptedException {
        try (var serverMock = new Mocker(serverResponses);
             var client = HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build()) {

            var authHeaderValue = authHeaderValue("user", "pwd");
            var request = HttpRequest.newBuilder().uri(URI.create(serverMock.baseURL() + "/some_serv_url"))
                .setHeader("User-Agent", "myUserAgent")
                .setHeader("Authorization", authHeaderValue)
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals(data1, response.body());

            var serverStr = serverMock.requests.getFirst();
            assertContains(serverStr, "/some_serv_url");
            assertPattern(".*^User-Agent:.*myUserAgent.*", serverStr);
            assertPattern(".*^Authorization:.*\\Q" + authHeaderValue + "\\E.*", serverStr);
        }
    }

    /**
     * A regression test for existing behavior.
     */
    @Test
    void h1TestServerOnlyAuthenticator() throws IOException, InterruptedException {
        try (var serverMock = new Mocker(authenticatorResponses);
             var client = HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .authenticator(new ServerAuth())
                .build()) {

            // credentials set in the server authenticator
            var request = HttpRequest.newBuilder().uri(URI.create(serverMock.baseURL() + "/some_serv_url"))
                .setHeader("User-Agent", "myUserAgent")
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals(data1, response.body());

            var serverStr = serverMock.requests.get(1);
            assertContains(serverStr, "/some_serv_url");
            assertPattern(".*^User-Agent:.*myUserAgent.*", serverStr);
            assertPattern(".*^Authorization:.*\\Q" + authHeaderValue("serverUser", "serverPwd") + "\\E.*", serverStr);
        }
    }

    private static final class Mocker extends Thread implements AutoCloseable {
        private final ServerSocket ss;
        private final String[] responses;
        private final List<String> requests;
        private volatile InputStream in;
        private volatile OutputStream out;
        private volatile Socket s = null;

        private Mocker(String[] responses) throws IOException {
            this.ss = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
            this.responses = responses;
            this.requests = new LinkedList<>();
            start();
        }

        @Override
        public void close() {
            close(ss, s, in, out);
        }

        private static void close(Closeable... clarray) {
            for (Closeable c : clarray) {
                try {
                    c.close();
                } catch (Exception e) {
                    // Do nothing
                }
            }
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
                // noinspection StringConcatenationInLoop
                req += (char) x;
            }
            return req;
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
                // noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
        }
    }

    private static final class ProxySel extends ProxySelector {

        private final int port;

        private ProxySel(int port) {
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

    private static final class ProxyAuth extends Authenticator {

        private volatile boolean called = false;

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            called = true;
            return new PasswordAuthentication("proxyUser", "proxyPwd".toCharArray());
        }

    }

    private static final class ServerAuth extends Authenticator {

        private volatile boolean called = false;

        private static final String USER = "serverUser";

        private static final String PASS = "serverPwd";

        private static final String AUTH_HEADER_VALUE = authHeaderValue(USER, PASS);

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            called = true;
            if (getRequestorType() != RequestorType.SERVER) {
                // We only want to handle server authentication here
                return null;
            }
            return new PasswordAuthentication(USER, PASS.toCharArray());
        }

    }

    private static String authHeaderValue(String username, String password) {
        String credentials = username + ':' + password;
        return "Basic " + java.util.Base64.getEncoder().encodeToString(credentials.getBytes(US_ASCII));
    }

    private static void assertContains(String container, String containee) {
        assertTrue(container.contains(containee), String.format("Error: expected %s Got %s", container, containee));
    }

    private static void assertPattern(String pattern, String candidate) {
        Pattern pat = Pattern.compile(pattern, Pattern.DOTALL | Pattern.MULTILINE);
        Matcher matcher = pat.matcher(candidate);
        assertTrue(matcher.matches(), String.format("Error: expected %s Got %s", pattern, candidate));
    }

}
