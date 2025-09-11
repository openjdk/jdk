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
 * @summary Basic tests for StableValue implementations
 * @enablePreview
 * @modules java.base/jdk.internal.lang.stable
 * @run junit ComputedConstantTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.atomic.AtomicReference;
import java.lang.ComputedConstant;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ComputedConstantTest {

    private static final int VALUE = 42;
    private static final int VALUE2 = 13;


    @ParameterizedTest
    @MethodSource("computedConstants")
    void orElse(ComputedConstant<Integer> stable) {
        assertNull(stable.orElse(null));
        stable.get();
        assertEquals(VALUE, stable.orElse(null));
    }

    @ParameterizedTest
    @MethodSource("computedConstants")
    void get(ComputedConstant<Integer> stable) {
        assertEquals(VALUE, stable.get());
    }

    @ParameterizedTest
    @MethodSource("computedConstants")
    void isSet(ComputedConstant<Integer> stable) {
        assertFalse(stable.isSet());
        stable.get();
        assertTrue(stable.isSet());
   }

    @ParameterizedTest
    @MethodSource("computedConstants")
    void testHashCode(ComputedConstant<Integer> stable) {
        // Should be Object::hashCode
        assertEquals(System.identityHashCode(stable), stable.hashCode());
    }

    @ParameterizedTest
    @MethodSource("computedConstants")
    void testEquals(ComputedConstant<Integer> s0) {
        assertNotEquals(null, s0);
        ComputedConstant<Integer> s1 = ComputedConstant.of(() -> VALUE);
        assertNotEquals(s0, s1); // Identity based
        s0.get();
        s1.get();
        assertNotEquals(s0, s1);
        assertNotEquals("a", s0);
    }

    @ParameterizedTest
    @MethodSource("computedConstants")
    void toStringUnset(ComputedConstant<Integer> stable) {
        assertEquals(".unset", stable.toString());
        stable.get();
        assertEquals(Integer.toString(VALUE), stable.toString());
    }

    @Test
    void toStringCircular() {
        AtomicReference<ComputedConstant<?>> ref = new AtomicReference<>();
        ComputedConstant<ComputedConstant<?>> stable = ComputedConstant.of(ref::get);
        ref.set(stable);
        stable.get();
        String toString = assertDoesNotThrow(stable::toString);
        assertEquals("(this ComputedConstant)", toString);
        assertDoesNotThrow(stable::hashCode);
        assertDoesNotThrow((() -> stable.equals(stable)));
    }

    @Test
    void recursiveCall() {
        AtomicReference<ComputedConstant<Integer>> ref = new AtomicReference<>();
        ComputedConstant<Integer> stable = ComputedConstant.of(() -> ref.get().get());
        ComputedConstant<Integer> stable2 = ComputedConstant.of(stable);
        ref.set(stable2);
        assertThrows(IllegalStateException.class, stable::get);
    }

    private static Stream<ComputedConstant<Integer>> computedConstants() {
        return factories()
                .map(Supplier::get);
    }

    private static Stream<Supplier<ComputedConstant<Integer>>> factories() {
        return Stream.of(
                supplier("ComputedConstant.of(<lambda>)", () -> ComputedConstant.of(() -> VALUE))
        );
    }

    private static <T> Supplier<T> supplier(String name, Supplier<? extends T> underlying) {
        return new Supplier<T>() {
            @Override
            public T get() {
                return underlying.get();
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

}
