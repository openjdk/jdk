/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestShrinkDefragmentedHeap
 * @bug 8038423
 * @summary Verify that heap shrinks after GC in the presence of fragmentation due to humongous objects
 *     1. allocate small objects mixed with humongous ones
 *        "ssssHssssHssssHssssHssssHssssHssssH"
 *     2. release all allocated object except the last humongous one
 *        "..................................H"
 *     3. invoke gc and check that memory returned to the system (amount of committed memory got down)
 *
 * @library /testlibrary
 */
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import sun.management.ManagementFactoryHelper;
import static com.oracle.java.testlibrary.Asserts.*;
import com.oracle.java.testlibrary.ProcessTools;
import com.oracle.java.testlibrary.OutputAnalyzer;

public class TestShrinkDefragmentedHeap {
    // Since we store all the small objects, they become old and old regions are also allocated at the bottom of the heap
    // together with humongous regions. So if there are a lot of old regions in the lower part of the heap,
    // the humongous regions will be allocated in the upper part of the heap anyway.
    // To avoid this the Eden needs to be big enough to fit all the small objects.
    private static final int INITIAL_HEAP_SIZE  = 200 * 1024 * 1024;
    private static final int MINIMAL_YOUNG_SIZE = 190 * 1024 * 1024;
    private static final int REGION_SIZE        = 1 * 1024 * 1024;

    public static void main(String[] args) throws Exception, Throwable {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:InitialHeapSize=" + INITIAL_HEAP_SIZE,
                "-Xmn" + MINIMAL_YOUNG_SIZE,
                "-XX:MinHeapFreeRatio=10",
                "-XX:MaxHeapFreeRatio=11",
                "-XX:+UseG1GC",
                "-XX:G1HeapRegionSize=" + REGION_SIZE,
                "-verbose:gc",
                GCTest.class.getName()
        );

        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldHaveExitValue(0);
    }

    static class GCTest {

        private static final String MIN_FREE_RATIO_FLAG_NAME = "MinHeapFreeRatio";
        private static final String MAX_FREE_RATIO_FLAG_NAME = "MaxHeapFreeRatio";
        private static final String NEW_SIZE_FLAG_NAME = "NewSize";

        private static final ArrayList<ArrayList<byte[]>> garbage = new ArrayList<>();

        private static final int SMALL_OBJS_SIZE  = 10 * 1024; // 10kB
        private static final int SMALL_OBJS_COUNT = MINIMAL_YOUNG_SIZE / (SMALL_OBJS_SIZE-1);
        private static final int ALLOCATE_COUNT = 3;
        // try to put all humongous object into gap between min young size and initial heap size
        // to avoid implicit GCs
        private static final int HUMONG_OBJS_SIZE = (int) Math.max(
                (INITIAL_HEAP_SIZE - MINIMAL_YOUNG_SIZE) / ALLOCATE_COUNT / 4,
                REGION_SIZE * 1.1
        );

        private static final long initialHeapSize = getHeapMemoryUsage().getUsed();

        public static void main(String[] args) throws InterruptedException {
            new GCTest().test();
        }

        private void test() throws InterruptedException {
            MemoryUsagePrinter.printMemoryUsage("init");

            allocate();
            System.gc();
            MemoryUsage muFull = getHeapMemoryUsage();
            MemoryUsagePrinter.printMemoryUsage("allocated");

            free();
            //Thread.sleep(1000); // sleep before measures due lags in JMX
            MemoryUsage muFree = getHeapMemoryUsage();
            MemoryUsagePrinter.printMemoryUsage("free");

            assertLessThan(muFree.getCommitted(), muFull.getCommitted(), prepareMessageCommittedIsNotLess() );
        }

        private void allocate() {
            System.out.format("Will allocate objects of small size = %s and humongous size = %s",
                    MemoryUsagePrinter.humanReadableByteCount(SMALL_OBJS_SIZE, false),
                    MemoryUsagePrinter.humanReadableByteCount(HUMONG_OBJS_SIZE, false)
            );

            for (int i = 0; i < ALLOCATE_COUNT; i++) {
                ArrayList<byte[]> stuff = new ArrayList<>();
                allocateList(stuff, SMALL_OBJS_COUNT / ALLOCATE_COUNT, SMALL_OBJS_SIZE);
                garbage.add(stuff);

                ArrayList<byte[]> humongousStuff = new ArrayList<>();
                allocateList(humongousStuff, 4, HUMONG_OBJS_SIZE);
                garbage.add(humongousStuff);
            }
        }

        private void free() {
            // do not free last one list
            garbage.subList(0, garbage.size() - 1).clear();

            // do not free last one element from last list
            ArrayList stuff = garbage.get(garbage.size() - 1);
            if (stuff.size() > 1) {
                stuff.subList(0, stuff.size() - 1).clear();
            }
            System.gc();
        }

        private String prepareMessageCommittedIsNotLess() {
            return String.format(
                    "committed free heap size is not less than committed full heap size, heap hasn't been shrunk?%n"
                    + "%s = %s%n%s = %s",
                    MIN_FREE_RATIO_FLAG_NAME,
                    ManagementFactoryHelper.getDiagnosticMXBean().getVMOption(MIN_FREE_RATIO_FLAG_NAME).getValue(),
                    MAX_FREE_RATIO_FLAG_NAME,
                    ManagementFactoryHelper.getDiagnosticMXBean().getVMOption(MAX_FREE_RATIO_FLAG_NAME).getValue()
            );
        }

        private static void allocateList(List garbage, int count, int size) {
            for (int i = 0; i < count; i++) {
                garbage.add(new byte[size]);
            }
        }
    }

    static MemoryUsage getHeapMemoryUsage() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    }

    /**
     * Prints memory usage to standard output
     */
    static class MemoryUsagePrinter {

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
}
