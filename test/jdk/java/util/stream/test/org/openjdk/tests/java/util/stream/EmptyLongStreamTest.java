/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongToDoubleFunction;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.testng.Assert.*;

/*
 * @test
 * @summary Checks that the EmptyLongStream is equivalent to the
 * old way of creating it with StreamSupport.
 * @author Heinz Kabutz
 */

public final class EmptyLongStreamTest extends EmptyBaseStreamTest {
    @Test
    public void testAll() {
        this.compare(LongStream.class,
                LongStream::empty, () -> StreamSupport.longStream(Spliterators.emptyLongSpliterator(), false)
        );
        this.compare(LongStream.class,
                () -> LongStream.empty().parallel(), () -> StreamSupport.longStream(Spliterators.emptyLongSpliterator(), true)
        );
    }

    public void testFilter(LongStream actual, LongStream expected) {
        actual = actual.filter(failing(LongPredicate.class));
        expected = expected.filter(failing(LongPredicate.class));
        var actualResult = actual.toArray();
        var expectedResult = expected.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testFilterExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "filter", LongPredicate.class);
    }

    public void testMap(LongStream actual, LongStream expected) {
        LongStream actualAsLongStream = actual.map(failing(LongUnaryOperator.class));
        LongStream expectedAsLongStream = expected.map(failing(LongUnaryOperator.class));
        var actualResult = actualAsLongStream.toArray();
        var expectedResult = expectedAsLongStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "map", LongUnaryOperator.class);
    }

    public void testMapToObj(LongStream actual, LongStream expected) {
        Stream<Long> actualAsStream = actual.mapToObj(failing(LongFunction.class));
        Stream<Long> expectedAsStream = expected.mapToObj(failing(LongFunction.class));
        var actualResult = actualAsStream.toArray();
        var expectedResult = expectedAsStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapToObjExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "mapToObj", LongFunction.class);
    }

    public void testMapToInt(LongStream actual, LongStream expected) {
        IntStream actualAsIntStream = actual.mapToInt(failing(LongToIntFunction.class));
        IntStream expectedAsIntStream = expected.mapToInt(failing(LongToIntFunction.class));
        var actualResult = actualAsIntStream.toArray();
        var expectedResult = expectedAsIntStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapToIntExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "mapToInt", LongToIntFunction.class);
    }

    public void testMapToDouble(LongStream actual, LongStream expected) {
        DoubleStream actualAsDoubleStream = actual.mapToDouble(failing(LongToDoubleFunction.class));
        DoubleStream expectedAsDoubleStream = expected.mapToDouble(failing(LongToDoubleFunction.class));
        var actualResult = actualAsDoubleStream.toArray();
        var expectedResult = expectedAsDoubleStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapToDoubleExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "mapToDouble", LongToDoubleFunction.class);
    }

    public void testFlatMap(LongStream actual, LongStream expected) {
        LongStream actualAsLongStream = actual.flatMap(failing(LongFunction.class));
        LongStream expectedAsLongStream = expected.flatMap(failing(LongFunction.class));
        var actualResult = actualAsLongStream.toArray();
        var expectedResult = expectedAsLongStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testFlatMapExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "flatMap", LongFunction.class);
    }

    public void testDistinct(LongStream actual, LongStream expected) {
        actual = actual.distinct();
        expected = expected.distinct();
        var actualResult = actual.toArray();
        var expectedResult = expected.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testDistinctExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "distinct");
    }

    public void testSorted(LongStream actual, LongStream expected) {
        actual = actual.sorted();
        expected = expected.sorted();
        var actualResult = actual.toArray();
        var expectedResult = expected.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testSortedExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "sorted");
    }

    public void testPeek(LongStream actual, LongStream expected) {
        actual.peek(failing(LongConsumer.class)).toArray();
        expected.peek(failing(LongConsumer.class)).toArray();
    }

    public void testPeekExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "peek", LongConsumer.class);
    }

    public void testLimit(LongStream actual, LongStream expected) {
        var actualResult = actual.limit(10).toArray();
        var expectedResult = expected.limit(10).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testLimitExceptions(LongStream actual, LongStream expected) {
        assertThrows(IllegalArgumentException.class, () -> actual.limit(-1));
        assertThrows(IllegalArgumentException.class, () -> expected.limit(-1));

        actual.limit(10);
        expected.limit(10);
        assertThrows(IllegalStateException.class, () -> actual.limit(10));
        assertThrows(IllegalStateException.class, () -> expected.limit(10));
    }

    public void testSkip(LongStream actual, LongStream expected) {
        var actualResult = actual.skip(10).toArray();
        var expectedResult = expected.skip(10).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testSkipExceptions(LongStream actual, LongStream expected) {
        assertThrows(IllegalArgumentException.class, () -> actual.skip(-1));
        assertThrows(IllegalArgumentException.class, () -> expected.skip(-1));

        actual.skip(0); // should be ok
        expected.skip(0); // should be ok
        actual.skip(0); // should be ok
        expected.skip(0); // should be ok

        actual.skip(10);
        expected.skip(10);
        assertThrows(IllegalStateException.class, () -> actual.skip(10));
        assertThrows(IllegalStateException.class, () -> expected.skip(10));
    }

    public void testForEach(LongStream actual, LongStream expected) {
        actual.forEach(failing(LongConsumer.class));
        expected.forEach(failing(LongConsumer.class));
    }

    public void testForEachExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "forEach", LongConsumer.class);
    }

    public void testForEachOrdered(LongStream actual, LongStream expected) {
        actual.forEachOrdered(failing(LongConsumer.class));
        expected.forEachOrdered(failing(LongConsumer.class));
    }

    public void testForEachOrderedExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "forEachOrdered", LongConsumer.class);
    }

    public void testToArray(LongStream actual, LongStream expected) {
        var actualResult = actual.toArray();
        var expectedResult = expected.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testToArrayExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "toArray");
    }

    public void testReduceLongBinaryOperator(LongStream actual, LongStream expected) {
        var actualResult = actual.reduce(failing(LongBinaryOperator.class));
        var expectedResult = expected.reduce(failing(LongBinaryOperator.class));

        compareResults(actualResult, expectedResult);
    }

    public void testReduceLongBinaryOperatorExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "reduce", LongBinaryOperator.class);
    }

    public void testReduceIdentityLongBinaryOperator(LongStream actual, LongStream expected) {
        var magicIdentity = 42L;
        var actualResult = actual.reduce(magicIdentity, failing(LongBinaryOperator.class));
        var expectedResult = expected.reduce(magicIdentity, failing(LongBinaryOperator.class));

        assertEquals(actualResult, magicIdentity);
        assertEquals(expectedResult, magicIdentity);
    }

    public void testReduceIdentityLongBinaryOperatorExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "reduce", long.class, LongBinaryOperator.class);
    }

    public void testCollect(LongStream actual, LongStream expected) {
        var magicIdentity = new Object();
        var actualResult = actual.collect(() -> magicIdentity, failing(ObjLongConsumer.class), failing(BiConsumer.class));
        var expectedResult = expected.collect(() -> magicIdentity, failing(ObjLongConsumer.class), failing(BiConsumer.class));

        assertSame(magicIdentity, actualResult);
        assertSame(magicIdentity, expectedResult);
    }

    public void testCollectExceptions(LongStream actual, LongStream expected) {
        Supplier<Object> supplier = Object::new;
        var accumulator = failing(ObjLongConsumer.class);
        var combiner = failing(BiConsumer.class);
        assertThrows(NullPointerException.class, () -> actual.collect(null, accumulator, combiner));
        assertThrows(NullPointerException.class, () -> expected.collect(null, accumulator, combiner));
        assertThrows(NullPointerException.class, () -> actual.collect(supplier, null, combiner));
        assertThrows(NullPointerException.class, () -> expected.collect(supplier, null, combiner));
        assertThrows(NullPointerException.class, () -> actual.collect(supplier, accumulator, null));
        assertThrows(NullPointerException.class, () -> expected.collect(supplier, accumulator, null));

        actual.collect(supplier, accumulator, combiner);
        expected.collect(supplier, accumulator, combiner);

        assertThrows(NullPointerException.class, () -> actual.collect(null, accumulator, combiner));
        assertThrows(NullPointerException.class, () -> expected.collect(null, accumulator, combiner));
        assertThrows(NullPointerException.class, () -> actual.collect(supplier, null, combiner));
        assertThrows(NullPointerException.class, () -> expected.collect(supplier, null, combiner));
        assertThrows(NullPointerException.class, () -> actual.collect(supplier, accumulator, null));
        assertThrows(NullPointerException.class, () -> expected.collect(supplier, accumulator, null));

        assertThrows(IllegalStateException.class, () -> actual.collect(supplier, accumulator, combiner));
        assertThrows(IllegalStateException.class, () -> expected.collect(supplier, accumulator, combiner));
    }

    public void testSum(LongStream actual, LongStream expected) {
        var actualResult = actual.sum();
        var expectedResult = expected.sum();

        assertEquals(actualResult, expectedResult);
        assertEquals(actualResult, 0);
    }

    public void testSumExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "sum");
    }

    public void testMin(LongStream actual, LongStream expected) {
        var actualResult = actual.min();
        var expectedResult = expected.min();
        compareResults(actualResult, expectedResult);
    }

    public void testMinExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "min");
    }

    public void testMax(LongStream actual, LongStream expected) {
        var actualResult = actual.max();
        var expectedResult = expected.max();
        compareResults(actualResult, expectedResult);
    }

    public void testMaxExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "max");
    }

    public void testCount(LongStream actual, LongStream expected) {
        var actualResult = actual.count();
        var expectedResult = expected.count();
        assertEquals(actualResult, expectedResult);
        assertEquals(actualResult, 0L);
    }

    public void testCountExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "count");
    }

    public void testAverage(LongStream actual, LongStream expected) {
        var actualResult = actual.average();
        var expectedResult = expected.average();
        compareResults(actualResult, expectedResult);
    }

    public void testAverageExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "average");
    }

    public void testSummaryStatistics(LongStream actual, LongStream expected) {
        var actualResult = actual.summaryStatistics().toString();
        var expectedResult = expected.summaryStatistics().toString();
        assertEquals(actualResult, expectedResult);
    }

    public void testSummaryStatisticsExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "summaryStatistics");
    }

    public void testAnyMatch(LongStream actual, LongStream expected) {
        var actualResult = actual.anyMatch(failing(LongPredicate.class));
        var expectedResult = expected.anyMatch(failing(LongPredicate.class));
        assertEquals(actualResult, expectedResult);
    }

    public void testAnyMatchExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "anyMatch", LongPredicate.class);
    }

    public void testAllMatch(LongStream actual, LongStream expected) {
        var actualResult = actual.allMatch(failing(LongPredicate.class));
        var expectedResult = expected.allMatch(failing(LongPredicate.class));
        assertEquals(actualResult, expectedResult);
    }

    public void testAllMatchExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "allMatch", LongPredicate.class);
    }

    public void testNoneMatch(LongStream actual, LongStream expected) {
        var actualResult = actual.noneMatch(failing(LongPredicate.class));
        var expectedResult = expected.noneMatch(failing(LongPredicate.class));
        assertEquals(actualResult, expectedResult);
    }

    public void testNoneMatchExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "noneMatch", LongPredicate.class);
    }

    public void testFindFirst(LongStream actual, LongStream expected) {
        var actualResult = actual.findFirst();
        var expectedResult = expected.findFirst();
        compareResults(actualResult, expectedResult);
    }

    public void testFindFirstExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "findFirst");
    }

    public void testFindAny(LongStream actual, LongStream expected) {
        var actualResult = actual.findAny();
        var expectedResult = expected.findAny();
        compareResults(actualResult, expectedResult);
    }

    public void testFindAnyExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "findAny");
    }

    public void testAsDoubleStream(LongStream actual, LongStream expected) {
        DoubleStream actualAsDoubleStream = actual.asDoubleStream();
        DoubleStream expectedAsDoubleStream = expected.asDoubleStream();
        double[] actualResult = actualAsDoubleStream.toArray();
        double[] expectedResult = expectedAsDoubleStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testAsDoubleStreamExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "asDoubleStream");
    }

    public void testBoxed(LongStream actual, LongStream expected) {
        Stream<Long> actualAsStream = actual.boxed();
        Stream<Long> expectedAsStream = expected.boxed();
        var actualResult = actualAsStream.toArray();
        var expectedResult = expectedAsStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testBoxedExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "boxed");
    }

    public void testMapMulti(LongStream actual, LongStream expected) {
        var actualResult = actual.mapMulti(failing(LongStream.LongMapMultiConsumer.class)).toArray();
        var expectedResult = expected.mapMulti(failing(LongStream.LongMapMultiConsumer.class)).toArray();

        compareResults(actualResult, expectedResult);
    }

    public void testMapMultiExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "mapMulti", LongStream.LongMapMultiConsumer.class);
    }

    public void testTakeWhile(LongStream actual, LongStream expected) {
        var actualResult = actual.takeWhile(failing(LongPredicate.class)).toArray();
        var expectedResult = expected.takeWhile(failing(LongPredicate.class)).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testTakeWhileExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "takeWhile", LongPredicate.class);
    }

    public void testDropWhile(LongStream actual, LongStream expected) {
        var actualResult = actual.dropWhile(failing(LongPredicate.class)).toArray();
        var expectedResult = expected.dropWhile(failing(LongPredicate.class)).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testDropWhileExceptions(LongStream actual, LongStream expected) {
        checkExpectedExceptions(actual, expected, "dropWhile", LongPredicate.class);
    }
}
