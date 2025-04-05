/*
 * Copyright (c) 2000, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.nio;

import jdk.internal.access.JavaLangRefAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;
import jdk.internal.misc.VM.BufferPool;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Access to bits, native and otherwise.
 */

class Bits {                            // package-private

    private Bits() { }


    // -- Swapping --

    static short swap(short x) {
        return Short.reverseBytes(x);
    }

    static char swap(char x) {
        return Character.reverseBytes(x);
    }

    static int swap(int x) {
        return Integer.reverseBytes(x);
    }

    static long swap(long x) {
        return Long.reverseBytes(x);
    }


    // -- Unsafe access --

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // -- Processor and memory-system properties --

    private static int PAGE_SIZE = -1;

    static int pageSize() {
        if (PAGE_SIZE == -1)
            PAGE_SIZE = UNSAFE.pageSize();
        return PAGE_SIZE;
    }

    static long pageCount(long size) {
        return (size + (long)pageSize() - 1L) / pageSize();
    }

    private static boolean UNALIGNED = UNSAFE.unalignedAccess();

    static boolean unaligned() {
        return UNALIGNED;
    }


    // -- Direct memory management --

    // A user-settable upper limit on the maximum amount of allocatable
    // direct buffer memory.  This value may be changed during VM
    // initialization if it is launched with "-XX:MaxDirectMemorySize=<size>".
    private static volatile long MAX_MEMORY = VM.maxDirectMemory();
    private static final AtomicLong RESERVED_MEMORY = new AtomicLong();
    private static final AtomicLong TOTAL_CAPACITY = new AtomicLong();
    private static final AtomicLong COUNT = new AtomicLong();
    private static volatile boolean MEMORY_LIMIT_SET;

    private static final Object RESERVE_SLOW_LOCK = new Object();

    // max. number of sleeps during try-reserving with exponentially
    // increasing delay before throwing OutOfMemoryError:
    // 1, 2, 4, 8, 16, 32, 64, 128, 256 (total 511 ms ~ 0.5 s)
    // which means that OOME will be thrown after 0.5 s of trying
    private static final int MAX_SLEEPS = 9;

    // These methods should be called whenever direct memory is allocated or
    // freed.  They allow the user to control the amount of direct memory
    // which a process may access.  All sizes are specified in bytes.
    static void reserveMemory(long size, long cap) {
        if (!MEMORY_LIMIT_SET && VM.initLevel() >= 1) {
            MAX_MEMORY = VM.maxDirectMemory();
            MEMORY_LIMIT_SET = true;
        }

        // Optimistic path: enough memory to satisfy allocation.
        if (tryReserveMemory(size, cap)) {
            return;
        }

        // Short on memory, with potentially many threads competing for it.
        // To alleviate progress races, acquire the lock and go slow.
        synchronized (RESERVE_SLOW_LOCK) {
            reserveMemorySlow(size, cap);
        }
    }

