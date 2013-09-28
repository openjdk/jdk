/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.lang.management.*;

import com.oracle.java.testlibrary.*;
import static com.oracle.java.testlibrary.Asserts.*;

/* @test TestPerfCountersAndMemoryPools
 * @bug 8023476
 * @summary Tests that a MemoryPoolMXBeans and PerfCounters for metaspace
 *          report the same data.
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-UseCompressedOops -XX:-UseCompressedKlassPointers -XX:+UseSerialGC -XX:+UsePerfData TestPerfCountersAndMemoryPools
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UseCompressedOops -XX:+UseCompressedKlassPointers -XX:+UseSerialGC -XX:+UsePerfData TestPerfCountersAndMemoryPools
 */
public class TestPerfCountersAndMemoryPools {
    public static void main(String[] args) throws Exception {
        checkMemoryUsage("Metaspace", "sun.gc.metaspace");

        if (InputArguments.contains("-XX:+UseCompressedKlassPointers") && Platform.is64bit()) {
            checkMemoryUsage("Compressed Class Space", "sun.gc.compressedclassspace");
        }
    }

    private static MemoryUsage getMemoryUsage(String memoryPoolName) {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean pool : pools) {
            if (pool.getName().equals(memoryPoolName)) {
                return pool.getUsage();
            }
        }

        throw new RuntimeException("Excpted to find a memory pool with name " +
                                   memoryPoolName);
    }

    private static void checkMemoryUsage(String memoryPoolName, String perfNS)
        throws Exception {
        // Need to do a gc before each comparison to update the perf counters

        System.gc();
        MemoryUsage mu = getMemoryUsage(memoryPoolName);
        assertEQ(getMinCapacity(perfNS), mu.getInit());

        System.gc();
        mu = getMemoryUsage(memoryPoolName);
        assertEQ(getUsed(perfNS), mu.getUsed());

        System.gc();
        mu = getMemoryUsage(memoryPoolName);
        assertEQ(getCapacity(perfNS), mu.getCommitted());
    }

    private static long getMinCapacity(String ns) throws Exception {
        return PerfCounters.findByName(ns + ".minCapacity").longValue();
    }

    private static long getCapacity(String ns) throws Exception {
        return PerfCounters.findByName(ns + ".capacity").longValue();
    }

    private static long getUsed(String ns) throws Exception {
        return PerfCounters.findByName(ns + ".used").longValue();
    }
}
