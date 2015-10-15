/*
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Martin Buchholz with assistance from members of JCP
 * JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @bug 8074773
 * @summary Stress test looks for lost unparks
 * @run main/timeout=1200 ParkLoops
 */

import static java.util.concurrent.TimeUnit.SECONDS;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;

public final class ParkLoops {
    static final int THREADS = 4; // must be power of two
    // static final int ITERS = 2_000_000;
    // static final int TIMEOUT = 3500;  // in seconds
    static final int ITERS = 100_000;
    static final int TIMEOUT = 1000;  // in seconds

    static class Parker implements Runnable {
        static {
            // Reduce the risk of rare disastrous classloading in first call to
            // LockSupport.park: https://bugs.openjdk.java.net/browse/JDK-8074773
            Class<?> ensureLoaded = LockSupport.class;
        }

        private final AtomicReferenceArray<Thread> threads;
        private final CountDownLatch done;

        Parker(AtomicReferenceArray<Thread> threads, CountDownLatch done) {
            this.threads = threads;
            this.done = done;
        }

        public void run() {
            final SimpleRandom rng = new SimpleRandom();
            final Thread current = Thread.currentThread();
            for (int k = ITERS, j; k > 0; k--) {
                do {
                    j = rng.next() & (THREADS - 1);
                } while (!threads.compareAndSet(j, null, current));
                do {                    // handle spurious wakeups
                    LockSupport.park();
                } while (threads.get(j) == current);
            }
            done.countDown();
        }
    }

    static class Unparker implements Runnable {
        static {
            // Reduce the risk of rare disastrous classloading in first call to
            // LockSupport.park: https://bugs.openjdk.java.net/browse/JDK-8074773
            Class<?> ensureLoaded = LockSupport.class;
        }

        private final AtomicReferenceArray<Thread> threads;
        private final CountDownLatch done;

        Unparker(AtomicReferenceArray<Thread> threads, CountDownLatch done) {
            this.threads = threads;
            this.done = done;
        }

        public void run() {
            final SimpleRandom rng = new SimpleRandom();
            for (int n = 0; (n++ & 0xff) != 0 || done.getCount() > 0;) {
                int j = rng.next() & (THREADS - 1);
                Thread parker = threads.get(j);
                if (parker != null &&
                    threads.compareAndSet(j, parker, null)) {
                    LockSupport.unpark(parker);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        final ExecutorService pool = Executors.newCachedThreadPool();
        final AtomicReferenceArray<Thread> threads
            = new AtomicReferenceArray<>(THREADS);
        final CountDownLatch done = new CountDownLatch(THREADS);
        final Runnable parker = new Parker(threads, done);
        final Runnable unparker = new Unparker(threads, done);
        for (int i = 0; i < THREADS; i++) {
            pool.submit(parker);
            pool.submit(unparker);
        }
        try {
          if (!done.await(TIMEOUT, SECONDS)) {
            dumpAllStacks();
            throw new AssertionError("lost unpark");
          }
        } finally {
          pool.shutdown();
          pool.awaitTermination(10L, SECONDS);
        }
    }

    static void dumpAllStacks() {
        ThreadInfo[] threadInfos =
            ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
        for (ThreadInfo threadInfo : threadInfos) {
            System.err.print(threadInfo);
        }
    }

    /**
     * An actually useful random number generator, but unsynchronized.
     * Basically same as java.util.Random.
     */
    public static class SimpleRandom {
        private static final long multiplier = 0x5DEECE66DL;
        private static final long addend = 0xBL;
        private static final long mask = (1L << 48) - 1;
        static final AtomicLong seq = new AtomicLong(1);
        private long seed = System.nanoTime() + seq.getAndIncrement();

        public int next() {
            long nextseed = (seed * multiplier + addend) & mask;
            seed = nextseed;
            return ((int)(nextseed >>> 17)) & 0x7FFFFFFF;
        }
    }
}
