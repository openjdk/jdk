/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.Test;

import java.util.*;
import java.util.function.Function;
import java.util.stream.*;

import static java.util.Comparator.*;
import static java.util.stream.LambdaTestHelpers.*;
import static org.testng.Assert.assertEquals;


/**
 * ToListOpTest
 */
@Test
public class ToListOpTest extends OpTestCase {

    public void testToList() {
        assertCountSum(countTo(0).stream().toList(), 0, 0);
        assertCountSum(countTo(10).stream().toList(), 10, 55);
    }

    private void checkUnmodifiable(List<Integer> list) {
        try {
            list.add(Integer.MIN_VALUE);
            fail("List.add did not throw UnsupportedOperationException");
        } catch (UnsupportedOperationException ignore) { }

        if (list.size() > 0) {
            try {
                list.set(0, Integer.MAX_VALUE);
                fail("List.set did not throw UnsupportedOperationException");
            } catch (UnsupportedOperationException ignore) { }
        }
    }

    @Test(dataProvider = "withNull:StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testOps(String name, TestData.OfRef<Integer> data) {
        List<Integer> objects = exerciseTerminalOps(data, s -> s.toList());
        checkUnmodifiable(objects);
    }

    @Test(dataProvider = "withNull:StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testDefaultOps(String name, TestData.OfRef<Integer> data) {
        List<Integer> objects = exerciseTerminalOps(data, s -> DefaultMethodStreams.delegateTo(s).toList());
        checkUnmodifiable(objects);
    }

    @Test(dataProvider = "withNull:StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testOpsWithMap(String name, TestData.OfRef<Integer> data) {
        // Retain the size of the source
        // This should kick in the parallel evaluation optimization for tasks stuffing elements into a shared array

        List<Integer> objects = exerciseTerminalOps(data, s -> s.map(i -> i == null ? 0 : (Integer) (i + i)), s -> s.toList());
        assertTrue(objects.size() == data.size());
    }

    @Test(dataProvider = "withNull:StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testOpsWithSorted(String name, TestData.OfRef<Integer> data) {
        // Retain the size of the source
        // This should kick in the parallel evaluation optimization for tasks stuffing elements into a shared array

        List<Integer> objects = exerciseTerminalOps(data, s -> s.sorted(nullsLast(naturalOrder())), s -> s.toList());
        assertTrue(objects.size() == data.size());
    }

    @Test(dataProvider = "withNull:StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testOpsWithFlatMap(String name, TestData.OfRef<Integer> data) {
        // Double the size of the source
        // Fixed size optimizations will not be used

        List<Object> objects = exerciseTerminalOps(data,
                                                   s -> s.flatMap(e -> Arrays.stream(new Object[] { e, e })),
                                                   s -> s.toList());
        assertTrue(objects.size() == data.size() * 2);
    }

    @Test(dataProvider = "withNull:StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testOpsWithFilter(String name, TestData.OfRef<Integer> data) {
        // Reduce the size of the source
        // Fixed size optimizations will not be used

        exerciseTerminalOps(data, s -> s.filter(i -> i == null ? false : LambdaTestHelpers.pEven.test(i)), s -> s.toList());
    }

    private List<Function<Stream<Integer>, Stream<Integer>>> uniqueAndSortedPermutations =
            LambdaTestHelpers.permuteStreamFunctions(Arrays.asList(
                    s -> s.distinct(),
                    s -> s.distinct(),
                    s -> s.sorted(),
                    s -> s.sorted()
            ));

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testDistinctAndSortedPermutations(String name, TestData.OfRef<Integer> data) {
        for (Function<Stream<Integer>, Stream<Integer>> f : uniqueAndSortedPermutations) {
            exerciseTerminalOps(data, f, s -> s.toList());
        }
    }

    private List<Function<Stream<Integer>, Stream<Integer>>> statefulOpPermutations =
            LambdaTestHelpers.permuteStreamFunctions(Arrays.asList(
                    s -> s.limit(10),
                    s -> s.distinct(),
                    s -> s.sorted()
            ));

    private <T extends Object> ResultAsserter<List<T>> statefulOpResultAsserter(TestData.OfRef<Integer> data) {
        return (act, exp, ord, par) -> {
            if (par) {
                if (!data.isOrdered()) {
                    // Relax the checking if the data source is unordered
                    // It is not exactly possible to determine if the limit
                    // operation is present and if it is before or after
                    // the sorted operation
                    // If the limit operation is present and before the sorted
                    // operation then the sub-set output after limit is a
                    // non-deterministic sub-set of the source
                    List<Integer> expected = new ArrayList<>();
                    data.forEach(expected::add);

                    assertEquals(act.size(), exp.size());
                    assertTrue(expected.containsAll(act));
                    return;
                }
                else if (!ord) {
                    LambdaTestHelpers.assertContentsUnordered(act, exp);
                    return;
                }
            }
            assertEquals(act, exp);
        };
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class,
          groups = { "serialization-hostile" })
    public void testStatefulOpPermutations(String name, TestData.OfRef<Integer> data) {
        for (Function<Stream<Integer>, Stream<Integer>> f : statefulOpPermutations) {
            withData(data).terminal(f, s -> s.toList())
                    .resultAsserter(statefulOpResultAsserter(data))
                    .exercise();
        }
    }

}
