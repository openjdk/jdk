/*
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
 */

package gc.g1;

/*
 * @test TestUncommitDuringConcurrentBitmapClear
 * @bug 8385369
 * @requires vm.gc.G1
 * @requires vm.debug
 * @requires vm.flagless
 * @requires os.maxMemory > 8g
 * @summary Verify that G1 does not crash while uncommitting a region whose
 *          bitmap is currently being cleared.
 *          Options are geared towards uncommitting aggressively. Also use a large
 *          region size so that corresponding bitmaps get uncommitted always too.
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm
 *   -Xbootclasspath/a:.
 *   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *   -Xmx8g
 *   -Xms32m
 *   -XX:G1HeapRegionSize=16m
 *   -XX:+UseG1GC
 *   -XX:ConcGCThreads=1
 *   -XX:GCTimeRatio=1
 *   -XX:G1CPUUsageShrinkThreshold=1
 *   -XX:G1ShrinkByPercentOfAvailable=100
 *   -XX:G1UncommitInitialDelay=0
 *   -Xlog:gc+marking,gc,gc+ergo+heap=debug
 *   gc.g1.TestUncommitDuringConcurrentBitmapClear
 */

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import jdk.test.whitebox.WhiteBox;

/*
 * Repeatedly make the concurrent cycle stop after the cleanup pause, issuing
 * young GCs during the Concurrent Cleanup for Next Mark phase. Humongous
 * regions allocated and dropped before that should get eager-reclaimed and
 * their memory uncommitted while bitmap clearing runs.
 */
public class TestUncommitDuringConcurrentBitmapClear {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static final int NumObjs = 400;                  // Number of humongous objects to allocate
                                                             // per attempt. Sized to fill a fair amount of
                                                             // the available memory.
    private static final int LargeObjSize = 9 * 1024 * 1024; // Large enough to be a humongous object.

    private static Object[] objects;

    private static void test() throws Exception {

        // This task drops the humongous objects, making them eligible for
        // uncommit, and starts the concurrent bitmap clearing. While it is
        // running, the caller triggers GCs that may or may not trigger the issue.
        FutureTask<Void> concurrentClearTask = new FutureTask<>(() -> {
            objects = null;
            WB.concurrentGCRunTo(WB.G1_BEFORE_CLEANUP_COMPLETED);
            return null;
        });

        try {
            System.out.println("taking control");
            WB.concurrentGCAcquireControl();

            // Allocate a new set of humongous objects. Acquire control first to avoid
            // unnecessary concurrent cycles due to that allocation. We do not need them.
            objects = new Object[NumObjs];
            for (int i = 0; i < objects.length; i++) {
                objects[i] = new byte[LargeObjSize];
            }

            WB.concurrentGCRunTo(WB.G1_AFTER_CLEANUP_STARTED);

            new Thread(concurrentClearTask).start();

            int numYoungGCs = 0;
            // Execute at least one young GC, even if the concurrent
            // clear bitmap finishes very quickly.
            do {
                WB.youngGC();
                numYoungGCs++;
                // Wait a bit. This should give the concurrent clear task a chance
                // to finish execution.
                Thread.sleep(1);
            } while (!concurrentClearTask.isDone() && numYoungGCs < 200);

            concurrentClearTask.get(30, TimeUnit.SECONDS); // Propagate exceptions, if any.
        } finally {
            WB.concurrentGCRunToIdle();

            System.out.println("Releasing control");
            WB.concurrentGCReleaseControl();
        }
    }

    public static void main(String[] args) throws Exception {
        if (!WB.supportsConcurrentGCBreakpoints()) {
            throw new RuntimeException("G1 should support GC breakpoints");
        }
        for (int i = 0; i < 20; i++) {
            test();
        }
    }
}
