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

import org.testng.annotations.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LambdaTestHelpers;
import java.util.stream.LongStream;
import java.util.stream.OpTestCase;
import java.util.stream.Stream;
import java.util.stream.StreamTestDataProvider;
import java.util.stream.TestData;

import static java.util.stream.LambdaTestHelpers.*;

/**
 * SliceOpTest
 *
 * @author Brian Goetz
 */
@Test
public class SliceOpTest extends OpTestCase {

    public void testSkip() {
        assertCountSum(countTo(0).stream().substream(0), 0, 0);
        assertCountSum(countTo(0).stream().substream(4), 0, 0);
        assertCountSum(countTo(4).stream().substream(4), 0, 0);
        assertCountSum(countTo(4).stream().substream(2), 2, 7);
        assertCountSum(countTo(4).stream().substream(0), 4, 10);

        assertCountSum(countTo(0).parallelStream().substream(0), 0, 0);
        assertCountSum(countTo(0).parallelStream().substream(4), 0, 0);
        assertCountSum(countTo(4).parallelStream().substream(4), 0, 0);
        assertCountSum(countTo(4).parallelStream().substream(2), 2, 7);
        assertCountSum(countTo(4).parallelStream().substream(0), 4, 10);

        exerciseOps(Collections.emptyList(), s -> s.substream(0), Collections.emptyList());
        exerciseOps(Collections.emptyList(), s -> s.substream(10), Collections.emptyList());

        exerciseOps(countTo(1), s -> s.substream(0), countTo(1));
        exerciseOps(countTo(1), s -> s.substream(1), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(0), countTo(100));
        exerciseOps(countTo(100), s -> s.substream(10), range(11, 100));
        exerciseOps(countTo(100), s -> s.substream(100), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(200), Collections.emptyList());
    }

    public void testLimit() {
        assertCountSum(countTo(0).stream().limit(4), 0, 0);
        assertCountSum(countTo(2).stream().limit(4), 2, 3);
        assertCountSum(countTo(4).stream().limit(4), 4, 10);
        assertCountSum(countTo(8).stream().limit(4), 4, 10);

        assertCountSum(countTo(0).parallelStream().limit(4), 0, 0);
        assertCountSum(countTo(2).parallelStream().limit(4), 2, 3);
        assertCountSum(countTo(4).parallelStream().limit(4), 4, 10);
        assertCountSum(countTo(8).parallelStream().limit(4), 4, 10);

        exerciseOps(Collections.emptyList(), s -> s.limit(0), Collections.emptyList());
        exerciseOps(Collections.emptyList(), s -> s.limit(10), Collections.emptyList());
        exerciseOps(countTo(1), s -> s.limit(0), Collections.emptyList());
        exerciseOps(countTo(1), s -> s.limit(1), countTo(1));
        exerciseOps(countTo(100), s -> s.limit(0), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.limit(10), countTo(10));
        exerciseOps(countTo(100), s -> s.limit(10).limit(10), countTo(10));
        exerciseOps(countTo(100), s -> s.limit(100), countTo(100));
        exerciseOps(countTo(100), s -> s.limit(100).limit(10), countTo(10));
        exerciseOps(countTo(100), s -> s.limit(200), countTo(100));
    }

    public void testSkipLimit() {
        exerciseOps(Collections.emptyList(), s -> s.substream(0).limit(0), Collections.emptyList());
        exerciseOps(Collections.emptyList(), s -> s.substream(0).limit(10), Collections.emptyList());
        exerciseOps(Collections.emptyList(), s -> s.substream(10).limit(0), Collections.emptyList());
        exerciseOps(Collections.emptyList(), s -> s.substream(10).limit(10), Collections.emptyList());

        exerciseOps(countTo(100), s -> s.substream(0).limit(100), countTo(100));
        exerciseOps(countTo(100), s -> s.substream(0).limit(10), countTo(10));
        exerciseOps(countTo(100), s -> s.substream(0).limit(0), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(10).limit(100), range(11, 100));
        exerciseOps(countTo(100), s -> s.substream(10).limit(10), range(11, 20));
        exerciseOps(countTo(100), s -> s.substream(10).limit(0), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(100).limit(100), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(100).limit(10), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(100).limit(0), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(200).limit(100), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(200).limit(10), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(200).limit(0), Collections.emptyList());
    }

    public void testSlice() {
        exerciseOps(Collections.emptyList(), s -> s.substream(0, 0), Collections.emptyList());
        exerciseOps(Collections.emptyList(), s -> s.substream(0, 10), Collections.emptyList());
        exerciseOps(Collections.emptyList(), s -> s.substream(10, 10), Collections.emptyList());
        exerciseOps(Collections.emptyList(), s -> s.substream(10, 20), Collections.emptyList());

        exerciseOps(countTo(100), s -> s.substream(0, 100), countTo(100));
        exerciseOps(countTo(100), s -> s.substream(0, 10), countTo(10));
        exerciseOps(countTo(100), s -> s.substream(0, 0), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(10, 110), range(11, 100));
        exerciseOps(countTo(100), s -> s.substream(10, 20), range(11, 20));
        exerciseOps(countTo(100), s -> s.substream(10, 10), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(100, 200), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(100, 110), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(100, 100), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(200, 300), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(200, 210), Collections.emptyList());
        exerciseOps(countTo(100), s -> s.substream(200, 200), Collections.emptyList());
    }

    private int sliceSize(int dataSize, int skip, int limit) {
        int size = Math.max(0, dataSize - skip);
        if (limit >= 0)
            size = Math.min(size, limit);
        return size;
    }

