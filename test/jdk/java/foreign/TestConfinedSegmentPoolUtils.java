/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.ConfinedSegmentPool;

import java.util.concurrent.atomic.AtomicReference;

final class TestConfinedSegmentPoolUtils {

    static final int DISABLED = -1;
    static final String POOLED_MEMORY_SIZE_PROPERTY = "jdk.internal.foreign.native.confined.pool.power.size";
    static final String THREAD_POOL_COUNT_PROPERTY = "jdk.internal.foreign.native.confined.pool.power.count";

    static final long POOLED_MEMORY_SIZE = ConfinedSegmentPool.pooledMemorySize();
    static final int THREAD_POOL_COUNT = configuredPowerOfTwo(THREAD_POOL_COUNT_PROPERTY, 0, 3, 2);

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private TestConfinedSegmentPoolUtils() {
    }

    static boolean isPoolEnabled() {
        return POOLED_MEMORY_SIZE > 0 && THREAD_POOL_COUNT > 0;
    }

    static int configuredPowerOfTwo(String property, int minPower,
                                    int maxPower, int defaultPower) {
        int power = Integer.getInteger(property, defaultPower);
        return power < 0
                ? DISABLED
                : 1 << Math.clamp(power, minPower, maxPower);
    }

    static long currentPool() {
        if (!isPoolEnabled()) {
            return 0;
        }
        return currentPlatformPool(JLA.currentCarrierThread());
    }

    static long currentPlatformPool(Thread thread) {
        long[] pools = confinedMemoryPools(thread);
        if (pools != null) {
            for (long pool : pools) {
                if (pool != 0) {
                    return pool;
                }
            }
        }
        return 0;
    }

    static long[] confinedMemoryPools(Thread thread) {
        return JLA.getConfinedMemoryPools(thread);
    }

    static long[] getOrCreateConfinedMemoryPools(Thread thread, int poolCount) {
        return JLA.getOrCreateConfinedMemoryPools(thread, poolCount);
    }

    static Thread runOn(Thread.Builder builder, ThrowingRunnable action)
            throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = builder.start(() -> {
            try {
                action.run();
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        thread.join();
        rethrowIfFailed(failure.get());
        return thread;
    }

    static void rethrowIfFailed(Throwable failure) throws Throwable {
        if (failure != null) {
            throw failure;
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
