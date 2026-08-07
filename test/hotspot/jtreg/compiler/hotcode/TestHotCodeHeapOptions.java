/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/*
 * @test TestHotCodeHeapOptions
 * @bug 8389652
 * @summary Checks VM options related to the hot code heap
 * @library /test/lib
 * @requires vm.compiler1.enabled & vm.compiler2.enabled & vm.flagless
 *
 * @run driver compiler.hotcode.TestHotCodeHeapOptions
 */

package compiler.hotcode;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestHotCodeHeapOptions {
    private static void passes(ProcessBuilder pb) throws Exception {
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldNotContain("HotCodeHeap disabled");
        out.shouldHaveExitValue(0);

        long actualHotCodeHeapSize = Long.parseLong(out.firstMatch("HotCodeHeapSize\\s+=\\s+(\\d+)", 1));
        if (actualHotCodeHeapSize == 0) {
            throw new RuntimeException("HotCodeHeapSize should be non zero but is " + actualHotCodeHeapSize);
        }
    }

    private static void warnsWith(ProcessBuilder pb, String message) throws Exception {
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldContain(message);
        out.shouldHaveExitValue(0);

        long actualHotCodeHeapSize = Long.parseLong(out.firstMatch("HotCodeHeapSize\\s+=\\s+(\\d+)", 1));
        if (actualHotCodeHeapSize != 0) {
            throw new RuntimeException("HotCodeHeapSize should have been disabled and set to zero but is " + actualHotCodeHeapSize);
        }

        String hotCodeHeap = out.firstMatch("HotCodeHeap\\s+=\\s+(\\w+)", 1);
        if (!hotCodeHeap.equals("false")) {
            throw new RuntimeException("HotCodeHeap should be false but is " + hotCodeHeap);
        }
    }

    private static void failsWith(ProcessBuilder pb, String message) throws Exception {
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldContain(message);
        out.shouldHaveExitValue(1);
    }

    /**
    * Check the result of hot code heap related VM options.
    */
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb;

        // No additional flags
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal",
                                                              "-XX:+UnlockExperimentalVMOptions",
                                                              "-XX:+HotCodeHeap",
                                                              "-version");
        passes(pb);

        // Valid sampling periods
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal",
                                                              "-XX:+UnlockExperimentalVMOptions",
                                                              "-XX:+HotCodeHeap",
                                                              "-XX:HotCodeMinSamplingMs=100",
                                                              "-XX:HotCodeMaxSamplingMs=100",
                                                              "-version");
        passes(pb);

        // Invalid sampling periods
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal",
                                                              "-XX:+UnlockExperimentalVMOptions",
                                                              "-XX:+HotCodeHeap",
                                                              "-XX:HotCodeMinSamplingMs=1000",
                                                              "-XX:HotCodeMaxSamplingMs=100",
                                                              "-version");
        failsWith(pb, "HotCodeMinSamplingMs cannot be larger than HotCodeMaxSamplingMs");

        // SegmentedCodeCache enabled
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal",
                                                              "-XX:+UnlockExperimentalVMOptions",
                                                              "-XX:+HotCodeHeap",
                                                              "-XX:+SegmentedCodeCache",
                                                              "-version");
        passes(pb);

        // SegmentedCodeCache disabled
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal",
                                                              "-XX:+UnlockExperimentalVMOptions",
                                                              "-XX:+HotCodeHeap",
                                                              "-XX:-SegmentedCodeCache",
                                                              "-version");
        warnsWith(pb, "HotCodeHeap disabled and HotCodeHeapSize zeroed because SegmentedCodeCache is disabled.");

        // SegmentedCodeCache disabled with HotCodeHeapSize explicitly set
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal",
                                                              "-XX:+UnlockExperimentalVMOptions",
                                                              "-XX:+HotCodeHeap",
                                                              "-XX:-SegmentedCodeCache",
                                                              "-XX:HotCodeHeapSize=8m",
                                                              "-version");
        warnsWith(pb, "HotCodeHeap disabled and HotCodeHeapSize zeroed because SegmentedCodeCache is disabled.");

        // TieredCompilation enabled
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal",
                                                              "-XX:+UnlockExperimentalVMOptions",
                                                              "-XX:+HotCodeHeap",
                                                              "-XX:+TieredCompilation",
                                                              "-version");
        passes(pb);

        // TieredCompilation disabled
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal",
                                                              "-XX:+UnlockExperimentalVMOptions",
                                                              "-XX:+HotCodeHeap",
                                                              "-XX:-TieredCompilation",
                                                              "-version");
        passes(pb);

        // C2 disabled with HotCodeHeapSize explicitly set
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal",
                                                              "-XX:+UnlockExperimentalVMOptions",
                                                              "-XX:+HotCodeHeap",
                                                              "-XX:TieredStopAtLevel=1",
                                                              "-XX:HotCodeHeapSize=8m",
                                                              "-version");
        warnsWith(pb, "HotCodeHeap disabled and HotCodeHeapSize zeroed because C2 is disabled.");

        // NMethodRelocation enabled
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal",
                                                              "-XX:+UnlockExperimentalVMOptions",
                                                              "-XX:+HotCodeHeap",
                                                              "-XX:+NMethodRelocation",
                                                              "-version");
        passes(pb);

        // NMethodRelocation disabled
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal",
                                                              "-XX:+UnlockExperimentalVMOptions",
                                                              "-XX:+HotCodeHeap",
                                                              "-XX:-NMethodRelocation",
                                                              "-version");
        failsWith(pb, "HotCodeHeap requires NMethodRelocation enabled");

        // HotCodeHeapSize set without HotCodeHeap
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal",
                                                              "-XX:+UnlockExperimentalVMOptions",
                                                              "-XX:HotCodeHeapSize=8m",
                                                              "-version");
        failsWith(pb, "HotCodeHeapSize requires HotCodeHeap enabled");
    }
}
