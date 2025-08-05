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
 * @summary Basic tests for StableFunction methods
 * @enablePreview
 * @run junit StableFunctionTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class StableFunctionTest {

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

    @ParameterizedTest
    @MethodSource("allSets")
    void factoryInvariants(Set<Value> inputs) {
        assertThrows(NullPointerException.class, () -> StableValue.function(null, MAPPER));
        assertThrows(NullPointerException.class, () -> StableValue.function(inputs, null));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void basic(Set<Value> inputs) {
        basic(inputs, MAPPER);
        toStringTest(inputs, MAPPER);
        basic(inputs, _ -> null);
        toStringTest(inputs, _ -> null);
    }

    void basic(Set<Value> inputs, Function<Value, Integer> mapper) {
        StableTestUtil.CountingFunction<Value, Integer> cif = new StableTestUtil.CountingFunction<>(mapper);
        var cached = StableValue.function(inputs, cif);
        assertEquals(mapper.apply(Value.FORTY_TWO), cached.apply(Value.FORTY_TWO));
        assertEquals(1, cif.cnt());
        assertEquals(mapper.apply(Value.FORTY_TWO), cached.apply(Value.FORTY_TWO));
        assertEquals(1, cif.cnt());
        var x0 = assertThrows(IllegalArgumentException.class, () -> cached.apply(Value.ILLEGAL_BEFORE));
        assertEquals("Input not allowed: ILLEGAL_BEFORE", x0.getMessage());
        var x1 = assertThrows(IllegalArgumentException.class, () -> cached.apply(Value.ILLEGAL_BETWEEN));
        assertEquals("Input not allowed: ILLEGAL_BETWEEN", x1.getMessage());
        var x2 = assertThrows(IllegalArgumentException.class, () -> cached.apply(Value.ILLEGAL_AFTER));
        assertEquals("Input not allowed: ILLEGAL_AFTER", x2.getMessage());
    }

    void toStringTest(Set<Value> inputs, Function<Value, Integer> mapper) {
        var cached = StableValue.function(inputs, mapper);
        cached.apply(Value.FORTY_TWO);
        var toString = cached.toString();
        assertTrue(toString.startsWith("{"));
        // Key order is unspecified
        assertTrue(toString.contains(Value.THIRTEEN + "=.unset"));
        assertTrue(toString.contains(Value.FORTY_TWO + "=" + mapper.apply(Value.FORTY_TWO)));
        assertTrue(toString.endsWith("}"));
        // One between the values
        assertEquals(1L, toString.chars().filter(ch -> ch == ',').count());
    }

    @ParameterizedTest
    @MethodSource("emptySets")
    void empty(Set<Value> inputs) {
        Function<Value, Integer> f0 = StableValue.function(inputs, Value::asInt);
        Function<Value, Integer> f1 = StableValue.function(inputs, Value::asInt);
        assertEquals("{}", f0.toString());
        assertThrows(NullPointerException.class, () -> f0.apply(null));
        assertNotEquals(f0, f1);
        assertNotEquals(null, f0);
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void exception(Set<Value> inputs) {
        StableTestUtil.CountingFunction<Value, Integer> cif = new StableTestUtil.CountingFunction<>(_ -> {
            throw new UnsupportedOperationException();
        });
        var cached = StableValue.function(inputs, cif);
        assertThrows(UnsupportedOperationException.class, () -> cached.apply(Value.FORTY_TWO));
        assertEquals(1, cif.cnt());
        assertThrows(UnsupportedOperationException.class, () -> cached.apply(Value.FORTY_TWO));
        assertEquals(2, cif.cnt());
        var toString = cached.toString();
        assertTrue(toString.startsWith("{"));
        // Key order is unspecified
        assertTrue(toString.contains(Value.THIRTEEN + "=.unset"));
        assertTrue(toString.contains(Value.FORTY_TWO + "=.unset"));
        assertTrue(toString.endsWith("}"));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void circular(Set<Value> inputs) {
        final AtomicReference<Function<?, ?>> ref = new AtomicReference<>();
        Function<Value, Function<?, ?>> cached = StableValue.function(inputs, _ -> ref.get());
        ref.set(cached);
        cached.apply(Value.FORTY_TWO);
        var toString = cached.toString();
        assertTrue(toString.contains("FORTY_TWO=(this StableFunction)"), toString);
        assertDoesNotThrow(cached::hashCode);
        assertDoesNotThrow((() -> cached.equals(cached)));
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void equality(Set<Value> inputs) {
        Function<Value, Integer> mapper = Value::asInt;
        Function<Value, Integer> f0 = StableValue.function(inputs, mapper);
        Function<Value, Integer> f1 = StableValue.function(inputs, mapper);
        // No function is equal to another function
        assertNotEquals(f0, f1);
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void hashCodeStable(Set<Value> inputs) {
        Function<Value, Integer> f0 = StableValue.function(inputs, Value::asInt);
        assertEquals(System.identityHashCode(f0), f0.hashCode());
        if (!inputs.isEmpty()) {
            f0.apply(Value.FORTY_TWO);
            assertEquals(System.identityHashCode(f0), f0.hashCode());
        }
    }

    @Test
    void nullKeys() {
        Set<Value> inputs = new HashSet<>();
        inputs.add(Value.FORTY_TWO);
        inputs.add(null);
        assertThrows(NullPointerException.class, () -> StableValue.function(inputs, MAPPER));
    }

    @Test
    void usesOptimizedVersion() {
        Function<Value, Integer> enumFunction = StableValue.function(EnumSet.of(Value.FORTY_TWO), Value::asInt);
        assertEquals("jdk.internal.lang.stable.StableEnumFunction", enumFunction.getClass().getName());
        Function<Value, Integer> emptyFunction = StableValue.function(Set.of(), Value::asInt);
        assertEquals("jdk.internal.lang.stable.StableFunction", emptyFunction.getClass().getName());
    }

    @Test
    void overriddenEnum() {
        final var overridden = Value.THIRTEEN;
        Function<Value, Integer> enumFunction = StableValue.function(EnumSet.of(overridden), Value::asInt);
        assertEquals(MAPPER.apply(overridden), enumFunction.apply(overridden));
    }

    private static Stream<Set<Value>> nonEmptySets() {
        return Stream.of(
                Set.of(Value.FORTY_TWO, Value.THIRTEEN),
                linkedHashSet(Value.THIRTEEN, Value.FORTY_TWO),
                treeSet(Value.FORTY_TWO, Value.THIRTEEN),
                EnumSet.of(Value.FORTY_TWO, Value.THIRTEEN)
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
