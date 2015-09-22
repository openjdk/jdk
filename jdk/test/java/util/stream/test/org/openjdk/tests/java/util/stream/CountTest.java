/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Tests counting of streams
 * @bug 8031187 8067969 8075307
 */

package org.openjdk.tests.java.util.stream;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.DoubleStreamTestDataProvider;
import java.util.stream.IntStream;
import java.util.stream.IntStreamTestDataProvider;
import java.util.stream.LongStream;
import java.util.stream.LongStreamTestDataProvider;
import java.util.stream.OpTestCase;
import java.util.stream.Stream;
import java.util.stream.StreamTestDataProvider;
import java.util.stream.TestData;

import org.testng.annotations.Test;

public class CountTest extends OpTestCase {

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testOps(String name, TestData.OfRef<Integer> data) {
        long expectedCount = data.size();

        withData(data).
                terminal(Stream::count).
                expectedResult(expectedCount).
                exercise();

        // Test with an unknown sized stream
        withData(data).
                terminal(s -> s.filter(e -> true), Stream::count).
                expectedResult(expectedCount).
                exercise();

        // Test counting collector
        withData(data).
                terminal(s -> s, s -> s.collect(Collectors.counting())).
                expectedResult(expectedCount).
                exercise();

        // Test with stateful distinct op that is a barrier or lazy
        // depending if source is not already distinct and encounter order is
        // preserved or not
        expectedCount = data.into(new HashSet<>()).size();
        withData(data).
                terminal(Stream::distinct, Stream::count).
                expectedResult(expectedCount).
                exercise();
        withData(data).
                terminal(s -> s.unordered().distinct(), Stream::count).
                expectedResult(expectedCount).
                exercise();
    }

    @Test(dataProvider = "IntStreamTestData", dataProviderClass = IntStreamTestDataProvider.class)
    public void testOps(String name, TestData.OfInt data) {
        long expectedCount = data.size();

        withData(data).
                terminal(IntStream::count).
                expectedResult(expectedCount).
                exercise();

        withData(data).
                terminal(s -> s.filter(e -> true), IntStream::count).
                expectedResult(expectedCount).
                exercise();

        expectedCount = data.into(new HashSet<>()).size();
        withData(data).
                terminal(IntStream::distinct, IntStream::count).
                expectedResult(expectedCount).
                exercise();
        withData(data).
                terminal(s -> s.unordered().distinct(), IntStream::count).
                expectedResult(expectedCount).
                exercise();
    }

    @Test(dataProvider = "LongStreamTestData", dataProviderClass = LongStreamTestDataProvider.class)
    public void testOps(String name, TestData.OfLong data) {
        long expectedCount = data.size();

        withData(data).
                terminal(LongStream::count).
                expectedResult(expectedCount).
                exercise();

        withData(data).
                terminal(s -> s.filter(e -> true), LongStream::count).
                expectedResult(expectedCount).
                exercise();

        expectedCount = data.into(new HashSet<>()).size();
        withData(data).
                terminal(LongStream::distinct, LongStream::count).
                expectedResult(expectedCount).
                exercise();
        withData(data).
                terminal(s -> s.unordered().distinct(), LongStream::count).
                expectedResult(expectedCount).
                exercise();
    }

    @Test(dataProvider = "DoubleStreamTestData", dataProviderClass = DoubleStreamTestDataProvider.class)
    public void testOps(String name, TestData.OfDouble data) {
        long expectedCount = data.size();

        withData(data).
                terminal(DoubleStream::count).
                expectedResult(expectedCount).
                exercise();

        withData(data).
                terminal(s -> s.filter(e -> true), DoubleStream::count).
                expectedResult(expectedCount).
                exercise();

        expectedCount = data.into(new HashSet<>()).size();
        withData(data).
                terminal(DoubleStream::distinct, DoubleStream::count).
                expectedResult(expectedCount).
                exercise();
        withData(data).
                terminal(s -> s.unordered().distinct(), DoubleStream::count).
                expectedResult(expectedCount).
                exercise();
    }

    public void testNoEvaluationForSizedStream() {
        {
            AtomicInteger ai = new AtomicInteger();
            Stream.of(1, 2, 3, 4).peek(e -> ai.getAndIncrement()).count();
            assertEquals(ai.get(), 0);

            Stream.of(1, 2, 3, 4).peek(e -> ai.getAndIncrement()).parallel().count();
            assertEquals(ai.get(), 0);
        }

        {
            AtomicInteger ai = new AtomicInteger();
            IntStream.of(1, 2, 3, 4).peek(e -> ai.getAndIncrement()).count();
            assertEquals(ai.get(), 0);

            IntStream.of(1, 2, 3, 4).peek(e -> ai.getAndIncrement()).parallel().count();
            assertEquals(ai.get(), 0);
        }

        {
            AtomicInteger ai = new AtomicInteger();
            LongStream.of(1, 2, 3, 4).peek(e -> ai.getAndIncrement()).count();
            assertEquals(ai.get(), 0);

            LongStream.of(1, 2, 3, 4).peek(e -> ai.getAndIncrement()).parallel().count();
            assertEquals(ai.get(), 0);
        }

        {
            AtomicInteger ai = new AtomicInteger();
            DoubleStream.of(1, 2, 3, 4).peek(e -> ai.getAndIncrement()).count();
            assertEquals(ai.get(), 0);

            DoubleStream.of(1, 2, 3, 4).peek(e -> ai.getAndIncrement()).parallel().count();
            assertEquals(ai.get(), 0);
        }
    }
}
