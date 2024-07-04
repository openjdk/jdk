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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestAlwaysPreTouchBehavior {

    // This test tests the ability of the JVM to pretouch its java heap
    // for test purposes (AlwaysPreTouch). We start a JVM with -XX:+AlwaysPreTouch,
    // then observe RSS and expect to see RSS covering the entirety of the
    // java heap, since it should all be pre-touched now.
    //
    // This test is important (we had pretouching break before) but inherently
    // shaky, since RSS of the JVM process is subject to host machine conditions.
    // If there is memory pressure, we may swap parts of the heap out, or parts
    // of the touched pages may be reassigned by the kernel to another process
    // after touching and before measuring.
    //
    // Therefore this test requires enough breathing room - a sufficiently large
    // available memory reserve on the machine - to not produce false negatives.
    // We do this via:
    // - specifying @requires os.maxMemory > 2G
    // - the test itself first checks if the available memory is larger than a
    //   certain required threshold A, only then it starts the testee JVM
    // - finally, in the testee JVM, if RSS is lower than expected and before
    //   registering that as an error, we check available memory again. If it is lower
    //   than threshold B, it again won't count as an error.

    final static WhiteBox wb = WhiteBox.getWhiteBox();

    final static long M = 1024 * 1024;
    final static long heapsize = M * 128;
    // maximum size of non-heap memory we expect the testee JVM to have.
    final static long expectedMaxNonHeapRSS = M * 256;
    // How much memory we require the host to have available before even starting the test
    final static  long requiredAvailableBefore = heapsize * 2 + expectedMaxNonHeapRSS;
    // In the testee JVM, if RSS is lower than expected, how much memory should *still* be available now to
    // count the low RSS as a real error - an indication for a misfunctioning pretouch, not just a low-memory
    // condition on the system.
    final static  long requiredAvailableDuring = expectedMaxNonHeapRSS;

    private static String[] prepareOptions(String[] extraVMOptions) {
        List<String> allOptions = new ArrayList<String>();
        if (extraVMOptions != null) {
            allOptions.addAll(Arrays.asList(extraVMOptions));
        }
        allOptions.add("-Xmx" + heapsize);
        allOptions.add("-Xms" + heapsize);
        allOptions.add("-XX:+AlwaysPreTouch");
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

        Runtime runtime = Runtime.getRuntime();
        long committed = runtime.totalMemory();
        long avail = wb.hostAvailableMemory();
        long rss = wb.rss();
        System.out.println("RSS: " + rss + " available: " + avail + " committed " + committed);

        if (args[0].equals("run")) { // see prepareOptions()
            if (rss < committed) {
                if (avail < requiredAvailableDuring) {
                    throw new SkippedException("Not enough memory for this  test (" + avail + ")");
                } else {
                    throw new RuntimeException("RSS of this process(" + rss + "b) should be bigger than or " +
                                               "equal to committed heap mem(" + committed + "b)");
                }
            }
        } else {
            System.out.println(" available: " + avail + "(required " + requiredAvailableBefore + ")");
            if (avail < requiredAvailableBefore) {
                throw new SkippedException("Not enough memory for this  test (" + avail + ")");
            }
            // pass options to the test
            runTestWithOptions(args);
        }
    }
}

