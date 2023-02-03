/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.net.ssl.SSLContext;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @test
 * @bug 8292044
 * @summary Tests behaviour of HttpClient when server responds with 102 or 103 status codes
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 * @run testng/othervm -Djdk.internal.httpclient.debug=true
 * *                   -Djdk.httpclient.HttpClient.log=headers,requests,responses,errors Response1xxTest
 */
public class Response1xxTest implements HttpServerAdapters {
    private static final String EXPECTED_RSP_BODY = "Hello World";

    private ServerSocket serverSocket;
    private Http11Server server;
    private String http1RequestURIBase;


    private HttpTestServer http2Server; // h2c
    private String http2RequestURIBase;


    private SSLContext sslContext;
    private HttpTestServer https2Server;  // h2
    private String https2RequestURIBase;

    private final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;

    @BeforeClass
    public void setup() throws Exception {
        serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        server = new Http11Server(serverSocket);
        new Thread(server).start();
        http1RequestURIBase = URIBuilder.newBuilder().scheme("http").loopback()
                .port(serverSocket.getLocalPort()).build().toString();

        http2Server = HttpTestServer.of(new Http2TestServer("localhost", false, 0));
        http2Server.addHandler(new Http2Handler(), "/http2/102");
        http2Server.addHandler(new Http2Handler(), "/http2/103");
        http2Server.addHandler(new Http2Handler(), "/http2/100");
        http2Server.addHandler(new Http2Handler(), "/http2/101");
        http2Server.addHandler(new OKHandler(), "/http2/200");
        http2Server.addHandler(new OnlyInformationalHandler(), "/http2/only-informational");
        http2RequestURIBase = URIBuilder.newBuilder().scheme("http").loopback()
                .port(http2Server.getAddress().getPort())
                .path("/http2").build().toString();

        http2Server.start();
        System.out.println("Started HTTP2 server at " + http2Server.getAddress());

        sslContext = new SimpleSSLContext().get();
        if (sslContext == null) {
            throw new AssertionError("Unexpected null sslContext");
        }
        https2Server = HttpTestServer.of(new Http2TestServer("localhost",
                true, sslContext));
        https2Server.addHandler(new Http2Handler(), "/http2/101");
        https2RequestURIBase = URIBuilder.newBuilder().scheme("https").loopback()
                .port(https2Server.getAddress().getPort())
                .path("/http2").build().toString();
        https2Server.start();
        System.out.println("Started (https) HTTP2 server at " + https2Server.getAddress());

    }

    @AfterClass
    public void teardown() throws Throwable {
        try {
            assertNoOutstandingClientOps();
        } finally {
            if (server != null) {
                server.stop = true;
                System.out.println("(HTTP 1.1) Server stop requested");
            }
            if (serverSocket != null) {
                serverSocket.close();
                System.out.println("Closed (HTTP 1.1) server socket");
            }
            if (http2Server != null) {
                http2Server.stop();
                System.out.println("Stopped HTTP2 server");
            }
            if (https2Server != null) {
                https2Server.stop();
                System.out.println("Stopped (https) HTTP2 server");
            }
        }
    }

    private static final class Http11Server implements Runnable {
        private static final int CONTENT_LENGTH = EXPECTED_RSP_BODY.getBytes(StandardCharsets.UTF_8).length;

        private static final String HTTP_1_1_RSP_200 = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: " + CONTENT_LENGTH + "\r\n\r\n" +
                EXPECTED_RSP_BODY;

        private static final String REQ_LINE_FOO = "GET /test/foo HTTP/1.1\r\n";
        private static final String REQ_LINE_BAR = "GET /test/bar HTTP/1.1\r\n";
        private static final String REQ_LINE_HELLO = "GET /test/hello HTTP/1.1\r\n";
        private static final String REQ_LINE_BYE = "GET /test/bye HTTP/1.1\r\n";


        private final ServerSocket serverSocket;
        private volatile boolean stop;

