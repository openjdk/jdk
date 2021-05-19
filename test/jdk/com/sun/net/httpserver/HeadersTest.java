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
 * @run testng/othervm HeadersTest
 */

import java.util.List;
import com.sun.net.httpserver.Headers;
import org.testng.annotations.Test;
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
    public static void testNull() {
        new Headers().add(null, "value");
        new Headers().set(null, "value");

        assertThrows(NPE, () -> new Headers().add("name", null));
        assertThrows(NPE, () -> new Headers().set("name", null));

        assertThrows(NPE, () -> Headers.of((String[])null));
        assertThrows(NPE, () -> Headers.of(null, "value"));
        assertThrows(NPE, () -> Headers.of("name", null));
    }

    @Test
    public static void testOfNumberOfElements() {
        assertThrows(IAE, () -> Headers.of(new String[] {}));
        assertThrows(IAE, () -> Headers.of("a"));
        assertThrows(IAE, () -> Headers.of("a", "b", "c"));
    }

    @Test
    public static void testOf() {
        final var h = Headers.of("a", "b", "c", "d");
        assertEquals(h.size(), 2);
        List.of("a", "c").forEach(n -> assertTrue(h.containsKey(n)));
        List.of("b", "d").forEach(v -> assertTrue(h.containsValue(List.of(v))));
    }

    @Test
    public static void testOfMultipleValues() {
        final var h = Headers.of("a", "b", "c", "d", "c", "e", "c", "f");
        assertEquals(h.size(), 2);
        List.of("a", "c").forEach(n -> assertTrue(h.containsKey(n)));
        List.of(List.of("b"), List.of("d", "e", "f")).forEach(v -> assertTrue(h.containsValue(v)));
    }
}
