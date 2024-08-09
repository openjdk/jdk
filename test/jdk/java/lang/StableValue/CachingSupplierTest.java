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
 * @summary Basic tests for CachingSupplier methods
 * @compile --enable-preview -source ${jdk.version} CachingSupplierTest.java
 * @run junit/othervm --enable-preview CachingSupplierTest
 */

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class CachingSupplierTest {

    private static final Supplier<Integer> SUPPLIER = () -> 42;

    @Test
    void basic() {
        basic(SUPPLIER);
        basic(() -> null);
    }

    void basic(Supplier<Integer> supplier) {
        StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(supplier);
        var cached = StableValue.newCachingSupplier(cs, null);
        assertEquals("CachingSupplier[value=.unset, original=" + cs + "]", cached.toString());
        assertEquals(supplier.get(), cached.get());
        assertEquals(1, cs.cnt());
        assertEquals(supplier.get(), cached.get());
        assertEquals(1, cs.cnt());
        assertEquals("CachingSupplier[value=[" + supplier.get() + "], original=" + cs + "]", cached.toString());
    }

    @Test
    void background() {
        final AtomicInteger cnt = new AtomicInteger(0);
        ThreadFactory factory = r -> new Thread(() -> {
            r.run();
            cnt.incrementAndGet();
        });
        var cached = StableValue.newCachingSupplier(SUPPLIER, factory);
        while (cnt.get() < 1) {
            Thread.onSpinWait();
        }
        assertEquals(SUPPLIER.get(), cached.get());
    }

    @Test
    void exception() {
        StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(() -> {
            throw new UnsupportedOperationException();
        });
        var cached = StableValue.newCachingSupplier(cs, null);
        assertThrows(UnsupportedOperationException.class, cached::get);
        assertEquals(1, cs.cnt());
        assertThrows(UnsupportedOperationException.class, cached::get);
        assertEquals(2, cs.cnt());
        assertEquals("CachingSupplier[value=.unset, original=" + cs + "]", cached.toString());
    }

    @Test
    void circular() {
        final AtomicReference<Supplier<?>> ref = new AtomicReference<>();
        Supplier<Supplier<?>> cached = StableValue.newCachingSupplier(ref::get, null);
        ref.set(cached);
        cached.get();
        String toString = cached.toString();
        assertTrue(toString.startsWith("CachingSupplier[value=(this CachingSupplier), original="));
        assertDoesNotThrow(cached::hashCode);
    }

    @Test
    void equality() {
        Supplier<Integer> f0 = StableValue.newCachingSupplier(SUPPLIER, null);
        Supplier<Integer> f1 = StableValue.newCachingSupplier(SUPPLIER, null);
        // No function is equal to another function
        assertNotEquals(f0, f1);
    }

    @Test
    void hashCodeStable() {
        Supplier<Integer> f0 = StableValue.newCachingSupplier(SUPPLIER, null);
        assertEquals(System.identityHashCode(f0), f0.hashCode());
        f0.get();
        assertEquals(System.identityHashCode(f0), f0.hashCode());
    }

}
