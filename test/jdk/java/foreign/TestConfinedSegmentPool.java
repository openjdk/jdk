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
 * @modules java.base/jdk.internal.access java.base/jdk.internal.foreign
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=0
 *                    TestConfinedSegmentPool
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=1
 *                    TestConfinedSegmentPool
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=2
 *                    TestConfinedSegmentPool
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=3
 *                    TestConfinedSegmentPool
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=4
 *                    TestConfinedSegmentPool
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=5
 *                    TestConfinedSegmentPool
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=6
 *                    TestConfinedSegmentPool
 */

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

final class TestConfinedSegmentPool {

    static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    static final Field THREAD_CONFINED_MEMORY_POOL;
    static final long POOLED_MEMORY_SIZE = JLA.pooledMemorySize();

    static {
        try {
            THREAD_CONFINED_MEMORY_POOL = Thread.class.getDeclaredField("confinedMemoryPool");
            THREAD_CONFINED_MEMORY_POOL.setAccessible(true);
            long pooledMemorySize = JLA.pooledMemorySize();
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static final boolean IS_POOL_ACCOMMODATES_TWO_LONGS = POOLED_MEMORY_SIZE >= Long.BYTES * 2;

    @ParameterizedTest
    @MethodSource("threadFactories")
    void basic(String name, Thread.Builder threadBuilder) throws Throwable {
        AtomicReference<Object> allocatorRef = new AtomicReference<>();
        AtomicReference<Throwable> failureRef = new AtomicReference<>();
        Thread thread = threadBuilder.factory().newThread(() -> {
            try {
                assertEquals(0, confinedMemoryPool(Thread.currentThread()));

                long firstAddress;
                try (Arena arena = Arena.ofConfined()) {
                    long allocator = confinedMemoryPool(Thread.currentThread());
                    assertEquals(0, allocator);
                    allocatorRef.set(allocator);

                    MemorySegment firstSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    MemorySegment secondSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    firstAddress = firstSegment.address();
                    if (IS_POOL_ACCOMMODATES_TWO_LONGS) {
                        assertEquals(secondSegment.address(), firstAddress + ValueLayout.JAVA_LONG.byteSize());
                    }
                    firstSegment.set(ValueLayout.JAVA_LONG, 0, -1L);
                    secondSegment.set(ValueLayout.JAVA_LONG, 0, -1L);
                }

                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment firstSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    MemorySegment secondSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    if (IS_POOL_ACCOMMODATES_TWO_LONGS) {
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

        if (thread.isVirtual()) {
            // Give the virtual thread some time to clean up
            long timeOut = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (System.nanoTime() < timeOut) {
                if (confinedMemoryPool(thread) == 0) {
                    break;
                }
                LockSupport.parkNanos(1_000_000L);
            }
        }

        assertEquals(name, 0, confinedMemoryPool(thread));
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
    void closedCachedSegmentCannotAccessReusedSlot() {
        if (IS_POOL_ACCOMMODATES_TWO_LONGS) {
            MemorySegment firstSegment;
            long firstAddress;
            try (Arena firstArena = Arena.ofConfined()) {
                firstSegment = firstArena.allocate(ValueLayout.JAVA_LONG);
                firstAddress = firstSegment.address();
                firstSegment.set(ValueLayout.JAVA_LONG, 0, 42L);
            }

            try (Arena secondArena = Arena.ofConfined()) {
                MemorySegment secondSegment = secondArena.allocate(ValueLayout.JAVA_LONG);
                assertEquals(secondSegment.address(), firstAddress);
                secondSegment.set(ValueLayout.JAVA_LONG, 0, -1L);
                assertThrows(IllegalStateException.class,
                        () -> firstSegment.get(ValueLayout.JAVA_LONG, 0));
                assertThrows(IllegalStateException.class,
                        () -> firstSegment.set(ValueLayout.JAVA_LONG, 0, 0L));
            }
        }
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
            if (IS_POOL_ACCOMMODATES_TWO_LONGS) {
                assertEquals(thirdSegment.address(), firstAddress);
            }
            assertEquals(thirdSegment.get(ValueLayout.JAVA_LONG, 0), 0L);
            assertEquals(secondSegment.get(ValueLayout.JAVA_LONG, 0), 42L);
        }

        secondArena.close();
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
    void zeroing() {
        for (int i = 0; i < POOLED_MEMORY_SIZE; i++) {
            try (Arena thirdArena = Arena.ofConfined()) {
                MemorySegment segment = thirdArena.allocate(ValueLayout.JAVA_BYTE, i);
                segment.fill((byte) 0xAA);
            }
            try (Arena thirdArena = Arena.ofConfined()) {
                MemorySegment segment = thirdArena.allocate(ValueLayout.JAVA_BYTE, POOLED_MEMORY_SIZE);
                for (int j = 0; j < POOLED_MEMORY_SIZE; j++) {
                    assertEquals(i + ", " + j, segment.get(ValueLayout.JAVA_BYTE, j), 0);
                }
            }
        }
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
    void noPoolAllocated() {
        if (!isPoolEnabled()) {
            try (Arena arena = Arena.ofConfined()) {
                arena.allocate(1);
            }
            // Make sure we didn't allocate a confined memory pool via the above
            // allocation or any other allocation in another test.
            assertEquals(0L, confinedMemoryPool(Thread.currentThread()));
        }
    }

    @Test
    void fallbackAfterAcquirePool() {
        if (isPoolEnabled()) {
            try (Arena arena = Arena.ofConfined()) {
                assertEquals(0L, confinedSessionSp(arena));
                // From the pool
                arena.allocate(1);
                assertEquals(1L, confinedSessionSp(arena));
                // Fallback allocation
                arena.allocate(16, 1024);
                assertEquals(1L, confinedSessionSp(arena));
            }
        }
    }

    @Test
    void uniqueZeroAddresses() {
        if (isPoolEnabled()) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment first = arena.allocate(0, 1);
                MemorySegment second = arena.allocate(0, 1);
                assertEquals(0, first.byteSize());
                assertEquals(0, second.byteSize());
                assertNotEquals(first.address(), second.address());
            }
        }
    }

    static long confinedMemoryPool(Thread thread) {
        try {
            return (long) THREAD_CONFINED_MEMORY_POOL.get(thread);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    static long confinedSessionSp(Arena arena) {

        final class Holder {

            private static Field sp;

            static Field getOrSet(Arena arena) {
                Field sp = Holder.sp;
                if (sp == null) {
                    try {
                        Holder.sp = sp = arena.scope().getClass().getDeclaredField("sp");
                        sp.setAccessible(true);
                    }  catch (ReflectiveOperationException ex) {
                        throw new AssertionError(ex);
                    }
                }
                return sp;
            }
        }

        try {
            return Holder.getOrSet(arena).getLong(arena.scope());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static boolean isPoolEnabled() {
        return POOLED_MEMORY_SIZE > 0;
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