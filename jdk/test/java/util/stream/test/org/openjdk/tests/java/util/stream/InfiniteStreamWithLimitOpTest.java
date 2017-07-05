/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.tests.java.util.stream;

import java.util.stream.OpTestCase;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.LambdaTestHelpers.assertContents;


@Test
public class InfiniteStreamWithLimitOpTest extends OpTestCase {

    private static final List<String> tenAs = Arrays.asList("A", "A", "A", "A", "A", "A", "A", "A", "A", "A");

    public void testRepeatLimit() {
        assertContents(Stream.generate(() -> "A").limit(10).iterator(), tenAs.iterator());
    }

    public void testIterateLimit() {
        assertContents(Stream.iterate("A", s -> s).limit(10).iterator(), tenAs.iterator());
    }

    public void testIterateFibLimit() {
        Stream<Integer> fib = Stream.iterate(new int[] {0, 1}, pair -> new int[] {pair[1], pair[0] + pair[1]})
                                    .map(pair -> pair[0]);

        assertContents(
                fib.limit(10).iterator(),
                Arrays.asList(0, 1, 1, 2, 3, 5, 8, 13, 21, 34).iterator());
    }

    public void testInfiniteWithLimitToShortCircuitTerminal() {
        Object[] array = Stream.generate(() -> 1).limit(4).toArray();
        assertEquals(4, array.length);
        array = Stream.generate(() -> 1).limit(4).filter(i -> true).toArray();
        assertEquals(4, array.length);
        List<Integer> result = Stream.generate(() -> 1).limit(4).collect(Collectors.toList());
        assertEquals(result, Arrays.asList(1, 1, 1, 1));
    }
}
