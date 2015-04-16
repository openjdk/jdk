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
 * @test TestLargePageUseForAuxMemory.java
 * @bug 8058354
 * @key gc
 * @library /testlibrary /../../test/lib
 * @requires (vm.gc=="G1" | vm.gc=="null")
 * @build TestLargePageUseForAuxMemory
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @summary Test that auxiliary data structures are allocated using large pages if available.
 * @run main/othervm -Xbootclasspath/a:. -XX:+UseG1GC -XX:+WhiteBoxAPI -XX:+IgnoreUnrecognizedVMOptions -XX:+UseLargePages TestLargePageUseForAuxMemory
 */

import com.oracle.java.testlibrary.*;
import sun.hotspot.WhiteBox;

public class TestLargePageUseForAuxMemory {
    static final int HEAP_REGION_SIZE = 4 * 1024 * 1024;
    static long largePageSize;
    static long smallPageSize;

    static void checkSmallTables(OutputAnalyzer output, long expectedPageSize) throws Exception {
        output.shouldContain("G1 'Block offset table': pg_sz=" + expectedPageSize);
        output.shouldContain("G1 'Card counts table': pg_sz=" + expectedPageSize);
    }

    static void checkBitmaps(OutputAnalyzer output, long expectedPageSize) throws Exception {
        output.shouldContain("G1 'Prev Bitmap': pg_sz=" + expectedPageSize);
        output.shouldContain("G1 'Next Bitmap': pg_sz=" + expectedPageSize);
    }

    static void testVM(long heapsize, boolean cardsShouldUseLargePages, boolean bitmapShouldUseLargePages) throws Exception {
        ProcessBuilder pb;
        // Test with large page enabled.
        pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                                   "-XX:G1HeapRegionSize=" + HEAP_REGION_SIZE,
                                                   "-Xms" + 10 * HEAP_REGION_SIZE,
                                                   "-Xmx" + heapsize,
                                                   "-XX:+TracePageSizes",
                                                   "-XX:+UseLargePages",
                                                   "-XX:+IgnoreUnrecognizedVMOptions",  // there is on ObjectAlignmentInBytes in 32 bit builds
                                                   "-XX:ObjectAlignmentInBytes=8",
                                                   "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        checkSmallTables(output, (cardsShouldUseLargePages ? largePageSize : smallPageSize));
        checkBitmaps(output, (bitmapShouldUseLargePages ? largePageSize : smallPageSize));
        output.shouldHaveExitValue(0);

        // Test with large page disabled.
        pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                                   "-XX:G1HeapRegionSize=" + HEAP_REGION_SIZE,
                                                   "-Xms" + 10 * HEAP_REGION_SIZE,
                                                   "-Xmx" + heapsize,
                                                   "-XX:+TracePageSizes",
                                                   "-XX:-UseLargePages",
                                                   "-XX:+IgnoreUnrecognizedVMOptions",  // there is on ObjectAlignmentInBytes in 32 bit builds
                                                   "-XX:ObjectAlignmentInBytes=8",
                                                   "-version");

        output = new OutputAnalyzer(pb.start());
        checkSmallTables(output, smallPageSize);
        checkBitmaps(output, smallPageSize);
        output.shouldHaveExitValue(0);
    }

    public static void main(String[] args) throws Exception {
        if (!Platform.isDebugBuild()) {
            System.out.println("Skip tests on non-debug builds because the required option TracePageSizes is a debug-only option.");
            return;
        }

        WhiteBox wb = WhiteBox.getWhiteBox();
        smallPageSize = wb.getVMPageSize();
        largePageSize = wb.getVMLargePageSize();

        if (largePageSize == 0) {
            System.out.println("Skip tests because large page support does not seem to be available on this platform.");
            return;
        }

        // To get large pages for the card table etc. we need at least a 1G heap (with 4k page size).
        // 32 bit systems will have problems reserving such an amount of contiguous space, so skip the
        // test there.
        if (!Platform.is32bit()) {
            // Size that a single card covers.
            final int cardSize = 512;

            final long heapSizeForCardTableUsingLargePages = largePageSize * cardSize;

            testVM(heapSizeForCardTableUsingLargePages, true, true);
            testVM(heapSizeForCardTableUsingLargePages + HEAP_REGION_SIZE, true, true);
            testVM(heapSizeForCardTableUsingLargePages - HEAP_REGION_SIZE, false, true);
        }

        // Minimum heap requirement to get large pages for bitmaps is 128M heap. This seems okay to test
        // everywhere.
        final int bitmapTranslationFactor = 8 * 8; // ObjectAlignmentInBytes * BitsPerByte
        final long heapSizeForBitmapUsingLargePages = largePageSize * bitmapTranslationFactor;

        testVM(heapSizeForBitmapUsingLargePages, false, true);
        testVM(heapSizeForBitmapUsingLargePages + HEAP_REGION_SIZE, false, true);
        testVM(heapSizeForBitmapUsingLargePages - HEAP_REGION_SIZE, false, false);
    }
}

