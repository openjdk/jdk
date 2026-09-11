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

/*
 * @test
 * @modules java.base/jdk.internal.foreign java.base/jdk.internal.access
 * @build TestConfinedSegmentPoolUtils
 * @run junit                                                                           TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=0              TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=1              TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=2              TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=3              TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=4              TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=5              TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=6              TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=7              TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=20             TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=21             TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=24             TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=-1             TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=23847682736221 TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=TEXT           TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.count=0              TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.count=1              TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.count=2              TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.count=3              TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.count=4              TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.count=-1             TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.count=23847682736221 TestConfinedSegmentPoolConfig
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.count=TEXT           TestConfinedSegmentPoolConfig
 * @run junit/othervm -Dsun.nio.PageAlignDirectMemory=true
 *                    -Djdk.internal.foreign.native.confined.pool.power.size=PAGE_ALIGN
 *                    -Djdk.internal.foreign.native.confined.pool.power.count=PAGE_ALIGN
 *                    TestConfinedSegmentPoolConfig
 */

import jdk.internal.foreign.ConfinedSegmentPool;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class TestConfinedSegmentPoolConfig {

    static final int DISABLED = TestConfinedSegmentPoolUtils.DISABLED;
    static final String POOLED_MEMORY_SIZE_PROPERTY = TestConfinedSegmentPoolUtils.POOLED_MEMORY_SIZE_PROPERTY;
    static final String THREAD_POOL_COUNT_PROPERTY = TestConfinedSegmentPoolUtils.THREAD_POOL_COUNT_PROPERTY;

    @Test
    void configuration() throws Throwable {
        boolean pageAligned = "PAGE_ALIGN".equals(System.getProperty(POOLED_MEMORY_SIZE_PROPERTY));
        long configuredSize = pageAligned
                ? DISABLED
                : TestConfinedSegmentPoolUtils.configuredPowerOfTwo(POOLED_MEMORY_SIZE_PROPERTY, 3, 20, 6);
        int expectedCount = pageAligned
                ? DISABLED
                : TestConfinedSegmentPoolUtils.configuredPowerOfTwo(THREAD_POOL_COUNT_PROPERTY, 0, 3, 2);
        long expectedSize = configuredSize > 0 && expectedCount > 0
                ? configuredSize
                : DISABLED;

        assertEquals(expectedSize, ConfinedSegmentPool.pooledMemorySize());

        AtomicReference<long[]> threadPools = new AtomicReference<>();

        Thread thread = TestConfinedSegmentPoolUtils.runOn(Thread.ofPlatform(), () -> {
            try (Arena arena = Arena.ofConfined()) {
                arena.allocate(1);
            }

            long[] pools = TestConfinedSegmentPoolUtils.confinedMemoryPools(Thread.currentThread());
            threadPools.set(pools);
            if (expectedSize > 0 && expectedCount > 0) {
                assertNotNull(pools);
                assertEquals(expectedCount, pools.length);
                assertEquals(1, Arrays.stream(pools).filter(pool -> pool != 0).count());
            } else {
                assertNull(pools);
            }
        });

        long[] pools = threadPools.get();
        if (expectedSize > 0 && expectedCount > 0) {
            assertArrayEquals(new long[expectedCount], pools);
        } else {
            assertNull(pools);
        }
    }

}
