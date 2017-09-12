/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.JavaLangRefAccess;
import jdk.internal.misc.JavaNioAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;

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

    private static final Unsafe unsafe = Unsafe.getUnsafe();

    static Unsafe unsafe() {
        return unsafe;
    }


    // -- Processor and memory-system properties --

    private static final ByteOrder byteOrder
        = unsafe.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;

    static ByteOrder byteOrder() {
        return byteOrder;
    }

    private static int pageSize = -1;

    static int pageSize() {
        if (pageSize == -1)
            pageSize = unsafe().pageSize();
        return pageSize;
    }

    static int pageCount(long size) {
        return (int)(size + (long)pageSize() - 1L) / pageSize();
    }

    private static boolean unaligned = unsafe.unalignedAccess();

    static boolean unaligned() {
        return unaligned;
    }


    // -- Direct memory management --

    // A user-settable upper limit on the maximum amount of allocatable
    // direct buffer memory.  This value may be changed during VM
    // initialization if it is launched with "-XX:MaxDirectMemorySize=<size>".
    private static volatile long maxMemory = VM.maxDirectMemory();
    private static final AtomicLong reservedMemory = new AtomicLong();
    private static final AtomicLong totalCapacity = new AtomicLong();
    private static final AtomicLong count = new AtomicLong();
    private static volatile boolean memoryLimitSet;

    // max. number of sleeps during try-reserving with exponentially
    // increasing delay before throwing OutOfMemoryError:
    // 1, 2, 4, 8, 16, 32, 64, 128, 256 (total 511 ms ~ 0.5 s)
    // which means that OOME will be thrown after 0.5 s of trying
    private static final int MAX_SLEEPS = 9;

    // These methods should be called whenever direct memory is allocated or
    // freed.  They allow the user to control the amount of direct memory
    // which a process may access.  All sizes are specified in bytes.
    static void reserveMemory(long size, int cap) {

        if (!memoryLimitSet && VM.initLevel() >= 1) {
            maxMemory = VM.maxDirectMemory();
            memoryLimitSet = true;
        }

        // optimist!
        if (tryReserveMemory(size, cap)) {
            return;
        }

        final JavaLangRefAccess jlra = SharedSecrets.getJavaLangRefAccess();
        boolean interrupted = false;
        try {

            // Retry allocation until success or there are no more
            // references (including Cleaners that might free direct
            // buffer memory) to process and allocation still fails.
            boolean refprocActive;
            do {
                try {
                    refprocActive = jlra.waitForReferenceProcessing();
                } catch (InterruptedException e) {
                    // Defer interrupts and keep trying.
                    interrupted = true;
                    refprocActive = true;
                }
                if (tryReserveMemory(size, cap)) {
                    return;
                }
            } while (refprocActive);

            // trigger VM's Reference processing
            System.gc();

            // A retry loop with exponential back-off delays.
            // Sometimes it would suffice to give up once reference
            // processing is complete.  But if there are many threads
            // competing for memory, this gives more opportunities for
            // any given thread to make progress.  In particular, this
            // seems to be enough for a stress test like
            // DirectBufferAllocTest to (usually) succeed, while
            // without it that test likely fails.  Since failure here
            // ends in OOME, there's no need to hurry.
            long sleepTime = 1;
            int sleeps = 0;
            while (true) {
                if (tryReserveMemory(size, cap)) {
                    return;
                }
                if (sleeps >= MAX_SLEEPS) {
                    break;
                }
                try {
                    if (!jlra.waitForReferenceProcessing()) {
                        Thread.sleep(sleepTime);
                        sleepTime <<= 1;
                        sleeps++;
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }

            // no luck
            throw new OutOfMemoryError("Direct buffer memory");

        } finally {
            if (interrupted) {
                // don't swallow interrupts
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean tryReserveMemory(long size, int cap) {

        // -XX:MaxDirectMemorySize limits the total capacity rather than the
        // actual memory usage, which will differ when buffers are page
        // aligned.
        long totalCap;
        while (cap <= maxMemory - (totalCap = totalCapacity.get())) {
            if (totalCapacity.compareAndSet(totalCap, totalCap + cap)) {
                reservedMemory.addAndGet(size);
                count.incrementAndGet();
                return true;
            }
        }

        return false;
    }


    static void unreserveMemory(long size, int cap) {
        long cnt = count.decrementAndGet();
        long reservedMem = reservedMemory.addAndGet(-size);
        long totalCap = totalCapacity.addAndGet(-cap);
        assert cnt >= 0 && reservedMem >= 0 && totalCap >= 0;
    }

    // -- Monitoring of direct buffer usage --

    static {
        // setup access to this package in SharedSecrets
        SharedSecrets.setJavaNioAccess(
            new JavaNioAccess() {
                @Override
                public JavaNioAccess.BufferPool getDirectBufferPool() {
                    return new JavaNioAccess.BufferPool() {
                        @Override
                        public String getName() {
                            return "direct";
                        }
                        @Override
                        public long getCount() {
                            return Bits.count.get();
                        }
                        @Override
                        public long getTotalCapacity() {
                            return Bits.totalCapacity.get();
                        }
                        @Override
                        public long getMemoryUsed() {
                            return Bits.reservedMemory.get();
                        }
                    };
                }
                @Override
                public ByteBuffer newDirectByteBuffer(long addr, int cap, Object ob) {
                    return new DirectByteBuffer(addr, cap, ob);
                }
                @Override
                public void truncate(Buffer buf) {
                    buf.truncate();
                }
        });
    }

    // These numbers represent the point at which we have empirically
    // determined that the average cost of a JNI call exceeds the expense
    // of an element by element copy.  These numbers may change over time.
    static final int JNI_COPY_TO_ARRAY_THRESHOLD   = 6;
    static final int JNI_COPY_FROM_ARRAY_THRESHOLD = 6;
}
