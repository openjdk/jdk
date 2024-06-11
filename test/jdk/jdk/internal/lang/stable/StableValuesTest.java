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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class StableValuesTest {

    @Test
    void ofMemoized() {
        StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(() -> 42);
        Supplier<Integer> memoizeded = StableValues.memoizedSupplier(cs, null);
        assertEquals(42, memoizeded.get());
        assertEquals(1, cs.cnt());
        assertEquals(42, memoizeded.get());
        assertEquals(1, cs.cnt());
        assertEquals("MemoizedSupplier[stable=StableValue[42], original=" + cs + "]", memoizeded.toString());
    }

    @Test
    void ofMemoizedBackground() {

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
        Supplier<Integer> memoizeded = StableValues.memoizedSupplier(() -> 42, factory);
        while (cnt.get() < 1) {
            Thread.onSpinWait();
        }
        assertEquals(42, memoizeded.get());
    }

    @Test
    void ofList() {
        List<StableValue<Integer>> list = StableValues.ofList(13);
        assertEquals(13, list.size());
        // Check, every StableValue is distinct
        Map<StableValue<Integer>, Boolean> idMap = new IdentityHashMap<>();
        list.forEach(e -> idMap.put(e, true));
        assertEquals(13, idMap.size());
    }

    @Test
    void ofMap() {
        Map<Integer, StableValue<Integer>> map = StableValues.ofMap(Set.of(1, 2, 3));
        assertEquals(3, map.size());
        // Check, every StableValue is distinct
        Map<StableValue<Integer>, Boolean> idMap = new IdentityHashMap<>();
        map.forEach((k, v) -> idMap.put(v, true));
        assertEquals(3, idMap.size());
    }

}
