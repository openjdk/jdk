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

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

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
    public static void testNull() {
        final Headers h = new Headers();
        h.put("Foo", List.of("Bar"));

        new Headers().add(null, "Bar");
        new Headers().set(null, "Bar");
        new Headers().put(null, List.of());

        final var map = new HashMap<String, List<String>>();
        map.put(null, List.of("Bar"));
        new Headers().putAll(map);

        // expected to throw NPE
        final var list = new LinkedList<String>();
        list.add(null);

        assertThrows(NPE, () -> new Headers().add("Foo", null));
        assertThrows(NPE, () -> new Headers().set("Foo", null));
        assertThrows(NPE, () -> new Headers().put("Foo", list));

        // compute
        h.compute(null, (k, v) -> List.of(""))
        // computeIfAbsent
        // computeIfPresent

        // containsKey
        // containsValue

        // copyof
        // entry
        // entrySet
        // keySet


        // equals

        // get
        // getOrDefault

        // merge

        // ofs ?

        // put
        // putAll
        // putIfAbsent

        // remove 1
        // remove 2

        // replace 2
        // replace 3
        // replaceAll






    }

    @DataProvider
    public Object[][] respHeadersWithNull() {
        return new Object[][] {
                {"Foo", null},
                {null, "Bar"}
        };
    }

    @Test(dataProvider = "respHeadersWithNull")
    public void testNullResponseHeaders(String headerKey, String headerVal)
            throws Exception {
        var handler = new Handler(headerKey, headerVal);
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", handler);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
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
    public static void testPutAll() {
        final var h0 = new Headers();
        final var map = new HashMap<String, List<String>>();
        map.put("a", null);
        assertThrows(NPE, () -> h0.putAll(map));

        final var list = new ArrayList<String>();
        list.add(null);
        assertThrows(NPE, () -> h0.putAll(Map.of("a", list)));
        assertThrows(IAE, () -> h0.putAll(Map.of("a", List.of("\n"))));

        final var h1 = new Headers();
        h1.put("a", List.of("1"));
        h1.put("b", List.of("2"));
        final var h2 = new Headers();
        h2.putAll(Map.of("a", List.of("1"), "b", List.of("2")));
        assertEquals(h1, h2);
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
        assertEquals(h1, h2);
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
        return headers.stream()
                .flatMap(header1 -> headers.stream().map(header2 -> new Headers[] { header1, header2 }))
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "headerPairs")
    public static void testEqualsAndHashCode(Headers h1, Headers h2) {
        // testng's asserts(Map, Map) do not call Headers.equals
        assertTrue(h1.equals(h2), "Headers differ");
        assertEquals(h1.hashCode(), h2.hashCode(), "hashCode differ for "
                + List.of(h1, h2));
    }

    @Test
    public static void testEqualsMap() {
        final var h = new Headers();
        final var m = new HashMap<String, List<String>>();
        assertFalse(h.equals(m), "Map instance cannot be equal to Headers");
        assertTrue(m.equals(h));
    }

    @Test
    public static void testToString() {
        final var h = new Headers();
        h.put("Accept-Encoding", List.of("gzip, deflate"));
        assertTrue(h.toString().startsWith("com.sun.net.httpserver.Headers"));
        assertTrue(h.toString().endsWith(" { {Accept-encoding=[gzip, deflate]} }"));
    }
}
