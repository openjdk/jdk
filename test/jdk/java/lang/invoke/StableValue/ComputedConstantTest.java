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
 * @summary Basic tests for ComputedConstant methods
 * @enablePreview
 * @modules java.base/jdk.internal.lang.stable
 * @compile StableTestUtil.java
 * @run junit/othervm --add-opens java.base/jdk.internal.lang.stable=ALL-UNNAMED ComputedConstantTest
 */

import jdk.internal.lang.stable.FunctionHolder;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class ComputedConstantTest {

    private static final Supplier<Integer> SUPPLIER = () -> 42;

    @Test
    void factoryInvariants() {
        assertThrows(NullPointerException.class, () -> ComputedConstant.of(null));
    }

    @Test
    void basic() {
        basic(SUPPLIER);
    }

    void basic(Supplier<Integer> supplier) {
        StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(supplier);
        var cached = ComputedConstant.of(cs);
        assertEquals(".unset", cached.toString());
        assertEquals(supplier.get(), cached.get());
        assertEquals(1, cs.cnt());
        assertEquals(supplier.get(), cached.get());
        assertEquals(1, cs.cnt());
        assertEquals(Objects.toString(supplier.get()), cached.toString());
    }

    @Test
    void exception() {
        StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(() -> {
            throw new UnsupportedOperationException();
        });
        var cached = ComputedConstant.of(cs);
        assertThrows(UnsupportedOperationException.class, cached::get);
        assertEquals(1, cs.cnt());
        assertThrows(UnsupportedOperationException.class, cached::get);
        assertEquals(2, cs.cnt());
        assertEquals(".unset", cached.toString());
    }

    @Test
    void circular() {
        final AtomicReference<ComputedConstant<?>> ref = new AtomicReference<>();
        ComputedConstant<ComputedConstant<?>> cached = ComputedConstant.of(ref::get);
        ref.set(cached);
        cached.get();
        String toString = cached.toString();
        assertTrue(toString.startsWith("(this ComputedConstant)"));
        assertDoesNotThrow(cached::hashCode);
    }

    @Test
    void equality() {
        ComputedConstant<Integer> f0 = ComputedConstant.of(SUPPLIER);
        ComputedConstant<Integer> f1 = ComputedConstant.of(SUPPLIER);
        // No function is equal to another function
        assertNotEquals(f0, f1);
    }

    @Test
    void hashCodeStable() {
        ComputedConstant<Integer> f0 = ComputedConstant.of(SUPPLIER);
        assertEquals(System.identityHashCode(f0), f0.hashCode());
        f0.get();
        assertEquals(System.identityHashCode(f0), f0.hashCode());
    }

    @Test
    void functionHolder() {
        StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(SUPPLIER);
        var f1 = ComputedConstant.of(cs);

        FunctionHolder<?> holder = StableTestUtil.functionHolder(f1);
        assertEquals(1, holder.counter());
        assertSame(cs, holder.function());
        int v = f1.get();
        int v2 = f1.get();
        assertEquals(0, holder.counter());
        assertNull(holder.function(), holder.toString());
    }

    @Test
    void functionHolderException() {
        StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(() -> {
            throw new UnsupportedOperationException();
        });
        var f1 = ComputedConstant.of(cs);

        FunctionHolder<?> holder = StableTestUtil.functionHolder(f1);
        assertEquals(1, holder.counter());
        assertSame(cs, holder.function());
        try {
            int v = f1.get();
        } catch (UnsupportedOperationException _) {
            // Expected
        }
        assertEquals(1, holder.counter());
        assertSame(cs, holder.function());
    }

}
