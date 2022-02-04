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

/* @test
 * @bug 8271195
 * @summary Use largest available large page size smaller than LargePageSizeInBytes when available.
 * @requires os.family == "linux"
 * @requires vm.gc != "Z"
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver runtime.os.TestExplicitPageAllocation
 */

package runtime.os;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import jdk.test.lib.Utils;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Scanner;
import java.io.FileWriter;

public class TestExplicitPageAllocation {
    private static String file1GHugePages = "/sys/kernel/mm/hugepages/hugepages-1048576kB/free_hugepages";
    private static String file2MHugePages = "/sys/kernel/mm/hugepages/hugepages-2048kB/free_hugepages";
    private static String file1GHugePagesResv = "/sys/kernel/mm/hugepages/hugepages-1048576kB/resv_hugepages";
    private static String file2MHugePagesResv = "/sys/kernel/mm/hugepages/hugepages-2048kB/resv_hugepages";

    private static String heapPrefix = "Heap:";
    private static Pattern heapPattern = Pattern.compile(heapPrefix);
    private static FileInputStream fis;
    private static DataInputStream dis;
    private static int orig1GPageCount;
    private static int orig2MPageCount;
    private static int resv1GPageCount;
    private static int resv2MPageCount;

    private static String errorMessage = null;


    public static void main(String args[]) throws Exception {
        try {
            doSetup();
            testCase1();
            testCase2();
            testCase3();
            testCase4();
        } catch(Exception e) {
           System.out.println("Exception"+e);
        }
        if (errorMessage!=null) {
            throw new AssertionError(errorMessage);
        }

    }

    private static boolean matchPattern(String line, Pattern regex) {
        return regex.matcher(line).find();
    }
    private static boolean matchPattern(String line) {
        return matchPattern(line, heapPattern);
    }

    private static boolean checkOutput(OutputAnalyzer out, String pageSize) throws Exception {
        List<String> lines = out.asLines();
        String traceLinePatternString = ".*page_size=([^ ]+).*";
        Pattern traceLinePattern = Pattern.compile(traceLinePatternString);
        for (int i = 0; i < lines.size(); ++i) {
            String line = lines.get(i);
            System.out.println(line);
            if (matchPattern(line)) {
                Matcher trace = traceLinePattern.matcher(line);
                trace.find();
                String tracePageSize = trace.group(1);
                if(pageSize.contains(tracePageSize)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int checkAndReadFile(String filename, String errorstr) throws Exception {
        try {
            fis = new FileInputStream(filename);
            dis = new DataInputStream(fis);
            int pagecount = Integer.parseInt(dis.readLine());
            dis.close();
            fis.close();
            return pagecount;
        } catch (Exception e) {
            System.out.println(errorstr);
        }
        return -1;
    }

    public static void doSetup() throws Exception{
        File file;
        // Legality check for 1G , 2M pages.
        orig1GPageCount = checkAndReadFile(file1GHugePages, "System does not support 1G pages");
        if (orig1GPageCount >= 0) {
            System.out.println("Number of 1G pages = " + orig1GPageCount + "\n");
        }
        orig2MPageCount =  checkAndReadFile(file2MHugePages, "System does not support 2M pages");
        if (orig2MPageCount >= 0) {
            System.out.println("Number of 2M pages = " + orig2MPageCount + "\n");
        }
        resv1GPageCount = checkAndReadFile(file1GHugePagesResv, "System does not support 1G pages");
        if (resv2MPageCount >= 0) {
            System.out.println("Number of reserved 1G pages = " + resv1GPageCount + "\n");
        }
        resv2MPageCount =  checkAndReadFile(file2MHugePagesResv, "System does not support 2M pages");
        if (resv2MPageCount >= 0) {
            System.out.println("Number of reserved 2M pages = " + resv2MPageCount + "\n");
        }

    }

    public static void testCase1() throws Exception {
        if((orig1GPageCount - resv1GPageCount)  < 2) {
            System.out.println("TestCase1 skipped\n");
            return;
        }
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xlog:pagesize",
                                                                  "-XX:LargePageSizeInBytes=1G",
                                                                  "-XX:+UseParallelGC",
                                                                  "-XX:+UseLargePages",
                                                                  "-Xmx2g",
                                                                  "-Xms1g",
                                                                  TestExplicitPageAllocation.class.getName());
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        if(!checkOutput(output,"1G")) {
           errorMessage = "Failed 1G page allocation\n";
        } else {
           System.out.println("TestCase1 Passed\n");
        }
    }

    public static void testCase2() throws Exception {
        if((orig1GPageCount - resv1GPageCount) > 0 || (orig2MPageCount - resv2MPageCount) < 1280) {
            System.out.println("TestCase2 skipped\n");
            return;
        }
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xlog:pagesize",
                                                                  "-XX:LargePageSizeInBytes=1G",
                                                                  "-XX:+UseParallelGC",
                                                                  "-XX:+UseLargePages",
                                                                  "-Xmx2g",
                                                                  "-Xms1g",
                                                                  TestExplicitPageAllocation.class.getName());
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        if(!checkOutput(output,"2M")) {
           errorMessage = "Failed 2M page allocation\n";
        } else {
           System.out.println("TestCase2 Passed\n");
        }
    }

    public static void testCase3() throws Exception {
        if((orig1GPageCount - resv1GPageCount) > 0 || (orig2MPageCount - resv2MPageCount) > 0) {
            System.out.println("TestCase3 skipped\n");
            return;
        }
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xlog:pagesize",
                                                                 "-XX:LargePageSizeInBytes=1G",
                                                                 "-XX:+UseParallelGC",
                                                                 "-XX:+UseLargePages",
                                                                 "-Xmx2g",
                                                                 "-Xms1g",
                                                                 TestExplicitPageAllocation.class.getName());
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        if(!checkOutput(output,"4K")) {
           errorMessage = "Failed 4K page allocation\n";
        } else {
           System.out.println("TestCase3 Passed\n");
        }
    }

    public static void testCase4() throws Exception {
        if((orig2MPageCount - resv2MPageCount) < 1280) {
            System.out.println("TestCase4 skipped\n");
            return;
        }
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xlog:pagesize",
                                                                 "-XX:LargePageSizeInBytes=2M",
                                                                 "-XX:+UseParallelGC",
                                                                 "-XX:+UseLargePages",
                                                                 "-Xmx2g",
                                                                 "-Xms1g",
                                                                 TestExplicitPageAllocation.class.getName());
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        if(!checkOutput(output,"2M")) {
           errorMessage = "Failed 2M page allocation\n";
        } else {
           System.out.println("TestCase4 Passed\n");
        }
    }
}
