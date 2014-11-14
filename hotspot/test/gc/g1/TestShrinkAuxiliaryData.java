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

import static com.oracle.java.testlibrary.Asserts.assertLessThanOrEqual;
import com.oracle.java.testlibrary.OutputAnalyzer;
import com.oracle.java.testlibrary.Platform;
import com.oracle.java.testlibrary.ProcessTools;
import com.oracle.java.testlibrary.Utils;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import sun.misc.Unsafe;

public class TestShrinkAuxiliaryData {

    private final static String[] initialOpts = new String[]{
        "-XX:MinHeapFreeRatio=10",
        "-XX:MaxHeapFreeRatio=11",
        "-XX:+UseG1GC",
        "-XX:G1HeapRegionSize=1m",
        "-XX:-ExplicitGCInvokesConcurrent",
        "-XX:+PrintGCDetails"
    };

    private final int RSetCacheSize;

    protected TestShrinkAuxiliaryData(int RSetCacheSize) {
        this.RSetCacheSize = RSetCacheSize;
    }

    protected void test() throws Exception {
        ArrayList<String> vmOpts = new ArrayList();
        Collections.addAll(vmOpts, initialOpts);

        int maxCacheSize = Math.max(0, Math.min(31, getMaxCacheSize()));
        if (maxCacheSize < RSetCacheSize) {
            System.out.format("Skiping test for %d cache size due max cache size %d",
                    RSetCacheSize, maxCacheSize
            );
            return;
        }

        printTestInfo(maxCacheSize);

        vmOpts.add("-XX:G1ConcRSLogCacheSize=" + RSetCacheSize);

        vmOpts.addAll(Arrays.asList(Utils.getFilteredTestJavaOpts(
                ShrinkAuxiliaryDataTest.prohibitedVmOptions)));

        // for 32 bits ObjectAlignmentInBytes is not a option
        if (Platform.is32bit()) {
            ArrayList<String> vmOptsWithoutAlign = new ArrayList(vmOpts);
            vmOptsWithoutAlign.add(ShrinkAuxiliaryDataTest.class.getName());
            performTest(vmOptsWithoutAlign);
            return;
        }

        for (int alignment = 3; alignment <= 8; alignment++) {
            ArrayList<String> vmOptsWithAlign = new ArrayList(vmOpts);
            vmOptsWithAlign.add("-XX:ObjectAlignmentInBytes="
                    + (int) Math.pow(2, alignment));
            vmOptsWithAlign.add(ShrinkAuxiliaryDataTest.class.getName());

            performTest(vmOptsWithAlign);
        }
    }

    private void performTest(List<String> opts) throws Exception {
        ProcessBuilder pb
                       = ProcessTools.createJavaProcessBuilder(
                opts.toArray(new String[opts.size()])
        );

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
    }

    private void printTestInfo(int maxCacheSize) {

        DecimalFormat grouped = new DecimalFormat("000,000");
        DecimalFormatSymbols formatSymbols = grouped.getDecimalFormatSymbols();
        formatSymbols.setGroupingSeparator(' ');
        grouped.setDecimalFormatSymbols(formatSymbols);

        System.out.format("Test will use %s bytes of memory of %s available%n"
                + "Available memory is %s with %d bytes pointer size - can save %s pointers%n"
                + "Max cache size: 2^%d = %s elements%n",
                grouped.format(ShrinkAuxiliaryDataTest.getMemoryUsedByTest()),
                grouped.format(Runtime.getRuntime().freeMemory()),
                grouped.format(Runtime.getRuntime().freeMemory()
                        - ShrinkAuxiliaryDataTest.getMemoryUsedByTest()),
                Unsafe.ADDRESS_SIZE,
                grouped.format((Runtime.getRuntime().freeMemory()
                        - ShrinkAuxiliaryDataTest.getMemoryUsedByTest())
                        / Unsafe.ADDRESS_SIZE),
                maxCacheSize,
                grouped.format((int) Math.pow(2, maxCacheSize))
        );
    }

    /**
     * Detects maximum possible size of G1ConcRSLogCacheSize available for
     * current process based on maximum available process memory size
     *
     * @return power of two
     */
    private static int getMaxCacheSize() {
        long availableMemory = Runtime.getRuntime().freeMemory()
                - ShrinkAuxiliaryDataTest.getMemoryUsedByTest() - 1l;
        if (availableMemory <= 0) {
            return 0;
        }
        long availablePointersCount = availableMemory / Unsafe.ADDRESS_SIZE;
        return (63 - (int) Long.numberOfLeadingZeros(availablePointersCount));
    }

