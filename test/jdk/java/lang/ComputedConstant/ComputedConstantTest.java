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
 * @summary Basic tests for ComputedConstant implementations
 * @enablePreview
 * @modules java.base/jdk.internal.lang
 * @run junit/othervm --add-opens java.base/jdk.internal.lang=ALL-UNNAMED ComputedConstantTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.ComputedConstant;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ComputedConstantTest {

    private static final int VALUE = 42;
    private static final Supplier<Integer> SUPPLIER = () -> VALUE;

    @Test
    void factoryInvariants() {
        assertThrows(NullPointerException.class, () -> ComputedConstant.of(null));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void basic(Function<Supplier<Integer>, ComputedConstant<Integer>> factory) {
        ComputedConstantTestUtil.CountingSupplier<Integer> cs = new ComputedConstantTestUtil.CountingSupplier<>(SUPPLIER);
        var cached = factory.apply(cs);
        assertEquals(".unset", cached.toString());
        assertEquals(SUPPLIER.get(), cached.get());
        assertEquals(1, cs.cnt());
        assertEquals(SUPPLIER.get(), cached.get());
        assertEquals(1, cs.cnt());
        assertEquals(Objects.toString(SUPPLIER.get()), cached.toString());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void exception(Function<Supplier<Integer>, ComputedConstant<Integer>> factory) {
        ComputedConstantTestUtil.CountingSupplier<Integer> cs = new ComputedConstantTestUtil.CountingSupplier<>(() -> {
            throw new UnsupportedOperationException();
        });
        var cached = factory.apply(cs);
        assertThrows(UnsupportedOperationException.class, cached::get);
        assertEquals(1, cs.cnt());
        assertThrows(UnsupportedOperationException.class, cached::get);
        assertEquals(2, cs.cnt());
        assertEquals(".unset", cached.toString());
    }

    @ParameterizedTest
    @MethodSource("computedConstants")
    void orElse(ComputedConstant<Integer> constant) {
        assertNull(constant.orElse(null));
        constant.get();
        assertEquals(VALUE, constant.orElse(null));
    }

    @ParameterizedTest
    @MethodSource("computedConstants")
    void get(ComputedConstant<Integer> constant) {
        assertEquals(VALUE, constant.get());
    }

    @ParameterizedTest
    @MethodSource("computedConstants")
    void isInitialized(ComputedConstant<Integer> constant) {
        assertFalse(constant.isInitialized());
        constant.get();
        assertTrue(constant.isInitialized());
   }

    @ParameterizedTest
    @MethodSource("computedConstants")
    void testHashCode(ComputedConstant<Integer> constant) {
        ComputedConstant<Integer> c1 = ComputedConstant.of(SUPPLIER);
        assertEquals(c1.hashCode(), constant.hashCode());
        constant.get();
        assertEquals(c1.hashCode(), constant.hashCode());
        c1.get();
        assertEquals(c1.hashCode(), constant.hashCode());
        ComputedConstant<Integer> different = ComputedConstant.of(() -> 13);
        assertNotEquals(different.hashCode(), constant.hashCode());
    }

    @ParameterizedTest
    @MethodSource("computedConstants")
    void testEquals(ComputedConstant<Integer> c0) {
        assertNotEquals(null, c0);
        ComputedConstant<Integer> s1 = ComputedConstant.of(SUPPLIER);
        assertEquals(c0, s1);
        c0.get();
        assertEquals(c0, s1);
        s1.get();
        assertEquals(c0, s1);
        ComputedConstant<Integer> different = ComputedConstant.of(() -> 13);
        assertNotEquals(different, c0);
        assertNotEquals("a", c0);
    }

    @ParameterizedTest
    @MethodSource("computedConstants")
    void toStringUnset(ComputedConstant<Integer> constant) {
        assertEquals(".unset", constant.toString());
        constant.get();
        assertEquals(Integer.toString(VALUE), constant.toString());
    }

    @Test
    void toStringCircular() {
        AtomicReference<ComputedConstant<?>> ref = new AtomicReference<>();
        ComputedConstant<ComputedConstant<?>> constant = ComputedConstant.of(ref::get);
        ref.set(constant);
        constant.get();
        String toString = assertDoesNotThrow(constant::toString);
        assertEquals("(this ComputedConstant)", toString);
    }

    @Test
    void recursiveCall() {
        AtomicReference<ComputedConstant<Integer>> ref = new AtomicReference<>();
        ComputedConstant<Integer> constant = ComputedConstant.of(() -> ref.get().get());
        ComputedConstant<Integer> constant1 = ComputedConstant.of(constant);
        ref.set(constant1);
        assertThrows(IllegalStateException.class, constant::get);
    }

    @ParameterizedTest
    @MethodSource("factories")
    void underlying(Function<Supplier<Integer>, ComputedConstant<Integer>> factory) {
        ComputedConstantTestUtil.CountingSupplier<Integer> cs = new ComputedConstantTestUtil.CountingSupplier<>(SUPPLIER);
        var f1 = factory.apply(cs);

        Supplier<?> underlyingBefore = ComputedConstantTestUtil.underlying(f1);
        assertSame(cs, underlyingBefore);
        int v = f1.get();
        Supplier<?> underlyingAfter = ComputedConstantTestUtil.underlying(f1);
        assertNull(underlyingAfter);
    }

    @ParameterizedTest
    @MethodSource("factories")
    void functionHolderException(Function<Supplier<Integer>, ComputedConstant<Integer>> factory) {
        ComputedConstantTestUtil.CountingSupplier<Integer> cs = new ComputedConstantTestUtil.CountingSupplier<>(() -> {
            throw new UnsupportedOperationException();
        });
        var f1 = factory.apply(cs);

        Supplier<?> underlyingBefore = ComputedConstantTestUtil.underlying(f1);
        assertSame(cs, underlyingBefore);
        try {
            int v = f1.get();
        } catch (UnsupportedOperationException _) {
            // Expected
        }
        Supplier<?> underlyingAfter = ComputedConstantTestUtil.underlying(f1);
        assertSame(cs, underlyingAfter);
    }

    private static Stream<ComputedConstant<Integer>> computedConstants() {
        return factories()
                .map(f -> f.apply(() -> VALUE));
    }

    private static Stream<Function<Supplier<Integer>, ComputedConstant<Integer>>> factories() {
        return Stream.of(
                supplier("ComputedConstant.of(<lambda>)", ComputedConstant::of)
        );
    }

    private static Function<Supplier<Integer>, ComputedConstant<Integer>> supplier(String name,
                                                                                   Function<Supplier<Integer>, ComputedConstant<Integer>> underlying) {
        return new Function<Supplier<Integer>, ComputedConstant<Integer>>() {
            @Override
            public ComputedConstant<Integer> apply(Supplier<Integer> supplier) {
                return ComputedConstant.of(supplier);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

}