    static void reserveMemorySlow(long size, long cap) {
        // Slow path under the lock. This code would try to trigger cleanups and
        // sense if cleaning was performed. Since the failure mode is OOME,
        // there is no need to rush.
        //
        // If this code is modified, make sure a stress test like DirectBufferAllocTest
        // performs well.

        // Semi-optimistic attempt after acquiring the slow-path lock.
        if (tryReserveMemory(size, cap)) {
           return;
        }

        // No free memory. We need to trigger cleanups and wait for them to make progress.
        // This requires triggering the GC and waiting for eventual buffer cleanups
        // or the absence of any profitable cleanups.
        //
        // To do this efficiently, we need to wait for several activities to run:
        //   1. GC needs to discover dead references and hand them over to Reference
        //      processing thread. This activity can be asynchronous and can complete after
        //      we unblock from System.gc().
        //   2. Reference processing thread needs to process dead references and enqueue them
        //      to Cleaner thread. This activity is normally concurrent with the rest of
        //      Java code, and is subject to reference processing thread having time to process.
        //   3. Cleaner thread needs to process the enqueued references and call cleanables
        //      on dead buffers. Like (2), this activity is also concurrent, and relies on
        //      Cleaner getting time to act.
        //
        // It is somewhat simple to wait for Reference processing and Cleaner threads to be idle.
        // However, that is not a good indicator they have processed buffers since our last
        // System.gc() request: they may not have started yet after System.gc() unblocked,
        // or have not yet seen that previous step ran. It is Really Hard (tm) to coordinate
        // all these activities.
        //
        // Instead, we are checking directly if Cleaner have acted on since our last System.gc():
        // install the canary, call System.gc(), wait for canary to get processed (dead). This
        // signals that since our last call to System.gc(), steps (1) and (2) have finished, and
        // step (3) is currently in progress.
        //
        // The last bit is a corner case: since canary is not ordered with other buffer cleanups,
        // it is possible that canary gets dead before the rest of the buffers get cleaned. This
        // corner case would be handled with a normal retry attempt, after trying to allocate.
        // If allocation succeeds even after partial cleanup, we are done. If it does not, we get
        // to try again, this time reliably getting the results of the first cleanup run. Not
        // handling this case specially simplifies implementation.

        boolean interrupted = false;
        try {
            BufferCleaner.Canary canary = null;

            long sleepTime = 1;
            for (int sleeps = 0; sleeps < MAX_SLEEPS; sleeps++) {
                if (canary == null || canary.isDead()) {
                    // If canary is not yet initialized, we have not triggered a cleanup.
                    // If canary is dead, there was progress, and it was not enough.
                    // Trigger GC -> Reference processing -> Cleaner again.
                    canary = BufferCleaner.newCanary();
                    System.gc();
                }

                // Exponentially back off waiting for Cleaner to catch up.
                try {
                    Thread.sleep(sleepTime);
                    sleepTime *= 2;
                } catch (InterruptedException e) {
                    interrupted = true;
                }

                // See if we can satisfy the allocation now.
                if (tryReserveMemory(size, cap)) {
                    return;
                }
            }

            // No luck:
            throw new OutOfMemoryError
                ("Cannot reserve "
                 + size + " bytes of direct buffer memory (allocated: "
                 + RESERVED_MEMORY.get() + ", limit: " + MAX_MEMORY +")");

        } finally {
            if (interrupted) {
                // don't swallow interrupts
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean tryReserveMemory(long size, long cap) {

        // -XX:MaxDirectMemorySize limits the total capacity rather than the
        // actual memory usage, which will differ when buffers are page
        // aligned.
        long totalCap;
        while (cap <= MAX_MEMORY - (totalCap = TOTAL_CAPACITY.get())) {
            if (TOTAL_CAPACITY.compareAndSet(totalCap, totalCap + cap)) {
                RESERVED_MEMORY.addAndGet(size);
                COUNT.incrementAndGet();
                return true;
            }
        }

        return false;
    }


    static void unreserveMemory(long size, long cap) {
        long cnt = COUNT.decrementAndGet();
        long reservedMem = RESERVED_MEMORY.addAndGet(-size);
        long totalCap = TOTAL_CAPACITY.addAndGet(-cap);
        assert cnt >= 0 && reservedMem >= 0 && totalCap >= 0;
    }

    static final BufferPool BUFFER_POOL = new BufferPool() {
        @Override
        public String getName() {
            return "direct";
        }
        @Override
        public long getCount() {
            return Bits.COUNT.get();
        }
        @Override
        public long getTotalCapacity() {
            return Bits.TOTAL_CAPACITY.get();
        }
        @Override
        public long getMemoryUsed() {
            return Bits.RESERVED_MEMORY.get();
        }
    };

    // These numbers represent the point at which we have empirically
    // determined that the average cost of a JNI call exceeds the expense
    // of an element by element copy.  These numbers may change over time.
    static final int JNI_COPY_TO_ARRAY_THRESHOLD   = 6;
    static final int JNI_COPY_FROM_ARRAY_THRESHOLD = 6;
}
