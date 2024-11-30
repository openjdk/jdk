/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8251496 8333590
 * @summary Test that UnmodifiableHeaders is in fact immutable
 * @modules jdk.httpserver/sun.net.httpserver:+open
 * @run testng/othervm UnmodifiableHeadersTest
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import sun.net.httpserver.UnmodifiableHeaders;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class UnmodifiableHeadersTest {

    @Test
    public static void testEquality() {
        var headers = new Headers();
        var unmodifiableHeaders1 = new UnmodifiableHeaders(headers);
        assertEquals(unmodifiableHeaders1, headers);
        assertEquals(unmodifiableHeaders1.hashCode(), headers.hashCode());
        assertEquals(unmodifiableHeaders1.get("Foo"), headers.get("Foo"));

        headers.add("Foo", "Bar");
        var unmodifiableHeaders2 = new UnmodifiableHeaders(headers);
        assertEquals(unmodifiableHeaders2, headers);
        assertEquals(unmodifiableHeaders2.hashCode(), headers.hashCode());
        assertEquals(unmodifiableHeaders2.get("Foo"), headers.get("Foo"));
    }

    @DataProvider
    public Object[][] headers() {
        var headers = new Headers();
        headers.add("Foo", "Bar");
        var exchange = new TestHttpExchange(headers);

        return new Object[][] {
                { exchange.getRequestHeaders() },
                { Headers.of("Foo", "Bar") },
                { Headers.of(Map.of("Foo", List.of("Bar"))) },
        };
    }

    @Test(dataProvider = "headers")
    public static void testUnmodifiableHeaders(Headers headers) {
        assertUnsupportedOperation(headers);
        assertUnmodifiableCollection(headers);
        assertUnmodifiableList(headers);
    }

    @DataProvider
    public Object[][] toStringHeaders() {
        final Headers headers = new Headers();
        headers.add("hello", "World");
        return new Object[][] {
                { headers },
                { Headers.of("abc", "XYZ") },
                { Headers.of(Map.of("foo", List.of("Bar"))) },
                { Headers.of(Map.of("Hello", List.of())) },
                { Headers.of(Map.of("one", List.of("two", "THREE"))) },
        };
    }

    /*
     * Verify that the String returned by Headers.toString() contains the expected
     * key/value(s)
     */
    @Test(dataProvider = "toStringHeaders")
    public void testToString(final Headers headers) {
        final Headers copy = Headers.of(headers);
        assertNotNull(copy, "Headers.of() returned null");
        final String actualToString = copy.toString();
        assertNotNull(actualToString, "toString() returned null");
        for (final Map.Entry<String, List<String>> originalHeadersEntry : headers.entrySet()) {
            final String expectedKey = originalHeadersEntry.getKey();
            // We just verify the presence of key and value in the toString()
            // return value. We intentionally don't expect or verify that the
            // toString() content is in some specific form.
            assertTrue(actualToString.contains(expectedKey),
                    expectedKey + " missing in output of Headers.of().toString()");
            final List<String> expectedVals = originalHeadersEntry.getValue();
            for (final String val : expectedVals) {
                assertTrue(actualToString.contains(val), val + " for header key "
                        + expectedKey + " missing in output of Headers.of().toString()");
            }
        }
    }

    static final Class<UnsupportedOperationException> UOP = UnsupportedOperationException.class;

    static void assertUnsupportedOperation(Headers headers) {
        assertThrows(UOP, () -> headers.add("a", "b"));
        assertThrows(UOP, () -> headers.compute("c", (k, v) -> List.of("c")));
        assertThrows(UOP, () -> headers.computeIfAbsent("d", k -> List.of("d")));
        assertThrows(UOP, () -> headers.computeIfPresent("Foo", (k, v) -> null));
        assertThrows(UOP, () -> headers.merge("e", List.of("e"), (k, v) -> List.of("e")));
        assertThrows(UOP, () -> headers.put("f", List.of("f")));
        assertThrows(UOP, () -> headers.putAll(Map.of()));
        assertThrows(UOP, () -> headers.putIfAbsent("g", List.of("g")));
        assertThrows(UOP, () -> headers.remove("h"));
        assertThrows(UOP, () -> headers.replace("i", List.of("i")));
        assertThrows(UOP, () -> headers.replace("j", List.of("j"), List.of("j")));
        assertThrows(UOP, () -> headers.replaceAll((k, v) -> List.of("k")));
        assertThrows(UOP, () -> headers.set("l", "m"));
        assertThrows(UOP, () -> headers.clear());
    }

    static void assertUnmodifiableCollection(Headers headers) {
        var entry = new AbstractMap.SimpleEntry<>("n", List.of("n"));

        assertThrows(UOP, () -> headers.values().remove(List.of("Bar")));
        assertThrows(UOP, () -> headers.values().removeAll(List.of("Bar")));
        assertThrows(UOP, () -> headers.keySet().remove("Foo"));
        assertThrows(UOP, () -> headers.keySet().removeAll(List.of("Foo")));
        assertThrows(UOP, () -> headers.entrySet().remove(entry));
        assertThrows(UOP, () -> headers.entrySet().removeAll(List.of(entry)));
    }

    static void assertUnmodifiableList(Headers headers) {
        assertThrows(UOP, () -> headers.get("Foo").remove(0));
        assertThrows(UOP, () -> headers.get("foo").remove(0));
        assertThrows(UOP, () -> headers.values().stream().findFirst().orElseThrow().remove(0));
        assertThrows(UOP, () -> headers.entrySet().stream().findFirst().orElseThrow().getValue().remove(0));
    }

    static class TestHttpExchange extends StubHttpExchange {
        final UnmodifiableHeaders headers;

        TestHttpExchange(Headers headers) {
            this.headers = new UnmodifiableHeaders(headers);
        }

        @Override
        public Headers getRequestHeaders() {
            return headers;
        }
    }

    static class StubHttpExchange extends HttpExchange {
        @Override public Headers getRequestHeaders() { return null; }
        @Override public Headers getResponseHeaders() { return null; }
        @Override public URI getRequestURI() { return null; }
        @Override public String getRequestMethod() { return null; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() { }
        @Override public InputStream getRequestBody() { return null; }
        @Override public OutputStream getResponseBody() { return null; }
        @Override public void sendResponseHeaders(int rCode, long responseLength) { }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public int getResponseCode() { return 0; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public String getProtocol() { return null; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { }
        @Override public void setStreams(InputStream i, OutputStream o) { }
        @Override public HttpPrincipal getPrincipal() { return null; }
    }
}
