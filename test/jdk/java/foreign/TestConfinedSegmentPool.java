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
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    TestConfinedSegmentPool
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.arena.power.pool-slot-size=2
 *                    TestConfinedSegmentPool
 */

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

final class TestConfinedSegmentPool {

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

    static final boolean IS_POOL_ACCOMODATES_LONG =
            Integer.getInteger("java.lang.foreign.native.confined.arena.power.pool-slot-size", 6) >= 4 ;

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
                    assertEquals("CachedArena", arena.getClass().getSimpleName());
                    Object allocator = threadAllocator(Thread.currentThread());
                    assertNotNull(allocator);
                    assertTrue(backingArena(allocator).scope().isAlive());
                    allocatorRef.set(allocator);

                    MemorySegment firstSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    MemorySegment secondSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    firstAddress = firstSegment.address();
                    if (IS_POOL_ACCOMODATES_LONG) {
                        assertEquals(secondSegment.address(), firstAddress + ValueLayout.JAVA_LONG.byteSize());
                    }
                    firstSegment.set(ValueLayout.JAVA_LONG, 0, -1L);
                    secondSegment.set(ValueLayout.JAVA_LONG, 0, -1L);
                }

                try (Arena arena = Arena.ofConfined()) {
                    assertEquals("CachedArena", arena.getClass().getSimpleName());
                    MemorySegment firstSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    MemorySegment secondSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    if (IS_POOL_ACCOMODATES_LONG) {
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
                if (threadAllocator(thread) == null) {
                    break;
                }
                LockSupport.parkNanos(1_000_000L);
            }
        }

        assertNull(name, threadAllocator(thread));
    }

    static Stream<Arguments> threadFactories() {
        return Stream.of(
                Arguments.of("platform", Thread.ofPlatform()),
                Arguments.of("virtual", Thread.ofVirtual()));
    }

    static Object threadAllocator(Thread thread) throws ReflectiveOperationException {
        return THREAD_ALLOCATOR_FIELD.get(thread);
    }

    static Arena backingArena(Object allocator) throws ReflectiveOperationException {
        return (Arena) BACKING_ARENA_FIELD.get(allocator);
    }

    static int poolSlots() throws ReflectiveOperationException {
        return POOL_SLOTS_FIELD.getInt(null);
    }

    @Test
    void testCachedSegmentScope() {
        try (Arena arena = Arena.ofConfined()) {
            assertEquals("CachedArena", arena.getClass().getSimpleName());
            MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
            assertSame(arena.scope(), segment.scope());
        }
    }

    @Test
    void testCachedSegmentIsClosedWithArena() {
        Arena arena = Arena.ofConfined();
        assertEquals("CachedArena", arena.getClass().getSimpleName());
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
        if (IS_POOL_ACCOMODATES_LONG) {
            MemorySegment firstSegment;
            long firstAddress;
            try (Arena firstArena = Arena.ofConfined()) {
                assertEquals("CachedArena", firstArena.getClass().getSimpleName());
                firstSegment = firstArena.allocate(ValueLayout.JAVA_LONG);
                firstAddress = firstSegment.address();
                firstSegment.set(ValueLayout.JAVA_LONG, 0, 42L);
            }

            try (Arena secondArena = Arena.ofConfined()) {
                assertEquals("CachedArena", secondArena.getClass().getSimpleName());
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
            if (IS_POOL_ACCOMODATES_LONG) {
                assertEquals(thirdSegment.address(), firstAddress);
            }
            assertEquals(thirdSegment.get(ValueLayout.JAVA_LONG, 0), 0L);
            assertEquals(secondSegment.get(ValueLayout.JAVA_LONG, 0), 42L);
        }

        secondArena.close();
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
    void testCleanerThreadCannotCloseCachedArena() throws Exception {
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
        assertEquals("CachedArena", arena.getClass().getSimpleName());
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
