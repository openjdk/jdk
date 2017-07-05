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

/*
 * @test TestNewSizeFlags
 * @key gc
 * @bug 8025166
 * @summary Verify that young gen size conforms values specified by NewSize, MaxNewSize and Xmn options
 * @library /testlibrary /../../test/lib
 * @modules java.base/sun.misc
 *          java.management
 * @build TestNewSizeFlags
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run driver/timeout=240  TestNewSizeFlags
 */

import jdk.test.lib.AllocationHelper;
import java.io.IOException;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import jdk.test.lib.HeapRegionUsageTool;
import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.ProcessTools;
import jdk.test.lib.Utils;
import sun.hotspot.WhiteBox;

public class TestNewSizeFlags {

    public static final long M = 1024 * 1024;

    public static void main(String args[]) throws Exception {
        LinkedList<String> options = new LinkedList<>(
                Arrays.asList(Utils.getFilteredTestJavaOpts("(-Xm[nsx][^ ]+)|"
                                + "(-XX:(Max)?((New)|"
                                + "(Heap))((Size)|"
                                + "(Ratio))=[^ ]+)"))
        );

        // Test NewSize and MaxNewSize
        testNewSizeFlags(20 * M, 10 * M, 30 * M, 40 * M, options, false);
        testNewSizeFlags(10 * M, 20 * M, 30 * M, 40 * M, options, false);
        testNewSizeFlags(-1, 20 * M, 30 * M, 40 * M, options, false);
        testNewSizeFlags(10 * M, -1, 30 * M, 40 * M, options, false);
        testNewSizeFlags(20 * M, 20 * M, 30 * M, 40 * M, options, false);
        testNewSizeFlags(20 * M, 30 * M, 40 * M, 50 * M, options, false);
        testNewSizeFlags(30 * M, 100 * M, 150 * M, 200 * M, options, false);
        testNewSizeFlags(0, -1, 30 * M, 40 * M, options, false);

        // Test -Xmn
        testXmnFlags(0, 30 * M, 40 * M, options, true);
        testXmnFlags(20 * M, 30 * M, 40 * M, options, false);
        testXmnFlags(50 * M, 70 * M, 100 * M, options, false);
    }

    /**
     * Verify that NewSize and MaxNewSize flags affect young gen size.
     *
     * @param newSize value of NewSize option, omitted if negative
     * @param maxNewSize value of MaxNewSize option, omitted if negative
     * @param heapSize value of HeapSize option
     * @param maxHeapSize value of MaxHeapSize option
     * @param options additional options for JVM
     * @param failureExpected true if JVM should fail with passed heap size options
     */
    public static void testNewSizeFlags(long newSize, long maxNewSize,
            long heapSize, long maxHeapSize,
            LinkedList<String> options,
            boolean failureExpected) throws Exception {
        testVMOptions(newSize, maxNewSize,
                heapSize, maxHeapSize,
                newSize, (maxNewSize >= 0 ? Math.max(maxNewSize, newSize) : maxNewSize),
                options, failureExpected);
    }

    /**
     * Verify that -Xmn flag affect young gen size.
     *
     * @param mnValue value of -Xmn option
     * @param heapSize value of HeapSize option
     * @param maxHeapSize value of MaxHeapSize option
     * @param options additional options for JVM
     * @param failureExpected true if JVM should fail with passed heap size options
     */
    public static void testXmnFlags(long mnValue,
            long heapSize, long maxHeapSize,
            LinkedList<String> options,
            boolean failureExpected) throws Exception {
        LinkedList<String> newOptions = new LinkedList<>(options);
        newOptions.add("-Xmn" + mnValue);
        testVMOptions(-1, -1,
                heapSize, maxHeapSize,
                mnValue, mnValue,
                newOptions, failureExpected);
    }

