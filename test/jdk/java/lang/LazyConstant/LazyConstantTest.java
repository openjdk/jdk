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
 * @summary Basic tests for the LazyConstant implementation
 * @enablePreview
 * @modules java.base/jdk.internal.lang
 * @run junit/othervm --add-opens java.base/jdk.internal.lang=ALL-UNNAMED LazyConstantTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.LazyConstant;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class LazyConstantTest {

    private static final int VALUE = 42;
    private static final Supplier<Integer> SUPPLIER = () -> VALUE;

    @Test
    void factoryInvariants() {
        assertThrows(NullPointerException.class, () -> LazyConstant.of(null));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void basic(Function<Supplier<Integer>, LazyConstant<Integer>> factory) {
        LazyConstantTestUtil.CountingSupplier<Integer> cs = new LazyConstantTestUtil.CountingSupplier<>(SUPPLIER);
        var lazy = factory.apply(cs);
        assertFalse(lazy.isInitialized());
        assertEquals(SUPPLIER.get(), lazy.get());
        assertEquals(1, cs.cnt());
        assertEquals(SUPPLIER.get(), lazy.get());
        assertEquals(1, cs.cnt());
        assertTrue(lazy.toString().contains(Integer.toString(SUPPLIER.get())));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void exception(Function<Supplier<Integer>, LazyConstant<Integer>> factory) {
        LazyConstantTestUtil.CountingSupplier<Integer> cs = new LazyConstantTestUtil.CountingSupplier<>(() -> {
            throw new UnsupportedOperationException();
        });
        var lazy = factory.apply(cs);
        assertThrows(UnsupportedOperationException.class, lazy::get);
        assertEquals(1, cs.cnt());
        assertThrows(UnsupportedOperationException.class, lazy::get);
        assertEquals(2, cs.cnt());
        assertTrue(lazy.toString().contains("computing function"));
    }

    @ParameterizedTest
    @MethodSource("lazyConstants")
    void orElse(LazyConstant<Integer> constant) {
        assertNull(constant.orElse(null));
        constant.get();
        assertEquals(VALUE, constant.orElse(null));
    }

    @ParameterizedTest
    @MethodSource("lazyConstants")
    void get(LazyConstant<Integer> constant) {
        assertEquals(VALUE, constant.get());
    }

    @ParameterizedTest
    @MethodSource("lazyConstants")
    void isInitialized(LazyConstant<Integer> constant) {
        assertFalse(constant.isInitialized());
        constant.get();
        assertTrue(constant.isInitialized());
   }

    @ParameterizedTest
    @MethodSource("lazyConstants")
    void testHashCode(LazyConstant<Integer> constant) {
        assertEquals(System.identityHashCode(constant), constant.hashCode());
    }

    @ParameterizedTest
    @MethodSource("lazyConstants")
    void testEquals(LazyConstant<Integer> c0) {
        assertNotEquals(null, c0);
        LazyConstant<Integer> different = LazyConstant.of(SUPPLIER);
        assertNotEquals(different, c0);
        assertNotEquals(c0, different);
        assertNotEquals("a", c0);
    }

    @ParameterizedTest
    @MethodSource("lazyConstants")
    void testLazyConstantAsComputingFunction(LazyConstant<Integer> constant) {
        LazyConstant<Integer> c1 = LazyConstant.of(constant);
        assertSame(constant, c1);
    }

    @Test
    void toStringTest() {
        Supplier<String> supplier = () -> "str";
        LazyConstant<String> lazy = LazyConstant.of(supplier);
        var expectedSubstring = "computing function=" + supplier;
        assertTrue(lazy.toString().contains(expectedSubstring));
        lazy.get();
        assertTrue(lazy.toString().contains("str"));
    }

    @ParameterizedTest
    @MethodSource("lazyConstants")
    void toStringUnset(LazyConstant<Integer> constant) {
        String unInitializedToString = constant.toString();
        int suffixEnd = unInitializedToString.indexOf("[");
        String suffix = unInitializedToString.substring(0, suffixEnd);
        String expectedUninitialized = suffix+"[computing function=";
        assertTrue(unInitializedToString.startsWith(expectedUninitialized));
        constant.get();
        String expectedInitialized = suffix + "[" + VALUE + "]";
        assertEquals(expectedInitialized, constant.toString());
    }

    @Test
    void toStringCircular() {
        AtomicReference<LazyConstant<?>> ref = new AtomicReference<>();
        LazyConstant<LazyConstant<?>> constant = LazyConstant.of(ref::get);
        ref.set(constant);
        constant.get();
        String toString = assertDoesNotThrow(constant::toString);
        assertTrue(constant.toString().contains("(this LazyConstant)"), toString);
    }

    @Test
    void recursiveCall() {
        AtomicReference<LazyConstant<Integer>> ref = new AtomicReference<>();
        LazyConstant<Integer> constant = LazyConstant.of(() -> ref.get().get());
        LazyConstant<Integer> constant1 = LazyConstant.of(constant);
        ref.set(constant1);
        assertThrows(IllegalStateException.class, constant::get);
    }

    @ParameterizedTest
    @MethodSource("factories")
    void underlying(Function<Supplier<Integer>, LazyConstant<Integer>> factory) {
        LazyConstantTestUtil.CountingSupplier<Integer> cs = new LazyConstantTestUtil.CountingSupplier<>(SUPPLIER);
        var f1 = factory.apply(cs);

        Supplier<?> underlyingBefore = LazyConstantTestUtil.computingFunction(f1);
        assertSame(cs, underlyingBefore);
        int v = f1.get();
        Supplier<?> underlyingAfter = LazyConstantTestUtil.computingFunction(f1);
        assertNull(underlyingAfter);
    }

    @ParameterizedTest
    @MethodSource("factories")
    void functionHolderException(Function<Supplier<Integer>, LazyConstant<Integer>> factory) {
        LazyConstantTestUtil.CountingSupplier<Integer> cs = new LazyConstantTestUtil.CountingSupplier<>(() -> {
            throw new UnsupportedOperationException();
        });
        var f1 = factory.apply(cs);

        Supplier<?> underlyingBefore = LazyConstantTestUtil.computingFunction(f1);
        assertSame(cs, underlyingBefore);
        try {
            int v = f1.get();
        } catch (UnsupportedOperationException _) {
            // Expected
        }
        Supplier<?> underlyingAfter = LazyConstantTestUtil.computingFunction(f1);
        assertSame(cs, underlyingAfter);
    }

    private static Stream<LazyConstant<Integer>> lazyConstants() {
        return factories()
                .map(f -> f.apply(() -> VALUE));
    }

    private static Stream<Function<Supplier<Integer>, LazyConstant<Integer>>> factories() {
        return Stream.of(
                supplier("ComputedConstant.of(<lambda>)", LazyConstant::of)
        );
    }

    private static Function<Supplier<Integer>, LazyConstant<Integer>> supplier(String name,
                                                                               Function<Supplier<Integer>, LazyConstant<Integer>> underlying) {
        return new Function<Supplier<Integer>, LazyConstant<Integer>>() {
            @Override
            public LazyConstant<Integer> apply(Supplier<Integer> supplier) {
                return underlying.apply(supplier);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    record Lazy<T>(LazyConstant<T> underlying) implements Supplier<T> {
        @Override
        public T get() { return underlying.get(); }

        static <T> Lazy<T> of(Supplier<? extends T> computingFunction) {
            return new Lazy<>(LazyConstant.of(computingFunction));
        }
    }

}
