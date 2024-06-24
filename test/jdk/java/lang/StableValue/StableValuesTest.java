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
 * @summary Basic tests for StableValues methods
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} StableValuesTest.java
 * @run junit/othervm --enable-preview StableValuesTest
 */

import jdk.internal.lang.StableValue;
import jdk.internal.lang.StableValues;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class StableValuesTest {

    private static final int SIZE = 2;

    @Test
    void memoizedSupplier() {
        StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(() -> 42);
        Supplier<Integer> memoized = StableValues.memoizedSupplier(cs, null);
        assertEquals(42, memoized.get());
        assertEquals(1, cs.cnt());
        assertEquals(42, memoized.get());
        assertEquals(1, cs.cnt());
        assertEquals("MemoizedSupplier[stable=StableValue[42], original=" + cs + "]", memoized.toString());
    }

    @Test
    void memoizedSupplierBackground() {

        final AtomicInteger cnt = new AtomicInteger(0);
        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(() -> {
                    r.run();
                    cnt.incrementAndGet();
                });
            }
        };
        Supplier<Integer> memoized = StableValues.memoizedSupplier(() -> 42, factory);
        while (cnt.get() < 1) {
            Thread.onSpinWait();
        }
        assertEquals(42, memoized.get());
    }

    @Test
    void memoizedIntFunction() {
        StableTestUtil.CountingIntFunction<Integer> cif = new StableTestUtil.CountingIntFunction<>(i -> i);
        IntFunction<Integer> memoized = StableValues.memoizedIntFunction(SIZE, cif, null);
        assertEquals(1, memoized.apply(1));
        assertEquals(1, cif.cnt());
        assertEquals(1, memoized.apply(1));
        assertEquals(1, cif.cnt());
        assertEquals("MemoizedIntFunction[stables=[StableValue.unset, StableValue[1]], original=" + cif + "]", memoized.toString());
    }

    @Test
    void memoizedIntFunctionBackground() {

        final AtomicInteger cnt = new AtomicInteger(0);
        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(() -> {
                    r.run();
                    cnt.incrementAndGet();
                });
            }
        };
        IntFunction<Integer> memoized = StableValues.memoizedIntFunction(SIZE, i -> i, factory);
        while (cnt.get() < 2) {
            Thread.onSpinWait();
        }
        assertEquals(0, memoized.apply(0));
        assertEquals(1, memoized.apply(1));
    }

    @Test
    void memoizedFunction() {
        StableTestUtil.CountingFunction<Integer, Integer> cif = new StableTestUtil.CountingFunction<>(i -> i);
        Function<Integer, Integer> memoized = StableValues.memoizedFunction(Set.of(13, 42), cif, null);
        assertEquals(42, memoized.apply(42));
        assertEquals(1, cif.cnt());
        assertEquals(42, memoized.apply(42));
        assertEquals(1, cif.cnt());
        assertTrue(memoized.toString().startsWith("MemoizedFunction[stables={"));
        // Key order is unspecified
        assertTrue(memoized.toString().contains("13=StableValue.unset"));
        assertTrue(memoized.toString().contains("42=StableValue[42]"));
        assertTrue(memoized.toString().endsWith(", original=" + cif + "]"));
    }

    @Test
    void memoizedFunctionBackground() {

        final AtomicInteger cnt = new AtomicInteger(0);
        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(() -> {
                    r.run();
                    cnt.incrementAndGet();
                });
            }
        };
        Function<Integer, Integer> memoized = StableValues.memoizedFunction(Set.of(13, 42), i -> i, factory);
        while (cnt.get() < 2) {
            Thread.onSpinWait();
        }
        assertEquals(42, memoized.apply(42));
        assertEquals(13, memoized.apply(13));
    }

}
