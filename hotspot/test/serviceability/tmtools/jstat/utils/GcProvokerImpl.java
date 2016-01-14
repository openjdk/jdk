/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package utils;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * Utilities to provoke GC in various ways
 */
public class GcProvokerImpl implements GcProvoker {

    private static List<Object> eatenMetaspace;
    private static List<Object> eatenMemory;

    static List<Object> eatHeapMemory(float targetUsage) {
        long maxMemory = Runtime.getRuntime().maxMemory();
        // uses fixed small objects to avoid Humongous objects allocation in G1
        int memoryChunk = 2048;
        List<Object> list = new ArrayList<>();
        float used = 0;
        while (used < targetUsage * maxMemory) {
            try {
                list.add(new byte[memoryChunk]);
                used += memoryChunk;
            } catch (OutOfMemoryError e) {
                list = null;
                throw new RuntimeException("Unexpected OOME while eating " + targetUsage + " of heap memory.");
            }
        }
        return list;
    }

    @Override
    public void provokeGc() {
        for (int i = 0; i < 3; i++) {
            long edenSize = Pools.getEdenCommittedSize();
            long heapSize = Pools.getHeapCommittedSize();
            float targetPercent = ((float) edenSize) / (heapSize);
            if ((targetPercent <= 0) || (targetPercent > 1.0)) {
                throw new RuntimeException("Error in the percent calculation" + " (eden size: " + edenSize + ", heap size: " + heapSize + ", calculated eden percent: " + targetPercent + ")");
            }
            eatHeapMemory(targetPercent);
            eatHeapMemory(targetPercent);
            System.gc();
        }
    }

    @Override
    public void eatMetaspaceAndHeap(float targetMemoryUsagePercent) {
        eatenMemory = eatHeapMemory(targetMemoryUsagePercent);
        eatenMetaspace = eatMetaspace(targetMemoryUsagePercent);
    }

    private static List<Object> eatMetaspace(float targetUsage) {
        List<Object> list = new ArrayList<>();
        final String metaspacePoolName = "Metaspace";
        MemoryPoolMXBean metaspacePool = null;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getName().contains(metaspacePoolName)) {
                metaspacePool = pool;
                break;
            }
        }
        if (metaspacePool == null) {
            throw new RuntimeException("MXBean for Metaspace pool wasn't found");
        }
        float currentUsage;
        GeneratedClassProducer gp = new GeneratedClassProducer();
        do {
            try {
                list.add(gp.create(0));
            } catch (OutOfMemoryError oome) {
                list = null;
                throw new RuntimeException("Unexpected OOME while eating " + targetUsage + " of Metaspace.");
            }
            MemoryUsage memoryUsage = metaspacePool.getUsage();
            currentUsage = (((float) memoryUsage.getUsed()) / memoryUsage.getMax());
        } while (currentUsage < targetUsage);
        return list;
    }

    public GcProvokerImpl() {
    }

}
