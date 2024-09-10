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
 * @summary Basic tests for CachingFunction methods
 * @compile --enable-preview -source ${jdk.version} CachingFunctionTest.java
 * @run junit/othervm --enable-preview CachingFunctionTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class CachingFunctionTest {

    enum Value {
        THIRTEEN(13),
        FORTY_TWO(42),
        ILLEGAL(-1);

        final int intValue;

        Value(int intValue) {
            this.intValue = intValue;
        }

        int asInt() {
            return intValue;
        }

    }

    private static final Value VALUE = Value.FORTY_TWO;
    private static final Value VALUE2 = Value.THIRTEEN;
    private static final Function<Value, Integer> MAPPER = Value::asInt;

    @ParameterizedTest
    @MethodSource("allSets")
    void factoryInvariants(Set<Value> inputs) {
        assertThrows(NullPointerException.class, () -> StableValue.newCachingFunction(null, MAPPER));
        assertThrows(NullPointerException.class, () -> StableValue.newCachingFunction(inputs, null));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void basic(Set<Value> inputs) {
        basic(inputs, MAPPER);
        basic(inputs, _ -> null);
    }

    void basic(Set<Value> inputs, Function<Value, Integer> mapper) {
        StableTestUtil.CountingFunction<Value, Integer> cif = new StableTestUtil.CountingFunction<>(mapper);
        var cached = StableValue.newCachingFunction(inputs, cif);
        assertEquals(mapper.apply(VALUE), cached.apply(VALUE));
        assertEquals(1, cif.cnt());
        assertEquals(mapper.apply(VALUE), cached.apply(VALUE));
        assertEquals(1, cif.cnt());
        assertTrue(cached.toString().startsWith(cached.getClass().getSimpleName() + "[values={"));
        // Key order is unspecified
        assertTrue(cached.toString().contains(VALUE2 + "=.unset"));
        assertTrue(cached.toString().contains(VALUE + "=[" + mapper.apply(VALUE) + "]"));
        assertTrue(cached.toString().endsWith(", original=" + cif + "]"));
        // One between the values and one just before "original"
        assertEquals(2L, cached.toString().chars().filter(ch -> ch == ',').count());
        var x = assertThrows(IllegalArgumentException.class, () -> cached.apply(Value.ILLEGAL));
        assertTrue(x.getMessage().contains("ILLEGAL"));
    }

    @ParameterizedTest
    @MethodSource("emptySets")
    void empty(Set<Value> inputs) {
        Function<Value, Integer> f0 = StableValue.newCachingFunction(inputs, Value::asInt);
        assertTrue(f0.toString().contains("{}"));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void exception(Set<Value> inputs) {
        StableTestUtil.CountingFunction<Value, Integer> cif = new StableTestUtil.CountingFunction<>(_ -> {
            throw new UnsupportedOperationException();
        });
        var cached = StableValue.newCachingFunction(inputs, cif);
        assertThrows(UnsupportedOperationException.class, () -> cached.apply(VALUE));
        assertEquals(1, cif.cnt());
        assertThrows(UnsupportedOperationException.class, () -> cached.apply(VALUE));
        assertEquals(2, cif.cnt());
        assertTrue(cached.toString().startsWith(cached.getClass().getSimpleName() + "[values={"));
        // Key order is unspecified
        assertTrue(cached.toString().contains(VALUE2 + "=.unset"));
        assertTrue(cached.toString().contains(VALUE + "=.unset"));
        assertTrue(cached.toString().endsWith(", original=" + cif + "]"));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void circular(Set<Value> inputs) {
        final AtomicReference<Function<?, ?>> ref = new AtomicReference<>();
        Function<Value, Function<?, ?>> cached = StableValue.newCachingFunction(inputs, _ -> ref.get());
        ref.set(cached);
        cached.apply(VALUE);
        String toString = cached.toString();
        assertTrue(toString.contains("(this " + cached.getClass().getSimpleName() + ")"));
        assertDoesNotThrow(cached::hashCode);
        assertDoesNotThrow((() -> cached.equals(cached)));
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void equality(Set<Value> inputs) {
        Function<Value, Integer> mapper = Value::asInt;
        Function<Value, Integer> f0 = StableValue.newCachingFunction(inputs, mapper);
        Function<Value, Integer> f1 = StableValue.newCachingFunction(inputs, mapper);
        // No function is equal to another function
        assertNotEquals(f0, f1);
    }

    @ParameterizedTest
    @MethodSource("allSets")
    void hashCodeStable(Set<Value> inputs) {
        Function<Value, Integer> f0 = StableValue.newCachingFunction(inputs, Value::asInt);
        assertEquals(System.identityHashCode(f0), f0.hashCode());
        if (!inputs.isEmpty()) {
            f0.apply(VALUE);
            assertEquals(System.identityHashCode(f0), f0.hashCode());
        }
    }

    private static Stream<Set<Value>> nonEmptySets() {
        return Stream.of(
                Set.of(VALUE, VALUE2),
                linkedHashSet(VALUE, VALUE2),
                treeSet(VALUE2, VALUE),
                EnumSet.of(VALUE, VALUE2)
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
