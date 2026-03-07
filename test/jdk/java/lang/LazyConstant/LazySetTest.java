/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests for lazy set methods
 * @enablePreview
 * @modules java.base/java.util:+open
 * @run junit LazySetTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.lang.Class;
import java.lang.Override;
import java.util.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class LazySetTest {

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

    private static final Value ELEMENT = Value.FORTY_TWO;
    private static final Set<Value> SET = Set.of(Value.THIRTEEN, ELEMENT);
    private static final Predicate<Value> PREDICATE = SET::contains; ;

    @ParameterizedTest
    @MethodSource("allSets")
    void factoryInvariants(Set<Value> set) {
        assertThrows(NullPointerException.class, () -> Set.ofLazy(set, null), set.getClass().getSimpleName());
        assertThrows(NullPointerException.class, () -> Set.ofLazy(null, PREDICATE));
        Set<Value> setWithNull = new HashSet<>();
        setWithNull.add(ELEMENT);
        setWithNull.add(null);
        assertThrows(NullPointerException.class, () -> Set.ofLazy(setWithNull, PREDICATE));
    }

    @ParameterizedTest
    @MethodSource("emptySets")
    void empty(Set<Value> set) {
        var lazy = newLazySet(set);
        assertTrue(lazy.isEmpty());
        assertEquals("[]", lazy.toString());
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void size(Set<Value> set) {
        assertEquals(newRegularSet(set).size(), newLazySet(set).size());
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void exception(Set<Value> set) {
        LazyConstantTestUtil.CountingFunction<Value, Integer> cif = new LazyConstantTestUtil.CountingFunction<>(_ -> {
            throw new UnsupportedOperationException();
        });
        var lazy = Map.ofLazy(set, cif);
        assertThrows(UnsupportedOperationException.class, () -> lazy.get(ELEMENT));
        assertEquals(1, cif.cnt());
        assertThrows(UnsupportedOperationException.class, () -> lazy.get(ELEMENT));
        assertEquals(2, cif.cnt());
        assertThrows(UnsupportedOperationException.class, lazy::toString);
        assertEquals(3, cif.cnt());
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void contains(Set<Value> set) {
        var lazy = newLazySet(set);
        var expected = newRegularSet(set);
        for (Value v : expected) {
            assertTrue(lazy.contains(v));
        }
        assertFalse(lazy.contains(Value.ILLEGAL_BETWEEN));
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void forEach(Set<Value> set) {
        var lazy = newLazySet(set);
        var expected = newRegularSet(set);
        Set<Value> actual = new HashSet<>();
        lazy.forEach(actual::add);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("emptySets")
    void toStringTestEmpty(Set<Value> set) {
        var lazy = newLazySet(set);
        assertEquals("[]", lazy.toString());
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void toStringTest(Set<Value> set) {
        var lazy = newLazySet(set);
        var expected = newRegularSet(set);
        var toString = lazy.toString();
        assertTrue(toString.startsWith("["));
        assertTrue(toString.endsWith("]"));

        // Key order is unspecified
        for (Value key : expected) {
            assertTrue(toString.contains(key.toString()), key + " is not in" + toString);
        }

        // One between the values
        assertEquals(expected.size() - 1, toString.chars().filter(ch -> ch == ',').count());
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void hashCodeTest(Set<Value> set) {
        var lazy = newLazySet(set);
        var regular = newRegularSet(set);
        assertEquals(regular.hashCode(), lazy.hashCode());
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void equality(Set<Value> set) {
        var lazy = newLazySet(set);
        var regular = newRegularSet(set);
        assertEquals(regular, lazy);
        assertEquals(lazy, regular);
        assertNotEquals("A", lazy);
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void recursiveCall(Set<Value> set) {
        final AtomicReference<Set<Value>> ref = new AtomicReference<>();
        @SuppressWarnings("unchecked")
        Set<Value> lazy = Set.ofLazy(set, k -> ref.get().contains(k));
        ref.set(lazy);
        var x = assertThrows(IllegalStateException.class, () -> lazy.contains(ELEMENT));
        assertEquals("Recursive initialization of a lazy collection is illegal", x.getMessage());
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void iteratorNext(Set<Value> set) {
        Set<Value> encountered = new HashSet<>();
        var expected = newRegularSet(set);
        var iterator = newLazySet(set).iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            encountered.add(entry);
        }
        assertEquals(expected, encountered);
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void iteratorForEachRemaining(Set<Value> set) {
        Set<Value> encountered = new HashSet<>();
        var expected = newRegularSet(set);
        var iterator = newLazySet(set).iterator();
        var value = iterator.next();
        encountered.add(value);
        iterator.forEachRemaining(encountered::add);
        assertEquals(expected, encountered);
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
            var lazy = newLazySet(set);
            assertThrows(expectedType, () -> operation.accept(lazy), set.getClass().getSimpleName() + " " + operation);
        }
    }

    // Implementing interfaces

    @ParameterizedTest
    @MethodSource("allSets")
    void serializable(Set<Value> set) {
        var lazy = newLazySet(set);
        assertFalse(lazy instanceof Serializable);
    }

    @Test
    void overriddenEnum() {
        final var overridden = Value.THIRTEEN;
        Set<Value> enumMap = Set.ofLazy(EnumSet.of(overridden), PREDICATE);
        assertEquals(PREDICATE.test(overridden), enumMap.contains(overridden), enumMap.toString());
    }

    // Support constructs

    record Operation(String name,
                     Consumer<Set<Value>> consumer) implements Consumer<Set<Value>> {
        @Override
        public void accept(Set<Value> set) { consumer.accept(set); }
        @Override
        public String toString() { return name; }
    }

    static Stream<Operation> nullAverseOperations() {
        return Stream.of(
            new Operation("forEach",     m -> m.forEach(null)),
            new Operation("containsAll", m -> m.containsAll(null))
        );
    }

    static Stream<Operation> unsupportedOperations() {
        return Stream.of(
            new Operation("clear",               Set::clear),
            new Operation("add",      m -> m.add(ELEMENT)),
            new Operation("addAll",   m -> m.addAll(Set.of(ELEMENT))),
            new Operation("remove",   m -> m.remove(ELEMENT)),
            new Operation("removeAll",m -> m.removeAll(Set.of(ELEMENT))),
            new Operation("retainAll",m -> m.retainAll(Set.of(ELEMENT))),
            new Operation("iter.rm",  m -> m.iterator().remove())
        );
    }

    static Set<Value> newLazySet(Set<Value> set) {
        return Set.ofLazy(set, PREDICATE);
    }

    static Set<Value> newRegularSet(Set<Value> set) {
        return set.stream()
                .filter(PREDICATE)
                .collect(Collectors.toSet());
    }

    private static Stream<Set<Value>> nonEmptySets() {
        return Stream.of(
                Set.of(ELEMENT, Value.THIRTEEN),
                linkedHashSet(Value.THIRTEEN, ELEMENT),
                treeSet(ELEMENT, Value.THIRTEEN),
                EnumSet.of(ELEMENT, Value.THIRTEEN)
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

    // JEP Example
    class Application {

        enum Option { VERBOSE, DRY_RUN, STRICT }

        // Return true when the given Option is enabled
        private static boolean isEnabled(Option option) {
            // Parse command line, read configuration file, load database
            return true;
        }

        // Lazily initialized Set of Options
        static final Set<Option> OPTIONS =
                Set.ofLazy(EnumSet.allOf(Option.class), Application::isEnabled);

        public static void process() {
            if (OPTIONS.contains(Option.DRY_RUN)) {
                // Skip processing in DRY_RUN mode
                return;
            }
            // Actual Processing logic
        }

    }

    // Javadoc equivalent
    class LazySet<E> extends AbstractCollection<E> implements Set<E> {

        private final Map<E, LazyConstant<Boolean>> backingMap;

        public LazySet(Set<E> elementCandidates, Predicate<E> computingFunction) {
            this.backingMap = elementCandidates.stream()
                    .collect(Collectors.toUnmodifiableMap(
                            Function.identity(),
                            k -> LazyConstant.of(() -> computingFunction.test(k))));
        }

        @Override
        public boolean contains(Object o) {
            var lazyConstant = backingMap.get(o);
            return lazyConstant == null
                    ? false
                    : lazyConstant.get();
        }

        @Override
        public Iterator<E> iterator() {
            return null;
        }

        @Override
        public int size() {
            return 0;
        }
    }

}
