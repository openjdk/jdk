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
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestCASAllocContention
 */

/*
 * @test id=single-slot
 * @summary Force all mutator threads onto a single alloc region slot
 * @bug 8361099
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
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahMutatorAllocRegions=128 -XX:ShenandoahCollectorAllocRegions=128
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestCASAllocContention
 */

/*
 * @test id=generational-default
 * @summary Default alloc regions with generational mode
 * @bug 8361099
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
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahGCMode=generational -XX:ShenandoahMutatorAllocRegions=1
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestCASAllocContention
 */

/*
 * @test id=generational-many-slots
 * @summary Generational mode with many alloc region slots, typically more than actively used
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahGCMode=generational -XX:ShenandoahMutatorAllocRegions=128 -XX:ShenandoahCollectorAllocRegions=128
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestCASAllocContention
 */

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spawns many concurrent allocator threads and verifies:
 *   - no crashes / assertion failures under sustained CAS contention
 *   - every worker completed its allocation loop and recorded a non-trivial tally
 *     (a sanity net against a silently broken or optimized-away run)
 *
 * The primary correctness check is +ShenandoahVerify, which runs throughout
 * so any heap corruption or accounting drift surfaces as a verification failure.
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

    // Per-worker retention slot, kept live so each worker's rolling window isn't DCE'd.
    // One slot per worker (rather than a single shared field) so the retention pressure
    // the comment below claims actually holds across all workers concurrently.
    static Object[] sinks;

    // First unexpected throwable raised by any worker (e.g. OutOfMemoryError when the
    // allocation rate outpaces GC on a slow CI host). Captured and rethrown from main so
    // the real failure surfaces as itself instead of being masked by the "contention was
    // not sustained" check, which would otherwise fire because the worker never completed.
    static volatile Throwable workerError;

    public static void main(String[] args) throws Exception {
        int nThreads = Math.min(MAX_THREADS, Math.max(2, Runtime.getRuntime().availableProcessors()));
        System.out.println("Spawning " + nThreads + " allocator threads for "
                           + (DURATION_NANOS / 1_000_000_000L) + " seconds");

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(nThreads);
        AtomicLong totalAllocs = new AtomicLong();
        AtomicLong totalBytes = new AtomicLong();
        // Number of workers that ran their full allocation loop and published their
        // tallies. Any worker that exits early (e.g. interrupted before recording)
        // fails to increment this, so we can assert below that every worker finished.
        AtomicLong completedWorkers = new AtomicLong();

        sinks = new Object[nThreads];
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
                    sinks[id] = window;  // publish so window isn't DCE'd
                    totalAllocs.addAndGet(allocs);
                    totalBytes.addAndGet(bytes);
                    completedWorkers.incrementAndGet();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    // Record the first real failure (e.g. OutOfMemoryError) so main can
                    // rethrow it as the actual cause rather than masking it behind the
                    // "contention was not sustained" completion check.
                    workerError = t;
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
        long completed = completedWorkers.get();
        System.out.println("Total allocs across threads: " + allocs);
        System.out.println("Total bytes across threads:  " + bytes);
        System.out.println("Workers completed:           " + completed + "/" + nThreads);

        // If any worker died with an unexpected throwable (most plausibly OutOfMemoryError when
        // a slow CI host can't keep up with the allocation rate), report that as the real cause
        // rather than letting the completion check below misattribute it to lost contention.
        if (workerError != null) {
            throw new IllegalStateException("Worker thread failed during allocation", workerError);
        }

        // The primary correctness check is +ShenandoahVerify, which runs throughout and
        // fails the test on any heap corruption or accounting drift under CAS contention. The
        // assertions below are a secondary sanity net: they catch a silently broken run (every
        // worker bailing out early, or the allocation loop being optimized away) that would
        // otherwise let the test pass without actually exercising the allocator.

        // Every worker must have completed its loop and published its tally; an early exit
        // (e.g. interruption) means we did not actually sustain contention for the full run.
        if (completed != nThreads) {
            throw new IllegalStateException("Only " + completed + " of " + nThreads
                                            + " workers completed; contention was not sustained");
        }
        // Smallest object size, allocated once per worker, is the floor on total bytes. A real
        // multi-second run allocates orders of magnitude more, so this just rejects a no-op run.
        long minExpectedBytes = (long) nThreads * SIZES[0];
        if (bytes < minExpectedBytes) {
            throw new IllegalStateException("Recorded " + bytes + " bytes, expected at least "
                                            + minExpectedBytes);
        }
        if (allocs < nThreads) {
            throw new IllegalStateException("Recorded " + allocs + " allocations across "
                                            + nThreads + " workers; allocation loop did not run");
        }
    }
}
