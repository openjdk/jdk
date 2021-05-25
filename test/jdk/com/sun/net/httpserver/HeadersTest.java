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
 * @run testng/othervm HeadersTest
 */

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import com.sun.net.httpserver.Headers;
import org.testng.annotations.Test;
import sun.net.httpserver.UnmodifiableHeaders;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class HeadersTest {

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;

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
        h2.put("foo", List.of("bar"));  // modifiable
        assertEquals(h2.get("foo"), List.of("bar"));
        assertEquals(h2.size(), 1);

        var h3 = new Headers(Map.of("foo", List.of("bar")));
        assertEquals(h3.get("foo"), List.of("bar"));
        assertEquals(h3.size(), 1);
    }

    @Test
    public static void testNull() {
        new Headers().add(null, "bar");
        new Headers().set(null, "bar");

        assertThrows(NPE, () -> new Headers().add("foo", null));
        assertThrows(NPE, () -> new Headers().set("foo", null));

        final var list = new LinkedList<String>();
        list.add(null);
        assertThrows(NPE, () -> new Headers().put("foo", list));

        assertThrows(NPE, () -> Headers.of((String[])null));
        assertThrows(NPE, () -> Headers.of(null, "bar"));
        assertThrows(NPE, () -> Headers.of("foo", null));

        assertThrows(NPE, () -> Headers.of((Map<String, List<String>>) null));

        final var m1 = new HashMap<String, List<String>>();
        m1.put(null, List.of("bar"));
        assertThrows(NPE, () -> Headers.of(m1));

        final var m2 = new HashMap<String, List<String>>();
        m2.put("foo", null);
        assertThrows(NPE, () -> Headers.of(m2));

        final var m3 = new HashMap<String, List<String>>();
        m3.put("foo", list);
        assertThrows(NPE, () -> Headers.of(m3));
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
