/*
 * Copyright (c) 2022, Alibaba Group Holding Limited. All rights reserved.
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
 * @test TestRemarkCleanupMXBean
 * @bug 8297247
 * @summary Test that Remark and Cleanup are correctly reported by
 *          a GarbageCollectorMXBean
 * @requires vm.gc.G1
 * @library /test/lib /
 * @build   jdk.test.whitebox.WhiteBox
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run     driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UseG1GC -Xlog:gc
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   gc.g1.TestRemarkCleanupMXBean
 */

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.gc.GC;
import gc.testlibrary.g1.MixedGCProvoker;

public class TestRemarkCleanupMXBean {
    public static void main(String[] args) throws Exception {
        GarbageCollectorMXBean g1ConcGCBean = null;
        String expectedName = "G1 Concurrent GC";
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (expectedName.equals(bean.getName())) {
                g1ConcGCBean = bean;
                break;
            }
        }
        if (g1ConcGCBean == null) {
            throw new RuntimeException("Unable to find GC bean: " + expectedName);
        }

        long before = g1ConcGCBean.getCollectionCount();
        MixedGCProvoker.provokeConcMarkCycle();
        long after = g1ConcGCBean.getCollectionCount();

        if (after >= before + 2) { // Must report a Remark and a Cleanup
            System.out.println(g1ConcGCBean.getName() + " reports a difference " +
                               after + " - " + before + " = " + (after - before));
        } else {
            throw new RuntimeException("Remark or Cleanup not reported by " +
                                       g1ConcGCBean.getName());
        }
    }
}
