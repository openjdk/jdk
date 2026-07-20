/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

    // max. number of sleeps during try-reserving with exponentially
    // increasing delay before throwing OutOfMemoryError:
    // 1, 2, 4, 8, 16, 32, 64, 128, 256 (total 511 ms ~ 0.5 s)
    // which means that OOME will be thrown after 0.5 s of trying
    private static final long INITIAL_SLEEP = 1;
    private static final int MAX_SLEEPS = 9;

    private static final Object RESERVE_SLOWPATH_LOCK = new Object();

    // Token for detecting whether some other thread has done a GC since the
    // last time the checking thread went around the retry-with-GC loop.
    private static int RESERVE_GC_EPOCH = 0; // Never negative.

    // These methods should be called whenever direct memory is allocated or
    // freed.  They allow the user to control the amount of direct memory
    // which a process may access.  All sizes are specified in bytes.
    static void reserveMemory(long size, long cap) {

        if (!MEMORY_LIMIT_SET && VM.initLevel() >= 1) {
            MAX_MEMORY = VM.maxDirectMemory();
            MEMORY_LIMIT_SET = true;
        }

        // optimist!
        if (tryReserveMemory(size, cap)) {
            return;
        }

        // Don't completely discard interruptions.  Instead, record them and
        // reapply when we're done here (whether successfully or OOME).
        boolean interrupted = false;
        try {
            // Keep trying to reserve until either succeed or there is no
            // further cleaning available from prior GCs. If the latter then
            // GC to hopefully find more cleaning to do. Once a thread GCs it
            // drops to the later retry with backoff loop.
            for (int cleanedEpoch = -1; true; ) {
                synchronized (RESERVE_SLOWPATH_LOCK) {
                    // Test if cleaning for prior GCs (from here) is complete.
                    // If so, GC to produce more cleaning work, and change
                    // the token to inform other threads that there may be
                    // more cleaning work to do.  This is done under the lock
                    // to close a race.  We could have multiple threads pass
                    // the test "simultaneously", resulting in back-to-back
                    // GCs.  For a STW GC the window is small, but for a
                    // concurrent GC it's quite large. If a thread were to
                    // somehow be stuck trying to take the lock while enough
                    // other threads succeeded for the epoch to wrap, it just
                    // does an excess GC.
                    if (RESERVE_GC_EPOCH == cleanedEpoch) {
                        // Increment with overflow to 0, so the value can
                        // never equal the initial/reset cleanedEpoch value.
                        RESERVE_GC_EPOCH = Integer.max(0, RESERVE_GC_EPOCH + 1);
                        System.gc();
                        break;
                    }
                    cleanedEpoch = RESERVE_GC_EPOCH;
                }
                try {
                    if (tryReserveOrClean(size, cap)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                    cleanedEpoch = -1; // Reset when incomplete.
                }
            }

            // A retry loop with exponential back-off delays.
            // Sometimes it would suffice to give up once reference
            // processing is complete.  But if there are many threads
            // competing for memory, this gives more opportunities for
            // any given thread to make progress.  In particular, this
            // seems to be enough for a stress test like
            // DirectBufferAllocTest to (usually) succeed, while
            // without it that test likely fails.  Since failure here
            // ends in OOME, there's no need to hurry.
            for (int sleeps = 0; true; ) {
                try {
                    if (tryReserveOrClean(size, cap)) {
                        return;
                    } else if (sleeps < MAX_SLEEPS) {
                        Thread.sleep(INITIAL_SLEEP << sleeps);
                        ++sleeps; // Only increment if sleep completed.
                    } else {
                        throw new OutOfMemoryError
                            ("Cannot reserve "
                             + size + " bytes of direct buffer memory (allocated: "
                             + RESERVED_MEMORY.get() + ", limit: " + MAX_MEMORY +")");
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }

        } finally {
            // Reapply any deferred interruption.
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Try to reserve memory, or failing that, try to make progress on
    // cleaning.  Returns true if successfully reserved memory, false if
    // failed and ran out of cleaning work.
    private static boolean tryReserveOrClean(long size, long cap)
        throws InterruptedException
    {
        JavaLangRefAccess jlra = SharedSecrets.getJavaLangRefAccess();
        boolean progressing = true;
        while (true) {
            if (tryReserveMemory(size, cap)) {
                return true;
            } else if (BufferCleaner.tryCleaning()) {
                progressing = true;
            } else if (!progressing) {
                return false;
            } else {
                progressing = jlra.waitForReferenceProcessing();
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

    // Maximum number of bytes to set in one call to {@code Unsafe.setMemory}.
    // This threshold allows safepoint polling during large memory operations.
    static final long UNSAFE_SET_THRESHOLD = 1024 * 1024;

    /**
     * Sets a block of memory starting from a given address to a specified byte value.
     *
     * @param srcAddr
     *        the starting memory address
     * @param count
     *        the number of bytes to set
     * @param value
     *        the byte value to set
     */
    static void setMemory(long srcAddr, long count, byte value) {
        long offset = 0;
        while (offset < count) {
            long len = Math.min(UNSAFE_SET_THRESHOLD, count - offset);
            UNSAFE.setMemory(srcAddr + offset, len, value);
            offset += len;
        }
    }

}
