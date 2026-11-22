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
 * @modules java.base/jdk.internal.foreign:+open java.base/jdk.internal.access java.base/jdk.internal.misc java.base/jdk.internal.util
 * @library /test/lib
 * @build TestConfinedSegmentPoolUtils
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=-1 TestConfinedSegmentPool
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=0 TestConfinedSegmentPool
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=1 TestConfinedSegmentPool
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=2 TestConfinedSegmentPool
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=3 TestConfinedSegmentPool
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=4 TestConfinedSegmentPool
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=5 TestConfinedSegmentPool
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=6 TestConfinedSegmentPool
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=7 TestConfinedSegmentPool
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.size=20 TestConfinedSegmentPool
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.count=0 TestConfinedSegmentPool
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.count=1 TestConfinedSegmentPool
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.count=3 TestConfinedSegmentPool
 * @run junit/othervm -Djdk.internal.foreign.native.confined.pool.power.count=-1 TestConfinedSegmentPool
 */

import jdk.internal.foreign.ConfinedSegmentPool;
import jdk.internal.misc.Unsafe;
import jdk.internal.util.Architecture;
import jdk.internal.util.OperatingSystem;
import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.IllegalStateException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class TestConfinedSegmentPool {

    static final long POOLED_MEMORY_SIZE = TestConfinedSegmentPoolUtils.POOLED_MEMORY_SIZE;
    static final int THREAD_POOL_COUNT = TestConfinedSegmentPoolUtils.THREAD_POOL_COUNT;

    static final boolean POOL_ACCOMMODATES_TWO_LONGS =
            TestConfinedSegmentPoolUtils.isPoolEnabled() && POOLED_MEMORY_SIZE >= Long.BYTES * 2;

    @ParameterizedTest
    @MethodSource("threadFactories")
    void basic(String name, Thread.Builder threadBuilder) throws Throwable {
        AtomicReference<long[]> threadPools = new AtomicReference<>();
        Thread thread = TestConfinedSegmentPoolUtils.runOn(threadBuilder, () -> {
            // Virtual threads are using the underlying carrier thread's pool so
            // there might already be a pool there.
            if (!Thread.currentThread().isVirtual()) {
                assertEquals(0, TestConfinedSegmentPoolUtils.currentPool());
            }

            long firstAddress;
            try (Arena arena = Arena.ofConfined()) {
                long allocator = TestConfinedSegmentPoolUtils.currentPool();
                if (!Thread.currentThread().isVirtual()) {
                    assertEquals(0, allocator);
                }

                MemorySegment firstSegment = arena.allocate(ValueLayout.JAVA_LONG);
                MemorySegment secondSegment = arena.allocate(ValueLayout.JAVA_LONG);
                firstAddress = firstSegment.address();
                if (POOL_ACCOMMODATES_TWO_LONGS) {
                    assertEquals(firstAddress + ValueLayout.JAVA_LONG.byteSize(), secondSegment.address());
                }
                firstSegment.set(ValueLayout.JAVA_LONG, 0, -1L);
                secondSegment.set(ValueLayout.JAVA_LONG, 0, -1L);
            }

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment firstSegment = arena.allocate(ValueLayout.JAVA_LONG);
                MemorySegment secondSegment = arena.allocate(ValueLayout.JAVA_LONG);
                if (POOL_ACCOMMODATES_TWO_LONGS) {
                    assertEquals(firstAddress, firstSegment.address());
                    assertEquals(firstAddress + ValueLayout.JAVA_LONG.byteSize(), secondSegment.address());
                }
                assertEquals(0L, firstSegment.get(ValueLayout.JAVA_LONG, 0));
                assertEquals(0L, secondSegment.get(ValueLayout.JAVA_LONG, 0));
            }

            if (!Thread.currentThread().isVirtual()) {
                threadPools.set(TestConfinedSegmentPoolUtils.confinedMemoryPools(Thread.currentThread()));
            }
        });

        if (!thread.isVirtual()) {
            long[] pools = threadPools.get();
            if (TestConfinedSegmentPoolUtils.isPoolEnabled()) {
                assertNotNull(pools);
                // Thread-exit cleanup must clear every cache entry.
                assertArrayEquals(new long[THREAD_POOL_COUNT], pools);
                // Thread-exit cleanup must clear the array reference
                assertNull(TestConfinedSegmentPoolUtils.confinedMemoryPools(thread));
            } else {
                // No pool array was ever allocated
                assertNull(pools);
            }
        }
    }

    static Stream<Arguments> threadFactories() {
        return Stream.of(
                Arguments.of("platform", Thread.ofPlatform()),
                Arguments.of("virtual", Thread.ofVirtual()));
    }

    @Test
    void cachedSegmentScope() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
            assertSame(arena.scope(), segment.scope());
        }
    }

    @Test
    void cachedSegmentScopeVt() {
       VThreadRunner.run(this::cachedSegmentScope);
    }

    @Test
    void cachedSegmentIsClosedWithArena() {
        Arena arena = Arena.ofConfined();
        MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
        segment.set(ValueLayout.JAVA_LONG, 0, 42L);

        arena.close();

        assertFalse(segment.scope().isAlive());
        assertThrows(IllegalStateException.class,
                () -> segment.get(ValueLayout.JAVA_LONG, 0));
        assertThrows(IllegalStateException.class,
                () -> segment.set(ValueLayout.JAVA_LONG, 0, -1L));
    }

    @Test
    void cachedSegmentIsClosedWithArenaVt() {
        VThreadRunner.run(this::cachedSegmentIsClosedWithArena);
    }

    @Test
    void closedCachedSegmentCannotAccessReusedSlot() {
        if (POOL_ACCOMMODATES_TWO_LONGS) {
            MemorySegment firstSegment;
            long firstAddress;
            try (Arena firstArena = Arena.ofConfined()) {
                firstSegment = firstArena.allocate(ValueLayout.JAVA_LONG);
                firstAddress = firstSegment.address();
                firstSegment.set(ValueLayout.JAVA_LONG, 0, 42L);
            }

            try (Arena secondArena = Arena.ofConfined()) {
                MemorySegment secondSegment = secondArena.allocate(ValueLayout.JAVA_LONG);
                assertEquals(firstAddress, secondSegment.address());
                secondSegment.set(ValueLayout.JAVA_LONG, 0, -1L);
                assertThrows(IllegalStateException.class,
                        () -> firstSegment.get(ValueLayout.JAVA_LONG, 0));
                assertThrows(IllegalStateException.class,
                        () -> firstSegment.set(ValueLayout.JAVA_LONG, 0, 0L));
            }
        }
    }

    @Test
    void closedCachedSegmentCannotAccessReusedSlotVt() {
        VThreadRunner.run(this::closedCachedSegmentCannotAccessReusedSlot);
    }

    @Test
    void outOfOrderClose() {
        Arena firstArena = Arena.ofConfined();
        MemorySegment firstSegment = firstArena.allocate(ValueLayout.JAVA_LONG);
        long firstAddress = firstSegment.address();
        firstSegment.set(ValueLayout.JAVA_LONG, 0, -1L);

        Arena secondArena = Arena.ofConfined();
        MemorySegment secondSegment = secondArena.allocate(ValueLayout.JAVA_LONG);
        secondSegment.set(ValueLayout.JAVA_LONG, 0, 42L);

        firstArena.close();

        try (Arena thirdArena = Arena.ofConfined()) {
            MemorySegment thirdSegment = thirdArena.allocate(ValueLayout.JAVA_LONG);
            if (POOL_ACCOMMODATES_TWO_LONGS) {
                assertEquals(firstAddress, thirdSegment.address());
            }
            assertEquals(0L, thirdSegment.get(ValueLayout.JAVA_LONG, 0));
            assertEquals(42L, secondSegment.get(ValueLayout.JAVA_LONG, 0));
        }

        secondArena.close();
    }

    @Test
    void outOfOrderCloseVt() {
        VThreadRunner.run(this::outOfOrderClose);
    }

    @Test
    void scopesAreUnique() {
        Arena firstArena = Arena.ofConfined();
        Arena secondArena = Arena.ofConfined();
        firstArena.close();
        try (Arena thirdArena = Arena.ofConfined()) {
            var scopes = Stream.of(firstArena, secondArena, thirdArena)
                    .map(Arena::scope)
                    .collect(Collectors.toSet());
            assertEquals(3, scopes.size());
        }
        secondArena.close();
    }

    @Test
    void scopesAreUniqueVt() {
        VThreadRunner.run(this::scopesAreUnique);
    }

    @Test
    void zeroing() {
        assumeTrue(TestConfinedSegmentPoolUtils.isPoolEnabled());
        MemorySegment zeroes = MemorySegment.ofArray(new byte[Math.toIntExact(POOLED_MEMORY_SIZE)]);
        for (long size : zeroingSizes()) {
            try (Arena thirdArena = Arena.ofConfined()) {
                MemorySegment segment = thirdArena.allocate(ValueLayout.JAVA_BYTE, size);
                segment.fill((byte) 0xAA);
            }
            try (Arena thirdArena = Arena.ofConfined()) {
                MemorySegment segment = thirdArena.allocate(ValueLayout.JAVA_BYTE, POOLED_MEMORY_SIZE);
                assertEquals(-1L, segment.mismatch(zeroes), "used size: " + size);
            }
        }
    }

    @Test
    void zeroingVt() {
        VThreadRunner.run(this::zeroing);
    }

    @Test
    void cleanerThreadCannotCloseCachedArena() throws Exception {
        AtomicReference<Thread> cleanerThreadRef = new AtomicReference<>();
        Cleaner cleaner = Cleaner.create(runnable -> {
            Thread cleanerThread = new Thread(runnable, "TestConfinedSegmentPool-Cleaner");
            cleanerThread.setDaemon(true);
            cleanerThreadRef.set(cleanerThread);
            return cleanerThread;
        });
        CountDownLatch cleanupLatch = new CountDownLatch(1);
        AtomicReference<Thread> cleanupThreadRef = new AtomicReference<>();
        AtomicReference<Throwable> failureRef = new AtomicReference<>();

        Arena arena = Arena.ofConfined();
        MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
        segment.set(ValueLayout.JAVA_LONG, 0, 42L);

        try {
            Cleaner.Cleanable cleanable = registerCleaner(cleaner, () -> {
                cleanupThreadRef.set(Thread.currentThread());
                try {
                    arena.close();
                } catch (Throwable ex) {
                    failureRef.set(ex);
                } finally {
                    cleanupLatch.countDown();
                }
            });

            awaitCleaner(cleanupLatch);

            assertSame(cleanerThreadRef.get(), cleanupThreadRef.get());
            assertNotNull(failureRef.get());
            assertEquals(WrongThreadException.class, failureRef.get().getClass());
            assertTrue(arena.scope().isAlive());
            assertEquals(42L, segment.get(ValueLayout.JAVA_LONG, 0));

            Reference.reachabilityFence(cleanable);
        } finally {
            if (arena.scope().isAlive()) {
                arena.close();
            }
        }
    }

    @Test
    void cleanerThreadCannotCloseCachedArenaVt() throws Exception {
        VThreadRunner.run(this::cleanerThreadCannotCloseCachedArena);
    }

    @Test
    void noPoolAllocated() {
        assumeTrue(TestConfinedSegmentPoolUtils.isPoolEnabled());
        long pool;
        try (Arena arena = Arena.ofConfined()) {
            pool = arena.allocate(1).address();
        }
        assertEquals(pool, TestConfinedSegmentPoolUtils.currentPool());
    }

    @Test
    void noPoolAllocatedVt() {
        VThreadRunner.run(this::noPoolAllocated);
    }

    @Test
    void fallbackAfterAcquirePool() {
        assumeTrue(TestConfinedSegmentPoolUtils.isPoolEnabled());
        try (Arena arena = Arena.ofConfined()) {
            assertEquals(0L, confinedSessionSp(arena));
            // From the pool
            arena.allocate(1);
            assertEquals(1L, confinedSessionSp(arena));
            // The full-size allocation no longer fits after consuming one byte and
            // must use the regular allocator.
            arena.allocate(POOLED_MEMORY_SIZE, 1);
            assertEquals(1L, confinedSessionSp(arena));
        }
    }

    @Test
    void fallbackAfterAcquirePoolVt() {
        VThreadRunner.run(this::fallbackAfterAcquirePool);
    }

    @Test
    void uniqueZeroAddresses() {
        assumeTrue(TestConfinedSegmentPoolUtils.isPoolEnabled());
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment first = arena.allocate(0, 1);
            MemorySegment second = arena.allocate(0, 1);
            assertEquals(0, first.byteSize());
            assertEquals(0, second.byteSize());
            assertNotEquals(first.address(), second.address());
        }
    }

    @Test
    void uniqueZeroAddressesVt() {
        VThreadRunner.run(this::uniqueZeroAddresses);
    }

    @Test
    void availablePoolsAreCleanedUpOnThreadExit() throws Throwable {
        assumeTrue(TestConfinedSegmentPoolUtils.isPoolEnabled());

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<long[]> threadPools = new AtomicReference<>();

        Thread thread = Thread.ofPlatform().unstarted(() -> {
            Arena[] cached = new Arena[THREAD_POOL_COUNT];

            try {
                allocateOneByte(cached);
                closeAll(cached); // One non-zero entry for each configured cache slot

                long[] pools = TestConfinedSegmentPoolUtils.confinedMemoryPools(Thread.currentThread());
                assertNotNull(pools);
                threadPools.set(pools);
                assertEquals(THREAD_POOL_COUNT, Arrays.stream(pools).filter(p -> p != 0).count());
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });

        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(10));

        assertFalse(thread.isAlive(), "platform thread did not terminate");
        if (failure.get() != null) {
            throw failure.get();
        }

        // Thread-exit cleanup must clear every cache entry.
        assertArrayEquals(new long[THREAD_POOL_COUNT], threadPools.get());
        // Thread-exit cleanup must clear the array reference
        assertNull(TestConfinedSegmentPoolUtils.confinedMemoryPools(thread));
    }

    @Test
    void threadExitCleanupDoesNotFreeAcquiredPool() throws Throwable {
        assumeTrue(TestConfinedSegmentPoolUtils.isPoolEnabled());

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = Thread.ofPlatform().unstarted(() -> {
            try {
                long pool;
                try (Arena scratch = Arena.ofConfined()) {
                    pool = scratch.allocate(1).address();
                }
                assertEquals(pool, TestConfinedSegmentPoolUtils.currentPool());

                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment segment = arena.allocate(ValueLayout.JAVA_BYTE);
                    assertEquals(pool, segment.address());
                    assertEquals(0, TestConfinedSegmentPoolUtils.currentPool());
                    segment.set(ValueLayout.JAVA_BYTE, 0, (byte) 42);

                    // Simulate thread-exit cleanup while the pool is detached and
                    // exclusively owned by this still-open arena.
                    ConfinedSegmentPool.threadTerminated(TestConfinedSegmentPoolUtils.confinedMemoryPools(Thread.currentThread()));
                    assertEquals((byte) 42, segment.get(ValueLayout.JAVA_BYTE, 0));
                }
                assertEquals(pool, TestConfinedSegmentPoolUtils.currentPool());
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });

        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(10));

        assertFalse(thread.isAlive(), "platform thread did not terminate");
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    @Test
    void cacheSaturation() {
        assumeTrue(TestConfinedSegmentPoolUtils.isPoolEnabled());
        testCacheSaturation();
    }

    @Test
    void cacheSaturationVt() {
        assumeTrue(TestConfinedSegmentPoolUtils.isPoolEnabled());
        VThreadRunner.run(this::testCacheSaturation);
    }

    // This test can only be run on certain platforms and we are using an
    // address alias to simulate negative values in order to test the inner workings
    // of a confined arena using such an address.
    @Test
    void negativeAddress() throws Throwable {
        assumeTrue(TestConfinedSegmentPoolUtils.isPoolEnabled());
        // Tagged memory can only be used on Aarch64
        assumeTrue(Architecture.isAARCH64());
        // Top Byte Ignore (TBI) is only guaranteed on these OSes
        assumeTrue(OperatingSystem.isLinux() || OperatingSystem.isMacOS());

        // Run on a fresh platform thread so that we can manipulate the cache
        // freely and isolated from the main thread.
        TestConfinedSegmentPoolUtils.runOn(Thread.ofPlatform(), () -> {
            final Unsafe u = Unsafe.getUnsafe();

            long originalAddress = 0;
            long negativeAliasAddress = 0;
            long[] pools = null;

            try {
                originalAddress = u.allocateMemory(POOLED_MEMORY_SIZE);
                negativeAliasAddress = originalAddress | Long.MIN_VALUE;
                pools = TestConfinedSegmentPoolUtils.getOrCreateConfinedMemoryPools(
                        Thread.currentThread(), THREAD_POOL_COUNT);

                u.setMemory(originalAddress, POOLED_MEMORY_SIZE, (byte) 0);
                pools[0] = negativeAliasAddress;

                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment segment = arena.allocate(1, 1);
                    assertEquals(negativeAliasAddress, segment.address());
                    segment.set(ValueLayout.JAVA_BYTE, 0, (byte) 42);
                    assertEquals((byte) 42, u.getByte(originalAddress));
                }

                assertEquals(negativeAliasAddress, pools[0]);
                assertEquals((byte) 0, u.getByte(originalAddress));
            } finally {
                // Prevent thread-exit cleanup from freeing the alias as we cannot
                // directly free memory using a tagged address.
                if (pools != null) {
                    Arrays.fill(pools, 0);
                }
                if (originalAddress != 0) {
                    u.freeMemory(originalAddress);
                }
            }
        });
    }

    private void testCacheSaturation() {
        Arena[] initial = new Arena[THREAD_POOL_COUNT + 1];
        Arena[] verification = new Arena[THREAD_POOL_COUNT];

        try {
            long[] addresses = allocateOneByte(initial);

            // The first releases fill every configured cache slot.
            for (int i = 0; i < THREAD_POOL_COUNT; i++) {
                initial[i].close();
            }

            // One further release must free its pool.
            initial[THREAD_POOL_COUNT].close();

            // All cached pools must remain reusable.
            long[] expected = Arrays.copyOf(addresses, THREAD_POOL_COUNT);
            long[] actual = allocateOneByte(verification);
            Arrays.sort(expected);
            Arrays.sort(actual);
            assertArrayEquals(expected, actual);
        } finally {
            closeAll(initial);
            closeAll(verification);
        }
    }

    static long[] allocateOneByte(Arena[] arenas) {
        long[] addresses = new long[arenas.length];
        for (int i = 0; i < arenas.length; i++) {
            arenas[i] = Arena.ofConfined();
            addresses[i] = arenas[i].allocate(ValueLayout.JAVA_BYTE).address();
        }
        return addresses;
    }

    static void closeAll(Arena[] arenas) {
        for (Arena arena : arenas) {
            if (arena != null && arena.scope().isAlive()) {
                arena.close();
            }
        }
    }

    static long confinedSessionSp(Arena arena) {

        final class Holder {

            private static Field poolSpField;

            static Field getOrSet(Arena arena) {
                Field poolSpField = Holder.poolSpField;
                if (poolSpField == null) {
                    try {
                        Holder.poolSpField = poolSpField = arena.getClass().getDeclaredField("poolSp");
                        poolSpField.setAccessible(true);
                    }  catch (ReflectiveOperationException ex) {
                        throw new AssertionError(ex);
                    }
                }
                return poolSpField;
            }
        }

        try {
            return Holder.getOrSet(arena).getLong(arena);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static long[] zeroingSizes() {
        return LongStream.of(0, 1, 7, 8, 9, 15, 16, 17,
                        31, 32, 33, 63, 64, 65,
                        POOLED_MEMORY_SIZE / 2,
                        POOLED_MEMORY_SIZE - 1,
                        POOLED_MEMORY_SIZE)
                .filter(size -> size >= 0 && size <= POOLED_MEMORY_SIZE)
                .distinct()
                .toArray();
    }

    static void awaitCleaner(CountDownLatch latch) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            System.gc();
            if (latch.await(10, TimeUnit.MILLISECONDS)) {
                return;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        fail("Cleaner did not run");
    }

    static Cleaner.Cleanable registerCleaner(Cleaner cleaner, Runnable action) {
        return cleaner.register(new Object(), action);
    }

}
