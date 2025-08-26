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
 * @summary Basic tests for StableSupplier methods
 * @enablePreview
 * @modules java.base/jdk.internal.invoke.stable
 * @compile StableTestUtil.java
 * @run junit/othervm --add-opens java.base/jdk.internal.invoke.stable=ALL-UNNAMED StableSupplierTest
 */

import jdk.internal.invoke.stable.FunctionHolder;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class StableSupplierTest {

    private static final Supplier<Integer> SUPPLIER = () -> 42;

    @Test
    void factoryInvariants() {
        assertThrows(NullPointerException.class, () -> Supplier.ofCaching(null));
    }

    @Test
    void basic() {
        basic(SUPPLIER);
    }

    void basic(Supplier<Integer> supplier) {
        StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(supplier);
        var cached = Supplier.ofCaching(cs);
        assertEquals(".unset", cached.toString());
        assertEquals(supplier.get(), cached.get());
        assertEquals(1, cs.cnt());
        assertEquals(supplier.get(), cached.get());
        assertEquals(1, cs.cnt());
        assertEquals(Objects.toString(supplier.get()), cached.toString());
    }

    @Test
    void deduplicate() {
         var cached = Supplier.ofCaching(SUPPLIER);
         assertSame(cached, Supplier.ofCaching(cached));
    }

    @Test
    void exception() {
        StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(() -> {
            throw new UnsupportedOperationException();
        });
        var cached = Supplier.ofCaching(cs);
        assertThrows(UnsupportedOperationException.class, cached::get);
        assertEquals(1, cs.cnt());
        assertThrows(UnsupportedOperationException.class, cached::get);
        assertEquals(2, cs.cnt());
        assertEquals(".unset", cached.toString());
    }

    @Test
    void circular() {
        final AtomicReference<Supplier<?>> ref = new AtomicReference<>();
        Supplier<Supplier<?>> cached = Supplier.ofCaching(ref::get);
        ref.set(cached);
        cached.get();
        String toString = cached.toString();
        assertTrue(toString.startsWith("(this StableSupplier)"));
        assertDoesNotThrow(cached::hashCode);
    }

    @Test
    void equality() {
        Supplier<Integer> f0 = Supplier.ofCaching(SUPPLIER);
        Supplier<Integer> f1 = Supplier.ofCaching(SUPPLIER);
        // No function is equal to another function
        assertNotEquals(f0, f1);
    }

    @Test
    void hashCodeStable() {
        Supplier<Integer> f0 = Supplier.ofCaching(SUPPLIER);
        assertEquals(System.identityHashCode(f0), f0.hashCode());
        f0.get();
        assertEquals(System.identityHashCode(f0), f0.hashCode());
    }

    @Test
    void functionHolder() {
        StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(SUPPLIER);
        var f1 = Supplier.ofCaching(cs);

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
        var f1 = Supplier.ofCaching(cs);

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
