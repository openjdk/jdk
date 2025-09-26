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
 * @run junit StableSupplierTest
 */

import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class StableSupplierTest {

    private static final Supplier<Integer> SUPPLIER = () -> 42;

    @Test
    void factoryInvariants() {
        assertThrows(NullPointerException.class, () -> StableValue.supplier(null));
    }

    @Test
    void basic() {
        basic(SUPPLIER);
        basic(() -> null);
    }

    void basic(Supplier<Integer> supplier) {
        StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(supplier);
        var cached = StableValue.supplier(cs);
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
        var cached = StableValue.supplier(cs);
        assertThrows(UnsupportedOperationException.class, cached::get);
        assertEquals(1, cs.cnt());
        assertThrows(UnsupportedOperationException.class, cached::get);
        assertEquals(2, cs.cnt());
        assertEquals(".unset", cached.toString());
    }

    @Test
    void circular() {
        final AtomicReference<Supplier<?>> ref = new AtomicReference<>();
        Supplier<Supplier<?>> cached = StableValue.supplier(ref::get);
        ref.set(cached);
        cached.get();
        String toString = cached.toString();
        assertTrue(toString.startsWith("(this StableSupplier)"));
        assertDoesNotThrow(cached::hashCode);
    }

    @Test
    void equality() {
        Supplier<Integer> f0 = StableValue.supplier(SUPPLIER);
        Supplier<Integer> f1 = StableValue.supplier(SUPPLIER);
        // No function is equal to another function
        assertNotEquals(f0, f1);
    }

    @Test
    void hashCodeStable() {
        Supplier<Integer> f0 = StableValue.supplier(SUPPLIER);
        assertEquals(System.identityHashCode(f0), f0.hashCode());
        f0.get();
        assertEquals(System.identityHashCode(f0), f0.hashCode());
    }

}
