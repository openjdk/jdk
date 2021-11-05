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
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
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
 * @summary Checks that the EmptyDoubleStream is equivalent to the
 * old way of creating it with StreamSupport.
 * @author Heinz Kabutz
 */

public final class EmptyDoubleStreamTest extends EmptyBaseStreamTest {
    @Test
    public void testAll() {
        this.compare(DoubleStream.class,
                () -> StreamSupport.doubleStream(Spliterators.emptyDoubleSpliterator(), false),
                DoubleStream::empty);
        this.compare(DoubleStream.class,
                () -> StreamSupport.doubleStream(Spliterators.emptyDoubleSpliterator(), true),
                () -> DoubleStream.empty().parallel());
    }

    public void testFilter(DoubleStream expected, DoubleStream actual) {
        expected = expected.filter(failing(DoublePredicate.class));
        actual = actual.filter(failing(DoublePredicate.class));
        var expectedResult = expected.toArray();
        var actualResult = actual.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testFilterParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.filter(null));
        assertThrows(NullPointerException.class, () -> actual.filter(null));
    }

    public void testMap(DoubleStream expected, DoubleStream actual) {
        DoubleStream expectedAsDoubleStream = expected.map(failing(DoubleUnaryOperator.class));
        DoubleStream actualAsDoubleStream = actual.map(failing(DoubleUnaryOperator.class));
        var expectedResult = expectedAsDoubleStream.toArray();
        var actualResult = actualAsDoubleStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.map(null));
        assertThrows(NullPointerException.class, () -> actual.map(null));
    }

    public void testMapToObj(DoubleStream expected, DoubleStream actual) {
        Stream<Double> expectedAsStream = expected.mapToObj(failing(DoubleFunction.class));
        Stream<Double> actualAsStream = actual.mapToObj(failing(DoubleFunction.class));
        var expectedResult = expectedAsStream.toArray();
        var actualResult = actualAsStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapToObjParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.mapToObj(null));
        assertThrows(NullPointerException.class, () -> actual.mapToObj(null));
    }

    public void testMapToInt(DoubleStream expected, DoubleStream actual) {
        IntStream expectedAsDoubleStream = expected.mapToInt(failing(DoubleToIntFunction.class));
        IntStream actualAsDoubleStream = actual.mapToInt(failing(DoubleToIntFunction.class));
        var expectedResult = expectedAsDoubleStream.toArray();
        var actualResult = actualAsDoubleStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapToIntParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.mapToInt(null));
        assertThrows(NullPointerException.class, () -> actual.mapToInt(null));
    }

    public void testMapToLong(DoubleStream expected, DoubleStream actual) {
        LongStream expectedAsDoubleStream = expected.mapToLong(failing(DoubleToLongFunction.class));
        LongStream actualAsDoubleStream = actual.mapToLong(failing(DoubleToLongFunction.class));
        var expectedResult = expectedAsDoubleStream.toArray();
        var actualResult = actualAsDoubleStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapToDoubleParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.mapToLong(null));
        assertThrows(NullPointerException.class, () -> actual.mapToLong(null));
    }

    public void testFlatMap(DoubleStream expected, DoubleStream actual) {
        DoubleStream expectedAsIntStream = expected.flatMap(failing(DoubleFunction.class));
        DoubleStream actualAsIntStream = actual.flatMap(failing(DoubleFunction.class));
        var expectedResult = expectedAsIntStream.toArray();
        var actualResult = actualAsIntStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testFlatMapParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.flatMap(null));
        assertThrows(NullPointerException.class, () -> actual.flatMap(null));
    }

    public void testDistinct(DoubleStream expected, DoubleStream actual) {
        expected = expected.distinct();
        actual = actual.distinct();
        var expectedResult = expected.toArray();
        var actualResult = actual.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testSorted(DoubleStream expected, DoubleStream actual) {
        expected = expected.sorted();
        actual = actual.sorted();
        var expectedResult = expected.toArray();
        var actualResult = actual.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testPeek(DoubleStream expected, DoubleStream actual) {
        expected.peek(failing(DoubleConsumer.class)).toArray();
        actual.peek(failing(DoubleConsumer.class)).toArray();
    }

    public void testPeekParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.peek(null));
        assertThrows(NullPointerException.class, () -> actual.peek(null));
    }

    public void testLimit(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.limit(10).toArray();
        var actualResult = actual.limit(10).toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testLimitParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(IllegalArgumentException.class, () -> expected.limit(-1));
        assertThrows(IllegalArgumentException.class, () -> actual.limit(-1));
    }

    public void testSkip(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.skip(10).toArray();
        var actualResult = actual.skip(10).toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testSkipParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(IllegalArgumentException.class, () -> expected.skip(-1));
        assertThrows(IllegalArgumentException.class, () -> actual.skip(-1));
    }

    public void testForEach(DoubleStream expected, DoubleStream actual) {
        expected.forEach(failing(DoubleConsumer.class));
        actual.forEach(failing(DoubleConsumer.class));
    }

    public void testForEachParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.forEach(null));
        assertThrows(NullPointerException.class, () -> actual.forEach(null));
    }

    public void testForEachOrdered(DoubleStream expected, DoubleStream actual) {
        expected.forEachOrdered(failing(DoubleConsumer.class));
        actual.forEachOrdered(failing(DoubleConsumer.class));
    }

    public void testForEachOrderedParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.forEachOrdered(null));
        assertThrows(NullPointerException.class, () -> actual.forEachOrdered(null));
    }

    public void testToArray(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.toArray();
        var actualResult = actual.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testReduceIntBinaryOperator(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.reduce(failing(DoubleBinaryOperator.class));
        var actualResult = actual.reduce(failing(DoubleBinaryOperator.class));

        compareResults(expectedResult, actualResult);
    }

    public void testReduceIntBinaryOperatorParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.reduce(null));
        assertThrows(NullPointerException.class, () -> actual.reduce(null));
    }

    public void testReduceIdentityIntBinaryOperator(DoubleStream expected, DoubleStream actual) {
        var magicIdentity = 42;
        var expectedResult = expected.reduce(magicIdentity, failing(DoubleBinaryOperator.class));
        var actualResult = actual.reduce(magicIdentity, failing(DoubleBinaryOperator.class));

        assertEquals(magicIdentity, expectedResult);
        assertEquals(magicIdentity, actualResult);
    }

    public void testReduceIdentityIntBinaryOperatorParameters(DoubleStream expected, DoubleStream actual) {
        int magicInt = 42;
        assertThrows(NullPointerException.class, () -> expected.reduce(magicInt, null));
        assertThrows(NullPointerException.class, () -> actual.reduce(magicInt, null));
    }

    public void testCollect(DoubleStream expected, DoubleStream actual) {
        var magicIdentity = new Object();
        Supplier<Object> supplier = () -> magicIdentity;
        ObjDoubleConsumer<Object> accumulator = failing(ObjDoubleConsumer.class);
        BiConsumer<Object, Object> combiner = failing(BiConsumer.class);

        var expectedResult = expected.collect(supplier, accumulator, combiner);
        var actualResult = actual.collect(supplier, accumulator, combiner);

        assertSame(magicIdentity, expectedResult);
        assertSame(magicIdentity, actualResult);
    }

    public void testCollectParameters(DoubleStream expected, DoubleStream actual) {
        Supplier<Object> supplier = Object::new;
        var accumulator = failing(ObjDoubleConsumer.class);
        var combiner = failing(BiConsumer.class);
        assertThrows(NullPointerException.class, () -> expected.collect(null, accumulator, combiner));
        assertThrows(NullPointerException.class, () -> actual.collect(null, accumulator, combiner));
        assertThrows(NullPointerException.class, () -> expected.collect(supplier, null, combiner));
        assertThrows(NullPointerException.class, () -> actual.collect(supplier, null, combiner));
        assertThrows(NullPointerException.class, () -> expected.collect(supplier, accumulator, null));
        assertThrows(NullPointerException.class, () -> actual.collect(supplier, accumulator, null));
    }

    public void testSum(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.sum();
        var actualResult = actual.sum();

        assertEquals(expectedResult, actualResult);
        assertEquals(0, actualResult);
    }

    public void testMin(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.min();
        var actualResult = actual.min();
        compareResults(expectedResult, actualResult);
    }

    public void testMax(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.max();
        var actualResult = actual.max();
        compareResults(expectedResult, actualResult);
    }

    public void testCount(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.count();
        var actualResult = actual.count();
        assertEquals(expectedResult, actualResult);
        assertEquals(0L, actualResult);
    }

    public void testAverage(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.average();
        var actualResult = actual.average();
        compareResults(expectedResult, actualResult);
    }

    public void testSummaryStatistics(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.summaryStatistics().toString();
        var actualResult = actual.summaryStatistics().toString();
        assertEquals(expectedResult, actualResult);
    }

    public void testAnyMatch(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.anyMatch(failing(DoublePredicate.class));
        var actualResult = actual.anyMatch(failing(DoublePredicate.class));
        assertEquals(expectedResult, actualResult);
    }

    public void testAnyMatchParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.anyMatch(null));
        assertThrows(NullPointerException.class, () -> actual.anyMatch(null));
    }

    public void testAllMatch(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.allMatch(failing(DoublePredicate.class));
        var actualResult = actual.allMatch(failing(DoublePredicate.class));
        assertEquals(expectedResult, actualResult);
    }

    public void testAllMatchParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.allMatch(null));
        assertThrows(NullPointerException.class, () -> actual.allMatch(null));
    }

    public void testNoneMatch(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.noneMatch(failing(DoublePredicate.class));
        var actualResult = actual.noneMatch(failing(DoublePredicate.class));
        assertEquals(expectedResult, actualResult);
    }

    public void testNoneMatchParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.noneMatch(null));
        assertThrows(NullPointerException.class, () -> actual.noneMatch(null));
    }

    public void testFindFirst(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.findFirst();
        var actualResult = actual.findFirst();
        compareResults(expectedResult, actualResult);
    }

    public void testFindAny(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.findAny();
        var actualResult = actual.findAny();
        compareResults(expectedResult, actualResult);
    }

    public void testBoxed(DoubleStream expected, DoubleStream actual) {
        Stream<Double> expectedAsStream = expected.boxed();
        Stream<Double> actualAsStream = actual.boxed();
        var expectedResult = expectedAsStream.toArray();
        var actualResult = actualAsStream.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapMulti(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.mapMulti(failing(DoubleStream.DoubleMapMultiConsumer.class)).toArray();
        var actualResult = actual.mapMulti(failing(DoubleStream.DoubleMapMultiConsumer.class)).toArray();

        compareResults(expectedResult, actualResult);
    }

    public void testMapMultiParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.mapMulti(null));
        assertThrows(NullPointerException.class, () -> actual.mapMulti(null));
    }

    public void testTakeWhile(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.takeWhile(failing(DoublePredicate.class)).toArray();
        var actualResult = actual.takeWhile(failing(DoublePredicate.class)).toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testTakeWhileParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.takeWhile(null));
        assertThrows(NullPointerException.class, () -> actual.takeWhile(null));
    }

    public void testDropWhile(DoubleStream expected, DoubleStream actual) {
        var expectedResult = expected.dropWhile(failing(DoublePredicate.class)).toArray();
        var actualResult = actual.dropWhile(failing(DoublePredicate.class)).toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testDropWhileParameters(DoubleStream expected, DoubleStream actual) {
        assertThrows(NullPointerException.class, () -> expected.dropWhile(null));
        assertThrows(NullPointerException.class, () -> actual.dropWhile(null));
    }
}
