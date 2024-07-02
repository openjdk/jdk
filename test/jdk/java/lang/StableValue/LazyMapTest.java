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
 * @summary Basic tests for LazyMap methods
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} LazyMapTest.java
 * @run junit/othervm --enable-preview LazyMapTest
 */

import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class LazyMapTest {

    private static final int NOT_PRESENT = 147;
    private static final int KEY = 7;
    private static final Set<Integer> KEYS = Set.of(0, KEY, 13);
    private static final Set<Integer> EMPTY = Set.of();
    private static final Function<Integer, Integer> IDENTITY = Function.identity();

    @Test
    void factoryInvariants() {
        assertThrows(NullPointerException.class, () -> StableValue.lazyMap(KEYS, null));
        assertThrows(NullPointerException.class, () -> StableValue.lazyMap(null, IDENTITY));
    }

    @Test
    void isEmpty() {
        assertFalse(newMap().isEmpty());
        assertTrue(newEmptyMap().isEmpty());
    }

    @Test
    void size() {
        assertEquals(KEYS.size(), newMap().size());
        assertEquals(EMPTY.size(), newEmptyMap().size());
    }

    @Test
    void get() {
        StableTestUtil.CountingFunction<Integer, Integer> cf = new StableTestUtil.CountingFunction<>(IDENTITY);
        var lazy = StableValue.lazyMap(KEYS, cf);
        int cnt = 1;
        for (int i : KEYS) {
            assertEquals(i, lazy.get(i));
            assertEquals(cnt, cf.cnt());
            assertEquals(i, lazy.get(i));
            assertEquals(cnt++, cf.cnt());
        }
        assertNull(lazy.get(NOT_PRESENT));
    }

    @Test
    void getException() {
        StableTestUtil.CountingFunction<Integer, Integer> cf = new StableTestUtil.CountingFunction<>(_ -> {
            throw new UnsupportedOperationException();
        });
        var lazy = StableValue.lazyMap(KEYS, cf);
        assertThrows(UnsupportedOperationException.class, () -> lazy.get(KEY));
        assertEquals(1, cf.cnt());
        assertThrows(UnsupportedOperationException.class, () -> lazy.get(KEY));
        assertEquals(2, cf.cnt());
    }

    @Test
    void containsKey() {
        var lazy = newMap();
        for (int i : KEYS) {
            assertTrue(lazy.containsKey(i));
        }
        assertFalse(lazy.containsKey(NOT_PRESENT));
    }

    @Test
    void containsValue() {
        var lazy = newMap();
        for (int i : KEYS) {
            assertTrue(lazy.containsValue(i));
        }
        assertFalse(lazy.containsValue(NOT_PRESENT));
    }

    @Test
    void forEach() {
        var lazy = newMap();
        Set<Map.Entry<Integer, Integer>> expected = KEYS.stream()
                .map(i -> new AbstractMap.SimpleImmutableEntry<>(i , i))
                .collect(Collectors.toSet());
        Set<Map.Entry<Integer, Integer>> actual = new HashSet<>();
        lazy.forEach((k, v) -> actual.add(new AbstractMap.SimpleImmutableEntry<>(k , v)));
        assertEquals(expected, actual);
    }

    @Test
    void toStringTest() {
        assertEquals("{}", newEmptyMap().toString());
        assertEquals("{" + KEY + "=" + KEY + "}", StableValue.lazyMap(Set.of(KEY), IDENTITY).toString());
        String actual = newMap().toString();
        assertTrue(actual.startsWith("{"));
        for (int key:KEYS) {
            assertTrue(actual.contains(key + "=" + key));
        }
        assertTrue(actual.endsWith("}"));
    }

    @Test
    void hashCodeTest() {
        assertEquals(Map.of().hashCode(), newEmptyMap().hashCode());
        assertEquals(newRegularMap().hashCode(), newMap().hashCode());
    }

    @Test
    void equalsTest() {
        assertTrue(newEmptyMap().equals(Map.of()));
        assertTrue(Map.of().equals(newEmptyMap()));
        assertTrue(newMap().equals(newRegularMap()));
        assertTrue(newRegularMap().equals(newMap()));
        assertFalse(newMap().equals("A"));
    }

    @Test
    void entrySet() {
        var regular = newRegularMap().entrySet();
        var actual = newMap().entrySet();
        assertTrue(regular.equals(actual));
        assertTrue(actual.equals(regular));
        assertTrue(regular.equals(actual));
    }

    @Test
    void iterator() {
        System.out.println("ITERATOR");
        var iterator = newMap().entrySet().iterator();
        while (iterator.hasNext()) {
            System.out.println("iterator.next() = " + iterator.next());
        }
    }

    // Immutability
    @ParameterizedTest
    @MethodSource("unsupportedOperations")
    void unsupported(Operation operation) {
        assertThrowsForOperation(UnsupportedOperationException.class, operation);
    }

    // Method parameter invariant checking

    @ParameterizedTest
    @MethodSource("nullAverseOperations")
    void nullAverse(Operation operation) {
        assertThrowsForOperation(NullPointerException.class, operation);
    }

    static <T extends Throwable> void assertThrowsForOperation(Class<T> expectedType, Operation operation) {
        var lazy = newMap();
        assertThrows(expectedType, () -> operation.accept(lazy));
    }

    // Implementing interfaces

    @Test
    void serializable() {
        assertFalse(newMap() instanceof Serializable);
        assertFalse(newEmptyMap() instanceof Serializable);
    }

    // Support constructs

    record Operation(String name,
                     Consumer<Map<Integer, Integer>> consumer) implements Consumer<Map<Integer, Integer>> {
        @java.lang.Override
        public void   accept(Map<Integer, Integer> map) { consumer.accept(map); }
        @java.lang.Override
        public String toString() { return name; }
    }

    static Stream<Operation> nullAverseOperations() {
        return Stream.of(
                new Operation("forEach",     m -> m.forEach(null))
        );
    }

    static Stream<Operation> unsupportedOperations() {
        return Stream.of(
                new Operation("clear",             Map::clear),
                new Operation("compute",           m -> m.compute(KEY, (_, _) -> 1)),
                new Operation("computeIfAbsent",   m -> m.computeIfAbsent(KEY, _ -> 1)),
                new Operation("computeIfPresent",  m -> m.computeIfPresent(KEY, (_, _) -> 1)),
                new Operation("merge",             m -> m.merge(KEY, KEY, (a, _) -> a)),
                new Operation("put",               m -> m.put(0, 0)),
                new Operation("putAll",            m -> m.putAll(Map.of())),
                new Operation("remove1",           m -> m.remove(KEY)),
                new Operation("remove2",           m -> m.remove(KEY, KEY)),
                new Operation("replace2",          m -> m.replace(KEY, 1)),
                new Operation("replace3",          m -> m.replace(KEY, KEY, 1)),
                new Operation("replaceAll",        m -> m.replaceAll((a, _) -> a))
        );
    }

    static Map<Integer, Integer> newMap() {
        return StableValue.lazyMap(KEYS, IDENTITY);
    }

    static Map<Integer, Integer> newEmptyMap() {
        return StableValue.lazyMap(EMPTY, IDENTITY);
    }

    static Map<Integer, Integer> newRegularMap() {
        return KEYS.stream().collect(Collectors.toMap(IDENTITY, IDENTITY));
    }

}
