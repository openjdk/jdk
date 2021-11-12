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
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.testng.Assert.*;

/*
 * @test
 * @summary Checks that the EmptyInttream is equivalent to the
 * old way of creating it with StreamSupport.
 * @author Heinz Kabutz
 */

public final class EmptyIntStreamTest extends EmptyBaseStreamTest {
    @Test
    public void testAll() {
        this.compare(IntStream.class,
                IntStream::empty, () -> StreamSupport.intStream(Spliterators.emptyIntSpliterator(), false)
        );
        this.compare(IntStream.class,
                () -> IntStream.empty().parallel(), () -> StreamSupport.intStream(Spliterators.emptyIntSpliterator(), true)
        );
    }

    public void testFilter(IntStream actual, IntStream expected) {
        actual = actual.filter(failing(IntPredicate.class));
        expected = expected.filter(failing(IntPredicate.class));
        var actualResult = actual.toArray();
        var expectedResult = expected.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testFilterExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "filter", IntPredicate.class);
    }

    public void testMap(IntStream actual, IntStream expected) {
        IntStream actualAsIntStream = actual.map(failing(IntUnaryOperator.class));
        IntStream expectedAsIntStream = expected.map(failing(IntUnaryOperator.class));
        var actualResult = actualAsIntStream.toArray();
        var expectedResult = expectedAsIntStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "map", IntUnaryOperator.class);
    }

    public void testMapToObj(IntStream actual, IntStream expected) {
        Stream<Integer> actualAsStream = actual.mapToObj(failing(IntFunction.class));
        Stream<Integer> expectedAsStream = expected.mapToObj(failing(IntFunction.class));
        var actualResult = actualAsStream.toArray();
        var expectedResult = expectedAsStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapToObjExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "mapToObj", IntFunction.class);
    }

    public void testMapToLong(IntStream actual, IntStream expected) {
        LongStream actualAsLongStream = actual.mapToLong(failing(IntToLongFunction.class));
        LongStream expectedAsLongStream = expected.mapToLong(failing(IntToLongFunction.class));
        var actualResult = actualAsLongStream.toArray();
        var expectedResult = expectedAsLongStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapToLongExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "mapToLong", IntToLongFunction.class);
    }

    public void testMapToDouble(IntStream actual, IntStream expected) {
        DoubleStream actualAsDoubleStream = actual.mapToDouble(failing(IntToDoubleFunction.class));
        DoubleStream expectedAsDoubleStream = expected.mapToDouble(failing(IntToDoubleFunction.class));
        var actualResult = actualAsDoubleStream.toArray();
        var expectedResult = expectedAsDoubleStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapToDoubleExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "mapToDouble", IntToDoubleFunction.class);
    }

    public void testFlatMap(IntStream actual, IntStream expected) {
        IntStream actualAsIntStream = actual.flatMap(failing(IntFunction.class));
        IntStream expectedAsIntStream = expected.flatMap(failing(IntFunction.class));
        var actualResult = actualAsIntStream.toArray();
        var expectedResult = expectedAsIntStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testFlatMapExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "flatMap", IntFunction.class);
    }

    public void testDistinct(IntStream actual, IntStream expected) {
        actual = actual.distinct();
        expected = expected.distinct();
        var actualResult = actual.toArray();
        var expectedResult = expected.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testDistinctExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "distinct");
    }

    public void testSorted(IntStream actual, IntStream expected) {
        actual = actual.sorted();
        expected = expected.sorted();
        var actualResult = actual.toArray();
        var expectedResult = expected.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testSortedExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "sorted");
    }

    public void testPeek(IntStream actual, IntStream expected) {
        actual.peek(failing(IntConsumer.class)).toArray();
        expected.peek(failing(IntConsumer.class)).toArray();
    }

    public void testPeekExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "peek", IntConsumer.class);
    }

    public void testLimit(IntStream actual, IntStream expected) {
        var actualResult = actual.limit(10).toArray();
        var expectedResult = expected.limit(10).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testLimitExceptions(IntStream actual, IntStream expected) {
        assertThrows(IllegalArgumentException.class, () -> actual.limit(-1));
        assertThrows(IllegalArgumentException.class, () -> expected.limit(-1));

        actual.limit(10);
        expected.limit(10);
        assertThrows(IllegalStateException.class, () -> actual.limit(10));
        assertThrows(IllegalStateException.class, () -> expected.limit(10));
    }

    public void testSkip(IntStream actual, IntStream expected) {
        var actualResult = actual.skip(10).toArray();
        var expectedResult = expected.skip(10).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testSkipExceptions(IntStream actual, IntStream expected) {
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

    public void testForEach(IntStream actual, IntStream expected) {
        actual.forEach(failing(IntConsumer.class));
        expected.forEach(failing(IntConsumer.class));
    }

    public void testForEachExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "forEach", IntConsumer.class);
    }

    public void testForEachOrdered(IntStream actual, IntStream expected) {
        actual.forEachOrdered(failing(IntConsumer.class));
        expected.forEachOrdered(failing(IntConsumer.class));
    }

    public void testForEachOrderedExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "forEachOrdered", IntConsumer.class);
    }

    public void testToArray(IntStream actual, IntStream expected) {
        var actualResult = actual.toArray();
        var expectedResult = expected.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testToArrayExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "toArray");
    }

    public void testReduceIntBinaryOperator(IntStream actual, IntStream expected) {
        var actualResult = actual.reduce(failing(IntBinaryOperator.class));
        var expectedResult = expected.reduce(failing(IntBinaryOperator.class));

        compareResults(actualResult, expectedResult);
    }

    public void testReduceIntBinaryOperatorExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "reduce", IntBinaryOperator.class);
    }

    public void testReduceIdentityIntBinaryOperator(IntStream actual, IntStream expected) {
        var magicIdentity = 42;
        var actualResult = actual.reduce(magicIdentity, failing(IntBinaryOperator.class));
        var expectedResult = expected.reduce(magicIdentity, failing(IntBinaryOperator.class));

        assertEquals(actualResult, magicIdentity);
        assertEquals(expectedResult, magicIdentity);
    }

    public void testReduceIdentityIntBinaryOperatorExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "reduce", int.class, IntBinaryOperator.class);
    }

    public void testCollect(IntStream actual, IntStream expected) {
        var magicIdentity = new Object();
        var actualResult = actual.collect(() -> magicIdentity, failing(ObjIntConsumer.class), failing(BiConsumer.class));
        var expectedResult = expected.collect(() -> magicIdentity, failing(ObjIntConsumer.class), failing(BiConsumer.class));

        assertSame(magicIdentity, actualResult);
        assertSame(magicIdentity, expectedResult);
    }

    public void testCollectExceptions(IntStream actual, IntStream expected) {
        Supplier<Object> supplier = Object::new;
        var accumulator = failing(ObjIntConsumer.class);
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

    public void testSum(IntStream actual, IntStream expected) {
        var actualResult = actual.sum();
        var expectedResult = expected.sum();

        assertEquals(actualResult, expectedResult);
        assertEquals(actualResult, 0);
    }

    public void testSumExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "sum");
    }

    public void testMin(IntStream actual, IntStream expected) {
        var actualResult = actual.min();
        var expectedResult = expected.min();
        compareResults(actualResult, expectedResult);
    }

    public void testMinExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "min");
    }

    public void testMax(IntStream actual, IntStream expected) {
        var actualResult = actual.max();
        var expectedResult = expected.max();
        compareResults(actualResult, expectedResult);
    }

    public void testMaxExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "max");
    }

    public void testCount(IntStream actual, IntStream expected) {
        var actualResult = actual.count();
        var expectedResult = expected.count();
        assertEquals(actualResult, expectedResult);
        assertEquals(actualResult, 0L);
    }

    public void testCountExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "count");
    }

    public void testAverage(IntStream actual, IntStream expected) {
        var actualResult = actual.average();
        var expectedResult = expected.average();
        compareResults(actualResult, expectedResult);
    }

    public void testAverageExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "average");
    }

    public void testSummaryStatistics(IntStream actual, IntStream expected) {
        var actualResult = actual.summaryStatistics().toString();
        var expectedResult = expected.summaryStatistics().toString();
        assertEquals(actualResult, expectedResult);
    }

    public void testSummaryStatisticsExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "summaryStatistics");
    }

    public void testAnyMatch(IntStream actual, IntStream expected) {
        var actualResult = actual.anyMatch(failing(IntPredicate.class));
        var expectedResult = expected.anyMatch(failing(IntPredicate.class));
        assertEquals(actualResult, expectedResult);
    }

    public void testAnyMatchExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "anyMatch", IntPredicate.class);
    }

    public void testAllMatch(IntStream actual, IntStream expected) {
        var actualResult = actual.allMatch(failing(IntPredicate.class));
        var expectedResult = expected.allMatch(failing(IntPredicate.class));
        assertEquals(actualResult, expectedResult);
    }

    public void testAllMatchExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "allMatch", IntPredicate.class);
    }

    public void testNoneMatch(IntStream actual, IntStream expected) {
        var actualResult = actual.noneMatch(failing(IntPredicate.class));
        var expectedResult = expected.noneMatch(failing(IntPredicate.class));
        assertEquals(actualResult, expectedResult);
    }

    public void testNoneMatchExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "noneMatch", IntPredicate.class);
    }

    public void testFindFirst(IntStream actual, IntStream expected) {
        var actualResult = actual.findFirst();
        var expectedResult = expected.findFirst();
        compareResults(actualResult, expectedResult);
    }

    public void testFindFirstExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "findFirst");
    }

    public void testFindAny(IntStream actual, IntStream expected) {
        var actualResult = actual.findAny();
        var expectedResult = expected.findAny();
        compareResults(actualResult, expectedResult);
    }

    public void testFindAnyExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "findAny");
    }

    public void testAsLongStream(IntStream actual, IntStream expected) {
        LongStream actualAsLongStream = actual.asLongStream();
        LongStream expectedAsLongStream = expected.asLongStream();
        long[] actualResult = actualAsLongStream.toArray();
        long[] expectedResult = expectedAsLongStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testAsLongStreamExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "asLongStream");
    }

    public void testAsDoubleStream(IntStream actual, IntStream expected) {
        DoubleStream actualAsDoubleStream = actual.asDoubleStream();
        DoubleStream expectedAsDoubleStream = expected.asDoubleStream();
        double[] actualResult = actualAsDoubleStream.toArray();
        double[] expectedResult = expectedAsDoubleStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testAsDoubleStreamExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "asDoubleStream");
    }

    public void testBoxed(IntStream actual, IntStream expected) {
        Stream<Integer> actualAsStream = actual.boxed();
        Stream<Integer> expectedAsStream = expected.boxed();
        var actualResult = actualAsStream.toArray();
        var expectedResult = expectedAsStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testBoxedExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "boxed");
    }

    public void testMapMulti(IntStream actual, IntStream expected) {
        var actualResult = actual.mapMulti(failing(IntStream.IntMapMultiConsumer.class)).toArray();
        var expectedResult = expected.mapMulti(failing(IntStream.IntMapMultiConsumer.class)).toArray();

        compareResults(actualResult, expectedResult);
    }

    public void testMapMultiExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "mapMulti", IntStream.IntMapMultiConsumer.class);
    }

    public void testTakeWhile(IntStream actual, IntStream expected) {
        var actualResult = actual.takeWhile(failing(IntPredicate.class)).toArray();
        var expectedResult = expected.takeWhile(failing(IntPredicate.class)).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testTakeWhileExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "takeWhile", IntPredicate.class);
    }

    public void testDropWhile(IntStream actual, IntStream expected) {
        var actualResult = actual.dropWhile(failing(IntPredicate.class)).toArray();
        var expectedResult = expected.dropWhile(failing(IntPredicate.class)).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testDropWhileExceptions(IntStream actual, IntStream expected) {
        checkExpectedExceptions(actual, expected, "dropWhile", IntPredicate.class);
    }
}
