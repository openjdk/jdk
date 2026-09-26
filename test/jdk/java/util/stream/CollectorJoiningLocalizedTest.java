/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;

import java.text.ListFormat;
import java.util.Locale;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8391789
 * @summary Tests for joiningConjunctively(Locale) and joiningDisjunctively(Locale) Collectors
 * @run junit CollectorJoiningLocalizedTest
 */
public class CollectorJoiningLocalizedTest {
    @Test
    void joiningConjunctively() {
        assertThrows(NullPointerException.class, () -> Collectors.joiningConjunctively(null));
        Collector<CharSequence, ?, String> collector = Collectors.joiningConjunctively(Locale.ENGLISH);
        assertThrows(IllegalArgumentException.class, () -> Stream.<CharSequence>empty().collect(collector));
        assertEquals("a",
                Stream.of("a").collect(collector));
        assertEquals("a and b",
                Stream.of("a", "b").collect(collector));
        assertEquals("a, b, and c",
                Stream.of("a", "b", "c").collect(collector));
        assertEquals("a, b, and c",
                Stream.of(new StringBuilder("a"), "b", new StringBuffer("c")).collect(collector));
        assertEquals("a, b, c, and null",
                Stream.of(new StringBuilder("a"), "b", new StringBuffer("c"), null).collect(collector));
        assertEquals("1, 2, 3, 4, 5, 6, 7, 8, and 9",
                IntStream.range(1, 10).mapToObj(String::valueOf).collect(collector));
        assertEquals("1, 2, 3, 4, 5, 6, 7, 8, and 9",
                IntStream.range(1, 10).parallel().mapToObj(String::valueOf).collect(collector));
    }

    @Test
    void joiningDisjunctively() {
        assertThrows(NullPointerException.class, () -> Collectors.joiningDisjunctively(null));
        Collector<CharSequence, ?, String> collector = Collectors.joiningDisjunctively(Locale.ENGLISH);
        assertThrows(IllegalArgumentException.class, () -> Stream.<CharSequence>empty().collect(collector));
        assertEquals("a",
                Stream.of("a").collect(collector));
        assertEquals("a or b",
                Stream.of("a", "b").collect(collector));
        assertEquals("a, b, or c",
                Stream.of("a", "b", "c").collect(collector));
        assertEquals("a, b, or c",
                Stream.of(new StringBuilder("a"), "b", new StringBuffer("c")).collect(collector));
        assertEquals("a, b, c, or null",
                Stream.of(new StringBuilder("a"), "b", new StringBuffer("c"), null).collect(collector));
        assertEquals("1, 2, 3, 4, 5, 6, 7, 8, or 9",
                IntStream.range(1, 10).mapToObj(String::valueOf).collect(collector));
        assertEquals("1, 2, 3, 4, 5, 6, 7, 8, or 9",
                IntStream.range(1, 10).parallel().mapToObj(String::valueOf).collect(collector));
    }
}