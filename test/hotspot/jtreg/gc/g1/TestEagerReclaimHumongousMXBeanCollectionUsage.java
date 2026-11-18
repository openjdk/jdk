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
 * @test TestEagerReclaimHumongousMXBeanCollectionUsage
 * @bug 8386253
 * @summary Test that eager reclaim updates the old generation collection usage.
 * @requires vm.gc.G1
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UseG1GC -Xms128m -Xmx128m -XX:G1HeapRegionSize=1m
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   gc.g1.TestEagerReclaimHumongousMXBeanCollectionUsage
 */

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

public class TestEagerReclaimHumongousMXBeanCollectionUsage {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final int M = 1024 * 1024;
    private static Object humongous;

    private static MemoryPoolMXBean findOldGenPool() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getName().equals("G1 Old Gen")) {
                return pool;
            }
        }
        throw new RuntimeException("G1 Old Gen pool not found");
    }

    public static void main(String[] args) throws Exception {
        MemoryPoolMXBean oldGen = findOldGenPool();

        WB.fullGC();
        long baseline = oldGen.getCollectionUsage().getUsed();

        humongous = new byte[4 * M];
        WB.concurrentGCAcquireControl();
        long withHumongous;
        try {
            WB.concurrentGCRunTo(WB.G1_AFTER_REBUILD_STARTED);
            withHumongous = oldGen.getCollectionUsage().getUsed();
            WB.concurrentGCRunToIdle();
        } finally {
            WB.concurrentGCReleaseControl();
        }

        // Keep the humongous object live across several young collections.
        for (int i = 0; i < 5; i++) {
            WB.youngGC();
        }

        humongous = null;
        WB.youngGC();

        long current = oldGen.getUsage().getUsed();
        long collection = oldGen.getCollectionUsage().getUsed();
        System.out.println("baseline=" + baseline
                           + " withHumongous=" + withHumongous
                           + " current=" + current
                           + " collection=" + collection);

        Asserts.assertGT(withHumongous, baseline,
                         "The humongous object should be reflected in collection usage");
        Asserts.assertLT(current, withHumongous,
                         "The eagerly reclaimed object should no longer be in current usage");
        Asserts.assertLT(collection, withHumongous,
                         "Collection usage should be updated after eager reclaim");
    }
}
