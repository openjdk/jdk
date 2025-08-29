/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests for StableMap methods
 * @modules java.base/jdk.internal.invoke.stable
 * @enablePreview
 * @run junit/othervm --add-opens java.base/java.util=ALL-UNNAMED StableMapTest
 */

import jdk.internal.invoke.stable.FunctionHolder;
import jdk.internal.invoke.stable.InternalStableValue;
import jdk.internal.invoke.stable.StableUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.lang.invoke.StableValue;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.*;

final class StableMapTest {

    enum Value {
        // Zero is here so that we have enums with ordinals before the first one
        // actually used in input sets (i.e. ZERO is not in the input set)
        ZERO(0),
        ILLEGAL_BEFORE(-1),
        // Valid values
        THIRTEEN(13) {
            @Override
            public String toString() {
                // getEnumConstants will be `null` for this enum as it is overridden
                return super.toString()+" (Overridden)";
            }
        },
        ILLEGAL_BETWEEN(-2),
        FORTY_TWO(42),
        // Illegal values (not in the input set)
        ILLEGAL_AFTER(-3);

        final int intValue;

        Value(int intValue) {
            this.intValue = intValue;
        }

        int asInt() {
            return intValue;
        }

    }

    private static final Function<Value, Integer> MAPPER = Value::asInt;

    private static final Value KEY = Value.FORTY_TWO;
    private static final Integer VALUE = MAPPER.apply(KEY);

    @ParameterizedTest
    @MethodSource("allSets")
    void factoryInvariants(Set<Value> set) {
        assertThrows(NullPointerException.class, () -> Map.ofLazy(set, null), set.getClass().getSimpleName());
        assertThrows(NullPointerException.class, () -> Map.ofLazy(null, MAPPER));
        Set<Value> setWithNull = new HashSet<>();
        setWithNull.add(KEY);
        setWithNull.add(null);
        assertThrows(NullPointerException.class, () -> Map.ofLazy(setWithNull, MAPPER));
    }

