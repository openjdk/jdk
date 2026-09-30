/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import jdk.internal.platform.CgroupV1Metrics;
import jdk.internal.platform.Metrics;

public class MetricsMemoryTester {

    public static final long UNLIMITED = -1;

    public static void main(String[] args) {
        System.out.println(Arrays.toString(args));
        switch (args[0]) {
            case "memory":
                testMemoryLimit(args[1]);
                break;
            case "memoryswap":
                testMemoryAndSwapLimit(args[1], args[2]);
                break;
            case "oomkill":
                testOomKillFlag(Boolean.parseBoolean(args[2]));
                break;
            case "failcount":
                testMemoryFailCount();
                break;
            case "softlimit":
                testMemorySoftLimit(args[1]);
                break;
            default:
                throw new RuntimeException("unknown args: " + args[0] + " for MetricsMemoryTester");
        }
    }

    private static void testMemoryLimit(String value) {
        long limit = getMemoryValue(value);

        if (limit != Metrics.systemMetrics().getMemoryLimit()) {
            throw new RuntimeException("Memory limit not equal, expected : ["
                    + limit + "]" + ", got : ["
                    + Metrics.systemMetrics().getMemoryLimit() + "]");
        }
        System.out.println("TEST PASSED!!!");
    }

    private static void testMemoryFailCount() {
        final Metrics metrics = Metrics.systemMetrics();
        final long memAndSwapLimit = metrics.getMemoryAndSwapLimit();
        final long memLimit = metrics.getMemoryLimit();

        final int M = 1024 * 1024;

        // We need swap to execute this test. Otherwise OOM killer acts with
        // SIGKILL before we read the fail counter.

        final long maxHeapSize = Runtime.getRuntime().maxMemory();
        if (maxHeapSize <= memLimit || maxHeapSize >= memAndSwapLimit) {
            throw new RuntimeException(
                    "Expected memory limit < maximum heap < memory-and-swap limit: "
                            + "memory=" + memLimit / M + "M, "
                            + "heap=" + maxHeapSize / M + "M, "
                            + "memory-and-swap=" + memAndSwapLimit / M + "M");
        }

        final long initialFailCount = metrics.getMemoryFailCount();

        System.out.println("Initial memory fail count: " + initialFailCount);

        // Allocate 512M of data in 1M chunks per iteration
        byte[][] bytes = new byte[512][];

        for (int i = 0; i < 512; i++) {
            if (i % 8 == 0) {
                System.out.printf("Allocated: %3dM, Memory usage: %3dM, Memory and swap: %3dM\n",
                        i,
                        metrics.getMemoryUsage() / M,
                        metrics.getMemoryAndSwapUsage() / M);
            } else {
                System.out.print(".");
            }
            bytes[i] = new byte[M];
            Arrays.fill(bytes[i], (byte) 1);  // dirty every page
            // Break out as soon as we see an increase in failcount
            if (metrics.getMemoryFailCount() > initialFailCount) {
                break;
            }
        }

        // Be sure bytes allocations don't get optimized out
        System.out.println("\nDEBUG: Bytes allocation length 1: " + bytes[0].length);
        final long newCount = metrics.getMemoryFailCount();
        System.out.println("Final memory fail count: " + newCount);

        if (newCount <= initialFailCount) {
            throw new RuntimeException("Memory fail count did not increase: initial="
                    + initialFailCount + ", final=" + newCount);
        }

        System.out.println("TEST PASSED!!!");
    }

    private static void testMemorySoftLimit(String softLimit) {

        long memorySoftLimit = Metrics.systemMetrics().getMemorySoftLimit();
        long newmemorySoftLimit = getMemoryValue(softLimit);

        if (newmemorySoftLimit != memorySoftLimit) {
            throw new RuntimeException("Memory softlimit not equal, Actual : ["
                    + newmemorySoftLimit + "]" + ", Expected : ["
                    + memorySoftLimit + "]");
        }
        System.out.println("TEST PASSED!!!");
    }

    private static void testMemoryAndSwapLimit(String memory, String memAndSwap) {
        long expectedMem = getMemoryValue(memory);
        long expectedMemAndSwap = getMemoryValue(memAndSwap);
        long actualMemAndSwap = Metrics.systemMetrics().getMemoryAndSwapLimit();

        if (expectedMem != Metrics.systemMetrics().getMemoryLimit()
                || (expectedMemAndSwap != actualMemAndSwap
                && expectedMem != actualMemAndSwap)) {
            throw new RuntimeException("Memory and swap limit not equal, expected : ["
                    + expectedMem + ", " + expectedMemAndSwap + "]"
                    + ", got : [" + Metrics.systemMetrics().getMemoryLimit()
                    + ", " + Metrics.systemMetrics().getMemoryAndSwapLimit() + "]");
        }
        System.out.println("TEST PASSED!!!");
    }

    private static long getMemoryValue(String value) {
        long result;
        if (value.endsWith("m")) {
            result = Long.parseLong(value.substring(0, value.length() - 1))
                    * 1024 * 1024;
        } else if (value.endsWith("g")) {
            result = Long.parseLong(value.substring(0, value.length() - 1))
                    * 1024 * 1024 * 1024;
        } else {
            result = Long.parseLong(value);
        }
        return result;
    }

    private static void testOomKillFlag(boolean oomKillFlag) {
        Metrics m = Metrics.systemMetrics();
        if (m instanceof CgroupV1Metrics) {
            CgroupV1Metrics mCgroupV1 = (CgroupV1Metrics)m;
            Boolean expected = Boolean.valueOf(oomKillFlag);
            Boolean actual = mCgroupV1.isMemoryOOMKillEnabled();
            if (!(expected.equals(actual))) {
                throw new RuntimeException("oomKillFlag error");
            }
            System.out.println("TEST PASSED!!!");
        } else {
            throw new RuntimeException("oomKillFlag test not supported for cgroups v2");
        }
    }
}
