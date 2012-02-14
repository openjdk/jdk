/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test TestFullGCount.java
 * @bug 7072527
 * @summary CMS: JMM GC counters overcount in some cases
 * @run main/othervm -XX:+UseConcMarkSweepGC TestFullGCCount
 *
 */
import java.util.*;
import java.lang.management.*;

public class TestFullGCCount {

    public String collectorName = "ConcurrentMarkSweep";

    public static void main(String [] args) {

        TestFullGCCount t = null;
        if (args.length==2) {
            t = new TestFullGCCount(args[0], args[1]);
        } else {
            t = new TestFullGCCount();
        }
        System.out.println("Monitoring collector: " + t.collectorName);
        t.run();
    }

    public TestFullGCCount(String pool, String collector) {
        collectorName = collector;
    }

    public TestFullGCCount() {
    }

    public void run() {
        int count = 0;
        int iterations = 20;
        long counts[] = new long[iterations];
        boolean diffAlways2 = true; // assume we will fail

        for (int i=0; i<iterations; i++) {
            System.gc();
            counts[i] = getCollectionCount();
            if (i>0) {
                if (counts[i] - counts[i-1] != 2) {
                    diffAlways2 = false;
                }
            }
        }
        if (diffAlways2) {
            throw new RuntimeException("FAILED: System.gc must be incrementing count twice.");
        }
        System.out.println("Passed.");
    }

    private long getCollectionCount() {
        long count = 0;
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        for (int i=0; i<collectors.size(); i++) {
            GarbageCollectorMXBean collector = collectors.get(i);
            String name = collector.getName();
            if (name.contains(collectorName)) {
                System.out.println(name + ": collection count = "
                                   + collector.getCollectionCount());
                count = collector.getCollectionCount();
            }
        }
        return count;
    }

}

