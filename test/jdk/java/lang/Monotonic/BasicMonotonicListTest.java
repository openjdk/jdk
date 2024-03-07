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

import java.util.Collection;
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

    private Monotonic.List<Integer> list;

    @BeforeEach
    void setup() {
        list = Monotonic.ofList(SIZE);
    }

    @Test
    void empty() {

        assertFalse(list.isEmpty());
        assertEquals(SIZE, list.size());

        IntStream.range(0, SIZE).forEach(i ->
                assertFalse(list.get(i).isPresent())
        );

        assertEquals(expectedToString(list), list.toString());
    }

    @ParameterizedTest
    @MethodSource("unsupportedOperations")
    void uoe(String name, Consumer<List<Monotonic<Integer>>> op) {
        assertThrows(UnsupportedOperationException.class, () -> op.accept(list), name);
    }


    @ParameterizedTest
    @MethodSource("nullOperations")
    void npe(String name, BiConsumer<List<Monotonic<Integer>>, Object> op) {
        assertThrows(NullPointerException.class, () -> op.accept(null, null), name);

    }

    private static Stream<Arguments> unsupportedOperations() {
        return Stream.of(
                Arguments.of("clear", asConsumer(List::clear)),
                Arguments.of("removeIf", asConsumer(l -> l.removeIf(Objects::isNull))),
                Arguments.of("add", asConsumer(l -> l.add(Monotonic.of())))
                // Todo: add stuff
        );
    }

    private static Stream<Arguments> nullOperations() {
        return Stream.of(
                Arguments.of("toArray", asBiConsumer((l, o) -> l.toArray((Object[]) o))),
                Arguments.of("containsAll", asBiConsumer((l, o) -> l.containsAll((Collection<?>) o)))
        );
    }

    private static Consumer<List<Monotonic<Integer>>> asConsumer(Consumer<List<Monotonic<Integer>>> consumer) {
        return consumer;
    }

    private static BiConsumer<List<Monotonic<Integer>>, Object> asBiConsumer(
            BiConsumer<List<Monotonic<Integer>>, Object> biConsumer) {
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
