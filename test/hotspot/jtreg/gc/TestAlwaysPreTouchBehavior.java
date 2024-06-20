/*
 * Copyright (c) 2014, 2024, Alibaba Group Holding Limited. All rights reserved.
 * Copyright (c) 2024, Red Hat Inc.
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

package gc;

/**
 * @test id=ParallelCollector
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.Parallel
 * @requires os.maxMemory > 2G & vm.flagless & os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx64m gc.TestAlwaysPreTouchBehavior -XX:+UseParallelGC
 */

 /**
 * @test id=SerialCollector
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.Serial
 * @requires os.maxMemory > 2G & vm.flagless & os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx64m gc.TestAlwaysPreTouchBehavior -XX:+UseSerialGC
 */

/**
 * @test id=Shenandoah
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.Shenandoah
 * @requires os.maxMemory > 2G & vm.flagless & os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx64m gc.TestAlwaysPreTouchBehavior -XX:+UseShenandoahGC
 */

/**
 * @test id=G1
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.G1
 * @requires os.maxMemory > 2G & vm.flagless & os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx64m gc.TestAlwaysPreTouchBehavior -XX:+UseG1GC
 */

/**
 * @test id=ZGenerational
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.ZGenerational
 * @requires os.maxMemory > 2G & vm.flagless & os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx64m gc.TestAlwaysPreTouchBehavior -XX:+UseZGC -XX:+ZGenerational
 */

/**
 * @test id=ZSinglegen
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.ZSinglegen
 * @requires os.maxMemory > 2G & vm.flagless & os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx64m gc.TestAlwaysPreTouchBehavior -XX:+UseZGC -XX:-ZGenerational
 */

/**
 * @test id=Epsilon
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.Epsilon
 * @requires os.maxMemory > 2G & vm.flagless & os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx64m gc.TestAlwaysPreTouchBehavior -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC
 */


import jdk.test.lib.Asserts;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestAlwaysPreTouchBehavior {

    final static WhiteBox wb = WhiteBox.getWhiteBox();

    final static long M = 1024 * 1024;
    final static long G = M * 1024L;
    final static  long heapsize = M * 256L;
    final static  long requiredAvailable = heapsize * 2 + G;

    private static String[] prepareOptions(String[] extraVMOptions) {
        List<String> allOptions = new ArrayList<String>();
        if (extraVMOptions != null) {
            allOptions.addAll(Arrays.asList(extraVMOptions));
        }
        allOptions.add("-Xmx" + heapsize);
        allOptions.add("-Xms" + heapsize);
        allOptions.add("-XX:+AlwaysPreTouch"); // Stabilize RSS
        allOptions.add("-XX:+UnlockDiagnosticVMOptions"); // For whitebox
        allOptions.add("-XX:+WhiteBoxAPI");
        allOptions.add("-Xbootclasspath/a:.");
        allOptions.add("-XX:-ExplicitGCInvokesConcurrent"); // Invoke explicit GC on System.gc
        allOptions.add("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");
        allOptions.add(TestAlwaysPreTouchBehavior.class.getName());
        allOptions.add("run");
        return allOptions.toArray(new String[0]);
    }

    private static OutputAnalyzer runTestWithOptions(String[] extraOptions) throws IOException {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(prepareOptions(extraOptions));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.reportDiagnosticSummary();
        return output;
    }

    public static void main(String [] args) throws IOException {

        long avail = wb.hostAvailableMemory();

        if (args[0].equals("run")) {
            long rss = wb.rss();
            Runtime runtime = Runtime.getRuntime();
            long committed = runtime.totalMemory();
            System.out.println("RSS: " + rss + " available: " + avail + " committed " + committed);
            Asserts.assertGreaterThan(rss, committed, "RSS of this process(" + rss + "b) should be bigger than or equal to committed heap mem(" + committed + "b)");
        } else {
            System.out.println(" available: " + avail + "(required " + requiredAvailable + ")");
            if (avail < requiredAvailable) {
                throw new SkippedException("Not enough memory for this  test (" + avail + ")");
            }
            // pass options to the test
            runTestWithOptions(args);
        }
    }
}

