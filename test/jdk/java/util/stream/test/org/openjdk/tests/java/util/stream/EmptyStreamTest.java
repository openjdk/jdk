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
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
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
                Stream::empty,
                () -> StreamSupport.stream(Spliterators.emptySpliterator(), false)
        );
        this.compare(Stream.class,
                () -> Stream.empty().parallel(),
                () -> StreamSupport.stream(Spliterators.emptySpliterator(), true)
        );
    }

    public void testFilter(Stream<Integer> actual, Stream<Integer> expected) {
        actual = actual.filter(failing(Predicate.class));
        expected = expected.filter(failing(Predicate.class));
        var actualResult = actual.toList();
        var expectedResult = expected.toList();
        compareResults(actualResult, expectedResult);
    }

    public void testFilterExceptions(Stream<?> actual, Stream<?> expected) {
        checkExpectedExceptions(actual, expected, "filter", Predicate.class);
    }

    public void testMap(Stream<Integer> actual, Stream<Integer> expected) {
        Stream<String> actualAsStrings = actual.map(failing(Function.class));
        Stream<String> expectedAsStrings = expected.map(failing(Function.class));
        var actualResult = actualAsStrings.toList();
        var expectedResult = expectedAsStrings.toList();
        compareResults(actualResult, expectedResult);
    }

    public void testMapExceptions(Stream<?> actual, Stream<?> expected) {
        checkExpectedExceptions(actual, expected, "map", Function.class);
    }

    public void testMapToInt(Stream<Integer> actual, Stream<Integer> expected) {
        IntStream actualAsIntStream = actual.mapToInt(failing(ToIntFunction.class));
        IntStream expectedAsIntStream = expected.mapToInt(failing(ToIntFunction.class));
        var actualResult = actualAsIntStream.toArray();
        var expectedResult = expectedAsIntStream.toArray();
        compareResults(actualResult, expectedResult);
        assertEquals(actual.isParallel(), actualAsIntStream.isParallel());
        assertEquals(expected.isParallel(), expectedAsIntStream.isParallel());
    }

    public void testMapToIntExceptions(Stream<?> actual, Stream<?> expected) {
        checkExpectedExceptions(actual, expected, "mapToInt", ToIntFunction.class);
    }

    public void testMapToLong(Stream<Integer> actual, Stream<Integer> expected) {
        LongStream actualAsLongStream = actual.mapToLong(failing(ToLongFunction.class));
        LongStream expectedAsLongStream = expected.mapToLong(failing(ToLongFunction.class));
        var actualResult = actualAsLongStream.toArray();
        var expectedResult = expectedAsLongStream.toArray();
        compareResults(actualResult, expectedResult);
        assertEquals(actual.isParallel(), actualAsLongStream.isParallel());
        assertEquals(expected.isParallel(), expectedAsLongStream.isParallel());
    }

    public void testMapToLongExceptions(Stream<Integer> actual, Stream<Integer> expected) {
        checkExpectedExceptions(actual, expected, "mapToLong", ToLongFunction.class);
    }

    public void testMapToDouble(Stream<Integer> actual, Stream<Integer> expected) {
        DoubleStream actualAsDoubleStream = actual.mapToDouble(failing(ToDoubleFunction.class));
        DoubleStream expectedAsDoubleStream = expected.mapToDouble(failing(ToDoubleFunction.class));
        var actualResult = actualAsDoubleStream.toArray();
        var expectedResult = expectedAsDoubleStream.toArray();
        compareResults(actualResult, expectedResult);
        assertEquals(actual.isParallel(), actualAsDoubleStream.isParallel());
        assertEquals(expected.isParallel(), expectedAsDoubleStream.isParallel());
    }

    public void testMapToDoubleExceptions(Stream<Integer> actual, Stream<Integer> expected) {
        checkExpectedExceptions(actual, expected, "mapToDouble", ToDoubleFunction.class);
    }

    public void testFlatMap(Stream<Stream<Integer>> actual, Stream<Stream<Integer>> expected) {
        var actualResult = actual.flatMap(Function.identity()).toList();
        var expectedResult = expected.flatMap(Function.identity()).toList();
        compareResults(actualResult, expectedResult);
    }

    public void testFlatMapExceptions(Stream<Stream<Integer>> actual, Stream<Stream<Integer>> expected) {
        checkExpectedExceptions(actual, expected, "flatMap", Function.class);
    }

    public void testFlatMapToInt(Stream<IntStream> actual, Stream<IntStream> expected) {
        var actualResult = actual.flatMapToInt(Function.identity()).toArray();
        var expectedResult = expected.flatMapToInt(Function.identity()).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testFlatMapToIntExceptions(Stream<IntStream> actual, Stream<IntStream> expected) {
        checkExpectedExceptions(actual, expected, "flatMapToInt", Function.class);
    }

    public void testFlatMapToLong(Stream<LongStream> actual, Stream<LongStream> expected) {
        var actualResult = actual.flatMapToLong(Function.identity()).toArray();
        var expectedResult = expected.flatMapToLong(Function.identity()).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testFlatMapToLongExceptions(Stream<LongStream> actual, Stream<LongStream> expected) {
        checkExpectedExceptions(actual, expected, "flatMapToLong", Function.class);
    }

    public void testFlatMapToDouble(Stream<DoubleStream> actual, Stream<DoubleStream> expected) {
        var actualResult = actual.flatMapToDouble(Function.identity()).toArray();
        var expectedResult = expected.flatMapToDouble(Function.identity()).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testFlatMapToDoubleExceptions(Stream<DoubleStream> actual, Stream<DoubleStream> expected) {
        checkExpectedExceptions(actual, expected, "flatMapToDouble", Function.class);
    }

    public void testMapMulti(Stream<Number> actual, Stream<Number> expected) {
        var actualResult = actual.mapMulti(failing(BiConsumer.class)).toList();
        var expectedResult = expected.mapMulti(failing(BiConsumer.class)).toList();
        compareResults(actualResult, expectedResult);
    }

    public void testMapMultiExceptions(Stream<Number> actual, Stream<Number> expected) {
        checkExpectedExceptions(actual, expected, "mapMulti", BiConsumer.class);
    }

    public void testMapMultiToInt(Stream<Number> actual, Stream<Number> expected) {
        var actualResult = actual.mapMultiToInt(failing(BiConsumer.class)).toArray();
        var expectedResult = expected.mapMultiToInt(failing(BiConsumer.class)).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapMultiToIntExceptions(Stream<Number> actual, Stream<Number> expected) {
        checkExpectedExceptions(actual, expected, "mapMultiToInt", BiConsumer.class);
    }

    public void testMapMultiToLong(Stream<Number> actual, Stream<Number> expected) {
        var actualResult = actual.mapMultiToLong(failing(BiConsumer.class)).toArray();
        var expectedResult = expected.mapMultiToLong(failing(BiConsumer.class)).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapMultiToLongExceptions(Stream<Number> actual, Stream<Number> expected) {
        checkExpectedExceptions(actual, expected, "mapMultiToLong", BiConsumer.class);
    }

    public void testMapMultiToDouble(Stream<Number> actual, Stream<Number> expected) {
        var actualResult = actual.mapMultiToDouble(failing(BiConsumer.class)).toArray();
        var expectedResult = expected.mapMultiToDouble(failing(BiConsumer.class)).toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testMapMultiToDoubleExceptions(Stream<Number> actual, Stream<Number> expected) {
        checkExpectedExceptions(actual, expected, "mapMultiToDouble", BiConsumer.class);
    }

    public void testDistinct(Stream<?> actual, Stream<?> expected) {
        actual = actual.distinct();
        expected = expected.distinct();
        var actualResult = actual.toList();
        var expectedResult = expected.toList();
        compareResults(actualResult, expectedResult);
    }

    public void testDistinctExceptions(Stream<Number> actual, Stream<Number> expected) {
        checkExpectedExceptions(actual, expected, "distinct");
    }

    public void testSorted(Stream<Integer> actual, Stream<Integer> expected) {
        actual = actual.sorted();
        expected = expected.sorted();
        var actualResult = actual.toList();
        var expectedResult = expected.toList();
        compareResults(actualResult, expectedResult);
    }

    public void testSortedExceptions(Stream<Number> actual, Stream<Number> expected) {
        checkExpectedExceptions(actual, expected, "sorted");
    }

    public void testSortedComparator(Stream<Integer> actual, Stream<Integer> expected) {
        actual = actual.sorted(Comparator.reverseOrder());
        expected = expected.sorted(Comparator.reverseOrder());
        var actualResult = actual.toList();
        var expectedResult = expected.toList();
        compareResults(actualResult, expectedResult);
    }

    public void testSortedComparatorNullPointerExceptions(Stream<Integer> actual, Stream<Integer> expected) {
        checkExpectedExceptions(actual, expected, "sorted", Comparator.class);
    }

    public void testPeek(Stream<Integer> actual, Stream<Integer> expected) {
        actual.peek(failing(Consumer.class)).toList();
        expected.peek(failing(Consumer.class)).toList();
    }

    public void testPeekExceptions(Stream<Integer> actual, Stream<Integer> expected) {
        checkExpectedExceptions(actual, expected, "peek", Consumer.class);
    }

    public void testSkip(Stream<Integer> actual, Stream<Integer> expected) {
        var actualResult = actual.skip(10).toList();
        var expectedResult = expected.skip(10).toList();
        compareResults(actualResult, expectedResult);
    }

    public void testSkipExceptions(Stream<Integer> actual, Stream<Integer> expected) {
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

    public void testTakeWhile(Stream<Integer> actual, Stream<Integer> expected) {
        var actualResult = actual.takeWhile(failing(Predicate.class)).toList();
        var expectedResult = expected.takeWhile(failing(Predicate.class)).toList();
        compareResults(actualResult, expectedResult);
    }

    public void testTakeWhileExceptions(Stream<Integer> actual, Stream<Integer> expected) {
        checkExpectedExceptions(actual, expected, "takeWhile", Predicate.class);
    }

    public void testDropWhile(Stream<Integer> actual, Stream<Integer> expected) {
        var actualResult = actual.dropWhile(failing(Predicate.class)).toList();
        var expectedResult = expected.dropWhile(failing(Predicate.class)).toList();
        compareResults(actualResult, expectedResult);
    }

    public void testDropWhileExceptions(Stream<Integer> actual, Stream<Integer> expected) {
        checkExpectedExceptions(actual, expected, "dropWhile", Predicate.class);
    }

    public void testForEach(Stream<Integer> actual, Stream<Integer> expected) {
        actual.forEach(failing(Consumer.class));
        expected.forEach(failing(Consumer.class));
    }

    public void testForEachExceptions(Stream<Integer> actual, Stream<Integer> expected) {
        assertThrows(NullPointerException.class, () -> actual.forEach(null));
        assertThrows(NullPointerException.class, () -> expected.forEach(null));

        var actualException = getThrowableType(() -> actual.forEach(null));
        var expectedException = getThrowableType(() -> expected.forEach(null));
        assertSame(actualException, expectedException);

        actualException = getThrowableType(() -> actual.forEach(null));
        expectedException = getThrowableType(() -> expected.forEach(null));
        assertSame(actualException, expectedException);

        actualException = getThrowableType(() -> actual.forEach(failing(Consumer.class)));
        expectedException = getThrowableType(() -> expected.forEach(failing(Consumer.class)));
        assertSame(actualException, expectedException);

        actualException = getThrowableType(() -> actual.forEach(failing(Consumer.class)));
        expectedException = getThrowableType(() -> expected.forEach(failing(Consumer.class)));
        assertSame(actualException, expectedException);
    }

    public void testForEachOrdered(Stream<Integer> actual, Stream<Integer> expected) {
        actual.forEachOrdered(failing(Consumer.class));
        expected.forEachOrdered(failing(Consumer.class));
    }

    public void testForEachOrderedExceptions(Stream<Integer> actual, Stream<Integer> expected) {
        assertThrows(NullPointerException.class, () -> actual.forEachOrdered(null));
        assertThrows(NullPointerException.class, () -> expected.forEachOrdered(null));

        var actualException = getThrowableType(() -> actual.forEachOrdered(null));
        var expectedException = getThrowableType(() -> expected.forEachOrdered(null));
        assertSame(actualException, expectedException);

        actualException = getThrowableType(() -> actual.forEachOrdered(null));
        expectedException = getThrowableType(() -> expected.forEachOrdered(null));
        assertSame(actualException, expectedException);

        actualException = getThrowableType(() -> actual.forEachOrdered(failing(Consumer.class)));
        expectedException = getThrowableType(() -> expected.forEachOrdered(failing(Consumer.class)));
        assertSame(actualException, expectedException);

        actualException = getThrowableType(() -> actual.forEachOrdered(failing(Consumer.class)));
        expectedException = getThrowableType(() -> expected.forEachOrdered(failing(Consumer.class)));
        assertSame(actualException, expectedException);
    }

    public void testToArray(Stream<Integer> actual, Stream<Integer> expected) {
        var actualResult = actual.toArray();
        var expectedResult = expected.toArray();
        compareResults(actualResult, expectedResult);
    }

    public void testToArrayExceptions(Stream<Integer> actual, Stream<Integer> expected) {
        checkExpectedExceptions(actual, expected, "toArray");
    }

    public void testToArrayFunction(Stream<Integer> actual, Stream<Integer> expected) {
        var actualResult = actual.toArray(Integer[]::new);
        var expectedResult = expected.toArray(Integer[]::new);
        compareResults(actualResult, expectedResult);
        assertEquals(Integer.class, expectedResult.getClass().getComponentType());
    }

    public void testToArrayFunctionExceptions(Stream<Integer> actual, Stream<Integer> expected) {
        checkExpectedExceptions(actual, expected, "toArray", IntFunction.class);
    }

    public void testToArrayFunctionReturnNullException(Stream<Integer> actual, Stream<Integer> expected) {
        assertThrows(NullPointerException.class, () -> actual.toArray(i -> null));
        assertThrows(NullPointerException.class, () -> expected.toArray(i -> null));
    }

    public void testReduceIdentityAccumulator(Stream<Object> actual, Stream<Object> expected) {
        var magicIdentity = new Object();
        var actualResult = actual.reduce(magicIdentity, failing(BinaryOperator.class));
        var expectedResult = expected.reduce(magicIdentity, failing(BinaryOperator.class));

        assertSame(magicIdentity, actualResult);
        assertSame(magicIdentity, expectedResult);
    }

    public void testReduceIdentityAccumulatorExceptions(Stream<Object> actual, Stream<Object> expected) {
        checkExpectedExceptions(actual, expected, "reduce", Object.class, BinaryOperator.class);
    }

    public void testReduceAccumulator(Stream<Object> actual, Stream<Object> expected) {
        var actualResult = actual.reduce(failing(BinaryOperator.class));
        var expectedResult = expected.reduce(failing(BinaryOperator.class));

        compareResults(actualResult, expectedResult);
    }

    public void testReduceAccumulatorExceptions(Stream<Object> actual, Stream<Object> expected) {
        checkExpectedExceptions(actual, expected, "reduce", BinaryOperator.class);
    }

    public void testReduceIdentityAccumulatorCombiner(Stream<Object> actual, Stream<Object> expected) {
        var magicIdentity = new Object();
        var actualResult = actual.reduce(magicIdentity, failing(BinaryOperator.class), failing(BinaryOperator.class));
        var expectedResult = expected.reduce(magicIdentity, failing(BinaryOperator.class), failing(BinaryOperator.class));

        assertSame(magicIdentity, actualResult);
        assertSame(magicIdentity, expectedResult);
    }

    public void testReduceIdentityAccumulatorCombinerExceptions(Stream<Object> actual, Stream<Object> expected) {
        checkExpectedExceptions(actual, expected, "reduce", Object.class, BiFunction.class, BinaryOperator.class);
    }

    public void testCollectSupplierAccumulatorCombiner(Stream<Integer> actual, Stream<Integer> expected) {
        var magicIdentity = new Object();
        var actualResult = actual.collect(() -> magicIdentity, failing(BiConsumer.class), failing(BiConsumer.class));
        var expectedResult = expected.collect(() -> magicIdentity, failing(BiConsumer.class), failing(BiConsumer.class));

        assertSame(magicIdentity, actualResult);
        assertSame(magicIdentity, expectedResult);
    }

    public void testCollectSupplierAccumulatorCombinerExceptions(Stream<Integer> actual, Stream<Integer> expected) {
        var magicIdentity = new Object();
        Supplier<Object> supplier = () -> magicIdentity;
        var accumulator = failing(BiConsumer.class);
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

    public void testCollectCollector(Stream<Object> actual, Stream<Object> expected) {
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
        var actualResult = actual.collect(collector);
        var expectedResult = expected.collect(collector);

        assertSame(superMagicIdentity, actualResult);
        assertSame(superMagicIdentity, expectedResult);
    }

    public void testCollectCollectorExceptions(Stream<Object> actual, Stream<Object> expected) {
        assertThrows(NullPointerException.class, () -> actual.collect(null));
        assertThrows(NullPointerException.class, () -> expected.collect(null));

        actual.collect(Collectors.toList());
        expected.collect(Collectors.toList());

        assertThrows(NullPointerException.class, () -> actual.collect(null));
        assertThrows(NullPointerException.class, () -> expected.collect(null));

        assertThrows(IllegalStateException.class, () -> actual.collect(Collectors.toList()));
        assertThrows(IllegalStateException.class, () -> expected.collect(Collectors.toList()));
    }

    public void testToList(Stream<Integer> actual, Stream<Integer> expected) {
        var actualResult = actual.toList();
        var expectedResult = expected.toList();
        compareResults(actualResult, expectedResult);
    }

    public void testToListExceptions(Stream<Object> actual, Stream<Object> expected) {
        checkExpectedExceptions(actual, expected, "toList");
    }

    public void testMin(Stream<Integer> actual, Stream<Integer> expected) {
        var actualResult = actual.min(Comparator.reverseOrder());
        var expectedResult = expected.min(Comparator.reverseOrder());
        compareResults(actualResult, expectedResult);
    }

    public void testMinExceptions(Stream<Object> actual, Stream<Object> expected) {
        checkExpectedExceptions(actual, expected, "min", Comparator.class);
    }

    public void testMax(Stream<Integer> actual, Stream<Integer> expected) {
        var actualResult = actual.max(Comparator.reverseOrder());
        var expectedResult = expected.max(Comparator.reverseOrder());
        compareResults(actualResult, expectedResult);
    }

    public void testMaxExceptions(Stream<Object> actual, Stream<Object> expected) {
        checkExpectedExceptions(actual, expected, "max", Comparator.class);
    }

    public void testCount(Stream<Integer> actual, Stream<Integer> expected) {
        var actualResult = actual.count();
        var expectedResult = expected.count();
        assertEquals(actualResult, expectedResult);
        assertEquals(actualResult, 0L);
    }

    public void testCountExceptions(Stream<Object> actual, Stream<Object> expected) {
        checkExpectedExceptions(actual, expected, "count");
    }

    public void testAnyMatch(Stream<?> actual, Stream<?> expected) {
        var actualResult = actual.anyMatch(failing(Predicate.class));
        var expectedResult = expected.anyMatch(failing(Predicate.class));
        assertEquals(actualResult, expectedResult);
    }

    public void testAnyMatchExceptions(Stream<?> actual, Stream<?> expected) {
        checkExpectedExceptions(actual, expected, "anyMatch", Predicate.class);
    }

    public void testAllMatch(Stream<?> actual, Stream<?> expected) {
        var actualResult = actual.allMatch(failing(Predicate.class));
        var expectedResult = expected.allMatch(failing(Predicate.class));
        assertEquals(actualResult, expectedResult);
    }

    public void testAllMatchExceptions(Stream<?> actual, Stream<?> expected) {
        checkExpectedExceptions(actual, expected, "allMatch", Predicate.class);
    }

    public void testNoneMatch(Stream<?> actual, Stream<?> expected) {
        var actualResult = actual.noneMatch(failing(Predicate.class));
        var expectedResult = expected.noneMatch(failing(Predicate.class));
        assertEquals(actualResult, expectedResult);
    }

    public void testNoneMatchExceptions(Stream<?> actual, Stream<?> expected) {
        checkExpectedExceptions(actual, expected, "noneMatch", Predicate.class);
    }

    public void testFindFirst(Stream<Integer> actual, Stream<Integer> expected) {
        var actualResult = actual.findFirst();
        var expectedResult = expected.findFirst();
        compareResults(actualResult, expectedResult);
    }

    public void testFindFirstExceptions(Stream<?> actual, Stream<?> expected) {
        checkExpectedExceptions(actual, expected, "findFirst");
    }

    public void testFindAny(Stream<Integer> actual, Stream<Integer> expected) {
        var actualResult = actual.findAny();
        var expectedResult = expected.findAny();
        compareResults(actualResult, expectedResult);
    }

    public void testFindAnyExceptions(Stream<?> actual, Stream<?> expected) {
        checkExpectedExceptions(actual, expected, "findAny");
    }

    public void testLimit(Stream<Integer> actual, Stream<Integer> expected) {
        var actualResult = actual.limit(10).toList();
        var expectedResult = expected.limit(10).toList();
        compareResults(actualResult, expectedResult);
    }

    public void testLimitExceptions(Stream<Integer> actual, Stream<Integer> expected) {
        assertThrows(IllegalArgumentException.class, () -> actual.limit(-1));
        assertThrows(IllegalArgumentException.class, () -> expected.limit(-1));

        actual.limit(10);
        expected.limit(10);
        assertThrows(IllegalStateException.class, () -> actual.limit(10));
        assertThrows(IllegalStateException.class, () -> expected.limit(10));
    }
}