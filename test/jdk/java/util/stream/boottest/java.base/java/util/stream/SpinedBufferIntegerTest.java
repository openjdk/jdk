/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SpinedBufferIntegerTest extends AbstractSpinedBufferTest {

    public static Stream<Arguments> createSpinedBuffer() {
        List<Arguments> params = new ArrayList<>();

        for (int size : SIZES) {
            int[] array = IntStream.range(0, size).toArray();

            SpinedBuffer<Integer> sb = new SpinedBuffer<>();
            Arrays.stream(array).boxed().forEach(sb);
            params.add(Arguments.of(array, sb));

            sb = new SpinedBuffer<>(size / 2);
            Arrays.stream(array).boxed().forEach(sb);
            params.add(Arguments.of(array, sb));

            sb = new SpinedBuffer<>(size);
            Arrays.stream(array).boxed().forEach(sb);
            params.add(Arguments.of(array, sb));

            sb = new SpinedBuffer<>(size * 2);
            Arrays.stream(array).boxed().forEach(sb);
            params.add(Arguments.of(array, sb));
        }

        return params.stream();
    }

    @ParameterizedTest
    @MethodSource("createSpinedBuffer")
    public void testSpliterator(int[] array, SpinedBuffer<Integer> sb) {
        assertEquals(array.length, sb.count());
        assertEquals(sb.count(), sb.spliterator().getExactSizeIfKnown());

        SpliteratorTestHelper.testSpliterator(sb::spliterator);
    }

    @ParameterizedTest
    @MethodSource("createSpinedBuffer")
    @Tag("serialization-hostile")
    public void testLastSplit(int[] array, SpinedBuffer<Integer> sb) {
        Spliterator<Integer> spliterator = sb.spliterator();
        Spliterator<Integer> split = spliterator.trySplit();
        long splitSizes = (split == null) ? 0 : split.getExactSizeIfKnown();
        long lastSplitSize = spliterator.getExactSizeIfKnown();
        splitSizes += lastSplitSize;

        assertEquals(array.length, splitSizes);

        List<Integer> contentOfLastSplit = new ArrayList<>();
        spliterator.forEachRemaining(contentOfLastSplit::add);

        assertEquals(lastSplitSize, contentOfLastSplit.size());

        List<Integer> end = Arrays.stream(array)
                .boxed()
                .skip(array.length - lastSplitSize)
                .collect(Collectors.toList());
        assertEquals(end, contentOfLastSplit);
    }

    @Test
    @Tag("serialization-hostile")
    public void testSpinedBuffer() {
        List<Integer> list1 = new ArrayList<>();
        List<Integer> list2 = new ArrayList<>();
        SpinedBuffer<Integer> sb = new SpinedBuffer<>();
        for (int i = 0; i < TEST_SIZE; i++) {
            list1.add(i);
            sb.accept(i);
        }
        Iterator<Integer> it = sb.iterator();
        for (int i = 0; i < TEST_SIZE; i++) {
            list2.add(it.next());
        }
        assertFalse(it.hasNext());
        assertEquals(list2, list1);

        for (int i = 0; i < TEST_SIZE; i++) {
            assertEquals((Integer) i, sb.get(i));
        }

        list2.clear();
        sb.forEach(list2::add);
        assertEquals(list2, list1);
        Integer[] array = sb.asArray(LambdaTestHelpers.integerArrayGenerator);
        list2.clear();
        for (Integer i : array) {
            list2.add(i);
        }
        assertEquals(list2, list1);
    }
}
