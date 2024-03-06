/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Basic tests for Monotonic.List implementations
 * @run junit BasicMonotonicListTest
 */

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.invoke.MethodHandle;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class BasicMonotonicListTest {

    private static final int SIZE = 7;
    private static final int INDEX = 2;

    @Test
    void testTypes() {
        Monotonic.List<Integer> ml1 = Monotonic.ofList(Integer.class, SIZE);
        Monotonic.List<Integer> ml2 = Monotonic.ofList(int.class, SIZE);
        Monotonic.List<Number> ml3 = Monotonic.ofList(Integer.class, SIZE);
        Monotonic.List<Object> ml4 = Monotonic.ofList(Integer.class, SIZE);
        Monotonic.List<Object> ml5 = Monotonic.ofList(int.class, SIZE);
    }

    @ParameterizedTest
    @MethodSource("emptyLists")
    void empty(Class<?> carrier, Monotonic.List<Integer> list) {
        assertFalse(list.isEmpty());
        assertEquals(SIZE, list.size());

        IntStream.range(0, SIZE).forEach(i ->
                assertFalse(list.isPresent(i))
        );

        IntStream.range(0, SIZE).forEach(i ->
                assertFalse(list.isPresent(i))
        );

        assertEquals(expectedToString(list), list.toString());

        IntStream.range(0, SIZE)
                .forEach(i ->
                        assertNull(list.get(i))
                );

        assertEquals(-1, list.indexOf(INDEX));
    }

    @ParameterizedTest
    @MethodSource("singleLists")
    void single(Class<?> carrier, Monotonic.List<Integer> list) {
        assertFalse(list.isEmpty());
        assertEquals(SIZE, list.size());

        IntStream.range(0, SIZE).forEach(i ->
                assertEquals(i == INDEX, list.isPresent(i))
        );

        Iterator<Integer> iterator = list.iterator();
        assertTrue(iterator.hasNext());

        assertEquals(expectedToString(list), list.toString());

        IntStream.range(0, SIZE)
                .filter(i -> i != INDEX)
                .forEach(i ->
                        assertNull(list.get(i))
                );

        assertEquals(INDEX, list.get(INDEX));

        assertEquals(INDEX, list.indexOf(INDEX));
    }

    @ParameterizedTest
    @MethodSource("unsupportedOperations")
    void uoe(String name, Consumer<List<Integer>> op) {
        for (Class<Integer> carrier : List.of(int.class, Integer.class)) {
            Monotonic.List<Integer> empty = Monotonic.ofList(carrier, SIZE);
            assertThrows(UnsupportedOperationException.class, () -> op.accept(empty), name + " (" + carrier + ")");
        }
    }

    @ParameterizedTest
    @MethodSource("nullOperations")
    void npe(String name, BiConsumer<Monotonic.List<?>, ?> op) {
        for (Class<Integer> carrier : List.of(int.class, Integer.class)) {
            Monotonic.List<Object> empty = Monotonic.ofList(carrier, SIZE);
            assertThrows(NullPointerException.class, () -> op.accept(empty, null), name + " (" + carrier + ")");
        }
    }

    private static Stream<Arguments> emptyLists() {
        return Stream.of(
                Arguments.of(int.class, Monotonic.ofList(int.class, SIZE)),
                Arguments.of(Integer.class, Monotonic.ofList(Integer.class, SIZE))
        );
    }

    private static Stream<Arguments> singleLists() {
        Monotonic.List<Integer> primitive = Monotonic.ofList(int.class, SIZE);
        primitive.put(INDEX, INDEX);
        Monotonic.List<Integer> reference = Monotonic.ofList(Integer.class, SIZE);
        reference.put(INDEX, INDEX);
        return Stream.of(
                Arguments.of(int.class, primitive),
                Arguments.of(Integer.class, reference)
        );
    }

    private static Stream<Arguments> unsupportedOperations() {
        return Stream.of(
                Arguments.of("clear", asConsumer(List::clear)),
                Arguments.of("removeIf", asConsumer(l -> l.removeIf(i -> i == INDEX))),
                Arguments.of("add", asConsumer(l -> l.add(13)))
                // Todo: add stuff
        );
    }

    private static Stream<Arguments> nullOperations() {
        return Stream.of(
                Arguments.of("computeIfAbsent(MethodHandle)", asBiConsumer((l, o) -> l.computeIfAbsent(0, (MethodHandle) o))),
                Arguments.of("computeIfAbsent(Supplier)", asBiConsumer((l, o) -> l.computeIfAbsent(0, (IntFunction<?>) o))),
                Arguments.of("putIfAbsent", asBiConsumer((l, o) -> l.putIfAbsent(0, o)))
                // Arguments.of("indexOf", asBiConsumer(List::indexOf))
        );
    }

    private static Consumer<Monotonic.List<Integer>> asConsumer(
            Consumer<Monotonic.List<Integer>> consumer) {
        return consumer;
    }

    private static BiConsumer<Monotonic.List<Object>, Object> asBiConsumer(
            BiConsumer<Monotonic.List<Object>, Object> biConsumer) {
        return biConsumer;
    }

    static final class CountingIntFunction<T> implements IntFunction<T> {

        private final AtomicInteger cnt = new AtomicInteger();
        private final IntFunction<T> delegate;

        public CountingIntFunction(IntFunction<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public T apply(int value) {
            cnt.incrementAndGet();
            return delegate.apply(value);
        }

        public int cnt() {
            return cnt.get();
        }

        @Override
        public String toString() {
            return cnt.toString();
        }
    }

    static String expectedToString(List<?> list) {
        return "[" + list.stream()
                .map(Objects::toString)
                .collect(Collectors.joining(", ")) + "]";
    }

}
