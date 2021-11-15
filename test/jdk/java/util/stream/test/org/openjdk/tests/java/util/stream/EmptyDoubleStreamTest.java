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

import java.util.Arrays;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.testng.Assert.*;

/*
 * @test
 * @summary Checks that the EmptyStream is equivalent to the
 * old way of creating it with StreamSupport.
 * @author Heinz Kabutz
 */

public final class EmptyDoubleStreamTest extends EmptyBaseStreamTest {
    @Test
    public void testAll() {
        Function<Spliterator.OfDouble, DoubleStream> emptyStreamCreator =
                StreamSupport::emptyDoubleStream;
        Function<Spliterator.OfDouble, DoubleStream> traditionalStreamCreator =
                spliterator -> StreamSupport.doubleStream(spliterator, false);

        this.compare(DoubleStream.class,
                () -> emptyStreamCreator.apply(Spliterators.emptyDoubleSpliterator()),
                () -> traditionalStreamCreator.apply(Spliterators.emptyDoubleSpliterator())
        );
        this.compare(DoubleStream.class,
                () -> emptyStreamCreator.apply(Spliterators.emptyDoubleSpliterator()).parallel(),
                () -> traditionalStreamCreator.apply(Spliterators.emptyDoubleSpliterator()).parallel()
        );
        this.compare(DoubleStream.class,
                () -> emptyStreamCreator.apply(Arrays.spliterator(new double[0])),
                () -> traditionalStreamCreator.apply(Arrays.spliterator(new double[0]))
        );
    }

