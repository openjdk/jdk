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

/**
 * @test id=satb-adaptive
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 * @bug 8372543
 * @summary When soft max heap size < Xmx, we had a bug reported in JBS-8372543 where available size was undercalculated.
 *          This caused excessive GC runs.
 *
 * @run main/othervm -XX:SoftMaxHeapSize=512m -Xmx2g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -Xlog:gc=info
 *      -XX:ShenandoahGCMode=satb
 *      -XX:+ShenandoahDegeneratedGC
 *      -XX:ShenandoahGCHeuristics=adaptive
 *      TestSoftMaxHeapSizeAvailableCalc
 */

/**
 * @test id=satb-static
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -XX:SoftMaxHeapSize=512m -Xmx2g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -Xlog:gc=info
 *      -XX:ShenandoahGCMode=satb
 *      -XX:+ShenandoahDegeneratedGC
 *      -XX:ShenandoahGCHeuristics=static
 *      TestSoftMaxHeapSizeAvailableCalc
 */

/**
 * @test id=generational
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -XX:SoftMaxHeapSize=512m -Xmx2g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -Xlog:gc=info
 *      -XX:ShenandoahGCMode=generational
 *      -XX:ShenandoahGCHeuristics=adaptive
 *      TestSoftMaxHeapSizeAvailableCalc
 *
 */
import java.lang.management.ManagementFactory;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.dcmd.PidJcmdExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.sun.management.GarbageCollectorMXBean;

public class TestSoftMaxHeapSizeAvailableCalc {
    public static void main(String[] args) throws Exception {
        Allocate.test();
    }

    // This test runs an app that has a stable heap of ~300M and allocates temporary garbage at ~100M/s
    // Soft max: 512M, ShenandoahMinFreeThreshold: 10 (default), ShenandoahEvacReserve: 5 (default)
    // Soft max for mutator: 512M * (100.0 - 5) / 100 = 486.4M
    // Threshold to trigger gc: 486.4M - 512 * 10 / 100.0 = 435.2M, just above (300 + 100)M.
    // Expect gc count to be less than 1 / sec.
    public static class Allocate {
        static final List<byte[]> longLived = new ArrayList<>();

        public static void test() throws Exception {
            final int expectedMaxGcCount = Integer.getInteger("expectedMaxGcCount", 30);
            List<java.lang.management.GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
            java.lang.management.GarbageCollectorMXBean cycleCollector = null;
            for (java.lang.management.GarbageCollectorMXBean bean : collectors) {
                if (bean.getName().contains("Cycles")) {
                    cycleCollector = bean;
                }
            }

            // Allocate ~300MB of long-lived objects
            for (int i = 0; i < 300; i++) {
                longLived.add(new byte[1_000_000]);
            }

            // allocate short-lived garbage to the heap
            long end = System.currentTimeMillis() + 30_000; // 30 seconds

            while (System.currentTimeMillis() < end) {
                byte[] garbage = new byte[1_000_000];
                garbage[0] = 1; // prevent optimization

                Thread.sleep(10); // Pace to generate garbage at speed of ~100M/s
            }

            long gcCount = cycleCollector.getCollectionCount();
            Asserts.assertLessThan(gcCount, (long) expectedMaxGcCount, "GC was triggered too many times. Expected to be less than: " + expectedMaxGcCount + ", triggered: " + gcCount);
        }
    }
}
