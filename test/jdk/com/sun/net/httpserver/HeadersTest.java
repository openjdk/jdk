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
 * @run testng HeadersTest
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.sun.net.httpserver.Headers;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
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
