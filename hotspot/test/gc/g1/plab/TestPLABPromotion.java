/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestPLABPromotion
 * @bug 8141278
 * @summary Test PLAB promotion
 * @requires vm.gc=="G1" | vm.gc=="null"
 * @requires vm.opt.FlightRecorder != true
 * @library /testlibrary /../../test/lib /
 * @modules java.management
 * @build ClassFileInstaller
 *        sun.hotspot.WhiteBox
 *        gc.g1.plab.lib.MemoryConsumer
 *        gc.g1.plab.lib.LogParser
 *        gc.g1.plab.lib.AppPLABPromotion
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/timeout=240 gc.g1.plab.TestPLABPromotion
 */
package gc.g1.plab;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.io.PrintStream;

import gc.g1.plab.lib.AppPLABPromotion;
import gc.g1.plab.lib.LogParser;
import gc.g1.plab.lib.PLABUtils;

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.ProcessTools;
import jdk.test.lib.Platform;

/**
 * Test checks PLAB promotion of different size objects.
 */
public class TestPLABPromotion {

    // GC ID with survivor PLAB statistics
    private final static long GC_ID_SURVIVOR_STATS = 1l;
    // GC ID with old PLAB statistics
    private final static long GC_ID_OLD_STATS = 2l;

    // Allowable difference for memory consumption (percentage)
    private final static long MEM_DIFFERENCE_PCT = 5;

    private static final int PLAB_SIZE_SMALL = 1024;
    private static final int PLAB_SIZE_MEDIUM = 4096;
    private static final int PLAB_SIZE_HIGH = 65536;
    private static final int OBJECT_SIZE_SMALL = 10;
    private static final int OBJECT_SIZE_MEDIUM = 100;
    private static final int OBJECT_SIZE_HIGH = 1000;
    private static final int GC_NUM_SMALL = 1;
    private static final int GC_NUM_MEDIUM = 3;
    private static final int GC_NUM_HIGH = 7;
    private static final int WASTE_PCT_SMALL = 10;
    private static final int WASTE_PCT_MEDIUM = 20;
    private static final int WASTE_PCT_HIGH = 30;
    private static final int YOUNG_SIZE_LOW = 16;
    private static final int YOUNG_SIZE_HIGH = 64;
    private static final boolean PLAB_FIXED = true;
    private static final boolean PLAB_DYNAMIC = false;

