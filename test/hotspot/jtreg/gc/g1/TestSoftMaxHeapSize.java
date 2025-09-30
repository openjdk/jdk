/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package gc.g1;

/**
 * @test TestSoftMaxHeapSize
 * @summary Verify that G1 observes the SoftMaxHeapSize flag as it is changed externally.
 * @requires vm.gc.G1
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver gc.g1.TestSoftMaxHeapSize
 *
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.whitebox.WhiteBox;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestSoftMaxHeapSize {
    static final int M = 1024 * 1024;
    static final int MinHeapSize = 100 * M;
    static final int MaxHeapSize = 512 * M;

    // Sets SoftMaxHeapSize using jcmd to the values passed in as arguments, executing
    // a GC after each time having set the value.
    static class RunTests {
        static final WhiteBox wb = jdk.test.whitebox.WhiteBox.getWhiteBox();

        public static void setSoftMaxHeapSize(long value) {
            PidJcmdExecutor jcmd = new PidJcmdExecutor();
            jcmd.execute("VM.set_flag SoftMaxHeapSize " + value, true);
        }

        public static void main(String[] args) {
            wb.fullGC(); // Clean up heap.

            for (String value : args) {
              setSoftMaxHeapSize(Long.parseLong(value));
              wb.youngGC();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // SoftMaxHeapSize values to set in order.
        long[] tests = new long[] {
                          MinHeapSize,
                          MinHeapSize / 2,  // Lower than MinHeapSize
                          MaxHeapSize / 2,
                          MaxHeapSize,
                          MaxHeapSize * 2   // Higher than MaxHeapSize
                        };
        // Expected values of the SoftMaxHeapSize as reported by the collector corresponding
        // to above requested values.
        long[] expected = new long[] {
                            MinHeapSize,
                            MinHeapSize,
                            MaxHeapSize / 2,
                            MaxHeapSize,
                            MaxHeapSize
                          };

        OutputAnalyzer output;
        String[] arguments = new String[]
                             { "-XX:+UseG1GC",
                               "-Xmx" + MaxHeapSize,
                               "-Xms" + MinHeapSize,
                               "-Xlog:gc+ihop=trace",
                               "-Xbootclasspath/a:.",
                               "-XX:+UnlockDiagnosticVMOptions",
                               "-XX:+WhiteBoxAPI",
                               "-Dtest.jdk=" + System.getProperty("test.jdk"), // Needed for the jcmd tool.
                               RunTests.class.getName()
                             };

        // Generate command line, adding the test values to above arguments.
        ArrayList<String> allArgs = new ArrayList<>();
        allArgs.addAll(Arrays.asList(arguments));
        for (long l: tests) {
            allArgs.add(String.valueOf(l));
        }

        output = ProcessTools.executeLimitedTestJava(allArgs);

        System.out.println(output.getStdout());

        // Verify target values. Every GC prints soft max heap size used in the
        // calculation in the gc+ihop "Basic information" message.
        Pattern p = Pattern.compile("Basic.* soft max size: (\\d+)B");
        Matcher m = p.matcher(output.getStdout());

        int i = 1;
        while (m.find()) {
          System.out.println("Found new soft max " + m.group(1));
          Asserts.assertEQ(expected[i - 1], Long.parseLong(m.group(1)), "Expected SoftMaxHeapSize of " + expected[i - 1] + "B but got " + m.group(1) + "B for test #" + i);
          i++;
        }
        Asserts.assertEQ(i - 1, expected.length, "Expected " + (i - 1) + " log lines, got " + expected.length);

        output.shouldHaveExitValue(0);
    }
}
