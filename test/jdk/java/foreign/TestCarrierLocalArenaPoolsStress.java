/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @summary Test TestCarrierLocalArenaPoolsStress
 * @library /test/lib
 * @modules java.base/jdk.internal.foreign
 * @modules java.base/jdk.internal.misc
 * @run junit TestCarrierLocalArenaPoolsStress
 */

import jdk.internal.foreign.CarrierLocalArenaPools;
import jdk.internal.misc.Unsafe;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

final class TestCarrierLocalArenaPoolsStress {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long POOL_SIZE = 64;
    private static final VarHandle LONG_HANDLE = JAVA_LONG.varHandle();

    /**
     * The objective of this test is to try to provoke a situation where threads are
     * competing to use allocated pooled memory and then trying to make sure no thread
     * can see the same shared memory another thread is using.
     */
    @Test
    void stress() throws InterruptedException {
        final long begin = System.nanoTime();

        System.out.println(duration(begin) + "EXPANDING VT FJP");

        // Encourage the VT ForkJoin pool to expand/contract so that VT:s will be allocated
        // on FJP threads that are later terminated.
        LongStream.range(0, Runtime.getRuntime().availableProcessors() * 2L)
                .parallel()
                // Using a CompletableFuture expands the FJP
                .forEach(_ -> Thread.ofVirtual().start(() -> {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException ie) {
                        throw new RuntimeException(ie);
                    }
                }));

        System.out.println(duration(begin) + "DONE EXPANDING");

        // Just use one pool variant as testing here is fairly expensive.
        //final CarrierLocalArenaPools pool = CarrierLocalArenaPools.create(POOL_SIZE);
        // Make sure it works for both virtual and platform threads (as they are handled differently)
        for (var threadBuilder : List.of(Thread.ofVirtual(), Thread.ofPlatform())) {
            final int noThreads = threadBuilder instanceof Thread.Builder.OfVirtual ? 1024 : 32;
            System.out.println(duration(begin) + "CREATING " + noThreads + " THREADS USING " + threadBuilder);
            final Thread[] threads = IntStream.range(0, noThreads).mapToObj(_ ->
                    threadBuilder.start(() -> {
                        /*final var seg = Arena.ofConfined().allocate(100);
                        final Arena arena = new ReusingArena(seg);*/
                        final long threadId = Thread.currentThread().threadId();
                        while (!Thread.interrupted()) {
                            for (int i = 0; i < 1_000_000; i++) {
                                //try (Arena arena = Arena.ofConfined()) {
                                //try (Arena arena = pool.take()) {
                                    // Try to assert no two threads get allocated the same memory region.
                                /*
                                    final MemorySegment segment = arena.allocate(JAVA_LONG);
                                    LONG_HANDLE.setVolatile(segment, 0L, threadId);
                                    assertEquals(threadId, (long) LONG_HANDLE.getVolatile(segment, 0L));
                                    */
                                final long adr = UNSAFE.allocateMemory(POOL_SIZE);
                                UNSAFE.putLongVolatile(null, adr, threadId);
                                long v = UNSAFE.getLongVolatile(null, adr);
                                assertEquals(threadId, v);
                                UNSAFE.freeMemory(adr);
                                //}
                            }
                            Thread.yield(); // make sure the driver thread gets a chance.
                        }
                    })).toArray(Thread[]::new);
            System.out.println(duration(begin) + "SLEEPING");
            Thread.sleep(Duration.of(10, SECONDS));
            System.out.println(duration(begin) + "INTERRUPTING");
            Arrays.stream(threads).forEach(
                    thread -> {
                        assertTrue(thread.isAlive());
                        thread.interrupt();
                    });
            System.out.println(duration(begin) + "DONE INTERRUPTING");

            // VTs are daemon threads ...
            Arrays.stream(threads).forEach(t -> {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            System.out.println(duration(begin) + "ALL THREADS COMPLETED");
        }
        System.out.println(duration(begin) + "DONE");
    }

    private static String duration(Long begin) {
        var duration = Duration.of(System.nanoTime() - begin, ChronoUnit.NANOS);
        long seconds = duration.toSeconds();
        int nanos = duration.toNanosPart();
        return (Thread.currentThread().isVirtual() ? "VT: " : "PT: ") +
                String.format("%3d:%09d ", seconds, nanos);
    }

    static final class ReusingArena implements Arena {

        private final MemorySegment segment;

        public ReusingArena(MemorySegment segment) {
            this.segment = segment;
        }

        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            return segment.asSlice(0, byteSize, byteAlignment);
        }

        @Override
        public MemorySegment.Scope scope() {
            return Arena.global().scope();
        }

        @Override
        public void close() {

        }
    }

}
