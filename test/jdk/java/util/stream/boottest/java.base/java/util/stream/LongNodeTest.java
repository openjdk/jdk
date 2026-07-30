/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
package java.util.stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.Spliterators;
import java.util.SpliteratorTestHelper;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LongNodeTest extends OpTestCase {

    public static Stream<Arguments> nodes() {
        List<Arguments> params = new ArrayList<>();

        for (int size : Arrays.asList(0, 1, 4, 15, 16, 17, 127, 128, 129, 1000)) {
            long[] array = new long[size];
            for (int i = 0; i < array.length; i++) {
                array[i] = i;
            }

            List<Node<Long>> nodes = new ArrayList<>();

            nodes.add(Nodes.node(array));
            nodes.add(degenerateTree(Spliterators.iterator(Arrays.spliterator(array))));
            nodes.add(tree(toList(array), l -> Nodes.node(toLongArray(l))));
            nodes.add(fill(array, Nodes.longBuilder(array.length)));
            nodes.add(fill(array, Nodes.longBuilder()));

            for (Node<Long> node : nodes) {
                params.add(Arguments.of(array, node));
            }

        }

        return params.stream();
    }

    private static void assertEqualsListLongArray(long[] expected, List<Long> actual) {
        assertEquals(expected.length, actual.size());
        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], (long) actual.get(i));
    }

    private static List<Long> toList(long[] a) {
        List<Long> l = new ArrayList<>();
        for (long i : a) {
            l.add(i);
        }

        return l;
    }

    private static long[] toLongArray(List<Long> l) {
        long[] a = new long[l.size()];

        int i = 0;
        for (Long e : l) {
            a[i++] = e;
        }
        return a;
    }

    private static Node.OfLong fill(long[] array, Node.Builder.OfLong nb) {
        nb.begin(array.length);
        for (long i : array)
            nb.accept(i);
        nb.end();
        return nb.build();
    }

    private static Node.OfLong degenerateTree(PrimitiveIterator.OfLong it) {
        if (!it.hasNext()) {
            return Nodes.node(new long[0]);
        }

        long i = it.nextLong();
        if (it.hasNext()) {
            return new Nodes.ConcNode.OfLong(Nodes.node(new long[] {i}), degenerateTree(it));
        }
        else {
            return Nodes.node(new long[] {i});
        }
    }

    private static Node.OfLong tree(List<Long> l, Function<List<Long>, Node.OfLong> m) {
        if (l.size() < 3) {
            return m.apply(l);
        }
        else {
            return new Nodes.ConcNode.OfLong(
                    tree(l.subList(0, l.size() / 2), m),
                    tree(l.subList(l.size() / 2, l.size()), m));
        }
    }

    @ParameterizedTest
    @MethodSource("nodes")
    public void testAsArray(long[] array, Node.OfLong n) {
        assertArrayEquals(array, n.asPrimitiveArray());
    }

    @ParameterizedTest
    @MethodSource("nodes")
    public void testFlattenAsArray(long[] array, Node.OfLong n) {
        assertArrayEquals(array, Nodes.flattenLong(n).asPrimitiveArray());
    }

    @ParameterizedTest
    @MethodSource("nodes")
    public void testCopyTo(long[] array, Node.OfLong n) {
        long[] copy = new long[(int) n.count()];
        n.copyInto(copy, 0);

        assertArrayEquals(array, copy);
    }

    @ParameterizedTest
    @MethodSource("nodes")
    @Tag("serialization-hostile")
    public void testForEach(long[] array, Node.OfLong n) {
        List<Long> l = new ArrayList<>((int) n.count());
        n.forEach((long e) -> {
            l.add(e);
        });

        assertEqualsListLongArray(array, l);
    }

    @ParameterizedTest
    @MethodSource("nodes")
    public void testStreams(long[] array, Node.OfLong n) {
        TestData.OfLong data = TestData.Factory.ofNode("Node", n);

        exerciseOps(data, s -> s);

        exerciseTerminalOps(data, s -> s.toArray());
    }

    @ParameterizedTest
    @MethodSource("nodes")
    public void testSpliterator(long[] array, Node.OfLong n) {
        SpliteratorTestHelper.testLongSpliterator(n::spliterator);
    }

    @ParameterizedTest
    @MethodSource("nodes")
    public void testTruncate(long[] array, Node.OfLong n) {
        int[] nums = new int[] { 0, 1, array.length / 2, array.length - 1, array.length };
        for (int start : nums)
            for (int end : nums) {
                if (start < 0 || end < 0 || end < start || end > array.length)
                    continue;
                Node.OfLong slice = n.truncate(start, end, Long[]::new);
                long[] asArray = slice.asPrimitiveArray();
                for (int k = start; k < end; k++)
                    assertEquals(array[k], asArray[k - start]);
            }
    }
}