    private int sliceSize(int dataSize, int skip) {
        return Math.max(0, dataSize - skip);
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testSkipOps(String name, TestData.OfRef<Integer> data) {
        List<Integer> skips = sizes(data.size());

        for (int s : skips) {
            setContext("skip", s);
            testSliceMulti(data,
                           sliceSize(data.size(), s),
                           st -> st.substream(s),
                           st -> st.substream(s),
                           st -> st.substream(s),
                           st -> st.substream(s));

            testSliceMulti(data,
                           sliceSize(sliceSize(data.size(), s), s/2),
                           st -> st.substream(s).substream(s / 2),
                           st -> st.substream(s).substream(s / 2),
                           st -> st.substream(s).substream(s / 2),
                           st -> st.substream(s).substream(s / 2));
        }
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testSkipLimitOps(String name, TestData.OfRef<Integer> data) {
        List<Integer> skips = sizes(data.size());
        List<Integer> limits = skips;

        for (int s : skips) {
            setContext("skip", s);
            for (int l : limits) {
                setContext("limit", l);
                testSliceMulti(data,
                               sliceSize(sliceSize(data.size(), s), 0, l),
                               st -> st.substream(s).limit(l),
                               st -> st.substream(s).limit(l),
                               st -> st.substream(s).limit(l),
                               st -> st.substream(s).limit(l));

                testSliceMulti(data,
                               sliceSize(data.size(), s, l),
                                st -> st.substream(s, l+s),
                                st -> st.substream(s, l+s),
                                st -> st.substream(s, l+s),
                                st -> st.substream(s, l+s));
            }
        }
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testLimitOps(String name, TestData.OfRef<Integer> data) {
        List<Integer> limits = sizes(data.size());

        for (int l : limits) {
            setContext("limit", l);
            testSliceMulti(data,
                           sliceSize(data.size(), 0, l),
                           st -> st.limit(l),
                           st -> st.limit(l),
                           st -> st.limit(l),
                           st -> st.limit(l));
        }

        for (int l : limits) {
            setContext("limit", l);
            testSliceMulti(data,
                           sliceSize(sliceSize(data.size(), 0, l), 0, l / 2),
                           st -> st.limit(l).limit(l / 2),
                           st -> st.limit(l).limit(l / 2),
                           st -> st.limit(l).limit(l / 2),
                           st -> st.limit(l).limit(l / 2));
        }
    }

    private ResultAsserter<Iterable<Integer>> sliceResultAsserter(Iterable<Integer> data,
                                                                  int expectedSize) {
        return (act, exp, ord, par) -> {
            if (par & !ord) {
                List<Integer> expected = new ArrayList<>();
                data.forEach(expected::add);

                List<Integer> actual = new ArrayList<>();
                act.forEach(actual::add);

                assertEquals(actual.size(), expectedSize);
                assertTrue(expected.containsAll(actual));
            }
            else {
                LambdaTestHelpers.assertContents(act, exp);
            }
        };
    }

    private void testSliceMulti(TestData.OfRef<Integer> data,
                                int expectedSize,
                                Function<Stream<Integer>, Stream<Integer>> mRef,
                                Function<IntStream, IntStream> mInt,
                                Function<LongStream, LongStream> mLong,
                                Function<DoubleStream, DoubleStream> mDouble) {

        @SuppressWarnings({ "rawtypes", "unchecked" })
        Function<Stream<Integer>, Stream<Integer>>[] ms = new Function[4];
        ms[0] = mRef;
        ms[1] = s -> mInt.apply(s.mapToInt(e -> e)).mapToObj(e -> e);
        ms[2] = s -> mLong.apply(s.mapToLong(e -> e)).mapToObj(e -> (int) e);
        ms[3] = s -> mDouble.apply(s.mapToDouble(e -> e)).mapToObj(e -> (int) e);
        testSliceMulti(data, expectedSize, ms);
    }

    @SafeVarargs
    private final void testSliceMulti(TestData.OfRef<Integer> data,
                                      int expectedSize,
                                      Function<Stream<Integer>, Stream<Integer>>... ms) {
        for (int i = 0; i < ms.length; i++) {
            setContext("mIndex", i);
            Function<Stream<Integer>, Stream<Integer>> m = ms[i];
            Collection<Integer> sr = withData(data)
                    .stream(m)
                    .resultAsserter(sliceResultAsserter(data, expectedSize))
                    .exercise();
            assertEquals(sr.size(), expectedSize);
        }
    }

    public void testLimitSort() {
        List<Integer> l = countTo(100);
        Collections.reverse(l);
        exerciseOps(l, s -> s.limit(10).sorted(Comparator.naturalOrder()));
    }

    @Test(groups = { "serialization-hostile" })
    public void testLimitShortCircuit() {
        for (int l : Arrays.asList(0, 10)) {
            setContext("l", l);
            AtomicInteger ai = new AtomicInteger();
            countTo(100).stream()
                    .peek(i -> ai.getAndIncrement())
                    .limit(l).toArray();
            // For the case of a zero limit, one element will get pushed through the sink chain
            assertEquals(ai.get(), l, "tee block was called too many times");
        }
    }

    private List<Integer> sizes(int size) {
        if (size < 4) {
            return Arrays.asList(0, 1, 2, 3, 4, 6);
        }
        else {
            return Arrays.asList(0, 1, size / 2, size - 1, size, size + 1, 2 * size);
        }
    }
}
