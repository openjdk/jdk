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
 * @summary Basic tests for Stable Heterogeneous Container methods
 * @modules java.base/jdk.internal.lang.stable
 * @compile --enable-preview -source ${jdk.version} StableHeterogeneousContainerTest.java
 * @run junit/othervm --enable-preview StableHeterogeneousContainerTest
 */

import jdk.internal.lang.stable.StableValueFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class StableHeterogeneousContainerTest {

    enum Value {
        SHORT((short) Short.BYTES),
        INTEGER(Integer.BYTES);

        final Object value;

        Value(Object value) {
            this.value = value;
        }

        Object value() {
            return value;
        }

        <T> T valueAs(Class<T> type) {
            return type.cast(value);
        }

        Class<?> clazz() {
            return value.getClass();
        }

    }

    @Test
    void factoryInvariants() {
        assertThrows(NullPointerException.class, () -> StableValueFactories.ofHeterogeneousContainer(null));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void tryPut(Set<Value> inputs) {
        var container = StableValueFactories.ofHeterogeneousContainer(classes(inputs));
        assertTrue(container.tryPut(Integer.class, Value.INTEGER.valueAs(Integer.class)));
        assertFalse(container.tryPut(Integer.class, Value.INTEGER.valueAs(Integer.class)));
        assertEquals(Value.INTEGER.value(), container.get(Integer.class));
        var iae = assertThrows(IllegalArgumentException.class, () -> container.tryPut(Long.class, 8L));
        assertEquals("No such type: " + Long.class, iae.getMessage());
        var npe = assertThrows(NullPointerException.class, () -> container.tryPut(Short.class, null));
        assertEquals("The provided instance for '" + Short.class + "' was null", npe.getMessage());
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void computeIfAbsent(Set<Value> inputs) {
        var container = StableValueFactories.ofHeterogeneousContainer(classes(inputs));
        assertEquals(Value.INTEGER.value(), container.computeIfAbsent(Integer.class, Value.INTEGER::valueAs));
        var iae = assertThrows(IllegalArgumentException.class, () -> container.computeIfAbsent(Long.class, _ -> 8L));
        assertEquals("No such type: " + Long.class, iae.getMessage());
        var npe = assertThrows(NullPointerException.class, () -> container.computeIfAbsent(Short.class, _ -> null));
        assertEquals("The constructor for `" + Short.class + "` returned null", npe.getMessage());
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void get(Set<Value> inputs) {
        var container = StableValueFactories.ofHeterogeneousContainer(classes(inputs));
        assertTrue(container.tryPut(Integer.class, Value.INTEGER.valueAs(Integer.class)));
        assertEquals(Value.INTEGER.value(), container.get(Integer.class));
        assertNull(container.get(Value.SHORT.clazz()));
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void getOrThrow(Set<Value> inputs) {
        var container = StableValueFactories.ofHeterogeneousContainer(classes(inputs));
        assertTrue(container.tryPut(Integer.class, Value.INTEGER.valueAs(Integer.class)));
        assertEquals(Value.INTEGER.value(), container.getOrThrow(Integer.class));
        var e = assertThrows(NoSuchElementException.class , () -> container.getOrThrow(Value.SHORT.clazz()));
        assertEquals("The type `" + Short.class + "` is know but there is no instance associated with it", e.getMessage());
    }

    @ParameterizedTest
    @MethodSource("nonEmptySets")
    void toString(Set<Value> inputs) {
        var container = StableValueFactories.ofHeterogeneousContainer(classes(inputs));
        assertTrue(container.toString().contains("class java.lang.Integer=StableValue.unset"));
        container.tryPut(Integer.class, Value.INTEGER.valueAs(Integer.class));
        assertTrue(container.toString().contains("class java.lang.Integer=StableValue[" + Value.INTEGER.value() + "]"), container.toString());
    }

    private static Set<Class<?>> classes(Set<Value> values) {
        return values.stream()
                .map(Value::clazz)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Stream<Set<Value>> nonEmptySets() {
        return Stream.of(
                Set.of(Value.SHORT, Value.INTEGER),
                linkedHashSet(Value.SHORT, Value.INTEGER),
                treeSet(Value.SHORT, Value.INTEGER),
                EnumSet.of(Value.SHORT, Value.INTEGER)
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
        return populate(new TreeSet<>(Comparator.comparingInt(Value::ordinal).reversed()),values);
    }

    static Set<Value> linkedHashSet(Value... values) {
        return populate(new LinkedHashSet<>(), values);
    }

    static Set<Value> populate(Set<Value> set, Value... values) {
        set.addAll(Arrays.asList(values));
        return set;
    }

}