    private final static TestCase[] TEST_CASES = {
        // Test cases for unreachable object, PLAB size is fixed
        new TestCase(WASTE_PCT_SMALL, PLAB_SIZE_SMALL, OBJECT_SIZE_MEDIUM, GC_NUM_SMALL, YOUNG_SIZE_LOW, PLAB_FIXED, false, false),
        new TestCase(WASTE_PCT_HIGH, PLAB_SIZE_MEDIUM, OBJECT_SIZE_SMALL, GC_NUM_HIGH, YOUNG_SIZE_HIGH, PLAB_FIXED, false, false),
        // Test cases for reachable objects, PLAB size is fixed
        new TestCase(WASTE_PCT_SMALL, PLAB_SIZE_SMALL, OBJECT_SIZE_SMALL, GC_NUM_HIGH, YOUNG_SIZE_HIGH, PLAB_FIXED, true, true),
        new TestCase(WASTE_PCT_SMALL, PLAB_SIZE_MEDIUM, OBJECT_SIZE_MEDIUM, GC_NUM_SMALL, YOUNG_SIZE_LOW, PLAB_FIXED, true, true),
        new TestCase(WASTE_PCT_SMALL, PLAB_SIZE_SMALL, OBJECT_SIZE_HIGH, GC_NUM_MEDIUM, YOUNG_SIZE_LOW, PLAB_FIXED, true, false),
        new TestCase(WASTE_PCT_MEDIUM, PLAB_SIZE_HIGH, OBJECT_SIZE_SMALL, GC_NUM_HIGH, YOUNG_SIZE_HIGH, PLAB_FIXED, true, true),
        new TestCase(WASTE_PCT_MEDIUM, PLAB_SIZE_SMALL, OBJECT_SIZE_MEDIUM, GC_NUM_SMALL, YOUNG_SIZE_LOW, PLAB_FIXED, true, true),
        new TestCase(WASTE_PCT_MEDIUM, PLAB_SIZE_MEDIUM, OBJECT_SIZE_HIGH, GC_NUM_MEDIUM, YOUNG_SIZE_LOW, PLAB_FIXED, true, true),
        new TestCase(WASTE_PCT_HIGH, PLAB_SIZE_SMALL, OBJECT_SIZE_SMALL, GC_NUM_HIGH, YOUNG_SIZE_HIGH, PLAB_FIXED, true, true),
        new TestCase(WASTE_PCT_HIGH, PLAB_SIZE_HIGH, OBJECT_SIZE_MEDIUM, GC_NUM_SMALL, YOUNG_SIZE_LOW, PLAB_FIXED, true, true),
        new TestCase(WASTE_PCT_HIGH, PLAB_SIZE_SMALL, OBJECT_SIZE_HIGH, GC_NUM_MEDIUM, YOUNG_SIZE_HIGH, PLAB_FIXED, true, false),
        // Test cases for unreachable object, PLAB size is not fixed
        new TestCase(WASTE_PCT_MEDIUM, PLAB_SIZE_MEDIUM, OBJECT_SIZE_SMALL, GC_NUM_HIGH, YOUNG_SIZE_LOW, PLAB_DYNAMIC, false, false),
        // Test cases for reachable objects, PLAB size is not fixed
        new TestCase(WASTE_PCT_SMALL, PLAB_SIZE_HIGH, OBJECT_SIZE_SMALL, GC_NUM_HIGH, YOUNG_SIZE_HIGH, PLAB_DYNAMIC, true, true),
        new TestCase(WASTE_PCT_MEDIUM, PLAB_SIZE_MEDIUM, OBJECT_SIZE_SMALL, GC_NUM_SMALL, YOUNG_SIZE_LOW, PLAB_DYNAMIC, true, true),
        new TestCase(WASTE_PCT_SMALL, PLAB_SIZE_MEDIUM, OBJECT_SIZE_HIGH, GC_NUM_HIGH, YOUNG_SIZE_HIGH, PLAB_DYNAMIC, true, false),
        new TestCase(WASTE_PCT_MEDIUM, PLAB_SIZE_SMALL, OBJECT_SIZE_MEDIUM, GC_NUM_MEDIUM, YOUNG_SIZE_LOW, PLAB_DYNAMIC, true, true),
        new TestCase(WASTE_PCT_HIGH, PLAB_SIZE_HIGH, OBJECT_SIZE_MEDIUM, GC_NUM_SMALL, YOUNG_SIZE_HIGH, PLAB_DYNAMIC, true, true),
        new TestCase(WASTE_PCT_HIGH, PLAB_SIZE_HIGH, OBJECT_SIZE_SMALL, GC_NUM_HIGH, YOUNG_SIZE_LOW, PLAB_DYNAMIC, true, true)
    };

    public static void main(String[] args) throws Throwable {

        for (TestCase testCase : TEST_CASES) {
            // What we going to check.
            testCase.print(System.out);
            List<String> options = PLABUtils.prepareOptions(testCase.toOptions());
            options.add(AppPLABPromotion.class.getName());
            OutputAnalyzer out = ProcessTools.executeTestJvm(options.toArray(new String[options.size()]));
            if (out.getExitValue() != 0) {
                System.out.println(out.getOutput());
                throw new RuntimeException("Expect exit code 0.");
            }
            checkResults(out.getOutput(), testCase);
        }
    }