    @ParameterizedTest
    @MethodSource("emptySets")
    void empty(Set<Value> set) {
        var f0 = newMap(set);
        assertTrue(f0.isEmpty());
        assertEquals("{}", f0.toString());
        assertThrows(NullPointerException.class, () -> f0.get(null));
        assertNotEquals(null, f0);
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void size(Set<Value> set) {
        assertEquals(newRegularMap(set).size(), newMap(set).size());
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void get(Set<Value> set) {
        StableTestUtil.CountingFunction<Value, Integer> cf = new StableTestUtil.CountingFunction<>(MAPPER);
        var lazy = Map.ofLazy(set, cf);
        int cnt = 1;
        for (Value v : set) {
            assertEquals(MAPPER.apply(v), lazy.get(v));
            assertEquals(cnt, cf.cnt());
            assertEquals(MAPPER.apply(v), lazy.get(v));
            assertEquals(cnt++, cf.cnt());
        }
        assertNull(lazy.get(Value.ILLEGAL_BETWEEN));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void exception(Set<Value> set) {
        StableTestUtil.CountingFunction<Value, Integer> cif = new StableTestUtil.CountingFunction<>(_ -> {
            throw new UnsupportedOperationException();
        });
        var cached = Map.ofLazy(set, cif);
        assertThrows(UnsupportedOperationException.class, () -> cached.get(KEY));
        assertEquals(1, cif.cnt());
        assertThrows(UnsupportedOperationException.class, () -> cached.get(KEY));
        assertEquals(2, cif.cnt());
        var toString = cached.toString();
        assertTrue(toString.startsWith("{"));
        // Key order is unspecified
        assertTrue(toString.contains(Value.THIRTEEN + "=.unset"));
        assertTrue(toString.contains(Value.FORTY_TWO + "=.unset"));
        assertTrue(toString.endsWith("}"));
    }


    @ParameterizedTest
    @MethodSource("allSets")
    void containsKey(Set<Value> set) {
        var lazy = newMap(set);
        for (Value v : set) {
            assertTrue(lazy.containsKey(v));
        }
        assertFalse(lazy.containsKey(Value.ILLEGAL_BETWEEN));
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void containsValue(Set<Value> set) {
        var lazy = newMap(set);
        for (Value v : set) {
            assertTrue(lazy.containsValue(MAPPER.apply(v)));
        }
        assertFalse(lazy.containsValue(MAPPER.apply(Value.ILLEGAL_BETWEEN)));
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void forEach(Set<Value> set) {
        var lazy = newMap(set);
        var ref = newRegularMap(set);
        Set<Map.Entry<Value, Integer>> expected = ref.entrySet();
        Set<Map.Entry<Value, Integer>> actual = new HashSet<>();
        lazy.forEach((k, v) -> actual.add(new AbstractMap.SimpleImmutableEntry<>(k , v)));
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("emptySets")
    void toStringTestEmpty(Set<Value> set) {
        var cached = newMap(set);
        assertEquals("{}", cached.toString());
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void toStringTest(Set<Value> set) {
        var cached = newMap(set);
        var toString = cached.toString();
        assertTrue(toString.startsWith("{"));
        assertTrue(toString.endsWith("}"));

        // Key order is unspecified
        for (Value key : set) {
            toString = cached.toString();
            assertTrue(toString.contains(key + "=.unset"), toString + " did not contain " + key + "=.unset");
            cached.get(key);
            toString = cached.toString();
            assertTrue(toString.contains(key + "=" + MAPPER.apply(key)), toString);
        }

        // One between the values
        assertEquals(set.size() - 1, toString.chars().filter(ch -> ch == ',').count());
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void hashCodeTest(Set<Value> set) {
        var lazy = newMap(set);
        var regular = newRegularMap(set);
        assertEquals(regular.hashCode(), lazy.hashCode());
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void equality(Set<Value> set) {
        var lazy = newMap(set);
        var regular = newRegularMap(set);
        assertEquals(regular, lazy);
        assertEquals(lazy, regular);
        assertNotEquals("A", lazy);
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void circular(Set<Value> set) {
        final AtomicReference<Map<?, ?>> ref = new AtomicReference<>();
        Map<Value, Map<?, ?>> cached = Map.ofLazy(set, _ -> ref.get());
        ref.set(cached);
        cached.get(KEY);
        var toString = cached.toString();
        assertTrue(toString.contains("FORTY_TWO=(this Map)"), toString);

        // Todo:: Investigate how this should be handled
        // assertDoesNotThrow(cached::hashCode);

        assertDoesNotThrow((() -> cached.equals(cached)));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void entrySet(Set<Value> set) {
        var regular = newRegularMap(set).entrySet();
        var actual = newMap(set).entrySet();
        assertTrue(regular.equals(actual));
        assertTrue(actual.equals(regular));
        assertTrue(regular.equals(actual));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void entrySetToString(Set<Value> set) {
        var map = newMap(set);
        var entrySet = map.entrySet();
        var toString = entrySet.toString();
        for (var key : set) {
            assertTrue(toString.contains(key + "=.unset"));
        }
        assertTrue(toString.startsWith("["));
        assertTrue(toString.endsWith("]"));

        map.get(KEY);
        for (var key : set) {
            if (key.equals(KEY)) {
                continue;
            }
            assertTrue(entrySet.toString().contains(key + "=.unset"));
        }
        assertTrue(entrySet.toString().contains(KEY + "=" + VALUE));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void values(Set<Value> set) {
        var map = newMap(set);
        var values = map.values();
        // Look at one of the elements
        var val = values.stream().iterator().next();
        var toString = map.toString();
        for (var key : set) {
            var v = MAPPER.apply(key);
            if (v.equals(val)) {
                assertTrue(toString.contains(key + "=" + v));
            } else {
                assertTrue(toString.contains(key + "=.unset"));
            }
        }

        // Mod ops
        assertThrows(UnsupportedOperationException.class, () -> values.remove(val));
        assertThrows(UnsupportedOperationException.class, () -> values.add(val));
        assertThrows(UnsupportedOperationException.class, values::clear);
        assertThrows(UnsupportedOperationException.class, () -> values.addAll(Set.of(VALUE)));
        assertThrows(UnsupportedOperationException.class, () -> values.removeIf(i -> true));
        assertThrows(UnsupportedOperationException.class, () -> values.retainAll(Set.of(VALUE)));
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void valuesToString(Set<Value> set) {
        var map = newMap(set);
        var values = map.values();
        var expected = set.stream()
                .map(_ -> ".unset")
                .collect(joining(", ", "[", "]"));
        //var expected = "[" + ".unset, ".repeat(set.size() - 1) + ".unset]";
        assertEquals(expected, values.toString());
        map.get(KEY);
        var afterGet = values.toString();
        assertEquals(set.contains(KEY), afterGet.contains("" + VALUE), afterGet);
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void iteratorNext(Set<Value> set) {
        Set<Value> encountered = new HashSet<>();
        var iterator = newMap(set).entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            assertEquals(MAPPER.apply(entry.getKey()), entry.getValue());
            encountered.add(entry.getKey());
        }
        assertEquals(set, encountered);
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void iteratorForEachRemaining(Set<Value> set) {
        Set<Value> encountered = new HashSet<>();
        var iterator = newMap(set).entrySet().iterator();
        var entry = iterator.next();
        assertEquals(MAPPER.apply(entry.getKey()), entry.getValue());
        encountered.add(entry.getKey());
        iterator.forEachRemaining(e -> {
            assertEquals(MAPPER.apply(e.getKey()), e.getValue());
            encountered.add(e.getKey());
        });
        assertEquals(set, encountered);
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void lazyEntry(Set<Value> set) {
        var map = newMap(set);
        var entry = map.entrySet().stream()
                .filter(e -> e.getKey().equals(KEY))
                .findAny()
                .orElseThrow();

        assertEquals(KEY + "=.unset", entry.toString());
        var otherDifferent = Map.entry(Value.ZERO, -1);
        assertNotEquals(entry, otherDifferent);
        assertEquals(KEY + "=.unset", entry.toString());
        var otherEqual = Map.entry(entry.getKey(), entry.getValue());
        assertEquals(entry, otherEqual);
        assertEquals(KEY + "=" + VALUE, entry.toString());
        assertEquals(entry.hashCode(), otherEqual.hashCode());
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void lazyForEachEntry(Set<Value> set) {
        var map = newMap(set);
        // Only touch the key.
        map.entrySet().iterator().forEachRemaining(Map.Entry::getKey);
        map.entrySet().iterator()
                .forEachRemaining(e -> assertTrue(e.toString().contains(".unset")));
        // Only touch the value.
        map.entrySet().iterator().forEachRemaining(Map.Entry::getValue);
        map.entrySet().iterator()
                .forEachRemaining(e -> assertFalse(e.toString().contains(".unset")));
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
        for (Set<Value> set : allSets().toList()) {
            var lazy = newMap(set);
            assertThrows(expectedType, () -> operation.accept(lazy), set.getClass().getSimpleName() + " " + operation);
        }
    }

    // Implementing interfaces

    @ParameterizedTest
    @MethodSource("allSets")
    void serializable(Set<Value> set) {
        var map = newMap(set);
        assertFalse(map instanceof Serializable);
        assertFalse(map.entrySet() instanceof Serializable);
        assertFalse(map.keySet() instanceof Serializable);
        assertFalse(map.values() instanceof Serializable);
    }

    @Test
    void distinct() {
        Map<Integer, InternalStableValue<Integer>> map = StableUtil.map(Set.of(1, 2, 3));
        assertEquals(3, map.size());
        // Check, every StableValue is distinct
        Map<StableValue<Integer>, Boolean> idMap = new IdentityHashMap<>();
        map.forEach((k, v) -> idMap.put(v, true));
        assertEquals(3, idMap.size());
    }

    @Test
    void nullResult() {
        var map = Map.ofLazy(Set.of(0), _ -> null);
        assertThrows(NullPointerException.class, () -> map.getOrDefault(0, 1));;
        assertTrue(map.containsKey(0));
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void functionHolder(Set<Value> set) {
        StableTestUtil.CountingFunction<Value, Integer> cif = new StableTestUtil.CountingFunction<>(MAPPER);
        Map<Value, Integer> f1 = Map.ofLazy(set, cif);

        FunctionHolder<?> holder = StableTestUtil.functionHolder(f1);

        int i = 0;
        for (Value key : set) {
            assertEquals(set.size() - i, holder.counter());
            assertSame(cif, holder.function());
            int v = f1.get(key);
            int v2 = f1.get(key);
            i++;
        }
        assertEquals(0, holder.counter());
        assertNull(holder.function());
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void functionHolderViaEntrySet(Set<Value> set) {
        StableTestUtil.CountingFunction<Value, Integer> cif = new StableTestUtil.CountingFunction<>(MAPPER);
        Map<Value, Integer> f1 = Map.ofLazy(set, cif);

        FunctionHolder<?> holder = StableTestUtil.functionHolder(f1);

        int i = 0;
        for (Map.Entry<Value, Integer> e : f1.entrySet()) {
            assertEquals(set.size() - i, holder.counter());
            assertSame(cif, holder.function());
            int v = e.getValue();
            int v2 = e.getValue();
            i++;
        }
        assertEquals(0, holder.counter());
        assertNull(holder.function());
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void underlyingRefViaEntrySetForEach(Set<Value> set) {
        StableTestUtil.CountingFunction<Value, Integer> cif = new StableTestUtil.CountingFunction<>(MAPPER);
        Map<Value, Integer> f1 = Map.ofLazy(set, cif);

        FunctionHolder<?> holder = StableTestUtil.functionHolder(f1);

        final AtomicInteger i = new AtomicInteger();
        f1.entrySet().forEach(e -> {
            assertEquals(set.size() - i.get(), holder.counter());
            assertSame(cif, holder.function());
            Integer val = e.getValue();
            Integer val2 = e.getValue();
            i.incrementAndGet();
        });
        assertEquals(0, holder.counter());
        assertNull(holder.function());
    }

    @Test
    void usesOptimizedVersion() {
        Map<Value, Integer> enumMap = Map.ofLazy(EnumSet.of(KEY), Value::asInt);
        assertTrue(enumMap.getClass().getName().contains("Enum"), enumMap.getClass().getName());
        Map<Value, Integer> emptyMap = Map.ofLazy(EnumSet.noneOf(Value.class), Value::asInt);
        assertFalse(emptyMap.getClass().getName().contains("Enum"), emptyMap.getClass().getName());
        Map<Value, Integer> regularMap = Map.ofLazy(Set.of(KEY), Value::asInt);
        assertFalse(regularMap.getClass().getName().contains("Enum"), regularMap.getClass().getName());
    }

    @Test
    void overriddenEnum() {
        final var overridden = Value.THIRTEEN;
        Map<Value, Integer> enumMap = Map.ofLazy(EnumSet.of(overridden), MAPPER);
        assertEquals(MAPPER.apply(overridden), enumMap.get(overridden), enumMap.toString());
    }

    // Support constructs

    record Operation(String name,
                     Consumer<Map<Value, Integer>> consumer) implements Consumer<Map<Value, Integer>> {
        @java.lang.Override
        public void accept(Map<Value, Integer> map) { consumer.accept(map); }
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
            new Operation("merge",             m -> m.merge(KEY, VALUE, (a, _) -> a)),
            new Operation("put",               m -> m.put(KEY, 0)),
            new Operation("putAll",            m -> m.putAll(Map.of())),
            new Operation("remove1",           m -> m.remove(KEY)),
            new Operation("remove2",           m -> m.remove(KEY, VALUE)),
            new Operation("replace2",          m -> m.replace(KEY, 1)),
            new Operation("replace3",          m -> m.replace(KEY, VALUE, 1)),
            new Operation("replaceAll",        m -> m.replaceAll((a, _) -> MAPPER.apply(a)))
        );
    }


    static Map<Value, Integer> newMap(Set<Value> set) {
        return Map.ofLazy(set, MAPPER);
    }
    static Map<Value, Integer> newRegularMap(Set<Value> set) {
        return set.stream()
                .collect(Collectors.toMap(Function.identity(), MAPPER));
    }

    private static Stream<Set<Value>> nonEmptySets() {
        return Stream.of(
                Set.of(KEY, Value.THIRTEEN),
                linkedHashSet(Value.THIRTEEN, KEY),
                treeSet(KEY, Value.THIRTEEN),
                EnumSet.of(KEY, Value.THIRTEEN)
        );
    }

    private static Stream<Set<Value>> emptySets() {
        return Stream.of(
                Set.of(),
                linkedHashSet(),
                treeSet(),
                EnumSet.noneOf(Value.class)
        );
    }

    private static Stream<Set<Value>> allSets() {
        return Stream.concat(
                nonEmptySets(),
                emptySets()
        );
    }

    static Set<Value> treeSet(Value... values) {
        return populate(new TreeSet<>(Comparator.comparingInt(Value::asInt).reversed()),values);
    }

    static Set<Value> linkedHashSet(Value... values) {
        return populate(new LinkedHashSet<>(), values);
    }

    static Set<Value> populate(Set<Value> set, Value... values) {
        set.addAll(Arrays.asList(values));
        return set;
    }

}
