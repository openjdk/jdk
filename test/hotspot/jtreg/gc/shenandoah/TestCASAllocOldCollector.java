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
 * @summary Stress the OldCollector CAS alloc-region path via promotion in generational mode
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahGCMode=generational
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestCASAllocOldCollector
 */

/*
 * @test id=many-collector-slots
 * @summary OldCollector path with maximum collector alloc region slots
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahGCMode=generational -XX:ShenandoahCollectorAllocRegions=16
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestCASAllocOldCollector
 */

/*
 * @test id=single-collector-slot
 * @summary OldCollector path with single collector alloc region slot
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahGCMode=generational -XX:ShenandoahCollectorAllocRegions=1
 *      -XX:+ShenandoahVerify -Xmx512m -Xms512m
 *      TestCASAllocOldCollector
 */

/**
 * Exercises the OldCollector CAS alloc-region path by creating promotion pressure in
 * generational Shenandoah. Objects are allocated and retained long enough to survive
 * young-gen collections, forcing promotion into old-gen via the PLAB/shared-gc CAS path.
 *
 * The primary correctness check is +ShenandoahVerify, which validates heap accounting
 * and region state after every GC phase boundary.
 */
public class TestCASAllocOldCollector {
    // Long-lived objects that survive young GC and get promoted to old gen.
    static Object[] tenured;

    // Short-lived allocations to drive young GC cycles.
    static volatile Object sink;

    public static void main(String[] args) throws Exception {
        // Tenured array: objects here survive long enough to be promoted.
        // Size chosen so promotion pressure is meaningful but doesn't OOM.
        tenured = new Object[4096];
        int tenuredIdx = 0;

        // Allocate enough to trigger multiple young GC cycles and promotions.
        long totalBytes = 0;
        long targetBytes = 400L * 1024 * 1024;

        System.out.println("Generating promotion pressure: target " + (targetBytes / (1024*1024)) + " MB allocation");

        while (totalBytes < targetBytes) {
            // Some objects go into the tenured set (will survive and be promoted)
            byte[] obj = new byte[1024];
            obj[0] = (byte) tenuredIdx;
            if (tenuredIdx < tenured.length) {
                tenured[tenuredIdx] = obj;
            } else {
                // Rotate: replace old tenured objects so old-gen also gets collected eventually
                tenured[tenuredIdx % tenured.length] = obj;
            }
            tenuredIdx++;
            totalBytes += 1024;

            // Additional short-lived garbage to accelerate young GC
            if ((tenuredIdx & 0xF) == 0) {
                sink = new byte[4096];
                totalBytes += 4096;
            }
        }

        // Force a full cycle to exercise old-gen collection with promoted objects
        System.gc();

        System.out.println("Done: allocated " + (totalBytes / (1024*1024)) + " MB, "
                           + tenuredIdx + " objects tenured");
    }
}
