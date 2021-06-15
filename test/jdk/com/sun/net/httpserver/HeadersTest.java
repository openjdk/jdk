/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8251496
 * @summary Tests for methods in Headers class
 * @modules jdk.httpserver/sun.net.httpserver:+open
 * @library /test/lib
 * @build jdk.test.lib.net.URIBuilder
 * @run testng/othervm HeadersTest
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import sun.net.httpserver.UnmodifiableHeaders;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class HeadersTest {

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    static final Class<IOException> IOE = IOException.class;

    @Test
    public static void testDefaultConstructor() {
        var headers = new Headers();
        assertTrue(headers.isEmpty());
    }

    @Test
    public static void test1ArgConstructor() {
        var h0 = new Headers();
        assertTrue(h0.isEmpty());

        var h1 = new Headers(h0);
        assertTrue(h1.isEmpty());

        var h2 = new Headers(new UnmodifiableHeaders(h0));
        assertTrue(h2.isEmpty());
        h2.put("Foo", List.of("Bar"));  // modifiable
        assertEquals(h2.get("Foo"), List.of("Bar"));
        assertEquals(h2.size(), 1);

        var h3 = new Headers(h2);
        assertEquals(h3.get("Foo"), List.of("Bar"));
        assertEquals(h3.size(), 1);

        var h4 = new Headers(Map.of("Foo", List.of("Bar")));
        assertEquals(h4.get("Foo"), List.of("Bar"));
        assertEquals(h4.size(), 1);
    }

    @Test
    public static void testNull() {
        // expected to pass
        new Headers().add(null, "Bar");
        new Headers().set(null, "Bar");
        new Headers().put(null, List.of());

        final var map = new HashMap<String, List<String>>();
        map.put(null, List.of("Bar"));
        new Headers().putAll(map);
        map.put("Foo", null);
        new Headers().putAll(map);

        final var list = new LinkedList<String>();
        list.add(null);
        map.put("Foo", list);
        new Headers().putAll(map);

        new Headers(map);

        // expected to throw NPE
        assertThrows(NPE, () -> new Headers().add("Foo", null));
        assertThrows(NPE, () -> new Headers().set("Foo", null));
        assertThrows(NPE, () -> new Headers().put("Foo", list));

        assertThrows(NPE, () -> new Headers(null));

        assertThrows(NPE, () -> Headers.of((String[])null));
        assertThrows(NPE, () -> Headers.of(null, "Bar"));
        assertThrows(NPE, () -> Headers.of("Foo", null));

        assertThrows(NPE, () -> Headers.of((Map<String, List<String>>) null));

        final var m1 = new HashMap<String, List<String>>();
        m1.put(null, List.of("Bar"));
        assertThrows(NPE, () -> Headers.of(m1));

        final var m2 = new HashMap<String, List<String>>();
        m2.put("Foo", null);
        assertThrows(NPE, () -> Headers.of(m2));

        final var m3 = new HashMap<String, List<String>>();
        m3.put("Foo", list);
        assertThrows(NPE, () -> Headers.of(m3));
    }

    @DataProvider
    public Object[][] handlerValues() {
        var list = new ArrayList<String>();
        list.add(null);
        return new Object[][] {
                {"Foo", null},
                {null, "Bar"}
        };
    }

    @Test(dataProvider = "handlerValues")
    public void testNullResponseHeaders(String headerKey, String headerVal)
            throws Exception {
        var handler = new Handler(headerKey, headerVal);
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", handler);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            expectThrows(IOE, () -> client.send(request, HttpResponse.BodyHandlers.ofString()));
        } finally {
            server.stop(0);
        }
    }

    private class Handler implements HttpHandler {
        private String headerKey;
        private String headerVal;

        public Handler(String key, String val) {
            headerKey = key;
            headerVal = val;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody();
                 OutputStream os = exchange.getResponseBody()) {
                is.readAllBytes();
                var resp = "hello world".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set(headerKey, headerVal);
                assertNull(exchange.getResponseHeaders().get("Foo"));
                exchange.sendResponseHeaders(200, resp.length);
                os.write(resp);
            }
        }
    }

    private static URI uri(HttpServer server, String path) {
        return URIBuilder.newBuilder()
                .host("localhost")
                .port(server.getAddress().getPort())
                .scheme("http")
                .path("/" + path)
                .buildUnchecked();
    }

    @Test
    public static void testOfNumberOfElements() {
        assertThrows(IAE, () -> Headers.of(new String[] {}));
        assertThrows(IAE, () -> Headers.of("a"));
        assertThrows(IAE, () -> Headers.of("a", "x", "b"));
    }

    @Test
    public static void testOf() {
        final var h = Headers.of("a", "x", "b", "y");
        assertEquals(h.size(), 2);
        List.of("a", "b").forEach(n -> assertTrue(h.containsKey(n)));
        List.of("x", "y").forEach(v -> assertTrue(h.containsValue(List.of(v))));
    }

    @Test
    public static void testOfMultipleValues() {
        final var h = Headers.of("a", "x", "b", "x", "b", "y", "b", "z");
        assertEquals(h.size(), 2);
        List.of("a", "b").forEach(n -> assertTrue(h.containsKey(n)));
        List.of(List.of("x"), List.of("x", "y", "z")).forEach(v -> assertTrue(h.containsValue(v)));
    }
}
