/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

package gc.g1;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import jdk.test.whitebox.WhiteBox;

public class TestShrinkAuxiliaryData {
    private static final Random RNG = Utils.getRandomInstance();

    private static final int REGION_SIZE = 1024 * 1024;

    private final static String[] initialOpts = new String[]{
        "-XX:NewSize=16m",
        "-XX:MinHeapFreeRatio=10",
        "-XX:MaxHeapFreeRatio=11",
        "-XX:+UseG1GC",
        "-XX:G1HeapRegionSize=" + REGION_SIZE,
        "-XX:-ExplicitGCInvokesConcurrent",
        "-Xlog:gc=debug",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+WhiteBoxAPI",
        "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
        "-Xbootclasspath/a:.",
    };

    protected TestShrinkAuxiliaryData() {
    }

    protected void test() throws Exception {
        ArrayList<String> vmOpts = new ArrayList<>();
        Collections.addAll(vmOpts, initialOpts);

        printTestInfo();

        // for 32 bits ObjectAlignmentInBytes is not a option
        if (Platform.is32bit()) {
            ArrayList<String> vmOptsWithoutAlign = new ArrayList<>(vmOpts);
            vmOptsWithoutAlign.add(ShrinkAuxiliaryDataTest.class.getName());
            performTest(vmOptsWithoutAlign);
            return;
        }

        for (int alignment = 3; alignment <= 8; alignment++) {
            ArrayList<String> vmOptsWithAlign = new ArrayList<>(vmOpts);
            vmOptsWithAlign.add("-XX:ObjectAlignmentInBytes="
                    + (int) Math.pow(2, alignment));
            vmOptsWithAlign.add(ShrinkAuxiliaryDataTest.class.getName());

            performTest(vmOptsWithAlign);
        }
    }

    private void performTest(List<String> opts) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(opts);

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        System.out.println(output.getStdout());
        System.err.println(output.getStderr());
        output.shouldHaveExitValue(0);
    }

    private void printTestInfo() {
        DecimalFormat grouped = new DecimalFormat("000,000");
        DecimalFormatSymbols formatSymbols = grouped.getDecimalFormatSymbols();
        formatSymbols.setGroupingSeparator(' ');
        grouped.setDecimalFormatSymbols(formatSymbols);

        System.out.format(
                "Test will use %s bytes of memory of %s available%n",
                grouped.format(ShrinkAuxiliaryDataTest.getMemoryUsedByTest()),
                grouped.format(Runtime.getRuntime().maxMemory())
        );
    }

    static class ShrinkAuxiliaryDataTest {

        public static void main(String[] args) throws Exception {

            ShrinkAuxiliaryDataTest testCase = new ShrinkAuxiliaryDataTest();

            if (!testCase.checkEnvApplicability()) {
                return;
            }

            testCase.test();
        }

        /**
         * Checks is this environment suitable to run this test
         * - memory is enough to decommit (page size is not big)
         *
         * @return true if test could run, false if test should be skipped
         */
        protected boolean checkEnvApplicability() {

            int pageSize = WhiteBox.getWhiteBox().getVMPageSize();
            // Auxiliary data size is about ~1.9% of heap size.
            int auxDataSize = REGION_SIZE * REGIONS_TO_ALLOCATE * 19 / 1000;
            System.out.println( "Page size = " + pageSize
                    + " region size = " + REGION_SIZE
                    + " aux data ~= " + auxDataSize);
            // If auxdata size will be less than page size it wouldn't decommit.
            if (pageSize >= auxDataSize) {
                System.out.format("Skipping test for too large page size = %d",
                       pageSize
                );
                return false;
            }

            if (REGION_SIZE * REGIONS_TO_ALLOCATE > Runtime.getRuntime().maxMemory()) {
                System.out.format("Skipping test for too low available memory. "
                        + "Need %d, available %d",
                        REGION_SIZE * REGIONS_TO_ALLOCATE,
                        Runtime.getRuntime().maxMemory()
                );
                return false;
            }

            return true;
        }

        class GarbageObject {

            private final List<byte[]> payload = new ArrayList<>();
            private final List<GarbageObject> ref = new LinkedList<>();

            public GarbageObject(int size) {
                payload.add(new byte[size]);
            }

            public void addRef(GarbageObject g) {
                ref.add(g);
            }

            public void mutate() {
                if (!payload.isEmpty() && payload.get(0).length > 0) {
                    payload.get(0)[0] = (byte) (RNG.nextDouble() * Byte.MAX_VALUE);
                }
            }
        }

        private final List<GarbageObject> garbage = new ArrayList<>();

        public void test() throws Exception {

            MemoryUsage muFull, muFree, muAuxDataFull, muAuxDataFree;
            float auxFull, auxFree;

            allocate();
            link();
            mutate();

            muFull = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            muAuxDataFull = WhiteBox.getWhiteBox().g1AuxiliaryMemoryUsage();

            System.out.format("Full -- heap capacity: %d, Aux data: %d\n",
                    muFull.getCommitted(), muAuxDataFull.getUsed()
            );

            deallocate();
            System.gc();

            if (WhiteBox.getWhiteBox().g1HasRegionsToUncommit()) {
                System.out.println("Waiting for concurrent uncommit to complete");
                do {
                    Thread.sleep(1000);
                } while(WhiteBox.getWhiteBox().g1HasRegionsToUncommit());
                System.out.println("Concurrent uncommit done");
            }

            muFree = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            muAuxDataFree = WhiteBox.getWhiteBox().g1AuxiliaryMemoryUsage();

            System.out.format("Free -- heap capacity: %d, Aux data: %d\n",
                    muFree.getCommitted(), muAuxDataFree.getUsed()
            );

            Asserts.assertLessThan(muFree.getCommitted(), muFull.getCommitted(),
                                   "heap decommit failed"
            );

            Asserts.assertLessThan(muAuxDataFree.getUsed(), muAuxDataFull.getUsed(),
                                   "auxiliary data decommit failed"
            );
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
                        regionToLink = (int) (RNG.nextDouble() * REGIONS_TO_ALLOCATE);
                    } while (regionToLink == regionNumber);

                    // get random garbage object from random region
                    garbage.get(ig).addRef(garbage.get(regionToLink
                            * NUM_OBJECTS_PER_REGION + (int) (RNG.nextDouble()
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

        private static final int REGIONS_TO_ALLOCATE = 100;
        private static final int NUM_OBJECTS_PER_REGION = 10;
        private static final int NUM_LINKS = 20; // how many links create for each object
    }
}
