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
 * @test TestRemarkCleanupMXBeanCollectionUsage
 * @bug 8386332
 * @summary Test that Remark and Cleanup correctly update old pool's getCollectionUsage() bean.
 * @requires vm.gc.G1
 * @library /test/lib /
 * @build   jdk.test.whitebox.WhiteBox
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run     driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UseG1GC -Xlog:gc -XX:G1HeapRegionSize=1m -Xms128m -Xmx128m
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   gc.g1.TestRemarkCleanupMXBeanCollectionUsage
 */

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.ref.Reference;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

public class TestRemarkCleanupMXBeanCollectionUsage {
    private static WhiteBox wb = WhiteBox.getWhiteBox();
    private static final int M = 1024 * 1024;

    private static MemoryPoolMXBean findPoolMXBean(String name) throws Exception {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getName().equals(name)) {
                return pool;
            }
        }
        throw new RuntimeException("Pool " + name + " not found.");
    }

    private static long getCollectionUsageUsedAndPrint(MemoryPoolMXBean pool, String message) {
        long result = pool.getCollectionUsage().getUsed();
        System.out.println(message + ": " + result);
        return result;
    }

    public static void main(String[] args) throws Exception {
        Object throwaway = new Object();
        try {
            MemoryPoolMXBean oldPool = findPoolMXBean("G1 Old Gen");

            wb.concurrentGCAcquireControl();
            wb.fullGC();
            long initialUsage = getCollectionUsageUsedAndPrint(oldPool, "Initial Usage");

            // Allocate something in old gen. CollectionUsage should be updated.
            throwaway = new byte[M]; // Humongous allocation.
            wb.fullGC();
            long afterFirstUsage = getCollectionUsageUsedAndPrint(oldPool, "After first alloc usage");
            Asserts.assertTrue(afterFirstUsage >= initialUsage + M,
                               "Full GC should updated collectionUsage. Before " + afterFirstUsage + " after " + initialUsage);

            // Remark pause should update collectionUsage, i.e. the following release of the memory be noticed.
            throwaway = null;
            wb.concurrentGCRunTo(wb.G1_AFTER_REBUILD_STARTED);
            long afterRemarkUsage = getCollectionUsageUsedAndPrint(oldPool, "After Remark usage");
            Asserts.assertTrue(afterRemarkUsage < afterFirstUsage - M,
                               "Remark pause should have updated getCollectionUsage(). Before " + afterFirstUsage + " after " + afterRemarkUsage);

            // Cleanup pause should not update collectionUsage, i.e. the following allocation go unnoticed.
            throwaway = new byte[M];
            wb.concurrentGCRunTo(wb.G1_AFTER_CLEANUP_STARTED);
            long afterCleanupUsage = getCollectionUsageUsedAndPrint(oldPool, "After Cleanup usage");
            Asserts.assertTrue(afterCleanupUsage == afterRemarkUsage,
                               "Cleanup pause should not update getCollectionUsage(). Before " + afterRemarkUsage + " after " + afterCleanupUsage);
        } finally {
            wb.concurrentGCReleaseControl();
            Reference.reachabilityFence(throwaway);
        }
    }
}