    static class ShrinkAuxiliaryDataTest {

        public static void main(String[] args) throws IOException {
            int iterateCount = DEFAULT_ITERATION_COUNT;

            if (args.length > 0) {
                try {
                    iterateCount = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    //num_iterate remains default
                }
            }

            new ShrinkAuxiliaryDataTest().test(iterateCount);
        }

        class GarbageObject {

            private final List<byte[]> payload = new ArrayList();
            private final List<GarbageObject> ref = new LinkedList();

            public GarbageObject(int size) {
                payload.add(new byte[size]);
            }

            public void addRef(GarbageObject g) {
                ref.add(g);
            }

            public void mutate() {
                if (!payload.isEmpty() && payload.get(0).length > 0) {
                    payload.get(0)[0] = (byte) (Math.random() * Byte.MAX_VALUE);
                }
            }
        }

        private final List<GarbageObject> garbage = new ArrayList();

        public void test(int num_iterate) throws IOException {

            allocate();
            link();
            mutate();
            deallocate();

            MemoryUsage muBeforeHeap
                        = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            MemoryUsage muBeforeNonHeap
                        = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();

            for (int i = 0; i < num_iterate; i++) {
                allocate();
                link();
                mutate();
                deallocate();
            }

            System.gc();
            MemoryUsage muAfterHeap
                        = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            MemoryUsage muAfterNonHeap
                        = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();

            assertLessThanOrEqual(muAfterHeap.getCommitted(), muBeforeHeap.getCommitted(),
                    String.format("heap decommit failed - after > before: %d > %d",
                            muAfterHeap.getCommitted(), muBeforeHeap.getCommitted()
                    )
            );

            if (muAfterHeap.getCommitted() < muBeforeHeap.getCommitted()) {
                assertLessThanOrEqual(muAfterNonHeap.getCommitted(), muBeforeNonHeap.getCommitted(),
                        String.format("non-heap decommit failed - after > before: %d > %d",
                                muAfterNonHeap.getCommitted(), muBeforeNonHeap.getCommitted()
                        )
                );
            }
        }

        private void allocate() {
            for (int r = 0; r < REGIONS_TO_ALLOCATE; r++) {
                for (int i = 0; i < NUM_OBJECTS_PER_REGION; i++) {
                    GarbageObject g = new GarbageObject(REGION_SIZE
                            / NUM_OBJECTS_PER_REGION);
                    garbage.add(g);
                }
            }
        }

        /**
         * Iterate through all allocated objects, and link to objects in another
         * regions
         */
        private void link() {
            for (int ig = 0; ig < garbage.size(); ig++) {
                int regionNumber = ig / NUM_OBJECTS_PER_REGION;

                for (int i = 0; i < NUM_LINKS; i++) {
                    int regionToLink;
                    do {
                        regionToLink = (int) (Math.random()
                                * REGIONS_TO_ALLOCATE);
                    } while (regionToLink == regionNumber);

                    // get random garbage object from random region
                    garbage.get(ig).addRef(garbage.get(regionToLink
                            * NUM_OBJECTS_PER_REGION + (int) (Math.random()
                            * NUM_OBJECTS_PER_REGION)));
                }
            }
        }

        private void mutate() {
            for (int ig = 0; ig < garbage.size(); ig++) {
                garbage.get(ig).mutate();
            }
        }

        private void deallocate() {
            garbage.clear();
            System.gc();
        }

        static long getMemoryUsedByTest() {
            return REGIONS_TO_ALLOCATE * REGION_SIZE;
        }

        private static final int REGION_SIZE = 1024 * 1024;
        private static final int DEFAULT_ITERATION_COUNT = 1;   // iterate main scenario
        private static final int REGIONS_TO_ALLOCATE = 5;
        private static final int NUM_OBJECTS_PER_REGION = 10;
        private static final int NUM_LINKS = 20; // how many links create for each object

        private static final String[] prohibitedVmOptions = {
            // remove this when @requires option will be on duty
            "-XX:\\+UseParallelGC",
            "-XX:\\+UseSerialGC",
            "-XX:\\+UseConcMarkSweepGC",
            "-XX:\\+UseParallelOldGC",
            "-XX:\\+UseParNewGC",
            "-Xconcgc",
            "-Xincgc"
        };
    }
}
