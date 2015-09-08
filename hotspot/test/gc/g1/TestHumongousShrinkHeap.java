/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test TestHumongousShrinkHeap
 * @bug 8036025 8056043
 * @requires vm.gc=="G1" | vm.gc=="null"
 * @summary Verify that heap shrinks after GC in the presence of fragmentation
 * due to humongous objects
 * @library /testlibrary
 * @modules java.management/sun.management
 * @run main/othervm -XX:-ExplicitGCInvokesConcurrent -XX:MinHeapFreeRatio=10
 * -XX:MaxHeapFreeRatio=12 -XX:+UseG1GC -XX:G1HeapRegionSize=1M -verbose:gc
 * TestHumongousShrinkHeap
 */

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import static jdk.test.lib.Asserts.*;

public class TestHumongousShrinkHeap {

    public static final String MIN_FREE_RATIO_FLAG_NAME = "MinHeapFreeRatio";
    public static final String MAX_FREE_RATIO_FLAG_NAME = "MaxHeapFreeRatio";

    private static final List<List<byte[]>> garbage = new ArrayList();
    private static final int REGION_SIZE = 1024 * 1024; // 1M
    private static final int LISTS_COUNT = 10;
    private static final int HUMON_SIZE = Math.round(.9f * REGION_SIZE);
    private static final long AVAILABLE_MEMORY
                              = Runtime.getRuntime().freeMemory();
    private static final int HUMON_COUNT
                             = (int) ((AVAILABLE_MEMORY / HUMON_SIZE)
            / LISTS_COUNT);


    public static void main(String[] args) {
        if (HUMON_COUNT == 0) {
            System.out.println("Skipped. Heap is too small");
            return;
        }
        System.out.format("Running with %s max heap size. "
                + "Will allocate humongous object of %s size %d times.%n",
                MemoryUsagePrinter.humanReadableByteCount(AVAILABLE_MEMORY, false),
                MemoryUsagePrinter.humanReadableByteCount(HUMON_SIZE, false),
                HUMON_COUNT
        );
        new TestHumongousShrinkHeap().test();
    }

    private final void test() {
        System.gc();
        MemoryUsagePrinter.printMemoryUsage("init");

        allocate();
        MemoryUsagePrinter.printMemoryUsage("allocated");
        MemoryUsage muFull = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

        free();
        MemoryUsagePrinter.printMemoryUsage("free");
        MemoryUsage muFree = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

        assertLessThan(muFree.getCommitted(), muFull.getCommitted(), String.format(
                "committed free heap size is not less than committed full heap size, heap hasn't been shrunk?%n"
                + "%s = %s%n%s = %s",
                MIN_FREE_RATIO_FLAG_NAME,
                ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                    .getVMOption(MIN_FREE_RATIO_FLAG_NAME).getValue(),
                MAX_FREE_RATIO_FLAG_NAME,
                ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                    .getVMOption(MAX_FREE_RATIO_FLAG_NAME).getValue()
        ));
    }

    private void allocate() {

        for (int i = 0; i < LISTS_COUNT; i++) {
            List<byte[]> stuff = new ArrayList();
            allocateList(stuff, HUMON_COUNT, HUMON_SIZE);
            MemoryUsagePrinter.printMemoryUsage("allocate #" + (i+1));
            garbage.add(stuff);
        }
    }

    private void free() {
        // do not free last one list
        garbage.subList(0, garbage.size() - 1).clear();

        // do not free last one element from last list
        List stuff = garbage.get(garbage.size() - 1);
        stuff.subList(0, stuff.size() - 1).clear();
        System.gc();
    }

    private static void allocateList(List garbage, int count, int size) {
        for (int i = 0; i < count; i++) {
            garbage.add(new byte[size]);
        }
    }
}

/**
 * Prints memory usage to standard output
 */
class MemoryUsagePrinter {

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static void printMemoryUsage(String label) {
        MemoryUsage memusage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        float freeratio = 1f - (float) memusage.getUsed() / memusage.getCommitted();
        System.out.format("[%-24s] init: %-7s, used: %-7s, comm: %-7s, freeRatio ~= %.1f%%%n",
                label,
                humanReadableByteCount(memusage.getInit(), false),
                humanReadableByteCount(memusage.getUsed(), false),
                humanReadableByteCount(memusage.getCommitted(), false),
                freeratio * 100
        );
    }
}
