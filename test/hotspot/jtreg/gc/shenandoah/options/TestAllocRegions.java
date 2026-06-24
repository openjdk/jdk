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
 * @summary Smoke test for CAS allocator with default alloc-regions settings
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/*
 * @test id=min-mutator
 * @summary Single mutator alloc region (maximum contention on one slot)
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahMutatorAllocRegions=1
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/*
 * @test id=max-mutator
 * @summary Maximum mutator alloc regions, driven by enough threads to populate all 128 slots
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahMutatorAllocRegions=128
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestAllocRegions 128
 */

/*
 * @test id=min-collector
 * @summary Single collector alloc region
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahCollectorAllocRegions=1
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/*
 * @test id=max-collector
 * @summary Maximum collector alloc regions (upper bound of the flag range)
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahCollectorAllocRegions=128
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/*
 * @test id=min-both
 * @summary Minimum alloc regions for both mutator and collector
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahMutatorAllocRegions=1 -XX:ShenandoahCollectorAllocRegions=1
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/*
 * @test id=generational-default
 * @summary Default alloc regions with generational mode
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahGCMode=generational
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/*
 * @test id=generational-min-mutator
 * @summary Generational mode with single mutator alloc region
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahGCMode=generational -XX:ShenandoahMutatorAllocRegions=1
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/*
 * @test id=generational-max-both
 * @summary Generational mode with maximum alloc regions, driven by enough threads to populate all 128 mutator slots
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahGCMode=generational
 *      -XX:ShenandoahMutatorAllocRegions=128 -XX:ShenandoahCollectorAllocRegions=128
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestAllocRegions 128
 */

/**
 * Allocates enough garbage to trigger several GC cycles, exercising:
 *   - ShenandoahAllocator fast path (CAS into shared alloc regions)
 *   - slow-path region install/replacement on region fill
 *   - release_alloc_regions at GC phase boundaries
 *
 * An optional first argument sets the number of concurrent allocator threads
 * (default 1). A mutator stripe slot becomes active only when a thread installs
 * a region into its own start slot, so the number of simultaneously-active
 * mutator slots is bounded by the count of concurrently-allocating threads, not
 * by ShenandoahMutatorAllocRegions alone. The many-slots configs therefore spawn
 * at least as many threads as slots so the high slot count is actually populated.
 *
 * Runs under +ShenandoahVerify so any heap corruption or accounting drift
 * surfaces as a verification failure rather than silent misbehavior.
 */
public class TestAllocRegions {
    // Total allocation target across all threads, enough to force multiple GC cycles.
    static final long TARGET_BYTES = 512L * 1024 * 1024;
    static final int OBJ_SIZE = 1024;

    // Per-thread retention slot, kept live so each thread's rolling window isn't optimized away.
    static volatile Object[] sinks;

    public static void main(String[] args) throws Exception {
        int nThreads = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        long perThreadBytes = TARGET_BYTES / nThreads;
        sinks = new Object[nThreads];
        System.out.println("Allocating " + TARGET_BYTES + " bytes across " + nThreads + " thread(s)");

        Thread[] threads = new Thread[nThreads];
        for (int i = 0; i < nThreads; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                long allocated = 0;
                // Small rolling window: keeps a tiny live set per thread so we exercise the
                // allocator's install/replace path without letting the live set outpace GC.
                Object[] window = new Object[4];
                int wIdx = 0;
                while (allocated < perThreadBytes) {
                    byte[] obj = new byte[OBJ_SIZE];
                    obj[0] = (byte) id;
                    window[wIdx] = obj;
                    wIdx = (wIdx + 1) & 3;
                    allocated += OBJ_SIZE;
                }
                sinks[id] = window;  // publish so the window isn't dead-code-eliminated
            }, "allocator-" + i);
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        System.out.println("Done");
    }
}
