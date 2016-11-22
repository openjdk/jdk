/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * This is an class used to provoke GC and perform other GC-related
 * procedures
 *
 */
public class GcProvoker{

    // Uses fixed small objects to avoid Humongous objects allocation in G1
    public static final int MEMORY_CHUNK = 2048;
    public static final float ALLOCATION_TOLERANCE = 0.05f;

    public static List<Object> allocatedMetaspace;
    public static List<Object> allocatedMemory;

    private final Runtime runtime;

    private List<Object> allocateHeap(float targetUsage) {
        long maxMemory = runtime.maxMemory();
        List<Object> list = new ArrayList<>();
        long used = 0;
        long target = (long) (maxMemory * targetUsage);
        while (used < target) {
            try {
                list.add(new byte[MEMORY_CHUNK]);
                used += MEMORY_CHUNK;
            } catch (OutOfMemoryError e) {
                list = null;
                throw new RuntimeException("Unexpected OOME '" + e.getMessage() + "' while eating " + targetUsage + " of heap memory.");
            }
        }
        return list;
    }

    private List<Object> allocateAvailableHeap(float targetUsage) {
        // Calculates size of free memory after allocation with small tolerance.
        long minFreeMemory = (long) ((1.0 - (targetUsage + ALLOCATION_TOLERANCE)) * runtime.maxMemory());
        List<Object> list = new ArrayList<>();
        do {
            try {
                list.add(new byte[MEMORY_CHUNK]);
            } catch (OutOfMemoryError e) {
                list = null;
                throw new RuntimeException("Unexpected OOME '" + e.getMessage() + "' while eating " + targetUsage + " of heap memory.");
            }
        } while (runtime.freeMemory() > minFreeMemory);
        return list;
    }

    /**
     * This method provokes a GC
     */
    public void provokeGc() {
        for (int i = 0; i < 3; i++) {
            long edenSize = Pools.getEdenCommittedSize();
            long heapSize = Pools.getHeapCommittedSize();
            float targetPercent = ((float) edenSize) / (heapSize);
            if ((targetPercent < 0) || (targetPercent > 1.0)) {
                throw new RuntimeException("Error in the percent calculation" + " (eden size: " + edenSize + ", heap size: " + heapSize + ", calculated eden percent: " + targetPercent + ")");
            }
            allocateHeap(targetPercent);
            allocateHeap(targetPercent);
            System.gc();
        }
    }

    /**
     * Allocates heap and metaspace upon exit not less than targetMemoryUsagePercent percents
     * of heap and metaspace have been consumed.
     *
     * @param targetMemoryUsagePercent how many percent of heap and metaspace to
     * allocate
     */

    public void allocateMetaspaceAndHeap(float targetMemoryUsagePercent) {
        // Metaspace should be filled before Java Heap to prevent unexpected OOME
        // in the Java Heap while filling Metaspace
        allocatedMetaspace = eatMetaspace(targetMemoryUsagePercent);
        allocatedMemory = allocateHeap(targetMemoryUsagePercent);
    }

    /**
     * Allocates heap and metaspace upon exit targetMemoryUsagePercent percents
     * of heap and metaspace have been consumed.
     *
     * @param targetMemoryUsagePercent how many percent of heap and metaspace to
     * allocate
     */
    public void allocateAvailableMetaspaceAndHeap(float targetMemoryUsagePercent) {
        // Metaspace should be filled before Java Heap to prevent unexpected OOME
        // in the Java Heap while filling Metaspace
        allocatedMetaspace = eatMetaspace(targetMemoryUsagePercent);
        allocatedMemory = allocateAvailableHeap(targetMemoryUsagePercent);
    }

    private List<Object> eatMetaspace(float targetUsage) {
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
                throw new RuntimeException("Unexpected OOME '" + oome.getMessage() + "' while eating " + targetUsage + " of Metaspace.");
            }
            MemoryUsage memoryUsage = metaspacePool.getUsage();
            currentUsage = (((float) memoryUsage.getUsed()) / memoryUsage.getMax());
        } while (currentUsage < targetUsage);
        return list;
    }

    public GcProvoker() {
        runtime = Runtime.getRuntime();
    }

}
