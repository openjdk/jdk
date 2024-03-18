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
 * @summary Basic tests for Monotonic.Map implementations
 * @compile --enable-preview -source ${jdk.version} BasicMonotonicMapTest.java
 * @run junit/othervm --enable-preview BasicMonotonicMapTest
 */

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class BasicMonotonicMapTest {

    private static final String[] STRINGS = "A,B,C,D,E,F,G".split(",");
    private static final int SIZE = STRINGS.length;
    private static final String KEY = "C";

    private Map<String, Monotonic<Integer>> map;

    @BeforeEach
    void setup() {
        map = Monotonic.ofMap(Arrays.asList(STRINGS));
    }

    @Test
    void empty() {
        assertFalse(map.isEmpty());
        assertEquals(SIZE, map.size());

        for (String key : STRINGS) {
            assertFalse(map.get(key).isPresent());
        }

        assertEquals(expectedToString(map), map.toString());
    }

    @ParameterizedTest
    @MethodSource("unsupportedOperations")
    void uoe(String name, Consumer<Map<String, Monotonic<Integer>>> op) {
        assertThrows(UnsupportedOperationException.class, () -> op.accept(map), name);
    }

    @ParameterizedTest
    @MethodSource("nullOperations")
    void npe(String name, Consumer<Map<String, Monotonic<Integer>>> op) {
        assertThrows(NullPointerException.class, () -> op.accept(map), name);

    }

    private static Stream<Arguments> unsupportedOperations() {
        return Stream.of(
                Arguments.of("clear",            asConsumer(Map::clear)),
                Arguments.of("put",              asConsumer(m -> m.put(KEY, Monotonic.of()))),
                Arguments.of("remove(K)",        asConsumer(m -> m.remove(KEY))),
                Arguments.of("remove(K, V)",     asConsumer(m -> m.remove(KEY, Monotonic.of()))),
                Arguments.of("putAll(K, V)",     asConsumer(m -> m.putAll(new HashMap<>()))),
                Arguments.of("replaceAll",       asConsumer(m -> m.replaceAll((_, _) -> null))),
                Arguments.of("putIfAbsent",      asConsumer(m -> m.putIfAbsent(KEY, Monotonic.of()))),
                Arguments.of("replace(K, V)",    asConsumer(m -> m.replace(KEY, Monotonic.of()))),
                Arguments.of("replace(K, V, V)", asConsumer(m -> m.replace(KEY, Monotonic.of(), Monotonic.of()))),
                Arguments.of("computeIfAbsent",  asConsumer(m -> m.computeIfAbsent(KEY, _ -> Monotonic.of()))),
                Arguments.of("computeIfPresent", asConsumer(m -> m.computeIfPresent(KEY, (_, _) -> Monotonic.of()))),
                Arguments.of("compute",          asConsumer(m -> m.compute(KEY, (_, _) -> Monotonic.of()))),
                Arguments.of("merge",            asConsumer(m -> m.merge(KEY, Monotonic.of(), (_, _) -> Monotonic.of())))
        );
    }

    private static Stream<Arguments> nullOperations() {
        return Stream.of(
                Arguments.of("forEach", asConsumer(m -> m.forEach(null)))
        );
    }

    private static Consumer<Map<String, Monotonic<Integer>>> asConsumer(Consumer<Map<String, Monotonic<Integer>>> consumer) {
        return consumer;
    }

    static final class CountingFunction<T, R> implements Function<T, R> {

        private final AtomicInteger cnt = new AtomicInteger();
        private final Function<T, R> delegate;

        public CountingFunction(Function<T, R> delegate) {
            this.delegate = delegate;
        }

        @Override
        public R apply(T value) {
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

    static String expectedToString(Map<String, Monotonic<Integer>> map) {
        return "{" + map.entrySet()
                .stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ")) + "}";
    }

}
