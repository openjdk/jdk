/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @summary Tests that when the httpclient sends a 100 Expect Continue header and receives
 *          a response code of 417 Expectation Failed, that the client does not hang
 *          indefinitely and closes the connection.
 * @bug 8286171
 * @modules java.base/sun.net.www.http
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 *          jdk.httpserver
 * @library /test/lib http2/server
 * @compile HttpServerAdapters.java
 * @run testng/othervm ExpectContinueTest
 */


import com.sun.net.httpserver.HttpServer;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ServerSocketFactory;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;

public class ExpectContinueTest implements HttpServerAdapters {

    HttpTestServer http1TestServer; // HTTP/1.1
    Http1HangServer http1HangServer;
    HttpTestServer http2TestServer; // HTTP/2

    URI getUri;
    URI postUri;
    URI hangUri;
    URI h2getUri;
    URI h2postUri;
    URI h2hangUri;

    static final String EXPECTATION_FAILED_417 = "417 Expectation Failed";

    @BeforeTest
    public void setup() throws Exception {
        InetSocketAddress sa = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        InetSocketAddress saHang = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

        http1TestServer = HttpTestServer.of(HttpServer.create(sa, 0));
        http1TestServer.addHandler(new GetHandler(), "/http1/get");
        http1TestServer.addHandler(new PostHandler(), "/http1/post");
        getUri = URI.create("http://" + http1TestServer.serverAuthority() + "/http1/get");
        postUri = URI.create("http://" + http1TestServer.serverAuthority() + "/http1/post");

        // Due to limitations of the above Http1 Server, a manual approach is taken to test the hanging with the
        // httpclient using Http1 so that the correct response header can be returned for the test case
        http1HangServer = new Http1HangServer(saHang);
        hangUri = URI.create("http://" + http1HangServer.ia.getCanonicalHostName() + ":" + http1HangServer.port + "/http1/hang");


        http2TestServer = HttpTestServer.of(
                new Http2TestServer("localhost", false, 0));
        http2TestServer.addHandler(new GetHandler(), "/http2/get");
        http2TestServer.addHandler(new PostHandler(), "/http2/post");
        http2TestServer.addHandler(new PostHandlerCantContinue(), "/http2/hang");
        h2getUri = URI.create("http://" + http2TestServer.serverAuthority() + "/http2/get");
        h2postUri = URI.create("http://" + http2TestServer.serverAuthority() + "/http2/post");
        h2hangUri = URI.create("http://" + http2TestServer.serverAuthority() + "/http2/hang");

        http1TestServer.start();
        http1HangServer.start();
        http2TestServer.start();
    }

    @AfterTest
    public void teardown() throws IOException {
        http1TestServer.stop();
        http1HangServer.close();
        http2TestServer.stop();
    }

