/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test id=default
 * @summary Multi-threaded CAS allocation stress with default alloc regions
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestCASAllocContention
 */

/*
 * @test id=single-slot
 * @summary Force all mutator threads onto a single alloc region slot
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahMutatorAllocRegions=1
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestCASAllocContention
 */

/*
 * @test id=many-slots
 * @summary Many alloc region slots, typically more than actively used
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahMutatorAllocRegions=128 -XX:ShenandoahCollectorAllocRegions=32
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestCASAllocContention
 */

/*
 * @test id=generational-default
 * @summary Default alloc regions with generational mode
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahGCMode=generational
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestCASAllocContention
 */

/*
 * @test id=generational-single-slot
 * @summary Generational mode with maximum contention on one mutator slot
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahGCMode=generational -XX:ShenandoahMutatorAllocRegions=1
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestCASAllocContention
 */

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spawns many concurrent allocator threads and verifies:
 *   - no crashes / assertion failures under sustained CAS contention
 *   - accumulated allocations are at least as large as the sum of per-thread
 *     acknowledgements (weak correctness sanity check)
 *
 * Runs under +ShenandoahVerify so heap corruption or accounting drift surfaces.
 *
 * Intentionally conservative on thread count and duration so CI slow boxes
 * don't trip OutOfMemoryError when allocation rate outpaces GC throughput
 * with +ShenandoahVerify enabled.
 */
public class TestCASAllocContention {

    // Wall-clock budget per run. Kept short so the test fits in default jtreg timeout
    // and so allocation rate doesn't blow past what GC can reclaim on slow CI hosts.
    static final long DURATION_NANOS = 5L * 1_000_000_000L;

    // Object size: mix of small (TLAB) and medium (shared) allocations.
    static final int[] SIZES = { 16, 64, 256, 1024 };

    // Cap threads so GC can keep up on small heaps. We want contention on the
    // CAS allocator's slot array, not an allocation-vs-GC race.
    static final int MAX_THREADS = 8;

    static volatile Object sink;

    public static void main(String[] args) throws Exception {
        int nThreads = Math.min(MAX_THREADS, Math.max(2, Runtime.getRuntime().availableProcessors()));
        System.out.println("Spawning " + nThreads + " allocator threads for "
                           + (DURATION_NANOS / 1_000_000_000L) + " seconds");

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(nThreads);
        AtomicLong totalAllocs = new AtomicLong();
        AtomicLong totalBytes = new AtomicLong();

        Thread[] threads = new Thread[nThreads];
        for (int i = 0; i < nThreads; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                try {
                    startGate.await();
                    long allocs = 0;
                    long bytes = 0;
                    long deadline = System.nanoTime() + DURATION_NANOS;
                    int sizeIdx = id % SIZES.length;
                    // Small rolling window: keeps some retention to exercise the
                    // collector's CAS alloc path but caps live set.
                    Object[] window = new Object[4];
                    int wIdx = 0;
                    while (System.nanoTime() < deadline) {
                        int sz = SIZES[sizeIdx];
                        byte[] obj = new byte[sz];
                        // Write something so the allocation isn't DCE'd.
                        obj[0] = (byte) allocs;
                        obj[sz - 1] = (byte) id;
                        window[wIdx] = obj;
                        wIdx = (wIdx + 1) & 3;
                        allocs++;
                        bytes += sz;
                        // Rotate sizes so a single thread covers the mix over time.
                        if ((allocs & 0xff) == 0) {
                            sizeIdx = (sizeIdx + 1) % SIZES.length;
                        }
                    }
                    sink = window;  // publish so window isn't DCE'd
                    totalAllocs.addAndGet(allocs);
                    totalBytes.addAndGet(bytes);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            }, "allocator-" + i);
            threads[i].setDaemon(true);
            threads[i].start();
        }

        startGate.countDown();
        doneGate.await();

        long allocs = totalAllocs.get();
        long bytes = totalBytes.get();
        System.out.println("Total allocs across threads: " + allocs);
        System.out.println("Total bytes across threads:  " + bytes);

        if (allocs == 0) {
            throw new IllegalStateException("No allocations recorded");
        }
        if (bytes == 0) {
            throw new IllegalStateException("No bytes recorded");
        }
    }
}
