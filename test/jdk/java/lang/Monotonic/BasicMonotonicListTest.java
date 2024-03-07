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
import java.util.Comparator;
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
    void npe(String name, Consumer<List<Monotonic<Integer>>> op) {
        assertThrows(NullPointerException.class, () -> op.accept(list), name);
    }

    private static Stream<Arguments> unsupportedOperations() {
        return Stream.of(
                Arguments.of("add",          asConsumer(l -> l.add(Monotonic.of()))),
                Arguments.of("remove",       asConsumer(l -> l.remove(Monotonic.of()))),
                Arguments.of("addAll(C)",    asConsumer(l -> l.addAll(List.of()))),
                Arguments.of("addAll(i, C)", asConsumer(l -> l.addAll(1, List.of()))),
                Arguments.of("removeAll",    asConsumer(l -> l.removeAll(List.<Monotonic<Integer>>of()))),
                Arguments.of("retainAll",    asConsumer(l -> l.retainAll(List.<Monotonic<Integer>>of()))),
                Arguments.of("replaceAll",   asConsumer(l -> l.replaceAll(_ -> Monotonic.of()))),
                Arguments.of("sort",         asConsumer(l -> l.sort(null))),
                Arguments.of("clear",        asConsumer(List::clear)),
                Arguments.of("set(i, E)",    asConsumer(l -> l.set(1, Monotonic.of()))),
                Arguments.of("add(i, E)",    asConsumer(l -> l.add(1, Monotonic.of()))),
                Arguments.of("remove(i)",    asConsumer(l -> l.remove(1))),
                Arguments.of("removeIf",     asConsumer(l -> l.removeIf(Objects::isNull))),
                Arguments.of("addFirst",     asConsumer(l -> l.addFirst(Monotonic.of()))),
                Arguments.of("addLast",      asConsumer(l -> l.addLast(Monotonic.of()))),
                Arguments.of("removeFirst",  asConsumer(List::removeFirst)),
                Arguments.of("removeLast",   asConsumer(List::removeLast))
        );
    }

    private static Stream<Arguments> nullOperations() {
        return Stream.of(
                Arguments.of("toArray",     asConsumer(l -> l.toArray((Object[]) null))),
                Arguments.of("toArray",     asConsumer(l -> l.toArray((IntFunction<Monotonic<Integer>[]>) null))),
                Arguments.of("containsAll", asConsumer(l -> l.containsAll(null))),
                Arguments.of("forEach",     asConsumer(l -> l.forEach(null))),
                Arguments.of("indexOf",     asConsumer(l -> l.indexOf(null))),
                Arguments.of("lastIndexOf", asConsumer(l -> l.lastIndexOf(null)))
        );
    }

    private static Consumer<List<Monotonic<Integer>>> asConsumer(Consumer<List<Monotonic<Integer>>> consumer) {
        return consumer;
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
