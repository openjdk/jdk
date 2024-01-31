/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

/**
 * This is an class used to provoke GC and perform other GC-related
 * procedures
 *
 */
public class GcProvoker{

    // Uses fixed small objects to avoid Humongous objects allocation in G1
    private static final int MEMORY_CHUNK = 2048;

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

    /**
     * This method provokes a GC
     */
    public void provokeGc() {
        float targetFraction = 0;
        // Read sizes of eden and overall heap, to find eden size as a fraction of heap.
        // Recognise if heap is changing size, and retry (heap is expected to initially shrink).
        for (int i = 0; i < 3; i++) {
            // One retry is expected occasionally.  More "should not happen":
            if (i == 2) {
                throw new RuntimeException("Cannot calculate targetFraction.  Heap size not stable.");
            }
            long heapSize0 = Pools.getHeapCommittedSize();
            long edenSize = Pools.getEdenCommittedSize();
            long heapSize = Pools.getHeapCommittedSize();
            if (heapSize < heapSize0) {
                System.out.println("provokeGc: Heap shrinking, retry. eden: " + edenSize + ", heap0: " + heapSize0 + ", heap: " + heapSize);
                System.gc();
                continue;
            }
            targetFraction = ((float) edenSize) / (heapSize);
            if ((targetFraction < 0) || (targetFraction > 1.0)) {
                throw new RuntimeException("Error in fraction calculation" + " (eden size: " + edenSize + ", heap size: " + heapSize
                                           + ", calculated eden fraction: " + targetFraction + ")");
            }
            break; // We have found eden as a fraction of heap.
        }
        // Previously these allocations and GC call were in a loop.
        // That appears unnecesary as the goal is simply to cause a GC:
        allocateHeap(targetFraction);
        allocateHeap(targetFraction);
        System.gc();
    }

    public GcProvoker() {
        runtime = Runtime.getRuntime();
    }
}
