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
                () -> StreamSupport.longStream(Spliterators.emptyLongSpliterator(), false),
                LongStream::empty);
        this.compare(LongStream.class,
                () -> StreamSupport.longStream(Spliterators.emptyLongSpliterator(), true),
                () -> LongStream.empty().parallel());
    }

    public void testFilter(LongStream expected, LongStream actual) {
        expected = expected.filter(failing(LongPredicate.class));
        actual = actual.filter(failing(LongPredicate.class));
        var expectedResult = expected.toArray();
        var actualResult = actual.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testFilterParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.filter(null));
        assertThrows(NullPointerException.class, () -> actual.filter(null));
    }

    public void testMap(LongStream expected, LongStream actual) {
        LongStream expectedAsLongStream = expected.map(failing(LongUnaryOperator.class));
        LongStream actualAsLongStream = actual.map(failing(LongUnaryOperator.class));
        var expectedResult = expectedAsLongStream.toArray();
        var actualResult = actualAsLongStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.map(null));
        assertThrows(NullPointerException.class, () -> actual.map(null));
    }

    public void testMapToObj(LongStream expected, LongStream actual) {
        Stream<Long> expectedAsStream = expected.mapToObj(failing(LongFunction.class));
        Stream<Long> actualAsStream = actual.mapToObj(failing(LongFunction.class));
        var expectedResult = expectedAsStream.toArray();
        var actualResult = actualAsStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapToObjParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.mapToObj(null));
        assertThrows(NullPointerException.class, () -> actual.mapToObj(null));
    }

    public void testMapToInt(LongStream expected, LongStream actual) {
        IntStream expectedAsLongStream = expected.mapToInt(failing(LongToIntFunction.class));
        IntStream actualAsLongStream = actual.mapToInt(failing(LongToIntFunction.class));
        var expectedResult = expectedAsLongStream.toArray();
        var actualResult = actualAsLongStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapToIntParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.mapToInt(null));
        assertThrows(NullPointerException.class, () -> actual.mapToInt(null));
    }

    public void testMapToDouble(LongStream expected, LongStream actual) {
        DoubleStream expectedAsDoubleStream = expected.mapToDouble(failing(LongToDoubleFunction.class));
        DoubleStream actualAsDoubleStream = actual.mapToDouble(failing(LongToDoubleFunction.class));
        var expectedResult = expectedAsDoubleStream.toArray();
        var actualResult = actualAsDoubleStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapToDoubleParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.mapToDouble(null));
        assertThrows(NullPointerException.class, () -> actual.mapToDouble(null));
    }

    public void testFlatMap(LongStream expected, LongStream actual) {
        LongStream expectedAsIntStream = expected.flatMap(failing(LongFunction.class));
        LongStream actualAsIntStream = actual.flatMap(failing(LongFunction.class));
        var expectedResult = expectedAsIntStream.toArray();
        var actualResult = actualAsIntStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testFlatMapParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.flatMap(null));
        assertThrows(NullPointerException.class, () -> actual.flatMap(null));
    }

    public void testDistinct(LongStream expected, LongStream actual) {
        expected = expected.distinct();
        actual = actual.distinct();
        var expectedResult = expected.toArray();
        var actualResult = actual.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testSorted(LongStream expected, LongStream actual) {
        expected = expected.sorted();
        actual = actual.sorted();
        var expectedResult = expected.toArray();
        var actualResult = actual.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testPeek(LongStream expected, LongStream actual) {
        expected.peek(failing(LongConsumer.class)).toArray();
        actual.peek(failing(LongConsumer.class)).toArray();
    }

    public void testPeekParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.peek(null));
        assertThrows(NullPointerException.class, () -> actual.peek(null));
    }

    public void testLimit(LongStream expected, LongStream actual) {
        var expectedResult = expected.limit(10).toArray();
        var actualResult = actual.limit(10).toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testLimitParameters(LongStream expected, LongStream actual) {
        assertThrows(IllegalArgumentException.class, () -> expected.limit(-1));
        assertThrows(IllegalArgumentException.class, () -> actual.limit(-1));
    }

    public void testSkip(LongStream expected, LongStream actual) {
        var expectedResult = expected.skip(10).toArray();
        var actualResult = actual.skip(10).toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testSkipParameters(LongStream expected, LongStream actual) {
        assertThrows(IllegalArgumentException.class, () -> expected.skip(-1));
        assertThrows(IllegalArgumentException.class, () -> actual.skip(-1));
    }

    public void testForEach(LongStream expected, LongStream actual) {
        expected.forEach(failing(LongConsumer.class));
        actual.forEach(failing(LongConsumer.class));
    }

    public void testForEachParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.forEach(null));
        assertThrows(NullPointerException.class, () -> actual.forEach(null));
    }

    public void testForEachOrdered(LongStream expected, LongStream actual) {
        expected.forEachOrdered(failing(LongConsumer.class));
        actual.forEachOrdered(failing(LongConsumer.class));
    }

    public void testForEachOrderedParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.forEachOrdered(null));
        assertThrows(NullPointerException.class, () -> actual.forEachOrdered(null));
    }

    public void testToArray(LongStream expected, LongStream actual) {
        var expectedResult = expected.toArray();
        var actualResult = actual.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testReduceIntBinaryOperator(LongStream expected, LongStream actual) {
        var expectedResult = expected.reduce(failing(LongBinaryOperator.class));
        var actualResult = actual.reduce(failing(LongBinaryOperator.class));

        compareResults(expectedResult, actualResult);
    }

    public void testReduceIntBinaryOperatorParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.reduce(null));
        assertThrows(NullPointerException.class, () -> actual.reduce(null));
    }

    public void testReduceIdentityIntBinaryOperator(LongStream expected, LongStream actual) {
        var magicIdentity = 42;
        var expectedResult = expected.reduce(magicIdentity, failing(LongBinaryOperator.class));
        var actualResult = actual.reduce(magicIdentity, failing(LongBinaryOperator.class));

        assertEquals(magicIdentity, expectedResult);
        assertEquals(magicIdentity, actualResult);
    }

    public void testReduceIdentityIntBinaryOperatorParameters(LongStream expected, LongStream actual) {
        int magicInt = 42;
        assertThrows(NullPointerException.class, () -> expected.reduce(magicInt, null));
        assertThrows(NullPointerException.class, () -> actual.reduce(magicInt, null));
    }

    public void testCollect(LongStream expected, LongStream actual) {
        var magicIdentity = new Object();
        var accumulator = failing(ObjLongConsumer.class);
        var combiner = failing(BiConsumer.class);
        var expectedResult = expected.collect(() -> magicIdentity, accumulator, combiner);
        var actualResult = actual.collect(() -> magicIdentity, accumulator, combiner);

        assertSame(magicIdentity, expectedResult);
        assertSame(magicIdentity, actualResult);
    }

    public void testCollectParameters(LongStream expected, LongStream actual) {
        Supplier<Object> supplier = Object::new;
        var accumulator = failing(ObjLongConsumer.class);
        var combiner = failing(BiConsumer.class);
        assertThrows(NullPointerException.class, () -> expected.collect(null, accumulator, combiner));
        assertThrows(NullPointerException.class, () -> actual.collect(null, accumulator, combiner));
        assertThrows(NullPointerException.class, () -> expected.collect(supplier, null, combiner));
        assertThrows(NullPointerException.class, () -> actual.collect(supplier, null, combiner));
        assertThrows(NullPointerException.class, () -> expected.collect(supplier, accumulator, null));
        assertThrows(NullPointerException.class, () -> actual.collect(supplier, accumulator, null));
    }

    public void testSum(LongStream expected, LongStream actual) {
        var expectedResult = expected.sum();
        var actualResult = actual.sum();

        assertEquals(expectedResult, actualResult);
        assertEquals(0, actualResult);
    }

    public void testMin(LongStream expected, LongStream actual) {
        var expectedResult = expected.min();
        var actualResult = actual.min();
        compareResults(expectedResult, actualResult);
    }

    public void testMax(LongStream expected, LongStream actual) {
        var expectedResult = expected.max();
        var actualResult = actual.max();
        compareResults(expectedResult, actualResult);
    }

    public void testCount(LongStream expected, LongStream actual) {
        var expectedResult = expected.count();
        var actualResult = actual.count();
        assertEquals(expectedResult, actualResult);
        assertEquals(0L, actualResult);
    }

    public void testAverage(LongStream expected, LongStream actual) {
        var expectedResult = expected.average();
        var actualResult = actual.average();
        compareResults(expectedResult, actualResult);
    }

    public void testSummaryStatistics(LongStream expected, LongStream actual) {
        var expectedResult = expected.summaryStatistics().toString();
        var actualResult = actual.summaryStatistics().toString();
        assertEquals(expectedResult, actualResult);
    }

    public void testAnyMatch(LongStream expected, LongStream actual) {
        var expectedResult = expected.anyMatch(failing(LongPredicate.class));
        var actualResult = actual.anyMatch(failing(LongPredicate.class));
        assertEquals(expectedResult, actualResult);
    }

    public void testAnyMatchParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.anyMatch(null));
        assertThrows(NullPointerException.class, () -> actual.anyMatch(null));
    }

    public void testAllMatch(LongStream expected, LongStream actual) {
        var expectedResult = expected.allMatch(failing(LongPredicate.class));
        var actualResult = actual.allMatch(failing(LongPredicate.class));
        assertEquals(expectedResult, actualResult);
    }

    public void testAllMatchParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.allMatch(null));
        assertThrows(NullPointerException.class, () -> actual.allMatch(null));
    }

    public void testNoneMatch(LongStream expected, LongStream actual) {
        var expectedResult = expected.noneMatch(failing(LongPredicate.class));
        var actualResult = actual.noneMatch(failing(LongPredicate.class));
        assertEquals(expectedResult, actualResult);
    }

    public void testNoneMatchParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.noneMatch(null));
        assertThrows(NullPointerException.class, () -> actual.noneMatch(null));
    }

    public void testFindFirst(LongStream expected, LongStream actual) {
        var expectedResult = expected.findFirst();
        var actualResult = actual.findFirst();
        compareResults(expectedResult, actualResult);
    }

    public void testFindAny(LongStream expected, LongStream actual) {
        var expectedResult = expected.findAny();
        var actualResult = actual.findAny();
        compareResults(expectedResult, actualResult);
    }

    public void testAsDoubleStream(LongStream expected, LongStream actual) {
        DoubleStream expectedAsDoubleStream = expected.asDoubleStream();
        DoubleStream actualAsDoubleStream = actual.asDoubleStream();
        double[] expectedResult = expectedAsDoubleStream.toArray();
        double[] actualResult = actualAsDoubleStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testBoxed(LongStream expected, LongStream actual) {
        Stream<Long> expectedAsStream = expected.boxed();
        Stream<Long> actualAsStream = actual.boxed();
        var expectedResult = expectedAsStream.toArray();
        var actualResult = actualAsStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapMulti(LongStream expected, LongStream actual) {
        var expectedResult = expected.mapMulti(failing(LongStream.LongMapMultiConsumer.class)).toArray();
        var actualResult = actual.mapMulti(failing(LongStream.LongMapMultiConsumer.class)).toArray();

        compareResults(expectedResult, actualResult);
    }

    public void testMapMultiParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.mapMulti(null));
        assertThrows(NullPointerException.class, () -> actual.mapMulti(null));
    }

    public void testTakeWhile(LongStream expected, LongStream actual) {
        var expectedResult = expected.takeWhile(failing(LongPredicate.class)).toArray();
        var actualResult = actual.takeWhile(failing(LongPredicate.class)).toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testTakeWhileParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.takeWhile(null));
        assertThrows(NullPointerException.class, () -> actual.takeWhile(null));
    }

    public void testDropWhile(LongStream expected, LongStream actual) {
        var expectedResult = expected.dropWhile(failing(LongPredicate.class)).toArray();
        var actualResult = actual.dropWhile(failing(LongPredicate.class)).toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testDropWhileParameters(LongStream expected, LongStream actual) {
        assertThrows(NullPointerException.class, () -> expected.dropWhile(null));
        assertThrows(NullPointerException.class, () -> actual.dropWhile(null));
    }
}