    /**
     * Verify that NewSize and MaxNewSize flags affect young gen size.
     *
     * @param newSize value of NewSize option, omitted if negative
     * @param maxNewSize value of MaxNewSize option, omitted if negative
     * @param heapSize value of HeapSize option
     * @param maxHeapSize value of MaxHeapSize option
     * @param expectedNewSize expected initial young gen size
     * @param expectedMaxNewSize expected max young gen size
     * @param options additional options for JVM
     * @param failureExpected true if JVM should fail with passed heap size options
     */
    public static void testVMOptions(long newSize, long maxNewSize,
            long heapSize, long maxHeapSize,
            long expectedNewSize, long expectedMaxNewSize,
            LinkedList<String> options, boolean failureExpected) throws Exception {
        OutputAnalyzer analyzer = startVM(options, newSize, maxNewSize, heapSize, maxHeapSize, expectedNewSize, expectedMaxNewSize);

        if (failureExpected) {
            analyzer.shouldHaveExitValue(1);
            analyzer.shouldMatch("(Error occurred during initialization of VM)|"
                    + "(Error: Could not create the Java Virtual Machine.)");
        } else {
            analyzer.shouldHaveExitValue(0);
        }
    }

    private static OutputAnalyzer startVM(LinkedList<String> options,
            long newSize, long maxNewSize,
            long heapSize, long maxHeapSize,
            long expectedNewSize, long expectedMaxNewSize) throws Exception, IOException {
        LinkedList<String> vmOptions = new LinkedList<>(options);
        Collections.addAll(vmOptions,
                "-Xbootclasspath/a:.",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                (newSize >= 0 ? "-XX:NewSize=" + newSize : ""),
                (maxNewSize >= 0 ? "-XX:MaxNewSize=" + maxNewSize : ""),
                "-Xmx" + maxHeapSize,
                "-Xms" + heapSize,
                "-XX:GCLockerEdenExpansionPercent=0",
                "-XX:-UseLargePages",
                NewSizeVerifier.class.getName(),
                Long.toString(expectedNewSize),
                Long.toString(expectedMaxNewSize)
        );
        vmOptions.removeIf(String::isEmpty);
        ProcessBuilder procBuilder = ProcessTools.createJavaProcessBuilder(vmOptions.toArray(new String[vmOptions.size()]));
        OutputAnalyzer analyzer = new OutputAnalyzer(procBuilder.start());
        return analyzer;
    }

    /**
     * NewSizeVerifier checks that initial young gen size is equal to expected
     * regardful to alignment and that young gen size will not be greater than
     * expected max size.
     * In order to verify that young gen size will not be greater then expected
     * max size, NewSizeVerifier do some object allocation to force garbage
     * collection and heap expansion.
     */
    public static class NewSizeVerifier {

        static WhiteBox wb = WhiteBox.getWhiteBox();

        public static final int ARRAY_LENGTH = 100;
        public static final int CHUNK_SIZE = 1024;
        public static final int MAX_ITERATIONS = 10;
        public static byte garbage[][] = new byte[ARRAY_LENGTH][];

        public static void main(String args[]) throws Exception {
            if (args.length != 2) {
                throw new IllegalArgumentException("Expected 2 args: <expectedNewSize> <expectedMaxNewSize>");
            }
            final long newSize = Long.valueOf(args[0]);
            final long maxNewSize = Long.valueOf(args[1]);

            // verify initial size
            verifyNewSize(newSize, maxNewSize);

            // force GC and verify that size is still correct
            AllocationHelper allocator = new AllocationHelper(MAX_ITERATIONS, ARRAY_LENGTH, CHUNK_SIZE, () -> (verifyNewSize(newSize, maxNewSize)));
            allocator.allocateMemoryAndVerifyNoOOME();
        }