    private static void checkResults(String output, TestCase testCase) {
        long plabAllocatedSurvivor;
        long directAllocatedSurvivor;
        long plabAllocatedOld;
        long directAllocatedOld;
        long memAllocated = testCase.getMemToFill();
        long wordSize = Platform.is32bit() ? 4l : 8l;
        LogParser logParser = new LogParser(output);

        Map<String, Long> survivorStats = getPlabStats(logParser, LogParser.ReportType.SURVIVOR_STATS, GC_ID_SURVIVOR_STATS);
        Map<String, Long> oldStats = getPlabStats(logParser, LogParser.ReportType.OLD_STATS, GC_ID_OLD_STATS);

        plabAllocatedSurvivor = wordSize * survivorStats.get("used");
        directAllocatedSurvivor = wordSize * survivorStats.get("direct_allocated");
        plabAllocatedOld = wordSize * oldStats.get("used");
        directAllocatedOld = wordSize * oldStats.get("direct_allocated");

        System.out.printf("Survivor PLAB allocated:%17d Direct allocated: %17d Mem consumed:%17d%n", plabAllocatedSurvivor, directAllocatedSurvivor, memAllocated);
        System.out.printf("Old      PLAB allocated:%17d Direct allocated: %17d Mem consumed:%17d%n", plabAllocatedOld, directAllocatedOld, memAllocated);

        // Unreachable objects case
        if (testCase.isDeadObjectCase()) {
            // No dead objects should be promoted
            if (!(checkRatio(plabAllocatedSurvivor, memAllocated) && checkRatio(directAllocatedSurvivor, memAllocated))) {
                System.out.println(output);
                throw new RuntimeException("Unreachable objects should not be allocated using PLAB or direct allocated to Survivor");
            }
            if (!(checkRatio(plabAllocatedOld, memAllocated) && checkRatio(directAllocatedOld, memAllocated))) {
                System.out.println(output);
                throw new RuntimeException("Unreachable objects should not be allocated using PLAB or direct allocated to Old");
            }
        } else {
            // Live objects case
            if (testCase.isPromotedByPLAB()) {
                // All live small objects should be promoted using PLAB
                if (!checkDifferenceRatio(plabAllocatedSurvivor, memAllocated)) {
                    System.out.println(output);
                    throw new RuntimeException("Expect that Survivor PLAB allocation are similar to all mem consumed");
                }
                if (!checkDifferenceRatio(plabAllocatedOld, memAllocated)) {
                    System.out.println(output);
                    throw new RuntimeException("Expect that Old PLAB allocation are similar to all mem consumed");
                }
            } else {
                // All big objects should be directly allocated
                if (!checkDifferenceRatio(directAllocatedSurvivor, memAllocated)) {
                    System.out.println(output);
                    throw new RuntimeException("Test fails. Expect that Survivor direct allocation are similar to all mem consumed");
                }
                if (!checkDifferenceRatio(directAllocatedOld, memAllocated)) {
                    System.out.println(output);
                    throw new RuntimeException("Test fails. Expect that Old direct allocation are similar to all mem consumed");
                }
            }

            // All promoted objects size should be similar to all consumed memory
            if (!checkDifferenceRatio(plabAllocatedSurvivor + directAllocatedSurvivor, memAllocated)) {
                System.out.println(output);
                throw new RuntimeException("Test fails. Expect that Survivor gen total allocation are similar to all mem consumed");
            }
            if (!checkDifferenceRatio(plabAllocatedOld + directAllocatedOld, memAllocated)) {
                System.out.println(output);
                throw new RuntimeException("Test fails. Expect that Old gen total allocation are similar to all mem consumed");
            }
        }
        System.out.println("Test passed!");
    }

    /**
     * Returns true if checkedValue is less than MEM_DIFFERENCE_PCT percent of controlValue.
     *
     * @param checkedValue - checked value
     * @param controlValue - referent value
     * @return true if checkedValue is less than MEM_DIFFERENCE_PCT percent of controlValue
     */
    private static boolean checkRatio(long checkedValue, long controlValue) {
        return (Math.abs(checkedValue) / controlValue) * 100L < MEM_DIFFERENCE_PCT;
    }

    /**
     * Returns true if difference of checkedValue and controlValue is less than
     * MEM_DIFFERENCE_PCT percent of controlValue.
     *
     * @param checkedValue - checked value
     * @param controlValue - referent value
     * @return true if difference of checkedValue and controlValue is less than
     * MEM_DIFFERENCE_PCT percent of controlValue
     */
    private static boolean checkDifferenceRatio(long checkedValue, long controlValue) {
        return (Math.abs(checkedValue - controlValue) / controlValue) * 100L < MEM_DIFFERENCE_PCT;
    }

    private static Map<String, Long> getPlabStats(LogParser logParser, LogParser.ReportType type, long gc_id) {

        Map<String, Long> survivorStats = logParser.getEntries()
                .get(gc_id)
                .get(type);
        return survivorStats;
    }

