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
 * @summary Basic tests for Lazy Enum Map implementations
 * @compile --enable-preview -source ${jdk.version} BasicLazyEnumMapTest.java
 * @run junit/othervm --enable-preview BasicLazyEnumMapTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class BasicLazyEnumMapTest {

    enum TestEnum {
        A, B, C, D, E, F, G;
    }

    private static final TestEnum KEY = TestEnum.C;

    @Test
    void basic() {
        for (var set : sets()) {
            Map<TestEnum, Lazy<Integer>> map = Lazy.ofMap(set);
            assertEquals(set.isEmpty(), map.isEmpty());

            for (TestEnum key : set) {
                assertFalse(
                        map.get(key)
                        .isSet());
            }

            for (TestEnum key : set) {
                assertNotNull(map.get(key));
                assertTrue(map.containsKey(key));
            }

            assertEquals(expectedToString(map), map.toString());
        }
    }

    @Test
    void entrySet() {
        for (var set : sets()) {
            Map<TestEnum, Lazy<Integer>> map = Lazy.ofMap(set);
            var es = map.entrySet();
            assertEquals(set.size(), es.size());
        }
    }

    @Test
    void entrySetIterator() {
        for (var set : sets()) {
            Map<TestEnum, Lazy<Integer>> map = Lazy.ofMap(set);
            var i = map.entrySet().iterator();

            Set<TestEnum> seen = new HashSet<>();
            while (i.hasNext()) {
                seen.add(i.next().getKey());
            }
            assertEquals(set, seen);
        }
    }

    @ParameterizedTest
    @MethodSource("unsupportedOperations")
    void uoe(String name, Consumer<Map<TestEnum, Lazy<Integer>>> op) {
        for (var map:maps()) {
            assertThrows(UnsupportedOperationException.class, () -> op.accept(map), name);
        }
    }

    @ParameterizedTest
    @MethodSource("nullOperations")
    void npe(String name, Consumer<Map<TestEnum, Lazy<Integer>>> op) {
        for (var map:maps()) {
            assertThrows(NullPointerException.class, () -> op.accept(map), name);
        }
    }

    private static List<Set<TestEnum>> sets() {
        return IntStream.range(0, TestEnum.values().length)
                .mapToObj(i -> Arrays.copyOfRange(TestEnum.values(), 0, i))
                .map(a -> {
                    Set<TestEnum> s = EnumSet.noneOf(TestEnum.class);
                    s.addAll(List.of(a));
                    return s;
                })
                .toList();
    }

    private static List<Map<TestEnum, Lazy<Integer>>> maps() {
        return sets().stream()
                .map(Lazy::<TestEnum, Integer>ofMap)
                .toList();
    }

    private static Stream<Arguments> unsupportedOperations() {
        return Stream.of(
                Arguments.of("clear",            asConsumer(Map::clear)),
                Arguments.of("put",              asConsumer(m -> m.put(KEY, Lazy.of()))),
                Arguments.of("remove(K)",        asConsumer(m -> m.remove(KEY))),
                Arguments.of("remove(K, V)",     asConsumer(m -> m.remove(KEY, Lazy.of()))),
                Arguments.of("putAll(K, V)",     asConsumer(m -> m.putAll(new HashMap<>()))),
                Arguments.of("replaceAll",       asConsumer(m -> m.replaceAll((_, _) -> null))),
                Arguments.of("putIfAbsent",      asConsumer(m -> m.putIfAbsent(KEY, Lazy.of()))),
                Arguments.of("replace(K, V)",    asConsumer(m -> m.replace(KEY, Lazy.of()))),
                Arguments.of("replace(K, V, V)", asConsumer(m -> m.replace(KEY, Lazy.of(), Lazy.of()))),
                Arguments.of("computeIfAbsent",  asConsumer(m -> m.computeIfAbsent(KEY, _ -> Lazy.of()))),
                Arguments.of("computeIfPresent", asConsumer(m -> m.computeIfPresent(KEY, (_, _) -> Lazy.of()))),
                Arguments.of("compute",          asConsumer(m -> m.compute(KEY, (_, _) -> Lazy.of()))),
                Arguments.of("merge",            asConsumer(m -> m.merge(KEY, Lazy.of(), (_, _) -> Lazy.of()))),
                Arguments.of("es().it().remove", asConsumer(m -> m.entrySet().iterator().remove()))
        );
    }

    private static Stream<Arguments> nullOperations() {
        return Stream.of(
                Arguments.of("forEach", asConsumer(m -> m.forEach(null)))
        );
    }

    private static Consumer<Map<TestEnum, Lazy<Integer>>> asConsumer(Consumer<Map<TestEnum, Lazy<Integer>>> consumer) {
        return consumer;
    }

    static String expectedToString(Map<TestEnum, Lazy<Integer>> map) {
        return "{" + map.entrySet()
                .stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ")) + "}";
    }

}
