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
 * @summary Basic tests for the Monotonics utility class
 * @compile --enable-preview -source ${jdk.version} BasicMonotonicsListTest.java
 * @run junit/othervm --enable-preview BasicMonotonicsListTest
 */

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class BasicMonotonicsMapTest {

    private static final String[] KEYS = "A,B,C,D,E,F,G".split(",");
    private static final String KEY = "C";
    private static final Function<String, Integer> FUNCTION = (String s) -> s.chars()
            .findFirst()
            .orElseThrow();
    private static final Integer EXPECTED = FUNCTION.apply(KEY);

    private Map<String, Monotonic<Integer>> map;

    @BeforeEach
    void setup() {
        map = Monotonic.ofMap(Arrays.asList(KEYS));
    }

    @Test
    void listComputeIfAbsent() {
        Integer v = Monotonics.computeIfAbsent(map, KEY, FUNCTION);
        assertEquals(EXPECTED, v);
        for (String key: KEYS) {
            Monotonic<Integer> m = map.get(key);
            if (key.equals(KEY)) {
                assertTrue(m.isPresent());
                assertEquals(EXPECTED, m.get());
            } else {
                assertFalse(m.isPresent());
            }
        }
    }

    @Test
    void listComputeIfAbsentNull() {
        Integer v = Monotonics.computeIfAbsent(map, KEY, i -> null);
        assertNull(v);
        for (String key: KEYS) {
            Monotonic<Integer> m = map.get(key);
            if (key.equals(KEY)) {
                assertTrue(m.isPresent());
                assertNull(m.get());
            } else {
                assertFalse(m.isPresent());
            }
        }
    }

    @Test
    void listComputeIfAbsentThrows() {
        assertThrows(UnsupportedOperationException.class, () ->
                Monotonics.computeIfAbsent(map, KEY, i -> {
                    throw new UnsupportedOperationException();
                })
        );
        for (String key: KEYS) {
            Monotonic<Integer> m = map.get(key);
            assertFalse(m.isPresent());
        }
    }

    @Test
    void asMemoized() {
        CountingFunction<String, Integer> function = new CountingFunction<>(FUNCTION);

        Function<String, Integer> memoized = Monotonics.asMemoized(Arrays.asList(KEYS), function);
        for (int j = 0; j < 2; j++) {
            for (String key:KEYS) {
                assertEquals(FUNCTION.apply(key), memoized.apply(key));
            }
        }
        assertEquals(KEYS.length, function.sum());
    }

    @ParameterizedTest
    @MethodSource("nullOperations")
    void npe(String name, Consumer<Map<String, Monotonic<Integer>>> op) {
        assertThrows(NullPointerException.class, () -> op.accept(map), name);
    }

    private static Stream<Arguments> nullOperations() {
        return Stream.of(
                Arguments.of("computeIfAbsent(M, K, null)",  asListConsumer(m -> Monotonics.computeIfAbsent(m, KEY, null))),
                Arguments.of("computeIfAbsent(M, null, M)",  asListConsumer(m -> Monotonics.computeIfAbsent(m, null, FUNCTION))),
                Arguments.of("computeIfAbsent(null, K, M)",  asListConsumer(m -> Monotonics.computeIfAbsent(null, KEY, FUNCTION))),
                Arguments.of("asMemoized(i, null, b)",       asListConsumer(m -> Monotonics.asMemoized(Arrays.asList(KEYS), null))),
                Arguments.of("asMemoized(null, M, b)",       asListConsumer(m -> Monotonics.asMemoized(null, FUNCTION)))
        );
    }

    private static Consumer<Map<String, Monotonic<Integer>>> asListConsumer(Consumer<Map<String, Monotonic<Integer>>> consumer) {
        return consumer;
    }

    private static final class CountingFunction<T, R> implements Function<T, R> {

        private final Function<T, R> delegate;
        private final Map<T, AtomicInteger> counters;


        public CountingFunction(Function<T, R> delegate) {
            this.delegate = delegate;
            this.counters = new ConcurrentHashMap<>();
        }

        @Override
        public R apply(T key) {
            counters.computeIfAbsent(key, _ -> new AtomicInteger()).incrementAndGet();
            return delegate.apply(key);
        }

        int cnt(T key) {
            return counters.get(key).get();
        }

        int sum() {
            return counters.values().stream()
                    .mapToInt(AtomicInteger::get)
                    .sum();
        }

    }

}
