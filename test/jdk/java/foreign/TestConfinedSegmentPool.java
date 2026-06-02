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
 * @library /test/lib
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.arena.power.pool-slot-size=6
 *                    TestConfinedSegmentPool
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.arena.power.pool-slot-size=0
 *                    TestConfinedSegmentPool
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.arena.pool-slots=0
 *                    TestConfinedSegmentPool
 */

import jdk.test.lib.thread.VThreadScheduler;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

final class TestConfinedSegmentPool {

    static final String PROPERTY_PATH = "java.lang.foreign.native.confined.arena.";
    static final Field THREAD_ALLOCATOR_FIELD;
    static final Field BACKING_ARENA_FIELD;
    static final Field POOL_SLOTS_FIELD;
    static final Field POOL_SLOT_SIZE_FIELD;

    static {
        try {
            THREAD_ALLOCATOR_FIELD = Thread.class.getDeclaredField("confinedArenaAllocator");
            THREAD_ALLOCATOR_FIELD.setAccessible(true);
            Class<?> allocatorClass = Class.forName("jdk.internal.foreign.ThreadConfinedSegmentPool");
            BACKING_ARENA_FIELD = allocatorClass.getDeclaredField("backingArena");
            BACKING_ARENA_FIELD.setAccessible(true);
            POOL_SLOTS_FIELD = allocatorClass.getDeclaredField("POOL_SLOTS");
            POOL_SLOTS_FIELD.setAccessible(true);
            POOL_SLOT_SIZE_FIELD = allocatorClass.getDeclaredField("POOL_SLOT_SIZE");
            POOL_SLOT_SIZE_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Test
    void testNoAllocation() {
        try (Arena arena = Arena.ofConfined()) {
            assertCorrectArenaImpl(arena);
        }
    }

    @Test
    void testManyAllocations() {
        // Try a couple of times to also test recycled pool elements
        for (int i = 0; i < 4; i++) {
            try (Arena arena = Arena.ofConfined()) {
                for (int j = 0; j < poolSlotSize() * 2; j++) {
                    MemorySegment segment = arena.allocate(ValueLayout.JAVA_BYTE);
                    // Make sure the segment is zeroed out
                    assertEquals("At " + i + ", " + j + " (" + poolSlotSize() + ")",
                            (byte) 0, segment.get(ValueLayout.JAVA_BYTE, 0));
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("threadFactories")
    void testThreadLocalAllocator(String name, Thread.Builder threadBuilder) throws Throwable {
        AtomicReference<Object> allocatorRef = new AtomicReference<>();
        AtomicReference<Throwable> failureRef = new AtomicReference<>();
        Thread thread = threadBuilder.factory().newThread(() -> {
            try {
                assertNull(threadAllocator(Thread.currentThread()));

                long firstAddress;
                try (Arena arena = Arena.ofConfined()) {
                    assertCorrectArenaImpl(arena);
                    Object allocator = threadAllocator(Thread.currentThread());
                    if (isPoolEnabled()) {
                        assertNotNull(allocator);
                        assertTrue(backingArena(allocator).scope().isAlive());
                        allocatorRef.set(allocator);
                    } else {
                        assertNull(allocator);
                    }

                    MemorySegment firstSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    MemorySegment secondSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    firstAddress = firstSegment.address();
                    if (isPoolEnabled()) {
                        assertEquals(secondSegment.address(), firstAddress + ValueLayout.JAVA_LONG.byteSize());
                    }
                    firstSegment.set(ValueLayout.JAVA_LONG, 0, -1L);
                    secondSegment.set(ValueLayout.JAVA_LONG, 0, -1L);
                }

                try (Arena arena = Arena.ofConfined()) {
                    assertCorrectArenaImpl(arena);
                    MemorySegment firstSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    MemorySegment secondSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    if (isPoolEnabled()) {
                        assertEquals(firstSegment.address(), firstAddress);
                        assertEquals(secondSegment.address(), firstAddress + ValueLayout.JAVA_LONG.byteSize());
                    }
                    assertEquals(firstSegment.get(ValueLayout.JAVA_LONG, 0), 0L);
                    assertEquals(secondSegment.get(ValueLayout.JAVA_LONG, 0), 0L);
                }
            } catch (Throwable ex) {
                failureRef.set(ex);
            }
        });

        thread.start();
        thread.join();

        if (failureRef.get() != null) {
            throw failureRef.get();
        }

        awaitThreadAllocatorCleared(thread);
        assertNull(name, threadAllocator(thread));
        if (allocatorRef.get() != null) {
            assertFalse(backingArena(allocatorRef.get()).scope().isAlive());
        }
    }

    static Stream<Arguments> threadFactories() {
        return Stream.of(
                Arguments.of("platform", Thread.ofPlatform()),
                Arguments.of("virtual", Thread.ofVirtual()));
    }

    static Object threadAllocator(Thread thread) {
        return getOrThrow(() -> THREAD_ALLOCATOR_FIELD.get(thread));
    }

    static Arena backingArena(Object allocator) {
        return getOrThrow(() -> (Arena) BACKING_ARENA_FIELD.get(allocator));
    }

    static int poolSlots() {
        return getOrThrow(() -> POOL_SLOTS_FIELD.getInt(null));
    }

    static long poolSlotSize() {
        return getOrThrow(() -> POOL_SLOT_SIZE_FIELD.getLong(null));
    }

    interface ReflectiveOperation<T> {
        T reflect() throws ReflectiveOperationException;
    }

    static <T> T getOrThrow(ReflectiveOperation<T> op) {
        try {
            return op.reflect();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    static boolean isPoolEnabled() {
        return poolSlots() > 0 && poolSlotSize() > 1;
    }

    static void assertCorrectArenaImpl(Arena arena) {
        assertEquals(isPoolEnabled() ? "CachedArena" : "ArenaImpl", arena.getClass().getSimpleName());
    }

    @Test
    void testCachedSegmentScope() {
        try (Arena arena = Arena.ofConfined()) {
            assertCorrectArenaImpl(arena);
            MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
            assertSame(arena.scope(), segment.scope());
        }
    }

    @Test
    void testCachedSegmentIsClosedWithArena() {
        Arena arena = Arena.ofConfined();
        assertCorrectArenaImpl(arena);
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
    void testClosedCachedSegmentCannotAccessReusedSlot() {
        Assumptions.assumeTrue(isPoolEnabled(), "Pool not enabled");

        MemorySegment firstSegment;
        long firstAddress;
        try (Arena firstArena = Arena.ofConfined()) {
            assertCorrectArenaImpl(firstArena);
            firstSegment = firstArena.allocate(ValueLayout.JAVA_LONG);
            firstAddress = firstSegment.address();
            firstSegment.set(ValueLayout.JAVA_LONG, 0, 42L);
        }

        try (Arena secondArena = Arena.ofConfined()) {
            assertCorrectArenaImpl(secondArena);
            MemorySegment secondSegment = secondArena.allocate(ValueLayout.JAVA_LONG);
            assertEquals(secondSegment.address(), firstAddress);
            secondSegment.set(ValueLayout.JAVA_LONG, 0, -1L);
            assertThrows(IllegalStateException.class,
                    () -> firstSegment.get(ValueLayout.JAVA_LONG, 0));
            assertThrows(IllegalStateException.class,
                    () -> firstSegment.set(ValueLayout.JAVA_LONG, 0, 0L));
        }
    }

    @Test
    void testOutOfOrderClose() {
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
            if (isPoolEnabled()) {
                assertEquals(thirdSegment.address(), firstAddress);
            }
            assertEquals(thirdSegment.get(ValueLayout.JAVA_LONG, 0), 0L);
            assertEquals(secondSegment.get(ValueLayout.JAVA_LONG, 0), 42L);
        }

        secondArena.close();
    }

    @Test
    void testLargeAllocationDoesNotConsumePoolSlot() {
        Assumptions.assumeTrue(isPoolEnabled(), "Pool is disabled");
        long poolSlotSize = poolSlotSize();
        Assumptions.assumeTrue(poolSlotSize < (1 << 20), "Pool slot too large for fallback test");

        long pooledAddress;
        try (Arena arena = Arena.ofConfined()) {
            assertCorrectArenaImpl(arena);
            MemorySegment largeSegment = arena.allocate(poolSlotSize + 1);
            largeSegment.set(ValueLayout.JAVA_BYTE, 0, (byte) 42);

            MemorySegment pooledSegment = arena.allocate(ValueLayout.JAVA_BYTE);
            pooledAddress = pooledSegment.address();
            pooledSegment.set(ValueLayout.JAVA_BYTE, 0, (byte) 1);
        }

        try (Arena arena = Arena.ofConfined()) {
            assertCorrectArenaImpl(arena);
            MemorySegment pooledSegment = arena.allocate(ValueLayout.JAVA_BYTE);
            assertEquals(pooledSegment.address(), pooledAddress);
            assertEquals((byte) 0, pooledSegment.get(ValueLayout.JAVA_BYTE, 0));
        }
    }

    @Test
    void testPoolExhaustionFallsBack() {
        int poolSlots = poolSlots();
        Assumptions.assumeTrue(isPoolEnabled(), "Pool is disabled");

        List<Arena> arenas = new ArrayList<>();
        try {
            for (int i = 0; i < poolSlots; i++) {
                Arena arena = Arena.ofConfined();
                assertCorrectArenaImpl(arena);
                MemorySegment segment = arena.allocate(ValueLayout.JAVA_BYTE);
                assertSame(arena.scope(), segment.scope());
                segment.set(ValueLayout.JAVA_BYTE, 0, (byte) i);
                arenas.add(arena);
            }

            try (Arena overflowArena = Arena.ofConfined()) {
                assertCorrectArenaImpl(overflowArena);
                MemorySegment segment = overflowArena.allocate(ValueLayout.JAVA_BYTE);
                assertSame(overflowArena.scope(), segment.scope());
                segment.set(ValueLayout.JAVA_BYTE, 0, (byte) 42);
            }
        } finally {
            for (Arena arena : arenas) {
                if (arena.scope().isAlive()) {
                    arena.close();
                }
            }
        }
    }

    @Test
    void testAllocateFromReusesCachedSlot() {
        byte[] firstBytes = { 1, 2, 3, 4 };
        byte[] secondBytes = { 5, 6, 7, 8 };
        Assumptions.assumeTrue(isPoolEnabled(), "Pool is disabled");
        Assumptions.assumeTrue(poolSlotSize() >= firstBytes.length, "Pool slot too small");

        long firstAddress;
        try (Arena arena = Arena.ofConfined()) {
            assertCorrectArenaImpl(arena);
            MemorySegment segment = arena.allocateFrom(ValueLayout.JAVA_BYTE, firstBytes);
            firstAddress = segment.address();
            assertBytes(segment, firstBytes);
        }

        try (Arena arena = Arena.ofConfined()) {
            assertCorrectArenaImpl(arena);
            MemorySegment segment = arena.allocateFrom(ValueLayout.JAVA_BYTE, secondBytes);
            assertEquals(segment.address(), firstAddress);
            assertBytes(segment, secondBytes);
        }
    }

    @Test
    void testVirtualThreadCleanupWithCustomScheduler() throws Throwable {
        Assumptions.assumeTrue(VThreadScheduler.supportsCustomScheduler(), "No support for custom schedulers");

        ExecutorService scheduler = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<Object> allocatorRef = new AtomicReference<>();
            AtomicReference<Throwable> failureRef = new AtomicReference<>();
            ThreadFactory factory = VThreadScheduler.virtualThreadFactory(scheduler);
            Thread thread = factory.newThread(() -> {
                try (Arena arena = Arena.ofConfined()) {
                    assertCorrectArenaImpl(arena);
                    Object allocator = threadAllocator(Thread.currentThread());
                    if (isPoolEnabled()) {
                        assertNotNull(allocator);
                        assertTrue(backingArena(allocator).scope().isAlive());
                        allocatorRef.set(allocator);
                    } else {
                        assertNull(allocator);
                    }

                    MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
                    segment.set(ValueLayout.JAVA_LONG, 0, 42L);
                } catch (Throwable ex) {
                    failureRef.set(ex);
                }
            });

            thread.start();
            thread.join();

            if (failureRef.get() != null) {
                throw failureRef.get();
            }

            awaitThreadAllocatorCleared(thread);
            assertNull(threadAllocator(thread));
            Object allocator = allocatorRef.get();
            if (allocator != null) {
                assertFalse(backingArena(allocator).scope().isAlive());
            }
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void testScopesAreUnique() {
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
    void testCleanerThreadCannotCloseConfinedArena() {
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
        assertCorrectArenaImpl(arena);
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

    static void awaitThreadAllocatorCleared(Thread thread) throws ReflectiveOperationException {
        long timeOut = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < timeOut) {
            if (threadAllocator(thread) == null) {
                return;
            }
            LockSupport.parkNanos(1_000_000L);
        }
    }

    static void assertBytes(MemorySegment segment, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            assertEquals(bytes[i], segment.get(ValueLayout.JAVA_BYTE, i));
        }
    }

    static void awaitCleaner(CountDownLatch latch)  {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            System.gc();
            try {
                if (latch.await(10, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        fail("Cleaner did not run");
    }

    static Cleaner.Cleanable registerCleaner(Cleaner cleaner, Runnable action) {
        return cleaner.register(new Object(), action);
    }

}