    static class GetHandler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody();
                 OutputStream os = exchange.getResponseBody()) {
                is.readAllBytes();
                byte[] bytes = "RESPONSE_BODY".getBytes(UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }
    }

    static class PostHandler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            // Http1 server has already sent 100 response at this point but not Http2 server
            if (exchange.getExchangeVersion().equals(HttpClient.Version.HTTP_2)) {
                // Send 100 Headers, tell client that we're ready for body
                exchange.sendResponseHeaders(100, 0);
            }

            // Read body from client and acknowledge with 200
            try (InputStream is = exchange.getRequestBody();
                OutputStream os = exchange.getResponseBody()) {
                is.readAllBytes();
                exchange.sendResponseHeaders(200, 0);
            }
        }
    }

    static class PostHandlerCantContinue implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            //Send 417 Headers, tell client to not send body
            try (InputStream is = exchange.getRequestBody();
                 OutputStream os = exchange.getResponseBody()) {
                byte[] bytes = EXPECTATION_FAILED_417.getBytes();
                exchange.sendResponseHeaders(417, bytes.length);
                os.write(bytes);
            }
        }
    }

    static class Http1HangServer extends Thread implements Closeable {

        final ServerSocket ss;
        final InetAddress ia;
        final int port;
        volatile boolean closed = false;
        volatile Socket client;

        Http1HangServer(InetSocketAddress sa) throws IOException {
            ss = ServerSocketFactory.getDefault()
                    .createServerSocket(sa.getPort(), -1, sa.getAddress());
            ia = ss.getInetAddress();
            port = ss.getLocalPort();
        }

        @Override
        public void run() {
            byte[] bytes = EXPECTATION_FAILED_417.getBytes();

            while (!closed) {
                try {
                    // Not using try with resources here as we expect the client to close resources when
                    // 417 is received
                    client = ss.accept();
                    InputStream is = client.getInputStream();
                    OutputStream os = client.getOutputStream();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    Writer w = new OutputStreamWriter(os, UTF_8);
                    PrintWriter pw = new PrintWriter(w);

                    StringBuilder response = new StringBuilder();
                    String line = null;
                    StringBuilder reqBuilder = new StringBuilder();
                    while (!(line = reader.readLine()).isEmpty()) {
                        reqBuilder.append(line + "\r\n");
                    }
                    String req = reqBuilder.toString();
                    System.err.println("Http1HangServer received: " + req);
                    StringTokenizer tokenizer = new StringTokenizer(req);
                    String method = tokenizer.nextToken();
                    String path = tokenizer.nextToken();
                    String version = tokenizer.nextToken();

                    boolean validRequest = method.equals("POST") && path.equals("/http1/hang")
                                        && version.equals("HTTP/1.1");
                    // If correct request, send 417 reply. Otherwise, wait for correct one
                    if (validRequest) {
                        closed = true;
                        response.append("HTTP/1.1 417 Expectation Failed\r\n")
                                .append("Content-Length: ")
                                .append(0)
                                .append("\r\n\r\n");
                        pw.print(response);
                        pw.flush();

                        os.write(bytes);
                        os.flush();
                    } else {
                        client.close();
                    }
                } catch (IOException e) {
                    closed = true;
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void close() throws IOException {
            if (client != null) client.close();
            if (ss != null) ss.close();
        }
    }

    @DataProvider(name = "uris")
    public Object[][] urisData() {
        return new Object[][]{
                { getUri,   postUri, hangUri, HttpClient.Version.HTTP_1_1 },
                { h2getUri,  h2postUri, h2hangUri, HttpClient.Version.HTTP_2 }
        };
    }

    @Test(dataProvider = "uris")
    public void test(URI getUri, URI postUri, URI hangUri, HttpClient.Version version) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .version(version)
                .build();

        HttpRequest getRequest = HttpRequest.newBuilder(getUri)
                .GET()
                .build();

        HttpRequest postRequest = HttpRequest.newBuilder(postUri)
                .POST(HttpRequest.BodyPublishers.ofString("Sample Post"))
                .expectContinue(true)
                .build();

        HttpRequest hangRequest = HttpRequest.newBuilder(hangUri)
                .POST(HttpRequest.BodyPublishers.ofString("Sample Post"))
                .expectContinue(true)
                .build();

        CompletableFuture<HttpResponse<String>> cf = client.sendAsync(getRequest, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> resp = cf.join();
        System.err.println("Response Headers: " + resp.headers());
        System.err.println("Response Status Code: " + resp.statusCode());
        assertEquals(resp.statusCode(), 200);

        cf = client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString());
        resp = cf.join();
        System.err.println("Response Headers: " + resp.headers());
        System.err.println("Response Status Code: " + resp.statusCode());
        assertEquals(resp.statusCode(), 200);

        cf = client.sendAsync(hangRequest, HttpResponse.BodyHandlers.ofString());
        resp = cf.join();
        System.err.println("Response Headers: " + resp.headers());
        System.err.println("Response Status Code: " + resp.statusCode());
        assertEquals(resp.statusCode(), 417);
    }

}
