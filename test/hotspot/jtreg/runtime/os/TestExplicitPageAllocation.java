/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package runtime.os;

/* @test
 * @bug 8271195
 * @summary Use largest available large page size smaller than LargePageSizeInBytes when available.
 * @requires os.family == "linux"
 * @requires vm.gc != "Z"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI runtime.os.TestExplicitPageAllocation
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.List;
import java.io.*;
import java.util.Scanner;
import jdk.test.whitebox.WhiteBox;

public class TestExplicitPageAllocation {

    private static final String DIR_HUGE_PAGES = "/sys/kernel/mm/hugepages/";
    private static final long HEAP_SIZE_IN_KB = 2 * 1024 * 1024;
    private static final long CODE_CACHE_SIZE_IN_KB = 256 * 1024;
    private static final Pattern HEAP_PATTERN = Pattern.compile("Heap:");
    private static final Pattern PAGE_SIZE_PATTERN = Pattern.compile(".*page_size=([^ ]+).*");
    private static final Pattern HUGEPAGE_PATTERN = Pattern.compile(".*hugepages-([^ ]+).*kB");

    private static String errorMessage = "";

    private static final int MAX_NUMBER_OF_PAGESIZE = 64;
    private static boolean[] pageSizes = new boolean[MAX_NUMBER_OF_PAGESIZE];
    private static int[] pageCount = new int[MAX_NUMBER_OF_PAGESIZE];
    private static int largestPageSizeIndex;
    private static int vmPageSizeIndex;
    private static final WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String args[]) {
        try {
            doSetup();
            if (largestPageSizeIndex > vmPageSizeIndex) {
                testCase(largestPageSizeIndex);
            }
        } catch (Exception e) {
           System.out.println("Exception " + e);
        }
    }

    private static long requiredPageCount(int index) {
        long pageSizeInKB = 1L << (index - 10);
        long codeCacheAddened = CODE_CACHE_SIZE_IN_KB >= pageSizeInKB ? CODE_CACHE_SIZE_IN_KB : 0;
        return (long) Math.ceil((double) (HEAP_SIZE_IN_KB + codeCacheAddened) / pageSizeInKB);
    }

    private static String sizeFromIndex(int index) {
        int k = 1024;
        int m = 1024 * 1024;
        int g = 1024 * 1024 * 1024;
        long sizeInBytes = 1L << index;
        if (sizeInBytes < m) {
           return Long.toString(sizeInBytes / k) + "K";
        } else if (sizeInBytes < g) {
           return Long.toString(sizeInBytes / m) + "M";
        } else {
           return Long.toString(sizeInBytes / g) + "G";
        }
    }

    private static boolean checkOutput(OutputAnalyzer out, String pageSize) throws Exception {
        List<String> lines = out.asLines();
        for (int i = 0; i < lines.size(); ++i) {
            String line = lines.get(i);
            System.out.println(line);
            if (HEAP_PATTERN.matcher(line).find()) {
                Matcher trace = PAGE_SIZE_PATTERN.matcher(line);
                trace.find();
                String tracePageSize = trace.group(1);
                if (pageSize.equals(tracePageSize)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int readPageCount(String filename, String pageSize) throws Exception {
        BufferedReader d = new BufferedReader(new FileReader(filename));
        int pageCount = Integer.parseInt(d.readLine());
        if (pageCount == 0) {
            System.out.println("No pages configured for " + pageSize + "kB");
        }
        return pageCount;
    }

    public static void doSetup() throws Exception {
        // Large page sizes
        File[] directories = new File(DIR_HUGE_PAGES).listFiles(File::isDirectory);
        for (File dir : directories) {
            String pageSizeFileName = dir.getName();
            Matcher matcher = HUGEPAGE_PATTERN.matcher(pageSizeFileName);
            matcher.find();
            String pageSize = matcher.group(1);
            assert pageSize != null;
            int availablePages = readPageCount(DIR_HUGE_PAGES + pageSizeFileName + "/free_hugepages", pageSize);
            int index = Long.numberOfTrailingZeros(Long.parseLong(pageSize) * 1024);
            largestPageSizeIndex = Math.max(index, largestPageSizeIndex);
            pageSizes[index] = true;
            pageCount[index] = availablePages;
        }
        // OS vm page size
        vmPageSizeIndex = Long.numberOfTrailingZeros(wb.getVMPageSize());
        pageSizes[vmPageSizeIndex] = true;
        pageCount[vmPageSizeIndex] = Integer.MAX_VALUE;
    }

    public static void testCase(int index) throws Exception {
        String pageSize = sizeFromIndex(index);
        long heap_size_in_gb = HEAP_SIZE_IN_KB >> 20;
        long code_cache_size = CODE_CACHE_SIZE_IN_KB >> 10;
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xlog:pagesize",
                                                                  "-XX:ReservedCodeCacheSize=" + code_cache_size + "m",
                                                                  "-XX:InitialCodeCacheSize=160k",
                                                                  "-XX:LargePageSizeInBytes=" + pageSize,
                                                                  "-XX:+UseParallelGC",
                                                                  "-XX:+UseLargePages",
                                                                  "-Xmx" + heap_size_in_gb + "g",
                                                                  "-Xms" + heap_size_in_gb + "g",
                                                                  "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        for (int i = index; i >= vmPageSizeIndex; i--) {
            if (pageSizes[i]) {
                String size = sizeFromIndex(i);
                System.out.println("Checking allocation for " + size);
                if (checkOutput(output, size)) {
                    System.out.println("TestCase Passed for pagesize: " + pageSize + ", allocated pagesize: " + size);
                    // Page allocation succeeded no need to check any more page sizes.
                    return;
                } else {
                    // Only consider this a test failure if there are enough configured
                    // pages to allow this reservation to succeeded.
                    if (requiredPageCount(i) <= pageCount[i]) {
                        throw new AssertionError("TestCase Failed for " + size + " page allocation. " +
                                                 "Required pages: " + requiredPageCount(i) + ", " +
                                                 "Configured pages: " + pageCount[i]);
                    }
                }
            }
        }

        if (errorMessage != "") {
            throw new AssertionError(errorMessage);
        }
    }
}