        private Http11Server(final ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        @Override
        public void run() {
            System.out.println("Server running at " + serverSocket);
            while (!stop) {
                Socket socket = null;
                try {
                    // accept a connection
                    socket = serverSocket.accept();
                    System.out.println("Accepted connection from client " + socket);
                    // read request
                    final String requestLine;
                    try {
                        requestLine = readRequestLine(socket);
                    } catch (Throwable t) {
                        // ignore connections from potential rogue client
                        System.err.println("Ignoring connection/request from client " + socket
                                + " due to exception:");
                        t.printStackTrace();
                        // close the socket
                        safeClose(socket);
                        continue;
                    }
                    System.out.println("Received following request line from client " + socket
                            + " :\n" + requestLine);
                    final int informationalResponseCode;
                    if (requestLine.startsWith(REQ_LINE_FOO)) {
                        // we will send intermediate/informational 102 response
                        informationalResponseCode = 102;
                    } else if (requestLine.startsWith(REQ_LINE_BAR)) {
                        // we will send intermediate/informational 103 response
                        informationalResponseCode = 103;
                    } else if (requestLine.startsWith(REQ_LINE_HELLO)) {
                        // we will send intermediate/informational 100 response
                        informationalResponseCode = 100;
                    } else if (requestLine.startsWith(REQ_LINE_BYE)) {
                        // we will send intermediate/informational 101 response
                        informationalResponseCode = 101;
                    } else {
                        // unexpected client. ignore and close the client
                        System.err.println("Ignoring unexpected request from client " + socket);
                        safeClose(socket);
                        continue;
                    }
                    try (final OutputStream os = socket.getOutputStream()) {
                        // send informational response headers a few times (spec allows them to
                        // be sent multiple times)
                        for (int i = 0; i < 3; i++) {
                            // send 1xx response header
                            if (informationalResponseCode == 101) {
                                os.write(("HTTP/1.1 " + informationalResponseCode + "\r\n" +
                                        "Connection: upgrade\r\n" +
                                        "Upgrade: websocket\r\n\r\n")
                                        .getBytes(StandardCharsets.UTF_8));
                            } else {
                                os.write(("HTTP/1.1 " + informationalResponseCode + "\r\n\r\n")
                                        .getBytes(StandardCharsets.UTF_8));
                            }
                            os.flush();
                            System.out.println("Sent response code " + informationalResponseCode
                                    + " to client " + socket);
                        }
                        // now send a final response
                        System.out.println("Now sending 200 response code to client " + socket);
                        os.write(HTTP_1_1_RSP_200.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        System.out.println("Sent 200 response code to client " + socket);
                    }
                } catch (Throwable t) {
                    // close the client connection
                    safeClose(socket);
                    // continue accepting any other client connections until we are asked to stop
                    System.err.println("Ignoring exception in server:");
                    t.printStackTrace();
                }
            }
        }

        static String readRequestLine(final Socket sock) throws IOException {
            final InputStream is = sock.getInputStream();
            final StringBuilder sb = new StringBuilder("");
            byte[] buf = new byte[1024];
            while (!sb.toString().endsWith("\r\n\r\n")) {
                final int numRead = is.read(buf);
                if (numRead == -1) {
                    return sb.toString();
                }
                final String part = new String(buf, 0, numRead, StandardCharsets.ISO_8859_1);
                sb.append(part);
            }
            return sb.toString();
        }

        private static void safeClose(final Socket socket) {
            try {
                socket.close();
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    private static class Http2Handler implements HttpTestHandler {

        @Override
        public void handle(final HttpTestExchange exchange) throws IOException {
            final URI requestURI = exchange.getRequestURI();
            final int informationResponseCode;
            if (requestURI.getPath().endsWith("/102")) {
                informationResponseCode = 102;
            } else if (requestURI.getPath().endsWith("/103")) {
                informationResponseCode = 103;
            } else if (requestURI.getPath().endsWith("/100")) {
                informationResponseCode = 100;
            } else if (requestURI.getPath().endsWith("/101")) {
                informationResponseCode = 101;
            } else {
                // unexpected request
                System.err.println("Unexpected request " + requestURI + " from client "
                        + exchange.getRemoteAddress());
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            // send informational response headers a few times (spec allows them to
            // be sent multiple times)
            for (int i = 0; i < 3; i++) {
                exchange.sendResponseHeaders(informationResponseCode, -1);
                System.out.println("Sent " + informationResponseCode + " response code from H2 server");
            }
            // now send 200 response
            try {
                final byte[] body = EXPECTED_RSP_BODY.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                System.out.println("Sent 200 response from H2 server");
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
                System.out.println("Sent response body from H2 server");
            } catch (Throwable e) {
                System.err.println("Failed to send response from HTTP2 handler:");
                e.printStackTrace();
                throw e;
            }
        }
    }

    private static class OnlyInformationalHandler implements HttpTestHandler {

        @Override
        public void handle(final HttpTestExchange exchange) throws IOException {
            // we only send informational response and then return
            for (int i = 0; i < 5; i++) {
                exchange.sendResponseHeaders(102, -1);
                System.out.println("Sent 102 response code from H2 server");
                // wait for a while before sending again
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    // just return
                    System.err.println("Handler thread interrupted");
                }
            }
        }
    }

    private static class OKHandler implements HttpTestHandler {

        @Override
        public void handle(final HttpTestExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, -1);
        }
    }

    /**
     * Tests that when a HTTP/1.1 server sends intermediate 1xx response codes and then the final
     * response, the client (internally) will ignore those intermediate informational response codes
     * and only return the final response to the application
     */
    @Test
    public void test1xxForHTTP11() throws Exception {
        final HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(HttpClient.Builder.NO_PROXY).build();
        TRACKER.track(client);
        final URI[] requestURIs = new URI[]{
                new URI(http1RequestURIBase + "/test/foo"),
                new URI(http1RequestURIBase + "/test/bar"),
                new URI(http1RequestURIBase + "/test/hello")};
        for (final URI requestURI : requestURIs) {
            final HttpRequest request = HttpRequest.newBuilder(requestURI).build();
            System.out.println("Issuing request to " + requestURI);
            final HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Assert.assertEquals(response.version(), HttpClient.Version.HTTP_1_1,
                    "Unexpected HTTP version in response");
            Assert.assertEquals(response.statusCode(), 200, "Unexpected response code");
            Assert.assertEquals(response.body(), EXPECTED_RSP_BODY, "Unexpected response body");
        }
    }

    /**
     * Tests that when a HTTP2 server sends intermediate 1xx response codes and then the final
     * response, the client (internally) will ignore those intermediate informational response codes
     * and only return the final response to the application
     */
    @Test
    public void test1xxForHTTP2() throws Exception {
        final HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .proxy(HttpClient.Builder.NO_PROXY).build();
        TRACKER.track(client);
        final URI[] requestURIs = new URI[]{
                new URI(http2RequestURIBase + "/102"),
                new URI(http2RequestURIBase + "/103"),
                new URI(http2RequestURIBase + "/100")};
        for (final URI requestURI : requestURIs) {
            final HttpRequest request = HttpRequest.newBuilder(requestURI).build();
            System.out.println("Issuing request to " + requestURI);
            final HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Assert.assertEquals(response.version(), HttpClient.Version.HTTP_2,
                    "Unexpected HTTP version in response");
            Assert.assertEquals(response.statusCode(), 200, "Unexpected response code");
            Assert.assertEquals(response.body(), EXPECTED_RSP_BODY, "Unexpected response body");
        }
    }


    /**
     * Tests that when a request is issued with a specific request timeout and the server
     * responds with intermediate 1xx response code but doesn't respond with a final response within
     * the timeout duration, then the application fails with a request timeout
     */
    @Test
    public void test1xxRequestTimeout() throws Exception {
        final HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .proxy(HttpClient.Builder.NO_PROXY).build();
        TRACKER.track(client);
        final URI requestURI = new URI(http2RequestURIBase + "/only-informational");
        final Duration requestTimeout = Duration.ofSeconds(2);
        final HttpRequest request = HttpRequest.newBuilder(requestURI).timeout(requestTimeout)
                .build();
        System.out.println("Issuing request to " + requestURI);
        // we expect the request to timeout
        Assert.assertThrows(HttpTimeoutException.class, () -> {
            client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        });
    }

    /**
     * Tests that when the HTTP/1.1 server sends a 101 response when the request hasn't asked
     * for an "Upgrade" then the request fails.
     */
    @Test
    public void testHTTP11Unexpected101() throws Exception {
        final HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(HttpClient.Builder.NO_PROXY).build();
        TRACKER.track(client);
        final URI requestURI = new URI(http1RequestURIBase + "/test/bye");
        final HttpRequest request = HttpRequest.newBuilder(requestURI).build();
        System.out.println("Issuing request to " + requestURI);
        // we expect the request to fail because the server sent an unexpected 101
        Assert.assertThrows(ProtocolException.class,
                () -> client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
    }


    /**
     * Tests that when the HTTP2 server (over HTTPS) sends a 101 response when the request
     * hasn't asked for an "Upgrade" then the request fails.
     */
    @Test
    public void testSecureHTTP2Unexpected101() throws Exception {
        final HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .sslContext(sslContext)
                .proxy(HttpClient.Builder.NO_PROXY).build();
        TRACKER.track(client);
        final URI requestURI = new URI(https2RequestURIBase + "/101");
        final HttpRequest request = HttpRequest.newBuilder(requestURI).build();
        System.out.println("Issuing request to " + requestURI);
        // we expect the request to fail because the server sent an unexpected 101
        Assert.assertThrows(ProtocolException.class,
                () -> client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
    }

    /**
     * Tests that when the HTTP2 server (over plain HTTP) sends a 101 response when the request
     * hasn't asked for an "Upgrade" then the request fails.
     */
    @Test
    public void testPlainHTTP2Unexpected101() throws Exception {
        final HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .proxy(HttpClient.Builder.NO_PROXY).build();
        TRACKER.track(client);
        // when using HTTP2 version against a "http://" (non-secure) URI
        // the HTTP client (implementation) internally initiates a HTTP/1.1 connection
        // and then does an "Upgrade:" to "h2c". This it does when there isn't already a
        // H2 connection against the target/destination server. So here we initiate a dummy request
        // using the client instance against the same target server and just expect it to return
        // back successfully. Once that connection is established (and internally pooled), the client
        // will then reuse that connection and won't issue an "Upgrade:" and thus we can then
        // start our testing
        warmupH2Client(client);
        // start the actual testing
        final URI requestURI = new URI(http2RequestURIBase + "/101");
        final HttpRequest request = HttpRequest.newBuilder(requestURI).build();
        System.out.println("Issuing request to " + requestURI);
        // we expect the request to fail because the server sent an unexpected 101
        Assert.assertThrows(ProtocolException.class,
                () -> client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
    }

    // sends a request and expects a 200 response back
    private void warmupH2Client(final HttpClient client) throws Exception {
        final URI requestURI = new URI(http2RequestURIBase + "/200");
        final HttpRequest request = HttpRequest.newBuilder(requestURI).build();
        System.out.println("Issuing (warmup) request to " + requestURI);
        final HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        Assert.assertEquals(response.statusCode(), 200, "Unexpected response code");
    }

    // verifies that the HttpClient being tracked has no outstanding operations
    private void assertNoOutstandingClientOps() throws AssertionError {
        System.gc();
        final AssertionError refCheckFailure = TRACKER.check(1000);
        if (refCheckFailure != null) {
            throw refCheckFailure;
        }
        // successful test completion
    }
}
