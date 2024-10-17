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
 * @summary Basic tests for CachingIntFunction methods
 * @compile --enable-preview -source ${jdk.version} CachingIntFunctionTest.java
 * @run junit/othervm --enable-preview CachingIntFunctionTest
 */

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

final class CachingIntFunctionTest {

    private static final int SIZE = 2;
    private static final IntFunction<Integer> MAPPER = i -> i;

    @Test
    void factoryInvariants() {
        assertThrows(IllegalArgumentException.class, () -> StableValue.newCachingIntFunction(-1, MAPPER));
        assertThrows(NullPointerException.class, () -> StableValue.newCachingIntFunction(SIZE, null));
        assertThrows(IllegalArgumentException.class, () -> StableValue.newCachingIntFunction(-1, MAPPER, Thread.ofVirtual().factory()));
        assertThrows(NullPointerException.class, () -> StableValue.newCachingIntFunction(SIZE, null, Thread.ofVirtual().factory()));
        assertThrows(NullPointerException.class, () -> StableValue.newCachingIntFunction(SIZE, MAPPER, null));
    }

    @Test
    void basic() {
        basic(MAPPER);
        basic(i -> null);
    }

    void basic(IntFunction<Integer> mapper) {
        StableTestUtil.CountingIntFunction<Integer> cif = new StableTestUtil.CountingIntFunction<>(mapper);
        var cached = StableValue.newCachingIntFunction(SIZE, cif);
        assertEquals("CachingIntFunction[values=[.unset, .unset], original=" + cif + "]", cached.toString());
        assertEquals(mapper.apply(1), cached.apply(1));
        assertEquals(1, cif.cnt());
        assertEquals(mapper.apply(1), cached.apply(1));
        assertEquals(1, cif.cnt());
        assertEquals("CachingIntFunction[values=[.unset, [" + mapper.apply(1) + "]], original=" + cif + "]", cached.toString());
        assertThrows(IllegalArgumentException.class, () -> cached.apply(SIZE));
        assertThrows(IllegalArgumentException.class, () -> cached.apply(-1));
        assertThrows(IllegalArgumentException.class, () -> cached.apply(1_000_000));
    }

    @Test
    void background() {
        final AtomicInteger cnt = new AtomicInteger(0);
        ThreadFactory factory = new ThreadFactory() {
            @java.lang.Override
            public Thread newThread(Runnable r) {
                return new Thread(() -> {
                    r.run();
                    cnt.incrementAndGet();
                });
            }
        };
        var cached = StableValue.newCachingIntFunction(SIZE, MAPPER, factory);
        while (cnt.get() < 2) {
            Thread.onSpinWait();
        }
        assertEquals(0, cached.apply(0));
        assertEquals(1, cached.apply(1));
    }

    @Test
    void exception() {
        StableTestUtil.CountingIntFunction<Integer> cif = new StableTestUtil.CountingIntFunction<>(_ -> {
            throw new UnsupportedOperationException();
        });
        var cached = StableValue.newCachingIntFunction(SIZE, cif);
        assertThrows(UnsupportedOperationException.class, () -> cached.apply(1));
        assertEquals(1, cif.cnt());
        assertThrows(UnsupportedOperationException.class, () -> cached.apply(1));
        assertEquals(2, cif.cnt());
        assertEquals("CachingIntFunction[values=[.unset, .unset], original=" + cif + "]", cached.toString());
    }

    @Test
    void circular() {
        final AtomicReference<IntFunction<?>> ref = new AtomicReference<>();
        IntFunction<IntFunction<?>> cached = StableValue.newCachingIntFunction(SIZE, _ -> ref.get());
        ref.set(cached);
        cached.apply(0);
        String toString = cached.toString();
        assertTrue(toString.startsWith("CachingIntFunction[values=[(this CachingIntFunction), .unset], original="));
        assertDoesNotThrow(cached::hashCode);
        assertDoesNotThrow((() -> cached.equals(cached)));
    }

    @Test
    void equality() {
        IntFunction<Integer> f0 = StableValue.newCachingIntFunction(8, MAPPER);
        IntFunction<Integer> f1 = StableValue.newCachingIntFunction(8, MAPPER);
        // No function is equal to another function
        assertNotEquals(f0, f1);
    }

    @Test
    void hashCodeStable() {
        IntFunction<Integer> f0 = StableValue.newCachingIntFunction(8, MAPPER);
        assertEquals(System.identityHashCode(f0), f0.hashCode());
        f0.apply(4);
        assertEquals(System.identityHashCode(f0), f0.hashCode());
    }

}
