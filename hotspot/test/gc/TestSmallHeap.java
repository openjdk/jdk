/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestSmallHeap
 * @bug 8067438 8152239
 * @requires vm.gc=="null"
 * @summary Verify that starting the VM with a small heap works
 * @library /testlibrary /test/lib /test/lib/share/classes
 * @ignore 8161552
 * @modules java.base/jdk.internal.misc
 * @modules java.management/sun.management
 * @build TestSmallHeap
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI TestSmallHeap
 */

/* Note: It would be nice to verify the minimal supported heap size (2m) here,
 * but we align the heap size based on the card table size. And the card table
 * size is aligned based on the minimal pages size provided by the os. This
 * means that on most platforms, where the minimal page size is 4k, we get a
 * minimal heap size of 2m but on Solaris/Sparc we have a page size of 8k and
 * get a minimal heap size of 4m. And on platforms where the page size is 64k
 * we get a minimal heap size of 32m. We never use large pages for the card table.
 *
 * There is also no check in the VM for verifying that the maximum heap size
 * is larger than the supported minimal heap size. This means that specifying
 * -Xmx1m on the command line is fine but will give a heap of 2m (or 4m or 32m).
 *
 * To work around these rather strange behaviors this test uses -Xmx2m but then
 * calculates what the expected heap size should be. The calculation is a
 * simplified version of the code in the VM. We assume that the card table will
 * use one page. Each byte in the card table corresponds to 512 bytes on the heap.
 * So, the expected heap size is page_size * 512.
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.LinkedList;

import sun.hotspot.WhiteBox;

public class TestSmallHeap {

    public static void main(String[] args) throws Exception {
        // Do all work in the VM driving the test, the VM
        // with the small heap size should do as little as
        // possible to avoid hitting an OOME.
        WhiteBox wb = WhiteBox.getWhiteBox();
        int pageSize = wb.getVMPageSize();
        int heapBytesPerCard = 512;
        long expectedMaxHeap = pageSize * heapBytesPerCard;

        verifySmallHeapSize("-XX:+UseParallelGC", expectedMaxHeap);
        verifySmallHeapSize("-XX:+UseSerialGC", expectedMaxHeap);
        verifySmallHeapSize("-XX:+UseG1GC", expectedMaxHeap);
        verifySmallHeapSize("-XX:+UseConcMarkSweepGC", expectedMaxHeap);
    }

    private static void verifySmallHeapSize(String gc, long expectedMaxHeap) throws Exception {
        LinkedList<String> vmOptions = new LinkedList<>();
        vmOptions.add(gc);
        vmOptions.add("-Xmx2m");
        vmOptions.add("-XX:+PrintFlagsFinal");
        vmOptions.add(VerifyHeapSize.class.getName());

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(vmOptions.toArray(new String[0]));
        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);

        long maxHeapSize = Long.parseLong(analyzer.firstMatch("MaxHeapSize.+=\\s+(\\d+)",1));
        long actualHeapSize = Long.parseLong(analyzer.firstMatch(VerifyHeapSize.actualMsg + "(\\d+)",1));
        Asserts.assertEQ(maxHeapSize, expectedMaxHeap);
        Asserts.assertLessThanOrEqual(actualHeapSize, maxHeapSize);
    }
}

class VerifyHeapSize {
    public static final String actualMsg = "Actual heap size: ";

    public static void main(String args[]) {
        // Avoid string concatenation
        System.out.print(actualMsg);
        System.out.println(Runtime.getRuntime().maxMemory());
    }
}
