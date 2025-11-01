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
 * @bug 8251496 8268960
 * @summary Tests for methods in Headers class
 * @modules jdk.httpserver/com.sun.net.httpserver:+open
 *          jdk.httpserver/sun.net.httpserver:+open
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class HeadersTest {

    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    static final Class<IOException> IOE = IOException.class;
    static final Class<NullPointerException> NPE = NullPointerException.class;

    @Test
    public static void testDefaultConstructor() {
        var headers = new Headers();
        assertTrue(headers.isEmpty());
    }

    @Test
    public static void testNull() {
        final Headers h = new Headers();
        h.put("Foo", List.of("Bar"));

        final var mapNullKey = new HashMap<String, List<String>>();
        mapNullKey.put(null, List.of("Bar"));

        final var mapNullList = new HashMap<String, List<String>>();
        mapNullList.put("Foo", null);

        final var listWithNull = new LinkedList<String>();
        listWithNull.add(null);

        final var mapNullInList = new HashMap<String, List<String>>();
        mapNullInList.put("Foo", listWithNull);

        assertThrows(NPE, () -> h.add(null, "Bar"));
        assertThrows(NPE, () -> h.add("Foo", null));

        assertThrows(NPE, () -> h.compute(null, (k, v) -> List.of("Bar")));
        assertThrows(NPE, () -> h.compute("Foo", (k, v) -> listWithNull));

        assertThrows(NPE, () -> h.computeIfAbsent(null, (k) -> List.of("Bar")));
        assertThrows(NPE, () -> h.computeIfAbsent("Foo-foo", (k) -> listWithNull));

        assertThrows(NPE, () -> h.computeIfPresent(null, (k, v) -> List.of("Bar")));
        assertThrows(NPE, () -> h.computeIfPresent("Foo", (k, v) -> listWithNull));

        assertThrows(NPE, () -> h.containsKey(null));

        assertThrows(NPE, () -> h.containsValue(null));

        assertThrows(NPE, () -> h.get(null));

        assertThrows(NPE, () -> h.getFirst(null));

        assertThrows(NPE, () -> h.getOrDefault(null, List.of("Bar")));

        assertThrows(NPE, () -> h.merge(null, List.of("Bar"), (k, v) -> List.of("Bar")));
        assertThrows(NPE, () -> h.merge("Foo-foo", null, (k, v) -> List.of("Bar")));
        assertThrows(NPE, () -> h.merge("Foo-foo", listWithNull, (k, v) -> List.of("Bar")));
        assertThrows(NPE, () -> h.merge("Foo", List.of("Bar"), (k, v) -> listWithNull));

        assertThrows(NPE, () -> h.put(null, List.of("Bar")));
        assertThrows(NPE, () -> h.put("Foo", null));
        assertThrows(NPE, () -> h.put("Foo", listWithNull));

        assertThrows(NPE, () -> h.putAll(mapNullKey));
        assertThrows(NPE, () -> h.putAll(mapNullList));
        assertThrows(NPE, () -> h.putAll(mapNullInList));

        assertThrows(NPE, () -> h.putIfAbsent(null, List.of("Bar")));
        assertThrows(NPE, () -> h.putIfAbsent("Foo-foo", null));
        assertThrows(NPE, () -> h.putIfAbsent("Foo-foo", listWithNull));

        assertThrows(NPE, () -> h.remove(null));

        assertThrows(NPE, () -> h.remove(null, List.of("Bar")));

        assertThrows(NPE, () -> h.replace(null, List.of("Bar")));
        assertThrows(NPE, () -> h.replace("Foo", null));
        assertThrows(NPE, () -> h.replace("Foo", listWithNull));

        assertThrows(NPE, () -> h.replace(null, List.of("Bar"), List.of("Bar")));
        assertThrows(NPE, () -> h.replace("Foo", List.of("Bar"), null));
        assertThrows(NPE, () -> h.replace("Foo", List.of("Bar"), listWithNull));

        assertThrows(NPE, () -> h.replaceAll((k, v) -> listWithNull));
        assertThrows(NPE, () -> h.replaceAll((k, v) -> null));

        assertThrows(NPE, () -> h.set(null, "Bar"));
        assertThrows(NPE, () -> h.set("Foo", null));
    }

    @DataProvider
    public Object[][] responseHeaders() {
        final var listWithNull = new LinkedList<String>();
        listWithNull.add(null);
        return new Object[][] {
                {null,  List.of("Bar")},
                {"Foo", null},
                {"Foo", listWithNull}
        };
    }

    /**
     * Confirms HttpExchange::sendResponseHeaders throws NPE if response headers
     * contain a null key or value.
     */
    @Test(dataProvider = "responseHeaders")
    public void testNullResponseHeaders(String headerKey, List<String> headerVal)
            throws Exception {
        var handler = new Handler(headerKey, headerVal);
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", handler);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            assertThrows(IOE, () -> client.send(request, HttpResponse.BodyHandlers.ofString()));
            assertEquals(throwable.get().getClass(), NPE);
            assertTrue(Arrays.stream(throwable.get().getStackTrace())
                    .anyMatch(e -> e.getClassName().equals("sun.net.httpserver.HttpExchangeImpl")
                            || e.getMethodName().equals("sendResponseHeaders")));
        } finally {
            server.stop(0);
        }
    }

    private static CompletableFuture<Throwable> throwable = new CompletableFuture<>();

    private record Handler(String headerKey, List<String> headerVal) implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody();
                 OutputStream os = exchange.getResponseBody()) {
                is.readAllBytes();
                var resp = "hello world".getBytes(StandardCharsets.UTF_8);
                putHeaders(exchange.getResponseHeaders(), headerKey, headerVal);
                try {
                    exchange.sendResponseHeaders(200, resp.length);
                } catch (Throwable t) {  // expect NPE
                    throwable.complete(t);
                    throw t;
                }
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

    /**
     * Sets headers reflectively to be able to set a null key or value.
     */
    private static void putHeaders(Headers headers,
                                   String headerKey,
                                   List<String> headerVal) {
        try {
            final var map = new HashMap<String, List<String>>();
            map.put(headerKey, headerVal);
            var mapField = Headers.class.getDeclaredField("map");
            mapField.setAccessible(true);
            mapField.set(headers, map);
        } catch (Exception e) {
            throw new RuntimeException("Could not set headers reflectively", e);
        }
    }

    @DataProvider
    public static Object[][] headerPairs() {
        final var h1 = new Headers();
        final var h2 = new Headers();
        final var h3 = new Headers();
        final var h4 = new Headers();
        final var h5 = new Headers();
        h1.put("Accept-Encoding", List.of("gzip, deflate"));
        h2.put("accept-encoding", List.of("gzip, deflate"));
        h3.put("AccePT-ENCoding", List.of("gzip, deflate"));
        h4.put("ACCept-EncodING", List.of("gzip, deflate"));
        h5.put("ACCEPT-ENCODING", List.of("gzip, deflate"));

        final var headers = List.of(h1, h2, h3, h4, h5);
        return headers.stream()  // cartesian product of headers
                .flatMap(header1 -> headers.stream().map(header2 -> new Headers[] { header1, header2 }))
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "headerPairs")
    public static void testEqualsAndHashCode(Headers h1, Headers h2) {
        // avoid testng's asserts(Map, Map) as they don't call Headers::equals
        assertTrue(h1.equals(h2), "Headers differ");
        assertEquals(h1.hashCode(), h2.hashCode(), "hashCode differ for "
                + List.of(h1, h2));
    }

    @Test
    public static void testEqualsMap() {
        final var h = new Headers();
        final var m = new HashMap<String, List<String>>();
        assertTrue(h.equals(m));
        assertTrue(m.equals(h));
        assertFalse(h.equals(null), "null cannot be equal to Headers");
    }

    @Test
    public static void testToString() {
        final var h = new Headers();
        h.put("Accept-Encoding", List.of("gzip, deflate"));
        assertTrue(h.toString().equals("Headers { {Accept-encoding=[gzip, deflate]} }"));
    }

    @Test
    public static void testPutAll() {
        final var h0 = new Headers();
        final var map = new HashMap<String, List<String>>();
        map.put("a", null);
        assertThrows(NPE, () -> h0.putAll(map));

        final var list = new ArrayList<String>();
        list.add(null);
        assertThrows(NPE, () -> h0.putAll(Map.of("a", list)));
        assertThrows(IAE, () -> h0.putAll(Map.of("a", List.of("\n"))));
        assertThrows(IAE, () -> h0.putAll(Map.of("AbC\n", List.of("hey"))));
        assertThrows(IAE, () -> h0.putAll(Map.of("Ab\n", List.of("hi"))));

        final var h1 = new Headers();
        h1.put("a", List.of("1"));
        h1.put("b", List.of("2"));
        final var h2 = new Headers();
        h2.putAll(Map.of("a", List.of("1"), "b", List.of("2")));
        assertTrue(h1.equals(h2));
    }

    @Test
    public static void testReplaceAll() {
        final var h1 = new Headers();
        h1.put("a", List.of("1"));
        h1.put("b", List.of("2"));
        final var list = new ArrayList<String>();
        list.add(null);
        assertThrows(NPE, () -> h1.replaceAll((k, v) -> list));
        assertThrows(IAE, () -> h1.replaceAll((k, v) -> List.of("\n")));

        h1.replaceAll((k, v) -> {
            String s = h1.get(k).get(0);
            return List.of(s+s);
        });
        final var h2 = new Headers();
        h2.put("a", List.of("11"));
        h2.put("b", List.of("22"));
        assertTrue(h1.equals(h2));
    }

    @Test
    public static void test1ArgConstructorNull() {
        assertThrows(NPE, () -> new Headers(null));
        {
            final var m = new HashMap<String, List<String>>();
            m.put(null, List.of("Bar"));
            assertThrows(NPE, () -> new Headers(m));
        }
        {
            final var m = new HashMap<String, List<String>>();
            m.put("Foo", null);
            assertThrows(NPE, () -> new Headers(m));
        }
        {
            final var m = new HashMap<String, List<String>>();
            final var list = new LinkedList<String>();
            list.add(null);
            m.put("Foo", list);
            assertThrows(NPE, () -> new Headers(m));
        }
    }

    @Test
    public static void test1ArgConstructor() {
        {
            var h = new Headers(new Headers());
            assertTrue(h.isEmpty());
        }
        {
            var h = new Headers(Map.of("Foo", List.of("Bar")));
            assertEquals(h.get("Foo"), List.of("Bar"));
            assertEquals(h.size(), 1);
        }
        {
            var h1 = new Headers(new UnmodifiableHeaders(new Headers()));
            assertTrue(h1.isEmpty());
            h1.put("Foo", List.of("Bar"));  // modifiable
            assertEquals(h1.get("Foo"), List.of("Bar"));
            assertEquals(h1.size(), 1);

            var h2 = new Headers(h1);
            assertEquals(h2.get("Foo"), List.of("Bar"));
            assertEquals(h2.size(), 1);

            assertEquals(h1, h2);
            h1.set("Foo", "Barbar");
            assertNotEquals(h1, h2);
        }
    }

    @Test
    public static void testMutableHeaders() {
        {
            var h = new Headers();
            h.add("Foo", "Bar");
            h.add("Foo", "Bazz");
            h.get("Foo").remove(0);
            h.remove("Foo");
            h.clear();
        }
        {
            var h = new Headers(Map.of("Foo", List.of("Bar")));
            h.get("Foo").add("Bazz");
            h.get("Foo").remove(0);
            h.remove("Foo");
            h.clear();
        }
    }

    @Test
    public static void testOfNull() {
        assertThrows(NPE, () -> Headers.of((String[])null));
        assertThrows(NPE, () -> Headers.of(null, "Bar"));
        assertThrows(NPE, () -> Headers.of("Foo", null));

        assertThrows(NPE, () -> Headers.of((Map<String, List<String>>) null));
        {
            final var m = new HashMap<String, List<String>>();
            m.put(null, List.of("Bar"));
            assertThrows(NPE, () -> Headers.of(m));
        }
        {
            final var m = new HashMap<String, List<String>>();
            m.put("Foo", null);
            assertThrows(NPE, () -> Headers.of(m));
        }
        {
            final var m = new HashMap<String, List<String>>();
            final var list = new LinkedList<String>();
            list.add(null);
            m.put("Foo", list);
            assertThrows(NPE, () -> Headers.of(m));
        }
    }

    @Test
    public static void testOf() {
        final var h = Headers.of("a", "1", "b", "2");
        assertEquals(h.size(), 2);
        List.of("a", "b").forEach(n -> assertTrue(h.containsKey(n)));
        List.of("1", "2").forEach(v -> assertTrue(h.containsValue(List.of(v))));
    }

    @Test
    public static void testOfEmpty() {
        for (var h : List.of(Headers.of(), Headers.of(new String[] { }))) {
            assertEquals(h.size(), 0);
            assertTrue(h.isEmpty());
        }
    }

    @Test
    public static void testOfNumberOfElements() {
        assertThrows(IAE, () -> Headers.of("a"));
        assertThrows(IAE, () -> Headers.of("a", "1", "b"));
    }

    @Test
    public static void testOfMultipleValues() {
        final var h = Headers.of("a", "1", "b", "1", "b", "2", "b", "3");
        assertEquals(h.size(), 2);
        List.of("a", "b").forEach(n -> assertTrue(h.containsKey(n)));
        List.of(List.of("1"), List.of("1", "2", "3")).forEach(v -> assertTrue(h.containsValue(v)));
    }

    // Immutability tests in UnmodifiableHeadersTest.java
}
