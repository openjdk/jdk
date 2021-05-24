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

import java.util.List;
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
        var h1 = new Headers();
        assertTrue(h1.isEmpty());

        var h2 = new Headers(h1);
        assertTrue(h2.isEmpty());

        var h3 = new Headers(new UnmodifiableHeaders(h1));
        assertTrue(h3.isEmpty());
        h3.put("foo", List.of("bar"));  // not unmodifiable
    }

    @Test
    public static void testNull() {
        new Headers().add(null, "value");
        new Headers().set(null, "value");

        assertThrows(NPE, () -> new Headers().add("name", null));
        assertThrows(NPE, () -> new Headers().set("name", null));

        assertThrows(NPE, () -> Headers.of((String[])null));
        assertThrows(NPE, () -> Headers.of(null, "value"));
        assertThrows(NPE, () -> Headers.of("name", null));

        assertThrows(NPE, () -> Headers.of((Headers)null));
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
