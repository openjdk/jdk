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
 * @summary Basic tests for lazy map methods
 * @enablePreview
 * @modules java.base/java.util:+open
 * @run junit LazyMapTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.util.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static org.junit.jupiter.api.Assertions.*;

final class LazyMapTest {

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
        var lazy = newLazyMap(set);
        assertTrue(lazy.isEmpty());
        assertEquals("{}", lazy.toString());
        assertThrows(NullPointerException.class, () -> lazy.get(null));
        assertNotEquals(null, lazy);
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void size(Set<Value> set) {
        assertEquals(newRegularMap(set).size(), newLazyMap(set).size());
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void get(Set<Value> set) {
        LazyConstantTestUtil.CountingFunction<Value, Integer> cf = new LazyConstantTestUtil.CountingFunction<>(MAPPER);
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
        LazyConstantTestUtil.CountingFunction<Value, Integer> cif = new LazyConstantTestUtil.CountingFunction<>(_ -> {
            throw new UnsupportedOperationException();
        });
        var lazy = Map.ofLazy(set, cif);
        assertThrows(UnsupportedOperationException.class, () -> lazy.get(KEY));
        assertEquals(1, cif.cnt());
        assertThrows(UnsupportedOperationException.class, () -> lazy.get(KEY));
        assertEquals(2, cif.cnt());
        assertThrows(UnsupportedOperationException.class, lazy::toString);
        assertEquals(3, cif.cnt());
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void containsKey(Set<Value> set) {
        var lazy = newLazyMap(set);
        for (Value v : set) {
            assertTrue(lazy.containsKey(v));
        }
        assertFalse(lazy.containsKey(Value.ILLEGAL_BETWEEN));
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void containsValue(Set<Value> set) {
        var lazy = newLazyMap(set);
        for (Value v : set) {
            assertTrue(lazy.containsValue(MAPPER.apply(v)));
        }
        assertFalse(lazy.containsValue(MAPPER.apply(Value.ILLEGAL_BETWEEN)));
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void forEach(Set<Value> set) {
        var lazy = newLazyMap(set);
        var ref = newRegularMap(set);
        Set<Map.Entry<Value, Integer>> expected = ref.entrySet();
        Set<Map.Entry<Value, Integer>> actual = new HashSet<>();
        lazy.forEach((k, v) -> actual.add(new AbstractMap.SimpleImmutableEntry<>(k , v)));
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("emptySets")
    void toStringTestEmpty(Set<Value> set) {
        var lazy = newLazyMap(set);
        assertEquals("{}", lazy.toString());
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void toStringTest(Set<Value> set) {
        var lazy = newLazyMap(set);
        var toString = lazy.toString();
        assertTrue(toString.startsWith("{"));
        assertTrue(toString.endsWith("}"));

        // Key order is unspecified
        for (Value key : set) {
            toString = lazy.toString();
            assertTrue(toString.contains(key + "=" + MAPPER.apply(key)), toString);
        }

        // One between the values
        assertEquals(set.size() - 1, toString.chars().filter(ch -> ch == ',').count());
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void hashCodeTest(Set<Value> set) {
        var lazy = newLazyMap(set);
        var regular = newRegularMap(set);
        assertEquals(regular.hashCode(), lazy.hashCode());
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void equality(Set<Value> set) {
        var lazy = newLazyMap(set);
        var regular = newRegularMap(set);
        assertEquals(regular, lazy);
        assertEquals(lazy, regular);
        assertNotEquals("A", lazy);
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void circular(Set<Value> set) {
        final AtomicReference<Map<?, ?>> ref = new AtomicReference<>();
        Map<Value, Map<?, ?>> lazy = Map.ofLazy(set, _ -> ref.get());
        ref.set(lazy);
        lazy.get(KEY);
        var toString = lazy.toString();
        assertTrue(toString.contains("FORTY_TWO=(this Map)"), toString);
        assertDoesNotThrow((() -> lazy.equals(lazy)));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void recursiveCall(Set<Value> set) {
        final AtomicReference<Map<Value, ?>> ref = new AtomicReference<>();
        @SuppressWarnings("unchecked")
        Map<Value, Map<Value, Object>> lazy = Map.ofLazy(set, k -> (Map<Value, Object>) ref.get().get(k));
        ref.set(lazy);
        var x = assertThrows(IllegalStateException.class, () -> lazy.get(KEY));
        assertEquals("Recursive initialization of a lazy collection is illegal", x.getMessage());
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void entrySet(Set<Value> set) {
        var lazy = newLazyMap(set).entrySet();
        var regular = newRegularMap(set).entrySet();
        assertTrue(regular.equals(lazy));
        assertTrue(lazy.equals(regular));
        assertTrue(regular.equals(lazy));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void entrySetToString(Set<Value> set) {
        var lazy = newLazyMap(set);
        var lazyEntrySet = lazy.entrySet();
        var toString = lazyEntrySet.toString();
        for (var key : set) {
            assertTrue(toString.contains(key + "=" + MAPPER.apply(key)));
        }
        assertTrue(toString.startsWith("["));
        assertTrue(toString.endsWith("]"));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void values(Set<Value> set) {
        var lazy = newLazyMap(set);
        var lazyValues = lazy.values();
        // Look at one of the elements
        var val = lazyValues.stream().iterator().next();
        assertEquals(lazy.size() - 1, functionCounter(lazy));

        // Mod ops
        assertThrows(UnsupportedOperationException.class, () -> lazyValues.remove(val));
        assertThrows(UnsupportedOperationException.class, () -> lazyValues.add(val));
        assertThrows(UnsupportedOperationException.class, lazyValues::clear);
        assertThrows(UnsupportedOperationException.class, () -> lazyValues.addAll(Set.of(VALUE)));
        assertThrows(UnsupportedOperationException.class, () -> lazyValues.removeIf(i -> true));
        assertThrows(UnsupportedOperationException.class, () -> lazyValues.retainAll(Set.of(VALUE)));
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void valuesToString(Set<Value> set) {
        var lazy = newLazyMap(set);
        var lazyValues = lazy.values();
        var toString = lazyValues.toString();

        // Key order is unspecified
        for (Value key : set) {
            assertTrue(toString.contains(MAPPER.apply(key).toString()), toString);
        }
        assertTrue(toString.startsWith("["), toString);
        assertTrue(toString.endsWith("]"), toString);
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void iteratorNext(Set<Value> set) {
        Set<Value> encountered = new HashSet<>();
        var iterator = newLazyMap(set).entrySet().iterator();
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
        var iterator = newLazyMap(set).entrySet().iterator();
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
        var lazy = newLazyMap(set);
        var entry = lazy.entrySet().stream()
                .filter(e -> e.getKey().equals(KEY))
                .findAny()
                .orElseThrow();

        assertEquals(lazy.size(), functionCounter(lazy));
        var otherDifferent = Map.entry(Value.ZERO, -1);
        assertNotEquals(entry, otherDifferent);
        assertEquals(lazy.size(), functionCounter(lazy));
        var otherEqual = Map.entry(entry.getKey(), entry.getValue());
        assertEquals(entry, otherEqual);
        assertEquals(lazy.size() - 1, functionCounter(lazy));
        assertEquals(entry.hashCode(), otherEqual.hashCode());
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void lazyForEachEntry(Set<Value> set) {
        var lazy = newLazyMap(set);
        // Only touch the key.
        lazy.entrySet().iterator().forEachRemaining(Map.Entry::getKey);
        assertEquals(lazy.size(), functionCounter(lazy)); // No evaluation
        // Only touch the value.
        lazy.entrySet().iterator().forEachRemaining(Map.Entry::getValue);
        assertEquals(0, functionCounter(lazy));
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
            var lazy = newLazyMap(set);
            assertThrows(expectedType, () -> operation.accept(lazy), set.getClass().getSimpleName() + " " + operation);
        }
    }

    // Implementing interfaces

    @ParameterizedTest
    @MethodSource("allSets")
    void serializable(Set<Value> set) {
        var lazy = newLazyMap(set);
        assertFalse(lazy instanceof Serializable);
        assertFalse(lazy.entrySet() instanceof Serializable);
        assertFalse(lazy.values() instanceof Serializable);
    }

    @Test
    void nullResult() {
        var lazy = Map.ofLazy(Set.of(0), _ -> null);
        assertThrows(NullPointerException.class, () -> lazy.getOrDefault(0, 1));;
        assertTrue(lazy.containsKey(0));
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void functionHolder(Set<Value> set) {
        LazyConstantTestUtil.CountingFunction<Value, Integer> cif = new LazyConstantTestUtil.CountingFunction<>(MAPPER);
        Map<Value, Integer> lazy = Map.ofLazy(set, cif);

        Object holder = LazyConstantTestUtil.functionHolder(lazy);

        int i = 0;
        for (Value key : set) {
            assertEquals(set.size() - i, LazyConstantTestUtil.functionHolderCounter(holder));
            assertSame(cif, LazyConstantTestUtil.functionHolderFunction(holder));
            int v = lazy.get(key);
            int v2 = lazy.get(key);
            i++;
        }
        assertEquals(0, LazyConstantTestUtil.functionHolderCounter(holder));
        assertNull(LazyConstantTestUtil.functionHolderFunction(holder));
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void functionHolderViaEntrySet(Set<Value> set) {
        LazyConstantTestUtil.CountingFunction<Value, Integer> cif = new LazyConstantTestUtil.CountingFunction<>(MAPPER);
        Map<Value, Integer> lazy = Map.ofLazy(set, cif);

        Object holder = LazyConstantTestUtil.functionHolder(lazy);

        int i = 0;
        for (Map.Entry<Value, Integer> e : lazy.entrySet()) {
            assertEquals(set.size() - i, LazyConstantTestUtil.functionHolderCounter(holder));
            assertSame(cif, LazyConstantTestUtil.functionHolderFunction(holder));
            int v = e.getValue();
            int v2 = e.getValue();
            i++;
        }
        assertEquals(0, LazyConstantTestUtil.functionHolderCounter(holder));
        assertNull(LazyConstantTestUtil.functionHolderFunction(holder));
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void underlyingRefViaEntrySetForEach(Set<Value> set) {
        LazyConstantTestUtil.CountingFunction<Value, Integer> cif = new LazyConstantTestUtil.CountingFunction<>(MAPPER);
        Map<Value, Integer> lazy = Map.ofLazy(set, cif);

        Object holder = LazyConstantTestUtil.functionHolder(lazy);

        final AtomicInteger i = new AtomicInteger();
        lazy.entrySet().forEach(e -> {
            assertEquals(set.size() - i.get(), LazyConstantTestUtil.functionHolderCounter(holder));
            assertSame(cif, LazyConstantTestUtil.functionHolderFunction(holder));
            Integer val = e.getValue();
            Integer val2 = e.getValue();
            i.incrementAndGet();
        });
        assertEquals(0, LazyConstantTestUtil.functionHolderCounter(holder));
        assertNull(LazyConstantTestUtil.functionHolderFunction(holder));
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

    @Test
    void enumAliasing() {
        enum MyEnum {FOO, BAR}
        enum MySecondEnum{BAZ, QUX}
        Map<MyEnum, Integer> mapEnum = Map.ofLazy(EnumSet.allOf(MyEnum.class), MyEnum::ordinal);
        assertEquals(MyEnum.BAR.ordinal(), mapEnum.get(MyEnum.BAR));
        // Make sure class is checked, not just `ordinal()`
        assertNull(mapEnum.get(MySecondEnum.QUX));
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


    static Map<Value, Integer> newLazyMap(Set<Value> set) {
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

    private static int functionCounter(Map<?, ?> lazy) {
        final Object holder = LazyConstantTestUtil.functionHolder(lazy);
        return LazyConstantTestUtil.functionHolderCounter(holder);
    }

    // Javadoc equivalent
    class LazyMap<K, V> extends AbstractMap<K, V> {

        private final Map<K, LazyConstant<V>> backingMap;

        public LazyMap(Set<K> keys, Function<K, V> computingFunction) {
            this.backingMap = keys.stream()
                    .collect(Collectors.toUnmodifiableMap(
                            Function.identity(),
                            k -> LazyConstant.of(() -> computingFunction.apply(k))));
        }

        @Override
        public V get(Object key) {
            var lazyConstant = backingMap.get(key);
            return lazyConstant == null
                    ? null
                    : lazyConstant.get();
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return Set.of();
        }
    }

}
