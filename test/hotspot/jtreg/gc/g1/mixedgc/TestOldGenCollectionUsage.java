/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestOldGenCollectionUsage.java
 * @bug 8195115
 * @summary G1 Old Gen's CollectionUsage.used is zero after mixed GC which is incorrect
 * @key gc
 * @requires vm.gc.G1
 * @requires vm.opt.MaxGCPauseMillis == "null"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @modules java.management
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -verbose:gc -XX:SurvivorRatio=1 -Xmx12m -Xms12m -XX:MaxTenuringThreshold=1 -XX:InitiatingHeapOccupancyPercent=100 -XX:-G1UseAdaptiveIHOP -XX:G1MixedGCCountTarget=4 -XX:MaxGCPauseMillis=30000 -XX:G1HeapRegionSize=1m -XX:G1HeapWastePercent=0 -XX:G1MixedGCLiveThresholdPercent=100 TestOldGenCollectionUsage
 */

import jdk.test.lib.Asserts;
import sun.hotspot.WhiteBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import java.lang.management.*;

// 8195115 says that for the "G1 Old Gen" MemoryPool, CollectionUsage.used
// is zero for G1 after a mixed collection, which is incorrect.

public class TestOldGenCollectionUsage {

    private String poolName = "G1 Old Gen";
    private String collectorName = "G1 Young Generation";

    public static void main(String [] args) throws Exception {
        TestOldGenCollectionUsage t = new TestOldGenCollectionUsage();
        t.run();
    }

    public TestOldGenCollectionUsage() {
        System.out.println("Monitor G1 Old Gen pool with G1 Young Generation collector.");
    }

    public void run() {
        // Find memory pool and collector
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        MemoryPoolMXBean pool = null;
        boolean foundPool = false;
        for (int i = 0; i < pools.size(); i++) {
            pool = pools.get(i);
            String name = pool.getName();
            if (name.contains(poolName)) {
                System.out.println("Found pool: " + name);
                foundPool = true;
                break;
            }
        }
        if (!foundPool) {
            throw new RuntimeException(poolName + " not found, test with -XX:+UseG1GC");
        }

        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        GarbageCollectorMXBean collector = null;
        boolean foundCollector = false;
        for (int i = 0; i < collectors.size(); i++) {
            collector = collectors.get(i);
            String name = collector.getName();
            if (name.contains(collectorName)) {
                System.out.println("Found collector: " + name);
                foundCollector = true;
                break;
            }
        }
        if (!foundCollector) {
            throw new RuntimeException(collectorName + " not found, test with -XX:+UseG1GC");
        }

        MixedGCProvoker gcProvoker = new MixedGCProvoker();
        gcProvoker.allocateOldObjects();

        // Verify no non-zero result was stored
        long usage = pool.getCollectionUsage().getUsed();
        System.out.println(poolName + ": usage after GC = " + usage);
        if (usage > 0) {
            throw new RuntimeException("Premature mixed collections(s)");
        }

        // Verify that collections were done
        long collectionCount = collector.getCollectionCount();
        System.out.println(collectorName + ": collection count = "
                           + collectionCount);
        long collectionTime = collector.getCollectionTime();
        System.out.println(collectorName + ": collection time  = "
                           + collectionTime);
        if (collectionCount <= 0) {
            throw new RuntimeException("Collection count <= 0");
        }
        if (collectionTime <= 0) {
            throw new RuntimeException("Collector has not run");
        }

        gcProvoker.provokeMixedGC();

        usage = pool.getCollectionUsage().getUsed();
        System.out.println(poolName + ": usage after GC = " + usage);
        if (usage <= 0) {
            throw new RuntimeException(poolName + " found with zero usage");
        }

        long newCollectionCount = collector.getCollectionCount();
        System.out.println(collectorName + ": collection count = "
                           + newCollectionCount);
        long newCollectionTime = collector.getCollectionTime();
        System.out.println(collectorName + ": collection time  = "
                           + newCollectionTime);
        if (newCollectionCount <= collectionCount) {
            throw new RuntimeException("No new collection");
        }
        if (newCollectionTime <= collectionTime) {
            throw new RuntimeException("Collector has not run some more");
        }

        System.out.println("Test passed.");
    }

    /**
     * Utility class to guarantee a mixed GC. The class allocates several arrays and
     * promotes them to the oldgen. After that it tries to provoke mixed GC by
     * allocating new objects.
     *
     * The necessary condition for guaranteed mixed GC is running MixedGCProvoker is
     * running in VM with the following flags: -XX:MaxTenuringThreshold=1 -Xms12M
     * -Xmx12M -XX:G1MixedGCLiveThresholdPercent=100 -XX:G1HeapWastePercent=0
     * -XX:G1HeapRegionSize=1m
     */
    public class MixedGCProvoker {
        private final WhiteBox WB = WhiteBox.getWhiteBox();
        private final List<byte[]> liveOldObjects = new ArrayList<>();
        private final List<byte[]> newObjects = new ArrayList<>();

        public static final int ALLOCATION_SIZE = 20000;
        public static final int ALLOCATION_COUNT = 15;

        public void allocateOldObjects() {
            List<byte[]> deadOldObjects = new ArrayList<>();
            // Allocates buffer and promotes it to the old gen. Mix live and dead old
            // objects
            for (int i = 0; i < ALLOCATION_COUNT; ++i) {
                liveOldObjects.add(new byte[ALLOCATION_SIZE * 5]);
                deadOldObjects.add(new byte[ALLOCATION_SIZE * 5]);
            }

            // Do two young collections, MaxTenuringThreshold=1 will force promotion.
            // G1HeapRegionSize=1m guarantees that old gen regions will be filled.
            WB.youngGC();
            WB.youngGC();
            // Check it is promoted & keep alive
            Asserts.assertTrue(WB.isObjectInOldGen(liveOldObjects),
                               "List of the objects is suppose to be in OldGen");
            Asserts.assertTrue(WB.isObjectInOldGen(deadOldObjects),
                               "List of the objects is suppose to be in OldGen");
        }

        /**
         * Waits until Concurent Mark Cycle finishes
         * @param wb  Whitebox instance
         * @param sleepTime sleep time
         */
        private void waitTillCMCFinished(int sleepTime) {
            while (WB.g1InConcurrentMark()) {
                if (sleepTime > -1) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        System.out.println("Got InterruptedException while waiting for ConcMarkCycle to finish");
                    }
                }
            }
        }

        public void provokeMixedGC() {
            waitTillCMCFinished(0);
            WB.g1StartConcMarkCycle();
            waitTillCMCFinished(0);
            WB.youngGC();

            System.out.println("Allocating new objects to provoke mixed GC");
            // Provoke a mixed collection. G1MixedGCLiveThresholdPercent=100
            // guarantees that full old gen regions will be included.
            for (int i = 0; i < (ALLOCATION_COUNT * 20); i++) {
                newObjects.add(new byte[ALLOCATION_SIZE]);
            }
            // check that liveOldObjects still alive
            Asserts.assertTrue(WB.isObjectInOldGen(liveOldObjects),
                               "List of the objects is suppose to be in OldGen");
        }

    }

}
