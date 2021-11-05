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

import java.util.Comparator;
import java.util.Set;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
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

public final class EmptyStreamTest extends EmptyBaseStreamTest {
    @Test
    public void testAll() {
        this.compare(Stream.class,
                () -> StreamSupport.stream(Spliterators.emptySpliterator(), false),
                Stream::empty);
        this.compare(Stream.class,
                () -> StreamSupport.stream(Spliterators.emptySpliterator(), true),
                () -> Stream.empty().parallel());
    }

    public void testFilter(Stream<Integer> expected, Stream<Integer> actual) {
        expected = expected.filter(failing(Predicate.class));
        actual = actual.filter(failing(Predicate.class));
        var expectedResult = expected.toList();
        var actualResult = actual.toList();
        compareResults(expectedResult, actualResult);
    }

    public void testFilterParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.filter(null));
        assertThrows(NullPointerException.class, () -> actual.filter(null));
    }

    public void testMap(Stream<Integer> expected, Stream<Integer> actual) {
        Stream<String> expectedAsStrings = expected.map(failing(Function.class));
        Stream<String> actualAsStrings = actual.map(failing(Function.class));
        var expectedResult = expectedAsStrings.toList();
        var actualResult = actualAsStrings.toList();
        compareResults(expectedResult, actualResult);
    }

    public void testMapParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.map(null));
        assertThrows(NullPointerException.class, () -> actual.map(null));
    }

    public void testMapToInt(Stream<Integer> expected, Stream<Integer> actual) {
        IntStream expectedAsIntStream = expected.mapToInt(failing(ToIntFunction.class));
        IntStream actualAsIntStream = actual.mapToInt(failing(ToIntFunction.class));
        var expectedResult = expectedAsIntStream.toArray();
        var actualResult = actualAsIntStream.toArray();
        compareResults(expectedResult, actualResult);
        assertEquals(expected.isParallel(), expectedAsIntStream.isParallel());
        assertEquals(actual.isParallel(), actualAsIntStream.isParallel());
    }

    public void testMapToIntParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.mapToInt(null));
        assertThrows(NullPointerException.class, () -> actual.mapToInt(null));
    }

    public void testMapToLong(Stream<Integer> expected, Stream<Integer> actual) {
        LongStream expectedAsLongStream = expected.mapToLong(failing(ToLongFunction.class));
        LongStream actualAsLongStream = actual.mapToLong(failing(ToLongFunction.class));
        var expectedResult = expectedAsLongStream.toArray();
        var actualResult = actualAsLongStream.toArray();
        compareResults(expectedResult, actualResult);
        assertEquals(expected.isParallel(), expectedAsLongStream.isParallel());
        assertEquals(actual.isParallel(), actualAsLongStream.isParallel());
    }

    public void testMapToLongParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.mapToLong(null));
        assertThrows(NullPointerException.class, () -> actual.mapToLong(null));
    }

    public void testMapToDouble(Stream<Integer> expected, Stream<Integer> actual) {
        DoubleStream expectedAsDoubleStream = expected.mapToDouble(failing(ToDoubleFunction.class));
        DoubleStream actualAsDoubleStream = actual.mapToDouble(failing(ToDoubleFunction.class));
        var expectedResult = expectedAsDoubleStream.toArray();
        var actualResult = actualAsDoubleStream.toArray();
        compareResults(expectedResult, actualResult);
        assertEquals(expected.isParallel(), expectedAsDoubleStream.isParallel());
        assertEquals(actual.isParallel(), actualAsDoubleStream.isParallel());
    }

    public void testMapToDoubleParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.mapToDouble(null));
        assertThrows(NullPointerException.class, () -> actual.mapToDouble(null));
    }

    public void testFlatMap(Stream<Stream<Integer>> expected, Stream<Stream<Integer>> actual) {
        var expectedResult = expected.flatMap(Function.identity())
                .toList();
        var actualResult = actual.flatMap(Function.identity())
                .toList();
        compareResults(expectedResult, actualResult);
    }

    public void testFlatMapParameters(Stream<Stream<Integer>> expected, Stream<Stream<Integer>> actual) {
        assertThrows(NullPointerException.class, () -> expected.flatMap(null));
        assertThrows(NullPointerException.class, () -> actual.flatMap(null));
    }

    public void testFlatMapToInt(Stream<IntStream> expected, Stream<IntStream> actual) {
        var expectedResult = expected.flatMapToInt(Function.identity())
                .toArray();
        var actualResult = actual.flatMapToInt(Function.identity())
                .toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testFlatMapToIntParameters(Stream<IntStream> expected, Stream<IntStream> actual) {
        assertThrows(NullPointerException.class, () -> expected.flatMapToInt(null));
        assertThrows(NullPointerException.class, () -> actual.flatMapToInt(null));
    }

    public void testFlatMapToLong(Stream<LongStream> expected, Stream<LongStream> actual) {
        var expectedResult = expected.flatMapToLong(Function.identity())
                .toArray();
        var actualResult = actual.flatMapToLong(Function.identity())
                .toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testFlatMapToLongParameters(Stream<LongStream> expected, Stream<LongStream> actual) {
        assertThrows(NullPointerException.class, () -> expected.flatMapToLong(null));
        assertThrows(NullPointerException.class, () -> actual.flatMapToLong(null));
    }

    public void testFlatMapToDouble(Stream<DoubleStream> expected, Stream<DoubleStream> actual) {
        var expectedResult = expected.flatMapToDouble(Function.identity())
                .toArray();
        var actualResult = actual.flatMapToDouble(Function.identity())
                .toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testFlatMapToDoubleParameters(Stream<DoubleStream> expected, Stream<DoubleStream> actual) {
        assertThrows(NullPointerException.class, () -> expected.flatMapToDouble(null));
        assertThrows(NullPointerException.class, () -> actual.flatMapToDouble(null));
    }

    public void testMapMulti(Stream<Number> expected, Stream<Number> actual) {
        var expectedResult = expected.mapMulti(failing(BiConsumer.class))
                .toList();
        var actualResult = actual.mapMulti(failing(BiConsumer.class))
                .toList();
        compareResults(expectedResult, actualResult);
    }

    public void testMapMultiParameters(Stream<Number> expected, Stream<Number> actual) {
        assertThrows(NullPointerException.class, () -> expected.mapMulti(null));
        assertThrows(NullPointerException.class, () -> actual.mapMulti(null));
    }

    public void testMapMultiToInt(Stream<Number> expected, Stream<Number> actual) {
        var expectedResult = expected.mapMultiToInt(failing(BiConsumer.class))
                .toArray();
        var actualResult = actual.mapMultiToInt(failing(BiConsumer.class))
                .toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testMapMultiToIntParameters(Stream<Number> expected, Stream<Number> actual) {
        assertThrows(NullPointerException.class, () -> expected.mapMultiToInt(null));
        assertThrows(NullPointerException.class, () -> actual.mapMultiToInt(null));
    }

    public void testMapMultiToLong(Stream<Number> expected, Stream<Number> actual) {
        var expectedResult = expected.mapMultiToLong(failing(BiConsumer.class))
                .toArray();
        var actualResult = actual.mapMultiToLong(failing(BiConsumer.class))
                .toArray();

        compareResults(expectedResult, actualResult);
    }

    public void testMapMultiToLongParameters(Stream<Number> expected, Stream<Number> actual) {
        assertThrows(NullPointerException.class, () -> expected.mapMultiToLong(null));
        assertThrows(NullPointerException.class, () -> actual.mapMultiToLong(null));
    }

    public void testMapMultiToDouble(Stream<Number> expected, Stream<Number> actual) {
        var expectedResult = expected.mapMultiToDouble(failing(BiConsumer.class))
                .toArray();
        var actualResult = actual.mapMultiToDouble(failing(BiConsumer.class))
                .toArray();

        compareResults(expectedResult, actualResult);
    }

    public void testMapMultiToDoubleParameters(Stream<Number> expected, Stream<Number> actual) {
        assertThrows(NullPointerException.class, () -> expected.mapMultiToDouble(null));
        assertThrows(NullPointerException.class, () -> actual.mapMultiToDouble(null));
    }

    public void testDistinct(Stream<?> expected, Stream<?> actual) {
        expected = expected.distinct();
        actual = actual.distinct();
        var expectedResult = expected.toList();
        var actualResult = actual.toList();
        compareResults(expectedResult, actualResult);
    }

    public void testSorted(Stream<Integer> expected, Stream<Integer> actual) {
        expected = expected.sorted();
        actual = actual.sorted();
        var expectedResult = expected.toList();
        var actualResult = actual.toList();
        compareResults(expectedResult, actualResult);
    }

    public void testSortedComparator(Stream<Integer> expected, Stream<Integer> actual) {
        expected = expected.sorted(Comparator.reverseOrder());
        actual = actual.sorted(Comparator.reverseOrder());
        var expectedResult = expected.toList();
        var actualResult = actual.toList();
        compareResults(expectedResult, actualResult);
    }

    public void testSortedComparatorParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.sorted(null));
        assertThrows(NullPointerException.class, () -> actual.sorted(null));
    }

    public void testPeek(Stream<Integer> expected, Stream<Integer> actual) {
        expected.peek(failing(Consumer.class)).toList();
        actual.peek(failing(Consumer.class)).toList();
    }

    public void testPeekParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.peek(null));
        assertThrows(NullPointerException.class, () -> actual.peek(null));
    }

    public void testSkip(Stream<Integer> expected, Stream<Integer> actual) {
        var expectedResult = expected.skip(10).toList();
        var actualResult = actual.skip(10).toList();
        compareResults(expectedResult, actualResult);
    }

    public void testSkipParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(IllegalArgumentException.class, () -> expected.skip(-1));
        assertThrows(IllegalArgumentException.class, () -> actual.skip(-1));
    }

    public void testTakeWhile(Stream<Integer> expected, Stream<Integer> actual) {
        var expectedResult = expected.takeWhile(failing(Predicate.class))
                .toList();
        var actualResult = actual.takeWhile(failing(Predicate.class))
                .toList();
        compareResults(expectedResult, actualResult);
    }

    public void testTakeWhileParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.takeWhile(null));
        assertThrows(NullPointerException.class, () -> actual.takeWhile(null));
    }

    public void testDropWhile(Stream<Integer> expected, Stream<Integer> actual) {
        var expectedResult = expected.dropWhile(failing(Predicate.class))
                .toList();
        var actualResult = actual.dropWhile(failing(Predicate.class))
                .toList();
        compareResults(expectedResult, actualResult);
    }

    public void testDropWhileParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.dropWhile(null));
        assertThrows(NullPointerException.class, () -> actual.dropWhile(null));
    }

    public void testForEach(Stream<Integer> expected, Stream<Integer> actual) {
        expected.forEach(failing(Consumer.class));
        actual.forEach(failing(Consumer.class));
    }

    public void testForEachParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.forEach(null));
        assertThrows(NullPointerException.class, () -> actual.forEach(null));
    }

    public void testForEachOrdered(Stream<Integer> expected, Stream<Integer> actual) {
        expected.forEachOrdered(failing(Consumer.class));
        actual.forEachOrdered(failing(Consumer.class));
    }

    public void testForEachOrderedParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.forEachOrdered(null));
        assertThrows(NullPointerException.class, () -> actual.forEachOrdered(null));
    }

    public void testToArray(Stream<Integer> expected, Stream<Integer> actual) {
        var expectedResult = expected.toArray();
        var actualResult = actual.toArray();
        compareResults(expectedResult, actualResult);
    }

    public void testToArrayFunction(Stream<Integer> expected, Stream<Integer> actual) {
        var expectedResult = expected.toArray(Integer[]::new);
        var actualResult = actual.toArray(Integer[]::new);
        compareResults(expectedResult, actualResult);
        assertEquals(Integer.class, expectedResult.getClass()
                .getComponentType());
    }

    public void testToArrayFunctionParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.toArray(null));
        assertThrows(NullPointerException.class, () -> actual.toArray(null));
    }

    public void testReduceIdentityAccumulator(Stream<Object> expected, Stream<Object> actual) {
        var magicIdentity = new Object();
        var expectedResult = expected.reduce(magicIdentity, failing(BinaryOperator.class));
        var actualResult = actual.reduce(magicIdentity, failing(BinaryOperator.class));

        assertSame(magicIdentity, expectedResult);
        assertSame(magicIdentity, actualResult);
    }

    public void testReduceIdentityAccumulatorParameters(Stream<Object> expected, Stream<Object> actual) {
        // null identity should be fine
        expected.reduce(null, failing(BinaryOperator.class));
        actual.reduce(null, failing(BinaryOperator.class));
        var magicIdentity = new Object();
        assertThrows(NullPointerException.class, () -> expected.reduce(magicIdentity, null));
        assertThrows(NullPointerException.class, () -> actual.reduce(magicIdentity, null));
    }

    public void testReduceAccumulator(Stream<Object> expected, Stream<Object> actual) {
        var expectedResult = expected.reduce(failing(BinaryOperator.class));
        var actualResult = actual.reduce(failing(BinaryOperator.class));

        compareResults(expectedResult, actualResult);
    }

    public void testReduceAccumulatorParameters(Stream<Object> expected, Stream<Object> actual) {
        assertThrows(NullPointerException.class, () -> expected.reduce(null));
        assertThrows(NullPointerException.class, () -> actual.reduce(null));
    }

    public void testReduceIdentityAccumulatorCombiner(Stream<Object> expected, Stream<Object> actual) {
        var magicIdentity = new Object();
        var expectedResult = expected.reduce(magicIdentity, failing(BinaryOperator.class), failing(BinaryOperator.class));
        var actualResult = actual.reduce(magicIdentity, failing(BinaryOperator.class), failing(BinaryOperator.class));

        assertSame(magicIdentity, expectedResult);
        assertSame(magicIdentity, actualResult);
    }

    public void testReduceIdentityAccumulatorCombinerParameters(Stream<Object> expected, Stream<Object> actual) {
        var accumulator = failing(BinaryOperator.class);
        var combiner = failing(BinaryOperator.class);
        // null identity should be fine
        expected.reduce(null, accumulator, combiner);
        actual.reduce(null, accumulator, combiner);
        var magicIdentity = new Object();
        assertThrows(NullPointerException.class, () -> expected.reduce(magicIdentity, null, combiner));
        assertThrows(NullPointerException.class, () -> actual.reduce(magicIdentity, null, combiner));
        assertThrows(NullPointerException.class, () -> expected.reduce(magicIdentity, accumulator, null));
        assertThrows(NullPointerException.class, () -> actual.reduce(magicIdentity, accumulator, null));
    }

    public void testCollectSupplierAccumulatorCombiner(Stream<Integer> expected, Stream<Integer> actual) {
        var magicIdentity = new Object();
        var expectedResult = expected.collect(() -> magicIdentity, failing(BiConsumer.class), failing(BiConsumer.class));
        var actualResult = actual.collect(() -> magicIdentity, failing(BiConsumer.class), failing(BiConsumer.class));

        assertSame(magicIdentity, expectedResult);
        assertSame(magicIdentity, actualResult);
    }

    public void testCollectSupplierAccumulatorCombinerParameters(Stream<Integer> expected, Stream<Integer> actual) {
        var magicIdentity = new Object();
        Supplier<Object> supplier = () -> magicIdentity;
        var accumulator = failing(BiConsumer.class);
        var combiner = failing(BiConsumer.class);
        assertThrows(NullPointerException.class, () -> expected.collect(null, accumulator, combiner));
        assertThrows(NullPointerException.class, () -> actual.collect(null, accumulator, combiner));
        assertThrows(NullPointerException.class, () -> expected.collect(supplier, null, combiner));
        assertThrows(NullPointerException.class, () -> actual.collect(supplier, null, combiner));
        assertThrows(NullPointerException.class, () -> expected.collect(supplier, accumulator, null));
        assertThrows(NullPointerException.class, () -> actual.collect(supplier, accumulator, null));
    }

    public void testCollectCollector(Stream<Object> expected, Stream<Object> actual) {
        var magicIdentity = new Object();
        var superMagicIdentity = new Object();
        Collector<Object, Object, Object> collector = new Collector<>() {
            @Override
            public Supplier<Object> supplier() {
                return () -> magicIdentity;
            }

            @Override
            public BiConsumer<Object, Object> accumulator() {
                return failing(BiConsumer.class);
            }

            @Override
            public BinaryOperator<Object> combiner() {
                return failing(BinaryOperator.class);
            }

            @Override
            public Function<Object, Object> finisher() {
                return a -> {
                    assertSame(magicIdentity, a);
                    return superMagicIdentity;
                };
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Set.of();
            }
        };
        var expectedResult = expected.collect(collector);
        var actualResult = actual.collect(collector);

        assertSame(superMagicIdentity, expectedResult);
        assertSame(superMagicIdentity, actualResult);
    }

    public void testCollectCollectorParameters(Stream<Object> expected, Stream<Object> actual) {
        assertThrows(NullPointerException.class, () -> expected.collect(null));
        assertThrows(NullPointerException.class, () -> actual.collect(null));
    }

    public void testToList(Stream<Integer> expected, Stream<Integer> actual) {
        var expectedResult = expected.toList();
        var actualResult = actual.toList();
        compareResults(expectedResult, actualResult);
    }

    public void testMin(Stream<Integer> expected, Stream<Integer> actual) {
        var expectedResult = expected.min(Comparator.reverseOrder());
        var actualResult = actual.min(Comparator.reverseOrder());
        compareResults(expectedResult, actualResult);
    }

    public void testMinParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.min(null));
        assertThrows(NullPointerException.class, () -> actual.min(null));
    }

    public void testMax(Stream<Integer> expected, Stream<Integer> actual) {
        var expectedResult = expected.max(Comparator.reverseOrder());
        var actualResult = actual.max(Comparator.reverseOrder());
        compareResults(expectedResult, actualResult);
    }

    public void testMaxParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(NullPointerException.class, () -> expected.max(null));
        assertThrows(NullPointerException.class, () -> actual.max(null));
    }

    public void testCount(Stream<Integer> expected, Stream<Integer> actual) {
        var expectedResult = expected.count();
        var actualResult = actual.count();
        assertEquals(expectedResult, actualResult);
        assertEquals(0L, actualResult);
    }

    public void testAnyMatch(Stream<?> expected, Stream<?> actual) {
        var expectedResult = expected.anyMatch(failing(Predicate.class));
        var actualResult = actual.anyMatch(failing(Predicate.class));
        assertEquals(expectedResult, actualResult);
    }

    public void testAnyMatchParameters(Stream<?> expected, Stream<?> actual) {
        assertThrows(NullPointerException.class, () -> expected.anyMatch(null));
        assertThrows(NullPointerException.class, () -> actual.anyMatch(null));
    }

    public void testAllMatch(Stream<?> expected, Stream<?> actual) {
        var expectedResult = expected.allMatch(failing(Predicate.class));
        var actualResult = actual.allMatch(failing(Predicate.class));
        assertEquals(expectedResult, actualResult);
    }

    public void testAllMatchParameters(Stream<?> expected, Stream<?> actual) {
        assertThrows(NullPointerException.class, () -> expected.allMatch(null));
        assertThrows(NullPointerException.class, () -> actual.allMatch(null));
    }

    public void testNoneMatch(Stream<?> expected, Stream<?> actual) {
        var expectedResult = expected.noneMatch(failing(Predicate.class));
        var actualResult = actual.noneMatch(failing(Predicate.class));
        assertEquals(expectedResult, actualResult);
    }

    public void testNoneMatchParameters(Stream<?> expected, Stream<?> actual) {
        assertThrows(NullPointerException.class, () -> expected.noneMatch(null));
        assertThrows(NullPointerException.class, () -> actual.noneMatch(null));
    }

    public void testFindFirst(Stream<Integer> expected, Stream<Integer> actual) {
        var expectedResult = expected.findFirst();
        var actualResult = actual.findFirst();
        compareResults(expectedResult, actualResult);
    }

    public void testFindAny(Stream<Integer> expected, Stream<Integer> actual) {
        var expectedResult = expected.findAny();
        var actualResult = actual.findAny();
        compareResults(expectedResult, actualResult);
    }

    public void testLimit(Stream<Integer> expected, Stream<Integer> actual) {
        var expectedResult = expected.limit(10).toList();
        var actualResult = actual.limit(10).toList();
        compareResults(expectedResult, actualResult);
    }

    public void testLimitParameters(Stream<Integer> expected, Stream<Integer> actual) {
        assertThrows(IllegalArgumentException.class, () -> expected.limit(-1));
        assertThrows(IllegalArgumentException.class, () -> actual.limit(-1));
    }
}
