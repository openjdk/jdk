/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib server
 * @build jdk.test.lib.net.SimpleSSLContext
 * @modules java.base/sun.net.www.http
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 * @run testng/othervm LargePushPromise
 */


import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.frame.ContinuationFrame;
import jdk.internal.net.http.frame.HeaderFrame;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import static java.nio.charset.StandardCharsets.UTF_8;

public class LargePushPromise {

    Http2TestServer server;
    URI uri;

    // Extend the Http2Test exchange impl
    //   - extend the http2testserverconnection
    // Supply the exchangeimpl
    // Profit?

    @BeforeTest
    public void setup() throws Exception {
        server = new Http2TestServer(false, 4040);
        server.addHandler(new ServerPushHandler("Main Response Body", "/promise"), "/");

        server.setExchangeSupplier(Http2LPPTestExchangeImpl::new);

        System.err.println("Server listening on port " + server.getAddress().getPort());
        server.start();
        int port = server.getAddress().getPort();
        uri = new URI("http://localhost:" + port + "/req");
    }

    @Test
    public void test() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest hreq = HttpRequest.newBuilder(uri).version(HttpClient.Version.HTTP_2).GET().build();

        try {
            HttpResponse<String> hres = client.send(hreq, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw ioe;
        }
//        assertTrue(hres.statusCode() == 200);
//        ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<String>>>
//                resultMap = new ConcurrentHashMap<>();
//        HttpResponse.PushPromiseHandler<String> pph = (initial, pushRequest, acceptor) -> {
//            HttpResponse.BodyHandler<String> s = HttpResponse.BodyHandlers.ofString(UTF_8);
//            CompletableFuture<HttpResponse<String>> cf = acceptor.apply(s);
//            resultMap.put(pushRequest, cf);
//        };
//
//        CompletableFuture<HttpResponse<String>> cf =
//                client.sendAsync(hreq, HttpResponse.BodyHandlers.ofString(UTF_8), pph);
//        cf.join();
//        resultMap.put(hreq, cf);
//        System.err.println("results.size: " + resultMap.size());
//        for (HttpRequest r : resultMap.keySet()) {
//            HttpResponse<String> response = resultMap.get(r).join();
//            System.err.println(response.toString());
//        }
    }

    static class Http2LPPTestExchangeImpl extends Http2TestExchangeImpl {

        Http2LPPTestExchangeImpl(int streamid, String method, HttpHeaders reqheaders, HttpHeadersBuilder rspheadersBuilder, URI uri, InputStream is, SSLSession sslSession, BodyOutputStream os, Http2TestServerConnection conn, boolean pushAllowed) {
            super(streamid, method, reqheaders, rspheadersBuilder, uri, is, sslSession, os, conn, pushAllowed);
        }

        @Override
        public void serverPush(URI uri, HttpHeaders headers, InputStream content) {
            HttpHeadersBuilder headersBuilder = new HttpHeadersBuilder();
            headersBuilder.setHeader(":method", "GET");
            headersBuilder.setHeader(":scheme", uri.getScheme());
            headersBuilder.setHeader(":authority", uri.getAuthority());
            headersBuilder.setHeader(":path", uri.getPath());
            for (Map.Entry<String,List<String>> entry : headers.map().entrySet()) {
                for (String value : entry.getValue())
                    headersBuilder.addHeader(entry.getKey(), value);
            }
            HttpHeaders combinedHeaders = headersBuilder.build();
            OutgoingPushPromise pp = new OutgoingPushPromise(streamid, uri, combinedHeaders, content);
            pp.setFlag(0x00);


            HttpHeadersBuilder altHeaders = new HttpHeadersBuilder();
//            Map<String, List<String>> map = new HashMap<>();
            for (int i = 0; i < 50; i++)
                altHeaders.addHeader("x-bloatedHeader-" + i, "data_" + i);
//                map.put("x-bloatedHeader-" + i, List.of("data_" + i));

            ContinuationFrame cf = new ContinuationFrame(streamid, HeaderFrame.END_HEADERS, conn.encodeHeaders(altHeaders.build()));
            System.err.println("PP Flags: " + pp.getFlags());
            try {
                conn.outputQ.put(pp);
                conn.outputQ.put(cf);
                // writeLoop will spin up thread to read the InputStream
            } catch (IOException ex) {
                System.err.println("TestServer: pushPromise exception: " + ex);
            }
        }
    }

    static class ServerPushHandler implements Http2Handler {

        private final String mainResponseBody;
        private final String mainPromiseBody;
        private final String promise;

        public ServerPushHandler(String mainResponseBody,
                                 String promise)
                throws Exception
        {
            Objects.requireNonNull(promise);
            this.mainResponseBody = mainResponseBody;
            this.mainPromiseBody = "Main Promise Body";
            this.promise = promise;
        }

        public void handle(Http2TestExchange exchange) throws IOException {
            System.err.println("Server: handle " + exchange);
            try (InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
            }

            if (exchange.serverPushAllowed()) {
                pushPromise(exchange);
//                sendPushContinuation(exchange);
            }

            // response data for the main response
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] bytes = mainResponseBody.getBytes(UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }

        static final BiPredicate<String,String> ACCEPT_ALL = (x, y) -> true;


        private void pushPromise(Http2TestExchange exchange) throws IOException {
            URI requestURI = exchange.getRequestURI();
            URI uri = requestURI.resolve("/promise");
            InputStream is = new ByteArrayInputStream("Test_String".getBytes(UTF_8));
            Map<String, List<String>> map = new HashMap<>();
            map.put("X-Promise", List.of(mainPromiseBody));

            HttpHeaders headers = HttpHeaders.of(map, ACCEPT_ALL);

            exchange.serverPush(uri, headers, is);
            System.err.println("Server: Push sent");
        }
    }
}