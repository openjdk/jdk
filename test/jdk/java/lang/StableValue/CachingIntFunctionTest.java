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
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} CachingIntFunctionTest.java
 * @run junit/othervm --enable-preview CachingIntFunctionTest
 */

import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class CachingIntFunctionTest {

    private static final int SIZE = 2;

    @Test
    void basic() {
        StableTestUtil.CountingIntFunction<Integer> cif = new StableTestUtil.CountingIntFunction<>(i -> i);
        var cached = StableValue.newCachingIntFunction(SIZE, cif, null);
        assertEquals(1, cached.apply(1));
        assertEquals(1, cif.cnt());
        assertEquals(1, cached.apply(1));
        assertEquals(1, cif.cnt());
        assertEquals("CachedIntFunction[stables=[StableValue.unset, StableValue[1]], original=" + cif + "]", cached.toString());
        assertThrows(IllegalArgumentException.class, () -> cached.apply(SIZE + 1));
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
        var cached = StableValue.newCachingIntFunction(SIZE, i -> i, factory);
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
        var cached = StableValue.newCachingIntFunction(SIZE, cif, null);
        assertThrows(UnsupportedOperationException.class, () -> cached.apply(1));
        assertEquals(1, cif.cnt());
        assertThrows(UnsupportedOperationException.class, () -> cached.apply(1));
        assertEquals(2, cif.cnt());
        assertEquals("CachedIntFunction[stables=[StableValue.unset, StableValue.unset], original=" + cif + "]", cached.toString());
    }

}