    public void testFilter(DoubleStream actual, DoubleStream expected) {
        actual = actual.filter(failing(DoublePredicate.class));
        expected = expected.filter(failing(DoublePredicate.class));
        var actualResult = actual.toArray();
        var expectedResult = expected.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testFilterExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "filter", DoublePredicate.class);
    }

    public void testMap(DoubleStream actual, DoubleStream expected) {
        DoubleStream actualAsDoubleStream = actual.map(failing(DoubleUnaryOperator.class));
        DoubleStream expectedAsDoubleStream = expected.map(failing(DoubleUnaryOperator.class));
        var actualResult = actualAsDoubleStream.toArray();
        var expectedResult = expectedAsDoubleStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "map", DoubleUnaryOperator.class);
    }

    public void testMapToObj(DoubleStream actual, DoubleStream expected) {
        Stream<Double> actualAsStream = actual.mapToObj(failing(DoubleFunction.class));
        Stream<Double> expectedAsStream = expected.mapToObj(failing(DoubleFunction.class));
        var actualResult = actualAsStream.toArray();
        var expectedResult = expectedAsStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapToObjExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "mapToObj", DoubleFunction.class);
    }

    public void testMapToInt(DoubleStream actual, DoubleStream expected) {
        IntStream actualAsIntStream = actual.mapToInt(failing(DoubleToIntFunction.class));
        IntStream expectedAsIntStream = expected.mapToInt(failing(DoubleToIntFunction.class));
        var actualResult = actualAsIntStream.toArray();
        var expectedResult = expectedAsIntStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapToIntExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "mapToInt", DoubleToIntFunction.class);
    }

    public void testMapToDouble(DoubleStream actual, DoubleStream expected) {
        LongStream actualAsLongStream = actual.mapToLong(failing(DoubleToLongFunction.class));
        LongStream expectedAsLongStream = expected.mapToLong(failing(DoubleToLongFunction.class));
        var actualResult = actualAsLongStream.toArray();
        var expectedResult = expectedAsLongStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapToDoubleExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "mapToLong", DoubleToLongFunction.class);
    }

    public void testFlatMap(DoubleStream actual, DoubleStream expected) {
        DoubleStream actualAsDoubleStream = actual.flatMap(failing(DoubleFunction.class));
        DoubleStream expectedAsDoubleStream = expected.flatMap(failing(DoubleFunction.class));
        var actualResult = actualAsDoubleStream.toArray();
        var expectedResult = expectedAsDoubleStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testFlatMapExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "flatMap", DoubleFunction.class);
    }

    public void testDistinct(DoubleStream actual, DoubleStream expected) {
        actual = actual.distinct();
        expected = expected.distinct();
        var actualResult = actual.toArray();
        var expectedResult = expected.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testDistinctExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "distinct");
    }

    public void testSorted(DoubleStream actual, DoubleStream expected) {
        actual = actual.sorted();
        expected = expected.sorted();
        var actualResult = actual.toArray();
        var expectedResult = expected.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testSortedExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "sorted");
    }

    public void testPeek(DoubleStream actual, DoubleStream expected) {
        actual.peek(failing(DoubleConsumer.class)).toArray();
        expected.peek(failing(DoubleConsumer.class)).toArray();
    }

    public void testPeekExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "peek", DoubleConsumer.class);
    }

    public void testLimit(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.limit(10).toArray();
        var expectedResult = expected.limit(10).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testLimitExceptions(DoubleStream actual, DoubleStream expected) {
        assertThrows(IllegalArgumentException.class, () -> actual.limit(-1));
        assertThrows(IllegalArgumentException.class, () -> expected.limit(-1));

        actual.limit(10);
        expected.limit(10);
        assertThrows(IllegalStateException.class, () -> actual.limit(10));
        assertThrows(IllegalStateException.class, () -> expected.limit(10));
    }

    public void testSkip(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.skip(10).toArray();
        var expectedResult = expected.skip(10).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testSkipExceptions(DoubleStream actual, DoubleStream expected) {
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

    public void testForEach(DoubleStream actual, DoubleStream expected) {
        actual.forEach(failing(DoubleConsumer.class));
        expected.forEach(failing(DoubleConsumer.class));
    }

    public void testForEachExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "forEach", DoubleConsumer.class);
    }

    public void testForEachOrdered(DoubleStream actual, DoubleStream expected) {
        actual.forEachOrdered(failing(DoubleConsumer.class));
        expected.forEachOrdered(failing(DoubleConsumer.class));
    }

    public void testForEachOrderedExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "forEachOrdered", DoubleConsumer.class);
    }

    public void testToArray(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.toArray();
        var expectedResult = expected.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testToArrayExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "toArray");
    }

    public void testReduceDoubleBinaryOperator(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.reduce(failing(DoubleBinaryOperator.class));
        var expectedResult = expected.reduce(failing(DoubleBinaryOperator.class));

        compareResults(actualResult, expectedResult);
    }

    public void testReduceDoubleBinaryOperatorExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "reduce", DoubleBinaryOperator.class);
    }

    public void testReduceIdentityDoubleBinaryOperator(DoubleStream actual, DoubleStream expected) {
        var magicIdentity = 42L;
        var actualResult = actual.reduce(magicIdentity, failing(DoubleBinaryOperator.class));
        var expectedResult = expected.reduce(magicIdentity, failing(DoubleBinaryOperator.class));

        assertEquals(actualResult, magicIdentity);
        assertEquals(expectedResult, magicIdentity);
    }

    public void testReduceIdentityDoubleBinaryOperatorExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "reduce", double.class, DoubleBinaryOperator.class);
    }

    public void testCollect(DoubleStream actual, DoubleStream expected) {
        var magicIdentity = new Object();
        var actualResult = actual.collect(() -> magicIdentity, failing(ObjDoubleConsumer.class), failing(BiConsumer.class));
        var expectedResult = expected.collect(() -> magicIdentity, failing(ObjDoubleConsumer.class), failing(BiConsumer.class));

        assertSame(magicIdentity, actualResult);
        assertSame(magicIdentity, expectedResult);
    }

    public void testCollectExceptions(DoubleStream actual, DoubleStream expected) {
        Supplier<Object> supplier = Object::new;
        var accumulator = failing(ObjDoubleConsumer.class);
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

    public void testSum(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.sum();
        var expectedResult = expected.sum();

        assertEquals(actualResult, expectedResult);
        assertEquals(actualResult, 0);
    }

    public void testSumExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "sum");
    }

    public void testMin(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.min();
        var expectedResult = expected.min();
        compareResults(actualResult, expectedResult);
    }

    public void testMinExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "min");
    }

    public void testMax(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.max();
        var expectedResult = expected.max();
        compareResults(actualResult, expectedResult);
    }

    public void testMaxExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "max");
    }

    public void testCount(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.count();
        var expectedResult = expected.count();
        assertEquals(actualResult, expectedResult);
        assertEquals(actualResult, 0L);
    }

    public void testCountExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "count");
    }

    public void testAverage(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.average();
        var expectedResult = expected.average();
        compareResults(actualResult, expectedResult);
    }

    public void testAverageExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "average");
    }

    public void testSummaryStatistics(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.summaryStatistics().toString();
        var expectedResult = expected.summaryStatistics().toString();
        assertEquals(actualResult, expectedResult);
    }

    public void testSummaryStatisticsExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "summaryStatistics");
    }

    public void testAnyMatch(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.anyMatch(failing(DoublePredicate.class));
        var expectedResult = expected.anyMatch(failing(DoublePredicate.class));
        assertEquals(actualResult, expectedResult);
    }

    public void testAnyMatchExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "anyMatch", DoublePredicate.class);
    }

    public void testAllMatch(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.allMatch(failing(DoublePredicate.class));
        var expectedResult = expected.allMatch(failing(DoublePredicate.class));
        assertEquals(actualResult, expectedResult);
    }

    public void testAllMatchExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "allMatch", DoublePredicate.class);
    }

    public void testNoneMatch(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.noneMatch(failing(DoublePredicate.class));
        var expectedResult = expected.noneMatch(failing(DoublePredicate.class));
        assertEquals(actualResult, expectedResult);
    }

    public void testNoneMatchExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "noneMatch", DoublePredicate.class);
    }

    public void testFindFirst(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.findFirst();
        var expectedResult = expected.findFirst();
        compareResults(actualResult, expectedResult);
    }

    public void testFindFirstExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "findFirst");
    }

    public void testFindAny(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.findAny();
        var expectedResult = expected.findAny();
        compareResults(actualResult, expectedResult);
    }

    public void testFindAnyExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "findAny");
    }

    public void testBoxed(DoubleStream actual, DoubleStream expected) {
        Stream<Double> actualAsStream = actual.boxed();
        Stream<Double> expectedAsStream = expected.boxed();
        var actualResult = actualAsStream.toArray();
        var expectedResult = expectedAsStream.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testBoxedExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "boxed");
    }

    public void testMapMulti(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.mapMulti(failing(DoubleStream.DoubleMapMultiConsumer.class)).toArray();
        var expectedResult = expected.mapMulti(failing(DoubleStream.DoubleMapMultiConsumer.class)).toArray();

        compareResults(actualResult, expectedResult);
    }

    public void testMapMultiExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "mapMulti", DoubleStream.DoubleMapMultiConsumer.class);
    }

    public void testTakeWhile(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.takeWhile(failing(DoublePredicate.class)).toArray();
        var expectedResult = expected.takeWhile(failing(DoublePredicate.class)).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testTakeWhileExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "takeWhile", DoublePredicate.class);
    }

    public void testDropWhile(DoubleStream actual, DoubleStream expected) {
        var actualResult = actual.dropWhile(failing(DoublePredicate.class)).toArray();
        var expectedResult = expected.dropWhile(failing(DoublePredicate.class)).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testDropWhileExceptions(DoubleStream actual, DoubleStream expected) {
        checkExpectedExceptions(actual, expected, "dropWhile", DoublePredicate.class);
    }
}
