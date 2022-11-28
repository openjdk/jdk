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
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -XX:+UseG1GC -XX:+ExplicitGCInvokesConcurrent -Xlog:gc
 *                   -Xmx16m -Xms16m -XX:G1HeapRegionSize=1m gc.g1.TestRemarkCleanupMXBean
 */

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TestRemarkCleanupMXBean {
    public static List<byte[]> memory = new ArrayList<>();
    static List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();

    public static void main(String[] args) throws Exception {
        HashMap<String, Long> counts = new HashMap<>();
        int num = 16;
        for (int i = 0; i < num; i++) {
            memory.add(new byte[1024 * 128]);
        }
        System.gc();

        for (int i = 0; i < beans.size(); i++) {
            GarbageCollectorMXBean bean = beans.get(i);
            counts.put(bean.getName(), bean.getCollectionCount());
        }
        memory = null;
        System.gc();
        boolean found = false;
        for (int i = 0; i < beans.size(); i++) {
            GarbageCollectorMXBean bean = beans.get(i);
            long before = counts.get(bean.getName());
            long after = bean.getCollectionCount();
            if (after >= before + 2) { // Must report a Remark and a Cleanup
                found = true;
                System.out.println(bean.getName() + " reports a difference " +
                                   after + " - " + before + " = " + (after - before));
            }
        }

        if (found == false) {
            throw new RuntimeException("Remark or Cleanup not reported by GarbageCollectorMXBean");
        }
    }
}