    /**
     * Description of one test case.
     */
    private static class TestCase {

        private final int wastePct;
        private final int plabSize;
        private final int chunkSize;
        private final int parGCThreads;
        private final int edenSize;
        private final boolean plabIsFixed;
        private final boolean objectsAreReachable;
        private final boolean promotedByPLAB;

        /**
         * @param wastePct
         * ParallelGCBufferWastePct
         * @param plabSize
         * -XX:OldPLABSize and -XX:YoungPLABSize
         * @param chunkSize
         * requested object size for memory consumption
         * @param parGCThreads
         * -XX:ParallelGCThreads
         * @param edenSize
         * NewSize and MaxNewSize
         * @param plabIsFixed
         * Use dynamic PLAB or fixed size PLAB
         * @param objectsAreReachable
         * true - allocate live objects
         * false - allocate unreachable objects
         * @param promotedByPLAB
         * true - we expect to see PLAB allocation during promotion
         * false - objects will be directly allocated during promotion
         */
        public TestCase(int wastePct,
                int plabSize,
                int chunkSize,
                int parGCThreads,
                int edenSize,
                boolean plabIsFixed,
                boolean objectsAreReachable,
                boolean promotedByPLAB
        ) {
            if (wastePct == 0 || plabSize == 0 || chunkSize == 0 || parGCThreads == 0 || edenSize == 0) {
                throw new IllegalArgumentException("Parameters should not be 0");
            }
            this.wastePct = wastePct;
            this.plabSize = plabSize;
            this.chunkSize = chunkSize;
            this.parGCThreads = parGCThreads;
            this.edenSize = edenSize;
            this.plabIsFixed = plabIsFixed;
            this.objectsAreReachable = objectsAreReachable;
            this.promotedByPLAB = promotedByPLAB;
        }

        /**
         * Convert current TestCase to List of options.
         * Assume test will fill half of existed eden.
         *
         * @return
         * List of options
         */
        public List<String> toOptions() {
            return Arrays.asList("-XX:ParallelGCThreads=" + parGCThreads,
                    "-XX:ParallelGCBufferWastePct=" + wastePct,
                    "-XX:OldPLABSize=" + plabSize,
                    "-XX:YoungPLABSize=" + plabSize,
                    "-XX:" + (plabIsFixed ? "-" : "+") + "ResizePLAB",
                    "-Dchunk.size=" + chunkSize,
                    "-Dreachable=" + objectsAreReachable,
                    "-XX:NewSize=" + edenSize + "m",
                    "-XX:MaxNewSize=" + edenSize + "m",
                    "-Dmem.to.fill=" + getMemToFill()
            );
        }

        /**
         * Print details about test case.
         */
        public void print(PrintStream out) {
            boolean expectPLABAllocation = promotedByPLAB && objectsAreReachable;
            boolean expectDirectAllocation = (!promotedByPLAB) && objectsAreReachable;

            out.println("Test case details:");
            out.println("  Young gen size : " + edenSize + "M");
            out.println("  Predefined PLAB size : " + plabSize);
            out.println("  Parallel GC buffer waste pct : " + wastePct);
            out.println("  Chunk size : " + chunkSize);
            out.println("  Parallel GC threads : " + parGCThreads);
            out.println("  Objects are created : " + (objectsAreReachable ? "reachable" : "unreachable"));
            out.println("  PLAB size is fixed: " + (plabIsFixed ? "yes" : "no"));
            out.println("Test expectations:");
            out.println("  PLAB allocation : " + (expectPLABAllocation ? "expected" : "unexpected"));
            out.println("  Direct allocation : " + (expectDirectAllocation ? "expected" : "unexpected"));
        }

        /**
         * @return
         * true if we expect PLAB allocation
         * false if no
         */
        public boolean isPromotedByPLAB() {
            return promotedByPLAB;
        }

        /**
         * @return
         * true if it is test case for unreachable objects
         * false for live objects
         */
        public boolean isDeadObjectCase() {
            return !objectsAreReachable;
        }

        /**
         * Returns amount of memory to fill
         *
         * @return amount of memory
         */
        public long getMemToFill() {
            return (long) (edenSize) * 1024l * 1024l / 2;
        }
    }
}
