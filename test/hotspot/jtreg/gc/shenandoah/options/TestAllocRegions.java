/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test id=default
 * @summary Smoke test for CAS allocator with default alloc-regions settings
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/*
 * @test id=min-mutator
 * @summary Single mutator alloc region (maximum contention on one slot)
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahMutatorAllocRegions=1
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/*
 * @test id=max-mutator
 * @summary Maximum mutator alloc regions
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahMutatorAllocRegions=128
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/*
 * @test id=min-collector
 * @summary Single collector alloc region
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahCollectorAllocRegions=1
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/*
 * @test id=max-collector
 * @summary Maximum collector alloc regions
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahCollectorAllocRegions=32
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/*
 * @test id=min-both
 * @summary Minimum alloc regions for both mutator and collector
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
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahGCMode=generational -XX:ShenandoahMutatorAllocRegions=1
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/*
 * @test id=generational-max-both
 * @summary Generational mode with maximum alloc regions
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahGCMode=generational
 *      -XX:ShenandoahMutatorAllocRegions=128 -XX:ShenandoahCollectorAllocRegions=32
 *      -XX:+ShenandoahVerify -Xmx256m -Xms256m
 *      TestAllocRegions
 */

/**
 * Allocates enough garbage to trigger several GC cycles, exercising:
 *   - ShenandoahAllocator fast path (CAS into shared alloc regions)
 *   - slow-path region install/replacement on region fill
 *   - release_alloc_regions at GC phase boundaries
 *
 * Runs under +ShenandoahVerify so any heap corruption or accounting drift
 * surfaces as a verification failure rather than silent misbehavior.
 */
public class TestAllocRegions {
    // Total allocation target, enough to force multiple GC cycles within 256m heap.
    static final long TARGET_BYTES = 512L * 1024 * 1024;
    static final int OBJ_SIZE = 1024;

    static volatile Object sink;

    public static void main(String[] args) throws Exception {
        long allocated = 0;
        long count = 0;
        while (allocated < TARGET_BYTES) {
            sink = new byte[OBJ_SIZE];
            allocated += OBJ_SIZE;
            count++;
        }
        System.out.println("Allocated " + count + " objects totalling " + allocated + " bytes");
    }
}
