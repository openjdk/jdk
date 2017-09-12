/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8153142
 * @modules jdk.incubator.httpclient
 *          jdk.httpserver
 * @run testng/othervm HeadersTest1
 */

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpHeaders;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.testng.annotations.Test;
import static jdk.incubator.http.HttpResponse.BodyHandler.asString;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;


public class HeadersTest1 {

    private static final String RESPONSE = "Hello world";

    @Test
    public void test() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 10);
        Handler h = new Handler();
        server.createContext("/test", h);
        int port = server.getAddress().getPort();
        System.out.println("Server port = " + port);

        ExecutorService e = Executors.newCachedThreadPool();
        server.setExecutor(e);
        server.start();
        HttpClient client = HttpClient.newBuilder()
                                      .executor(e)
                                      .build();

        try {
            URI uri = new URI("http://127.0.0.1:" + Integer.toString(port) + "/test/foo");
            HttpRequest req = HttpRequest.newBuilder(uri)
                                         .headers("X-Bar", "foo1")
                                         .headers("X-Bar", "foo2")
                                         .GET()
                                         .build();

            HttpResponse<?> resp = client.send(req, asString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("Expected 200, got: " + resp.statusCode());
            }
            HttpHeaders hd = resp.headers();

            assertTrue(!hd.firstValue("Non-Existent-Header").isPresent());

            List<String> v1 = hd.allValues("Non-Existent-Header");
            assertNotNull(v1);
            assertTrue(v1.isEmpty(), String.valueOf(v1));
            TestKit.assertUnmodifiableList(v1);

            List<String> v2 = hd.allValues("X-Foo-Response");
            assertNotNull(v2);
            assertEquals(new HashSet<>(v2), Set.of("resp1", "resp2"));
            TestKit.assertUnmodifiableList(v2);

            Map<String, List<String>> map = hd.map();
            TestKit.assertUnmodifiableMap(map);
            for (List<String> values : map.values()) {
                TestKit.assertUnmodifiableList(values);
            }
        } finally {
            server.stop(0);
            e.shutdownNow();
        }
        System.out.println("OK");
    }

    private static final class Handler implements HttpHandler {

        @Override
        public void handle(HttpExchange he) throws IOException {
            List<String> l = he.getRequestHeaders().get("X-Bar");
            if (!l.contains("foo1") || !l.contains("foo2")) {
                for (String s : l) {
                    System.out.println("HH: " + s);
                }
                he.sendResponseHeaders(500, -1);
                he.close();
                return;
            }
            Headers h = he.getResponseHeaders();
            h.add("X-Foo-Response", "resp1");
            h.add("X-Foo-Response", "resp2");
            he.sendResponseHeaders(200, RESPONSE.length());
            OutputStream os = he.getResponseBody();
            os.write(RESPONSE.getBytes(US_ASCII));
            os.close();
        }
    }
}
