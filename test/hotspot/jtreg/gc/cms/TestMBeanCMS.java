/*
 * Copyright (c) 2010, 2019, Oracle and/or its affiliates. All rights reserved.
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

package gc.cms;

/*
 * @test TestMBeanCMS.java
 * @bug 6581734
 * @requires vm.gc.ConcMarkSweep & !vm.graal.enabled
 * @summary CMS Old Gen's collection usage is zero after GC which is incorrect
 * @modules java.management
 * @run main/othervm -Xmx512m -verbose:gc -XX:+UseConcMarkSweepGC gc.cms.TestMBeanCMS
 *
 */

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.LinkedList;
import java.util.List;

// 6581734 states that memory pool usage via the mbean is wrong
// for CMS (zero, even after a collection).
//
// 6580448 states that the collection count similarly is wrong
// (stays at zero for CMS collections)
// -- closed as dup of 6581734 as the same fix resolves both.


public class TestMBeanCMS {

    private String poolName = "CMS";
    private String collectorName = "ConcurrentMarkSweep";

    public static void main(String [] args) {

        TestMBeanCMS t = null;
        if (args.length==2) {
            t = new TestMBeanCMS(args[0], args[1]);
        } else {
            System.out.println("Defaulting to monitor CMS pool and collector.");
            t = new TestMBeanCMS();
        }
        t.run();
    }

    public TestMBeanCMS(String pool, String collector) {
        poolName = pool;
        collectorName = collector;
    }

    public TestMBeanCMS() {
    }

    public void run() {
        // Use some memory, enough that we expect collections should
        // have happened.
        // Must run with options to ensure no stop the world full GC,
        // but e.g. at least one CMS cycle.
        allocationWork(300*1024*1024);
        System.out.println("Done allocationWork");

        // Verify some non-zero results are stored.
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        int poolsFound = 0;
        int poolsWithStats = 0;
        for (int i=0; i<pools.size(); i++) {
            MemoryPoolMXBean pool = pools.get(i);
            String name = pool.getName();
            System.out.println("found pool: " + name);

            if (name.contains(poolName)) {
                long usage = pool.getCollectionUsage().getUsed();
                System.out.println(name + ": usage after GC = " + usage);
                poolsFound++;
                if (usage > 0) {
                    poolsWithStats++;
                }
            }
        }
        if (poolsFound == 0) {
            throw new RuntimeException("No matching memory pools found: test with -XX:+UseConcMarkSweepGC");
        }

        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        int collectorsFound = 0;
        int collectorsWithTime= 0;
        for (int i=0; i<collectors.size(); i++) {
            GarbageCollectorMXBean collector = collectors.get(i);
            String name = collector.getName();
            System.out.println("found collector: " + name);
            if (name.contains(collectorName)) {
                collectorsFound++;
                System.out.println(name + ": collection count = "
                                   + collector.getCollectionCount());
                System.out.println(name + ": collection time  = "
                                   + collector.getCollectionTime());
                if (collector.getCollectionCount() <= 0) {
                    throw new RuntimeException("collection count <= 0");
                }
                if (collector.getCollectionTime() > 0) {
                    collectorsWithTime++;
                }
            }
        }
        // verify:
        if (poolsWithStats < poolsFound) {
            throw new RuntimeException("pools found with zero stats");
        }

        if (collectorsWithTime<collectorsFound) {
            throw new RuntimeException("collectors found with zero time");
        }
        System.out.println("Test passed.");
    }

    public void allocationWork(long target) {

        long sizeAllocated = 0;
        List<byte[]> list = new LinkedList<>();
        long delay = 50;
        long count = 0;

        while (sizeAllocated < target) {
            int size = 1024*1024;
            byte [] alloc = new byte[size];
            if (count % 2 == 0) {
                list.add(alloc);
                sizeAllocated+=size;
                System.out.print(".");
            }
            try { Thread.sleep(delay); } catch (InterruptedException ie) { }
            count++;
        }
    }

}
