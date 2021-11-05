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
 * @summary Checks that the EmptyIntStream is equivalent to the
 * old way of creating it with StreamSupport.
 * @author Heinz Kabutz
 */

public final class EmptyIntStreamTest extends EmptyBaseStreamTest {
    @Test
    public void testAll() {
        this.compare(IntStream.class,
                () -> StreamSupport.intStream(Spliterators.emptyIntSpliterator(), false),
                IntStream::empty);
        this.compare(IntStream.class,
                () -> StreamSupport.intStream(Spliterators.emptyIntSpliterator(), true),
                () -> IntStream.empty().parallel());
    }

    public void testFilter(IntStream expected, IntStream actual) {
        expected = expected.filter(failing(IntPredicate.class));
        actual = actual.filter(failing(IntPredicate.class));
        var expectedResult = expected.toArray();
        var actualResult = actual.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testFilterParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.filter(null));
        assertThrows(NullPointerException.class, () -> actual.filter(null));
    }

    public void testMap(IntStream expected, IntStream actual) {
        IntStream expectedAsIntStream = expected.map(failing(IntUnaryOperator.class));
        IntStream actualAsIntStream = actual.map(failing(IntUnaryOperator.class));
        var expectedResult = expectedAsIntStream.toArray();
        var actualResult = actualAsIntStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.map(null));
        assertThrows(NullPointerException.class, () -> actual.map(null));
    }

    public void testMapToObj(IntStream expected, IntStream actual) {
        Stream<Integer> expectedAsStream = expected.mapToObj(failing(IntFunction.class));
        Stream<Integer> actualAsStream = actual.mapToObj(failing(IntFunction.class));
        var expectedResult = expectedAsStream.toArray();
        var actualResult = actualAsStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapToObjParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.mapToObj(null));
        assertThrows(NullPointerException.class, () -> actual.mapToObj(null));
    }

    public void testMapToLong(IntStream expected, IntStream actual) {
        LongStream expectedAsLongStream = expected.mapToLong(failing(IntToLongFunction.class));
        LongStream actualAsLongStream = actual.mapToLong(failing(IntToLongFunction.class));
        var expectedResult = expectedAsLongStream.toArray();
        var actualResult = actualAsLongStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapToLongParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.mapToLong(null));
        assertThrows(NullPointerException.class, () -> actual.mapToLong(null));
    }

    public void testMapToDouble(IntStream expected, IntStream actual) {
        DoubleStream expectedAsDoubleStream = expected.mapToDouble(failing(IntToDoubleFunction.class));
        DoubleStream actualAsDoubleStream = actual.mapToDouble(failing(IntToDoubleFunction.class));
        var expectedResult = expectedAsDoubleStream.toArray();
        var actualResult = actualAsDoubleStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapToDoubleParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.mapToDouble(null));
        assertThrows(NullPointerException.class, () -> actual.mapToDouble(null));
    }

    public void testFlatMap(IntStream expected, IntStream actual) {
        IntStream expectedAsIntStream = expected.flatMap(failing(IntFunction.class));
        IntStream actualAsIntStream = actual.flatMap(failing(IntFunction.class));
        var expectedResult = expectedAsIntStream.toArray();
        var actualResult = actualAsIntStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testFlatMapParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.flatMap(null));
        assertThrows(NullPointerException.class, () -> actual.flatMap(null));
    }

    public void testDistinct(IntStream expected, IntStream actual) {
        expected = expected.distinct();
        actual = actual.distinct();
        var expectedResult = expected.toArray();
        var actualResult = actual.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testSorted(IntStream expected, IntStream actual) {
        expected = expected.sorted();
        actual = actual.sorted();
        var expectedResult = expected.toArray();
        var actualResult = actual.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testPeek(IntStream expected, IntStream actual) {
        expected.peek(failing(IntConsumer.class)).toArray();
        actual.peek(failing(IntConsumer.class)).toArray();
    }

    public void testPeekParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.peek(null));
        assertThrows(NullPointerException.class, () -> actual.peek(null));
    }

    public void testLimit(IntStream expected, IntStream actual) {
        var expectedResult = expected.limit(10).toArray();
        var actualResult = actual.limit(10).toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testLimitParameters(IntStream expected, IntStream actual) {
        assertThrows(IllegalArgumentException.class, () -> expected.limit(-1));
        assertThrows(IllegalArgumentException.class, () -> actual.limit(-1));
    }

    public void testSkip(IntStream expected, IntStream actual) {
        var expectedResult = expected.skip(10).toArray();
        var actualResult = actual.skip(10).toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testSkipParameters(IntStream expected, IntStream actual) {
        assertThrows(IllegalArgumentException.class, () -> expected.skip(-1));
        assertThrows(IllegalArgumentException.class, () -> actual.skip(-1));
    }

    public void testForEach(IntStream expected, IntStream actual) {
        expected.forEach(failing(IntConsumer.class));
        actual.forEach(failing(IntConsumer.class));
    }

    public void testForEachParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.forEach(null));
        assertThrows(NullPointerException.class, () -> actual.forEach(null));
    }

    public void testForEachOrdered(IntStream expected, IntStream actual) {
        expected.forEachOrdered(failing(IntConsumer.class));
        actual.forEachOrdered(failing(IntConsumer.class));
    }

    public void testForEachOrderedParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.forEachOrdered(null));
        assertThrows(NullPointerException.class, () -> actual.forEachOrdered(null));
    }

    public void testToArray(IntStream expected, IntStream actual) {
        var expectedResult = expected.toArray();
        var actualResult = actual.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testReduceIntBinaryOperator(IntStream expected, IntStream actual) {
        var expectedResult = expected.reduce(failing(IntBinaryOperator.class));
        var actualResult = actual.reduce(failing(IntBinaryOperator.class));

        compareResults(expectedResult, actualResult);
    }

    public void testReduceIntBinaryOperatorParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.reduce(null));
        assertThrows(NullPointerException.class, () -> actual.reduce(null));
    }

    public void testReduceIdentityIntBinaryOperator(IntStream expected, IntStream actual) {
        var magicIdentity = 42;
        var expectedResult = expected.reduce(magicIdentity, failing(IntBinaryOperator.class));
        var actualResult = actual.reduce(magicIdentity, failing(IntBinaryOperator.class));

        assertEquals(magicIdentity, expectedResult);
        assertEquals(magicIdentity, actualResult);
    }

    public void testReduceIdentityIntBinaryOperatorParameters(IntStream expected, IntStream actual) {
        int magicInt = 42;
        assertThrows(NullPointerException.class, () -> expected.reduce(magicInt, null));
        assertThrows(NullPointerException.class, () -> actual.reduce(magicInt, null));
    }

    public void testCollect(IntStream expected, IntStream actual) {
        var magicIdentity = new Object();
        var expectedResult = expected.collect(() -> magicIdentity, failing(ObjIntConsumer.class), failing(BiConsumer.class));
        var actualResult = actual.collect(() -> magicIdentity, failing(ObjIntConsumer.class), failing(BiConsumer.class));

        assertSame(magicIdentity, expectedResult);
        assertSame(magicIdentity, actualResult);
    }

    public void testCollectParameters(IntStream expected, IntStream actual) {
        Supplier<Object> supplier = Object::new;
        var accumulator = failing(ObjIntConsumer.class);
        var combiner = failing(BiConsumer.class);
        assertThrows(NullPointerException.class, () -> expected.collect(null, accumulator, combiner));
        assertThrows(NullPointerException.class, () -> actual.collect(null, accumulator, combiner));
        assertThrows(NullPointerException.class, () -> expected.collect(supplier, null, combiner));
        assertThrows(NullPointerException.class, () -> actual.collect(supplier, null, combiner));
        assertThrows(NullPointerException.class, () -> expected.collect(supplier, accumulator, null));
        assertThrows(NullPointerException.class, () -> actual.collect(supplier, accumulator, null));
    }

    public void testSum(IntStream expected, IntStream actual) {
        var expectedResult = expected.sum();
        var actualResult = actual.sum();

        assertEquals(expectedResult, actualResult);
        assertEquals(0, actualResult);
    }

    public void testMin(IntStream expected, IntStream actual) {
        var expectedResult = expected.min();
        var actualResult = actual.min();
        compareResults(expectedResult, actualResult);
    }

    public void testMax(IntStream expected, IntStream actual) {
        var expectedResult = expected.max();
        var actualResult = actual.max();
        compareResults(expectedResult, actualResult);
    }

    public void testCount(IntStream expected, IntStream actual) {
        var expectedResult = expected.count();
        var actualResult = actual.count();
        assertEquals(expectedResult, actualResult);
        assertEquals(0L, actualResult);
    }

    public void testAverage(IntStream expected, IntStream actual) {
        var expectedResult = expected.average();
        var actualResult = actual.average();
        compareResults(expectedResult, actualResult);
    }

    public void testSummaryStatistics(IntStream expected, IntStream actual) {
        var expectedResult = expected.summaryStatistics().toString();
        var actualResult = actual.summaryStatistics().toString();
        assertEquals(expectedResult, actualResult);
    }

    public void testAnyMatch(IntStream expected, IntStream actual) {
        var expectedResult = expected.anyMatch(failing(IntPredicate.class));
        var actualResult = actual.anyMatch(failing(IntPredicate.class));
        assertEquals(expectedResult, actualResult);
    }

    public void testAnyMatchParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.anyMatch(null));
        assertThrows(NullPointerException.class, () -> actual.anyMatch(null));
    }

    public void testAllMatch(IntStream expected, IntStream actual) {
        var expectedResult = expected.allMatch(failing(IntPredicate.class));
        var actualResult = actual.allMatch(failing(IntPredicate.class));
        assertEquals(expectedResult, actualResult);
    }

    public void testAllMatchParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.allMatch(null));
        assertThrows(NullPointerException.class, () -> actual.allMatch(null));
    }

    public void testNoneMatch(IntStream expected, IntStream actual) {
        var expectedResult = expected.noneMatch(failing(IntPredicate.class));
        var actualResult = actual.noneMatch(failing(IntPredicate.class));
        assertEquals(expectedResult, actualResult);
    }

    public void testNoneMatchParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.noneMatch(null));
        assertThrows(NullPointerException.class, () -> actual.noneMatch(null));
    }

    public void testFindFirst(IntStream expected, IntStream actual) {
        var expectedResult = expected.findFirst();
        var actualResult = actual.findFirst();
        compareResults(expectedResult, actualResult);
    }

    public void testFindAny(IntStream expected, IntStream actual) {
        var expectedResult = expected.findAny();
        var actualResult = actual.findAny();
        compareResults(expectedResult, actualResult);
    }

    public void testAsLongStream(IntStream expected, IntStream actual) {
        LongStream expectedAsLongStream = expected.asLongStream();
        LongStream actualAsLongStream = actual.asLongStream();
        long[] expectedResult = expectedAsLongStream.toArray();
        long[] actualResult = actualAsLongStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testAsDoubleStream(IntStream expected, IntStream actual) {
        DoubleStream expectedAsDoubleStream = expected.asDoubleStream();
        DoubleStream actualAsDoubleStream = actual.asDoubleStream();
        double[] expectedResult = expectedAsDoubleStream.toArray();
        double[] actualResult = actualAsDoubleStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testBoxed(IntStream expected, IntStream actual) {
        Stream<Integer> expectedAsStream = expected.boxed();
        Stream<Integer> actualAsStream = actual.boxed();
        var expectedResult = expectedAsStream.toArray();
        var actualResult = actualAsStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapMulti(IntStream expected, IntStream actual) {
        var expectedResult = expected.mapMulti(failing(IntStream.IntMapMultiConsumer.class)).toArray();
        var actualResult = actual.mapMulti(failing(IntStream.IntMapMultiConsumer.class)).toArray();

        compareResults(expectedResult, actualResult);
    }

    public void testMapMultiParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.mapMulti(null));
        assertThrows(NullPointerException.class, () -> actual.mapMulti(null));
    }

    public void testTakeWhile(IntStream expected, IntStream actual) {
        var expectedResult = expected.takeWhile(failing(IntPredicate.class)).toArray();
        var actualResult = actual.takeWhile(failing(IntPredicate.class)).toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testTakeWhileParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.takeWhile(null));
        assertThrows(NullPointerException.class, () -> actual.takeWhile(null));
    }

    public void testDropWhile(IntStream expected, IntStream actual) {
        var expectedResult = expected.dropWhile(failing(IntPredicate.class)).toArray();
        var actualResult = actual.dropWhile(failing(IntPredicate.class)).toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testDropWhileParameters(IntStream expected, IntStream actual) {
        assertThrows(NullPointerException.class, () -> expected.dropWhile(null));
        assertThrows(NullPointerException.class, () -> actual.dropWhile(null));
    }
}
