/*
 * Copyright (c) 2025, Huawei Technologies Co., Ltd. All rights reserved.
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

/*
 * @test TestG1MetadataSize.java
 * @bug 8350860
 * @summary Ensure G1 metadata size does not grow unreasonably.
 * Should treat JDK-8350857 as a followup to reduce MarkStackSize
 * @requires vm.gc.G1
 * @requires vm.bits != "32"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver gc.g1.TestG1MetadataSize
 */
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestG1MetadataSize {

    private static final int K = 1024;
    private static final int MARK_STACK_SIZE_IN_M = 4;
    // NMT report sample:
    // - GC (reserved=448282674, committed=111427634)
    //  (malloc=54079538 #626) (at peak)
    //  (mmap: reserved=394203136, committed=57348096, at peak)
    private static final String GC_LINE_PATTERN = "GC \\((.*)\\n(.*?\\n)(.*?\\n)(.*)\\n";;

    public static void main(String[] args) throws Exception {
        for (int maxHeapSizeMb = 64; maxHeapSizeMb <= 16 * K; maxHeapSizeMb *= 2) {
            // NMT current is not related to initialHeapSizeMb. Iterate it over to prevent regression
            for (int initialHeapSizeMb = 64; initialHeapSizeMb <= maxHeapSizeMb; initialHeapSizeMb *= 2) {
                for (int parallelGCThreads = 1; parallelGCThreads <= 16; parallelGCThreads *= 2) {
                    for (int concGCThreads = 1; concGCThreads <= 16; concGCThreads *= 2) {
                        test(initialHeapSizeMb, maxHeapSizeMb, parallelGCThreads, concGCThreads);
                    }
                }
            }
        }
    }

    private static void test(int initialHeapSizeMb, int maxHeapSizeMb, int parallelGCThreads, int concGCThreads) throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
            String.format("-Xms%dm", initialHeapSizeMb),
            String.format("-Xmx%dm", maxHeapSizeMb),
            "-XX:+UseG1GC",
            String.format("-XX:ParallelGCThreads=%d", parallelGCThreads),
            String.format("-XX:ConcGCThreads=%d", concGCThreads),
            String.format("-XX:MarkStackSize=%d", MARK_STACK_SIZE_IN_M * K * K),
            "-Xlog:nmt",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+PrintNMTStatistics",
            "-XX:NativeMemoryTracking=summary",
            GCTest.class.getName());

        verifyMalloc(output, maxHeapSizeMb, parallelGCThreads, concGCThreads);
        verifyMmap(output, maxHeapSizeMb);
    }

    private static void verifyMalloc(OutputAnalyzer output, int maxHeapSizeMb, int parallelGCThreads, int concGCThreads) {
        String mallocLine = output.firstMatch(GC_LINE_PATTERN, 2);
        Asserts.assertNotNull(mallocLine, "Couldn't find pattern '" + GC_LINE_PATTERN
                + "': in output '" + output.getOutput() + "'");

        Pattern mmapLinePattern = Pattern.compile("malloc=(.*) \\#(.*)");
        Matcher matcher = mmapLinePattern.matcher(mallocLine);
        matcher.find();
        String mallocMem = matcher.group(1);

        long mallocByte = Long.parseLong(mallocMem);

        // Must be more than zero
        Asserts.assertGT(mallocByte, 0L);

        long maxBytes = expectedMallocFootPrintInK(parallelGCThreads, concGCThreads, maxHeapSizeMb) * K;
        Asserts.assertLTE(mallocByte, maxBytes);
    }

    private static void verifyMmap(OutputAnalyzer output, int maxHeapSizeMb) {
        String mmapLine = output.firstMatch(TestG1MetadataSize.GC_LINE_PATTERN, 3);
        Asserts.assertNotNull(mmapLine, "Couldn't find pattern '" + TestG1MetadataSize.GC_LINE_PATTERN
        + "': in output '" + output.getOutput() + "'");
        System.out.println(mmapLine);

        Pattern mmapLinePattern = Pattern.compile("mmap: reserved=(.*), committed=(.*), (.*)");
        Matcher matcher = mmapLinePattern.matcher(mmapLine);
        matcher.find();
        String committed = matcher.group(2);

        long committedBytes = Long.parseLong(committed);

        // Must be more than zero
        Asserts.assertGT(committedBytes, 0L);

        long maxBytes = expectedMaxMmapFootprintInK(maxHeapSizeMb) * K;
        Asserts.assertLTE(committedBytes, maxBytes);
    }

    private static long expectedMaxMmapFootprintInK(int maxHeapSizeMb) {
        long expected = 0;
        // Top contributor 1: G1CMMarkStack::ChunkAllocator::reserve(unsigned long)
        // Only related to MarkStackSize value. Does not grow along with heap size, parallelGCThreads or concGCThreads
        // 4 Mb MarkStackSize - 32768KB mmap memory
        expected += MARK_STACK_SIZE_IN_M * K * 8;

        // Top contributor 2: G1CollectedHeap::create_aux_memory_mapper
        // Grows as heap size grows. It creates BOT, card table and the bitmap
        // 1g heap - 20480KB mmap memory
        expected += maxHeapSizeMb * 20;

        // Other insignificant mmap memory and headroom
        expected += expected / 10;

        System.out.println("expected Kb for mmap: " + expected + " maxHeapSizeMb: " + maxHeapSizeMb);
        return expected;
    }

    private static int expectedMallocFootPrintInK(int parallelGCThreads, int concGCThreads, int maxHeapSizeMb) {
        // malloc size is not impacted by heap size dramatically. When both parallelGCThreads and concGCThreads are 1,
        // GC malloc is around 3877KB when heap is 16G, around 2231KB when heap is 64M.

        int expectedInK = 0;
        // Top contributor 1: G1CollectedHeap::G1CollectedHeap 
        // It grows along with parallelGCThreads. n parallelGCThreads -> n MB
        expectedInK += parallelGCThreads * K;

        // Top contributor 2: G1ConcurrentMark::G1ConcurrentMark(G1CollectedHeap*, G1RegionToSpaceMapper*)
        // Grows with thread counts. 
        expectedInK += Math.max(parallelGCThreads, concGCThreads) * K;

        // Top contributor 3: G1CardSetMemoryManager::G1CardSetMemoryManager
        // It grows along with heap size. heapsize 1024M -> ~1024K memory allocated by malloc
        expectedInK += maxHeapSizeMb;
        
        // 10% headroom
        expectedInK += expectedInK / 10;

        return expectedInK;
    }

    static class GCTest {
        static Object sink;
        static int ARRAY_SIZE = 100;

        public static void main(String[] args) throws Exception {
            sink = new byte[ARRAY_SIZE];
        }
    }
}