        /**
         * Verify that actual young gen size conforms NewSize and MaxNewSize values.
         */
        public static Void verifyNewSize(long newSize, long maxNewSize) {
            long alignedNewSize = alignNewSize(newSize);
            long alignedMaxNewSize = alignNewSize(maxNewSize);

            MemoryUsage youngGenUsage = getYoungGenUsage();

            if (newSize != -1) {
                if (youngGenUsage.getInit() < alignedNewSize) {
                    throw new RuntimeException("initial new size < NewSize value: "
                            + youngGenUsage.getInit() + " < " + alignedNewSize);
                }

                if (youngGenUsage.getCommitted() < alignedNewSize) {
                    throw new RuntimeException("actual new size < NewSize value: "
                            + youngGenUsage.getCommitted() + " < " + alignedNewSize);
                }

                // for G1 max new size == committed new size
                if (GCTypes.YoungGCType.getYoungGCType() != GCTypes.YoungGCType.G1
                        && youngGenUsage.getMax() < alignedNewSize) {
                    throw new RuntimeException("max new size < NewSize value: "
                            + youngGenUsage.getMax() + " < " + alignedNewSize);
                }
            }

            if (maxNewSize != -1) {
                if (youngGenUsage.getInit() > alignedMaxNewSize) {
                    throw new RuntimeException("initial new size > MaxNewSize value: "
                            + youngGenUsage.getInit() + " > " + alignedMaxNewSize);
                }

                if (youngGenUsage.getCommitted() > alignedMaxNewSize) {
                    throw new RuntimeException("actual new size > MaxNewSize value: "
                            + youngGenUsage.getCommitted() + " > " + alignedMaxNewSize);
                }

                if (GCTypes.YoungGCType.getYoungGCType() != GCTypes.YoungGCType.G1
                        && youngGenUsage.getMax() != alignedMaxNewSize) {
                    throw new RuntimeException("max new size != MaxNewSize value: "
                            + youngGenUsage.getMax() + " != " + alignedMaxNewSize);
                }
            }
            return null;
        }

        /**
         *  Get young gen memory usage.
         *
         *  For G1 it is EdenUsage + SurvivorUsage,
         *  for other GCs it is EdenUsage + 2 * SurvivorUsage.
         *  For G1 max value is just LONG_MAX.
         *  For all GCs used value is 0.
         */
        private static MemoryUsage getYoungGenUsage() {
            if (GCTypes.YoungGCType.getYoungGCType() == GCTypes.YoungGCType.G1) {
                return new MemoryUsage(HeapRegionUsageTool.getEdenUsage().getInit()
                        + HeapRegionUsageTool.getSurvivorUsage().getInit(),
                        0,
                        HeapRegionUsageTool.getEdenUsage().getCommitted()
                        + HeapRegionUsageTool.getSurvivorUsage().getCommitted(),
                        Long.MAX_VALUE);
            } else {
                return new MemoryUsage(HeapRegionUsageTool.getEdenUsage().getInit()
                        + HeapRegionUsageTool.getSurvivorUsage().getInit() * 2,
                        0,
                        HeapRegionUsageTool.getEdenUsage().getCommitted()
                        + HeapRegionUsageTool.getSurvivorUsage().getCommitted() * 2,
                        HeapRegionUsageTool.getEdenUsage().getMax()
                        + HeapRegionUsageTool.getSurvivorUsage().getMax() * 2);
            }
        }

        /**
         * Align value regardful to used young GC.
         */
        public static long alignNewSize(long value) {
            switch (GCTypes.YoungGCType.getYoungGCType()) {
                case DefNew:
                case ParNew:
                    return HeapRegionUsageTool.alignDown(value, wb.getHeapSpaceAlignment());
                case PSNew:
                    return HeapRegionUsageTool.alignUp(HeapRegionUsageTool.alignDown(value,
                            wb.getHeapSpaceAlignment()),
                            wb.psVirtualSpaceAlignment());
                case G1:
                    return HeapRegionUsageTool.alignUp(value, wb.g1RegionSize());
                default:
                    throw new RuntimeException("Unexpected young GC type");
            }
        }
    }
}
