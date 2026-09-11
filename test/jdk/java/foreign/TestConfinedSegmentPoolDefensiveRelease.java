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
 * @requires vm.flavor != "zero"
 * @modules java.base/jdk.internal.foreign:+open java.base/java.lang:+open java.base/jdk.internal.access
 * @library /test/lib
 * @build TestConfinedSegmentPoolUtils
 * @run junit                                                                            TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=0              TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=1              TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=2              TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=3              TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=4              TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=5              TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=6              TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=7              TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=-1             TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=23847682736221 TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=TEXT           TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm -Dsun.nio.PageAlignDirectMemory=true
 *                    -Djdk.internal.foreign.native.confined.pool.power.size=PAGE_ALIGN
 *                    TestConfinedSegmentPoolDefensiveRelease
 */

import jdk.internal.foreign.ConfinedSegmentPool;
import jdk.test.lib.thread.VThreadScheduler;
import org.junit.jupiter.api.Test;

import java.lang.IllegalStateException;
import java.lang.foreign.Arena;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class TestConfinedSegmentPoolDefensiveRelease {

    static final Method RELEASE = releaseMethod();

    @Test
    void releaseWithNoPreviousAcquire() throws Throwable {
        TestConfinedSegmentPoolUtils.runOn(Thread.ofPlatform(), () ->
                assertThrows(IllegalStateException.class, () -> release(0, 1)));
    }

    @Test
    void releaseWithNoPreviousAcquireVt() throws Throwable {
        testOnVirtualThreadWithUntouchedCarrierThread(() -> {
            assertThrows(IllegalStateException.class, () -> release(0, 1));
        });
    }

    @Test
    void releaseAfterRelease() throws Throwable {
        testOnUntouchedThread(pool -> assertThrows(IllegalStateException.class, () -> release(pool, 1)));
    }

    @Test
    void releaseAfterReleaseVt() throws Throwable {
        assumeTrue(ConfinedSegmentPool.pooledMemorySize() > 0);
        testOnVirtualThreadWithUntouchedCarrierThread(() -> {
            long pool;
            try (Arena arena = Arena.ofConfined()) {
                pool = arena.allocate(1).address();
            }

            // The first allocation starts at the pool base, and closing the
            // arena returns that pool to the dedicated carrier.
            assertEquals(pool, TestConfinedSegmentPoolUtils.currentPool());

            assertThrows(IllegalStateException.class,
                    () -> release(pool, 1));
        });
    }

    @Test
    void releaseIllegalSize() throws Throwable {
        // Only test with pooling enabled
        assumeTrue(ConfinedSegmentPool.pooledMemorySize() > 0);
        testOnUntouchedThread(pool -> {
            try (Arena arena = Arena.ofConfined()) {
                arena.allocate(1);
                assertThrows(IllegalStateException.class, () -> release(pool, ConfinedSegmentPool.pooledMemorySize() + 1));
                assertThrows(IllegalStateException.class, () -> release(pool, -1));
                assertEquals(0 /* acquired and detached */,
                        TestConfinedSegmentPoolUtils.currentPool());
            }
            assertEquals(pool /* released */, TestConfinedSegmentPoolUtils.currentPool());
        });
    }

    @Test
    void releaseIllegalSizeVt() throws Throwable {
        assumeTrue(ConfinedSegmentPool.pooledMemorySize() > 0);
        testOnVirtualThreadWithUntouchedCarrierThread(() -> {
            long pool;
            try (Arena scratch = Arena.ofConfined()) {
                pool = scratch.allocate(1).address();
            }
            assertEquals(pool, TestConfinedSegmentPoolUtils.currentPool());

            try (Arena arena = Arena.ofConfined()) {
                assertEquals(pool, arena.allocate(1).address());
                assertThrows(IllegalStateException.class, () -> release(pool, ConfinedSegmentPool.pooledMemorySize() + 1));
                assertThrows(IllegalStateException.class, () -> release(pool, -1));
                assertEquals(0 /* acquired but not remembered */,
                        TestConfinedSegmentPoolUtils.currentPool());
            }
            assertEquals(pool /* released */, TestConfinedSegmentPoolUtils.currentPool());
        });
    }

    static void testOnUntouchedThread(LongConsumer c) throws Throwable {
        // Only test with pooling enabled
        assumeTrue(ConfinedSegmentPool.pooledMemorySize() > 0);
        TestConfinedSegmentPoolUtils.runOn(Thread.ofPlatform(), () -> {
            try (Arena arena = Arena.ofConfined()) {
                arena.allocate(1);
            }
            long pool = TestConfinedSegmentPoolUtils.currentPool();
            assertNotEquals(0L, pool, "Pool was not allocated");
            c.accept(pool);
        });
    }

    static void testOnVirtualThreadWithUntouchedCarrierThread(Runnable action)
            throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();

        ThreadFactory carrierFactory = task -> Thread.ofPlatform()
                        .name("TestConfinedSegmentPool-carrier")
                        .unstarted(task);

        try (ExecutorService scheduler = Executors.newSingleThreadExecutor(carrierFactory)) {
            Thread thread = VThreadScheduler.virtualThreadFactory(scheduler)
                    .newThread(() -> {
                        try {
                            action.run();
                        } catch (Throwable throwable) {
                            failure.set(throwable);
                        }
                    });

            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        TestConfinedSegmentPoolUtils.rethrowIfFailed(failure.get());
    }

    static void release(long pool, long size) throws Throwable {
        try {
            RELEASE.invoke(null, pool, size);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static Method releaseMethod() {
        try {
            Method method = ConfinedSegmentPool.class.getDeclaredMethod("release", long.class, long.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

}
